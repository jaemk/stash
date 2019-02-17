(ns stash.handlers
  (:import [java.security MessageDigest]
           [java.nio ByteBuffer])
  (:require [taoensso.timbre :as t]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [aleph.http :as http]
            [byte-streams :as bs]
            [stash.execution :as ex]
            [stash.database :as db]
            [stash.utils :refer [get-config]]
            [clojure.java.jdbc :as j]
            [clojure.java.io :as io]
            [cheshire.core :refer [parse-string generate-string]
                           :rename {parse-string s->map
                                    generate-string  map->s}]))


(def buf-size 2048)


(defn ->resp
  "Construct a response map
  Any kwargs provided are merged into a default 200 response"
  [& kwargs]
  (let [kwargs (apply hash-map kwargs)
        ct (or (:ct kwargs) "text/plain")
        kwargs (dissoc kwargs :ct)
        default {:status 200
                 :headers {"content-type" ct}
                 :body ""}]
    (merge default kwargs)))


(defn ->text [s & kwargs]
  (let [kwargs (apply hash-map kwargs)
        s (if (instance? String s) s (str s))]
    (merge
      {:status 200
       :headers {"content-type" "text/plain"}
       :body s}
      kwargs)))


(defn ->json [mapping & kwargs]
  (let [kwargs (apply hash-map kwargs)]
    (merge
      {:status 200
       :headers {"content-type" "application/json"}
       :body (map->s mapping)}
      kwargs)))


(defn index [req]
  (->text "hello"))


(defn status [req]
  (->json {:status "ok"
           :version (get-config "APP_VERSION")}))


(defn stream-to-file [src file]
  (d/future
    (with-open [file-stream (io/output-stream file)]
      (io/copy src file-stream))))


(defn new-digest [] (MessageDigest/getInstance "sha-256"))


(defn new-upload-path [token]
  (let [upload-dir-name (get-config "UPLOAD_DIR" :default "uploads")
        upload-dir-f (io/file upload-dir-name)]
    (if (.exists upload-dir-f)
      (format "%s/%s" upload-dir-name token)
      (throw (Exception. (str "upload dir does not exist: " upload-dir-f))))))


(defn create [req]
  (let [token (java.util.UUID/randomUUID)
        token-str (-> token .toString (.replace "-" ""))
        upload-path (new-upload-path token-str)
        upload-file (io/file upload-path)
        size (atom 0)
        data-stream (s/stream buf-size)
        connect-fn (fn [buf]
                     (do
                       (swap! size #(+ % (.readableBytes buf)))
                       (s/put! data-stream buf)))
        _ (s/connect-via (:body req) connect-fn data-stream)
        body (bs/to-input-stream data-stream)]
    (d/chain
      (d/future-with
        ex/cpu
        (j/with-db-transaction [conn (db/conn)]
          (j/insert! conn :items {:path upload-path :token token :size @size})))
      (fn [item]
        (do
          (t/info "uploading item" item "to" upload-path)
          item))
      (fn [item]
        (stream-to-file body upload-file)
        item)
      (fn [item] (->json {:ok :ok
                          :size @size
                          :token token-str})))))


(defn stream-file [sink file-name]
  (d/future-with
    ex/fs
    (with-open [file-stream (io/input-stream (io/file file-name))
                buf (byte-array buf-size)]
      (loop []
        (let [size (.read file-stream buf)]
          (if (pos? size)
            (do
              (s/put! sink (into-array Byte/TYPE (take size buf)))
              (recur))
            (s/close! sink)))))))


(defn retrieve [req]
  (let [sink (s/stream buf-size)
        body (s/stream buf-size)
        _ (s/connect sink body {:description "file transfer stream"})]
    (stream-file sink "LICENSE")
    (->resp
     :headers {"content-type" "text/plain"}
     :body body)))

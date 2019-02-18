(ns stash.handlers
  (:import [java.security MessageDigest]
           [java.nio ByteBuffer]
           [java.util UUID]
           [org.apache.commons.codec.binary Hex]
           [io.netty.buffer PooledSlicedByteBuf])
  (:require [taoensso.timbre :as t]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [aleph.http :as http]
            [byte-streams :as bs]
            [stash.execution :as ex]
            [stash.database :as db]
            [stash.utils :as u]
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
           :version (u/get-config "APP_VERSION")}))


(defn stream-to-file [src file]
  (d/future
    (with-open [file-stream (io/output-stream file)]
      (io/copy src file-stream))))


(defn new-digest [] (MessageDigest/getInstance "sha-256"))


(defn new-upload-path [token]
  (let [upload-dir-name (u/get-config "UPLOAD_DIR" :default "uploads")
        upload-dir-f (io/file upload-dir-name)]
    (if (.exists upload-dir-f)
      (format "%s/%s" upload-dir-name token)
      (throw (Exception. (str "upload dir does not exist: " upload-dir-f))))))


(defn create [req]
  (let [token (u/uuid)
        token-str (u/format-uuid token)
        upload-path (new-upload-path token-str)
        upload-file (io/file upload-path)
        size (atom 0)
        supplied-token (-> req :params :supplied-token)
        data-stream (s/stream buf-size)
        connect-fn (fn [^PooledSlicedByteBuf buf]
                     (do
                       (swap! size #(+ % (.readableBytes buf)))
                       (s/put! data-stream buf)))
        _ (s/connect-via (:body req) connect-fn data-stream)
        body (bs/to-input-stream data-stream)]
    (d/chain
      (d/future-with
        ex/cpu
        (j/with-db-transaction [conn (db/conn)]
          (j/insert! conn :items {:path upload-path
                                  :stash_token token
                                  :supplied_token supplied-token
                                  :size @size})))
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


(defn stream-from-file [sink file-name]
  (d/future-with
    ex/fs
    (let [buf (byte-array buf-size)
          file (io/file file-name)]
      (with-open [file-stream (io/input-stream file)]
        (loop []
          (let [size (.read file-stream buf)]
            (if (pos? size)
              (do
                (s/put! sink (into-array Byte/TYPE (take size buf)))
                (recur))
              (s/close! sink))))))))


(defn backing-file-exists [item]
  (let [item-id (:id item)
        file-path (:path item)
        file (io/file file-path)]
    (if (.exists file)
      true
      (do
        (t/error
          (format "Backing file (%s) for item (%s) does not exist" file-path item-id))
        false))))


(defn retrieve [req]
  (let [supplied-token (-> req :params :supplied-token)
        stash-token (-> req
                        :body
                        (bs/to-string)
                        (s->map)
                        (get "stash_token")
                        (u/parse-uuid))
        _ (prn supplied-token stash-token)
        sink (s/stream buf-size)
        resp (s/stream buf-size)
        _ (s/connect sink resp {:description "file transfer stream"})]
    (d/chain
      (d/future-with
        ex/cpu
        (j/with-db-transaction [conn (db/conn)]
          (j/query conn ["select * from items where stash_token = ? and supplied_token = ?"
                         stash-token
                         supplied-token]
                   {:result-set-fn first})))
      (fn [item]
        (if (or
              (nil? item)
              (not (backing-file-exists item)))
          (->resp :status 404 :body "nothing to see here")
          (d/chain
            (stream-from-file sink (:path item))
            (fn [_]
              (->resp :headers {"content-type" "text/plain"}
                      :body resp))))))))


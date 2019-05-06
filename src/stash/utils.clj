(ns stash.utils
  (:import [java.util UUID]
           [java.nio ByteBuffer]
           [org.apache.commons.codec.binary Hex])
  (:require [taoensso.timbre :as t]
            [cheshire.core :refer [parse-string generate-string]
             :rename {parse-string    s->map
                      generate-string map->s}]
            [stash.config :as config]
            [clojure.java.io :as io]
            [clojure.string :as string]))


;; ---- response builders
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


;; ---- error builders
(defn ex-invalid-request!
  [& {:keys [e-msg resp-msg] :or {e-msg "invalid request"
                                  resp-msg "invalid request"}}]
  (throw
    (ex-info e-msg
             {:type :invalid-request
              :msg e-msg
              :resp (->resp :status 400 :body resp-msg)})))

(defn ex-unauthorized!
  [& {:keys [e-msg resp-msg] :or {e-msg "unauthorized"
                                  resp-msg "unauthorized"}}]
  (throw
    (ex-info e-msg
             {:type :invalid-request
              :msg e-msg
              :resp (->resp :status 401 :body resp-msg)})))

(defn ex-not-found!
  [& {:keys [e-msg resp-msg] :or {e-msg "item not found"
                                  resp-msg "item not found"}}]
  (throw
    (ex-info e-msg
             {:type :invalid-request
              :msg e-msg
              :resp (->resp :status 404 :body resp-msg)})))

(defn ex-does-not-exist! [record-type]
  (let [msg (format "%s does not exist" record-type)]
    (throw
      (ex-info
        msg
        {:type :does-not-exist
         :cause record-type
         :msg msg
         :resp (->resp :status 404 :body "item not found")}))))

(defn ex-error!
  [e-msg & {:keys [resp-msg cause] :or {resp-msg "something went wrong"
                                        cause nil}}]
  (throw
   (ex-info
     e-msg
     {:type :internal-error
      :cause cause
      :msg e-msg
      :resp (->resp :status 500 :body resp-msg)})))


;; ---- general
(defn uuid []
  (UUID/randomUUID))


(defn format-uuid [^UUID uuid]
  (-> uuid .toString (.replace "-" "")))


(defn parse-uuid [^String uuid-str]
  (when (some? uuid-str)
    (try
      (-> (Hex/decodeHex uuid-str)
          ((fn [^"[B" buf]
            (if (not (= 16 (alength buf)))
              (throw (Exception. "invalid uuid"))
              buf)))
          (ByteBuffer/wrap)
          ((fn [^ByteBuffer buf]
             (UUID. (.getLong buf) (.getLong buf)))))
      (catch Exception e
        (t/error e)
        (throw (Exception. "Invalid uuid"))))))


(defn token->path [^String token]
  (let [upload-dir-name (config/v :upload-dir :default "uploads")
        upload-path (-> (io/file upload-dir-name)
                        .toPath
                        .toAbsolutePath)]
    (if (.exists (.toFile upload-path))
      (-> (.resolve upload-path token) .toString)
      (throw (Exception. (str "upload dir does not exist: " upload-path))))))


(defn item->file [item]
  (let [item-id (:id item)
        file-path (-> item :token format-uuid token->path)
        file (io/file file-path)]
    (if-not (.exists file)
      (ex-not-found! :e-msg (format "backing file (%s) does not exist for item %s"
                                    file-path
                                    item-id))
      file)))


(defn kebab->under [s]
  (string/replace s "-" "_"))


(defn under->kebab [s]
  (string/replace s "_" "-"))


(defn pad-vec
  ([coll size] (pad-vec coll size nil))
  ([coll size pad]
   (as-> coll v
         (concat v (repeat pad))
         (take size v)
         (vec v))))
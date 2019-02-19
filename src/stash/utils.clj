(ns stash.utils
  (:import [java.util UUID]
           [java.nio ByteBuffer]
           [java.nio.file Path]
           [org.apache.commons.codec.binary Hex])
  (:require [taoensso.timbre :as t]
            [clojure.java.io :as io]
            [cheshire.core :refer [parse-string generate-string]
                           :rename {parse-string s->map
                                    generate-string  map->s}]))


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
(defn ex-invalid-request
  [& {:keys [e-msg resp-msg] :or {e-msg "Missing request access token"
                                  reps-msg "expected access token header"}}]
  (ex-info e-msg
           {:type :invalid-request
            :msg e-msg
            :resp (->resp :status 400 :body e-msg)}))

(defn ex-not-found
  [& {:keys [e-msg resp-msg] :or {e-msg "item not found"
                                  resp-msg "item not found"}}]
  (ex-info e-msg
           {:type :invalid-request
            :msg e-msg
            :resp (->resp :status 404 :body resp-msg)}))


(defn ex-does-not-exist [record-type]
  (let [msg (format "Record %s does not exist" record-type)]
    (throw
      (ex-info
        msg
        {:type :does-not-exist
         :cause record-type
         :msg msg
         :resp (->resp :status 404 :body "item not found")}))))


;; ---- assertions
(defn assert-has-all [coll keys]
  (let [presence (map #(some? (% coll)) keys)
        missing-keys (->> (map vector keys presence)
                          (filter (fn [[_ flag]] (false? flag)))
                          (map first)
                          (vector))]
    (when-not (every? true? presence)
      (throw (ex-info "Some expected keys are missing"
                      {:type :missing-keys
                       :cause :models-insert
                       :msg (format "missing expected keys: %s" missing-keys)
                       :resp (->resp :status 500 :body "internal error")})))))


;; ---- general
(defn assert-file-exists [item]
  (let [item-id (:id item)
        file-path (:path item)
        file (io/file file-path)]
    (if-not (.exists file)
      (ex-not-found :e-msg (format "backing file (%s) does not exist for item %s"
                                   file-path
                                   item-id)))))

(defn get-config
  "Get a config value from system properties or the environment"
  [prop & {:keys [default]
           :or {default nil}}]
  (if-let [v (System/getProperty prop)]
    v
    (if-let [v (System/getenv prop)]
      v
      default)))


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


(defn new-upload-path [^String token]
  (let [upload-dir-name (get-config "UPLOAD_DIR" :default "uploads")
        upload-path (-> (io/file upload-dir-name)
                        .toPath
                        .toAbsolutePath)]
    (if (.exists (.toFile upload-path))
      (-> (.resolve upload-path token) .toString)
      (throw (Exception. (str "upload dir does not exist: " upload-path))))))



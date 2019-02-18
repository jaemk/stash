(ns stash.utils
  (:import [java.util UUID]
           [java.nio ByteBuffer]
           [org.apache.commons.codec.binary Hex])
  (:require [taoensso.timbre :as t]))


(defn mapply [func mapping]
  (apply func (apply concat mapping)))


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
          ((fn [buf]
            (if (not (= 16 (alength buf)))
              (throw (Exception. "invalid uuid"))
              buf)))
          (ByteBuffer/wrap)
          ((fn [^ByteBuffer buf]
             (t/infof "buf cap %s" (.capacity buf))
             (UUID. (.getLong buf) (.getLong buf)))))
      (catch Exception e
        (t/error e)
        (throw (Exception. "Invalid uuid"))))))



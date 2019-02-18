(ns stash.utils
  (:import [java.util UUID]
           [java.nio ByteBuffer]
           [org.apache.commons.codec.binary Hex]))


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
  (-> (Hex/decodeHex uuid-str)
      (ByteBuffer/wrap)
      ((fn [^ByteBuffer buf]
         (UUID. (.getLong buf) (.getLong buf))))))


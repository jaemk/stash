(ns stash.config)


(defn env [k]
  (System/getenv k))

(defn prop [k]
  (System/getenv k))


(def values
  (delay
    {:upload-dir (env "STASH_UPLOAD_DIR")
     :db-host (env "DATABASE_HOST")
     :db-port (env "DATABASE_PORT")
     :db-name (env "DATABASE_NAME")
     :db-user (env "DATABASE_USER")
     :db-password (env "DATABASE_PASSWORD")}))



(defn v
  [k & {:keys [default]
        :or {default nil}}]
  (if-let [value (get @values k)]
    value
    default))

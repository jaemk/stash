(ns stash.config)


(defn env [k & {:keys [default parse]
                :or {default nil
                     parse identity}}]
  (if-let [value (System/getenv k)]
    (parse value)
    default))


(def ^:const app-version
  (-> "project.clj" slurp read-string (nth 2)))


(def num-cpus (.availableProcessors (Runtime/getRuntime)))

(defn parse-int [s] (Integer/parseInt s))
(defn parse-bool [s] (Boolean/parseBoolean s))

(def values
  (delay
    {:upload-dir (env "STASH_UPLOAD_DIR")
     :db-host (env "DATABASE_HOST")
     :db-port (env "DATABASE_PORT")
     :db-name (env "DATABASE_NAME")
     :db-user (env "DATABASE_USER")
     :db-password (env "DATABASE_PASSWORD")
     :app-port (env "PORT" :default 3003 :parse parse-int)
     :repl-port (env "REPL_PORT" :default 3999 :parse parse-int)
     :repl-public (env "REPL_PUBLIC" :default false :parse parse-bool)
     :num-cpus num-cpus
     :num-threads (* num-cpus
                     (env "THREAD_MUTIPLIER" :default 8 :parse parse-int))}))



(defn v
  [k & {:keys [default]
        :or {default nil}}]
  (if-let [value (get @values k)]
    value
    default))

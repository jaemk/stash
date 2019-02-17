(ns stash.utils)


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


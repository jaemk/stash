(ns stash.database
  (:require [stash.config :as config]
            [hikari-cp.core :refer [make-datasource]]
            [stash.execution :as ex]))


(def db-config
  {:adapter           "postgresql"
   :username          (config/v :db-user)
   :password          (config/v :db-pass)
   :database-name     (config/v :db-name)
   :server-name       (config/v :db-host)
   :port-number       (config/v :db-port)
   :maximum-pool-size (max 10 ex/num-threads)})

(defonce datasource (delay (make-datasource db-config)))

(defn conn [] {:datasource @datasource})

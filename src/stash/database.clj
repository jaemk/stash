(ns stash.database
  (:require [stash.config :as config])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))


(def db-config
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname (format "//%s:%s/%s"
                    (config/v :db-host)
                    (config/v :db-port)
                    (config/v :db-name))
   :user (config/v :db-user)
   :password (config/v :db-password)})


(defn make-pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60)))]
    {:datasource cpds}))


(def pool (delay (make-pool db-config)))
(defn conn [] @pool)

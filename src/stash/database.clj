(ns stash.database
  (:require [stash.utils :refer [get-config]])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))


(def config
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname (format "//%s:%s/%s"
                    (get-config "DATABASE_HOST")
                    (get-config "DATABASE_PORT")
                    (get-config "DATABASE_NAME"))
   :user (get-config "DATABASE_USER")
   :password (get-config "DATABASE_PASSWORD")})


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


(def pool (delay (make-pool config)))
(defn conn [] @pool)

(ns stash.commands.core
  (:require [stash.utils :as u]
            [stash.database.core :as db]
            [clojure.java.jdbc :as j]
            [clojure.core.match :refer [match]]
            [honeysql.core :as sql]
            [migratus.core :as migratus]))


(defn list-users []
  (letfn [(display [row]
            (println
              (:name row)
              (-> row :token u/format-uuid)))]
    (map display (db/select-users (db/conn)))))


(defn add-user [name']
  (-> (db/create-user (db/conn) name')
      ((fn [{:keys [user auth]}]
         (println
           (:name user)
           (-> auth :token u/format-uuid))))))


(defn pending-migrations []
  (migratus/pending-list (db/migration-config)))

(defn applied-migrations []
  (migratus/completed-list (db/migration-config)))

(defn init-migrations! []
  (migratus/init (db/migration-config)))

(defn create-migration! [name']
  (migratus/create (db/migration-config) name'))

(defn migrate! []
  (migratus/migrate (db/migration-config)))

(defn rollback! [& args]
  (match
    (u/pad-vec args 1)
    [nil] (migratus/rollback (db/migration-config))
    [:all] (loop [applied (applied-migrations)]
             (when (not-empty applied)
               (rollback!)
               (recur (applied-migrations))))
    [k] (u/ex-error! (format "unknown op %s" k))))


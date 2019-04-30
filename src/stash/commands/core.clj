(ns stash.commands.core
  (:require [stash.utils :as u]
            [stash.database.core :as db]
            [clojure.java.jdbc :as j]
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


(defn migrate []
  (migratus/migrate (db/migration-config)))

(defn pending-migrations []
  (migratus/pending-list (db/migration-config)))

(defn completed-migrations []
  (migratus/completed-list (db/migration-config)))




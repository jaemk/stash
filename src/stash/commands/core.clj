(ns stash.commands.core
  (:require [stash.utils :as u]
            [clojure.java.jdbc :as j]
            [honeysql.core :as sql]
            [stash.database.core :as db]))


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




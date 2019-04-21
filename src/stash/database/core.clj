(ns stash.database.core
  (:require [stash.config :as config]
            [hikari-cp.core :refer [make-datasource]]
            [clojure.string :as s]
            [clojure.java.jdbc :as j]
            [stash.utils :as u]
            [taoensso.timbre :as t])
  (:import (clojure.lang Keyword)
           (org.postgresql.util PGobject)
           (java.sql ResultSetMetaData)))


; ----- datasource config ------
(def db-config
  {:adapter           "postgresql"
   :username          (config/v :db-user)
   :password          (config/v :db-pass)
   :database-name     (config/v :db-name)
   :server-name       (config/v :db-host)
   :port-number       (config/v :db-port)
   :maximum-pool-size (max 10 (config/v :num-threads))})

(defonce datasource (delay (make-datasource db-config)))

(defn conn [] {:datasource @datasource})


; ----- postgres/jdbc extensions ------
(defn kw->pg-enum [kw]
  "Converts a namespaced keyword to a jdbc/postgres enum"
  (let [type (-> (namespace kw)
                 (s/replace "-" "_"))
        value (name kw)]
    (doto (PGobject.)
      (.setType type)
      (.setValue value))))


(extend-type Keyword
  j/ISQLValue
  (sql-value [kw]
    "Extends keywords to be auto-converted to postgres enums"
    (kw->pg-enum kw)))


(def +schema-enums+
  "A set of all PostgreSQL enums in schema.sql. Used to convert
  enum-values back into namespaced keywords."
  #{"access_kind"})


(extend-type String
  j/IResultSetReadColumn
  (result-set-read-column [val
                           ^ResultSetMetaData rsmeta
                           idx]
    "Hook in enum->keyword conversion for all registered `schema-enums`"
    (let [type (.getColumnTypeName rsmeta idx)]
      (if (contains? +schema-enums+ type)
        (keyword (s/replace type "_" "-") val)
        val))))


; ----- helpers ------
(defn first-or-err [ty]
  "create a fn for retrieving a single row or throwing an error"
  (fn [result-set]
    (if-let [one (first result-set)]
      one
      (u/ex-does-not-exist! ty))))


; ----- database queries ------
(defn get-auth-by-token [conn auth-token]
  (t/infof "loading auth token %s" auth-token)
  (j/query conn ["select * from auth_tokens where token = ?" auth-token]
           {:result-set-fn (first-or-err :db-get/auth)}))


(defn list-users [conn]
  (let [display (fn [row] (println (:name row) (-> row :token u/format-uuid)))]
    (j/query conn [(str "select u.name, auth.token from users u "
                        "  join auth_tokens auth on u.id = auth.user_id")]
             {:row-fn display})))


(defn create-item [conn data]
  (u/assert-has-all data [:token :name :creator_id])
  (j/insert! conn :items data
             {:result-set-fn (first-or-err :db-get/item)}))


(defn count-items [conn]
  (j/query conn ["select count(*) from items"]
           {:row-fn :count
            :result-set-fn (first-or-err :db-count/item)}))


(defn update-item-size [conn item-id size]
  (j/update! conn :items {:size size} ["id = ?" item-id]))


(defn get-item-by-tokens [conn stash-token supplied-token request-user-token]
  (t/infof "loading item %s" (u/format-uuid stash-token))
  (j/query conn
           [(str
              "select items.* from items"
              " inner join auth_tokens auth on auth.user_id = items.creator_id"
              " where items.token = ?"
              "   and items.name = ?"
              "   and auth.token = ?")
            stash-token
            supplied-token
            request-user-token]
           {:result-set-fn (first-or-err :db-get/item)}))


(defn delete-item-by-id [conn id]
  (t/infof "deleting item %s" id)
  (j/delete! conn :items ["items.id = ?" id]
             {:result-set-fn first}))


(defn create-access [conn data]
  (u/assert-has-all data [:item_id :user_id :kind])
  (let [{item :item_id
         user_id :user_id
         kind :kind}        data]
    (j/query conn [(str "insert into access (item_id, user_id, kind) values (?, ?, ?)"
                        "  returning *")
                   item user_id kind]
             {:result-set-fn (first-or-err :db-get/access)})))

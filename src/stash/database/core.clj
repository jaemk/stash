(ns stash.database.core
  (:require [stash.config :as config]
            [clojure.spec.alpha :as s]
            [hikari-cp.core :refer [make-datasource]]
            [clojure.string :as str]
            [clojure.java.jdbc :as j]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
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


; ----- postgres/jdbc/honeysql extensions ------
(defn kw->pg-enum [kw]
  "Converts a namespaced keyword to a jdbc/postgres enum"
  (let [type (-> (namespace kw)
                 (str/replace "-" "_"))
        value (name kw)]
    (doto (PGobject.)
      (.setType type)
      (.setValue value))))


(extend-type Keyword
  j/ISQLValue
  (sql-value [kw]
    "Extends keywords to be auto-converted by jdbc to postgres enums"
    (kw->pg-enum kw)))


(defn kw-to-sql [kw]
  "Copy of honeysql's internal Keyword to-sql functionality"
  (let [s (name kw)]
    (case (.charAt s 0)
      \% (let [call-args (str/split (subs s 1) #"\." 2)]
           (honeysql.format/to-sql (apply honeysql.types/call (map keyword call-args))))
      \? (honeysql.format/to-sql (honeysql.types/param (keyword (subs s 1))))
      (honeysql.format/quote-identifier kw))))

(extend-protocol honeysql.format/ToSql
  Keyword
  (to-sql [kw]
    "Extends honeysql to convert namespaced keywords to pg enums"
    (let [type (namespace kw)]
      (if (nil? type)
        (kw-to-sql kw) ;; do default honeysql conversions
        (let [type (str/replace type "-" "_")
              enum-value (format "'%s'::%s" (name kw) type)]
          enum-value)))))


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
        (keyword (str/replace type "_" "-") val)
        val))))


; ----- helpers ------
(defn first-or-err [ty]
  "create a fn for retrieving a single row or throwing an error"
  (fn [result-set]
    (if-let [one (first result-set)]
      one
      (u/ex-does-not-exist! ty))))


(defn not-nil-pluck [rs]
  "Asserts the result-set, `rs` has something in it and then
  plucks the first item if the result-set is a seq of one item"
  (when (or (nil? rs)
            (empty? rs))
    (u/ex-error!
      (format "Expected a result returned from database query, found %s" rs)))
  (let [[head tail] [(first rs) (rest rs)]]
    (if (empty? tail)
      head
      rs)))
    

(defn insert! [conn stmt]
  "Executes insert statement returning a single map if
  the insert result is a seq of one item"
  (j/execute! conn stmt
              {:return-keys true
               :result-set-fn not-nil-pluck}))


; ----- database queries ------
(defn create-user [conn name']
  (j/with-db-connection [trans conn]
   (let [user (insert! trans
                       (-> (h/insert-into :users)
                           (h/values [{:name name'}])
                           sql/format))
         user-id (:id user)
         uuid (u/uuid)
         auth (insert! trans
                       (-> (h/insert-into :auth_tokens)
                           (h/values [{:user_id user-id
                                       :token uuid}])
                           sql/format))]
     {:user user :auth auth})))


(defn get-auth-by-token [conn auth-token]
  (t/infof "loading auth token %s" auth-token)
  (j/query conn
           (-> (h/select :*)
               (h/from :auth_tokens)
               (h/where [:= :token auth-token])
               sql/format)
           {:result-set-fn (first-or-err :db-get/auth)}))


(defn select-users [conn & {:keys [where] :or {where nil}}]
  (j/query conn
           (-> (h/select :u.name :auth.token)
               (h/from [:users :u])
               (h/where where)
               (h/join [:auth_tokens :auth] [:= :u.id :auth.user_id])
               sql/format)))


(defn create-item [conn data]
  (u/assert-has-all data [:token :name :creator_id])
  (insert! conn
           (-> (h/insert-into :items)
               (h/values [data])
               sql/format)))


(defn count-items [conn]
  (j/query conn
           (-> (h/select :%count.*)
               (h/from :items)
               sql/format)
           {:row-fn :count
            :result-set-fn (first-or-err :db-count/item)}))


(defn update-item-size [conn item-id size]
  (j/execute! conn
              (-> (h/update :items)
                  (h/sset {:size size})
                  (h/where [:= :id item-id])
                  sql/format)
              {:return-keys true}))


(defn get-item-by-tokens [conn stash-token supplied-token request-user-token]
  (t/infof "loading item %s" (u/format-uuid stash-token))
  (j/query conn
           (-> (h/select :items.*)
               (h/from :items)
               (h/join [:auth_tokens :auth] [:= :auth.user_id :items.creator_id])
               (h/where [:= :items.token stash-token]
                        [:= :items.name supplied-token]
                        [:= :auth.token request-user-token])
               sql/format)
           {:result-set-fn (first-or-err :db-get/item)}))


(defn delete-item-by-id [conn id]
  (t/infof "deleting item %s" id)
  (j/execute! conn
              (-> (h/delete-from :items)
                  (h/where [:= :items.id id])
                  sql/format)
              {:return-keys true
               :result-set-fn first}))


(def access-variants #{"create" "retrieve" "delete"})
(defn access-kw? [kw]
  (and (= (namespace kw) "access-kind")
       (contains? access-variants (name kw))))

(s/def ::id int?)
(s/def ::item_id int?)
(s/def ::user_id int?)
(s/def ::kind access-kw?)
(s/def ::created (java.util.Date.))
(s/def ::access-record (s/keys :req-un [::item_id ::user_id ::kind]))
(s/def ::access-record-full (s/keys :req-un [::id ::item_id ::user_id ::kind ::created]))

(defn create-access [conn data]
  (s/assert ::access-record data)
  (insert! conn
           (-> (h/insert-into :access)
               (h/values [data])
               sql/format)))

(s/fdef create-access
        :args (s/cat :conn some? :data ::access-record)
        :ret ::access-record-full
        :fn (fn [{:keys [args ret]}]
              (not (nil? ret))))

(defn select-access [conn kind]
  (j/query conn
           (-> (h/select :*)
               (h/from :access)
               (h/where [:= :kind kind])
               sql/format)
           {:return-keys true}))

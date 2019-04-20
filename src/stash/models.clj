(ns stash.models
  (:require [taoensso.timbre :as t]
            [clojure.java.jdbc :as j]
            [clojure.java.io :as io]
            [stash.config :as config]
            [stash.utils :as u]
            [clojure.string :as s])
  (:import (org.postgresql.util PGobject)
           (clojure.lang Keyword)))


(defn kw->pgenum [kw]
  (let [type (-> (namespace kw)
                 (s/replace "-" "_"))
        value (name kw)]
    (doto (PGobject.)
      (.setType type)
      (.setValue value))))

(extend-type Keyword
  j/ISQLValue
  (sql-value [kw]
    (kw->pgenum kw)))

(def +schema-enums+
  "A set of all PostgreSQL enums in schema.sql. Used to convert
  enum-values back into Clojure keywords."
  #{"access_kind"})

(extend-type String
  j/IResultSetReadColumn
  (result-set-read-column [val rsmeta idx]
    (let [type (.getColumnTypeName rsmeta idx)]
      (if (contains? +schema-enums+ type)
        (keyword (s/replace type "_" "-") val)
        val))))


(defn first-or-err [ty]
  (fn [result-set]
    (if-let [one (first result-set)]
      one
      (u/ex-does-not-exist! ty))))


(defn get-auth-by-token [conn auth-token]
  (t/infof "loading auth token %s" auth-token)
  (j/query conn ["select * from auth_tokens where token = ?" auth-token]
           {:result-set-fn (first-or-err :models-get/Auth)}))


(defn list-users [conn]
  (let [display (fn [row] (println (:name row) (-> row :token u/format-uuid)))]
    (j/query conn [(str "select u.name, auth.token from users u "
                        "  join auth_tokens auth on u.id = auth.user_id")]
             {:row-fn display})))


(defn create-item [conn data]
  (u/assert-has-all data [:token :name :creator_id])
  (j/insert! conn :items data
             {:result-set-fn (first-or-err :models-get/Item)}))


(defn count-items [conn]
  (j/query conn ["select count(*) from items"]
           {:row-fn :count
            :result-set-fn (first-or-err :models-count/Item)}))


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
           {:result-set-fn (first-or-err :models-get/Item)}))


(defn delete-item-by-id [conn id]
  (t/infof "deleting item %s" id)
  (j/delete! conn :items ["items.id = ?" id]
             {:result-set-fn first}))


(defn token->path [^String token]
  (let [upload-dir-name (config/v :upload-dir :default "uploads")
        upload-path (-> (io/file upload-dir-name)
                        .toPath
                        .toAbsolutePath)]
    (if (.exists (.toFile upload-path))
      (-> (.resolve upload-path token) .toString)
      (throw (Exception. (str "upload dir does not exist: " upload-path))))))


(defn item->file [item]
  (let [item-id (:id item)
        file-path (-> item :token u/format-uuid token->path)
        file (io/file file-path)]
    (if-not (.exists file)
      (u/ex-not-found! :e-msg (format "backing file (%s) does not exist for item %s"
                                      file-path
                                      item-id))
      file)))


(defn create-access [conn data]
  (u/assert-has-all data [:item_id :user_id :kind])
  (let [{item :item_id
         user_id :user_id
         kind :kind}        data]
    (j/query conn [(str "insert into access (item_id, user_id, kind) values (?, ?, ?)"
                        "  returning *")
                   item user_id kind]
                {:result-set-fn (first-or-err :models-get/access)})))

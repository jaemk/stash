(ns stash.models
  (:require [taoensso.timbre :as t]
            [clojure.java.jdbc :as j]
            [clojure.java.io :as io]
            [stash.config :as config]
            [stash.utils :as u]))



(defn first-or-err [ty]
  (fn [result-set]
    (if-let [one (first result-set)]
      one
      (u/ex-does-not-exist! ty))))


(defrecord Auth
  [id
   user_id
   token
   created])

(defn get-auth-by-token [conn auth-token]
  (t/infof "loading auth token %s" auth-token)
  (j/query conn ["select * from auth_tokens where token = ?" auth-token]
           {:row-fn map->Auth
            :result-set-fn (first-or-err :models-get/Auth)}))


(defn list-users [conn]
  (let [display (fn [row] (println (:name row) (-> row :token u/format-uuid)))]
    (j/query conn [(str "select u.name, auth.token from users u "
                        "  join auth_tokens auth on u.id = auth.user_id")]
             {:row-fn display})))


(defrecord Item
  [id
   size
   token
   name
   hash
   creator_id
   created
   expires_at])

(defn create-item [conn data]
  (u/assert-has-all data [:token :name :creator_id])
  (j/insert! conn :items data
             {:row-fn map->Item
              :result-set-fn (first-or-err :models-get/Item)}))

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
           {:row-fn map->Item
            :result-set-fn (first-or-err :models-get/Item)}))

(defn delete-item-by-id [conn id]
  (t/infof "deleting item %s" id)
  (j/delete! conn :items ["items.id = ?" id]
             {:row-fn #(= 1 %)
              :result-set-fn first}))

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


(defn access-kind->s [k]
  (if-let [s ({:create "create"
               :retrieve "retrieve"
               :delete "delete"} k)]
    s
    (u/ex-error! (format "Invalid access_kind: %s" k))))

(defn s->access-kind [s]
  (if-let [k ({"create" :create
               "retrieve" :retrieve
               "delete" :delete} s)]
    k
    (u/ex-error! (format "Invalid access_kind: %s" s))))

(defn convert-row-with-enum
  "Create a row-fn that can convert postgres enum strings to keywords
   and then converts the row to a model record"
  [field s->enum map->model]
  (fn [row]
    (-> (update row field s->enum)
        (map->model))))


(defrecord Access
  [id
   item
   user_id
   kind
   created])

(defn create-access [conn data]
  (u/assert-has-all data [:item_id :user_id :kind])
  (let [{item :item_id
         user_id :user_id
         kind :kind}        data
        kind-str (access-kind->s kind)]
    (j/query conn [(str "insert into access (item_id, user_id, kind) values (?, ?, ?::access_kind)"
                        "  returning *")
                   item user_id kind-str]
                {:row-fn (convert-row-with-enum :kind s->access-kind map->Access)
                 :result-set-fn (first-or-err :models-get/Access)})))


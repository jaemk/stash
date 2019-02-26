(ns stash.models
  (:require [taoensso.timbre :as t]
            [clojure.java.jdbc :as j]
            [stash.utils :as u]))



(defn first-or-err [ty result-set]
  (if-let [one (first result-set)]
    one
    (u/ex-does-not-exist! ty)))


(defrecord Auth
  [id
   app_user
   token
   created])

(defn get-auth-by-token [conn auth-token]
  (t/infof "loading auth token %s" auth-token)
  (j/query conn ["select * from auth_tokens where token = ?" auth-token]
           {:row-fn map->Auth
            :result-set-fn #(first-or-err :models-get/Auth %)}))


(defn list-users [conn]
  (let [display (fn [row] (println (:name row) (-> row :token u/format-uuid)))]
    (j/query conn [(str "select u.name, auth.token from app_users u "
                        "  join auth_tokens auth on u.id = auth.app_user")]
             {:row-fn display})))


(defrecord Item
  [id
   size
   path
   stash_token
   supplied_token
   content_hash
   creator
   created
   expires_at])

(defn create-item [conn data]
  (u/assert-has-all data [:path :stash_token :supplied_token :creator])
  (j/insert! conn :items data
             {:row-fn map->Item
              :result-set-fn #(first-or-err :models-get/Item %)}))

(defn count-items [conn]
  (j/query conn ["select count(*) from items"]
           {:row-fn :count
            :result-set-fn #(first-or-err :models-count/Item %)}))

(defn update-item-size [conn item-id size]
  (j/update! conn :items {:size size} ["id = ?" item-id]))

(defn get-item-by-tokens [conn stash-token supplied-token request-user-token]
  (t/infof "loading item %s" stash-token)
  (j/query conn
           [(str
              "select items.* from items"
              " inner join auth_tokens auth on auth.app_user = items.creator"
              " where items.stash_token = ?"
              "   and items.supplied_token = ?"
              "   and auth.token = ?")
            stash-token
            supplied-token
            request-user-token]
           {:row-fn map->Item
            :result-set-fn #(first-or-err :models-get/Item %)}))

(defn delete-item-by-id [conn id]
  (t/infof "deleting item %s" id)
  (j/delete! conn :items ["items.id = ?" id]
             {:row-fn #(= 1 %)
              :result-set-fn first}))


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
   app_user
   kind
   created])

(defn create-access [conn data]
  (u/assert-has-all data [:item :app_user :kind])
  (let [{item :item
         app_user :app_user
         kind :kind}        data
        kind-str (access-kind->s kind)]
    (j/query conn [(str "insert into access (item, app_user, kind) values (?, ?, ?::access_kind)"
                        "  returning *")
                   item app_user kind-str]
                {:row-fn (convert-row-with-enum :kind s->access-kind map->Access)
                 :result-set-fn #(first-or-err :models-get/Access %)
                 })))

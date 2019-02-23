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
  (t/info "fetching auth token")
  (j/query conn ["select * from auth_tokens where token = ?" auth-token]
           {:row-fn map->Auth
            :result-set-fn #(first-or-err :models-get/Auth %)}))



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

(defn update-item-size [conn item-id size]
  (j/update! conn :items {:size size} ["id = ?" item-id]))

(defn get-item-by-tokens [conn stash-token supplied-token request-user-token]
  (t/info "fetching item")
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
  (j/delete! conn :items ["id = ?" id]
             {:row-fn #(= 1 %)
              :result-set-fn first}))

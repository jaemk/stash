(ns stash.database-test
  (:use midje.sweet)
  (:require [stash.database.core :as db]
            [stash.utils :as u]
            [stash.test-utils :refer [setup-db
                                      teardown-db
                                      truncate-db]]))


(defonce test-db (atom nil))
(defonce state (atom {}))

(with-state-changes
  [(before :contents (do
                       (setup-db test-db)
                       (reset! state {})))
   (after :contents (teardown-db test-db))
   (before :facts (truncate-db test-db))]
  (facts
    (fact
      "we can create users and items"
      ; db is setup
      (nil? @test-db) => false

      ; create and save a user
      (db/create-user (:conn @test-db) "bean") =>
        (fn [result]
          (swap! state #(merge % result)) ; save the :user and :auth for later
          (and (= (-> result :user :name) "bean")
               (uuid? (-> result :auth :token))))

      ; query auth item
      (db/get-auth-by-token (:conn @test-db) (-> @state :auth :token)) =>
        (fn [auth]
          (= (-> @state :auth :id)
             (:id auth)))

      ; query user
      (db/select-users (:conn @test-db)) =>
        (fn [result]
          (and (= 1 (count result))
               (= (-> @state :user :name)
                  (-> (first result) :name))))

      ; create and save an item with our user
      (let [user-id (-> @state :user :id)]
        (db/create-item
          (:conn @test-db)
          {:token (u/uuid)
           :name "thing"
           :creator_id user-id})) =>
        (fn [item]
          (swap! state #(assoc % :item item)) ;; save :item for later
          (= "thing" (:name item)))

      ; confirm it's saved
      (db/count-items (:conn @test-db)) => 1

      ; pull the item
      (db/get-item-by-tokens
        (:conn @test-db)
        (-> @state :item :token)
        (-> @state :item :name)
        (-> @state :auth :token)) =>
        (fn [item]
          (and (= (-> @state :item :id)
                  (:id item))
               (= nil
                  (:size item))))

      ; update size
      (db/update-item-size (:conn @test-db)
                           (-> @state :item :id)
                           4392) =>
        (fn [item]
          (= 4392
             (:size item)))

      ; create an access/create record
      (db/create-access (:conn @test-db)
                        {:kind :access-kind/create
                         :user_id (-> @state :user :id)
                         :item_id (-> @state :item :id)}) => truthy

      ; now delete it
      (db/delete-item-by-id (:conn @test-db)
                            (-> @state :item :id)) =>
        (fn [deleted]
          (= (-> @state :item :id)
             (:id deleted)))

      ; confirm it's gone
      (db/count-items (:conn @test-db)) => 0

      ; access record is still around
      (db/select-access (:conn @test-db) :access-kind/create) =>
        (fn [access-items]
          (= 1
             (count access-items))))
    (fact
      "we can truncate the db"
      (db/select-users (:conn @test-db)) =>
        (fn [result]
          (empty? result)))))

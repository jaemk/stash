(ns stash.core-test
  (:use midje.sweet)
  (:require [stash.database.core :as db]
            [stash.utils :as u]
            [stash.test-utils :refer [setup-db
                                      teardown-db
                                      truncate-db]]))


(defonce test-db (atom nil))

(with-state-changes
  [(before :contents (setup-db test-db))
   (after :contents (teardown-db test-db))
   (before :facts (truncate-db test-db))]
  (facts
    "here we go"
    (fact
      "we can add"
      (+ 1 2) => 3
      (+ 2 3) => 5)

    (fact
      "we can do db things"
      (nil? @test-db) => false
      (db/create-user (:conn @test-db) "bean") =>
        (fn [result]
          (and (= (-> result :user :name) "bean")
               (some? (-> result :auth :token))))
      (db/select-users (:conn @test-db)) =>
        (fn [result]
          (and (= 1 (count result))
               (= "bean"
                  (-> (first result) :name))))
      (db/create-item
        (:conn @test-db)
        {:token (u/uuid)
         :name "thing"
         :creator_id (-> (db/select-users
                           (:conn @test-db)
                           :where [:= :name "bean"])
                         first
                         :id)}) =>
        (fn [result]
          (= "thing" (:name result))))

    (fact
      "we truncated the db"
      (db/select-users (:conn @test-db)) =>
        (fn [result]
          (empty? result)))))


(defonce test-db2 (atom nil))

(with-state-changes
  [(before :contents (setup-db test-db2))
   (after :contents (teardown-db test-db2))
   (before :facts (truncate-db test-db2))]
  (facts
    "we can do db things separately"
    (nil? @test-db2) => false
    (db/create-user (:conn @test-db2) "bean") =>
    (fn [result]
      (and (= (-> result :user :name) "bean")
           (some? (-> result :auth :token))))
    (db/select-users (:conn @test-db2)) =>
    (fn [result]
      (and (= 1 (count result))
           (= "bean"
              (-> (first result) :name))))
    (db/create-item
      (:conn @test-db2)
      {:token (u/uuid)
       :name "thing"
       :creator_id (-> (db/select-users
                         (:conn @test-db2)
                         :where [:= :name "bean"])
                       first
                       :id)}) =>
    (fn [result]
      (= "thing" (:name result)))))

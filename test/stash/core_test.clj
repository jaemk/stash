(ns stash.core-test
  (:use midje.sweet)
  (:require [stash.database.core :as db]
            [hikari-cp.core :refer [make-datasource
                                    close-datasource]]
            [clojure.java.jdbc :as jdbc]
            [stash.utils :as u]
            [migratus.core :as migratus]
            [taoensso.timbre :as t]))


(defonce test-db-name (atom nil))
(defonce test-db (atom nil))

(defn teardown-db []
  (swap! test-db
         (fn [db]
           (when (not (nil? db))
             (try
               (t/info "closing test db")
               (close-datasource (:datasource db))
               (catch Exception e
                 (t/warn "ignoring error" e))))
           nil))
  (swap! test-db-name
         (fn [db-name]
           (when (not (nil? db-name))
             (try
               (t/info "dropping test db")
               (jdbc/execute! (db/conn)
                              [(str "drop database " db-name)]
                              {:transaction? false})
               (catch Exception e
                 (t/warn "ignoring error" e))))

           nil)))

(defn setup-db []
  (teardown-db)
  (let [db-name (->> (u/uuid)
                     u/format-uuid
                     (str "stash_test_"))]
    (t/info "setting up test db" db-name)
    (jdbc/execute! (db/conn)
                   [(str "create database " db-name)]
                   {:transaction? false})
    (reset! test-db-name db-name)
    (let [ds (-> db/db-config
                 (assoc :database-name db-name)
                 (assoc :maximum-pool-size 1)
                 (make-datasource))
          conn {:datasource ds}
          config (db/migration-config conn)]
      (reset! test-db conn)
      (migratus/migrate config))))


(with-state-changes
  [(before :contents (setup-db))
   (after :contents (teardown-db))]
  (facts
    "here we go"
    (fact
      "we can add"
      (+ 1 2) => 3
      (+ 2 3) => 5)
    (fact
      "we can do db things"
      (nil? @test-db) => false
      (db/create-user @test-db "bean") =>
        (fn [a]
          (and (= (-> a :user :name) "bean")
               (some? (-> a :auth :token))))
      (db/select-users @test-db) =>
        (fn [a]
          (and (= 1 (count a))
               (= "bean"
                  (-> (first a) :name))))
      (db/create-item
        @test-db
        {:token (u/uuid)
         :name "thing"
         :creator_id (-> (db/select-users
                           @test-db
                           :where [:= :name "bean"])
                         first
                         :id)}) =>
        (fn [a]
          (t/warn a)
          (= "thing" (:name a))))))


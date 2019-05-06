(ns stash.types
  (:require [clojure.spec.alpha :as s])
  (:import (java.util Date UUID)
           (com.zaxxer.hikari HikariDataSource)))


(def kw-namespaces
  #{"access-kind"})

(defn registered-kw-namespace? [namespace']
  (contains? kw-namespaces namespace'))


(def access-variants #{"create" "retrieve" "delete"})
(defn access-kw? [kw]
  (and (= (namespace kw) "access-kind")
       (contains? access-variants (name kw))))

(defn date? [d]
  (instance? Date d))

(defn datasource? [ds]
  (instance? HikariDataSource ds))

(defn nullable [check]
  (fn [v]
    (or (nil? v)
        (check v))))

(s/def ::id int?)
(s/def ::size (nullable int?))
(s/def ::item_id int?)
(s/def ::user_id int?)
(s/def ::creator_id int?)

(s/def ::name string?)
(s/def ::hash string?)
(s/def ::registered-kw-namespace registered-kw-namespace?)
(s/def ::kind access-kw?)

(s/def ::token uuid?)
(s/def ::datasource datasource?)
(s/def ::created date?)
(s/def ::expires_at (nullable date?))

(s/def ::conn (s/keys :req-un [::datasource]))

(s/def ::user
  (s/keys :req-un [::id ::name ::created]))

(s/def ::auth
  (s/keys :req-un [::id ::user_id ::token ::created]))

(s/def ::item
  (s/keys :req-un [::id ::size ::token ::name ::creator_id ::created ::expires_at]))
(s/def ::item-min
  (s/keys :req-un [::name ::token ::creator_id]))

(s/def ::access-record-min
  (s/keys :req-un [::item_id ::user_id ::kind]))
(s/def ::access-record
  (s/keys :req-un [::id ::item_id ::user_id ::kind ::created]))

(s/def ::where map?)
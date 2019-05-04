(ns stash.types
  (:require [clojure.spec.alpha :as s])
  (:import (java.util Date)))


(def kw-namespaces
  #{"access-kind"})

(defn registered-kw-namespace? [namespace']
  (contains? kw-namespaces namespace'))


(def access-variants #{"create" "retrieve" "delete"})
(defn access-kw? [kw]
  (and (= (namespace kw) "access-kind")
       (contains? access-variants (name kw))))

(s/def ::id int?)
(s/def ::item_id int?)
(s/def ::user_id int?)
(s/def ::registered-kw-namespace registered-kw-namespace?)
(s/def ::kind access-kw?)
(s/def ::created #(instance? Date %))

(s/def ::access-record
  (s/keys :req-un [::item_id ::user_id ::kind]))
(s/def ::access-record-full
  (s/keys :req-un [::id ::item_id ::user_id ::kind ::created]))

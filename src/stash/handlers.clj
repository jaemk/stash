(ns stash.handlers
  (:import [java.io File]
           [io.netty.buffer PooledSlicedByteBuf])
  (:require [taoensso.timbre :as t]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [aleph.http :as http]
            [byte-streams :as bs]
            [stash.execution :as ex]
            [stash.database :as db]
            [stash.utils :as u :refer [->resp ->text ->json]]
            [stash.models :as m]
            [clojure.java.jdbc :as j]
            [clojure.java.io :as io]
            [cheshire.core :refer [parse-string generate-string]
                           :rename {parse-string s->map
                                    generate-string  map->s}]))


(defn index [req]
  (->text "hello"))


(defn get-request-user-token [req]
  (->
    (:headers req)
    (get "x-stash-access-token")
    ((fn [token]
       (if (nil? token)
         (u/ex-unauthorized!
           :e-msg "missing access token"
           :resp-msg "missing access token")
         token)))))


(defn stream-to-file [src file]
  (d/future-with
    ex/pool
    (with-open [file-stream (io/output-stream file)]
      (io/copy src file-stream))))


(defn create [req]
  (let [token (u/uuid)
        token-str (u/format-uuid token)
        upload-path (u/new-upload-path token-str)
        upload-file (io/file upload-path)

        supplied-token (-> req :params :supplied-token)
        user-auth-token (-> (get-request-user-token req) u/parse-uuid)

        size (atom 0)
        data-stream (s/stream 2048)
        connect-fn (fn [^PooledSlicedByteBuf buf]
                     (let [n_bytes (.capacity buf)]
                       (swap! size #(+ % n_bytes))
                       (s/put! data-stream buf)))
        _ (s/connect-via (:body req) connect-fn data-stream)
        body (bs/to-input-stream data-stream)]
    (d/chain
      (d/future-with
        ex/pool
        (j/with-db-transaction [conn (db/conn)]
          (let [auth (m/get-auth-by-token conn user-auth-token)]
            (m/create-item conn {:path upload-path
                                 :stash_token token
                                 :supplied_token supplied-token
                                 :creator (:app_user auth)}))))
      (fn [item]
        (t/infof "uploading item %s to %s" (:id item) upload-path)
        (d/chain
          (stream-to-file body upload-file)
          (fn [_]
            (d/future-with
              ex/pool
              (j/with-db-transaction [conn (db/conn)]
                (m/update-item-size conn (:id item) @size))
              (t/infof "finished item %s upload of %s bytes" (:id item) @size)
              (->json {:ok :ok
                       :size @size
                       :stash_token (-> (:stash_token item)
                                        u/format-uuid)}))))))))


(defn get-stash-token [req]
  (-> req
      :body
      (bs/to-string)
      (s->map)
      (get "stash_token")
      ((fn [token]
         (if (nil? token)
           (u/ex-invalid-request!
             :e-msg "missing stash token"
             :resp-msg "missing stash token")
           token)))))


(defn retrieve [req]
  (let [supplied-token (-> req :params :supplied-token)
        request-user-token (-> (get-request-user-token req) u/parse-uuid)
        stash-token (-> (get-stash-token req) u/parse-uuid)]
    (d/chain
      (d/future-with
        ex/pool
        (j/with-db-transaction [conn (db/conn)]
          (m/get-item-by-tokens conn stash-token supplied-token request-user-token)))
      (fn [item]
        (u/assert-item-file-exists item)
        (->resp
          :headers {"content-type" "application/octet-stream"}
          :body (io/file (:path item)))))))


(defn delete [req]
  (let [supplied-token (-> req :params :supplied-token)
        request-user-token (-> (get-request-user-token req) u/parse-uuid)
        stash-token (-> (get-stash-token req) u/parse-uuid)]
    (d/chain
      (d/future-with
        ex/pool
        (j/with-db-transaction [conn (db/conn)]
          (let [item (m/get-item-by-tokens conn stash-token supplied-token request-user-token)
                item-deleted (m/delete-item-by-id conn (:id item))
                _ (if-not item-deleted
                    (u/ex-error! "Failed deleting database item"))
                ^File file (u/assert-item-file-exists item)
                file-deleted (.delete file)
                _ (if-not file-deleted
                    (u/ex-error! "Failed deleting item backing file"))]
            (->json {:ok :ok})))))))

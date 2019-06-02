(ns stash.handlers
  (:import [java.io File]
           [io.netty.buffer PooledSlicedByteBuf])
  (:require [taoensso.timbre :as t]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [byte-streams :as bs]
            [clojure.java.jdbc :as j]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [stash.execution :as ex]
            [stash.database.core :as db]
            [stash.utils :as u :refer [->resp ->text ->json]]
            [stash.config :as config]))


(defn index [_]
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



(defn create [req]
  (let [token (u/uuid)
        token-str (u/format-uuid token)
        upload-path (token->path token-str)
        upload-file (io/file upload-path)

        name (-> req :params :name)
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
          (let [auth (db/get-auth-by-token conn user-auth-token)
                item (db/create-item conn {:token token
                                           :name name
                                           :creator_id (:user_id auth)})]
            {:auth auth
             :item item})))
      (fn [{auth :auth
            item :item}]
        (t/info "uploading item"
                {:item-id (:id item)
                 :upload-path upload-path})
        (d/chain
          (stream-to-file body upload-file)
          (fn [_]
            (d/future-with
              ex/pool
              (j/with-db-transaction [conn (db/conn)]
                (db/update-item-size conn (:id item) @size)
                (db/create-access conn {:item_id (:id item)
                                        :user_id (:user_id auth)
                                        :kind :access-kind/create}))
              (t/info "finished item upload"
                      {:item-id (:id item)
                       :size-bytes @size})
              (->json {:ok :ok
                       :size @size
                       :stash_token (-> (:token item)
                                        u/format-uuid)}))))))))


(defn get-stash-token [req]
  (-> req
      :body
      (bs/to-string)
      (json/decode)
      (get "stash_token")
      ((fn [token]
         (if (nil? token)
           (u/ex-invalid-request!
             :e-msg "missing stash token"
             :resp-msg "missing stash token")
           token)))))


(defn retrieve [req]
  (let [name (-> req :params :name)
        request-user-token (-> (get-request-user-token req) u/parse-uuid)
        stash-token (-> (get-stash-token req) u/parse-uuid)]
    (d/chain
      (d/future-with
        ex/pool
        (j/with-db-transaction [conn (db/conn)]
          (let [item (db/get-item-by-tokens conn stash-token name request-user-token)]
            (db/create-access conn {:item_id (:id item)
                                    :user_id (:creator_id item)
                                    :kind :access-kind/retrieve})
            item)))
      (fn [item]
        (let [file (item->file item)]
          (->resp
            :headers {"content-type" "application/octet-stream"}
            :body file))))))


(defn delete [req]
  (let [name (-> req :params :name)
        request-user-token (-> (get-request-user-token req) u/parse-uuid)
        stash-token (-> (get-stash-token req) u/parse-uuid)]
    (d/chain
      (d/future-with
        ex/pool
        (j/with-db-transaction [conn (db/conn)]
          (let [item (db/get-item-by-tokens conn stash-token name request-user-token)
                item-deleted (db/delete-item-by-id conn (:id item))
                _ (db/create-access conn {:item_id (:id item)
                                          :user_id (:creator_id item)
                                          :kind :access-kind/delete})
                _ (if-not item-deleted
                    (u/ex-error! "Failed deleting database item"))
                ^File file (item->file item)
                file-deleted (.delete file)
                _ (if-not file-deleted
                    (u/ex-error! "Failed deleting item backing file"))]
            (->json {:ok :ok})))))))

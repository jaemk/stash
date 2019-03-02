(ns stash.core
  (:require [taoensso.timbre :as t]
            [clojure.string :as string]
            [manifold.deferred :as d]
            [aleph.http :as http]
            [ring.middleware.params :as params]
            [ring.util.request :as ring-request]
            [compojure.response :refer [Renderable]]
            [clojure.java.jdbc :as j]
            [stash.router :as router]
            [stash.utils :as u :refer [->resp]]
            [stash.models :as m]
            [stash.cli :as cli]
            [stash.config :as config]
            [stash.execution :as exec]
            [stash.database :as db])
  (:gen-class))


;; Setup stdout logging
(t/refer-timbre)
(t/set-level! :debug)


;; Make sure compojure passes through all
;; deferred objects to play nice with aleph
(extend-protocol Renderable
  manifold.deferred.IDeferred
  (render [d _] d))


(defn- wrap-deferred-request
  "Wrap deferred request with logging and error handling"
  [handler]
  (fn [request]
    (let [method (:request-method request)
          uri (:uri request)
          status (atom nil)
          start (:aleph/request-arrived request)]
      (-> request
        (d/chain
          handler
          (fn [resp]
            (reset! status (:status resp))
            resp))
        (d/catch
          Exception
          (fn [^Exception e]
            (let [info (ex-data e)]
              (if (nil? info)
                (do
                  (reset! status 500)
                  (t/error e)
                  (->resp :status 500 :body "Something went wrong"))
                (do
                  (reset! status (-> info :resp :status))
                  (t/errorf "%s %s" (:type info) (:msg info))
                  (:resp info))))))
        (d/finally
          (fn []
            (let [elap-ms (-> (System/nanoTime)
                              (- start)
                              (/ 1000000.))]
              (t/info method uri @status (str elap-ms "ms")))))))))


(defn- wrap-query-params
  [handler]
  (fn [request]
    (let [enc (or (ring-request/character-encoding request)
                  "UTF-8")
          request (params/assoc-query-params request enc)]
      (handler request))))


;; reloadable server
(defonce server (atom nil))


(defn- init-server
  "Initialize server with middleware"
  [opts]
  (let [app (-> (router/load-routes cli/APP_VERSION)
                wrap-query-params
                wrap-deferred-request)]
    (http/start-server app opts)))


(defn- start-server
  "Start a server on a given port"
  [port]
  (t/info "Starting server on port:" port)
  (let [s (init-server {:port port :raw-stream? true})]
    (t/info "Listening on port:" port)
    s))


(defn stop
  "Stop any running server"
  []
  (swap!
    server
    (fn [svr]
      (do
        (when (not (nil? svr))
          (do
            (.close svr)
            (t/info "server closed!")))
        svr))))


(defn start
  "Start server, closing any existing server if needed"
  ([] (start 3003))
  ([port]
   (if (nil? port)
     (start 3003)
     (do
       (reset! server (start-server port))))))

(defn restart
  []
  (stop)
  (start))


(defn reload
  "Reload a specific namespace"
  [-ns]
  (use -ns :reload))


(defn add-user [name-]
  (j/with-db-transaction [conn (db/conn)]
    (let [user (j/insert! conn :app_users {:name name-} {:result-set-fn first})
          user-id (:id user)
          _ (prn user-id)
          uuid (u/uuid)
          auth-token (j/insert! conn :auth_tokens {:app_user user-id
                                                   :token uuid})
          token-str (u/format-uuid uuid)]
      (println
        (format "Created user (%s) with auth token %s" user-id token-str)))))


(defn -main
  [& args]
  (let [{:keys [command opts msg ok?]} (cli/parse-args args)]
    (if msg
      (do
        (println msg)
        (System/exit (if ok? 0 1)))
      (case command
         "list-users" (m/list-users (db/conn))
         "add-user" (add-user (:name opts))
         "serve" (do
                   (t/infof "Current item count: %s" (m/count-items (db/conn)))
                   (t/infof "Using upload directory: %s" (config/v :upload-dir))
                   (t/infof "Using thread pool size: %s" exec/num-threads)
                   (start (:port opts)))))))

(ns stash.core
  (:require [taoensso.timbre :as t]
            [nrepl.server]
            [manifold.deferred :as d]
            [aleph.http :as http]
            [ring.middleware.params :as params]
            [ring.util.request :as ring-request]
            [compojure.response :refer [Renderable]]
            [clojure.java.jdbc :as j]
            [stash.router :as router]
            [stash.utils :as u :refer [->resp]]
            [stash.cli :as cli]
            [stash.config :as config]
            [stash.database.core :as db])
  (:gen-class))


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


;; reloadable servers
(defonce server (atom nil))
(defonce nrepl-server (atom nil))


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


(defn- start-repl
  [port & {:keys [public]}]
  (let [host (if (or public false) "0.0.0.0" "127.0.0.1")]
    (t/infof "Starting nrepl server on %s:%s" host port)
    (nrepl.server/start-server :bind host :port port)))


(defn- stop-repl
  [svr]
  (t/info "Stopping nrepl server")
  (nrepl.server/stop-server svr))


(defn stop
  "Stop any running server"
  []
  (do
    (swap!
      server
      (fn [svr]
        (do
          (when (not (nil? svr))
            (do
              (.close svr)
              (t/info "server closed!")))
          svr)))
    (swap!
      nrepl-server
      (fn [svr]
        (do
          (when (not (nil? svr))
            (do
              (stop-repl svr)
              (t/info "nrepl-server closed!"))))))))


(defn start
  "Start server, closing any existing server if needed"
  ([] (start (config/v :app-port) (config/v :repl-port)))
  ([port] (start port (config/v :repl-port)))
  ([port nrepl-port] (start port nrepl-port (config/v :repl-public)))
  ([port nrepl-port nrepl-public]
   (do
     (reset! nrepl-server (start-repl nrepl-port :public nrepl-public))
     (reset! server (start-server port)))))



(defn restart
  []
  (stop)
  (start))


(defn reload
  "Reload a specific namespace"
  [ns']
  (use ns' :reload))


(defn add-user [name-]
  (j/with-db-transaction [conn (db/conn)]
    (let [user (j/insert! conn :users {:name name-} {:result-set-fn first})
          user-id (:id user)
          _ (prn user-id)
          uuid (u/uuid)
          _ (j/insert! conn :auth_tokens {:user_id user-id
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
         "list-users" (db/list-users (db/conn))
         "add-user" (add-user (:name opts))
         (do
           (t/infof "Current item count: %s" (db/count-items (db/conn)))
           (t/infof "Using upload directory: %s" (config/v :upload-dir))
           (t/infof "Using thread pool size: %s" (config/v :num-threads))
           (start (:port opts)
                  (:repl-port opts)
                  (:repl-public opts)))))))

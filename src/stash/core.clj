(ns stash.core
  (:require [taoensso.timbre :as t]
            [clojure.tools.cli :refer [parse-opts]]
            [manifold.deferred :as d]
            [aleph.http :as http]
            [ring.middleware.params :as params]
            [ring.util.request :as ring-request]
            [compojure.response :refer [Renderable]]
            [clojure.java.jdbc :as j]
            [stash.router :as router]
            [stash.utils :as u :refer [->resp]]
            [stash.database :as db])
  (:gen-class))


;; Setup stdout logging
(t/refer-timbre)
(t/set-level! :debug)


;; initialize connection pool
(db/conn)


(def APP_VERSION
  (-> "project.clj" slurp read-string (nth 2)))


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
  (let [app (-> (router/load-routes APP_VERSION)
                wrap-query-params
                wrap-deferred-request)]
    (http/start-server app opts)))


(defn- start-server
  "Start a server on a given port"
  [port]
  (t/info "starting server on port:" port)
  (let [s (init-server {:port port :raw-stream? true})]
    (t/info "listening on port:" port)
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


(def cli
  [["-p" "--port PORT" "Port to listen on"
    :default 3003
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65536) "Must be 0..=65536"]]
   ["-n" "--name NAME" "Name to use when creating new user"
    :validate [#(not (empty? %)) "Name is required"]]
   ["-h" "--help"]])


(defn usage [opts]
  (->> [(format "Stash %s" APP_VERSION)
        ""
        "Usage: stash [options] command"
        ""
        "Options:"
        opts
        ""
        "Commands:"
        "  serve     Start server listening on PORT"
        "  add-user  Create a new user with NAME"]
       (clojure.string/join \newline)))


(defn has-required [command opts]
  (cond
    (and
      (= command "serve")
      (some? (:port opts)))   true
    (and
      (= command "add-user")
      (some? (:name opts)))   true
    :else                     false))


(defn parse-args [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli)]
    (cond
      (:help options)     {:msg (usage summary) :ok? true}
      errors              {:msg (str "Error:\n" (clojure.string/join \newline errors))}
      (and (= 1 (count arguments))
           (#{"serve" "add-user"} (first arguments))
           (has-required (first arguments) options))
                          {:command (first arguments) :opts options}
      :else               {:msg (usage summary)})))


(defn -main
  [& args]
  (let [{:keys [command opts msg ok?]} (parse-args args)]
    (if msg
      (do
        (println msg)
        (System/exit (if ok? 0 1)))
      (case command
         "serve" (start (:port opts))
         "add-user" (add-user (:name opts))))))

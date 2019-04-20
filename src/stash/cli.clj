(ns stash.cli
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]))


(def APP_VERSION
  (-> "project.clj" slurp read-string (nth 2)))


(def cli
  [["-p" "--port PORT" "Port to listen on"
    :default 3003
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65536) "Must be 0..=65536"]]
   ["-r" "--repl-port PORT" "Port to start a network repl on"
    :default nil
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65536) "Must be 0..=65536"]]
   [nil "--repl-public FLAG" "Whether to start a public network repl"
    :default "false"
    :parse-fn #(Boolean/parseBoolean %)]
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
        "  serve       Start server listening on PORT"
        "  list-users  list all users and their tokens"
        "  add-user    Create a new user with NAME"]
       (string/join \newline)))


(defn has-required [command opts]
  (cond
    (= command "list-users")  true
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
      errors              {:msg (str "Error:\n" (string/join \newline errors))}
      :else {:command (first arguments) :opts options})))

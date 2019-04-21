(ns stash.cli
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli-tools]))


(def APP_VERSION
  (-> "project.clj" slurp read-string (nth 2)))


(def cli-opts
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
   ["-h" "--help"]])


(defn usage [opts]
  (->> [(format "Stash %s" APP_VERSION)
        ""
        "Usage: stash [options]"
        ""
        "Options:"
        opts]
       (string/join \newline)))


(defn parse-args [args]
  (let [{:keys [options arguments errors summary]} (cli-tools/parse-opts args cli-opts)]
    (cond
      (:help options)     {:msg (usage summary) :ok? true}
      errors              {:msg (str "Error:\n" (string/join \newline errors))}
      :else {:command (first arguments) :opts options})))

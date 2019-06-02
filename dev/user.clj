(ns user
  (:require [stash.core :as app]
            [stash.database.core :as db]
            [stash.config :as config]
            [stash.utils :as u]
            [stash.types :as types]
            [stash.commands.core :as cmd])
  (:use [midje.repl]))


(defn -main []
  (app/-main))

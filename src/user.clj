(ns user)


(defn initenv []
  (require '[stash.core :as app]
           '[stash.utils :as u]
           '[stash.config :as config]
           '[stash.database.core :as db]
           '[stash.commands.core :as cmd]))


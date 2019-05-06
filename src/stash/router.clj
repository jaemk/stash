(ns stash.router
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [stash.utils :as u]
            [stash.handlers :as h]
            [stash.config :as config]))

(defn load-routes []
  (routes
    (ANY "/" [] h/index)
    (ANY "/status" [] (u/->json {:status :ok :version (config/v :app-version)}))
    (POST "/create/:name" _ h/create)
    (POST "/retrieve/:name" _ h/retrieve)
    (POST "/delete/:name" _ h/delete)
    (route/not-found (u/->resp
                       :body "nothing to see here"
                       :status 404))))

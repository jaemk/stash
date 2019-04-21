(ns stash.router
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [stash.utils :as u]
            [stash.handlers :as h]))

(defn load-routes [app-version]
  (routes
    (ANY "/" [] h/index)
    (ANY "/status" [] (u/->json {:status :ok :version app-version}))
    (POST "/create/:supplied-token" _ h/create)
    (POST "/retrieve/:supplied-token" _ h/retrieve)
    (POST "/delete/:supplied-token" _ h/delete)
    (route/not-found (u/->resp
                       :body "nothing to see here"
                       :status 404))))

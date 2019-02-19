(ns stash.router
  (:require [taoensso.timbre :as t]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [stash.utils :as u]
            [stash.handlers :as h]))

(defn load-routes [app-version]
  (routes
    (ANY "/" [] h/index)
    (ANY "/status" [] (u/->json {:status :ok :version app-version}))
    (POST "/create/:supplied-token" r h/create)
    (POST "/retrieve/:supplied-token" r h/retrieve)
    (route/not-found (u/->resp
                       :body "nothing to see here"
                       :status 404))))

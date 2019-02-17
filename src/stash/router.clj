(ns stash.router
  (:require [taoensso.timbre :as t]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [stash.handlers :as h]))

(defn load-routes []
  (routes
    (ANY "/" [] h/index)
    (ANY "/status" [] h/status)
    (POST "/create/:supplied-token" r h/create)
    (POST "/retrieve/:supplied-token" r h/retrieve)
    (route/not-found (h/->resp
                       :body "nothing to see here"
                       :status 404))))

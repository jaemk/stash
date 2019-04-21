(ns stash.execution
  (:require [manifold.executor :refer [fixed-thread-executor]]
            [stash.config :as config]))

(def pool (fixed-thread-executor (config/v :num-threads)))

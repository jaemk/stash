(ns stash.execution
  (:require [manifold.executor :refer [fixed-thread-executor]]))


(def num-cpus (.availableProcessors (Runtime/getRuntime)))
(def num-threads (* 8 num-cpus))

(def pool (fixed-thread-executor num-threads))

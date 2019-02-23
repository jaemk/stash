(ns stash.execution
  (:require [manifold.executor :refer [fixed-thread-executor]]))


(def num_cpus (.availableProcessors (Runtime/getRuntime)))
(def num_threads (* 8 num_cpus))

(def pool (fixed-thread-executor num_threads))

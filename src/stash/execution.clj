(ns stash.execution)


(def num_cpus (.availableProcessors (Runtime/getRuntime)))
(def num_threads (* 4 num_cpus))

(def cpu (manifold.executor/fixed-thread-executor num_threads))
(def fs (manifold.executor/fixed-thread-executor num_threads))

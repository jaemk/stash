(ns stash.execution)


(def num_cpus (.availableProcessors (Runtime/getRuntime)))
(def num_threads (* 8 num_cpus))

(def pool (manifold.executor/fixed-thread-executor num_threads))

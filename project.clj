(defproject stash "0.1.0"
  :description "Simple file storage system"
  :url "https://github.com/jaemk/stash"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.1"]
                 [org.clojure/tools.logging "0.4.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [aleph "0.4.6"]
                 [manifold "0.1.8"]
                 [byte-streams "0.2.4"]
                 [byte-transforms "0.1.4"]
                 [ring/ring-core "1.6.3"]
                 [compojure "1.6.1"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [com.mchange/c3p0 "0.9.5.3"]
                 [org.postgresql/postgresql "42.2.5"]
                 [commons-codec/commons-codec "1.11"]
                 [cheshire "5.8.0"]]
  :main ^:skip-aot stash.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

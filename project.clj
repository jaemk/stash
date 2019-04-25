(defproject stash "0.3.1"
  :description "Simple file storage system"
  :url "https://github.com/jaemk/stash"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [nrepl "0.6.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.slf4j/slf4j-simple "1.7.25"]
                 [aleph "0.4.6"]
                 [manifold "0.1.8"]
                 [byte-streams "0.2.4"]
                 [byte-transforms "0.1.4"]
                 [ring/ring-core "1.6.3"]
                 [compojure "1.6.1"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [hikari-cp "2.7.1"]
                 [org.postgresql/postgresql "42.2.5"]
                 [commons-codec/commons-codec "1.11"]
                 [cheshire "5.8.0"]]
  :main ^:skip-aot stash.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[lein-binplus "0.6.5"]]}}
  :bin {:name "stash"
        :bin-path "bin"
        :jvm-opts ["-server" "-Dfile.encoding=utf-8" "$JVM_OPTS"]
        :custom-preamble "#!/bin/sh\nexec java {{{jvm-opts}}} -jar $0 \"$@\"\n"})

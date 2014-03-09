(defproject dar/web "0.1.0-SNAPSHOT"
  :description "Minimalistic web framework"
  :url "http://github.com/dar-clojure/web"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [dar/async "0.1.0-SNAPSHOT"]
                 [dar/core "0.1.0-SNAPSHOT"]
                 [clj-stacktrace "0.2.7"]]
  :profiles {:dev {:dependencies [[ring/ring-jetty-adapter "1.2.1"]]
                   :source-paths ["example"]}})

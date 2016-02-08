(defproject com.greenyouse/plugin-helpers "0.1.5"
  :description "Helper fns for leiningen plugins"
  :url "http://github.com/greenyouse/plugin-helpers"
  :license {:name "Eclipse Public License",
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [leiningen-core "2.5.3"]
                 [rewrite-clj "0.4.12"]]

  :profiles {:dev {:plugins [[lein-codox "0.9.1"]]}}

  :codox {:source-uri "https://github.com/greenyouse/plugin-helpers/blob/master/{filepath}#L{line}"
          :include [plugin-helpers.core]})

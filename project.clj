(defproject chrome-clojurescript-repl "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [tailrecursion/cljson "1.0.7"]
                 [alandipert/storage-atom "1.2.4"]
                 [javax.xml.bind/jaxb-api "2.2.11"]
                 [com.sun.xml.bind/jaxb-core "2.2.11"]
                 [com.sun.xml.bind/jaxb-impl "2.2.11"]
                 [javax.activation/activation "1.1.1"]
                 ]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :node-dependencies [[source-map-support "0.3.2"]]
  :plugins [[lein-npm "0.5.0"]]
  :source-paths ["src" "target/classes"]
  :clean-targets ["out" "release"]
  :target-path "target")

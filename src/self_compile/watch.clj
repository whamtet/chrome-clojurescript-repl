(ns self-compile.watch)

(require '[cljs.build.api :as b])

(defn -main [& args]
  (b/watch "src"
           {:main 'planck.core
            :output-to "out/self_compile.js"
            :output-dir "out"
            :optimizations :whitespace
            }))

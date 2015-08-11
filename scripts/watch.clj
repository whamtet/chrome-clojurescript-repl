(require '[cljs.build.api :as b])

(b/watch "src"
  {:main 'self-compile.core
   :output-to "out/self_compile.js"
   :output-dir "out"
   :optimizations :whitespace
   })

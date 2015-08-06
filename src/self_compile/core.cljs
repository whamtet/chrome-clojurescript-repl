(ns self-compile.core
  (:require ;[clojure.browser.repl :as repl]
            cljs.js
            )
  (:import goog.net.Jsonp)
  )

(enable-console-print!)
(set! cljs.js/*eval-fn* cljs.js/js-eval)

(def state (cljs.js/empty-state))

(defn cb [line cb2]
  (cljs.js/eval-str state line #(-> % :value cb2)))

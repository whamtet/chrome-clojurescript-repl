(ns self-compile.core
  (:require ;[clojure.browser.repl :as repl]
   cljs.js
   cljs.reader
   )
  (:import goog.net.Jsonp)
  )

(enable-console-print!)
(set! cljs.js/*eval-fn* cljs.js/js-eval)

(def state (cljs.js/empty-state))

(defn cb [line cb2]
  (cljs.js/eval-str state line
                    (fn [response]
                      (cb2
                       (or
                        (if-let [value (:value response)]
                          (pr-str value))
                        (if (contains? response :value) line)
                        (when-let [error (:error response)]
                          (str "Error: " (str error)))
                        (pr-str response))))))

(defn t []
  (pr-str @state))

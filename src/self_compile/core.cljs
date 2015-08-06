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

(defn print-through [x] (println x) x)

(defn cb [line cb2]
  (cljs.js/eval-str state line
                    (fn [response]
                      (println response)
                      (cb2
                       (or
                        (:value response)
                        (if (contains? response :value)
                          (try
                            (cljs.reader/read-string line)
                            line
                            (catch js/Error e)))
                        (when-let [error (:error response)]
                          (println response)
                          (str "Error: " (str error)))
                        (pr-str response))))))

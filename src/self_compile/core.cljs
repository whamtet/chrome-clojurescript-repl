(ns self-compile.core
  (:require ;[clojure.browser.repl :as repl]
   cljs.js
   [cljs.reader :as r]
   )
  (:import goog.net.Jsonp)
  )

(enable-console-print!)
(set! cljs.js/*eval-fn* cljs.js/js-eval)
(def state (cljs.js/empty-state))

(defn print-though [x] (println "okok" (= nil x)) x)

(defn safe-read [s]
  (try
    (r/read-string s)
    (catch :default e
      (prn e)
      )))

(defn define-macro [line]
  (cljs.js/require
   {:*compiler* state
    :*load-fn* (fn [_ cb]
                 (println "loading")
                 (cb {:lang :clj :source (str "(ns cljs.user)\n" line)}))
    }
    'poo
    false ;reload
    {:macros-ns true}
    (fn [res]
      (println "require result:" res))))


(def precompile? '#{defmacro})
(js/eval "cljs.user = {}")

(defn cb [line repl-callback]
  (let [
        [type ns :as x] (safe-read line)
        ]
    (cond
     (= type 'defmacro) (do
                          (define-macro line)
                          (str ns))
     :default
     (print-though
     (cljs.js/eval-str state line
                       (fn [response]
                         (repl-callback
                          (or
                           (if-let [value (:value response)]
                             (pr-str value))
                           (if (contains? response :value) line)
                           (when-let [error (:error response)]
                             (str "Error: " (str error)))
                           (pr-str response)))))))))

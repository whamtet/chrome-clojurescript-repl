(ns planck.core
  (:require-macros [cljs.env.macros :refer [with-compiler-env]]
                   )
  (:require [cljs.js :as cljs]
            [cljs.tagged-literals :as tags]
            [cljs.tools.reader :as r]
            [cljs.analyzer :as ana]
            [cljs.repl :as repl]
            [cljs.stacktrace :as st]
            [cljs.source-map :as sm]
            [tailrecursion.cljson :refer [cljson->clj]]
            [alandipert.storage-atom :as storage-atom]
            ))

(enable-console-print!)

(defonce st (cljs/empty-state))

(defonce current-ns (atom 'cljs.user))

(defonce app-env (atom nil))

(defn map-keys [f m]
  (reduce-kv (fn [r k v] (assoc r (f k) v)) {} m))

(defn ^:export init-app-env [app-env]
  (reset! planck.core/app-env (map-keys keyword (cljs.core/js->clj app-env))))

(defn repl-read-string [line]
  (r/read-string {:read-cond :allow :features #{:cljs}} line))

(defn ^:export is-readable? [line]
  (binding [r/*data-readers* tags/*cljs-data-readers*]
    (try
      (repl-read-string line)
      true
      (catch :default _
        false))))

(defn ns-form? [form]
  (and (seq? form) (= 'ns (first form))))

(def repl-specials '#{in-ns require require-macros doc defmacro})

(defn repl-special? [form]
  (and (seq? form) (repl-specials (first form))))

(defn in-target? [form]
  (prn ["b" form])
  (and (seq? form) (= 'inject (first form))))

(def repl-special-doc-map
  '{in-ns          {:arglists ([name])
                    :doc      "Sets *cljs-ns* to the namespace named by the symbol, creating it if needed."}
    require        {:arglists ([& args])
                    :doc      "Loads libs, skipping any that are already loaded."}
    require-macros {:arglists ([& args])
                    :doc      "Similar to the require REPL special function but\n  only for macros."}
    doc            {:arglists ([name])
                    :doc      "Prints documentation for a var or special form given its name"}})

(defn- repl-special-doc [name-symbol]
  (assoc (repl-special-doc-map name-symbol)
    :name name-symbol
    :repl-special-function true))

(defn resolve
  "Given an analysis environment resolve a var. Analogous to
  clojure.core/resolve"
  [env sym]
  {:pre [(map? env) (symbol? sym)]}
  (try
    (ana/resolve-var env sym
                     (ana/confirm-var-exists-throw))
    (catch :default _
      (ana/resolve-macro-var env sym))))

(defn ^:export get-current-ns []
  (str @current-ns))

(defn completion-candidates-for-ns [ns-sym allow-private?]
  (map (comp str key)
       (filter (if allow-private?
                 identity
                 #(not (:private (:meta (val %)))))
               (apply merge
                      ((juxt :defs :macros)
                       (get (:cljs.analyzer/namespaces @planck.core/st) ns-sym))))))

(defn is-completion? [buffer-match-suffix candidate]
  (re-find (js/RegExp. (str "^" buffer-match-suffix)) candidate))

(defn ^:export get-completions [buffer]
  (let [namespace-candidates (map str
                                  (keys (:cljs.analyzer/namespaces @planck.core/st)))
        top-form? (re-find #"^\s*\(\s*[^()\s]*$" buffer)
        typed-ns (second (re-find #"(\b[a-zA-Z-.]+)/[a-zA-Z-]+$" buffer))
        all-candidates (set (if typed-ns
                              (completion-candidates-for-ns (symbol typed-ns) false)
                              (concat namespace-candidates
                                      (completion-candidates-for-ns 'cljs.core false)
                                      (completion-candidates-for-ns @current-ns true)
                                      (when top-form? (map str repl-specials)))))]
    (let [buffer-match-suffix (re-find #"[a-zA-Z-]*$" buffer)
          buffer-prefix (subs buffer 0 (- (count buffer) (count buffer-match-suffix)))]
      (clj->js (if (= "" buffer-match-suffix)
                 []
                 (map #(str buffer-prefix %)
                      (sort
                       (filter (partial is-completion? buffer-match-suffix)
                               all-candidates))))))))

(defn extension->lang [extension]
  (if (= ".js" extension)
    :js
    :clj))

(def banned #{'clojure.string})

#_(defn load [sym {:keys [name macros path] :as full} cb]
;  (println "looad" name macros path (type name))
  (js/$.get (str "http://localhost:5000/?path=" path "&name=" name "&sym=" sym)
            #(cb (repl-read-string %))))

(defn require [macros-ns? sym reload]
  (cljs.js/require
   {:*compiler*     st
    :*data-readers* tags/*cljs-data-readers*
    :*load-fn*      load
    :*eval-fn*      cljs/js-eval}
   sym
   reload
   {:macros-ns  macros-ns?
    :verbose    false
    :source-map true}
   (fn [res]
     (println "require result:" res))))

(defn define-macro [source]
  (cljs.js/require
   {:*compiler*     st
    :*data-readers* tags/*cljs-data-readers*
    :*load-fn*      (fn [_ cb]
                      (cb {:lang :clj
                           :source ;"(ns repl.macros) (defmacro m [] 3)"
                           (str "(ns repl.macros) " source)
                           }))
    :*eval-fn*      cljs/js-eval}
   'repl.macros
   true
   {:macros-ns  true
    :verbose    (:verbose @app-env)
    :source-map true
    }
   (fn [res]
     (println "require result:" res))))

(defn require-destructure [macros-ns? args]
  (let [[[_ sym] reload] args]
    (require macros-ns? sym reload)))

(defn- canonicalize-specs [specs]
  (letfn [(canonicalize [quoted-spec-or-kw]
                        (if (keyword? quoted-spec-or-kw)
                          quoted-spec-or-kw
                          (as-> (second quoted-spec-or-kw) spec
                                (if (vector? spec) spec [spec]))))]
    (map canonicalize specs)))

(defn- process-reloads! [specs]
  (if-let [k (some #{:reload :reload-all} specs)]
    (let [specs (->> specs (remove #{k}))]
      (if (= k :reload-all)
        (reset! cljs.js/*loaded* #{})
        (apply swap! cljs.js/*loaded* disj (map first specs)))
      specs)
    specs))

(defn- self-require? [specs]
  (some
   (fn [quoted-spec-or-kw]
     (and (not (keyword? quoted-spec-or-kw))
          (let [spec (second quoted-spec-or-kw)
                ns (if (sequential? spec)
                     (first spec)
                     spec)]
            (= ns @current-ns))))
   specs))

(defn- process-require
  [macros-ns? cb specs]
  (try
    (let [is-self-require? (self-require? specs)
          [target-ns restore-ns]
          (if-not is-self-require?
            [@current-ns nil]
            ['cljs.user @current-ns])
          pr-cb #(cb (pr-str %))
          require-sym (rand-int 1e10)
          ]
      (cljs/eval
       st
       (let [ns-form `(~'ns ~target-ns
                            (~(if macros-ns?
                                :require-macros :require)
                              ~@(-> specs canonicalize-specs process-reloads!)))]
         (when (:verbose @app-env)
           (println "Implementing"
                    (if macros-ns?
                      "require-macros"
                      "require")
                    "via ns:\n  "
                    (pr-str ns-form)))
         ns-form)
       {:ns      @current-ns
        :context :expr
        :verbose (:verbose @app-env)
        :load #(load require-sym %1 %2)
        :eval
        (fn [{:keys [source]}]
          (let [
                source2 (str (insertion-code) source)
                ]
            (try
              (if js/chrome.devtools
                (js/chrome.devtools.inspectedWindow.eval
                 source2
                 (fn [res err]
                   (if-not res
                     (println err))))
                (js/eval source))
              (catch :default e (println e)))))
        }
       (fn [{e :error}]
         (when is-self-require?
           (reset! current-ns restore-ns))
         (when e
           (println e))
         )))
    (catch :default e
      (println e))))


(defn ^:export run-main [main-ns args]
  (let [main-args (js->clj args)]
    (require false (symbol main-ns) nil)
    (cljs/eval-str st
                   (str "(var -main)")
                   nil
                   {:ns         (symbol main-ns)
                    :load       load
                    :eval       cljs/js-eval
                    :source-map true
                    :context    :expr}
                   (fn [{:keys [ns value error] :as ret}]
                     (apply value args)))
    nil))

(defn load-core-source-maps! []
  (when-not (get (:source-maps @planck.core/st) 'planck.core)
    (swap! st update-in [:source-maps] merge {'planck.core
                                              (sm/decode
                                               (cljson->clj
                                                (js/PLANCK_LOAD "planck/core.js.map")))
                                              'cljs.core
                                              (sm/decode
                                               (cljson->clj
                                                (js/PLANCK_LOAD "cljs/core.js.map")))})))

(defn print-error [error]
  (let [cause (or (.-cause error) error)]
    (println (.-message cause))
    ;    (load-core-source-maps!)
    (let [canonical-stacktrace (st/parse-stacktrace
                                {}
                                (.-stack cause)
                                {:ua-product :safari}
                                {:output-dir "file://(/goog/..)?"})]
      (println
       (st/mapped-stacktrace-str
        canonical-stacktrace
        (or (:source-maps @planck.core/st) {})
        nil)))))

#_(defn print-error [error]
    (throw error))

;;insert the bitch
(def code-injected? (atom false))
(if js/chrome.devtools
  (js/chrome.devtools.network.onNavigated.addListener #(reset! code-injected? false)))
(if (and js/$ js/$.get) (js/$.get "/out/self_compile.js" #(def src %)))

(defn insertion-code []
  (when-not @code-injected?
    (reset! code-injected? true)
    (str "
         if (!window.cljs) {"
         src
         "}
         ")))

(defn ^:export load-js
  ([]
   (load-js "cljs_server.core"))
  ([root]
   (load-js "https://localhost:8000" root))
  ([server root]
   (let [
         url (str server "?root=" root)
         s (js/document.createElement "script")]
     (set! (.-src s) url)
     (js/document.head.appendChild s))))

(defn ^:export inject-js [url]
  (let [
        s (js/document.createElement "script")]
    (set! (.-src s) url)
    (js/document.head.appendChild s)))

(defn ^:export read-eval-print [source cb]
  (let [
        pr-cb #(cb (pr-str %))
        ]
    (try
      (binding [ana/*cljs-ns* @current-ns
                *ns* (create-ns @current-ns)
                r/*data-readers* tags/*cljs-data-readers*]
        (let [
              expression-form (repl-read-string source)
              ]
          (if (repl-special? expression-form)
            (let [env (assoc
                        (ana/empty-env)
                        :context :expr
                        :ns {:name @current-ns})]
              (cb "")
              (case (first expression-form)
                defmacro (define-macro source)
                in-ns (reset! current-ns (second (second expression-form)))
                require (process-require false cb (rest expression-form))
                require-macros (process-require true cb (rest expression-form))
                doc (if (repl-specials (second expression-form))
                      (repl/print-doc (repl-special-doc (second expression-form)))
                      (repl/print-doc
                       (let [sym (second expression-form)]
                         (with-compiler-env st (resolve env sym)))))))
            (let [
                  expression-form (cond
                                   (= 'load expression-form) `(js/planck.core.load-js)
                                   (and (seq? expression-form)
                                        (= 'load (first expression-form)))
                                   `(js/planck.core.load-js ~@(rest expression-form))
                                   (= 'inject-js expression-form)
                                   `(js/planck.core.inject-js ~(js/chrome.extension.getURL "/jquery-2.1.1.min.js"))
                                   :default `(pr-str ~expression-form))
                  ]
              (cljs/eval
               st
               expression-form
               {:ns         @current-ns
                :load       load
                :eval       (fn [{:keys [source]}]
                              (let [
                                    source2 (str (insertion-code) source)
                                    ]
                                (try
                                  (if js/chrome.devtools
                                    (js/chrome.devtools.inspectedWindow.eval
                                     source2
                                     (fn [res err]
                                       (if res
                                         (cb res)
                                         (pr-cb err))))
                                    (cb (js/eval source)))
                                  (catch :default e (pr-cb e)))))
                :source-map false
                :verbose    (:verbose @app-env)
                :context       :expr
                :def-emits-var true
                }
               (fn [{:keys [ns value error] :as ret}]
                 (when error
                   (pr-cb error))))))))
      (catch :default e (pr-cb e)))))

(def history-atom (storage-atom/local-storage (atom [])))
(def get-history #(clj->js @history-atom))
(defn notify-push [line]
  (swap! history-atom
         #(let [
                updated (conj % line)
                new-len (count updated)
                max-len 10
                ]
            (if (< max-len new-len)
              (subvec updated (- new-len max-len))
              updated))))

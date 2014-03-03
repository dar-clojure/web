(ns easy-app.web.router
  (:require [clojure.string :as string]))

(defprotocol IRoute
  (match [this ^String uri req])
  (gen-url [this name opts]))

(defrecord Router [routes]
  IRoute
  (match [_ uri req] (->> routes
                          (map #(match % uri req))
                          (some identity)))

  (gen-url [_ name opts] (->> routes
                              (map #(gen-url % name opts))
                              (some identity))))

(def EMPTY (->Router []))

(defn add-route [router route]
  (update-in router [:routes] conj route))

(defrecord PrefixedRoute [route prefix]
  IRoute
  (match [_ uri req] (when (.startsWith uri prefix)
                       (match route (subs uri (.length prefix)) req)))

  (gen-url [_ name opts] (when-let [url (gen-url route name opts)]
                           (str prefix url))))

(defn set-prefix [route prefix]
  (->PrefixedRoute route prefix))

(defrecord PreFilteredRoute [route pred]
  IRoute
  (match [_ uri req] (when (pred uri req)
                       (match route uri req)))

  (gen-url [_ name opts] (gen-url route name opts)))

(defn set-pre-condition [route pred]
  (->PreFilteredRoute route pred))

(defn- escape-regex [s]
  (string/escape s #(when (>= (.indexOf "\\.*+|?()[]{}$^" (str %)) 0)
                      (str \\ %))))

(defn- url-encode [s]
  (java.net.URLEncoder/encode s "UTF-8"))

(defn- url-decode [s]
  (java.net.URLDecoder/decode s "UTF-8"))

(def ^:private re-url-param #"\{([^\}]+)\}")

(defn- compile-pattern [s]
  (let [parts (string/split s re-url-param -1)
        params (mapv #(-> % second keyword) (re-seq re-url-param s))
        pattern (->> parts (interpose "([^/]+)") (apply str))]
    [(re-pattern (str "^" pattern "$")) params parts]))

(defn- match-regex [uri regex params]
  (when-let [m (re-find regex uri)]
    (zipmap params (->> m next (map url-decode)))))

(defrecord RegexRoute [name regex params parts]
  IRoute
  (match [_ uri req] (when-let [m (match-regex uri regex params)]
                       (merge req m {:route name})))

  (gen-url [_ name* opts] (when (= name* name)
                            (apply str
                                   (->> parts
                                        (interpose :param)
                                        (map-indexed (fn [i p]
                                                       (if (= p :param)
                                                         (url-encode (str (get opts (nth params (dec i)))))
                                                         p))))))))

(defn- get-or-head? [_ {m :request-method}]
  (or (= :get m)
      (= :head m)))

(defn make-route
  ([name method url]
   (-> (apply ->RegexRoute name (compile-pattern url))
       (#(condp = method
          :all %
          :get (set-pre-condition % get-or-head?)
          (set-pre-condition % (fn [_ req]
                                 (= method (:request-method req)))))))))

;;
;; Implicit namespace bound router
;;

(defn- var-get* [ns var-name]
  (let [s (symbol (name ns) (name var-name))]
    (when-let [var (find-var s)]
      (var-get var))))

(defn- get-ns-router*
  ([]
   (get-ns-router* (ns-name *ns*)))
  ([ns]
   (var-get* ns '*easy-app-router*)))

(defn get-ns-router [& args]
  (when-let [r (apply get-ns-router* args)]
    @r))

(defn load-ns-router [ns]
  (require ns)
  (get-ns-router ns))

(defn declare-router []
  (when-not (get-ns-router)
    (.setDynamic (intern *ns*
                         (with-meta '*easy-app-router* {:private true})
                         (atom EMPTY)))))

;;
;; DSL
;;

(require '[easy-app.core :as app])

(defn defroute
  ([route]
   (declare-router)
   (swap! (get-ns-router*) add-route route))
  ([method url & args]
   (let [name (if (even? (count args))
                (gensym url)
                (first args))
         opts (if (even? (count args))
                args
                (next args))
         route (make-route name method url)]
     (when (some #{:fn} args)
       (apply app/define name opts))
     (defroute route))))

(defmacro defzone [url & body]
  `(do (declare-router)
     (defroute (binding [~'*easy-app-router* (atom EMPTY)]
                 ~@body
                 (set-prefix ~url ~'*easy-app-router*)))))

(defn- upper-case-first [s]
  (apply str (first (string/upper-case s)) (next s)))

(defn gen-defroute-for-method [m]
  `(defn ~(-> (name m) upper-case-first symbol) [& args#]
     (apply defroute ~m args#)))

(defmacro gen-defroutes []
  `(do ~@(map gen-defroute-for-method
              [:get
               :head
               :post
               :put
               :delete])))

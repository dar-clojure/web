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
                       (assoc req
                         :params m
                         :route name)))

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
   (-> (apply ->RegexRoute name (compile-pattern pattern))
       #(condp = method
          :all %
          :get (set-pre-condition % get-or-head?)
          (set-pre-condition % (fn [_ req]
                                 (= method (:request-method req))))))))

(ns easy-app.web
  (:require [clojure.core.async :refer [<!]]
            [easy-app.core :as co :refer [define go* <?]]
            [easy-app.web.router :as router]
            [clj-stacktrace.repl :refer [pst-on pst-str]])
  (:import (java.lang Throwable)))

;;
;; Routing
;;

(def defroute router/defroute)

(defmacro defzone [url & body]
  `(router/defzone url ~@body))

(router/gen-defroutes)

;;
;; Actions
;;

(defrecord Action [name args])

(defn action [name & args]
  (->Action name args))

(defn ex-action [& args]
  (ex-info (apply action args)))

(defn action? [obj]
  (instance? Action obj))

;;
;; Dispatch
;;

(defn- do-action [app {:keys [name args]}]
  (go*
    (let [res (<? (co/eval app name))]
      (if (fn? res)
        (if (:async (meta res))
          (<? (apply res args))
          (apply res args))
        res))))

(defn dispatch [app act]
  (go*
    (loop [act act i 1]
      (when (> i 100) ;; TODO: revisit this
        (throw (ex-info "Max actions count (100) exceeded" {::last-action act})))
      (let [res (<! (do-action app act))
            i (inc i)]
        (cond (action? res) (recur res i)
              (action? (ex-data res)) (recur (ex-data res) i)
              :else res)))))

(defn dispatch-on [app level params act]
  (go*
    (let [app (co/start app level params)]
      (try
        (<? (dispatch app act))
        (finally
          (co/stop! app))))))

;;
;; Http server
;;

(defn request-handler
  ([app]
   (fn [req]
     (dispatch app (action :proc-http-request req))))
  ([]
   (let [routes (or (router/get-ns-router) router/EMPTY)
         spec (merge (co/get-ns-spec 'easy-app.web)
                     {:http-router routes}
                     (co/get-ns-spec))]
     (request-handler (co/make* spec)))))

(defn wrap-stacktrace [handler]
  (fn [req]
    (go*
      (try
        (<? (handler req))
        (catch Throwable ex
          (pst-on *err* false ex)
          {:status 500
           :headers {"content-type" "text/plain; charset=UTF-8"}
           :body (str "500 Internal Server Error\n\n" (pst-str ex))})))))

(defn dev-request-handler [& args]
  (wrap-stacktrace (apply request-handler args)))

;;
;; Base/default app setup
;;

(define :proc-http-request
  :level :app
  :args [:http-router ::co/self]
  :fn (fn [routes app]
        (with-meta
          (fn [req]
            (let [req (or (router/match routes req) req)]
              (dispatch-on app :request {:http-req req}
                           (action (get req :route :http-404))))
          {:async true}))))

(define :http-404
  {:status 404
   :body "404 Not Found"})

(defn send
  ([body]
   (send 200 body))
  ([status body]
   (send status body {}))
  ([status body headers]
   {:status status :headers headers :body body}))

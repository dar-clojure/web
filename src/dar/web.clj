(ns dar.web
  (:refer-clojure :exclude [send])
  (:require [dar.core :as App :refer [define]]
            [dar.async :refer :all]
            [dar.web.router :as router]
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
  (go
    (let [res (<? (App/eval app name))]
      (if (fn? res)
        (<? (apply res args))
        res))))

(defn dispatch [app act]
  (go
    (loop [act act i 1]
      (when (> i 100) ;; TODO: revisit this
        (throw (ex-info "Max actions count (100) exceeded" {::last-action act})))
      (let [res (<! (do-action app act))
            i (inc i)]
        (cond (action? res) (recur res i)
              (action? (ex-data res)) (recur (ex-data res) i)
              :else res)))))

(defn dispatch-on [app level params act]
  (go
    (let [app (App/start app level params)]
      (try
        (<? (dispatch app act))
        (finally
          (App/stop! app))))))

(defn dispatch-http-request [app req]
  (dispatch app (action :proc-http-request req)))

;;
;; Base/default HTTP
;;

(define :proc-http-request
  :level :app
  :args [:http-router ::App/self]
  :fn (fn [routes app]
        (fn [req]
          (let [req (or (router/match routes (:uri req) req) req)]
            (dispatch-on app :request {:http-req req}
                         (action (get req :route :http-404)))))))

(define :http-send
  :args [:http-req]
  :fn (fn [req]
        (fn [status headers body]
          {:status status :headers headers :body body})))

(defn send
  ([body]
   (send 200 body))
  ([status body]
   (send status body {}))
  ([status body headers]
   (action :http-send status headers body)))

(define :http-404
  (send 404 "404 Not Found"))

;;
;; Request handler creation
;;

(defn request-handler
  ([ns]
   (let [routes (or (router/get-ns-router ns) router/EMPTY)
         spec (merge (App/get-ns-spec 'dar.web)
                     {:http-router routes}
                     (App/get-ns-spec ns))
         app (App/make* spec)]
     (fn [req]
       (dispatch-http-request app req))))
  ([]
   (request-handler (ns-name *ns*))))

(defn wrap-stacktrace [handler]
  (fn [req]
    (go
      (try
        (<? (handler req))
        (catch Throwable ex
          (pst-on *err* false ex)
          {:status 500
           :headers {"content-type" "text/plain; charset=UTF-8"}
           :body (str "500 Internal Server Error\n\n" (pst-str ex))})))))

(defn dev-request-handler [& args]
  (wrap-stacktrace (apply request-handler args)))

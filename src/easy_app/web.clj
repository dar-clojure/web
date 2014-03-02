(ns easy-app.web
  (:require [clojure.core.async :refer [<!]]
            [easy-app.core :as co :refer [define go* <?]]
            [easy-app.web.router :as router])
  (:import (java.lang Throwable)))

;;
;; Routing
;;

(def defroute router/defroute)

(defmacro at [url & body]
  `(~@(apply router/at* url body)))

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
        (apply res args)
        res))))

(defn- dispatch-action [app act]
  (go*
    (loop [act act i 1]
      (when (> i 100) ;; TODO: revisit this
        (throw (ex-info "Max actions count (100) exceeded" {::last-action act})))
      (let [res (<! (do-action app act))
            i (inc i)]
        (cond (action? res) (recur res i)
              (action? (ex-data res)) (recur (ex-data res) i)
              :else res)))))

(defn dispatch [app req]
  (dispatch-action (co/start app :request {:http-req req})
                   (action :http-res)))

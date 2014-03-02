(ns easy-app.web
  (:require [easy-app.core :as app :refer [define]]
            [easy-app.web.router :as router]))

(def defroute router/defroute)

(defmacro at [url & body]
  `(~@(apply router/at* url body)))

(router/gen-defroutes)

(ns easy-app.web.server.jetty
  (:require [easy-app.web.server :refer :all]
            [easy-app.core :refer [<?!]]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn run [handler opts]
  (let [on-request #(<?! (handler %))
        opts (merge opts {:join? false})
        server (run-jetty on-request opts)]
    (reify
      IServer
      (stop! [_] (.stop server))
      (provider [_] server))))

(ns dar.web.server.jetty
  (:require [dar.web.server :refer :all]
            [dar.core :refer [<?!]]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn run [handler opts]
  (let [on-request #(<?! (handler %))
        opts (merge opts {:join? false})
        server (run-jetty on-request opts)]
    (reify
      IServer
      (stop! [_] (.stop server))
      (provider [_] server))))

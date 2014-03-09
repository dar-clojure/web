(ns hello-world
  (:refer-clojure :exclude [send])
  (:require [dar.web :as web :refer [Get send]]
            [dar.web.server.jetty :as jetty]))

(Get "/"
  :fn #(send "Hello world!"))

(Get "/{greeting}"
  :args [:http-req]
  :fn (fn [{g :greeting}]
        (send (str g " world!"))))

(defn -main [& _]
  (jetty/run (web/dev-request-handler 'hello-world)
             {:port 3000}))

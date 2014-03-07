(require '[dar.web :as web :refer [Get send]])
(require '[dar.web.server.jetty :as jetty])

(Get "/"
  :fn #(send "Hello world!"))

(Get "/{greeting}"
  :args [:http-req]
  :fn (fn [{g :greeting}]
        (send (str g " world!"))))

(jetty/run (web/dev-request-handler)
           {:port 3000})

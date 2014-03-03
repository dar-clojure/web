(ns easy-app.web.server)

(defprotocol IServer
  (stop! [this timeout] [this])
  (provider [this]))

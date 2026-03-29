(ns registration-service.core
  (:require [ring.adapter.jetty :as jetty]
            [registration-service.routes :as routes]
            [clojure.tools.logging :as log])
  (:gen-class))

(defn start-server
  "Starts the Jetty HTTP server on the configured port."
  [port]
  (log/info (str "Starting Registration Service on port " port))
  (jetty/run-jetty #'routes/app
                   {:port port
                    :join? true}))

(defn -main
  "Application entry point."
  [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8081"))]
    (start-server port)))

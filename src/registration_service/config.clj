(ns registration-service.config
  (:require [clojure.tools.logging :as log]))

(defn get-env
  "Gets an environment variable with an optional default value."
  ([key] (get-env key nil))
  ([key default]
   (or (System/getenv key) default)))

(def db-config
  {:jdbcUrl (str "jdbc:mysql://"
                 (get-env "DB_HOST" "localhost")
                 ":"
                 (get-env "DB_PORT" "3306")
                 "/"
                 (get-env "DB_NAME" "vozni_park_db")
                 "?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false")
   :username (get-env "DB_USERNAME" "root")
   :password (get-env "DB_PASSWORD" "")
   :maximum-pool-size 5})

(def jwt-config
  "JWT configuration for token signing."
  {:secret     (get-env "JWT_SECRET" "dev-secret-change-in-production")
   :expiration (* 2 60 60)})  ;; 2 hours in seconds

(def mail-config
  "SMTP email configuration."
  {:host (get-env "MAIL_HOST" "smtp.gmail.com")
   :port (Integer/parseInt (get-env "MAIL_PORT" "587"))
   :user (get-env "MAIL_USER" "")
   :pass (get-env "MAIL_PASSWORD" "")
   :tls  true})

(def app-config
  "General application configuration."
  {:port            (Integer/parseInt (get-env "PORT" "8081"))
   :base-url        (get-env "BASE_URL" "http://localhost:8081")
   :frontend-url    (get-env "FRONTEND_URL" "http://localhost:5173")
   :token-ttl-hours 2})

(defn log-config
  "Logs the active configuration (without secrets)."
  []
  (log/info (str "DB Host: " (:host db-config)))
  (log/info (str "DB Name: " (:dbname db-config)))
  (log/info (str "DB Port: " (:port db-config)))
  (log/info (str "App Port: " (:port app-config)))
  (log/info (str "Base URL: " (:base-url app-config)))
  (log/info (str "Frontend URL: " (:frontend-url app-config))))

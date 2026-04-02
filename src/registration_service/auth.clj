(ns registration-service.auth
  (:require [buddy.sign.jwt :as jwt]
            [registration-service.config :as config]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)
           (org.mindrot.jbcrypt BCrypt)))

(defn generate-registration-token []
  (str (UUID/randomUUID)))

(defn generate-otp []
  (format "%06d" (rand-int 1000000)))

(defn hash-password [password]
  (BCrypt/hashpw password (BCrypt/gensalt 10)))

(defn verify-password [password hash]
  (BCrypt/checkpw password hash))

(defn create-jwt [user-id username role]
  (let [claims {:user-id  user-id
                :username username
                :role     role
                :exp      (+ (quot (System/currentTimeMillis) 1000)
                             (:expiration config/jwt-config))}]
    (jwt/sign claims (:secret config/jwt-config))))

(defn verify-jwt [token]
  (try
    (jwt/unsign token (:secret config/jwt-config))
    (catch Exception e
      (log/warn (str "JWT verification failed: " (.getMessage e)))
      nil)))
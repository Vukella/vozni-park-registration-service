(ns registration-service.auth
  (:require [buddy.sign.jwt :as jwt]
            [buddy.hashers :as hashers]
            [registration-service.config :as config]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

;; Registration Token Generation

(defn generate-registration-token
  "Generates a random UUID string for the magic link."
  []
  (str (UUID/randomUUID)))

;; JWT Token (for final authentication handshake)

(defn create-jwt
  "Creates a signed JWT for the newly registered user.
   This token will be consumed by the Spring Boot application."
  [user-id username role]
  (let [claims {:user-id  user-id
                :username username
                :role     role
                :exp      (+ (quot (System/currentTimeMillis) 1000)
                             (:expiration config/jwt-config))}]
    (jwt/sign claims (:secret config/jwt-config))))

(defn verify-jwt
  "Verifies and decodes a JWT. Returns claims or nil on failure."
  [token]
  (try
    (jwt/unsign token (:secret config/jwt-config))
    (catch Exception e
      (log/warn (str "JWT verification failed: " (.getMessage e)))
      nil)))

;; Password Hashing (BCrypt — compatible with Spring Boot)

(defn hash-password
  "Hashes a password with BCrypt. Produces hashes compatible
   with Spring Boot's BCryptPasswordEncoder."
  [password]
  (hashers/derive password {:alg :bcrypt :iterations 10}))

(defn verify-password
  "Verifies a password against a BCrypt hash."
  [password hash]
  (:valid (hashers/verify password hash)))

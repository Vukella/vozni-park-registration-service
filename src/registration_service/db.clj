(ns registration-service.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [registration-service.config :as config]
            [clojure.tools.logging :as log])
  (:import (com.zaxxer.hikari HikariDataSource)))

;; Connection Pool

(defonce datasource
  (delay
    (log/info "Initializing HikariCP connection pool...")
    (connection/->pool HikariDataSource config/db-config)))

(defn get-datasource
  "Returns the HikariCP datasource, initializing on first call."
  []
  @datasource)

;; ZAPOSLENI Queries

(defn find-employee-by-email
  "Checks if an active employee exists with the given email.
   Returns the employee record or nil."
  [email]
  (jdbc/execute-one! (get-datasource)
                     ["SELECT * FROM zaposleni WHERE EMAIL = ? AND IS_ACTIVE = 1" email]))

;; Registration Token Queries

(defn save-registration-token!
  "Saves a registration token to the database."
  [token email zaposleni-id expires-at]
  (jdbc/execute-one! (get-datasource)
                     ["INSERT INTO registration_tokens (TOKEN, EMAIL, ZAPOSLENI_ID, EXPIRES_AT)
                       VALUES (?, ?, ?, ?)"
                      token email zaposleni-id expires-at]))

(defn find-valid-token
  "Finds a token that is not expired and not used."
  [token]
  (jdbc/execute-one! (get-datasource)
                     ["SELECT * FROM registration_tokens
                       WHERE TOKEN = ? AND USED = 0 AND EXPIRES_AT > NOW()"
                      token]))

(defn mark-token-used!
  "Marks a registration token as used."
  [token]
  (jdbc/execute! (get-datasource)
                 ["UPDATE registration_tokens SET USED = 1 WHERE TOKEN = ?" token]))

;; User Creation

(defn create-user!
  "Creates a new user in the app_user table.
   password-hash should already be BCrypt-encoded."
  [username full-name password-hash role-id zaposleni-id]
  (jdbc/execute-one! (get-datasource)
                     ["INSERT INTO app_user (USERNAME, FULL_NAME, PASSWORD_HASH, ROLE_ID, ZAPOSLENI_ID, IS_ACTIVE)
                       VALUES (?, ?, ?, ?, ?, 1)"
                      username full-name password-hash role-id zaposleni-id]))

(defn username-exists?
  "Checks if a username is already taken."
  [username]
  (let [result (jdbc/execute-one! (get-datasource)
                                  ["SELECT COUNT(*) AS cnt FROM app_user WHERE USERNAME = ?" username])]
    (> (:cnt result) 0)))

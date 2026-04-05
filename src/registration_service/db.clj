(ns registration-service.db
    (:require [next.jdbc :as jdbc]
      [next.jdbc.connection :as connection]
      [next.jdbc.result-set :as rs]
      [registration-service.config :as config]
      [clojure.tools.logging :as log])
    (:import (com.zaxxer.hikari HikariDataSource)))

;; Without this, next.jdbc returns qualified keys like :zaposleni/email
;; which break simple keyword access. This forces plain :email, :id_zaposleni, etc.
(def ^:private query-opts
  {:builder-fn rs/as-unqualified-lower-maps})

;; Connection Pool

(defonce datasource
         (delay
           (log/info "Initializing HikariCP connection pool...")
           (connection/->pool HikariDataSource config/db-config)))

(defn get-datasource []
      @datasource)

;; ZAPOSLENI Queries

(defn find-employee-by-email [email]
      (jdbc/execute-one! (get-datasource)
                         ["SELECT * FROM zaposleni WHERE EMAIL = ? AND IS_ACTIVE = 1" email]
                         query-opts))

(defn find-employee-by-zaposleni-id [id]
      (jdbc/execute-one! (get-datasource)
                         ["SELECT * FROM zaposleni WHERE ID_ZAPOSLENI = ?" id]
                         query-opts))

;; Registration Token Queries

(defn save-registration-token! [token email zaposleni-id expires-at]
      (jdbc/execute-one! (get-datasource)
                         ["INSERT INTO registration_tokens (TOKEN, EMAIL, ZAPOSLENI_ID, EXPIRES_AT)
                       VALUES (?, ?, ?, ?)"
                          token email zaposleni-id expires-at]
                         query-opts))

(defn find-valid-token [token]
      (jdbc/execute-one! (get-datasource)
                         ["SELECT * FROM registration_tokens
                       WHERE TOKEN = ? AND USED = 0 AND EXPIRES_AT > UTC_TIMESTAMP()"
                          token]
                         query-opts))

(defn mark-token-used! [token]
      (jdbc/execute! (get-datasource)
                     ["UPDATE registration_tokens SET USED = 1 WHERE TOKEN = ?" token]
                     query-opts))

;; User Creation

(defn create-user! [username full-name password-hash role-id zaposleni-id]
  (jdbc/execute-one! (get-datasource)
                     ["INSERT INTO app_user (USERNAME, FULL_NAME, PASSWORD_HASH, ROLE_ID, ZAPOSLENI_ID, IS_ACTIVE, SKIP_NEXT_OTP)
                       VALUES (?, ?, ?, ?, ?, 1, 1)"
                      username full-name password-hash role-id zaposleni-id]
                     query-opts))

(defn username-exists? [username]
      (let [result (jdbc/execute-one! (get-datasource)
                                      ["SELECT COUNT(*) AS cnt FROM app_user WHERE USERNAME = ?" username]
                                      query-opts)]
           (> (:cnt result) 0)))


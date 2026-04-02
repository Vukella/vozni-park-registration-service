(ns registration-service.handlers
  (:require [clojure.tools.logging :as log]
            [registration-service.db :as db]
            [registration-service.auth :as auth]
            [registration-service.mail :as mail]))

(defn health-check [_request]
  {:status 200
   :body {:status "UP"
          :service "registration-service"
          :version "0.1.0"}})

(defn register-request [request]
  (let [email (get-in request [:body :email])]
    (if (or (nil? email) (empty? email))
      {:status 400 :body {:error "Email is required."}}
      (do
        (let [employee (db/find-employee-by-email email)]
          (when employee
            (let [token        (auth/generate-registration-token)
                  zaposleni-id (:id_zaposleni employee)
                  expires-at   (java.sql.Timestamp.
                                 (+ (System/currentTimeMillis)
                                    (* 2 60 60 1000)))]
              (db/save-registration-token! token email zaposleni-id expires-at)
              (mail/send-registration-email email (:full_name employee) token))))
        {:status 200
         :body {:message "If this email is registered in our system, you will receive a registration link."}}))))

(defn verify-token [request]
  (let [token (get-in request [:query-params "token"])]
    (let [record (db/find-valid-token token)]
      (if record
        {:status 200
         :body {:valid true :email (:email record) :message "Token is valid."}}
        {:status 400
         :body {:valid false :error "Token is invalid or has expired."}}))))

(defn request-otp [request]
  (let [token (get-in request [:body :token])]
    (if (or (nil? token) (empty? token))
      {:status 400 :body {:error "Token is required."}}
      (let [record (db/find-valid-token token)]
        (if (nil? record)
          {:status 400 :body {:error "Token is invalid or has expired."}}
          (let [email      (:email record)
                code       (auth/generate-otp)
                expires-at (java.sql.Timestamp.
                             (+ (System/currentTimeMillis)
                                (* 5 60 1000)))]
            (db/save-otp! email code expires-at)
            (mail/send-otp-email email code)
            (log/info (str "OTP sent to: " email))
            {:status 200
             :body {:message "A verification code has been sent to your email. It expires in 5 minutes."}}))))))

(defn complete-registration [request]
  (let [{:keys [token otp username password]} (:body request)]
    (cond
      (nil? token)
      {:status 400 :body {:error "Token is required."}}

      (nil? otp)
      {:status 400 :body {:error "Verification code is required."}}

      (nil? username)
      {:status 400 :body {:error "Username is required."}}

      (nil? password)
      {:status 400 :body {:error "Password is required."}}

      (< (count password) 8)
      {:status 400 :body {:error "Password must be at least 8 characters."}}

      :else
      (let [record (db/find-valid-token token)]
        (cond
          (nil? record)
          {:status 400 :body {:error "Token is invalid or has expired."}}

          (nil? (db/find-valid-otp (:email record) otp))
          {:status 400 :body {:error "Verification code is invalid or has expired."}}

          (db/username-exists? username)
          {:status 409 :body {:error "Username is already taken."}}

          :else
          (let [zaposleni-id  (:zaposleni_id record)
                employee      (db/find-employee-by-zaposleni-id zaposleni-id)
                full-name     (:full_name employee)
                password-hash (auth/hash-password password)]
            (db/create-user! username full-name password-hash 2 zaposleni-id)
            (db/mark-token-used! token)
            (db/mark-otp-used! (:email record) otp)
            (log/info (str "User account created: " username))
            {:status 201
             :body {:message  "Account created successfully. You can now log in."
                    :username username}}))))))
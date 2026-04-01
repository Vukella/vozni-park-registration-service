(ns registration-service.handlers
  (:require [clojure.tools.logging :as log]))

(defn health-check
  "Health check endpoint — confirms service is running."
  [_request]
  {:status 200
   :body {:status "UP"
          :service "registration-service"
          :version "0.1.0"}})

(defn register-request
  "POST /api/register — Accepts email, checks ZAPOSLENI table,
   sends registration link if employee exists.
   TODO: Implement DB check, token generation, email sending."
  [request]
  (let [email (get-in request [:body :email])]
    (log/info (str "Registration request received for: " email))
    (if (and email (not (empty? email)))
      ;; Placeholder — always returns success message
      ;; In production: check ZAPOSLENI table, generate token, send email
      {:status 200
       :body {:message "If this email is registered in our system, you will receive a registration link."}}
      {:status 400
       :body {:error "Email is required."}})))

(defn verify-token
  "GET /api/verify?token=... — Validates the registration token.
   TODO: Implement token lookup and expiration check."
  [request]
  (let [token (get-in request [:query-params "token"])]
    (log/info (str "Token verification request: " (when token (subs token 0 (min 8 (count token)))) "..."))
    (if token
      ;; Placeholder — returns confirmation page data
      {:status 200
       :body {:message "Token is valid. Please complete your registration."
              :token token}}
      {:status 400
       :body {:error "Token parameter is required."}})))

(defn complete-registration
  "POST /api/complete — Creates user account with username and password.
   TODO: Implement user creation in APP_USER table with BCrypt hash."
  [request]
  (let [{:keys [token username password]} (:body request)]
    (log/info (str "Registration completion for username: " username))
    (cond
      (nil? token)    {:status 400 :body {:error "Token is required."}}
      (nil? username) {:status 400 :body {:error "Username is required."}}
      (nil? password) {:status 400 :body {:error "Password is required."}}
      :else
      ;; Placeholder — returns success
      {:status 201
       :body {:message "User account created successfully."
              :username username}})))

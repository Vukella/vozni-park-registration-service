(ns registration-service.handlers-test
  (:require [midje.sweet :refer :all]
            [registration-service.handlers :as handlers]
            [registration-service.db       :as db]
            [registration-service.mail     :as mail]
            [registration-service.auth     :as auth]))

;; Helper — build a Ring request with a JSON body

(defn- body-request
  "Builds a minimal Ring request map with a parsed JSON body (keyword keys).
   ring-json's wrap-json-body delivers the body as a Clojure map under :body."
  [body-map]
  {:body body-map})

(defn- query-request
  "Builds a minimal Ring request with query params (string keys,
   as delivered by ring's wrap-params middleware)."
  [params-map]
  {:query-params params-map})

;; health-check

(facts "about health-check"

       (fact "responds with HTTP 200"
             (:status (handlers/health-check {})) => 200)

       (fact "body contains status UP"
             (get-in (handlers/health-check {}) [:body :status]) => "UP"))

;; register-request

(facts "about register-request"

       (fact "returns 400 when the request body is empty"
             (:status (handlers/register-request (body-request {}))) => 400)

       (fact "returns 400 when the email key is missing from the body"
             (:status (handlers/register-request (body-request {:name "no email here"}))) => 400)

       (fact "returns 200 when a matching active employee exists — sends registration email"
             (handlers/register-request (body-request {:email "ana.jovanovic@voznipark.rs"}))
             => (contains {:status 200})
             (provided
               (db/find-employee-by-email "ana.jovanovic@voznipark.rs")
               => {:id_zaposleni 7 :full_name "Ana Jovanovic" :email "ana.jovanovic@voznipark.rs"}

               (auth/generate-registration-token)
               => "mock-uuid-token-1234"

               (db/save-registration-token! "mock-uuid-token-1234" "ana.jovanovic@voznipark.rs" 7 anything)
               => nil

               (mail/send-registration-email "ana.jovanovic@voznipark.rs" "Ana Jovanovic" "mock-uuid-token-1234")
               => nil))

       (fact "returns 200 even when the employee is NOT found (prevents email enumeration)"
             (handlers/register-request (body-request {:email "unknown@voznipark.rs"}))
             => (contains {:status 200})
             (provided
               (db/find-employee-by-email "unknown@voznipark.rs") => nil)))

;; verify-token

(facts "about verify-token"

       (fact "returns 400 when the token query parameter is missing"
             (:status (handlers/verify-token {:query-params {}})) => 400)

       (fact "returns 200 and valid:true when token exists and has not expired"
             (handlers/verify-token (query-request {"token" "valid-uuid-abc"}))
             => (contains {:status 200})
             (provided
               (db/find-valid-token "valid-uuid-abc")
               => {:token "valid-uuid-abc" :email "marko.markovic@voznipark.rs"}))

       (fact "response body contains valid=true on success"
             (get-in (handlers/verify-token (query-request {"token" "valid-uuid-abc"}))
                     [:body :valid])
             => true
             (provided
               (db/find-valid-token "valid-uuid-abc")
               => {:token "valid-uuid-abc" :email "marko.markovic@voznipark.rs"}))

       (fact "response body contains the employee email on success"
             (get-in (handlers/verify-token (query-request {"token" "valid-uuid-abc"}))
                     [:body :email])
             => "marko.markovic@voznipark.rs"
             (provided
               (db/find-valid-token "valid-uuid-abc")
               => {:token "valid-uuid-abc" :email "marko.markovic@voznipark.rs"}))

       (fact "returns 400 and valid:false when the token is expired or not found"
             (handlers/verify-token (query-request {"token" "expired-or-fake"}))
             => (contains {:status 400})
             (provided
               (db/find-valid-token "expired-or-fake") => nil))

       (fact "response body contains valid=false on failure"
             (get-in (handlers/verify-token (query-request {"token" "bad-token"}))
                     [:body :valid])
             => false
             (provided
               (db/find-valid-token "bad-token") => nil)))

;; complete-registration

(facts "about complete-registration"

       (fact "returns 400 when all required fields are missing"
             (:status (handlers/complete-registration (body-request {}))) => 400)

       (fact "returns 400 when token is missing"
             (:status (handlers/complete-registration
                        (body-request {:username "newuser" :password "pass1234"})))
             => 400)

       (fact "returns 400 when username is missing"
             (:status (handlers/complete-registration
                        (body-request {:token "tok" :password "pass1234"})))
             => 400)

       (fact "returns 400 when password is missing"
             (:status (handlers/complete-registration
                        (body-request {:token "tok" :username "newuser"})))
             => 400)

       (fact "returns 400 when password is shorter than 8 characters"
             (:status (handlers/complete-registration
                        (body-request {:token "tok" :username "newuser" :password "short"})))
             => 400)

       (fact "returns 410 when the token is invalid or expired"
             (:status (handlers/complete-registration
                        (body-request {:token "bad-token" :username "newuser" :password "validpass1"})))
             => 410
             (provided
               (db/find-valid-token "bad-token") => nil))

       (fact "returns 409 when the requested username is already taken"
             (:status (handlers/complete-registration
                        (body-request {:token "good-token" :username "taken.user" :password "validpass1"})))
             => 409
             (provided
               (db/find-valid-token "good-token")
               => {:token "good-token" :email "pera@voznipark.rs" :zaposleni_id 3}

               (db/username-exists? "taken.user") => true))

       (fact "returns 201 and creates the user when all inputs are valid"
             (handlers/complete-registration
               (body-request {:token "good-token" :username "pera.peric" :password "validpass1"}))
             => (contains {:status 201})
             (provided
               (db/find-valid-token "good-token")
               => {:token "good-token" :email "pera@voznipark.rs" :zaposleni_id 3}

               (db/username-exists? "pera.peric") => false

               (db/find-employee-by-zaposleni-id 3)
               => {:id_zaposleni 3 :full_name "Pera Peric" :email "pera@voznipark.rs"}

               (auth/hash-password "validpass1") => "$2a$10$mockedBCryptHash"

               (db/create-user! "pera.peric" "Pera Peric" "$2a$10$mockedBCryptHash" 2 3) => nil

               (db/mark-token-used! "good-token") => nil))

       (fact "response body contains the username on successful registration"
             (get-in (handlers/complete-registration
                       (body-request {:token "good-token" :username "pera.peric" :password "validpass1"}))
                     [:body :username])
             => "pera.peric"
             (provided
               (db/find-valid-token "good-token")
               => {:token "good-token" :email "pera@voznipark.rs" :zaposleni_id 3}

               (db/username-exists? "pera.peric") => false

               (db/find-employee-by-zaposleni-id 3)
               => {:id_zaposleni 3 :full_name "Pera Peric" :email "pera@voznipark.rs"}

               (auth/hash-password "validpass1") => "$2a$10$mockedBCryptHash"

               (db/create-user! "pera.peric" "Pera Peric" "$2a$10$mockedBCryptHash" 2 3) => nil

               (db/mark-token-used! "good-token") => nil)))
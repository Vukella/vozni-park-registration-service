(ns registration-service.auth-test
  (:require [midje.sweet :refer :all]
            [registration-service.auth :as auth]))

;; ============================================================
;; generate-registration-token
;; ============================================================

(facts "about generate-registration-token"

       (fact "returns a string"
             (auth/generate-registration-token) => string?)

       (fact "returns a UUID-formatted string (8-4-4-4-12 hex groups)"
             (auth/generate-registration-token)
             => (fn [token]
                  (boolean (re-matches
                             #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                             token))))

       (fact "returns a different token on every call (random UUID)"
             (let [t1 (auth/generate-registration-token)
                   t2 (auth/generate-registration-token)]
               t1 =not=> t2)))

;; ============================================================
;; hash-password
;; ============================================================

(facts "about hash-password"

       (fact "returns a non-nil string"
             (auth/hash-password "password123") => string?)

       (fact "returns a BCrypt hash (starts with $2a$)"
             (auth/hash-password "password123")
             => (fn [h] (.startsWith h "$2a$")))

       (fact "uses cost factor 10 ($2a$10$)"
             (auth/hash-password "password123")
             => (fn [h] (.startsWith h "$2a$10$")))

       (fact "produces different hashes for the same input (BCrypt uses random salt)"
             (let [h1 (auth/hash-password "password123")
                   h2 (auth/hash-password "password123")]
               h1 =not=> h2)))

;; ============================================================
;; verify-password
;; ============================================================

(facts "about verify-password"

       (fact "returns true when the plain-text password matches its hash"
             (let [plain    "securePassword99"
                   hashed   (auth/hash-password plain)]
               (auth/verify-password plain hashed) => true))

       (fact "returns false when the password does not match the hash"
             (let [hashed (auth/hash-password "correctPassword")]
               (auth/verify-password "wrongPassword" hashed) => false))

       (fact "returns false for an empty string against a real hash"
             (let [hashed (auth/hash-password "something")]
               (auth/verify-password "" hashed) => false)))
(ns registration-service.core-test
  (:require [clojure.test :refer :all]
            [registration-service.handlers :as handlers]))

(deftest health-check-test
  (testing "Health check returns status UP"
    (let [response (handlers/health-check {})]
      (is (= 200 (:status response)))
      (is (= "UP" (get-in response [:body :status]))))))

(deftest register-request-missing-email-test
  (testing "Register request with missing email returns 400"
    (let [response (handlers/register-request {:body {}})]
      (is (= 400 (:status response))))))

(deftest register-request-with-email-test
  (testing "Register request with email returns 200"
    (let [response (handlers/register-request {:body {:email "test@example.com"}})]
      (is (= 200 (:status response))))))

(deftest verify-token-missing-test
  (testing "Verify without token returns 400"
    (let [response (handlers/verify-token {:query-params {}})]
      (is (= 400 (:status response))))))

(deftest complete-registration-missing-fields-test
  (testing "Complete registration with missing fields returns 400"
    (let [response (handlers/complete-registration {:body {}})]
      (is (= 400 (:status response))))))

(ns registration-service.routes
  (:require [reitit.ring :as ring]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [registration-service.handlers :as handlers]))

(def app
  (ring/ring-handler
   (ring/router
    [["/health"
      {:get {:handler handlers/health-check}}]

     ["/api/register"
      {:post {:handler handlers/register-request}}]

     ["/api/verify"
      {:get {:handler handlers/verify-token}}]

     ["/api/complete"
      {:post {:handler handlers/complete-registration}}]])

   (ring/create-default-handler
    {:not-found (constantly {:status 404
                             :body {:error "Not found"}})})

   {:middleware [wrap-json-response
                [wrap-json-body {:keywords? true}]]}))

(ns registration-service.mail
  (:require [postal.core :as postal]
            [selmer.parser :as selmer]
            [registration-service.config :as config]
            [clojure.tools.logging :as log]))

(defn send-registration-email
  [to-email full-name token]
  (let [registration-url (str (:frontend-url config/app-config)
                              "/complete-registration?token=" token)
        html-body (selmer/render-file "templates/registration-email.html"
                                      {:name full-name
                                       :registration-url registration-url
                                       :expiry-hours (:token-ttl-hours config/app-config)})]
    (log/info (str "Sending registration email to: " to-email))
    (try
      (postal/send-message
        {:host (:host config/mail-config)
         :port (:port config/mail-config)
         :user (:user config/mail-config)
         :pass (:pass config/mail-config)
         :tls  (:tls config/mail-config)}
        {:from    (:user config/mail-config)
         :to      to-email
         :subject "Vozni Park — Registracija korisnickog naloga"
         :body    [{:type    "text/html; charset=utf-8"
                    :content html-body}]})
      (log/info (str "Registration email sent successfully to: " to-email))
      (catch Exception e
        (log/error (str "Failed to send email to " to-email ": " (.getMessage e)))))))
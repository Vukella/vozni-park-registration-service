(ns registration-service.mail
  (:require [postal.core :as postal]
            [selmer.parser :as selmer]
            [registration-service.config :as config]
            [clojure.tools.logging :as log]))

(defn send-registration-email
  "Sends a registration email with the magic link to the user.
   Uses Selmer to render the HTML email template."
  [to-email full-name token]
  (let [registration-url (str (:base-url config/app-config)
                              "/api/verify?token=" token)
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

(defn send-otp-email
  "Sends a 2FA verification code email to the user."
  [to-email code]
  (let [html-body (selmer/render-file "templates/otp-email.html"
                                      {:code code
                                       :expiry-minutes 5})]
    (log/info (str "Sending OTP email to: " to-email))
    (try
      (postal/send-message
        {:host (:host config/mail-config)
         :port (:port config/mail-config)
         :user (:user config/mail-config)
         :pass (:pass config/mail-config)
         :tls  (:tls config/mail-config)}
        {:from    (:user config/mail-config)
         :to      to-email
         :subject "Vozni Park — Kod za verifikaciju"
         :body    [{:type    "text/html; charset=utf-8"
                    :content html-body}]})
      (log/info (str "OTP email sent successfully to: " to-email))
      (catch Exception e
        (log/error (str "Failed to send OTP email to " to-email ": " (.getMessage e)))))))

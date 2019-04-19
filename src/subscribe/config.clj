;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.config)

(defn port []
  (or (System/getenv "SUBSCRIBE_PORT") "3000"))

(def mailgun-api-url "https://api.mailgun.net/v3")

(def mailgun-host "smtp.mailgun.org")

(defn mailgun-api-key
  []
  (or (System/getenv "MAILGUN_API_KEY")
      (throw (Exception. "Missing API key"))))

(defn mailgun-login
  []
  (or (System/getenv "MAILGUN_LOGIN")
      (throw (Exception. "Missing login"))))

(defn mailgun-password
  []
  (or (System/getenv "MAILGUN_PASSWORD")
      (throw (Exception. "Missing password"))))

(defn mailgun-from
  "The address to send transactional emails from."
  []
  (or (System/getenv "MAILGUN_FROM")
      (throw (Exception. "Missing from address"))))

(def mailgun-lists-endpoint "/lists/pages")

(defn mailgun-subscribe-endpoint [list]
  (str "/lists/" list "/members"))

(def warn-every-x-subscribers 10)

(defn admin-email
  "The email address where to send warnings."
  []
  (or (System/getenv "SUBSCRIBE_ADMIN_EMAIL")
      (throw (Exception. "Missing admin email address"))))

(defn base-url
  "The base URL of the subscription page."
  []
  (or (System/getenv "SUBSCRIBE_BASE_URL")
      (throw (Exception. "Missing base URL"))))

(defn return-url
  "URL to redirect the user to when subscribed."
  [] ;; Optional
  (or (System/getenv "SUBSCRIBE_RETURN_URL") "/"))

(defn db-uri
  "The db URI for datahike."
  [] ;; Optional
  (or (System/getenv "SUBSCRIBE_DB_URI")
      "datahike:mem:///subscribe"))

(defn log-file
  "Filename to store logs."
  [] ;; Optional
  (or (System/getenv "SUBSCRIBE_LOG_FILE") "log.txt"))

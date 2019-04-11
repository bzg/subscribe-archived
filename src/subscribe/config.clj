;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.config)

(defn port []
  (or (System/getenv "SUBSCRIBE_PORT") "3000"))

(def mailgun-api-url "https://api.mailgun.net/v3")

(def mailgun-host "smtp.mailgun.org")

(def db-uri "datahike:mem:///subscribe")

(defn mailgun-api-key []
  (or (System/getenv "MAILGUN_API_KEY")
      (throw (Exception. "Missing API key"))))

(defn mailgun-login []
  (or (System/getenv "MAILGUN_LOGIN")
      (throw (Exception. "Missing login"))))

(defn mailgun-password []
  (or (System/getenv "MAILGUN_PASSWORD")
      (throw (Exception. "Missing password"))))

(defn mailgun-from []
  (or (System/getenv "MAILGUN_FROM")
      (throw (Exception. "Missing from address"))))

(defn mailgun-mailing-list []
  (or (System/getenv "MAILGUN_MAILING_LIST")
      (throw (Exception. "Missing mailing list"))))

(def mailgun-subscribe-endpoint
  (str "/lists/" (mailgun-mailing-list) "/members"))

(defn base-url []
  (or (System/getenv "MAILGUN_BASE_URL")
      (throw (Exception. "Missing base URL"))))

(defn return-url [] ;; Optional
  (or (System/getenv "MAILGUN_RETURN_URL") "/"))


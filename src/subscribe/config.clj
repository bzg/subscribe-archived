;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.config
  "Subscribe configuration variables."
  (:require [clojure.java.io :as io]))

;; Mailgun constants
(def mailgun-api-url "https://api.mailgun.net/v3")
(def mailgun-host "smtp.mailgun.org")
(def mailgun-lists-endpoint "/lists/pages")

;; Mailgun environment variables
(def mailgun-api-key (System/getenv "MAILGUN_API_KEY"))
(def mailgun-login (System/getenv "MAILGUN_LOGIN"))
(def mailgun-password (System/getenv "MAILGUN_PASSWORD"))
(def port (read-string (or (System/getenv "SUBSCRIBE_PORT") "3000")))
(def base-url (or (System/getenv "SUBSCRIBE_BASEURL")
                  (str "http://localhost:" port)))

;; Configuration from your config.edn file
(def config (read-string (slurp (io/resource "config.edn"))))
(def from (or (:from config) mailgun-login))
(def locale (:locale config))
(def ui-strings (:ui-strings config))
(def team (:team config))
(def return-url (or (:return-url config) base-url))
(def tos-url (:tos-url config))
(def admin-email (or (:admin-email config) from mailgun-login))
(def warn-every-x-subscribers (or (:warn-every-x-subscribers config) 100))
(def lists-exclude-regexp (or (:lists-exclude-regexp config) #""))
(def lists-include-regexp (or (:lists-include-regexp config) #".*"))
(def db-uri (or (not-empty (:db-uri config)) "datahike:mem:///subscribe"))
(def log-file (or (not-empty (:log-file config)) "log.txt"))

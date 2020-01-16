;; Copyright (c) 2019-2020 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.config
  "Subscribe configuration variables."
  (:require [clojure.java.io :as io]))

;; Mailgun constants
(def mailgun-api-url "https://api.mailgun.net/v3")
(def mailgun-lists-endpoint "/lists/pages")

;; Mailgun environment variables
(def mailgun-api-key (System/getenv "MAILGUN_API_KEY"))
(def port (read-string (or (System/getenv "SUBSCRIBE_PORT") "3000")))
(def base-url (or (System/getenv "SUBSCRIBE_BASEURL")
                  (str "http://localhost:" port)))

;; Configuration from your config.edn file
(def config (read-string (slurp (io/resource "config.edn"))))
(def db-uri (or (not-empty (:db-uri config)) "datahike:mem:///subscribe"))
(def lists-exclude-regexp (or (:lists-exclude-regexp config) #""))
(def lists-include-regexp (or (:lists-include-regexp config) #".*"))
(def log-file (or (not-empty (:log-file config)) "log.txt"))
(def ui-strings (:ui-strings config))
(def locale (or (:locale config) "en-US"))
(def css (or (:css config)
             "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.8.0/css/bulma.min.css"))

;; Configuration for additional HTML
(def before-head-closing-html (:before-head-closing-html config))
(def after-body-beginning-html (:after-body-beginning-html config))
(def footer-html (:footer-html config))

;; Per-list configuration options
(defn smtp-host [ml]
  (or (:smtp-host (get (:lists config) ml))
      "smtp.mailgun.org"))

(defn smtp-login [ml]
  (or (:smtp-login (get (:lists config) ml))
      (System/getenv "MAILGUN_LOGIN")))

(def admin-email (or (:admin-email config) (:from config) (smtp-login nil)))

(defn smtp-password [ml]
  (or (:smtp-password (get (:lists config) ml))
      (System/getenv "MAILGUN_PASSWORD")))

(defn from [ml]
  (or (:from (get (:lists config) ml))
      (:from config)
      (smtp-login ml)))

(defn team [ml]
  (or (:team (get (:lists config) ml))
      (:team config)))

(defn return-url [ml]
  (or (:return-url (get (:lists config) ml))
      (:return-url config)
      base-url))

(defn tos-url [ml]
  (or (:tos-url (get (:lists config) ml))
      (:tos-url config)))

(defn warn-every-x-subscribers [ml]
  (or (:warn-every-x-subscribers (get (:lists config) ml))
      (:warn-every-x-subscribers config)
      100))

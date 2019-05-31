;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.config
  (:require [clojure.java.io :as io]))

(defn config []
  (if-let [config-file (not-empty (System/getenv "SUBSCRIBE_CONFIG"))]
    (if (.exists (io/file config-file))
      (read-string (slurp config-file))
      (throw (Exception. "Can't read configuration file")))
    (throw (Exception. "Missing SUBSCRIBE_CONFIG environment variable"))))

;; Mailgun variables
(def mailgun-api-url "https://api.mailgun.net/v3")
(def mailgun-host "smtp.mailgun.org")
(def mailgun-lists-endpoint "/lists/pages")
(defn mailgun-subscribe-endpoint [list]
  (str "/lists/" list "/members"))

;; Mailgun personal configuration
(def mailgun-api-key (:mailgun-api-key (config)))
(def mailgun-login (:mailgun-login (config)))
(def mailgun-password (:mailgun-password (config)))
(def mailgun-from (or (:mailgun-from (config)) mailgun-login))

;;Configuration from your config file
(def locale (:locale (config)))
(def port (or (:port (config)) 3000))
(def admin-email (:admin-email (config)))
(def base-url (or (:base-url (config)) (str "http://localhost:" port)))
(def return-url (or (:return-url (config)) base-url))
(def warn-every-x-subscribers (:warn-every-x-subscribers (config)))
(def lists-exclude-regexp (or (:lists-exclude-regexp (config)) #""))
(def lists-include-regexp (or (:lists-include-regexp (config)) #".*"))
(def db-uri (or (not-empty (:db-uri (config))) "datahike:mem:///subscribe"))
(def log-file (or (not-empty (:log-file (config))) "log.txt"))

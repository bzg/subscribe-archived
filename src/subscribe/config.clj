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

(def mailgun-api-url "https://api.mailgun.net/v3")
(def mailgun-host "smtp.mailgun.org")

(def mailgun-api-key (:mailgun-api-key (config)))
(def mailgun-login (:mailgun-login (config)))
(def mailgun-password (:mailgun-password (config)))
(def mailgun-from (:mailgun-from (config)))
(def mailgun-lists-endpoint "/lists/pages")

(defn mailgun-subscribe-endpoint [list]
  (str "/lists/" list "/members"))

(def port (:port (config)))
(def admin-email (:admin-email (config)))
(def base-url (:base-url (config)))
(def return-url (:return-url (config)))
(def warn-every-x-subscribers (:warn-every-x-subscribers (config)))

(defn db-uri
  "The db URI for datahike."
  [] ;; Optional
  (or (not-empty (:db-uri (config)))
      "datahike:mem:///subscribe"))

(defn log-file
  "Filename to store logs."
  [] ;; Optional
  (or (not-empty (:log-file (config)))
      "log.txt"))

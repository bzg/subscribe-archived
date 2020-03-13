;; Copyright (c) 2019-2020 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.config
  "Subscribe configuration variables."
  (:require [clojure.edn :as edn]))

;; Configuration from your config.edn file
(def config (read-string (slurp "config.edn")))

;; Backends configuration
(def backends
  [{:backend                        "mailgun"
    :host                           "smtp.mailgun.org"
    :api-url                        "https://api.mailgun.net/v3"
    :lists-endpoint                 "/lists/pages"
    :subscribe-http-verb            "POST"
    :unsubscribe-http-verb          "DELETE"
    :subscribe-endpoint-fn          (fn [a _] (str "/lists/" a "/members"))
    :unsubscribe-endpoint-fn        (fn [a b] (str "/lists/" a "/members/" b))
    :subscribe-form-params-fn       (fn [a b] {:address a :name b})
    :unsubscribe-form-params-fn     nil
    :check-subscription-endpoint-fn (fn [e ml] (str "/lists/" ml "/members/" e))
    :check-subscription-validate-fn (fn [body _] (:subscribed (:member body)))
    :api-key                        (System/getenv "MAILGUN_API_KEY")
    :api-secret                     (System/getenv "MAILGUN_API_SECRET")
    :basic-auth                     ["api" :api-key]
    :replacements                   nil
    :data-keyword                   :items}
   {:backend                        "mailjet"
    :host                           "in-v3.mailjet.com"
    :api-url                        "https://api.mailjet.com/v3/REST"
    :lists-endpoint                 "/contactslist"
    :subscribe-http-verb            "POST"
    :subscribe-endpoint-fn          (fn [a _] (str "/contactslist/" a "/managecontact"))
    :subscribe-form-params-fn       (fn [a b] {:Email a :Name b :Action "addforce"})
    :unsubscribe-form-params-fn     (fn [a b] {:Email a :Name b :Action "remove"})
    :check-subscription-endpoint-fn (fn [e _] (str "/contact/" e "/getcontactslists"))
    :check-subscription-validate-fn (fn [body id] (seq (first (filter #(= (:ListID %) id) (:Data body)))))
    :api-key                        (System/getenv "MAILJET_API_KEY")
    :api-secret                     (System/getenv "MAILJET_API_SECRET")
    :basic-auth                     [:api-key :api-secret]
    :replacements                   {:Address :address :Name :name :ID :list-id}
    :data-keyword                   :Data}])

(def backends-expanded
  (map #(update % :basic-auth
                (fn [b] (replace {:api-key    (:api-key %)
                                  :api-secret (:api-secret %)}
                                 b)))
       (filter #((:backends config) (:backend %)) backends)))

(def port (read-string (or (System/getenv "SUBSCRIBE_PORT") "3000")))
(def base-url (or (System/getenv "SUBSCRIBE_BASEURL")
                  (str "http://localhost:" port)))

(def db-uri (or (not-empty (:db-uri config)) "datahike:mem:///subscribe"))
(def lists-exclude-regexp (:lists-exclude-regexp config))
(def lists-include-regexp (:lists-include-regexp config))
(def log-file (or (not-empty (:log-file config)) "log.txt"))
(def ui-strings (:ui-strings config))
(def css (or (:css config)
             "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.8.0/css/bulma.min.css"))

;; Configuration for additional HTML
(def before-head-closing-html (:before-head-closing-html config))
(def after-body-beginning-html (:after-body-beginning-html config))
(def footer-html (:footer-html config))

(defn locale [ml]
  (or (:locale (get (:lists config) ml))
      (:locale config)
      "en"))

;; Per-list configuration options
(defn smtp-host [ml]
  (or (:smtp-host (get (:lists config) ml))
      (:smtp-host config)
      (System/getenv "SUBSCRIBE_SMTP_HOST")))

(defn smtp-port [ml]
  (let [port (or (:smtp-port (get (:lists config) ml))
                 (:smtp-port config)
                 (System/getenv "SUBSCRIBE_SMTP_PORT"))]
    (if (string? port) (edn/read-string port) port)))

(defn smtp-login [ml]
  (or (:smtp-login (get (:lists config) ml))
      (:smtp-login config)
      (System/getenv "SUBSCRIBE_SMTP_LOGIN")))

(defn smtp-password [ml]
  (or (:smtp-password (get (:lists config) ml))
      (:smtp-password config)
      (System/getenv "SUBSCRIBE_SMTP_PASSWORD")))

(def admin-email (or (:admin-email config) (:from config) (smtp-login nil)))

(defn from
  "From field for transational messages for mailing list ML."
  [ml]
  (or (:from (get (:lists config) ml))
      (:from config)
      (smtp-login ml)))

(defn to
  "Address to send messages to about mailing list ML."
  [ml]
  (or (:to (get (:lists config) ml))
      (:admin-email config)
      (smtp-login ml)))

(defn msg-id
  "Message-Id part of transactional messages for mailing list ML."
  [ml]
  (or (:msg-id (get (:lists config) ml))
      (:msg-id config)))

(defn description
  "The description of the mailing list.
  It will be used as a fallback value when the backend does not allow
  to provide a description."
  [ml]
  (not-empty (:description (get (:lists config) ml))))

(defn team
  "The name of the team of mailing list ML."
  [ml]
  (or (:team (get (:lists config) ml))
      (:team config)))

(defn return-url
  "The return url for mailing list ML."
  [ml]
  (or (:return-url (get (:lists config) ml))
      (:return-url config)
      base-url))

(defn tos-url
  "The terms of service url for mailing list ML."
  [ml]
  (or (:tos-url (get (:lists config) ml))
      (:tos-url config)))

(defn warn-every-x-subscribers
  "Warn every x subscribers for mailing list ML."
  [ml]
  (or (:warn-every-x-subscribers (get (:lists config) ml))
      (:warn-every-x-subscribers config)
      100))

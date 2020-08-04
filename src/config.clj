;; Copyright (c) 2019-2020 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns config
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
    ;; :subscribe-http-verb         "POST"
    :unsubscribe-http-verb          "DELETE"
    :subscribe-endpoint-fn          (fn [a _] (str "/lists/" a "/members"))
    :unsubscribe-endpoint-fn        (fn [a b] (str "/lists/" a "/members/" b))
    :subscribe-form-params-fn       (fn [_ b c] {:address b :name c})
    :unsubscribe-form-params-fn     nil
    :check-subscription-endpoint-fn (fn [e ml] (str "/lists/" ml "/members/" e))
    :check-subscription-validate-fn (fn [body _] (:subscribed (:member body)))
    :auth                           {:basic-auth ["api" (System/getenv "MAILGUN_API_KEY")]}
    :replacements                   nil
    :data-keyword                   :items}
   {:backend                        "sendinblue"
    :host                           "smtp-relay.sendinblue.com"
    :api-url                        "https://api.sendinblue.com/v3"
    :lists-endpoint                 "/contacts/lists"
    :subscribe-endpoint-fn          (fn [_ _] "/contacts")
    :unsubscribe-endpoint-fn        (fn [a _] (str "/contacts/lists/" a "/contacts/remove"))
    :subscribe-form-params-fn       (fn [a b _] {:updateEnabled true :listIds [a] :email b})
    :unsubscribe-form-params-fn     (fn [_ b _] {:emails [b]})
    :check-subscription-endpoint-fn (fn [e _] (str "/contacts/" e))
    :check-subscription-validate-fn (fn [body id] (contains? #{id} (vals (:listIds body))))
    :auth                           {:headers {"api-key" (System/getenv "SENDINBLUE_API_KEY")}}
    :replacements                   {:name :description :id :address :folderId :list-id}
    :data-keyword                   :lists}
   {:backend                        "mailjet"
    :host                           "in-v3.mailjet.com"
    :api-url                        "https://api.mailjet.com/v3/REST"
    :lists-endpoint                 "/contactslist"
    :subscribe-endpoint-fn          (fn [a _] (str "/contactslist/" a "/managecontact"))
    :subscribe-form-params-fn       (fn [_ a b] {:Email a :Name b :Action "addforce"})
    :unsubscribe-form-params-fn     (fn [_ a b] {:Email a :Name b :Action "remove"})
    :check-subscription-endpoint-fn (fn [e _] (str "/contact/" e "/getcontactslists"))
    :check-subscription-validate-fn (fn [body id] (seq (first (filter #(= (:ListID %) id) (:Data body)))))
    :auth                           {:basic-auth [(System/getenv "MAILJET_API_KEY")
                                                  (System/getenv "MAILJET_API_SECRET")]}
    :replacements                   {:Address :address :Name :list-name :ID :list-id}
    :data-keyword                   :Data}])

(def backends-expanded
  (filter #((:backends config) (:backend %)) backends))

(def port (read-string (or (System/getenv "SUBSCRIBE_PORT") "3000")))
(def base-url (or (System/getenv "SUBSCRIBE_BASEURL")
                  (str "http://localhost:" port)))

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
(def lists (:lists config))

(defn locale [ml]
  (or (:locale (get lists ml))
      (:locale config)
      "en"))

;; Per-list configuration options
(defn smtp-host [ml]
  (or (:smtp-host (get lists ml))
      (:smtp-host config)
      (System/getenv "SUBSCRIBE_SMTP_HOST")))

(defn smtp-port [ml]
  (let [port (or (:smtp-port (get lists ml))
                 (:smtp-port config)
                 (System/getenv "SUBSCRIBE_SMTP_PORT"))]
    (if (string? port) (edn/read-string port) port)))

(defn smtp-login [ml]
  (or (:smtp-login (get lists ml))
      (:smtp-login config)
      (System/getenv "SUBSCRIBE_SMTP_LOGIN")))

(defn smtp-password [ml]
  (or (:smtp-password (get lists ml))
      (:smtp-password config)
      (System/getenv "SUBSCRIBE_SMTP_PASSWORD")))

(def admin-email (or (:admin-email config) (:from config) (smtp-login nil)))

(defn from
  "From field for transational messages for mailing list ML."
  [ml]
  (or (:from (get lists ml))
      (:from config)
      (smtp-login ml)))

(defn to
  "Address to send messages to about mailing list ML."
  [ml]
  (or (:to (get lists ml))
      (:admin-email config)
      (smtp-login ml)))

(defn msg-id
  "Message-Id part of transactional messages for mailing list ML."
  [ml]
  (or (:msg-id (get lists ml))
      (:msg-id config)))

(defn description
  "The description of the mailing list.
  It will be used as a fallback value when the backend does not allow
  to provide a description."
  [ml]
  (not-empty (:description (get lists ml))))

(defn list-name
  "The name of the mailing list.
  It will be used as a fallback value when the backend does not allow
  to provide a name."
  [ml]
  (not-empty (:list-name (get lists ml))))

(defn team
  "The name of the team of mailing list ML."
  [ml]
  (or (:team (get lists ml))
      (:team config)))

(defn return-url
  "The return url for mailing list ML."
  [ml]
  (or (:return-url (get lists ml))
      (:return-url config)
      base-url))

(defn tos-url
  "The terms of service url for mailing list ML."
  [ml]
  (or (:tos-url (get lists ml))
      (:tos-url config)))

(defn warn-every-x-subscribers
  "Warn every x subscribers for mailing list ML."
  [ml]
  (or (:warn-every-x-subscribers (get lists ml))
      (:warn-every-x-subscribers config)
      100))

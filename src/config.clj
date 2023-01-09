;; Copyright (c) 2019-2023 Bastien Guerry <bzg@bzg.fr>
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
    :lists-query-params             {:query-params {"limit" "100"}}
    ;; :subscribe-http-verb         "POST"
    :unsubscribe-http-verb          "DELETE"
    :subscribe-endpoint-fn          #(str "/lists/" (:mailing-list %) "/members")
    :unsubscribe-endpoint-fn        #(str "/lists/" (:mailing-list %) "/members/" (:subscriber %))
    :subscribe-params-fn            #(conj {:address (:subscriber %)} {:name (:username %)})
    ;; :unsubscribe-params-fn          nil
    :check-subscription-endpoint-fn #(str "/lists/" (:mailing-list %) "/members/" (:subscriber %))
    :check-subscription-validate-fn #(:subscribed (:member (:body %)))
    :auth                           {:basic-auth ["api" (System/getenv "MAILGUN_API_KEY")]}
    :replacements                   nil
    :data-keyword                   :items}
   {:backend                        "mailjet"
    :host                           "in-v3.mailjet.com"
    :api-url                        "https://api.mailjet.com/v3/REST"
    :lists-endpoint                 "/contactslist"
    :lists-query-params             {:query-params {"Limit" "1000"}}
    :subscribe-endpoint-fn          #(str "/contactslist/" (:mailing-list %) "/managecontact")
    ;; :unsubscribe-endpoint-fn     nil
    :subscribe-params-fn            #(merge {:Email (:subscriber %)}
                                            {:Name (:username %) :Action "addforce"})
    :unsubscribe-params-fn          #(merge {:Email (:subscriber %)}
                                            {:Name (:username %) :Action "remove"})
    :check-subscription-endpoint-fn #(str "/contact/" (:subscriber %) "/getcontactslists")
    :check-subscription-validate-fn (fn [{:keys [body id]}] (seq (first (filter #(= (:ListID %) id) (:Data body)))))
    :auth                           {:basic-auth [(System/getenv "MAILJET_API_KEY")
                                                  (System/getenv "MAILJET_API_SECRET")]}
    :replacements                   {:Address :address :Name :name :ID :list-id}
    :data-keyword                   :Data}
   {:backend                        "sendinblue"
    :host                           "smtp-relay.sendinblue.com"
    :api-url                        "https://api.sendinblue.com/v3"
    :lists-endpoint                 "/contacts/lists"
    ;; FIXME: sendinblue only allows to get 50 lists:
    ;; https://apidocs.sendinblue.com/list/#1
    :lists-query-params             nil
    :subscribe-endpoint-fn          (fn [_] "/contacts")
    :unsubscribe-endpoint-fn        #(str "/contacts/lists/" (:mailing-list %) "/contacts/remove")
    :subscribe-params-fn            #(conj {:updateEnabled true :listIds [(edn/read-string (:mailing-list %))]}
                                           {:email (:subscriber %)})
    :unsubscribe-params-fn          #(conj {:emails [(:subscriber %)]})
    :check-subscription-endpoint-fn #(str "/contacts/" (:subscriber %))
    :check-subscription-validate-fn #(contains? (into #{} (:listIds (:body %))) (:id %))
    :auth                           {:headers {"api-key" (System/getenv "SENDINBLUE_API_KEY")}}
    :replacements                   {:id :list-id :name :description}
    :data-keyword                   :lists}])

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
             "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.9.2/css/bulma.min.css"))

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

(defn short-name
  "The name of the mailing list.
  It will be used as a fallback value when the backend does not allow
  to provide a name."
  [ml]
  (not-empty (:name (get lists ml))))

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

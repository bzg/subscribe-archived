;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.handler
  (:require [org.httpkit.server :as http-kit]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [compojure.core :as compojure :refer (GET POST defroutes)]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clj-http.client :as http]
            [subscribe.views :as views]
            [subscribe.i18n :refer [i18n]]
            [subscribe.config :as config]
            [postal.core :as postal]
            [clojure.core.async :as async]
            [datahike.api :as d]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [taoensso.timbre.appenders (postal :as postal-appender)])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup logging

(timbre/set-config!
 {:level     :debug
  :output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})
  :appenders
  {:println (timbre/println-appender {:stream :auto})
   :spit    (appenders/spit-appender {:fname (config/log-file)})
   :postal  (postal-appender/postal-appender ;; :min-level :warn
             ^{:host config/mailgun-host
               :user (config/mailgun-login)
               :pass (config/mailgun-password)}
             {:from (config/mailgun-from)
              :to   (config/admin-email)})}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Create db and connect to it

(d/create-database (config/db-uri))
(def db-conn (d/connect (config/db-uri)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle email token creation and validation

(defn create-email-token
  "Store an email along with a token in the database."
  [token email]
  (let [emails (d/q `[:find ?e :where [?e :email ~email]] @db-conn)]
    (if-not (empty? emails)
      (do @(d/transact db-conn [[:db.fn/retractEntity (ffirst emails)]])
          (timbre/info (format (i18n [:regenerate-token]) email))))
    @(d/transact db-conn [{:db/id (d/tempid -1) :email email :token token}])))

(defn validate-token
  "Validate a token and delete the email/token pair."
  [token]
  (let [eids (d/q `[:find ?e :where [?e :token ~token]] @db-conn)
        eid  (ffirst eids)]
    (when eid
      (let [email (:email (d/pull @db-conn '[:email] eid))]
        @(d/transact db-conn [[:db.fn/retractEntity eid]])
        ;; Return the email address
        email))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle email sending

(defn send-email
  "Send an email."
  [{:keys [email subject body log]}]
  (postal/send-message
   {:host config/mailgun-host
    :port 587
    :user (config/mailgun-login)
    :pass (config/mailgun-password)}
   {:from    (config/mailgun-from)
    :to      email
    :subject subject
    :body    body})
  (timbre/info log))

(defn send-validation-link
  "Create a validation link and send it by email."
  [email]
  (let [token (.toString (java.util.UUID/randomUUID))]
    ;; FIXME: check email format
    (create-email-token token email)
    (send-email
     {:email   email
      :subject (format (i18n [:confirm-subscription]) (config/mailgun-mailing-list))
      :body    (format "%s/confirm/%s" (config/base-url) token)
      :log     (format (i18n [:validation-sent-to]) email)})))

(defn subscribe-address
  "Perform the actual email subscription to the mailing list."
  [email]
  (try
    (let [req (http/post
               (str config/mailgun-api-url config/mailgun-subscribe-endpoint)
               {:basic-auth  ["api" (config/mailgun-api-key)]
                :form-params {:address email}})]
      {:message (:message (json/parse-string (:body req) true))
       :result  "SUBSCRIBED"})
    (catch Exception e
      (let [message (:message (json/parse-string (:body (ex-data e)) true))]
        {:message message
         :result  "ERROR"}))))

(defn check-already-subscribed
  "Check if an email is already subscribed to the mailing list."
  [email]
  (try
    (let [req  (http/get
                (str config/mailgun-api-url config/mailgun-subscribe-endpoint "/" email)
                {:basic-auth ["api" (config/mailgun-api-key)]})
          body (json/parse-string (:body req) true)]
      (:subscribed (:member body)))
    (catch Exception e false)))

(defn subscribe-and-send-confirmation
  "Try to subscribe an email address to the mailing list."
  [token]
  (when-let [email (validate-token token)]
    (let [result (subscribe-address email)]
      (if-not (= (:result result) "SUBSCRIBED")
        (timbre/info (:message result))
        (send-email
         {:email   email
          :subject (format (config/mailgun-mailing-list))
          :body    (i18n [:thanks])
          :log     (format (i18n [:confirmation-sent-to]) email)})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Define async channels

(def subscribe-channel (async/chan 10))

(defn start-subscription-loop
  "Intercept subscription requests and send validation links."
  []
  (async/go
    (loop [email (async/<! subscribe-channel)]
      (send-validation-link email)        
      (recur (async/<! subscribe-channel)))))

(def confirm-channel (async/chan 10))

(defn start-confirmation-loop
  "Intercept confirmations and send confirmation email."
  []
  (async/go
    (loop [token (async/<! confirm-channel)]
      (subscribe-and-send-confirmation token)
      (recur (async/<! confirm-channel)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Application routes

(defroutes app-routes
  (GET "/" [] (views/home))
  (GET "/already-subscribed" [] (views/feedback (i18n [:already-subscribed])))
  (GET "/email-sent" [] (views/feedback (i18n [:validation-sent])))
  (GET "/thanks" [] (views/feedback (i18n [:successful-subscription])))
  (POST "/subscribe" [email]
        (if (check-already-subscribed email)
          (response/redirect "/already-subscribed")
          (do (async/go (async/>! subscribe-channel email))
              (response/redirect "/email-sent"))))
  (GET "/confirm/:token" [token]
       (do (async/go (async/>! confirm-channel token))
           (response/redirect "/thanks")))
  (route/resources "/")
  (route/not-found "404 error"))

(def app (-> app-routes
             (wrap-defaults (assoc site-defaults
                                   :security {:anti-forgery false}))
             ;; (wrap-defaults site-defaults)
             wrap-reload
             params/wrap-params))

(defn -main [& args]
  (start-subscription-loop)
  (start-confirmation-loop)
  (http-kit/run-server
   #'app {:port (Integer/parseInt (config/port))}))

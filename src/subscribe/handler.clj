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

(defn create-subscriber-token
  "Create a token in the database for a subscriber/mailing-list."
  [token subscriber mailing-list]
  (let [subscribers (d/q `[:find ?e :where [?e :subscriber ~subscriber]] @db-conn)]
    (if-not (empty? subscribers)
      (do @(d/transact db-conn [[:db.fn/retractEntity (ffirst subscribers)]])
          (timbre/info
           (format (i18n [:regenerate-token]) subscriber mailing-list))))
    @(d/transact db-conn [{:db/id        (d/tempid -1)
                           :token        token
                           :subscriber   subscriber
                           :mailing-list mailing-list}])))

(defn validate-token
  "Validate a token and delete the subscriber/token pair."
  [token]
  (let [eids (d/q `[:find ?e :where [?e :token ~token]] @db-conn)
        eid  (ffirst eids)]
    (when eid
      (let [infos (d/pull @db-conn '[:subscriber :mailing-list] eid)]
        @(d/transact db-conn [[:db.fn/retractEntity eid]])
        ;; Return {:subscriber "..." :mailing-list "..."}
        infos))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Get mailing lists informations

(defn get-lists-from-server
  "Get information for lists from the server."
  []
  (let [req  (http/get
              (str config/mailgun-api-url config/mailgun-lists-endpoint)
              {:basic-auth ["api" (config/mailgun-api-key)]})
        body (json/parse-string (:body req) true)]
    (:items body)))

(defn store-lists-information
  "Store lists information in the db."
  []
  (let [lists (get-lists-from-server)]
    (doall
     (map (fn [l] @(d/transact db-conn [(merge {:db/id (d/tempid -1)} l)]))
          lists))))

(defn get-lists-from-db []
  (let [lists (d/q `[:find ?e :where [?e :description]] @db-conn)]
    (map (fn [l]
           (d/pull @db-conn '[:address :description :name :members_count]
                   (first l)))
         lists)))

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
  [email-and-list]
  (let [subscriber   (get email-and-list "subscriber")
        mailing-list (get email-and-list "mailing-list")
        token        (.toString (java.util.UUID/randomUUID))]
    ;; FIXME: check email format
    (create-subscriber-token token subscriber mailing-list)
    (send-email
     {:email   subscriber
      :subject (format (i18n [:confirm-subscription]) mailing-list)
      :body    (format "%s/confirm/%s" (config/base-url) token)
      :log     (format (i18n [:validation-sent-to]) subscriber)})))

(defn subscribe-address
  "Perform the actual email subscription to the mailing list."
  [{:keys [subscriber mailing-list]}]
  (try
    (let [req (http/post
               (str config/mailgun-api-url
                    (config/mailgun-subscribe-endpoint mailing-list))
               {:basic-auth  ["api" (config/mailgun-api-key)]
                :form-params {:address subscriber}})]
      {:message (:message (json/parse-string (:body req) true))
       :result  "SUBSCRIBED"})
    (catch Exception e
      (let [message (:message (json/parse-string (:body (ex-data e)) true))]
        {:message message
         :result  "ERROR"}))))

(defn check-already-subscribed
  "Check if an email is already subscribed to the mailing list."
  [email-and-list]
  (let [subscriber   (get email-and-list "subscriber")
        mailing-list (get email-and-list "mailing-list")
        endpoint     (config/mailgun-subscribe-endpoint mailing-list)]
    (try
      (let [req  (http/get
                  (str config/mailgun-api-url endpoint "/" subscriber)
                  {:basic-auth ["api" (config/mailgun-api-key)]})
            body (json/parse-string (:body req) true)]
        (:subscribed (:member body)))
      (catch Exception e false))))

(defn subscribe-and-send-confirmation
  "Try to subscribe an email address to the mailing list."
  [token]
  (when-let [infos (validate-token token)]
    (let [result     (subscribe-address infos)
          subscriber (:subscriber infos)]
      (if-not (= (:result result) "SUBSCRIBED")
        (timbre/info (:message result))
        (send-email
         {:email   subscriber
          :subject (format (:mailing-list infos))
          :body    (i18n [:thanks])
          :log     (format (i18n [:confirmation-sent-to]) subscriber)})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Define async channels

(def subscribe-channel (async/chan 10))

(defn start-subscription-loop
  "Intercept subscription requests and send validation links."
  []
  (async/go
    (loop [req (async/<! subscribe-channel)]
      (send-validation-link req)        
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
  (GET "/" [] (views/mailing-lists (get-lists-from-db)))
  (GET "/already-subscribed" []
       (views/feedback (i18n [:error]) (i18n [:already-subscribed])))
  (GET "/email-sent" []
       (views/feedback (i18n [:thanks]) (i18n [:validation-sent])))
  (GET "/thanks" []
       (views/feedback (i18n [:done]) (i18n [:successful-subscription])))
  (GET "/list/:address" [address] (views/mailing-list address))
  (POST "/subscribe" req
        (if (check-already-subscribed (:form-params req))
          (response/redirect "/already-subscribed")
          (do (async/go (async/>! subscribe-channel (:form-params req)))
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
  (store-lists-information)
  (http-kit/run-server
   #'app {:port (Integer/parseInt (config/port))}))

;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.handler
  "Subscribe core functions."
  (:require [org.httpkit.server :as http-kit]
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
   :spit    (appenders/spit-appender {:fname config/log-file})
   :postal  (postal-appender/postal-appender ;; :min-level :warn
             ^{:host config/mailgun-host
               :user config/mailgun-login
               :pass config/mailgun-password}
             {:from config/from
              :to   config/admin-email})}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Create db and connect to it

(d/create-database config/db-uri)
(def db-conn (d/connect config/db-uri))

(defn mailgun-subscribe-endpoint
  "Return the mailgun API endpoint for `mailing-list`."
  [mailing-list]
  (str "/lists/" mailing-list "/members"))

(defn increment-subscribers
  "Increment the count of new subscribers to a mailing list.
  Send an email every X new subscribers, X being defined by
  `config/warn-every-x-subscribers`."
  [mailing-list & dec?]
  (let [count-id (ffirst (d/q `[:find ?c :where [?c :address ~mailing-list]] @db-conn))
        count    (:members_new (d/pull @db-conn '[:members_new] count-id))]
    @(d/transact db-conn [{:db/id count-id :members_new (if dec? (dec count) (inc count))}])
    (when (and (not dec?) (zero? (mod (inc count) config/warn-every-x-subscribers)))
      (timbre/warn
       (format "%s subscribers added to %s"
               config/warn-every-x-subscribers
               mailing-list)))))

(defn decrement-subscribers
  "Decrement the count of new subscribers to a mailing list."
  [mailing-list]
  (increment-subscribers mailing-list true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle email token creation and validation

(defn create-action-token
  "Create a token in the database for a subscriber/mailing-list."
  [token subscriber full-name mailing-list]
  (let [subscribers (d/q `[:find ?e :where [?e :subscriber ~subscriber]] @db-conn)]
    (when-not (empty? subscribers)
      @(d/transact db-conn [[:db.fn/retractEntity (ffirst subscribers)]])
      (timbre/info
       (format (i18n [:regenerate-token]) subscriber mailing-list)))
    @(d/transact db-conn [{:db/id        (d/tempid -1)
                           :token        token
                           :name         full-name
                           :subscriber   subscriber
                           :mailing-list mailing-list}])))

(defn validate-token
  "Validate a token and delete the subscriber/token pair."
  [token]
  (let [eids (d/q `[:find ?e :where [?e :token ~token]] @db-conn)
        eid  (ffirst eids)]
    (when eid
      (let [infos (d/pull @db-conn '[:subscriber :name :mailing-list] eid)]
        @(d/transact db-conn [[:db.fn/retractEntity eid]])
        ;; Return {:subscriber "..." :mailing-list "..." :name "..."}
        infos))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Get mailing lists informations

(defn get-lists-from-server
  "Get information for lists from the server."
  []
  (let [req  (http/get
              (str config/mailgun-api-url config/mailgun-lists-endpoint)
              {:basic-auth ["api" config/mailgun-api-key]})
        body (json/parse-string (:body req) true)]
    (:items body)))

(defn initialize-lists-information
  "Store lists information in the db."
  []
  (let [lists (get-lists-from-server)]
    (doall
     (map (fn [l] @(d/transact
                    db-conn [(merge {:db/id (d/tempid -1) :members_new 0} l)]))
          lists))))

(defn get-lists-from-db
  "Get the list of mailing lists stored in the database."
  []
  (let [lists (d/q '[:find ?e :where [?e :description]] @db-conn)]
    (map #(d/pull @db-conn '[:address :description
                             :name :members_count
                             :members_new]
                  (first %))
         lists)))

(defn get-lists-filtered
  "Get the list of mailing list while including and excluding lists
  depending on `config/lists-include/exclude-regexp`."
  [lists]
  (->> lists
       (filter #(not (re-matches config/lists-exclude-regexp (:address %))))
       (filter #(re-matches config/lists-include-regexp (:address %)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle email sending

(defn send-email
  "Send a templated email."
  [{:keys [email name subject body log]}]
  (try
    (postal/send-message
     {:host config/mailgun-host
      :port 587
      :user config/mailgun-login
      :pass config/mailgun-password}
     {:from    config/from
      :to      email
      :subject subject
      :body    (str (if name (format (i18n [:opening-name]) name)
                        (i18n [:opening-no-name]))
                    "\n\n" body "\n\n"
                    (i18n [:closing]) "\n\n"
                    (format "-- \n%s" (or config/team config/return-url)))})
    (catch Exception e
      (timbre/error (ex-data e))))
  (timbre/info log))

(defn send-validation-link
  "Create a validation link and send it by email."
  [email-and-list & unsubscribe?]
  (let [subscriber   (get email-and-list "subscriber")
        name         (or (get email-and-list "name") "")
        mailing-list (get email-and-list "mailing-list")
        token        (str (java.util.UUID/randomUUID))]
    ;; FIXME: check email format
    (create-action-token token subscriber name mailing-list)
    (send-email
     {:email   subscriber
      :name    name
      :subject (format (i18n (if unsubscribe?
                               [:confirm-unsubscription]
                               [:confirm-subscription])) mailing-list)
      :body    (str
                (format (i18n (if unsubscribe?
                                [:confirm-unsubscription]
                                [:confirm-subscription])) mailing-list)
                ":\n"
                (format (str "%s/confirm-"
                             (if unsubscribe? "un")
                             "subscription/%s") config/base-url token))
      :log     (format (i18n [:validation-sent-to]) mailing-list subscriber)})))

(defn unsubscribe-address
  "Perform the actual email unsubscription to the mailing list."
  [{:keys [subscriber mailing-list]}]
  (try
    (let [req (http/delete
               (str config/mailgun-api-url
                    (mailgun-subscribe-endpoint mailing-list)
                    "/" subscriber)
               {:basic-auth ["api" config/mailgun-api-key]})]
      {:message (:message (json/parse-string (:body req) true))
       :result  "UNSUBSCRIBED"})
    (catch Exception e
      (let [message (:message (json/parse-string (:body (ex-data e)) true))]
        {:message message
         :result  "ERROR"}))))

(defn subscribe-address
  "Perform the actual email subscription to the mailing list."
  [{:keys [subscriber name mailing-list]}]
  (try
    (let [req (http/post
               (str config/mailgun-api-url
                    (mailgun-subscribe-endpoint mailing-list))
               {:basic-auth  ["api" config/mailgun-api-key]
                :form-params {:address subscriber :name name}})]
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
        endpoint     (mailgun-subscribe-endpoint mailing-list)]
    (try
      (let [req  (http/get
                  (str config/mailgun-api-url endpoint "/" subscriber)
                  {:basic-auth ["api" config/mailgun-api-key]})
            body (json/parse-string (:body req) true)]
        (:subscribed (:member body)))
      (catch Exception e false))))

(defn subscribe-and-send-confirmation
  "Subscribe an email address to a mailing list.
  Send a confirmation email."
  [token]
  (when-let [infos (validate-token token)]
    (let [result       (subscribe-address infos)
          name         (:name infos)
          subscriber   (:subscriber infos)
          mailing-list (:mailing-list infos)]
      (if-not (= (:result result) "SUBSCRIBED")
        (timbre/info (:message result))
        (do (increment-subscribers mailing-list)
            (send-email
             {:email   subscriber
              :name    name
              :subject (format (i18n [:subscribed-to]) mailing-list)
              :body    (format (i18n [:subscribed-message]) mailing-list)
              :log     (format (i18n [:confirmation-sent-to]) mailing-list subscriber)}))))))

(defn unsubscribe-and-send-confirmation
  "Unsubscribe an email address from a mailing list.
  Send a confirmation email."
  [token]
  (when-let [infos (validate-token token)]
    (let [result       (unsubscribe-address infos)
          name         (:name infos)
          subscriber   (:subscriber infos)
          mailing-list (:mailing-list infos)]
      (if-not (= (:result result) "UNSUBSCRIBED")
        (timbre/info (:message result))
        (do (decrement-subscribers mailing-list)
            (send-email
             {:email   subscriber
              :name    name
              :subject (format (i18n [:unsubscribed-from]) mailing-list)
              :body    (format (i18n [:unsubscribed-message]) mailing-list)
              :log     (format (i18n [:confirmation-sent-to]) mailing-list subscriber)}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Define async channels

(def subscribe-channel (async/chan 10))
(def unsubscribe-channel (async/chan 10))
(def subscribe-confirmation-channel (async/chan 10))
(def unsubscribe-confirmation-channel (async/chan 10))

(defn start-subscription-loop
  "Intercept subscription requests and send validation links."
  []
  (async/go
    (loop [req (async/<! subscribe-channel)]
      (send-validation-link req)
      (recur (async/<! subscribe-channel)))))

(defn start-unsubscription-loop
  "Intercept unsubscription requests and send validation links."
  []
  (async/go
    (loop [req (async/<! unsubscribe-channel)]
      (send-validation-link req true)
      (recur (async/<! unsubscribe-channel)))))

(defn start-subscribe-confirmation-loop
  "Intercept confirmations and send corresponding emails."
  []
  (async/go
    (loop [token (async/<! subscribe-confirmation-channel)]
      (subscribe-and-send-confirmation token)
      (recur (async/<! subscribe-confirmation-channel)))))

(defn start-unsubscribe-confirmation-loop
  "Intercept confirmations and send the corresponding emails."
  []
  (async/go
    (loop [token (async/<! unsubscribe-confirmation-channel)]
      (unsubscribe-and-send-confirmation token)
      (recur (async/<! unsubscribe-confirmation-channel)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Application routes

(defroutes app-routes
  (GET "/" [] (views/mailing-lists (get-lists-filtered (get-lists-from-db))))
  (GET "/already-subscribed" []
       (views/feedback (i18n [:error]) (i18n [:already-subscribed])))
  (GET "/not-subscribed" []
       (views/feedback (i18n [:error]) (i18n [:not-subscribed])))
  (GET "/email-sent" []
       (views/feedback (i18n [:thanks]) (i18n [:validation-sent])))
  (GET "/thanks" []
       (views/feedback (i18n [:done]) (i18n [:successful-subscription])))
  (GET "/bye" []
       (views/feedback (i18n [:done]) (i18n [:successful-unsubscription])))
  (GET "/subscribe/:address" [address] (views/subscribe-to-mailing-list address))
  (GET "/unsubscribe/:address" [address] (views/unsubscribe-to-mailing-list address))
  (POST "/subscribe" req
        (if (check-already-subscribed (:form-params req))
          (response/redirect "/already-subscribed")
          (do (async/go (async/>! subscribe-channel (:form-params req)))
              (response/redirect "/email-sent"))))
  (POST "/unsubscribe" req
        (if-not (check-already-subscribed (:form-params req))
          (response/redirect "/not-subscribed")
          (do (async/go (async/>! unsubscribe-channel (:form-params req)))
              (response/redirect "/email-sent"))))
  (GET "/confirm-subscription/:token" [token]
       (do (async/go (async/>! subscribe-confirmation-channel token))
           (response/redirect "/thanks")))
  (GET "/confirm-unsubscription/:token" [token]
       (do (async/go (async/>! unsubscribe-confirmation-channel token))
           (response/redirect "/bye")))
  (route/resources "/")
  (route/not-found (views/error)))

(def app (-> app-routes
             (wrap-defaults site-defaults)
             params/wrap-params))

(defn -main
  "Initialize the db, the loops and the web serveur."
  [& args]
  (initialize-lists-information)
  (start-subscription-loop)
  (start-unsubscription-loop)
  (start-subscribe-confirmation-loop)
  (start-unsubscribe-confirmation-loop)
  (http-kit/run-server #'app {:port config/port})
  (println (str "Subscribe application started on localhost:" {:port config/port})))

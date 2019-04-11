;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.handler
  (:gen-class)
  (:require [org.httpkit.server :as http-kit]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [compojure.core :as compojure :refer (GET POST defroutes)]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [hiccup.page :as h]
            [hiccup.element :as he]
            [clj-http.client :as http]
            [subscribe.i18n :refer [i18n]]
            [subscribe.config :as config]
            [postal.core :as postal]
            [clojure.core.async :as async]
            [datahike.api :as d]
            [cheshire.core :as json]))

;; TODO:
;;
;; - Check email format
;; - Fix/enhance UI strings
;; - Enable antiforgery
;; - Add unsubscribe link

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Create db and db connection

(d/create-database config/db-uri)
(def db-conn (d/connect config/db-uri))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle email token creation and validation

(defn create-email-token [token email]
  (let [emails (d/q `[:find ?e :where [?e :email ~email]] @db-conn)]
    (if-not (empty? emails)
      @(d/transact db-conn [[:db.fn/retractEntity (ffirst emails)]]))
    @(d/transact db-conn [{:db/id (d/tempid -1) :email email :token token}])))

(defn validate-token [token]
  (let [eids (d/q `[:find ?e :where [?e :token ~token]] @db-conn)
        eid  (ffirst eids)]
    (when eid
      (let [email (:email (d/pull @db-conn '[:email] eid))]
        @(d/transact db-conn [[:db.fn/retractEntity eid]])
        ;; Return the email address
        email))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle email

(defn send-email [{:keys [email subject body log]}]
  (postal/send-message
   {:host config/mailgun-host
    :port 587
    :user (config/mailgun-login)
    :pass (config/mailgun-password)}
   {:from    (config/mailgun-from)
    :to      email
    :subject subject
    :body    body})
  (println log))

(defn send-validation-link [email]
  (let [token (.toString (java.util.UUID/randomUUID))]
    ;; FIXME: check email format
    (create-email-token token email)
    (send-email
     {:email   email
      :subject (format "Confirmez votre inscription à %s" (config/mailgun-mailing-list))
      :body    (format "%s/confirm/%s" (config/base-url) token)
      :log     (str "Validation link sent to " email)})))

(defn subscribe-address [email]
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

(defn check-already-subscribed [email]
  (try
    (let [req  (http/get
                (str config/mailgun-api-url config/mailgun-subscribe-endpoint "/" email)
                {:basic-auth ["api" (config/mailgun-api-key)]})
          body (json/parse-string (:body req) true)]
      (:subscribed (:member body)))
    (catch Exception e false)))

(defn subscribe-and-send-confirmation [token]
  (when-let [email (validate-token token)]
    (let [result (subscribe-address email)]
      (if-not (= (:result result) "SUBSCRIBED")
        (println (:message result))
        (send-email
         {:email   email
          :subject (format "Votre inscription à la liste %s est bien prise en compte" (config/mailgun-mailing-list))
          :body    "Merci"
          :log     "Final confirmation email sent"})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Define async channels

(def subscribe-channel (async/chan 10))

(defn start-subscription-loop []
  (async/go
    (loop [email (async/<! subscribe-channel)]
      (send-validation-link email)        
      (recur (async/<! subscribe-channel)))))

(def confirm-channel (async/chan 10))

(defn start-confirmation-loop []
  (async/go
    (loop [token (async/<! confirm-channel)]
      (subscribe-and-send-confirmation token)
      (recur (async/<! confirm-channel)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; HTML content

(defmacro default-page [content]
  `(h/html5
    {:lang "fr"}
    [:head
     [:title (i18n [:title])]
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1, shrink-to-fit=yes"}]
     (h/include-css "https://maxcdn.bootstrapcdn.com/bootstrap/4.3.0/css/bootstrap.min.css")
     [:style "body {margin-top: 2em;}"]]
    [:body {:class "bg-light"}
     [:div {:class "container" :style "width:70%"}
      ~content]]))

(defn- home-page []
  (default-page
   `([:h1 ~(config/mailgun-mailing-list)]
     [:br]
     [:form
      {:action "/subscribe" :method "post"}
      [:input {:name        "email" :type  "text"
               :size        "30"    :class "form-control"
               :placeholder ~(i18n [:email-address])
               :required    true}]
      [:br]
      [:input {:type  "submit" :value ~(i18n [:subscribe])
               :class "btn btn-warning btn-lg"}]])))

(defn- feedback-page [message]
  (default-page
   `([:h1 ~(config/mailgun-mailing-list)]
     [:br]
     [:h2 ~message]
     [:br]
     [:a {:href ~(config/return-url)} ~(i18n [:return-to-site])])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Application routes

(defroutes app-routes
  (GET "/" [] (home-page))
  (GET "/already-subscribed" [] (feedback-page "Already subscribed"))
  (GET "/email-sent" [] (feedback-page "Email sent with validation link"))
  (GET "/thanks" [] (feedback-page (i18n [:successful-subscription])))
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

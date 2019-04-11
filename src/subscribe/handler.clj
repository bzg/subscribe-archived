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
            [postal.core :as postal]
            [clojure.core.async :as async]
            [datahike.api :as d]))

;; TODO
;;
;; - Update license to v2
;; - Fix already subscribed feedback
;; - Enhance UI strings
;; - Use bulma
;; - Enable antiforgery
;; - Add unsubscribe link
;; - Add email validation?

(def mailgun-api-url "https://api.mailgun.net/v3")
(def mailgun-api-key (or (System/getenv "MAILGUN_API_KEY") ""))
(def mailgun-login (or (System/getenv "MAILGUN_LOGIN") ""))
(def mailgun-password (or (System/getenv "MAILGUN_PASSWORD") ""))
(def mailgun-from (or (System/getenv "MAILGUN_FROM") (System/getenv "MAILGUN_LOGIN") ""))
(def mailgun-mailing-list (or (System/getenv "MAILGUN_MAILING_LIST") ""))
(def mailgun-subscribe-endpoint (str "/lists/" mailgun-mailing-list "/members"))

(def base-url (or (System/getenv "MAILGUN_BASE_URL") ""))
(def return-url (or (System/getenv "MAILGUN_RETURN_URL") ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Create db and db connection

(def db-uri "datahike:mem:///mailgun-subscribe")
(d/create-database db-uri)
(def db-conn (d/connect db-uri))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle token creation and validation

(defn create-email-token [token email]
  (let [emails (d/q `[:find ?e :where [?e :email ~email]] @db-conn)]
    (if-not (empty? emails)
      (println (format "%s already in the db" email))
      @(d/transact db-conn
                   [{:db/id (d/tempid -1)
                     :email email
                     :token token}]))))

(defn validate-token [token]
  (let [eids (d/q `[:find ?e :where [?e :token ~token]] @db-conn)
        eid  (ffirst eids)]
    (when eid
      (let [email (:email (d/pull @db-conn '[:email] eid))]
        @(d/transact db-conn [[:db.fn/retractEntity eid]])
        email))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle email

(defn send-email [{:keys [email subject body]}]
  (postal/send-message
   {:host "smtp.mailgun.org"
    :port 587
    :user mailgun-login
    :pass mailgun-password}
   {:from    mailgun-from
    :to      email
    :subject subject
    :body    body}))

(defn send-validation-link [email]
  (let [token (.toString (java.util.UUID/randomUUID))]
    (if (create-email-token token email)
      (send-email
       {:email   email
        :subject (format "Confirmez votre inscription à %s" mailgun-mailing-list)
        :body    (format "%s/confirm/%s" base-url token)}))))

(defn subscribe-address [email]
  (http/post (str mailgun-api-url mailgun-subscribe-endpoint)
             {:basic-auth  ["api" mailgun-api-key]
              :form-params {:address email}}))

(defn subscribe-and-send-confirmation [token]
  (when-let [email (validate-token token)]
    (try
      (do (subscribe-address email)
          (send-email
           {:email   email
            :subject (format "Votre inscription à la liste %s est bien prise en compte" mailgun-mailing-list)
            :body    "Merci"}))
      (catch Throwable e
        (println "Can't subscribe to mailgun")))))

(def email-channel (async/chan 10))

(defn start-email-loop []
  (async/go
    (loop [email (async/<! email-channel)]
      (try
        (do (send-validation-link email)
            (println email "subscribed"))
        (catch Throwable e
          (println "Can't create token or send email")))
      (recur (async/<! email-channel)))))

(def confirm-channel (async/chan 10))

(defn start-confirmation-loop []
  (async/go
    (loop [token (async/<! confirm-channel)]
      (try
        (do (subscribe-and-send-confirmation token)
            (println "Confirmation email sent"))
        (catch Throwable e (println "Can't confirm token")))
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
   `([:h1 ~mailgun-mailing-list]
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

(defn- thanks-page []
  (default-page
   `([:h1 ~mailgun-mailing-list]
     [:br]
     [:h2 ~(i18n [:successful-subscription])]
     [:br]
     [:a {:href ~return-url} ~(i18n [:return-to-site])])))

(defroutes app-routes
  (GET "/" [] (home-page))
  (GET "/thanks" [] (thanks-page))
  (POST "/subscribe" [email]
        (do (async/go (async/>! email-channel email))
            (response/redirect "/thanks")))
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
  (start-email-loop)
  (start-confirmation-loop)
  (http-kit/run-server
   #'app {:port (Integer/parseInt (or (System/getenv "SUBSCRIBE_PORT") "3000"))}))

;; (-main)

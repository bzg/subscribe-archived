;; Copyright (c) 2019-2020 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.handler
  "Subscribe core functions."
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [compojure.core :as compojure :refer (GET POST defroutes)]
            [compojure.route :as route]
            [clj-http.lite.client :as http]
            [clojure.set :as set]
            [subscribe.views :as views]
            [subscribe.i18n :refer [i]]
            [subscribe.config :as config]
            [postal.core :as postal]
            [postal.support]
            [clojure.core.async :as async]
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
             ^{:host (config/smtp-host nil)
               :user (config/smtp-login nil)
               :pass (config/smtp-password nil)}
             {:from (config/from nil)
              :to   config/admin-email})}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handle mailing lists information

(def lists (atom nil))

(defn cleanup-list-data [b]
  (comp
   (map #(set/rename-keys % (:replacements b)))
   (map #(merge % {:backend     (:backend b)
                   :members_new 0
                   :list-name   (or (config/list-name (:address %))
                                    (not-empty (:list-name %))
                                    (:address %))
                   :description (or (not-empty (:description %))
                                    (config/description (:address %))
                                    (:address %))}))
   (map #(select-keys % [:list-name :address :description
                         :backend :list-id :members_new]))))

(defn get-lists-filtered [lists]
  (let [ex-re config/lists-exclude-regexp
        in-re config/lists-include-regexp]
    (filter #(and (or (nil? ex-re) (not (re-find ex-re (:address %))))
                  (or (nil? in-re) (re-find in-re (:address %))))
            lists)))

(defn get-lists-from-server
  "Get information for lists from the server."
  []
  (let [l (atom nil)]
    (doseq [{:keys [api-url lists-endpoint basic-auth] :as b}
            config/backends-expanded]
      (let [result
            (json/parse-string
             (:body
              (try (http/get
                    (str api-url lists-endpoint)
                    {:basic-auth basic-auth})
                   (catch Exception e
                     {:message (:message (json/parse-string
                                          (:body (ex-data e)) true))
                      :result  "ERROR"})))
             true)]
        (swap! l concat
               (sequence (cleanup-list-data b)
                         ((:data-keyword b) result)))))
    (when (reset! lists
                  (into {} (map (fn [m] {(:address m) m})
                                (get-lists-filtered @l))))
      (timbre/info "Information from mailing lists retrieved"))))

(defn get-list-backend-config
  "Get the backend configuration for mailing list ML."
  [ml]
  (some #(when (= (:backend %) (get-in @lists [ml :backend])) %)
        config/backends-expanded))

(defn increment-subscribers
  "Increment the count of new subscribers to a mailing list.
  Send an email every X new subscribers, X being defined by
  `config/warn-every-x-subscribers`."
  [mailing-list & dec?]
  (let [members_today (:members_new (get @lists mailing-list))
        warn-every-x  (config/warn-every-x-subscribers mailing-list)]
    (swap! lists update-in [mailing-list :members_new] inc)
    (when (and (not dec?) (zero? (mod (inc members_today) warn-every-x)))
      (timbre/with-config
        {:level     :debug
         :output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})
         :appenders
         {:println (timbre/println-appender {:stream :auto})
          :spit    (appenders/spit-appender {:fname config/log-file})
          :postal  (postal-appender/postal-appender ;; :min-level :warn
                    ^{:host (config/smtp-host mailing-list)
                      :user (config/smtp-login mailing-list)
                      :pass (config/smtp-password mailing-list)}
                    {:from (config/from mailing-list)
                     :to   (config/to mailing-list)})}}
        (timbre/warn
         (format (i (config/locale nil) [:subscribers-added])
                 (config/warn-every-x-subscribers mailing-list)
                 mailing-list))))))

(defn decrement-subscribers
  "Decrement the count of new subscribers to a mailing list."
  [mailing-list]
  (increment-subscribers mailing-list true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handle subscribers information

(def subscribers (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handle tokens

(defn create-action-token
  "Create a token in the database for a subscriber/mailing-list."
  [token subscriber username mailing-list]
  (when (get @subscribers token)
    (timbre/info
     (format (i (config/locale nil) [:regenerate-token]) subscriber mailing-list)))
  (swap! subscribers conj
         [token
          {:username     username
           :backend      (:backend (get-list-backend-config mailing-list))
           :subscriber   subscriber
           :mailing-list mailing-list}]))

(defn validate-token
  "Validate a token and delete the subscriber/token pair."
  [token]
  (let [infos (get @subscribers token)]
    (swap! subscribers dissoc token)
    infos))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle emails

(defn build-email-body
  "Build the plain text and HTML parts of the email."
  [{:keys [mailing-list username html-body plain-body]}]
  (let [ml        (get @lists mailing-list)
        lang      (config/locale (:address ml))
        ml-desc   (or (:description ml) (config/description ml))
        list-name (:list-name ml)]
    [:alternative
     {:type    "text/plain; charset=utf-8"
      :content (str (if username (format (i lang [:opening-name]) username)
                        (i lang [:opening-no-name]))
                    "\n\n" plain-body "\n\n"
                    (i lang [:closing]) "\n\n-- \n"
                    (or (config/team mailing-list)
                        (config/return-url mailing-list)))}
     {:type    "text/html; charset=utf-8"
      :content (views/default
                list-name ml-desc mailing-list lang
                [:div
                 [:p (if username (format (i lang [:opening-name]) username)
                         (i lang [:opening-no-name]))]
                 [:p (or html-body plain-body)]
                 [:p (i lang [:closing])]
                 [:p [:a {:href (config/return-url mailing-list)}
                      (or (config/team ml)
                          (config/return-url mailing-list))]]])}]))

(defn send-email
  "Send a templated email."
  [{:keys [email username subject plain-body html-body log mailing-list]}]
  (try
    (postal/send-message
     {:host (config/smtp-host mailing-list)
      :port (config/smtp-port mailing-list)
      :user (config/smtp-login mailing-list)
      :pass (config/smtp-password mailing-list)}
     {:from             (config/from mailing-list)
      :message-id       #(postal.support/message-id
                          (config/msg-id mailing-list))
      :to               email
      :subject          subject
      :body             (build-email-body {:mailing-list mailing-list
                                           :username     username
                                           :plain-body   plain-body
                                           :html-body    html-body})
      :List-Unsubscribe (str "<" config/base-url "/unsubscribe/" mailing-list ">")})
    (timbre/info log)
    (catch Exception e
      (timbre/error (str "Can't send email: " (:cause (Throwable->map e)))))))

(defn send-validation-link
  "Create a validation link and send it by email."
  [email-and-list & unsubscribe?]
  (let [subscriber   (get email-and-list "subscriber")
        username     (or (get email-and-list "username") "")
        mailing-list (get email-and-list "mailing-list")
        ml           (get @lists mailing-list)
        lang         (config/locale ml)
        token        (str (java.util.UUID/randomUUID))]
    ;; FIXME: check email address format before sending?
    (create-action-token token subscriber username mailing-list)
    (send-email
     {:email        subscriber
      :username     username
      :mailing-list mailing-list
      :subject      (format (i lang (if unsubscribe?
                                      [:confirm-unsubscription]
                                      [:confirm-subscription]))
                            (:list-name ml))
      :plain-body   (str
                     (format (i lang (if unsubscribe?
                                       [:confirm-unsubscription]
                                       [:confirm-subscription]))
                             (:list-name ml))
                     ":\n"
                     (format (str "%s/confirm-"
                                  (when unsubscribe? "un")
                                  "subscription/%s")
                             config/base-url token))
      :html-body    (str
                     (format (i lang (if unsubscribe?
                                       [:confirm-unsubscription]
                                       [:confirm-subscription]))
                             (:list-name ml))
                     ":\n"
                     (str "<a href=\""
                          (format (str "%s/confirm-"
                                       (when unsubscribe? "un")
                                       "subscription/%s")
                                  config/base-url token)
                          "\">" (i lang [:click-here]) "</a>"))
      :log          (format (i lang [:validation-sent-to])
                            (str mailing-list " (" (:backend ml) ")")
                            subscriber)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle subscriptions

(defn subscribe-or-unsubscribe-address
  "Perform the actual email subscription to the mailing list."
  [{:keys [subscriber username mailing-list backend action]}]
  (let [b              (some  #(when (= (:backend %) backend) %)
                              config/backends-expanded)
        http-verb      (if (= action "subscribe")
                         (:subscribe-http-verb b)
                         (or (:unsubscribe-http-verb b)
                             (:subscribe-http-verb b)))
        endpoint-fn    (if (= action "subscribe")
                         (:subscribe-endpoint-fn b)
                         (or (:unsubscribe-endpoint-fn b)
                             (:subscribe-endpoint-fn b)))
        form-params-fn (if (= action "subscribe")
                         (:subscribe-form-params-fn b)
                         (or (:unsubscribe-form-params-fn b)
                             (:subscribe-form-params-fn b)))
        result-msg     (if (= action "subscribe")
                         {:message " subscribed to " :output "subscribe"}
                         {:message " unsubscribed to " :output "unsubscribe"})]
    (try
      (apply (if (= http-verb "DELETE") http/delete http/post)
             [(str (:api-url b) (endpoint-fn mailing-list subscriber))
              {:basic-auth  (:basic-auth b)
               :form-params (form-params-fn subscriber username)}])
      {:message (str subscriber (:message result-msg) mailing-list " on " backend)
       :result  (:output result-msg)}
      (catch Exception e
        (let [message (:message (json/parse-string (:body (ex-data e)) true))]
          {:message message
           :result  "ERROR"})))))

(defn subscribe-and-send-confirmation
  "Subscribe an email address to a mailing list.
  Send a confirmation email."
  [token unsubscribe]
  (when-let [{:keys [subscriber username mailing-list] :as infos}
             (validate-token token)]
    (let [action     (if unsubscribe "unsubscribe" "subscribe")
          inc-or-dec (if unsubscribe decrement-subscribers increment-subscribers)
          result     (subscribe-or-unsubscribe-address (merge infos {:action action}))]
      (if-not (= (:result result) action)
        (timbre/info (:message result))
        (do (inc-or-dec mailing-list)
            (let [{:keys [address backend]} (get @lists mailing-list)
                  lang                      (config/locale address)
                  subscribed-to
                  (if unsubscribe (i lang [:unsubscribed-to])
                      (i lang [:subscribed-to]))
                  subscribed-message
                  (if unsubscribe (i lang [:unsubscribed-message])
                      (i lang [:subscribed-message]))]
              (send-email
               {:email        subscriber
                :username     username
                :mailing-list mailing-list
                :subject      (format subscribed-to mailing-list)
                :plain-body   (format subscribed-message mailing-list)
                :log          (format (i lang [:confirmation-sent-to])
                                      (str mailing-list " (" backend ")")
                                      subscriber)})))))))

(defn check-already-subscribed
  "Check if an email is already subscribed to the mailing list."
  [email-and-list]
  (let [subscriber      (get email-and-list "subscriber")
        mailing-list    (get email-and-list "mailing-list")
        backend-conf    (get-list-backend-config mailing-list)
        mailing-list-id (:list-id (get @lists mailing-list))]
    (try
      (let [req  (http/get
                  (str (:api-url backend-conf)
                       ((:check-subscription-endpoint-fn backend-conf)
                        subscriber mailing-list))
                  {:basic-auth (:basic-auth backend-conf)})
            body (json/parse-string (:body req) true)]
        ((:check-subscription-validate-fn backend-conf) body mailing-list-id))
      (catch Exception _ nil))))

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
      (subscribe-and-send-confirmation token nil)
      (recur (async/<! subscribe-confirmation-channel)))))

(defn start-unsubscribe-confirmation-loop
  "Intercept confirmations and send the corresponding emails."
  []
  (async/go
    (loop [token (async/<! unsubscribe-confirmation-channel)]
      (subscribe-and-send-confirmation token true)
      (recur (async/<! unsubscribe-confirmation-channel)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Application routes

(defroutes app-routes
  (GET "/" [] (views/mailing-lists @lists))
  (GET "/already-subscribed/:ml" [ml]
       (let [ml-opts (get @lists ml)
             lang    (config/locale (:address ml-opts))]
         (views/feedback
          (i lang [:error])
          ml-opts
          (i lang [:already-subscribed]))))
  (GET "/not-subscribed/:ml" [ml]
       (let [ml-opts (get @lists ml)
             lang    (config/locale (:address ml-opts))]
         (views/feedback
          (i lang [:error])
          ml-opts
          (i lang [:not-subscribed]))))
  (GET "/email-sent/:ml" [ml]
       (let [ml-opts (get @lists ml)
             lang    (config/locale (:address ml-opts))]
         (views/feedback
          (i lang [:thanks])
          ml-opts
          (i lang [:validation-sent]))))
  (GET "/thanks" []
       (views/feedback (i (config/locale nil) [:done])
                       nil (i (config/locale nil)
                              [:successful-subscription])))
  (GET "/bye" []
       (views/feedback (i (config/locale nil) [:done])
                       nil (i (config/locale nil)
                              [:successful-unsubscription])))
  (GET "/subscribe/:ml" [ml] (views/subscribe-to-mailing-list
                              (get @lists ml)))
  (GET "/unsubscribe/:ml" [ml] (views/unsubscribe-from-mailing-list
                                (get @lists ml)))
  (POST "/subscribe" req
        (let [params (:form-params req)
              ml     (get params "mailing-list")]
          (if (check-already-subscribed params)
            (response/redirect (str "/already-subscribed/" ml))
            (do (async/go (async/>! subscribe-channel params))
                (response/redirect (str "/email-sent/" ml))))))
  (POST "/unsubscribe" req
        (let [params (:form-params req)
              ml     (get params "mailing-list")]
          (if-not (check-already-subscribed params)
            (response/redirect (str "/not-subscribed/" ml))
            (do (async/go (async/>! unsubscribe-channel params))
                (response/redirect (str "/email-sent/" ml))))))
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
  []
  (get-lists-from-server)
  (start-subscription-loop)
  (start-unsubscription-loop)
  (start-subscribe-confirmation-loop)
  (start-unsubscribe-confirmation-loop)
  (jetty/run-jetty #'app {:port config/port :join? false})
  (println (str "Subscribe application started on localhost:" config/port)))

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
             ^{:host (config/smtp-host nil)
               :user (config/smtp-login nil)
               :pass (config/smtp-password nil)}
             {:from (config/from nil)
              :to   config/admin-email})}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Create db and connect to it

(d/create-database config/db-uri :schema-on-read true)
(def db-conn (d/connect config/db-uri))

(defn increment-subscribers
  "Increment the count of new subscribers to a mailing list.
  Send an email every X new subscribers, X being defined by
  `config/warn-every-x-subscribers`."
  [mailing-list & dec?]
  (let [count-id (ffirst (d/q `[:find ?c :where [?c :address ~mailing-list]] @db-conn))
        count    (:members_new (d/pull @db-conn '[:members_new] count-id))]
    @(d/transact! db-conn [{:db/id count-id :members_new (if dec? (dec count) (inc count))}])
    (when (and (not dec?)
               (zero? (mod (inc count) (config/warn-every-x-subscribers mailing-list))))
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
         (format (i18n [:subscribers-added])
                 (config/warn-every-x-subscribers mailing-list)
                 mailing-list))))))

(defn decrement-subscribers
  "Decrement the count of new subscribers to a mailing list."
  [mailing-list]
  (increment-subscribers mailing-list true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle email token creation and validation

(defn get-ml-backend-config
  "Get the backend configuration for mailing list ML."
  [ml-address]
  (let [l (d/q `[:find ?l :where [?l :address ~ml-address]] @db-conn)]
    (when (not-empty l)
      (first (filter #(= (:backend %) (:backend (d/pull @db-conn '[:backend] (ffirst l))))
                     config/backends-expanded)))))

(defn create-action-token
  "Create a token in the database for a subscriber/mailing-list."
  [token subscriber full-name mailing-list]
  (let [subscribers (d/q `[:find ?e :where [?e :subscriber ~subscriber]] @db-conn)]
    (when-not (empty? subscribers)
      @(d/transact! db-conn [[:db.fn/retractEntity (ffirst subscribers)]])
      (timbre/info
       (format (i18n [:regenerate-token]) subscriber mailing-list)))
    @(d/transact! db-conn [{:db/id        (d/tempid -1)
                            :token        token
                            :name         full-name
                            :backend      (:backend (get-ml-backend-config mailing-list))
                            :subscriber   subscriber
                            :mailing-list mailing-list}])))

(defn validate-token
  "Validate a token and delete the subscriber/token pair."
  [token]
  (let [eids (d/q `[:find ?e :where [?e :token ~token]] @db-conn)
        eid  (ffirst eids)]
    (when eid
      (let [infos (d/pull @db-conn '[:subscriber :name :mailing-list :backend] eid)]
        @(d/transact! db-conn [[:db.fn/retractEntity eid]])
        ;; Return {:subscriber "..." :mailing-list "..." :name "..." :backend "..."}
        infos))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Get mailing lists informations
(defn cleanup-list-data [data b]
  (->> data
       (map #(clojure.set/rename-keys % (:replacements b)))
       (map #(merge {:description "" :backend (:backend b) :list-id ""} %))
       (map #(select-keys % [:name :address :description :backend :list-id]))))

(defn get-lists-from-server
  "Get information for lists from the server."
  []
  (let [lists (atom {})]
    (doseq [b config/backends-expanded]
      (let [api-url        (:api-url b)
            lists-endpoint (:lists-endpoint b)
            basic-auth     (:basic-auth b)
            result         (json/parse-string
                            (:body
                             (try (http/get
                                   (str api-url lists-endpoint)
                                   {:basic-auth basic-auth})
                                  (catch Exception e
                                    {:message (:message (json/parse-string (:body (ex-data e)) true))
                                     :result  "ERROR"})))
                            true)]
        (swap! lists concat (cleanup-list-data ((:data-keyword b) result) b))))
    @lists))

(defn initialize-lists-information
  "Store lists information in the db."
  []
  (doseq [l (get-lists-from-server)]
    @(d/transact! db-conn [(merge {:db/id (d/tempid -1) :members_new 0} l)])))

(defn get-lists-from-db
  "Get the list of mailing lists stored in the database."
  []
  (let [lists (d/q '[:find ?l :where [?l :address ]] @db-conn)]
    (map #(d/pull @db-conn '[:address :description
                             :name :members_count
                             :members_new :backend :list-id]
                  (first %))
         lists)))

;; (get-lists-filtered (get-lists-from-db))

;; ({:address "test-antoine-augusti@mail.etalab.studio", :description "", :name "Test Liste Antoine Augusti", :members_new 0, :backend "mailgun", :list-id ""} {:address "tdg9i71hw", :description "", :name "MonPremierTest", :members_new 0, :backend "mailjet", :list-id 5904} {:address "forum-etalab@mail.etalab.studio", :description "Utilisateurs du forum d'Etalab", :name "Utilisateurs du forum d'Etalab", :members_new 0, :backend "mailgun", :list-id ""} {:address "test@mail.etalab.studio", :description "Liste test", :name "Liste test", :members_new 0, :backend "mailgun", :list-id ""} {:address "entrepreneur-interet-general@mail.etalab.studio", :description "Liste d'information sur le programme Entrepreneurs d'intérêt général", :name "Entrepreneurs d'intérêt général", :members_new 0, :backend "mailgun", :list-id ""} {:address "bluehats@mail.etalab.studio", :description "La gazette du logiciel libre dans/pour l'administration", :name "Gazette #bluehats", :members_new 0, :backend "mailgun", :list-id ""})

(defn get-lists-filtered
  "Get the list of mailing list while including and excluding lists
  depending on `config/lists-include/exclude-regexp`."
  [lists]
  (->> lists
       (filter #(not (re-matches config/lists-exclude-regexp (:address %))))
       (filter #(re-matches config/lists-include-regexp (:address %)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handle email sending

(defn build-email-body [{:keys [ml name title html-body plain-body]}]
  [:alternative
   {:type    "text/plain"
    :content (str (if name (format (i18n [:opening-name]) name)
                      (i18n [:opening-no-name]))
                  "\n\n" plain-body "\n\n"
                  (i18n [:closing]) "\n\n-- \n"
                  (or (config/team ml)
                      (config/return-url ml)))}
   {:type    "text/html"
    :content (views/default
              title ml
              [:div
               [:p (if name (format (i18n [:opening-name]) name)
                       (i18n [:opening-no-name]))]
               [:p (or html-body plain-body)]
               [:p (i18n [:closing])]
               [:p [:a {:href (config/return-url ml)}
                    (or (config/team ml) (config/return-url ml))]]])}])

(defn send-email
  "Send a templated email."
  [{:keys [email name subject plain-body html-body log mailing-list]}]
  (try
    (postal/send-message
     {:host (config/smtp-host mailing-list)
      :port 587
      :user (config/smtp-login mailing-list)
      :pass (config/smtp-password mailing-list)}
     {:from             (config/from mailing-list)
      :message-id       #(postal.support/message-id
                          (config/msg-id mailing-list))
      :to               email
      :subject          subject
      :body             (build-email-body {:ml         mailing-list
                                           :name       name
                                           :title      mailing-list
                                           :plain-body plain-body
                                           :html-body  html-body})
      :List-Unsubscribe (str "<" config/base-url "/unsubscribe/" mailing-list ">")})
    (timbre/info log)
    (catch Exception e
      (timbre/error (str "Can't send email: " (:cause (Throwable->map e)))))))

(defn send-validation-link
  "Create a validation link and send it by email."
  [email-and-list & unsubscribe?]
  (let [subscriber   (get email-and-list "subscriber")
        name         (or (get email-and-list "name") "")
        mailing-list (get email-and-list "mailing-list")
        token        (str (java.util.UUID/randomUUID))]
    ;; FIXME: check email address format before sending?
    (create-action-token token subscriber name mailing-list)
    (send-email
     {:email        subscriber
      :name         name
      :mailing-list mailing-list
      :subject      (format (i18n (if unsubscribe?
                                    [:confirm-unsubscription]
                                    [:confirm-subscription]))
                            mailing-list)
      :plain-body   (str
                     (format (i18n (if unsubscribe?
                                     [:confirm-unsubscription]
                                     [:confirm-subscription]))
                             mailing-list)
                     ":\n"
                     (format (str "%s/confirm-"
                                  (if unsubscribe? "un")
                                  "subscription/%s")
                             config/base-url token))
      :html-body    (str
                     (format (i18n (if unsubscribe?
                                     [:confirm-unsubscription]
                                     [:confirm-subscription]))
                             mailing-list)
                     ":\n"
                     (str "<a href=\""
                          (format (str "%s/confirm-"
                                       (if unsubscribe? "un")
                                       "subscription/%s")
                                  config/base-url token)
                          "\">" (i18n [:click-here]) "</a>"))
      :log          (format (i18n [:validation-sent-to])
                            mailing-list subscriber)})))

(defn subscribe-or-unsubscribe-address
  "Perform the actual email subscription to the mailing list."
  [{:keys [subscriber name mailing-list backend action]}]
  (let [b              (first (filter #(= (:backend %) backend) config/backends-expanded))
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
      (let [req (apply
                 (if (= http-verb "DELETE") http/delete http/post)
                 [(str (:api-url b) (endpoint-fn mailing-list subscriber))
                  {:basic-auth  (:basic-auth b)
                   :form-params (form-params-fn subscriber name)}])]
        {:message (str subscriber (:message result-msg) mailing-list " on " backend)
         :result  (:output result-msg)})
      (catch Exception e
        (let [message (:message (json/parse-string (:body (ex-data e)) true))]
          {:message message
           :result  "ERROR"})))))

(defn get-list-from-db
  "Get a map of information for mailing list ML."
  [ml]
  (first (filter #(= (:address %) ml) (get-lists-from-db))))

(defn check-already-subscribed
  "Check if an email is already subscribed to the mailing list."
  [email-and-list]
  (let [subscriber      (get email-and-list "subscriber")
        mailing-list    (get email-and-list "mailing-list")
        backend-conf    (get-ml-backend-config mailing-list)
        mailing-list-id (:list-id (get-list-from-db mailing-list))]
    (try
      (let [req  (http/get
                  (str (:api-url backend-conf)
                       ((:check-subscription-endpoint-fn backend-conf)
                        subscriber mailing-list))
                  {:basic-auth (:basic-auth backend-conf)})
            body (json/parse-string (:body req) true)]
        ((:check-subscription-validate-fn backend-conf) body mailing-list-id))
      (catch Exception e false))))

(defn subscribe-and-send-confirmation
  "Subscribe an email address to a mailing list.
  Send a confirmation email."
  [token unsubscribe]
  (if-let [infos (validate-token token)]
    (let [action             (if unsubscribe "unsubscribe" "subscribe")
          inc-or-dec         (if unsubscribe decrement-subscribers increment-subscribers)
          result             (subscribe-or-unsubscribe-address (merge infos {:action action}))
          subscribed-to      (if unsubscribe (i18n [:unsubscribed-to])
                                 (i18n [:subscribed-to]))
          subscribed-message (if unsubscribe (i18n [:unsubscribed-message])
                                 (i18n [:subscribed-message]))
          backend            (:backend infos)
          name               (:name infos)
          subscriber         (:subscriber infos)
          mailing-list       (:mailing-list infos)]
      (if-not (= (:result result) action)
        (timbre/info (:message result))
        (do (inc-or-dec mailing-list)
            (send-email
             {:email      subscriber
              :name       name
              :subject    (format subscribed-to mailing-list)
              :plain-body (format subscribed-message mailing-list)
              :log        (format (i18n [:confirmation-sent-to])
                                  mailing-list subscriber)}))))))

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
  (GET "/" [] (views/mailing-lists (get-lists-filtered (get-lists-from-db))))
  (GET "/already-subscribed/:ml" [ml]
       (views/feedback (i18n [:error]) ml (i18n [:already-subscribed])))
  (GET "/not-subscribed/:ml" [ml]
       (views/feedback (i18n [:error]) ml (i18n [:not-subscribed])))
  (GET "/email-sent/:ml" [ml]
       (views/feedback (i18n [:thanks]) ml (i18n [:validation-sent])))
  ;; FIXME: customize per mailing list?
  (GET "/thanks" []
       (views/feedback (i18n [:done]) nil (i18n [:successful-subscription])))
  (GET "/bye" []
       (views/feedback (i18n [:done]) nil (i18n [:successful-unsubscription])))
  (GET "/subscribe/:ml" [ml] (views/subscribe-to-mailing-list ml))
  (GET "/unsubscribe/:ml" [ml] (views/unsubscribe-from-mailing-list ml))
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
  [& args]
  (initialize-lists-information)
  (start-subscription-loop)
  (start-unsubscription-loop)
  (start-subscribe-confirmation-loop)
  (start-unsubscribe-confirmation-loop)
  (jetty/run-jetty #'app {:port config/port})
  (println (str "Subscribe application started on localhost:" config/port)))

;; (-main)


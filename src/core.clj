;; Copyright (c) 2019-2024 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns core
  "Subscribe core functions."
  (:require
   [reitit.ring :as ring]
   [ring.middleware.params :as params]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.middleware.reload :as reload]
   [ring.middleware.cors :refer [wrap-cors]]
   ;; [integrant.core :as ig]
   [selmer.parser :as html]
   [selmer.filters :as filters]
   [org.httpkit.server :as server]
   [clj-http.lite.client :as http]
   [clojure.set :as set]
   [clojure.java.io :as io]
   [i18n :refer [i]]
   [config :as config]
   [postal.core :as postal]
   [postal.support]
   [clojure.core.async :as async]
   [clojure.walk :as walk]
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

(defn cleanup-list-data [backend]
  (comp
   (map #(set/rename-keys % (:replacements backend)))
   (map #(merge % {:backend     (:backend backend)
                   :members_new 0
                   :address     (or (:address %) (str (:list-id %)))
                   :name        (or (:name (get (:lists config/config) (:address %)))
                                    (:name %))
                   :description (or (:description (get (:lists config/config) (:address %)))
                                    (:description %))}))
   (map #(select-keys % [:name :address :description
                         :backend :list-id :members_new]))))

(defn get-lists-filtered [lists]
  (let [ex-re config/lists-exclude-regexp
        in-re config/lists-include-regexp]
    (filter #(and (or (nil? ex-re) (not (re-find ex-re (:address %))))
                  (or (nil? in-re) (re-find in-re (:address %))))
            lists)))

(defn get-lists-from-server!
  "Set the lists atom with lists information from the backends."
  []
  (let [l (atom nil)]
    (doseq [{:keys [api-url lists-endpoint auth lists-query-params data-keyword] :as b}
            config/backends-expanded]
      (when-let [result
                 (json/parse-string
                  (:body
                   (try (http/get (str api-url lists-endpoint)
                                  (merge auth lists-query-params))
                        (catch Exception e
                          {:message (:message (json/parse-string
                                               (:body (ex-data e)) true))
                           :result  "ERROR"})))
                  true)]
        (swap! l concat
               (sequence (cleanup-list-data b) (data-keyword result)))))
    ;; Sets the lists atom and return an info when done.
    (when (reset! lists
                  (into {} (map (fn [m] {(str (:address m)) m})
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
  (let [ml         (get @lists mailing-list)
        lang       (config/locale (:address ml))
        ml-desc    (or (:description ml) (config/description ml))
        short-name (:name ml)]
    [:alternative
     {:type    "text/plain; charset=utf-8"
      :content (str (if username (format (i lang [:opening-name]) username)
                        (i lang [:opening-no-name]))
                    "\n\n" plain-body "\n\n"
                    (i lang [:closing]) "\n\n-- \n"
                    (or (config/team mailing-list)
                        (config/return-url mailing-list)))}
     {:type "text/html; charset=utf-8"
      :content ;; (views/default
      ;;  short-name ml-desc mailing-list lang
      ;;  [:div
      ;;   [:p (if username (format (i lang [:opening-name]) username)
      ;;           (i lang [:opening-no-name]))]
      ;;   [:p (or html-body plain-body)]
      ;;   [:p (i lang [:closing])]
      ;;   [:p [:a {:href (config/return-url mailing-list)}
      ;;        (or (config/team ml)
      ;;            (config/return-url mailing-list))]]]
      ;;  true)
      ""
      }]))

(defn send-email
  "Send a templated email."
  [{:keys [email username subject plain-body html-body log mailing-list]}]
  (try
    (postal/send-message
     {:host (config/smtp-host mailing-list)
      :port (config/smtp-port mailing-list)
      :user (config/smtp-login mailing-list)
      :pass (config/smtp-password mailing-list)
      :tls  true}
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
  [{:keys [subscriber username mailing-list unsubscribe?]}]
  (let [;; username     (or username "")
        ml    (get @lists mailing-list)
        lang  (config/locale ml)
        token (str (java.util.UUID/randomUUID))]
    ;; FIXME: check email address format before sending?
    (create-action-token token subscriber username mailing-list)
    (send-email
     {:email        subscriber
      :username     username
      :mailing-list mailing-list
      :subject      (format (i lang (if unsubscribe?
                                      [:confirm-unsubscription]
                                      [:confirm-subscription]))
                            (:name ml))
      :plain-body   (str
                     (format (i lang (if unsubscribe?
                                       [:confirm-unsubscription]
                                       [:confirm-subscription]))
                             (:name ml))
                     ":\n"
                     (format (str "%s/confirm-"
                                  (when unsubscribe? "un")
                                  "subscription/%s")
                             config/base-url token))
      :html-body    (str
                     (format (i lang (if unsubscribe?
                                       [:confirm-unsubscription]
                                       [:confirm-subscription]))
                             (:name ml))
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
  (let [b           (some  #(when (= (:backend %) backend) %)
                           config/backends-expanded)
        http-verb   (if (= action "subscribe")
                      (or (:subscribe-http-verb b) "POST")
                      (or (:unsubscribe-http-verb b)
                          (:subscribe-http-verb b)
                          "POST"))
        endpoint-fn (if (= action "subscribe")
                      (:subscribe-endpoint-fn b)
                      (or (:unsubscribe-endpoint-fn b)
                          (:subscribe-endpoint-fn b)))
        params-fn   (if (= action "subscribe")
                      (:subscribe-params-fn b)
                      (or (:unsubscribe-params-fn b)
                          (:subscribe-params-fn b)))
        params      (params-fn {:mailing-list mailing-list :subscriber subscriber :username username})
        result-msg  (if (= action "subscribe")
                      {:message " subscribed to " :output "subscribe"}
                      {:message " unsubscribed to " :output "unsubscribe"})]
    (try
      (apply (if (= http-verb "DELETE") http/delete http/post)
             [(str (:api-url b) (endpoint-fn {:mailing-list mailing-list :subscriber subscriber}))
              (merge (:auth b)
                     (if (= backend "sendinblue") ;; FIXME: why being specific here?
                       {:body         (json/generate-string params)
                        :content-type :json}
                       {:form-params params}))])
      {:message (str subscriber (:message result-msg) mailing-list " on " backend)
       :result  (:output result-msg)}
      (catch Exception e
        (let [message (:message (json/parse-string (:body (ex-data e)) true))]
          {:message message
           :result  "ERROR"})))))

(defn subscribe-and-send-confirmation
  "Subscribe an email address to a mailing list.
  Send a confirmation email."
  [token unsubscribe?]
  (when-let [{:keys [subscriber username mailing-list] :as infos}
             (validate-token token)]
    (let [action     (if unsubscribe? "unsubscribe" "subscribe")
          inc-or-dec (if unsubscribe? decrement-subscribers increment-subscribers)
          result     (subscribe-or-unsubscribe-address (merge infos {:action action}))]
      (if-not (= (:result result) action)
        (timbre/info (:message result))
        (do (inc-or-dec mailing-list)
            (let [{:keys [address backend name]} (get @lists mailing-list)
                  lang                           (config/locale address)
                  subscribed-to
                  (if unsubscribe?
                    (i lang [:unsubscribed-from])
                    (i lang [:subscribed-to]))
                  subscribed-message
                  (if unsubscribe?
                    (i lang [:unsubscribed-message])
                    (i lang [:subscribed-message]))]
              (send-email
               {:email        subscriber
                :username     username
                :mailing-list mailing-list
                :subject      (format subscribed-to name)
                :plain-body   (format subscribed-message name)
                :log          (format (if unsubscribe?
                                        (i lang [:unsubscribe-confirmation-sent-to])
                                        (i lang [:subscribe-confirmation-sent-to]))
                                      (str mailing-list " (" backend ")")
                                      subscriber)})))))))

(defn check-already-subscribed
  "Check if an email is already subscribed to the mailing list."
  [{:keys [subscriber mailing-list]}]
  (let [backend-conf    (get-list-backend-config mailing-list)
        mailing-list-id (:list-id (get @lists mailing-list))]
    (try
      (let [req  (http/get
                  (str (:api-url backend-conf)
                       ((:check-subscription-endpoint-fn backend-conf)
                        {:subscriber subscriber :mailing-list mailing-list}))
                  (:auth backend-conf))
            body (json/parse-string (:body req) true)]
        ((:check-subscription-validate-fn backend-conf)
         {:body body :id mailing-list-id}))
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
      (send-validation-link (merge req {:unsubscribe? true}))
      (recur (async/<! unsubscribe-channel)))))

(defn start-subscribe-confirmation-loop
  "Intercept confirmations and send corresponding emails."
  []
  (async/go
    (loop [token (async/<! subscribe-confirmation-channel)]
      (subscribe-and-send-confirmation token false)
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

(defn- page-index [page]
  {:page  "index"
   :howto (slurp (io/resource (str "themes/" (:theme config/config) "/index.html")))})

(defn- page-404 [_]
  :page   "404")

(defn- get-page [page {:keys [query-params path-params uri headers]}]
  (let [ ;; format-params   {}
        ;; lang            (if-let [lang (get headers "accept-language")]
        ;;                   (subs lang 0 2) "en")
        ;; config-defaults nil
        html-page (condp = page
                    :404 {:html "/404.html" :fn page-404}
                    {:html "/index.html" :fn page-index})
        theme     (:theme config/config)]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html/render-file
               (io/resource (str "themes/" theme (:html html-page)))
               ((:fn html-page) page))}))

(defn- post-page [page])

(def handler
  (ring/ring-handler
   (ring/router
    ;; View mailing lists
    [["/"
      ;; (views/mailing-lists @lists))
      ["" {:get #(get-page :index %)}]]
     ["/already-subscribed/:ml"
      ["" {:get #(get-page :already-subscribed %)}]]
     ;; (let [ml-opts (get @lists ml)
     ;;       lang    (config/locale (:address ml-opts))]
     ;;   (views/feedback
     ;;    (i lang [:error])
     ;;    ml-opts
     ;;    (i lang [:already-subscribed])))
     ["/not-subscribed/:ml"
      ["" {:get #(get-page :not-subscribed %)}]]
     ;; (let [ml-opts (get @lists ml)
     ;;       lang    (config/locale (:address ml-opts))]
     ;;   (views/feedback
     ;;    (i lang [:error])
     ;;    ml-opts
     ;;    (i lang [:not-subscribed])))
     ["/email-sent/:ml"
      ["" {:get #(get-page :not-subscribed %)}]]
     ;; (let [ml-opts (get @lists ml)
     ;;       lang    (config/locale (:address ml-opts))]
     ;;   (views/feedback
     ;;    (i lang [:thanks])
     ;;    ml-opts
     ;;    (i lang [:validation-sent])))
     ["/thanks"
      ["" {:get #(get-page :thanks %)}]]
     ;; (views/feedback (i (config/locale nil) [:done])
     ;;                 nil (i (config/locale nil)
     ;;                        [:successful-subscription]))
     ["/bye"
      ["" {:get #(get-page :bye %)}]]
     ;; (views/feedback (i (config/locale nil) [:done])
     ;;                 nil (i (config/locale nil)
     ;;                        [:successful-unsubscription]))
     ["/subscribe/:ml"
      ["" {:get  #(get-page :subscribe %)
           :post #(post-page :confirm-subscription %)}]]
     ;; (views/subscribe-to-mailing-list
     ;;  (get @lists ml))
     ["/unsubscribe/:ml"
      ["" {:get  #(get-page :unsubscribe %)
           :post #(post-page :confirm-unsubscription %)}]]     
     ;; (views/unsubscribe-from-mailing-list
     ;;  (get @lists ml))
     ;; ["/subscribe/:ml"
     ;;  ["" {}]]
     ;; (let [{:keys [mailing-list] :as params}
     ;;       (walk/keywordize-keys (:form-params req))]
     ;;   (if (check-already-subscribed params)
     ;;     (response/redirect (str "/already-subscribed/" mailing-list))
     ;;     (do (async/go (async/>! subscribe-channel params))
     ;;         (response/redirect (str "/email-sent/" mailing-list)))))
     ["/confirm-subscription/:token"
      ["" {:get #(get-page :confirm-subscription %)}]]
     ;; (do (async/go (async/>! subscribe-confirmation-channel token))
     ;;     (response/redirect "/thanks"))
     ;; ["/unsubscribe/:ml"
     ;;  ["" {}]]
     ;; (let [{:keys [mailing-list] :as params}
     ;;       (walk/keywordize-keys (:form-params req))]
     ;;   (if-not (check-already-subscribed params)
     ;;     (response/redirect (str "/not-subscribed/" mailing-list))
     ;;     (do (async/go (async/>! unsubscribe-channel params))
     ;;         (response/redirect (str "/email-sent/" mailing-list)))))
     ["/confirm-unsubscription/:token"
      ["" {:get #(get-page :confirm-unsubscription %)}]]
     ;; (do (async/go (async/>! unsubscribe-confirmation-channel token))
     ;;     (response/redirect "/bye"))
     ]
    {:data {:middleware [params/wrap-params]}})
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler
     {:not-found (fn [_] (get-page :404 nil))})
    {:middleware
     [parameters/parameters-middleware
      #(wrap-cors
        %
        :access-control-allow-origin [#"^*$"]
        :access-control-allow-methods [:get])]})))

(defn -main
  "Initialize the db, the loops and the web serveur."
  []
  (get-lists-from-server!)
  (start-subscription-loop)
  (start-unsubscription-loop)
  (start-subscribe-confirmation-loop)
  (start-unsubscribe-confirmation-loop)
  (let [port config/port server "localhost"]
    (server/run-server
     (reload/wrap-reload handler {:dirs ["src" "resources"]})
     {:port port :server server})
    (timbre/info
     (format "Web server started on %s (port %s)" server port)))
  (println (str "Subscribe application started on localhost:" config/port)))

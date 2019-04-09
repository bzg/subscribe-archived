(ns subscribe.handler
  (:gen-class)
  (:require [org.httpkit.server :as http-kit]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [compojure.core :as compojure :refer (GET POST defroutes)]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [hiccup.page :as h]
            [hiccup.element :as he]
            [clj-http.client :as http]
            [subscribe.i18n :refer [i18n]]))

(def mailgun-api-url "https://api.mailgun.net/v3")
(def mailgun-api-key (or (System/getenv "MAILGUN_API_KEY") ""))
(def mailgun-mailing-list (or (System/getenv "MAILGUN_MAILING_LIST") ""))
(def mailgun-subscribe-endpoint (str "/lists/" mailgun-mailing-list "/members"))
(def return-url (or (System/getenv "MAILGUN_RETURN_URL") ""))
(def token (.toString (java.util.UUID/randomUUID)))

(def dummy-captcha
  ["35 + 7" "32 + 10" "84 / 2" "21 * 2" "44 - 2"
   "36 + 6" "31 + 11" "126 / 3" "40 + 2" "46 - 4"
   "29 + 12" "12 + 30" "9 + 33" "14 * 3" "17 + 25"
   "126 / 3" "168 / 4" "41 + 1"])

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
      [:input {:name "hidden-field" :type "hidden" :value ~token}]
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

(defn- subscribe-address [email]
  (http/post (str mailgun-api-url mailgun-subscribe-endpoint)
             {:basic-auth  ["api" mailgun-api-key]
              :form-params {:address email}}))

(defroutes app-routes
  (GET "/" [] (home-page))
  (GET "/thanks" [] (thanks-page))
  (POST "/subscribe" [email hidden-field]
        (if (not (= hidden-field token))
          (response/redirect "/")
          (do (subscribe-address email)
              (println
               (str email " subscribed to " mailgun-mailing-list))
              (response/redirect "/thanks"))))
  (route/resources "/")
  (route/not-found "404 error"))

(def app (-> app-routes
             wrap-reload
             params/wrap-params))

(defn -main [& args]
  (http-kit/run-server
   #'app {:port (Integer/parseInt (or (System/getenv "SUBSCRIBE_PORT") "3000"))}))


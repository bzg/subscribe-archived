;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.pages
  (:require [hiccup.page :as h]
            [hiccup.element :as he]
            [subscribe.i18n :refer [i18n]]
            [subscribe.config :as config]))

(defmacro default [content]
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

(defn home []
  (default
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

(defn feedback [message]
  (default
   `([:h1 ~(config/mailgun-mailing-list)]
     [:br]
     [:h2 ~message]
     [:br]
     [:a {:href ~(config/return-url)} ~(i18n [:return-to-site])])))



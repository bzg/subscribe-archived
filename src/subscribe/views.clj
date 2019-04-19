;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.views
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
     (h/include-css "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.7.4/css/bulma.min.css")]
    [:body
     [:section {:class "section"}
      [:div {:class "container"}
       [:h1 {:class "title"} ~(config/mailgun-mailing-list)]]]
     [:section {:class "section"} ~content]]
    [:footer {:class "footer"}
     [:div {:class "content has-text-centered"}
      [:p "Made with Subscribe"]]]))

(defn home []
  (default
   `([:div {:class "container"}
      [:form
       {:action "/subscribe" :method "post"}
       [:label {:class "label"} ~(i18n [:email-address])]
       [:input {:name        "email" :type  "text"
                :size        "30"    :class "input"
                :placeholder ~(i18n [:email-address])
                :required    true}]
       [:br]
       [:br]
       [:input {:type  "submit"
                :value ~(i18n [:subscribe])
                :class "button is-primary"}]]])))

(defn feedback [message]
  (default
   `([:div {:class "container"}
      [:p {:class "subtitle"} ~message]
      [:p [:a {:href ~(config/return-url)} ~(i18n [:return-to-site])]]])))



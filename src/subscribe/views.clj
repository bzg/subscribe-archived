;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.views
  (:require [hiccup.page :as h]
            [hiccup.element :as he]
            [ring.util.anti-forgery :as afu]
            [subscribe.i18n :refer [i18n]]
            [subscribe.config :as config]))

(defn default [title content]
  (h/html5
   {:lang config/locale}
   [:head
    [:title (i18n [:title])]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, shrink-to-fit=yes"}]
    (h/include-css "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.7.5/css/bulma.min.css")]
   [:body
    [:section {:class "hero is-primary"}
     [:div {:class "hero-body"}
      [:div {:class "container"}
       [:h1 {:class "title has-text-centered"} title]]]]
    [:section {:class "section"}
     [:div {:class "column is-8 is-offset-2"}
      content]]]
   [:footer {:class "footer"}
    [:div {:class "content has-text-centered"}
     [:p "Made with " [:a {:href   "https://github.com/bzg/subscribe"
                           :target "new"} "Subscribe"]]]]))

(defn error []
  (default
   (i18n [:error])
   [:div {:class "container"}
    [:p [:a {:href config/return-url} (i18n [:return-to-site])]]]))

(defn mailing-lists [lists]
  (default
   (i18n [:mailing-lists])
   [:div {:class "container"}
    (for [l lists]
      [:div {:style "margin: 1.4em;"}
       [:p {:title (:address l)
            :class "title"} (:name l)]
       [:p {:class "subtitle"} (:description l)]
       [:div {:class "level-left"}
        [:a {:class "level-item button is-link"
             :href  (str "/subscribe/" (:address l))}
         (i18n [:go-subscribe-page])]
        [:a {:class "level-item button is-danger"
             :href  (str "/unsubscribe/" (:address l))}
         (i18n [:go-unsubscribe-page])]]])]))

(defn subscribe-to-mailing-list [address]
  (let [email-ui (i18n [:email-address])
        name-ui  (i18n [:name])]
    (default
     address
     [:div {:class "container"}
      [:form
       {:action "/subscribe" :method "post"}
       (afu/anti-forgery-field)
       [:input {:name  "mailing-list" :type "hidden"
                :value address}]
       [:label {:class "label"} name-ui]
       [:input {:name        "name" :type  "text"
                :size        "30"   :class "input"
                :placeholder name-ui}]
       [:br]
       [:br]
       [:label {:class "label"} email-ui]
       [:input {:name        "subscriber" :type     "email"
                :size        "30"         :class    "input"
                :placeholder email-ui     :required true}]
       [:br]
       [:br]
       [:input {:type  "submit"
                :value (i18n [:subscribe])
                :class "button is-link"}]]])))

(defn unsubscribe-to-mailing-list [address]
  (let [email-ui (i18n [:email-address])]
    (default
     address
     [:div {:class "container"}
      [:form
       {:action "/unsubscribe" :method "post"}
       [:input {:name  "mailing-list" :type "hidden"
                :value address}]
       [:label {:class "label"} email-ui]
       [:input {:name        "subscriber" :type     "email"
                :size        "30"         :class    "input"
                :placeholder email-ui     :required true}]
       [:br]
       [:br]
       [:input {:type  "submit"
                :value (i18n [:unsubscribe])
                :class "button is-danger"}]]])))

(defn feedback [title message]
  (default
   title
   [:div {:class "container"}
    [:p {:class "subtitle"} message]
    [:p [:a {:href config/return-url} (i18n [:return-to-site])]]]))

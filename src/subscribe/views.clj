;; Copyright (c) 2019-2020 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.views
  "Subscribe application views."
  (:require [hiccup.page :as h]
            [ring.util.anti-forgery :as afu]
            [subscribe.i18n :refer [i]]
            [subscribe.config :as config]))

(defn default [title subtitle ml-address lang content]
  (h/html5
   {:lang lang}
   [:head
    [:title (i lang [:title])]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, shrink-to-fit=yes"}]
    (h/include-css config/css)
    config/before-head-closing-html]
   [:body
    config/after-body-beginning-html
    [:section.hero.is-primary
     [:div.hero-body
      [:div.container
       [:h1.title.has-text-centered title]
       [:h2.subtitle.has-text-centered subtitle]]]]
    [:section.section [:div.is-8.is-offset-2 content]]
    (or config/footer-html
        [:footer.footer
         [:div.content.has-text-centered
          (if-let [tos (config/tos-url ml-address)]
            [:p [:a {:href tos :target "new"} (i lang [:tos])]])
          [:p (i lang [:made-with]) " "
           [:a {:href   "https://github.com/bzg/subscribe"
                :target "new"} "Subscribe"]]]])]))

(defn error []
  (let [lang (config/locale nil)]
    (default
     (i lang [:error])
     nil
     nil
     lang
     [:div.container
      [:p [:a {:href (config/return-url nil)}
           (i lang [:return-to-site])]]])))

(defn mailing-lists [lists]
  (let [lang (config/locale nil)]
    (default
     (or (config/team nil) (i lang [:mailing-lists]))
     (when (config/team nil) (i lang [:mailing-lists]))
     nil
     lang
     [:div.container
      (for [l lists]
        [:div.columns
         [:div.column.is-8
          [:p.title (:name l)]
          [:p.subtitle (or (not-empty (:description l))
                           (config/description (:address l))
                           (:address l))]]
         [:div.column
          [:div.level-left
           [:div.level-item
            [:a.button.is-info
             {:href (str "/subscribe/" (:address l))}
             (i lang [:subscribe-button])]]
           [:div.level-item
            [:a.button.is-danger
             {:href (str "/unsubscribe/" (:address l))}
             (i lang [:unsubscribe-button])]]]]])])))

(defn subscribe-to-mailing-list [ml]
  (let [ml-address (:address ml)
        lang       (config/locale ml-address)
        email-ui   (i lang [:email-address])
        name-ui    (i lang [:name])
        ml-name    (:name ml)
        ml-desc    (or (:description ml) (config/description ml) ml-address)]
    (default
     ml-name
     ml-desc
     ml-address
     lang
     [:div.container
      [:form
       {:action "/subscribe" :method "post"}
       (afu/anti-forgery-field)
       [:input {:name "mailing-list" :type "hidden" :value ml-address}]
       [:div.field
        [:label.label name-ui]
        [:div.control
         [:input.input
          {:name "name" :type "text" :size "30" :placeholder name-ui}]]]
       [:div.field
        [:label.label email-ui]
        [:div.control
         [:input.input
          {:name        "subscriber" :type     "email" :size "30"
           :placeholder email-ui     :required true}]]]
       [:div.field
        [:div.control
         [:input.button.is-info
          {:type  "submit"
           :value (i lang [:subscribe])}]]]]])))

(defn unsubscribe-from-mailing-list [ml]
  (let [ml-address (:address ml)
        lang       (config/locale ml-address)
        email-ui   (i lang [:email-address])
        ml-name    (:name ml)
        ml-desc    (or (:description ml) (config/description ml) ml-address)]
    (default
     ml-name
     ml-desc
     ml-address
     lang
     [:div.container
      [:form
       {:action "/unsubscribe" :method "post"}
       (afu/anti-forgery-field)
       [:input {:name  "mailing-list" :type "hidden"
                :value ml-address}]
       [:label.label email-ui]
       [:input.input
        {:name     "subscriber" :type        "email"
         :size     "30"         :placeholder email-ui
         :required true}]
       [:br]
       [:br]
       [:input.button.is-danger
        {:type  "submit"
         :value (i lang [:unsubscribe])}]]])))

(defn feedback [title ml message]
  (let [address (:address ml)
        lang    (config/locale address)]
    (default
     title
     (:name ml)
     address
     lang
     [:div.container
      [:p.subtitle message]
      [:p [:a {:href (config/return-url address)}
           (i lang [:return-to-site])]]])))

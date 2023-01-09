;; Copyright (c) 2019-2023 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns views
  "Subscribe application views."
  (:require [hiccup.page :as h]
            [ring.util.anti-forgery :as afu]
            [i18n :refer [i]]
            [config :as config]))

(defn default [title subtitle ml-address lang content & [email?]]
  (h/html5
   {:lang lang}
   [:head
    [:title title]
    [:meta {:charset "utf-8"}]
    [:meta {:name "description" :content (str title " " subtitle)}]
    [:link {:rel "canonical" :href (config/return-url ml-address)}]
    [:meta {:property "og:locale", :content "fr_FR"}]
    [:meta {:property "og:type", :content "website"}]
    [:meta {:property "og:title", :content title}]
    [:meta {:property "og:url", :content (config/return-url ml-address)}]
    [:meta {:property "og:site_name", :content (str title " - " subtitle)}]
    ;; [:meta {:property "og:image", :content ""}]
    [:meta {:name "twitter:card", :content (str title " " subtitle)}]
    [:meta {:name "twitter:title", :content title}]
    [:meta {:name "twitter:site", :content (config/return-url ml-address)}]
    ;; [:meta {:name "twitter:creator", :content title}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, shrink-to-fit=yes"}]
    (when-not email? (h/include-css config/css))
    (when-not email? config/before-head-closing-html)]
   [:body
    (when-not email? config/after-body-beginning-html)
    [:section.hero.is-primary
     [:div.hero-body
      [:div.container
       [:h1.title.has-text-centered title]
       [:h2.subtitle.has-text-centered subtitle]]]]
    [:section.section [:div.is-8.is-offset-2 content]]
    (when-not email?
      (or config/footer-html
          [:footer.footer
           [:div.content.has-text-centered
            (when-let [tos (config/tos-url ml-address)]
              [:p [:a {:href tos :target "new"} (i lang [:tos])]])
            [:p (i lang [:made-with]) " "
             [:a {:href   "https://github.com/bzg/subscribe"
                  :target "new"} "Subscribe"]]]]))]))

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
      (for [[l val] lists]
        [:div.columns
         [:div.column.is-8
          [:p.is-size-3 (:name val)]
          (when-let [d (:description val)] [:p.is-size-5 d])]
         [:div.column
          [:div.level-left
           [:div.level-item
            [:a.button.is-info
             {:href (str "/subscribe/" l)}
             (i lang [:subscribe-button])]]
           [:div.level-item
            [:a.button.is-danger
             {:href (str "/unsubscribe/" l)}
             (i lang [:unsubscribe-button])]]]]])])))

(defn subscribe-to-mailing-list [{:keys [address name description]}]
  (let [lang     (config/locale address)
        email-ui (i lang [:email-address])
        name-ui  (i lang [:name])]
    (default
     (str (i lang [:subscribing]) " - " name)
     description
     address
     lang
     [:div.container
      [:form
       {:action "/subscribe" :method "post"}
       (afu/anti-forgery-field)
       [:input {:name "mailing-list" :type "hidden" :value address}]
       [:div.field
        [:label.label name-ui]
        [:div.control
         [:input.input
          {:name "username" :type "text" :size "30" :placeholder name-ui}]]]
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

(defn unsubscribe-from-mailing-list [{:keys [address name description]}]
  (let [lang     (config/locale address)
        email-ui (i lang [:email-address])]
    (default
     (str (i lang [:unsubscribing]) " - " name)
     description
     address
     lang
     [:div.container
      [:form
       {:action "/unsubscribe" :method "post"}
       (afu/anti-forgery-field)
       [:input {:name  "mailing-list" :type "hidden"
                :value address}]
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

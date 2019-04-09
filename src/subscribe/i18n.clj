(ns subscribe.i18n
  (:require [taoensso.tempura :refer [tr]]))

(def localization
  {:en-GB
   {:missing                 "Missing translation"
    :title                   "Newsletter subscription"
    :email-address           "Email address"
    :subscribe               "Subscribe"
    :successful-subscription "Subscription successful."
    :return-to-site          "Return to website"}
   :fr-FR
   {:missing                 "Traduction manquante"
    :title                   "Inscription à une liste de discussion"
    :email-address           "Adresse de courriel"
    :subscribe               "S'inscrire"
    :successful-subscription "Inscription réussie !"
    :return-to-site          "Cliquez pour revenir au site"}})

(def lang (keyword (or (System/getenv "SUBSCRIBE_LOCALE") "en-GB")))

(def opts {:dict localization})

(def i18n (partial tr opts [lang]))


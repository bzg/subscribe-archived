;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.i18n
  (:require [taoensso.tempura :refer [tr]]))

(def localization
  {:en-GB
   {:already-subscribed      "Your email address is already subscribed to this list."
    :confirm-subscription    "Please confirm your subscription to the list %s"
    :confirmation-sent-to    "Confirmation link sent to %s."
    :email-address           "Email address"
    :missing                 "Missing translation"
    :regenerate-token        "Email token for %s regenerated."
    :return-to-site          "Return to website"
    :subscribe               "Subscribe"
    :subscribed-message      "Your subscription to the list %s is now effective, thanks!"
    :successful-subscription "Subscription successful."
    :thanks                  "Thanks!"
    :title                   "Newsletter subscription"
    :validation-sent         "Validation link sent."
    :validation-sent-to      "Validation link sent to %s"}
   :fr-FR
   {:already-subscribed      "Votre adresse d'email est déjà inscrite à cette liste."
    :confirm-subscription    "Confirmez votre inscription à la liste %s"
    :confirmation-sent-to    "Lien de confirmation envoyé à %s."
    :email-address           "Adresse de courriel"
    :missing                 "Traduction manquante"
    :regenerate-token        "Le jeton pour l'email %s a été renouvelé."
    :return-to-site          "Cliquez pour revenir au site"
    :subscribe               "S'inscrire"
    :subscribed-message      "Votre inscription à la liste %s est bien prise en compte, merci !"
    :successful-subscription "Inscription réussie !"
    :thanks                  "Merci !"
    :title                   "Inscription à une liste de discussion"
    :validation-sent         "Lien de validation envoyé."
    :validation-sent-to      "Lien de validation envoyé à %s."}})

(def lang (keyword (or (System/getenv "SUBSCRIBE_LOCALE") "en-GB")))

(def opts {:dict localization})

(def i18n (partial tr opts [lang]))

;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.i18n
  (:require [taoensso.tempura :refer [tr]]
            [subscribe.config :as config]))

(def localization
  {:en-GB
   {:already-subscribed      "Your email address is already subscribed to this list."
    :confirm-subscription    "Please confirm your subscription to the list %s"
    :confirmation-sent-to    "Confirmation link sent to %s."
    :done                    "You're all set!"
    :email-address           "Email address"
    :error                   "Ooops!"
    :missing                 "Missing translation"
    :mailing-lists           "The list of lists"
    :go-subscription-page    "Go to the subscription page."
    :regenerate-token        "Email token of %s for %s regenerated."
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
    :done                    "C'est fini !"
    :email-address           "Adresse de courriel"
    :error                   "Ooops !"
    :missing                 "Traduction manquante"
    :mailing-lists           "La liste des listes"
    :go-subscription-page    "Aller à la page d'inscription."
    :regenerate-token        "Le jeton de %s pour la liste %s a été renouvelé."
    :return-to-site          "Cliquez pour revenir au site"
    :subscribe               "S'inscrire"
    :subscribed-message      "Votre inscription à la liste %s est bien prise en compte, merci !"
    :successful-subscription "Inscription réussie !"
    :thanks                  "Merci !"
    :title                   "Inscription à une liste de discussion"
    :validation-sent         "Lien de validation envoyé."
    :validation-sent-to      "Lien de validation envoyé à %s."}})

(def lang (keyword (or (not-empty (:locale (config/config))) "en-GB")))

(def opts {:dict localization})

(def i18n (partial tr opts [lang]))

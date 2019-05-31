;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.i18n
  (:require [taoensso.tempura :refer [tr]]
            [subscribe.config :as config]))

(def localization
  {:en-GB
   {:already-subscribed        "Your email address is already subscribed to this list."
    :not-subscribed            "Your email address is not subscribed to this list."
    :confirm-subscription      "Please confirm your subscription to the list %s"
    :confirm-unsubscription    "Please confirm your unsubscription to the list %s"
    :confirmation-sent-to      "Confirmation link sent to %s."
    :done                      "You're all set!"
    :email-address             "Email address"
    :name                      "Name"
    :error                     "Ooops!"
    :missing                   "Missing translation"
    :mailing-lists             "The list of your lists"
    :go-subscribe-page         "Subscribe"
    :go-unsubscribe-page       "Unsubscribe"
    :regenerate-token          "Email token of %s for %s regenerated."
    :return-to-site            "Return to website"
    :subscribe                 "Subscribe"
    :unsubscribe               "Unsubscribe"
    :subscribed-message        "Your subscription to the list %s is now effective, thanks!"
    :successful-subscription   "Subscription successful."
    :successful-unsubscription "Unsubscription successful."
    :thanks                    "Thanks!"
    :bye                       "Bye bye!"
    :title                     "Newsletter subscription"
    :validation-sent           "Validation link sent."
    :validation-sent-to        "Validation link sent to %s"}
   :fr-FR
   {:already-subscribed        "Votre adresse email est déjà inscrite à cette liste."
    :not-subscribed            "Votre adresse email n'était pas inscrite à cette liste."
    :confirm-subscription      "Confirmez votre inscription à la liste %s"
    :confirm-unsubscription    "Merci de confirmer votre désinscription de la liste %s"
    :confirmation-sent-to      "Lien de confirmation envoyé à %s."
    :done                      "C'est fini !"
    :email-address             "Adresse de courriel"
    :name                      "Nom"
    :error                     "Ooops !"
    :missing                   "Traduction manquante"
    :mailing-lists             "La liste des listes"
    :go-subscribe-page         "Inscription"
    :go-unsubscribe-page       "Désincription"
    :regenerate-token          "Le jeton de %s pour la liste %s a été renouvelé."
    :return-to-site            "Cliquez pour revenir au site"
    :subscribe                 "S'inscrire"
    :unsubscribe               "Se désinscrire"
    :subscribed-message        "Votre inscription à la liste %s est bien prise en compte, merci !"
    :successful-subscription   "Inscription réussie !"
    :successful-unsubscription "Désinscription réussie !"
    :thanks                    "Merci !"
    :bye                       "Adieu !"
    :title                     "Inscription à une liste de discussion"
    :validation-sent           "Lien de validation envoyé."
    :validation-sent-to        "Lien de validation envoyé à %s."}})

(def lang (keyword (or (not-empty (:locale (config/config))) "en-GB")))

(def opts {:dict localization})

(def i18n (partial tr opts [lang]))

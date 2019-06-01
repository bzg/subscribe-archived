;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.i18n
  (:require [taoensso.tempura :refer [tr]]
            [subscribe.config :as config]))

(def localization
  {:en-GB
   {:already-subscribed        "Your email address is already subscribed to this list."
    :not-subscribed            "Your are not subscribed to this list, we cannot unsubscribe you."
    :confirm-subscription      "Please confirm your subscription to the list %s"
    :confirm-unsubscription    "Please confirm your unsubscription to the list %s"
    :confirmation-sent-to      "Confirmation link sent to %s."
    :opening                   "Hello,"
    :closing                   "Thanks!"
    :done                      "You're all set!"
    :email-address             "Email address"
    :name                      "Name"
    :error                     "Ooops!"
    :missing                   "Missing translation"
    :subscribed-to             "Subscribed to the %s mailing list"
    :unsubscribed-from         "Unsubscribed from the %s mailing list"
    :mailing-lists             "Mailing lists"
    :subscribe-button          "Subscribe"
    :made-with                 "Made with"
    :tos                       "Terms of service"
    :unsubscribe-button        "Unsubscribe"
    :regenerate-token          "The token of %s for %s has been regenerated."
    :return-to-site            "Click here to return to our website."
    :subscribe                 "Subscribe"
    :unsubscribe               "Unsubscribe"
    :subscribed-message        "Your subscription to the %s mailing list is now effective."
    :unsubscribed-message      "We unsubscribed you from the %s mailing list."
    :successful-subscription   "You are now subscribed."
    :successful-unsubscription "You are now unsubscribed."
    :thanks                    "Thanks!"
    :bye                       "Bye!"
    :title                     "Mailing list subscription"
    :validation-sent           "Please check your inbox: we sent you a validation link by email. Thanks!"
    :validation-sent-to        "Validation link sent to %s"}

   :fr-FR
   {:already-subscribed        "Votre adresse email est déjà inscrite à cette liste."
    :not-subscribed            "Désolé, votre adresse email n'était pas inscrite à cette liste."
    :confirm-subscription      "Merci de confirmer votre inscription à la liste %s"
    :confirm-unsubscription    "Merci de confirmer votre désinscription de la liste %s"
    :confirmation-sent-to      "Lien de confirmation envoyé à %s."
    :opening                   "Bonjour,"
    :closing                   "Bonne journée !"
    :done                      "Tout est bon !"
    :email-address             "Adresse de courriel"
    :name                      "Nom"
    :error                     "Ooops !"
    :missing                   "Traduction manquante"
    :mailing-lists             "Listes de diffusion"
    :subscribed-to             "Inscription à la liste %s"
    :unsubscribed-from         "Désinscription de la liste %s"
    :subscribe-button          "Inscription"
    :made-with                 "Fait avec"
    :tos                       "Conditions générales d'utilisation"
    :unsubscribe-button        "Désincription"
    :regenerate-token          "Le jeton de %s pour la liste %s a été renouvelé."
    :return-to-site            "Cliquez pour revenir au site."
    :subscribe                 "S'inscrire"
    :unsubscribe               "Se désinscrire"
    :subscribed-message        "Votre inscription à la liste %s est bien prise en compte."
    :unsubscribed-message      "Nous vous avons correctement désinscrit de la liste %s."
    :successful-subscription   "Inscription réussie !"
    :successful-unsubscription "Désinscription réussie !"
    :thanks                    "Merci !"
    :bye                       "À bientôt !"
    :title                     "Inscription à une liste de diffusion"
    :validation-sent           "Vérifier vos messages : nous venons de vous envoyer un lien pour valider votre inscription."
    :validation-sent-to        "Lien de validation envoyé à %s."}})

(def localization-custom
  (into {}
        (map (fn [locale] {(key locale)
                           (merge (val locale) config/ui-strings)})
             localization)))

(def lang (keyword (or (not-empty (:locale config/config)) "en-GB")))

(def opts {:dict localization-custom})

(def i18n (partial tr opts [lang]))


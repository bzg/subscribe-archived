;; Copyright (c) 2019-2021 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns i18n
  "Subscribe localization."
  (:require [taoensso.tempura :refer [tr]]
            [config :as config]))

(def supported-languages
  "A set of supported languages."
  #{"en" "fr"})

(def localization
  {:en
   {:already-subscribed               "Your email address is already subscribed to this list."
    :not-subscribed                   "Your are not subscribed to this list, we cannot unsubscribe you."
    :confirm-subscription             "Please confirm your subscription to \"%s\""
    :confirm-unsubscription           "Please confirm your unsubscription \"%s\""
    :subscribe-confirmation-sent-to   "%s: subscription confirmed for %s"
    :unsubscribe-confirmation-sent-to "%s: unsubscription confirmed for %s"
    :opening-name                     "Hello %s,"
    :opening-no-name                  "Hello,"
    :closing                          "Thanks!"
    :click-here                       "click here"
    :done                             "You're all set!"
    :email-address                    "Email address"
    :name                             "Name"
    :error                            "Ooops!"
    :missing                          "Missing translation"
    :subscribed-to                    "Subscribed to \"%s\""
    :unsubscribed-from                "Unsubscribed from \"%s\""
    :mailing-lists                    "Mailing lists"
    :subscribe-button                 "Subscribe"
    :subscribing                      "Subscription"
    :unsubscribing                    "Unsubscription"
    :made-with                        "Made with"
    :tos                              "Terms of service"
    :unsubscribe-button               "Unsubscribe"
    :regenerate-token                 "The token of %s for \"%s\" has been regenerated."
    :return-to-site                   "Click here to return to our website."
    :subscribe                        "Subscribe"
    :unsubscribe                      "Unsubscribe"
    :subscribed-message               "Your are now subscribed (%s)."
    :unsubscribed-message             "Your are now unsubscribed (%s)."
    :successful-subscription          "You are now subscribed."
    :successful-unsubscription        "You are now unsubscribed."
    :thanks                           "Thanks!"
    :bye                              "Bye!"
    :validation-sent                  "Please check your inbox: we sent you a validation link by email. Thanks!"
    :validation-sent-to               "%s: validation link sent to %s"
    :subscribers-added                "%s subscribers added to %s"}

   :fr
   {:already-subscribed               "Votre adresse email est déjà inscrite à cette liste."
    :not-subscribed                   "Désolé, votre adresse email n'était pas inscrite à cette liste."
    :confirm-subscription             "Merci de confirmer votre inscription à \"%s\""
    :confirm-unsubscription           "Merci de confirmer votre désinscription de \"%s\""
    :subscribe-confirmation-sent-to   "%s: inscription faite pour %s."
    :unsubscribe-confirmation-sent-to "%s: désinscription faite pour %s."
    :opening-name                     "Bonjour %s,"
    :opening-no-name                  "Bonjour,"
    :closing                          "Bonne journée !"
    :click-here                       "cliquez ici"
    :done                             "Tout est bon !"
    :email-address                    "Adresse de courriel"
    :name                             "Nom"
    :error                            "Ooops !"
    :missing                          "Traduction manquante"
    :mailing-lists                    "Listes de diffusion"
    :subscribing                      "Inscription"
    :unsubscribing                    "Désincription"
    :subscribed-to                    "Inscription à \"%s\""
    :unsubscribed-from                "Désinscription de \"%s\""
    :subscribe-button                 "Inscription"
    :made-with                        "Fait avec"
    :tos                              "Conditions générales d'utilisation"
    :unsubscribe-button               "Désincription"
    :regenerate-token                 "Le jeton de %s pour \"%s\" a été renouvelé."
    :return-to-site                   "Cliquez pour revenir au site."
    :subscribe                        "S'inscrire"
    :unsubscribe                      "Se désinscrire"
    :subscribed-message               "Vous avez bien été inscrit (%s)."
    :unsubscribed-message             "Vous avez bien été désincrit (%s)."
    :successful-subscription          "Inscription réussie !"
    :successful-unsubscription        "Désinscription réussie !"
    :thanks                           "Merci !"
    :bye                              "À bientôt !"
    :validation-sent                  "Vérifiez vos messages : nous venons de vous envoyer un lien pour valider votre inscription."
    :validation-sent-to               "%s: envoi du lien de validation vers %s."
    :subscribers-added                "%s inscrits ajoutés à %s"}})

(def localization-custom
  (into {}
        (map (fn [locale] {(key locale)
                           (merge (val locale) config/ui-strings)})
             localization)))

(def opts {:dict localization-custom})

(defn i [lang input] (tr opts [lang] input))

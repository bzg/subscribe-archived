;; Copyright (c) 2019-2020 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(ns subscribe.test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.java.io :as io]
            [subscribe.handler :refer :all]
            [subscribe.config :as config])
  (:import org.apache.commons.validator.routines.UrlValidator))

(defn valid-url? [url-str]
  (let [validator (UrlValidator.)]
    (.isValid validator url-str)))

(defn regexp? [re-str]
  (= (re-pattern re-str) re-str))

(deftest test-environment-variables
  (testing "Checking if all environment variables contain strings."
    (is (and (string? (System/getenv "MAILGUN_API_KEY"))
             (string? (System/getenv "MAILGUN_API_SECRET"))
             (string? (System/getenv "MAILJET_API_KEY"))
             (string? (System/getenv "MAILJET_API_SECRET"))
             (string? (System/getenv "SUBSCRIBE_SMTP_LOGIN"))
             (string? (System/getenv "SUBSCRIBE_SMTP_PASSWORD"))
             (string? (System/getenv "SUBSCRIBE_PORT"))
             (string? (System/getenv "SUBSCRIBE_BASEURL"))))))

(deftest test-lists-exists
  (testing "Checking mailgun connection and existing list(s)."
    (is (boolean (not-empty (get-lists-from-server))))))

;; Configuration keys
(s/def ::from string?)
(s/def ::to string?)
(s/def ::msg-id string?)

(s/def ::return-url valid-url?)
(s/def ::base-url valid-url?)
(s/def ::tos-url valid-url?)

(s/def ::admin-email string?)
(s/def ::port int?)
(s/def ::smtp-host string?)
(s/def ::smtp-login string?)
(s/def ::smtp-password string?)
(s/def ::locale string?)
(s/def ::team string?)
(s/def ::db-uri string?)
(s/def ::log-file string?)
(s/def ::lists-exclude-regexp regexp?)
(s/def ::lists-include-regexp regexp?)
(s/def ::warn-every-x-subscribers int?)

(s/def ::config
  (s/keys
   :req-un [::admin-email ::base-url]
   :opt-un [::from ::return-url ::tos-url ::msg-id
            ::locale ::team ::log-file ::port ::db-uri
            ::lists-exclude-regexp ::lists-include-regexp
            ::smtp-login ::smtp-password ::smtp-host
            ::warn-every-x-subscribers]))

(deftest test-config-specs
  (testing "Checking entries in the configuration map."
    (is (s/valid? ::config config/config))))

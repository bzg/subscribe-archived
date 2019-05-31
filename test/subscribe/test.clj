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

(deftest test-config-exists
  (testing "Checking if SUBSCRIBE_CONFIG points to an existing file."
    (is (.exists (io/file (System/getenv "SUBSCRIBE_CONFIG"))))))

(deftest test-lists-exists
  (testing "Checking mailgun connection and existing list(s)."
    (is (boolean (not-empty (get-lists-from-server))))))

(s/def ::mailgun-api-key string?)
(s/def ::mailgun-login string?)
(s/def ::mailgun-password string?)
(s/def ::mailgun-from string?)
(s/def ::base-url valid-url?)
(s/def ::return-url valid-url?)
(s/def ::admin-email string?)
(s/def ::locale string?)
(s/def ::port int?)
(s/def ::db-uri string?)
(s/def ::log-file string?)
(s/def ::lists-exclude-regexp (s/nilable regexp?))
(s/def ::lists-include-regexp (s/nilable regexp?))
(s/def ::warn-every-x-subscribers int?)

(s/def ::config
  (s/keys :req-un [::mailgun-api-key
                   ::mailgun-login
                   ::mailgun-password
                   ::mailgun-from
                   ::base-url
                   ::return-url
                   ::admin-email]
          :opt-un [::locale
                   ::log-file
                   ::port
                   ::db-uri
                   ::lists-exclude-regexp
                   ::lists-include-regexp
                   ::warn-every-x-subscribers]))

(deftest test-config-specs
  (testing "Checking entries in the configuration map."
    (is (s/valid? ::config (config/config)))))

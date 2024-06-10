;; Copyright (c) 2019-2023 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(defproject
  subscribe "0.7.5"
  :url "https://git.sr.ht/~bzg/subscribe"
  :description "Web application to subscribe to mailing lists."
  :license {:name "Eclipse Public License v2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :min-lein-version "2.0.0"
  :dependencies [
                 ;; [integrant/integrant "0.10.0"]
                 [cheshire/cheshire "5.13.0"]
                 [com.draines/postal "2.0.5"]
                 [com.taoensso/tempura "1.5.4"]
                 [com.taoensso/timbre "6.5.0"]
                 [commons-validator/commons-validator "1.9.0"]
                 [hiccup/hiccup "1.0.5"]
                 [http-kit/http-kit "2.8.0"]
                 [markdown-clj/markdown-clj "1.12.1"]
                 [metosin/reitit "0.7.0"]
                 [metosin/reitit-middleware "0.7.0"]
                 [metosin/reitit-ring  "0.7.0"]
                 [org.clojure/clojure "1.11.3"]
                 [org.clojure/core.async "1.6.681"]
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                 [ring-cors/ring-cors "0.1.13"]
                 [ring/ring-devel "1.12.1"]
                 [selmer/selmer "1.12.61"]
                 ]
  :main core
  :jvm-opts ["-Xmx500m"]
  :profiles {:uberjar {:omit-source    true
                       :aot            :all
                       :uberjar-name   "subscribe.jar"
                       :source-paths   ["src/" ]
                       :resource-paths ["resources"]}})

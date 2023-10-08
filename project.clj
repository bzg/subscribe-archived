;; Copyright (c) 2019-2023 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(defproject
  subscribe "0.7.4"
  :url "https://github.com/bzg/subscribe"
  :description "Web application to subscribe to mailing lists."
  :license {:name "Eclipse Public License v2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.681"
                  :exclusions [org.clojure/tools.reader]]
                 [compojure "1.7.0"]
                 [hiccup "1.0.5"]
                 [ring/ring-core "1.10.0"]
                 [ring/ring-jetty-adapter "1.10.0"]
                 [ring/ring-devel "1.10.0"]
                 [ring/ring-defaults "0.4.0"]
                 [cheshire "5.12.0"]
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                 [com.taoensso/tempura "1.5.3"]
                 [com.draines/postal "2.0.5"]
                 [com.taoensso/timbre "6.3.1"]
                 [commons-validator "1.7"]]
  :main core
  :jvm-opts ["-Xmx500m"]
  :profiles {:uberjar {:omit-source    true
                       :aot            :all
                       :uberjar-name   "subscribe.jar"
                       :source-paths   ["src/" ]
                       :resource-paths ["resources"]}})

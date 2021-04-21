;; Copyright (c) 2019-2021 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(defproject
  subscribe "0.7.4"
  :url "https://github.com/bzg/subscribe"
  :description "Web application to subscribe to mailing lists."
  :license {:name "Eclipse Public License v2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/core.async "1.3.610"
                  :exclusions [org.clojure/tools.reader]]
                 [compojure "1.6.2"]
                 [hiccup "1.0.5"]
                 [ring/ring-core "1.9.2"]
                 [ring/ring-jetty-adapter "1.9.2"]
                 [ring/ring-devel "1.9.2"]
                 [ring/ring-defaults "0.3.2"]
                 [cheshire "5.10.0"]
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                 [com.taoensso/tempura "1.2.1"]
                 [com.draines/postal "2.0.4"]
                 [com.taoensso/encore "3.12.1"]
                 [com.taoensso/timbre "5.1.2"]
                 [commons-validator "1.7"]]
  :main core
  :jvm-opts ["-Xmx500m"]
  :profiles {:uberjar {:aot :all}}
  :uberjar-name "subscribe-standalone.jar")

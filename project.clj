;; Copyright (c) 2019-2020 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(defproject
  subscribe "0.6.1"
  :url "https://github.com/bzg/subscribe"
  :license {:name "Eclipse Public License v2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "0.7.559"
                  :exclusions [org.clojure/tools.reader]]
                 [compojure "1.6.1"]
                 [hiccup "1.0.5"]
                 [ring/ring-core "1.8.0"]
                 [ring/ring-jetty-adapter "1.8.0"]
                 [ring/ring-devel "1.8.0"]
                 [ring/ring-defaults "0.3.2"]
                 [cheshire "5.10.0"]
                 [clj-http "3.10.0"]
                 [com.taoensso/tempura "1.2.1"]
                 [io.replikativ/datahike "0.2.1"]
                 [com.draines/postal "2.0.3"]
                 [com.taoensso/timbre "4.10.0"]
                 [commons-validator "1.6"]]
  :description "Web application to subscribe to mailing lists."
  :main subscribe.handler
  :jvm-opts ["-Xmx500m"]
  :profiles {:uberjar {:aot :all}}
  :uberjar-name "subscribe-standalone.jar")

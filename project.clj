;; Copyright (c) 2019-2020 Bastien Guerry <bzg@bzg.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(defproject
  subscribe "0.5.0"
  :url "https://github.com/etalab/subscribe"
  :license {:name "Eclipse Public License v2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "0.6.532"
                  :exclusions [org.clojure/tools.reader]]
                 [compojure "1.6.1"]
                 [hiccup "1.0.5"]
                 [ring "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [http-kit "2.3.0"]
                 [cheshire "5.9.0"]
                 [clj-http "3.10.0"]
                 [com.taoensso/tempura "1.2.1"]
                 [io.replikativ/datahike "0.2.0"]
                 [com.draines/postal "2.0.3"]
                 [com.taoensso/timbre "4.10.0"]
                 [commons-validator "1.6"]]
  :description "Web app to subscribe to mailgun mailing lists."
  :main subscribe.handler
  :jvm-opts ["-Xmx500m"]
  :profiles {:uberjar {:aot :all}}
  :uberjar-name "subscribe-standalone.jar")

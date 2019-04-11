;; Copyright (c) 2019 Bastien Guerry <bzg@bzg.fr>

;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(defproject
  subscribe "0.1.1"
  :url "https://github.com/etalab/subscribe"
  :license {:name "Eclipse Public License v2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-ancient "0.6.14"]]
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.4.490"
                  :exclusions [org.clojure/tools.reader]]
                 [compojure "1.6.1"]
                 [hiccup "1.0.5"]
                 [ring "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [http-kit "2.3.0"]
                 [cheshire "5.8.1"]
                 [clj-http "3.9.1"]
                 [com.taoensso/tempura "1.2.1"]
                 [io.replikativ/datahike "0.1.3"]
                 [com.draines/postal "2.0.3"]
                 [cheshire "5.8.1"]]
  :description "Subscribe to a mailgun mailing list."
  :main subscribe.handler
  :profiles {:uberjar {:aot :all}})

(ns subscribe.test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]))

(deftest test-config
  (testing "Checking if SUBSCRIBE_CONFIG points to an existing file."
    (is (.exists (io/file (System/getenv "SUBSCRIBE_CONFIG"))))))

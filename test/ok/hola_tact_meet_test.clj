(ns ok.hola-tact-meet-test
  (:require [clojure.test :refer :all]
            [ok.hola-tact-meet.core :as core]))

(deftest basic-test
  (testing "Basic functionality"
    (is (= 1 1))))

(deftest app-config-test
  (testing "App config exists"
    (is (some? core/app-config))))

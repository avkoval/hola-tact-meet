(ns ok.hola-tact-meet.views-test
  (:require [clojure.test :refer :all]
            [ok.hola-tact-meet.views :refer [parse-timer-string]]))

(deftest parse-timer-string-test
  (testing "Parse timer string with minutes and seconds format MM:SS"
    (is (= 80 (parse-timer-string "01:20")))
    (is (= 20 (parse-timer-string "00:20")))
    (is (= 125 (parse-timer-string "02:05")))
    (is (= 600 (parse-timer-string "10:00"))))

  (testing "Parse timer string with minutes and seconds format M:SS"
    (is (= 80 (parse-timer-string "1:20")))
    (is (= 20 (parse-timer-string "0:20")))
    (is (= 125 (parse-timer-string "2:05"))))

  (testing "Parse timer string with seconds only"
    (is (= 20 (parse-timer-string "20")))
    (is (= 0 (parse-timer-string "0")))
    (is (= 300 (parse-timer-string "300"))))

  (testing "Parse invalid timer strings"
    (is (= 0 (parse-timer-string "")))
    (is (= 0 (parse-timer-string "invalid")))
    (is (= 0 (parse-timer-string "10:20:30")))))

(ns ok.hola-tact-meet-test
  (:require [clojure.test :refer :all]))

(deftest basic-test
  (testing "Basic functionality"
    (is (= 1 1))))

(deftest string-manipulation-test
  (testing "String operations work"
    (is (= "hello world" (str "hello" " " "world")))))

(deftest math-test
  (testing "Math operations work"
    (is (= 4 (+ 2 2)))
    (is (= 6 (* 2 3)))))

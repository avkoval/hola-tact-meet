(ns hola-tact-meet.handler-test
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :as mock]
    [hola-tact-meet.handler :refer :all]
    [hola-tact-meet.middleware.formats :as formats]
    [muuntaja.core :as m]
    [mount.core :as mount]))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'hola-tact-meet.config/env
                 #'hola-tact-meet.handler/app-routes)
    (f)))

(deftest test-app
  (testing "main route"
    (let [request (-> (mock/request :get "/")
                      (mock/header "ngrok-auth-user-email" "alex@smith.com")
                      )
          response ((app) request)
          ]
      (is (= 200 (:status response)))
      ))

  (testing "not-found route"
    (let [response ((app) (mock/request :get "/invalid"))]
      (is (= 404 (:status response))))))

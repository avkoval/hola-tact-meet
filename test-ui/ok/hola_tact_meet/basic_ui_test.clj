(ns ok.hola-tact-meet.basic-ui-test
  (:require [clojure.test :refer :all]
            [wally.main :as w]
            [wally.selectors :as ws]
            [garden.selectors :as s]
            [ok.hola-tact-meet.utils :as utils]
            ))

(def APP_URL (str "http://localhost:" (:server/port (utils/app-config))  "/"))

(deftest ^:ui fetch-site-homepage-test
  (testing "Can fetch a website homepage"
    (do
      ;; When some command is run for the first time, Playwright
      ;; will kick in and open a browser.
      (w/navigate APP_URL)
      (w/click (s/a (s/attr= :href "/login/fake")))

      ; (w/click [(ws/text "Copy") (ws/nth= "1")])
      )

    ;; Check number of downloads for reitit.
    ;; (do
    ;;   (w/fill :#search "reitit")
    ;;   (w/keyboard-press "Enter")

    ;;   (.textContent (w/-query (ws/text "Downloads"))))

    ;; Get the Playwright page object.
    ;; https://playwright.dev/docs/api/class-page.
    ;; (w/get-page)

    ))

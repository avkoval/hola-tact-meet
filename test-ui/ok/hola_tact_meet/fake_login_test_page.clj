(ns ok.hola-tact-meet.fake-login-test-page
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [wally.main :as w]
            [wally.selectors :as ws]
            [ok.hola-tact-meet.common :as common]
            ))


(def APP_URL common/APP_URL)

;; =============================================================================
;; PAGE OBJECT MODEL - Fake Login Page
;; =============================================================================

(defn navigate-to-fake-login-page
  "Navigate to the fake login page"
  []
  (w/navigate (str APP_URL "login/fake")))

(defn get-page-title
  "Get the page title"
  []
  (clojure.string/trim (.textContent (w/-query "h1.title"))))

;; =============================================================================
;; REPL DEVELOPMENT HELPERS
;; =============================================================================

(comment
  ;; REPL Development Section
  ;; Run these forms interactively during development

  ;; 1. Start test server with clean database
  (common/start-test-server!)

  ;; 2. Navigate to fake login page (browser starts automatically)
  (navigate-to-fake-login-page)

  ;; 3. Test page elements
  (get-page-title)

  ;; 4. Clean up
  (common/stop-test-server!)

  ;; Quick restart sequence
  (do
    (common/restart-test-server!)
    (navigate-to-fake-login-page))

  ;; Alternative: Use with-test-server for automatic cleanup
  (common/with-test-server
    (fn []
      (navigate-to-fake-login-page)
      (get-page-title))))

;; =============================================================================
;; TEST FIXTURES
;; =============================================================================

(defn ui-test-fixture
  "Fixture that starts test server once for all tests"
  [test-fn]
  (common/with-test-server
    (fn []
      (test-fn))))

(use-fixtures :once ui-test-fixture)

;; =============================================================================
;; ACTUAL TESTS
;; =============================================================================

(deftest ^:ui fake-login-generate-random-data-works-fine

  (testing "Fake login page loads correctly"
    (navigate-to-fake-login-page)
    (is (= "Fake Login (Development Only)" (get-page-title))
        "Page title should be 'Fake Login (Development Only)'"))

  (testing "generate random data"
    ;; at first all values must be empty
    (is (= "" (.inputValue (w/-query "input[name='name']"))) "The value should be empty")
    (is (= "" (.inputValue (w/-query "input[name='email']"))) "The value should be empty")
    (is (= "" (.inputValue (w/-query "input[name='given-name']"))) "The value should be empty")
    (is (= "" (.inputValue (w/-query "input[name='family-name']"))) "The value should be empty")
    (is (= "" (.inputValue (w/-query "select[name='access-level']"))) "The value should be empty")

    (w/click [(ws/text "Generate Random Data")])

    (is (.inputValue (w/-query "input[name='name']")) "The value should NOT be empty")
    (is (.inputValue (w/-query "input[name='email']")) "The value should NOT be empty")
    (is (.inputValue (w/-query "input[name='given-name']")) "The value should NOT be empty")
    (is (.inputValue (w/-query "input[name='family-name']")) "The value should NOT be empty")
    (is (.inputValue (w/-query "select[name='access-level']")) "The value should NOT be empty")
    )

)

(comment (fake-login-generate-random-data-works-fine))

(deftest ^:ui create-fake-admin-user-test
  (testing "Create fake admin user with random data"
    (navigate-to-fake-login-page)

    ;; Generate random data
    (w/click [(ws/text "Generate Random Data")])

    ;; Set access level to admin
    (.selectOption (w/-query "select[name='access-level']") "admin")

    ;; Submit the form
    (w/click [(ws/text "Create & Login")])

    ;; Verify we're redirected (should be on app landing page)
    (is (clojure.string/includes? (w/url) "app")
        "Should be redirected to app after login")))

(comment (create-fake-admin-user-test))

(deftest ^:ui create-fake-staff-user-test
  (testing "Create fake staff user with random data"
    (navigate-to-fake-login-page)

    ;; Generate random data
    (w/click [(ws/text "Generate Random Data")])

    ;; Set access level to staff
    (.selectOption (w/-query "select[name='access-level']") "staff")

    ;; Submit the form
    (w/click [(ws/text "Create & Login")])

    ;; Verify we're redirected (should be on app landing page)
    (is (clojure.string/includes? (w/url) "app")
        "Should be redirected to app after login")))

(comment (create-fake-staff-user-test))

(deftest ^:ui create-fake-regular-user-test
  (testing "Create fake regular user with random data"
    (navigate-to-fake-login-page)

    ;; Generate random data
    (w/click [(ws/text "Generate Random Data")])

    ;; Set access level to user
    (.selectOption (w/-query "select[name='access-level']") "user")

    ;; Submit the form
    (w/click [(ws/text "Create & Login")])

    ;; Verify we're redirected (should be on app landing page)
    (is (clojure.string/includes? (w/url) "app")
        "Should be redirected to app after login")))

(comment (create-fake-regular-user-test))

(ns ok.hola-tact-meet.admin-users-page-test
  (:require [clojure.test :refer :all]
            [wally.main :as w]
            [wally.selectors :as ws]
            [garden.selectors :as s]
            [ok.hola-tact-meet.common :as common]
            [ok.hola-tact-meet.utils :as utils]))


(def APP_URL common/APP_URL)

;; =============================================================================
;; PAGE OBJECT MODEL - Admin Users Page
;; =============================================================================

(defn navigate-to-admin-users-page
  "Navigate to the admin users management page"
  []
  (w/navigate (str APP_URL "admin/manage-users")))

(defn get-page-title
  "Get the page title"
  []
  (clojure.string/trim (.textContent (w/-query "h1.title"))))

(defn get-users-table
  "Get the users table element"
  []
  (w/-query "table.table"))

(defn get-user-rows
  "Get all user rows from the table"
  []
  ;; Use a simpler approach - get the first row for now
  (w/-query "tbody tr"))

(defn get-user-row-by-email
  "Get user row by email address"
  [email]
  (w/-query [(ws/text email)]))

(defn get-user-access-level
  "Get user access level from row - simplified to get first user's access level"
  []
  (let [cell (w/-query "tbody tr td:nth-child(3)")]
    (when cell (.textContent cell))))

(defn get-user-status
  "Get user status from row - simplified to get first user's status"
  []
  (let [cell (w/-query "tbody tr td:nth-child(4)")]
    (when cell (.textContent cell))))

(defn click-user-toggle
  "Click the first user's toggle button"
  []
  (w/click "tbody tr button"))

(defn click-manage-teams
  "Click the first user's manage teams button"
  []
  (w/click "tbody tr a[href*='teams']"))

(defn get-summary-stats
  "Get summary statistics from the bottom boxes"
  []
  {:total-users (Integer/parseInt (or (.textContent (w/-query ".column:nth-child(1) .title")) "0"))
   :active-users (Integer/parseInt (or (.textContent (w/-query ".column:nth-child(2) .title")) "0"))
   :admin-count (Integer/parseInt (or (.textContent (w/-query ".column:nth-child(3) .title")) "0"))
   :staff-count (Integer/parseInt (or (.textContent (w/-query ".column:nth-child(4) .title")) "0"))})

(defn is-manage-teams-modal-open?
  "Check if the manage teams modal is open"
  []
  (let [modal (w/-query "#manageTeamsModal.is-active")]
    (not (nil? modal))))

(defn close-manage-teams-modal
  "Close the manage teams modal"
  []
  (w/click "#manageTeamsModal .delete"))

;; =============================================================================
;; TEST HELPERS - Use common functions from common.clj
;; =============================================================================

;; =============================================================================
;; REPL DEVELOPMENT HELPERS
;; =============================================================================

(comment
  ;; REPL Development Section
  ;; Run these forms interactively during development

  ;; 1. Start test server with clean database
  (common/start-test-server!)

  ;; 2. Start Wally browser session
  (w/start-browser!)

  ;; 3. Setup test data and login
  (common/setup-test-users)
  (common/login-as-admin)

  ;; 4. Navigate to admin users page
  (navigate-to-admin-users-page)

  ;; 5. Test page elements
  (get-page-title)
  (get-summary-stats)
  (count (get-user-rows))

  ;; 6. Test specific user interactions
  (get-user-access-level)
  (get-user-status)
  (count (get-user-rows))

  ;; 7. Clean up
  (w/stop-browser!)
  (common/stop-test-server!)

  ;; Quick restart sequence
  (do
    (common/restart-test-server!)
    (w/start-browser!)
    (common/setup-test-users)
    (common/login-as-admin)
    (navigate-to-admin-users-page))

  ;; Alternative: Use with-test-server for automatic cleanup
  (common/with-test-server
    (fn []
      (w/start-browser!)
      (common/login-as-admin)
      (navigate-to-admin-users-page)
      (get-page-title)
      (w/stop-browser!))))

;; =============================================================================
;; TEST FIXTURES
;; =============================================================================

(defn ui-test-fixture
  "Fixture that starts test server and browser for each UI test"
  [test-fn]
  (common/with-test-server
    (fn []
      ;; Give server extra time to fully start
      ;; (Thread/sleep 500)
      (test-fn))))

(use-fixtures :each ui-test-fixture)

;; =============================================================================
;; ACTUAL TESTS
;; =============================================================================

(deftest ^:ui admin-users-page-loads-test
  (testing "Admin users page loads correctly"
    (do
      (common/login-as-admin)
      (navigate-to-admin-users-page)

      (is (= "Manage Users" (get-page-title))
          "Page title should be 'Manage Users'")

      (is (w/-query "table.table")
          "Users table should be visible")

      (is (w/-query ".column .title")
          "Summary statistics should be visible"))))

(deftest ^:ui admin-users-summary-stats-test
  (testing "Admin users page shows correct summary statistics"
    (do
      (common/login-as-admin)
      (navigate-to-admin-users-page)

      (let [stats (get-summary-stats)]
        (is (>= (:total-users stats) 0)
            "Total users should be non-negative")
        (is (>= (:active-users stats) 0)
            "Active users should be non-negative")
        (is (<= (:admin-count stats) (:total-users stats))
            "Admin count should not exceed total users")
        (is (<= (:staff-count stats) (:total-users stats))
            "Staff count should not exceed total users")))))

(deftest ^:ui admin-users-table-structure-test
  (testing "Admin users table has correct structure"
    (do
      (common/login-as-admin)
      (navigate-to-admin-users-page)

      (is (w/-query "thead")
          "Table header should be visible")

      ;; Simplified test - just check if key elements exist
      (is (w/-query "thead th")
          "Table should have header cells"))))

;; TODO: Add more specific tests for:
;; - User status toggle functionality
;; - Manage teams modal functionality
;; - Access level changes
;; - User filtering/search
;; - Permissions validation

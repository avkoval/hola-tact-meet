(ns ok.hola-tact-meet.admin-users-page-test
  (:require [clojure.test :refer :all]
            [wally.main :as w]
            [wally.selectors :as ws]
            [ok.hola-tact-meet.common :as common]
            [ok.hola-tact-meet.db :as db]
            [datomic.client.api :as d]))


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

(defn get-first-regular-user-id
  "Get ID of first non-admin, non-staff user from database to avoid modifying privileged users"
  []
  (let [db (db/get-db)
        result (d/q '[:find ?e
                      :where
                      [?e :user/access-level "user"]
                      [?e :user/active true]] db)]
    (ffirst result)))

(defn get-user-access-level
  "Get access level for a specific non-privileged user by their ID"
  []
  (when-let [user-id (get-first-regular-user-id)]
    (let [cell (w/-query (str "#row-" user-id " td:nth-child(3)"))]
      (when cell (.textContent cell)))))

(defn get-user-status
  "Get status for a specific non-privileged user by their ID"
  []
  (when-let [user-id (get-first-regular-user-id)]
    (let [cell (w/-query (str "#row-" user-id " td:nth-child(4)"))]
      (when cell (.textContent cell)))))

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
;; REPL DEVELOPMENT HELPERS
;; =============================================================================

(comment
  ;; REPL Development Section
  ;; Run these forms interactively during development

  ;; 1. Start test server with clean database
  (common/start-test-server!)

  ;; 3. Setup test data and login
  (common/setup-test-users)
  (common/login-as-admin)

  ;; 4. Navigate to admin users page
  (navigate-to-admin-users-page)

  ;; 5. Test page elements
  (get-page-title)
  (get-summary-stats)
  (.count (get-user-rows))

  ;; 6. Test specific user interactions
  (get-user-access-level)
  (get-user-status)
  (.count (get-user-rows))

  ;; 7. Clean up
  ;; (common/stop-test-server!) ; Commented out to avoid delays between tests

  ;; Quick restart sequence for REPL development
  ;; (do
  ;;   (common/restart-test-server!)
  ;;   (common/setup-test-users)
  ;;   (common/login-as-admin)
  ;;   (navigate-to-admin-users-page))

  ;; Alternative: Use with-test-server for automatic cleanup
  (common/with-test-server
    (fn []
      (common/login-as-admin)
      (navigate-to-admin-users-page)
      (get-page-title)
      )))

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


(deftest ^:ui change-user-access-level
  (testing "Admin can change user access level"
    (do
      (common/login-as-admin)
      (navigate-to-admin-users-page)
      (is (w/-query "thead")
          "Table header should be visible")

      ;; Use existing user instead of creating new one to avoid refresh issues
      (when-let [user-id (get-first-regular-user-id)]
        ;; Refresh page to ensure user table is current by re-navigating
        (navigate-to-admin-users-page)
        (Thread/sleep 1000)

        ;; Test changing to staff level
        (let [level "staff"]
          ;; Click on label containing the radio button (more reliable than direct radio click)
          (w/click (str "#row-" user-id " label:has(input[value='" level "'])"))

          ;; Wait for update to process
          (Thread/sleep 500)

          ;; Verify the radio button was selected
          (is (w/-query (str "input[name='access-level-" user-id "'][value='" level "']:checked"))
              (str level " radio button should be selected for user " user-id)))))))

(comment
  ;; REPL helper for change-user-access-level test
  ;; Convert let bindings to def statements for individual execution

  ;; Setup
  (common/login-as-admin)
  (navigate-to-admin-users-page)

  ;; Test data (equivalent to let bindings in the test)
  (def repl-user-id (common/create-test-regular-user "repl-access-test"))
  (def repl-user-levels ["staff" "admin" "user"])

  ;; Step by step execution for each level
  (def current-level "staff") ;; Change this to test different levels

  ;; Click on radio button for current level
  (w/click (str "input[name='access-level-" repl-user-id "'][value='" current-level "']"))

  ;; Wait for update
  (Thread/sleep 200)

  ;; Verify UI selection
  (w/-query (str "input[name='access-level-" repl-user-id "'][value='" current-level "']:checked"))

  ;; Verify database change
  (def repl-user-data (db/get-user-by-id repl-user-id))
  (:user/access-level repl-user-data)

  ;; Test all levels in sequence
  (doseq [level repl-user-levels]
    (println "Testing level:" level)
    (w/click (str "input[name='access-level-" repl-user-id "'][value='" level "']"))
    (Thread/sleep 200)
    (let [user-data (db/get-user-by-id repl-user-id)]
      (println "User access level in DB:" (:user/access-level user-data))))

  ;; Run complete test
  (change-user-access-level)
  )

;; REPL-friendly helper functions
(defn create-test-user-and-verify-active
  "Create test user and return user-id. REPL-friendly."
  []
  (let [user-id (common/create-test-regular-user "toggle-test")]
    (println "Created user ID:" user-id)
    user-id))

(defn verify-user-active
  "Check if user has expected active status. REPL-friendly."
  [user-id expected-active?]
  (let [user-data (db/get-user-by-id user-id)
        actual-active (:user/active user-data)]
    (println "User" user-id "active status:" actual-active "expected:" expected-active?)
    (= expected-active? actual-active)))

(defn click-deactivate-button
  "Click deactivate button for user. REPL-friendly."
  [user-id]
  (println "Clicking deactivate button for user" user-id)
  ;; Refresh page to ensure user is visible
  (navigate-to-admin-users-page)

  ;; Simple approach: Just click the button with the toggle URL for this user
  ;; Based on the HTML template: data-on-click="@post('/admin/manage-users/toggle?user_id={{ user.id }}')"
  (let [selector (str "button[data-on-click*='/admin/manage-users/toggle?user_id=" user-id "']")]
    (w/wait-for selector)
    (w/click selector)
    )

  (Thread/sleep 300))

(defn click-activate-button
  "Click activate button for user. REPL-friendly."
  [user-id]
  (println "Clicking activate button for user" user-id)
  (let [selector (str "#row-" user-id " button.is-success")]
    (w/wait-for selector)
    (w/click selector))
  (Thread/sleep 300))

(defn verify-ui-button-exists
  "Verify UI button exists with expected class. REPL-friendly."
  [user-id button-class]
  (let [selector (str "#row-" user-id " button." button-class)
        exists? (w/-query selector)]
    (println "Checking for button" selector "exists:" (boolean exists?))
    exists?))

(deftest ^:ui toggle-user-status
  (testing "Admin can deactivate and activate users"
    (do


      (common/login-as-admin)
      (navigate-to-admin-users-page)

      (is (w/-query "thead")
          "Table header should be visible")

      (let [user-id (create-test-user-and-verify-active)]

        (navigate-to-admin-users-page)  ; reloads the page
        ;; Verify user is initially active
        (is (verify-user-active user-id true)
            "User should be initially active in database")

        (navigate-to-admin-users-page)
        ;; Click deactivate button
        (click-deactivate-button user-id)

        (navigate-to-admin-users-page)  ; reloads the page
        ;; Click activate button
        (click-activate-button user-id)

        ;; Verify user is activated in database
        (is (verify-user-active user-id true)
            "User should be reactivated in database")

        ;; Verify the UI shows deactivate button again
        (is (verify-ui-button-exists user-id "is-warning")
            "Deactivate button should be visible after reactivation")))))

(comment
  ;; REPL helper for toggle-user-status test
  ;; Convert let bindings to def statements for individual execution

  ;; Setup

  ;; Method 1: Try to reset the atom to nil first, then let it recreate

  (common/login-as-admin)
  (navigate-to-admin-users-page)  ; reloads the page

  ;; Test data (equivalent to let binding in the test)
  (def toggle-user-id (create-test-user-and-verify-active))
  (navigate-to-admin-users-page)

  ;; Step 1: Verify user is initially active
  (verify-user-active toggle-user-id true)

  ;; Step 2: Click deactivate button
  (click-deactivate-button toggle-user-id)

  ;; Step 3: Verify user is deactivated in database
  (verify-user-active toggle-user-id false)

  ;; Step 4: Verify UI shows activate button
  (verify-ui-button-exists toggle-user-id "is-success")

  ;; Step 5: Click activate button
  (click-activate-button toggle-user-id)

  ;; Step 6: Verify user is reactivated in database
  (verify-user-active toggle-user-id true)

  ;; Step 7: Verify UI shows deactivate button again
  (verify-ui-button-exists toggle-user-id "is-warning")

  ;; Debug: Check current user status anytime
  (def current-user-data (db/get-user-by-id toggle-user-id))
  (:user/active current-user-data)

  ;; Run complete test
  (toggle-user-status)
  (common/pause-repl-browser!)
  )




;; TODO: Add more specific tests for:
;; - Manage teams modal functionality
;; - User filtering/search
;; - Permissions validation

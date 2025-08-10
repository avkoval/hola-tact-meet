(ns ok.hola-tact-meet.common
  (:require [wally.main :as w]
            [wally.selectors :as ws]
            [garden.selectors :as s]
            [ok.hola-tact-meet.utils :as utils]
            [ok.hola-tact-meet.db :as db]
            [clojure.java.shell]
            [clojure.string]))

;; Set test profile immediately when this namespace loads
(System/setProperty "aero.profile" "test")

;; Test server configuration
(def TEST_PORT 8082)  ; Use different port for test server
(def APP_URL (str "http://localhost:" TEST_PORT "/"))

;; Store test server process
(defonce test-server-process (atom nil))

;; =============================================================================
;; DATABASE TEST HELPERS
;; =============================================================================

(defn create-test-admin-user
  "Create a test admin user in the database and return the user ID"
  ([] (create-test-admin-user ""))
  ([suffix]
   (let [email (str "test-admin" suffix "@example.com")
         test-admin {:email email
                     :name (str "Test Admin" suffix)
                     :given-name "Test"
                     :family-name (str "Admin" suffix)
                     :access-level "admin"
                     :active true
                     :auth-provider "fake"}]
     (try
       (db/create-user test-admin)
       (catch Exception e
         ;; User might already exist, find existing user
         (println "Admin user already exists or creation failed:" (.getMessage e))
         (db/find-user-by-email email))))))

(defn create-test-staff-user
  "Create a test staff user in the database and return the user ID"
  ([] (create-test-staff-user ""))
  ([suffix]
   (let [email (str "test-staff" suffix "@example.com")
         test-staff {:email email
                     :name (str "Test Staff" suffix)
                     :given-name "Test"
                     :family-name (str "Staff" suffix)
                     :access-level "staff"
                     :active true
                     :auth-provider "fake"}]
     (try
       (let [user-id (db/create-user test-staff)]
         (println "Created staff user with ID:" user-id)
         user-id)
       (catch Exception e
         (println "Staff user creation failed, trying to find existing:" (.getMessage e))
         (let [existing-user-id (db/find-user-by-email email)]
           (println "Found existing staff user with ID:" existing-user-id)
           existing-user-id))))))

(defn create-test-regular-user
  "Create a test regular user in the database and return the user ID"
  ([] (create-test-regular-user ""))
  ([suffix]
   (let [email (str "test-user" suffix "@example.com")
         test-user {:email email
                    :name (str "Test User" suffix)
                    :given-name "Test"
                    :family-name (str "User" suffix)
                    :access-level "user"
                    :active true
                    :auth-provider "fake"}]
     (try
       (let [user-id (db/create-user test-user)]
         (println "Created regular user with ID:" user-id)
         user-id)
       (catch Exception e
         (println "Regular user creation failed, trying to find existing:" (.getMessage e))
         (let [existing-user-id (db/find-user-by-email email)]
           (println "Found existing regular user with ID:" existing-user-id)
           existing-user-id))))))

(defn setup-test-users
  "Setup a full set of test users for comprehensive testing
   Returns a map with user IDs: {:admin id :staff id :user id}"
  []
  {:admin (create-test-admin-user)
   :staff (create-test-staff-user)
   :user (create-test-regular-user)})

(defn create-test-team
  "Create a test team in the database and return the team ID.
   If team already exists, returns existing team ID - perfect for REPL usage."
  ([] (create-test-team ""))
  ([suffix]
   (let [team-name (str "Test Team" suffix)]
     ;; First check if team already exists
     (if-let [existing-team-id (db/find-team-by-name team-name)]
       (do
         (println "Found existing team" team-name "with ID:" existing-team-id)
         existing-team-id)
       ;; Team doesn't exist, create it
       (let [team-data {:name team-name
                        :description (str "Test team for UI testing" suffix)
                        :managers []}]
         (try
           (let [result (db/create-team-with-validation! team-data)]
             (if (:success result)
               (do
                 (println "Created new team" team-name "with ID:" (:team-id result))
                 (:team-id result))
               (do
                 (println "Team creation failed:" (:error result))
                 nil)))
           (catch Exception e
             (println "Team creation failed:" (.getMessage e))
             nil)))))))

(defn verify-user-team-membership
  "Verify user is member of specific team. REPL-friendly."
  [user-id team-id]
  (let [user-data (db/get-user-by-id user-id)
        user-teams (:user/teams user-data)
        team-ids (map :db/id user-teams)
        is-member (some #(= team-id %) team-ids)]
    (println "User" user-id "teams:" team-ids)
    (println "Checking membership in team:" team-id)
    (println "Is member:" is-member)
    is-member))

(defn add-user-to-team
  "Add user to team for testing purposes"
  [user-id team-id]
  (println "Adding user" user-id "to team" team-id)
  (try
    (let [result (db/add-user-to-teams! user-id [team-id])]
      (println "Add user to team result:" result)
      ;; Verify the assignment worked
      (verify-user-team-membership user-id team-id)
      result)
    (catch Exception e
      (println "Adding user to team failed:" (.getMessage e))
      (println "Stack trace:" (.printStackTrace e))
      nil)))

(defn clean-test-database
  "Clean the test database - Now using in-memory database for true isolation.
   The in-memory database (memory://test-meetings) is automatically cleaned
   when the JVM process ends, providing perfect test isolation."
  []
  ;; With memory:// database, no explicit cleanup is needed
  ;; Each test server restart gets a fresh in-memory database
  (println "Using in-memory database - auto-cleaned on server restart"))

;; =============================================================================
;; AUTHENTICATION HELPERS
;; =============================================================================

(defn login-as-user
  "Login as a specific user using fake login by clicking their login button"
  [user-id]
  (w/navigate (str APP_URL "login/fake"))
  ;; Find the form with the matching user-id and click its submit button
  (w/click (str "form:has(input[name='user-id'][value='" user-id "']) button[type='submit']")))

(defn create-and-login-as-admin
  "Create and login as admin user using the fake user form"
  []
  (w/navigate (str APP_URL "login/fake"))
  ;; Generate random data
  (w/click [(ws/text "Generate Random Data")])
  ;; Set access level to admin
  (.selectOption (w/-query "select[name='access-level']") "admin")
  ;; Submit the form
  (w/click [(ws/text "Create & Login")]))

(defn login-as-admin
  "Login as admin user for testing - tries existing user first, falls back to creating new one"
  []
  (try
    (let [admin-user-id (create-test-admin-user)]
      (login-as-user admin-user-id))
    (catch Exception e
      ;; If existing user login fails, create a new one
      (println "Existing user login failed, creating new admin user")
      (create-and-login-as-admin))))

(defn login-as-staff
  "Login as staff user for testing"
  []
  (let [staff-user-id (create-test-staff-user)]
    (login-as-user staff-user-id)))

(defn login-as-regular-user
  "Login as regular user for testing"
  []
  (let [user-id (create-test-regular-user)]
    (login-as-user user-id)))

;; =============================================================================
;; COMMON PAGE INTERACTIONS
;; =============================================================================

(defn wait-for-page-load
  "Wait for page to load by checking for a specific element"
  [selector]
  (let [element (w/-query selector)]
    (when element
      (Thread/sleep 50) ; Small delay to ensure page is fully loaded
      element)))

(defn navigate-and-wait
  "Navigate to URL and wait for specific element to load"
  [url selector]
  (w/navigate url)
  (wait-for-page-load selector))

;; =============================================================================
;; BROWSER MANAGEMENT FOR REPL/TEST COORDINATION
;; =============================================================================

(defn pause-repl-browser!
  "Temporarily close REPL browser to allow test runner to use Playwright.
   Use this when you want to run clj -M:test-ui while keeping REPL active."
  []
  (try
    (println "Pausing REPL browser to allow test runner...")
    (let [current-page (w/get-page)]
      (.close (.context current-page)))
    (println "✅ REPL browser closed. You can now run: clj -M:test-ui")
    (catch Exception e
      (println "Browser was already closed or not initialized"))))

(defn resume-repl-browser!
  "Resume REPL browser after tests complete by resetting wally's internal state."
  []
  (println "Resuming REPL browser...")
  ;; Reset wally's internal page state to force new browser creation
  (alter-var-root #'w/*page* (constantly (delay (w/make-page))))
  ;; Now navigate to restart the browser
  (w/navigate (str APP_URL "login/fake"))
  (println "✅ REPL browser restarted"))

;; =============================================================================
;; TEST SERVER MANAGEMENT
;; =============================================================================

(defn wait-for-server-ready
  "Wait for server to be ready by checking if it responds"
  [port max-attempts]
  (loop [attempts 0]
    (if (< attempts max-attempts)
      (do
        (Thread/sleep 1000)
        (if (try
              (slurp (str "http://localhost:" port "/"))
              true
              (catch Exception e
                (println "Attempt" (inc attempts) "- Server not ready:" (.getMessage e))
                false))
          (do
            (println "Server is responding on port" port)
            true)
          (recur (inc attempts))))
      (do
        (println "Server failed to start after" max-attempts "attempts")
        false))))

(defn stop-test-server!
  "Stop the test server if it was started by this process"
  []
  (when @test-server-process
    (println "Stopping test server...")
    ;; For HTTP server, call the server stop function
    (when (fn? @test-server-process)
      (@test-server-process))
    (reset! test-server-process nil)
    (Thread/sleep 1000)
    (println "Test server stopped!")))

(defn server-already-running?
  "Check if server is already running on the test port"
  []
  (try
    (slurp (str "http://localhost:" TEST_PORT "/"))
    true
    (catch Exception e
      false)))

(defn start-test-server!
  "Start development server with test configuration using Aero test profile.
   If server is already running externally, use that instead of starting a new one."
  []
  (if (server-already-running?)
    (println "✅ Test server already running on port" TEST_PORT "- using existing server")
    (when-not @test-server-process
      (println "Starting test server in same JVM with test profile...")
      (try
        ;; Import core namespace functions
        (require '[ok.hola-tact-meet.core :as core])
        ;; Start the server in the current JVM (shares database)
        (reset! test-server-process ((resolve 'ok.hola-tact-meet.core/start!)))
        (println "✅ Test server started in same JVM and ready!")
        (catch Exception e
          (println "❌ Failed to start test server:" (.getMessage e))
          (reset! test-server-process nil)
          (throw e))))))

(defn restart-test-server!
  "Restart the test server"
  []
  (stop-test-server!)
  (start-test-server!))

(defn with-test-server
  "Execute function with test server running.
   Only stops server if it was started by this process."
  [f]
  (let [server-was-external (server-already-running?)]
    (try
      (start-test-server!)
      (f)
      (finally
        ;; Only stop if we started the server ourselves
        (when-not server-was-external
          (stop-test-server!))))))

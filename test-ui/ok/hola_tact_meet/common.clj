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
  []
  (let [test-admin {:email "test-admin@example.com"
                    :name "Test Admin"
                    :given-name "Test"
                    :family-name "Admin"
                    :access-level "admin"
                    :active true
                    :auth-provider "fake"}]
    (try
      (db/create-user test-admin)
      (catch Exception e
        ;; User might already exist, find existing user
        (println "Admin user already exists or creation failed:" (.getMessage e))
        (db/find-user-by-email "test-admin@example.com")))))

(defn create-test-staff-user
  "Create a test staff user in the database and return the user ID"
  []
  (let [test-staff {:email "test-staff@example.com"
                    :name "Test Staff"
                    :given-name "Test"
                    :family-name "Staff"
                    :access-level "staff"
                    :active true
                    :auth-provider "fake"}]
    (try
      (db/create-user test-staff)
      (catch Exception e
        (println "Staff user already exists or creation failed:" (.getMessage e))
        (db/find-user-by-email "test-staff@example.com")))))

(defn create-test-regular-user
  "Create a test regular user in the database and return the user ID"
  []
  (let [test-user {:email "test-user@example.com"
                   :name "Test User"
                   :given-name "Test"
                   :family-name "User"
                   :access-level "user"
                   :active true
                   :auth-provider "fake"}]
    (try
      (db/create-user test-user)
      (catch Exception e
        (println "Regular user already exists or creation failed:" (.getMessage e))
        (db/find-user-by-email "test-user@example.com")))))

(defn setup-test-users
  "Setup a full set of test users for comprehensive testing
   Returns a map with user IDs: {:admin id :staff id :user id}"
  []
  {:admin (create-test-admin-user)
   :staff (create-test-staff-user)
   :user (create-test-regular-user)})

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
    (.destroy @test-server-process)
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
      (println "Starting test server with Aero test profile...")
      (let [process (-> (ProcessBuilder. ["clj" "-M:test-server"])
                        (.directory (java.io.File. "."))
                        (.redirectOutput java.lang.ProcessBuilder$Redirect/INHERIT)
                        (.redirectError java.lang.ProcessBuilder$Redirect/INHERIT)
                        (.start))]
        (reset! test-server-process process)
        ;; Wait for server to be ready
        (println "Waiting for test server to be ready...")
        (if (wait-for-server-ready TEST_PORT 15)
          (println "✅ Test server started and ready!")
          (do
            (println "❌ Failed to start test server")
            (stop-test-server!)
            (throw (Exception. "Test server failed to start"))))))))

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

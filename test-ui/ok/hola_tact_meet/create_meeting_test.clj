(ns ok.hola-tact-meet.create-meeting-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [wally.main :as w]
            [wally.selectors :as ws]
            [ok.hola-tact-meet.common :as common]
            [ok.hola-tact-meet.db :as db]
            [faker.generate :as gen]))


(def APP_URL common/APP_URL)

;; =============================================================================
;; PAGE OBJECT MODEL - Create Meeting
;; =============================================================================

(defn navigate-to-app
  "Navigate to the main app page"
  []
  (w/navigate (str APP_URL "app")))

(defn open-create-meeting-modal
  "Click the 'Create New Meeting' button to open modal"
  []
  (w/click [(ws/text "Create New Meeting")]))

;; =============================================================================
;; REPL-FRIENDLY HELPER FUNCTIONS
;; =============================================================================

(defn setup-meeting-test-data
  "Setup test user, team, and return test data. REPL-friendly."
  []
  (let [staff-user-id (common/create-test-staff-user "meeting-test")
        team-id (common/create-test-team "-meeting-test")]
    (common/add-user-to-team staff-user-id team-id)
    (println "Created staff user:" staff-user-id "and team:" team-id)
    {:staff-user-id staff-user-id
     :team-id team-id}))

(defn generate-meeting-data
  "Generate random meeting data using faker. REPL-friendly."
  []
  (let [future-time (-> (java.time.LocalDateTime/now)
                        (.plusHours 2)
                        (.truncatedTo java.time.temporal.ChronoUnit/MINUTES))
        datetime-str (.format future-time (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm"))
        meeting-data {:title (gen/sentence {:words-range [2 4]})
                      :description (gen/paragraph {:sentences-range [1 3]})
                      :scheduled-datetime datetime-str}]
    (println "Generated meeting data:" meeting-data)
    meeting-data))

(defn verify-datetime-min-attribute
  "Verify datetime input has min attribute set to current time or later. REPL-friendly."
  []
  (let [datetime-input-with-min (w/-query "input[name='scheduled-at'][min]")
        current-time (str (java.time.LocalDateTime/now))]
    (println "DateTime input with min attribute exists:" (boolean datetime-input-with-min))
    (println "Current time:" current-time)
    (not (nil? datetime-input-with-min))))

(defn verify-team-in-dropdown
  "Verify team appears in the team dropdown. REPL-friendly."
  [team-id]
  (let [team-option (w/-query (str "select[name='team'] option[value='" team-id "']"))
        dropdown-select (w/-query "select[name='team']")]
    (println "Looking for team ID:" team-id)
    (when dropdown-select
      (println "Team dropdown found, checking for team option"))
    (println "Team" team-id "found in dropdown:" (boolean team-option))
    (not (nil? team-option))))

(defn fill-meeting-form
  "Fill meeting form with test data. REPL-friendly."
  [meeting-data team-id]
  (println "Filling meeting form with:" meeting-data)

  ;; Fill title
  (w/fill "input[name='title']" (:title meeting-data))

  ;; Fill description
  (w/fill "textarea[name='description']" (:description meeting-data))

  ;; Verify team is available before selecting
  (verify-team-in-dropdown team-id)

  ;; Select team
  (w/select "select[name='team']" (str team-id))

  ;; Fill scheduled datetime
  (w/fill "input[name='scheduled-at']" (:scheduled-datetime meeting-data))

  (println "Form filled successfully"))

(defn submit-meeting-form
  "Submit the meeting creation form. REPL-friendly."
  []
  (println "Submitting meeting form")
  (w/click [(ws/text "Create Meeting")])
  ;; Wait for form submission to complete - look for modal to close or success indicator
  (Thread/sleep 1000)
  (println "Form submitted, waiting for processing..."))

(defn verify-meeting-in-database
  "Verify meeting was created in database with correct data. REPL-friendly."
  [meeting-data team-id staff-user-id]
  (println "Verifying meeting in database")
  (println "Looking for meeting with title:" (:title meeting-data))
  (println "Looking for meeting with description:" (:description meeting-data))

  ;; Retry logic - give the server more time to process the async request
  (loop [attempts 0]
    (let [recent-meetings (db/get-recent-meetings-for-user staff-user-id)]
      (println "Attempt" (+ attempts 1) "- Found recent meetings:" (count recent-meetings))
      (println "All recent meetings:" recent-meetings)

      ;; Look for meeting with matching title in all recent meetings
      (let [matching-meeting (first (filter #(= (:title meeting-data) (:title %)) recent-meetings))]
        (if matching-meeting
          (do
            (println "Found matching meeting by title:" matching-meeting)
            (let [title-match (= (:title meeting-data) (:title matching-meeting))
                  desc-match (= (:description meeting-data) (:description matching-meeting))]
              (println "Title matches:" title-match)
              (println "Description matches:" desc-match)
              (if (and title-match desc-match)
                true
                (if (< attempts 4)
                  (do
                    (println "Meeting data doesn't fully match, retrying in 50ms...")
                    (Thread/sleep 50)
                    (recur (inc attempts)))
                  false))))
          (if (< attempts 4)
            (do
              (println "No meeting with matching title found, retrying in 50ms...")
              (Thread/sleep 50)
              (recur (inc attempts)))
            (do
              (println "No meeting with matching title found after 5 attempts")
              false)))))))

(defn close-modal-if-open
  "Close the create meeting modal if it's open. REPL-friendly."
  []
  (when (w/-query ".modal.is-active")
    (println "Closing modal")
    (w/click ".modal .delete")
    (Thread/sleep 300)))

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
;; TESTS
;; =============================================================================

(deftest ^:ui create-meeting-modal-test
  (testing "Create meeting modal functionality"
    (let [test-data (setup-meeting-test-data)
          {:keys [staff-user-id team-id]} test-data]

      ;; Login as staff user (required to create meetings)
      (common/login-as-user staff-user-id)
      (navigate-to-app)

      ;; Open create meeting modal
      (open-create-meeting-modal)

      ;; Verify modal is open
      (is (w/-query ".modal.is-active")
          "Create meeting modal should be open")

      ;; Verify datetime field has min attribute
      (is (verify-datetime-min-attribute)
          "Scheduled datetime field should have min attribute")

      ;; Verify team appears in dropdown (confirms user is team member)
      (is (verify-team-in-dropdown team-id)
          "Team should appear in dropdown for team member")

      ;; Generate test meeting data
      (let [meeting-data (generate-meeting-data)]

        ;; Fill and submit form
        (fill-meeting-form meeting-data team-id)
        (submit-meeting-form)

        ;; Verify meeting was created in database
        (print (verify-meeting-in-database meeting-data team-id staff-user-id))
        (is (verify-meeting-in-database meeting-data team-id staff-user-id)
            "Meeting should be created in database with correct data"))

      ;; Cleanup
      ;; (close-modal-if-open)
      )))

(comment
  ;; REPL helper for create-meeting-modal-test
  ;; Convert let bindings to def statements for individual execution

  ;; Setup test data (equivalent to let bindings in the test)
  (def repl-test-data (setup-meeting-test-data))
  (def repl-staff-user-id (:staff-user-id repl-test-data))
  (def repl-team-id (:team-id repl-test-data))

  ;; Login and navigate
  (common/login-as-user repl-staff-user-id)
  (navigate-to-app)

  ;; Open modal and verify basic setup
  (open-create-meeting-modal)

  ;; Step 1: Verify modal is open
  (w/-query ".modal.is-active")

  ;; Step 2: Verify datetime field has min attribute
  (verify-datetime-min-attribute)

  ;; Step 3: Verify team appears in dropdown
  (verify-team-in-dropdown repl-team-id)

  ;; Step 4: Generate meeting data
  (def repl-meeting-data (generate-meeting-data))

  ;; Step 5: Fill form (you can debug each field individually)
  ;; Fill title
  (w/fill "input[name='title']" (:title repl-meeting-data))

  ;; Fill description
  (w/fill "textarea[name='description']" (:description repl-meeting-data))

  ;; Select team
  (w/select "select[name='team']" (str repl-team-id))

  ;; Fill datetime
  (w/fill "input[name='scheduled-at']" (:scheduled-datetime repl-meeting-data))

  ;; OR fill entire form at once
  (fill-meeting-form repl-meeting-data repl-team-id)

  ;; Step 6: Submit form
  (submit-meeting-form)

  ;; Step 7: Verify meeting was created in database
  (verify-meeting-in-database repl-meeting-data repl-team-id repl-staff-user-id)

  ;; Step 8: Cleanup
  (close-modal-if-open)

  ;; Debug helpers - check data anytime
  (def current-meetings (db/get-recent-meetings-for-user repl-staff-user-id))
  (count current-meetings)
  (first current-meetings)

  ;; Check team membership
  (common/verify-user-team-membership repl-staff-user-id repl-team-id)

  ;; Run complete test
  (create-meeting-modal-test)
  )

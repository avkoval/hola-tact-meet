(ns ok.hola-tact-meet.meeting-page-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [wally.main :as w]
            [wally.selectors :as ws]
            [ok.hola-tact-meet.common :as common]
            [ok.hola-tact-meet.db :as db]
            [faker.generate :as gen]))

(def APP_URL common/APP_URL)

;; =============================================================================
;; PAGE OBJECT MODEL - Meeting Page
;; =============================================================================

(defn navigate-to-meeting
  "Navigate to the meeting page by ID"
  [meeting-id]
  (w/navigate (str APP_URL "meeting/" meeting-id "/main")))

(defn join-meeting
  "Join meeting from the meeting page"
  []
  (w/click [(ws/text "Join Meeting")]))

(defn verify-meeting-title-displayed
  "Verify that the meeting title is displayed in the breadcrumb"
  [expected-title]
  (let [breadcrumb-element (w/-query ".breadcrumb .is-active a")]
    (when breadcrumb-element
      (let [actual-title (.textContent breadcrumb-element)]
        (clojure.string/includes? actual-title expected-title)))))

(defn verify-meeting-description-displayed
  "Verify that the meeting description is displayed on the page"
  [expected-description]
  (let [description-element (or (w/-query [(ws/text expected-description)])
                                (w/-query ".meeting-description")
                                (w/-query ".description")
                                (w/-query "[data-testid='meeting-description']"))]
    (when description-element
      (let [actual-description (.textContent description-element)]
        (clojure.string/includes? actual-description expected-description)))))

(defn verify-meeting-screen-loaded
  "Verify that we are on the meeting screen by checking for meeting-specific elements"
  []
  (or (w/-query "#meeting-content")
      (w/-query ".panel-heading")
      (w/-query [(ws/text "Topics")])
      (w/-query ".breadcrumb .is-active")))

(defn add-topic-to-meeting
  "Add a topic to the meeting using the UI form"
  [topic-title]
  (println "Adding topic:" topic-title)
  
  ;; Find the topic input field and fill it
  (w/fill "input[name='new-topic'], #new-topic" topic-title)
  
  ;; Click the add topic button (the one with the plus icon)
  (w/click "button.is-primary .fa-plus")
  
  (println "Topic added via UI"))

(defn verify-topic-appears-on-screen
  "Verify that a topic with the given title appears on the screen"
  [topic-title]
  (let [topic-element (w/-query [(ws/text topic-title)])]
    (when topic-element
      (println "Found topic on screen:" topic-title)
      true)))

(defn verify-topic-in-database
  "Verify that a topic with the given title exists in the database for the meeting"
  [meeting-id topic-title]
  (let [topics (db/get-topics-for-meeting meeting-id)
        matching-topic (first (filter #(= (:title %) topic-title) topics))]
    (when matching-topic
      (println "Found topic in database:" topic-title)
      (println "Topic data:" matching-topic)
      true)))

(defn generate-topic-title
  "Generate a random topic title using faker"
  []
  (let [topic (str (gen/word {:capitalize true}) " " 
                   (gen/word) " " 
                   (gen/word))]
    (println "Generated topic title:" topic)
    topic))

;; =============================================================================
;; TEST SETUP HELPERS
;; =============================================================================

(defn find-existing-test-meeting
  "Find existing test meeting by title using db function"
  [meeting-title]
  (when-let [meeting-id (db/find-meeting-by-title meeting-title)]
    {:id meeting-id :title meeting-title}))

(defn setup-meeting-test-environment
  "Setup comprehensive test environment with users, team, and meeting.
   Reuses existing meeting if one with the same title already exists."
  []
  (println "Setting up meeting test environment...")
  
  ;; Create users with different access levels
  (let [admin-user-id (common/create-test-admin-user "-meeting-page")
        staff-user-id (common/create-test-staff-user "-meeting-page")
        regular-user-id (common/create-test-regular-user "-meeting-page")
        team-id (common/create-test-team "-meeting-page")]
    
    ;; Add all users to the team
    (common/add-user-to-team admin-user-id team-id)
    (common/add-user-to-team staff-user-id team-id)
    (common/add-user-to-team regular-user-id team-id)
    
    ;; Check for existing meeting first
    (let [meeting-title "Test Meeting for Page Testing"
          existing-meeting (find-existing-test-meeting meeting-title)]
      
      (if existing-meeting
        ;; Use existing meeting
        (do
          (println "Found existing meeting with ID:" (:id existing-meeting))
          {:admin-user-id admin-user-id
           :staff-user-id staff-user-id
           :regular-user-id regular-user-id
           :team-id team-id
           :meeting-id (:id existing-meeting)
           :meeting-data {:title meeting-title
                          :description "This is a test meeting created for UI testing purposes"}})
        
        ;; Create new meeting
        (let [meeting-data {:title meeting-title
                            :description "This is a test meeting created for UI testing purposes"
                            :team (str team-id)
                            :scheduled-at (-> (java.time.LocalDateTime/now)
                                              (.plusHours 1)
                                              (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm")))
                            :allow-topic-voting true
                            :sort-topics-by-votes false
                            :is-visible true}
              meeting-result (db/add-meeting! meeting-data admin-user-id)]
          
          (if (:success meeting-result)
            (let [meeting-id (:meeting-id meeting-result)]
              (println "Created new meeting with ID:" meeting-id)
              {:admin-user-id admin-user-id
               :staff-user-id staff-user-id
               :regular-user-id regular-user-id
               :team-id team-id
               :meeting-id meeting-id
               :meeting-data meeting-data})
            (throw (ex-info "Failed to create test meeting" meeting-result))))))))

(defn verify-meeting-data-in-db
  "Verify meeting exists in database and has correct data"
  [meeting-id expected-title expected-description]
  (let [meeting (db/get-meeting-by-id meeting-id)]
    (and meeting
         (= (:meeting/title meeting) expected-title)
         (= (:meeting/description meeting) expected-description))))

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

(deftest ^:ui meeting-page-basic-display-test
  (testing "Meeting page displays title and description correctly"
    (let [test-env (setup-meeting-test-environment)
          {:keys [regular-user-id meeting-id meeting-data]} test-env
          expected-title (:title meeting-data)
          expected-description (:description meeting-data)]
      
      (println "Testing meeting page with meeting ID:" meeting-id)
      (println "Expected title:" expected-title)
      (println "Expected description:" expected-description)
      
      ;; Login as regular user
      (common/login-as-user regular-user-id)
      
      ;; Navigate to meeting page
      (navigate-to-meeting meeting-id)
      
      ;; Verify we are on the meeting screen
      (is (verify-meeting-screen-loaded)
          "Should be redirected to meeting screen")
      
      ;; Verify meeting title is displayed
      (is (verify-meeting-title-displayed expected-title)
          "Meeting title should be displayed on the page")
      
      ;; Verify meeting description is displayed
      (is (verify-meeting-description-displayed expected-description)
          "Meeting description should be displayed on the page"))))

(comment
  ;; REPL helpers for meeting-page-basic-display-test
  
  ;; Setup test environment
  (def repl-basic-test-env (setup-meeting-test-environment))
  (def repl-basic-meeting-id (:meeting-id repl-basic-test-env))
  (def repl-basic-regular-user-id (:regular-user-id repl-basic-test-env))
  (def repl-basic-meeting-data (:meeting-data repl-basic-test-env))
  
  repl-basic-meeting-data
  ;; Login and navigate
  (common/login-as-user repl-basic-regular-user-id)
  (navigate-to-meeting repl-basic-meeting-id)
  
  ;; Debug current page
  (println "Current URL:" (.url (w/get-page)))
  (println "Page title:" (.title (w/get-page)))
  
  ;; Test individual verification functions
  (verify-meeting-screen-loaded)
  (verify-meeting-title-displayed (:title repl-basic-meeting-data))
  (verify-meeting-description-displayed (:description repl-basic-meeting-data))
  
  ;; Check database
  (verify-meeting-data-in-db repl-basic-meeting-id 
                             (:title repl-basic-meeting-data)
                             (:description repl-basic-meeting-data))
  
  ;; Run the test
  (meeting-page-basic-display-test)
  )

(deftest ^:ui meeting-page-join-functionality-test
  (testing "Meeting page join functionality works correctly"
    (let [test-env (setup-meeting-test-environment)
          {:keys [staff-user-id meeting-id]} test-env]
      
      ;; Login as staff user
      (common/login-as-user staff-user-id)
      
      ;; Navigate to meeting page
      (navigate-to-meeting meeting-id)
      
      ;; Wait for page to load
      
      ;; Verify we can join the meeting
      (is (verify-meeting-screen-loaded)
          "Should be on meeting screen")
      
      ;; Check if join button exists and click it if present
      (when (w/-query [(ws/text "Join Meeting")])
        (join-meeting)
        
        
        ;; After joining, verify we're still on meeting page
        (is (verify-meeting-screen-loaded)
            "Should remain on meeting screen after joining")))))

(comment
  ;; REPL helpers for meeting-page-join-functionality-test
  
  ;; Setup test environment
  (def repl-join-test-env (setup-meeting-test-environment))
  (def repl-join-meeting-id (:meeting-id repl-join-test-env))
  (def repl-join-staff-user-id (:staff-user-id repl-join-test-env))
  
  ;; Login and navigate
  (common/login-as-user repl-join-staff-user-id)
  (navigate-to-meeting repl-join-meeting-id)
  
  ;; Check if on meeting screen
  (verify-meeting-screen-loaded)
  
  ;; Check for join button
  (w/-query [(ws/text "Join Meeting")])
  
  ;; Join meeting if button exists
  (when (w/-query [(ws/text "Join Meeting")])
    (join-meeting))
  
  ;; Verify still on meeting screen
  (verify-meeting-screen-loaded)
  
  ;; Run the test
  (meeting-page-join-functionality-test)
  )

(deftest ^:ui meeting-page-add-topic-test
  (testing "Adding topic to meeting works correctly"
    (let [test-env (setup-meeting-test-environment)
          {:keys [regular-user-id meeting-id]} test-env
          topic-title (generate-topic-title)]
      
      (println "Testing topic addition with meeting ID:" meeting-id)
      (println "Generated topic title:" topic-title)
      
      ;; Login as regular user
      (common/login-as-user regular-user-id)
      
      ;; Navigate to meeting page
      (navigate-to-meeting meeting-id)
      
      ;; Verify we are on the meeting screen
      (is (verify-meeting-screen-loaded)
          "Should be on meeting screen")
      
      ;; Join the meeting first (required to add topics)
      (when (w/-query [(ws/text "Join Meeting")])
        (join-meeting)
        )
      
      ;; Add topic using the UI
      (add-topic-to-meeting topic-title)
      
     
      ;; Verify topic appears on screen
      (is (verify-topic-appears-on-screen topic-title)
          "Topic should appear on the screen after being added")
      
      ;; Verify topic appears in database
      (is (verify-topic-in-database meeting-id topic-title)
          "Topic should be saved in the database"))))

(comment
  ;; REPL helpers for meeting-page-add-topic-test
  
  ;; Setup test environment
  (def repl-topic-test-env (setup-meeting-test-environment))
  (def repl-topic-meeting-id (:meeting-id repl-topic-test-env))
  (def repl-topic-regular-user-id (:regular-user-id repl-topic-test-env))
  
  ;; Generate random topic
  (def repl-topic-title (generate-topic-title))
  
  ;; Login and navigate
  (common/login-as-user repl-topic-regular-user-id)
  (navigate-to-meeting repl-topic-meeting-id)
  
  ;; Check if on meeting screen
  (verify-meeting-screen-loaded)
  
  ;; Join meeting first (if needed)
  (when (w/-query [(ws/text "Join Meeting")])
    (join-meeting))
  
  ;; Add topic via UI
  (add-topic-to-meeting repl-topic-title)
  (add-topic-to-meeting  "hello there2!")
  
  ;; Verify topic on screen
  (verify-topic-appears-on-screen repl-topic-title)
  
  ;; Verify topic in database
  (verify-topic-in-database repl-topic-meeting-id repl-topic-title)
  
  ;; Check all topics for this meeting
  (db/get-topics-for-meeting repl-topic-meeting-id)
  
  ;; Run the test
  (meeting-page-add-topic-test)
  )

(comment
  ;; General REPL helpers for meeting page testing
  
  ;; Run all meeting page tests
  (meeting-page-basic-display-test)
  (meeting-page-join-functionality-test)
  (meeting-page-add-topic-test)
  
  ;; Quick setup for manual testing
  (def repl-env (setup-meeting-test-environment))
  (common/login-as-user (:regular-user-id repl-env))
  (navigate-to-meeting (:meeting-id repl-env))
  
  ;; Debug current page state
  (println "Current URL:" (.url (w/get-page)))
  (verify-meeting-screen-loaded)
  )

(ns ok.hola-tact-meet.views
  (:require
   [ring.util.response :as response]
   [selmer.parser :refer [render-file] :as selmer]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
   [ok.hola-tact-meet.utils :as utils]
   [ok.hola-tact-meet.db :as db]
   [ok.hola-tact-meet.validation :as v]
   [clojure.java.io]
   [ok.oauth2.utils :refer [get-oauth-config]]
   [clojure.tools.logging :as log]
   [clojure.walk :refer [keywordize-keys]]
   [faker.generate :as gen]
   [datomic.client.api :as d]
   [clojure.pprint :refer [pprint]]
   [malli.core :as m]
   )
  (:gen-class))



(defn home [{session :session :as request}]
  (let [oauth2-config (get-oauth-config (utils/app-config))
        remote-addr (:remote-addr request)
        dev_mode (utils/localhost? remote-addr)
        ]

    (log/info "Home page accessed from" remote-addr "dev_mode: " dev_mode)
    (if (get-in session [:userinfo :logged-in])
      (response/redirect "/app")
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (render-file "templates/home.html" {:google_client_id (get-in oauth2-config [:google :client-id])
                                                 :google_launch_uri (get-in oauth2-config [:google :launch-uri])
                                                 :google_login_uri "/google-login"
                                                 :host (get-in request [:headers "host"])
                                                 :dev_mode dev_mode})})))

(defn app-main [{session :session}]
  (log/info "Main app page accessed")
  (let [userinfo (:userinfo session)
        user-email (:email userinfo)
        user-id (when user-email (db/find-user-by-email user-email))
        recent-meetings (if user-id (db/get-recent-meetings-for-user user-id) [])
        active-meetings (if user-id (db/get-active-meetings-for-user user-id) [])]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/main.html" {:userinfo userinfo
                                               :recent-meetings recent-meetings
                                               :active-meetings active-meetings})}))



(defn meeting-main [{session :session}]
  (log/info "Main meeting screen")
  (let [userinfo (:userinfo session)
        user-email (:email userinfo)
        user-id (when user-email (db/find-user-by-email user-email))
        ]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/meeting.html" {:userinfo userinfo
                                                  })}))

(defn join-meeting
  "Join meeting by checking permissions and saving join time"
  [{session :session :as request}]
  (log/info "Join meeting")
  (let [userinfo (:userinfo session)
        user-email (:email userinfo)
        user-id (when user-email (db/find-user-by-email user-email))
        meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))]
    (cond
      (not user-id)
      {:status 401
       :body "User not found"}

      (not (db/user-has-active-meeting? user-id meeting-id))
      {:status 403
       :body "You don't have access to this meeting"}

      :else
      (let [result (db/add-participant! user-id meeting-id)]
        (if (:success result)
          {:status 302
           :headers {"Location" (str "/meeting/" meeting-id "/main")}}
          {:status 500
           :body (:error result)})))))


(defn admin-manage-users [{session :session}]
  (let [users (db/get-all-users)
        statistics (db/get-user-statistics)
        userinfo (:userinfo session)]
    (log/info "admin-manage-users accessed")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/users.html" (merge {:users users :userinfo userinfo} statistics))}))

(defn admin-update-user-access-level [request]
  (->sse-response
   request
   {on-open
    (fn [sse]
      ;; (pprint request)
      (let [user_id (get-in request [:query-params "user_id"])
            params (utils/datastar-query request)
            new_access_level (get params (keyword (str "accessLevel" user_id)))]
        (log/debug "Params:" params)
        (log/debug "New access level:" new_access_level)
        (when (and user_id new_access_level)
          (d/transact (db/get-conn) {:tx-data [{:db/id (Long/parseLong user_id)
                                                :user/access-level new_access_level}]}))
        (d*/with-open-sse sse
          (d*/patch-elements! sse (render-file "templates/users_list.html" {:users (db/get-all-users)})))))}))


(defn admin-toggle-user [request]
  (->sse-response
   request
   {on-open
    (fn [sse]
      (let [user_id (get-in request [:query-params "user_id"])
            ]
        (log/debug "Toggle user ID:" user_id)
        (when user_id
          (let [user-id-long (Long/parseLong user_id)
                new-active-status (db/toggle-user-active! user-id-long)]
            (log/info "User" user_id "active status toggled to" new-active-status)))
        (d*/with-open-sse sse
          (d*/patch-elements! sse (render-file "templates/users_list.html" {:users (db/get-all-users)}))))
      )}))


(defn admin-refresh-users-list [request]
  (->sse-response
   request
   {on-open
    (fn [sse]
      (d*/with-open-sse sse
          (d*/patch-elements! sse (render-file "templates/users_list.html" {:users (db/get-all-users)})))
      )}))

(defn- prepare-teams-with-membership
  "Prepare teams data with user membership flags for template"
  [user-id-long]
  (let [user-data (when user-id-long (db/get-user-by-id user-id-long))
        all-teams (db/get-all-teams)
        staff-admin-users (db/get-staff-admin-users)
        user-teams (or (:user/teams user-data) [])
        user-team-ids (set (map :db/id user-teams))
        teams-with-membership (mapv (fn [team]
                                      (assoc team :user-is-member (contains? user-team-ids (:id team))))
                                    all-teams)]
    {:user user-data
     :user-id user-id-long
     :all-teams teams-with-membership
     :staff-admin-users staff-admin-users
     :user-teams user-teams
     :user-team-ids user-team-ids}))

(defn admin-user-teams [request]
  (->sse-response
   request
   {on-open
    (fn [sse]
      (let [user_id (get-in request [:path-params :user])
            user-id-long (when user_id (Long/parseLong user_id))
            template-data (prepare-teams-with-membership user-id-long)]
        (log/debug "Loading teams for user:" user_id)
        (d*/with-open-sse sse
          (d*/patch-elements! sse (render-file "templates/user_teams_management_modal.html" template-data))
          ))
      )}))


(defn join-meeting-modal [{session :session :as request}]
  (->sse-response
   request
   {on-open
    (fn [sse]
      (let [userinfo (:userinfo session)
        user-email (:email userinfo)
        user-id (when user-email (db/find-user-by-email user-email))
        active-meetings (if user-id (db/get-active-meetings-for-user user-id) [])]
        (log/info active-meetings)
        (log/debug "Join meeting for:" user-id)
        (d*/with-open-sse sse
          (d*/patch-elements! sse (render-file "templates/join_meeting_modal.html" {:active-meetings active-meetings}))
          (d*/patch-signals! sse "{joinMeetingModalOpen: true}")
          ))
      )}))

(defn staff-create-meeting-popup [request]
  (->sse-response
   request
   {on-open
    (fn [sse]
      (let [user-id (get-in request [:session :userinfo :user-id])
            user-data (db/get-user-by-id user-id)
            user-teams (:user/teams user-data)]
        (log/info (str "Create meeting popup requested by: " (:user/email user-data)))
        (d*/with-open-sse sse
          (d*/patch-elements! sse (render-file "templates/create_meeting_modal.html" {:teams user-teams}))
          (d*/patch-signals! sse "{createMeetingModalOpen: true}")
          ))
      )}))


(defn staff-create-meeting-save [request]
  (->sse-response
   request
   {on-open
    (fn [sse]
      (let [user-id (get-in request [:session :userinfo :user-id])
            user-data (db/get-user-by-id user-id)
            form-params (keywordize-keys (:form-params request))
            user-teams (:user/teams user-data)]
        (log/info (str "Create meeting save requested by: " (:user/email user-data)))
        (pprint form-params)

        ;; Validate meeting data
        (let [meeting-data {:title (:title form-params)
                            :description (:description form-params)
                            :team (:team form-params)
                            :scheduled-at (:scheduled-at form-params)
                            :join-url (:join-url form-params)
                            :allow-topic-voting (= (:allow-topic-voting form-params) "on")
                            :sort-topics-by-votes (= (:sort-topics-by-votes form-params) "on")
                            :is-visible (= (:is-visible form-params) "on")}]

          (d*/with-open-sse sse
            (if (m/validate v/MeetingData meeting-data)
              ;; Check if user is member of selected team
              (let [team-id (Long/parseLong (:team meeting-data))]
                (if (db/user-is-team-member? user-id team-id)
                  ;; Create meeting
                  (let [create-result (db/add-meeting! meeting-data user-id)]
                    (if (:success create-result)
                      (do
                        (log/info "Meeting created successfully:" (:meeting-id create-result))
                        (d*/patch-elements! sse "<div id=\"createMeetingModal\"></div>")
                        (d*/patch-elements!
                         sse
                         (render-file "templates/notifications.html"
                                      {:notifications [{:level "info"
                                                        :text "New meeting created successfully" }]})))
                      (do
                        (log/warn "Failed to create meeting:" (:error create-result))
                        (d*/patch-elements! sse (render-file "templates/create_meeting_modal.html"
                                                             {:teams user-teams
                                                              :error-message (:error create-result)})))))
                  ;; User is not a member of the team
                  (do
                    (log/warn "User" user-id "is not a member of team" team-id)
                    (d*/patch-elements! sse (render-file "templates/create_meeting_modal.html"
                                                         {:teams user-teams
                                                          :error-message "You are not a member of the selected team"})))))
              ;; Invalid meeting data
              (do
                (log/warn "Invalid meeting data:" meeting-data)
                (d*/patch-elements! sse (render-file "templates/create_meeting_modal.html"
                                                     {:teams user-teams
                                                      :error-message "Invalid meeting data. Please check all fields."}))))))
        )
      )}))


(defn admin-project-settings [request]
  (->sse-response
   request
   {on-open
    (fn [sse]
      (let [settings {:allowed-domains ["example.com" "company.org"]
                      :google-oauth-enabled true
                      :google-client-id "your-google-client-id"
                      :google-client-secret "***hidden***"}]
        (log/info "Loading project settings")
        (d*/with-open-sse sse
          (d*/patch-elements! sse (render-file "templates/project_settings_modal.html" settings))))
      )}))


(defn admin-user-teams-change [request]
  (->sse-response
   request
   {on-open
    (fn [sse]
      (let [user_id (get-in request [:path-params :user])
            user-id-long (when user_id (Long/parseLong user_id))
            form-params (:form-params request)
            team-ids-str (get form-params "user-teams")
            team-ids (when team-ids-str
                      (if (string? team-ids-str)
                        [(Long/parseLong team-ids-str)]
                        (mapv #(Long/parseLong %) team-ids-str)))]
        (log/info "User ID:" user_id)
        (log/info "Form params:" form-params)
        (log/info "Team IDs:" team-ids)

        ;; Update user's team memberships (handle both selection and deselection)
        (when user-id-long
          (let [result (db/update-user-teams! user-id-long (or team-ids []))]
            (log/info "Update result:" result)))

        (d*/with-open-sse sse
          ;; Return updated modal
          (d*/patch-elements! sse (render-file "templates/user_teams_management_modal.html" (prepare-teams-with-membership user-id-long)))
          )
          )

      )}))



(defn admin-user-teams-add [request]
  (->sse-response
   request
   {on-open
    (fn [sse]
      (let [user_id (get-in request [:path-params :user])
            user-id-long (when user_id (Long/parseLong user_id))
            form-params (:form-params request)

            ;; Parse form data
            team-name (get form-params "name")
            team-description (get form-params "description" "")
            team-managers-str (get form-params "managers" [])
            team-managers (if (vector? team-managers-str)
                           (mapv #(Long/parseLong %) team-managers-str)
                           (if (string? team-managers-str)
                             [(Long/parseLong team-managers-str)]
                             []))

            ;; Create team data structure
            team-data {:name team-name
                      :description team-description
                      :managers team-managers}]

        (log/debug "Adding team for user:" user_id)
        (log/debug "Form params:" form-params)
        (log/debug "Team data:" team-data)

        ;; Validate and create team
        ;;; XXX fix this code I don't like repetition here
        (if (m/validate v/TeamData team-data)
          (let [create-result (db/create-team-with-validation! team-data)]
            (if (:success create-result)
              (do
                (log/info "Team created successfully:" (:team-id create-result))
                (let [template-data (assoc (prepare-teams-with-membership user-id-long)
                                           :success-message "Team created successfully!")]
                  (d*/with-open-sse sse
                    (d*/patch-elements! sse (render-file "templates/user_teams_management_modal.html" template-data)))))
              (do
                (log/warn "Failed to create team:" (:error create-result))
                (let [template-data (assoc (prepare-teams-with-membership user-id-long)
                                           :error-message (:error create-result))]
                  (d*/with-open-sse sse
                    (d*/patch-elements! sse (render-file "templates/user_teams_management_modal.html" template-data)))))))
          (let [validation-errors (m/explain v/TeamData team-data)
                template-data (assoc (prepare-teams-with-membership user-id-long)
                                     :validation-errors validation-errors
                                     :error-message "Invalid team data. Please check your input.")]
            (log/warn "Invalid team data:" team-data)
            (log/warn "Validation errors:" validation-errors)
            (d*/with-open-sse sse
              (d*/patch-elements! sse (render-file "templates/user_teams_management_modal.html" template-data))))
          )

        ))}))


(defn change-css-theme [{session :session :as request}]
  (->sse-response
   request
   {on-open
    (fn [sse]
      ;; (pprint request)
      (let [; user_id (get-in request [:query-params "user_id"])
            params (utils/datastar-query request)
            ;new_access_level (get params (keyword (str "accessLevel" user_id)))
            ]
        (log/debug "Theme params:" params)

        ;; TODO I need to understand how to change session from here

        ;; (when (and user_id new_access_level)
        ;;   (d/transact (db/get-conn) {:tx-data [{:db/id (Long/parseLong user_id)
        ;;                                         :user/access-level new_access_level}]}))
        ;; (d*/with-open-sse sse
        ;;   (d*/patch-elements! sse (render-file "templates/users_list.html" {:users (db/get-all-users)})))


        ))}))


(defn test-session [{session :session}]
  (let [count   (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response/response (str "You accessed this page " count " times."))
        (assoc :session session))))

(defn google-login [{session :session :as request}]
  (let [login-count   (:login-count session 0)
        session (assoc session :count (inc login-count))]
    (log/info "Google login attempt from" (:remote-addr request))
    (-> (response/response (str "Logged IN. " login-count " times."))
        (assoc :session session))))


(defn login-user-by-id
  "Log in user by their database ID"
  [{session :session} user-id userinfo]

  (let [updated-session (assoc session :userinfo (assoc userinfo
                                                        :logged-in true
                                                        :user-id user-id))]
    (db/update-last-login! user-id)
    (log/info "User logged in:" (:email userinfo) "ID:" user-id)
    (-> (response/redirect "/app")
        (assoc :session updated-session))))

(defn login
  "Main login function that creates user and logs them in"
  [request userinfo]
  (let [user-id (db/create-user userinfo)]
    (log/info "User created:" (:email userinfo) "ID:" user-id)
    (login-user-by-id request user-id userinfo)))

(defn fake-login-page [request]
  (let [users (db/get-all-users)]
    (log/info "Fake login page accessed from" (:remote-addr request))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/fake.html" {:users users})}))

(defn fake-login-existing [request]
  (let [user-id-str (get-in request [:params "user-id"])
        user-id (Long/parseLong user-id-str)
        user-data (db/get-user-by-id user-id)
        userinfo {:name (:user/name user-data)
                  :email (:user/email user-data)
                  :family-name (:user/family-name user-data)
                  :given-name (:user/given-name user-data)
                  :picture (:user/picture user-data)
                  :auth-provider "fake"
                  :access-level (:user/access-level user-data)}]
    (log/info "Fake login as existing user:" (:email userinfo))
    (login-user-by-id request user-id userinfo)))

(defn fake-login-new [request]
  (let [params (:params request)
        fake-userinfo {:name (get params "name")
                       :email (get params "email")
                       :family-name (get params "family-name")
                       :given-name (get params "given-name")
                       :picture ""
                       :auth-provider "fake"
                       :access-level (get params "access-level")}]
    (log/info "Creating new fake user:" (:email fake-userinfo))
    (login request fake-userinfo)))


(defn fake-user-data []
  {:userinfo
   {:name (gen/word)
    :email (utils/gen-email)
    :family-name (gen/word)
    :given-name (gen/word)
    :picture ""
    :auth-provider "fake"
    :access-level (rand-nth ["admin" "user" "staff"])}}
  )


(defn fake-generate-random-data [request]
  (->sse-response request
                  {on-open
                   (fn [sse]
                     (d*/with-open-sse sse
                       (d*/patch-elements! sse
                        (render-file "templates/fake-user-form.html" (fake-user-data)))
                       ))}))


(defn logout [_]
  (-> (response/redirect "/")
      (assoc :session {})))

(ns ok.hola-tact-meet.views
  (:require
   [ring.util.response :as response]
   [selmer.parser :refer [render-file] :as selmer]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response] :as hk-gen]
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
   [clojure.data.json :as json]
   [clj-http.client :as http]
   [clojure.string]
   [buddy.sign.jwt :as jwt]
   [buddy.core.keys :as keys]
   )
  (:gen-class))

;; Atom to store timer data with meeting-id as key and timer-until as data
(defonce meeting-timers (atom {}))

(defn parse-timer-string
  "Parse timer string like '00:20', '0:20', or '20' and return total seconds"
  [timer-str]
  (try
    (if (and timer-str (not (clojure.string/blank? timer-str)))
      (let [parts (clojure.string/split timer-str #":")]
        (cond
          (= (count parts) 2) (+ (* (Integer/parseInt (first parts)) 60)
                                 (Integer/parseInt (second parts)))
          (= (count parts) 1) (Integer/parseInt (first parts))
          :else 0))
      0)
    (catch Exception _
      0)))

(defn set-meeting-timer!
  "Set timer for a meeting. timer-str can be '00:20', '0:20', or '20'"
  [meeting-id timer-str]
  (let [seconds (parse-timer-string timer-str)
        timer-until (+ (System/currentTimeMillis) (* seconds 1000))]
    (swap! meeting-timers assoc meeting-id timer-until)
    timer-until))

(defn get-timer-seconds-remaining
  "Get the number of seconds remaining for a meeting timer"
  [meeting-id]
  (if-let [timer-until (get @meeting-timers meeting-id)]
    (max 0 (int (/ (- timer-until (System/currentTimeMillis)) 1000)))
    0))

(defn home [{session :session :as request}]
  (let [oauth2-config (get-oauth-config (utils/app-config))
        remote-addr (:remote-addr request)
        dev_mode (utils/localhost? remote-addr)
        ]

    (log/info "Home page accessed from" remote-addr "dev_mode: " dev_mode)
    (pprint session)
    (if (get-in session [:userinfo :logged-in])
      (response/redirect "/app")
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (render-file "templates/home.html" {:google_client_id (get-in oauth2-config [:google :client-id])
                                                 :google_launch_uri (get-in oauth2-config [:google :launch-uri])
                                                 :google_login_uri "/google-login"
                                                 :host (get-in request [:headers "host"])
                                                 :dev_mode dev_mode})})))

(defn app-main-data [{session :session}]
  (let [userinfo (:userinfo session)
        user-id (:user-id userinfo)
        recent-meetings (if user-id (db/get-recent-meetings-for-user user-id) [])
        active-meetings (if user-id (db/get-active-meetings-for-user user-id) [])
        statistics (if user-id (db/get-dashboard-statistics user-id) {})
        ]
    {:userinfo userinfo
     :recent-meetings recent-meetings
     :recent-meetings-count (count recent-meetings)
     :active-meetings active-meetings
     :meetings-count (count active-meetings)
     :statistics statistics})
  )


(defn app-main [request]
  (log/info "Main app page accessed")
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (render-file "templates/main.html" (app-main-data request))})


(defn get-topics-for-meeting [meeting-id user-id]
  (->> (db/get-topics-for-meeting meeting-id)
       (mapv (fn [topic] (assoc topic :user-vote (db/get-user-vote-for-topic user-id (:id topic)))))
       (sort-by (juxt (comp - :vote-score) (comp - #(.getTime (:created-at %)))))))

(defn get-userinfo-by-user-id
  [user-id]
  (let [user-details (db/get-user-by-id user-id)
        session-userinfo {:email (:user/email user-details)
                          :name (:user/name user-details)
                          :user-id (:user-id user-details)
                          :given-name (:user/given-name user-details)
                          :family-name (:user/family-name user-details)
                          :picture (:user/picture user-details)
                          :logged-in true
                          :auth-provider (:user/auth-provider user-details)
                          :access-level (:user/access-level user-details)}
        ]
    session-userinfo)
)


(defn get-meeting-screen-data [meeting-id user-id]
  (let [meeting-data (db/get-meeting-by-id meeting-id)
        topics-with-votes (get-topics-for-meeting meeting-id user-id)
        current-topic (:meeting/current-topic meeting-data)
        can-change-meeting (db/user-can-change-meeting? user-id meeting-id)
        actions (db/get-actions-for-meeting meeting-id)
        team-members (db/get-team-members-for-meeting meeting-id)
        userinfo (get-userinfo-by-user-id user-id)
        ]
    {:meeting-id meeting-id
     :meeting meeting-data
     :topics topics-with-votes
     :current-topic current-topic
     :can-change-meeting can-change-meeting
     :current-user-id user-id
     :actions actions
     :userinfo userinfo
     :countdown (get-timer-seconds-remaining meeting-id)
     :team-members team-members}))

(defn render-topics [meeting-id user-id]
  (render-file "templates/topics-list.html" (get-meeting-screen-data meeting-id user-id)))

(defn render-meeting-body [render-full-body meeting-id user-id]
  (render-file (if render-full-body "templates/meeting.html" "templates/meeting-content.html")
               (get-meeting-screen-data meeting-id user-id)))

(defn meeting-main [{session :session :as request}]
  (log/info "Main meeting screen")
  (let [meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
        userinfo (:userinfo session)
        user-id (:user-id userinfo)
        ]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-meeting-body true meeting-id user-id)}))


(defn fetch-google-userinfo
  "Fetch user information from Google API using bearer token"
  [access-token]
  (try
    (let [response (http/get "https://www.googleapis.com/oauth2/v2/userinfo"
                            {:headers {"Authorization" (str "Bearer " access-token)}
                             :as :json})]
      (if (= 200 (:status response))
        {:success true :userinfo (:body response)}
        {:success false :error (str "Google API error: " (:status response))}))
    (catch Exception e
      (log/error "Error fetching Google userinfo:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn assign-user-to-auto-teams!
  "Assign user to teams based on email domain matching"
  [user-id email]
  (let [auto-teams (db/find-teams-for-email-domain email)]
    (when (seq auto-teams)
      (log/info (str "Auto-assigning user " user-id " to teams " auto-teams " based on email domain"))
      (db/add-user-to-teams! user-id auto-teams))))

(defn create-or-login-user
  "Create new user or login existing user based on Google userinfo"
  [google-userinfo]
  (try
    (log/info "Google userinfo received:" google-userinfo)
    (log/info "Google given_name:" (:given_name google-userinfo))
    (log/info "Google family_name:" (:family_name google-userinfo))
    (let [email (:email google-userinfo)
          existing-user-id (db/find-user-by-email email)]
      (if existing-user-id
        ;; User exists, update last login and return user info
        (do
          (db/update-last-login! existing-user-id)
          (log/info "User logged in:" email)
          {:success true :user-id existing-user-id :action :login})
        ;; User doesn't exist, create new user
        (let [constructed-name (or (:name google-userinfo)
                                  (let [given (:given_name google-userinfo)
                                        family (:family_name google-userinfo)]
                                    (if (and given family)
                                      (str given " " family)
                                      (or given family "Unknown User"))))
              user-data {:name constructed-name
                        :email email
                        :family-name (:family_name google-userinfo)
                        :given-name (:given_name google-userinfo)
                        :picture (:picture google-userinfo)
                        :auth-provider "google"
                        :active true}]
          (log/info "Creating user with data:" user-data)
          (let [new-user-id (db/create-user user-data)]
            (log/info "New user created:" email)
            (assign-user-to-auto-teams! new-user-id email)
            {:success true :user-id new-user-id :action :register}))))
    (catch Exception e
      (log/error "Error in create-or-login-user:" (.getMessage e))
      {:success false :error (.getMessage e)})))


(defn app-landing [request]
  (log/info "App landing screen (after oauth2)")
  (let [access-token (get-in request [:session :ring.middleware.oauth2/access-tokens :google :token])]
    (if access-token
      ;; Fetch user info from Google
      (let [userinfo-result (fetch-google-userinfo access-token)]
        (println userinfo-result)
        (if (:success userinfo-result)
          ;; Create or login user
          (let [user-result (create-or-login-user (:userinfo userinfo-result))]
            (if (:success user-result)
              ;; Get user details and update session
              (let [
                    session-userinfo (get-userinfo-by-user-id (:user-id user-result))
                    updated-session (assoc (:session request) :userinfo session-userinfo)]
                (pprint updated-session)
                {:status 302
                 :headers {"Location" "/app"}
                 :session updated-session})
              ;; User creation/login failed
              {:status 500
               :headers {"Content-Type" "text/html"}
               :body (str "Authentication failed: " (:error user-result))}))
          ;; Google userinfo fetch failed
          {:status 500
           :headers {"Content-Type" "text/html"}
           :body (str "Failed to fetch user info: " (:error userinfo-result))}))
      ;; No access token
      {:status 400
       :headers {"Content-Type" "text/html"}
       :body "No access token found"})))

; Lets collect all meeting participants open SSE generators into
; atom, to be able to broadcast updates
(def !meeting-screen-sse-gens (atom #{}))
(defn meeting-screen-sse-gen-data [{session :session :as request} sse-gen]
  (let [meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
        userinfo (:userinfo session)
        user-id (:user-id userinfo)]
    [sse-gen meeting-id user-id]
    )
  )

(defn meeting-main-refresh-content-watcher
  "This SSE connection will stay open and will be used to broadcast updates"
  [request]
  (->sse-response
   request
   {:headers {"X-Accel-Buffering" "no"}
    hk-gen/on-open
    (fn [sse-gen]
      (log/info "meeting-main-refresh-content-watcher established hk-gen/on-open" sse-gen)
      (swap! !meeting-screen-sse-gens conj (meeting-screen-sse-gen-data request sse-gen))
      )

    hk-gen/on-close
    (fn [sse-gen status]
      (log/info "meeting-main-refresh-content-watcher hk-gen/on-close:" status)
      (swap! !meeting-screen-sse-gens disj (meeting-screen-sse-gen-data request sse-gen)))}))

(defn broadcast-meeting-page-update! [func meeting-id func-args add-user-and-meeting-args?]
  (doseq [[c gen-meeting-id gen-user-id] @!meeting-screen-sse-gens]
    (when (= gen-meeting-id meeting-id)
      (let [args (if add-user-and-meeting-args? (concat func-args [gen-meeting-id gen-user-id]) func-args)
            elements (apply func args)]
        (d*/patch-elements! c elements)))))

(defn broadcast-meeting-page-signals! [meeting-id signals exclude-user]
  (doseq [[c gen-meeting-id gen-user-id] @!meeting-screen-sse-gens]
    (when (= gen-meeting-id meeting-id)
      (if exclude-user (when-not (= gen-user-id exclude-user) (d*/patch-signals! c signals))
          (d*/patch-signals! c signals)))))

(defn broadcast-execute-script! [script]
  (doseq [[c _ _] @!meeting-screen-sse-gens]
    (d*/execute-script! c script)))

(defn join-meeting
  "Join meeting by checking permissions and saving join time"
  [{session :session :as request}]
  (log/info "Join meeting")
  (let [userinfo (:userinfo session)
        user-id (:user-id userinfo)
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

(defn meeting-add-topic [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            user-id (get-in request [:session :userinfo :user-id])
            new-topic (get-in request [:form-params "new-topic"])]
        (log/info "Adding new topic:" new-topic "to meeting:" meeting-id "by user:" user-id)

        (if (and new-topic user-id meeting-id
                 (not (clojure.string/blank? new-topic))
                 (<= (count new-topic) 250))
          ;; Add topic to database
          (let [result (db/add-topic! meeting-id user-id (clojure.string/trim new-topic))]
            (if (:success result)
              (do
                (d*/patch-elements! sse (render-file "templates/add-new-topic.html" {}))
                (broadcast-meeting-page-update! render-topics meeting-id [] true))
              (do
                (log/error "Failed to add topic:" (:error result))
                (d*/patch-elements! sse "<div class='notification is-danger'>Failed to add topic</div>"))))
          ;; Invalid input
          (do
            (log/warn "Invalid topic input - topic:" new-topic "user-id:" user-id "meeting-id:" meeting-id)
            (d*/patch-elements! sse "")))
        (d*/close-sse! sse)
        ))}))


(defn meeting-edit-topic [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [user-name (get-in request [:session :userinfo :name])
            user-id (get-in request [:session :userinfo :user-id])
            meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            topicNotes (get-in request [:json :topicNotes])
            reflection {"topicNotes" topicNotes "userIsTyping" (str user-name " is typing ...")}
            reflection-json (json/write-str reflection)
            ]
        (log/info "Edit topic by" user-name reflection-json)
        (broadcast-meeting-page-signals! meeting-id reflection-json user-id)
        (d*/close-sse! sse)
        ))}))


(defn meeting-edit-topic-save [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [user-name (get-in request [:session :userinfo :name])
            user-id (get-in request [:session :userinfo :user-id])
            meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            topic-id (Long/parseLong (get-in request [:path-params :topic-id]))
            topicNotes (get-in request [:json :topicNotes])
            reflection {"topicNotes" topicNotes "userIsTyping" " Saved "}
            ]
        (log/info "Save topic data by" user-name)
        (log/info meeting-id topic-id)
        ;; Update topic discussion notes in database
        (let [update-result (db/update-topic-discussion-notes! topic-id topicNotes)]
          (if (:success update-result)
            (log/info "Topic discussion notes updated successfully")
            (log/error "Failed to update topic discussion notes:" (:error update-result))))
        (broadcast-meeting-page-signals! meeting-id (json/write-str reflection) nil)
        (d*/close-sse! sse)
        ))}))


(defn meeting-vote-topic [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [user-id (get-in request [:session :userinfo :user-id])
            meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            meeting-data (db/get-meeting-by-id meeting-id)
            topic-id (Long/parseLong (get-in request [:form-params "topic_id"]))
            vote-type (get-in request [:form-params "vote_type"])]
        (log/info "User" user-id "voting" vote-type "on topic" topic-id)

        (cond
          ;; Check if meeting allows voting
          (not (:meeting/allow-topic-voting meeting-data))
          (log/warn "Voting is not allowed for this meeting")

          ;; Validate input and handle vote toggle
          (and user-id topic-id vote-type
               (contains? #{"upvote" "downvote"} vote-type))
          ;; Check current vote and toggle if same, otherwise add/update
          (let [current-vote (db/get-user-vote-for-topic user-id topic-id)
                result (if (= current-vote vote-type)
                         ;; Same vote type - remove it (toggle off)
                         (db/remove-vote! user-id topic-id)
                         ;; Different vote type or no vote - add/update it
                         (db/add-vote! user-id topic-id vote-type))]
            (if (:success result)
              (do
                (if (:removed result)
                  (log/info "Vote removed successfully (toggled off)")
                  (log/info "Vote added/updated successfully"))
                (broadcast-meeting-page-update! render-topics meeting-id [] true))
              (log/error "Failed to process vote:" (:error result))))

          ;; Invalid input
          :else
          (log/warn "Invalid vote input - user-id:" user-id "topic-id:" topic-id "vote-type:" vote-type)
          )
        (d*/close-sse! sse)
        ))}))

(defn meeting-set-current-topic [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [user-id (get-in request [:session :userinfo :user-id])
            meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            topic-id (Long/parseLong (get-in request [:form-params "topic_id"]))]
        (log/info "User" user-id "setting current topic to" topic-id "for meeting" meeting-id)
        (cond
          ;; Check permissions
          (not (db/user-can-change-meeting? user-id meeting-id))
          (log/warn "User" user-id "does not have permission to set current topic for meeting" meeting-id)

          ;; Valid input - set current topic
          (and user-id topic-id meeting-id)
          (let [result (db/set-current-topic! meeting-id topic-id)
                topic (db/get-topic-by-id topic-id)]
            (if (:success result)
              (do
                (log/info "Current topic set successfully")
                (broadcast-meeting-page-update! render-meeting-body meeting-id [false] true)
                (log/info (json/write-str {"topicNotes" (:topic/discussion-notes topic)}) user-id)
                (broadcast-meeting-page-signals! meeting-id (json/write-str {"topicNotes" (:topic/discussion-notes topic)}) nil)
                )
              (log/error "Failed to set current topic:" (:error result))))

          ;; Invalid input
          :else
          (log/warn "Invalid input - user-id:" user-id "topic-id:" topic-id "meeting-id:" meeting-id)
          ))
      (d*/close-sse! sse)
      )}))

(defn meeting-delete-topic [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [_]
      (let [user-id (get-in request [:session :userinfo :user-id])
            meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            topic-id (Long/parseLong (get-in request [:form-params "topic_id"]))
            db (db/get-db)
            ;; Get topic author directly
            topic-author-result (d/q '[:find ?author
                                      :in $ ?topic-id
                                      :where [?topic-id :topic/created-by ?author]]
                                    db topic-id)
            topic-author-id (ffirst topic-author-result)]
        (log/info "User" user-id "attempting to delete topic" topic-id)
        (cond
          ;; Check if user is the topic author
          (not= user-id topic-author-id)
          (log/warn "User" user-id "is not the author of topic" topic-id)

          ;; Valid deletion - user is the author
          (and user-id topic-id topic-author-id)
          (let [result (db/delete-topic! topic-id)]
            (if (:success result)
              (do
                (log/info "Topic deleted successfully")
                (broadcast-meeting-page-update! render-topics meeting-id [] true))
              (log/error "Failed to delete topic:" (:error result))))

          ;; Invalid input
          :else
          (log/warn "Invalid input - user-id:" user-id "topic-id:" topic-id)
          )))}))


(defn meeting-start-timer [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [user-name (get-in request [:session :userinfo :name])
            user-id (get-in request [:session :userinfo :user-id])
            meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            timer-str (get-in request [:json :timer])]
        (log/info "Start timer by:" user-name timer-str)
        ;; Set the timer for this meeting
        (set-meeting-timer! meeting-id timer-str)
        (log/info "Timer set for meeting" meeting-id "until" (get @meeting-timers meeting-id))
        ;; Loop while timer is active, sending updates every second
        (d*/close-sse! sse)
        (while (> (get-timer-seconds-remaining meeting-id) 0)
          (let [remaining (get-timer-seconds-remaining meeting-id)]
            (log/info "Timer remaining for meeting" meeting-id ":" remaining "seconds")
            ;; Send timer update to client here if needed
            (broadcast-meeting-page-signals! meeting-id (json/write-str {"countdown" remaining}) nil)
            (Thread/sleep 800)))
        (broadcast-meeting-page-signals! meeting-id (json/write-str {"countdown" 0}) nil)
        (log/info "Timer finished for meeting" meeting-id)
        (broadcast-execute-script! "alert('Timer is up!')")
        (broadcast-meeting-page-signals! meeting-id (json/write-str {"countdown" 0}) nil)
        ))}))

(defn meeting-stop-timer [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [user-name (get-in request [:session :userinfo :name])
            user-id (get-in request [:session :userinfo :user-id])
            meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            timer-str (get-in request [:json :timer])]
        (log/info "Stop timer by:" user-name timer-str)
        ;; Set the timer for this meeting
        ;; (set-meeting-timer! meeting-id timer-str)
        (swap! meeting-timers dissoc meeting-id)
        (broadcast-meeting-page-signals! meeting-id (json/write-str {"countdown" 0}) nil)
        (d*/close-sse! sse)))}))


(defn meeting-add-action [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            user-id (get-in request [:session :userinfo :user-id])
            current-topic-id (get-in request [:path-params :topic-id])
            description (get-in request [:form-params "action-description"])
            assigned-to (get-in request [:form-params "assigned-to"])
            deadline-str (get-in request [:form-params "deadline"])
            is-team-action (= (get-in request [:form-params "team-action"]) "on")
            deadline (when (and deadline-str (not (clojure.string/blank? deadline-str)))
                      (java.time.Instant/parse (str deadline-str "T00:00:00Z")))
            assigned-to-user (when (and assigned-to (not is-team-action) (not= assigned-to ""))
                              (Long/parseLong assigned-to))
            assigned-to-team (when is-team-action
                              (:db/id (:meeting/team (db/get-meeting-by-id meeting-id))))]

        (log/info "Adding action:" description "assigned to user:" assigned-to-user "team:" assigned-to-team)

        (if (and description user-id meeting-id (not (clojure.string/blank? description)))
          (let [result (db/add-action! meeting-id
                                      (when current-topic-id (Long/parseLong current-topic-id))
                                      (clojure.string/trim description)
                                      assigned-to-user
                                      assigned-to-team
                                      (when deadline (java.util.Date/from deadline)))]
            (if (:success result)
              (do
                (log/info "Action added successfully")
                (broadcast-meeting-page-update! render-meeting-body meeting-id [false] true)
                (d*/patch-signals! sse "{showAddAction: false}")
                (d*/execute-script! sse "document.getElementById('add-action-form').reset()")
                )

              (log/error "Failed to add action:" (:error result))
                ))
          (log/warn "Invalid action input - description:" description "user-id:" user-id "meeting-id:" meeting-id)
          ))
      (d*/close-sse! sse)
      )}))


(defn meeting-finish [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            user-id (get-in request [:session :userinfo :user-id])
            user-access (get-in request [:session :userinfo :access-level])
            has-staff-access (contains? #{"staff" "admin"} user-access)]

        (log/info "User" user-id "attempting to finish meeting" meeting-id)

        (cond
          ;; Check if user has staff/admin access
          (not has-staff-access)
          (log/warn "User" user-id "does not have staff/admin access")

          ;; Valid request - finish the meeting
          (and user-id meeting-id has-staff-access)
          (let [result (db/finish-meeting! meeting-id)]
            (if (:success result)
              (do
                (log/info "Meeting" meeting-id "finished successfully by user" user-id)
                ;; TODO later we can show nice modal, timeout, redirect, as in example https://data-star.dev/how_tos/redirect_the_page_from_the_backend/
                (broadcast-execute-script! "window.location = '/app'")
                )
              (log/error "Failed to finish meeting:" (:error result))
                ))

          ;; Invalid input
          :else
          (log/warn "Invalid input - user-id:" user-id "meeting-id:" meeting-id)))
      (d*/close-sse! sse))}))

(defn topic-finish [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            topic-id (Long/parseLong (get-in request [:path-params :topic-id]))
            user-id (get-in request [:session :userinfo :user-id])]

        (log/info "User" user-id "attempting to finish topic" topic-id "in meeting" meeting-id)

        (cond
          ;; Check if user can change the meeting
          (not (db/user-can-change-meeting? user-id meeting-id))
          (log/warn "User" user-id "does not have permission to finish topic in meeting" meeting-id)

          ;; Valid request - finish the topic
          (and user-id meeting-id topic-id)
          (let [result (db/finish-topic! topic-id meeting-id)]
            (if (:success result)
              (do
                (log/info "Topic" topic-id "finished successfully by user" user-id)
                (broadcast-meeting-page-update! render-meeting-body meeting-id [false] true)
                )
              (log/error "Failed to finish topic:" (:error result))
                ))

          ;; Invalid input
          :else
          (log/warn "Invalid input - user-id:" user-id "meeting-id:" meeting-id "topic-id:" topic-id)))
      (d*/close-sse! sse))}))

(defn meetings-list
  "Display all finished meetings with topics and actions"
  [{session :session}]
  (log/info "Meetings list page accessed")
  (let [userinfo (:userinfo session)
        user-id (:user-id userinfo)
        user-access (:access-level userinfo)
        is-admin (= user-access "admin")
        finished-meetings (if user-id (db/get-finished-meetings-for-user user-id is-admin) [])]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/meetings-list.html" {:userinfo userinfo
                                                        :meetings finished-meetings
                                                        :is-admin is-admin})}))

(defn my-actions
  "Display all actions assigned to the current user"
  [{session :session}]
  (log/info "My Actions page accessed")
  (let [userinfo (:userinfo session)
        user-id (:user-id userinfo)
        user-actions (if user-id (db/get-user-actions user-id) [])]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/my-actions.html" {:userinfo userinfo
                                                     :actions user-actions})}))

(defn action-completion-modal [{session :session :as request}]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [userinfo (:userinfo session)
            user-id (:user-id userinfo)
            action-id (Long/parseLong (get-in request [:path-params :action-id]))
            action (db/get-user-action-by-id action-id user-id)]
        (d*/with-open-sse sse
          (if action
            (do
              (d*/patch-elements! sse (render-file "templates/action_completion_modal.html" {:action action}))
              (d*/patch-signals! sse "{actionCompletionModalOpen: true}"))
            (d*/patch-elements! sse "<div class=\"notification is-danger\">Action not found or you don't have permission to access it</div>"))))
      )}))

(defn action-update-status
  "Update action status (complete or reject)"
  [{session :session :as request}]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [userinfo (:userinfo session)
            user-id (:user-id userinfo)
            action-id (Long/parseLong (get-in request [:path-params :action-id]))
            form-params (:form-params request)
            status (get form-params "status")
            completion-notes (get form-params "completion-notes" "")
            action (db/get-user-action-by-id action-id user-id)]
        (d*/with-open-sse sse
          (if action
            (try
              (db/update-action-status! action-id status completion-notes)
              (log/info (str "Action " action-id " status updated to: " status))

              ;; Close modal and refresh page
              (d*/patch-signals! sse "{actionCompletionModalOpen: false}")
              (d*/execute-script! sse "window.location.reload()")
              (catch Exception e
                (log/error "Error updating action status:" (.getMessage e))
                (d*/patch-elements! sse "<div id=\"action-completion-error\" class=\"notification is-danger\">Error updating action status</div>")))
            (d*/patch-elements! sse "<div class=\"notification is-danger\">Action not found or you don't have permission to access it</div>")))))}))


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
   {hk-gen/on-open
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
   {hk-gen/on-open
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
   {hk-gen/on-open
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
   {hk-gen/on-open
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
   {hk-gen/on-open
    (fn [sse]
      (let [userinfo (:userinfo session)
        user-id (:user-id userinfo)
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
   {hk-gen/on-open
    (fn [sse]
      (let [user-id (get-in request [:session :userinfo :user-id])
            user-data (db/get-user-by-id user-id)
            user-teams (:user/teams user-data)]
        (log/info (str "Create meeting popup requested by: " (:user/email user-data)))
        (d*/with-open-sse sse
          (d*/patch-elements! sse (render-file "templates/create_meeting_modal.html" {:teams user-teams
                                                                                      :datetime-min (subs (str (java.time.LocalDateTime/now)) 0 16)}))
          (d*/patch-signals! sse "{createMeetingModalOpen: true}")
          ))
      )}))


(defn staff-create-meeting-save [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [user-id (get-in request [:session :userinfo :user-id])
            user-data (db/get-user-by-id user-id)
            form-params (keywordize-keys (:form-params request))]
        (log/info (str "Create meeting save requested by: " (:user/email user-data)))
        (pprint form-params)

        ;; Validate meeting data
        (let [meeting-data {:title (:title form-params)
                            :description (:description form-params)
                            :team (:team form-params)
                            :scheduled-at (:scheduled-at form-params)
                            :join-url (:join-url form-params)
                            :allow-topic-voting (= (:allow-topic-voting form-params) "on")
                            :votes-are-public (= (:votes-are-public form-params) "on")
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
                        (d*/patch-elements! sse (render-file "templates/main-content.html" (app-main-data request)))
                        (d*/patch-elements!
                         sse (render-file "templates/notifications.html" {:notifications [{:level "info"
                                                                                           :text "New meeting created successfully"}]}))

                        )

                      (do
                        (log/warn "Failed to create meeting:" (:error create-result))
                        (d*/patch-elements! sse (render-file "templates/create_meeting_modal_error.html"
                                                             {:error-message (:error create-result)})))))
                  ;; User is not a member of the team
                  (do
                    (log/warn "User" user-id "is not a member of team" team-id)
                    (d*/patch-elements! sse (render-file "templates/create_meeting_modal_error.html"
                                                         {:error-message "You are not a member of the selected team"})))))
              ;; Invalid meeting data
              (do
                (log/warn "Invalid meeting data:" meeting-data)
                (d*/patch-elements! sse (render-file "templates/create_meeting_modal_error.html"
                                                     {:error-message "Invalid meeting data. Please check all fields."}))))))
        )
      )}))


(defn admin-project-settings [request]
  (->sse-response
   request
   {hk-gen/on-open
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
   {hk-gen/on-open
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
   {hk-gen/on-open
    (fn [sse]
      (let [user_id (get-in request [:path-params :user])
            user-id-long (when user_id (Long/parseLong user_id))
            form-params (:form-params request)

            ;; Parse form data
            team-name (get form-params "name")
            team-description (get form-params "description" "")
            team-auto-domains (get form-params "auto-domains" "")
            team-managers-str (get form-params "managers" [])
            team-managers (if (vector? team-managers-str)
                           (mapv #(Long/parseLong %) team-managers-str)
                           (if (string? team-managers-str)
                             [(Long/parseLong team-managers-str)]
                             []))

            ;; Create team data structure
            team-data {:name team-name
                      :description team-description
                      :auto-domains team-auto-domains
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
   {hk-gen/on-open
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

(defn decode-google-jwt
  "Decode Google JWT credential without verification (Google already verified it)"
  [jwt-token]
  (try
    ;; For Google Sign-In, we can decode without verification since Google has already verified it
    ;; and we're receiving it directly from Google's servers
    (let [decoded (jwt/unsign jwt-token nil {:alg :none})]
      {:success true :payload decoded})
    (catch Exception e
      (log/error "Error decoding Google JWT:" (.getMessage e))
      ;; Fallback: manually decode the payload (JWT structure: header.payload.signature)
      (try
        (let [parts (clojure.string/split jwt-token #"\.")
              payload-b64 (nth parts 1)
              ;; Add padding if needed for base64 decoding
              padded-payload (let [mod (mod (count payload-b64) 4)]
                              (if (zero? mod)
                                payload-b64
                                (str payload-b64 (apply str (repeat (- 4 mod) "=")))))
              decoded-bytes (.decode (java.util.Base64/getUrlDecoder) padded-payload)
              json-str (String. decoded-bytes "UTF-8")
              payload (json/read-str json-str :key-fn keyword)]
          {:success true :payload payload})
        (catch Exception e2
          (log/error "Error manually decoding JWT:" (.getMessage e2))
          {:success false :error (.getMessage e2)})))))

(defn google-login [{session :session :as request}]
  (let [credential (get-in request [:form-params "credential"])
        g_csrf_token (get-in request [:form-params "g_csrf_token"])]
    (log/info "Google login attempt from" (:remote-addr request))
    (log/info "Received credential token of length:" (count (or credential "")))

    (if credential
      ;; Decode JWT and process user info
      (let [jwt-result (decode-google-jwt credential)]
        (if (:success jwt-result)
          (let [google-userinfo (:payload jwt-result)
                ;; Map Google JWT fields to our expected format
                mapped-userinfo {:email (:email google-userinfo)
                                :name (:name google-userinfo)
                                :given_name (:given_name google-userinfo)
                                :family_name (:family_name google-userinfo)
                                :picture (:picture google-userinfo)}]
            (log/info "Decoded Google userinfo:" mapped-userinfo)
            ;; Use existing create-or-login-user logic
            (let [user-result (create-or-login-user mapped-userinfo)]
              (if (:success user-result)
                ;; Get user details and update session
                (let [user-details (db/get-user-by-id (:user-id user-result))
                      session-userinfo {:email (:user/email user-details)
                                       :name (:user/name user-details)
                                       :user-id (:user-id user-result)
                                       :given-name (:user/given-name user-details)
                                       :family-name (:user/family-name user-details)
                                       :picture (:user/picture user-details)
                                       :logged-in true
                                       :auth-provider (:user/auth-provider user-details)
                                       :access-level (:user/access-level user-details)}
                      updated-session (assoc session :userinfo session-userinfo)]
                  {:status 302
                   :headers {"Location" "/app"}
                   :session updated-session})
                ;; User creation/login failed
                {:status 500
                 :headers {"Content-Type" "text/html"}
                 :body (str "Authentication failed: " (:error user-result))})))
          ;; JWT decoding failed
          {:status 500
           :headers {"Content-Type" "text/html"}
           :body (str "Failed to decode JWT: " (:error jwt-result))}))
      ;; No credential provided
      {:status 400
       :headers {"Content-Type" "text/html"}
       :body "No credential provided"})))


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
    (assign-user-to-auto-teams! user-id (:email userinfo))
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
                  {hk-gen/on-open
                   (fn [sse]
                     (d*/with-open-sse sse
                       (d*/patch-elements! sse
                        (render-file "templates/fake-user-form.html" (fake-user-data)))
                       ))}))


(defn meeting-start [request]
  (->sse-response
   request
   {hk-gen/on-open
    (fn [sse]
      (let [meeting-id (Long/parseLong (get-in request [:path-params :meeting-id]))
            user-id (get-in request [:session :userinfo :user-id])]
        (log/info "Start meeting:" meeting-id "by user:" user-id)

        (let [result (db/start-meeting! meeting-id)]
          (if (:success result)
            (do
              (log/info "Meeting started successfully")
              (broadcast-meeting-page-update! render-meeting-body meeting-id [false] true))
            (log/error "Failed to start meeting:" (:error result))))

        (d*/close-sse! sse)))}))



(defn logout [_]
  (-> (response/redirect "/")
      (assoc :session {})))

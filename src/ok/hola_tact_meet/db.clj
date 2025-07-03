(ns ok.hola_tact_meet.db
  (:require [datomic.client.api :as d])
  )

(def client (d/client {:server-type :datomic-local
                       :system "dev"}))

(defn ensure-database-exists! 
  "Create the database if it doesn't exist"
  []
  
  (d/create-database client {:db-name "meetings"}))

(defn get-connection 
  "Get connection to the database, creating it if necessary"
  []
  
  (ensure-database-exists!)
  (d/connect client {:db-name "meetings"}))

(def user-schema [{:db/ident       :user/name
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The name of the user."}

                  {:db/ident       :user/email
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The email address of the user."}

                  {:db/ident       :user/family-name
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The family name of the user."}

                  {:db/ident       :user/given-name
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The given name of the user."}

                  {:db/ident       :user/picture
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The URL of the user's picture."}

                  {:db/ident       :user/auth-provider
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The authentication provider for the user."}

                  {:db/ident       :user/access-level
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The access level of the user: user, admin, or staff."}

                  {:db/ident       :user/teams
                   :db/valueType   :db.type/ref
                   :db/cardinality :db.cardinality/many
                   :db/doc         "Teams this user belongs to."}

                  {:db/ident       :user/last-login
                   :db/valueType   :db.type/instant
                   :db/cardinality :db.cardinality/one
                   :db/doc         "When the user last logged in."}

                  ])

(def team-schema [{:db/ident       :team/name
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The name of the team."}

                  {:db/ident       :team/description
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "Description of the team."}

                  {:db/ident       :team/created-at
                   :db/valueType   :db.type/instant
                   :db/cardinality :db.cardinality/one
                   :db/doc         "When the team was created."}

                  {:db/ident       :team/created-by
                   :db/valueType   :db.type/ref
                   :db/cardinality :db.cardinality/one
                   :db/doc         "User who created the team."}

                  {:db/ident       :team/managers
                   :db/valueType   :db.type/ref
                   :db/cardinality :db.cardinality/many
                   :db/doc         "Users who can manage this team."}

                  ])

(def meeting-schema [{:db/ident       :meeting/title
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one
                      :db/doc         "The title of the meeting."}

                     {:db/ident       :meeting/description
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one
                      :db/doc         "Description of the meeting."}

                     {:db/ident       :meeting/team
                      :db/valueType   :db.type/ref
                      :db/cardinality :db.cardinality/one
                      :db/doc         "The team this meeting belongs to."}

                     {:db/ident       :meeting/created-by
                      :db/valueType   :db.type/ref
                      :db/cardinality :db.cardinality/one
                      :db/doc         "User who created the meeting."}

                     {:db/ident       :meeting/created-at
                      :db/valueType   :db.type/instant
                      :db/cardinality :db.cardinality/one
                      :db/doc         "When the meeting was created."}

                     {:db/ident       :meeting/scheduled-at
                      :db/valueType   :db.type/instant
                      :db/cardinality :db.cardinality/one
                      :db/doc         "When the meeting is scheduled."}

                     {:db/ident       :meeting/status
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one
                      :db/doc         "Status: scheduled, in-progress, completed, cancelled."}

                     {:db/ident       :meeting/join-url
                      :db/valueType   :db.type/string
                      :db/cardinality :db.cardinality/one
                      :db/doc         "URL for joining the meeting (Google Meet, Zoom, etc)."}

                     {:db/ident       :meeting/allow-topic-voting
                      :db/valueType   :db.type/boolean
                      :db/cardinality :db.cardinality/one
                      :db/doc         "Whether to allow voting on topics."}

                     {:db/ident       :meeting/sort-topics-by-votes
                      :db/valueType   :db.type/boolean
                      :db/cardinality :db.cardinality/one
                      :db/doc         "Whether to sort topics by vote count."}

                     {:db/ident       :meeting/is-visible
                      :db/valueType   :db.type/boolean
                      :db/cardinality :db.cardinality/one
                      :db/doc         "Whether the meeting is visible to team members."}

                     ])

(def topic-schema [{:db/ident       :topic/title
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "The title of the topic/tension."}

                   {:db/ident       :topic/description
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "Description of the topic."}

                   {:db/ident       :topic/meeting
                    :db/valueType   :db.type/ref
                    :db/cardinality :db.cardinality/one
                    :db/doc         "The meeting this topic belongs to."}

                   {:db/ident       :topic/created-by
                    :db/valueType   :db.type/ref
                    :db/cardinality :db.cardinality/one
                    :db/doc         "User who created the topic."}

                   {:db/ident       :topic/created-at
                    :db/valueType   :db.type/instant
                    :db/cardinality :db.cardinality/one
                    :db/doc         "When the topic was created."}

                   {:db/ident       :topic/vote-count
                    :db/valueType   :db.type/long
                    :db/cardinality :db.cardinality/one
                    :db/doc         "Number of votes for this topic."}

                   {:db/ident       :topic/timer-minutes
                    :db/valueType   :db.type/long
                    :db/cardinality :db.cardinality/one
                    :db/doc         "Timer duration in minutes for this topic."}

                   {:db/ident       :topic/status
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "Status: pending, discussing, accepted, declined."}

                   {:db/ident       :topic/decision
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "Final decision or outcome for the topic."}

                   ])

(def action-item-schema [{:db/ident       :action/title
                          :db/valueType   :db.type/string
                          :db/cardinality :db.cardinality/one
                          :db/doc         "The title of the action item."}

                         {:db/ident       :action/description
                          :db/valueType   :db.type/string
                          :db/cardinality :db.cardinality/one
                          :db/doc         "Description of the action item."}

                         {:db/ident       :action/assignee
                          :db/valueType   :db.type/ref
                          :db/cardinality :db.cardinality/one
                          :db/doc         "User assigned to this action item."}

                         {:db/ident       :action/topic
                          :db/valueType   :db.type/ref
                          :db/cardinality :db.cardinality/one
                          :db/doc         "Topic this action item came from."}

                         {:db/ident       :action/meeting
                          :db/valueType   :db.type/ref
                          :db/cardinality :db.cardinality/one
                          :db/doc         "Meeting this action item was created in."}

                         {:db/ident       :action/created-at
                          :db/valueType   :db.type/instant
                          :db/cardinality :db.cardinality/one
                          :db/doc         "When the action item was created."}

                         {:db/ident       :action/due-date
                          :db/valueType   :db.type/instant
                          :db/cardinality :db.cardinality/one
                          :db/doc         "When the action item is due."}

                         {:db/ident       :action/status
                          :db/valueType   :db.type/string
                          :db/cardinality :db.cardinality/one
                          :db/doc         "Status: open, in-progress, completed, cancelled."}

                         {:db/ident       :action/completed-at
                          :db/valueType   :db.type/instant
                          :db/cardinality :db.cardinality/one
                          :db/doc         "When the action item was completed."}

                         ])

(def vote-schema [{:db/ident       :vote/topic
                   :db/valueType   :db.type/ref
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The topic being voted on."}

                  {:db/ident       :vote/user
                   :db/valueType   :db.type/ref
                   :db/cardinality :db.cardinality/one
                   :db/doc         "User who cast the vote."}

                  {:db/ident       :vote/created-at
                   :db/valueType   :db.type/instant
                   :db/cardinality :db.cardinality/one
                   :db/doc         "When the vote was cast."}

                  ])

(def all-schema (concat user-schema
                        team-schema
                        meeting-schema
                        topic-schema
                        action-item-schema
                        vote-schema))


(defn install-schema! 
  "Install the schema into the database"
  [conn]
  
  (d/transact conn {:tx-data all-schema}))

(defn initialize-db! 
  "Initialize database with schema"
  []
  
  (let [conn (get-connection)]
    (install-schema! conn)
    conn))

;; Use lazy initialization
(defonce conn-atom (atom nil))

(defn get-conn []
  (when (nil? @conn-atom)
    (reset! conn-atom (initialize-db!)))
  @conn-atom)

(defn get-db []
  (d/db (get-conn)))


(defn find-user-by-email 
  "Find existing user by email, returns user ID or nil"
  [email]
  
  (let [db (get-db)
        result (d/q '[:find ?e
                      :in $ ?email
                      :where [?e :user/email ?email]]
                    db email)]
    (ffirst result)))

(defn create-user 
  "Create new user, throws exception if user with same email already exists"
  [userinfo]

  (let [existing-user (find-user-by-email (:email userinfo))]
    (when existing-user
      (throw (ex-info "User with this email already exists" 
                      {:email (:email userinfo) 
                       :existing-user-id existing-user})))
    (let [user-data {:user/name (:name userinfo)
                     :user/email (:email userinfo)
                     :user/family-name (:family-name userinfo)
                     :user/given-name (:given-name userinfo)
                     :user/picture (:picture userinfo)
                     :user/auth-provider (:auth-provider userinfo)
                     :user/access-level (or (:access-level userinfo) "user")}
          result (d/transact (get-conn) {:tx-data [user-data]})
          user-id (get-in result [:tempids (first (keys (:tempids result)))])]
      user-id)))

(defn get-all-users 
  "Get all users with their basic information"
  []
  (let [db (get-db)
        user-ids (d/q '[:find ?e
                        :where [?e :user/name _]]
                      db)]
    (map (fn [[user-id]]
           (let [user-data (d/pull db '[:db/id :user/name :user/email :user/access-level 
                                        :user/picture :user/family-name :user/given-name 
                                        :user/last-login] user-id)]
             {:id (:db/id user-data)
              :name (:user/name user-data)
              :email (:user/email user-data)
              :access-level (:user/access-level user-data)
              :picture (:user/picture user-data)
              :family-name (:user/family-name user-data)
              :given-name (:user/given-name user-data)
              :last-login (:user/last-login user-data)}))
         user-ids)))

(defn get-user-by-id 
  "Get user data by their database ID"
  [user-id]

  (let [db (get-db)]
    (d/pull db '[:user/name :user/email :user/family-name 
                 :user/given-name :user/picture :user/auth-provider 
                 :user/access-level] 
            user-id)))

(defn update-last-login!
  "Update the last login timestamp for a user"
  [user-id]
  (d/transact (get-conn) {:tx-data [{:db/id user-id
                                     :user/last-login (java.util.Date.)}]}))

(comment

  ;; Initialize database and schema
  (initialize-db!)

  ;; To add new schema fields to existing database:
  ;; 1. Add the field definition to the appropriate schema (e.g., user-schema)
  ;; 2. Transact the new schema to the existing database:
  (d/transact (get-conn) {:tx-data [{:db/ident       :user/last-login
                                     :db/valueType   :db.type/instant
                                     :db/cardinality :db.cardinality/one
                                     :db/doc         "When the user last logged in."}]})

  ;; Query examples
  (def all-users-q '[:find ?user-name
                     :where [_ :user/name ?user-name]])

  (d/q all-users-q (get-db))

  (def query '[:find ?e ?name ?email
               :where 
               [?e :user/name ?name]
               [?e :user/email ?email]])

  (def users (d/q query (get-db)))
  (d/q query (get-db))
  (doseq [user users]
    (println user))

  )

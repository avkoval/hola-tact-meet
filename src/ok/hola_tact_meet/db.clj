(ns ok.hola-tact-meet.db
  (:require [datomic.client.api :as d]
            [ok.hola-tact-meet.schema :as schema]
            [ok.hola-tact-meet.utils :as utils]
            [malli.core]
            [clojure.tools.logging :as log]
            [clojure.string]
            ))

(defn get-client []
  (let [config (utils/app-config)
        system (:datomic/system config)]
    (d/client {:server-type :datomic-local
               :system system})))

(defn ensure-database-exists!
  "Create the database if it doesn't exist"
  [db-name]
  (let [client (get-client)]
    (d/create-database client {:db-name db-name})))

;; Generate unique DB name once per JVM startup for memory databases
(defonce unique-db-suffix (System/currentTimeMillis))

(defn get-connection
  "Get connection to the database, creating it if necessary"
  []
  (let [config (utils/app-config)
        base-db-name (:datomic/db-name config)
        ;; For memory databases, add unique suffix to avoid lock conflicts
        db-name (if (.startsWith base-db-name "memory://")
                  (str base-db-name "-" unique-db-suffix)
                  base-db-name)
        client (get-client)]
    (ensure-database-exists! db-name)
    (d/connect client {:db-name db-name})))



(defn install-schema!
  "Install the schema into the database"
  [conn]

  (d/transact conn {:tx-data schema/all-schema}))

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

(defn find-teams-for-email-domain
  "Find teams that should auto-assign users based on email domain"
  [email]
  (when email
    (let [domain (second (clojure.string/split email #"@"))
          db (get-db)
          teams-with-domains (d/q '[:find ?e ?domains
                                    :where [?e :team/auto-domains ?domains]]
                                  db)]
      (->> teams-with-domains
           (filter (fn [[team-id domains]]
                     (when domains
                       (let [domain-list (clojure.string/split-lines domains)]
                         (some #(= (clojure.string/trim %) domain) domain-list)))))
           (map first)))))

(defn is-first-user?
  "Check if this would be the first user in the system"
  []
  (let [db (get-db)
        users (d/q '[:find ?e
                     :where [?e :user/name _]]
                   db)]
    (empty? users)))

(defn create-user
  "Create new user, throws exception if user with same email already exists"
  [userinfo]
  (when (nil? (:email userinfo))
    (throw (ex-info "Email is required" {:userinfo userinfo})))
  (when (nil? (:name userinfo))
    (throw (ex-info "Name is required" {:userinfo userinfo})))

  (let [existing-user (find-user-by-email (:email userinfo))]
    (when existing-user
      (throw (ex-info "User with this email already exists"
                      {:email (:email userinfo)
                       :existing-user-id existing-user})))
    (let [is-first-user (is-first-user?)
          default-access-level (if is-first-user "admin" "user")
          full-name (str (or (:given-name userinfo) "")
                         (if (and (:given-name userinfo) (:family-name userinfo)) " " "")
                         (or (:family-name userinfo) ""))
          constructed-name (if (clojure.string/blank? full-name)
                            (or (:name userinfo) "Unknown User")
                            (clojure.string/trim full-name))
          temp-id "new-user"
          user-data (->> {:db/id temp-id
                          :user/name constructed-name
                          :user/email (:email userinfo)
                          :user/family-name (:family-name userinfo)
                          :user/given-name (:given-name userinfo)
                          :user/picture (:picture userinfo)
                          :user/auth-provider (:auth-provider userinfo)
                          :user/access-level (or (:access-level userinfo) default-access-level)
                          :user/active (or (:active userinfo) true)
                          :user/last-login (java.util.Date.)}
                         (filter (fn [[k v]] (and (not (nil? v)) (not= v ""))))
                         (into {}))
          result (d/transact (get-conn) {:tx-data [user-data]})
          user-id (get-in result [:tempids temp-id])]
      (when is-first-user
        (log/info (str "First user registered with admin privileges: " (:email userinfo))))
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
                                        :user/last-login :user/active
                                        {:user/teams [:team/name]}] user-id)]
             {:id (:db/id user-data)
              :name (:user/name user-data)
              :email (:user/email user-data)
              :access-level (:user/access-level user-data)
              :picture (:user/picture user-data)
              :family-name (:user/family-name user-data)
              :given-name (:user/given-name user-data)
              :last-login (:user/last-login user-data)
              :active (:user/active user-data)
              :teams (map :team/name (:user/teams user-data))}))
         user-ids)))

(defn get-user-by-id
  "Get user data by their database ID"
  [user-id]

  (let [db (get-db)]
    (d/pull db '[:user/name :user/email :user/family-name
                 :user/given-name :user/picture :user/auth-provider
                 :user/access-level :user/active
                 {:user/teams [:db/id :team/name :team/description]}]
            user-id)))

(defn update-last-login!
  "Update the last login timestamp for a user"
  [user-id]
  (d/transact (get-conn) {:tx-data [{:db/id user-id
                                     :user/last-login (java.util.Date.)}]}))

(defn toggle-user-active!
  "Toggle the active status of a user"
  [user-id]
  (let [db (get-db)
        current-active-result (d/q '[:find ?active
                                     :in $ ?user-id
                                     :where [?user-id :user/active ?active]]
                                   db user-id)
        current-active (ffirst current-active-result)
        new-active (not current-active)]
    (d/transact (get-conn) {:tx-data [{:db/id user-id
                                       :user/active new-active}]})
    new-active))

(defn get-all-teams
  "Get all teams from the database"
  []
  (let [db (get-db)
        team-ids (d/q '[:find ?e
                        :where [?e :team/name _]]
                      db)]
    (map (fn [[team-id]]
           (let [team-data (d/pull db '[:db/id :team/name :team/description
                                        {:team/managers [:db/id :user/name :user/email]}] team-id)]
             {:id (:db/id team-data)
              :name (:team/name team-data)
              :description (:team/description team-data)
              :managers (:team/managers team-data)}))
         team-ids)))

(defn get-staff-admin-users
  "Get all users with staff or admin access level"
  []
  (let [db (get-db)
        user-ids (d/q '[:find ?e
                        :where
                        [?e :user/access-level ?level]
                        [(contains? #{"staff" "admin"} ?level)]]
                      db)]
    (map (fn [[user-id]]
           (let [user-data (d/pull db '[:db/id :user/name :user/email :user/access-level] user-id)]
             {:id (:db/id user-data)
              :name (:user/name user-data)
              :email (:user/email user-data)
              :access-level (:user/access-level user-data)}))
         user-ids)))

(defn find-team-by-name
  "Find existing team by name, returns team ID or nil"
  [team-name]
  (let [db (get-db)
        result (d/q '[:find ?e
                      :in $ ?name
                      :where [?e :team/name ?name]]
                    db team-name)]
    (ffirst result)))

(defn validate-user-ids
  "Check if user IDs exist in database, returns vector of valid IDs"
  [user-ids]
  (when (seq user-ids)
    (let [db (get-db)
          existing-ids-result (d/q '[:find ?e
                                     :in $ [?e ...]
                                     :where [?e :user/name _]]
                                   db user-ids)
          existing-ids (mapv first existing-ids-result)]
      existing-ids)))

(defn create-team!
  "Create a new team with validation for unique name
   team-data should contain: {:name string :description string :managers [user-ids]}
   Returns: {:success true :team-id id} or {:success false :error string}"
  [team-data]
  (let [{:keys [name description auto-domains managers]} team-data]
    (log/info "ok-2025-07-06-1751810064")
    (log/info [name description managers])
    (try
      ;; Check if team name already exists
      (if (find-team-by-name name)
        {:success false :error (str "Team with name '" name "' already exists")}

        ;; Create the team
        (let [;; Validate that manager IDs exist in database
              valid-managers (validate-user-ids managers)
              ;; Create transaction data with temp ID
              temp-id "new-team"
              team-tx-data (cond-> {:db/id temp-id
                                   :team/name name
                                   :team/description (or description "")
                                   :team/created-at (java.util.Date.)}
                             (seq valid-managers) (assoc :team/managers valid-managers)
                             (first valid-managers) (assoc :team/created-by (first valid-managers))
                             (and auto-domains (not (clojure.string/blank? auto-domains)))
                             (assoc :team/auto-domains (clojure.string/trim auto-domains)))
              _ (log/debug "Team transaction data:" team-tx-data)
              result (d/transact (get-conn) {:tx-data [team-tx-data]})
              team-id (get-in result [:tempids temp-id])]
          (log/debug "Team created with ID:" team-id)
          {:success true :team-id team-id}))

      (catch Exception e
        (if (re-find #"unique" (.getMessage e))
          {:success false :error (str "Team with name '" name "' already exists")}
          {:success false :error (str "Failed to create team: " (.getMessage e))})))))

(defn create-team-with-validation!
  "Create team with Malli validation and uniqueness check
   Returns: {:success true :team-id id} or {:success false :error string}"
  [team-data]
  (let [validation-schema [:map
                          [:name [:string {:min 1}]]
                          [:description {:optional true} [:string]]
                          [:auto-domains {:optional true} [:string]]
                          [:managers {:optional true} [:vector :int]]]]
    (if (malli.core/validate validation-schema team-data)
      (create-team! team-data)
      {:success false :error "Invalid team data format"})))

(defn user-is-team-member?
  "Check if a user is a member of a specific team"
  [user-id team-id]
  (let [db (get-db)
        result (d/q '[:find ?user-id
                      :in $ ?user-id ?team-id
                      :where [?user-id :user/teams ?team-id]]
                    db user-id team-id)]
    (seq result)))

(defn add-meeting!
  "Add a new meeting to the database"
  [meeting-data user-id]
  (try
    (let [team-id (Long/parseLong (:team meeting-data))
          scheduled-at (java.time.Instant/parse (str (:scheduled-at meeting-data) ":00Z"))
          temp-id "new-meeting"
          meeting-tx-data {:db/id temp-id
                          :meeting/title (:title meeting-data)
                          :meeting/description (or (:description meeting-data) "")
                          :meeting/team team-id
                          :meeting/created-by user-id
                          :meeting/created-at (java.util.Date.)
                          :meeting/scheduled-at (java.util.Date/from scheduled-at)
                          :meeting/status "scheduled"
                          :meeting/join-url (or (:join-url meeting-data) "")
                          :meeting/allow-topic-voting (boolean (:allow-topic-voting meeting-data))
                          :meeting/sort-topics-by-votes (boolean (:sort-topics-by-votes meeting-data))
                          :meeting/is-visible (boolean (:is-visible meeting-data))}
          result (d/transact (get-conn) {:tx-data [meeting-tx-data]})
          meeting-id (get-in result [:tempids temp-id])]
      (log/info meeting-tx-data result)
      (log/info "Meeting created successfully with ID:" meeting-id)
      {:success true :meeting-id meeting-id})
    (catch Exception e
      (log/error "Failed to create meeting:" (.getMessage e))
      {:success false :error (str "Failed to create meeting: " (.getMessage e))})))

(defn add-user-to-teams!
  "Add user to additional teams (doesn't remove existing teams)"
  [user-id team-ids]
  (when (seq team-ids)
    (let [tx-data (mapv (fn [team-id]
                          {:db/id user-id
                           :user/teams team-id})
                        team-ids)]
      (d/transact (get-conn) {:tx-data tx-data}))))

(defn update-user-teams!
  "Update user's team memberships. Replaces all existing team memberships with new ones.
   user-id: database ID of the user
   team-ids: vector of team database IDs (can be empty to remove all teams)
   Returns: {:success true} or {:success false :error string}"
  [user-id team-ids]
  (try
    (let [db (get-db)
          current-teams-result (d/q '[:find ?team
                                      :in $ ?user-id
                                      :where [?user-id :user/teams ?team]]
                                    db user-id)
          current-teams (set (mapv first current-teams-result))
          new-teams (set team-ids)
          teams-to-retract (clojure.set/difference current-teams new-teams)
          teams-to-add (clojure.set/difference new-teams current-teams)
          retract-data (mapv (fn [team-id] [:db/retract user-id :user/teams team-id]) teams-to-retract)
          add-data (mapv (fn [team-id] [:db/add user-id :user/teams team-id]) teams-to-add)
          tx-data (concat retract-data add-data)]
      (when (seq tx-data)
        (log/info (str "Updating user " user-id " teams with tx-data:" tx-data))
        (d/transact (get-conn) {:tx-data tx-data}))
      {:success true})
    (catch Exception e
      {:success false :error (str "Failed to update user teams: " (.getMessage e))})))

(defn get-recent-meetings-for-user
  "Get recent meetings for user's teams, limited to 3 most recent"
  [user-id]
  (let [db (get-db)
        ;; First get user's teams
        user-teams-result (d/q '[:find ?team
                                :in $ ?user-id
                                :where [?user-id :user/teams ?team]]
                              db user-id)
        user-team-ids (mapv first user-teams-result)]
    (if (seq user-team-ids)
      ;; Get meetings for user's teams, sorted by created-at desc, limit 3
      (let [meetings-result (d/q '[:find ?meeting ?title ?created-at ?created-by-name ?status
                                  :in $ [?team-id ...]
                                  :where
                                  [?meeting :meeting/team ?team-id]
                                  [?meeting :meeting/title ?title]
                                  [?meeting :meeting/created-at ?created-at]
                                  [?meeting :meeting/created-by ?created-by]
                                  [?created-by :user/name ?created-by-name]
                                   [?meeting :meeting/status ?status]]
                                db user-team-ids)
            ;; Sort by created-at desc and take first 3
            sorted-meetings (->> meetings-result
                               (sort-by #(nth % 2) #(compare %2 %1))
                               (take 3))]
        (mapv (fn [[meeting-id title created-at created-by-name status]]
                (let [;; Use d/pull to safely get optional attributes
                      meeting-data (d/pull (get-db) [:meeting/description] meeting-id)]
                  {:id meeting-id
                   :title title
                   :created-at created-at
                   :created-by-name created-by-name
                   :status status
                   :description (:meeting/description meeting-data)
                   }))
              sorted-meetings))
      [])))

(defn get-active-meetings-for-user
  "Get all active meetings for user's teams (future/now + is_visible=true + not finished)"
  [user-id]
  (let [db (get-db)
        now (java.util.Date.)
        ;; Get start of today (midnight)
        today-start (let [cal (java.util.Calendar/getInstance)]
                      (.setTime cal now)
                      (.set cal java.util.Calendar/HOUR_OF_DAY 0)
                      (.set cal java.util.Calendar/MINUTE 0)
                      (.set cal java.util.Calendar/SECOND 0)
                      (.set cal java.util.Calendar/MILLISECOND 0)
                      (.getTime cal))
        ;; First get user's teams
        user-teams-result (d/q '[:find ?team
                                :in $ ?user-id
                                :where [?user-id :user/teams ?team]]
                              db user-id)
        user-team-ids (mapv first user-teams-result)]
    (if (seq user-team-ids)
      ;; Get active meetings for user's teams (excluding finished meetings)
      (let [meetings-result (d/q '[:find ?meeting ?title ?scheduled-at ?created-by-name ?join-url ?status
                                  :in $ [?team-id ...] ?today-start
                                  :where
                                  [?meeting :meeting/team ?team-id]
                                  [?meeting :meeting/title ?title]
                                  [?meeting :meeting/scheduled-at ?scheduled-at]
                                  [?meeting :meeting/is-visible true]
                                  [?meeting :meeting/created-by ?created-by]
                                  [?created-by :user/name ?created-by-name]
                                  [?meeting :meeting/join-url ?join-url]
                                  [?meeting :meeting/status ?status]
                                  [(>= ?scheduled-at ?today-start)]
                                  [(not= ?status "finished")]]
                                db user-team-ids today-start)
            ;; Sort by scheduled-at asc (earliest first)
            sorted-meetings (->> meetings-result
                               (sort-by #(nth % 2)))]
        (mapv (fn [[meeting-id title scheduled-at created-by-name join-url status]]
                {:id meeting-id
                 :title title
                 :scheduled-at scheduled-at
                 :created-by-name created-by-name
                 :join-url join-url
                 :status status})
              sorted-meetings))
      [])))

(defn get-user-statistics
  "Get user statistics for dashboard display"
  []
  (let [db (get-db)
        total-users-result (d/q '[:find ?e
                                  :where [?e :user/name _]]
                                db)
        active-users-result (d/q '[:find ?e
                                   :where
                                   [?e :user/name _]
                                   [?e :user/active true]]
                                 db)
        admin-users-result (d/q '[:find ?e
                                  :where
                                  [?e :user/name _]
                                  [?e :user/access-level "admin"]]
                                db)
        staff-users-result (d/q '[:find ?e
                                  :where
                                  [?e :user/name _]
                                  [?e :user/access-level "staff"]]
                                db)]
    {:total-users (count total-users-result)
     :active-users (count active-users-result)
     :admin-count (count admin-users-result)
     :staff-count (count staff-users-result)}))


(defn user-has-active-meeting?
  "Check if user has a specific meeting in their active meetings"
  [user-id meeting-id]
  (let [active-meetings (get-active-meetings-for-user user-id)
        meeting-ids (set (map :id active-meetings))]
    (contains? meeting-ids meeting-id)))

(defn add-participant!
  "Add a participant to a meeting"
  [user-id meeting-id]
  (try
    (let [participant-data {:participant/user user-id
                           :participant/meeting meeting-id
                           :participant/joined-at (java.util.Date.)}
          _ (d/transact (get-conn) {:tx-data [participant-data]})]

      (log/info "Participant added successfully:" participant-data)
      {:success true})
    (catch Exception e
      (log/error "Failed to add participant:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn add-topic!
  "Add a new topic to a meeting"
  [meeting-id user-id topic-title]
  (try
    (when (and meeting-id user-id topic-title
               (<= (count topic-title) 250))
      (let [topic-data {:topic/title topic-title
                        :topic/meeting meeting-id
                        :topic/created-by user-id
                        :topic/created-at (java.util.Date.)}
            result (d/transact (get-conn) {:tx-data [topic-data]})
            topic-id (get-in result [:tempids (first (keys (:tempids result)))])]
        (log/info "Topic added successfully:" topic-data)
        {:success true :topic-id topic-id}))
    (catch Exception e
      (log/error "Failed to add topic:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn get-topics-for-meeting
  "Get all topics for a meeting with vote counts, sorted by vote score"
  [meeting-id]
  (let [db (get-db)
        topics-result (d/q '[:find ?topic ?title ?created-at ?created-by ?created-by-name
                             :in $ ?meeting-id
                             :where
                             [?topic :topic/meeting ?meeting-id]
                             [?topic :topic/title ?title]
                             [?topic :topic/created-at ?created-at]
                             [?topic :topic/created-by ?created-by]
                             [?created-by :user/name ?created-by-name]]
                           db meeting-id)]
    (mapv (fn [[topic-id title created-at created-by created-by-name]]
            (let [;; Get discussion notes and finished-at using pull
                  topic-data (d/pull db '[:topic/discussion-notes :topic/finished-at] topic-id)
                  discussion-notes (or (:topic/discussion-notes topic-data) "")
                  finished-at (:topic/finished-at topic-data)
                  ;; Get vote counts for this topic
                  upvotes-result (d/q '[:find (count ?vote)
                                        :in $ ?topic-id
                                        :where
                                        [?vote :vote/topic ?topic-id]
                                        [?vote :vote/type "upvote"]]
                                      db topic-id)
                  downvotes-result (d/q '[:find (count ?vote)
                                          :in $ ?topic-id
                                          :where
                                          [?vote :vote/topic ?topic-id]
                                          [?vote :vote/type "downvote"]]
                                        db topic-id)
                  upvotes (or (ffirst upvotes-result) 0)
                  downvotes (or (ffirst downvotes-result) 0)
                  vote-score (- upvotes downvotes)]
              {:id topic-id
               :title title
               :created-at created-at
               :created-by created-by
               :created-by-name created-by-name
               :discussion-notes discussion-notes
               :upvotes upvotes
               :downvotes downvotes
               :vote-score vote-score
               :finished-at finished-at}))
          ;; Sort by vote score (highest first)
          (sort-by :vote-score #(compare %2 %1) topics-result))))

(defn add-vote!
  "Add or update a vote for a topic by a user"
  [user-id topic-id vote-type]
  (try
    (let [db (get-db)
          ;; Check if user already voted on this topic
          existing-vote-result (d/q '[:find ?vote
                                     :in $ ?user-id ?topic-id
                                     :where
                                     [?vote :vote/user ?user-id]
                                     [?vote :vote/topic ?topic-id]]
                                   db user-id topic-id)
          existing-vote-id (ffirst existing-vote-result)]
      (if existing-vote-id
        ;; Update existing vote
        (let [result (d/transact (get-conn) {:tx-data [{:db/id existing-vote-id
                                                        :vote/type vote-type}]})]
          (log/info "Vote updated for user" user-id "on topic" topic-id "to" vote-type)
          {:success true})
        ;; Create new vote
        (let [vote-data {:vote/user user-id
                         :vote/topic topic-id
                         :vote/type vote-type
                         :vote/created-at (java.util.Date.)}
              result (d/transact (get-conn) {:tx-data [vote-data]})]
          (log/info "New vote added for user" user-id "on topic" topic-id "with type" vote-type)
          {:success true})))
    (catch Exception e
      (log/error "Failed to add vote:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn remove-vote!
  "Remove a user's vote for a topic"
  [user-id topic-id]
  (try
    (let [db (get-db)
          ;; Find existing vote
          existing-vote-result (d/q '[:find ?vote
                                     :in $ ?user-id ?topic-id
                                     :where
                                     [?vote :vote/user ?user-id]
                                     [?vote :vote/topic ?topic-id]]
                                   db user-id topic-id)
          existing-vote-id (ffirst existing-vote-result)]
      (if existing-vote-id
        ;; Remove the vote
        (do
          (d/transact (get-conn) {:tx-data [[:db/retractEntity existing-vote-id]]})
          (log/info "Vote removed for user" user-id "on topic" topic-id)
          {:success true :removed true})
        ;; No vote to remove
        (do
          (log/info "No vote to remove for user" user-id "on topic" topic-id)
          {:success true :removed false})))
    (catch Exception e
      (log/error "Failed to remove vote:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn get-user-vote-for-topic
  "Get the current vote type for a user on a specific topic"
  [user-id topic-id]
  (let [db (get-db)
        result (d/q '[:find ?vote-type
                      :in $ ?user-id ?topic-id
                      :where
                      [?vote :vote/user ?user-id]
                      [?vote :vote/topic ?topic-id]
                      [?vote :vote/type ?vote-type]]
                    db user-id topic-id)]
    (ffirst result)))

(defn get-meeting-by-id
  "Get meeting data by ID"
  [meeting-id]
  (let [db (get-db)]
    (d/pull db '[:db/id :meeting/title :meeting/description :meeting/status
                 :meeting/scheduled-at :meeting/created-at :meeting/join-url
                 :meeting/allow-topic-voting :meeting/sort-topics-by-votes
                 :meeting/is-visible :meeting/votes-are-public
                 {:meeting/team [:db/id :team/name]}
                 {:meeting/created-by [:db/id :user/name :user/email]}
                 {:meeting/current-topic [:db/id :topic/title :topic/finished-at
                                          :topic/discussion-notes
                                         {:topic/created-by [:db/id :user/name]}]}]
            meeting-id)))

(defn set-current-topic!
  "Set the current topic for a meeting"
  [meeting-id topic-id]
  (try
    (let [result (d/transact (get-conn) {:tx-data [{:db/id meeting-id
                                                    :meeting/current-topic topic-id}]})]
      (log/info "Current topic set for meeting" meeting-id "to topic" topic-id)
      {:success true})
    (catch Exception e
      (log/error "Failed to set current topic:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn user-can-change-meeting?
  "Check if user has permission to set current topic (staff/admin and meeting participant)"
  [user-id meeting-id]
  (let [db (get-db)
        ;; Check user access level
        user-access-result (d/q '[:find ?access-level
                                 :in $ ?user-id
                                 :where [?user-id :user/access-level ?access-level]]
                               db user-id)
        user-access (ffirst user-access-result)
        has-staff-access (contains? #{"staff" "admin"} user-access)

        ;; Check if user is participant in the meeting
        participant-result (d/q '[:find ?participant
                                 :in $ ?user-id ?meeting-id
                                 :where [?participant :participant/user ?user-id]
                                        [?participant :participant/meeting ?meeting-id]]
                               db user-id meeting-id)
        is-participant (seq participant-result)]

    (and has-staff-access is-participant)))

(defn delete-action! [action-id]
  (d/transact (get-conn) {:tx-data [[:db/retractEntity action-id]]})
)

(defn update-action!
  "Update an existing action"
  [action-id description assigned-to-user assigned-to-team deadline]
  (try
    (let [action-data (cond-> {:db/id action-id
                              :action/description description}
                        assigned-to-user (assoc :action/assigned-to-user assigned-to-user)
                        assigned-to-team (assoc :action/assigned-to-team assigned-to-team)
                        deadline (assoc :action/deadline deadline))
          ;; Remove existing assignments if switching between user/team
          retract-data (cond-> []
                         (and (nil? assigned-to-user) (not (nil? assigned-to-team)))
                         (conj [:db/retract action-id :action/assigned-to-user])
                         (and (nil? assigned-to-team) (not (nil? assigned-to-user)))
                         (conj [:db/retract action-id :action/assigned-to-team])
                         (nil? deadline)
                         (conj [:db/retract action-id :action/deadline]))
          tx-data (concat [action-data] retract-data)
          result (d/transact (get-conn) {:tx-data tx-data})]
      (log/info "Action updated successfully:" action-data)
      {:success true})
    (catch Exception e
      (log/error "Failed to update action:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn delete-topic!
  "Delete a topic by ID, including all related votes"
  [topic-id]
  (try
    (let [db (get-db)
          ;; Find all votes for this topic
          votes-result (d/q '[:find ?vote
                             :in $ ?topic-id
                             :where [?vote :vote/topic ?topic-id]]
                           db topic-id)
          vote-ids (mapv first votes-result)
          ;; Create transaction data to delete votes first, then topic
          tx-data (concat
                   ;; Delete all votes for this topic
                   (mapv (fn [vote-id] [:db/retractEntity vote-id]) vote-ids)
                   ;; Delete the topic itself
                   [[:db/retractEntity topic-id]])]
      (d/transact (get-conn) {:tx-data tx-data})
      (log/info "Topic" topic-id "and" (count vote-ids) "related votes deleted")
      {:success true})
    (catch Exception e
      (log/error "Failed to delete topic:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn get-topic-by-id
  "Get topic data by ID"
  [topic-id]
  (let [db (get-db)]
    (d/pull db '[:db/id :topic/title :topic/discussion-notes :topic/created-at
                 {:topic/created-by [:db/id :user/name]}
                 {:topic/meeting [:db/id :meeting/title]}]
            topic-id)))

(defn update-topic-discussion-notes!
  "Update discussion notes for a topic"
  [topic-id discussion-notes]
  (try
    (let [result (d/transact (get-conn) {:tx-data [{:db/id topic-id
                                                    :topic/discussion-notes discussion-notes}]})]
      (log/info "Discussion notes updated for topic" topic-id)
      {:success true})
    (catch Exception e
      (log/error "Failed to update topic discussion notes:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn start-meeting!
  "Set meeting status to 'started'"
  [meeting-id]
  (try
    (let [result (d/transact (get-conn) {:tx-data [{:db/id meeting-id
                                                    :meeting/status "started"}]})]
      (log/info "Meeting" meeting-id "started")
      {:success true})
    (catch Exception e
      (log/error "Failed to start meeting:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn add-action!
  "Add a new action to a meeting/topic"
  [meeting-id topic-id description assigned-to-user assigned-to-team deadline]
  (try
    (let [action-data (cond-> {:action/description description
                              :action/meeting meeting-id
                              :action/added-at (java.util.Date.)}
                        topic-id (assoc :action/topic topic-id)
                        assigned-to-user (assoc :action/assigned-to-user assigned-to-user)
                        assigned-to-team (assoc :action/assigned-to-team assigned-to-team)
                        deadline (assoc :action/deadline deadline))
          result (d/transact (get-conn) {:tx-data [action-data]})
          action-id (get-in result [:tempids (first (keys (:tempids result)))])]
      (log/info "Action added successfully:" action-data)
      {:success true :action-id action-id})
    (catch Exception e
      (log/error "Failed to add action:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn get-actions-for-meeting
  "Get all actions for a meeting with assignee information"
  [meeting-id]
  (let [db (get-db)
        ;; Get basic action info first
        basic-actions-result (d/q '[:find ?action ?description ?added-at
                                    :in $ ?meeting-id
                                    :where
                                    [?action :action/meeting ?meeting-id]
                                    [?action :action/description ?description]
                                    [?action :action/added-at ?added-at]]
                                  db meeting-id)]
    (mapv (fn [[action-id description added-at]]
            (let [;; Get deadline if exists
                  deadline-result (d/q '[:find ?deadline
                                         :in $ ?action-id
                                         :where [?action-id :action/deadline ?deadline]]
                                       db action-id)
                  deadline (ffirst deadline-result)

                  ;; Get user assignment if exists
                  user-result (d/q '[:find ?user ?user-name
                                     :in $ ?action-id
                                     :where
                                     [?action-id :action/assigned-to-user ?user]
                                     [?user :user/name ?user-name]]
                                   db action-id)
                  [assigned-user assigned-user-name] (first user-result)

                  ;; Get team assignment if exists
                  team-result (d/q '[:find ?team ?team-name
                                     :in $ ?action-id
                                     :where
                                     [?action-id :action/assigned-to-team ?team]
                                     [?team :team/name ?team-name]]
                                   db action-id)
                  [assigned-team assigned-team-name] (first team-result)

                  ;; Get completion status
                  completed-at-result (d/q '[:find ?completed-at
                                           :in $ ?action-id
                                           :where [?action-id :action/completed-at ?completed-at]]
                                         db action-id)
                  completed-at (ffirst completed-at-result)
                  rejected-at-result (d/q '[:find ?rejected-at
                                          :in $ ?action-id
                                          :where [?action-id :action/rejected-at ?rejected-at]]
                                        db action-id)
                  rejected-at (ffirst rejected-at-result)
                  ;; Get completion notes
                  completion-notes-result (d/q '[:find ?notes
                                               :in $ ?action-id
                                               :where [?action-id :action/completion-notes ?notes]]
                                             db action-id)
                  completion-notes (ffirst completion-notes-result)
                  status (cond
                           completed-at "completed"
                           rejected-at "rejected"
                           :else "pending")]

              {:id action-id
               :description description
               :deadline deadline
               :added-at added-at
               :assigned-to-user assigned-user
               :assigned-to-user-name assigned-user-name
               :assigned-to-team assigned-team
               :assigned-to-team-name assigned-team-name
               :is-team-action (some? assigned-team)
               :status status
               :completed-at completed-at
               :rejected-at rejected-at
               :completion-notes completion-notes}))
          basic-actions-result)))

(defn get-team-members-for-meeting
  "Get team members for the meeting team to populate assignee dropdown"
  [meeting-id]
  (let [db (get-db)
        team-members-result (d/q '[:find ?user ?user-name
                                   :in $ ?meeting-id
                                   :where
                                   [?meeting-id :meeting/team ?team]
                                   [?user :user/teams ?team]
                                   [?user :user/name ?user-name]
                                   [?user :user/active true]]
                                 db meeting-id)]
    (mapv (fn [[user-id user-name]]
            {:id user-id
             :name user-name})
          team-members-result)))

(defn finish-meeting!
  "Set meeting status to 'finished'"
  [meeting-id]
  (try
    (let [result (d/transact (get-conn) {:tx-data [{:db/id meeting-id
                                                    :meeting/status "finished"}]})]
      (log/info "Meeting" meeting-id "status set to finished")
      {:success true})
    (catch Exception e
      (log/error "Failed to finish meeting:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn finish-topic!
  "Set topic finished-at timestamp and clear meeting current-topic if this topic is current"
  [topic-id meeting-id]
  (try
    (let [db (get-db)
          ;; Check if this topic is the current topic for the meeting
          current-topic-result (d/q '[:find ?current-topic
                                     :in $ ?meeting-id
                                     :where [?meeting-id :meeting/current-topic ?current-topic]]
                                   db meeting-id)
          current-topic-id (ffirst current-topic-result)
          ;; Prepare transaction data
          tx-data (cond-> [{:db/id topic-id
                           :topic/finished-at (java.util.Date.)}]
                    ;; If this topic is current, clear it from meeting
                    (= current-topic-id topic-id)
                    (conj [:db/retract meeting-id :meeting/current-topic topic-id]))
          result (d/transact (get-conn) {:tx-data tx-data})]
      (if (= current-topic-id topic-id)
        (log/info "Topic" topic-id "finished and cleared as current topic from meeting" meeting-id)
        (log/info "Topic" topic-id "finished"))
      {:success true})
    (catch Exception e
      (log/error "Failed to finish topic:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn get-dashboard-statistics
  "Get dashboard statistics for a specific user"
  [user-id]
  (let [db (get-db)
        now (java.util.Date.)
        ;; Get first day of current month
        cal (java.util.Calendar/getInstance)
        _ (.setTime cal now)
        _ (.set cal java.util.Calendar/DAY_OF_MONTH 1)
        _ (.set cal java.util.Calendar/HOUR_OF_DAY 0)
        _ (.set cal java.util.Calendar/MINUTE 0)
        _ (.set cal java.util.Calendar/SECOND 0)
        _ (.set cal java.util.Calendar/MILLISECOND 0)
        month-start (.getTime cal)

        ;; Get user's teams
        user-teams-result (d/q '[:find ?team
                                :in $ ?user-id
                                :where [?user-id :user/teams ?team]]
                              db user-id)
        user-team-ids (mapv first user-teams-result)

        ;; Total meetings for user's teams
        total-meetings (if (seq user-team-ids)
                        (count (d/q '[:find ?meeting
                                     :in $ [?team-id ...]
                                     :where
                                     [?meeting :meeting/team ?team-id]]
                                   db user-team-ids))
                        0)

        ;; Meetings this month for user's teams
        meetings-this-month (if (seq user-team-ids)
                             (count (d/q '[:find ?meeting
                                          :in $ [?team-id ...] ?month-start
                                          :where
                                          [?meeting :meeting/team ?team-id]
                                          [?meeting :meeting/created-at ?created-at]
                                          [(>= ?created-at ?month-start)]]
                                        db user-team-ids month-start))
                             0)

        ;; Active actions assigned to user (both direct and team assignments)
        user-actions-count (count (d/q '[:find ?action
                                        :in $ ?user-id
                                        :where [?action :action/assigned-to-user ?user-id]]
                                      db user-id))

        team-actions-count (if (seq user-team-ids)
                            (count (d/q '[:find ?action
                                         :in $ [?team-id ...]
                                         :where [?action :action/assigned-to-team ?team-id]]
                                       db user-team-ids))
                            0)

        active-actions (+ user-actions-count team-actions-count)

        ;; Team members count (all members across user's teams)
        team-members-count (if (seq user-team-ids)
                            (count (d/q '[:find ?user
                                         :in $ [?team-id ...]
                                         :where
                                         [?user :user/teams ?team-id]
                                         [?user :user/active true]]
                                       db user-team-ids))
                            1)] ; At least the current user

    {:total-meetings total-meetings
     :active-actions active-actions
     :team-members team-members-count
     :meetings-this-month meetings-this-month}))

(defn- get-action-details
  "Get detailed information for an action (deadline, completion status, notes)"
  [db action-id]
  (let [deadline-result (d/q '[:find ?deadline
                              :in $ ?action-id
                              :where [?action-id :action/deadline ?deadline]]
                            db action-id)
        deadline (ffirst deadline-result)
        completed-at-result (d/q '[:find ?completed-at
                                 :in $ ?action-id
                                 :where [?action-id :action/completed-at ?completed-at]]
                               db action-id)
        completed-at (ffirst completed-at-result)
        rejected-at-result (d/q '[:find ?rejected-at
                                :in $ ?action-id
                                :where [?action-id :action/rejected-at ?rejected-at]]
                              db action-id)
        rejected-at (ffirst rejected-at-result)
        completion-notes-result (d/q '[:find ?notes
                                     :in $ ?action-id
                                     :where [?action-id :action/completion-notes ?notes]]
                                   db action-id)
        completion-notes (ffirst completion-notes-result)
        status (cond
                 completed-at "completed"
                 rejected-at "rejected"
                 :else "pending")]
    {:deadline deadline
     :completed-at completed-at
     :rejected-at rejected-at
     :completion-notes completion-notes
     :status status}))

(defn- process-user-action
  "Process a single user action (individual assignment)"
  [db [action-id description added-at meeting-id meeting-title]]
  (let [action-details (get-action-details db action-id)]
    (merge {:id action-id
            :description description
            :added-at added-at
            :meeting-id meeting-id
            :meeting-title meeting-title
            :is-team-action false
            :assignment-type "Individual"}
           action-details)))

(defn- process-team-action
  "Process a single team action with team name lookup"
  [db [action-id description added-at meeting-id meeting-title]]
  (let [team-name-result (d/q '[:find ?team-name
                               :in $ ?action-id
                               :where
                               [?action-id :action/assigned-to-team ?team]
                               [?team :team/name ?team-name]]
                             db action-id)
        team-name (ffirst team-name-result)
        action-details (get-action-details db action-id)]
    (merge {:id action-id
            :description description
            :added-at added-at
            :meeting-id meeting-id
            :meeting-title meeting-title
            :is-team-action true
            :assignment-type "Team"
            :team-name team-name}
           action-details)))

(defn get-user-actions
  "Get all actions assigned to a specific user across all meetings"
  [user-id]
  (let [db (get-db)
        ;; Get actions assigned directly to user
        user-actions-result (d/q '[:find ?action ?description ?added-at ?meeting ?meeting-title
                                   :in $ ?user-id
                                   :where
                                   [?action :action/assigned-to-user ?user-id]
                                   [?action :action/description ?description]
                                   [?action :action/added-at ?added-at]
                                   [?action :action/meeting ?meeting]
                                   [?meeting :meeting/title ?meeting-title]]
                                 db user-id)

        ;; Get actions assigned to user's teams
        user-teams-result (d/q '[:find ?team
                                :in $ ?user-id
                                :where [?user-id :user/teams ?team]]
                              db user-id)
        user-team-ids (mapv first user-teams-result)

        team-actions-result (if (seq user-team-ids)
                             (d/q '[:find ?action ?description ?added-at ?meeting ?meeting-title
                                    :in $ [?team-id ...]
                                    :where
                                    [?action :action/assigned-to-team ?team-id]
                                    [?action :action/description ?description]
                                    [?action :action/added-at ?added-at]
                                    [?action :action/meeting ?meeting]
                                    [?meeting :meeting/title ?meeting-title]]
                                  db user-team-ids)
                             [])

        ;; Process actions using helper functions
        processed-user-actions (mapv (partial process-user-action db) user-actions-result)
        processed-team-actions (mapv (partial process-team-action db) team-actions-result)]

    ;; Combine and sort all actions
    (sort-by :added-at #(compare %2 %1) (concat processed-user-actions processed-team-actions))))

(defn get-action-by-id
  "Get action by ID with all details"
  [action-id]
  (let [db (get-db)]
    (d/pull db '[:db/id
                 :action/description
                 :action/deadline
                 :action/added-at
                 :action/completed-at
                 :action/rejected-at
                 :action/completion-notes
                 {:action/assigned-to-user [:db/id :user/name]}
                 {:action/assigned-to-team [:db/id :team/name]}
                 {:action/meeting [:db/id :meeting/title]}]
            action-id)))

(defn get-user-action-by-id
  "Get action by ID only if it's assigned to the user or user's team (permission check)"
  [action-id user-id]
  (let [db (get-db)
        ;; Check if action is assigned directly to user
        user-action-result (d/q '[:find ?action-id
                                  :in $ ?action-id ?user-id
                                  :where
                                  [?action-id :action/assigned-to-user ?user-id]]
                                db action-id user-id)
        ;; Check if action is assigned to user's team
        team-action-result (d/q '[:find ?action-id
                                  :in $ ?action-id ?user-id
                                  :where
                                  [?user-id :user/teams ?team]
                                  [?action-id :action/assigned-to-team ?team]]
                                db action-id user-id)]
    (when (or (seq user-action-result) (seq team-action-result))
      (get-action-by-id action-id))))

(defn find-meeting-by-title
  "Find existing meeting by title, returns meeting ID or nil"
  [meeting-title]
  (let [db (get-db)
        result (d/q '[:find ?e
                      :in $ ?title
                      :where
                      [?e :meeting/title ?title]
                      [?e :meeting/status ?status]
                      [(not= ?status "finished")]]
                    db meeting-title)]
    (ffirst result)))

(defn update-action-status!
  "Update action status (complete or reject) with completion notes"
  [action-id status completion-notes]
  (let [now (java.util.Date.)
        tx-data (cond-> {:db/id action-id}
                  (= status "complete") (assoc :action/completed-at now)
                  (= status "reject") (assoc :action/rejected-at now)
                  (and completion-notes (not (clojure.string/blank? completion-notes)))
                  (assoc :action/completion-notes (clojure.string/trim completion-notes)))]
    (d/transact (get-conn) {:tx-data [tx-data]})))

(defn get-finished-meetings-for-user
  "Get all finished meetings for user's teams with topics and actions"
  [user-id is-admin?]
  (let [db (get-db)]
    (if is-admin?
      ;; Admin can see all finished meetings
      (let [basic-meetings-result (d/q '[:find ?meeting ?title ?scheduled-at ?created-by-name ?team-name
                                         :where
                                         [?meeting :meeting/status "finished"]
                                         [?meeting :meeting/title ?title]
                                         [?meeting :meeting/scheduled-at ?scheduled-at]
                                         [?meeting :meeting/created-by ?created-by]
                                         [?created-by :user/name ?created-by-name]
                                         [?meeting :meeting/team ?team]
                                         [?team :team/name ?team-name]]
                                       db)]
        (mapv (fn [[meeting-id title scheduled-at created-by-name team-name]]
                ;; Get description separately if it exists
                (let [description-result (d/q '[:find ?description
                                               :in $ ?meeting-id
                                               :where [?meeting-id :meeting/description ?description]]
                                             db meeting-id)
                      description (ffirst description-result)]
                  {:id meeting-id
                   :title title
                   :scheduled-at scheduled-at
                   :created-by-name created-by-name
                   :team-name team-name
                   :description description
                   :topics (get-topics-for-meeting meeting-id)
                   :actions (get-actions-for-meeting meeting-id)}))
              (sort-by #(nth % 2) #(compare %2 %1) basic-meetings-result)))

      ;; Regular users see only their team's finished meetings
      (let [user-teams-result (d/q '[:find ?team
                                     :in $ ?user-id
                                     :where [?user-id :user/teams ?team]]
                                   db user-id)
            user-team-ids (mapv first user-teams-result)]
        (if (seq user-team-ids)
          (let [basic-meetings-result (d/q '[:find ?meeting ?title ?scheduled-at ?created-by-name ?team-name
                                             :in $ [?team-id ...]
                                             :where
                                             [?meeting :meeting/status "finished"]
                                             [?meeting :meeting/team ?team-id]
                                             [?meeting :meeting/title ?title]
                                             [?meeting :meeting/scheduled-at ?scheduled-at]
                                             [?meeting :meeting/created-by ?created-by]
                                             [?created-by :user/name ?created-by-name]
                                             [?meeting :meeting/team ?team]
                                             [?team :team/name ?team-name]]
                                           db user-team-ids)]
            (mapv (fn [[meeting-id title scheduled-at created-by-name team-name]]
                    ;; Get description separately if it exists
                    (let [description-result (d/q '[:find ?description
                                                   :in $ ?meeting-id
                                                   :where [?meeting-id :meeting/description ?description]]
                                                 db meeting-id)
                          description (ffirst description-result)]
                      {:id meeting-id
                       :title title
                       :scheduled-at scheduled-at
                       :created-by-name created-by-name
                       :team-name team-name
                       :description description
                       :topics (get-topics-for-meeting meeting-id)
                       :actions (get-actions-for-meeting meeting-id)}))
                  (sort-by #(nth % 2) #(compare %2 %1) basic-meetings-result)))
          [])))))

(comment

  ;; Initialize database and schema
  (initialize-db!)

  ;; To add new schema fields to existing database:
  ;; 1. Add the field definition to the appropriate schema (e.g., user-schema)
  ;; 2. Transact the new schema to the existing database:
  ;; (d/transact (get-conn) {:tx-data [{:db/ident       :user/last-login
  ;;                                    :db/valueType   :db.type/instant
  ;;                                    :db/cardinality :db.cardinality/one
  ;;                                    :db/doc         "When the user last logged in."}]})

  ;; Example: Adding user/active field to existing database
  ;; 1. First, add the schema for the new field:
  (d/transact (get-conn) {:tx-data [{:db/ident       :user/active
                                     :db/valueType   :db.type/boolean
                                     :db/cardinality :db.cardinality/one
                                     :db/doc         "Whether the user is active."}]})

  ;; 2. Then, set all existing users to active by default:
  (let [db (get-db)
        all-user-ids (d/q '[:find ?e
                            :where [?e :user/name _]]
                          db)
        tx-data (map (fn [[user-id]]
                       {:db/id user-id
                        :user/active true})
                     all-user-ids)]
    (d/transact (get-conn) {:tx-data tx-data}))

  ;; Example: Making team names unique (migration)
  ;; 1. First, add the unique constraint to the schema:
  (d/transact (get-conn) {:tx-data [{:db/ident       :team/name
                                     :db/valueType   :db.type/string
                                     :db/cardinality :db.cardinality/one
                                     :db/unique      :db.unique/value
                                     :db/doc         "The name of the team."}]})

  ;; 2. Handle any existing duplicate team names (if needed):
  ;; Note: This step is only needed if you have existing teams with duplicate names
  ;; You may need to manually resolve duplicates before applying the unique constraint
  ;; (let [db (get-db)
  ;;       team-names (d/q '[:find ?name (count ?e)
  ;;                         :where [?e :team/name ?name]
  ;;                         :having [(> (count ?e) 1)]]
  ;;                       db)]
  ;;   (when (seq team-names)
  ;;     (println "Warning: Found duplicate team names that need manual resolution:")
  ;;     (doseq [[name count] team-names]
  ;;       (println (str "  '" name "' appears " count " times")))))

  ;; Query examples
  (def all-users-q '[:find ?user-name
                     :where [_ :user/name ?user-name]])

  (def all-teams-q '[:find ?team-name
                     :where [_ :team/name ?team-name]])

  (def all-meetings-q '[:find ?meeting-title
                        :where [_ :meeting/title ?meeting-title]])



  (d/q all-users-q (get-db))
  (d/q all-teams-q (get-db))
  (d/q all-meetings-q (get-db))

  (def query '[:find ?e ?name ?email
               :where
               [?e :user/name ?name]
               [?e :user/email ?email]])

  (get-all-users)

  (d/transact (get-conn) {:tx-data schema/participant-schema})

  ;; Test topic functions
  (add-topic! 12345 67890 "Test topic")
  (get-topics-for-meeting 12345)
  (add-vote! 67890 98765 "upvote")

  ;; Migration: Add votes_are_public field to meeting schema
  ;; 1. Add the schema for the new field:
  (d/transact (get-conn) {:tx-data [{:db/ident       :meeting/votes-are-public
                                     :db/valueType   :db.type/boolean
                                     :db/cardinality :db.cardinality/one
                                     :db/doc         "Whether votes on topics are publicly visible."}]})

  ;; 2. Set default value (false) for all existing meetings:
  (let [db (get-db)
        all-meeting-ids (d/q '[:find ?e
                               :where [?e :meeting/title _]]
                             db)
        tx-data (map (fn [[meeting-id]]
                       {:db/id meeting-id
                        :meeting/votes-are-public false})
                     all-meeting-ids)]
    (when (seq tx-data)
      (d/transact (get-conn) {:tx-data tx-data})))

  ;; Migration: Update existing meeting status values to match new schema
  ;; Note: Update status values from old format to new format if needed
  ;; Old: "scheduled" -> New: "new"
  ;; Old: "in-progress" -> New: "started"
  ;; Old: "completed"/"cancelled" -> New: "finished"
  (let [db (get-db)
        meetings-to-update (d/q '[:find ?e ?status
                                  :where
                                  [?e :meeting/status ?status]
                                  [(contains? #{"scheduled" "in-progress" "completed" "cancelled"} ?status)]]
                                db)
        tx-data (map (fn [[meeting-id old-status]]
                       {:db/id meeting-id
                        :meeting/status (case old-status
                                         "scheduled" "new"
                                         "in-progress" "started"
                                         "completed" "finished"
                                         "cancelled" "finished"
                                         old-status)})
                     meetings-to-update)]
    (when (seq tx-data)
      (d/transact (get-conn) {:tx-data tx-data})))

  (get-actions-for-meeting 96757023244418)
  )

(ns ok.hola-tact-meet.db
  (:require [datomic.client.api :as d]
            [ok.hola-tact-meet.schema :as schema]
            [malli.core]
            [clojure.tools.logging :as log]
            ))

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
                     :user/access-level (or (:access-level userinfo) "user")
                     :user/active (or (:active userinfo) true)}
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
  (let [{:keys [name description managers]} team-data]
    (log/info "ok-2025-07-06-1751810064")
    (log/info [name description managers])
    (try
      ;; Check if team name already exists
      (if (find-team-by-name name)
        {:success false :error (str "Team with name '" name "' already exists")}

        ;; Create the team
        (let [;; Validate that manager IDs exist in database
              valid-managers (validate-user-ids managers)
              ;; Create transaction data
              team-tx-data (cond-> {:team/name name
                                   :team/description (or description "")
                                   :team/created-at (java.util.Date.)}
                             (seq valid-managers) (assoc :team/managers valid-managers)
                             (first valid-managers) (assoc :team/created-by (first valid-managers)))
              _ (log/debug "Team transaction data:" team-tx-data)
              result (d/transact (get-conn) {:tx-data [team-tx-data]})
              team-id (get-in result [:tempids (first (keys (:tempids result)))])]
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
      (let [meetings-result (d/q '[:find ?meeting ?title ?created-at ?created-by-name
                                  :in $ [?team-id ...]
                                  :where
                                  [?meeting :meeting/team ?team-id]
                                  [?meeting :meeting/title ?title]
                                  [?meeting :meeting/created-at ?created-at]
                                  [?meeting :meeting/created-by ?created-by]
                                  [?created-by :user/name ?created-by-name]]
                                db user-team-ids)
            ;; Sort by created-at desc and take first 3
            sorted-meetings (->> meetings-result
                               (sort-by #(nth % 2) #(compare %2 %1))
                               (take 3))]
        (mapv (fn [[meeting-id title created-at created-by-name]]
                {:id meeting-id
                 :title title
                 :created-at created-at
                 :created-by-name created-by-name})
              sorted-meetings))
      [])))

(defn get-active-meetings-for-user
  "Get all active meetings for user's teams (future/now + is_visible=true)"
  [user-id]
  (let [db (get-db)
        now (java.util.Date.)
        ;; First get user's teams
        user-teams-result (d/q '[:find ?team
                                :in $ ?user-id
                                :where [?user-id :user/teams ?team]]
                              db user-id)
        user-team-ids (mapv first user-teams-result)]
    (if (seq user-team-ids)
      ;; Get active meetings for user's teams
      (let [meetings-result (d/q '[:find ?meeting ?title ?scheduled-at ?created-by-name ?join-url
                                  :in $ [?team-id ...] ?now
                                  :where
                                  [?meeting :meeting/team ?team-id]
                                  [?meeting :meeting/title ?title]
                                  [?meeting :meeting/scheduled-at ?scheduled-at]
                                  [?meeting :meeting/is-visible true]
                                  [?meeting :meeting/created-by ?created-by]
                                  [?created-by :user/name ?created-by-name]
                                  [?meeting :meeting/join-url ?join-url]
                                  [(>= ?scheduled-at ?now)]]
                                db user-team-ids now)
            ;; Sort by scheduled-at asc (earliest first)
            sorted-meetings (->> meetings-result
                               (sort-by #(nth % 2)))]
        (mapv (fn [[meeting-id title scheduled-at created-by-name join-url]]
                {:id meeting-id
                 :title title
                 :scheduled-at scheduled-at
                 :created-by-name created-by-name
                 :join-url join-url})
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
      )))


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
  )

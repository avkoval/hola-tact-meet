(ns ok.hola-tact-meet.db
  (:require [datomic.client.api :as d]
            [ok.hola-tact-meet.schema :as schema])
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
                                        :user/last-login :user/active] user-id)]
             {:id (:db/id user-data)
              :name (:user/name user-data)
              :email (:user/email user-data)
              :access-level (:user/access-level user-data)
              :picture (:user/picture user-data)
              :family-name (:user/family-name user-data)
              :given-name (:user/given-name user-data)
              :last-login (:user/last-login user-data)
              :active (:user/active user-data)}))
         user-ids)))

(defn get-user-by-id 
  "Get user data by their database ID"
  [user-id]

  (let [db (get-db)]
    (d/pull db '[:user/name :user/email :user/family-name 
                 :user/given-name :user/picture :user/auth-provider 
                 :user/access-level :user/active] 
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

  ;; Query examples
  (def all-users-q '[:find ?user-name
                     :where [_ :user/name ?user-name]])

  (d/q all-users-q (get-db))

  (def query '[:find ?e ?name ?email
               :where 
               [?e :user/name ?name]
               [?e :user/email ?email]])

  (get-all-users)
  )

(ns ok.hola-tact-meet.migration
  (:require [datomic.client.api :as d]
            [ok.hola-tact-meet.db :as db]
            [ok.hola-tact-meet.schema :as schema]
            [clojure.tools.logging :as log]))

(defn migrate-schema!
  "Apply new schema to existing database"
  []
  (let [conn (db/get-conn)]
    (log/info "Starting schema migration...")
    
    ;; Add new topic schema fields
    (d/transact conn {:tx-data [{:db/ident       :topic/discussion-notes
                                 :db/valueType   :db.type/string
                                 :db/cardinality :db.cardinality/one
                                 :db/doc         "Discussion notes for the topic."}]})
    
    ;; Add new action schema fields
    (d/transact conn {:tx-data [{:db/ident       :action/assigned-to-user
                                 :db/valueType   :db.type/ref
                                 :db/cardinality :db.cardinality/one
                                 :db/doc         "User assigned to this action item."}
                                
                                {:db/ident       :action/assigned-to-team
                                 :db/valueType   :db.type/ref
                                 :db/cardinality :db.cardinality/one
                                 :db/doc         "Team assigned to this action item."}
                                
                                {:db/ident       :action/deadline
                                 :db/valueType   :db.type/instant
                                 :db/cardinality :db.cardinality/one
                                 :db/doc         "Optional deadline for the action item."}
                                
                                {:db/ident       :action/added-at
                                 :db/valueType   :db.type/instant
                                 :db/cardinality :db.cardinality/one
                                 :db/doc         "When the action item was added."}
                                
                                {:db/ident       :action/rejected-at
                                 :db/valueType   :db.type/instant
                                 :db/cardinality :db.cardinality/one
                                 :db/doc         "When the action item was rejected."}]})
    
    ;; Add new vote schema fields
    (d/transact conn {:tx-data [{:db/ident       :vote/type
                                 :db/valueType   :db.type/string
                                 :db/cardinality :db.cardinality/one
                                 :db/doc         "Type of vote: upvote or downvote."}]})
    
    ;; Add unique vote constraint
    (d/transact conn {:tx-data schema/vote-unique-schema})
    
    (log/info "Schema migration completed successfully")))

(defn migrate-existing-data!
  "Migrate existing data to match new schema"
  []
  (let [conn (db/get-conn)
        db (d/db conn)]
    (log/info "Starting data migration...")
    
    ;; Migrate existing actions to use new timestamp fields
    (let [existing-actions (d/q '[:find ?e ?created-at
                                  :where
                                  [?e :action/created-at ?created-at]]
                                db)
          migration-data (mapv (fn [[action-id created-at]]
                                 {:db/id action-id
                                  :action/added-at created-at})
                               existing-actions)]
      (when (seq migration-data)
        (d/transact conn {:tx-data migration-data})))
    
    ;; Set default vote type for existing votes
    (let [existing-votes (d/q '[:find ?e
                                :where
                                [?e :vote/user _]
                                (not [?e :vote/type _])]
                              db)
          migration-data (mapv (fn [[vote-id]]
                                 {:db/id vote-id
                                  :vote/type "upvote"})
                               existing-votes)]
      (when (seq migration-data)
        (d/transact conn {:tx-data migration-data})))
    
    (log/info "Data migration completed successfully")))

(defn full-migration!
  "Run complete migration"
  []
  (migrate-schema!)
  (migrate-existing-data!))

(comment
  ;; Run migration
  (full-migration!)
  )
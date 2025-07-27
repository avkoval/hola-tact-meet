(ns ok.hola-tact-meet.schema)

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

                  {:db/ident       :user/active
                   :db/valueType   :db.type/boolean
                   :db/cardinality :db.cardinality/one
                   :db/doc         "Whether the user is active."}

                  ])

(def team-schema [{:db/ident       :team/name
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/unique      :db.unique/value
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

                  {:db/ident       :team/auto-domains
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "Newline-separated list of email domains for auto-assignment to this team."}

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
                      :db/doc         "Status: new, started, finished."}

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

                     {:db/ident       :meeting/votes-are-public
                      :db/valueType   :db.type/boolean
                      :db/cardinality :db.cardinality/one
                      :db/doc         "Whether votes on topics are publicly visible."}

                     {:db/ident       :meeting/current-topic
                      :db/valueType   :db.type/ref
                      :db/cardinality :db.cardinality/one
                      :db/doc         "The current active topic being discussed in the meeting."}

                     ])

(def topic-schema [{:db/ident       :topic/title
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "The title of the topic/tension (max 250 chars)."}

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

                   {:db/ident       :topic/discussion-notes
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "Discussion notes for the topic."}

                   ])

(def action-item-schema [{:db/ident       :action/description
                          :db/valueType   :db.type/string
                          :db/cardinality :db.cardinality/one
                          :db/doc         "Description of the action item."}

                         {:db/ident       :action/topic
                          :db/valueType   :db.type/ref
                          :db/cardinality :db.cardinality/one
                          :db/doc         "Topic this action item came from."}

                         {:db/ident       :action/meeting
                          :db/valueType   :db.type/ref
                          :db/cardinality :db.cardinality/one
                          :db/doc         "Meeting this action item was created in."}

                         {:db/ident       :action/assigned-to-user
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

                         {:db/ident       :action/completed-at
                          :db/valueType   :db.type/instant
                          :db/cardinality :db.cardinality/one
                          :db/doc         "When the action item was completed."}

                         {:db/ident       :action/rejected-at
                          :db/valueType   :db.type/instant
                          :db/cardinality :db.cardinality/one
                          :db/doc         "When the action item was rejected."}

                         ])

(def vote-schema [{:db/ident       :vote/topic
                   :db/valueType   :db.type/ref
                   :db/cardinality :db.cardinality/one
                   :db/doc         "The topic being voted on."}

                  {:db/ident       :vote/user
                   :db/valueType   :db.type/ref
                   :db/cardinality :db.cardinality/one
                   :db/doc         "User who cast the vote."}

                  {:db/ident       :vote/type
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "Type of vote: upvote or downvote."}

                  {:db/ident       :vote/created-at
                   :db/valueType   :db.type/instant
                   :db/cardinality :db.cardinality/one
                   :db/doc         "When the vote was cast."}

                  ])

(def participant-schema [{:db/ident       :participant/user
                          :db/valueType   :db.type/ref
                          :db/cardinality :db.cardinality/one
                          :db/doc         "User who joined the meeting."}

                         {:db/ident       :participant/meeting
                          :db/valueType   :db.type/ref
                          :db/cardinality :db.cardinality/one
                          :db/doc         "Meeting the user joined."}

                         {:db/ident       :participant/joined-at
                          :db/valueType   :db.type/instant
                          :db/cardinality :db.cardinality/one
                          :db/doc         "When the user joined the meeting."}

                         ])

;; Ensure unique vote per user per topic
(def vote-unique-schema [{:db/ident       :vote/user-topic
                          :db/valueType   :db.type/tuple
                          :db/tupleAttrs  [:vote/user :vote/topic]
                          :db/cardinality :db.cardinality/one
                          :db/unique      :db.unique/identity
                          :db/doc         "Ensures one vote per user per topic."}])

(def all-schema (concat user-schema
                        team-schema
                        meeting-schema
                        topic-schema
                        action-item-schema
                        vote-schema
                        vote-unique-schema
                        participant-schema))

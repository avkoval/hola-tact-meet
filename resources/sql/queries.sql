-- :name create-user! :! :n
-- :doc creates a new user record
INSERT INTO users
(email)
VALUES (:email)

-- :name update-user! :! :n
-- :doc updates an existing user record
UPDATE users
SET email = :email
WHERE id = :id

-- :name get-user :? :1
-- :doc retrieves a user record given the id
SELECT * FROM users
WHERE
--~ (when-not (nil? (:id params)) "id = :id")
--~ (when-not (nil? (:email params)) "email = :email")

-- :name get-users :? :*
-- :doc retrieves all users
SELECT * FROM users

-- :name delete-user! :! :n
-- :doc deletes a user record given the id
DELETE FROM users
WHERE id = :id

-- :name create-team! :! :n
-- :doc creates a new team record
INSERT INTO team
(name, description)
VALUES (:name, :description)

-- :name update-team! :! :n
-- :doc updates an existing team record
UPDATE team
SET 
 name = :name,
 description = :description
WHERE id = :id

-- :name archive-team! :! :n
-- :doc archives an existing team record
UPDATE team
SET 
 archived = CURRENT_TIMESTAMP
WHERE id = :id

-- :name unarchive-team! :! :n
-- :doc unarchives an existing team record
UPDATE team
SET 
 archived = null
WHERE id = :id

-- :name get-team :? :1
-- :doc retrieves a team record given the id
SELECT * FROM team
WHERE id = :id

-- :name count-teams :? :1
-- :doc count team records
SELECT count(*) as cnt FROM team

-- :name get-teams :? :*
-- :doc retrieves a team record given the id
SELECT * FROM team

-- :name delete-team! :! :n
-- :doc deletes a team record given the id
DELETE FROM team
WHERE id = :id

-- :name join-team! :! :n
-- :doc creates a new team record
INSERT INTO user_team
(user_id, team_id)
VALUES (:user_id, :team_id)

-- :name get-user-team :? :1
-- :doc retrieves a team record given the id
SELECT * FROM user_team
WHERE id = :id

-- :name get-users-teams :? :*
-- :doc retrieves a users and teams records given the user_id or team_id
SELECT users.id as user_id, users.email as user_email, team.name as team_name, team.created as team_created, team.archived as team_archived, team.id as team_id
FROM user_team
LEFT JOIN users ON users.id = user_team.user_id
LEFT JOIN team ON team.id = user_team.team_id
WHERE
--~ (if (nil? (:user_id params)) "team_id = :team_id" "user_id = :user_id")


-- :name delete-user-team! :! :n
-- :doc deletes a team record given the id
/* :require [clojure.string :as s] */
DELETE FROM user_team
/*~
(let [params (filter (comp some? val) params)]
  (when (not (empty? params))
    (str "WHERE "
      (s/join " AND "
              (for [[field _] params]
                (str (name field) " = " field))))))
~*/

-- :name insert-log! :! :n
-- :doc creates a new user record
INSERT INTO system_log
(added_by, stamp, data)
VALUES (:added_by, :stamp, :data)

-- :name teams-and-user :? :*
-- :doc Get team name and membership status for user by email
select
  team.*,
  (select
    joined_at
   from user_team
   join users on users.id=user_id
   where
     team_id=team.id
     and email=:email
   ) as joined_at,
   (select count(*) from meeting) as meetings_count
from team
  where
    team.archived is NULL;

-- :name create-meeting! :! :n
-- :doc creates a new meeting record
INSERT INTO meeting
(agenda, description, scheduled_to, duration, added_by)
VALUES (:agenda, :description, :scheduled_to, :duration, :added_by)

-- :name get-meetings :? :*
-- :doc retrieves meetings
SELECT * FROM meeting

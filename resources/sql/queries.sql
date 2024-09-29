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
WHERE id = :id

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
(name)
VALUES (:name)

-- :name update-team! :! :n
-- :doc updates an existing team record
UPDATE team
SET name = :name
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

-- :name create-user-team! :! :n
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
DELETE FROM user_team
WHERE 
--~ (cond (not (nil? (:user_id params))) "user_id = :user_id" (not (nil? (:team_id params))) "team_id = :team_id" :else "id = :id")

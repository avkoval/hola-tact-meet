CREATE TABLE users
(id INTEGER PRIMARY KEY,
email VARCHAR(128)
);
--;;
CREATE TABLE team
(id INTEGER PRIMARY KEY,
name VARCHAR(128),
archived TIMESTAMP,
created TIMESTAMP
);
--;;
CREATE TABLE user_team
(id INTEGER PRIMARY KEY,
user_id INT,
team_id INT,
FOREIGN KEY (user_id) REFERENCES users(id),
FOREIGN KEY (team_id) REFERENCES team(id)
);

CREATE TABLE users
(id INTEGER PRIMARY KEY AUTOINCREMENT,
email VARCHAR(128)
);
--;;
CREATE TABLE team
(id INTEGER PRIMARY KEY AUTOINCREMENT,
name VARCHAR(128),
archived  TIMESTAMP WITH TIME ZONE,
created  TIMESTAMP WITH TIME ZONE
);
--;;
CREATE TABLE user_team
(id INTEGER PRIMARY KEY AUTOINCREMENT,
user_id INT,
team_id INT,
joined_at TIMESTAMP WITH TIME ZONE DEFAULT current_timestamp,
FOREIGN KEY (user_id) REFERENCES users(id),
FOREIGN KEY (team_id) REFERENCES team(id)
);
--;;
CREATE TABLE meeting
(id INTEGER PRIMARY KEY AUTOINCREMENT,
agenda varchar(128),
scheduled_to TIMESTAMP WITH TIME ZONE,
started_at TIMESTAMP WITH TIME ZONE,
finished_at TIMESTAMP WITH TIME ZONE,
added_by INT,
FOREIGN KEY (added_by) REFERENCES users(id)
);
--;;
CREATE TABLE meeting_participant
(id INTEGER PRIMARY KEY AUTOINCREMENT,
meeting_id INT,
user_id INT,
joined_at TIMESTAMP WITH TIME ZONE,
FOREIGN KEY (user_id) REFERENCES users(id),
FOREIGN KEY (meeting_id) REFERENCES meeting(id)
);
--;;
CREATE TABLE meeting_topic
(id INTEGER PRIMARY KEY AUTOINCREMENT,
meeting_id INT,
added_by INT,
added_at TIMESTAMP WITH TIME ZONE,
topic VARCHAR(128),
FOREIGN KEY (added_by) REFERENCES users(id),
FOREIGN KEY (meeting_id) REFERENCES meeting(id)
);
--;;
CREATE TABLE meeting_action
(id INTEGER PRIMARY KEY AUTOINCREMENT,
meeting_id INT,
added_by INT,
assigned_to INT,
topic INT,
todo TEXT,
completed_at TIMESTAMP WITH TIME ZONE,
FOREIGN KEY (added_by) REFERENCES users(id),
FOREIGN KEY (assigned_to) REFERENCES users(id),
FOREIGN KEY (meeting_id) REFERENCES meeting(id)
);
--;;
CREATE TABLE system_log
(id INTEGER PRIMARY KEY AUTOINCREMENT,
added_by INT,
stamp TIMESTAMP WITH TIME ZONE,
data TEXT,
FOREIGN KEY (added_by) REFERENCES users(id)
);

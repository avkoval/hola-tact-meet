(ns hola-tact-meet.db.core-test
  (:require
   [hola-tact-meet.db.core :refer [*db*] :as db]
   [java-time.pre-java8]
   [java-time.api :as jt]
   [luminus-migrations.core :as migrations]
   [clojure.test :refer [deftest use-fixtures is]]
   [next.jdbc :as jdbc]
   [hola-tact-meet.config :refer [env]]
   [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'hola-tact-meet.config/env
     #'hola-tact-meet.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest test-users
  (jdbc/with-transaction [t-conn *db* {:rollback-only true}]

    (is (= 1 (db/create-user!
              t-conn
              {:email      "sam.smith@example.com"}
              {})))

    (is (= {:id         2
            :email      "sam.smith@example.com"}
           (db/get-user t-conn {:id 2} {})))

    (is (= 1 (db/update-user! t-conn {:id 1 :email "alex@smith.kharkov.ua"} {})))

    (is (= {:id         1
            :email      "alex@smith.kharkov.ua"}
           (db/get-user t-conn {:id "1"} {})))

    (is (= 1 (db/delete-user! t-conn {:id 1} {})))
    ))


(deftest test-team
  (jdbc/with-transaction [t-conn *db* {:rollback-only true}]

    (is (= 1 (db/create-team!
              t-conn
              {:name      "Team A"}
              {})))

    (is (= 1 (db/create-team!
              t-conn
              {:name      "Team B"}
              {})))

    (is (= {:id         1
            :name      "Team A"
            :archived nil
            :created nil}
           (db/get-team t-conn {:id "1"} {})))

    (is (= [{:id         1
             :name      "Team A"
             :archived nil
             :created nil}
            {:id         2
             :name      "Team B"
             :archived nil
             :created nil}]
           (db/get-teams t-conn {:id 1} {})))

    (is (= 1 (db/update-team! t-conn {:id 2 :name "Team B-1"} {})))

    (is (= {:id         2
            :name      "Team B-1"
            :archived nil
            :created nil}
           (db/get-team t-conn {:id 2} {})))

    (is (= {:cnt 2} (db/count-teams t-conn {} {})))
    (is (= 1 (db/delete-team! t-conn {:id 2} {})))
    (is (= {:cnt 1} (db/count-teams t-conn {} {})))
    ))


(deftest test-users-teams
  (jdbc/with-transaction [t-conn *db* {:rollback-only true}]

    (is (= 1 (db/create-user!
              t-conn
              {:email      "sam.smith@example.com"}
              {})))


    (is (= 1 (db/create-team!
              t-conn
              {:name      "Team A"}
              {})))

    (is (= 1 (db/join-team!
              t-conn
              {:user_id      2
               :team_id      1}
              {})))

    (is (= {:id              1
            :user_id         2
            :team_id         1
            :joined_at       nil}
           (db/get-user-team t-conn {:id 1} {})))

    (is (= [{:user_id         2
             :user_email      "sam.smith@example.com"
             :team_name       "Team A"
             :team_created    nil
             :team_archived   nil
             :team_id         1
             }]
           (db/get-users-teams t-conn {:user_id 2} {})))

    (is (= [{:user_id         2
             :user_email      "sam.smith@example.com"
             :team_name       "Team A"
             :team_created    nil
             :team_archived   nil
             :team_id         1
             }]
           (db/get-users-teams t-conn {:team_id 1} {})))

    (is (= [{:id 1 :name "Team A", :joined_at nil}]
           (db/teams-and-user t-conn {:email "sam.smith@example.com"} {})))

    (is (= 1 (db/delete-user-team! t-conn {:id 1} {})))
    ))

(deftest test-system-log
  (jdbc/with-transaction [t-conn *db* {:rollback-only true}]

    (is (= 1 (db/create-user!
              t-conn
              {:email      "sam.smith@example.com"}
              {})))

    (is (= 1 (db/insert-log!
              t-conn
              {:added_by      1
               :stamp (jt/local-date-time)
               :data "{'test': true}"
               }
              {})))
    ))

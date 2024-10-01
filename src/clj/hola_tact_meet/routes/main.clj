(ns hola-tact-meet.routes.main
  (:require
   [hola-tact-meet.layout :as layout]
   [hola-tact-meet.db.core :as db]
   [clojure.java.io :as io]
   [hola-tact-meet.middleware :as middleware]
   [ring.util.response]
   [clojure.pprint]
   [java-time.api :as jt]
   [ring.util.http-response :as response]))

(defn get-or-add-user [email]
  (let [user (db/get-user {:email email})]
    (if (nil? user)
      (do
        (db/create-user! {:email email})
        (db/get-user {:email email})
        )
      user
      )))

(defn home-page [request]
  (let [headers (:headers request)
        user-email (get headers "ngrok-auth-user-email")
        user (get-or-add-user user-email)
        user-id (:id user)
        ]
    (if (nil? user-email)
      (layout/render request "error.html"
                     {:title "Authentication error" :message "User is not recognized"})
      (layout/render request "home.html"
                     {:user-email user-email :teams (db/teams-and-user {:email user-email})})
      )
  ))

(defn about-page [request]
  (layout/render request "about.html" {:docs (-> "docs/process.md" io/resource slurp)}))

(defn config-page [request]
  (layout/render request "config.html" {}))

(defn teams-page [request]
  (layout/render request "teams.html" {}))

(defn add-team [request]
  ;; (clojure.pprint/pprint request)
  (if (= :post (:request-method request))
    (do
      (db/create-team! (select-keys (:params request) [:name]))
      {:status 200
       :headers {"HX-Redirect" "/"}
       :body ""})
    (layout/render request "team/add.html" {}))
  )


(defn join-team [request]
  ;; (clojure.pprint/pprint request)
  (let [headers (:headers request)
        user-email (get headers "ngrok-auth-user-email")
        user (get-or-add-user user-email)
        user-id (:id user)
        ]
    (db/join-team! {:user_id user-id :team_id (:team_id (:params request))}))
  (layout/render request "team/joined.html" {:team {:joined_at (jt/local-date-time)}})
  )


(defn main-routes []
  [""
   {:middleware [middleware/wrap-csrf
;;                 middleware/wrap-ngrok-auth-middleware
                 middleware/wrap-formats
                 ]}
   ["/" {:get home-page}]
   ["/about" {:get about-page}]
   ["/config" {:get config-page}]
   ["/team/add" {:get add-team :post add-team}]
   ["/team/join" {:post join-team}]
])

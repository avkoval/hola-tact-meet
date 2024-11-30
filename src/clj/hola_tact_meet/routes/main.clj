(ns hola-tact-meet.routes.main
  (:require
   [hola-tact-meet.layout :as layout]
   [hola-tact-meet.db.core :as db]
   [clojure.java.io :as io]
   [hola-tact-meet.middleware :as middleware]
   [ring.util.response]
   [clojure.pprint]
   [java-time.api :as jt]
   [ring.util.http-response :as response]
   [struct.core :as st]))

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
                     {:user-email user-email
                      :teams (db/teams-and-user {:email user-email})
                      :meetings (db/get-meetings)})
      )
  ))

(defn about-page [request]
  (layout/render request "about.html" {:docs (-> "docs/process.md" io/resource slurp)}))

(defn config-page [request]
  (layout/render request "config.html" {}))

(defn teams-page [request]
  (layout/render request "teams.html" {}))

(def team-schema
  [[:name
    st/required
    st/string]
   [:description
    st/string]]
)

(defn validate-team-params [params]
  (first (st/validate params team-schema)))

(defn add-team [request]
  ;; (clojure.pprint/pprint request)

  (if (= :post (:request-method request))
    (let [params (:params request)
          errors (validate-team-params params)]
      (println errors)
      (if errors
        (layout/render request "team/add.html" (assoc params :errors errors))
        (do
          (db/create-team! (select-keys (:params request) [:name :description]))
          {:status 200
           :headers {"HX-Redirect" "/"}
           :body ""})))
    (layout/render request "team/add.html" {}))
  )

(def meeting-schema
  [[:scheduled_to
    st/required
    st/string]
   [:description
    st/string]]
)

(defn validate-meeting-params [params]
  (first (st/validate params meeting-schema)))

(defn add-meeting [request]
  ;; (clojure.pprint/pprint request)
  (let [headers (:headers request)
        user-email (get headers "ngrok-auth-user-email")
        user (get-or-add-user user-email)
        user-id (:id user)]
    (if (= :post (:request-method request))
      (do
        (db/create-meeting!
         (merge {:added_by user-id}
                (select-keys (:params request)
                             [:agenda :scheduled_to :description :duration :added_by])))
        {:status 200
         :headers {"HX-Redirect" "/"}
         :body ""})
      (layout/render request "meeting/add.html" {})))
  )

(defn edit-team [request]
  (let [headers (:headers request)
        user-email (get headers "ngrok-auth-user-email")
        user (get-or-add-user user-email)
        user-id (:id user)
        request-params (:params request)
        team_id (:team_id request-params)
        team (db/get-team {:id team_id})
        ]
    (if (= :post (:request-method request))
      (do
        (db/update-team! (select-keys (:params request) [:id :name :description]))
        {:status 200
         :headers {"HX-Redirect" "/"}
         :body ""})
      (layout/render request "team/edit.html" {:team team})))
  )


(defn archive-team [request]
  ;; (clojure.pprint/pprint request)
  (when (= :post (:request-method request))
    (db/archive-team! (select-keys (:params request) [:id]))
    )
  {:status 200
   :headers {"HX-Redirect" "/"}
   :body ""}
  )


(defn leave-team [request]
  ;; (clojure.pprint/pprint request)
  (let [headers (:headers request)
        user-email (get headers "ngrok-auth-user-email")
        user (get-or-add-user user-email)
        user-id (:id user)
        team_id (:team_id (:params request))
        team (db/get-team {:id team_id})
        ]
    (db/delete-user-team! {:user_id user-id :team_id team_id})
    (layout/render request "team/leave.html" {:team team}))
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
   ["/team/edit" {:get edit-team :post edit-team}]
   ["/team/archive" {:post archive-team}]
   ["/team/join" {:post join-team}]
   ["/team/leave" {:post leave-team}]
   ["/meeting/add" {:get add-meeting :post add-meeting}]
])

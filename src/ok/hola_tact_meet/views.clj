(ns ok.hola-tact-meet.views
  (:require
   [ring.util.response :as response]
   [selmer.parser :refer [render-file] :as selmer]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.ring :refer [->sse-response on-open]]
   [ok.hola_tact_meet.utils :as utils]
   [ok.hola_tact_meet.db :as db]
   [clojure.java.io]
   [ok.oauth2.utils :refer [get-oauth-config]]
   [clojure.tools.logging :as log]
   [faker.generate :as gen]
   [datomic.client.api :as d]
   )
  (:gen-class))



(defn home [{session :session :as request}]
  (let [oauth2-config (get-oauth-config (utils/app-config))
        remote-addr (:remote-addr request)
        dev_mode (utils/localhost? remote-addr)
        ]
    
    (log/info "Home page accessed from" remote-addr "dev_mode: " dev_mode)
    (if (get-in session [:userinfo :logged-in])
      (response/redirect "/app")
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (render-file "templates/home.html" {:google_client_id (get-in oauth2-config [:google :client-id])
                                                 :google_launch_uri (get-in oauth2-config [:google :launch-uri])
                                                 :google_login_uri "/google-login"
                                                 :host (get-in request [:headers "host"])
                                                 :dev_mode dev_mode})})))

(defn app-main [{session :session}]
  (log/info "Main app page accessed")
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (render-file "templates/main.html" {:userinfo (:userinfo session)})})

(defn admin-manage-users [{session :session}]
  (let [users (db/get-all-users)
        userinfo (:userinfo session)]
    (log/info "admin-manage-users accessed")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/users.html" {:users users :userinfo userinfo})}))

(defn admin-update-user-access-level [request]
  (->sse-response 
   request
   {on-open
    (fn [sse]
      ;; (pprint request)
      (let [user_id (get-in request [:query-params "user_id"])
            params (utils/datastar-query request)
            new_access_level (get params (keyword (str "accessLevel" user_id)))]
        (println params)
        (println new_access_level)
        (when (and user_id new_access_level)
          (d/transact (db/get-conn) {:tx-data [{:db/id (Long/parseLong user_id)
                                                :user/access-level new_access_level}]}))
        (d*/with-open-sse sse
          (d*/merge-fragment! sse (render-file "templates/users_list.html" {:users (db/get-all-users)})))))}))

(defn test-session [{session :session}]
  (let [count   (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response/response (str "You accessed this page " count " times."))
        (assoc :session session))))

(defn google-login [{session :session :as request}]
  (let [login-count   (:login-count session 0)
        session (assoc session :count (inc login-count))]
    (log/info "Google login attempt from" (:remote-addr request))
    (-> (response/response (str "Logged IN. " login-count " times."))
        (assoc :session session))))


(defn login-user-by-id 
  "Log in user by their database ID"
  [{session :session} user-id userinfo]

  (let [updated-session (assoc session :userinfo (assoc userinfo 
                                                        :logged-in true
                                                        :user-id user-id))]
    (db/update-last-login! user-id)
    (log/info "User logged in:" (:email userinfo) "ID:" user-id)
    (-> (response/redirect "/app")
        (assoc :session updated-session))))

(defn login 
  "Main login function that creates user and logs them in"
  [request userinfo]
  (let [user-id (db/create-user userinfo)]
    (log/info "User created:" (:email userinfo) "ID:" user-id)
    (login-user-by-id request user-id userinfo)))

(defn fake-login-page [request]
  (let [users (db/get-all-users)]
    (log/info "Fake login page accessed from" (:remote-addr request))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/fake.html" {:users users})}))

(defn fake-login-existing [request]
  (let [user-id-str (get-in request [:params "user-id"])
        user-id (Long/parseLong user-id-str)
        user-data (db/get-user-by-id user-id)
        userinfo {:name (:user/name user-data)
                  :email (:user/email user-data)
                  :family-name (:user/family-name user-data)
                  :given-name (:user/given-name user-data)
                  :picture (:user/picture user-data)
                  :auth-provider "fake"
                  :access-level (:user/access-level user-data)}]
    (log/info "Fake login as existing user:" (:email userinfo))
    (login-user-by-id request user-id userinfo)))

(defn fake-login-new [request]
  (let [params (:params request)
        fake-userinfo {:name (get params "name")
                       :email (get params "email")
                       :family-name (get params "family-name")
                       :given-name (get params "given-name")
                       :picture ""
                       :auth-provider "fake"
                       :access-level (get params "access-level")}]
    (log/info "Creating new fake user:" (:email fake-userinfo))
    (login request fake-userinfo)))


(defn fake-user-data []
  {:userinfo 
   {:name (gen/word)
    :email (utils/gen-email)
    :family-name (gen/word)
    :given-name (gen/word)
    :picture ""
    :auth-provider "fake"
    :access-level (rand-nth ["admin" "user" "staff"])}}
  )


(defn fake-generate-random-data [request]
  (->sse-response request
                  {on-open
                   (fn [sse]
                     (d*/with-open-sse sse
                       (d*/merge-fragment! sse
                        (render-file "templates/fake-user-form.html" (fake-user-data)))
                       ))}))


(defn logout [_]
  (-> (response/redirect "/")
      (assoc :session {})))

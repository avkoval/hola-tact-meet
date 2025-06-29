(ns ok.hola-tact-meet.core
  (:require
   [nrepl.server :as nrepl-server]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as response]
   [ring.middleware.session :refer [wrap-session]]
   [reitit.ring :as ring]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [selmer.parser :refer [render-file] :as selmer]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.ring :refer [->sse-response]]
   [ok.hola_tact_meet.utils :as utils]
   [ok.hola_tact_meet.db :as db]
   [aero.core :refer [read-config]]
   [clojure.java.io]
   ;; [clojure.pprint :refer [pprint]]
   [ok.oauth2.utils :refer [get-oauth-config]]
   [ok.session.utils :refer [encode-secret-key]]
   [ring.middleware.oauth2 :refer [wrap-oauth2]]
   [clojure.tools.logging :as log]
   [faker.generate :as gen]
   )
  (:gen-class))

(defn app-config []
  (read-config (clojure.java.io/resource "config.edn")))

(defn home [{session :session :as request}]
  (let [oauth2-config (get-oauth-config (app-config))
        remote-addr (:remote-addr request)
        dev_mode (= "127.0.0.1" remote-addr)
        ]
    
    (log/info "Home page accessed from" remote-addr)
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
  {:name (gen/word)
   :email (utils/gen-email)
   :family-name (gen/word)
   :given-name (gen/word)
   :picture ""
   :auth-provider "fake"
   :access-level (rand-nth ["admin" "user" "staff"])}
  )


(defn fake-generate-random-data [request]
  (->sse-response request
                  {:on-open (fn [sse] (d*/merge-fragment! sse (render-file "templates/fake-user-form.html" (fake-user-data))))}))

(defn logout [_]
  (-> (response/redirect "/")
      (assoc :session {})))

(defn wrap-request-logging [handler]
  (fn [request]
    (let [start-time (System/currentTimeMillis)
          response (handler request)
          duration (- (System/currentTimeMillis) start-time)]
      (log/info (format "%s %s %d (%dms)"
                        (:request-method request)
                        (:uri request)
                        (:status response)
                        duration))
      response)))

(defn wrap-require-auth [handler]
  (fn [request]
    (if (get-in request [:session :userinfo :logged-in])
      (handler request)
      (response/redirect "/"))))

(defn wrap-localhost-only [handler]
  (fn [request]
    (let [remote-addr (:remote-addr request)]
      (if (or (= remote-addr "127.0.0.1")
              (= remote-addr "::1")
              (= remote-addr "localhost"))
        (handler request)
        {:status 403
         :headers {"Content-Type" "text/plain"}
         :body "Access denied. This endpoint is only available from localhost."}))))

(defn wrap-force-https [handler]
  (fn [request]
    (let [proto (get-in request [:headers "x-forwarded-proto"])
          request' (if (= proto "https")
                     (assoc request :scheme :https)
                     request)]
      (handler request'))))

(defn my-wrap-oauth2 [base-handler]
  (let [config (get-oauth-config (app-config))]
    (wrap-oauth2 base-handler config)))


(def base-app
  (ring/ring-handler
   (ring/router
    [
     ["/" {:get home}]
     ["/favicon.ico" {:get (fn [_] (response/file-response "resources/public/favicon.ico"))}]
     ["/assets/*" (ring/create-resource-handler)]
     ["/app" {:get (wrap-require-auth app-main)}]
     ["/test-session" {:get test-session}]
     ["/google-login" {:post google-login}]
     ["/login/fake" {:get (wrap-localhost-only fake-login-page)}]
     ["/login/fake/existing" {:post (wrap-localhost-only fake-login-existing)}]
     ["/login/fake/new" {:post (wrap-localhost-only fake-login-new)}]
     ["/login/fake/generate-random-data" {:get (wrap-localhost-only fake-generate-random-data)}]
     ["/logout" {:get logout}]
     ])
   (constantly {:status 404, :body "Not Found."})))


(defn create-app []
  (let [secret-key (encode-secret-key (:secret-key (app-config)))]
    (-> base-app
        utils/wrap-json-params
        wrap-params
        my-wrap-oauth2
        (wrap-session {:store (cookie-store {:key secret-key})
                       :cookie-attrs {:http-only true}
                       })
        wrap-request-logging
        wrap-cookies
        wrap-forwarded-remote-addr
        wrap-force-https
        )))

(def app (create-app))

(defonce server (atom nil))

(defn start! []
  (log/info "Starting server on port 8080")
  (reset! server
          (jetty/run-jetty
           (wrap-reload #'app)
           {:port 8080 :join? false})))

(def nrepl-port 7888)

(defn start-nrepl []
  (log/info "Starting nrepl-server on port:" nrepl-port)
  (nrepl-server/start-server :port nrepl-port :handler cider-nrepl-handler))

(defn run [& _args]
  (start!))

(defn -main [& _args]
  (start!)
  (selmer/cache-off!)
  (start-nrepl))

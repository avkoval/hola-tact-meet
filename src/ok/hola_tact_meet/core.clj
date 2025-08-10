(ns ok.hola-tact-meet.core
  (:require
   [nrepl.server :as nrepl-server]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [org.httpkit.server :as httpkit]
   [ring.util.response :as response]
   [ring.middleware.session :refer [wrap-session]]
   [reitit.ring :as ring]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [selmer.parser :as selmer]
   [ok.hola-tact-meet.middleware :as middleware]
   [ok.hola-tact-meet.utils :as utils]
   [ok.hola-tact-meet.views :as views]
   [clojure.java.io]
   [ok.session.utils :refer [encode-secret-key]]
   [clojure.tools.logging :as log]
   [sentry-clj.core :as sentry]
   )
  (:gen-class))

;; Configure logging before any logging occurs
(utils/configure-logging!)

;; Initialize Sentry
(defn init-sentry! []
  (let [config (utils/app-config)]
    (when (:sentry/enabled config)
      (sentry/init! {:dsn (:sentry/dsn config)
                     :environment (:sentry/environment config)
                     :debug false})
      (log/info "Sentry initialized successfully"))))



(def base-app
  (ring/ring-handler
   (ring/router
    [
     ["/" {:get views/home}]
     ["/favicon.ico" {:get (fn [_] (response/file-response "resources/public/favicon.ico"))}]
     ["/assets/*" (ring/create-resource-handler)]
     ["/app" {:get (middleware/wrap-require-auth views/app-main)}]
     ["/app-landing" {:get views/app-landing}]
     ["/meetings" {:get (middleware/wrap-require-auth views/meetings-list)}]
     ["/actions" {:get (middleware/wrap-require-auth views/my-actions)}]
     ["/test-session" {:get views/test-session}]
     ["/google-login" {:post views/google-login}]
     ["/login/fake" {:get (middleware/wrap-localhost-only views/fake-login-page)}]
     ["/login/fake/existing" {:post (middleware/wrap-localhost-only views/fake-login-existing)}]
     ["/login/fake/new" {:post (middleware/wrap-localhost-only views/fake-login-new)}]
     ["/login/fake/generate-random-data" {:get views/fake-generate-random-data}]
     ["/admin/manage-users/toggle" {:post (middleware/wrap-require-admin views/admin-toggle-user)}]
     ["/admin/manage-users" {:get (middleware/wrap-require-admin views/admin-manage-users)}]
     ["/admin/manage-users-list" {:get (middleware/wrap-require-admin views/admin-refresh-users-list)}]
     ["/admin/manage-users/update-user-access-level" {:get (middleware/wrap-require-admin views/admin-update-user-access-level)}]
     ["/admin/manage-users/:user/teams" {:get (middleware/wrap-require-admin views/admin-user-teams)
                                         :post (middleware/wrap-require-admin views/admin-user-teams-change)}]
     ["/staff/create-meeting" {:get (middleware/wrap-require-staff views/staff-create-meeting-popup)
                               :post (middleware/wrap-require-staff views/staff-create-meeting-save)
                            }]
     ["/admin/project-settings" {:get (middleware/wrap-require-admin views/admin-project-settings)}]
     ["/admin/manage-users/:user/teams/add" {:post (middleware/wrap-require-admin views/admin-user-teams-add)}]
     ["/change-css-theme" {:get views/change-css-theme}]
     ["/logout" {:get views/logout}]
     ["/access/login-as/:user-id" {:get (middleware/wrap-require-admin views/admin-login-as-user)}]
     ["/meeting/join" {:get (middleware/wrap-require-auth views/join-meeting-modal)}]
     ["/meeting/:meeting-id/join" {:post (middleware/wrap-require-auth views/join-meeting)}]
     ["/meeting/:meeting-id/start" {:post (middleware/wrap-require-staff views/meeting-start)}]
     ["/meeting/:meeting-id/main" {:get (middleware/wrap-require-auth views/meeting-main)}]
     ["/meeting/:meeting-id/main/refresh" {:get (middleware/wrap-require-auth views/meeting-main-refresh-content-watcher)}]
     ["/meeting/:meeting-id/add-topic" {:post (middleware/wrap-require-auth views/meeting-add-topic)}]
     ["/meeting/:meeting-id/vote-topic" {:post (middleware/wrap-require-auth views/meeting-vote-topic)}]
     ["/meeting/:meeting-id/set-current-topic" {:post (middleware/wrap-require-auth views/meeting-set-current-topic)}]
     ["/meeting/:meeting-id/start-timer" {:post (middleware/wrap-require-auth views/meeting-start-timer)}]
     ["/meeting/:meeting-id/stop-timer" {:post (middleware/wrap-require-auth views/meeting-stop-timer)}]
     ["/meeting/:meeting-id/delete-topic" {:post (middleware/wrap-require-auth views/meeting-delete-topic)}]
     ["/meeting/:meeting-id/topic/:topic-id" {:post (middleware/wrap-require-auth views/meeting-edit-topic)}]
     ["/meeting/:meeting-id/topic/:topic-id/save" {:post (middleware/wrap-require-auth views/meeting-edit-topic-save)}]
     ["/meeting/:meeting-id/topic/:topic-id/finish" {:post (middleware/wrap-require-auth views/topic-finish)}]
     ["/meeting/:meeting-id/add-action" {:post (middleware/wrap-require-auth views/meeting-add-action)}]
     ["/meeting/:meeting-id/finish" {:post (middleware/wrap-require-auth views/meeting-finish)}]
     ["/action/:action-id/completion-modal" {:get (middleware/wrap-require-auth views/action-completion-modal)}]
     ["/action/:action-id/update-status" {:post (middleware/wrap-require-auth views/action-update-status)}]
     ])
   (constantly {:status 404, :body "Not Found."})))


(defn create-app []
  (let [secret-key (encode-secret-key (:secret-key (utils/app-config)))]
    (-> base-app
        middleware/my-wrap-oauth2
        utils/wrap-json-params
        wrap-params
        (wrap-session {:store (cookie-store {:key secret-key})
                       :cookie-attrs {:http-only true}
                       })
        middleware/wrap-request-logging
        middleware/wrap-exception-handling
        wrap-cookies
        wrap-forwarded-remote-addr
        middleware/wrap-force-https
        )))

(def app (create-app))

(defonce server (atom nil))

(defn start! []
  (init-sentry!)
  (let [config (utils/app-config)
        port (:server/port config)]
    (log/info (str "Starting server on port " port))
    (log/info (str "Log level: " (:log-level config)))
    (reset! server
            (httpkit/run-server
             (wrap-reload #'app)
             {:port port}))))

(def nrepl-port 7889)
(def test-nrepl-port 7890)

(defn start-nrepl []
  (log/info "Starting nrepl-server on port:" nrepl-port)
  (nrepl-server/start-server :port nrepl-port :handler cider-nrepl-handler))

(defn start-test-nrepl []
  (log/info "Starting test nrepl-server on port:" test-nrepl-port)
  (nrepl-server/start-server :port test-nrepl-port :handler cider-nrepl-handler))

(defn run [& _args]
  (start!))

(defn -main [& args]
  (if (= "test" (first args))
    (do
      (println "Starting server in TEST mode with test profile")
      ;; Set the profile for Aero configuration
      (System/setProperty "aero.profile" "test")
      (start!)
      (start-test-nrepl)
      (selmer/cache-off!))
    (do
      (start!)
      (selmer/cache-off!)  ;; FIXME this should not be done on production!
      (start-nrepl))))

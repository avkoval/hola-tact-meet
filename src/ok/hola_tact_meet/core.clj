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
   [selmer.parser :as selmer]
   [ok.hola-tact-meet.middleware :as middleware]
   [ok.hola-tact-meet.utils :as utils]
   [ok.hola-tact-meet.views :as views]
   [clojure.java.io]
   [ok.session.utils :refer [encode-secret-key]]
   [clojure.tools.logging :as log]
   )
  (:gen-class))



(def base-app
  (ring/ring-handler
   (ring/router
    [
     ["/" {:get views/home}]
     ["/favicon.ico" {:get (fn [_] (response/file-response "resources/public/favicon.ico"))}]
     ["/assets/*" (ring/create-resource-handler)]
     ["/app" {:get (middleware/wrap-require-auth views/app-main)}]
     ["/test-session" {:get views/test-session}]
     ["/google-login" {:post views/google-login}]
     ["/login/fake" {:get (middleware/wrap-localhost-only views/fake-login-page)}]
     ["/login/fake/existing" {:post (middleware/wrap-localhost-only views/fake-login-existing)}]
     ["/login/fake/new" {:post (middleware/wrap-localhost-only views/fake-login-new)}]
     ["/login/fake/generate-random-data" {:get views/fake-generate-random-data}]
     ["/admin/manage-users" {:get (middleware/wrap-require-admin views/admin-manage-users)}]
     ["/admin/manage-users/update-user-access-level" {:get (middleware/wrap-require-admin views/admin-update-user-access-level)}]
     ["/change-css-theme" {:get views/change-css-theme}]
     ["/logout" {:get views/logout}]
     ])
   (constantly {:status 404, :body "Not Found."})))


(defn create-app []
  (let [secret-key (encode-secret-key (:secret-key (utils/app-config)))]
    (-> base-app
        utils/wrap-json-params
        wrap-params
        middleware/my-wrap-oauth2
        (wrap-session {:store (cookie-store {:key secret-key})
                       :cookie-attrs {:http-only true}
                       })
        middleware/wrap-request-logging
        wrap-cookies
        wrap-forwarded-remote-addr
        middleware/wrap-force-https
        )))

(def app (create-app))

(defonce server (atom nil))

(defn start! []
  (log/info "Starting server on port 8080")
  (reset! server
          (jetty/run-jetty
           (wrap-reload #'app)
           {:port 8081 :join? false})))

(def nrepl-port 7889)

(defn start-nrepl []
  (log/info "Starting nrepl-server on port:" nrepl-port)
  (nrepl-server/start-server :port nrepl-port :handler cider-nrepl-handler))

(defn run [& _args]
  (start!))

(defn -main [& _args]
  (start!)
  (selmer/cache-off!)
  (start-nrepl))

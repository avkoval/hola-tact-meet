(ns ok.hola-tact-meet.core
  (:require
   [nrepl.server :as nrepl-server]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as response]
   [ring.middleware.session]
   [reitit.ring :as ring]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
   [ring.middleware.session :refer [wrap-session]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [selmer.parser :refer [render-file] :as selmer]
;;   [starfederation.datastar.clojure.api :as d*]
;;   [starfederation.datastar.clojure.adapter.ring :refer [->sse-response]]
   [ok.hola-tact-meet.utils :as utils]
;;   [clojure.data.json :as json]
;;   [clojure.walk :refer [keywordize-keys]]
;;   [clojure.string :as string]
   [aero.core :refer [read-config]]
   [clojure.java.io]
   [clojure.pprint :refer [pprint]]
   [ok.oauth2.core :refer [get-oauth-config]]
   [ok.session.utils :refer [encode-secret-key]]
   [ring.middleware.oauth2 :refer [wrap-oauth2]]
  )
  (:gen-class))

(defn app-config []
  (read-config (clojure.java.io/resource "config.edn")))

(defn home [request]
  (let [oauth2-config (get-oauth-config (app-config))
        ]
    ;; (println "test reload")
    ;; (pprint (config))
    (pprint request)
    (println (get-in request [:session ::state]))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/home.html" {:google_client_id (get-in oauth2-config [:google :client-id])
                                               :google_launch_uri (get-in oauth2-config [:google :launch-uri])
                                               :google_login_uri "/google-login"
                                               :host (get-in request [:headers "host"])})}))

(defn app-main [request]
  (let [oauth2-config (get-oauth-config (app-config))]
    ;; (println "test reload")
    ;; (pprint (config))
    ;; (pprint oauth2-config)
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/main.html" {})}))


(defn test-session [{session :session}]
(let [count   (:count session 0)
      session (assoc session :count (inc count))]
  (pprint session)
  (-> (response/response (str "You accessed this page " count " times."))
      (assoc :session session))))

(defn google-login [{session :session :as request}]
(let [login-count   (:login-count session 0)
      session (assoc session :count (inc login-count))]
  (pprint session)
  (pprint request)
  (-> (response/response (str "Logged IN. " count " times."))
      (assoc :session session))))

(def base-app
  (ring/ring-handler
    (ring/router
     [
      ["/" {:get home}]
      ["/favicon.ico" {:get (fn [_] (response/file-response "resources/public/favicon.ico"))}]
      ["/assets/*" (ring/create-resource-handler)]
      ["/app" {:get app-main}]
      ["/test-session" {:get test-session}]
      ["/google-login" {:post google-login}]
      ])
    (constantly {:status 404, :body "Not Found."})))

(defn wrap-force-https [handler]
  (fn [request]
    (let [proto (get-in request [:headers "x-forwarded-proto"])
          request' (if (= proto "https")
                     (assoc request :scheme :https)
                     request)]
      (handler request'))))

(defn my-wrap-oauth2 [base-handler]
  (let [config (get-oauth-config (config))]
    (wrap-oauth2 base-handler config)))


(def secret-key (encode-secret-key "your-secret-key123")) ;; Ensure it is 16 characters

(def app
  (-> base-app
      my-wrap-oauth2
      (wrap-session {:store (cookie-store {:key secret-key})
                     :cookie-attrs {:http-only true}
                     })
      ))

(defonce server (atom nil))

(defn start! []
  (reset! server
          (jetty/run-jetty
           (-> #'app
               wrap-cookies
               wrap-reload
               wrap-forwarded-remote-addr
               wrap-force-https
               wrap-params
               utils/wrap-json-params
               )
           {:port 8080 :join? false})))

(def nrepl-port 7888)

(defn start-nrepl []
  (println (str "Starting nrepl-server on port: " nrepl-port))
  (nrepl-server/start-server :port nrepl-port :handler cider-nrepl-handler))

(defn run [& args]
  (start!))

(defn -main [& args]
  (start!)
  (selmer/cache-off!)
  (start-nrepl))

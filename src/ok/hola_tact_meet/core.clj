(ns ok.hola-tact-meet.core
  (:require
   [nrepl.server :as nrepl-server]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as response]
   [reitit.ring :as ring]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.proxy-headers :refer [wrap-forwarded-remote-addr]]
   [ring.middleware.session :refer [wrap-session]]
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
   [ring.middleware.oauth2 :refer [wrap-oauth2]]
  )
  (:gen-class))

(defn config []
  (read-config (clojure.java.io/resource "config.edn")))

(defn home [request]
  (let [oauth2-config (get-oauth-config (config))]
    ;; (println "test reload")
    ;; (pprint (config))
    (pprint oauth2-config)
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/home.html" {:google_client_id (get-in oauth2-config [:google :client-id])
                                               :google_launch_uri (get-in oauth2-config [:google :launch-uri])
                                               :host (get-in request [:headers "host"])})}))

(defn main [request]
  (let [oauth2-config (get-oauth-config (config))]
    ;; (println "test reload")
    ;; (pprint (config))
    (pprint oauth2-config)
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/main.html" {})}))


(def base-app
  (ring/ring-handler
    (ring/router
     [
      ["/" {:get home}]
      ["/favicon.ico" {:get (fn [_] (response/file-response "resources/public/favicon.ico"))}]
      ["/assets/*" (ring/create-resource-handler)]
      ["/app" {:get main}]
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

(def app
  (-> base-app
      my-wrap-oauth2))

(defonce server (atom nil))

(defn start! []
  (reset! server
          (jetty/run-jetty
           (-> #'app
               wrap-reload
               wrap-forwarded-remote-addr
               wrap-force-https
               wrap-session
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

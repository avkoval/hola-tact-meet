(ns ok.hola-tact-meet.core
  (:require
   [nrepl.server :as nrepl-server]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as response]
   [reitit.ring :as ring]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.middleware.params :refer [wrap-params]]
   [selmer.parser :refer [render-file] :as selmer]
;;   [starfederation.datastar.clojure.api :as d*]
;;   [starfederation.datastar.clojure.adapter.ring :refer [->sse-response]]
   [ok.hola-tact-meet.utils :as utils]
;;   [clojure.data.json :as json]
;;   [clojure.walk :refer [keywordize-keys]]
;;   [clojure.string :as string]
  )
  (:gen-class))


(defn home [request]
  (let [test "aaa"]
    (println "test reload")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (render-file "templates/home.html" {:test test})}))

(def app
  (ring/ring-handler
    (ring/router
     [
      ["/" {:get home}]
      ["/favicon.ico" {:get (fn [_] (response/file-response "resources/public/favicon.ico"))}]
      ["/assets/*" (ring/create-resource-handler)]
      ])
    (constantly {:status 404, :body "Not Found."})))

(defonce server (atom nil))

(defn start! []
  (reset! server
          (jetty/run-jetty
           (-> #'app
               wrap-reload
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

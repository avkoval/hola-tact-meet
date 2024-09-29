(ns hola-tact-meet.routes.home
  (:require
   [hola-tact-meet.layout :as layout]
   [hola-tact-meet.db.core :as db]
   [clojure.java.io :as io]
   [hola-tact-meet.middleware :as middleware]
   [ring.util.response]
   [clojure.pprint]
   [ring.util.http-response :as response]))

(defn home-page [request]
  (let [headers (:headers request)
        user-email (get headers "ngrok-auth-user-email")]
    (if (nil? user-email)
      (layout/render request "error.html" {:title "Authentication error" :message "User is not recognized"})
      (do 
        (layout/render request "home.html" {:user-email user-email}))
      )
  ))

(defn about-page [request]
  (layout/render request "about.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
;;                 middleware/wrap-ngrok-auth-middleware
                 middleware/wrap-formats
                 ]}
   ["/" {:get home-page}]
   ["/about" {:get about-page}]])


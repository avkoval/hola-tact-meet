(ns ok.hola-tact-meet.middleware
  (:require
   [ring.util.response :as response]
   [ok.hola-tact-meet.utils :as utils]
   [clojure.java.io]
   [ok.oauth2.utils :refer [get-oauth-config]]
   [ring.middleware.oauth2 :refer [wrap-oauth2]]
   [clojure.tools.logging :as log]
   )
  (:gen-class))

(defn wrap-request-logging [handler]
  (fn [request]
    (if (utils/sse-endpoint? request)
      ;; For SSE, just log the request and pass through
      (do
        (log/info (format "%s %s (SSE)"
                          (:request-method request)
                          (:uri request)))
        (handler request))
      ;; For regular requests, do full logging
      (let [start-time (System/currentTimeMillis)
            response (handler request)
            duration (- (System/currentTimeMillis) start-time)]
        (log/info (format "%s %s %d (%dms)"
                          (:request-method request)
                          (:uri request)
                          (:status response)
                          duration))
        response))))

(defn wrap-require-auth [handler]
  (fn [request]
    (if (get-in request [:session :userinfo :logged-in])
      (handler request)
      (response/redirect "/"))))

(defn wrap-localhost-only [handler]
  (fn [request]
    (let [remote-addr (:remote-addr request)]
      (if (utils/localhost? remote-addr)
        (handler request)
        {:status 403
         :headers {"Content-Type" "text/plain"}
         :body "Access denied. This endpoint is only available from localhost."}))))

(defn wrap-require-admin [handler]
  (fn [request]
    (let [access-level (get-in request [:session :userinfo :access-level])]
      (if (= access-level "admin")
        (handler request)
        {:status 403
         :headers {"Content-Type" "text/plain"}
         :body "Access denied. Admin privileges required."}))))

(defn wrap-require-staff [handler]
  (fn [request]
    (let [access-level (get-in request [:session :userinfo :access-level])]
      (if (or (= access-level "staff") (= access-level "admin"))
        (handler request)
        {:status 403
         :headers {"Content-Type" "text/plain"}
         :body "Access denied. Staff privileges required."}))))

(defn wrap-force-https [handler]
  (fn [request]
    (if (utils/sse-endpoint? request)
      ;; Skip HTTPS processing for SSE to avoid interfering with streaming
      (handler request)
      (let [proto (get-in request [:headers "x-forwarded-proto"])
            request' (if (= proto "https")
                       (assoc request :scheme :https)
                       request)]
        (handler request')))))

(defn my-wrap-oauth2 [base-handler]
  (let [config (get-oauth-config (utils/app-config))]
    (wrap-oauth2 base-handler config)))

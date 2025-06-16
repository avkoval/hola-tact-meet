(ns ok.hola-tact-meet.utils
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [clojure.data.json :as json]
   )
  (:gen-class))

(defn wrap-json-params
  "Add :json to request map when content-type is application/json"
  [handler]
  (fn [request]
    (let [body (slurp (:body request))]
      (if (and (not (= "" body)) (= "application/json" (:content-type request)))
        (handler (assoc request :json (keywordize-keys (json/read-str body))))
        (handler request)
        )
      )
    ))
(ns ok.hola-tact-meet.utils
  (:require [clojure.data.json :as json]
            [clojure.walk :refer [keywordize-keys]]))

(defn wrap-json-params
  "Ring middleware to parse JSON request bodies and add them to :json-params"
  [handler]
  (fn [request]
    (let [content-type (get-in request [:headers "content-type"])
          request' (if (and content-type
                           (.startsWith content-type "application/json")
                           (:body request))
                    (try
                      (let [body-str (slurp (:body request))
                            json-params (when (not-empty body-str)
                                         (keywordize-keys (json/read-str body-str)))]
                        (assoc request :json-params json-params))
                      (catch Exception e
                        request))
                    request)]
      (handler request'))))

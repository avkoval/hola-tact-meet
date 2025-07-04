(ns ok.hola_tact_meet.utils
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [clojure.data.json :as json]
   [clojure.string :as string]
   [faker.generate :as gen]
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


(defn capitalize-first [s]
  (if (empty? s)
    s
    (str (clojure.string/upper-case (subs s 0 1)) (subs s 1))))


(defn gen-email []
  (str (gen/word) "@" (gen/word) ".com")
)


(defn sanitize-query
  "TODO: escape shell arguments too, remove duplicate tags"
  [q]
  (-> q
      (string/replace #"^\s*and\s" "")
      (string/trim)
      )
  )

(defn sse-endpoint?
  "Check if this is an SSE endpoint"
  [request]
  (= "text/event-stream" (get-in request [:headers "accept"])))


(defn datastar-query
  "Parse JSON array of datastar GET params"
  [request]
  (keywordize-keys (json/read-str (get-in request [:query-params "datastar"])))
)

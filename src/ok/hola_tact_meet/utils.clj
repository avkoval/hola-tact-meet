(ns ok.hola-tact-meet.utils
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [clojure.data.json :as json]
   [clojure.string :as string]
   [faker.generate :as gen]
   [aero.core :refer [read-config]]
   )
  (:gen-class))

(defn app-config []
  (read-config (clojure.java.io/resource "config.edn")))

(defn configure-logging!
  "Configure SLF4J Simple logging based on config.edn log level
   Note: This may not work if SLF4J is already initialized.
   For guaranteed effect, set JVM system properties at startup."
  []
  (let [config (app-config)
        log-level (or (:log-level config) "INFO")
        slf4j-level (case (clojure.string/upper-case log-level)
                      "DEBUG" "debug"
                      "INFO" "info" 
                      "WARN" "warn"
                      "ERROR" "error"
                      "info")] ; default fallback
    ;; Set SLF4J Simple system properties (may be too late if SLF4J already initialized)
    (System/setProperty "org.slf4j.simpleLogger.defaultLogLevel" slf4j-level)
    (System/setProperty "org.slf4j.simpleLogger.showDateTime" "true")
    (System/setProperty "org.slf4j.simpleLogger.dateTimeFormat" "yyyy-MM-dd HH:mm:ss")
    (System/setProperty "org.slf4j.simpleLogger.showThreadName" "true")
    (System/setProperty "org.slf4j.simpleLogger.showLogName" "true")
    (System/setProperty "org.slf4j.simpleLogger.showShortLogName" "true")
    (System/setProperty "org.slf4j.simpleLogger.levelInBrackets" "true")
    (println (str "Logging configured to level: " log-level " (SLF4J: " slf4j-level ")"))
    (println "Note: If debug messages don't appear, SLF4J was already initialized.")
    (println "Alternative: Start with JVM property: -Dorg.slf4j.simpleLogger.defaultLogLevel=debug")))

(defn localhost?
  "Check if the given address is a localhost address.
   Handles IPv4, IPv6, and hostname variants."
  [addr]
  (when addr
    (or (= addr "127.0.0.1")
        (= addr "::1")
        (= addr "0:0:0:0:0:0:0:1")
        (= addr "[0:0:0:0:0:0:0:1]")
        (= addr "localhost"))))



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

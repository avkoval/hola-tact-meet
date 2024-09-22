(ns hola-tact-meet.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [hola-tact-meet.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[hola-tact-meet started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[hola-tact-meet has shut down successfully]=-"))
   :middleware wrap-dev})

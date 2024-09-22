(ns hola-tact-meet.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[hola-tact-meet started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[hola-tact-meet has shut down successfully]=-"))
   :middleware identity})

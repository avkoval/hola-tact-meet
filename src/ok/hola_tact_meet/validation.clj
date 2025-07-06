(ns ok.hola-tact-meet.validation
  (:require
   [malli.core :as m]
   )
  (:gen-class))

(def TeamData
  [:map
   [:name [:string {:min 1}]]
   [:description {:optional true} [:string]]
   [:managers {:optional true} [:vector :int]]
   ])

(ns ok.hola-tact-meet.validation
  ;; (:require
  ;;  [malli.core :as m]
  ;;  )
  (:gen-class))

(def TeamData
  [:map
   [:name [:string {:min 1}]]
   [:description {:optional true} [:string]]
   [:managers {:optional true} [:vector :int]]
   ])

(def MeetingData
  [:map
   [:title [:string {:min 1}]]
   [:description {:optional true} [:string]]
   [:team [:string {:min 1}]]
   [:scheduled-at [:string {:min 1}]]
   [:join-url {:optional true} [:string]]
   [:allow-topic-voting {:optional true} [:boolean]]
   [:sort-topics-by-votes {:optional true} [:boolean]]
   [:is-visible {:optional true} [:boolean]]
   ])

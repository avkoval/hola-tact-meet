(ns ok.hola-tact-meet.basic-ui-test
  (:require [clojure.test :refer :all]
            [wally.main :as w]
            [wally.selectors :as ws]
            [garden.selectors :as s]
            [ok.hola-tact-meet.utils :as utils]
            ))

(def APP_URL (str "http://localhost:" (:server/port (utils/app-config))  "/"))


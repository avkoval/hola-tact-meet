(ns ok.oauth2.core)

(def OAUTH2-PARAMS ["client-id" "client-secret" "authorize-uri" "access-token-uri" "scopes" "launch-uri" 
                    "redirect-uri" "landing-url"])

(defn get-oauth-config [config]
  (reduce 
   (fn [acc provider]
     (assoc acc (keyword provider) 
            (into {} (map (fn [key] [(keyword key) (get config (keyword (str "oauth2/" provider "-" key))) ]) OAUTH2-PARAMS))))
   {}
   (:oauth2/providers config))
  )

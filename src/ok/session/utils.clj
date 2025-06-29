(ns ok.session.utils)

(defn encode-secret-key [key-string]
  (cond
    (nil? key-string)
    (throw (Exception. "Secret key is nil - check your config.edn"))

    (not (string? key-string))
    (throw (Exception. (str "Secret key must be a string, got: " (type key-string) " value: " key-string)))
    
    (empty? key-string)
    (throw (Exception. "Secret key cannot be empty"))
    
    :else nil)
  (let [key-bytes (.getBytes key-string "UTF-8")
        ;; Truncate or pad the key to ensure it's 16 bytes
        truncated-or-padded-key (if (> (count key-bytes) 16)
                                   (take 16 key-bytes)
                                   (concat key-bytes (repeat (- 16 (count key-bytes)) 0)))]
    (byte-array truncated-or-padded-key)))

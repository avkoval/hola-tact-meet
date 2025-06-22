(ns ok.session.utils)

(defn encode-secret-key [key-string]
  (let [key-bytes (-> key-string
                      (.getBytes "UTF-8")
                      (byte-array))
        ;; Truncate or pad the key to ensure it's 16 bytes
        truncated-or-padded-key (if (> (count key-bytes) 16)
                                   (take 16 key-bytes)
                                   (concat key-bytes (repeat (- 16 (count key-bytes)) 0)))]
    (byte-array truncated-or-padded-key)))

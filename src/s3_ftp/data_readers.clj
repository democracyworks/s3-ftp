(ns s3-ftp.data-readers
  (:import [com.amazonaws.regions Region Regions]))

(defn aws-region [region-name]
  (-> region-name
      (Regions/valueOf)
      (Region/getRegion)))

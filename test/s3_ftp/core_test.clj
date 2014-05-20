(ns s3-ftp.core-test
  (:require [clojure.test :refer :all]
            [s3-ftp.core :refer :all]
            [aws.sdk.s3 :as s3]
            [cemerick.bandalore :as sqs]
            [miner.ftp :as ftp]))

(def s3-state (promise))

(defn run-server [f]
  (with-redefs [sqs/create-queue (constantly nil)
                sqs/send (constantly nil)
                s3/put-object (fn [_ bucket _ _]
                                (deliver s3-state {:bucket bucket}))]
    (let [server (start-server)]
      (f)
      (.stop server))))

(use-fixtures :once run-server)

(deftest user-config-test
  (testing "files are uploaded to user-specific S3 bucket; not default"
    (is (= "democracyworks-user1-ftp-test"
           (do
             (ftp/with-ftp [client "ftp://user1:admin@localhost:50021"]
               (ftp/client-put client "README.md"))
             (:bucket @s3-state))))))

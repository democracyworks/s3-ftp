(ns s3-ftp.core-test
  (:require [clojure.test :refer :all]
            [s3-ftp.core :refer :all]
            [aws.sdk.s3 :as s3]
            [cemerick.bandalore :as sqs]
            [miner.ftp :as ftp]))

(defn run-server [f]
  (with-redefs [sqs/create-queue (constantly nil)
                sqs/send (constantly nil)]
    (let [server (start-server)]
      (f)
      (.stop server))))

(use-fixtures :once run-server)

(deftest user-config-test
  (testing "files are uploaded to user-specific S3 bucket; not default"
    (let [s3-bucket (promise)]
      (with-redefs [s3/put-object (fn [_ bucket _ _]
                                    (deliver s3-bucket bucket))]
        (is (= "democracyworks-user1-ftp-test"
               (do
                 (ftp/with-ftp [client "ftp://user1:admin@localhost:50021"]
                   (ftp/client-put client "README.md"))
                 @s3-bucket))))))

  (testing "files are uploaded to default S3 bucket when no user-specific bucket"
    (let [s3-bucket (promise)]
      (with-redefs [s3/put-object (fn [_ bucket _ _]
                                    (deliver s3-bucket bucket))]
        (is (= "democracyworks-s3-ftp-test"
               (do
                 (ftp/with-ftp [client "ftp://user2:admin@localhost:50021"]
                   (ftp/client-put client "README.md"))
                 @s3-bucket)))))))

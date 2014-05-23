(ns s3-ftp.core-test
  (:require [clojure.test :refer :all]
            [s3-ftp.core :refer :all]
            [aws.sdk.s3 :as s3]
            [cemerick.bandalore :as sqs]
            [miner.ftp :as ftp]))

(defn run-server [tests]
  (with-redefs [sqs/create-queue (fn [_ queue-name] queue-name)
                sqs/send (constantly nil)
                s3/put-object (constantly nil)]
    (let [server (start-server)]
      (tests)
      (.stop server))))

(use-fixtures :once run-server)

(deftest s3-bucket-config-test
  (testing "files are uploaded to user-specific S3 bucket; not default"
    (let [s3-bucket (promise)]
      (with-redefs [s3/put-object (fn [_ bucket _ _]
                                    (deliver s3-bucket bucket))]
        (is (= "democracyworks-user1-ftp-test"
               (do
                 (ftp/with-ftp [client "ftp://user1:admin@localhost:50021"]
                   (ftp/client-put client "README.md"))
                 (deref s3-bucket 5000 nil)))))))

  (testing "files are uploaded to default S3 bucket when no user-specific bucket"
    (let [s3-bucket (promise)]
      (with-redefs [s3/put-object (fn [_ bucket _ _]
                                    (deliver s3-bucket bucket))]
        (is (= "democracyworks-s3-ftp-test"
               (do
                 (ftp/with-ftp [client "ftp://user2:admin@localhost:50021"]
                   (ftp/client-put client "README.md"))
                 (deref s3-bucket 5000 nil))))))))

(deftest sqs-queue-config-test
  (testing "file upload notifications are sent to user-specific SQS queue; not default"
    (let [sqs-queue (promise)]
      (with-redefs [sqs/send (fn [_ queue _]
                               (deliver sqs-queue queue))]
        (is (= "democracyworks-user1-ftp-test"
               (do (ftp/with-ftp [client "ftp://user1:admin@localhost:50021"]
                     (ftp/client-put client "README.md"))
                   (deref sqs-queue 5000 nil)))))))

  (testing "file upload notifications are sent to default queue when no user-specific queue"
    (let [sqs-queue (promise)]
      (with-redefs [sqs/send (fn [_ queue _]
                               (deliver sqs-queue queue))]
        (is (= "democracyworks-s3-ftp-test"
               (do (ftp/with-ftp [client "ftp://user2:admin@localhost:50021"]
                     (ftp/client-put client "README.md"))
                   (deref sqs-queue 5000 nil))))))))

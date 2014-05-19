(ns s3-ftp.core
  (:require [aws.sdk.s3 :as s3]
            [cemerick.bandalore :as sqs]
            [turbovote.resource-config :refer [config]]
            [clojure.tools.logging :as logging]
            [s3-ftp.data-readers])
  (:import [java.io File]
           [org.apache.ftpserver FtpServerFactory DataConnectionConfigurationFactory]
           [org.apache.ftpserver.listener ListenerFactory]
           [org.apache.ftpserver.usermanager PropertiesUserManagerFactory]
           [org.apache.ftpserver.ftplet DefaultFtplet FtpletResult])
  (:gen-class))

(def allowed-commands #{"USER" "PASS" "SYST" "FEAT" "PWD" "EPSV" "PASV" "TYPE" "QUIT" "STOR"})

(defn user-config
  "Looks for a username specific config, and if not found, uses the default"
  [username & keys]
  (let [override-keys (concat [:user-overrides username] keys)]
    (or (apply config override-keys) (apply config keys))))

(defn S3CopierFtplet [sqs-client]
  (proxy [DefaultFtplet] []
    (beforeCommand [session request]
      (let [cmd (-> request (.getCommand) clojure.string/upper-case)]
        (if (allowed-commands cmd)
          (proxy-super beforeCommand session request)
          FtpletResult/DISCONNECT)))
    (onUploadEnd [session request]
      (let [user (.getUser session)
            username (.getName user)
            user-home (.getHomeDirectory user)
            curr-dir (-> session
                         (.getFileSystemView)
                         (.getWorkingDirectory)
                         (.getAbsolutePath))
            filename (.getArgument request)
            file (File. (str user-home curr-dir filename))
            s3-bucket (user-config username :aws :s3 :bucket)
            queue-name (user-config username :aws :sqs :queue)
            sqs-queue (sqs/create-queue sqs-client queue-name)]
        (println "queue name" queue-name)
        (println "bucket name" s3-bucket)
        (println "creds" (user-config username :aws :creds))
        (try (s3/put-object (user-config username :aws :creds)
                            s3-bucket
                            filename file)
             (sqs/send sqs-client sqs-queue (pr-str {:bucket s3-bucket
                                                     :filename filename}))
             (.delete file)
             (catch Exception e (logging/error (str "S3 Upload failed: "
                                                    (.getMessage e)))))
        nil))))

(defn user-manager []
  (let [user-file (-> "users.properties"
                      (clojure.java.io/resource)
                      (.toURI)
                      (java.io.File.))
        user-manager-factory (doto (PropertiesUserManagerFactory.)
                               (.setFile user-file))]
    (.createUserManager user-manager-factory)))

(defn data-connection-configuration [config]
  (let [factory (DataConnectionConfigurationFactory.)]
    (some->> config :passive-ports (.setPassivePorts factory))
    (some->> config :passive-external-address (.setPassiveExternalAddress factory))
    (.createDataConnectionConfiguration factory)))

(defn create-client []
  (let [client (if (config :aws :creds)
                 (sqs/create-client (config :aws :creds :access-key)
                                    (config :aws :creds :secret-key))
                 (sqs/create-client))]
    (.setRegion client (config :aws :sqs :region))
    client))

(defn -main []
  (let [sqs-client (create-client)
        server-factory (FtpServerFactory.)
        active-port (or (config :ftp :active-port) 2221)
        listener-factory (doto (ListenerFactory.)
                           (.setPort active-port)
                           (.setDataConnectionConfiguration (data-connection-configuration (config :ftp))))
        server (.createServer
                (doto (FtpServerFactory.)
                  (.addListener "default" (.createListener listener-factory))
                  (.setUserManager (user-manager))
                  (.setFtplets (java.util.HashMap. {"s3CopierFtplet" (S3CopierFtplet sqs-client)}))))]
    (.start server)))

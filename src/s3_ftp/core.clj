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

(def allowed-commands #{"USER" "PASS" "SYST" "FEAT" "PWD" "EPSV" "PASV" "TYPE"
                        "QUIT" "STOR" "CWD"})

(defn- user-config [username]
  (config :user-overrides username))

(defn- user-or-default-config [username key-path]
  (if-let [uc (user-config username)]
    (get-in uc key-path)
    (apply config key-path)))

(defn- s3-bucket [username]
  (user-or-default-config username [:aws :s3 :bucket]))

(defn- sqs-queue-name [username]
  (user-or-default-config username [:aws :sqs :queue]))

(defn- create-sqs-queue [sqs-client username]
  (sqs/create-queue sqs-client (sqs-queue-name username)))

(defn S3CopierFtplet [sqs-client]
  (proxy [DefaultFtplet] []
    (beforeCommand [session request]
      (let [cmd (-> request (.getCommand) clojure.string/upper-case)]
        (if (allowed-commands cmd)
          (proxy-super beforeCommand session request)
          FtpletResult/DISCONNECT)))
    (onUploadEnd [session request]
      (let [user-home (-> session (.getUser) (.getHomeDirectory))
            curr-dir (-> session
                         (.getFileSystemView)
                         (.getWorkingDirectory)
                         (.getAbsolutePath))
            filename (.getArgument request)
            file (File. (str user-home curr-dir filename))
            username (-> session (.getUser) (.getName) keyword)
            bucket (s3-bucket username)
            queue (create-sqs-queue sqs-client username)]
        (try (s3/put-object (config :aws :creds) bucket filename file)
             (try (sqs/send sqs-client queue (pr-str {:bucket bucket
                                                      :filename filename}))
                  (try (.delete file)
                       (catch Exception e (logging/error
                                           (str "Unable to delete local file: "
                                                (.getMessage e)))))
                  (catch Exception e (logging/error
                                      (str "SQS message send failed: "
                                           (.getMessage e)))))
             (catch Exception e (logging/error (str "S3 upload failed: "
                                                    (.getMessage e)))))
        nil))))

(defn- user-manager []
  (let [user-file (-> "users.properties"
                      (clojure.java.io/resource)
                      (.toURI)
                      (java.io.File.))
        user-manager-factory (doto (PropertiesUserManagerFactory.)
                               (.setFile user-file))]
    (.createUserManager user-manager-factory)))

(defn- data-connection-configuration [config]
  (let [factory (DataConnectionConfigurationFactory.)]
    (some->> config :passive-ports (.setPassivePorts factory))
    (some->> config :passive-external-address (.setPassiveExternalAddress factory))
    (.createDataConnectionConfiguration factory)))

(defn- create-sqs-client []
  (let [client (if (config :aws :creds)
                 (sqs/create-client (config :aws :creds :access-key)
                                    (config :aws :creds :secret-key))
                 (sqs/create-client))]
    (doto client (.setRegion (config :aws :sqs :region)))))

(defn start-server []
  (let [sqs-client (create-sqs-client)
        server-factory (FtpServerFactory.)
        active-port (or (config :ftp :active-port) 2221)
        listener-factory (doto (ListenerFactory.)
                           (.setPort active-port)
                           (.setDataConnectionConfiguration
                            (data-connection-configuration (config :ftp))))
        server (.createServer
                (doto (FtpServerFactory.)
                  (.addListener "default" (.createListener listener-factory))
                  (.setUserManager (user-manager))
                  (.setFtplets (java.util.HashMap.
                                {"s3CopierFtplet"
                                 (S3CopierFtplet sqs-client)}))))]
    (doto server (.start))))

(defn -main []
  (start-server))

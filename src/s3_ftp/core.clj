(ns s3-ftp.core
  (:require [aws.sdk.s3 :as s3]
            [cemerick.bandalore :as sqs]
            [turbovote.resource-config :as cfg]
            [clojure.tools.logging :as logging]
            [clojure.java.io :as io]
            [s3-ftp.data-readers])
  (:import [java.io File]
           [org.apache.ftpserver FtpServerFactory DataConnectionConfigurationFactory]
           [org.apache.ftpserver.listener ListenerFactory]
           [org.apache.ftpserver.usermanager PropertiesUserManagerFactory]
           [org.apache.ftpserver.ftplet DefaultFtplet FtpletResult]
           [org.apache.ftpserver.ssl SslConfigurationFactory])
  (:gen-class))

(def allowed-commands #{"USER" "PASS" "SYST" "FEAT" "PWD" "EPSV" "PASV" "TYPE"
                        "QUIT" "STOR" "AUTH" "PBSZ" "PROT" "PORT" "OPTS"})

(defn- user-config [username]
  (cfg/config [:user-overrides username] nil))

(defn- user-or-default-config [username key-path]
  (if-let [uc (user-config username)]
    (get-in uc key-path)
    (cfg/config key-path)))

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
          (do
            (logging/error "Command not allowed: " cmd)
            FtpletResult/DISCONNECT))))
    (onUploadEnd [session request]
      (let [user-home (-> session (.getUser) (.getHomeDirectory))
            curr-dir (-> session
                         (.getFileSystemView)
                         (.getWorkingDirectory)
                         (.getAbsolutePath))
            filename (.getArgument request)
            file (File. (str user-home curr-dir filename))
            file-size (.length file)
            username (-> session (.getUser) (.getName) keyword)
            bucket (s3-bucket username)
            queue (create-sqs-queue sqs-client username)]
        (if (> file-size 0)
          (logging/info (str filename " uploaded " file-size " bytes"))
          (logging/warn (str filename " is zero bytes")))
        (try (s3/put-object (cfg/config [:aws :creds]) bucket filename file)
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
  (let [client (if (cfg/config [:aws :creds] nil)
                 (sqs/create-client (cfg/config [:aws :creds :access-key])
                                    (cfg/config [:aws :creds :secret-key]))
                 (sqs/create-client))]
    (doto client (.setRegion (cfg/config [:aws :sqs :region])))))

(defn- ssl-configuration [listener-factory config]
  "sets the ssl configuration on the ListenerFactory if the config exists"
  (if config
    (let [ks-name (some-> config :keystore :filename)
          ts-name (some-> config :truststore :filename)
          factory (SslConfigurationFactory.)]
      (some->> ks-name io/resource io/file (.setKeystoreFile factory))
      (some->> config :keystore :password (.setKeystorePassword factory))
      (some->> ts-name io/resource io/file (.setTruststoreFile factory))
      (some->> config :truststore :password (.setTruststorePassword factory))
      (.setSslConfiguration listener-factory (.createSslConfiguration factory)))))

(defn start-server []
  (let [sqs-client (create-sqs-client)
        server-factory (FtpServerFactory.)
        active-port (or (cfg/config [:ftp :active-port] 2221) 2221)
        listener-factory (doto (ListenerFactory.)
                           (.setPort active-port)
                           (.setDataConnectionConfiguration
                            (data-connection-configuration (cfg/config [:ftp])))
                           (ssl-configuration (cfg/config [:ssl] nil)))
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

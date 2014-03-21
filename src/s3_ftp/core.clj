(ns s3-ftp.core
  (:require [aws.sdk.s3 :as s3]
            [turbovote.resource-config :refer [config]]
            [clojure.tools.logging :as logging])
  (:import [java.io File]
           [org.apache.ftpserver FtpServerFactory DataConnectionConfigurationFactory]
           [org.apache.ftpserver.listener ListenerFactory]
           [org.apache.ftpserver.usermanager PropertiesUserManagerFactory]
           [org.apache.ftpserver.ftplet DefaultFtplet FtpletResult])
  (:gen-class))

(def allowed-commands #{"USER" "PASS" "SYST" "FEAT" "PWD" "EPSV" "TYPE" "QUIT" "STOR"})

(def S3CopierFtplet
  (proxy [DefaultFtplet] []
    (beforeCommand [session request]
      (let [cmd (-> request (.getCommand) clojure.string/upper-case)]
        (if (allowed-commands cmd)
          (proxy-super beforeCommand session request)
          FtpletResult/DISCONNECT)))
    (onUploadEnd [session request]
      (let [user-home (-> session
                          (.getUser)
                          (.getHomeDirectory))
            curr-dir (-> session
                         (.getFileSystemView)
                         (.getWorkingDirectory)
                         (.getAbsolutePath))
            filename (.getArgument request)
            file (File. (str user-home curr-dir filename))]
        (try (s3/put-object (config :aws-credentials)
                            (config :aws-bucket-name)
                            filename file)
             (.delete file)
             (catch Exception e (logging/error (str "S3 Upload failed: " (.getMessage e)))))
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

(defn -main []
  (let [server-factory (FtpServerFactory.)
        active-port (or (config :ftp :active-port) 2221)
        listener-factory (doto (ListenerFactory.)
                           (.setPort active-port)
                           (.setDataConnectionConfiguration (data-connection-configuration (config :ftp))))
        server (.createServer
                (doto (FtpServerFactory.)
                  (.addListener "default" (.createListener listener-factory))
                  (.setUserManager (user-manager))
                  (.setFtplets (java.util.HashMap. {"s3CopierFtplet" S3CopierFtplet}))))]
    (.start server)))

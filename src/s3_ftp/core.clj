(ns s3-ftp.core
  (:require [aws.sdk.s3 :as s3]
            [turbovote.resource-config :refer [config]])
  (:import [java.io File]
           [org.apache.ftpserver FtpServerFactory DataConnectionConfigurationFactory]
           [org.apache.ftpserver.listener ListenerFactory]
           [org.apache.ftpserver.usermanager PropertiesUserManagerFactory]
           [org.apache.ftpserver.ftplet DefaultFtplet])
  (:gen-class))

(def S3CopierFtplet
  (proxy [DefaultFtplet] []
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
        (s3/put-object (config :aws-credentials)
                       (config :aws-bucket-name)
                       filename file)
        (.delete file)))))

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

(defproject s3-ftp "0.1.0-SNAPSHOT"
  :description "An FTP frontend that forwards files to a predefined s3 bucket"
  :url "http://github.com/turbovote/s3-ftp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojure-tools "1.1.3"]
                 [org.apache.ftpserver/ftpserver-core "1.0.6" :exclusions [org.slf4j/slf4j-api]]
                 [org.apache.ftpserver/ftplet-api "1.0.6"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [clj-aws-s3 "0.3.10"]
                 [com.cemerick/bandalore "0.0.6"]
                 [turbovote.resource-config "0.2.1"]]
  :resource-paths ["config", "resources"]
  :main s3-ftp.core
  :profiles {:uberjar {:aot [s3-ftp.core]}
             :test {:dependencies [[com.velisco/clj-ftp "0.3.6"]]
                    :resource-paths ["test-resources"]}}
  :uberjar-name "s3-ftp.jar")

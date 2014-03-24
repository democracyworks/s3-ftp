(defproject s3-ftp "0.1.0-SNAPSHOT"
  :description "An FTP frontend that forwards files to a predefined s3 bucket"
  :url "http://github.com/turbovote/s3-ftp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clojure-tools "1.1.2"]
                 [org.apache.ftpserver/ftpserver-core "1.0.6"]
                 [org.apache.ftpserver/ftplet-api "1.0.6"]
                 [org.slf4j/slf4j-jdk14 "1.5.2" :exclusions [org.slf4j/slf4j-api]]
                 [clj-aws-s3 "0.3.8"]
                 [com.cemerick/bandalore "0.0.5"]
                 [turbovote.resource-config "0.1.0"]]
  :main s3-ftp.core
  :profiles {:build [:uberjar]
             :uberjar {:aot [s3-ftp.core]}}
  :uberjar-name "s3-ftp.jar")

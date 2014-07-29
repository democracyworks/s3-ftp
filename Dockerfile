FROM quay.io/democracyworks/clojure-api-supervisor:latest
MAINTAINER Democracy Works, Inc. <dev@democracy.works>

ONBUILD ADD ./resources/ /var/local/s3-ftp/resources/

ADD ./target/s3-ftp.jar /var/local/s3-ftp/
ADD docker/start-s3-ftp.sh /start-s3-ftp.sh
ADD docker/supervisord-s3-ftp.conf /etc/supervisor/conf.d/supervisord-s3-ftp.conf

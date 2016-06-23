# s3-ftp

s3-ftp is an FTP server frontend that pushes all uploads to a specified AWS S3 bucket and also publishes a message with the key of the newly pushed file to a specified AWS SQS queue. Users are strictly limited in that they can only upload files. All other commands (cd, get, ls, ...) will force a disconnect

It uses embedded [Apache FtpServer](http://mina.apache.org/ftpserver-project/) to act as an FTP server.


## Setup

You will need a config.edn file available as a resource with the
following shape:

```clojure
{:aws {:creds {:access-key "your-access-key"
               :secret-key "your-secret-key"}}
       :s3 {:bucket "bucket-to-put-uploads-in"}
       :sqs {:region #aws/region "US_WEST_2" ; see below
             :queue "queue-name-to-put-s3-keys-in"}}
 :ftp {:passive-external-address "your-accessible-hostname.org" ; optional. overrides using machines hostname
       :passive-ports "1234-2345" ; optional. overrides default passive ports
       :active-port 21 ; optional. overrides default active port
       }}

```

You will also need a users.properties available as a resource. There is an example in dev-resources.

The valid values for `:sqs :region` are the enum values listed in
the API documentation for [com.amazonaws.regions.Regions](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/regions/Regions.html)

## Usage

It can be run locally by just using

`lein run`

To run it inside a docker container, run:

`script/build`

Finally, run the docker image making sure to expose your configured active and passive ports.
Like this:

`docker run -d -p 21:21 -p 57649:57649 -p 57650:57650 -p 57651:57651 \
            -p 57652:57652 -p 57653:57653 -p 57654:57654 -p 57655:57655 \
            -p 57656:57656 -p 57657:57657 -p 57658:57658 -p 57659:57659 \
            quay.io/democracyworks/s3-ftp`

## TLS

To enable TLS, you need to provide a Keystore file with the server's certificate and private key installed, which you do using the keytool command [see here](http://docs.oracle.com/javase/1.5.0/docs/tooldocs/solaris/keytool.html). A self-signed cert for "localhost" is installed and configured in dev-resources. You just need to copy `dev-resources/sample-config.edn` to `dev-resources/config.edn` (or copy over the :ssl bit if you already have a config.edn in dev-resources). Since it is self-signed, it is included in the truststore.jks file too. A real cert from a legit CA doesn't need that.

One way to test that the TLS is working in dev is to use a curl command like this:
`curl -3 -v -k --ftp-ssl -T FILENAME ftp://USERNAME:PASSWORD@SERVER:PORT`

## License

Copyright © 2014 Democracy Works, Inc.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

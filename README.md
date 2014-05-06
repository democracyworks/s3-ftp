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

To run it inside a docker container, first build the uberjar by running

`script/build`

Then build your base docker image by running 

`docker build -t democracyworks/s3-ftp .`

This image contains no configuration and cannot be run. You need to build another image that uses `s3-ftp` as it's base image, creates the homedirectory you specified in your `users.properties`, exposes your active and passive ports and has in its build context a `resources` directory containing your `config.edn` and `user.properties` files.

`docker build -t democracyworks/my-s3-ftp path/to/other/dockerfile`

Finally, run the docker image making sure to expose your configured active and passive ports.

## License

Copyright © 2014 Democracy Works, Inc.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

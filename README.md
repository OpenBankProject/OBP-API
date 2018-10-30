# README

The Open Bank Project API

## ABOUT

The Open Bank Project is an open source API for banks that enables account holders to interact with their bank using a wider range of applications and services.

The OBP API supports transparency options (enabling account holders to share configurable views of their transaction data with trusted individuals and even the public), data blurring (to preserve sensitive information) and data enrichment (enabling users to add tags, comments and images to transactions).

Thus, the OBP API abstracts away the peculiarities of each core banking system so that a wide range of apps can interact with  multiple banks on behalf of the account holder. We want to raise the bar of financial transparency and enable a rich ecosystem of innovative financial applications and services.

Our tag line is: Bank as a Platform. Transparency as an Asset.

The API uses OAuth 1.0 authentication.

The project roadmap is available [here.](https://github.com/OpenBankProject/OBP-API/blob/develop/roadmap.md)

## DOCUMENTATION 

The API documentation is best viewed using the OBP API Explorer or a third party tool that has imported the OBP Swagger definitions.
Please refer to the [wiki](https://github.com/OpenBankProject/OBP-API/wiki) for links. 

## STATUS of API Versions

OBP instances support multiple versions of the API simultaniously (unless they are deactivated in config)
To see the status (DRAFT, STABLE or BLEEDING-EDGE) of an API version, look at the root endpoint e.g. /obp/v2.0.0/root or /obp/v3.0.0/root

On the 8th of June 2017, [V2.0.0](https://apisandbox.openbankproject.com/obp/v2.0.0/root) was marked as stable.

## LICENSE

This project is dual licensed under the AGPL V3 (see NOTICE) and commercial licenses from TESOBE Ltd.

## SETUP

The project uses Maven 3 as its build tool.

To compile and run jetty, install Maven 3 and execute:

        ./mvn.sh jetty:run

## To run with IntelliJ IDEA

* Make sure you have the IntelliJ Scala plugin installed.

* Create a new folder e.g. OpenBankProject and cd there

* git clone https://github.com/OpenBankProject/OBP-API.git

* In IntelliJ IDEA do File -> New -> Project from existing sources, navigate to the folder and select pom.xml

* Alternatively you can do File -> New -> Project from VCS and checkout the project directly from github.

* When / if prompted for SDK, choose Java 1.8 (and Scala 2.11) otherwise keep the defaults. Use the Maven options. Do not change the project name etc.

* If you see a message about an unmanaged pom.xml, click the option to let Maven manage it.

* Navigate to test/scala/code/RunWebApp. You may see a Setup Scala SDK link. Click this and check Scala 2.11.8 or so.

* In src/main/resources/props create a test.default.props for tests. Set connector=mapped

* In src/main/resources/props create a \<yourloginname\>.props (or default.props) for development. Set connector=mapped

* Now **Rebuild** the project so everything is compiled. At this point you may need to select the SDK, see above.

* Once you have rebuilt the project wihtout compile errors, you should be able to RunWebApp in src/test/scala

* Run RunWebApp by right clicking on it or selecting Run. The built in jetty server should start on localhost:8080

* Browse to localhost:8080 but don't try anything else there yet.

### Run some tests.
  
* Run a single test. For instance right click on test/scala/code/branches/MappedBranchProviderTest and select Run Mapp...

* Run multiple tests: Right click on test/scala/code and select Run. If need be:
    Goto Run / Debug configurations
    Test Kind: Select All in Package
    Package: Select code
    Add the absolute /path-to-your-OBP-API in the "working directory" field
    You might need to assign more memory via VM Options: e.g. -Xmx1512M -XX:MaxPermSize=512M
    
    Make sure your test.default.props has the minimum settings (see test.default.props.template)

    
    Right click test/scala/code and select the Scala Tests in code to run them all.
    
    Note: You may want to disable some tests not relevant to your setup e.g.:
    set bank_account_creation_listener=false in test.default.props 


## Other ways to run tests

* See pom.xml for test configuration
* See http://www.scalatest.org/user_guide


## From the command line

Set memory options

    export MAVEN_OPTS="-Xmx3000m -XX:MaxPermSize=512m"

Run one test

    mvn -DwildcardSuites=code.api.directloginTest test

## Ubuntu

If you use Ubuntu (or a derivate) and encrypted home directories (e.g. you have ~/.Private), you might run into the following error when the project is built:

    uncaught exception during compilation: java.io.IOException
    [ERROR] File name too long
    [ERROR] two errors found
    [DEBUG] Compilation failed (CompilerInterface)

The current workaround is to move the project directory onto a different partition, e.g. under /opt/ .


## Databases:

The default database for testing etc is H2. PostgreSQL is used for the sandboxes (user accounts, metadata, transaction cache).

### Notes on using Postgres with SSL:

Postgres needs to be compiled with SSL support.

Use openssl to create the files you need.

For the steps, see: https://www.howtoforge.com/postgresql-ssl-certificates

In short, edit postgresql.conf

ssl = on

ssl_cert_file = '/etc/YOUR-DIR/server.crt'

ssl_key_file = '/etc/YOUR-DIR/server.key'

And restart postgres.

Now, this should enable SSL (on the same port that Postgres normally listens on) - but it doesn't force it.
To force SSL, edit pg_hba.conf replacing the host entries with hostssl

Now in OBP-API Props, edit your db.url and add &ssl=true

 e.g.

 db.url=jdbc:postgresql://localhost:5432/my_obp_database?user=my_obp_user&password=the_password&ssl=true

Note: Your Java environment may need to be setup correctly to use SSL

Restart OBP-API, if you get an error, check your Java environment can connect to the host over SSL.

Note you can change the log level in /src/main/resources/default.logback.xml (try TRACE or DEBUG)

There is a gist / tool which is useful for this. Search the web for SSLPoke. Note this is an external repository.

e.g. https://gist.github.com/4ndrej/4547029

or

git clone https://github.com/MichalHecko/SSLPoke.git .

gradle jar
cd ./build/libs/

java -jar SSLPoke-1.0.jar www.github.com 443

Successfully connected

java -jar SSLPoke-1.0.jar YOUR-POSTGRES-DATABASE-HOST PORT

You can add switches e.g. for debugging.

java -jar -Dhttps.protocols=TLSv1.1,TLSv1.2 -Djavax.net.debug=all SSLPoke-1.0.jar localhost 5432


To import a certificate:

keytool -import -storepass changeit -noprompt -alias localhost_postgres_cert -keystore /Library/Java/JavaVirtualMachines/jdk1.8.0_73.jdk/Contents/Home/jre/lib/security/cacerts -trustcacerts -file /etc/postgres_ssl_certs/server/server.crt


To get certificate from the server / get further debug information:

openssl s_client -connect ip:port

The above section is work in progress.



## Sandbox data

To populate the OBP database with sandbox data:

1) In the API's props file, set `allow_sandbox_data_import=true`

Probably best then, is to use the API Explorer (https://github.com/OpenBankProject/API-Explorer):

2) Get your `user_id` from the API Explorer at `/#2_0_0-getCurrentUser`
3) Add this id to `super_admin_user_ids` in the API's props file and restart the API
4) Go back to API Explorer, log in again and grant your user the role `CanCreateSandbox` at `/#2_0_0-addEntitlement` (make `bank_id` empty)
5) Now post the JSON data using the payload field at `/#2_1_0-sandboxDataImport`
6) If successful you should see an empty result `{}` and no error message


## Kafka (optional):

If Kafka connector is selected in props (connector=kafka), Kafka and Zookeeper have to be installed, as well as OBP-Kafka-Python (which can be either running from command-propmpt or from inside Docker container):

* Kafka and Zookeeper can be installed using system's default installer or by unpacking the archives (http://apache.mirrors.spacedump.net/kafka/ and http://apache.mirrors.spacedump.net/zookeeper/)

* OBP-Kafka-Python can be downloaded from https://github.com/OpenBankProject/OBP-Kafka-Python

## Running with a Zookeeper/Kafka Cluster

1) NGINX Configuration for Load Balancing

* Create file /etc/nginx/sites-available/api

* Configure as follows:

        upstream backend {
                least_conn;
                server host1:8080; # The name of the server shall be changed as appropriate
                server host2:8080;
                server host3:8080;
        }

        server {
                server_name obptest.com www.obptest.com; # The server name should be changed as appropriate
                access_log /var/log/nginx/api.access.log;
                error_log /var/log/nginx/api.error.log;
                location / {
                    proxy_pass http://backend/;
                }
            location /obp/v2.1.0/sandbox/data-import {
                    proxy_pass http://backend/;
                }
        }

2) Zookeeper/Kafka Cluster Setup

* The Zookeeper/Kafka cluster is deployed on 3 nodes. The following configurations need to be done on each of the three nodes

* Zookeeper configuration

* Inside the Kafka directory, edit the file conf/zookeeper.properties and include these lines:

        dataDir=/home/user/zookeeper
        server.1=host1:2888:3888 # The name of the servers shall be changed as appropriate
        server.2=host2:2888:3888
        server.3=host3:2888:3888
        initLimit=5
        syncLimit=2

* Create a myid file under dataDir which is /home/user/zookeeper in this example:

        echo “1” > /home/user/zookeeper/myid       #Insert unique id’s on each of the machines

* Start the zookpeer daemons on each of the 3 machines

        bin/zookeeper-server-start.sh config/zookeeper.properties &

* Kafka Configuration

* Inside the Kafka directory, edit the file conf/server.properties and include these lines:

        broker.id=1 # The broker.id should be unique for each host

        num.partitions=4

        zookeeper.connect=host1:2181,host2:2181,host3:2181

* Start the kafka broker daemons on all the machines:

        bin/kafka-server-start.sh config/server.properties &

* Create the topics:

        bin/kafka-topics.sh --create --zookeeper host1:2181,host2:2181,host3:2181 --replication-factor 1 --partitions 1 --topic Request

        bin/kafka-topics.sh --create --zookeeper host1:2181,host2:2181,host3:2181 --replication-factor 1 --partitions 1 --topic Response

3) OBP-API

* Configuration

* Edit the OBP-API/src/main/resources/props/default.props so that it contains the following lines. Please note that 
kafka.host is used by the producer and kafka.zookeeper_host is used by the consumer. This should be done on each node:

        connector=kafka
        # Address to be used by consumer
        kafka.zookeeper_host=localhost:2181
        # Address to be used by producer
        kafka.host=localhost:9092
        kafka.request_topic=Request
        kafka.response_topic=Response

* Start the server:

        cd OBP-API
        mvn jetty:run

4) OBP-JVM

* Build the package:

        cd OBP-JVM
        mvn install

* Run the demo:

        java -jar obp-ri-demo/target/obp-ri-demo-2016.9-SNAPSHOT-jar-with-dependencies.jar&

* Here be aware that the name of the jar file might be different, so make sure to use the correct name of the jar file

5) OBP-Kafka-Python

* Run from the command line:

        cd OBP-Kafka-Python
        python server.py

6) To test the setup, try a request

http://localhost:8080/obp/v2.0.0/banks

## Production Options.

* set the status of HttpOnly and Secure cookie flags for production, uncomment the following lines of  "webapp/WEB-INF/web.xml" :

        <session-config>
          <cookie-config>
            <secure>true</secure>
            <http-only>true</http-only>
          </cookie-config>
        </session-config>

## Running the API in Production Mode

We use jetty8 to run the API in production mode.

1) Install java and jetty8

2) jetty 8 configuration

* Edit the /etc/default/jetty8 file so that it contains the following settings:

        NO_START=0
        JETTY_HOST=127.0.0.1 #If you want your application to be accessed from other hosts, change this to your IP address
        JAVA_OPTIONS="-Drun.mode=production -XX:PermSize=256M -XX:MaxPermSize=512M -Xmx768m -verbose -Dobp.resource.dir=$JETTY_HOME/resources -Dprops.resource.dir=$JETTY_HOME/resources"

* In src/main/resources/props create a test.default.props file for tests. Set connector=mapped

* In src/main/resources/props create a default.props file for development. Set connector=mapped

* In src/main/resources/props create a production.default.props file for production. Set connector=mapped.

* This file could be similar to the default.props file created above, or it could include production settings, such as information about Postgresql server, if you are using one. For example, it could have the following line for postgresql configuration.

        db.driver=org.postgresql.Driver
        db.url=jdbc:postgresql://localhost:5432/yourdbname?user=yourdbusername&password=yourpassword

* Now, build the application to generate .war file which will be deployed on jetty8 server:

        cd OBP-API/
        mvn package

* This will generate OBP-API-1.0.war under OBP-API/target/

* Copy OBP-API-1.0.war to /usr/share/jetty8/webapps/ directory and rename it to root.war

* Edit the /etc/jetty8/jetty.conf file and comment out the lines:

        etc/jetty-logging.xml
        etc/jetty-started.xml

* Now restart jetty8:

        sudo service jetty8 restart

* You should now be able to browse to localhost:8080 (or yourIPaddress:8080)


## Using Akka remote storage

Most internal OBP model data access now occurs over Akka. This is so the machine that has JDBC access to the OBP database can be physically separated from the OBP API layer. In this configuration we run two instances of OBP-API on two different machines and they communicate over Akka. Please see README.Akka.md for instructions.

## Using SSL Encryption with kafka

For SSL encryption we use jks keystores.
Note that both the keystore and the truststore (and all keys within) must have the same password for unlocking, for which
the api will stop at boot up and ask for. 

* Edit your props file(s) to contain:
        
        kafka.use.ssl=true
        keystore.path=/path/to/api.keystore.jks
        truststore.path=/path/to/api.truststore.jks

## Using SSL Encryption with props file

For SSL encryption we use jks keystores.
Note that keystore (and all keys within) must have the same password for unlocking, for which the api will stop at boot up and ask for. 

* Edit your props file(s) to contain:
        
        jwt.use.ssl=true
        keystore.path=/path/to/api.keystore.jks
        keystore.alias=SOME_KEYSTORE_ALIAS
        
A props key value, XXX, is considered encrypted if has an encryption property (XXX.is_encrypted) in addition to the regular props key name in the props file e.g:

   *  db.url.is_encrypted=true
   *  db.url=BASE64URL(SOME_ENCRYPTED_VALUE)
   
The Encrypt/Decrypt workflow is :
1. Encrypt: Array[Byte]
2. Helpers.base64Encode(encrypted)
3. Props file: String
4. Helpers.base64Decode(encryptedValue)
5. Decrypt: Array[Byte]

1st, 2nd and 3rd step can be done using an external tool

### Encrypting props values with openssl on the commandline

1. Export the public certificate from the keystore:

    `keytool -export -keystore /PATH/TO/KEYSTORE.jks -alias CERTIFICATE_ALIAS -rfc -file apipub.cert`
2. Extract the public key from the public certificate

    `openssl x509 -pubkey -noout -in apipub.cert > PUBKEY.pub`
3. Get the encrypted propsvalue like in the following bash script (usage ./scriptname.sh /PATH/TO/PUBKEY.pub propsvalue)

```
#!/bin/bash
echo -n $2 |openssl pkeyutl -pkeyopt rsa_padding_mode:pkcs1 -encrypt  -pubin -inkey $1 -out >(base64)
```

## Using jetty password obfuscation with props file

You can obfuscate passwords in the props file the same way as for jetty:

1. Create the obfuscated value as described here: https://www.eclipse.org/jetty/documentation/9.3.x/configuring-security-secure-passwords.html

2. A props key value, XXX, is considered obfuscated if has an obfuscation property (XXX.is_obfuscated) in addition to the regular props key name in the props file e.g:

   *  db.url.is_obfuscated=true
   *  db.url=OBF:fdsafdsakwaetcetcetc

## Code Generation
We support to generate the OBP-API code from the following two types of json. You can choose one of them as your own requirements. 

    1 Choose one of the following types: type1 or type2 
    2 Modify the json file your selected,
    3 Run the Main method according to your json file
    4 Run/Restart OBP-API project.
    5 Run API_Exploer project to test your new APIs. (click the Tag `APIBuilder B1)

Here are the two types: 

Type1: If you use `modelSource.json`, please run `APIBuilderModel.scala` main method
```
OBP-API/src/main/scala/code/api/APIBuilder/modelSource.json
OBP-API/src/main/scala/code/api/APIBuilder/APIBuilderModel.scala
```
Type2: If you use `apisResource.json`, please run `APIBuilder.scala` main method
```
OBP-API/src/main/scala/code/api/APIBuilder/apisResource.json
OBP-API/src/main/scala/code/api/APIBuilder/APIBuilder.scala
```

## Using jetty password obfuscation with props file

You can obfuscate passwords in the props file the same way as for jetty:

1. Create the obfuscated value as described here: https://www.eclipse.org/jetty/documentation/9.3.x/configuring-security-secure-passwords.html

2. A props key value, XXX, is considered obfuscated if has an obfuscation property (XXX.is_obfuscated) in addition to the regular props key name in the props file e.g:

   *  db.url.is_obfuscated=true
   *  db.url=OBF:fdsafdsakwaetcetcetc

## Rate Limiting
We support rate limiting i.e functionality to limit calls per consumer key (App). Only `New Style Endpoins` support it. The list of they can be found at this fie: https://github.com/OpenBankProject/OBP-API/blob/develop/src/main/scala/code/api/util/NewStyle.scala. 
It is assumed that you have a Redis instance if you wan to use the functionality. In order to make it work edit your props file in next way:
```
use_consumer_limits=false, In case isn't defined default value is "false"
redis_address=YOUR_REDIS_URL_ADDRESS, In case isn't defined default value is 127.0.0.1
redis_port=YOUR_REDIS_PORT, In case isn't defined default value is 6379
```
Next types are supported:
```
1. per minute
2. per hour
3. per day
4. per week
5. per month
```    
If you exced rate limit per minute for instance you will get the response:
```json
{
    "error": "OBP-10018: Too Many Requests.We only allow 3 requests per minute for this Consumer."
}
```
and response headers:
```
X-Rate-Limit-Limit → 3
X-Rate-Limit-Remaining → 0
X-Rate-Limit-Reset → 22
```
Description of the headers above:
1. `X-Rate-Limit-Limit` - The number of allowed requests in the current period
2. `X-Rate-Limit-Remaining` - The number of remaining requests in the current period
3. `X-Rate-Limit-Reset` - The number of seconds left in the current period

Please note that first will be checked `per minute` call limit then `per hour` etc.

Info about rate limiting availibility at some instance can be found over next API endpoint: https://apisandbox.openbankproject.com/obp/v3.1.0/root. Response we are interested in looks lke:
```json
{
  ...
  ,
  "rate_limiting":{
    "enabled":true,
    "redis_available":true,
    "is_active":true
  }
}
```
   
## Scala / Lift

* We use scala and liftweb http://www.liftweb.net/

* Advanced architecture: http://exploring.liftweb.net/master/index-9.html

* A good book on Lift: "Lift in Action" by Timothy Perrett published by Manning.

# README

The Open Bank Project API

## ABOUT

The Open Bank Project is an open source API for banks that enables account holders to interact with their bank using a wider range of applications and services.

The OBP API supports transparency options (enabling account holders to share configurable views of their transaction data with trusted individuals and even the public), data blurring (to preserve sensitive information) and data enrichment (enabling users to add tags, comments and images to transactions).

Thus, the OBP API abstracts away the peculiarities of each core banking system so that a wide range of apps can interact with  multiple banks on behalf of the account holder. We want to raise the bar of financial transparency and enable a rich ecosystem of innovative financial applications and services.

Our tag line is: Bank as a Platform. Transparency as an Asset.

The API uses OAuth 1.0 authentication.

The project roadmap is available [here.](https://openbankproject.com/roadmap/)

## DOCUMENTATION 

Please refer to the [wiki](https://github.com/OpenBankProject/OBP-API/wiki) to see the API specification. 

## STATUS

[V1.2.1] (https://github.com/OpenBankProject/OBP-API/wiki/REST-API-V1.2.1) is the current stable API.

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

* In IntelliJ IDEA do File -> New -> Project from existing sources

* (Alternatively you can do File -> New -> Project from VCS and checkout from github)

* When / if prompted, choose Java 1.8 and Scala 2.11 otherwise keep the defaults. Use the Maven options. Do not change the project name etc.

* Navigate to test/scala/code/RunWebApp. You may see a Setup Scala SDK link. Click this and check Scala 2.11.8 or so.

* In src/main/resources/props create a test.default.props for tests. Set connector=mapped

* In src/main/resources/props create a \<yourloginname\>.props (or default.props) for development. Set connector=mapped

* Now **Rebuild** the project so everything is compiled.

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


----

## Ubuntu

If you use Ubuntu (or a derivate) and encrypted home directories (e.g. you have ~/.Private), you might run into the following error when the project is built:

    uncaught exception during compilation: java.io.IOException
    [ERROR] File name too long
    [ERROR] two errors found
    [DEBUG] Compilation failed (CompilerInterface)

The current workaround is to move the project directory onto a different partition, e.g. under /opt/ .


## Databases:

The default database for testing etc is H2. PostgreSQL is used for the sandboxes (user accounts, metadata, transaction cache).

## Sandbox data

To populate the OBP database with sandbox data:

1) In your Props file, set allow_sandbox_data_import=true
2) In your Props files, set sandbox_data_import_secret=YOUR-KEY-HERE
3) Now you can POST the sandbox json found in src/main/scala/code/api/sandbox/example_data/example_import.json to /sandbox/v1.0/data-import?secret_token=YOUR-KEY-HERE
4) If successful you should get 201 Created.


## Kafka (optional):

If Kafka connector is selected in props (connector=kafka), Kafka and Zookeeper have to be installed, as well as OBP-Kafka-Python (which can be either running from command-propmpt or from inside Docker container):

* Kafka and Zookeeper can be installed using system's default installer or by unpacking the archives (http://apache.mirrors.spacedump.net/kafka/ and http://apache.mirrors.spacedump.net/zookeeper/)

* OBP-Kafka-Python can be downloaded from https://github.com/OpenBankProject/OBP-Kafka-Python

# Running with a Zookeeper/Kafka Cluster

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

* Create a myid file under dataDir which is /home/user/zookeeper in this example

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

* Edit the OBP-API/src/main/resources/props/default.props so that it contains the following lines. This should be done on each node:

            connector=kafka
            kafka.zookeeper_host=localhost:2181
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

# Production Options.

* set the status of HttpOnly and Secure cookie flags for produnction
,uncomment the following lines of  "webapp/WEB-INF/web.xml" :

        <session-config>
          <cookie-config>
            <secure>true</secure>
            <http-only>true</http-only>
          </cookie-config>
        </session-config>

# Running the API in Production Mode

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

## Scala / Lift

* We use scala and liftweb http://www.liftweb.net/

* Advanced architecture: http://exploring.liftweb.net/master/index-9.html

* A good book on Lift: "Lift in Action" by Timothy Perrett published by Manning.
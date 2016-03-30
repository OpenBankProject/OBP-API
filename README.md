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

* When / if prompted, choose Java 1.7 and Scala 2.10 otherwise keep the defaults. Use the Maven options. Do not change the project name etc.

* Navigate to test/scala/code/RunWebApp. You may see a Setup Scala SDK link. Click this and check Scala 2.10.5 or so.

* In src/main/resources/props create a test.default.props for tests. Set connector=mapped

* In src/main/resources/props create a <yourloginname>.default.props for development. Set connector=mapped

* Now **Rebuild** the project so everything is compiled.

* Run RunWebApp by right clicking on it or selecting Run. The built in jetty server should start on localhost:8080

* Browse to localhost:8080 but don't try anything else there yet.

### Run some tests.
  
* Run a single test. For instance right click on test/scala/code/branches/MappedBranchProviderTest and select Run Mapp...

* Run multiple tests: Right click on code and select Run. If need be:  
    Goto Run / Debug configurations
    Test Kind: Select All in Package
    Package: Select code
    Add the absolute /path-to-your-OBP-API in the "working directory" field 
    
    Make sure your test.default.props has the minimum settings (see test.default.props.template)
    
    Right click test/scala/code and select the Scala Tests in code to run them all.
    
    Note: You may want to disable some tests not relevant to your setup e.g.:
    set bank_account_creation_listener=false in test.default.props 


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


## Kafka (optional):

If Kafka connector is selected in props (connector=kafka), Kafka and Zookeeper have to be installed, as well as OBP-Kafka-Python (which can be either running from command-propmpt or from inside Docker container):

* Kafka and Zookeeper can be installed using system's default installer or by unpacking the archives (http://apache.mirrors.spacedump.net/kafka/ and http://apache.mirrors.spacedump.net/zookeeper/)

* OBP-Kafka-Python can be downloaded from https://github.com/OpenBankProject/OBP-Kafka-Python


## Scala / Lift

* We use scala and liftweb http://www.liftweb.net/

* Advanced architecture: http://exploring.liftweb.net/master/index-9.html


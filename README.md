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

This project is dual licensed under the AGPL V3 (see NOTICE) and a commercial license from TESOBE
Some files (OAuth related) are licensed under the Apache 2 license.

## SETUP

The project uses Maven 3 as its build tool.

To compile and run jetty, install Maven 3 and execute:

./mvn.sh jetty:run



## To run with IntelliJ IDEA

* Make sure you have the IntelliJ Scala plugin installed.

* Create a new folder e.g. OpenBankProject and cd there

* git clone https://github.com/OpenBankProject/OBP-API.git

* In IntelliJ IDEA do File -> New -> Project from existing sources

* When / if prompted, choose Java 1.7 and Scala 2.10 otherwise keep the defaults. Use the Maven options. Do not change the project name etc.

* Navigate to test/scala/code/RunWebApp. You may see a Setup Scala SDK link. Click this and check Scala 2.10.5 or so.

* Now **Rebuild** the project so everything is compiled.

* Run RunWebApp by right clicking on it or selecting Run. The built in jetty server should start on localhost:8080

* Browse to localhost:8080 but don't try anything else there yet.

### Run some tests.
  
* In src/main/resources/props create a test.default.props for tests. Set connector=mapped

* In src/main/resources/props create a <yourloginname>.default.props for development. Set connector=mapped

* Run a single test. For instance right click on test/scala/code/branches/MappedBranchProviderTest and select Run Mapp...

* Run all the tests: 
    Goto Run / Debug configurations 
    Select or create  a "ScalaTests in code" configuration, give some more memory: -Xmx1512M -XX:MaxPermSize=512M and put "." in the working driectory 
    
    Make sure your test.default.props has the minimum settings (see test.default.props.template)
    
    Right click test/scala/code and select the Scala Tests in code to run them all.


----

# Databases:

The default datastore used is PostgreSQL (user accounts, metadata, transaction cache).


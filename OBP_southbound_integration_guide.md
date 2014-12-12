







#Open Bank Project 
![OBP icon](https://www.openbankproject.com/static/images/logo.png)
##Southbound Integration Guide 

###Integrating the Open Bank Project API with a new banking system


###Contact

TESOBE
Music Pictures Ltd.
Osloer Strasse 16/17
D-13359 Berlin
Germany

Tel. (DE): +49 (0)30 8145 3994

E-Mail: contact@tesobe.com

###Document

Version: 1.0
Published: Dec. 12th 2014
Author: Everett Sochowski, Tweaking: Simon Redfern.
MD Editor Used: Mou (http://25.io/mou/)


##Obtaining the code

There is a branch of the Open Bank Project API suitable for forking to add new connector implementations:

https://github.com/OpenBankProject/OBP-API/tree/new_implementation_base

##Setup


The current codebase currently makes use of MongoDB and a (JDBC) relational database. The default relational databases are PostgreSQL and h2. If you wish to use a different relational database, you will need to add the appropriate dependency in the pom.xml file, and configure the Open Bank Project .props file accordingly (as described below). At this point MongoDB is mandatory, but we intend to make it optional in the near future, likely in January or February 2015. The project is built using maven 3.

Please ensure you have all the requirements installed. Note: if you intend to use h2, there is no need for any setup or installation.


###Props file

The Open Bank Project uses .props files for configuration. Props files should be created in src/main/resources/props. For initial purposes, it will be enough to name your main development props file “default.props” and your main test props file “test.default.props”. The full naming conventions for props files are describe in the following link: https://www.assembla.com/wiki/show/liftweb/Properties


###Creating your props files:

default.props:

	mongo.host=127.0.0.1
	mongo.dbName=dev
	mongo.port=27017
	db.driver=org.postgresql.Driver
	db.url=jdbc:postgresql://localhost:5432/mypostgresdb?user=mypostgresuser&password=mypw
	hostname=http://127.0.0.1:8080
	payments_enabled=false
	allow_sandbox_data_import=false
	sandbox_data_import_secret=some-uuid-string 

Notably:

	“db.driver” should be the driver used to connect to your relational database
	“db.url” should be the jdbc url of a development database of whatever relational db you have configured.

If both “db.driver” and “db.url” are not included in the file, an h2 database will be automatically created and used. This can be useful for development.

“hostname” should be the url at which you will access the url when it runs on your machine. You must specify this in order for oAuth to work properly.

“payments_enabled” controls a specific payments api call: it is not required if you are going to implement your own payment calls

“allow_sandbox_data_import” controls an api call for adding banks/users/accounts/transactions. You will probably not need it (and it will require implementation work by you in order for it to work, if you want it)

“sandbox_data_import_secret” is a secret token guarding the above mentioned data import call. It is only important (and should be set to some UUID string) if  allow_sandbox_data_import is set to true.


test.default.props

	mongo.host=127.0.0.1
	mongo.dbName=OBPTEST
	hostname=http://localhost:8000
	payments_enabled=true
	allow_sandbox_account_creation=true
	allow_sandbox_data_import=true
	sandbox_data_import_secret=123456


In your test.default.props file, be sure to pick a different mongo.dbName than the one specified in your default.props file, as it will be wiped automatically during the tests. There is no need to specify the relational db url, as the tests will run against an in memory h2 database, for increased performance. However it is still possible to specify another database to test again, in the same manner as in default.props, however the same warning goes as for mongodb: use a different database name.

###Running the Code

Via the command line:

	mvn jetty:run

This will start a server running on port 8080. The port can be specified by setting -Djetty.port, e.g:

	mvn -Djetty.port=8081 jetty:run

Important note: You need to make sure the hostname used to access the api corresponds to the hostname property in your .props file. E.g. if hostname=http://localhost:8080 then the api should always be accessed via that, and not for example, http://127.0.0.1:8080. The reason for this is that Oauth uses the request url as part of the signing process.


Via an IDE:

Running the code via an IDE is usually the best choice as the IDE will typically take care of continuous compilation for you. It should be possible to obtain continuous compilation via maven on the command line, but that is outside the scope of this document.

There is a runnable object RunWebApp in src/test/scala that when run, will start a web server on port 8080. In IntelliJ IDEA using the Scala plugin, is it possible to right click and run this file. Eclipse supports something similar with its Scala plugin.



###OAuth

If you want to actually use the API, you will want to register for OAuth, which is done at /consumer-registration (it should also be linked from the site index). One currently mysterious parameter in the input form is "User Authentication URL”. Most of the time you will want to leave this blank. Once you register, you will receive two important keys: Consumer Key, and Consumer Secret. You can use these with an OAuth client library to make authenticated API requests.

For a pre-made project capable of handling OAuth and making API calls, see the section “Social Finance” later in this document.

###Running the tests

Via the command line:

	mvn test

To run only tests with certain tags:

	mvn test -D tagsToInclude=getBankAccounts,getPermissions

The test tags are defined in the various test files, e.g. some are defined in src/test/scala/code/api/API121Test.scala


Via an IDE:

It is also possible to run the tests via Eclipse and Intellij (with their respective Scala plugins). The exact mechanism will depend on the particular IDE, but it should be very similar to running standard jUnit tests.


Notes on tests:

The existing tests are designed to test the API calls, rather than the connector implementation backing those calls. As such, having all the tests pass does not necessarily mean your implementation is bug free. For example, the tests do not cover if each field on each model is set correctly (e.g. a transaction's currency type).

The best way to ensure your connector is doing what you want it to do will be to write your own tests that test the api calls related to banks, accounts, transactions, cards and account holders (i.e. everything covered by your Connector implementation).

If you chose not to implement certain features, e.g. the data import api call, then some tests will fail. You may disable these tests if they are not wanted.

Social Finance
https://github.com/OpenBankProject/Social-Finance

Social Finance is a web app that makes use of most Open Bank Project API features. You may be interested in running it to provide a different perspective than the tests do on what is working with your changes.

Props configuration: default.props


	transloadit.authkey=012345555
	transloadit.addImageTemplate=5234324324
	api_hostname=http://127.0.0.1:8080
	obp_consumer_key=n55at5thsltxtaalavjaz4w0r1w2dgtvbbmqsgad
	obp_secret_key=0yyupvorqdlpj3a0nog5eoiqktxyc1qdjgmtxgqf
	defaultAuthProvider=http://127.0.0.1:8080
	hostname=http://localhost:8089


The “transloadit” parameters are for the image processing service that handles images tagged to transactions. If you would like to use this feature, you will need to register on transloadit.com.

“api_hostname” should be the same as the “hostname” value set in your Open Bank Project API props file.

“obp_consumer_key” and “obp_secret_key” are obtained via registration on the Open Bank Project API website (as described earlier in this document).

“defaultAuthProvider” should be set to the same value as “api_hostname”, for now.

“hostname” will be the url through which you will access the Social Finance web app. Important notes: if your API is running on 127.0.0.1, you will want to use “localhost” (or some other value not equal to 127.0.0.1). The same idea applies to API instances running on “localhost” or another value: they will need to be different between the API and Social Finance. Otherwise, there can be issues with conflicting sessions. 

As you will want to run Social Finance on a different port than the API, remember this is possible via, e.g. 

	mvn -Djetty.port=8089 jetty:run

##Implementing a new connection to a banking system

There are a few traits you will need to implement to get the Open Bank Project API running against a new banking system.

**AuthChecker** (src/main/scala/code/users/UserAuth.scala)

This trait is used to with the default oauth login page, and assumes users will log in via username/password. You will need to implement checkAuth, which should verify the username and password match (e.g. by making a call to the banking system you are integrating with's authentication method). If the call succeeds, you should return a Full(AuthResult), where AuthResult will contain the email and name of the user, if these attributes are available. If they are not available, they may be set to None.

You will want to create an object extending AuthChecker (see DummyAuthChecker.scala), and then edit the line

	def buildOne: AuthChecker = DummyAuthChecker

in UserAuth

to read

	def buildOne: AuthChecker = MyNewAuthChecker

where MyNewAuthChecker is the name of your implementation of the AuthChecker trait.


**Connector** (src/main/scala/code/bankconnectors/Connector.scala

Your implementation of this trait is the bulk of the work involved with connecting to a new banking system.

Important note: There is one Connector method, makePaymentImpl that you will probably not want to support, as it fits a specific type of payment.

You will want to create an object extending Connector (see DummyConnector.scala) and the edit the line

	def buildOne: Connector = DummyConnector

to read

	def buildOne: Connector = MyNewConnector

where MyNewConnector  is the name of your implementation of the Connector trait.



**DummyTestSetup** (src/test/scala/code/api/DummyTestSetup.scala)

You will want to rename this file (preferably via an IDE's smart refactoring functionality, as you will otherwise have to changes the references to it elsewhere in the code) and implement it.

This trait is responsible for setting up a test data source. If your new implementation of the Connector trait (see above) works with, for example, a SOAP service, then your implementation of DummyTestSetup will need to be able to configure that SOAP service by adding banks, accounts, and transactions.

As this is a relatively new feature, it is possible there are still some issues. Notably, if your Connector implementation only supports a single bank, the tests for the API calls returning the supported banks may fail, as they assume the connector can be configured to support multiple banks. This issue will be resolved in future releases. If all non-bank specific API tests pass, you will be in good shape.


**OBPDataImport** (src/main/scala/code/sandbox/OBPDataImport.scala

This is optional. It is a way to import banks/users/accounts/transactions via a json api call. You will probably not want to use it, but if you do, you will need to implement this trait and edit

	def buildOne : OBPDataImport = DummyDataImport

in OBPDataImport 

to read

	def buildOne: Connector = MyNewDataImport

where MyNewDataImport  is the name of your implementation of the OBPDataImport trait.

You will also need to set “sandbox_data_import_secret” in your .props file to some value (preferably a UUID). The api call to import data via json requires the sandbox_data_import_secret url parameter to match the value set in the props file in order to work (it is considered a private API call and is used primarily for setting up sandbox environments).


###Customising the default oAuth login page

The template for the login page is found at src/main/webapp/oauth/authorize.html , and is governed by the standard templating rules of the Lift web framework (see https://www.assembla.com/wiki/show/liftweb/Templates_and_Binding for more details)

The short version is: You may edit this html as long as you take care not to remove any class attributes starting with “lift:”, or edit any existing id or name attributes. If you wish to include css or js files, it can be done in the standard way:

	<link href="/media/css/website.css?20140425" rel="stylesheet" type="text/css" />
	<script src="/media/js/website.js" type="text/javascript"></script>

Where the root source path is src/main/webapp. So in the example above, the css file at src/main/webapp/media/css/website.css?20140425 will be used, as well as the js file at src/main/webapp/media/js/website.js


###Adding new API methods

API routes are defined as partial functions matching a request with a function to be executed upon that request. 

The function that is executed on a match has an input parameter of Box[User], and should output a JsonResponse. 

The Box[User] will be provided by the oAuth layer: it will be a full box if there is an oAuth user associated with the request, or an Empty box if oAuth is not being used.

An example of a route handler is below:

    lazy val bankById : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      //get bank by id
      case "banks" :: BankId(bankId) :: Nil JsonGet json => {
        user =>
          def bankToJson(bank : Bank) : JValue = {
            val bankJSON = JSONFactory.createBankJSON(bank)
            Extraction.decompose(bankJSON)
          }
          for(bank <- Bank(bankId))
          yield successJsonResponse(bankToJson(bank))
      }
    }

This will match routes of the format /banks/SOME_BANK_ID and then execute a function that ignores the user parameter (i.e. this call does not require oauth). 

In order to add a new route to an api version, you will need to locate the appropriate file, e.g. for version 1.3.0 this would be src/main/scala/code/api/v1_3_0/OBPAPI1_3_0.scala. You will then need to add this new route to the “routes” list.


Warning: it may be difficult to understand this process and how to add new API calls without knowledge of Scala.

##License Note

Reminder: Open Bank Project is dual licensed under the AGPL and commercial licenses. If you fork, please contribute your changes via a pull request or purchase a commercial license from TESOBE or one of our partners. Contributors should sign our CLA.  

**Good luck! :-)**
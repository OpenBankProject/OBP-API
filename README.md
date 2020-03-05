# README

The Open Bank Project API

## ABOUT

The Open Bank Project is an open source API for banks that enables account holders to interact with their bank using a wider range of applications and services.

The OBP API supports transparency options (enabling account holders to share configurable views of their transaction data with trusted individuals and even the public), data blurring (to preserve sensitive information) and data enrichment (enabling users to add tags, comments and images to transactions).

Thus, the OBP API abstracts away the peculiarities of each core banking system so that a wide range of apps can interact with  multiple banks on behalf of the account holder. We want to raise the bar of financial transparency and enable a rich ecosystem of innovative financial applications and services.

Our tag line is: Bank as a Platform. Transparency as an Asset.

The API supports OAuth 1.0a, OAuth 2, OpenID Connect and other authentication methods. See [here](https://github.com/OpenBankProject/OBP-API/wiki/Authentication) for more information.

The project roadmap is available [here.](https://github.com/OpenBankProject/OBP-API/blob/develop/roadmap.md)

## DOCUMENTATION 

The API documentation is best viewed using the OBP API Explorer or a third party tool that has imported the OBP Swagger definitions.
Please refer to the [wiki](https://github.com/OpenBankProject/OBP-API/wiki) for links. 

## STATUS of API Versions

OBP instances support multiple versions of the API simultaniously (unless they are deactivated in config)
To see the status (DRAFT, STABLE or BLEEDING-EDGE) of an API version, look at the root endpoint e.g. /obp/v2.0.0/root or /obp/v3.0.0/root

On the 8th of June 2017, [V2.0.0](https://apisandbox.openbankproject.com/obp/v2.0.0/root) was marked as stable.

## LICENSE
.
This project is dual licensed under the AGPL V3 (see NOTICE) and commercial licenses from TESOBE GmbH.

## SETUP

The project uses Maven 3 as its build tool.

To compile and run jetty, install Maven 3, create your configuration in obp-api/src/main/resources/props/default.props and execute:

     mvn install -pl .,obp-commons && mvn jetty:run -pl obp-api

## To run with IntelliJ IDEA

* Make sure you have the IntelliJ Scala plugin installed.

* Create a new folder e.g. OpenBankProject and cd there

* git clone https://github.com/OpenBankProject/OBP-API.git

* In IntelliJ IDEA do File -> New -> Project from existing sources, navigate to the folder and select pom.xml

* Alternatively you can do File -> New -> Project from VCS and checkout the project directly from github.

* When / if prompted for SDK, choose Java 1.8 (and Scala 2.12) otherwise keep the defaults. Use the Maven options. Do not change the project name etc.

* If you see a message about an unmanaged pom.xml, click the option to let Maven manage it.

* Navigate to obp-api/test/scala/code/RunWebApp. You may see a Setup Scala SDK link. Click this and check Scala 2.12.4 or so.

* In obp-api/src/main/resources/props create a test.default.props for tests. Set connector=mapped

* In obp-api/src/main/resources/props create a \<yourloginname\>.props (or default.props) for development. Set connector=mapped

* Now **Rebuild** the project so everything is compiled. At this point you may need to select the SDK, see above.

* Once you have rebuilt the project without compile errors, you should be able to RunWebApp in obp-api/src/test/scala

* If you have trouble (re)building, try using the IntelliJ IDEA terminal: mvn clean test-compile

* Run RunWebApp by right clicking on it or selecting Run. The built in jetty server should start on localhost:8080

* Browse to localhost:8080 but don't try anything else there yet.

### Run some tests.
  
* Run a single test. For instance right click on obp-api/test/scala/code/branches/MappedBranchProviderTest and select Run Mapp...

* Run multiple tests: Right click on obp-api/test/scala/code and select Run. If need be:
    Goto Run / Debug configurations
    Test Kind: Select All in Package
    Package: Select code
    Add the absolute /path-to-your-OBP-API in the "working directory" field
    You might need to assign more memory via VM Options: e.g. -Xmx1512M -XX:MaxPermSize=512M

    or

    -Xmx2048m -Xms1024m -Xss2048k -XX:MaxPermSize=1024m
    
    Make sure your test.default.props has the minimum settings (see test.default.props.template)

    
    Right click obp-api/test/scala/code and select the Scala Tests in code to run them all.
    
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

Note you can change the log level in /obp-api/src/main/resources/logback.xml (try TRACE or DEBUG)

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

## Administrator role / SuperUser

In the API's props file, add the ID of your user account to `super_admin_user_ids=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`. User Id can be retrieved via the "Get User (Current)" endpoint (e.g. /obp/v4.0.0/users/current) after login or via API Explorer (https://github.com/OpenBankProject/API-Explorer) at `/#OBPv3_0_0-getCurrentUser`.

Super users can give themselves any entitlement, but it is recommended to use this props only for bootstrapping (creating the first admin user). Use this admin user to create further priviledged users by granting them the "CanCreateEntitlementAtAnyBank" role. This, again, can be done via API Explorer (`/#OBPv2_0_0-addEntitlement`, leave `bank_id` empty) or, more conveniently, via API Manager (https://github.com/OpenBankProject/API-Manager).

## Sandbox data

To populate the OBP database with sandbox data:

1) In the API's props file, set `allow_sandbox_data_import=true`
2) Grant your user the role `CanCreateSandbox`. See previous section on how to do this
5) Now post the JSON data using the payload field at `/#2_1_0-sandboxDataImport`
6) If successful you should see an empty result `{}` and no error message



## Production Options.

* set the status of HttpOnly and Secure cookie flags for production, uncomment the following lines of  "webapp/WEB-INF/web.xml" :

        <session-config>
          <cookie-config>
            <secure>true</secure>
            <http-only>true</http-only>
          </cookie-config>
        </session-config>

## Running the API in Production Mode

We use 9 to run the API in production mode.

1) Install java and jetty9

2) jetty configuration

* Edit the /etc/default/jetty9 file so that it contains the following settings:

        NO_START=0
        JETTY_HOST=127.0.0.1 #If you want your application to be accessed from other hosts, change this to your IP address
        JAVA_OPTIONS="-Drun.mode=production -XX:PermSize=256M -XX:MaxPermSize=512M -Xmx768m -verbose -Dobp.resource.dir=$JETTY_HOME/resources -Dprops.resource.dir=$JETTY_HOME/resources"

* In obp-api/src/main/resources/props create a test.default.props file for tests. Set connector=mapped

* In obp-api/src/main/resources/props create a default.props file for development. Set connector=mapped

* In obp-api/src/main/resources/props create a production.default.props file for production. Set connector=mapped.

* This file could be similar to the default.props file created above, or it could include production settings, such as information about Postgresql server, if you are using one. For example, it could have the following line for postgresql configuration.

        db.driver=org.postgresql.Driver
        db.url=jdbc:postgresql://localhost:5432/yourdbname?user=yourdbusername&password=yourpassword

* Now, build the application to generate .war file which will be deployed on jetty server:

        cd OBP-API/
        mvn package

* This will generate OBP-API-1.0.war under OBP-API/target/

* Copy OBP-API-1.0.war to /usr/share/jetty9/webapps/ directory and rename it to root.war

* Edit the /etc/jetty9/jetty.conf file and comment out the lines:

        etc/jetty-logging.xml
        etc/jetty-started.xml

* Now restart jetty9:

        sudo service jetty9 restart

* You should now be able to browse to localhost:8080 (or yourIPaddress:8080)

## Using OBP-API in different app modes

1) `portal` => OBP-API as a portal i.e. without REST API 
2) `apis` => OBP-API as a apis app i.e. only REST APIs
3) `apis,portal`=> OBP-API as portal and apis i.e. REST APIs and web portal

* Edit your props file(s) to contain one of the next cases:
        
        1) server_mode=portal
        2) server_mode=apis
        3) server_mode=apis,portal
        In case is not defined default case is the 3rd one i.e. server_mode=apis,portal

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
Please refer to the [Code Generation](https://github.com/OpenBankProject/OBP-API/blob/develop/CONTRIBUTING.md##code-generation) for links

## Using jetty password obfuscation with props file

You can obfuscate passwords in the props file the same way as for jetty:

1. Create the obfuscated value as described here: https://www.eclipse.org/jetty/documentation/9.3.x/configuring-security-secure-passwords.html

2. A props key value, XXX, is considered obfuscated if has an obfuscation property (XXX.is_obfuscated) in addition to the regular props key name in the props file e.g:

   *  db.url.is_obfuscated=true
   *  db.url=OBF:fdsafdsakwaetcetcetc

## Rate Limiting
We support rate limiting i.e functionality to limit calls per consumer key (App). Only `New Style Endpoins` support it. The list of they can be found at this file: https://github.com/OpenBankProject/OBP-API/blob/develop/obp-api/src/main/scala/code/api/util/NewStyle.scala. 
There are two supported modes:
   *  In-Memory
   *  Redis
   
It is assumed that you have some Redis instance if you wan to use the functionality in multi node architecture.

To set up Rate Limiting in case of In-Memory mode edit your props file in next way:
```
use_consumer_limits_in_memory_mode=true
``` 

We apply Rate Limiting for two type of access:
   *  Authorized
   *  Anonymous

To set up Rate Limiting in case of the anonymous access edit your props file in next way:
```
user_consumer_limit_anonymous_access=100, In case isn't defined default value is 60
```
   
Te set up Rate Limiting in case of the authorized access use these endpoints
1. `GET ../management/consumers/CONSUMER_ID/consumer/call-limits` - Get Call Limits for a Consumer
2. `PUT ../management/consumers/CONSUMER_ID/consumer/call-limits` - Set Call Limits for a Consumer


In order to make it work edit your props file in next way:

```
use_consumer_limits=false, In case isn't defined default value is "false"
redis_address=YOUR_REDIS_URL_ADDRESS, In case isn't defined default value is 127.0.0.1
redis_port=YOUR_REDIS_PORT, In case isn't defined default value is 6379
```


Next types are supported:
```
1. per second
2. per minute
3. per hour
4. per day
5. per week
6. per month
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

Please note that first will be checked `per second` call limit then `per minute` etc.

Info about rate limiting availability at some instance can be found over next API endpoint: https://apisandbox.openbankproject.com/obp/v3.1.0/rate-limiting. Response we are interested in looks lke:
```json
{
  "enabled": false,
  "technology": "REDIS",
  "service_available": false,
  "is_active": false
}
```

## Webhooks
Webhooks are used to call external URLs when certain events happen.
Account Webhooks focus on events around accounts.
For instance, a webhook could be used to notify an external service if a balance changes on an account.
This functionality is work in progress!

There are 3 API's endpoint related to webhooks:
1. `POST ../banks/BANK_ID/account-web-hooks` - Create an Account Webhook
2. `PUT ../banks/BANK_ID/account-web-hooks` - Enable/Disable an Account Webhook
3. `GET ../management/banks/BANK_ID/account-web-hooks` - Get Account Webhooks
---
## OAuth 2.0
In order to enable an OAuth2 workflow at an instance of OBP-API backend app you need to setup next props:
```
# -- OAuth 2 ---------------------------------------------------------------------------------
# Enable/Disable OAuth 2 workflow at a server instance
# In case isn't defined default value is false
# allow_oauth2_login=false
# URL of Public server JWK set used for validating bearer JWT access tokens
# It can contain more than one URL i.e. list of uris. Values are comma separated.
# If MITREId URL is present it must be at 1st place in the list
# because MITREId URL can be an appropirate value and we cannot rely on it.
# oauth2.jwk_set.url=http://localhost:8080/jwk.json,https://www.googleapis.com/oauth2/v3/certs
# ------------------------------------------------------------------------------ OAuth 2 ------

OpenID Connect is supported.
Tested Identity providers: Google, MITREId.

```
### Example for Google's OAuth 2.0 implementation for authentication, which conforms to the OpenID Connect specification
```
allow_oauth2_login=true
oauth2.jwk_set.url=https://www.googleapis.com/oauth2/v3/certs
```
---

## Frozen APIs
API versions may be marked as "STABLE", if changes are made to an API which has been marked as "STABLE", then unit test `FrozenClassTest`  will fail.
### Changes to "STABLE" api cause the tests fail: 
* modify request or response body structure of apis
* add or delete apis
* change the apis versionStatus from or to "STABLE"

If it is required for a "STABLE" api to be changed, then the class metadata must be regenerated using the FrozenClassUtil (see how to freeze an api)
### Steps to freeze an api
* Run the FrozenClassUtil to regenerate persist file of frozen apis information, the file is `PROJECT_ROOT_PATH/obp-api/src/test/resources/frozen_type_meta_data`
* push the file `frozen_type_meta_data` to github

There is a video about the detail: [demonstrate the detail of the feature](https://www.youtube.com/watch?v=m9iYCSM0bKA)

## Frozen Connector InBound OutBound types
The same as `Frozen APIs`, if related unit test fail, make sure whether the modify is required, if yes, run frozen util to re-generate frozen types metadata file. take `RestConnector_vMar2019` as example, the corresponding util is `RestConnector_vMar2019_FrozenUtil`, the corresponding unit test is `RestConnector_vMar2019_FrozenTest`

## Scala / Lift

* We use scala and liftweb http://www.liftweb.net/

* Advanced architecture: http://exploring.liftweb.net/master/index-9.html

* A good book on Lift: "Lift in Action" by Timothy Perrett published by Manning.

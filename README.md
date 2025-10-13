# ReadMe

The Open Bank Project API

## About

The Open Bank Project is an open-source API for banks that enables account holders to interact with their bank using a wider range of applications and services.

The OBP API supports transparency options (enabling account holders to share configurable views of their transaction data with trusted individuals and even the public), data blurring (to preserve sensitive information) and data enrichment (enabling users to add tags, comments and images to transactions).

The OBP API abstracts away the peculiarities of each core banking system so that a wide range of apps can interact with multiple banks on behalf of the account holder. We want to raise the bar of financial transparency and enable a rich ecosystem of innovative financial applications and services.

Our tagline is: "Bank as a Platform. Transparency as an Asset".

The API supports [OAuth 1.0a](https://apiexplorer-ii-sandbox.openbankproject.com/glossary#OAuth%201.0a), [OAuth 2](https://apiexplorer-ii-sandbox.openbankproject.com/glossary#OAuth%202), [OpenID Connect OIDC](https://apiexplorer-ii-sandbox.openbankproject.com/glossary#OAuth%202%20with%20Google) and other authentication methods including [Direct Login](https://apiexplorer-ii-sandbox.openbankproject.com/glossary#Direct%20Login).

## Documentation

The API documentation is best viewed using the [OBP API Explorer](https://apiexplorer-ii-sandbox.openbankproject.com) or a third-party tool that has imported the OBP Swagger definitions.

If you want to run your own copy of API Explorer II, see [here](https://github.com/OpenBankProject/API-Explorer-II)

## Status of API Versions

OBP instances support multiple versions of the API simultaneously (unless they are deactivated in config)
To see the status (DRAFT, STABLE or BLEEDING-EDGE) of an API version, look at the root endpoint. For example, `/obp/v2.0.0/root` or `/obp/v3.0.0/root`.

```log
24.01.2017, [V1.2.1](https://apisandbox.openbankproject.com/obp/v1.2.1/root) was marked as stable.
24.01.2017, [V1.3.0](https://apisandbox.openbankproject.com/obp/v1.3.0/root) was marked as stable.
08.06.2017, [V2.0.0](https://apisandbox.openbankproject.com/obp/v2.0.0/root) was marked as stable.
27.10.2018, [V2.1.0](https://apisandbox.openbankproject.com/obp/v2.1.0/root) was marked as stable.
27.10.2018, [V2.2.0](https://apisandbox.openbankproject.com/obp/v2.2.0/root) was marked as stable.
18.11.2020, [V3.0.0](https://apisandbox.openbankproject.com/obp/v3.0.0/root) was marked as stable.
18.11.2020, [V3.1.0](https://apisandbox.openbankproject.com/obp/v3.1.0/root) was marked as stable.
16.12.2022, [V4.0.0](https://apisandbox.openbankproject.com/obp/v4.0.0/root) was marked as stable.
16.12.2022, [V5.0.0](https://apisandbox.openbankproject.com/obp/v5.0.0/root) was marked as stable.
```

## License

This project is dual licensed under the AGPL V3 (see NOTICE) and commercial licenses from TESOBE GmbH.

## Setup

### Installing JDK
#### With sdkman

A good way to manage JDK versions and install the correct version for OBP is [sdkman](https://sdkman.io/). If you have this installed then you can install the correct JDK easily using:
```
sdk env install
```

#### Manually

- OracleJDK: 1.8, 13
- OpenJdk: 11

OpenJDK 11 is available for download here: [https://jdk.java.net/archive/](https://jdk.java.net/archive/).

The project uses Maven 3 as its build tool.

To compile and run Jetty, install Maven 3, create your configuration in `obp-api/src/main/resources/props/default.props` and execute:
To compile and run Jetty, install Maven 3, create your configuration in `obp-api/src/main/resources/props/`, copy `sample.props.template` to `default.props` and edit the latter. Then:

```sh
mvn install -pl .,obp-commons && mvn jetty:run -pl obp-api
```

### ZED IDE Setup

For ZED IDE users, we provide a complete development environment with Scala language server support:

```bash
./zed/setup-zed-ide.sh
```

This sets up automated build tasks, code navigation, and real-time error checking. See [`zed/README.md`](zed/README.md) for complete documentation.

In case the above command fails try the next one:

```sh
export MAVEN_OPTS="-Xss128m" && mvn install -pl .,obp-commons && mvn jetty:run -pl obp-api
```

Note: depending on your Java version you might need to do this in the OBP-API directory.
This creates a .mvn/jvm.config File

```sh
mkdir -p .mvn
cat > .mvn/jvm.config << 'EOF'
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens java.base/java.security=ALL-UNNAMED
--add-opens java.base/java.util.jar=ALL-UNNAMED
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/java.net=ALL-UNNAMED
--add-opens java.base/java.io=ALL-UNNAMED
EOF
```

Then try the above command.

Or use this approach:

```sh

export MAVEN_OPTS="-Xss128m \
 --add-opens=java.base/java.util.jar=ALL-UNNAMED \
 --add-opens=java.base/java.lang=ALL-UNNAMED \
 --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"

```

[Note: How to run via IntelliJ IDEA](obp-api/src/main/docs/glossary/Run_via_IntelliJ_IDEA.md)

## Run some tests

- In `obp-api/src/main/resources/props` create a `test.default.props` for tests. Set `connector=mapped`.

- Run a single test. For instance, right-click on `obp-api/test/scala/code/branches/MappedBranchProviderTest` and select "Run Mapp"...

- Run multiple tests: Right-click on `obp-api/test/scala/code` and select Run. If need be:

  Goto Run / Debug configurations
  Test Kind: Select All in Package
  Package: Select code
  Add the absolute /path-to-your-OBP-API in the "working directory" field
  You might need to assign more memory via VM Options. For example:

  ```
  -Xmx1512M -XX:MaxPermSize=512M
  ```

  or

  ```
  -Xmx2048m -Xms1024m -Xss2048k -XX:MaxPermSize=1024m
  ```

  Ensure your `test.default.props` has the minimum settings (see `test.default.props.template`).

  Right-click `obp-api/test/scala/code` and select the Scala Tests in the code to run them all.

  Note: You may want to disable some tests not relevant to your setup e.g.:
  set `bank_account_creation_listener=false` in `test.default.props`.

## Other ways to run tests

- See `pom.xml` for test configuration.
- See http://www.scalatest.org/user_guide.

## From the command line

Set memory options:

```sh
export MAVEN_OPTS="-Xmx3000m -Xss2m"
```

Run one test:

```sh
mvn -DwildcardSuites=code.api.directloginTest test
```

Run all tests and save the output to a file:

```sh
export MAVEN_OPTS="-Xss128m" && mvn clean test | tee obp-api-test-results.txt
```

## Ubuntu

If you use Ubuntu (or a derivate) and encrypted home directories (e.g. you have ~/.Private), you might run into the following error when the project is built:

```log
uncaught exception during compilation: java.io.IOException
[ERROR] File name too long
[ERROR] two errors found
[DEBUG] Compilation failed (CompilerInterface)
```

The current workaround is to move the project directory onto a different partition, e.g. under `/opt/`.

## Running the docker image

Docker images of OBP API can be found on Dockerhub: https://hub.docker.com/r/openbankproject/obp-api - pull with `docker pull openbankproject/obp-api`.

Props values can be set as environment variables. Props need to be prefixed with `OBP_`, `.` replaced with `_`, and all upper-case, e.g.:

`openid_connect.enabled=true` becomes `OBP_OPENID_CONNECT_ENABLED=true`.

## Databases

The default database for testing etc is H2. PostgreSQL is used for the sandboxes (user accounts, metadata, transaction cache). The list of databases fully tested is: PostgreSQL, MS SQL and H2.

### Notes on using H2 web console in Dev and Test mode:

Set DB options in the props file:

```
db.driver=org.h2.Driver
db.url=jdbc:h2:./obp_api.db;DB_CLOSE_ON_EXIT=FALSE
```

In order to start H2 web console go to [http://127.0.0.1:8080/console](http://127.0.0.1:8080/console) and you will see a login screen.
Please use the following values:
Note: make sure the JDBC URL used matches your Props value!

```
Driver Class: org.h2.Driver
JDBC URL: jdbc:h2:./obp_api.db;AUTO_SERVER=FALSE
User Name:
Password:
```

### Notes on the basic usage of Postgres

Once Postgres is installed (On macOS, use `brew`):

1.  ```sh
    psql postgres
    ```

1.  Create database `obpdb`; (or any other name of your choosing).

1.  Create user `obp`; (this is the user that OBP-API will use to create and access tables etc).

1.  Alter user obp with password `daniel.says`; (put this password in the OBP-API Props).

1.  Grant all on database `obpdb` to `obp`; (So OBP-API can create tables etc.)

#### For newer versions of postgres 16 and above, you need to follow the following instructions

-- Connect to the sandbox database
\c sandbox;

-- Grant schema usage and creation privileges
GRANT USAGE ON SCHEMA public TO obp;
GRANT CREATE ON SCHEMA public TO obp;

-- Grant all privileges on existing tables (if any)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO obp;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO obp;

-- Grant privileges on future tables and sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO obp;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO obp;

1.  Then, set the `db.url` in your Props:

    ```
    db.driver=org.postgresql.Driver
    db.url=jdbc:postgresql://localhost:5432/obpdb?user=obp&password=daniel.says
    ```

1.  Then, restart OBP-API.

### Notes on using Postgres with SSL

Postgres needs to be compiled with SSL support.

Use OpenSSL to create the files you need.

For the steps, see [https://www.howtoforge.com/postgresql-ssl-certificates](https://www.howtoforge.com/postgresql-ssl-certificates).

In short, edit `postgresql.conf`:

```
ssl = on
```

```
ssl_cert_file = '/etc/YOUR-DIR/server.crt'
```

```
ssl_key_file = '/etc/YOUR-DIR/server.key'
```

And restart Postgres.

Now, this should enable SSL (on the same port that Postgres normally listens on) - but it doesn't force it.
To force SSL, edit pg_hba.conf replacing the host entries with `hostssl`.

Now in OBP-API Props, edit your `db.url` and add `&ssl=true`. For example:

```
db.url=jdbc:postgresql://localhost:5432/my_obp_database?user=my_obp_user&password=the_password&ssl=true
```

Note: Your Java environment may need to be set up correctly to use SSL.

Restart OBP-API, if you get an error, check your Java environment can connect to the host over SSL.

Note: You can copy the following example files to prepare your own configurations:

- `/obp-api/src/main/resources/logback.xml.example` -> `/obp-api/src/main/resources/logback.xml` (try TRACE or DEBUG).
- `/obp-api/src/main/resources/logback-test.xml.example` -> `/obp-api/src/main/resources/logback-test.xml` (try TRACE or DEBUG).

There is a gist/tool which is useful for this. Search the web for SSLPoke. Note this is an external repository.

For example:

- [https://gist.github.com/4ndrej/4547029](https://gist.github.com/4ndrej/4547029/84d3bff7bba262b3f77baa32a43873ea95993e45#file-sslpoke-java-L1-L40)

  or

- ```sh
  git clone https://github.com/MichalHecko/SSLPoke.git .

  gradle jar
  cd ./build/libs/

  java -jar SSLPoke-1.0.jar www.github.com 443
  ```

  > Successfully connected

  ```sh
  java -jar SSLPoke-1.0.jar YOUR-POSTGRES-DATABASE-HOST PORT
  ```

You can add switches. For example, for debugging:

```sh
java -jar -Dhttps.protocols=TLSv1.1,TLSv1.2 -Djavax.net.debug=all SSLPoke-1.0.jar localhost 5432
```

To import a certificate:

```sh
keytool -import -storepass changeit -noprompt -alias localhost_postgres_cert -keystore /Library/Java/JavaVirtualMachines/jdk1.8.0_73.jdk/Contents/Home/jre/lib/security/cacerts -trustcacerts -file /etc/postgres_ssl_certs/server/server.crt
```

To get a certificate from the server / get further debug information:

```sh
openssl s_client -connect ip:port
```

The above section is work in progress.

## Administrator role / SuperUser

In the API's props file, add the ID of your user account to `super_admin_user_ids=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`. User Id can be retrieved via the "Get User (Current)" endpoint (e.g. /obp/v4.0.0/users/current) after login or via API Explorer (https://github.com/OpenBankProject/API-Explorer) at `/#OBPv3_0_0-getCurrentUser`.

Super users can give themselves any entitlement, but it is recommended to use this props only for bootstrapping (creating the first admin user). Use this admin user to create further privileged users by granting them the "CanCreateEntitlementAtAnyBank" role. This, again, can be done via API Explorer (`/#OBPv2_0_0-addEntitlement`, leave `bank_id` empty) or, more conveniently, via API Manager (https://github.com/OpenBankProject/API-Manager).

## Sandbox data

To populate the OBP database with sandbox data:

1. In the API's props file, set `allow_sandbox_data_import=true`.
2. Grant your user the role `CanCreateSandbox`. See the previous section on how to do this.
3. Now, post the JSON data using the payload field at `/#2_1_0-sandboxDataImport`. An example of an import set of data (JSON) can be found [here](https://raw.githubusercontent.com/OpenBankProject/OBP-API/develop/obp-api/src/main/scala/code/api/sandbox/example_data/2016-04-28/example_import.json).
4. If successful you should see this result `{ "success": "Success" }` and no error message.

## Production Options

- set the status of HttpOnly and Secure cookie flags for production, uncomment the following lines of `webapp/WEB-INF/web.xml`:

```XML
   <session-config>
     <cookie-config>
       <secure>true</secure>
       <http-only>true</http-only>
     </cookie-config>
   </session-config>
```

## Running the API in Production Mode

We use 9 to run the API in production mode.

1. Install java and jetty9.

2. jetty configuration

- Edit the `/etc/default/jetty9` file so that it contains the following settings:

  ```
  NO_START=0
  JETTY_HOST=127.0.0.1 #If you want your application to be accessed from other hosts, change this to your IP address
  JAVA_OPTIONS="-Drun.mode=production -XX:PermSize=256M -XX:MaxPermSize=512M -Xmx768m -verbose -Dobp.resource.dir=$JETTY_HOME/resources -Dprops.resource.dir=$JETTY_HOME/resources"
  ```

- In obp-api/src/main/resources/props create a `test.default.props` file for tests. Set `connector=mapped`.

- In obp-api/src/main/resources/props create a `default.props file` for development. Set `connector=mapped`.

- In obp-api/src/main/resources/props create a `production.default.props` file for production. Set `connector=mapped`.

- This file could be similar to the `default.props` file created above, or it could include production settings, such as information about the Postgresql server if you are using one. For example, it could have the following line for Postgresql configuration.

  ```
  db.driver=org.postgresql.Driver
  db.url=jdbc:postgresql://localhost:5432/yourdbname?user=yourdbusername&password=yourpassword
  ```

- Now, build the application to generate `.war` file which will be deployed on the jetty server:

  ```sh
  cd OBP-API/
  mvn package
  ```

- This will generate OBP-API-1.0.war under `OBP-API/target/`.

- Copy OBP-API-1.0.war to `/usr/share/jetty9/webapps/` directory and rename it to root.war

- Edit the `/etc/jetty9/jetty.conf` file and comment out the lines:

  ```
  etc/jetty-logging.xml
  etc/jetty-started.xml
  ```

- Now restart jetty9:

  ```sh
  sudo service jetty9 restart
  ```

- You should now be able to browse to `localhost:8080` (or `yourIPaddress:8080`).

## Using OBP-API in different app modes

1. `portal` => OBP-API as a portal i.e. without REST API.
2. `apis` => OBP-API as an _APIs_ app i.e. only REST APIs.
3. `apis,portal`=> OBP-API as portal and apis i.e. REST APIs and web portal.

- Edit your props file(s) to contain one of the next cases:
  1.  `server_mode=portal`
  2.  `server_mode=apis`
  3.  `server_mode=apis,portal`

  In case it is not defined, the default case is the 3rd one. For example, `server_mode=apis,portal`.

## Using Akka remote storage

Most internal OBP model data access now occurs over Akka. This is so the machine that has JDBC access to the OBP database can be physically separated from the OBP API layer. In this configuration we run two instances of OBP-API on two different machines and they communicate over Akka. Please see README.Akka.md for instructions.

## Using SSL Encryption with RabbitMq

For SSL encryption we use JKS keystores. Note that both the keystore and the truststore (and all keys within) must have the same password for unlocking, for which the API will stop at boot up and ask for.

- Edit your props file(s) to contain:

  ```
   rabbitmq.use.ssl=true
   keystore.path=/path/to/api.keystore.jks
   keystore.password=123456
   truststore.path=/path/to/api.truststore.jks
  ```

## Using SSL Encryption with props file

For SSL encryption we use jks keystores.
Note that keystore (and all keys within) must have the same password for unlocking, for which the API will stop at boot up and ask for.

- Edit your props file(s) to contain:

  ```
  jwt.use.ssl=true
  keystore.path=/path/to/api.keystore.jks
  keystore.alias=SOME_KEYSTORE_ALIAS
  ```

A props key value, XXX, is considered encrypted if has an encryption property (XXX.is_encrypted) in addition to the regular props key name in the props file e.g:

- db.url.is_encrypted=true
- db.url=BASE64URL(SOME_ENCRYPTED_VALUE)

The Encrypt/Decrypt workflow is :

1. Encrypt: Array[Byte]
2. Helpers.base64Encode(encrypted)
3. Props file: String
4. Helpers.base64Decode(encryptedValue)
5. Decrypt: Array[Byte]

1st, 2nd and 3rd step can be done using an external tool

### Encrypting props values with OpenSSL on the command line

1. Export the public certificate from the keystone:

   ```sh
   keytool -export -keystore /PATH/TO/KEYSTORE.jks -alias CERTIFICATE_ALIAS -rfc -file apipub.cert
   ```

2. Extract the public key from the public certificate:

   ```sh
   openssl x509 -pubkey -noout -in apipub.cert > PUBKEY.pub`
   ```

3. Get the encrypted `propsvalue` like in the following bash script (usage `./scriptname.sh /PATH/TO/PUBKEY.pub propsvalue`):

   ```
   #!/bin/bash
   echo -n $2 |openssl pkeyutl -pkeyopt rsa_padding_mode:pkcs1 -encrypt  -pubin -inkey $1 -out >(base64)
   ```

## Using jetty password obfuscation with props file

You can obfuscate passwords in the props file the same way as for jetty:

1. Create the obfuscated value as described here: [https://www.eclipse.org/jetty/documentation/9.3.x/configuring-security-secure-passwords.html](https://www.eclipse.org/jetty/documentation/9.3.x/configuring-security-secure-passwords.html).

2. A props key value, XXX, is considered obfuscated if has an obfuscation property (`XXX.is_obfuscated`) in addition to the regular props key name in the props file e.g:
   - `db.url.is_obfuscated=true`
   - `db.url=OBF:fdsafdsakwaetcetcetc`

## Code Generation

Please refer to the [Code Generation](https://github.com/OpenBankProject/OBP-API/blob/develop/CONTRIBUTING.md##code-generation) for links.

## Customize Portal WebPage

Please refer to the [Custom Webapp](obp-api/src/main/resources/custom_webapp/README.md) for links.

## Using jetty password obfuscation with props file

You can obfuscate passwords in the props file the same way as for jetty:

1. Create the obfuscated value as described here: [https://www.eclipse.org/jetty/documentation/9.3.x/configuring-security-secure-passwords.html](https://www.eclipse.org/jetty/documentation/9.3.x/configuring-security-secure-passwords.html).

2. A props key value, XXX, is considered obfuscated if has an obfuscation property (XXX.is_obfuscated) in addition to the regular props key name in the props file e.g:
   - db.url.is_obfuscated=true
   - db.url=OBF:fdsafdsakwaetcetcetc

## Rate Limiting

We support rate limiting i.e functionality to limit calls per consumer key (App). Only `New Style Endpoins` support it. The list of they can be found at this file: [https://github.com/OpenBankProject/OBP-API/blob/develop/obp-api/src/main/scala/code/api/util/NewStyle.scala](https://github.com/OpenBankProject/OBP-API/blob/develop/obp-api/src/main/scala/code/api/util/NewStyle.scala).

There are two supported modes:

- In-Memory
- Redis

It is assumed that you have some Redis instances if you want to use the functionality in multi-node architecture.

We apply Rate Limiting for two types of access:

- Authorized
- Anonymous

To set up Rate Limiting in case of anonymous access edit your props file in the following way:

```
user_consumer_limit_anonymous_access=100, In case isn't defined default value is 60
```

Te set up Rate Limiting in case of the authorized access use these endpoints:

1. `GET ../management/consumers/CONSUMER_ID/consumer/call-limits` - Get Call Limits for a Consumer
2. `PUT ../management/consumers/CONSUMER_ID/consumer/call-limits` - Set Call Limits for a Consumer

In order to make it work edit your props file in next way:

```
use_consumer_limits=false, In case isn't defined default value is "false"
cache.redis.url=YOUR_REDIS_URL_ADDRESS, In case isn't defined default value is 127.0.0.1
cache.redis.port=YOUR_REDIS_PORT, In case isn't defined default value is 6379
```

The next types are supported:

1. per second
2. per minute
3. per hour
4. per day
5. per week
6. per month

If you exceed the rate limit per minute for instance you will get the response:

```JSON
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

1. `X-Rate-Limit-Limit` - The number of allowed requests in the current period.
2. `X-Rate-Limit-Remaining` - The number of remaining requests in the current period.
3. `X-Rate-Limit-Reset` - The number of seconds left in the current period.

Please note that first will be checked `per second` call limit then `per minute`, etc.

Info about rate limiting availability at some instance can be found over next API endpoint: https://apisandbox.openbankproject.com/obp/v3.1.0/rate-limiting. The response we are interested in looks like this:

```JSON
{
  "enabled": false,
  "technology": "REDIS",
  "service_available": false,
  "is_active": false
}
```

## Webhooks

Webhooks are used to call external URLs when certain events happen. Account Webhooks focus on events around accounts. For instance, a webhook could be used to notify an external service if a balance changes on an account. This functionality is a work in progress!

There are 3 API endpoints related to webhooks:

1. `POST ../banks/BANK_ID/account-web-hooks` - Create an Account Webhook
2. `PUT ../banks/BANK_ID/account-web-hooks` - Enable/Disable an Account Webhook
3. `GET ../management/banks/BANK_ID/account-web-hooks` - Get Account Webhooks

---

## OpenID Connect

In order to enable an OIDC workflow at an instance of OBP-API portal app(login functionality) you need to set up the following props:

```props
## Google as an identity provider
# openid_connect_1.client_secret=OYdWujJl******_NXzPlDI4T
# openid_connect_1.client_id=883**3244***-s4hi72j0rble0iiivq1gn09k7***tdci.apps.googleusercontent.com
# openid_connect_1.callback_url=http://127.0.0.1:8080/auth/openid-connect/callback
# openid_connect_1.endpoint.authorization=https://accounts.google.com/o/oauth2/v2/auth
# openid_connect_1.endpoint.userinfo=https://openidconnect.googleapis.com/v1/userinfo
# openid_connect_1.endpoint.token=https://oauth2.googleapis.com/token
# openid_connect_1.endpoint.jwks_uri=https://www.googleapis.com/oauth2/v3/certs
# openid_connect_1.access_type_offline=false
# openid_connect_1.button_text = Yahoo

## Yahoo as an identity provider
# openid_connect_2.client_secret=685d47412efd8b74891ad711876558189793e957
# openid_connect_2.client_id=zg0yJmk9WUEzaERzd1RtMU02JmQ9WVdrOU9FOHpTbXN5TkhNbWNHbzlNQS0tJnM9Y38uc3VtZXJzZWNyZXQmc3Y9MCZ4PWjW
# openid_connect_2.callback_url=https://1aaac045.ngrok.io/auth/openid-connect/callback-2
# openid_connect_2.endpoint.authorization=https://api.login.yahoo.com/oauth2/request_auth
# openid_connect_2.endpoint.userinfo=https://api.login.yahoo.com/openid/v1/userinfo
# openid_connect_2.endpoint.token=https://api.login.yahoo.com/oauth2/get_token
# openid_connect_2.endpoint.jwks_uri=https://api.login.yahoo.com/openid/v1/certs
# openid_connect_2.access_type_offline=true
# openid_connect_2.button_text = Yahoo
```

Please note in the example above you MUST run OBP-API portal at the URL: http://127.0.0.1:8080

## OAuth 2.0 Authentication

In order to enable an OAuth2 workflow at an instance of OBP-API backend app you need to set up the following props:

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

### OAuth2 JWKS URI Configuration

The `oauth2.jwk_set.url` property is critical for OAuth2 JWT token validation. OBP-API uses this to verify the authenticity of JWT tokens by fetching the JSON Web Key Set (JWKS) from the specified URI(s).

#### Configuration Methods

The `oauth2.jwk_set.url` property is resolved in the following order of priority:

1. **Environment Variable**

   ```bash
   export OBP_OAUTH2_JWK_SET_URL="https://your-oidc-server.com/jwks"
   ```

2. **Properties Files** (located in `obp-api/src/main/resources/props/`)
   - `production.default.props` (for production deployments)
   - `default.props` (for development)
   - `test.default.props` (for testing)

#### Supported Formats

- **Single URL**: `oauth2.jwk_set.url=http://localhost:9000/obp-oidc/jwks`
- **Multiple URLs**: `oauth2.jwk_set.url=http://localhost:8080/jwk.json,https://www.googleapis.com/oauth2/v3/certs`

#### Common OAuth2 Provider Examples

- **Google**: `https://www.googleapis.com/oauth2/v3/certs`
- **OBP-OIDC**: `http://localhost:9000/obp-oidc/jwks`
- **Keycloak**: `http://localhost:7070/realms/master/protocol/openid-connect/certs`
- **Azure AD**: `https://login.microsoftonline.com/common/discovery/v2.0/keys`

#### Troubleshooting OBP-20208 Error

If you encounter the error "OBP-20208: Cannot match the issuer and JWKS URI at this server instance", check the following:

1. **Verify JWT Issuer Claim**: The JWT token's `iss` (issuer) claim must match one of the configured identity providers
2. **Check JWKS URL Configuration**: Ensure `oauth2.jwk_set.url` contains URLs that correspond to your JWT issuer
3. **Case-Insensitive Matching**: OBP-API performs case-insensitive substring matching between the issuer and JWKS URLs
4. **URL Format Consistency**: Check for trailing slashes or URL formatting differences

**Debug Logging**: Enable debug logging to see detailed information about the matching process:

```properties
# Add to your logging configuration
logger.code.api.OAuth2=DEBUG
```

The debug logs will show:

- Expected identity provider vs actual JWT issuer claim
- Available JWKS URIs from configuration
- Matching logic results

---

## Frozen APIs

API versions may be marked as "STABLE", if changes are made to an API which has been marked as "STABLE", then unit test `FrozenClassTest` will fail.

### Changes to "STABLE" API cause the tests to fail:

- modify request or response body structure of APIs
- add or delete APIs
- change the APIS' `versionStatus` from or to "STABLE"

If it is required for a "STABLE" api to be changed, then the class metadata must be regenerated using the FrozenClassUtil (see how to freeze an API)

### Steps to freeze an API

- Run the FrozenClassUtil to regenerate persist file of frozen apis information, the file is `PROJECT_ROOT_PATH/obp-api/src/test/resources/frozen_type_meta_data`
- push the file `frozen_type_meta_data` to github

There is a video about the detail: [demonstrate the detail of the feature](https://www.youtube.com/watch?v=m9iYCSM0bKA)

## Frozen Connector InBound OutBound types

The same as `Frozen APIs`, if a related unit test fails, make sure whether the modification is required, if yes, run frozen util to re-generate frozen types metadata file. take `RestConnector_vMar2019` as an example, the corresponding util is `RestConnector_vMar2019_FrozenUtil`, the corresponding unit test is `RestConnector_vMar2019_FrozenTest`

## Scala / Lift

- We use scala and liftweb: [http://www.liftweb.net/](http://www.liftweb.net/).

- Advanced architecture: [http://exploring.liftweb.net/master/index-9.html
  ](http://exploring.liftweb.net/master/index-9.html).

- A good book on Lift: "Lift in Action" by Timothy Perrett published by Manning.

## Endpoint Request and Response Example

```log
ResourceDoc#exampleRequestBody and ResourceDoc#successResponseBody can be the follow type
```

- Any Case class
- JObject
- Wrapper JArray: JArrayBody(jArray)
- Wrapper String: StringBody("Hello")
- Wrapper primary type: IntBody(1), BooleanBody(true), FloatBody(1.2F)...
- Empty: EmptyBody

Example:

```
resourceDocs += ResourceDoc(
      exampleRequestBody= EmptyBody,
      successResponseBody= BooleanBody(true),
      ...
)
```

## Language support

### Add a new language

An additional language can be added via props `supported_locales`

Steps to add Spanish language:

- tweak the property supported_locales = en_GB to `supported_locales = en_GB,es_ES`
- add file `lift-core_es_ES.properties` at the folder `/resources/i18n`

Please note that default translation file is `lift-core.properties`

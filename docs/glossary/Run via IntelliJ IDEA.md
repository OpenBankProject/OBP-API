## To run via IntelliJ IDEA

* Make sure you have the IntelliJ Scala plugin installed.

* Create a new folder e.g. OpenBankProject and cd there

* git clone https://github.com/OpenBankProject/OBP-API.git

* In IntelliJ IDEA do File -> New -> Project from existing sources, navigate to the folder and select pom.xml

* Alternatively you can do File -> New -> Project from VCS and checkout the project directly from github.

* When / if prompted for SDK, choose Java 1.8 (and Scala 2.12) otherwise keep the defaults. Use the Maven options. Do not change the project name etc.

* If you see a message about an unmanaged pom.xml, click the option to let Maven manage it.

* Navigate to obp-api/test/scala/code/RunWebApp. You may see a Setup Scala SDK link. Click this and check Scala 2.12.12 or so.

* In obp-api/src/main/resources/props create a \<yourloginname\>.props (or default.props) for development. Set connector=mapped

* Now **Rebuild** the project so everything is compiled. At this point you may need to select the SDK, see above.

* Once you have rebuilt the project without compile errors, you should be able to RunWebApp/RunTLSWebApp/RunMTLSWebApp in obp-api/src/test/scala

* If you have trouble (re)building, try using the IntelliJ IDEA terminal: mvn clean test-compile

### To run via IntelliJ IDEA in development mode without secure connection

* Run RunWebApp by right clicking on it or selecting Run. The built in jetty server should start on localhost:8080

* Browse to localhost:8080 but don't try anything else there yet.

### To run via IntelliJ IDEA in TLS development mode (secure connection)

* Run RunTLSWebApp by right clicking on it and selecting Run/Debug. The built in jetty server should start on localhost:8080

* Browse to localhost:8080 but don't try anything else there yet

In `development` mode we use this option in order to try OpenID Connect functionality. I.e. redirect URI must be `https` one. 

### To run via IntelliJ IDEA in MTLS development mode (secure connection)

* Run RunMTLSWebApp by right clicking on it and selecting Run/Debug. The built in jetty server should start on localhost:8080

* Import certificate obp-api/src/test/resources/cert/localhost_SAN_dns_ip.pfx into your browser.

* Browse to localhost:8080 but don't try anything else there yet

In `development` mode we use this option in order to try UK Open Banking APIs functionality where mutual TLS is part of that standard.

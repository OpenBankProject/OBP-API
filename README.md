# README

Welcome to the Open Bank Project API 

## ABOUT

The Open Bank Project is an open source API for banks that enables account holders to interact with their transaction data using a wider range of applications and services. 
The OBP API supports transparency options (enabling account holders to share configurable views of their transaction data with trusted individuals and even the publi, 
data blurring (to preserve sensitive information) and data enrichment (enabling users to add tags, comments and images to transactions). 

Thus, the OBP API aims to abstract away the peculiarities of each core banking system so that a wide range of apps can read (and one day write) transactions to many banks.

The API provides OAuth 1.0 authentication.

## DOCUMENTATION 

Please refer to the [wiki](https://github.com/OpenBankProject/OBP-API/wiki) to see the API specification. 

## STATUS

Currently most of the read only API calls are implemented.

## LICENSE

This project is dual licensed under the AGPL V3 (see NOTICE) and a commercial license from TESOBE
Some files (OAuth related) are licensed under the Apache 2 license.

## SETUP

We recommend to use the Vagrant and Puppet scripts available [here](https://github.com/OpenBankProject/OBP-VM) to create a Virtual Box VM running the Open Bank Project API.

Other wise the project is using sbt or Maven 2 as a build tool.

----

To compile and run jetty, cd into the "MavLift" directory and run:

$ sbt
...
> compile
> ~;container:start; container:reload /

(Note that you first have to start sbt and then on its console start jetty with the container:start task, otherwise it will exit immediately. More here: https://github.com/siasia/xsbt-web-plugin/wiki)

In OS X, sbt can be installed with $ sudo port install sbt

----

Alternatively, maven can also be used:

mvn jetty:run

You need to install MongoDB and create an empty database called "OBP006".

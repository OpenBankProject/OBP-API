# README

Welcome to the Open Bank Project API 

## ABOUT

The Open Bank Project is an open source API for banks that enables account holders to interact with their transaction data using a wider range of applications and services.

The OBP API supports transparency options (enabling account holders to share configurable views of their transaction data with trusted individuals and even the public), data blurring (to preserve sensitive information) and data enrichment (enabling users to add tags, comments and images to transactions). 

Thus, the OBP API aims to abstract away the peculiarities of each core banking system so that a wide range of apps can read (and one day write) transactions to multiple banks. We want to raise the bar of financial transparency and enable a rich ecosystem of innovative financial applications and services.

Our tag line is: Bank as a Platform. Transparency as an Asset.

The API provides OAuth 1.0 authentication.

The project roadmap is available [here.](https://trello.com/b/O9IjhPXB/open-bank-project-api) 

## DOCUMENTATION 

Please refer to the [wiki](https://github.com/OpenBankProject/OBP-API/wiki) to see the API specification. 

## STATUS

[V1.2] (https://github.com/OpenBankProject/OBP-API/wiki/REST-API-V1.2) is mostly implemented

## LICENSE

This project is dual licensed under the AGPL V3 (see NOTICE) and a commercial license from TESOBE
Some files (OAuth related) are licensed under the Apache 2 license.

## SETUP

Vagrant and Puppet scripts are available [here](https://github.com/OpenBankProject/OBP-VM) to create a Virtual Box VM running the Open Bank Project API.

Note: These may be a bit outdated.

Otherwise use sbt or Maven 2 as a build tool.

----

SBT:

To compile and run jetty execute:

$ sbt
...
> compile
> ~;container:start; container:reload /

(Note that you first have to start sbt and then on its console start jetty with the container:start task, otherwise it will exit immediately. More here: https://github.com/siasia/xsbt-web-plugin/wiki)

In OS X, sbt can be installed with $ sudo port install sbt

----

Maven:

mvn jetty:run

----

# Databases:

The default datastores used are MongoDB (metadata, transaction cache) and Postgres (user accounts).

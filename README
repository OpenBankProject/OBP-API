# README

Welcome to the Open Bank Project API 

## ABOUT

The API aims to provide a read only access to bank transaction in a simple and consistent structure by abstracting away the peculiarities of each banking system. 

The API aims also to facilitate the data sharing with users, with several level of details in a secure way and to enhance the raw transactions with some metadata : comments, tags, pictures etc.

The API provides also OAuth 1.0 authentication.

## Document 

Please refer to the [wiki](https://github.com/OpenBankProject/OBP-API/wiki) to see the API specification. 


## STATUS

For the moment some ready only API calls are implemented, by in the future there will be write API calls to post metadata like : comments, pictures, etc.


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
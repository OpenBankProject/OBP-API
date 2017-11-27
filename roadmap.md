# Welcome to the OBP roadmap!

This document contains

* Upcoming developments
* Completed developments (most recent first)
* The future (as much as anyone can know!) 

Our roadmap is agile and likely to be modified / re-prioritised based on demand from banks and developers. It should be seen as an indication of direction rather than something set in stone. 

This document mainly concerns OBP API but may reference other OBP projects.

If you have a particular requirement or would like to comment or help us specify, please [get in touch](http://www.openbankproject.com/contact) or make a pull request to this document.



## Upcoming developments

*   Web Hooks (on balance change etc.)

## Completed developments (most recent first)



### Message Docs
Message Docs (which define Core Banking System Kafka messages) are now avilalbe independent of the connector being used on the API instance. See [here](https://apiexplorersandbox.openbankproject.com/?ignoredefcat=true&tags=#v2_2_0-getMessageDocs) 

### Endpoint config and cleanup
Endpoints can now be enabled / disabled explicitly using Props file.
We removed old versions including v1.0, v1.1 and v.1.2.

### API Explorer UI improved
Left panel of API Explorer is now grouped by API Tags.

### Automated performance tests
We added automated performance tests driven by Jenkins.


### Performance improvements
We added "new style" endpoints and acheived some significant performance gains by using Futures and purer (no read side effects) functions.


### Custom code folders in OBP-API
We added Custom code folders so that bank specific forks can more easily git merge in updates from OBP-API develop branches. 


### API Tester
API Tester is a Python/Djano App for testing an OBP API instance from the outside. Partiularly useful when using a non-sandbox (e.g. kafka) connector. It supports a variety of authentication methods so you can test outside a gateway. You can configure different data profiles for specifying parameters such as bank_id, account_id etc. See [here](https://github.com/OpenBankProject/API-Tester) for the source code and installation instructions.

### Extend Swagger support 
We improved the information contained in the Swagger (and Resource Doc) endpoints. They are also available from the API Explorer. See [here](https://apiexplorersandbox.openbankproject.com/?ignoredefcat=true&tags=#v1_4_0-getResourceDocsSwagger)


### Kafka versioning
The built in kafka connectors now provide message versioning 

### Akka Remote data (Three tier architechture)
Most OBP data access now happens over Akka. This allows the API layer to be physically separated from the storage layer with the API layer only able to call a specified set of data access functions with only the storage layer having JDBC / SQL access.    

### Security improvements

Including

* User lockout
* Consumer redirect lockdown

### User model refactoring
The two user models are now called AuthUser and ResourceUser


### Version 2.2.0

*   Create View for Account (with can_add_counterparty permission)
*   Update View for Account (with can_add_counterparty permission)
*   Get current FX Rate
*   Get Counterparties
*   Get Metrics


### Version 2.1.0

*   Sandbox Import. This is now a documented call.
*   Get Transaction Request Types Supported By Bank
*   Create Transaction Request (updated)
*   Answer Transaction Request Challenge (updated)
*   Get Transaction Requests (updated)
*   Get Roles (new)
*   Get Entitlements By Bank And User (new)
*   Get Consumer (App) (new)
*   Get Consumers (App) (new)
*   Enable Disable Consumers (Apps) (new)
*   Update Consumer Redirect Url
*   Create Cards For Bank (new)
*   Get Users (new)
*   Create Transaction Type (new)
*   Create Counterparty / Beneficiary (new)
*   Get Atm (new) (Previously we just had the plural)
*   Get Branch (new) (Previously we just had the plural)
*   Get Product (new) (Previously we just had the plural)
*   Get Customer (updated with customer rating and credit limit)

### API Manager 
has been added to manage consumers, users, roles etc.. See [API Manager](https://github.com/OpenBankProject/API-Manager)

### OBP JVM 
has been added as an alternative to the Scala Kafka connector. It handles OBP message queue interaction. See [OBP JVM](https://github.com/OpenBankProject/OBP-JVM)



### Version 2.0.0

*   Move View Permission fields into separate resource so account resource is less cluttered.
*   Rename other_count to counterparty in new API references
*   Customer acquisition and on boarding (user registration, customer, KYC documents, KYC media, status)
*   Get All Accounts At All Banks
*   Get Account By Id (updated)
*   Create Entitlement
*   Create KYC Check
*   Create KYC Document
*   Create KycMedia
*   Create KycStatus
*   Create Social Media Handle
*   Get All Accounts At One Bank
*   Create Account
*   Create Customer
*   Create Meeting
*   Create User
*   Create User Customer Links
*   Delete Entitlement
*   Elastic Search Metrics
*   Elastic Search Warehouse
*   Get All Entitlements
*   Get Core Account By Id
*   Get Core Transactions For Bank Account
*   Get CurrentUser
*   Get Customers
*   Get Entitlements
*   Get Kyc Checks
*   Get Kyc Documents
*   Get Kyc Media
*   Get Kyc Statuses
*   Get Meeting
*   Get Meetings
*   Get Permission For User For Bank Account
*   Get Permissions For Bank Account
*   Get Social Media Handles
*   Get Transaction Types
*   Get User
*   Get Private Accounts At All Banks
*   Get Private Accounts At One Bank
*   Get Public Accounts At All Banks
*   Get Public Accounts At One Bank


### Docker containers

*   Run OBP, API Explorer and Social Finance (a reference App) in Docker.

See [Docker obp-full](https://hub.docker.com/r/openbankproject/obp-full/)


### OBP API Explorer 
is used to explore and interact with the OBP API. See [API Explorer on Sandbox](https://apiexplorersandbox.openbankproject.com/)

### Endpoints for API documentation 
See [Resource Docs endpoint](https://api.openbankproject.com/obp/v1.4.0/resource-docs/obp)


### Kafka connector

*   Get transactions via Kafka bus and language neutral connector on the south side of the MQ

See [Docker obp-full-kafka](https://hub.docker.com/r/openbankproject/obp-full-kafka/)


### Version 1.4.0

This version is stable. For the spec see [here](https://github.com/OpenBankProject/OBP-API/wiki/REST-API-V1.4.0) or [here](https://apiexplorersandbox.openbankproject.com/?version=1.4.0&list-all-banks=false&core=&psd2=&obwg=&ignoredefcat=true)

New features included:

*   Branch Locations
*   Customer messages
*   ATM Locations
*   Customer information
*   Financial products offered by the bank (account types, category, family, link to more info)
*   [Payment Orders with Security Challenges](https://github.com/OpenBankProject/OBP-API/wiki/REST-API-V1.4.0#transactionRequests)

### Version 1.3.0

This version is stable. For the specification see [here](https://github.com/OpenBankProject/OBP-API/wiki/REST-API-V1.3.0) or [here](https://apiexplorersandbox.openbankproject.com/?version=1.3.0&list-all-banks=false&core=&psd2=&obwg=&ignoredefcat=true)

New features included:

*   [Transfers / Payments](https://github.com/OpenBankProject/OBP-API/wiki/REST-API-V1.3.0#transfers)
*   [Cards](https://github.com/OpenBankProject/OBP-API/wiki/REST-API-V1.3.0#all-physical-cards)




### Version 1.2.1

This version is stable. For the specification see [here](https://github.com/OpenBankProject/OBP-API/wiki/REST-API-V1.2.1) or [here](https://apiexplorersandbox.openbankproject.com/?version=1.2.1&list-all-banks=false&core=&psd2=&obwg=&ignoredefcat=true)




## Future

### OAuth Documentation Upgrade

*   Update OAuth docs so API root links are clearly place holders
*   Update SDK docs so API root links are clearly place holders

### Version X

*   Firehose / Elastic Search access to Accounts / Transactions


### Version Y

Intended features:

*   Account type becomes a Type instead of String
*   Add product_code to Account which links to Financial Product

### Version Z

Intended features:

*   Offers

### Version A

Intended features:

*   Extended Customer information (contact info, social info, summary of portfolio across the bank)

### Version B

Intended features:

*   Overdrafts (limit, interest rate, approval date, utilization)
*   Loans (size, reason, interest rate, approval date, utilization)
*   Mortgages (size, reason, property, details)
*   Applications and status of the above

### Version C

Intended features:

*   Fixed term deposits (size, term, interest rate, constraints)
*   Savings accounts (interest rate, constraints)

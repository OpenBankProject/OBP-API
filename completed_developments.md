

## Completed developments (most recent first)


### Version 3.1.0
50 new and updated endpoints. Major new functionality includes:

* Webhooks
* Rate limiting and Throttling.
* Extended Customer information
* Account Applications
* Product Attributes and Hierarchy
* Product Collections
* User Auth Context

See [here](https://apiexplorersandbox.openbankproject.com/?version=OBPv3.1.0&ignoredefcat=true&native=true) for details.


    API
        Get API Configuration
        Get Adapter Info (general)
        Get Connector Status (Loopback)
        Get Rate Limiting Info
    Account
        Check Available Funds
        get Checkbook orders
    Account Application
        Create Account Application
        Get Account Application by Id
        Get Account Applications
        Update Account Application Status
    Account Firehose
        Get Firehose Customers
    Card
        Get status of Credit Card order
    Consumer
        Get Call Limits for a Consumer
        Get Consumers (logged in User)
        Set Calls Limit for a Consumer
    Customer
        Add Address to Customer
        Add Tax Residence to Customer
        Create Credit Limit Order Request
        Create Customer.
        Delete Customer Address
        Delete Tax Residence
        Get Credit Limit Order Request By Request Id
        Get Credit Limit Order Requests
        Get Customer Addresses
        Get Customer by CUSTOMER_ID
        Get Customer by CUSTOMER_NUMBER
        Get Tax Residences of Customer
        Update the Address of an Customer
    Metric
        Get Top APIs
        Get Top Consumers
    Product
        Create Product
        Create Product Attribute
        Create Product Collections
        Delete Product Attribute
        Get Bank Product
        Get Bank Product
        Get Bank Products
        Get Product Attribute
        Update Product Attribute
    Role
        Get all Entitlements
    Transaction
        Get Transaction by Id.
    Transaction Request
        Get Transaction Requests.
    User
        Create User Auth Context
        Delete User Auth Context
        Delete User's Auth Contexts
        Get User Auth Contexts
        Get User Lock Status
        Refresh User.
        Unlock the user
    Webhook
        Create an Account Webhook
        Enable/Disable an Account Webhook
        Get Account Webhooks


See [here](https://apiexplorersandbox.openbankproject.com/?version=OBPv3.1.0&ignoredefcat=true) for all OBP v3.1 APIS


### Product Collections
Used to define Collections of Financial Products.

### Product Attributes
Used to further define a Product using list of key value pairs e.g.:
*   Overdrafts (limit, interest rate, approval date, utilization)
*   Loans (size, reason, interest rate, approval date, utilization)
*   Mortgages (size, reason, property, details)


### Glossary
An endpoint which returns information about terms and processes in OBP. This live Documentation is aware of Props values e.g. hosts and so on.
The output is used in API Explorer to provide documentation which is aware of the correct hosts etc.

### Glossary in API Explorer
Glossary page added. Aims to replace some documentation in the wiki because we can display correct hosts and ports etc.
Helped with "Update OAuth docs so API root links are clearly place holders" from the roadmap.

### OAuth2 / Open Id Connect
Support for Google + Yahoo OAuth2 + OpenId Connect. See the glossary.
Support for on premise OAuth2 provider e.g. MitreId. See the glossary.

### Message Docs (for Akka)
Message Docs (which define Core Banking System Akka messages) are now available independent of the connector being used on the API instance. See [here](https://apiexplorersandbox.openbankproject.com/?ignoredefcat=true&tags=#v2_2_0-getMessageDocs)


### Message Docs (for Kafka)
Message Docs (which define Core Banking System Kafka messages) are now available independent of the connector being used on the API instance. See [here](https://apiexplorersandbox.openbankproject.com/?ignoredefcat=true&tags=#v2_2_0-getMessageDocs)

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



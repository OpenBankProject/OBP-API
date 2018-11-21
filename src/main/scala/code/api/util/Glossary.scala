package code.api.util

import code.api.util.APIUtil.{getObpApiRoot, getServerUrl, getOAuth2ServerUrl}
import org.pegdown.PegDownProcessor

import scala.collection.mutable.ArrayBuffer


import code.api.util.ExampleValue._


object Glossary {

	val PegDownProcessorTimeout: Long = 1000*20
	val pegDownProcessor : PegDownProcessor = new PegDownProcessor(PegDownProcessorTimeout)

    case class GlossaryItem(
															 title: String,
															 description: String,
															 htmlDescription: String,
															 textDescription: String
                            )

		def makeGlossaryItem (title: String, connectorField: ConnectorField) : GlossaryItem = {
			GlossaryItem(
				title = title,
				description = s"""
																						|${connectorField.description}
																						|
																						|Example value: ${connectorField.value}
				""")
		}

	object GlossaryItem {


		// Constructs a GlossaryItem from just two parameters.
		def apply(title: String, description: String): GlossaryItem = {

		// Convert markdown to HTML
		val htmlDescription: String = pegDownProcessor.markdownToHtml(description.stripMargin)

		// Try and generate a plain text string (requires valid HTML)
		val textDescription: String = try {
			scala.xml.XML.loadString(htmlDescription).text
		} catch {
			// Fallback to the html
			case _ : Throwable => htmlDescription
		}

			new GlossaryItem(title,
				description,
				htmlDescription,
				textDescription
			)
		}

	}




    val glossaryItems = ArrayBuffer[GlossaryItem]()

	// NOTE! Some glossary items are defined in ExampleValue.scala


	//implicit val formats = DefaultFormats
	//val prettyJson: String = extraction(decompose(authInfoExample))


	/*




	 */


	val latestKafkaConnector : String = "kafka_vSept2018"

	def messageDocLink(process: String) : String = {
		s"""<a href="/message-docs?connector=$latestKafkaConnector#$process">$process</a>"""
	}



	glossaryItems += GlossaryItem(
		title = "Adapter.Kafka.Intro",
		description =
				s"""
					|## Brief introduction to using Kafka as the interface layer between OBP and your Core Banking System (CBS).
					|### Prerequesites
					|
					|
					 |* We assume you have OBP-API running and it is connected to a working Kafka installation.
						| You can check OBP -> Kafka connectivity using the <a href="/#vv3_1_0-getObpApiLoopback">"loopback" endpoint</a>.
|
					|* We assume you have API Explorer running (the application serving this page) but its not necessary - (you could use a REST client)
					|* You might want to also run API Manager as it makes it easier to grant yourself roles, but its not nessessary (you could use a REST client  / API Explorer instead).
					|* You should register a User that you want to use to call the API. Let's call this user Jane.
|* You will need another user that will have the roles required for the following steps. Let's call this user CarolaAdmin.
					 |* Use <a href="/index#vv3_1_0-createUserAuthContext">Create Auth Context</a> to add a “token” to the User who while request accounts.
						|This token which could be a CUSTOMER_NUMBER is sent inside the AuthInfo object to Kafka
					 |* OR Use Create Customer and Create User Customer Link (note that Create Auth Context is preferred)
					 |
					 |Then its time to configure or program your Adapter to consume, and respond to, the messages OBP will send to Kafka.
|
| We suggest they are implemented in the following order:
|
|
 |1) Core (Prerequisites) - Get Adapter, Get Banks, Get Bank)
 |
					 |* ${messageDocLink("obp.get.AdapterInfo")}
					 |* ${messageDocLink("obp.get.Banks")}
					 |* ${messageDocLink("obp.get.Bank")}
					 |
 |2) Get Accounts
 |
					 |* ${messageDocLink("obp.get.CustomersByUserIdBox")}
					 |* ${messageDocLink("obp.get.coreBankAccounts")}
					 |* ${messageDocLink("obp.check.BankAccountExists")}
					 |* ${messageDocLink("obp.get.Accounts")}
					 |* ${messageDocLink("obp.get.Account")}
					 |
 |3) Get Transactions
 |
					 |* ${messageDocLink("obp.get.Transactions")}
					 |* ${messageDocLink("obp.get.Transaction")}
					 |
 |4) Manage Counterparties
 |
					 |* ${messageDocLink("obp.get.counterparties")}
					 |* ${messageDocLink("obp.get.CounterpartyByCounterpartyId")}
					 |* ${messageDocLink("obp.create.Counterparty")}
					 |
 |5) Get Transaction Request Types
 |
					 |* This is configured using OBP Props - No messages required
					 |
 |6) Get Challenge Threshold (CBS)
 |
					 |* ${messageDocLink("obp.get.getChallengeThreshold")}
					 |
 |7)  Make Payment (used by Create Transaction Request)
 |
					 |* ${messageDocLink("obp.get.makePaymentv210")}
 						|* This also requires 8,9,10 for high value payments.
					 |
 |8) Get Transaction Requests.
 |
					 |* ${messageDocLink("obp.get.transactionRequests210")}
					 |
 |9) Generate Security Challenges (CBS)
 |
					 |* ${messageDocLink("obp.create.Challenge")}
					 |
 |10) Answer Security Challenges (Validate)
 |
					 |* Optional / Internal OBP (No additional messages required)
					 |
 |11) Manage Counterparty Metadata
 |
					 |* Internal OBP (No additional messages required)
					 |
 |12) Get Entitlements
 |
					 |* Internal OBP (No additional messages required)
					 |
 |13) Manage Roles
 |
					 |* Internal OBP (No additional messages required)
					 |
 |14) Manage Entitlements
 |
					 |* Internal OBP (No additional messages required)
					 |
 |15) Manage Views
 |
					 |* Internal OBP (No additional messages required)
					 |
 |16) Manage Transaction Metadata
 |
					 |* Internal OBP (No additional messages required)
					 |
 |"""
	)






	glossaryItems += GlossaryItem(
		title = "Adapter.authInfo",
		description =
				s"""authInfo is a JSON object sent by the Connector to the Adapter so the Adapter and/or Core Banking System can
  | identify the User making the call.
  |
  | The authInfo object contains several optional objects and fields.
  |
  |Please see the Message Docs for your connector for the current JSON structure. The following serves as a guide:
  |
  |* userId is the user_id as generated by OBP
  |* username can be chosen explicitly to match an existing customer number (not recommended)
  |* linkedCustomers is a list of Customers the User is explicitly linked to. Use the <a href="/#vv2_0_0-createUserCustomerLinks">Create User Customer Link endpoint</a> to populate this data.
  |* userAuthContexts may contain the customer number or other tokens in order to boot strap the User Customer Links
  |or provide an alternative method of tagging the User with an authorisation context.
  |Use the <a href="/#vv3_1_0-createUserAuthContext">Create UserAuthContext endpoint</a> to populate this data.
  |* cbsToken is a token used by the CBS to identify the user's session. Either generated by the CBS or Gateway.
  |* isFirst is a flag that indicates that OBP should refresh the user's list of accounts from the CBS (and flush / invalidate any User's cache)
  |* correlationId just identifies the API call.
  |* authViews are entitlements given by account holders to third party users e.g. Sam may grant her accountant Jill read only access to her business account. See the <a href="/index#vv3_0_0-createViewForBankAccount">Create View endpoint</a>
  |
  |<img width="468" alt="authinfo_annotated_1" src="https://user-images.githubusercontent.com/485218/48432550-f6f0d100-e774-11e8-84dc-e94520ba186e.png"></img>
  |
  |
  |
 |"""
	)


	glossaryItems += GlossaryItem(
		title = "API.Interfaces",
		description =
				s"""
					 |<img width="468" alt="authinfo_annotated_1" src="https://user-images.githubusercontent.com/485218/48845997-413e0780-ed9e-11e8-86c5-e5ce510c140c.png"></img>
					 |
  |
  |
 |"""
	)




/*

{
  "authInfo":{
    "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1", <- The user_id as generated by OBP
    "username":"felixsmith", <- The username the user logs into the API / App with
    "cbsToken":"FYIUYF6SUYFSD", <- A token used by the CBS to identify the user's session. Either generated by the CBS or Gateway.
    "isFirst":true, <- A flag that indicates that OBP should not use its cache for list of Accounts etc.
    "correlationId":"2ba9a7e4-6d02-40e3-a129-0b2bf89de92b", <- Identifies the API call.
    "linkedCustomers":[{ <- The customers that this User is allowed to represent.
      "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
      "customerNumber":"5987953",
      "legalName":"Eveline Tripman"
    }],
    "userAuthContexts":[{ <- A list of key value pairs that adds authentication context to the user.
      "key":"CUSTOMER_NUMBER",
      "value":"5987953"
    },{
      "key":"TOKEN",
      "value":"qieuriopwoir987ASYDUFISUYDF678u"
    }],
    "authViews":[{ <- For third party access to accounts. e.g. an accountant accessing her clients account.
      "view":{
        "id":"owner", <- Specifies the type of access
        "short_name":"Owner",
        "description":"This is the owner view"
      },
      "account":{ <- Minimal information about the Account
        "id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
        "accountRoutings":[{
          "scheme":"AccountNumber",
          "address":"546387432"
        },{
          "scheme":"IBAN",
          "address":"DE91 1000 0000 0123 4567 89"
        }],
        "customerOwners":[{ <- The Customer Owners of the Account
          "bankId":"GENODEM1GLS",
          "customerId":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
          "customerNumber":"5987953",
          "legalName":"Eveline Tripman",
          "dateOfBirth":"2017-09-18T22:00:00Z"
        }],
        "userOwners":[{ <- The User Owners of the Account
          "userId":"9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
          "emailAddress":"eveline@example.com",
          "name":"Eveline Tripman"
        }]
      }
    }]
  }
}



 */



	  glossaryItems += GlossaryItem(
		title =
				"Account",
		description =
				"""The thing that tokens of value (money) come in and out of.
			|An account has one or more `owners` which are `Users`.
			|In the future, `Customers` may also be `owners`.
			|An account has a balance in a specified currency and zero or more `transactions` which are records of successful movements of money.
			|"""
	  )

	  glossaryItems += GlossaryItem(
		title = "Account.account_id",
		description =
		s"""
		  |An identifier for the account that MUST NOT leak the account number or other identifier nomrally used by the customer or bank staff.
		  |It SHOULD be a UUID. It MUST be unique in combination with the BANK_ID. ACCOUNT_ID is used in many URLS so it should be considered public.
		  |(We do NOT use account number in URLs since URLs are cached and logged all over the internet.)
		  |In local / sandbox mode, ACCOUNT_ID is generated as a UUID and stored in the database.
		  |In non sandbox modes (Kafka etc.), ACCOUNT_ID is mapped to core banking account numbers / identifiers at the South Side Adapter level.
		  |ACCOUNT_ID is used to link Metadata and Views so it must be persistant and known to the North Side (OBP-API).
			|
			| Example value: ${accountIdExample.value}
			|
		""")

	  glossaryItems += GlossaryItem(
		title = "Bank",
		description =
		"""
		  |The entity that represents the financial institution or bank within a financial group.
		  |Open Bank Project is a multi-bank API. Each bank resource contains basic identifying information such as name, logo and website.
		""")


	  glossaryItems += GlossaryItem(
		title = "Bank.bank_id",
		description =
		s"""
		  |An identifier that uniquely identifies the bank or financial institution on the OBP-API instance.
		  |
		  |It is typically a human (developer) friendly string for ease of identification.
			|
			|It SHOULD NOT contain spaces.
			|
		  |In sandbox mode it typically has the form: "financialinstitutuion.sequencennumber.region.language". e.g. "bnpp-irb.01.it.it"
			|
			|For production, it's value could be the BIC of the institution.
			|
			|
			|Example value: ${bankIdExample.value}
		 """)

	  glossaryItems += GlossaryItem(
		title = "Consumer",
		description =
		s"""
		  |The "consumer" of the API, i.e. the web, mobile or serverside "App" that calls on the OBP API on behalf of the end user (or system).
		  |
		  |Each Consumer has a consumer key and secrect which allows it to enter into secure communication with the API server.
		""")

	  glossaryItems += GlossaryItem(
		title = "Customer",
		description =
		  """
			|The legal entity that has the relationship to the bank. Customers are linked to Users via `User Customer Links`. Customer attributes include Date of Birth, Customer Number etc.
			|
		  """)

	  glossaryItems += GlossaryItem(
		title = "Customer.customer_id",
		description =
		  s"""
			|The identifier that MUST NOT leak the customer number or other identifier nomrally used by the customer or bank staff. It SHOULD be a UUID and MUST be unique in combination with BANK_ID.
			|
			|Example value: ${customerIdExample.value}
		  """)

	  glossaryItems += GlossaryItem(
		title = "Transaction",
		description =
		  """
			|Records of successful movements of money from / to an `Account`. OBP Transactions don't contain any "draft" or "pending" Transactions. (see Transaction Requests). Transactions contain infomration including type, description, from, to, currency, amount and new balance information.
			|
		  """)

	  glossaryItems += GlossaryItem(
		title = "Transaction Requests",
		description =
		  """
			|Transaction Requests are records of transaction / payment requests coming to the API. They may or may not result in Transactions (following authorisation, security challenges and sufficient funds etc.)
			|
			|A successful Transaction Request results in a Transaction.
			|
			|For more information [see here](https://github.com/OpenBankProject/OBP-API/wiki/Transaction-Requests)
		  """)

	  glossaryItems += GlossaryItem(
		title = "User",
		description =
		  """
			|The entity that accesses the API with a login / authorisation token and has access to zero or more resources on the OBP API. The User is linked to the core banking user / customer at the South Side Adapter layer.
		  """)

	  glossaryItems += GlossaryItem(
		title = "User.user_id",
		description =
		  s"""
			|An identifier that MUST NOT leak the user name or other identifier nomrally used by the customer or bank staff. It SHOULD be a UUID and MUST be unique on the OBP instance.
			|
			| Example value: ${userIdExample.value}
		  """)

	  glossaryItems += GlossaryItem(
		title = "User.provider",
		description =
		  """
			|The name of the authentication service. e.g. the OBP hostname or kafka if users are authenticated over Kafka.
		  """)

	  glossaryItems += GlossaryItem(
		title = "User.provider_id",
		description =
		  """
			|The id of the user given by the authenticaiton provider.
		  """)

	  glossaryItems += GlossaryItem(
		title = "User Customer Links",
		description =
		  """
			|Link Users and Customers in a many to many relationship. A User can represent many Customers (e.g. the bank may have several Customer records for the same individual or a dependant). In this way Customers can easily be attached / detached from Users.
		  """)


	glossaryItems += GlossaryItem(
		title = "Consent / Account Onboarding",
		description =
				"""|*Consent*, or *Account onboarding*, is the process by which the account owner gives permission for their account(s) to be accessible to the API endpoints.
|
|In OBP, the account, transaction and payment APIs are all guarded by Account *Views* - with one exception, the account holders endpoint which can be used to
|bootstrap account on-boarding.
|
|Note: the account holders endpoint is generally made available only to the Account Onboarding App, so if a View does not exist, no API access to the account is possible.
|
|*Consent* or *Account onboarding* can be managed in one of two ways:
|
|1) A backend system (CBS or other) is the system of record for User Consent, and OBP mirrors this.
|
|In this case:
|
| a) OBP requires the CBS or other backend system to return a list of accounts and permissions associated with a User.
|
| b) At User login, OBP automatically creates one or more Views for that User based on the permissions supplied by the CBS.
|
|2) OBP is the system of record for User Consent.
|
|In this case:
|
|  a) OBP requires the CBS, Gateway or other system to provide just a basic list of accounts owned by the User.
|
|  b) The Onboarding App or Bank's Onboarding Page then authenticates the User and calls the Create View endpoint.
|
|  c) The account, transaction and payment API endpoints then work as moderated by the relevant View permissions.
|
|  d) The User can revoke access by calling the delete View endpoint.
|
|
|In summary:
|
|Prior to Views being created on an Account for a User, only the 'accounts held' endpoint will work for the account holder, and this endpoint only provides enough information
|to identify the account so it can be selected and on-boarded into the API.
|
|Once a View exists for an Account, a User can interact with the Account via the API based on permissions defined in the View.
|
|""")



	  glossaryItems += GlossaryItem(
		title = "Direct Login",
		description =
		  s"""
			|Direct Login is a simple authentication process to be used at hackathons and trusted environments:
			|
			|
			|### 1) Get your App key
			|
			|[Sign up]($getServerUrl/user_mgt/sign_up) or [login]($getServerUrl/user_mgt/login) as a developer.
			|
			|Register your App key [HERE]($getServerUrl/consumer-registration)
			|
			|Copy and paste the consumer key for step two below.
			|
			|### 2) Authenticate
			|
			|
			|Using your favorite http client:
			|
			|	POST $getServerUrl/my/logins/direct
			|
			|Body
			|
			|	Leave Empty!
			|
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|
			|    Authorization: DirectLogin username="janeburel",
			|                    password="the-password-of-jane",
			|                    consumer_key="your-consumer-key-from-step-one"
			|
			|Here is it all together:
			|
			|	POST $getServerUrl/my/logins/direct HTTP/1.1
			|	Authorization: DirectLogin username="janeburel",   password="686876",  consumer_key="GET-YOUR-OWN-API-KEY-FROM-THE-OBP"
			|	Content-Type: application/json
			|	Host: 127.0.0.1:8080
			|	Connection: close
			|	User-Agent: Paw/2.3.3 (Macintosh; OS X/10.11.3) GCDHTTPRequest
			|	Content-Length: 0
			|
			|
			|
			|
			|You should receive a token:
			|
			|	{"token":"a-long-token-string"}
			|
			|### 3) Make authenticated API calls
			|
			|In subsequent calls you can use the token received in step 2
			|
			|e.g.
			|
			|
			|Action:
			|
			|	PUT $getObpApiRoot/v2.0.0/banks/obp-bankx-n/accounts/my-new-account-id
			|
			|Body:
			|
			|	{  "type":"CURRENT",  "balance":{    "currency":"USD",    "amount":"0"  }}
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-step-2"
			|
			|Here is another example:
			|
			|	PUT $getObpApiRoot/v2.0.0/banks/enbd-egy--p3/accounts/newaccount1 HTTP/1.1
			|	Authorization: DirectLogin token="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyIiOiIifQ.C8hJZNPDI59OOu78pYs4BWp0YY_21C6r4A9VbgfZLMA"
			|	Content-Type: application/json
			|	Cookie: JSESSIONID=7h1ssu6d7j151u08p37a6tsx1
			|	Host: 127.0.0.1:8080
			|	Connection: close
			|	User-Agent: Paw/2.3.3 (Macintosh; OS X/10.11.3) GCDHTTPRequest
			|	Content-Length: 60
			|
			|	{"type":"CURRENT","balance":{"currency":"USD","amount":"0"}}
			|
			|
			|### More information
			|
			|   Parameter names and values are case sensitive.
			|   The following parameters must be sent by the client to the server:
			|
			|       username
			|         The name of the user to authenticate.
			|
			|       password
			|         The password used to authenticate user. Alphanumeric string.
			|
			|       consumer_key
			|         The application identifier. Generated on OBP side via
			|         $getServerUrl/consumer-registration endpoint.
			|
			|
			|  Each parameter MUST NOT appear more than once per request.
			|
		  """)

	  glossaryItems += GlossaryItem(
		title = "Scenario 1: Onboarding a User",
		description =
		  s"""
			|### 1) Create a user
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.0.0/users
			|
			|Body:
			|
			|	{  "email":"ellie@example.com",  "username":"ellie",  "password":"P@55w0RD123",  "first_name":"Ellie",  "last_name":"Williams"}
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-direct-login"
			|
			|Please note the user_id
			|
			|### 2) Create customer
			|
			|Requires CanCreateCustomer and CanCreateUserCustomerLink roles
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/customers
			|
			|Body:
			|
			|	{  "user_id":"user-id-from-step-1", "customer_number":"687687678", "legal_name":"NONE",  "mobile_phone_number":"+44 07972 444 876", "email":"person@example.com", "face_image":{    "url":"www.openbankproject",    "date":"2013-01-22T00:08:00Z"  },  "date_of_birth":"2013-01-22T00:08:00Z",  "relationship_status":"Single",  "dependants":5,  "dob_of_dependants":["2013-01-22T00:08:00Z"],  "credit_rating":{    "rating":"OBP",    "source":"OBP"  },  "credit_limit":{    "currency":"EUR",    "amount":"10"  },  "highest_education_attained":"Bachelor’s Degree",  "employment_status":"Employed",  "kyc_status":true,  "last_ok_date":"2013-01-22T00:08:00Z"}
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-direct-login"
			|
			|### 3) List customers for the user
			|
			|Action:
			|
			|	GET $getObpApiRoot/v3.0.0/users/current/customers
			|
			|Body:
			|
			|	Leave empty!
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-direct-login"
			|
			|### 4) Create user customer link
			|
			|Requires CanCreateCustomer and CanCreateUserCustomerLink roles
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/user_customer_links
			|
			|Body:
			|
			|	{ "user_customer_link_id":"String", "customer_id":"customer-id-from-step-2", "user_id":"user-id-from-step-1", "date_inserted":"2018-03-22T00:08:00Z", "is_active":true }
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-direct-login"
			|
			|### 5) Create account
			|
			|Requires CanCreateAccount role
			|
			|Action:
			|
			|	PUT $getObpApiRoot/v3.0.0/banks/BANK_ID/accounts/ACCOUNT_ID
			|
			|Body:
			|
			|	{  "user_id":"user-id-from-step-1",  "label":"Label",  "type":"CURRENT",  "balance":{    "currency":"EUR",    "amount":"0"  },  "branch_id":"1234",  "account_routing":{    "scheme":"OBP",    "address":"UK123456"  }}
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-direct-login"
			|
			|### 6) List accounts
			|
			|Action:
			|
			|	GET $getObpApiRoot/v3.0.0/my/banks/BANK_ID/accounts/account-id-from-step-2/account
			|
			|Body:
			|
			|	Leave empty!
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-direct-login"
			|
			|### 7) Create card
			|
			|Requires CanCreateCardsForBank role
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/cards
			|
			|Body:
			|
			|	{  "account_id":"account-id-from-step-4","bank_card_number":"String",  "name_on_card":"String",  "issue_number":"String",  "serial_number":"String",  "valid_from_date":"2013-01-22T00:08:00Z",  "expires_date":"2013-01-22T00:08:00Z",  "enabled":true,  "cancelled":true,  "on_hot_list":false,  "technology":"String",  "networks":["String"],  "allows":["credit"],  "account_id":"String",  "replacement":{    "requested_date":"2013-01-22T00:08:00Z",    "reason_requested":"Good Point"  },  "pin_reset":[{    "requested_date":"2013-01-22T00:08:00Z",    "reason_requested":"forgot"  }],  "collected":"2013-01-22T00:08:00Z",  "posted":"2013-01-22T00:08:00Z"}
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-direct0login"
			|
			|### 8) List cards
			|
			|Action:
			|
			|	GET $getObpApiRoot/v3.0.0/cards
			|
			|Body:
			|
			|	Leave empty!
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-direct0login"
			|
		  """)

	  glossaryItems += GlossaryItem(
		title = "Scenario 2: Create a Public Account",
		description =
		  s"""
			 |### 1) Create account
			 |
			 |Create an account as described in Step 5 of section [Onboarding a user](#Onboarding-a-user)
			 |
			 |### 2) Create a view
			 |
			 |Action:
			 |
			 |	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/accounts/your-account-id-from-step-1/views
			 |
			 |Body:
			 |
			 |	{  "name":"_test", "description":"good", "is_public":true, "which_alias_to_use":"good", "hide_metadata_if_alias_used":false,  "allowed_actions": ["can_see_transaction_this_bank_account", "can_see_transaction_other_bank_account", "can_see_transaction_metadata", "can_see_transaction_label", "can_see_transaction_amount", "can_see_transaction_type", "can_see_transaction_currency", "can_see_transaction_start_date", "can_see_transaction_finish_date", "can_see_transaction_balance", "can_see_comments", "can_see_narrative", "can_see_tags", "can_see_images", "can_see_bank_account_owners", "can_see_bank_account_type", "can_see_bank_account_balance", "can_see_bank_account_currency", "can_see_bank_account_label", "can_see_bank_account_national_identifier", "can_see_bank_account_swift_bic", "can_see_bank_account_iban", "can_see_bank_account_number", "can_see_bank_account_bank_name", "can_see_other_account_national_identifier", "can_see_other_account_swift_bic", "can_see_other_account_iban", "can_see_other_account_bank_name", "can_see_other_account_number", "can_see_other_account_metadata", "can_see_other_account_kind", "can_see_more_info", "can_see_url", "can_see_image_url", "can_see_open_corporates_url", "can_see_corporate_location", "can_see_physical_location", "can_see_public_alias", "can_see_private_alias", "can_add_more_info", "can_add_url", "can_add_image_url", "can_add_open_corporates_url", "can_add_corporate_location", "can_add_physical_location", "can_add_public_alias", "can_add_private_alias", "can_delete_corporate_location", "can_delete_physical_location", "can_edit_narrative", "can_add_comment", "can_delete_comment", "can_add_tag", "can_delete_tag", "can_add_image", "can_delete_image", "can_add_where_tag", "can_see_where_tag", "can_delete_where_tag", "can_create_counterparty", "can_see_bank_routing_scheme", "can_see_bank_routing_address", "can_see_bank_account_routing_scheme", "can_see_bank_account_routing_address", "can_see_other_bank_routing_scheme", "can_see_other_bank_routing_address", "can_see_other_account_routing_scheme", "can_see_other_account_routing_address"]}
			 |
			 | Headers:
			 |
			 |	Content-Type:  application/json
			 |
			 |	Authorization: DirectLogin token="your-token"
			 |
			 |### 3) Grant user access to view
			 |
			 |Action:
			 |
			 |	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/accounts/your-account-id-from-step-1/permissions/PROVIDER/PROVIDER_ID/views/view-id-from-step-2
			 |
			 |Body:
			 |
			 |	{  "json_string":"{}"}
			 |
			 | Headers:
			 |
			 |	Content-Type:  application/json
			 |
			 |	Authorization: DirectLogin token="your-token"
			 |
		  """)

	  glossaryItems += GlossaryItem(
		title = "Scenario 3: Create counterparty and make payment",
		description =
		  s"""
			|### 1) Create counterparty
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/accounts/account-id-from-account-creation/VIEW_ID/counterparties
			|
			|Body:
			|
			|	{  "name":"CounterpartyName",  "description":"My landlord",  "other_account_routing_scheme":"IBAN",  "other_account_routing_address":"7987987-2348987-234234",  "other_account_secondary_routing_scheme":"accountNumber",  "other_account_secondary_routing_address":"BIC201483",  "other_bank_routing_scheme":"bankCode",  "other_bank_routing_address":"10",  "other_branch_routing_scheme":"branchNumber",  "other_branch_routing_address":"10010",  "is_beneficiary":true,  "bespoke":[{    "key":"englishName",    "value":"english Name"  }]}
			|
			| Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token"
			|
			|### 2) Make payment by SEPA
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SEPA/transaction-requests
			|
			|Body:
			|
			|	{  "value":{    "currency":"EUR",    "amount":"10"  },  "to":{    "iban":"123"  },  "description":"This is a SEPA Transaction Request",  "charge_policy":"SHARED"}
			|
			| Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token"        |
			|
			|
			|### 3) Make payment by COUNTERPARTY
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests
			|
			|Body:
			|
			|	{  "to":{    "counterparty_id":"counterparty-id-from-step-1"  },  "value":{    "currency":"EUR",    "amount":"10"  },  "description":"A description for the transaction to the counterparty",  "charge_policy":"SHARED"}
			|
			| Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token"
			|
			|
		  """)

	  glossaryItems += GlossaryItem(
		title = "Scenario 4: Grant account access to another User",
		description =
		  s"""
			|### 1) Create account
			|
			|Create an account as described in Step 5 of section [Onboarding a user](#Onboarding-a-user)
			|
			|### 2) Create a view (private)
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/accounts/your-account-id-from-step-1/views
			|
			|Body:
			|
			|	{  "name":"_test", "description":"good", "is_public":false, "which_alias_to_use":"accountant", "hide_metadata_if_alias_used":false,  "allowed_actions": ["can_see_transaction_this_bank_account", "can_see_transaction_other_bank_account", "can_see_transaction_metadata", "can_see_transaction_label", "can_see_transaction_amount", "can_see_transaction_type", "can_see_transaction_currency", "can_see_transaction_start_date", "can_see_transaction_finish_date", "can_see_transaction_balance", "can_see_comments", "can_see_narrative", "can_see_tags", "can_see_images", "can_see_bank_account_owners", "can_see_bank_account_type", "can_see_bank_account_balance", "can_see_bank_account_currency", "can_see_bank_account_label", "can_see_bank_account_national_identifier", "can_see_bank_account_swift_bic", "can_see_bank_account_iban", "can_see_bank_account_number", "can_see_bank_account_bank_name", "can_see_other_account_national_identifier", "can_see_other_account_swift_bic", "can_see_other_account_iban", "can_see_other_account_bank_name", "can_see_other_account_number", "can_see_other_account_metadata", "can_see_other_account_kind", "can_see_more_info", "can_see_url", "can_see_image_url", "can_see_open_corporates_url", "can_see_corporate_location", "can_see_physical_location", "can_see_public_alias", "can_see_private_alias", "can_add_more_info", "can_add_url", "can_add_image_url", "can_add_open_corporates_url", "can_add_corporate_location", "can_add_physical_location", "can_add_public_alias", "can_add_private_alias", "can_delete_corporate_location", "can_delete_physical_location", "can_edit_narrative", "can_add_comment", "can_delete_comment", "can_add_tag", "can_delete_tag", "can_add_image", "can_delete_image", "can_add_where_tag", "can_see_where_tag", "can_delete_where_tag", "can_create_counterparty", "can_see_bank_routing_scheme", "can_see_bank_routing_address", "can_see_bank_account_routing_scheme", "can_see_bank_account_routing_address", "can_see_other_bank_routing_scheme", "can_see_other_bank_routing_address", "can_see_other_account_routing_scheme", "can_see_other_account_routing_address"]}
			|
			| Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token"
			|
			|### 3) Grant user access to view
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.0.0/banks/BANK_ID/accounts/your-account-id-from-step-1/permissions/PROVIDER/PROVIDER_ID/views/view-id-from-step-2
			|
			|Body:
			|
			|	{  "json_string":"{}"}
			|
			| Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token"
			|
			|
		  """)

	glossaryItems += GlossaryItem(
		title = "OAuth 1.0a",
		description =
			s"""
			|The following steps will explain how to connect an instance of the Open Bank Project OAuth Server 1.0a. This authentication mechanism is necessary so a third party application can consume the Open Bank project API securely.
			|
			|The following documentation is based on the OAuth 1.0a specification so if you need more details you can refer to it.
			|
			|Before starting to interact with the API, third party applications needs to get OAuth keys (consumer key and secret key). You can register your application here to get those keys for interacting with real data. If you want to use the sandbox before handling real data, please register your application here
			|
			|### 1) Obtain a request token
			|
			|To start a sign in flow, the application must obtain a request token by sending a signed message to :
			|
			|    POST $getServerUrl/oauth/initiate
			|
			|* oauth_callback: an absolute URI back to which the server will redirect the resource owner (user) when Authorization step is completed. If the application is unable to receive callbacks the parameter value MUST be set to “oob” (case sensitive), to indicate an out-of-band configuration.
			|
			|* oauth_consumer_key : The identifier portion of the client credentials (consumer key) which is obtained after application registration.
			|
			|* oauth_nonce : A nonce is a random string, uniquely generated by the client to allow the server to verify that a request has never been made before. The nonce value MUST be unique across all requests with the same timestamp, application credentials, and token combinations.
			|
			|* oauth_signature : the result of signing the request. Explained in detail here.
			|
			|* oauth_signature_method : The name of the signature method that will be used by the application to sign the request, as defined in OAuth protocol. The Open Bank Project OAuth server support "SHA1" and "SHA256" so the parameter MUST be set to “HMAC-SHA1" or “HMAC-SHA256”
			|
			|* oauth_timestamp : The timestamp value MUST be a positive integer and is expressed in the number of seconds since January 1, 1970 00:00:00 GMT.
			|
			|* oauth_version : OPTIONAL. If present, MUST be set to "1.0". Provides the version of the authentication process as defined in the OAuth 1.0 protocol specification.
			|
			|Example:
			|
			|    POST /oauth/initiate HTTP/1.1
			|
			|    Host: $getServerUrl
			|
			|    Authorization: OAuth
			|
			|    oauth_callback="http%3A%2F%2Fprinter.example.com%2Fready",
			|
			|    oauth_consumer_key="cChZNFj6T5R0TigYB9yd1w",
			|
			|    oauth_nonce="ea9ec8429b68d6b77cd5600adbbb0456",
			|
			|    oauth_signature="F1Li3tvehgcraF8DMJ7OyxO4w9Y%3D",
			|
			|    oauth_signature_method="HMAC-SHA256",
			|
			|    oauth_timestamp="1318467427",
			|
			|    oauth_version="1.0"
			|
			|important: We will explain below in the "signature" section how to calculate the value of the "oauth_signature" field.
			|
			|Note : line breaks are for display purposes only, the application MUST send the parameters on one line and the only separator between the parameters is a coma “,”.
			|
			|The server validates the request and replies with a set of temporary credentials in the body of the HTTP response.
			|
			|Example (line breaks are for display purposes only) :
			|
			|    HTTP/1.1 200 OK
			|    Content-Type: application/x-www-form-urlencoded
			|    oauth_token=hh5s93j4hdidpola&oauth_token_secret=hdhd0244k9j7ao03&oauth_callback_confirmed=true
			|
			|The application should examine the HTTP status of the response. Any value other than 200 indicates a failure. The body of the response will contain the oauth_token, oauth_token_secret, and oauth_callback_confirmed parameters. The application should verify that oauth_callback_confirmed is true and store the other two values for the next steps.
			|
			|### 2) Redirect the user
			|
			|The next step is to direct the user to Open Bank Project so that he may complete the authentication.
			|
			|Direct the user to :
			|
			|    GET oauth/authorize
			|
			|and the request token obtained in step 1 should be passed as the oauth_token parameter.
			|
			|The most seamless way for a website to implement this would be to issue a HTTP 302 redirect as the response to the original request. Mobile and desktop applications should open a new browser window or direct to the URL via an embedded web view.
			|
			|Example :
			|
			|    $getServerUrl/oauth/authorize?oauth_token=NPcudxy0yU5T3tBzho7iCotZ3cnetKwcTIRlX0iwRl0
			|
			|Upon a successful authentication, the callback URL would receive a request containing the oauth_token and oauth_verifier parameters. The application should verify that the token matches the request token received in step 1.
			|
 			|If the callback URL was not specified (oob) than the verifier will be shown in the page and the user has to enter it into the application manually.
			|
			|### 3) Convert the request token to an access token
			|
			|To convert the request token into a usable access token, the application must make a:
			|
			|    POST $getServerUrl/oauth/token
			|
			|request containing the oauth_verifier value obtained in step 2. The request token is also passed as oauth_token parameter of the header.
			|
			|Note : The oauth_callback_url parameter is not necessary any more.
			|
			|Example :
			|
			|    POST /oauth/token HTTP/1.1
			|
			|    Host: $getServerUrl
			|
			|    Authorization: OAuth
			|
			|    oauth_verifier="9312832",
			|
			|    oauth_token=”aze2342352aze”,
			|
			|    oauth_consumer_key="cChZNFj6T5R0TigYB9yd1w",
			|
			|    oauth_nonce="ea9ec8429b68d6b77cd5600adbbb0456",
			|
			|    oauth_signature="F1Li3tvehgcraF8DMJ7OyxO4w9Y%3D",
			|
			|    oauth_signature_method="HMAC-SHA256",
			|
			|    oauth_timestamp="1318467427",
			|
			|    oauth_version="1.0"
			|
			|Like the step 1, a successful response contains the oauth_token & oauth_token_secret and they should be stored and used for future authenticated requests to the OBP API.
			|
			|The application can now use the access token to access protected resources.
			|
			|### 4) Access protected resources
			|
			|Once the application has an a access token and secret token, it can access protected resources. The request is the same as in step 3 except the oauth_verifer which MUST not be included in the header.
			|
			|Please see the API documentation for more details on how to access protected resources.
			|
			|### Recommended OAuth 1.0 libraries:
			|
			|If you want to use a OAuth library to handle the OAuth process for your application, we have successfully tested these ones:
			|
			|* JAVA:
			|
			| [signpost](http://code.google.com/p/oauth-signpost/). Warning any version below 1.2.1.2 probably will not work. Version 1.2 which is the current Maven version seems to cause problems.
			|
			|* PHP:
			|
			| [OAuth Consumer And Server Library] (https://code.google.com/p/oauth-php/)
			|
			|* Scala:
			|
			| [Dispatch] (http://dispatch.databinder.net/Dispatch.html)
			|
			|* OBP SDKs / examples of client code with OAuth:
			|
			| [OBP SDKs](https://github.com/OpenBankProject/OBP-API/wiki/OAuth-Client-SDKS)
			|
			|### Examples :
			|
			|To show the OAuth integration in concrete examples, please check out these projects listed here:
			|[Hello-OBP-OAuth1.0a-LANGUAGE/PLATFORM](https://github.com/OpenBankProject)
			|
			|### Signature :
			|
			|According to the [section-3.4](http://tools.ietf.org/html/rfc5849#section-3.4) in the OAuth 1.0 protocol specification the signature computation is done following theses steps :
			|
			|
			|a) Signature Base String :
			|
			|The signature base string is a consistent, reproducible concatenation of several of the HTTP request elements into a single string. The string is used as an input to the signature methods.
			|
			|The signature base string includes the following components of the HTTP request:
			|
			|* The HTTP request method (e.g., "GET", "POST", etc.).
			|
			|* The authority as declared by the HTTP "Host" request header field.
			|
			|* The path and query components of the request resource URI.
			|
			|* he protocol parameters excluding the "oauth_signature".
			|
			|The signature base string does not cover the entire HTTP request. Most notably, it does not include the entity-body in most requests, nor does it include most HTTP entity-headers.
			|
			|The signature base string is constructed by concatenating together, in order, the following HTTP request elements:
			|
			|1. The HTTP request method in uppercase. For example: "HEAD", "GET", "POST", etc. If the request uses a custom HTTP method, it MUST be encoded (Section 3.6).
			|
			|2. An "&" character (ASCII code 38).
			|
			|3. The base string URI from Section 3.4.1.2, after being encoded (Section 3.6).
			|
			|4. An "&" character (ASCII code 38).
			|
			|5. The request parameters as normalized in Section 3.4.1.3.2, after being encoded (Section 3.6).
			|
			|Explained shortly below the example.
			|
			|Example:
			|
			|    POST /oauth/token HTTP/1.1
			|    Host: $getServerUrl
			|    Content-Type: application/x-www-form-urlencoded
			|    Authorization: OAuth
			|    oauth_consumer_key="91919",
			|    oauth_token="OGESD9MrWQEGPXOyPjHCRrCw7BPelWJjnomibV6bePU",
			|    oauth_signature_method="HMAC-SHA256",
			|    oauth_timestamp="1340878170",
			|    oauth_nonce="DFXOQFZVK8K46KDR11",
			|    oauth_signature="bYT5CMsGcbgUdFHObYMEfcx6bsw%3D"
			|
			|Is represented by the following signature base string (line breaks are for display purposes only):
			|
			|    POST&https%3A%2F%2F$getServerUrl&oauth_consumer_key%3D91919%26oauth_nonce%3DDFXOQFZVK8K46KDR11%26oauth_signature_method%3Dhmac-sha256%26oauth_timestamp%3D1340878170%26oauth_token%3DOGESD9MrWQEGPXOyPjHCRrCw7BPelWJjnomibV6bePU%26oauth_verifier%3DT0dXUDBZR09LUVlGTU9NSlhIUUc%26oauth_version%3D1
			|
			|The request parameters normalization :
			|
			|1. The name and value of each parameter are encoded Section 3.6.
			|
			|2. The parameters are sorted by name, using ascending byte value ordering.
			|
			|3. The name of each parameter is concatenated to its corresponding value using an "=" character (ASCII code 61) as a separator, even if the value is empty.
			|
			|4. The sorted name/value pairs are concatenated together into a single string by using an "&" character (ASCII code 38) as separator.
			|
			|B) Signing the request :
			|
			|The Open Bank Project OAuth 1.0 implementation uses the “HMAC-SHA1” and “HMAC-SHA256” as signing methods. The key to sign the base string is the concatenation of the consumer secret and the token secret with the “&” character in the middle like this: oauth_consumer_secret&oauth_token_secret, in the first step the application does not have yet a token so it will be an empty string.
			|
			|The signature that results from the signature process MUST be encoded in base 64 also since the protocol requires encoding all the OAuth parameters.
			|
			|### Illustration of integration with a bank back-end :
			|
			|The following link shows how the integration of the OAuth process would be with a bank back-end: [https://github.com/OpenBankProject/OBP-API/wiki/OAuth-Integration-Illustration](https://github.com/OpenBankProject/OBP-API/wiki/OAuth-Integration-Illustration)
			|
			|
			|
			""")

  if (APIUtil.getPropsAsBoolValue("allow_oauth2_login", false) == true) {

    glossaryItems += GlossaryItem(
      title = "OAuth 2",
      description =
        s"""
        |OAuth 2 is an authorization framework that enables applications to obtain limited access to user accounts on an HTTP service, in this case any OBP REST call. It works by delegating user authentication to the service that hosts the user account, and authorizing third-party applications to access the user account. OAuth 2 provides authorization flows for web and desktop applications, and mobile devices.
        |
        |### OAuth 2 Roles
        |
        |* Resource Owner
        |* Client
        |* Resource Server
        |* Authorization Server
        |
        |### Resource Owner: User
        |
        |The resource owner is the user who authorizes an application to access their account. The application's access to the user's account is limited to the "scope" of the authorization granted (e.g. openid).
        |
        |### Authorization Server: API
        |
        |The authorization server verifies the identity of the user then issues access tokens to the application. E.g. MITREid Connect
        |
        |### Resource Server: API
        |
        |The resource server hosts the protected user resources. E.g. OBP-API
        |
        |### Client: Application
        |
        |The client is the application that wants to access the user's resource. In order to do that, it must be authorized by the user, and the authorization must be validated by the Authorization Server: API.
        |
        |### Authorization Grant
        |
        |OAuth 2 defines four grant types, each of which is useful in different cases:
        |
        |* Authorization Code: used with server-side Applications
        |
        |* Implicit: used with Mobile Apps or Web Applications (applications that run on the user's device)
        |
        |* Resource Owner Password Credentials: used with trusted Applications, such as those owned by the service itself
        |
        |* Client Credentials: used with Applications API access
        |
        |OBP-API supports at the moment only Authorization Code
        |
        |### Step 1: Get your App key
        |
        |[Sign up]($getServerUrl/user_mgt/sign_up) or [login]($getServerUrl/user_mgt/login) as a developer
        |
        |Register your App key [HERE]($getServerUrl/consumer-registration)
        |
        |Copy and paste the CONSUMER_KEY, CONSUMER_SECRET and REDIRECT_URL for the subsequent steps below.
        |
        |
        |### Step 2: Authorization Code Link
        |
        |Using your favorite web browser request a URL like this one
				|
        |    $getOAuth2ServerUrl/authorize?response_type=code&client_id=CONSUMER_KEY&redirect_uri=REDIRECT_URL&scope=openid
        |
        |This assumes that that you are already logged in at the OAuth2 authentication server [$getOAuth2ServerUrl]($getOAuth2ServerUrl). Otherwise, you will be redirected to [$getOAuth2ServerUrl/login]($getOAuth2ServerUrl/login).
        |Please note that you use the same credentials as the sandbox [$getServerUrl]($getServerUrl) to login to the OAuth2 authentication server.
        |
        |Here is an explanation of the link components:
        |
        |* $getOAuth2ServerUrl/authorize: the API authorization endpoint
        |
        |* client_id=CONSUMER_KEY: the application's client ID (how the API identifies the application)
        |
        |* redirect_uri=REDIRECT_URL: where the service redirects the user-agent after an authorization code is granted
        |
        |* response_type=code: specifies that your application is requesting an authorization code grant
        |
        |* scope=openid: specifies the level of access that the application is requesting
        |
        |### Step 3: User Authorizes Application
        |
        |Please authorize the application on the OAuth2 server web interface by clicking on "Authorize".
        |
        |<img src="https://static.openbankproject.com/images/sandbox/oauth2-authorize.png" width="885" height="402.75"></img>
        |
        |### Step 4: Application Receives Authorization Code
        |
        |If the user clicks "Authorize", the service redirects the user-agent to the application redirect URI, which was specified during the client registration, along with an authorization code.
        |
        |    REDIRECT_URL/&scope=openid/?code=AUTHORIZATION_CODE
        |
        |The redirect would look something like this: https://YOUR-APPLICATION.com/&scope=openid/?code=h7jSgP
        |
        |### Step 5: Application Requests Access Token
        |
        |The application requests an access token from the API, by passing the authorization code along with authentication details, including the client secret, to the API token endpoint.
        |
        |    POST $getOAuth2ServerUrl/token?client_id=CONSUMER_KEY&client_secret=CONSUMER_SECRET&grant_type=authorization_code&code=AUTHORIZATION_CODE&redirect_uri=REDIRECT_URL
        |
        |### Step 6: Application Receives Access Token
        |
        |If the authorization is valid, the API will send a response containing the access token to the application. The entire response will look something like this:
        |
        |    {
        |    "access_token": "eyJraWQiOiJyc2ExIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJhZG1pbiIsImF6cCI6ImNsaWVudCIsImlzcyI6Imh0dHA6XC9cL2xvY2FsaG9zdDo4MDgwXC9vcGVuaWQtY29ubmVjdC1zZXJ2ZXItd2ViYXBwXC8iLCJleHAiOjE1MTk1MDMxODAsImlhdCI6MTUxOTQ5OTU4MCwianRpIjoiMmFmZjNhNGMtZjY5Zi00ZWM1LWE2MzEtYWUzMGYyYzQ4MjZiIn0.NwlK2EJKutaybB4YyEhuwb231ZNkD-BEwhScadcWWn8PFftjVyjqjD5_BwSiWHHa_QaESNPdZugAnF4I2DxtXmpir_x2fB2ch888AzXw6CgTT482I16m1jpL-2iSlQk1D-ZW6fJ2Qemdi3x2V13Xgt9PBvk5CsUukJ8SSqTPbSNNER9Nq2dlS-qQfg61TzhPkuuXDlmCQ3b8QHgUf6UnCfee1jRaohHQoCvJJJubmUI3dY0Df1ynTodTTZm4J1TV6Wp6ZhsPkQVmdBAUsE5kIFqADaE179lldh86-97bVHGU5a4aTYRRKoTPDltt1NvY5XJrjLCgZH8AEW7mOHz9mw",
        |    "token_type": "Bearer",
        |    "expires_in": 3599,
        |    "scope": "openid"
        |    }
        |
        |### Step 7: Try a REST call using the header
        |
        |Using your favorite http client:
        |
        |    GET $getServerUrl/obp/v3.0.0/users/current
        |
        |Body
        |
        |    Leave Empty!
        |
        |Headers:
        |
        |    Authorization: Bearer ACCESS_TOKEN
        |
        |Here is it all together:
        |
        |    GET /obp/v3.0.0/users/current HTTP/1.1 Host: $getServerUrl User-Agent: curl/7.47.0 Accept: / Authorization: Bearer "eyJraWQiOiJyc2ExIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJhZG1pbiIsImF6cCI6ImNsaWVudCIsImlzcyI6Imh0dHA6XC9cL2xvY2FsaG9zdDo4MDgwXC9vcGVuaWQtY29ubmVjdC1zZXJ2ZXItd2ViYXBwXC8iLCJleHAiOjE1MTk1MDMxODAsImlhdCI6MTUxOTQ5OTU4MCwianRpIjoiMmFmZjNhNGMtZjY5Zi00ZWM1LWE2MzEtYWUzMGYyYzQ4MjZiIn0.NwlK2EJKutaybB4YyEhuwb231ZNkD-BEwhScadcWWn8PFftjVyjqjD5_BwSiWHHa_QaESNPdZugAnF4I2DxtXmpir_x2fB2ch888AzXw6CgTT482I16m1jpL-2iSlQk1D-ZW6fJ2Qemdi3x2V13Xgt9PBvk5CsUukJ8SSqTPbSNNER9Nq2dlS-qQfg61TzhPkuuXDlmCQ3b8QHgUf6UnCfee1jRaohHQoCvJJJubmUI3dY0Df1ynTodTTZm4J1TV6Wp6ZhsPkQVmdBAUsE5kIFqADaE179lldh86-97bVHGU5a4aTYRRKoTPDltt1NvY5XJrjLCgZH8AEW7mOHz9mw"
        |
        |CURL example:
        |
        |    curl -v -H 'Authorization: Bearer eyJraWQiOiJyc2ExIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJhZG1pbiIsImF6cCI6ImNsaWVudCIsImlzcyI6Imh0dHA6XC9cL2xvY2FsaG9zdDo4MDgwXC9vcGVuaWQtY29ubmVjdC1zZXJ2ZXItd2ViYXBwXC8iLCJleHAiOjE1MTk1MDMxODAsImlhdCI6MTUxOTQ5OTU4MCwianRpIjoiMmFmZjNhNGMtZjY5Zi00ZWM1LWE2MzEtYWUzMGYyYzQ4MjZiIn0.NwlK2EJKutaybB4YyEhuwb231ZNkD-BEwhScadcWWn8PFftjVyjqjD5_BwSiWHHa_QaESNPdZugAnF4I2DxtXmpir_x2fB2ch888AzXw6CgTT482I16m1jpL-2iSlQk1D-ZW6fJ2Qemdi3x2V13Xgt9PBvk5CsUukJ8SSqTPbSNNER9Nq2dlS-qQfg61TzhPkuuXDlmCQ3b8QHgUf6UnCfee1jRaohHQoCvJJJubmUI3dY0Df1ynTodTTZm4J1TV6Wp6ZhsPkQVmdBAUsE5kIFqADaE179lldh86-97bVHGU5a4aTYRRKoTPDltt1NvY5XJrjLCgZH8AEW7mOHz9mw' $getServerUrl/obp/v3.0.0/users/current
        |
			""")
  }


else {

		glossaryItems += GlossaryItem(
			title = "OAuth 2",
			description =
					s"""
						 |OAuth 2 is not enabled for this OBP API instance.
			""")
	}





	// NOTE! Some glossary items are generated in ExampleValue.scala


}

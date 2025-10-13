package code.api.util

import code.api.Constant
import code.api.Constant._
import code.api.util.APIUtil.{getObpApiRoot, getServerUrl}
import code.api.util.ExampleValue.{accountIdExample, bankIdExample, customerIdExample, userIdExample}
import code.util.Helper.MdcLoggable
import code.webuiprops.MappedWebUiPropsProvider.getWebUiPropsValue

import java.io.File
import scala.collection.mutable.ArrayBuffer


object Glossary extends MdcLoggable  {

	def getGlossaryItem(title: String): String = {

		//logger.debug(s"getGlossaryItem says Hello. title to find is: $title")

		val something = glossaryItems.find(_.title.toLowerCase == title.toLowerCase) match {
			case Some(foundItem) =>
				/**
				 * Two important rules:
				 * 1. Make sure you have an **empty line** after the closing `</summary>` tag, otherwise the markdown/code blocks won't show correctly.
				 * 2. Make sure you have an **empty line** after the closing `</details>` tag if you have multiple collapsible sections.
				 */
				s"""
				 |<details>
				 |  <summary style="display:list-item;cursor:s-resize;">${foundItem.title}</summary>
				 |
				 |  ${foundItem.htmlDescription}
				 |</details>
				 |
				 |<br></br>
				 |""".stripMargin
				case None => "glossary-item-not-found"
		}
		//logger.debug(s"getGlossaryItem says the text to return is $something")
		something
	}

	def getGlossaryItemSimple(title: String): String = {
    // This function just returns a string without Title and collapsable element.
		// Can use this if getGlossaryItem is problematic with a certain glossary item (e.g. JSON Schema Validation Glossary Item) or just want a simple inclusion of text.

		//logger.debug(s"getGlossaryItemSimple says Hello. title to find is: $title")

		val something = glossaryItems.find(_.title.toLowerCase == title.toLowerCase) match {
			case Some(foundItem) =>
				s"""
				 |  ${foundItem.htmlDescription}
				 |""".stripMargin
			case None => "glossary-item-simple-not-found"
		}
		//logger.debug(s"getGlossaryItemSimple says the text to return is $something")
		something
	}

	def getGlossaryItemLink(title: String): String = {
		// This function just returns a link to the Glossary Item in question.
		// Can reduce bandwith and maybe make things semantically clearer if we use links instead of includes.

		val something = glossaryItems.find(_.title.toLowerCase == title.toLowerCase) match {
			case Some(foundItem) =>
				// We use the title because anchors are case sensitive, but we find it so we can log / display not found.
				s"""[here](/glossary#${title})"""
			case None => "glossary-item-link-not-found"
		}
		something
	}


	// reason of description is function: because we want make description is dynamic, so description can read
	// webui_ props dynamic instead of a constant string.
 case class GlossaryItem(
															 title: String,
															 description: () => String,
															 htmlDescription: String,
															 textDescription: String
                            )

		def makeGlossaryItem (title: String, connectorField: ConnectorField) : GlossaryItem = {
			GlossaryItem(
				title = title,
				description =
					s"""
						|Example value: ${connectorField.value}
						|
						|Description: ${connectorField.description}
						|
				""".stripMargin
			)
		}

	object GlossaryItem {

		// Constructs a GlossaryItem from just two parameters.
		def apply(title: String, description: => String): GlossaryItem = {

			// Convert markdown to HTML
			val htmlDescription = PegdownOptions.convertPegdownToHtmlTweaked(description)

			// Try and generate a plain text string (requires valid HTML)
			val textDescription: String = try {
				scala.xml.XML.loadString(htmlDescription).text
			} catch {
				// Fallback to the html
				case _ : Throwable => htmlDescription
			}

			new GlossaryItem(
				title,
				() => description,
				htmlDescription,
				textDescription
			)
		}

	}




    val glossaryItems = ArrayBuffer[GlossaryItem]()

	// NOTE! Some glossary items are defined in ExampleValue.scala


	val latestConnector : String = "rest_vMar2019"

	def messageDocLink(process: String) : String = {
		s"""<a href="/message-docs/$latestConnector#$process">$process</a>"""
	}

	val latestAkkaConnector : String = "akka_vDec2018"
	def messageDocLinkAkka(process: String) : String = {
		s"""<a href="/message-docs/$latestAkkaConnector#$process">$process</a>"""
	}

	val latestRabbitMQConnector : String = "rabbitmq_vOct2024"
	def messageDocLinkRabbitMQ(process: String) : String = {
		s"""<a href="/message-docs/$latestRabbitMQConnector#$process">$process</a>"""
	}

	// Note: this doesn't get / use an OBP version
	def getApiExplorerLink(title: String, operationId: String) : String = {
		val apiExplorerPrefix = APIUtil.getPropsValue("webui_api_explorer_url", "")
		// Note: This is hardcoded for API Explorer II
		s"""<a href="$apiExplorerPrefix/operationid/$operationId">$title</a>"""
	}

	glossaryItems += GlossaryItem(
		title = "Cheat Sheet",
		description =
			s"""
				 |### A selection of links to get you started using the Open Bank Project API platform, applications and tools.
				 				 |
				 |[OBP API Installation](https://github.com/OpenBankProject/OBP-API/blob/develop/README.md)
				 				 |
				 |[OBP API Contributing](https://github.com/OpenBankProject/OBP-API/blob/develop/CONTRIBUTING.md)
				 				 |
				 |[Access Control](/glossary#API.Access-Control)
				 				 |
|[Versioning](https://github.com/OpenBankProject/OBP-API/wiki/API-Versioning)
|
 |[Authentication](https://github.com/OpenBankProject/OBP-API/wiki/Authentication)
|
				 |[Interfaces](/glossary#API.Interfaces)
				 				 |
				 |[Endpoints](https://apiexplorersandbox.openbankproject.com)
				 				 |
				 |[Glossary](/glossary)
				 				 |
				 |[Access Control](/glossary#API.Access-Control)
				 				 |
				 |[OBP Akka](/glossary#Adapter.Akka.Intro)
				 				 |
				 |[API Explorer](https://github.com/OpenBankProject/API-Explorer/blob/develop/README.md)
				 				 |
				 |[API Manager](https://github.com/OpenBankProject/API-Manager/blob/master/README.md)
				 				 |
				 |[API Tester](https://github.com/OpenBankProject/API-Tester/blob/master/README.md)
				 				 |
				 				 |
""")







	glossaryItems += GlossaryItem(
		title = "Adapter.Akka.Intro",
		description =
			s"""
				 |## Use Akka as an interface between OBP and your Core Banking System (CBS).
|
|For an introduction to Akka see [here](https://akka.io/)
|
|The OBP Akka interface allows integrators to write Java or Scala Adapters (any JVM language with Akka support)
|respond to requests for data and services from OBP.
|
|For the message definitions see [here](/message-docs?connector=akka_vDec2018)
|
|### Installation Prerequisites
|
|
|* You have OBP-API running.
|
|* Ideally you have API Explorer running (the application serving this page) but its not necessary - you could use any other REST client.
|* You might want to also run API Manager as it makes it easier to grant yourself roles, but its not necessary - you could use the API Explorer / any REST client instead.
|
|
|### Create a Customer User and an Admin User
|
|* Register a User who will use the API as a Customer.
|* Register another User that will use the API as an Admin. The Admin user will need some Roles. See [here](/index#OBPv2_0_0-addEntitlement). You can bootstrap an Admin user by editing the Props file. See the README for that.
|
|### Add some authentication context to the Customer User
|
|* As the Admin User, use the [Create Auth Context](/index#OBPv3_1_0-createUserAuthContext) endpoint to add one or more attributes to the Customer User.
|For instance you could add the name/value pair CUSTOMER_NUMBER/889763 and this will be sent to the Adapter / CBS inside the AuthInfo object.
|
|
|Now you should be able to use the [Get Auth Contexts](/index#OBPv3_1_0-getUserAuthContexts) endpoint to see the data you added.
|
|### Write or Build an Adapter to respond to the following messages.
|
| When getting started, we suggest that you implement the messages in the following order:
|
|1) Core (Prerequisites) - Get Adapter, Get Banks, Get Bank
|
|* ${messageDocLinkAkka("obp.getAdapterInfo")}
|
|Now you should be able to use the [Adapter Info](/index#OBPv3_1_0-getAdapterInfo) endpoint
|
|* ${messageDocLinkAkka("obp.getBanks")}
|
|Now you should be able to use the [Get Banks](/index#OBPv3_0_0-getBanks) endpoint
|
|* ${messageDocLinkAkka("obp.getBank")}
|
|Now you should be able to use the [Get Bank](/index#OBPv3_0_0-bankById) endpoint
|
|
|2) Get Customers by USER_ID
|
|* ${messageDocLinkAkka("obp.getCustomersByUserId")}
|
|Now you should be able to use the [Get Customers](/index#OBPv3_0_0-get.CustomersByUserId) endpoint.
|
|
|3) Get Accounts
|
|* ${messageDocLinkAkka("obp.checkBankAccountExists")}
|* ${messageDocLinkAkka("obp.getCoreBankAccounts")}
|
| The above messages should enable at least the following endpoints:
|
|* [Get Accounts at Bank (IDs only)](/index#OBPv3_0_0-getPrivateAccountIdsbyBankId)
|* [Get Accounts at Bank (Minimal).](/index#OBPv3_0_0-privateAccountsAtOneBank)
|* [Get Accounts at all Banks (private)](/index#OBPv3_0_0-corePrivateAccountsAllBanks)
|
|4) Get Account
|
|* ${messageDocLinkAkka("obp.getBankAccount")}
|
| The above message should enable at least the following endpoints:
|
|* [Get Account by Id - Core](/index#OBPv3_0_0-getCoreAccountById)
|* [Get Account by Id - Full](/index#OBPv3_0_0-getPrivateAccountById)
|
|5) Get Transactions
|
|* ${messageDocLinkAkka("obp.getTransactions")}
|* ${messageDocLinkAkka("obp.getTransaction")}
|
|6) Manage Counterparties
|
|* ${messageDocLinkAkka("obp.getCounterparties")}
|
|7) Get Transaction Request Types
|
|* This is configured using OBP Props - No messages required
|
|
|This glossary item is Work In Progress.
|
""")

	glossaryItems += GlossaryItem(
		title = "Adapter.Stored_Procedure.Intro",
		description =
			s"""
					|## Use Stored_Procedure as an interface between OBP and your Core Banking System (CBS).
					|
					|
					|For an introduction to Stored Procedures see [here](https://en.wikipedia.org/wiki/Stored_procedure)
					|
					|### Installation Prerequisites
					|
					|
					|* You have OBP-API running and it is connected to a stored procedure related database.
					|* Ideally you have API Explorer running (the application serving this page) but its not necessary - you could use any other REST client.
					|* You might want to also run API Manager as it makes it easier to grant yourself roles, but its not necessary - you could use the API Explorer / any REST client instead.
					|"""
	)

	glossaryItems += GlossaryItem(
		title = "Roles of Open Bank Project",
		description =
			s"""<ol>${ApiRole.availableRoles.sorted.map(i => "<li>" + i + "</li>").mkString}</ol>""".stripMargin
	)




	// ***Note***! Don't use "--" (double hyphen) in the description because API Explorer scala.xml.XML.loadString cannot parse.

	glossaryItems += GlossaryItem(
		title = "Connector",
		description =
			s"""In OBP, most internal functions / methods can have different implementations which follow the same interface.
				 |
				 |These functions are called connector methods and their implementations.
				 |
				 |The default implementation of the connector is the "mapped" connector.
				 |
				 |It's called "mapped" because the default datasource on OBP is a relational database, and access to that database is always done through an Object-Relational Mapper (ORM) called Mapper (from a framework we use called Liftweb).
				 |
				 |
				 |<pre>
				 |[=============]                                                                     [============]       [============]
				 |[.............]                                                                     [            ]       [            ]
				 |[...OBP API...] ===> OBP Endpoints call connector functions (aka methods) ===>      [  Connector ] ===>  [  Database  ]
				 |[.............]          The default implementation is called "Mapped"              [  (Mapped)  ]       [  (Adapter) ]
				 |[=============]              The Mapped Connector talks to a Database               [============]       [============]
				 |
				 |</pre>
				 |
				 |However, there are multiple available connector implementations - and you can also mix and create your own.|
				 |
				 |E.g. RabbitMq
				 |
				 |<pre>
				 |[=============]                              [============]       [============]     [============]       [============]
				 |[             ]                              [            ]       [            ]     [            ]       [            ]
				 |[   OBP API   ] ===> RabbitMq Connector ===> [  RabbitMq  ] ===>  [  RabbitMq  ]     [ OBP RabbitMq] ===> [     CBS    ]
				 |[             ]      Puts OBP Messages       [  Connector ]       [  Cluster   ]     [  Adapter   ]       [            ]
				 |[=============]       onto a RabbitMq           [============]       [============]     [============]       [============]
				 |
				 |</pre>
				 |
				 |
				 |
				 |You can mix and match them using the Star connector and you can write your own in Scala. You can also write Adapters in any language which respond to messages sent by the connector.
				 |
				 |we use the term "Connector" to mean the Scala/Java/Other JVM code in OBP that connects directly or indirectly to the systems of record i.e. the Core Banking Systems, Payment Systems and Databases.
				 |
				 |
				 | A "Direct Connector" is considered to be one that talks directly to the system of record or existing service layer.
				 |
				 | i.e. API -> Connector -> CBS
				 |
				 | An "Indirect Connector" is considered one which pairs with an Adapter which in turn talks to the system of record or service layer.
				 |
				 | i.e. API -> Connector -> Adapter -> CBS
				 |
				 | The advantage of a Direct connector is that its perhaps simpler. The disadvantage is that you have to code in a JVM language, understand a bit about OBP internals and a bit of Scala.
				 |
				 | The advantage of the Indirect Connector is that you can write the Adapter in any language and the Connector and Adapter are decoupled (you just have to respect the Outbound / Inbound message format).
				 |
				 | The default Connector in OBP is a Direct Connector called "mapped". It is called the "mapped" connector because it talks directly to the OBP database (Postgres, MySQL, Oracle, MSSQL etc.) via the Liftweb ORM which is called Mapper.
				 |
				 |If you want to create your own (Direct) Connector you can fork any of the connectors within OBP.
				 |
				 |
				 | There is a special Connector called the Star Connector which can use functions from all the normal connectors.
				 |
				 | Using the Star Connector we can dynamically reroute function calls to different Connectors per function per bank_id.
				 |
				 | The OBP API Manager has a GUI to manage this or you can use the OBP Method Routing APIs to set destinations for each function call.
				 |
				 | Note: We generate the source code for individual connectors automatically.
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
  |* linkedCustomers is a list of Customers the User is explicitly linked to. Use the <a href="/#OBPv2_0_0-createUserCustomerLinks">Create User Customer Link endpoint</a> to populate this data.
  |* userAuthContexts may contain the customer number or other tokens in order to boot strap the User Customer Links
  |or provide an alternative method of tagging the User with an authorisation context.
  |Use the <a href="/#OBPv3_1_0-createUserAuthContext">Create UserAuthContext endpoint</a> to populate this data.
  |* cbsToken is a token used by the CBS to identify the user's session. Either generated by the CBS or Gateway.
  |* isFirst is a flag that indicates that OBP should refresh the user's list of accounts from the CBS (and flush / invalidate any User's cache)
  |* correlationId just identifies the API call.
  |* authViews are entitlements given by account holders to third party users e.g. Sam may grant her accountant Jill read only access to her business account. See the <a href="/index#OBPv3_0_0-createViewForBankAccount">Create View endpoint</a>
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
					 |<img width="468" alt="OBP Interfaces Image" src="https://user-images.githubusercontent.com/485218/49711990-9ef99d00-fc42-11e8-8cb4-cc68bab74703.png"></img>
					 |
  |
  |
 |"""
	)

	glossaryItems += GlossaryItem(
		title = "API.Timeouts",
		description =
				s"""
					 |<img width="1000" alt="OBP Timeouts Image" src="https://user-images.githubusercontent.com/29032407/50471858-b52f8900-09b6-11e9-9888-454e6d41907c.png"></img>
					 |
           |
           |
           |"""
	)




	glossaryItems += GlossaryItem(
		title = "API.Access Control",
		description =
			s"""
|
|Access Control is achieved via the following mechanisms in OBP:
|
|* APIs are enabled in Props. See the README.md
|
|* Consumers (AKA Clients or Apps) are granted access to Roles and Views via Scopes
|
|See [here](/index#group-Scope) for related endpoints and documentation.
|
|* Users are granted access to System or Bank Roles via Entitlements.
|
|See [here](/index#group-Role) for related endpoints and documentation.
|
|Users may request Entitlement Requests [here](/index#OBPv3_0_0-addEntitlementRequest)
|
|Entitlements and Entitlement Requests can be managed in the OBP API Manager.
|
|* Users are granted access to Customer Accounts, Transactions and Payments via Views.
|
|See [here](/index#group-View) for related endpoints and documentation.
|
|User Views can be managed via the OBP Sofit Consent App.
|
|
				 					 |<img width="468" alt="OBP Access Control Image" src="https://user-images.githubusercontent.com/485218/49863122-e6795800-fdff-11e8-9b05-bba99e2c72da.png"></img>
				 					 |
				 |
  |
 |"""
	)



	val justInTimeEntitlements : String = if (APIUtil.getPropsAsBoolValue("create_just_in_time_entitlements", false))
	{"Just in Time Entitlements are ENABLED on this instance."} else {"Just in Time Entitlements are NOT enabled on this instance."}


	glossaryItems += GlossaryItem(
		title = "Just In Time Entitlements",
		description =
			s"""
				 |
				 |${justInTimeEntitlements}
				 |
				 |This is how Just in Time Entitlements work:
				 |
				 |If Just in Time Entitlements are enabled then OBP does the following:
				 |If a user is trying to use a Role (via an endpoint) and the user could grant them selves the required Role(s), then OBP automatically grants the Role.
				 |i.e. if the User already has canCreateEntitlementAtOneBank or canCreateEntitlementAtAnyBank then OBP will automatically grant a role that would be granted by a manual process anyway.
				 |This speeds up the process of granting of roles. Certain roles are excluded from this automation:
				 |  - CanCreateEntitlementAtOneBank
				 |  - CanCreateEntitlementAtAnyBank
				 |If create_just_in_time_entitlements is again set to false after it was true for a while, any auto granted Entitlements to roles are kept in place.
				 |Note: In the entitlements model we set createdbyprocess=create_just_in_time_entitlements. For manual operations we set createdbyprocess=manual
				 |
				 |To enable / disable this feature set the Props create_just_in_time_entitlements=true or false. The default is false.
				 |
				 |"""
	)







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
		title =
			"Age",
		description =
			"""The user Age"""
	)

	  glossaryItems += GlossaryItem(
		title = "Account.account_id",
		description =
		s"""
		  |An identifier for the account that MUST NOT leak the account number or other identifier nomrally used by the customer or bank staff.
		  |It SHOULD be a UUID. It MUST be unique in combination with the BANK_ID. ACCOUNT_ID is used in many URLS so it should be considered public.
		  |(We do NOT use account number in URLs since URLs are cached and logged all over the internet.)
		  |In local / sandbox mode, ACCOUNT_ID is generated as a UUID and stored in the database.
		  |In non sandbox modes (RabbitMq etc.), ACCOUNT_ID is mapped to core banking account numbers / identifiers at the South Side Adapter level.
		  |ACCOUNT_ID is used to link Metadata and Views so it must be persistant and known to the North Side (OBP-API).
			|
			| Example value: ${accountIdExample.value}
			|
		""")

	  glossaryItems += GlossaryItem(
		title = "Bank",
		description =
		"""
		  |A Bank (aka Space) represents a financial institution, brand or organizational unit under which resources such as endpoints and entities exist.
|
|Both standard entities (e.g. financial products and bank accounts in the OBP standard) and dynamic entities and endpoints (created by you or your organisation) can exist at the Bank level.
|
|For example see [Bank/Space level Dynamic Entities](/?version=OBPv4.0.0&operation_id=OBPv4_0_0-createBankLevelDynamicEntity) and [Bank/Space level Dynamic Endpoints](http://localhost:8082/?version=OBPv4.0.0&operation_id=OBPv4_0_0-createBankLevelDynamicEndpoint)
|
|The Bank is important because many Roles can be granted at the Bank level. In this way, it's possible to create segregated or partitioned sets of endpoints and data structures in a single OBP instance.
|
|A User creating a Bank (if they have the right so to do), automatically gets the Entitlement to grant any Role for that Bank. Thus the creator of a Bank / Space becomes the "god" of that Bank / Space.
|
|Basic attributes for the bank resource include identifying information such as name, logo and website.
|
|Using the OBP endpoints for bank accounts it's possible to view accounts at one Bank or aggregate accounts from all Banks connected to the OBP instance.
|
|See also Props settings named "brand".
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
		  |Each Consumer has a consumer key and secret which allows it to enter into secure communication with the API server.
			|
			|A Consumer is given a Consumer ID (a UUID) which appears in logs and messages to the backend.
			|
			|A Consumer may be pinned to an mTLS certificate i.e. the consumer record in the database is given a field which matches the PEM representation of the certificate.
			|
			|After pinning, the consumer must present the certificate in all communication with the server.
			|
			|There is a one to one relationship between a Consumer and its certificate. i.e. OBP does not (currently) store the history of certificates bound to a Consumer. If a certificate expires, the third party provider (TPP) must generate a new consumer using a new certificate. In this case, related resources such as rate limits and scopes must be copied from the old consumer to the new consumer. In the future, OBP may store multiple certificates for a consumer, but a certificate will always identify only one consumer record.
			|
		""")

	  glossaryItems += GlossaryItem(
		title = "Consumer.consumer_key (Consumer Key)",
		description =
		s"""
			 |The client identifier issued to the client during the registration process. It is a unique string representing the registration information provided by the client.
			 |At the time the consumer_key was introduced OAuth 1.0a was only available. The OAuth 2.0 counterpart for this value is client_id
				|""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "client_id (Client ID)",
		description =
			s"""Please see Consumer.consumer_key""".stripMargin)

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
			|The identifier that MUST NOT leak the customer number or other identifier normally used by the customer or bank staff. It SHOULD be a UUID and MUST be unique in combination with BANK_ID.
			|
			|Example value: ${customerIdExample.value}
		  """)

	  glossaryItems += GlossaryItem(
		title = "Transaction",
		description =
		  """
			|Transactions are records of successful movements of value into or out of an `Account`.
			|
			|OBP Transactions don't contain any "draft" or "pending" Transactions; pending transactions see represented by Transaction Requests.
			|
			|OBP Transactions are modelled on a Bank statement where everything is based on the perspective of my account.
			|That is, if I look at "my account", I see credits (positive numbers) and debits (negative numbers)

			|An OBP transaction stores information including the:
			|Bank ID
			|Account ID
			|Currency
			|Amount (positive for a credit, negative for a debit)
			|Date
			|Counterparty (information that describes the other party in the transaction)
			|- optionally description and new balance.
|
|Note, OBP operates a Double-Entry Bookkeeping system which means that every transfer of value within OBP is represented by *two* transactions.
|
|For instance, to represent 5 Euros going from Account A to Account B, we would have 2 transactions:
|
|Transaction 1.
|
|Account: A
|Currency: EUR
|Amount: -5
|CounterpartyCounterpartyCounterparty: Account B
|
|Transaction 2.
|
|Account: B
|Currency: EUR
|Amount: +5
|Counterparty: Account A
|
|The sum of the two transactions must be zero.
|
|What about representing value coming into or out of the system? Here we use "settlement accounts":
|
|OBP-INCOMING-SETTLEMENT-ACCOUNT is typically the ID for a default incoming settlement account
|
|OBP-OUTGOING-SETTLEMENT-ACCOUNT is typically the ID for a default outgoing settlement account
|
|See the following diagram:
|
|![OBP Double-Entry Bookkeeping](https://user-images.githubusercontent.com/485218/167990092-e76e6265-faa2-4425-b366-e570ed3301b9.png)
|
|See the [Get Double Entry Transaction](/index?version=OBPv4.0.0&operation_id=OBPv4_0_0-getDoubleEntryTransaction&currentTag=Transaction#OBPv4_0_0-getDoubleEntryTransaction) endpoint
|
|
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
			|The host name of the authentication service. e.g. the OBP hostname or OIDC host.
		  """)

	  glossaryItems += GlossaryItem(
		title = "User.provider_id",
		description =
		  """
			|The id of the user given by the authentication provider. This is UNIQUE in combination with PROVIDER name.
		  """)

	  glossaryItems += GlossaryItem(
		title = "User Customer Links",
		description =
		  """
			|Link Users and Customers in a many to many relationship. A User can represent many Customers (e.g. the bank may have several Customer records for the same individual or a dependant). In this way Customers can easily be attached / detached from Users.
		  """)

	  glossaryItems += GlossaryItem(
		title = "Consent",
		description =
			s"""Consents provide a mechanism through which a resource owner (e.g. a customer) can grant a third party certain access to their resources.
|
|The following are important considerations in Consent flows:
|
|1) The privacy of the resource owner (the Customer or User) should be preserved.
|
|This means that when a TPP first asks a User if they would like to provide their data, the user should not be authenticated.
|Thus the start of the Consent process authenticates the Client (TPP) but not the User.
|
|Authentication of the user comes later.
|
|${getApiExplorerLink("This endpoint initiates a consent in OBP", "OBPv5.0.0-createConsentRequest")}
|
|2) Consent finalisation often involves SCA.
|
|Since a consent gives its holder privileges on the API, we need to make sure it is not created lightly, therefore some second factor of authentication is employed.
|
|${getApiExplorerLink("This endpoint finalises an OBP consent", "OBPv5.0.0-createConsentByConsentRequestIdSms")}
|
|3) A User should be able to list and revoke their consents.
|
|
|
|${getApiExplorerLink("This endpoint lists consents for the authenticated user.", "OBPv5.1.0-getMyConsents")}
|
|${getApiExplorerLink("This endpoint revokes a consent for the current user.", "OBPv3.1.0-revokeConsent")}
|
|This gives the user visibility over the consents they have granted to various apps for various purposes and confidence they can stop the TPP acting for a certain purpose.
|
|4) The consent manager should be able to list and revoke consents.
|
|${getApiExplorerLink("This is a management endpoint lists consents with various query parameters", "OBPv5.1.0-getConsentsAtBank")}
|
|${getApiExplorerLink("This is a management endpoint to revoke a consent", "OBPv5.1.0-revokeConsentAtBank")}
|
|The consent manager may want to list the consents by each Client or User and the ability to revoke individual consents (rather than disabling a client completely).
|
|This requires that the resource server stores the CONSENT_ID and other information so that it can be disabled or queried.
|
|However, the consent manager should not be able to see the CONSENT_ID since this would make it easier to actually use it.
|
|5) A consent is bound to the application has created it.
|
|The User gave consent to a certain application not any application.
|
|6) The consent will have a limited life time.
|
|The consent can become valid in the future and need not last forever.
|
|7) The consent will be signed using JWT.
|
|This increases the security of the claims contained in the consent.
|
|
|
				|See ${getGlossaryItemLink("Consent_OBP_Flow_Example")} for an example flow.
				|See ${getGlossaryItemLink("Consent_Account_Onboarding")} for more information about onboarding.
|
				|<img width="468" alt="OBP Access Control Image" src="$getServerUrl/media/images/glossary/OBP_Consent_Request__3_.png"></img>
				|""".stripMargin)


	glossaryItems += GlossaryItem(
		title = "Consent_OBP_Flow_Example",
		description =
				s"""
					|#### 1) Call endpoint Create Consent Request using application access (Client Credentials)
					|
					|Url: [$getObpApiRoot/v5.0.0/consumer/consent-requests]($getObpApiRoot/v5.0.0/consumer/consent-requests)
					|
					|Post body:
					|
					|```
					|{
					|  "everything": false,
					|  "account_access": [],
					|  "entitlements": [
					|    {
					|      "bank_id": "gh.29.uk.x",
					|      "role_name": "CanGetCustomer"
					|    }
					|  ],
					|  "email": "marko@tesobe.com"
					|}
					|```
					|
					|Output:
					|```
					|{
					|  "consent_request_id":"bc0209bd-bdbe-4329-b953-d92d17d733f4",
					|  "payload":{
					|    "everything":false,
					|    "account_access":[],
					|    "entitlements":[{
					|      "bank_id":"gh.29.uk.x",
					|      "role_name":"CanGetCustomer"
					|    }],
					|    "email":"marko@tesobe.com"
					|  },
					|  "consumer_id":"0b34068b-cb22-489a-b1ee-9f49347b3346"
					|}
					|```
					|
					|
					|
					|
					|#### 2) Call endpoint Create Consent By CONSENT_REQUEST_ID (SMS) with logged on user
					|
					|Url: $getObpApiRoot/v5.0.0/consumer/consent-requests/bc0209bd-bdbe-4329-b953-d92d17d733f4/EMAIL/consents
					|
					|Output:
					|```
					|{
					|  "consent_id":"155f86b2-247f-4702-a7b2-671f2c3303b6",
					|  "jwt":"eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOlt7InJvbGVfbmFtZSI6IkNhbkdldEN1c3RvbWVyIiwiYmFua19pZCI6ImdoLjI5LnVrLngifV0sImNyZWF0ZWRCeVVzZXJJZCI6ImFiNjUzOWE5LWIxMDUtNDQ4OS1hODgzLTBhZDhkNmM2MTY1NyIsInN1YiI6IjU3NGY4OGU5LTE5NDktNDQwNy05NTMwLTA0MzM3MTU5YzU2NiIsImF1ZCI6IjFhMTA0NjNiLTc4NTYtNDU4ZC1hZGI2LTViNTk1OGY1NmIxZiIsIm5iZiI6MTY2OTg5NDU5OSwiaXNzIjoiaHR0cDpcL1wvMTI3LjAuMC4xOjgwODAiLCJleHAiOjE2Njk4OTgxOTksImlhdCI6MTY2OTg5NDU5OSwianRpIjoiMTU1Zjg2YjItMjQ3Zi00NzAyLWE3YjItNjcxZjJjMzMwM2I2Iiwidmlld3MiOltdfQ.lLbn9BtgKvgAcb07if12SaEyPAKgXOEmr6x3Y5pU-vE",
					|  "status":"INITIATED",
					|  "consent_request_id":"bc0209bd-bdbe-4329-b953-d92d17d733f4"
					|}
					|```
					|
					|#### 3) We receive the SCA message via SMS
					|Your consent challenge : 29131491, Application: Any application
					|
					|
					|
					|
					|#### 4) Call endpoint Answer Consent Challenge with logged on user
					|Url: $getObpApiRoot/v5.0.0/banks/gh.29.uk.x/consents/155f86b2-247f-4702-a7b2-671f2c3303b6/challenge
					|Post body:
					|```
					|{
					|  "answer": "29131491"
					|}
					|```
					|Output:
					|```
					|{
					|  "consent_id":"155f86b2-247f-4702-a7b2-671f2c3303b6",
					|  "jwt":"eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOlt7InJvbGVfbmFtZSI6IkNhbkdldEN1c3RvbWVyIiwiYmFua19pZCI6ImdoLjI5LnVrLngifV0sImNyZWF0ZWRCeVVzZXJJZCI6ImFiNjUzOWE5LWIxMDUtNDQ4OS1hODgzLTBhZDhkNmM2MTY1NyIsInN1YiI6IjU3NGY4OGU5LTE5NDktNDQwNy05NTMwLTA0MzM3MTU5YzU2NiIsImF1ZCI6IjFhMTA0NjNiLTc4NTYtNDU4ZC1hZGI2LTViNTk1OGY1NmIxZiIsIm5iZiI6MTY2OTg5NDU5OSwiaXNzIjoiaHR0cDpcL1wvMTI3LjAuMC4xOjgwODAiLCJleHAiOjE2Njk4OTgxOTksImlhdCI6MTY2OTg5NDU5OSwianRpIjoiMTU1Zjg2YjItMjQ3Zi00NzAyLWE3YjItNjcxZjJjMzMwM2I2Iiwidmlld3MiOltdfQ.lLbn9BtgKvgAcb07if12SaEyPAKgXOEmr6x3Y5pU-vE",
					|  "status":"ACCEPTED"
					|}
					|```
					|
					|
					|
					|
					|#### 5) Call endpoint Get Customer by CUSTOMER_ID with Consent Header
					|
					|Url: $getObpApiRoot/v5.0.0/banks/gh.29.uk.x/customers/a9c8bea0-4f03-4762-8f27-4b463bb50a93
					|
					|Request Header:
					|```
					|Consent-JWT:eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOlt7InJvbGVfbmFtZSI6IkNhbkdldEN1c3RvbWVyIiwiYmFua19pZCI6ImdoLjI5LnVrLngifV0sImNyZWF0ZWRCeVVzZXJJZCI6ImFiNjUzOWE5LWIxMDUtNDQ4OS1hODgzLTBhZDhkNmM2MTY1NyIsInN1YiI6IjU3NGY4OGU5LTE5NDktNDQwNy05NTMwLTA0MzM3MTU5YzU2NiIsImF1ZCI6IjFhMTA0NjNiLTc4NTYtNDU4ZC1hZGI2LTViNTk1OGY1NmIxZiIsIm5iZiI6MTY2OTg5NDU5OSwiaXNzIjoiaHR0cDpcL1wvMTI3LjAuMC4xOjgwODAiLCJleHAiOjE2Njk4OTgxOTksImlhdCI6MTY2OTg5NDU5OSwianRpIjoiMTU1Zjg2YjItMjQ3Zi00NzAyLWE3YjItNjcxZjJjMzMwM2I2Iiwidmlld3MiOltdfQ.lLbn9BtgKvgAcb07if12SaEyPAKgXOEmr6x3Y5pU-
					|```
					|Output:
					|```
					|{
					|  "bank_id":"gh.29.uk.x",
					|  "customer_id":"a9c8bea0-4f03-4762-8f27-4b463bb50a93",
					|  "customer_number":"0908977830011-#2",
					|  "legal_name":"NONE",
					|  "mobile_phone_number":"+3816319549071",
					|  "email":"marko@tesobe.com1",
					|  "face_image":{
					|    "url":"www.openbankproject",
					|    "date":"2017-09-18T22:00:00Z"
					|  },
					|  "date_of_birth":"2017-09-18T22:00:00Z",
					|  "relationship_status":"Single",
					|  "dependants":5,
					|  "dob_of_dependants":[],
					|  "credit_rating":{
					|    "rating":"3",
					|    "source":"OBP"
					|  },
					|  "credit_limit":{
					|    "currency":"EUR",
					|    "amount":"10001"
					|  },
					|  "highest_education_attained":"Bachelor’s Degree",
					|  "employment_status":"Employed",
					|  "kyc_status":true,
					|  "last_ok_date":"2017-09-18T22:00:00Z",
					|  "title":null,
					|  "branch_id":"3210",
					|  "name_suffix":null,
					|  "customer_attributes":[]
					|}
					|```
					|""".stripMargin)



	glossaryItems += GlossaryItem(
		title = "Consent_Account_Onboarding",
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
		title = "Authentication",
		description =
			s"""
			|Authentication generally refers to a set of processes which result in a resource server (in this case, OBP-API) knowing about the User and/or Application that is making the http request it receives.
|
|In most cases when we talk about authentication we are thinking about User authentication, e.g. the user J.Brown is requesting data from the API.
|However, user authentication is pretty much always accompanied by knowledge of the Client AKA Consumer, TPP or Application.
|In some cases, we only perform Client authentication which results in knowledge of the Application but not the human that is making the call. This is useful when we want to protect the identity of a user but still want to control access to the API.
|
|In most cases, OBP-API server knows about at least two entities involved in the http request / call: The Client and the User - but it will also know about (and trust) the Identity Server (Provider) that authenticated the user and other elements in the chain of trust such as load balancers and certificate authorities.
|
|In simple terms, there are two phases of the Authentication process:
|
|1) The phase where an authorisation token is obtained.
|2) The phase where an authorisation token is used.
|
|Phase 1 is an exchange of credentials such as a username and password and possibly knowledge of a "second factor" for a token.
|
|Phase 2 is the execution of an http call which contains the token in a "header" in exchange for some response data or some resource being created, update or deleted.
|
|There are several methods of obtaining and using a token which vary in their ease of use and security.
|
|Direct Login and OAuth 1.0a are used for testing purposes / local installations and are built into OBP.
|
|OAuth2 / Open ID Connect (OIDC) depend on the configuration of Identity Provider solutions such as Keycloak or Hydra or external services such as Google or Yahoo.
|
|Open Bank Project can support multiple identity providers per OBP instance. For example, for a single OBP installation, some Users could authenticate against Google and some could authenticate against a local identity provider.
|In the cases where multiple identity providers are configured, OBP differentiates between Users by not only their Username but also by their "Identity Provider". i.e. J.Brown logged in via Google is distinct from J.Brown who logged in via a local OBP instance.
|
|Phase 1 generally results in a temporary token i.e. a token that is valid for a limited amount of time e.g. 2 hours or 3 minutes.
|
|Phase 1 might also result in a token that represents a subset of the User's full permissions. This token is generally called a Consent. i.e. a User might give consent for an application to access one of her accounts but not all of them. A Consent is generally given to a Client and bound to that Client i.e. no other application may use it.
|
|Phase 2 results in OBP having identified a User record in the OBP database so that Authorisation can proceed.
|
""")


	glossaryItems += GlossaryItem(
		title = "Authorization",
		description =
			s"""
|If Authentication involves the process of determining the *identity* of a user or application, Authorization involves the process of determining *what* the user or application can do.
|
|In OBP, Endpoints are protected by "Guards".
|
|There are two types of permissions which can be granted:
|
|1) *Entitlements to Roles* provide course grained access to resources which are related to the OBP system or a bank / space e.g. CanCreateAtm would allow the holder to create an ATM record.
|
|2) *Account Access records* provide fine grained permissions to customer bank accounts, their transactions and payments through Views. e.g. the A User with the Balances View on Account No 12345 would be allowed to get the balances on that account.
|
|Both types of permissions can be encapsulated in Consents or other authentication mechanisms.
|
|When OBP receives a call, after authentication is performed, OBP checks if the caller has sufficient permissions.
|
|If an endpoint guard blocks a call due to insufficient permissions / authorization, OBP will return an OBP- error message.
|
|If the caller passes the guards, the OBP-API forwards the request to the next step in the process.
|
|Note: All OBP- error messages can be found in the OBP-API logs and OBP source code for debugging purposes.
""")



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
			|    directlogin: username=janeburel,
			|                 password=the-password-of-jane,
			|                 consumer_key=your-consumer-key-from-step-one
			|
			|Here is it all together:
			|
			|	POST $getServerUrl/my/logins/direct HTTP/1.1
			|	$directLoginHeaderName: username=janeburel, password=686876, consumer_key=GET-YOUR-OWN-API-KEY-FROM-THE-OBP
			|	Content-Type: application/json
			|	Host: 127.0.0.1:8080
			|	Connection: close
			|	User-Agent: Paw/2.3.3 (Macintosh; OS X/10.11.3) GCDHTTPRequest
			|	Content-Length: 0
			|
			|Note: HTTP/2.0 requires that header names are *lower* case. Currently the header name for $directLoginHeaderName is case insensitive.
			|
      |To troubleshoot request headers, you may want to ask your administrator to Echo Request headers.
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
			|	$directLoginHeaderName: token=your-token-from-step-2
			|
			|Here is another example:
			|
			|	PUT $getObpApiRoot/v2.0.0/banks/enbd-egy--p3/accounts/newaccount1 HTTP/1.1
			|	$directLoginHeaderName: token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyIiOiIifQ.C8hJZNPDI59OOu78pYs4BWp0YY_21C6r4A9VbgfZLMA
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
		title = "Echo Request Headers",
		description =
			s"""
			 |Question: How can I see the request headers that OBP API finally receives from a REST client after the request has passed through HTTP infrastructure such as load balancers, firewalls and proxies?
|
|Answer: If your OBP administrator (you?) sets the following OBP API Props:
|
|```echo_request_headers=true```
|
|then OBP API will echo all the request headers it receives to the response headers except that every request header name is prefixed with echo_
|
|e.g. if you send the request header:value "DirectLogin:hello" it will be echoed in the response headers as "echo_DirectLogin:hello"
|
|Note: HTTP/2.0 requires that header names must be *lower* case. This can be a source of confusion as some libraries / tools may drop or convert header names to lowercase.
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
			|	POST $getObpApiRoot/v4.0.0/users
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
			|	POST $getObpApiRoot/v4.0.0/banks/BANK_ID/customers
			|
			|Body:
			|
			|	{  "legal_name":"Eveline Tripman",  "mobile_phone_number":"+44 07972 444 876",  "email":"eveline@example.com",  "face_image":{    "url":"www.openbankproject",    "date":"1100-01-01T00:00:00Z"  },  "date_of_birth":"1100-01-01T00:00:00Z",  "relationship_status":"single",  "dependants":10,  "dob_of_dependants":["1100-01-01T00:00:00Z"],  "credit_rating":{    "rating":"OBP",    "source":"OBP"  },  "credit_limit":{    "currency":"EUR",    "amount":"10"  },  "highest_education_attained":"Master",  "employment_status":"worker",  "kyc_status":true,  "last_ok_date":"1100-01-01T00:00:00Z",  "title":"Dr.",  "branch_id":"DERBY6",  "name_suffix":"Sr"}
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: $directLoginHeaderName token="your-token-from-direct-login"
			|
			|### 3) List customers for the user
			|
			|Action:
			|
			|	GET $getObpApiRoot/v4.0.0/users/current/customers
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
			|	POST $getObpApiRoot/v4.0.0/banks/BANK_ID/user_customer_links
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
			|	PUT $getObpApiRoot/v4.0.0/banks/BANK_ID/accounts/ACCOUNT_ID
			|
			|Body:
			|
			|	{  "user_id":"userid-from-step-1",  "label":"My Account",  "product_code":"AC",  "balance":{    "currency":"EUR",    "amount":"10"  },  "branch_id":"DERBY6",  "account_routing":{    "scheme":"AccountNumber",    "address":"4930396"  },  "account_attributes":[{    "product_code":"saving1",    "account_attribute_id":"613c83ea-80f9-4560-8404-b9cd4ec42a7f",    "name":"OVERDRAFT_START_DATE",    "type":"DATE_WITH_DAY",    "value":"2012-04-23"  }]}
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
			|	GET $getObpApiRoot/v4.0.0/my/banks/BANK_ID/accounts/account-id-from-step-5/account
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
			|	POST $getObpApiRoot/v4.0.0/management/banks/BANK_ID/cards
			|
			|Body:
			|
      | {  "card_number":"364435172576215",  "card_type":"Credit",  "name_on_card":"SusanSmith",  "issue_number":"1",  "serial_number":"1324234",  "valid_from_date":"2017-09-19T00:00:00Z",  "expires_date":"2017-09-19T00:00:00Z",  "enabled":true,  "technology":"technology1",  "networks":["network1","network2"],  "allows":["credit","debit"],  "account_id":"account_id from step 5",  "replacement":{    "requested_date":"2017-09-19T00:00:00Z",    "reason_requested":"RENEW"  },  "pin_reset":[{    "requested_date":"2017-09-19T00:00:00Z",    "reason_requested":"FORGOT"  },{    "requested_date":"2020-01-18T16:39:23Z",    "reason_requested":"GOOD_SECURITY_PRACTICE"  }],  "collected":"2017-09-19T00:00:00Z",  "posted":"2017-09-19T00:00:00Z",  "customer_id":"customer_id from step 2"}
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
       | {  "name":"_test",  "description":"This view is for family",  "metadata_view":"_test",  "is_public":true,  "which_alias_to_use":"family",  "hide_metadata_if_alias_used":false,  "allowed_actions":[$CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT,$CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT,$CAN_SEE_TRANSACTION_METADATA,,$CAN_SEE_TRANSACTION_AMOUNT,$CAN_SEE_TRANSACTION_TYPE,$CAN_SEE_TRANSACTION_CURRENCY,$CAN_SEE_TRANSACTION_START_DATE,$CAN_SEE_TRANSACTION_FINISH_DATE,$CAN_SEE_TRANSACTION_BALANCE,$CAN_SEE_COMMENTS,$CAN_SEE_TAGS,$CAN_SEE_IMAGES,$CAN_SEE_BANK_ACCOUNT_OWNERS,$CAN_SEE_BANK_ACCOUNT_TYPE,$CAN_SEE_BANK_ACCOUNT_BALANCE,$CAN_SEE_BANK_ACCOUNT_CURRENCY,$CAN_SEE_BANK_ACCOUNT_LABEL,$CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER,$CAN_SEE_BANK_ACCOUNT_SWIFT_BIC,$CAN_SEE_BANK_ACCOUNT_IBAN,$CAN_SEE_BANK_ACCOUNT_NUMBER,$CAN_SEE_BANK_ACCOUNT_BANK_NAME,$CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER,$CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC,$CAN_SEE_OTHER_ACCOUNT_IBAN,$CAN_SEE_OTHER_ACCOUNT_BANK_NAME,$CAN_SEE_OTHER_ACCOUNT_NUMBER,$CAN_SEE_OTHER_ACCOUNT_METADATA,$CAN_SEE_OTHER_ACCOUNT_KIND,$CAN_SEE_MORE_INFO,$CAN_SEE_URL,$CAN_SEE_IMAGE_URL,$CAN_SEE_OPEN_CORPORATES_URL,$CAN_SEE_CORPORATE_LOCATION,$CAN_SEE_PHYSICAL_LOCATION,$CAN_SEE_PUBLIC_ALIAS,$CAN_SEE_PRIVATE_ALIAS,$CAN_ADD_MORE_INFO,$CAN_ADD_URL,$CAN_ADD_IMAGE_URL,$CAN_ADD_OPEN_CORPORATES_URL,$CAN_ADD_CORPORATE_LOCATION,$CAN_ADD_PHYSICAL_LOCATION,$CAN_ADD_PUBLIC_ALIAS,$CAN_ADD_PRIVATE_ALIAS,$CAN_DELETE_CORPORATE_LOCATION,$CAN_DELETE_PHYSICAL_LOCATION,$CAN_ADD_COMMENT,$CAN_DELETE_COMMENT,$CAN_ADD_TAG,$CAN_DELETE_TAG,$CAN_ADD_IMAGE,$CAN_DELETE_IMAGE,$CAN_ADD_WHERE_TAG,$CAN_SEE_WHERE_TAG,$CAN_DELETE_WHERE_TAG,$CAN_SEE_BANK_ROUTING_SCHEME,$CAN_SEE_BANK_ROUTING_ADDRESS,$CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME,$CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS,$CAN_SEE_OTHER_BANK_ROUTING_SCHEME,$CAN_SEE_OTHER_BANK_ROUTING_ADDRESS,$CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME,$CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS,$CAN_QUERY_AVAILABLE_FUNDS,$CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT,$CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT,$CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT,$CAN_CREATE_DIRECT_DEBIT,$CAN_CREATE_STANDING_ORDER]}			 |
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
			|	POST $getObpApiRoot/v4.0.0/banks/BANK_ID/accounts/account-id-from-account-creation/VIEW_ID/counterparties
			|
			|Body:
			|
      | {  "name":"CounterpartyName",  "description":"My landlord",  "other_account_routing_scheme":"accountNumber",  "other_account_routing_address":"7987987-2348987-234234",  "other_account_secondary_routing_scheme":"IBAN",  "other_account_secondary_routing_address":"DE89370400440532013000",  "other_bank_routing_scheme":"bankCode",  "other_bank_routing_address":"10",  "other_branch_routing_scheme":"branchNumber",  "other_branch_routing_address":"10010",  "is_beneficiary":true,  "bespoke":[{    "key":"englishName",    "value":"english Name"  }]}			|
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
			|	POST $getObpApiRoot/v4.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/SEPA/transaction-requests
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
			|	POST $getObpApiRoot/v4.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests
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
			|	POST $getObpApiRoot/v4.0.0/banks/BANK_ID/accounts/your-account-id-from-step-1/views
			|
			|Body:
			|
			|	{  "name":"_test", "description":"good", "is_public":false, "which_alias_to_use":"accountant", "hide_metadata_if_alias_used":false,  "allowed_actions": [$CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT,$CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT,$CAN_SEE_TRANSACTION_METADATA,,$CAN_SEE_TRANSACTION_AMOUNT,$CAN_SEE_TRANSACTION_TYPE,$CAN_SEE_TRANSACTION_CURRENCY,$CAN_SEE_TRANSACTION_START_DATE,$CAN_SEE_TRANSACTION_FINISH_DATE,$CAN_SEE_TRANSACTION_BALANCE,$CAN_SEE_COMMENTS,$CAN_SEE_TAGS,$CAN_SEE_IMAGES,$CAN_SEE_BANK_ACCOUNT_OWNERS,$CAN_SEE_BANK_ACCOUNT_TYPE,$CAN_SEE_BANK_ACCOUNT_BALANCE,$CAN_SEE_BANK_ACCOUNT_CURRENCY,$CAN_SEE_BANK_ACCOUNT_LABEL,$CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER,$CAN_SEE_BANK_ACCOUNT_SWIFT_BIC,$CAN_SEE_BANK_ACCOUNT_IBAN,$CAN_SEE_BANK_ACCOUNT_NUMBER,$CAN_SEE_BANK_ACCOUNT_BANK_NAME,$CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER,$CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC,$CAN_SEE_OTHER_ACCOUNT_IBAN,$CAN_SEE_OTHER_ACCOUNT_BANK_NAME,$CAN_SEE_OTHER_ACCOUNT_NUMBER,$CAN_SEE_OTHER_ACCOUNT_METADATA,$CAN_SEE_OTHER_ACCOUNT_KIND,$CAN_SEE_MORE_INFO,$CAN_SEE_URL,$CAN_SEE_IMAGE_URL,$CAN_SEE_OPEN_CORPORATES_URL,$CAN_SEE_CORPORATE_LOCATION,$CAN_SEE_PHYSICAL_LOCATION,$CAN_SEE_PUBLIC_ALIAS,$CAN_SEE_PRIVATE_ALIAS,$CAN_ADD_MORE_INFO,$CAN_ADD_URL,$CAN_ADD_IMAGE_URL,$CAN_ADD_OPEN_CORPORATES_URL,$CAN_ADD_CORPORATE_LOCATION,$CAN_ADD_PHYSICAL_LOCATION,$CAN_ADD_PUBLIC_ALIAS,$CAN_ADD_PRIVATE_ALIAS,$CAN_DELETE_CORPORATE_LOCATION,$CAN_DELETE_PHYSICAL_LOCATION,$CAN_ADD_COMMENT,$CAN_DELETE_COMMENT,$CAN_ADD_TAG,$CAN_DELETE_TAG,$CAN_ADD_IMAGE,$CAN_DELETE_IMAGE,$CAN_ADD_WHERE_TAG,$CAN_SEE_WHERE_TAG,$CAN_DELETE_WHERE_TAG,$CAN_SEE_BANK_ROUTING_SCHEME,$CAN_SEE_BANK_ROUTING_ADDRESS,$CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME,$CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS,$CAN_SEE_OTHER_BANK_ROUTING_SCHEME,$CAN_SEE_OTHER_BANK_ROUTING_ADDRESS,$CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME,$CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS,$CAN_QUERY_AVAILABLE_FUNDS,$CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT,$CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT,$CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT,$CAN_CREATE_DIRECT_DEBIT,$CAN_CREATE_STANDING_ORDER]}
			|
			| Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token"
			|
			|### 3) Get User (Current)
			|
			|Action:
			|
			|	GET $getObpApiRoot/v4.0.0/users/current
			|
			|
			| Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token"
			|
			|### 4) Grant user access to himself
			|
			|Action:
			|
			|	POST $getObpApiRoot/v4.0.0/banks/BANK_ID/accounts/your-account-id-from-step-1/account-access/grant
			|
			|Body:
			|
			|	{  "user_id":"your-user-id-from-step3",  "view":{    "view_id":"_test",    "is_system":false  }}
			|
			| Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token"
			|
			|### 5) Grant user access to view to another user
			|
			|Action:
			|
			|	POST $getObpApiRoot/v4.0.0/banks/BANK_ID/accounts/your-account-id-from-step-1/account-access/grant
			|
			|Body:
			|
			|	{  "user_id":"another-user-id",  "view":{    "view_id":"_test",    "is_system":false  }}
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
		title = "Scenario 5: Onboarding a User using Auth Context ",
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
			|### 2) Create User Auth Context
			|
			| These key value pairs will be propagated over connector to adapter and to bank. So the bank can use these key value paris
			| to map obp user to real bank customer.
			|
			|Action:
			|
			|	POST $getObpApiRoot/obp/v4.0.0/users/USER_ID/auth-context
			|
			|Body:
			|
			|	{  "key":"CUSTOMER_NUMBER",  "value":"78987432"}
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	Authorization: DirectLogin token="your-token-from-direct-login"
			|
			|### 3) Create customer
			|
			|Requires CanCreateCustomer or canCreateCustomerAtAnyBank roles
			|
			|Action:
			|
			|	POST $getObpApiRoot/v3.1.0/banks/BANK_ID/customers
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
			|### 4) Get Customers for Current User
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

		  """)

	glossaryItems += GlossaryItem(
		title = "Scenario 6: Update credit score based on transaction and device data.",
		description =
			s"""
			|### 1) Use Case
			|
			| As an App developer you want to give a Credit Rating to a Customer based on their Transactions and also device data.
			|
|### 2) Solution Overview:
|
|In general your application will need to:
|				1) Loop through Customers
|     	2) For each Customer, get its related Users and associated device data
|       3) For each Customer or User get the related accounts
|       4) For each Account, get its Transaction data
|       5) Update the Credit Rating and Credit Rating Readiness score of the Customer.
|
|### 3) Authentication and Authorisation
|
|Depending on the configuration of this OBP instance, and the endpoints being called, the Consumer / Client may need Scopes and / or the User may need Entitlements and Account Access.
|To get started, we suggest requesting Entitlements via the API Explorer.
|
|### 4) Endpoints
|
|* Get Customers (minimal). Click [here](/index?version=OBPv4.0.0&operation_id=OBPv4_0_0-getCustomersMinimalAtAnyBank&currentTag=Customer#OBPv4_0_0-getCustomersMinimalAtAnyBank) for documentation.
|
|The above endpoints return a list of bank_id and customer_id which can be used for getting correlated Users and their attributes:
|
|* Get Correlated Users for a Customer. Click [here](/index?version=OBPv4.0.0&operation_id=OBPv4_0_0-getCustomersMinimalAtAnyBank&currentTag=Customer#OBPv4_0_0-getCorrelatedUsersInfoByCustomerId) for documentation.
|
|Then get Accounts related to a Customer:
|
|* GET Accounts Minimal for a Customer. Click [here](/index?version=OBPv4.0.0&operation_id=OBPv4_0_0-getAccountsMinimalByCustomerId&currentTag=Account#OBPv4_0_0-getAccountsMinimalByCustomerId) for documentation.
|
|Once you have the list of bank_ids and account_ids, you can get their transactions which include tags for each transaction:
|
|* GET Firehose Transactions. Click [here](/index?version=OBPv4.0.0&operation_id=OBPv3_0_0-getFirehoseTransactionsForBankAccount&currentTag=Transaction#OBPv3_0_0-getFirehoseTransactionsForBankAccount) for documentation.
|
|After your processing of the data you can update the Credit Score:
|
|* Update Credit Score. Click [here](/index?version=OBPv4.0.0&operation_id=OBPv3_1_0-updateCustomerCreditRatingAndSource&currentTag=Customer#OBPv3_1_0-updateCustomerCreditRatingAndSource) for documentation.
|
|You can create a CREDIT_SCORE_READINESS attribute using the following endpoint:
|
|* Create Customer Attribute. Click [here](/index?version=OBPv4.0.0&operation_id=OBPv3_1_0-updateCustomerCreditRatingAndSource&currentTag=Customer#OBPv4_0_0-createCustomerAttribute) for documentation.
|
|And update it here:
|
|* Update Customer Attribute. Click [here](/index?version=OBPv4.0.0&operation_id=OBPv3_1_0-updateCustomerCreditRatingAndSource&currentTag=Customer#OBPv4_0_0-updateCustomerAttribute) for documentation.
|
|""")

	glossaryItems += GlossaryItem(
		title = "Scenario 7: Onboarding a User with multiple User Auth Context records",
		description =
			s"""
			|### 1) Assuming a User is registered.
			|
			|The User can authenticate using OAuth, OIDC, Direct Login etc.
      |
			|### 2) Create a first User Auth Context record e.g. ACCOUNT_NUMBER
			|
			| The setting of the first User Auth Context record for a User, typically involves sending an SMS to the User.
      | The phone number used for the SMS is retrieved from the bank's Core Banking System via an Account Number to Phone Number lookup.
			| If this step succeeds we can be reasonably confident that the User who initiated it has access to a SIM card that can use the Phone Number linked to the Bank Account on the Core Banking System.
			|
			|Action: Create User Auth Context Update Request
			|
			|	POST $getObpApiRoot/obp/v5.0.0/banks/BANK_ID/users/current/auth-context-updates/SMS
			|
			|Body:
			|
			|	{  "key":"ACCOUNT_NUMBER",  "value":"78987432"}
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	$directLoginHeaderName: token="your-token-from-direct-login"
			|
			| When customer get the the challenge answer from SMS, then need to call `Answer Auth Context Update Challenge` to varify the challenge.
			| Then the customer create the 1st `User Auth Context` successfully.
			|
			|
			|Action: Answer Auth Context Update Challenge
			|
			|	POST $getObpApiRoot/obp/v5.0.0/banks/BANK_ID/users/current/auth-context-updates/AUTH_CONTEXT_UPDATE_ID/challenge
			|
			|Body:
			|
			|	{  "answer": "12345678"}
			|
			|Headers:
			|
			|	Content-Type:  application/json
			|
			|	$directLoginHeaderName: token="your-token-from-direct-login"
			|
|### 3) Create a second User Auth Context record e.g. SMALL_PAYMENT_VERIFIED
|
| Once the first User Auth Context record is set, we can require the App to set a second record which builds on the information of the first.
|
|Action: Create User Auth Context Update Request
|
|	POST $getObpApiRoot/obp/v5.0.0/banks/BANK_ID/users/current/auth-context-updates/SMS
|
|Body:
|
|	{  "key":"SMALL_PAYMENT_VERIFIED",  "value":"78987432"}
|
|Headers:
|
|	Content-Type:  application/json
|
|	$directLoginHeaderName: token="your-token-from-direct-login"
|
|
|
|Following `Create User Auth Context Update Request` request the API will send a small payment with a random code from the Users bank account specified in the SMALL_PAYMENT_VERIFIED key value.
|
|In order to answer the challenge, the User must have access to the online banking statement (or some other App that already can read transactions in realtime) so they can read the code in the description of the payment.
|
|
|Then Action:Answer Auth Context Update Challenge
|
|	POST $getObpApiRoot/obp/v5.0.0/banks/BANK_ID/users/current/auth-context-updates/AUTH_CONTEXT_UPDATE_ID/challenge
|
|Body:
|
|	{  "answer": "12345678"}
|
|Headers:
|
|	Content-Type:  application/json
|
|	$directLoginHeaderName: token="your-token-from-direct-login"
|
| Note! The above logic must be encoded in a dynamic connector method for the OBP internal function validateUserAuthContextUpdateRequest which is used by the endpoint Create User Auth Context Update Request See the next step.
|
|### 4) Create or Update Connector Method for validateUserAuthContextUpdateRequest
|
| Using this endpoint you can modify the Scala logic
|
|Action:
|
|	POST $getObpApiRoot/obp/v4.0.0/management/connector-methods
|
|Body:
|
|	{  "method_name":"validateUserAuthContextUpdateRequest",  "method_body":"%20%20%20%20%20%20Future.successful%28%0A%20%20%20%20%20%20%20%20Full%28%28BankCommons%28%0A%20%20%20%20%20%20%20%20%20%20BankId%28%22Hello%20bank%20id%22%29%2C%0A%20%20%20%20%20%20%20%20%20%20%221%22%2C%0A%20%20%20%20%20%20%20%20%20%20%221%22%2C%0A%20%20%20%20%20%20%20%20%20%20%221%22%2C%0A%20%20%20%20%20%20%20%20%20%20%221%22%2C%0A%20%20%20%20%20%20%20%20%20%20%221%22%2C%0A%20%20%20%20%20%20%20%20%20%20%221%22%2C%0A%20%20%20%20%20%20%20%20%20%20%221%22%2C%0A%20%20%20%20%20%20%20%20%20%20%228%22%0A%20%20%20%20%20%20%20%20%29%2C%20None%29%29%0A%20%20%20%20%20%20%29"}
|
|Headers:
|
|	Content-Type:  application/json
|
|	$directLoginHeaderName: token="your-token-from-direct-login"
|
|### 5) Allow automated access to the App with Create Consent (SMS)
|
|
| Following the creation of User Auth Context records, OBP will create the relevant Account Access Views which allows the User to access their account(s).
| The App can then request an OBP consent which can be used as a bearer token and have automated access to the accounts.
| The Consent can be deleted at any time by the User.
|
| The Consent can have access to everything the User has access to, or a subset of this.
|
|Action:
|
|	POST $getObpApiRoot/obp/v4.0.0/banks/BANK_ID/my/consents/SMS
|
|Body:
|
|	{  "everything":false,  "views":[{    "bank_id":"gh.29.uk",    "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",    "view_id":${Constant.SYSTEM_OWNER_VIEW_ID}],  "entitlements":[{    "bank_id":"gh.29.uk",    "role_name":"CanGetCustomer"  }],  "consumer_id":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",  "phone_number":"+44 07972 444 876",  "valid_from":"2022-04-29T10:40:03Z",  "time_to_live":3600}
|
|Headers:
|
|	Content-Type:  application/json
|
|	$directLoginHeaderName: token="your-token-from-direct-login"
|
|![OBP User Auth Context, Views, Consents 2022](https://user-images.githubusercontent.com/485218/165982767-f656c965-089b-46de-a5e6-9f05b14db182.png)
|
|
		  """)


	glossaryItems += GlossaryItem(
		title = "KYC (Know Your Customer)",
		description =
			s"""
|KYC is the process by which the Bank can be assured that the customer is who they say they are.
|
|OBP provides a [number of endpoints](/index?ignoredefcat=true&tags=KYC) that KYC Apps can interact with in order to get and store relevant data and update the KYC status of a Customer.
|
|For instance:
|
|
 |1) Use KYC documents to add information about passports, ID cards, driving licenses including:
 |
|  * customer_number
|  * type (of document)
|  * number (of document)
|  * issue_date
|  * issue_place
|  * expiry_date
|
|
 |For more info see [here](/index?ignoredefcat=true&tags=KYC#OBPv2_0_0-addKycDocument).
|
 |
 |2) Use KYC check to record a check of the customer via phone call, in person meeting or PEP database search.
|
|KYC Checks store:
|
| * customer_number
| * date
| * how (FACE_TO_FACE_MEETING, PHONE_INTERVIEW, PEP_SEARCH)
| * staff_user_id (who recorded the record)
| * staff_name (who recorded the record)
| * satisfied (True/False)
| * comments
|
 |For more info see [here](/index?ignoredefcat=true&tags=KYC#OBPv2_0_0-addKycCheck).
|
 |3) Use KYC Media to add media (image or video etc.) related to:
|
 |A) the documents listed in KYC documents
|
 |B) media that identifies the user e.g. a video ident.
|
 |C) media that relates to a KYC check
|
 |
 |KYC Media stores:
 |
| * customer_number
| * type
| * url
| * date
| * relates_to_kyc_document_id
| * relates_to_kyc_check_id
|
|
 |For more information see [here](/index?ignoredefcat=true&tags=KYC#OBPv2_0_0-addKycMedia).
|
 |
 |4) Use KYC status to update the KYC status of a customer following a meeting or using one of the above calls.
|
|KYC status stores:
|
|  * customer_number
|  * ok (True/False)
|  * date
|
 |
 |For more information see [here](/index?ignoredefcat=true&tags=KYC#OBPv2_0_0-addKycStatus).
 |
 |
 |5) Use other Customer related endpoints shown [here](/index?ignoredefcat=true&tags=KYC) to check for known Addresses, contact details, Tax Residences etc.
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



  val oauth2EnabledMessage : String = if (APIUtil.getPropsAsBoolValue("allow_oauth2_login", false))
		{"OAuth2 is allowed on this instance."} else {"Note: *OAuth2 is NOT allowed on this instance!*"}

    glossaryItems += GlossaryItem(
      title = "OAuth 2",
      description =
        s"""
        |
        |$oauth2EnabledMessage
        |
        |OAuth2 is an authorization framework that enables applications to obtain limited access to user accounts on an HTTP service, in this case any OBP REST call. It works by delegating user authentication to the service that hosts the user account, and authorizing third-party applications to access the user account. OAuth 2 provides authorization flows for web and desktop applications, and mobile devices.
        |
        |### OAuth2 Roles
        |
        |The following is a general introduction to a so called "3 legged OAuth2" flow:
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
        |The authorization server verifies the identity of the user then issues access tokens to the application. E.g. Hydra
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
        |### Step 1: Get your App key
        |
        |[Sign up]($getServerUrl/user_mgt/sign_up) or [login]($getServerUrl/user_mgt/login) as a developer
        |
        |Register your App key [HERE]($getServerUrl/consumer-registration)
        |
        |Copy and paste the CLIENT ID (AKA CONSUMER KEY), CLIENT SECRET (AKA CONSUMER SECRET) and REDIRECT_URL for the subsequent steps below.
        |
        |
        |### Step 2: Initiate the OAuth 2.0 / OpenID Connect Flow
        |
        |Once you have registered your App you should initiate the OAuth2 / OIDC flow using the following URL
        |
        |${APIUtil.getHydraPublicServerUrl}/oauth2/auth
        |
        |WITH THE following parameters:
        |
        |${APIUtil.getHydraPublicServerUrl}/oauth2/auth?client_id=YOUR-CLIENT-ID&response_type=code&state=GENERATED_BY_YOUR_APP&scope=openid+offline+ReadAccountsBasic+ReadAccountsDetail+ReadBalances+ReadTransactionsBasic+ReadTransactionsDebits+ReadTransactionsDetail&redirect_uri=https%3A%2F%2FYOUR-APP.com%2Fmain.html
        |
        |### Step 3: Exchange the code for an access token
        |
        |The token endpoint is:
        |
        |${APIUtil.getHydraPublicServerUrl}/oauth2/token
        |
        |
        |For further information please see [here](https://www.ory.sh/hydra/docs/concepts/login#initiating-the-oauth-20--openid-connect-flow)
        |
        |In this sandbox, this will cause the following flow:
        |
        |1) The User is authenticated using OAuth2 / OpenID Connect against the banks authentication system
        |2) The User grants consent to the App on the bank's Consent page.
        |3) The User grants access to one or more accounts that they own on the bank's Account Selection page
        |4) The User is redirected back to the App where they can now see the Accounts they have selected.
        |
        |
        |
        |<img src="https://static.openbankproject.com/images/OBP-OAuth2-flow.png" width="885"></img>
        |
        |
        |
        |An example Consent Testing App (Hola) using this flow can be found [here](https://github.com/OpenBankProject/OBP-Hola)
        |
        |
        |
			""")






	glossaryItems += GlossaryItem(
		title = "OpenID Connect with Google",
		description =
			s"""
|
|$oauth2EnabledMessage
|
|## OpenID Connect with Google
|
 |### Introduction
|Google's OAuth 2.0 APIs can be used for both authentication and authorization. This document describes our OAuth 2.0 implementation for authentication, which conforms to the OpenID Connect specification, and is OpenID Certified.
|For complete documentation please refer to the official doc's page: [OpenID Connect](https://developers.google.com/identity/protocols/OpenIDConnect)
|
|<img width="1000" alt="OpenID Connect with Google Image" src="https://user-images.githubusercontent.com/29032407/51373848-76967580-1b01-11e9-9c9d-799c0c42f98b.png"></img>
|
 |### Obtain OAuth 2.0 credentials
|Please refer to the official doc's page: [OpenID Connect](https://developers.google.com/identity/protocols/OpenIDConnect)
|In case you use Google's [OAuth 2.0 Playground](https://developers.google.com/oauthplayground/)
|example of an response is shown below:
|{
|  "access_token": "ya29.a0Adw1xeVr_WAYaipiH_6QKCFjIFsnZxW7kbxA8a2RU_uy5meEufErwPDLSHMga8IEQghNSX2GbkOfZUQb6j_fMGHL_HaW3RoULZq5AayUdEjI9bC4TMe-Nd4cZR17C0Rg3GLNzuHTXXe05UyMmNODZ6Up0aXZBBTHl-4",
|  "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImE1NDFkNmVmMDIyZDc3YTIzMThmN2RkNjU3ZjI3NzkzMjAzYmVkNGEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJlbWFpbCI6Im1hcmtvLm1pbGljLnNyYmlqYUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6ImtrcENIWUFaSTZVOFZiZEJsRHNfX1EiLCJuYW1lIjoiTWFya28gTWlsacSHIiwicGljdHVyZSI6Imh0dHBzOi8vbGg1Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tWGQ0NGhuSjZURG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFBQUEvQUtGMDVuQ1pyaTdmWHdkUUhuZUNwN09pTVh1WGlOMkpVQS9zOTYtYy9waG90by5qcGciLCJnaXZlbl9uYW1lIjoiTWFya28iLCJmYW1pbHlfbmFtZSI6Ik1pbGnEhyIsImxvY2FsZSI6ImVuIiwiaWF0IjoxNTg0NTIxNDU3LCJleHAiOjE1ODQ1MjUwNTd9.LgwY-OhltYS2p91l2Lt4u5lUR5blR7L8097J0ZpK0GyxWxOlnhSouk9MRMmyfSGuYfWKBtdSUy3Esaphk2f7wpLS-wBx3KJpvrXhgbsyemt9s7eu5bAdHaCteO8MqHPjbU9tych8iH0tA1MSL_tVZ73hy56rS2irzIC33wYDoBf8C5nEOd2uzQ758ydK5QvvdFwRgkLhKDS8vq2qVJTWgtk9VVd5JwJ5OfiVimXfGUzNJmGreEJKj14iUj-78REybpUbI9mGevRhjLPhs51Uc9j-SsdRMymVbVhVxlbsWAPTpjLAJnOodeHzAvmKFkOUfahQHHctx4fl8V3PVYf1aA",
|  "expires_in": 3599,
|  "token_type": "Bearer",
|  "scope": "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email openid",
|  "refresh_token": "1//04w7RCdl9ZnG-CgYIARAAGAQSNwF-L9IrNZVxs6fliP7xAlHjKcZpfpw7JoYyBsvxKMD7n0xyB74G8aRlFoBkkCbloETrWMU6yOA"
|}
|Note: The OAuth Playground will automatically revoke refresh tokens after 24h. You can avoid this by specifying your own application OAuth credentials using the Configuration panel.
|
 |### An ID token's payload
|
 |
|		{
|		"iss": "https://accounts.google.com",
|		"azp": "407408718192.apps.googleusercontent.com",
|		"aud": "407408718192.apps.googleusercontent.com",
|		"sub": "113966854245780892959",
|		"email": "marko.milic.srbija@gmail.com",
|		"email_verified": true,
|		"at_hash": "nGKRToKNnVA28H6MhwXBxw",
|		"name": "Marko Milić",
|		"picture": "https://lh5.googleusercontent.com/-Xd44hnJ6TDo/AAAAAAAAAAI/AAAAAAAAAAA/AKxrwcadwzhm4N4tWk5E8Avxi-ZK6ks4qg/s96-c/photo.jpg",
|		"given_name": "Marko",
|		"family_name": "Milić",
|		$PARAM_LOCALE: "en",
|		"iat": 1547705691,
|		"exp": 1547709291
|		}
|
|
 |### Try a REST call using the authorization's header
|		Using your favorite http client:
|
 |		GET /obp/v3.0.0/users/current
|
 |Body
|
 |Leave Empty!
|
 |Headers:
|
 |
|		Authorization: Bearer ID_TOKEN
|
|
 |Here is it all together:
|
 |
|
 |	GET /obp/v3.0.0/users/current HTTP/1.1
|		Host: $getServerUrl
|		Authorization: Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IjA4ZDMyNDVjNjJmODZiNjM2MmFmY2JiZmZlMWQwNjk4MjZkZDFkYzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJlbWFpbCI6Im1hcmtvLm1pbGljLnNyYmlqYUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6IkFvYVNGQTlVTTdCSGg3YWZYNGp2TmciLCJuYW1lIjoiTWFya28gTWlsacSHIiwicGljdHVyZSI6Imh0dHBzOi8vbGg1Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tWGQ0NGhuSjZURG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFBQUEvQUt4cndjYWR3emhtNE40dFdrNUU4QXZ4aS1aSzZrczRxZy9zOTYtYy9waG90by5qcGciLCJnaXZlbl9uYW1lIjoiTWFya28iLCJmYW1pbHlfbmFtZSI6Ik1pbGnEhyIsImxvY2FsZSI6ImVuIiwiaWF0IjoxNTQ3NzExMTE1LCJleHAiOjE1NDc3MTQ3MTV9.MKsyecCSKS4Y0C8R4JP0J0d2Oa-xahvMAbtfFrGHncTm8xBgeaNb50XSJn20ak1YyA8hZiRP2M3el0f4eIVQZsMMa22MrwaiL8pLb1zGfawDLPb1RvOmoCWTDJGc_s1qQMlyc21Wenr9rjuu1bQCerGTYM6M0Aq-Uu_GT0lCEjz5WVDI5xDUf4Mhdi8HYq7UQ1kGz1gQFiBm5nI3_xtYm75EfXFeDg3TejaMmy36NpgtwN_vwpHByoHE5BoTl2J55rJ2creZZ7CmtZttm-9HsT6v1vxT8zi0RXObFrZSk-LgfF0tJQcGZ5LXQZL0yMKXPQVFIMCg8J0Gg7l_QACkCA
|		Cache-Control: no-cache
|
 |
|
 |CURL example:
|
 |
|		curl -X GET
|		$getServerUrl/obp/v3.0.0/users/current
|		-H 'Authorization: Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IjA4ZDMyNDVjNjJmODZiNjM2MmFmY2JiZmZlMWQwNjk4MjZkZDFkYzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJlbWFpbCI6Im1hcmtvLm1pbGljLnNyYmlqYUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6IkFvYVNGQTlVTTdCSGg3YWZYNGp2TmciLCJuYW1lIjoiTWFya28gTWlsacSHIiwicGljdHVyZSI6Imh0dHBzOi8vbGg1Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tWGQ0NGhuSjZURG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFBQUEvQUt4cndjYWR3emhtNE40dFdrNUU4QXZ4aS1aSzZrczRxZy9zOTYtYy9waG90by5qcGciLCJnaXZlbl9uYW1lIjoiTWFya28iLCJmYW1pbHlfbmFtZSI6Ik1pbGnEhyIsImxvY2FsZSI6ImVuIiwiaWF0IjoxNTQ3NzExMTE1LCJleHAiOjE1NDc3MTQ3MTV9.MKsyecCSKS4Y0C8R4JP0J0d2Oa-xahvMAbtfFrGHncTm8xBgeaNb50XSJn20ak1YyA8hZiRP2M3el0f4eIVQZsMMa22MrwaiL8pLb1zGfawDLPb1RvOmoCWTDJGc_s1qQMlyc21Wenr9rjuu1bQCerGTYM6M0Aq-Uu_GT0lCEjz5WVDI5xDUf4Mhdi8HYq7UQ1kGz1gQFiBm5nI3_xtYm75EfXFeDg3TejaMmy36NpgtwN_vwpHByoHE5BoTl2J55rJ2creZZ7CmtZttm-9HsT6v1vxT8zi0RXObFrZSk-LgfF0tJQcGZ5LXQZL0yMKXPQVFIMCg8J0Gg7l_QACkCA'
|		-H 'Cache-Control: no-cache'
|		-H 'Postman-Token: aa812d04-eddd-4752-adb7-4d56b3a98f36'
|
 |
|
 |And we get the response:
|
 |
|		{
|			"user_id": "6d411bce-50c1-4eb8-b8b0-3953e4211773",
|			"email": "marko.milic.srbija@gmail.com",
|			"provider_id": "113966854245780892959",
|			"provider": "https://accounts.google.com",
|			"username": "Marko Milić",
|			"entitlements": {
|			"list": []
|		}
|		}
|
|
|""")




	val gatewayLoginEnabledMessage : String = if (APIUtil.getPropsAsBoolValue("allow_gateway_login", false))
	{"Note: Gateway Login is enabled."} else {"Note: *Gateway Login is NOT enabled on this instance!*"}


	glossaryItems += GlossaryItem(
		title = "Gateway Login",
		description =
			s"""
						 |### Introduction
|
|$gatewayLoginEnabledMessage
|
|Gateway Login Authorisation is made by including a specific header (see step 3 below) in any OBP REST call.
|
|Note: Gateway Login does *not* require an explicit POST like Direct Login to create the token.
|
|The **Gateway is responsible** for creating a token which is trusted by OBP **absolutely**!
|
|When OBP recieves a token via Gateway Login, OBP creates or gets a user based on the username supplied.
|
|![obp login via gateway and jwt](https://user-images.githubusercontent.com/485218/32783397-e39620ee-c94b-11e7-92e3-b244b8e841dd.png)
|
|
|To use Gateway Login:
|
|### 1) Configure OBP API to accept Gateway Login.
|
|Set up properties in a props file
|
|```
|# -- Gateway login --------------------------------------
|# Enable/Disable Gateway communication at all
|# In case isn't defined default value is false
|# allow_gateway_login=false
|# Define comma separated list of allowed IP addresses
|# gateway.host=127.0.0.1
|# Define secret used to validate JWT token
|# jwt_token_secret=your-at-least-256-bit-secret-token
|# -------------------------------------- Gateway login --
|```
|Please keep in mind that property jwt_token_secret is used to validate JWT token to check it is not changed or corrupted during transport.
|
|### 2) Create / have access to a JWT
|
|
|
|HEADER:ALGORITHM & TOKEN TYPE
|
|```
|{
|  "alg": "HS256",
|  "typ": "JWT"
|}
|```
|PAYLOAD:DATA
|
|```
|{
|    "login_user_name": "username",
|    "is_first": false,
|    "app_id": "85a965f0-0d55-4e0a-8b1c-649c4b01c4fb",
|    "app_name": "GWL",
|    "time_stamp": "2018-08-20T14:13:40Z",
|    "cbs_token": "your_token",
|    "cbs_id": "your_cbs_id",
|    "session_id": "123456789"
|}
|```
|VERIFY SIGNATURE
|```
|HMACSHA256(
|  base64UrlEncode(header) + "." +
|  base64UrlEncode(payload),
|
|) your-at-least-256-bit-secret-token
|```
|
|Here is the above example token:
|
|```
|eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
|eyJsb2dpbl91c2VyX25hbWUiOiJ1c2VybmFtZSIsImlzX2ZpcnN0IjpmYWxzZSwiYXBwX2lkIjoiODVhOTY1ZjAtMGQ1NS00ZTBhLThiMWMtNjQ5YzRiMDFjNGZiIiwiYXBwX25hbWUiOiJHV0wiLCJ0aW1lX3N0YW1wIjoiMjAxOC0wOC0yMFQxNDoxMzo0MFoiLCJjYnNfdG9rZW4iOiJ5b3VyX3Rva2VuIiwiY2JzX2lkIjoieW91cl9jYnNfaWQiLCJzZXNzaW9uX2lkIjoiMTIzNDU2Nzg5In0.
|bfWGWttEEcftiqrb71mE6Xy1tT_I-gmDPgjzvn6kC_k
|```
|
|
|
|### 3) Try a REST call using the header
|
|
|Using your favorite http client:
|
|  GET $getServerUrl/obp/v3.0.0/users/current
|
|Body
|
|  Leave Empty!
|
|
|Headers:
|
|       Authorization: GatewayLogin token="your-jwt-from-step-above"
|
|Here is it all together:
|
|  GET $getServerUrl/obp/v3.0.0/users/current HTTP/1.1
|        Host: localhost:8080
|        User-Agent: curl/7.47.0
|        Accept: */*
|        Authorization: GatewayLogin token="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
|AS8D76F7A89S87D6F7A9SD876FA789SD78F6A7S9D78F6AS79DF87A6S7D9F7A6S7D9F78A6SD798F78679D786S789D78F6A7S9D78F6AS79DF876A7S89DF786AS9D87F69AS7D6FN1bWVyIn0.
|KEuvjv3dmwkOhQ3JJ6dIShK8CG_fd2REApOGn1TRmgU"
|
|CURL example
|
|```
|curl -v -H 'Authorization: GatewayLogin token="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
|eyJsb2dpbl91c2VyX25hbWUiOiJ1c2VybmFtZSIsImlzX2ZpcnN0IjpmYWxzZSwiYXBwX2lkIjoiODVhOTY1ZjAtMGQ1NS00ZTBhLThiMWMtNjQ5YzRiMDFjNGZiIiwiYXBwX25hbWUiOiJHV0wiLCJ0aW1lX3N0YW1wIjoiMjAxOC0wOC0yMFQxNDoxMzo0MFoiLCJjYnNfdG9rZW4iOiJ5b3VyX3Rva2VuIiwiY2JzX2lkIjoieW91cl9jYnNfaWQiLCJzZXNzaW9uX2lkIjoiMTIzNDU2Nzg5In0.
|bfWGWttEEcftiqrb71mE6Xy1tT_I-gmDPgjzvn6kC_k"' $getServerUrl/obp/v3.0.0/users/current
|```
|
|
|You should receive a response like:
|
|```
|{
|  "user_id": "33fd104f-3e6f-4025-97cc-b76bbdc9148e",
|  "email": "marko@tesobe.com",
|  "provider_id": "marko.milic",
|  "provider": "https://tesobe.openbankproject.com",
|  "username": "marko.milic",
|  "entitlements": {
|    "list": []
|  }
|}
|```
|and custom response header i.e. OBP returns a new token in the custom response header called GatewayLogin (to the Gateway)
|
|```
|{
|"username": "simonr",
|"CBS_auth_token": "fapsoidfuipoi889w3ih", (Encrypted by OBP Adapter)
|"timestamp": "timestamp",
|"consumer_id": "123",
|"consumer_name": "Name of Consumer"
|}
|```
|GatewayLogin token="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
|AS8D76F7A89S87D6F7A9SD876FA789SD78F6A7S9D78F6AS79DF87A6S7D9F7A6S7D9F78A6SD798F78679D786S789D78F6A7S9D78F6AS79DF876A7S89DF786AS9D87F69AS7D6FN1bWVyIn0.
|KEuvjv3dmwkOhQ3JJ6dIShK8CG_fd2REApOGn1TRmgU"
|
|### Example python script
|```
|import jwt
|from datetime import datetime, timezone
|import requests
|
|env = 'local'
|DATE_FORMAT = '%Y-%m-%dT%H:%M:%SZ'
|
|obp_api_host = 'https://yourhost.com'
|payload = {
|    "login_user_name": "username",
|    "is_first": False,
|    "app_id": "85a965f0-0d55-4e0a-8b1c-649c4b01c4fb",
|    "app_name": "Name",
|    "time_stamp": datetime.now(timezone.utc).strftime(DATE_FORMAT),
|    "cbs_token": "yourtokenforcbs",
|    "cbs_id": "yourcbs_id",
|    "session_id": "123456789"
|}
|
|
|token = jwt.encode(payload, 'your-at-least-256-bit-secret-token', algorithm='HS256').decode("utf-8")
|authorization = 'GatewayLogin token="{}"'.format(token)
|headers = {'Authorization': authorization}
|url = obp_api_host + '/obp/v4.0.0/users/current'
|req = requests.get(url, headers=headers)
|print(req.text)
|```
|
|### Under the hood
|
|The file, GatewayLogin.scala handles the Gateway Login.
|
|We:
|
|```
|-> Check if Props allow_gateway_login is true
|  -> Check if GatewayLogin header exists
|    -> Check if getRemoteIpAddress is OK
|      -> Look for "token"
|        -> If "is_first" is true -OR- CBS_auth_token is empty then, call CBS to get accounts
|```
|
|The CBS_auth_token (either the new one from CBS or existing one from previous token) is returned in the GatewayLogin custom response header.
|
|
|
|### More information
|
|   Parameter names and values are case sensitive.
|
|
|  Each parameter MUST NOT appear more than once per request.
|
					""")


	val dauthEnabledMessage : String = if (APIUtil.getPropsAsBoolValue("allow_dauth", false))
	{"Note: DAuth is enabled."} else {"Note: *DAuth is NOT enabled on this instance!*"}


	glossaryItems += GlossaryItem(
		title = APIUtil.DAuthHeaderKey,
		description =
			s"""
						 |### DAuth Introduction, Setup and Usage
|
|
|DAuth is an experimental authentication mechanism that aims to pin an ethereum or other blockchain Smart Contract to an OBP "User".
|
|In the future, it might be possible to be more specific and pin specific actors (wallets) that are acting within the smart contract, but so far, one smart contract acts on behalf of one User.
|
|Thus, if a smart contract "X" calls the OBP API using the DAuth header, OBP will get or create a user called X and the call will proceed in the context of that User "X".
|
|
|DAuth is invoked by the REST client (caller) including a specific header (see step 3 below) in any OBP REST call.
|
|When OBP receives the DAuth token, it creates or gets a User with a username based on the smart_contract_address and the provider based on the network_name. The combination of username and provider is unique in OBP.
|
|If you are calling OBP-API via an API3 Airnode, the Airnode will take care of constructing the required header.
|
|When OBP detects a DAuth header / token it first checks if the Consumer is allowed to make such a call. OBP will validate the Consumer ip address and signature etc.
|
|Note: The DAuth flow does *not* require an explicit POST like Direct Login to create the token.
|
|Permissions may be assigned to an OBP User at any time, via the UserAuthContext, Views, Entitlements to Roles or Consents.
|
|$dauthEnabledMessage
|
|Note: *The DAuth client is responsible for creating a token which will be trusted by OBP absolutely*!
|
|
|To use DAuth:
|
|### 1) Configure OBP API to accept DAuth.
|
|Set up properties in your props file
|
|```
|# -- DAuth --------------------------------------
|# Define secret used to validate JWT token
|# jwt.public_key_rsa=path-to-the-pem-file
|# Enable/Disable DAuth communication at all
|# In case isn't defined default value is false
|# allow_dauth=false
|# Define comma separated list of allowed IP addresses
|# dauth.host=127.0.0.1
|# -------------------------------------- DAuth--
|```
|Please keep in mind that property jwt.public_key_rsa is used to validate JWT token to check it is not changed or corrupted during transport.
|
|### 2) Create / have access to a JWT
|
|The following videos are available:
|	* [DAuth in local environment](https://vimeo.com/644315074)
|
|HEADER:ALGORITHM & TOKEN TYPE
|
|```
|{
|  "alg": "RS256",
|  "typ": "JWT"
|}
|```
|PAYLOAD:DATA
|
|```
|{
|  "smart_contract_address": "0xe123425E7734CE288F8367e1Bb143E90bb3F051224",
|  "network_name": "AIRNODE.TESTNET.ETHEREUM",
|  "msg_sender": "0xe12340927f1725E7734CE288F8367e1Bb143E90fhku767",
|  "consumer_key": "0x1234a4ec31e89cea54d1f125db7536e874ab4a96b4d4f6438668b6bb10a6adb",
|  "timestamp": "2021-11-04T14:13:40Z",
|  "request_id": "0Xe876987694328763492876348928736497869273649"
|}
|```
|VERIFY SIGNATURE
|```
|RSASHA256(
|  base64UrlEncode(header) + "." +
|  base64UrlEncode(payload),
|
|) your-RSA-key-pair
|```
|
|Here is an example token:
|
|```
|eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzbWFydF9jb250cmFjdF9hZGRyZXNzIjoiMHhlMTIzNDI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGJiM0YwNTEyMjQiLCJuZXR3b3JrX25hbWUiOiJFVEhFUkVVTSIsIm1zZ19zZW5kZXIiOiIweGUxMjM0MDkyN2YxNzI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGZoa3U3NjciLCJjb25zdW1lcl9rZXkiOiIweDEyMzRhNGVjMzFlODljZWE1NGQxZjEyNWRiNzUzNmU4NzRhYjRhOTZiNGQ0ZjY0Mzg2NjhiNmJiMTBhNmFkYiIsInRpbWVzdGFtcCI6IjIwMjEtMTEtMDRUMTQ6MTM6NDBaIiwicmVxdWVzdF9pZCI6IjBYZTg3Njk4NzY5NDMyODc2MzQ5Mjg3NjM0ODkyODczNjQ5Nzg2OTI3MzY0OSJ9.XSiQxjEVyCouf7zT8MubEKsbOBZuReGVhnt9uck6z6k
|```
|
|
|
|### 3) Try a REST call using the header
|
|
|Using your favorite http client:
|
|  GET $getServerUrl/obp/v3.0.0/users/current
|
|Body
|
|  Leave Empty!
|
|
|Headers:
|
|       DAuth: your-jwt-from-step-above
|
|Here is it all together:
|
|  GET $getServerUrl/obp/v3.0.0/users/current HTTP/1.1
|        Host: localhost:8080
|        User-Agent: curl/7.47.0
|        Accept: */*
|        DAuth: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzbWFydF9jb250cmFjdF9hZGRyZXNzIjoiMHhlMTIzNDI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGJiM0YwNTEyMjQiLCJuZXR3b3JrX25hbWUiOiJFVEhFUkVVTSIsIm1zZ19zZW5kZXIiOiIweGUxMjM0MDkyN2YxNzI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGZoa3U3NjciLCJjb25zdW1lcl9rZXkiOiIweDEyMzRhNGVjMzFlODljZWE1NGQxZjEyNWRiNzUzNmU4NzRhYjRhOTZiNGQ0ZjY0Mzg2NjhiNmJiMTBhNmFkYiIsInRpbWVzdGFtcCI6IjIwMjEtMTEtMDRUMTQ6MTM6NDBaIiwicmVxdWVzdF9pZCI6IjBYZTg3Njk4NzY5NDMyODc2MzQ5Mjg3NjM0ODkyODczNjQ5Nzg2OTI3MzY0OSJ9.XSiQxjEVyCouf7zT8MubEKsbOBZuReGVhnt9uck6z6k
|
|CURL example
|
|```
|curl -v -H 'DAuth: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzbWFydF9jb250cmFjdF9hZGRyZXNzIjoiMHhlMTIzNDI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGJiM0YwNTEyMjQiLCJuZXR3b3JrX25hbWUiOiJFVEhFUkVVTSIsIm1zZ19zZW5kZXIiOiIweGUxMjM0MDkyN2YxNzI1RTc3MzRDRTI4OEY4MzY3ZTFCYjE0M0U5MGZoa3U3NjciLCJjb25zdW1lcl9rZXkiOiIweDEyMzRhNGVjMzFlODljZWE1NGQxZjEyNWRiNzUzNmU4NzRhYjRhOTZiNGQ0ZjY0Mzg2NjhiNmJiMTBhNmFkYiIsInRpbWVzdGFtcCI6IjIwMjEtMTEtMDRUMTQ6MTM6NDBaIiwicmVxdWVzdF9pZCI6IjBYZTg3Njk4NzY5NDMyODc2MzQ5Mjg3NjM0ODkyODczNjQ5Nzg2OTI3MzY0OSJ9.XSiQxjEVyCouf7zT8MubEKsbOBZuReGVhnt9uck6z6k' $getServerUrl/obp/v3.0.0/users/current
|```
|
|
|You should receive a response like:
|
|```
|{
|    "user_id": "4c4d3175-1e5c-4cfd-9b08-dcdc209d8221",
|    "email": "",
|    "provider_id": "0xe123425E7734CE288F8367e1Bb143E90bb3F051224",
|    "provider": "ETHEREUM",
|    "username": "0xe123425E7734CE288F8367e1Bb143E90bb3F051224",
|    "entitlements": {
|        "list": []
|    }
|}
|```
|
|### Under the hood
|
|The file, dauth.scala handles the DAuth,
|
|We:
|
|```
|-> Check if Props allow_dauth is true
|  -> Check if DAuth header exists
|    -> Check if getRemoteIpAddress is OK
|      -> Look for "token"
|        -> parse the JWT token and getOrCreate the user
|          -> get the data of the user
|```
|
|### More information
|
|  Parameter names and values are case sensitive.
|  Each parameter MUST NOT appear more than once per request.
|
					""")



	glossaryItems += GlossaryItem(
		title = "SCA (Strong Customer Authentication)",
		description =
			s"""|
|SCA is the process by which a Customer of the Bank securely identifies him/her self to the Bank.
|
|Generally this involves using an Out Of Band (OOB) form of communication e.g. a One Time Password (OTP) / code sent to a mobile phone.
|
|In the OBP APIs, SCA is invoked during Transaction Requests and Consent creation.
|
|See the following endpoints:
|
|[Create Consent via SMS](/index#OBPv3_1_0-createConsentSms)
|[Create Consent via Email (for testing purposes)](/index#OBPv3_1_0-createConsentEmail)
|[Answer Consent Challenge](/index#OBPv3_1_0-answerConsentChallenge)
|
|[Create Transaction Request](/index#OBPv2_1_0-createTransactionRequestSandboxTan)
|[Answer Transaction Request Challenge](/index#OBPv2_1_0-answerTransactionRequestChallenge)
|
|Possible SCA flows:
|
|<img width="468" alt="obp-sca-image-1" src="https://user-images.githubusercontent.com/485218/58027906-ed786500-7b19-11e9-817e-c02e53ef9bd2.png"></img>
|
|
|
""")



	glossaryItems += GlossaryItem(
		title = "Dummy Customer Logins",
		description =
			s"""|
|The following dummy Customer Logins may be used by developers testing their applications on this sandbox:
|
|${getWebUiPropsValue("webui_dummy_user_logins", "")}
|
|
|
|${scala.xml.Unparsed(getWebUiPropsValue("webui_api_documentation_url", "") + "#customer-logins")}
|
|
|
|
|
|
""")

  glossaryItems += GlossaryItem(
    title = "Sandbox Introduction",
    description =
      s"""
          ${getWebUiPropsValue("webui_sandbox_introduction", "")}
 """)


	glossaryItems += GlossaryItem(
		title = "Data Model Overview",
		description =
			s"""
|
|An overview of the Open Bank Project Data Model.
|
|This diagram may help in understanding the Open Bank Project entities, capabilities and concepts. However, it is subject to change. If we change the data model, we release migration scripts.
|
|
|<img width="468" alt="obp-data-model-overview" src="https://user-images.githubusercontent.com/485218/63519307-04e93480-c4f3-11e9-8bfc-b64096d2f034.png"></img>
|
|
|
 """)
	glossaryItems += GlossaryItem(
		title = "Qualified Certificate Profiles (PSD2 context)",
		description =
			s"""
				 |An overview of the Qualified Certificate Profiles.
				 |
				 |<img width="700" alt="qualified-certificate-profiles"  src="$getServerUrl/media/images/glossary/Qualified_Certificate_Profiles.png"></img>
				 |
				 | """.stripMargin)

	glossaryItems += GlossaryItem(
		title = "Consumer, Consent, Transport and Payload Security",
		description =
			s"""
|
|Consumer, Consent, Transport and Payload Security with MTLS and JWS
				 |This glossary item aims to give an overview of how the communication between an Application and the OBP API server is secured with Consents, Consumer records, MTLs and JWS.
					|
					|It includes some implementation step notes for the Application developer.
				|
|The following components are required:
|
|## Consumer record
|
|The Application must have an active API Consumer / Client record on the server.
|
|## MTLS
|
|With Mutual TLS both the Consumer and the Server (OBP API) must use certificates.
|
|## JWS
|
|The Request is signed by the Consumer with a JWS using the client certificate of the Consumer. Example: [OBP-Hola private void requestIntercept](https://github.com/OpenBankProject/OBP-Hydra-OAuth2/blob/40359cf569a814c1aec4ce593303b39ddf9bdded/src/main/java/com/openbankproject/hydra/auth/RestTemplateConfig.java#L106)
|The Request is verified by the OBP API Server using the JWS provided by the Consumer. See [OBP-API def verifySignedRequest](https://github.com/OpenBankProject/OBP-API/blob/752044a35ca73ea4d3563c6ced57ee80903b6d30/obp-api/src/main/scala/code/api/util/JwsUtil.scala#L121)
|The Response is signed by the OBP API Server with a JWS. See [OBP-API def signResponse](https://github.com/OpenBankProject/OBP-API/blob/752044a35ca73ea4d3563c6ced57ee80903b6d30/obp-api/src/main/scala/code/api/util/JwsUtil.scala#L233)
|The Response is verified by the Client using the JWS provided by the OBP API Server. Example: [OBP-Hola private void responseIntercept](https://github.com/OpenBankProject/OBP-Hydra-OAuth2/blob/c2e4589ad7e6e6b156b54e535bdcd93638317ff7/src/main/java/com/openbankproject/hydra/auth/RestTemplateConfig.java#L121)
|
|
|## Consent
|
|The end user must give permission to the Application in order for the Application to see his/her account and transaction data.
|
|<img width="468" alt="obp-permission-transport-and-payload-security" src="https://user-images.githubusercontent.com/485218/114748431-38c13f80-9d52-11eb-9e54-50633a0ee601.png"></img>
|
|## In order to get an App / Consumer key
|
|[Sign up]($getServerUrl/user_mgt/sign_up) or [login]($getServerUrl/user_mgt/login) as a developer.
|
|Register your App / Consumer [HERE]($getServerUrl/consumer-registration)
|
|Be sure to enter your Client Certificate in the above form. To create the user.crt file see [HERE](https://fardog.io/blog/2017/12/30/client-side-certificate-authentication-with-nginx/)
|
|
|## Authenticate
|
|To test the service your App will need to authenticate using OAuth2.
|
|You can use the [OBP Hola App](https://github.com/OpenBankProject/OBP-Hydra-OAuth2) as an example / starting point for your App.
|
 """)


// TODO put the following wiki text here in source code with soft coded hosts etc. The problem is the text is currently too long
	glossaryItems += GlossaryItem(
		title = "Hola App log trace",
		description =
			s"""
       Please see:
				 [OBP Hola App Log Trace](https://github.com/OpenBankProject/OBP-API/wiki/Log-trace-of-the-Hola-App-performing-Georgian-flavour-of-Berlin-Group-authentication,-consent-generation-and-consuming-Berlin-Group-Account,-Balance-and-Transaction-resources)
 """)


	glossaryItems += GlossaryItem(
		title = "API Collection",
		description = s"""An API Collection is a collection of endpoints grouped together for a certain purpose.
|
|Having read access to a Collection does not constitute execute access on the endpoints in the Collection.
|
|(Execute access is governed by Entitlements to Roles - and in some cases, Views.)
|
|Collections can be created and shared. You can make a collection non-sharable but the default is sharable.
|
|Your "Favourites" in API Explorer is actually a collection you control named "Favourites".
|
|To share a Collection (e.g. your Favourites) just click on your Favourites in the API Explorer and share the URL in the browser. If you want to share the Collection via an API, just share the collection_id with a developer.
|
|If you share a Collection it can't be modified by anyone else, but anyone can use it as a basis for their own Favourites or another collection.
|
|There are over 13 endpoints for controlling Collections.
|Some of these endpoints require Entitlements to Roles and some operate on your own personal collections such as your favourites.
|
 """)

	glossaryItems += GlossaryItem(
		title = "Space",
		description =
			s"""In OBP, if you have access to a "Space", you have access to a set of Dynamic Endpoints and Dynamic Entities that belong to that Space.
|Internally, Spaces are defined as a "Banks" thus Spaces are synonymous with OBP Banks.
|
|A user can have access to several spaces. The API Explorer shows these under the Spaces menu.
|
|In order to see the documentation for the Dynamic Endpoints and Dynamic Entities, a user may need to have access to the CanReadDynamicResourceDocsAtOneBank Role.
|
|You can create your own Space by creating an OBP Bank.
|
""".stripMargin)


	glossaryItems += GlossaryItem(
		title = "Dynamic Entity Manage",
		description =
			s"""
|
|Dynamic Entities can be used to store and retrieve custom data objects (think your own tables and fields) in the OBP instance.
|
|You can define your own Dynamic Entities or use Dynamic Entities created by others.
|
|You would use Dynamic Entities if you want to go beyond the OBP standard data model and store custom data structures. Note, if you want to extend the core OBP banking model of Customers, Products, Accounts, Transactions and so on you can also add Custom Attributes to these standard objects.
|
|You would use Dynamic Endpoints if you want to go beyond the standard OBP or other open banking standard APIs.
|
|Dynamic Entities have their own REST APIs so you can easily Create, Read, Update and Delete records. However, you can also connect Dynamic Endpoints with your own API definitions (via Swagger) and so create custom GET endpoints connecting to any combination of Dynamic Entities.
|
|Dynamic Endpoints can retrieve the data of Dynamic Entities so you can effectively create bespoke endpoint / data combinations - at least for GET endpoints - using Dynamic Endpoints, Entities and Endpoint Mapping.
|
|In order to use Dynamic Entities you will need to have the appropriate Entitlements to Create, Read, Update or Delete records in the Dynamic Entity.
|
|You define your Dynamic Entities in JSON.
|
|Fields are typed, have an example value and a (markdown) description. They can also be constrained in size.
|
|You can also create field "references" to other fields in other Entities. These are like foreign keys to other Dynamic or Static (built in) entities.
|In other words, if you create an Entity called X which has a field called A, you can force the values of X.A to match the values of Y.B where Y is another Dynamic Entity or Z.B where Z is a Static (OBP) Entity.
|If you want to add data to an existing Entity, you can create a Dynamic Entity which has a reference field to the existing entity.
|
|Dynamic Entities can be created at the System level (bank_id is null) - or Bank / Space level (bank_id is not null). You might want to create Bank level Dynamic Entities in order to grant automated roles based on user email domain.
|
|When creating a Dynamic Entity, OBP automatically:
|
|* Creates a data structure in the OBP database in which to store the records of the new Entity.
|* Creates a primary key for the Entity which can be used to update and delete the Entity.
|* Creates Create, Read, Update and Delete endpoints to operate on the Entity so you can insert, get, modify and delete records. These CRUD operations are all available over the generated REST endpoints.
|* Creates Roles to guard the above endpoints.
|
|Following the creation of a Dynamic Entity you will need to grant yourself or others the appropriate roles before you can insert or get records.
|
|The generated Roles required for CRUD operations on a Dynamic Entity are like any other OBP Role i.e. they can be requested, granted, revoked and auto-granted using the API Explorer / API Manager or via REST API. To see the Roles required for a Dynamic Entities endpoints, see the API Explorer for each endpoint concerned.
|
|Each Dynamic Entity gets a dynamicEntityId which uniquely identifies it and also the userId which identifies the user who created the Entity. The dynamicEntityId is used to update the definition of the Entity.
|
|To visualise any data contained in Dynamic Entities you could use external BI tools and use the GET endpoints and authenticate using OAuth or Direct Login.
|
|The following videos are available:
|
|	* [Introduction to Dynamic Entities](https://vimeo.com/426524451)
|	* [Features of Dynamic Entities](https://vimeo.com/446465797)
|
""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Dynamic Endpoint Manage",
		description =
			s"""
|
|If you want to create endpoints from Swagger / Open API specification files, use Dynamic Endpoints.
|
|We use the term "Dynamic" because these Endpoints persist in the OBP database and are served from real time generated Scala code.
|
|This contrasts to the "Static" endpoints (see the Static glossary item) which are served from static Scala code.
|
|Dynamic endpoints can be changed in real-time and do not require an OBP instance restart.
|
|When you POST a swagger file, all the endpoints defined in the swagger file, will be created in this OBP instance.
|
|You can create a set of endpoints in three different modes:
|
|1) If the *host* field in the Swagger file is set to "dynamic_entity", then you should link the swagger JSON fields to Dynamic Entity fields. To do this use the *Endpoint Mapping* endpoints.
|
|2) If the *host* field in the Swagger file is set to "obp_mock", the Dynamic Endpoints created will return *example responses defined in the swagger file*.
|
|3) If you need to link the responses to external resource, use the *Method Routing* endpoints.
|
|
|Dynamic Endpoints can be created at the System level (bank_id is null) or Bank / Space level (bank_id is NOT null).
|You might want to create Bank level Dynamic Entities in order to grant automated roles based on user email domain. See the OBP-API sample.props.template
|
|Upon the successful creation of each Dynamic Endpoint, OBP will automatically:
|
|*Create a Guard with a named Role on the Endpoint to protect it from unauthorised users.
|*Grant you an Entitlement to the required Role so you can call the endpoint and pass its Guard.
|
|The following videos are available:
|
|	* [Introduction to Dynamic Endpoints](https://vimeo.com/426235612)
|	* [Features of Dynamic Endpoints](https://vimeo.com/444133309)
|
""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Endpoint Mapping",
		description =
			s"""
   |Endpoint Mapping can be used to map each JSON field in a Dynamic Endpoint to different Dynamic Entity fields.
   |
   |This document assumes you already have some knowledge of OBP Dynamic Endpoints and Dynamic Entities.
   |
   |To enable Endpoint Mapping for your Dynamic Endpoints, either set the `host` in the swagger file to "dynamic_entity" upon creation of the Dynamic Endpoints - or update the host using the Update Dynamic Endpoint Host endpoints.
   |
   |Once the `host` is thus set, you can use the Endpoint Mapping endpoints to map the Dynamic Endpoint fields to Dynamic Entity data.
   |
   |See the [Create Endpoint Mapping](/index#OBPv4.0.0-createEndpointMapping) JSON body. You will need to know the operation_id in advance and you can prepare the request_mapping and response_mapping objects. You can get the operation ID from the API Explorer or Get Dynamic Endpoints endpoints.
   |
	 |For more details and a walk through, please see the following video:
	 |
	 |	* [Endpoint Mapping](https://vimeo.com/553369108)
   |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Branch",
		description =
			s"""The bank branches, it contains the address, location, lobby, drive_up of the Branch.
				 """.stripMargin)

	glossaryItems += GlossaryItem(
		title = "API",
		description =
			s"""|The terms `API` (Application Programming Interface) and `Endpoint` are used somewhat interchangeably.
|
|However, an API normally refers to a group of Endpoints.
|
|An endpoint has a unique URL path and HTTP verb (GET, POST, PUT, DELETE etc).
|
|When we POST a Swagger file to the Create Endpoint endpoint, we are in fact creating a set of Endpoints that have a common Tag. Tags are used to group Endpoints in the API Explorer and filter the Endpoints in the Resource Doc endpoints.
|
|Endpoints can also be grouped together in Collections.
|
|See also [Endpoint](/glossary#Endpoint)
|
				 """.stripMargin)

	glossaryItems += GlossaryItem(
		title = "Endpoint",
		description =
			s"""
|The terms `Endpoint` and `API` (Application Programming Interface) are used somewhat interchangeably. However, an Endpoint is a specific URL defined by its path (eg. /obp/v4.0/root) and its http verb (e.g. GET, POST, PUT, DELETE etc).
|Endpoints are like arrows into a system. Like any good computer function, endpoints should expect much and offer little in return. They should fail early and be clear about any reason for failure. In other words each endpoint should have a tight and limited contract with any caller - and especially the outside world!
|
|In OBP, all system endpoints are RESTful - and most Open Banking Standards are RESTful. However, it is possible to create non-RESTful APIs in OBP using the Create Endpoint endpoints.
|
|You can immediately tell if an endpoint is not RESTful by seeing a verb in the URL. For example:
|
|POST /customers is RESTful = GOOD
|POST /create-customer is NOT RESTful (due to the word "create") = BAD
|
|RESTful APIs use resource names in URL paths. You can think of RESTful resources like database tables. You wouldn't name a database table "create-customer", so don't use that in a URL path.
|
|If we consider interacting with a Customers table, we read the data using GET /Customers and write to the table using POST /Customers. This model keeps the names clear and predictable.
|Note that we are only talking about the front end interface here - anything could be happening in the backend - and that is one of the beauties of APIs. For instance GET /Customers could call 5 different databases and 3 XML services in the background. Similarly POST /Customers could insert into various different tables and backend services. The important thing is that the user of the API (The Consumer or Client in OAuth parlance) has a simple and consistent experience.
|
|In OBP, all Endpoints are implemented by `Partial Functions`. A Partial Function is a function which only accepts (and responds) to calls with certain parameter values. In the case of API Endpoints the inputs to the Partial Functions are the URL path and http verb. Note that it would be possible to have different Partial Functions respond even to different query parameters, but for OBP static endpoints at least, we take the approach of URL path + http Verb is handled by one Partial Function.
|Each Partial Function is identified by an Operation ID which uniquely identifies the endpoint in the system. Having an Operation ID allows us to decorate the Endpoint with metadata (e.g. Tags) and surround the Endpoint with behaviour such as JSON Schema Validation.
|
|See also [API](/glossary#API)
|
""".stripMargin)



	glossaryItems += GlossaryItem(
		title = "API Tag",
		description =
			s"""All OBP API relevant docs, eg: API configuration, JSON Web Key, Adapter Info, Rate Limiting
				 """.stripMargin)



	glossaryItems += GlossaryItem(
		title = "Account Access",
		description =
			s"""
   |Account Access governs access to Bank Accounts by end Users. It is an intersecting entity between the User and the View Definition.
   |A User must have at least one Account Access record record in order to interact with a Bank Account over the OBP API.
   |""".stripMargin)

//	val allTagNames: Set[String] = ApiTag.allDisplayTagNames
//	val existingItems: Set[String] = glossaryItems.map(_.title).toSet
//	allTagNames.diff(existingItems).map(title => glossaryItems += GlossaryItem(title, title))

	glossaryItems += GlossaryItem(
		title = "Static Endpoint",
		description =
			s"""
|Static endpoints are served from static Scala source code which is contained in (public) Git repositories.
|
|Static endpoints cover all the OBP API and User management functionality as well as the Open Bank Project banking APIs and other Open Banking standards such as UK Open Banking, Berlin Group and STET etc..
				 |In short, Static (standard) endpoints are defined in Git as Scala source code, where as Dynamic (custom) endpoints are defined in the OBP database.
				 |
|Modifications to Static endpoint core properties such as URLs and response bodies require source code changes and an instance restart. However, JSON Schema Validation and Dynamic Connector changes can be applied in real-time.
""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Message Doc",
		description =
			s"""
|OBP can communicate with core banking systems (CBS) and other back end services using a "Connector -> Adapter" approach.
|
|The OBP Connector is a core part of the OBP-API and is written in Scala / Java and potentially other JVM languages.
|
|The OBP Connector implements multiple functions / methods in a style that satisfies a particular transport / protocol such as HTTP REST, Akka or RabbitMq.
|
|An OBP Adapter is a separate software component written in any programming language that responds to requests from the OBP Connector.
|
|Requests are sent by the Connector to the Adapter (or a message queue).
|
|The Adapter must satisfy the Connector method's request for data (or return an error).
|
|"Message Docs" are used to define and document the request / response structure.
|
|Message Docs are visible in the API Explorer.
|
|Message Docs are also available over the Message Doc endpoints.
|
|Each Message Doc relates to one OBP function / method.
|
|The Message Doc includes:
|
|  1) The Name of the internal OBP function / method e.g. getAccountsForUser
|  2) The Outbound Message structure.
|  3) The Inbound Message structure.
|  4) The Connector name which denotes the protocol / transport used (e.g. REST, Akka, RabbitMq etc)
|  5) Outbound / Inbound Topic
|  6) A list of required Inbound fields
|  7) A list of dependent endpoints.
|
|The perspective is that of the OBP-API Connector i.e. the OBP Connector sends the message Out, and it receives the answer In.
|
|The Outbound message contains several top level data structures:
|
| 1) The outboundAdapterCallContext
|
| This tells the Adapter about the specific REST call that triggered the request and contains the correlationId to uniquely identify the REST call, the consumerId to identify the API Consumer (App) and a generalContext which is a list of key / value pairs that give the Adapter additional custom information about the call.
|
| 2) outboundAdapterAuthInfo
|
|This tells the Adapter about the authenticated User that is making the call including: the userId, the userName, the userAuthContext (a list of key / value pairs that have been validated using SCA (see the UserAuthContext endpoints)) and other optional structures such as linked Customers and Views on Accounts to further identify the User.
|
|3) The body
|
|The body contains named fields that are specific to each Function / Message Doc.
|
|For instance, getTransaction might send the bankId, accountId and transactionId so the Adapter can route the request based on bankId and check User permissions on the AccountId before retrieving a Transaction.
|
|The Inbound message
|
|The Inbound message is the reply or response from the Adapter and has the following structure:
|
|1) The inboundAdapterCallContext
|
|This is generally an echo of the outboundAdapterCallContext so the Connector can double check the target destination of the response.
|
|2) The status
|
|This contains information about status of the response including any errorCode and a list of backendMessages.
|
|3) The data
|
|This contains the named fields and their values which are specific to each Function / Message Doc.
|
|
|The Outbound / Inbound Topics are used for routing in multi OBP instance / RabbitMq installations. (so OBP nodes only listen only to the correct Topics).
|
|The dependent endpoints are listed to facilitate navigation in the API Explorer so integrators can test endpoints during integration.
|
|Message Docs can be generated automatically using OBP code tools. Thus, it's possible to create custom connectors that follow specific protocol and structural patterns e.g. for message queue X over XML format Y.
|
|""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Method Routing",
		description =
			s"""
   |
   | Open Bank Project can have different connectors, to connect difference data sources.
   | We support several sources at the moment, eg: databases, rest services, stored procedures and RabbitMq.
   |
   | If OBP set connector=star, then you can use this method routing to switch the sources.
   | And we also provide the fields mapping in side the endpoints. If the fields in the source are different from connector,
   | then you can map the fields yourself.
   |
   |  The following videos are available:
   |
   | *[Method Routing Endpoints](https://vimeo.com/398973130)
   | *[Method Routing Endpoints Mapping](https://vimeo.com/404983764)
   |
   |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "JSON Schema Validation",
		description =
			s"""
   |
   |JSON Schema is "a vocabulary that allows you to annotate and validate JSON documents".
   |
   |By applying JSON Schema Validation to your OBP endpoints you can constrain POST and PUT request bodies. For example, you can set minimum / maximum lengths of fields and constrain values to certain lists or regular expressions.
	 |
	 |See [JSONSchema.org](https://json-schema.org/) for more information about the JSON Schema standard.
|
|To create a JSON Schema from an any JSON Request body you can use [JSON Schema Net](https://jsonschema.net/app/schemas/0)
|
|(The video link below shows how to use that)
   |
   |Note: OBP Dynamic Entities also use JSON Schema Validation so you don't need to additionally wrap the resulting endpoints with extra JSON Schema Validation but you could do.
   |
   | You can apply JSON schema validations to any OBP endpoint's request body using the POST and PUT endpoints listed in the link below.
   |
   |PLEASE SEE the following video explanation: [JSON schema validation of request for Static and Dynamic Endpoints and Entities](https://vimeo.com/485287014)
   |
   |""".stripMargin)


	glossaryItems += GlossaryItem(
		title = "Connector Method",
		description =
			s"""
			| Developers can override all the existing Connector methods.
			| This function needs to be used together with the Method Routing.
			| When we set "connector = internal", then the developer can call their own method body at API level.
			|
			|For example, the GetBanks endpoint calls the connector "getBanks" method. Then, developers can use these endpoints to modify the business logic in the getBanks method body.
			|
			|  The following videos are available:
		  |* [Introduction for Connector Method] (https://vimeo.com/507795470)
		  |* [Introduction 2 for Connector Method] (https://vimeo.com/712557419)
		  |
		  |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Dynamic Message Doc",
		description =
			s"""
			| In OBP we represent messages sent by a Connector method / function as MessageDocs.
			| A MessageDoc defines the message the Connector sends to an Adapter and the response it expects from the Adapter.
			|
			| Using this endpoint, developers can create their own scala methods aka Connectors in OBP code.
			| These endpoints are designed for extending the current connector methods.
			|
			| When you call the Dynamic Resource Doc endpoints, sometimes you need to call internal Scala methods which
			|don't yet exist in the OBP code. In this case you can use these endpoints to create your own internal Scala methods.
      |
      |You can also use these endpoints to create your own helper methods in OBP code.
			|
			| This feature is somewhat work in progress (WIP).
|
		  |The following videos are available:
			|* [Introduction to Dynamic Message Doc] (https://vimeo.com/623317747)
		  |
		  |""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "QWAC",
			description =
				s"""A Qualified Website Authentication Certificate is a qualified digital certificate under the trust services defined in the European Union eIDAS Regulation.
					 |A website authentication certificate makes it possible to establish a Transport Layer Security channel with the subject of the certificate, which secures data transferred through the channel.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "Dynamic linking (PSD2 context)",
			description =
				s"""""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "TPP",
			description =
				s"""(TPP) Third Party Providers are authorised/registered organisations or natural persons that use APIs developed to Standards to access customer’s accounts, in order to provide account information services and/or to initiate payments.
					 |Third Party Providers are either/both Payment Initiation Service Providers (PISPs) and/or Account Information Service Providers (AISPs).""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "QSealC",
			description =
				s"""Qualified electronic Seal Certificate.
					 |A certificate for electronic seals allows the relying party to validate the identity of the subject of the certificate,
					 |as well as the authenticity and integrity of the sealed data, and also prove it to third parties.
					 |The electronic seal provides strong evidence, capable of having legal effect, that given data is originated by the legal entity identified in the certificate.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "CRL",
			description =
				s"""Certificate Revocation List.
					 |CRL issuers issue CRLs. The CRL issuer is either the CA (certification authority) or an entity that has been authorized by the CA to issue CRLs.
					 |CAs publish CRLs to provide status information about the certificates they issued.
					 |However, a CA may delegate this responsibility to another trusted authority.
					 |It is described in RFC 5280.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "OCSP",
			description =
				s"""The Online Certificate Status Protocol (OCSP) is an Internet protocol used for obtaining the revocation status of an X.509 digital certificate.
					 |It is described in RFC 6960 and is on the Internet standards track. It was created as an alternative to certificate revocation lists (CRL),""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "Cross-Device Authorization",
			description =
				s"""
					 |Cross-device authorization flows enable a user to initiate an authorization flow on one device
					 |(the Consumption Device) and then use a second, personally trusted, device (Authorization Device) to
					 |authorize the Consumption Device to access a resource (e.g., access to a service).
					 |Two examples of popular cross-device authorization flows are:
					 | - The Device Authorization Grant [RFC8628](https://datatracker.ietf.org/doc/html/rfc8628)
					 | - Client-Initiated Backchannel Authentication [CIBA]((https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html))
					 |""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "Consumption Device (CD)",
			description =
				s"""The Consumption Device is the device that helps the user consume the service. In the [CIBA]((https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)) use case, the user is not necessarily in control of the CD. For example, the CD may be in the control of an RP agent (e.g. at a bank teller) or might be a device controlled by the RP (e.g. a petrol pump)|""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "Authentication Device (AD)",
			description =
				s"""The device on which the user will authenticate and authorize the request, often a smartphone.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "Risk-based authentication",
			description =
				s"""Please take a look at "Adaptive authentication" glossary item.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "Adaptive authentication",
			description =
				s"""Adaptive authentication, also known as risk-based authentication, is dynamic in a way it automatically triggers additional authentication factors, usually via MFA factors, depending on a user's risk profile.
					 |An example of this authentication at OBP-API side is the feature "Transaction request challenge threshold".
					 | -
					 |""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "Transaction request challenge threshold",
			description =
				s"""Is an example of "Adaptive authentication" where, in a dynamic way, we get challenge threshold via CBS depending on a user's risk profile.
   |It implies that in a case of risky transaction request, over a certain amount, a user is prompted to answer the challenge.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "Multi-factor authentication (MFA)",
			description =
				s"""Multi-factor authentication (MFA) is a multi-step account login process that requires users to enter more information than just a password. For example, along with the password, users might be asked to enter a code sent to their email, answer a secret question, or scan a fingerprint.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "CIBA",
			description =
				s"""An acronym for Client-Initiated Backchannel Authentication.
   |For more details about it please take a look at the official specification: [OpenID Connect Client Initiated Backchannel Authentication Flow](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)
   |Please note it is a cross-device protocol and SHOULD not be used for same-device scenarios.
   |If the Consumption Device and Authorization Device are the same device, protocols like OpenID Connect Core [OpenID.Core](https://openid.net/specs/openid-connect-core-1_0.html) and OAuth 2.0 Authorization Code Grant as defined in [RFC6749](https://www.rfc-editor.org/info/rfc6749) are more appropriate.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "OIDC",
			description =
				s"""An acronym for OpenID Connect (OIDC) is an identity authentication protocol that is an extension of open authorization (OAuth) 2.0 to standardize the process for authenticating and authorizing users when they sign in to access digital services.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "How OpenID Connect Works",
			description =
				s"""The OpenID Connect protocol, in abstract, follows these steps:
				|
				   |* End user navigates to a website or web application via a browser.
					 |* End user clicks sign-in and types their username and password.
					 |* The RP (Client) sends a request to the OpenID Provider (OP).
					 |* The OP authenticates the User and obtains authorization.
					 |* The OP responds with an Identity Token and usually an Access Token.
					 |* The RP can send a request with the Access Token to the User device.
					 |* The UserInfo Endpoint returns Claims about the End-User.
           |### Terminology
					 |#### Authentication
					 |The secure process of establishing and communicating that the person operating an application or browser is who they claim to be.
					 |#### Client
					 |A client is a piece of software that requests tokens either for authenticating a user or for accessing a resource (also often called a relying party or RP).
					 |A client must be registered with the OP. Clients can be web applications, native mobile and desktop applications, etc.
					 |#### Relying Party (RP)
					 |RP stands for Relying Party, an application or website that outsources its
					 |user authentication function to an IDP.
					 |#### OpenID Provider (OP) or Identity Provider (IDP)
					 |An OpenID Provider (OP) is an entity that has implemented the OpenID Connect and OAuth 2.0 protocols,
					 |OP’s can sometimes be referred to by the role it plays, such as: a security token service,
					 |an identity provider (IDP), or an authorization server.
					 |#### Identity Token
					 |An identity token represents the outcome of an authentication process.
					 |It contains at a bare minimum an identifier for the user (called the sub aka subject claim)
					 |and information about how and when the user authenticated. It can contain additional identity data.
					 |#### User
					 |A user is a person that is using a registered client to access resources.
					 |    """.stripMargin)

		glossaryItems += GlossaryItem(
			title = "OAuth 2.0",
			description =
				s"""OAuth 2.0, is a framework, specified by the IETF in RFCs 6749 and 6750 (published in 2012) designed to support the development of authentication and authorization protocols. It provides a variety of standardized message flows based on JSON and HTTP.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "FAPI",
			description =
				s"""An acronym for Financial-grade API.""".stripMargin)

		glossaryItems += GlossaryItem(
			title = "FAPI 1.0",
			description =
				s"""The Financial-grade API is a highly secured OAuth profile that aims to provide specific implementation guidelines for security and interoperability.
   |The Financial-grade API security profile can be applied to APIs in any market area that requires a higher level of security than provided by standard [OAuth](https://datatracker.ietf.org/doc/html/rfc6749) or [OpenID Connect](https://openid.net/specs/openid-connect-core-1_0.html).
   |Financial-grade API Security Profile 1.0 consists of the following parts:
	 |
	 |* <a href="https://openid.net/specs/openid-financial-api-part-1-1_0.html" target="_blank">Financial-grade API Security Profile 1.0 - Part 1: Baseline</a>
	 |* <a href="https://openid.net/specs/openid-financial-api-part-2-1_0.html" target="_blank">Financial-grade API Security Profile 1.0 - Part 2: Advanced</a>
   |
   |These parts are intended to be used with <a href="https://tools.ietf.org/html/rfc6749" target="_blank">RFC6749</a>, <a href="https://tools.ietf.org/html/rfc6750" target="_blank">RFC6750</a>, <a href="https://tools.ietf.org/html/rfc7636" target="_blank">RFC7636</a>, and <a href="https://openid.net/specs/openid-connect-core-1_0.html" target="_blank">OIDC</a>.
	 |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Transaction-Request-Introduction",
		description =
			s"""
   |In OBP we initiate a Payment by creating a Transaction Request.
   |
	 |An OBP `transaction request` may or may not result in a `transaction`. However, a `transaction` only has one possible state: completed.
	 |
	 |A `Transaction Request` can have one of several states: INITIATED, NEXT_CHALLENGE_PENDING etc.
	 |
	 |`Transactions` are modeled on items in a bank statement that represent the movement of money.
	 |
	 |`Transaction Requests` are requests to move money which may or may not succeed and thus result in a `Transaction`.
	 |
	 |A `Transaction Request` might create a security challenge that needs to be answered before the `Transaction Request` proceeds.
	 |In case 1 person needs to answer security challenge we have next flow of state of an `transaction request`:
	 |  INITIATED => COMPLETED
	 |In case n persons needs to answer security challenge we have next flow of state of an `transaction request`:
	 |  INITIATED => NEXT_CHALLENGE_PENDING => ... => NEXT_CHALLENGE_PENDING => COMPLETED
	 |
	 |The security challenge is bound to a user i.e. in case of right answer and the user is different than expected one the challenge will fail.
	 |
	 |Rule for calculating number of security challenges:
	 |If product Account attribute REQUIRED_CHALLENGE_ANSWERS=N then create N challenges
	 |(one for every user that has a View where permission $CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT=true)
	 |In case REQUIRED_CHALLENGE_ANSWERS is not defined as an account attribute default value is 1.
	 |
	 |Transaction Requests contain charge information giving the client the opportunity to proceed or not (as long as the challenge level is appropriate).
	 |
	 |Transaction Requests can have one of several Transaction Request Types which expect different bodies. The escaped body is returned in the details key of the GET response.
	 |This provides some commonality and one URL for many different payment or transfer types with enough flexibility to validate them differently.
	 |
	 |The payer is set in the URL. Money comes out of the BANK_ID and ACCOUNT_ID specified in the URL.
	 |
	 |In sandbox mode, TRANSACTION_REQUEST_TYPE is commonly set to ACCOUNT. See getTransactionRequestTypesSupportedByBank for all supported types.
	 |
	 |In sandbox mode, if the amount is less than 1000 EUR (any currency, unless it is set differently on this server), the transaction request will create a transaction without a challenge, else the Transaction Request will be set to INITIALISED and a challenge will need to be answered.
	 |
	 |If a challenge is created you must answer it using Answer Transaction Request Challenge before the Transaction is created.
	 |
	 |You can transfer between different currency accounts. (new in 2.0.0). The currency in body must match the sending account.
	 |
	 |For exchange rates in this sandbox see here: ${Glossary.getGlossaryItemLink("FX-Rates")}
	 |
	 |Transaction Requests satisfy PSD2 requirements thus:
	 |
	 |1) A transaction can be initiated by a third party application.
	 |
	 |2) The customer is informed of the charge that will incurred.
	 |
	 |3) The call supports delegated authentication (OAuth)
	 |
	 |See [this python code](https://github.com/OpenBankProject/Hello-OBP-DirectLogin-Python/blob/master/hello_payments.py) for a complete example of this flow.
	 |
	 |There is further documentation [here](https://github.com/OpenBankProject/OBP-API/wiki/Transaction-Requests)
	 |
	 |
   |
	 |""".stripMargin)

//	val exchangeRates =
//		APIUtil.getPropsValue("webui_api_explorer_url", "") +
//			"/more?version=OBPv4.0.0&list-all-banks=false&core=&psd2=&obwg=#OBPv2_2_0-getCurrentFxRate"

	glossaryItems += GlossaryItem(
		title = "FX-Rates",
		description =
			s"""You can use the following endpoint to get the FX Rates available on this OBP instance: ${getApiExplorerLink("Get FX Rates", "OBPv2.2.0-getCurrentFxRate")}
|
|""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Counterparty-Limits",
		description =
			s"""Counterparty Limits can be used to restrict payment (Transaction Request) amounts and frequencies (per month, year, total) that can be made to a Counterparty (Beneficiary).
				 |
|Counterparty Limits can be used to limit both single or repeated payments (VRPs) to a Counterparty Beneficiary.
|
|Counterparty Limits reference a counterparty_id (a UUID) rather an an IBAN or Account Number.
|This means it is possible to have multiple Counterparties that refer to the same external bank account.
|In other words, a Counterparty Limit restricts an OBP Counterparty rather than a certain IBAN or other Bank Account Number.
|
|Since Counterparties are bound to OBP Views it is possible to create similar Counterparties used by different Views. This is by design i.e. a Two Users called Accountant1 could Accountant2 could create their own Views and Counterparties referencing the same corporation but still have their own limits say for different cost centers.
|
|To manually create and use a Counterparty Limit via a Consent for Variable Recurring Payments (VRP) you would:
				 |1) Create a Custom View named e.g. VRP1.
				 |2) Place a Beneficiary Counterparty on that view.
				 |3) Add Counterparty Limits for that Counterparty.
				 |4) Generate a Consent containing the bank, account and view (e.g. VRP1)
				 |5) Let the App use the consent to trigger Transaction Requests.
|
|However, you can use the following ${Glossary.getApiExplorerLink("endpoint", "OBPv5.1.0-createVRPConsentRequest")} to automate the above steps.
|
				 |""".stripMargin)






	glossaryItems += GlossaryItem(
			title = "FAPI 2.0",
			description =
				s"""FAPI 2.0 has a broader scope than FAPI 1.0.
					 |It aims for complete interoperability at the interface between client and authorization server as well as interoperable security mechanisms at the interface between client and resource server.
					 |It also has a more clearly defined attacker model to aid formal analysis.
					 |Please note that <a href="https://openid.net/specs/fapi-2_0-baseline-01.html" target="_blank">FAPI 2.0</a> is still in draft.""".stripMargin)


		glossaryItems += GlossaryItem(
			title = "Available FAPI profiles",
			description =
				s"""The following are the FAPI profiles which are either in use by multiple implementers or which are being actively developed by the OpenID Foundation’s FAPI working group:
					 |
					 |* <a href="https://openid.net/specs/openid-financial-api-part-2-wd-06.html">FAPI 1 Implementers Draft 6 (OBIE Profile)</a>
					 |* <a href="https://openid.net/specs/openid-financial-api-part-1-1_0.html">FAPI 1 Baseline</a>
					 |* <a href="https://openid.net/specs/openid-financial-api-part-2-1_0.html">FAPI 1 Advanced</a>
					 |* <a href="https://openbanking-brasil.github.io/specs-seguranca/open-banking-brasil-financial-api-1_ID3-ptbr.html">Brazil Security Standard</a>
					 |* <a href="https://openid.net/specs/fapi-2_0-baseline-01.html">FAPI 2</a>
					 |* <a href="https://bitbucket.org/openid/fapi/src/master/FAPI_2_0_Advanced_Profile.md">FAPI 2 Message Signing:</a>
					 |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Counterparties",
		description =
			s"""
|
|In OBP, there are two types of Counterparty:
|
|* Explicit Counterparties are created by calling an OBP endpoint - mainly for the purpose of creating a payment or variable recurring payments (VRPs) via Transaction Requests.
|
|* Implicit Counterparties (or "Other Accounts") are generated automatically from transactions - mainly for the purpose of tagging or adding other metadata.
|
|Counterparties always bound to a "View" on an Account. In this way, different managers of an account can use different sets of beneficiaries.
|
|Counterparties can be thought of the other side of of a transaction i.e. the other account or other party.
|
|Common fields in a Counterparty are:
|
|- id : A UUID which references it.
|
|- name : the human readable name (e.g. Piano teacher)
|
|- description : the human readable name (e.g. Piano teacher)
|
|- currency : account currency (e.g. EUR, GBP, USD, ...)
|
|- other_bank_routing_scheme : eg: 'OBP', 'BIC', 'bankCode' etc
|
|- other_bank_routing_address : eg: `gh.29.uk` - it must be a valid example of the scheme and may be validated for existance.
|
|- other_account_routing_scheme : eg: 'OBP', 'IBAN', 'AccountNumber' etc.
|
|- other_account_routing_address : eg: `1d65db7c-a7b2-4839-af41-95` -  a valid example of the scheme which may be validated for existance.
|
|The above fields describe how the backend can route payments to the counterparty.
|
|Alternative routings might be useful as well:
|
|- other_account_secondary_routing_scheme : An alternative routing scheme
|
|- other_account_secondary_routing_address : If it is an IBAN value, it should be unique for each counterparty.
|
|- other_branch_routing_scheme : eg: OBP or other branch scheme
|
|- other_branch_routing_address : eg: `branch-id-123. Unlikely to be used in sandbox mode.
|
|In order to send payments to a counterparty:
|
|- is_beneficiary : must be set to `true`
|
|If the backend wants to transmit other information we can use:
|
| - bespoke: A list of key-value pairs can be added to the counterparty.
|
|Note: In order to add a Counterparty to a View, the view must have the canAddCounterparty permission
|
|Counterparties may have Limits have setup for them which constrain payments made to them through Variable Recurring Payments (VRP).
					 |
					 |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Regulated-Entities",
		description =
			s"""
				 |In the context of the Open Bank Project (OBP), a "Regulated Entity" refers to organizations that are recognized and authorized to provide financial services under regulatory frameworks. These entities are overseen by regulatory authorities to ensure compliance with financial regulations and standards.
				 |
				 |## Key Points About Regulated Entities in OBP:
				 |
				 |**Endpoint for Retrieval**: You can retrieve information about regulated entities using the ${getApiExplorerLink("Get Regulated Entities", "OBPv5.1.0-regulatedEntities")} endpoint. This does not require authentication and provides data on various regulated entities, including their services, entity details, and more.
				 |
				 |**Creating a Regulated Entity**: The API also allows for the creation of a regulated entity using the ${getApiExplorerLink("Create Regulated Entity", "OBPv5.1.0-createRegulatedEntity")} endpoint. User authentication is required for this operation.
				 |
				 |**Retrieving Specific Entity Details**: To get details of a specific regulated entity, you can use the ${getApiExplorerLink("Get Regulated Entity by Id", "OBPv5.1.0-getRegulatedEntityById")} endpoint, where you need to specify the entity ID. No authentication is needed.
				 |
				 |**Deleting a Regulated Entity**: If you need to remove a regulated entity, the ${getApiExplorerLink("Delete Regulated Entity", "OBPv5.1.0-deleteRegulatedEntity")} endpoint is available, but it requires authentication.
				 |
				 |## Entity Information:
				 |
				 |Each regulated entity has several attributes, including:
				 |
				 |* **Entity Code**: A unique identifier for the entity
				 |* **Website**: The entitys official website URL
				 |* **Country and Address Details**: Location information for the entity
				 |* **Certificate Public Key**: Public key used for digital certificates
				 |* **Entity Type and Name**: Classification and official name of the entity
				 |* **Services offered**: List of financial services provided by the entity
				 |
				 |Regulated entities play a crucial role in maintaining trust and compliance within the financial ecosystem managed through the OBP platform.
				 |
				 |## Configuration Properties:
				 |
				 |Regulated entities functionality is supported by several configuration properties in OBP:
				 |
				 |**Certificate and Signature Verification** (for Berlin Group/PSD2 TPP authentication):
				 |
				 |* `truststore.path.tpp_signature` - Path to the truststore containing TPP certificates
				 |* `truststore.password.tpp_signature` - Password for the TPP signature truststore
				 |* `truststore.alias.tpp_signature` - Alias for the TPP signature certificate
				 |
				 |**Fallback Certificate Configuration**:
				 |
				 |* `truststore.path` - General truststore path (fallback if TPP-specific not set)
				 |* `keystore.path` - Path to the keystore for certificate operations
				 |* `keystore.password` - Password for the keystore
				 |* `keystore.passphrase` - Passphrase for keystore private keys
				 |* `keystore.alias` - Alias for certificate entries in keystore
				 |
				 |These properties are used for TPP (Third Party Provider) certificate validation in PSD2/Berlin Group implementations, where regulated entities authenticate using QWAC (Qualified Website Authentication Certificate) or other qualified certificates.
				 |
				 |## Internal Usage by OBP:
				 |
				 |OBP internally uses regulated entities for several authentication and authorization functions:
				 |
				 |**Certificate-Based Authentication**: When the property `requirePsd2Certificates=ONLINE` is set, OBP automatically validates incoming API requests against registered regulated entities using their certificate information.
				 |
				 |**Automatic Consumer Creation**: For Berlin Group/PSD2 compliance, OBP automatically creates API consumers for TPPs based on their regulated entity registration and certificate validation.
				 |
				 |**Service Provider Authorization**: OBP checks if regulated entities have the required service provider roles (PSP_AI, PSP_PI, PSP_IC, PSP_AS) before granting access to specific API endpoints.
				 |
				 |**Berlin Group/UK Open Banking Integration**: Many Berlin Group (v1.3) and UK Open Banking (v3.1.0) API endpoints automatically call `passesPsd2Aisp()` and related functions to validate regulated entity certificates.
				 |
				 |This integration ensures that only properly registered and certificated Third Party Providers can access sensitive banking data and payment initiation services in compliance with PSD2 regulations.
				 |
				 |## Real-Time Entity / Certificate Retrieval:
				 |
				 |Regulated Entities can be retrieved in real time from the National Authority / National Bank through the following data flow patterns:
				 |
				 |**Direct National Authority Connection**:
				 |
				 |`OBP BG API instance -> getRegulatedEntities -> Connector -> National Authority`
				 |
				 |**Via OBP Regulated Entities API Instance**:
				 |
				 |`OBP BG API instance -> getRegulatedEntities -> Connector -> OBP Regulated Entities API instance -> Connector -> National Authority`
				 |
				 |This real-time integration ensures that regulated entity information is always current and reflects the latest regulatory status and certifications from official national sources.
				 |
				 |
				 |**RabbitMQ Message Documentation** (other connectors are also available):
				 |
				 |* ${messageDocLinkRabbitMQ("obp.getRegulatedEntities")} - Retrieve all regulated entities
				 |* ${messageDocLinkRabbitMQ("obp.getRegulatedEntityByEntityId")} - Retrieve a specific regulated entity by ID
					| For instance, a National Authority might publish:
					|{
|  "comercialName": "BANK_X_TPP_AISP",
|  "idno": "1234567890123",
|  "licenseNumber": "123456_bank_x",
|  "roles": [
|    "PISP"
|  ],
|  "certificate": {
|    "snCert": "117",
|    "caCert": "Bank (test)"
|  }
|}
|
|
|and the Bank's OBP Adapter converts this and returns it to the connector like so:
|
|{
|  "inboundAdapterCallContext": {
|    "correlationId": "f347feb7-0c25-4a2f-8a40-d853917d0ccd"
|  },
|  "status": {
|    "errorCode": "",
|    "backendMessages": []
|  },
|  "data": [
|    {
|      "entityName": "BANCA COM S.A.",
|      "entityCode": "198762948",
|      "attributes": [
|        {
|          "attributeType": "STRING",
|          "name": "CERTIFICATE_SERIAL_NUMBER",
|          "value": "1082"
|        },
|        {
|          "attributeType": "STRING",
|          "name": "CERTIFICATE_CA_NAME",
|          "value": "BANK CA (test)"
|        }
|      ],
|      "services": [
|        {
|          "roles": [
|            "PSP_PI",
|            "PSP_AI"
|          ]
|        }
|      ]
|    },
|    {
|      "entityName": "Bank Y S.A.",
|      "entityCode": "1029876963",
|      "attributes": [
|        {
|          "attributeType": "STRING",
|          "name": "CERTIFICATE_SERIAL_NUMBER",
|          "value": "1135"
|        },
|        {
|          "attributeType": "STRING",
|          "name": "CERTIFICATE_CA_NAME",
|          "value": "BANK CA (test)"
|        }
|      ],
|      "services": [
|        {
|          "roles": [
|            "PSP_PI",
|            "PSP_AI"
|          ]
|        }
|      ]
|    }
|  ]
|}
|
| Note the use of Regulated Entity Attribute Names to handle different data types from the national authority.
				|
				 |Note: You can / should run a separate instance of OBP for surfacing the Regulated Entities endpoints.
				 |""".stripMargin)


	private def getContentFromMarkdownFile(path: String): String = {
		val source = scala.io.Source.fromFile(path)
		val lines: String = try source.mkString finally source.close()
		lines
			.replaceAll("getServerUrl", getServerUrl)
			.replaceAll("getObpApiRoot", getObpApiRoot)
	}

  private def getListOfFiles(): List[File] = {
		import java.net.URLDecoder
		import java.nio.charset.StandardCharsets
    val resourceUrl = getClass.getClassLoader.getResource("docs/glossary")
    val resourcePath = URLDecoder.decode(resourceUrl.getPath, StandardCharsets.UTF_8.name())
		val glossaryPath = new File(resourcePath)
    logger.info(s"|---> Glossary path: $glossaryPath")

    if (glossaryPath.exists && glossaryPath.isDirectory) {
      Option(glossaryPath.listFiles())
        .getOrElse(Array.empty) // Avoid NullPointerException
        .filter(_.isFile)
        .filter(_.getName.endsWith(".md"))
        .toList
    } else {
			logger.error(s"There are no glossary files under the path ($glossaryPath), please double check the folder path: $glossaryPath")
      List.empty[File]
    }
  }

	// Append all files from /OBP-API/docs/glossary as items
	// File name is used as a title
	// File content is used as a description
	glossaryItems.appendAll(
		getListOfFiles().map(file =>
			GlossaryItem(
				title = file.getName.replace(".md", "").replace("_", " "),
				description = getContentFromMarkdownFile(file.getPath)
			)
		)
	)

	///////////////////////////////////////////////////////////////////
	// NOTE! Some glossary items are generated in ExampleValue.scala
//////////////////////////////////////////////////////////////////

}

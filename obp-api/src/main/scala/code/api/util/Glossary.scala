package code.api.util

import code.api.Constant
import code.api.Constant._
import code.api.ResourceDocs1_4_0.OpenAPI31JSONFactory
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
		val apiExplorerPrefix = APIUtil.getPropsValue("webui_api_explorer_url", "http://localhost:5174")
		// Note: This is hardcoded for API Explorer II
		s"""<a href="$apiExplorerPrefix/operationid/$operationId">$title</a>"""
	}

	// Consumer registration URL helper
	def getConsumerRegistrationUrl(): String = {
		val apiExplorerUrl = APIUtil.getPropsValue("webui_api_explorer_url", "http://localhost:5174")
		s"$apiExplorerUrl/consumers/register"
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
		title = "Rate Limiting",
		description =
			s"""
				 |Rate Limiting controls the number of API requests a Consumer can make within specific time periods. This prevents abuse and ensures fair resource allocation across all API consumers.
				 |
				 |### Architecture - Single Source of Truth
				 |
				 |```
				 |┌─────────────────────────────────────────────────────────────────────────┐
				 |│                      RateLimitingUtil.scala                             │
				 |│                                                                         │
				 |│  ┌───────────────────────────────────────────────────────────────────┐ │
				 |│  │                                                                   │ │
				 |│  │  getActiveRateLimitsWithIds(consumerId, date):                   │ │
				 |│  │  Future[(CallLimit, List[String])]                               │ │
				 |│  │                                                                   │ │
				 |│  │  ═══════════════════════════════════════════════════════         │ │
				 |│  │  Single Source of Truth                              │ │
				 |│  │  ═══════════════════════════════════════════════════════         │ │
				 |│  │                                                                   │ │
				 |│  │  This function calculates active rate limits                │ │
				 |│  │                                                                   │ │
				 |│  │  Logic:                                                           │ │
				 |│  │  1. Query RateLimiting table for active records                  │ │
				 |│  │  2. If found, per period:                                         │ │
				 |│  │     • Ignore -1 values (unlimited rows add nothing)               │ │
				 |│  │     • Sum the rest; a sum of 0 -> blocked (429 on every call)     │ │
				 |│  │     • Nothing to sum (all -1) -> -1 (unlimited)                   │ │
				 |│  │     • Extract rate_limiting_ids                                  │ │
				 |│  │  3. If not found:                                                 │ │
				 |│  │     • Return system defaults from props                          │ │
				 |│  │     • Empty ID list                                              │ │
				 |│  │  4. Return: (CallLimit, List[rate_limiting_ids])                 │ │
				 |│  │                                                                   │ │
				 |│  └───────────────────────────────────────────────────────────────────┘ │
				 |│                              ▲                                          │
				 |│                              │                                          │
				 |└──────────────────────────────┼──────────────────────────────────────────┘
				 |                               │
				 |                               │ Both callers use
				 |                               │ the same function
				 |                               │
				 |               ┌───────────────┴───────────────┐
				 |               │                               │
				 |               │                               │
				 |    ┌──────────▼──────────┐         ┌──────────▼──────────┐
				 |    │                     │         │                     │
				 |    │  AfterApiAuth.scala │         │ Http4s600.scala     │
				 |    │                     │         │                     │
				 |    │  checkRateLimiting()│         │ getActiveCallLimits │
				 |    │                     │         │ AtDate              │
				 |    │  ─────────────────  │         │  ────────────────   │
				 |    │                     │         │                     │
				 |    │  Called: Every      │         │  Endpoint:          │
				 |    │  API request        │         │  GET /management/   │
				 |    │                     │         │  consumers/ID/      │
				 |    │  Uses:              │         │  consumer/active-   │
				 |    │  (rateLimit, _)     │         │  rate-limits/DATE   │
				 |    │                     │         │                     │
				 |    │  Ignores IDs,       │         │  Uses:              │
				 |    │  just needs the     │         │  (rateLimit, ids)   │
				 |    │  CallLimit for      │         │                     │
				 |    │  enforcement        │         │  Returns both in    │
				 |    │                     │         │  JSON response      │
				 |    │                     │         │                     │
				 |    └─────────────────────┘         └─────────────────────┘
				 |```
				 |
				 |**Key Point**: There is one function that calculates active rate limits. Both enforcement and API reporting call this one function.
				 |
				 |### How It Works
				 |
				 |1. **Rate Limit Records**: Stored in the `RateLimiting` table with date ranges (from_date, to_date)
				 |2. **Multiple Records**: A consumer can have multiple active rate limit records that overlap
				 |3. **Aggregation**: When multiple records are active, per period: a `0` in any record blocks the period; otherwise the positive values are summed; otherwise (all `-1`) the period is unlimited
				 |4. **Enforcement**: On every API request, the system checks Redis counters against the aggregated limits
				 |
				 |### Time Periods
				 |
				 |Rate limits can be set for six time periods:
				 |- **per_second_rate_limit**: Maximum requests per second
				 |- **per_minute_rate_limit**: Maximum requests per minute
				 |- **per_hour_rate_limit**: Maximum requests per hour
				 |- **per_day_rate_limit**: Maximum requests per day
				 |- **per_week_rate_limit**: Maximum requests per week
				 |- **per_month_rate_limit**: Maximum requests per month
				 |
				 |Each value means:
				 |- `0`: this record grants no calls for that period. Records are summed, so a `0` only blocks the Consumer when the sum over all of its records is 0 (for example when it is the Consumer's only record). A blocked period refuses every call with 429. This is how a suspended API Product Subscription stops a Consumer whose access came from that subscription alone.
				 |- `-1`: unlimited for that period. Once a record exists, `-1` is literal: the system default for that period does not apply. `-1` records add nothing to the sum.
				 |- a positive number: the maximum number of calls in that period. Overlapping records are summed.
				 |
				 |A Consumer with no records at all gets the system defaults (see below).
				 |
				 |### HTTP Headers
				 |
				 |When rate limiting is active, responses include:
				 |- `X-Rate-Limit-Limit`: Maximum allowed requests for the period
				 |- `X-Rate-Limit-Remaining`: Remaining requests in current period
				 |- `X-Rate-Limit-Reset`: Seconds until the limit resets
				 |
				 |### HTTP Status Codes
				 |
				 |- **200 OK**: Request allowed, headers show current limit status
				 |- **429 Too Many Requests**: Rate limit exceeded for a time period
				 |
				 |### Querying Active Rate Limits
				 |
				 |Use the endpoint:
				 |```
				 |GET /obp/v6.0.0/management/consumers/{CONSUMER_ID}/active-rate-limits/{DATE_WITH_HOUR}
				 |```
				 |
				 |Where `DATE_WITH_HOUR` is in format `YYYY-MM-DD-HH` in **UTC timezone** (e.g., `2025-12-31-13` for hour 13:00-13:59 UTC on Dec 31, 2025).
				 |
				 |Returns the aggregated active rate limits for the specified hour, including which rate limit records contributed to the totals.
				 |
				 |Rate limits are cached and queried at hour-level granularity for performance. All hours are interpreted in UTC for consistency across all servers.
				 |
				 |### System Defaults
				 |
				 |If no rate limit records exist for a consumer, system-wide defaults are used from properties:
				 |- `rate_limiting_per_second`
				 |- `rate_limiting_per_minute`
				 |- `rate_limiting_per_hour`
				 |- `rate_limiting_per_day`
				 |- `rate_limiting_per_week`
				 |- `rate_limiting_per_month`
				 |
				 |Default value: `-1` (unlimited). These defaults apply only to Consumers with no active records; a default of `0` would block every such Consumer.
				 |
				 |### Example
				 |
				 |A consumer with two overlapping rate limit records:
				 |- Record 1: 10 requests/second, 100 requests/minute
				 |- Record 2: 5 requests/second, 50 requests/minute
				 |
				 |**Aggregated limits**: 15 requests/second, 150 requests/minute
				 |
				 |The same consumer with a third record of 0 requests/second (for example a suspended API Product Subscription) is unchanged, because the 0 adds nothing to the sum:
				 |
				 |**Aggregated limits**: 15 requests/second, 150 requests/minute
				 |
				 |A consumer whose only record is 0 requests/second:
				 |
				 |**Aggregated limits**: 0 requests/second (blocked, 429 on every call)
				 |
				 |### Configuration
				 |
				 |Enable rate limiting by setting:
				 |```
				 |use_consumer_limits=true
				 |```
				 |
				 |For anonymous access, configure:
				 |```
				 |user_consumer_limit_anonymous_access=1000
				 |```
				 |(Default: 1000 requests per hour. `0` blocks all anonymous access, `-1` removes the limit.)
				 |
				 |### Related Concepts
				 |
				 |- **Consumer**: The API client subject to rate limiting
				 |- **Redis**: Storage system for tracking request counts
				 |- **Single Source of Truth**: `RateLimitingUtil.getActiveRateLimitsWithIds()` function calculates all active rate limits
			""".stripMargin)

	glossaryItems += GlossaryItem(
    title = "API-Explorer-II-Help",
    description = s"""
			 |## API Explorer II - How to Use
			 |
			 |API Explorer II is an interactive Swagger/OpenAPI interface for discovering and testing OBP and other standard endpoints.
			 |
			 |### Key Features
			 |
			 |* Browse and search all available API endpoints
			 |* Execute API calls directly from your browser
			 |* View request and response examples
			 |* Test authentication and authorization flows
			 |
			 |### Finding Dynamic Entities
			 |
			 |Dynamic Entities can be found under the **More** list of API Versions. Look for versions starting with `OBPdynamic-entity` or similar in the version selector.
			 |
			 |To programmatically discover all Dynamic Entity endpoints, use: `GET /resource-docs/API_VERSION/obp?content=dynamic`
			 |
			 |For more information about Dynamic Entities see ${getGlossaryItemLink("Dynamic-Entities")}
			 |
			|### Creating Favorites
		|
		|If you click the star icon next to an endpoint, it will be added to your favorites list.
		|
		|Favorites appear in the Collections section in the left panel interface.
		|
		|Note: Favorites are a special type of collection. You can create other collections using endpoints.
"""
  )



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

	glossaryItems += GlossaryItem(
		title = "Virtual Entitlements",
		description =
			s"""A virtual Entitlement is a Role a User holds because their USER_ID is listed in an instance props entry, not because an Entitlement row exists.
				 |
				 |Two props entries grant them:
				 |
				 |* `super_admin_user_ids`: ${APIUtil.superAdminVirtualRoles.mkString(", ")}
				 |* `oidc_operator_user_ids`: ${APIUtil.oidcOperatorVirtualRoles.mkString(", ")}
				 |
				 |Where they appear: `GET /my/entitlements` and `GET /users/current` list them next to stored Entitlements with an empty `entitlement_id` and an empty `bank_id`; in v6.0.0 and later `created_by_process` names the props entry.
				 |
				 |What they do: a virtual Entitlement satisfies the Role check of a direct call exactly as a stored one would. Super admins additionally bypass the granting-Role check of Add Entitlement, so they can grant any Role to any User (including themselves) at any Bank.
				 |
				 |What they do not do: they are not rows, so they cannot be deleted or listed per Bank, and they cannot be delegated. A Consent may only carry stored Entitlements of the User creating it, so a super admin who wants an agent (a consent user) to hold a Role must first grant that Role to their own USER_ID with Add Entitlement, then create the Consent that carries it. The "just in time" grant (`create_just_in_time_entitlements`) likewise honours only stored granting Roles.
				 |
				 |See also [Roles of Open Bank Project](/glossary#Roles-of-Open-Bank-Project) and [Consent](/glossary#Consent).
			""".stripMargin
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
		title = "Adapter",
		description =
			s"""
				 |## Adapter
				 |
				 |In OBP, an Adapter is an out of process component that sits between OBP and a bank's systems of record (Core Banking System, Payment System, or database) and translates between them.
				 |
				 |An Adapter is paired with an Indirect [Connector](/glossary#Connector): the Connector inside OBP turns OBP function calls into messages and sends them over a transport (for example RabbitMQ, Akka, or a stored procedure call); the Adapter receives those messages, talks to the CBS, and returns a response in the agreed Outbound / Inbound message format.
				 |
				 |i.e. OBP API -> Connector -> Adapter -> CBS
				 |
				 |Key properties:
				 |
				 |* It runs outside OBP, in its own process, typically on the bank's side.
				 |* It can be written in any language, as long as it respects the message format published in the Message Docs for the relevant Connector. This is the main advantage over a Direct Connector, which must be written in a JVM language.
				 |* It usually contains bank specific integration code: the data access, field mappings, identifier translation, and quirks of that one bank's CBS. As a result, each bank typically has its own Adapter build.
				 |* The Adapter is responsible for emitting OBP shaped values where required (for example a UUID shaped ACCOUNT_ID mapped to the underlying core banking account number).
				 |
				 |For worked examples of writing an Adapter, see [Adapter.Akka.Intro](/glossary#Adapter.Akka.Intro) and [Adapter.Stored_Procedure.Intro](/glossary#Adapter.Stored_Procedure.Intro).
				 |""".stripMargin
	)

	glossaryItems += GlossaryItem(
		title = "OBP Bank Node",
		description =
			s"""
				 |## OBP Bank Node
				 |
				 |An OBP Bank Node is a standardised software component designed to run at many banks inside their own network that connect to their Core Banking System (CBS) to an OBP API instance operated by a platform operator (for example TESOBE), without the bank having to run any OBP infrastructure itself.
				 |
				 |It is deployed as a single self contained service (typically a Docker container). All of its network connections are outbound from the bank's network (or controlled cloud) and no inbound ports are exposed to the public internet. To the bank's CBS it presents one small local interface (for example few REST endpoints); everything else (talking to the OBP API, and any systems it integrates) happens behind that interface.
				 |
				 |### How it relates to a Connector and an Adapter
				 |
				 |The OBP Bank Node is neither an OBP [Connector](/glossary#Connector) nor a traditional South Side Adapter, although it sits in similar territory. Two properties make the difference:
				 |
				 |* Direction of control. A South Side Adapter is called by an OBP Connector over a message bus: OBP is the caller and the Adapter responds to request messages with CBS data. The OBP Bank Node does the opposite on its north side: it acts as a client of the OBP API, calling OBP's REST interface itself. It both initiates calls to OBP and exposes a local interface to the bank's CBS, rather than only responding.
				 |
				 |* Code versus configuration. A South Side Adapter usually carries a significant amount of bank specific code: the translation logic for one bank's CBS (its data access, field mappings, and integration quirks) is written into the Adapter, so each bank effectively gets its own Adapter build. The OBP Bank Node carries no bank specific code; its per bank behaviour is entirely configuration. Any bank specific code in an integration lives on the bank's own side of the local interface (for example the CBS code that receives the Node's notifications), never inside the Node.
				 |
				 |In short: an OBP Connector is JVM code inside OBP that talks to systems of record; an Adapter is an out of process component that an Indirect Connector calls and that contains bank specific integration code; the OBP Bank Node is a bank side gateway that is configured rather than coded per bank and that acts as a client of the OBP API.
				 |
				 |### Open or closed source
				 |
				 |Because it integrates through the OBP API's published interfaces, vendors can build and run their own implementations.
					|
					|### Use cases
					| The Node approach is suitable when implementing an OBP platform business that involves many banks in a common use case - or where the Platform utilises other interfaces e.g. to blockchains.
					|
				 |""".stripMargin
	)

	glossaryItems += GlossaryItem(
		title = "Connector.User.Authentication",
		description =
			s"""
				 |### Overview
				 |
				 |The property `connector.user.authentication` (default: `false`) controls whether OBP can authenticate a user via the Connector when they are not found locally.
				 |
				 |OBP always checks for users locally first. When this property is enabled and a user is not found locally (or exists but is from an external provider), OBP will attempt to authenticate them against an external identity provider or Core Banking System (CBS) via the Connector.
				 |
				 |### Configuration
				 |
				 |In your props file:
				 |
				 |```
				 |connector.user.authentication=true
				 |```
				 |
				 |### Behavior When Enabled (true)
				 |
				 |**1. Login Authentication Flow:**
				 |
				 |When a user attempts to log in:
				 |
				 |```
				 |User Login Request
				 |       │
				 |       ▼
				 |┌─────────────────────────┐
				 |│ 1. Check if user exists │
				 |│    locally in OBP       │
				 |└───────────┬─────────────┘
				 |            │
				 |   ┌────────┼────────┬─────────────────┐
				 |   │        │        │                 │
				 |   ▼        ▼        ▼                 ▼
				 |Found     Found    Found            Not Found
				 |(local   (external (external        (and property
				 |provider) provider) provider         enabled)
				 |   │      property  property            │
				 |   │      disabled) enabled)            │
				 |   │        │        │                  │
				 |   ▼        ▼        ▼                  ▼
				 |┌────────┐ ┌────┐  ┌─────────────────────────┐
				 |│Check   │ │Fail│  │ 2. Call Connector:      │
				 |│local   │ │    │  │ checkExternalUser       │
				 |│password│ │    │  │ Credentials()           │
				 |└───┬────┘ └────┘  └───────────┬─────────────┘
				 |    │                          │
				 |    ▼                 ┌────────┴────────┐
				 | Success/             │                 │
				 | Failure              ▼                 ▼
				 |                   Success           Failure
				 |                      │                 │
				 |                      ▼                 ▼
				 |               ┌─────────────┐  ┌─────────────┐
				 |               │Create local │  │Increment    │
				 |               │AuthUser if  │  │bad login    │
				 |               │not exists   │  │attempts     │
				 |               └─────────────┘  └─────────────┘
				 |```
				 |
				 |**2. Username Uniqueness Validation:**
				 |
				 |During user signup, OBP checks if the username already exists in the external system by calling `checkExternalUserExists()`.
				 |
				 |**3. Auto Creation of Local Users:**
				 |
				 |If external authentication succeeds but the user doesn't exist locally, OBP automatically creates a local `AuthUser` record linked to the external provider.
				 |
				 |### Behavior When Disabled (false, default)
				 |
				 |* Users must exist locally in OBP's database
				 |* Authentication is performed against locally stored credentials
				 |* No connector calls are made for authentication
				 |
				 |### Required Connector Methods
				 |
				 |When enabled, your Connector must implement:
				 |
				 |* ${messageDocLinkRabbitMQ("obp.checkExternalUserCredentials")} : Validates username and password against external system. Returns `InboundExternalUser` with user details (sub, iss, email, name, userAuthContexts).
				 |
				 |* ${messageDocLinkRabbitMQ("obp.checkExternalUserExists")} : Checks if a username exists in the external system. Used during signup validation.
				 |
				 |### InboundExternalUser Response
				 |
				 |The connector should return user information including:
				 |
				 |* `sub`: Subject identifier (username)
				 |* `iss`: Issuer (provider identifier)
				 |* `email`: User's email address
				 |* `name`: User's display name
				 |* `userAuthContexts`: Optional list of auth contexts (e.g., customer numbers)
				 |
				 |### Use Cases
				 |
				 |**Enable when:**
				 |* You have an external identity provider (LDAP, Active Directory, OAuth provider)
				 |* User credentials are managed by the Core Banking System
				 |* You want single sign on with an existing user directory
				 |
				 |**Disable when:**
				 |* OBP manages all user authentication locally
				 |* You're using OBP's built in user management
				 |* You don't have an external authentication system
				 |
				 |### Related Properties
				 |
				 |* `connector`: Specifies which connector implementation to use
				 |* `connector.user.authcontext.read.in.login`: Read user auth contexts during login
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



	glossaryItems += GlossaryItem(
		title = "API.Endpoint Auth Modes",
		description =
			s"""
|
|Each API endpoint has an **authMode** that determines how Roles are checked when both a User and a Consumer (Application) are present in the request.
|
|The four auth modes are:
|
|* **UserOnly** (default): Only the User's Entitlements are checked. Consumer Scopes are ignored.
|
|* **ApplicationOnly**: Only the Consumer's Scopes are checked. No User is required.
|
|* **UserOrApplication**: Access is granted if the Consumer has the required Scope **OR** the User has the required Entitlement. This effectively gives the union of both.
|
|* **UserAndApplication**: Access is granted only if the Consumer has the required Scope **AND** the User has the required Entitlement. Both are required.
|
|For example, if a User logs in via DirectLogin with a Consumer that has the Scope *CanGetConsumers*, and the endpoint's authMode is *UserOrApplication*, the User can access the endpoint even without a personal *CanGetConsumers* Entitlement (because the Consumer's Scope is sufficient).
|
|The authMode is set in the ResourceDoc definition for each endpoint, for example:
|
|```
|resourceDocs += ResourceDoc(
|  getConsumers,
|  implementedInApiVersion,
|  nameOf(getConsumers),
|  "GET",
|  "/management/consumers",
|  "Get Consumers",
|  ...,
|  Some(List(canGetConsumers)),
|  authMode = UserOrApplication
|)
|```
|
|If authMode is not specified, it defaults to UserOnly.
|
|Note: If the property *require_scopes_for_all_roles* is set to true, all endpoints behave as *UserAndApplication* regardless of their configured authMode.
|
|See also: [Access Control](/index#API.Access-Control), [Scopes](/index#group-Scope), [Roles](/index#group-Role)
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
				 |Consent users (the principal a Consent-JWT authenticates as) never receive Just in Time Entitlements: their Roles come only from the Consent, even if the Consent carries CanCreateEntitlementAtOneBank.
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
		  |An identifier for the account that MUST NOT leak the account number or other identifier normally used by the customer or bank staff.
		  |
		  |### Format
		  |
		  |`account_id` **MUST be a UUID**. The MUST is deliberate: a UUID is effectively globally unique by construction (collision probability ≈ 0), which means `(OBP, account_id)` is a self-contained, federation-safe routing pair without needing to be qualified by the surrounding `bank_id`. Older OBP releases said "SHOULD be a UUID" — the contract has been tightened.
		  |
		  |It MUST also be unique in combination with the BANK_ID (this remains true and is enforced at the database level).
		  |
		  |### Why a UUID
		  |
		  |- ACCOUNT_ID is used in many URLs so it must be considered public; a UUID leaks no information about the account number, customer, or position in any sequence.
		  |- (We do NOT use the human-facing account number in URLs since URLs are cached and logged all over the internet.)
		  |- A UUID also makes the canonical `(OBP, account_id)` self-routing (see `Account.account_routings`) usable across instances without ambiguity.
		  |
		  |### How it is generated
		  |
		  |- In local / sandbox mode, ACCOUNT_ID is generated as a UUID and stored in the database.
		  |- In non-sandbox modes (RabbitMQ, etc.), ACCOUNT_ID is mapped to core-banking account numbers / identifiers at the South-Side Adapter level. The adapter is responsible for emitting a UUID-shaped value.
		  |- ACCOUNT_ID is used to link Metadata and Views, so it MUST be persistent and known to the North Side (OBP-API).
			|
			| Example value: ${accountIdExample.value}
			|
		""")

	  glossaryItems += GlossaryItem(
		title = "Account.account_routings",
		description =
		s"""
		  |A list of routing entries that identify the account on external rails (IBAN, account number, mobile-money MSISDN, etc.) and on OBP itself.
		  |
		  |Each entry has two fields:
		  |
		  |- `scheme` — the name of the routing scheme, e.g. `IBAN`, `BIC`, `AccountNumber`, `OBP`.
		  |- `address` — the address within that scheme, e.g. an IBAN value, an account-number string, or — for the `OBP` scheme — the OBP `account_id`.
		  |
		  |### A note on the "OBP" scheme name
		  |
		  |The implicit self-routing is currently emitted with `scheme: "OBP"`. Read in context — inside an `account_routings` array — this unambiguously means "the address is the OBP `account_id`". Read out of context (a flat routing table, a federation message, a log line), the name `"OBP"` alone does not say whether the address is an account_id or a bank_id.
		  |
		  |The explicit alias `"OBP_ACCOUNT_ID"` is also recognised on input (when storing a routing via the `Create or Update Account Routing` endpoint, or when resolving a counterparty). It is not emitted in responses today, but robust clients should treat `"OBP"` and `"OBP_ACCOUNT_ID"` as equivalent — e.g. by matching case-insensitively against the set `{"OBP", "OBP_ACCOUNT_ID"}` rather than equality with the literal `"OBP"`.
		  |
		  |See also: `Bank.bank_routings` for the analogous bank-level alias `"OBP_BANK_ID"`.
		  |
		  |### Response shape (v6.0.0 onwards)
		  |
		  |For every endpoint that returns `account_routings` (e.g. `getCoreAccountById`, `getPrivateAccountByIdFull`, `getAccountDirectory`, the transaction endpoints), the response is guaranteed to contain:
		  |
		  |1. **Exactly one canonical OBP self-routing** as the first element: `{ "scheme": "OBP", "address": "<account_id>" }`. This means a client can always address the account by its `account_id` without first probing for which routing schemes the bank has configured.
		  |2. **Zero or more stored routings** from the `bankaccountrouting` table — whatever the bank or admin has configured (IBAN, BIC, AccountNumber, country-qualified MSISDN, etc.).
		  |
		  |If a bank has stored an `OBP`-scheme routing whose address diverges from the `account_id`, the response prefers the canonical form (`address = account_id`) — the stored value is dropped to guarantee a single, consistent OBP entry.
		  |
		  |### Example
		  |
		  |```json
		  |"account_routings": [
		  |  { "scheme": "OBP",           "address": "${accountIdExample.value}" },
		  |  { "scheme": "IBAN",          "address": "DE89370400440532013000" },
		  |  { "scheme": "AccountNumber", "address": "12345678" }
		  |]
		  |```
		  |
		  |### Where to set the stored routings
		  |
		  |The non-OBP entries come from the `BankAccountRouting` model — one row per `(BANK_ID, ACCOUNT_ID, scheme)` triple. Use `Create or Update Account Routing` to manage them. Multiple entries per account are supported (e.g. an IBAN plus an MSISDN), and each `(scheme, address)` pair is unique within a bank.
		  |
		  |### Earlier versions
		  |
		  |In versions earlier than v6.0.0 the canonical `OBP` entry was not automatically prepended. A client targeting older versions cannot rely on `OBP` being present unless the bank/admin explicitly stored it. Migrating to v6.0.0+ simplifies routing logic since the OBP self-routing is always available.
		  |
		  |See also: `Bank.bank_routings` for the analogous bank-level field.
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
|For example see [Bank/Space level Dynamic Entities](/?version=OBPv4.0.0&operation_id=OBPv4_0_0-createBankLevelDynamicEntity) and [Bank/Space level Dynamic Endpoints](http://localhost:5174/?version=OBPv4.0.0&operation_id=OBPv4_0_0-createBankLevelDynamicEndpoint)
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
		  |### Format
		  |
		  |`bank_id` **SHOULD be of the form `<human-friendly>-<UUID>`** — a short, readable prefix that names the institution, followed by a hyphen and a UUID. The human-friendly prefix preserves scannability in URLs and logs; the UUID suffix guarantees global uniqueness across OBP instances and federations.
		  |
		  |Examples:
		  |
		  |- `bisb-7f3a9c2b-1d4e-4b6a-9c0f-5e2d1a3b8c0d`
		  |- `bnpp-irb-it-01-2a3b...c4d5`
		  |
		  |It SHOULD NOT contain spaces. It MUST be unique on the OBP-API instance (enforced at the database level) and SHOULD be globally unique across all OBP instances (achieved by the UUID suffix).
		  |
		  |### Earlier conventions
		  |
		  |Older OBP releases used purely human-friendly identifiers like `bnpp-irb.01.it.it` (sandbox convention: `financialinstitution.sequence.region.language`) or the institution's BIC. Existing bank_ids in production will not be renamed retroactively — the new convention applies to **newly created banks** going forward. Federation logic must therefore handle both shapes (with and without UUID suffix) indefinitely.
		  |
			|Example value: ${bankIdExample.value}
			|
			|## Version history
			|
			|The JSON field name for this identifier changed across OBP-API versions:
			|
			|- **v6.0.0+** (current): `bank_id` — the canonical field name in both request and response bodies (e.g. `PostBankJson600`, `BankJson600`).
			|- **v5.0.0**: `id` (Option[String]) — see `PostBankJson500` / `BankJson500`.
			|- **v4.0.0**: `id` (String), plus a now-removed `short_name` field — see `PostBankJson400` / `BankJson400`.
			|
			|The v6 createBank request body shape is exactly:
			|`bank_id`, `bank_code`, `full_name`, `logo`, `website`, `bank_routings`.
			|
			|If you're regenerating client code from older docs, samples, or LLM training data, double-check
			|the field name — sending `id` to v6 endpoints will silently produce an empty `bank_id` and
			|fail validation with a confusing length error.
		 """)

	  glossaryItems += GlossaryItem(
		title = "Bank.bank_routings",
		description =
		s"""
		  |A list of routing entries that identify the bank on external rails (BIC/SWIFT, national bank codes, etc.) and on OBP itself.
		  |
		  |Each entry has two fields:
		  |
		  |- `scheme` — the name of the routing scheme, e.g. `BIC`, `bankCode`, `BLZ`, `FRENCH_NCC`, `OBP`.
		  |- `address` — the address within that scheme, e.g. a BIC value, a national bank code, or — for the `OBP` scheme — the OBP `bank_id`.
		  |
		  |### A note on the "OBP" scheme name
		  |
		  |The implicit self-routing is currently emitted with `scheme: "OBP"`. Read in context — inside a `bank_routings` array — this unambiguously means "the address is the OBP `bank_id`". Read out of context (a flat routing table, a federation message, a log line), the name `"OBP"` alone does not say whether the address is a bank_id or an account_id.
		  |
		  |The explicit alias `"OBP_BANK_ID"` is also recognised on input. It is not emitted in responses today, but robust clients should treat `"OBP"` and `"OBP_BANK_ID"` as equivalent — e.g. by matching case-insensitively against the set `{"OBP", "OBP_BANK_ID"}` rather than equality with the literal `"OBP"`.
		  |
		  |See also: `Account.account_routings` for the analogous account-level alias `"OBP_ACCOUNT_ID"`.
		  |
		  |### Response shape (v6.0.0 onwards)
		  |
		  |For every endpoint that returns `bank_routings` (e.g. `getBank`, `getBanks`, `createBank`), the response is guaranteed to contain:
		  |
		  |1. **Exactly one canonical OBP self-routing** as the first element: `{ "scheme": "OBP", "address": "<bank_id>" }`. This means a client can always address the bank by its `bank_id` regardless of which other schemes have been registered.
		  |2. **A BIC entry**, derived from the bank's dedicated SWIFT/BIC column (`swiftBic`), if non-empty. If the explicit stored routing is itself a BIC, only one BIC entry appears — duplicates are removed.
		  |3. **The explicit stored routing** (the legacy single `(bankRoutingScheme, bankRoutingAddress)` column pair), unless it is an `OBP` or `BIC` entry already covered above.
		  |
		  |If a bank has stored an `OBP`-scheme routing whose address diverges from the `bank_id`, the response prefers the canonical form (`address = bank_id`) — the stored value is dropped to guarantee a single, consistent OBP entry.
		  |
		  |Entries with an empty/null address are filtered out (e.g. if a bank has no BIC, the implicit BIC entry is dropped rather than emitted as a null).
		  |
		  |### Example
		  |
		  |```json
		  |"bank_routings": [
		  |  { "scheme": "OBP", "address": "${bankIdExample.value}" },
		  |  { "scheme": "BIC", "address": "BARCGB22" },
		  |  { "scheme": "BLZ", "address": "10010010" }
		  |]
		  |```
		  |
		  |### Earlier versions
		  |
		  |In versions earlier than v6.0.0 the canonical `OBP` entry was not automatically prepended. A client targeting older versions cannot rely on `OBP` being present unless explicitly stored. Migrating to v6.0.0+ simplifies routing logic since the OBP self-routing is always available.
		  |
		  |See also: `Account.account_routings` for the analogous account-level field.
		  |
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
			 |The name `consumer_key` is historical (it originated in OAuth 1.0a, which is no longer supported by OBP). The OAuth 2.0 counterpart for this value is `client_id`, and the two are used interchangeably.
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
		title = "Password Policy",
		description =
		  s"""
			|The rules a password must satisfy when it is set — at user creation (POST /users) and at password reset.
			|
			|A password is valid if it satisfies AT LEAST ONE of the following policies:
			|
			|1) **Composition**: 10 to 16 printable ASCII characters (no space), including at least one digit, one lower case letter, one upper case letter and one special character.
			|
			|2) **Passphrase**: 17 to 512 printable ASCII characters (no space), with no composition rules.
			|
			|The machine-readable policy is published anonymously at `GET /obp/v7.0.0/public/password-config`, including per-policy length bounds, required character classes, allowed characters, and an equivalent regular expression written in a portable subset that behaves identically in Java, JavaScript and Python — so client applications can validate locally, while the user types, using either the structured fields (normative) or the regex (convenience):
			|
			|Composition: `${APIUtil.passwordCompositionPolicyRegex}`
			|
			|Passphrase: `${APIUtil.passwordPassphrasePolicyRegex}`
			|
			|The server remains the final enforcer: a password failing the policy is rejected with error OBP-30207 (InvalidStrongPasswordFormat).
			|
			|The policy applies only when a password is set. Already-stored passwords are never re-checked against it, so tightening the policy does not lock out existing users.
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
|**What an OBP Consent carries**
|
|| key | nature | check when the Consent is created |
||---|---|---|
|| `views` | the User's own account access (owned) | the User has the view |
|| `entitlements` | Roles at a Bank or the system (granted) | the User holds the stored Entitlement; virtual Entitlements do not count |
|| `my_resources` | the User's own personal resources (owned), one typed list per kind, e.g. `personal_dynamic_entities` | the kind and instance exist; no Role, the User owns these rows |
|
|`my_resources` is accepted by the Create Consent endpoint from v6.0.0 (older create-consent bodies are frozen). Example: `{"personal_dynamic_entities": [{"bank_id": "", "entity_name": "FooBar", "actions": ["read", "write"]}]}`. An entry names what the consent user may act on for the granting User; rows it writes belong to that User. Absent or empty means none, and `everything: true` does not include it. See ${getGlossaryItemLink("Dynamic-Entity-Access-Model")}.
|
|
|
				|See ${getGlossaryItemLink("Consent_OBP_Flow_Example")} for an example flow.
				|See ${getGlossaryItemLink("Consent_Account_Onboarding")} for more information about onboarding.
|
				|<img width="468" alt="OBP Access Control Image" src="$getServerUrl/media/images/glossary/OBP_Consent_Request__3_.png"></img>
				|""".stripMargin)


	glossaryItems += GlossaryItem(
		title = "Authentication: Consent OBP Flow Example",
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
					|      "role_name": "CanGetCustomersAtOneBank"
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
					|      "role_name":"CanGetCustomersAtOneBank"
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
|Direct Login is built into OBP and used for testing purposes / local installations.
|
|OAuth2 / Open ID Connect (OIDC) is the recommended method for production use, and depends on the configuration of Identity Provider solutions such as Keycloak or OBP-OIDC or external services such as Google or Yahoo.
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



	// Direct Login documentation is sourced from OpenAPI31JSONFactory (the source of truth for auth docs)
	glossaryItems += GlossaryItem(
		title = "Authentication: Direct Login",
		description = OpenAPI31JSONFactory.directLoginDescription(getServerUrl)
	)


	// OAuth2 / OIDC Client Credentials documentation is sourced from OpenAPI31JSONFactory (the source of truth for auth docs)
	glossaryItems += GlossaryItem(
		title = "Authentication: OAuth2 / OIDC Client Credentials",
		description = OpenAPI31JSONFactory.oAuth2Description(getServerUrl)
	)


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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			 |	$directLoginHeaderName: token="your-token"
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
			 |	$directLoginHeaderName: token="your-token"
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
			|	$directLoginHeaderName: token="your-token"
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
			|	$directLoginHeaderName: token="your-token"
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
			|	$directLoginHeaderName: token="your-token"
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
			|	$directLoginHeaderName: token="your-token"
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
			|	$directLoginHeaderName: token="your-token"
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
			|	$directLoginHeaderName: token="your-token"
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
			|	$directLoginHeaderName: token="your-token"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
			|	$directLoginHeaderName: token="your-token-from-direct-login"
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
|	{  "everything":false,  "views":[{    "bank_id":"gh.29.uk",    "account_id":"8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",    "view_id":${Constant.SYSTEM_OWNER_VIEW_ID}],  "entitlements":[{    "bank_id":"gh.29.uk",    "role_name":"CanGetCustomersAtOneBank"  }],  "consumer_id":"7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",  "phone_number":"+44 07972 444 876",  "valid_from":"2022-04-29T10:40:03Z",  "time_to_live":3600}
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

  val oauth2EnabledMessage : String = if (APIUtil.getPropsAsBoolValue("allow_oauth2_login", true))
		{"OAuth2 is allowed on this instance."} else {"Note: *OAuth2 is NOT allowed on this instance!*"}

	// OAuth2 documentation is sourced from OpenAPI31JSONFactory (the source of truth for auth docs)
    glossaryItems += GlossaryItem(
      title = "Authentication: OAuth 2",
      description = s"""
        |$oauth2EnabledMessage
        |
        |${OpenAPI31JSONFactory.oAuth2Description(getServerUrl)}
        |
        |<img src="https://static.openbankproject.com/images/OBP-OAuth2-flow.png" width="885"></img>
        |
        |An example Consent Testing App (Hola) using this flow can be found [here](https://github.com/OpenBankProject/OBP-Hola)
			""".stripMargin)






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
|		Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IjA4ZDMyNDVjNjJmODZiNjM2MmFmY2JiZmZlMWQwNjk4MjZkZDFkYzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJlbWFpbCI6Im1hcmtvLm1pbGljLnNyYmlqYUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6IkFvYVNGQTlVTTdCSGg3YWZYNGp2TmciLCJuYW1lIjoiTWFya28gTWlsacSHIiwicGljdHVyZSI6Imh0dHBzOi8vbGg1Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tWGQ0NGhuSjZURG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFBQUEvQUt4cndjYWR3emhtNE40dFdrNUU4QXZ4aS1aSzZrczRxZy9zOTYtYy9waG90by5qcGciLCJnaXZlbl9uYW1lIjoiTWFya28iLCJmYW1pbHlfbmFtZSI6Ik1pbGnEhyIsImxvY2FsZSI6ImVuIiwiaWF0IjoxNTQ3NzExMTE1LCJleHAiOjE1NDc3MTQ3MTV9.MKsyecCSKS4Y0C8R4JP0J0d2Oa-xahvMAbtfFrGHncTm8xBgeaNb50XSJn20ak1YyA8hZiRP2M3el0f4eIVQZsMMa22MrwaiL8pLb1zGfawDLPb1RvOmoCWTDJGc_s1qQMlyc21Wenr9rjuu1bQCerGTYM6M0Aq-Uu_GT0lCEjz5WVDI5xDUf4Mhdi8HYq7UQ1kGz1gQFiBm5nI3_xtYm75EfXFeDg3TejaMmy36NpgtwN_vwpHByoHE5BoTl2J55rJ2creZZ7CmtZttm-9HsT6v1vxT8zi0RXObFrZSk-LgfF0tJQcGZ5LXQZL0yMKXPQVFIMCg8J0Gg7l_QACkCA
|		Cache-Control: no-cache
|
 |
|
 |CURL example:
|
 |
|		curl -X GET
|		$getServerUrl/obp/v3.0.0/users/current
|		-H 'Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IjA4ZDMyNDVjNjJmODZiNjM2MmFmY2JiZmZlMWQwNjk4MjZkZDFkYzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI0MDc0MDg3MTgxOTIuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTM5NjY4NTQyNDU3ODA4OTI5NTkiLCJlbWFpbCI6Im1hcmtvLm1pbGljLnNyYmlqYUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6IkFvYVNGQTlVTTdCSGg3YWZYNGp2TmciLCJuYW1lIjoiTWFya28gTWlsacSHIiwicGljdHVyZSI6Imh0dHBzOi8vbGg1Lmdvb2dsZXVzZXJjb250ZW50LmNvbS8tWGQ0NGhuSjZURG8vQUFBQUFBQUFBQUkvQUFBQUFBQUFBQUEvQUt4cndjYWR3emhtNE40dFdrNUU4QXZ4aS1aSzZrczRxZy9zOTYtYy9waG90by5qcGciLCJnaXZlbl9uYW1lIjoiTWFya28iLCJmYW1pbHlfbmFtZSI6Ik1pbGnEhyIsImxvY2FsZSI6ImVuIiwiaWF0IjoxNTQ3NzExMTE1LCJleHAiOjE1NDc3MTQ3MTV9.MKsyecCSKS4Y0C8R4JP0J0d2Oa-xahvMAbtfFrGHncTm8xBgeaNb50XSJn20ak1YyA8hZiRP2M3el0f4eIVQZsMMa22MrwaiL8pLb1zGfawDLPb1RvOmoCWTDJGc_s1qQMlyc21Wenr9rjuu1bQCerGTYM6M0Aq-Uu_GT0lCEjz5WVDI5xDUf4Mhdi8HYq7UQ1kGz1gQFiBm5nI3_xtYm75EfXFeDg3TejaMmy36NpgtwN_vwpHByoHE5BoTl2J55rJ2creZZ7CmtZttm-9HsT6v1vxT8zi0RXObFrZSk-LgfF0tJQcGZ5LXQZL0yMKXPQVFIMCg8J0Gg7l_QACkCA'
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


	// Gateway Login core documentation is sourced from OpenAPI31JSONFactory (the source of truth for auth docs)
	// Additional operational/admin details are Glossary-specific below.
	glossaryItems += GlossaryItem(
		title = "Authentication: Gateway Login",
		description =
			s"""
|$gatewayLoginEnabledMessage
|
|${OpenAPI31JSONFactory.gatewayLoginDescription(getServerUrl)}
|
|![obp login via gateway and jwt](https://user-images.githubusercontent.com/485218/32783397-e39620ee-c94b-11e7-92e3-b244b8e841dd.png)
|
|---
|
|### Administrator Guide: Configuration and JWT Details
|
|The **Gateway is responsible** for creating a token which is trusted by OBP **absolutely**.
|When OBP receives a token via Gateway Login, OBP creates or gets a user based on the username supplied.
|
|### 1) Configure OBP API to accept Gateway Login
|
|Set up properties in a props file:
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
|
|The property jwt_token_secret is used to validate the JWT token to check it is not changed or corrupted during transport.
|
|### 2) JWT Structure
|
|HEADER:
|
|```
|{
|  "alg": "HS256",
|  "typ": "JWT"
|}
|```
|
|PAYLOAD:
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
|
|### Example python script
|
|```
|import jwt
|from datetime import datetime, timezone
|import requests
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
|token = jwt.encode(payload, 'your-at-least-256-bit-secret-token', algorithm='HS256')
|authorization = 'GatewayLogin token="{}"'.format(token)
|headers = {'Authorization': authorization}
|url = obp_api_host + '/obp/v6.0.0/users/current'
|req = requests.get(url, headers=headers)
|print(req.text)
|```
|
|### Under the hood
|
|The file GatewayLogin.scala handles the Gateway Login:
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
|Register your App / Consumer [HERE](${getConsumerRegistrationUrl()})
|
|Be sure to enter your Client Certificate in the registration form. To create the user.crt file see [HERE](https://fardog.io/blog/2017/12/30/client-side-certificate-authentication-with-nginx/)
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
		title = "Berlin Group Mandatory Headers",
		description =
			s"""
|OBP validates mandatory HTTP request headers for Berlin Group (NextGenPSD2) API endpoints.
|
|When a request targets a Berlin Group endpoint (identified by the Berlin Group URL prefix), OBP checks for the presence of required headers before processing the request.
|
|## Mandatory Headers
|
|The following headers are required on all Berlin Group API requests by default:
|
|* **Content-Type** - The media type of the request body
|* **Date** - The date and time of the request (must be a valid RFC 7231 date)
|* **Digest** - A digest of the request body for integrity verification
|* **PSU-Device-ID** - UUID of the device used by the Payment Service User (PSU)
|* **PSU-Device-Name** - Name of the device used by the PSU
|* **PSU-IP-Address** - IP address of the PSU device
|* **Signature** - Digital signature of the request (keyId must match the serial number from the TPP certificate)
|* **TPP-Signature-Certificate** - The certificate used by the TPP to sign the request
|* **X-Request-ID** - UUID that uniquely identifies the request (must not be reused for POST requests that returned 201)
|
|## Additional Consent Headers
|
|When creating a consent (POST to /consents), the following additional header is required:
|
|* **TPP-Redirect-URI** - URI to redirect the PSU to after consent authorization
|
|## TPP Requests Without PSU Involvement
|
|For background/batch requests where no PSU is directly involved, set:
|
|* PSU-IP-Address: 0.0.0.0
|* PSU-Device-ID: no-psu-involved
|* PSU-Device-Name: no-psu-involved
|
|This enables OBP to apply different consent frequency rules for TPP-initiated requests.
|
|## Configuration
|
|The mandatory headers can be customized in the Props file:
|
|* `berlin_group_mandatory_headers` - Comma-separated list of mandatory header names. Set to empty to disable header checks.
|* `berlin_group_mandatory_header_consent` - Additional headers required for consent creation endpoints.
|
|Example Props configuration:
|
|    # Use default mandatory headers
|    #berlin_group_mandatory_headers = Content-Type,Date,Digest,PSU-Device-ID,PSU-Device-Name,PSU-IP-Address,Signature,TPP-Signature-Certificate,X-Request-ID
|    #berlin_group_mandatory_header_consent = TPP-Redirect-URI
|
|    # Disable mandatory header checks (e.g. for testing)
|    berlin_group_mandatory_headers =
|    berlin_group_mandatory_header_consent =
|
|## Validation Chain
|
|OBP performs the following validation steps on Berlin Group requests in order:
|
|1. **Missing headers check** - Returns HTTP 400 if any mandatory headers are absent
|2. **Date format check** - Validates the Date header conforms to RFC 7231
|3. **X-Request-ID format check** - Validates the X-Request-ID is a valid UUID
|4. **X-Request-ID uniqueness check** - Ensures the X-Request-ID has not been used in a previous successful POST (201) request
|5. **Signature header check** - Parses the Signature header and verifies the keyId serial number matches the TPP certificate
|6. **Consent-ID usage check** - Ensures the Consent-ID header is not sent on consent management endpoints where it is not expected
|
|If any check fails, OBP returns an appropriate error message (OBP-20251 through OBP-20256) with HTTP status 400.
|
 """)


	glossaryItems += GlossaryItem(
		title = "Berlin Group Transaction and Consent Lifecycle",
		description =
			s"""
|OBP provides background schedulers that automatically manage the lifecycle of Berlin Group transactions and consents.
|
|## Outdated Transactions
|
|Berlin Group payment transactions with status "received" (RCVD) that remain unprocessed beyond a configured time threshold are automatically rejected by a background scheduler task.
|
|* `berlin_group_outdated_transactions_time_in_seconds` - Time in seconds after which a "received" transaction is considered outdated. Default: **300** (5 minutes).
|* `berlin_group_outdated_transactions_interval_in_seconds` - How often (in seconds) the scheduler checks for outdated transactions. Must be set to a value greater than 0 to enable the task. **Not set by default** (task is disabled).
|
|Example:
|
|    # Reject transactions stuck in "received" status for more than 5 minutes, checking every 60 seconds
|    berlin_group_outdated_transactions_time_in_seconds = 300
|    berlin_group_outdated_transactions_interval_in_seconds = 60
|
|## Outdated Consents
|
|Berlin Group consents with status "received" that remain unfinished (e.g. the PSU never completed the SCA flow) beyond a configured time threshold are automatically rejected.
|
|* `berlin_group_outdated_consents_time_in_seconds` - Time in seconds after which an unfinished consent is considered outdated. Default: **300** (5 minutes).
|* `berlin_group_outdated_consents_interval_in_seconds` - How often (in seconds) the scheduler checks for outdated consents. Default: **599**. Set to 0 to disable.
|
|Example:
|
|    # Reject consents stuck in "received" status for more than 5 minutes, checking every 60 seconds
|    berlin_group_outdated_consents_time_in_seconds = 300
|    berlin_group_outdated_consents_interval_in_seconds = 60
|
|## Expired Consents
|
|Berlin Group consents with status "valid" whose `validUntil` date has passed are automatically transitioned to "expired" status.
|OBP consents with status "ACCEPTED" whose `validUntil` date has passed are automatically transitioned to "EXPIRED" status.
|
|* `berlin_group_expired_consents_interval_in_seconds` - How often (in seconds) the scheduler checks for expired Berlin Group consents. Default: **597**. Set to 0 to disable.
|* `obp_expired_consents_interval_in_seconds` - How often (in seconds) the scheduler checks for expired OBP consents. Default: **595**. Set to 0 to disable.
|
|Example:
|
|    # Check for expired consents every 120 seconds
|    berlin_group_expired_consents_interval_in_seconds = 120
|    obp_expired_consents_interval_in_seconds = 120
|
 """)


	glossaryItems += GlossaryItem(
		title = "Berlin Group URL and Path Configuration",
		description =
			s"""
|OBP allows customization of the URL paths used for Berlin Group (NextGenPSD2) API endpoints.
|
|## Canonical Path
|
|* `berlin_group_version_1_canonical_path` - Overrides the version segment of the Berlin Group v1 URL path. By default, the built-in path is `v1.3` (i.e. endpoints are served at `/berlin-group/v1.3/...`). Setting this property changes the version segment.
|
|Example:
|
|    # Serve Berlin Group endpoints at /berlin-group/v1.3.12/...
|    berlin_group_version_1_canonical_path = v1.3.12
|
|## Alias Path
|
|* `berlin_group_v1_3_alias_path` - Defines an alternative URL prefix under which Berlin Group v1.3 endpoints are also available. The format must be `xxx/yyy`. When set, all Berlin Group v1.3 endpoints are duplicated under this alternative path.
|
|Example:
|
|    # Also serve Berlin Group endpoints at /0.6/v1/...
|    berlin_group_v1_3_alias_path = 0.6/v1
|
 """)


	glossaryItems += GlossaryItem(
		title = "Berlin Group Response Formatting",
		description =
			s"""
|OBP provides several configuration options to control how Berlin Group API responses are formatted.
|
|## Account Name Visibility
|
|* `BG_v1312_show_account_name` - Boolean flag that controls whether the `name` field is included in Berlin Group account responses (at `/berlin-group/v1.3/accounts` and `/berlin-group/v1.3/accounts/{accountId}`). Default: **true**.
|
|Some implementations may require omitting the account name for privacy or compliance reasons.
|
|Example:
|
|    # Hide account names in Berlin Group responses
|    BG_v1312_show_account_name = false
|
|## Amount Sign Removal
|
|* `BG_remove_sign_of_amounts` - Boolean flag that controls whether the sign (positive/negative indicator) is removed from transaction amount values in Berlin Group responses. Default: **false**.
|
|When enabled, amounts such as "-100.00" are returned as "100.00". This can be useful when the sign is conveyed by other means (e.g. booked vs pending lists, or credit/debit indicators).
|
|Example:
|
|    # Remove the sign from transaction amounts
|    BG_remove_sign_of_amounts = true
|
|## Error Message Path Visibility
|
|* `berlin_group_error_message_show_path` - Boolean flag that controls whether the request URL path is included in Berlin Group error response messages. Default: **true**.
|
|When enabled, error responses include the `path` field showing which URL triggered the error. This can be disabled for privacy or security reasons.
|
|Example:
|
|    # Hide the request path in error messages
|    berlin_group_error_message_show_path = false
|
 """)


	glossaryItems += GlossaryItem(
		title = "Berlin Group Consent Settings",
		description =
			s"""
|OBP provides configuration options for Berlin Group consent creation and SCA (Strong Customer Authentication) flows.
|
|## Frequency Per Day Limit
|
|* `berlin_group_frequency_per_day_upper_limit` - Maximum allowed value for the `frequencyPerDay` field when creating a Berlin Group consent. Default: **4**.
|
|When a TPP creates a consent, the requested `frequencyPerDay` must be greater than 0 and less than or equal to this upper limit. For one-off access consents, the frequency must be exactly 1.
|
|Example:
|
|    # Allow up to 10 requests per day per consent
|    berlin_group_frequency_per_day_upper_limit = 10
|
|## ASPSP SCA Approach
|
|* `berlin_group_aspsp_sca_approach` - Defines the SCA approach advertised by the ASPSP (Account Servicing Payment Service Provider) in the `ASPSP-SCA-Approach` response header for consent creation endpoints. Default: **redirect**.
|
|Possible values include:
|
|* `redirect` - The PSU is redirected to the ASPSP for authentication
|* `embedded` - Authentication is performed within the TPP interface
|* `decoupled` - Authentication is performed on a separate device/channel
|
|This header is returned in the response to POST `/consents` requests to inform the TPP which SCA method the ASPSP supports.
|
|Example:
|
|    # Use embedded SCA approach
|    berlin_group_aspsp_sca_approach = embedded
|
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
		title = "API Product Subscription",
		description = s"""An API Product Subscription records that one Consumer (the subscriber) holds one API Product for a period, with a status.
|
|The API Product describes the plan: which endpoints (its API Collection), how many calls (six rate limits), the monthly price, and any attributes. The Subscription is the record of who holds it. Its status is what makes the product enforceable:
|
|- `requested`: created, nothing granted yet.
|- `active`: OBP-API has given the Consumer a rate limit record with the product's six limits, and a Scope for each Role required by the endpoints in the product's Collection.
|- `past_due`: payment is overdue. A grace period; nothing changes for the Consumer.
|- `suspended`: the subscription's rate limit record is set to `0` in every period, which blocks the Consumer's calls. Scopes are kept so reinstatement is cheap.
|- `cancelled`: the rate limit record and the derived Scopes are removed. Terminal; a new subscription is a new record.
|
|Only the rate limit record and the Scopes created by the subscription are touched. Limits and Scopes granted by hand are never removed. Overlapping rate limit records are summed, so a Consumer holding two products gets both allowances.
|
|A developer never needs a Role to subscribe their own Consumer, read their own subscriptions or cancel them. Roles exist for bank staff (enrol a partner's Consumer, approve, suspend, reinstate) and for billing systems (move the status on payment events). Two attributes on the API Product decide the flow: `SELF_SUBSCRIBE` (may developers subscribe their own Consumers; default `true`) and `BILLING_SYSTEM` (`none` activates at once; `manual` waits for a bank admin; `stripe` or `invoice_ninja` waits for that billing system).
|
|OBP-API core carries no billing vocabulary: payments, invoices and refunds live in the billing system, which only ever changes the subscription status.
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
		title = "Dynamic-Entity-Intro",
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
		title = "Dynamic-Entities",
		description =
			s"""
|
|Dynamic Entities allow you to create custom data structures and their corresponding CRUD endpoints at runtime without writing code or restarting the OBP-API instance.
|
|**Overview:**
|
|Dynamic Entities enable you to define custom business objects (entities) with their fields, types, and validation rules via API calls. Once created, OBP automatically generates fully functional REST API endpoints for Create, Read, Update, and Delete operations.
|
|**Types of Dynamic Entities:**
|
|1. **System Level Dynamic Entities** - Available across the entire OBP instance
|2. **Bank Level Dynamic Entities** - Scoped to a specific bank
|
|**Creating a Dynamic Entity:**
|
|```json
|POST /management/system-dynamic-entities
|{
|  "hasPersonalEntity": true,
|  "CustomerPreferences": {
|    "description": "Customer preferences and settings",
|    "required": ["theme"],
|    "properties": {
|      "theme": {
|        "type": "string",
|        "example": "dark"
|      },
|      "language": {
|        "type": "string",
|        "example": "en"
|      },
|      "notifications_enabled": {
|        "type": "boolean",
|        "example": "true"
|      }
|    }
|  }
|}
|```
|
|**IMPORTANT - JSON Structure:**
|
|The entity name (e.g., "CustomerPreferences") MUST be a direct top-level key in the JSON. Besides the entity name, the root object may only contain the access flags: "hasPersonalEntity", "personalRequiresRole", "hasPublicAccess", "hasCommunityAccess", "useRowLevelAccess" and "authMode" (see ${getGlossaryItemLink("Dynamic-Entity-Access-Model")}).
|
|**Common mistake - DO NOT do this:**
|```json
|{
|  "entity": {
|    "CustomerPreferences": { ... }
|  }
|}
|```
|This will fail with error: "There must be 'required' field in entity"
|
|**Supported field types:**
|
|STRING, INTEGER, DOUBLE, BOOLEAN, DATE_WITH_DAY (format: yyyy-MM-dd), JSON (objects and arrays), and reference types (foreign keys)
|
|**The hasPersonalEntity flag:**
|
|When **hasPersonalEntity = true** (default):
|
|OBP generates TWO sets of endpoints:
|
|1. **Regular endpoints** - Access all entities (requires specific roles)
|   * POST /CustomerPreferences
|   * GET /CustomerPreferences
|   * GET /CustomerPreferences/ID
|   * PUT /CustomerPreferences/ID
|   * DELETE /CustomerPreferences/ID
|
|2. **Personal 'my' endpoints** - User-scoped access (see ${getGlossaryItemLink("My-Dynamic-Entities")})
|   * POST /my/CustomerPreferences
|   * GET /my/CustomerPreferences
|   * GET /my/CustomerPreferences/ID
|   * PUT /my/CustomerPreferences/ID
|   * DELETE /my/CustomerPreferences/ID
|
|When **hasPersonalEntity = false**:
|
|OBP generates ONLY the regular endpoints. No 'my' endpoints are created. Use this when the entity represents shared data that should not be user-scoped.
|
|**Data Storage Differences:**
|
|Both personal and non-personal entities use the same database table (DynamicData), but the key difference is how user ownership is handled:
|
|When **hasPersonalEntity = true**:
|
|* Each record stores the UserId of the user who created it
|* The UserId is **actively used in all queries** to filter results
|* Users can only see, update, and delete their own records via 'my' endpoints
|* The 'my' endpoints **skip role checks** - user isolation provides the authorization
|* Cascade delete (deleting the entity definition and all data at once) is **not allowed**
|
|When **hasPersonalEntity = false**:
|
|* UserId may be stored for audit purposes but is **ignored in queries**
|* All authorized users see the same shared data
|* Role-based authorization is **required** (e.g., CanGetDynamicEntity_FooBar)
|* Cascade delete **is allowed** - you can delete the entity definition and all its records in one operation
|
|**Summary table:**
|
|| Feature | hasPersonalEntity=true | hasPersonalEntity=false |
||---------|------------------------|-------------------------|
|| Data visibility | Per-user (isolated) | Shared (all users) |
|| UserId in queries | Yes (filters results) | No (ignored) |
|| 'my' endpoints | Generated | Not generated |
|| Authorization | User-scoped (no roles needed for 'my' endpoints) | Role-based |
|| Cascade delete | Blocked | Allowed |
|
|**For bank-level entities**, endpoints include the bank ID:
|
|* POST /banks/BANK_ID/CustomerPreferences
|* POST /banks/BANK_ID/my/CustomerPreferences (if hasPersonalEntity = true)
|
|**Auto-generated roles:**
|
|When you create a Dynamic Entity named 'FooBar', OBP automatically creates these roles:
|
|* CanCreateDynamicEntity_FooBar
|* CanUpdateDynamicEntity_FooBar
|* CanGetDynamicEntity_FooBar
|* CanDeleteDynamicEntity_FooBar
|
|**Field-level write/read permissions (per property):**
|
|Each property in the schema can optionally restrict who may write or read that field, independently of the entity-level roles above:
|
|* `write_role_required` (boolean) or `write_role` (explicit role name) — the field becomes **write-restricted**: it cannot be set via POST or PUT (its existing value is preserved), only via **PATCH** by a caller holding the field's write role.
|* `read_role_required` (boolean) or `read_role` (explicit role name) — the field becomes **read-restricted**: it is omitted from GET responses unless the caller holds the field's read role (public/anonymous access omits it entirely).
|
|Restriction is on if either the boolean is `true` or an explicit role name is given. When a boolean is used, OBP auto-generates the role; e.g. for entity 'FooBar' field 'owner':
|
|* CanWriteDynamicEntityField_FooBar__owner (bank level) / CanWriteDynamicEntityField_SystemFooBar__owner (system level)
|* CanGetDynamicEntityField_FooBar__owner / CanGetDynamicEntityField_SystemFooBar__owner
|
|Naming an explicit `write_role`/`read_role` lets several fields (even across entities) share a single role — useful for a privileged service (e.g. an indexer) that maintains many fields. Typical use: a field written only by a verifier/service or projected from an external system, but read by ordinary consumers.
|
|**Management endpoints:**
|
|* POST /management/system-dynamic-entities - Create system level entity
|* POST /management/banks/BANK_ID/dynamic-entities - Create bank level entity
|* GET /management/system-dynamic-entities - List all system level entities
|* GET /management/banks/BANK_ID/dynamic-entities - List bank level entities
|* PUT /management/system-dynamic-entities/DYNAMIC_ENTITY_ID - Update entity definition
|* DELETE /management/system-dynamic-entities/DYNAMIC_ENTITY_ID - Delete entity (and all its data)
|
|**Discovering Dynamic Entity Endpoints (for application developers):**
|
|Once Dynamic Entities are created, their auto-generated CRUD endpoints are documented in the Resource Docs API. To programmatically discover all available Dynamic Entity endpoints, use:
|
|```
|GET /resource-docs/API_VERSION/obp?content=dynamic
|```
|
|For example: `GET /resource-docs/v5.1.0/obp?content=dynamic`
|
|This returns documentation for all dynamic endpoints (both Dynamic Entities and Dynamic Endpoints) including:
|
|* Endpoint paths and HTTP methods
|* Request and response schemas with examples
|* Required roles and authentication
|* Field descriptions and types
|
|You can also get this documentation in OpenAPI/Swagger format for code generation and API client tooling.
|
|**Required roles to manage Dynamic Entities:**
|
|* CanCreateSystemLevelDynamicEntity
|* CanCreateBankLevelDynamicEntity
|
|**Use cases:**
|
|* Customer preferences and settings
|* Custom metadata for accounts or transactions
|* Business-specific data structures
|* Rapid prototyping of new features
|* Extension of core banking data model
|
|For user-scoped Dynamic Entities, see ${getGlossaryItemLink("My-Dynamic-Entities")}
|
|For more detailed information about managing Dynamic Entities, see ${getGlossaryItemLink("Dynamic-Entity-Intro")}
|
|---
|
|## Querying the list (GET) endpoint: filter, sort, paginate and one-hop joins
|
|The "Get ... List" endpoint of every Dynamic Entity accepts declarative query parameters:
|
|* **Filter**: `?obp_filter[FIELD]=OP:VALUE`. Operators: `eq`, `ne`, `in`, `lt`, `gt`, `le`, `ge`, `between`, `like`, `is_null`, `not_set`. `in`/`between` take comma-separated values; `is_null`/`not_set` take no value. Repeat the key to AND several constraints on one field.
|* **Sort**: `?obp_sort_by=FIELD[,FIELD2]&obp_sort_direction=ASC` (or DESC).
|* **Paginate**: `?obp_limit=20&obp_offset=40`.
|
|Only fields declared `"indexed": true` are queryable. Filtering, sorting, pagination and `is_null`/`not_set` work on any deployment.
|
|### One-hop joins (EXISTS / NOT EXISTS)
|
|You can filter one entity by a condition on a *related* entity that links to it through a declared `reference:` field — the relational "does a matching related record exist?" question:
|
|* `?obp_exists[child]` — keep parents that have at least one related child.
|* `?obp_exists[child]=filter[status]=eq:active` — parents that have at least one child matching the predicate.
|* `?obp_not_exists[child]` — parents with no related child at all.
|* `?obp_not_exists[child]=filter[status]=eq:active` — parents with no matching child (this **includes** parents that have no child at all).
|* If two entities are linked by more than one reference, disambiguate the edge with `via:`, e.g. `?obp_exists[child]=via:parent_ref;filter[status]=eq:active`.
|
|Mind the distinction: `obp_not_exists[child]=filter[x]=eq:true` (no child with x=true — includes childless parents) is NOT the same as `obp_exists[child]=filter[x]=ne:true` (has a child with x not equal to true — excludes childless parents).
|
|**Requirements for joins:**
|
|* The SQL projection backend must be enabled (`dynamic_entity.indexing.backend=auto` on Postgres or SQL Server). On an in-memory deployment a join query returns `400 (OBP-09022)`; while an index is still building it returns `409`.
|* The link must be a field typed `reference:<Entity>` AND declared `"indexed": true`. A plain string field holding ids is not joinable.
|* The queried (parent) entity must itself have at least one indexed field.
|* Joins are available on the authenticated and `/my/` list endpoints, not on the public/community ones.
|
|### Worked example: entity `parent` and entity `child` whose field references `parent`
|
|1. Create `parent` with an indexed field (create it first, so `reference:parent` becomes a valid type):
|```json
|POST /obp/v6.0.0/management/system-dynamic-entities
|{ "entity_name": "parent", "has_personal_entity": false,
|  "schema": { "properties": { "name": {"type":"string","example":"Acme","indexed":true} } } }
|```
|2. Create `child` with a reference to `parent`:
|```json
|POST /obp/v6.0.0/management/system-dynamic-entities
|{ "entity_name": "child", "has_personal_entity": false,
|  "schema": { "properties": {
|    "parent_ref": {"type":"reference:parent","example":"00000000-0000-0000-0000-000000000000","indexed":true},
|    "status":     {"type":"string","example":"active","indexed":true} } } }
|```
|3. Create records. A `POST /obp/v6.0.0/parent` response contains a `parent_id`; put that value into the child's `parent_ref`:
|```
|POST /obp/v6.0.0/parent  {"name":"P1"}                                  -> returns parent_id (call it P1)
|POST /obp/v6.0.0/child   {"parent_ref":"<P1>","status":"active"}
|POST /obp/v6.0.0/child   {"parent_ref":"<P1>","status":"closed"}
|```
|4. Query parents by a condition on their children:
|```
|GET /obp/v6.0.0/parent?obp_exists[child]=filter[status]=eq:active        -> parents that have an active child
|GET /obp/v6.0.0/parent?obp_not_exists[child]=filter[status]=eq:active    -> parents with no active child
|GET /obp/v6.0.0/parent?obp_exists[child]                                 -> parents that have any child
|GET /obp/v6.0.0/parent?obp_not_exists[child]                             -> parents with no child at all
|```
|
""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Dynamic-Entity-Access-Model",
		description =
			s"""
|A Dynamic Entity definition carries six access flags. Together they decide who may create, read, edit and delete rows, and through which route. This page is the reference; the flags are set in the definition JSON next to the entity name (see ${getGlossaryItemLink("Dynamic-Entities")}).
|
|**The five routes on one entity**
|
|| Route | Exists when | Who may read | Who may write | Which rows |
||---|---|---|---|---|
|| System: `/obp/dynamic-entity/ENTITY` or `/obp/dynamic-entity/banks/BANK_ID/ENTITY` | always | holders of the entity Get role | holders of the Create, Update and Delete roles | the shared pool: rows created here; never personal rows |
|| Personal: `/obp/dynamic-entity/my/ENTITY` | `hasPersonalEntity` | any authenticated User; a role only if `personalRequiresRole`; a consent user in addition only if its Consent lists the entity in `my_resources` | same rule | the caller's own rows only, keyed by the on-behalf-of user |
|| Community: `/obp/dynamic-entity/community/ENTITY` | `hasCommunityAccess` | authenticated holders of the Get role | nobody (read only) | every row, personal rows included |
|| Public: `/obp/dynamic-entity/public/ENTITY` | `hasPublicAccess` | anyone, no login | nobody (read only) | the shared pool only |
|| Row level: the System routes with `useRowLevelAccess` | `useRowLevelAccess` | per row, whoever the access list marks readable; lists are filtered | per row, access list Update and Delete; the creator is granted read, update, delete and grant on their own row | whatever the access list says |
|
|The entity roles are named after the entity: `CanCreateDynamicEntity_SystemENTITY`, `CanGetDynamicEntity_SystemENTITY`, `CanUpdateDynamicEntity_SystemENTITY`, `CanDeleteDynamicEntity_SystemENTITY` (without `System` for bank level entities, held at the bank).
|
|Two settings apply on top of the routes:
|
|* `authMode` says which credential satisfies the role checks on the System route: `UserOnly` (Entitlements), `ApplicationOnly` (Consumer Scopes), `UserOrApplication`, `UserAndApplication`. The Personal and Row level routes always need a User; `ApplicationOnly` is refused on an entity with `hasPersonalEntity`.
|* Field roles: a field with a `read_role` is omitted from every response unless the caller holds that role; a field with a `write_role` is only changed by PATCH from a holder (POST ignores it, PUT preserves its value).
|
|**Authorship and editing by actor**
|
|| Actor | Shared pool | Own rows via `my` | Other Users' personal rows |
||---|---|---|---|
|| Anonymous | read, if `hasPublicAccess` | none | none |
|| Authenticated User, no role | none | create, edit, delete (unless `personalRequiresRole`) | none |
|| Get role holder | read | as above | read them all via `community`, if `hasCommunityAccess` |
|| Create, Update, Delete role holders | write | as above | none: personal rows are invisible to the System route |
|| Row access list grantee | per row | as above | per row, if granted |
|| Consent user (a User minted by a Consent) | as the roles its Consent carries | only if the Consent lists the entity in `my_resources.personal_dynamic_entities` with the needed action (plus the role when `personalRequiresRole`); rows it writes belong to the User who granted the Consent | none |
|
|**Patterns**
|
|| Pattern | Flags | Behaviour |
||---|---|---|
|| Curated reference data | personal off, public on | role holders maintain it, everyone reads it |
|| Restricted registry | personal off, public off, community off | role holders only |
|| Team space | personal on, `personalRequiresRole` on, community on | members write their own rows, the whole team reads everything; the entity roles define the team |
|| User owned records | personal on, `personalRequiresRole` off, community off, public off | each User has their own rows; an agent reaches them only through a Consent whose `my_resources` lists the entity |
|| Shared records with per row sharing | `useRowLevelAccess` on | the creator owns the row and grants others read, update, delete or grant |
|
|One combination deserves care: personal on, `personalRequiresRole` off, community on. It shows every User's personal rows to any holder of the Get role, which is rarely intended.
|
|`personalRequiresRole` gates the `my` route with the entity's own roles, the same roles that open the shared pool. It therefore suits the team space pattern, where the users of `my` are the role holders anyway. It is not a way to restrict ordinary Users' personal use: giving a User the Get role so they may use `my` also lets them read the shared pool.
|
|See also ${getGlossaryItemLink("My-Dynamic-Entities")}, ${getGlossaryItemLink("Consent")} and ${getGlossaryItemLink("Virtual Entitlements")}.
|"""
	)

	glossaryItems += GlossaryItem(
		title = "My-Dynamic-Entities",
		description =
			s"""
|
|My Dynamic Entities are user-scoped endpoints that are automatically generated when you create a Dynamic Entity with hasPersonalEntity set to true (which is the default).
|
|**How it works:**
|
|1. Create a Dynamic Entity definition (System or Bank Level) with hasPersonalEntity = true
|2. OBP automatically generates both regular CRUD endpoints AND 'my' endpoints
|3. The 'my' endpoints only return data created by the authenticated user
|
|**Example workflow:**
|
|**Step 1:** Create a Dynamic Entity definition
|
|```json
|POST /management/system-dynamic-entities
|{
|  "hasPersonalEntity": true,
|  "CustomerPreferences": {
|    "description": "User preferences",
|    "required": ["theme"],
|    "properties": {
|      "theme": {"type": "string"},
|      "language": {"type": "string"}
|    }
|  }
|}
|```
|
|**Step 2:** Use the auto-generated 'my' endpoints:
|
|* POST /my/CustomerPreferences - Create my preference
|* GET /my/CustomerPreferences - Get all my preferences
|* GET /my/CustomerPreferences/ID - Get one of my preferences
|* PUT /my/CustomerPreferences/ID - Update my preference
|* DELETE /my/CustomerPreferences/ID - Delete my preference
|
|**For bank-level entities:**
|
|* POST /banks/BANK_ID/my/CustomerPreferences
|* GET /banks/BANK_ID/my/CustomerPreferences
|* GET /banks/BANK_ID/my/CustomerPreferences/ID
|* PUT /banks/BANK_ID/my/CustomerPreferences/ID
|* DELETE /banks/BANK_ID/my/CustomerPreferences/ID
|
|**Key differences:**
|
|* **Regular endpoints** (e.g., /CustomerPreferences): Access ALL entities (requires roles)
|* **My endpoints** (e.g., /my/CustomerPreferences): Access only your own entities (user-scoped)
|
|**Note:** If hasPersonalEntity is set to false, no 'my' endpoints are generated.
|
|**Management endpoints for Dynamic Entity definitions (available from v4.0.0):**
|
|* GET /my/dynamic-entities - Get all Dynamic Entity definitions I created
|* PUT /my/dynamic-entities/DYNAMIC_ENTITY_ID - Update a definition I created
|
|**Discovery endpoint (available from v6.0.0):**
|
|* GET /personal-dynamic-entities/available - Discover all Dynamic Entities that support personal data storage
|
|This endpoint allows regular users (without admin roles) to discover which dynamic entities they can interact with for storing personal data via the /my/ENTITY_NAME endpoints. No special roles required - just needs to be logged in.
|
|**Response format for GET /my/dynamic-entities and GET /personal-dynamic-entities/available:**
|
|**v6.0.0 format (recommended):**
|
|The v6.0.0 response uses snake_case field names and an explicit `entity_name` field:
|
|```json
|{
|  "dynamic_entities": [
|    {
|      "dynamic_entity_id": "abc-123-def",
|      "entity_name": "CustomerPreferences",
|      "user_id": "user-456",
|      "bank_id": null,
|      "has_personal_entity": true,
|      "definition": {
|        "description": "User preferences",
|        "required": ["theme"],
|        "properties": {
|          "theme": {"type": "string"},
|          "language": {"type": "string"}
|        }
|      }
|    }
|  ]
|}
|```
|
|**v4.0.0 format (legacy):**
|
|The v4.0.0 response uses camelCase field names and the **entity name is a dynamic key** (not a fixed property name):
|
|```json
|{
|  "dynamic_entities": [
|    {
|      "CustomerPreferences": {
|        "description": "User preferences",
|        "required": ["theme"],
|        "properties": {
|          "theme": {"type": "string"},
|          "language": {"type": "string"}
|        }
|      },
|      "dynamicEntityId": "abc-123-def",
|      "userId": "user-456",
|      "hasPersonalEntity": true,
|      "bankId": null
|    }
|  ]
|}
|```
|
|To extract the entity name from the v4.0.0 format programmatically, find the key that is NOT one of the standard properties: dynamicEntityId, userId, hasPersonalEntity, bankId.
|
|**Required roles:**
|
|* CanCreateSystemLevelDynamicEntity - To create system level dynamic entities
|* CanCreateBankLevelDynamicEntity - To create bank level dynamic entities
|
|For general information about Dynamic Entities, see ${getGlossaryItemLink("Dynamic-Entities")}
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
|### Served URL
|
|Dynamic Endpoints are served under a dedicated path prefix, *not* under `/obp/vX.Y.Z/`:
|
|`/obp/dynamic-endpoint` + optional `dynamic_endpoints_url_prefix` (from props) + the path declared in the uploaded Swagger file.
|
|For example, if your Swagger declares `/fashion-brand-list/{brandId}` and `dynamic_endpoints_url_prefix` is unset (the default), the endpoint will be available at:
|
|`/obp/dynamic-endpoint/fashion-brand-list/{brandId}`
|
|For Bank / Space level Dynamic Endpoints, OBP automatically prepends `/banks/BANK_ID` to each path in the Swagger file at creation time. So a Swagger path of `/fashion-brand-list` created for bank `gh.29.uk` is served at:
|
|`/obp/dynamic-endpoint/banks/gh.29.uk/fashion-brand-list`
|
|(plus `dynamic_endpoints_url_prefix` if set.)
|
|Note: the `/obp/vX.Y.Z/management/banks/BANK_ID/dynamic-endpoints` routes are only the administrative CRUD endpoints for creating and managing Dynamic Endpoints — they are not the served URLs of the endpoints themselves.
|
|The following videos are available:
|
|	* [Introduction to Dynamic Endpoints](https://vimeo.com/426235612)
|	* [Features of Dynamic Endpoints](https://vimeo.com/444133309)
|
""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Dynamic Resource Doc",
		description =
			s"""
|A Dynamic Resource Doc defines a *single* Endpoint at runtime: its verb, URL path, summary, description, example request and response bodies, error list, tags and Roles - plus a *method body* written in Scala which is compiled at runtime and becomes the handler of the Endpoint.
|
|Whereas a Dynamic Endpoint (see ${getGlossaryItemLink("Dynamic Endpoint Manage")}) is created from a Swagger / OpenAPI file and contains *no code* (its behaviour is selected by the swagger `host` field), a Dynamic Resource Doc *is* code: the method body has access to the full CallContext and can transform payloads, call Connector methods and NewStyle functions, or invoke Dynamic Message Docs.
|
|Like all Resource Docs, Dynamic Resource Docs are part of the server registry of the API (see ${getGlossaryItemLink("Resource Doc")}), so they appear in the API Explorer and resource-docs endpoints like any Static endpoint.
|
|Dynamic Resource Docs can be created at System level or Bank / Space level, and are served under the `/obp/dynamic-endpoint/dynamic-resource-doc` path prefix (configurable via the `url.prefix.dynamic.resourceDoc` prop).
|
|Authentication and Role checks are applied to the compiled endpoint exactly as for Static endpoints - including the checks that run inside the shared authentication step: Consumer disabled, User locked / deleted, Consent processing and Rate Limiting.
|
|Some cross-cutting features of the Static pipeline do *not* currently apply to runtime-compiled Dynamic Resource Doc endpoints: API Metrics are not recorded, the JSON Schema Validation and Force-Error interceptors are not run, the Idempotency-Key mechanism is unavailable, and handlers run on auto-commit (no request-scoped database transaction). Dynamic Endpoints created from Swagger (the proxy path) *do* record Metrics and *do* run the JSON Schema Validation interceptors.
|
|Because the method body is user-supplied code compiled at runtime, this feature is guarded by the `allow_user_generated_scala_code` prop (default: false) and the Roles CanCreateDynamicResourceDoc / CanCreateBankLevelDynamicResourceDoc etc.
|
|A helper endpoint (`POST /management/dynamic-resource-docs/endpoint-code`) can generate a method-body template from example request / response bodies.
|
|See ${getGlossaryItemLink("Dynamic Code Paths")} for how Dynamic Resource Docs relate to the other runtime-defined building blocks.
|
""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Dynamic Code Paths",
		description =
			s"""
|OBP offers several building blocks for defining API behaviour at *runtime* - stored in the OBP database as instance configuration rather than compiled into the source code. This item explains how they fit together.
|
|**The building blocks**
|
|At the *API surface* layer (what URL / verb exists, who may call it):
|
|1) **Dynamic Endpoint** (${getGlossaryItemLink("Dynamic Endpoint Manage")}) - created from a Swagger / OpenAPI file. No code. Every operation in the file becomes a live endpoint with an auto-generated Role.
|
|2) **Dynamic Resource Doc** (${getGlossaryItemLink("Dynamic Resource Doc")}) - one endpoint definition *plus* a Scala method body compiled at runtime. The code is the handler.
|
|At the *Connector* layer (how a backend system is reached):
|
|3) **Method Routing** (${getGlossaryItemLink("Method Routing")}) - a routing rule that selects which Connector implementation serves a given Connector method (per bank, per URL pattern etc.). Pure configuration, no code.
|
|4) **Connector Method** (${getGlossaryItemLink("Connector Method")}) - a runtime-compiled body (Scala, Java or JavaScript) for one of the *existing* methods of the Connector trait (e.g. getBanks, makePaymentv210, dynamicEndpointProcess). Executed when a Method Routing rule routes that method to `connector = internal`.
|
|5) **Dynamic Message Doc** (${getGlossaryItemLink("Dynamic Message Doc")}) - a runtime-compiled function keyed by a *process name*, for logic that does not correspond to an existing Connector method. Invoked from other dynamic code (or by Dynamic Entity storage operations).
|
|Related: **Dynamic Entities** (${getGlossaryItemLink("Dynamic-Entities")}) provide runtime-defined data storage, and **Endpoint Mapping** (${getGlossaryItemLink("Endpoint Mapping")}) maps Dynamic Endpoint JSON fields onto Dynamic Entity fields.
|
|**How they compose - the paths**
|
|```
|                              +--> host=obp_mock ......... returns swagger example      (mock)
|                              |
| Dynamic Endpoint (swagger) --+--> host=dynamic_entity ... Endpoint Mapping
|   no code                   |                              -> Dynamic Entity storage  (data-backed)
|                              |
|                              +--> any other host ......... Method Routing:
|                                                             connector=rest  -> HTTP proxy to backend
|                                                             connector=internal -> Connector Method (code)
|
| Dynamic Resource Doc ------------> compiled Scala handler
|   code at the endpoint layer        |-> Connector methods (routed by Method Routing)
|                                     |-> Dynamic Message Docs (by process name)
|                                     |-> any transformation / orchestration logic
|```
|
|**Choosing a path**
|
|* Need a quick mock of an API from its spec? Dynamic Endpoint with `host = obp_mock`.
|* Need a data-backed CRUD API with no code? Dynamic Endpoint with `host = dynamic_entity` + Endpoint Mapping + a Dynamic Entity.
|* Need to pass requests through to an existing backend *unchanged*? Dynamic Endpoint + Method Routing with a `url` parameter (transparent HTTP proxy - no payload transformation, no credential minting).
|* Need transformation, authentication against the backend, error mapping or orchestration? Use code: either a Dynamic Resource Doc (code at the endpoint layer - one self-contained artifact per endpoint) or a Connector Method (code at the connector seam - keeps backend integration reusable across endpoints and swappable via Method Routing). These combine well: Dynamic Resource Docs for the API surface, Connector Methods / Dynamic Message Docs for the backend calls.
|
|**Static vs Dynamic**
|
|Static endpoints (${getGlossaryItemLink("Static Endpoint")}) are Scala source code in Git, changed via release and restart. All the dynamic building blocks above live in the OBP database of the instance: they can be created and changed in real time over the management API (or via the API Manager UI) with *no code deployment and no restart*, and they never require instance-specific code in the public source repositories.
|
|**Guards**
|
|Runtime-compiled code (Dynamic Resource Docs, Connector Methods, Dynamic Message Docs) is disabled unless the `allow_user_generated_scala_code` prop is set to true, and every creation endpoint requires its corresponding Role. Dynamic Endpoints (swagger, no code) are not affected by that prop; each generated endpoint is protected by its own auto-generated Role.
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
   |### Mapping structure
   |
   |Each entry in `request_mapping` / `response_mapping` is keyed by the JSON field name you want in the Dynamic Endpoint's request or response payload, and its value is an object of the form:
   |
   |```
   |"<jsonFieldName>": {
   |  "entity": "<DynamicEntityName>",
   |  "field":  "<dynamicEntityFieldName>",
   |  "query":  "<dynamicEntityLookupField>"
   |}
   |```
   |
   |What each key does at runtime:
   |
   |* **`entity`** — the name of the Dynamic Entity to read from / write to. The current implementation only supports **one entity per mapping**; only the first `entity` value encountered is used.
   |* **`field`** — the Dynamic Entity field whose value populates the `<jsonFieldName>` in the output JSON. When the Dynamic Endpoint URL includes a query string (e.g. `?status=available`), `field` is also the Dynamic Entity field used to filter records.
   |* **`query`** — the Dynamic Entity field used as the **lookup key** when the URL has a path parameter whose name contains "id" (e.g. `/pet/{petId}`). OBP uses the **first** `query` value in the mapping as this lookup key, so by convention all entries in a mapping repeat the same `query` value (it is per-mapping, not per-field, in practice).
   |
   |Worked example. Endpoint served URL `/obp/dynamic-endpoint/pet/{petId}` with mapping:
   |
   |```
   |{
   |  "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
   |  "request_mapping": {},
   |  "response_mapping": {
   |    "id":     { "entity": "PetEntity", "field": "field1", "query": "field1" },
   |    "name":   { "entity": "PetEntity", "field": "field4", "query": "field1" },
   |    "status": { "entity": "PetEntity", "field": "field8", "query": "field1" }
   |  }
   |}
   |```
   |
   |When called as `GET /pet/123`, OBP:
   |
   |1. Takes the first `query` value — `"field1"` — and the URL path value `123`.
   |2. Finds the `PetEntity` record where `field1 == 123`.
   |3. Builds the response body by copying that record's `field1` → `id`, `field4` → `name`, `field8` → `status`.
   |
   |Notes and caveats:
   |
   |* Non-id-named path parameters (anything that does not contain "id") are not used for lookup.
   |* URL query-string filtering uses `field`, **not** `query`. A call like `GET /pets?status=available` filters `PetEntity` records by the `field` value on the mapping entry whose key matches `status` — in the example above, by `field8 == "available"`.
   |* `request_mapping` is used on write operations (POST/PUT) to translate the inbound payload to a Dynamic Entity record; leave it as `{}` for read-only operations.
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
		title = "Resource Doc",
		description =
			s"""
|A Resource Doc is the machine readable definition / description of an OBP Endpoint.
|
|The aim is that as much endpoint definition as possible is *defined first* within the Resource Doc making the Resource Doc the canonical source of truth about the endpoints structure and behaviour.
|
|In total Resource Docs form the server registry of the API: every Endpoint, static or dynamic, is registered in the running server with its Resource Doc, and that registry is the source of truth about the API surface.
|
|Note that the Resource Docs (like the Glossary) can contain instance variables about the OBP-API instance that is running, so HOSTNAMES and various configuration settings are automatically correct.
|
|An OBP API instance only serves resource docs about endpoints that are actually enabled so any client (e.g. the OBP MCP Server or API Explorer) can use them as a capability discovery channel.
|
|
|Each Resource Doc includes:
|
|  1) The Operation ID / Scala Partial Function name (the source code / function that runs the endpoint) which uniquely identifies the Endpoint (e.g. getCoreAccountById)
|  2) The API version the Endpoint is implemented in
|  3) The request verb (GET, POST, PUT, DELETE etc.) and URL path
|  4) A summary and a longer description (markdown)
|  5) An example request body and a successful response body. These are generated from the actual Scala case classes the Endpoint uses, so field names and types reflect the real implementation rather than separately maintained documentation.
|  6) The possible error responses
|  7) Tags used to group Endpoints in the API Explorer and filter Resource Docs
|  8) The Roles (Entitlements) required to call the Endpoint. Roles declared in the Resource Doc are automatically checked by the framework at runtime.
|  9) Connector methods the Endpoint depends on (linking to the related Message Docs)
|
|Because the Resource Doc registry lives inside the running server, it differs from a published OpenAPI file in two important ways:
|
|* The arrow of generation points from code to documentation: the Swagger / OpenAPI documents that OBP publishes are generated *from* the Resource Docs, not maintained alongside the code. This avoids documentation drift.
|
|* It covers Endpoints created at runtime: Dynamic Endpoints and the auto-generated CRUD Endpoints for Dynamic Entities get Resource Docs when they are created, so per-bank custom APIs are documented by the same mechanism as the static Endpoints - something a static, pre-published API description cannot do.
|
|Resource Docs are available over the Resource Doc endpoints in OBP format (which includes OBP specific metadata such as Roles, Tags and Connector methods) and in Swagger / OpenAPI format. They can be filtered by tags, functions and API collections.
|
|As mentioned above, Resource Docs power the API Explorer interface and are the natural foundation for programmatic consumers of the API surface: SDK generators, API management tooling and AI assistants (such as [Opey](/glossary#Opey) and [OBP-MCP](/glossary#OBP-MCP)) that need to discover, select and validate calls to Endpoints.
|
|See also [Endpoint](/glossary#Endpoint), [Static Endpoint](/glossary#Static-Endpoint), [Dynamic Endpoint Manage](/glossary#Dynamic-Endpoint-Manage), [Message Doc](/glossary#Message-Doc)
|
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
				s"""Dynamic linking is a security requirement under PSD2's Strong Customer Authentication (SCA) rules.
					 |
					 |When a payer initiates an electronic payment transaction, the authentication code must be dynamically linked to:
					 |
					 |1. **The amount** of the transaction
					 |2. **The payee** (recipient) of the transaction
					 |
					 |This means if either the amount or payee is modified after authentication, the authentication code becomes invalid. This protects against man-in-the-middle attacks where an attacker might try to redirect funds or change the payment amount after the user has authenticated.
					 |
					 |The requirement is specified in Article 97(2) of PSD2 and further detailed in the Regulatory Technical Standards (RTS) on SCA (Articles 5 and 6).
					 |""".stripMargin)

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
			title = "Authentication: OAuth 2.0",
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


	glossaryItems += GlossaryItem(
		title = "ABAC_Simple_Guide",
		description =
			s"""
				 |# ABAC Rules Engine - Simple Guide
				 |
				 |## Overview
				 |
				 |The ABAC (Attribute-Based Access Control) Rules Engine allows you to create dynamic access control rules in Scala that evaluate whether a user should have access to a resource.
				 |
				 |## API Usage
				 |
				 |### Endpoint
				 |```
				 |POST $getObpApiRoot/v6.0.0/management/abac-rules/{RULE_ID}/execute
				 |```
				 |
				 |### Request Example
				 |```bash
				 |curl -X POST \\
				 |  '$getObpApiRoot/v6.0.0/management/abac-rules/admin-only-rule/execute' \\
				 |  -H '$directLoginHeaderName: token=eyJhbGciOiJIUzI1...' \\
				 |  -H 'Content-Type: application/json' \\
				 |  -d '{
				 |    "bank_id": "gh.29.uk",
				 |    "account_id": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
				 |  }'
				 |```
				 |
				 |## Understanding the Three User Parameters
				 |
				 |### 1. `authenticatedUserId` (Required)
				 |**The person actually logged in and making the API call**
				 |
				 |- The real user who authenticated
				 |- Retrieved from the authentication token
				 |
				 |### 2. `onBehalfOfUserId` (Optional)
				 |**When someone acts on behalf of another user (delegation)**
				 |
				 |- Used for delegation scenarios
				 |- The authenticated user is acting for someone else
				 |- Common in customer service, admin tools, power of attorney
				 |
				 |### 3. `userId` (Optional)
				 |**The target user being evaluated by the rule**
				 |
				 |- Defaults to `authenticatedUserId` if not provided
				 |- The user whose permissions/attributes are being checked
				 |- Useful for testing rules for different users
				 |
				 |## Writing ABAC Rules
				 |
				 |### Simple Rule Examples
				 |
				 |**Rule 1: User Must Own Account**
				 |```scala
				 |accountOpt.exists(account =>
				 |  account.owners.exists(owner => owner.userId == user.userId)
				 |)
				 |```
				 |
				 |**Rule 2: Admin or Owner**
				 |```scala
				 |val isAdmin = authenticatedUser.emailAddress.endsWith("@admin.com")
				 |val isOwner = accountOpt.exists(account =>
				 |  account.owners.exists(owner => owner.userId == user.userId)
				 |)
				 |
				 |isAdmin || isOwner
				 |```
				 |
				 |**Rule 3: Account Balance Check**
				 |```scala
				 |accountOpt.exists(account => account.balance.toDouble >= 1000.0)
				 |```
				 |
				 |## Available Objects in Rules
				 |
				 |```scala
				 |authenticatedUser: User                    // The logged in user
				 |onBehalfOfUserOpt: Option[User]           // User being acted on behalf of (if provided)
				 |user: User                                 // The target user being evaluated
				 |bankOpt: Option[Bank]                      // Bank context (if bank_id provided)
				 |accountOpt: Option[BankAccount]            // Account context (if account_id provided)
				 |transactionOpt: Option[Transaction]        // Transaction context (if transaction_id provided)
				 |customerOpt: Option[Customer]              // Customer context (if customer_id provided)
				 |```
				 |
				 |**Related Documentation:**
				 |- ABAC_Parameters_Summary - Complete list of all 18 parameters
				 |- ABAC_Object_Properties_Reference - Detailed property reference
				 |- ABAC_Testing_Examples - More testing examples
				 |- ABAC_Account_Access_Enforcement - Runtime gate model
				 |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "ABAC_Parameters_Summary",
		description =
			s"""
				 |# ABAC Rule Parameters Summary
				 |
				 |The ABAC Rules Engine provides 18 parameters to your rule function, organized into three categories:
				 |
				 |## User Parameters (6 parameters)
				 |
				 |1. **authenticatedUser: User** - The logged-in user
				 |2. **authenticatedUserAttributes: List[UserAttributeTrait]** - Non-personal attributes of authenticated user (IsPersonal=false)
				 |3. **authenticatedUserAuthContext: List[UserAuthContext]** - Auth context of authenticated user
				 |4. **onBehalfOfUserOpt: Option[User]** - User being acted on behalf of (if provided)
				 |5. **onBehalfOfUserAttributes: List[UserAttributeTrait]** - Non-personal attributes of on-behalf-of user (IsPersonal=false)
				 |6. **onBehalfOfUserAuthContext: List[UserAuthContext]** - Auth context of on-behalf-of user
				 |
				 |## Target User Parameters (3 parameters)
				 |
				 |7. **userOpt: Option[User]** - Target user being evaluated
				 |8. **userAttributes: List[UserAttributeTrait]** - Non-personal attributes of target user (IsPersonal=false)
				 |9. **user: User** - Resolved target user (defaults to authenticatedUser)
				 |
				 |## Resource Context Parameters (9 parameters)
				 |
				 |10. **bankOpt: Option[Bank]** - Bank context (if bank_id provided)
				 |11. **bankAttributes: List[BankAttributeTrait]** - Bank attributes
				 |12. **accountOpt: Option[BankAccount]** - Account context (if account_id provided)
				 |13. **accountAttributes: List[AccountAttribute]** - Account attributes
				 |14. **transactionOpt: Option[Transaction]** - Transaction context (if transaction_id provided)
				 |15. **transactionAttributes: List[TransactionAttribute]** - Transaction attributes
				 |16. **transactionRequestOpt: Option[TransactionRequest]** - Transaction request context
				 |17. **transactionRequestAttributes: List[TransactionRequestAttributeTrait]** - Transaction request attributes
				 |18. **customerOpt: Option[Customer]** - Customer context (if customer_id provided)
				 |19. **customerAttributes: List[CustomerAttribute]** - Customer attributes
				 |
				 |## Usage in Rules
				 |
				 |```scala
				 |// Access user email
				 |authenticatedUser.emailAddress
				 |
				 |// Check if account exists and has sufficient balance
				 |accountOpt.exists(account => account.balance.toDouble >= 1000.0)
				 |
				 |// Check user attributes (non-personal only)
				 |authenticatedUserAttributes.exists(attr =>
				 |  attr.name == "role" && attr.value == "admin"
				 |)
				 |
				 |// Note: Only non-personal attributes (IsPersonal=false) are included
				 |
				 |// Check delegation
				 |onBehalfOfUserOpt.isDefined
				 |```
				 |
				 |**Related Documentation:**
				 |- ABAC_Simple_Guide - Getting started guide
				 |- ABAC_Object_Properties_Reference - Detailed property reference
				 |- ABAC_Account_Access_Enforcement - Runtime gate model
				 |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "ABAC_Object_Properties_Reference",
		description =
			s"""
				 |# ABAC Object Properties Reference
				 |
				 |This document lists all properties available on objects passed to ABAC rules.
				 |
				 |## User Object
				 |
				 |Available as: `authenticatedUser`, `user`, `onBehalfOfUserOpt.get`
				 |
				 |### Core Properties
				 |
				 |```scala
				 |user.userId              // String - Unique user ID
				 |user.emailAddress        // String - User's email
				 |user.name                // String - Display name
				 |user.provider            // String - Auth provider
				 |user.providerId          // String - Provider's user ID
				 |```
				 |
				 |### Usage Examples
				 |
				 |```scala
				 |// Check if user is admin
				 |user.emailAddress.endsWith("@admin.com")
				 |
				 |// Check specific user
				 |user.userId == "alice@example.com"
				 |```
				 |
				 |## BankAccount Object
				 |
				 |Available as: `accountOpt.get`
				 |
				 |### Core Properties
				 |
				 |```scala
				 |account.accountId         // AccountId - Account identifier
				 |account.bankId            // BankId - Bank identifier
				 |account.accountType       // String - Account type
				 |account.balance           // BigDecimal - Current balance
				 |account.currency          // String - Currency code (e.g., "EUR")
				 |account.name              // String - Account name
				 |account.label             // String - Account label
				 |account.owners            // List[User] - Account owners
				 |```
				 |
				 |### Usage Examples
				 |
				 |```scala
				 |// Check balance
				 |accountOpt.exists(_.balance.toDouble >= 1000.0)
				 |
				 |// Check ownership
				 |accountOpt.exists(account =>
				 |  account.owners.exists(owner => owner.userId == user.userId)
				 |)
				 |
				 |// Check currency
				 |accountOpt.exists(_.currency == "EUR")
				 |```
				 |
				 |## Bank Object
				 |
				 |Available as: `bankOpt.get`
				 |
				 |### Core Properties
				 |
				 |```scala
				 |bank.bankId               // BankId - Bank identifier
				 |bank.shortName            // String - Short name
				 |bank.fullName             // String - Full legal name
				 |bank.logoUrl              // String - URL to bank logo
				 |bank.websiteUrl           // String - Bank website URL
				 |bank.bankRoutingScheme    // String - Routing scheme
				 |bank.bankRoutingAddress   // String - Routing address
				 |```
				 |
				 |### Usage Examples
				 |
				 |```scala
				 |// Check specific bank
				 |bankOpt.exists(_.bankId.value == "gh.29.uk")
				 |
				 |// Check bank by routing
				 |bankOpt.exists(_.bankRoutingScheme == "SWIFT_BIC")
				 |```
				 |
				 |## Transaction Object
				 |
				 |Available as: `transactionOpt.get`
				 |
				 |### Core Properties
				 |
				 |```scala
				 |transaction.id            // TransactionId - Transaction ID
				 |transaction.amount        // BigDecimal - Transaction amount
				 |transaction.currency      // String - Currency code
				 |transaction.description   // String - Description
				 |transaction.startDate     // Option[Date] - Posted date
				 |transaction.finishDate    // Option[Date] - Completed date
				 |transaction.transactionType // String - Transaction type
				 |```
				 |
				 |### Usage Examples
				 |
				 |```scala
				 |// Check transaction amount
				 |transactionOpt.exists(tx => tx.amount.abs.toDouble < 100.0)
				 |
				 |// Check transaction type
				 |transactionOpt.exists(_.transactionType == "SEPA")
				 |```
				 |
				 |## Customer Object
				 |
				 |Available as: `customerOpt.get`
				 |
				 |### Core Properties
				 |
				 |```scala
				 |customer.customerId       // String - Customer ID
				 |customer.customerNumber   // String - Customer number
				 |customer.legalName        // String - Legal name
				 |customer.mobileNumber     // String - Mobile number
				 |customer.email            // String - Email address
				 |customer.dateOfBirth      // Date - Date of birth
				 |```
				 |
				 |### Usage Examples
				 |
				 |```scala
				 |// Check customer email domain
				 |customerOpt.exists(_.email.endsWith("@company.com"))
				 |```
				 |
				 |## Attribute Objects
				 |
				 |### UserAttributeTrait
				 |
				 |```scala
				 |attr.name                 // String - Attribute name
				 |attr.value                // String - Attribute value
				 |attr.attributeType        // UserAttributeType - Type of attribute
				 |```
				 |
				 |### Usage Example
				 |
				 |```scala
				 |// Check for specific non-personal attribute
				 |authenticatedUserAttributes.exists(attr =>
				 |  attr.name == "department" && attr.value == "finance"
				 |)
				 |
				 |// Note: User attributes in ABAC rules only include non-personal attributes
				 |// (where IsPersonal=false). Personal attributes are not available for
				 |// privacy and GDPR compliance reasons.
				 |```
				 |
				 |**Related Documentation:**
				 |- ABAC_Simple_Guide - Getting started guide
				 |- ABAC_Parameters_Summary - Complete parameter list
				 |- ABAC_Account_Access_Enforcement - Runtime gate model
				 |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "ABAC_Testing_Examples",
		description =
			s"""
				 |# ABAC Testing Examples
				 |
				 |## API Endpoint
				 |
				 |```
				 |POST $getObpApiRoot/v6.0.0/management/abac-rules/{RULE_ID}/execute
				 |```
				 |
				 |## Example 1: Admin Only Rule
				 |
				 |**Rule Code:**
				 |```scala
				 |authenticatedUser.emailAddress.endsWith("@admin.com")
				 |```
				 |
				 |**Test Request:**
				 |```bash
				 |curl -X POST \\
				 |  '$getObpApiRoot/v6.0.0/management/abac-rules/admin-only-rule/execute' \\
				 |  -H '$directLoginHeaderName: token=YOUR_TOKEN' \\
				 |  -H 'Content-Type: application/json' \\
				 |  -d '{}'
				 |```
				 |
				 |**Expected Result:**
				 |- Admin user → `{"result": true}`
				 |- Regular user → `{"result": false}`
				 |
				 |## Example 2: Account Owner Check
				 |
				 |**Rule Code:**
				 |```scala
				 |accountOpt.exists(account =>
				 |  account.owners.exists(owner => owner.userId == user.userId)
				 |)
				 |```
				 |
				 |**Test Request:**
				 |```bash
				 |curl -X POST \\
				 |  '$getObpApiRoot/v6.0.0/management/abac-rules/account-owner-only/execute' \\
				 |  -H '$directLoginHeaderName: token=YOUR_TOKEN' \\
				 |  -H 'Content-Type: application/json' \\
				 |  -d '{
				 |    "user_id": "alice@example.com",
				 |    "bank_id": "gh.29.uk",
				 |    "account_id": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
				 |  }'
				 |```
				 |
				 |## Example 3: Balance Check
				 |
				 |**Rule Code:**
				 |```scala
				 |accountOpt.exists(account => account.balance.toDouble >= 1000.0)
				 |```
				 |
				 |**Test Request:**
				 |```bash
				 |curl -X POST \\
				 |  '$getObpApiRoot/v6.0.0/management/abac-rules/high-balance-only/execute' \\
				 |  -H '$directLoginHeaderName: token=YOUR_TOKEN' \\
				 |  -H 'Content-Type: application/json' \\
				 |  -d '{
				 |    "bank_id": "gh.29.uk",
				 |    "account_id": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
				 |  }'
				 |```
				 |
				 |## Example 4: Transaction Amount Check
				 |
				 |**Rule Code:**
				 |```scala
				 |transactionOpt.exists(tx => tx.amount.abs.toDouble < 100.0)
				 |```
				 |
				 |**Test Request:**
				 |```bash
				 |curl -X POST \\
				 |  '$getObpApiRoot/v6.0.0/management/abac-rules/small-transactions/execute' \\
				 |  -H '$directLoginHeaderName: token=YOUR_TOKEN' \\
				 |  -H 'Content-Type: application/json' \\
				 |  -d '{
				 |    "bank_id": "gh.29.uk",
				 |    "account_id": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
				 |    "transaction_id": "trans-123"
				 |  }'
				 |```
				 |
				 |## Testing Patterns
				 |
				 |### Pattern 1: Test Different Users
				 |
				 |```bash
				 |# Test for admin
				 |curl -X POST '$getObpApiRoot/v6.0.0/management/abac-rules/RULE_ID/execute' \\
				 |  -d '{"user_id": "admin@admin.com", "bank_id": "gh.29.uk"}'
				 |
				 |# Test for regular user
				 |curl -X POST '$getObpApiRoot/v6.0.0/management/abac-rules/RULE_ID/execute' \\
				 |  -d '{"user_id": "alice@example.com", "bank_id": "gh.29.uk"}'
				 |```
				 |
				 |### Pattern 2: Test Edge Cases
				 |
				 |```bash
				 |# No context (minimal)
				 |curl -X POST '$getObpApiRoot/v6.0.0/management/abac-rules/RULE_ID/execute' -d '{}'
				 |
				 |# Full context
				 |curl -X POST '$getObpApiRoot/v6.0.0/management/abac-rules/RULE_ID/execute' -d '{
				 |  "user_id": "alice@example.com",
				 |  "bank_id": "gh.29.uk",
				 |  "account_id": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
				 |  "transaction_id": "trans-123",
				 |  "customer_id": "cust-456"
				 |}'
				 |```
				 |
				 |## Common Errors
				 |
				 |### Error 1: Rule Not Found
				 |
				 |```bash
				 |curl -X POST '$getObpApiRoot/v6.0.0/management/abac-rules/nonexistent-rule/execute' \\
				 |  -H '$directLoginHeaderName: token=YOUR_TOKEN' \\
				 |  -d '{}'
				 |```
				 |
				 |**Response:** `{"error": "ABAC Rule not found with ID: nonexistent-rule"}`
				 |
				 |### Error 2: Invalid Context
				 |
				 |**Response:** Objects will be `None` if IDs are invalid, rule should handle gracefully
				 |
				 |**Related Documentation:**
				 |- ABAC_Simple_Guide - Getting started guide
				 |- ABAC_Parameters_Summary - Complete parameter list
				 |- ABAC_Object_Properties_Reference - Property reference
				 |- ABAC_Account_Access_Enforcement - Runtime gate model
				 |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "ABAC_Account_Access_Enforcement",
		description =
			s"""
				 |# ABAC Account Access Enforcement
				 |
				 |How OBP decides whether the ABAC subsystem grants account access at runtime, and
				 |how that's kept separate from rule management. For writing rules, see
				 |ABAC_Simple_Guide — this entry is for operators, security reviewers, and anyone
				 |tracing why a request did or did not succeed.
				 |
				 |## Two distinct guard surfaces
				 |
				 |**Management plane** — controls who can author and run rules:
				 |
				 |- `CanCreateAbacRule` — POST `/management/abac-rules`, validate
				 |- `CanGetAbacRule` — GET rule(s), schema, list policies
				 |- `CanUpdateAbacRule` — PUT rule (also flips `is_active`)
				 |- `CanDeleteAbacRule` — DELETE rule
				 |- `CanExecuteAbacRule` — POST `/management/abac-rules/{id}/execute` and `…/abac-policies/{policy}/execute`
				 |
				 |**Runtime gate** — controls whether ABAC fallback can grant access on a real API
				 |call. Implemented in `APIUtil.checkAbacAccountAccess`. None of the management
				 |roles above are involved at request time, with the deliberate exception of
				 |`CanExecuteAbacRule` (see "dual purpose" below).
				 |
				 |## Fallback ordering
				 |
				 |ABAC is **only consulted as a fallback** after normal access checks fail.
				 |`APIUtil.hasAccountAccess` evaluates in this order:
				 |
				 |1. Public view → grant
				 |2. User has firehose access → grant
				 |3. User has the view via the AccountAccess table → grant
				 |4. **None of the above and a user is present → try ABAC**
				 |5. No user → deny
				 |
				 |Consequence: ABAC can only ever **widen** access. It cannot deny a user who
				 |already has access through a normal mechanism, and it cannot revoke a granted
				 |view. Removing a rule never breaks an existing access path; adding a rule never
				 |restricts one.
				 |
				 |## Six conditions for ABAC to grant access
				 |
				 |All six must hold. If any one fails, the runtime gate returns `false` and the
				 |request is denied at the access layer.
				 |
				 |1. **Normal checks failed.** ABAC was reached via the fallback ordering above.
				 |   If any earlier check granted, ABAC is never invoked.
				 |
				 |2. **Master switch on.** Props key `allow_abac_account_access=true`. Default is
				 |   **false** — ABAC is off out of the box. When false,
				 |   `checkAbacAccountAccess` returns `Full(false)` immediately; no rules execute.
				 |
				 |3. **Target user opted in.** The user being evaluated must hold the
				 |   `CanExecuteAbacRule` system-level entitlement (bankId=`""`). Without it the
				 |   runtime returns `Full(false)`. Granting this entitlement is the act that
				 |   subjects a user to the ABAC subsystem at runtime.
				 |
				 |4. **CallContext present.** Internal — `None` returns `Full(false)`.
				 |
				 |5. **At least one active rule PASSes.**
				 |   `AbacRuleEngine.executeRulesByPolicyDetailed(ABAC_POLICY_ACCOUNT_ACCESS, ...)`
				 |   evaluates every rule whose `is_active=true` under the `account-access`
				 |   policy. OR semantics — one PASS is enough. Inactive rules are skipped
				 |   entirely. If no rule passes but at least one explicitly denied, the call
				 |   surfaces a `Failure` naming the failing rule IDs instead of a silent deny.
				 |
				 |6. **No timeout, no exception.** Rule evaluation is awaited for at most
				 |   10 seconds, wrapped in try/catch. Any timeout, thrown exception, or engine
				 |   error → `Full(false)` (fail closed).
				 |
				 |## Dual purpose of `CanExecuteAbacRule`
				 |
				 |The same role gates two unrelated capabilities:
				 |
				 |- **Manual testing** — invoking `/management/abac-rules/{id}/execute` or
				 |  `/management/abac-policies/{policy}/execute` to dry-run a rule.
				 |- **Runtime opt-in** — being eligible for ABAC fallback on real account access
				 |  decisions (condition #3 above).
				 |
				 |Deliberate: a user has to be allowed to invoke a rule manually before they can
				 |be subject to one automatically. But it means revoking "can test rules" also
				 |revokes "can be granted access via ABAC" — keep this coupling in mind when
				 |building admin UIs or splitting roles.
				 |
				 |## Diagnosing a decision
				 |
				 |```
				 |GET $getObpApiRoot/v7.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views/TARGET_VIEW_ID/users/TARGET_USER_ID/account-access-trace
				 |```
				 |
				 |Returns a structured trace with each of the six conditions surfaced:
				 |
				 |- `account_access_trace.has_account_access_for_view` — whether condition #1
				 |  even matters (true means normal access already grants, ABAC not reached)
				 |- `entitlement_trace.has_can_execute_abac_rule` — condition #3
				 |- `abac_trace.allow_abac_account_access` — condition #2
				 |- `abac_trace.rules_evaluated[].result` — condition #5, per rule (see below)
				 |- `abac_trace.standalone_abac_result` — the AND of #2, #3, and "at least one
				 |  PASS". This is the verdict ABAC would produce **on its own**, ignoring the
				 |  AccountAccess table. It is **not** the same as "ABAC granted this user's
				 |  access" — see "Standalone vs decisive" below.
				 |- `has_access` and `access_source` — `"ACCOUNT_ACCESS"` |
				 |  `"ABAC"` | `"NONE"`. `access_source` is what actually decided.
				 |
				 |### Standalone vs decisive
				 |
				 |`standalone_abac_result` answers the question "if ABAC were the only mechanism,
				 |would it grant?" It is computed independently of the AccountAccess lookup.
				 |
				 |To answer "did ABAC actually grant **this** user's access?", use
				 |`access_source == "ABAC"` instead.
				 |
				 |Worked example: a user holds the `owner` view directly via the AccountAccess
				 |table, AND every ABAC condition holds (prop on, has `CanExecuteAbacRule`, a
				 |rule PASSes). The trace will show:
				 |
				 |- `account_access_trace.has_account_access_for_view: true`
				 |- `standalone_abac_result: true`
				 |- `access_source: "ACCOUNT_ACCESS"`
				 |
				 |ABAC didn't grant anything for this user — AccountAccess did. ABAC was simply
				 |evaluated in parallel and would also have granted if asked. UIs rendering an
				 |"ABAC access" column should read `access_source`, not
				 |`standalone_abac_result`.
				 |
				 |### Per-rule `result` values
				 |
				 |`result` is a four-state string (not a boolean — `FAIL` and `ERROR` are not the
				 |same thing, and a disabled rule is not the same as a rejecting rule):
				 |
				 |- `PASS` — rule executed and returned `true`. Counts toward access being granted.
				 |- `FAIL` — rule executed and returned `false`. Clean rejection; no error.
				 |- `ERROR` — rule threw an exception, returned a `Failure`, or returned an empty
				 |  result. `error_message` is populated. Investigate as a bug or upstream
				 |  outage — the rule did not produce a decision.
				 |- `SKIPPED` — rule has `is_active=false`. Engine never ran it.
				 |  `error_message` is `"Rule is not active"`.
				 |
				 |Only `PASS` contributes to granting access. `FAIL`, `ERROR`, and `SKIPPED` all
				 |mean "this rule did not grant" but are intentionally distinct in the trace so
				 |operators can tell a rejecting rule from a broken one from an inactive one.
				 |
				 |The trace endpoint is **diagnostic only** — it does not affect enforcement. It
				 |is gated by `CanGetAccountAccessTrace`, a read-only audit role distinct from
				 |the management and runtime roles above.
				 |
				 |## Enabling ABAC in a deployment
				 |
				 |1. Set `allow_abac_account_access=true` in props.
				 |2. Grant `CanCreateAbacRule` to a rule author and create at least one active
				 |   rule under the `account-access` policy.
				 |3. Grant `CanExecuteAbacRule` to each user who should be eligible for ABAC
				 |   fallback. Without this, rules never run for them.
				 |4. Grant `CanGetAccountAccessTrace` to anyone who needs to debug decisions
				 |   (audit, support, compliance).
				 |
				 |**Related Documentation:**
				 |- ABAC_Simple_Guide - Writing rules
				 |- ABAC_Parameters_Summary - Rule parameters
				 |- ABAC_Object_Properties_Reference - Object properties in rules
				 |- ABAC_Testing_Examples - Testing patterns
				 |""".stripMargin)

	glossaryItems += GlossaryItem(
		title = "Tenancy-Model-Open-Bank-Project",
		description =
			s"""
				 |The Open Bank Project (OBP) supports multi-bank operation within a single deployment, with banks acting as the primary domain and isolation boundary. Integration behaviour can be configured per bank, including connector routing based on bank_id.
				 |
				 |For SaaS deployments requiring a "dedicated tenant", OBP typically applies tenancy at the deployment level, using separate runtimes, databases, and secrets to meet regulatory and operational isolation requirements common in banking environments.
				 |
				 |Centralised operations across multiple deployments are achieved through automated platform tooling (e.g. CI/CD, configuration management, monitoring, logging, and backups), providing a unified operational experience even when tenants are deployed separately.
				 |""".stripMargin)

  private def applyGlossarySubstitutions(content: String): String =
    content
      .replaceAll("getServerUrl", getServerUrl)
      .replaceAll("getObpApiRoot", getObpApiRoot)

  // Returns (filename-without-extension, content) pairs from docs/glossary/*.md.
  // Handles both file: (IDE / unpacked classes) and jar: (fat-jar deployment) protocols.
  private def getGlossaryEntries(): List[(String, String)] = {
    import java.net.URLDecoder
    import java.nio.charset.StandardCharsets
    val resourceUrl = getClass.getClassLoader.getResource("docs/glossary")
    if (resourceUrl == null) {
      logger.error("Could not locate docs/glossary resource on the classpath")
      return List.empty
    }
    logger.info(s"|---> Glossary resource URL: $resourceUrl")
    resourceUrl.getProtocol match {
      case "file" =>
        val glossaryPath = new File(URLDecoder.decode(resourceUrl.getPath, StandardCharsets.UTF_8.name()))
        if (glossaryPath.exists && glossaryPath.isDirectory) {
          Option(glossaryPath.listFiles()).getOrElse(Array.empty)
            .filter(f => f.isFile && f.getName.endsWith(".md"))
            .map { f =>
              val src = scala.io.Source.fromFile(f)
              val content = try src.mkString finally src.close()
              (f.getName.stripSuffix(".md"), content)
            }.toList
        } else {
          logger.error(s"Glossary directory not found at: $glossaryPath")
          List.empty
        }
      case "jar" =>
        // fat-jar: use JarURLConnection to enumerate entries inside the jar
        val conn = resourceUrl.openConnection().asInstanceOf[java.net.JarURLConnection]
        val jar  = conn.getJarFile
        val prefix = conn.getEntryName + "/"
        import scala.jdk.CollectionConverters._
        jar.entries().asScala
          .filter(e => !e.isDirectory && e.getName.startsWith(prefix) && e.getName.endsWith(".md"))
          .map { entry =>
            val name = entry.getName.stripPrefix(prefix).stripSuffix(".md")
            val src  = scala.io.Source.fromInputStream(jar.getInputStream(entry))
            val content = try src.mkString finally src.close()
            (name, content)
          }.toList
      case proto =>
        logger.error(s"Unsupported classpath protocol '$proto' for docs/glossary — cannot load file-backed glossary items")
        List.empty
    }
  }

	// Append all files from /OBP-API/docs/glossary as items.
	// File name (without .md) becomes the title; file content becomes the description.
	glossaryItems.appendAll(
		getGlossaryEntries().map { case (name, content) =>
			GlossaryItem(
				title = name.replace("_", " "),
				description = applyGlossarySubstitutions(content)
			)
		}
	)

	glossaryItems += GlossaryItem(
		title = "Email Validation for OBP Local Users",
		description =
			s"""
				 |### Overview
				 |
				 |When a new OBP local user is created, they may be required to validate their email address before they can log in.
				 |This is controlled by the `authUser.skipEmailValidation` property (default: `false`).
				 |
				 |When email validation is enabled, the user receives an email containing a signed JWT token with a validation link.
				 |The user clicks the link, and the App (portal) extracts the token and calls the API to complete the validation.
				 |
				 |### Props
				 |
				 |The following properties are involved:
				 |
				 |- `authUser.skipEmailValidation` — Set to `true` to skip email validation entirely (default: `false`). Currently: `${APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", false)}`
				 |- `portal_external_url` — **Required.** The base URL of your frontend/portal application. Used to construct the validation link in the email. For example: `portal_external_url=https://your-portal.example.com`. Currently: `${APIUtil.getPropsValue("portal_external_url", "not set")}`
				 |- `email_validation_token_expiry_minutes` — Expiry time for the validation JWT token in minutes (default: `1440` i.e. 24 hours). Currently: `${APIUtil.getPropsAsIntValue("email_validation_token_expiry_minutes", 1440)}`
				 |
				 |### Step 1: User Creation
				 |
				 |A user can be created via:
				 |
				 |**POST /obp/v6.0.0/users** (no authentication required)
				 |
				 |Request body:
				 |
				 |    {
				 |      "username": "user@example.com",
				 |      "password": "Str0ng!Password",
				 |      "first_name": "Jane",
				 |      "last_name": "Doe",
				 |      "email": "user@example.com"
				 |    }
				 |
				 |If `authUser.skipEmailValidation=false`, the API will:
				 |
				 |1. Create the user with `validated=false`
				 |2. Generate a signed JWT token containing the user's unique ID as the subject, with a configurable expiry
				 |3. Construct a validation link: `{portal_external_url}/user-validation?token={JWT}`
				 |4. Send an email to the user with the validation link
				 |
				 |The user or the legacy Lift signup form can also trigger validation emails. In all cases, the same JWT-based token is used.
				 |
				 |### Step 2: Email Validation
				 |
				 |**POST /obp/v6.0.0/users/email-validation** (no authentication required)
				 |
				 |Request body:
				 |
				 |    {
				 |      "token": "eyJhbGciOiJIUzI1NiJ9..."
				 |    }
				 |
				 |Response (201):
				 |
				 |    {
				 |      "user_id": "5995d6a2-01b3-423c-a173-5481df49bdaf",
				 |      "email": "user@example.com",
				 |      "username": "user@example.com",
				 |      "provider": "https://your-api.example.com",
				 |      "validated": true,
				 |      "message": "Email validated successfully"
				 |    }
				 |
				 |Error responses:
				 |
				 |- **400** — Invalid JSON format or empty token
				 |- **404** — Invalid or expired JWT token (bad signature, expired, or user not found)
				 |- **400** — User email is already validated
				 |
				 |This endpoint:
				 |
				 |1. Verifies the JWT signature (HMAC) and checks the expiry time
				 |2. Extracts the unique ID from the JWT subject
				 |3. Looks up the user by unique ID
				 |4. Sets the user's validated status to `true`
				 |5. Resets the unique ID (invalidating the token — it is single-use)
				 |6. Grants default entitlements to the user
				 |
				 |### Token Security
				 |
				 |- The token is a **signed JWT** (HMAC-SHA256) — it cannot be forged without the server's shared secret.
				 |- The token has a **configurable expiry** (default: 24 hours) set via `email_validation_token_expiry_minutes`.
				 |- The token is **single-use** — after validation, the unique ID is reset, so the same token cannot be used again.
				 |
				 |### Typical App Flow
				 |
				 |1. User submits registration form
				 |2. App calls POST /obp/v6.0.0/users
				 |3. App shows "Check your email for a validation link"
				 |4. User clicks link in email, App opens at `/user-validation?token={JWT}`
				 |5. App extracts the token from the URL query parameter
				 |6. App calls POST /obp/v6.0.0/users/email-validation with the token
				 |7. App shows "Email validated successfully. Please log in."
				 |
				 |""")

	glossaryItems += GlossaryItem(
		title = "Password Reset for OBP Local Users",
		description =
			s"""
				 |### Overview
				 |
				 |The password reset flow allows a user who has forgotten their password to request a reset email and then set a new password. There are two steps:
				 |
				 |1. **Request a password reset email** (anonymous — no login required)
				 |2. **Set the new password** using the token from the email (anonymous — no login required)
				 |
				 |There is also an admin endpoint for requesting a reset on behalf of a user (requires authentication and the `CanCreateResetPasswordUrl` role).
				 |
				 |### Step 1: Request Password Reset Email
				 |
				 |**POST /obp/v6.0.0/users/password-reset-url**
				 |
				 |No authentication required.
				 |
				 |Request body:
				 |
				 |    {
				 |      "username": "user@example.com",
				 |      "email": "user@example.com"
				 |    }
				 |
				 |Response (201):
				 |
				 |    {
				 |      "message": "If the account exists, a password reset email has been sent."
				 |    }
				 |
				 |Notes:
				 |
				 |- The response is always the same whether or not the user exists. This prevents user enumeration.
				 |- If the user exists, is validated, and the email matches, a reset email is sent containing a link with a reset token.
				 |- The reset link base URL is constructed from the `portal_external_url` props value (currently: `${APIUtil.getPropsValue("portal_external_url", "not set")}`). This must be set to your frontend/portal URL so that reset emails contain the correct link.
				 |- The App should present a form asking for username and email, call this endpoint, and then show a message saying "Check your email for a reset link."
				 |
				 |### Step 2: Complete Password Reset
				 |
				 |**POST /obp/v6.0.0/users/password**
				 |
				 |No authentication required.
				 |
				 |Request body:
				 |
				 |    {
				 |      "token": "a1b2c3d4e5f67890abcdef1234567890",
				 |      "new_password": "NewStr0ng!Password"
				 |    }
				 |
				 |Response (201):
				 |
				 |    {
				 |      "message": "Password has been reset successfully."
				 |    }
				 |
				 |Error responses:
				 |
				 |- **400** — Invalid or expired token
				 |- **400** — Weak password
				 |
				 |Notes:
				 |
				 |- The token is a signed JWT with a configurable expiry (default: 120 minutes). The server-side expiry can be configured with the `password_reset_token_expiry_minutes` property (currently: `${APIUtil.getPropsAsIntValue("password_reset_token_expiry_minutes", 120)}` minutes).
				 |- The token comes from the reset email URL. The App should extract the token from the URL path (everything after `/user_mgt/reset_password/`) and URL-decode it before sending it to this endpoint.
				 |- The token is single-use. Once the password is reset, the token is invalidated. An expired token will also be rejected.
				 |
				 |### Admin Endpoint (Optional)
				 |
				 |**POST /obp/v6.0.0/management/user/reset-password-url**
				 |
				 |Authentication required. Requires the `CanCreateResetPasswordUrl` role.
				 |
				 |Request body:
				 |
				 |    {
				 |      "username": "user@example.com",
				 |      "email": "user@example.com",
				 |      "user_id": "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1"
				 |    }
				 |
				 |Response (201):
				 |
				 |    {
				 |      "reset_password_url": "https://your-obp-instance.com/user_mgt/reset_password/TOKEN"
				 |    }
				 |
				 |This endpoint returns the reset URL directly (for logging/admin purposes) and also sends the email. It requires all three fields: `username`, `email`, and `user_id`.
				 |
				 |### Typical App Flow
				 |
				 |1. User clicks "Forgot Password"
				 |2. App shows form with username and email fields
				 |3. App calls POST /obp/v6.0.0/users/password-reset-url
				 |4. App shows "Check your email for a reset link"
				 |5. User clicks link in email, App opens reset page and extracts token from URL
				 |6. App shows form with new password field
				 |7. App calls POST /obp/v6.0.0/users/password with token and new_password
				 |8. App shows "Password has been reset successfully. Please log in."
				 |
				 |### Password Requirements
				 |
				 |The new password must meet one of these criteria:
				 |
				 |- **10-16 characters:** Must contain at least one uppercase letter, one lowercase letter, one digit, and one special character
				 |- **17-512 characters:** No additional complexity requirements (length alone is sufficient)
				 |
""")

	glossaryItems += GlossaryItem(
		title = "Authentication: Credential Checking Flow",
		description =
			s"""
				 |### Overview
				 |
				 |OBP supports both **local** and **external** credential checking. Local credentials are verified against the AuthUser table (bcrypt). External credentials are delegated to a core banking system or identity provider via the Connector.
				 |
				 |### Login Flow (Web Form and DirectLogin)
				 |
				 |```
				 |                         ┌─────────────────────────┐
				 |                         │      LOGIN REQUEST       │
				 |                         │   (username + password)  │
				 |                         │                          │
				 |                         │  Via: Web Form login()   │
				 |                         │   or DirectLogin header  │
				 |                         └────────────┬─────────────┘
				 |                                      │
				 |                                      ▼
				 |                         ┌─────────────────────────┐
				 |                         │  Look up AuthUser by     │
				 |                         │  username in local DB    │
				 |                         └────────────┬─────────────┘
				 |                                      │
				 |                    ┌─────────────────┼─────────────────┐
				 |                    │                 │                  │
				 |                    ▼                 ▼                  ▼
				 |              ┌──────────┐    ┌─────────────┐    ┌───────────┐
				 |              │  FOUND   │    │   FOUND     │    │ NOT FOUND │
				 |              │  Local   │    │  External   │    │           │
				 |              │ Provider │    │  Provider   │    │           │
				 |              └────┬─────┘    └──────┬──────┘    └─────┬─────┘
				 |                   │                 │                  │
				 |                   ▼                 ▼                  ▼
				 |            ┌────────────┐   ┌─────────────┐   ┌──────────────┐
				 |            │ Validated? │   │  Validated? │   │ Props:       │
				 |            │ Locked?    │   │  Locked?    │   │ connector.   │
				 |            └─────┬──────┘   └──────┬──────┘   │ user.auth    │
				 |                  │                 │          │ == true?     │
				 |           ┌──Yes─┘                 │          └──────┬───────┘
				 |           │                        │            No┌──┘Yes
				 |           ▼                        │              ▼    │
				 |   ┌───────────────┐                │         ┌──────┐  │
				 |   │ testPassword() │                │         │REJECT│  │
				 |   │ (local bcrypt  │                │         └──────┘  │
				 |   │  check)        │                │                   │
				 |   └───────┬────────┘                │                   │
				 |           │                         ▼                   ▼
				 |           │                  ┌─────────────┐   ┌──────────────────┐
				 |           │                  │ Props:      │   │                  │
				 |           │                  │ connector.  │   │  externalUser    │
				 |           │                  │ user.auth   │   │  Helper()        │
				 |           │                  │ == true?    │   │                  │
				 |           │                  └──────┬──────┘   └────────┬─────────┘
				 |           │                    No┌──┘Yes                │
				 |           │                      ▼    │                 │
				 |           │                 ┌──────┐  │                 │
				 |           │                 │REJECT│  │                 │
				 |           │                 └──────┘  │                 │
				 |           │                           ▼                 │
				 |           │              ┌──────────────────────────────┘
				 |           │              │
				 |           │              ▼
				 |           │   ╔══════════════════════════════════════════════════╗
				 |           │   ║      checkExternalUserViaConnector()            ║
				 |           │   ║                                                  ║
				 |           │   ║  Connector.checkExternalUserCredentials          ║
				 |           │   ║         (username, password)                     ║
				 |           │   ║                                                  ║
				 |           │   ║  ┌──────────────┬──────────────┬──────────────┐ ║
				 |           │   ║  │ Akka         │ StoredProc   │ LocalMapped  │ ║
				 |           │   ║  │ Connector    │ Connector    │ Connector    │ ║
				 |           │   ║  │              │              │              │ ║
				 |           │   ║  │ southSide    │ HTTP call to │ Returns      │ ║
				 |           │   ║  │ Actor msg    │ stored proc  │ Failure("")  │ ║
				 |           │   ║  │ "obp.check   │ "obp_check_  │ (N/A)       │ ║
				 |           │   ║  │  External    │  external_   │              │ ║
				 |           │   ║  │  UserCreds"  │  user_creds" │              │ ║
				 |           │   ║  └──────┬───────┴──────┬───────┴──────────────┘ ║
				 |           │   ║         │              │                         ║
				 |           │   ║         ▼              ▼                         ║
				 |           │   ║   ┌──────────────────────────┐                  ║
				 |           │   ║   │  External System /       │                  ║
				 |           │   ║   │  Core Banking Adapter    │                  ║
				 |           │   ║   │                          │                  ║
				 |           │   ║   │  Validates credentials   │                  ║
				 |           │   ║   │  Returns:                │                  ║
				 |           │   ║   │   InboundExternalUser    │                  ║
				 |           │   ║   │   - sub  (user id)       │                  ║
				 |           │   ║   │   - iss  (provider)      │                  ║
				 |           │   ║   │   - email                │                  ║
				 |           │   ║   │   - emailVerified        │                  ║
				 |           │   ║   │   - name                 │                  ║
				 |           │   ║   │   - userAuthContext       │                  ║
				 |           │   ║   └────────────┬─────────────┘                  ║
				 |           │   ╚════════════════╪═════════════════════════════════╝
				 |           │                    │
				 |           │              ┌─────┴──────┐
				 |           │              │            │
				 |           │           Success      Failure
				 |           │              │            │
				 |           │              ▼            ▼
				 |           │   ┌────────────────┐  ┌────────────┐
				 |           │   │ User exists    │  │ Increment  │
				 |           │   │ locally by     │  │ bad login  │
				 |           │   │ (sub, iss)?    │  │ attempts   │
				 |           │   └───┬────────┬───┘  │ → REJECT   │
				 |           │       │        │      └────────────┘
				 |           │     Yes        No
				 |           │       │        │
				 |           │       ▼        ▼
				 |           │   ┌───────┐ ┌──────────────────┐
				 |           │   │ Use   │ │ Create new       │
				 |           │   │ exist-│ │ AuthUser +       │
				 |           │   │ ing   │ │ ResourceUser     │
				 |           │   │ Auth  │ │  user = sub      │
				 |           │   │ User  │ │  provider = iss  │
				 |           │   │       │ │  password = UUID  │
				 |           │   │       │ │  (dummy, unused) │
				 |           │   └───┬───┘ └────────┬─────────┘
				 |           │       └──────┬───────┘
				 |           │              │
				 |     ┌─────┴──────────────┘
				 |     │
				 |     ▼
				 |┌─────────────┐      ┌──────────────┐
				 |│  SUCCESS    │      │   FAILURE    │
				 |│             │      │              │
				 |│ Reset bad   │      │ Increment    │
				 |│ login       │      │ bad login    │
				 |│ attempts    │      │ attempts     │
				 |│             │      │              │
				 |│ Establish   │      │ Lock if max  │
				 |│ session     │      │ exceeded     │
				 |│             │      │              │
				 |│ Redirect    │      │ Return error │
				 |└─────────────┘      └──────────────┘
				 |```
				 |
				 |### Decision Logic
				 |
				 |The **provider** field on the AuthUser record determines which path is taken:
				 |
				 |- **Local provider** (e.g. the OBP instance URL) → bcrypt password check via `testPassword()`
				 |- **External provider** (e.g. `google.com`) → delegated to the Connector via `checkExternalUserCredentials()`
				 |- **User not found locally** → can still succeed if `connector.user.authentication=true` is set. The system creates a new AuthUser + ResourceUser on the fly from the adapter response.
				 |
				 |The property `connector.user.authentication=true` must be set to enable external credential checking. Without it, external auth is rejected.
				 |
				 |### Verify Credentials Endpoint (POST /users/verify-credentials)
				 |
				 |In addition to the login flows above, OBP v6.0.0 provides a **credential verification endpoint** that validates credentials **without** creating a session or token.
				 |
				 |```
				 |              ┌──────────────────────────────────────────────┐
				 |              │   POST /obp/v6.0.0/users/verify-credentials │
				 |              │                                              │
				 |              │   Body: { username, password, provider }     │
				 |              │                                              │
				 |              │   → Does NOT create session/token            │
				 |              │   → Just validates and returns user info     │
				 |              │   → For external systems to verify creds     │
				 |              └────────────────────┬─────────────────────────┘
				 |                                   │
				 |                                   ▼
				 |                        ┌─────────────────────┐
				 |                        │ authenticatedAccess  │
				 |                        │ (caller must already │
				 |                        │  be logged in)       │
				 |                        └──────────┬──────────┘
				 |                                   │
				 |                                   ▼
				 |                        ┌─────────────────────┐
				 |                        │ Check role:          │
				 |                        │ isSuperAdmin?        │
				 |                        │ OR has               │
				 |                        │ canVerifyUserCreds?  │
				 |                        └──────────┬──────────┘
				 |                                   │
				 |                                   ▼
				 |              ┌────────────────────────────────────────┐
				 |              │  AuthUser.getResourceUserId             │
				 |              │     (username, password)                │
				 |              │                                        │
				 |              │  Same method used by DirectLogin and   │
				 |              │  the login flows above                  │
				 |              └──────────────────┬─────────────────────┘
				 |                                 │
				 |                (same local / external / not-found
				 |                 branching as the login flow above)
				 |                                 │
				 |                                 ▼
				 |                      ┌───────────────────┐
				 |                      │ Locked?           │──Yes──▶ 401
				 |                      └────────┬──────────┘
				 |                               │ No
				 |                               ▼
				 |                      ┌───────────────────┐
				 |                      │ Valid userId?     │──No───▶ 401
				 |                      └────────┬──────────┘
				 |                               │ Yes
				 |                               ▼
				 |                      ┌───────────────────┐
				 |                      │ Provider matches  │
				 |                      │ posted provider?  │──No───▶ 401
				 |                      │ (if non-empty)    │
				 |                      └────────┬──────────┘
				 |                               │ Yes
				 |                               ▼
				 |                      ┌───────────────────┐
				 |                      │ 200 OK            │
				 |                      │ Return UserJson   │
				 |                      │                   │
				 |                      │ NO token created  │
				 |                      │ NO session created│
				 |                      └───────────────────┘
				 |```
				 |
				 |**Key differences from the login flows:**
				 |
				 |1. **Check only** — validates credentials and returns user info, but does not create a session or token
				 |2. **Requires an already-authenticated caller** with `canVerifyUserCredentials` role (or SuperAdmin)
				 |3. **May auto-provision users** — if the local lookup fails and the external fallback via `checkExternalUserViaConnector()` succeeds, a new AuthUser and ResourceUser will be created locally (same behaviour as the web login flow)
				 |4. **Provider matching** — optionally verifies the user's provider matches what was posted (skipped if provider is empty)
				 |
				 |### Key Source Files
				 |
				 |- `AuthUser.scala` — `login()` entry point, `getResourceUserId()`, `checkExternalUserViaConnector()`
				 |- `directlogin.scala` — `getUserId()` with local-then-external fallback
				 |- `Connector.scala` — `checkExternalUserCredentials()` abstract method
				 |- `AkkaConnector_vDec2018.scala` — Akka connector implementation
				 |- `StoredProcedureConnector_vDec2019.scala` — Stored procedure connector implementation
				 |- `Http4s600.scala` — `verifyUserCredentials` endpoint definition
				 |
""")

	glossaryItems += GlossaryItem(
		title = "Mandates",
		description =
			s"""
				 |# Mandates
				 |
				 |## Overview
				 |
				 |A Mandate is a formal agreement between a corporate customer and a bank that defines who can operate an account, what they can do, and under what conditions.
				 |
				 |In OBP, a Mandate is an entity that ties together existing authorisation constructs (Views, ABAC Rules, Challenges) into a single, auditable policy document.
				 |
				 |## Structure
				 |
				 |A Mandate has three parts:
				 |
				 |### 1. Mandate
				 |
				 |The top-level container. It is linked to a bank account and a corporate customer, and holds the legal text, status (ACTIVE, SUSPENDED, EXPIRED, DRAFT), and validity period.
				 |
				 |### 2. Mandate Provisions
				 |
				 |Each provision maps a clause of the mandate to an OBP enforcement mechanism. Provision types:
				 |
				 |- **SIGNATORY_RULE** — defines who can sign and in what combination (e.g., "2 from Panel A" or "1 from Panel A and 1 from Panel B")
				 |- **VIEW_ASSIGNMENT** — links a Signatory Panel to a View, controlling what members of that panel can see and do
				 |- **ABAC_CONDITION** — links to an ABAC rule for attribute-based conditions (e.g., department matching, amount limits)
				 |- **RESTRICTION** — a negative rule that blocks certain operations (e.g., no international payments)
				 |- **NOTIFICATION** — triggers a notification rather than blocking (e.g., alert CFO for payments over a threshold)
				 |
				 |Provisions can specify conditions (e.g., amount thresholds, currency), link to a View, an ABAC Rule, and/or a Challenge type.
				 |
				 |### 3. Signatory Panels
				 |
				 |A Signatory Panel is a named set of users who are authorised to act under the mandate. For example:
				 |
				 |- Panel A: Directors (user-1, user-2, user-3)
				 |- Panel B: Finance team (user-4, user-5)
				 |
				 |Provisions reference panels by ID and specify how many signatories are required from each panel.
				 |
				 |## How it connects to existing OBP features
				 |
				 |- **Views** control what each panel member can see and do on the account (e.g., canSeeTransactionAmount, canAddTransactionRequestToBeneficiary)
				 |- **ABAC Rules** provide attribute-based conditions evaluated at runtime (e.g., user department must match account business unit)
				 |- **Challenges / Maker-Checker** enforce signatory requirements. A provision can require multiple challenges answered by different users from specified panels
				 |- **Corporate Customers** (CORPORATE / SUBSIDIARY types with parent-child hierarchy) represent the legal entities that mandates apply to
				 |
				 |## Example
				 |
				 |ACME Corp has a mandate on their operating account:
				 |
				 |1. Panel A (Directors): user-1, user-2, user-3
				 |2. Panel B (Finance): user-4, user-5
				 |3. Provision: payments < 5,000 EUR require 1 signature from Panel A
				 |4. Provision: payments 5,000-50,000 EUR require 2 signatures from Panel A
				 |5. Provision: payments > 50,000 EUR require 1 from Panel A and 1 from Panel B
				 |
				 |## Enforcement via REQUIRED_CHALLENGE_ANSWERS
				 |
				 |The existing OBP mechanism for requiring multiple signatories on a transaction request is the Account Attribute `REQUIRED_CHALLENGE_ANSWERS`:
				 |
				 |- If the account attribute `REQUIRED_CHALLENGE_ANSWERS` is set to N, the system creates N SCA challenges when a transaction request is made.
				 |- Each challenge is assigned to a user who has access to a View on the account with the `CAN_ANSWER_TRANSACTION_REQUEST_CHALLENGE` permission.
				 |- The transaction request only completes when N challenges have been successfully answered (quorum).
				 |- If `REQUIRED_CHALLENGE_ANSWERS` is not set, the default is 1 (only the initiating user is challenged).
				 |
				 |Combined with the `CAN_BYPASS_MAKER_CHECKER_SEPARATION` View permission:
				 |
				 |- If `CAN_BYPASS_MAKER_CHECKER_SEPARATION` is **false** on the View, the system enforces that the user who created the transaction request (maker) cannot be the same user who answers the challenge (checker).
				 |- If **true**, the same user can both create and approve the transaction request.
				 |
				 |A Mandate Provision of type `SIGNATORY_RULE` maps to this mechanism:
				 |
				 |1. The provision's `signatory_requirements` (e.g., "2 from Panel A") determines the value of `REQUIRED_CHALLENGE_ANSWERS` on the account.
				 |2. The panel members are granted access to a View that has `CAN_ANSWER_TRANSACTION_REQUEST_CHALLENGE = true`.
				 |3. The View's `CAN_BYPASS_MAKER_CHECKER_SEPARATION` is set to `false` to enforce separation of duties.
				 |
				 |## API Endpoints
				 |
				 |Mandates, Provisions, and Signatory Panels each have CRUD endpoints under the Mandate tag.
				 |
				 |All endpoints require bank-level roles (e.g., CanCreateMandate, CanGetMandateProvision, CanUpdateSignatoryPanel).
				 |
""")

	glossaryItems += GlossaryItem(
		title = "SDKs",
		description =
			s"""
				 |# SDKs
				 |
				 |OBP SDKs (Software Development Kits) are client libraries that make it easier to interact with the OBP API from various programming languages.
				 |
				 |SDKs are available for multiple languages including Python, Java, Scala, PHP, C#, Javascript and more.
				 |
				 |For more information see [OBP SDKs on GitHub](https://github.com/OpenBankProject/OBP-SDKs).
				 |
""")

	glossaryItems += GlossaryItem(
		title = "Chat",
		description =
			s"""
				 |# Chat
				 |
				 |OBP provides a built-in Chat / Messaging API that allows users and applications to communicate within the platform.
				 |
				 |Chat Rooms can be scoped to a specific Bank (bank-level) or be system-wide (system-level).
				 |
				 |## Key Concepts
				 |
				 |### Chat Rooms
				 |A Chat Room is a named space where participants exchange messages.
				 |
				 |A system-level room called **general** is created automatically at startup with **is_open_room = true** — meaning every authenticated user can read and send messages without needing an explicit Participant record.
				 |
				 |Each room has:
				 |- A unique **joining key** (UUID) that can be shared to invite others. The key can be refreshed to revoke access.
				 |- A **name** that is unique within its scope (per bank, or globally for system-level rooms).
				 |- An optional **bank_id** — if set, the room is scoped to that bank. If empty, it is a system-level room.
				 |- An **is_open_room** flag — if true, all authenticated users are treated as implicit participants without needing a database record. They can read and send messages but have no special permissions.
				 |
				 |### Participants
				 |A Participant is a user or consumer (application/bot) that belongs to a Chat Room. Participants can:
				 |- Send and read messages.
				 |- Have a granular **permissions** list that controls what management actions they can perform.
				 |- Optionally specify a **webhook_url** to receive HTTP POST notifications for room events (new messages, mentions, etc.).
				 |
				 |Participants join rooms by presenting the room's joining key. The room creator automatically receives all permissions.
				 |
				 |### Participant Permissions
				 |Permissions are stored as a list on each Participant record. Possible values:
				 |- **can_delete_message** — delete any message in the room
				 |- **can_remove_participant** — remove other participants from the room
				 |- **can_refresh_joining_key** — regenerate the room's joining key
				 |- **can_update_room** — edit the room name and description
				 |- **can_manage_permissions** — grant or revoke permissions for other participants, and add participants directly
				 |
				 |Any participant can send messages, read messages, and add emoji reactions without special permissions. A participant can also remove themselves (leave the room) without needing the can_remove_participant permission.
				 |
				 |### OBP-Level Roles
				 |In addition to room-level permissions, OBP Roles provide platform-wide moderation:
				 |- **CanDeleteBankChatRoom** — delete any chat room within a bank
				 |- **CanDeleteSystemChatRoom** — delete any system-level chat room
				 |- **CanArchiveBankChatRoom** — archive any chat room within a bank
				 |- **CanArchiveSystemChatRoom** — archive any system-level chat room
				 |
				 |Bank-scoped roles apply per bank; system-scoped roles apply to system-level chat rooms. Both kinds apply regardless of room-level permissions.
				 |
				 |### Consumer / Bot Participation
				 |API Consumers (applications) can participate in chat rooms alongside human users. A Participant record stores either a user_id or a consumer_id (not both). This enables automated assistants, notification bots, and integrations.
				 |
				 |### Messages
				 |Messages support:
				 |- **@mentions** — the mentioned_user_ids field tracks which users are referenced in a message.
				 |- **Threading** — a message can reference a thread_id (the root message) to form a conversation thread.
				 |- **Editing** — only the sender can edit their own message.
				 |- **Soft deletion** — messages are marked as deleted rather than removed, preserving audit trails.
				 |- **Emoji reactions** — participants can react to messages with emoji. Each user can add a given emoji to a message only once.
				 |
				 |### Typing Indicators
				 |Typing state is ephemeral and stored in Redis with a short TTL (5 seconds). No database records are created.
				 |
				 |### Polling
				 |Clients retrieve new messages by polling the GET messages endpoint with a **since** parameter (timestamp). This avoids the complexity of WebSocket infrastructure while providing a simple, reliable mechanism for near-real-time updates.
				 |
				 |### gRPC Streaming (real-time)
				 |For clients that need true real-time updates without polling, OBP exposes a **ChatStreamService** over gRPC (see `chat.proto`, package `code.obp.grpc.chat.g1`). It provides four server-streaming / bidirectional RPCs:
				 |- **StreamMessages(StreamMessagesRequest) → stream ChatMessageEvent** — push new/edited/deleted messages for a given chat room as they happen.
				 |- **StreamTyping(stream TypingEvent) → stream TypingIndicator** — bidirectional stream: clients send their own typing state, server fans out typing indicators from other participants.
				 |- **StreamPresence(StreamPresenceRequest) → stream PresenceEvent** — online/offline updates for participants in a room.
				 |- **StreamUnreadCounts(StreamUnreadCountsRequest) → stream UnreadCountEvent** — per-room unread counters for the authenticated user.
				 |
				 |gRPC calls are authenticated via the same credentials as REST (see `AuthInterceptor`). The REST polling endpoints remain the canonical API; the gRPC streams are an optional push channel for clients that want lower latency and less request overhead.
				 |
				 |## API Endpoints
				 |
				 |All chat REST endpoints are available in two forms:
				 |- **Bank-scoped**: /banks/BANK_ID/chat-rooms/...
				 |- **System-level**: /chat-rooms/...
				 |
				 |See the API Explorer for the full list of Chat endpoints, tagged with **Chat**. For the real-time streaming surface, see `chat.proto` / `ChatStreamServiceImpl`.
				 |
""")

	glossaryItems += GlossaryItem(
		title = "Chat Room",
		description =
			s"""
				 |# Chat Room
				 |
				 |A **Chat Room** is a named space where users and consumers (apps/bots) exchange messages. Each room is either **system-level** or scoped to a single **bank**.
				 |
				 |See also the broader [Chat](/glossary#Chat) entry, which covers messages, threads, reactions, mentions, typing indicators, gRPC streaming, and the full permissions model.
				 |
				 |## Identity and scope
				 |- **chat_room_id** — UUID identifying the room.
				 |- **bank_id** — non-empty for bank-scoped rooms, empty string for system-level rooms.
				 |- **name** — unique within scope (per bank, or globally for system-level).
				 |
				 |## Open vs Closed rooms
				 |The **is_open_room** flag controls how membership works:
				 |
				 |- **Closed room** (`is_open_room = false`): only users with an explicit Participant record can read or post. New members must present the room's joining_key.
				 |- **Open room** (`is_open_room = true`): every authenticated user is treated as an **implicit participant** (see `ChatPermissions.isParticipant`). They can read and post without a Participant record, but have no special permissions. Open rooms also appear in `GET /chat-rooms` for everyone, not just existing members.
				 |
				 |The auto-created system room **general** is open by default.
				 |
				 |## Joining keys
				 |Each Chat Room has a **joining_key** (UUID). To join a room explicitly, a user calls `POST /chat-room-participants` with `{ joining_key }` — the key alone identifies the room.
				 |
				 |- For closed rooms, the key is the only way in. It is exposed in `GET /chat-rooms` and `GET /chat-rooms/{id}` to existing participants only, who then share it out-of-band (chat, email, link).
				 |- For open rooms, the key still exists but is rarely needed, since users are already implicit participants. Joining explicitly creates a Participant record so the user can be granted permissions, mute the room, or track last_read_at.
				 |- The key can be rotated by a participant with the **can_refresh_joining_key** permission, via `PUT /chat-rooms/{id}/joining-key`. The old key becomes invalid.
				 |
				 |## Lifecycle flags
				 |- **is_archived** — archived rooms reject new messages and new participants but remain readable for audit.
				 |- **created_by / created_by_username / created_by_provider** — identifies the room creator. The creator is granted all participant permissions.
				 |
				 |## Endpoints
				 |Each Chat Room operation has both a system-level and bank-scoped variant:
				 |- System-level: `/obp/v6.0.0/chat-rooms/...`
				 |- Bank-scoped: `/obp/v6.0.0/banks/BANK_ID/chat-rooms/...`
				 |
				 |See the API Explorer with the **Chat** tag for the full list.
				 |
""")

	glossaryItems += GlossaryItem(
		title = "Signal Channels",
		description =
			s"""
				 |# Signal Channels
				 |
				 |**Signal Channels** are short-lived, Redis-backed message channels for lightweight coordination between AI agents and other OBP consumers — service discovery, task hand-off, presence announcements. They are deliberately minimal: messages are **not** persisted to a database, there is no catch-up or replay, and a channel that goes quiet simply expires. Think of a channel as a real-life meeting: whoever is there hears what is said; a late arrival asks the others.
				 |
				 |Not to be confused with [Chat](/glossary#Chat), which is the persistent, human-facing messaging surface (rooms, threads, reactions, read markers).
				 |
				 |## Lifecycle
				 |- Channels are auto-created on first publish; no registration step.
				 |- On this instance a channel expires ${code.api.cache.RedisMessaging.channelTtlSeconds} seconds after its last publish, and holds at most ${code.api.cache.RedisMessaging.channelMaxMessages} messages (oldest are trimmed).
				 |- Channel names are 1 to 128 characters from letters, digits, dot, underscore and hyphen.
				 |
				 |## Constraints on published messages
				 |All publishing requires authentication. Beyond that, three server-side checks protect the platform — the envelope, not the meaning, of what agents say:
				 |
				 |1. **Size cap** — the whole publish request body may be up to ${code.signal.SignalContentPolicy.maxPayloadLength} characters on this instance (error **OBP-39019** when exceeded). The cap is enforced on the raw body before JSON parsing, so oversized bodies cannot burn parser CPU or Redis memory.
				 |2. **Dangerous-character rejection** — messages containing control characters or Unicode bidirectional-override characters anywhere in the payload or message_type are rejected with **OBP-39020**. See "Why bidirectional-override characters are rejected" below.
				 |3. **Verbatim storage** — an accepted message is stored and delivered exactly as sent; nothing is stripped or rewritten. Agents may therefore hash, sign, or byte-compare payloads. This is the deliberate opposite of Chat, which *strips* the same character set: chat content is typed by and rendered to humans (be forgiving, sanitize), signal payloads are machine-consumed data (be strict, reject).
				 |
				 |## Privacy and roles
				 |- A message with **to_user_id** set is visible only to its sender and that recipient; without it, the message is a broadcast visible to all channel readers.
				 |- **CanGetSignalStats** — read message counts and TTLs across all channels.
				 |- **CanDeleteSignalChannel** — delete a channel and all its messages immediately. Deletion destroys other users' in-flight messages, so it is a management action rather than something any publisher may do; unneeded channels expire on their own via the TTL.
				 |
				 |## Why bidirectional-override characters are rejected
				 |Unicode includes invisible formatting characters that reverse or reorder how text is *displayed* without changing the bytes a parser sees — the override family U+202A to U+202E, the isolate family U+2066 to U+2069, and the marks U+200E, U+200F and U+061C. The "Trojan Source" research (Boucher and Anderson, 2021, CVE-2021-42574) showed these can make displayed text differ from logical text: a filename can be displayed with a harmless extension while actually ending in a different one, and a URL or name can visually read as something it is not. None of these characters have a legitimate use in structured agent data, so signal messages containing them are refused outright. (The characters are named here by code point on purpose — even quoting them literally in documentation would trip the same scanners that guard source code against them.)
				 |
				 |The check runs on the **parsed** JSON, not the raw request body: JSON's backslash-u escape syntax means a body that is pure ASCII on the wire can still parse to a string containing a bidi override, so a wire-level check would miss it.
				 |
				 |## Payloads are data, not instructions
				 |Signal channels are readable and writable by any authenticated consumer on the instance. If your agent feeds received payloads to an LLM, treat them as **untrusted data, never as instructions** — the character checks above stop display-layer trickery, but no server-side check can stop a payload from *saying* something misleading. Prompt-injection defence belongs in the consuming agent.
				 |
				 |## Endpoints
				 |See the API Explorer tags **Signal** / **AI-Agent**: list channels, channel info, channel stats, publish message, get messages (offset/limit polling), delete channel — under `/obp/v6.0.0/signal/channels/...`. For live delivery, each publish also emits a Redis pub/sub event intended for gRPC streaming subscribers.
				 |
""")

	glossaryItems += GlossaryItem(
		title = "OBP-MCP",
		description =
			s"""
				 |# OBP-MCP
				 |
				 |**OBP-MCP** is a [Model Context Protocol](https://modelcontextprotocol.io) server for the Open Bank Project API. It lets AI assistants (Claude, Opey, IDE agents, custom LLM tooling) discover and call OBP-API endpoints as MCP *tools*, without hard-coding any knowledge of the 600+ endpoints.
				 |
				 |Repository: [github.com/OpenBankProject/OBP-MCP](https://github.com/OpenBankProject/OBP-MCP)
				 |
				 |## What it does
				 |
				 |OBP-MCP is a thin protocol bridge. AI clients speak **MCP** to it; it speaks **HTTPS / REST** to OBP-API on their behalf, attaching the user's OAuth token or Consent-JWT.
				 |
				 |```
				 |┌──────────────────┐   MCP    ┌────────────────────────┐   HTTPS    ┌──────────────┐
				 |│  AI client       │ ───────▶ │      OBP-MCP           │ ─────────▶ │   OBP-API    │
				 |│  (Claude, Opey,  │ ◀─────── │   (FastMCP server)     │ ◀───────── │              │
				 |│   IDE agent)     │  tools   │                        │  JSON      │              │
				 |└──────────────────┘          └────────────────────────┘            └──────────────┘
				 |```
				 |
				 |## Architecture diagram
				 |
				 |The full picture — Portal/API Explorer, Opey, external MCP clients (Claude Code, Claude Desktop, IDE agents), OBP-OIDC, the numbered consent flow, and OBP-API down to the core banking systems:
				 |
				 |![How Opey, Claude Code and OBP-MCP call OBP-API](https://github.com/user-attachments/assets/d3ff5c10-7167-4034-98f7-c53a323bf985)
				 |
				 |The editable master is a Lucidchart document linked from the [OBP-MCP README](https://github.com/OpenBankProject/OBP-MCP#architecture).
				 |
				 |## Three-step discovery + call (no RAG, no vector DB)
				 |
				 |OBP-MCP avoids embedding the 4 MB OpenAPI spec into the LLM's context. Instead it exposes three tools that work together:
				 |
				 |1. **`list_endpoints_by_tag(tags)`** — returns lightweight summaries (~50–100 tokens each) from a local `endpoint_index.json`. Lets the LLM narrow down to a handful of candidate endpoints by tag (e.g. `Account`, `Transaction-Request`, `Consent`).
				 |2. **`get_endpoint_schema(endpoint_id)`** — lazy-loads the full OpenAPI schema for one endpoint from a local `endpoint_schemas.json`.
				 |3. **`call_obp_api(endpoint_id, path_params, query_params, body, headers)`** — actually executes the HTTP request against the live OBP-API.
				 |
				 |Two further tools cover the glossary itself: **`list_glossary_terms(search_query)`** and **`get_glossary_term(term_id)`**, backed by a local `glossary_index.json` of 800+ banking terms.
				 |
				 |## Three kinds of traffic
				 |
				 |It is important to understand that OBP-MCP is **not** a documentation lookup tool — it makes real, authenticated business calls:
				 |
				 |- **Documentation / discovery** — `list_endpoints_by_tag`, `get_endpoint_schema`, glossary tools. Served from local JSON, no network.
				 |- **Business calls** — `call_obp_api` proxies whatever the endpoint declares: `GET /banks/{BANK_ID}/accounts`, `POST .../transaction-requests/SEPA`, `PUT /accounts/{ACC}/label`, `DELETE /my/consents/{CONSENT_ID}`, etc. Real money / data moves.
				 |- **Index refresh** — at startup and on a timer, OBP-MCP re-fetches OBP's [Resource Docs](/glossary#Resource-Doc) and swagger to rebuild the local indexes, so discovery stays fast and offline.
				 |
				 |## Authentication and authorization
				 |
				 |OBP-MCP supports several modes via the `AUTH_PROVIDER` environment variable for client-to-MCP auth:
				 |
				 || Mode           | Use case                              | Notes                                              |
				 ||----------------|---------------------------------------|----------------------------------------------------|
				 || `bearer-only`  | Internal agents (e.g. Opey)           | JWT validation only, multi-issuer                  |
				 || `obp-oidc`     | External MCP clients                  | Full OAuth 2.1 + Dynamic Client Registration       |
				 || `keycloak`     | External MCP clients                  | OAuth 2.1 + minimal DCR proxy workaround           |
				 || `none`         | Development / testing                 | No auth required                                   |
				 |
				 |For onward calls to OBP-API, `OBP_AUTHORIZATION_VIA` selects:
				 |
				 |- **`oauth`** — pulls the access token from the MCP request context and sends `Authorization: Bearer ...`.
				 |- **`consent`** — the default mode for user-facing deployments. `call_obp_api` requires a `Consent-JWT` for **every** endpoint except a small allowlist of genuinely public ones (`GET /root`, the bank directory `/banks` and `/banks/{BANK_ID}`, glossary, resource-docs, API metadata). For any other endpoint called without a `Consent-JWT`, the tool returns a `consent_required` payload — required roles, bank / account / view scope, and `requires_view_access` / `is_user_scoped` flags — so the client can build the right consent and retry with a `Consent-JWT` header. Consent is required **by default**, not only for role-gated endpoints, because many identity-bound endpoints (`/users/current`, `/my/*`, account-access-via-view endpoints) declare no roles yet still need the caller's identity — a role-only gate would call them unauthenticated. The allowlist is deliberately conservative: a wrongly-excluded endpoint costs only an extra prompt, whereas wrongly skipping consent fails silently.
				 |- **`none`** — calls OBP unauthenticated (only useful for genuinely public endpoints).
				 |
				 |This means the consent flow is enforced at the MCP layer, not just at OBP-API: an agent cannot accidentally call a privileged endpoint without explicit user consent.
				 |
				 |## Why it matters
				 |
				 |OBP-MCP is the canonical way to make Open Bank Project endpoints **agent-callable**. Instead of teaching every LLM about every endpoint up front, the LLM is given five generic tools and lets the indexes and schemas guide it to the right call at runtime. The same server can serve internal agents (Opey) and external clients (Claude Desktop, IDE plugins, third-party agents) by switching auth providers.
				 |
				 |See also: [Opey](/glossary#Opey), [Resource Doc](/glossary#Resource-Doc), [Consent](/glossary#Consent), [Authentication: OAuth 2.0](/glossary#Authentication:-OAuth-2.0).
				 |
""")


	glossaryItems += GlossaryItem(
		title = "Opey",
		description =
			s"""
				 |# Opey
				 |
				 |**Opey** (current generation: **Opey II**) is the Open Bank Project's agentic AI assistant — a chatbot that lets users explore and operate the OBP API in natural language. It is built on [LangGraph](https://www.langchain.com/langgraph), is provider-agnostic across LLMs (Anthropic, OpenAI, Ollama), and is the chat backend used by **OBP-Portal**.
				 |
				 |Repository: [github.com/OpenBankProject/OBP-Opey-II](https://github.com/OpenBankProject/OBP-Opey-II)
				 |
				 |## Opey is an agent. OBP-MCP is its tool surface.
				 |
				 |Since [OBP-MCP](/glossary#OBP-MCP) was introduced, Opey has been refactored from a self-contained chatbot (with its own endpoint search, glossary search, and OBP HTTP client baked in) into a focused **agent** that *consumes* OBP-MCP as its primary tool source.
				 |
				 |![How Opey, Claude Code and OBP-MCP call OBP-API](https://github.com/user-attachments/assets/d3ff5c10-7167-4034-98f7-c53a323bf985)
				 |
				 |Besides the MCP path shown above, Opey makes some direct HTTP calls to OBP-API for its own infrastructure (session validation via `/users/current`, admin DirectLogin operations, persisting LangGraph checkpoints as dynamic entities, and health probes) — see the architecture section of the [Opey README](https://github.com/OpenBankProject/OBP-Opey-II#architecture-how-opey-reaches-the-obp-api) for the detail diagram.
				 |
				 |Opey's `mcp_servers.json` typically points at a running OBP-MCP instance:
				 |
				 |```json
				 |{
				 |  "servers": [
				 |    {
				 |      "name": "obp",
				 |      "url": "http://0.0.0.0:9100/mcp",
				 |      "transport": "http",
				 |      "requires_auth": true
				 |    }
				 |  ]
				 |}
				 |```
				 |
				 |The Opey README puts it bluntly: *"As a minimum, Opey should be connected to OBP-MCP, or it won't know anything about the Open Bank Project except for what you put in the system prompt."*
				 |
				 |## What OBP-MCP took over
				 |
				 |Subsystems that used to live in Opey are now generic MCP tools any client can use:
				 |
				 || Old Opey responsibility                                                              | Now in OBP-MCP                                              |
				 ||--------------------------------------------------------------------------------------|-------------------------------------------------------------|
				 || Endpoint Retrieval RAG pipeline (vector store of swagger, query reformulation, etc.) | `list_endpoints_by_tag` + `get_endpoint_schema`             |
				 || Glossary Retrieval RAG pipeline                                                      | `list_glossary_terms` + `get_glossary_term`                 |
				 || `OBPClient` (aiohttp + OAuth + consent JWT) — the actual HTTP layer to OBP-API       | `call_obp_api` (`oauth` / `consent` / `none` modes)         |
				 || "Which endpoint should I call?" logic baked into the agent                           | Externalised — any MCP client can now discover and call     |
				 |
				 |## What Opey still uniquely does
				 |
				 |OBP-MCP is stateless and has no model — it cannot reason, plan, or hold a conversation. Everything below is what makes Opey *Opey*:
				 |
				 |- **The LLM loop itself.** Opey runs the actual reasoning via a LangGraph state machine (`START → Opey Agent → Tools → Sanitize → Opey → Summarize → END`), with **task follow-through**: when a tool call fails (e.g. missing entitlement), Opey reuses tools to self-correct instead of bouncing the problem back to the user.
				 |- **Human-in-the-loop approval — richer than MCP's `consent_required`.** A `ToolRegistry` classifies operations as **SAFE / MODERATE / DANGEROUS / CRITICAL**. An `ApprovalManager` persists "approve once / session / user / workspace" decisions with TTLs. The human-review node only interrupts when truly needed. OBP-MCP just *says* consent is required; Opey decides **how** to ask, **whether** to ask again, and **remembers** the answer.
				 |- **Conversation state.** SQLite-backed LangGraph checkpoints (`checkpoints.db`), token counting, automatic summarisation when approaching the model context limit, and graceful degradation in long sessions.
				 |- **The streaming chat service.** FastAPI endpoints (`POST /invoke`, `POST /stream` SSE, `POST /submit_approval`, `GET /user/consent`, `GET /status`) — this is what OBP-Portal's chat UI actually talks to. Streaming events are produced by dedicated processors (token, tool, human-review, metadata, end).
				 |- **Session, auth, usage.** OBP user session management, consent-JWT parsing for user identification, rate limiting, usage tracking, and an admin-client singleton for system-level operations.
				 |- **Domain-tuned system prompt.** Behavioural guidelines such as *Tool-First / Knowledge-Second*, *No Hallucination*, *Proactive Verification*, and *Transparent Errors*. Configurable via `OPEY_SYSTEM_PROMPT`.
				 |- **Model abstraction.** Provider-agnostic via `MODEL_PROVIDER` / `MODEL_NAME` — swap Claude for GPT or a local Ollama model without touching the graph. New models are registered in `MODEL_CONFIGS` (`src/agent/utils/model_factory.py`).
				 |- **Evaluation framework.** Parameter-sweep experiments over batch size, k-value, retry thresholds; CSV export of precision / recall / latency P50–P99; combined scoring (e.g. 70% recall + 30% speed) to find sweet spots. Something a tool surface like MCP has no concept of.
				 |
				 |## One-line summary
				 |
				 |**OBP-MCP is the *tool surface* over OBP-API. Opey II is the *agent* that drives it.** Before OBP-MCP, Opey had to be both. Now OBP-MCP provides discovery and authenticated calls as a generic, multi-client surface (Claude Desktop, IDE plugins, third-party agents can all use it), and Opey II becomes a thinner, more focused orchestrator: planning, approvals, conversation state, streaming, and the chat UX that OBP-Portal embeds.
				 |
				 |See also: [OBP-MCP](/glossary#OBP-MCP), [Resource Doc](/glossary#Resource-Doc), [Consent](/glossary#Consent), [Authentication: OAuth 2.0](/glossary#Authentication:-OAuth-2.0).
				 |
""")


	///////////////////////////////////////////////////////////////////
	// NOTE! Some glossary items are generated in ExampleValue.scala
//////////////////////////////////////////////////////////////////

}

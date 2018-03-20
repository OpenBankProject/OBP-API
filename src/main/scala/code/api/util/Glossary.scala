package code.api.util

import code.api.util.APIUtil.{getObpApiRoot, getServerUrl}

import scala.collection.mutable.ArrayBuffer

object Glossary {

    case class GlossaryItem(
                            title: String,
                            description: String
                            )





    val glossaryItems = ArrayBuffer[GlossaryItem]()

	  glossaryItems += GlossaryItem(
		title = "Account",
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
		"""
		  |An identifier for the account that MUST NOT leak the account number or other identifier nomrally used by the customer or bank staff.
		  |It SHOULD be a UUID. It MUST be unique in combination with the BANK_ID. ACCOUNT_ID is used in many URLS so it should be considered public.
		  |(We do NOT use account number in URLs since URLs are cached and logged all over the internet.)
		  |In local / sandbox mode, ACCOUNT_ID is generated as a UUID and stored in the database.
		  |In non sandbox modes (Kafka etc.), ACCOUNT_ID is mapped to core banking account numbers / identifiers at the South Side Adapter level.
		  |ACCOUNT_ID is used to link Metadata and Views so it must be persistant and known to the North Side (OBP-API).
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
		"""
		  |An identifier that uniquely identifies the bank or financial institution on the OBP-API instance.
		  |
		  |It is typically a human (developer) friendly string for ease of identification.
			|
			|It SHOULD NOT contain spaces.
			|
		  |In sandbox mode it typically has the form: "financialinstitutuion.sequencennumber.region.language". e.g. "bnpp-irb.01.it.it"
			|
			|For production, it's value could be the BIC of the institution.
		 """)

	  glossaryItems += GlossaryItem(
		title = "Consumer",
		description =
		"""
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
		  """
			|The identifier that MUST NOT leak the customer number or other identifier nomrally used by the customer or bank staff. It SHOULD be a UUID and MUST be unique in combination with BANK_ID.
			|
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
		  """
			|An identifier that MUST NOT leak the user name or other identifier nomrally used by the customer or bank staff. It SHOULD be a UUID and MUST be unique on the OBP instance.
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
		title = "Direct Login",
		description =
		  s"""
			|## TL;DR
			|
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
			 |	{  "name":"_test", "description":"good", "is_public":true, "which_alias_to_use":"good", "hide_metadata_if_alias_used":false,  "allowed_actions": ["can_see_transaction_this_bank_account", "can_see_transaction_other_bank_account", "can_see_transaction_metadata", "can_see_transaction_label", "can_see_transaction_amount", "can_see_transaction_type", "can_see_transaction_currency", "can_see_transaction_start_date", "can_see_transaction_finish_date", "can_see_transaction_balance", "can_see_comments", "can_see_narrative", "can_see_tags", "can_see_images", "can_see_bank_account_owners", "can_see_bank_account_type", "can_see_bank_account_balance", "can_see_bank_account_currency", "can_see_bank_account_label", "can_see_bank_account_national_identifier", "can_see_bank_account_swift_bic", "can_see_bank_account_iban", "can_see_bank_account_number", "can_see_bank_account_bank_name", "can_see_other_account_national_identifier", "can_see_other_account_swift_bic", "can_see_other_account_iban", "can_see_other_account_bank_name", "can_see_other_account_number", "can_see_other_account_metadata", "can_see_other_account_kind", "can_see_more_info", "can_see_url", "can_see_image_url", "can_see_open_corporates_url", "can_see_corporate_location", "can_see_physical_location", "can_see_public_alias", "can_see_private_alias", "can_add_more_info", "can_add_url", "can_add_image_url", "can_add_open_corporates_url", "can_add_corporate_location", "can_add_physical_location", "can_add_public_alias", "can_add_private_alias", "can_delete_corporate_location", "can_delete_physical_location", "can_edit_narrative", "can_add_comment", "can_delete_comment", "can_add_tag", "can_delete_tag", "can_add_image", "can_delete_image", "can_add_where_tag", "can_see_where_tag", "can_delete_where_tag", "can_create_counterparty", "can_see_bank_routing_scheme", "can_see_bank_routing_address", "can_see_bank_account_routing_scheme", "can_see_bank_account_routing_address", "can_see_other_bank_routing_scheme", "can_see_other_bank_routing_address", "can_see_other_account_routing_scheme", "can_see_other_account_routing_addres"]}
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
			|	{  "name":"_test", "description":"good", "is_public":false, "which_alias_to_use":"accountant", "hide_metadata_if_alias_used":false,  "allowed_actions": ["can_see_transaction_this_bank_account", "can_see_transaction_other_bank_account", "can_see_transaction_metadata", "can_see_transaction_label", "can_see_transaction_amount", "can_see_transaction_type", "can_see_transaction_currency", "can_see_transaction_start_date", "can_see_transaction_finish_date", "can_see_transaction_balance", "can_see_comments", "can_see_narrative", "can_see_tags", "can_see_images", "can_see_bank_account_owners", "can_see_bank_account_type", "can_see_bank_account_balance", "can_see_bank_account_currency", "can_see_bank_account_label", "can_see_bank_account_national_identifier", "can_see_bank_account_swift_bic", "can_see_bank_account_iban", "can_see_bank_account_number", "can_see_bank_account_bank_name", "can_see_other_account_national_identifier", "can_see_other_account_swift_bic", "can_see_other_account_iban", "can_see_other_account_bank_name", "can_see_other_account_number", "can_see_other_account_metadata", "can_see_other_account_kind", "can_see_more_info", "can_see_url", "can_see_image_url", "can_see_open_corporates_url", "can_see_corporate_location", "can_see_physical_location", "can_see_public_alias", "can_see_private_alias", "can_add_more_info", "can_add_url", "can_add_image_url", "can_add_open_corporates_url", "can_add_corporate_location", "can_add_physical_location", "can_add_public_alias", "can_add_private_alias", "can_delete_corporate_location", "can_delete_physical_location", "can_edit_narrative", "can_add_comment", "can_delete_comment", "can_add_tag", "can_delete_tag", "can_add_image", "can_delete_image", "can_add_where_tag", "can_see_where_tag", "can_delete_where_tag", "can_create_counterparty", "can_see_bank_routing_scheme", "can_see_bank_routing_address", "can_see_bank_account_routing_scheme", "can_see_bank_account_routing_address", "can_see_other_bank_routing_scheme", "can_see_other_bank_routing_address", "can_see_other_account_routing_scheme", "can_see_other_account_routing_addres"]}
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
      



}

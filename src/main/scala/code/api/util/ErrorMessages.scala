package code.api.util

object ErrorMessages {
  import code.api.util.APIUtil._
  // Notes to developers. Please:
  // 1) Follow (the existing) grouping of messages
  // 2) Stick to existing terminology e.g. use "invalid" or "incorrect" rather than "wrong"
  // 3) Before adding a new message, check that you can't use one that already exists.
  // 4) Use Proper Names for OBP Resources.
  // 5) Don't use abbreviations.
  // 6) Any messaage defined here should be considered "fair game" to return over the API. Thus:
  // 7) Since the existance of "OBP-..." in a message is used to determine if we should display to a user if display_internal_errors=false, do *not* concatenate internal or core banking system error messages to these strings.

  // Infrastructure / config level messages (OBP-00XXX)
  val HostnameNotSpecified = "OBP-00001: Hostname not specified. Could not get hostname from Props. Please edit your props file. Here are some example settings: hostname=http://127.0.0.1:8080 or hostname=https://www.example.com"
  val DataImportDisabled  = "OBP-00002: Data import is disabled for this API instance."
  val TransactionDisabled = "OBP-00003: Transaction Requests is disabled in this API instance."

  @deprecated("This is too generic","25-06-2017")
  val ServerAddDataError = "OBP-00004: Server error: could not add message" // Do not use this

  val PublicViewsNotAllowedOnThisInstance = "OBP-00005: Public views not allowed on this instance. Please set allow_public_views = true in props files. "


  val RemoteDataSecretMatchError = "OBP-00006: Remote data secret cannot be matched! Check OBP-API and OBP-Storage Props values for remotedata.hostname, remotedata.port and remotedata.secret." // (was OBP-20021)
  val RemoteDataSecretObtainError = "OBP-00007: Remote data secret cannot be obtained! Check OBP-API and OBP-Storage Props values for remotedata.hostname, remotedata.port and remotedata.secret." // (was OBP-20022)

  val ApiVersionNotSupported = "OBP-00008: The API version you called is not enabled on this server. Please contact your API administrator or use another version."

  val FirehoseViewsNotAllowedOnThisInstance = "OBP-00009: Firehose views not allowed on this instance. Please set allow_firehose_views = true in props files. "
  val MissingPropsValueAtThisInstance = "OBP-00010: Missing props value at this API instance - "
  val NoValidElasticsearchIndicesConfigured = "OBP-00011: No elasticsearch indices are allowed on this instance. Please set es.warehouse.allowed.indices = index1,index2 (or = ALL for all). "

  // General messages (OBP-10XXX)
  val InvalidJsonFormat = "OBP-10001: Incorrect json format."
  val InvalidNumber = "OBP-10002: Invalid Number. Could not convert value to a number."
  val InvalidISOCurrencyCode = "OBP-10003: Invalid Currency Value. It should be three letters ISO Currency Code. "
  val FXCurrencyCodeCombinationsNotSupported = "OBP-10004: ISO Currency code combination not supported for FX. Please modify the FROM_CURRENCY_CODE or TO_CURRENCY_CODE. "
  val InvalidDateFormat = "OBP-10005: Invalid Date Format. Could not convert value to a Date."
  val InvalidCurrency = "OBP-10006: Invalid Currency Value."
  val IncorrectRoleName = "OBP-10007: Incorrect Role name: "
  val CouldNotTransformJsonToInternalModel = "OBP-10008: Could not transform Json to internal model."
  val CountNotSaveOrUpdateResource = "OBP-10009: Could not save or update resource."
  val NotImplemented = "OBP-10010: Not Implemented "
  val InvalidFutureDateValue = "OBP-10011: future_date has to be in future."
  val maximumLimitExceeded = "OBP-10012: Invalid value. Maximum number is 10000."
  val attemptedToOpenAnEmptyBox = "OBP-10013: Attempted to open an empty Box."
  val cannotDecryptValueOfProperty = "OBP-10014: Could not decrypt value of property "
  val AllowedValuesAre = "OBP-10015: Allowed values are: "
  val InvalidFilterParameterFormat = "OBP-10016: Incorrect filter Parameters in URL. "
  val InvalidUrl = "OBP-10017: Incorrect URL Format. "
  val TooManyRequests = "OBP-10018: Too Many Requests."
  val InvalidBoolean = "OBP-10019: Invalid Boolean. Could not convert value to a boolean type."

  // General Sort and Paging
  val FilterSortDirectionError = "OBP-10023: obp_sort_direction parameter can only take two values: DESC or ASC!" // was OBP-20023
  val FilterOffersetError = "OBP-10024: wrong value for obp_offset parameter. Please send a positive integer (=>0)!" // was OBP-20024
  val FilterLimitError = "OBP-10025: wrong value for obp_limit parameter. Please send a positive integer (=>1)!" // was OBP-20025
  val FilterDateFormatError = s"OBP-10026: Failed to parse date string. Please use this format ${DateWithMsFormat.toPattern}!" // OBP-20026
  val FilterAnonFormatError = s"OBP-10028: anon parameter can only take two values: TRUE or FALSE!"
  val FilterDurationFormatError = s"OBP-10029: wrong value for `duration` parameter. Please send a positive integer (=>0)!"

  val InvalidApiVersionString = "OBP-00027: Invalid API Version string. We could not find the version specified."
  val IncorrectTriggerName = "OBP-10028: Incorrect Trigger name: "




  // Authentication / Authorisation / User messages (OBP-20XXX)
  val UserNotLoggedIn = "OBP-20001: User not logged in. Authentication is required!"
  val DirectLoginMissingParameters = "OBP-20002: These DirectLogin parameters are missing: "
  val DirectLoginInvalidToken = "OBP-20003: This DirectLogin token is invalid or expired: "
  val InvalidLoginCredentials = "OBP-20004: Invalid login credentials. Check username/password."
  val UserNotFoundById = "OBP-20005: User not found. Please specify a valid value for USER_ID."
  val UserHasMissingRoles = "OBP-20006: User is missing one or more roles: "
  val UserNotFoundByEmail = "OBP-20007: User not found by email."

  val InvalidConsumerKey = "OBP-20008: Invalid Consumer Key."
  val InvalidConsumerCredentials = "OBP-20009: Invalid consumer credentials"

  val InvalidValueLength = "OBP-20010: Value too long"
  val InvalidValueCharacters = "OBP-20011: Value contains invalid characters"

  val InvalidDirectLoginParameters = "OBP-20012: Invalid direct login parameters"

  val UsernameHasBeenLocked = "OBP-20013: The account has been locked, please contact administrator !"

  val InvalidConsumerId = "OBP-20014: Invalid Consumer ID. Please specify a valid value for CONSUMER_ID."

  val UserNoPermissionUpdateConsumer = "OBP-20015: Only the developer that created the consumer key should be able to edit it, please login with the right user."

  val UnexpectedErrorDuringLogin = "OBP-20016: An unexpected login error occurred. Please try again."

  val UserNoPermissionAccessView = "OBP-20017: Current user does not have access to the view. Please specify a valid value for VIEW_ID."


  val InvalidInternalRedirectUrl = "OBP-20018: Login failed, invalid internal redirectUrl."
  val UserNoOwnerView = "OBP-20019: User does not have access to owner view. "
  val InvalidCustomViewFormat = "OBP-20020: View name must start with `_`. eg: _work, _life "
  val SystemViewsCanNotBeModified = "OBP-20021: System Views can not be modified. Only the created views can be modified."
  val ViewDoesNotPermitAccess = "OBP-20022: View does not permit the access."

  val ConsumerHasMissingRoles = "OBP-20023: Consumer is missing one or more roles: "
  val ConsumerNotFoundById = "OBP-20024: Consumer not found. Please specify a valid value for CONSUMER_ID."
  val ScopeNotFound = "OBP-20025: Scope not found. Please specify a valid value for SCOPE_ID."
  val ConsumerDoesNotHaveScope = "OBP-20026: CONSUMER_ID does not have the SCOPE_ID "

  val UserNotFoundByUsername = "OBP-20027: User not found by username."
  val GatewayLoginMissingParameters = "OBP-20028: These GatewayLogin parameters are missing: "
  val GatewayLoginUnknownError = "OBP-20029: Unknown Gateway login error."
  val GatewayLoginHostPropertyMissing = "OBP-20030: Property gateway.host is not defined."
  val GatewayLoginWhiteListAddresses = "OBP-20031: Gateway login can be done only from allowed addresses."
  val GatewayLoginJwtTokenIsNotValid = "OBP-20040: The JWT is corrupted/changed during a transport."
  val GatewayLoginCannotExtractJwtToken = "OBP-20041: Header, Payload and Signature cannot be extracted from the JWT."
  val GatewayLoginNoNeedToCallCbs = "OBP-20042: There is no need to call CBS"
  val GatewayLoginCannotFindUser = "OBP-20043: User cannot be found. Please initiate CBS communication in order to create it."
  val GatewayLoginCannotGetCbsToken = "OBP-20044: Cannot get the CBSToken response from South side"
  val GatewayLoginCannotGetOrCreateUser = "OBP-20045: Cannot get or create user during GatewayLogin process."
  val GatewayLoginNoJwtForResponse = "OBP-20046: There is no useful value for JWT."

  val UserNotSuperAdmin = "OBP-20050: Logged user is not super admin!"

  val ElasticSearchIndexNotFound = "OBP-20051: Elasticsearch index or indices not found."
  val NotEnoughtSearchStatisticsResults = "OBP-20052: Result set too small. Will not be displayed for reasons of privacy."
  val ElasticSearchEmptyQueryBody = "OBP-20053: The Elasticsearch query body cannot be empty"
  val ElasticSearchDisabled  = "OBP-20056: Elasticsearch is disabled for this API instance."

  // OAuth 2
  val Oauth2IsNotAllowed = "OBP-20201: OAuth2 is not allowed at this instance."
  val Oauth2IJwtCannotBeVerified = "OBP-20202: OAuth2's Access Token cannot be verified."
  val Oauth2ThereIsNoUrlOfJwkSet = "OBP-20203: There is no an URL of OAuth 2.0 server's JWK set, published at a well-known URL."
  val Oauth2BadJWTException = "OBP-20204: Bad JWT error. "
  val Oauth2ParseException = "OBP-20205: Parse error. "

  val InvalidAmount = "OBP-20054: Invalid amount. Please specify a valid value for amount."
  val MissingQueryParams = "OBP-20055: These query parameters are missing: "




  // Resource related messages (OBP-30XXX)
  val BankNotFound = "OBP-30001: Bank not found. Please specify a valid value for BANK_ID."
  val CustomerNotFound = "OBP-30002: Customer not found. Please specify a valid value for CUSTOMER_NUMBER."
  val CustomerNotFoundByCustomerId = "OBP-30046: Customer not found. Please specify a valid value for CUSTOMER_ID."

  val AccountNotFound = "OBP-30003: Account not found. Please specify a valid value for ACCOUNT_ID."
  val CounterpartyNotFound = "OBP-30004: Counterparty not found. The BANK_ID / ACCOUNT_ID specified does not exist on this server."

  val ViewNotFound = "OBP-30005: View not found for Account. Please specify a valid value for VIEW_ID"

  val CustomerNumberAlreadyExists = "OBP-30006: Customer Number already exists. Please specify a different value for BANK_ID or CUSTOMER_NUMBER."
  val CustomerAlreadyExistsForUser = "OBP-30007: The User is already linked to a Customer at the bank specified by BANK_ID"
  val UserCustomerLinksNotFoundForUser = "OBP-30008: User Customer Link not found by USER_ID"
  val AtmNotFoundByAtmId = "OBP-30009: ATM not found. Please specify a valid value for ATM_ID."
  val BranchNotFoundByBranchId = "OBP-300010: Branch not found. Please specify a valid value for BRANCH_ID."
  val ProductNotFoundByProductCode = "OBP-30011: Product not found. Please specify a valid value for PRODUCT_CODE."
  val CounterpartyNotFoundByIban = "OBP-30012: Counterparty not found. Please specify a valid value for IBAN."
  val CounterpartyBeneficiaryPermit = "OBP-30013: The account can not send money to the Counterparty. Please set the Counterparty 'isBeneficiary' true first"
  val CounterpartyAlreadyExists = "OBP-30014: Counterparty already exists. Please specify a different value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME."
  val CreateBranchError = "OBP-30015: Could not insert the Branch"
  val UpdateBranchError = "OBP-30016: Could not update the Branch"
  val CounterpartyNotFoundByCounterpartyId = "OBP-30017: Counterparty not found. Please specify a valid value for COUNTERPARTY_ID."
  val BankAccountNotFound = "OBP-30018: Bank Account not found. Please specify valid values for BANK_ID and ACCOUNT_ID. "
  val ConsumerNotFoundByConsumerId = "OBP-30019: Consumer not found. Please specify a valid value for CONSUMER_ID."

  val CreateBankError = "OBP-30020: Could not create the Bank"
  val UpdateBankError = "OBP-30021: Could not update the Bank"
  val NoViewPermission = "OBP-30022: The current view does not have the permission: "
  val UpdateConsumerError = "OBP-30023: Cannot update Consumer "
  val CreateConsumerError = "OBP-30024: Could not create Consumer "
  val CreateUserCustomerLinksError = "OBP-30025: Could not create user_customer_links "
  val ConsumerKeyAlreadyExists = "OBP-30026: Consumer Key already exists. Please specify a different value."
  val NoExistingAccountHolders = "OBP-30027: Account Holders not found. The BANK_ID / ACCOUNT_ID specified for account holder does not exist on this server"


  val CreateAtmError = "OBP-30028: Could not insert the ATM"
  val UpdateAtmError = "OBP-30029: Could not update the ATM"

  val CreateProductError = "OBP-30030: Could not insert the Product"
  val UpdateProductError = "OBP-30031: Could not update the Product"

  val CreateCardError = "OBP-30032: Could not insert the Card"
  val UpdateCardError = "OBP-30033: Could not update the Card"

  val ViewIdNotSupported = "OBP-30034: This ViewId is do not supported. Only support four now: Owner, Public, Accountant, Auditor."

  val UserCustomerLinkNotFound = "OBP-30035: User Customer Link not found"

  val CreateOrUpdateCounterpartyMetadataError = "OBP-30036: Could not create or update CounterpartyMetadata"
  val CounterpartyMetadataNotFound = "OBP-30037: CounterpartyMetadata not found. Please specify valid values for BANK_ID, ACCOUNT_ID and COUNTERPARTY_ID. "

  val CreateFxRateError = "OBP-30038: Could not insert the Fx Rate"
  val UpdateFxRateError = "OBP-30039: Could not update the Fx Rate"
  val UnknownFxRateError = "OBP-30040: Unknown Fx Rate error"
  
  val CheckbookOrderNotFound = "OBP-30041: CheckbookOrder not found for Account. "
  val GetTopApisError = "OBP-30042: Could not get the top apis from database.  "
  val GetMetricsTopConsumersError = "OBP-30045: Could not get the top consumers from database.  "
  val GetAggregateMetricsError = "OBP-30043: Could not get the aggregate metrics from database.  "

  val DefaultBankIdNotSet = "OBP-30044: Default BankId is not set on this instance. Please set defaultBank.bank_id in props files. "

  val CreateWebhookError = "OBP-30047: Cannot create Webhook"
  val GetWebhooksError = "OBP-30048: Cannot get Webhooks"
  val UpdateWebhookError = "OBP-30049: Cannot create Webhook"
  val WebhookNotFound = "OBP-30050: Webhook not found. Please specify a valid value for account_webhook_id."
  val CreateCustomerError = "OBP-30051: Cannot create Customer"
  val CheckCustomerError = "OBP-30052: Cannot check Customer"
  
  val CreateUserAuthContextError = "OBP-30053: Could not insert the UserAuthContext"
  val UpdateUserAuthContextError = "OBP-30054: Could not update the UserAuthContext"
  val UpdateUserAuthContextNotFound = "OBP-30055: UserAuthContext not found. Please specify a valid value for USER_ID."
  val DeleteUserAuthContextNotFound = "OBP-30056: UserAuthContext not found by USER_AUTH_CONTEXT_ID."

  // Meetings
  val MeetingsNotSupported = "OBP-30101: Meetings are not supported on this server."
  val MeetingApiKeyNotConfigured = "OBP-30102: Meeting provider API Key is not configured."
  val MeetingApiSecretNotConfigured = "OBP-30103: Meeting provider Secret is not configured."
  val MeetingNotFound = "OBP-30104: Meeting not found."


  val InvalidAccountBalanceCurrency = "OBP-30105: Invalid Balance Currency."
  val InvalidAccountBalanceAmount = "OBP-30106: Invalid Balance Amount."

  val InvalidUserId = "OBP-30107: Invalid User Id."
  val InvalidAccountType = "OBP-30108: Invalid Account Type."
  val InitialBalanceMustBeZero = "OBP-30109: Initial Balance of Account must be Zero (0)."
  val InvalidAccountIdFormat = "OBP-30110: Invalid Account Id. The ACCOUNT_ID should only contain 0-9/a-z/A-Z/'-'/'.'/'_', the length should be smaller than 255."
  val InvalidBankIdFormat = "OBP-30111: Invalid Bank Id. The BANK_ID should only contain 0-9/a-z/A-Z/'-'/'.'/'_', the length should be smaller than 255."
  val InvalidAccountInitialBalance = "OBP-30112: Invalid Number. Initial balance must be a number, e.g 1000.00"


  val EntitlementIsBankRole = "OBP-30205: This entitlement is a Bank Role. Please set bank_id to a valid bank id."
  val EntitlementIsSystemRole = "OBP-30206: This entitlement is a System Role. Please set bank_id to empty string."


  val InvalidStrongPasswordFormat = "OBP-30207: Invalid Password Format. Your password should EITHER be at least 10 characters long and contain mixed numbers and both upper and lower case letters and at least one special character, OR be longer than 16 characters."

  val AccountIdAlreadyExsits = "OBP-30208: Account_ID already exists at the Bank."


  val InsufficientAuthorisationToCreateBranch  = "OBP-30209: Insufficient authorisation to Create Branch. You do not have the role CanCreateBranch." // was OBP-20019
  val InsufficientAuthorisationToCreateBank  = "OBP-30210: Insufficient authorisation to Create Bank. You do not have the role CanCreateBank." // was OBP-20020

  val InvalidConnector = "OBP-30211: Invalid Connector Version. Please specify a valid value for CONNECTOR."

  val EntitlementNotFound = "OBP-30212: EntitlementId not found"
  val UserDoesNotHaveEntitlement = "OBP-30213: USER_ID does not have the ENTITLEMENT_ID."
  val EntitlementRequestAlreadyExists = "OBP-30214: Entitlement Request already exists for the user."
  val EntitlementRequestCannotBeAdded = "OBP-30217: Entitlement Request cannot be added."
  val EntitlementRequestNotFound = "OBP-30215: EntitlementRequestId not found"
  val EntitlementAlreadyExists = "OBP-30216: Entitlement already exists for the user."

  val TaxResidenceNotFound = "OBP-30300: Tax Residence not found by TAX_RESIDENCE_ID. "
  val CustomerAddressNotFound = "OBP-30310: Customer's Address not found by CUSTOMER_ADDRESS_ID. "

  // Branch related messages
  val branchesNotFoundLicense = "OBP-32001: No branches available. License may not be set."
  val BranchesNotFound = "OBP-32002: No branches available."

  // ATM related messages
  val atmsNotFoundLicense = "OBP-33001: No ATMs available. License may not be set."
  val atmsNotFound = "OBP-33002: No ATMs available."

  // General Resource related messages above here


  // Transaction Request related messages (OBP-40XXX)
  val InvalidTransactionRequestType = "OBP-40001: Invalid value for TRANSACTION_REQUEST_TYPE"
  val InsufficientAuthorisationToCreateTransactionRequest  = "OBP-40002: Insufficient authorisation to create TransactionRequest. The Transaction Request could not be created because you don't have access to the owner view of the from account or you don't have access to canCreateAnyTransactionRequest."
  val InvalidTransactionRequestCurrency = "OBP-40003: Transaction Request Currency must be the same as From Account Currency."
  val InvalidTransactionRequestId = "OBP-40004: Transaction Request Id not found."
  val InsufficientAuthorisationToCreateTransactionType  = "OBP-40005: Insufficient authorisation to Create Transaction Type offered by the bank. The Request could not be created because you don't have access to CanCreateTransactionType."
  val CreateTransactionTypeInsertError  = "OBP-40006: Could not insert Transaction Type: Non unique BANK_ID / SHORT_CODE"
  val CreateTransactionTypeUpdateError  = "OBP-40007: Could not update Transaction Type: Non unique BANK_ID / SHORT_CODE"
  val NotPositiveAmount = "OBP-40008: Can't send a payment with a value of 0 or less."
  val TransactionRequestTypeHasChanged = "OBP-40009: The TRANSACTION_REQUEST_TYPE has changed."
  val InvalidTransactionRequesChallengeId = "OBP-40010: Invalid Challenge Id. Please specify a valid value for CHALLENGE_ID."
  val TransactionRequestStatusNotInitiated = "OBP-40011: Transaction Request Status is not INITIATED."
  val CounterpartyNotFoundOtherAccountProvider = "OBP-40012: Please set up the otherAccountRoutingScheme and otherBankRoutingScheme fields of the Counterparty to 'OBP'"
  val InvalidChargePolicy = "OBP-40013: Invalid Charge Policy. Please specify a valid value for Charge_Policy: SHARED, SENDER or RECEIVER. "
  val AllowedAttemptsUsedUp = "OBP-40014: Sorry, you've used up your allowed attempts. "
  val InvalidChallengeType = "OBP-40015: Invalid Challenge Type. Please specify a valid value for CHALLENGE_TYPE, when you create the transaction request."
  val InvalidChallengeAnswer = "OBP-40016: Invalid Challenge Answer. Please specify a valid value for answer in Json body. If it is sandbox mode, the answer must be `123`. If it kafka mode, the answer can be got by phone message or other security ways."
  val InvalidPhoneNumber = "OBP-40017: Invalid Phone Number. Please specify a valid value for PHONE_NUMBER. Eg:+9722398746 "
  val TransactionRequestsNotEnabled = "OBP-40018: Sorry, Transaction Requests are not enabled in this API instance."



  // Exceptions (OBP-50XXX)
  val UnknownError = "OBP-50000: Unknown Error."
  val FutureTimeoutException = "OBP-50001: Future Timeout Exception."
  val KafkaMessageClassCastException = "OBP-50002: Kafka Response Message Class Cast Exception."
  val AdapterOrCoreBankingSystemException = "OBP-50003: Adapter Or Core Banking System Exception. Failed to get a valid response from the south side Adapter or Core Banking System."
  // This error may not be shown to user, just for debugging.
  val CurrentUserNotFoundException = "OBP-50004: Method (AuthUser.getCurrentUser) can not find the current user in the current context!"
  val AnUnspecifiedOrInternalErrorOccurred = "OBP-50005: An unspecified or internal error occurred."
  val KafkaInterruptedException = "OBP-50006: Kafka interrupted exception."
  val KafkaExecutionException = "OBP-50007: Kafka execution exception."
  val KafkaStreamTimeoutException = "OBP-50008: Akka Kafka stream timeout exception."
  val KafkaUnknownError = "OBP-50009: Kafka unknown error."
  val ScalaEmptyBoxToLiftweb = "OBP-50010: Scala return Empty box to Liftweb."
  val NoCallContext = "OBP-50012: Can not get the CallContext object here."
  val UnspecifiedCbsError = "OBP-50013: The Core Banking System returned an unspecified error or response."
  val RefreshUserError = "OBP-50014: Can not refresh User."

  // Connector Data Exceptions (OBP-502XX)
  val ConnectorEmptyResponse = "OBP-50200: Connector cannot return the data we requested." // was OBP-30200
  val InvalidConnectorResponseForGetBankAccounts = "OBP-50201: Connector did not return the set of accounts we requested."  // was OBP-30201
  val InvalidConnectorResponseForGetBankAccount = "OBP-50202: Connector did not return the account we requested."  // was OBP-30202
  val InvalidConnectorResponseForGetTransaction = "OBP-50203: Connector did not return the transaction we requested."  // was OBP-30203
  val InvalidConnectorResponseForGetTransactions = "OBP-50204: Connector did not return the set of transactions we requested."  // was OBP-30204
  val InvalidConnectorResponseForGetTransactionRequests210 = "OBP-50205: Connector did not return the set of transaction requests we requested."
  val InvalidConnectorResponseForGetChallengeThreshold = "OBP-50206: Connector did not return the set of challenge threshold we requested."
  val InvalidConnectorResponseForGetChargeLevel = "OBP-50207: Connector did not return the set of challenge level we requested."
  val InvalidConnectorResponseForCreateTransactionRequestImpl210 = "OBP-50208: Connector did not return the set of transactions requests we requested."
  val InvalidConnectorResponseForMakePayment = "OBP-50209: Connector did not return the set of transactions we requested."
  val InvalidConnectorResponseForMakePaymentv200 = "OBP-50210: Connector did not return the set of transaction id we requested."
  val InvalidConnectorResponseForGetCheckbookOrdersFuture = "OBP-50211: Connector did not return the set of check book."
  val InvalidConnectorResponseForGetStatusOfCreditCardOrderFuture = "OBP-50212: Connector did not return the set of status of credit card."
  val InvalidConnectorResponseForCreateTransactionAfterChallengev300 = "OBP-50213: The Connector did not return a valid response for payments."


  // Adapter Exceptions (OBP-6XXXX)
  // Reserved for adapter (south of Kafka) messages
  // Also used for connector == mapped, and show it as the Internal errors.
  val GetStatusException = "OBP-60001: Save Transaction Exception. "
  val GetChargeValueException = "OBP-60002: Get ChargeValue Exception. "
  val CreateTransactionsException = "OBP-60003: Create transaction Exception. "
  val UpdateBankAccountException = "OBP-60004: Update bank account Exception. "
  val SaveTransactionRequestTransactionException = "OBP-60005: Save Transaction Request Transaction Exception. "
  val SaveTransactionRequestChallengeException = "OBP-60006: Save Transaction Request Challenge Exception. "
  val SaveTransactionRequestStatusException = "OBP-60007: Save Transaction Request Status Exception. "
  val TransactionRequestDetailsExtractException = "OBP-60008: Transaction detail body extract exception. "
  val GetTransactionsException = "OBP-60009: Get Transaction Exception. "
  val GetTransactionRequestsException = "OBP-60010: Get Transaction Requests Exception. "


  ///////////



  //For Swagger, used reflect to  list all the varible names and values.
  // eg : val InvalidUserId = "OBP-30107: Invalid User Id."
  //   -->(InvalidUserId, "OBP-30107: Invalid User Id.")
  val allFields =
  for (
    v <- this.getClass.getDeclaredFields
    //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
    if (APIUtil.notExstingBaseClass(v.getName()))
  ) yield {
    v.setAccessible(true)
    v.getName() -> v.get(this)
  }

  //For Swagger, get varible name by value:
  // eg: val InvalidUserId = "OBP-30107: Invalid User Id."
  //  getFildNameByValue("OBP-30107: Invalid User Id.") return InvalidUserId
  def getFildNameByValue(value: String) = {
    val strings = for (e <- allFields if (e._2 == (value))) yield e._1
    strings.head
  }


  def getDuplicatedMessageNumbers = {
    import scala.meta._
    val source: Source = new java.io.File("src/main/scala/code/api/util/ErrorMessages.scala").parse[Source].get

    val listOfMessaegeNumbers = source.collect {
      case obj: Defn.Object if obj.name.value == "ErrorMessages" =>
        obj.collect {
          case v: Defn.Val if v.rhs.syntax.startsWith(""""OBP-""") =>
            val messageNumber = v.rhs.syntax.split(":")
            messageNumber(0)
        }
    }
    val list = listOfMessaegeNumbers.flatten
    val duplicatedMessageNumbers = list
      .groupBy(x => x).mapValues(x => x.length) // Compute the number of occurrences of each message number
      .toList.filter(_._2 > 1) // Make a list with numbers which have more than 1 occurrences
    duplicatedMessageNumbers
  }

  def main (args: Array[String]): Unit = {
    val duplicatedMessageNumbers: List[(String, Int)] = getDuplicatedMessageNumbers
    duplicatedMessageNumbers.size match {
      case number if number > 0 =>
        val msg=
          """

                ____              ___            __           __                                                                      __
               / __ \__  ______  / (_)________ _/ /____  ____/ /  ____ ___  ___  ______________ _____ ____     ____  __  ______ ___  / /_  ___  __________
              / / / / / / / __ \/ / / ___/ __ `/ __/ _ \/ __  /  / __ `__ \/ _ \/ ___/ ___/ __ `/ __ `/ _ \   / __ \/ / / / __ `__ \/ __ \/ _ \/ ___/ ___/
             / /_/ / /_/ / /_/ / / / /__/ /_/ / /_/  __/ /_/ /  / / / / / /  __(__  |__  ) /_/ / /_/ /  __/  / / / / /_/ / / / / / / /_/ /  __/ /  (__  )
            /_____/\__,_/ .___/_/_/\___/\__,_/\__/\___/\__,_/  /_/ /_/ /_/\___/____/____/\__,_/\__, /\___/  /_/ /_/\__,_/_/ /_/ /_/_.___/\___/_/  /____/
                       /_/                                                                    /____/

            """
        println(msg)
        println(duplicatedMessageNumbers)
      case _ =>
    }
  }

}

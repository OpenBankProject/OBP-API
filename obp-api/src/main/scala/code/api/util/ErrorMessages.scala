package code.api.util

import java.util.Objects
import java.util.regex.Pattern
import com.openbankproject.commons.model.enums.TransactionRequestStatus._
import code.api.Constant._

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

  val AccountFirehoseNotAllowedOnThisInstance = "OBP-00009: Account firehose is not allowed on this instance. Please set allow_account_firehose = true in props files. "
  val MissingPropsValueAtThisInstance = "OBP-00010: Missing props value at this API instance - "
  val NoValidElasticsearchIndicesConfigured = "OBP-00011: No elasticsearch indices are allowed on this instance. Please set es.warehouse.allowed.indices = index1,index2 (or = ALL for all). "
  val CustomerFirehoseNotAllowedOnThisInstance = "OBP-00012: Customer firehose is not allowed on this instance. Please set allow_customer_firehose = true in props files. "

  // WebUiProps Exceptions (OBP-08XXX)
  val InvalidWebUiProps = "OBP-08001: Incorrect format of name."
  val WebUiPropsNotFound = "OBP-08002: WebUi props not found. Please specify a valid value for WEB_UI_PROPS_ID."

  // DynamicEntity Exceptions (OBP-09XXX)
  val DynamicEntityNotFoundByDynamicEntityId = "OBP-09001: DynamicEntity not found. Please specify a valid value for DYNAMIC_ENTITY_ID."
  val DynamicEntityNameAlreadyExists = "OBP-09002: DynamicEntity's entityName already exists. Please specify a different value for entityName."
  val DynamicEntityNotExists = "OBP-09003: DynamicEntity not exists. Please check entityName." 
  val DynamicEntityMissArgument = "OBP-09004: DynamicEntity process related argument is missing."
  val EntityNotFoundByEntityId = "OBP-09005: Entity not found. Please specify a valid value for entityId."
  val DynamicEntityOperationNotAllowed = "OBP-09006: Operation is not allowed, because Current DynamicEntity have upload data, must to delete all the data before this operation."
  val DynamicEntityInstanceValidateFail = "OBP-09007: DynamicEntity data validation failure."

  val DynamicEndpointExists = "OBP-09008: DynamicEndpoint already exists."
  val DynamicEndpointNotFoundByDynamicEndpointId = "OBP-09009: DynamicEndpoint not found. Please specify a valid value for DYNAMIC_ENDPOINT_ID."
  val InvalidMyDynamicEntityUser = "OBP-09010: DynamicEntity can only be updated/deleted by the user who created it. Please try `Update/DELETE Dynamic Entity` endpoint"
  val InvalidMyDynamicEndpointUser = "OBP-09011: DynamicEndpoint can only be updated/deleted by the user who created it. Please try `Update/DELETE Dynamic Endpoint` endpoint"
  
  val InvalidRequestPayload = "OBP-09013: Incorrect request body Format, it should be a valid json that matches Validation rule."


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
  val InvalidJsonContent = "OBP-10020: Incorrect json."
  val InvalidConnectorName = "OBP-10021: Incorrect Connector name."
  val InvalidConnectorMethodName = "OBP-10022: Incorrect Connector method name."
  val InvalidOutBoundMapping = "OBP-10031: Incorrect outBoundMapping Format, it should be a json structure."
  val InvalidInBoundMapping = "OBP-10032: Incorrect inBoundMapping Format, it should be a json structure."
  val invalidIban = "OBP-10033: Invalid IBAN."
  val InvalidUri = "OBP-10404: Request Not Found. The server has not found anything matching the Request-URI.Check your URL and the headers. " +
    "NOTE: when it is POST or PUT api, the Content-Type must be `application/json`. OBP only support the json format body."
  val ResourceDoesNotExist = "OBP-10405: Resource does not exist."

  // General Sort and Paging
  val FilterSortDirectionError = "OBP-10023: obp_sort_direction parameter can only take two values: DESC or ASC!" // was OBP-20023
  val FilterOffersetError = "OBP-10024: wrong value for obp_offset parameter. Please send a positive integer (=>0)!" // was OBP-20024
  val FilterLimitError = "OBP-10025: wrong value for obp_limit parameter. Please send a positive integer (=>1)!" // was OBP-20025
  val FilterDateFormatError = s"OBP-10026: Failed to parse date string. Please use this format ${DateWithMsFormat.toPattern}!" // OBP-20026
  val FilterAnonFormatError = s"OBP-10028: anon parameter can only take two values: TRUE or FALSE!"
  val FilterDurationFormatError = s"OBP-10029: wrong value for `duration` parameter. Please send a positive integer (=>0)!"

  val InvalidApiVersionString = "OBP-00027: Invalid API Version string. We could not find the version specified."
  val IncorrectTriggerName = "OBP-10028: Incorrect Trigger name: "

  val ScaMethodNotDefined = "OBP-10030: Strong customer authentication method is not defined at this instance."



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
  val InvalidCustomViewFormat = s"OBP-20020: View name must start with `_`. eg: _work, _life. Reserved names: $CUSTOM_OWNER_VIEW_ID "
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

  val UserMissOwnerViewOrNotAccountHolder = "OBP-20047: User must have access to the owner view or must be an account holder."

  val UserNotSuperAdmin = "OBP-20050: Current User is not a Super Admin!"

  val ElasticSearchIndexNotFound = "OBP-20051: Elasticsearch index or indices not found."
  val NotEnoughtSearchStatisticsResults = "OBP-20052: Result set too small. Will not be displayed for reasons of privacy."
  val ElasticSearchEmptyQueryBody = "OBP-20053: The Elasticsearch query body cannot be empty"
  val InvalidAmount = "OBP-20054: Invalid amount. Please specify a valid value for amount."
  val MissingQueryParams = "OBP-20055: These query parameters are missing: "
  val ElasticSearchDisabled  = "OBP-20056: Elasticsearch is disabled for this API instance."
  val UserNotFoundByUserId = "OBP-20057: User not found by userId."
  val ConsumerIsDisabled = "OBP-20058: Consumer is disabled."
  val CouldNotGetUserLockStatus = "OBP-20059: Could not get the lock status of the user."

  val UserNotSuperAdminOrMissRole = "OBP-20101: Current User is not super admin or is missing entitlements: "

  // OAuth 2
  val ApplicationNotIdentified = "OBP-20200: The application cannot be identified. "
  val Oauth2IsNotAllowed = "OBP-20201: OAuth2 is not allowed at this instance."
  val Oauth2IJwtCannotBeVerified = "OBP-20202: OAuth2's Access Token cannot be verified."
  val Oauth2ThereIsNoUrlOfJwkSet = "OBP-20203: There is no an URL of OAuth 2.0 server's JWK set, published at a well-known URL."
  val Oauth2BadJWTException = "OBP-20204: Bad JWT error. "
  val Oauth2ParseException = "OBP-20205: Parse error. "
  val Oauth2BadJOSEException = "OBP-20206: Bad JSON Object Signing and Encryption (JOSE) exception. The ID token is invalid or expired. "
  val Oauth2JOSEException = "OBP-20207: Bad JSON Object Signing and Encryption (JOSE) exception. An internal JOSE exception was encountered. "
  val Oauth2CannotMatchIssuerAndJwksUriException = "OBP-20208: Cannot match the issuer and JWKS URI at this server instance. "
  val Oauth2TokenHaveNoConsumer = "OBP-20209: The token have no linked consumer. "
  val Oauth2TokenMatchCertificateFail = "OBP-20210: The token linked with a different client certificate. "

  // X.509
  val X509GeneralError = "OBP-20300: PEM Encoded Certificate issue."
  val X509ParsingFailed = "OBP-20301: Parsing failed for PEM Encoded Certificate."
  val X509CertificateExpired = "OBP-20302: PEM Encoded Certificate expired."
  val X509CertificateNotYetValid = "OBP-20303: PEM Encoded Certificate not yet valid."
  val X509CannotGetRSAPublicKey = "OBP-20304: RSA public key cannot be found at PEM Encoded Certificate."
  val X509CannotGetECPublicKey = "OBP-20305: EC public key cannot be found at PEM Encoded Certificate."
  val X509CannotGetCertificate = "OBP-20306: PEM Encoded Certificate cannot be found at request header."
  val X509ActionIsNotAllowed = "OBP-20307: PEM Encoded Certificate does not provide the proper role for the action has been taken."
  val X509ThereAreNoPsd2Roles = "OBP-20308: PEM Encoded Certificate does not contain PSD2 roles."
  
  // OpenID Connect
  val CouldNotExchangeAuthorizationCodeForTokens = "OBP-20400: Could not exchange authorization code for tokens."
  val CouldNotSaveOpenIDConnectUser = "OBP-20401: Could not get/save OpenID Connect user."
  val CouldNotSaveOpenIDConnectToken = "OBP-20402: Could not save OpenID Connect token."
  val InvalidOpenIDConnectState = "OBP-20403: Invalid OpenIDConnect state parameter."
  val CouldNotHandleOpenIDConnectData = "OBP-20404: Could not handle OpenID Connect data."
  val CouldNotValidateIDToken = "OBP-20405: ID token could note be validated."

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
  val BranchNotFoundByBranchId = "OBP-300010: Branch not found. Please specify a valid value for BRANCH_ID. Or License may not be set. meta.license.id and meta.license.name can not be empty"
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
  val CreateOAuth2ConsumerError = "OBP-30077: Could not create OAuth2 Consumer."

  val CreateUserCustomerLinksError = "OBP-30025: Could not create user_customer_links "
  val ConsumerKeyAlreadyExists = "OBP-30026: Consumer Key already exists. Please specify a different value."
  val NoExistingAccountHolders = "OBP-30027: Account Holders not found. The BANK_ID / ACCOUNT_ID specified for account holder does not exist on this server"


  val CreateAtmError = "OBP-30028: Could not insert the ATM"
  val UpdateAtmError = "OBP-30029: Could not update the ATM"

  val CreateProductError = "OBP-30030: Could not insert the Product"
  val UpdateProductError = "OBP-30031: Could not update the Product"

  val CreateCardError = "OBP-30032: Could not insert the Card"
  val UpdateCardError = "OBP-30033: Could not update the Card"

  val ViewIdNotSupported = "OBP-30034: This ViewId is do not supported. Only support four now: Owner, Accountant, Auditor, _Public."

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
  val UserAuthContextUpdateNotFound = "OBP-30057: User Auth Context Update not found by AUTH_CONTEXT_UPDATE_ID."
  val UpdateCustomerError = "OBP-30058: Cannot update the Customer"
  
  val CardNotFound = "OBP-30059: This Card can not be found for the user "
  val CardAlreadyExists = "OBP-30060: Card already exists. Please specify different values for bankId, card_number and issueNumber."
  val CardAttributeNotFound = "OBP-30061: Card Attribute not found. Please specify a valid value for CARD_ATTRIBUTE_ID."
  val ParentProductNotFoundByProductCode = "OBP-30062: Parent product not found. Please specify an existing product code for parent_product_code. Leave empty if no parent product exists."
  val CannotGrantAccountAccess = "OBP-30063: Cannot grant account access."
  val CannotRevokeAccountAccess = "OBP-30064: Cannot revoke account access."
  val CannotFindAccountAccess = "OBP-30065: Cannot find account access."
  val CannotGetAccounts = "OBP-30066: Could not get accounts."
  val TransactionNotFound = "OBP-30067: Transaction not found. Please specify a valid value for TRANSACTION_ID."
  val RefundedTransaction = "OBP-30068: Transaction was already refunded . Please specify a valid value for TRANSACTION_ID."
  val CustomerAttributeNotFound = "OBP-30069: Customer Attribute not found. Please specify a valid value for CUSTOMER_ATTRIBUTE_ID."
  val TransactionAttributeNotFound = "OBP-30070: Transaction Attribute not found. Please specify a valid value for TRANSACTION_ATTRIBUTE_ID."
  val AttributeNotFound = "OBP-30071: Attribute Definition not found. Please specify a valid value for ATTRIBUTE_DEFINITION_ID."
  
  val CreateCounterpartyError = "OBP-30072: Could not create the Counterparty."

  val BankAccountNotFoundByAccountRouting = "OBP-30073: Bank Account not found. Please specify valid values for account routing scheme and address."
  val BankAccountNotFoundByIban = "OBP-30074: Bank Account not found. Please specify a valid value for iban."
  val AccountRoutingNotFound = "OBP-30075: Account routing not found, Please specify valid values for account routing scheme and address"
  val BankAccountNotFoundByAccountId = "OBP-30076: Bank Account not found. Please specify a valid value for ACCOUNT_ID."

  val TransactionRequestAttributeNotFound = "OBP-30078: Transaction Request Attribute not found. Please specify a valid value for TRANSACTION_REQUEST_ATTRIBUTE_ID."

  val ApiCollectionNotFound = "OBP-30079: ApiCollection not found."
  val CreateApiCollectionError = "OBP-30080: Could not create ApiCollection."
  val DeleteApiCollectionError = "OBP-30081: Could not delete ApiCollection."

  val ApiCollectionEndpointNotFound = "OBP-30082: ApiCollectionEndpoint not found."
  val CreateApiCollectionEndpointError = "OBP-30083: Could not create ApiCollectionEndpoint."
  val DeleteApiCollectionEndpointError = "OBP-30084: Could not delete ApiCollectionEndpoint."
  val ApiCollectionEndpointAlreadyExisting = "OBP-30085: The ApiCollectionEndpoint is already Existing."
  val ApiCollectionAlreadyExisting = "OBP-30086: The ApiCollection is already Existing."

  val DoubleEntryTransactionNotFound = "OBP-30087: Double Entry Transaction not found."

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
  val InvalidCustomerBankId = "OBP-30113: Invalid Bank Id. The Customer does not belong to this Bank"
  val InvalidAccountRoutings = "OBP-30114: Invalid Account Routings."
  val AccountRoutingAlreadyExist = "OBP-30115: Account Routing already exist."
  val InvalidPaymentSystemName = "OBP-30116: Invalid payment system name. The payment system name should only contain 0-9/a-z/A-Z/'-'/'.'/'_', the length should be smaller than 200."


  val EntitlementIsBankRole = "OBP-30205: This entitlement is a Bank Role. Please set bank_id to a valid bank id."
  val EntitlementIsSystemRole = "OBP-30206: This entitlement is a System Role. Please set bank_id to empty string."


  val InvalidStrongPasswordFormat = "OBP-30207: Invalid Password Format. Your password should EITHER be at least 10 characters long and contain mixed numbers and both upper and lower case letters and at least one special character, OR be longer than 16 characters."

  val AccountIdAlreadyExists = "OBP-30208: Account_ID already exists at the Bank."


  val InsufficientAuthorisationToCreateBranch  = "OBP-30209: Insufficient authorisation to Create Branch. You do not have the role CanCreateBranch." // was OBP-20019
  val InsufficientAuthorisationToDeleteBranch  = "OBP-30218: Insufficient authorisation to Create Branch. You do not have the role CanCreateBranch." // was OBP-20019
  val InsufficientAuthorisationToCreateBank  = "OBP-30210: Insufficient authorisation to Create Bank. You do not have the role CanCreateBank." // was OBP-20020

  val InvalidConnector = "OBP-30211: Invalid Connector Version. Please specify a valid value for CONNECTOR."

  val EntitlementNotFound = "OBP-30212: EntitlementId not found"
  val UserDoesNotHaveEntitlement = "OBP-30213: USER_ID does not have the ENTITLEMENT_ID."
  val EntitlementRequestAlreadyExists = "OBP-30214: Entitlement Request already exists for the user."
  val EntitlementRequestCannotBeAdded = "OBP-30217: Entitlement Request cannot be added."
  val EntitlementRequestNotFound = "OBP-30215: EntitlementRequestId not found"
  val EntitlementAlreadyExists = "OBP-30216: Entitlement already exists for the user."
  val EntitlementCannotBeDeleted = "OBP-30219: EntitlementId cannot be deleted."
  
  val CreateSystemViewError = "OBP-30250: Could not create the system view"
  val DeleteSystemViewError = "OBP-30251: Could not delete the system view"
  val SystemViewNotFound = "OBP-30252: System view not found. Please specify a valid value for VIEW_ID"
  val UpdateSystemViewError = "OBP-30253: Could not update the system view"
  val ExistingSystemViewError = "OBP-30254: There is already a view with permalink"
  val EmptyNameOfSystemViewError = "OBP-30255: You cannot create a View with an empty Name"
  val DeleteCustomViewError = "OBP-30256: Could not delete the custom view"
  val CannotFindCustomViewError = "OBP-30257: Could not find the custom view"
  val SystemViewCannotBePublicError = "OBP-30258: System view cannot be public"

  val TaxResidenceNotFound = "OBP-30300: Tax Residence not found by TAX_RESIDENCE_ID. "
  val CustomerAddressNotFound = "OBP-30310: Customer's Address not found by CUSTOMER_ADDRESS_ID. "
  val AccountApplicationNotFound = "OBP-30311: AccountApplication not found by ACCOUNT_APPLICATION_ID. "
  val ResourceUserNotFound = "OBP-30312: ResourceUser not found by USER_ID. "
  val UserIdAndCustomerIdNotPresent = "OBP-30313: userId and customerId not present. "
  val AccountApplicationAlreadyAccepted = "OBP-30314: AccountApplication has already been accepted. "
  val UpdateAccountApplicationStatusError = "OBP-30315: AccountApplication Status can not be updated. "
  val CreateAccountApplicationError = "OBP-30316: AccountApplication Status can not be created. "

  // Branch related messages
  val BranchesNotFoundLicense = "OBP-32001: No branches available. License may not be set."
  val BranchesNotFound = "OBP-32002: No branches available."

  // ATM related messages
  val atmsNotFoundLicense = "OBP-33001: No ATMs available. License may not be set."
  val atmsNotFound = "OBP-33002: No ATMs available."
  
  // Consents
  val ConsentNotFound = "OBP-35001: Consent not found by CONSENT_ID. "
  val ConsentNotBeforeIssue = "OBP-35002: The time Consent-ID token was issued is set in the future. "
  val ConsentExpiredIssue = "OBP-35003: Consent-Id is expired. "
  val ConsentVerificationIssue = "OBP-35004: Consent-Id JWT value couldn't be verified. "
  val ConsentStatusIssue = "OBP-35005: Consent-Id is not in status "
  val ConsentCheckExpiredIssue = "OBP-35006: Cannot check is Consent-Id expired. "
  val ConsentDisabled = "OBP-35007: Consents are not allowed at this instance. "
  val ConsentHeaderNotFound = "OBP-35008: Cannot get Consent-Id. "
  val ConsentAllowedScaMethods = "OBP-35009: Only SMS and EMAIL are supported as SCA methods. "
  val SmsServerNotResponding = "OBP-35010: SMS server is not working or SMS server can not send the message to the phone number: "
  val AuthorizationNotFound = "OBP-35011: Resource identification of the related Consent authorisation sub-resource not found by AUTHORIZATION_ID. "
  val ConsentAlreadyRevoked = "OBP-35012: Consent is already revoked. "
  val RolesAllowedInConsent = "OBP-35013: Consents can only contain Roles that you already have access to."
  val ViewsAllowedInConsent = "OBP-35014: Consents can only contain Views that you already have access to."
  val ConsentDoesNotMatchConsumer = "OBP-35015: The Consent does not match a valid Consumer."
  val ConsumerKeyHeaderMissing = "OBP-35016: The Consumer-Key request header is missing. The request header must contain the Consumer-Key of the Consumer that was used to create the Consent."
  val ConsumerAtConsentDisabled = "OBP-35017: The Consumer specified in this consent is disabled."
  val ConsumerAtConsentCannotBeFound = "OBP-35018: The Consumer specified in this consent cannot be found."
  val ConsumerValidationMethodForConsentNotDefined = "OBP-35019: Consumer validation method for consent is not defined at this instance."
  val ConsentMaxTTL = "OBP-35020: You exceeded max value of time to live of consents."
  val ConsentViewNotFund = "OBP-35021: Consent Views not found by CONSENT_ID."
  val ConsumerNotFound = "OBP-35022: The Consumer cannot be found by logged in user."
  val ConsentDoesNotMatchUser = "OBP-35023: The Consent does not match a valid User."
  val ConsentUserAlreadyAdded = "OBP-35024: The Consent's User is already added."
  val ConsentUpdateStatusError = "OBP-35025: The Consent's status cannot be updated."

  //Authorisations
  val AuthorisationNotFound = "OBP-36001: Authorisation not found. Please specify valid values for PAYMENT_ID and AUTHORISATION_ID. "
  val InvalidAuthorisationStatus = "OBP-36002: Authorisation Status is Invalid"
  val AuthorisationNotFoundByPaymentId = "OBP-36003: Authorisation not found. Please specify valid values for PAYMENT_ID. "
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
  val InvalidTransactionRequestChallengeId = "OBP-40010: Invalid Challenge Id. Please specify a valid value for CHALLENGE_ID."
  val TransactionRequestStatusNotInitiated = "OBP-40011: Transaction Request Status is not INITIATED."
  val CounterpartyNotFoundOtherAccountProvider = "OBP-40012: Please set up the otherAccountRoutingScheme and otherBankRoutingScheme fields of the Counterparty to 'OBP'"
  val InvalidChargePolicy = "OBP-40013: Invalid Charge Policy. Please specify a valid value for Charge_Policy: SHARED, SENDER or RECEIVER. "
  val AllowedAttemptsUsedUp = "OBP-40014: Sorry, you've used up your allowed attempts. "
  val InvalidChallengeType = "OBP-40015: Invalid Challenge Type. Please specify a valid value for CHALLENGE_TYPE, when you create the transaction request."
  val InvalidChallengeAnswer = "OBP-40016: Invalid Challenge Answer. Please specify a valid value for answer in Json body. If it is sandbox mode, the answer must be `123`. If it kafka mode, the answer can be got by phone message or other security ways."
  val InvalidPhoneNumber = "OBP-40017: Invalid Phone Number. Please specify a valid value for PHONE_NUMBER. Eg:+9722398746 "
  val TransactionRequestsNotEnabled = "OBP-40018: Sorry, Transaction Requests are not enabled in this API instance."
  val NextChallengePending = s"OBP-40019: Cannot create transaction due to transaction request is in status: ${NEXT_CHALLENGE_PENDING}."
  val TransactionRequestStatusNotInitiatedOrPendingOrForwarded = s"OBP-40020: Transaction Request Status is not ${INITIATED} or ${NEXT_CHALLENGE_PENDING} or ${FORWARDED}."
  val InvalidChallengeTransactionRequestId = "OBP-40021: Invalid Challenge PaymentId or TRANSACTION_REQUEST_ID. "
  val InvalidChallengeChallengeId = "OBP-40022: Invalid ChallengeId. "
  val TransactionRequestCannotBeCancelled = "OBP-40023: Transaction Request cannot be cancelled. "
  val CannotUpdatePSUData = s"OBP-40024: Cannot Update PSU Data for payment initiation due to transaction request is not in status: ${INITIATED}."
  val CannotUpdatePSUDataCancellation = s"OBP-40025: Cannot Update PSU Data for payment initiation cancellation due to transaction request is not in status: ${INITIATED}, ${CANCELLATION_PENDING} or ${COMPLETED}."
  val JsonSchemaIllegal = "OBP-40026: Incorrect json-schema Format. "
  val JsonSchemaValidationNotFound = "OBP-40027: JSON Schema Validation not found, please specify valid query parameter. "
  val ValidationDeleteError = "OBP-40028: Could not delete the JSON Schema Validation. "
  val OperationIdExistsError = "OBP-40029: OPERATION_ID already exists. Please specify different values for OPERATION_ID. "

  val CannotStartTheAuthorisationProcessForTheCancellation = s"OBP-40031: Cannot start the authorisation process for the cancellation of the addressed payment due to transaction request is not in status: ${CANCELLATION_PENDING}."

  val AuthenticationTypeNameIllegal= s"OBP-40030: AuthenticationType name not correct. "
  val AuthenticationTypeValidationNotFound = "OBP-40032: AuthenticationTypeValidation not found, please specify valid query parameter. "
  val AuthenticationTypeValidationDeleteError = "OBP-40033: Could not delete the AuthenticationTypeValidation. "
  val AuthenticationTypeIllegal = "OBP-40034: Current request authentication type is illegal. "

  val ForceErrorInvalid = "OBP-40035: Force Error request header is invalid. "

  val ConnectorMethodNotFound = "OBP-40036: ConnectorMethod not found, please specify valid CONNECTOR_METHOD_ID. "
  val ConnectorMethodAlreadyExists = "OBP-40037: ConnectorMethod already exists. "
  val ConnectorMethodBodyCompileFail = "OBP-40038: ConnectorMethod methodBody is illegal scala code, compile fail. "


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
  val KafkaUnknownError = "OBP-50009: Kafka Unknown Error."
  val ScalaEmptyBoxToLiftweb = "OBP-50010: Scala return Empty box to Liftweb."
  val NoCallContext = "OBP-50012: Can not get the CallContext object here."
  val UnspecifiedCbsError = "OBP-50013: The Core Banking System returned an unspecified error or response."
  val RefreshUserError = "OBP-50014: Can not refresh User."
  val InternalServerError = "OBP-50015: The server encountered an unexpected condition which prevented it from fulfilling the request."
  val KafkaServerUnavailable = "OBP-50016: The kafka server is unavailable."
  val NotAllowedEndpoint = "OBP-50017: The endpoint is forbidden at this API instance."


  // Connector Data Exceptions (OBP-502XX)
  val InvalidConnectorResponse = "OBP-50200: Connector cannot return the data we requested." // was OBP-30200
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
  val InvalidConnectorResponseForMissingRequiredValues = "OBP-50214: Connector return the data, but the data has missing required values."
  val InvalidConnectorResponseForCreateChallenge = "OBP-50215: Connector did not return the set of challenge we requested."
  val InvalidConnectorResponseForSaveDoubleEntryBookTransaction = "OBP-50216: The Connector did not return a valid response for saving double-entry transaction."
  val InvalidConnectorResponseForCancelPayment = "OBP-50217: Connector did not return the transaction we requested."


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
  val CreateMeetingException = "OBP-60011: Create Meeting Exception. "
  val CreateMeetingInviteeException = "OBP-60012: Create Meeting Invitee Exception. "
  val AdapterUnknownError = "OBP-60013: Adapter Unknown Error. "
  val AdapterTimeOurError = "OBP-60014: Adapter Timeout Error. "
  val AdapterFunctionNotImplemented = "OBP-60015: Adapter Function Not Implemented."
  val SaveTransactionRequestDescriptionException = "OBP-60016: Save Transaction Request Description Exception. "

  // MethodRouting Exceptions (OBP-7XXXX)
  val InvalidBankIdRegex = "OBP-70001: Incorrect regex for bankIdPattern."
  val MethodRoutingNotFoundByMethodRoutingId = "OBP-70002: MethodRouting not found. Please specify a valid value for method_routing_id."
  val ExistingMethodRoutingError = "OBP-70003: Method Routing is already existing."

  // Cascade Deletion Exceptions (OBP-8XXXX)
  val CouldNotDeleteCascade = "OBP-80001: Could not delete cascade."
  
  ///////////

  private val ObpErrorMsgPattern = Pattern.compile("OBP-\\d+:.+")

  def isObpErrorMsg(str: String) = Objects.nonNull(str) && ObpErrorMsgPattern.matcher(str).matches()

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

  private lazy val fieldValueToName = allFields.map(it => (it._2, it._1)).toMap
  //For Swagger, get varible name by value:
  // eg: val InvalidUserId = "OBP-30107: Invalid User Id."
  //  getFildNameByValue("OBP-30107: Invalid User Id.") return InvalidUserId
  def getFieldNameByValue(value: String): String =
    fieldValueToName.getOrElse(value, throw new IllegalArgumentException(s"ErrorMessages not exists field value is: $value"))

  def getValueMatches(predicate: String => Boolean): Option[String] = fieldValueToName.collectFirst {
    case (key: String, _) if predicate(key) => key
  }

  // check whether given name is valid errorMessage name
  val isValidName: String => Boolean = {
    val pattern = Pattern.compile("""OBP\-\d+:?""")
    pattern.matcher(_:String).matches()
  }

  /**
   * Error message value mapping response statusCode,
   * Those statusCode is not 400 must add at here.
   */
  private val errorToCode: Map[String, Int] = Map(
    DataImportDisabled -> 403,
    DynamicEntityNotFoundByDynamicEntityId -> 404,
    EntityNotFoundByEntityId -> 404,
    DynamicEndpointNotFoundByDynamicEndpointId -> 404,
//    NotImplemented -> 501, // 400 or 501
    TooManyRequests -> 429,
    ResourceDoesNotExist -> 404,
    UserNotLoggedIn -> 401,
    DirectLoginInvalidToken -> 401,
    InvalidLoginCredentials -> 401,
    UserNotFoundById -> 404,
    UserHasMissingRoles -> 403, // or 400
    InvalidConsumerKey -> 401,
//    InvalidConsumerCredentials -> 401, // or 400
    UsernameHasBeenLocked -> 401,
    UserNoPermissionAccessView -> 403,
    UserNotSuperAdminOrMissRole -> 403,
    ConsumerHasMissingRoles -> 403,
    UserNotFoundByUsername -> 404,
    ApplicationNotIdentified -> 401,
    CouldNotExchangeAuthorizationCodeForTokens -> 401,
    CouldNotSaveOpenIDConnectUser -> 401,
    InvalidOpenIDConnectState -> 401,
    CouldNotHandleOpenIDConnectData -> 401,
    CouldNotValidateIDToken -> 401,
    BankNotFound -> 404,
    CustomerNotFound -> 404,
    CustomerNotFoundByCustomerId -> 404,
    AccountNotFound -> 404,
    CounterpartyNotFoundByIban -> 404,
    BankAccountNotFound -> 404,
    ConsumerNotFoundByConsumerId -> 404,
//    TransactionNotFound -> 404, // or 400
    BankAccountNotFoundByAccountRouting -> 404,
    BankAccountNotFoundByIban -> 404,
    AccountRoutingNotFound -> 404,
    BankAccountNotFoundByAccountId -> 404,
    DoubleEntryTransactionNotFound -> 404,
    MeetingApiKeyNotConfigured -> 403,
    MeetingApiSecretNotConfigured -> 403,
    EntitlementNotFound -> 404,
    EntitlementCannotBeDeleted -> 404,
    ConsentStatusIssue -> 401,
    ConsentDisabled -> 401,
    InternalServerError -> 500,
  )

  /**
   * get response statusCode by error message, return 400 if error message not exists or have not mapping statusCode
   * @param errorMsg
   * @return response statusCode, default is 400
   */
  def getCode(errorMsg: String): Int = errorToCode.get(errorMsg).getOrElse(400)

  /****** special error message, start with $, mark as do validation according ResourceDoc errorResponseBodies *****/
  /**
   * validate method: APIUtil.authorizedAccess
   */
  def $UserNotLoggedIn = UserNotLoggedIn

  /**
   * validate method: NewStyle.function.getBank
   */
  def $BankNotFound = BankNotFound

  /**
   * validate method: NewStyle.function.getBankAccount
   */
  def $BankAccountNotFound = BankAccountNotFound

  /**
   *  validate method: NewStyle.function.checkViewAccessAndReturnView
   */
  def $UserNoPermissionAccessView = UserNoPermissionAccessView


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

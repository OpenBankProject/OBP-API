package code.api.util

import org.json4s._
import code.api.APIFailureNewStyle
import code.api.util.ApiRole.{CanCreateAnyTransactionRequest, canCreateEntitlementAtAnyBank, canCreateEntitlementAtOneBank}
import com.openbankproject.commons.model.enums.TransactionRequestStatus._
import org.json4s.{Extraction, JsonAST}

import java.util.Objects
import java.util.regex.Pattern

object ErrorMessages {
  import code.api.util.APIUtil._
  // Notes to developers. Please:
  // 1) Follow (the existing) grouping of messages
  // 2) Stick to existing terminology e.g. use "invalid" or "incorrect" rather than "wrong"
  // 3) Before adding a new message, check that you can't use one that already exists.
  // 4) Use Proper Names for OBP Resources.
  // 5) Don't use abbreviations.
  // 6) Any message defined here should be considered "fair game" to return over the API. Thus:
  // 7) Since the existence of "OBP-..." in a message is used to determine if we should display to a user if display_internal_errors=false, do *not* concatenate internal or core banking system error messages to these strings.


  def apiFailureToString(code: Int, message: String, context: Option[CallContext]): String = com.openbankproject.commons.util.JsonAliases.compactRender(
    Extraction.decompose(
      APIFailureNewStyle(failMsg = message, failCode = code, context.map(_.toLight))
    )
  )
  def apiFailureToString(code: Int, message: String, context: CallContext): String =
    apiFailureToString(code, message, Some(context))

  // Infrastructure / config level messages (OBP-00XXX)
  val HostnameNotSpecified = "OBP-00001: Hostname not specified. Could not get hostname from Props. Please edit your props file. Here are some example settings: hostname=http://127.0.0.1:8080 or hostname=https://www.example.com"
  val DataImportDisabled  = "OBP-00002: Data import is disabled for this API instance."
  val TransactionDisabled = "OBP-00003: Transaction Requests is disabled in this API instance."

  @deprecated("This is too generic","25-06-2017")
  val ServerAddDataError = "OBP-00004: Server error: could not add message" // Do not use this

  val PublicViewsNotAllowedOnThisInstance = "OBP-00005: Public views not allowed on this instance. Please set allow_public_views = true in props files. "

  val ApiVersionNotSupported = "OBP-00008: The API version you called is not enabled on this server. Please contact your API administrator or use another version."

  val AccountFirehoseNotAllowedOnThisInstance = "OBP-00009: Account firehose is not allowed on this instance. Please set allow_account_firehose = true in props files. "
  val MissingPropsValueAtThisInstance = "OBP-00010: Missing props value at this API instance - "
  val NoValidElasticsearchIndicesConfigured = "OBP-00011: No elasticsearch indices are allowed on this instance. Please set es.warehouse.allowed.indices = index1,index2 (or = ALL for all). "
  val CustomerFirehoseNotAllowedOnThisInstance = "OBP-00012: Customer firehose is not allowed on this instance. Please set allow_customer_firehose = true in props files. "
  val ApiInstanceIdNotSpecified = "OBP-00013: 'api_instance_id' not specified. Please edit your props file."
  val MandatoryPropertyIsNotSet = "OBP-00014: Mandatory properties must be set."

  // Exceptions (OBP-01XXX) ------------------------------------------------>
  val requestTimeout = "OBP-01000: Request Timeout. The OBP API decided to return a timeout. This is probably because a backend service did not respond in time. "
  // <------------------------------------------------ Exceptions (OBP-01XXX)

  // WebUiProps Exceptions (OBP-08XXX)
  val InvalidWebUiProps = "OBP-08001: Incorrect format of name."
  val WebUiPropsNotFound = "OBP-08002: WebUi props not found. Please specify a valid value for WEB_UI_PROPS_ID."
  val WebUiPropsNotFoundByName = "OBP-08003: WebUi prop not found. Please specify a valid value for WEBUI_PROP_NAME."

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
  val InvalidDynamicEndpointSwagger = "OBP-09013: Invalid DynamicEndpoint Swagger Json. "

  val InvalidRequestPayload = "OBP-09014: Incorrect request body Format, it should be a valid json that matches Validation rule."
  val DynamicDataNotFound = "OBP-09015: Dynamic Data not found. Please specify a valid value."
  val DuplicateQueryParameters = "OBP-09016: Duplicate Query Parameters are not allowed."
  val DuplicateHeaderKeys = "OBP-09017: Duplicate Header Keys are not allowed."
  val InvalidDynamicEntityName = "OBP-09018: Invalid entity_name format. Entity names must be lowercase with underscores (snake_case), e.g. 'customer_preferences'. No uppercase letters or spaces allowed."
  val DynamicEntityFieldNotYetQueryable = "OBP-09019: Requested field(s) are not yet queryable - the index is still being built. Please retry shortly."
  val RowLevelAccessRequiresLocalBacking = "OBP-09020: use_row_level_access is only supported for locally-backed dynamic entities. This entity is routed to an external connector (a method routing for dynamicEntityProcess exists for it), where the row-level ACL cannot be enforced. Remove the method routing or disable use_row_level_access."
  val RowLevelAccessNotEnabled = "OBP-09021: The row-access endpoints are only available for dynamic entities created with use_row_level_access = true."
  val DynamicEntityJoinRequiresProjection = "OBP-09022: obp_exists / obp_not_exists join queries require the SQL projection backend (dynamic_entity.indexing.backend=auto on a supported database). This deployment serves Dynamic Entity reads in-memory, where joins are not supported."


  // General messages (OBP-10XXX)
  val InvalidJsonFormat = "OBP-10001: Incorrect json format."
  val InvalidNumber = "OBP-10002: Invalid Number. Could not convert value to a number."
  val InvalidISOCurrencyCode = """OBP-10003: Invalid Currency Value. Expected a 3-letter ISO Currency Code (e.g., 'USD', 'EUR'), 'lovelace' (Cardano), or 'ETH' (Ethereum). Refer to ISO 4217 currency codes: https://www.iso.org/iso-4217-currency-codes.html""".stripMargin
  val FXCurrencyCodeCombinationsNotSupported = "OBP-10004: ISO Currency code combination not supported for FX. Please modify the FROM_CURRENCY_CODE or TO_CURRENCY_CODE. "
  val InvalidDateFormat = "OBP-10005: Invalid Date Format. Could not convert value to a Date."
  val InvalidCurrency = "OBP-10006: Invalid Currency Value."
  val InvalidCacheNamespaceId = "OBP-10123: Invalid namespace_id."
  val IncorrectRoleName = "OBP-10007: Incorrect Role name:"
  val CouldNotTransformJsonToInternalModel = "OBP-10008: Could not transform Json to internal model."
  val CountNotSaveOrUpdateResource = "OBP-10009: Could not save or update resource."
  val NotImplemented = "OBP-10010: Not Implemented "
  val InvalidFutureDateValue = "OBP-10011: future_date has to be in future."
  val maximumLimitExceeded = "OBP-10012: Invalid value. Maximum number is 10000."
  val attemptedToOpenAnEmptyBox = "OBP-10013: Attempted to open an empty Box."
  val cannotDecryptValueOfProperty = "OBP-10014: Could not decrypt value of property "
  val AllowedValuesAre = "OBP-10015: Allowed values are:"
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
  val InvalidUrlParameters = "OBP-10034: Invalid URL parameters."
  val InvalidUri = "OBP-10404: 404 Not Found. The server could not find the requested URI. Please double check your URL, headers and body. " +
    "Note: When you are making a POST or PUT request, the Content-Type header MUST be `application/json`. Note: OBP only supports JSON formatted bodies."
  val ResourceDoesNotExist = "OBP-10405: Resource does not exist."
  val InvalidJsonValue = "OBP-10035: Incorrect json value."
  val InvalidHttpMethod = "OBP-10037: Incorrect http_method."
  val InvalidHttpProtocol = "OBP-10038: Incorrect http_protocol."
  val ServiceIsTooBusy = "OBP-10040: The Service is too busy, please try it later."
  val InvalidLocale = "OBP-10041: This locale is not supported. Only the following can be used: en_GB, es_ES, ro_RO."

  // General Sort and Paging
  val FilterSortDirectionError = "OBP-10023: obp_sort_direction parameter can only take two values: DESC or ASC!" // was OBP-20023
  val FilterSortByError = s"OBP-10042: sort_by parameter value is not in the global whitelist. Allowed values are: ${SortFields.All.toSeq.sorted.mkString(", ")}."
  val FilterSortByNotAllowedForEndpoint = "OBP-10043: sort_by value is not allowed for this endpoint."
  def filterSortByNotAllowedForEndpointDetail(endpoint: String, requested: String, allowed: Iterable[String]): String =
    s"$FilterSortByNotAllowedForEndpoint Endpoint: $endpoint. Value requested: '$requested'. Allowed values: ${allowed.toSeq.sorted.mkString(", ")}."
  val FilterOffersetError = "OBP-10024: wrong value for obp_offset parameter. Please send a positive integer (=>0)!" // was OBP-20024
  val FilterLimitError = "OBP-10025: wrong value for obp_limit parameter. Please send a positive integer (=>1)!" // was OBP-20025
  val FilterDateFormatError = s"OBP-10026: Failed to parse date string. Please use this format ${DateWithMsFormat.toPattern}!" // OBP-20026
  val FilterAnonFormatError = s"OBP-10028: anon parameter can only take two values: TRUE or FALSE!"
  val FilterDurationFormatError = s"OBP-10029: wrong value for `duration` parameter. Please send a positive integer (=>0)!"
  val FilterIsDeletedFormatError = s"OBP-10036: is_deleted parameter can only take two values: TRUE or FALSE!"

  val InvalidApiVersionString = "OBP-00027: Invalid API Version string. We could not find the version specified."
  val IncorrectTriggerName = "OBP-10039: Incorrect Trigger name:"

  val ScaMethodNotDefined = "OBP-10030: Strong customer authentication method is not defined at this instance."

  val createFxCurrencyIssue = "OBP-10050: Cannot create FX currency. "
  val invalidLogLevel = "OBP-10051: Invalid log level. "
  val InvalidContentParameter = "OBP-10052: Invalid content parameter. Valid values are: static, dynamic, all"
  val InvalidTagsParameter = "OBP-10053: Invalid tags parameter. Tags cannot be empty when provided"
  val InvalidFunctionsParameter = "OBP-10054: Invalid functions parameter. Functions cannot be empty when provided"
  val InvalidApiCollectionIdParameter = "OBP-10055: Invalid api-collection-id parameter. API collection ID cannot be empty when provided"
  val IncompleteServerConfiguration = "OBP-10056: A required server configuration property is missing. "
  val InvalidSignalChannelName = "OBP-10057: Invalid Signal Channel name. " +
    "Signal Channel names must use only alphanumeric characters, dots, hyphens, and underscores, " +
    "and be between 1 and 128 characters long."
  val UserFilterParametersNotSupported = "OBP-10058: User identity filter parameters (user_id, username, email, provider_provider_id, anon) " +
    "are not supported on this endpoint. It only ever returns the logged in user's own records. "





  // Authentication / Authorisation / User messages (OBP-20XXX)
  val AuthenticatedUserIsRequired = "OBP-20001: User not logged in. Authentication is required!"
  val DirectLoginMissingParameters = "OBP-20002: These DirectLogin parameters are missing:"
  val DirectLoginInvalidToken = "OBP-20003: This DirectLogin token is invalid or expired:"
  val InvalidLoginCredentials = "OBP-20004: Invalid login credentials. Check username/password."
  val UserNotFoundById = "OBP-20005: User not found. Please specify a valid value for USER_ID."
  val UserHasMissingRoles = "OBP-20006: User is missing one or more roles: "
  val UserNotFoundByEmail = "OBP-20007: User not found by email."

  val InvalidConsumerKey = "OBP-20008: Invalid Consumer Key."
  val InvalidConsumerCredentials = "OBP-20009: Invalid consumer credentials"

  val InvalidValueLength = "OBP-20010: Value too long"
  val InvalidValueCharacters = "OBP-20011: Value contains invalid characters"

  val InvalidDirectLoginParameters = "OBP-20012: Invalid direct login parameters"

  val UsernameHasBeenLocked = "OBP-20013: The account has been locked, please contact an administrator!"
  val UserEmailNotValidated = "OBP-20073: The user email has not been validated. Please validate your email address first."

  val InvalidConsumerId = "OBP-20014: Invalid Consumer ID. Please specify a valid value for CONSUMER_ID."

  val UserNoPermissionUpdateConsumer = "OBP-20015: Only the developer that created the consumer key should be able to edit it, please login with the right user."

  val UnexpectedErrorDuringLogin = "OBP-20016: An unexpected login error occurred. Please try again."

  val UserNoPermissionAccessView = "OBP-20017: Current user does not have access to the view. Please specify a valid value for VIEW_ID."


  val InvalidInternalRedirectUrl = "OBP-20018: Login failed, invalid internal redirectUrl."
  val UserNoOwnerView = "OBP-20019: User does not have access to owner view. "
  val InvalidCustomViewFormat = s"OBP-20020: Custom view name/view_id must start with `_`. eg: _work, _life. "
  val InvalidSystemViewFormat = s"OBP-20039: System view name/view_id can not start with '_'. eg: owner, standard. "
  val SystemViewsCanNotBeModified = "OBP-20021: System Views can not be modified. Only the created views can be modified."
  val ViewDoesNotPermitAccess = "OBP-20022: View does not permit the access."

  val ConsumerHasMissingRoles = "OBP-20023: Consumer is missing one or more roles:"
  val ConsumerNotFoundById = "OBP-20024: Consumer not found. Please specify a valid value for CONSUMER_ID."
  val ScopeNotFound = "OBP-20025: Scope not found. Please specify a valid value for SCOPE_ID."
  val ConsumerDoesNotHaveScope = "OBP-20026: CONSUMER_ID does not have the SCOPE_ID "

  val UserNotFoundByProviderAndUsername = "OBP-20027: User not found by provider and username."
  val GatewayLoginMissingParameters = "OBP-20028: These GatewayLogin parameters are missing:"
  val GatewayLoginUnknownError = "OBP-20029: Unknown Gateway login error."
  val GatewayLoginHostPropertyMissing = "OBP-20030: Property gateway.host is not defined."
  val GatewayLoginWhiteListAddresses = "OBP-20031: Gateway login can be done only from allowed addresses."
  val GatewayLoginJwtTokenIsNotValid = "OBP-20040: The Gateway login JWT is corrupted/changed during a transport."
  val GatewayLoginCannotExtractJwtToken = "OBP-20041: Header, Payload and Signature cannot be extracted from the JWT."
  val GatewayLoginNoNeedToCallCbs = "OBP-20042: There is no need to call CBS"
  val GatewayLoginCannotFindUser = "OBP-20043: User cannot be found. Please initiate CBS communication in order to create it."
  val GatewayLoginCannotGetCbsToken = "OBP-20044: Cannot get the CBSToken response from South side"
  val GatewayLoginCannotGetOrCreateUser = "OBP-20045: Cannot get or create user during GatewayLogin process."
  val GatewayLoginNoJwtForResponse = "OBP-20046: There is no useful value for JWT."
  val GatewayLoginIsDisabled = "OBP-20406: Gateway Login is disabled. Set allow_gateway_login=true to enable."

  val UserLacksPermissionCanGrantAccessToViewForTargetAccount =
    s"OBP-20047: If target viewId is system view,  the current view.can_grant_access_to_views does not contains it. Or" +
      s"if target viewId is custom view, the current view.can_grant_access_to_custom_views is false."

  val UserLacksPermissionCanRevokeAccessToViewForTargetAccount =
    s"OBP-20048: If target viewId is system view,  the current view.can_revoke_access_to_views does not contains it. Or" +
      s"if target viewId is custom view, the current view.can_revoke_access_to_custom_views is false."

  val SourceViewHasLessPermission = "OBP-20049: Source view contains less permissions than target view."

  val UserNotSuperAdmin = "OBP-20050: Current User is not a Super Admin!"

  val ElasticSearchIndexNotFound = "OBP-20051: Elasticsearch index or indices not found."
  val NotEnoughtSearchStatisticsResults = "OBP-20052: Result set too small. Will not be displayed for reasons of privacy."
  val ElasticSearchEmptyQueryBody = "OBP-20053: The Elasticsearch query body cannot be empty"
  val InvalidAmount = "OBP-20054: Invalid amount. Please specify a valid value for amount."
  val MissingQueryParams = "OBP-20055: These query parameters are missing:"
  val ElasticSearchDisabled  = "OBP-20056: Elasticsearch is disabled for this API instance."
  val UserNotFoundByUserId = "OBP-20057: User not found by userId."
  val ConsumerIsDisabled = "OBP-20058: Consumer is disabled."
  val CouldNotAssignAccountAccess = "OBP-20059: Could not assign account access. "
  val NoViewReadAccountsBerlinGroup = s"OBP-20060: User does not have access to the view:"
  val FrequencyPerDayError = s"OBP-20062: Frequency per day must be greater than 0 and less or equal to ${APIUtil.getPropsAsIntValue("berlin_group_frequency_per_day_upper_limit", 4)}"
  val FrequencyPerDayMustBeOneError = "OBP-20063: Frequency per day must be equal to 1 in case of one-off access."

  val UserIsDeleted = "OBP-20064: The user is deleted!"

  val DAuthCannotGetOrCreateUser = "OBP-20065: Cannot get or create user during DAuth process."
  val DAuthMissingParameters = "OBP-20066: These DAuth parameters are missing:"
  val DAuthUnknownError = "OBP-20067: Unknown DAuth login error."
  val DAuthHostPropertyMissing = "OBP-20068: Property dauth.host is not defined."
  val DAuthWhiteListAddresses = "OBP-20069: DAuth login can be done only from allowed addresses."
  val DAuthNoJwtForResponse = "OBP-20070: There is no useful value for JWT."
  val DAuthJwtTokenIsNotValid = "OBP-20071: The DAuth JWT is corrupted/changed during a transport."
  val InvalidDAuthHeaderToken = "OBP-20072: DAuth Header value should be one single string."
  val DAuthIsDisabled = "OBP-20407: DAuth is disabled. Set allow_dauth=true to enable."
  val OpenIDConnectIsDisabled = "OBP-20408: OpenID Connect is disabled. Set allow_openid_connect=true to enable."

  val InvalidProviderUrl = "OBP-20079: Cannot match the local identity provider."

  val InvalidAuthorizationHeader = "OBP-20080: Authorization Header format is not supported at this instance."

  val UserAttributeNotFound = "OBP-20081: User Attribute not found by USER_ATTRIBUTE_ID."
  val MissingDirectLoginHeader = "OBP-20082: Missing DirectLogin or Authorization header."
  val InvalidDirectLoginHeader = "OBP-20083: Missing DirectLogin word at the value of Authorization header."


  val UserLacksPermissionCanGrantAccessToSystemViewForTargetAccount =
    s"OBP-20084: The current source view.can_grant_access_to_views does not contains target view."

  val UserLacksPermissionCanGrantAccessToCustomViewForTargetAccount =
    s"OBP-20085: The current source view.can_grant_access_to_custom_views is false."

  val UserLacksPermissionCanRevokeAccessToSystemViewForTargetAccount =
    s"OBP-20086: The current source view.can_revoke_access_to_views does not contains target view."

  val UserLacksPermissionCanRevokeAccessToCustomViewForTargetAccount =
    s"OBP-20087: The current source view.can_revoke_access_to_custom_views is false."

  val BerlinGroupConsentAccessIsEmpty = s"OBP-20088: An access must be requested."
  val BerlinGroupConsentAccessRecurringIndicator = s"OBP-20089: Recurring indicator must be false when availableAccounts is used."
  val BerlinGroupConsentAccessFrequencyPerDay = s"OBP-20090: Frequency per day must be 1 when availableAccounts is used."
  val BerlinGroupConsentAccessAvailableAccounts = s"OBP-20091: availableAccounts must be exactly 'allAccounts'."

  // SIWE (Sign-In With Ethereum, EIP-4361). Feature is off by default (allow_siwe=false).
  val SiweNotEnabled = "OBP-20092: SIWE authentication is not enabled on this instance."
  val SiweConfigMissing = "OBP-20093: SIWE is enabled but not fully configured. Missing required prop: "
  val SiweMessageMalformed = "OBP-20094: The SIWE message could not be parsed as an EIP-4361 message."
  val SiweInvalidAddress = "OBP-20095: The address in the SIWE message is not a valid Ethereum address."
  val SiweDomainMismatch = "OBP-20096: The domain in the SIWE message does not match the configured siwe.domain."
  val SiweChainIdNotAllowed = "OBP-20097: The Chain ID in the SIWE message is not in siwe.allowed_chain_ids."
  val SiweNonceInvalid = "OBP-20098: The nonce is unknown, already used, or expired. Request a new challenge."
  val SiweMessageExpired = "OBP-20099: The SIWE message has expired (Expiration Time is in the past)."
  val SiweSignatureInvalid = "OBP-20100: The signature does not match the address claimed in the SIWE message."

  val UserNotSuperAdminOrMissRole = "OBP-20101: Current User is not super admin or is missing entitlements:"
  val CannotGetOrCreateUser = "OBP-20102: Cannot get or create user."
  val InvalidUserProvider = "OBP-20103: Invalid DAuth User Provider."
  val UserNotFoundByProviderAndProvideId= "OBP-20104: User not found by PROVIDER and PROVIDER_ID."

  val BankAccountBalanceNotFoundById = "OBP-20105: BankAccountBalance not found. Please specify a valid value for BALANCE_ID."
  val UserNotFoundByToken = "OBP-20106: User not found by token. The validation token is invalid or expired."
  val UserAlreadyValidated = "OBP-20107: User email is already validated."

  // OAuth 2
  val ApplicationNotIdentified = "OBP-20200: The application cannot be identified. "
  val Oauth2IsNotAllowed = "OBP-20201: OAuth2 is not allowed at this instance."
  val Oauth2IJwtCannotBeVerified = "OBP-20202: OAuth2's Access Token cannot be verified."
  val Oauth2ThereIsNoUrlOfJwkSet = "OBP-20203: There is no an URL of OAuth 2.0 server's JWK set, published at a well-known URL."
  val Oauth2BadJWTException = "OBP-20204: Bad JWT error. "
  val Oauth2ParseException = "OBP-20205: Parse error. "
  val Oauth2BadJOSEException = "OBP-20206: Bad JSON Object Signing and Encryption (JOSE) exception. The ID token is invalid or expired. OBP-API Admin should check the oauth2.jwk_set.url list contains the jwks url of the provider."
  val Oauth2JOSEException = "OBP-20207: Bad JSON Object Signing and Encryption (JOSE) exception. An internal JOSE exception was encountered. "
  val Oauth2CannotMatchIssuerAndJwksUriException = "OBP-20208: Cannot match the issuer and JWKS URI at this server instance. "
  val Oauth2TokenHaveNoConsumer = "OBP-20209: The token have no linked consumer. "
  val Oauth2TokenMatchCertificateFail = "OBP-20210: The token is linked with a different client certificate. "
  val Oauth2TokenEndpointAuthMethodForbidden = "OBP-20213: The Token Endpoint Auth Method is not supported at this instance: "
  val OneTimePasswordExpired = "OBP-20211: The One Time Password (OTP) has expired. "
  val Oauth2IsNotRecognized = "OBP-20214: OAuth2 Access Token is not recognised at this instance."
  val Oauth2ValidateAccessTokenError = "OBP-20215: There was a problem validating the OAuth2 access token. "
  val OneTimePasswordInvalid = "OBP-20216: The One Time Password (OTP) is invalid. "
  val Oauth2TokenAudienceNotAllowed = "OBP-20217: The Bearer token was not issued for this API instance. Audience (aud) claim is not allowed."
  val Oauth2ProviderNotEnabled = "OBP-20218: The token issuer is not an enabled OAuth2 provider at this API instance. The provider is not listed in the oauth2.oidc_provider props."

  val AuthorizationHeaderAmbiguity = "OBP-20250: Request headers used for authorization are ambiguous. "
  val MissingMandatoryBerlinGroupHeaders= "OBP-20251: Missing mandatory request headers. "
  val EmptyRequestHeaders = "OBP-20252: Empty or null headers are not allowed. "
  val InvalidUuidValue = "OBP-20253: Invalid format. Must be a UUID."
  val InvalidSignatureHeader = "OBP-20254: Invalid Signature header. "
  val InvalidRequestIdValueAlreadyUsed = "OBP-20255: Request Id value already used. "
  val InvalidConsentIdUsage = "OBP-20256: Consent-Id must not be used for this API Endpoint. "
  val NotValidRfc7231Date = "OBP-20257: Request header Date is not in accordance with RFC 7231 "

  val DuplicateUsername = "OBP-20258: Duplicate Username. Cannot create Username because it already exists. "
  val ExternalUserCheckFailed = "OBP-20259: Could not check username uniqueness against the external provider. The Connector or Adapter may not be running. "

  val Oauth2TokenBindingCertificateMissing = "OBP-20260: The access token is certificate-bound (cnf.x5t#S256) but no client certificate was presented with the request. "
  val Oauth2TokenBindingCertificateMismatch = "OBP-20261: The presented client certificate does not match the certificate binding (cnf.x5t#S256) of the access token. "
  val Oauth2TokenBindingRequired = "OBP-20262: This instance requires certificate-bound access tokens (oauth2.token_binding.mode=REQUIRED) but the token carries no cnf.x5t#S256 claim. "


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
  val X509CannotGetPublicKey = "OBP-20309: Public key cannot be found in the PEM Encoded Certificate."
  val X509PublicKeyCannotVerify = "OBP-20310: The signed request cannot be verified by certificate's public key."
  val X509PublicKeyCannotBeValidated = "OBP-20312: Certificate's public key cannot be validated."
  val X509RequestIsNotSigned = "OBP-20311: The Request is not signed."

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
  val NoViewPermission = "OBP-30022: The current view does not have the permission:"
  val UpdateConsumerError = "OBP-30023: Cannot update Consumer "
  val CreateConsumerError = "OBP-30024: Could not create Consumer "
  val CreateOAuth2ConsumerError = "OBP-30077: Could not create OAuth2 Consumer."

  val CreateUserCustomerLinksError = "OBP-30025: Could not create user_customer_links "
  val ConsumerKeyAlreadyExists = "OBP-30026: Consumer Key already exists. Please specify a different value."
  val NoExistingAccountHolders = "OBP-30027: Account Holders not found. The BANK_ID / ACCOUNT_ID specified for account holder does not exist on this server"


  val CreateAtmError = "OBP-30028: Could not insert the ATM"
  val DeleteAtmError = "OBP-30120: Could not delete the ATM"
  val UpdateAtmError = "OBP-30029: Could not update the ATM"

  val CreateProductError = "OBP-30030: Could not insert the Product."
  val UpdateProductError = "OBP-30031: Could not update the Product."
  val GetProductError = "OBP-30320: Could not get the Product."
  val GetProductTreeError = "OBP-30321: Could not get the Product Tree."

  val CreateCardError = "OBP-30032: Could not insert the Card"
  val UpdateCardError = "OBP-30033: Could not update the Card"

  val ViewIdNotSupported = s"OBP-30034: This ViewId is not supported. Only the following can be used: "

  val UserCustomerLinkNotFound = "OBP-30035: User Customer Link not found"

  val CreateOrUpdateCounterpartyMetadataError = "OBP-30036: Could not create or update CounterpartyMetadata"
  val CounterpartyMetadataNotFound = "OBP-30037: CounterpartyMetadata not found. Please specify valid values for BANK_ID, ACCOUNT_ID and COUNTERPARTY_ID. "

  val CreateFxRateError = "OBP-30038: Could not insert the Fx Rate"
  val UpdateFxRateError = "OBP-30039: Could not update the Fx Rate"
  val UnknownFxRateError = "OBP-30040: Unknown Fx Rate error"

  val CheckbookOrderNotFound = "OBP-30041: CheckbookOrder not found for Account. "
  val GetTopApisError = "OBP-30042: Could not get the top apis from database.  "
  val GetTopUsersError = "OBP-30551: Could not get the top users from database.  "
  val GetTopConsumersError = "OBP-30552: Could not get the top consumers from database.  "
  val GetMetricsTopConsumersError = "OBP-30045: Could not get the top consumers from database.  "
  val GetAggregateMetricsError = "OBP-30043: Could not get the aggregate metrics from database.  "

  val DefaultBankIdNotSet = "OBP-30044: Default BankId is not set on this instance. Please set defaultBank.bank_id in props files. "
  val ExcludeParametersNotSupported = "OBP-30146: The exclude_* parameters are not supported in v6.0.0+. Please use the corresponding include_* parameters instead (include_app_names, include_url_patterns, include_implemented_by_partial_functions). "

  val CustomerLinkNotFound = "OBP-30147: Customer Link not found. Please specify a valid value for CUSTOMER_LINK_ID."
  val CreateCustomerLinkError = "OBP-30148: Could not create the Customer Link."
  val UpdateCustomerLinkError = "OBP-30149: Could not update the Customer Link."
  val InvestigationReportNotAvailable = "OBP-30150: Investigation Report is only available in mapped mode (connector=mapped)."

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
  val UserAuthContextUpdateStatusError = "OBP-30090: User Auth Context Update status is not INITIATED. It has already been actioned."
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
  val AccountRoutingNotUnique = "OBP-31075: Routing is not unique at this instance"
  val BankAccountNotFoundByAccountId = "OBP-30076: Bank Account not found. Please specify a valid value for ACCOUNT_ID."

  val TransactionRequestAttributeNotFound = "OBP-30078: Transaction Request Attribute not found. Please specify a valid value for TRANSACTION_REQUEST_ATTRIBUTE_ID."

  val ApiCollectionNotFound = "OBP-30079: ApiCollection not found."
  val CreateApiCollectionError = "OBP-30080: Could not create ApiCollection."
  val UpdateApiCollectionError = "OBP-3008A: Could not update ApiCollection."
  val DeleteApiCollectionError = "OBP-30081: Could not delete ApiCollection."

  val ApiCollectionEndpointNotFound = "OBP-30082: ApiCollectionEndpoint not found."
  val CreateApiCollectionEndpointError = "OBP-30083: Could not create ApiCollectionEndpoint."
  val DeleteApiCollectionEndpointError = "OBP-30084: Could not delete ApiCollectionEndpoint."
  val ApiCollectionEndpointAlreadyExists = "OBP-30085: The ApiCollectionEndpoint is already exists."
  val ApiCollectionAlreadyExists = "OBP-30086: The ApiCollection is already exists."

  val ApiProductNotFound = "OBP-30500: ApiProduct not found. Please specify a valid value for BANK_ID and API_PRODUCT_CODE."
  val CreateApiProductError = "OBP-30501: Could not create ApiProduct."
  val DeleteApiProductError = "OBP-30502: Could not delete ApiProduct."
  val ApiProductAttributeNotFound = "OBP-30503: ApiProductAttribute not found. Please specify a valid value for API_PRODUCT_ATTRIBUTE_ID."
  val CreateApiProductAttributeError = "OBP-30504: Could not create ApiProductAttribute."
  val DeleteApiProductAttributeError = "OBP-30505: Could not delete ApiProductAttribute."

  val OrganisationNotFound = "OBP-30506: Organisation not found. Please specify a valid value for ORGANISATION_ID."
  val OrganisationAlreadyExists = "OBP-30507: Organisation already exists. Please specify a different value for ORGANISATION_ID."
  val InvalidOrganisationIdFormat = "OBP-30508: Invalid Organisation Id. The ORGANISATION_ID should only contain 0-9/a-z/A-Z/'-'/'.'/'_', and be between 2 and 64 characters in length."
  val InvalidOrganisationStatus = "OBP-30509: Invalid Organisation status. Allowed values are: active, suspended, archived."
  val InvalidOrganisationVisibility = "OBP-30510: Invalid Organisation visibility. Allowed values are: public, unlisted, private."
  val CreateOrganisationError = "OBP-30511: Could not create Organisation."
  val UpdateOrganisationError = "OBP-30512: Could not update Organisation."
  val DeleteOrganisationError = "OBP-30513: Could not delete Organisation."

  // Routing Scheme registry (OBP-30514 .. OBP-30525)
  val RoutingSchemeNotFound = "OBP-30514: Routing Scheme not found. Please specify a valid value for SCHEME."
  val RoutingSchemeAlreadyExists = "OBP-30515: Routing Scheme already exists. Please specify a different value for scheme."
  val InvalidRoutingSchemeName = "OBP-30516: Invalid Routing Scheme name. Must match ^(?:IBAN|BIC|OBP|[A-Z]{2}(?:\\.[A-Z][A-Z0-9_]*)+)$ — either an allow-listed global scheme (IBAN, BIC, OBP) or a country-qualified name like TZ.MSISDN."
  val RoutingSchemeCountryMismatch = "OBP-30517: Routing Scheme country prefix does not match the country field."
  val InvalidRoutingSchemeCategory = "OBP-30518: Invalid Routing Scheme category. Allowed values are: ACCOUNT, BANK, BRANCH, IDENTITY, BILL, UTILITY."
  val InvalidRoutingSchemeStatus = "OBP-30519: Invalid Routing Scheme status. Allowed values are: ACTIVE, RESERVED, DEPRECATED, RETIRED."
  val InvalidRoutingSchemeAddressPattern = "OBP-30520: Invalid Routing Scheme address_pattern. The value must be a valid regular expression."
  val RoutingSchemeExampleAddressMismatch = "OBP-30521: Routing Scheme example_address does not match address_pattern."
  val CreateRoutingSchemeError = "OBP-30522: Could not create Routing Scheme."
  val UpdateRoutingSchemeError = "OBP-30523: Could not update Routing Scheme."
  val DeleteRoutingSchemeError = "OBP-30524: Could not delete Routing Scheme."
  val RoutingSchemeNotSupportedByBank = "OBP-30525: This bank does not support the requested Routing Scheme."

  // Payee Lookup (OBP-30526 .. OBP-30530)
  val PayeeLookupIdentifierTypeNotRegistered = "OBP-30526: identifier_type is not a registered Routing Scheme. Register it via POST /routing-schemes first."
  val PayeeLookupIdentifierTypeWrongCategory = "OBP-30527: identifier_type category is not valid for payee lookup. Allowed categories are: ACCOUNT, BILL, UTILITY."
  val PayeeLookupAddressMismatch = "OBP-30528: identifier does not match the address_pattern of the identifier_type."
  val PayeeNotFound = "OBP-30529: No payee was found for the given identifier."
  val PayeeLookupCreateError = "OBP-30530: Could not create payee lookup."

  // Mobile-Wallet transaction-request (OBP-30531 .. OBP-30535)
  val PayeeLookupExpiredOrNotFound = "OBP-30531: verified_payee_lookup_id is unknown or has expired. Lookups are valid for 10 minutes."
  val PayeeLookupMismatch = "OBP-30532: verified_payee_lookup_id does not match the supplied identifier."
  val MobileWalletDestinationNotFound = "OBP-30533: No mobile-wallet account is registered for the supplied msisdn. In mapped mode the destination must have an account routing for the country-qualified MSISDN scheme (e.g. TZ.MSISDN)."
  val MobileWalletInvalidMsisdn = "OBP-30534: Invalid msisdn — does not match the address_pattern of the country-qualified MSISDN routing scheme."
  val MobileWalletPaymentError = "OBP-30535: Could not create MOBILE_WALLET transaction request."

  // BULK transaction-request (OBP-30536 .. OBP-30544)
  val BulkBatchReferenceAlreadyUsed = "OBP-30536: batch_reference has already been used for this source account. Use a unique batch_reference per submission."
  val BulkPaymentsArrayEmpty = "OBP-30537: payments array must contain at least one item."
  val BulkPaymentsArrayTooLarge = "OBP-30538: payments array exceeds the configured maximum. See `bulk_payments.max_items_per_batch`."
  val BulkDuplicateEndToEndId = "OBP-30539: Duplicate end_to_end_id within the batch. Each item's end_to_end_id must be unique within a single batch submission."
  val BulkPaymentCurrencyMismatch = "OBP-30540: One or more payments use a currency that does not match the source account's currency. Cross-currency bulk payments are not supported in v7.0.0."
  val BulkPaymentRoutingSchemeNotRegistered = "OBP-30541: A payment references a routing_scheme that is not in the Routing-Scheme registry."
  val BulkPaymentRoutingSchemeWrongCategory = "OBP-30542: A payment's routing_scheme is not an ACCOUNT-category scheme — only ACCOUNT schemes are valid for BULK destinations."
  val BulkPaymentAddressMismatch = "OBP-30543: A payment's address does not match the address_pattern of its routing_scheme."
  val BulkPaymentTransactionRequestError = "OBP-30544: Could not create BULK transaction request."

  // UTILITY transaction-request (OBP-30546 .. OBP-30549)
  // Polymorphic bill/utility payment (prepaid utility meter, bill control number, ...).
  // The destination is identified by a QualifiedIdentifier whose `scheme` must be a
  // registered routing scheme of category UTILITY or BILL.
  val UtilityIdentifierTypeWrongCategory = "OBP-30546: identifier scheme category is not valid for a UTILITY payment. Allowed categories are: UTILITY, BILL."
  val UtilityInvalidIdentifier = "OBP-30547: Invalid identifier value — does not match the address_pattern of the routing scheme (e.g. TZ.UTILITY_METER)."
  val UtilityDestinationNotFound = "OBP-30548: No biller/utility account is registered for the supplied identifier. In mapped mode the destination must have an account routing for the identifier scheme (e.g. TZ.UTILITY_METER)."
  val UtilityPaymentError = "OBP-30549: Could not create UTILITY transaction request."
  val UtilityTransactionRequestNotFound = "OBP-30550: No UTILITY transaction request found for the supplied UTILITY_TRANSACTION_REQUEST_ID."

  // Implicit OBP-family routing schemes (OBP-30545)
  // The schemes "OBP" / "OBP_ACCOUNT_ID" / "OBP_BANK_ID" are reserved self-identifiers —
  // they map (scheme, address) directly to (kind, primary_key) without a stored row.
  // Any attempt to persist them as explicit account/bank routings is rejected.
  val OBPSchemeIsImplicit = "OBP-30545: OBP-family routing schemes (OBP, OBP_ACCOUNT_ID, OBP_BANK_ID) are implicit self-identifiers and cannot be set explicitly. Remove these entries from the routings list."

  val FeaturedApiCollectionNotFound = "OBP-30400: FeaturedApiCollection not found. Please specify a valid value for API_COLLECTION_ID."
  val CreateFeaturedApiCollectionError = "OBP-30401: Could not create FeaturedApiCollection."
  val UpdateFeaturedApiCollectionError = "OBP-30402: Could not update FeaturedApiCollection."
  val DeleteFeaturedApiCollectionError = "OBP-30403: Could not delete FeaturedApiCollection."
  val FeaturedApiCollectionAlreadyExists = "OBP-30404: The ApiCollection is already featured."

  val DoubleEntryTransactionNotFound = "OBP-30087: Double Entry Transaction not found."

  val InvalidAuthContextUpdateRequestKey = "OBP-30088: Invalid Auth Context Update Request Key."

  val UpdateAtmSupportedLanguagesException = "OBP-30089: Could not update the Atm Supported Languages."

  val UpdateAtmSupportedCurrenciesException = "OBP-30091: Could not update the Atm Supported Currencies."

  val UpdateAtmAccessibilityFeaturesException = "OBP-30092: Could not update the Atm Accessibility Features."

  val UpdateAtmServicesException = "OBP-30093: Could not update the Atm Services."

  val UpdateAtmNotesException = "OBP-30094: Could not update the Atm Notes."

  val UpdateAtmLocationCategoriesException = "OBP-30095: Could not update the Atm Location Categories."

  val CreateEndpointTagError = "OBP-30096: Could not insert the Endpoint Tag."
  val UpdateEndpointTagError = "OBP-30097: Could not update the Endpoint Tag."
  val UnknownEndpointTagError = "OBP-30098: Unknown Endpoint Tag error. "
  val EndpointTagNotFoundByEndpointTagId = "OBP-30099: Invalid ENDPOINT_TAG_ID. Please specify a valid value for ENDPOINT_TAG_ID."
  val EndpointTagAlreadyExists = "OBP-30100: EndpointTag already exists."

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

  val ProductFeeNotFoundById = "OBP-30117: Product Fee not found. Please specify a valid value for PRODUCT_FEE_ID."
  val CreateProductFeeError = "OBP-30118: Could not insert the Product Fee."
  val UpdateProductFeeError = "OBP-30119: Could not update the Product Fee."

  val InvalidCardNumber = "OBP-30200: Card not found. Please specify a valid value for CARD_NUMBER. "
  val AgentNotFound = "OBP-30201: Agent not found. Please specify a valid value for AGENT_ID. "
  val CreateAgentError = "OBP-30202: Could not create Agent."
  val UpdateAgentError = "OBP-30203: Could not update Agent."

  val CustomerAccountLinkNotFound = "OBP-30204: Customer Account Link not found"

  val EntitlementIsBankRole = "OBP-30205: This entitlement is a Bank Role. Please set bank_id to a valid bank id."
  val EntitlementIsSystemRole = "OBP-30206: This entitlement is a System Role. Please set bank_id to empty string."


  val InvalidStrongPasswordFormat = "OBP-30207: Invalid Password Format. Your password should EITHER be at least 10 characters long and contain mixed numbers and both upper and lower case letters and at least one special character, OR the length should be > 16 and <= 512."

  val AccountIdAlreadyExists = "OBP-30208: Account_ID already exists at the Bank."


  val InsufficientAuthorisationToCreateBranch  = "OBP-30209: Insufficient authorisation to Create Branch. You do not have the role CanCreateBranch." // was OBP-20019
  val InsufficientAuthorisationToDeleteBranch  = "OBP-30218: Insufficient authorisation to Create Branch. You do not have the role CanCreateBranch." // was OBP-20019
  val InsufficientAuthorisationToCreateBank  = "OBP-30210: Insufficient authorisation to Create Bank. You do not have the role CanCreateBank." // was OBP-20020

  val InvalidConnector = "OBP-30211: Invalid Connector. Please specify a valid value for CONNECTOR."

  val EntitlementNotFound = "OBP-30212: EntitlementId not found"
  val UserDoesNotHaveEntitlement = "OBP-30213: USER_ID does not have the ENTITLEMENT_ID."
  val EntitlementRequestAlreadyExists = "OBP-30214: Entitlement Request already exists for the user."
  val EntitlementRequestCannotBeAdded = "OBP-30217: Entitlement Request cannot be added."
  val EntitlementRequestNotFound = "OBP-30215: EntitlementRequestId not found"
  val EntitlementAlreadyExists = "OBP-30216: Entitlement already exists for the user."
  val EntitlementCannotBeDeleted = "OBP-30219: EntitlementId cannot be deleted."
  val EntitlementCannotBeGranted = "OBP-30220: Entitlement cannot be granted."
  val EntitlementCannotBeGrantedGrantorIssue = "OBP-30221: Entitlement cannot be granted due to the grantor's insufficient privileges."

  val CounterpartyNotFoundByRoutings = "OBP-30222: Counterparty not found. Please specify valid value for Routings."
  val AccountAlreadyExistsForCustomer = "OBP-30223: The Account is already linked to a Customer at the bank specified by BANK_ID"
  val CreateCustomerAccountLinkError = "OBP-30224: Could not create the customer account link."
  val CustomerAccountLinkNotFoundById = "OBP-30225: Customer Account Link not found. Please specify valid values for CUSTOMER_ACCOUNT_LINK_ID."
  val GetCustomerAccountLinksError = "OBP-30226: Could not get the customer account links."
  val UpdateCustomerAccountLinkError = "OBP-30227: Could not update the customer account link."
  val DeleteCustomerAccountLinkError = "OBP-30228: Could not delete the customer account link."
  val GetConsentImplicitSCAError = "OBP-30229: Could not get the implicit SCA consent."
  val SelfServiceBankCreationDisabled = "OBP-30230: Self-service bank creation is disabled on this instance."
  val SelfServiceBankLimitReached = "OBP-30231: Self-service bank creation limit reached. To create more banks you require the role CanCreateBank."

  val CreateSystemViewError = "OBP-30250: Could not create the system view"
  val DeleteSystemViewError = "OBP-30251: Could not delete the system view"
  val SystemViewNotFound = "OBP-30252: System view not found. Please specify a valid value for VIEW_ID"
  val UpdateSystemViewError = "OBP-30253: Could not update the system view"
  val SystemViewAlreadyExistsError = "OBP-30254: The system view is already exists."
  val EmptyNameOfSystemViewError = "OBP-30255: You cannot create a View with an empty Name"
  val DeleteCustomViewError = "OBP-30256: Could not delete the custom view"
  val CannotFindCustomViewError = "OBP-30257: Could not find the custom view"
  val SystemViewCannotBePublicError = "OBP-30258: System view cannot be public"
  val CreateCustomViewError = "OBP-30259: Could not create the custom view"
  val UpdateCustomViewError = "OBP-30260: Could not update the custom view"
  val CreateCounterpartyLimitError = "OBP-30261: Could not create the counterparty limit."
  val UpdateCounterpartyLimitError = "OBP-30262: Could not update the counterparty limit."
  val GetCounterpartyLimitError = "OBP-30263: Counterparty limit not found. Please specify a valid value for BANK_ID, ACCOUNT_ID, VIEW_ID or COUNTERPARTY_ID."
  val CounterpartyLimitAlreadyExists = "OBP-30264: Counterparty limit already exists. Please specify a different value for BANK_ID, ACCOUNT_ID, VIEW_ID or COUNTERPARTY_ID."
  val DeleteCounterpartyLimitError = "OBP-30265: Could not delete the counterparty limit."
  val CustomViewAlreadyExistsError = "OBP-30266: The custom view is already exists."
  val UserDoesNotHavePermission = "OBP-30267: The user does not have the permission:"
  val CounterpartyLimitValidationError = "OBP-30268: Counterparty Limit Validation Error."
  val AccountNumberNotUniqueError = "OBP-30269: Finding an account by the accountNumber is ambiguous."
  val InvalidAccountNumber = "OBP-30270: Account not found. Please specify a valid value for ACCOUNT_NUMBER."
  val BankAccountNotFoundByRoutings = "OBP-30271: Bank Account not found. Please specify valid values for routing schemes and addresses."

  val InvalidCustomerType = "OBP-30272: Invalid customer_type. Must be one of: INDIVIDUAL, CORPORATE, SUBSIDIARY."
  val ParentCustomerNotFound = "OBP-30273: Parent customer not found. The parent_customer_id must reference an existing customer at the same bank."
  val CustomerTypeMismatch = "OBP-30274: Customer type does not match the endpoint. Use the generic /customers endpoint or the correct type-specific endpoint."

  val AccountAccessRequestNotFound = "OBP-30275: Account Access Request not found."
  val AccountAccessRequestAlreadyExists = "OBP-30276: An Account Access Request already exists for this user, account, and view."
  val AccountAccessRequestCannotBeCreated = "OBP-30277: Account Access Request could not be created."
  val AccountAccessRequestStatusNotInitiated = "OBP-30278: Account Access Request status is not INITIATED. Only INITIATED requests can be approved or rejected."
  val MakerCheckerSameUser = "OBP-30279: The checker (approver/rejecter) cannot be the same user as the maker (requestor). Maker/Checker separation is required."
  val MakerCheckerUnknownMaker = "OBP-30283: Maker/Checker separation is required for this view but the Transaction Request has no user_id (maker) recorded. Cannot verify separation."
  val BusinessJustificationRequired = "OBP-30280: Business justification is required."
  val CheckerCommentRequiredForRejection = "OBP-30281: A comment is required when rejecting an Account Access Request."
  val AccountAccessRequestCannotBeUpdated = "OBP-30282: Account Access Request could not be updated."

  val TaxResidenceNotFound = "OBP-30300: Tax Residence not found by TAX_RESIDENCE_ID. "
  val CustomerAddressNotFound = "OBP-30310: Customer's Address not found by CUSTOMER_ADDRESS_ID. "
  val AccountApplicationNotFound = "OBP-30311: AccountApplication not found by ACCOUNT_APPLICATION_ID. "
  val ResourceUserNotFound = "OBP-30312: ResourceUser not found by USER_ID. "
  val UserIdAndCustomerIdNotPresent = "OBP-30313: userId and customerId not present. "
  val AccountApplicationAlreadyAccepted = "OBP-30314: AccountApplication has already been accepted. "
  val UpdateAccountApplicationStatusError = "OBP-30315: AccountApplication Status can not be updated. "
  val CreateAccountApplicationError = "OBP-30316: AccountApplication Status can not be created. "

  val DeleteCounterpartyError = "OBP-30317: Could not delete the Counterparty."
  val DeleteCounterpartyMetadataError = "OBP-30318: Could not delete CounterpartyMetadata"
  val UpdateBankAccountLabelError = "OBP-30319: Could not update Bank Account Label."

  val GetChargeValueError = "OBP-30323: Could not get the Charge Value."
  val GetTransactionRequestTypeChargesError = "OBP-30324: Could not get Transaction Request Type Charges."
  val AgentAccountLinkNotFound = "OBP-30325: Agent Account Link not found."
  val AgentsNotFound = "OBP-30326: Agents not found."
  val CreateAgentAccountLinkError = "OBP-30327: Could not create the agent account link."
  val AgentNumberAlreadyExists = "OBP-30328: Agent Number already exists. Please specify a different value for BANK_ID or AGENT_NUMBER."
  val GetAgentAccountLinksError = "OBP-30329: Could not get the agent account links."
  val AgentBeneficiaryPermit = "OBP-30330: The account can not send money to the Agent. Please set the Agent 'is_confirmed_agent' true  and `is_pending_agent` false."
  val InvalidEntitlement = "OBP-30331: Invalid Entitlement Name. Please specify a proper name."
  val CannotAddEntitlement = "OBP-30332: Failed to add entitlement. Please check the provided details and try again."
  val CannotGetEntitlements = "OBP-30333: Cannot get entitlements for user id."

  val ViewPermissionNameExists = "OBP-30334: View Permission name already exists. Please specify a different value."
  val CreateViewPermissionError = "OBP-30335: Could not create the View Permission."
  val ViewPermissionNotFound = "OBP-30336: View Permission not found by name. "
  val InvalidViewPermissionName = "OBP-30337: The view permission name does not exist in OBP."
  val DeleteViewPermissionError = "OBP-30338: Could not delete the View Permission."

  val UserEmailAddressMissing = "OBP-30339: User does not have an email address set. Cannot send test email."
  val EmailSendingFailed = "OBP-30340: Failed to send email. Check the server logs and SMTP configuration."
  val SmtpAuthenticationFailed = "OBP-30341: SMTP authentication failed. Check mail.smtp.user and mail.smtp.password."
  val SmtpConnectionFailed = "OBP-30342: Could not connect to SMTP server. Check mail.smtp.host, mail.smtp.port and network reachability."
  val SmtpTlsHandshakeFailed = "OBP-30343: TLS handshake with SMTP server failed. Check mail.smtp.starttls.enable, mail.smtp.ssl.enable and mail.smtp.ssl.protocols."
  val SmtpRecipientRejected = "OBP-30344: SMTP server rejected the recipient or message. The From address may be unauthorised, the recipient may be invalid, or the message may have failed policy/anti-spam checks."
  val SmtpProtocolError = "OBP-30345: SMTP protocol error from the mail server."

  // Branch related messages
  val BranchesNotFoundLicense = "OBP-32001: No branches available. License may not be set."
  val BranchesNotFound = "OBP-32002: No branches available."

  // ATM related messages
  val atmsNotFoundLicense = "OBP-33001: No ATMs available. License may not be set."
  val atmsNotFound = "OBP-33002: No ATMs available."
  val DeleteAtmAttributeError = "OBP-33003: Could not delete ATM Attribute."

  // Bank related messages
  val bankIdAlreadyExists = "OBP-34000: Bank Id already exists. Please specify a different value."
  val updateBankError = "OBP-34001: Could not update the Bank"

  val RegulatedEntityNotFound = "OBP-34100: Regulated Entity not found. Please specify a valid value for REGULATED_ENTITY_ID."
  val RegulatedEntityNotDeleted = "OBP-34101: Regulated Entity cannot be deleted. Please specify a valid value for REGULATED_ENTITY_ID."
  val RegulatedEntityNotFoundByCertificate = "OBP-34102: Regulated Entity cannot be found by provided certificate."
  val RegulatedEntityAmbiguityByCertificate = "OBP-34103: More than 1 Regulated Entity found by provided certificate."
  val PostJsonIsNotSigned = "OBP-34110: JWT at the post json cannot be verified."

  // Consents
  val ConsentNotFound = "OBP-35001: Consent not found by CONSENT_ID. "
  val ConsentNotBeforeIssue = "OBP-35002: The Consent Not Before time (nbf) is in the future. Not Before (nbf) should be in the past. Please make sure the Consent nbf is before the current date time of the OBP API server. "
  val ConsentExpiredIssue = "OBP-35003: Consent-Id is expired. "
  val ConsentVerificationIssue = "OBP-35004: Consent-Id JWT value couldn't be verified. "
  val ConsentStatusIssue = "OBP-35005: Consent-Id is not in status "
  val ConsentCheckExpiredIssue = "OBP-35006: Cannot check is Consent-Id expired. "
  val ConsentDisabled = "OBP-35007: Consents are not allowed at this instance. "
  val ConsentHeaderNotFound = "OBP-35008: Cannot get Consent-Id. "
  val ConsentAllowedScaMethods = "OBP-35009: Only SMS, EMAIL and IMPLICIT are supported as SCA methods. "
  val SmsServerNotResponding = "OBP-35010: SMS server is not working or SMS server can not send the message to the phone number:"
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
  val ConsentUserCannotBeAdded = "OBP-35026: The Consent's User cannot be added."
  val ConsentUserAuthContextCannotBeAdded = "OBP-35027: The Consent's User Auth Context cannot be added."
  val ConsentRequestNotFound = "OBP-35028: Consent Request not found by CONSENT_REQUEST_ID. "
  val ConsentRequestIsInvalid = "OBP-35029: The CONSENT_REQUEST_ID is invalid. "
  val ConsumerKeyIsInvalid = "OBP-35030: The Consumer Key must be alphanumeric. (A-Z, a-z, 0-9)"
  val ConsumerKeyIsToLong = "OBP-35031: The Consumer Key max length <= 512"
  val ConsentHeaderValueInvalid = "OBP-35032: The Consent's Request Header value is not formatted as UUID or JWT."
  val RolesForbiddenInConsent = s"OBP-35033: Consents cannot contain the following Roles: ${canCreateEntitlementAtOneBank} and ${canCreateEntitlementAtAnyBank}."
  val UserAuthContextUpdateRequestAllowedScaMethods = "OBP-35034: Unsupported as SCA method. "
  val ConsentIdClaimMissing = "OBP-35035: The access token is not bound to a Consent. The identity provider must include a consent_id claim in access tokens issued via the consent authorisation flow. "
  val ConsentDoesNotMatchStandard = "OBP-35036: The Consent was created by a different API standard than the endpoint using it. A consent may only be used by endpoints of the standard that created it. "
  val ConsentAccountNotHeldByUser = "OBP-35037: One or more of the specified account_ids is not held by the current user. A consent may only be authorised for accounts the authorising user holds. "
  val InvalidUKConsentPermissions = "OBP-35038: The Permissions array is not a valid combination for UK Open Banking. "
  val BerlinGroupPsuNotIdentified = "OBP-35039: The PSU this authorisation is for cannot be identified. Send the PSU-ID header, or authenticate as the PSU. "
  val ConsentNamesNoAccount = "OBP-35040: The Consent names no account, so it grants no access. It was authorised before consents were bound to accounts; re-authorise it to select which accounts it applies to. "
  val ConsentAccountAccessCannotBeGranted = "OBP-35041: The Consent's account access cannot be granted. The Consent has not been authorised; please retry the authorisation. "

  //Authorisations
  val AuthorisationNotFound = "OBP-36001: Authorisation not found. Please specify valid values for PAYMENT_ID and AUTHORISATION_ID. "
  val InvalidAuthorisationStatus = "OBP-36002: Authorisation Status is Invalid"
  val AuthorisationNotFoundByPaymentId = "OBP-36003: Authorisation not found. Please specify valid values for PAYMENT_ID. "

  //EndpointMappings
  val EndpointMappingNotFoundByEndpointMappingId = "OBP-36004: Endpoint Mapping not found. Please specify valid values for ENDPOINT_MAPPING_ID. "
  val EndpointMappingNotFoundByOperationId = "OBP-36005: Endpoint Mapping not found. Please specify valid values for OPERATION_ID. "
  val InvalidEndpointMapping = "OBP-36006: Invalid Endpoint Mapping. "
  // General Resource related messages above here

  // User Invitation
  val CannotCreateUserInvitation = "OBP-37081: Cannot create user invitation."
  val CannotGetUserInvitation = "OBP-37882: Cannot get user invitation."
  val CannotFindUserInvitation = "OBP-37883: Cannot find user invitation."

  // ABAC Rule related messages (OBP-38XXX)
  val AbacRuleValidationFailed = "OBP-38001: ABAC rule validation failed. The rule code could not be validated."
  val AbacRuleCompilationFailed = "OBP-38002: ABAC rule compilation failed. The rule code contains syntax errors or invalid Scala code."
  val AbacRuleTypeMismatch = "OBP-38003: ABAC rule type mismatch. The rule code must return a Boolean value but returns a different type."
  val AbacRuleSyntaxError = "OBP-38004: ABAC rule syntax error. The rule code contains invalid syntax."
  val AbacRuleFieldReferenceError = "OBP-38005: ABAC rule field reference error. The rule code references fields or objects that do not exist."
  val AbacRuleCodeEmpty = "OBP-38006: ABAC rule code must not be empty."
  val AbacRuleNotFound = "OBP-38007: ABAC rule not found. Please specify a valid value for ABAC_RULE_ID."
  val AbacRuleNotActive = "OBP-38008: ABAC rule is not active."
  val AbacRuleExecutionFailed = "OBP-38009: ABAC rule execution failed. An error occurred while executing the rule."
  val AbacRuleTooPermissive = "OBP-38010: ABAC rule is too permissive. The rule code contains a tautological expression (e.g. 'true', '1==1') that would always grant access. Please write a rule that checks specific attributes."
  val AbacRuleStatisticallyTooPermissive = "OBP-38011: ABAC rule is statistically too permissive. When evaluated against a sample of system users with no resource context, the rule grants access to more than 50% of users. Please write a more selective rule that checks specific attributes."

  // Chat / Messaging related messages (OBP-39XXX)
  val ChatRoomNotFound = "OBP-39001: Chat Room not found. Please specify a valid value for CHAT_ROOM_ID."
  val ChatRoomAlreadyExists = "OBP-39002: Chat Room with this name already exists in this bank."
  val ChatRoomIsArchived = "OBP-39003: Chat Room is archived. No new messages or participants can be added."
  val NotChatRoomParticipant = "OBP-39004: Current user is not a participant of this Chat Room."
  val ChatMessageNotFound = "OBP-39005: Chat Message not found. Please specify a valid value for CHAT_MESSAGE_ID."
  val ChatRoomParticipantAlreadyExists = "OBP-39006: User is already a participant of this Chat Room."
  val ChatRoomParticipantNotFound = "OBP-39007: Participant not found in this Chat Room."
  val InsufficientChatPermission = "OBP-39008: You do not have the required permission for this Chat Room action."
  val CannotEditOthersMessage = "OBP-39009: You can only edit your own messages."
  val CannotDeleteMessage = "OBP-39010: You do not have permission to delete this message."
  val InvalidJoiningKey = "OBP-39011: Invalid joining key. The key may have been refreshed."
  val ReactionAlreadyExists = "OBP-39012: You have already added this reaction to this message."
  val ReactionNotFound = "OBP-39013: Reaction not found."
  val MustSpecifyUserIdOrConsumerId = "OBP-39014: Must specify either user_id or consumer_id, but not both."
  val ChatMessageLinkHostNotAllowed = "OBP-39015: Chat message contains a link to a host that is not allowed on this instance."
  val ChatMessageTooLong = "OBP-39016: Chat message content exceeds the maximum allowed length."
  val ChatMentionedUserNotParticipant = "OBP-39017: One or more mentioned users are not participants of this Chat Room."
  val ChatMessageTypeNotAllowed = "OBP-39018: Invalid message_type. Allowed values: text, system."
  val SignalMessageTooLong = "OBP-39019: Signal message exceeds the maximum allowed length."
  val SignalMessageContainsDangerousCharacters = "OBP-39020: Signal message contains control or bidirectional-override characters, which are not allowed."
  val SignalChannelNotFound = "OBP-39021: Signal Channel not found."

  // Transaction Request related messages (OBP-40XXX)
  val InvalidTransactionRequestType = "OBP-40001: Invalid value for TRANSACTION_REQUEST_TYPE"
  val InsufficientAuthorisationToCreateTransactionRequest  = "OBP-40002: Insufficient authorisation to create TransactionRequest. " +
    "The Transaction Request could not be created " +
    "because the login user doesn't have access to the view of the from account " +
    "or the consumer doesn't have the access to the view of the from account " +
    s"or the login user does not have the `${CanCreateAnyTransactionRequest.toString()}` role " +
    s"or the view does not have the permission can_add_transaction_request_to_any_account " +
    s"or the view does not have the permission can_add_transaction_request_to_beneficiary."
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
  val InvalidChallengeAnswer = s"OBP-40016: Invalid Challenge Answer. Please specify a valid value for answer in Json body. " +
    s"The challenge answer may be expired." +
    s"Or you've used up your allowed attempts." +
    "Or if connector = mapped and transactionRequestType_OTP_INSTRUCTION_TRANSPORT = DUMMY and suggested_default_sca_method=DUMMY, the answer must be `123`. " +
    "Or if connector = others, the challenge answer can be got by phone message or other security ways."
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
  val ConnectorMethodBodyCompileFail = "OBP-40038: ConnectorMethod methodBody is not valid Scala code, compilation failed. "
  val DynamicResourceDocAlreadyExists = "OBP-40039: DynamicResourceDoc already exists. "
  val DynamicResourceDocNotFound = "OBP-40040: DynamicResourceDoc not found, please specify valid DYNAMIC_RESOURCE_DOC_ID. "
  val DynamicResourceDocDeleteError = "OBP-40041: DynamicResourceDoc could not be deleted. "

  val DynamicMessageDocAlreadyExists = "OBP-40042: DynamicMessageDoc already exists. "
  val DynamicMessageDocNotFound = "OBP-40043: DynamicMessageDoc not found, please specify valid DYNAMIC_MESSAGE_DOC_ID. "
  val DynamicMessageDocDeleteError = "OBP-40044: DynamicMessageDoc could not be deleted. "
  val DynamicCodeCompileFail = "OBP-40045: The code to compile is not valid Scala code, compilation failed. "

  val DynamicResourceDocMethodDependency = "OBP-40046: DynamicResourceDoc method calls a forbidden method. "
  val DynamicResourceDocMethodPermission = "OBP-40047: DynamicResourceDoc method does not have sufficient permissions. "
  val DynamicCodeLangNotSupport = "OBP-40049: This language of dynamic code is not supported. "

  val InvalidOperationId = "OBP-40048: Invalid operation_id, please specify valid operation_id."
  val TransactionRequestLockFailed = "OBP-40050: Could not acquire a lock on the Transaction Request. Please try again."
  val OpenCorridorPromiseTypeMismatch = "OBP-40051: The Transaction Request is not of type OPEN_CORRIDOR_PROMISE."
  val OpenCorridorPromiseNotPending = "OBP-40052: The Open Corridor promise Transaction Request is not in PENDING status."
  val OpenCorridorPromiseEvidenceConflict = "OBP-40053: Open Corridor promise evidence is already attached to this Transaction Request with different values. Evidence cannot be overwritten."
  val AmqpBankBrokerNotConfigured = "OBP-40054: No AMQP broker is configured for this bank. Register the bank's AMQP coordinates first."
  val OpenCorridorPublishFailed = "OBP-40055: Could not publish the Open Corridor message to the bank's broker or no reply arrived in time."
  val OpenCorridorSettlementAddressMissing = "OBP-40056: The creditor bank has no settlement address registered in its Open Corridor broker registration, so the settlement instruction cannot be addressed."
  val OpenCorridorDisabled = "OBP-40057: Open Corridor is not enabled on this API instance. Set open_corridor_enabled=true in the props."
  val OpenCorridorSettlementNotFound = "OBP-40058: No Open Corridor settlement with this SETTLEMENT_ID exists for this bank."
  val OpenCorridorSameBankNotAllowed = "OBP-40061: OPEN_CORRIDOR is inter-bank: the beneficiary bank must differ from the sending bank. Use an ordinary payment for intra-bank transfers."
  val MessageOutboxRowNotFound = "OBP-40059: No message outbox row with this OUTBOX_ID exists."
  val MessageOutboxRowNotSticky = "OBP-40060: The message outbox row is not STICKY. Only STICKY rows can be re-queued; PENDING rows retry automatically."
  // Renumbered from OBP-40058 on merge: develop had meanwhile taken 40058 through 40061 for Open
  // Corridor settlements and the message outbox. An error number is part of the API's contract, so
  // the two cannot share one -- DuplicatedMessages enforces that -- and the Open Corridor block is
  // the published side. Both call sites name the constant rather than the literal, so nothing but
  // the number moves.
  val PaymentNotInitiatedByCaller = "OBP-40062: The addressed payment was not initiated by you. "
  // Exceptions (OBP-50XXX)
  val UnknownError = "OBP-50000: Unknown Error."
  val FutureTimeoutException = "OBP-50001: Future Timeout Exception."
  val AdapterOrCoreBankingSystemException = "OBP-50003: Adapter Or Core Banking System Exception. Failed to get a valid response from the south side Adapter or Core Banking System."
  // This error may not be shown to user, just for debugging.
  val CurrentUserNotFoundException = "OBP-50004: Method (AuthUser.getCurrentUser) can not find the current user in the current context!"
  val AnUnspecifiedOrInternalErrorOccurred = "OBP-50005: An unspecified or internal error occurred."
  val ScalaEmptyBoxToLiftweb = "OBP-50010: Scala return Empty box to Liftweb."
  val NoCallContext = "OBP-50012: Can not get the CallContext object here."
  val UnspecifiedCbsError = "OBP-50013: The Core Banking System returned an unspecified error or response."
  val RefreshUserError = "OBP-50014: Can not refresh User."
  val InternalServerError = "OBP-50015: The server encountered an unexpected condition which prevented it from fulfilling the request."
  val NotAllowedEndpoint = "OBP-50017: The endpoint is not enabled at this OBP API instance."
  val UnderConstructionError = "OBP-50018: Under Construction Error."
  val DatabaseConnectionClosedError = "OBP-50019: Cannot connect to the OBP database."
  val DynamicCodeExecutionDisabled = "OBP-50020: User-generated dynamic code execution is disabled on this API instance. Set allow_user_generated_scala_code=true to enable."
  val DynamicCodeExecutionUnsandboxed = "OBP-50021: User-generated dynamic code execution is enabled, but this JVM cannot enforce the sandbox (SecurityManager was removed in JDK 24, JEP 486), so dynamic code runs with unrestricted file, network and reflection access. Set allow_user_generated_scala_code_without_sandbox=true to accept that risk explicitly, or run on a JVM where the sandbox can be installed."


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
  val InvalidConnectorResponseForGetCheckbookOrdersFuture = "OBP-50211: Connector did not return the set of check book."
  val InvalidConnectorResponseForGetStatusOfCreditCardOrderFuture = "OBP-50212: Connector did not return the set of status of credit card."
  val InvalidConnectorResponseForCreateTransactionAfterChallengev300 = "OBP-50213: The Connector did not return a valid response for payments."
  val InvalidConnectorResponseForMissingRequiredValues = "OBP-50214: Connector return the data, but the data has missing required values."
  val InvalidConnectorResponseForCreateChallenge = "OBP-50215: Connector did not return the set of challenge we requested."
  val InvalidConnectorResponseForSaveDoubleEntryBookTransaction = "OBP-50216: The Connector did not return a valid response for saving double-entry transaction."
  val InvalidConnectorResponseForCancelPayment = "OBP-50217: Connector did not return the transaction we requested."
  val InvalidConnectorResponseForGetEndpointTags = "OBP-50218: Connector did not return the set of endpoint tags we requested."
  val InvalidConnectorResponseForGetBankAccountsWithAttributes = "OBP-50219: Connector did not return the bank accounts we requested."
  val InvalidConnectorResponseForGetPaymentLimit = "OBP-50220: Connector did not return the payment limit we requested."
  val InvalidConnectorResponseForCreateTransactionRequestBGV1 = "OBP-50221: CreateTransactionRequestBGV1 Connector did not return the data we requested."
  val InvalidConnectorResponseForGetStatus = "OBP-50222: Connector method getStatus did not return the data we requested."
  val InvalidConnectorResponseForGetAccountDirectory = "OBP-50223: Connector did not return the account directory we requested."

  // Adapter Exceptions (OBP-6XXXX)
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
  val MethodRoutingAlreadyExistsError = "OBP-70003: Method Routing is already exists."

  // Trading Exceptions (OBP-71XXX)
  val OfferNotFound = "OBP-71001: Trading offer not found."
  val InvalidOfferType = "OBP-71002: Invalid offer type. Must be 'BUY' or 'SELL'."
  val InvalidTradingAmount = "OBP-71003: Invalid amount. Must be a positive number."
  val CreateTradingOfferError = "OBP-71005: Could not create trading offer."

  // Market Trading Exceptions (OBP-72XXX)
  val OrderNotFound = "OBP-72001: Market order not found."
  val InvalidOrderSide = "OBP-72002: Invalid order side. Must be 'BUY' or 'SELL'."
  val TradeNotFound = "OBP-72003: Market trade not found."
  val InvalidMatchParameters = "OBP-72004: Invalid match parameters."
  val SettlementFailed = "OBP-72005: Settlement request failed."
  val WithdrawalFailed = "OBP-72006: Withdrawal request failed."

  // TCC Payment Authorization Exceptions (OBP-73XXX)
  val PaymentAuthNotFound = "OBP-73001: Payment authorization not found."
  val InvalidPaymentAuthState = "OBP-73002: Invalid payment authorization state transition."
  val PaymentAuthAlreadyCaptured = "OBP-73003: Payment authorization has already been captured."
  val PaymentAuthAlreadyReleased = "OBP-73004: Payment authorization has already been released."
  val CreatePaymentAuthError = "OBP-73005: Could not create payment authorization."

  // Cascade Deletion Exceptions (OBP-8XXXX)
  val CouldNotDeleteCascade = "OBP-80001: Could not delete cascade."
  val CannotDeleteCascadePersonalEntity = "OBP-80002: Cannot delete cascade for personal entities (hasPersonalEntity=true). Please delete the records and definition separately."

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
    AuthenticatedUserIsRequired -> 401,
    DirectLoginInvalidToken -> 401,
    InvalidLoginCredentials -> 401,
    UserNotFoundById -> 404,
    UserHasMissingRoles -> 403, // or 400
    InvalidConsumerKey -> 401,
//    InvalidConsumerCredentials -> 401, // or 400
    UsernameHasBeenLocked -> 401,
    UserEmailNotValidated -> 401,
    UserNoPermissionAccessView -> 403,
    UserLacksPermissionCanGrantAccessToViewForTargetAccount -> 403,
    UserLacksPermissionCanRevokeAccessToViewForTargetAccount -> 403,
    UserNotSuperAdminOrMissRole -> 403,
    ConsumerHasMissingRoles -> 403,
    UserNotFoundByProviderAndUsername -> 404,
    UserNotFoundByToken -> 404,
    UserAlreadyValidated -> 404,
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
    OBPSchemeIsImplicit -> 409,
    DoubleEntryTransactionNotFound -> 404,
    MeetingApiKeyNotConfigured -> 403,
    MeetingApiSecretNotConfigured -> 403,
    EntitlementNotFound -> 404,
    EntitlementCannotBeDeleted -> 500,
    ConsentStatusIssue -> 401,
    ConsentDisabled -> 401,
    // 403 not 401: the token authenticated fine, it just isn't bound to a consent (UK OB: insufficient authorisation)
    ConsentIdClaimMissing -> 403,
    // 403: authenticated + a real consent, but it belongs to another API standard — forbidden here.
    ConsentDoesNotMatchStandard -> 403,
    InternalServerError -> 500,
  )

  /**
   * get response statusCode by error message, return 400 if error message not exists or have not mapping statusCode
   * @param errorMsg
   * @return response statusCode, default is 400
   */
  def getCode(errorMsg: String): Int = errorToCode.get(errorMsg).getOrElse(400)

  /**
   * Resolve HTTP status code for an OBP-prefixed error message that may have a
   * runtime suffix appended (e.g. "OBP-20020: User does not have access to the view.
   * Current ViewId is owner"). Extracts the OBP-XXXXX prefix and returns the
   * canonical status code from errorToCode, but ONLY when the canonical code is
   * one of the auth-overrides Lift's `errorJsonResponse` performs (401, 403, 408,
   * 429). For other codes (e.g. 404 BankNotFound) the default failCode the caller
   * supplied wins — callers that want 404 must request it explicitly via
   * `unboxFullOrFail(..., emptyBoxErrorCode = 404)` or similar, matching Lift's
   * intent that 400 is a deliberate "validation failure" code.
   */
  def getCodeByOBPPrefix(errorMsg: String): Int = {
    val prefixOpt = "OBP-\\d{5}".r.findFirstIn(errorMsg)
    prefixOpt.flatMap { prefix =>
      errorToCode.find { case (key, _) => key.startsWith(prefix + ":") }.map(_._2)
    }.filter(Set(401, 403, 408, 429).contains).getOrElse(400)
  }

  /****** special error message, start with $, mark as do validation according ResourceDoc errorResponseBodies *****/
  /**
   * validate method: APIUtil.authorizedAccess
   */
  def $AuthenticatedUserIsRequired = AuthenticatedUserIsRequired

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

  /**
   *  validate method: NewStyle.function.getCounterpartyByCounterpartyId
   */
  def $CounterpartyNotFoundByCounterpartyId = CounterpartyNotFoundByCounterpartyId


  def getDuplicatedMessageNumbers = {
    import scala.meta._
    val source: Source = new java.io.File("src/main/scala/code/api/util/ErrorMessages.scala").parse[Source].get

    // scalameta 4.13.6 (_3) moved Tree#collect from a plain method to the standalone
    // scala.meta.contrib.TreeOps.collect function - the extension available via
    // scala.meta.contrib._ only provides collectFirst/descendants/ancestors, not collect.
    import scala.meta.contrib.TreeOps
    val listOfMessaegeNumbers = TreeOps.collect(source) {
      case obj: Defn.Object if obj.name.value == "ErrorMessages" =>
        TreeOps.collect(obj) {
          case v: Defn.Val if v.rhs.syntax.startsWith(""""OBP-""") =>
            val messageNumber = v.rhs.syntax.split(":")
            messageNumber(0)
        }
    }
    val list = listOfMessaegeNumbers.flatten
    val duplicatedMessageNumbers = list
      .groupBy(x => x).map { case (n, occurrences) => n -> occurrences.length } // Compute the number of occurrences of each message number
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

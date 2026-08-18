package code.api.v6_0_0

/**
 * All v6.0.0 Lift endpoints have been migrated to Http4s600.
 * This stub is kept so that existing test files that import
 * APIMethods600.Implementations6_0_0 continue to compile.
 */
object APIMethods600 {
  val Implementations6_0_0 = Http4s600.Implementations6_0_0
}

trait APIMethods600

// ─── Original Lift implementation (commented out) ────────────────────────────
//package code.api.v6_0_0
//
//import scala.language.reflectiveCalls
//import code.accountattribute.AccountAttributeX
//import code.api.Constant._
//import code.api.{Constant, DirectLogin, JsonResponseException, ObpApiFailure}
//import code.api.dynamic.endpoint.helper.CompiledObjects
//import code.dynamicResourceDoc.JsonDynamicResourceDoc
//import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
//import code.api.cache.{Caching, Redis, RedisMessaging}
//import code.api.util.APIUtil._
//import code.api.util.ApiRole
//import code.api.util.ApiRole._
//import code.api.util.ApiTag._
//import code.api.util.ErrorMessages.{$AuthenticatedUserIsRequired, InvalidDateFormat, InvalidJsonFormat, UnknownError, DynamicEntityOperationNotAllowed, _}
//import code.api.util.FutureUtil.EndpointContext
//import code.api.util.{CertificateUtil, Glossary}
//import code.api.util.JsonSchemaGenerator
//import code.api.util.NewStyle.HttpCode
//import code.api.util.{APIUtil, ApiVersionUtils, CallContext, DiagnosticDynamicEntityCheck, ErrorMessages, NewStyle, OBPLimit, OBPOffset, OBPSortBy, RateLimitingUtil}
//import com.openbankproject.commons.util.json
//import code.api.util.NewStyle.function.extractQueryParams
//import code.api.util.newstyle.ViewNewStyle
//import code.api.v3_0_0.JSONFactory300
//import code.api.v3_0_0.JSONFactory300.createAggregateMetricJson
//import code.api.v2_0_0.{BasicViewJson, JSONFactory200}
//import code.api.v3_1_0.{JSONFactory310, PostCustomerNumberJsonV310}
//import code.api.v1_2_1.{AccountHolderJSON, BankRoutingJsonV121, TransactionDetailsJSON}
//import code.api.v4_0_0.{BankAttributeBankResponseJsonV400, CallLimitPostJsonV400}
//import code.api.v4_0_0.JSONFactory400.createCallsLimitJson
//import code.api.v5_0_0.JSONFactory500
//import code.api.v5_0_0.{ViewJsonV500, ViewsJsonV500}
//import code.api.v5_1_0.{JSONFactory510, PostCustomerLegalNameJsonV510}
//import code.api.dynamic.entity.helper.{DynamicEntityHelper, DynamicEntityInfo}
//import code.api.v6_0_0.JSONFactory600.{AddUserToGroupResponseJsonV600, CleanupOrphanedDynamicEntityResponseJsonV600, DynamicEntityDiagnosticsJsonV600, DynamicEntityIssueJsonV600, OrphanedDynamicEntityJsonV600, GroupEntitlementJsonV600, GroupEntitlementsJsonV600, GroupJsonV600, GroupsJsonV600, ModeratedAccountJSON600, PostGroupJsonV600, PostGroupMembershipJsonV600, PostResetPasswordUrlJsonV600, PostResetPasswordUrlAnonymousJsonV600, PostResetPasswordCompleteJsonV600, PutGroupJsonV600, ReferenceTypeJsonV600, ReferenceTypesJsonV600, ResetPasswordUrlJsonV600, ResetPasswordUrlAnonymousResponseJsonV600, ResetPasswordCompleteResponseJsonV600, RoleWithEntitlementCountJsonV600, RolesWithEntitlementCountsJsonV600, ScannedApiVersionJsonV600, UpdateViewJsonV600, UserGroupMembershipJsonV600, UserGroupMembershipsJsonV600, UserWithViewAccessJsonV600, UsersWithViewAccessJsonV600, ValidateUserEmailJsonV600, ValidateUserEmailResponseJsonV600, ViewJsonV600, ViewPermissionJsonV600, ViewPermissionsJsonV600, ViewsJsonV600, createAbacRuleJsonV600, createAbacRulesJsonV600, createActiveRateLimitsJsonV600, createActiveRateLimitsJsonV600FromCallLimit, createBankAccountJSON600, createCallLimitJsonV600, createConsumerJsonV600, createRedisCallCountersJson, createFeaturedApiCollectionJsonV600, createFeaturedApiCollectionsJsonV600}
//import code.metadata.tags.Tags
//import code.products.ProductTagsProvider
//import code.api.v6_0_0.OBPAPI6_0_0
//import code.abacrule.{AbacRuleEngine, MappedAbacRuleProvider}
//import code.mandate.{MappedMandateProvider}
//import code.api.v6_0_0.JSONFactory600.{createMandateJsonV600, createMandatesJsonV600, createMandateProvisionJsonV600, createMandateProvisionsJsonV600, createSignatoryPanelJsonV600, createSignatoryPanelsJsonV600, createCounterpartyAttributeJson, createCounterpartyAttributesJson}
//// Chat case classes are at package level in JSONFactory6.0.0.scala, not inside JSONFactory600 object
//import code.metrics.{APIMetrics, ConnectorCountsRedis, ConnectorTraceProvider}
//import code.bankconnectors.{Connector, LocalMappedConnectorInternal}
//import code.bankconnectors.storedprocedure.StoredProcedureUtils
//import code.bankconnectors.LocalMappedConnectorInternal._
//import code.consumer.Consumers
//import code.entitlement.Entitlement
//import code.loginattempts.LoginAttempt
//import code.model._
//import code.users.{UserAgreement, UserAgreementProvider, Users}
//import code.ratelimiting.RateLimitingDI
//import code.util.Helper
//import code.util.Helper.{MdcLoggable, ObpS, SILENCE_IS_GOLDEN}
//import code.views.Views
//import code.views.system.{AccountAccess, ViewDefinition}
//import code.webuiprops.{MappedWebUiPropsProvider, WebUiPropsCommons, WebUiPropsPutJsonV600}
//import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider, DynamicEntityT}
//import code.DynamicData.{DynamicData, DynamicDataProvider}
//import com.github.dwickern.macros.NameOf.nameOf
//import com.openbankproject.commons.ExecutionContext.Implicits.global
//import com.openbankproject.commons.dto.GetProductsParam
//import com.openbankproject.commons.model._
//import com.openbankproject.commons.model.enums.CounterpartyAttributeType
//import com.openbankproject.commons.model.enums.DynamicEntityOperation._
//import com.openbankproject.commons.model.enums.UserAttributeType
//import code.api.util.newstyle.CounterpartyAttributeNewStyle
//import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
//import net.liftweb.common.{Box, Empty, Failure, Full}
//import net.liftweb.util.Helpers.tryo
//import org.apache.commons.lang3.StringUtils
//import org.json4s.{Extraction, JsonParser}
//import org.json4s.JsonAST.{JArray, JField, JNothing, JObject, JString, JValue}
//import org.json4s.JsonDSL._
//import net.liftweb.mapper.{By, Descending, MaxRows, NullRef, OrderBy}
//import code.api.util.ExampleValue
//import code.api.util.ExampleValue.dynamicEntityResponseBodyExample
//import net.liftweb.common.Box
//
//import java.net.URLDecoder
//import java.nio.charset.StandardCharsets
//import java.text.SimpleDateFormat
//import java.util.UUID.randomUUID
//import scala.collection.immutable.{List, Nil}
//import scala.collection.mutable.ArrayBuffer
//import scala.concurrent.Future
//import scala.concurrent.duration._
//import scala.collection.JavaConverters._
//import scala.util.Random
//
//
//trait APIMethods600 {
//  self: RestHelper =>
//
//  val Implementations6_0_0 = new Implementations600()
//
//  class Implementations600 extends RestHelper with MdcLoggable {
//
//    val implementedInApiVersion: ScannedApiVersion = ApiVersion.v6_0_0
//
//    val staticResourceDocs = ArrayBuffer[ResourceDoc]()
//    val resourceDocs = staticResourceDocs
//
//    val apiRelations = ArrayBuffer[ApiRelation]()
//    val codeContext = CodeContext(staticResourceDocs, apiRelations)
//
//
//    staticResourceDocs += ResourceDoc(
//      root,
//      implementedInApiVersion,
//      nameOf(root),
//      "GET",
//      "/root",
//      "Get API Info (root)",
//      """Returns information about:
//        |
//        |* API version
//        |* Hosted by information
//        |* Hosted at information
//        |* Energy source information
//        |* Git Commit""",
//      EmptyBody,
//      apiInfoJson400,
//      List(UnknownError, MandatoryPropertyIsNotSet),
//      apiTagApi  :: Nil)
//
//    lazy val root: OBPEndpoint = {
//      case (Nil | "root" :: Nil) JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            _ <- Future(()) // Just start async call
//          } yield {
//            (JSONFactory510.getApiInfoJSON(OBPAPI6_0_0.version, OBPAPI6_0_0.versionStatus), HttpCode.`200`(cc.callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getFeatures,
//      implementedInApiVersion,
//      nameOf(getFeatures),
//      "GET",
//      "/features",
//      "Get Features",
//      """Returns information about the features enabled on this OBP instance.
//        |
//        |No Authentication is Required.""",
//      EmptyBody,
//      featuresJsonV600,
//      List(UnknownError),
//      apiTagApi :: Nil)
//
//    lazy val getFeatures: OBPEndpoint = {
//      case "features" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            _ <- Future(())
//          } yield {
//            val featuresJson = FeaturesJsonV600(
//              allow_public_views = APIUtil.getPropsAsBoolValue("allow_public_views", false),
//              allow_abac_account_access = APIUtil.getPropsAsBoolValue("allow_abac_account_access", false),
//              allow_account_firehose = APIUtil.getPropsAsBoolValue("allow_account_firehose", false),
//              allow_customer_firehose = APIUtil.getPropsAsBoolValue("allow_customer_firehose", false),
//              allow_direct_login = APIUtil.getPropsAsBoolValue("allow_direct_login", true),
//              allow_gateway_login = APIUtil.getPropsAsBoolValue("allow_gateway_login", false),
//              allow_oauth2_login = APIUtil.getPropsAsBoolValue("allow_oauth2_login", true),
//              allow_dauth = APIUtil.getPropsAsBoolValue("allow_dauth", false),
//              allow_sandbox_account_creation = APIUtil.getPropsAsBoolValue("allow_sandbox_account_creation", false),
//              allow_sandbox_data_import = APIUtil.getPropsAsBoolValue("allow_sandbox_data_import", false),
//              allow_account_deletion = APIUtil.getPropsAsBoolValue("allow_account_deletion", false),
//              allow_just_in_time_entitlements = APIUtil.getPropsAsBoolValue("create_just_in_time_entitlements", false)
//            )
//            (featuresJson, HttpCode.`200`(cc.callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createTransactionRequestHold,
//      implementedInApiVersion,
//      nameOf(createTransactionRequestHold),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/HOLD/transaction-requests",
//      "Create Transaction Request (HOLD)",
//      s"""
//         |
//         |Create a transaction request to move funds from the account to its Holding Account.
//         |If the Holding Account does not exist, it will be created automatically.
//         |
//         |${transactionRequestGeneralText}
//         |
//       """.stripMargin,
//      transactionRequestBodyHoldJsonV600,
//      transactionRequestWithChargeJSON400,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        $BankAccountNotFound,
//        InsufficientAuthorisationToCreateTransactionRequest,
//        InvalidTransactionRequestType,
//        InvalidJsonFormat,
//        NotPositiveAmount,
//        InvalidTransactionRequestCurrency,
//        TransactionDisabled,
//        UnknownError
//      ),
//      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
//    )
//
//    lazy val createTransactionRequestHold: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
//        "HOLD" :: "transaction-requests" :: Nil JsonPost json -> _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          val transactionRequestType = TransactionRequestType("HOLD")
//          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
//    }
//
//    // --- GET Holding Account by Parent ---
//    staticResourceDocs += ResourceDoc(
//      getHoldingAccountByReleaser,
//      implementedInApiVersion,
//      nameOf(getHoldingAccountByReleaser),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/holding-accounts",
//      "Get Holding Accounts By Releaser",
//      s"""
//         |
//         |Return the first Holding Account linked to the given releaser account via account attribute `RELEASER_ACCOUNT_ID`.
//         |Response is wrapped in a list and includes account attributes.
//         |
//       """.stripMargin,
//      EmptyBody,
//      moderatedCoreAccountsJsonV300,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        $BankAccountNotFound,
//        $UserNoPermissionAccessView,
//        UnknownError
//      ),
//      List(apiTagAccount)
//    )
//
//    lazy val getHoldingAccountByReleaser: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "holding-accounts" :: Nil JsonGet _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (user @Full(u), _, _, view, callContext) <- SS.userBankAccountView
//            // Find accounts by attribute RELEASER_ACCOUNT_ID
//            (accountIdsBox, callContext) <- AccountAttributeX.accountAttributeProvider.vend.getAccountIdsByParams(bankId, Map("RELEASER_ACCOUNT_ID" -> List(accountId.value))) map { ids => (ids, callContext) }
//            accountIds = accountIdsBox.getOrElse(Nil)
//            // load the first holding account
//            holdingOpt <- {
//              def firstHolding(ids: List[String]): Future[Option[BankAccount]] = ids match {
//                case Nil => Future.successful(None)
//                case id :: tail =>
//                  NewStyle.function.getBankAccount(bankId, AccountId(id), callContext).flatMap { case (acc, cc) =>
//                    if (acc.accountType == "HOLDING") Future.successful(Some(acc)) else firstHolding(tail)
//                  }
//              }
//              firstHolding(accountIds)
//            }
//            holding <- NewStyle.function.tryons($BankAccountNotFound, 404, callContext) { holdingOpt.get }
//            moderatedAccount <- Future { holding.moderatedBankAccount(view, BankIdAccountId(holding.bankId, holding.accountId), user, callContext) } map {
//              x => unboxFullOrFail(x, callContext, UnknownError)
//            }
//            (attributes, callContext) <- NewStyle.function.getAccountAttributesByAccount(bankId, holding.accountId, callContext)
//          } yield {
//            val accountsJson = JSONFactory300.createFirehoseCoreBankAccountJSON(List(moderatedAccount), Some(attributes))
//            (accountsJson, HttpCode.`200`(callContext))
//          }
//    }
//
//    // --- GET Accounts at Bank (v6.0.0 with account_id) ---
//    staticResourceDocs += ResourceDoc(
//      getAccountsAtBank,
//      implementedInApiVersion,
//      nameOf(getAccountsAtBank),
//      "GET",
//      "/banks/BANK_ID/accounts",
//      "Get Accounts at Bank",
//      s"""
//         |Returns the list of accounts at BANK_ID that the user has access to.
//         |For each account the API returns the account ID and the views available to the user.
//         |Each account must have at least one private View.
//         |
//         |This v6.0.0 version returns `account_id` instead of `id` for consistency with other v6.0.0 endpoints.
//         |
//         |Optional request parameters for filtering with attributes:
//         |URL params example: /banks/some-bank-id/accounts?limit=50&offset=1
//         |
//         |${userAuthenticationMessage(true)}
//         |
//       """.stripMargin,
//      EmptyBody,
//      BasicAccountsJsonV600(List(BasicAccountJsonV600(
//        account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
//        bank_id = "gh.29.uk",
//        label = "My Account",
//        views_available = List(BasicViewJson("owner", "Owner", false))
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagAccount, apiTagPrivateData, apiTagPublicData)
//    )
//
//    lazy val getAccountsAtBank: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: Nil JsonGet req => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          (Full(u), bank, callContext) <- SS.userBank
//          (privateViewsUserCanAccessAtOneBank, privateAccountAccess) <- Future {
//            Views.views.vend.privateViewsUserCanAccessAtBank(u, bankId)
//          }
//          params <- Future {
//            req.params
//              .filterNot(_._1 == PARAM_TIMESTAMP)
//              .filterNot(_._1 == PARAM_LOCALE)
//          }
//          privateAccountAccess2 <-
//            if (params.isEmpty || privateAccountAccess.isEmpty) {
//              Future.successful(privateAccountAccess)
//            } else {
//              AccountAttributeX.accountAttributeProvider.vend
//                .getAccountIdsByParams(bankId, params)
//                .map { boxedAccountIds =>
//                  val accountIds = boxedAccountIds.getOrElse(Nil)
//                  privateAccountAccess.filter(aa =>
//                    accountIds.contains(aa.account_id.get)
//                  )
//                }
//            }
//          (availablePrivateAccounts, callContext2) <- bank.privateAccountsFuture(
//            privateAccountAccess2,
//            callContext
//          )
//        } yield {
//          val accountsJson = availablePrivateAccounts.map { account =>
//            val viewsAvailable = privateViewsUserCanAccessAtOneBank
//              .filter(v => v.bankId == bankId && v.accountId == account.accountId)
//              .map(v => BasicViewJson(v.viewId.value, v.name, v.isPublic))
//            JSONFactory600.createBasicAccountJsonV600(account, viewsAvailable)
//          }
//          (BasicAccountsJsonV600(accountsJson), HttpCode.`200`(callContext2))
//        }
//      }
//    }
//
//    // --- GET Account by Id (Core) (v6.0.0 with account_id) ---
//    staticResourceDocs += ResourceDoc(
//      getCoreAccountByIdV600,
//      implementedInApiVersion,
//      nameOf(getCoreAccountByIdV600),
//      "GET",
//      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/account",
//      "Get Account by Id (Core)",
//      s"""Information returned about the account specified by ACCOUNT_ID:
//         |
//         |* Number - The human readable account number given by the bank that identifies the account.
//         |* Label - A label given by the owner of the account
//         |* Owners - Users that own this account
//         |* Type - The type of account
//         |* Balance - Currency and Value
//         |* Account Routings - A list that might include IBAN or national account identifiers
//         |* Account Rules - A list that might include Overdraft and other bank specific rules
//         |* Tags - A list of Tags assigned to this account
//         |
//         |This call returns the owner view and requires access to that view.
//         |
//         |This v6.0.0 version returns `account_id` instead of `id` for consistency with other v6.0.0 endpoints.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ModeratedCoreAccountJsonV600(
//        account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
//        bank_id = "gh.29.uk",
//        label = "My Account",
//        number = "123456",
//        product_code = "CURRENT",
//        balance = AmountOfMoneyJsonV121("EUR", "1000.00"),
//        account_routings = List(AccountRoutingJsonV121("IBAN", "DE89370400440532013000")),
//        views_basic = List("owner")
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankAccountNotFound,
//        UnknownError
//      ),
//      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil
//    )
//
//    lazy val getCoreAccountByIdV600: OBPEndpoint = {
//      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account" :: Nil JsonGet req => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          (user @ Full(u), account, callContext) <- SS.userAccount
//          view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(
//            u,
//            BankIdAccountId(account.bankId, account.accountId),
//            callContext
//          )
//          moderatedAccount <- NewStyle.function.moderatedBankAccountCore(
//            account,
//            view,
//            user,
//            callContext
//          )
//        } yield {
//          val availableViews: List[View] =
//            Views.views.vend.privateViewsUserCanAccessForAccount(
//              u,
//              BankIdAccountId(account.bankId, account.accountId)
//            )
//          (
//            JSONFactory600.createModeratedCoreAccountJsonV600(moderatedAccount, availableViews),
//            HttpCode.`200`(callContext)
//          )
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getConsumerCallCounters,
//      implementedInApiVersion,
//      nameOf(getConsumerCallCounters),
//      "GET",
//      "/management/consumers/CONSUMER_ID/call-counters",
//      "Get Call Counts for Consumer",
//      s"""
//         |Get the call counters (current usage) for a specific consumer. Shows how many API calls have been made and when the counters reset.
//         |
//         |This endpoint returns the current state of API rate limits across all time periods (per second, per minute, per hour, per day, per week, per month).
//         |
//         |**Response Structure:**
//         |The response always contains a consistent structure with all six time periods, regardless of whether rate limits are configured or active.
//         |
//         |Each time period contains:
//         |- `calls_made`: Number of API calls made in the current period (null if no data available)
//         |- `reset_in_seconds`: Seconds until the counter resets (null if no data available)
//         |- `status`: Current state of the rate limit for this period
//         |
//         |**Status Values:**
//         |- `ACTIVE`: Rate limit counter is active and tracking calls. Both `calls_made` and `reset_in_seconds` will have numeric values.
//         |- `NO_COUNTER`: Key does not exist - the consumer has not made any API calls in this time period yet.
//         |- `EXPIRED`: The rate limit counter has expired (TTL reached 0). The counter will be recreated on the next API call.
//         |- `REDIS_UNAVAILABLE`: Cannot retrieve data from Redis. This indicates a system connectivity issue.
//         |- `DATA_MISSING`: Unexpected error - period data is missing from the response. This should not occur under normal circumstances.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      redisCallCountersJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        InvalidConsumerId,
//        ConsumerNotFoundByConsumerId,
//        UserHasMissingRoles,
//        UpdateConsumerError,
//        UnknownError
//      ),
//      List(apiTagConsumer),
//      Some(List(canGetRateLimits)))
//
//
//    lazy val getConsumerCallCounters: OBPEndpoint = {
//      case "management" :: "consumers" :: consumerId :: "call-counters" :: Nil JsonGet _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, cc.callContext)
//            currentConsumerCallCounters <- Future(RateLimitingUtil.consumerRateLimitState(consumerId).toList)
//          } yield {
//            (createRedisCallCountersJson(currentConsumerCallCounters), HttpCode.`200`(cc.callContext))
//          }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      createCallLimits,
//      implementedInApiVersion,
//      nameOf(createCallLimits),
//      "POST",
//      "/management/consumers/CONSUMER_ID/consumer/rate-limits",
//      "Create Rate Limits for a Consumer",
//      s"""
//         |Create Rate Limits for a Consumer
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      callLimitPostJsonV600,
//      callLimitJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        InvalidConsumerId,
//        ConsumerNotFoundByConsumerId,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagConsumer),
//      Some(List(canCreateRateLimits)))
//
//
//    lazy val createCallLimits: OBPEndpoint = {
//      case "management" :: "consumers" :: consumerId :: "consumer" :: "rate-limits" :: Nil JsonPost json -> _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateRateLimits, callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitPostJsonV600 ", 400, callContext) {
//              json.extract[CallLimitPostJsonV600]
//            }
//            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
//            rateLimiting <- RateLimitingDI.rateLimiting.vend.createConsumerCallLimits(
//              consumerId,
//              postJson.from_date,
//              postJson.to_date,
//              postJson.api_version,
//              postJson.api_name,
//              postJson.bank_id,
//              Some(postJson.per_second_call_limit),
//              Some(postJson.per_minute_call_limit),
//              Some(postJson.per_hour_call_limit),
//              Some(postJson.per_day_call_limit),
//              Some(postJson.per_week_call_limit),
//              Some(postJson.per_month_call_limit)
//            )
//          } yield {
//            rateLimiting match {
//              case Full(rateLimitingObj) => (createCallLimitJsonV600(rateLimitingObj), HttpCode.`201`(callContext))
//              case _ => (UnknownError, HttpCode.`400`(callContext))
//            }
//          }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      updateRateLimits,
//      implementedInApiVersion,
//      nameOf(updateRateLimits),
//      "PUT",
//      "/management/consumers/CONSUMER_ID/consumer/rate-limits/RATE_LIMITING_ID",
//      "Set Rate Limits / Call Limits per Consumer",
//      s"""
//         |Set the API rate limits / call limits for a Consumer:
//         |
//         |Rate limiting can be set:
//         |
//         |Per Second
//         |Per Minute
//         |Per Hour
//         |Per Week
//         |Per Month
//         |
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      callLimitPostJsonV400,
//      callLimitPostJsonV400,
//      List(
//        AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        InvalidConsumerId,
//        ConsumerNotFoundByConsumerId,
//        UserHasMissingRoles,
//        UpdateConsumerError,
//        UnknownError
//      ),
//      List(apiTagConsumer, apiTagRateLimits),
//      Some(List(canUpdateRateLimits)))
//
//    lazy val updateRateLimits: OBPEndpoint = {
//      case "management" :: "consumers" :: consumerId :: "consumer" :: "rate-limits" :: rateLimitingId :: Nil JsonPut json -> _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.handleEntitlementsAndScopes("", u.userId, List(canUpdateRateLimits), callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitPostJsonV400 ", 400, callContext) {
//              json.extract[CallLimitPostJsonV400]
//            }
//            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
//            rateLimiting <- RateLimitingDI.rateLimiting.vend.updateConsumerCallLimits(
//              rateLimitingId,
//              postJson.from_date,
//              postJson.to_date,
//              postJson.api_version,
//              postJson.api_name,
//              postJson.bank_id,
//              Some(postJson.per_second_call_limit),
//              Some(postJson.per_minute_call_limit),
//              Some(postJson.per_hour_call_limit),
//              Some(postJson.per_day_call_limit),
//              Some(postJson.per_week_call_limit),
//              Some(postJson.per_month_call_limit)) map {
//              unboxFullOrFail(_, callContext, UpdateConsumerError)
//            }
//          } yield {
//            (createCallsLimitJson(rateLimiting), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      deleteCallLimits,
//      implementedInApiVersion,
//      nameOf(deleteCallLimits),
//      "DELETE",
//      "/management/consumers/CONSUMER_ID/consumer/rate-limits/RATE_LIMITING_ID",
//      "Delete Rate Limit by Rate Limiting ID",
//      s"""
//         |Delete a specific Rate Limit by Rate Limiting ID
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidConsumerId,
//        ConsumerNotFoundByConsumerId,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagConsumer),
//      Some(List(canDeleteRateLimits)))
//
//
//    lazy val deleteCallLimits: OBPEndpoint = {
//      case "management" :: "consumers" :: consumerId :: "consumer" :: "rate-limits" :: rateLimitingId :: Nil JsonDelete _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteRateLimits, callContext)
//            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
//            rateLimiting <- RateLimitingDI.rateLimiting.vend.getByRateLimitingId(rateLimitingId)
//            _ <- rateLimiting match {
//              case Full(rl) if rl.consumerId == consumerId =>
//                Future.successful(Full(rl))
//              case Full(_) =>
//                Future.successful(ObpApiFailure(s"Rate limiting ID $rateLimitingId does not belong to consumer $consumerId", 400, callContext))
//              case _ =>
//                Future.successful(ObpApiFailure(s"Rate limiting ID $rateLimitingId not found", 404, callContext))
//            }
//            deleteResult <- RateLimitingDI.rateLimiting.vend.deleteByRateLimitingId(rateLimitingId)
//          } yield {
//            deleteResult match {
//              case Full(true) => (EmptyBody, HttpCode.`204`(callContext))
//              case _ => (UnknownError, HttpCode.`400`(callContext))
//            }
//          }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      getActiveRateLimitsAtDate,
//      implementedInApiVersion,
//      nameOf(getActiveRateLimitsAtDate),
//      "GET",
//      "/management/consumers/CONSUMER_ID/active-rate-limits/DATE_WITH_HOUR",
//      "Get Active Rate Limits for Hour",
//      s"""
//         |Get the active rate limits for a consumer for a specific hour. Returns the aggregated rate limits from all active records during that hour.
//         |
//         |Rate limits are cached and queried at hour-level granularity.
//         |
//         |See ${Glossary.getGlossaryItemLink("Rate Limiting")} for more details on how rate limiting works.
//         |
//         |Date format: YYYY-MM-DD-HH in UTC timezone (e.g. 2025-12-31-13 for hour 13:00-13:59 UTC on Dec 31, 2025)
//         |
//         |Note: The hour is always interpreted in UTC for consistency across all servers.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      activeRateLimitsJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidConsumerId,
//        ConsumerNotFoundByConsumerId,
//        UserHasMissingRoles,
//        InvalidDateFormat,
//        UnknownError
//      ),
//      List(apiTagConsumer),
//      Some(List(canGetRateLimits)))
//
//
//    lazy val getActiveRateLimitsAtDate: OBPEndpoint = {
//      case "management" :: "consumers" :: consumerId :: "active-rate-limits" :: dateWithHourString :: Nil JsonGet _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetRateLimits, callContext)
//            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
//            date <- NewStyle.function.tryons(s"$InvalidDateFormat Current date format is: $dateWithHourString. Please use this format: YYYY-MM-DD-HH in UTC (e.g. 2025-12-31-13 for hour 13:00-13:59 UTC on Dec 31, 2025)", 400, callContext) {
//              val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")
//              val localDateTime = java.time.LocalDateTime.parse(dateWithHourString, formatter)
//              java.util.Date.from(localDateTime.atZone(java.time.ZoneOffset.UTC).toInstant())
//            }
//            (rateLimit, rateLimitIds) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumerId, date)
//          } yield {
//            (JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(rateLimit, rateLimitIds, date), HttpCode.`200`(callContext))
//          }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      getActiveRateLimitsNow,
//      implementedInApiVersion,
//      nameOf(getActiveRateLimitsNow),
//      "GET",
//      "/management/consumers/CONSUMER_ID/active-rate-limits",
//      "Get Active Rate Limits (Current)",
//      s"""
//         |Get the active rate limits for a consumer at the current date/time. Returns the aggregated rate limits from all active records at this moment.
//         |
//         |This is a convenience endpoint that uses the current date/time automatically.
//         |
//         |See ${Glossary.getGlossaryItemLink("Rate Limiting")} for more details on how rate limiting works.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      activeRateLimitsJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidConsumerId,
//        ConsumerNotFoundByConsumerId,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagConsumer),
//      Some(List(canGetRateLimits)))
//
//
//    lazy val getActiveRateLimitsNow: OBPEndpoint = {
//      case "management" :: "consumers" :: consumerId :: "active-rate-limits" :: Nil JsonGet _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetRateLimits, callContext)
//            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
//            date = new java.util.Date() // Use current date/time
//            (rateLimit, rateLimitIds) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumerId, date)
//          } yield {
//            (JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(rateLimit, rateLimitIds, date), HttpCode.`200`(callContext))
//          }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCurrentConsumer,
//      implementedInApiVersion,
//      nameOf(getCurrentConsumer),
//      "GET",
//      "/consumers/current",
//      "Get Current Consumer",
//      s"""Returns the consumer_id of the current authenticated consumer.
//        |
//        |This endpoint requires authentication via:
//        |* User authentication (OAuth, DirectLogin, etc.) - returns the consumer associated with the user's session
//        |* Consumer/Client authentication - returns the consumer credentials being used
//        |
//        |${userAuthenticationMessage(true)}
//        |""",
//      EmptyBody,
//      CurrentConsumerJsonV600(
//        app_name = "SOFI",
//        app_type = "Web",
//        description = "Account Management",
//        consumer_id = "123",
//        active_rate_limits = activeRateLimitsJsonV600,
//        call_counters = redisCallCountersJsonV600
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidConsumerCredentials,
//        UnknownError
//      ),
//      apiTagConsumer :: apiTagApi :: Nil,
//      Some(List(canGetCurrentConsumer))
//    )
//
//    staticResourceDocs += ResourceDoc(
//      getConsumer,
//      implementedInApiVersion,
//      nameOf(getConsumer),
//      "GET",
//      "/management/consumers/CONSUMER_ID",
//      "Get Consumer",
//      s"""Get the Consumer specified by CONSUMER_ID.
//         |
//         |This endpoint returns all consumer fields including:
//         |- Basic info: consumer_id, app_name, app_type, description, developer_email, company
//         |- OAuth: consumer_key, redirect_url
//         |- Status: enabled, created
//         |- Certificate: certificate_pem, certificate_info (subject, issuer, validity dates, PSD2 roles)
//         |- Branding: logo_url
//         |- Creator: created_by_user details
//         |- Rate limits: active_rate_limits showing current rate limiting configuration
//         |- Call counters: call_counters showing current API call usage from Redis
//         |
//         |Note: consumer_secret is never returned for security reasons.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      consumerJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ConsumerNotFoundByConsumerId,
//        UnknownError
//      ),
//      List(apiTagConsumer),
//      Some(List(canGetConsumers)),
//      authMode = UserOrApplication
//    )
//
//    lazy val getConsumer: OBPEndpoint = {
//      case "management" :: "consumers" :: consumerId :: Nil JsonGet _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetConsumers, callContext)
//            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
//            // Get rate limits and call counters
//            currentConsumerCallCounters <- Future(RateLimitingUtil.consumerRateLimitState(consumer.consumerId.get).toList)
//            date = new java.util.Date()
//            (activeRateLimit, rateLimitIds) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumer.consumerId.get, date)
//            activeRateLimitsJson = JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(activeRateLimit, rateLimitIds, date)
//            callCountersJson = JSONFactory600.createRedisCallCountersJson(currentConsumerCallCounters)
//          } yield {
//            (JSONFactory600.createConsumerJsonV600(consumer, None, activeRateLimitsJson, callCountersJson), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      invalidateCacheNamespace,
//      implementedInApiVersion,
//      nameOf(invalidateCacheNamespace),
//      "POST",
//      "/management/cache/namespaces/invalidate",
//      "Invalidate Cache Namespace",
//      """Invalidates a cache namespace by incrementing its version counter.
//        |
//        |This provides instant cache invalidation without deleting individual keys.
//        |Incrementing the version counter makes all keys with the old version unreachable.
//        |
//        |Available namespace IDs: call_counter, rl_active, rd_localised, rd_dynamic,
//        |rd_static, rd_all, swagger_static, connector, metrics_stable, metrics_recent, abac_rule
//        |
//        |Use after updating rate limits, translations, endpoints, or CBS data.
//        |
//        |Authentication is Required
//        |""",
//      InvalidateCacheNamespaceJsonV600(namespace_id = "rd_localised"),
//      InvalidatedCacheNamespaceJsonV600(
//        namespace_id = "rd_localised",
//        old_version = 1,
//        new_version = 2,
//        status = "invalidated"
//      ),
//      List(
//        InvalidJsonFormat,
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagCache, apiTagSystem, apiTagApi),
//      Some(List(canInvalidateCacheNamespace))
//    )
//
//    lazy val invalidateCacheNamespace: OBPEndpoint = {
//      case "management" :: "cache" :: "namespaces" :: "invalidate" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) {
//              json.extract[InvalidateCacheNamespaceJsonV600]
//            }
//            namespaceId = postJson.namespace_id
//            _ <- Helper.booleanToFuture(
//              s"$InvalidCacheNamespaceId $namespaceId. Valid values: ${Constant.ALL_CACHE_NAMESPACES.mkString(", ")}",
//              400,
//              callContext
//            )(Constant.ALL_CACHE_NAMESPACES.contains(namespaceId))
//            oldVersion = Constant.getCacheNamespaceVersion(namespaceId)
//            newVersionOpt = Constant.incrementCacheNamespaceVersion(namespaceId)
//            _ <- Helper.booleanToFuture(
//              s"Failed to increment cache namespace version for: $namespaceId",
//              500,
//              callContext
//            )(newVersionOpt.isDefined)
//          } yield {
//            val result = InvalidatedCacheNamespaceJsonV600(
//              namespace_id = namespaceId,
//              old_version = oldVersion,
//              new_version = newVersionOpt.get,
//              status = "invalidated"
//            )
//            (result, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCacheConfig,
//      implementedInApiVersion,
//      nameOf(getCacheConfig),
//      "GET",
//      "/system/cache/config",
//      "Get Cache Configuration",
//      """Returns cache configuration information including:
//        |
//        |- Redis status: availability, connection details (URL, port, SSL)
//        |- In-memory cache status: availability and current size
//        |- Instance ID and environment
//        |- Global cache namespace prefix
//        |
//        |This helps understand what cache backend is being used and how it's configured.
//        |
//        |Authentication is Required
//        |""",
//      EmptyBody,
//      CacheConfigJsonV600(
//        redis_status = RedisCacheStatusJsonV600(
//          available = true,
//          url = "127.0.0.1",
//          port = 6379,
//          use_ssl = false
//        ),
//        in_memory_status = InMemoryCacheStatusJsonV600(
//          available = true,
//          current_size = 42
//        ),
//        instance_id = "obp",
//        environment = "dev",
//        global_prefix = "obp_dev_"
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagCache, apiTagSystem, apiTagApi),
//      Some(List(canGetCacheConfig))
//    )
//
//    lazy val getCacheConfig: OBPEndpoint = {
//      case "system" :: "cache" :: "config" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetCacheConfig, callContext)
//          } yield {
//            val result = JSONFactory600.createCacheConfigJsonV600()
//            (result, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCacheInfo,
//      implementedInApiVersion,
//      nameOf(getCacheInfo),
//      "GET",
//      "/system/cache/info",
//      "Get Cache Information",
//      """Returns detailed cache information for all namespaces:
//        |
//        |- Namespace ID and versioned prefix
//        |- Current version counter
//        |- Number of keys in each namespace
//        |- Description and category
//        |- Storage location (redis, memory, both, or unknown)
//        |  - "redis": Keys stored in Redis
//        |  - "memory": Keys stored in in-memory cache
//        |  - "both": Keys in both locations (indicates a BUG - should never happen)
//        |  - "unknown": No keys found, storage location cannot be determined
//        |- TTL info: Sampled TTL information from actual keys
//        |  - Shows actual TTL values from up to 5 sample keys
//        |  - Format: "123s" (fixed), "range 60s to 3600s (avg 1800s)" (variable), "no expiry" (persistent)
//        |- Total key count across all namespaces
//        |- Redis availability status
//        |
//        |This endpoint helps monitor cache usage and identify which namespaces contain the most data.
//        |
//        |Authentication is Required
//        |""",
//      EmptyBody,
//      CacheInfoJsonV600(
//        namespaces = List(
//          CacheNamespaceInfoJsonV600(
//            namespace_id = "call_counter",
//            prefix = "obp_dev_call_counter_1_",
//            current_version = 1,
//            key_count = 42,
//            description = "Rate limit call counters",
//            category = "Rate Limiting",
//            storage_location = "redis",
//            ttl_info = "range 60s to 86400s (avg 3600s)"
//          ),
//          CacheNamespaceInfoJsonV600(
//            namespace_id = "rd_localised",
//            prefix = "obp_dev_rd_localised_1_",
//            current_version = 1,
//            key_count = 128,
//            description = "Localized resource docs",
//            category = "API Documentation",
//            storage_location = "redis",
//            ttl_info = "3600s"
//          )
//        ),
//        total_keys = 170,
//        redis_available = true
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagCache, apiTagSystem, apiTagApi),
//      Some(List(canGetCacheInfo))
//    )
//
//    lazy val getCacheInfo: OBPEndpoint = {
//      case "system" :: "cache" :: "info" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetCacheInfo, callContext)
//          } yield {
//            val result = JSONFactory600.createCacheInfoJsonV600()
//            (result, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getDatabasePoolInfo,
//      implementedInApiVersion,
//      nameOf(getDatabasePoolInfo),
//      "GET",
//      "/system/database/pool",
//      "Get Database Pool Information",
//      """Returns HikariCP connection pool information including:
//        |
//        |- Pool name
//        |- Active connections: currently in use
//        |- Idle connections: available in pool
//        |- Total connections: active + idle
//        |- Threads awaiting connection: requests waiting for a connection
//        |- Configuration: max pool size, min idle, timeouts
//        |
//        |This helps diagnose connection pool issues such as connection leaks or pool exhaustion.
//        |
//        |Authentication is Required
//        |""",
//      EmptyBody,
//      DatabasePoolInfoJsonV600(
//        pool_name = "HikariPool-1",
//        active_connections = 5,
//        idle_connections = 3,
//        total_connections = 8,
//        threads_awaiting_connection = 0,
//        maximum_pool_size = 10,
//        minimum_idle = 2,
//        connection_timeout_ms = 30000,
//        idle_timeout_ms = 600000,
//        max_lifetime_ms = 1800000,
//        keepalive_time_ms = 0
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagSystem, apiTagApi),
//      Some(List(canGetDatabasePoolInfo))
//    )
//
//    lazy val getDatabasePoolInfo: OBPEndpoint = {
//      case "system" :: "database" :: "pool" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetDatabasePoolInfo, callContext)
//          } yield {
//            val result = JSONFactory600.createDatabasePoolInfoJsonV600()
//            (result, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getStoredProcedureConnectorHealth,
//      implementedInApiVersion,
//      nameOf(getStoredProcedureConnectorHealth),
//      "GET",
//      "/system/connectors/stored_procedure_vDec2019/health",
//      "Get Stored Procedure Connector Health",
//      """Returns health status of the stored procedure connector including:
//        |
//        |- Connection status (ok/error)
//        |- Database server name: identifies which backend node handled the request (useful for load balancer diagnostics)
//        |- Server IP address
//        |- Database name
//        |- Response time in milliseconds
//        |- Error message (if any)
//        |
//        |Supports database-specific queries for: SQL Server, PostgreSQL, Oracle, and MySQL/MariaDB.
//        |
//        |This endpoint is useful for diagnosing connectivity issues, especially when the database is behind a load balancer
//        |and you need to identify which node is responding or experiencing SSL certificate issues.
//        |
//        |Note: This endpoint may take a long time to respond if the database connection is slow or experiencing issues.
//        |The response time depends on the connection pool timeout and JDBC driver settings.
//        |
//        |Authentication is Required
//        |""",
//      EmptyBody,
//      StoredProcedureConnectorHealthJsonV600(
//        status = "ok",
//        server_name = Some("DBSERVER01"),
//        server_ip = Some("10.0.1.50"),
//        database_name = Some("obp_adapter"),
//        response_time_ms = 45,
//        error_message = None
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagConnector, apiTagSystem, apiTagApi),
//      Some(List(canGetConnectorHealth))
//    )
//
//    lazy val getStoredProcedureConnectorHealth: OBPEndpoint = {
//      case "system" :: "connectors" :: "stored_procedure_vDec2019" :: "health" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetConnectorHealth, callContext)
//          } yield {
//            val health = StoredProcedureUtils.getHealth()
//            val result = StoredProcedureConnectorHealthJsonV600(
//              status = health.status,
//              server_name = health.serverName,
//              server_ip = health.serverIp,
//              database_name = health.databaseName,
//              response_time_ms = health.responseTimeMs,
//              error_message = health.errorMessage
//            )
//            (result, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getBanks,
//      implementedInApiVersion,
//      nameOf(getBanks),
//      "GET",
//      "/banks",
//      "Get Banks",
//      """Get banks on this API instance
//        |Returns a list of banks supported on this server:
//        |
//        |- bank_id used as parameter in URLs
//        |- Short and full name of bank
//        |- Logo URL
//        |- Website
//        |
//        |User Authentication is Optional. The User need not be logged in.
//        |""",
//      EmptyBody,
//      BanksJsonV600(List(BankJsonV600(
//        bank_id = "gh.29.uk",
//        bank_code = "bank_code",
//        full_name = "full_name",
//        logo = "logo",
//        website = "www.openbankproject.com",
//        bank_routings = List(BankRoutingJsonV121("OBP", "gh.29.uk")),
//        attributes = Some(List(BankAttributeBankResponseJsonV400("OVERDRAFT_LIMIT", "1000")))
//      ))),
//      List(UnknownError),
//      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: Nil
//    )
//
//    lazy val getBanks: OBPEndpoint = {
//      case "banks" :: Nil JsonGet _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          (banks, callContext) <- NewStyle.function.getBanks(cc.callContext)
//        } yield {
//          (JSONFactory600.createBanksJsonV600(banks), HttpCode.`200`(callContext))
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getBank,
//      implementedInApiVersion,
//      nameOf(getBank),
//      "GET",
//      "/banks/BANK_ID",
//      "Get Bank",
//      """Get the bank specified by BANK_ID
//        |Returns information about a single bank specified by BANK_ID including:
//        |
//        |- bank_id: The unique identifier of this bank
//        |- Short and full name of bank
//        |- Logo URL
//        |- Website
//        |""",
//      EmptyBody,
//      BankJsonV600(
//        bank_id = "gh.29.uk",
//        bank_code = "bank_code",
//        full_name = "full_name",
//        logo = "logo",
//        website = "www.openbankproject.com",
//        bank_routings = List(BankRoutingJsonV121("OBP", "gh.29.uk")),
//        attributes = Some(List(BankAttributeBankResponseJsonV400("OVERDRAFT_LIMIT", "1000")))
//      ),
//      List(UnknownError, BankNotFound),
//      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: Nil
//    )
//
//    lazy val getBank: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: Nil JsonGet _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          (bank, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
//          (attributes, callContext) <- NewStyle.function.getBankAttributesByBank(bankId, callContext)
//        } yield {
//          (JSONFactory600.createBankJsonV600(bank, attributes), HttpCode.`200`(callContext))
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getTransactionsForBankAccount,
//      implementedInApiVersion,
//      nameOf(getTransactionsForBankAccount),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions",
//      "Get Transactions for Account (Full)",
//      s"""Returns transactions list of the account specified by ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
//        |
//        |${userAuthenticationMessage(false)}
//        |
//        |Authentication is required if the view is not public.
//        |
//        |${urlParametersDocument(true, true)}
//        |
//        |**Note:** This v6.0.0 endpoint returns `bank_id` directly in both `this_account` and `other_account` objects,
//        |making it easier to identify which bank each account belongs to without parsing the `bank_routing` object.
//        |
//        |""",
//      EmptyBody,
//      TransactionsJsonV600(List(TransactionJsonV600(
//        transaction_id = "123",
//        this_account = ThisAccountJsonV600(
//          bank_id = "gh.29.uk",
//          account_id = "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
//          bank_routing = BankRoutingJsonV121("OBP", "gh.29.uk"),
//          account_routings = List(AccountRoutingJsonV121("OBP", "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0")),
//          holders = List(AccountHolderJSON("John Doe", false))
//        ),
//        other_account = OtherAccountJsonV600(
//          bank_id = "other.bank.uk",
//          account_id = "counterparty-123",
//          holder = AccountHolderJSON("Jane Smith", false),
//          bank_routing = BankRoutingJsonV121("OBP", "other.bank.uk"),
//          account_routings = List(AccountRoutingJsonV121("OBP", "counterparty-123")),
//          metadata = otherAccountMetadataJSON
//        ),
//        details = TransactionDetailsJSON(
//          `type` = "SEPA",
//          description = "Payment for services",
//          posted = new java.util.Date(),
//          completed = new java.util.Date(),
//          new_balance = AmountOfMoneyJsonV121("EUR", "1000.00"),
//          value = AmountOfMoneyJsonV121("EUR", "100.00")
//        ),
//        metadata = transactionMetadataJSON,
//        transaction_attributes = Nil
//      ))),
//      List(
//        FilterSortDirectionError,
//        FilterOffersetError,
//        FilterLimitError,
//        FilterDateFormatError,
//        AuthenticatedUserIsRequired,
//        BankAccountNotFound,
//        ViewNotFound,
//        UnknownError
//      ),
//      List(apiTagTransaction, apiTagAccount)
//    )
//
//    lazy val getTransactionsForBankAccount: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonGet req => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (user, callContext) <- authenticatedAccess(cc)
//            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
//            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
//            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), user, callContext)
//            (params, callContext) <- createQueriesByHttpParamsFuture(callContext.get.requestHeaders, callContext)
//            (transactions, callContext) <- bankAccount.getModeratedTransactionsFuture(bank, user, view, callContext, params) map {
//              connectorEmptyResponse(_, callContext)
//            }
//            moderatedTransactionsWithAttributes <- Future.sequence(transactions.map(transaction =>
//              NewStyle.function.getTransactionAttributes(
//                bankId,
//                transaction.id,
//                cc.callContext: Option[CallContext]).map(attributes => code.api.v3_0_0.ModeratedTransactionWithAttributes(transaction, attributes._1))
//            ))
//          } yield {
//            (JSONFactory600.createTransactionsJsonV600(moderatedTransactionsWithAttributes), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    lazy val getCurrentConsumer: OBPEndpoint = {
//      case "consumers" :: "current" :: Nil JsonGet _ => {
//        cc => {
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            consumer <- Future {
//              cc.consumer match {
//                case Full(c) => Full(c)
//                case _ => Empty
//              }
//            } map {
//              unboxFullOrFail(_, cc.callContext, InvalidConsumerCredentials, 401)
//            }
//            currentConsumerCallCounters <- Future(RateLimitingUtil.consumerRateLimitState(consumer.consumerId.get).toList)
//            date = new java.util.Date()
//            (activeRateLimit, rateLimitIds) <- RateLimitingUtil.getActiveRateLimitsWithIds(consumer.consumerId.get, date)
//            activeRateLimitsJson = JSONFactory600.createActiveRateLimitsJsonV600FromCallLimit(activeRateLimit, rateLimitIds, date)
//            callCountersJson = createRedisCallCountersJson(currentConsumerCallCounters)
//          } yield {
//            (CurrentConsumerJsonV600(consumer.name.get, consumer.appType.get, consumer.description.get, consumer.consumerId.get, activeRateLimitsJson, callCountersJson), HttpCode.`200`(callContext))
//          }
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getDynamicEntityDiagnostics,
//      implementedInApiVersion,
//      nameOf(getDynamicEntityDiagnostics),
//      "GET",
//      "/management/diagnostics/dynamic-entities",
//      "Get Dynamic Entity Diagnostics",
//      s"""Get diagnostic information about Dynamic Entities to help troubleshoot Swagger generation issues.
//         |
//         |**Use Case:**
//         |This endpoint is particularly useful when:
//         |* The Swagger endpoint (`/obp/v6.0.0/resource-docs/OBPv6.0.0/swagger?content=dynamic`) fails with errors like "expected boolean"
//         |* The OBP endpoint (`/obp/v6.0.0/resource-docs/OBPv6.0.0/obp?content=dynamic`) works fine
//         |* You need to identify which dynamic entity has malformed field definitions
//         |
//         |**What It Checks:**
//         |This endpoint analyzes all dynamic entities (both system and bank level) for:
//         |* Boolean fields with invalid example values (e.g., actual JSON booleans or invalid strings instead of `"true"` or `"false"`)
//         |* Malformed JSON in field definitions
//         |* Fields that cannot be converted to their declared types
//         |* Other validation issues that cause Swagger generation to fail
//         |
//         |**Response Format:**
//         |The response contains:
//         |* `issues` - List of issues found, each with:
//         |  * `entity_name` - Name of the problematic entity
//         |  * `bank_id` - Bank ID (or "SYSTEM_LEVEL" for system entities)
//         |  * `field_name` - Name of the problematic field
//         |  * `example_value` - The current (invalid) example value
//         |  * `error_message` - Description of what's wrong and how to fix it
//         |* `total_issues` - Count of total issues found
//         |* `scanned_entities` - List of all dynamic entities that were scanned (format: "EntityName (BANK_ID)" or "EntityName (SYSTEM)")
//         |
//         |**How to Fix Issues:**
//         |1. Identify the problematic entity from the diagnostic output
//         |2. Update the entity definition using PUT `/management/system-dynamic-entities/DYNAMIC_ENTITY_ID` or PUT `/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID`
//         |3. For boolean fields, ensure the example value is either `"true"` or `"false"` (as strings)
//         |4. Re-run this diagnostic to verify the fix
//         |5. Check that the Swagger endpoint now works
//         |
//         |**Example Issue:**
//         |```
//         |{
//         |  "entity_name": "Customer",
//         |  "bank_id": "gh.29.uk",
//         |  "field_name": "is_active",
//         |  "example_value": "malformed_value",
//         |  "error_message": "Boolean field has invalid example value. Expected 'true' or 'false', got: 'malformed_value'"
//         |}
//         |```
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |**Required Role:** `CanGetDynamicEntityDiagnostics`
//         |
//         |If no issues are found, the response will contain an empty issues list with `total_issues: 0`, but `scanned_entities` will show which entities were checked.
//         |""",
//      EmptyBody,
//      DynamicEntityDiagnosticsJsonV600(
//        scanned_entities = List("MyEntity (gh.29.uk)", "AnotherEntity (SYSTEM)"),
//        issues = List(
//          DynamicEntityIssueJsonV600(
//            entity_name = "MyEntity",
//            bank_id = "gh.29.uk",
//            field_name = "is_active",
//            example_value = "malformed_value",
//            error_message = "Boolean field has invalid example value. Expected 'true' or 'false', got: 'malformed_value'"
//          )
//        ),
//        total_issues = 1,
//        orphaned_entities = List(
//          OrphanedDynamicEntityJsonV600(
//            entity_name = "OldEntity",
//            bank_id = "gh.29.uk",
//            record_count = 42
//          )
//        )
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagDynamicEntity, apiTagApi),
//      Some(List(canGetDynamicEntityDiagnostics))
//    )
//
//    lazy val getDynamicEntityDiagnostics: OBPEndpoint = {
//      case "management" :: "diagnostics" :: "dynamic-entities" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetDynamicEntityDiagnostics, callContext)
//          } yield {
//            val result = DiagnosticDynamicEntityCheck.checkAllDynamicEntities()
//            val issuesJson = result.issues.map { issue =>
//              DynamicEntityIssueJsonV600(
//                entity_name = issue.entityName,
//                bank_id = issue.bankId.getOrElse("SYSTEM_LEVEL"),
//                field_name = issue.fieldName,
//                example_value = issue.exampleValue,
//                error_message = issue.errorMessage
//              )
//            }
//            val orphanedJson = result.orphanedEntities.map { orphan =>
//              OrphanedDynamicEntityJsonV600(
//                entity_name = orphan.entityName,
//                bank_id = orphan.bankId,
//                record_count = orphan.recordCount
//              )
//            }
//            val response = DynamicEntityDiagnosticsJsonV600(result.scannedEntities, issuesJson, result.issues.length, orphanedJson)
//            (response, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      cleanupOrphanedDynamicEntityRecords,
//      implementedInApiVersion,
//      nameOf(cleanupOrphanedDynamicEntityRecords),
//      "DELETE",
//      "/management/diagnostics/dynamic-entities/orphaned-records",
//      "Cleanup Orphaned Dynamic Entity Records",
//      s"""Delete orphaned dynamic entity data records.
//         |
//         |Orphaned records are rows in the DynamicData table whose entityName/bankId combination
//         |has no matching Dynamic Entity definition. These can accumulate when entity definitions
//         |are deleted but their data records are not cleaned up.
//         |
//         |This endpoint first identifies all orphaned records (using the same detection logic as
//         |GET /management/diagnostics/dynamic-entities), then deletes them.
//         |
//         |**Response Format:**
//         |* `deleted_orphaned_entities` - List of orphaned entity groups that were deleted, each with:
//         |  * `entity_name` - Name of the orphaned entity
//         |  * `bank_id` - Bank ID (or empty string for system-level)
//         |  * `record_count` - Number of records that were deleted for this entity group
//         |* `total_records_deleted` - Total count of all deleted records
//         |
//         |Authentication is Required
//         |
//         |**Required Role:** `CanCleanupOrphanedDynamicEntityRecords`
//         |""",
//      EmptyBody,
//      CleanupOrphanedDynamicEntityResponseJsonV600(
//        deleted_orphaned_entities = List(
//          OrphanedDynamicEntityJsonV600(
//            entity_name = "OldEntity",
//            bank_id = "gh.29.uk",
//            record_count = 42
//          )
//        ),
//        total_records_deleted = 42
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagDynamicEntity, apiTagApi),
//      Some(List(canCleanupOrphanedDynamicEntityRecords))
//    )
//
//    lazy val cleanupOrphanedDynamicEntityRecords: OBPEndpoint = {
//      case "management" :: "diagnostics" :: "dynamic-entities" :: "orphaned-records" :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canCleanupOrphanedDynamicEntityRecords, callContext)
//          } yield {
//            // Get all entity definitions (both bank and system level)
//            val definitions = DynamicEntityProvider.connectorMethodProvider.vend.getDynamicEntities(None, true)
//            // Identify orphaned records
//            val orphaned = DiagnosticDynamicEntityCheck.checkOrphanedRecords(definitions)
//            // Delete orphaned data records for each orphaned entity group
//            var totalDeleted: Long = 0
//            orphaned.foreach { orphan =>
//              val bankIdOption = if (orphan.bankId.isEmpty) None else Some(orphan.bankId)
//              val records = bankIdOption match {
//                case None =>
//                  DynamicData.findAll(
//                    By(DynamicData.DynamicEntityName, orphan.entityName),
//                    NullRef(DynamicData.BankId)
//                  )
//                case Some(bid) =>
//                  DynamicData.findAll(
//                    By(DynamicData.DynamicEntityName, orphan.entityName),
//                    By(DynamicData.BankId, bid)
//                  )
//              }
//              records.foreach { record =>
//                record.delete_!
//                totalDeleted += 1
//              }
//            }
//            // Build response
//            val orphanedJson = orphaned.map { o =>
//              OrphanedDynamicEntityJsonV600(o.entityName, o.bankId, o.recordCount)
//            }
//            (CleanupOrphanedDynamicEntityResponseJsonV600(orphanedJson, totalDeleted), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getReferenceTypes,
//      implementedInApiVersion,
//      nameOf(getReferenceTypes),
//      "GET",
//      "/management/dynamic-entities/reference-types",
//      "Get Reference Types for Dynamic Entities",
//      s"""Get a list of all available reference types that can be used in Dynamic Entity field definitions.
//         |
//         |Reference types allow Dynamic Entity fields to reference other entities (similar to foreign keys).
//         |This endpoint returns both:
//         |* **Static reference types** - Built-in reference types for core OBP entities (e.g., Customer, Account, Transaction)
//         |* **Dynamic reference types** - Reference types for Dynamic Entities that have been created
//         |
//         |Each reference type includes:
//         |* `type_name` - The full reference type string to use in entity definitions (e.g., "reference:Customer")
//         |* `example_value` - An example value showing the correct format
//         |* `description` - Description of what the reference type represents
//         |
//         |**Use Case:**
//         |When creating a Dynamic Entity with a field that references another entity, you need to know:
//         |1. What reference types are available
//         |2. The correct format for the type name
//         |3. The correct format for example values
//         |
//         |This endpoint provides all that information.
//         |
//         |**Example Usage:**
//         |If you want to create a Dynamic Entity with a field that references a Customer, you would:
//         |1. Call this endpoint to see that "reference:Customer" is available
//         |2. Use it in your entity definition like:
//         |```json
//         |{
//         |  "customer_id": {
//         |    "type": "reference:Customer",
//         |    "example": "a8770fca-3d1d-47af-b6d0-7a6c3f124388"
//         |  }
//         |}
//         |```
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |**Required Role:** `CanGetDynamicEntityReferenceTypes`
//         |""",
//      EmptyBody,
//      ReferenceTypesJsonV600(
//        reference_types = List(
//          ReferenceTypeJsonV600(
//            type_name = "reference:Customer",
//            example_value = "a8770fca-3d1d-47af-b6d0-7a6c3f124388",
//            description = "Reference to a Customer entity"
//          ),
//          ReferenceTypeJsonV600(
//            type_name = "reference:Account:BANK_ID&ACCOUNT_ID",
//            example_value = "BANK_ID=b9881ecb-4e2e-58bg-c7e1-8b7d4e235499&ACCOUNT_ID=c0992fdb-5f3f-69ch-d8f2-9c8e5f346600",
//            description = "Composite reference to an Account by bank ID and account ID"
//          ),
//          ReferenceTypeJsonV600(
//            type_name = "reference:MyDynamicEntity",
//            example_value = "d1aa3gec-6g4g-70di-e9g3-0d9f6g457711",
//            description = "Reference to MyDynamicEntity (dynamic entity)"
//          )
//        )
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagDynamicEntity, apiTagApi),
//      Some(List(canGetDynamicEntityReferenceTypes))
//    )
//
//    lazy val getReferenceTypes: OBPEndpoint = {
//      case "management" :: "dynamic-entities" :: "reference-types" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetDynamicEntityReferenceTypes, callContext)
//          } yield {
//            val referenceTypeNames = code.dynamicEntity.ReferenceType.referenceTypeNames
//
//            // Get list of dynamic entity names to distinguish from static references
//            val dynamicEntityNames = NewStyle.function.getDynamicEntities(None, true)
//              .map(entity => s"reference:${entity.entityName}")
//              .toSet
//
//            val exampleId1 = APIUtil.generateUUID()
//            val exampleId2 = APIUtil.generateUUID()
//            val exampleId3 = APIUtil.generateUUID()
//            val exampleId4 = APIUtil.generateUUID()
//
//            val reg1 = """reference:([^:]+)""".r
//            val reg2 = """reference:(?:[^:]+):([^&]+)&([^&]+)""".r
//            val reg3 = """reference:(?:[^:]+):([^&]+)&([^&]+)&([^&]+)""".r
//            val reg4 = """reference:(?:[^:]+):([^&]+)&([^&]+)&([^&]+)&([^&]+)""".r
//
//            val referenceTypes = referenceTypeNames.map { refTypeName =>
//              val example = refTypeName match {
//                case reg1(entityName) =>
//                  val description = if (dynamicEntityNames.contains(refTypeName)) {
//                    s"Reference to $entityName (dynamic entity)"
//                  } else {
//                    s"Reference to $entityName entity"
//                  }
//                  (exampleId1, description)
//                case reg2(a, b) =>
//                  (s"$a=$exampleId1&$b=$exampleId2", s"Composite reference with $a and $b")
//                case reg3(a, b, c) =>
//                  (s"$a=$exampleId1&$b=$exampleId2&$c=$exampleId3", s"Composite reference with $a, $b and $c")
//                case reg4(a, b, c, d) =>
//                  (s"$a=$exampleId1&$b=$exampleId2&$c=$exampleId3&$d=$exampleId4", s"Composite reference with $a, $b, $c and $d")
//                case _ => (exampleId1, "Reference type")
//              }
//
//              ReferenceTypeJsonV600(
//                type_name = refTypeName,
//                example_value = example._1,
//                description = example._2
//              )
//            }
//
//            val response = ReferenceTypesJsonV600(referenceTypes)
//            (response, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      getCurrentUser,
//      implementedInApiVersion,
//      nameOf(getCurrentUser), // TODO can we get this string from the val two lines above?
//      "GET",
//      "/users/current",
//      "Get User (Current)",
//      s"""Get the logged in user
//         |
//         |${userAuthenticationMessage(true)}
//      """.stripMargin,
//      EmptyBody,
//      userJsonV300,
//      List(AuthenticatedUserIsRequired, UnknownError),
//      List(apiTagUser))
//
//    lazy val getCurrentUser: OBPEndpoint = {
//      case "users" :: "current" :: Nil JsonGet _ => {
//        cc => {
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            entitlements <- NewStyle.function.getEntitlementsByUserId(u.userId, callContext)
//          } yield {
//            val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(u).toOption
//            // Add virtual entitlements for super_admin_user_ids or oidc_operator_user_ids
//            val virtualRoleNames = if (APIUtil.isSuperAdmin(u.userId)) {
//              JSONFactory200.superAdminVirtualRoles
//            } else if (APIUtil.isOidcOperator(u.userId)) {
//              JSONFactory200.oidcOperatorVirtualRoles
//            } else {
//              List.empty
//            }
//            val existingRoleNames = entitlements.map(_.roleName).toSet
//            val virtualEntitlements = virtualRoleNames.filterNot(existingRoleNames.contains).map { role =>
//              new Entitlement {
//                def entitlementId: String = ""
//                def bankId: String = ""
//                def userId: String = u.userId
//                def roleName: String = role
//                def createdByProcess: String = if (APIUtil.isSuperAdmin(u.userId)) "super_admin_user_ids" else "oidc_operator_user_ids"
//                def entitlementRequestId: Option[String] = None
//                def groupId: Option[String] = None
//                def process: Option[String] = None
//              }
//            }
//            val finalEntitlements = entitlements ::: virtualEntitlements
//            val currentUser = UserV600(u, finalEntitlements, permissions)
//            val onBehalfOfUser = if(cc.onBehalfOfUser.isDefined) {
//              val user = cc.onBehalfOfUser.toOption.get
//              val entitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).headOption.toList.flatten
//              val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(user).toOption
//              Some(UserV600(user, entitlements, permissions))
//            } else {
//              None
//            }
//            (JSONFactory600.createUserInfoJSON(currentUser, onBehalfOfUser), HttpCode.`200`(callContext))
//          }
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getUsers,
//      implementedInApiVersion,
//      nameOf(getUsers),
//      "GET",
//      "/users",
//      "Get all Users",
//      s"""Get all users, optionally filtered.
//         |
//         |All query parameters are optional and may be combined.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |CanGetAnyUser entitlement is required.
//         |
//         |${urlParametersDocument(false, false)}
//         |* provider (if null ignore) - filter by identity provider, exact match
//         |* username (if null ignore) - filter by username, exact match
//         |* email (if null ignore) - filter by email, exact match (may return multiple users — duplicate emails are allowed in OBP by design)
//         |* user_id (if null ignore) - filter by user_id, exact match
//         |* locked_status (if null ignore) - "active" or "locked"
//         |* is_deleted (default: false)
//         |* role_name (if null ignore) - filter by entitlement/role name e.g. CanCreateAccount
//         |* bank_id (if null ignore) - when used with role_name, filter entitlements by bank_id
//         |* sort_by (if null ignore) - sort by field; allowed values: ${code.users.DoobieUserQueries.SortableColumns.keySet.toSeq.sorted.mkString(", ")}
//         |* sort_direction (if null defaults to DESC) - "asc" or "desc" (case-insensitive)
//         |
//         |When sort_by is omitted, results are ordered by insertion order ascending (stable pagination).
//         |
//         |Returns an empty list (not 404) when no users match.
//         |
//      """.stripMargin,
//      EmptyBody,
//      usersInfoJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        FilterSortByError,
//        FilterSortByNotAllowedForEndpoint,
//        FilterSortDirectionError,
//        UnknownError
//      ),
//      List(apiTagUser),
//      Some(List(canGetAnyUser))
//    )
//
//    lazy val getUsers: OBPEndpoint = {
//      case "users" :: Nil JsonGet _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        logger.info(s"getUsers says: GET /users called, url=${cc.url}")
//        for {
//          (Full(u), callContext) <- authenticatedAccess(cc)
//          _ = logger.info(s"getUsers says: authenticated user_id=${u.userId} provider=${u.provider}")
//          _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyUser, callContext)
//          httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
//          (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(
//            httpParams,
//            callContext
//          )
//          _ <- Future {
//            val requestedSort = obpQueryParams.collectFirst { case OBPSortBy(v) => v }
//            val allowed = code.users.DoobieUserQueries.SortableColumns.keySet
//            val valid: Box[Unit] = requestedSort match {
//              case Some(v) if !allowed.contains(v) =>
//                Failure(ErrorMessages.filterSortByNotAllowedForEndpointDetail("GET /users", v, allowed))
//              case _ => Full(())
//            }
//            unboxFullOrFail(valid, callContext, ErrorMessages.FilterSortByNotAllowedForEndpoint, 400)
//          }
//          rows <- code.users.Users.users.vend.getUsersV600F(obpQueryParams)
//          _ = logger.info(s"getUsers says: returning ${rows.size} user(s) to user_id=${u.userId}")
//        } yield {
//          (JSONFactory600.createUsersInfoJsonV600(rows), HttpCode.`200`(callContext))
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getUserByUserId,
//      implementedInApiVersion,
//      nameOf(getUserByUserId),
//      "GET",
//      "/users/user-id/USER_ID",
//      "Get User by USER_ID",
//      s"""Get user by USER_ID
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |CanGetAnyUser entitlement is required,
//         |
//      """.stripMargin,
//      EmptyBody,
//      userInfoDetailJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UserNotFoundByUserId,
//        UnknownError
//      ),
//      List(apiTagUser),
//      Some(List(canGetAnyUser))
//    )
//
//    lazy val getUserByUserId: OBPEndpoint = {
//      case "users" :: "user-id" :: userId :: Nil JsonGet _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          (Full(u), callContext) <- authenticatedAccess(cc)
//          user <- Users.users.vend.getUserByUserIdFuture(userId) map {
//            x => unboxFullOrFail(x, callContext, s"$UserNotFoundByUserId Current UserId($userId)")
//          }
//          entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, callContext)
//          // Fetch user agreements
//          agreements <- Future {
//            val acceptMarketingInfo = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "accept_marketing_info")
//            val termsAndConditions = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "terms_and_conditions")
//            val privacyConditions = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "privacy_conditions")
//            val agreementList = acceptMarketingInfo.toList ::: termsAndConditions.toList ::: privacyConditions.toList
//            if (agreementList.isEmpty) None else Some(agreementList)
//          }
//          isLocked = LoginAttempt.userIsLocked(user.provider, user.name)
//          authUser = code.model.dataAccess.AuthUser.find(
//            By(code.model.dataAccess.AuthUser.user, user.userPrimaryKey.value)
//          )
//          // Fetch metrics data for the user
//          userMetrics <- Future {
//            code.metrics.MappedMetric.findAll(
//              By(code.metrics.MappedMetric.userId, userId),
//              OrderBy(code.metrics.MappedMetric.date, Descending),
//              MaxRows(5)
//            )
//          }
//          lastActivityDate = userMetrics.headOption.map(_.getDate())
//          recentOperationIds = userMetrics.map(_.getImplementedByPartialFunction()).distinct.take(5)
//        } yield {
//          (JSONFactory600.createUserInfoJsonV600(
//            user,
//            authUser.map(_.firstName.get).getOrElse(""),
//            authUser.map(_.lastName.get).getOrElse(""),
//            entitlements,
//            agreements,
//            isLocked,
//            lastActivityDate,
//            recentOperationIds
//          ), HttpCode.`200`(callContext))
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getMigrations,
//      implementedInApiVersion,
//      nameOf(getMigrations),
//      "GET",
//      "/system/migrations",
//      "Get Database Migrations",
//      s"""Get all database migration script logs.
//         |
//         |This endpoint returns information about all migration scripts that have been executed or attempted.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |CanGetMigrations entitlement is required.
//         |
//      """.stripMargin,
//      EmptyBody,
//      migrationScriptLogsJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagSystem, apiTagApi),
//      Some(List(canGetMigrations))
//    )
//
//    lazy val getMigrations: OBPEndpoint = {
//      case "system" :: "migrations" :: Nil JsonGet _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          (Full(u), callContext) <- authenticatedAccess(cc)
//          _ <- NewStyle.function.hasEntitlement("", u.userId, canGetMigrations, callContext)
//        } yield {
//          val migrations = code.migration.MigrationScriptLogProvider.migrationScriptLogProvider.vend.getMigrationScriptLogs()
//          (JSONFactory600.createMigrationScriptLogsJsonV600(migrations), HttpCode.`200`(callContext))
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCacheNamespaces,
//      implementedInApiVersion,
//      nameOf(getCacheNamespaces),
//      "GET",
//      "/system/cache/namespaces",
//      "Get Cache Namespaces",
//      """Returns information about all cache namespaces in the system.
//        |
//        |This endpoint provides visibility into:
//        |* Cache namespace prefixes and their purposes
//        |* Number of keys in each namespace
//        |* TTL configurations
//        |* Example keys for each namespace
//        |
//        |This is useful for:
//        |* Monitoring cache usage
//        |* Understanding cache structure
//        |* Debugging cache-related issues
//        |* Planning cache management operations
//        |
//        |""",
//      EmptyBody,
//      CacheNamespacesJsonV600(
//        namespaces = List(
//          CacheNamespaceJsonV600(
//            prefix = "call_counter_",
//            description = "Rate limiting counters per consumer and time period",
//            ttl_seconds = "varies",
//            category = "Rate Limiting",
//            key_count = 42,
//            example_key = "rl_counter_consumer123_PER_MINUTE"
//          ),
//          CacheNamespaceJsonV600(
//            prefix = "rl_active_",
//            description = "Active rate limit configurations",
//            ttl_seconds = "3600",
//            category = "Rate Limiting",
//            key_count = 15,
//            example_key = "rl_active_consumer123_2024-12-27-14"
//          ),
//          CacheNamespaceJsonV600(
//            prefix = "rd_localised_",
//            description = "Localized resource documentation",
//            ttl_seconds = "3600",
//            category = "Resource Documentation",
//            key_count = 128,
//            example_key = "rd_localised_operationId:getBanks-locale:en"
//          )
//        )
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagCache, apiTagSystem, apiTagApi),
//      Some(List(canGetCacheNamespaces))
//    )
//
//    lazy val getCacheNamespaces: OBPEndpoint = {
//      case "system" :: "cache" :: "namespaces" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetCacheNamespaces, callContext)
//          } yield {
//            // Define known cache namespaces with their metadata
//            val namespaces = List(
//              // Rate Limiting
//              (Constant.CALL_COUNTER_PREFIX, "Rate limiting counters per consumer and time period", "varies", "Rate Limiting"),
//              (Constant.RATE_LIMIT_ACTIVE_PREFIX, "Active rate limit configurations", Constant.RATE_LIMIT_ACTIVE_CACHE_TTL.toString, "Rate Limiting"),
//              // Resource Documentation
//              (Constant.LOCALISED_RESOURCE_DOC_PREFIX, "Localized resource documentation", Constant.CREATE_LOCALISED_RESOURCE_DOC_JSON_TTL.toString, "Resource Documentation"),
//              (Constant.DYNAMIC_RESOURCE_DOC_CACHE_KEY_PREFIX, "Dynamic resource documentation", Constant.GET_DYNAMIC_RESOURCE_DOCS_TTL.toString, "Resource Documentation"),
//              (Constant.STATIC_RESOURCE_DOC_CACHE_KEY_PREFIX, "Static resource documentation", Constant.GET_STATIC_RESOURCE_DOCS_TTL.toString, "Resource Documentation"),
//              (Constant.ALL_RESOURCE_DOC_CACHE_KEY_PREFIX, "All resource documentation", Constant.GET_STATIC_RESOURCE_DOCS_TTL.toString, "Resource Documentation"),
//              (Constant.STATIC_SWAGGER_DOC_CACHE_KEY_PREFIX, "Swagger documentation", Constant.GET_STATIC_RESOURCE_DOCS_TTL.toString, "Resource Documentation"),
//              // Connector
//              (Constant.CONNECTOR_PREFIX, "Connector method names and metadata", "3600", "Connector"),
//              // Metrics
//              (Constant.METRICS_STABLE_PREFIX, "Stable metrics (historical)", "86400", "Metrics"),
//              (Constant.METRICS_RECENT_PREFIX, "Recent metrics", "7", "Metrics"),
//              // ABAC
//              (Constant.ABAC_RULE_PREFIX, "ABAC rule cache", "indefinite", "ABAC")
//            ).map { case (prefix, description, ttl, category) =>
//              // Get actual key count and example from Redis
//              val keyCount = Redis.countKeys(s"${prefix}*")
//              val exampleKey = Redis.getSampleKey(s"${prefix}*")
//              JSONFactory600.createCacheNamespaceJsonV600(
//                prefix = prefix,
//                description = description,
//                ttlSeconds = ttl,
//                category = category,
//                keyCount = keyCount,
//                exampleKey = exampleKey
//              )
//            }
//
//            (JSONFactory600.createCacheNamespacesJsonV600(namespaces), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createTransactionRequestCardano,
//      implementedInApiVersion,
//      nameOf(createTransactionRequestCardano),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/CARDANO/transaction-requests",
//      "Create Transaction Request (CARDANO)",
//      s"""
//         |
//         |For sandbox mode, it will use the Cardano Preprod Network.
//         |The accountId can be the wallet_id for now, as it uses cardano-wallet in the backend.
//         |
//         |${transactionRequestGeneralText}
//         |
//       """.stripMargin,
//      transactionRequestBodyCardanoJsonV600,
//      transactionRequestWithChargeJSON400,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        $BankAccountNotFound,
//        InsufficientAuthorisationToCreateTransactionRequest,
//        InvalidTransactionRequestType,
//        InvalidJsonFormat,
//        NotPositiveAmount,
//        InvalidTransactionRequestCurrency,
//        TransactionDisabled,
//        UnknownError
//      ),
//      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
//    )
//
//    lazy val createTransactionRequestCardano: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
//        "CARDANO" :: "transaction-requests" :: Nil JsonPost json -> _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          val transactionRequestType = TransactionRequestType("CARDANO")
//          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createTransactionRequestEthereumeSendTransaction,
//      implementedInApiVersion,
//      nameOf(createTransactionRequestEthereumeSendTransaction),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/ETH_SEND_TRANSACTION/transaction-requests",
//      "Create Transaction Request (ETH_SEND_TRANSACTION)",
//      s"""
//         |
//         |Send ETH via Ethereum JSON-RPC.
//         |AccountId should hold the 0x address for now.
//         |
//         |${transactionRequestGeneralText}
//         |
//       """.stripMargin,
//      transactionRequestBodyEthereumJsonV600,
//      transactionRequestWithChargeJSON400,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        $BankAccountNotFound,
//        InsufficientAuthorisationToCreateTransactionRequest,
//        InvalidTransactionRequestType,
//        InvalidJsonFormat,
//        NotPositiveAmount,
//        InvalidTransactionRequestCurrency,
//        TransactionDisabled,
//        UnknownError
//      ),
//      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
//    )
//
//    lazy val createTransactionRequestEthereumeSendTransaction: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
//        "ETH_SEND_TRANSACTION" :: "transaction-requests" :: Nil JsonPost json -> _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          val transactionRequestType = TransactionRequestType("ETH_SEND_TRANSACTION")
//          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
//    }
//    staticResourceDocs += ResourceDoc(
//      createTransactionRequestEthSendRawTransaction,
//      implementedInApiVersion,
//      nameOf(createTransactionRequestEthSendRawTransaction),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/ETH_SEND_RAW_TRANSACTION/transaction-requests",
//      "CREATE TRANSACTION REQUEST (ETH_SEND_RAW_TRANSACTION )",
//      s"""
//         |
//         |Send ETH via Ethereum JSON-RPC.
//         |AccountId should hold the 0x address for now.
//         |
//         |${transactionRequestGeneralText}
//         |
//       """.stripMargin,
//      transactionRequestBodyEthSendRawTransactionJsonV600,
//      transactionRequestWithChargeJSON400,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        $BankAccountNotFound,
//        InsufficientAuthorisationToCreateTransactionRequest,
//        InvalidTransactionRequestType,
//        InvalidJsonFormat,
//        NotPositiveAmount,
//        InvalidTransactionRequestCurrency,
//        TransactionDisabled,
//        UnknownError
//      ),
//      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
//    )
//
//    lazy val createTransactionRequestEthSendRawTransaction: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
//        "ETH_SEND_RAW_TRANSACTION" :: "transaction-requests" :: Nil JsonPost json -> _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          val transactionRequestType = TransactionRequestType("ETH_SEND_RAW_TRANSACTION")
//          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      createBank,
//      implementedInApiVersion,
//      "createBank",
//      "POST",
//      "/banks",
//      "Create Bank",
//      s"""Create a new bank (Authenticated access).
//         |
//         |The user creating this will be automatically assigned the Role CanCreateEntitlementAtOneBank.
//         |Thus the User can manage the bank they create and assign Roles to other Users.
//         |
//         Only SANDBOX mode (i.e. when connector=mapped in properties file)
//         |The settlement accounts are automatically created by the system when the bank is created.
//         |Name and account id are created in accordance to the next rules:
//         |  - Incoming account (name: Default incoming settlement account, Account ID: OBP_DEFAULT_INCOMING_ACCOUNT_ID, currency: EUR)
//         |  - Outgoing account (name: Default outgoing settlement account, Account ID: OBP_DEFAULT_OUTGOING_ACCOUNT_ID, currency: EUR)
//         |
//         |""",
//
//      postBankJson600,
//      bankJson600,
//      List(
//        InvalidJsonFormat,
//        $AuthenticatedUserIsRequired,
//        InsufficientAuthorisationToCreateBank,
//        UnknownError
//      ),
//      List(apiTagBank),
//      Some(List(canCreateBank))
//    )
//
//    lazy val createBank: OBPEndpoint = {
//      case "banks" :: Nil JsonPost json -> _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostBankJson600 "
//          // Catch the common v4/v5 → v6 field-rename mistakes before extraction.
//          // Without this, sending {"id": "..."} silently produces an empty bank_id
//          // and fails downstream with a confusing BANK_ID length-validation error.
//          val deprecatedFieldHints: List[String] = json match {
//            case JObject(fields) => fields.collect {
//              case JField("id", _) =>
//                "'id' was renamed to 'bank_id' in v6.0.0 (it was the field name in v4.0.0 and v5.0.0)"
//              case JField("short_name", _) =>
//                "'short_name' was removed in v5.0.0 (it only existed in v4.0.0)"
//            }
//            case _ => Nil
//          }
//          for {
//            _ <- Helper.booleanToFuture(
//              failMsg = s"$InvalidJsonFormat Deprecated request-body field(s): ${deprecatedFieldHints.mkString("; ")}. The v6.0.0 createBank body shape is: bank_id, bank_code, full_name, logo, website, bank_routings.",
//              cc = cc.callContext
//            ) {
//              deprecatedFieldHints.isEmpty
//            }
//            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
//              json.extract[PostBankJson600]
//            }
//
//            // TODO: Improve this error message to not hardcode "16" - should reference the max length from checkOptionalShortString function
//            checkShortStringValue = APIUtil.checkOptionalShortString(postJson.bank_id)
//            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID: $checkShortStringValue BANK_ID must contain only characters A-Z, a-z, 0-9, -, _, . and be max 16 characters.", cc = cc.callContext) {
//              checkShortStringValue == SILENCE_IS_GOLDEN
//            }
//
//            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.InvalidConsumerCredentials, cc = cc.callContext) {
//              cc.callContext.map(_.consumer.isDefined == true).isDefined
//            }
//            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc = cc.callContext) {
//              postJson.bank_id.length > 3
//            }
//            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain space characters", cc = cc.callContext) {
//              !postJson.bank_id.contains(" ")
//            }
//            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain `::::` characters", cc = cc.callContext) {
//              !`checkIfContains::::`(postJson.bank_id)
//            }
//            (banks, callContext) <- NewStyle.function.getBanks(cc.callContext)
//            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.bankIdAlreadyExists, failCode = 409, cc = cc.callContext) {
//              !banks.exists { b => b.bankId.value == postJson.bank_id }
//            }
//            (success, callContext) <- NewStyle.function.createOrUpdateBank(
//              postJson.bank_id,
//              postJson.full_name.getOrElse(""),
//              postJson.bank_code,
//              postJson.logo.getOrElse(""),
//              postJson.website.getOrElse(""),
//              postJson.bank_routings.getOrElse(Nil).find(_.scheme == "BIC").map(_.address).getOrElse(""),
//              "",
//              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
//              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
//              callContext
//            )
//            entitlements <- NewStyle.function.getEntitlementsByUserId(cc.userId, callContext)
//            entitlementsByBank = entitlements.filter(_.bankId == postJson.bank_id)
//            _ <- entitlementsByBank.exists(_.roleName == CanCreateEntitlementAtOneBank.toString()) match {
//              case true =>
//                // Already has entitlement
//                Future(())
//              case false =>
//                Future(Entitlement.entitlement.vend.addEntitlement(postJson.bank_id, cc.userId, CanCreateEntitlementAtOneBank.toString()))
//            }
//            _ <- entitlementsByBank.exists(_.roleName == CanReadDynamicResourceDocsAtOneBank.toString()) match {
//              case true =>
//                // Already has entitlement
//                Future(())
//              case false =>
//                Future(Entitlement.entitlement.vend.addEntitlement(postJson.bank_id, cc.userId, CanReadDynamicResourceDocsAtOneBank.toString()))
//            }
//          } yield {
//            (JSONFactory600.createBankJSON600(success), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//
//
//    staticResourceDocs += ResourceDoc(
//      getProviders,
//      implementedInApiVersion,
//      nameOf(getProviders),
//      "GET",
//      "/providers",
//      "Get Providers",
//      s"""Get the list of authentication providers that have been used to create users on this OBP instance.
//         |
//         |This endpoint returns a distinct list of provider values from the resource_user table.
//         |
//         |Providers may include:
//         |* Local OBP provider (e.g., "http://127.0.0.1:8080")
//         |* OAuth 2.0 / OpenID Connect providers (e.g., "google.com", "microsoft.com")
//         |* Custom authentication providers
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      JSONFactory600.createProvidersJson(List("http://127.0.0.1:8080", "OBP", "google.com")),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagUser),
//      None
//    )
//
//    lazy val getProviders: OBPEndpoint = {
//      case "providers" :: Nil JsonGet _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            providers <- Future { code.model.dataAccess.ResourceUser.getDistinctProviders }
//          } yield {
//            (JSONFactory600.createProvidersJson(providers), HttpCode.`200`(callContext))
//          }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getConnectorMethodNames,
//      implementedInApiVersion,
//      nameOf(getConnectorMethodNames),
//      "GET",
//      "/system/connector-method-names",
//      "Get Connector Method Names",
//      s"""Get the list of all available connector method names.
//         |
//         |These are the method names that can be used in Method Routing configuration.
//         |
//         |## Data Source
//         |
//         |The data comes from **scanning the actual Scala connector code at runtime** using reflection, NOT from a database or configuration file.
//         |
//         |The endpoint:
//         |1. Reads the connector name from props (e.g., `connector=mapped`)
//         |2. Gets the connector instance (e.g., LocalMappedConnector, KafkaConnector, StarConnector)
//         |3. Uses Scala reflection to scan all public methods that override the base Connector trait
//         |4. Filters for valid connector methods (public, has parameters, overrides base trait)
//         |5. Returns the method names as a sorted list
//         |
//         |## Which Connector?
//         |
//         |Depends on your `connector` property:
//         |* `connector=mapped` → Returns methods from LocalMappedConnector
//         |* `connector=kafka_vSept2018` → Returns methods from KafkaConnector
//         |* `connector=star` → Returns methods from StarConnector
//         |* `connector=rest_vMar2019` → Returns methods from RestConnector
//         |
//         |## When Does It Change?
//         |
//         |The list only changes when:
//         |* Code is deployed with new/modified connector methods
//         |* The `connector` property is changed to point to a different connector
//         |
//         |## Performance
//         |
//         |This endpoint uses caching (default: 1 hour) because Scala reflection is expensive.
//         |Configure via: `getConnectorMethodNames.cache.ttl.seconds=3600`
//         |
//         |## Use Case
//         |
//         |Use this endpoint to discover which connector methods are available when configuring Method Routing.
//         |These method names are different from API endpoint operation IDs (which you get from /resource-docs).
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |CanGetSystemConnectorMethodNames entitlement is required.
//         |
//      """.stripMargin,
//      EmptyBody,
//      ConnectorMethodNamesJsonV600(List("getBank", "getBanks", "getUser", "getAccount", "makePayment", "getTransactions")),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagConnectorMethod, apiTagSystem, apiTagMethodRouting, apiTagApi),
//      Some(List(canGetSystemConnectorMethodNames))
//    )
//
//    lazy val getConnectorMethodNames: OBPEndpoint = {
//      case "system" :: "connector-method-names" :: Nil JsonGet _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            // Fetch connector method names with caching
//            methodNames <- Future {
//              /**
//               * Connector methods rarely change (only on deployment), so we cache for a long time.
//               */
//              val cacheKey = "getConnectorMethodNames"
//              val cacheTTL = APIUtil.getPropsAsIntValue("getConnectorMethodNames.cache.ttl.seconds", 3600)
//              Caching.memoizeSyncWithProvider(Some(cacheKey))(cacheTTL.seconds) {
//                val connectorName = APIUtil.getPropsValue("connector", "mapped")
//                val connector = code.bankconnectors.Connector.getConnectorInstance(connectorName)
//                connector.callableMethods.keys.toList
//              }
//            }
//          } yield {
//            (JSONFactory600.createConnectorMethodNamesJson(methodNames), HttpCode.`200`(callContext))
//          }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getConnectors,
//      implementedInApiVersion,
//      nameOf(getConnectors),
//      "GET",
//      "/system/connectors",
//      "Get Connectors",
//      s"""Get the list of connectors and their availability for method routing.
//         |
//         |Returns a sorted list of all connectors with their availability status for use in Method Routing.
//         |
//         |## Response Fields
//         |
//         |* **connector_name** - The name of the connector
//         |* **is_available_in_method_routing** - Whether this connector can be used in Method Routing configuration.
//         |  This depends on the `connector` and `starConnector_supported_types` props settings.
//         |
//         |## Available Connectors
//         |
//         |The OBP-API supports multiple connectors for accessing banking data:
//         |
//         |* **mapped** - Local database connector using Lift Mapper ORM
//         |* **akka_vDec2018** - Akka-based connector for remote banking systems
//         |* **rest_vMar2019** - REST connector for external APIs
//         |* **stored_procedure_vDec2019** - Stored procedure connector for database-native operations
//         |* **rabbitmq_vOct2024** - RabbitMQ message queue connector
//         |* **cardano_vJun2025** - Cardano blockchain connector
//         |* **ethereum_vSept2025** - Ethereum blockchain connector
//         |* **star** - Star connector (special routing connector)
//         |* **proxy** - Proxy connector (for testing)
//         |* **internal** - Internal dynamic connector
//         |
//         |## Use Case
//         |
//         |Use this endpoint to discover which connectors are available when configuring Method Routing.
//         |A connector is available for method routing if it matches the `connector` prop setting,
//         |or if `connector=star` and the connector is listed in `starConnector_supported_types`.
//         |
//         |Authentication is Optional.
//         |
//      """.stripMargin,
//      EmptyBody,
//      ConnectorsJsonV600(List(
//        ConnectorInfoJsonV600("mapped", true),
//        ConnectorInfoJsonV600("akka_vDec2018", false),
//        ConnectorInfoJsonV600("rest_vMar2019", true),
//        ConnectorInfoJsonV600("stored_procedure_vDec2019", false)
//      )),
//      List(
//        UnknownError
//      ),
//      List(apiTagConnector, apiTagSystem, apiTagApi),
//      None
//    )
//
//    lazy val getConnectors: OBPEndpoint = {
//      case "system" :: "connectors" :: Nil JsonGet _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- anonymousAccess(cc)
//          } yield {
//            // Get the connector names from the Connector object's nameToConnector map
//            // Also include "star" which is handled separately in getConnectorInstance
//            val connectorNames = code.bankconnectors.Connector.nameToConnector.keys.toList :+ "star"
//            val connectorInfos = connectorNames.map { name =>
//              ConnectorInfoJsonV600(
//                connector_name = name,
//                is_available_in_method_routing = NewStyle.function.getConnectorByName(name).isDefined
//              )
//            }
//            (JSONFactory600.createConnectorsJson(connectorInfos), HttpCode.`200`(callContext))
//          }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getTopAPIs,
//      implementedInApiVersion,
//      nameOf(getTopAPIs),
//      "GET",
//      "/management/metrics/top-apis",
//      "Get Top APIs",
//      s"""Get metrics about the most popular APIs. e.g.: total count, response time (in ms), etc.
//         |
//         |This v6.0.0 version includes the **operation_id** field which uniquely identifies each API endpoint
//         |across different API standards (OBP, Berlin Group, UK Open Banking, etc.).
//         |
//         |Should be able to filter on the following fields:
//         |
//         |eg: /management/metrics/top-apis?from_date=$epochTimeString&to_date=$DefaultToDateString&consumer_id=5
//         |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
//         |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
//         |&verb=GET&anon=false&app_name=MapperPostman
//         |&exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
//         |
//         |1 from_date (defaults to one year ago): eg:from_date=$epochTimeString
//         |
//         |2 to_date (defaults to the current date) eg:to_date=$DefaultToDateString
//         |
//         |3 consumer_id (if null ignore)
//         |
//         |4 user_id (if null ignore)
//         |
//         |5 anon (if null ignore) only support two values: true (return where user_id is null) or false (return where user_id is not null)
//         |
//         |6 url (if null ignore), note: can not contain '&'.
//         |
//         |7 app_name (if null ignore)
//         |
//         |8 implemented_by_partial_function (if null ignore)
//         |
//         |9 implemented_in_version (if null ignore)
//         |
//         |10 verb (if null ignore)
//         |
//         |11 correlation_id (if null ignore)
//         |
//         |12 duration (if null ignore) non digit chars will be silently omitted
//         |
//         |13 exclude_app_names (if null ignore). eg: &exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
//         |
//         |14 exclude_url_patterns (if null ignore). You can design your own SQL NOT LIKE pattern. eg: &exclude_url_patterns=%management/metrics%,%management/aggregate-metrics%
//         |
//         |15 exclude_implemented_by_partial_functions (if null ignore). eg: &exclude_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |CanReadMetrics entitlement is required.
//         |
//      """.stripMargin,
//      EmptyBody,
//      TopApisJsonV600(List(
//        TopApiJsonV600(1000, "getBanks", "v4.0.0", "OBPv4.0.0-getBanks"),
//        TopApiJsonV600(500, "getBank", "v4.0.0", "OBPv4.0.0-getBank"),
//        TopApiJsonV600(250, "getAccountList", "v1.3", "BGv1.3-getAccountList")
//      )),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidFilterParameterFormat,
//        GetTopApisError,
//        UnknownError
//      ),
//      List(apiTagMetric, apiTagApi),
//      Some(List(canReadMetrics))
//    )
//
//    lazy val getTopAPIs: OBPEndpoint = {
//      case "management" :: "metrics" :: "top-apis" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadMetrics, callContext)
//            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
//            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, callContext)
//            topApis <- APIMetrics.apiMetrics.vend.getTopApisFuture(obpQueryParams) map {
//              unboxFullOrFail(_, callContext, GetTopApisError)
//            }
//          } yield {
//            // Build lookup map from partialFunctionName -> operationId
//            // This handles OBP, Berlin Group, UK Open Banking, and other standards correctly
//            val allDocs = APIUtil.getAllResourceDocs
//            val lookupMap: Map[String, String] = allDocs.map { doc =>
//              doc.partialFunctionName -> doc.operationId
//            }.toMap
//
//            // Convert TopApi to TopApiJsonV600 with operation_id
//            val topApisWithOperationId = topApis.map { api =>
//              val operationId = lookupMap.getOrElse(
//                api.ImplementedByPartialFunction,
//                scala.util.Try(APIUtil.buildOperationId(ApiVersionUtils.valueOf(api.implementedInVersion), api.ImplementedByPartialFunction))
//                  .getOrElse(s"${api.implementedInVersion}-${api.ImplementedByPartialFunction}")
//              )
//              TopApiJsonV600(
//                count = api.count,
//                implemented_by_partial_function = api.ImplementedByPartialFunction,
//                implemented_in_version = api.implementedInVersion,
//                operation_id = operationId
//              )
//            }
//            (JSONFactory600.createTopApisJsonV600(topApisWithOperationId), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getScannedApiVersions,
//      implementedInApiVersion,
//      nameOf(getScannedApiVersions),
//      "GET",
//      "/api/versions",
//      "Get Scanned API Versions",
//      s"""Get all scanned API versions available in this codebase.
//         |
//         |This endpoint returns all API versions that have been discovered/scanned, along with their active status.
//         |
//         |**Response Fields:**
//         |
//         |* `url_prefix`: The URL prefix for the version (e.g., "obp", "berlin-group", "open-banking")
//         |* `api_standard`: The API standard name (e.g., "OBP", "BG", "UK", "STET")
//         |* `api_short_version`: The version number (e.g., "v4.0.0", "v1.3")
//         |* `fully_qualified_version`: The fully qualified version combining standard and version (e.g., "OBPv4.0.0", "BGv1.3")
//         |* `is_active`: Boolean indicating if the version is currently enabled and accessible
//         |
//         |**Active Status:**
//         |
//         |* `is_active=true`: Version is enabled and can be accessed via its URL prefix
//         |* `is_active=false`: Version is scanned but disabled (via `api_disabled_versions` props)
//         |
//         |**Use Cases:**
//         |
//         |* Discover what API versions are available in the codebase
//         |* Check which versions are currently enabled
//         |* Verify that disabled versions configuration is working correctly
//         |* API documentation and discovery
//         |
//         |**Note:** This differs from v4.0.0's `/api/versions` endpoint which shows all scanned versions without is_active status.
//         |
//         |""",
//      EmptyBody,
//      ListResult(
//        "scanned_api_versions",
//        List(
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v1_2_1.toString, fully_qualified_version = ApiVersion.v1_2_1.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v1_3_0.toString, fully_qualified_version = ApiVersion.v1_3_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v1_4_0.toString, fully_qualified_version = ApiVersion.v1_4_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v2_0_0.toString, fully_qualified_version = ApiVersion.v2_0_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v2_1_0.toString, fully_qualified_version = ApiVersion.v2_1_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v2_2_0.toString, fully_qualified_version = ApiVersion.v2_2_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v3_0_0.toString, fully_qualified_version = ApiVersion.v3_0_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v3_1_0.toString, fully_qualified_version = ApiVersion.v3_1_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v4_0_0.toString, fully_qualified_version = ApiVersion.v4_0_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v5_0_0.toString, fully_qualified_version = ApiVersion.v5_0_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v5_1_0.toString, fully_qualified_version = ApiVersion.v5_1_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "obp", api_standard = "OBP", api_short_version = ApiVersion.v6_0_0.toString, fully_qualified_version = ApiVersion.v6_0_0.fullyQualifiedVersion, is_active = true),
//          ScannedApiVersionJsonV600(url_prefix = "berlin-group", api_standard = "BG", api_short_version = "v1.3", fully_qualified_version = "BGv1.3", is_active = false)
//        )
//      ),
//      List(
//        UnknownError
//      ),
//      List(apiTagDocumentation, apiTagApi),
//      Some(Nil)
//    )
//
//    lazy val getScannedApiVersions: OBPEndpoint = {
//      case "api" :: "versions" :: Nil JsonGet _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        Future {
//          val versions: List[ScannedApiVersionJsonV600] =
//            ApiVersion.allScannedApiVersion.asScala.toList
//              .filter(version => version.urlPrefix.trim.nonEmpty)
//              .map { version =>
//                ScannedApiVersionJsonV600(
//                  url_prefix = version.urlPrefix,
//                  api_standard = version.apiStandard,
//                  api_short_version = version.apiShortVersion,
//                  fully_qualified_version = version.fullyQualifiedVersion,
//                  is_active = versionIsAllowed(version)
//                )
//              }
//          (
//            ListResult("scanned_api_versions", versions),
//            HttpCode.`200`(cc.callContext)
//          )
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createCustomer,
//      implementedInApiVersion,
//      nameOf(createCustomer),
//      "POST",
//      "/banks/BANK_ID/customers",
//      "Create Customer",
//      s"""
//         |The Customer resource stores the customer number, legal name, email, phone number, date of birth, relationship status,
//         |education attained, a url for a profile image, KYC status, credit rating, credit limit, and other customer information.
//         |
//         |**Required Fields:**
//         |- legal_name: The customer's full legal name
//         |- mobile_phone_number: The customer's mobile phone number
//         |
//         |**Optional Fields:**
//         |- customer_number: If not provided, a random number will be generated
//         |- email: Customer's email address
//         |- face_image: Customer's face image (url and date)
//         |- date_of_birth: Customer's date of birth in YYYY-MM-DD format
//         |- relationship_status: Customer's relationship status
//         |- dependants: Number of dependants (must match the length of dob_of_dependants array)
//         |- dob_of_dependants: Array of dependant birth dates in YYYY-MM-DD format
//         |- credit_rating: Customer's credit rating (rating and source)
//         |- credit_limit: Customer's credit limit (currency and amount)
//         |- highest_education_attained: Customer's highest education level
//         |- employment_status: Customer's employment status
//         |- kyc_status: Know Your Customer verification status (true/false). Default: false
//         |- last_ok_date: Last verification date
//         |- title: Customer's title (e.g., Mr., Mrs., Dr.)
//         |- branch_id: Associated branch identifier
//         |- name_suffix: Customer's name suffix (e.g., Jr., Sr.)
//         |- customer_type: Type of customer - INDIVIDUAL (default), CORPORATE, or SUBSIDIARY
//         |- parent_customer_id: For SUBSIDIARY customers, the customer_id of the parent CORPORATE customer
//         |
//         |**Date Format:**
//         |In v6.0.0, date_of_birth and dob_of_dependants must be provided in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
//         |The dates are strictly validated and must be valid calendar dates.
//         |Dates are stored with time set to midnight (00:00:00) UTC for consistency.
//         |
//         |**Validations:**
//         |- customer_number cannot contain `::::` characters
//         |- customer_number must be unique for the bank
//         |- The number of dependants must equal the length of the dob_of_dependants array
//         |- date_of_birth must be in valid YYYY-MM-DD format if provided
//         |- Each date in dob_of_dependants must be in valid YYYY-MM-DD format
//         |
//         |Note: If you need to set a specific customer number, use the Update Customer Number endpoint after this call.
//         |
//         |${userAuthenticationMessage(true)}
//         |""",
//      postCustomerJsonV600,
//      customerJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        InvalidJsonFormat,
//        InvalidJsonContent,
//        InvalidDateFormat,
//        InvalidCustomerType,
//        ParentCustomerNotFound,
//        CustomerNumberAlreadyExists,
//        UserNotFoundById,
//        CustomerAlreadyExistsForUser,
//        CreateConsumerError,
//        UnknownError
//      ),
//      List(apiTagCustomer, apiTagPerson),
//      Some(List(canCreateCustomer,canCreateCustomerAtAnyBank))
//    )
//    lazy val createCustomer : OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCustomerJsonV600 ", 400, cc.callContext) {
//              json.extract[PostCustomerJsonV600]
//            }
//            _ <- Helper.booleanToFuture(failMsg =  InvalidJsonContent + s" The field dependants(${postedData.dependants.getOrElse(0)}) not equal the length(${postedData.dob_of_dependants.getOrElse(Nil).length }) of dob_of_dependants array", 400, cc.callContext) {
//              postedData.dependants.getOrElse(0) == postedData.dob_of_dependants.getOrElse(Nil).length
//            }
//
//            // Validate and parse date_of_birth (YYYY-MM-DD format)
//            dateOfBirth <- Future {
//              postedData.date_of_birth.map { dateStr =>
//                try {
//                  val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
//                  formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
//                  formatter.setLenient(false)
//                  formatter.parse(dateStr)
//                } catch {
//                  case _: Exception =>
//                    throw new Exception(s"$InvalidJsonFormat date_of_birth must be in YYYY-MM-DD format (e.g., 1990-05-15), got: $dateStr")
//                }
//              }.orNull
//            }
//
//            // Validate and parse dob_of_dependants (YYYY-MM-DD format)
//            dobOfDependants <- Future {
//              postedData.dob_of_dependants.getOrElse(Nil).map { dateStr =>
//                try {
//                  val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
//                  formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
//                  formatter.setLenient(false)
//                  formatter.parse(dateStr)
//                } catch {
//                  case _: Exception =>
//                    throw new Exception(s"$InvalidJsonFormat dob_of_dependants must contain dates in YYYY-MM-DD format (e.g., 2010-03-20), got: $dateStr")
//                }
//              }
//            }
//
//            customerNumber = postedData.customer_number.getOrElse(Random.nextInt(Integer.MAX_VALUE).toString)
//
//            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat customer_number can not contain `::::` characters", cc=cc.callContext) {
//              !`checkIfContains::::` (customerNumber)
//            }
//            (_, callContext) <- NewStyle.function.checkCustomerNumberAvailable(bankId, customerNumber, cc.callContext)
//
//            customerType = postedData.customer_type.getOrElse("INDIVIDUAL")
//            _ <- Helper.booleanToFuture(failMsg = InvalidCustomerType, 400, callContext) {
//              List("INDIVIDUAL", "CORPORATE", "SUBSIDIARY").contains(customerType)
//            }
//
//            parentCustomerIdValue = postedData.parent_customer_id.getOrElse("")
//            _ <- if (parentCustomerIdValue.nonEmpty) {
//              NewStyle.function.getCustomerByCustomerId(parentCustomerIdValue, callContext).map(_ => ())
//            } else {
//              Future.successful(())
//            }
//
//            (customer, callContext) <- NewStyle.function.createCustomerC2(
//              bankId,
//              postedData.legal_name,
//              customerNumber,
//              postedData.mobile_phone_number,
//              postedData.email.getOrElse(""),
//              CustomerFaceImage(
//                postedData.face_image.map(_.date).getOrElse(null),
//                postedData.face_image.map(_.url).getOrElse("")
//              ),
//              dateOfBirth,
//              postedData.relationship_status.getOrElse(""),
//              postedData.dependants.getOrElse(0),
//              dobOfDependants,
//              postedData.highest_education_attained.getOrElse(""),
//              postedData.employment_status.getOrElse(""),
//              postedData.kyc_status.getOrElse(false),
//              postedData.last_ok_date.getOrElse(null),
//              postedData.credit_rating.map(i => CreditRating(i.rating, i.source)),
//              postedData.credit_limit.map(i => CreditLimit(i.currency, i.amount)),
//              postedData.title.getOrElse(""),
//              postedData.branch_id.getOrElse(""),
//              postedData.name_suffix.getOrElse(""),
//              customerType,
//              parentCustomerIdValue,
//              callContext,
//            )
//          } yield {
//            (JSONFactory600.createCustomerJson(customer), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomerChildren,
//      implementedInApiVersion,
//      nameOf(getCustomerChildren),
//      "GET",
//      "/banks/BANK_ID/customers/CUSTOMER_ID/children",
//      "Get Customer Children",
//      s"""Get the child (subsidiary) customers of a parent customer.
//         |
//         |Returns a list of customers whose parent_customer_id matches the specified CUSTOMER_ID.
//         |This is useful for corporate banking where a corporate customer may have subsidiary customers.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      customerJSONsV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        CustomerNotFoundByCustomerId,
//        UnknownError
//      ),
//      List(apiTagCustomer),
//      Some(List(canGetCustomersAtOneBank))
//    )
//    lazy val getCustomerChildren: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "children" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
//            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
//            (children, callContext) <- NewStyle.function.getCustomersByParentCustomerId(bankId, customerId, callContext)
//          } yield {
//            (JSONFactory600.createCustomersJson(children), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomersAtAllBanks,
//      implementedInApiVersion,
//      nameOf(getCustomersAtAllBanks),
//      "GET",
//      "/customers",
//      "Get Customers at All Banks",
//      s"""Get Customers at All Banks.
//         |
//         |Returns a list of all customers across all banks.
//         |
//         |**Date Format:**
//         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
//         |
//         |**Query Parameters:**
//         |- limit: Maximum number of customers to return (optional)
//         |- offset: Number of customers to skip for pagination (optional)
//         |- sort_direction: Sort direction - ASC or DESC (optional)
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""",
//      EmptyBody,
//      customerJSONsV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserCustomerLinksNotFoundForUser,
//        UnknownError
//      ),
//      List(apiTagCustomer, apiTagUser),
//      Some(List(canGetCustomersAtAllBanks))
//    )
//
//    lazy val getCustomersAtAllBanks : OBPEndpoint = {
//      case "customers" :: Nil JsonGet _ => {
//        cc => {
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
//            (customers, callContext) <- NewStyle.function.getCustomersAtAllBanks(callContext, requestParams)
//          } yield {
//            (JSONFactory600.createCustomersJson(customers.sortBy(_.bankId)), HttpCode.`200`(callContext))
//          }
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomersByLegalName,
//      implementedInApiVersion,
//      nameOf(getCustomersByLegalName),
//      "POST",
//      "/banks/BANK_ID/customers/legal-name",
//      "Get Customers by Legal Name",
//      s"""Gets the Customers specified by Legal Name.
//         |
//         |Returns a list of customers that match the provided legal name.
//         |
//         |**Date Format:**
//         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""",
//      PostCustomerLegalNameJsonV510(legal_name = "John Smith"),
//      customerJSONsV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserCustomerLinksNotFoundForUser,
//        UnknownError
//      ),
//      List(apiTagCustomer, apiTagKyc),
//      Some(List(canGetCustomersAtOneBank))
//    )
//
//    lazy val getCustomersByLegalName: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customers" :: "legal-name" :: Nil JsonPost json -> _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (bank, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
//            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerLegalNameJsonV510 "
//            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              json.extract[PostCustomerLegalNameJsonV510]
//            }
//            (customers, callContext) <- NewStyle.function.getCustomersByCustomerLegalName(bank.bankId, postedData.legal_name, callContext)
//          } yield {
//            (JSONFactory600.createCustomersJson(customers), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomersAtOneBank,
//      implementedInApiVersion,
//      nameOf(getCustomersAtOneBank),
//      "GET",
//      "/banks/BANK_ID/customers",
//      "Get Customers at Bank",
//      s"""Get Customers at Bank.
//         |
//         |Returns a list of all customers at the specified bank.
//         |
//         |**Date Format:**
//         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
//         |
//         |**Query Parameters:**
//         |- limit: Maximum number of customers to return (optional)
//         |- offset: Number of customers to skip for pagination (optional)
//         |- sort_direction: Sort direction - ASC or DESC (optional)
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""",
//      EmptyBody,
//      customerJSONsV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserCustomerLinksNotFoundForUser,
//        UnknownError
//      ),
//      List(apiTagCustomer, apiTagUser),
//      Some(List(canGetCustomersAtOneBank))
//    )
//
//    lazy val getCustomersAtOneBank : OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonGet _ => {
//        cc => {
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
//            customers <- NewStyle.function.getCustomers(bankId, callContext, requestParams)
//          } yield {
//            (JSONFactory600.createCustomersJson(customers.sortBy(_.bankId)), HttpCode.`200`(callContext))
//          }
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomerByCustomerId,
//      implementedInApiVersion,
//      nameOf(getCustomerByCustomerId),
//      "GET",
//      "/banks/BANK_ID/customers/CUSTOMER_ID",
//      "Get Customer by CUSTOMER_ID",
//      s"""Gets the Customer specified by CUSTOMER_ID.
//         |
//         |**Date Format:**
//         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""",
//      EmptyBody,
//      customerWithAttributesJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UserCustomerLinksNotFoundForUser,
//        UnknownError
//      ),
//      List(apiTagCustomer),
//      Some(List(canGetCustomersAtOneBank)))
//
//    lazy val getCustomerByCustomerId : OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customers" :: customerId ::  Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
//            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
//            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
//              bankId,
//              CustomerId(customerId),
//              callContext: Option[CallContext])
//          } yield {
//            (JSONFactory600.createCustomerWithAttributesJson(customer, customerAttributes), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomerByCustomerNumber,
//      implementedInApiVersion,
//      nameOf(getCustomerByCustomerNumber),
//      "POST",
//      "/banks/BANK_ID/customers/customer-number",
//      "Get Customer by CUSTOMER_NUMBER",
//      s"""Gets the Customer specified by CUSTOMER_NUMBER.
//         |
//         |**Date Format:**
//         |In v6.0.0, date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD** (e.g., "1990-05-15", "2010-03-20").
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""",
//      postCustomerNumberJsonV310,
//      customerWithAttributesJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserCustomerLinksNotFoundForUser,
//        UnknownError
//      ),
//      List(apiTagCustomer, apiTagKyc),
//      Some(List(canGetCustomersAtOneBank))
//    )
//
//    lazy val getCustomerByCustomerNumber : OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customers" :: "customer-number" ::  Nil JsonPost  json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (bank, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
//            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerNumberJsonV310 "
//            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              json.extract[PostCustomerNumberJsonV310]
//            }
//            (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bank.bankId, callContext)
//            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
//              bankId,
//              CustomerId(customer.customerId),
//              callContext: Option[CallContext])
//          } yield {
//            (JSONFactory600.createCustomerWithAttributesJson(customer, customerAttributes), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // Retail Customer Endpoints
//
//    staticResourceDocs += ResourceDoc(
//      createRetailCustomer,
//      implementedInApiVersion,
//      nameOf(createRetailCustomer),
//      "POST",
//      "/banks/BANK_ID/retail-customers",
//      "Create Retail Customer",
//      s"""Create a retail (individual) customer.
//         |
//         |This endpoint is specifically for creating individual/retail customers.
//         |The customer_type will be automatically set to INDIVIDUAL.
//         |
//         |**Required Fields:**
//         |- legal_name: The customer's full legal name
//         |- mobile_phone_number: The customer's mobile phone number
//         |
//         |**Optional Fields:**
//         |- customer_number: If not provided, a random number will be generated
//         |- email, face_image, date_of_birth, relationship_status, dependants, dob_of_dependants
//         |- credit_rating, credit_limit, highest_education_attained, employment_status
//         |- kyc_status, last_ok_date, title, branch_id, name_suffix
//         |
//         |**Date Format:**
//         |date_of_birth and dob_of_dependants must be in ISO 8601 date format: **YYYY-MM-DD**
//         |
//         |**Validations:**
//         |- customer_number cannot contain `::::` characters
//         |- customer_number must be unique for the bank
//         |- The number of dependants must equal the length of the dob_of_dependants array
//         |
//         |Authentication is Required
//         |""",
//      postRetailCustomerJsonV600,
//      customerJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        InvalidJsonFormat,
//        InvalidJsonContent,
//        InvalidDateFormat,
//        CustomerNumberAlreadyExists,
//        UserNotFoundById,
//        CustomerAlreadyExistsForUser,
//        CreateConsumerError,
//        UnknownError
//      ),
//      List(apiTagRetailCustomer, apiTagCustomer),
//      Some(List(canCreateCustomer, canCreateCustomerAtAnyBank))
//    )
//    lazy val createRetailCustomer: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "retail-customers" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostRetailCustomerJsonV600 ", 400, cc.callContext) {
//              json.extract[PostRetailCustomerJsonV600]
//            }
//            _ <- Helper.booleanToFuture(failMsg = InvalidJsonContent + s" The field dependants(${postedData.dependants.getOrElse(0)}) not equal the length(${postedData.dob_of_dependants.getOrElse(Nil).length}) of dob_of_dependants array", 400, cc.callContext) {
//              postedData.dependants.getOrElse(0) == postedData.dob_of_dependants.getOrElse(Nil).length
//            }
//            dateOfBirth <- Future {
//              postedData.date_of_birth.map { dateStr =>
//                try {
//                  val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
//                  formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
//                  formatter.setLenient(false)
//                  formatter.parse(dateStr)
//                } catch {
//                  case _: Exception =>
//                    throw new Exception(s"$InvalidJsonFormat date_of_birth must be in YYYY-MM-DD format (e.g., 1990-05-15), got: $dateStr")
//                }
//              }.orNull
//            }
//            dobOfDependants <- Future {
//              postedData.dob_of_dependants.getOrElse(Nil).map { dateStr =>
//                try {
//                  val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
//                  formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
//                  formatter.setLenient(false)
//                  formatter.parse(dateStr)
//                } catch {
//                  case _: Exception =>
//                    throw new Exception(s"$InvalidJsonFormat dob_of_dependants must contain dates in YYYY-MM-DD format (e.g., 2010-03-20), got: $dateStr")
//                }
//              }
//            }
//            customerNumber = postedData.customer_number.getOrElse(Random.nextInt(Integer.MAX_VALUE).toString)
//            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat customer_number can not contain `::::` characters", cc = cc.callContext) {
//              !`checkIfContains::::` (customerNumber)
//            }
//            (_, callContext) <- NewStyle.function.checkCustomerNumberAvailable(bankId, customerNumber, cc.callContext)
//            (customer, callContext) <- NewStyle.function.createCustomerC2(
//              bankId,
//              postedData.legal_name,
//              customerNumber,
//              postedData.mobile_phone_number,
//              postedData.email.getOrElse(""),
//              CustomerFaceImage(
//                postedData.face_image.map(_.date).getOrElse(null),
//                postedData.face_image.map(_.url).getOrElse("")
//              ),
//              dateOfBirth,
//              postedData.relationship_status.getOrElse(""),
//              postedData.dependants.getOrElse(0),
//              dobOfDependants,
//              postedData.highest_education_attained.getOrElse(""),
//              postedData.employment_status.getOrElse(""),
//              postedData.kyc_status.getOrElse(false),
//              postedData.last_ok_date.getOrElse(null),
//              postedData.credit_rating.map(i => CreditRating(i.rating, i.source)),
//              postedData.credit_limit.map(i => CreditLimit(i.currency, i.amount)),
//              postedData.title.getOrElse(""),
//              postedData.branch_id.getOrElse(""),
//              postedData.name_suffix.getOrElse(""),
//              "INDIVIDUAL",
//              "",
//              callContext,
//            )
//          } yield {
//            (JSONFactory600.createCustomerJson(customer), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getRetailCustomersAtOneBank,
//      implementedInApiVersion,
//      nameOf(getRetailCustomersAtOneBank),
//      "GET",
//      "/banks/BANK_ID/retail-customers",
//      "Get Retail Customers at Bank",
//      s"""Get Retail (Individual) Customers at Bank.
//         |
//         |Returns a list of customers with customer_type INDIVIDUAL at the specified bank.
//         |
//         |**Date Format:**
//         |date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD**
//         |
//         |**Query Parameters:**
//         |- limit: Maximum number of customers to return (optional)
//         |- offset: Number of customers to skip for pagination (optional)
//         |- sort_direction: Sort direction - ASC or DESC (optional)
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      customerJSONsV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagRetailCustomer, apiTagCustomer),
//      Some(List(canGetCustomersAtOneBank))
//    )
//    lazy val getRetailCustomersAtOneBank: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "retail-customers" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit", "offset", "sort_direction"), cc.callContext)
//            (customers, callContext) <- NewStyle.function.getCustomersByCustomerTypes(bankId, List("INDIVIDUAL"), callContext, requestParams)
//          } yield {
//            (JSONFactory600.createCustomersJson(customers), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getRetailCustomerByCustomerId,
//      implementedInApiVersion,
//      nameOf(getRetailCustomerByCustomerId),
//      "GET",
//      "/banks/BANK_ID/retail-customers/CUSTOMER_ID",
//      "Get Retail Customer by CUSTOMER_ID",
//      s"""Gets the Retail Customer specified by CUSTOMER_ID.
//         |
//         |Returns 404 if the customer exists but is not of type INDIVIDUAL.
//         |Use the generic /customers/CUSTOMER_ID endpoint for any customer type.
//         |
//         |**Date Format:**
//         |date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD**
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      customerWithAttributesJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        CustomerTypeMismatch,
//        UnknownError
//      ),
//      List(apiTagRetailCustomer, apiTagCustomer),
//      Some(List(canGetCustomersAtOneBank))
//    )
//    lazy val getRetailCustomerByCustomerId: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "retail-customers" :: customerId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
//            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
//            _ <- Helper.booleanToFuture(failMsg = CustomerTypeMismatch, 404, callContext) {
//              customer.customerType.contains("INDIVIDUAL")
//            }
//            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
//              bankId,
//              CustomerId(customerId),
//              callContext: Option[CallContext])
//          } yield {
//            (JSONFactory600.createCustomerWithAttributesJson(customer, customerAttributes), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // Corporate Customer Endpoints
//
//    staticResourceDocs += ResourceDoc(
//      createCorporateCustomer,
//      implementedInApiVersion,
//      nameOf(createCorporateCustomer),
//      "POST",
//      "/banks/BANK_ID/corporate-customers",
//      "Create Corporate Customer",
//      s"""Create a corporate customer.
//         |
//         |This endpoint is specifically for creating corporate customers.
//         |Individual-oriented fields (relationship_status, dependants, highest_education_attained, employment_status, name_suffix, date_of_birth, face_image, title) are not available on this endpoint.
//         |
//         |**Required Fields:**
//         |- legal_name: The corporate entity's legal name
//         |- mobile_phone_number: The corporate entity's phone number
//         |
//         |**Optional Fields:**
//         |- customer_number: If not provided, a random number will be generated
//         |- email, credit_rating, credit_limit, kyc_status, last_ok_date, branch_id
//         |- customer_type: CORPORATE (default) or SUBSIDIARY
//         |- parent_customer_id: For SUBSIDIARY customers, the customer_id of the parent customer
//         |
//         |**Validations:**
//         |- customer_number cannot contain `::::` characters
//         |- customer_number must be unique for the bank
//         |- customer_type must be CORPORATE or SUBSIDIARY
//         |- parent_customer_id must reference an existing customer if provided
//         |
//         |Authentication is Required
//         |""",
//      postCorporateCustomerJsonV600,
//      customerJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        InvalidJsonFormat,
//        InvalidCustomerType,
//        ParentCustomerNotFound,
//        CustomerNumberAlreadyExists,
//        UserNotFoundById,
//        CustomerAlreadyExistsForUser,
//        CreateConsumerError,
//        UnknownError
//      ),
//      List(apiTagCorporateCustomer, apiTagCustomer),
//      Some(List(canCreateCustomer, canCreateCustomerAtAnyBank))
//    )
//    lazy val createCorporateCustomer: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "corporate-customers" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCorporateCustomerJsonV600 ", 400, cc.callContext) {
//              json.extract[PostCorporateCustomerJsonV600]
//            }
//            customerNumber = postedData.customer_number.getOrElse(Random.nextInt(Integer.MAX_VALUE).toString)
//            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat customer_number can not contain `::::` characters", cc = cc.callContext) {
//              !`checkIfContains::::` (customerNumber)
//            }
//            (_, callContext) <- NewStyle.function.checkCustomerNumberAvailable(bankId, customerNumber, cc.callContext)
//            customerType = postedData.customer_type.getOrElse("CORPORATE")
//            _ <- Helper.booleanToFuture(failMsg = InvalidCustomerType + " For corporate customers, must be CORPORATE or SUBSIDIARY.", 400, callContext) {
//              List("CORPORATE", "SUBSIDIARY").contains(customerType)
//            }
//            parentCustomerIdValue = postedData.parent_customer_id.getOrElse("")
//            _ <- if (parentCustomerIdValue.nonEmpty) {
//              NewStyle.function.getCustomerByCustomerId(parentCustomerIdValue, callContext).map(_ => ())
//            } else {
//              Future.successful(())
//            }
//            (customer, callContext) <- NewStyle.function.createCustomerC2(
//              bankId,
//              postedData.legal_name,
//              customerNumber,
//              postedData.mobile_phone_number,
//              postedData.email.getOrElse(""),
//              CustomerFaceImage(null, ""), // not applicable for corporate
//              null, // date_of_birth - not applicable for corporate
//              "", // relationship_status - not applicable for corporate
//              0,  // dependants - not applicable for corporate
//              Nil, // dob_of_dependants - not applicable for corporate
//              "", // highest_education_attained - not applicable for corporate
//              "", // employment_status - not applicable for corporate
//              postedData.kyc_status.getOrElse(false),
//              postedData.last_ok_date.getOrElse(null),
//              postedData.credit_rating.map(i => CreditRating(i.rating, i.source)),
//              postedData.credit_limit.map(i => CreditLimit(i.currency, i.amount)),
//              "", // title - not applicable for corporate
//              postedData.branch_id.getOrElse(""),
//              "", // name_suffix - not applicable for corporate
//              customerType,
//              parentCustomerIdValue,
//              callContext,
//            )
//          } yield {
//            (JSONFactory600.createCustomerJson(customer), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCorporateCustomersAtOneBank,
//      implementedInApiVersion,
//      nameOf(getCorporateCustomersAtOneBank),
//      "GET",
//      "/banks/BANK_ID/corporate-customers",
//      "Get Corporate Customers at Bank",
//      s"""Get Corporate Customers at Bank.
//         |
//         |Returns a list of customers with customer_type CORPORATE or SUBSIDIARY at the specified bank.
//         |
//         |**Date Format:**
//         |date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD**
//         |
//         |**Query Parameters:**
//         |- limit: Maximum number of customers to return (optional)
//         |- offset: Number of customers to skip for pagination (optional)
//         |- sort_direction: Sort direction - ASC or DESC (optional)
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      customerJSONsV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagCorporateCustomer, apiTagCustomer),
//      Some(List(canGetCustomersAtOneBank))
//    )
//    lazy val getCorporateCustomersAtOneBank: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "corporate-customers" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit", "offset", "sort_direction"), cc.callContext)
//            (customers, callContext) <- NewStyle.function.getCustomersByCustomerTypes(bankId, List("CORPORATE", "SUBSIDIARY"), callContext, requestParams)
//          } yield {
//            (JSONFactory600.createCustomersJson(customers), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCorporateCustomerByCustomerId,
//      implementedInApiVersion,
//      nameOf(getCorporateCustomerByCustomerId),
//      "GET",
//      "/banks/BANK_ID/corporate-customers/CUSTOMER_ID",
//      "Get Corporate Customer by CUSTOMER_ID",
//      s"""Gets the Corporate Customer specified by CUSTOMER_ID.
//         |
//         |Returns 404 if the customer exists but is not of type CORPORATE or SUBSIDIARY.
//         |Use the generic /customers/CUSTOMER_ID endpoint for any customer type.
//         |
//         |**Date Format:**
//         |date_of_birth and dob_of_dependants are returned in ISO 8601 date format: **YYYY-MM-DD**
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      customerWithAttributesJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        CustomerTypeMismatch,
//        UnknownError
//      ),
//      List(apiTagCorporateCustomer, apiTagCustomer),
//      Some(List(canGetCustomersAtOneBank))
//    )
//    lazy val getCorporateCustomerByCustomerId: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "corporate-customers" :: customerId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
//            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
//            _ <- Helper.booleanToFuture(failMsg = CustomerTypeMismatch, 404, callContext) {
//              customer.customerType.exists(ct => List("CORPORATE", "SUBSIDIARY").contains(ct))
//            }
//            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
//              bankId,
//              CustomerId(customerId),
//              callContext: Option[CallContext])
//          } yield {
//            (JSONFactory600.createCustomerWithAttributesJson(customer, customerAttributes), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCorporateCustomerSubsidiaries,
//      implementedInApiVersion,
//      nameOf(getCorporateCustomerSubsidiaries),
//      "GET",
//      "/banks/BANK_ID/corporate-customers/CUSTOMER_ID/subsidiaries",
//      "Get Corporate Customer Subsidiaries",
//      s"""Get the subsidiary customers of a corporate customer.
//         |
//         |Returns a list of customers whose parent_customer_id matches the specified CUSTOMER_ID.
//         |The specified customer must be of type CORPORATE or SUBSIDIARY.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      customerJSONsV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        CustomerNotFoundByCustomerId,
//        CustomerTypeMismatch,
//        UnknownError
//      ),
//      List(apiTagCorporateCustomer, apiTagCustomer),
//      Some(List(canGetCustomersAtOneBank))
//    )
//    lazy val getCorporateCustomerSubsidiaries: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "corporate-customers" :: customerId :: "subsidiaries" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
//            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
//            _ <- Helper.booleanToFuture(failMsg = CustomerTypeMismatch, 404, callContext) {
//              customer.customerType.exists(ct => List("CORPORATE", "SUBSIDIARY").contains(ct))
//            }
//            (children, callContext) <- NewStyle.function.getCustomersByParentCustomerId(bankId, customerId, callContext)
//          } yield {
//            (JSONFactory600.createCustomersJson(children), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getMetrics,
//      implementedInApiVersion,
//      nameOf(getMetrics),
//      "GET",
//      "/management/metrics",
//      "Get Metrics",
//      s"""Get API metrics rows. These are records of each REST API call.
//         |
//         |require CanReadMetrics role
//         |
//         |**NOTE: Automatic from_date Default**
//         |
//         |If you do not provide a `from_date` parameter, this endpoint will automatically set it to:
//         |**now - ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes ago**
//         |
//         |This prevents accidentally querying all metrics since Unix Epoch and ensures reasonable response times.
//         |For historical/reporting queries, always explicitly specify your desired `from_date`.
//         |
//         |**IMPORTANT: Smart Caching & Performance**
//         |
//         |This endpoint uses intelligent two-tier caching to optimize performance:
//         |
//         |**Stable Data Cache (Long TTL):**
//         |- Metrics older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600")} seconds (${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} minutes) are considered immutable/stable
//         |- These are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400")} seconds (${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours)
//         |- Used when your query's from_date is older than the stable boundary
//         |
//         |**Recent Data Cache (Short TTL):**
//         |- Recent metrics (within the stable boundary) are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds
//         |- Used when your query includes recent data or has no from_date
//         |
//         |**STRONGLY RECOMMENDED: Always specify from_date in your queries!**
//         |
//         |**Why from_date matters:**
//         |- Queries WITH from_date older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} mins → cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours (fast!)
//         |- Queries WITHOUT from_date → cached for only ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds (slower)
//         |
//         |**Examples:**
//         |- `from_date=2025-01-01T00:00:00.000Z` → Uses ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours cache (historical data)
//         |- `from_date=$DateWithMsExampleString` (recent date) → Uses ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds cache (recent data)
//         |- No from_date → **Automatically set to ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes ago** → Uses ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds cache (recent data)
//         |
//         |For best performance on historical/reporting queries, always include a from_date parameter!
//         |
//         |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/metrics
//         |
//         |You can filter by the following fields by applying url parameters
//         |
//         |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=50&offset=2
//         |
//         |1 from_date e.g.:from_date=$DateWithMsExampleString
//         |   **DEFAULT**: If not provided, automatically set to now - ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes (keeps queries in recent data zone)
//         |   **IMPORTANT**: Including from_date enables long-term caching for historical data queries!
//         |
//         |2 to_date e.g.:to_date=$DateWithMsExampleString Defaults to a far future date i.e. ${APIUtil.ToDateInFuture}
//         |
//         |3 limit (for pagination: defaults to 50)  eg:limit=200
//         |
//         |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
//         |
//         |5 sort_by (defaults to date field) eg: sort_by=date
//         |  possible values:
//         |    "url",
//         |    "date",
//         |    "username" (or "user_name" for backward compatibility),
//         |    "app_name",
//         |    "developer_email",
//         |    "implemented_by_partial_function",
//         |    "implemented_in_version",
//         |    "consumer_id",
//         |    "verb"
//         |
//         |6 direction (defaults to date desc) eg: direction=desc
//         |
//         |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=10000&offset=0&anon=false&app_name=TeatApp&implemented_in_version=v2.1.0&verb=POST&user_id=c7b6cb47-cb96-4441-8801-35b57456753a&username=susan.uk.29@example.com&consumer_id=78
//         |
//         |Other filters:
//         |
//         |7 consumer_id  (if null ignore)
//         |
//         |8 user_id (if null ignore)
//         |
//         |9 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
//         |
//         |10 url (if null ignore), note: can not contain '&'.
//         |
//         |11 app_name (if null ignore)
//         |
//         |12 implemented_by_partial_function (if null ignore),
//         |
//         |13 implemented_in_version (if null ignore)
//         |
//         |14 verb (if null ignore)
//         |
//         |15 correlation_id (if null ignore)
//         |
//         |16 duration (if null ignore) - Returns calls where duration > specified value (in milliseconds). Use this to find slow API calls. eg: duration=5000 returns calls taking more than 5 seconds
//         |
//      """.stripMargin,
//      EmptyBody,
//      metricsJsonV600,
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagMetric, apiTagApi),
//      Some(List(canReadMetrics)))
//
//    lazy val getMetrics: OBPEndpoint = {
//      case "management" :: "metrics" :: Nil JsonGet _ => {
//        cc => {
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canReadMetrics, callContext)
//            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
//            // If from_date is not provided, set it to now - (stable.boundary - 1 second)
//            // This ensures we get recent data with the shorter cache TTL
//            httpParamsWithDefault = {
//              val hasFromDate = httpParams.exists(p => p.name == "from_date" || p.name == "obp_from_date")
//              if (!hasFromDate) {
//                val stableBoundarySeconds = APIUtil.getPropsAsIntValue("MappedMetrics.stable.boundary.seconds", 600)
//                val defaultFromDate = new java.util.Date(System.currentTimeMillis() - ((stableBoundarySeconds - 1) * 1000L))
//                val dateStr = APIUtil.DateWithMsFormat.format(defaultFromDate)
//                HTTPParam("from_date", List(dateStr)) :: httpParams
//              } else {
//                httpParams
//              }
//            }
//            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParamsWithDefault, callContext)
//            metrics <- Future(APIMetrics.apiMetrics.vend.getAllMetrics(obpQueryParams))
//            _ <- Future {
//              if (metrics.isEmpty) {
//                logger.warn(s"getMetrics returned empty list. Query params: $obpQueryParams, URL: ${cc.url}")
//              }
//            }
//          } yield {
//            // Build lookup map from partialFunctionName -> operationId
//            val allDocs = APIUtil.getAllResourceDocs
//            val lookupMap: Map[String, String] = allDocs.map { doc =>
//              doc.partialFunctionName -> doc.operationId
//            }.toMap
//            (JSONFactory600.createMetricsJsonV600(metrics, lookupMap), HttpCode.`200`(callContext))
//          }
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAggregateMetrics,
//      implementedInApiVersion,
//      nameOf(getAggregateMetrics),
//      "GET",
//      "/management/aggregate-metrics",
//      "Get Aggregate Metrics",
//      s"""Returns aggregate metrics on api usage eg. total count, response time (in ms), etc.
//         |
//         |require CanReadAggregateMetrics role
//         |
//         |**NOTE: Automatic from_date Default**
//         |
//         |If you do not provide a `from_date` parameter, this endpoint will automatically set it to:
//         |**now - ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes ago**
//         |
//         |This prevents accidentally querying all metrics since Unix Epoch and ensures reasonable response times.
//         |For historical/reporting queries, always explicitly specify your desired `from_date`.
//         |
//         |**IMPORTANT: Smart Caching & Performance**
//         |
//         |This endpoint uses intelligent two-tier caching to optimize performance:
//         |
//         |**Stable Data Cache (Long TTL):**
//         |- Metrics older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600")} seconds (${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} minutes) are considered immutable/stable
//         |- These are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400")} seconds (${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours)
//         |- Used when your query's from_date is older than the stable boundary
//         |
//         |**Recent Data Cache (Short TTL):**
//         |- Recent metrics (within the stable boundary) are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds
//         |- Used when your query includes recent data or has no from_date
//         |
//         |**Why from_date matters:**
//         |- Queries WITH from_date older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} mins → cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours (fast!)
//         |- Queries WITHOUT from_date → cached for only ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds (slower)
//         |
//         |Should be able to filter on the following fields
//         |
//         |eg: /management/aggregate-metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&consumer_id=5
//         |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
//         |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
//         |&verb=GET&anon=false&app_name=MapperPostman
//         |&include_app_names=API-EXPLORER,API-Manager,SOFI,null&http_status_code=200
//         |
//         |**IMPORTANT: v6.0.0+ Breaking Change**
//         |
//         |This version does NOT support the old `exclude_*` parameters:
//         |-  `exclude_app_names` - NOT supported (returns error)
//         |-  `exclude_url_patterns` - NOT supported (returns error)
//         |-  `exclude_implemented_by_partial_functions` - NOT supported (returns error)
//         |
//         |Use `include_*` parameters instead (all optional):
//         |- `include_app_names` - Optional - include only these apps
//         |- `include_url_patterns` - Optional - include only URLs matching these patterns
//         |- `include_implemented_by_partial_functions` - Optional - include only these functions
//         |
//         |1 from_date e.g.:from_date=$DateWithMsExampleString
//         |   **DEFAULT**: If not provided, automatically set to now - ${(APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt - 1) / 60} minutes (keeps queries in recent data zone)
//         |   **IMPORTANT**: Including from_date enables long-term caching for historical data queries!
//         |
//         |2 to_date (defaults to the current date) eg:to_date=$DateWithMsExampleString
//         |
//         |3 consumer_id  (if null ignore)
//         |
//         |4 user_id (if null ignore)
//         |
//         |5 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
//         |
//         |6 url (if null ignore), note: can not contain '&'.
//         |
//         |7 app_name (if null ignore)
//         |
//         |8 implemented_by_partial_function (if null ignore)
//         |
//         |9 implemented_in_version (if null ignore)
//         |
//         |10 verb (if null ignore)
//         |
//         |11 correlation_id (if null ignore)
//         |
//         |12 include_app_names (if null ignore).eg: &include_app_names=API-EXPLORER,API-Manager,SOFI,null
//         |
//         |13 include_url_patterns (if null ignore).you can design you own SQL LIKE pattern. eg: &include_url_patterns=%management/metrics%,%management/aggregate-metrics%
//         |
//         |14 include_implemented_by_partial_functions (if null ignore).eg: &include_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics
//         |
//         |15 http_status_code (if null ignore) - Filter by HTTP status code. eg: http_status_code=200 returns only successful calls, http_status_code=500 returns server errors
//         |
//      """.stripMargin,
//      EmptyBody,
//      aggregateMetricsJSONV300,
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagMetric, apiTagAggregateMetrics),
//      Some(List(canReadAggregateMetrics)))
//
//    lazy val getAggregateMetrics: OBPEndpoint = {
//      case "management" :: "aggregate-metrics" :: Nil JsonGet _ => {
//        cc => {
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canReadAggregateMetrics, callContext)
//            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
//            // Reject old exclude_* parameters in v6.0.0+
//            _ <- Future {
//              val excludeParams = httpParams.filter(p =>
//                p.name == "exclude_app_names" ||
//                p.name == "exclude_url_patterns" ||
//                p.name == "exclude_implemented_by_partial_functions"
//              )
//              if (excludeParams.nonEmpty) {
//                val paramNames = excludeParams.map(_.name).mkString(", ")
//                throw new Exception(s"${ErrorMessages.ExcludeParametersNotSupported} Parameters found: [$paramNames]")
//              }
//            }
//            // If from_date is not provided, set it to now - (stable.boundary - 1 second)
//            // This ensures we get recent data with the shorter cache TTL
//            httpParamsWithDefault = {
//              val hasFromDate = httpParams.exists(p => p.name == "from_date" || p.name == "obp_from_date")
//              if (!hasFromDate) {
//                val stableBoundarySeconds = APIUtil.getPropsAsIntValue("MappedMetrics.stable.boundary.seconds", 600)
//                val defaultFromDate = new java.util.Date(System.currentTimeMillis() - ((stableBoundarySeconds - 1) * 1000L))
//                val dateStr = APIUtil.DateWithMsFormat.format(defaultFromDate)
//                HTTPParam("from_date", List(dateStr)) :: httpParams
//              } else {
//                httpParams
//              }
//            }
//            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParamsWithDefault, callContext)
//            aggregateMetrics <- APIMetrics.apiMetrics.vend.getAllAggregateMetricsFuture(obpQueryParams, true) map {
//              x => unboxFullOrFail(x, callContext, GetAggregateMetricsError)
//            }
//            _ <- Future {
//              if (aggregateMetrics.isEmpty) {
//                logger.warn(s"getAggregateMetrics returned empty list. Query params: $obpQueryParams, URL: ${cc.url}")
//              }
//            }
//          } yield {
//            (createAggregateMetricJson(aggregateMetrics), HttpCode.`200`(callContext))
//          }
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      directLoginEndpoint,
//      implementedInApiVersion,
//      nameOf(directLoginEndpoint),
//      "POST",
//      "/my/logins/direct",
//      "Direct Login",
//      s"""DirectLogin is a simple authentication flow. You POST your credentials (username, password, and consumer key)
//         |to the DirectLogin endpoint and receive a token in return.
//         |
//         |This is an alias to the DirectLogin endpoint that includes the standard API versioning prefix.
//         |
//         |This endpoint requires the following header:
//         |
//         |    DirectLogin: username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=YOUR_CONSUMER_KEY
//         |
//         |Note: You can also use the Authorization header (Authorization: DirectLogin username=...) but the DirectLogin header is preferred.
//         |
//         |The token returned can then be used in subsequent API calls using the header:
//         |
//         |    DirectLogin: token=YOUR_TOKEN
//         |
//         |""".stripMargin,
//      EmptyBody,
//      JSONFactory600.createTokenJSON("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJpYXQiOjE0NTU4OTQyNzYsImV4cCI6MTQ1NTg5Nzg3NiwiYXVkIjoib2JwLWFwaSIsInN1YiI6IjA2Zjc0YjUwLTA5OGYtNDYwNi1hOGNjLTBjNDc5MjAyNmI5ZCIsImNvbnN1bWVyX2tleSI6IjYwNGY3ZTAyNGQ5MWU2MzMwNGMzOGM0YzRmZjc0MjMwZGU5NDk4NTEwNjgxZWNjM2Q5MzViNWQ5MGEwOTI3ODciLCJyb2xlIjoiY2FuX2FjY2Vzc19hcGkifQ.f8xHvXP5fDxo5-LlfTj1OQS9oqHNZfFd7N-WkV2o4Cc"),
//      List(
//        InvalidDirectLoginParameters,
//        InvalidLoginCredentials,
//        InvalidConsumerCredentials,
//        UnknownError
//      ),
//      List(apiTagUser),
//      Some(List()))
//
//
//    lazy val directLoginEndpoint: OBPEndpoint = {
//      case "my" :: "logins" :: "direct" :: Nil JsonPost _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (httpCode: Int, message: String, userId: Long) <- DirectLogin.createTokenFuture(DirectLogin.getAllParameters)
//            _ <- Future { DirectLogin.grantEntitlementsToUseDynamicEndpointsInSpacesInDirectLogin(userId) }
//          } yield {
//            if (httpCode == 200) {
//              (JSONFactory600.createTokenJSON(message), HttpCode.`201`(cc.callContext))
//            } else {
//              unboxFullOrFail(Empty, None, message, httpCode)
//            }
//          }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      validateUserEmail,
//      implementedInApiVersion,
//      nameOf(validateUserEmail),
//      "POST",
//      "/users/email-validation",
//      "Validate User Email",
//      s"""Validate a user's email address using the JWT token sent via email.
//         |
//         |This is a self-service endpoint for users to confirm their email address as part of the sign-up process.
//         |
//         |When a user registers and email validation is enabled (authUser.skipEmailValidation=false),
//         |they receive an email containing a validation link with a signed JWT token.
//         |The user (or a client application) then calls this endpoint with that token to complete validation.
//         |
//         |This endpoint:
//         |- Verifies the JWT signature and checks expiry
//         |- Extracts the unique ID from the JWT subject
//         |- Sets the user's validated status to true
//         |- Resets the unique ID token (invalidating the link)
//         |- Grants default entitlements to the user
//         |
//         |**Important: This is a single-use token.** Once the email is validated, the token is invalidated.
//         |Any subsequent attempts to use the same token will return a 404 error (UserNotFoundByToken or UserAlreadyValidated).
//         |
//         |The token is a signed JWT with a configurable expiry (default: 1440 minutes / 24 hours).
//         |The server-side expiry can be configured with the `email_validation_token_expiry_minutes` property.
//         |
//         |For administrative validation (without an email token), see the Validate a User endpoint (PUT /management/users/USER_ID).
//         |
//         |${userAuthenticationMessage(false)}
//         |
//         |""".stripMargin,
//      JSONFactory600.ValidateUserEmailJsonV600(
//        token = "eyJhbGciOiJIUzI1NiJ9..."
//      ),
//      JSONFactory600.ValidateUserEmailResponseJsonV600(
//        user_id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
//        email = "user@example.com",
//        username = "username",
//        provider = "https://localhost:8080",
//        validated = true,
//        message = "Email validated successfully"
//      ),
//      List(
//        InvalidJsonFormat,
//        UserNotFoundByToken,
//        UserAlreadyValidated,
//        UnknownError
//      ),
//      List(apiTagUser),
//      Some(List())
//    )
//
//    lazy val validateUserEmail: OBPEndpoint = {
//      case "users" :: "email-validation" :: Nil JsonPost json -> _ =>
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ValidateUserEmailJsonV600 ", 400, cc.callContext) {
//              json.extract[JSONFactory600.ValidateUserEmailJsonV600]
//            }
//            token = postedData.token.trim
//            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat Token cannot be empty", cc = cc.callContext) {
//              token.nonEmpty
//            }
//            // Verify JWT signature and extract uniqueId from subject
//            uniqueId <- NewStyle.function.tryons(
//              s"$UserNotFoundByToken Invalid or expired validation token",
//              404,
//              cc.callContext
//            ) {
//              val signedJWT = com.nimbusds.jwt.SignedJWT.parse(token)
//              val expiration = signedJWT.getJWTClaimsSet.getExpirationTime
//              if (expiration == null || expiration.before(new java.util.Date())) {
//                throw new Exception("Token has expired")
//              }
//              if (!CertificateUtil.verifywtWithHmacProtection(token)) {
//                throw new Exception("Invalid token signature")
//              }
//              signedJWT.getJWTClaimsSet.getSubject
//            }
//            // Find user by unique ID from JWT
//            authUser <- Future {
//              code.model.dataAccess.AuthUser.findUserByValidationToken(uniqueId) match {
//                case Full(user) => Full(user)
//                case Empty => Empty
//                case f: net.liftweb.common.Failure => f
//              }
//            }
//            user <- NewStyle.function.tryons(s"$UserNotFoundByToken Invalid or expired validation token", 404, cc.callContext) {
//              authUser.openOrThrowException("User not found")
//            }
//            // Check if user is already validated
//            _ <- Helper.booleanToFuture(s"$UserAlreadyValidated User email is already validated", cc = cc.callContext) {
//              !user.validated.get
//            }
//            // Validate the user and reset the unique ID token
//            validatedUser <- Future {
//              code.model.dataAccess.AuthUser.validateAndResetToken(user)
//            }
//            // Grant default entitlements
//            _ <- Future {
//              code.model.dataAccess.AuthUser.grantDefaultEntitlementsToAuthUser(validatedUser)
//            }
//          } yield {
//            val response = JSONFactory600.ValidateUserEmailResponseJsonV600(
//              user_id = validatedUser.user.obj.map(_.userId).getOrElse(""),
//              email = validatedUser.email.get,
//              username = validatedUser.username.get,
//              provider = validatedUser.provider.get,
//              validated = validatedUser.validated.get,
//              message = "Email validated successfully"
//            )
//            (response, HttpCode.`200`(cc.callContext))
//          }
//    }
//
//    // ============================================ GROUP MANAGEMENT ============================================
//
//    staticResourceDocs += ResourceDoc(
//      createGroup,
//      implementedInApiVersion,
//      nameOf(createGroup),
//      "POST",
//      "/management/groups",
//      "Create Group",
//      s"""Create a new group of roles.
//         |
//         |Groups can be either:
//         |- System-level (bank_id = null) - requires CanCreateGroupAtAllBanks role
//         |- Bank-level (bank_id provided) - requires CanCreateGroupAtOneBank role
//         |
//         |A group contains a list of role names that can be assigned together.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      PostGroupJsonV600(
//        bank_id = Some("gh.29.uk"),
//        group_name = "Teller Group",
//        group_description = "Standard teller roles for branch operations",
//        list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction"),
//        is_enabled = true
//      ),
//      GroupJsonV600(
//        group_id = "group-id-123",
//        bank_id = Some("gh.29.uk"),
//        group_name = "Teller Group",
//        group_description = "Standard teller roles for branch operations",
//        list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction"),
//        is_enabled = true
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagGroup),
//      Some(List(canCreateGroupAtAllBanks, canCreateGroupAtOneBank))
//    )
//
//    lazy val createGroup: OBPEndpoint = {
//      case "management" :: "groups" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostGroupJsonV600", 400, callContext) {
//              json.extract[PostGroupJsonV600]
//            }
//            _ <- Helper.booleanToFuture(failMsg = s"${InvalidJsonFormat} bank_id and group_name cannot be empty", cc = callContext) {
//              postJson.group_name.nonEmpty
//            }
//            _ <- postJson.bank_id match {
//              case Some(bankId) if bankId.nonEmpty =>
//                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canCreateGroupAtOneBank :: canCreateGroupAtAllBanks :: Nil, callContext)
//              case _ =>
//                NewStyle.function.hasEntitlement("", u.userId, canCreateGroupAtAllBanks, callContext)
//            }
//            group <- Future {
//              code.group.GroupTrait.group.vend.createGroup(
//                postJson.bank_id.filter(_.nonEmpty),
//                postJson.group_name,
//                postJson.group_description,
//                postJson.list_of_roles,
//                postJson.is_enabled
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot create group", 400)
//            }
//          } yield {
//            val response = GroupJsonV600(
//              group_id = group.groupId,
//              bank_id = group.bankId,
//              group_name = group.groupName,
//              group_description = group.groupDescription,
//              list_of_roles = group.listOfRoles,
//              is_enabled = group.isEnabled
//            )
//            (response, HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getGroup,
//      implementedInApiVersion,
//      nameOf(getGroup),
//      "GET",
//      "/management/groups/GROUP_ID",
//      "Get Group",
//      s"""Get a group by its ID.
//         |
//         |Requires either:
//         |- CanGetGroupsAtAllBanks (for any group)
//         |- CanGetGroupsAtOneBank (for groups at specific bank)
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      GroupJsonV600(
//        group_id = "group-id-123",
//        bank_id = Some("gh.29.uk"),
//        group_name = "Teller Group",
//        group_description = "Standard teller roles for branch operations",
//        list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction"),
//        is_enabled = true
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagGroup),
//      Some(List(canGetGroupsAtAllBanks, canGetGroupsAtOneBank))
//    )
//
//    lazy val getGroup: OBPEndpoint = {
//      case "management" :: "groups" :: groupId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            group <- Future {
//              code.group.GroupTrait.group.vend.getGroup(groupId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
//            }
//            _ <- group.bankId match {
//              case Some(bankId) =>
//                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canGetGroupsAtOneBank :: canGetGroupsAtAllBanks :: Nil, callContext)
//              case None =>
//                NewStyle.function.hasEntitlement("", u.userId, canGetGroupsAtAllBanks, callContext)
//            }
//          } yield {
//            val response = GroupJsonV600(
//              group_id = group.groupId,
//              bank_id = group.bankId,
//              group_name = group.groupName,
//              group_description = group.groupDescription,
//              list_of_roles = group.listOfRoles,
//              is_enabled = group.isEnabled
//            )
//            (response, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getGroups,
//      implementedInApiVersion,
//      nameOf(getGroups),
//      "GET",
//      "/management/groups",
//      "Get Groups",
//      s"""Get all groups. Optionally filter by bank_id.
//         |
//         |Query parameters:
//         |- bank_id (optional): Filter groups by bank. Use "null" or omit for system-level groups.
//         |
//         |Requires either:
//         |- CanGetGroupsAtAllBanks (for any/all groups)
//         |- CanGetGroupsAtOneBank (for groups at specific bank with bank_id parameter)
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      GroupsJsonV600(
//        groups = List(
//          GroupJsonV600(
//            group_id = "group-id-123",
//            bank_id = Some("gh.29.uk"),
//            group_name = "Teller Group",
//            group_description = "Standard teller roles",
//            list_of_roles = List("CanGetCustomer", "CanGetAccount"),
//            is_enabled = true
//          )
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagGroup),
//      Some(List(canGetGroupsAtAllBanks, canGetGroupsAtOneBank))
//    )
//
//    lazy val getGroups: OBPEndpoint = {
//      case "management" :: "groups" :: Nil JsonGet req => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
//            bankIdParam = httpParams.find(_.name == "bank_id").flatMap(_.values.headOption)
//            bankIdFilter = bankIdParam match {
//              case Some("null") | Some("") => None
//              case Some(id) => Some(id)
//              case None => None
//            }
//            _ <- bankIdFilter match {
//              case Some(bankId) =>
//                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canGetGroupsAtOneBank :: canGetGroupsAtAllBanks :: Nil, callContext)
//              case None =>
//                NewStyle.function.hasEntitlement("", u.userId, canGetGroupsAtAllBanks, callContext)
//            }
//            groups <- bankIdFilter match {
//              case Some(bankId) =>
//                code.group.GroupTrait.group.vend.getGroupsByBankId(Some(bankId)) map {
//                  x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get groups", 400)
//                }
//              case None if bankIdParam.isDefined =>
//                code.group.GroupTrait.group.vend.getGroupsByBankId(None) map {
//                  x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get groups", 400)
//                }
//              case None =>
//                code.group.GroupTrait.group.vend.getAllGroups() map {
//                  x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get groups", 400)
//                }
//            }
//          } yield {
//            val response = GroupsJsonV600(
//              groups = groups.map(group =>
//                GroupJsonV600(
//                  group_id = group.groupId,
//                  bank_id = group.bankId,
//                  group_name = group.groupName,
//                  group_description = group.groupDescription,
//                  list_of_roles = group.listOfRoles,
//                  is_enabled = group.isEnabled
//                )
//              )
//            )
//            (response, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateGroup,
//      implementedInApiVersion,
//      nameOf(updateGroup),
//      "PUT",
//      "/management/groups/GROUP_ID",
//      "Update Group",
//      s"""Update a group. All fields are optional.
//         |
//         |Requires either:
//         |- CanUpdateGroupAtAllBanks (for any group)
//         |- CanUpdateGroupAtOneBank (for groups at specific bank)
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      PutGroupJsonV600(
//        group_name = Some("Updated Teller Group"),
//        group_description = Some("Updated description"),
//        list_of_roles = Some(List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction", "CanGetTransaction")),
//        is_enabled = Some(true)
//      ),
//      GroupJsonV600(
//        group_id = "group-id-123",
//        bank_id = Some("gh.29.uk"),
//        group_name = "Updated Teller Group",
//        group_description = "Updated description",
//        list_of_roles = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction", "CanGetTransaction"),
//        is_enabled = true
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagGroup),
//      Some(List(canUpdateGroupAtAllBanks, canUpdateGroupAtOneBank))
//    )
//
//    lazy val updateGroup: OBPEndpoint = {
//      case "management" :: "groups" :: groupId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutGroupJsonV600", 400, callContext) {
//              json.extract[PutGroupJsonV600]
//            }
//            existingGroup <- Future {
//              code.group.GroupTrait.group.vend.getGroup(groupId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
//            }
//            _ <- existingGroup.bankId match {
//              case Some(bankId) =>
//                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canUpdateGroupAtOneBank :: canUpdateGroupAtAllBanks :: Nil, callContext)
//              case None =>
//                NewStyle.function.hasEntitlement("", u.userId, canUpdateGroupAtAllBanks, callContext)
//            }
//            updatedGroup <- Future {
//              code.group.GroupTrait.group.vend.updateGroup(
//                groupId,
//                putJson.group_name,
//                putJson.group_description,
//                putJson.list_of_roles,
//                putJson.is_enabled
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot update group", 400)
//            }
//          } yield {
//            val response = GroupJsonV600(
//              group_id = updatedGroup.groupId,
//              bank_id = updatedGroup.bankId,
//              group_name = updatedGroup.groupName,
//              group_description = updatedGroup.groupDescription,
//              list_of_roles = updatedGroup.listOfRoles,
//              is_enabled = updatedGroup.isEnabled
//            )
//            (response, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createUser,
//      implementedInApiVersion,
//      nameOf(createUser),
//      "POST",
//      "/users",
//      "Create User (self-registration)",
//      s"""Creates OBP user.
//         | No authorisation required.
//         |
//         | Mimics current webform to Register.
//         |
//         | Requires username(email), password, first_name, last_name, and email.
//         |
//         | Validation checks performed:
//         | - Password must meet strong password requirements (InvalidStrongPasswordFormat error if not)
//         | - Username must be unique (409 error if username already exists)
//         | - All required fields must be present in valid JSON format
//         |
//         | Email validation behavior:
//         | - Controlled by property 'authUser.skipEmailValidation' (default: false)
//         | - When false: User is created with validated=false and a validation email is sent to the user's email address
//         | - The validation link is constructed using the `portal_external_url` property which must be set (currently: `${APIUtil.getPropsValue("portal_external_url", "not set")}`).
//         | - When true: User is created with validated=true and no validation email is sent
//         | - Default entitlements are granted immediately regardless of validation status
//         |
//         | Note: If email validation is required (skipEmailValidation=false), the user must click the validation link
//         | in the email before they can log in, even though entitlements are already granted.
//         |
//         |""",
//      createUserJsonV600,
//      userJsonV200,
//      List(InvalidJsonFormat, InvalidStrongPasswordFormat, DuplicateUsername, ExternalUserCheckFailed, "Error occurred during user creation.", UnknownError),
//      List(apiTagUser, apiTagOnboarding))
//
//    lazy val createUser: OBPEndpoint = {
//      case "users" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            // STEP 1: Extract and validate JSON structure
//            postedData <- NewStyle.function.tryons(ErrorMessages.InvalidJsonFormat, 400, cc.callContext) {
//              json.extract[code.api.v6_0_0.CreateUserJsonV600]
//            }
//
//            // STEP 2: Validate password strength
//            _ <- Helper.booleanToFuture(ErrorMessages.InvalidStrongPasswordFormat, 400, cc.callContext) {
//              fullPasswordValidation(postedData.password)
//            }
//
//            // STEP 3: Check username uniqueness (returns 409 Conflict if exists)
//            _ <- Helper.booleanToFuture(ErrorMessages.DuplicateUsername, 409, cc.callContext) {
//              code.model.dataAccess.AuthUser.find(net.liftweb.mapper.By(code.model.dataAccess.AuthUser.username, postedData.username)).isEmpty
//            }
//
//            // STEP 4: Create AuthUser object
//            userCreated <- Future {
//              code.model.dataAccess.AuthUser()
//                .firstName(postedData.first_name)
//                .lastName(postedData.last_name)
//                .username(postedData.username)
//                .email(postedData.email)
//                .password(postedData.password)
//                .validated(APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", defaultValue = false))
//            }
//
//            // STEP 5: Validate Lift field validators
//            _ <- Helper.booleanToFuture(ErrorMessages.InvalidJsonFormat+userCreated.validate.map(_.msg).mkString(";"), 400, cc.callContext) {
//              userCreated.validate.size == 0
//            }
//
//            // STEP 6: Save user to database
//            savedUser <- NewStyle.function.tryons(ErrorMessages.InvalidJsonFormat, 400, cc.callContext) {
//              userCreated.saveMe()
//            }
//
//            // STEP 7: Verify save was successful
//            _ <- Helper.booleanToFuture(s"$UnknownError Error occurred during user creation.", 400, cc.callContext) {
//              userCreated.saved_?
//            }
//          } yield {
//            // STEP 8: Send validation email (if required)
//            val skipEmailValidation = APIUtil.getPropsAsBoolValue("authUser.skipEmailValidation", defaultValue = false)
//            if (!skipEmailValidation) {
//              APIUtil.getPropsValue("portal_external_url") match {
//                case Full(portalUrl) =>
//                  // Create a JWT token with the uniqueId as subject and configurable expiry
//                  val expiryMinutes = APIUtil.getPropsAsIntValue("email_validation_token_expiry_minutes", 1440)
//                  val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
//                    .subject(savedUser.uniqueId.get)
//                    .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
//                    .issueTime(new java.util.Date())
//                    .build()
//                  val jwtToken = CertificateUtil.jwtWithHmacProtection(claimsSet)
//
//                  val emailValidationLink = portalUrl + "/user-validation?token=" + java.net.URLEncoder.encode(jwtToken, "UTF-8")
//
//                  val textContent = Some(s"Welcome! Please validate your account by clicking the following link: $emailValidationLink")
//                  val htmlContent = Some(s"<p>Welcome! Please validate your account by clicking the following link:</p><p><a href='$emailValidationLink'>$emailValidationLink</a></p>")
//                  val subjectContent = "Sign up confirmation"
//
//                  val emailContent = code.api.util.CommonsEmailWrapper.EmailContent(
//                    from = code.model.dataAccess.AuthUser.emailFrom,
//                    to = List(savedUser.email.get),
//                    bcc = code.model.dataAccess.AuthUser.bccEmail.toList,
//                    subject = subjectContent,
//                    textContent = textContent,
//                    htmlContent = htmlContent
//                  )
//
//                  code.api.util.CommonsEmailWrapper.sendHtmlEmail(emailContent)
//                case _ =>
//                  logger.error("portal_external_url is not set in props. Cannot send validation email.")
//              }
//            }
//
//            // STEP 9: Grant default entitlements
//            code.model.dataAccess.AuthUser.grantDefaultEntitlementsToAuthUser(savedUser)
//
//            // STEP 10: Return JSON response
//            val json = JSONFactory200.createUserJSONfromAuthUser(userCreated)
//            (json, HttpCode.`201`(cc.callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteEntitlement,
//      implementedInApiVersion,
//      nameOf(deleteEntitlement),
//      "DELETE",
//      "/entitlements/ENTITLEMENT_ID",
//      "Delete Entitlement",
//      s"""Delete Entitlement specified by ENTITLEMENT_ID
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |Requires the $canDeleteEntitlementAtAnyBank role.
//         |
//         |This endpoint is idempotent - if the entitlement does not exist, it returns 204 No Content.
//         |
//      """.stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        EntitlementCannotBeDeleted,
//        UnknownError
//      ),
//      List(apiTagRole, apiTagUser, apiTagEntitlement),
//      Some(List(canDeleteEntitlementAtAnyBank)))
//
//    lazy val deleteEntitlement: OBPEndpoint = {
//      case "entitlements" :: entitlementId :: Nil JsonDelete _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            // TODO: This role check may be redundant since role is already specified in ResourceDoc.
//            // See ideas/should_fix_role_docs.md for details on removing duplicate role checks.
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteEntitlementAtAnyBank, callContext)
//            entitlementBox <- Future(Entitlement.entitlement.vend.getEntitlementById(entitlementId))
//            _ <- entitlementBox match {
//              case Full(entitlement) =>
//                // Entitlement exists - delete it
//                Future(Entitlement.entitlement.vend.deleteEntitlement(Some(entitlement))) map {
//                  case Full(true) => Full(())
//                  case _ => ObpApiFailure(EntitlementCannotBeDeleted, 500, callContext)
//                }
//              case _ =>
//                // Entitlement not found - idempotent delete returns success
//                Future.successful(Full(()))
//            }
//          } yield {
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getRolesWithEntitlementCountsAtAllBanks,
//      implementedInApiVersion,
//      nameOf(getRolesWithEntitlementCountsAtAllBanks),
//      "GET",
//      "/management/roles-with-entitlement-counts",
//      "Get Roles with Entitlement Counts",
//      s"""Returns all available roles with the count of entitlements that use each role.
//         |
//         |This endpoint provides statistics about role usage across all banks by counting
//         |how many entitlements have been granted for each role.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |Requires the CanGetRolesWithEntitlementCountsAtAllBanks role.
//         |
//         |""",
//      EmptyBody,
//      RolesWithEntitlementCountsJsonV600(
//        roles = List(
//          RoleWithEntitlementCountJsonV600(
//            role = "CanGetCustomer",
//            requires_bank_id = true,
//            entitlement_count = 5
//          ),
//          RoleWithEntitlementCountJsonV600(
//            role = "CanGetBank",
//            requires_bank_id = false,
//            entitlement_count = 3
//          )
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagRole, apiTagEntitlement),
//      Some(List(canGetRolesWithEntitlementCountsAtAllBanks))
//    )
//
//    lazy val getRolesWithEntitlementCountsAtAllBanks: OBPEndpoint = {
//      case "management" :: "roles-with-entitlement-counts" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetRolesWithEntitlementCountsAtAllBanks, callContext)
//
//            // Get all available roles
//            allRoles = ApiRole.availableRoles.sorted
//
//            // Get entitlement counts for each role
//            rolesWithCounts <- Future.sequence {
//              allRoles.map { role =>
//                Entitlement.entitlement.vend.getEntitlementsByRoleFuture(role).map { entitlementsBox =>
//                  val count = entitlementsBox.map(_.length).getOrElse(0)
//                  (role, count)
//                }
//              }
//            }
//          } yield {
//            val json = JSONFactory600.createRolesWithEntitlementCountsJson(rolesWithCounts)
//            (json, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteGroup,
//      implementedInApiVersion,
//      nameOf(deleteGroup),
//      "DELETE",
//      "/management/groups/GROUP_ID",
//      "Delete Group",
//      s"""Delete a Group.
//         |
//         |Requires either:
//         |- CanDeleteGroupAtAllBanks (for any group)
//         |- CanDeleteGroupAtOneBank (for groups at specific bank)
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagGroup),
//      Some(List(canDeleteGroupAtAllBanks, canDeleteGroupAtOneBank))
//    )
//
//    lazy val deleteGroup: OBPEndpoint = {
//      case "management" :: "groups" :: groupId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            existingGroup <- Future {
//              code.group.GroupTrait.group.vend.getGroup(groupId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
//            }
//            _ <- existingGroup.bankId match {
//              case Some(bankId) =>
//                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canDeleteGroupAtOneBank :: canDeleteGroupAtAllBanks :: Nil, callContext)
//              case None =>
//                NewStyle.function.hasEntitlement("", u.userId, canDeleteGroupAtAllBanks, callContext)
//            }
//            deleted <- Future {
//              code.group.GroupTrait.group.vend.deleteGroup(groupId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot delete group", 400)
//            }
//          } yield {
//            (Full(deleted), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      addUserToGroup,
//      implementedInApiVersion,
//      nameOf(addUserToGroup),
//      "POST",
//      "/users/USER_ID/group-entitlements",
//      "Grant User Membership to Group Entitlements",
//      s"""Grant the User Group Entitlements.
//         |
//         |This endpoint creates entitlements for every Role in the Group. If the user
//         |already has a particular role at the same bank, that entitlement is skipped (not duplicated).
//         |
//         |Each entitlement created will have:
//         |- group_id set to the group ID
//         |- process set to "GROUP_MEMBERSHIP"
//         |
//         |**Response Fields:**
//         |- target_entitlements: All roles defined in the group (the complete list of entitlements that this group aims to grant)
//         |- entitlements_created: Roles that were newly created as entitlements during this operation
//         |- entitlements_skipped: Roles that the user already possessed, so no new entitlement was created
//         |
//         |Note: target_entitlements = entitlements_created + entitlements_skipped
//         |
//         |Requires either:
//         |- CanAddUserToGroupAtAllBanks (for any group)
//         |- CanAddUserToGroupAtOneBank (for groups at specific bank)
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      PostGroupMembershipJsonV600(
//        group_id = "group-id-123"
//      ),
//      AddUserToGroupResponseJsonV600(
//        group_id = "group-id-123",
//        user_id = "user-id-123",
//        bank_id = Some("gh.29.uk"),
//        group_name = "Teller Group",
//        target_entitlements = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction"),
//        entitlements_created = List("CanGetCustomer", "CanGetAccount"),
//        entitlements_skipped = List("CanCreateTransaction")
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagGroup, apiTagUser, apiTagEntitlement),
//      Some(List(canAddUserToGroupAtAllBanks, canAddUserToGroupAtOneBank))
//    )
//
//    lazy val addUserToGroup: OBPEndpoint = {
//      case "users" :: userId :: "group-entitlements" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostGroupMembershipJsonV600", 400, callContext) {
//              json.extract[PostGroupMembershipJsonV600]
//            }
//            (user, callContext) <- NewStyle.function.findByUserId(userId, callContext)
//            group <- Future {
//              code.group.GroupTrait.group.vend.getGroup(postJson.group_id)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
//            }
//            _ <- group.bankId match {
//              case Some(bankId) =>
//                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canAddUserToGroupAtOneBank :: canAddUserToGroupAtAllBanks :: Nil, callContext)
//              case None =>
//                NewStyle.function.hasEntitlement("", u.userId, canAddUserToGroupAtAllBanks, callContext)
//            }
//            _ <- Helper.booleanToFuture(failMsg = s"$UnknownError Group is not enabled", 400, callContext) {
//              group.isEnabled
//            }
//            // Get existing entitlements for this user
//            existingEntitlements <- Future {
//              Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
//            }
//            // Create entitlements for all roles in the group, tracking which were added vs already present
//            entitlementResults <- Future.sequence {
//              group.listOfRoles.map { roleName =>
//                Future {
//                  // Check if user already has this role at this bank
//                  val alreadyHasRole = existingEntitlements.toOption.exists(_.exists { ent =>
//                    ent.roleName == roleName && ent.bankId == group.bankId.getOrElse("")
//                  })
//
//                  if (!alreadyHasRole) {
//                    Entitlement.entitlement.vend.addEntitlement(
//                      group.bankId.getOrElse(""),
//                      userId,
//                      roleName,
//                      "manual",
//                      None,
//                      Some(postJson.group_id),
//                      Some("GROUP_MEMBERSHIP")
//                    )
//                    (roleName, true) // true means it was added
//                  } else {
//                    (roleName, false) // false means it was already present
//                  }
//                }
//              }
//            }
//            entitlementsAdded = entitlementResults.filter(_._2).map(_._1)
//            entitlementsAlreadyPresent = entitlementResults.filterNot(_._2).map(_._1)
//          } yield {
//            val response = AddUserToGroupResponseJsonV600(
//              group_id = group.groupId,
//              user_id = userId,
//              bank_id = group.bankId,
//              group_name = group.groupName,
//              target_entitlements = group.listOfRoles,
//              entitlements_created = entitlementsAdded,
//              entitlements_skipped = entitlementsAlreadyPresent
//            )
//            (response, HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getUserGroupMemberships,
//      implementedInApiVersion,
//      nameOf(getUserGroupMemberships),
//      "GET",
//      "/users/USER_ID/group-entitlements",
//      "Get User's Group Memberships",
//      s"""Get all groups a user is a member of.
//         |
//         |Returns groups where the user has entitlements with process = "GROUP_MEMBERSHIP".
//         |
//         |The response includes:
//         |- list_of_entitlements: entitlements the user currently has from this group membership
//         |
//         |Requires either:
//         |- CanGetUserGroupMembershipsAtAllBanks (for any user)
//         |- CanGetUserGroupMembershipsAtOneBank (for users at specific bank)
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      UserGroupMembershipsJsonV600(
//        group_entitlements = List(
//          UserGroupMembershipJsonV600(
//            group_id = "group-id-123",
//            user_id = "user-id-123",
//            bank_id = Some("gh.29.uk"),
//            group_name = "Teller Group",
//            list_of_entitlements = List("CanGetCustomer", "CanGetAccount", "CanCreateTransaction")
//          )
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagGroup, apiTagUser, apiTagEntitlement),
//      Some(List(canGetUserGroupMembershipsAtAllBanks, canGetUserGroupMembershipsAtOneBank))
//    )
//
//    lazy val getUserGroupMemberships: OBPEndpoint = {
//      case "users" :: userId :: "group-entitlements" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            (user, callContext) <- NewStyle.function.findByUserId(userId, callContext)
//            // Get all entitlements for this user that came from groups
//            entitlements <- Future {
//              Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
//            }
//            groupEntitlements = entitlements.toOption.getOrElse(List.empty).filter(_.process == Some("GROUP_MEMBERSHIP"))
//            // Get unique group IDs
//            groupIds = groupEntitlements.flatMap(_.groupId).distinct
//            // Check permissions for each bank
//            _ <- Future.sequence {
//              groupIds.flatMap { groupId =>
//                // Get the group to find its bank_id
//                code.group.GroupTrait.group.vend.getGroup(groupId).toOption.map { group =>
//                  group.bankId match {
//                    case Some(bankId) =>
//                      NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canGetUserGroupMembershipsAtOneBank :: canGetUserGroupMembershipsAtAllBanks :: Nil, callContext)
//                    case None =>
//                      NewStyle.function.hasEntitlement("", u.userId, canGetUserGroupMembershipsAtAllBanks, callContext)
//                  }
//                }
//              }
//            }
//            // Get full group details
//            groups <- Future.sequence {
//              groupIds.map { groupId =>
//                Future {
//                  code.group.GroupTrait.group.vend.getGroup(groupId)
//                }
//              }
//            }
//            validGroups = groups.flatten
//          } yield {
//            val memberships = validGroups.map { group =>
//              // Get entitlements for this user that came from this specific group
//              val groupSpecificEntitlements = groupEntitlements
//                .filter(_.groupId.contains(group.groupId))
//                .map(_.roleName)
//                .distinct
//
//              UserGroupMembershipJsonV600(
//                group_id = group.groupId,
//                user_id = userId,
//                bank_id = group.bankId,
//                group_name = group.groupName,
//                list_of_entitlements = groupSpecificEntitlements
//              )
//            }
//            (UserGroupMembershipsJsonV600(group_entitlements = memberships), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getGroupEntitlements,
//      implementedInApiVersion,
//      nameOf(getGroupEntitlements),
//      "GET",
//      "/management/groups/GROUP_ID/entitlements",
//      "Get Group Entitlements",
//      s"""Get all entitlements that have been granted from a specific group.
//         |
//         |This returns all entitlements where the group_id matches the specified GROUP_ID.
//         |
//         |Requires:
//         |- CanGetEntitlementsForAnyBank
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      GroupEntitlementsJsonV600(
//        entitlements = List(
//          GroupEntitlementJsonV600(
//            entitlement_id = "entitlement-id-123",
//            role_name = "CanGetCustomer",
//            bank_id = "gh.29.uk",
//            user_id = "user-id-123",
//            username = "susan.uk.29@example.com",
//            group_id = Some("group-id-123"),
//            process = Some("GROUP_MEMBERSHIP")
//          )
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagGroup, apiTagEntitlement),
//      Some(List(canGetEntitlementsForAnyBank))
//    )
//
//    lazy val getGroupEntitlements: OBPEndpoint = {
//      case "management" :: "groups" :: groupId :: "entitlements" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            // Verify the group exists
//            group <- Future {
//              code.group.GroupTrait.group.vend.getGroup(groupId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
//            }
//            // Get entitlements by group_id
//            groupEntitlements <- Entitlement.entitlement.vend.getEntitlementsByGroupId(groupId) map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get entitlements", 400)
//            }
//            // Get usernames for each entitlement
//            entitlementsWithUsernames <- Future.sequence {
//              groupEntitlements.map { ent =>
//                Users.users.vend.getUserByUserIdFuture(ent.userId).map { userBox =>
//                  val username = userBox.map(_.name).getOrElse("")
//                  GroupEntitlementJsonV600(
//                    entitlement_id = ent.entitlementId,
//                    role_name = ent.roleName,
//                    bank_id = ent.bankId,
//                    user_id = ent.userId,
//                    username = username,
//                    group_id = ent.groupId,
//                    process = ent.process
//                  )
//                }
//              }
//            }
//          } yield {
//            val entitlementCount = entitlementsWithUsernames.length
//            logger.info(s"getGroupEntitlements called for group_id: $groupId, returned $entitlementCount records")
//            (GroupEntitlementsJsonV600(entitlements = entitlementsWithUsernames), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      removeUserFromGroup,
//      implementedInApiVersion,
//      nameOf(removeUserFromGroup),
//      "DELETE",
//      "/users/USER_ID/group-entitlements/GROUP_ID",
//      "Remove User from Group",
//      s"""Remove a user from a group. This will delete all entitlements that were created by this group membership.
//         |
//         |Only removes entitlements with:
//         |- group_id matching GROUP_ID
//         |- process = "GROUP_MEMBERSHIP"
//         |
//         |Requires either:
//         |- CanRemoveUserFromGroupAtAllBanks (for any group)
//         |- CanRemoveUserFromGroupAtOneBank (for groups at specific bank)
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagGroup, apiTagUser, apiTagEntitlement),
//      Some(List(canRemoveUserFromGroupAtAllBanks, canRemoveUserFromGroupAtOneBank))
//    )
//
//    lazy val removeUserFromGroup: OBPEndpoint = {
//      case "users" :: userId :: "group-entitlements" :: groupId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            (user, callContext) <- NewStyle.function.findByUserId(userId, callContext)
//            group <- Future {
//              code.group.GroupTrait.group.vend.getGroup(groupId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Group not found", 404)
//            }
//            _ <- group.bankId match {
//              case Some(bankId) =>
//                NewStyle.function.hasAtLeastOneEntitlement(bankId, u.userId, canRemoveUserFromGroupAtOneBank :: canRemoveUserFromGroupAtAllBanks :: Nil, callContext)
//              case None =>
//                NewStyle.function.hasEntitlement("", u.userId, canRemoveUserFromGroupAtAllBanks, callContext)
//            }
//            // Get all entitlements for this user from this group
//            entitlements <- Future {
//              Entitlement.entitlement.vend.getEntitlementsByUserId(userId)
//            }
//            groupEntitlements = entitlements.toOption.getOrElse(List.empty).filter(e =>
//              e.groupId == Some(groupId) && e.process == Some("GROUP_MEMBERSHIP")
//            )
//            // Delete all entitlements from this group
//            _ <- Future.sequence {
//              groupEntitlements.map { entitlement =>
//                Future {
//                  Entitlement.entitlement.vend.deleteEntitlement(Full(entitlement))
//                }
//              }
//            }
//          } yield {
//            (Full(true), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getSystemViews,
//      implementedInApiVersion,
//      nameOf(getSystemViews),
//      "GET",
//      "/management/system-views",
//      "Get System Views",
//      s"""Get all system views.
//         |
//         |System views are predefined views that apply to all accounts, such as:
//         |- owner
//         |- accountant
//         |- auditor
//         |- standard
//         |
//         |Each view is returned with an `allowed_actions` array containing all permissions for that view.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ViewsJsonV600(List(
//        ViewJsonV600(
//          bank_id = "",
//          account_id = "",
//          view_id = "owner",
//          view_name = "Owner",
//          description = "The owner of the account",
//          metadata_view = "owner",
//          is_public = false,
//          is_system = true,
//          is_firehose = Some(false),
//          alias = "private",
//          hide_metadata_if_alias_used = false,
//          can_grant_access_to_views = List("owner"),
//          can_revoke_access_to_views = List("owner"),
//          allowed_actions = List("can_see_transaction_amount", "can_see_bank_account_balance")
//        )
//      )),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagSystemView, apiTagView),
//      Some(List(canGetSystemViews))
//    )
//
//    lazy val getSystemViews: OBPEndpoint = {
//      case "management" :: "system-views" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            views <- Views.views.vend.getSystemViews()
//          } yield {
//            (JSONFactory600.createViewsJsonV600(views), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getSystemViewById,
//      implementedInApiVersion,
//      nameOf(getSystemViewById),
//      "GET",
//      "/management/system-views/VIEW_ID",
//      "Get System View",
//      s"""Get a single system view by its ID.
//         |
//         |System views are predefined views that apply to all accounts, such as:
//         |- owner
//         |- accountant
//         |- auditor
//         |- standard
//         |
//         |The view is returned with an `allowed_actions` array containing all permissions for that view.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ViewJsonV600(
//        bank_id = "",
//        account_id = "",
//        view_id = "owner",
//        view_name = "Owner",
//        description = "The owner of the account. Has full privileges.",
//        metadata_view = "owner",
//        is_public = false,
//        is_system = true,
//        is_firehose = Some(false),
//        alias = "private",
//        hide_metadata_if_alias_used = false,
//        can_grant_access_to_views = List("owner", "accountant"),
//        can_revoke_access_to_views = List("owner", "accountant"),
//        allowed_actions = List(
//          "can_see_transaction_amount",
//          "can_see_bank_account_balance",
//          "can_add_comment",
//          "can_create_custom_view"
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        SystemViewNotFound,
//        UnknownError
//      ),
//      List(apiTagSystemView, apiTagView),
//      Some(List(canGetSystemViews))
//    )
//
//    lazy val getSystemViewById: OBPEndpoint = {
//      case "management" :: "system-views" :: viewId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            view <- ViewNewStyle.systemView(ViewId(viewId), callContext)
//          } yield {
//            (JSONFactory600.createViewJsonV600(view), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
////    staticResourceDocs += ResourceDoc(
////      getSystemView,
////      implementedInApiVersion,
////      nameOf(getSystemView),
////      "GET",
////      "/system-views/VIEW_ID",
////      "Get System View",
////      s"""Get a single system view by its ID.
////         |
////         |System views are predefined views that apply to all accounts, such as:
////         |- owner
////         |- accountant
////         |- auditor
////         |- standard
////         |
////         |This endpoint returns the view with an `allowed_actions` array containing all permissions.
////         |
////         |${userAuthenticationMessage(true)}
////         |
////         |""".stripMargin,
////      EmptyBody,
////      ViewJsonV600(
////        view_id = "owner",
////        view_name = "Owner",
////        description = "The owner of the account. Has full privileges.",
////        metadata_view = "owner",
////        is_public = false,
////        is_system = true,
////        is_firehose = Some(false),
////        alias = "private",
////        hide_metadata_if_alias_used = false,
////        can_grant_access_to_views = List("owner", "accountant"),
////        can_revoke_access_to_views = List("owner", "accountant"),
////        allowed_actions = List(
////          "can_see_transaction_amount",
////          "can_see_bank_account_balance",
////          "can_add_comment",
////          "can_create_custom_view"
////        )
////      ),
////      List(
////        AuthenticatedUserIsRequired,
////        UserHasMissingRoles,
////        SystemViewNotFound,
////        UnknownError
////      ),
////      List(apiTagSystemView, apiTagView),
////      Some(List(canGetSystemViews))
////    )
////
////    lazy val getSystemView: OBPEndpoint = {
////      case "system-views" :: viewId :: Nil JsonGet _ => {
////        cc => implicit val ec = EndpointContext(Some(cc))
////          for {
////            (Full(u), callContext) <- authenticatedAccess(cc)
////            view <- ViewNewStyle.systemView(ViewId(viewId), callContext)
////          } yield {
////            (JSONFactory600.createViewJsonV600(view), HttpCode.`200`(callContext))
////          }
////      }
////    }
//
//    staticResourceDocs += ResourceDoc(
//      updateSystemView,
//      implementedInApiVersion,
//      nameOf(updateSystemView),
//      "PUT",
//      "/system-views/VIEW_ID",
//      "Update System View",
//      s"""Update an existing system view.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |The JSON sent is the same as during view creation, with one difference: the 'name' field
//         |of a view is not editable (it is only set when a view is created).
//         |
//         |The 'metadata_view' field determines where metadata (comments, tags, images, where tags) for transactions are stored and retrieved. If set to another view's ID (e.g. 'owner'), metadata added through this view will be shared with all other views that also use the same metadata_view value. If left empty, metadata is stored under this view's own ID and is not shared with other views.
//         |
//         |The response contains the updated view with an `allowed_actions` array.
//         |
//         |""".stripMargin,
//      UpdateViewJsonV600(
//        description = "This is the owner view",
//        metadata_view = "owner",
//        is_public = false,
//        is_firehose = Some(false),
//        which_alias_to_use = "private",
//        hide_metadata_if_alias_used = false,
//        allowed_actions = List(
//          "can_see_transaction_amount",
//          "can_see_bank_account_balance",
//          "can_add_comment"
//        ),
//        can_grant_access_to_views = Some(List("owner", "accountant")),
//        can_revoke_access_to_views = Some(List("owner", "accountant"))
//      ),
//      ViewJsonV600(
//        bank_id = "",
//        account_id = "",
//        view_id = "owner",
//        view_name = "Owner",
//        description = "This is the owner view",
//        metadata_view = "owner",
//        is_public = false,
//        is_system = true,
//        is_firehose = Some(false),
//        alias = "private",
//        hide_metadata_if_alias_used = false,
//        can_grant_access_to_views = List("owner", "accountant"),
//        can_revoke_access_to_views = List("owner", "accountant"),
//        allowed_actions = List(
//          "can_see_transaction_amount",
//          "can_see_bank_account_balance",
//          "can_add_comment"
//        )
//      ),
//      List(
//        InvalidJsonFormat,
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        SystemViewNotFound,
//        SystemViewCannotBePublicError,
//        UnknownError
//      ),
//      List(apiTagSystemView, apiTagView),
//      Some(List(canUpdateSystemView))
//    )
//
//    lazy val updateSystemView: OBPEndpoint = {
//      case "system-views" :: viewId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canUpdateSystemView, callContext)
//            updateJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the UpdateViewJsonV600", 400, callContext) {
//              json.extract[UpdateViewJsonV600]
//            }
//            _ <- Helper.booleanToFuture(SystemViewCannotBePublicError, failCode = 400, cc = callContext) {
//              updateJson.is_public == false
//            }
//            _ <- ViewNewStyle.systemView(ViewId(viewId), callContext)
//            updatedView <- ViewNewStyle.updateSystemView(ViewId(viewId), updateJson.toUpdateViewJson, callContext)
//          } yield {
//            (JSONFactory600.createViewJsonV600(updatedView), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getViewPermissions,
//      implementedInApiVersion,
//      nameOf(getViewPermissions),
//      "GET",
//      "/management/view-permissions",
//      "Get View Permissions",
//      s"""Get a list of all available view permissions.
//         |
//         |This endpoint returns all the available permissions that can be assigned to views,
//         |organized by category. These permissions control what actions and data can be accessed
//         |through a view.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |The response contains all available view permission names that can be used in the
//         |`allowed_actions` field when creating or updating custom views.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ViewPermissionsJsonV600(
//        permissions = List(
//          ViewPermissionJsonV600("can_see_transaction_amount", "Transaction"),
//          ViewPermissionJsonV600("can_see_bank_account_balance", "Account"),
//          ViewPermissionJsonV600("can_create_custom_view", "View")
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagSystemView, apiTagView),
//      Some(List(canGetViewPermissionsAtAllBanks))
//    )
//
//    lazy val getViewPermissions: OBPEndpoint = {
//      case "management" :: "view-permissions" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetViewPermissionsAtAllBanks, callContext)
//          } yield {
//            import Constant._
//
//            // Helper function to determine category from permission name
//            def categorizePermission(permission: String): String = {
//              permission match {
//                case p if p.contains("transaction") && !p.contains("request") => "Transaction"
//                case p if p.contains("bank_account") || p.contains("bank_routing") || p.contains("available_funds") => "Account"
//                case p if p.contains("other_account") || p.contains("other_bank") ||
//                         p.contains("counterparty") || p.contains("more_info") ||
//                         p.contains("url") || p.contains("corporates") ||
//                         p.contains("location") || p.contains("alias") => "Counterparty"
//                case p if p.contains("comment") || p.contains("tag") ||
//                         p.contains("image") || p.contains("where_tag") => "Metadata"
//                case p if p.contains("transaction_request") || p.contains("direct_debit") ||
//                         p.contains("standing_order") => "Transaction Request"
//                case p if p.contains("view") => "View"
//                case p if p.contains("grant") || p.contains("revoke") => "Access Control"
//                case _ => "Other"
//              }
//            }
//
//            // Return all view permissions directly from the constants with generated categories
//            val permissions = ALL_VIEW_PERMISSION_NAMES.map { permission =>
//              ViewPermissionJsonV600(permission, categorizePermission(permission))
//            }.sortBy(p => (p.category, p.permission))
//
//            (ViewPermissionsJsonV600(permissions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createCustomViewManagement,
//      implementedInApiVersion,
//      nameOf(createCustomViewManagement),
//      "POST",
//      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/views",
//      "Create Custom View (Management)",
//      s"""Create a custom view on a bank account via management endpoint.
//         |
//         |This is a **management endpoint** that requires the `CanCreateCustomView` role (entitlement).
//         |
//         |This endpoint provides a simpler, role-based authorization model compared to the original
//         |v3.0.0 endpoint which requires view-level permissions. Use this endpoint when you want to
//         |grant view creation ability through direct role assignment rather than through view access.
//         |
//         |For the original endpoint that checks account-level view permissions, see:
//         |POST /obp/v3.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/views
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |The 'alias' field in the JSON can take one of three values:
//         |
//         | * _public_: to use the public alias if there is one specified for the other account.
//         | * _private_: to use the private alias if there is one specified for the other account.
//         |
//         | * _''(empty string)_: to use no alias; the view shows the real name of the other account.
//         |
//         | The 'hide_metadata_if_alias_used' field in the JSON can take boolean values. If it is set to `true` and there is an alias on the other account then the other accounts' metadata (like more_info, url, image_url, open_corporates_url, etc.) will be hidden. Otherwise the metadata will be shown.
//         |
//         | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.
//         |
//         | The 'metadata_view' field determines where metadata (comments, tags, images, where tags) for transactions are stored and retrieved. If set to another view's ID (e.g. 'owner'), metadata added through this view will be shared with all other views that also use the same metadata_view value. If left empty, metadata is stored under this view's own ID and is not shared with other views.
//         |
//         | You MUST use a leading _ (underscore) in the view name because other view names are reserved for OBP [system views](/index#group-View-System).
//         |
//         |""".stripMargin,
//      createViewJsonV300,
//      viewJsonV300,
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        InvalidCustomViewFormat,
//        BankAccountNotFound,
//        UnknownError
//      ),
//      List(apiTagView, apiTagAccount),
//      Some(List(canCreateCustomView))
//    )
//
//    lazy val createCustomViewManagement: OBPEndpoint = {
//      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            createViewJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CreateViewJson ", 400, callContext) {
//              json.extract[CreateViewJson]
//            }
//            //customer views are started with `_`, eg _life, _work, and System views start with letter, eg: owner
//            _ <- Helper.booleanToFuture(failMsg = InvalidCustomViewFormat + s"Current view_name (${createViewJson.name})", cc = callContext) {
//              isValidCustomViewName(createViewJson.name)
//            }
//            (account, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
//            (view, callContext) <- ViewNewStyle.createCustomView(BankIdAccountId(bankId, accountId), createViewJson, callContext)
//          } yield {
//            (JSONFactory600.createViewJsonV600(view), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomViews,
//      implementedInApiVersion,
//      nameOf(getCustomViews),
//      "GET",
//      "/management/custom-views",
//      "Get Custom Views",
//      s"""Get all custom views.
//         |
//         |Custom views are user-created views with names starting with underscore (_), such as:
//         |- _work
//         |- _personal
//         |- _audit
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ViewsJsonV600(List()),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagView, apiTagSystemView),
//      Some(List(canGetCustomViews))
//    )
//
//    lazy val getCustomViews: OBPEndpoint = {
//      case "management" :: "custom-views" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            customViews <- Future { ViewDefinition.getCustomViews() }
//          } yield {
//            (JSONFactory600.createViewsJsonV600(customViews), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomViewById,
//      implementedInApiVersion,
//      nameOf(getCustomViewById),
//      "GET",
//      "/management/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID",
//      "Get Custom View",
//      s"""Get a single custom view by bank, account, and view ID.
//         |
//         |Custom views are user-created views with names starting with underscore (_), such as:
//         |- _work
//         |- _personal
//         |- _audit
//         |
//         |Custom views are unique per bank_id, account_id, and view_id combination.
//         |
//         |The view is returned with an `allowed_actions` array containing all permissions for that view.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ViewJsonV600(
//        bank_id = ExampleValue.bankIdExample.value,
//        account_id = ExampleValue.accountIdExample.value,
//        view_id = "_work",
//        view_name = "Work",
//        description = "A custom view for work-related transactions.",
//        metadata_view = "_work",
//        is_public = false,
//        is_system = false,
//        is_firehose = Some(false),
//        alias = "private",
//        hide_metadata_if_alias_used = false,
//        can_grant_access_to_views = List("_work"),
//        can_revoke_access_to_views = List("_work"),
//        allowed_actions = List(
//          "can_see_transaction_amount",
//          "can_see_bank_account_balance",
//          "can_add_comment"
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ViewNotFound,
//        UnknownError
//      ),
//      List(apiTagView, apiTagSystemView),
//      Some(List(canGetCustomViews))
//    )
//
//    lazy val getCustomViewById: OBPEndpoint = {
//      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: viewId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            view <- ViewNewStyle.customView(ViewId(viewId), BankIdAccountId(bankId, accountId), callContext)
//          } yield {
//            (JSONFactory600.createViewJsonV600(view), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      resetPasswordUrl,
//      implementedInApiVersion,
//      nameOf(resetPasswordUrl),
//      "POST",
//      "/management/user/reset-password-url",
//      "Create Password Reset URL and Send Email",
//      s"""Create a password reset URL for a user and automatically send it via email.
//         |
//         |Authentication is Required.
//         |
//         |Behavior:
//         |- Generates a unique password reset token
//         |- Creates a reset URL using the portal_external_url property (falls back to API hostname)
//         |- Sends an email to the user with the reset link
//         |- Returns the reset URL in the response for logging/tracking purposes
//         |
//         |Required fields:
//         |- username: The user's username (typically email)
//         |- email: The user's email address (must match username)
//         |- user_id: The user's UUID
//         |
//         |The user must exist and be validated before a reset URL can be generated.
//         |
//         |Email configuration must be set up correctly for email delivery to work.
//         |
//         |""".stripMargin,
//      PostResetPasswordUrlJsonV600(
//        "user@example.com",
//        "user@example.com",
//        "74a8ebcc-10e4-4036-bef3-9835922246bf"
//      ),
//      ResetPasswordUrlJsonV600(
//        "https://api.example.com/reset-password/QOL1CPNJPCZ4BRMPX3Z01DPOX1HMGU3L"
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagUser),
//      Some(List(canCreateResetPasswordUrl))
//    )
//
//    lazy val resetPasswordUrl: OBPEndpoint = {
//      case "management" :: "user" :: "reset-password-url" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postedData <- NewStyle.function.tryons(
//              s"$InvalidJsonFormat The Json body should be the ${classOf[PostResetPasswordUrlJsonV600]}",
//              400,
//              callContext
//            ) {
//              json.extract[PostResetPasswordUrlJsonV600]
//            }
//            // Find the AuthUser
//            authUserBox <- Future {
//              code.model.dataAccess.AuthUser.find(
//                net.liftweb.mapper.By(code.model.dataAccess.AuthUser.username, postedData.username)
//              )
//            }
//            authUser <- NewStyle.function.tryons(
//              s"$UnknownError User not found or validation failed",
//              400,
//              callContext
//            ) {
//              authUserBox match {
//                case Full(user) if user.validated.get && user.email.get == postedData.email =>
//                  // Verify user_id matches
//                  Users.users.vend.getUserByUserId(postedData.user_id) match {
//                    case Full(resourceUser) if resourceUser.name == postedData.username &&
//                                                 resourceUser.emailAddress == postedData.email =>
//                      user
//                    case _ => throw new Exception("User ID does not match username and email")
//                  }
//                case _ => throw new Exception("User not found, not validated, or email mismatch")
//              }
//            }
//            portalUrl <- APIUtil.getPropsValue("portal_external_url") match {
//              case Full(url) => Future.successful(url)
//              case _ => Future.failed(new Exception(s"$IncompleteServerConfiguration portal_external_url is not set in props. It is required to construct the password reset link."))
//            }
//          } yield {
//            // Explicitly type the user to ensure proper method resolution
//            val user: code.model.dataAccess.AuthUser = authUser
//
//            // Generate new reset token
//            user.uniqueId.set(java.util.UUID.randomUUID().toString.replace("-", ""))
//            user.save
//
//            // Create a JWT token with the uniqueId as subject and configurable expiry
//            val expiryMinutes = APIUtil.getPropsAsIntValue("password_reset_token_expiry_minutes", 120)
//            val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
//              .subject(user.uniqueId.get)
//              .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
//              .issueTime(new java.util.Date())
//              .build()
//            val jwtToken = CertificateUtil.jwtWithHmacProtection(claimsSet)
//
//            // Construct reset URL using portal_external_url
//            val resetPasswordLink = portalUrl +
//              "/reset-password/" +
//              java.net.URLEncoder.encode(jwtToken, "UTF-8")
//
//            // Send email using CommonsEmailWrapper (like createUser does)
//            val textContent = Some(s"Please use the following link to reset your password: $resetPasswordLink")
//            val htmlContent = Some(s"<p>Please use the following link to reset your password:</p><p><a href='$resetPasswordLink'>$resetPasswordLink</a></p>")
//            val subjectContent = "Reset your password - " + user.username.get
//
//            val emailContent = code.api.util.CommonsEmailWrapper.EmailContent(
//              from = code.model.dataAccess.AuthUser.emailFrom,
//              to = List(user.email.get),
//              bcc = code.model.dataAccess.AuthUser.bccEmail.toList,
//              subject = subjectContent,
//              textContent = textContent,
//              htmlContent = htmlContent
//            )
//
//            code.api.util.CommonsEmailWrapper.sendHtmlEmail(emailContent)
//
//            (
//              ResetPasswordUrlJsonV600(resetPasswordLink),
//              HttpCode.`201`(callContext)
//            )
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      resetPasswordUrlAnonymous,
//      implementedInApiVersion,
//      nameOf(resetPasswordUrlAnonymous),
//      "POST",
//      "/users/password-reset-url",
//      "Request Password Reset Email",
//      s"""Request a password reset email for a user. No authentication is required.
//         |
//         |Authentication is NOT Required.
//         |
//         |This endpoint is designed for users who have forgotten their password and cannot log in.
//         |
//         |Behavior:
//         |- Looks up the user by username and email
//         |- Generates a unique password reset token
//         |- Creates a reset URL using the portal_external_url property (falls back to API hostname)
//         |- Sends an email to the user with the reset link
//         |
//         |Required fields:
//         |- username: The user's username (typically email)
//         |- email: The user's email address (must match username)
//         |
//         |The user must exist and be validated before a reset email can be sent.
//         |
//         |Email configuration must be set up correctly for email delivery to work.
//         |
//         |Note: For security reasons, this endpoint returns a generic success message regardless of
//         |whether the user was found, to prevent user enumeration.
//         |
//         |""".stripMargin,
//      PostResetPasswordUrlAnonymousJsonV600(
//        "user@example.com",
//        "user@example.com"
//      ),
//      ResetPasswordUrlAnonymousResponseJsonV600(
//        "If the account exists, a password reset email has been sent."
//      ),
//      List(
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagUser),
//      Some(List())
//    )
//
//    lazy val resetPasswordUrlAnonymous: OBPEndpoint = {
//      case "users" :: "password-reset-url" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- anonymousAccess(cc)
//            postedData <- NewStyle.function.tryons(
//              s"$InvalidJsonFormat The Json body should be the ${classOf[PostResetPasswordUrlAnonymousJsonV600]}",
//              400,
//              callContext
//            ) {
//              json.extract[PostResetPasswordUrlAnonymousJsonV600]
//            }
//          } yield {
//            // Look up the user - but always return the same response to prevent user enumeration
//            val authUserBox = code.model.dataAccess.AuthUser.find(
//              net.liftweb.mapper.By(code.model.dataAccess.AuthUser.username, postedData.username)
//            )
//
//            (authUserBox, APIUtil.getPropsValue("portal_external_url")) match {
//              case (Full(user), Full(portalUrl)) if user.validated.get && user.email.get == postedData.email =>
//                // Generate new reset token
//                user.uniqueId.set(java.util.UUID.randomUUID().toString.replace("-", ""))
//                user.save
//
//                // Create a JWT token with the uniqueId as subject and configurable expiry
//                val expiryMinutes = APIUtil.getPropsAsIntValue("password_reset_token_expiry_minutes", 120)
//                val claimsSet = new com.nimbusds.jwt.JWTClaimsSet.Builder()
//                  .subject(user.uniqueId.get)
//                  .expirationTime(new java.util.Date(System.currentTimeMillis() + expiryMinutes * 60L * 1000L))
//                  .issueTime(new java.util.Date())
//                  .build()
//                val jwtToken = CertificateUtil.jwtWithHmacProtection(claimsSet)
//
//                // Construct reset URL
//                val resetPasswordLink = portalUrl +
//                  "/reset-password/" +
//                  java.net.URLEncoder.encode(jwtToken, "UTF-8")
//
//                // Send email
//                val textContent = Some(s"Please use the following link to reset your password: $resetPasswordLink")
//                val htmlContent = Some(s"<p>Please use the following link to reset your password:</p><p><a href='$resetPasswordLink'>$resetPasswordLink</a></p>")
//                val subjectContent = "Reset your password - " + user.username.get
//
//                val emailContent = code.api.util.CommonsEmailWrapper.EmailContent(
//                  from = code.model.dataAccess.AuthUser.emailFrom,
//                  to = List(user.email.get),
//                  bcc = code.model.dataAccess.AuthUser.bccEmail.toList,
//                  subject = subjectContent,
//                  textContent = textContent,
//                  htmlContent = htmlContent
//                )
//
//                code.api.util.CommonsEmailWrapper.sendHtmlEmail(emailContent)
//
//              case (_, Empty) =>
//                logger.error("portal_external_url is not set in props. Cannot send password reset email.")
//
//              case _ =>
//                // Do nothing - return same response to prevent user enumeration
//            }
//
//            (
//              ResetPasswordUrlAnonymousResponseJsonV600("If the account exists, a password reset email has been sent."),
//              HttpCode.`201`(callContext)
//            )
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      resetPasswordComplete,
//      implementedInApiVersion,
//      nameOf(resetPasswordComplete),
//      "POST",
//      "/users/password",
//      "Complete Password Reset",
//      s"""Complete a password reset using the token received via email.
//         |
//         |Authentication is NOT Required.
//         |
//         |After requesting a password reset email (via POST /management/user/reset-password-url or
//         |POST /users/password-reset-url), the user receives an email with a reset link containing a JWT token.
//         |
//         |This endpoint accepts that token along with a new password and completes the password reset.
//         |
//         |The token is a signed JWT with a configurable expiry (default: 120 minutes).
//         |Configure the expiry with the property: password_reset_token_expiry_minutes
//         |
//         |Required fields:
//         |- token: The JWT reset token from the password reset email
//         |- new_password: The new password (must meet strong password requirements)
//         |
//         |The token is single-use. Once the password is reset, the token is invalidated.
//         |
//         |""".stripMargin,
//      PostResetPasswordCompleteJsonV600(
//        "a1b2c3d4e5f67890abcdef1234567890",
//        "NewStr0ng!Password"
//      ),
//      ResetPasswordCompleteResponseJsonV600(
//        "Password has been reset successfully."
//      ),
//      List(
//        InvalidJsonFormat,
//        InvalidStrongPasswordFormat,
//        UnknownError
//      ),
//      List(apiTagUser),
//      Some(List())
//    )
//
//    lazy val resetPasswordComplete: OBPEndpoint = {
//      case "users" :: "password" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- anonymousAccess(cc)
//            postedData <- NewStyle.function.tryons(
//              s"$InvalidJsonFormat The Json body should be the ${classOf[PostResetPasswordCompleteJsonV600]}",
//              400,
//              callContext
//            ) {
//              json.extract[PostResetPasswordCompleteJsonV600]
//            }
//            token = postedData.token.trim
//            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat Token cannot be empty", cc = callContext) {
//              token.nonEmpty
//            }
//            // Validate password strength
//            _ <- Helper.booleanToFuture(ErrorMessages.InvalidStrongPasswordFormat, 400, callContext) {
//              fullPasswordValidation(postedData.new_password)
//            }
//            // Verify JWT signature
//            _ <- Helper.booleanToFuture(s"$UnknownError Invalid or expired reset token", 400, callContext) {
//              try {
//                CertificateUtil.verifywtWithHmacProtection(token)
//              } catch {
//                case _: Exception => false
//              }
//            }
//            // Check JWT expiration and extract subject (uniqueId)
//            uniqueId <- NewStyle.function.tryons(
//              s"$UnknownError Invalid or expired reset token",
//              400,
//              callContext
//            ) {
//              val signedJWT = com.nimbusds.jwt.SignedJWT.parse(token)
//              val expiration = signedJWT.getJWTClaimsSet.getExpirationTime
//              if (expiration == null || expiration.before(new java.util.Date())) {
//                throw new Exception("Token has expired")
//              }
//              signedJWT.getJWTClaimsSet.getSubject
//            }
//            // Find user by uniqueId from JWT
//            authUserBox <- Future {
//              code.model.dataAccess.AuthUser.findUserByValidationToken(uniqueId)
//            }
//            user <- NewStyle.function.tryons(
//              s"$UnknownError Invalid or expired reset token",
//              400,
//              callContext
//            ) {
//              authUserBox.openOrThrowException("User not found")
//            }
//          } yield {
//            // Set the new password
//            user.password.set(postedData.new_password)
//            // Reset the unique ID token to invalidate the reset link
//            user.uniqueId.set(java.util.UUID.randomUUID().toString.replace("-", ""))
//            user.save
//
//            (
//              ResetPasswordCompleteResponseJsonV600("Password has been reset successfully."),
//              HttpCode.`201`(callContext)
//            )
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getWebUiProp,
//      implementedInApiVersion,
//      nameOf(getWebUiProp),
//      "GET",
//      "/webui-props/WEBUI_PROP_NAME",
//      "Get WebUiProp by Name",
//      s"""
//         |
//         |Get a single WebUiProp by name.
//         |
//         |Properties with names starting with "webui_" can be stored in the database and managed via API.
//         |
//         |**Data Sources:**
//         |
//         |1. **Explicit WebUiProps (Database)**: Custom values created/updated via the API and stored in the database.
//         |
//         |2. **Implicit WebUiProps (Configuration File)**: Default values defined in the `sample.props.template` configuration file.
//         |
//         |**Response Fields:**
//         |
//         |* `name`: The property name
//         |* `value`: The property value
//         |* `webUiPropsId` (optional): UUID for database props, omitted for config props
//         |* `source`: Either "database" (editable via API) or "config" (read-only from config file)
//         |
//         |**Query Parameter:**
//         |
//         |* `active` (optional, boolean string, default: "false")
//         |  - If `active=false` or omitted: Returns only explicit prop from the database (source="database")
//         |  - If `active=true`: Returns explicit prop from database, or if not found, returns implicit (default) prop from configuration file (source="config")
//         |
//         |**Examples:**
//         |
//         |Get database-stored prop only:
//         |${getObpApiRoot}/v6.0.0/webui-props/webui_api_explorer_url
//         |
//         |Get database prop or fallback to default:
//         |${getObpApiRoot}/v6.0.0/webui-props/webui_api_explorer_url?active=true
//         |
//         |""",
//      EmptyBody,
//      WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("web-ui-props-id"), Some("config")),
//      List(
//        WebUiPropsNotFoundByName,
//        UnknownError
//      ),
//      List(apiTagWebUiProps)
//    )
//    lazy val getWebUiProp: OBPEndpoint = {
//      case "webui-props" :: webUiPropName :: Nil JsonGet req => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          logger.info(s"========== GET /obp/${ApiVersion.v6_0_0}/webui-props/$webUiPropName (SINGLE PROP) called ==========")
//          val active = ObpS.param("active").getOrElse("false")
//          for {
//            invalidMsg <- Future(s"""$InvalidFilterParameterFormat `active` must be a boolean, but current `active` value is: ${active} """)
//            isActived <- NewStyle.function.tryons(invalidMsg, 400, cc.callContext) {
//              active.toBoolean
//            }
//            explicitWebUiProps <- Future{ MappedWebUiPropsProvider.getAll() }
//            explicitProp = explicitWebUiProps.find(_.name == webUiPropName)
//            result <- {
//              explicitProp match {
//                case Some(prop) =>
//                  // Found in database
//                  Future.successful(WebUiPropsCommons(prop.name, prop.value, prop.webUiPropsId, source = Some("database")))
//                case None if isActived =>
//                  // Not in database, check implicit props if active=true
//                  val implicitWebUiProps = getWebUIPropsPairs.map(webUIPropsPairs =>
//                    WebUiPropsCommons(webUIPropsPairs._1, webUIPropsPairs._2, webUiPropsId = Some("default"), source = Some("config"))
//                  )
//                  val implicitProp = implicitWebUiProps.find(_.name == webUiPropName)
//                  implicitProp match {
//                    case Some(prop) => Future.successful(prop)
//                    case None => Future.failed(new Exception(s"$WebUiPropsNotFoundByName Current WEBUI_PROP_NAME($webUiPropName)"))
//                  }
//                case None =>
//                  // Not in database and active=false
//                  Future.failed(new Exception(s"$WebUiPropsNotFoundByName Current WEBUI_PROP_NAME($webUiPropName)"))
//              }
//            }
//          } yield {
//            (result, HttpCode.`200`(cc.callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getWebUiProps,
//      implementedInApiVersion,
//      nameOf(getWebUiProps),
//      "GET",
//      "/webui-props",
//      "Get WebUiProps",
//      s"""
//         |
//         |Get WebUiProps - properties that configure the Web UI behavior and appearance.
//         |
//         |Properties with names starting with "webui_" can be stored in the database and managed via API.
//         |
//         |**Data Sources:**
//         |
//         |1. **Explicit WebUiProps (Database)**: Custom values created/updated via the API and stored in the database.
//         |
//         |2. **Implicit WebUiProps (Configuration File)**: Default values defined in the `sample.props.template` configuration file.
//         |
//         |**Response Fields:**
//         |
//         |* `name`: The property name
//         |* `value`: The property value
//         |* `webUiPropsId` (optional): UUID for database props, omitted for config props
//         |* `source`: Either "database" (editable via API) or "config" (read-only from config file)
//         |
//         |**Query Parameter:**
//         |
//         |* `what` (optional, string, default: "active")
//         |  - `active`: Returns one value per property name
//         |    - If property exists in database: returns database value (source="database")
//         |    - If property only in config file: returns config default value (source="config")
//         |  - `database`: Returns ONLY properties explicitly stored in the database (source="database")
//         |  - `config`: Returns ONLY default properties from configuration file (source="config")
//         |
//         |**Examples:**
//         |
//         |Get active props (database overrides config, one value per prop):
//         |${getObpApiRoot}/v6.0.0/webui-props
//         |${getObpApiRoot}/v6.0.0/webui-props?what=active
//         |
//         |Get only database-stored props:
//         |${getObpApiRoot}/v6.0.0/webui-props?what=database
//         |
//         |Get only default props from configuration:
//         |${getObpApiRoot}/v6.0.0/webui-props?what=config
//         |
//         |For more details about WebUI Props, including how to set config file defaults and precedence order, see ${Glossary.getGlossaryItemLink("webui_props")}.
//         |
//         |""",
//      EmptyBody,
//      ListResult(
//        "webui_props",
//        (List(WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("web-ui-props-id"), Some("database"))))
//      )
//      ,
//      List(
//        UnknownError
//      ),
//      List(apiTagWebUiProps)
//    )
//
//
//    lazy val getWebUiProps: OBPEndpoint = {
//      case "webui-props":: Nil JsonGet req => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          val what = ObpS.param("what").getOrElse("active")
//          logger.info(s"========== GET /obp/${ApiVersion.v6_0_0}/webui-props (ALL PROPS) called with what=$what ==========")
//          for {
//            callContext <- Future.successful(cc.callContext)
//            _ <- NewStyle.function.tryons(s"""$InvalidFilterParameterFormat `what` must be one of: active, database, config. Current value: $what""", 400, callContext) {
//              what match {
//                case "active" | "database" | "config" => true
//                case _ => false
//              }
//            }
//            explicitWebUiProps <- Future{ MappedWebUiPropsProvider.getAll() }
//            explicitWebUiPropsWithSource = explicitWebUiProps.map(prop => WebUiPropsCommons(prop.name, prop.value, prop.webUiPropsId, source = Some("database")))
//            implicitWebUiProps = getWebUIPropsPairs.map(webUIPropsPairs=>WebUiPropsCommons(webUIPropsPairs._1, webUIPropsPairs._2, webUiPropsId = Some("default"), source = Some("config")))
//            result = what match {
//              case "database" =>
//                // Return only database props
//                explicitWebUiPropsWithSource
//              case "config" =>
//                // Return only config file props
//                implicitWebUiProps.distinct
//              case "active" =>
//                // Return one value per prop: database value if exists, otherwise config value
//                val databasePropNames = explicitWebUiPropsWithSource.map(_.name).toSet
//                val configPropsNotInDatabase = implicitWebUiProps.distinct.filterNot(prop => databasePropNames.contains(prop.name))
//                explicitWebUiPropsWithSource ++ configPropsNotInDatabase
//            }
//          } yield {
//            logger.info(s"========== GET /obp/${ApiVersion.v6_0_0}/webui-props returning ${result.size} records ==========")
//            result.foreach { prop =>
//              logger.info(s"  - name: ${prop.name}, value: ${prop.value}, webUiPropsId: ${prop.webUiPropsId}")
//            }
//            logger.info(s"========== END GET /obp/${ApiVersion.v6_0_0}/webui-props ==========")
//            (ListResult("webui_props", result), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createOrUpdateWebUiProps,
//      implementedInApiVersion,
//      nameOf(createOrUpdateWebUiProps),
//      "PUT",
//      "/management/webui_props/WEBUI_PROP_NAME",
//      "Create or Update WebUiProps",
//      s"""Create or Update a WebUiProps.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |This endpoint is idempotent - it will create the property if it doesn't exist, or update it if it does.
//         |The property is identified by WEBUI_PROP_NAME in the URL path.
//         |
//         |Explanation of Fields:
//         |
//         |* WEBUI_PROP_NAME in URL path (must start with `webui_`, contain only alphanumeric characters, underscore, and dot, not exceed 255 characters, and will be converted to lowercase)
//         |* value is required String value in request body
//         |
//         |The line break and double quotations should be escaped, example:
//         |
//         |```
//         |
//         |{"name": "webui_some", "value": "this value
//         |have "line break" and double quotations."}
//         |
//         |```
//         |should be escaped like this:
//         |
//         |```
//         |
//         |{"name": "webui_some", "value": "this value\\nhave \\"line break\\" and double quotations."}
//         |
//         |```
//         |
//         |Insert image examples:
//         |
//         |```
//         |// set width=100 and height=50
//         |{"name": "webui_some_pic", "value": "here is a picture ![hello](http://somedomain.com/images/pic.png =100x50)"}
//         |
//         |// only set height=50
//         |{"name": "webui_some_pic", "value": "here is a picture ![hello](http://somedomain.com/images/pic.png =x50)"}
//         |
//         |// only width=20%
//         |{"name": "webui_some_pic", "value": "here is a picture ![hello](http://somedomain.com/images/pic.png =20%x)"}
//         |
//         |```
//         |
//         |""",
//      WebUiPropsPutJsonV600("https://apiexplorer.openbankproject.com"),
//      WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("some-web-ui-props-id")),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        InvalidWebUiProps,
//        UnknownError
//      ),
//      List(apiTagWebUiProps),
//      Some(List(canCreateWebUiProps))
//    )
//
//    lazy val createOrUpdateWebUiProps: OBPEndpoint = {
//      case "management" :: "webui_props" :: webUiPropName :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateWebUiProps, callContext)
//            // Convert name to lowercase
//            webUiPropNameLower = webUiPropName.toLowerCase
//            invalidMsg = s"""$InvalidWebUiProps name must start with webui_, but current name is: ${webUiPropNameLower} """
//            _ <- NewStyle.function.tryons(invalidMsg, 400, callContext) {
//              require(webUiPropNameLower.startsWith("webui_"))
//            }
//            invalidCharsMsg = s"""$InvalidWebUiProps name must contain only alphanumeric characters, underscore, and dot. Current name: ${webUiPropNameLower} """
//            _ <- NewStyle.function.tryons(invalidCharsMsg, 400, callContext) {
//              require(webUiPropNameLower.matches("^[a-zA-Z0-9_.]+$"))
//            }
//            invalidLengthMsg = s"""$InvalidWebUiProps name must not exceed 255 characters. Current length: ${webUiPropNameLower.length} """
//            _ <- NewStyle.function.tryons(invalidLengthMsg, 400, callContext) {
//              require(webUiPropNameLower.length <= 255)
//            }
//            // Check if resource already exists to determine status code
//            existingProp <- Future { MappedWebUiPropsProvider.getByName(webUiPropNameLower) }
//            resourceExists = existingProp.isDefined
//            failMsg = s"$InvalidJsonFormat The Json body should contain a value field"
//            valueJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              json.extract[WebUiPropsPutJsonV600]
//            }
//            webUiPropsData = WebUiPropsCommons(webUiPropNameLower, valueJson.value)
//            Full(webUiProps) <- Future { MappedWebUiPropsProvider.createOrUpdate(webUiPropsData) }
//          } yield {
//            val commonsData: WebUiPropsCommons = webUiProps
//            val statusCode = if (resourceExists) HttpCode.`200`(callContext) else HttpCode.`201`(callContext)
//            (commonsData, statusCode)
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteWebUiProps,
//      implementedInApiVersion,
//      nameOf(deleteWebUiProps),
//      "DELETE",
//      "/management/webui_props/WEBUI_PROP_NAME",
//      "Delete WebUiProps",
//      s"""Delete a WebUiProps specified by WEBUI_PROP_NAME.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |The property name will be converted to lowercase before deletion.
//         |
//         |Returns 204 No Content on successful deletion.
//         |
//         |This endpoint is idempotent - if the property does not exist, it still returns 204 No Content.
//         |
//         |Requires the $canDeleteWebUiProps role.
//         |
//         |""",
//      EmptyBody,
//      EmptyBody,
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidWebUiProps,
//        UnknownError
//      ),
//      List(apiTagWebUiProps),
//      Some(List(canDeleteWebUiProps))
//    )
//
//    lazy val deleteWebUiProps: OBPEndpoint = {
//      case "management" :: "webui_props" :: webUiPropName :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteWebUiProps, callContext)
//            // Convert name to lowercase
//            webUiPropNameLower = webUiPropName.toLowerCase
//            invalidMsg = s"""$InvalidWebUiProps name must start with webui_, but current name is: ${webUiPropNameLower} """
//            _ <- NewStyle.function.tryons(invalidMsg, 400, callContext) {
//              require(webUiPropNameLower.startsWith("webui_"))
//            }
//            invalidCharsMsg = s"""$InvalidWebUiProps name must contain only alphanumeric characters, underscore, and dot. Current name: ${webUiPropNameLower} """
//            _ <- NewStyle.function.tryons(invalidCharsMsg, 400, callContext) {
//              require(webUiPropNameLower.matches("^[a-zA-Z0-9_.]+$"))
//            }
//            invalidLengthMsg = s"""$InvalidWebUiProps name must not exceed 255 characters. Current length: ${webUiPropNameLower.length} """
//            _ <- NewStyle.function.tryons(invalidLengthMsg, 400, callContext) {
//              require(webUiPropNameLower.length <= 255)
//            }
//            // Check if resource exists
//            existingProp <- Future { MappedWebUiPropsProvider.getByName(webUiPropNameLower) }
//            _ <- existingProp match {
//              case Full(prop) =>
//                // Property exists - delete it
//                Future { MappedWebUiPropsProvider.delete(prop.webUiPropsId.getOrElse("")) } map {
//                  case Full(true) => Full(())
//                  case Full(false) => ObpApiFailure(s"$UnknownError Cannot delete WebUI prop", 500, callContext)
//                  case Empty => ObpApiFailure(s"$UnknownError Cannot delete WebUI prop", 500, callContext)
//                  case Failure(msg, _, _) => ObpApiFailure(msg, 500, callContext)
//                }
//              case Empty =>
//                // Property not found - idempotent delete returns success
//                Future.successful(Full(()))
//              case Failure(msg, _, _) =>
//                Future.failed(new Exception(msg))
//            }
//          } yield {
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getSystemDynamicEntities,
//      implementedInApiVersion,
//      nameOf(getSystemDynamicEntities),
//      "GET",
//      "/management/system-dynamic-entities",
//      "Get System Dynamic Entities",
//      s"""Get all System Dynamic Entities with record counts.
//         |
//         |Each dynamic entity in the response includes a `record_count` field showing how many data records exist for that entity.
//         |
//         |This v6.0.0 endpoint returns snake_case field names and an explicit `entity_name` field.
//         |
//         |For more information see ${Glossary.getGlossaryItemLink(
//          "Dynamic-Entities"
//        )} """,
//      EmptyBody,
//      DynamicEntitiesWithCountJsonV600(
//        dynamic_entities = List(
//          DynamicEntityDefinitionWithCountJsonV600(
//            dynamic_entity_id = "abc-123-def",
//            entity_name = "customer_preferences",
//            user_id = "user-456",
//            bank_id = None,
//            has_personal_entity = true,
//            schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences", "required": ["theme"], "properties": {"theme": {"type": "string"}, "language": {"type": "string"}}}""").asInstanceOf[org.json4s.JsonAST.JObject],
//            record_count = 42
//          )
//        )
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi),
//      Some(List(canGetSystemLevelDynamicEntities))
//    )
//
//    lazy val getSystemDynamicEntities: OBPEndpoint = {
//      case "management" :: "system-dynamic-entities" :: Nil JsonGet req => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            dynamicEntities <- Future(
//              NewStyle.function.getDynamicEntities(None, false)
//            )
//          } yield {
//            val listCommons: List[DynamicEntityCommons] = dynamicEntities.sortBy(_.entityName)
//            val entitiesWithCounts = listCommons.map { entity =>
//              val recordCount = DynamicData.count(
//                By(DynamicData.DynamicEntityName, entity.entityName),
//                By(DynamicData.IsPersonalEntity, false),
//                if (entity.bankId.isEmpty) NullRef(DynamicData.BankId) else By(DynamicData.BankId, entity.bankId.get)
//              )
//              (entity, recordCount)
//            }
//            (
//              JSONFactory600.createDynamicEntitiesWithCountJson(entitiesWithCounts),
//              HttpCode.`200`(cc.callContext)
//            )
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getBankLevelDynamicEntities,
//      implementedInApiVersion,
//      nameOf(getBankLevelDynamicEntities),
//      "GET",
//      "/management/banks/BANK_ID/dynamic-entities",
//      "Get Bank Level Dynamic Entities",
//      s"""Get all Bank Level Dynamic Entities for one bank with record counts.
//         |
//         |Each dynamic entity in the response includes a `record_count` field showing how many data records exist for that entity.
//         |
//         |This v6.0.0 endpoint returns snake_case field names and an explicit `entity_name` field.
//         |
//         |For more information see ${Glossary.getGlossaryItemLink(
//          "Dynamic-Entities"
//        )} """,
//      EmptyBody,
//      DynamicEntitiesWithCountJsonV600(
//        dynamic_entities = List(
//          DynamicEntityDefinitionWithCountJsonV600(
//            dynamic_entity_id = "abc-123-def",
//            entity_name = "customer_preferences",
//            user_id = "user-456",
//            bank_id = Some("gh.29.uk"),
//            has_personal_entity = true,
//            schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences", "required": ["theme"], "properties": {"theme": {"type": "string"}, "language": {"type": "string"}}}""").asInstanceOf[org.json4s.JsonAST.JObject],
//            record_count = 42
//          )
//        )
//      ),
//      List(
//        $BankNotFound,
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi),
//      Some(List(canGetBankLevelDynamicEntities, canGetAnyBankLevelDynamicEntities))
//    )
//
//    lazy val getBankLevelDynamicEntities: OBPEndpoint = {
//      case "management" :: "banks" :: bankId :: "dynamic-entities" :: Nil JsonGet req => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            dynamicEntities <- Future(
//              NewStyle.function.getDynamicEntities(Some(bankId), false)
//            )
//          } yield {
//            val listCommons: List[DynamicEntityCommons] = dynamicEntities.sortBy(_.entityName)
//            val entitiesWithCounts = listCommons.map { entity =>
//              val recordCount = DynamicData.count(
//                By(DynamicData.DynamicEntityName, entity.entityName),
//                By(DynamicData.IsPersonalEntity, false),
//                By(DynamicData.BankId, bankId)
//              )
//              (entity, recordCount)
//            }
//            (
//              JSONFactory600.createDynamicEntitiesWithCountJson(entitiesWithCounts),
//              HttpCode.`200`(cc.callContext)
//            )
//          }
//      }
//    }
//
//    private def unboxResult[T: Manifest](box: Box[T], entityName: String): T = {
//      if (box.isInstanceOf[Failure]) {
//        val failure = box.asInstanceOf[Failure]
//        // change the internal db column name 'dynamicdataid' to entity's id name
//        val msg = failure.msg.replace(
//          DynamicData.DynamicDataId.dbColumnName,
//          StringUtils.uncapitalize(entityName) + "Id"
//        )
//        val changedMsgFailure = failure.copy(msg = s"$InternalServerError $msg")
//        fullBoxOrException[T](changedMsgFailure)
//      }
//      box.openOrThrowException(s"$UnknownError ")
//    }
//
//    // Helper method for creating dynamic entities with v6.0.0 response format
//    private def createDynamicEntityV600(
//        cc: CallContext,
//        dynamicEntity: DynamicEntityCommons
//    ) = {
//      for {
//        Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(
//          dynamicEntity,
//          cc.callContext
//        )
//        // Grant the CRUD roles to the logged-in user
//        crudRoles = List(
//          DynamicEntityInfo.canCreateRole(result.entityName, dynamicEntity.bankId),
//          DynamicEntityInfo.canUpdateRole(result.entityName, dynamicEntity.bankId),
//          DynamicEntityInfo.canGetRole(result.entityName, dynamicEntity.bankId),
//          DynamicEntityInfo.canDeleteRole(result.entityName, dynamicEntity.bankId)
//        )
//      } yield {
//        crudRoles.map(role =>
//          Entitlement.entitlement.vend.addEntitlement(
//            dynamicEntity.bankId.getOrElse(""),
//            cc.userId,
//            role.toString()
//          )
//        )
//        val commonsData: DynamicEntityCommons = result
//        (
//          JSONFactory600.createMyDynamicEntitiesJson(List(commonsData)).dynamic_entities.head,
//          HttpCode.`201`(cc.callContext)
//        )
//      }
//    }
//
//    // Helper method for updating dynamic entities with v6.0.0 response format
//    private def updateDynamicEntityV600(
//        cc: CallContext,
//        dynamicEntity: DynamicEntityCommons
//    ) = {
//      for {
//        Full(result) <- NewStyle.function.createOrUpdateDynamicEntity(
//          dynamicEntity,
//          cc.callContext
//        )
//      } yield {
//        val commonsData: DynamicEntityCommons = result
//        (
//          JSONFactory600.createMyDynamicEntitiesJson(List(commonsData)).dynamic_entities.head,
//          HttpCode.`200`(cc.callContext)
//        )
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createSystemDynamicEntity,
//      implementedInApiVersion,
//      nameOf(createSystemDynamicEntity),
//      "POST",
//      "/management/system-dynamic-entities",
//      "Create System Level Dynamic Entity",
//      s"""Create a system level Dynamic Entity.
//         |
//         |This v6.0.0 endpoint accepts and returns snake_case field names with an explicit `entity_name` field.
//         |
//         |**Request format:**
//         |```json
//         |{
//         |  "entity_name": "customer_preferences",
//         |  "has_personal_entity": true,
//         |  "has_public_access": false,
//         |  "has_community_access": false,
//         |  "personal_requires_role": false,
//         |  "schema": {
//         |    "description": "User preferences",
//         |    "required": ["theme"],
//         |    "properties": {
//         |      "theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"},
//         |      "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}
//         |    }
//         |  }
//         |}
//         |```
//         |
//         |**Note:**
//         |* The `entity_name` must be lowercase with underscores (snake_case), e.g. `customer_preferences`. No uppercase letters or spaces allowed.
//         |* Each property MUST include an `example` field with a valid example value.
//         |* Each property can optionally include `description` (markdown text), and for string types: `minLength` and `maxLength`.
//         |* Set `has_public_access` to `true` to generate read-only public endpoints (GET only, no authentication required) under `/public/`.
//         |* Set `has_community_access` to `true` to generate read-only community endpoints (GET only, authentication required + CanGet role) under `/community/`. Community endpoints return ALL records (personal + non-personal from all users).
//         |* Set `personal_requires_role` to `true` to require the corresponding role (e.g. CanCreateDynamicEntity_, CanGetDynamicEntity_) for `/my/` personal entity endpoints. Default is `false` (any authenticated user can use `/my/` endpoints).
//         |
//         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
//      CreateDynamicEntityRequestJsonV600(
//        entity_name = "customer_preferences",
//        has_personal_entity = Some(true),
//        has_public_access = Some(false),
//        has_community_access = Some(false),
//        personal_requires_role = Some(false),
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      DynamicEntityDefinitionJsonV600(
//        dynamic_entity_id = "abc-123-def",
//        entity_name = "customer_preferences",
//        user_id = "user-456",
//        bank_id = None,
//        has_personal_entity = true,
//        has_public_access = false,
//        has_community_access = false,
//        personal_requires_role = false,
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi),
//      Some(List(canCreateSystemLevelDynamicEntity)),
//      authMode = UserOrApplication
//    )
//
//    // v6.0.0 entity names must be lowercase with underscores (snake_case)
//    private val validEntityNamePattern = "^[a-z][a-z0-9_]*$".r.pattern
//
//    private def validateEntityNameV600(entityName: String, callContext: Option[CallContext]): Future[Unit] = {
//      if (validEntityNamePattern.matcher(entityName).matches()) {
//        Future.successful(())
//      } else {
//        Future.failed(new RuntimeException(s"$InvalidDynamicEntityName Current value: '$entityName'"))
//      }
//    }
//
//    lazy val createSystemDynamicEntity: OBPEndpoint = {
//      case "management" :: "system-dynamic-entities" :: Nil JsonPost json -> _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          request <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//            json.extract[CreateDynamicEntityRequestJsonV600]
//          }
//          _ <- validateEntityNameV600(request.entity_name, cc.callContext)
//          internalJson = JSONFactory600.convertV600RequestToInternal(request)
//          dynamicEntity = DynamicEntityCommons(internalJson, None, cc.userId, None)
//          result <- createDynamicEntityV600(cc, dynamicEntity)
//        } yield result
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createBankLevelDynamicEntity,
//      implementedInApiVersion,
//      nameOf(createBankLevelDynamicEntity),
//      "POST",
//      "/management/banks/BANK_ID/dynamic-entities",
//      "Create Bank Level Dynamic Entity",
//      s"""Create a bank level Dynamic Entity.
//         |
//         |This v6.0.0 endpoint accepts and returns snake_case field names with an explicit `entity_name` field.
//         |
//         |**Request format:**
//         |```json
//         |{
//         |  "entity_name": "customer_preferences",
//         |  "has_personal_entity": true,
//         |  "has_public_access": false,
//         |  "has_community_access": false,
//         |  "personal_requires_role": false,
//         |  "schema": {
//         |    "description": "User preferences",
//         |    "required": ["theme"],
//         |    "properties": {
//         |      "theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"},
//         |      "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}
//         |    }
//         |  }
//         |}
//         |```
//         |
//         |**Note:**
//         |* The `entity_name` must be lowercase with underscores (snake_case), e.g. `customer_preferences`. No uppercase letters or spaces allowed.
//         |* Each property MUST include an `example` field with a valid example value.
//         |* Each property can optionally include `description` (markdown text), and for string types: `minLength` and `maxLength`.
//         |* Set `has_public_access` to `true` to generate read-only public endpoints (GET only, no authentication required) under `/public/`.
//         |* Set `has_community_access` to `true` to generate read-only community endpoints (GET only, authentication required + CanGet role) under `/community/`. Community endpoints return ALL records (personal + non-personal from all users).
//         |* Set `personal_requires_role` to `true` to require the corresponding role (e.g. CanCreateDynamicEntity_, CanGetDynamicEntity_) for `/my/` personal entity endpoints. Default is `false` (any authenticated user can use `/my/` endpoints).
//         |
//         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
//      CreateDynamicEntityRequestJsonV600(
//        entity_name = "customer_preferences",
//        has_personal_entity = Some(true),
//        has_public_access = Some(false),
//        has_community_access = Some(false),
//        personal_requires_role = Some(false),
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      DynamicEntityDefinitionJsonV600(
//        dynamic_entity_id = "abc-123-def",
//        entity_name = "customer_preferences",
//        user_id = "user-456",
//        bank_id = Some("gh.29.uk"),
//        has_personal_entity = true,
//        has_public_access = false,
//        has_community_access = false,
//        personal_requires_role = false,
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      List(
//        $BankNotFound,
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi),
//      Some(List(canCreateBankLevelDynamicEntity, canCreateAnyBankLevelDynamicEntity))
//    )
//
//    lazy val createBankLevelDynamicEntity: OBPEndpoint = {
//      case "management" :: "banks" :: bankId :: "dynamic-entities" :: Nil JsonPost json -> _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          request <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//            json.extract[CreateDynamicEntityRequestJsonV600]
//          }
//          _ <- validateEntityNameV600(request.entity_name, cc.callContext)
//          internalJson = JSONFactory600.convertV600RequestToInternal(request)
//          dynamicEntity = DynamicEntityCommons(internalJson, None, cc.userId, Some(bankId))
//          result <- createDynamicEntityV600(cc, dynamicEntity)
//        } yield result
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateSystemDynamicEntity,
//      implementedInApiVersion,
//      nameOf(updateSystemDynamicEntity),
//      "PUT",
//      "/management/system-dynamic-entities/DYNAMIC_ENTITY_ID",
//      "Update System Level Dynamic Entity",
//      s"""Update a system level Dynamic Entity.
//         |
//         |This v6.0.0 endpoint accepts and returns snake_case field names with an explicit `entity_name` field.
//         |
//         |**Request format:**
//         |```json
//         |{
//         |  "entity_name": "customer_preferences",
//         |  "has_personal_entity": true,
//         |  "has_public_access": false,
//         |  "has_community_access": false,
//         |  "personal_requires_role": false,
//         |  "schema": {
//         |    "description": "User preferences updated",
//         |    "required": ["theme"],
//         |    "properties": {
//         |      "theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"},
//         |      "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"},
//         |      "notifications_enabled": {"type": "boolean", "example": "true", "description": "Whether to send notifications"}
//         |    }
//         |  }
//         |}
//         |```
//         |
//         |**Note:**
//         |* The `entity_name` must be lowercase with underscores (snake_case), e.g. `customer_preferences`. No uppercase letters or spaces allowed.
//         |* Each property can optionally include `description` (markdown text), and for string types: `minLength` and `maxLength`.
//         |* Set `has_public_access` to `true` to generate read-only public endpoints (GET only, no authentication required) under `/public/`.
//         |* Set `has_community_access` to `true` to generate read-only community endpoints (GET only, authentication required + CanGet role) under `/community/`. Community endpoints return ALL records (personal + non-personal from all users).
//         |* Set `personal_requires_role` to `true` to require the corresponding role (e.g. CanCreateDynamicEntity_, CanGetDynamicEntity_) for `/my/` personal entity endpoints. Default is `false` (any authenticated user can use `/my/` endpoints).
//         |
//         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
//      UpdateDynamicEntityRequestJsonV600(
//        entity_name = "customer_preferences",
//        has_personal_entity = Some(true),
//        has_public_access = Some(false),
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences updated", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}, "notifications_enabled": {"type": "boolean", "example": "true", "description": "Whether to send notifications"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      DynamicEntityDefinitionJsonV600(
//        dynamic_entity_id = "abc-123-def",
//        entity_name = "customer_preferences",
//        user_id = "user-456",
//        bank_id = None,
//        has_personal_entity = true,
//        has_public_access = false,
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences updated", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}, "notifications_enabled": {"type": "boolean", "example": "true", "description": "Whether to send notifications"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi),
//      Some(List(canUpdateSystemDynamicEntity))
//    )
//
//    lazy val updateSystemDynamicEntity: OBPEndpoint = {
//      case "management" :: "system-dynamic-entities" :: dynamicEntityId :: Nil JsonPut json -> _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          request <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//            json.extract[UpdateDynamicEntityRequestJsonV600]
//          }
//          _ <- validateEntityNameV600(request.entity_name, cc.callContext)
//          internalJson = JSONFactory600.convertV600UpdateRequestToInternal(request)
//          dynamicEntity = DynamicEntityCommons(internalJson, Some(dynamicEntityId), cc.userId, None)
//          result <- updateDynamicEntityV600(cc, dynamicEntity)
//        } yield result
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateBankLevelDynamicEntity,
//      implementedInApiVersion,
//      nameOf(updateBankLevelDynamicEntity),
//      "PUT",
//      "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID",
//      "Update Bank Level Dynamic Entity",
//      s"""Update a bank level Dynamic Entity.
//         |
//         |This v6.0.0 endpoint accepts and returns snake_case field names with an explicit `entity_name` field.
//         |
//         |**Request format:**
//         |```json
//         |{
//         |  "entity_name": "customer_preferences",
//         |  "has_personal_entity": true,
//         |  "has_public_access": false,
//         |  "has_community_access": false,
//         |  "personal_requires_role": false,
//         |  "schema": {
//         |    "description": "User preferences updated",
//         |    "required": ["theme"],
//         |    "properties": {
//         |      "theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"},
//         |      "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"},
//         |      "notifications_enabled": {"type": "boolean", "example": "true", "description": "Whether to send notifications"}
//         |    }
//         |  }
//         |}
//         |```
//         |
//         |**Note:**
//         |* The `entity_name` must be lowercase with underscores (snake_case), e.g. `customer_preferences`. No uppercase letters or spaces allowed.
//         |* Each property can optionally include `description` (markdown text), and for string types: `minLength` and `maxLength`.
//         |* Set `has_public_access` to `true` to generate read-only public endpoints (GET only, no authentication required) under `/public/`.
//         |* Set `has_community_access` to `true` to generate read-only community endpoints (GET only, authentication required + CanGet role) under `/community/`. Community endpoints return ALL records (personal + non-personal from all users).
//         |* Set `personal_requires_role` to `true` to require the corresponding role (e.g. CanCreateDynamicEntity_, CanGetDynamicEntity_) for `/my/` personal entity endpoints. Default is `false` (any authenticated user can use `/my/` endpoints).
//         |
//         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}""",
//      UpdateDynamicEntityRequestJsonV600(
//        entity_name = "customer_preferences",
//        has_personal_entity = Some(true),
//        has_public_access = Some(false),
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences updated", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}, "notifications_enabled": {"type": "boolean", "example": "true", "description": "Whether to send notifications"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      DynamicEntityDefinitionJsonV600(
//        dynamic_entity_id = "abc-123-def",
//        entity_name = "customer_preferences",
//        user_id = "user-456",
//        bank_id = Some("gh.29.uk"),
//        has_personal_entity = true,
//        has_public_access = false,
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences updated", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}, "notifications_enabled": {"type": "boolean", "example": "true", "description": "Whether to send notifications"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      List(
//        $BankNotFound,
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi),
//      Some(List(canUpdateBankLevelDynamicEntity))
//    )
//
//    lazy val updateBankLevelDynamicEntity: OBPEndpoint = {
//      case "management" :: "banks" :: bankId :: "dynamic-entities" :: dynamicEntityId :: Nil JsonPut json -> _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          request <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//            json.extract[UpdateDynamicEntityRequestJsonV600]
//          }
//          _ <- validateEntityNameV600(request.entity_name, cc.callContext)
//          internalJson = JSONFactory600.convertV600UpdateRequestToInternal(request)
//          dynamicEntity = DynamicEntityCommons(internalJson, Some(dynamicEntityId), cc.userId, Some(bankId))
//          result <- updateDynamicEntityV600(cc, dynamicEntity)
//        } yield result
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateMyDynamicEntity,
//      implementedInApiVersion,
//      nameOf(updateMyDynamicEntity),
//      "PUT",
//      "/my/dynamic-entities/DYNAMIC_ENTITY_ID",
//      "Update My Dynamic Entity",
//      s"""Update a Dynamic Entity that I created.
//         |
//         |This v6.0.0 endpoint accepts and returns snake_case field names with an explicit `entity_name` field.
//         |
//         |**Request format:**
//         |```json
//         |{
//         |  "entity_name": "customer_preferences",
//         |  "has_personal_entity": true,
//         |  "has_public_access": false,
//         |  "has_community_access": false,
//         |  "personal_requires_role": false,
//         |  "schema": {
//         |    "description": "User preferences updated",
//         |    "required": ["theme"],
//         |    "properties": {
//         |      "theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"},
//         |      "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"},
//         |      "notifications_enabled": {"type": "boolean", "example": "true", "description": "Whether to send notifications"}
//         |    }
//         |  }
//         |}
//         |```
//         |
//         |**Note:**
//         |* The `entity_name` must be lowercase with underscores (snake_case), e.g. `customer_preferences`. No uppercase letters or spaces allowed.
//         |* Each property can optionally include `description` (markdown text), and for string types: `minLength` and `maxLength`.
//         |* Set `has_public_access` to `true` to generate read-only public endpoints (GET only, no authentication required) under `/public/`.
//         |* Set `has_community_access` to `true` to generate read-only community endpoints (GET only, authentication required + CanGet role) under `/community/`. Community endpoints return ALL records (personal + non-personal from all users).
//         |* Set `personal_requires_role` to `true` to require the corresponding role (e.g. CanCreateDynamicEntity_, CanGetDynamicEntity_) for `/my/` personal entity endpoints. Default is `false` (any authenticated user can use `/my/` endpoints).
//         |
//         |For more information see ${Glossary.getGlossaryItemLink("My-Dynamic-Entities")}""",
//      UpdateDynamicEntityRequestJsonV600(
//        entity_name = "customer_preferences",
//        has_personal_entity = Some(true),
//        has_public_access = Some(false),
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences updated", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}, "notifications_enabled": {"type": "boolean", "example": "true", "description": "Whether to send notifications"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      DynamicEntityDefinitionJsonV600(
//        dynamic_entity_id = "abc-123-def",
//        entity_name = "customer_preferences",
//        user_id = "user-456",
//        bank_id = None,
//        has_personal_entity = true,
//        has_public_access = false,
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences updated", "required": ["theme"], "properties": {"theme": {"type": "string", "minLength": 1, "maxLength": 20, "example": "dark", "description": "The UI theme preference"}, "language": {"type": "string", "minLength": 2, "maxLength": 5, "example": "en", "description": "ISO language code"}, "notifications_enabled": {"type": "boolean", "example": "true", "description": "Whether to send notifications"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi)
//    )
//
//    lazy val updateMyDynamicEntity: OBPEndpoint = {
//      case "my" :: "dynamic-entities" :: dynamicEntityId :: Nil JsonPut json -> _ => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          // Verify the user owns this dynamic entity
//          existingEntity <- Future(
//            NewStyle.function.getDynamicEntitiesByUserId(cc.userId).find(_.dynamicEntityId.contains(dynamicEntityId))
//          )
//          _ <- Helper.booleanToFuture(s"$DynamicEntityNotFoundByDynamicEntityId dynamicEntityId = $dynamicEntityId", cc = cc.callContext) {
//            existingEntity.isDefined
//          }
//          request <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//            json.extract[UpdateDynamicEntityRequestJsonV600]
//          }
//          _ <- validateEntityNameV600(request.entity_name, cc.callContext)
//          internalJson = JSONFactory600.convertV600UpdateRequestToInternal(request)
//          dynamicEntity = DynamicEntityCommons(internalJson, Some(dynamicEntityId), cc.userId, existingEntity.get.bankId)
//          result <- updateDynamicEntityV600(cc, dynamicEntity)
//        } yield result
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteSystemDynamicEntityCascade,
//      implementedInApiVersion,
//      nameOf(deleteSystemDynamicEntityCascade),
//      "DELETE",
//      "/management/system-dynamic-entities/cascade/DYNAMIC_ENTITY_ID",
//      "Delete System Level Dynamic Entity Cascade",
//      s"""Delete a DynamicEntity specified by DYNAMIC_ENTITY_ID and all its data records.
//         |
//         |This endpoint performs a cascade delete:
//         |1. Automatically backs up the entity definition and all data records to a ZZ_BAK_ prefixed entity (e.g. my_entity is backed up to ZZ_BAK_my_entity). If a previous ZZ_BAK_ backup exists, it is overwritten.
//         |2. Deletes all data records associated with the dynamic entity
//         |3. Deletes the dynamic entity definition itself
//         |
//         |Note: Entities whose name already starts with ZZ_BAK_ are not backed up again (to avoid infinite backup chains).
//         |
//         |This operation is only allowed for non-personal entities (hasPersonalEntity=false).
//         |For personal entities (hasPersonalEntity=true), you must delete the records and definition separately.
//         |
//         |
//         |
//         |For more information see ${Glossary.getGlossaryItemLink(
//          "Dynamic-Entities"
//        )}/
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""",
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi),
//      Some(List(canDeleteCascadeSystemDynamicEntity))
//    )
//    lazy val deleteSystemDynamicEntityCascade: OBPEndpoint = {
//      case "management" :: "system-dynamic-entities" :: "cascade" :: dynamicEntityId :: Nil JsonDelete _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          deleteDynamicEntityCascadeMethod(None, dynamicEntityId, cc)
//      }
//    }
//
//    private def backupDynamicEntity(
//        entity: DynamicEntityT,
//        backupName: String,
//        dataRecords: JArray
//    ): Unit = {
//      // Clean up any existing backup
//      DynamicEntityProvider.connectorMethodProvider.vend
//        .getByEntityName(entity.bankId, backupName).foreach { existingBackup =>
//          // Delete old backup data
//          DynamicDataProvider.connectorMethodProvider.vend
//            .getAll(entity.bankId, backupName, None, false)
//            .foreach { record =>
//              DynamicDataProvider.connectorMethodProvider.vend.delete(
//                entity.bankId, backupName, record.dynamicDataId.getOrElse(""), None, false
//              )
//            }
//          // Delete old backup definition
//          DynamicEntityProvider.connectorMethodProvider.vend.delete(existingBackup)
//        }
//
//      // Create backup entity definition (rename top-level key in metadataJson)
//      val originalMetadata = json.parse(entity.metadataJson).asInstanceOf[JObject]
//      val backupMetadata = JObject(originalMetadata.obj.map {
//        case JField(name, value) if name == entity.entityName => JField(backupName, value)
//        case other => other
//      })
//      val backupEntity = DynamicEntityCommons(
//        entityName = backupName,
//        metadataJson = json.compactRender(backupMetadata),
//        dynamicEntityId = None,
//        userId = entity.userId,
//        bankId = entity.bankId,
//        hasPersonalEntity = entity.hasPersonalEntity
//      )
//      DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(backupEntity)
//
//      // Copy data records
//      val originalIdField = DynamicEntityHelper.createEntityId(entity.entityName)
//      val backupIdField = DynamicEntityHelper.createEntityId(backupName)
//      dataRecords.arr.foreach { record =>
//        val recordObj = record.asInstanceOf[JObject]
//        val transformedFields = recordObj.obj.map {
//          case JField(name, _) if name == originalIdField =>
//            JField(backupIdField, JString(java.util.UUID.randomUUID().toString))
//          case other => other
//        }
//        DynamicDataProvider.connectorMethodProvider.vend.save(
//          entity.bankId, backupName, JObject(transformedFields),
//          Some(entity.userId), entity.hasPersonalEntity
//        )
//      }
//    }
//
//    private def deleteDynamicEntityCascadeMethod(
//        bankId: Option[String],
//        dynamicEntityId: String,
//        cc: CallContext
//    ) = {
//      for {
//        // Get the dynamic entity
//        (entity, _) <- NewStyle.function.getDynamicEntityById(
//          bankId,
//          dynamicEntityId,
//          cc.callContext
//        )
//        // Check if this is a personal entity - cascade delete not allowed for personal entities
//        _ <- Helper.booleanToFuture(failMsg = CannotDeleteCascadePersonalEntity, cc = cc.callContext) {
//          !entity.hasPersonalEntity
//        }
//        // Get all data records for this entity
//        (box, _) <- NewStyle.function.invokeDynamicConnector(
//          GET_ALL,
//          entity.entityName,
//          None,
//          None,
//          entity.bankId,
//          None,
//          None,
//          false,
//          cc.callContext
//        )
//        resultList: JArray = unboxResult(
//          box.asInstanceOf[Box[JArray]],
//          entity.entityName
//        )
//        // Backup entity and data before deletion (skip if already a backup entity)
//        _ <- Future {
//          if (!entity.entityName.startsWith("ZZ_BAK_")) {
//            backupDynamicEntity(entity, s"ZZ_BAK_${entity.entityName}", resultList)
//          }
//        }
//        // Delete all data records
//        _ <- Future.sequence {
//          resultList.arr.map { record =>
//            val idFieldName = DynamicEntityHelper.createEntityId(entity.entityName)
//            val recordId = (record \ idFieldName).asInstanceOf[JString].s
//            Future {
//              DynamicDataProvider.connectorMethodProvider.vend.delete(
//                entity.bankId,
//                entity.entityName,
//                recordId,
//                None,
//                false
//              )
//            }
//          }
//        }
//        // Delete the dynamic entity definition
//        deleted: Box[Boolean] <- NewStyle.function.deleteDynamicEntity(
//          bankId,
//          dynamicEntityId
//        )
//      } yield {
//        (deleted, HttpCode.`200`(cc.callContext))
//      }
//    }
//
//    // ABAC Rule Endpoints
//    staticResourceDocs += ResourceDoc(
//      createAbacRule,
//      implementedInApiVersion,
//      nameOf(createAbacRule),
//      "POST",
//      "/management/abac-rules",
//      "Create ABAC Rule",
//      s"""Create a new ABAC (Attribute-Based Access Control) rule.
//         |
//         |ABAC rules are Scala functions that return a Boolean value indicating whether access should be granted.
//         |
//         |**Documentation:**
//         |- ${Glossary.getGlossaryItemLink("ABAC_Simple_Guide")} - Getting started with ABAC rules
//         |- ${Glossary.getGlossaryItemLink("ABAC_Parameters_Summary")} - Complete list of all 18 parameters
//         |- ${Glossary.getGlossaryItemLink("ABAC_Object_Properties_Reference")} - Detailed property reference
//         |- ${Glossary.getGlossaryItemLink("ABAC_Testing_Examples")} - Testing examples and patterns
//         |
//         |The rule function receives 18 parameters including authenticatedUser, attributes, auth context, and optional objects (bank, account, transaction, etc.).
//         |
//         |Example rule code:
//         |```scala
//         |// Allow access only if authenticated user is admin
//         |authenticatedUser.emailAddress.contains("admin")
//         |```
//         |
//         |```scala
//         |// Allow access only to accounts with balance > 1000
//         |accountOpt.exists(_.balance.toDouble > 1000.0)
//         |```
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      CreateAbacRuleJsonV600(
//        rule_name = "admin_only",
//        rule_code = """user.emailAddress.contains("admin")""",
//        description = "Only allow access to users with admin email",
//        policy = "user-access,admin",
//        is_active = true
//      ),
//      AbacRuleJsonV600(
//        abac_rule_id = "abc123",
//        rule_name = "admin_only",
//        rule_code = """user.emailAddress.contains("admin")""",
//        is_active = true,
//        description = "Only allow access to users with admin email",
//        policy = "user-access,admin",
//        created_by_user_id = "user123",
//        updated_by_user_id = "user123"
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canCreateAbacRule))
//    )
//
//    lazy val createAbacRule: OBPEndpoint = {
//      case "management" :: "abac-rules" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canCreateAbacRule, callContext)
//            createJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, callContext) {
//              json.extract[CreateAbacRuleJsonV600]
//            }
//            _ <- NewStyle.function.tryons(s"Rule name must not be empty", 400, callContext) {
//              createJson.rule_name.nonEmpty
//            }
//            _ <- NewStyle.function.tryons(s"Rule code must not be empty", 400, callContext) {
//              createJson.rule_code.nonEmpty
//            }
//            // Validate rule code by attempting to compile it (includes statistical permissiveness check)
//            _ <- AbacRuleEngine.validateRuleCodeAsync(createJson.rule_code) map {
//              unboxFullOrFail(_, callContext, s"Invalid ABAC rule code", 400)
//            }
//            rule <- Future {
//              MappedAbacRuleProvider.createAbacRule(
//                ruleName = createJson.rule_name,
//                ruleCode = createJson.rule_code,
//                description = createJson.description,
//                policy = createJson.policy,
//                isActive = createJson.is_active,
//                createdBy = user.userId
//              )
//            } map {
//              unboxFullOrFail(_, callContext, s"Could not create ABAC rule", 400)
//            }
//          } yield {
//            (createAbacRuleJsonV600(rule), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAbacRule,
//      implementedInApiVersion,
//      nameOf(getAbacRule),
//      "GET",
//      "/management/abac-rules/ABAC_RULE_ID",
//      "Get ABAC Rule",
//      s"""Get an ABAC rule by its ID.
//         |
//         |**Documentation:**
//         |- ${Glossary.getGlossaryItemLink("ABAC_Simple_Guide")} - Getting started with ABAC rules
//         |- ${Glossary.getGlossaryItemLink("ABAC_Parameters_Summary")} - Complete list of all 18 parameters
//         |- ${Glossary.getGlossaryItemLink("ABAC_Object_Properties_Reference")} - Detailed property reference
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      AbacRuleJsonV600(
//        abac_rule_id = "abc123",
//        rule_name = "admin_only",
//        rule_code = """user.emailAddress.contains("admin")""",
//        is_active = true,
//        description = "Only allow access to users with admin email",
//        policy = "user-access,admin",
//        created_by_user_id = "user123",
//        updated_by_user_id = "user123"
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canGetAbacRule))
//    )
//
//    lazy val getAbacRule: OBPEndpoint = {
//      case "management" :: "abac-rules" :: ruleId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetAbacRule, callContext)
//            rule <- Future {
//              MappedAbacRuleProvider.getAbacRuleById(ruleId)
//            } map {
//              unboxFullOrFail(_, callContext, s"ABAC Rule not found with ID: $ruleId", 404)
//            }
//          } yield {
//            (createAbacRuleJsonV600(rule), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAbacRules,
//      implementedInApiVersion,
//      nameOf(getAbacRules),
//      "GET",
//      "/management/abac-rules",
//      "Get ABAC Rules",
//      s"""Get all ABAC rules.
//         |
//         |**Documentation:**
//         |- ${Glossary.getGlossaryItemLink("ABAC_Simple_Guide")} - Getting started with ABAC rules
//         |- ${Glossary.getGlossaryItemLink("ABAC_Parameters_Summary")} - Complete list of all 18 parameters
//         |- ${Glossary.getGlossaryItemLink("ABAC_Object_Properties_Reference")} - Detailed property reference
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      AbacRulesJsonV600(
//        abac_rules = List(
//          AbacRuleJsonV600(
//            abac_rule_id = "abc123",
//            rule_name = "admin_only",
//            rule_code = """user.emailAddress.contains("admin")""",
//            is_active = true,
//            description = "Only allow access to users with admin email",
//            policy = "user-access,admin",
//            created_by_user_id = "user123",
//            updated_by_user_id = "user123"
//          )
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canGetAbacRule))
//    )
//
//    lazy val getAbacRules: OBPEndpoint = {
//      case "management" :: "abac-rules" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetAbacRule, callContext)
//            rules <- Future {
//              MappedAbacRuleProvider.getAllAbacRules()
//            }
//          } yield {
//            (createAbacRulesJsonV600(rules), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAbacRulesByPolicy,
//      implementedInApiVersion,
//      nameOf(getAbacRulesByPolicy),
//      "GET",
//      "/management/abac-rules/policy/POLICY",
//      "Get ABAC Rules by Policy",
//      s"""Get all ABAC rules that belong to a specific policy.
//         |
//         |Multiple rules can share the same policy. Rules with multiple policies (comma-separated)
//         |will be returned if any of their policies match the requested policy.
//         |
//         |**Documentation:**
//         |- ${Glossary.getGlossaryItemLink("ABAC_Simple_Guide")} - Getting started with ABAC rules
//         |- ${Glossary.getGlossaryItemLink("ABAC_Parameters_Summary")} - Complete list of all 18 parameters
//         |- ${Glossary.getGlossaryItemLink("ABAC_Object_Properties_Reference")} - Detailed property reference
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      AbacRulesJsonV600(
//        abac_rules = List(
//          AbacRuleJsonV600(
//            abac_rule_id = "abc123",
//            rule_name = "admin_only",
//            rule_code = """user.emailAddress.contains("admin")""",
//            is_active = true,
//            description = "Only allow access to users with admin email",
//            policy = "user-access,admin",
//            created_by_user_id = "user123",
//            updated_by_user_id = "user123"
//          ),
//          AbacRuleJsonV600(
//            abac_rule_id = "def456",
//            rule_name = "admin_department_check",
//            rule_code = """user.department == "admin"""",
//            is_active = true,
//            description = "Check if user is in admin department",
//            policy = "admin",
//            created_by_user_id = "user123",
//            updated_by_user_id = "user123"
//          )
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canGetAbacRule))
//    )
//
//    lazy val getAbacRulesByPolicy: OBPEndpoint = {
//      case "management" :: "abac-rules" :: "policy" :: policy :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetAbacRule, callContext)
//            rules <- Future {
//              MappedAbacRuleProvider.getAbacRulesByPolicy(policy)
//            }
//          } yield {
//            (createAbacRulesJsonV600(rules), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateAbacRule,
//      implementedInApiVersion,
//      nameOf(updateAbacRule),
//      "PUT",
//      "/management/abac-rules/ABAC_RULE_ID",
//      "Update ABAC Rule",
//      s"""Update an existing ABAC rule.
//         |
//         |**Documentation:**
//         |- ${Glossary.getGlossaryItemLink("ABAC_Simple_Guide")} - Getting started with ABAC rules
//         |- ${Glossary.getGlossaryItemLink("ABAC_Parameters_Summary")} - Complete list of all 18 parameters
//         |- ${Glossary.getGlossaryItemLink("ABAC_Object_Properties_Reference")} - Detailed property reference
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      UpdateAbacRuleJsonV600(
//        rule_name = "admin_only_updated",
//        rule_code = """user.emailAddress.contains("admin") && user.provider == "obp"""",
//        description = "Only allow access to OBP admin users",
//        policy = "user-access,admin,obp",
//        is_active = true
//      ),
//      AbacRuleJsonV600(
//        abac_rule_id = "abc123",
//        rule_name = "admin_only_updated",
//        rule_code = """user.emailAddress.contains("admin") && user.provider == "obp"""",
//        is_active = true,
//        description = "Only allow access to OBP admin users",
//        policy = "user-access,admin,obp",
//        created_by_user_id = "user123",
//        updated_by_user_id = "user456"
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canUpdateAbacRule))
//    )
//
//    lazy val updateAbacRule: OBPEndpoint = {
//      case "management" :: "abac-rules" :: ruleId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canUpdateAbacRule, callContext)
//            updateJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, callContext) {
//              json.extract[UpdateAbacRuleJsonV600]
//            }
//            // Validate rule code by attempting to compile it (includes statistical permissiveness check)
//            _ <- AbacRuleEngine.validateRuleCodeAsync(updateJson.rule_code) map {
//              unboxFullOrFail(_, callContext, s"Invalid ABAC rule code", 400)
//            }
//            rule <- Future {
//              MappedAbacRuleProvider.updateAbacRule(
//                ruleId = ruleId,
//                ruleName = updateJson.rule_name,
//                ruleCode = updateJson.rule_code,
//                description = updateJson.description,
//                policy = updateJson.policy,
//                isActive = updateJson.is_active,
//                updatedBy = user.userId
//              )
//            } map {
//              unboxFullOrFail(_, callContext, s"Could not update ABAC rule with ID: $ruleId", 400)
//            }
//            // Clear rule from cache after update
//            _ <- Future {
//              AbacRuleEngine.clearRuleFromCache(ruleId)
//            }
//          } yield {
//            (createAbacRuleJsonV600(rule), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteAbacRule,
//      implementedInApiVersion,
//      nameOf(deleteAbacRule),
//      "DELETE",
//      "/management/abac-rules/ABAC_RULE_ID",
//      "Delete ABAC Rule",
//      s"""Delete an ABAC rule by its ID.
//         |
//         |**Documentation:**
//         |- ${Glossary.getGlossaryItemLink("ABAC_Simple_Guide")} - Getting started with ABAC rules
//         |- ${Glossary.getGlossaryItemLink("ABAC_Parameters_Summary")} - Complete list of all 18 parameters
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canDeleteAbacRule))
//    )
//
//    lazy val deleteAbacRule: OBPEndpoint = {
//      case "management" :: "abac-rules" :: ruleId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canDeleteAbacRule, callContext)
//            deleted <- Future {
//              MappedAbacRuleProvider.deleteAbacRule(ruleId)
//            } map {
//              unboxFullOrFail(_, callContext, s"Could not delete ABAC rule with ID: $ruleId", 400)
//            }
//            // Clear rule from cache after deletion
//            _ <- Future {
//              AbacRuleEngine.clearRuleFromCache(ruleId)
//            }
//          } yield {
//            (Full(deleted), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAbacRuleSchema,
//      implementedInApiVersion,
//      nameOf(getAbacRuleSchema),
//      "GET",
//      "/management/abac-rules-schema",
//      "Get ABAC Rule Schema",
//      s"""Get schema information about ABAC rule structure for building rule code.
//         |
//         |This endpoint returns schema information including:
//         |- All 18 parameters available in ABAC rules
//         |- Object types (User, Bank, Account, etc.) and their properties
//         |- Available operators and syntax
//         |- Example rules
//         |
//         |This schema information is useful for:
//         |- Building rule editors with auto-completion
//         |- Validating rule syntax in frontends
//         |- AI agents that help construct rules
//         |- Dynamic form builders
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      AbacRuleSchemaJsonV600(
//        parameters = List(
//          AbacParameterJsonV600(
//            name = "authenticatedUser",
//            `type` = "User",
//            description = "The logged-in user (always present)",
//            required = true,
//            category = "User"
//          )
//        ),
//        object_types = List(
//          AbacObjectTypeJsonV600(
//            name = "User",
//            description = "User object with profile information",
//            properties = List(
//              AbacObjectPropertyJsonV600(
//                name = "userId",
//                `type` = "String",
//                description = "Unique user ID"
//              )
//            )
//          )
//        ),
//        examples = List(
//          AbacRuleExampleJsonV600(
//            rule_name = "Check User Identity",
//            rule_code = "authenticatedUser.userId == user.userId",
//            description = "Verify that the authenticated user matches the target user",
//            policy = "user-access",
//            is_active = true
//          ),
//          AbacRuleExampleJsonV600(
//            rule_name = "Check Specific Bank",
//            rule_code = "bankOpt.isDefined && bankOpt.get.bankId.value == \"gh.29.uk\"",
//            policy = "bank-access",
//            description = "Verify that the bank context is defined and matches a specific bank ID",
//            is_active = true
//          )
//        ),
//        available_operators = List("==", "!=", "&&", "||", "!", ">", "<", ">=", "<=", "contains", "isDefined"),
//        notes = List(
//          "Only authenticatedUser is guaranteed to exist (not wrapped in Option)",
//          "All other objects are Option types - use isDefined or pattern matching",
//          "Attributes are Lists - use .find(), .exists(), .forall() etc."
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canGetAbacRule))
//    )
//
//    lazy val getAbacRuleSchema: OBPEndpoint = {
//      case "management" :: "abac-rules-schema" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetAbacRule, callContext)
//          } yield {
//            val metadata = AbacRuleSchemaJsonV600(
//              parameters = List(
//                AbacParameterJsonV600("authenticatedUser", "User", "The logged-in user (always present)", required = true, "User"),
//                AbacParameterJsonV600("authenticatedUserAttributes", "List[UserAttributeTrait]", "Non-personal attributes of authenticated user", required = true, "User"),
//                AbacParameterJsonV600("authenticatedUserAuthContext", "List[UserAuthContext]", "Auth context of authenticated user", required = true, "User"),
//                AbacParameterJsonV600("authenticatedUserEntitlements", "List[Entitlement]", "Entitlements (roles) of authenticated user", required = true, "User"),
//                AbacParameterJsonV600("onBehalfOfUserOpt", "Option[User]", "User being acted on behalf of (delegation)", required = false, "User"),
//                AbacParameterJsonV600("onBehalfOfUserAttributes", "List[UserAttributeTrait]", "Attributes of delegation user", required = false, "User"),
//                AbacParameterJsonV600("onBehalfOfUserAuthContext", "List[UserAuthContext]", "Auth context of delegation user", required = false, "User"),
//                AbacParameterJsonV600("onBehalfOfUserEntitlements", "List[Entitlement]", "Entitlements (roles) of delegation user", required = false, "User"),
//                AbacParameterJsonV600("userOpt", "Option[User]", "Target user being evaluated", required = false, "User"),
//                AbacParameterJsonV600("userAttributes", "List[UserAttributeTrait]", "Attributes of target user", required = false, "User"),
//                AbacParameterJsonV600("bankOpt", "Option[Bank]", "Bank context", required = false, "Bank"),
//                AbacParameterJsonV600("bankAttributes", "List[BankAttributeTrait]", "Bank attributes", required = false, "Bank"),
//                AbacParameterJsonV600("accountOpt", "Option[BankAccount]", "Account context", required = false, "Account"),
//                AbacParameterJsonV600("accountAttributes", "List[AccountAttribute]", "Account attributes", required = false, "Account"),
//                AbacParameterJsonV600("transactionOpt", "Option[Transaction]", "Transaction context", required = false, "Transaction"),
//                AbacParameterJsonV600("transactionAttributes", "List[TransactionAttribute]", "Transaction attributes", required = false, "Transaction"),
//                AbacParameterJsonV600("transactionRequestOpt", "Option[TransactionRequest]", "Transaction request context", required = false, "TransactionRequest"),
//                AbacParameterJsonV600("transactionRequestAttributes", "List[TransactionRequestAttributeTrait]", "Transaction request attributes", required = false, "TransactionRequest"),
//                AbacParameterJsonV600("customerOpt", "Option[Customer]", "Customer context", required = false, "Customer"),
//                AbacParameterJsonV600("customerAttributes", "List[CustomerAttribute]", "Customer attributes", required = false, "Customer"),
//                AbacParameterJsonV600("callContext", "Option[CallContext]", "Request call context with metadata (IP, user agent, etc.)", required = false, "Context")
//              ),
//              object_types = List(
//                AbacObjectTypeJsonV600("User", "User object with profile and authentication information", List(
//                  AbacObjectPropertyJsonV600("userId", "String", "Unique user ID"),
//                  AbacObjectPropertyJsonV600("emailAddress", "String", "User email address"),
//                  AbacObjectPropertyJsonV600("provider", "String", "Authentication provider (e.g., 'obp')"),
//                  AbacObjectPropertyJsonV600("name", "String", "User display name"),
//                  AbacObjectPropertyJsonV600("idGivenByProvider", "String", "ID given by provider (same as username)"),
//                  AbacObjectPropertyJsonV600("createdByConsentId", "Option[String]", "Consent ID that created the user (if any)"),
//                  AbacObjectPropertyJsonV600("isDeleted", "Option[Boolean]", "Whether user is deleted")
//                )),
//                AbacObjectTypeJsonV600("Bank", "Bank object", List(
//                  AbacObjectPropertyJsonV600("bankId", "BankId", "Bank ID"),
//                  AbacObjectPropertyJsonV600("fullName", "String", "Bank full name"),
//                  AbacObjectPropertyJsonV600("shortName", "String", "Bank short name"),
//                  AbacObjectPropertyJsonV600("logoUrl", "String", "Bank logo URL"),
//                  AbacObjectPropertyJsonV600("websiteUrl", "String", "Bank website URL"),
//                  AbacObjectPropertyJsonV600("bankRoutingScheme", "String", "Bank routing scheme"),
//                  AbacObjectPropertyJsonV600("bankRoutingAddress", "String", "Bank routing address")
//                )),
//                AbacObjectTypeJsonV600("BankAccount", "Bank account object", List(
//                  AbacObjectPropertyJsonV600("accountId", "AccountId", "Account ID"),
//                  AbacObjectPropertyJsonV600("bankId", "BankId", "Bank ID"),
//                  AbacObjectPropertyJsonV600("accountType", "String", "Account type"),
//                  AbacObjectPropertyJsonV600("balance", "BigDecimal", "Account balance"),
//                  AbacObjectPropertyJsonV600("currency", "String", "Account currency"),
//                  AbacObjectPropertyJsonV600("name", "String", "Account name"),
//                  AbacObjectPropertyJsonV600("label", "String", "Account label"),
//                  AbacObjectPropertyJsonV600("number", "String", "Account number"),
//                  AbacObjectPropertyJsonV600("lastUpdate", "Date", "Last update date"),
//                  AbacObjectPropertyJsonV600("branchId", "String", "Branch ID"),
//                  AbacObjectPropertyJsonV600("accountRoutings", "List[AccountRouting]", "Account routings")
//                )),
//                AbacObjectTypeJsonV600("Transaction", "Transaction object", List(
//                  AbacObjectPropertyJsonV600("id", "TransactionId", "Transaction ID"),
//                  AbacObjectPropertyJsonV600("uuid", "String", "Universally unique ID"),
//                  AbacObjectPropertyJsonV600("thisAccount", "BankAccount", "This account"),
//                  AbacObjectPropertyJsonV600("otherAccount", "Counterparty", "Other account/counterparty"),
//                  AbacObjectPropertyJsonV600("transactionType", "String", "Transaction type (e.g., cash withdrawal)"),
//                  AbacObjectPropertyJsonV600("amount", "BigDecimal", "Transaction amount"),
//                  AbacObjectPropertyJsonV600("currency", "String", "Transaction currency (ISO 4217)"),
//                  AbacObjectPropertyJsonV600("description", "Option[String]", "Bank provided label"),
//                  AbacObjectPropertyJsonV600("startDate", "Date", "Date transaction was initiated"),
//                  AbacObjectPropertyJsonV600("finishDate", "Option[Date]", "Date money finished changing hands"),
//                  AbacObjectPropertyJsonV600("balance", "BigDecimal", "New balance after transaction"),
//                  AbacObjectPropertyJsonV600("status", "Option[String]", "Transaction status")
//                )),
//                AbacObjectTypeJsonV600("TransactionRequest", "Transaction request object", List(
//                  AbacObjectPropertyJsonV600("id", "TransactionRequestId", "Transaction request ID"),
//                  AbacObjectPropertyJsonV600("type", "String", "Transaction request type"),
//                  AbacObjectPropertyJsonV600("from", "TransactionRequestAccount", "From account"),
//                  AbacObjectPropertyJsonV600("status", "String", "Transaction request status"),
//                  AbacObjectPropertyJsonV600("start_date", "Date", "Start date"),
//                  AbacObjectPropertyJsonV600("end_date", "Date", "End date"),
//                  AbacObjectPropertyJsonV600("transaction_ids", "String", "Associated transaction IDs"),
//                  AbacObjectPropertyJsonV600("charge", "TransactionRequestCharge", "Charge information"),
//                  AbacObjectPropertyJsonV600("this_bank_id", "BankId", "This bank ID"),
//                  AbacObjectPropertyJsonV600("this_account_id", "AccountId", "This account ID"),
//                  AbacObjectPropertyJsonV600("counterparty_id", "CounterpartyId", "Counterparty ID")
//                )),
//                AbacObjectTypeJsonV600("Customer", "Customer object", List(
//                  AbacObjectPropertyJsonV600("customerId", "String", "Customer ID (UUID)"),
//                  AbacObjectPropertyJsonV600("bankId", "String", "Bank ID"),
//                  AbacObjectPropertyJsonV600("number", "String", "Customer number (bank identifier)"),
//                  AbacObjectPropertyJsonV600("legalName", "String", "Customer legal name"),
//                  AbacObjectPropertyJsonV600("mobileNumber", "String", "Customer mobile number"),
//                  AbacObjectPropertyJsonV600("email", "String", "Customer email"),
//                  AbacObjectPropertyJsonV600("dateOfBirth", "Date", "Date of birth"),
//                  AbacObjectPropertyJsonV600("relationshipStatus", "String", "Relationship status"),
//                  AbacObjectPropertyJsonV600("dependents", "Integer", "Number of dependents")
//                )),
//                AbacObjectTypeJsonV600("UserAttributeTrait", "User attribute", List(
//                  AbacObjectPropertyJsonV600("name", "String", "Attribute name"),
//                  AbacObjectPropertyJsonV600("value", "String", "Attribute value"),
//                  AbacObjectPropertyJsonV600("attributeType", "AttributeType", "Attribute type (STRING, INTEGER, DOUBLE, DATE_WITH_DAY)")
//                )),
//                AbacObjectTypeJsonV600("AccountAttribute", "Account attribute", List(
//                  AbacObjectPropertyJsonV600("name", "String", "Attribute name"),
//                  AbacObjectPropertyJsonV600("value", "String", "Attribute value"),
//                  AbacObjectPropertyJsonV600("attributeType", "AttributeType", "Attribute type")
//                )),
//                AbacObjectTypeJsonV600("TransactionAttribute", "Transaction attribute", List(
//                  AbacObjectPropertyJsonV600("name", "String", "Attribute name"),
//                  AbacObjectPropertyJsonV600("value", "String", "Attribute value"),
//                  AbacObjectPropertyJsonV600("attributeType", "AttributeType", "Attribute type")
//                )),
//                AbacObjectTypeJsonV600("CustomerAttribute", "Customer attribute", List(
//                  AbacObjectPropertyJsonV600("name", "String", "Attribute name"),
//                  AbacObjectPropertyJsonV600("value", "String", "Attribute value"),
//                  AbacObjectPropertyJsonV600("attributeType", "AttributeType", "Attribute type")
//                )),
//                AbacObjectTypeJsonV600("Entitlement", "User entitlement (role)", List(
//                  AbacObjectPropertyJsonV600("entitlementId", "String", "Entitlement ID"),
//                  AbacObjectPropertyJsonV600("roleName", "String", "Role name (e.g., CanCreateAccount, CanReadTransactions)"),
//                  AbacObjectPropertyJsonV600("bankId", "String", "Bank ID (empty string for system-wide roles)"),
//                  AbacObjectPropertyJsonV600("userId", "String", "User ID this entitlement belongs to")
//                )),
//                AbacObjectTypeJsonV600("CallContext", "Request context with metadata", List(
//                  AbacObjectPropertyJsonV600("correlationId", "String", "Correlation ID for request tracking"),
//                  AbacObjectPropertyJsonV600("url", "Option[String]", "Request URL"),
//                  AbacObjectPropertyJsonV600("verb", "Option[String]", "HTTP verb (GET, POST, etc.)"),
//                  AbacObjectPropertyJsonV600("ipAddress", "Option[String]", "Client IP address"),
//                  AbacObjectPropertyJsonV600("userAgent", "Option[String]", "Client user agent"),
//                  AbacObjectPropertyJsonV600("implementedByPartialFunction", "Option[String]", "Endpoint implementation name"),
//                  AbacObjectPropertyJsonV600("startTime", "Option[Date]", "Request start time"),
//                  AbacObjectPropertyJsonV600("endTime", "Option[Date]", "Request end time")
//                ))
//              ),
//              examples = List(
//                AbacRuleExampleJsonV600(
//                  rule_name = "Branch Manager Internal Account Access",
//                  rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadAccountsAtOneBank\") && authenticatedUserAttributes.exists(a => a.name == \"branch\" && accountAttributes.exists(aa => aa.name == \"branch\" && a.value == aa.value)) && callContext.exists(_.verb.exists(_ == \"GET\")) && accountOpt.exists(_.accountType == \"CURRENT\")",
//                  description = "Allow GET access to current accounts when user has CanReadAccountsAtOneBank role and branch matches account's branch",
//                  policy = "account-access",
//                  is_active = true
//                ),
//                AbacRuleExampleJsonV600(
//                  rule_name = "Internal Network High-Value Transaction Review",
//                  rule_code = "callContext.exists(_.ipAddress.exists(_.startsWith(\"10.\"))) && authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadTransactionsAtOneBank\") && transactionOpt.exists(_.amount > 10000)",
//                  description = "Allow users with CanReadTransactionsAtOneBank role on internal network to review high-value transactions over 10,000",
//                  policy = "transaction-access",
//                  is_active = true
//                ),
//                AbacRuleExampleJsonV600(
//                  rule_name = "Department Head Same-Department Account Read where overdrawn",
//                  rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadAccountsAtOneBank\") && authenticatedUserAttributes.exists(ua => ua.name == \"department\" && accountAttributes.exists(aa => aa.name == \"department\" && ua.value == aa.value)) && callContext.exists(_.url.exists(_.contains(\"/accounts/\"))) && accountOpt.exists(_.balance < 0)",
//                  description = "Allow users with CanReadAccountsAtOneBank role to read overdrawn accounts in their department",
//                  policy = "account-access",
//                  is_active = true
//                ),
//                AbacRuleExampleJsonV600(
//                  rule_name = "Manager Internal Network Transaction Approval",
//                  rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanCreateTransactionRequest\") && callContext.exists(_.ipAddress.exists(ip => ip.startsWith(\"10.\") || ip.startsWith(\"192.168.\"))) && transactionRequestOpt.exists(tr => tr.status == \"PENDING\" && tr.charge.value.toDouble < 50000)",
//                  description = "Allow users with CanCreateTransactionRequest role on internal network to approve pending transaction requests under 50,000",
//                  policy = "transaction-request",
//                  is_active = true
//                ),
//                AbacRuleExampleJsonV600(
//                  rule_name = "KYC Officer Customer Creation from Branch",
//                  rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanCreateCustomer\") && authenticatedUserAttributes.exists(a => a.name == \"certification\" && a.value == \"kyc_certified\") && callContext.exists(_.verb.exists(_ == \"POST\")) && callContext.exists(_.ipAddress.exists(_.startsWith(\"10.20.\"))) && customerAttributes.exists(ca => ca.name == \"onboarding_status\" && ca.value == \"pending\")",
//                  description = "Allow users with CanCreateCustomer role and KYC certification to create customers via POST from branch network (10.20.x.x) when status is pending",
//                  policy = "customer-access",
//                  is_active = true
//                ),
//                AbacRuleExampleJsonV600(
//                  rule_name = "International Team Foreign Currency Transaction",
//                  rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadTransactionsAtOneBank\") && authenticatedUserAttributes.exists(a => a.name == \"team\" && a.value == \"international\") && callContext.exists(_.url.exists(_.contains(\"/transactions/\"))) && transactionOpt.exists(t => t.currency != \"USD\" && t.amount < 100000) && accountOpt.exists(a => accountAttributes.exists(aa => aa.name == \"international_enabled\" && aa.value == \"true\"))",
//                  description = "Allow international team users with CanReadTransactionsAtOneBank role to access foreign currency transactions under 100k on international-enabled accounts",
//                  policy = "transaction-access",
//                  is_active = true
//                ),
//                AbacRuleExampleJsonV600(
//                  rule_name = "Assistant with Limited Delegation Account View",
//                  rule_code = "onBehalfOfUserOpt.isDefined && onBehalfOfUserEntitlements.exists(e => e.roleName == \"CanReadAccountsAtOneBank\") && authenticatedUserAttributes.exists(a => a.name == \"assistant_of\" && onBehalfOfUserOpt.exists(u => a.value == u.userId)) && callContext.exists(_.verb.exists(_ == \"GET\")) && accountOpt.exists(a => accountAttributes.exists(aa => aa.name == \"tier\" && List(\"gold\", \"platinum\").contains(aa.value)))",
//                  description = "Allow assistants to view gold/platinum accounts via GET when acting on behalf of a user with CanReadAccountsAtOneBank role",
//                  policy = "account-access",
//                  is_active = true
//                ),
//                AbacRuleExampleJsonV600(
//                  rule_name = "Fraud Analyst High-Risk Transaction Access",
//                  rule_code = "authenticatedUserEntitlements.exists(e => e.roleName == \"CanReadTransactionsAtOneBank\") && callContext.exists(c => c.verb.exists(_ == \"GET\") && c.implementedByPartialFunction.exists(_.contains(\"Transaction\"))) && transactionAttributes.exists(ta => ta.name == \"risk_score\" && ta.value.toInt >= 75) && transactionOpt.exists(_.status.exists(_ != \"COMPLETED\"))",
//                  description = "Allow users with CanReadTransactionsAtOneBank role to GET high-risk (score ≥75) non-completed transactions",
//                  policy = "transaction-access",
//                  is_active = true
//                )
//              ),
//              available_operators = List(
//                "==", "!=", "&&", "||", "!", ">", "<", ">=", "<=",
//                "contains", "startsWith", "endsWith",
//                "isDefined", "isEmpty", "nonEmpty",
//                "exists", "forall", "find", "filter",
//                "get", "getOrElse"
//              ),
//              notes = List(
//                "PARAMETER NAMES: Use authenticatedUser, userOpt, accountOpt, bankOpt, transactionOpt, etc. (NOT user, account, bank)",
//                "PROPERTY NAMES: Use camelCase - userId (NOT user_id), accountId (NOT account_id), emailAddress (NOT email_address)",
//                "OPTION TYPES: Only authenticatedUser is guaranteed to exist. All others are Option types - check isDefined before using .get",
//                "ATTRIBUTES: All attributes are Lists - use Scala collection methods like exists(), find(), filter()",
//                "SAFE OPTION HANDLING: Use pattern matching: userOpt match { case Some(u) => u.userId == ... case None => false }",
//                "RETURN TYPE: Rule must return Boolean - true = access granted, false = access denied",
//                "AUTO-FETCHING: Objects are automatically fetched based on IDs passed to execute endpoint",
//                "COMMON MISTAKE: Writing 'user.user_id' instead of 'userOpt.get.userId' or 'authenticatedUser.userId'"
//              )
//            )
//            (metadata, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAbacPolicies,
//      implementedInApiVersion,
//      nameOf(getAbacPolicies),
//      "GET",
//      "/management/abac-policies",
//      "Get ABAC Policies",
//      s"""Get the list of allowed ABAC policy names.
//         |
//         |ABAC rules are organized by policies. Each rule must have at least one policy assigned.
//         |Rules can have multiple policies (comma-separated). This endpoint returns the list of
//         |standardized policy names that should be used when creating or updating rules.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      AbacPoliciesJsonV600(
//        policies = List(
//          AbacPolicyJsonV600(
//            policy = "account-access",
//            description = "Rules for controlling access to account information"
//          )
//        )
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canGetAbacRule))
//    )
//
//    lazy val getAbacPolicies: OBPEndpoint = {
//      case "management" :: "abac-policies" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetAbacRule, callContext)
//          } yield {
//            val policies = Constant.ABAC_POLICIES.map { policy =>
//              AbacPolicyJsonV600(
//                policy = policy,
//                description = Constant.ABAC_POLICY_DESCRIPTIONS.getOrElse(policy, "No description available")
//              )
//            }
//
//            (AbacPoliciesJsonV600(policies), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      validateAbacRule,
//      implementedInApiVersion,
//      nameOf(validateAbacRule),
//      "POST",
//      "/management/abac-rules/validate",
//      "Validate ABAC Rule",
//      s"""Validate ABAC rule code syntax and structure without creating or executing the rule.
//         |
//         |This endpoint performs the following validations:
//         |- Parse the rule_code as a Scala expression
//         |- Validate syntax - check for parsing errors
//         |- Validate field references - check if referenced objects/fields exist
//         |- Check type consistency - verify the expression returns a Boolean
//         |
//         |**Available ABAC Context Objects:**
//         |- AuthenticatedUser - The user who is logged in
//         |- OnBehalfOfUser - Optional delegation user
//         |- User - Target user being evaluated
//         |- Bank, Account, View, Transaction, TransactionRequest, Customer
//         |- Attributes for each entity (e.g., userAttributes, accountAttributes)
//         |
//         |**Documentation:**
//         |- ${Glossary.getGlossaryItemLink("ABAC_Simple_Guide")} - Getting started with ABAC rules
//         |- ${Glossary.getGlossaryItemLink("ABAC_Parameters_Summary")} - Complete list of all 18 parameters
//         |- ${Glossary.getGlossaryItemLink("ABAC_Object_Properties_Reference")} - Detailed property reference
//         |
//         |This is a "dry-run" validation that does NOT save or execute the rule.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      ValidateAbacRuleJsonV600(
//        rule_code = """AuthenticatedUser.user_id == Account.owner_id"""
//      ),
//      ValidateAbacRuleSuccessJsonV600(
//        valid = true,
//        message = "ABAC rule code is valid"
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canCreateAbacRule))
//    )
//
//    lazy val validateAbacRule: OBPEndpoint = {
//      case "management" :: "abac-rules" :: "validate" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canCreateAbacRule, callContext)
//            validateJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, callContext) {
//              json.extract[ValidateAbacRuleJsonV600]
//            }
//            _ <- NewStyle.function.tryons(s"$AbacRuleCodeEmpty", 400, callContext) {
//              validateJson.rule_code.trim.nonEmpty
//            }
//            validationResult <- AbacRuleEngine.validateRuleCodeAsync(validateJson.rule_code).map {
//                case Full(msg) =>
//                  Full(ValidateAbacRuleSuccessJsonV600(
//                    valid = true,
//                    message = msg
//                  ))
//                case Failure(errorMsg, _, _) =>
//                  // Extract error details from the error message
//                  val cleanError = errorMsg.replace("Invalid ABAC rule code: ", "").replace("Failed to compile ABAC rule: ", "")
//
//                  // Determine the proper OBP error message and error type
//                  val (obpErrorMessage, errorType) = if (cleanError.toLowerCase.contains("too permissive") || cleanError.toLowerCase.contains("tautological")) {
//                    val errorConst = if (cleanError.toLowerCase.contains("statistical")) AbacRuleStatisticallyTooPermissive else AbacRuleTooPermissive
//                    (errorConst, "PermissivenessError")
//                  } else if (cleanError.toLowerCase.contains("type mismatch") || cleanError.toLowerCase.contains("found:") && cleanError.toLowerCase.contains("required: boolean")) {
//                    (AbacRuleTypeMismatch, "TypeError")
//                  } else if (cleanError.toLowerCase.contains("syntax") || cleanError.toLowerCase.contains("parse")) {
//                    (AbacRuleSyntaxError, "SyntaxError")
//                  } else if (cleanError.toLowerCase.contains("not found") || cleanError.toLowerCase.contains("not a member")) {
//                    (AbacRuleFieldReferenceError, "FieldReferenceError")
//                  } else if (cleanError.toLowerCase.contains("compilation failed") || cleanError.toLowerCase.contains("reflective compilation has failed")) {
//                    (AbacRuleCompilationFailed, "CompilationError")
//                  } else {
//                    (AbacRuleValidationFailed, "ValidationError")
//                  }
//
//                  Full(ValidateAbacRuleFailureJsonV600(
//                    valid = false,
//                    error = cleanError,
//                    message = obpErrorMessage,
//                    details = ValidateAbacRuleErrorDetailsJsonV600(
//                      error_type = errorType
//                    )
//                  ))
//                case Empty =>
//                  Full(ValidateAbacRuleFailureJsonV600(
//                    valid = false,
//                    error = "Unknown validation error",
//                    message = AbacRuleValidationFailed,
//                    details = ValidateAbacRuleErrorDetailsJsonV600(
//                      error_type = "UnknownError"
//                    )
//                  ))
//              } map {
//              unboxFullOrFail(_, callContext, AbacRuleValidationFailed, 400)
//            }
//          } yield {
//            (validationResult, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      validateDynamicResourceDoc,
//      implementedInApiVersion,
//      nameOf(validateDynamicResourceDoc),
//      "POST",
//      "/management/dynamic-resource-docs/validate",
//      "Validate Dynamic Resource Doc",
//      s"""Dry-run validation of a Dynamic Resource Doc. Send the same payload you would send to `Create Dynamic Resource Doc` and this endpoint will:
//         |
//         |- Parse `method_body` (URL-decoded) as Scala code and run the ToolBox compiler against it, wrapped in the same template used at runtime (request/response case classes generated from `example_request_body` / `success_response_body`).
//         |- Run the OBP compilation-dependency guard (when the OBP prop `dynamic_code_compile_validate_enable` is set to `true`).
//         |
//         |Always returns HTTP 200. Inspect the `valid` field in the response:
//         |
//         |* `true`  — the Scala compiles and all referenced OBP methods are on the allowlist.
//         |* `false` — the response includes `error` (raw compiler / guard message), `message` (OBP error constant) and `details.error_type` — one of:
//         |  * `CompilationError` — `method_body` failed to compile.
//         |  * `DependencyError` — compiled, but references OBP types/methods that the admin has not allowed in `dynamic_code_compile_validate_dependencies`.
//         |  * `UnknownError` — any other unexpected exception.
//         |
//         |Nothing is persisted and no endpoint is served as a result of calling this.
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      jsonDynamicResourceDoc.copy(dynamicResourceDocId = None),
//      ValidateDynamicResourceDocSuccessJsonV600(
//        valid = true,
//        message = "Dynamic Resource Doc method body is valid Scala and uses allowed dependencies."
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagDynamicResourceDoc),
//      Some(List(canCreateDynamicResourceDoc))
//    )
//
//    lazy val validateDynamicResourceDoc: OBPEndpoint = {
//      case "management" :: "dynamic-resource-docs" :: "validate" :: Nil JsonPost json -> _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canCreateDynamicResourceDoc, callContext)
//            body <- NewStyle.function.tryons(
//              s"$InvalidJsonFormat The Json body should be the $JsonDynamicResourceDoc",
//              400,
//              callContext
//            ) {
//              json.extract[JsonDynamicResourceDoc]
//            }
//            _ <- Helper.booleanToFuture(
//              failMsg = s"""$InvalidJsonFormat The request_verb must be one of ["POST", "PUT", "GET", "DELETE"]""",
//              cc = callContext
//            ) {
//              Set("POST", "PUT", "GET", "DELETE").contains(body.requestVerb)
//            }
//            _ <- Helper.booleanToFuture(
//              failMsg = s"""$InvalidJsonFormat When request_verb is "GET" or "DELETE", the example_request_body must be a blank String "" or just totally omit the field""",
//              cc = callContext
//            ) {
//              (body.requestVerb, body.exampleRequestBody) match {
//                case ("GET" | "DELETE", Some(JString(s))) => StringUtils.isBlank(s)
//                case ("GET" | "DELETE", Some(rb)) => rb == JNothing
//                case _ => true
//              }
//            }
//            result = try {
//              CompiledObjects(body.exampleRequestBody, body.successResponseBody, body.methodBody)
//                .validateDependency()
//              ValidateDynamicResourceDocSuccessJsonV600(
//                valid = true,
//                message = "Dynamic Resource Doc method body is valid Scala and uses allowed dependencies."
//              )
//            } catch {
//              case e: JsonResponseException =>
//                // validateDependency throws JsonResponseException (OBP-40046) when the compiled
//                // code references types/methods outside the compile-time allowlist. The useful
//                // error text is in the response body, not getMessage.
//                val errorText = e.jsonResponse match {
//                  case JsonResponseExtractor(msg, _) => msg
//                  case _ => ""
//                }
//                ValidateDynamicResourceDocFailureJsonV600(
//                  valid = false,
//                  error = errorText,
//                  message = DynamicResourceDocMethodDependency,
//                  details = ValidateDynamicResourceDocErrorDetailsJsonV600(error_type = "DependencyError")
//                )
//              case e: Exception =>
//                ValidateDynamicResourceDocFailureJsonV600(
//                  valid = false,
//                  error = Option(e.getMessage).getOrElse(""),
//                  message = DynamicCodeCompileFail,
//                  details = ValidateDynamicResourceDocErrorDetailsJsonV600(error_type = "CompilationError")
//                )
//            }
//          } yield {
//            (result, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      executeAbacRule,
//      implementedInApiVersion,
//      nameOf(executeAbacRule),
//      "POST",
//      "/management/abac-rules/ABAC_RULE_ID/execute",
//      "Execute ABAC Rule",
//      s"""Execute an ABAC rule to test access control.
//         |
//         |This endpoint allows you to test an ABAC rule with specific context (authenticated user, bank, account, transaction, customer, etc.).
//         |
//         |**Documentation:**
//         |- ${Glossary.getGlossaryItemLink("ABAC_Simple_Guide")} - Getting started with ABAC rules
//         |- ${Glossary.getGlossaryItemLink("ABAC_Parameters_Summary")} - Complete list of all 18 parameters
//         |- ${Glossary.getGlossaryItemLink("ABAC_Object_Properties_Reference")} - Detailed property reference
//         |- ${Glossary.getGlossaryItemLink("ABAC_Testing_Examples")} - Testing examples and patterns
//         |
//         |You can provide optional IDs in the request body to test the rule with specific context.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      ExecuteAbacRuleJsonV600(
//        authenticated_user_id = Some("c7b6cb47-cb96-4441-8801-35b57456753a"),
//        on_behalf_of_user_id = Some("a3b5c123-1234-5678-9012-fedcba987654"),
//        user_id = Some("c7b6cb47-cb96-4441-8801-35b57456753a"),
//        bank_id = Some("gh.29.uk"),
//        account_id = Some("8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"),
//        view_id = Some("owner"),
//        transaction_request_id = Some("123456"),
//        transaction_id = Some("abc123"),
//        customer_id = Some("customer-id-123")
//      ),
//      AbacRuleResultJsonV600(
//        result = true
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canExecuteAbacRule))
//    )
//
//    lazy val executeAbacRule: OBPEndpoint = {
//      case "management" :: "abac-rules" :: ruleId :: "execute" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canExecuteAbacRule, callContext)
//            execJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, callContext) {
//              json.extract[ExecuteAbacRuleJsonV600]
//            }
//            rule <- Future {
//              MappedAbacRuleProvider.getAbacRuleById(ruleId)
//            } map {
//              unboxFullOrFail(_, callContext, s"ABAC Rule not found with ID: $ruleId", 404)
//            }
//
//            // Execute the rule with IDs - object fetching happens internally
//            // authenticatedUserId: can be provided in request (for testing) or defaults to actual authenticated user
//            // onBehalfOfUserId: optional delegation - acting on behalf of another user
//            // userId: the target user being evaluated (defaults to authenticated user)
//            effectiveAuthenticatedUserId = execJson.authenticated_user_id.getOrElse(user.userId)
//
//            result <- AbacRuleEngine.executeRule(
//                ruleId = ruleId,
//                authenticatedUserId = effectiveAuthenticatedUserId,
//                onBehalfOfUserId = execJson.on_behalf_of_user_id,
//                userId = execJson.user_id,
//                callContext = callContext.getOrElse(cc),
//                bankId = execJson.bank_id,
//                accountId = execJson.account_id,
//                viewId = execJson.view_id,
//                transactionId = execJson.transaction_id,
//                transactionRequestId = execJson.transaction_request_id,
//                customerId = execJson.customer_id
//              ).map {
//                case Full(allowed) =>
//                  AbacRuleResultJsonV600(result = allowed)
//                case Failure(msg, _, _) =>
//                  AbacRuleResultJsonV600(result = false)
//                case Empty =>
//                  AbacRuleResultJsonV600(result = false)
//              }
//          } yield {
//            (result, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      executeAbacPolicy,
//      implementedInApiVersion,
//      nameOf(executeAbacPolicy),
//      "POST",
//      "/management/abac-policies/POLICY/execute",
//      "Execute ABAC Policy",
//      s"""Execute all ABAC rules in a policy to test access control.
//         |
//         |This endpoint executes all active rules that belong to the specified policy.
//         |The policy uses OR logic - access is granted if at least one rule passes.
//         |
//         |This allows you to test a complete policy with specific context (authenticated user, bank, account, transaction, customer, etc.).
//         |
//         |**Documentation:**
//         |- ${Glossary.getGlossaryItemLink("ABAC_Simple_Guide")} - Getting started with ABAC rules
//         |- ${Glossary.getGlossaryItemLink("ABAC_Parameters_Summary")} - Complete list of all 18 parameters
//         |- ${Glossary.getGlossaryItemLink("ABAC_Object_Properties_Reference")} - Detailed property reference
//         |- ${Glossary.getGlossaryItemLink("ABAC_Testing_Examples")} - Testing examples and patterns
//         |
//         |You can provide optional IDs in the request body to test the policy with specific context.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      ExecuteAbacRuleJsonV600(
//        authenticated_user_id = Some("c7b6cb47-cb96-4441-8801-35b57456753a"),
//        on_behalf_of_user_id = Some("a3b5c123-1234-5678-9012-fedcba987654"),
//        user_id = Some("c7b6cb47-cb96-4441-8801-35b57456753a"),
//        bank_id = Some("gh.29.uk"),
//        account_id = Some("8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"),
//        view_id = Some("owner"),
//        transaction_request_id = Some("123456"),
//        transaction_id = Some("abc123"),
//        customer_id = Some("customer-id-123")
//      ),
//      AbacRuleResultJsonV600(
//        result = true
//      ),
//      List(
//        AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagABAC),
//      Some(List(canExecuteAbacRule))
//    )
//
//    lazy val executeAbacPolicy: OBPEndpoint = {
//      case "management" :: "abac-policies" :: policy :: "execute" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(user), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", user.userId, canExecuteAbacRule, callContext)
//            execJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, callContext) {
//              json.extract[ExecuteAbacRuleJsonV600]
//            }
//
//            // Verify the policy exists
//            _ <- Future {
//              if (Constant.ABAC_POLICIES.contains(policy)) {
//                Full(true)
//              } else {
//                Failure(s"Policy not found: $policy. Available policies: ${Constant.ABAC_POLICIES.mkString(", ")}")
//              }
//            } map {
//              unboxFullOrFail(_, callContext, s"Invalid ABAC Policy: $policy", 404)
//            }
//
//            // Execute the policy with IDs - object fetching happens internally
//            // authenticatedUserId: can be provided in request (for testing) or defaults to actual authenticated user
//            // onBehalfOfUserId: optional delegation - acting on behalf of another user
//            // userId: the target user being evaluated (defaults to authenticated user)
//            effectiveAuthenticatedUserId = execJson.authenticated_user_id.getOrElse(user.userId)
//
//            result <- AbacRuleEngine.executeRulesByPolicy(
//                policy = policy,
//                authenticatedUserId = effectiveAuthenticatedUserId,
//                onBehalfOfUserId = execJson.on_behalf_of_user_id,
//                userId = execJson.user_id,
//                callContext = callContext.getOrElse(cc),
//                bankId = execJson.bank_id,
//                accountId = execJson.account_id,
//                viewId = execJson.view_id,
//                transactionId = execJson.transaction_id,
//                transactionRequestId = execJson.transaction_request_id,
//                customerId = execJson.customer_id
//              ).map {
//                case Full(allowed) =>
//                  AbacRuleResultJsonV600(result = allowed)
//                case Failure(msg, _, _) =>
//                  AbacRuleResultJsonV600(result = false)
//                case Empty =>
//                  AbacRuleResultJsonV600(result = false)
//              }
//          } yield {
//            (result, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // ============================================================================================================
//    // USER ATTRIBUTES v6.0.0 - Consistent with other entity attributes
//    // ============================================================================================================
//    // "user attributes" = IsPersonal=false (requires roles) - consistent with other entity attributes
//    // "personal user attributes" = IsPersonal=true (no roles, user manages their own)
//    // ============================================================================================================
//
//    staticResourceDocs += ResourceDoc(
//      createUserAttribute,
//      implementedInApiVersion,
//      nameOf(createUserAttribute),
//      "POST",
//      "/users/USER_ID/attributes",
//      "Create User Attribute",
//      s"""Create a User Attribute for the user specified by USER_ID.
//         |
//         |User Attributes are non-personal attributes (IsPersonal=false) that can be used in ABAC rules.
//         |They require a role to set, similar to Customer Attributes, Account Attributes, etc.
//         |
//         |For personal attributes that users manage themselves, see the /my/personal-data-fields endpoints.
//         |
//         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or "DATE_WITH_DAY"
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      code.api.v5_1_0.UserAttributeJsonV510(
//        name = "account_type",
//        `type` = "STRING",
//        value = "premium"
//      ),
//      userAttributeResponseJsonV510,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UserNotFoundByUserId,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List(canCreateUserAttribute))
//    )
//
//    lazy val createUserAttribute: OBPEndpoint = {
//      case "users" :: userId :: "attributes" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateUserAttribute, callContext)
//            (user, callContext) <- NewStyle.function.getUserByUserId(userId, callContext)
//            failMsg = s"$InvalidJsonFormat The Json body should be the UserAttributeJsonV510"
//            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              json.extract[code.api.v5_1_0.UserAttributeJsonV510]
//            }
//            failMsg = s"$InvalidJsonFormat The `type` field can only accept: ${UserAttributeType.DOUBLE}, ${UserAttributeType.STRING}, ${UserAttributeType.INTEGER}, ${UserAttributeType.DATE_WITH_DAY}"
//            userAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              UserAttributeType.withName(postedData.`type`)
//            }
//            (userAttribute, callContext) <- NewStyle.function.createOrUpdateUserAttribute(
//              user.userId,
//              None,
//              postedData.name,
//              userAttributeType,
//              postedData.value,
//              false, // IsPersonal = false for user attributes
//              callContext
//            )
//          } yield {
//            (JSONFactory510.createUserAttributeJson(userAttribute), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getUserAttributes,
//      implementedInApiVersion,
//      nameOf(getUserAttributes),
//      "GET",
//      "/users/USER_ID/attributes",
//      "Get User Attributes",
//      s"""Get User Attributes for the user specified by USER_ID.
//         |
//         |Returns non-personal user attributes (IsPersonal=false) that can be used in ABAC rules.
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      EmptyBody,
//      code.api.v5_1_0.UserAttributesResponseJsonV510(
//        user_attributes = List(userAttributeResponseJsonV510)
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UserNotFoundByUserId,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List(canGetUserAttributes))
//    )
//
//    lazy val getUserAttributes: OBPEndpoint = {
//      case "users" :: userId :: "attributes" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetUserAttributes, callContext)
//            (user, callContext) <- NewStyle.function.getUserByUserId(userId, callContext)
//            (attributes, callContext) <- NewStyle.function.getNonPersonalUserAttributes(user.userId, callContext)
//          } yield {
//            (code.api.v5_1_0.UserAttributesResponseJsonV510(attributes.map(JSONFactory510.createUserAttributeJson)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getUserAttributeById,
//      implementedInApiVersion,
//      nameOf(getUserAttributeById),
//      "GET",
//      "/users/USER_ID/attributes/USER_ATTRIBUTE_ID",
//      "Get User Attribute By Id",
//      s"""Get a User Attribute by USER_ATTRIBUTE_ID for the user specified by USER_ID.
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      EmptyBody,
//      userAttributeResponseJsonV510,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UserNotFoundByUserId,
//        UserAttributeNotFound,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List(canGetUserAttributes))
//    )
//
//    lazy val getUserAttributeById: OBPEndpoint = {
//      case "users" :: userId :: "attributes" :: userAttributeId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetUserAttributes, callContext)
//            (user, callContext) <- NewStyle.function.getUserByUserId(userId, callContext)
//            (attributes, callContext) <- NewStyle.function.getNonPersonalUserAttributes(user.userId, callContext)
//            attribute <- Future {
//              attributes.find(_.userAttributeId == userAttributeId)
//            } map {
//              unboxFullOrFail(_, callContext, UserAttributeNotFound, 404)
//            }
//          } yield {
//            (JSONFactory510.createUserAttributeJson(attribute), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateUserAttribute,
//      implementedInApiVersion,
//      nameOf(updateUserAttribute),
//      "PUT",
//      "/users/USER_ID/attributes/USER_ATTRIBUTE_ID",
//      "Update User Attribute",
//      s"""Update a User Attribute by USER_ATTRIBUTE_ID for the user specified by USER_ID.
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      code.api.v5_1_0.UserAttributeJsonV510(
//        name = "account_type",
//        `type` = "STRING",
//        value = "enterprise"
//      ),
//      userAttributeResponseJsonV510,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UserNotFoundByUserId,
//        UserAttributeNotFound,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List(canUpdateUserAttribute))
//    )
//
//    lazy val updateUserAttribute: OBPEndpoint = {
//      case "users" :: userId :: "attributes" :: userAttributeId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canUpdateUserAttribute, callContext)
//            (user, callContext) <- NewStyle.function.getUserByUserId(userId, callContext)
//            failMsg = s"$InvalidJsonFormat The Json body should be the UserAttributeJsonV510"
//            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              json.extract[code.api.v5_1_0.UserAttributeJsonV510]
//            }
//            failMsg = s"$InvalidJsonFormat The `type` field can only accept: ${UserAttributeType.DOUBLE}, ${UserAttributeType.STRING}, ${UserAttributeType.INTEGER}, ${UserAttributeType.DATE_WITH_DAY}"
//            userAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              UserAttributeType.withName(postedData.`type`)
//            }
//            (attributes, callContext) <- NewStyle.function.getNonPersonalUserAttributes(user.userId, callContext)
//            _ <- Future {
//              attributes.find(_.userAttributeId == userAttributeId)
//            } map {
//              unboxFullOrFail(_, callContext, UserAttributeNotFound, 404)
//            }
//            (userAttribute, callContext) <- NewStyle.function.createOrUpdateUserAttribute(
//              user.userId,
//              Some(userAttributeId),
//              postedData.name,
//              userAttributeType,
//              postedData.value,
//              false, // IsPersonal = false for user attributes
//              callContext
//            )
//          } yield {
//            (JSONFactory510.createUserAttributeJson(userAttribute), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteUserAttribute,
//      implementedInApiVersion,
//      nameOf(deleteUserAttribute),
//      "DELETE",
//      "/users/USER_ID/attributes/USER_ATTRIBUTE_ID",
//      "Delete User Attribute",
//      s"""Delete a User Attribute by USER_ATTRIBUTE_ID for the user specified by USER_ID.
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UserNotFoundByUserId,
//        UserAttributeNotFound,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List(canDeleteUserAttribute))
//    )
//
//    lazy val deleteUserAttribute: OBPEndpoint = {
//      case "users" :: userId :: "attributes" :: userAttributeId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteUserAttribute, callContext)
//            (user, callContext) <- NewStyle.function.getUserByUserId(userId, callContext)
//            (attributes, callContext) <- NewStyle.function.getNonPersonalUserAttributes(user.userId, callContext)
//            _ <- Future {
//              attributes.find(_.userAttributeId == userAttributeId)
//            } map {
//              unboxFullOrFail(_, callContext, UserAttributeNotFound, 404)
//            }
//            (deleted, callContext) <- Connector.connector.vend.deleteUserAttribute(
//              userAttributeId,
//              callContext
//            ) map {
//              i => (connectorEmptyResponse(i._1, callContext), i._2)
//            }
//          } yield {
//            (Full(deleted), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // ============================================================================================================
//    // PERSONAL DATA FIELDS - User manages their own personal data fields
//    // ============================================================================================================
//
//    staticResourceDocs += ResourceDoc(
//      createPersonalDataField,
//      implementedInApiVersion,
//      nameOf(createPersonalDataField),
//      "POST",
//      "/my/personal-data-fields",
//      "Create Personal Data Field",
//      s"""Create a Personal Data Field for the currently authenticated user.
//         |
//         |Personal Data Fields (IsPersonal=true) are managed by the user themselves and do not require special roles.
//         |This data is not available in ABAC rules for privacy reasons.
//         |
//         |For non-personal attributes that can be used in ABAC rules, see the /users/USER_ID/attributes endpoints.
//         |
//         |The type field must be one of "STRING", "INTEGER", "DOUBLE" or "DATE_WITH_DAY"
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      code.api.v5_1_0.UserAttributeJsonV510(
//        name = "favorite_color",
//        `type` = "STRING",
//        value = "blue"
//      ),
//      userAttributeResponseJsonV510,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List())
//    )
//
//    lazy val createPersonalDataField: OBPEndpoint = {
//      case "my" :: "personal-data-fields" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            failMsg = s"$InvalidJsonFormat The Json body should be the UserAttributeJsonV510"
//            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              json.extract[code.api.v5_1_0.UserAttributeJsonV510]
//            }
//            failMsg = s"$InvalidJsonFormat The `type` field can only accept: ${UserAttributeType.DOUBLE}, ${UserAttributeType.STRING}, ${UserAttributeType.INTEGER}, ${UserAttributeType.DATE_WITH_DAY}"
//            userAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              UserAttributeType.withName(postedData.`type`)
//            }
//            (userAttribute, callContext) <- NewStyle.function.createOrUpdateUserAttribute(
//              u.userId,
//              None,
//              postedData.name,
//              userAttributeType,
//              postedData.value,
//              true, // IsPersonal = true for personal user attributes
//              callContext
//            )
//          } yield {
//            (JSONFactory510.createUserAttributeJson(userAttribute), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getPersonalDataFields,
//      implementedInApiVersion,
//      nameOf(getPersonalDataFields),
//      "GET",
//      "/my/personal-data-fields",
//      "Get Personal Data Fields",
//      s"""Get Personal Data Fields for the currently authenticated user.
//         |
//         |Returns Personal Data Fields (IsPersonal=true) that are managed by the user.
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      EmptyBody,
//      code.api.v5_1_0.UserAttributesResponseJsonV510(
//        user_attributes = List(userAttributeResponseJsonV510)
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List())
//    )
//
//    lazy val getPersonalDataFields: OBPEndpoint = {
//      case "my" :: "personal-data-fields" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            (attributes, callContext) <- NewStyle.function.getPersonalUserAttributes(u.userId, callContext)
//          } yield {
//            (code.api.v5_1_0.UserAttributesResponseJsonV510(attributes.map(JSONFactory510.createUserAttributeJson)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getPersonalDataFieldById,
//      implementedInApiVersion,
//      nameOf(getPersonalDataFieldById),
//      "GET",
//      "/my/personal-data-fields/USER_ATTRIBUTE_ID",
//      "Get Personal Data Field By Id",
//      s"""Get a Personal Data Field by USER_ATTRIBUTE_ID for the currently authenticated user.
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      EmptyBody,
//      userAttributeResponseJsonV510,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserAttributeNotFound,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List())
//    )
//
//    lazy val getPersonalDataFieldById: OBPEndpoint = {
//      case "my" :: "personal-data-fields" :: userAttributeId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            (attributes, callContext) <- NewStyle.function.getPersonalUserAttributes(u.userId, callContext)
//            attribute <- Future {
//              attributes.find(_.userAttributeId == userAttributeId)
//            } map {
//              unboxFullOrFail(_, callContext, UserAttributeNotFound, 404)
//            }
//          } yield {
//            (JSONFactory510.createUserAttributeJson(attribute), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updatePersonalDataField,
//      implementedInApiVersion,
//      nameOf(updatePersonalDataField),
//      "PUT",
//      "/my/personal-data-fields/USER_ATTRIBUTE_ID",
//      "Update Personal Data Field",
//      s"""Update a Personal Data Field by USER_ATTRIBUTE_ID for the currently authenticated user.
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      code.api.v5_1_0.UserAttributeJsonV510(
//        name = "favorite_color",
//        `type` = "STRING",
//        value = "green"
//      ),
//      userAttributeResponseJsonV510,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserAttributeNotFound,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List())
//    )
//
//    lazy val updatePersonalDataField: OBPEndpoint = {
//      case "my" :: "personal-data-fields" :: userAttributeId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            failMsg = s"$InvalidJsonFormat The Json body should be the UserAttributeJsonV510"
//            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              json.extract[code.api.v5_1_0.UserAttributeJsonV510]
//            }
//            failMsg = s"$InvalidJsonFormat The `type` field can only accept: ${UserAttributeType.DOUBLE}, ${UserAttributeType.STRING}, ${UserAttributeType.INTEGER}, ${UserAttributeType.DATE_WITH_DAY}"
//            userAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
//              UserAttributeType.withName(postedData.`type`)
//            }
//            (attributes, callContext) <- NewStyle.function.getPersonalUserAttributes(u.userId, callContext)
//            _ <- Future {
//              attributes.find(_.userAttributeId == userAttributeId)
//            } map {
//              unboxFullOrFail(_, callContext, UserAttributeNotFound, 404)
//            }
//            (userAttribute, callContext) <- NewStyle.function.createOrUpdateUserAttribute(
//              u.userId,
//              Some(userAttributeId),
//              postedData.name,
//              userAttributeType,
//              postedData.value,
//              true, // IsPersonal = true for personal user attributes
//              callContext
//            )
//          } yield {
//            (JSONFactory510.createUserAttributeJson(userAttribute), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deletePersonalDataField,
//      implementedInApiVersion,
//      nameOf(deletePersonalDataField),
//      "DELETE",
//      "/my/personal-data-fields/USER_ATTRIBUTE_ID",
//      "Delete Personal Data Field",
//      s"""Delete a Personal Data Field by USER_ATTRIBUTE_ID for the currently authenticated user.
//         |
//         |${userAuthenticationMessage(true)}
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserAttributeNotFound,
//        UnknownError
//      ),
//      List(apiTagUser, apiTagUserAttribute, apiTagAttribute),
//      Some(List())
//    )
//
//    lazy val deletePersonalDataField: OBPEndpoint = {
//      case "my" :: "personal-data-fields" :: userAttributeId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            (attributes, callContext) <- NewStyle.function.getPersonalUserAttributes(u.userId, callContext)
//            _ <- Future {
//              attributes.find(_.userAttributeId == userAttributeId)
//            } map {
//              unboxFullOrFail(_, callContext, UserAttributeNotFound, 404)
//            }
//            (deleted, callContext) <- Connector.connector.vend.deleteUserAttribute(
//              userAttributeId,
//              callContext
//            ) map {
//              i => (connectorEmptyResponse(i._1, callContext), i._2)
//            }
//          } yield {
//            (Full(deleted), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getMessageDocsJsonSchema,
//      implementedInApiVersion,
//      nameOf(getMessageDocsJsonSchema),
//      "GET",
//      "/message-docs/CONNECTOR/json-schema",
//      "Get Message Docs as JSON Schema",
//      """Returns message documentation as JSON Schema format for code generation in any language.
//        |
//        |This endpoint provides machine-readable schemas instead of just examples, making it ideal for:
//        |- AI-powered code generation
//        |- Automatic adapter creation in multiple languages
//        |- Type-safe client generation with tools like quicktype
//        |
//        |**Supported Connectors:**
//        |- rabbitmq_vOct2024 - RabbitMQ connector message schemas
//        |- rest_vMar2019 - REST connector message schemas
//        |- akka_vDec2018 - Akka connector message schemas
//        |- kafka_vMay2019 - Kafka connector message schemas (if available)
//        |
//        |**Code Generation Examples:**
//        |
//        |Generate Scala code with Circe:
//        |```bash
//        |curl https://api.../message-docs/rabbitmq_vOct2024/json-schema > schemas.json
//        |quicktype -s schema schemas.json -o Messages.scala --framework circe
//        |```
//        |
//        |Generate Python code:
//        |```bash
//        |quicktype -s schema schemas.json -o messages.py --lang python
//        |```
//        |
//        |Generate TypeScript code:
//        |```bash
//        |quicktype -s schema schemas.json -o messages.ts --lang typescript
//        |```
//        |
//        |**Schema Structure:**
//        |Each message includes:
//        |- `process` - The connector method name (e.g., "obp.getAdapterInfo")
//        |- `description` - Human-readable description of what the message does
//        |- `outbound_schema` - JSON Schema for request messages (OBP-API -> Adapter)
//        |- `inbound_schema` - JSON Schema for response messages (Adapter -> OBP-API)
//        |
//        |All nested type definitions are included in the `definitions` section for reuse.
//        |
//        |**Authentication:**
//        |This endpoint is publicly accessible (no authentication required) to facilitate adapter development.
//        |
//        |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        InvalidConnector,
//        UnknownError
//      ),
//      List(apiTagMessageDoc, apiTagDocumentation, apiTagApi)
//    )
//
//    lazy val getMessageDocsJsonSchema: OBPEndpoint = {
//      case "message-docs" :: connector :: "json-schema" :: Nil JsonGet _ => {
//        cc => {
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- anonymousAccess(cc)
//            cacheKey = s"message-docs-json-schema-$connector"
//            cacheValueFromRedis = Caching.getStaticSwaggerDocCache(cacheKey)
//            jsonSchema <- if (cacheValueFromRedis.isDefined) {
//              NewStyle.function.tryons(s"$UnknownError Cannot parse cached JSON Schema.", 400, callContext) {
//                json.parse(cacheValueFromRedis.get).asInstanceOf[JObject]
//              }
//            } else {
//              NewStyle.function.tryons(s"$UnknownError Cannot generate JSON Schema.", 400, callContext) {
//                val connectorObjectBox = tryo{Connector.getConnectorInstance(connector)}
//                val connectorObject = unboxFullOrFail(
//                  connectorObjectBox,
//                  callContext,
//                  s"$InvalidConnector Current input is: $connector. Valid connectors include: rabbitmq_vOct2024, rest_vMar2019, akka_vDec2018"
//                )
//                val schema = JsonSchemaGenerator.messageDocsToJsonSchema(
//                  connectorObject.messageDocs.toList,
//                  connector
//                )
//                val schemaString = json.compactRender(schema)
//                Caching.setStaticSwaggerDocCache(cacheKey, schemaString)
//                schema
//              }
//            }
//          } yield {
//            (jsonSchema, HttpCode.`200`(callContext))
//          }
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getMyDynamicEntities,
//      implementedInApiVersion,
//      nameOf(getMyDynamicEntities),
//      "GET",
//      "/my/dynamic-entities",
//      "Get My Dynamic Entities",
//      s"""Get all Dynamic Entity definitions I created.
//         |
//         |This v6.0.0 endpoint returns a cleaner response format with:
//         |* snake_case field names (dynamic_entity_id, user_id, bank_id, has_personal_entity)
//         |* An explicit entity_name field instead of using the entity name as a dynamic JSON key
//         |* The entity schema in a separate definition object
//         |
//         |For more information see ${Glossary.getGlossaryItemLink(
//          "My-Dynamic-Entities"
//        )}""",
//      EmptyBody,
//      MyDynamicEntitiesJsonV600(
//        dynamic_entities = List(
//          DynamicEntityDefinitionJsonV600(
//            dynamic_entity_id = "abc-123-def",
//            entity_name = "customer_preferences",
//            user_id = "user-456",
//            bank_id = None,
//            has_personal_entity = true,
//            schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences", "required": ["theme"], "properties": {"theme": {"type": "string"}, "language": {"type": "string"}}}""").asInstanceOf[org.json4s.JsonAST.JObject],
//            _links = Some(DynamicEntityLinksJsonV600(
//              related = List(
//                RelatedLinkJsonV600("personal-list", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences", "GET"),
//                RelatedLinkJsonV600("personal-create", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences", "POST"),
//                RelatedLinkJsonV600("personal-read", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences/CUSTOMER_PREFERENCES_ID", "GET"),
//                RelatedLinkJsonV600("personal-update", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences/CUSTOMER_PREFERENCES_ID", "PUT"),
//                RelatedLinkJsonV600("personal-delete", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences/CUSTOMER_PREFERENCES_ID", "DELETE")
//              )
//            ))
//          )
//        )
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi)
//    )
//
//    lazy val getMyDynamicEntities: OBPEndpoint = {
//      case "my" :: "dynamic-entities" :: Nil JsonGet req => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          dynamicEntities <- Future(
//            NewStyle.function.getDynamicEntitiesByUserId(cc.userId)
//          )
//        } yield {
//          val listCommons: List[DynamicEntityCommons] = dynamicEntities
//          (
//            JSONFactory600.createMyDynamicEntitiesJson(listCommons),
//            HttpCode.`200`(cc.callContext)
//          )
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAvailablePersonalDynamicEntities,
//      implementedInApiVersion,
//      nameOf(getAvailablePersonalDynamicEntities),
//      "GET",
//      "/personal-dynamic-entities/available",
//      "Get Available Personal Dynamic Entities",
//      s"""Get all Dynamic Entities that support personal data storage (hasPersonalEntity == true).
//         |
//         |This endpoint allows regular users (without admin roles) to discover which dynamic entities
//         |they can interact with for storing personal data via the /my/ENTITY_NAME endpoints.
//         |
//         |Authentication: User must be logged in (no special roles required).
//         |
//         |Use case: Portals and apps can show users what personal data types are available
//         |without needing admin access to view all dynamic entity definitions.
//         |
//         |For more information see ${Glossary.getGlossaryItemLink("My-Dynamic-Entities")}""",
//      EmptyBody,
//      MyDynamicEntitiesJsonV600(
//        dynamic_entities = List(
//          DynamicEntityDefinitionJsonV600(
//            dynamic_entity_id = "abc-123-def",
//            entity_name = "customer_preferences",
//            user_id = "user-456",
//            bank_id = None,
//            has_personal_entity = true,
//            schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "User preferences", "required": ["theme"], "properties": {"theme": {"type": "string"}, "language": {"type": "string"}}}""").asInstanceOf[org.json4s.JsonAST.JObject],
//            _links = Some(DynamicEntityLinksJsonV600(
//              related = List(
//                RelatedLinkJsonV600("personal-list", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences", "GET"),
//                RelatedLinkJsonV600("personal-create", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences", "POST"),
//                RelatedLinkJsonV600("personal-read", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences/CUSTOMER_PREFERENCES_ID", "GET"),
//                RelatedLinkJsonV600("personal-update", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences/CUSTOMER_PREFERENCES_ID", "PUT"),
//                RelatedLinkJsonV600("personal-delete", s"/obp/${ApiVersion.`dynamic-entity`}/my/customer_preferences/CUSTOMER_PREFERENCES_ID", "DELETE")
//              )
//            ))
//          )
//        )
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagDynamicEntity, apiTagPersonalDynamicEntity, apiTagApi)
//    )
//
//    lazy val getAvailablePersonalDynamicEntities: OBPEndpoint = {
//      case "personal-dynamic-entities" :: "available" :: Nil JsonGet req => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          // Get all dynamic entities (system and bank level)
//          allDynamicEntities <- Future(
//            NewStyle.function.getDynamicEntities(None, true)
//          )
//        } yield {
//          // Filter to only those with hasPersonalEntity == true
//          val personalEntities: List[DynamicEntityCommons] = allDynamicEntities.filter(_.hasPersonalEntity)
//          (
//            JSONFactory600.createMyDynamicEntitiesJson(personalEntities),
//            HttpCode.`200`(cc.callContext)
//          )
//        }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      verifyUserCredentials,
//      implementedInApiVersion,
//      nameOf(verifyUserCredentials),
//      "POST",
//      "/users/verify-credentials",
//      "Verify User Credentials",
//      s"""Verify a user's credentials (username, password, provider) and return user information if valid.
//         |
//         |This endpoint validates the provided credentials without creating a token or session.
//         |It can be used to verify user credentials in external systems.
//         |
//         |${applicationAccessMessage(true)}
//         |
//         |""",
//      PostVerifyUserCredentialsJsonV600(
//        username = "username",
//        password = "password",
//        provider = Constant.localIdentityProvider
//      ),
//      userJsonV200,
//      List(
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        InvalidLoginCredentials,
//        UsernameHasBeenLocked,
//        UnknownError
//      ),
//      List(apiTagUser),
//      Some(List(canVerifyUserCredentials)),
//      authMode = UserOrApplication
//    )
//
//    lazy val verifyUserCredentials: OBPEndpoint = {
//      case "users" :: "verify-credentials" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            callContext <- Future.successful(Some(cc))
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostVerifyUserCredentialsJsonV600", 400, callContext) {
//              json.extract[PostVerifyUserCredentialsJsonV600]
//            }
//            // Decode the provider in case it's URL-encoded (e.g., "http%3A%2F%2Fexample.com" -> "http://example.com")
//            decodedProvider = URLDecoder.decode(postedData.provider, StandardCharsets.UTF_8)
//            // Validate credentials using the existing AuthUser mechanism
//
//            resourceUserIdBox = code.model.dataAccess.AuthUser.getResourceUserId(
//              postedData.username, postedData.password, decodedProvider
//            )
//            // Check if account is locked
//            _ <- Helper.booleanToFuture(UsernameHasBeenLocked, 401, callContext) {
//              resourceUserIdBox != Full(code.model.dataAccess.AuthUser.usernameLockedStateCode)
//            }
//            // Check if email is validated
//            _ <- Helper.booleanToFuture(UserEmailNotValidated, 401, callContext) {
//              resourceUserIdBox != Full(code.model.dataAccess.AuthUser.userEmailNotValidatedStateCode)
//            }
//            // Check if credentials are valid
//            resourceUserId <- Future {
//              resourceUserIdBox
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$InvalidLoginCredentials Failed to authenticate user credentials.", 401)
//            }
//            // Get the user object
//            user <- Future {
//              Users.users.vend.getUserByResourceUserId(resourceUserId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$InvalidLoginCredentials User account not found in system.", 401)
//            }
//            // Verify provider matches if specified and not empty
//            _ <- Helper.booleanToFuture(s"$InvalidLoginCredentials Authentication provider mismatch.", 401, callContext) {
//              decodedProvider.isEmpty || user.provider == decodedProvider
//            }
//          } yield {
//            (JSONFactory200.createUserJSON(user), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      verifyOidcClient,
//      implementedInApiVersion,
//      nameOf(verifyOidcClient),
//      "POST",
//      "/oidc/clients/verify",
//      "Verify OIDC Client",
//      s"""Verifies an OIDC/OAuth2 client's credentials.
//         |
//         |Returns `valid: true` if the client_id and client_secret match an active consumer.
//         |Also returns the consumer_id and redirect_uris for use by the OIDC provider.
//         |
//         |${userAuthenticationMessage(true)}
//         |""",
//      VerifyOidcClientRequestJsonV600(
//        client_id = "abc123def456",
//        client_secret = "supersecret123"
//      ),
//      VerifyOidcClientResponseJsonV600(
//        valid = true,
//        client_id = Some("abc123def456"),
//        consumer_id = Some("7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh"),
//        redirect_uris = Some(List("https://app.example.com/callback"))
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagOIDC, apiTagConsumer, apiTagOAuth),
//      Some(List(canVerifyOidcClient)),
//      authMode = UserOrApplication
//    )
//
//    lazy val verifyOidcClient: OBPEndpoint = {
//      case "oidc" :: "clients" :: "verify" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the VerifyOidcClientRequestJsonV600", 400, callContext) {
//              json.extract[VerifyOidcClientRequestJsonV600]
//            }
//            consumerBox <- Future {
//              Consumers.consumers.vend.getConsumerByConsumerKey(postedData.client_id)
//            }
//          } yield {
//            consumerBox match {
//              case Full(consumer) if consumer.isActive.get && consumer.secret.get == postedData.client_secret =>
//                val redirectUris = Option(consumer.redirectURL.get)
//                  .filter(_.nonEmpty)
//                  .map(_.split("[,\\s]+").map(_.trim).filter(_.nonEmpty).toList)
//                (VerifyOidcClientResponseJsonV600(
//                  valid = true,
//                  client_id = Some(postedData.client_id),
//                  consumer_id = Some(consumer.consumerId.get),
//                  redirect_uris = redirectUris
//                ), HttpCode.`200`(callContext))
//              case Full(consumer) if !consumer.isActive.get =>
//                logger.warn(s"verifyOidcClient: client_id ${postedData.client_id} exists but is not active (consumer_id: ${consumer.consumerId.get})")
//                (VerifyOidcClientResponseJsonV600(valid = false), HttpCode.`200`(callContext))
//              case _ =>
//                (VerifyOidcClientResponseJsonV600(valid = false), HttpCode.`200`(callContext))
//            }
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getOidcClient,
//      implementedInApiVersion,
//      nameOf(getOidcClient),
//      "GET",
//      "/oidc/clients/CLIENT_ID",
//      "Get OIDC Client",
//      s"""Gets an OIDC/OAuth2 client's metadata by client_id.
//         |
//         |Returns client information including name, consumer_id, redirect_uris, and enabled status.
//         |This endpoint does not verify the client secret - use POST /oidc/clients/verify for authentication.
//         |
//         |${userAuthenticationMessage(true)}
//         |""",
//      EmptyBody,
//      GetOidcClientResponseJsonV600(
//        client_id = "abc123def456",
//        client_name = "My Application",
//        consumer_id = "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
//        redirect_uris = List("https://app.example.com/callback"),
//        enabled = true
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagOIDC, apiTagConsumer, apiTagOAuth),
//      Some(List(canGetOidcClient)),
//      authMode = UserOrApplication
//    )
//
//    lazy val getOidcClient: OBPEndpoint = {
//      case "oidc" :: "clients" :: clientId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            consumerBox <- Future {
//              Consumers.consumers.vend.getConsumerByConsumerKey(clientId)
//            }
//            consumer <- NewStyle.function.tryons(s"OBP-OIDC-003: Client not found: $clientId", 404, callContext) {
//              consumerBox match {
//                case Full(c) => c
//                case _ => throw new RuntimeException("Client not found")
//              }
//            }
//          } yield {
//            val redirectUris = Option(consumer.redirectURL.get)
//              .filter(_.nonEmpty)
//              .map(_.split("[,\\s]+").map(_.trim).filter(_.nonEmpty).toList)
//              .getOrElse(List.empty)
//            (GetOidcClientResponseJsonV600(
//              client_id = clientId,
//              client_name = consumer.name.get,
//              consumer_id = consumer.consumerId.get,
//              redirect_uris = redirectUris,
//              enabled = consumer.isActive.get
//            ), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // Featured API Collections Management Endpoints
//
//    staticResourceDocs += ResourceDoc(
//      createFeaturedApiCollection,
//      implementedInApiVersion,
//      nameOf(createFeaturedApiCollection),
//      "POST",
//      "/management/api-collections/featured",
//      "Create Featured Api Collection",
//      s"""Add an API Collection to the featured list.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      postFeaturedApiCollectionJsonV600,
//      featuredApiCollectionJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ApiCollectionNotFound,
//        FeaturedApiCollectionAlreadyExists,
//        CreateFeaturedApiCollectionError,
//        UnknownError
//      ),
//      List(apiTagApiCollection, apiTagApi),
//      Some(List(canManageFeaturedApiCollections))
//    )
//
//    lazy val createFeaturedApiCollection: OBPEndpoint = {
//      case "management" :: "api-collections" :: "featured" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canManageFeaturedApiCollections, callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostFeaturedApiCollectionJsonV600", 400, callContext) {
//              json.extract[PostFeaturedApiCollectionJsonV600]
//            }
//            // Verify the API Collection exists and is sharable
//            (apiCollection, callContext) <- NewStyle.function.getApiCollectionById(postJson.api_collection_id, callContext)
//            _ <- Helper.booleanToFuture(s"$ApiCollectionNotFound The API Collection must be sharable to be featured.", cc=callContext) {
//              apiCollection.isSharable
//            }
//            // Check it's not already featured
//            _ <- NewStyle.function.checkFeaturedApiCollectionDoesNotExist(postJson.api_collection_id, callContext)
//            // Create the featured entry
//            (featuredApiCollection, callContext) <- NewStyle.function.createFeaturedApiCollection(
//              postJson.api_collection_id,
//              postJson.sort_order,
//              callContext
//            )
//          } yield {
//            (JSONFactory600.createFeaturedApiCollectionJsonV600(featuredApiCollection), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getFeaturedApiCollectionsAdmin,
//      implementedInApiVersion,
//      nameOf(getFeaturedApiCollectionsAdmin),
//      "GET",
//      "/management/api-collections/featured",
//      "Get Featured Api Collections (Admin)",
//      s"""Get all featured API collections with their sort order (admin view).
//         |
//         |This endpoint returns the featured collections stored in the database with their sort order.
//         |It is intended for administrators to manage the featured list.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      featuredApiCollectionsJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagApiCollection, apiTagApi),
//      Some(List(canManageFeaturedApiCollections))
//    )
//
//    lazy val getFeaturedApiCollectionsAdmin: OBPEndpoint = {
//      case "management" :: "api-collections" :: "featured" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canManageFeaturedApiCollections, callContext)
//            (featuredApiCollections, callContext) <- NewStyle.function.getAllFeaturedApiCollectionsAdmin(callContext)
//          } yield {
//            (JSONFactory600.createFeaturedApiCollectionsJsonV600(featuredApiCollections), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateFeaturedApiCollection,
//      implementedInApiVersion,
//      nameOf(updateFeaturedApiCollection),
//      "PUT",
//      "/management/api-collections/featured/API_COLLECTION_ID",
//      "Update Featured Api Collection",
//      s"""Update the sort order of a featured API collection.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      putFeaturedApiCollectionJsonV600,
//      featuredApiCollectionJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        FeaturedApiCollectionNotFound,
//        UpdateFeaturedApiCollectionError,
//        UnknownError
//      ),
//      List(apiTagApiCollection, apiTagApi),
//      Some(List(canManageFeaturedApiCollections))
//    )
//
//    lazy val updateFeaturedApiCollection: OBPEndpoint = {
//      case "management" :: "api-collections" :: "featured" :: apiCollectionId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canManageFeaturedApiCollections, callContext)
//            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutFeaturedApiCollectionJsonV600", 400, callContext) {
//              json.extract[PutFeaturedApiCollectionJsonV600]
//            }
//            (updatedFeaturedApiCollection, callContext) <- NewStyle.function.updateFeaturedApiCollection(
//              apiCollectionId,
//              putJson.sort_order,
//              callContext
//            )
//          } yield {
//            (JSONFactory600.createFeaturedApiCollectionJsonV600(updatedFeaturedApiCollection), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteFeaturedApiCollection,
//      implementedInApiVersion,
//      nameOf(deleteFeaturedApiCollection),
//      "DELETE",
//      "/management/api-collections/featured/API_COLLECTION_ID",
//      "Delete Featured Api Collection",
//      s"""Remove an API Collection from the featured list.
//         |
//         |${userAuthenticationMessage(true)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        FeaturedApiCollectionNotFound,
//        DeleteFeaturedApiCollectionError,
//        UnknownError
//      ),
//      List(apiTagApiCollection, apiTagApi),
//      Some(List(canManageFeaturedApiCollections))
//    )
//
//    lazy val deleteFeaturedApiCollection: OBPEndpoint = {
//      case "management" :: "api-collections" :: "featured" :: apiCollectionId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canManageFeaturedApiCollections, callContext)
//            (_, callContext) <- NewStyle.function.deleteFeaturedApiCollectionByApiCollectionId(apiCollectionId, callContext)
//          } yield {
//            (Full(true), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getPopularApis,
//      implementedInApiVersion,
//      nameOf(getPopularApis),
//      "GET",
//      "/api/popular-endpoints",
//      "Get Popular Endpoints",
//      s"""Returns the operation IDs of the 50 most popular endpoints based on usage metrics.
//         |
//         |This endpoint is public and does not require authentication.
//         |
//         |The response contains a simple list of operation_id strings, ordered by popularity (most called first).
//         |
//         |This includes endpoints from all API standards: OBP, Berlin Group, UK Open Banking, STET, Polish API, etc.
//         |
//         |Example operation_id formats:
//         |* OBP: OBPv4.0.0-getBanks
//         |* Berlin Group: BGv1.3-getAccountList
//         |* UK Open Banking: UKv3.1-getAccounts
//         |
//         |""".stripMargin,
//      EmptyBody,
//      PopularApisJsonV600(
//        operation_ids = List(
//          "OBPv4.0.0-getBanks",
//          "OBPv4.0.0-getBank",
//          "BGv1.3-getAccountList"
//        )
//      ),
//      List(
//        UnknownError
//      ),
//      List(apiTagMetric, apiTagApi)
//    )
//
//    lazy val getPopularApis: OBPEndpoint = {
//      case "api" :: "popular-endpoints" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- anonymousAccess(cc)
//            // Get top 50 APIs - use default date range (all time) with limit of 50
//            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
//            // Add limit=50 to the query params
//            limitParams = List(OBPLimit(50))
//            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, callContext)
//            queryParamsWithLimit = obpQueryParams ++ limitParams
//            topApis <- APIMetrics.apiMetrics.vend.getTopApisFuture(queryParamsWithLimit) map {
//              unboxFullOrFail(_, callContext, UnknownError)
//            }
//          } yield {
//            // Build lookup map from partialFunctionName -> operationId
//            // This handles OBP, Berlin Group, UK Open Banking, and other standards correctly
//            val allDocs = APIUtil.getAllResourceDocs
//            val lookupMap: Map[String, String] = allDocs.map { doc =>
//              doc.partialFunctionName -> doc.operationId
//            }.toMap
//
//            // Convert TopApi to operation_id, looking up correct format for each standard
//            val operationIds = topApis.flatMap { api =>
//              lookupMap.get(api.ImplementedByPartialFunction)
//                .orElse(
//                  scala.util.Try(Some(APIUtil.buildOperationId(ApiVersionUtils.valueOf(api.implementedInVersion), api.ImplementedByPartialFunction)))
//                    .getOrElse(None)
//                )
//            }
//            (PopularApisJsonV600(operationIds), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getConnectorCallCounts,
//      implementedInApiVersion,
//      nameOf(getConnectorCallCounts),
//      "GET",
//      "/management/connector/metrics/counts",
//      "Get Connector Call Counts",
//      s"""Returns per-hour Redis counters for connector outbound and inbound messages.
//         |
//         |This provides real-time visibility into which connector methods are being called
//         |and how many responses (success/failure) are being received.
//         |
//         |Counters automatically reset every hour (rolling window).
//         |The ttl_seconds field shows when the current hour window resets.
//         |
//         |Requires the prop: write_connector_metrics_redis=true
//         |
//         |Redis key format:
//         |
//         |- Outbound (before connector call): {instance}_{env}_connector_outbound_{version}_{connectorName}_{methodName}_PER_HOUR
//         |- Inbound success (after connector call): {instance}_{env}_connector_inbound_{version}_{connectorName}_{methodName}_success_PER_HOUR
//         |- Inbound failure (after connector call): {instance}_{env}_connector_inbound_{version}_{connectorName}_{methodName}_failure_PER_HOUR
//         |
//         |For example: obp_dev_connector_outbound_1_star_getBanks_PER_HOUR
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ConnectorCountsJsonV600(
//        enabled = true,
//        connector_counts = List(
//          ConnectorCountJsonV600(
//            connector_name = "mapped",
//            method_name = "getBank",
//            per_hour_outbound_count = 152,
//            per_hour_inbound_success_count = 150,
//            per_hour_inbound_failure_count = 2,
//            ttl_seconds = 2847
//          )
//        )
//      ),
//      List(
//        UnknownError
//      ),
//      List(apiTagMetric, apiTagApi),
//      Some(List(canReadMetrics))
//    )
//
//    lazy val getConnectorCallCounts: OBPEndpoint = {
//      case "management" :: "connector" :: "metrics" :: "counts" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canReadMetrics, callContext)
//          } yield {
//            val counts = ConnectorCountsRedis.getAllCounts()
//            val json = ConnectorCountsJsonV600(
//              enabled = ConnectorCountsRedis.isEnabled,
//              connector_counts = counts.map(c => ConnectorCountJsonV600(
//                connector_name = c.connector_name,
//                method_name = c.method_name,
//                per_hour_outbound_count = c.per_hour_outbound_count,
//                per_hour_inbound_success_count = c.per_hour_inbound_success_count,
//                per_hour_inbound_failure_count = c.per_hour_inbound_failure_count,
//                ttl_seconds = c.ttl_seconds
//              ))
//            )
//            (json, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // Api Product Endpoints (independent of CBS)
//
//    staticResourceDocs += ResourceDoc(
//      createApiProduct,
//      implementedInApiVersion,
//      nameOf(createApiProduct),
//      "POST",
//      "/banks/BANK_ID/api-products/API_PRODUCT_CODE",
//      "Create Api Product",
//      s"""Create an Api Product for the Bank.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      postPutApiProductJsonV600,
//      apiProductJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        CreateApiProductError,
//        UnknownError
//      ),
//      List(apiTagApi, apiTagApiProduct),
//      Some(List(canCreateApiProduct))
//    )
//
//    lazy val createApiProduct: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "api-products" :: apiProductCode :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canCreateApiProduct, callContext)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostPutApiProductJsonV600", 400, callContext) {
//              json.extract[PostPutApiProductJsonV600]
//            }
//            (apiProduct, callContext) <- NewStyle.function.createOrUpdateApiProduct(
//              bankId.value,
//              apiProductCode,
//              postJson.parent_api_product_code.getOrElse(""),
//              postJson.name,
//              postJson.category.getOrElse(""),
//              postJson.more_info_url.getOrElse(""),
//              postJson.terms_and_conditions_url.getOrElse(""),
//              postJson.description.getOrElse(""),
//              postJson.collection_id.getOrElse(""),
//              postJson.monthly_subscription_currency.getOrElse(""),
//              postJson.monthly_subscription_amount.getOrElse(""),
//              postJson.per_second_call_limit.getOrElse(-1L),
//              postJson.per_minute_call_limit.getOrElse(-1L),
//              postJson.per_hour_call_limit.getOrElse(-1L),
//              postJson.per_day_call_limit.getOrElse(-1L),
//              postJson.per_week_call_limit.getOrElse(-1L),
//              postJson.per_month_call_limit.getOrElse(-1L),
//              postJson.tags.getOrElse(Nil),
//              callContext
//            )
//          } yield {
//            (JSONFactory600.createApiProductJsonV600(apiProduct, None), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createOrUpdateApiProduct,
//      implementedInApiVersion,
//      nameOf(createOrUpdateApiProduct),
//      "PUT",
//      "/banks/BANK_ID/api-products/API_PRODUCT_CODE",
//      "Create or Update Api Product",
//      s"""Create or Update an Api Product for the Bank.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      postPutApiProductJsonV600,
//      apiProductJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        CreateApiProductError,
//        UnknownError
//      ),
//      List(apiTagApi, apiTagApiProduct),
//      Some(List(canUpdateApiProduct))
//    )
//
//    lazy val createOrUpdateApiProduct: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "api-products" :: apiProductCode :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateApiProduct, callContext)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostPutApiProductJsonV600", 400, callContext) {
//              json.extract[PostPutApiProductJsonV600]
//            }
//            (apiProduct, callContext) <- NewStyle.function.createOrUpdateApiProduct(
//              bankId.value,
//              apiProductCode,
//              postJson.parent_api_product_code.getOrElse(""),
//              postJson.name,
//              postJson.category.getOrElse(""),
//              postJson.more_info_url.getOrElse(""),
//              postJson.terms_and_conditions_url.getOrElse(""),
//              postJson.description.getOrElse(""),
//              postJson.collection_id.getOrElse(""),
//              postJson.monthly_subscription_currency.getOrElse(""),
//              postJson.monthly_subscription_amount.getOrElse(""),
//              postJson.per_second_call_limit.getOrElse(-1L),
//              postJson.per_minute_call_limit.getOrElse(-1L),
//              postJson.per_hour_call_limit.getOrElse(-1L),
//              postJson.per_day_call_limit.getOrElse(-1L),
//              postJson.per_week_call_limit.getOrElse(-1L),
//              postJson.per_month_call_limit.getOrElse(-1L),
//              postJson.tags.getOrElse(Nil),
//              callContext
//            )
//          } yield {
//            (JSONFactory600.createApiProductJsonV600(apiProduct, None), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getApiProduct,
//      implementedInApiVersion,
//      nameOf(getApiProduct),
//      "GET",
//      "/banks/BANK_ID/api-products/API_PRODUCT_CODE",
//      "Get Api Product",
//      s"""Get an Api Product by BANK_ID and API_PRODUCT_CODE.
//         |
//         |Returns the Api Product with its attributes.
//         |
//         |${userAuthenticationMessage(!getApiProductsIsPublic)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      apiProductJsonV600,
//      if (getApiProductsIsPublic) List(ApiProductNotFound, UnknownError) else List(UserHasMissingRoles, ApiProductNotFound, UnknownError),
//      List(apiTagApi, apiTagApiProduct),
//      if (getApiProductsIsPublic) None else Some(List(canGetApiProduct))
//    )
//
//    lazy val getApiProduct: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "api-products" :: apiProductCode :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- getApiProductsIsPublic match {
//              case false => authenticatedAccess(cc)
//              case true  => anonymousAccess(cc)
//            }
//            _ <- if (!getApiProductsIsPublic) {
//              for {
//                (Full(u), callContext) <- authenticatedAccess(cc)
//                _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetApiProduct, callContext)
//              } yield callContext
//            } else {
//              Future.successful(callContext)
//            }
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            (apiProduct, callContext) <- NewStyle.function.getApiProductByBankIdAndCode(bankId.value, apiProductCode, callContext)
//            (attributes, callContext) <- NewStyle.function.getApiProductAttributesByBankIdAndCode(bankId.value, apiProductCode, callContext)
//          } yield {
//            (JSONFactory600.createApiProductJsonV600(apiProduct, Some(attributes)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getApiProducts,
//      implementedInApiVersion,
//      nameOf(getApiProducts),
//      "GET",
//      "/banks/BANK_ID/api-products",
//      "Get Api Products",
//      s"""Get Api Products for the Bank.
//         |
//         |Optional query parameter: `tag` — filter to products that have the given tag (e.g. `?tag=featured`). Tag matching is case-insensitive.
//         |
//         |${userAuthenticationMessage(!getApiProductsIsPublic)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      apiProductsJsonV600,
//      if (getApiProductsIsPublic) List(UnknownError) else List(UserHasMissingRoles, UnknownError),
//      List(apiTagApi, apiTagApiProduct),
//      if (getApiProductsIsPublic) None else Some(List(canGetApiProduct))
//    )
//
//    lazy val getApiProducts: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "api-products" :: Nil JsonGet req => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- getApiProductsIsPublic match {
//              case false => authenticatedAccess(cc)
//              case true  => anonymousAccess(cc)
//            }
//            _ <- if (!getApiProductsIsPublic) {
//              for {
//                (Full(u), callContext) <- authenticatedAccess(cc)
//                _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetApiProduct, callContext)
//              } yield callContext
//            } else {
//              Future.successful(callContext)
//            }
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            tagFilter = req.params.get("tag").flatMap(_.headOption).map(_.trim).filter(_.nonEmpty)
//            (apiProducts, callContext) <- NewStyle.function.getApiProductsByBankId(bankId.value, tagFilter, callContext)
//          } yield {
//            (JSONFactory600.createApiProductsJsonV600(apiProducts), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAllApiProductsV600,
//      implementedInApiVersion,
//      nameOf(getAllApiProductsV600),
//      "GET",
//      "/api-products",
//      "Get Api Products At All Banks",
//      s"""Returns the Api Products across every bank, merged into a single list. Each product carries its `bank_id`.
//         |
//         |Optional query parameter `tag` — filter to products that have the given tag (e.g. `?tag=featured`). Tag matching is case-insensitive.
//         |
//         |${userAuthenticationMessage(!getApiProductsIsPublic)}""".stripMargin,
//      EmptyBody,
//      apiProductsJsonV600,
//      List(UnknownError),
//      List(apiTagApi, apiTagApiProduct)
//    )
//
//    lazy val getAllApiProductsV600: OBPEndpoint = {
//      case "api-products" :: Nil JsonGet req => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- getApiProductsIsPublic match {
//              case false => authenticatedAccess(cc)
//              case true  => anonymousAccess(cc)
//            }
//            tagFilter = req.params.get("tag").flatMap(_.headOption).map(_.trim).filter(_.nonEmpty)
//            resultJson <- {
//              implicit val formats: org.json4s.Formats = org.json4s.DefaultFormats
//              val cacheKey = s"all:${tagFilter.getOrElse("")}"
//              val cacheTTL = APIUtil.getPropsAsIntValue("getAllApiProductsV600.cache.ttl.seconds", 5)
//              val hit = Caching.getApiProductsCache(cacheKey, cacheTTL)
//                .flatMap(s => try Some(com.openbankproject.commons.util.JsonAliases.parse(s).extract[ApiProductsJsonV600]) catch { case _: Throwable => None })
//              hit match {
//                case Some(cached) => Future.successful(cached)
//                case None =>
//                  for {
//                    (banks, _) <- NewStyle.function.getBanks(callContext).map(t => (t._1, t._2))
//                    perBank <- Future.sequence(
//                      banks.map(b => NewStyle.function.getApiProductsByBankId(b.bankId.value, tagFilter, callContext).map(_._1))
//                    )
//                    apiProducts = perBank.flatten
//                  } yield {
//                    val result = JSONFactory600.createApiProductsJsonV600(apiProducts)
//                    Caching.setApiProductsCache(cacheKey, com.openbankproject.commons.util.JsonAliases.compactRender(Extraction.decompose(result)), cacheTTL)
//                    result
//                  }
//              }
//            }
//          } yield {
//            (resultJson, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteApiProduct,
//      implementedInApiVersion,
//      nameOf(deleteApiProduct),
//      "DELETE",
//      "/banks/BANK_ID/api-products/API_PRODUCT_CODE",
//      "Delete Api Product",
//      s"""Delete an Api Product by BANK_ID and API_PRODUCT_CODE.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ApiProductNotFound,
//        DeleteApiProductError,
//        UnknownError
//      ),
//      List(apiTagApi, apiTagApiProduct),
//      Some(List(canDeleteApiProduct))
//    )
//
//    lazy val deleteApiProduct: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "api-products" :: apiProductCode :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canDeleteApiProduct, callContext)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            (_, callContext) <- NewStyle.function.deleteApiProductAttributesByBankIdAndCode(bankId.value, apiProductCode, callContext)
//            (_, callContext) <- NewStyle.function.deleteApiProduct(bankId.value, apiProductCode, callContext)
//          } yield {
//            (Full(true), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // Financial Product Endpoints (v6.0.0 — adds tag support)
//
//    staticResourceDocs += ResourceDoc(
//      getProductsV600,
//      implementedInApiVersion,
//      nameOf(getProductsV600),
//      "GET",
//      "/banks/BANK_ID/products",
//      "Get Products",
//      s"""Returns the financial Products offered by the bank specified by BANK_ID. Response includes the new `tags` field.
//         |
//         |Optional query parameter `tag` — filter to products that carry the given tag (case-insensitive). Repeat `tag=` to require multiple tags (e.g. `?tag=featured&tag=new`).
//         |
//         |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
//      EmptyBody,
//      productsJsonV600,
//      List(
//        BankNotFound,
//        UnknownError
//      ),
//      List(apiTagProduct)
//    )
//
//    lazy val getProductsV600: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "products" :: Nil JsonGet req => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- getProductsIsPublic match {
//              case false => authenticatedAccess(cc)
//              case true  => anonymousAccess(cc)
//            }
//            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
//            params = req.params.toList.map(kv => GetProductsParam(kv._1, kv._2))
//            resultJson <- {
//              // Short TTL is the freshness guarantee; an admin tag change becomes visible within the TTL.
//              // Redis-backed with versioned namespace prefix so the cache shows up on /system/cache/info
//              // and can be invalidated by bumping the namespace version.
//              implicit val formats: org.json4s.Formats = org.json4s.DefaultFormats
//              val cacheKey = APIMethods600.productsCacheKey(bankId.value, params)
//              val cacheTTL = APIUtil.getPropsAsIntValue("getProductsV600.cache.ttl.seconds", 5)
//              val hit = Caching.getFinancialProductsCache(cacheKey, cacheTTL)
//                .flatMap(s => try Some(com.openbankproject.commons.util.JsonAliases.parse(s).extract[ProductsJsonV600]) catch { case _: Throwable => None })
//              hit match {
//                case Some(cached) => Future.successful(cached)
//                case None =>
//                  for {
//                    (products, _) <- NewStyle.function.getProducts(bankId, params, callContext)
//                  } yield {
//                    val tagsByCode = ProductTagsProvider.getTagsByProductCodes(bankId, products.map(_.code.value))
//                    val result = JSONFactory600.createProductsJsonV600(products, tagsByCode)
//                    Caching.setFinancialProductsCache(cacheKey, com.openbankproject.commons.util.JsonAliases.compactRender(Extraction.decompose(result)), cacheTTL)
//                    result
//                  }
//              }
//            }
//          } yield {
//            (resultJson, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAllProductsV600,
//      implementedInApiVersion,
//      nameOf(getAllProductsV600),
//      "GET",
//      "/products",
//      "Get Products At All Banks",
//      s"""Returns the financial Products offered by every bank this instance knows about, merged into a single list. Each product carries its `bank_id`.
//         |
//         |Optional query parameter `tag` — filter to products that carry the given tag (e.g. `?tag=featured`). Tag matching is case-insensitive. Repeat `tag=` to require multiple tags.
//         |
//         |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
//      EmptyBody,
//      productsJsonV600,
//      List(UnknownError),
//      List(apiTagProduct)
//    )
//
//    lazy val getAllProductsV600: OBPEndpoint = {
//      case "products" :: Nil JsonGet req => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- getProductsIsPublic match {
//              case false => authenticatedAccess(cc)
//              case true  => anonymousAccess(cc)
//            }
//            params = req.params.toList.map(kv => GetProductsParam(kv._1, kv._2))
//            resultJson <- {
//              implicit val formats: org.json4s.Formats = org.json4s.DefaultFormats
//              val cacheKey = APIMethods600.productsCacheKey("__all__", params)
//              val cacheTTL = APIUtil.getPropsAsIntValue("getAllProductsV600.cache.ttl.seconds", 60)
//              val hit = Caching.getFinancialProductsCache(cacheKey, cacheTTL)
//                .flatMap(s => try Some(com.openbankproject.commons.util.JsonAliases.parse(s).extract[ProductsJsonV600]) catch { case _: Throwable => None })
//              hit match {
//                case Some(cached) => Future.successful(cached)
//                case None =>
//                  for {
//                    (banks, _) <- NewStyle.function.getBanks(callContext).map(t => (t._1, t._2))
//                    // Fan-out server-side: one getProducts call per bank. The whole fan-out is cached
//                    // so the per-bank cost is paid once per TTL.
//                    perBank <- Future.sequence(
//                      banks.map(b => NewStyle.function.getProducts(b.bankId, params, callContext).map(_._1))
//                    )
//                    products = perBank.flatten
//                  } yield {
//                    val tagsByBank = banks.map { b =>
//                      val codesForBank = products.filter(_.bankId == b.bankId).map(_.code.value)
//                      b.bankId.value -> ProductTagsProvider.getTagsByProductCodes(b.bankId, codesForBank)
//                    }.toMap
//                    val tagsByCode = tagsByBank.values.foldLeft(Map.empty[String, List[String]])(_ ++ _)
//                    val result = JSONFactory600.createProductsJsonV600(products, tagsByCode)
//                    Caching.setFinancialProductsCache(cacheKey, com.openbankproject.commons.util.JsonAliases.compactRender(Extraction.decompose(result)), cacheTTL)
//                    result
//                  }
//              }
//            }
//          } yield {
//            (resultJson, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getProductTagsV600,
//      implementedInApiVersion,
//      nameOf(getProductTagsV600),
//      "GET",
//      "/banks/BANK_ID/products/PRODUCT_CODE/tags",
//      "Get Product Tags",
//      s"""Returns the list of tags currently set on the financial Product.
//         |
//         |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
//      EmptyBody,
//      productTagsJsonV600,
//      List(
//        BankNotFound,
//        ProductNotFoundByProductCode,
//        UnknownError
//      ),
//      List(apiTagProduct)
//    )
//
//    lazy val getProductTagsV600: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: "tags" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- getProductsIsPublic match {
//              case false => authenticatedAccess(cc)
//              case true  => anonymousAccess(cc)
//            }
//            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
//            (_, callContext) <- NewStyle.function.getProduct(bankId, productCode, callContext)
//            tags = ProductTagsProvider.getTags(bankId, productCode)
//          } yield {
//            (JSONFactory600.createProductTagsJsonV600(tags), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    val updateProductTagsEntitlements = canUpdateProductTagsAtOneBank :: canUpdateProductTagsAtAnyBank :: Nil
//    val updateProductTagsEntitlementsRequiredText = UserHasMissingRoles + updateProductTagsEntitlements.mkString(" or ")
//
//    staticResourceDocs += ResourceDoc(
//      updateProductTagsV600,
//      implementedInApiVersion,
//      nameOf(updateProductTagsV600),
//      "PUT",
//      "/banks/BANK_ID/products/PRODUCT_CODE/tags",
//      "Update Product Tags",
//      s"""Replaces the tags on a financial Product. Tags are free-form string labels (e.g. `featured`, `new`, `beta`). Tag matching in queries is case-insensitive.
//         |
//         |Authentication is Required.""".stripMargin,
//      productTagsJsonV600,
//      productTagsJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        BankNotFound,
//        ProductNotFoundByProductCode,
//        UnknownError
//      ),
//      List(apiTagProduct),
//      Some(updateProductTagsEntitlements)
//    )
//
//    lazy val updateProductTagsV600: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: "tags" :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = updateProductTagsEntitlementsRequiredText)(bankId.value, u.userId, updateProductTagsEntitlements, callContext)
//            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
//            (_, callContext) <- NewStyle.function.getProduct(bankId, productCode, callContext)
//            body <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ProductTagsJsonV600", 400, callContext) {
//              json.extract[ProductTagsJsonV600]
//            }
//            updatedTags <- NewStyle.function.tryons(s"$UpdateProductError", 400, callContext) {
//              ProductTagsProvider.setTags(bankId, productCode, body.tags)
//                .openOrThrowException(UpdateProductError)
//            }
//          } yield {
//            (JSONFactory600.createProductTagsJsonV600(updatedTags), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // Api Product Attribute Endpoints
//
//    staticResourceDocs += ResourceDoc(
//      createApiProductAttribute,
//      implementedInApiVersion,
//      nameOf(createApiProductAttribute),
//      "POST",
//      "/banks/BANK_ID/api-products/API_PRODUCT_CODE/attribute",
//      "Create Api Product Attribute",
//      s"""Create an Api Product Attribute.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      apiProductAttributeJsonV600,
//      apiProductAttributeResponseJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        ApiProductNotFound,
//        CreateApiProductAttributeError,
//        UnknownError
//      ),
//      List(apiTagApi, apiTagApiProductAttribute),
//      Some(List(canCreateApiProductAttribute))
//    )
//
//    lazy val createApiProductAttribute: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "api-products" :: apiProductCode :: "attribute" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canCreateApiProductAttribute, callContext)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            _ <- NewStyle.function.getApiProductByBankIdAndCode(bankId.value, apiProductCode, callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ApiProductAttributeJsonV600", 400, callContext) {
//              json.extract[ApiProductAttributeJsonV600]
//            }
//            (attribute, callContext) <- NewStyle.function.createOrUpdateApiProductAttribute(
//              bankId.value,
//              apiProductCode,
//              None,
//              postJson.name,
//              postJson.`type`,
//              postJson.value,
//              postJson.is_active,
//              callContext
//            )
//          } yield {
//            (JSONFactory600.createApiProductAttributeResponseJsonV600(attribute), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateApiProductAttribute,
//      implementedInApiVersion,
//      nameOf(updateApiProductAttribute),
//      "PUT",
//      "/banks/BANK_ID/api-products/API_PRODUCT_CODE/attributes/API_PRODUCT_ATTRIBUTE_ID",
//      "Update Api Product Attribute",
//      s"""Update an Api Product Attribute.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      apiProductAttributeJsonV600,
//      apiProductAttributeResponseJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        ApiProductNotFound,
//        ApiProductAttributeNotFound,
//        UnknownError
//      ),
//      List(apiTagApi, apiTagApiProductAttribute),
//      Some(List(canUpdateApiProductAttribute))
//    )
//
//    lazy val updateApiProductAttribute: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "api-products" :: apiProductCode :: "attributes" :: apiProductAttributeId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateApiProductAttribute, callContext)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            _ <- NewStyle.function.getApiProductByBankIdAndCode(bankId.value, apiProductCode, callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ApiProductAttributeJsonV600", 400, callContext) {
//              json.extract[ApiProductAttributeJsonV600]
//            }
//            (attribute, callContext) <- NewStyle.function.createOrUpdateApiProductAttribute(
//              bankId.value,
//              apiProductCode,
//              Some(apiProductAttributeId),
//              postJson.name,
//              postJson.`type`,
//              postJson.value,
//              postJson.is_active,
//              callContext
//            )
//          } yield {
//            (JSONFactory600.createApiProductAttributeResponseJsonV600(attribute), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getApiProductAttribute,
//      implementedInApiVersion,
//      nameOf(getApiProductAttribute),
//      "GET",
//      "/banks/BANK_ID/api-products/API_PRODUCT_CODE/attributes/API_PRODUCT_ATTRIBUTE_ID",
//      "Get Api Product Attribute",
//      s"""Get an Api Product Attribute by API_PRODUCT_ATTRIBUTE_ID.
//         |
//         |${userAuthenticationMessage(!getApiProductsIsPublic)}
//         |
//         |""".stripMargin,
//      EmptyBody,
//      apiProductAttributeResponseJsonV600,
//      if (getApiProductsIsPublic) List(ApiProductAttributeNotFound, UnknownError) else List(UserHasMissingRoles, ApiProductAttributeNotFound, UnknownError),
//      List(apiTagApi, apiTagApiProductAttribute),
//      if (getApiProductsIsPublic) None else Some(List(canGetApiProductAttribute))
//    )
//
//    lazy val getApiProductAttribute: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "api-products" :: apiProductCode :: "attributes" :: apiProductAttributeId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- getApiProductsIsPublic match {
//              case false => authenticatedAccess(cc)
//              case true  => anonymousAccess(cc)
//            }
//            _ <- if (!getApiProductsIsPublic) {
//              for {
//                (Full(u), callContext) <- authenticatedAccess(cc)
//                _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetApiProductAttribute, callContext)
//              } yield callContext
//            } else {
//              Future.successful(callContext)
//            }
//            (attribute, callContext) <- NewStyle.function.getApiProductAttributeById(apiProductAttributeId, callContext)
//          } yield {
//            (JSONFactory600.createApiProductAttributeResponseJsonV600(attribute), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteApiProductAttribute,
//      implementedInApiVersion,
//      nameOf(deleteApiProductAttribute),
//      "DELETE",
//      "/banks/BANK_ID/api-products/API_PRODUCT_CODE/attributes/API_PRODUCT_ATTRIBUTE_ID",
//      "Delete Api Product Attribute",
//      s"""Delete an Api Product Attribute by API_PRODUCT_ATTRIBUTE_ID.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ApiProductAttributeNotFound,
//        DeleteApiProductAttributeError,
//        UnknownError
//      ),
//      List(apiTagApi, apiTagApiProductAttribute),
//      Some(List(canDeleteApiProductAttribute))
//    )
//
//    lazy val deleteApiProductAttribute: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "api-products" :: apiProductCode :: "attributes" :: apiProductAttributeId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canDeleteApiProductAttribute, callContext)
//            (_, callContext) <- NewStyle.function.deleteApiProductAttribute(apiProductAttributeId, callContext)
//          } yield {
//            (Full(true), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getConnectorTraces,
//      implementedInApiVersion,
//      nameOf(getConnectorTraces),
//      "GET",
//      "/management/connector/traces",
//      "Get Connector Traces",
//      s"""Get connector traces which capture the full outbound/inbound messages for each connector call.
//         |
//         |Connector tracing must be enabled via the write_connector_trace=true property.
//         |
//         |Filters Part 1.*filtering* parameters to GET /management/connector/traces
//         |
//         |Should be able to filter on the following fields:
//         |
//         |eg: /management/connector/traces?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=50&offset=2
//         |
//         |1 from_date (defaults to one week before current date): eg:from_date=$DateWithMsExampleString
//         |
//         |2 to_date (defaults to current date) eg:to_date=$DateWithMsExampleString
//         |
//         |3 limit (for pagination: defaults to 1000) eg:limit=2000
//         |
//         |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
//         |
//         |5 connector_name (if null ignore)
//         |
//         |6 function_name (if null ignore)
//         |
//         |7 correlation_id (if null ignore)
//         |
//         |8 bank_id (if null ignore)
//         |
//         |9 user_id (if null ignore)
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      connectorTracesJsonV600,
//      List(
//        InvalidDateFormat,
//        UnknownError
//      ),
//      List(apiTagMetric, apiTagApi),
//      Some(List(canGetConnectorTrace)))
//
//    lazy val getConnectorTraces: OBPEndpoint = {
//      case "management" :: "connector" :: "traces" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
//            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, callContext)
//            traces <- Future(ConnectorTraceProvider.getAllConnectorTraces(obpQueryParams))
//          } yield {
//            (JSONFactory600.createConnectorTracesJsonV600(traces), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getConfigProps,
//      implementedInApiVersion,
//      nameOf(getConfigProps),
//      "GET",
//      "/management/config-props",
//      "Get Config Props",
//      s"""Get the active configuration properties and their runtime values.
//         |
//         |This endpoint uses a self-registration mechanism: each time the code calls
//         |getPropsValue, getPropsAsBoolValue, getPropsAsIntValue, or getPropsAsLongValue
//         |with a default value, that property key is registered.
//         |
//         |Only registered properties are returned. The list grows as more code paths are
//         |exercised. Most properties are registered at startup.
//         |
//         |For each property, the value shown is the actual runtime value. If the property
//         |is not explicitly set, the code-defined default is shown.
//         |
//         |The response includes both regular and webui_ properties, sorted alphabetically by key.
//         |
//         |Properties with sensitive keys or values (containing ${APIUtil.sensitiveKeywords.mkString(", ")})
//         |are excluded from the response entirely.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      configPropsJsonV600,
//      List(
//        UnknownError
//      ),
//      List(apiTagApi),
//      Some(List(canGetConfigProps)))
//
//    lazy val getConfigProps: OBPEndpoint = {
//      case "management" :: "config-props" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            configProps = getConfigPropsPairs.map { case (key, value) =>
//              ConfigPropJsonV600(key, maskSensitivePropValue(key, value))
//            }
//          } yield {
//            (ListResult("config_props", configProps), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAppDirectory,
//      implementedInApiVersion,
//      nameOf(getAppDirectory),
//      "GET",
//      "/app-directory",
//      "Get App Directory",
//      s"""Get connectivity information for apps in the OBP ecosystem.
//         |
//         |Returns configuration properties that apps (Portal, API Explorer, API Manager,
//         |Sandbox Populator, OIDC, Keycloak, Hola, MCP, Opey) and agents can use to discover
//         |endpoints in the OBP ecosystem.
//         |
//         |Any props starting with public_ and ending with _url are included automatically.
//         |
//         |Known public app URL props:
//         |${APIUtil.publicAppUrlPropNames.mkString(", ")}
//         |
//         |Empty (unconfigured) values are excluded from the response.
//         |
//         |Authentication is NOT Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      appDirectoryJsonV600,
//      List(
//        UnknownError
//      ),
//      List(apiTagApi),
//      Some(List()))
//
//    lazy val getAppDirectory: OBPEndpoint = {
//      case "app-directory" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, callContext) <- anonymousAccess(cc)
//            directoryProps = getAppDiscoveryPairs.map { case (key, value) =>
//              ConfigPropJsonV600(key, value)
//            }
//          } yield {
//            (ListResult("app_directory", directoryProps), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // Backup Dynamic Entity Endpoints
//
//    private def computeBackupName(bankId: Option[String], baseName: String): String = {
//      val firstCandidate = s"${baseName}_BAK"
//      if (DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(bankId, firstCandidate).isEmpty) {
//        firstCandidate
//      } else {
//        var suffix = 2
//        var candidate = s"${baseName}_BAK$suffix"
//        while (DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(bankId, candidate).isDefined) {
//          suffix += 1
//          candidate = s"${baseName}_BAK$suffix"
//        }
//        candidate
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      backupSystemDynamicEntity,
//      implementedInApiVersion,
//      nameOf(backupSystemDynamicEntity),
//      "POST",
//      "/management/system-dynamic-entities/DYNAMIC_ENTITY_ID/backup",
//      "Backup System Level Dynamic Entity",
//      s"""Create a backup copy of a system level DynamicEntity specified by DYNAMIC_ENTITY_ID.
//         |
//         |This endpoint creates a backup of the dynamic entity definition and all its data records.
//         |The backup entity will be named with a _BAK suffix (e.g. my_entity_BAK).
//         |If a backup with that name already exists, _BAK2, _BAK3 etc. will be used.
//         |
//         |The calling user will be granted CanGetDynamicEntity_`<BackupEntityName>` on the newly created backup entity.
//         |
//         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}
//         |
//         |Authentication is Required
//         |
//         |""",
//      EmptyBody,
//      DynamicEntityDefinitionJsonV600(
//        dynamic_entity_id = "abc-123-def",
//        entity_name = "my_entity_BAK",
//        user_id = "user-456",
//        bank_id = None,
//        has_personal_entity = false,
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "Backup entity", "required": ["name"], "properties": {"name": {"type": "string", "example": "test"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi),
//      Some(List(canBackupSystemDynamicEntity))
//    )
//    lazy val backupSystemDynamicEntity: OBPEndpoint = {
//      case "management" :: "system-dynamic-entities" :: dynamicEntityId :: "backup" :: Nil JsonPost _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          backupDynamicEntityMethod(None, dynamicEntityId, cc)
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      backupBankLevelDynamicEntity,
//      implementedInApiVersion,
//      nameOf(backupBankLevelDynamicEntity),
//      "POST",
//      "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/backup",
//      "Backup Bank Level Dynamic Entity",
//      s"""Create a backup copy of a bank level DynamicEntity specified by DYNAMIC_ENTITY_ID.
//         |
//         |This endpoint creates a backup of the dynamic entity definition and all its data records.
//         |The backup entity will be named with a _BAK suffix (e.g. my_entity_BAK).
//         |If a backup with that name already exists, _BAK2, _BAK3 etc. will be used.
//         |
//         |The calling user will be granted CanGetDynamicEntity_`<BackupEntityName>` on the newly created backup entity.
//         |
//         |For more information see ${Glossary.getGlossaryItemLink("Dynamic-Entities")}
//         |
//         |Authentication is Required
//         |
//         |""",
//      EmptyBody,
//      DynamicEntityDefinitionJsonV600(
//        dynamic_entity_id = "abc-123-def",
//        entity_name = "my_entity_BAK",
//        user_id = "user-456",
//        bank_id = Some("gh.29.uk"),
//        has_personal_entity = false,
//        schema = com.openbankproject.commons.util.JsonAliases.parse("""{"description": "Backup entity", "required": ["name"], "properties": {"name": {"type": "string", "example": "test"}}}""").asInstanceOf[org.json4s.JsonAST.JObject]
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagManageDynamicEntity, apiTagApi),
//      Some(List(canBackupBankLevelDynamicEntity))
//    )
//    lazy val backupBankLevelDynamicEntity: OBPEndpoint = {
//      case "management" :: "banks" :: BankId(bankId) :: "dynamic-entities" :: dynamicEntityId :: "backup" :: Nil JsonPost _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          backupDynamicEntityMethod(Some(bankId.value), dynamicEntityId, cc)
//      }
//    }
//
//    private def backupDynamicEntityMethod(
//        bankId: Option[String],
//        dynamicEntityId: String,
//        cc: CallContext
//    ) = {
//      for {
//        // Get the dynamic entity definition
//        (entity, _) <- NewStyle.function.getDynamicEntityById(
//          bankId,
//          dynamicEntityId,
//          cc.callContext
//        )
//        // Check CanGetDynamicEntity_<EntityName> role
//        canGetRole = DynamicEntityInfo.canGetRole(entity.entityName, entity.bankId)
//        _ <- NewStyle.function.hasEntitlement(entity.bankId.getOrElse(""), cc.userId, canGetRole, cc.callContext)
//
//        // Get all data records for this entity
//        (box, _) <- NewStyle.function.invokeDynamicConnector(
//          GET_ALL,
//          entity.entityName,
//          None,
//          None,
//          entity.bankId,
//          None,
//          None,
//          false,
//          cc.callContext
//        )
//        resultList: JArray = unboxResult(
//          box.asInstanceOf[Box[JArray]],
//          entity.entityName
//        )
//
//        // Compute backup name with _BAK, _BAK2, _BAK3 etc.
//        backupName = computeBackupName(entity.bankId, entity.entityName)
//
//        // Perform the backup
//        _ <- Future { backupDynamicEntity(entity, backupName, resultList) }
//
//        // Grant CanGet role on the backup entity to the calling user
//        backupCanGetRole = DynamicEntityInfo.canGetRole(backupName, entity.bankId)
//        _ <- Future {
//          Entitlement.entitlement.vend.addEntitlement(
//            entity.bankId.getOrElse(""), cc.userId, backupCanGetRole.toString()
//          )
//        }
//
//        // Fetch the created backup entity to return it
//        backupEntity <- Future {
//          DynamicEntityProvider.connectorMethodProvider.vend
//            .getByEntityName(entity.bankId, backupName)
//            .openOrThrowException("Backup entity not found after creation")
//        }
//      } yield {
//        val commonsData: DynamicEntityCommons = backupEntity
//        (
//          JSONFactory600.createMyDynamicEntitiesJson(List(commonsData)).dynamic_entities.head,
//          HttpCode.`201`(cc.callContext)
//        )
//      }
//    }
//
//    // --- Account Access Request Endpoints ---
//
//    staticResourceDocs += ResourceDoc(
//      createAccountAccessRequest,
//      implementedInApiVersion,
//      nameOf(createAccountAccessRequest),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests",
//      "Create Account Access Request",
//      s"""Create a new Account Access Request (maker step in maker/checker workflow).
//         |
//         |The requestor (maker) creates a request to grant a target user access to a specific view on an account.
//         |A business justification is required.
//         |
//         |The request is created with status INITIATED and must be approved or rejected by a different user (checker).
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      JSONFactory600.PostAccountAccessRequestJsonV600(
//        target_user_id = ExampleValue.userIdExample.value,
//        view_id = ExampleValue.viewIdExample.value,
//        is_system_view = true,
//        business_justification = "Need access to review monthly account statements for audit purposes."
//      ),
//      JSONFactory600.AccountAccessRequestJsonV600(
//        account_access_request_id = "b4e0352a-9a0f-4bfa-b30b-9003aa467f51",
//        bank_id = ExampleValue.bankIdExample.value,
//        account_id = ExampleValue.accountIdExample.value,
//        view_id = ExampleValue.viewIdExample.value,
//        is_system_view = true,
//        requestor_user_id = ExampleValue.userIdExample.value,
//        target_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b2",
//        business_justification = "Need access to review monthly account statements for audit purposes.",
//        status = "INITIATED",
//        checker_user_id = "",
//        checker_comment = "",
//        created = APIUtil.DateWithMsExampleObject,
//        updated = APIUtil.DateWithMsExampleObject
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        $BankNotFound,
//        $BankAccountNotFound,
//        BusinessJustificationRequired,
//        AccountAccessRequestAlreadyExists,
//        AccountAccessRequestCannotBeCreated,
//        UnknownError
//      ),
//      List(apiTagAccountAccessRequest),
//      Some(List(canCreateAccountAccessRequestAtOneBank, canCreateAccountAccessRequestAtAnyBank))
//    )
//
//    lazy val createAccountAccessRequest: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access-requests" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
//            _ <- NewStyle.function.hasAtLeastOneEntitlement(bankId.value, u.userId, canCreateAccountAccessRequestAtOneBank :: canCreateAccountAccessRequestAtAnyBank :: Nil, callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${JSONFactory600.PostAccountAccessRequestJsonV600.getClass.getSimpleName}", 400, callContext) {
//              json.extract[JSONFactory600.PostAccountAccessRequestJsonV600]
//            }
//            _ <- Helper.booleanToFuture(failMsg = BusinessJustificationRequired, cc = callContext) {
//              postJson.business_justification.trim.nonEmpty
//            }
//            // Validate target user exists
//            (_, callContext) <- NewStyle.function.findByUserId(postJson.target_user_id, callContext)
//            // Check for existing INITIATED request for same user/account/view
//            _ <- Helper.booleanToFuture(failMsg = AccountAccessRequestAlreadyExists, failCode = 409, cc = callContext) {
//              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
//                .getByUserAccountView(postJson.target_user_id, bankId.value, accountId.value, postJson.view_id)
//                .isEmpty
//            }
//            // Validate the view exists
//            _ <- if (postJson.is_system_view) {
//              ViewNewStyle.systemView(ViewId(postJson.view_id), callContext).map(_ => ())
//            } else {
//              ViewNewStyle.customView(ViewId(postJson.view_id), BankIdAccountId(bankId, accountId), callContext).map(_ => ())
//            }
//            request <- Future {
//              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend.createAccountAccessRequest(
//                bankId.value,
//                accountId.value,
//                postJson.view_id,
//                postJson.is_system_view,
//                u.userId,
//                postJson.target_user_id,
//                postJson.business_justification
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, AccountAccessRequestCannotBeCreated, 400)
//            }
//          } yield {
//            (JSONFactory600.createAccountAccessRequestJsonV600(request), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAccountAccessRequestsForAccount,
//      implementedInApiVersion,
//      nameOf(getAccountAccessRequestsForAccount),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests",
//      "Get Account Access Requests for Account",
//      s"""Get Account Access Requests for a specific account (checker view).
//         |
//         |Optionally filter by status using the query parameter: ?status=INITIATED
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      JSONFactory600.AccountAccessRequestsJsonV600(
//        account_access_requests = List(JSONFactory600.AccountAccessRequestJsonV600(
//          account_access_request_id = "b4e0352a-9a0f-4bfa-b30b-9003aa467f51",
//          bank_id = ExampleValue.bankIdExample.value,
//          account_id = ExampleValue.accountIdExample.value,
//          view_id = ExampleValue.viewIdExample.value,
//          is_system_view = true,
//          requestor_user_id = ExampleValue.userIdExample.value,
//          target_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b2",
//          business_justification = "Need access to review monthly account statements for audit purposes.",
//          status = "INITIATED",
//          checker_user_id = "",
//          checker_comment = "",
//          created = APIUtil.DateWithMsExampleObject,
//          updated = APIUtil.DateWithMsExampleObject
//        ))
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        $BankAccountNotFound,
//        UnknownError
//      ),
//      List(apiTagAccountAccessRequest),
//      Some(List(canGetAccountAccessRequestsAtOneBank, canGetAccountAccessRequestsAtAnyBank))
//    )
//
//    lazy val getAccountAccessRequestsForAccount: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access-requests" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
//            _ <- NewStyle.function.hasAtLeastOneEntitlement(bankId.value, u.userId, canGetAccountAccessRequestsAtOneBank :: canGetAccountAccessRequestsAtAnyBank :: Nil, callContext)
//            statusParam = ObpS.param("status")
//            requests <- Future {
//              statusParam match {
//                case Full(status) =>
//                  code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
//                    .getByAccountAndStatus(bankId.value, accountId.value, status)
//                case _ =>
//                  code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
//                    .getByAccount(bankId.value, accountId.value)
//              }
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get account access requests", 400)
//            }
//          } yield {
//            (JSONFactory600.createAccountAccessRequestsJsonV600(requests), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getMyAccountAccessRequests,
//      implementedInApiVersion,
//      nameOf(getMyAccountAccessRequests),
//      "GET",
//      "/my/account-access-requests",
//      "Get My Account Access Requests",
//      s"""Get Account Access Requests created by the current user (maker view).
//         |
//         |No special roles are required — a user can always see their own requests.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      JSONFactory600.AccountAccessRequestsJsonV600(
//        account_access_requests = List(JSONFactory600.AccountAccessRequestJsonV600(
//          account_access_request_id = "b4e0352a-9a0f-4bfa-b30b-9003aa467f51",
//          bank_id = ExampleValue.bankIdExample.value,
//          account_id = ExampleValue.accountIdExample.value,
//          view_id = ExampleValue.viewIdExample.value,
//          is_system_view = true,
//          requestor_user_id = ExampleValue.userIdExample.value,
//          target_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b2",
//          business_justification = "Need access to review monthly account statements for audit purposes.",
//          status = "INITIATED",
//          checker_user_id = "",
//          checker_comment = "",
//          created = APIUtil.DateWithMsExampleObject,
//          updated = APIUtil.DateWithMsExampleObject
//        ))
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagAccountAccessRequest),
//      None
//    )
//
//    lazy val getMyAccountAccessRequests: OBPEndpoint = {
//      case "my" :: "account-access-requests" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            requests <- Future {
//              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
//                .getByRequestorUserId(u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get account access requests", 400)
//            }
//          } yield {
//            (JSONFactory600.createAccountAccessRequestsJsonV600(requests), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAccountAccessRequestById,
//      implementedInApiVersion,
//      nameOf(getAccountAccessRequestById),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests/ACCOUNT_ACCESS_REQUEST_ID",
//      "Get Account Access Request by Id",
//      s"""Get a single Account Access Request by its ID.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      JSONFactory600.AccountAccessRequestJsonV600(
//        account_access_request_id = "b4e0352a-9a0f-4bfa-b30b-9003aa467f51",
//        bank_id = ExampleValue.bankIdExample.value,
//        account_id = ExampleValue.accountIdExample.value,
//        view_id = ExampleValue.viewIdExample.value,
//        is_system_view = true,
//        requestor_user_id = ExampleValue.userIdExample.value,
//        target_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b2",
//        business_justification = "Need access to review monthly account statements for audit purposes.",
//        status = "INITIATED",
//        checker_user_id = "",
//        checker_comment = "",
//        created = APIUtil.DateWithMsExampleObject,
//        updated = APIUtil.DateWithMsExampleObject
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        $BankAccountNotFound,
//        AccountAccessRequestNotFound,
//        UnknownError
//      ),
//      List(apiTagAccountAccessRequest),
//      Some(List(canGetAccountAccessRequestsAtOneBank, canGetAccountAccessRequestsAtAnyBank))
//    )
//
//    lazy val getAccountAccessRequestById: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access-requests" :: accountAccessRequestId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
//            _ <- NewStyle.function.hasAtLeastOneEntitlement(bankId.value, u.userId, canGetAccountAccessRequestsAtOneBank :: canGetAccountAccessRequestsAtAnyBank :: Nil, callContext)
//            request <- Future {
//              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
//                .getById(accountAccessRequestId)
//            } map {
//              x => unboxFullOrFail(x, callContext, AccountAccessRequestNotFound, 404)
//            }
//            // Verify the request belongs to this bank/account
//            _ <- Helper.booleanToFuture(failMsg = AccountAccessRequestNotFound, cc = callContext) {
//              request.bankId == bankId.value && request.accountId == accountId.value
//            }
//          } yield {
//            (JSONFactory600.createAccountAccessRequestJsonV600(request), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      approveAccountAccessRequest,
//      implementedInApiVersion,
//      nameOf(approveAccountAccessRequest),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests/ACCOUNT_ACCESS_REQUEST_ID/approval",
//      "Approve Account Access Request",
//      s"""Approve an Account Access Request (checker step in maker/checker workflow).
//         |
//         |The checker must be a different user than the maker (requestor). This enforces dual control / maker-checker separation.
//         |
//         |Only requests with status INITIATED can be approved.
//         |
//         |On approval, the system automatically grants the target user access to the specified view.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      JSONFactory600.PostApproveAccountAccessRequestJsonV600(
//        comment = Some("Approved for Q1 audit.")
//      ),
//      JSONFactory600.AccountAccessRequestJsonV600(
//        account_access_request_id = "b4e0352a-9a0f-4bfa-b30b-9003aa467f51",
//        bank_id = ExampleValue.bankIdExample.value,
//        account_id = ExampleValue.accountIdExample.value,
//        view_id = ExampleValue.viewIdExample.value,
//        is_system_view = true,
//        requestor_user_id = ExampleValue.userIdExample.value,
//        target_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b2",
//        business_justification = "Need access to review monthly account statements for audit purposes.",
//        status = "APPROVED",
//        checker_user_id = "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
//        checker_comment = "Approved for Q1 audit.",
//        created = APIUtil.DateWithMsExampleObject,
//        updated = APIUtil.DateWithMsExampleObject
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        $BankNotFound,
//        $BankAccountNotFound,
//        AccountAccessRequestNotFound,
//        AccountAccessRequestStatusNotInitiated,
//        MakerCheckerSameUser,
//        AccountAccessRequestCannotBeUpdated,
//        UnknownError
//      ),
//      List(apiTagAccountAccessRequest),
//      Some(List(canUpdateAccountAccessRequestAtOneBank, canUpdateAccountAccessRequestAtAnyBank))
//    )
//
//    lazy val approveAccountAccessRequest: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access-requests" :: accountAccessRequestId :: "approval" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
//            _ <- NewStyle.function.hasAtLeastOneEntitlement(bankId.value, u.userId, canUpdateAccountAccessRequestAtOneBank :: canUpdateAccountAccessRequestAtAnyBank :: Nil, callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostApproveAccountAccessRequestJsonV600", 400, callContext) {
//              json.extract[JSONFactory600.PostApproveAccountAccessRequestJsonV600]
//            }
//            request <- Future {
//              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
//                .getById(accountAccessRequestId)
//            } map {
//              x => unboxFullOrFail(x, callContext, AccountAccessRequestNotFound, 404)
//            }
//            // Verify the request belongs to this bank/account
//            _ <- Helper.booleanToFuture(failMsg = AccountAccessRequestNotFound, cc = callContext) {
//              request.bankId == bankId.value && request.accountId == accountId.value
//            }
//            // Only INITIATED requests can be approved
//            _ <- Helper.booleanToFuture(failMsg = AccountAccessRequestStatusNotInitiated, cc = callContext) {
//              request.status == com.openbankproject.commons.model.enums.AccountAccessRequestStatus.INITIATED.toString
//            }
//            // Maker/checker separation: checker must not be the requestor
//            _ <- Helper.booleanToFuture(failMsg = MakerCheckerSameUser, cc = callContext) {
//              u.userId != request.requestorUserId
//            }
//            // Get the target user
//            (targetUser, callContext) <- NewStyle.function.findByUserId(request.targetUserId, callContext)
//            // Grant view access
//            _ <- if (request.isSystemView) {
//              ViewNewStyle.systemView(ViewId(request.viewId), callContext).flatMap { view =>
//                ViewNewStyle.grantAccessToSystemView(bankId, accountId, view, targetUser, callContext)
//              }
//            } else {
//              ViewNewStyle.customView(ViewId(request.viewId), BankIdAccountId(bankId, accountId), callContext).flatMap { view =>
//                ViewNewStyle.grantAccessToCustomView(view, targetUser, callContext)
//              }
//            }
//            // Update the request status to APPROVED
//            updatedRequest <- Future {
//              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend.updateStatus(
//                accountAccessRequestId,
//                com.openbankproject.commons.model.enums.AccountAccessRequestStatus.APPROVED.toString,
//                u.userId,
//                postJson.comment.getOrElse("")
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, AccountAccessRequestCannotBeUpdated, 400)
//            }
//          } yield {
//            (JSONFactory600.createAccountAccessRequestJsonV600(updatedRequest), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      rejectAccountAccessRequest,
//      implementedInApiVersion,
//      nameOf(rejectAccountAccessRequest),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/account-access-requests/ACCOUNT_ACCESS_REQUEST_ID/rejection",
//      "Reject Account Access Request",
//      s"""Reject an Account Access Request (checker step in maker/checker workflow).
//         |
//         |The checker must be a different user than the maker (requestor). This enforces dual control / maker-checker separation.
//         |
//         |Only requests with status INITIATED can be rejected.
//         |
//         |A comment is required when rejecting a request.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      JSONFactory600.PostRejectAccountAccessRequestJsonV600(
//        comment = "Insufficient business justification provided."
//      ),
//      JSONFactory600.AccountAccessRequestJsonV600(
//        account_access_request_id = "b4e0352a-9a0f-4bfa-b30b-9003aa467f51",
//        bank_id = ExampleValue.bankIdExample.value,
//        account_id = ExampleValue.accountIdExample.value,
//        view_id = ExampleValue.viewIdExample.value,
//        is_system_view = true,
//        requestor_user_id = ExampleValue.userIdExample.value,
//        target_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b2",
//        business_justification = "Need access to review monthly account statements for audit purposes.",
//        status = "REJECTED",
//        checker_user_id = "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
//        checker_comment = "Insufficient business justification provided.",
//        created = APIUtil.DateWithMsExampleObject,
//        updated = APIUtil.DateWithMsExampleObject
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        InvalidJsonFormat,
//        $BankNotFound,
//        $BankAccountNotFound,
//        AccountAccessRequestNotFound,
//        AccountAccessRequestStatusNotInitiated,
//        MakerCheckerSameUser,
//        CheckerCommentRequiredForRejection,
//        AccountAccessRequestCannotBeUpdated,
//        UnknownError
//      ),
//      List(apiTagAccountAccessRequest),
//      Some(List(canUpdateAccountAccessRequestAtOneBank, canUpdateAccountAccessRequestAtAnyBank))
//    )
//
//    lazy val rejectAccountAccessRequest: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "account-access-requests" :: accountAccessRequestId :: "rejection" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
//            _ <- NewStyle.function.hasAtLeastOneEntitlement(bankId.value, u.userId, canUpdateAccountAccessRequestAtOneBank :: canUpdateAccountAccessRequestAtAnyBank :: Nil, callContext)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostRejectAccountAccessRequestJsonV600", 400, callContext) {
//              json.extract[JSONFactory600.PostRejectAccountAccessRequestJsonV600]
//            }
//            _ <- Helper.booleanToFuture(failMsg = CheckerCommentRequiredForRejection, cc = callContext) {
//              postJson.comment.trim.nonEmpty
//            }
//            request <- Future {
//              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend
//                .getById(accountAccessRequestId)
//            } map {
//              x => unboxFullOrFail(x, callContext, AccountAccessRequestNotFound, 404)
//            }
//            // Verify the request belongs to this bank/account
//            _ <- Helper.booleanToFuture(failMsg = AccountAccessRequestNotFound, cc = callContext) {
//              request.bankId == bankId.value && request.accountId == accountId.value
//            }
//            // Only INITIATED requests can be rejected
//            _ <- Helper.booleanToFuture(failMsg = AccountAccessRequestStatusNotInitiated, cc = callContext) {
//              request.status == com.openbankproject.commons.model.enums.AccountAccessRequestStatus.INITIATED.toString
//            }
//            // Maker/checker separation: checker must not be the requestor
//            _ <- Helper.booleanToFuture(failMsg = MakerCheckerSameUser, cc = callContext) {
//              u.userId != request.requestorUserId
//            }
//            // Update the request status to REJECTED
//            updatedRequest <- Future {
//              code.accountaccessrequest.AccountAccessRequestTrait.accountAccessRequest.vend.updateStatus(
//                accountAccessRequestId,
//                com.openbankproject.commons.model.enums.AccountAccessRequestStatus.REJECTED.toString,
//                u.userId,
//                postJson.comment
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, AccountAccessRequestCannotBeUpdated, 400)
//            }
//          } yield {
//            (JSONFactory600.createAccountAccessRequestJsonV600(updatedRequest), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//
//    // ---- Signal Channels (Redis-backed short-lived messaging for AI agents and other consumers) ----
//
//    staticResourceDocs += ResourceDoc(
//      publishSignalMessage,
//      implementedInApiVersion,
//      nameOf(publishSignalMessage),
//      "POST",
//      "/signal/channels/CHANNEL_NAME/messages",
//      "Publish Signal Message",
//      s"""Publish a message to a signal channel.
//         |
//         |Signal channels provide short-lived, Redis-backed messaging for lightweight coordination between
//         |AI agents and other OBP consumers. Messages are not persisted to a database.
//         |
//         |Channels are auto-created on first publish and expire after a configurable TTL (default 1 hour).
//         |Messages are capped at a configurable maximum per channel (default 1000).
//         |
//         |The payload field accepts any valid JSON content.
//         |
//         |Set to_user_id to send a private message visible only to the sender and recipient.
//         |Leave to_user_id empty for a broadcast message visible to all channel readers.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      postSignalMessageJsonV600,
//      signalMessagePublishedJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        InvalidSignalChannelName,
//        UnknownError
//      ),
//      List(apiTagAiAgent, apiTagSignal, apiTagSignalling, apiTagChannel))
//
//    lazy val publishSignalMessage: OBPEndpoint = {
//      case "signal" :: "channels" :: channelName :: "messages" :: Nil JsonPost json -> _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the PostSignalMessageJsonV600", 400, callContext) {
//              json.extract[PostSignalMessageJsonV600]
//            }
//            _ <- Helper.booleanToFuture(failMsg = InvalidSignalChannelName, cc = callContext) {
//              RedisMessaging.validateChannelName(channelName)
//            }
//            channelMessageCount <- Future {
//              val consumerId: String = cc.consumer match {
//                case Full(c) => c.consumerId.get
//                case _ => ""
//              }
//              val messageId = randomUUID().toString
//              val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
//              sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
//              val timestamp = sdf.format(new java.util.Date())
//              val messageEnvelope = SignalMessageJsonV600(
//                message_id = messageId,
//                channel_name = channelName,
//                sender_consumer_id = consumerId,
//                sender_user_id = u.userId,
//                to_user_id = postJson.to_user_id,
//                timestamp = timestamp,
//                message_type = postJson.message_type.getOrElse(""),
//                payload = postJson.payload
//              )
//              val messageJsonString = com.openbankproject.commons.util.JsonAliases.compactRender(org.json4s.Extraction.decompose(messageEnvelope))
//              val count = RedisMessaging.publishMessage(channelName, messageJsonString)
//              (messageId, timestamp, count)
//            }
//          } yield {
//            val (messageId, timestamp, count) = channelMessageCount
//            val response = SignalMessagePublishedJsonV600(
//              message_id = messageId,
//              channel_name = channelName,
//              timestamp = timestamp,
//              channel_message_count = count
//            )
//            (response, HttpCode.`201`(callContext))
//          }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      getSignalMessages,
//      implementedInApiVersion,
//      nameOf(getSignalMessages),
//      "GET",
//      "/signal/channels/CHANNEL_NAME/messages",
//      "Get Signal Messages",
//      s"""Fetch messages from a signal channel with offset/limit pagination.
//         |
//         |Signal channels provide short-lived, Redis-backed messaging designed for AI agent discovery
//         |and coordination, but usable by any authenticated OBP consumer.
//         |
//         |Messages are returned oldest-first.
//         |
//         |Privacy filtering is applied server-side: you will only see broadcast messages (no to_user_id)
//         |and private messages addressed to you (to_user_id matches your user ID) or sent by you.
//         |
//         |Use the offset parameter to poll for new messages by tracking your position.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      signalMessagesJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidSignalChannelName,
//        UnknownError
//      ),
//      List(apiTagAiAgent, apiTagSignal, apiTagSignalling, apiTagChannel))
//
//    lazy val getSignalMessages: OBPEndpoint = {
//      case "signal" :: "channels" :: channelName :: "messages" :: Nil JsonGet _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Helper.booleanToFuture(failMsg = InvalidSignalChannelName, cc = callContext) {
//              RedisMessaging.validateChannelName(channelName)
//            }
//            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
//            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, callContext)
//            limit = obpQueryParams.collectFirst { case OBPLimit(value) => value }.getOrElse(50)
//            offset = obpQueryParams.collectFirst { case OBPOffset(value) => value }.getOrElse(0)
//            (rawMessages, totalCount) <- Future {
//              RedisMessaging.fetchMessages(channelName, offset, limit)
//            }
//          } yield {
//            val parsedMessages: List[SignalMessageJsonV600] = rawMessages.flatMap { msgStr =>
//              scala.util.Try(com.openbankproject.commons.util.JsonAliases.parse(msgStr).extract[SignalMessageJsonV600]).toOption
//            }
//            // Privacy filter: only show broadcasts (to_user_id is None) and messages to/from this user
//            val filteredMessages = parsedMessages.filter { msg =>
//              msg.to_user_id.isEmpty ||
//                msg.to_user_id.contains(u.userId) ||
//                msg.sender_user_id == u.userId
//            }
//            val response = SignalMessagesJsonV600(
//              channel_name = channelName,
//              messages = filteredMessages,
//              total_count = totalCount,
//              has_more = (offset + limit) < totalCount
//            )
//            (response, HttpCode.`200`(callContext))
//          }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      getSignalChannels,
//      implementedInApiVersion,
//      nameOf(getSignalChannels),
//      "GET",
//      "/signal/channels",
//      "List Signal Channels",
//      s"""Signal channels provide short-lived, Redis-backed messaging designed for AI agent discovery and coordination, but usable by any authenticated OBP consumer.
//         |Messages are ephemeral and will expire after the configured TTL (default 1 hour).
//         |
//         |This endpoint lists active signal channels.
//         |Only channels that contain at least one broadcast message (no to_user_id) are listed.
//         |Private-only channels are not shown.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      signalChannelsJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagAiAgent, apiTagSignal, apiTagSignalling, apiTagChannel))
//
//    lazy val getSignalChannels: OBPEndpoint = {
//      case "signal" :: "channels" :: Nil JsonGet _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            channelNames <- Future {
//              RedisMessaging.listChannels()
//            }
//            channelsWithInfo <- Future.sequence(
//              channelNames.map { name =>
//                Future {
//                  RedisMessaging.channelInfo(name).map { case (count, ttl) =>
//                    // Check if channel has any broadcast messages
//                    val (messages, _) = RedisMessaging.fetchMessages(name, 0, count.toInt)
//                    val hasBroadcast = messages.exists { msgStr =>
//                      scala.util.Try {
//                        val msg = com.openbankproject.commons.util.JsonAliases.parse(msgStr).extract[SignalMessageJsonV600]
//                        msg.to_user_id.isEmpty
//                      }.getOrElse(false)
//                    }
//                    (name, count, ttl, hasBroadcast)
//                  }
//                }
//              }
//            )
//          } yield {
//            val channels = channelsWithInfo.flatten
//              .filter(_._4) // Only channels with broadcast messages
//              .map { case (name, count, ttl, _) =>
//                SignalChannelInfoJsonV600(
//                  channel_name = name,
//                  message_count = count,
//                  ttl_seconds = ttl
//                )
//              }
//            (SignalChannelsJsonV600(channels), HttpCode.`200`(callContext))
//          }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      getSignalChannelInfo,
//      implementedInApiVersion,
//      nameOf(getSignalChannelInfo),
//      "GET",
//      "/signal/channels/CHANNEL_NAME/info",
//      "Get Signal Channel Info",
//      s"""Signal channels provide short-lived, Redis-backed messaging designed for AI agent discovery and coordination, but usable by any authenticated OBP consumer.
//         |Messages are ephemeral and will expire after the configured TTL (default 1 hour).
//         |
//         |This endpoint returns metadata about a signal channel including the current message count and remaining TTL in seconds.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      signalChannelInfoJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidSignalChannelName,
//        UnknownError
//      ),
//      List(apiTagAiAgent, apiTagSignal, apiTagSignalling, apiTagChannel))
//
//    lazy val getSignalChannelInfo: OBPEndpoint = {
//      case "signal" :: "channels" :: channelName :: "info" :: Nil JsonGet _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Helper.booleanToFuture(failMsg = InvalidSignalChannelName, cc = callContext) {
//              RedisMessaging.validateChannelName(channelName)
//            }
//            info <- Future {
//              RedisMessaging.channelInfo(channelName)
//            }
//            (count, ttl) <- info match {
//              case Some((c, t)) => Future.successful((c, t))
//              case None => Future.failed(new RuntimeException(s"Channel '$channelName' not found"))
//            }
//          } yield {
//            val response = SignalChannelInfoJsonV600(
//              channel_name = channelName,
//              message_count = count,
//              ttl_seconds = ttl
//            )
//            (response, HttpCode.`200`(callContext))
//          }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      deleteSignalChannel,
//      implementedInApiVersion,
//      nameOf(deleteSignalChannel),
//      "DELETE",
//      "/signal/channels/CHANNEL_NAME",
//      "Delete Signal Channel",
//      s"""Signal channels provide short-lived, Redis-backed messaging designed for AI agent discovery and coordination, but usable by any authenticated OBP consumer.
//         |Messages are ephemeral and will expire after the configured TTL (default 1 hour).
//         |
//         |This endpoint deletes a signal channel and all its messages immediately.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      signalChannelDeletedJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidSignalChannelName,
//        UnknownError
//      ),
//      List(apiTagAiAgent, apiTagSignal, apiTagSignalling, apiTagChannel))
//
//    staticResourceDocs += ResourceDoc(
//      getSignalStats,
//      implementedInApiVersion,
//      nameOf(getSignalStats),
//      "GET",
//      "/signal/channels/stats",
//      "Get Signal Channel Stats",
//      s"""Returns statistics for all signal channels, including private-only channels.
//         |
//         |Unlike the List Signal Channels endpoint, this does not filter out private-only channels.
//         |It provides a complete view of all active channels with message counts and TTL info.
//         |
//         |Authentication is Required.
//         |
//         |""".stripMargin,
//      EmptyBody,
//      signalStatsJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagAiAgent, apiTagSignal, apiTagSignalling, apiTagChannel),
//      Some(List(canGetSignalStats)))
//
//    lazy val getSignalStats: OBPEndpoint = {
//      case "signal" :: "channels" :: "stats" :: Nil JsonGet _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            channelNames <- Future {
//              RedisMessaging.listChannels()
//            }
//            channelsWithInfo <- Future.sequence(
//              channelNames.map { name =>
//                Future {
//                  RedisMessaging.channelInfo(name).map { case (count, ttl) =>
//                    SignalChannelInfoJsonV600(
//                      channel_name = name,
//                      message_count = count,
//                      ttl_seconds = ttl
//                    )
//                  }
//                }
//              }
//            )
//          } yield {
//            val channels = channelsWithInfo.flatten
//            val totalMessages = channels.map(_.message_count).sum
//            val response = SignalStatsJsonV600(
//              total_channels = channels.size,
//              total_messages = totalMessages,
//              channels = channels
//            )
//            (response, HttpCode.`200`(callContext))
//          }
//    }
//
//
//    lazy val deleteSignalChannel: OBPEndpoint = {
//      case "signal" :: "channels" :: channelName :: Nil JsonDelete _ =>
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Helper.booleanToFuture(failMsg = InvalidSignalChannelName, cc = callContext) {
//              RedisMessaging.validateChannelName(channelName)
//            }
//            deleted <- Future {
//              RedisMessaging.deleteChannel(channelName)
//            }
//          } yield {
//            val response = SignalChannelDeletedJsonV600(
//              channel_name = channelName,
//              deleted = deleted
//            )
//            (response, HttpCode.`200`(callContext))
//          }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAccountDirectory,
//      implementedInApiVersion,
//      nameOf(getAccountDirectory),
//      "GET",
//      "/banks/BANK_ID/account-directory",
//      "Get Account Directory at Bank",
//      s"""Returns a list of accounts at the bank with identifiers and metadata.
//         |
//         |This endpoint is designed for management UIs that need to list accounts
//         |without exposing sensitive data (balance and owners are excluded).
//         |
//         |The response includes: account_id, bank_id, label, account_number, account_type, branch_id,
//         |account_routings, account_attributes and view_ids.
//         |
//         |${urlParametersDocument(true, false)}
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      JSONFactory600.AccountDirectoryJsonV600(
//        accounts = List(JSONFactory600.AccountDirectoryItemJsonV600(
//          account_id = ExampleValue.accountIdExample.value,
//          bank_id = ExampleValue.bankIdExample.value,
//          label = "My Account",
//          account_number = "123456789",
//          account_type = "CURRENT",
//          branch_id = "BRANCH_1",
//          account_routings = List(FastFirehoseRoutings(bank_id = ExampleValue.bankIdExample.value, account_id = ExampleValue.accountIdExample.value)),
//          account_attributes = List(FastFirehoseAttributes(`type` = "STRING", code = "OVERDRAFT_LIMIT", value = "1000")),
//          view_ids = List("owner")
//        ))
//      ),
//      List(
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagAccount),
//      Some(List(canGetAccountDirectoryAtOneBank))
//    )
//
//    lazy val getAccountDirectory: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "account-directory" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.getBank(bankId, callContext)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetAccountDirectoryAtOneBank, callContext)
//            allowedParams = List("limit", "offset", "sort_direction")
//            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
//            (obpQueryParams, callContext) <- NewStyle.function.createObpParams(httpParams, allowedParams, callContext)
//            (accounts, callContext) <- NewStyle.function.getAccountDirectory(bankId, obpQueryParams, callContext)
//          } yield {
//            val viewsPerAccount: Map[BankIdAccountId, List[String]] = accounts.map { a =>
//              val bankIdAccountId = BankIdAccountId(BankId(a.bankId), AccountId(a.id))
//              val viewIds = Views.views.vend.availableViewsForAccount(bankIdAccountId).map(_.viewId.value)
//              bankIdAccountId -> viewIds
//            }.toMap
//            (JSONFactory600.createAccountDirectoryJsonV600(accounts, viewsPerAccount), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      hasAccountAccess,
//      implementedInApiVersion,
//      nameOf(hasAccountAccess),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/has-account-access",
//      "Has Account Access",
//      s"""Check whether the authenticated user has access to a specific view on a specific account.
//         |
//         |Returns a boolean `has_account_access` along with the `access_source` (currently "ACCOUNT_ACCESS")
//         |and the `account_access_id` (primary key of the AccountAccess record).
//         |
//         |If the user does not have access, `has_account_access` is false and the other fields are empty strings.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      JSONFactory600.HasAccountAccessJsonV600(
//        has_account_access = true,
//        access_source = "ACCOUNT_ACCESS",
//        account_access_id = ExampleValue.uuidExample.value,
//        abac_rule_id = ""
//      ),
//      List(
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagView, apiTagAccount)
//    )
//
//    lazy val hasAccountAccess: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: "has-account-access" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
//            bankIdAccountId = BankIdAccountId(bankId, accountId)
//            _ <- Future {
//              Views.views.vend.customViewFuture(viewId, bankIdAccountId).flatMap {
//                case Full(v) => Future.successful(Full(v))
//                case _ => Views.views.vend.systemViewFuture(viewId)
//              }
//            }.flatten.map {
//              unboxFullOrFail(_, callContext, s"$ViewNotFound Current ViewId is ${viewId.value}")
//            }
//            accountAccessBox <- Future {
//              AccountAccess.findByBankIdAccountIdViewIdUserPrimaryKey(
//                bankId, accountId, viewId, u.userPrimaryKey
//              )
//            }
//          } yield {
//            val response = accountAccessBox match {
//              case Full(aa) =>
//                JSONFactory600.HasAccountAccessJsonV600(
//                  has_account_access = true,
//                  access_source = "ACCOUNT_ACCESS",
//                  account_access_id = aa.id.get.toString,
//                  abac_rule_id = ""
//                )
//              case _ =>
//                JSONFactory600.HasAccountAccessJsonV600(
//                  has_account_access = false,
//                  access_source = "",
//                  account_access_id = "",
//                  abac_rule_id = ""
//                )
//            }
//            (response, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getUsersWithAccountAccess,
//      implementedInApiVersion,
//      nameOf(getUsersWithAccountAccess),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/users-with-access",
//      "Get Users With Account Access",
//      s"""Get all users who have access to a specific view on a specific account, and how that access was granted.
//         |
//         |This endpoint combines both traditional AccountAccess records and ABAC (Attribute-Based Access Control)
//         |evaluation to provide a complete picture of who can access the specified view.
//         |
//         |Each user entry includes an access_source indicating how access was granted
//         |(either "ACCOUNT_ACCESS" for direct grants or "ABAC" for rule-based access).
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      UsersWithViewAccessJsonV600(
//        users = List(UserWithViewAccessJsonV600(
//          user_id = ExampleValue.userIdExample.value,
//          username = "robert.x.smith.test",
//          email = "robert.x@example.com",
//          provider = "https://apisandbox.openbankproject.com",
//          access_source = "ACCOUNT_ACCESS"
//        ))
//      ),
//      List(
//        $BankNotFound,
//        BankAccountNotFound,
//        UnknownError
//      ),
//      List(apiTagAccount, apiTagView),
//      Some(List(canSeeAccountAccessForAnyUser))
//    )
//
//    lazy val getUsersWithAccountAccess: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: ViewId(viewId) :: "users-with-access" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
//            (_, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
//            bankIdAccountId = BankIdAccountId(bankId, accountId)
//
//            // Validate the view exists
//            _ <- Future {
//              Views.views.vend.customViewFuture(viewId, bankIdAccountId).flatMap {
//                case Full(v) => Future.successful(Full(v))
//                case _ => Views.views.vend.systemViewFuture(viewId)
//              }
//            }.flatten.map {
//              unboxFullOrFail(_, callContext, s"$ViewNotFound Current ViewId is ${viewId.value}")
//            }
//
//            // Step A: Get traditional AccountAccess users for this view
//            permissions <- Future(Views.views.vend.permissions(bankIdAccountId))
//            accountAccessUsers: List[UserWithViewAccessJsonV600] = permissions.flatMap { perm =>
//              if (perm.views.exists(_.viewId == viewId)) {
//                Some(UserWithViewAccessJsonV600(
//                  user_id = perm.user.userId,
//                  username = perm.user.name,
//                  email = perm.user.emailAddress,
//                  provider = perm.user.provider,
//                  access_source = "ACCOUNT_ACCESS"
//                ))
//              } else None
//            }
//            accountAccessUserIds = accountAccessUsers.map(_.user_id).toSet
//
//            // Step B: ABAC evaluation — always report ABAC access regardless of
//            // allow_abac_account_access prop. This endpoint reports the truth about
//            // who has access, it does not enforce access.
//            abacUsers <- {
//              // Find users with CanExecuteAbacRule entitlement
//              val abacEntitlements = Entitlement.entitlement.vend.getEntitlementsByRole(canExecuteAbacRule.toString)
//                .getOrElse(Nil)
//              val abacUserIds = abacEntitlements.map(_.userId).distinct
//                .filterNot(accountAccessUserIds.contains) // Skip users already covered by AccountAccess
//              logger.info(s"getUsersWithAccountAccess says: view=${viewId.value} abacUserIds to evaluate=$abacUserIds")
//
//              if (abacUserIds.isEmpty) {
//                logger.info("getUsersWithAccountAccess says: No ABAC users to evaluate")
//                Future.successful(List.empty[UserWithViewAccessJsonV600])
//              } else {
//                for {
//                  users <- Users.users.vend.getUsersByUserIdsFuture(abacUserIds)
//                  _ = logger.info(s"getUsersWithAccountAccess says: Resolved ${users.size} ABAC users: ${users.map(u => s"${u.userId}/${u.name}").mkString(", ")}")
//
//                  abacEvaluations <- Future.sequence(
//                    users.map { user =>
//                      callContext match {
//                        case Some(cc) =>
//                          logger.info(s"getUsersWithAccountAccess says: Evaluating user=${user.userId}/${user.name} view=${viewId.value} bank=${bankId.value} account=${accountId.value}")
//                          AbacRuleEngine.executeRulesByPolicyDetailed(
//                            policy = ABAC_POLICY_ACCOUNT_ACCESS,
//                            authenticatedUserId = user.userId,
//                            callContext = cc,
//                            bankId = Some(bankId.value),
//                            accountId = Some(accountId.value),
//                            viewId = Some(viewId.value)
//                          ).map { result =>
//                            logger.info(s"getUsersWithAccountAccess says: user=${user.userId}/${user.name} view=${viewId.value} result=$result")
//                            result match {
//                              case Full((true, _)) => Some(UserWithViewAccessJsonV600(
//                                user_id = user.userId,
//                                username = user.name,
//                                email = user.emailAddress,
//                                provider = user.provider,
//                                access_source = "ABAC"
//                              ))
//                              case _ => None
//                            }
//                          }.recover { case ex =>
//                            logger.error(s"getUsersWithAccountAccess says: user=${user.userId}/${user.name} view=${viewId.value} EXCEPTION: ${ex.getMessage}", ex)
//                            None
//                          }
//                        case None =>
//                          logger.warn("getUsersWithAccountAccess says: callContext is None, skipping ABAC evaluation")
//                          Future.successful(None)
//                      }
//                    }
//                  )
//                } yield abacEvaluations.flatten
//              }
//            }
//          } yield {
//            val response = UsersWithViewAccessJsonV600(
//              users = accountAccessUsers ++ abacUsers
//            )
//            (response, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getPrivateAccountByIdFull,
//      implementedInApiVersion,
//      nameOf(getPrivateAccountByIdFull),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
//      "Get Account by Id (Full)",
//      """Information returned about an account specified by ACCOUNT_ID as moderated by the view (VIEW_ID):
//        |
//        |* Number
//        |* Owners
//        |* Type
//        |* Balance
//        |* Available views (sorted by view_name)
//        |
//        |More details about the data moderation by the view [here](#1_2_1-getViewsForBankAccount).
//        |
//        |PSD2 Context: PSD2 requires customers to have access to their account information via third party applications.
//        |This call provides balance and other account information via delegated authentication using OAuth.
//        |
//        |Authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
//        |""".stripMargin,
//      EmptyBody,
//      ModeratedAccountJSON600(
//        id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
//        label = "NoneLabel",
//        number = "123",
//        owners = List(userJSONV121),
//        product_code = ExampleValue.productCodeExample.value,
//        balance = amountOfMoneyJsonV121,
//        views_available = List(ViewJsonV600(
//          bank_id = "",
//          account_id = "",
//          view_id = "owner",
//          view_name = "Owner",
//          description = "The owner of the account",
//          metadata_view = "owner",
//          is_public = false,
//          is_system = true,
//          is_firehose = Some(false),
//          alias = "private",
//          hide_metadata_if_alias_used = false,
//          can_grant_access_to_views = List("owner"),
//          can_revoke_access_to_views = List("owner"),
//          allowed_actions = List("can_see_transaction_amount", "can_see_bank_account_balance")
//        )),
//        bank_id = ExampleValue.bankIdExample.value,
//        account_routings = List(accountRoutingJsonV121),
//        account_attributes = List(accountAttributeResponseJson),
//        tags = List(accountTagJSON)
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        $BankAccountNotFound,
//        $UserNoPermissionAccessView,
//        UnknownError
//      ),
//      apiTagAccount :: Nil
//    )
//
//    lazy val getPrivateAccountByIdFull: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(
//            accountId
//          ) :: ViewId(viewId) :: "account" :: Nil JsonGet req => { cc =>
//        implicit val ec = EndpointContext(Some(cc))
//        for {
//          (user @ Full(u), _, account, view, callContext) <-
//            SS.userBankAccountView
//          moderatedAccount <- NewStyle.function.moderatedBankAccountCore(
//            account,
//            view,
//            user,
//            callContext
//          )
//          (accountAttributes, callContext) <- NewStyle.function
//            .getAccountAttributesByAccount(
//              bankId,
//              accountId,
//              callContext: Option[CallContext]
//            )
//        } yield {
//          val availableViews =
//            Views.views.vend.privateViewsUserCanAccessForAccount(
//              u,
//              BankIdAccountId(account.bankId, account.accountId)
//            )
//          val viewsAvailable =
//            availableViews.map(JSONFactory600.createViewJsonV600).sortBy(_.view_name)
//          val tags = Tags.tags.vend.getTagsOnAccount(bankId, accountId)(viewId)
//          (
//            createBankAccountJSON600(
//              moderatedAccount,
//              viewsAvailable,
//              accountAttributes,
//              tags
//            ),
//            HttpCode.`200`(callContext)
//          )
//        }
//      }
//    }
//
//
//    // ========== Mandate Endpoints ==========
//
//    staticResourceDocs += ResourceDoc(
//      createMandate,
//      implementedInApiVersion,
//      nameOf(createMandate),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates",
//      "Create Mandate",
//      s"""Create a new mandate for a bank account.
//         |
//         |A mandate is a legal document that defines who can operate an account, what they can do,
//         |and under what conditions (e.g., signatory requirements, amount thresholds).
//         |
//         |Mandates tie together OBP constructs such as Views, ABAC Rules, Signatory Panels,
//         |and Challenges into a coherent authorization policy.
//         |
//         |**Status values:** ACTIVE, SUSPENDED, EXPIRED, DRAFT
//         |
//         |**Date format:** yyyy-MM-dd'T'HH:mm:ss'Z' (UTC)
//         |
//         |Authentication is Required
//         |""",
//      CreateMandateJsonV600(
//        customer_id = "customer-id-123",
//        mandate_name = "ACME Corp Operating Account Authority",
//        mandate_reference = "MND-2026-00042",
//        legal_text = "The following persons are authorised to operate this account...",
//        description = "Payment and account access authority for ACME Corp",
//        status = "ACTIVE",
//        valid_from = "2026-01-01T00:00:00Z",
//        valid_to = "2027-01-01T00:00:00Z"
//      ),
//      MandateJsonV600(
//        mandate_id = "mandate-id-123",
//        bank_id = "gh.29.uk",
//        account_id = "8ca8a7e4-6d02",
//        customer_id = "customer-id-123",
//        mandate_name = "ACME Corp Operating Account Authority",
//        mandate_reference = "MND-2026-00042",
//        legal_text = "The following persons are authorised to operate this account...",
//        description = "Payment and account access authority for ACME Corp",
//        status = "ACTIVE",
//        valid_from = "2026-01-01T00:00:00Z",
//        valid_to = "2027-01-01T00:00:00Z",
//        created_by_user_id = "user-id-123",
//        updated_by_user_id = "user-id-123"
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canCreateMandate))
//    )
//
//    lazy val createMandate: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "mandates" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            createJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//              json.extract[CreateMandateJsonV600]
//            }
//            validFrom <- NewStyle.function.tryons(s"$InvalidDateFormat valid_from must be in yyyy-MM-dd'T'HH:mm:ss'Z' format", 400, cc.callContext) {
//              val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
//              formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
//              formatter.setLenient(false)
//              formatter.parse(createJson.valid_from)
//            }
//            validTo <- NewStyle.function.tryons(s"$InvalidDateFormat valid_to must be in yyyy-MM-dd'T'HH:mm:ss'Z' format", 400, cc.callContext) {
//              val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
//              formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
//              formatter.setLenient(false)
//              formatter.parse(createJson.valid_to)
//            }
//            (mandate, callContext) <- Connector.connector.vend.createMandate(
//              bankId,
//              accountId,
//              createJson.customer_id,
//              createJson.mandate_name,
//              createJson.mandate_reference,
//              createJson.legal_text,
//              createJson.description,
//              createJson.status,
//              validFrom,
//              validTo,
//              cc.userId,
//              cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not create mandate"), i._2)
//            }
//          } yield {
//            (createMandateJsonV600(mandate), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getMandates,
//      implementedInApiVersion,
//      nameOf(getMandates),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates",
//      "Get Mandates for Account",
//      s"""Get all mandates for a bank account.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      MandatesJsonV600(List(MandateJsonV600(
//        mandate_id = "mandate-id-123",
//        bank_id = "gh.29.uk",
//        account_id = "8ca8a7e4-6d02",
//        customer_id = "customer-id-123",
//        mandate_name = "ACME Corp Operating Account Authority",
//        mandate_reference = "MND-2026-00042",
//        legal_text = "The following persons are authorised...",
//        description = "Payment authority for ACME Corp",
//        status = "ACTIVE",
//        valid_from = "2026-01-01T00:00:00Z",
//        valid_to = "2027-01-01T00:00:00Z",
//        created_by_user_id = "user-id-123",
//        updated_by_user_id = "user-id-123"
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canGetMandate))
//    )
//
//    lazy val getMandates: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "mandates" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (mandates, callContext) <- Connector.connector.vend.getMandatesByBankAndAccount(
//              bankId, accountId, cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not get mandates"), i._2)
//            }
//          } yield {
//            (createMandatesJsonV600(mandates), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getMandate,
//      implementedInApiVersion,
//      nameOf(getMandate),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates/MANDATE_ID",
//      "Get Mandate",
//      s"""Get a mandate by its ID.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      MandateJsonV600(
//        mandate_id = "mandate-id-123",
//        bank_id = "gh.29.uk",
//        account_id = "8ca8a7e4-6d02",
//        customer_id = "customer-id-123",
//        mandate_name = "ACME Corp Operating Account Authority",
//        mandate_reference = "MND-2026-00042",
//        legal_text = "The following persons are authorised...",
//        description = "Payment authority for ACME Corp",
//        status = "ACTIVE",
//        valid_from = "2026-01-01T00:00:00Z",
//        valid_to = "2027-01-01T00:00:00Z",
//        created_by_user_id = "user-id-123",
//        updated_by_user_id = "user-id-123"
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canGetMandate))
//    )
//
//    lazy val getMandate: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "mandates" :: mandateId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (mandate, callContext) <- Connector.connector.vend.getMandateById(
//              mandateId, cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Mandate not found. Mandate ID: $mandateId", 404), i._2)
//            }
//          } yield {
//            (createMandateJsonV600(mandate), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateMandate,
//      implementedInApiVersion,
//      nameOf(updateMandate),
//      "PUT",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates/MANDATE_ID",
//      "Update Mandate",
//      s"""Update a mandate.
//         |
//         |Authentication is Required
//         |""",
//      UpdateMandateJsonV600(
//        mandate_name = "Updated Mandate Name",
//        mandate_reference = "MND-2026-00042",
//        legal_text = "Updated legal text...",
//        description = "Updated description",
//        status = "ACTIVE",
//        valid_from = "2026-01-01T00:00:00Z",
//        valid_to = "2027-01-01T00:00:00Z"
//      ),
//      MandateJsonV600(
//        mandate_id = "mandate-id-123",
//        bank_id = "gh.29.uk",
//        account_id = "8ca8a7e4-6d02",
//        customer_id = "customer-id-123",
//        mandate_name = "Updated Mandate Name",
//        mandate_reference = "MND-2026-00042",
//        legal_text = "Updated legal text...",
//        description = "Updated description",
//        status = "ACTIVE",
//        valid_from = "2026-01-01T00:00:00Z",
//        valid_to = "2027-01-01T00:00:00Z",
//        created_by_user_id = "user-id-123",
//        updated_by_user_id = "user-id-456"
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canUpdateMandate))
//    )
//
//    lazy val updateMandate: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "mandates" :: mandateId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            updateJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//              json.extract[UpdateMandateJsonV600]
//            }
//            validFrom <- NewStyle.function.tryons(s"$InvalidDateFormat valid_from must be in yyyy-MM-dd'T'HH:mm:ss'Z' format", 400, cc.callContext) {
//              val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
//              formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
//              formatter.setLenient(false)
//              formatter.parse(updateJson.valid_from)
//            }
//            validTo <- NewStyle.function.tryons(s"$InvalidDateFormat valid_to must be in yyyy-MM-dd'T'HH:mm:ss'Z' format", 400, cc.callContext) {
//              val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
//              formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
//              formatter.setLenient(false)
//              formatter.parse(updateJson.valid_to)
//            }
//            (mandate, callContext) <- Connector.connector.vend.updateMandate(
//              mandateId,
//              updateJson.mandate_name,
//              updateJson.mandate_reference,
//              updateJson.legal_text,
//              updateJson.description,
//              updateJson.status,
//              validFrom,
//              validTo,
//              cc.userId,
//              cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not update mandate. Mandate ID: $mandateId"), i._2)
//            }
//          } yield {
//            (createMandateJsonV600(mandate), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteMandate,
//      implementedInApiVersion,
//      nameOf(deleteMandate),
//      "DELETE",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/mandates/MANDATE_ID",
//      "Delete Mandate",
//      s"""Delete a mandate and all its provisions and signatory panels.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canDeleteMandate))
//    )
//
//    lazy val deleteMandate: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "mandates" :: mandateId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (deleted, callContext) <- Connector.connector.vend.deleteMandate(
//              mandateId, cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not delete mandate. Mandate ID: $mandateId"), i._2)
//            }
//          } yield {
//            (deleted, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // ========== Mandate Provision Endpoints ==========
//
//    staticResourceDocs += ResourceDoc(
//      createMandateProvision,
//      implementedInApiVersion,
//      nameOf(createMandateProvision),
//      "POST",
//      "/banks/BANK_ID/mandates/MANDATE_ID/provisions",
//      "Create Mandate Provision",
//      s"""Create a new provision for a mandate.
//         |
//         |A provision links the mandate's legal clauses to OBP enforcement mechanisms
//         |(Views, ABAC Rules, Challenges).
//         |
//         |**Provision types:**
//         |- SIGNATORY_RULE — Who can sign and in what combination
//         |- VIEW_ASSIGNMENT — Which view a signatory panel gets on the account
//         |- ABAC_CONDITION — Links to an ABAC rule for attribute-based conditions
//         |- RESTRICTION — Negative rule (e.g., no international payments)
//         |- NOTIFICATION — Triggers notification rather than blocking
//         |
//         |Authentication is Required
//         |""",
//      CreateMandateProvisionJsonV600(
//        provision_name = "Payments under 5000",
//        provision_description = "Any single Director may authorise payments below EUR 5,000",
//        legal_reference = "Clause 3.1(a)",
//        provision_type = "SIGNATORY_RULE",
//        conditions = """{"currency": "EUR", "amount_below": 5000.00}""",
//        signatory_requirements = List(SignatoryRequirementJsonV600(panel_id = "panel-id-001", required_count = 1)),
//        linked_view_id = Some("PaymentInitiator"),
//        linked_abac_rule_id = None,
//        linked_challenge_type = Some("OBP_TRANSACTION_REQUEST_CHALLENGE"),
//        is_active = true,
//        sort_order = 1
//      ),
//      MandateProvisionJsonV600(
//        provision_id = "provision-id-123",
//        mandate_id = "mandate-id-123",
//        provision_name = "Payments under 5000",
//        provision_description = "Any single Director may authorise payments below EUR 5,000",
//        legal_reference = "Clause 3.1(a)",
//        provision_type = "SIGNATORY_RULE",
//        conditions = """{"currency": "EUR", "amount_below": 5000.00}""",
//        signatory_requirements = List(SignatoryRequirementJsonV600(panel_id = "panel-id-001", required_count = 1)),
//        linked_view_id = "PaymentInitiator",
//        linked_abac_rule_id = "",
//        linked_challenge_type = "OBP_TRANSACTION_REQUEST_CHALLENGE",
//        is_active = true,
//        sort_order = 1
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canCreateMandateProvision))
//    )
//
//    lazy val createMandateProvision: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "provisions" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            createJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//              json.extract[CreateMandateProvisionJsonV600]
//            }
//            sigReqJson <- Future {
//              import org.json4s._
//              implicit val formats: Formats = DefaultFormats
//              org.json4s.native.Serialization.write(createJson.signatory_requirements)
//            }
//            (provision, callContext) <- Connector.connector.vend.createMandateProvision(
//              mandateId,
//              createJson.provision_name,
//              createJson.provision_description,
//              createJson.legal_reference,
//              createJson.provision_type,
//              createJson.conditions,
//              sigReqJson,
//              createJson.linked_view_id.getOrElse(""),
//              createJson.linked_abac_rule_id.getOrElse(""),
//              createJson.linked_challenge_type.getOrElse(""),
//              createJson.is_active,
//              createJson.sort_order,
//              cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not create mandate provision"), i._2)
//            }
//          } yield {
//            (createMandateProvisionJsonV600(provision), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getMandateProvisions,
//      implementedInApiVersion,
//      nameOf(getMandateProvisions),
//      "GET",
//      "/banks/BANK_ID/mandates/MANDATE_ID/provisions",
//      "Get Mandate Provisions",
//      s"""Get all provisions for a mandate.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      MandateProvisionsJsonV600(List(MandateProvisionJsonV600(
//        provision_id = "provision-id-123",
//        mandate_id = "mandate-id-123",
//        provision_name = "Payments under 5000",
//        provision_description = "Any single Director may authorise payments below EUR 5,000",
//        legal_reference = "Clause 3.1(a)",
//        provision_type = "SIGNATORY_RULE",
//        conditions = """{"currency": "EUR", "amount_below": 5000.00}""",
//        signatory_requirements = List(SignatoryRequirementJsonV600(panel_id = "panel-id-001", required_count = 1)),
//        linked_view_id = "PaymentInitiator",
//        linked_abac_rule_id = "",
//        linked_challenge_type = "OBP_TRANSACTION_REQUEST_CHALLENGE",
//        is_active = true,
//        sort_order = 1
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canGetMandateProvision))
//    )
//
//    lazy val getMandateProvisions: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "provisions" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (provisions, callContext) <- Connector.connector.vend.getMandateProvisionsByMandateId(
//              mandateId, cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not get provisions for mandate: $mandateId"), i._2)
//            }
//          } yield {
//            (createMandateProvisionsJsonV600(provisions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getMandateProvision,
//      implementedInApiVersion,
//      nameOf(getMandateProvision),
//      "GET",
//      "/banks/BANK_ID/mandates/MANDATE_ID/provisions/PROVISION_ID",
//      "Get Mandate Provision",
//      s"""Get a specific provision by its ID.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      MandateProvisionJsonV600(
//        provision_id = "provision-id-123",
//        mandate_id = "mandate-id-123",
//        provision_name = "Payments under 5000",
//        provision_description = "Any single Director may authorise payments below EUR 5,000",
//        legal_reference = "Clause 3.1(a)",
//        provision_type = "SIGNATORY_RULE",
//        conditions = """{"currency": "EUR", "amount_below": 5000.00}""",
//        signatory_requirements = List(SignatoryRequirementJsonV600(panel_id = "panel-id-001", required_count = 1)),
//        linked_view_id = "PaymentInitiator",
//        linked_abac_rule_id = "",
//        linked_challenge_type = "OBP_TRANSACTION_REQUEST_CHALLENGE",
//        is_active = true,
//        sort_order = 1
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canGetMandateProvision))
//    )
//
//    lazy val getMandateProvision: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "provisions" :: provisionId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (provision, callContext) <- Connector.connector.vend.getMandateProvisionById(
//              provisionId, cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Mandate provision not found. Provision ID: $provisionId", 404), i._2)
//            }
//          } yield {
//            (createMandateProvisionJsonV600(provision), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateMandateProvision,
//      implementedInApiVersion,
//      nameOf(updateMandateProvision),
//      "PUT",
//      "/banks/BANK_ID/mandates/MANDATE_ID/provisions/PROVISION_ID",
//      "Update Mandate Provision",
//      s"""Update a mandate provision.
//         |
//         |Authentication is Required
//         |""",
//      UpdateMandateProvisionJsonV600(
//        provision_name = "Updated provision",
//        provision_description = "Updated description",
//        legal_reference = "Clause 3.1(b)",
//        provision_type = "SIGNATORY_RULE",
//        conditions = """{"currency": "EUR", "amount_below": 50000.00}""",
//        signatory_requirements = List(SignatoryRequirementJsonV600(panel_id = "panel-id-001", required_count = 2)),
//        linked_view_id = Some("PaymentInitiator"),
//        linked_abac_rule_id = None,
//        linked_challenge_type = Some("OBP_TRANSACTION_REQUEST_CHALLENGE"),
//        is_active = true,
//        sort_order = 2
//      ),
//      MandateProvisionJsonV600(
//        provision_id = "provision-id-123",
//        mandate_id = "mandate-id-123",
//        provision_name = "Updated provision",
//        provision_description = "Updated description",
//        legal_reference = "Clause 3.1(b)",
//        provision_type = "SIGNATORY_RULE",
//        conditions = """{"currency": "EUR", "amount_below": 50000.00}""",
//        signatory_requirements = List(SignatoryRequirementJsonV600(panel_id = "panel-id-001", required_count = 2)),
//        linked_view_id = "PaymentInitiator",
//        linked_abac_rule_id = "",
//        linked_challenge_type = "OBP_TRANSACTION_REQUEST_CHALLENGE",
//        is_active = true,
//        sort_order = 2
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canUpdateMandateProvision))
//    )
//
//    lazy val updateMandateProvision: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "provisions" :: provisionId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            updateJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//              json.extract[UpdateMandateProvisionJsonV600]
//            }
//            sigReqJson <- Future {
//              import org.json4s._
//              implicit val formats: Formats = DefaultFormats
//              org.json4s.native.Serialization.write(updateJson.signatory_requirements)
//            }
//            (provision, callContext) <- Connector.connector.vend.updateMandateProvision(
//              provisionId,
//              updateJson.provision_name,
//              updateJson.provision_description,
//              updateJson.legal_reference,
//              updateJson.provision_type,
//              updateJson.conditions,
//              sigReqJson,
//              updateJson.linked_view_id.getOrElse(""),
//              updateJson.linked_abac_rule_id.getOrElse(""),
//              updateJson.linked_challenge_type.getOrElse(""),
//              updateJson.is_active,
//              updateJson.sort_order,
//              cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not update provision. Provision ID: $provisionId"), i._2)
//            }
//          } yield {
//            (createMandateProvisionJsonV600(provision), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteMandateProvision,
//      implementedInApiVersion,
//      nameOf(deleteMandateProvision),
//      "DELETE",
//      "/banks/BANK_ID/mandates/MANDATE_ID/provisions/PROVISION_ID",
//      "Delete Mandate Provision",
//      s"""Delete a mandate provision.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canDeleteMandateProvision))
//    )
//
//    lazy val deleteMandateProvision: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "provisions" :: provisionId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (deleted, callContext) <- Connector.connector.vend.deleteMandateProvision(
//              provisionId, cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not delete provision. Provision ID: $provisionId"), i._2)
//            }
//          } yield {
//            (deleted, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // ========== Signatory Panel Endpoints ==========
//
//    staticResourceDocs += ResourceDoc(
//      createSignatoryPanel,
//      implementedInApiVersion,
//      nameOf(createSignatoryPanel),
//      "POST",
//      "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels",
//      "Create Signatory Panel",
//      s"""Create a new signatory panel for a mandate.
//         |
//         |A signatory panel is a named set of authorised signatories (users) that can be
//         |referenced by mandate provisions. For example, "Panel A - Directors" and "Panel B - Finance".
//         |
//         |Provision rules then reference panels, e.g., "1 from Panel A and 1 from Panel B".
//         |
//         |Authentication is Required
//         |""",
//      CreateSignatoryPanelJsonV600(
//        panel_name = "Panel A - Directors",
//        description = "Board directors authorised to sign",
//        user_ids = List("user-id-1", "user-id-2", "user-id-3")
//      ),
//      SignatoryPanelJsonV600(
//        panel_id = "panel-id-001",
//        mandate_id = "mandate-id-123",
//        panel_name = "Panel A - Directors",
//        description = "Board directors authorised to sign",
//        user_ids = List("user-id-1", "user-id-2", "user-id-3")
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canCreateSignatoryPanel))
//    )
//
//    lazy val createSignatoryPanel: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "signatory-panels" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            createJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//              json.extract[CreateSignatoryPanelJsonV600]
//            }
//            userIdsStr = createJson.user_ids.mkString(",")
//            (panel, callContext) <- Connector.connector.vend.createSignatoryPanel(
//              mandateId,
//              createJson.panel_name,
//              createJson.description,
//              userIdsStr,
//              cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not create signatory panel"), i._2)
//            }
//          } yield {
//            (createSignatoryPanelJsonV600(panel), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getSignatoryPanels,
//      implementedInApiVersion,
//      nameOf(getSignatoryPanels),
//      "GET",
//      "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels",
//      "Get Signatory Panels",
//      s"""Get all signatory panels for a mandate.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      SignatoryPanelsJsonV600(List(SignatoryPanelJsonV600(
//        panel_id = "panel-id-001",
//        mandate_id = "mandate-id-123",
//        panel_name = "Panel A - Directors",
//        description = "Board directors authorised to sign",
//        user_ids = List("user-id-1", "user-id-2", "user-id-3")
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canGetSignatoryPanel))
//    )
//
//    lazy val getSignatoryPanels: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "signatory-panels" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (panels, callContext) <- Connector.connector.vend.getSignatoryPanelsByMandateId(
//              mandateId, cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not get signatory panels for mandate: $mandateId"), i._2)
//            }
//          } yield {
//            (createSignatoryPanelsJsonV600(panels), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getSignatoryPanel,
//      implementedInApiVersion,
//      nameOf(getSignatoryPanel),
//      "GET",
//      "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels/PANEL_ID",
//      "Get Signatory Panel",
//      s"""Get a specific signatory panel by its ID.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      SignatoryPanelJsonV600(
//        panel_id = "panel-id-001",
//        mandate_id = "mandate-id-123",
//        panel_name = "Panel A - Directors",
//        description = "Board directors authorised to sign",
//        user_ids = List("user-id-1", "user-id-2", "user-id-3")
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canGetSignatoryPanel))
//    )
//
//    lazy val getSignatoryPanel: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "signatory-panels" :: panelId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (panel, callContext) <- Connector.connector.vend.getSignatoryPanelById(
//              panelId, cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Signatory panel not found. Panel ID: $panelId", 404), i._2)
//            }
//          } yield {
//            (createSignatoryPanelJsonV600(panel), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateSignatoryPanel,
//      implementedInApiVersion,
//      nameOf(updateSignatoryPanel),
//      "PUT",
//      "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels/PANEL_ID",
//      "Update Signatory Panel",
//      s"""Update a signatory panel.
//         |
//         |Authentication is Required
//         |""",
//      UpdateSignatoryPanelJsonV600(
//        panel_name = "Panel A - Updated Directors",
//        description = "Updated board directors",
//        user_ids = List("user-id-1", "user-id-2", "user-id-4")
//      ),
//      SignatoryPanelJsonV600(
//        panel_id = "panel-id-001",
//        mandate_id = "mandate-id-123",
//        panel_name = "Panel A - Updated Directors",
//        description = "Updated board directors",
//        user_ids = List("user-id-1", "user-id-2", "user-id-4")
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canUpdateSignatoryPanel))
//    )
//
//    lazy val updateSignatoryPanel: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "signatory-panels" :: panelId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            updateJson <- NewStyle.function.tryons(s"$InvalidJsonFormat", 400, cc.callContext) {
//              json.extract[UpdateSignatoryPanelJsonV600]
//            }
//            userIdsStr = updateJson.user_ids.mkString(",")
//            (panel, callContext) <- Connector.connector.vend.updateSignatoryPanel(
//              panelId,
//              updateJson.panel_name,
//              updateJson.description,
//              userIdsStr,
//              cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not update signatory panel. Panel ID: $panelId"), i._2)
//            }
//          } yield {
//            (createSignatoryPanelJsonV600(panel), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteSignatoryPanel,
//      implementedInApiVersion,
//      nameOf(deleteSignatoryPanel),
//      "DELETE",
//      "/banks/BANK_ID/mandates/MANDATE_ID/signatory-panels/PANEL_ID",
//      "Delete Signatory Panel",
//      s"""Delete a signatory panel.
//         |
//         |Authentication is Required
//         |""",
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        $BankNotFound,
//        UnknownError
//      ),
//      List(apiTagMandate),
//      Some(List(canDeleteSignatoryPanel))
//    )
//
//    lazy val deleteSignatoryPanel: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "mandates" :: mandateId :: "signatory-panels" :: panelId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (deleted, callContext) <- Connector.connector.vend.deleteSignatoryPanel(
//              panelId, cc.callContext
//            ) map {
//              i => (unboxFullOrFail(i._1, cc.callContext, s"Could not delete signatory panel. Panel ID: $panelId"), i._2)
//            }
//          } yield {
//            (deleted, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createCounterpartyAttribute,
//      implementedInApiVersion,
//      nameOf(createCounterpartyAttribute),
//      "POST",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes",
//      "Create Counterparty Attribute",
//      s"""
//         | Create a new Counterparty Attribute for a given COUNTERPARTY_ID.
//         |
//         | The type field must be one of "STRING", "INTEGER", "DOUBLE" or "DATE_WITH_DAY".
//         | Authentication is Required
//         |
//     """.stripMargin,
//      counterpartyAttributeRequestJsonV600,
//      counterpartyAttributeResponseJsonV600,
//      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
//      List(apiTagCounterpartyAttribute, apiTagApi),
//      Some(List(canCreateCounterpartyAttribute))
//    )
//
//    lazy val createCounterpartyAttribute: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "counterparties" :: counterpartyId :: "attributes" :: Nil JsonPost json -> _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CounterpartyAttributeRequestJsonV600 ", 400, cc.callContext) {
//              json.extract[CounterpartyAttributeRequestJsonV600]
//            }
//            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
//              s"${CounterpartyAttributeType.DOUBLE}(12.1234), ${CounterpartyAttributeType.STRING}(TAX_NUMBER), ${CounterpartyAttributeType.INTEGER}(123) and ${CounterpartyAttributeType.DATE_WITH_DAY}(2012-04-23)"
//
//            counterpartyAttributeType <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
//              CounterpartyAttributeType.withName(postedData.attribute_type)
//            }
//
//            (attribute, callContext) <- CounterpartyAttributeNewStyle.createOrUpdateCounterpartyAttribute(
//              counterpartyId = CounterpartyId(counterpartyId),
//              counterpartyAttributeId = None,
//              name = postedData.name,
//              attributeType = counterpartyAttributeType,
//              value = postedData.value,
//              isActive = postedData.is_active,
//              callContext = cc.callContext
//            )
//          } yield {
//            (JSONFactory600.createCounterpartyAttributeJson(attribute), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteCounterpartyAttribute,
//      implementedInApiVersion,
//      nameOf(deleteCounterpartyAttribute),
//      "DELETE",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes/COUNTERPARTY_ATTRIBUTE_ID",
//      "Delete Counterparty Attribute",
//      s"""
//         | Delete a Counterparty Attribute specified by COUNTERPARTY_ATTRIBUTE_ID.
//         |
//         | Authentication is Required
//         |
//     """.stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List($AuthenticatedUserIsRequired, UnknownError),
//      List(apiTagCounterpartyAttribute, apiTagApi),
//      Some(List(canDeleteCounterpartyAttribute))
//    )
//
//    lazy val deleteCounterpartyAttribute: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "counterparties" :: counterpartyId :: "attributes" :: attributeId :: Nil JsonDelete _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (deleted, callContext) <- CounterpartyAttributeNewStyle.deleteCounterpartyAttribute(attributeId, cc.callContext)
//          } yield {
//            (Full(deleted), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCounterpartyAttributeById,
//      implementedInApiVersion,
//      nameOf(getCounterpartyAttributeById),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes/COUNTERPARTY_ATTRIBUTE_ID",
//      "Get Counterparty Attribute By ID",
//      s"""
//         | Get a specific Counterparty Attribute by its COUNTERPARTY_ATTRIBUTE_ID.
//         |
//         | Authentication is Required
//         |
//     """.stripMargin,
//      EmptyBody,
//      counterpartyAttributeResponseJsonV600,
//      List($AuthenticatedUserIsRequired, UnknownError),
//      List(apiTagCounterpartyAttribute, apiTagApi),
//      Some(List(canGetCounterpartyAttribute))
//    )
//
//    lazy val getCounterpartyAttributeById: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "counterparties" :: counterpartyId :: "attributes" :: attributeId :: Nil JsonGet _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (attribute, callContext) <- CounterpartyAttributeNewStyle.getCounterpartyAttributeById(attributeId, cc.callContext)
//          } yield {
//            (JSONFactory600.createCounterpartyAttributeJson(attribute), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getAllCounterpartyAttributes,
//      implementedInApiVersion,
//      nameOf(getAllCounterpartyAttributes),
//      "GET",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes",
//      "Get All Counterparty Attributes",
//      s"""
//         | Get all attributes for the specified Counterparty.
//         |
//         | Authentication is Required
//         |
//     """.stripMargin,
//      EmptyBody,
//      counterpartyAttributesJsonV600,
//      List($AuthenticatedUserIsRequired, UnknownError),
//      List(apiTagCounterpartyAttribute, apiTagApi),
//      Some(List(canGetCounterpartyAttributes))
//    )
//
//    lazy val getAllCounterpartyAttributes: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "counterparties" :: counterpartyId :: "attributes" :: Nil JsonGet _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            (attributes, callContext) <- CounterpartyAttributeNewStyle.getCounterpartyAttributes(CounterpartyId(counterpartyId), cc.callContext)
//          } yield {
//            (JSONFactory600.createCounterpartyAttributesJson(attributes), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateCounterpartyAttribute,
//      implementedInApiVersion,
//      nameOf(updateCounterpartyAttribute),
//      "PUT",
//      "/banks/BANK_ID/accounts/ACCOUNT_ID/counterparties/COUNTERPARTY_ID/attributes/COUNTERPARTY_ATTRIBUTE_ID",
//      "Update Counterparty Attribute",
//      s"""
//         | Update an existing Counterparty Attribute specified by COUNTERPARTY_ATTRIBUTE_ID.
//         |
//         | Authentication is Required
//         |
//     """.stripMargin,
//      counterpartyAttributeRequestJsonV600,
//      counterpartyAttributeResponseJsonV600,
//      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
//      List(apiTagCounterpartyAttribute, apiTagApi),
//      Some(List(canUpdateCounterpartyAttribute))
//    )
//
//    lazy val updateCounterpartyAttribute: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "counterparties" :: counterpartyId :: "attributes" :: attributeId :: Nil JsonPut json -> _ => {
//        cc =>
//          implicit val ec = EndpointContext(Some(cc))
//          for {
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CounterpartyAttributeRequestJsonV600 ", 400, cc.callContext) {
//              json.extract[CounterpartyAttributeRequestJsonV600]
//            }
//            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
//              s"${CounterpartyAttributeType.DOUBLE}(12.1234), ${CounterpartyAttributeType.STRING}(TAX_NUMBER), ${CounterpartyAttributeType.INTEGER}(123) and ${CounterpartyAttributeType.DATE_WITH_DAY}(2012-04-23)"
//
//            counterpartyAttributeType <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
//              CounterpartyAttributeType.withName(postedData.attribute_type)
//            }
//            (updatedAttribute, callContext) <- CounterpartyAttributeNewStyle.createOrUpdateCounterpartyAttribute(
//              counterpartyId = CounterpartyId(counterpartyId),
//              counterpartyAttributeId = Some(attributeId),
//              name = postedData.name,
//              attributeType = counterpartyAttributeType,
//              value = postedData.value,
//              isActive = postedData.is_active,
//              callContext = cc.callContext
//            )
//          } yield {
//            (JSONFactory600.createCounterpartyAttributeJson(updatedAttribute), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      createCustomerLink,
//      implementedInApiVersion,
//      nameOf(createCustomerLink),
//      "POST",
//      "/banks/BANK_ID/customer-links",
//      "Create Customer Link",
//      s"""Link a Customer to another Customer (e.g. spouse, parent, close_associate).
//         |
//         |Authentication is Required
//         |
//         |""",
//      postCustomerLinkJsonV600,
//      customerLinkJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        InvalidJsonFormat,
//        CustomerNotFoundByCustomerId,
//        UserHasMissingRoles,
//        CreateCustomerLinkError,
//        UnknownError
//      ),
//      List(apiTagCustomer),
//      Some(List(canCreateCustomerLink)))
//
//    lazy val createCustomerLink: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customer-links" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, _, callContext) <- SS.userBank
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCustomerLinkJsonV600 ", 400, callContext) {
//              json.extract[PostCustomerLinkJsonV600]
//            }
//            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(postedData.customer_id, callContext)
//            _ <- Helper.booleanToFuture(s"Bank of the customer specified by the CUSTOMER_ID(${customer.bankId}) has to match BANK_ID(${bankId.value}) in URL", 400, callContext) {
//              customer.bankId == bankId.value
//            }
//            (_, callContext) <- NewStyle.function.getBank(BankId(postedData.other_bank_id), callContext)
//            (otherCustomer, callContext) <- NewStyle.function.getCustomerByCustomerId(postedData.other_customer_id, callContext)
//            _ <- Helper.booleanToFuture(s"Bank of the other customer specified by the OTHER_CUSTOMER_ID(${otherCustomer.bankId}) has to match OTHER_BANK_ID(${postedData.other_bank_id})", 400, callContext) {
//              otherCustomer.bankId == postedData.other_bank_id
//            }
//            (customerLink, callContext) <- NewStyle.function.createCustomerLink(bankId.value, postedData.customer_id, postedData.other_bank_id, postedData.other_customer_id, postedData.relationship_to, callContext)
//          } yield {
//            (JSONFactory600.createCustomerLinkJson(customerLink), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomerLinksByCustomerId,
//      implementedInApiVersion,
//      nameOf(getCustomerLinksByCustomerId),
//      "GET",
//      "/banks/BANK_ID/customers/CUSTOMER_ID/customer-links",
//      "Get Customer Links by CUSTOMER_ID",
//      s"""Get Customer Links by CUSTOMER_ID.
//         |
//         |Authentication is Required
//         |
//         |""",
//      EmptyBody,
//      customerLinksJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        CustomerNotFoundByCustomerId,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagCustomer),
//      Some(List(canGetCustomerLinks)))
//
//    lazy val getCustomerLinksByCustomerId: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "customer-links" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, _, callContext) <- SS.userBank
//            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
//            (customerLinks, callContext) <- NewStyle.function.getCustomerLinksByCustomerId(customerId, callContext)
//          } yield {
//            (JSONFactory600.createCustomerLinksJson(customerLinks), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomerLinksByBankId,
//      implementedInApiVersion,
//      nameOf(getCustomerLinksByBankId),
//      "GET",
//      "/banks/BANK_ID/customer-links",
//      "Get Customer Links at Bank",
//      s"""Get all Customer Links at a Bank.
//         |
//         |Authentication is Required
//         |
//         |""",
//      EmptyBody,
//      customerLinksJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagCustomer),
//      Some(List(canGetCustomerLinks)))
//
//    lazy val getCustomerLinksByBankId: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customer-links" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, _, callContext) <- SS.userBank
//            (customerLinks, callContext) <- NewStyle.function.getCustomerLinksByBankId(bankId.value, callContext)
//          } yield {
//            (JSONFactory600.createCustomerLinksJson(customerLinks), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      getCustomerLinkById,
//      implementedInApiVersion,
//      nameOf(getCustomerLinkById),
//      "GET",
//      "/banks/BANK_ID/customer-links/CUSTOMER_LINK_ID",
//      "Get Customer Link by CUSTOMER_LINK_ID",
//      s"""Get Customer Link by CUSTOMER_LINK_ID.
//         |
//         |Authentication is Required
//         |
//         |""",
//      EmptyBody,
//      customerLinkJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        CustomerLinkNotFound,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagCustomer),
//      Some(List(canGetCustomerLink)))
//
//    lazy val getCustomerLinkById: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customer-links" :: customerLinkId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, _, callContext) <- SS.userBank
//            (customerLink, callContext) <- NewStyle.function.getCustomerLinkById(customerLinkId, callContext)
//          } yield {
//            (JSONFactory600.createCustomerLinkJson(customerLink), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      updateCustomerLink,
//      implementedInApiVersion,
//      nameOf(updateCustomerLink),
//      "PUT",
//      "/banks/BANK_ID/customer-links/CUSTOMER_LINK_ID",
//      "Update Customer Link",
//      s"""Update an existing Customer Link.
//         |
//         |Authentication is Required
//         |
//         |""",
//      putCustomerLinkJsonV600,
//      customerLinkJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        InvalidJsonFormat,
//        CustomerLinkNotFound,
//        UserHasMissingRoles,
//        UpdateCustomerLinkError,
//        UnknownError
//      ),
//      List(apiTagCustomer),
//      Some(List(canUpdateCustomerLink)))
//
//    lazy val updateCustomerLink: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customer-links" :: customerLinkId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, _, callContext) <- SS.userBank
//            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutCustomerLinkJsonV600 ", 400, callContext) {
//              json.extract[PutCustomerLinkJsonV600]
//            }
//            (customerLink, callContext) <- NewStyle.function.updateCustomerLinkById(customerLinkId, postedData.relationship_to, callContext)
//          } yield {
//            (JSONFactory600.createCustomerLinkJson(customerLink), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    staticResourceDocs += ResourceDoc(
//      deleteCustomerLink,
//      implementedInApiVersion,
//      nameOf(deleteCustomerLink),
//      "DELETE",
//      "/banks/BANK_ID/customer-links/CUSTOMER_LINK_ID",
//      "Delete Customer Link",
//      s"""Delete a Customer Link.
//         |
//         |Authentication is Required
//         |
//         |""",
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        CustomerLinkNotFound,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagCustomer),
//      Some(List(canDeleteCustomerLink)))
//
//    lazy val deleteCustomerLink: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customer-links" :: customerLinkId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, _, callContext) <- SS.userBank
//            (_, callContext) <- NewStyle.function.deleteCustomerLinkById(customerLinkId, callContext)
//          } yield {
//            (Full(true), HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//
//    staticResourceDocs += ResourceDoc(
//      getCustomerInvestigationReport,
//      implementedInApiVersion,
//      nameOf(getCustomerInvestigationReport),
//      "GET",
//      "/banks/BANK_ID/customers/CUSTOMER_ID/investigation-report",
//      "Get Customer Investigation Report",
//      s"""Get a Customer Investigation Report for fraud detection, AML (Anti-Money Laundering), and financial crime analysis.
//         |
//         |This endpoint assembles a comprehensive data package for a customer in a single API call,
//         |designed for use by AI agents, compliance officers, and financial crime investigators.
//         |
//         |**Use Cases:**
//         |
//         |* Fraud Detection - identify suspicious transaction patterns
//         |* AML / Anti-Money Laundering - trace fund flows and flag anomalies
//         |* KYC Enhanced Due Diligence - deep-dive into customer activity
//         |* Suspicious Activity Report (SAR) preparation
//         |* Financial crime investigation and evidence gathering
//         |
//         |**Data Returned:**
//         |
//         |* Customer details (legal name, KYC status)
//         |* All accounts linked to the customer (with balances)
//         |* Transaction history for those accounts (within the specified date range)
//         |* Related customers (via customer links) — spouses, associates, business partners
//         |
//         |**Suspicious Patterns This Data Supports Detecting:**
//         |
//         |* Money flowing through intermediary companies (A to B to C patterns)
//         |* Payments inconsistent with known income or salary
//         |* Transfers to related parties (spouses, associates) shortly after large inflows
//         |* Round-tripping — money returning to origin via indirect paths
//         |* Vague or generic transaction descriptions on large amounts
//         |* Structuring — multiple transactions just below reporting thresholds
//         |* Rapid movement of funds across accounts (layering)
//         |
//         |**Query Parameters:**
//         |
//         |* from_date: Start date for transactions (ISO format, e.g. $DateWithMsExampleString). Defaults to 1 year ago.
//         |* to_date: End date for transactions (ISO format, e.g. $DateWithMsExampleString). Defaults to now.
//         |* limit: Maximum number of transactions per account (default 500).
//         |
//         |**Note:** This endpoint is only available in mapped mode (connector=mapped).
//         |For other connector configurations, use the individual endpoints to retrieve
//         |customer, account, transaction, and customer link data separately.
//         |
//         |Authentication is Required
//         |
//         |""",
//      EmptyBody,
//      investigationReportJsonV600,
//      List(
//        $AuthenticatedUserIsRequired,
//        $BankNotFound,
//        CustomerNotFoundByCustomerId,
//        InvestigationReportNotAvailable,
//        UserHasMissingRoles,
//        UnknownError
//      ),
//      List(apiTagCustomer, apiTagKyc, apiTagTransaction, apiTagAccount, apiTagFinancialCrime, apiTagAiAgent),
//      Some(List(canGetInvestigationReport)))
//
//    lazy val getCustomerInvestigationReport: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "investigation-report" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (_, _, callContext) <- SS.userBank
//            // Check connector is mapped
//            connectorName = code.api.Constant.CONNECTOR.openOrThrowException("connector not set")
//            _ <- Helper.booleanToFuture(failMsg = InvestigationReportNotAvailable, cc = callContext) {
//              connectorName == "mapped"
//            }
//            // Validate customer exists
//            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
//            _ <- Helper.booleanToFuture(failMsg = s"Customer bank (${customer.bankId}) does not match BANK_ID (${bankId.value})", 400, callContext) {
//              customer.bankId == bankId.value
//            }
//            // Parse query params
//            fromDateStr = ObpS.param("from_date")
//            toDateStr = ObpS.param("to_date")
//            limitStr = ObpS.param("limit")
//            fromDate = fromDateStr.flatMap(d => APIUtil.parseDate(d)).getOrElse {
//              new java.util.Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000)
//            }
//            toDate = toDateStr.flatMap(d => APIUtil.parseDate(d)).getOrElse {
//              new java.util.Date()
//            }
//            limit = limitStr.flatMap(s => tryo(s.toInt)).getOrElse(500)
//            // Run Doobie queries
//            accounts <- Future {
//              code.investigation.DoobieInvestigationQueries.getAccountsForCustomer(customerId)
//            }
//            accountIds = accounts.map(_.accountId)
//            transactions <- Future {
//              code.investigation.DoobieInvestigationQueries.getTransactionsForAccounts(
//                accountIds, bankId.value,
//                new java.sql.Timestamp(fromDate.getTime),
//                new java.sql.Timestamp(toDate.getTime),
//                limit
//              )
//            }
//            customerLinks <- Future {
//              code.investigation.DoobieInvestigationQueries.getCustomerLinks(customerId)
//            }
//            customerRow = code.investigation.DoobieInvestigationQueries.CustomerRow(
//              customerId = customer.customerId,
//              legalName = customer.legalName,
//              email = customer.email,
//              mobileNumber = customer.mobileNumber,
//              kycStatus = customer.kycStatus
//            )
//          } yield {
//            (JSONFactory600.createInvestigationReportJson(
//              customerRow, bankId.value, accounts, transactions, customerLinks, fromDate, toDate
//            ), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // ============================================ CHAT / MESSAGING API ENDPOINTS ============================================
//
//    // ------ Batch A: Room CRUD ------
//
//    // 1a. createBankChatRoom
//    staticResourceDocs += ResourceDoc(
//      createBankChatRoom,
//      implementedInApiVersion,
//      nameOf(createBankChatRoom),
//      "POST",
//      "/banks/BANK_ID/chat-rooms",
//      "Create Bank Chat Room",
//      s"""Create a new chat room scoped to a bank.
//         |The creator is automatically added as a participant with all permissions.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostChatRoomJsonV600(name = "General Discussion", description = "A place to discuss general topics"),
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "gh.29.uk",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomAlreadyExists,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val createBankChatRoom: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostChatRoomJsonV600", 400, callContext) {
//              json.extract[PostChatRoomJsonV600]
//            }
//            existingRoom <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoomByBankIdAndName(bankId.value, postJson.name))
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomAlreadyExists, failCode = 409, cc = callContext) {
//              existingRoom.isEmpty
//            }
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.createChatRoom(bankId.value, postJson.name, postJson.description, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot create chat room", 400)
//            }
//            _ <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.addParticipant(room.chatRoomId, u.userId, "", code.chat.ChatPermissions.ALL_PERMISSIONS, "")
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot add creator as participant", 400)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(room, participantCount = computeParticipantCount(room.chatRoomId)), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 1b. createSystemChatRoom
//    staticResourceDocs += ResourceDoc(
//      createSystemChatRoom,
//      implementedInApiVersion,
//      nameOf(createSystemChatRoom),
//      "POST",
//      "/chat-rooms",
//      "Create System Chat Room",
//      s"""Create a new system-level chat room (not scoped to a bank).
//         |The creator is automatically added as a participant with all permissions.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostChatRoomJsonV600(name = "General Discussion", description = "A place to discuss general topics"),
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomAlreadyExists,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val createSystemChatRoom: OBPEndpoint = {
//      case "chat-rooms" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostChatRoomJsonV600", 400, callContext) {
//              json.extract[PostChatRoomJsonV600]
//            }
//            existingRoom <- Future(code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoomByBankIdAndName("", postJson.name))
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomAlreadyExists, failCode = 409, cc = callContext) {
//              existingRoom.isEmpty
//            }
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.createChatRoom("", postJson.name, postJson.description, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot create chat room", 400)
//            }
//            _ <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.addParticipant(room.chatRoomId, u.userId, "", code.chat.ChatPermissions.ALL_PERMISSIONS, "")
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot add creator as participant", 400)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(room, participantCount = computeParticipantCount(room.chatRoomId)), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 2a. getBankChatRooms
//    staticResourceDocs += ResourceDoc(
//      getBankChatRooms,
//      implementedInApiVersion,
//      nameOf(getBankChatRooms),
//      "GET",
//      "/banks/BANK_ID/chat-rooms",
//      "Get Bank Chat Rooms",
//      s"""Get all chat rooms for the specified bank that the current user is a participant of.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatRoomsJsonV600(chat_rooms = List(ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "gh.29.uk",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getBankChatRooms: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            rooms <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoomsByBankIdForUser(bankId.value, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get chat rooms", 400)
//            }
//            unreadCounts <- Future {
//              computeUnreadCounts(rooms, u.userId)
//            }
//            participantCounts <- Future {
//              computeParticipantCounts(rooms)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomsJson(rooms, unreadCounts, participantCounts), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 2b. getSystemChatRooms
//    staticResourceDocs += ResourceDoc(
//      getSystemChatRooms,
//      implementedInApiVersion,
//      nameOf(getSystemChatRooms),
//      "GET",
//      "/chat-rooms",
//      "Get System Chat Rooms",
//      s"""Get all system-level chat rooms that the current user is a participant of.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatRoomsJsonV600(chat_rooms = List(ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getSystemChatRooms: OBPEndpoint = {
//      case "chat-rooms" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            rooms <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoomsByBankIdForUser("", u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get chat rooms", 400)
//            }
//            unreadCounts <- Future {
//              computeUnreadCounts(rooms, u.userId)
//            }
//            participantCounts <- Future {
//              computeParticipantCounts(rooms)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomsJson(rooms, unreadCounts, participantCounts), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 3a. getBankChatRoom
//    staticResourceDocs += ResourceDoc(
//      getBankChatRoom,
//      implementedInApiVersion,
//      nameOf(getBankChatRoom),
//      "GET",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID",
//      "Get Bank Chat Room",
//      s"""Get a specific chat room by ID within a bank. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "gh.29.uk",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getBankChatRoom: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(room, participantCount = computeParticipantCount(room.chatRoomId)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 3b. getSystemChatRoom
//    staticResourceDocs += ResourceDoc(
//      getSystemChatRoom,
//      implementedInApiVersion,
//      nameOf(getSystemChatRoom),
//      "GET",
//      "/chat-rooms/CHAT_ROOM_ID",
//      "Get System Chat Room",
//      s"""Get a specific system-level chat room by ID. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getSystemChatRoom: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(room, participantCount = computeParticipantCount(room.chatRoomId)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 4a. updateBankChatRoom
//    staticResourceDocs += ResourceDoc(
//      updateBankChatRoom,
//      implementedInApiVersion,
//      nameOf(updateBankChatRoom),
//      "PUT",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID",
//      "Update Bank Chat Room",
//      s"""Update the name and/or description of a chat room. Requires can_update_room permission.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PutChatRoomJsonV600(name = Some("Updated Name"), description = Some("Updated description")),
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "gh.29.uk",
//        name = "Updated Name",
//        description = "Updated description",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        InsufficientChatPermission,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val updateBankChatRoom: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutChatRoomJsonV600", 400, callContext) {
//              json.extract[PutChatRoomJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_UPDATE_ROOM)
//            } map {
//              x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//            }
//            updatedRoom <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.updateChatRoom(chatRoomId, putJson.name, putJson.description)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot update chat room", 400)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(updatedRoom, participantCount = computeParticipantCount(updatedRoom.chatRoomId)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 4b. updateSystemChatRoom
//    staticResourceDocs += ResourceDoc(
//      updateSystemChatRoom,
//      implementedInApiVersion,
//      nameOf(updateSystemChatRoom),
//      "PUT",
//      "/chat-rooms/CHAT_ROOM_ID",
//      "Update System Chat Room",
//      s"""Update the name and/or description of a system-level chat room. Requires can_update_room permission.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PutChatRoomJsonV600(name = Some("Updated Name"), description = Some("Updated description")),
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "",
//        name = "Updated Name",
//        description = "Updated description",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        InsufficientChatPermission,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val updateSystemChatRoom: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutChatRoomJsonV600", 400, callContext) {
//              json.extract[PutChatRoomJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_UPDATE_ROOM)
//            } map {
//              x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//            }
//            updatedRoom <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.updateChatRoom(chatRoomId, putJson.name, putJson.description)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot update chat room", 400)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(updatedRoom, participantCount = computeParticipantCount(updatedRoom.chatRoomId)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 5a. deleteBankChatRoom
//    staticResourceDocs += ResourceDoc(
//      deleteBankChatRoom,
//      implementedInApiVersion,
//      nameOf(deleteBankChatRoom),
//      "DELETE",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID",
//      "Delete Bank Chat Room",
//      s"""Delete a chat room. Requires the CanDeleteBankChatRoom role.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ChatRoomNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      Some(List(canDeleteBankChatRoom))
//    )
//
//    lazy val deleteBankChatRoom: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canDeleteBankChatRoom, callContext)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.deleteChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot delete chat room", 400)
//            }
//          } yield {
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // 5b. deleteSystemChatRoom
//    staticResourceDocs += ResourceDoc(
//      deleteSystemChatRoom,
//      implementedInApiVersion,
//      nameOf(deleteSystemChatRoom),
//      "DELETE",
//      "/chat-rooms/CHAT_ROOM_ID",
//      "Delete System Chat Room",
//      s"""Delete a system-level chat room. Requires the CanDeleteSystemChatRoom role.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ChatRoomNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      Some(List(canDeleteSystemChatRoom))
//    )
//
//    lazy val deleteSystemChatRoom: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteSystemChatRoom, callContext)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.deleteChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot delete chat room", 400)
//            }
//          } yield {
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // 6a. archiveBankChatRoom
//    staticResourceDocs += ResourceDoc(
//      archiveBankChatRoom,
//      implementedInApiVersion,
//      nameOf(archiveBankChatRoom),
//      "PUT",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/archive-status",
//      "Archive Bank Chat Room",
//      s"""Archive a chat room. Archived rooms cannot receive new messages or participants.
//         |Requires the CanArchiveBankChatRoom role.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "gh.29.uk",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = true,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ChatRoomNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      Some(List(canArchiveBankChatRoom))
//    )
//
//    lazy val archiveBankChatRoom: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "archive-status" :: Nil JsonPut _ -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canArchiveBankChatRoom, callContext)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            archivedRoom <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.archiveChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot archive chat room", 400)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(archivedRoom, participantCount = computeParticipantCount(archivedRoom.chatRoomId)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 6b. archiveSystemChatRoom
//    staticResourceDocs += ResourceDoc(
//      archiveSystemChatRoom,
//      implementedInApiVersion,
//      nameOf(archiveSystemChatRoom),
//      "PUT",
//      "/chat-rooms/CHAT_ROOM_ID/archive-status",
//      "Archive System Chat Room",
//      s"""Archive a system-level chat room. Archived rooms cannot receive new messages or participants.
//         |Requires the CanArchiveSystemChatRoom role.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = true,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ChatRoomNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      Some(List(canArchiveSystemChatRoom))
//    )
//
//    lazy val archiveSystemChatRoom: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "archive-status" :: Nil JsonPut _ -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canArchiveSystemChatRoom, callContext)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            archivedRoom <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.archiveChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot archive chat room", 400)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(archivedRoom, participantCount = computeParticipantCount(archivedRoom.chatRoomId)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 6c. setBankChatRoomOpenRoom
//    staticResourceDocs += ResourceDoc(
//      setBankChatRoomOpenRoom,
//      implementedInApiVersion,
//      nameOf(setBankChatRoomOpenRoom),
//      "PUT",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/open-room",
//      "Set Chat Room All Users Are Participants",
//      s"""Set whether all authenticated users are implicit participants of this chat room.
//         |
//         |If true, all users can read and send messages without needing an explicit Participant record.
//         |
//         |Requires the CanSetBankChatRoomIsOpenRoom role.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "gh.29.uk",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "username",
//        created_by_provider = "provider",
//        is_open_room = true,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ChatRoomNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      Some(List(canSetBankChatRoomIsOpenRoom))
//    )
//
//    lazy val setBankChatRoomOpenRoom: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "open-room" :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canSetBankChatRoomIsOpenRoom, callContext)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            isOpenRoom = (json \ "is_open_room").extractOrElse[Boolean](false)
//            updatedRoom <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.setIsOpenRoom(chatRoomId, isOpenRoom)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot update chat room", 400)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(updatedRoom, participantCount = computeParticipantCount(updatedRoom.chatRoomId)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 6d. setSystemChatRoomOpenRoom
//    staticResourceDocs += ResourceDoc(
//      setSystemChatRoomOpenRoom,
//      implementedInApiVersion,
//      nameOf(setSystemChatRoomOpenRoom),
//      "PUT",
//      "/chat-rooms/CHAT_ROOM_ID/open-room",
//      "Set System Chat Room All Users Are Participants",
//      s"""Set whether all authenticated users are implicit participants of this system-level chat room.
//         |
//         |If true, all users can read and send messages without needing an explicit Participant record.
//         |
//         |Requires the CanSetSystemChatRoomIsOpenRoom role.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "username",
//        created_by_provider = "provider",
//        is_open_room = true,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        UserHasMissingRoles,
//        ChatRoomNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      Some(List(canSetSystemChatRoomIsOpenRoom))
//    )
//
//    lazy val setSystemChatRoomOpenRoom: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "open-room" :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- NewStyle.function.hasEntitlement("", u.userId, canSetSystemChatRoomIsOpenRoom, callContext)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            isOpenRoom = (json \ "is_open_room").extractOrElse[Boolean](false)
//            updatedRoom <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.setIsOpenRoom(chatRoomId, isOpenRoom)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot update chat room", 400)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomJson(updatedRoom, participantCount = computeParticipantCount(updatedRoom.chatRoomId)), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // ------ Batch B: Joining ------
//
//    // 7a. joinBankChatRoom
//    staticResourceDocs += ResourceDoc(
//      joinBankChatRoom,
//      implementedInApiVersion,
//      nameOf(joinBankChatRoom),
//      "POST",
//      "/banks/BANK_ID/chat-room-participants",
//      "Join Bank Chat Room",
//      s"""Join a chat room using a joining key (passed as joining_key in the JSON body).
//         |The user is added as a participant with no special permissions.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ParticipantJsonV600(
//        participant_id = "participant-id-123",
//        chat_room_id = "chat-room-id-123",
//        user_id = "user-id-123",
//        username = "robert.x.0.gh",
//        provider = "https://github.com",
//        consumer_id = "",
//        consumer_name = "",
//        permissions = List(),
//        webhook_url = "",
//        joined_at = new java.util.Date(),
//        last_read_at = new java.util.Date(),
//        is_muted = false
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJoiningKey,
//        ChatRoomIsArchived,
//        ChatRoomParticipantAlreadyExists,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val joinBankChatRoom: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-room-participants" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            joiningKey = (json \ "joining_key").extractOpt[String].getOrElse("")
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoomByJoiningKey(joiningKey)
//            } map {
//              x => unboxFullOrFail(x, callContext, InvalidJoiningKey, 404)
//            }
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomIsArchived, cc = callContext) {
//              !room.isArchived
//            }
//            existingParticipant <- Future(code.chat.ChatPermissions.isParticipant(room.chatRoomId, u.userId))
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomParticipantAlreadyExists, failCode = 409, cc = callContext) {
//              existingParticipant.isEmpty
//            }
//            participant <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.addParticipant(room.chatRoomId, u.userId, "", List.empty, "")
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot join chat room", 400)
//            }
//          } yield {
//            (JSONFactory600.createParticipantJson(participant), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 7b. joinSystemChatRoom
//    staticResourceDocs += ResourceDoc(
//      joinSystemChatRoom,
//      implementedInApiVersion,
//      nameOf(joinSystemChatRoom),
//      "POST",
//      "/chat-room-participants",
//      "Join System Chat Room",
//      s"""Join a system-level chat room using a joining key (passed as joining_key in the JSON body).
//         |The user is added as a participant with no special permissions.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ParticipantJsonV600(
//        participant_id = "participant-id-123",
//        chat_room_id = "chat-room-id-123",
//        user_id = "user-id-123",
//        username = "robert.x.0.gh",
//        provider = "https://github.com",
//        consumer_id = "",
//        consumer_name = "",
//        permissions = List(),
//        webhook_url = "",
//        joined_at = new java.util.Date(),
//        last_read_at = new java.util.Date(),
//        is_muted = false
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJoiningKey,
//        ChatRoomIsArchived,
//        ChatRoomParticipantAlreadyExists,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val joinSystemChatRoom: OBPEndpoint = {
//      case "chat-room-participants" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            joiningKey = (json \ "joining_key").extractOpt[String].getOrElse("")
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoomByJoiningKey(joiningKey)
//            } map {
//              x => unboxFullOrFail(x, callContext, InvalidJoiningKey, 404)
//            }
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomIsArchived, cc = callContext) {
//              !room.isArchived
//            }
//            existingParticipant <- Future(code.chat.ChatPermissions.isParticipant(room.chatRoomId, u.userId))
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomParticipantAlreadyExists, failCode = 409, cc = callContext) {
//              existingParticipant.isEmpty
//            }
//            participant <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.addParticipant(room.chatRoomId, u.userId, "", List.empty, "")
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot join chat room", 400)
//            }
//          } yield {
//            (JSONFactory600.createParticipantJson(participant), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 8a. refreshBankJoiningKey
//    staticResourceDocs += ResourceDoc(
//      refreshBankJoiningKey,
//      implementedInApiVersion,
//      nameOf(refreshBankJoiningKey),
//      "PUT",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/joining-key",
//      "Refresh Bank Chat Room Joining Key",
//      s"""Refresh the joining key for a chat room. The old key becomes invalid.
//         |Requires can_refresh_joining_key permission.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      JoiningKeyJsonV600(joining_key = "new-key-abc123"),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        InsufficientChatPermission,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val refreshBankJoiningKey: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "joining-key" :: Nil JsonPut _ -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_REFRESH_JOINING_KEY)
//            } map {
//              x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//            }
//            updatedRoom <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.refreshJoiningKey(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot refresh joining key", 400)
//            }
//          } yield {
//            (JoiningKeyJsonV600(joining_key = updatedRoom.joiningKey), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 8b. refreshSystemJoiningKey
//    staticResourceDocs += ResourceDoc(
//      refreshSystemJoiningKey,
//      implementedInApiVersion,
//      nameOf(refreshSystemJoiningKey),
//      "PUT",
//      "/chat-rooms/CHAT_ROOM_ID/joining-key",
//      "Refresh System Chat Room Joining Key",
//      s"""Refresh the joining key for a system-level chat room. The old key becomes invalid.
//         |Requires can_refresh_joining_key permission.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      JoiningKeyJsonV600(joining_key = "new-key-abc123"),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        InsufficientChatPermission,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val refreshSystemJoiningKey: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "joining-key" :: Nil JsonPut _ -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_REFRESH_JOINING_KEY)
//            } map {
//              x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//            }
//            updatedRoom <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.refreshJoiningKey(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot refresh joining key", 400)
//            }
//          } yield {
//            (JoiningKeyJsonV600(joining_key = updatedRoom.joiningKey), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // ------ Batch C: Participants ------
//
//    // 9a. addBankChatRoomParticipant
//    staticResourceDocs += ResourceDoc(
//      addBankChatRoomParticipant,
//      implementedInApiVersion,
//      nameOf(addBankChatRoomParticipant),
//      "POST",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/participants",
//      "Add Bank Chat Room Participant",
//      s"""Add a participant to a chat room. Requires can_manage_permissions permission.
//         |Specify either user_id or consumer_id, but not both.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostParticipantJsonV600(user_id = Some("user-id-456"), consumer_id = None, permissions = Some(List("can_delete_message")), webhook_url = None),
//      ParticipantJsonV600(
//        participant_id = "participant-id-456",
//        chat_room_id = "chat-room-id-123",
//        user_id = "user-id-456",
//        username = "ellie.y.1.gh",
//        provider = "https://github.com",
//        consumer_id = "",
//        consumer_name = "",
//        permissions = List("can_delete_message"),
//        webhook_url = "",
//        joined_at = new java.util.Date(),
//        last_read_at = new java.util.Date(),
//        is_muted = false
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        InsufficientChatPermission,
//        MustSpecifyUserIdOrConsumerId,
//        ChatRoomParticipantAlreadyExists,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val addBankChatRoomParticipant: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "participants" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostParticipantJsonV600", 400, callContext) {
//              json.extract[PostParticipantJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_MANAGE_PERMISSIONS)
//            } map {
//              x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//            }
//            userId = postJson.user_id.getOrElse("")
//            consumerId = postJson.consumer_id.getOrElse("")
//            _ <- Helper.booleanToFuture(failMsg = MustSpecifyUserIdOrConsumerId, cc = callContext) {
//              (userId.nonEmpty || consumerId.nonEmpty) && !(userId.nonEmpty && consumerId.nonEmpty)
//            }
//            existingParticipant <- Future {
//              if (userId.nonEmpty) code.chat.ChatPermissions.isParticipant(chatRoomId, userId)
//              else code.chat.ChatPermissions.isParticipantByConsumerId(chatRoomId, consumerId)
//            }
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomParticipantAlreadyExists, failCode = 409, cc = callContext) {
//              existingParticipant.isEmpty
//            }
//            participant <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.addParticipant(
//                chatRoomId, userId, consumerId,
//                postJson.permissions.getOrElse(List.empty),
//                postJson.webhook_url.getOrElse("")
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot add participant", 400)
//            }
//          } yield {
//            (JSONFactory600.createParticipantJson(participant), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 9b. addSystemChatRoomParticipant
//    staticResourceDocs += ResourceDoc(
//      addSystemChatRoomParticipant,
//      implementedInApiVersion,
//      nameOf(addSystemChatRoomParticipant),
//      "POST",
//      "/chat-rooms/CHAT_ROOM_ID/participants",
//      "Add System Chat Room Participant",
//      s"""Add a participant to a system-level chat room. Requires can_manage_permissions permission.
//         |Specify either user_id or consumer_id, but not both.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostParticipantJsonV600(user_id = Some("user-id-456"), consumer_id = None, permissions = Some(List("can_delete_message")), webhook_url = None),
//      ParticipantJsonV600(
//        participant_id = "participant-id-456",
//        chat_room_id = "chat-room-id-123",
//        user_id = "user-id-456",
//        username = "ellie.y.1.gh",
//        provider = "https://github.com",
//        consumer_id = "",
//        consumer_name = "",
//        permissions = List("can_delete_message"),
//        webhook_url = "",
//        joined_at = new java.util.Date(),
//        last_read_at = new java.util.Date(),
//        is_muted = false
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        InsufficientChatPermission,
//        MustSpecifyUserIdOrConsumerId,
//        ChatRoomParticipantAlreadyExists,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val addSystemChatRoomParticipant: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "participants" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostParticipantJsonV600", 400, callContext) {
//              json.extract[PostParticipantJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_MANAGE_PERMISSIONS)
//            } map {
//              x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//            }
//            userId = postJson.user_id.getOrElse("")
//            consumerId = postJson.consumer_id.getOrElse("")
//            _ <- Helper.booleanToFuture(failMsg = MustSpecifyUserIdOrConsumerId, cc = callContext) {
//              (userId.nonEmpty || consumerId.nonEmpty) && !(userId.nonEmpty && consumerId.nonEmpty)
//            }
//            existingParticipant <- Future {
//              if (userId.nonEmpty) code.chat.ChatPermissions.isParticipant(chatRoomId, userId)
//              else code.chat.ChatPermissions.isParticipantByConsumerId(chatRoomId, consumerId)
//            }
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomParticipantAlreadyExists, failCode = 409, cc = callContext) {
//              existingParticipant.isEmpty
//            }
//            participant <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.addParticipant(
//                chatRoomId, userId, consumerId,
//                postJson.permissions.getOrElse(List.empty),
//                postJson.webhook_url.getOrElse("")
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot add participant", 400)
//            }
//          } yield {
//            (JSONFactory600.createParticipantJson(participant), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 10a. getBankChatRoomParticipants
//    staticResourceDocs += ResourceDoc(
//      getBankChatRoomParticipants,
//      implementedInApiVersion,
//      nameOf(getBankChatRoomParticipants),
//      "GET",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/participants",
//      "Get Bank Chat Room Participants",
//      s"""Get all participants of a chat room. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ParticipantsJsonV600(participants = List(ParticipantJsonV600(
//        participant_id = "participant-id-123",
//        chat_room_id = "chat-room-id-123",
//        user_id = "user-id-123",
//        username = "robert.x.0.gh",
//        provider = "https://github.com",
//        consumer_id = "",
//        consumer_name = "",
//        permissions = List("can_update_room", "can_delete_message"),
//        webhook_url = "",
//        joined_at = new java.util.Date(),
//        last_read_at = new java.util.Date(),
//        is_muted = false
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getBankChatRoomParticipants: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "participants" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            participants <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.getParticipants(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get participants", 400)
//            }
//          } yield {
//            (JSONFactory600.createParticipantsJson(participants), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 10b. getSystemChatRoomParticipants
//    staticResourceDocs += ResourceDoc(
//      getSystemChatRoomParticipants,
//      implementedInApiVersion,
//      nameOf(getSystemChatRoomParticipants),
//      "GET",
//      "/chat-rooms/CHAT_ROOM_ID/participants",
//      "Get System Chat Room Participants",
//      s"""Get all participants of a system-level chat room. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ParticipantsJsonV600(participants = List(ParticipantJsonV600(
//        participant_id = "participant-id-123",
//        chat_room_id = "chat-room-id-123",
//        user_id = "user-id-123",
//        username = "robert.x.0.gh",
//        provider = "https://github.com",
//        consumer_id = "",
//        consumer_name = "",
//        permissions = List("can_update_room", "can_delete_message"),
//        webhook_url = "",
//        joined_at = new java.util.Date(),
//        last_read_at = new java.util.Date(),
//        is_muted = false
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getSystemChatRoomParticipants: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "participants" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            participants <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.getParticipants(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get participants", 400)
//            }
//          } yield {
//            (JSONFactory600.createParticipantsJson(participants), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 11a. updateBankParticipantPermissions
//    staticResourceDocs += ResourceDoc(
//      updateBankParticipantPermissions,
//      implementedInApiVersion,
//      nameOf(updateBankParticipantPermissions),
//      "PUT",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/participants/USER_ID",
//      "Update Bank Chat Room Participant Permissions",
//      s"""Update the permissions of a participant. Requires can_manage_permissions permission.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PutParticipantPermissionsJsonV600(permissions = List("can_delete_message", "can_update_room")),
//      ParticipantJsonV600(
//        participant_id = "participant-id-456",
//        chat_room_id = "chat-room-id-123",
//        user_id = "user-id-456",
//        username = "ellie.y.1.gh",
//        provider = "https://github.com",
//        consumer_id = "",
//        consumer_name = "",
//        permissions = List("can_delete_message", "can_update_room"),
//        webhook_url = "",
//        joined_at = new java.util.Date(),
//        last_read_at = new java.util.Date(),
//        is_muted = false
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        InsufficientChatPermission,
//        ChatRoomParticipantNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val updateBankParticipantPermissions: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "participants" :: targetUserId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutParticipantPermissionsJsonV600", 400, callContext) {
//              json.extract[PutParticipantPermissionsJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_MANAGE_PERMISSIONS)
//            } map {
//              x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, targetUserId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomParticipantNotFound, 404)
//            }
//            updatedParticipant <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.updateParticipantPermissions(chatRoomId, targetUserId, putJson.permissions)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot update participant permissions", 400)
//            }
//          } yield {
//            (JSONFactory600.createParticipantJson(updatedParticipant), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 11b. updateSystemParticipantPermissions
//    staticResourceDocs += ResourceDoc(
//      updateSystemParticipantPermissions,
//      implementedInApiVersion,
//      nameOf(updateSystemParticipantPermissions),
//      "PUT",
//      "/chat-rooms/CHAT_ROOM_ID/participants/USER_ID",
//      "Update System Chat Room Participant Permissions",
//      s"""Update the permissions of a participant in a system-level chat room. Requires can_manage_permissions permission.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PutParticipantPermissionsJsonV600(permissions = List("can_delete_message", "can_update_room")),
//      ParticipantJsonV600(
//        participant_id = "participant-id-456",
//        chat_room_id = "chat-room-id-123",
//        user_id = "user-id-456",
//        username = "ellie.y.1.gh",
//        provider = "https://github.com",
//        consumer_id = "",
//        consumer_name = "",
//        permissions = List("can_delete_message", "can_update_room"),
//        webhook_url = "",
//        joined_at = new java.util.Date(),
//        last_read_at = new java.util.Date(),
//        is_muted = false
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        InsufficientChatPermission,
//        ChatRoomParticipantNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val updateSystemParticipantPermissions: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "participants" :: targetUserId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutParticipantPermissionsJsonV600", 400, callContext) {
//              json.extract[PutParticipantPermissionsJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_MANAGE_PERMISSIONS)
//            } map {
//              x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, targetUserId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomParticipantNotFound, 404)
//            }
//            updatedParticipant <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.updateParticipantPermissions(chatRoomId, targetUserId, putJson.permissions)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot update participant permissions", 400)
//            }
//          } yield {
//            (JSONFactory600.createParticipantJson(updatedParticipant), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 12a. removeBankChatRoomParticipant
//    staticResourceDocs += ResourceDoc(
//      removeBankChatRoomParticipant,
//      implementedInApiVersion,
//      nameOf(removeBankChatRoomParticipant),
//      "DELETE",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/participants/USER_ID",
//      "Remove Bank Chat Room Participant",
//      s"""Remove a participant from a chat room. Requires can_remove_participant permission, or the user can remove themselves.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        InsufficientChatPermission,
//        ChatRoomParticipantNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val removeBankChatRoomParticipant: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "participants" :: targetUserId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            // Self-removal is allowed; otherwise need can_remove_participant
//            _ <- if (u.userId == targetUserId) {
//              Future.successful(Full(()))
//            } else {
//              Future {
//                code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_REMOVE_PARTICIPANT)
//              } map {
//                x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//              }
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, targetUserId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomParticipantNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.removeParticipant(chatRoomId, targetUserId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot remove participant", 400)
//            }
//          } yield {
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // 12b. removeSystemChatRoomParticipant
//    staticResourceDocs += ResourceDoc(
//      removeSystemChatRoomParticipant,
//      implementedInApiVersion,
//      nameOf(removeSystemChatRoomParticipant),
//      "DELETE",
//      "/chat-rooms/CHAT_ROOM_ID/participants/USER_ID",
//      "Remove System Chat Room Participant",
//      s"""Remove a participant from a system-level chat room. Requires can_remove_participant permission, or the user can remove themselves.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        InsufficientChatPermission,
//        ChatRoomParticipantNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val removeSystemChatRoomParticipant: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "participants" :: targetUserId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- if (u.userId == targetUserId) {
//              Future.successful(Full(()))
//            } else {
//              Future {
//                code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_REMOVE_PARTICIPANT)
//              } map {
//                x => unboxFullOrFail(x, callContext, InsufficientChatPermission, 403)
//              }
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, targetUserId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomParticipantNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.removeParticipant(chatRoomId, targetUserId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot remove participant", 400)
//            }
//          } yield {
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // ------ Batch D: Messages ------
//
//    // 13a. sendBankChatMessage
//    staticResourceDocs += ResourceDoc(
//      sendBankChatMessage,
//      implementedInApiVersion,
//      nameOf(sendBankChatMessage),
//      "POST",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages",
//      "Send Bank Chat Message",
//      s"""Send a message in a chat room. The current user must be a participant and the room must not be archived.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostChatMessageJsonV600(content = "Hello everyone!", message_type = Some("text"), mentioned_user_ids = None, reply_to_message_id = None, thread_id = None),
//      ChatMessageJsonV600(
//        chat_message_id = "msg-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "Hello everyone!",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatRoomIsArchived,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val sendBankChatMessage: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostChatMessageJsonV600", 400, callContext) {
//              json.extract[PostChatMessageJsonV600]
//            }
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomIsArchived, cc = callContext) {
//              !room.isArchived
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.createMessage(
//                chatRoomId,
//                u.userId,
//                "",
//                postJson.content,
//                postJson.message_type.getOrElse("text"),
//                postJson.mentioned_user_ids.getOrElse(List.empty),
//                postJson.reply_to_message_id.getOrElse(""),
//                postJson.thread_id.getOrElse("")
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot send message", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterCreate(msg, u.name, u.provider, "")
//            (JSONFactory600.createChatMessageJson(msg, List.empty), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 13b. sendSystemChatMessage
//    staticResourceDocs += ResourceDoc(
//      sendSystemChatMessage,
//      implementedInApiVersion,
//      nameOf(sendSystemChatMessage),
//      "POST",
//      "/chat-rooms/CHAT_ROOM_ID/messages",
//      "Send System Chat Message",
//      s"""Send a message in a system-level chat room. The current user must be a participant and the room must not be archived.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostChatMessageJsonV600(content = "Hello everyone!", message_type = Some("text"), mentioned_user_ids = None, reply_to_message_id = None, thread_id = None),
//      ChatMessageJsonV600(
//        chat_message_id = "msg-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "Hello everyone!",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatRoomIsArchived,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val sendSystemChatMessage: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostChatMessageJsonV600", 400, callContext) {
//              json.extract[PostChatMessageJsonV600]
//            }
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomIsArchived, cc = callContext) {
//              !room.isArchived
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.createMessage(
//                chatRoomId,
//                u.userId,
//                "",
//                postJson.content,
//                postJson.message_type.getOrElse("text"),
//                postJson.mentioned_user_ids.getOrElse(List.empty),
//                postJson.reply_to_message_id.getOrElse(""),
//                postJson.thread_id.getOrElse("")
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot send message", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterCreate(msg, u.name, u.provider, "")
//            (JSONFactory600.createChatMessageJson(msg, List.empty), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 14a. getBankChatMessages
//    staticResourceDocs += ResourceDoc(
//      getBankChatMessages,
//      implementedInApiVersion,
//      nameOf(getBankChatMessages),
//      "GET",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages",
//      "Get Bank Chat Messages",
//      s"""Get messages in a chat room.
//         |
//         |${getObpApiRoot}/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages?limit=50&offset=0&from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString
//         |
//         |The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatMessagesJsonV600(messages = List(ChatMessageJsonV600(
//        chat_message_id = "msg-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "Hello everyone!",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List(ReactionSummaryJsonV600(emoji = "thumbsup", count = 2, user_ids = List("user-1", "user-2")))
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getBankChatMessages: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            limitParam = ObpS.param("limit").map(_.toInt).getOrElse(50)
//            offsetParam = ObpS.param("offset").map(_.toInt).getOrElse(0)
//            fromDate = ObpS.param("from_date").flatMap(parseObpStandardDate(_).toOption).getOrElse(theEpochTime)
//            toDate = ObpS.param("to_date").flatMap(parseObpStandardDate(_).toOption).getOrElse(APIUtil.DefaultToDate)
//            (messageRows, reactionRows) = code.chat.DoobieChatMessageQueries.getMessagesWithReactions(chatRoomId, fromDate, toDate, limitParam, offsetParam)
//          } yield {
//            (JSONFactory600.createChatMessagesJsonFromRows(messageRows, reactionRows), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 14b. getSystemChatMessages
//    staticResourceDocs += ResourceDoc(
//      getSystemChatMessages,
//      implementedInApiVersion,
//      nameOf(getSystemChatMessages),
//      "GET",
//      "/chat-rooms/CHAT_ROOM_ID/messages",
//      "Get System Chat Messages",
//      s"""Get messages in a system-level chat room.
//         |
//         |${getObpApiRoot}/chat-rooms/CHAT_ROOM_ID/messages?limit=50&offset=0&from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString
//         |
//         |The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatMessagesJsonV600(messages = List(ChatMessageJsonV600(
//        chat_message_id = "msg-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "Hello everyone!",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List(ReactionSummaryJsonV600(emoji = "thumbsup", count = 2, user_ids = List("user-1", "user-2")))
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getSystemChatMessages: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            limitParam = ObpS.param("limit").map(_.toInt).getOrElse(50)
//            offsetParam = ObpS.param("offset").map(_.toInt).getOrElse(0)
//            fromDate = ObpS.param("from_date").flatMap(parseObpStandardDate(_).toOption).getOrElse(theEpochTime)
//            toDate = ObpS.param("to_date").flatMap(parseObpStandardDate(_).toOption).getOrElse(APIUtil.DefaultToDate)
//            (messageRows, reactionRows) = code.chat.DoobieChatMessageQueries.getMessagesWithReactions(chatRoomId, fromDate, toDate, limitParam, offsetParam)
//          } yield {
//            (JSONFactory600.createChatMessagesJsonFromRows(messageRows, reactionRows), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 15a. getBankChatMessage
//    staticResourceDocs += ResourceDoc(
//      getBankChatMessage,
//      implementedInApiVersion,
//      nameOf(getBankChatMessage),
//      "GET",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID",
//      "Get Bank Chat Message",
//      s"""Get a specific message by ID. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatMessageJsonV600(
//        chat_message_id = "msg-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "Hello everyone!",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getBankChatMessage: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            reactions <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId).openOr(List.empty)
//            }
//          } yield {
//            (JSONFactory600.createChatMessageJson(msg, reactions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 15b. getSystemChatMessage
//    staticResourceDocs += ResourceDoc(
//      getSystemChatMessage,
//      implementedInApiVersion,
//      nameOf(getSystemChatMessage),
//      "GET",
//      "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID",
//      "Get System Chat Message",
//      s"""Get a specific message by ID in a system-level chat room. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatMessageJsonV600(
//        chat_message_id = "msg-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "Hello everyone!",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getSystemChatMessage: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            reactions <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId).openOr(List.empty)
//            }
//          } yield {
//            (JSONFactory600.createChatMessageJson(msg, reactions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 16a. editBankChatMessage
//    staticResourceDocs += ResourceDoc(
//      editBankChatMessage,
//      implementedInApiVersion,
//      nameOf(editBankChatMessage),
//      "PUT",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID",
//      "Edit Bank Chat Message",
//      s"""Edit a message. Only the sender can edit their own messages.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PutChatMessageJsonV600(content = "Updated message content"),
//      ChatMessageJsonV600(
//        chat_message_id = "msg-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "Updated message content",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        CannotEditOthersMessage,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val editBankChatMessage: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutChatMessageJsonV600", 400, callContext) {
//              json.extract[PutChatMessageJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            _ <- Helper.booleanToFuture(failMsg = CannotEditOthersMessage, cc = callContext) {
//              msg.senderUserId == u.userId
//            }
//            updatedMsg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.updateMessage(chatMessageId, putJson.content)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot edit message", 400)
//            }
//            reactions <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId).openOr(List.empty)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterUpdate(updatedMsg, u.name, u.provider, "")
//            (JSONFactory600.createChatMessageJson(updatedMsg, reactions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 16b. editSystemChatMessage
//    staticResourceDocs += ResourceDoc(
//      editSystemChatMessage,
//      implementedInApiVersion,
//      nameOf(editSystemChatMessage),
//      "PUT",
//      "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID",
//      "Edit System Chat Message",
//      s"""Edit a message in a system-level chat room. Only the sender can edit their own messages.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PutChatMessageJsonV600(content = "Updated message content"),
//      ChatMessageJsonV600(
//        chat_message_id = "msg-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "Updated message content",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        CannotEditOthersMessage,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val editSystemChatMessage: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: Nil JsonPut json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutChatMessageJsonV600", 400, callContext) {
//              json.extract[PutChatMessageJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            _ <- Helper.booleanToFuture(failMsg = CannotEditOthersMessage, cc = callContext) {
//              msg.senderUserId == u.userId
//            }
//            updatedMsg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.updateMessage(chatMessageId, putJson.content)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot edit message", 400)
//            }
//            reactions <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId).openOr(List.empty)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterUpdate(updatedMsg, u.name, u.provider, "")
//            (JSONFactory600.createChatMessageJson(updatedMsg, reactions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 17a. deleteBankChatMessage
//    staticResourceDocs += ResourceDoc(
//      deleteBankChatMessage,
//      implementedInApiVersion,
//      nameOf(deleteBankChatMessage),
//      "DELETE",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID",
//      "Delete Bank Chat Message",
//      s"""Soft-delete a message. The sender can delete their own messages, or a participant with can_delete_message permission can delete any message.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        CannotDeleteMessage,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val deleteBankChatMessage: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            _ <- if (msg.senderUserId == u.userId) {
//              Future.successful(Full(()))
//            } else {
//              Future {
//                code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_DELETE_MESSAGE)
//              } map {
//                x => unboxFullOrFail(x, callContext, CannotDeleteMessage, 403)
//              }
//            }
//            deletedMsg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.softDeleteMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot delete message", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterDelete(deletedMsg, u.name, u.provider, "")
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // 17b. deleteSystemChatMessage
//    staticResourceDocs += ResourceDoc(
//      deleteSystemChatMessage,
//      implementedInApiVersion,
//      nameOf(deleteSystemChatMessage),
//      "DELETE",
//      "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID",
//      "Delete System Chat Message",
//      s"""Soft-delete a message in a system-level chat room. The sender can delete their own messages, or a participant with can_delete_message permission can delete any message.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        CannotDeleteMessage,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val deleteSystemChatMessage: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            _ <- if (msg.senderUserId == u.userId) {
//              Future.successful(Full(()))
//            } else {
//              Future {
//                code.chat.ChatPermissions.checkParticipantPermission(chatRoomId, u.userId, code.chat.ChatPermissions.CAN_DELETE_MESSAGE)
//              } map {
//                x => unboxFullOrFail(x, callContext, CannotDeleteMessage, 403)
//              }
//            }
//            deletedMsg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.softDeleteMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot delete message", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterDelete(deletedMsg, u.name, u.provider, "")
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // ------ Batch E: Threads ------
//
//    // 18a. getBankThreadReplies
//    staticResourceDocs += ResourceDoc(
//      getBankThreadReplies,
//      implementedInApiVersion,
//      nameOf(getBankThreadReplies),
//      "GET",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread",
//      "Get Bank Thread Replies",
//      s"""Get all replies in a message thread. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatMessagesJsonV600(messages = List(ChatMessageJsonV600(
//        chat_message_id = "reply-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-456",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "This is a reply",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "msg-id-123",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getBankThreadReplies: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "thread" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            replies <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getThreadReplies(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get thread replies", 400)
//            }
//            allReactions <- Future {
//              replies.map { msg =>
//                val reactions = code.chat.ReactionTrait.reactionProvider.vend.getReactions(msg.chatMessageId).openOr(List.empty)
//                (msg.chatMessageId, reactions)
//              }.toMap
//            }
//          } yield {
//            (JSONFactory600.createChatMessagesJson(replies, allReactions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 18b. getSystemThreadReplies
//    staticResourceDocs += ResourceDoc(
//      getSystemThreadReplies,
//      implementedInApiVersion,
//      nameOf(getSystemThreadReplies),
//      "GET",
//      "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread",
//      "Get System Thread Replies",
//      s"""Get all replies in a message thread in a system-level chat room. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatMessagesJsonV600(messages = List(ChatMessageJsonV600(
//        chat_message_id = "reply-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-456",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "This is a reply",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "msg-id-123",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getSystemThreadReplies: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "thread" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            replies <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getThreadReplies(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get thread replies", 400)
//            }
//            allReactions <- Future {
//              replies.map { msg =>
//                val reactions = code.chat.ReactionTrait.reactionProvider.vend.getReactions(msg.chatMessageId).openOr(List.empty)
//                (msg.chatMessageId, reactions)
//              }.toMap
//            }
//          } yield {
//            (JSONFactory600.createChatMessagesJson(replies, allReactions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 19a. replyInBankThread
//    staticResourceDocs += ResourceDoc(
//      replyInBankThread,
//      implementedInApiVersion,
//      nameOf(replyInBankThread),
//      "POST",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread",
//      "Reply In Bank Thread",
//      s"""Reply to a message in a thread. The current user must be a participant and the room must not be archived.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostChatMessageJsonV600(content = "This is a thread reply", message_type = Some("text"), mentioned_user_ids = None, reply_to_message_id = None, thread_id = None),
//      ChatMessageJsonV600(
//        chat_message_id = "reply-id-456",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "This is a thread reply",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "msg-id-123",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatRoomIsArchived,
//        ChatMessageNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val replyInBankThread: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "thread" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostChatMessageJsonV600", 400, callContext) {
//              json.extract[PostChatMessageJsonV600]
//            }
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomIsArchived, cc = callContext) {
//              !room.isArchived
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.createMessage(
//                chatRoomId,
//                u.userId,
//                "",
//                postJson.content,
//                postJson.message_type.getOrElse("text"),
//                postJson.mentioned_user_ids.getOrElse(List.empty),
//                postJson.reply_to_message_id.getOrElse(""),
//                chatMessageId // threadId is the parent message ID
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot send thread reply", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterCreate(msg, u.name, u.provider, "")
//            (JSONFactory600.createChatMessageJson(msg, List.empty), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 19b. replyInSystemThread
//    staticResourceDocs += ResourceDoc(
//      replyInSystemThread,
//      implementedInApiVersion,
//      nameOf(replyInSystemThread),
//      "POST",
//      "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/thread",
//      "Reply In System Thread",
//      s"""Reply to a message in a thread in a system-level chat room. The current user must be a participant and the room must not be archived.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostChatMessageJsonV600(content = "This is a thread reply", message_type = Some("text"), mentioned_user_ids = None, reply_to_message_id = None, thread_id = None),
//      ChatMessageJsonV600(
//        chat_message_id = "reply-id-456",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-123",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "This is a thread reply",
//        message_type = "text",
//        mentioned_user_ids = List(),
//        reply_to_message_id = "",
//        thread_id = "msg-id-123",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatRoomIsArchived,
//        ChatMessageNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val replyInSystemThread: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "thread" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostChatMessageJsonV600", 400, callContext) {
//              json.extract[PostChatMessageJsonV600]
//            }
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Helper.booleanToFuture(failMsg = ChatRoomIsArchived, cc = callContext) {
//              !room.isArchived
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            msg <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.createMessage(
//                chatRoomId,
//                u.userId,
//                "",
//                postJson.content,
//                postJson.message_type.getOrElse("text"),
//                postJson.mentioned_user_ids.getOrElse(List.empty),
//                postJson.reply_to_message_id.getOrElse(""),
//                chatMessageId // threadId is the parent message ID
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot send thread reply", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterCreate(msg, u.name, u.provider, "")
//            (JSONFactory600.createChatMessageJson(msg, List.empty), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // ------ Batch F: Reactions ------
//
//    // 20a. addBankReaction
//    staticResourceDocs += ResourceDoc(
//      addBankReaction,
//      implementedInApiVersion,
//      nameOf(addBankReaction),
//      "POST",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions",
//      "Add Bank Reaction",
//      s"""Add a reaction (emoji) to a message. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostReactionJsonV600(emoji = "thumbsup"),
//      ReactionJsonV600(
//        reaction_id = "reaction-id-123",
//        chat_message_id = "msg-id-123",
//        user_id = "user-id-123",
//        username = "robert.x.0.gh",
//        provider = "https://github.com",
//        emoji = "thumbsup",
//        created_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        ReactionAlreadyExists,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val addBankReaction: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "reactions" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostReactionJsonV600", 400, callContext) {
//              json.extract[PostReactionJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            existingReaction <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReaction(chatMessageId, u.userId, postJson.emoji))
//            _ <- Helper.booleanToFuture(failMsg = ReactionAlreadyExists, failCode = 409, cc = callContext) {
//              existingReaction.isEmpty
//            }
//            reaction <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.addReaction(chatMessageId, u.userId, postJson.emoji)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot add reaction", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterReactionAdd(chatRoomId, chatMessageId, postJson.emoji, u.userId, u.name, u.provider)
//            (JSONFactory600.createReactionJson(reaction), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 20b. addSystemReaction
//    staticResourceDocs += ResourceDoc(
//      addSystemReaction,
//      implementedInApiVersion,
//      nameOf(addSystemReaction),
//      "POST",
//      "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions",
//      "Add System Reaction",
//      s"""Add a reaction (emoji) to a message in a system-level chat room. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      PostReactionJsonV600(emoji = "thumbsup"),
//      ReactionJsonV600(
//        reaction_id = "reaction-id-123",
//        chat_message_id = "msg-id-123",
//        user_id = "user-id-123",
//        username = "robert.x.0.gh",
//        provider = "https://github.com",
//        emoji = "thumbsup",
//        created_at = new java.util.Date()
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        ReactionAlreadyExists,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val addSystemReaction: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "reactions" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostReactionJsonV600", 400, callContext) {
//              json.extract[PostReactionJsonV600]
//            }
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            existingReaction <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReaction(chatMessageId, u.userId, postJson.emoji))
//            _ <- Helper.booleanToFuture(failMsg = ReactionAlreadyExists, failCode = 409, cc = callContext) {
//              existingReaction.isEmpty
//            }
//            reaction <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.addReaction(chatMessageId, u.userId, postJson.emoji)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot add reaction", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterReactionAdd(chatRoomId, chatMessageId, postJson.emoji, u.userId, u.name, u.provider)
//            (JSONFactory600.createReactionJson(reaction), HttpCode.`201`(callContext))
//          }
//      }
//    }
//
//    // 21a. removeBankReaction
//    staticResourceDocs += ResourceDoc(
//      removeBankReaction,
//      implementedInApiVersion,
//      nameOf(removeBankReaction),
//      "DELETE",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions/EMOJI",
//      "Remove Bank Reaction",
//      s"""Remove your own reaction from a message.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        ReactionNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val removeBankReaction: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "reactions" :: emoji :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            decodedEmoji = URLDecoder.decode(emoji, StandardCharsets.UTF_8.name())
//            existingReaction <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReaction(chatMessageId, u.userId, decodedEmoji))
//            _ <- Helper.booleanToFuture(failMsg = ReactionNotFound, cc = callContext) {
//              existingReaction.isDefined
//            }
//            _ <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.removeReaction(chatMessageId, u.userId, decodedEmoji)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot remove reaction", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterReactionRemove(chatRoomId, chatMessageId, decodedEmoji, u.userId, u.name, u.provider)
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // 21b. removeSystemReaction
//    staticResourceDocs += ResourceDoc(
//      removeSystemReaction,
//      implementedInApiVersion,
//      nameOf(removeSystemReaction),
//      "DELETE",
//      "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions/EMOJI",
//      "Remove System Reaction",
//      s"""Remove your own reaction from a message in a system-level chat room.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        ReactionNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val removeSystemReaction: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "reactions" :: emoji :: Nil JsonDelete _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            decodedEmoji = URLDecoder.decode(emoji, StandardCharsets.UTF_8.name())
//            existingReaction <- Future(code.chat.ReactionTrait.reactionProvider.vend.getReaction(chatMessageId, u.userId, decodedEmoji))
//            _ <- Helper.booleanToFuture(failMsg = ReactionNotFound, cc = callContext) {
//              existingReaction.isDefined
//            }
//            _ <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.removeReaction(chatMessageId, u.userId, decodedEmoji)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot remove reaction", 400)
//            }
//          } yield {
//            code.chat.ChatEventPublisher.afterReactionRemove(chatRoomId, chatMessageId, decodedEmoji, u.userId, u.name, u.provider)
//            (EmptyBody, HttpCode.`204`(callContext))
//          }
//      }
//    }
//
//    // 22a. getBankReactions
//    staticResourceDocs += ResourceDoc(
//      getBankReactions,
//      implementedInApiVersion,
//      nameOf(getBankReactions),
//      "GET",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions",
//      "Get Bank Reactions",
//      s"""Get all reactions for a message. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ReactionsJsonV600(reactions = List(ReactionJsonV600(
//        reaction_id = "reaction-id-123",
//        chat_message_id = "msg-id-123",
//        user_id = "user-id-123",
//        username = "robert.x.0.gh",
//        provider = "https://github.com",
//        emoji = "thumbsup",
//        created_at = new java.util.Date()
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getBankReactions: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "reactions" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            reactions <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get reactions", 400)
//            }
//          } yield {
//            (JSONFactory600.createReactionsJson(reactions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 22b. getSystemReactions
//    staticResourceDocs += ResourceDoc(
//      getSystemReactions,
//      implementedInApiVersion,
//      nameOf(getSystemReactions),
//      "GET",
//      "/chat-rooms/CHAT_ROOM_ID/messages/CHAT_MESSAGE_ID/reactions",
//      "Get System Reactions",
//      s"""Get all reactions for a message in a system-level chat room. The current user must be a participant.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ReactionsJsonV600(reactions = List(ReactionJsonV600(
//        reaction_id = "reaction-id-123",
//        chat_message_id = "msg-id-123",
//        user_id = "user-id-123",
//        username = "robert.x.0.gh",
//        provider = "https://github.com",
//        emoji = "thumbsup",
//        created_at = new java.util.Date()
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        ChatMessageNotFound,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getSystemReactions: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: chatMessageId :: "reactions" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMessage(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatMessageNotFound, 404)
//            }
//            reactions <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.getReactions(chatMessageId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get reactions", 400)
//            }
//          } yield {
//            (JSONFactory600.createReactionsJson(reactions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // ------ Batch G: Typing ------
//
//    // 23a. signalBankTyping
//    staticResourceDocs += ResourceDoc(
//      signalBankTyping,
//      implementedInApiVersion,
//      nameOf(signalBankTyping),
//      "PUT",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/typing-indicators",
//      "Signal Bank Typing",
//      s"""Signal that the current user is typing in a chat room. The typing indicator expires after 5 seconds.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val signalBankTyping: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "typing-indicators" :: Nil JsonPut _ -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              val key = s"chat_typing_${chatRoomId}_${u.userId}"
//              Redis.use(code.api.JedisMethod.SET, key, Some(5), Some("1"))
//              code.chat.ChatEventPublisher.afterTyping(chatRoomId, u.userId, u.name, u.provider, true)
//            }
//          } yield {
//            (EmptyBody, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 23b. signalSystemTyping
//    staticResourceDocs += ResourceDoc(
//      signalSystemTyping,
//      implementedInApiVersion,
//      nameOf(signalSystemTyping),
//      "PUT",
//      "/chat-rooms/CHAT_ROOM_ID/typing-indicators",
//      "Signal System Typing",
//      s"""Signal that the current user is typing in a system-level chat room. The typing indicator expires after 5 seconds.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      EmptyBody,
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val signalSystemTyping: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "typing-indicators" :: Nil JsonPut _ -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            _ <- Future {
//              val key = s"chat_typing_${chatRoomId}_${u.userId}"
//              Redis.use(code.api.JedisMethod.SET, key, Some(5), Some("1"))
//              code.chat.ChatEventPublisher.afterTyping(chatRoomId, u.userId, u.name, u.provider, true)
//            }
//          } yield {
//            (EmptyBody, HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 24a. getBankTypingUsers
//    staticResourceDocs += ResourceDoc(
//      getBankTypingUsers,
//      implementedInApiVersion,
//      nameOf(getBankTypingUsers),
//      "GET",
//      "/banks/BANK_ID/chat-rooms/CHAT_ROOM_ID/typing-indicators",
//      "Get Bank Typing Users",
//      s"""Get the list of users currently typing in a chat room.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      TypingUsersJsonV600(users = List(TypingUserJsonV600(user_id = "user-id-123", username = "robert.x.0.gh", provider = "https://github.com"))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getBankTypingUsers: OBPEndpoint = {
//      case "banks" :: BankId(bankId) :: "chat-rooms" :: chatRoomId :: "typing-indicators" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            participants <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.getParticipants(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get participants", 400)
//            }
//            typingUsers <- Future {
//              participants.filter(_.userId.nonEmpty).flatMap { p =>
//                val key = s"chat_typing_${chatRoomId}_${p.userId}"
//                try {
//                  Redis.use(code.api.JedisMethod.GET, key) match {
//                    case Some(_) =>
//                      val typingUser = code.users.Users.users.vend.getUserByUserId(p.userId)
//                      Some(TypingUserJsonV600(user_id = p.userId, username = typingUser.map(_.name).getOrElse(""), provider = typingUser.map(_.provider).getOrElse("")))
//                    case None => None
//                  }
//                } catch {
//                  case _: Throwable => None
//                }
//              }
//            }
//          } yield {
//            (TypingUsersJsonV600(users = typingUsers), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 24b. getSystemTypingUsers
//    staticResourceDocs += ResourceDoc(
//      getSystemTypingUsers,
//      implementedInApiVersion,
//      nameOf(getSystemTypingUsers),
//      "GET",
//      "/chat-rooms/CHAT_ROOM_ID/typing-indicators",
//      "Get System Typing Users",
//      s"""Get the list of users currently typing in a system-level chat room.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      TypingUsersJsonV600(users = List(TypingUserJsonV600(user_id = "user-id-123", username = "robert.x.0.gh", provider = "https://github.com"))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getSystemTypingUsers: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "typing-indicators" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            participants <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.getParticipants(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get participants", 400)
//            }
//            typingUsers <- Future {
//              participants.filter(_.userId.nonEmpty).flatMap { p =>
//                val key = s"chat_typing_${chatRoomId}_${p.userId}"
//                try {
//                  Redis.use(code.api.JedisMethod.GET, key) match {
//                    case Some(_) =>
//                      val typingUser = code.users.Users.users.vend.getUserByUserId(p.userId)
//                      Some(TypingUserJsonV600(user_id = p.userId, username = typingUser.map(_.name).getOrElse(""), provider = typingUser.map(_.provider).getOrElse("")))
//                    case None => None
//                  }
//                } catch {
//                  case _: Throwable => None
//                }
//              }
//            }
//          } yield {
//            (TypingUsersJsonV600(users = typingUsers), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // ------ Batch H: User-Level ------
//
//    // 25. getMyChatRooms
//    staticResourceDocs += ResourceDoc(
//      getMyChatRooms,
//      implementedInApiVersion,
//      nameOf(getMyChatRooms),
//      "GET",
//      "/users/current/chat-rooms",
//      "Get My Chat Rooms",
//      s"""Get all chat rooms the current user is a participant of, across all banks and system-level rooms.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatRoomsJsonV600(chat_rooms = List(ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "gh.29.uk",
//        name = "General Discussion",
//        description = "A place to discuss general topics",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-123",
//        created_by_username = "robert.x.0.gh",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello everyone!"),
//        last_message_sender_username =Some("robert.x.0.gh"),
//        unread_count = Some(3),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getMyChatRooms: OBPEndpoint = {
//      case "users" :: "current" :: "chat-rooms" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            participantRecords <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.getParticipantRoomsByUserId(u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get participant records", 400)
//            }
//            roomsAndCounts <- Future {
//              participantRecords.flatMap { p =>
//                code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(p.chatRoomId).toList.map { room =>
//                  val count = if (room.isOpenRoom) {
//                    code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadMentionCount(p.chatRoomId, p.userId, p.lastReadAt)
//                  } else {
//                    code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadCount(p.chatRoomId, p.userId, p.lastReadAt)
//                  }
//                  (room, count.openOr(0L))
//                }
//              }
//            }
//            participantCounts <- Future {
//              computeParticipantCounts(roomsAndCounts.map(_._1))
//            }
//          } yield {
//            val rooms = roomsAndCounts.map(_._1)
//            val unreadCounts = roomsAndCounts.map { case (room, count) => room.chatRoomId -> count }.toMap
//            (JSONFactory600.createChatRoomsJson(rooms, unreadCounts, participantCounts), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 25b. searchChatRooms
//    staticResourceDocs += ResourceDoc(
//      searchChatRooms,
//      implementedInApiVersion,
//      nameOf(searchChatRooms),
//      "POST",
//      "/chat-rooms/search",
//      "Search Chat Rooms",
//      s"""Search chat rooms the current user is a participant of, filtered by the supplied criteria.
//         |
//         |Currently supports filtering by participant set:
//         |
//         |- `with_user_ids` (array of user_id strings, required): only return rooms where the current user
//         |  AND every listed user_id are participants. Pass an empty list to match all of the current user's rooms.
//         |- `exact_participants` (boolean, optional, default `false`): if `true`, the room's participant set
//         |  must equal exactly `{current user} ∪ with_user_ids` with no extras. Open rooms are excluded
//         |  from exact-participant searches because their participant set is implicitly "everyone".
//         |
//         |Primary use case: a client looking up an existing 1-on-1 direct-message room before creating one,
//         |by calling with `with_user_ids: [<other user_id>]` and `exact_participants: true`.
//         |
//         |The response shape is the same as `Get My Chat Rooms`.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      ChatRoomSearchRequestJsonV600(
//        with_user_ids = List("user-id-123"),
//        exact_participants = Some(true)
//      ),
//      ChatRoomsJsonV600(chat_rooms = List(ChatRoomJsonV600(
//        chat_room_id = "chat-room-id-123",
//        bank_id = "",
//        name = "DM with robert.x.0.gh",
//        description = "",
//        joining_key = "abc123key",
//        created_by_user_id = "user-id-456",
//        created_by_username = "alice",
//        created_by_provider = "https://github.com",
//        is_open_room = false,
//        is_archived = false,
//        last_message_at = Some(new java.util.Date()),
//        last_message_preview = Some("Hello!"),
//        last_message_sender_username =Some("alice"),
//        unread_count = Some(0),
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date()
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        InvalidJsonFormat,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val searchChatRooms: OBPEndpoint = {
//      case "chat-rooms" :: "search" :: Nil JsonPost json -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $ChatRoomSearchRequestJsonV600", 400, callContext) {
//              json.extract[ChatRoomSearchRequestJsonV600]
//            }
//            rooms <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.searchChatRoomsForUserWithParticipants(
//                u.userId,
//                postJson.with_user_ids,
//                postJson.exact_participants.getOrElse(false)
//              )
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot search chat rooms", 400)
//            }
//            unreadCounts <- Future {
//              computeUnreadCounts(rooms, u.userId)
//            }
//            participantCounts <- Future {
//              computeParticipantCounts(rooms)
//            }
//          } yield {
//            (JSONFactory600.createChatRoomsJson(rooms, unreadCounts, participantCounts), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 26. getMyUnreadCounts
//    staticResourceDocs += ResourceDoc(
//      getMyUnreadCounts,
//      implementedInApiVersion,
//      nameOf(getMyUnreadCounts),
//      "GET",
//      "/users/current/chat-rooms/unread",
//      "Get My Unread Counts",
//      s"""Get unread message counts for all chat rooms the current user is a participant of.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      UnreadCountsJsonV600(unread_counts = List(UnreadCountJsonV600(chat_room_id = "chat-room-id-123", unread_count = 5))),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getMyUnreadCounts: OBPEndpoint = {
//      case "users" :: "current" :: "chat-rooms" :: "unread" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            participantRecords <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.getParticipantRoomsByUserId(u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get participant records", 400)
//            }
//            unreadCounts <- Future {
//              participantRecords.flatMap { p =>
//                val room = code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(p.chatRoomId)
//                val isOpenRoom = room.map(_.isOpenRoom).openOr(false)
//                val count = if (isOpenRoom) {
//                  code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadMentionCount(p.chatRoomId, p.userId, p.lastReadAt)
//                } else {
//                  code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadCount(p.chatRoomId, p.userId, p.lastReadAt)
//                }
//                count.toList.map(c => UnreadCountJsonV600(chat_room_id = p.chatRoomId, unread_count = c))
//              }
//            }
//          } yield {
//            (UnreadCountsJsonV600(unread_counts = unreadCounts), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 27. markChatRoomRead
//    staticResourceDocs += ResourceDoc(
//      markChatRoomRead,
//      implementedInApiVersion,
//      nameOf(markChatRoomRead),
//      "PUT",
//      "/users/current/chat-rooms/CHAT_ROOM_ID/read-marker",
//      "Mark Chat Room Read",
//      s"""Mark all messages in a chat room as read for the current user by updating lastReadAt to now.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ParticipantJsonV600(
//        participant_id = "participant-id-123",
//        chat_room_id = "chat-room-id-123",
//        user_id = "user-id-123",
//        username = "robert.x.0.gh",
//        provider = "https://github.com",
//        consumer_id = "",
//        consumer_name = "",
//        permissions = List(),
//        webhook_url = "",
//        joined_at = new java.util.Date(),
//        last_read_at = new java.util.Date(),
//        is_muted = false
//      ),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val markChatRoomRead: OBPEndpoint = {
//      case "users" :: "current" :: "chat-rooms" :: chatRoomId :: "read-marker" :: Nil JsonPut _ -> _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            _ <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            updatedParticipant <- Future {
//              code.chat.ParticipantTrait.participantProvider.vend.updateLastReadAt(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot mark as read", 400)
//            }
//          } yield {
//            (JSONFactory600.createParticipantJson(updatedParticipant), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 28. getMyMentions
//    staticResourceDocs += ResourceDoc(
//      getMyMentions,
//      implementedInApiVersion,
//      nameOf(getMyMentions),
//      "GET",
//      "/users/current/mentions",
//      "Get My Mentions",
//      s"""Get messages where the current user is mentioned. Supports limit and offset query parameters.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      ChatMessagesJsonV600(messages = List(ChatMessageJsonV600(
//        chat_message_id = "msg-id-123",
//        chat_room_id = "chat-room-id-123",
//        sender_user_id = "user-id-456",
//        sender_consumer_id = "",
//        sender_username = "robert.x.0.gh",
//        sender_provider = "https://github.com",
//        sender_consumer_name = "My Banking App",
//        content = "Hey @user-id-123, check this out!",
//        message_type = "text",
//        mentioned_user_ids = List("user-id-123"),
//        reply_to_message_id = "",
//        thread_id = "",
//        is_deleted = false,
//        created_at = new java.util.Date(),
//        updated_at = new java.util.Date(),
//        reactions = List()
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getMyMentions: OBPEndpoint = {
//      case "users" :: "current" :: "mentions" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            limitParam = ObpS.param("limit").map(_.toInt).getOrElse(50)
//            offsetParam = ObpS.param("offset").map(_.toInt).getOrElse(0)
//            messages <- Future {
//              code.chat.ChatMessageTrait.chatMessageProvider.vend.getMentionsForUser(u.userId, limitParam, offsetParam)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get mentions", 400)
//            }
//            allReactions <- Future {
//              messages.map { msg =>
//                val reactions = code.chat.ReactionTrait.reactionProvider.vend.getReactions(msg.chatMessageId).openOr(List.empty)
//                (msg.chatMessageId, reactions)
//              }.toMap
//            }
//          } yield {
//            (JSONFactory600.createChatMessagesJson(messages, allReactions), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    // 29. getBulkReactions
//    staticResourceDocs += ResourceDoc(
//      getBulkReactions,
//      implementedInApiVersion,
//      nameOf(getBulkReactions),
//      "GET",
//      "/chat-rooms/CHAT_ROOM_ID/messages/reactions",
//      "Get Bulk Reactions",
//      s"""Get reactions for multiple messages in a single request.
//         |
//         |Pass message IDs as a comma-separated query parameter: ?message_ids=id1,id2,id3
//         |
//         |Returns reactions grouped by message ID.
//         |
//         |Authentication is Required
//         |
//         |""".stripMargin,
//      EmptyBody,
//      BulkReactionsJsonV600(message_reactions = List(MessageReactionsJsonV600(
//        chat_message_id = "msg-id-123",
//        reactions = List(ReactionSummaryJsonV600(emoji = "thumbsup", count = 2, user_ids = List("user-1", "user-2")))
//      ))),
//      List(
//        $AuthenticatedUserIsRequired,
//        ChatRoomNotFound,
//        NotChatRoomParticipant,
//        UnknownError
//      ),
//      List(apiTagChat),
//      None
//    )
//
//    lazy val getBulkReactions: OBPEndpoint = {
//      case "chat-rooms" :: chatRoomId :: "messages" :: "reactions" :: Nil JsonGet _ => {
//        cc => implicit val ec = EndpointContext(Some(cc))
//          for {
//            (Full(u), callContext) <- authenticatedAccess(cc)
//            room <- Future {
//              code.chat.ChatRoomTrait.chatRoomProvider.vend.getChatRoom(chatRoomId)
//            } map {
//              x => unboxFullOrFail(x, callContext, ChatRoomNotFound, 404)
//            }
//            _ <- Future {
//              code.chat.ChatPermissions.isParticipant(chatRoomId, u.userId)
//            } map {
//              x => unboxFullOrFail(x, callContext, NotChatRoomParticipant, 403)
//            }
//            messageIds = ObpS.param("message_ids").map(_.split(",").map(_.trim).filter(_.nonEmpty).toList).getOrElse(List.empty)
//            allReactions <- Future {
//              code.chat.ReactionTrait.reactionProvider.vend.getReactionsForMessages(messageIds)
//            } map {
//              x => unboxFullOrFail(x, callContext, s"$UnknownError Cannot get reactions", 400)
//            }
//          } yield {
//            (JSONFactory600.createBulkReactionsJson(allReactions, messageIds), HttpCode.`200`(callContext))
//          }
//      }
//    }
//
//    /**
//     * Compute the participant count for a single chat room.
//     */
//    private def computeParticipantCount(chatRoomId: String): Long = {
//      code.chat.ParticipantTrait.participantProvider.vend
//        .getParticipants(chatRoomId)
//        .map(_.length.toLong)
//        .openOr(0L)
//    }
//
//    /**
//     * Compute the participant count for each given room.
//     * One DB query per room — same N+1 pattern as `computeUnreadCounts`.
//     */
//    private def computeParticipantCounts(rooms: List[code.chat.ChatRoomTrait]): Map[String, Long] = {
//      rooms.map(room => room.chatRoomId -> computeParticipantCount(room.chatRoomId)).toMap
//    }
//
//    /**
//     * Compute unread counts for a list of rooms for a given user.
//     * For open rooms, counts only mentions. For private rooms, counts all unread messages.
//     */
//    private def computeUnreadCounts(rooms: List[code.chat.ChatRoomTrait], userId: String): Map[String, Long] = {
//      rooms.flatMap { room =>
//        val participant = code.chat.ChatPermissions.isParticipant(room.chatRoomId, userId)
//        participant.toList.map { p =>
//          val count = if (room.isOpenRoom) {
//            code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadMentionCount(room.chatRoomId, userId, p.lastReadAt)
//          } else {
//            code.chat.ChatMessageTrait.chatMessageProvider.vend.getUnreadCount(room.chatRoomId, userId, p.lastReadAt)
//          }
//          room.chatRoomId -> count.openOr(0L)
//        }
//      }.toMap
//    }
//
//  }
//}
//
//
//
//object APIMethods600 {
//  lazy val newStyleEndpoints: List[(String, String)] = Implementations6_0_0.resourceDocs.map {
//    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
//  }.toList
//
//  // Canonical cache key for product-list endpoints. Params are sorted by name (and by value within each)
//  // so that `?tag=a&tag=b` and `?tag=b&tag=a` share a cache entry. Bank is "__all__" for the system-level endpoint.
//  def productsCacheKey(bankId: String, params: List[GetProductsParam]): String = {
//    val canonical = params
//      .map(p => p.name -> p.value.sorted)
//      .sortBy(_._1)
//      .map { case (name, values) => s"$name=${values.mkString(",")}" }
//      .mkString("&")
//    s"productsV600:$bankId:$canonical"
//  }
//}

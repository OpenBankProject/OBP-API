package code.api.v7_0_0

import code.api.Constant
import code.api.util.{APIUtil, AuthRateLimiter, CallContext, ExampleValue, RateLimitingUtil, SelfServiceRateLimiter}
import code.api.util.ErrorMessages
import code.api.util.{Glossary, PegdownOptions}
import code.api.util.ErrorMessages.MandatoryPropertyIsNotSet
import code.api.v2_0_0.EntitlementJSONs
import code.api.v3_0_0.{UserJsonV300, ViewsJSON300}
import code.api.v4_0_0.{EnergySource400, HostedAt400, HostedBy400, PostSimpleCounterpartyJson400, UserAgreementJson}
import code.api.v6_0_0.{EntitlementsJsonV600, JSONFactory600, UserInfoDetailJsonV600, UserV600}
import code.apiproductsubscription.ApiProductSubscriptionTrait
import code.apiproductsubscriptionattribute.ApiProductSubscriptionAttributeTrait
import code.bankconnectors.Connector
import code.customer.CustomerX
import code.metrics.{MappedMetric, MetricArchive, MetricsArchiveRun, MetricsProps}
import code.util.Helper.MdcLoggable
import code.views.Views
import code.api.v3_1_0.{AccountAttributeResponseJson, JSONFactory310}
import code.dynamicResourceDoc.{DynamicResourceDoc, JsonDynamicResourceDoc}
import code.connectormethod.{ConnectorMethod, JsonConnectorMethod}
import code.dynamicMessageDoc.{DynamicMessageDoc, JsonDynamicMessageDoc}
import org.apache.commons.lang3.StringUtils
import com.openbankproject.commons.model.{AccountAttribute, AccountId, AccountRoutingJsonV121, AmountOfMoneyJsonV121, BankAccount, BankId, BankIdAccountId, CoreAccount, TransactionRequest, TransactionRequestCommonBodyJSON, User}
import com.openbankproject.commons.util.ApiVersion
import java.util.Date
import org.json4s.{Extraction, JValue}
import org.json4s.JsonAST.{JNothing, JString}
import net.liftweb.common.Full
import net.liftweb.mapper.{Ascending, By, By_<=, Descending, MaxRows, OrderBy}

import scala.concurrent.{ExecutionContext, Future}

object JSONFactory700 extends MdcLoggable with code.api.util.CustomJsonFormats {

  // ─── Provenance for runtime-compiled dynamic code (v7.0.0 read-only exposure) ───
  // The v4.0.0 create/update endpoints capture who created / last updated a piece of runtime
  // code and a SHA-256 of its (decoded) method body into DB columns, but the v4 response shape
  // is frozen (STABLE) and does not carry them. These v7 GET endpoints expose that provenance,
  // wrapping the unchanged v4 resource JSON alongside a `provenance` object.
  case class ProvenanceJsonV700(
    created_by_user_id: Option[String],
    updated_by_user_id: Option[String],
    method_body_hash: Option[String],
    created_at: Option[String],
    updated_at: Option[String],
    // maker/checker: the body hash a checker approved (None when never approved) and the active flag
    approved_hash: Option[String] = None,
    is_active: Option[Boolean] = None
  )
  case class DynamicResourceDocProvenanceJsonV700(dynamic_resource_doc: JsonDynamicResourceDoc, provenance: ProvenanceJsonV700)
  case class DynamicResourceDocsProvenanceJsonV700(dynamic_resource_docs: List[DynamicResourceDocProvenanceJsonV700])
  case class ConnectorMethodProvenanceJsonV700(connector_method: JsonConnectorMethod, provenance: ProvenanceJsonV700)
  case class ConnectorMethodsProvenanceJsonV700(connector_methods: List[ConnectorMethodProvenanceJsonV700])
  case class DynamicMessageDocProvenanceJsonV700(dynamic_message_doc: JsonDynamicMessageDoc, provenance: ProvenanceJsonV700)
  case class DynamicMessageDocsProvenanceJsonV700(dynamic_message_docs: List[DynamicMessageDocProvenanceJsonV700])

  private def blankToNone(s: String): Option[String] = Option(s).filter(StringUtils.isNotBlank)
  private def formatDateOpt(d: Date): Option[String] = Option(d).map(APIUtil.formatDate)

  def createDynamicResourceDocProvenanceJsonV700(entity: DynamicResourceDoc): DynamicResourceDocProvenanceJsonV700 =
    DynamicResourceDocProvenanceJsonV700(
      DynamicResourceDoc.getJsonDynamicResourceDoc(entity),
      ProvenanceJsonV700(
        blankToNone(entity.CreatedByUserId.get), blankToNone(entity.UpdatedByUserId.get),
        blankToNone(entity.MethodBodyHash.get), formatDateOpt(entity.createdAt.get), formatDateOpt(entity.updatedAt.get),
        blankToNone(entity.ApprovedHash.get), Some(entity.IsActive.get))
    )

  def createConnectorMethodProvenanceJsonV700(entity: ConnectorMethod): ConnectorMethodProvenanceJsonV700 =
    ConnectorMethodProvenanceJsonV700(
      ConnectorMethod.getJsonConnectorMethod(entity),
      ProvenanceJsonV700(
        blankToNone(entity.CreatedByUserId.get), blankToNone(entity.UpdatedByUserId.get),
        blankToNone(entity.MethodBodyHash.get), formatDateOpt(entity.createdAt.get), formatDateOpt(entity.updatedAt.get),
        blankToNone(entity.ApprovedHash.get), Some(entity.IsActive.get))
    )

  def createDynamicMessageDocProvenanceJsonV700(entity: DynamicMessageDoc): DynamicMessageDocProvenanceJsonV700 =
    DynamicMessageDocProvenanceJsonV700(
      DynamicMessageDoc.getJsonDynamicMessageDoc(entity),
      ProvenanceJsonV700(
        blankToNone(entity.CreatedByUserId.get), blankToNone(entity.UpdatedByUserId.get),
        blankToNone(entity.MethodBodyHash.get), formatDateOpt(entity.createdAt.get), formatDateOpt(entity.updatedAt.get),
        blankToNone(entity.ApprovedHash.get), Some(entity.IsActive.get))
    )

  // ─── Maker/checker: dynamic change requests (design: MAKER_CHECKER_DYNAMIC_CODE_DESIGN.md) ───
  case class PostDynamicChangeRequestJsonV700(
    target_type: String,
    operation: String,
    target_id: Option[String],
    bank_id: Option[String],
    proposed_payload: JValue,
    business_justification: Option[String]
  )
  case class PostApproveDynamicChangeRequestJsonV700(payload_hash: String, checker_comment: Option[String])
  case class PostRejectDynamicChangeRequestJsonV700(comment: String)
  case class PostWithdrawDynamicChangeRequestJsonV700(comment: Option[String])
  case class PostDeactivateDynamicArtefactJsonV700(comment: Option[String])

  case class DynamicChangeRequestJsonV700(
    dynamic_change_request_id: String,
    target_type: String,
    target_id: String,
    operation: String,
    status: String,
    request_verb: String,
    request_path: String,
    payload_hash: String,
    current_payload_hash: String,
    proposed_payload: JValue,
    current_payload: JValue,
    requestor_user_id: String,
    business_justification: String,
    checker_user_id: String,
    checker_comment: String,
    created_at: String,
    actioned_at: String,
    expires_at: String
  )
  case class DynamicChangeRequestsJsonV700(dynamic_change_requests: List[DynamicChangeRequestJsonV700])

  private def parseOrString(s: String): JValue =
    com.openbankproject.commons.util.JsonAliases.parseOpt(Option(s).getOrElse("")).getOrElse(if (StringUtils.isBlank(s)) JNothing else JString(s))

  /** The live target's JSON, so a client can diff proposed vs current; JNothing when it does not exist. */
  def currentPayloadOf(targetType: String, targetId: String): JValue = {
    import code.abacrule.MappedAbacRuleProvider
    import com.openbankproject.commons.model.enums.DynamicChangeRequestTargetType._
    if (StringUtils.isBlank(targetId)) JNothing
    else scala.util.Try(com.openbankproject.commons.model.enums.DynamicChangeRequestTargetType.withName(targetType)).toOption.map {
      case DYNAMIC_RESOURCE_DOC => code.dynamicResourceDoc.DynamicResourceDocProvider.provider.vend.getById(None, targetId).map(Extraction.decompose(_)).getOrElse(JNothing)
      case DYNAMIC_MESSAGE_DOC  => code.dynamicMessageDoc.DynamicMessageDocProvider.provider.vend.getById(None, targetId).map(Extraction.decompose(_)).getOrElse(JNothing)
      case CONNECTOR_METHOD     => code.connectormethod.ConnectorMethodProvider.provider.vend.getById(targetId).map(Extraction.decompose(_)).getOrElse(JNothing)
      case ABAC_RULE            => MappedAbacRuleProvider.getAbacRuleById(targetId).map(r => Extraction.decompose(JSONFactory600.createAbacRuleJsonV600(r))).getOrElse(JNothing)
      case _                    => JNothing
    }.getOrElse(JNothing)
  }

  def createDynamicChangeRequestJsonV700(r: code.dynamicchangerequest.DynamicChangeRequestTrait): DynamicChangeRequestJsonV700 =
    DynamicChangeRequestJsonV700(
      dynamic_change_request_id = r.dynamicChangeRequestId,
      target_type = r.targetType,
      target_id = r.targetId,
      operation = r.operation,
      status = r.status,
      request_verb = r.requestVerb,
      request_path = r.requestPath,
      payload_hash = r.payloadHash,
      current_payload_hash = r.currentPayloadHash,
      proposed_payload = parseOrString(r.proposedPayload),
      current_payload = currentPayloadOf(r.targetType, r.targetId),
      requestor_user_id = r.requestorUserId,
      business_justification = r.businessJustification,
      checker_user_id = r.checkerUserId,
      checker_comment = r.checkerComment,
      created_at = APIUtil.formatDate(r.created),
      actioned_at = r.actionedAt.map(APIUtil.formatDate).getOrElse(""),
      expires_at = r.expiresAt.map(APIUtil.formatDate).getOrElse("")
    )

  case class ErrorMessageEntryJsonV700(code: String, name: String, message: String)

  // ─── API tags (GET /api/tags) ─────────────────────────────────────────────────────────────
  /** One API tag and the number of endpoints in the aggregated v7.0.0 resource docs that carry it. */
  case class ApiTagJsonV700(tag: String, number_of_endpoints: Int)

  /**
   * All API tags with per-tag endpoint counts, sorted by number_of_endpoints descending then tag name.
   * `number_of_endpoints` at the top level is the number of distinct endpoints counted; an endpoint
   * with several tags is counted once under each of them, so the per-tag counts sum to more than that.
   */
  case class ApiTagsJsonV700(tags: List[ApiTagJsonV700], number_of_endpoints: Int)

  val apiTagsJsonV700Example: ApiTagsJsonV700 = ApiTagsJsonV700(
    tags = List(
      ApiTagJsonV700(tag = "Account", number_of_endpoints = 42),
      ApiTagJsonV700(tag = "Bank", number_of_endpoints = 17),
      ApiTagJsonV700(tag = "Transaction Request", number_of_endpoints = 12)
    ),
    number_of_endpoints = 900
  )

  /**
   * Counts endpoints per tag over the given resource docs and merges the result with every tag known to
   * `ApiTag` (static and dynamic), so tags with no endpoints still appear with a count of 0.
   */
  def createApiTagsJsonV700(resourceDocs: Seq[APIUtil.ResourceDoc]): ApiTagsJsonV700 = {
    val counts: Map[String, Int] = resourceDocs
      .flatMap(_.tags.map(_.displayTag).distinct)
      .groupBy(identity)
      .map { case (tag, occurrences) => tag -> occurrences.size }
    val allTagNames: Set[String] = code.api.util.ApiTag.allDisplayTagNames ++ counts.keySet
    val tags = allTagNames.toList
      .map(tag => ApiTagJsonV700(tag, counts.getOrElse(tag, 0)))
      .sortBy(t => (-t.number_of_endpoints, t.tag))
    ApiTagsJsonV700(tags, resourceDocs.size)
  }

  // ─── Rate limiter config (GET /management/rate-limiter-config) ─────────────────────────
  /** One limit row of a rate limiter. Windows the limiter does not have are absent; -1 means unlimited, 0 blocks. */
  case class RateLimiterLimitJsonV700(
    scope: String,
    per_second: Option[Long] = None,
    per_minute: Option[Long] = None,
    per_hour: Option[Long] = None,
    per_day: Option[Long] = None,
    per_week: Option[Long] = None,
    per_month: Option[Long] = None,
    global_per_hour: Option[Long] = None
  )
  /** One of the three rate limiters, in the order they are checked. `mode` is shadow or enforce. */
  case class RateLimiterJsonV700(
    name: String,
    order: Int,
    error_code: String,
    enabled: Boolean,
    mode: String,
    keyed_by: String,
    runs: String,
    props_prefix: String,
    limits: List[RateLimiterLimitJsonV700]
  )
  case class RateLimitersJsonV700(rate_limiters: List[RateLimiterJsonV700])

  val rateLimitersJsonV700Example: RateLimitersJsonV700 = RateLimitersJsonV700(List(
    RateLimiterJsonV700("self_service", 1, "OBP-10060", enabled = true, "shadow", "client IP address",
      "before routing and before authentication, on the self-service endpoints", "self_service.rate_limit",
      List(RateLimiterLimitJsonV700("signup", per_minute = Some(3), per_hour = Some(5), per_day = Some(10), global_per_hour = Some(500)))),
    RateLimiterJsonV700("authentication", 2, "OBP-10061", enabled = false, "shadow", "client IP address and account",
      "inside the credential check of Direct Login, DAuth, Gateway Login and SIWE", "auth.rate_limit",
      List(RateLimiterLimitJsonV700("ip", per_minute = Some(10), per_hour = Some(100)), RateLimiterLimitJsonV700("account", per_minute = Some(6)))),
    RateLimiterJsonV700("consumer", 3, "OBP-10018", enabled = true, "enforce", "Consumer, or client IP address for anonymous calls",
      "after authentication, on every endpoint", "rate_limiting_per_*",
      List(RateLimiterLimitJsonV700("consumer_default", Some(-1), Some(-1), Some(-1), Some(-1), Some(-1), Some(-1)), RateLimiterLimitJsonV700("anonymous", per_hour = Some(1000))))
  ))

  /** The live configuration of the three rate limiters, from props and built-in defaults. */
  def createRateLimitersJsonV700(): RateLimitersJsonV700 = {
    def errorCode(msg: String): String = APIUtil.extractErrorMessageCode(msg)
    def prop(name: String, default: Long): Long = APIUtil.getPropsAsLongValue(name, default)
    def opt(v: Long): Option[Long] = Some(v)

    val selfService = RateLimiterJsonV700(
      name = "self_service", order = 1, error_code = errorCode(ErrorMessages.TooManyRequestsSelfService),
      enabled = SelfServiceRateLimiter.enabled, mode = SelfServiceRateLimiter.mode,
      keyed_by = "client IP address",
      runs = "before routing and before authentication, on the self-service endpoints",
      props_prefix = SelfServiceRateLimiter.PropsPrefix,
      limits = SelfServiceRateLimiter.scopeDefaults.keys.toList.sorted.map { scope =>
        RateLimiterLimitJsonV700(scope,
          per_minute = opt(SelfServiceRateLimiter.perKeyLimit(scope, "per_minute")),
          per_hour = opt(SelfServiceRateLimiter.perKeyLimit(scope, "per_hour")),
          per_day = opt(SelfServiceRateLimiter.perKeyLimit(scope, "per_day")),
          global_per_hour = opt(SelfServiceRateLimiter.globalPerHourLimit(scope)))
      }
    )
    val authentication = RateLimiterJsonV700(
      name = "authentication", order = 2, error_code = errorCode(ErrorMessages.TooManyRequestsAuth),
      enabled = AuthRateLimiter.enabled, mode = AuthRateLimiter.mode,
      keyed_by = "client IP address and account",
      runs = "inside the credential check of Direct Login, DAuth, Gateway Login and SIWE",
      props_prefix = AuthRateLimiter.PropsPrefix,
      limits = List(
        RateLimiterLimitJsonV700("ip", per_minute = opt(AuthRateLimiter.perIpPerMinute), per_hour = opt(AuthRateLimiter.perIpPerHour)),
        RateLimiterLimitJsonV700("account", per_minute = opt(AuthRateLimiter.perUserPerMinute)))
    )
    val consumer = RateLimiterJsonV700(
      name = "consumer", order = 3, error_code = errorCode(ErrorMessages.TooManyRequests),
      enabled = RateLimitingUtil.useConsumerLimits, mode = "enforce",
      keyed_by = "Consumer, or client IP address for anonymous calls",
      runs = "after authentication, on every endpoint",
      props_prefix = "rate_limiting_per_*",
      limits = List(
        // Props defaults apply to a Consumer with no rate limit rows; rows written by the management
        // endpoints and API Product Subscriptions override them per Consumer.
        RateLimiterLimitJsonV700("consumer_default",
          per_second = opt(prop("rate_limiting_per_second", -1)), per_minute = opt(prop("rate_limiting_per_minute", -1)),
          per_hour = opt(prop("rate_limiting_per_hour", -1)), per_day = opt(prop("rate_limiting_per_day", -1)),
          per_week = opt(prop("rate_limiting_per_week", -1)), per_month = opt(prop("rate_limiting_per_month", -1))),
        RateLimiterLimitJsonV700("anonymous", per_hour = opt(prop("user_consumer_limit_anonymous_access", 1000))))
    )
    RateLimitersJsonV700(List(selfService, authentication, consumer))
  }

  // Cached for server lifetime: ErrorMessages is a static catalog of `val X = "OBP-NNNNN: ..."`
  // strings, so reflecting over it once at first access is sufficient. Filters:
  //  - only String-typed fields (skips synthetic lazy-val bitmaps and helper defs)
  //  - only values starting with "OBP-" (skips helper strings that don't carry a code)
  lazy val errorMessagesCatalog: List[ErrorMessageEntryJsonV700] = {
    ErrorMessages.getClass.getDeclaredFields.toList
      .filter(f => f.getType == classOf[String])
      .flatMap { f =>
        f.setAccessible(true)
        Option(f.get(ErrorMessages)).collect { case s: String => s }
          .filter(_.startsWith("OBP-"))
          .map { msg =>
            val colonIdx = msg.indexOf(':')
            val (code, text) =
              if (colonIdx > 0) (msg.substring(0, colonIdx), msg.substring(colonIdx + 1).trim)
              else ("", msg)
            ErrorMessageEntryJsonV700(code = code, name = f.getName, message = text)
          }
      }
      .sortBy(e => (e.code, e.name))
  }


  case class APIInfoJsonV700(
    version: String,
    version_status: String,
    git_commit: String,
    stage: String,
    connector: String,
    hostname: String,
    local_identity_provider: String,
    hosted_by: HostedBy400,
    hosted_at: HostedAt400,
    energy_source: EnergySource400,
    resource_docs_requires_role: Boolean
  )

  def getApiInfoJSON(apiVersion: ApiVersion, apiVersionStatus: String): APIInfoJsonV700 = {
    val organisation = APIUtil.hostedByOrganisation
    val email = APIUtil.hostedByEmail
    val phone = APIUtil.hostedByPhone
    val organisationWebsite = APIUtil.organisationWebsite
    val hostedBy = new HostedBy400(organisation, email, phone, organisationWebsite)

    val organisationHostedAt = APIUtil.hostedAtOrganisation
    val organisationWebsiteHostedAt = APIUtil.hostedAtOrganisationWebsite
    val hostedAt = HostedAt400(organisationHostedAt, organisationWebsiteHostedAt)

    val organisationEnergySource = APIUtil.energySourceOrganisation
    val organisationWebsiteEnergySource = APIUtil.energySourceOrganisationWebsite
    val energySource = EnergySource400(organisationEnergySource, organisationWebsiteEnergySource)

    val connector = code.api.Constant.CONNECTOR.openOrThrowException(s"$MandatoryPropertyIsNotSet. The missing prop is `connector` ")
    val resourceDocsRequiresRole = APIUtil.resourceDocsRequiresRole

    APIInfoJsonV700(
      version = apiVersion.vDottedApiVersion,
      version_status = apiVersionStatus,
      git_commit = APIUtil.gitCommit,
      connector = connector,
      hostname = Constant.HostName,
      stage = System.getProperty("run.mode"),
      local_identity_provider = Constant.localIdentityProvider,
      hosted_by = hostedBy,
      hosted_at = hostedAt,
      energy_source = energySource,
      resource_docs_requires_role = resourceDocsRequiresRole
    )
  }

  // Trading JSON Models

  // Request Models
  case class CreateOfferRequestJson(
    offer_type: String,           // "BUY" | "SELL"
    asset_code: String,           // e.g., "OGCR"
    asset_amount: BigDecimal,     // e.g., 100.00
    price_currency: String,       // e.g., "EUR"
    price_amount: BigDecimal,     // e.g., 1.50
    settlement_account_id: String,
    expiry_datetime: Option[String] = None,  // ISO 8601
    minimum_fill: Option[BigDecimal] = None
  )

  case class UpdateOfferRequestJson(
    price_amount: Option[BigDecimal],
    expiry_datetime: Option[String],  // ISO 8601
    minimum_fill: Option[BigDecimal]
  )

  // Response Models
  case class TradingOfferJson(
    offer_id: String,
    status: String,
    offer_details: OfferDetailsJson,
    account_info: AccountInfoJson,
    executions: List[OfferExecutionJson],
    user_id: String,         // Audit field
    consent_id: Option[String],  // Audit field
    created_at: String,  // ISO 8601
    updated_at: String   // ISO 8601
  )

  case class OfferDetailsJson(
    offer_type: String,
    asset_code: String,
    asset_amount: BigDecimal,
    price_currency: String,
    price_amount: BigDecimal,
    settlement_account_id: String,
    expiry_datetime: Option[String],
    minimum_fill: Option[BigDecimal]
  )

  case class AccountInfoJson(
    bank_id: String,
    account_id: String,
    view_id: String
  )

  case class OfferExecutionJson(
    execution_id: String,
    executed_amount: BigDecimal,
    executed_price: BigDecimal,
    executed_at: String,  // ISO 8601
    counterpart_offer_id: String
  )

  case class CancelOfferResponseJson(
    offer_id: String,
    status: String
  )

  case class TradingOffersJson(
    offers: List[TradingOfferJson]
  )

  // Conversion Functions
  def createTradingOfferJson(offer: com.openbankproject.commons.model.TradingOffer): TradingOfferJson = {
    TradingOfferJson(
      offer_id = offer.offerId,
      status = offer.status,
      offer_details = OfferDetailsJson(
        offer_type = offer.offerType,
        asset_code = offer.offerDetails.assetCode,
        asset_amount = offer.offerDetails.assetAmount,
        price_currency = offer.offerDetails.priceCurrency,
        price_amount = offer.offerDetails.priceAmount,
        settlement_account_id = offer.offerDetails.settlementAccountId,
        expiry_datetime = offer.offerDetails.expiryDatetime.map(_.toInstant.toString),
        minimum_fill = offer.offerDetails.minimumFill
      ),
      account_info = AccountInfoJson(
        bank_id = offer.accountInfo.bankId,
        account_id = offer.accountInfo.accountId,
        view_id = offer.accountInfo.viewId
      ),
      executions = offer.executions.map(e => OfferExecutionJson(
        execution_id = e.executionId,
        executed_amount = e.executedAmount,
        executed_price = e.executedPrice,
        executed_at = e.executedAt.toInstant.toString,
        counterpart_offer_id = e.counterpartOfferId
      )),
      user_id = offer.userId,
      consent_id = offer.consentId,
      created_at = offer.createdAt.toInstant.toString,
      updated_at = offer.updatedAt.toInstant.toString
    )
  }

  def createCancelOfferResponseJson(offer: com.openbankproject.commons.model.TradingOffer): CancelOfferResponseJson = {
    CancelOfferResponseJson(
      offer_id = offer.offerId,
      status = offer.status
    )
  }

  // Market Trading JSON Models

  // Market Request Models
  case class CreateMarketOrderRequestJson(
    side: String,                 // "BUY" | "SELL"
    price: BigDecimal,
    quantity: BigDecimal,
    settlement_account_id: String
  )

  case class CreateMarketMatchRequestJson(
    order_id: String,
    counter_order_id: String,
    amount: BigDecimal,
    price: BigDecimal
  )

  case class RequestSettlementJson(
    trade_id: String,
    step: Option[String]
  )

  case class NotifyDepositJson(
    tx_hash: String,
    from: String,
    to: String,
    amount: BigDecimal,
    confirmations: Int
  )

  case class RequestWithdrawalJson(
    settlement_account_id: String,
    amount: BigDecimal,
    address: String
  )

  // Market Response Models
  case class MarketOrderJson(
    order_id: String,
    side: String,
    price: BigDecimal,
    quantity: BigDecimal,
    account_id: String,
    status: String,
    user_id: String,         // Audit field
    consent_id: Option[String],  // Audit field
    created_at: String,  // ISO 8601
    updated_at: String   // ISO 8601
  )

  case class MarketMatchJson(
    match_id: String,
    order_id: String,
    counter_order_id: String,
    amount: BigDecimal,
    price: BigDecimal,
    user_id: String,         // Audit field
    consent_id: Option[String],  // Audit field
    created_at: String  // ISO 8601
  )

  case class MarketTradeJson(
    trade_id: String,
    buy_order_id: String,
    sell_order_id: String,
    amount: BigDecimal,
    price: BigDecimal,
    status: String,
    user_id: String,         // Audit field
    consent_id: Option[String],  // Audit field
    created_at: String  // ISO 8601
  )

  case class SettlementJson(
    settlement_id: String,
    trade_id: String,
    step: Option[String],
    status: String,
    user_id: String,         // Audit field
    consent_id: Option[String],  // Audit field
    created_at: String,           // ISO 8601
    completed_at: Option[String]  // ISO 8601
  )

  case class DepositJson(
    deposit_id: String,
    tx_hash: String,
    from: String,
    to: String,
    amount: BigDecimal,
    confirmations: Int,
    required_confirmations: Int,  // Number of confirmations required
    status: String,
    nonce: Option[Long],          // Transaction nonce
    gas_used: Option[Long],       // Gas consumed
    error_message: Option[String], // Error details if failed
    user_id: String,              // Audit field
    consent_id: Option[String],   // Audit field
    created_at: String            // ISO 8601
  )

  case class WithdrawalJson(
    withdrawal_id: String,
    account_id: String,
    amount: BigDecimal,
    address: String,
    status: String,
    tx_hash: Option[String],
    confirmations: Option[Int],    // Current confirmations
    required_confirmations: Int,   // Required confirmations
    nonce: Option[Long],           // Transaction nonce
    gas_used: Option[Long],        // Gas consumed
    error_message: Option[String], // Error details if failed
    user_id: String,               // Audit field
    consent_id: Option[String],    // Audit field
    created_at: String             // ISO 8601
  )

  // TCC Payment Authorization Request/Response JSON
  case class CreatePaymentAuthRequestJson(
    trade_id: String,
    buyer_account_id: String,
    seller_account_id: String,
    amount_fiat: BigDecimal,
    currency: String
  )

  case class PaymentAuthJson(
    auth_id: String,
    trade_id: String,
    buyer_account_id: String,
    seller_account_id: String,
    amount_fiat: BigDecimal,
    currency: String,
    state: String,                 // PREAUTH | CAPTURED | RELEASED | FAILED
    hold_id: Option[String],       // Link to OBP Account Hold
    error_message: Option[String], // Error details if failed
    user_id: String,               // Audit field
    consent_id: Option[String],    // Audit field
    created_at: String,            // ISO 8601
    updated_at: String             // ISO 8601
  )

  // Market Conversion Functions
  def createMarketOrderJson(order: com.openbankproject.commons.model.MarketOrder): MarketOrderJson = {
    MarketOrderJson(
      order_id = order.orderId,
      side = order.side,
      price = order.price,
      quantity = order.quantity,
      account_id = order.accountId,
      status = order.status,
      user_id = order.userId,
      consent_id = order.consentId,
      created_at = order.createdAt.toInstant.toString,
      updated_at = order.updatedAt.toInstant.toString
    )
  }

  def createMarketMatchJson(marketMatch: com.openbankproject.commons.model.MarketMatch): MarketMatchJson = {
    MarketMatchJson(
      match_id = marketMatch.matchId,
      order_id = marketMatch.orderId,
      counter_order_id = marketMatch.counterOrderId,
      amount = marketMatch.amount,
      price = marketMatch.price,
      user_id = marketMatch.userId,
      consent_id = marketMatch.consentId,
      created_at = marketMatch.createdAt.toInstant.toString
    )
  }

  def createMarketTradeJson(trade: com.openbankproject.commons.model.MarketTrade): MarketTradeJson = {
    MarketTradeJson(
      trade_id = trade.tradeId,
      buy_order_id = trade.buyOrderId,
      sell_order_id = trade.sellOrderId,
      amount = trade.amount,
      price = trade.price,
      status = trade.status,
      user_id = trade.userId,
      consent_id = trade.consentId,
      created_at = trade.createdAt.toInstant.toString
    )
  }

  def createSettlementJson(settlement: com.openbankproject.commons.model.Settlement): SettlementJson = {
    SettlementJson(
      settlement_id = settlement.settlementId,
      trade_id = settlement.tradeId,
      step = settlement.step,
      status = settlement.status,
      user_id = settlement.userId,
      consent_id = settlement.consentId,
      created_at = settlement.createdAt.toInstant.toString,
      completed_at = settlement.completedAt.map(_.toInstant.toString)
    )
  }

  def createDepositJson(deposit: com.openbankproject.commons.model.Deposit): DepositJson = {
    DepositJson(
      deposit_id = deposit.depositId,
      tx_hash = deposit.txHash,
      from = deposit.from,
      to = deposit.to,
      amount = deposit.amount,
      confirmations = deposit.confirmations,
      required_confirmations = deposit.requiredConfirmations,
      status = deposit.status,
      nonce = deposit.nonce,
      gas_used = deposit.gasUsed,
      error_message = deposit.errorMessage,
      user_id = deposit.userId,
      consent_id = deposit.consentId,
      created_at = deposit.createdAt.toInstant.toString
    )
  }

  def createWithdrawalJson(withdrawal: com.openbankproject.commons.model.Withdrawal): WithdrawalJson = {
    WithdrawalJson(
      withdrawal_id = withdrawal.withdrawalId,
      account_id = withdrawal.accountId,
      amount = withdrawal.amount,
      address = withdrawal.address,
      status = withdrawal.status,
      tx_hash = withdrawal.txHash,
      confirmations = withdrawal.confirmations,
      required_confirmations = withdrawal.requiredConfirmations,
      nonce = withdrawal.nonce,
      gas_used = withdrawal.gasUsed,
      error_message = withdrawal.errorMessage,
      user_id = withdrawal.userId,
      consent_id = withdrawal.consentId,
      created_at = withdrawal.createdAt.toInstant.toString
    )
  }

  def createPaymentAuthJson(auth: com.openbankproject.commons.model.PaymentAuth): PaymentAuthJson = {
    PaymentAuthJson(
      auth_id = auth.authId,
      trade_id = auth.tradeId,
      buyer_account_id = auth.buyerAccountId,
      seller_account_id = auth.sellerAccountId,
      amount_fiat = auth.amountFiat,
      currency = auth.currency,
      state = auth.state,
      hold_id = auth.holdId,
      error_message = auth.errorMessage,
      user_id = auth.userId,
      consent_id = auth.consentId,
      created_at = auth.createdAt.toInstant.toString,
      updated_at = auth.updatedAt.toInstant.toString
    )
  }

  // Account-access decision diagnostic — returned by GET /banks/.../views/.../users/.../account-access-trace
  case class AccountAccessLookupJsonV700(
    has_account_access_for_view: Boolean,
    account_access_view_ids: List[String]
  )

  case class EntitlementTraceJsonV700(
    has_can_execute_abac_rule: Boolean
  )

  case class AbacRuleTraceJsonV700(
    rule_id: String,
    rule_name: String,
    is_active: Boolean,
    result: String,                 // "PASS" | "FAIL" | "ERROR"
    error_message: Option[String]
  )

  case class AbacEvaluationTraceJsonV700(
    policy: String,
    allow_abac_account_access: Boolean,
    standalone_abac_result: Boolean,
    rules_evaluated: List[AbacRuleTraceJsonV700]
  )

  case class AccountAccessTraceJsonV700(
    user_id: String,
    bank_id: String,
    account_id: String,
    view_id: String,
    has_access: Boolean,
    access_source: String,    // "ACCOUNT_ACCESS" | "ABAC" | "NONE"
    account_access_trace: AccountAccessLookupJsonV700,
    entitlement_trace: EntitlementTraceJsonV700,
    abac_trace: AbacEvaluationTraceJsonV700
  )

  // Organisation JSON case classes
  case class PostOrganisationJsonV700(
      organisation_id: String,
      name: String,
      website: Option[String],
      logo_url: Option[String],
      status: Option[String],
      visibility: Option[String]
  )

  case class PutOrganisationJsonV700(
      name: Option[String],
      website: Option[String],
      logo_url: Option[String],
      status: Option[String],
      visibility: Option[String]
  )

  case class OrganisationJsonV700(
      organisation_id: String,
      name: String,
      website: Option[String],
      logo_url: Option[String],
      status: String,
      visibility: String,
      created_by_user_id: String,
      created_at: java.util.Date,
      updated_at: java.util.Date
  )

  case class OrganisationsJsonV700(organisations: List[OrganisationJsonV700])

  def createOrganisationJsonV700(o: code.organisation.OrganisationTrait): OrganisationJsonV700 = {
    OrganisationJsonV700(
      organisation_id = o.organisationId,
      name = o.name,
      website = o.website,
      logo_url = o.logoUrl,
      status = o.status,
      visibility = o.visibility,
      created_by_user_id = o.createdByUserId,
      created_at = o.createdAt,
      updated_at = o.updatedAt
    )
  }

  def createOrganisationsJsonV700(orgs: List[code.organisation.OrganisationTrait]): OrganisationsJsonV700 = {
    OrganisationsJsonV700(orgs.map(createOrganisationJsonV700))
  }

  // ── Routing Scheme JSON case classes ─────────────────────────────────────────

  case class PostRoutingSchemeJsonV700(
      scheme: String,
      country: String,
      category: String,
      address_pattern: String,
      secondary_address_pattern: Option[String],
      example_address: String,
      description: String,
      downstream_rails: Option[List[String]],
      status: Option[String]
  )

  case class PutRoutingSchemeJsonV700(
      address_pattern: Option[String],
      secondary_address_pattern: Option[String],
      example_address: Option[String],
      description: Option[String],
      downstream_rails: Option[List[String]],
      status: Option[String]
  )

  // Full record returned on POST/GET-single/PUT.
  case class RoutingSchemeJsonV700(
      scheme: String,
      country: String,
      category: String,
      address_pattern: String,
      secondary_address_pattern: Option[String],
      example_address: String,
      description: String,
      downstream_rails: List[String],
      status: String,
      created_by_user_id: String,
      created_at: java.util.Date,
      updated_at: java.util.Date
  )

  // Trimmed record returned in list responses.
  case class RoutingSchemeSummaryJsonV700(
      scheme: String,
      country: String,
      category: String,
      status: String,
      address_pattern: String,
      example_address: String
  )

  case class RoutingSchemePaginationJsonV700(total: Int, limit: Int, offset: Int)

  case class RoutingSchemesJsonV700(
      routing_schemes: List[RoutingSchemeSummaryJsonV700],
      pagination: RoutingSchemePaginationJsonV700
  )

  case class BankSupportedRoutingSchemeJsonV700(
      scheme: String,
      bank_notes: Option[String]
  )

  case class BankSupportedRoutingSchemesJsonV700(
      bank_id: String,
      supported_routing_schemes: List[BankSupportedRoutingSchemeJsonV700]
  )

  case class PutBankSupportedRoutingSchemeJsonV700(
      bank_notes: Option[String],
      enabled: Option[Boolean]
  )

  def createRoutingSchemeJsonV700(r: code.routingscheme.RoutingSchemeTrait): RoutingSchemeJsonV700 =
    RoutingSchemeJsonV700(
      scheme = r.scheme,
      country = r.country,
      category = r.category,
      address_pattern = r.addressPattern,
      secondary_address_pattern = r.secondaryAddressPattern,
      example_address = r.exampleAddress,
      description = r.description,
      downstream_rails = r.downstreamRails,
      status = r.status,
      created_by_user_id = r.createdByUserId,
      created_at = r.createdAt,
      updated_at = r.updatedAt
    )

  def createRoutingSchemeSummaryJsonV700(r: code.routingscheme.RoutingSchemeTrait): RoutingSchemeSummaryJsonV700 =
    RoutingSchemeSummaryJsonV700(
      scheme = r.scheme,
      country = r.country,
      category = r.category,
      status = r.status,
      address_pattern = r.addressPattern,
      example_address = r.exampleAddress
    )

  def createRoutingSchemesJsonV700(
      rows: List[code.routingscheme.RoutingSchemeTrait],
      total: Int,
      limit: Int,
      offset: Int
  ): RoutingSchemesJsonV700 =
    RoutingSchemesJsonV700(
      routing_schemes = rows.map(createRoutingSchemeSummaryJsonV700),
      pagination = RoutingSchemePaginationJsonV700(total = total, limit = limit, offset = offset)
    )

  def createBankSupportedRoutingSchemesJsonV700(
      bankId: String,
      rows: List[code.routingscheme.BankSupportedRoutingSchemeTrait]
  ): BankSupportedRoutingSchemesJsonV700 =
    BankSupportedRoutingSchemesJsonV700(
      bank_id = bankId,
      supported_routing_schemes = rows.filter(_.enabled).map(r =>
        BankSupportedRoutingSchemeJsonV700(scheme = r.scheme, bank_notes = r.bankNotes)
      )
    )

  // ── Dynamic Glossary Item JSON case classes ─────────────────────────────────
  // Description is carried as markdown on the way in and returned as both markdown and rendered
  // html on the way out, matching GlossaryDescriptionJsonV300 as served by GET /api/glossary.

  case class PostGlossaryItemJsonV700(
      title: String,
      description: String
  )

  case class PutGlossaryItemJsonV700(
      description: String
  )

  case class GlossaryItemDescriptionJsonV700(markdown: String, html: String)

  case class GlossaryItemJsonV700(
      glossary_item_id: String,
      title: String,
      description: GlossaryItemDescriptionJsonV700,
      overrides_static_glossary_item: Boolean,
      created_by_user_id: String,
      created_at: java.util.Date,
      updated_at: java.util.Date
  )

  case class GlossaryItemPaginationJsonV700(total: Int, limit: Int, offset: Int)

  case class GlossaryItemsJsonV700(
      glossary_items: List[GlossaryItemJsonV700],
      pagination: GlossaryItemPaginationJsonV700
  )

  def createGlossaryItemJsonV700(r: code.glossaryitem.DynamicGlossaryItemTrait): GlossaryItemJsonV700 =
    GlossaryItemJsonV700(
      glossary_item_id = r.glossaryItemId,
      title = r.title,
      description = GlossaryItemDescriptionJsonV700(
        markdown = r.description,
        html = PegdownOptions.convertPegdownToHtmlTweaked(r.description)
      ),
      // Flagged so a caller can see at a glance that this item is shadowing shipped text.
      overrides_static_glossary_item =
        Glossary.glossaryItems.exists(_.title.toLowerCase == r.title.toLowerCase),
      created_by_user_id = r.createdByUserId,
      created_at = r.createdAt,
      updated_at = r.updatedAt
    )

  def createGlossaryItemsJsonV700(
      rows: List[code.glossaryitem.DynamicGlossaryItemTrait],
      total: Int,
      limit: Int,
      offset: Int
  ): GlossaryItemsJsonV700 =
    GlossaryItemsJsonV700(
      glossary_items = rows.map(createGlossaryItemJsonV700),
      pagination = GlossaryItemPaginationJsonV700(total = total, limit = limit, offset = offset)
    )

  // ── Qualified Identifier ────────────────────────────────────────────────────
  // A (scheme, value) triple where the scheme qualifies the value's namespace.
  // Used wherever the API takes or returns an identifier that belongs to a
  // registered routing-scheme: account routings, bill references, meter
  // numbers, KYC documents, etc.
  //
  // `fsp_id` is optional and only meaningful for multi-FSP namespaces where
  // the same value may live with different providers (e.g. mobile money:
  // TZ.MSISDN portability). When present, it participates in identity:
  // (scheme + value + fsp_id) uniquely picks one wallet; (scheme + value)
  // alone may not.
  case class QualifiedIdentifierJsonV700(
      scheme: String,
      value: String,
      fsp_id: Option[String] = None
  )

  // ── Payee Lookup JSON case classes ──────────────────────────────────────────

  case class PayeeIdentityJsonV700(`type`: String, value: String)

  case class PostPayeeLookupJsonV700(
      identifier: QualifiedIdentifierJsonV700
  )

  case class PayeeLookupResponseJsonV700(
      lookup_id: String,
      expires_at: java.util.Date,
      identifier: QualifiedIdentifierJsonV700,
      network_provider: Option[String],
      full_name: String,
      account_category: Option[String],
      account_type: Option[String],
      identity: Option[PayeeIdentityJsonV700]
  )

  // ── MOBILE_WALLET transaction-request body ─────────────────────────────────

  case class MobileWalletToJsonV700(
      msisdn: String,
      fsp_id: Option[String],
      network_provider: Option[String],
      full_name: Option[String],
      account_category: Option[String],
      account_type: Option[String],
      identity: Option[PayeeIdentityJsonV700]
  )

  case class MobileWalletDataFieldJsonV700(name: String, value: String)

  /**
   * Body for `POST .../transaction-request-types/MOBILE_WALLET/transaction-requests`.
   *
   * Implements `TransactionRequestCommonBodyJSON` so it plugs into the existing
   * v400 transaction-request pipeline (which requires `value` + `description`).
   */
  case class TransactionRequestBodyMobileWalletJsonV700(
      to: MobileWalletToJsonV700,
      value: com.openbankproject.commons.model.AmountOfMoneyJsonV121,
      description: String,
      client_reference: Option[String],
      verified_payee_lookup_id: Option[String],
      country_code: Option[String],
      data_fields: Option[List[MobileWalletDataFieldJsonV700]],
      charge_policy: Option[String]
  ) extends com.openbankproject.commons.model.TransactionRequestCommonBodyJSON

  // v7 response shape for MOBILE_WALLET. Mirrors v4's wrapper but binds `details`
  // to the type-specific request body so resource-doc examples and the live
  // response no longer advertise the legacy `TransactionRequestBodyAllTypes` union.
  case class TransactionRequestWithChargeMobileWalletJsonV700(
      id: String,
      `type`: String,
      from: code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140,
      details: TransactionRequestBodyMobileWalletJsonV700,
      transaction_ids: List[String],
      status: String,
      start_date: java.util.Date,
      end_date: java.util.Date,
      challenges: List[code.api.v4_0_0.ChallengeJsonV400],
      charge: code.api.v2_0_0.TransactionRequestChargeJsonV200,
      attributes: Option[List[code.api.v4_0_0.BankAttributeBankResponseJsonV400]]
  )

  def createTransactionRequestWithChargeMobileWalletJsonV700(
      tr: com.openbankproject.commons.model.TransactionRequest,
      requestBody: TransactionRequestBodyMobileWalletJsonV700,
      challenges: List[com.openbankproject.commons.model.ChallengeTrait],
      transactionRequestAttribute: List[com.openbankproject.commons.model.TransactionRequestAttributeTrait]
  ): TransactionRequestWithChargeMobileWalletJsonV700 = {
    val v4 = code.api.v4_0_0.JSONFactory400.createTransactionRequestWithChargeJSON(
      tr, challenges, transactionRequestAttribute
    )
    TransactionRequestWithChargeMobileWalletJsonV700(
      id = v4.id,
      `type` = v4.`type`,
      from = v4.from,
      details = requestBody,
      transaction_ids = v4.transaction_ids,
      status = v4.status,
      start_date = v4.start_date,
      end_date = v4.end_date,
      challenges = v4.challenges,
      charge = v4.charge,
      attributes = v4.attributes
    )
  }

  // ── UTILITY transaction-request body ───────────────────────────────────────
  //
  // A polymorphic bill / utility payment. The destination is a QualifiedIdentifier
  // whose `scheme` must be a registered routing scheme of category UTILITY or BILL
  // — e.g. `TZ.UTILITY_METER` (prepaid electricity meter), later `TZ.BILL_CONTROL_NUMBER`.
  // Mirrors the meter/bill token-purchase flow: verify the destination via
  // POST .../payees/lookup, then pay quoting `verified_payee_lookup_id`.

  /** Payer block — the depositor's phone / name / email for the biller receipt. */
  case class UtilityPayerJsonV700(
      phone: Option[String],
      name: Option[String],
      email: Option[String]
  )

  /**
   * Body for `POST .../transaction-request-types/UTILITY/transaction-requests`.
   *
   * Implements `TransactionRequestCommonBodyJSON` so it plugs into the existing
   * v400 transaction-request pipeline (which requires `value` + `description`).
   *
   * `callback_url`, when present, registers a fire-and-forget callback that OBP
   * POSTs the final token-purchase result to.
   */
  case class TransactionRequestBodyUtilityJsonV700(
      to: QualifiedIdentifierJsonV700,
      value: com.openbankproject.commons.model.AmountOfMoneyJsonV121,
      description: String,
      client_reference: Option[String],
      verified_payee_lookup_id: Option[String],
      payer: Option[UtilityPayerJsonV700],
      callback_url: Option[String],
      data_fields: Option[List[MobileWalletDataFieldJsonV700]],
      charge_policy: Option[String]
  ) extends com.openbankproject.commons.model.TransactionRequestCommonBodyJSON

  /** Registration status of the per-request callback (step c). */
  case class UtilityCallbackJsonV700(
      callback_id: String,
      callback_url: String,
      status: String                        // REGISTERED | DELIVERED | FAILED
  )

  // The asynchronous vend result delivered by the downstream rail/adapter after the
  // utility purchase settles — e.g. the STS token (typically 20 digits) for a prepaid
  // electricity meter. Persisted on the transaction request as attributes and surfaced
  // here (and on the client callback) once the vend completes.
  case class UtilityVendResultJsonV700(
      status: String,                        // ACCEPTED | COMPLETED | FAILED (provider vend status)
      token: Option[String],                 // the STS token the customer keys into the meter (e.g. 20 digits)
      rcpt_num: Option[String],              // provider receipt number
      units: Option[String],                 // units purchased (e.g. electricity kWh)
      provider_reference: Option[String],    // downstream rail / provider reference
      provider_message: Option[String]       // free-text provider remark
  )

  /** Inbound body for the vend-result delivery endpoint (rail/adapter → OBP). */
  case class PostUtilityVendResultJsonV700(
      status: String,
      token: Option[String],
      rcpt_num: Option[String],
      units: Option[String],
      provider_reference: Option[String],
      provider_message: Option[String]
  )

  // Response of the vend-result delivery endpoint, and the payload OBP POSTs to the
  // payer's registered callback_url. Deliberately lean — it carries the vend result
  // (the token), not an echo of the original request (the payer already has that from
  // the create response).
  case class UtilityVendResultResponseJsonV700(
      transaction_request_id: String,
      `type`: String,                       // always "UTILITY"
      status: String,                       // the transaction request's status
      vend_result: Option[UtilityVendResultJsonV700],
      callback: Option[UtilityCallbackJsonV700]   // delivery status, when a callback was registered
  )

  // Attribute names under which the vend result is persisted on the transaction request.
  object UtilityVendAttribute {
    val Token             = "UTILITY_VEND_TOKEN"
    val RcptNum           = "UTILITY_VEND_RCPT_NUM"
    val Units             = "UTILITY_VEND_UNITS"
    val ProviderReference = "UTILITY_VEND_PROVIDER_REFERENCE"
    val VendStatus        = "UTILITY_VEND_STATUS"
    val ProviderMessage   = "UTILITY_VEND_PROVIDER_MESSAGE"
  }

  // v7 response shape for UTILITY. Mirrors MOBILE_WALLET's wrapper and adds the
  // optional callback-registration block and the asynchronous vend result.
  case class TransactionRequestWithChargeUtilityJsonV700(
      id: String,
      `type`: String,
      from: code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140,
      details: TransactionRequestBodyUtilityJsonV700,
      transaction_ids: List[String],
      status: String,
      start_date: java.util.Date,
      end_date: java.util.Date,
      challenges: List[code.api.v4_0_0.ChallengeJsonV400],
      charge: code.api.v2_0_0.TransactionRequestChargeJsonV200,
      callback: Option[UtilityCallbackJsonV700],
      vend_result: Option[UtilityVendResultJsonV700],
      attributes: Option[List[code.api.v4_0_0.BankAttributeBankResponseJsonV400]]
  )

  def createTransactionRequestWithChargeUtilityJsonV700(
      tr: com.openbankproject.commons.model.TransactionRequest,
      requestBody: TransactionRequestBodyUtilityJsonV700,
      callback: Option[UtilityCallbackJsonV700],
      vendResult: Option[UtilityVendResultJsonV700],
      challenges: List[com.openbankproject.commons.model.ChallengeTrait],
      transactionRequestAttribute: List[com.openbankproject.commons.model.TransactionRequestAttributeTrait]
  ): TransactionRequestWithChargeUtilityJsonV700 = {
    val v4 = code.api.v4_0_0.JSONFactory400.createTransactionRequestWithChargeJSON(
      tr, challenges, transactionRequestAttribute
    )
    TransactionRequestWithChargeUtilityJsonV700(
      id = v4.id,
      `type` = v4.`type`,
      from = v4.from,
      details = requestBody,
      transaction_ids = v4.transaction_ids,
      status = v4.status,
      start_date = v4.start_date,
      end_date = v4.end_date,
      challenges = v4.challenges,
      charge = v4.charge,
      callback = callback,
      vend_result = vendResult,
      attributes = v4.attributes
    )
  }

  /** Build the typed vend-result block from the transaction request's persisted attributes.
    * Returns None when no vend has been recorded yet. */
  def utilityVendResultFromAttributes(
      attributes: List[com.openbankproject.commons.model.TransactionRequestAttributeTrait]
  ): Option[UtilityVendResultJsonV700] = {
    val byName = attributes.map(a => a.name -> a.value).toMap
    byName.get(UtilityVendAttribute.VendStatus).map { status =>
      UtilityVendResultJsonV700(
        status = status,
        token = byName.get(UtilityVendAttribute.Token),
        rcpt_num = byName.get(UtilityVendAttribute.RcptNum),
        units = byName.get(UtilityVendAttribute.Units),
        provider_reference = byName.get(UtilityVendAttribute.ProviderReference),
        provider_message = byName.get(UtilityVendAttribute.ProviderMessage)
      )
    }
  }

  // ── BULK transaction-request body ─────────────────────────────────────────

  case class BulkPaymentItemJsonV700(
      end_to_end_id: String,
      to_account_routing: com.openbankproject.commons.model.AccountRoutingJsonV121,
      value: com.openbankproject.commons.model.AmountOfMoneyJsonV121,
      description: String
  )

  /**
   * Body for `POST .../transaction-request-types/BULK/transaction-requests`.
   *
   * `value` and `description` at this level are the **batch-level rollups** —
   * `value` is the sum of all items' amounts (server-validated), and `description`
   * is a free-text label for the batch. Required because we plug into the existing
   * v400 transaction-request pipeline via `TransactionRequestCommonBodyJSON`.
   */
  case class TransactionRequestBodyBulkJsonV700(
      batch_reference: String,
      payments: List[BulkPaymentItemJsonV700],
      requested_execution_date: Option[java.util.Date],
      value: com.openbankproject.commons.model.AmountOfMoneyJsonV121,
      description: String,
      charge_policy: Option[String]
  ) extends com.openbankproject.commons.model.TransactionRequestCommonBodyJSON

  case class BulkPaymentItemResultJsonV700(
      end_to_end_id: String,
      to_account_routing: com.openbankproject.commons.model.AccountRoutingJsonV121,
      value: com.openbankproject.commons.model.AmountOfMoneyJsonV121,
      status: String,                       // SUCCEEDED | FAILED | PENDING
      transaction_id: Option[String],
      failure_reason: Option[String]
  )

  case class BulkTransactionRequestResponseJsonV700(
      id: String,                            // OBP transaction_request_id
      batch_reference: String,               // caller-supplied
      status: String,                        // batch-level rollup: COMPLETED | PARTIALLY_COMPLETED | FAILED | INITIATED
      from: code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140,
      total_value: com.openbankproject.commons.model.AmountOfMoneyJsonV121,
      total_payments: Int,
      succeeded_count: Int,
      failed_count: Int,
      payments: List[BulkPaymentItemResultJsonV700],
      transaction_ids: List[String],
      start_date: java.util.Date,
      end_date: java.util.Date
  )

  def createBulkTransactionRequestResponseJsonV700(
      tr: com.openbankproject.commons.model.TransactionRequest,
      batchReference: String,
      results: List[code.bulkpayment.BulkPaymentTrait]
  ): BulkTransactionRequestResponseJsonV700 = {
    val v4From = code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140(
      bank_id = tr.from.bank_id, account_id = tr.from.account_id
    )
    val succeeded = results.count(_.status == "SUCCEEDED")
    val failed    = results.count(_.status == "FAILED")
    val total = tr.body.value
    BulkTransactionRequestResponseJsonV700(
      id = tr.id.value,
      batch_reference = batchReference,
      status = tr.status,
      from = v4From,
      total_value = com.openbankproject.commons.model.AmountOfMoneyJsonV121(
        currency = total.currency, amount = total.amount
      ),
      total_payments = results.size,
      succeeded_count = succeeded,
      failed_count = failed,
      payments = results.map { p =>
        BulkPaymentItemResultJsonV700(
          end_to_end_id = p.endToEndId,
          to_account_routing = com.openbankproject.commons.model.AccountRoutingJsonV121(
            scheme = p.routingScheme, address = p.address
          ),
          value = com.openbankproject.commons.model.AmountOfMoneyJsonV121(currency = p.currency, amount = p.amount),
          status = p.status,
          transaction_id = p.transactionId,
          failure_reason = p.failureReason
        )
      },
      transaction_ids = Option(tr.transaction_ids).getOrElse("").split(",").toList.map(_.trim).filter(_.nonEmpty),
      start_date = tr.start_date,
      end_date = tr.end_date
    )
  }

  // ─── OPEN_CORRIDOR_PROMISE Transaction Request type ────────────────────────
  //
  // SIMPLE-shaped beneficiary routing plus a REQUIRED `originator` block carrying
  // FATF Recommendation 16 (Travel Rule) information about the actual payer. The
  // originator is supplied explicitly on the create body and validated by the
  // OpenCorridorProcessor.

  case class TransactionRequestBodyOpenCorridorJsonV700(
    to: PostSimpleCounterpartyJson400,
    value: AmountOfMoneyJsonV121,
    description: String,
    charge_policy: String,
    originator: com.openbankproject.commons.model.TransactionRequestOriginator,
    future_date: Option[String] = None,
    /// Set when this promise is a RETURN of an earlier promise whose credit
    /// the beneficiary bank's CBS refused: the original transaction_request_id.
    /// Carried opaque onto the credit notification so the receiving bank knows
    /// it is being repaid (and never returns a return — one hop only).
    return_of: Option[String] = None
  ) extends TransactionRequestCommonBodyJSON

  // Outbound originator block emitted on v7 TR responses. `source` discriminates:
  //   - "explicit"      — taken from the TR's persisted originator fields
  //   - "customer_link" — virtually filled at read time from customer_account_link
  case class TransactionRequestOriginatorJsonV700(
    name: String,
    address: String,
    account_routing: TransactionRequestOriginatorAccountRoutingJsonV700,
    source: String
  )

  case class TransactionRequestOriginatorAccountRoutingJsonV700(
    scheme: String,
    address: String
  )

  // OPEN_CORRIDOR_PROMISE response wrapper — v4 TransactionRequestWithChargeJSON400 shape
  // plus the originator block. `originator` is None when there's no explicit value
  // stored AND no customer_account_link for the from-account; serializes as null.
  case class TransactionRequestWithChargeOpenCorridorJsonV700(
    id: String,
    `type`: String,
    from: code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140,
    details: TransactionRequestBodyOpenCorridorJsonV700,
    transaction_ids: List[String],
    status: String,
    start_date: java.util.Date,
    end_date: java.util.Date,
    challenges: List[code.api.v4_0_0.ChallengeJsonV400],
    charge: code.api.v2_0_0.TransactionRequestChargeJsonV200,
    originator: Option[TransactionRequestOriginatorJsonV700]
  )

  def createTransactionRequestWithChargeOpenCorridorJsonV700(
    tr: com.openbankproject.commons.model.TransactionRequest,
    requestBody: TransactionRequestBodyOpenCorridorJsonV700,
    originator: Option[TransactionRequestOriginatorJsonV700],
    challenges: List[com.openbankproject.commons.model.ChallengeTrait]
  ): TransactionRequestWithChargeOpenCorridorJsonV700 = {
    val v4 = code.api.v4_0_0.JSONFactory400.createTransactionRequestWithChargeJSON(tr, challenges, Nil)
    TransactionRequestWithChargeOpenCorridorJsonV700(
      id = v4.id,
      `type` = v4.`type`,
      from = v4.from,
      details = requestBody,
      // The v4 factory emits List("") for a TR with no transactions; a held promise
      // genuinely has none, so v7 emits a truly empty list instead.
      transaction_ids = v4.transaction_ids.filter(_.trim.nonEmpty),
      status = v4.status,
      start_date = v4.start_date,
      end_date = v4.end_date,
      challenges = v4.challenges,
      charge = v4.charge,
      originator = originator
    )
  }

  // ─── OPEN_CORRIDOR promise report-back (salt relay intake) ─────────────────
  //
  // After the Bank Node writes the Promise commitment to Cardano, it reports the
  // on-chain references and the commit–reveal evidence back so OBP-API can relay
  // the salt to the beneficiary bank in `obp_credit_notification`. The evidence
  // fields are opaque to OBP-API: it stores and relays them, it never needs to
  // parse the preimage.

  // `tx_hash` is deliberately chain-neutral: the chain is identified by `blockchain`
  // (e.g. "cardano"), so the hash field must not bake a chain name in.
  case class PostOpenCorridorPromiseJsonV700(
    tx_hash: String,
    blockchain: String,
    commitment: String,
    salt: String,
    preimage: String
  )

  case class OpenCorridorPromiseJsonV700(
    transaction_request_id: String,
    transaction_request_status: String,
    tx_hash: String,
    blockchain: String,
    commitment: String,
    salt: String,
    preimage: String,
    reported_by_user_id: String,
    reported_at: String
  )

  // ─── Create Account ────────────────────────────────────────────────────────

  /** Request body for POST /banks/BANK_ID/accounts (server-generated id) and
    * PUT /banks/BANK_ID/accounts/ACCOUNT_ID (caller-chosen id). The OBP-family
    * routing schemes (OBP, OBP_ACCOUNT_ID) are implicit — supplying one in
    * account_routings is refused; the canonical OBP routing is derived from
    * the account id on every read. */
  case class CreateAccountRequestJsonV700(
    user_id: Option[String],
    label: String,
    product_code: String,
    balance: AmountOfMoneyJsonV121,
    branch_id: Option[String],
    account_routings: Option[List[AccountRoutingJsonV121]]
  )

  case class CreateAccountResponseJsonV700(
    account_id: String,
    bank_id: String,
    user_id: String,
    label: String,
    product_code: String,
    balance: AmountOfMoneyJsonV121,
    branch_id: String,
    account_routings: List[AccountRoutingJsonV121],
    account_attributes: List[AccountAttributeResponseJson]
  )

  def createAccountJsonV700(
    userId: String,
    account: BankAccount,
    accountAttributes: List[AccountAttribute]
  ): CreateAccountResponseJsonV700 =
    CreateAccountResponseJsonV700(
      account_id = account.accountId.value,
      bank_id = account.bankId.value,
      user_id = userId,
      label = account.label,
      product_code = account.accountType,
      balance = AmountOfMoneyJsonV121(account.currency, account.balance.toString()),
      branch_id = account.branchId,
      account_routings = Constant.accountRoutingsWithImplicitOBP(
        account.accountId.value,
        account.accountRoutings.map(r => AccountRoutingJsonV121(r.scheme, r.address))
      ),
      account_attributes = accountAttributes.map(JSONFactory310.createAccountAttributeJson)
    )

  // ─── OPEN_CORRIDOR per-bank broker registry (admin) ────────────────────────

  // Transport coordinates only. The settlement address is NOT part of the broker
  // record: it is the CARDANO account routing on OBP-INCOMING-SETTLEMENT-ACCOUNT.
  case class PostAmqpBankBrokerJsonV700(
    host: String,
    port: Int,
    virtual_host: String,
    username: String,
    password: String,
    use_ssl: Boolean
  )

  // The password is write-only: never echoed on any response.
  case class AmqpBankBrokerJsonV700(
    bank_id: String,
    host: String,
    port: Int,
    virtual_host: String,
    username: String,
    use_ssl: Boolean
  )

  // ─── Message outbox (operator) ─────────────────────────────────────────────

  /** One message-outbox row. `subject_id`/`subject_id_type` name the business
    * object the message is about (NOT the per-REST-call Correlation-Id). The
    * wire payload is deliberately NOT exposed here: it can carry commit–reveal
    * evidence and originator PII. */
  case class MessageOutboxRowJsonV700(
    outbox_id: Long,
    outbox_type: String,
    subject_id: String,
    subject_id_type: String,
    operation_name: String,
    target_id: String,
    status: String,
    attempts: Int,
    last_error: String,
    created_at: String,
    updated_at: String
  )

  case class MessageOutboxJsonV700(rows: List[MessageOutboxRowJsonV700])

  def createMessageOutboxRowJson(
    row: code.messageoutbox.MessageOutbox
  ): MessageOutboxRowJsonV700 =
    MessageOutboxRowJsonV700(
      outbox_id = row.id.get,
      outbox_type = row.outboxType,
      subject_id = row.subjectId,
      subject_id_type = row.subjectIdType,
      operation_name = row.operationName,
      target_id = row.targetId,
      status = row.status,
      attempts = row.attempts,
      last_error = row.LastError.get,
      created_at = APIUtil.DateWithMsFormat.format(row.CreatedAt.get),
      updated_at = APIUtil.DateWithMsFormat.format(row.UpdatedAt.get)
    )

  // ─── OPEN_CORRIDOR settlements ─────────────────────────────────────────────

  /** POST /banks/BANK_ID/open-corridor/settlements: the URL bank is one side of
    * the pair, the body names the other. The caller's role is checked at the
    * URL bank, so a bank can only trigger settlement of corridors it is party to. */
  case class PostOpenCorridorSettlementJsonV700(
    other_bank_id: String,
    currency: String
  )

  /** Body of the platform fee sweep trigger (fee-settlements). */
  case class PostOpenCorridorFeeSettlementJsonV700(
    currency: String
  )

  /**
   * Result of a settle-pair run. `transaction_id` is empty when the pair's flows
   * offset exactly (net zero: promises are discharged, nothing moves) and when
   * `covered_transaction_request_ids` is empty (no-op: nothing was pending).
   * NOTE: the posted net Transaction deliberately does NOT match any single
   * promise — it is the offset difference, between the settlement accounts,
   * possibly opposite in direction to a given covered promise. Reconciliation
   * must use `settled_by_transaction_ids` on each promise, never assume the
   * Transaction mirrors the promise body.
   */
  case class OpenCorridorSettleResultJsonV700(
    settlement_id: String,
    settlement_transaction_request_id: String,
    transaction_id: String,
    debtor_bank_id: String,
    creditor_bank_id: String,
    currency: String,
    net_amount: String,
    covered_transaction_request_ids: List[String],
    settlement_advices_enqueued: Int,
    settlement_instructions_enqueued: Int
  )

  /** One Interface C outbox message belonging to a settlement, for the GET
    * status view. `delivery_status` is the outbox row lifecycle
    * (PENDING / DELIVERED / STICKY), not the rail state. */
  case class OpenCorridorSettlementMessageJsonV700(
    operation_name: String,
    target_bank_id: String,
    delivery_status: String,
    attempts: Int,
    last_error: String
  )

  /**
   * GET view of one settlement. The ledger side (`ledger_status`) completes at
   * settle time; the rail side (`settlement_status`) completes only when the
   * debtor bank's node reports FINAL via the outbox relay's redelivery poll:
   *   NET_ZERO   — flows offset exactly; nothing to move on any rail
   *   INSTRUCTED — instruction enqueued, no node reply recorded yet
   *   SETTLING / SUBMITTED — the node's last reported rail state (with
   *                `settlement_depth` = confirmation depth when reported)
   *   FINAL      — the node reported finality; the instruction row is DELIVERED
   *   ERROR      — the node replied with a non-retryable error (row STICKY);
   *                operator reconciliation required, see the message's last_error
   */
  case class OpenCorridorSettlementStatusJsonV700(
    settlement_id: String,
    debtor_bank_id: String,
    creditor_bank_id: String,
    currency: String,
    net_amount: String,
    transaction_id: String,
    ledger_status: String,
    settlement_status: String,
    settlement_depth: Option[Int],
    covered_transaction_request_ids: List[String],
    messages: List[OpenCorridorSettlementMessageJsonV700]
  )

  // Build the originator block for a TR response. Returns None when there's no
  // explicit originator and no customer_account_link for the from-account — the
  // outer JSON wrapper emits `originator: null` in that case.
  def buildTransactionRequestOriginatorJson(
    tr: TransactionRequest,
    callContext: Option[CallContext]
  )(implicit ec: ExecutionContext): Future[(Option[TransactionRequestOriginatorJsonV700], Option[CallContext])] = {
    tr.originator match {
      case Some(o) =>
        Future.successful((
          Some(TransactionRequestOriginatorJsonV700(
            name = o.name,
            address = o.address,
            account_routing = TransactionRequestOriginatorAccountRoutingJsonV700(
              scheme = o.account_routing.scheme,
              address = o.account_routing.address
            ),
            source = "explicit"
          )),
          callContext
        ))
      case None =>
        Connector.connector.vend.getCustomerAccountLinksByBankIdAccountId(
          tr.from.bank_id,
          tr.from.account_id,
          callContext
        ).map {
          case (Full(link :: _), cc) =>
            CustomerX.customerProvider.vend.getCustomerByCustomerId(link.customerId) match {
              case Full(customer) =>
                (Some(TransactionRequestOriginatorJsonV700(
                  name = customer.legalName,
                  address = "", // TODO derive from CustomerAddress (multi-record, separate model)
                  account_routing = TransactionRequestOriginatorAccountRoutingJsonV700(scheme = "", address = ""),
                  source = "customer_link"
                )), cc)
              case _ =>
                (None, cc)
            }
          case (_, cc) =>
            (None, cc)
        }
    }
  }

  // ─── Core accounts at all banks (v7 rename: id → account_id / views[].id → view_id) ──

  case class ViewBasicV700(
    view_id: String,
    short_name: String,
    description: String,
    is_public: Boolean
  )

  case class CoreAccountJsonV700(
    account_id: String,
    label: String,
    bank_id: String,
    account_type: String,
    account_routings: List[AccountRoutingJsonV121],
    views: List[ViewBasicV700]
  )

  case class CoreAccountsJsonV700(accounts: List[CoreAccountJsonV700])

  def createCoreAccountsByCoreAccountsJsonV700(
    coreAccounts: List[CoreAccount],
    user: User
  ): CoreAccountsJsonV700 =
    CoreAccountsJsonV700(coreAccounts.map { coreAccount =>
      CoreAccountJsonV700(
        account_id = coreAccount.id,
        label = coreAccount.label,
        bank_id = coreAccount.bankId,
        account_type = coreAccount.accountType,
        account_routings = coreAccount.accountRoutings.map(r =>
          AccountRoutingJsonV121(r.scheme, r.address)),
        views = Views.views.vend
          .privateViewsUserCanAccessForAccount(
            user, BankIdAccountId(BankId(coreAccount.bankId), AccountId(coreAccount.id)))
          .filter(_.isPrivate)
          .map(v => ViewBasicV700(
            view_id = v.viewId.value,
            short_name = v.name,
            description = v.description,
            is_public = v.isPublic
          ))
      )
    })

  lazy val viewBasicV700Example = ViewBasicV700(
    view_id = "owner",
    short_name = "Owner",
    description = "Owner View",
    is_public = false
  )

  lazy val coreAccountJsonV700Example = CoreAccountJsonV700(
    account_id = "f026fbd3-d1ea-496b-a853-3cbe65629881",
    label = "Account 1",
    bank_id = "smnr.bnk.1",
    account_type = "330",
    account_routings = List(AccountRoutingJsonV121("IBAN", "DE89 3704 0044 0532 0130 00")),
    views = List(viewBasicV700Example)
  )

  lazy val coreAccountsJsonV700Example =
    CoreAccountsJsonV700(accounts = List(coreAccountJsonV700Example))

  // ─── Consents config — operator-published policy clients need before issuing a consent ──

  case class ConsentsConfigJsonV700(
    consents_allowed: Boolean,
    max_time_to_live_in_seconds: Int,
    sca_enabled: Boolean
  )

  lazy val consentsConfigJsonV700Example = ConsentsConfigJsonV700(
    consents_allowed = true,
    max_time_to_live_in_seconds = code.api.Constant.DEFAULT_CONSENT_TTL,
    sca_enabled = true
  )

  // ─── Consumer rate limits across all consumers — what overrides the consumer limiter's defaults ──

  case class ConsumerRateLimitJsonV700(
    rate_limiting_id: String,
    consumer_id: String,
    consumer_name: String,
    api_version: Option[String],
    api_name: Option[String],
    bank_id: Option[String],
    from_date: java.util.Date,
    to_date: java.util.Date,
    is_active: Boolean,
    per_second_call_limit: String,
    per_minute_call_limit: String,
    per_hour_call_limit: String,
    per_day_call_limit: String,
    per_week_call_limit: String,
    per_month_call_limit: String,
    created_at: java.util.Date,
    updated_at: java.util.Date
  )
  case class ConsumerRateLimitsJsonV700(rate_limits: List[ConsumerRateLimitJsonV700])

  def createConsumerRateLimitJsonV700(r: code.ratelimiting.RateLimiting, consumerName: String, now: java.util.Date): ConsumerRateLimitJsonV700 =
    ConsumerRateLimitJsonV700(
      rate_limiting_id = r.rateLimitingId,
      consumer_id = r.consumerId,
      consumer_name = consumerName,
      api_version = r.apiVersion,
      api_name = r.apiName,
      bank_id = r.bankId,
      from_date = r.fromDate,
      to_date = r.toDate,
      is_active = !now.before(r.fromDate) && !now.after(r.toDate),
      per_second_call_limit = r.perSecondCallLimit.toString,
      per_minute_call_limit = r.perMinuteCallLimit.toString,
      per_hour_call_limit = r.perHourCallLimit.toString,
      per_day_call_limit = r.perDayCallLimit.toString,
      per_week_call_limit = r.perWeekCallLimit.toString,
      per_month_call_limit = r.perMonthCallLimit.toString,
      created_at = r.createdAt.get,
      updated_at = r.updatedAt.get
    )

  lazy val consumerRateLimitsJsonV700Example = ConsumerRateLimitsJsonV700(List(ConsumerRateLimitJsonV700(
    rate_limiting_id = "2f1b6c0e-9d5a-4c3b-8e7f-1a2b3c4d5e6f",
    consumer_id = "8e716299-4668-4efd-976a-67f57a9984ec",
    consumer_name = "Mobile App",
    api_version = None,
    api_name = None,
    bank_id = None,
    from_date = APIUtil.DateWithDayExampleObject,
    to_date = APIUtil.DateWithDayExampleObject,
    is_active = true,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "1000",
    per_day_call_limit = "10000",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1",
    created_at = APIUtil.DateWithDayExampleObject,
    updated_at = APIUtil.DateWithDayExampleObject
  )))

  // ─── Dynamic resource doc dry-run compile ─────────────────────────────────

  /** Request: the parts of a Dynamic Resource Doc that shape the compiled code. */
  case class DynamicResourceDocCompileJsonV700(
    request_verb: String,
    request_url: String,
    method_body: String,
    example_request_body: Option[JValue],
    success_response_body: Option[JValue]
  )
  case class DynamicCompileErrorJsonV700(line: Int, column: Int, severity: String, message: String)
  case class DynamicCompileResultJsonV700(
    compiles: Boolean,
    errors: List[DynamicCompileErrorJsonV700],
    dependency_error: Option[String],
    duration_ms: Long
  )
  lazy val dynamicResourceDocCompileJsonV700Example = DynamicResourceDocCompileJsonV700(
    request_verb = "GET",
    request_url = "/hello/world",
    method_body = java.net.URLEncoder.encode("Future.successful((Map(\"hello\" -> \"world\"), HttpCode.`200`(callContext)))", "UTF-8"),
    example_request_body = None,
    success_response_body = Some(org.json4s.JsonAST.JObject(List(org.json4s.JsonAST.JField("hello", org.json4s.JsonAST.JString("world")))))
  )
  lazy val dynamicCompileResultJsonV700Example = DynamicCompileResultJsonV700(
    compiles = false,
    errors = List(DynamicCompileErrorJsonV700(1, 24, "ERROR", "not found: value Full")),
    dependency_error = None,
    duration_ms = 850
  )

  // ─── Dynamic code approval config — whether maker/checker gates dynamic artefacts on this instance ──

  case class DynamicCodeApprovalConfigJsonV700(
    dynamic_code_execution_enabled: Boolean,
    requires_approval: Boolean,
    target_types: List[String],
    delete_requires_approval: Boolean,
    request_ttl_hours: Int,
    approval_role: String
  )

  lazy val dynamicCodeApprovalConfigJsonV700Example = DynamicCodeApprovalConfigJsonV700(
    dynamic_code_execution_enabled = true,
    requires_approval = true,
    target_types = List("DYNAMIC_RESOURCE_DOC", "DYNAMIC_MESSAGE_DOC", "CONNECTOR_METHOD", "ABAC_RULE"),
    delete_requires_approval = true,
    request_ttl_hours = 168,
    approval_role = "CanApproveDynamicChangeRequest"
  )

  // ─── User JSON — v7 adds the user's own OBP-verified mobile phone fields ───────
  // Distinct from Customer.mobile_phone_number (bank-scoped KYC data): this is the
  // authenticated person's number, global across banks, stored on ResourceUser.
  // The validated flag is separate from the validated date so it can be reset
  // (re-verification policy, suspected SIM swap) without losing the audit trail;
  // the date is set only on successful validation, so it always means "last time
  // this number passed verification".

  case class UserJsonV700(
    user_id: String,
    email: String,
    provider_id: String,
    provider: String,
    username: String,
    mobile_phone_number: Option[String],
    mobile_phone_number_is_validated: Option[Boolean],
    mobile_phone_number_validated_date: Option[Date],
    entitlements: EntitlementsJsonV600,
    views: Option[ViewsJSON300],
    on_behalf_of: Option[UserJsonV300]
  )

  case class UserInfoDetailJsonV700(
    user_id: String,
    email: String,
    provider_id: String,
    provider: String,
    username: String,
    first_name: String,
    last_name: String,
    mobile_phone_number: Option[String],
    mobile_phone_number_is_validated: Option[Boolean],
    mobile_phone_number_validated_date: Option[Date],
    entitlements: EntitlementJSONs,
    views: Option[ViewsJSON300],
    agreements: Option[List[UserAgreementJson]],
    is_deleted: Boolean,
    last_marketing_agreement_signed_date: Option[Date],
    is_locked: Boolean,
    created_date: Option[Date],
    updated_date: Option[Date],
    email_validated: Option[Boolean],
    last_used_locale: Option[String],
    last_activity_date: Option[Date],
    recent_operation_ids: List[String]
  )

  def createUserJsonV700(currentUser: UserV600, onBehalfOfUser: Option[UserV600]): UserJsonV700 = {
    val v600 = JSONFactory600.createUserInfoJSON(currentUser, onBehalfOfUser)
    UserJsonV700(
      user_id = v600.user_id,
      email = v600.email,
      provider_id = v600.provider_id,
      provider = v600.provider,
      username = v600.username,
      mobile_phone_number = currentUser.user.mobilePhoneNumber,
      mobile_phone_number_is_validated = currentUser.user.mobilePhoneNumberIsValidated,
      mobile_phone_number_validated_date = currentUser.user.mobilePhoneNumberValidatedDate,
      entitlements = v600.entitlements,
      views = v600.views,
      on_behalf_of = v600.on_behalf_of
    )
  }

  private def toUserInfoDetailJsonV700(
      v600: UserInfoDetailJsonV600,
      mobilePhoneNumber: Option[String],
      mobilePhoneNumberIsValidated: Option[Boolean],
      mobilePhoneNumberValidatedDate: Option[Date]
  ): UserInfoDetailJsonV700 =
    UserInfoDetailJsonV700(
      user_id = v600.user_id,
      email = v600.email,
      provider_id = v600.provider_id,
      provider = v600.provider,
      username = v600.username,
      first_name = v600.first_name,
      last_name = v600.last_name,
      mobile_phone_number = mobilePhoneNumber,
      mobile_phone_number_is_validated = mobilePhoneNumberIsValidated,
      mobile_phone_number_validated_date = mobilePhoneNumberValidatedDate,
      entitlements = v600.entitlements,
      views = v600.views,
      agreements = v600.agreements,
      is_deleted = v600.is_deleted,
      last_marketing_agreement_signed_date = v600.last_marketing_agreement_signed_date,
      is_locked = v600.is_locked,
      created_date = v600.created_date,
      updated_date = v600.updated_date,
      email_validated = v600.email_validated,
      last_used_locale = v600.last_used_locale,
      last_activity_date = v600.last_activity_date,
      recent_operation_ids = v600.recent_operation_ids
    )

  def createUserInfoDetailJsonV700(user: User, v600: UserInfoDetailJsonV600): UserInfoDetailJsonV700 =
    toUserInfoDetailJsonV700(
      v600,
      user.mobilePhoneNumber,
      user.mobilePhoneNumberIsValidated,
      user.mobilePhoneNumberValidatedDate
    )

  lazy val userJsonV700Example = UserJsonV700(
    user_id = ExampleValue.userIdExample.value,
    email = ExampleValue.emailExample.value,
    provider_id = ExampleValue.providerIdValueExample.value,
    provider = ExampleValue.providerValueExample.value,
    username = ExampleValue.usernameExample.value,
    mobile_phone_number = Some(ExampleValue.mobileNumberExample.value),
    mobile_phone_number_is_validated = Some(true),
    mobile_phone_number_validated_date = Some(APIUtil.DateWithSecondsExampleObject),
    entitlements = EntitlementsJsonV600(Nil),
    views = None,
    on_behalf_of = None
  )

  lazy val userInfoDetailJsonV700Example = toUserInfoDetailJsonV700(
    code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.userInfoDetailJsonV600,
    Some(ExampleValue.mobileNumberExample.value),
    Some(true),
    Some(APIUtil.DateWithSecondsExampleObject)
  )

  case class PutMyMobilePhoneNumberJsonV700(mobile_phone_number: String)

  case class MyMobilePhoneNumberJsonV700(
    mobile_phone_number: Option[String],
    mobile_phone_number_is_validated: Option[Boolean],
    mobile_phone_number_validated_date: Option[Date]
  )

  lazy val putMyMobilePhoneNumberJsonV700Example =
    PutMyMobilePhoneNumberJsonV700(ExampleValue.mobileNumberExample.value)

  // a freshly set number is unverified: flag false, no validated date yet
  lazy val myMobilePhoneNumberJsonV700Example = MyMobilePhoneNumberJsonV700(
    mobile_phone_number = Some(ExampleValue.mobileNumberExample.value),
    mobile_phone_number_is_validated = Some(false),
    mobile_phone_number_validated_date = None
  )

  // ─── Create User (self-registration) — v7 adds the optional mobile phone number ──
  // The number belongs to the person registering (global across banks, stored on
  // ResourceUser) and is stored UNVERIFIED: is_validated=false, no validated date.
  // Verification is a separate flow. Absent or blank means "no number".
  case class CreateUserJsonV700(
    email: String,
    username: String,
    password: String,
    first_name: String,
    last_name: String,
    mobile_phone_number: Option[String]
  )

  case class CreatedUserJsonV700(
    user_id: String,
    email: String,
    provider_id: String,
    provider: String,
    username: String,
    mobile_phone_number: Option[String],
    mobile_phone_number_is_validated: Option[Boolean],
    mobile_phone_number_validated_date: Option[Date],
    entitlements: EntitlementJSONs
  )

  def createCreatedUserJsonV700(v200: code.api.v2_0_0.JSONFactory200.UserJsonV200, resourceUser: User): CreatedUserJsonV700 =
    CreatedUserJsonV700(
      user_id = v200.user_id,
      email = v200.email,
      provider_id = v200.provider_id,
      provider = v200.provider,
      username = v200.username,
      mobile_phone_number = resourceUser.mobilePhoneNumber,
      mobile_phone_number_is_validated = resourceUser.mobilePhoneNumberIsValidated,
      mobile_phone_number_validated_date = resourceUser.mobilePhoneNumberValidatedDate,
      entitlements = v200.entitlements
    )

  lazy val createUserJsonV700Example = CreateUserJsonV700(
    email = ExampleValue.emailExample.value,
    username = ExampleValue.usernameExample.value,
    password = "String",
    first_name = "Simon",
    last_name = "Redfern",
    mobile_phone_number = Some(ExampleValue.mobileNumberExample.value)
  )

  lazy val createdUserJsonV700Example = CreatedUserJsonV700(
    user_id = ExampleValue.userIdExample.value,
    email = ExampleValue.emailExample.value,
    provider_id = ExampleValue.providerIdValueExample.value,
    provider = ExampleValue.providerValueExample.value,
    username = ExampleValue.usernameExample.value,
    mobile_phone_number = Some(ExampleValue.mobileNumberExample.value),
    mobile_phone_number_is_validated = Some(false),
    mobile_phone_number_validated_date = None,
    entitlements = EntitlementJSONs(Nil)
  )

  // ─── Password policy — published so clients can validate locally before user creation /
  // password reset. The structured fields are the normative contract; `regex` is a convenience
  // written in the portable subset that behaves identically in Java, JavaScript and Python.
  // A password is valid if it satisfies AT LEAST ONE of the policies.

  case class RequiredCharacterClassJsonV700(
    name: String,
    regex: String
  )

  /** The calling Consumer's identity and nothing else: no description, no limits, no counters, no key. */
  case class CurrentConsumerIdentityJsonV700(
    consumer_id: String,
    consumer_name: String
  )

  def createCurrentConsumerIdentityJsonV700(consumer: code.model.Consumer): CurrentConsumerIdentityJsonV700 =
    CurrentConsumerIdentityJsonV700(
      consumer_id = consumer.consumerId.get,
      consumer_name = Option(consumer.name.get).getOrElse("")
    )

  lazy val currentConsumerIdentityJsonV700Example = CurrentConsumerIdentityJsonV700(
    consumer_id = ExampleValue.consumerIdExample.value,
    consumer_name = "OBP Portal"
  )

  case class PasswordPolicyJsonV700(
    description: String,
    min_length: Int,
    max_length: Int,
    required_character_classes: List[RequiredCharacterClassJsonV700],
    allowed_characters: String,
    regex: String
  )

  case class PasswordPoliciesJsonV700(
    description: String,
    policies: List[PasswordPolicyJsonV700]
  )

  // printable ASCII without space — the character set both policy branches accept
  private val passwordAllowedCharacters = (0x21 to 0x7e).map(_.toChar).mkString

  lazy val passwordPoliciesJsonV700 = PasswordPoliciesJsonV700(
    description = "A password must satisfy at least one of the policies: " +
      "10 to 16 characters including at least one digit, one lower case letter, one upper case letter " +
      "and one special character - or a passphrase of 17 to 512 characters.",
    policies = List(
      PasswordPolicyJsonV700(
        description = "10 to 16 characters (printable ASCII, no space) including at least one digit, " +
          "one lower case letter, one upper case letter and one special character.",
        min_length = 10,
        max_length = 16,
        required_character_classes = List(
          RequiredCharacterClassJsonV700("digit", "[0-9]"),
          RequiredCharacterClassJsonV700("lowercase letter", "[a-z]"),
          RequiredCharacterClassJsonV700("uppercase letter", "[A-Z]"),
          RequiredCharacterClassJsonV700("special character", """[\x21-\x2f\x3a-\x40\x5b-\x60\x7b-\x7e]""")
        ),
        allowed_characters = passwordAllowedCharacters,
        regex = APIUtil.passwordCompositionPolicyRegex
      ),
      PasswordPolicyJsonV700(
        description = "A passphrase of 17 to 512 characters (printable ASCII, no space), no composition rules.",
        min_length = 17,
        max_length = 512,
        required_character_classes = Nil,
        allowed_characters = passwordAllowedCharacters,
        regex = APIUtil.passwordPassphrasePolicyRegex
      )
    )
  )

  // ─── Chat config — published so chat clients can apply the same link-host
  // policy at render time that the server enforces on message input
  // (code.chat.ChatLinkPolicy). ───────────────────────────────────────────────

  case class ChatConfigJsonV700(
    allowed_link_hosts: List[String],
    max_message_length: Int
  )

  lazy val chatConfigJsonV700Example = ChatConfigJsonV700(
    allowed_link_hosts = List("apisandbox.openbankproject.com", "openbankproject.com", "tesobe.com"),
    max_message_length = 10000
  )

  // ─── Validation email (anonymous resend) ────────────────────────────────────
  // The request identifies the target by (username, email). The response is the
  // same generic acknowledgement regardless of whether the user exists, is
  // already validated, the rate limit was hit, or the SMTP send failed — this
  // is the anti-enumeration property of the endpoint.
  case class PostValidationEmailRequestJsonV700(
    username: String,
    email: String
  )

  case class ValidationEmailResponseJsonV700(message: String)

  lazy val validationEmailResponseJsonV700Example = ValidationEmailResponseJsonV700(
    message = "If an unvalidated account exists for this username and email, a validation email has been sent."
  )

  // ── Metrics & Archive Metrics diagnostics ──────────────────────────────────
  //
  // Reports the metrics-archiving configuration plus row counts and the
  // oldest/newest record in both the `metric` and `metricarchive` tables, and
  // runs a set of integrity checks that flag whether MetricsArchiveScheduler is
  // actually keeping the tables within their configured retention windows.

  case class MetricsTableStatsJsonV700(
    table_name: String,
    count: Long,
    oldest_record_date: Option[Date],
    newest_record_date: Option[Date],
    oldest_record_age_days: Option[Long],
    newest_record_age_days: Option[Long]
  )

  case class MetricsArchiveConfigJsonV700(
    write_metrics: Boolean,
    enable_metrics_scheduler: Boolean,
    retain_metrics_scheduler_interval_in_seconds: Int,
    retain_metrics_days: Long,
    retain_archive_metrics_days: Long,
    retain_metrics_move_limit: Int
  )

  // status is one of "OK", "WARNING", "ERROR".
  case class MetricsIntegrityCheckJsonV700(
    name: String,
    status: String,
    message: String
  )

  // One row of the metricsarchiverun audit log (a completed scheduler run).
  case class MetricsArchiveRunJsonV700(
    run_id: String,
    api_instance_id: String,
    started_at: Date,
    ended_at: Date,
    duration_ms: Long,
    rows_moved_to_archive: Int,
    rows_deleted_from_archive: Int,
    success: Boolean,
    remark: String
  )

  case class MetricsAndArchiveMetricsDiagnosticsJsonV700(
    config: MetricsArchiveConfigJsonV700,
    metric: MetricsTableStatsJsonV700,
    metric_archive: MetricsTableStatsJsonV700,
    last_run: Option[MetricsArchiveRunJsonV700],
    last_successful_run: Option[MetricsArchiveRunJsonV700],
    checks: List[MetricsIntegrityCheckJsonV700],
    everything_as_expected: Boolean
  )

  private def metricsArchiveRunToJson(r: MetricsArchiveRun): MetricsArchiveRunJsonV700 =
    MetricsArchiveRunJsonV700(
      run_id                    = r.RunId.get,
      api_instance_id           = r.ApiInstanceId.get,
      started_at                = r.StartedAt.get,
      ended_at                  = r.EndedAt.get,
      duration_ms               = r.DurationMs.get,
      rows_moved_to_archive     = r.RowsMovedToArchive.get,
      rows_deleted_from_archive = r.RowsDeletedFromArchive.get,
      success                   = r.Success.get,
      remark                    = r.Remark.get
    )

  // The in-progress archive job whose lock blocked a new run. Surfaced so an
  // operator can tell a genuinely-running job from a stale lock left by a dead
  // JVM: an `age_seconds` of seconds is a real run; minutes/hours/days is almost
  // certainly abandoned and the `jobscheduler` lock row can be cleared by hand.
  case class InProgressArchiveJobJsonV700(
    job_id: String,
    api_instance_id: String,
    started_at: Date,
    age_seconds: Long
  )

  // Result of manually triggering an archive run. `status` is one of
  // "completed" (a run executed — inspect `run.success`) or
  // "skipped_already_in_progress" (a run was already running, so none was started;
  // `in_progress` then describes the lock that blocked it).
  // ─── Top Consumers (v7.0.0) ───
  // Grouped by the consumer id stored on the metric row (NOT by app name like v3.1.0), so
  // for a given window the number of rows matches aggregate-metrics' distinct_consumer_count.
  // app_name / developer_email are empty when the consumer row no longer exists.
  case class TopConsumerJsonV700(
      count: Int,
      consumer_id: String,
      app_name: String,
      developer_email: String
  )

  case class TopConsumersJsonV700(top_consumers: List[TopConsumerJsonV700])

  def createTopConsumersJsonV700(topConsumers: List[code.metrics.TopConsumer]): TopConsumersJsonV700 =
    TopConsumersJsonV700(
      topConsumers.map(topConsumer =>
        TopConsumerJsonV700(topConsumer.count, topConsumer.consumerId, topConsumer.appName, topConsumer.developerEmail)
      )
    )

  // ─── Top Users (v7.0.0) ───
  // One distinct user and their call count. On-behalf-of aware: consent-borne calls are
  // attributed to the granting human (resolved via the consent table), so for a given
  // window the number of rows matches aggregate-metrics' distinct_user_count.
  case class TopUserJsonV700(
      count: Int,
      user_id: String,
      username: String
  )

  case class TopUsersJsonV700(top_users: List[TopUserJsonV700])

  def createTopUsersJsonV700(topUsers: List[code.metrics.TopUser]): TopUsersJsonV700 =
    TopUsersJsonV700(
      topUsers.map(topUser => TopUserJsonV700(topUser.count, topUser.userId, topUser.userName))
    )

  case class TriggerMetricsArchiveRunResponseJsonV700(
    status: String,
    message: String,
    run: Option[MetricsArchiveRunJsonV700],
    in_progress: Option[InProgressArchiveJobJsonV700] = None
  )

  def createTriggerMetricsArchiveRunResponseJsonV700(outcome: code.scheduler.RunOutcome): TriggerMetricsArchiveRunResponseJsonV700 =
    outcome match {
      case code.scheduler.RunCompleted(r) =>
        val msg =
          if (r.Success.get)
            s"Archive run completed: moved ${r.RowsMovedToArchive.get} rows to the archive, deleted ${r.RowsDeletedFromArchive.get} outdated archive rows."
          else
            s"Archive run completed with errors: ${r.Remark.get}"
        TriggerMetricsArchiveRunResponseJsonV700("completed", msg, Some(metricsArchiveRunToJson(r)))
      case code.scheduler.RunSkippedAlreadyInProgress(jobId, apiInstanceId, startedAt) =>
        val ageSeconds = (System.currentTimeMillis - startedAt.getTime) / 1000L
        TriggerMetricsArchiveRunResponseJsonV700(
          "skipped_already_in_progress",
          s"An archive run started at $startedAt on api_instance_id '$apiInstanceId' is already in progress " +
            s"(job $jobId, running for $ageSeconds seconds); no new run was started. " +
            s"If this is much older than a normal run, the lock is likely stale and can be cleared.",
          None,
          Some(InProgressArchiveJobJsonV700(jobId, apiInstanceId, startedAt, ageSeconds)))
    }

  lazy val triggerMetricsArchiveRunResponseJsonV700Example = TriggerMetricsArchiveRunResponseJsonV700(
    status  = "completed",
    message = "Archive run completed: moved 4000 rows to the archive, deleted 1500 outdated archive rows.",
    run = Some(MetricsArchiveRunJsonV700(
      run_id                    = "9f3c2b1a-7d4e-4c8a-9b2f-1e6d5a0c4b7e",
      api_instance_id           = "obp",
      started_at                = new Date(1717200000000L),
      ended_at                  = new Date(1717200012000L),
      duration_ms               = 12000L,
      rows_moved_to_archive     = 4000,
      rows_deleted_from_archive = 1500,
      success                   = true,
      remark                    = ""
    ))
  )

  // One row of the `jobscheduler` lock table. This table holds a row only while a
  // job holds the scheduler lock (deleted when the job finishes), so a row here is
  // a currently-running job or a stale lock left by a dead JVM — `age_seconds`
  // tells them apart.
  case class SchedulerJobJsonV700(
    job_id: String,
    name: String,
    api_instance_id: String,
    started_at: Date,
    age_seconds: Long
  )

  case class SchedulerJobsJsonV700(
    jobs: List[SchedulerJobJsonV700],
    count: Int
  )

  def createSchedulerJobsJsonV700(rows: List[code.scheduler.JobScheduler]): SchedulerJobsJsonV700 = {
    val now = System.currentTimeMillis
    val jobs = rows.map { r =>
      val startedAt = r.createdAt.get
      SchedulerJobJsonV700(
        job_id          = r.JobId.get,
        name            = r.Name.get,
        api_instance_id = r.ApiInstanceId.get,
        started_at      = startedAt,
        age_seconds     = (now - startedAt.getTime) / 1000L
      )
    }
    SchedulerJobsJsonV700(jobs, jobs.size)
  }

  lazy val schedulerJobsJsonV700Example = SchedulerJobsJsonV700(
    jobs = List(SchedulerJobJsonV700(
      job_id          = "9f3c2b1a-7d4e-4c8a-9b2f-1e6d5a0c4b7e",
      name            = "MetricsArchiveScheduler",
      api_instance_id = "obp",
      started_at      = new Date(1717200000000L),
      age_seconds     = 42L
    )),
    count = 1
  )

  private val metricsOneDayInMillis: Long = 86400000L
  private def metricsAgeInDays(d: Date, now: Date): Long =
    (now.getTime - d.getTime) / metricsOneDayInMillis

  /**
   * Inspect the `metric` and `metricarchive` tables together with the archiving
   * props and report whether the MetricsArchiveScheduler is behaving as
   * configured. All props are read through `code.metrics.MetricsProps` — the same
   * accessors the scheduler acts on — so the reported values (fallback defaults
   * included) are by construction the ones the scheduler uses.
   *
   * Note: this issues blocking Mapper queries (count + a single-row ORDER BY on
   * the indexed `date` column) — call it from a Future.
   */
  def createMetricsAndArchiveMetricsDiagnosticsJsonV700(): MetricsAndArchiveMetricsDiagnosticsJsonV700 = {
    val now = new Date()

    val writeMetrics      = MetricsProps.writeMetrics
    val schedulerEnabled  = MetricsProps.enableMetricsScheduler
    val schedulerIntervalSeconds = MetricsProps.retainMetricsSchedulerIntervalInSeconds
    val retainMetricsDays = MetricsProps.retainMetricsDays
    val retainArchiveMetricsDays = MetricsProps.retainArchiveMetricsDays
    val moveLimit = MetricsProps.retainMetricsMoveLimit

    val config = MetricsArchiveConfigJsonV700(
      write_metrics                         = writeMetrics,
      enable_metrics_scheduler              = schedulerEnabled,
      retain_metrics_scheduler_interval_in_seconds = schedulerIntervalSeconds,
      retain_metrics_days                   = retainMetricsDays,
      retain_archive_metrics_days           = retainArchiveMetricsDays,
      retain_metrics_move_limit             = moveLimit
    )

    def statsFor(tableName: String, count: Long, oldest: Option[Date], newest: Option[Date]) =
      MetricsTableStatsJsonV700(
        table_name             = tableName,
        count                  = count,
        oldest_record_date     = oldest,
        newest_record_date     = newest,
        oldest_record_age_days = oldest.map(metricsAgeInDays(_, now)),
        newest_record_age_days = newest.map(metricsAgeInDays(_, now))
      )

    val metricOldest = MappedMetric.findAll(OrderBy(MappedMetric.date, Ascending), MaxRows(1)).headOption.map(_.getDate())
    val metricNewest = MappedMetric.findAll(OrderBy(MappedMetric.date, Descending), MaxRows(1)).headOption.map(_.getDate())
    val metricStats  = statsFor("metric", MappedMetric.count, metricOldest, metricNewest)

    val archiveOldest = MetricArchive.findAll(OrderBy(MetricArchive.date, Ascending), MaxRows(1)).headOption.map(_.getDate())
    val archiveNewest = MetricArchive.findAll(OrderBy(MetricArchive.date, Descending), MaxRows(1)).headOption.map(_.getDate())
    val archiveStats  = statsFor("metricarchive", MetricArchive.count, archiveOldest, archiveNewest)

    val graceDays = 7L
    val checks = scala.collection.mutable.ListBuffer[MetricsIntegrityCheckJsonV700]()

    checks += (if (writeMetrics)
      MetricsIntegrityCheckJsonV700("check_metrics_are_being_written", "OK",
        "write_metrics=true: API calls are being recorded into the metric table.")
    else
      MetricsIntegrityCheckJsonV700("check_metrics_are_being_written", "WARNING",
        "write_metrics=false: no new API metrics are being written, so the metric table count will not grow."))

    checks += (if (schedulerEnabled)
      MetricsIntegrityCheckJsonV700("check_archive_scheduler_is_enabled", "OK",
        "enable_metrics_scheduler=true: the archive/cleanup scheduler is active.")
    else
      MetricsIntegrityCheckJsonV700("check_archive_scheduler_is_enabled", "ERROR",
        "enable_metrics_scheduler=false: old metrics are never moved to the archive nor deleted; the metric table will grow without bound."))

    metricOldest match {
      case Some(d) =>
        val age = metricsAgeInDays(d, now)
        if (age <= retainMetricsDays + graceDays)
          checks += MetricsIntegrityCheckJsonV700("check_metric_retention_policy_is_respected", "OK",
            s"Oldest metric is $age days old, within the configured retention of $retainMetricsDays days (+${graceDays}d grace).")
        else
          checks += MetricsIntegrityCheckJsonV700("check_metric_retention_policy_is_respected", "ERROR",
            s"Oldest metric is $age days old but the configured retention is $retainMetricsDays days. Records older than this should have been moved to the archive — the archive move job is not keeping up or has stopped.")
      case None =>
        checks += MetricsIntegrityCheckJsonV700("check_metric_retention_policy_is_respected", "OK", "The metric table is empty.")
    }

    // Previously: rows with an empty/null correlation id could not be archived and were
    // surfaced here as a permanent backlog. As of the synthetic-id change in
    // MetricsArchiveScheduler.copyRowToMetricsArchive, such rows ARE archived (with an
    // "ORIGINALLY_NOT_SET-<uuid>" correlation id), so there is no un-archivable category
    // anymore. The check slot is retained (so consumers/dashboards keep a stable shape)
    // but its condition is intentionally empty for now — it always reports OK.
    checks += MetricsIntegrityCheckJsonV700("check_all_old_metrics_can_be_archived", "OK",
      "All metric rows older than the retention window are archivable; rows with no correlation id are archived with a generated 'ORIGINALLY_NOT_SET-<uuid>' id.")

    archiveOldest match {
      case Some(d) =>
        val age = metricsAgeInDays(d, now)
        if (age <= retainArchiveMetricsDays + graceDays)
          checks += MetricsIntegrityCheckJsonV700("check_archive_retention_policy_is_respected", "OK",
            s"Oldest archived metric is $age days old, within the configured archive retention of $retainArchiveMetricsDays days (+${graceDays}d grace).")
        else
          checks += MetricsIntegrityCheckJsonV700("check_archive_retention_policy_is_respected", "ERROR",
            s"Oldest archived metric is $age days old but the configured archive retention is $retainArchiveMetricsDays days. Records older than this should have been deleted — the archive cleanup job is not keeping up or has stopped.")
      case None =>
        checks += MetricsIntegrityCheckJsonV700("check_archive_retention_policy_is_respected", "OK", "The metricarchive table is empty.")
    }

    // If a backlog of metrics older than the retention window exists, the move
    // job must be running, so the newest archived record should itself be
    // roughly retain_metrics_days old. A much older newest-archive value means
    // the move job stopped.
    (metricOldest, archiveNewest) match {
      case (Some(mo), Some(an)) if metricsAgeInDays(mo, now) > retainMetricsDays + graceDays =>
        val newestArchiveAge = metricsAgeInDays(an, now)
        if (newestArchiveAge <= retainMetricsDays + graceDays)
          checks += MetricsIntegrityCheckJsonV700("check_archive_metrics_is_fresh_enough", "OK",
            s"Newest archived metric is $newestArchiveAge days old, consistent with an active move job.")
        else
          checks += MetricsIntegrityCheckJsonV700("check_archive_metrics_is_fresh_enough", "ERROR",
            s"There are metric rows older than the retention window, yet the newest archived record is $newestArchiveAge days old. The move job appears to have stopped roughly ${newestArchiveAge - retainMetricsDays} days ago.")
      case _ =>
        checks += MetricsIntegrityCheckJsonV700("check_archive_metrics_is_fresh_enough", "OK",
          "No backlog of metrics older than the retention window — nothing to move right now.")
    }

    // Run-log derived check: the durable record of scheduler runs (metricsarchiverun).
    val lastRun = MetricsArchiveRun.lastRun
    val lastSuccessfulRun = MetricsArchiveRun.lastSuccessfulRun
    lastRun match {
      case Some(r) if r.Success.get =>
        val ageDays = metricsAgeInDays(r.StartedAt.get, now)
        checks += MetricsIntegrityCheckJsonV700("check_last_archive_run_succeeded", "OK",
          s"Last archive run succeeded $ageDays days ago (moved ${r.RowsMovedToArchive.get} rows, deleted ${r.RowsDeletedFromArchive.get} outdated archive rows).")
      case Some(r) =>
        val ageDays = metricsAgeInDays(r.StartedAt.get, now)
        val lastOkNote = lastSuccessfulRun
          .map(s => s" Last successful run was ${metricsAgeInDays(s.StartedAt.get, now)} days ago.")
          .getOrElse(" No successful run has ever been recorded.")
        checks += MetricsIntegrityCheckJsonV700("check_last_archive_run_succeeded", "ERROR",
          s"The most recent archive run ($ageDays days ago) failed: ${r.Remark.get}.$lastOkNote")
      case None if schedulerEnabled =>
        checks += MetricsIntegrityCheckJsonV700("check_last_archive_run_succeeded", "WARNING",
          "No archive run has been recorded yet. The scheduler is enabled but may not have completed its first run since this table was introduced.")
      case None =>
        checks += MetricsIntegrityCheckJsonV700("check_last_archive_run_succeeded", "OK",
          "No archive run recorded — the scheduler is disabled, so this is expected.")
    }

    MetricsAndArchiveMetricsDiagnosticsJsonV700(
      config                 = config,
      metric                 = metricStats,
      metric_archive         = archiveStats,
      last_run               = lastRun.map(metricsArchiveRunToJson),
      last_successful_run    = lastSuccessfulRun.map(metricsArchiveRunToJson),
      checks                 = checks.toList,
      everything_as_expected = checks.forall(_.status == "OK")
    )
  }

  lazy val metricsAndArchiveMetricsDiagnosticsJsonV700Example = MetricsAndArchiveMetricsDiagnosticsJsonV700(
    config = MetricsArchiveConfigJsonV700(
      write_metrics                         = true,
      enable_metrics_scheduler              = true,
      retain_metrics_scheduler_interval_in_seconds = 599,
      retain_metrics_days                   = 90,
      retain_archive_metrics_days           = 730,
      retain_metrics_move_limit             = 4000
    ),
    metric = MetricsTableStatsJsonV700(
      table_name             = "metric",
      count                  = 1240000L,
      oldest_record_date     = Some(new Date(1709251200000L)),
      newest_record_date     = Some(new Date(1717200000000L)),
      oldest_record_age_days = Some(85L),
      newest_record_age_days = Some(0L)
    ),
    metric_archive = MetricsTableStatsJsonV700(
      table_name             = "metricarchive",
      count                  = 9800000L,
      oldest_record_date     = Some(new Date(1654041600000L)),
      newest_record_date     = Some(new Date(1701907200000L)),
      oldest_record_age_days = Some(700L),
      newest_record_age_days = Some(92L)
    ),
    last_run = Some(MetricsArchiveRunJsonV700(
      run_id                    = "9f3c2b1a-7d4e-4c8a-9b2f-1e6d5a0c4b7e",
      api_instance_id           = "obp",
      started_at                = new Date(1717200000000L),
      ended_at                  = new Date(1717200012000L),
      duration_ms               = 12000L,
      rows_moved_to_archive     = 4000,
      rows_deleted_from_archive = 1500,
      success                   = true,
      remark                    = ""
    )),
    last_successful_run = Some(MetricsArchiveRunJsonV700(
      run_id                    = "9f3c2b1a-7d4e-4c8a-9b2f-1e6d5a0c4b7e",
      api_instance_id           = "obp",
      started_at                = new Date(1717200000000L),
      ended_at                  = new Date(1717200012000L),
      duration_ms               = 12000L,
      rows_moved_to_archive     = 4000,
      rows_deleted_from_archive = 1500,
      success                   = true,
      remark                    = ""
    )),
    checks = List(
      MetricsIntegrityCheckJsonV700("check_metrics_are_being_written", "OK",
        "write_metrics=true: API calls are being recorded into the metric table."),
      MetricsIntegrityCheckJsonV700("check_archive_scheduler_is_enabled", "OK",
        "enable_metrics_scheduler=true: the archive/cleanup scheduler is active."),
      MetricsIntegrityCheckJsonV700("check_metric_retention_policy_is_respected", "OK",
        "Oldest metric is 85 days old, within the effective retention of 90 days (+7d grace)."),
      MetricsIntegrityCheckJsonV700("check_all_old_metrics_can_be_archived", "OK",
        "All metric rows older than the retention window are archivable; rows with no correlation id are archived with a generated 'ORIGINALLY_NOT_SET-<uuid>' id."),
      MetricsIntegrityCheckJsonV700("check_archive_retention_policy_is_respected", "OK",
        "Oldest archived metric is 700 days old, within the effective archive retention of 730 days (+7d grace)."),
      MetricsIntegrityCheckJsonV700("check_archive_metrics_is_fresh_enough", "OK",
        "Newest archived metric is 92 days old, consistent with an active move job."),
      MetricsIntegrityCheckJsonV700("check_last_archive_run_succeeded", "OK",
        "Last archive run succeeded 0 days ago (moved 4000 rows, deleted 1500 outdated archive rows).")
    ),
    everything_as_expected = true
  )

  // ─── API Product Subscription (v7.0.0). See API_PRODUCT_SUBSCRIPTION_PLAN.md ───────────────

  case class PostApiProductSubscriptionJsonV700(
    consumer_id: String,
    start_date: Option[Date],
    end_date: Option[Date]
  )

  case class PutApiProductSubscriptionStatusJsonV700(
    status: String,
    end_date: Option[Date]
  )

  case class ApiProductSubscriptionAttributeJsonV700(
    name: String,
    `type`: String,
    value: String,
    is_active: Option[Boolean]
  )

  case class ApiProductSubscriptionAttributeResponseJsonV700(
    api_product_subscription_id: String,
    api_product_subscription_attribute_id: String,
    name: String,
    `type`: String,
    value: String,
    is_active: Option[Boolean]
  )

  case class ApiProductSubscriptionJsonV700(
    api_product_subscription_id: String,
    bank_id: String,
    api_product_code: String,
    consumer_id: String,
    status: String,
    start_date: Date,
    end_date: Option[Date],
    created_by_user_id: String,
    rate_limiting_id: Option[String],
    created_at: Date,
    updated_at: Date,
    attributes: Option[List[ApiProductSubscriptionAttributeResponseJsonV700]]
  )

  case class ApiProductSubscriptionsJsonV700(api_product_subscriptions: List[ApiProductSubscriptionJsonV700])

  def createApiProductSubscriptionAttributeResponseJsonV700(attribute: ApiProductSubscriptionAttributeTrait): ApiProductSubscriptionAttributeResponseJsonV700 =
    ApiProductSubscriptionAttributeResponseJsonV700(
      api_product_subscription_id = attribute.apiProductSubscriptionId,
      api_product_subscription_attribute_id = attribute.apiProductSubscriptionAttributeId,
      name = attribute.name,
      `type` = attribute.attributeType,
      value = attribute.value,
      is_active = attribute.isActive
    )

  def createApiProductSubscriptionJsonV700(subscription: ApiProductSubscriptionTrait, attributes: Option[List[ApiProductSubscriptionAttributeTrait]]): ApiProductSubscriptionJsonV700 =
    ApiProductSubscriptionJsonV700(
      api_product_subscription_id = subscription.apiProductSubscriptionId,
      bank_id = subscription.bankId,
      api_product_code = subscription.apiProductCode,
      consumer_id = subscription.consumerId,
      status = subscription.status,
      start_date = subscription.startDate,
      end_date = subscription.endDate,
      created_by_user_id = subscription.createdByUserId,
      rate_limiting_id = subscription.rateLimitingId,
      created_at = subscription.createdAtDate,
      updated_at = subscription.updatedAtDate,
      attributes = attributes.map(_.map(createApiProductSubscriptionAttributeResponseJsonV700))
    )

  def createApiProductSubscriptionsJsonV700(subscriptions: List[ApiProductSubscriptionJsonV700]): ApiProductSubscriptionsJsonV700 =
    ApiProductSubscriptionsJsonV700(subscriptions)

  // Examples for the resource docs.
  lazy val postApiProductSubscriptionJsonV700Example = PostApiProductSubscriptionJsonV700(
    consumer_id = ExampleValue.consumerIdExample.value,
    start_date = Some(APIUtil.DateWithDayExampleObject),
    end_date = None
  )
  lazy val putApiProductSubscriptionStatusJsonV700Example = PutApiProductSubscriptionStatusJsonV700(status = "active", end_date = None)
  lazy val apiProductSubscriptionAttributeJsonV700Example = ApiProductSubscriptionAttributeJsonV700(
    name = "STRIPE_SUBSCRIPTION_ID", `type` = "STRING", value = "sub_1234567890", is_active = Some(true)
  )
  lazy val apiProductSubscriptionAttributeResponseJsonV700Example = ApiProductSubscriptionAttributeResponseJsonV700(
    api_product_subscription_id = "api-product-subscription-id-123",
    api_product_subscription_attribute_id = "api-product-subscription-attribute-id-123",
    name = "STRIPE_SUBSCRIPTION_ID", `type` = "STRING", value = "sub_1234567890", is_active = Some(true)
  )
  lazy val apiProductSubscriptionJsonV700Example = ApiProductSubscriptionJsonV700(
    api_product_subscription_id = "api-product-subscription-id-123",
    bank_id = ExampleValue.bankIdExample.value,
    api_product_code = ExampleValue.productCodeExample.value,
    consumer_id = ExampleValue.consumerIdExample.value,
    status = "active",
    start_date = APIUtil.DateWithDayExampleObject,
    end_date = None,
    created_by_user_id = ExampleValue.userIdExample.value,
    rate_limiting_id = Some("rate-limiting-id-123"),
    created_at = APIUtil.DateWithDayExampleObject,
    updated_at = APIUtil.DateWithDayExampleObject,
    attributes = Some(List(apiProductSubscriptionAttributeResponseJsonV700Example))
  )
  lazy val apiProductSubscriptionsJsonV700Example = ApiProductSubscriptionsJsonV700(List(apiProductSubscriptionJsonV700Example))
}

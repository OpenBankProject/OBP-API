package code.api.v7_0_0

import code.api.Constant
import code.api.util.APIUtil
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.MandatoryPropertyIsNotSet
import code.api.v4_0_0.{EnergySource400, HostedAt400, HostedBy400}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiVersion

object JSONFactory700 extends MdcLoggable with code.api.util.CustomJsonFormats {

  case class ErrorMessageEntryJsonV700(code: String, name: String, message: String)

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

  // ── Payee Lookup JSON case classes ──────────────────────────────────────────

  case class PayeeIdentityJsonV700(`type`: String, value: String)

  case class PostPayeeLookupJsonV700(
      identifier_type: String,
      identifier: String,
      fsp_id: Option[String]
  )

  case class PayeeLookupResponseJsonV700(
      lookup_id: String,
      expires_at: java.util.Date,
      identifier_type: String,
      identifier: String,
      fsp_id: Option[String],
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
}

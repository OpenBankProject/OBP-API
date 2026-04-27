package code.api.v7_0_0

import code.api.Constant
import code.api.util.APIUtil
import code.api.util.ErrorMessages.MandatoryPropertyIsNotSet
import code.api.v4_0_0.{EnergySource400, HostedAt400, HostedBy400}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiVersion

object JSONFactory700 extends MdcLoggable with code.api.util.CustomJsonFormats {

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
}

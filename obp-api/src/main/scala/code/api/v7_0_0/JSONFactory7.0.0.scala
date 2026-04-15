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

  // Response Models
  case class TradingOfferJson(
    offer_id: String,
    status: String,
    offer_details: OfferDetailsJson,
    account_info: AccountInfoJson,
    executions: List[OfferExecutionJson],
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
    accountId: String,
    idempotencyKey: String
  )

  case class CreateMarketMatchRequestJson(
    orderId: String,
    counterOrderId: String,
    amount: BigDecimal,
    price: BigDecimal
  )

  case class RequestSettlementJson(
    tradeId: String,
    step: Option[String]
  )

  case class NotifyDepositJson(
    txHash: String,
    from: String,
    to: String,
    amount: BigDecimal,
    confirmations: Int
  )

  case class RequestWithdrawalJson(
    accountId: String,
    amount: BigDecimal,
    address: String,
    idempotencyKey: String
  )

  // Market Response Models
  case class MarketOrderJson(
    orderId: String,
    side: String,
    price: BigDecimal,
    quantity: BigDecimal,
    accountId: String,
    status: String,
    createdAt: String,  // ISO 8601
    updatedAt: String   // ISO 8601
  )

  case class MarketMatchJson(
    matchId: String,
    orderId: String,
    counterOrderId: String,
    amount: BigDecimal,
    price: BigDecimal,
    createdAt: String  // ISO 8601
  )

  case class MarketTradeJson(
    tradeId: String,
    buyOrderId: String,
    sellOrderId: String,
    amount: BigDecimal,
    price: BigDecimal,
    status: String,
    createdAt: String  // ISO 8601
  )

  case class SettlementJson(
    settlementId: String,
    tradeId: String,
    step: Option[String],
    status: String,
    createdAt: String,           // ISO 8601
    completedAt: Option[String]  // ISO 8601
  )

  case class DepositJson(
    depositId: String,
    txHash: String,
    from: String,
    to: String,
    amount: BigDecimal,
    confirmations: Int,
    status: String,
    createdAt: String  // ISO 8601
  )

  case class WithdrawalJson(
    withdrawalId: String,
    accountId: String,
    amount: BigDecimal,
    address: String,
    status: String,
    txHash: Option[String],
    createdAt: String  // ISO 8601
  )

  // Market Conversion Functions
  def createMarketOrderJson(order: com.openbankproject.commons.model.MarketOrder): MarketOrderJson = {
    MarketOrderJson(
      orderId = order.orderId,
      side = order.side,
      price = order.price,
      quantity = order.quantity,
      accountId = order.accountId,
      status = order.status,
      createdAt = order.createdAt.toInstant.toString,
      updatedAt = order.updatedAt.toInstant.toString
    )
  }

  def createMarketMatchJson(marketMatch: com.openbankproject.commons.model.MarketMatch): MarketMatchJson = {
    MarketMatchJson(
      matchId = marketMatch.matchId,
      orderId = marketMatch.orderId,
      counterOrderId = marketMatch.counterOrderId,
      amount = marketMatch.amount,
      price = marketMatch.price,
      createdAt = marketMatch.createdAt.toInstant.toString
    )
  }

  def createMarketTradeJson(trade: com.openbankproject.commons.model.MarketTrade): MarketTradeJson = {
    MarketTradeJson(
      tradeId = trade.tradeId,
      buyOrderId = trade.buyOrderId,
      sellOrderId = trade.sellOrderId,
      amount = trade.amount,
      price = trade.price,
      status = trade.status,
      createdAt = trade.createdAt.toInstant.toString
    )
  }

  def createSettlementJson(settlement: com.openbankproject.commons.model.Settlement): SettlementJson = {
    SettlementJson(
      settlementId = settlement.settlementId,
      tradeId = settlement.tradeId,
      step = settlement.step,
      status = settlement.status,
      createdAt = settlement.createdAt.toInstant.toString,
      completedAt = settlement.completedAt.map(_.toInstant.toString)
    )
  }

  def createDepositJson(deposit: com.openbankproject.commons.model.Deposit): DepositJson = {
    DepositJson(
      depositId = deposit.depositId,
      txHash = deposit.txHash,
      from = deposit.from,
      to = deposit.to,
      amount = deposit.amount,
      confirmations = deposit.confirmations,
      status = deposit.status,
      createdAt = deposit.createdAt.toInstant.toString
    )
  }

  def createWithdrawalJson(withdrawal: com.openbankproject.commons.model.Withdrawal): WithdrawalJson = {
    WithdrawalJson(
      withdrawalId = withdrawal.withdrawalId,
      accountId = withdrawal.accountId,
      amount = withdrawal.amount,
      address = withdrawal.address,
      status = withdrawal.status,
      txHash = withdrawal.txHash,
      createdAt = withdrawal.createdAt.toInstant.toString
    )
  }
}

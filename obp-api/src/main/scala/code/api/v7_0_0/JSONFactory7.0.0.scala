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
}

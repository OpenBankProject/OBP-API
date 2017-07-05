/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.api.v2_2_0

//import code.api.v1_2_1.JSONFactory
import java.util.Date

import code.api.v1_2_1.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, BankRoutingJsonV121}
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_1_0.{MetricJson, MetricsJson, ResourceUserJSON}
import code.atms.Atms.Atm
import code.branches.Branches.Branch
import code.products.Products.Product
import code.fx.FXRate
import code.metadata.counterparties.CounterpartyTrait
import code.metrics.{APIMetric, ConnectorMetric}
import code.model._
import code.users.Users
import net.liftweb.common.Full
//import net.liftweb.common.Box
//import net.liftweb.json.Extraction
//import net.liftweb.json.JsonAST.JValue


case class ViewsJSONV220(
                      views : List[ViewJSONV220]
                    )
case class ViewJSONV220(
                val id: String,
                val short_name: String,
                val description: String,
                val is_public: Boolean,
                val alias: String,
                val hide_metadata_if_alias_used: Boolean,
                val can_add_comment : Boolean,
                val can_add_corporate_location : Boolean,
                val can_add_image : Boolean,
                val can_add_image_url: Boolean,
                val can_add_more_info: Boolean,
                val can_add_open_corporates_url : Boolean,
                val can_add_physical_location : Boolean,
                val can_add_private_alias : Boolean,
                val can_add_public_alias : Boolean,
                val can_add_tag : Boolean,
                val can_add_url: Boolean,
                val can_add_where_tag : Boolean,
                val can_delete_comment: Boolean,
                val can_add_counterparty : Boolean,
                val can_delete_corporate_location : Boolean,
                val can_delete_image : Boolean,
                val can_delete_physical_location : Boolean,
                val can_delete_tag : Boolean,
                val can_delete_where_tag : Boolean,
                val can_edit_owner_comment: Boolean,
                val can_see_bank_account_balance: Boolean,
                val can_see_bank_account_bank_name: Boolean,
                val can_see_bank_account_currency: Boolean,
                val can_see_bank_account_iban: Boolean,
                val can_see_bank_account_label: Boolean,
                val can_see_bank_account_national_identifier: Boolean,
                val can_see_bank_account_number: Boolean,
                val can_see_bank_account_owners: Boolean,
                val can_see_bank_account_swift_bic: Boolean,
                val can_see_bank_account_type: Boolean,
                val can_see_comments: Boolean,
                val can_see_corporate_location: Boolean,
                val can_see_image_url: Boolean,
                val can_see_images: Boolean,
                val can_see_more_info: Boolean,
                val can_see_open_corporates_url: Boolean,
                val can_see_other_account_bank_name: Boolean,
                val can_see_other_account_iban: Boolean,
                val can_see_other_account_kind: Boolean,
                val can_see_other_account_metadata: Boolean,
                val can_see_other_account_national_identifier: Boolean,
                val can_see_other_account_number: Boolean,
                val can_see_other_account_swift_bic: Boolean,
                val can_see_owner_comment: Boolean,
                val can_see_physical_location: Boolean,
                val can_see_private_alias: Boolean,
                val can_see_public_alias: Boolean,
                val can_see_tags: Boolean,
                val can_see_transaction_amount: Boolean,
                val can_see_transaction_balance: Boolean,
                val can_see_transaction_currency: Boolean,
                val can_see_transaction_description: Boolean,
                val can_see_transaction_finish_date: Boolean,
                val can_see_transaction_metadata: Boolean,
                val can_see_transaction_other_bank_account: Boolean,
                val can_see_transaction_start_date: Boolean,
                val can_see_transaction_this_bank_account: Boolean,
                val can_see_transaction_type: Boolean,
                val can_see_url: Boolean,
                val can_see_where_tag : Boolean
              )

case class AccountsJSONV220(
                         accounts : List[AccountJSONV220]
                       )
case class AccountJSONV220(
                        id : String,
                        label : String,
                        views_available : List[ViewJSONV220],
                        bank_id : String
                      )

case class FXRateJSON(
                       from_currency_code: String,
                       to_currency_code: String,
                       conversion_value: Double,
                       inverse_conversion_value: Double,
                       effective_date: Date
                     )

case class CounterpartyJsonV220(
                             name: String,
                             created_by_user_id: String,
                             this_bank_id: String,
                             this_account_id: String,
                             this_view_id: String,
                             counterparty_id: String,
                             other_bank_routing_scheme: String,
                             other_bank_routing_address: String,
                             other_branch_routing_scheme: String,
                             other_branch_routing_address: String,
                             other_account_routing_scheme: String,
                             other_account_routing_address: String,
                             is_beneficiary: Boolean
                           )

case class CounterpartiesJsonV220(
                                  counterparties: List[CounterpartyJsonV220]
                                 )




// used for Create Bank in V220
// keep it similar as "case class BankJSON" in V121  
case class BankJSONV220(
  id: String,
  full_name: String,
  short_name: String,
  logo_url: String,
  website_url: String,
  swift_bic: String,
  national_identifier: String,
  bank_routing: BankRoutingJsonV121
)

//keep similar to "case class BranchJsonPost" in V210
case class BranchJsonV220(
  id: String,
  bank_id: String,
  name: String,
  address: AddressJson,
  location: LocationJson,
  meta: MetaJson,
  lobby: LobbyJson,
  drive_up: DriveUpJson,
  branch_routing: BranchRoutingJsonV141
)



case class AtmJsonV220(
                           id: String,
                           bank_id: String,
                           name: String,
                           address: AddressJson,
                           location: LocationJson,
                           meta: MetaJson
                         )


//Copied from V210
case class ProductJsonV220(bank_id: String,
                           code : String,
                           name : String,
                           category: String,
                           family : String,
                           super_family : String,
                           more_info_url: String,
                           details: String,
                           description: String,
                           meta : MetaJson)

case class ProductsJsonV220 (products : List[ProductJsonV220])






// keep similar to case class CreateAccountJSON - v200
// Added branch_id and account_routing
case class CreateAccountJSONV220(
  user_id : String,
  label   : String,
  `type` : String,
  balance : AmountOfMoneyJsonV121,
  branch_id : String,
  account_routing: AccountRoutingJsonV121
)

case class CachedFunctionJSON(function_name: String, ttl_in_seconds: Int)
case class PortJSON(property: String, value: String)
case class AkkaJSON(ports: List[PortJSON], log_level: String)
case class MetricsJSON(property: String, value: String)
case class WarehouseJSON(property: String, value: String)
case class ElasticSearchJSON(metrics: List[MetricsJSON], warehouse: List[WarehouseJSON])
case class ConfigurationJSON(akka: AkkaJSON, elastic_search: ElasticSearchJSON, cache: List[CachedFunctionJSON])

case class ConnectorMetricJson(
                               connector_name: String,
                               function_name: String,
                               obp_api_request_id: String,
                               date: Date,
                               duration: Long
                             )
case class ConnectorMetricsJson(metrics: List[ConnectorMetricJson])

case class ConsumerJson(consumer_id: Long,
                        key: String,
                        secret: String,
                        app_name: String,
                        app_type: String,
                        description: String,
                        developer_email: String,
                        redirect_url: String,
                        created_by_user_id: String,
                        created_by_user: ResourceUserJSON,
                        enabled: Boolean,
                        created: Date
                       )

object JSONFactory220{

  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text

  def createViewsJSON(views : List[View]) : ViewsJSONV220 = {
    val list : List[ViewJSONV220] = views.map(createViewJSON)
    new ViewsJSONV220(list)
  }

  def createViewJSON(view : View) : ViewJSONV220 = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    new ViewJSONV220(
      id = view.viewId.value,
      short_name = stringOrNull(view.name),
      description = stringOrNull(view.description),
      is_public = view.isPublic,
      alias = alias,
      hide_metadata_if_alias_used = view.hideOtherAccountMetadataIfAlias,
      can_add_comment = view.canAddComment,
      can_add_corporate_location = view.canAddCorporateLocation,
      can_add_image = view.canAddImage,
      can_add_image_url = view.canAddImageURL,
      can_add_more_info = view.canAddMoreInfo,
      can_add_open_corporates_url = view.canAddOpenCorporatesUrl,
      can_add_physical_location = view.canAddPhysicalLocation,
      can_add_private_alias = view.canAddPrivateAlias,
      can_add_public_alias = view.canAddPublicAlias,
      can_add_tag = view.canAddTag,
      can_add_url = view.canAddURL,
      can_add_where_tag = view.canAddWhereTag,
      can_add_counterparty = view.canAddCounterparty,
      can_delete_comment = view.canDeleteComment,
      can_delete_corporate_location = view.canDeleteCorporateLocation,
      can_delete_image = view.canDeleteImage,
      can_delete_physical_location = view.canDeletePhysicalLocation,
      can_delete_tag = view.canDeleteTag,
      can_delete_where_tag = view.canDeleteWhereTag,
      can_edit_owner_comment = view.canEditOwnerComment,
      can_see_bank_account_balance = view.canSeeBankAccountBalance,
      can_see_bank_account_bank_name = view.canSeeBankAccountBankName,
      can_see_bank_account_currency = view.canSeeBankAccountCurrency,
      can_see_bank_account_iban = view.canSeeBankAccountIban,
      can_see_bank_account_label = view.canSeeBankAccountLabel,
      can_see_bank_account_national_identifier = view.canSeeBankAccountNationalIdentifier,
      can_see_bank_account_number = view.canSeeBankAccountNumber,
      can_see_bank_account_owners = view.canSeeBankAccountOwners,
      can_see_bank_account_swift_bic = view.canSeeBankAccountSwift_bic,
      can_see_bank_account_type = view.canSeeBankAccountType,
      can_see_comments = view.canSeeComments,
      can_see_corporate_location = view.canSeeCorporateLocation,
      can_see_image_url = view.canSeeImageUrl,
      can_see_images = view.canSeeImages,
      can_see_more_info = view.canSeeMoreInfo,
      can_see_open_corporates_url = view.canSeeOpenCorporatesUrl,
      can_see_other_account_bank_name = view.canSeeOtherAccountBankName,
      can_see_other_account_iban = view.canSeeOtherAccountIBAN,
      can_see_other_account_kind = view.canSeeOtherAccountKind,
      can_see_other_account_metadata = view.canSeeOtherAccountMetadata,
      can_see_other_account_national_identifier = view.canSeeOtherAccountNationalIdentifier,
      can_see_other_account_number = view.canSeeOtherAccountNumber,
      can_see_other_account_swift_bic = view.canSeeOtherAccountSWIFT_BIC,
      can_see_owner_comment = view.canSeeOwnerComment,
      can_see_physical_location = view.canSeePhysicalLocation,
      can_see_private_alias = view.canSeePrivateAlias,
      can_see_public_alias = view.canSeePublicAlias,
      can_see_tags = view.canSeeTags,
      can_see_transaction_amount = view.canSeeTransactionAmount,
      can_see_transaction_balance = view.canSeeTransactionBalance,
      can_see_transaction_currency = view.canSeeTransactionCurrency,
      can_see_transaction_description = view.canSeeTransactionDescription,
      can_see_transaction_finish_date = view.canSeeTransactionFinishDate,
      can_see_transaction_metadata = view.canSeeTransactionMetadata,
      can_see_transaction_other_bank_account = view.canSeeTransactionOtherBankAccount,
      can_see_transaction_start_date = view.canSeeTransactionStartDate,
      can_see_transaction_this_bank_account = view.canSeeTransactionThisBankAccount,
      can_see_transaction_type = view.canSeeTransactionType,
      can_see_url = view.canSeeUrl,
      can_see_where_tag = view.canSeeWhereTag
    )
  }

  def createFXRateJSON(fxRate: FXRate): FXRateJSON = {
    FXRateJSON(from_currency_code = fxRate.fromCurrencyCode,
      to_currency_code = fxRate.toCurrencyCode,
      conversion_value = fxRate.conversionValue,
      inverse_conversion_value = fxRate.inverseConversionValue,
      effective_date = fxRate.effectiveDate
    )
  }

  def createCounterpartyJSON(counterparty: CounterpartyTrait): CounterpartyJsonV220 = {
    CounterpartyJsonV220(
      name = counterparty.name,
      created_by_user_id = counterparty.createdByUserId,
      this_bank_id = counterparty.thisBankId,
      this_account_id = counterparty.thisAccountId,
      this_view_id = counterparty.thisViewId,
      counterparty_id = counterparty.counterpartyId,
      other_bank_routing_scheme = counterparty.otherBankRoutingScheme,
      other_account_routing_scheme = counterparty.otherAccountRoutingScheme,
      other_bank_routing_address = counterparty.otherBankRoutingAddress,
      other_account_routing_address = counterparty.otherAccountRoutingAddress,
      other_branch_routing_scheme = counterparty.otherBranchRoutingScheme,
      other_branch_routing_address =counterparty.otherBranchRoutingAddress,
      is_beneficiary = counterparty.isBeneficiary
    )
  }

  def createCounterpartiesJSON(counterparties : List[CounterpartyTrait]) : CounterpartiesJsonV220 = {
    val list : List[CounterpartyJsonV220] = counterparties.map(createCounterpartyJSON)
    new CounterpartiesJsonV220(list)
  }

  def createBankJSON(bank: Bank): BankJSONV220 = {
    BankJSONV220(
      id = bank.bankId.value,
      full_name = bank.fullName,
      short_name = bank.shortName,
      logo_url = bank.logoUrl,
      website_url = bank.websiteUrl,
      swift_bic = bank.swiftBic,
      national_identifier = bank.nationalIdentifier,
      bank_routing = BankRoutingJsonV121(
        scheme = bank.bankRoutingScheme,
        address = bank.bankRoutingAddress
      )
    )
  }

  // keep similar to def createBranchJson(branch: Branch) -- v140
  def createBranchJson(branch: Branch): BranchJsonV220 = {
    BranchJsonV220(
      id= branch.branchId.value,
      bank_id= branch.bankId.value,
      name= branch.name,
      address= createAddressJson(branch.address),
      location= createLocationJson(branch.location),
      meta= createMetaJson(branch.meta),
      lobby= createLobbyJson(branch.lobby.hours),
      drive_up= createDriveUpJson(branch.driveUp.hours),
      branch_routing = BranchRoutingJsonV141(
        scheme = branch.branchRoutingScheme,
        address = branch.branchRoutingAddress
      )
    )
  }


  def createAtmJson(atm: Atm): AtmJsonV220 = {
    AtmJsonV220(
      id= atm.atmId.value,
      bank_id= atm.bankId.value,
      name= atm.name,
      address= createAddressJson(atm.address),
      location= createLocationJson(atm.location),
      meta= createMetaJson(atm.meta)
    )
  }


  def createProductJson(product: Product) : ProductJsonV220 = {
    ProductJsonV220(
      product.bankId.toString,
      product.code.value,
      product.name,
      product.category,
      product.family,
      product.superFamily,
      product.moreInfoUrl,
      product.details,
      product.description,
      createMetaJson(product.meta))
  }

  def createProductsJson(productsList: List[Product]) : ProductsJsonV220 = {
    ProductsJsonV220(productsList.map(createProductJson))
  }





  
  def createAccountJSON(userId: String, account: BankAccount): CreateAccountJSONV220 = {
    CreateAccountJSONV220(
      user_id = userId,
      label = account.label,
      `type` = account.accountType,
      balance = AmountOfMoneyJsonV121(
        account.currency,
        account.balance.toString()
      ),
      branch_id = account.branchId,
      account_routing = AccountRoutingJsonV121(
        scheme = account.accountRoutingScheme,
        address = account.accountRoutingAddress
      )
    )
  }

  def createConnectorMetricJson(metric: ConnectorMetric): ConnectorMetricJson = {
    ConnectorMetricJson(
      connector_name = metric.getConnectorName(),
      function_name = metric.getFunctionName(),
      obp_api_request_id = metric.getCorrelationId(),
      duration = metric.getDuration(),
      date = metric.getDate()
    )
  }
  def createConnectorMetricsJson(metrics : List[ConnectorMetric]) : ConnectorMetricsJson = {
    ConnectorMetricsJson(metrics.map(createConnectorMetricJson))
  }

  def createConsumerJSON(c: Consumer): ConsumerJson = {

    val resourceUserJSON =  Users.users.vend.getUserByUserId(c.createdByUserId.toString()) match {
      case Full(resourceUser) => ResourceUserJSON(
        user_id = resourceUser.userId,
        email = resourceUser.emailAddress,
        provider_id = resourceUser.idGivenByProvider,
        provider = resourceUser.provider,
        username = resourceUser.name
      )
      case _ => null
    }

    ConsumerJson(consumer_id=c.id,
      key=c.key,
      secret=c.secret,
      app_name=c.name,
      app_type=c.appType.toString(),
      description=c.description,
      developer_email=c.developerEmail,
      redirect_url=c.redirectURL,
      created_by_user_id =c.createdByUserId,
      created_by_user =resourceUserJSON,
      enabled=c.isActive,
      created=c.createdAt
    )
  }
  
}
/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.v2_2_0

//import code.api.v1_2_1.JSONFactory
import java.util.Date

import code.actorsystem.ObpActorConfig
import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.util.APIUtil.MessageDoc
import code.api.v1_2_1.BankRoutingJsonV121
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121}
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_1_0.{JSONFactory210, LocationJsonV210, PostCounterpartyBespokeJson, ResourceUserJSON}
import code.atms.Atms.Atm
import code.branches.Branches.{Branch, DriveUpString, LobbyString}
import code.fx.FXRate
import code.metrics.ConnectorMetric
import code.model.dataAccess.ResourceUser
import code.model._
import com.openbankproject.commons.model.Product
import code.users.Users
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}
import net.liftweb.json.Extraction.decompose
import net.liftweb.json.JsonAST.JValue

import scala.collection.immutable.List



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

case class FXRateJsonV220(
                       bank_id: String,
                       from_currency_code: String,
                       to_currency_code: String,
                       conversion_value: Double,
                       inverse_conversion_value: Double,
                       effective_date: Date
                     )

case class CounterpartyWithMetadataJson(
  name: String,
  description: String,
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
  other_account_secondary_routing_scheme: String,
  other_account_secondary_routing_address: String,
  is_beneficiary: Boolean,
  bespoke:List[PostCounterpartyBespokeJson],
  metadata: CounterpartyMetadataJson
)
case class CounterpartyJsonV220(
                             name: String,
                             description: String,
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
                             other_account_secondary_routing_scheme: String,
                             other_account_secondary_routing_address: String,
                             is_beneficiary: Boolean,
                             bespoke:List[PostCounterpartyBespokeJson]
                           )

case class CounterpartyMetadataJson(
  public_alias : String, // Only have this value when we create explict counterparty
  more_info : String,
  url : String,
  image_url : String,
  open_corporates_url : String,
  corporate_location : LocationJsonV210,
  physical_location :  LocationJsonV210,
  private_alias : String
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
                           address: AddressJsonV140,
                           location: LocationJsonV140,
                           meta: MetaJsonV140,
                           lobby: LobbyStringJson,
                           drive_up: DriveUpStringJson,
                           branch_routing: BranchRoutingJsonV141
)



case class AtmJsonV220(
                        id: String,
                        bank_id: String,
                        name: String,
                        address: AddressJsonV140,
                        location: LocationJsonV140,
                        meta: MetaJsonV140
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
                           meta : MetaJsonV140)


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
case class AkkaJSON(ports: List[PortJSON], log_level: String, remote_data_secret_matched: Option[Boolean])
case class MetricsJSON(property: String, value: String)
case class WarehouseJSON(property: String, value: String)
case class ElasticSearchJSON(metrics: List[MetricsJSON], warehouse: List[WarehouseJSON])
case class ConfigurationJSON(akka: AkkaJSON, elastic_search: ElasticSearchJSON, cache: List[CachedFunctionJSON])

case class ConnectorMetricJson(
                               connector_name: String,
                               function_name: String,
                               correlation_id: String,
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



case class BasicUserJsonV220 (
                               user_id: String,
                               email: String,
                               provider_id: String,
                               provider: String,
                               username: String
                             )


case class BasicCustomerJsonV220(
                                  customer_id: String,
                                  customer_number: String,
                                  legal_name: String
                                )


case class BasicViewJsonV220(
                              view_id: String,
                              name: String,
                              description: String,
                              is_public: Boolean
                            )


case class CustomerViewJsonV220(
  user: BasicUserJsonV220,
  customer: BasicCustomerJsonV220,
  view: BasicViewJsonV220
)






/*

[{
"user": {
"user_id": "5995d6a2-01b3-423c-a173-5481df49bdaf",
"email": "robert.x.0.gh@example.com",
"provider_id": "robert.x.0.gh",
"provider": "OBP",
"username": "robert.x.0.gh"
},
"customer": {
"customer_id": "yauiuy67876f",
"customer_number": "12345",
"legal_name": "Robert Manchester"
},
"view": {
"view_id": "owner",
"short_name": "Accountant",
"description": "For the accountants",
"is_public": false
}
}]

*/



object JSONFactory220 extends CustomJsonFormats {
  
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

  def createFXRateJSON(fxRate: FXRate): FXRateJsonV220 = {
    FXRateJsonV220(
      bank_id = fxRate.bankId.value,
      from_currency_code = fxRate.fromCurrencyCode,
      to_currency_code = fxRate.toCurrencyCode,
      conversion_value = fxRate.conversionValue,
      inverse_conversion_value = fxRate.inverseConversionValue,
      effective_date = fxRate.effectiveDate
    )
  }

  def createCounterpartyWithMetadataJSON(counterparty: CounterpartyTrait, counterpartyMetadata: CounterpartyMetadata): CounterpartyWithMetadataJson = {
    CounterpartyWithMetadataJson(
      name = counterparty.name,
      description = counterparty.description,
      created_by_user_id = counterparty.createdByUserId,
      this_bank_id = counterparty.thisBankId,
      this_account_id = counterparty.thisAccountId,
      this_view_id = counterparty.thisViewId,
      counterparty_id = counterparty.counterpartyId,
      other_bank_routing_scheme = counterparty.otherBankRoutingScheme,
      other_bank_routing_address = counterparty.otherBankRoutingAddress,
      other_account_routing_scheme = counterparty.otherAccountRoutingScheme,
      other_account_routing_address = counterparty.otherAccountRoutingAddress,
      other_account_secondary_routing_scheme = counterparty.otherAccountSecondaryRoutingScheme,
      other_account_secondary_routing_address = counterparty.otherAccountSecondaryRoutingAddress,
      other_branch_routing_scheme = counterparty.otherBranchRoutingScheme,
      other_branch_routing_address =counterparty.otherBranchRoutingAddress,
      is_beneficiary = counterparty.isBeneficiary,
      bespoke = counterparty.bespoke.map(bespoke =>PostCounterpartyBespokeJson(bespoke.key,bespoke.value)),
      metadata=CounterpartyMetadataJson(
        public_alias = counterpartyMetadata.getPublicAlias,
        more_info = counterpartyMetadata.getMoreInfo,
        url = counterpartyMetadata.getUrl,
        image_url = counterpartyMetadata.getImageURL,
        open_corporates_url = counterpartyMetadata.getOpenCorporatesURL,
        corporate_location = JSONFactory210.createLocationJSON(counterpartyMetadata.getCorporateLocation),
        physical_location = JSONFactory210.createLocationJSON(counterpartyMetadata.getPhysicalLocation),
        private_alias = counterpartyMetadata.getPrivateAlias
      )
    )
  }
  
  def createCounterpartyJSON(counterparty: CounterpartyTrait): CounterpartyJsonV220 = {
      CounterpartyJsonV220(
        name = counterparty.name,
        description = counterparty.description,
        created_by_user_id = counterparty.createdByUserId,
        this_bank_id = counterparty.thisBankId,
        this_account_id = counterparty.thisAccountId,
        this_view_id = counterparty.thisViewId,
        counterparty_id = counterparty.counterpartyId,
        other_bank_routing_scheme = counterparty.otherBankRoutingScheme,
        other_bank_routing_address = counterparty.otherBankRoutingAddress,
        other_account_routing_scheme = counterparty.otherAccountRoutingScheme,
        other_account_routing_address = counterparty.otherAccountRoutingAddress,
        other_account_secondary_routing_scheme = counterparty.otherAccountSecondaryRoutingScheme,
        other_account_secondary_routing_address = counterparty.otherAccountSecondaryRoutingAddress,
        other_branch_routing_scheme = counterparty.otherBranchRoutingScheme,
        other_branch_routing_address =counterparty.otherBranchRoutingAddress,
        is_beneficiary = counterparty.isBeneficiary,
        bespoke = counterparty.bespoke.map(bespoke =>PostCounterpartyBespokeJson(bespoke.key,bespoke.value))
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
//  def createBranchJson(branch: BranchT): BranchJsonV220 = {
//    BranchJsonV220(
//      id= branch.branchId.value,
//      bank_id= branch.bankId.value,
//      name= branch.name,
//      address= createAddressJson(branch.address),
//      location= createLocationJson(branch.location),
//      meta= createMetaJson(branch.meta),
//      lobby= createLobbyStringJson(branch.lobbyString.getOrElse("")),
//      drive_up= createDriveUpStringJson(branch.driveUpString.getOrElse("")),
//      branch_routing = BranchRoutingJsonV141(
//        scheme = branch.branchRouting.map(_.scheme).getOrElse(""),
//        address = branch.branchRouting.map(_.address).getOrElse("")
//      )
//    )
//  }

  def createBranchJson(branch: BranchT): BranchJsonV220 = {
    BranchJsonV220(
      id= branch.branchId.value,
      bank_id= branch.bankId.value,
      name= branch.name,
      address= createAddressJson(branch.address),
      location= createLocationJson(branch.location),
      meta= createMetaJson(branch.meta),
      lobby= createLobbyStringJson(branch.lobbyString.map(_.hours).getOrElse("")),
      drive_up= createDriveUpStringJson(branch.driveUpString.map(_.hours).getOrElse("")),
      branch_routing = BranchRoutingJsonV141(
        scheme = branch.branchRouting.map(_.scheme).getOrElse(""),
        address = branch.branchRouting.map(_.address).getOrElse("")
      )
    )
  }




  def createAtmJson(atm: AtmT): AtmJsonV220 = {
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
      correlation_id = metric.getCorrelationId(),
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

    ConsumerJson(consumer_id=c.id.get,
      key=c.key.get,
      secret=c.secret.get,
      app_name=c.name.get,
      app_type=c.appType.toString(),
      description=c.description.get,
      developer_email=c.developerEmail.get,
      redirect_url=c.redirectURL.get,
      created_by_user_id =c.createdByUserId.get,
      created_by_user =resourceUserJSON,
      enabled=c.isActive.get,
      created=c.createdAt.get
    )
  }



  def createUserCustomerViewJsonV220(user: ResourceUser, customer: Customer, view: View): CustomerViewJsonV220 = {

    var basicUser = BasicUserJsonV220(
      user_id = user.userId,
      email = user.email.get,
      provider_id = user.idGivenByProvider,
      provider = user.provider,
      username = user.name_.get // TODO Double check this is the same as AuthUser.username ??
    )

    val basicCustomer = BasicCustomerJsonV220(
      customer_id = customer.customerId,
      customer_number = customer.number.toString,
      legal_name = customer.legalName
    )

    val basicView = BasicViewJsonV220(
      view_id = view.viewId.value,
      name = view.name,
      description = view.description,
      is_public = view.isPublic
    )

    val customerViewJsonV220: CustomerViewJsonV220 =
      CustomerViewJsonV220(
        user = basicUser,
        customer = basicCustomer,
        view = basicView
      )

    customerViewJsonV220
  }






  def transformV220ToBranch(branchJsonV220: BranchJsonV220): Box[Branch] = {

    val address : Address = transformToAddressFromV140(branchJsonV220.address) // Note the address in V220 is V140
    val location: Location =  transformToLocationFromV140(branchJsonV220.location)  // Note the location in V220 is V140
    val meta: Meta =  transformToMetaFromV140(branchJsonV220.meta)  // Note the meta in V220 is V140

    Full(Branch(
      BranchId(branchJsonV220.id),
      BankId(branchJsonV220.bank_id),
      branchJsonV220.name,
      address = address,
      location = location,
      lobbyString = Some(LobbyString(branchJsonV220.lobby.hours)),
      driveUpString = Some(DriveUpString(branchJsonV220.drive_up.hours)),
      meta = meta,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None))
  }

  def transformToAtmFromV220(atmJsonV220: AtmJsonV220): Box[Atm] = {
    val address : Address = transformToAddressFromV140(atmJsonV220.address) // Note the address in V220 is V140
    val location: Location =  transformToLocationFromV140(atmJsonV220.location)  // Note the location in V220 is V140
    val meta: Meta =  transformToMetaFromV140(atmJsonV220.meta)  // Note the meta in V220 is V140

    val atm = Atm(
      atmId = AtmId(atmJsonV220.id),
      bankId = BankId(atmJsonV220.bank_id),
      name = atmJsonV220.name,
      address = address,
      location = location,
      meta = meta,
      OpeningTimeOnMonday = None,
      ClosingTimeOnMonday = None,

      OpeningTimeOnTuesday = None,
      ClosingTimeOnTuesday = None,

      OpeningTimeOnWednesday = None,
      ClosingTimeOnWednesday = None,

      OpeningTimeOnThursday = None,
      ClosingTimeOnThursday = None,

      OpeningTimeOnFriday = None,
      ClosingTimeOnFriday = None,

      OpeningTimeOnSaturday = None,
      ClosingTimeOnSaturday = None,

      OpeningTimeOnSunday = None,
      ClosingTimeOnSunday = None,
      // Easy access for people who use wheelchairs etc. true or false ""=Unknown
      isAccessible = None,
      locatedAt = None,
      moreInfo = None,
      hasDepositCapability = None
    )
    Full(atm)
  }

  def getConfigInfoJSON(): ConfigurationJSON = {

    val f1 = CachedFunctionJSON("getBank", APIUtil.getPropsValue("connector.cache.ttl.seconds.getBank", "0").toInt)
    val f2 = CachedFunctionJSON("getBanks", APIUtil.getPropsValue("connector.cache.ttl.seconds.getBanks", "0").toInt)
    val f3 = CachedFunctionJSON("getAccount", APIUtil.getPropsValue("connector.cache.ttl.seconds.getAccount", "0").toInt)
    val f4 = CachedFunctionJSON("getAccounts", APIUtil.getPropsValue("connector.cache.ttl.seconds.getAccounts", "0").toInt)
    val f5 = CachedFunctionJSON("getTransaction", APIUtil.getPropsValue("connector.cache.ttl.seconds.getTransaction", "0").toInt)
    val f6 = CachedFunctionJSON("getTransactions", APIUtil.getPropsValue("connector.cache.ttl.seconds.getTransactions", "0").toInt)
    val f7 = CachedFunctionJSON("getCounterpartyFromTransaction", APIUtil.getPropsValue("connector.cache.ttl.seconds.getCounterpartyFromTransaction", "0").toInt)
    val f8 = CachedFunctionJSON("getCounterpartiesFromTransaction", APIUtil.getPropsValue("connector.cache.ttl.seconds.getCounterpartiesFromTransaction", "0").toInt)

    val akkaPorts = PortJSON("remotedata.local.port", ObpActorConfig.localPort.toString) :: PortJSON("remotedata.port", ObpActorConfig.remotePort) :: Nil
    val akka = AkkaJSON(akkaPorts, ObpActorConfig.akka_loglevel, APIUtil.akkaSanityCheck())
    val cache = f1::f2::f3::f4::f5::f6::f7::f8::Nil

    val metrics = MetricsJSON("es.metrics.port.tcp", APIUtil.getPropsValue("es.metrics.port.tcp", "9300")) ::
                  MetricsJSON("es.metrics.port.http", APIUtil.getPropsValue("es.metrics.port.tcp", "9200")) ::
                  Nil
    val warehouse = WarehouseJSON("es.warehouse.port.tcp", APIUtil.getPropsValue("es.warehouse.port.tcp", "9300")) ::
                    WarehouseJSON("es.warehouse.port.http", APIUtil.getPropsValue("es.warehouse.port.http", "9200")) ::
                    Nil

    ConfigurationJSON(akka, ElasticSearchJSON(metrics, warehouse), cache)
  }




  case class MessageDocJson(
                             process: String, // Should be unique
                             message_format: String,
                             outbound_topic: Option[String] = None,
                             inbound_topic: Option[String] = None,
                             description: String,
                             example_outbound_message: JValue,
                             example_inbound_message: JValue,
                             // TODO in next API version change these two fields to snake_case
                             outboundAvroSchema: Option[JValue] = None,
                             inboundAvroSchema: Option[JValue] = None,
                             adapter_implementation : AdapterImplementationJson
                           )

  case class AdapterImplementationJson(
                                        group: String,
                                        suggested_order: Integer
                           )


  // Creates the json message docs
  // changed key from messageDocs to message_docs 27 Oct 2018 whilst this version still DRAFT.
  case class MessageDocsJson(message_docs: List[MessageDocJson])

  def createMessageDocsJson(messageDocsList: List[MessageDoc]): MessageDocsJson = {
    MessageDocsJson(messageDocsList.map(createMessageDocJson))
  }

  def createMessageDocJson(md: MessageDoc): MessageDocJson = {
    MessageDocJson(
      process = md.process,
      message_format = md.messageFormat,
      description = md.description,
      outbound_topic = md.outboundTopic,
      inbound_topic = md.inboundTopic,
      example_outbound_message = decompose(md.exampleOutboundMessage),
      example_inbound_message = decompose(md.exampleInboundMessage),
      // TODO In next version of this endpoint, change these two fields to snake_case
      inboundAvroSchema = md.inboundAvroSchema,
      outboundAvroSchema = md.outboundAvroSchema,
      //////////////////////////////////////////
      adapter_implementation = AdapterImplementationJson(
                            md.adapterImplementation.map(_.group).getOrElse(""),
                            md.adapterImplementation.map(_.suggestedOrder).getOrElse(100)
      )
    )
  }



  
}
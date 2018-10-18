/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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

 */
package code.api.v3_0_0

import java.lang
import java.util.Date

import code.api.util.APIUtil
import code.api.util.APIUtil._
import code.api.util.Glossary.GlossaryItem
import code.api.v1_2_1.JSONFactory._
import code.api.v1_2_1.{UserJSONV121, _}
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0.JSONFactory200.{UserJsonV200, UsersJsonV200}
import code.api.v2_1_0.CustomerCreditRatingJSON
import code.atms.Atms.{Atm, AtmId, AtmT}
import code.bankconnectors.vMar2017.InboundAdapterInfoInternal
import code.branches.Branches._
import code.customer.Customer
import code.entitlement.Entitlement
import code.entitlementrequest.EntitlementRequest
import code.metrics.AggregateMetrics
import code.model.dataAccess.ResourceUser
import code.scope.Scope
import code.views.Views
import net.liftweb.common.{Box, Full}
import org.pegdown.PegDownProcessor

import scala.collection.immutable.List

//import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0.JSONFactory200
import code.api.v2_0_0.JSONFactory200.CoreTransactionDetailsJSON
import code.branches.Branches.Branch
import code.common._

// should replace Address in 1.4

import code.model._

import scala.util.Try





//started - view relevant case classes

case class ViewsJsonV300(
  views : List[ViewJsonV300]
)
case class ViewJsonV300(
  val id: String,
  val short_name: String,
  val description: String,
  val metadata_view: String,
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
  val can_query_available_funds: Boolean,
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
  val can_see_where_tag: Boolean,
  //V300 new 
  val can_see_bank_routing_scheme: Boolean,
  val can_see_bank_routing_address: Boolean,
  val can_see_bank_account_routing_scheme: Boolean,
  val can_see_bank_account_routing_address: Boolean,
  val can_see_other_bank_routing_scheme: Boolean,
  val can_see_other_bank_routing_address: Boolean,
  val can_see_other_account_routing_scheme: Boolean,
  val can_see_other_account_routing_address: Boolean,
  val can_add_transaction_request_to_own_account: Boolean, //added following two for payments
  val can_add_transaction_request_to_any_account: Boolean,
  val can_see_bank_account_credit_limit: Boolean
)

case class BasicViewJson(
  val id: String,
  val short_name: String,
  val is_public: Boolean
)

//ended -- View relevant case classes ////
//stated -- Transaction relevant case classes /////
case class ThisAccountJsonV300(
  id: String,
  bank_routing: BankRoutingJsonV121,
  account_routings: List[AccountRoutingJsonV121],
  holders: List[AccountHolderJSON]
)

case class OtherAccountJsonV300(
  id: String,
  holder: AccountHolderJSON,
  bank_routing: BankRoutingJsonV121,
  account_routings: List[AccountRoutingJsonV121],
  metadata: OtherAccountMetadataJSON
)

case class OtherAccountsJsonV300(
  other_accounts: List[OtherAccountJsonV300]
)

case class TransactionJsonV300(
  id: String,
  this_account: ThisAccountJsonV300,
  other_account: OtherAccountJsonV300,
  details: TransactionDetailsJSON,
  metadata: TransactionMetadataJSON
)

case class TransactionsJsonV300(
  transactions: List[TransactionJsonV300]
)

case class CoreCounterpartyJsonV300(
  id: String,
  holder: AccountHolderJSON,
  bank_routing: BankRoutingJsonV121,
  account_routings: List[AccountRoutingJsonV121]
)

case class CoreTransactionJsonV300(
  id: String,
  this_account: ThisAccountJsonV300,
  other_account: CoreCounterpartyJsonV300,
  details: CoreTransactionDetailsJSON
)

case class CoreCounterpartiesJsonV300(
  counterparties: List[CoreCounterpartyJsonV300]
)

case class CoreTransactionsJsonV300(
  transactions: List[CoreTransactionJsonV300]
)

//ended -- Transaction relevant case classes /////

//stated -- account relevant case classes /////
case class ModeratedAccountJsonV300(
  id: String,
  bank_id: String,
  label: String,
  number: String,
  owners: List[UserJSONV121],
  `type`: String,
  balance: AmountOfMoneyJsonV121,
  views_available: List[ViewJsonV300],
  account_routings: List[AccountRoutingJsonV121]
)
case class CoreAccountJsonV300(
  id : String,
  label : String,
  bank_id : String,
  account_routings: List[AccountRoutingJsonV121]
)


case class ViewBasic(
  id: String,
  short_name: String,
  description: String,
  is_public: Boolean
)

case class CoreAccountJson(
  id: String,
  label: String,
  bank_id: String,
  account_type: String,
  account_routings: List[AccountRoutingJsonV121],
  views: List[ViewBasic]
)

case class AccountHeldJson(
  id: String,
  bank_id: String,
  number: String,
  account_routings: List[AccountRoutingJsonV121]
)
case class CoreAccountsJsonV300(accounts: List[CoreAccountJson])
case class CoreAccountsHeldJsonV300(accounts: List[AccountHeldJson])

case class AccountIdJson(
  id: String
)
case class AccountsIdsJsonV300(accounts: List[AccountIdJson])

case class AccountRuleJsonV300(scheme: String, value: String)

case class ModeratedCoreAccountJsonV300(
                                         id: String,
                                         bank_id: String,
                                         label: String,
                                         number: String,
                                         owners: List[UserJSONV121],
                                         `type`: String,
                                         balance: AmountOfMoneyJsonV121,
                                         account_routings: List[AccountRoutingJsonV121],
                                         account_rules: List[AccountRuleJsonV300]
)

case class ModeratedCoreAccountsJsonV300(
  accounts: List[ModeratedCoreAccountJsonV300]
)

case class ElasticSearchJSON(query: ElasticSearchQuery)
case class ElasticSearchQuery(match_all: EmptyElasticSearch)
case class EmptyElasticSearch(none:Option[String] = None)


//ended -- account relevant case classes /////




case class OpeningTimesV300(
                             opening_time: String,
                             closing_time: String
                           )

case class LobbyJsonV330(
                          monday: List[OpeningTimesV300],
                          tuesday: List[OpeningTimesV300],
                          wednesday: List[OpeningTimesV300],
                          thursday: List[OpeningTimesV300],
                          friday: List[OpeningTimesV300],
                          saturday: List[OpeningTimesV300],
                          sunday: List[OpeningTimesV300]
                        )

case class DriveUpJsonV330(
                          monday: OpeningTimesV300,
                          tuesday: OpeningTimesV300,
                          wednesday: OpeningTimesV300,
                          thursday: OpeningTimesV300,
                          friday: OpeningTimesV300,
                          saturday: OpeningTimesV300,
                          sunday: OpeningTimesV300
                        )


//trait AddressvJson330 {
//  def line_1 : String
//  def line_2 : String
//  def line_3 : String
//  def city : String
//  def county : String
//  def state : String
//  def post_code : String
//  //ISO_3166-1_alpha-2
//  def country_code : String
//}


case class AddressJsonV300(
                             line_1 : String,
                             line_2 : String,
                             line_3 : String,
                             city : String,
                             county : String,
                             state : String,
                             postcode : String,
                             //ISO_3166-1_alpha-2
                             country_code : String
)



case class BranchJsonV300(
                           id: String,
                           bank_id: String,
                           name: String,
                           address: AddressJsonV300,
                           location: LocationJsonV140,
                           meta: MetaJsonV140,
                           lobby: LobbyJsonV330,
                           drive_up: DriveUpJsonV330,
                           branch_routing: BranchRoutingJsonV141,
                           // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
                           is_accessible : String,
                           accessibleFeatures: String,
                           branch_type : String,
                           more_info : String,
                           phone_number : String
                         )



case class BranchesJsonV300(branches : List[BranchJsonV300])


case class AtmJsonV300 (
                 id : String,
                 bank_id : String,
                 name : String,
                 address: AddressJsonV300,
                 location: LocationJsonV140,
                 meta: MetaJsonV140,

                 monday: OpeningTimesV300,
                 tuesday: OpeningTimesV300,
                 wednesday: OpeningTimesV300,
                 thursday: OpeningTimesV300,
                 friday: OpeningTimesV300,
                 saturday: OpeningTimesV300,
                 sunday: OpeningTimesV300,

                 is_accessible : String,
                 located_at : String,
                 more_info : String,
                 has_deposit_capability : String
               )

case class AtmsJsonV300(atms : List[AtmJsonV300])


case class AdapterInfoJsonV300(
                                name: String,
                                version: String,
                                git_commit: String,
                                date: String
                              )
case class CustomerJsonV300(
                             bank_id: String,
                             customer_id: String,
                             customer_number : String,
                             legal_name : String,
                             mobile_phone_number : String,
                             email : String,
                             face_image : CustomerFaceImageJson,
                             date_of_birth: String,
                             relationship_status: String,
                             dependants: Integer,
                             dob_of_dependants: List[String],
                             credit_rating: Option[CustomerCreditRatingJSON],
                             credit_limit: Option[AmountOfMoneyJsonV121],
                             highest_education_attained: String,
                             employment_status: String,
                             kyc_status: lang.Boolean,
                             last_ok_date: Date,
                             title: String, 
                             branchId: String,
                             nameSuffix: String)
case class CustomerJSONs(customers: List[CustomerJsonV300])

case class EntitlementRequestJSON(entitlement_request_id: String, user: UserJsonV200, role_name: String, bank_id: String, created: Date)
case class EntitlementRequestsJSON(entitlement_requests: List[EntitlementRequestJSON])
case class CreateEntitlementRequestJSON(bank_id: String, role_name: String)



case class GlossaryDescriptionJsonV300 (markdown: String, html: String)

case class GlossaryItemJsonV300 (title: String,
                                 description : GlossaryDescriptionJsonV300
                                )

case class GlossaryItemsJsonV300 (glossary_items: List[GlossaryItemJsonV300])


case class ScopeJson(scope_id: String, role_name: String, bank_id: String)
case class ScopeJsons(list: List[ScopeJson])
case class BanksJson(banks: List[BankJSON])
case class CreateScopeJson(bank_id: String, role_name: String)

case class AggregateMetricJSON(
                                  count: Int,
                                  average_response_time: Double,
                                  minimum_response_time: Double,
                                  maximum_response_time: Double
                                )

object JSONFactory300{

  // There are multiple flavours of markdown. For instance, original markdown emphasises underscores (surrounds _ with (<em>))
  // But we don't want to have to escape underscores (\_) in our documentation
  // Thus we use a flavour of markdown that ignores underscores in words. (Github markdown does this too)
  // PegDown seems to be feature rich and ignores underscores in words by default.

  // We return html rather than markdown to the consumer so they don't have to bother with these questions.
  // Set the timeout: https://github.com/sirthias/pegdown#parsing-timeouts
  val PegDownProcessorTimeout: Long = 1000*20
  val pegDownProcessor : PegDownProcessor = new PegDownProcessor(PegDownProcessorTimeout)



  def createGlossaryItemsJsonV300(glossaryItems: List[GlossaryItem]) : GlossaryItemsJsonV300 = {
    GlossaryItemsJsonV300(glossary_items = glossaryItems.map(createGlossaryItemJsonV300))
  }

  def createGlossaryItemJsonV300(glossaryItem : GlossaryItem) : GlossaryItemJsonV300 = {
    GlossaryItemJsonV300(
      title = glossaryItem.title,
      description = GlossaryDescriptionJsonV300 (markdown = glossaryItem.description.stripMargin, //.replaceAll("\n", ""),
                                                  html = pegDownProcessor.markdownToHtml(glossaryItem.description.stripMargin)// .replaceAll("\n", "")
      )
    )
  }

  //stated -- Transaction relevant methods /////
  def createTransactionsJson(transactions: List[ModeratedTransaction]) : TransactionsJsonV300 = {
    TransactionsJsonV300(transactions.map(createTransactionJSON))
  }

  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJsonV300 = {
    TransactionJsonV300(
      id = transaction.id.value,
      this_account = transaction.bankAccount.map(createThisAccountJSON).getOrElse(null),
      other_account = transaction.otherBankAccount.map(createOtherBankAccount).getOrElse(null),
      details = createTransactionDetailsJSON(transaction),
      metadata = transaction.metadata.map(createTransactionMetadataJSON).getOrElse(null)
    )
  }

  def createTransactionMetadataJSON(metadata : ModeratedTransactionMetadata) : TransactionMetadataJSON = {
    TransactionMetadataJSON(
      narrative = stringOptionOrNull(metadata.ownerComment),
      comments = metadata.comments.map(_.map(createTransactionCommentJSON)).getOrElse(null),
      tags = metadata.tags.map(_.map(createTransactionTagJSON)).getOrElse(null),
      images = metadata.images.map(_.map(createTransactionImageJSON)).getOrElse(null),
      where = metadata.whereTag.map(createLocationJSON).getOrElse(null)
    )
  }

  def createTransactionDetailsJSON(transaction : ModeratedTransaction) : TransactionDetailsJSON = {
    TransactionDetailsJSON(
      `type` = stringOptionOrNull(transaction.transactionType),
      description = stringOptionOrNull(transaction.description),
      posted = transaction.startDate.getOrElse(null),
      completed = transaction.finishDate.getOrElse(null),
      new_balance = createAmountOfMoneyJSON(transaction.currency, transaction.balance),
      value= createAmountOfMoneyJSON(transaction.currency, transaction.amount.map(_.toString))
    )
  }

  def createThisAccountJSON(bankAccount : ModeratedBankAccount) : ThisAccountJsonV300 = {
    ThisAccountJsonV300(
      id = bankAccount.accountId.value,
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.bankRoutingScheme),stringOptionOrNull(bankAccount.bankRoutingAddress)),
      account_routings = List(AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress))),
      holders = bankAccount.owners.map(x => x.toList.map(holder => AccountHolderJSON(name = holder.name, is_alias = false))).getOrElse(null)
    )
  }

  def createOtherAccountMetaDataJSON(metadata : ModeratedOtherBankAccountMetadata) : OtherAccountMetadataJSON = {
    OtherAccountMetadataJSON(
      public_alias = stringOptionOrNull(metadata.publicAlias),
      private_alias = stringOptionOrNull(metadata.privateAlias),
      more_info = stringOptionOrNull(metadata.moreInfo),
      URL = stringOptionOrNull(metadata.url),
      image_URL = stringOptionOrNull(metadata.imageURL),
      open_corporates_URL = stringOptionOrNull(metadata.openCorporatesURL),
      corporate_location = metadata.corporateLocation.map(createLocationJSON).getOrElse(null),
      physical_location = metadata.physicalLocation.map(createLocationJSON).getOrElse(null)
    )
  }

  def createOtherBankAccount(bankAccount : ModeratedOtherBankAccount) : OtherAccountJsonV300 = {
    OtherAccountJsonV300(
      id = bankAccount.id,
      holder = createAccountHolderJSON(bankAccount.label.display, bankAccount.isAlias),
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.bankRoutingScheme),stringOptionOrNull(bankAccount.bankRoutingAddress)),
      account_routings = List(AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress))),
      metadata = bankAccount.metadata.map(createOtherAccountMetaDataJSON).getOrElse(null)
    )
  }

  def createOtherBankAccountsJson(otherBankAccounts : List[ModeratedOtherBankAccount]) : OtherAccountsJsonV300 =  {
    val otherAccountJsonV300 : List[OtherAccountJsonV300] = otherBankAccounts.map(createOtherBankAccount)
    OtherAccountsJsonV300(otherAccountJsonV300)
  }

  // following are create core transactions, without the meta data parts
  def createCoreTransactionsJSON(transactionsCore: List[ModeratedTransactionCore]) : CoreTransactionsJsonV300 = {
    CoreTransactionsJsonV300(transactionsCore.map(createCoreTransactionJSON))
  }

  def createCoreTransactionJSON(transactionCore : ModeratedTransactionCore) : CoreTransactionJsonV300 = {
    CoreTransactionJsonV300(
      id = transactionCore.id.value,
      this_account = transactionCore.bankAccount.map(createThisAccountJSON).getOrElse(null),
      other_account = transactionCore.otherBankAccount.map(createCoreCounterparty).getOrElse(null),
      details = createCoreTransactionDetailsJSON(transactionCore)
    )
  }

  def createCoreTransactionDetailsJSON(transactionCore : ModeratedTransactionCore) : CoreTransactionDetailsJSON = {
    CoreTransactionDetailsJSON(
      `type` = stringOptionOrNull(transactionCore.transactionType),
      description = stringOptionOrNull(transactionCore.description),
      posted = transactionCore.startDate.getOrElse(null),
      completed = transactionCore.finishDate.getOrElse(null),
      new_balance = createAmountOfMoneyJSON(transactionCore.currency, transactionCore.balance),
      value = createAmountOfMoneyJSON(transactionCore.currency, transactionCore.amount.map(_.toString))
    )
  }

  def createCoreCounterparty(bankAccount : ModeratedOtherBankAccountCore) : CoreCounterpartyJsonV300 = {
    CoreCounterpartyJsonV300(
      id = bankAccount.id,
      holder = createAccountHolderJSON(bankAccount.label.display, bankAccount.isAlias),
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.bankRoutingScheme),stringOptionOrNull(bankAccount.bankRoutingAddress)),
      account_routings = List(AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)))
    )
  }

  //ended -- Transaction relevant methods /////

  def createViewsJSON(views : List[View]) : ViewsJsonV300 = {
    ViewsJsonV300(views.map(createViewJSON))
  }

  def createViewJSON(view : View) : ViewJsonV300 = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    ViewJsonV300(
      id = view.viewId.value,
      short_name = stringOrNull(view.name),
      description = stringOrNull(view.description),
      metadata_view= view.metadataView,
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
      can_delete_comment = view.canDeleteComment,
      can_add_counterparty = view.canAddCounterparty,
      can_delete_corporate_location = view.canDeleteCorporateLocation,
      can_delete_image = view.canDeleteImage,
      can_delete_physical_location = view.canDeletePhysicalLocation,
      can_delete_tag = view.canDeleteTag,
      can_delete_where_tag = view.canDeleteWhereTag,
      can_edit_owner_comment = view.canEditOwnerComment,
      can_see_bank_account_balance = view.canSeeBankAccountBalance,
      can_query_available_funds = view.canQueryAvailableFunds,
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
      can_see_where_tag = view.canSeeWhereTag,
      //V300 new
      can_see_bank_routing_scheme         = view.canSeeBankRoutingScheme,
      can_see_bank_routing_address        = view.canSeeBankRoutingAddress,
      can_see_bank_account_routing_scheme  = view.canSeeBankAccountRoutingScheme,
      can_see_bank_account_routing_address = view.canSeeBankAccountRoutingAddress,
      can_see_other_bank_routing_scheme    = view.canSeeOtherBankRoutingScheme,
      can_see_other_bank_routing_address   = view.canSeeOtherBankRoutingAddress,
      can_see_other_account_routing_scheme = view.canSeeOtherAccountRoutingScheme,
      can_see_other_account_routing_address= view.canSeeOtherAccountRoutingAddress,
      can_add_transaction_request_to_own_account = view.canAddTransactionRequestToOwnAccount, //added following two for payments
      can_add_transaction_request_to_any_account = view.canAddTransactionRequestToAnyAccount,
      can_see_bank_account_credit_limit = view.canSeeBankAccountCreditLimit
    )
  }
  def createBasicViewJSON(view : View) : BasicViewJson = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

      BasicViewJson(
      id = view.viewId.value,
      short_name = stringOrNull(view.name),
      is_public = view.isPublic
    )
  }

  def createCoreAccountJSON(account : BankAccount): CoreAccountJsonV300 =
    CoreAccountJsonV300(
      account.accountId.value,
      stringOrNull(account.label),
      account.bankId.value,
      List(AccountRoutingJsonV121(account.accountRoutingScheme,account.accountRoutingAddress))
    )

  def createCoreAccountsByCoreAccountsJSON(coreAccounts : List[CoreAccount]): CoreAccountsJsonV300 =
    CoreAccountsJsonV300(coreAccounts.map(coreAccount => CoreAccountJson(
      coreAccount.id,
      coreAccount.label,
      coreAccount.bankId,
      coreAccount.accountType,
      coreAccount.accountRoutings.map(accountRounting =>AccountRoutingJsonV121(accountRounting.scheme, accountRounting.address)),
      views = Views.views.vend
        .viewsForAccount(BankIdAccountId(BankId(coreAccount.bankId), AccountId(coreAccount.id)))
        .map(mappedView => 
          ViewBasic(
            mappedView.viewId.value,
            mappedView.name,
            mappedView.description,
            mappedView.isPublic
          ))
    )))
  
  def createCoreAccountsByCoreAccountsJSON(accountsHeld : List[AccountHeld]): CoreAccountsHeldJsonV300 =
    CoreAccountsHeldJsonV300(accountsHeld.map(
      account => AccountHeldJson(
        account.id,
        account.bankId, 
        account.number, 
        account.accountRoutings.map(accountRounting =>AccountRoutingJsonV121(accountRounting.scheme, accountRounting.address))
        )))
  
  def createAccountsIdsByBankIdAccountIds(bankaccountIds :  List[BankIdAccountId]): AccountsIdsJsonV300 =
    AccountsIdsJsonV300(bankaccountIds.map(x => AccountIdJson(x.accountId.value)))

  def createAccountRulesJSON(rules: List[AccountRule]): List[AccountRuleJsonV300] = {
    rules.map(i => AccountRuleJsonV300(scheme = i.scheme, value = i.value))
  }
  def createAccountRoutingsJSON(routings: List[AccountRouting]): List[AccountRoutingJsonV121] = {
    routings.map(i => AccountRoutingJsonV121(scheme = i.scheme, address = i.address))
  }

  def createCoreBankAccountJSON(account : ModeratedBankAccount) : ModeratedCoreAccountJsonV300 =  {
    val bankName = account.bankName.getOrElse("")
    new ModeratedCoreAccountJsonV300 (
      account.accountId.value,
      stringOrNull(account.bankId.value),
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      createOwnersJSON(account.owners.getOrElse(Set()), bankName),
      stringOptionOrNull(account.accountType),
      createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance),
      createAccountRoutingsJSON(account.accountRoutings),
      createAccountRulesJSON(account.accountRules)
    )
  }
  
  def createFirehoseCoreBankAccountJSON(accounts : List[ModeratedBankAccount]) : ModeratedCoreAccountsJsonV300 =  {
    ModeratedCoreAccountsJsonV300(
      accounts.map(
        account => 
          ModeratedCoreAccountJsonV300 (
            account.accountId.value,
            stringOrNull(account.bankId.value),
            stringOptionOrNull(account.label),
            stringOptionOrNull(account.number),
            createOwnersJSON(account.owners.getOrElse(Set()), account.bankName.getOrElse("")),
            stringOptionOrNull(account.accountType),
            createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance),
            createAccountRoutingsJSON(account.accountRoutings),
            createAccountRulesJSON(account.accountRules)
          )
      )
    )
  }

  // Accept a license object and return its json representation
  def createLicenseJson(license : LicenseT) : LicenseJsonV140 = {
    LicenseJsonV140(license.id, license.name)
  }

  def createLocationJson(location : LocationT) : LocationJsonV140 = {
    LocationJsonV140(location.latitude, location.longitude)
  }


  def createDriveUpStringJson(hours : String) : DriveUpStringJson = {
    DriveUpStringJson(hours)
  }

  def createLobbyStringJson(hours : String) : LobbyStringJson = {
    LobbyStringJson(hours)
  }

  def createMetaJson(meta: MetaT) : MetaJsonV140 = {
    MetaJsonV140(createLicenseJson(meta.license))
  }



  def createBranchJsonV300(branch: BranchT): BranchJsonV300 = {
    val getOrCreateLobby: Lobby = branch.lobby.getOrElse(Lobby(List(OpeningTimes("","")),List(OpeningTimes("","")),List(OpeningTimes("","")),List(OpeningTimes("","")),List(OpeningTimes("","")),List(OpeningTimes("","")),List(OpeningTimes("",""))))
    
    BranchJsonV300(branch.branchId.value,
      branch.bankId.value,
      branch.name,
      AddressJsonV300(branch.address.line1,
        branch.address.line2,
        branch.address.line3,
        branch.address.city,
        branch.address.county.getOrElse(""),
        branch.address.state,
        branch.address.postCode,
        branch.address.countryCode),
      createLocationJson(branch.location),
      createMetaJson(branch.meta),
      LobbyJsonV330(
        monday = getOrCreateLobby.monday.map(x => OpeningTimesV300(x.openingTime,x.closingTime)),
        tuesday = getOrCreateLobby.tuesday.map(x => OpeningTimesV300(x.openingTime,x.closingTime)),
        wednesday = getOrCreateLobby.wednesday.map(x => OpeningTimesV300(x.openingTime,x.closingTime)),
        thursday = getOrCreateLobby.thursday.map(x => OpeningTimesV300(x.openingTime,x.closingTime)),
        friday = getOrCreateLobby.friday.map(x => OpeningTimesV300(x.openingTime,x.closingTime)),
        saturday = getOrCreateLobby.saturday.map(x => OpeningTimesV300(x.openingTime,x.closingTime)),
        sunday = getOrCreateLobby.sunday.map(x => OpeningTimesV300(x.openingTime,x.closingTime))
      ),
      DriveUpJsonV330(
        monday = OpeningTimesV300(
          opening_time = branch.driveUp.map(_.monday.openingTime).getOrElse(""),
          closing_time = branch.driveUp.map(_.monday.closingTime).getOrElse("")),
        tuesday = OpeningTimesV300(
          opening_time = branch.driveUp.map(_.tuesday.openingTime).getOrElse(""),
          closing_time = branch.driveUp.map(_.tuesday.closingTime).getOrElse("")),
        wednesday = OpeningTimesV300(
          opening_time = branch.driveUp.map(_.wednesday.openingTime).getOrElse(""),
          closing_time = branch.driveUp.map(_.wednesday.closingTime).getOrElse("")),
        thursday = OpeningTimesV300(
          opening_time = branch.driveUp.map(_.thursday.openingTime).getOrElse(""),
          closing_time = branch.driveUp.map(_.thursday.closingTime).getOrElse("")),
        friday = OpeningTimesV300(
          opening_time = branch.driveUp.map(_.friday.openingTime).getOrElse(""),
          closing_time = branch.driveUp.map(_.friday.closingTime).getOrElse("")),
        saturday = OpeningTimesV300(
          opening_time = branch.driveUp.map(_.saturday.openingTime).getOrElse(""),
          closing_time = branch.driveUp.map(_.saturday.closingTime).getOrElse("")),
        sunday = OpeningTimesV300(
          opening_time = branch.driveUp.map(_.sunday.openingTime).getOrElse(""),
          closing_time = branch.driveUp.map(_.sunday.closingTime).getOrElse(""))
      ),
      BranchRoutingJsonV141(
        scheme = branch.branchRouting.map(_.scheme).getOrElse(""),
        address = branch.branchRouting.map(_.address).getOrElse("")
      ),
      is_accessible = branch.isAccessible.map(_.toString).getOrElse(""),
      accessibleFeatures = branch.accessibleFeatures.getOrElse(""),
      branch_type = branch.branchType.getOrElse(""),
      more_info = branch.moreInfo.getOrElse(""),
      phone_number = branch.phoneNumber.getOrElse("")
    )
  }

  def createBranchesJson(branchesList: List[BranchT]): BranchesJsonV300 = {
    BranchesJsonV300(branchesList.map(createBranchJsonV300))
  }

  def createAtmJsonV300(atm: AtmT): AtmJsonV300 = {
    AtmJsonV300(
      id= atm.atmId.value,
      bank_id= atm.bankId.value,
      name= atm.name,
      AddressJsonV300(atm.address.line1,
        atm.address.line2,
        atm.address.line3,
        atm.address.city,
        atm.address.county.getOrElse(""),
        atm.address.state,
        atm.address.postCode,
        atm.address.countryCode),
      createLocationJson(atm.location),
      createMetaJson(atm.meta),
      monday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnMonday.getOrElse(""),
        closing_time = atm.ClosingTimeOnMonday.getOrElse("")),
      tuesday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnTuesday.getOrElse(""),
        closing_time = atm.ClosingTimeOnTuesday.getOrElse("")),
      wednesday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnWednesday.getOrElse(""),
        closing_time = atm.ClosingTimeOnWednesday.getOrElse("")),
      thursday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnThursday.getOrElse(""),
        closing_time = atm.ClosingTimeOnThursday.getOrElse("")),
      friday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnFriday.getOrElse(""),
        closing_time = atm.ClosingTimeOnFriday.getOrElse("")),
      saturday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnSaturday.getOrElse(""),
        closing_time = atm.ClosingTimeOnSaturday.getOrElse("")),
      sunday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnSunday.getOrElse(""),
        closing_time = atm.ClosingTimeOnSunday.getOrElse("")),
      is_accessible = atm.isAccessible.map(_.toString).getOrElse(""),
      located_at = atm.locatedAt.getOrElse(""),
      more_info = atm.moreInfo.getOrElse(""),
      has_deposit_capability = atm.hasDepositCapability.map(_.toString).getOrElse("")
    )
  }
  def createAtmsJsonV300(atmList: List[AtmT]): AtmsJsonV300 = {
    AtmsJsonV300(atmList.map(createAtmJsonV300))
  }


  def transformToAddressFromV300(addressJsonV300: AddressJsonV300): Address = {
    Address(
      line1 = addressJsonV300.line_1,
      line2 = addressJsonV300.line_2,
      line3 = addressJsonV300.line_3,
      city = addressJsonV300.city,
      county = Some(addressJsonV300.county),
      state = addressJsonV300.state,
      postCode = addressJsonV300.postcode,
      countryCode = addressJsonV300.country_code // May not be a code
    )
  }

  def transformToAtmFromV300(atmJsonV300: AtmJsonV300): Box[Atm] = {
    val address : Address = transformToAddressFromV300(atmJsonV300.address) // Note the address in V220 is V140
    val location: Location =  transformToLocationFromV140(atmJsonV300.location)  // Note the location is V140
    val meta: Meta =  transformToMetaFromV140(atmJsonV300.meta)  // Note the meta  is V140
    val isAccessible: Boolean = Try(atmJsonV300.is_accessible.toBoolean).getOrElse(false)
    val hdc: Boolean = Try(atmJsonV300.has_deposit_capability.toBoolean).getOrElse(false)

    val atm = Atm(
      atmId = AtmId(atmJsonV300.id),
      bankId = BankId(atmJsonV300.bank_id),
      name = atmJsonV300.name,
      address = address,
      location = location,
      meta = meta,
      OpeningTimeOnMonday = Some(atmJsonV300.monday.opening_time),
      ClosingTimeOnMonday = Some(atmJsonV300.monday.closing_time),

      OpeningTimeOnTuesday = Some(atmJsonV300.tuesday.opening_time),
      ClosingTimeOnTuesday = Some(atmJsonV300.tuesday.closing_time),

      OpeningTimeOnWednesday = Some(atmJsonV300.wednesday.opening_time),
      ClosingTimeOnWednesday = Some(atmJsonV300.wednesday.closing_time),

      OpeningTimeOnThursday = Some(atmJsonV300.thursday.opening_time),
      ClosingTimeOnThursday = Some(atmJsonV300.thursday.closing_time),

      OpeningTimeOnFriday = Some(atmJsonV300.friday.opening_time),
      ClosingTimeOnFriday = Some(atmJsonV300.friday.closing_time),

      OpeningTimeOnSaturday = Some(atmJsonV300.saturday.opening_time),
      ClosingTimeOnSaturday = Some(atmJsonV300.saturday.closing_time),

      OpeningTimeOnSunday = Some(atmJsonV300.sunday.opening_time),
      ClosingTimeOnSunday = Some(atmJsonV300.sunday.closing_time),
      // Easy access for people who use wheelchairs etc. true or false ""=Unknown
      isAccessible = Some(isAccessible),
      locatedAt = Some(atmJsonV300.located_at),
      moreInfo = Some(atmJsonV300.more_info),
      hasDepositCapability = Some(hdc)
    )
    Full(atm)
  }

  // This goes FROM JSON TO internal representation of a Branch
  def transformToBranchFromV300(branchJsonV300: BranchJsonV300): Box[Branch] = {


    val address : Address = transformToAddressFromV300(branchJsonV300.address) // Note the address in V220 is V140
    val location: Location =  transformToLocationFromV140(branchJsonV300.location)  // Note the location is V140
    val meta: Meta =  transformToMetaFromV140(branchJsonV300.meta)  // Note the meta  is V140


    val lobby: Lobby = Lobby(
      monday = branchJsonV300.lobby.monday.map(x => OpeningTimes(x.opening_time, x.closing_time)),
      tuesday = branchJsonV300.lobby.tuesday.map(x => OpeningTimes(x.opening_time, x.closing_time)),
      wednesday = branchJsonV300.lobby.wednesday.map(x => OpeningTimes(x.opening_time, x.closing_time)),
      thursday = branchJsonV300.lobby.thursday.map(x => OpeningTimes(x.opening_time, x.closing_time)),
      friday = branchJsonV300.lobby.friday.map(x => OpeningTimes(x.opening_time, x.closing_time)),
      saturday = branchJsonV300.lobby.saturday.map(x => OpeningTimes(x.opening_time, x.closing_time)),
      sunday = branchJsonV300.lobby.sunday.map(x => OpeningTimes(x.opening_time, x.closing_time))
    )


    val driveUp: DriveUp = DriveUp(
      monday = OpeningTimes(
        openingTime = branchJsonV300.drive_up.monday.opening_time,
        closingTime = branchJsonV300.drive_up.monday.closing_time),
      tuesday = OpeningTimes(
        openingTime = branchJsonV300.drive_up.tuesday.opening_time,
        closingTime = branchJsonV300.drive_up.tuesday.closing_time),
      wednesday = OpeningTimes(
        openingTime = branchJsonV300.drive_up.wednesday.opening_time,
        closingTime = branchJsonV300.drive_up.wednesday.closing_time),
      thursday = OpeningTimes(
        openingTime = branchJsonV300.drive_up.thursday.opening_time,
        closingTime = branchJsonV300.drive_up.thursday.closing_time),
      friday = OpeningTimes(
        openingTime = branchJsonV300.drive_up.friday.opening_time,
        closingTime = branchJsonV300.drive_up.friday.closing_time),
      saturday = OpeningTimes(
        openingTime = branchJsonV300.drive_up.saturday.opening_time,
        closingTime = branchJsonV300.drive_up.saturday.closing_time),
      sunday = OpeningTimes(
        openingTime = branchJsonV300.drive_up.sunday.opening_time,
        closingTime = branchJsonV300.drive_up.sunday.closing_time)
    )




    val branchRouting = Some(Routing(branchJsonV300.branch_routing.scheme, branchJsonV300.branch_routing.address))




    val isAccessible: Boolean = Try(branchJsonV300.is_accessible.toBoolean).getOrElse(false)


    val branch: Branch = Branch(
      branchId = BranchId(branchJsonV300.id),
      bankId = BankId(branchJsonV300.bank_id),
      name = branchJsonV300.name,
      address = address,
      location = location,
      meta = meta,
      lobbyString = None,
      driveUpString = None,
      lobby = Some(lobby),
      driveUp = Some(driveUp),
      branchRouting = branchRouting,
      // Easy access for people who use wheelchairs etc. true or false ""=Unknown
      isAccessible = Some(isAccessible),
      accessibleFeatures = Some(branchJsonV300.accessibleFeatures),
      branchType = Some(branchJsonV300.branch_type),
      moreInfo = Some(branchJsonV300.more_info),
      phoneNumber = Some(branchJsonV300.phone_number)
    )

    Full(branch)
  }

  def createUserJSON(user : User, entitlements: List[Entitlement]) : UserJsonV200 = {
    new UserJsonV200(
      user_id = user.userId,
      email = user.emailAddress,
      username = stringOrNull(user.name),
      provider_id = user.idGivenByProvider,
      provider = stringOrNull(user.provider),
      entitlements = JSONFactory200.createEntitlementJSONs(entitlements)
    )
  }

  def createUserJSONs(users : List[(ResourceUser, Box[List[Entitlement]])]) : UsersJsonV200 = {
    UsersJsonV200(users.map(t => createUserJSON(t._1, t._2.getOrElse(Nil))))
  }


  def createAdapterInfoJson(ai: InboundAdapterInfoInternal): AdapterInfoJsonV300 = {
    AdapterInfoJsonV300(
      name = ai.name,
      version = ai.version,
      git_commit = ai.git_commit,
      date = ai.date
    )
  }


  def createCustomerJson(cInfo : Customer) : CustomerJsonV300 = {

    CustomerJsonV300(
      bank_id = cInfo.bankId.toString,
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      face_image = CustomerFaceImageJson(url = cInfo.faceImage.url,
        date = cInfo.faceImage.date),
      date_of_birth = (if (cInfo.dateOfBirth==null) "" else (APIUtil.DateWithDayFormat).format(cInfo.dateOfBirth)),
      relationship_status = cInfo.relationshipStatus,
      dependants = cInfo.dependents,
      dob_of_dependants = cInfo.dobOfDependents.map(x => if (x==null) "" else (APIUtil.DateWithDayFormat).format(x)),
      credit_rating = Option(CustomerCreditRatingJSON(rating = cInfo.creditRating.rating, source = cInfo.creditRating.source)),
      credit_limit = Option(AmountOfMoneyJsonV121(currency = cInfo.creditLimit.currency, amount = cInfo.creditLimit.amount)),
      highest_education_attained = cInfo.highestEducationAttained,
      employment_status = cInfo.employmentStatus,
      kyc_status = cInfo.kycStatus,
      last_ok_date = cInfo.lastOkDate,
      title = cInfo.title,
      branchId = cInfo.branchId,
      nameSuffix = cInfo.nameSuffix
    )
  }
  def createCustomersJson(customers : List[Customer]) : CustomerJSONs = {
    CustomerJSONs(customers.map(createCustomerJson))
  }

  def createEntitlementRequestJSON(e: EntitlementRequest): EntitlementRequestJSON = {
    EntitlementRequestJSON(
      entitlement_request_id = e.entitlementRequestId,
      user = JSONFactory200.createUserJSON(e.user),
      role_name = e.roleName,
      bank_id = e.bankId,
      created = e.created
    )
  }
  def createEntitlementRequestsJSON(list : List[EntitlementRequest]) : EntitlementRequestsJSON = {
    EntitlementRequestsJSON(list.map(createEntitlementRequestJSON))
  }

  
  def createScopeJson(scope: Scope): ScopeJson = {
    ScopeJson(
      scope_id = scope.scopeId,
      role_name = scope.roleName,
      bank_id = scope.bankId
    )
  }
  
  def createScopeJSONs(l: List[Scope]): ScopeJsons = {
    ScopeJsons(l.map(createScopeJson))
  }

  def createBanksJson(l: List[Bank]): BanksJson = {
    BanksJson(l.map(JSONFactory.createBankJSON))
  }
  
  
  def createAggregateMetricJson(aggregateMetrics: List[AggregateMetrics]) = {
      aggregateMetrics.map(
        aggregateMetric => AggregateMetricJSON(
          aggregateMetric.totalCount, 
          aggregateMetric.avgResponseTime, 
          aggregateMetric.minResponseTime,
          aggregateMetric.maxResponseTime
        )
      )
  }
}
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

 */
package code.api.v3_0_0

import code.api.util.APIUtil._
import code.api.v1_2_1.JSONFactory._
import code.api.v1_2_1._
import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0.JSONFactory200
import code.api.v2_0_0.JSONFactory200.CoreTransactionDetailsJSON
import code.model._

//started - view relevant case classes

case class ViewsJsonV300(
  views : List[ViewJsonV300]
)
case class ViewJsonV300(
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
  val can_see_where_tag: Boolean,
  //V300 new 
  val can_see_bank_routing_scheme: Boolean,
  val can_see_bank_routing_address: Boolean,
  val can_see_bank_account_routing_scheme: Boolean,
  val can_see_bank_account_routing_address: Boolean,
  val can_see_other_bank_routing_scheme: Boolean,
  val can_see_other_bank_routing_address: Boolean,
  val can_see_other_account_routing_scheme: Boolean,
  val can_see_other_account_routing_address: Boolean
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
  account_routing: AccountRoutingJsonV121,
  holders: List[AccountHolderJSON],
  kind: String
)

case class OtherAccountJsonV300(
  id: String,
  bank_routing: BankRoutingJsonV121,
  account_routing: AccountRoutingJsonV121,
  kind: String,
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
  bank_routing: BankRoutingJsonV121,
  account_routing: AccountRoutingJsonV121,
  kind: String
)

case class CoreTransactionJsonV300(
  id: String,
  account: ThisAccountJsonV300,
  counterparty: CoreCounterpartyJsonV300,
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
  account_routing: AccountRoutingJsonV121
)
case class CoreAccountJsonV300(
  id : String,
  label : String,
  bank_id : String,
  account_routing: AccountRoutingJsonV121
)
case class CoreAccountsJsonV300( accounts: List[CoreAccountJsonV300])

case class CreateAccountJsonV300(
  user_id : String,
  label   : String,
  `type` : String,
   balance : AmountOfMoneyJsonV121
)


case class ModeratedCoreAccountJSON(
  id: String,
  bank_id: String,
  label: String,
  number: String,
  owners: List[UserJSONV121],
  `type`: String,
  balance: AmountOfMoneyJsonV121,
  account_routing: AccountRoutingJsonV121
)

case class ElasticSearchJSON(es_uri_part: String, es_body_part: Any)

//ended -- account relevant case classes /////




case class OpeningTimesV300(
                             opening_time: String,
                             closing_time: String
                           )

case class LobbyJsonV330(
                        monday: OpeningTimesV300,
                        tuesday: OpeningTimesV300,
                        wednesday: OpeningTimesV300,
                        thursday: OpeningTimesV300,
                        friday: OpeningTimesV300,
                        saturday: OpeningTimesV300,
                        sunday: OpeningTimesV300
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

case class BranchJsonV300(
                           id: String,
                           bank_id: String,
                           name: String,
                           address: AddressJson,
                           location: LocationJson,
                           meta: MetaJson,
                           lobby: LobbyJsonV330,
                           drive_up: DriveUpJsonV330,
                           branch_routing: BranchRoutingJsonV141,
                           // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
                           is_accessible : String,
                           branch_type : String,
                           more_info : String
                         )


object JSONFactory300{
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
      kind = stringOptionOrNull(bankAccount.accountType),
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      account_routing = AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
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
      kind = stringOptionOrNull(bankAccount.kind),
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      account_routing = AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      metadata = bankAccount.metadata.map(createOtherAccountMetaDataJSON).getOrElse(null)
    )
  }

  // following are create core transactions, without the meta data parts
  def createCoreTransactionsJSON(transactions: List[ModeratedTransaction]) : CoreTransactionsJsonV300 = {
    CoreTransactionsJsonV300(transactions.map(createCoreTransactionJSON))
  }

  def createCoreTransactionJSON(transaction : ModeratedTransaction) : CoreTransactionJsonV300 = {
    CoreTransactionJsonV300(
      id = transaction.id.value,
      account = transaction.bankAccount.map(createThisAccountJSON).getOrElse(null),
      counterparty = transaction.otherBankAccount.map(createCoreCounterparty).getOrElse(null),
      details = JSONFactory200.createCoreTransactionDetailsJSON(transaction)
    )
  }

  def createCoreCounterparty(bankAccount : ModeratedOtherBankAccount) : CoreCounterpartyJsonV300 = {
    CoreCounterpartyJsonV300(
      id = bankAccount.id,
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      account_routing = AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      kind = stringOptionOrNull(bankAccount.kind)
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
      can_see_other_account_routing_address= view.canSeeOtherAccountRoutingAddress
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
      AccountRoutingJsonV121(account.accountRoutingScheme,account.accountRoutingAddress)
    )




  def createBankAccountJSON(account : ModeratedBankAccount, viewsAvailable : List[ViewJsonV300]) : ModeratedAccountJsonV300 =  {
    val bankName = account.bankName.getOrElse("")
    ModeratedAccountJsonV300(
      account.accountId.value,
      stringOrNull(account.bankId.value),
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      createOwnersJSON(account.owners.getOrElse(Set()), bankName),
      stringOptionOrNull(account.accountType),
      createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance),
      viewsAvailable,
      AccountRoutingJsonV121(stringOptionOrNull(account.accountRoutingScheme),stringOptionOrNull(account.accountRoutingAddress))
    )
  }
  
  def createCoreBankAccountJSON(account : ModeratedBankAccount, viewsAvailable : List[ViewJsonV300]) : ModeratedCoreAccountJSON =  {
    val bankName = account.bankName.getOrElse("")
    new ModeratedCoreAccountJSON (
      account.accountId.value,
      stringOrNull(account.bankId.value),
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      createOwnersJSON(account.owners.getOrElse(Set()), bankName),
      stringOptionOrNull(account.accountType),
      createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance),
      AccountRoutingJsonV121(stringOptionOrNull(account.accountRoutingScheme),stringOptionOrNull(account.accountRoutingAddress))
    )
  }
  
  
}
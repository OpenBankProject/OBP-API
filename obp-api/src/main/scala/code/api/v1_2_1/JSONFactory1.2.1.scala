/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.v1_2_1

import code.api.Constant._
import code.api.util.APIUtil
import code.api.util.APIUtil._
import code.api.util.ErrorMessages.MandatoryPropertyIsNotSet
import code.model._
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Full}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue

import java.util.Date

case class APIInfoJSON(
  version : String,
  version_status: String,
  git_commit : String,
  connector : String,
  hosted_by : HostedBy
)
case class HostedBy(
  organisation : String,
  email : String,
  phone : String,
  organisation_website: String
)
case class RateLimiting(enabled: Boolean, technology: String, service_available: Boolean, is_active: Boolean)

case class ErrorMessage(code: Int,
                        message : String
)
case class SuccessMessage(
  success : String
)
case class BanksJSON(
  banks : List[BankJSON]
)
case class MinimalBankJSON(
  national_identifier : String,
  name : String
)
case class BankJSON(
  id : String,
  short_name : String,
  full_name : String,
  logo : String,
  website : String,
  bank_routing: BankRoutingJsonV121
)

case class BankRoutingJsonV121(
  scheme: String,
  address: String
)

case class ViewsJSONV121(
  views : List[ViewJSONV121]
)

case class ViewJSONV121(
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

case class CreateViewJsonV121(
  name: String,
  description: String,
  is_public: Boolean,
  which_alias_to_use: String,
  hide_metadata_if_alias_used: Boolean,
  allowed_actions : List[String]
)

case class UpdateViewJsonV121(
  description: String,
  is_public: Boolean,
  which_alias_to_use: String,
  hide_metadata_if_alias_used: Boolean,
  allowed_actions: List[String]
)

case class AccountsJSON(
  accounts : List[AccountJSON]
)
case class AccountJSON(
  id : String,
  label : String,
  views_available : List[ViewJSONV121],
  bank_id : String
)
case class TransactionIdJson(transaction_id : String)

case class UpdateAccountJSON(
  id : String,
  label : String,
  bank_id : String
)

case class ModeratedAccountJSON(
  id : String,
  label : String,
  number : String,
  owners : List[UserJSONV121],
  `type` : String,
  balance : AmountOfMoneyJsonV121,
  IBAN : String,
  swift_bic: String,
  views_available : List[ViewJSONV121],
  bank_id : String,
  account_routing :AccountRoutingJsonV121
)
case class UserJSONV121(
  id : String,
  provider : String,
  display_name : String
)
case class PermissionsJSON(
  permissions : List[PermissionJSON]
)
case class PermissionJSON(
  user : UserJSONV121,
  views : List[ViewJSONV121]
)

case class AccountHolderJSON(
  name : String,
  is_alias : Boolean
)
case class ThisAccountJSON(
  id : String,
  holders : List[AccountHolderJSON],
  number : String,
  kind : String,
  IBAN : String,
  swift_bic: String,
  bank : MinimalBankJSON
)
case class OtherAccountsJSON(
  other_accounts : List[OtherAccountJSON]
)
case class OtherAccountJSON(
  id : String,
  holder : AccountHolderJSON,
  number : String,
  kind : String,
  IBAN : String,
  swift_bic: String,
  bank : MinimalBankJSON,
  metadata : OtherAccountMetadataJSON
)
case class OtherAccountMetadataJSON(
  public_alias : String,
  private_alias : String,
  more_info : String,
  URL : String,
  image_URL : String,
  open_corporates_URL : String,
  corporate_location : LocationJSONV121,
  physical_location : LocationJSONV121
)
case class LocationJSONV121(
  latitude : Double,
  longitude : Double,
  date : Date,
  user : UserJSONV121
)
case class TransactionDetailsJSON(
  `type` : String,
  description : String,
  posted : Date,
  completed : Date,
  new_balance : AmountOfMoneyJsonV121,
  value : AmountOfMoneyJsonV121
)
case class TransactionMetadataJSON(
  narrative : String,
  comments : List[TransactionCommentJSON],
  tags :  List[TransactionTagJSON],
  images :  List[TransactionImageJSON],
  where : LocationJSONV121
)
case class TransactionsJSON(
  transactions: List[TransactionJSON]
)
case class TransactionJSON(
  id : String,
  this_account : ThisAccountJSON,
  other_account : OtherAccountJSON,
  details : TransactionDetailsJSON,
  metadata : TransactionMetadataJSON
)
case class TransactionImagesJSON(
  images : List[TransactionImageJSON]
)
case class TransactionImageJSON(
  id : String,
  label : String,
  URL : String,
  date : Date,
  user : UserJSONV121
)
case class PostTransactionImageJSON(
  label : String,
  URL : String
)
case class PostTransactionCommentJSON(
  value: String
)
case class PostTransactionTagJSON(
  value : String
)
case class TransactionTagJSON(
  id : String,
  value : String,
  date : Date,
  user : UserJSONV121
)
case class TransactionTagsJSON(
  tags: List[TransactionTagJSON]
)
case class TransactionCommentJSON(
  id : String,
  value : String,
  date: Date,
  user : UserJSONV121
)
case class TransactionCommentsJSON(
  comments: List[TransactionCommentJSON]
)
case class TransactionWhereJSON(
  where: LocationJSONV121
)
case class PostTransactionWhereJSON(
  where: LocationPlainJSON
)
case class AliasJSON(
  alias: String
)
case class MoreInfoJSON(
  more_info: String
)
case class UrlJSON(
  URL:String
)
case class ImageUrlJSON(
  image_URL: String
)
case class OpenCorporateUrlJSON(
  open_corporates_URL: String
)
case class CorporateLocationJSON(
  corporate_location: LocationPlainJSON
)
case class PhysicalLocationJSON(
  physical_location: LocationPlainJSON
)
case class LocationPlainJSON(
  latitude : Double,
  longitude : Double
)
case class TransactionNarrativeJSON(
  narrative : String
)

case class ViewIdsJson(
  views : List[String]
)

case class MakePaymentJson(
  bank_id : String,
  account_id : String,
  amount : String
)

object JSONFactory{

  def getApiInfoJSON(apiVersion : ApiVersion, apiVersionStatus : String) = {
    val apiDetails: JValue = {

      val organisation = APIUtil.getPropsValue("hosted_by.organisation", "TESOBE")
      val email = APIUtil.getPropsValue("hosted_by.email", "contact@tesobe.com")
      val phone = APIUtil.getPropsValue("hosted_by.phone", "+49 (0)30 8145 3994")
      val organisationWebsite = APIUtil.getPropsValue("organisation_website", "https://www.tesobe.com")

      val connector = code.api.Constant.CONNECTOR.openOrThrowException(s"$MandatoryPropertyIsNotSet. The missing prop is `connector` ")

      val hostedBy = new HostedBy(organisation, email, phone, organisationWebsite)
      val apiInfoJSON = new APIInfoJSON(apiVersion.vDottedApiVersion, apiVersionStatus, gitCommit, connector, hostedBy)
      Extraction.decompose(apiInfoJSON)
    }
    apiDetails
  }
  
  def createBankJSON(bank : Bank) : BankJSON = {
    new BankJSON(
      stringOrNull(bank.bankId.value),
      stringOrNull(bank.shortName),
      stringOrNull(bank.fullName),
      stringOrNull(bank.logoUrl),
      stringOrNull(bank.websiteUrl),
      BankRoutingJsonV121(bank.bankRoutingScheme,bank.bankRoutingAddress)
    )
  }

  def createViewsJSON(views : List[View]) : ViewsJSONV121 = {
    val list : List[ViewJSONV121] = views.map(createViewJSON)
    new ViewsJSONV121(list)
  }

  def createViewJSON(view : View) : ViewJSONV121 = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    val allowed_actions = view.allowed_actions
    
    new ViewJSONV121(
      id = view.viewId.value,
      short_name = stringOrNull(view.name),
      description = stringOrNull(view.description),
      is_public = view.isPublic,
      alias = alias,
      hide_metadata_if_alias_used = view.hideOtherAccountMetadataIfAlias,
      can_add_comment = allowed_actions.exists(_ == CAN_ADD_COMMENT),
      can_add_corporate_location = allowed_actions.exists(_ == CAN_ADD_CORPORATE_LOCATION),
      can_add_image = allowed_actions.exists(_ == CAN_ADD_IMAGE),
      can_add_image_url = allowed_actions.exists(_ == CAN_ADD_IMAGE_URL),
      can_add_more_info = allowed_actions.exists(_ == CAN_ADD_MORE_INFO),
      can_add_open_corporates_url = allowed_actions.exists(_ == CAN_ADD_OPEN_CORPORATES_URL),
      can_add_physical_location = allowed_actions.exists(_ == CAN_ADD_PHYSICAL_LOCATION),
      can_add_private_alias = allowed_actions.exists(_ == CAN_ADD_PRIVATE_ALIAS),
      can_add_public_alias = allowed_actions.exists(_ == CAN_ADD_PUBLIC_ALIAS),
      can_add_tag = allowed_actions.exists(_ == CAN_ADD_TAG),
      can_add_url = allowed_actions.exists(_ == CAN_ADD_URL),
      can_add_where_tag = allowed_actions.exists(_ == CAN_ADD_WHERE_TAG),
      can_delete_comment = allowed_actions.exists(_ == CAN_DELETE_COMMENT),
      can_delete_corporate_location = allowed_actions.exists(_ == CAN_DELETE_CORPORATE_LOCATION),
      can_delete_image = allowed_actions.exists(_ == CAN_DELETE_IMAGE),
      can_delete_physical_location = allowed_actions.exists(_ == CAN_DELETE_PHYSICAL_LOCATION),
      can_delete_tag = allowed_actions.exists(_ == CAN_DELETE_TAG),
      can_delete_where_tag = allowed_actions.exists(_ == CAN_DELETE_WHERE_TAG),
      can_edit_owner_comment = allowed_actions.exists(_ == CAN_EDIT_OWNER_COMMENT),
      can_see_bank_account_balance = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_BALANCE),
      can_see_bank_account_bank_name = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_BANK_NAME),
      can_see_bank_account_currency = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_CURRENCY),
      can_see_bank_account_iban = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_IBAN),
      can_see_bank_account_label = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_LABEL),
      can_see_bank_account_national_identifier = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER),
      can_see_bank_account_number = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_NUMBER),
      can_see_bank_account_owners = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_OWNERS),
      can_see_bank_account_swift_bic = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_SWIFT_BIC),
      can_see_bank_account_type = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_TYPE),
      can_see_comments = allowed_actions.exists(_ == CAN_SEE_COMMENTS),
      can_see_corporate_location = allowed_actions.exists(_ == CAN_SEE_CORPORATE_LOCATION),
      can_see_image_url = allowed_actions.exists(_ == CAN_SEE_IMAGE_URL),
      can_see_images = allowed_actions.exists(_ == CAN_SEE_IMAGES),
      can_see_more_info = allowed_actions.exists(_ == CAN_SEE_MORE_INFO),
      can_see_open_corporates_url = allowed_actions.exists(_ == CAN_SEE_OPEN_CORPORATES_URL),
      can_see_other_account_bank_name = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_BANK_NAME),
      can_see_other_account_iban = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_IBAN),
      can_see_other_account_kind = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_KIND),
      can_see_other_account_metadata = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_METADATA),
      can_see_other_account_national_identifier = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER),
      can_see_other_account_number = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_NUMBER),
      can_see_other_account_swift_bic = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC),
      can_see_owner_comment = allowed_actions.exists(_ == CAN_SEE_OWNER_COMMENT),
      can_see_physical_location = allowed_actions.exists(_ == CAN_SEE_PHYSICAL_LOCATION),
      can_see_private_alias = allowed_actions.exists(_ == CAN_SEE_PRIVATE_ALIAS),
      can_see_public_alias = allowed_actions.exists(_ == CAN_SEE_PUBLIC_ALIAS),
      can_see_tags = allowed_actions.exists(_ == CAN_SEE_TAGS),
      can_see_transaction_amount = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_AMOUNT),
      can_see_transaction_balance = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_BALANCE),
      can_see_transaction_currency = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_CURRENCY),
      can_see_transaction_description = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_DESCRIPTION),
      can_see_transaction_finish_date = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_FINISH_DATE),
      can_see_transaction_metadata = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_METADATA),
      can_see_transaction_other_bank_account = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT),
      can_see_transaction_start_date = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_START_DATE),
      can_see_transaction_this_bank_account = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT),
      can_see_transaction_type = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_TYPE),
      can_see_url = allowed_actions.exists(_ == CAN_SEE_URL),
      can_see_where_tag = allowed_actions.exists(_ == CAN_SEE_WHERE_TAG)
    )
  }

  def createAccountJSON(account : BankAccount, viewsAvailable : List[ViewJSONV121] ) : AccountJSON = {
    new AccountJSON(
      account.accountId.value,
      stringOrNull(account.label),
      viewsAvailable,
      account.bankId.value
    )
  }

  def createBankAccountJSON(account : ModeratedBankAccount, viewsAvailable : List[ViewJSONV121]) : ModeratedAccountJSON =  {
    val bankName = account.bankName.getOrElse("")
    new ModeratedAccountJSON(
      account.accountId.value,
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      createOwnersJSON(account.owners.getOrElse(Set()), bankName),
      stringOptionOrNull(account.accountType),
      createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance),
      stringOptionOrNull(account.iban),
      stringOptionOrNull(None),//set it None for V121
      viewsAvailable,
      stringOrNull(account.bankId.value),
      AccountRoutingJsonV121(stringOptionOrNull(account.accountRoutingScheme),stringOptionOrNull(account.accountRoutingAddress))
    )
  }

  def createTransactionsJSON(transactions: List[ModeratedTransaction]) : TransactionsJSON = {
    new TransactionsJSON(transactions.map(createTransactionJSON))
  }

  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJSON = {
    new TransactionJSON(
        id = transaction.id.value,
        this_account = transaction.bankAccount.map(createThisAccountJSON).getOrElse(null),
        other_account = transaction.otherBankAccount.map(createOtherBankAccount).getOrElse(null),
        details = createTransactionDetailsJSON(transaction),
        metadata = transaction.metadata.map(createTransactionMetadataJSON).getOrElse(null)
      )
  }

  def createTransactionCommentsJSON(comments : List[Comment]) : TransactionCommentsJSON = {
    new TransactionCommentsJSON(comments.map(createTransactionCommentJSON))
  }

  def createTransactionCommentJSON(comment : Comment) : TransactionCommentJSON = {
    new TransactionCommentJSON(
      id = comment.id_,
      value = comment.text,
      date = comment.datePosted,
      user = createUserJSON(comment.postedBy)
    )
  }

  def createTransactionImagesJSON(images : List[TransactionImage]) : TransactionImagesJSON = {
    new TransactionImagesJSON(images.map(createTransactionImageJSON))
  }

  def createTransactionImageJSON(image : TransactionImage) : TransactionImageJSON = {
    new TransactionImageJSON(
      id = image.id_,
      label = image.description,
      URL = image.imageUrl.toString,
      date = image.datePosted,
      user = createUserJSON(image.postedBy)
    )
  }

  def createTransactionTagsJSON(tags : List[TransactionTag]) : TransactionTagsJSON = {
    new TransactionTagsJSON(tags.map(createTransactionTagJSON))
  }

  def createTransactionTagJSON(tag : TransactionTag) : TransactionTagJSON = {
    new TransactionTagJSON(
      id = tag.id_,
      value = tag.value,
      date = tag.datePosted,
      user = createUserJSON(tag.postedBy)
    )
  }

  def createLocationJSON(loc : Option[GeoTag]) : LocationJSONV121 = {
    loc match {
      case Some(location) => {
        val user = createUserJSON(location.postedBy)
        //test if the GeoTag is set to its default value
        if(location.latitude == 0.0 & location.longitude == 0.0 & user == null)
          null
        else
          new LocationJSONV121(
            latitude = location.latitude,
            longitude = location.longitude,
            date = location.datePosted,
            user = user
          )
      }
      case _ => null
    }
  }

  def createLocationPlainJSON(lat: Double, lon: Double) : LocationPlainJSON = {
    new LocationPlainJSON(
      latitude = lat,
      longitude = lon
      )
  }

  def createTransactionMetadataJSON(metadata : ModeratedTransactionMetadata) : TransactionMetadataJSON = {
    new TransactionMetadataJSON(
      narrative = stringOptionOrNull(metadata.ownerComment),
      comments = metadata.comments.map(_.map(createTransactionCommentJSON)).getOrElse(null),
      tags = metadata.tags.map(_.map(createTransactionTagJSON)).getOrElse(null),
      images = metadata.images.map(_.map(createTransactionImageJSON)).getOrElse(null),
      where = metadata.whereTag.map(createLocationJSON).getOrElse(null)
    )
  }

  def createTransactionDetailsJSON(transaction : ModeratedTransaction) : TransactionDetailsJSON = {
    new TransactionDetailsJSON(
      `type` = stringOptionOrNull(transaction.transactionType),
      description = stringOptionOrNull(transaction.description),
      posted = transaction.startDate.getOrElse(null),
      completed = transaction.finishDate.getOrElse(null),
      new_balance = createAmountOfMoneyJSON(transaction.currency, transaction.balance),
      value= createAmountOfMoneyJSON(transaction.currency, transaction.amount.map(_.toString))
    )
  }

  def createMinimalBankJSON(bankAccount : ModeratedBankAccount) : MinimalBankJSON = {
    new MinimalBankJSON(
      national_identifier = stringOptionOrNull(bankAccount.nationalIdentifier),
      name = stringOptionOrNull(bankAccount.bankName)
    )
  }

  def createMinimalBankJSON(bankAccount : ModeratedOtherBankAccount) : MinimalBankJSON = {
    new MinimalBankJSON(
      national_identifier = stringOptionOrNull(bankAccount.nationalIdentifier),
      name = stringOptionOrNull(bankAccount.bankName)
    )
  }

  def createThisAccountJSON(bankAccount : ModeratedBankAccount) : ThisAccountJSON = {
    new ThisAccountJSON(
      id = bankAccount.accountId.value,
      number = stringOptionOrNull(bankAccount.number),
      kind = stringOptionOrNull(bankAccount.accountType),
      IBAN = stringOptionOrNull(bankAccount.iban),
      swift_bic = stringOptionOrNull(None),//set it None for V121
      bank = createMinimalBankJSON(bankAccount),
      holders = bankAccount.owners.map(x => x.toList.map(h => createAccountHolderJSON(h, false))).getOrElse(null)
    )
  }

  def createAccountHolderJSON(owner : User, isAlias : Boolean) : AccountHolderJSON = {
    new AccountHolderJSON(
      name = owner.name,
      is_alias = isAlias
    )
  }

  def createAccountHolderJSON(name : String, isAlias : Boolean) : AccountHolderJSON = {
    new AccountHolderJSON(
      name = name,
      is_alias = isAlias
    )
  }

  def createOtherAccountMetaDataJSON(metadata : ModeratedOtherBankAccountMetadata) : OtherAccountMetadataJSON = {
    new OtherAccountMetadataJSON(
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

  def createOtherBankAccount(bankAccount : ModeratedOtherBankAccount) : OtherAccountJSON = {
    new OtherAccountJSON(
      id = bankAccount.id,
      number = stringOptionOrNull(bankAccount.number),
      kind = stringOptionOrNull(bankAccount.kind),
      IBAN = stringOptionOrNull(bankAccount.iban),
      swift_bic = stringOptionOrNull(None),//set it None for V121
      bank = createMinimalBankJSON(bankAccount),
      holder = createAccountHolderJSON(bankAccount.label.display, bankAccount.isAlias),
      metadata = bankAccount.metadata.map(createOtherAccountMetaDataJSON).getOrElse(null)
    )
  }

  def createOtherBankAccountsJSON(otherBankAccounts : List[ModeratedOtherBankAccount]) : OtherAccountsJSON =  {
    val otherAccountsJSON : List[OtherAccountJSON] = otherBankAccounts.map(createOtherBankAccount)
    OtherAccountsJSON(otherAccountsJSON)
  }

  def createUserJSON(user : User) : UserJSONV121 = {
    new UserJSONV121(
          user.idGivenByProvider,
          stringOrNull(user.provider),
          stringOrNull(user.emailAddress) //TODO: shouldn't this be the display name?
        )
  }

  def createUserJSON(user : Box[User]) : UserJSONV121 = {
    user match {
      case Full(u) => createUserJSON(u)
      case _ => null
    }
  }

  def createOwnersJSON(owners : Set[User], bankName : String) : List[UserJSONV121] = {
    owners.map(o => {
        new UserJSONV121(
          o.idGivenByProvider,
          stringOrNull(o.provider),
          stringOrNull(o.name)
        )
      }
    ).toList
  }

  def createAmountOfMoneyJSON(currency : String, amount  : String) : AmountOfMoneyJsonV121 = {
    new AmountOfMoneyJsonV121(
      stringOrNull(currency),
      stringOrNull(amount)
    )
  }

  def createAmountOfMoneyJSON(currency : Option[String], amount  : Option[String]) : AmountOfMoneyJsonV121 = {
    new AmountOfMoneyJsonV121(
      stringOptionOrNull(currency),
      stringOptionOrNull(amount)
    )
  }

  def createAmountOfMoneyJSON(currency : Option[String], amount  : String) : AmountOfMoneyJsonV121 = {
    new AmountOfMoneyJsonV121(
      stringOptionOrNull(currency),
      stringOrNull(amount)
    )
  }

  def createPermissionsJSON(permissions : List[Permission]) : PermissionsJSON = {
    val permissionsJson = permissions.map(p => {
        new PermissionJSON(
          createUserJSON(p.user),
          p.views.map(createViewJSON)
        )
      })
    new PermissionsJSON(permissionsJson)
  }

  def createAliasJSON(alias: String): AliasJSON = {
    AliasJSON(stringOrNull(alias))
  }

  def createTransactionNarrativeJSON(narrative: String): TransactionNarrativeJSON = {
    TransactionNarrativeJSON(stringOrNull(narrative))
  }

}
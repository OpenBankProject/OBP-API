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
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.api.v1_2

import java.util.Date
import net.liftweb.common.{Box, Full}
import code.model._

case class APIInfoJSON(
  version : String,
  git_commit : String,
  hosted_by : HostedBy
)
case class HostedBy(
  organisation : String,
  email : String,
  phone : String
)
case class ErrorMessage(
  error : String
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
  website : String
)
case class ViewsJSON(
  views : List[ViewJSON]
)
class ViewJSON(
  val id: String,
  val short_name: String,
  val description: String,
  val is_public: Boolean,
  val alias: String,
  val hide_metadata_if_alias: Boolean,
  val can_see_transaction_this_bank_account: Boolean,
  val can_see_transaction_other_bank_account: Boolean,
  val can_see_transaction_metadata: Boolean,
  val can_see_transaction_label: Boolean,
  val can_see_transaction_amount: Boolean,
  val can_see_transaction_type: Boolean,
  val can_see_transaction_currency: Boolean,
  val can_see_transaction_start_date: Boolean,
  val can_see_transaction_finish_date: Boolean,
  val can_see_transaction_balance: Boolean,
  val can_see_comments: Boolean,
  val can_see_owner_comment: Boolean,
  val can_see_tags: Boolean,
  val can_see_images: Boolean,
  val can_see_bank_account_owners: Boolean,
  val can_see_bank_account_type: Boolean,
  val can_see_bank_account_balance: Boolean,
  val can_see_bank_account_currency: Boolean,
  val can_see_bank_account_label: Boolean,
  val can_see_bank_account_national_identifier: Boolean,
  val can_see_bank_account_swift_bic: Boolean,
  val can_see_bank_account_iban: Boolean,
  val can_see_bank_account_number: Boolean,
  val can_see_bank_account_bank_name: Boolean,
  val can_see_other_account_national_identifier: Boolean,
  val can_see_other_account_swift_bic: Boolean,
  val can_see_other_account_iban: Boolean,
  val can_see_other_account_bank_name: Boolean,
  val can_see_other_account_number: Boolean,
  val can_see_other_account_metadata: Boolean,
  val can_see_other_account_kind: Boolean,
  val can_see_more_info: Boolean,
  val can_see_url: Boolean,
  val can_see_image_url: Boolean,
  val can_see_open_corporates_url: Boolean,
  val can_see_corporate_location: Boolean,
  val can_see_physical_location: Boolean,
  val can_see_public_alias: Boolean,
  val can_see_private_alias: Boolean,
  val can_add_more_info: Boolean,
  val can_add_url: Boolean,
  val can_add_image_url: Boolean,
  val can_add_open_corporates_url : Boolean,
  val can_add_corporate_location : Boolean,
  val can_add_physical_location : Boolean,
  val can_add_public_alias : Boolean,
  val can_add_private_alias : Boolean,
  val can_delete_corporate_location : Boolean,
  val can_delete_physical_location : Boolean,
  val can_edit_owner_comment: Boolean,
  val can_add_comment : Boolean,
  val can_delete_comment: Boolean,
  val can_add_tag : Boolean,
  val can_delete_tag : Boolean,
  val can_add_image : Boolean,
  val can_delete_image : Boolean,
  val can_add_where_tag : Boolean,
  val can_see_where_tag : Boolean,
  val can_delete_where_tag : Boolean
)
case class AccountsJSON(
  accounts : List[AccountJSON]
)
case class AccountJSON(
  id : String,
  label : String,
  views_available : List[ViewJSON],
  bank_id : String
)
case class ModeratedAccountJSON(
  id : String,
  label : String,
  number : String,
  owners : List[UserJSON],
  `type` : String,
  balance : AmountOfMoneyJSON,
  IBAN : String,
  views_available : List[ViewJSON],
  bank_id : String
)
case class UserJSON(
  id : String,
  provider : String,
  display_name : String
)
case class PermissionsJSON(
  permissions : List[PermissionJSON]
)
case class PermissionJSON(
  user : UserJSON,
  views : List[ViewJSON]
)
case class AmountOfMoneyJSON(
  currency : String,
  amount : String
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
  corporate_location : LocationJSON,
  physical_location : LocationJSON
)
case class LocationJSON(
  latitude : Double,
  longitude : Double,
  date : Date,
  user : UserJSON
)
case class TransactionDetailsJSON(
  `type` : String,
  label : String,
  posted : Date,
  completed : Date,
  new_balance : AmountOfMoneyJSON,
  value : AmountOfMoneyJSON
)
case class TransactionMetadataJSON(
  narrative : String,
  comments : List[TransactionCommentJSON],
  tags :  List[TransactionTagJSON],
  images :  List[TransactionImageJSON],
  where : LocationJSON
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
  user : UserJSON
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
  user : UserJSON
)
case class TransactionTagsJSON(
  tags: List[TransactionTagJSON]
)
case class TransactionCommentJSON(
  id : String,
  value : String,
  date: Date,
  user : UserJSON
)
case class TransactionCommentsJSON(
  comments: List[TransactionCommentJSON]
)
case class TransactionWhereJSON(
  where: LocationJSON
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
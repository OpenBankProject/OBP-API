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
import code.model._
//import net.liftweb.common.Box
//import net.liftweb.json.Extraction
//import net.liftweb.json.JsonAST.JValue


case class ViewsJSON(
                      views : List[ViewJSON]
                    )
class ViewJSON(
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

case class AccountsJSON(
                         accounts : List[AccountJSON]
                       )
case class AccountJSON(
                        id : String,
                        label : String,
                        views_available : List[ViewJSON],
                        bank_id : String
                      )

object JSONFactory220{

  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text

  def createViewsJSON(views : List[View]) : ViewsJSON = {
    val list : List[ViewJSON] = views.map(createViewJSON)
    new ViewsJSON(list)
  }

  def createViewJSON(view : View) : ViewJSON = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    new ViewJSON(
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



}
/**
Open Bank Project - API
Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
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
package code.api.v2_0_0

import java.util.Date
import code.kycdocuments.KycDocument
import code.kycmedias.KycMedia
import code.kycstatuses.KycStatus
import code.kycchecks.KycCheck
import code.socialmedia.SocialMedia
import net.liftweb.common.{Box, Full}
import code.model._





// New in 2.0.0

class ViewBasicJSON(
  val id: String,
  val short_name: String,
  val is_public: Boolean
)

case class AccountsBasicJSON(
  accounts : List[AccountBasicJSON]
)

case class AccountBasicJSON(
  id : String,
  label : String,
  views_available : List[ViewBasicJSON],
  bank_id : String
)

case class KycDocumentJSON(
  id: String,
  customer_number: String,
  `type`: String,
  number: String,
  issue_date: Date,
  issue_place: String,
  expiry_date: Date
)
case class KycDocumentsJSON(documents: List[KycDocumentJSON])

case class KycMediaJSON(
  id: String,
  customer_number: String,
  `type`: String,
  url: String,
  date: Date,
  relates_to_kyc_document_id: String,
  relates_to_kyc_check_id: String
)
case class KycMediasJSON(medias: List[KycMediaJSON])

case class KycCheckJSON(
  id: String,
  customer_number: String,
  date: Date,
  how: String,
  staff_user_id: String,
  staff_name: String,
  satisfied: Boolean,
  comments: String
)
case class KycChecksJSON(checks: List[KycCheckJSON])

case class KycStatusJSON(
   customer_number: String,
   ok: Boolean,
   date: Date
)
case class KycStatusesJSON(statuses: List[KycStatusJSON])

case class SocialMediaJSON(
   customer_number: String,
   `type`: String,
   handle: String,
   date_added: Date,
   date_activated: Date
)
case class SocialMediasJSON(checks: List[SocialMediaJSON])

object JSONFactory{


  // New in 2.0.0

  def createViewBasicJSON(view : View) : ViewBasicJSON = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    new ViewBasicJSON(
      id = view.viewId.value,
      short_name = stringOrNull(view.name),
      is_public = view.isPublic
    )
  }


  def createAccountBasicJSON(account : BankAccount, viewsBasicAvailable : List[ViewBasicJSON] ) : AccountBasicJSON = {
    new AccountBasicJSON(
      account.accountId.value,
      stringOrNull(account.label),
      viewsBasicAvailable,
      account.bankId.value
    )
  }

  def createKycDocumentJSON(kycDocument : KycDocument) : KycDocumentJSON = {
    new KycDocumentJSON(
      id = kycDocument.idKycDocument,
      customer_number = kycDocument.customerNumber,
      `type` = kycDocument.`type`,
      number = kycDocument.number,
      issue_date = kycDocument.issueDate,
      issue_place = kycDocument.issuePlace,
      expiry_date = kycDocument.expiryDate
    )
  }

  def createKycDocumentsJSON(messages : List[KycDocument]) : KycDocumentsJSON = {
    KycDocumentsJSON(messages.map(createKycDocumentJSON))
  }

  def createKycMediaJSON(kycMedia : KycMedia) : KycMediaJSON = {
    new KycMediaJSON(
      id = kycMedia.idKycMedia,
      customer_number = kycMedia.customerNumber,
      `type` = kycMedia.`type`,
      url = kycMedia.url,
      date = kycMedia.date,
      relates_to_kyc_document_id = kycMedia.relatesToKycDocumentId,
      relates_to_kyc_check_id = kycMedia.relatesToKycCheckId
    )
  }
  def createKycMediasJSON(messages : List[KycMedia]) : KycMediasJSON = {
    KycMediasJSON(messages.map(createKycMediaJSON))
  }

  def createKycCheckJSON(kycCheck : KycCheck) : KycCheckJSON = {
    new KycCheckJSON(
      id = kycCheck.idKycCheck,
      customer_number = kycCheck.customerNumber,
      date = kycCheck.date,
      how = kycCheck.how,
      staff_user_id = kycCheck.staffUserId,
      staff_name = kycCheck.staffName,
      satisfied = kycCheck.satisfied,
      comments = kycCheck.comments
    )
  }
  def createKycChecksJSON(messages : List[KycCheck]) : KycChecksJSON = {
    KycChecksJSON(messages.map(createKycCheckJSON))
  }

  def createKycStatusJSON(kycStatus : KycStatus) : KycStatusJSON = {
    new KycStatusJSON(
      customer_number = kycStatus.customerNumber,
      ok = kycStatus.ok,
      date = kycStatus.date
    )
  }
  def createKycStatusesJSON(messages : List[KycStatus]) : KycStatusesJSON = {
    KycStatusesJSON(messages.map(createKycStatusJSON))
  }

  def createSocialMediaJSON(socialMedia : SocialMedia) : SocialMediaJSON = {
    new SocialMediaJSON(
      customer_number = socialMedia.customerNumber,
      `type` = socialMedia.`type`,
      handle = socialMedia.handle,
      date_added = socialMedia.dateAdded,
      date_activated = socialMedia.dateActivated
    )
  }
  def createSocialMediasJSON(messages : List[SocialMedia]) : SocialMediasJSON = {
    SocialMediasJSON(messages.map(createSocialMediaJSON))
  }
  // From 1.2.1

  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text



}
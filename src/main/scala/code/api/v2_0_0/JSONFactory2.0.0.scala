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

import java.net.URL
import java.util.Date
import code.api.util.APIUtil.ApiLink
import code.kycdocuments.KycDocument
import code.kycmedias.KycMedia
import code.kycstatuses.KycStatus
import code.kycchecks.KycCheck
import code.socialmedia.SocialMedia
import net.liftweb.common.{Box, Full}
import code.model._
import net.liftweb.json.JsonAST.JValue

// Import explicitly and rename so its clear.
import code.api.v1_2_1.{AccountHolderJSON => AccountHolderJSON121, AmountOfMoneyJSON => AmountOfMoneyJSON121, UserJSON => UserJSON121, ViewJSON => ViewJSON121, ThisAccountJSON => ThisAccountJSON121, OtherAccountJSON => OtherAccountJSON121, TransactionDetailsJSON => TransactionDetailsJSON121, JSONFactory => JSONFactory121, MinimalBankJSON => MinimalBankJSON121}





// New in 2.0.0

class LinkJSON(
  val href: URL,
  val rel: String,
  val method: String
)

class LinksJSON(
  val _links: List[LinkJSON]
)

class ResultAndLinksJSON(
  val result : JValue,
  val links: LinksJSON
)



class BasicViewJSON(
  val id: String,
  val short_name: String,
  val is_public: Boolean
)

case class BasicAccountsJSON(
  accounts : List[BasicAccountJSON]
)

// Basic Account has basic View
case class BasicAccountJSON(
                             id : String,
                             label : String,
                             views_available : List[BasicViewJSON],
                             bank_id : String
)

// No view in core
case class CoreAccountJSON(
                             id : String,
                             label : String,
                             bank_id : String,
                             _links: List[ApiLink]
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

object JSONFactory200{


  // New in 2.0.0







  def createViewBasicJSON(view : View) : BasicViewJSON = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    new BasicViewJSON(
      id = view.viewId.value,
      short_name = stringOrNull(view.name),
      is_public = view.isPublic
    )
  }


  def createBasicAccountJSON(account : BankAccount, basicViewsAvailable : List[BasicViewJSON] ) : BasicAccountJSON = {
    new BasicAccountJSON(
      account.accountId.value,
      stringOrNull(account.label),
      basicViewsAvailable,
      account.bankId.value
    )
  }

  // TODO This should include some more account info as long as the user has access to the owner view.
  def createCoreAccountJSON(account : BankAccount, viewsBasicAvailable : List[BasicViewJSON], links: List[ApiLink] ) : CoreAccountJSON = {
    val x = new CoreAccountJSON(
      account.accountId.value,
      stringOrNull(account.label),
      account.bankId.value,
      links
    )
    x
  }




  case class ModeratedCoreAccountJSON(
                                   id : String,
                                   label : String,
                                   number : String,
                                   owners : List[UserJSON121],
                                   `type` : String,
                                   balance : AmountOfMoneyJSON121,
                                   IBAN : String,
                                   swift_bic: String,
                                   bank_id : String
                                 )


  ////


  case class CoreTransactionsJSON(
                               transactions: List[CoreTransactionJSON]
                             )

  case class CoreTransactionJSON(
                              id : String,
                              account : ThisAccountJSON121,
                              counterparty : CoreCounterpartyJSON,
                              details : CoreTransactionDetailsJSON
                            )



  case class CoreAccountHolderJSON(
                                name : String
                              )


  case class CoreCounterpartyJSON(
                                   id : String,
                                   holder : CoreAccountHolderJSON,
                                   number : String,
                                   kind : String,
                                   IBAN : String,
                                   swift_bic: String,
                                   bank : MinimalBankJSON121
                             )




  def createCoreTransactionsJSON(transactions: List[ModeratedTransaction]) : CoreTransactionsJSON = {
    new CoreTransactionsJSON(transactions.map(createCoreTransactionJSON))
  }

  case class CoreTransactionDetailsJSON(
                                     `type` : String,
                                     description : String,
                                     posted : Date,
                                     completed : Date,
                                     new_balance : AmountOfMoneyJSON121,
                                     value : AmountOfMoneyJSON121
                                   )



  def createCoreTransactionDetailsJSON(transaction : ModeratedTransaction) : CoreTransactionDetailsJSON = {
    new CoreTransactionDetailsJSON(
      `type` = stringOptionOrNull(transaction.transactionType),
      description = stringOptionOrNull(transaction.description),
      posted = transaction.startDate.getOrElse(null),
      completed = transaction.finishDate.getOrElse(null),
      new_balance = JSONFactory121.createAmountOfMoneyJSON(transaction.currency, transaction.balance),
      value= JSONFactory121.createAmountOfMoneyJSON(transaction.currency, transaction.amount.map(_.toString))
    )
  }


  def createCoreTransactionJSON(transaction : ModeratedTransaction) : CoreTransactionJSON = {
    new CoreTransactionJSON(
      id = transaction.id.value,
      account = transaction.bankAccount.map(JSONFactory121.createThisAccountJSON).getOrElse(null),
      counterparty = transaction.otherBankAccount.map(createCoreCounterparty).getOrElse(null),
      details = createCoreTransactionDetailsJSON(transaction)
    )
  }





  case class CounterpartiesJSON(
                                 counterparties : List[CoreCounterpartyJSON]
                              )


  def createCoreCounterparty(bankAccount : ModeratedOtherBankAccount) : CoreCounterpartyJSON = {
    new CoreCounterpartyJSON(
      id = bankAccount.id,
      number = stringOptionOrNull(bankAccount.number),
      kind = stringOptionOrNull(bankAccount.kind),
      IBAN = stringOptionOrNull(bankAccount.iban),
      swift_bic = stringOptionOrNull(bankAccount.swift_bic),
      bank = JSONFactory121.createMinimalBankJSON(bankAccount),
      holder = createAccountHolderJSON(bankAccount.label.display, bankAccount.isAlias)
    )
  }



  def createAccountHolderJSON(owner : User, isAlias : Boolean) : CoreAccountHolderJSON = {
    // Note we are not using isAlias
    new CoreAccountHolderJSON(
      name = owner.name
    )
  }

  def createAccountHolderJSON(name : String, isAlias : Boolean) : CoreAccountHolderJSON = {
    // Note we are not using isAlias
    new CoreAccountHolderJSON(
      name = name
    )
  }



  def createCoreBankAccountJSON(account : ModeratedBankAccount, viewsAvailable : List[ViewJSON121]) : ModeratedCoreAccountJSON =  {
    val bankName = account.bankName.getOrElse("")
    new ModeratedCoreAccountJSON (
      account.accountId.value,
      JSONFactory121.stringOptionOrNull(account.label),
      JSONFactory121.stringOptionOrNull(account.number),
      JSONFactory121.createOwnersJSON(account.owners.getOrElse(Set()), bankName),
      JSONFactory121.stringOptionOrNull(account.accountType),
      JSONFactory121.createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance),
      JSONFactory121.stringOptionOrNull(account.iban),
      JSONFactory121.stringOptionOrNull(account.swift_bic),
      stringOrNull(account.bankId.value)
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

  // Copied from 1.2.1 (import just this def instead?)
  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text

  // Copied from 1.2.1 (import just this def instead?)
  def stringOptionOrNull(text : Option[String]) =
    text match {
      case Some(t) => stringOrNull(t)
      case _ => null
    }






}
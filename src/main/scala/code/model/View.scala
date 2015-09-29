/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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


package code.model

import java.util.Date
import code.util.Helper
import net.liftweb.common._
import code.views.Views
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JObject


class AliasType
class Alias extends AliasType
object PublicAlias extends Alias
object PrivateAlias extends Alias
object NoAlias extends AliasType
case class AccountName(display: String, aliasType: AliasType)
case class Permission(
  user : User,
  views : List[View]
)

trait ViewData {
  def description: String
  def is_public: Boolean
  def which_alias_to_use: String
  def hide_metadata_if_alias_used: Boolean
  def allowed_actions : List[String]
}

case class ViewCreationJSON(
  name: String,
  description: String,
  is_public: Boolean,
  which_alias_to_use: String,
  hide_metadata_if_alias_used: Boolean,
  allowed_actions : List[String]
) extends ViewData

case class ViewUpdateData(
  description: String,
  is_public: Boolean,
  which_alias_to_use: String,
  hide_metadata_if_alias_used: Boolean,
  allowed_actions: List[String]) extends ViewData

trait View {
  val viewLogger = Logger(classOf[View])
  //e.g. "Public", "Authorities", "Our Network", etc.

  //these ids are used together to uniquely identify a view
  def viewId : ViewId
  def accountId : AccountId
  def bankId : BankId

  //and here is the unique identifier
  def uid : ViewUID = ViewUID(viewId, bankId, accountId)

  def name: String
  def description : String
  def isPublic : Boolean
  def users: List[User]

  //the view settings
  def usePublicAliasIfOneExists: Boolean
  def usePrivateAliasIfOneExists: Boolean
  def hideOtherAccountMetadataIfAlias: Boolean

  //reading access

  //transaction fields
  def canSeeTransactionThisBankAccount : Boolean
  def canSeeTransactionOtherBankAccount : Boolean
  def canSeeTransactionMetadata : Boolean
  def canSeeTransactionDescription: Boolean
  def canSeeTransactionAmount: Boolean
  def canSeeTransactionType: Boolean
  def canSeeTransactionCurrency: Boolean
  def canSeeTransactionStartDate: Boolean
  def canSeeTransactionFinishDate: Boolean
  def canSeeTransactionBalance: Boolean

  //transaction metadata
  def canSeeComments: Boolean
  def canSeeOwnerComment: Boolean
  def canSeeTags : Boolean
  def canSeeImages : Boolean

  //Bank account fields
  def canSeeBankAccountOwners : Boolean
  def canSeeBankAccountType : Boolean
  def canSeeBankAccountBalance : Boolean
  def canSeeBankAccountCurrency : Boolean
  def canSeeBankAccountLabel : Boolean
  def canSeeBankAccountNationalIdentifier : Boolean
  def canSeeBankAccountSwift_bic : Boolean
  def canSeeBankAccountIban : Boolean
  def canSeeBankAccountNumber : Boolean
  def canSeeBankAccountBankName : Boolean

  //other bank account fields
  def canSeeOtherAccountNationalIdentifier : Boolean
  def canSeeOtherAccountSWIFT_BIC : Boolean
  def canSeeOtherAccountIBAN : Boolean
  def canSeeOtherAccountBankName : Boolean
  def canSeeOtherAccountNumber : Boolean
  def canSeeOtherAccountMetadata : Boolean
  def canSeeOtherAccountKind : Boolean

  //other bank account meta data
  def canSeeMoreInfo: Boolean
  def canSeeUrl: Boolean
  def canSeeImageUrl: Boolean
  def canSeeOpenCorporatesUrl: Boolean
  def canSeeCorporateLocation : Boolean
  def canSeePhysicalLocation : Boolean
  def canSeePublicAlias : Boolean
  def canSeePrivateAlias : Boolean
  def canAddMoreInfo : Boolean
  def canAddURL : Boolean
  def canAddImageURL : Boolean
  def canAddOpenCorporatesUrl : Boolean
  def canAddCorporateLocation : Boolean
  def canAddPhysicalLocation : Boolean
  def canAddPublicAlias : Boolean
  def canAddPrivateAlias : Boolean
  def canDeleteCorporateLocation : Boolean
  def canDeletePhysicalLocation : Boolean

  //writing access
  def canEditOwnerComment: Boolean
  def canAddComment : Boolean
  def canDeleteComment: Boolean
  def canAddTag : Boolean
  def canDeleteTag : Boolean
  def canAddImage : Boolean
  def canDeleteImage : Boolean
  def canAddWhereTag : Boolean
  def canSeeWhereTag : Boolean
  def canDeleteWhereTag : Boolean

  def canInitiateTransaction: Boolean  

  def moderate(transaction : Transaction): Box[ModeratedTransaction] = {
    moderate(transaction, moderate(transaction.thisAccount))
  }

  // In the future we can add a method here to allow someone to show only transactions over a certain limit
  private def moderate(transaction: Transaction, moderatedAccount : Option[ModeratedBankAccount]): Box[ModeratedTransaction] = {

    lazy val moderatedTransaction = {
      //transaction data
      val transactionId = transaction.id
      val transactionUUID = transaction.uuid
      val otherBankAccount = moderate(transaction.otherAccount)

      //transation metadata
      val transactionMetadata =
        if(canSeeTransactionMetadata)
        {
          val ownerComment = if (canSeeOwnerComment) Some(transaction.metadata.ownerComment()) else None
          val comments =
            if (canSeeComments)
              Some(transaction.metadata.comments(viewId))
            else None
          val addCommentFunc= if(canAddComment) Some(transaction.metadata.addComment) else None
          val deleteCommentFunc =
            if(canDeleteComment)
              Some(transaction.metadata.deleteComment)
            else
              None
          val addOwnerCommentFunc:Option[String=> Unit] = if (canEditOwnerComment) Some(transaction.metadata.addOwnerComment) else None
          val tags =
            if(canSeeTags)
              Some(transaction.metadata.tags(viewId))
            else None
          val addTagFunc =
            if(canAddTag)
              Some(transaction.metadata.addTag)
            else
              None
          val deleteTagFunc =
            if(canDeleteTag)
              Some(transaction.metadata.deleteTag)
            else
              None
          val images =
            if(canSeeImages) Some(transaction.metadata.images(viewId))
            else None

          val addImageFunc =
            if(canAddImage) Some(transaction.metadata.addImage)
            else None

          val deleteImageFunc =
            if(canDeleteImage) Some(transaction.metadata.deleteImage)
            else None

          val whereTag =
            if(canSeeWhereTag)
              Some(transaction.metadata.whereTags(viewId))
            else
              None

          val addWhereTagFunc : Option[(UserId, ViewId, Date, Double, Double) => Boolean] =
            if(canAddWhereTag)
              Some(transaction.metadata.addWhereTag)
            else
              Empty

          val deleteWhereTagFunc : Option[(ViewId) => Boolean] =
            if (canDeleteWhereTag)
              Some(transaction.metadata.deleteWhereTag)
            else
              Empty


          new Some(
            new ModeratedTransactionMetadata(
              ownerComment = ownerComment,
              addOwnerComment = addOwnerCommentFunc,
              comments = comments,
              addComment = addCommentFunc,
              deleteComment = deleteCommentFunc,
              tags = tags,
              addTag = addTagFunc,
              deleteTag = deleteTagFunc,
              images = images,
              addImage = addImageFunc,
              deleteImage = deleteImageFunc,
              whereTag = whereTag,
              addWhereTag = addWhereTagFunc,
              deleteWhereTag = deleteWhereTagFunc
            )
          )
        }
        else
          None

      val transactionType =
        if (canSeeTransactionType) Some(transaction.transactionType)
        else None

      val transactionAmount =
        if (canSeeTransactionAmount) Some(transaction.amount)
        else None

      val transactionCurrency =
        if (canSeeTransactionCurrency) Some(transaction.currency)
        else None

      val transactionDescription =
        if (canSeeTransactionDescription) transaction.description
        else None

      val transactionStartDate =
        if (canSeeTransactionStartDate) Some(transaction.startDate)
        else None

      val transactionFinishDate =
        if (canSeeTransactionFinishDate) Some(transaction.finishDate)
        else None

      val transactionBalance =
        if (canSeeTransactionBalance) transaction.balance.toString()
        else ""

      new ModeratedTransaction(
        UUID = transactionUUID,
        id = transactionId,
        bankAccount = moderatedAccount,
        otherBankAccount = otherBankAccount,
        metadata = transactionMetadata,
        transactionType = transactionType,
        amount = transactionAmount,
        currency = transactionCurrency,
        description = transactionDescription,
        startDate = transactionStartDate,
        finishDate = transactionFinishDate,
        balance = transactionBalance
      )
    }


    val belongsToModeratedAccount : Boolean = moderatedAccount match {
      case Some(acc) => acc.accountId == transaction.accountId && acc.bankId == transaction.bankId
      case None => true
    }

    if(!belongsToModeratedAccount) {
      val failMsg = "Attempted to moderate a transaction using the incorrect moderated account"
      viewLogger.warn(failMsg)
      Failure(failMsg)
    } else {
      Full(moderatedTransaction)
    }

  }

  def moderateTransactionsWithSameAccount(transactions : List[Transaction]) : Box[List[ModeratedTransaction]] = {

    val accountUids = transactions.map(t => BankAccountUID(t.bankId, t.accountId))

    if(accountUids.toSet.size > 1) {
      viewLogger.warn("Attempted to moderate transactions not belonging to the same account in a call where they should")
      Failure("Could not moderate transactions as they do not all belong to the same account")
    } else {
      transactions.headOption match {
        case Some(firstTransaction) =>
          val moderatedAccount = moderate(firstTransaction.thisAccount)
          Full(transactions.flatMap(t => moderate(t, moderatedAccount)))
        case None =>
          Full(Nil)
      }
    }
  }

  def moderate(bankAccount: BankAccount) : Option[ModeratedBankAccount] = {
    if(canSeeTransactionThisBankAccount)
    {
      val owners : Set[User] = if(canSeeBankAccountOwners) bankAccount.owners else Set()
      val balance = if(canSeeBankAccountBalance) bankAccount.balance.toString else ""
      val accountType = if(canSeeBankAccountType) Some(bankAccount.accountType) else None
      val currency = if(canSeeBankAccountCurrency) Some(bankAccount.currency) else None
      val label = if(canSeeBankAccountLabel) Some(bankAccount.label) else None
      val nationalIdentifier = if(canSeeBankAccountNationalIdentifier) Some(bankAccount.nationalIdentifier) else None
      val swiftBic = if(canSeeBankAccountSwift_bic) bankAccount.swift_bic else None
      val iban = if(canSeeBankAccountIban) bankAccount.iban else None
      val number = if(canSeeBankAccountNumber) Some(bankAccount.number) else None
      val bankName = if(canSeeBankAccountBankName) Some(bankAccount.bankName) else None
      val bankId = bankAccount.bankId

      Some(
        new ModeratedBankAccount(
          accountId = bankAccount.accountId,
          owners = Some(owners),
          accountType = accountType,
          balance = balance,
          currency = currency,
          label = label,
          nationalIdentifier = nationalIdentifier,
          swift_bic = swiftBic,
          iban = iban,
          number = number,
          bankName = bankName,
          bankId = bankId
        )
      )
    }
    else
      None
  }

  def moderate(otherBankAccount : OtherBankAccount) : Option[ModeratedOtherBankAccount] = {
    if (canSeeTransactionOtherBankAccount)
    {
      //other account data
      val otherAccountId = otherBankAccount.id
      val otherAccountLabel: AccountName = {
        val realName = otherBankAccount.label

        if (usePublicAliasIfOneExists) {

          val publicAlias = otherBankAccount.metadata.getPublicAlias

          if (! publicAlias.isEmpty ) AccountName(publicAlias, PublicAlias)
          else AccountName(realName, NoAlias)

        } else if (usePrivateAliasIfOneExists) {

          val privateAlias = otherBankAccount.metadata.getPrivateAlias

          if (! privateAlias.isEmpty) AccountName(privateAlias, PrivateAlias)
          else AccountName(realName, PrivateAlias)
        } else
          AccountName(realName, NoAlias)
      }

      def isAlias = otherAccountLabel.aliasType match {
        case NoAlias => false
        case _ => true
      }

      def moderateField[T](canSeeField: Boolean, field: T) : Option[T] = {
        if(isAlias & hideOtherAccountMetadataIfAlias)
            None
        else
          if(canSeeField)
            Some(field)
          else
            None
      }

      implicit def optionStringToString(x : Option[String]) : String = x.getOrElse("")
      val otherAccountNationalIdentifier = if(canSeeOtherAccountNationalIdentifier) Some(otherBankAccount.nationalIdentifier) else None
      val otherAccountSWIFT_BIC = if(canSeeOtherAccountSWIFT_BIC) otherBankAccount.swift_bic else None
      val otherAccountIBAN = if(canSeeOtherAccountIBAN) otherBankAccount.iban else None
      val otherAccountBankName = if(canSeeOtherAccountBankName) Some(otherBankAccount.bankName) else None
      val otherAccountNumber = if(canSeeOtherAccountNumber) Some(otherBankAccount.number) else None
      val otherAccountKind = if(canSeeOtherAccountKind) Some(otherBankAccount.kind) else None
      val otherAccountMetadata =
        if(canSeeOtherAccountMetadata){
          //other bank account metadata
          val moreInfo = moderateField(canSeeMoreInfo,otherBankAccount.metadata.getMoreInfo)
          val url = moderateField(canSeeUrl, otherBankAccount.metadata.getUrl)
          val imageUrl = moderateField(canSeeImageUrl, otherBankAccount.metadata.getImageURL)
          val openCorporatesUrl = moderateField (canSeeOpenCorporatesUrl, otherBankAccount.metadata.getOpenCorporatesURL)
          val corporateLocation : Option[Option[GeoTag]] = moderateField(canSeeCorporateLocation, otherBankAccount.metadata.getCorporateLocation)
          val physicalLocation : Option[Option[GeoTag]] = moderateField(canSeePhysicalLocation, otherBankAccount.metadata.getPhysicalLocation)
          val addMoreInfo = moderateField(canAddMoreInfo, otherBankAccount.metadata.addMoreInfo)
          val addURL = moderateField(canAddURL, otherBankAccount.metadata.addURL)
          val addImageURL = moderateField(canAddImageURL, otherBankAccount.metadata.addImageURL)
          val addOpenCorporatesUrl = moderateField(canAddOpenCorporatesUrl, otherBankAccount.metadata.addOpenCorporatesURL)
          val addCorporateLocation = moderateField(canAddCorporateLocation, otherBankAccount.metadata.addCorporateLocation)
          val addPhysicalLocation = moderateField(canAddPhysicalLocation, otherBankAccount.metadata.addPhysicalLocation)
          val publicAlias = moderateField(canSeePublicAlias, otherBankAccount.metadata.getPublicAlias)
          val privateAlias = moderateField(canSeePrivateAlias, otherBankAccount.metadata.getPrivateAlias)
          val addPublicAlias = moderateField(canAddPublicAlias, otherBankAccount.metadata.addPublicAlias)
          val addPrivateAlias = moderateField(canAddPrivateAlias, otherBankAccount.metadata.addPrivateAlias)
          val deleteCorporateLocation = moderateField(canDeleteCorporateLocation, otherBankAccount.metadata.deleteCorporateLocation)
          val deletePhysicalLocation= moderateField(canDeletePhysicalLocation, otherBankAccount.metadata.deletePhysicalLocation)

          Some(
            new ModeratedOtherBankAccountMetadata(
              moreInfo = moreInfo,
              url = url,
              imageURL = imageUrl,
              openCorporatesURL = openCorporatesUrl,
              corporateLocation = corporateLocation,
              physicalLocation = physicalLocation,
              publicAlias = publicAlias,
              privateAlias = privateAlias,
              addMoreInfo = addMoreInfo,
              addURL = addURL,
              addImageURL = addImageURL,
              addOpenCorporatesURL = addOpenCorporatesUrl,
              addCorporateLocation = addCorporateLocation,
              addPhysicalLocation = addPhysicalLocation,
              addPublicAlias = addPublicAlias,
              addPrivateAlias = addPrivateAlias,
              deleteCorporateLocation = deleteCorporateLocation,
              deletePhysicalLocation = deletePhysicalLocation
            )
          )
        }
        else
            None

      Some(
        new ModeratedOtherBankAccount(
          id = otherAccountId,
          label = otherAccountLabel,
          nationalIdentifier = otherAccountNationalIdentifier,
          swift_bic = otherAccountSWIFT_BIC,
          iban = otherAccountIBAN,
          bankName = otherAccountBankName,
          number = otherAccountNumber,
          metadata = otherAccountMetadata,
          kind = otherAccountKind
        )
      )
    }
    else
      None
  }

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def toJson : JObject = {
    ("name" -> name) ~
    ("description" -> description)
  }

}

object View {
  def fromUrl(viewId: ViewId, account: BankAccount): Box[View] =
    Views.views.vend.view(viewId, account)
  def fromUrl(viewId: ViewId, accountId: AccountId, bankId: BankId): Box[View] =
    Views.views.vend.view(ViewUID(viewId, bankId, accountId))

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def linksJson(views: List[View], accountId: AccountId, bankId: BankId): JObject = {
    val viewsJson = views.map(view => {
      ("rel" -> "account") ~
        ("href" -> { "/" + bankId + "/account/" + accountId + "/" + view.viewId }) ~
        ("method" -> "GET") ~
        ("title" -> "Get information about one account")
    })

    ("links" -> viewsJson)
  }
}
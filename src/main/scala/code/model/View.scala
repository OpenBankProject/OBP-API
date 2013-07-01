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

import net.liftweb.http.SHtml
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JObject
import net.liftweb.common.{Box, Empty, Full, Failure}
import java.util.Date


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

trait View {

  //e.g. "Public", "Authorities", "Our Network", etc.
  def id: Long
  def name: String
  def description : String
  def permalink : String
  def isPublic : Boolean

  //the view settings
  def usePrivateAliasIfOneExists: Boolean
  def usePublicAliasIfOneExists: Boolean

  //reading access

  //transaction fields
  def canSeeTransactionThisBankAccount : Boolean
  def canSeeTransactionOtherBankAccount : Boolean
  def canSeeTransactionMetadata : Boolean
  def canSeeTransactionLabel: Boolean
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
  def canSeeBankAccountBalancePositiveOrNegative : Boolean
  def canSeeBankAccountCurrency : Boolean
  def canSeeBankAccountLabel : Boolean
  def canSeeBankAccountNationalIdentifier : Boolean
  def canSeeBankAccountSwift_bic : Boolean
  def canSeeBankAccountIban : Boolean
  def canSeeBankAccountNumber : Boolean
  def canSeeBankAccountBankName : Boolean
  def canSeeBankAccountBankPermalink : Boolean

  //other bank account fields
  def canSeeOtherAccountNationalIdentifier : Boolean
  def canSeeSWIFT_BIC : Boolean
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

  // In the future we can add a method here to allow someone to show only transactions over a certain limit

  def moderate(transaction: Transaction): ModeratedTransaction = {
    //transaction data
    val transactionId = transaction.id
    val transactionUUID = transaction.uuid
    val thisBankAccount = moderate(transaction.thisAccount)
    val otherBankAccount = moderate(transaction.otherAccount)

    //transation metadata
    val transactionMetadata =
      if(canSeeTransactionMetadata)
      {
        val ownerComment = if (canSeeOwnerComment) Some(transaction.metadata.ownerComment) else None
        val comments =
          if (canSeeComments)
            Some(transaction.metadata.comments.filter(comment => comment.viewId==id))
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
            Some(transaction.metadata.tags.filter(_.viewId==id))
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
          if(canSeeImages) Some(transaction.metadata.images.filter(_.viewId == id))
          else None

        val addImageFunc =
          if(canAddImage) Some(transaction.metadata.addImage)
          else None

        val deleteImageFunc =
          if(canDeleteImage) Some(transaction.metadata.deleteImage)
          else None

          val whereTag =
          if(canSeeWhereTag)
            transaction.metadata.whereTags.find(tag => tag.viewId == id)
          else
            None

        val addWhereTagFunc : Option[(String, Long, Date, Double, Double) => Boolean] =
          if(canAddWhereTag)
            Some(transaction.metadata.addWhereTag)
          else
            Empty

        val deleteWhereTagFunc : Option[(Long) => Boolean] =
          if (canDeleteWhereTag)
            Some(transaction.metadata.deleteWhereTag)
          else
            Empty


        new Some(
          new ModeratedTransactionMetadata(
            ownerComment,
            addOwnerCommentFunc,
            comments,
            addCommentFunc,
            deleteCommentFunc,
            tags,
            addTagFunc,
            deleteTagFunc,
            images,
            addImageFunc,
            deleteImageFunc,
            whereTag,
            addWhereTagFunc,
            deleteWhereTagFunc
        ))
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

    val transactionLabel =
      if (canSeeTransactionLabel) transaction.label
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

    new ModeratedTransaction(transactionUUID, transactionId, thisBankAccount, otherBankAccount, transactionMetadata,
     transactionType, transactionAmount, transactionCurrency, transactionLabel, transactionStartDate,
      transactionFinishDate, transactionBalance)
  }

  def moderate(bankAccount: BankAccount) : Option[ModeratedBankAccount] = {
    if(canSeeTransactionThisBankAccount)
    {
      val owners : Set[AccountOwner] = if(canSeeBankAccountOwners) bankAccount.owners else Set()
      val balance =
        if(canSeeBankAccountBalance){
          bankAccount.balance.toString
        } else if(canSeeBankAccountBalancePositiveOrNegative) {
          if(bankAccount.balance.toString.startsWith("-")) "-" else "+"
        } else ""
      val accountType = if(canSeeBankAccountType) Some(bankAccount.accountType) else None
      val currency = if(canSeeBankAccountCurrency) Some(bankAccount.currency) else None
      val label = if(canSeeBankAccountLabel) Some(bankAccount.label) else None
      val nationalIdentifier = if(canSeeBankAccountNationalIdentifier) Some(bankAccount.label) else None
      val swiftBic = if(canSeeBankAccountSwift_bic) bankAccount.swift_bic else None
      val iban = if(canSeeBankAccountIban) bankAccount.iban else None
      val number = if(canSeeBankAccountNumber) Some(bankAccount.number) else None
      val bankName = if(canSeeBankAccountBankName) Some(bankAccount.bankName) else None
      val bankPermalink = if(canSeeBankAccountBankPermalink) Some(bankAccount.bankPermalink) else None

      Some(
        new ModeratedBankAccount(
          id = bankAccount.permalink,
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
          bankPermalink = bankPermalink
        ))
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

          val publicAlias = otherBankAccount.metadata.publicAlias

          if (! publicAlias.isEmpty ) AccountName(publicAlias, PublicAlias)
          else AccountName(realName, NoAlias)

        } else if (usePrivateAliasIfOneExists) {

          val privateAlias = otherBankAccount.metadata.privateAlias

          if (! privateAlias.isEmpty) AccountName(privateAlias, PrivateAlias)
          else AccountName(realName, PrivateAlias)
        } else
          AccountName(realName, NoAlias)
      }
      val otherAccountNationalIdentifier = if (canSeeOtherAccountNationalIdentifier) Some(otherBankAccount.nationalIdentifier) else None
      val otherAccountSWIFT_BIC = if (canSeeSWIFT_BIC) otherBankAccount.swift_bic else None
      val otherAccountIBAN = if(canSeeOtherAccountIBAN) otherBankAccount.iban else None
      val otherAccountBankName = if(canSeeOtherAccountBankName) Some(otherBankAccount.bankName) else None
      val otherAccountNumber = if(canSeeOtherAccountNumber) Some(otherBankAccount.number) else None
      val otherAccountKind = if(canSeeOtherAccountKind) Some(otherBankAccount.kind) else None
      val otherAccountMetadata =
        if(canSeeOtherAccountMetadata)
        {
          //other bank account metadata
          val moreInfo =
            if (canSeeMoreInfo) Some(otherBankAccount.metadata.moreInfo)
            else None
          val url =
            if (canSeeUrl) Some(otherBankAccount.metadata.url)
            else None
          val imageUrl =
            if (canSeeImageUrl) Some(otherBankAccount.metadata.imageURL)
            else None
          val openCorporatesUrl =
            if (canSeeOpenCorporatesUrl) Some(otherBankAccount.metadata.openCorporatesURL)
            else None
          val corporateLocation : Option[GeoTag] =
            if(canSeeCorporateLocation)
              Some(otherBankAccount.metadata.corporateLocation)
            else
              None
          val physicalLocation : Option[GeoTag] =
            if(canSeePhysicalLocation)
              Some(otherBankAccount.metadata.physicalLocation)
            else
              None
          val addMoreInfo =
            if(canAddMoreInfo)
              Some(otherBankAccount.metadata.addMoreInfo)
            else
              None
          val addURL =
            if(canAddURL)
              Some(otherBankAccount.metadata.addURL)
            else
              None
          val addImageURL =
            if(canAddImageURL)
              Some(otherBankAccount.metadata.addImageURL)
            else
              None
          val addOpenCorporatesUrl =
            if(canAddOpenCorporatesUrl)
              Some(otherBankAccount.metadata.addOpenCorporatesURL)
            else
              None
          val addCorporateLocation =
            if(canAddCorporateLocation)
              Some(otherBankAccount.metadata.addCorporateLocation)
            else
              None
          val addPhysicalLocation =
            if(canAddPhysicalLocation)
              Some(otherBankAccount.metadata.addPhysicalLocation)
            else
              None
          val publicAlias =
            if(canSeePublicAlias)
              Some(otherBankAccount.metadata.publicAlias)
            else
              None
          val privateAlias =
            if(canSeePrivateAlias)
              Some(otherBankAccount.metadata.privateAlias)
            else
              None
          val addPublicAlias =
            if(canAddPublicAlias)
              Some(otherBankAccount.metadata.addPublicAlias)
            else
              None
          val addPrivateAlias =
            if(canAddPrivateAlias)
              Some(otherBankAccount.metadata.addPrivateAlias)
            else
              None
          val deleteCorporateLocation =
            if(canDeleteCorporateLocation)
              Some(otherBankAccount.metadata.deleteCorporateLocation)
            else
              None
          val deletePhysicalLocation=
            if(canDeletePhysicalLocation)
              Some(otherBankAccount.metadata.deletePhysicalLocation)
            else
              None


          Some(
            new ModeratedOtherBankAccountMetadata(
              moreInfo,
              url,
              imageUrl,
              openCorporatesUrl,
              corporateLocation,
              physicalLocation,
              publicAlias,
              privateAlias,
              addMoreInfo,
              addURL,
              addImageURL,
              addOpenCorporatesUrl,
              addCorporateLocation,
              addPhysicalLocation,
              addPublicAlias,
              addPrivateAlias,
              deleteCorporateLocation,
              deletePhysicalLocation
          ))
        }
        else
            None

      Some(
        new ModeratedOtherBankAccount(
          otherAccountId,
          otherAccountLabel,
          otherAccountNationalIdentifier,
          otherAccountSWIFT_BIC,
          otherAccountIBAN,
          otherAccountBankName,
          otherAccountNumber,
          otherAccountMetadata,
          otherAccountKind))
    }
    else
      None
  }

  def toJson : JObject = {
    ("name" -> name) ~
    ("description" -> description)
  }

}

//An implementation that has the least amount of permissions possible
class BaseView extends View {
  def id = 1
  def name = "Restricted"
  def permalink = "restricted"
  def description = ""
  def isPublic = false

  //the view settings
  def usePrivateAliasIfOneExists = true
  def usePublicAliasIfOneExists = true

  //reading access

  //transaction fields
  def canSeeTransactionThisBankAccount = false
  def canSeeTransactionOtherBankAccount = false
  def canSeeTransactionMetadata = false
  def canSeeTransactionLabel = false
  def canSeeTransactionAmount = false
  def canSeeTransactionType = false
  def canSeeTransactionCurrency = false
  def canSeeTransactionStartDate = false
  def canSeeTransactionFinishDate = false
  def canSeeTransactionBalance = false

  //transaction metadata
  def canSeeComments = false
  def canSeeOwnerComment = false
  def canSeeTags = false
  def canSeeImages = false

  //Bank account fields
  def canSeeBankAccountOwners = false
  def canSeeBankAccountType = false
  def canSeeBankAccountBalance = false
  def canSeeBankAccountBalancePositiveOrNegative = false
  def canSeeBankAccountCurrency = false
  def canSeeBankAccountLabel = false
  def canSeeBankAccountNationalIdentifier = false
  def canSeeBankAccountSwift_bic = false
  def canSeeBankAccountIban = false
  def canSeeBankAccountNumber = false
  def canSeeBankAccountBankName = false
  def canSeeBankAccountBankPermalink = false

  //other bank account fields
  def canSeeOtherAccountNationalIdentifier = false
  def canSeeSWIFT_BIC = false
  def canSeeOtherAccountIBAN = false
  def canSeeOtherAccountBankName = false
  def canSeeOtherAccountNumber = false
  def canSeeOtherAccountMetadata = false
  def canSeeOtherAccountKind = false

  //other bank account meta data
  def canSeeMoreInfo = false
  def canSeeUrl = false
  def canSeeImageUrl = false
  def canSeeOpenCorporatesUrl = false
  def canSeeCorporateLocation = false
  def canSeePhysicalLocation = false
  def canSeePublicAlias = false
  def canSeePrivateAlias = false

  def canAddMoreInfo = false
  def canAddURL = false
  def canAddImageURL = false
  def canAddOpenCorporatesUrl = false
  def canAddCorporateLocation = false
  def canAddPhysicalLocation = false
  def canAddPublicAlias = false
  def canAddPrivateAlias = false
  def canDeleteCorporateLocation = false
  def canDeletePhysicalLocation = false

  //writing access
  def canEditOwnerComment = false
  def canAddComment = false
  def canDeleteComment = false
  def canAddTag = false
  def canDeleteTag = false
  def canAddImage = false
  def canDeleteImage = false
  def canSeeWhereTag = false
  def canAddWhereTag = false
  def canDeleteWhereTag = false
}

class FullView extends View {
  def id = 2
  def name = "Full"
  def permalink ="full"
  def description = ""
  def isPublic = false

  //the view settings
  def usePrivateAliasIfOneExists = false
  def usePublicAliasIfOneExists = false

  //reading access

  //transaction fields
  def canSeeTransactionThisBankAccount = true
  def canSeeTransactionOtherBankAccount = true
  def canSeeTransactionMetadata = true
  def canSeeTransactionLabel = true
  def canSeeTransactionAmount = true
  def canSeeTransactionType = true
  def canSeeTransactionCurrency = true
  def canSeeTransactionStartDate = true
  def canSeeTransactionFinishDate = true
  def canSeeTransactionBalance = true

  //transaction metadata
  def canSeeComments = true
  def canSeeOwnerComment = true
  def canSeeTags = true
  def canSeeImages = true

  //Bank account fields
  def canSeeBankAccountOwners = true
  def canSeeBankAccountType = true
  def canSeeBankAccountBalance = true
  def canSeeBankAccountBalancePositiveOrNegative = true
  def canSeeBankAccountCurrency = true
  def canSeeBankAccountLabel = true
  def canSeeBankAccountNationalIdentifier = true
  def canSeeBankAccountSwift_bic = true
  def canSeeBankAccountIban = true
  def canSeeBankAccountNumber = true
  def canSeeBankAccountBankName = true
  def canSeeBankAccountBankPermalink = true

  //other bank account fields
  def canSeeOtherAccountNationalIdentifier = true
  def canSeeSWIFT_BIC = true
  def canSeeOtherAccountIBAN = true
  def canSeeOtherAccountMetadata = true
  def canSeeOtherAccountBankName = true
  def canSeeOtherAccountNumber = true
  def canSeeOtherAccountKind = true

  //other bank account meta data
  def canSeeMoreInfo = true
  def canSeeUrl = true
  def canSeeImageUrl = true
  def canSeeOpenCorporatesUrl = true
  def canSeeCorporateLocation = true
  def canSeePhysicalLocation = true
  def canSeePublicAlias = true
  def canSeePrivateAlias = true

  def canAddMoreInfo = true
  def canAddURL = true
  def canAddImageURL = true
  def canAddOpenCorporatesUrl = true
  def canAddCorporateLocation = true
  def canAddPhysicalLocation = true
  def canAddPublicAlias = true
  def canAddPrivateAlias = true
  def canDeleteCorporateLocation = true
  def canDeletePhysicalLocation = true

  //writing access
  def canEditOwnerComment = true
  def canAddComment = true
  def canDeleteComment = true
  def canAddTag = true
  def canDeleteTag = true
  def canAddImage = true
  def canDeleteImage = true
  def canSeeWhereTag = true
  def canAddWhereTag = true
  def canDeleteWhereTag = true
}


object View {
  //transform the url into a view
  //TODO : load the view from the Data base
  def fromUrl(viewNameURL: String): Box[View] =
    viewNameURL match {
      case "authorities" => Full(Authorities)
      case "board" => Full(Board)
      case "our-network" => Full(OurNetwork)
      case "team" => Full(Team)
      case "owner" => Full(Owner)
      case "public" | "anonymous" => Full(Public)
      case "management" => Full(Management)
      case _ => Failure("view " + viewNameURL + " not found", Empty, Empty)
    }

  def linksJson(views: Set[View], accountPermalink: String, bankPermalink: String): JObject = {
    val viewsJson = views.map(view => {
      ("rel" -> "account") ~
        ("href" -> { "/" + bankPermalink + "/account/" + accountPermalink + "/" + view.permalink }) ~
        ("method" -> "GET") ~
        ("title" -> "Get information about one account")
    })

    ("links" -> viewsJson)
  }
}

object Team extends FullView {
  override def id = 3
  override def name = "Team"
  override def permalink = "team"
  override def description = "A view for team members related to the account. E.g. for a company bank account -> employees/contractors"
  override def canEditOwnerComment= false

}
object Board extends FullView {
  override def id = 4
  override def name = "Board"
  override def permalink = "board"
  override def description = "A view for board members of a company to view that company's account data."
  override def canEditOwnerComment= false
}
object Authorities extends FullView {
  override def id = 5
  override def name = "Authorities"
  override def permalink = "authorities"
  override def description = "A view for authorities such as tax officials to view an account's data"
  override def canEditOwnerComment= false
}

object Public extends BaseView {
  //the actual class extends the BaseView but in fact it does not matters be cause we don't care about the values
  //of the canSeeMoreInfo, canSeeUrl,etc  attributes and we implement a specific moderate method

    /**
   * Current rules:
   *
   * If Public, and a public alias exists : Show the public alias
   * If Public, and no public alias exists : Show the real account holder
   * If our network, and a private alias exists : Show the private alias
   * If our network, and no private alias exists : Show the real account holder
   */
  override def id = 6
  override def name = "Public"
  override def permalink = "public"
  override def description = "A view of the account accessible by anyone."
  override def isPublic = true


  //Bank account fields
  override def canSeeBankAccountOwners = true
  override def canSeeBankAccountType = true
  override def canSeeBankAccountBalancePositiveOrNegative = true
  override def canSeeBankAccountCurrency = true
  override def canSeeBankAccountLabel = true
  override def canSeeBankAccountNationalIdentifier = true
  override def canSeeBankAccountSwift_bic = true
  override def canSeeBankAccountIban = true
  override def canSeeBankAccountNumber = true
  override def canSeeBankAccountBankName = true

  override def moderate(transaction: Transaction): ModeratedTransaction = {

    val transactionId = transaction.id
    val transactionUUID = transaction.uuid
    val accountBalance = "" //not used when displaying transactions, but we might eventually need it. if so, we need a ref to
      //the bank account so we could do something like if(canSeeBankAccountBalance) bankAccount.balance else if
      // canSeeBankAccountBalancePositiveOrNegative {show + or -} else ""
    val thisBankAccount = moderate(transaction.thisAccount)
    val otherBankAccount = moderate(transaction.otherAccount)
    val transactionMetadata =
      Some(
        new ModeratedTransactionMetadata(
          Some(transaction.metadata.ownerComment),
          None,
          Some(transaction.metadata.comments.filter(comment => comment.viewId==id)),
          Some(transaction.metadata.addComment),
          Some(transaction.metadata.deleteComment),
          Some(transaction.metadata.tags.filter(_.viewId==id)),
          Some(transaction.metadata.addTag),
          Some(transaction.metadata.deleteTag),
          Some(transaction.metadata.images.filter(_.viewId==id)), //TODO: Better if image takes a view as a parameter?
          Some(transaction.metadata.addImage),
          Some(transaction.metadata.deleteImage),
          transaction.metadata.whereTags.find(tag => tag.viewId == id),
          Some(transaction.metadata.addWhereTag),
          Some(transaction.metadata.deleteWhereTag)
      ))

    val transactionType = Some(transaction.transactionType)
    val transactionAmount = Some(transaction.amount)
    val transactionCurrency = Some(transaction.currency)
    val transactionLabel = None
    val transactionStartDate = Some(transaction.startDate)
    val transactionFinishDate = Some(transaction.finishDate)
    val transactionBalance =  if (transaction.balance.toString().startsWith("-")) "-" else "+"

    new ModeratedTransaction(
      transactionUUID,
      transactionId,
      thisBankAccount,
      otherBankAccount,
      transactionMetadata,
      transactionType,
      transactionAmount,
      transactionCurrency,
      transactionLabel,
      transactionStartDate,
      transactionFinishDate,
      transactionBalance
    )
  }
  override def moderate(bankAccount: BankAccount) : Option[ModeratedBankAccount] = {
    Some(
        new ModeratedBankAccount(
          id = bankAccount.permalink,
          owners = Some(bankAccount.owners),
          accountType = Some(bankAccount.accountType),
          currency = Some(bankAccount.currency),
          label = Some(bankAccount.label),
          nationalIdentifier = None,
          swift_bic = None,
          iban = None,
          number = Some(bankAccount.number),
          bankName = Some(bankAccount.bankName),
          bankPermalink = Some(bankAccount.bankPermalink)
        )
      )
  }
  override def moderate(otherAccount : OtherBankAccount) : Option[ModeratedOtherBankAccount] = {
    val otherAccountLabel = {
      val publicAlias = otherAccount.metadata.publicAlias
      if(publicAlias.isEmpty)
        AccountName(otherAccount.label, NoAlias)
      else
        AccountName(publicAlias, PublicAlias)
    }
    val otherAccountMetadata = {
      def isPublicAlias = otherAccountLabel.aliasType match {
        case PublicAlias => true
        case _ => false
      }
      val moreInfo = if (isPublicAlias) None else Some(otherAccount.metadata.moreInfo)
      val url = if (isPublicAlias) None else Some(otherAccount.metadata.url)
      val imageUrl = if (isPublicAlias) None else Some(otherAccount.metadata.imageURL)
      val openCorporatesUrl = if (isPublicAlias) None else Some(otherAccount.metadata.openCorporatesURL)
      val corporateLocation = if (isPublicAlias) None else Some(otherAccount.metadata.corporateLocation)
      val physicalLocation = if (isPublicAlias) None else Some(otherAccount.metadata.physicalLocation)

      Some(
        new ModeratedOtherBankAccountMetadata(
          moreInfo,
          url,
          imageUrl,
          openCorporatesUrl,
          corporateLocation,
          physicalLocation,
          Some(otherAccount.metadata.publicAlias),
          None,
          None,
          None,
          None,
          None,
          Some(otherAccount.metadata.addCorporateLocation),
          Some(otherAccount.metadata.addPhysicalLocation),
          None,
          None,
          Some(otherAccount.metadata.deleteCorporateLocation),
          Some(otherAccount.metadata.deletePhysicalLocation)
      ))
    }

    Some(
      new ModeratedOtherBankAccount(
        otherAccount.id,
        otherAccountLabel,
        None,
        None,
        None,
        None,
        None,
        otherAccountMetadata,
        None))
  }
}

object OurNetwork extends BaseView {
  override def id = 7
  override def name = "Our Network"
  override def permalink ="our-network"
  override def description = "A view for people related to the account in some way. E.g. for a company account this could include investors" +
    " or current/potential clients"
  override def moderate(transaction: Transaction): ModeratedTransaction = {
    val transactionId = transaction.id
    val transactionUUID = transaction.uuid
    val accountBalance = "" //not used when displaying transactions, but we might eventually need it. if so, we need a ref to
      //the bank account so we could do something like if(canSeeBankAccountBalance) bankAccount.balance else if
      // canSeeBankAccountBalancePositiveOrNegative {show + or -} else ""
    val thisBankAccount = moderate(transaction.thisAccount)
    val otherBankAccount = moderate(transaction.otherAccount)
    val transactionMetadata =
      Some(
        new ModeratedTransactionMetadata(
          Some(transaction.metadata.ownerComment),
          None,
          Some(transaction.metadata.comments.filter(comment => comment.viewId==id)),
          Some(transaction.metadata.addComment),
          Some(transaction.metadata.deleteComment),
          Some(transaction.metadata.tags.filter(_.viewId==id)),
          Some(transaction.metadata.addTag),
          Some(transaction.metadata.deleteTag),
          Some(transaction.metadata.images.filter(_.viewId==id)), //TODO: Better if image takes a view as a parameter?
          Some(transaction.metadata.addImage),
          Some(transaction.metadata.deleteImage),
          transaction.metadata.whereTags.find(tag => tag.viewId == id),
          Some(transaction.metadata.addWhereTag),
          Some(transaction.metadata.deleteWhereTag)
      ))
    val transactionType = Some(transaction.transactionType)
    val transactionAmount = Some(transaction.amount)
    val transactionCurrency = Some(transaction.currency)
    val transactionLabel = transaction.label
    val transactionStartDate = Some(transaction.startDate)
    val transactionFinishDate = Some(transaction.finishDate)
    val transactionBalance =  transaction.balance.toString()

    new ModeratedTransaction(transactionUUID, transactionId, thisBankAccount, otherBankAccount, transactionMetadata,
     transactionType, transactionAmount, transactionCurrency, transactionLabel, transactionStartDate,
      transactionFinishDate, transactionBalance)
  }
  override def moderate(bankAccount: BankAccount) : Option[ModeratedBankAccount] = {
    Some(
        new ModeratedBankAccount(
          id = bankAccount.permalink,
          owners = Some(bankAccount.owners),
          accountType = Some(bankAccount.accountType),
          currency = Some(bankAccount.currency),
          label = Some(bankAccount.label),
          nationalIdentifier = None,
          swift_bic = None,
          iban = None,
          number = Some(bankAccount.number),
          bankName = Some(bankAccount.bankName),
          bankPermalink = Some(bankAccount.bankPermalink)
        )
      )
  }
  override def moderate(otherAccount : OtherBankAccount) : Option[ModeratedOtherBankAccount] = {
    val otherAccountLabel = {
      val privateAlias = otherAccount.metadata.privateAlias
      if(privateAlias.isEmpty)
        AccountName(otherAccount.label, NoAlias)
      else
        AccountName(privateAlias, PrivateAlias)
    }
    val otherAccountMetadata =
      Some(
        new ModeratedOtherBankAccountMetadata(
        Some(otherAccount.metadata.moreInfo),
        Some(otherAccount.metadata.url),
        Some(otherAccount.metadata.imageURL),
        Some(otherAccount.metadata.openCorporatesURL),
        Some(otherAccount.metadata.corporateLocation),
        Some(otherAccount.metadata.physicalLocation),
        Some(otherAccount.metadata.publicAlias),
        Some(otherAccount.metadata.privateAlias),
        None,
        None,
        None,
        None,
        Some(otherAccount.metadata.addCorporateLocation),
        Some(otherAccount.metadata.addPhysicalLocation),
        Some(otherAccount.metadata.addPublicAlias),
        Some(otherAccount.metadata.addPrivateAlias),
        Some(otherAccount.metadata.deleteCorporateLocation),
        Some(otherAccount.metadata.deletePhysicalLocation)
      ))

    Some(new ModeratedOtherBankAccount(otherAccount.id,otherAccountLabel,None,None,None,
        None, None, otherAccountMetadata, None))
  }
}

object Owner extends FullView {
  override def id = 8
  override def name="Owner"
  override def permalink = "owner"
}

object Management extends FullView {
  override def id = 9
  override def name="Management"
  override def permalink = "management"
}

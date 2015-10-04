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

package code.model
import java.util.Date
import code.model.Moderation.Moderated
import code.util.Helper
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonAST.JField
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import java.net.URL
import net.liftweb.common.Box
import net.liftweb.common.Failure

object Moderation {
  type Moderated[A] = Option[A]
}

class ModeratedTransaction(
  val UUID : String,
  val id: TransactionId,
  val bankAccount: Moderated[ModeratedBankAccount],
  val otherBankAccount: Moderated[ModeratedOtherBankAccount],
  val metadata : Moderated[ModeratedTransactionMetadata],
  val transactionType: Moderated[String],
  val amount: Moderated[BigDecimal],
  val currency: Moderated[String],
  val description: Moderated[String],
  val startDate: Moderated[Date],
  val finishDate: Moderated[Date],
  //the filteredBlance type in this class is a string rather than Big decimal like in Transaction trait for snippet (display) reasons.
  //the view should be able to return a sign (- or +) or the real value. casting signs into big decimal is not possible
  val balance : String
) {

  def dateOption2JString(date: Option[Date]) : JString = {
    import java.text.SimpleDateFormat

    val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    JString(date.map(d => dateFormat.format(d)) getOrElse "")
  }

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def toJson(view: View): JObject = {
    ("view" -> view.viewId.value) ~
    ("uuid" -> id.value) ~ //legacy bug: id is used here (kept this way to keep api behaviour)
      ("this_account" -> bankAccount) ~
      ("other_account" -> otherBankAccount) ~
      ("details" ->
        ("type_en" -> transactionType) ~ //TODO: Need translations for transaction types and a way to
        ("type_de" -> transactionType) ~ // figure out what language the original type is in
        ("posted" -> dateOption2JString(startDate)) ~
        ("completed" -> dateOption2JString(finishDate)) ~
        ("new_balance" ->
          ("currency" -> currency.getOrElse("")) ~
          ("amount" -> balance)) ~
          ("value" ->
            ("currency" -> currency.getOrElse("")) ~
            ("amount" -> amount)))
  }
}

class ModeratedTransactionMetadata(
  val ownerComment : Moderated[String],
  val addOwnerComment : Moderated[(String => Unit)],
  val comments : Moderated[List[Comment]],
  val addComment: Moderated[(UserId, ViewId, String, Date) => Box[Comment]],
  private val deleteComment: Moderated[(String) => Box[Unit]],
  val tags : Moderated[List[TransactionTag]],
  val addTag : Moderated[(UserId, ViewId, String, Date) => Box[TransactionTag]],
  private val deleteTag : Moderated[(String) => Box[Unit]],
  val images : Moderated[List[TransactionImage]],
  val addImage : Moderated[(UserId, ViewId, String, Date, URL) => Box[TransactionImage]],
  private val deleteImage : Moderated[String => Unit],
  val whereTag : Moderated[Option[GeoTag]],
  val addWhereTag : Moderated[(UserId, ViewId, Date, Double, Double) => Boolean],
  private val deleteWhereTag : Moderated[(ViewId) => Boolean]
){

  /**
  * @return Full if deleting the tag worked, or a failure message if it didn't
  */
  def deleteTag(tagId : String, user: Option[User], bankAccount : BankAccount) : Box[Unit] = {
    for {
      u <- Box(user) ?~ { "User must be logged in"}
      tagList <- Box(tags) ?~ { "You must be able to see tags in order to delete them"}
      tag <- Box(tagList.find(tag => tag.id_ == tagId)) ?~ {"Tag with id " + tagId + "not found for this transaction"}
      deleteFunc <- if(tag.postedBy == user || u.ownerAccess(bankAccount))
    	               Box(deleteTag) ?~ "Deleting tags not permitted for this view"
                    else
                      Failure("deleting tags not permitted for the current user")
      tagIsDeleted <- deleteFunc(tagId)
    } yield {
    }
  }

  /**
  * @return Full if deleting the image worked, or a failure message if it didn't
  */
  def deleteImage(imageId : String, user: Option[User], bankAccount : BankAccount) : Box[Unit] = {
    for {
      u <- Box(user) ?~ { "User must be logged in"}
      imageList <- Box(images) ?~ { "You must be able to see images in order to delete them"}
      image <- Box(imageList.find(image => image.id_ == imageId)) ?~ {"Image with id " + imageId + "not found for this transaction"}
      deleteFunc <- if(image.postedBy == user || u.ownerAccess(bankAccount))
    	                Box(deleteImage) ?~ "Deleting images not permitted for this view"
                    else
                      Failure("Deleting images not permitted for the current user")
    } yield {
      deleteFunc(imageId)
    }
  }

  def deleteComment(commentId: String, user: Option[User],bankAccount: BankAccount) : Box[Unit] = {
    for {
      u <- Box(user) ?~ { "User must be logged in"}
      commentList <- Box(comments) ?~ {"You must be able to see comments in order to delete them"}
      comment <- Box(commentList.find(comment => comment.id_ == commentId)) ?~ {"Comment with id "+commentId+" not found for this transaction"}
      deleteFunc <- if(comment.postedBy == user || u.ownerAccess(bankAccount))
                    Box(deleteComment) ?~ "Deleting comments not permitted for this view"
                  else
                    Failure("Deleting comments not permitted for the current user")
    } yield {
      deleteFunc(commentId)
    }
  }

  def deleteWhereTag(viewId: ViewId, user: Option[User],bankAccount: BankAccount) : Box[Boolean] = {
    for {
      u <- Box(user) ?~ { "User must be logged in"}
      whereTagOption <- Box(whereTag) ?~ {"You must be able to see the where tag in order to delete it"}
      whereTag <- Box(whereTagOption) ?~ {"there is no tag to delete"}
      deleteFunc <- if(whereTag.postedBy == user || u.ownerAccess(bankAccount))
                      Box(deleteWhereTag) ?~ "Deleting tag is not permitted for this view"
                    else
                      Failure("Deleting tags not permitted for the current user")
    } yield {
      deleteFunc(viewId)
    }
  }
}



object ModeratedTransactionMetadata {
  @deprecated(Helper.deprecatedJsonGenerationMessage)
  implicit def moderatedTransactionMetadata2Json(mTransactionMeta: ModeratedTransactionMetadata) : JObject = {
    JObject(JField("blah", JString("test")) :: Nil)
  }
}

class ModeratedBankAccount(
  val accountId : AccountId,
  val owners : Moderated[Set[User]],
  val accountType : Moderated[String],
  val balance: String = "", //TODO: Moderated[String]?
  val currency : Moderated[String],
  val label : Moderated[String],
  val nationalIdentifier : Moderated[String],
  val swift_bic : Moderated[String],
  val iban : Moderated[String],
  val number: Moderated[String],
  val bankName: Moderated[String],
  val bankId : BankId
){
  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def toJson = {
    def ownersJson(x : Set[User])=
      x.map(owner =>
      ("id" ->owner.idGivenByProvider) ~
      ("name" -> owner.name))

    ("number" -> number.getOrElse("")) ~
    ("owners" -> ownersJson(owners.getOrElse(Set()))) ~
    ("type" -> accountType.getOrElse("")) ~
    ("balance" ->
    	("currency" -> currency.getOrElse("")) ~
    	("amount" -> balance)) ~
    ("IBAN" -> iban.getOrElse("")) ~
    ("date_opened" -> "")
  }
}

object ModeratedBankAccount {

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  def bankJson(holderName: String, isAlias : String, number: String,
      	kind: String, bankIBAN: String, bankNatIdent: String,
      	bankName: String) : JObject = {
    ("holder" ->
      (
    	 ("name" -> holderName) ~
    	 ("alias"-> isAlias)
      ))~
    ("number" -> number) ~
    ("kind" -> kind) ~
    ("bank" ->
    	("IBAN" -> bankIBAN) ~
    	("national_identifier" -> bankNatIdent) ~
    	("name" -> bankName))
  }

  @deprecated(Helper.deprecatedJsonGenerationMessage)
  implicit def moderatedBankAccount2Json(mBankAccount: ModeratedBankAccount) : JObject = {
    val holderName = mBankAccount.owners match{
        case Some(ownersSet) => if(ownersSet.size!=0)
                                  ownersSet.toList(0).name
                                else
                                  ""
        case _ => ""
      }
    val isAlias = "no"
    val number = mBankAccount.number getOrElse ""
    val kind = mBankAccount.accountType getOrElse ""
    val bankIBAN = mBankAccount.iban.getOrElse("")
    val bankNatIdent = mBankAccount.nationalIdentifier getOrElse ""
    val bankName = mBankAccount.bankName getOrElse ""
    bankJson(holderName, isAlias, number, kind, bankIBAN, bankNatIdent, bankName)
  }
}

class ModeratedOtherBankAccount(
  val id : String,
  val label : AccountName,
  val nationalIdentifier : Moderated[String],
  val swift_bic : Moderated[String],
  val iban : Moderated[String],
  val bankName : Moderated[String],
  val number : Moderated[String],
  val metadata : Moderated[ModeratedOtherBankAccountMetadata],
  val kind : Moderated[String]
){

  def isAlias : Boolean = label.aliasType match{
    case PublicAlias | PrivateAlias => true
    case _ => false
  }
}

object ModeratedOtherBankAccount {
  @deprecated(Helper.deprecatedJsonGenerationMessage)
  implicit def moderatedOtherBankAccount2Json(mOtherBank: ModeratedOtherBankAccount) : JObject = {
    val holderName = mOtherBank.label.display
    val isAlias = if(mOtherBank.isAlias) "yes" else "no"
    val number = mOtherBank.number getOrElse ""
    val kind = ""
    val bankIBAN = mOtherBank.iban.getOrElse("")
    val bankNatIdent = mOtherBank.nationalIdentifier getOrElse ""
    val bankName = mOtherBank.bankName getOrElse ""
    ModeratedBankAccount.bankJson(holderName, isAlias, number, kind, bankIBAN, bankNatIdent, bankName)
  }
}

class ModeratedOtherBankAccountMetadata(
  val moreInfo : Moderated[String],
  val url : Moderated[String],
  val imageURL : Moderated[String],
  val openCorporatesURL : Moderated[String],
  val corporateLocation : Moderated[Option[GeoTag]],
  val physicalLocation :  Moderated[Option[GeoTag]],
  val publicAlias : Moderated[String],
  val privateAlias : Moderated[String],
  val addMoreInfo : Moderated[(String) => Boolean],
  val addURL : Moderated[(String) => Boolean],
  val addImageURL : Moderated[(String) => Boolean],
  val addOpenCorporatesURL : Moderated[(String) => Boolean],
  val addCorporateLocation : Moderated[(UserId, Date, Double, Double) => Boolean],
  val addPhysicalLocation : Moderated[(UserId, Date, Double, Double) => Boolean],
  val addPublicAlias : Moderated[(String) => Boolean],
  val addPrivateAlias : Moderated[(String) => Boolean],
  val deleteCorporateLocation : Moderated[() => Boolean],
  val deletePhysicalLocation : Moderated[() => Boolean]
)

object ModeratedOtherBankAccountMetadata {
  @deprecated(Helper.deprecatedJsonGenerationMessage)
  implicit def moderatedOtherBankAccountMetadata2Json(mOtherBankMeta: ModeratedOtherBankAccountMetadata) : JObject = {
    JObject(JField("blah", JString("test")) :: Nil)
  }
}
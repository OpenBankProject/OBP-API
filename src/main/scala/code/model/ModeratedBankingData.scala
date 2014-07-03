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
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonAST.JField
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.liftweb.http.JsonResponse
import net.liftweb.http.LiftResponse
import java.text.SimpleDateFormat
import java.net.URL
import net.liftweb.common.Box
import net.liftweb.common.Full
import net.liftweb.common.Failure


class ModeratedTransaction(
  val UUID : String,
  val id: String,
  val bankAccount: Option[ModeratedBankAccount],
  val otherBankAccount: Option[ModeratedOtherBankAccount],
  val metadata : Option[ModeratedTransactionMetadata],
  val transactionType: Option[String],
  val amount: Option[BigDecimal],
  val currency: Option[String],
  val description: Option[String],
  val startDate: Option[Date],
  val finishDate: Option[Date],
  //the filteredBlance type in this class is a string rather than Big decimal like in Transaction trait for snippet (display) reasons.
  //the view should be able to return a sign (- or +) or the real value. casting signs into big decimal is not possible
  val balance : String
) {

  def dateOption2JString(date: Option[Date]) : JString = {
    JString(date.map(d => ModeratedTransaction.dateFormat.format(d)) getOrElse "")
  }

  def toJson(view: View): JObject = {
    ("view" -> view.permalink) ~
    ("uuid" -> id) ~
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

object ModeratedTransaction {
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
}

class ModeratedTransactionMetadata(
  val ownerComment : Option[String],
  val addOwnerComment : Option[(String => Unit)],
  val comments : Option[List[Comment]],
  val addComment: Option[(String, Long, String, Date) => Box[Comment]],
  private val deleteComment: Option[(String) => Box[Unit]],
  val tags : Option[List[Tag]],
  val addTag : Option[(String, Long, String, Date) => Box[Tag]],
  private val deleteTag : Option[(String) => Box[Unit]],
  val images : Option[List[TransactionImage]],
  val addImage : Option[(String, Long, String, Date, URL) => Box[TransactionImage]],
  private val deleteImage : Option[String => Unit],
  val whereTag : Option[Option[GeoTag]],
  val addWhereTag : Option[(String, Long, Date, Double, Double) => Boolean],
  private val deleteWhereTag : Option[(Long) => Boolean]
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

  def deleteWhereTag(viewId: Long, user: Option[User],bankAccount: BankAccount) : Box[Boolean] = {
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
  implicit def moderatedTransactionMetadata2Json(mTransactionMeta: ModeratedTransactionMetadata) : JObject = {
    JObject(JField("blah", JString("test")) :: Nil)
  }
}

class ModeratedBankAccount(
  val id : String,
  val owners : Option[Set[AccountOwner]],
  val accountType : Option[String],
  val balance: String = "",
  val currency : Option[String],
  val label : Option[String],
  val nationalIdentifier : Option[String],
  val swift_bic : Option[String],
  val iban : Option[String],
  val number: Option[String],
  val bankName: Option[String],
  val bankPermalink : String
){
  def toJson = {
    def ownersJson(x : Set[AccountOwner])=
      x.map(owner =>
      ("id" ->owner.id) ~
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
  val nationalIdentifier : Option[String],
  val swift_bic : Option[String],
  val iban : Option[String],
  val bankName : Option[String],
  val number : Option[String],
  val metadata : Option[ModeratedOtherBankAccountMetadata],
  val kind : Option[String]
){

  def isAlias : Boolean = label.aliasType match{
    case PublicAlias | PrivateAlias => true
    case _ => false
  }
}

object ModeratedOtherBankAccount {
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
  val moreInfo : Option[String],
  val url : Option[String],
  val imageURL : Option[String],
  val openCorporatesURL : Option[String],
  val corporateLocation : Option[Option[GeoTag]],
  val physicalLocation :  Option[Option[GeoTag]],
  val publicAlias : Option[String],
  val privateAlias : Option[String],
  val addMoreInfo : Option[(String) => Boolean],
  val addURL : Option[(String) => Boolean],
  val addImageURL : Option[(String) => Boolean],
  val addOpenCorporatesURL : Option[(String) => Boolean],
  val addCorporateLocation : Option[(String, Long, Date, Double, Double) => Boolean],
  val addPhysicalLocation : Option[(String, Long, Date, Double, Double) => Boolean],
  val addPublicAlias : Option[(String) => Boolean],
  val addPrivateAlias : Option[(String) => Boolean],
  val deleteCorporateLocation : Option[() => Boolean],
  val deletePhysicalLocation : Option[() => Boolean]
)

object ModeratedOtherBankAccountMetadata {
  implicit def moderatedOtherBankAccountMetadata2Json(mOtherBankMeta: ModeratedOtherBankAccountMetadata) : JObject = {
    JObject(JField("blah", JString("test")) :: Nil)
  }
}
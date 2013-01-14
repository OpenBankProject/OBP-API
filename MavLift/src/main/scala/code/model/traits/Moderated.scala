/** 
Open Bank Project - Transparency / Social Finance Web Application
Copyright (C) 2011, 2012, TESOBE / Music Pictures Ltd

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

package code.model.traits
import java.util.Date
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonAST.JField
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.liftweb.http.JsonResponse
import net.liftweb.http.LiftResponse
import java.text.SimpleDateFormat

class ModeratedOtherBankAccount (filteredId : String, filteredLabel : AccountName, 
  filteredNationalIdentifier : Option[String], filteredSWIFT_BIC : Option[Option[String]], 
  filteredIBAN : Option[Option[String]], filteredBankName: Option[String],
  filteredNumber: Option[String], filteredMetadata : Option[ModeratedOtherBankAccountMetadata]) 
{
    def id = filteredId
    def label = filteredLabel
    def nationalIdentifier = filteredNationalIdentifier
    def swift_bic = filteredSWIFT_BIC
    def iban = filteredIBAN
    def bankName = filteredBankName
    def number = filteredNumber
    def metadata = filteredMetadata 
    def isAlias = filteredLabel.aliasType match{
    case Public | Private => true
    case _ => false
  }
}

object ModeratedOtherBankAccount {
  implicit def moderatedOtherBankAccount2Json(mOtherBank: ModeratedOtherBankAccount) : JObject = {
    val holderName = mOtherBank.label.display
    val isAlias = if(mOtherBank.isAlias) "yes" else "no"
    val number = mOtherBank.number getOrElse ""
    val kind = ""
    val bankIBAN = (for { //TODO: This should be handled a bit better... might want to revisit the Option stuff in ModeratedOtherAccount etc.
      i <- mOtherBank.iban
      iString <- i
    } yield iString).getOrElse("")
    val bankNatIdent = mOtherBank.nationalIdentifier getOrElse ""
    val bankName = mOtherBank.bankName getOrElse ""
    ModeratedBankAccount.bankJson(holderName, isAlias, number, kind, bankIBAN, bankNatIdent, bankName)
  }
}

class ModeratedOtherBankAccountMetadata(filteredMoreInfo : Option[String], 
  filteredUrl : Option[String], filteredImageUrl : Option[String], filteredOpenCorporatesUrl : Option[String]) {
  def moreInfo = filteredMoreInfo 
  def url = filteredUrl
  def imageUrl = filteredImageUrl
  def openCorporatesUrl = filteredOpenCorporatesUrl
}

object ModeratedOtherBankAccountMetadata {
  implicit def moderatedOtherBankAccountMetadata2Json(mOtherBankMeta: ModeratedOtherBankAccountMetadata) : JObject = {
    JObject(JField("blah", JString("test")) :: Nil)
  }
}


class ModeratedTransaction(filteredId: String, filteredBankAccount: Option[ModeratedBankAccount], 
  filteredOtherBankAccount: Option[ModeratedOtherBankAccount], filteredMetaData : Option[ModeratedTransactionMetadata], 
  filteredTransactionType: Option[String], filteredAmount: Option[BigDecimal], filteredCurrency: Option[String], 
  filteredLabel: Option[Option[String]],filteredStartDate: Option[Date], filteredFinishDate: Option[Date],
  filteredBalance : String) {
  
  //the filteredBlance type in this class is a string rather than Big decimal like in Transaction trait for snippet (display) reasons.
  //the view should be able to return a sign (- or +) or the real value. casting signs into bigdecimal is not possible  
  def id = filteredId 
  def bankAccount = filteredBankAccount
  def otherBankAccount = filteredOtherBankAccount
  def metadata = filteredMetaData
  def transactionType = filteredTransactionType
  def amount = filteredAmount 
  def currency = filteredCurrency
  def label = filteredLabel
  def startDate = filteredStartDate
  def finishDate = filteredFinishDate
  def balance = filteredBalance

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
          ("currency" -> currency.getOrElse("")) ~ //TODO: Need separate currency for balances and values?
          ("amount" -> balance)) ~
          ("value" ->
            ("currency" -> currency.getOrElse("")) ~
            ("amount" -> amount)))
  }
}

object ModeratedTransaction {
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
}

class ModeratedTransactionMetadata(filteredOwnerComment : Option[String], filteredComments : Option[List[Comment]], 
  addOwnerComment : Option[(String => Unit)], addCommentFunc: Option[(Long, Long, String, Date) => Unit])
  {
    def ownerComment = filteredOwnerComment
    def comments = filteredComments
    def ownerComment(text : String) = addOwnerComment match {
      case None => None
      case Some(o) => o.apply(text)
    }
    def addComment= addCommentFunc

  }

object ModeratedTransactionMetadata {
  implicit def moderatedTransactionMetadata2Json(mTransactionMeta: ModeratedTransactionMetadata) : JObject = {
    JObject(JField("blah", JString("test")) :: Nil)
  }
}

class ModeratedBankAccount(filteredId : String, 
  filteredOwners : Option[Set[AccountOwner]], filteredAccountType : Option[String], 
  filteredBalance: String, filteredCurrency : Option[String], 
  filteredLabel : Option[String], filteredNationalIdentifier : Option[String],
  filteredSwift_bic : Option[Option[String]], filteredIban : Option[Option[String]],
  filteredNumber: Option[String], filteredBankName: Option[String])
{
  def id = filteredId
  def owners = filteredOwners
  def accountType = filteredAccountType
  def balance = filteredBalance 
  def currency = filteredCurrency
  def label = filteredLabel
  def nationalIdentifier = filteredNationalIdentifier
  def swift_bic = filteredSwift_bic
  def iban = filteredIban
  def number = filteredNumber
  def bankName = filteredBankName
  
  def toJson = {
    //TODO: Decide if unauthorised info (I guess that is represented by a 'none' option'? I can't really remember)
    // should just disappear from the json or if an empty string should be used. 
    //I think we decided to use empty strings. What was the point of all the options again? 
    ("number" -> number.getOrElse("")) ~
    ("owners" -> owners.flatten.map(owner => 
      ("id" ->owner.id) ~ 
      ("name" -> owner.name))) ~
    ("type" -> accountType.getOrElse("")) ~
    ("balance" -> 
    	("currency" -> currency.getOrElse("")) ~
    	("amount" -> balance)) ~
    ("IBAN" -> iban.getOrElse(Some(""))) ~
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
    val bankIBAN = (for { //TODO: This should be handled a bit better... might want to revisit the Option stuff in ModeratedOtherAccount etc.
      i <- mBankAccount.iban
      iString <- i
    } yield iString).getOrElse("")
    val bankNatIdent = mBankAccount.nationalIdentifier getOrElse ""
    val bankName = mBankAccount.bankName getOrElse ""
    bankJson(holderName, isAlias, number, kind, bankIBAN, bankNatIdent, bankName)
  }
}
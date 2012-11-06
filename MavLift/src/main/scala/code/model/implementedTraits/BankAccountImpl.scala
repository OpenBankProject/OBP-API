package code.model.implementedTraits

import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.Set
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Full
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import code.model.traits.{BankAccount,AccountOwner, Transaction}
import code.model.dataAccess.{Account,OBPEnvelope}
import code.model.traits.Transaction
import code.model.traits.ModeratedTransaction
import code.model.dataAccess.OBPEnvelope.OBPQueryParam
import net.liftweb.common.Box
import code.model.dataAccess.LocalStorage
import code.model.dataAccess.OBPEnvelope._
import code.model.dataAccess.OBPUser
import code.model.traits.View

class BankAccountImpl(id_ : String, var _owners : Set[AccountOwner], accountType_ : String,
  currency_ : String, label_ : String, nationalIdentifier_ : String, swift_bic_ : Option[String],
  iban_ : Option[String], allowAnnoymousAccess_ : Boolean,
  number_ : String, bankName_ : String, bankPermalink_ : String, permalink_ : String) extends BankAccount {

  def id = id_
  def owners = _owners
  def owners_=(owners_ : Set[AccountOwner]) = _owners = owners_
  def accountType = accountType_
  def balance = {
    val newest = getTransactions(OBPLimit(1), OBPOrdering(None, OBPDescending))
    newest match {
      case Full(n) => {
        n match {
          case Nil => None
          case x :: xs => Some(x.balance)
        }
      }
      case _ => None
    }
  }
  def bankPermalink = bankPermalink_
  def permalink = permalink_
  def currency = currency_
  def label = label_
  def nationalIdentifier = nationalIdentifier_
  def swift_bic = swift_bic_
  def iban = iban_
  def number = number_
  def bankName = bankName_
  def transactions(from: Date, to: Date): Set[Transaction] = { 
    throw new NotImplementedException
  }
  def transaction(id: String): Option[Transaction] = { 
    throw new NotImplementedException
  }
  def allowAnnoymousAccess = allowAnnoymousAccess_

  def getModeratedTransactions(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction] = {
   LocalStorage.getModeratedTransactions(permalink, bankPermalink)(moderate)
  }

  def getModeratedTransactions(queryParams: OBPQueryParam*)(moderate: Transaction => ModeratedTransaction): List[ModeratedTransaction] = {
    LocalStorage.getModeratedTransactions(permalink, bankPermalink, queryParams: _*)(moderate)
  }
  
  def getTransactions(queryParams: OBPQueryParam*) : Box[List[Transaction]] = {
    LocalStorage.getTransactions(permalink, bankPermalink, queryParams: _*)
  }

  def getTransactions(bank: String, account: String): Box[List[Transaction]] = {
   LocalStorage.getTransactions(permalink, bankPermalink)
  }

  def authorisedAccess(view: View, user: Option[OBPUser]) = {
    view match {
      case Anonymous => allowAnnoymousAccess
      case _ => user match {
        case Some(u) => u.permittedViews(this).contains(view)
        case _ => false
      }
    }
  }

}


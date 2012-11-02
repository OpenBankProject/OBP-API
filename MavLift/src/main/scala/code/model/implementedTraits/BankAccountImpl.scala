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

class BankAccountImpl(id_ : String, var _owners : Set[AccountOwner], accountType_ : String, balance_ : BigDecimal,
  currency_ : String, label_ : String, nationalIdentifier_ : String, swift_bic_ : Option[String],
   iban_ : Option[String], transactions_ : Set[Transaction], allowAnnoymousAccess_ : Boolean,
   number_ : String, bankName_ : String) extends BankAccount {

  def id = id_
  def owners = _owners
  def owners_=(owners_ : Set[AccountOwner]) = _owners = owners_
  def accountType = accountType_
  def balance = balance_
  def currency = currency_
  def label = label_
  def nationalIdentifier = nationalIdentifier_
  def swift_bic = swift_bic_
  def iban = iban_
  def number = number_
  def bankName = bankName_
  def transactions = transactions_
  def transactions(from: Date, to: Date): Set[Transaction] = { 
    throw new NotImplementedException
  }
  def transaction(id: String): Option[Transaction] = { 
    throw new NotImplementedException
  }
  def allowAnnoymousAccess = allowAnnoymousAccess_
}


package code.model.implementedTraits

import code.model.dataAccess.{OBPEnvelope,OBPTransaction,OtherAccount}
import code.model.traits.{Transaction,BankAccount,OtherBankAccount, TransactionMetadata}
import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.List
import net.liftweb.common.Loggable
import net.liftweb.common.Box
import code.model.traits.Comment

class TransactionImpl(id_ : String, var _thisAccount : BankAccount = null, otherAccount_ : OtherBankAccount, 
  metadata_ : TransactionMetadata, transactionType_ : String, amount_ : BigDecimal, currency_ : String,
  label_ : Option[String], startDate_ : Date, finishDate_ : Date, balance_ :  BigDecimal) extends Transaction with Loggable {

  def id = id_
  def thisAccount = _thisAccount
  def thisAccount_= (newThisAccount : BankAccount) = _thisAccount = newThisAccount
  def otherAccount = otherAccount_
  def metadata = metadata_
  def transactionType = transactionType_
  def amount = amount_
  def currency = currency_
  def label = label_
  def startDate = startDate_
  def finishDate = finishDate_
  def balance = balance_
}
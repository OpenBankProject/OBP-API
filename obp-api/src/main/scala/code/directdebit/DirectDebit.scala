package code.directdebit

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object DirectDebits extends SimpleInjector {
  val directDebitProvider = new Inject(buildOne _) {}
  def buildOne: DirectDebitProvider = MappedDirectDebitProvider
}

trait DirectDebitProvider {
  def createDirectDebit(bankId: String, 
                        accountId: String,
                        customerId: String,
                        userId: String,
                        counterpartyId: String,
                        dateSigned: Date,
                        dateStarts: Date,
                        dateExpires: Option[Date]
                       ): Box[DirectDebitTrait]
  def getDirectDebitsByCustomer(customerId: String) : List[DirectDebitTrait]
  def getDirectDebitsByUser(userId: String) : List[DirectDebitTrait]
}

trait DirectDebitTrait {
  def directDebitId: String
  def bankId: String
  def accountId: String
  def customerId: String
  def userId: String
  def counterpartyId: String
  def dateSigned: Date
  def dateCancelled: Date
  def dateStarts: Date
  def dateExpires: Date
  def active: Boolean
}
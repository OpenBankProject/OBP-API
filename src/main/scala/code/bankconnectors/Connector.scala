package code.bankconnectors

import net.liftweb.common.Box
import code.model.Bank
import code.model.BankAccount
import net.liftweb.util.SimpleInjector
import code.model.User
import code.model.ModeratedOtherBankAccount
import code.model.OtherBankAccount
import code.model.dataAccess.OBPEnvelope.OBPQueryParam
import code.model.ModeratedTransaction
import code.model.Transaction

object Connector  extends SimpleInjector {

  val connector = new Inject(buildOne _) {}
  
  def buildOne: Connector = LocalConnector
  
}

trait Connector {
  
  //gets a particular bank handled by this connector
  def getBank(permalink : String) : Box[Bank]
  
  //gets banks handled by this connector
  def getBanks : List[Bank]
  
  def getBankAccount(bankPermalink : String, accountId : String) : Box[BankAccount]
  
  def getAllPublicAccounts : List[BankAccount]
  
  def getPublicBankAccounts(bank : Bank) : List[BankAccount]
  
  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccount]
  
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : Box[List[BankAccount]]
  
  def getNonPublicBankAccounts(user : User) : Box[List[BankAccount]]
  
  def getNonPublicBankAccounts(user : User, bankID : String) : Box[List[BankAccount]]
  
  def getModeratedOtherBankAccount(bankID: String, accountID : String, otherAccountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]) : Box[ModeratedOtherBankAccount]
  
  def getModeratedOtherBankAccounts(bankID: String, accountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]]
  
  //TODO: Move OBPQueryParam out of com.dataAccess.OBPEnvelope into a more general package
  def getModeratedTransactions(permalink: String, bankPermalink: String, queryParams: OBPQueryParam*)
    (moderate: Transaction => ModeratedTransaction): Box[List[ModeratedTransaction]]
  
  def getModeratedTransaction(id : String, bankPermalink : String, accountPermalink : String)
    (moderate: Transaction => ModeratedTransaction) : Box[ModeratedTransaction]

  
  //...
}
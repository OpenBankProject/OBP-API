package code.bankconnectors

import code.management.ImporterAPI.ImporterTransaction
import code.tesobe.CashTransaction
import code.util.Helper._
import com.tesobe.model.CreateBankAccount
import net.liftweb.common.Box
import code.model._
import net.liftweb.util.{Props, SimpleInjector}
import code.model.User
import code.model.OtherBankAccount
import code.model.Transaction
import java.util.Date

import scala.util.Random

object Connector  extends SimpleInjector {

  val connector = new Inject(buildOne _) {}

  def buildOne: Connector =
    Props.get("connector").openOrThrowException("no connector set") match {
      case "mapped" => LocalMappedConnector
      case "mongo" => LocalConnector
    }

}

class OBPQueryParam
trait OBPOrder { def orderValue : Int }
object OBPOrder {
  def apply(s: Option[String]): OBPOrder = s match {
    case Some("asc") => OBPAscending
    case Some("ASC")=> OBPAscending
    case _ => OBPDescending
  }
}
object OBPAscending extends OBPOrder { def orderValue = 1 }
object OBPDescending extends OBPOrder { def orderValue = -1}
case class OBPLimit(value: Int) extends OBPQueryParam
case class OBPOffset(value: Int) extends OBPQueryParam
case class OBPFromDate(value: Date) extends OBPQueryParam
case class OBPToDate(value: Date) extends OBPQueryParam
case class OBPOrdering(field: Option[String], order: OBPOrder) extends OBPQueryParam

trait Connector {

  //We have the Connector define its BankAccount implementation here so that it can
  //have access to the implementation details (e.g. the ability to set the balance) in
  //the implementation of makePaymentImpl
  type AccountType <: BankAccount

  //gets a particular bank handled by this connector
  def getBank(bankId : BankId) : Box[Bank]

  //gets banks handled by this connector
  def getBanks : List[Bank]

  def getBankAccount(bankId : BankId, accountId : AccountId) : Box[BankAccount] =
    getBankAccountType(bankId, accountId)

  protected def getBankAccountType(bankId : BankId, accountId : AccountId) : Box[AccountType]

  def getOtherBankAccount(bankId: BankId, accountID : AccountId, otherAccountID : String) : Box[OtherBankAccount]

  def getOtherBankAccounts(bankId: BankId, accountID : AccountId): List[OtherBankAccount]

  def getTransactions(bankId: BankId, accountID: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]]

  def getTransaction(bankId: BankId, accountID : AccountId, transactionId : TransactionId): Box[Transaction]

  def getPhysicalCards(user : User) : Set[PhysicalCard]

  def getPhysicalCardsForBank(bankId: BankId, user : User) : Set[PhysicalCard]
  
  //gets the users who are the legal owners/holders of the account
  def getAccountHolders(bankId: BankId, accountID: AccountId) : Set[User]


  //Payments api: just return Failure("not supported") from makePaymentImpl if you don't want to implement it
  /**
   * \
   *
   * @param initiator The user attempting to make the payment
   * @param fromAccountUID The unique identifier of the account sending money
   * @param toAccountUID The unique identifier of the account receiving money
   * @param amt The amount of money to send ( > 0 )
   * @return The id of the sender's new transaction,
   */
  def makePayment(initiator : User, fromAccountUID : BankAccountUID, toAccountUID : BankAccountUID, amt : BigDecimal) : Box[TransactionId] = {
    for{
      fromAccount <- getBankAccountType(fromAccountUID.bankId, fromAccountUID.accountId) ?~
        s"account ${fromAccountUID.accountId} not found at bank ${fromAccountUID.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
      toAccount <- getBankAccountType(toAccountUID.bankId, toAccountUID.accountId) ?~
        s"account ${toAccountUID.accountId} not found at bank ${toAccountUID.bankId}"
      sameCurrency <- booleanToBox(fromAccount.currency == toAccount.currency, {
        s"Cannot send payment to account with different currency (From ${fromAccount.currency} to ${toAccount.currency}"
      })
      isPositiveAmtToSend <- booleanToBox(amt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. ($amt)")
      //TODO: verify the amount fits with the currency -> e.g. 12.543 EUR not allowed, 10.00 JPY not allowed, 12.53 EUR allowed
      transactionId <- makePaymentImpl(fromAccount, toAccount, amt)
    } yield transactionId
  }

  protected def makePaymentImpl(fromAccount : AccountType, toAccount : AccountType, amt : BigDecimal) : Box[TransactionId]


  //non-standard calls --do not make sense in the regular context

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  def createBankAndAccount(bankName : String, bankNationalIdentifier : String, accountNumber : String, accountHolderName : String) : (Bank, BankAccount)

  //generates an unused account number and then creates the sandbox account using that number
  def createSandboxBankAccount(bankId : BankId, accountId : AccountId, currency : String, initialBalance : BigDecimal, accountHolderName : String) : Box[BankAccount] = {
    val uniqueAccountNumber = {
      def exists(number : String) = Connector.connector.vend.accountExists(bankId, number)

      def appendUntilOkay(number : String) : String = {
        val newNumber = number + Random.nextInt(10)
        if(!exists(newNumber)) newNumber
        else appendUntilOkay(newNumber)
      }

      //generates a random 8 digit account number
      val firstTry = (Random.nextDouble() * 10E8).toInt.toString
      appendUntilOkay(firstTry)
    }

    createSandboxBankAccount(bankId, accountId, uniqueAccountNumber, currency, initialBalance, accountHolderName)
  }

  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  def createSandboxBankAccount(bankId : BankId, accountId : AccountId,  accountNumber: String,
                               currency : String, initialBalance : BigDecimal, accountHolderName : String) : Box[BankAccount]

  //sets a user as an account owner/holder
  def setAccountHolder(bankAccountUID: BankAccountUID, user : User) : Unit

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  def accountExists(bankId : BankId, accountNumber : String) : Boolean


  //cash api requires getting an account via a uuid: for legacy reasons it does not use bankId + accountId
  def getAccountByUUID(uuid : String) : Box[AccountType]

  //cash api requires a call to add a new transaction and update the account balance
  def addCashTransactionAndUpdateBalance(account : AccountType, cashTransaction : CashTransaction)

  //used by transaction import api call to check for duplicates
  //the implementation is responsible for dealing with the amount as a string
  def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount : String, completed : Date, otherAccountHolder : String) : Int

  //used by transaction import api
  def createImportedTransaction(transaction: ImporterTransaction) : Box[Transaction]

  //used by the transaction import api
  def updateAccountBalance(bankId : BankId, accountId : AccountId, newBalance : BigDecimal) : Boolean
}
package code.bankconnectors

import code.management.ImporterAPI.ImporterTransaction
import code.model.dataAccess.MappedBankAccount
import code.tesobe.CashTransaction
import code.transactionrequests.TransactionRequests
import code.transactionrequests.TransactionRequests.{TransactionRequestChallenge, TransactionRequest, TransactionRequestId, TransactionRequestBody}
import code.util.Helper._
import com.javafx.tools.doclets.formats.html.markup.DocType
import com.tesobe.model.CreateBankAccount
import net.liftweb.common.{Full, Empty, Box}
import code.model._
import net.liftweb.util.Helpers._
import net.liftweb.util.{Props, SimpleInjector}
import code.model.User
import code.model.OtherBankAccount
import code.model.Transaction
import java.util.Date

import scala.util.Random


/*
So we can switch between different sources of resources e.g.
- Mapper ORM for connecting to RDBMS (via JDBC) https://www.assembla.com/wiki/show/liftweb/Mapper
- MongoDB
etc.

Note: We also have individual providers for resources like Branches and Products.
Probably makes sense to have more targeted providers like this.

Could consider a Map of ("resourceType" -> "provider") - this could tell us which tables we need to schemify (for list in Boot), whether or not to
 initialise MongoDB etc. resourceType might be sub devided to allow for different account types coming from different internal APIs, MQs.
 */

object Connector  extends SimpleInjector {

  val connector = new Inject(buildOne _) {}

  def buildOne: Connector =
    Props.get("connector").openOrThrowException("no connector set") match {
      case "mapped" => LocalMappedConnector
      case "mongodb" => LocalConnector
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


  /*
    Transaction Requests
  */

  def createTransactionRequest(initiator : User, fromAccount : BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) : Box[TransactionRequest] = {
    //set initial status
    //for sandbox / testing: depending on amount, we ask for challenge or not
    val status =
      if (transactionRequestType.value == "SANDBOX" && body.value.currency == "EUR" && BigDecimal(body.value.amount) < 100) {
        TransactionRequests.STATUS_COMPLETED
      } else {
        TransactionRequests.STATUS_INITIATED
      }

    //create a new transaction request
    var result = for {
      fromAccountType <- getBankAccountType(fromAccount.bankId, fromAccount.accountId) ?~
        s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
      toAccountType <- getBankAccountType(toAccount.bankId, toAccount.accountId) ?~
        s"account ${toAccount.accountId} not found at bank ${toAccount.bankId}"
      rawAmt <- tryo { BigDecimal(body.value.amount) } ?~! s"amount ${body.value.amount} not convertible to number"
      sameCurrency <- booleanToBox(fromAccount.currency == toAccount.currency, {
        s"Cannot send payment to account with different currency (From ${fromAccount.currency} to ${toAccount.currency}"
      })
      isPositiveAmtToSend <- booleanToBox(rawAmt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. (${rawAmt})")
      transactionRequest <- createTransactionRequestImpl(TransactionRequestId(java.util.UUID.randomUUID().toString), transactionRequestType, fromAccount, toAccount, body, status)
    } yield transactionRequest

    //make sure we got something back
    result = Full(result.openOrThrowException("Exception: Can't get transactionRequest after creating it"))

    //if no challenge necessary, create transaction immediately and put in data store and object to return
    if (status == TransactionRequests.STATUS_COMPLETED) {
      val createdTransactionId = Connector.connector.vend.makePayment(initiator, BankAccountUID(fromAccount.bankId, fromAccount.accountId),
        BankAccountUID(toAccount.bankId, toAccount.accountId), BigDecimal(body.value.amount))

      //set challenge to null
      result = Full(result.get.copy(challenge = null))

      //save transaction_id if we have one
      createdTransactionId match {
        case Full(ti) => {
          if (! createdTransactionId.isEmpty) {
            Connector.connector.vend.saveTransactionRequestTransaction (result.get.transactionRequestId, ti)
            result = Full(result.get.copy(transaction_ids = ti.value))
          }
        }
        case _ => None
      }
    } else {
      //if challenge necessary, create a new one
      var challenge = TransactionRequestChallenge(id = java.util.UUID.randomUUID().toString, allowed_attempts = 3, challenge_type = TransactionRequests.CHALLENGE_SANDBOX_TAN)
      saveTransactionRequestChallenge(result.get.transactionRequestId, challenge)
    }

    result
  }

  protected def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                             fromAccount : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                             status: String) : Box[TransactionRequest]


  def saveTransactionRequestTransaction(transactionRequestId: TransactionRequestId, transactionId: TransactionId) = {
    saveTransactionRequestTransactionImpl(transactionRequestId, transactionId)
  }

  protected def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId)

  def saveTransactionRequestChallenge(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) = {
    saveTransactionRequestChallengeImpl(transactionRequestId, challenge)
  }

  protected def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge)

  def getTransactionRequests(initiator : User, fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    for {
      fromAccount <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
            s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
      transactionRequests <- getTransactionRequestImpl(fromAccount)
    } yield transactionRequests
  }

  protected def getTransactionRequestImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]]


  def getTransactionRequestTypes(initiator : User, fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
    for {
      fromAccount <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
        s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
      transactionRequestTypes <- getTransactionRequestTypesImpl(fromAccount)
    } yield transactionRequestTypes
  }

  protected def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]]

  /*
    non-standard calls --do not make sense in the regular context but are used for e.g. tests
  */

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

  //remove an account and associated transactions
  def removeAccount(bankId: BankId, accountId: AccountId) : Boolean

  //cash api requires getting an account via a uuid: for legacy reasons it does not use bankId + accountId
  def getAccountByUUID(uuid : String) : Box[AccountType]

  //cash api requires a call to add a new transaction and update the account balance
  def addCashTransactionAndUpdateBalance(account : AccountType, cashTransaction : CashTransaction)

  //used by transaction import api call to check for duplicates

  //the implementation is responsible for dealing with the amount as a string
  def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount : String, completed : Date, otherAccountHolder : String) : Int
  def createImportedTransaction(transaction: ImporterTransaction) : Box[Transaction]
  def updateAccountBalance(bankId : BankId, accountId : AccountId, newBalance : BigDecimal) : Boolean
  def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Boolean

  def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Boolean

  }
package code.bankconnectors

import code.util.Helper._
import net.liftweb.common.Box
import code.model._
import net.liftweb.util.SimpleInjector
import code.model.User
import code.model.OtherBankAccount
import code.model.Transaction
import java.util.Date

object Connector  extends SimpleInjector {

  val connector = new Inject(buildOne _) {}

  //Have this return a proper connector that actually does something
  def buildOne: Connector = DummyConnector

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

  //set this in the implementation
  //e.g. type AccountType = MyBankAccountImplClass
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
}
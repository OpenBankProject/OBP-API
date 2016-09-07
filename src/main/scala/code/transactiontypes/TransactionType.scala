package code.TransactionTypes


import java.util.Date


import code.model._
import code.transaction_types.MappedTransactionTypeProvider
import net.liftweb.common.Logger
import net.liftweb.util.{Props, SimpleInjector}


// See http://simply.liftweb.net/index-8.2.html for info about "vend" and SimpleInjector


object TransactionType extends SimpleInjector {


  /** A TransactionType categorises each transaction on a bank statement.
    *
    * i.e. it justifies the reason for a transaction on a bank statement to exist
    * e.g. a bill-payment, ATM-withdrawal, interest-payment or some kind of fee to the customer.
    *
    * This internal representation may differ from the representation exposed by various API versions
    *
    * @param id Unique id across the API instance. Ideally a UUID
    * @param bankId The bank that supports this TransactionType
    * @param shortCode A short code (ideally-no-spaces) unique for the bank. Should map to transaction.details.type
    * @param summary A succinct summary
    * @param description A longer description
    * @param charge The fee to the customer each time this type of transaction happens
    */

  case class TransactionType (
                               id: TransactionTypeId,
                               bankId : BankId,
                               shortCode : String,
                               summary: String,
                               description: String,
                               charge: AmountOfMoney
  )


  val TransactionTypeProvider = new Inject(buildOne _) {}

  def buildOne: TransactionTypeProvider  =
    Props.get("TransactionTypes_connector", "mapped") match {
      case "mapped" => MappedTransactionTypeProvider
      case ttc: String => throw new IllegalArgumentException("No such connector for Transaction Types: " + ttc)
    }
  
}

trait TransactionTypeProvider {

  import code.TransactionTypes.TransactionType.TransactionType

  private val logger = Logger(classOf[TransactionTypeProvider])


  // Transaction types for bank (we may add getTransactionTypesForBankAccount and getTransactionTypesForBankAccountView)
  final def getTransactionTypesForBank(bankId : BankId) : Option[List[TransactionType]] = {
    getTransactionTypesForBankFromProvider(bankId)
  }

  final def getTransactionType(transactionTypeId : TransactionTypeId) : Option[TransactionType] = {
    getTransactionTypeFromProvider(transactionTypeId)
  }


  protected def getTransactionTypesForBankFromProvider(bankId : BankId) : Option[List[TransactionType]]

  protected def getTransactionTypeFromProvider(TransactionTypeId : TransactionTypeId) : Option[TransactionType]
}


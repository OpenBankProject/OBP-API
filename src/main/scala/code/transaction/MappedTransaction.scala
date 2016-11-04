package code.transaction

import java.util.UUID

import code.bankconnectors.Connector
import code.util.{DefaultStringField, Helper, MappedAccountNumber, MappedUUID}
import net.liftweb.common.Logger
import net.liftweb.mapper._
import code.model._

class MappedTransaction extends LongKeyedMapper[MappedTransaction] with IdPK with CreatedUpdated with TransactionUUID {

  private val logger = Logger(classOf[MappedTransaction])

  def getSingleton = MappedTransaction

  object bank extends MappedString(this, 255)
  object account extends MappedString(this, 255)
  object transactionId extends MappedString(this, 255) {
    override def defaultValue = UUID.randomUUID().toString
  }
  //TODO: review the need for this
  // (why do we need transactionUUID and transactionId - which is a UUID?)
  object transactionUUID extends MappedUUID(this)
  object transactionType extends MappedString(this, 100)

  //amount/new balance use the smallest unit of currency! e.g. cents, yen, pence, øre, etc.
  object amount extends MappedLong(this)
  object newAccountBalance extends MappedLong(this)

  object currency extends MappedString(this, 10) // This should probably be 3 only characters long

  object tStartDate extends MappedDateTime(this)
  object tFinishDate extends MappedDateTime(this)

  object description extends DefaultStringField(this)

  object counterpartyAccountNumber extends MappedAccountNumber(this)
  object counterpartyAccountHolder extends MappedString(this, 100)
  //still unclear exactly how what this is defined to mean
  object counterpartyNationalId extends MappedString(this, 40)
  //this should eventually be calculated using counterpartyNationalId
  object counterpartyBankName extends MappedString(this, 100)
  //this should eventually either generate counterpartyAccountNumber or be generated
  object counterpartyIban extends MappedString(this, 100)
  object counterpartyAccountKind extends MappedString(this, 40)

  //This is a holder for storing data from a previous model version that wasn't set correctly
  //e.g. some previous models had counterpartyAccountNumber set to a string that was clearly
  //not a valid account number, though the string may have actually contained the account number
  //somewhere within it (e.g. "BLS 3020201 BLAH BLAH S/C 2014-05-22")
  //
  // We save information like this so that we can try to manually process it later.
  //
  // Keep in mind that changing the counterparty account number will require an update
  // to the corresponding counterparty metadata object!
  @deprecated
  object extraInfo extends DefaultStringField(this)


  override def theTransactionId = TransactionId(transactionId.get)
  override def theAccountId = AccountId(account.get)
  override def theBankId = BankId(bank.get)

  def getCounterpartyIban() = {
    val i = counterpartyIban.get
    if(i.isEmpty) None else Some(i)
  }

  def toTransaction(account: BankAccount): Option[Transaction] = {
    val tBankId = theBankId
    val tAccId = theAccountId

    if (tBankId != account.bankId || tAccId != account.accountId) {
      logger.warn("Attempted to convert MappedTransaction to Transaction using unrelated existing BankAccount object")
      None
    } else {
      val label = {
        val d = description.get
        if (d.isEmpty) None else Some(d)
      }

      val transactionCurrency = currency.get
      val amt = Helper.smallestCurrencyUnitToBigDecimal(amount.get, transactionCurrency)
      val newBalance = Helper.smallestCurrencyUnitToBigDecimal(newAccountBalance.get, transactionCurrency)

      def createOtherBankAccount(alreadyFoundMetadata : Option[CounterpartyMetadata]) = {
        new Counterparty(
          id = alreadyFoundMetadata.map(_.metadataId).getOrElse(""),
          label = counterpartyAccountHolder.get,
          nationalIdentifier = counterpartyNationalId.get,
          swift_bic = None, //TODO: need to add this to the json/model
          iban = getCounterpartyIban(),
          number = counterpartyAccountNumber.get,
          bankName = counterpartyBankName.get,
          kind = counterpartyAccountKind.get,
          originalPartyBankId = theBankId,
          originalPartyAccountId = theAccountId,
          alreadyFoundMetadata = alreadyFoundMetadata
        )
      }

      //it's a bit confusing what's going on here, as normally metadata should be automatically generated if
      //it doesn't exist when an OtherBankAccount object is created. The issue here is that for legacy reasons
      //otherAccount ids are metadata ids, so the metadata needs to exist before we created the OtherBankAccount
      //so that we know what id to give it.

      //creates a dummy OtherBankAccount without an OtherBankAccountMetadata, which results in one being generated (in OtherBankAccount init)
      val dummyOtherBankAccount = createOtherBankAccount(None)

      //and create the proper OtherBankAccount with the correct "id" attribute set to the metadataId of the OtherBankAccountMetadata object
      //note: as we are passing in the OtherBankAccountMetadata we don't incur another db call to get it in OtherBankAccount init
      val otherAccount = createOtherBankAccount(Some(dummyOtherBankAccount.metadata))

      Some(new Transaction(
        transactionUUID.get,
        theTransactionId,
        account,
        otherAccount,
        transactionType.get,
        amt,
        transactionCurrency,
        label,
        tStartDate.get,
        tFinishDate.get,
        newBalance))
    }
  }

  def toTransaction : Option[Transaction] = {
    for {
      acc <- Connector.connector.vend.getBankAccount(theBankId, theAccountId)
      transaction <- toTransaction(acc)
    } yield transaction
  }

}

object MappedTransaction extends MappedTransaction with LongKeyedMetaMapper[MappedTransaction] {
  override def dbIndexes = UniqueIndex(transactionId, bank, account) :: Index(bank, account) :: super.dbIndexes
}

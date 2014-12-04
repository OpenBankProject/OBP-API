package code.model

import java.util.UUID

import code.bankconnectors.Connector
import code.metadata.counterparties.Counterparties
import code.util.{Helper, MappedUUID}
import net.liftweb.common.Box
import net.liftweb.mapper._

class MappedTransaction extends LongKeyedMapper[MappedTransaction] with IdPK with CreatedUpdated with TransactionUUID {
  def getSingleton = MappedTransaction

  object bank extends MappedString(this, 255)
  object account extends MappedString(this, 255)
  object transactionId extends MappedString(this, 255) {
    override def defaultValue = UUID.randomUUID().toString
  }
  //TODO: review the need for this
  object transactionUUID extends MappedUUID(this)
  object transactionType extends MappedString(this, 20)

  //amount/new balance use the smallest unit of currency! e.g. cents, yen, pence, øre, etc.
  object amount extends MappedLong(this)
  object newAccountBalance extends MappedLong(this)

  object currency extends MappedString(this, 10)

  object tStartDate extends MappedDateTime(this)
  object tFinishDate extends MappedDateTime(this)

  object description extends MappedText(this)

  object counterpartyAccountNumber extends MappedString(this, 60)
  object counterpartyAccountHolder extends MappedString(this, 100)
  //still unclear exactly how what this is defined to mean
  object counterpartyNationalId extends MappedString(this, 40)
  //this should eventually be calculated using counterpartyNationalId
  object counterpartyBankName extends MappedString(this, 100)
  //this should eventually either generate counterpartyAccountNumber or be generated
  object counterpartyIban extends MappedString(this, 100)
  object counterpartyAccountKind extends MappedString(this, 40)


  override def theTransactionId = TransactionId(transactionId.get)
  override def theAccountId = AccountId(account.get)
  override def theBankId = BankId(bank.get)

  def getCounterpartyIban() = {
    val i = counterpartyIban.get
    if(i.isEmpty) None else Some(i)
  }

  def toTransaction : Box[Transaction] = {

    val label = {
      val d = description.get
      if(d.isEmpty) None else Some(d)
    }

    val transactionCurrency = currency.get
    val amt = Helper.smallestCurrencyUnitToBigDecimal(amount.get, transactionCurrency)
    val newBalance = Helper.smallestCurrencyUnitToBigDecimal(newAccountBalance.get, transactionCurrency)

    def createOtherBankAccount(id: String) = {
      new OtherBankAccount(
        id = id,
        label = counterpartyAccountHolder.get,
        nationalIdentifier = counterpartyNationalId.get,
        swift_bic = None, //TODO: need to add this to the json/model
        iban = getCounterpartyIban(),
        number = counterpartyAccountNumber.get,
        bankName = counterpartyBankName.get,
        kind = counterpartyAccountKind.get,
        originalPartyBankId = theBankId,
        originalPartyAccountId = theAccountId
      )
    }

    //it's a bit confusing what's going on here, as normally metadata should be automatically generated if
    //it doesn't exist when an OtherBankAccount object is created. The issue here is that for legacy reasons
    //otherAccount ids are metadata ids, so the metadata needs to exist before we created the OtherBankAccount
    //so that we know what id to give it.
    val dummyOtherBankAccount = createOtherBankAccount("")
    val metadata = Counterparties.counterparties.vend.getOrCreateMetadata(theBankId, theAccountId, dummyOtherBankAccount)

    val otherAccount = createOtherBankAccount(metadata.metadataId)

    for {
      acc <- Connector.connector.vend.getBankAccount(theBankId, theAccountId)
    } yield {
      new Transaction(
        transactionUUID.get,
        theTransactionId,
        acc,
        otherAccount,
        transactionType.get,
        amt,
        transactionCurrency,
        label,
        tStartDate.get,
        tFinishDate.get,
        newBalance)
    }
  }
}

object MappedTransaction extends MappedTransaction with LongKeyedMetaMapper[MappedTransaction] {
  override def dbIndexes = UniqueIndex(transactionId, bank, account) :: Index(bank, account) :: super.dbIndexes
}

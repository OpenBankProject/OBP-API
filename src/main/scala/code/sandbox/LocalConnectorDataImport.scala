package code.sandbox

import code.metadata.counterparties.{MongoCounterparties, Metadata}
import code.model._
import code.model.dataAccess._
import net.liftweb.common._
import java.util.UUID
import net.liftweb.mapper.By
import net.liftweb.mongodb.record.MongoRecord
import net.liftweb.util.Helpers._
import org.bson.types.ObjectId

//An basic implementation of Saveable for MongoRecords
case class SaveableMongoObj[T <: MongoRecord[_]](value : T) extends Saveable[T] {
  def save() = value.save(true)
}

/**
 * Imports data into the format used by LocalRecordConnector (e.g. HostedBank)
 */


/*

Not currently using this connector so not updating it at the moment.

object LocalRecordConnectorDataImport extends OBPDataImport with CreateAuthUsers {

  type BankType = HostedBank
  type AccountType = Account
  type TransactionType = OBPEnvelope
  type MetadataType = Metadata

  protected def createSaveableBanks(data : List[SandboxBankImport]) : Box[List[Saveable[BankType]]] = {
    val hostedBanks = data.map(b => {
      HostedBank.createRecord
        .id(ObjectId.get)
        .permalink(b.id)
        .name(b.full_name)
        .alias(b.short_name)
        .website(b.website)
        .logoURL(b.logo)
        .national_identifier(b.id) //this needs to match up with what goes in the OBPEnvelopes
    })

    val validationErrors = hostedBanks.flatMap(_.validate)

    if(!validationErrors.isEmpty) {
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      Full(hostedBanks.map(SaveableMongoObj(_)))
    }
  }

  protected def createSaveableAccount(acc : SandboxAccountImport, banks : List[HostedBank]) : Box[Saveable[Account]] = {
    def getHostedBank(acc : SandboxAccountImport) = Box(banks.find(b => b.permalink.get == acc.bank))

    def asSaveableAccount(account : Account, hostedBank : HostedBank) = new Saveable[Account] {
      val value = account

      def save() = {
        //this looks pointless, but what it is doing is refreshing the Account.bankID.obj reference, which
        //is used by Account.bankId. If we don't refresh it here, Account.bankId will return BankId("")
        account.bankID(account.bankID.get).save(true)
      }
    }

    for {
      hBank <- getHostedBank(acc) ?~ {
        logger.warn("hosted bank not found")
        "Server error"
      }
      balance <- tryo{BigDecimal(acc.balance.amount)} ?~ s"Invalid balance: ${acc.balance.amount}"
    } yield {
      val account = Account.createRecord
        .permalink(acc.id)
        .bankID(hBank.id.get)
        .accountLabel(acc.label)
        .accountCurrency(acc.balance.currency)
        .accountBalance(balance)
        .accountNumber(acc.number)
        .kind(acc.`type`)
        .accountIban(acc.IBAN)

      asSaveableAccount(account, hBank)
    }
  }

  override protected def createSaveableTransaction(t : SandboxTransactionImport, createdBanks : List[BankType], createdAccounts : List[AccountType]) :
    Box[Saveable[TransactionType]] = {

    val counterpartyHolder = t.counterparty.flatMap(_.name).getOrElse("")

    for {
      createdBank <- Box(createdBanks.find(b => b.permalink.get == t.this_account.bank)) ?~
        s"Transaction this_account bank must be specified in import banks. Unspecified bank: ${t.this_account.bank}"
      //have to compare a.bankID to createdBank.id instead of just checking a.bankId against t.this_account.bank as createdBank hasn't been
      //saved so the a.bankId method (which involves a db lookup) will not work
      createdAcc <- Box(createdAccounts.find(a => a.bankID.toString == createdBank.id.get.toString && a.accountId == AccountId(t.this_account.id))) ?~
        s"Transaction this_account account must be specified in import accounts. Unspecified account id: ${t.this_account.id} at bank: ${t.this_account.bank}"
      newBalanceValue <- tryo{BigDecimal(t.details.new_balance)} ?~ s"Invalid new balance: ${t.details.new_balance}"
      tValue <- tryo{BigDecimal(t.details.value)} ?~ s"Invalid transaction value: ${t.details.value}"
      postedDate <- tryo{dateFormat.parse(t.details.posted)} ?~ s"Invalid date format: ${t.details.posted}. Expected pattern $datePattern"
      completedDate <-tryo{dateFormat.parse(t.details.completed)} ?~ s"Invalid date format: ${t.details.completed}. Expected pattern $datePattern"
    } yield {

      //bankNationalIdentifier not available from  createdAcc.bankNationalIdentifier as it hasn't been saved so we get it from createdBank
      val obpThisAccountBank = OBPBank.createRecord
        .national_identifier(createdBank.national_identifier.get)

      val obpThisAccount = OBPAccount.createRecord
        .holder(createdAcc.holder.get)
        .number(createdAcc.accountNumber.get)
        .kind(createdAcc.kind.get)
        .bank(obpThisAccountBank)

      val counterpartyAccountNumber = t.counterparty.flatMap(_.account_number)

      val obpOtherAccount = OBPAccount.createRecord
        .holder(counterpartyHolder)
        .number(counterpartyAccountNumber.getOrElse(""))

      val newBalance = OBPBalance.createRecord
        .amount(newBalanceValue)
        .currency(createdAcc.accountCurrency.get)

      val transactionValue = OBPValue.createRecord
        .amount(tValue)
        .currency(createdAcc.accountCurrency.get)

      val obpDetails = OBPDetails.createRecord
        .completed(completedDate)
        .posted(postedDate)
        .kind(t.details.`type`)
        .label(t.details.description)
        .new_balance(newBalance)
        .value(transactionValue)


      val obpTransaction = OBPTransaction.createRecord
        .details(obpDetails)
        .this_account(obpThisAccount)
        .other_account(obpOtherAccount)

      val env = OBPEnvelope.createRecord
        .transactionId(t.id)
        .obp_transaction(obpTransaction)

      SaveableMongoObj(env)
    }
  }

}

*/

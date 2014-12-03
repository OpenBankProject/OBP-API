package code.sandbox

import code.metadata.counterparties.{MongoCounterparties, Metadata}
import code.model._
import code.model.dataAccess._
import net.liftweb.common._
import java.util.UUID
import net.liftweb.mongodb.record.MongoRecord
import net.liftweb.util.Helpers._
import org.bson.types.ObjectId

//An basic implementation of Saveable for MongoRecords
case class SaveableMongoObj[T <: MongoRecord[_]](value : T) extends Saveable[T] {
  def save() = value.save(true)
}

/**
 * Imports data into the format used by LocalConnector (e.g. HostedBank)
 */
object LocalConnectorDataImport extends OBPDataImport with CreateViewImpls {

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

  override protected def createSaveableTransactions(transactions : List[SandboxTransactionImport], createdBanks : List[BankType], createdAccounts : List[AccountType]):
    Box[List[Saveable[TransactionType]]] = {

    /**
     * Because we want to generate placeholder counterparty names if they're not present, but also want to have counterparties with
     * missing names but the same account number share metadata, we need to keep track of all generated names and the account numbers
     * to which they are linked to avoid generating two names for the same account number
     */
    val emptyHoldersAccNums = scala.collection.mutable.Map[String, String]()

    val envs : List[Box[OBPEnvelope]] = transactions.map(t => {

      type Counterparty = String

      def randomCounterpartyHolderName(accNumber: Option[String]) : String = {
        val name = s"unknown_${UUID.randomUUID.toString}"
        accNumber.foreach(emptyHoldersAccNums.put(_, name))
        name
      }

      val counterpartyAccNumber = t.counterparty.flatMap(_.account_number)

      //If the counterparty name is present in 't', then use it
      val counterpartyHolder = t.counterparty.flatMap(_.name) match {
        case Some(holder) if holder.nonEmpty => holder
        case _ => {
          counterpartyAccNumber match {
            case Some(accNum) if accNum.nonEmpty => {
              val existing = emptyHoldersAccNums.get(accNum)
              existing match {
                case Some(e) => e //holder already generated for an empty-name counterparty with the same account number
                case None => randomCounterpartyHolderName(Some(accNum)) //generate a new counterparty name
              }
            }
            //no name, no account number, generate a random new holder
            case _ => randomCounterpartyHolderName(None)
          }
        }
      }

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

        env
      }

    })

    val envelopes = dataOrFirstFailure(envs)
    envelopes.map(es => es.map(SaveableMongoObj(_)))
  }

}

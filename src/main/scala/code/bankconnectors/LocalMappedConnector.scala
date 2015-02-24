package code.bankconnectors

import java.util.Date

import code.metadata.counterparties.Counterparties
import code.model._
import code.model.dataAccess.{UpdatesRequestSender, MappedBankAccount, MappedAccountHolder, MappedBank}
import code.tesobe.CashTransaction
import code.tesobe.ImporterAPI.ImporterTransaction
import code.util.Helper
import com.tesobe.model.UpdateBankAccount
import net.liftweb.common.{Loggable, Full, Box}
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

import scala.concurrent.ops._

object LocalMappedConnector extends Connector with Loggable {

  type AccountType = MappedBankAccount

  //gets a particular bank handled by this connector
  override def getBank(bankId: BankId): Box[Bank] =
    getMappedBank(bankId)

  private def getMappedBank(bankId: BankId): Box[MappedBank] =
    MappedBank.find(By(MappedBank.permalink, bankId.value))

  //gets banks handled by this connector
  override def getBanks: List[Bank] =
    MappedBank.findAll

  override def getTransaction(bankId: BankId, accountID: AccountId, transactionId: TransactionId): Box[Transaction] = {

    updateAccountTransactions(bankId, accountID)

    MappedTransaction.find(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountID.value),
      By(MappedTransaction.transactionId, transactionId.value)).flatMap(_.toTransaction)
  }

  override def getTransactions(bankId: BankId, accountID: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedTransaction](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedTransaction](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedTransaction.tFinishDate, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedTransaction.tFinishDate, date) }.headOption
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedTransaction.tFinishDate, Ascending)
          case OBPDescending => OrderBy(MappedTransaction.tFinishDate, Descending)
        }
    }

    val optionalParams : Seq[QueryParam[MappedTransaction]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
    val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountID.value)) ++ optionalParams

    val mappedTransactions = MappedTransaction.findAll(mapperParams: _*)

    updateAccountTransactions(bankId, accountID)

    for (account <- getBankAccount(bankId, accountID))
      yield mappedTransactions.flatMap(_.toTransaction(account))
  }

  /**
   *
   * refreshes transactions via hbci if the transaction info is sourced from hbci
   *
   *  Checks if the last update of the account was made more than one hour ago.
   *  if it is the case we put a message in the message queue to ask for
   *  transactions updates
   *
   *  It will be used each time we fetch transactions from the DB. But the test
   *  is performed in a different thread.
   */
  private def updateAccountTransactions(bankId : BankId, accountId : AccountId) = {

    for {
      bank <- getMappedBank(bankId)
      account <- getBankAccountType(bankId, accountId)
    } {
      spawn{
        val useMessageQueue = Props.getBool("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = now after time(account.lastUpdate.get.getTime + hours(1))
        if(outDatedTransactions && useMessageQueue) {
          UpdatesRequestSender.sendMsg(UpdateBankAccount(account.accountNumber.get, bank.national_identifier.get))
        }
      }
    }
  }

  override def getBankAccountType(bankId: BankId, accountId: AccountId): Box[MappedBankAccount] = {
    MappedBankAccount.find(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.theAccountId, accountId.value))
  }

  //gets the users who are the legal owners/holders of the account
  override def getAccountHolders(bankId: BankId, accountID: AccountId): Set[User] =
    MappedAccountHolder.findAll(
      By(MappedAccountHolder.accountBankPermalink, bankId.value),
      By(MappedAccountHolder.accountPermalink, accountID.value)).map(accHolder => accHolder.user.obj).flatten.toSet


  def getOtherBankAccount(thisAccountBankId : BankId, thisAccountId : AccountId, metadata : OtherBankAccountMetadata) : Box[OtherBankAccount] = {
    //because we don't have a db backed model for OtherBankAccounts, we need to construct it from an
    //OtherBankAccountMetadata and a transaction
    for { //find a transaction with this counterparty
      t <- MappedTransaction.find(
        By(MappedTransaction.bank, thisAccountBankId.value),
        By(MappedTransaction.account, thisAccountId.value),
        By(MappedTransaction.counterpartyAccountHolder, metadata.getHolder),
        By(MappedTransaction.counterpartyAccountNumber, metadata.getAccountNumber))
    } yield {
      new OtherBankAccount(
        //counterparty id is defined to be the id of its metadata as we don't actually have an id for the counterparty itself
        id = metadata.metadataId,
        label = metadata.getHolder,
        nationalIdentifier = t.counterpartyNationalId.get,
        swift_bic = None,
        iban = t.getCounterpartyIban(),
        number = metadata.getAccountNumber,
        bankName = t.counterpartyBankName.get,
        kind = t.counterpartyAccountKind.get,
        originalPartyBankId = thisAccountBankId,
        originalPartyAccountId = thisAccountId,
        alreadyFoundMetadata = Some(metadata)
      )
    }
  }

  override def getOtherBankAccounts(bankId: BankId, accountID: AccountId): List[OtherBankAccount] =
    Counterparties.counterparties.vend.getMetadatas(bankId, accountID).flatMap(getOtherBankAccount(bankId, accountID, _))

  override def getOtherBankAccount(bankId: BankId, accountID: AccountId, otherAccountID: String): Box[OtherBankAccount] =
    Counterparties.counterparties.vend.getMetadata(bankId, accountID, otherAccountID).flatMap(getOtherBankAccount(bankId, accountID, _))

  override def getPhysicalCards(user: User): Set[PhysicalCard] =
    Set.empty

  override def getPhysicalCardsForBank(bankId: BankId, user: User): Set[PhysicalCard] =
    Set.empty


  override def makePaymentImpl(fromAccount: MappedBankAccount, toAccount: MappedBankAccount, amt: BigDecimal): Box[TransactionId] = {
    val fromTransAmt = -amt //from account balance should decrease
    val toTransAmt = amt //to account balance should increase

    //we need to save a copy of this payment as a transaction in each of the accounts involved, with opposite amounts
    val sentTransactionId = saveTransaction(fromAccount, toAccount, fromTransAmt)
    saveTransaction(toAccount, fromAccount, toTransAmt)

    sentTransactionId
  }

  /**
   * Saves a transaction with amount @amt and counterparty @counterparty for account @account. Returns the id
   * of the saved transaction.
   */
  private def saveTransaction(account : MappedBankAccount, counterparty : BankAccount, amt : BigDecimal) : Box[TransactionId] = {

    val transactionTime = now
    val currency = account.currency


    //update the balance of the account for which a transaction is being created
    val newAccountBalance : Long = account.accountBalance.get + Helper.convertToSmallestCurrencyUnits(amt, account.currency)
    account.accountBalance(newAccountBalance).save()


    val mappedTransaction = MappedTransaction.create
      .bank(account.bankId.value)
      .account(account.accountId.value)
      .transactionType("sandbox-payment")
      .amount(Helper.convertToSmallestCurrencyUnits(amt, currency))
      .newAccountBalance(newAccountBalance)
      .currency(currency)
      .tStartDate(transactionTime)
      .tFinishDate(transactionTime)
      .description("")
      .counterpartyAccountHolder(counterparty.accountHolder)
      .counterpartyAccountNumber(counterparty.number)
      .counterpartyAccountKind(counterparty.accountType)
      .counterpartyBankName(counterparty.bankName)
      .counterpartyIban(counterparty.iban.getOrElse(""))
      .counterpartyNationalId(counterparty.nationalIdentifier).saveMe

    Full(mappedTransaction.theTransactionId)
  }

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  override def createBankAndAccount(bankName: String, bankNationalIdentifier: String, accountNumber: String, accountHolderName: String): (Bank, BankAccount) = ???

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String): Boolean = ???

  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  override def createSandboxBankAccount(bankId: BankId, accountId: AccountId, accountNumber: String, currency: String, initialBalance: BigDecimal, accountHolderName: String): Box[BankAccount] = ???

  //used by the transaction import api
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal): Boolean = ???

  //sets a user as an account owner/holder
  override def setAccountHolder(bankAccountUID: BankAccountUID, user: User): Unit = ???

  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String): Int = ???

  //used by transaction import api
  override def createImportedTransaction(transaction: ImporterTransaction): Box[Transaction] = ???

  //cash api requires getting an account via a uuid: for legacy reasons it does not use bankId + accountId
  override def getAccountByUUID(uuid: String): Box[AccountType] = {
    MappedBankAccount.find(By(MappedBankAccount.accUUID, uuid))
  }

  //cash api requires a call to add a new transaction and update the account balance
  override def addCashTransactionAndUpdateBalance(account: AccountType, cashTransaction: CashTransaction): Unit = {

    val currency = account.currency
    val currencyDecimalPlaces = Helper.currencyDecimalPlaces(currency)

    //not ideal to have to convert it this way
    def doubleToSmallestCurrencyUnits(x : Double) : Long = {
      (x * math.pow(10, currencyDecimalPlaces)).toLong
    }

    //can't forget to set the sign of the amount cashed on kind being "in" or "out"
    //we just assume if it's not "in", then it's "out"
    val amountInSmallestCurrencyUnits = {
      if(cashTransaction.kind == "in") doubleToSmallestCurrencyUnits(cashTransaction.amount)
      else doubleToSmallestCurrencyUnits(-1 * cashTransaction.amount)
    }

    val currentBalanceInSmallestCurrencyUnits = account.accountBalance.get
    val newBalanceInSmallestCurrencyUnits = currentBalanceInSmallestCurrencyUnits + amountInSmallestCurrencyUnits

    //create transaction
    val transactionCreated = MappedTransaction.create
      .bank(account.bankId.value)
      .account(account.accountId.value)
      .transactionType("cash")
      .amount(amountInSmallestCurrencyUnits)
      .newAccountBalance(newBalanceInSmallestCurrencyUnits)
      .currency(account.currency)
      .tStartDate(cashTransaction.date)
      .tFinishDate(cashTransaction.date)
      .description(cashTransaction.label)
      .counterpartyAccountHolder(cashTransaction.otherParty)
      .counterpartyAccountKind("cash")
      .save

    if(!transactionCreated) {
      logger.warn("Failed to save cash transaction")
    } else {
      //update account
      val accountUpdated = account.accountBalance(newBalanceInSmallestCurrencyUnits).save()

      if(!accountUpdated)
        logger.warn("Failed to update account balance after new cash transaction")
    }
  }
}

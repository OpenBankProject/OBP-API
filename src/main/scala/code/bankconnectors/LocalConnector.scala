package code.bankconnectors

import java.text.SimpleDateFormat
import java.util.TimeZone

import code.payments.{SandboxLocalConnectorPayments, PaymentProcessor}
import net.liftweb.common.{Failure, Box, Loggable, Full}
import net.liftweb.json.JsonAST.JValue
import scala.concurrent.ops.spawn
import code.model._
import code.model.dataAccess._
import net.liftweb.mapper.By
import net.liftweb.mongodb.BsonDSL._
import org.bson.types.ObjectId
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import com.mongodb.QueryBuilder
import code.metadata.counterparties.{Counterparties, MongoCounterparties, Metadata}
import com.tesobe.model.UpdateBankAccount

private object LocalConnector extends Connector with Loggable {

  type AccountType = Account

  override def getBank(bankId : BankId): Box[Bank] =
    getHostedBank(bankId)

  //gets banks handled by this connector
  override def getBanks : List[Bank] =
    HostedBank.findAll

  override def getBankAccountType(bankId : BankId, accountId : AccountId) : Box[Account] = {
    for{
      bank <- getHostedBank(bankId)
      account <- bank.getAccount(accountId)
    } yield account
  }


  override def getOtherBankAccount(bankId: BankId, accountId : AccountId, otherAccountID : String): Box[OtherBankAccount] = {

    /**
     * In this implementation (for legacy reasons), the "otherAccountID" is actually the mongodb id of the
     * "other account metadata" object.
     */

      for{
        objId <- tryo{ new ObjectId(otherAccountID) }
        otherAccountmetadata <- {
          //"otherAccountID" is actually the mongodb id of the other account metadata" object.
          val query = QueryBuilder.
            start("_id").is(objId)
            .put("originalPartyBankId").is(bankId.value)
            .put("originalPartyAccountId").is(accountId.value).get()
          Metadata.find(query)
        }
      } yield{
        val query = QueryBuilder
          .start("obp_transaction.other_account.holder").is(otherAccountmetadata.holder.get)
          .put("obp_transaction.other_account.number").is(otherAccountmetadata.accountNumber.get).get()

        val otherAccountFromTransaction : OBPAccount = OBPEnvelope.find(query) match {
          case Full(envelope) => envelope.obp_transaction.get.other_account.get
          case _ => {
            logger.warn("no other account found")
            OBPAccount.createRecord
          }
        }
        createOtherBankAccount(bankId, accountId, otherAccountmetadata, otherAccountFromTransaction)
      }
  }

  override def getOtherBankAccounts(bankId: BankId, accountId : AccountId): List[OtherBankAccount] = {

    /**
     * In this implementation (for legacy reasons), the "otherAccountID" is actually the mongodb id of the
     * "other account metadata" object.
     */

    Counterparties.counterparties.vend.getMetadatas(bankId, accountId).map(meta => {
      //for legacy reasons some of the data about the "other account" are stored only on the transactions
      //so we need first to get a transaction that match to have the rest of the data
      val query = QueryBuilder
        .start("obp_transaction.other_account.holder").is(meta.getHolder)
        .put("obp_transaction.other_account.number").is(meta.getAccountNumber).get()

      val otherAccountFromTransaction : OBPAccount = OBPEnvelope.find(query) match {
        case Full(envelope) => {
          envelope.obp_transaction.get.other_account.get
        }
        case _ => {
          logger.warn(s"envelope not found for other account ${meta.metadataId}")
          OBPAccount.createRecord
        }
      }
      createOtherBankAccount(bankId, accountId, meta, otherAccountFromTransaction)
    })
  }

  override def getTransactions(bankId: BankId, accountId: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
    logger.debug("getTransactions for " + bankId + "/" + accountId)
    for{
      bank <- getHostedBank(bankId)
      account <- bank.getAccount(accountId)
    } yield {
      updateAccountTransactions(bank, account)
      account.envelopes(queryParams: _*).map(createTransaction(_, account))
    }
  }

  override def getTransaction(bankId: BankId, accountId : AccountId, transactionId : TransactionId): Box[Transaction] = {
    for{
      bank <- getHostedBank(bankId) ?~! s"Transaction not found: bank $bankId not found"
      account  <- bank.getAccount(accountId) ?~! s"Transaction not found: account $accountId not found"
      envelope <- OBPEnvelope.find(account.transactionsForAccount.put("transactionId").is(transactionId.value).get)
    } yield {
      updateAccountTransactions(bank, account)
      createTransaction(envelope,account)
    }
  }

  override def getPhysicalCards(user : User) : Set[PhysicalCard] = {
    Set.empty
  }

  override def getPhysicalCardsForBank(bankId: BankId, user : User) : Set[PhysicalCard] = {
    Set.empty
  }

  override def getAccountHolders(bankId: BankId, accountID: AccountId) : Set[User] = {
    MappedAccountHolder.findAll(
      By(MappedAccountHolder.accountBankPermalink, bankId.value),
      By(MappedAccountHolder.accountPermalink, accountID.value)).map(accHolder => accHolder.user.obj).flatten.toSet
  }

  override protected def makePaymentImpl(fromAccount : Account, toAccount : Account, amt : BigDecimal) : Box[TransactionId] = {
    val fromTransAmt = -amt //from account balance should decrease
    val toTransAmt = amt //to account balance should increase

    //this is the transaction that gets attached to the account of the person making the payment
    val createdFromTrans = saveNewTransaction(fromAccount, toAccount, fromTransAmt)

    // this creates the transaction that gets attached to the account of the person receiving the payment
    saveNewTransaction(toAccount, fromAccount, toTransAmt)

    //assumes OBPEnvelope id is what gets used as the Transaction id in the API. If that gets changed, this needs to
    //be updated (the tests should fail if it doesn't)
    createdFromTrans.map(t => TransactionId(t.transactionId.get))
  }

  private def createTransaction(env: OBPEnvelope, theAccount: Account): Transaction = {
    val transaction: OBPTransaction = env.obp_transaction.get
    val otherAccount_ = transaction.other_account.get

    val id = TransactionId(env.transactionId.get)
    val uuid = id.value

    //slight hack required: otherAccount id is, for legacy reasons, the mongodb id of its metadata object
    //so we have to find that
    val query = QueryBuilder.start("originalPartyBankId").is(theAccount.bankId.value).
      put("originalPartyAccountId").is(theAccount.permalink.get).
      put("accountNumber").is(otherAccount_.number.get).
      put("holder").is(otherAccount_.holder.get).get


    //it's a bit confusing what's going on here, as normally metadata should be automatically generated if
    //it doesn't exist when an OtherBankAccount object is created. The issue here is that for legacy reasons
    //otherAccount ids are mongo metadata ids, so the metadata needs to exist before we created the OtherBankAccount
    //so that we know what id to give it. That's why there's a hardcoded dependency on MongoCounterparties.
    val metadataId = Metadata.find(query) match {
      case Full(m) => m.id.get.toString
      case _ => MongoCounterparties.createMetadata(
        theAccount.bankId,
        theAccount.accountId,
        otherAccount_.holder.get,
        otherAccount_.number.get).id.get.toString
    }

    val otherAccount = new OtherBankAccount(
      id = metadataId,
      label = otherAccount_.holder.get,
      nationalIdentifier = otherAccount_.bank.get.national_identifier.get,
      swift_bic = None, //TODO: need to add this to the json/model
      iban = Some(otherAccount_.bank.get.IBAN.get),
      number = otherAccount_.number.get,
      bankName = otherAccount_.bank.get.name.get,
      kind = otherAccount_.kind.get,
      originalPartyBankId = theAccount.bankId,
      originalPartyAccountId = theAccount.accountId
    )
    val transactionType = transaction.details.get.kind.get
    val amount = transaction.details.get.value.get.amount.get
    val currency = transaction.details.get.value.get.currency.get
    val label = Some(transaction.details.get.label.get)
    val startDate = transaction.details.get.posted.get
    val finishDate = transaction.details.get.completed.get
    val balance = transaction.details.get.new_balance.get.amount.get

    new Transaction(
      uuid,
      id,
      theAccount,
      otherAccount,
      transactionType,
      amount,
      currency,
      label,
      startDate,
      finishDate,
      balance)
  }

  private def saveNewTransaction(account : Account, otherAccount : Account, amount : BigDecimal) : Box[OBPEnvelope] = {

    val oldBalance = account.balance

    def saveAndUpdateAccountBalance(transactionJS : JValue, thisAccount : Account) : Box[OBPEnvelope] = {

      val envelope: Box[OBPEnvelope] = OBPEnvelope.envlopesFromJvalue(transactionJS)

      if(envelope.isDefined) {
        val e : OBPEnvelope = envelope.get
        logger.debug(s"Updating current balance for ${thisAccount.bankName} / ${thisAccount.accountNumber} / ${thisAccount.accountType}")
        thisAccount.accountBalance(e.obp_transaction.get.details.get.new_balance.get.amount.get).save
        logger.debug("Saving new transaction")
        Full(e.save)
      } else {
        Failure("couldn't save transaction")
      }
    }

    for {
      otherBank <- Connector.connector.vend.getBank(otherAccount.bankId) ?~! "no other bank found"
      transTime = now
      //mongodb/the lift mongo thing wants a literal Z in the timestamp, apparently
      envJsonDateFormat = {
        val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
        simpleDateFormat
      }

      envJson =
      ("obp_transaction" ->
        ("this_account" ->
          ("holder" -> account.owners.headOption.map(_.name).getOrElse("")) ~ //TODO: this is rather fragile...
            ("number" -> account.number) ~
            ("kind" -> account.accountType) ~
            ("bank" ->
              ("IBAN" -> account.iban.getOrElse("")) ~
                ("national_identifier" -> account.nationalIdentifier) ~
                ("name" -> account.bankId.value))) ~
          ("other_account" ->
            ("holder" -> otherAccount.accountHolder) ~
              ("number" -> otherAccount.number) ~
              ("kind" -> otherAccount.accountType) ~
              ("bank" ->
                ("IBAN" -> "") ~
                  ("national_identifier" -> otherBank.nationalIdentifier) ~
                  ("name" -> otherBank.fullName))) ~
          ("details" ->
            ("type_en" -> "") ~
              ("type_de" -> "") ~
              ("posted" ->
                ("$dt" -> envJsonDateFormat.format(transTime))
                ) ~
              ("completed" ->
                ("$dt" -> envJsonDateFormat.format(transTime))
                ) ~
              ("new_balance" ->
                ("currency" -> account.currency) ~
                  ("amount" -> (oldBalance + amount).toString)) ~
              ("value" ->
                ("currency" -> account.currency) ~
                  ("amount" -> amount.toString))))
      saved <- saveAndUpdateAccountBalance(envJson, account)
    } yield {
      saved
    }
  }

  /**
  *  Checks if the last update of the account was made more than one hour ago.
  *  if it is the case we put a message in the message queue to ask for
  *  transactions updates
  *
  *  It will be used each time we fetch transactions from the DB. But the test
  *  is performed in a different thread.
  */

  private def updateAccountTransactions(bank: HostedBank, account: Account): Unit = {
    spawn{
      val useMessageQueue = Props.getBool("messageQueue.updateBankAccountsTransaction", false)
      val outDatedTransactions = now after time(account.lastUpdate.get.getTime + hours(1))
      if(outDatedTransactions && useMessageQueue) {
        UpdatesRequestSender.sendMsg(UpdateBankAccount(account.accountNumber.get, bank.national_identifier.get))
      }
    }
  }


  private def createOtherBankAccount(originalPartyBankId: BankId, originalPartyAccountId: AccountId,
    otherAccount : OtherBankAccountMetadata, otherAccountFromTransaction : OBPAccount) : OtherBankAccount = {
    new OtherBankAccount(
      id = otherAccount.metadataId,
      label = otherAccount.getHolder,
      nationalIdentifier = otherAccountFromTransaction.bank.get.national_identifier.get,
      swift_bic = None, //TODO: need to add this to the json/model
      iban = Some(otherAccountFromTransaction.bank.get.IBAN.get),
      number = otherAccountFromTransaction.number.get,
      bankName = otherAccountFromTransaction.bank.get.name.get,
      kind = "",
      originalPartyBankId = originalPartyBankId,
      originalPartyAccountId = originalPartyAccountId
    )
  }

  private def getHostedBank(bankId : BankId) : Box[HostedBank] = {
    HostedBank.find("permalink", bankId.value) ?~ {"bank " + bankId + " not found"}
  }
}
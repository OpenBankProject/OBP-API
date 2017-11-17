package code.bankconnectors

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone, UUID}

import code.api.util.SessionContext
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.atms.Atms.AtmId
import code.atms.MappedAtm
import code.bankconnectors.vMar2017.InboundAdapterInfoInternal
import code.branches.Branches.{Branch, BranchId, BranchT}
import code.branches.MappedBranch
import code.fx.{FXRate, fx}
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.counterparties.{Counterparties, CounterpartyTrait, Metadata, MongoCounterparties}
import code.model._
import code.model.dataAccess._
import code.products.Products.{Product, ProductCode}
import code.transactionrequests.TransactionRequestTypeCharge
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import code.transactionrequests.TransactionRequests._
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.mongodb.QueryBuilder
import com.tesobe.model.UpdateBankAccount
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.mongodb.BsonDSL._
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import org.bson.types.ObjectId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

private object LocalRecordConnector extends Connector with MdcLoggable {

  type AccountType = Account

  implicit override val nameOfConnector = LocalRecordConnector.getClass.getSimpleName

  override def getAdapterInfo: Box[InboundAdapterInfoInternal] = Empty

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String) = {
    val limit = BigDecimal("50")
    val rate = fx.exchangeRate ("EUR", currency)
    val convertedLimit = fx.convert(limit, rate)
    Full(AmountOfMoney(currency,convertedLimit.toString()))
  }
  
  override def getChargeLevel(bankId: BankId,
                              accountId: AccountId,
                              viewId: ViewId,
                              userId: String,
                              userName: String,
                              transactionRequestType: String,
                              currency: String): Box[AmountOfMoney] = {
    LocalMappedConnector.getChargeLevel(bankId: BankId, accountId: AccountId, viewId: ViewId, userId: String, userName: String,
                                        transactionRequestType: String, currency: String)
  }

  override def getBank(bankId : BankId): Box[Bank] =
    getHostedBank(bankId)

  //gets banks handled by this connector
  override def getBanks(): Box[List[Bank]] =
    Full(HostedBank.findAll)

  override def getBankAccount(bankId : BankId, accountId : AccountId, session: Option[SessionContext]) : Box[Account] = {
    for{
      bank <- getHostedBank(bankId)
      account <- bank.getAccount(accountId)
    } yield account
  }


  override def getCounterpartyFromTransaction(bankId: BankId, accountId : AccountId, counterpartyID : String): Box[Counterparty] = {

    /**
     * In this implementation (for legacy reasons), the "otherAccountID" is actually the mongodb id of the
     * "other account metadata" object.
     */

      for{
        objId <- tryo{ new ObjectId(counterpartyID) }
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

  override def getCounterpartiesFromTransaction(bankId: BankId, accountId : AccountId) = {

    /**
     * In this implementation (for legacy reasons), the "otherAccountID" is actually the mongodb id of the
     * "other account metadata" object.
     */

    Full(Counterparties.counterparties.vend.getMetadatas(bankId, accountId).map(meta => {
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
    }))
  }

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] = Empty

  override def getTransactions(bankId: BankId, accountId: AccountId, session: Option[SessionContext], queryParams: OBPQueryParam*): Box[List[Transaction]] = {
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

  override def createOrUpdatePhysicalCard(bankCardNumber: String,
                      nameOnCard: String,
                      issueNumber: String,
                      serialNumber: String,
                      validFrom: Date,
                      expires: Date,
                      enabled: Boolean,
                      cancelled: Boolean,
                      onHotList: Boolean,
                      technology: String,
                      networks: List[String],
                      allows: List[String],
                      accountId: String,
                      bankId: String,
                      replacement: Option[CardReplacementInfo],
                      pinResets: List[PinResetInfo],
                      collected: Option[CardCollectionInfo],
                      posted: Option[CardPostedInfo]
                     ) : Box[PhysicalCard] = {
    Empty
  }


  override protected def makePaymentImpl(fromAccount: Account, toAccount: Account, toCounterparty: CounterpartyTrait, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String): Box[TransactionId] = {
    val fromTransAmt = -amt //from account balance should decrease
    val toTransAmt = amt //to account balance should increase

    //this is the transaction that gets attached to the account of the person making the payment
    val createdFromTrans = saveNewTransaction(fromAccount, toAccount, fromTransAmt, description)

    // this creates the transaction that gets attached to the account of the person receiving the payment
    saveNewTransaction(toAccount, fromAccount, toTransAmt, description)

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
    val metadata = Metadata.find(query) match {
      case Full(m) => m
      case _ => MongoCounterparties.createMetadata(
        theAccount.bankId,
        theAccount.accountId,
        otherAccount_.holder.get,
        otherAccount_.number.get)
    }

    val otherAccount = new Counterparty(
      counterPartyId = metadata.metadataId,
      label = otherAccount_.holder.get,
      nationalIdentifier = otherAccount_.bank.get.national_identifier.get,
      otherBankRoutingAddress = None, 
      otherAccountRoutingAddress = Some(otherAccount_.bank.get.IBAN.get),
      thisAccountId = AccountId(otherAccount_.number.get),
      thisBankId = BankId(otherAccount_.bank.get.name.get),
      kind = otherAccount_.kind.get,
      otherBankId = theAccount.bankId,
      otherAccountId = theAccount.accountId,
      alreadyFoundMetadata = Some(metadata),
      name = "",
      otherBankRoutingScheme = "",
      otherAccountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true
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

  private def saveNewTransaction(account : Account, otherAccount : Account, amount : BigDecimal, description : String) : Box[OBPEnvelope] = {

    val oldBalance = account.balance

    def saveAndUpdateAccountBalance(transactionJS : JValue, thisAccount : Account) : Box[OBPEnvelope] = {

      val envelope: Box[OBPEnvelope] = OBPEnvelope.envelopesFromJValue(transactionJS)

      if(envelope.isDefined) {
        val e : OBPEnvelope = envelope.openOrThrowException("Attempted to open an empty Box.")
        logger.debug(s"Updating current balance for ${thisAccount.bankName} / ${thisAccount.accountNumber} / ${thisAccount.accountType}")
        thisAccount.accountBalance(e.obp_transaction.get.details.get.new_balance.get.amount.get).save(true)
        logger.debug("Saving new transaction")
        Full(e.save(true))
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
      "obp_transaction" ->
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
                  ("amount" -> amount.toString)))
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
    Future {
      val useMessageQueue = Props.getBool("messageQueue.updateBankAccountsTransaction", false)
      val outDatedTransactions = now after time(account.accountLastUpdate.get.getTime + hours(Props.getInt("messageQueue.updateTransactionsInterval", 1)))
      if(outDatedTransactions && useMessageQueue) {
        UpdatesRequestSender.sendMsg(UpdateBankAccount(account.accountNumber.get, bank.national_identifier.get))
      }
    }
  }

  override def getTransactionRequestStatusesImpl() :  Box[TransactionRequestStatus] = Empty
  /*
   Transaction Requests
 */

  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                            account : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                            status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = ???

  protected override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                                         transactionRequestType: TransactionRequestType,
                                                         account: BankAccount, toAccount: BankAccount, toCounterparty: CounterpartyTrait,
                                                         transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                                         details: String, status: String,
                                                         charge: TransactionRequestCharge,
                                                         chargePolicy: String): Box[TransactionRequest] = ???

  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId) = ???
  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) = ???
  override def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = ???
  override def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]] = ???
  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId) : Box[TransactionRequest] = ???
  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String) = ???



  private def createOtherBankAccount(originalPartyBankId: BankId, originalPartyAccountId: AccountId,
    otherAccount : CounterpartyMetadata, otherAccountFromTransaction : OBPAccount) : Counterparty = {
    new Counterparty(
      counterPartyId = otherAccount.metadataId,
      label = otherAccount.getHolder,
      nationalIdentifier = otherAccountFromTransaction.bank.get.national_identifier.get,
      otherBankRoutingAddress = None, 
      otherAccountRoutingAddress = Some(otherAccountFromTransaction.bank.get.IBAN.get),
      thisAccountId = AccountId(otherAccountFromTransaction.number.get),
      thisBankId = BankId(otherAccountFromTransaction.bank.get.name.get),
      kind = "",
      otherBankId = originalPartyBankId,
      otherAccountId = originalPartyAccountId,
      alreadyFoundMetadata = Some(otherAccount),
      name = "",
      otherBankRoutingScheme = "",
      otherAccountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true
    )
  }

  private def getHostedBank(bankId : BankId) : Box[HostedBank] = {
    HostedBank.find("permalink", bankId.value) ?~ {"bank " + bankId + " not found"}
  }

  //Need to pass in @hostedBank because the Account model doesn't have any references to BankId, just to the mongo id of the Bank object (which itself does have the bank id)
  private def createAccount(hostedBank : HostedBank, accountId : AccountId, accountNumber: String,
                            accountType: String, accountLabel: String, currency : String, initialBalance : BigDecimal, holderName : String) : BankAccount = {
    import net.liftweb.mongodb.BsonDSL._
    Account.find(
      (Account.accountNumber.name -> accountNumber)~
        (Account.bankID.name -> hostedBank.id.get)
    ) match {
      case Full(bankAccount) => {
        logger.debug(s"account with number ${bankAccount.accountNumber} at bank ${hostedBank.bankId} already exists. No need to create a new one.")
        bankAccount
      }
      case _ => {
        logger.debug("creating account record ")
        val bankAccount =
          Account
            .createRecord
            .accountBalance(initialBalance)
            .holder(holderName)
            .accountNumber(accountNumber)
            .kind(accountType)
            .accountLabel(accountLabel)
            .accountName("")
            .permalink(accountId.value)
            .bankID(hostedBank.id.get)
            .accountCurrency(currency)
            .accountIban("")
            .accountLastUpdate(now)
            .save(true)
        bankAccount
      }
    }
  }

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  override def createBankAndAccount(
    bankName: String,
    bankNationalIdentifier: String,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String
  ) = {

    // TODO: use a more unique id for the long term
    val hostedBank = {
      // TODO: use a more unique id for the long term
      HostedBank.find(HostedBank.national_identifier.name, bankNationalIdentifier) match {
        case Full(b)=> {
          logger.debug(s"bank ${b.name} found")
          b
        }
        case _ =>{
          //TODO: if name is empty use bank id as name alias

          //TODO: need to handle the case where generatePermalink returns a permalink that is already used for another bank

          logger.debug(s"creating HostedBank")
          HostedBank
            .createRecord
            .name(bankName)
            .alias(bankName)
            .permalink(Helper.generatePermalink(bankName))
            .national_identifier(bankNationalIdentifier)
            .save(true)
        }
      }
    }

    val createdAccount = createAccount(hostedBank, AccountId(UUID.randomUUID().toString),
      accountNumber, accountType, accountLabel, currency, BigDecimal("0.00"), accountHolderName)

    Full((hostedBank, createdAccount))
  }


  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String) = {
    import net.liftweb.mongodb.BsonDSL._

    getHostedBank(bankId).map(_.id.get) match {
      case Full(mongoId) =>
        Full(Account.count((Account.accountNumber.name -> accountNumber) ~ (Account.bankID.name -> mongoId)) > 0)
      case _ =>
        logger.warn("tried to check account existence for an account at a bank that doesn't exist")
        Full(false)
    }
  }

  override def removeAccount(bankId: BankId, accountId: AccountId) = {
    import net.liftweb.mongodb.BsonDSL._
    for {
        account <- Account.find((Account.bankID.name -> bankId.value) ~ (Account.accountId.value -> accountId.value)) ?~
          s"No account found with number ${accountId} at bank with id ${bankId}: could not save envelope"
      } yield {
        account.delete_!
      }

    Full(false)
/*          account
      } match {
        case Full(acc) => acc.
      }
      */
  }

  //creates a bank account for an existing bank, with the appropriate values set
  override def createSandboxBankAccount(
    bankId: BankId,
    accountId: AccountId,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    initialBalance: BigDecimal,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String
  ): Box[BankAccount] = {
    HostedBank.find(bankId) match {
      case Full(b) => Full(createAccount(b, accountId, accountNumber, accountType, accountLabel, currency, initialBalance, accountHolderName))
      case _ => Failure(s"Bank with id ${bankId.value} not found. Cannot create account at non-existing bank.")
    }

  }

  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String) = {

    val baseQuery = QueryBuilder.start("obp_transaction.details.value.amount")
      .is(amount)
      .put("obp_transaction.details.completed")
      .is(completed)
      .put("obp_transaction.this_account.bank.national_identifier")
      .is(bankNationalIdentifier)
      .put("obp_transaction.this_account.number")
      .is(accountNumber)

    //this is refactored legacy code, and it seems the empty account holder check had to do with potentially missing
    //fields in the db. not sure if this is still required.
    if(otherAccountHolder.isEmpty){
      def emptyHolderOrEmptyString(holder: Box[String]): Boolean = {
        holder match {
          case Full(s) => s.isEmpty
          case _ => true
        }
      }

      val partialMatches = OBPEnvelope.findAll(baseQuery.get())
  
      Full(partialMatches.filter(e => {
        emptyHolderOrEmptyString(e.obp_transaction.get.other_account.get.holder.valueBox)
      }).size)
    }
    else{
      val qry = baseQuery.put("obp_transaction.other_account.holder").is(otherAccountHolder).get

      val partialMatches = OBPEnvelope.count(qry)
      Full(partialMatches.toInt)//icky
    }
  }

  //used by transaction import api
  override def createImportedTransaction(transaction: ImporterTransaction): Box[Transaction] = {
    import net.liftweb.mongodb.BsonDSL._

    implicit val formats =  net.liftweb.json.DefaultFormats.lossless
    val asJValue = Extraction.decompose(transaction)

    for {
      env <- OBPEnvelope.envelopesFromJValue(asJValue)
      nationalIdentifier = transaction.obp_transaction.this_account.bank.national_identifier
      bank <- HostedBank.find(HostedBank.national_identifier.name -> nationalIdentifier) ?~
        s"No bank found with national identifier ${nationalIdentifier} could not save envelope"
      accountNumber = transaction.obp_transaction.this_account.number
      account <- Account.find((Account.bankID.name -> bank.id.get) ~ (Account.accountNumber.name -> accountNumber)) ?~
        s"No account found with number ${accountNumber} at bank with id ${bank.bankId}: could not save envelope"
      savedEnv <- env.saveTheRecord() ?~ "Could not save envelope"
    } yield {
      createTransaction(savedEnv, account)
    }
  }

  //used by the transaction import api
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal) = {
    getBankAccount(bankId, accountId) match {
      case Full(acc) =>
        acc.accountBalance(newBalance).saveTheRecord().isDefined
        Full(true)
      case _ =>
        Full(false)
    }
  }

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) = {
    Account.find(
      (Account.accountNumber.name -> accountNumber)~
        (Account.nationalIdentifier -> bankNationalIdentifier)
    ) match {
      case Full(acc) => Full(acc.accountLastUpdate(updateDate).saveTheRecord().isDefined)
      case _ => logger.warn("can't set bank account.lastUpdated because the account was not found"); Full(false)
    }
  }

  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String) = {
    getBankAccount(bankId, accountId) match {
      case Full(acc) =>
        acc.accountLabel(label).saveTheRecord().isDefined
        Full(true)
      case _ =>
        Full(false)
    }
  }

  override def getProducts(bankId: BankId): Box[List[Product]] = Empty

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = Empty

  override def createOrUpdateBranch(branch: Branch): Box[BranchT] = Empty

  override def getBranch(bankId: BankId, branchId: BranchId): Box[MappedBranch] = Empty

  override def getAtm(bankId: BankId, atmId: AtmId): Box[MappedAtm] = Empty // TODO Return Not Implemented
  
  override def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = Empty
  
  override def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge] = Empty

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId,viewId :ViewId): Box[List[CounterpartyTrait]] = Empty

  override def getEmptyBankAccount(): Box[AccountType] = Empty
  
  override def createOrUpdateBank(
    bankId: String,
    fullBankName: String,
    shortBankName: String,
    logoURL: String,
    websiteURL: String,
    swiftBIC: String,
    national_identifier: String,
    bankRoutingScheme: String,
    bankRoutingAddress: String
  ): Box[Bank] = Empty

}

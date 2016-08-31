//package code.bankconnectors
//
//import java.text.SimpleDateFormat
//import java.util.{Optional, Date, Locale, UUID}
//
//import code.api.util.ErrorMessages
//import code.management.ImporterAPI.ImporterTransaction
//import code.metadata.comments.MappedComment
//import code.metadata.counterparties.Counterparties
//import code.metadata.narrative.MappedNarrative
//import code.metadata.tags.MappedTag
//import code.metadata.transactionimages.MappedTransactionImage
//import code.metadata.wheretags.MappedWhereTag
//import code.model._
//import code.model.dataAccess._
//import code.sandbox.{CreateViewImpls, Saveable}
//import code.transaction.MappedTransaction
//import code.transactionrequests.{MappedTransactionRequest210, MappedTransactionRequest}
//import code.transactionrequests.TransactionRequests._
//import code.util.{Helper, TTLCache}
//import code.views.Views
//import com.tesobe.obp.kafka.SimpleNorth
//import com.tesobe.obp.transport.Transport
//import com.tesobe.obp.transport.Transport.Factory
//import net.liftweb.common._
//import net.liftweb.json
//import net.liftweb.mapper._
//import net.liftweb.util.Helpers._
//import net.liftweb.util.Props
//import net.liftweb.json._
//
//import scala.collection.JavaConversions._
//
//object KafkaLibMappedConnector extends Connector with CreateViewImpls with Loggable {
//  type JBank = com.tesobe.obp.transport.Bank
//  type JTransaction = com.tesobe.obp.transport.Transaction
//  type JAccount = com.tesobe.obp.transport.Account
//  type OutboundContext = com.tesobe.obp.transport.OutboundContext
//
//  val version = getVersion()
//
//  val factory : Factory = Transport.factory(version, Transport.Encoding.json).get
//  //todo get topic names from the props
//  val north: SimpleNorth = new SimpleNorth("Request", "Response") // Kafka
//  val connector : com.tesobe.obp.transport.Connector = factory.connector(north)
//
//  north.receive() // start Kafka
//
//  var producer = new KafkaProducer()
//  var consumer = new KafkaConsumer()
//  type AccountType = KafkaBankAccount
//
//  // Local TTL Cache
//  val cacheTTL              = Props.get("kafka.cache.ttl.seconds", "3").toInt
//  val cachedUser            = TTLCache[KafkaInboundValidatedUser](cacheTTL)
//  val cachedBank            = TTLCache[KafkaInboundBank](cacheTTL)
//  val cachedAccount         = TTLCache[KafkaInboundAccount](cacheTTL)
//  val cachedBanks           = TTLCache[List[KafkaInboundBank]](cacheTTL)
//  val cachedAccounts        = TTLCache[List[KafkaInboundAccount]](cacheTTL)
//  val cachedPublicAccounts  = TTLCache[List[KafkaInboundAccount]](cacheTTL)
//  val cachedUserAccounts    = TTLCache[List[KafkaInboundAccount]](cacheTTL)
//
//  implicit val formats = net.liftweb.json.DefaultFormats
//
//  def getVersion(): Transport.Version = {
//    val versionProp = Props.get("connector").openOrThrowException("no connector set")
//    val version = versionProp.split("_").last
//
//    version.toLowerCase match {
//      case "v0" => Transport.Version.v0
//      case _ => Transport.Version.v0
//    }
//  }
//
//  //gets user handled by this connector
//  def getUser( username: String, password: String ): Box[KafkaInboundUser] = {
//    val user = connector.getUser(username, new OutboundContext(null, null, null)).user
//
//    if (user.isPresent) {
//      val u = user.get
//      Full(new KafkaInboundUser(u.email(), u.password(), u.email()))
//    } else {
//      Empty
//    }
//  }
//
//  //gets banks handled by this connector
//  override def getBanks: List[Bank] = {
//    val banks = connector.getBanks(new OutboundContext(null, null, null)).banks
//
//    //Loop through list of responses and create entry for each
//    val res = {
//      for (r <- banks) yield {
//        KafkaBank(KafkaInboundBank(r.id, r.shortName, r.fullName, r.logo, r.url))
//      }
//    }
//    // Return list of results
//
//    logger.debug(s"Kafka getBanks says res is $res")
//    res.toList
//  }
//
//  // Gets bank identified by bankId
//  override def getBank(id: BankId): Box[Bank] = {
//    val bank = connector.getBank(id.value, new OutboundContext(null, null, null)).bank
//
//    if(bank.isPresent) {
//      val b = bank.get
//      Full(KafkaBank(KafkaInboundBank(b.id, b.shortName, b.fullName, b.logo, b.url)))
//    } else {
//      Empty
//    }
//  }
//
//  // Gets transaction identified by bankid, accountid and transactionId
//  def getTransaction(bankId: BankId, accountID: AccountId, transactionId: TransactionId): Box[Transaction] = {
//    val transaction = connector.getTransaction(bankId.value, accountID.value, transactionId.value, new OutboundContext(null, null, null)).transaction
//
//    if(transaction.isPresent) {
//      val t = transaction.get
//      val kafkaInboundTransaction = new KafkaInboundTransaction(t.id(),
//        new KafkaInboundAccountId(t.account_number(), t.bank()),
//        Some(new KafkaInboundTransactionCounterparty(Some(t.name()), Some(t.account_number()))),
//        new KafkaInboundTransactionDetails(t.`type`(), t.description(), t.posted().toString, t.completed().toString, t.new_balance(), t.value())
//      )
//
//      if (transactionId.value == t.id()){
//        createNewTransaction(kafkaInboundTransaction)
//      } else {
//        Failure(ErrorMessages.InvalidGetTransactionConnectorResponse, Empty, Empty)
//      }
//    } else {
//      Empty
//    }
//  }
//
//  // Gets transaction identified by bankid and accountid
//  override def getTransactions(bankId: BankId, accountID: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
//    val transactions = connector.getTransactions(bankId.value, accountID.value, new OutboundContext(null, null, null)).transactions
//
//    // Check does the response data match the requested data
//    val isCorrect = transactions.forall(x => x.account_number() == accountID.value && x.bank() == bankId.value)
//    if (!isCorrect) throw new Exception(ErrorMessages.InvalidGetTransactionsConnectorResponse)
//    // Populate fields and generate result
//    val res = for {
//      t <- transactions
//      kafkaInboundTransaction = new KafkaInboundTransaction(t.id(),
//        new KafkaInboundAccountId(t.account_number(), t.bank()),
//        Some(new KafkaInboundTransactionCounterparty(Some(t.name()), Some(t.account_number()))),
//        new KafkaInboundTransactionDetails(t.`type`(), t.description(), t.posted().toString, t.completed().toString, t.new_balance(), t.value())
//      )
//      transaction <- createNewTransaction(kafkaInboundTransaction)
//    } yield {
//      transaction
//    }
//    Full(res.toList)
//  }
//
//  // Gets account identified by bankid and accountid
//  override def getBankAccount(bankId: BankId, accountID: AccountId): Box[KafkaBankAccount] = {
//    val account = connector.getAccount(bankId.value, accountID.value, new OutboundContext(null, null, null)).account
//
//    if (account.isPresent) {
//      val a = account.get
//      // Check does the response data match the requested data
//      val accResp = List((BankId(a.bank()), AccountId(a.id()))).toSet
//      val acc = List((bankId, accountID)).toSet
//      if ((accResp diff acc).size > 0) throw new Exception(ErrorMessages.InvalidGetBankAccountConnectorResponse)
//
//      createMappedAccountDataIfNotExisting(a.bank(), a.id(), a.label())
//
//      Full(new KafkaBankAccount(new KafkaInboundAccount(a.id(), a.bank(), a.label(), a.number(), a.`type`(),
//        new KafkaInboundBalance(a.currency(), a.amount()), a.iban(), Nil, false, false, false))) //TODO figure out the generate parameters
//    } else {
//      Empty
//    }
//  }
//
//  // Gets accounts identified by bankid and accountid
//  override def getBankAccounts(accts: List[(BankId, AccountId)]): List[KafkaBankAccount] = {
//    var accounts: List[JAccount] = Nil
//
//    for (ba <- accts){
//      val account = connector.getAccount(ba._1.value, ba._2.value, new OutboundContext(null, null, null)).account
//      if (account.isPresent){
//        accounts = accounts ::: List[JAccount](account.get)
//      }
//    }
//
//    // Check does the response data match the requested data
//    val accRes = for(row <- accounts) yield {
//      (BankId(row.bank), AccountId(row.id))
//    }
//    if ((accRes.toSet diff accts.toSet).size > 0) throw new Exception(ErrorMessages.InvalidGetBankAccountsConnectorResponse)
//
//    accounts.map { a =>
//      createMappedAccountDataIfNotExisting(a.bank, a.id, a.label)
//      new KafkaBankAccount(new KafkaInboundAccount(a.id(), a.bank(), a.label(), a.number(), a.`type`(),
//        new KafkaInboundBalance(a.currency(), a.amount()), a.iban(), Nil, false, false, false)) }
//  }
//
//  private def getAccountByNumber(bankId : BankId, number : String) : Box[AccountType] = {
//    // Generate random uuid to be used as request-respose match id
//    val reqId: String = UUID.randomUUID().toString
//    // Create argument list with reqId
//    // in order to fetch corresponding response
//    val argList = Map("bankId" -> bankId.toString,
//      "username" -> OBPUser.getCurrentUserUsername,
//      "number" -> number)
//    // Since result is single account, we need only first list entry
//    implicit val formats = net.liftweb.json.DefaultFormats
//    val r = {
//      cachedAccount.getOrElseUpdate( argList.toString, () => process(reqId, "getBankAccount", argList).extract[KafkaInboundAccount])
//    }
//    createMappedAccountDataIfNotExisting(r.bank, r.id, r.label)
//    Full(new KafkaBankAccount(r))
//  }
//
//  def getOtherBankAccount(thisAccountBankId : BankId, thisAccountId : AccountId, metadata : OtherBankAccountMetadata) : Box[OtherBankAccount] = {
//    //because we don't have a db backed model for OtherBankAccounts, we need to construct it from an
//    //OtherBankAccountMetadata and a transaction
//    val t = getTransactions(thisAccountBankId, thisAccountId).map { t =>
//      t.filter { e =>
//        if (e.otherAccount.number == metadata.getAccountNumber)
//          true
//        else
//          false
//      }
//    }.get.head
//
//    val res = new OtherBankAccount(
//      //counterparty id is defined to be the id of its metadata as we don't actually have an id for the counterparty itself
//      id = metadata.metadataId,
//      label = metadata.getHolder,
//      nationalIdentifier = t.otherAccount.nationalIdentifier,
//      swift_bic = None,
//      iban = t.otherAccount.iban,
//      number = metadata.getAccountNumber,
//      bankName = t.otherAccount.bankName,
//      kind = t.otherAccount.kind,
//      originalPartyBankId = thisAccountBankId,
//      originalPartyAccountId = thisAccountId,
//      alreadyFoundMetadata = Some(metadata)
//    )
//    Full(res)
//  }
//
//  /**
//    *
//    * refreshes transactions via hbci if the transaction info is sourced from hbci
//    *
//    *  Checks if the last update of the account was made more than one hour ago.
//    *  if it is the case we put a message in the message queue to ask for
//    *  transactions updates
//    *
//    *  It will be used each time we fetch transactions from the DB. But the test
//    *  is performed in a different thread.
//    */
//  /*
//  private def updateAccountTransactions(bankId : BankId, accountID : AccountId) = {
//
//    for {
//      bank <- getBank(bankId)
//      account <- getBankAccountType(bankId, accountID)
//    } {
//      spawn{
//        val useMessageQueue = Props.getBool("messageQueue.updateBankAccountsTransaction", false)
//        val outDatedTransactions = Box!!account.lastUpdate match {
//          case Full(l) => now after time(l.getTime + hours(Props.getInt("messageQueue.updateTransactionsInterval", 1)))
//          case _ => true
//        }
//        //if(outDatedTransactions && useMessageQueue) {
//        //  UpdatesRequestSender.sendMsg(UpdateBankAccount(account.number, bank.national_identifier.get))
//        //}
//      }
//    }
//  }
//  */
//
//  //gets the users who are the legal owners/holders of the account
//  override def getAccountHolders(bankId: BankId, accountID: AccountId): Set[User] =
//    MappedAccountHolder.findAll(
//      By(MappedAccountHolder.accountBankPermalink, bankId.value),
//      By(MappedAccountHolder.accountPermalink, accountID.value)).map(accHolder => accHolder.user.obj).flatten.toSet
//
//
//  // Get all counterparties related to an account
//  override def getOtherBankAccounts(bankId: BankId, accountID: AccountId): List[OtherBankAccount] =
//    Counterparties.counterparties.vend.getMetadatas(bankId, accountID).flatMap(getOtherBankAccount(bankId, accountID, _))
//
//  // Get one counterparty related to a bank account
//  override def getOtherBankAccount(bankId: BankId, accountID: AccountId, otherAccountID: String): Box[OtherBankAccount] =
//  // Get the metadata and pass it to getOtherBankAccount to construct the other account.
//    Counterparties.counterparties.vend.getMetadata(bankId, accountID, otherAccountID).flatMap(getOtherBankAccount(bankId, accountID, _))
//
//  override def getPhysicalCards(user: User): Set[PhysicalCard] =
//    Set.empty
//
//  override def getPhysicalCardsForBank(bankId: BankId, user: User): Set[PhysicalCard] =
//    Set.empty
//
//
//  override def makePaymentImpl(fromAccount: AccountType, toAccount: AccountType, amt: BigDecimal, description : String): Box[TransactionId] = {
//    val fromTransAmt = -amt //from account balance should decrease
//    val toTransAmt = amt //to account balance should increase
//
//    //we need to save a copy of this payment as a transaction in each of the accounts involved, with opposite amounts
//    val sentTransactionId = saveTransaction(fromAccount, toAccount, fromTransAmt, description)
//    saveTransaction(toAccount, fromAccount, toTransAmt, description)
//
//    sentTransactionId
//  }
//
//
//  /**
//    * Saves a transaction with amount @amt and counterparty @counterparty for account @account. Returns the id
//    * of the saved transaction.
//    */
//  private def saveTransaction(account : AccountType,
//                              counterparty : BankAccount,
//                              amt : BigDecimal,
//                              description : String) : Box[TransactionId] = {
//
//    val transactionTime = now
//    val currency = account.currency
//
//    //update the balance of the account for which a transaction is being created
//    val newAccountBalance : Long = account.balance.toLong + Helper.convertToSmallestCurrencyUnits(amt, account.currency)
//    //account.balance = newAccountBalance
//
//    val reqId: String = UUID.randomUUID().toString
//    // Create argument list with reqId
//    // in order to fetch corresponding response
//    val argObj = KafkaOutboundTransaction(username = OBPUser.getCurrentUserUsername,
//      accountId = account.accountId.value,
//      currency = currency,
//      amount = amt.toString,
//      otherAccountId = counterparty.accountId.value,
//      otherAccountCurrency = counterparty.currency,
//      transactionType = "AC")
//
//    // Since result is single account, we need only first list entry
//    implicit val formats = net.liftweb.json.DefaultFormats
//    val argMap = Extraction.decompose(argObj).values
//    val r = process(reqId, "saveTransaction", argMap.asInstanceOf[Map[String,String]]) //.extract[KafkaInboundTransactionId]
//
//    r.extract[KafkaInboundTransactionId] match {
//      case r: KafkaInboundTransactionId => Full(TransactionId(r.transactionId))
//      case _ => Full(TransactionId("0"))
//    }
//
//  }
//
//  /*
//    Transaction Requests
//  */
//
//  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
//                                            account : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
//                                            status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
//    val mappedTransactionRequest = MappedTransactionRequest.create
//      .mTransactionRequestId(transactionRequestId.value)
//      .mType(transactionRequestType.value)
//      .mFrom_BankId(account.bankId.value)
//      .mFrom_AccountId(account.accountId.value)
//      .mBody_To_BankId(counterparty.bankId.value)
//      .mBody_To_AccountId(counterparty.accountId.value)
//      .mBody_Value_Currency(body.value.currency)
//      .mBody_Value_Amount(body.value.amount)
//      .mBody_Description(body.description)
//      .mStatus(status)
//      .mStartDate(now)
//      .mEndDate(now).saveMe
//    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
//  }
//
//  override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
//                                               account : BankAccount, details: String,
//                                               status: String, charge: TransactionRequestCharge) : Box[TransactionRequest210] = {
//    val mappedTransactionRequest = MappedTransactionRequest210.create
//      .mTransactionRequestId(transactionRequestId.value)
//      .mType(transactionRequestType.value)
//      .mFrom_BankId(account.bankId.value)
//      .mFrom_AccountId(account.accountId.value)
//      .mDetails(details)
//      .mStatus(status)
//      .mStartDate(now)
//      .mEndDate(now).saveMe
//    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest210)
//  }
//
//  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = {
//    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
//    mappedTransactionRequest match {
//      case Full(tr: MappedTransactionRequest) => Full(tr.mTransactionIDs(transactionId.value).save)
//      case _ => Failure("Couldn't find transaction request ${transactionRequestId}")
//    }
//  }
//
//  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = {
//    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
//    mappedTransactionRequest match {
//      case Full(tr: MappedTransactionRequest) => Full{
//        tr.mChallenge_Id(challenge.id)
//        tr.mChallenge_AllowedAttempts(challenge.allowed_attempts)
//        tr.mChallenge_ChallengeType(challenge.challenge_type).save
//      }
//      case _ => Failure(s"Couldn't find transaction request ${transactionRequestId} to set transactionId")
//    }
//  }
//
//  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = {
//    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
//    mappedTransactionRequest match {
//      case Full(tr: MappedTransactionRequest) => Full(tr.mStatus(status).save)
//      case _ => Failure(s"Couldn't find transaction request ${transactionRequestId} to set status")
//    }
//  }
//
//
//  override def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
//    val transactionRequests = MappedTransactionRequest.findAll(By(MappedTransactionRequest.mFrom_AccountId, fromAccount.accountId.value),
//      By(MappedTransactionRequest.mFrom_BankId, fromAccount.bankId.value))
//
//    Full(transactionRequests.flatMap(_.toTransactionRequest))
//  }
//
//  override def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest210]] = {
//    val transactionRequests = MappedTransactionRequest210.findAll(By(MappedTransactionRequest210.mFrom_AccountId, fromAccount.accountId.value),
//      By(MappedTransactionRequest210.mFrom_BankId, fromAccount.bankId.value))
//
//    Full(transactionRequests.flatMap(_.toTransactionRequest210))
//  }
//
//  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId) : Box[TransactionRequest] = {
//    val transactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
//    transactionRequest.flatMap(_.toTransactionRequest)
//  }
//
//
//  override def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
//    //TODO: write logic / data access
//    Full(List(TransactionRequestType("SANDBOX_TAN")))
//  }
//
//  /*
//    Bank account creation
//   */
//
//  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
//  //again assume national identifier is unique
//  override def createBankAndAccount(bankName: String, bankNationalIdentifier: String, accountNumber: String,
//                                    accountType: String, accountLabel: String,  currency: String, accountHolderName: String): (Bank, BankAccount) = {
//    //don't require and exact match on the name, just the identifier
//    val bank: Bank = MappedBank.find(By(MappedBank.national_identifier, bankNationalIdentifier)) match {
//      case Full(b) =>
//        logger.info(s"bank with id ${b.bankId} and national identifier ${b.nationalIdentifier} found")
//        b
//      case _ =>
//        logger.info(s"creating bank with national identifier $bankNationalIdentifier")
//        //TODO: need to handle the case where generatePermalink returns a permalink that is already used for another bank
//        MappedBank.create
//          .permalink(Helper.generatePermalink(bankName))
//          .fullBankName(bankName)
//          .shortBankName(bankName)
//          .national_identifier(bankNationalIdentifier)
//          .saveMe()
//    }
//
//    //TODO: pass in currency as a parameter?
//    val account = createAccountIfNotExisting(
//      bank.bankId,
//      AccountId(UUID.randomUUID().toString),
//      accountNumber,
//      accountType,
//      accountLabel,
//      currency,
//      0L,
//      accountHolderName
//    )
//
//    (bank, account)
//  }
//
//  //for sandbox use -> allows us to check if we can generate a new test account with the given number
//  override def accountExists(bankId: BankId, accountNumber: String): Boolean = {
//    getAccountByNumber(bankId, accountNumber) != null
//  }
//
//  //remove an account and associated transactions
//  override def removeAccount(bankId: BankId, accountID: AccountId) : Boolean = {
//    //delete comments on transactions of this account
//    val commentsDeleted = MappedComment.bulkDelete_!!(
//      By(MappedComment.bank, bankId.value),
//      By(MappedComment.account, accountID.value)
//    )
//
//    //delete narratives on transactions of this account
//    val narrativesDeleted = MappedNarrative.bulkDelete_!!(
//      By(MappedNarrative.bank, bankId.value),
//      By(MappedNarrative.account, accountID.value)
//    )
//
//    //delete narratives on transactions of this account
//    val tagsDeleted = MappedTag.bulkDelete_!!(
//      By(MappedTag.bank, bankId.value),
//      By(MappedTag.account, accountID.value)
//    )
//
//    //delete WhereTags on transactions of this account
//    val whereTagsDeleted = MappedWhereTag.bulkDelete_!!(
//      By(MappedWhereTag.bank, bankId.value),
//      By(MappedWhereTag.account, accountID.value)
//    )
//
//    //delete transaction images on transactions of this account
//    val transactionImagesDeleted = MappedTransactionImage.bulkDelete_!!(
//      By(MappedTransactionImage.bank, bankId.value),
//      By(MappedTransactionImage.account, accountID.value)
//    )
//
//    //delete transactions of account
//    val transactionsDeleted = MappedTransaction.bulkDelete_!!(
//      By(MappedTransaction.bank, bankId.value),
//      By(MappedTransaction.account, accountID.value)
//    )
//
//    //remove view privileges (get views first)
//    val views = ViewImpl.findAll(
//      By(ViewImpl.bankPermalink, bankId.value),
//      By(ViewImpl.accountPermalink, accountID.value)
//    )
//
//    //loop over them and delete
//    var privilegesDeleted = true
//    views.map (x => {
//      privilegesDeleted &&= ViewPrivileges.bulkDelete_!!(By(ViewPrivileges.view, x.id_))
//    })
//
//    //delete views of account
//    val viewsDeleted = ViewImpl.bulkDelete_!!(
//      By(ViewImpl.bankPermalink, bankId.value),
//      By(ViewImpl.accountPermalink, accountID.value)
//    )
//
//    //delete account
//    val account = getBankAccount(bankId, accountID)
//
//    val accountDeleted = account match {
//      case acc => true //acc.delete_! //TODO
//      case _ => false
//    }
//
//    commentsDeleted && narrativesDeleted && tagsDeleted && whereTagsDeleted && transactionImagesDeleted &&
//      transactionsDeleted && privilegesDeleted && viewsDeleted && accountDeleted
//  }
//
//  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
//  override def createSandboxBankAccount(bankId: BankId, accountID: AccountId, accountNumber: String,
//                                        accountType: String, accountLabel: String, currency: String,
//                                        initialBalance: BigDecimal, accountHolderName: String): Box[BankAccount] = {
//
//    for {
//      bank <- getBank(bankId) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
//    } yield {
//
//      val balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
//      createAccountIfNotExisting(bankId, accountID, accountNumber, accountType, accountLabel, currency, balanceInSmallestCurrencyUnits, accountHolderName)
//    }
//
//  }
//
//  //sets a user as an account owner/holder
//  override def setAccountHolder(bankAccountUID: BankAccountUID, user: User): Unit = {
//    MappedAccountHolder.createMappedAccountHolder(user.apiId.value, bankAccountUID.accountId.value, bankAccountUID.bankId.value)
//  }
//
//  private def createAccountIfNotExisting(bankId: BankId, accountID: AccountId, accountNumber: String,
//                                         accountType: String, accountLabel: String, currency: String,
//                                         balanceInSmallestCurrencyUnits: Long, accountHolderName: String) : BankAccount = {
//    getBankAccount(bankId, accountID) match {
//      case Full(a) =>
//        logger.info(s"account with id $accountID at bank with id $bankId already exists. No need to create a new one.")
//        a
//      case _ => null //TODO
//      /*
//     new  KafkaBankAccount
//        .bank(bankId.value)
//        .theAccountId(accountID.value)
//        .accountNumber(accountNumber)
//        .accountType(accountType)
//        .accountLabel(accountLabel)
//        .accountCurrency(currency)
//        .accountBalance(balanceInSmallestCurrencyUnits)
//        .holder(accountHolderName)
//        .saveMe()
//        */
//    }
//  }
//
//  private def createMappedAccountDataIfNotExisting(bankId: String, accountId: String, label: String) : Boolean = {
//    MappedKafkaBankAccountData.find(By(MappedKafkaBankAccountData.accountId, accountId),
//      By(MappedKafkaBankAccountData.bankId, bankId)) match {
//      case Empty =>
//        val data = new MappedKafkaBankAccountData
//        data.setAccountId(accountId)
//        data.setBankId(bankId)
//        data.setLabel(label)
//        data.save()
//        true
//      case _ =>
//        logger.info(s"account data with id $accountId at bank with id $bankId already exists. No need to create a new one.")
//        false
//    }
//  }
//
//  /*
//    End of bank account creation
//   */
//
//
//  /*
//    Transaction importer api
//   */
//
//  //used by the transaction import api
//  override def updateAccountBalance(bankId: BankId, accountID: AccountId, newBalance: BigDecimal): Boolean = {
//
//    //this will be Full(true) if everything went well
//    val result = for {
//      acc <- getBankAccount(bankId, accountID)
//      bank <- getBank(bankId)
//    } yield {
//      //acc.balance = newBalance
//      setBankAccountLastUpdated(bank.nationalIdentifier, acc.number, now)
//    }
//
//    result.getOrElse(false)
//  }
//
//  //transaction import api uses bank national identifiers to uniquely indentify banks,
//  //which is unfortunate as theoretically the national identifier is unique to a bank within
//  //one country
//  private def getBankByNationalIdentifier(nationalIdentifier : String) : Box[Bank] = {
//    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
//  }
//
//
//  private val bigDecimalFailureHandler : PartialFunction[Throwable, Unit] = {
//    case ex : NumberFormatException => {
//      logger.warn(s"could not convert amount to a BigDecimal: $ex")
//    }
//  }
//
//  //used by transaction import api call to check for duplicates
//  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String): Int = {
//    //we need to convert from the legacy bankNationalIdentifier to BankId, and from the legacy accountNumber to AccountId
//    val count = for {
//      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
//      account <- getAccountByNumber(bankId, accountNumber)
//      amountAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(amount))
//    } yield {
//
//      val amountInSmallestCurrencyUnits =
//        Helper.convertToSmallestCurrencyUnits(amountAsBigDecimal, account.currency)
//
//      MappedTransaction.count(
//        By(MappedTransaction.bank, bankId.value),
//        By(MappedTransaction.account, account.accountId.value),
//        By(MappedTransaction.amount, amountInSmallestCurrencyUnits),
//        By(MappedTransaction.tFinishDate, completed),
//        By(MappedTransaction.counterpartyAccountHolder, otherAccountHolder))
//    }
//
//    //icky
//    count.map(_.toInt) getOrElse 0
//  }
//
//  //used by transaction import api
//  override def createImportedTransaction(transaction: ImporterTransaction): Box[Transaction] = {
//    //we need to convert from the legacy bankNationalIdentifier to BankId, and from the legacy accountNumber to AccountId
//    val obpTransaction = transaction.obp_transaction
//    val thisAccount = obpTransaction.this_account
//    val nationalIdentifier = thisAccount.bank.national_identifier
//    val accountNumber = thisAccount.number
//    for {
//      bank <- getBankByNationalIdentifier(transaction.obp_transaction.this_account.bank.national_identifier) ?~!
//        s"No bank found with national identifier $nationalIdentifier"
//      bankId = bank.bankId
//      account <- getAccountByNumber(bankId, accountNumber)
//      details = obpTransaction.details
//      amountAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(details.value.amount))
//      newBalanceAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(details.new_balance.amount))
//      amountInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(amountAsBigDecimal, account.currency)
//      newBalanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(newBalanceAsBigDecimal, account.currency)
//      otherAccount = obpTransaction.other_account
//      mappedTransaction = MappedTransaction.create
//        .bank(bankId.value)
//        .account(account.accountId.value)
//        .transactionType(details.kind)
//        .amount(amountInSmallestCurrencyUnits)
//        .newAccountBalance(newBalanceInSmallestCurrencyUnits)
//        .currency(account.currency)
//        .tStartDate(details.posted.`$dt`)
//        .tFinishDate(details.completed.`$dt`)
//        .description(details.label)
//        .counterpartyAccountNumber(otherAccount.number)
//        .counterpartyAccountHolder(otherAccount.holder)
//        .counterpartyAccountKind(otherAccount.kind)
//        .counterpartyNationalId(otherAccount.bank.national_identifier)
//        .counterpartyBankName(otherAccount.bank.name)
//        .counterpartyIban(otherAccount.bank.IBAN)
//        .saveMe()
//      transaction <- mappedTransaction.toTransaction(account)
//    } yield transaction
//  }
//
//  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Boolean = {
//    val result = for {
//      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
//      account <- getAccountByNumber(bankId, accountNumber)
//    } yield {
//      val acc = getBankAccount(bankId, account.accountId)
//      acc match {
//        case a => true //a.lastUpdate = updateDate //TODO
//        case _ => logger.warn("can't set bank account.lastUpdated because the account was not found"); false
//      }
//    }
//    result.getOrElse(false)
//  }
//
//  /*
//    End of transaction importer api
//   */
//
//
//  override def updateAccountLabel(bankId: BankId, accountID: AccountId, label: String): Boolean = {
//    //this will be Full(true) if everything went well
//    val result = for {
//      acc <- getBankAccount(bankId, accountID)
//      bank <- getBank(bankId)
//      d <- MappedKafkaBankAccountData.find(By(MappedKafkaBankAccountData.accountId, accountID.value), By(MappedKafkaBankAccountData.bankId, bank.bankId.value))
//    } yield {
//      d.setLabel(label)
//      d.save()
//    }
//    result.getOrElse(false)
//  }
//
//
//
//
//  /////////////////////////////////////////////////////////////////////////////
//
//
//
//  def process(reqId: String, command: String, argList: Map[String,String]): json.JValue = { //List[Map[String,String]] = {
//  var retries:Int = 3
//    while (consumer == null && retries > 0 ) {
//      retries -= 1
//      consumer = new KafkaConsumer()
//    }
//    retries = 3
//    while (producer == null && retries > 0) {
//      retries -= 1
//      producer = new KafkaProducer()
//    }
//    if (producer == null || consumer == null)
//      return json.parse("""{"error":"connection failed. try again later."}""")
//    // Send request to Kafka
//    producer.send(reqId, command, argList, "1")
//    // Request sent, now we wait for response with the same reqId
//    val res = consumer.getResponse(reqId)
//    res
//  }
//
//
//  // Helper for creating a transaction
//  def createNewTransaction(r: KafkaInboundTransaction):Box[Transaction] = {
//    var datePosted: Date = null
//    if (r.details.posted != null) // && r.details.posted.matches("^[0-9]{8}$"))
//      datePosted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(r.details.posted)
//
//    var dateCompleted: Date = null
//    if (r.details.completed != null) // && r.details.completed.matches("^[0-9]{8}$"))
//      dateCompleted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(r.details.completed)
//
//    for {
//      counterparty <- tryo{r.counterparty}
//      thisAccount <- getBankAccount(BankId(r.this_account.bank), AccountId(r.this_account.id))
//      //creates a dummy OtherBankAccount without an OtherBankAccountMetadata, which results in one being generated (in OtherBankAccount init)
//      dummyOtherBankAccount <- tryo{createOtherBankAccount(counterparty.get, thisAccount, None)}
//      //and create the proper OtherBankAccount with the correct "id" attribute set to the metadataId of the OtherBankAccountMetadata object
//      //note: as we are passing in the OtherBankAccountMetadata we don't incur another db call to get it in OtherBankAccount init
//      otherAccount <- tryo{createOtherBankAccount(counterparty.get, thisAccount, Some(dummyOtherBankAccount.metadata))}
//    } yield {
//      // Create new transaction
//      new Transaction(
//        r.id,                             // uuid:String
//        TransactionId(r.id),              // id:TransactionId
//        thisAccount,                      // thisAccount:BankAccount
//        otherAccount,                     // otherAccount:OtherBankAccount
//        r.details.`type`,                 // transactionType:String
//        BigDecimal(r.details.value),      // val amount:BigDecimal
//        thisAccount.currency,             // currency:String
//        Some(r.details.description),      // description:Option[String]
//        datePosted,                       // startDate:Date
//        dateCompleted,                    // finishDate:Date
//        BigDecimal(r.details.new_balance) // balance:BigDecimal)
//      )
//    }
//  }
//
//
//  case class KafkaBank(r: KafkaInboundBank) extends Bank {
//    def fullName           = r.full_name
//    def shortName          = r.short_name
//    def logoUrl            = r.logo
//    def bankId             = BankId(r.id)
//    def nationalIdentifier = "None"  //TODO
//    def swiftBic           = "None"  //TODO
//    def websiteUrl         = r.website
//  }
//
//  // Helper for creating other bank account
//  def createOtherBankAccount(c: KafkaInboundTransactionCounterparty, o: KafkaBankAccount, alreadyFoundMetadata : Option[OtherBankAccountMetadata]) = {
//    new OtherBankAccount(
//      id = alreadyFoundMetadata.map(_.metadataId).getOrElse(""),
//      label = c.account_number.getOrElse(c.name.getOrElse("")),
//      nationalIdentifier = "",
//      swift_bic = None,
//      iban = None,
//      number = c.account_number.getOrElse(""),
//      bankName = "",
//      kind = "",
//      originalPartyBankId = BankId(o.bankId.value),
//      originalPartyAccountId = AccountId(o.accountId.value),
//      alreadyFoundMetadata = alreadyFoundMetadata
//    )
//  }
//
//  case class KafkaBankAccount(r: KafkaInboundAccount) extends BankAccount {
//    def accountId : AccountId       = AccountId(r.id)
//    def accountType : String        = r.`type`
//    def balance : BigDecimal        = BigDecimal(r.balance.amount)
//    def currency : String           = r.balance.currency
//    def name : String               = r.owners.head
//    def swift_bic : Option[String]  = Some("swift_bic") //TODO
//    def iban : Option[String]       = Some(r.IBAN)
//    def number : String             = r.number
//    def bankId : BankId             = BankId(r.bank)
//    def lastUpdate : Date           = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(today.getTime.toString)
//    def accountHolder : String      = r.owners.head
//
//    // Fields modifiable from OBP are stored in mapper
//    def label : String              = (for {
//      d <- MappedKafkaBankAccountData.find(By(MappedKafkaBankAccountData.accountId, r.id))
//    } yield {
//      d.getLabel
//    }).getOrElse(r.number)
//
//  }
//
//
//  case class KafkaInboundBank(
//                               id : String,
//                               short_name : String,
//                               full_name : String,
//                               logo : String,
//                               website : String)
//
//
//  /** Bank Branches
//    *
//    * @param id Uniquely identifies the Branch within the Bank. SHOULD be url friendly (no spaces etc.) Used in URLs
//    * @param bank_id MUST match bank_id in Banks
//    * @param name Informal name for the Branch
//    * @param address Address
//    * @param location Geolocation
//    * @param meta Meta information including the license this information is published under
//    * @param lobby Info about when the lobby doors are open
//    * @param driveUp Info about when automated facilities are open e.g. cash point machine
//    */
//  case class KafkaInboundBranch(
//                                 id : String,
//                                 bank_id: String,
//                                 name : String,
//                                 address : KafkaInboundAddress,
//                                 location : KafkaInboundLocation,
//                                 meta : KafkaInboundMeta,
//                                 lobby : Option[KafkaInboundLobby],
//                                 driveUp : Option[KafkaInboundDriveUp])
//
//  case class KafkaInboundLicense(
//                                  id : String,
//                                  name : String)
//
//  case class KafkaInboundMeta(
//                               license : KafkaInboundLicense)
//
//  case class KafkaInboundLobby(
//                                hours : String)
//
//  case class KafkaInboundDriveUp(
//                                  hours : String)
//
//  /**
//    *
//    * @param line_1 Line 1 of Address
//    * @param line_2 Line 2 of Address
//    * @param line_3 Line 3 of Address
//    * @param city City
//    * @param county County i.e. Division of State
//    * @param state State i.e. Division of Country
//    * @param post_code Post Code or Zip Code
//    * @param country_code 2 letter country code: ISO 3166-1 alpha-2
//    */
//  case class KafkaInboundAddress(
//                                  line_1 : String,
//                                  line_2 : String,
//                                  line_3 : String,
//                                  city : String,
//                                  county : String, // Division of State
//                                  state : String, // Division of Country
//                                  post_code : String,
//                                  country_code: String)
//
//  case class KafkaInboundLocation(
//                                   latitude : Double,
//                                   longitude : Double)
//
//  case class KafkaInboundUser(
//                               email : String,
//                               password : String,
//                               display_name : String)
//
//  case class KafkaInboundValidatedUser(
//                                        email : String,
//                                        display_name : String)
//
//  case class KafkaInboundAccount(
//                                  id : String,
//                                  bank : String,
//                                  label : String,
//                                  number : String,
//                                  `type` : String,
//                                  balance : KafkaInboundBalance,
//                                  IBAN : String,
//                                  owners : List[String],
//                                  generate_public_view : Boolean,
//                                  generate_accountants_view : Boolean,
//                                  generate_auditors_view : Boolean)
//
//  case class KafkaInboundBalance(
//                                  currency : String,
//                                  amount : String)
//
//  case class KafkaInboundTransaction(
//                                      id : String,
//                                      this_account : KafkaInboundAccountId,
//                                      counterparty : Option[KafkaInboundTransactionCounterparty],
//                                      details : KafkaInboundTransactionDetails)
//
//  case class KafkaInboundTransactionCounterparty(
//                                                  name : Option[String],  // Also known as Label
//                                                  account_number : Option[String])
//
//  case class KafkaInboundAccountId(
//                                    id : String,
//                                    bank : String)
//
//  case class KafkaInboundTransactionDetails(
//                                             `type` : String,
//                                             description : String,
//                                             posted : String,
//                                             completed : String,
//                                             new_balance : String,
//                                             value : String)
//
//
//  case class KafkaInboundAtm(
//                              id : String,
//                              bank_id: String,
//                              name : String,
//                              address : KafkaInboundAddress,
//                              location : KafkaInboundLocation,
//                              meta : KafkaInboundMeta
//                            )
//
//
//  case class KafkaInboundProduct(
//                                  bank_id : String,
//                                  code: String,
//                                  name : String,
//                                  category : String,
//                                  family : String,
//                                  super_family : String,
//                                  more_info_url : String,
//                                  meta : KafkaInboundMeta
//                                )
//
//
//  case class KafkaInboundAccountData(
//                                      banks : List[KafkaInboundBank],
//                                      users : List[KafkaInboundUser],
//                                      accounts : List[KafkaInboundAccount]
//                                    )
//
//  // We won't need this. TODO clean up.
//  case class KafkaInboundData(
//                               banks : List[KafkaInboundBank],
//                               users : List[KafkaInboundUser],
//                               accounts : List[KafkaInboundAccount],
//                               transactions : List[KafkaInboundTransaction],
//                               branches: List[KafkaInboundBranch],
//                               atms: List[KafkaInboundAtm],
//                               products: List[KafkaInboundProduct],
//                               crm_events: List[KafkaInboundCrmEvent]
//                             )
//
//
//  case class KafkaInboundCrmEvent(
//                                   id : String, // crmEventId
//                                   bank_id : String,
//                                   customer: KafkaInboundCustomer,
//                                   category : String,
//                                   detail : String,
//                                   channel : String,
//                                   actual_date: String
//                                 )
//
//  case class KafkaInboundCustomer(
//                                   name: String,
//                                   number : String // customer number, also known as ownerId (owner of accounts) aka API User?
//                                 )
//
//
//  case class KafkaInboundTransactionId(
//                                        transactionId : String
//                                      )
//  case class KafkaOutboundTransaction(username: String,
//                                      accountId: String,
//                                      currency: String,
//                                      amount: String,
//                                      otherAccountId: String,
//                                      otherAccountCurrency: String,
//                                      transactionType: String)
//}
//

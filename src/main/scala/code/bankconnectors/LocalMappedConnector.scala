package code.bankconnectors

import java.util.{Date, UUID}

import code.api.util.APIUtil.saveConnectorMetric
import code.api.util.ErrorMessages
import code.api.v2_1_0.{AtmJsonPost, BranchJsonPostV210, TransactionRequestCommonBodyJSON}
import code.atms.Atms.{Atm, AtmId}
import code.atms.MappedAtm
import code.branches.Branches._
import code.branches.MappedBranch
import code.common.{Address, _}
import code.fx.{FXRate, MappedFXRate, fx}
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.comments.Comments
import code.metadata.counterparties.{Counterparties, CounterpartyTrait}
import code.metadata.narrative.Narrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.model.dataAccess._
import code.model.{TransactionRequestType, _}
import code.products.MappedProduct
import code.products.Products.{Product, ProductCode}
import code.transaction.MappedTransaction
import code.transactionrequests.TransactionRequests._
import code.transactionrequests._
import code.util.Helper
import code.util.Helper.{MdcLoggable, _}
import code.views.Views
import com.google.common.cache.CacheBuilder
import com.tesobe.model.UpdateBankAccount
import net.liftweb.common._
import net.liftweb.mapper.{By, _}
import net.liftweb.util.Helpers.{tryo, _}
import net.liftweb.util.{BCrypt, Props, StringHelpers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.BigInt
import scalacache.{ScalaCache, _}
import scalacache.guava.GuavaCache
import scalacache.memoization._


object LocalMappedConnector extends Connector with MdcLoggable {

  type AccountType = MappedBankAccount
  val maxBadLoginAttempts = Props.get("max.bad.login.attempts") openOr "10"
  
  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
  val getTransactionsTTL                    = Props.get("connector.cache.ttl.seconds.getTransactions", "0").toInt * 1000 // Miliseconds

  //This is the implicit parameter for saveConnectorMetric function.  
  //eg:  override def getBank(bankId: BankId): Box[Bank] = saveConnectorMetric 
  implicit override val nameOfConnector = LocalMappedConnector.getClass.getSimpleName


  override def getAdapterInfo: Box[InboundAdapterInfo] = Empty

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String): AmountOfMoney = {
    val propertyName = "transactionRequests_challenge_threshold_" + transactionRequestType.toUpperCase
    val threshold = BigDecimal(Props.get(propertyName, "1000"))
    logger.debug(s"threshold is $threshold")

    // TODO constrain this to supported currencies.
    val thresholdCurrency = Props.get("transactionRequests_challenge_currency", "EUR")
    logger.debug(s"thresholdCurrency is $thresholdCurrency")

    val rate = fx.exchangeRate(thresholdCurrency, currency)
    val convertedThreshold = fx.convert(threshold, rate)
    logger.debug(s"getChallengeThreshold for currency $currency is $convertedThreshold")
    AmountOfMoney(currency, convertedThreshold.toString())
  }

  /**
    * Steps To Create, Store and Send Challenge
    * 1. Generate a random challenge
    * 2. Generate a long random salt
    * 3. Prepend the salt to the challenge and hash it with a standard password hashing function like Argon2, bcrypt, scrypt, or PBKDF2.
    * 4. Save both the salt and the hash in the user's database record.
    * 5. Send the challenge over an separate communication channel.
    */
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, phoneNumber: String): Box[String] = {
    val challengeId = UUID.randomUUID().toString
    val challenge = StringHelpers.randomString(6)
    // Random string. For instance: EONXOA
    val salt = BCrypt.gensalt()
    val hash = BCrypt.hashpw(challenge, salt).substring(0, 44)
    // TODO Extend database model in order to store users salt and hash
    // Store salt and hash and bind to challengeId
    // TODO Send challenge to the user over an separate communication channel
    //Return id of challenge
    Full(challengeId)
  }

  /**
    * To Validate A Challenge Answer
    * 1. Retrieve the user's salt and hash from the database.
    * 2. Prepend the salt to the given password and hash it using the same hash function.
    * 3. Compare the hash of the given answer with the hash from the database. If they match, the answer is correct. Otherwise, the answer is incorrect.
    */
  // TODO Extend database model in order to get users salt and hash it
  override def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String): Box[Boolean] = {
    for {
      nonEmpty <- booleanToBox(hashOfSuppliedAnswer.nonEmpty) ?~ "Need a non-empty answer"
      answerToNumber <- tryo(BigInt(hashOfSuppliedAnswer)) ?~! "Need a numeric TAN"
      positive <- booleanToBox(answerToNumber > 0) ?~ "Need a positive TAN"
    } yield true
  }

  override def getChargeLevel(bankId: BankId,
                              accountId: AccountId,
                              viewId: ViewId,
                              userId: String,
                              userName: String,
                              transactionRequestType: String,
                              currency: String): Box[AmountOfMoney] = {
    val propertyName = "transactionRequests_charge_level_" + transactionRequestType.toUpperCase
    val chargeLevel = BigDecimal(Props.get(propertyName, "0.0001"))
    logger.debug(s"transactionRequests_charge_level is $chargeLevel")

    // TODO constrain this to supported currencies.
    //    val chargeLevelCurrency = Props.get("transactionRequests_challenge_currency", "EUR")
    //    logger.debug(s"chargeLevelCurrency is $chargeLevelCurrency")
    //    val rate = fx.exchangeRate (chargeLevelCurrency, currency)
    //    val convertedThreshold = fx.convert(chargeLevel, rate)
    //    logger.debug(s"getChallengeThreshold for currency $currency is $convertedThreshold")

    Full(AmountOfMoney(currency, chargeLevel.toString))
  }

  def getUser(name: String, password: String): Box[InboundUser] = ???

  //gets a particular bank handled by this connector
  override def getBank(bankId: BankId): Box[Bank] = saveConnectorMetric {
    getMappedBank(bankId)
  }("getBank")

  private def getMappedBank(bankId: BankId): Box[MappedBank] =
    MappedBank
      .find(By(MappedBank.permalink, bankId.value))
      .map(
        bank => 
          bank.bankRoutingScheme ==null && bank.bankRoutingAddress == null match {
            case true  => bank.mBankRoutingScheme("OBP_BANK_ID").mBankRoutingAddress(bank.bankId.value) 
            case _ => bank
          }
      )

  //gets banks handled by this connector
  override def getBanks(): Box[List[Bank]] = saveConnectorMetric {
     Full(MappedBank
        .findAll()
        .map(
          bank =>
            bank.bankRoutingScheme ==null && bank.bankRoutingAddress == null match {
              case true  => bank.mBankRoutingScheme("OBP_BANK_ID").mBankRoutingAddress(bank.bankId.value)
              case _ => bank
            }
        )
     )
  }("getBanks")


  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Box[Transaction] = {

    updateAccountTransactions(bankId, accountId)

    MappedTransaction.find(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountId.value),
      By(MappedTransaction.transactionId, transactionId.value)).flatMap(_.toTransaction)
  }

  override def getTransactions(bankId: BankId, accountId: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
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
    val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountId.value)) ++ optionalParams
  
    def getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams : Seq[QueryParam[MappedTransaction]]): Box[List[Transaction]] =  memoizeSync(getTransactionsTTL millisecond){
  
      val mappedTransactions = MappedTransaction.findAll(mapperParams: _*)
  
      updateAccountTransactions(bankId, accountId)
  
      for (account <- getBankAccount(bankId, accountId))
        yield mappedTransactions.flatMap(_.toTransaction(account))
    }
  
    getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams)
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
      account <- getBankAccount(bankId, accountId)
    } {
      Future{
        val useMessageQueue = Props.getBool("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = Box!!account.accountLastUpdate.get match {
          case Full(l) => now after time(l.getTime + hours(Props.getInt("messageQueue.updateTransactionsInterval", 1)))
          case _ => true
        }
        if(outDatedTransactions && useMessageQueue) {
          UpdatesRequestSender.sendMsg(UpdateBankAccount(account.accountNumber.get, bank.national_identifier.get))
        }
      }
    }
  }

  override def getBankAccount(bankId: BankId, accountId: AccountId): Box[MappedBankAccount] = {
    MappedBankAccount
      .find(By(MappedBankAccount.bank, bankId.value), 
        By(MappedBankAccount.theAccountId, accountId.value))
      .map(
        account => 
          account.accountRoutingScheme ==null && account.accountRoutingAddress == null match {
            case true  => account.mAccountRoutingScheme("OBP_ACCOUNT_ID").mAccountRoutingAddress(account.accountId.value) 
            case _ => account
        }
    )
  }

  override def getEmptyBankAccount(): Box[AccountType] = {
    Full(new MappedBankAccount())
  }

  /**
    * This is used for create or update the special bankAccount for COUNTERPARTY stuff (toAccountProvider != "OBP") and (Connector = Kafka)
    * details in createTransactionRequest - V210 ,case "COUNTERPARTY"
    *
    */
  def createOrUpdateMappedBankAccount(bankId: BankId, accountId: AccountId, currency: String): Box[BankAccount] = {

    val mappedBankAccount = getBankAccount(bankId, accountId) match {
      case Full(f) =>
        f.bank(bankId.value).theAccountId(accountId.value).accountCurrency(currency).saveMe()
      case _ =>
        MappedBankAccount.create.bank(bankId.value).theAccountId(accountId.value).accountCurrency(currency).saveMe()
    }

    Full(mappedBankAccount)
  }


  // Get all counterparties related to an account
  override def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId): List[Counterparty] =
  //TODO, performance issue, when many metadata and many transactions, this will course a big problem .
  Counterparties.counterparties.vend.getMetadatas(bankId, accountId).flatMap(getCounterpartyFromTransaction(bankId, accountId, _))

  // Get one counterparty related to a bank account
  override def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyID: String): Box[Counterparty] =
  // Get the metadata and pass it to getOtherBankAccount to construct the other account.
  Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterpartyID).flatMap(getCounterpartyFromTransaction(bankId, accountId, _))


  def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = {
    for {
      t <- Counterparties.counterparties.vend.getMetadata(thisBankId, thisAccountId, couterpartyId)
    } yield {
      new Counterparty(
        //counterparty id is defined to be the id of its metadata as we don't actually have an id for the counterparty itself
        counterPartyId = t.metadataId,
        label = t.getHolder,
        nationalIdentifier = "",
        otherBankRoutingAddress = None,
        otherAccountRoutingAddress = None,
        thisAccountId = AccountId(t.getAccountNumber),
        thisBankId = BankId(""),
        kind = "",
        otherBankId = thisBankId,
        otherAccountId = thisAccountId,
        alreadyFoundMetadata = Some(t),
        name = "",
        otherBankRoutingScheme = "",
        otherAccountRoutingScheme="",
        otherAccountProvider = "",
        isBeneficiary = true
      )
    }
  }

  def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait] ={
    Counterparties.counterparties.vend.getCounterparty(counterpartyId.value)
  }

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] ={
    Counterparties.counterparties.vend.getCounterpartyByIban(iban)
  }


  override def getPhysicalCards(user: User): List[PhysicalCard] = {
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCards(user)
    for (l <- list) yield
      new PhysicalCard(
        bankCardNumber = l.mBankCardNumber,
        nameOnCard = l.mNameOnCard,
        issueNumber = l.mIssueNumber,
        serialNumber = l.mSerialNumber,
        validFrom = l.validFrom,
        expires = l.expires,
        enabled = l.enabled,
        cancelled = l.cancelled,
        onHotList = l.onHotList,
        technology = "",
        networks = List(),
        allows = l.allows,
        account = l.account,
        replacement = l.replacement,
        pinResets = l.pinResets,
        collected = l.collected,
        posted = l.posted
      )
  }

  override def getPhysicalCardsForBank(bank: Bank, user: User): List[PhysicalCard] = {
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCardsForBank(bank, user)
    for (l <- list) yield
      new PhysicalCard(
        bankCardNumber = l.mBankCardNumber,
        nameOnCard = l.mNameOnCard,
        issueNumber = l.mIssueNumber,
        serialNumber = l.mSerialNumber,
        validFrom = l.validFrom,
        expires = l.expires,
        enabled = l.enabled,
        cancelled = l.cancelled,
        onHotList = l.onHotList,
        technology = "",
        networks = List(),
        allows = l.allows,
        account = l.account,
        replacement = l.replacement,
        pinResets = l.pinResets,
        collected = l.collected,
        posted = l.posted
      )
  }

  def AddPhysicalCard(bankCardNumber: String,
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
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.AddPhysicalCard(
                                                                              bankCardNumber,
                                                                              nameOnCard,
                                                                              issueNumber,
                                                                              serialNumber,
                                                                              validFrom,
                                                                              expires,
                                                                              enabled,
                                                                              cancelled,
                                                                              onHotList,
                                                                              technology,
                                                                              networks,
                                                                              allows,
                                                                              accountId,
                                                                              bankId: String,
                                                                              replacement,
                                                                              pinResets,
                                                                              collected,
                                                                              posted
                                                                            )
    for (l <- list) yield
    new PhysicalCard(
      bankCardNumber = l.mBankCardNumber,
      nameOnCard = l.mNameOnCard,
      issueNumber = l.mIssueNumber,
      serialNumber = l.mSerialNumber,
      validFrom = l.validFrom,
      expires = l.expires,
      enabled = l.enabled,
      cancelled = l.cancelled,
      onHotList = l.onHotList,
      technology = "",
      networks = List(),
      allows = l.allows,
      account = l.account,
      replacement = l.replacement,
      pinResets = l.pinResets,
      collected = l.collected,
      posted = l.posted
    )
  }

  /**
    * Perform a payment (in the sandbox) Store one or more transactions
   */
  override def makePaymentImpl(fromAccount: MappedBankAccount,
                               toAccount: MappedBankAccount,
                               toCounterparty: CounterpartyTrait,
                               amount: BigDecimal,
                               description: String,
                               transactionRequestType: TransactionRequestType,
                               chargePolicy: String): Box[TransactionId] = {

    // Note: These are guards. Values are calculated in makePaymentv200
    val rate = tryo {
      fx.exchangeRate(fromAccount.currency, toAccount.currency)
    } ?~! {
      s"The requested currency conversion (${fromAccount.currency} to ${toAccount.currency}) is not supported."
    }

    // Is it better to pass these into this function ?
    val fromTransAmt = -amount//from fromAccount balance should decrease
    val toTransAmt = fx.convert(amount, rate.get)

    // From
    val sentTransactionId = saveTransaction(fromAccount, toAccount, toCounterparty, fromTransAmt, description, transactionRequestType, chargePolicy)

    // To
    val recievedTransactionId = saveTransaction(toAccount, fromAccount, toCounterparty, toTransAmt, description, transactionRequestType, chargePolicy)

    // Return the sent transaction id
    sentTransactionId
  }
  
  /**
    * Perform a payment (in the sandbox) Store one or more transactions
    */
  override def makePaymentv300(
    initiator: User,
    fromAccount: BankAccount,
    toAccount: BankAccount,
    toCounterparty: CounterpartyTrait,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    transactionRequestType: TransactionRequestType,
    chargePolicy: String
  ): Box[TransactionId] = {
    
    // Note: These are guards. Values are calculated in makePaymentv200
    val rate = tryo {
      fx.exchangeRate(fromAccount.currency, toAccount.currency)
    } ?~! {
      s"The requested currency conversion (${fromAccount.currency} to ${toAccount.currency}) is not supported."
    }
    
    val amount = BigDecimal(transactionRequestCommonBody.value.amount)
    val description = transactionRequestCommonBody.description
    // Is it better to pass these into this function ?
    val fromTransAmt = -amount//from fromAccount balance should decrease
    val toTransAmt = fx.convert(amount, rate.get)
    
    // From
    val sentTransactionId = saveTransaction(fromAccount, toAccount, toCounterparty, fromTransAmt, description, transactionRequestType, chargePolicy)
    
    // To
    val recievedTransactionId = saveTransaction(toAccount, fromAccount, toCounterparty, toTransAmt, description, transactionRequestType, chargePolicy)
    
    // Return the sent transaction id
    sentTransactionId
  }
  
  
  /**
    * Saves a transaction with @amount, @toAccount and @transactionRequestType for @fromAccount and @toCounterparty. <br>
    * Returns the id of the saved transactionId.<br>
    */
  private def saveTransaction(fromAccount: BankAccount,
                              toAccount: BankAccount,
                              toCounterparty: CounterpartyTrait,
                              amount: BigDecimal,
                              description: String,
                              transactionRequestType: TransactionRequestType,
                              chargePolicy: String): Box[TransactionId] = {
    //Note: read the latest data from database
    //For FREE_FORM, we need make sure always use the latest data
    val fromAccountUpdate: Box[MappedBankAccount] = getBankAccount(fromAccount.bankId, fromAccount.accountId)
    val transactionTime = now
    val currency = fromAccount.currency


    //update the balance of the fromAccount for which a transaction is being created
    val newAccountBalance: Long = fromAccountUpdate.get.accountBalance.get + Helper.convertToSmallestCurrencyUnits(amount, fromAccountUpdate.get.currency)
    fromAccountUpdate.get.accountBalance(newAccountBalance).save()

    val mappedTransaction = MappedTransaction.create
      //No matter which type (SANDBOX_TAN,SEPA,FREE_FORM,COUNTERPARTYE), always filled the following nine fields.
      .bank(fromAccountUpdate.get.bankId.value)
      .account(fromAccountUpdate.get.accountId.value)
      .transactionType(transactionRequestType.value)
      .amount(Helper.convertToSmallestCurrencyUnits(amount, currency))
      .newAccountBalance(newAccountBalance)
      .currency(currency)
      .tStartDate(transactionTime)
      .tFinishDate(transactionTime)
      .description(description)
       //Old data: other BankAccount(toAccount: BankAccount)simulate counterparty
      .counterpartyAccountHolder(toAccount.accountHolder)
      .counterpartyAccountNumber(toAccount.number)
      .counterpartyAccountKind(toAccount.accountType)
      .counterpartyBankName(toAccount.bankName)
      .counterpartyIban(toAccount.iban.getOrElse(""))
      .counterpartyNationalId(toAccount.nationalIdentifier)
       //New data: real counterparty (toCounterparty: CounterpartyTrait)
      .CPCounterPartyId(toCounterparty.counterpartyId)
      .CPOtherAccountRoutingScheme(toCounterparty.otherAccountRoutingScheme)
      .CPOtherAccountRoutingAddress(toCounterparty.otherAccountRoutingAddress)
      .CPOtherBankRoutingScheme(toCounterparty.otherBankRoutingScheme)
      .CPOtherBankRoutingAddress(toCounterparty.otherBankRoutingAddress)
      .chargePolicy(chargePolicy)
      .saveMe

    Full(mappedTransaction.theTransactionId)
  }

  /*
    Transaction Requests
  */
  override def getTransactionRequestStatusesImpl() : Box[TransactionRequestStatus] = Empty

  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId,
                                            transactionRequestType: TransactionRequestType,
                                            account : BankAccount,
                                            counterparty : BankAccount,
                                            body: TransactionRequestBody,
                                            status: String,
                                            charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl(transactionRequestId,
      transactionRequestType,
      account,
      counterparty,
      body,
      status,
      charge)
  }

  override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                               transactionRequestType: TransactionRequestType,
                                               fromAccount: BankAccount,
                                               toAccount: BankAccount,
                                               toCounterparty: CounterpartyTrait,
                                               transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                               details: String,
                                               status: String,
                                               charge: TransactionRequestCharge,
                                               chargePolicy: String): Box[TransactionRequest] = {

    TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl210(transactionRequestId,
      transactionRequestType,
      fromAccount,
      toAccount,
      toCounterparty,
      transactionRequestCommonBody,
      details,
      status,
      charge,
      chargePolicy)
  }

  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = {
    TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestTransactionImpl(transactionRequestId, transactionId)
  }

  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = {
    TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestChallengeImpl(transactionRequestId, challenge)
  }

  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = {
    TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestStatusImpl(transactionRequestId, status)
  }


  override def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
  }

  override def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
  }

  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId) : Box[TransactionRequest] = {
    // TODO need to pass a status variable so we can return say only INITIATED
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequest(transactionRequestId)
  }


  override def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
    //TODO: write logic / data access
    // Get Transaction Request Types from Props "transactionRequests_supported_types". Default is empty string
    val validTransactionRequestTypes = Props.get("transactionRequests_supported_types", "").split(",").map(x => TransactionRequestType(x)).toList
    Full(validTransactionRequestTypes)
  }

  /*
    Bank account creation
   */

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  //again assume national identifier is unique
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
  ): (Bank, BankAccount) = {
    //don't require and exact match on the name, just the identifier
    val bank = MappedBank.find(By(MappedBank.national_identifier, bankNationalIdentifier)) match {
      case Full(b) =>
        logger.debug(s"bank with id ${b.bankId} and national identifier ${b.nationalIdentifier} found")
        b
      case _ =>
        logger.debug(s"creating bank with national identifier $bankNationalIdentifier")
        //TODO: need to handle the case where generatePermalink returns a permalink that is already used for another bank
        MappedBank.create
          .permalink(Helper.generatePermalink(bankName))
          .fullBankName(bankName)
          .shortBankName(bankName)
          .national_identifier(bankNationalIdentifier)
          .saveMe()
    }

    //TODO: pass in currency as a parameter?
    val account = createAccountIfNotExisting(
      bank.bankId,
      AccountId(UUID.randomUUID().toString),
      accountNumber, accountType,
      accountLabel, currency,
      0L, accountHolderName,
      "", "", "" //added field in V220
    )

    (bank, account)
  }

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String): Boolean = {
    MappedBankAccount.count(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.accountNumber, accountNumber)) > 0
  }

  //remove an account and associated transactions
  override def removeAccount(bankId: BankId, accountId: AccountId) : Boolean = {
    //delete comments on transactions of this account
    val commentsDeleted = Comments.comments.vend.bulkDeleteComments(bankId, accountId)

    //delete narratives on transactions of this account
    val narrativesDeleted = Narrative.narrative.vend.bulkDeleteNarratives(bankId, accountId)

    //delete narratives on transactions of this account
    val tagsDeleted = Tags.tags.vend.bulkDeleteTags(bankId, accountId)

    //delete WhereTags on transactions of this account
    val whereTagsDeleted = WhereTags.whereTags.vend.bulkDeleteWhereTags(bankId, accountId)

    //delete transaction images on transactions of this account
    val transactionImagesDeleted = TransactionImages.transactionImages.vend.bulkDeleteTransactionImage(bankId, accountId)

    //delete transactions of account
    val transactionsDeleted = MappedTransaction.bulkDelete_!!(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountId.value)
    )

    //remove view privileges
    val privilegesDeleted = Views.views.vend.removeAllPermissions(bankId, accountId)

    //delete views of account
    val viewsDeleted = Views.views.vend.removeAllViews(bankId, accountId)

    //delete account
    val account = MappedBankAccount.find(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.theAccountId, accountId.value)
    )

    val accountDeleted = account match {
      case Full(acc) => acc.delete_!
      case _ => false
    }

    commentsDeleted && narrativesDeleted && tagsDeleted && whereTagsDeleted && transactionImagesDeleted &&
      transactionsDeleted && privilegesDeleted && viewsDeleted && accountDeleted
}

  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
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

    for {
      bank <- getBank(bankId) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
    } yield {

      val balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
      createAccountIfNotExisting(
        bankId,
        accountId,
        accountNumber,
        accountType,
        accountLabel,
        currency,
        balanceInSmallestCurrencyUnits,
        accountHolderName,
        branchId,
        accountRoutingScheme,
        accountRoutingAddress
      )
    }

  }


  private def createAccountIfNotExisting(
    bankId: BankId,
    accountId: AccountId,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    balanceInSmallestCurrencyUnits: Long,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String
  ) : BankAccount = {
    getBankAccount(bankId, accountId) match {
      case Full(a) =>
        logger.debug(s"account with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        a
      case _ =>
        MappedBankAccount.create
          .bank(bankId.value)
          .theAccountId(accountId.value)
          .accountNumber(accountNumber)
          .kind(accountType)
          .accountLabel(accountLabel)
          .accountCurrency(currency)
          .accountBalance(balanceInSmallestCurrencyUnits)
          .holder(accountHolderName)
          .mBranchId(branchId)
          .mAccountRoutingScheme(accountRoutingScheme)
          .mAccountRoutingAddress(accountRoutingAddress)
          .saveMe()
    }
  }

  /*
    End of bank account creation
   */


  /*
    Transaction importer api
   */

  //used by the transaction import api
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal): Boolean = {

    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      bank <- getMappedBank(bankId)
    } yield {
      acc.accountBalance(Helper.convertToSmallestCurrencyUnits(newBalance, acc.currency)).save
      setBankAccountLastUpdated(bank.nationalIdentifier, acc.number, now)
    }

    result.getOrElse(false)
  }

  //transaction import api uses bank national identifiers to uniquely indentify banks,
  //which is unfortunate as theoretically the national identifier is unique to a bank within
  //one country
  private def getBankByNationalIdentifier(nationalIdentifier : String) : Box[Bank] = {
    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
  }

  private def getAccountByNumber(bankId : BankId, number : String) : Box[AccountType] = {
    MappedBankAccount.find(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.accountNumber, number))
  }

  private val bigDecimalFailureHandler : PartialFunction[Throwable, Unit] = {
    case ex : NumberFormatException => {
      logger.warn(s"could not convert amount to a BigDecimal: $ex")
    }
  }

  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String): Int = {
    //we need to convert from the legacy bankNationalIdentifier to BankId, and from the legacy accountNumber to AccountId
    val count = for {
      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
      account <- getAccountByNumber(bankId, accountNumber)
      amountAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(amount))
    } yield {

      val amountInSmallestCurrencyUnits =
        Helper.convertToSmallestCurrencyUnits(amountAsBigDecimal, account.currency)

      MappedTransaction.count(
        By(MappedTransaction.bank, bankId.value),
        By(MappedTransaction.account, account.accountId.value),
        By(MappedTransaction.amount, amountInSmallestCurrencyUnits),
        By(MappedTransaction.tFinishDate, completed),
        By(MappedTransaction.counterpartyAccountHolder, otherAccountHolder))
    }

    //icky
    count.map(_.toInt) getOrElse 0
  }

  //used by transaction import api
  override def createImportedTransaction(transaction: ImporterTransaction): Box[Transaction] = {
    //we need to convert from the legacy bankNationalIdentifier to BankId, and from the legacy accountNumber to AccountId
    val obpTransaction = transaction.obp_transaction
    val thisAccount = obpTransaction.this_account
    val nationalIdentifier = thisAccount.bank.national_identifier
    val accountNumber = thisAccount.number
    for {
      bank <- getBankByNationalIdentifier(transaction.obp_transaction.this_account.bank.national_identifier) ?~!
        s"No bank found with national identifier $nationalIdentifier"
      bankId = bank.bankId
      account <- getAccountByNumber(bankId, accountNumber)
      details = obpTransaction.details
      amountAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(details.value.amount))
      newBalanceAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(details.new_balance.amount))
      amountInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(amountAsBigDecimal, account.currency)
      newBalanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(newBalanceAsBigDecimal, account.currency)
      otherAccount = obpTransaction.other_account
      mappedTransaction = MappedTransaction.create
        .bank(bankId.value)
        .account(account.accountId.value)
        .transactionType(details.kind)
        .amount(amountInSmallestCurrencyUnits)
        .newAccountBalance(newBalanceInSmallestCurrencyUnits)
        .currency(account.currency)
        .tStartDate(details.posted.`$dt`)
        .tFinishDate(details.completed.`$dt`)
        .description(details.label)
        .counterpartyAccountNumber(otherAccount.number)
        .counterpartyAccountHolder(otherAccount.holder)
        .counterpartyAccountKind(otherAccount.kind)
        .counterpartyNationalId(otherAccount.bank.national_identifier)
        .counterpartyBankName(otherAccount.bank.name)
        .counterpartyIban(otherAccount.bank.IBAN)
        .saveMe()
      transaction <- mappedTransaction.toTransaction(account)
    } yield transaction
  }

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Boolean = {
    val result = for {
      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
      account <- getAccountByNumber(bankId, accountNumber)
    } yield {
        val acc = MappedBankAccount.find(
          By(MappedBankAccount.bank, bankId.value),
          By(MappedBankAccount.theAccountId, account.accountId.value)
        )
        acc match {
          case Full(a) => a.accountLastUpdate(updateDate).save
          case _ => logger.warn("can't set bank account.lastUpdated because the account was not found"); false
        }
    }
    result.getOrElse(false)
  }

  /*
    End of transaction importer api
   */


  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Boolean = {
    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      bank <- getMappedBank(bankId)
    } yield {
        acc.accountLabel(label).save
      }

    result.getOrElse(false)
  }

  override def getProducts(bankId: BankId): Box[List[MappedProduct]] = {
    Full(MappedProduct.findAll(By(MappedProduct.mBankId, bankId.value)))
  }

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[MappedProduct] = {
    MappedProduct.find(
      By(MappedProduct.mBankId, bankId.value),
      By(MappedProduct.mCode, productCode.value)
    )
  }


  override def createOrUpdateBranch(branch: Branch): Box[BranchT] = {

    // TODO
    // Either this should accept a Branch case class i.e. extract the construction of a Branch out of here and move it to the API
    // OR maybe this function could accept different versions of json and use pattern mathing to decide how to extract here.


    //override def createOrUpdateBranch(branch: BranchJsonPost, branchRoutingScheme: String, branchRoutingAddress: String): Box[Branch] = {



/*


    val address : Address = Address(
      branch.address.line_1,
      branch.address.line_2,
      branch.address.line_3,
      branch.address.city,
      branch.address.county,
      branch.address.state,
      branch.address.post_code,
      branch.address.country_code
    )

    val location: Location = Location(branch.location.latitude.toDouble,
                                      branch.location.longitude.toDouble)


    val lobby : Lobby = Lobby(
      monday = OpeningTimes(
        openingTime = branch.lobby.monday.opening_time,
        closingTime = branch.lobby.monday.closing_time),
      tuesday = OpeningTimes(
        openingTime = branch.lobby.tuesday.opening_time,
        closingTime = branch.lobby.tuesday.closing_time),
      wednesday = OpeningTimes(
        openingTime = branch.lobby.wednesday.opening_time,
        closingTime = branch.lobby.wednesday.closing_time),
      thursday = OpeningTimes(
        openingTime = branch.lobby.thursday.opening_time,
        closingTime = branch.lobby.thursday.closing_time),
      friday = OpeningTimes(
        openingTime = branch.lobby.friday.opening_time,
        closingTime = branch.lobby.friday.closing_time),
      saturday = OpeningTimes(
        openingTime = branch.lobby.saturday.opening_time,
        closingTime = branch.lobby.saturday.closing_time),
      sunday = OpeningTimes(
        openingTime = branch.lobby.sunday.opening_time,
        closingTime = branch.lobby.sunday.closing_time)
    )

    val driveUp : DriveUp = DriveUp(
      monday = OpeningTimes(
        openingTime = branch.drive_up.monday.opening_time,
        closingTime = branch.drive_up.monday.closing_time),
      tuesday = OpeningTimes(
        openingTime = branch.drive_up.tuesday.opening_time,
        closingTime = branch.drive_up.tuesday.closing_time),
      wednesday = OpeningTimes(
        openingTime = branch.drive_up.wednesday.opening_time,
        closingTime = branch.drive_up.wednesday.closing_time),
      thursday = OpeningTimes(
        openingTime = branch.drive_up.thursday.opening_time,
        closingTime = branch.drive_up.thursday.closing_time),
      friday = OpeningTimes(
        openingTime = branch.drive_up.friday.opening_time,
        closingTime = branch.drive_up.friday.closing_time),
      saturday = OpeningTimes(
        openingTime = branch.drive_up.saturday.opening_time,
        closingTime = branch.drive_up.saturday.closing_time),
      sunday = OpeningTimes(
        openingTime = branch.drive_up.sunday.opening_time,
        closingTime = branch.drive_up.sunday.closing_time)
    )



    val license = License(branch.meta.license.id, branch.meta.license.name)

    val meta = Meta(license = license)

    val branchRouting = Routing(branch.branch_routing.scheme, branch.branch_routing.address)



    val branch : Branch = Branch(
    branchId =  BranchId(branch.id),
    bankId = BankId(branch.bank_id),
    name = branch.name,
    address = address,
    location = location,
    meta =  meta,
    lobbyString = "depreciated from V3.0.0",
    driveUpString = "depreciated from V3.0.0",
    lobby = lobby,
    driveUp = driveUp,
    branchRouting = branchRouting,
    // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
    isAccessible = branch.is_accessible,
    branchType = branch.branch_type,
    moreInfo = branch.more_info
    )

*/



     val isAccessibleString = optionBooleanToString(branch.isAccessible)
     val branchTypeString = branch.branchType.orNull


      //check the branch existence and update or insert data
    getBranch(branch.bankId, branch.branchId) match {
      case Full(mappedBranch) =>
        tryo {
          // Update...
          mappedBranch
            // Doesn't make sense to update branchId and bankId
            //.mBranchId(branch.branchId)
            //.mBankId(branch.bankId)
            .mName(branch.name)
            .mLine1(branch.address.line1)
            .mLine2(branch.address.line2)
            .mLine3(branch.address.line3)
            .mCity(branch.address.city)
            .mCounty(branch.address.county.orNull)
            .mState(branch.address.state)
            .mPostCode(branch.address.postCode)
            .mCountryCode(branch.address.countryCode)
            .mlocationLatitude(branch.location.latitude)
            .mlocationLongitude(branch.location.longitude)
            .mLicenseId(branch.meta.license.id)
            .mLicenseName(branch.meta.license.name)
            .mLobbyHours(branch.lobbyString.map(_.hours).getOrElse("")) // ok like this? only used by versions prior to v3.0.0
            .mDriveUpHours(branch.driveUpString.map(_.hours).getOrElse("")) // ok like this? only used by versions prior to v3.0.0
            .mBranchRoutingScheme(branch.branchRouting.map(_.scheme).orNull) //Added in V220
            .mBranchRoutingAddress(branch.branchRouting.map(_.address).orNull) //Added in V220
            .mLobbyOpeningTimeOnMonday(branch.lobby.map(_.monday).map(_.openingTime).orNull)
            .mLobbyClosingTimeOnMonday(branch.lobby.map(_.monday).map(_.closingTime).orNull)

            .mLobbyOpeningTimeOnTuesday(branch.lobby.map(_.tuesday).map(_.openingTime).orNull)
//            .mLobbyClosingTimeOnTuesday(branch.lobby.tuesday.closingTime)
//
//            .mLobbyOpeningTimeOnWednesday(branch.lobby.wednesday.openingTime)
//            .mLobbyClosingTimeOnWednesday(branch.lobby.wednesday.closingTime)
//
//            .mLobbyOpeningTimeOnThursday(branch.lobby.thursday.openingTime)
//            .mLobbyClosingTimeOnThursday(branch.lobby.thursday.closingTime)
//
//            .mLobbyOpeningTimeOnFriday(branch.lobby.friday.openingTime)
//            .mLobbyClosingTimeOnFriday(branch.lobby.friday.closingTime)
//
//            .mLobbyOpeningTimeOnSaturday(branch.lobby.saturday.openingTime)
//            .mLobbyClosingTimeOnSaturday(branch.lobby.saturday.closingTime)
//
//            .mLobbyOpeningTimeOnSunday(branch.lobby.sunday.openingTime)
//            .mLobbyClosingTimeOnSunday(branch.lobby.sunday.closingTime)
//
//
//          // Drive Up
//            .mDriveUpOpeningTimeOnMonday(branch.driveUp.monday.openingTime)
//            .mDriveUpClosingTimeOnMonday(branch.driveUp.monday.closingTime)
//
//            .mDriveUpOpeningTimeOnTuesday(branch.driveUp.tuesday.openingTime)
//            .mDriveUpClosingTimeOnTuesday(branch.driveUp.tuesday.closingTime)
//
//            .mDriveUpOpeningTimeOnWednesday(branch.driveUp.wednesday.openingTime)
//            .mDriveUpClosingTimeOnWednesday(branch.driveUp.wednesday.closingTime)
//
//            .mDriveUpOpeningTimeOnThursday(branch.driveUp.thursday.openingTime)
//            .mDriveUpClosingTimeOnThursday(branch.driveUp.thursday.closingTime)
//
//            .mDriveUpOpeningTimeOnFriday(branch.driveUp.friday.openingTime)
//            .mDriveUpClosingTimeOnFriday(branch.driveUp.friday.closingTime)
//
//            .mDriveUpOpeningTimeOnSaturday(branch.driveUp.saturday.openingTime)
//            .mDriveUpClosingTimeOnSaturday(branch.driveUp.saturday.closingTime)
//
//            .mDriveUpOpeningTimeOnSunday(branch.driveUp.sunday.openingTime)
//            .mDriveUpClosingTimeOnSunday(branch.driveUp.sunday.closingTime)

            .mIsAccessible(isAccessibleString) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown

            .mBranchType(branch.branchType.orNull)
            .mMoreInfo(branch.moreInfo.orNull)

            .saveMe()
        }
      case _ =>
        tryo {
          // Insert...
          MappedBranch.create
            .mBranchId(branch.branchId.toString)
            .mBankId(branch.bankId.toString)
            .mName(branch.name)
            .mLine1(branch.address.line1)
            .mLine2(branch.address.line2)
            .mLine3(branch.address.line3)
            .mCity(branch.address.city)
            .mCounty(branch.address.county.orNull)
            .mState(branch.address.state)
            .mPostCode(branch.address.postCode)
            .mCountryCode(branch.address.countryCode)
            .mlocationLatitude(branch.location.latitude)
            .mlocationLongitude(branch.location.longitude)
            .mLicenseId(branch.meta.license.id)
            .mLicenseName(branch.meta.license.name)
            .mLobbyHours(branch.lobbyString.map(_.hours).getOrElse("")) // OK like this?  only used by versions prior to v3.0.0
            .mDriveUpHours(branch.driveUpString.map(_.hours).getOrElse("")) // OK like this? only used by versions prior to v3.0.0
            .mBranchRoutingScheme(branch.branchRouting.map(_.scheme).orNull) //Added in V220
            .mBranchRoutingAddress(branch.branchRouting.map(_.address).orNull) //Added in V220
            .mLobbyOpeningTimeOnMonday(branch.lobby.map(_.monday).map(_.openingTime).orNull)
            .mLobbyClosingTimeOnMonday(branch.lobby.map(_.monday).map(_.closingTime).orNull)

            .mLobbyOpeningTimeOnTuesday(branch.lobby.map(_.tuesday).map(_.openingTime).orNull)
//            .mLobbyClosingTimeOnTuesday(branch.lobby.tuesday.closingTime)
//
//            .mLobbyOpeningTimeOnWednesday(branch.lobby.wednesday.openingTime)
//            .mLobbyClosingTimeOnWednesday(branch.lobby.wednesday.closingTime)
//
//            .mLobbyOpeningTimeOnThursday(branch.lobby.thursday.openingTime)
//            .mLobbyClosingTimeOnThursday(branch.lobby.thursday.closingTime)
//
//            .mLobbyOpeningTimeOnFriday(branch.lobby.friday.openingTime)
//            .mLobbyClosingTimeOnFriday(branch.lobby.friday.closingTime)
//
//            .mLobbyOpeningTimeOnSaturday(branch.lobby.saturday.openingTime)
//            .mLobbyClosingTimeOnSaturday(branch.lobby.saturday.closingTime)
//
//            .mLobbyOpeningTimeOnSunday(branch.lobby.sunday.openingTime)
//            .mLobbyClosingTimeOnSunday(branch.lobby.sunday.closingTime)
//
//
//            // Drive Up
//            .mDriveUpOpeningTimeOnMonday(branch.driveUp.monday.openingTime)
//            .mDriveUpClosingTimeOnMonday(branch.driveUp.monday.closingTime)
//
//            .mDriveUpOpeningTimeOnTuesday(branch.driveUp.tuesday.openingTime)
//            .mDriveUpClosingTimeOnTuesday(branch.driveUp.tuesday.closingTime)
//
//            .mDriveUpOpeningTimeOnWednesday(branch.driveUp.wednesday.openingTime)
//            .mDriveUpClosingTimeOnWednesday(branch.driveUp.wednesday.closingTime)
//
//            .mDriveUpOpeningTimeOnThursday(branch.driveUp.thursday.openingTime)
//            .mDriveUpClosingTimeOnThursday(branch.driveUp.thursday.closingTime)
//
//            .mDriveUpOpeningTimeOnFriday(branch.driveUp.friday.openingTime)
//            .mDriveUpClosingTimeOnFriday(branch.driveUp.friday.closingTime)
//
//            .mDriveUpOpeningTimeOnSaturday(branch.driveUp.saturday.openingTime)
//            .mDriveUpClosingTimeOnSaturday(branch.driveUp.saturday.closingTime)
//
//            .mDriveUpOpeningTimeOnSunday(branch.driveUp.sunday.openingTime)
//            .mDriveUpClosingTimeOnSunday(branch.driveUp.sunday.closingTime)

            .mIsAccessible(isAccessibleString) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown

            .mBranchType(branch.branchType.orNull)
            .mMoreInfo(branch.moreInfo.orNull)
            .saveMe()
        }
    }
  }


  // TODO This should accept a normal case class not "json" case class i.e. don't rely on REST json structures
  override def createOrUpdateAtm(atm: AtmJsonPost): Box[Atm] = {

    //check the atm existence and update or insert data
    getAtm(BankId(atm.bank_id), AtmId(atm.id)) match {
      case Full(mappedAtm) =>
        tryo {
          mappedAtm.mName(atm.name)
            .mLine1(atm.address.line_1)
            .mLine2(atm.address.line_2)
            .mLine3(atm.address.line_3)
            .mCity(atm.address.city)
            .mCounty(atm.address.country)
            .mState(atm.address.state)
            .mPostCode(atm.address.postcode)
            .mlocationLatitude(atm.location.latitude)
            .mlocationLongitude(atm.location.longitude)
            .mLicenseId(atm.meta.license.id)
            .mLicenseName(atm.meta.license.name)
            .saveMe()
        } ?~! ErrorMessages.UpdateAtmError
      case _ =>
        tryo {
          MappedAtm.create
            .mAtmId(atm.id)
            .mBankId(atm.bank_id)
            .mName(atm.name)
            .mLine1(atm.address.line_1)
            .mLine2(atm.address.line_2)
            .mLine3(atm.address.line_3)
            .mCity(atm.address.city)
            .mCounty(atm.address.country)
            .mState(atm.address.state)
            .mPostCode(atm.address.postcode)
            .mlocationLatitude(atm.location.latitude)
            .mlocationLongitude(atm.location.longitude)
            .mLicenseId(atm.meta.license.id)
            .mLicenseName(atm.meta.license.name)
            .saveMe()
        } ?~! ErrorMessages.CreateAtmError
    }
  }



  override def createOrUpdateProduct(bankId : String,
                                     code : String,
                                     name : String,
                                     category : String,
                                     family : String,
                                     superFamily : String,
                                     moreInfoUrl : String,
                                     details : String,
                                     description : String,
                                     metaLicenceId : String,
                                     metaLicenceName : String): Box[Product] = {

    //check the product existence and update or insert data
    getProduct(BankId(bankId), ProductCode(code)) match {
      case Full(mappedProduct) =>
        tryo {
          mappedProduct.mName(name)
          .mCode (code)
          .mBankId(bankId)
          .mName(name)
          .mCategory(category)
          .mFamily(family)
          .mSuperFamily(superFamily)
          .mMoreInfoUrl(moreInfoUrl)
          .mDetails(details)
          .mDescription(description)
          .mLicenseId(metaLicenceId)
          .mLicenseName(metaLicenceName)
          .saveMe()
        } ?~! ErrorMessages.UpdateProductError
      case _ =>
        tryo {
          MappedProduct.create
            .mName(name)
            .mCode (code)
            .mBankId(bankId)
            .mName(name)
            .mCategory(category)
            .mFamily(family)
            .mSuperFamily(superFamily)
            .mMoreInfoUrl(moreInfoUrl)
            .mDetails(details)
            .mDescription(description)
            .mLicenseId(metaLicenceId)
            .mLicenseName(metaLicenceName)
            .saveMe()
        } ?~! ErrorMessages.CreateProductError
    }

  }





  override def getBranch(bankId : BankId, branchId: BranchId) : Box[MappedBranch]= {
    MappedBranch
      .find(
        By(MappedBranch.mBankId, bankId.value), 
        By(MappedBranch.mBranchId, branchId.value))
      .map(
        branch => 
          branch.branchRouting.map(_.scheme) == null && branch.branchRouting.map(_.address) == null match {
            case true => branch.mBranchRoutingScheme("OBP_BRANCH_ID").mBranchRoutingAddress(branch.branchId.value)
            case _ => branch
        }
    )
  }



  override def getAtm(bankId : BankId, atmId: AtmId) : Box[MappedAtm]= {
    MappedAtm
      .find(
        By(MappedAtm.mBankId, bankId.value),
        By(MappedAtm.mAtmId, atmId.value))
  }





  /**
    * get the latest record from FXRate table by the fields: fromCurrencyCode and toCurrencyCode.
    * If it is not found by (fromCurrencyCode, toCurrencyCode) order, it will try (toCurrencyCode, fromCurrencyCode) order .
    */
  override def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate]  = {
    /**
      * find FXRate by (fromCurrencyCode, toCurrencyCode), the normal order
      */
    val fxRateFromTo = MappedFXRate.find(
      By(MappedFXRate.mBankId, bankId.value),
      By(MappedFXRate.mFromCurrencyCode, fromCurrencyCode),
      By(MappedFXRate.mToCurrencyCode, toCurrencyCode)
    )
    /**
      * find FXRate by (toCurrencyCode, fromCurrencyCode), the reverse order
      */
    val fxRateToFrom = MappedFXRate.find(
      By(MappedFXRate.mBankId, bankId.value),
      By(MappedFXRate.mFromCurrencyCode, toCurrencyCode),
      By(MappedFXRate.mToCurrencyCode, fromCurrencyCode)
    )

    // if the result of normal order is empty, then return the reverse order result
    fxRateFromTo.orElse(fxRateToFrom)
  }

  /**
    * get the TransactionRequestTypeCharge from the TransactionRequestTypeCharge table
    * In Mapped, we will ignore accountId, viewId for now.
    */
  override def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge] = {
    val transactionRequestTypeChargeMapper = MappedTransactionRequestTypeCharge.find(
      By(MappedTransactionRequestTypeCharge.mBankId, bankId.value),
      By(MappedTransactionRequestTypeCharge.mTransactionRequestTypeId, transactionRequestType.value))

    val transactionRequestTypeCharge = transactionRequestTypeChargeMapper match {
      case Full(transactionRequestType) => TransactionRequestTypeChargeMock(
        transactionRequestType.transactionRequestTypeId,
        transactionRequestType.bankId,
        transactionRequestType.chargeCurrency,
        transactionRequestType.chargeAmount,
        transactionRequestType.chargeSummary
      )
      //If it is empty, return the default value : "0.0000000" and set the BankAccount currency
      case _ =>
        val fromAccountCurrency: String = getBankAccount(bankId, accountId).get.currency
        TransactionRequestTypeChargeMock(transactionRequestType.value, bankId.value, fromAccountCurrency, "0.00", "Warning! Default value!")
    }

    Full(transactionRequestTypeCharge)
  }

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] = {
    Counterparties.counterparties.vend.getCounterparties(thisBankId, thisAccountId, viewId)
  }

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
  ): Box[Bank] =
  //check the bank existence and update or insert data
    getMappedBank(BankId(bankId)) match {
      case Full(mappedBank) =>
        tryo {
               mappedBank
               .permalink(bankId)
               .fullBankName(fullBankName)
               .shortBankName(shortBankName)
               .logoURL(logoURL)
               .websiteURL(websiteURL)
               .swiftBIC(swiftBIC)
               .national_identifier(national_identifier)
               .mBankRoutingScheme(bankRoutingScheme)
               .mBankRoutingAddress(bankRoutingAddress)
               .saveMe()
             } ?~! ErrorMessages.CreateBankError
      case _ =>
        tryo {
               MappedBank.create
               .permalink(bankId)
               .fullBankName(fullBankName)
               .shortBankName(shortBankName)
               .logoURL(logoURL)
               .websiteURL(websiteURL)
               .swiftBIC(swiftBIC)
               .national_identifier(national_identifier)
               .mBankRoutingScheme(bankRoutingScheme)
               .mBankRoutingAddress(bankRoutingAddress)
               .saveMe()
             } ?~! ErrorMessages.UpdateBankError
    }
}

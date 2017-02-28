package code.bankconnectors

import java.util.{Date, UUID}

import code.TransactionTypes.TransactionType.TransactionTypeProvider
import code.accountholder.MapperAccountHolders$
import code.api.util.ErrorMessages
import code.api.v2_1_0.{BranchJsonPost, BranchJsonPut}
import code.branches.Branches.{Branch, BranchId}
import code.branches.MappedBranch
import code.fx.{FXRate, MappedFXRate, fx}
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.comments.{Comments, MappedComment}
import code.metadata.counterparties.{Counterparties, CounterpartyTrait, MappedCounterparty}
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.MappedTag
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.model.{TransactionRequestType, _}
import code.model.dataAccess._
import code.products.MappedProduct
import code.products.Products.{Product, ProductCode}
import code.sandbox.SandboxBranchImport
import code.transaction.MappedTransaction
import code.transactionrequests.{MappedTransactionRequest, MappedTransactionRequestTypeCharge, TransactionRequestTypeCharge, TransactionRequestTypeChargeMock}
import code.transactionrequests.TransactionRequests._
import code.util.Helper
import code.util.Helper._
import code.views.Views
import com.tesobe.model.UpdateBankAccount
import net.liftweb.common._
import net.liftweb.mapper.{By, _}
import net.liftweb.util.Helpers.{tryo, _}
import net.liftweb.util.{BCrypt, Props, StringHelpers}

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.math.BigInt

object LocalMappedConnector extends Connector with Loggable {

  type AccountType = MappedBankAccount
  val maxBadLoginAttempts = Props.get("max.bad.login.attempts") openOr "10"

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String): AmountOfMoney = {
    val propertyName = "transactionRequests_challenge_threshold_" + transactionRequestType.toUpperCase
    val threshold = BigDecimal(Props.get(propertyName, "1000"))
    logger.info(s"threshold is $threshold")

    // TODO constrain this to supported currencies.
    val thresholdCurrency = Props.get("transactionRequests_challenge_currency", "EUR")
    logger.info(s"thresholdCurrency is $thresholdCurrency")

    val rate = fx.exchangeRate (thresholdCurrency, currency)
    val convertedThreshold = fx.convert(threshold, rate)
    logger.info(s"getChallengeThreshold for currency $currency is $convertedThreshold")
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
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String): Box[String] = {
    val challengeId = UUID.randomUUID().toString
    val challenge = StringHelpers.randomString(6) // Random string. For instance: EONXOA
    val salt = BCrypt.gensalt()
    val hash = BCrypt.hashpw(challenge, salt).substring(0,44)
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


  def getUser(name: String, password: String): Box[InboundUser] = ???
  def updateUserAccountViews(user: ResourceUser): Unit = ???

  //gets a particular bank handled by this connector
  override def getBank(bankId: BankId): Box[Bank] =
    getMappedBank(bankId)

  private def getMappedBank(bankId: BankId): Box[MappedBank] =
    MappedBank.find(By(MappedBank.permalink, bankId.value))

  //gets banks handled by this connector
  override def getBanks: List[Bank] =
    MappedBank.findAll

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

    val mappedTransactions = MappedTransaction.findAll(mapperParams: _*)

    updateAccountTransactions(bankId, accountId)

    for (account <- getBankAccount(bankId, accountId))
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
    MappedBankAccount.find(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.theAccountId, accountId.value))
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


  def getCounterpartyFromTransaction(thisBankId: BankId, thisAccountId: AccountId, metadata: CounterpartyMetadata): Box[Counterparty] = {
    //because we don't have a db backed model for OtherBankAccounts, we need to construct it from an
    //OtherBankAccountMetadata and a transaction
    for { //find a transaction with this counterparty
      t <- MappedTransaction.find(
        By(MappedTransaction.bank, thisBankId.value),
        By(MappedTransaction.account, thisAccountId.value),
        By(MappedTransaction.counterpartyAccountHolder, metadata.getHolder),
        By(MappedTransaction.counterpartyAccountNumber, metadata.getAccountNumber))
    } yield {
      new Counterparty(
        //counterparty id is defined to be the id of its metadata as we don't actually have an id for the counterparty itself
        counterPartyId = metadata.metadataId,
        label = metadata.getHolder,
        nationalIdentifier = t.counterpartyNationalId.get,
        otherBankRoutingAddress = None,
        otherAccountRoutingAddress = t.getCounterpartyIban(),
        thisAccountId = AccountId(metadata.getAccountNumber),
        thisBankId = BankId(t.counterpartyBankName.get),
        kind = t.counterpartyAccountKind.get,
        otherBankId = thisBankId,
        otherAccountId = thisAccountId,
        alreadyFoundMetadata = Some(metadata),
        name = "",
        otherBankRoutingScheme = "",
        otherAccountRoutingScheme="",
        otherAccountProvider = "",
        isBeneficiary = true
      )
    }
  }

  // Get all counterparties related to an account
  override def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId): List[Counterparty] =
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
    MappedCounterparty.find(By(MappedCounterparty.mCounterPartyId, counterpartyId.value))
  }
  
  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] ={
    MappedCounterparty.find(
      By(MappedCounterparty.mOtherAccountRoutingAddress, iban),
      By(MappedCounterparty.mOtherAccountRoutingScheme, "IBAN")
    )
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

/*
Perform a payment (in the sandbox)
Store one or more transactions
 */
   override def makePaymentImpl(fromAccount: MappedBankAccount, toAccount: MappedBankAccount, toCounterparty: CounterpartyTrait, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String): Box[TransactionId] = {

    //we need to save a copy of this payment as a transaction in each of the accounts involved, with opposite amounts



    val rate = tryo {
      fx.exchangeRate(fromAccount.currency, toAccount.currency)
    } ?~! {
      s"The requested currency conversion (${fromAccount.currency} to ${toAccount.currency}) is not supported."
    }

    // Is it better to pass these into this function ?
    val fromTransAmt = -amt//from fromAccount balance should decrease
    val toTransAmt = fx.convert(amt, rate.get)

    // From
    val sentTransactionId = saveTransaction(fromAccount, toAccount, toCounterparty, fromTransAmt, description, transactionRequestType, chargePolicy)

    // To
    val recievedTransactionId = saveTransaction(toAccount, fromAccount, toCounterparty, toTransAmt, description, transactionRequestType, chargePolicy)

    // Return the sent transaction id
    sentTransactionId
  }

  /**
    * Saves a transaction with @amt, @toAccount and @transactionRequestType for @fromAccount and @toCounterparty. <br>
    * Returns the id of the saved transactionId.<br>
    */
  private def saveTransaction(fromAccount: MappedBankAccount,
                              toAccount: BankAccount,
                              toCounterparty: CounterpartyTrait,
                              amt: BigDecimal,
                              description: String,
                              transactionRequestType: TransactionRequestType,
                              chargePolicy: String): Box[TransactionId] = {
    
    val transactionTime = now
    val currency = fromAccount.currency


    //update the balance of the fromAccount for which a transaction is being created
    val newAccountBalance : Long = fromAccount.accountBalance.get + Helper.convertToSmallestCurrencyUnits(amt, fromAccount.currency)
    fromAccount.accountBalance(newAccountBalance).save()

    val mappedTransaction = MappedTransaction.create
      //No matter which type (SANDBOX_TAN,SEPA,FREE_FORM,COUNTERPARTYE), always filled the following nine fields.
      .bank(fromAccount.bankId.value)
      .account(fromAccount.accountId.value)
      .transactionType(transactionRequestType.value)
      .amount(Helper.convertToSmallestCurrencyUnits(amt, currency))
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
  override def getTransactionRequestStatusesImpl() : Box[Map[String, String]] = ???

  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                            account : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                            status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    val mappedTransactionRequest = MappedTransactionRequest.create
      .mTransactionRequestId(transactionRequestId.value)
      .mType(transactionRequestType.value)
      .mFrom_BankId(account.bankId.value)
      .mFrom_AccountId(account.accountId.value)
      .mBody_To_BankId(counterparty.bankId.value)
      .mBody_To_AccountId(counterparty.accountId.value)
      .mBody_Value_Currency(body.value.currency)
      .mBody_Value_Amount(body.value.amount)
      .mBody_Description(body.description)
      .mStatus(status)
      .mStartDate(now)
      .mEndDate(now)
      .mCharge_Summary(charge.summary)
      .mCharge_Amount(charge.value.amount)
      .mCharge_Currency(charge.value.currency)
      .saveMe
    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
  }

   override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType, counterpartyId: CounterpartyId, account: BankAccount, details: String, status: String, charge: TransactionRequestCharge, chargePolicy: String): Box[TransactionRequest] = {

    // Note: We don't save transaction_ids here.
    val mappedTransactionRequest = MappedTransactionRequest.create
      .mTransactionRequestId(transactionRequestId.value)
      .mCounterpartyId(counterpartyId.value)
      .mType(transactionRequestType.value)
      .mFrom_BankId(account.bankId.value)
      .mFrom_AccountId(account.accountId.value)
      .mDetails(details) // This is the details / body of the request (contains all fields in the body)
      .mStatus(status)
      .mStartDate(now)
      .mEndDate(now)
      .mCharge_Summary(charge.summary)
      .mCharge_Amount(charge.value.amount)
      .mCharge_Currency(charge.value.currency)
      .mcharge_Policy(chargePolicy)
      .saveMe
    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
  }

  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = {
    // This saves transaction_ids
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
        case Full(tr: MappedTransactionRequest) => Full(tr.mTransactionIDs(transactionId.value).save)
        case _ => Failure("Couldn't find transaction request ${transactionRequestId}")
      }
  }

  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = {
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
      case Full(tr: MappedTransactionRequest) => Full{
        tr.mChallenge_Id(challenge.id)
        tr.mChallenge_AllowedAttempts(challenge.allowed_attempts)
        tr.mChallenge_ChallengeType(challenge.challenge_type).save
      }
      case _ => Failure(s"Couldn't find transaction request ${transactionRequestId} to set transactionId")
    }
  }

  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = {
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
      case Full(tr: MappedTransactionRequest) => Full(tr.mStatus(status).save)
      case _ => Failure(s"Couldn't find transaction request ${transactionRequestId} to set status")
    }
  }


  override def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    val transactionRequests = MappedTransactionRequest.findAll(By(MappedTransactionRequest.mFrom_AccountId, fromAccount.accountId.value),
                                                               By(MappedTransactionRequest.mFrom_BankId, fromAccount.bankId.value))

    Full(transactionRequests.flatMap(_.toTransactionRequest))
  }

  override def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    val transactionRequests = MappedTransactionRequest.findAll(By(MappedTransactionRequest.mFrom_AccountId, fromAccount.accountId.value),
      By(MappedTransactionRequest.mFrom_BankId, fromAccount.bankId.value))

    Full(transactionRequests.flatMap(_.toTransactionRequest))
  }

  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId) : Box[TransactionRequest] = {
    // TODO need to pass a status variable so we can return say only INITIATED
    val transactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    transactionRequest.flatMap(_.toTransactionRequest)
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
  override def createBankAndAccount(bankName: String, bankNationalIdentifier: String, accountNumber: String, accountType: String, accountLabel: String, currency: String, accountHolderName: String): (Bank, BankAccount) = {
    //don't require and exact match on the name, just the identifier
    val bank = MappedBank.find(By(MappedBank.national_identifier, bankNationalIdentifier)) match {
      case Full(b) =>
        logger.info(s"bank with id ${b.bankId} and national identifier ${b.nationalIdentifier} found")
        b
      case _ =>
        logger.info(s"creating bank with national identifier $bankNationalIdentifier")
        //TODO: need to handle the case where generatePermalink returns a permalink that is already used for another bank
        MappedBank.create
          .permalink(Helper.generatePermalink(bankName))
          .fullBankName(bankName)
          .shortBankName(bankName)
          .national_identifier(bankNationalIdentifier)
          .saveMe()
    }

    //TODO: pass in currency as a parameter?
    val account = createAccountIfNotExisting(bank.bankId, AccountId(UUID.randomUUID().toString), accountNumber, accountType, accountLabel, currency, 0L, accountHolderName)

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
    val narrativesDeleted = MappedNarrative.bulkDelete_!!(
      By(MappedNarrative.bank, bankId.value),
      By(MappedNarrative.account, accountId.value)
    )

    //delete narratives on transactions of this account
    val tagsDeleted = MappedTag.bulkDelete_!!(
      By(MappedTag.bank, bankId.value),
      By(MappedTag.account, accountId.value)
    )

    //delete WhereTags on transactions of this account
    val whereTagsDeleted = MappedWhereTag.bulkDelete_!!(
      By(MappedWhereTag.bank, bankId.value),
      By(MappedWhereTag.account, accountId.value)
    )

    //delete transaction images on transactions of this account
    val transactionImagesDeleted = MappedTransactionImage.bulkDelete_!!(
      By(MappedTransactionImage.bank, bankId.value),
      By(MappedTransactionImage.account, accountId.value)
    )

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
  override def createSandboxBankAccount(bankId: BankId, accountId: AccountId, accountNumber: String,
                                        accountType: String, accountLabel: String,
                                        currency: String, initialBalance: BigDecimal, accountHolderName: String): Box[BankAccount] = {

    for {
      bank <- getBank(bankId) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
    } yield {

      val balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
      createAccountIfNotExisting(bankId, accountId, accountNumber, accountType, accountLabel, currency, balanceInSmallestCurrencyUnits, accountHolderName)
    }

  }


  private def createAccountIfNotExisting(bankId: BankId, accountId: AccountId, accountNumber: String,
                                         accountType: String, accountLabel: String, currency: String,
                                         balanceInSmallestCurrencyUnits: Long, accountHolderName: String) : BankAccount = {
    getBankAccount(bankId, accountId) match {
      case Full(a) =>
        logger.info(s"account with id $accountId at bank with id $bankId already exists. No need to create a new one.")
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

  override def getProducts(bankId: BankId): Box[List[Product]] = {
    Full(MappedProduct.findAll(By(MappedProduct.mBankId, bankId.value)))
  }

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = {
    MappedProduct.find(
      By(MappedProduct.mBankId, bankId.value),
      By(MappedProduct.mCode, productCode.value)
    )
  }

  override def createOrUpdateBranch(branch: BranchJsonPost): Box[Branch] = {

    //check the branch existence and update or insert data
    getBranch(BankId(branch.bank_id), BranchId(branch.id)) match {
      case Full(mappedBranch) =>
        tryo {
          mappedBranch
            .mBranchId(branch.id)
            .mBankId(branch.bank_id)
            .mName(branch.name)
            .mLine1(branch.address.line_1)
            .mLine2(branch.address.line_2)
            .mLine3(branch.address.line_3)
            .mCity(branch.address.city)
            .mCounty(branch.address.country)
            .mState(branch.address.state)
            .mPostCode(branch.address.postcode)
            .mlocationLatitude(branch.location.latitude)
            .mlocationLongitude(branch.location.longitude)
            .mLicenseId(branch.meta.license.id)
            .mLicenseName(branch.meta.license.name)
            .mLobbyHours(branch.lobby.hours)
            .mDriveUpHours(branch.driveUp.hours)
            .saveMe()
        } ?~! ErrorMessages.CreateBranchUpdateError
      case _ =>
        tryo {
          MappedBranch.create
            .mBranchId(branch.id)
            .mBankId(branch.bank_id)
            .mName(branch.name)
            .mLine1(branch.address.line_1)
            .mLine2(branch.address.line_2)
            .mLine3(branch.address.line_3)
            .mCity(branch.address.city)
            .mCounty(branch.address.country)
            .mState(branch.address.state)
            .mPostCode(branch.address.postcode)
            .mlocationLatitude(branch.location.latitude)
            .mlocationLongitude(branch.location.longitude)
            .mLicenseId(branch.meta.license.id)
            .mLicenseName(branch.meta.license.name)
            .mLobbyHours(branch.lobby.hours)
            .mDriveUpHours(branch.driveUp.hours)
            .saveMe()
        } ?~! ErrorMessages.CreateBranchInsertError
    }
  }

  override def getBranch(bankId : BankId, branchId: BranchId) : Box[MappedBranch]= {
    MappedBranch.find(
      By(MappedBranch.mBankId, bankId.value),
      By(MappedBranch.mBranchId, branchId.value)
    )
  }

  override def getConsumerByConsumerId(consumerId: Long): Box[Consumer] = {
    Consumer.find(By(Consumer.id, consumerId))
  }

  /**
    * get the latest record from FXRate table by the fields: fromCurrencyCode and toCurrencyCode.
    * If it is not found by (fromCurrencyCode, toCurrencyCode) order, it will try (toCurrencyCode, fromCurrencyCode) order .
    */
  override def getCurrentFxRate(fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate]  = {
    /**
      * find FXRate by (fromCurrencyCode, toCurrencyCode), the normal order  
      */
    val fxRateFromTo = MappedFXRate.find(
      By(MappedFXRate.mFromCurrencyCode, fromCurrencyCode),
      By(MappedFXRate.mToCurrencyCode, toCurrencyCode)
    )
    /**
      * find FXRate by (toCurrencyCode, fromCurrencyCode), the reverse order
      */
    val fxRateToFrom = MappedFXRate.find(
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

  override def getTransactionRequestTypeCharges(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestTypes: List[TransactionRequestType]): Box[List[TransactionRequestTypeCharge]] = {
    Full(transactionRequestTypes.map(getTransactionRequestTypeCharge(bankId, accountId, viewId, _).get))
  }

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] = {
    Full(MappedCounterparty.findAll(By(MappedCounterparty.mThisAccountId, thisAccountId.value),
                                    By(MappedCounterparty.mThisBankId, thisBankId.value),
                                    By(MappedCounterparty.mThisViewId, viewId.value)))
  }
  
}

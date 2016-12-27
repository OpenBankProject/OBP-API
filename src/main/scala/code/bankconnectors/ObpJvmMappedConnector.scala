package code.bankconnectors

import java.text.SimpleDateFormat
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.{Date, Locale, Optional, UUID}

import code.api.util.ErrorMessages
import code.api.v2_1_0.BranchJsonPost
import code.fx.fx
import code.branches.Branches.{Branch, BranchId}
import code.branches.MappedBranch
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.comments.MappedComment
import code.metadata.counterparties.{Counterparties, CounterpartyTrait}
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.MappedTag
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.model._
import code.model.dataAccess._
import code.transaction.MappedTransaction
import code.transactionrequests.MappedTransactionRequest
import code.transactionrequests.TransactionRequests._
import code.util.{Helper, TTLCache}
import code.views.Views
import com.tesobe.obp.kafka.SimpleNorth
import com.tesobe.obp.transport.Transport
import com.tesobe.obp.transport.Transport.Factory
import net.liftweb.common._
import net.liftweb.json
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import net.liftweb.json._
import code.products.Products.{Product, ProductCode}
import scala.collection.JavaConversions._

/**
  * Uses the https://github.com/OpenBankProject/OBP-JVM library to connect to
  * bank resources.
  */
object ObpJvmMappedConnector extends Connector with Loggable {


  type JAccount = com.tesobe.obp.transport.Account
  type JBank = com.tesobe.obp.transport.Bank
  type JTransaction = com.tesobe.obp.transport.Transaction

  type JConnector = com.tesobe.obp.transport.Connector
  type JHashMap = java.util.HashMap[String, Object]

  type AccountType = ObpJvmBankAccount

  var jvmNorth : JConnector = null

  val producerProps : JHashMap = new JHashMap
  val consumerProps : JHashMap = new JHashMap

  consumerProps.put("bootstrap.servers",
    Props.get("kafka.host").openOr("localhost:9092"))
  producerProps.put("bootstrap.servers",
    Props.get("kafka.host").openOr("localhost:9092"))

  val factory = Transport.factory(Transport.Version.Nov2016, Transport.Encoding.json).get
  val north   = new SimpleNorth(
                    Props.get("kafka.response_topic").openOr("Response"),
                    Props.get("kafka.request_topic").openOr("Request"),
                    consumerProps, producerProps)
  jvmNorth = factory.connector(north)
  north.receive() // start ObpJvm
  logger.info(s"ObpJvmMappedConnector running")

  // Local TTL Cache
  val cacheTTL              = Props.get("connector.cache.ttl.seconds", "0").toInt
  val cachedUser            = TTLCache[ObpJvmInboundValidatedUser](cacheTTL)
  val cachedBank            = TTLCache[ObpJvmInboundBank](cacheTTL)
  val cachedAccount         = TTLCache[ObpJvmInboundAccount](cacheTTL)
  val cachedBanks           = TTLCache[List[ObpJvmInboundBank]](cacheTTL)
  val cachedAccounts        = TTLCache[List[ObpJvmInboundAccount]](cacheTTL)
  val cachedPublicAccounts  = TTLCache[List[ObpJvmInboundAccount]](cacheTTL)
  val cachedUserAccounts    = TTLCache[List[ObpJvmInboundAccount]](cacheTTL)

  implicit val formats = net.liftweb.json.DefaultFormats

  def toOptional[T](opt: Option[T]): Optional[T] = Optional.ofNullable(opt.getOrElse(null).asInstanceOf[T])
  def toOption[T](opt: Optional[T]): Option[T] = if (opt.isPresent) Some(opt.get()) else None

  def getUser( username: String, password: String ): Box[InboundUser] = {
    // We have no way of authenticating users
    logger.info(s"getUser username ${username} will do nothing and return null")
    null
  }

  def updateUserAccountViews( user: APIUser ) = {
    logger.debug(s"ObpJvm updateUserAccountViews for user.email ${user.email} user.name ${user.name}")
    val accounts: List[ObpJvmInboundAccount] = jvmNorth.getAccounts(null, user.name).map(a =>
        ObpJvmInboundAccount(
                             a.accountId,
                             a.bankId,
                             a.label,
                             a.number,
                             a.`type`,
                             ObpJvmInboundBalance(a.balanceAmount, a.balanceCurrency),
                             a.iban,
                             user.name :: Nil,
                             false,  //public_view
                             false, //accountants_view
                             false  //auditors_view
        )
    ).toList

    logger.debug(s"ObpJvm getUserAccounts says res is $accounts")

    val views = for {
      acc <- accounts
      username <- tryo {user.name}
      views <- tryo {createViews( BankId(acc.bank),
        AccountId(acc.id),
        acc.owners.contains(username),
        acc.generate_public_view,
        acc.generate_accountants_view,
        acc.generate_auditors_view
      )}
      existing_views <- tryo {Views.views.vend.views(new ObpJvmBankAccount(acc))}
    } yield {
      setAccountOwner(username, BankId(acc.bank), AccountId(acc.id), acc.owners)
      views.foreach(v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> updated view ${v.uid} for apiuser ${user} and account ${acc}")
      })
      existing_views.filterNot(_.users.contains(user.apiId)).foreach (v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> added apiuser ${user} to view ${v.uid} for account ${acc}")
      })
    }
  }


  //gets banks handled by this connector
  override def getBanks: List[Bank] = {
    val banks: List[Bank] = jvmNorth.getBanks().map(b =>
        ObpJvmBank(
          ObpJvmInboundBank(
            b.bankId,
            b.name,
            b.logo,
            b.url
          )
        )
      ).toList

    logger.debug(s"ObpJvm getBanks says res is $banks")
    // Return list of results
    banks
  }

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(userId: String, accountId: String, transactionRequestType: String, currency: String): (BigDecimal, String) = {
    var r:Option[ObpJvmInboundChallengeLevel] = None
    /*TODO Needs to be implemented in OBP-JVM
    var r:Option[ObpJvmInboundChallengeLevel] = jvmNorth.getChallengeThreshold(userId, accountId, transactionRequestType, currency).map(b =>
        ObpJvmInboundChallengeLevel(
          b.limit,
          b.currency
        )
      )
    */

    r match {
      // Check does the response data match the requested data
      case Some(x)  => (x.limit, x.currency)
      case _ => {
        val limit = BigDecimal("50")
        val rate = fx.exchangeRate ("EUR", currency)
        val convertedLimit = fx.convert(limit, rate)
        (convertedLimit, currency)
      }
    }
  }

  // Gets bank identified by bankId
  override def getBank(id: BankId): Box[Bank] = {
   toOption[JBank](jvmNorth.getBank(id.value)) match {
      case Some(b) => Full(ObpJvmBank(ObpJvmInboundBank(b.bankId, b.name, b.logo, b.url)))
      case None => Empty
    }
  }

  // Gets transaction identified by bankid, accountid and transactionId
  def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Box[Transaction] = {
    toOption[JTransaction](jvmNorth.getTransaction(bankId.value, accountId.value, transactionId.value, OBPUser.getCurrentUserUsername )) match {
      case Some(t) =>
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        createNewTransaction(ObpJvmInboundTransaction(
        t.transactionId,
        ObpJvmInboundAccountId(t.accountId, bankId.value),
        Option(ObpJvmInboundTransactionCounterparty(Option(t.counterpartyId), Option(t.counterpartyName))),
        ObpJvmInboundTransactionDetails(
          t.`type`,
          t.description,
          t.postedDate.format(formatter),
          t.completedDate.format(formatter),
          t.newBalanceAmount.toString,
          t.amount.toString
        )
      ))
      case None => Empty
    }
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
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
    val optionalParams : Seq[QueryParam[MappedTransaction]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
    val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountId.value)) ++ optionalParams
    implicit val formats = net.liftweb.json.DefaultFormats
    val rList: List[ObpJvmInboundTransaction] = jvmNorth.getTransactions(bankId.value, accountId.value, OBPUser.getCurrentUserUsername).map(t =>
      ObpJvmInboundTransaction(
            t.id,
            ObpJvmInboundAccountId(t.accountId, bankId.value),
            Option(ObpJvmInboundTransactionCounterparty(Option(t.counterpartyId), Option(t.counterpartyName))),
            ObpJvmInboundTransactionDetails(
              t.`type`,
              t.description,
              t.postedDate.format(formatter),
              t.completedDate.format(formatter),
              t.newBalanceAmount.toString,
              t.amount.toString
           )
      )
    ).toList


    // Check does the response data match the requested data
    val isCorrect = rList.forall(x=>x.this_account.id == accountId.value && x.this_account.bank == bankId.value)
    if (!isCorrect) {
      //rList.foreach(x=> println("====> x.this_account.id=" + x.this_account.id +":accountId.value=" + accountId.value +":x.this_account.bank=" + x.this_account.bank +":bankId.value="+ bankId.value) )
      throw new Exception(ErrorMessages.InvalidGetTransactionsConnectorResponse)
    }
    // Populate fields and generate result
    val res = for {
      r <- rList
      transaction <- createNewTransaction(r)
    } yield {
      transaction
    }
    Full(res)
    //TODO is this needed updateAccountTransactions(bankId, accountId)
  }

  override def getBankAccount(bankId: BankId, accountId: AccountId): Box[ObpJvmBankAccount] = {
     val account : Optional[JAccount] = jvmNorth.getAccount(bankId.value,
      accountId.value, OBPUser.getCurrentUserUsername)
    if(account.isPresent) {
      val a : JAccount = account.get
      val balance : ObpJvmInboundBalance = ObpJvmInboundBalance(a.balanceCurrency, a.balanceAmount)
      Full(
        ObpJvmBankAccount(
          ObpJvmInboundAccount(
            a.accountId,
            a.bankId,
            a.label,
            a.number,
            a.`type`,
            balance,
            a.iban,
            List(),
            true,
            true,
            true
          )
        )
      )
    } else {
      logger.info(s"getBankAccount says ! account.isPresent")
      Empty
    }
  }

  override def getBankAccounts(accts: List[(BankId, AccountId)]): List[ObpJvmBankAccount] = {

    logger.info(s"hello from ObpJvmMappedConnnector.getBankAccounts accts is $accts")

    val r:List[ObpJvmInboundAccount] = accts.map { a => {

      val primaryUserIdentifier = OBPUser.getCurrentUserUsername
      logger.info (s"ObpJvmMappedConnnector.getBankAccounts is calling jvmNorth.getAccount with params ${a._1.value} and  ${a._2.value} and primaryUserIdentifier is $primaryUserIdentifier")
      val account: Optional[JAccount] = jvmNorth.getAccount(a._1.value,
        a._2.value, primaryUserIdentifier)
      if (account.isPresent) {
        val a: JAccount = account.get
        val balance: ObpJvmInboundBalance = ObpJvmInboundBalance(a.balanceCurrency, a.balanceAmount)
        Full(ObpJvmInboundAccount(
          a.accountId,
          a.bankId,
          a.label,
          a.number,
          a.`type`,
          balance,
          a.iban,
          List(),
          true,
          true,
          true
        )
        )
      } else {
        Empty
      }
    }.get
  }

    // Check does the response data match the requested data
    //val accRes = for(row <- r) yield {
    //  (row.bank, row.id)
    //}
    //if ((accRes.toSet diff accts.toSet).size > 0) throw new Exception(ErrorMessages.InvalidGetBankAccountsConnectorResponse)

    r.map { t =>
      createMappedAccountDataIfNotExisting(t.bank, t.id, t.label)
      new ObpJvmBankAccount(t) }
  }

  private def getAccountByNumber(bankId : BankId, number : String) : Box[AccountType] = {
    val account : Optional[JAccount] = jvmNorth.getAccount(bankId.value,
      number, OBPUser.getCurrentUserUsername)
    if(account.isPresent) {
      val a : JAccount = account.get
      val balance : ObpJvmInboundBalance = ObpJvmInboundBalance(a.balanceCurrency, a.balanceAmount)
      Full(
        ObpJvmBankAccount(
          ObpJvmInboundAccount(
            a.accountId,
            a.bankId,
            a.label,
            a.number,
            a.`type`,
            balance,
            a.iban,
            List(),
            true,
            true,
            true
          )
        )
      )
    } else {
      Empty
    }
  }

  def getCounterpartyFromTransaction(thisAccountBankId : BankId, thisAccountId : AccountId, metadata : CounterpartyMetadata) : Box[Counterparty] = {
    //because we don't have a db backed model for Counterpartys, we need to construct it from an
    //CounterpartyMetadata and a transaction
    val t = getTransactions(thisAccountBankId, thisAccountId).map { t =>
      t.filter { e =>
        if (e.otherAccount.thisAccountId == metadata.getAccountNumber)
          true
        else
          false
      }
    }.get.head

    val res = new Counterparty(
      //counterparty id is defined to be the id of its metadata as we don't actually have an id for the counterparty itself
      counterPartyId = metadata.metadataId,
      label = metadata.getHolder,
      nationalIdentifier = t.otherAccount.nationalIdentifier,
      bankRoutingAddress = None,
      accountRoutingAddress = t.otherAccount.accountRoutingAddress,
      thisAccountId = AccountId(metadata.getAccountNumber),
      thisBankId = t.otherAccount.thisBankId,
      kind = t.otherAccount.kind,
      otherBankId = thisAccountBankId,
      otherAccountId = thisAccountId,
      alreadyFoundMetadata = Some(metadata),

      //TODO V210 following five fields are new, need to be fiexed
      name = "",
      bankRoutingScheme = "",
      accountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true

    )
    Full(res)
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
  /*
  private def updateAccountTransactions(bankId : BankId, accountId : AccountId) = {

    for {
      bank <- getBank(bankId)
      account <- getBankAccountType(bankId, accountId)
    } {
      spawn{
        val useMessageQueue = Props.getBool("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = Box!!account.lastUpdate match {
          case Full(l) => now after time(l.getTime + hours(Props.getInt("messageQueue.updateTransactionsInterval", 1)))
          case _ => true
        }
        //if(outDatedTransactions && useMessageQueue) {
        //  UpdatesRequestSender.sendMsg(UpdateBankAccount(account.number, bank.national_identifier.get))
        //}
      }
    }
  }
  */

  //gets the users who are the legal owners/holders of the account
  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] =
    MappedAccountHolder.findAll(
      By(MappedAccountHolder.accountBankPermalink, bankId.value),
      By(MappedAccountHolder.accountPermalink, accountId.value)).map(accHolder => accHolder.user.obj).flatten.toSet


  // Get all counterparties related to an account
  override def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId): List[Counterparty] =
    Counterparties.counterparties.vend.getMetadatas(bankId, accountId).flatMap(getCounterpartyFromTransaction(bankId, accountId, _))

  // Get one counterparty related to a bank account
  override def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyID: String): Box[Counterparty] =
    // Get the metadata and pass it to getCounterparty to construct the other account.
    Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterpartyID).flatMap(getCounterpartyFromTransaction(bankId, accountId, _))

  def getCounterparty(thisAccountBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = Empty

  def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait] =Empty

  override def getPhysicalCards(user: User): List[PhysicalCard] =
    List()

  override def getPhysicalCardsForBank(bank: Bank, user: User): List[PhysicalCard] =
    List()

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
    Empty
  }


  override def makePaymentImpl(fromAccount: AccountType, toAccount: AccountType, amt: BigDecimal, description : String): Box[TransactionId] = {
    val fromTransAmt = -amt //from account balance should decrease
    val toTransAmt = amt //to account balance should increase

    //we need to save a copy of this payment as a transaction in each of the accounts involved, with opposite amounts
    val sentTransactionId = saveTransaction(fromAccount, toAccount, fromTransAmt, description)
    saveTransaction(toAccount, fromAccount, toTransAmt, description)

    sentTransactionId
  }


  /**
   * Saves a transaction with amount @amt and counterparty @counterparty for account @account. Returns the id
   * of the saved transaction.
   */
  // TODO: Extend jvmNorth function with parameters transactionRequestId and description
private def saveTransaction(fromAccount: AccountType, toAccount: AccountType, amt: BigDecimal, description : String): Box[TransactionId] = {

    val name = OBPUser.getCurrentUserUsername
    val user = OBPUser.getApiUserByUsername(name)
    val userId = for (u <- user) yield u.userId
    val accountId = fromAccount.accountId.value
    val bankId = fromAccount.bankId.value
    val currency = fromAccount.currency
    val amount = amt.asInstanceOf[java.math.BigDecimal]
    val counterpartyId = toAccount.accountId.value
    val newBalanceCurrency = toAccount.currency
    val newBalanceAmount = toAccount.balance
    val counterpartyName = toAccount.name
    val transactionType = "AC"
    val completed = ZonedDateTime.of(1999, 1, 2, 0, 0, 0, 0, UTC)
    val posted = ZonedDateTime.of(1999, 1, 2, 0, 0, 0, 0, UTC)
    val transactionId = "1"
    val `type` = ""

    toOption[String](jvmNorth.createTransaction(accountId, amount, bankId, completed, counterpartyId, counterpartyName, currency,
      description, newBalanceAmount.bigDecimal, newBalanceCurrency, posted, transactionId,
      `type`, userId.getOrElse(""))) match {
      case Some(x) => Full(TransactionId(x))
      case None => Empty
    }
  }

  /*
    Transaction Requests
  */

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
      .mEndDate(now).saveMe
    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
  }

  override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                               account : BankAccount, details: String,
                                               status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    val mappedTransactionRequest = MappedTransactionRequest.create
      .mTransactionRequestId(transactionRequestId.value)
      .mType(transactionRequestType.value)
      .mFrom_BankId(account.bankId.value)
      .mFrom_AccountId(account.accountId.value)
      .mDetails(details)
      .mStatus(status)
      .mStartDate(now)
      .mEndDate(now).saveMe
    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
  }

  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = {
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
    val transactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    transactionRequest.flatMap(_.toTransactionRequest)
  }


  override def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
    //TODO: write logic / data access
    Full(List(TransactionRequestType("SANDBOX_TAN")))
  }

  /*
    Bank account creation
   */

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  //again assume national identifier is unique
  override def createBankAndAccount(bankName: String, bankNationalIdentifier: String, accountNumber: String,
                                    accountType: String, accountLabel: String,  currency: String, accountHolderName: String): (Bank, BankAccount) = {
    //don't require and exact match on the name, just the identifier
    val bank: Bank = MappedBank.find(By(MappedBank.national_identifier, bankNationalIdentifier)) match {
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
    val account = createAccountIfNotExisting(
      bank.bankId,
      AccountId(UUID.randomUUID().toString),
      accountNumber,
      accountType,
      accountLabel,
      currency,
      0L,
      accountHolderName
    )

    (bank, account)
  }

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String): Boolean = {
    getAccountByNumber(bankId, accountNumber) != null
  }

  //remove an account and associated transactions
  override def removeAccount(bankId: BankId, accountId: AccountId) : Boolean = {
    //delete comments on transactions of this account
    val commentsDeleted = MappedComment.bulkDelete_!!(
      By(MappedComment.bank, bankId.value),
      By(MappedComment.account, accountId.value)
    )

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
    val account = getBankAccount(bankId, accountId)

    val accountDeleted = account match {
      case acc => true //acc.delete_! //TODO
      case _ => false
    }

    commentsDeleted && narrativesDeleted && tagsDeleted && whereTagsDeleted && transactionImagesDeleted &&
      transactionsDeleted && privilegesDeleted && viewsDeleted && accountDeleted
}

  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  override def createSandboxBankAccount(bankId: BankId, accountId: AccountId, accountNumber: String,
                                        accountType: String, accountLabel: String, currency: String,
                                        initialBalance: BigDecimal, accountHolderName: String): Box[BankAccount] = {

    for {
      bank <- getBank(bankId) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
    } yield {

      val balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
      createAccountIfNotExisting(bankId, accountId, accountNumber, accountType, accountLabel, currency, balanceInSmallestCurrencyUnits, accountHolderName)
    }

  }

  //sets a user as an account owner/holder
  override def setAccountHolder(bankAccountUID: BankAccountUID, user: User): Unit = {
    MappedAccountHolder.createMappedAccountHolder(user.apiId.value, bankAccountUID.accountId.value, bankAccountUID.bankId.value)
  }

  private def createAccountIfNotExisting(bankId: BankId, accountId: AccountId, accountNumber: String,
                                         accountType: String, accountLabel: String, currency: String,
                                         balanceInSmallestCurrencyUnits: Long, accountHolderName: String) : BankAccount = {
    getBankAccount(bankId, accountId) match {
      case Full(a) =>
        logger.info(s"account with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        a
      case _ => null //TODO
        /*
       new  ObpJvmBankAccount
          .bank(bankId.value)
          .theAccountId(accountId.value)
          .accountNumber(accountNumber)
          .accountType(accountType)
          .accountLabel(accountLabel)
          .accountCurrency(currency)
          .accountBalance(balanceInSmallestCurrencyUnits)
          .holder(accountHolderName)
          .saveMe()
          */
    }
  }

  private def createMappedAccountDataIfNotExisting(bankId: String, accountId: String, label: String) : Boolean = {
    MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId),
                                    By(MappedBankAccountData.bankId, bankId)) match {
      case Empty =>
        val data = new MappedBankAccountData
        data.setAccountId(accountId)
        data.setBankId(bankId)
        data.setLabel(label)
        data.save()
        true
      case _ =>
        logger.info(s"account data with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        false
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
      bank <- getBank(bankId)
    } yield {
      //acc.balance = newBalance
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


  private val bigDecimalFailureHandler : PartialFunction[Throwable, Unit] = {
    case ex : NumberFormatException => {
      logger.warn(s"could not convert amount to a BigDecimal: $ex")
    }
  }

  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, counterpartyHolder: String): Int = {
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
        By(MappedTransaction.counterpartyAccountHolder, counterpartyHolder))
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
      counterparty = obpTransaction.other_account
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
        .counterpartyAccountNumber(counterparty.number)
        .counterpartyAccountHolder(counterparty.holder)
        .counterpartyAccountKind(counterparty.kind)
        .counterpartyNationalId(counterparty.bank.national_identifier)
        .counterpartyBankName(counterparty.bank.name)
        .counterpartyIban(counterparty.bank.IBAN)
        .saveMe()
      transaction <- mappedTransaction.toTransaction(account)
    } yield transaction
  }

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Boolean = {
    val result = for {
      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
      account <- getAccountByNumber(bankId, accountNumber)
    } yield {
        val acc = getBankAccount(bankId, account.accountId)
        acc match {
          case a => true //a.lastUpdate = updateDate //TODO
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
      bank <- getBank(bankId)
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId.value), By(MappedBankAccountData.bankId, bank.bankId.value))
    } yield {
      d.setLabel(label)
      d.save()
    }
    result.getOrElse(false)
  }




  /////////////////////////////////////////////////////////////////////////////



  // Helper for creating a transaction
  def createNewTransaction(r: ObpJvmInboundTransaction):Box[Transaction] = {
    var datePosted: Date = null
    if (r.details.posted != null) // && r.details.posted.matches("^[0-9]{8}$"))
      datePosted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(r.details.posted)

    var dateCompleted: Date = null
    if (r.details.completed != null) // && r.details.completed.matches("^[0-9]{8}$"))
      dateCompleted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(r.details.completed)

    for {
        counterparty <- tryo{r.counterparty}
        thisAccount <- getBankAccount(BankId(r.this_account.bank), AccountId(r.this_account.id))
        //creates a dummy Counterparty without an CounterpartyMetadata, which results in one being generated (in Counterparty init)
        dummyCounterparty <- tryo{createCounterparty(counterparty.get, thisAccount, None)}
        //and create the proper Counterparty with the correct "id" attribute set to the metadataId of the CounterpartyMetadata object
        //note: as we are passing in the CounterpartyMetadata we don't incur another db call to get it in Counterparty init
        counterparty <- tryo{createCounterparty(counterparty.get, thisAccount, Some(dummyCounterparty.metadata))}
      } yield {

        // Fix balance if null
        val new_balance = if (r.details.new_balance != null)
          r.details.new_balance
        else
          "0.0"

        // Create new transaction
        new Transaction(
          r.id,                             // uuid:String
          TransactionId(r.id),              // id:TransactionId
          thisAccount,                      // thisAccount:BankAccount
          counterparty,                     // counterparty:Counterparty
          r.details.`type`,                 // transactionType:String
          BigDecimal(r.details.value),      // val amount:BigDecimal
          thisAccount.currency,             // currency:String
          Some(r.details.description),      // description:Option[String]
          datePosted,                       // startDate:Date
          dateCompleted,                    // finishDate:Date
          BigDecimal(new_balance)           // balance:BigDecimal)
        )
    }
  }


  case class ObpJvmBank(r: ObpJvmInboundBank) extends Bank {
    def fullName           = r.name
    def shortName          = r.name
    def logoUrl            = r.logo
    def bankId             = BankId(r.bankId)
    def nationalIdentifier = "None"  //TODO
    def swiftBic           = "None"  //TODO
    def websiteUrl         = r.website
  }

  // Helper for creating other bank account
  def createCounterparty(c: ObpJvmInboundTransactionCounterparty, o: ObpJvmBankAccount, alreadyFoundMetadata : Option[CounterpartyMetadata]) = {
    new Counterparty(
      counterPartyId = alreadyFoundMetadata.map(_.metadataId).getOrElse(""),
      label = c.account_number.getOrElse(c.name.getOrElse("")),
      nationalIdentifier = "",
      bankRoutingAddress = None,
      accountRoutingAddress = None,
      thisAccountId = AccountId(c.account_number.getOrElse("")),
      thisBankId = BankId(""),
      kind = "",
      otherBankId = o.bankId,
      otherAccountId = o.accountId,
      alreadyFoundMetadata = alreadyFoundMetadata,

      //TODO V210 following five fields are new, need to be fiexed
      name = "",
      bankRoutingScheme = "",
      accountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true


    )
  }

  case class ObpJvmBankAccount(r: ObpJvmInboundAccount) extends BankAccount {
    def accountId : AccountId       = AccountId(r.id)
    def accountType : String        = r.`type`
    def balance : BigDecimal        = BigDecimal(r.balance.amount)
    def currency : String           = r.balance.currency
    def name : String               = r.owners.head
    def swift_bic : Option[String]  = Some("swift_bic") //TODO
    def iban : Option[String]       = Some(r.IBAN)
    def number : String             = r.number
    def bankId : BankId             = BankId(r.bank)
    def lastUpdate : Date           = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(today.getTime.toString)
    def accountHolder : String      = r.owners.head

    // Fields modifiable from OBP are stored in mapper
    def label : String              = (for {
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, r.id))
    } yield {
      d.getLabel
    }).getOrElse(r.number)

  }


  case class ObpJvmInboundBank(
                              bankId : String,
                              name : String,
                              logo : String,
                              website : String)


  /** Bank Branches
    *
    * @param id Uniquely identifies the Branch within the Bank. SHOULD be url friendly (no spaces etc.) Used in URLs
    * @param bank_id MUST match bank_id in Banks
    * @param name Informal name for the Branch
    * @param address Address
    * @param location Geolocation
    * @param meta Meta information including the license this information is published under
    * @param lobby Info about when the lobby doors are open
    * @param driveUp Info about when automated facilities are open e.g. cash point machine
    */
  case class ObpJvmInboundBranch(
                                 id : String,
                                 bank_id: String,
                                 name : String,
                                 address : ObpJvmInboundAddress,
                                 location : ObpJvmInboundLocation,
                                 meta : ObpJvmInboundMeta,
                                 lobby : Option[ObpJvmInboundLobby],
                                 driveUp : Option[ObpJvmInboundDriveUp])

  case class ObpJvmInboundLicense(
                                 id : String,
                                 name : String)

  case class ObpJvmInboundMeta(
                              license : ObpJvmInboundLicense)

  case class ObpJvmInboundLobby(
                               hours : String)

  case class ObpJvmInboundDriveUp(
                                 hours : String)

  /**
    *
    * @param line_1 Line 1 of Address
    * @param line_2 Line 2 of Address
    * @param line_3 Line 3 of Address
    * @param city City
    * @param county County i.e. Division of State
    * @param state State i.e. Division of Country
    * @param post_code Post Code or Zip Code
    * @param country_code 2 letter country code: ISO 3166-1 alpha-2
    */
  case class ObpJvmInboundAddress(
                                 line_1 : String,
                                 line_2 : String,
                                 line_3 : String,
                                 city : String,
                                 county : String, // Division of State
                                 state : String, // Division of Country
                                 post_code : String,
                                 country_code: String)

  case class ObpJvmInboundLocation(
                                  latitude : Double,
                                  longitude : Double)

  case class ObpJvmInboundValidatedUser(
                                       email : String,
                                       display_name : String)

  case class ObpJvmInboundAccount(
                                  id : String,
                                  bank : String,
                                  label : String,
                                  number : String,
                                  `type` : String,
                                  balance : ObpJvmInboundBalance,
                                  IBAN : String,
                                  owners : List[String],
                                  generate_public_view : Boolean,
                                  generate_accountants_view : Boolean,
                                  generate_auditors_view : Boolean)

  case class ObpJvmInboundBalance(
                                 currency : String,
                                 amount : String)

  case class ObpJvmInboundTransaction(
                                      id : String,
                                      this_account : ObpJvmInboundAccountId,
                                      counterparty : Option[ObpJvmInboundTransactionCounterparty],
                                      details : ObpJvmInboundTransactionDetails)

  case class ObpJvmInboundTransactionCounterparty(
                                           name : Option[String],  // Also known as Label
                                           account_number : Option[String])

  case class ObpJvmInboundAccountId(
                                   id : String,
                                   bank : String)

  case class ObpJvmInboundTransactionDetails(
                                        `type` : String,
                                        description : String,
                                        posted : String,
                                        completed : String,
                                        new_balance : String,
                                        value : String)


  case class ObpJvmInboundAtm(
                              id : String,
                              bank_id: String,
                              name : String,
                              address : ObpJvmInboundAddress,
                              location : ObpJvmInboundLocation,
                              meta : ObpJvmInboundMeta
                           )


  case class ObpJvmInboundProduct(
                                 bank_id : String,
                                 code: String,
                                 name : String,
                                 category : String,
                                 family : String,
                                 super_family : String,
                                 more_info_url : String,
                                 meta : ObpJvmInboundMeta
                               )


  case class ObpJvmInboundAccountData(
                                      banks : List[ObpJvmInboundBank],
                                      users : List[InboundUser],
                                      accounts : List[ObpJvmInboundAccount]
                                   )

  // We won't need this. TODO clean up.
  case class ObpJvmInboundData(
                               banks : List[ObpJvmInboundBank],
                               users : List[InboundUser],
                               accounts : List[ObpJvmInboundAccount],
                               transactions : List[ObpJvmInboundTransaction],
                               branches: List[ObpJvmInboundBranch],
                               atms: List[ObpJvmInboundAtm],
                               products: List[ObpJvmInboundProduct],
                               crm_events: List[ObpJvmInboundCrmEvent]
                            )


  case class ObpJvmInboundCrmEvent(
                                   id : String, // crmEventId
                                   bank_id : String,
                                   customer: ObpJvmInboundCustomer,
                                   category : String,
                                   detail : String,
                                   channel : String,
                                   actual_date: String
                                 )

  case class ObpJvmInboundCustomer(
                                   name: String,
                                   number : String // customer number, also known as ownerId (owner of accounts) aka API User?
                                 )


  case class ObpJvmInboundTransactionId(
                                        transactionId : String
                                      )
  case class ObpJvmOutboundTransaction(username: String,
                                      accountId: String,
                                      currency: String,
                                      amount: String,
                                      counterpartyId: String,
                                      counterpartyCurrency: String,
                                      transactionType: String)


  case class ObpJvmInboundChallengeLevel(
                                       limit: BigDecimal,
                                       currency: String
                                        )

  override def getProducts(bankId: BankId): Box[List[Product]] = Empty

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = Empty

  override  def createOrUpdateBranch(branch: BranchJsonPost ): Box[Branch] = Empty

  override def getBranch(bankId : BankId, branchId: BranchId) : Box[MappedBranch]= Empty
}


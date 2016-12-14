package code.bankconnectors

import java.text.SimpleDateFormat
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.{Date, Locale, Optional, UUID}

import code.api.util.ErrorMessages
import code.api.v2_1_0.{BranchJsonPost}
import code.fx.fx
import code.bankconnectors.KafkaMappedConnector.{KafkaBank, KafkaBankAccount, KafkaInboundAccount, KafkaInboundBank}
import code.branches.Branches.{Branch, BranchId}
import code.branches.MappedBranch
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.comments.MappedComment
import code.metadata.counterparties.Counterparties
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.MappedTag
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.model._
import code.model.dataAccess._
import code.sandbox.{CreateViewImpls, Saveable}
import code.sandbox.{CreateViewImpls, Saveable}
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
  * Kafka.
  */
object KafkaLibMappedConnector extends Connector with CreateViewImpls with Loggable {

  type JAccount = com.tesobe.obp.transport.Account
  type JBank = com.tesobe.obp.transport.Bank
  type JTransaction = com.tesobe.obp.transport.Transaction

  type JConnector = com.tesobe.obp.transport.Connector
  type JHashMap = java.util.HashMap[String, Object]

  val producerProps : JHashMap = new JHashMap
  val consumerProps : JHashMap = new JHashMap

  // todo better way of translating scala props to java properties for kafka

  consumerProps.put("bootstrap.servers", Props.get("kafka.host").openOr("localhost:9092"))
  producerProps.put("bootstrap.servers", Props.get("kafka.host").openOr("localhost:9092"))

  val factory : Factory = Transport.defaultFactory()
  val north: SimpleNorth = new SimpleNorth(
    Props.get("kafka.response_topic").openOr("Response"),
    Props.get("kafka.request_topic").openOr("Request"),
    consumerProps, producerProps)
  val jvmNorth : JConnector = factory.connector(north)

  north.receive() // start Kafka

  type AccountType = KafkaBankAccount

  // Local TTL Cache
  val cacheTTL              = Props.get("kafka.cache.ttl.seconds", "3").toInt
  val cachedUser            = TTLCache[KafkaInboundValidatedUser](cacheTTL)
  val cachedBank            = TTLCache[KafkaInboundBank](cacheTTL)
  val cachedAccount         = TTLCache[KafkaInboundAccount](cacheTTL)
  val cachedBanks           = TTLCache[List[KafkaInboundBank]](cacheTTL)
  val cachedAccounts        = TTLCache[List[KafkaInboundAccount]](cacheTTL)
  val cachedPublicAccounts  = TTLCache[List[KafkaInboundAccount]](cacheTTL)
  val cachedUserAccounts    = TTLCache[List[KafkaInboundAccount]](cacheTTL)

  implicit val formats = net.liftweb.json.DefaultFormats

  def toOptional[T](opt: Option[T]): Optional[T] = Optional.ofNullable(opt.getOrElse(null).asInstanceOf[T])
  def toOption[T](opt: Optional[T]): Option[T] = if (opt.isPresent) Some(opt.get()) else None

  def getUser( username: String, password: String ): Box[InboundUser] = {
    // We have no way of authenticating users
    logger.info(s"getUser username ${username} will do nothing and return null")
    null
  }

  def accountOwnerExists(user: APIUser, account: KafkaInboundAccount): Boolean = {
    val res =
      MappedAccountHolder.findAll(
        By(MappedAccountHolder.user, user),
        By(MappedAccountHolder.accountBankPermalink, account.bank),
        By(MappedAccountHolder.accountPermalink, account.id)
      )

    res.nonEmpty
  }

  def setAccountOwner(owner : String, account: KafkaInboundAccount) : Unit = {
    if (account.owners.contains(owner)) {
      val apiUserOwner = APIUser.findAll.find(user => owner == user.name)
      apiUserOwner match {
        case Some(o) => {
          if ( ! accountOwnerExists(o, account)) {
            logger.info(s"setAccountOwner account owner does not exist. creating for ${o.apiId.value} ${account.id}")
            MappedAccountHolder.createMappedAccountHolder(o.apiId.value, account.bank, account.id, "KafkaLibMappedConnector")
          }
       }
        case None => {
          //This shouldn't happen as OBPUser should generate the APIUsers when saved
          logger.error(s"api user(s) with username $owner not found.")
       }
      }
    }
  }

  def updateUserAccountViews( user: APIUser ) = {
    logger.debug(s"KafkaLib updateUserAccountViews for user.email ${user.email} user.name ${user.name}")
    val accounts: List[KafkaInboundAccount] = jvmNorth.getAccounts(null, user.name).map(a =>
        KafkaInboundAccount(
                             a.id,
                             a.bank,
                             a.label,
                             a.number,
                             a.`type`,
                             KafkaInboundBalance(a.amount, a.currency),
                             a.iban,
                             user.name :: Nil,
                             false,  //public_view
                             false, //accountants_view
                             false  //auditors_view
        )
    ).toList

    logger.debug(s"Kafka getUserAccounts says res is $accounts")

    for {
      acc <- accounts
      username <- tryo {user.name}
      views <- tryo {createSaveableViews(acc, acc.owners.contains(username))}
      existing_views <- tryo {Views.views.vend.views(new KafkaBankAccount(acc))}
    } yield {
      setAccountOwner(username, acc)
      views.foreach(_.save())
      views.map(_.value).foreach(v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> updated view ${v.uid} for apiuser ${user} and account ${acc}")
      })
      existing_views.filterNot(_.users.contains(user)).foreach (v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> added apiuser ${user} to view ${v.uid} for account ${acc}")
      })
    }
  }

  def viewExists(account: KafkaInboundAccount, name: String): Boolean = {
    val res =
      ViewImpl.findAll(
        By(ViewImpl.bankPermalink, account.bank),
        By(ViewImpl.accountPermalink, account.id),
        By(ViewImpl.name_, name)
      )
    res.nonEmpty
  }

  def createSaveableViews(acc : KafkaInboundAccount, owner: Boolean = false) : List[Saveable[ViewType]] = {
    logger.info(s"Kafka createSaveableViews acc is $acc")
    val bankId = BankId(acc.bank)
    val accountId = AccountId(acc.id)

    val ownerView =
      if(owner && ! viewExists(acc, "Owner")) {
        logger.info("Creating owner view")
        Some(createSaveableOwnerView(bankId, accountId))
      }
      else None

    val publicView =
      if(acc.generate_public_view && ! viewExists(acc, "Public")) {
        logger.info("Creating public view")
        Some(createSaveablePublicView(bankId, accountId))
      }
      else None

    val accountantsView =
      if(acc.generate_accountants_view && ! viewExists(acc, "Accountant")) {
        logger.info("Creating accountants view")
        Some(createSaveableAccountantsView(bankId, accountId))
      }
      else None

    val auditorsView =
      if(acc.generate_auditors_view && ! viewExists(acc, "Auditor") ) {
        logger.info("Creating auditors view")
        Some(createSaveableAuditorsView(bankId, accountId))
      }
      else None

    List(ownerView, publicView, accountantsView, auditorsView).flatten
  }


  //gets banks handled by this connector
  override def getBanks: List[Bank] = {
    val banks: List[Bank] = jvmNorth.getBanks().map(b =>
        KafkaBank(
          KafkaInboundBank(
            b.id,
            b.shortName,
            b.fullName,
            b.logo,
            b.url
          )
        )
      ).toList

    logger.debug(s"Kafka getBanks says res is $banks")
    // Return list of results
    banks
  }

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(userId: String, accountId: String, transactionRequestType: String, currency: String): (BigDecimal, String) = {
    var r:Option[KafkaInboundChallengeLevel] = None
    /*TODO Needs to be implemented in OBP-JVM
    var r:Option[KafkaInboundChallengeLevel] = jvmNorth.getChallengeThreshold(userId, accountId, transactionRequestType, currency).map(b =>
        KafkaInboundChallengeLevel(
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
      case Some(b) => Full(KafkaBank(KafkaInboundBank(
       b.id, 
       b.shortName,
       b.fullName, 
       b.logo, 
       b.url)))
      case None => Empty
    }
  }

  // Gets transaction identified by bankid, accountid and transactionId
  def getTransaction(bankId: BankId, accountID: AccountId, transactionId: TransactionId): Box[Transaction] = {
    toOption[JTransaction](jvmNorth.getTransaction(bankId.value, accountID.value, transactionId.value, OBPUser.getCurrentUserUsername )) match {
      case Some(t) =>
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        createNewTransaction(KafkaInboundTransaction(
        t.id,
        KafkaInboundAccountId(t.account, bankId.value),
        Option(KafkaInboundTransactionCounterparty(Option(t.otherId), Option(t.otherAccount))),
        KafkaInboundTransactionDetails(
          t.`type`,
          t.description,
          t.posted.format(formatter),
          t.completed.format(formatter),
          t.balance,
          t.value
        )
      ))
      case None => Empty
    }
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
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
    val optionalParams : Seq[QueryParam[MappedTransaction]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
    val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountID.value)) ++ optionalParams
    implicit val formats = net.liftweb.json.DefaultFormats
    val rList: List[KafkaInboundTransaction] = jvmNorth.getTransactions(bankId.value, accountID.value, OBPUser.getCurrentUserUsername).map(t =>
      KafkaInboundTransaction(
            t.id,
            KafkaInboundAccountId(t.account, bankId.value),
            Option(KafkaInboundTransactionCounterparty(Option(t.otherId), Option(t.otherAccount))),
            KafkaInboundTransactionDetails(
              t.`type`,
              t.description,
              t.posted.format(formatter),
              t.completed.format(formatter),
              t.balance,
              t.value
           )
      )
    ).toList


    // Check does the response data match the requested data
    val isCorrect = rList.forall(x=>x.this_account.id == accountID.value && x.this_account.bank == bankId.value)
    if (!isCorrect) {
      //rList.foreach(x=> println("====> x.this_account.id=" + x.this_account.id +":accountID.value=" + accountID.value +":x.this_account.bank=" + x.this_account.bank +":bankId.value="+ bankId.value) )
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
    //TODO is this needed updateAccountTransactions(bankId, accountID)
  }

  override def getBankAccount(bankId: BankId, accountID: AccountId): Box[KafkaBankAccount] = {
     val account : Optional[JAccount] = jvmNorth.getAccount(bankId.value,
      accountID.value, OBPUser.getCurrentUserUsername)
    if(account.isPresent) {
      val a : JAccount = account.get
      val balance : KafkaInboundBalance = KafkaInboundBalance(a.currency, a.amount)
      Full(
        KafkaBankAccount(
          KafkaInboundAccount(
            a.id,
            a.bank,
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

  override def getBankAccounts(accts: List[(BankId, AccountId)]): List[KafkaBankAccount] = {

    logger.info(s"hello from KafkaLibMappedConnnector.getBankAccounts accts is $accts")

    val r:List[KafkaInboundAccount] = accts.map { a => {

      val primaryUserIdentifier = OBPUser.getCurrentUserUsername
      logger.info (s"KafkaLibMappedConnnector.getBankAccounts is calling jvmNorth.getAccount with params ${a._1.value} and  ${a._2.value} and primaryUserIdentifier is $primaryUserIdentifier")
      val account: Optional[JAccount] = jvmNorth.getAccount(a._1.value,
        a._2.value, primaryUserIdentifier)
      if (account.isPresent) {
        val a: JAccount = account.get
        val balance: KafkaInboundBalance = KafkaInboundBalance(a.currency, a.amount)
        Full(KafkaInboundAccount(
          a.id,
          a.bank,
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
      new KafkaBankAccount(t) }
  }

  private def getAccountByNumber(bankId : BankId, number : String) : Box[AccountType] = {
    val account : Optional[JAccount] = jvmNorth.getAccount(bankId.value,
      number, OBPUser.getCurrentUserUsername)
    if(account.isPresent) {
      val a : JAccount = account.get
      val balance : KafkaInboundBalance = KafkaInboundBalance(a.currency, a.amount)
      Full(
        KafkaBankAccount(
          KafkaInboundAccount(
            a.id,
            a.bank,
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
        if (e.otherAccount.otherBankId == metadata.getAccountNumber)
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
      otherBankId = metadata.getAccountNumber,
      thisBankId = t.otherAccount.thisBankId,
      kind = t.otherAccount.kind,
      thisAccountId = thisAccountBankId,
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
  private def updateAccountTransactions(bankId : BankId, accountID : AccountId) = {

    for {
      bank <- getBank(bankId)
      account <- getBankAccountType(bankId, accountID)
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
  override def getAccountHolders(bankId: BankId, accountID: AccountId): Set[User] =
    MappedAccountHolder.findAll(
      By(MappedAccountHolder.accountBankPermalink, bankId.value),
      By(MappedAccountHolder.accountPermalink, accountID.value)).map(accHolder => accHolder.user.obj).flatten.toSet


  // Get all counterparties related to an account
  override def getCounterpartiesFromTransaction(bankId: BankId, accountID: AccountId): List[Counterparty] =
    Counterparties.counterparties.vend.getMetadatas(bankId, accountID).flatMap(getCounterpartyFromTransaction(bankId, accountID, _))

  // Get one counterparty related to a bank account
  override def getCounterpartyFromTransaction(bankId: BankId, accountID: AccountId, counterpartyID: String): Box[Counterparty] =
    // Get the metadata and pass it to getCounterparty to construct the other account.
    Counterparties.counterparties.vend.getMetadata(bankId, accountID, counterpartyID).flatMap(getCounterpartyFromTransaction(bankId, accountID, _))

  def getCounterparty(thisAccountBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = Empty

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
  private def saveTransaction(fromAccount: AccountType, toAccount: AccountType, amt: BigDecimal, description : String): Box[TransactionId] = {

    val name = OBPUser.getCurrentUserUsername
    val user = OBPUser.getApiUserByUsername(name)
    val userId = for (u <- user) yield u.userId

    val transactionType = "AC"
    val transactionAmount = amt.bigDecimal
    val transactionCurrency = fromAccount.currency 

    val accountId = fromAccount.accountId.value
    val accountName = fromAccount.label
    val accountBankId= fromAccount.bankId.value
    val accountCurrency = fromAccount.currency

    val counterpartyId = toAccount.accountId.value
    val counterpartyName = toAccount.name
    val counterpartyBank = toAccount.bankId.value
    val counterpartyCurrency = toAccount.currency

    val datePosted = ZonedDateTime.now
    val dateCompleted = ZonedDateTime.now

    toOption[String](
      jvmNorth.createTransaction(
        accountId,
        transactionAmount,
        accountBankId,
        dateCompleted,
        counterpartyId,
        counterpartyName,
        accountCurrency,
        description,
        transactionAmount,
        counterpartyCurrency,
        datePosted,
        "",
        transactionType,
        userId.getOrElse("")
      )
    ) match {
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
  override def removeAccount(bankId: BankId, accountID: AccountId) : Boolean = {
    //delete comments on transactions of this account
    val commentsDeleted = MappedComment.bulkDelete_!!(
      By(MappedComment.bank, bankId.value),
      By(MappedComment.account, accountID.value)
    )

    //delete narratives on transactions of this account
    val narrativesDeleted = MappedNarrative.bulkDelete_!!(
      By(MappedNarrative.bank, bankId.value),
      By(MappedNarrative.account, accountID.value)
    )

    //delete narratives on transactions of this account
    val tagsDeleted = MappedTag.bulkDelete_!!(
      By(MappedTag.bank, bankId.value),
      By(MappedTag.account, accountID.value)
    )

    //delete WhereTags on transactions of this account
    val whereTagsDeleted = MappedWhereTag.bulkDelete_!!(
      By(MappedWhereTag.bank, bankId.value),
      By(MappedWhereTag.account, accountID.value)
    )

    //delete transaction images on transactions of this account
    val transactionImagesDeleted = MappedTransactionImage.bulkDelete_!!(
      By(MappedTransactionImage.bank, bankId.value),
      By(MappedTransactionImage.account, accountID.value)
    )

    //delete transactions of account
    val transactionsDeleted = MappedTransaction.bulkDelete_!!(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountID.value)
    )

    //remove view privileges (get views first)
    val views = ViewImpl.findAll(
      By(ViewImpl.bankPermalink, bankId.value),
      By(ViewImpl.accountPermalink, accountID.value)
    )

    //loop over them and delete
    var privilegesDeleted = true
    views.map (x => {
      privilegesDeleted &&= ViewPrivileges.bulkDelete_!!(By(ViewPrivileges.view, x.id_))
    })

    //delete views of account
    val viewsDeleted = ViewImpl.bulkDelete_!!(
      By(ViewImpl.bankPermalink, bankId.value),
      By(ViewImpl.accountPermalink, accountID.value)
    )

    //delete account
    val account = getBankAccount(bankId, accountID)

    val accountDeleted = account match {
      case acc => true //acc.delete_! //TODO
      case _ => false
    }

    commentsDeleted && narrativesDeleted && tagsDeleted && whereTagsDeleted && transactionImagesDeleted &&
      transactionsDeleted && privilegesDeleted && viewsDeleted && accountDeleted
}

  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  override def createSandboxBankAccount(bankId: BankId, accountID: AccountId, accountNumber: String,
                                        accountType: String, accountLabel: String, currency: String,
                                        initialBalance: BigDecimal, accountHolderName: String): Box[BankAccount] = {

    for {
      bank <- getBank(bankId) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
    } yield {

      val balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
      createAccountIfNotExisting(bankId, accountID, accountNumber, accountType, accountLabel, currency, balanceInSmallestCurrencyUnits, accountHolderName)
    }

  }

  //sets a user as an account owner/holder
  override def setAccountHolder(bankAccountUID: BankAccountUID, user: User): Unit = {
    MappedAccountHolder.createMappedAccountHolder(user.apiId.value, bankAccountUID.accountId.value, bankAccountUID.bankId.value)
  }

  private def createAccountIfNotExisting(bankId: BankId, accountID: AccountId, accountNumber: String,
                                         accountType: String, accountLabel: String, currency: String,
                                         balanceInSmallestCurrencyUnits: Long, accountHolderName: String) : BankAccount = {
    getBankAccount(bankId, accountID) match {
      case Full(a) =>
        logger.info(s"account with id $accountID at bank with id $bankId already exists. No need to create a new one.")
        a
      case _ => null //TODO
        /*
       new  KafkaBankAccount
          .bank(bankId.value)
          .theAccountId(accountID.value)
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
    MappedKafkaBankAccountData.find(By(MappedKafkaBankAccountData.accountId, accountId),
                                    By(MappedKafkaBankAccountData.bankId, bankId)) match {
      case Empty =>
        val data = new MappedKafkaBankAccountData
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
  override def updateAccountBalance(bankId: BankId, accountID: AccountId, newBalance: BigDecimal): Boolean = {

    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountID)
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


  override def updateAccountLabel(bankId: BankId, accountID: AccountId, label: String): Boolean = {
    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountID)
      bank <- getBank(bankId)
      d <- MappedKafkaBankAccountData.find(By(MappedKafkaBankAccountData.accountId, accountID.value), By(MappedKafkaBankAccountData.bankId, bank.bankId.value))
    } yield {
      d.setLabel(label)
      d.save()
    }
    result.getOrElse(false)
  }




  /////////////////////////////////////////////////////////////////////////////



  def process(reqId: String, command: String, argList: Map[String,String]): json.JValue = { //List[Map[String,String]] = {
    //if (producer.send(reqId, command, argList, "1")) {
      // Request sent, now we wait for response with the same reqId
    //  val res = consumer.getResponse(reqId)
    //  return res
    //}
    return json.parse("""{"error":"could not send message to kafka"}""")
  }


  // Helper for creating a transaction
  def createNewTransaction(r: KafkaInboundTransaction):Box[Transaction] = {
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


  case class KafkaBank(r: KafkaInboundBank) extends Bank {
    def fullName           = r.full_name
    def shortName          = r.short_name
    def logoUrl            = r.logo
    def bankId             = BankId(r.id)
    def nationalIdentifier = "None"  //TODO
    def swiftBic           = "None"  //TODO
    def websiteUrl         = r.website
  }

  // Helper for creating other bank account
  def createCounterparty(c: KafkaInboundTransactionCounterparty, o: KafkaBankAccount, alreadyFoundMetadata : Option[CounterpartyMetadata]) = {
    new Counterparty(
      counterPartyId = alreadyFoundMetadata.map(_.metadataId).getOrElse(""),
      label = c.account_number.getOrElse(c.name.getOrElse("")),
      nationalIdentifier = "",
      bankRoutingAddress = None,
      accountRoutingAddress = None,
      otherBankId = c.account_number.getOrElse(""),
      thisBankId = "",
      kind = "",
      thisAccountId = BankId(o.bankId.value),
      otherAccountId = AccountId(o.accountId.value),
      alreadyFoundMetadata = alreadyFoundMetadata,

      //TODO V210 following five fields are new, need to be fiexed
      name = "",
      bankRoutingScheme = "",
      accountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true


    )
  }

  case class KafkaBankAccount(r: KafkaInboundAccount) extends BankAccount {
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
      d <- MappedKafkaBankAccountData.find(By(MappedKafkaBankAccountData.accountId, r.id))
    } yield {
      d.getLabel
    }).getOrElse(r.number)

  }


  case class KafkaInboundBank(
                              id : String,
                              short_name : String,
                              full_name : String,
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
  case class KafkaInboundBranch(
                                 id : String,
                                 bank_id: String,
                                 name : String,
                                 address : KafkaInboundAddress,
                                 location : KafkaInboundLocation,
                                 meta : KafkaInboundMeta,
                                 lobby : Option[KafkaInboundLobby],
                                 driveUp : Option[KafkaInboundDriveUp])

  case class KafkaInboundLicense(
                                 id : String,
                                 name : String)

  case class KafkaInboundMeta(
                              license : KafkaInboundLicense)

  case class KafkaInboundLobby(
                               hours : String)

  case class KafkaInboundDriveUp(
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
  case class KafkaInboundAddress(
                                 line_1 : String,
                                 line_2 : String,
                                 line_3 : String,
                                 city : String,
                                 county : String, // Division of State
                                 state : String, // Division of Country
                                 post_code : String,
                                 country_code: String)

  case class KafkaInboundLocation(
                                  latitude : Double,
                                  longitude : Double)

  case class KafkaInboundValidatedUser(
                                       email : String,
                                       display_name : String)

  case class KafkaInboundAccount(
                                  id : String,
                                  bank : String,
                                  label : String,
                                  number : String,
                                  `type` : String,
                                  balance : KafkaInboundBalance,
                                  IBAN : String,
                                  owners : List[String],
                                  generate_public_view : Boolean,
                                  generate_accountants_view : Boolean,
                                  generate_auditors_view : Boolean)

  case class KafkaInboundBalance(
                                 currency : String,
                                 amount : String)

  case class KafkaInboundTransaction(
                                      id : String,
                                      this_account : KafkaInboundAccountId,
                                      counterparty : Option[KafkaInboundTransactionCounterparty],
                                      details : KafkaInboundTransactionDetails)

  case class KafkaInboundTransactionCounterparty(
                                           name : Option[String],  // Also known as Label
                                           account_number : Option[String])

  case class KafkaInboundAccountId(
                                   id : String,
                                   bank : String)

  case class KafkaInboundTransactionDetails(
                                        `type` : String,
                                        description : String,
                                        posted : String,
                                        completed : String,
                                        new_balance : String,
                                        value : String)


  case class KafkaInboundAtm(
                              id : String,
                              bank_id: String,
                              name : String,
                              address : KafkaInboundAddress,
                              location : KafkaInboundLocation,
                              meta : KafkaInboundMeta
                           )


  case class KafkaInboundProduct(
                                 bank_id : String,
                                 code: String,
                                 name : String,
                                 category : String,
                                 family : String,
                                 super_family : String,
                                 more_info_url : String,
                                 meta : KafkaInboundMeta
                               )


  case class KafkaInboundAccountData(
                                      banks : List[KafkaInboundBank],
                                      users : List[InboundUser],
                                      accounts : List[KafkaInboundAccount]
                                   )

  // We won't need this. TODO clean up.
  case class KafkaInboundData(
                               banks : List[KafkaInboundBank],
                               users : List[InboundUser],
                               accounts : List[KafkaInboundAccount],
                               transactions : List[KafkaInboundTransaction],
                               branches: List[KafkaInboundBranch],
                               atms: List[KafkaInboundAtm],
                               products: List[KafkaInboundProduct],
                               crm_events: List[KafkaInboundCrmEvent]
                            )


  case class KafkaInboundCrmEvent(
                                   id : String, // crmEventId
                                   bank_id : String,
                                   customer: KafkaInboundCustomer,
                                   category : String,
                                   detail : String,
                                   channel : String,
                                   actual_date: String
                                 )

  case class KafkaInboundCustomer(
                                   name: String,
                                   number : String // customer number, also known as ownerId (owner of accounts) aka API User?
                                 )


  case class KafkaInboundTransactionId(
                                        transactionId : String
                                      )
  case class KafkaOutboundTransaction(username: String,
                                      accountId: String,
                                      currency: String,
                                      amount: String,
                                      counterpartyId: String,
                                      counterpartyCurrency: String,
                                      transactionType: String)


  case class KafkaInboundChallengeLevel(
                                       limit: BigDecimal,
                                       currency: String
                                        )

  override def getProducts(bankId: BankId): Box[List[Product]] = Empty

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = Empty

  override  def createOrUpdateBranch(branch: BranchJsonPost ): Box[Branch] = Empty

  override def getBranch(bankId : BankId, branchId: BranchId) : Box[MappedBranch]= Empty
}


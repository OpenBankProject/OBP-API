package code.bankconnectors

import java.text.SimpleDateFormat
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.{Date, Optional}

import code.accountholders.{AccountHolders, MapperAccountHolders}
import code.api.util.ErrorMessages._
import code.api.util._
import code.branches.Branches.Branch
import code.fx.{FXRate, fx}
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.comments.Comments
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.model._
import code.model.dataAccess._
import com.openbankproject.commons.model.Product
import code.transaction.MappedTransaction
import code.transactionrequests.TransactionRequests._
import code.transactionrequests._
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.views.Views
import com.google.common.cache.CacheBuilder
import com.openbankproject.commons.model.{Bank, Transaction, _}
import com.tesobe.obp.kafka.{Configuration, SimpleConfiguration, SimpleNorth}
import com.tesobe.obp.transport.nov2016._
import com.tesobe.obp.transport.spi.{DefaultSorter, TimestampFilter}
import com.tesobe.obp.transport.{Pager, Transport}
import net.liftweb.common._
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import scalacache._
import scalacache.guava._
import scalacache.memoization._

import scala.collection.JavaConversions._
import scala.collection.immutable.{List, Seq}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Uses the https://github.com/OpenBankProject/OBP-JVM library to connect to
  * bank resources.
  */
@deprecated("This is used obp-jvm library, we used KafkaMappedConnector_JVMcompatible instead")
object ObpJvmMappedConnector extends Connector with MdcLoggable {

  type JConnector = com.tesobe.obp.transport.Connector
  type JData = com.tesobe.obp.transport.Data
  type JHashMap = java.util.HashMap[String, Object]
  type JResponse = com.tesobe.obp.transport.Decoder.Response
  type JTransport = com.tesobe.obp.transport.Transport

  implicit override val nameOfConnector = ObpJvmMappedConnector.getClass.getSimpleName

  // Maybe we should read the date format from props?
  val DATE_FORMAT = APIUtil.DateWithMs

  var jvmNorth : JConnector = null

  val responseTopic = APIUtil.getPropsValue("obpjvm.response_topic").openOr("Response")
  val requestTopic  = APIUtil.getPropsValue("obpjvm.request_topic").openOr("Request")

  var propsFile = "/props/production.default.props"

  try {
    if (getClass.getResourceAsStream(propsFile) == null)
      propsFile = "/props/default.props"
  } catch {
    case e: Throwable => propsFile = "/props/default.props"
  }

  val cfg: Configuration = new SimpleConfiguration(
    this,
    propsFile,
    responseTopic,
    propsFile,
    requestTopic
  )
  val north   = new SimpleNorth( cfg )

  val factory = Transport.factory(Transport.Version.Nov2016, Transport.Encoding.json).get
  jvmNorth = factory.connector(north)
  north.receive() // start ObpJvm

  logger.info(s"ObpJvmMappedConnector running")

  def toOptional[T](opt: Option[T]): Optional[T] = Optional.ofNullable(opt.getOrElse(null).asInstanceOf[T])
  def toOption[T](opt: Optional[T]): Option[T] = if (opt.isPresent) Some(opt.get()) else None


  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
  val getBankTTL                            = APIUtil.getPropsValue("connector.cache.ttl.seconds.getBank", "0").toInt * 1000 // Miliseconds
  val getBanksTTL                           = APIUtil.getPropsValue("connector.cache.ttl.seconds.getBanks", "0").toInt * 1000 // Miliseconds
  val getAccountTTL                         = APIUtil.getPropsValue("connector.cache.ttl.seconds.getAccount", "0").toInt * 1000 // Miliseconds
  val getAccountsTTL                        = APIUtil.getPropsValue("connector.cache.ttl.seconds.getAccounts", "0").toInt * 1000 // Miliseconds
  val getTransactionTTL                     = APIUtil.getPropsValue("connector.cache.ttl.seconds.getTransaction", "0").toInt * 1000 // Miliseconds
  val getTransactionsTTL                    = APIUtil.getPropsValue("connector.cache.ttl.seconds.getTransactions", "0").toInt * 1000 // Miliseconds
  val getCounterpartyFromTransactionTTL     = APIUtil.getPropsValue("connector.cache.ttl.seconds.getCounterpartyFromTransaction", "0").toInt * 1000 // Miliseconds
  val getCounterpartiesFromTransactionTTL   = APIUtil.getPropsValue("connector.cache.ttl.seconds.getCounterpartiesFromTransaction", "0").toInt * 1000 // Miliseconds

  override def getUser( username: String, password: String ): Box[InboundUser] = {
    val parameters = new JHashMap
    parameters.put("username", username)
    parameters.put("password", password)

    val response = jvmNorth.get("getUser", Transport.Target.user, parameters)

    response.data().map(d => new UserReader(d)).headOption match {
      case Some(u) if (username == u.displayName) => Full(new InboundUser( u.email, password, u.displayName ))
      case None => Empty
    }

  }

  override def updateUserAccountViewsOld( user: ResourceUser ) = {

    val accounts = getBanks(None).map(_._1).openOrThrowException(ErrorMessages.attemptedToOpenAnEmptyBox).flatMap { bank => {
      val bankId = bank.bankId.value
      logger.debug(s"ObpJvm updateUserAccountViews for user.email ${user.email} user.name ${user.name} at bank ${bankId}")
      val parameters = new JHashMap
      parameters.put("userId", user.name)
      parameters.put("bankId", bankId)
      val response = jvmNorth.get("getAccounts", Transport.Target.accounts, parameters)
      // todo response.error().isPresent
      response.data().map(d => new AccountReader(d)).map(a =>  ObpJvmInboundAccount(
        a.accountId,
        a.bankId,
        a.label,
        a.number,
        a.`type`,
        ObpJvmInboundBalance(a.balanceCurrency, a.balanceAmount),
        a.iban,
        user.name :: Nil,
        generate_public_view = true,
        generate_accountants_view = true,
        generate_auditors_view = true,
        account_routing_scheme  = "None",
        account_routing_address = "None",
        branchId = "None"
      )).toList
      }
    }

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
      existing_views <- tryo {Views.views.vend.viewsForAccount(BankIdAccountId(BankId(acc.bank), AccountId(acc.id)))}
    } yield {
      setAccountHolder(username, BankId(acc.bank), AccountId(acc.id), acc.owners)
      views.foreach(v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> updated view ${v.uid} for resourceuser ${user} and account ${acc}")
      })
      existing_views.filterNot(_.users.contains(user.userPrimaryKey)).foreach (v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> added resourceuser ${user} to view ${v.uid} for account ${acc}")
      })
    }
  }


  //gets banks handled by this connector
  override def getBanks(callContext: Option[CallContext])= memoizeSync(getBanksTTL millisecond) {
    val response = jvmNorth.get("getBanks", Transport.Target.banks, null)

    // todo response.error().isPresent

    val banks: List[Bank] = response.data().map(d => new BankReader(d)).map(b =>
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
    Full(banks, callContext)
  }

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String, callContext: Option[CallContext]) = Future{
    val parameters = new JHashMap

    parameters.put("accountId", accountId)
    parameters.put("currency", currency)
    parameters.put("transactionRequestType", transactionRequestType)
    parameters.put("userId", userId)

    val response = jvmNorth.get("getChallengeThreshold", Transport.Target.challengeThreshold, parameters)


    // TODO log here the _ (unhandled) case

    response.data().map(d => new ChallengeThresholdReader(d)) match {
      // Check does the response data match the requested data
      case c:ChallengeThresholdReader => (Full(AmountOfMoney(c.currency, c.amount)), callContext)
      case _ =>
        val limit = BigDecimal("0")
        val rate = fx.exchangeRate ("EUR", currency, Some(bankId))
        val convertedLimit = fx.convert(limit, rate)
        (Full(AmountOfMoney(currency, convertedLimit.toString())), callContext)
    }

  }

  // Gets bank identified by bankId
  override def getBank(bankId: BankId, callContext: Option[CallContext]) = memoizeSync(getBankTTL millisecond) {
    val parameters = new JHashMap

    parameters.put(com.tesobe.obp.transport.nov2016.Bank.bankId, bankId.value)

    val response = jvmNorth.get("getBank", Transport.Target.bank, parameters)

    // todo response.error().isPresent

    val bank = response.data().map(d => new BankReader(d)).headOption match {
      case Some(b) => Full(ObpJvmBank(ObpJvmInboundBank(
        b.bankId,
        b.name,
        b.logo,
        b.url)))
      case None => Empty
    }
    bank.map(bank=>(bank, callContext))
  }

  // Gets transaction identified by bankid, accountid and transactionId
  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]) = {

    val primaryUserIdentifier = AuthUser.getCurrentUserUsername

    def getTransactionInner(bankId: BankId, accountId: AccountId, transactionId: TransactionId, userName: String) : Box[Transaction] = memoizeSync(getTransactionTTL millisecond) {
      val invalid = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, UTC)
      val parameters = new JHashMap

      parameters.put("accountId", accountId.value)
      parameters.put("bankId", bankId.value)
      parameters.put("transactionId", transactionId.value)
      parameters.put("userId", primaryUserIdentifier)

      val response = jvmNorth.get("getTransaction", Transport.Target.transaction, parameters)

      // todo response.error().isPresent

      val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT)

      response.data().map(d => new TransactionReader(d)).headOption match {
        case Some(t) =>
          createNewTransaction(ObpJvmInboundTransaction(
            t.transactionId(),
            ObpJvmInboundAccountId(t.accountId, bankId.value),
            Option(ObpJvmInboundTransactionCounterparty(Option(t.counterPartyId()), Option(t.counterPartyName()))),
            ObpJvmInboundTransactionDetails(
              t.`type`,
              t.description,
              {if (t.postedDate == null) invalid.format(formatter) else t.postedDate.format(formatter)},
              {if (t.completedDate == null) invalid.format(formatter) else t.completedDate.format(formatter)},
              t.newBalanceAmount.toString,
              t.amount.toString
            ))
          )
        case None => Empty
      }
    }
    getTransactionInner(bankId, accountId, transactionId, primaryUserIdentifier).map(transaction =>(transaction, callContext))
  }

  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = {

    val primaryUserIdentifier = AuthUser.getCurrentUserUsername

    def getTransactionsInner(bankId: BankId, accountId: AccountId, userName: String, queryParams: OBPQueryParam*) : Box[List[Transaction]] = memoizeSync(getTransactionsTTL millisecond) {
      val limit: OBPLimit = queryParams.collect { case OBPLimit(value) => OBPLimit(value) }.headOption.get
      val offset = queryParams.collect { case OBPOffset(value) => OBPOffset(value) }.headOption.get
      val fromDate = queryParams.collect { case OBPFromDate(date) => OBPFromDate(date) }.headOption.get
      val toDate = queryParams.collect { case OBPToDate(date) => OBPToDate(date)}.headOption.get
      val ordering = queryParams.collect {
        //we don't care about the intended sort field and only sort on finish date for now
        case OBPOrdering(field, direction) => OBPOrdering(field, direction)}.headOption.get
      val optionalParams = Seq(limit, offset, fromDate, toDate, ordering)

      val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT)

      val parameters = new JHashMap
      val invalid = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, UTC)
      val earliest = ZonedDateTime.of(1999, 1, 1, 0, 0, 0, 0, UTC) // todo how from scala?
      val latest = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, UTC)   // todo how from scala?
      val filter = new TimestampFilter("postedDate", earliest, latest)
      val sorter = new DefaultSorter("completedDate", Pager.SortOrder.ascending)
      val pageSize = Pager.DEFAULT_SIZE; // all in one page
      val pager = jvmNorth.pager(pageSize, 0, filter, sorter)


      // On the one hand its nice to be able to easily add a key/value pair, but we can't know from here how the message will appear on the Kafka or other queue.
      // i.e. we don't have a case class here, we are just telling obpjvm to add a parameter and it does the rest.

      parameters.put("accountId", accountId.value)
      parameters.put("bankId", bankId.value)
      // TODO If we say we are sending userId, send the userId (not the username)
      parameters.put("userId", primaryUserIdentifier)

      val response = jvmNorth.get("getTransactions", Transport.Target.transactions, pager, parameters)

      // todo response.error().isPresent

      val rList: List[ObpJvmInboundTransaction] = response.data().map(d => new TransactionReader(d)).map(t => ObpJvmInboundTransaction(
        t.transactionId(),
        ObpJvmInboundAccountId(t.accountId, bankId.value),
        Option(ObpJvmInboundTransactionCounterparty(Option(t.counterPartyId), Option(t.counterPartyName))),
        ObpJvmInboundTransactionDetails(
          t.`type`,
          t.description,
          {if (t.postedDate == null) invalid.format(formatter) else t.postedDate.format(formatter)},
          {if (t.completedDate == null) invalid.format(formatter) else t.completedDate.format(formatter)},
          t.newBalanceAmount.toString,
          t.amount.toString
        ))
      ).toList

      // Check does the response data match the requested data
      val isCorrect = rList.forall(x=>x.this_account.id == accountId.value && x.this_account.bank == bankId.value)
      if (!isCorrect) {
        //rList.foreach(x=> println("====> x.this_account.id=" + x.this_account.id +":accountId.value=" + accountId.value +":x.this_account.bank=" + x.this_account.bank +":bankId.value="+ bankId.value) )
        throw new Exception(ErrorMessages.InvalidConnectorResponseForGetTransactions)
      }
      // Populate fields and generate result
      val res = for {
        r <- rList
        transaction <- createNewTransaction(r)
      } yield {
        transaction
      }
      Full(res)
    }

    getTransactionsInner(bankId, accountId, primaryUserIdentifier, queryParams: _*).map(transactions =>(transactions, callContext))
    //TODO is this needed updateAccountTransactions(bankId, accountId)
  }

  override def getBankAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = memoizeSync(getAccountTTL millisecond) {
    val parameters = new JHashMap

    //val primaryUserIdentifier = AuthUser.getCurrentUserUsername
    //Note: for Socegn, need bankid, accountId and userId.
    // But the up statmnet is only get user from Login/AuthUser/DeriectLogin. It has no revelvent on the BankId and AccountId.
    // So we links the bankId and UserId in MapperAccountHolders talble.
    val primaryUserIdentifier = AccountHolders.accountHolders.vend.getAccountHolders(bankId, accountId).toList.length match {
       //For now just make it in the log, not throw new RuntimeException("wrong userId, set it in MapperAccountHolders table first!")
      case 0 => "xxxxxxxxxxxxx, wrong userId, set it in MapperAccountHolders table first!"
        //TODO CM this is a super vital sercurity problem, a serious attack vector,Never put Bank specific code in OBP
      case _ => MapperAccountHolders.getAccountHolders(bankId, accountId).toList(0).name
    }

    parameters.put("accountId", accountId.value)
    parameters.put("bankId", bankId.value)
    parameters.put("userId", primaryUserIdentifier)

    val response = jvmNorth.get("getBankAccount", Transport.Target.account, parameters)

    // todo response.error().isPresent

    response.data().map(d => new AccountReader(d)).headOption match {
      case Some(a) => Full(ObpJvmBankAccount(ObpJvmInboundAccount(
        a.accountId,
        a.bankId,
        a.label,
        a.number,
        a.`type`,
        ObpJvmInboundBalance(a.balanceCurrency, a.balanceAmount),
        a.iban,
        primaryUserIdentifier :: Nil,
        generate_public_view = false,
        generate_accountants_view = false,
        generate_auditors_view = false,
        account_routing_scheme  = "None",
        account_routing_address = "None",
        branchId = "None"
      )), callContext)
      case None =>
        logger.info(s"getBankAccount says ! account.isPresent and userId is ${primaryUserIdentifier}")
        Empty
    }
  }

  @deprecated("No sense to use list of its to get bankaccount back.","26/04/2019")
  def getBankAccounts(accts: List[(BankId, AccountId)]): List[BankAccount] = {

    logger.info(s"hello from ObpJvmMappedConnnector.getBankAccounts accts is $accts")

    val primaryUserIdentifier = AuthUser.getCurrentUserUsername

    def getBankAccountsInner(accts: List[(BankId, AccountId)], userName: String) : List[ObpJvmInboundAccount] = memoizeSync(getAccountsTTL millisecond) {
      accts.flatMap { a => {

        logger.info(s"ObpJvmMappedConnnector.getBankAccounts is calling jvmNorth.getAccount with params ${a._1.value} and  ${a._2.value} and primaryUserIdentifier is $primaryUserIdentifier")

        val parameters = new JHashMap

        parameters.put("bankId", a._1.value)
        parameters.put("accountId", a._2.value)
        parameters.put("userId", primaryUserIdentifier)

        val response = jvmNorth.get("getBankAccounts", Transport.Target.account, parameters)

        // todo response.error().isPresent

        response.data().map(d => new AccountReader(d)).headOption match {
          case Some(account) => Full(ObpJvmInboundAccount(
            account.accountId,
            account.bankId,
            account.label,
            account.number,
            account.`type`,
            ObpJvmInboundBalance(account.balanceCurrency, account.balanceAmount),
            account.iban,
            primaryUserIdentifier :: Nil,
            generate_public_view = false,
            generate_accountants_view = false,
            generate_auditors_view = false))
          case None =>
            logger.info(s"getBankAccount says ! account.isPresent")
            Empty
        }
      }
      }
    }

    val r: List[ObpJvmInboundAccount] = getBankAccountsInner(accts, primaryUserIdentifier)

    // Check does the response data match the requested data
    //val accRes = for(row <- r) yield {
    //  (row.bank, row.id)
    //}
    //if ((accRes.toSet diff accts.toSet).size > 0) throw new Exception(ErrorMessages.InvalidGetBankAccountsConnectorResponse)

    r.map { t =>
      createMappedAccountDataIfNotExisting(t.bank, t.id, t.label)
      new ObpJvmBankAccount(t) }
  }

  private def getAccountByNumber(bankId : BankId, number : String, userName: String) : Box[BankAccount] = {
    val primaryUserIdentifier = userName
    val parameters = new JHashMap

    parameters.put("accountId", number)
    parameters.put("bankId", bankId.value)
    parameters.put("userId", primaryUserIdentifier)

    val response = jvmNorth.get("getAccountByNumber", Transport.Target.account, parameters)

    // todo response.error().isPresent

    response.data().map(d => new AccountReader(d)).headOption match {
      case Some(a) => Full(ObpJvmBankAccount(ObpJvmInboundAccount(
        a.accountId,
        a.bankId,
        a.label,
        a.number,
        a.`type`,
        ObpJvmInboundBalance(a.balanceCurrency, a.balanceAmount),
        a.iban,
        primaryUserIdentifier :: Nil,
        generate_public_view = false,
        generate_accountants_view = false,
        generate_auditors_view = false)))
      case None =>
        logger.info(s"getBankAccount says ! account.isPresent")
        Empty
    }
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
      (bank, _)<- getBank(bankId, None)
      account <- getBankAccountType(bankId, accountId)
    } {
      spawn{
        val useMessageQueue = APIUtil.getPropsAsBoolValue("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = Box!!account.lastUpdate match {
          case Full(l) => now after time(l.getTime + hours(APIUtil.getPropsAsIntValue("messageQueue.updateTransactionsInterval", 1)))
          case _ => true
        }
        //if(outDatedTransactions && useMessageQueue) {
        //  UpdatesRequestSender.sendMsg(UpdateBankAccount(account.number, bank.national_identifier.get))
        //}
      }
    }
  }
  */

  
  override def getCounterpartyByIban(iban: String , callContext: Option[CallContext]) =
    LocalMappedConnector.getCounterpartyByIban(iban: String, callContext)

  
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


  protected override def makePaymentImpl(fromAccount: BankAccount,
                                         toAccount: BankAccount,
                                         transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                         amt: BigDecimal,
                                         description: String,
                                         transactionRequestType: TransactionRequestType,
                                         chargePolicy: String): Box[TransactionId] = {

    val sentTransactionId = saveTransaction(fromAccount,
                                            toAccount,
                                            transactionRequestCommonBody,
                                            amt,
                                            description,
                                            transactionRequestType,
                                            chargePolicy)

    sentTransactionId
  }

  /**
   * Saves a transaction with amount @amt and counterparty @counterparty for account @account. Returns the id
   * of the saved transaction.
   */
  private def saveTransaction( fromAccount: BankAccount,
                               toAccount: BankAccount,
                               transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                               amount: BigDecimal,
                               description: String,
                               transactionRequestType: TransactionRequestType,
                               chargePolicy: String): Box[TransactionId] = {
  
    val parameters = new JHashMap
    val fields = new JHashMap

    parameters.put("type", "obp.mar.2017")
  
    fields.put("toCounterpartyId", toAccount.accountId.value)
    fields.put("toCounterpartyName", toAccount.name)
    fields.put("toCounterpartyCurrency", fromAccount.currency) // TODO toCounterparty.currency
    fields.put("toCounterpartyRoutingAddress", toAccount.accountRoutingAddress)
    fields.put("toCounterpartyRoutingScheme", toAccount.accountRoutingScheme)
    fields.put("toCounterpartyBankRoutingAddress", toAccount.bankRoutingAddress)
    fields.put("toCounterpartyBankRoutingScheme", toAccount.bankRoutingScheme)

    val userId = AuthUser.getCurrentResourceUserUserId
    val postedDate = ZonedDateTime.now
    val transactionUUID = APIUtil.generateUUID()
    // Remove last "-" from UUIID to make it 35-character
    // long string (8-4-4-4-12 -> 8-4-4-16)
    val transactionId   = transactionUUID.patch(transactionUUID.lastIndexOf('-'), "", 1)

    // fromAccount
    fields.put("fromAccountName",     fromAccount.name) //optional name, no need to be correct
    fields.put("fromAccountId",       fromAccount.accountId.value)//DEBTOR_ACCOUNT_NUMBER
    fields.put("fromAccountBankId",   fromAccount.bankId.value)
    fields.put("fromAccountCurrency", fromAccount.currency)

    // transaction details
    fields.put("transactionId",             transactionId)
    fields.put("transactionRequestType",    transactionRequestType.value)
    fields.put("transactionAmount",         amount.bigDecimal.toString)
    fields.put("transactionCurrency",       fromAccount.currency)
    fields.put("transactionChargePolicy",   chargePolicy)
    fields.put("transactionChargeAmount",   "7155.0000")  // TODO this maybe change
    fields.put("transactionChargeCurrency", fromAccount.currency) // TODO get correct charge currency
    fields.put("transactionDescription",    description)
    fields.put("transactionPostedDate",     "2016-01-21T18:46:19.056Z")//postedDate) // TODO get correct charge currency, details in TAG_EXECUTION_DATE 

    // might be useful, e.g. for tracking purpose, to
    // send id of the user requesting transaction
    fields.put("userId", AuthUser.getCurrentResourceUserUserId)


    logger.info(s"Before calling jvmNorth.put createTransaction transactionId is: $transactionId")

    val response : JResponse = jvmNorth.put("createTransaction", Transport.Target.transaction, parameters, fields)

    if(response.error().isPresent) {
      val message = response.error().get().message()
      logger.error(s"After calling jvmNorth.put createTransaction we got an error: $message")
    }

    logger.info(s"After calling jvmNorth.put createTransaction we got response: $response")
    // todo response.error().isPresent
    // the returned transaction id should be the same that was sent

    response.data().headOption match {
      case Some(x) => Full(TransactionId(x.text("transactionId")))
      case None => Empty
    }
  }

  override def getTransactionRequestStatusesImpl() : Box[TransactionRequestStatus] = {
    //val parameters = new JHashMap
    //val fields = new JHashMap
    logger.info(s"OBPJVM getTransactionRequestStatusesImpl sart: ")
    val response : JResponse = jvmNorth.fetch

    val r = response.error.isPresent match {
      case false =>
        val res =ObpJvmInboundTransactionRequestStatus(
                                                        response.data.toString,
                                                        List(ObpJvmInboundTransactionStatus(response.data.toString,response.data.toString,response.data.toString)))
        Full(new ObpJvmTransactionRequestStatus(res))
      case true => Empty
    }

    logger.info(s"OBPJVM getTransactionRequestStatusesImpl response: ${r.toString}")
    r
  }

  /*
    Transaction Requests
  */

  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                            account : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                            status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl(transactionRequestId,
      transactionRequestType,
      account,
      counterparty,
      body,
      status,
      charge)
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
  ) = {
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
      AccountId(APIUtil.generateUUID()),
      accountNumber,
      accountType,
      accountLabel,
      currency,
      0L,
      accountHolderName
    )

    Full((bank, account))
  }

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String) = {
    Full(getAccountByNumber(bankId, accountNumber, AuthUser.getCurrentUserUsername) != null)
  }

  //remove an account and associated transactions
  override def removeAccount(bankId: BankId, accountId: AccountId) = {
    //delete comments on transactions of this account
    val commentsDeleted = Comments.comments.vend.bulkDeleteComments(bankId, accountId)

    //delete narratives on transactions of this account
    val narrativesDeleted = MappedNarrative.bulkDelete_!!(
      By(MappedNarrative.bank, bankId.value),
      By(MappedNarrative.account, accountId.value)
    )

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
    val account = getBankAccount(bankId, accountId)

    val accountDeleted = account match {
      case acc => true //acc.delete_! //TODO
      // case _ => false
    }

    Full(commentsDeleted && narrativesDeleted && tagsDeleted && whereTagsDeleted && transactionImagesDeleted &&
      transactionsDeleted && privilegesDeleted && viewsDeleted && accountDeleted)
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
      (bank, _)<- getBank(bankId, None) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
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

  //TODO This will create the OBP local data from remote, but is it correct ? to save the remote data locally? 
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
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal) = {

    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      (bank, _)<- getBank(bankId, None)
    } yield {
      //acc.balance = newBalance
      setBankAccountLastUpdated(bank.nationalIdentifier, acc.number, now).openOrThrowException(attemptedToOpenAnEmptyBox)
    }
  
    Full(result.getOrElse(false))
  }

  //transaction import api uses bank national identifiers to uniquely indentify banks,
  //which is unfortunate as theoretically the national identifier is unique to a bank within
  //one country
  private def getBankByNationalIdentifier(nationalIdentifier : String) : Box[Bank] = memoizeSync(getBankTTL millisecond) {
    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
  }


  private val bigDecimalFailureHandler : PartialFunction[Throwable, Unit] = {
    case ex : NumberFormatException => {
      logger.warn(s"could not convert amount to a BigDecimal: $ex")
    }
  }

  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, counterpartyHolder: String) = {
    //we need to convert from the legacy bankNationalIdentifier to BankId, and from the legacy accountNumber to AccountId
    val count = for {
      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
      account <- getAccountByNumber(bankId, accountNumber, AuthUser.getCurrentUserUsername)
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
    Full(count.map(_.toInt) getOrElse 0)
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
      account <- getAccountByNumber(bankId, accountNumber, AuthUser.getCurrentUserUsername)
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

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) = {
    val result = for {
      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
      account <- getAccountByNumber(bankId, accountNumber, AuthUser.getCurrentUserUsername)
    } yield {
        val acc = getBankAccount(bankId, account.accountId)
        acc match {
          case a => true //a.lastUpdate = updateDate //TODO
          // case _ => logger.warn("can't set bank account.lastUpdated because the account was not found"); false
        }
    }
    Full(result.getOrElse(false))
  }

  /*
    End of transaction importer api
   */


  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String) = {
    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      (bank, _)<- getBank(bankId, None)
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId.value), By(MappedBankAccountData.bankId, bank.bankId.value))
    } yield {
      d.setLabel(label)
      d.save()
    }
    Full(result.getOrElse(false))
  }




  /////////////////////////////////////////////////////////////////////////////



  // Helper for creating a transaction
  def createNewTransaction(r: ObpJvmInboundTransaction):Box[Transaction] = {
    var datePosted: Date = null
    if (r.details.posted != null) // && r.details.posted.matches("^[0-9]{8}$"))
      datePosted = new SimpleDateFormat(DATE_FORMAT).parse(r.details.posted)

    var dateCompleted: Date = null
    if (r.details.completed != null) // && r.details.completed.matches("^[0-9]{8}$"))
      dateCompleted = new SimpleDateFormat(DATE_FORMAT).parse(r.details.completed)

    for {
        cparty <- r.counterparty
        thisAccount <- getBankAccount(BankId(r.this_account.bank), AccountId(r.this_account.id))
        //creates a dummy Counterparty without an CounterpartyMetadata, which results in one being generated (in Counterparty init)
        dummyCounterparty <- tryo{createCounterparty(cparty, thisAccount, None)}
        //and create the proper Counterparty with the correct "id" attribute set to the metadataId of the CounterpartyMetadata object
        //note: as we are passing in the CounterpartyMetadata we don't incur another db call to get it in Counterparty init
        counterparty <- tryo{createCounterparty(cparty, thisAccount, Some(dummyCounterparty.metadata))}
      } yield {

        // Fix balance if null
        val new_balance = if (r.details.new_balance != null)
          r.details.new_balance
        else
          "0.0"

        // Create new transaction
        new Transaction(
                         r.id, // uuid:String
                         TransactionId(r.id), // id:TransactionId
                         thisAccount, // thisAccount:BankAccount
                         counterparty, // counterparty:Counterparty
                         r.details.`type`, // transactionType:String
                         BigDecimal(r.details.value), // val amount:BigDecimal
                         thisAccount.currency, // currency:String
                         Some(r.details.description), // description:Option[String]
                         datePosted, // startDate:Date
                         dateCompleted, // finishDate:Date
                         BigDecimal(new_balance) // balance:BigDecimal)
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
    def websiteUrl         = r.url
    def bankRoutingScheme = "None"
    def bankRoutingAddress = "None"
  }

  // Helper for creating other bank account
  def createCounterparty(c: ObpJvmInboundTransactionCounterparty, o: BankAccount, alreadyFoundMetadata : Option[CounterpartyMetadata]) = {
    new Counterparty(
      counterpartyId = alreadyFoundMetadata.map(_.getCounterpartyId).getOrElse(""),
      counterpartyName = c.account_number.getOrElse(c.name.getOrElse("")),
      nationalIdentifier = "",
      otherBankRoutingAddress = None,
      otherAccountRoutingAddress = None,
      thisAccountId = AccountId(c.account_number.getOrElse("")),
      thisBankId = BankId(""),
      kind = "",
      otherBankRoutingScheme = "",
      otherAccountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true


    )
  }

  case class ObpJvmBankAccount(r: ObpJvmInboundAccount) extends BankAccount {
    def accountId : AccountId       = AccountId(r.id)
    def accountType : String        = r.`type`
    def balance : BigDecimal        = BigDecimal(if (r.balance.amount.isEmpty) "-0.00" else r.balance.amount)
    def currency : String           = r.balance.currency
    def name : String               = r.owners.head
    def iban : Option[String]       = Some(r.IBAN)
    def number : String             = r.number
    def bankId : BankId             = BankId(r.bank)
    def lastUpdate : Date           = new SimpleDateFormat(DATE_FORMAT).parse(today.getTime.toString)
    def accountHolder : String      = r.owners.head
    def accountRoutingScheme: String = r.account_routing_scheme
    def accountRoutingAddress: String = r.account_routing_address
    def accountRoutings: List[AccountRouting] = List()
    def branchId: String = r.branchId
    def accountRules: List[AccountRule] = List()

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
                              url : String)


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
                                  generate_auditors_view : Boolean,
                                  account_routing_scheme: String  = "None",
                                  account_routing_address: String  = "None",
                                  branchId: String  = "None"
                                  )

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

  case class ObpJvmInboundTransactionRequestStatus(
                                             transactionRequestId : String,
                                             bulkTransactionsStatus: List[ObpJvmInboundTransactionStatus]
                                           )

  case class ObpJvmInboundTransactionStatus(
                                transactionId : String,
                                transactionStatus: String,
                                transactionTimestamp: String
                              ) extends TransactionStatus

  case class ObpJvmTransactionRequestStatus (obpJvmInboundTransactionRequestStatus: ObpJvmInboundTransactionRequestStatus) extends TransactionRequestStatus {
    override def transactionRequestId: String = obpJvmInboundTransactionRequestStatus.transactionRequestId
    override def bulkTransactionsStatus: List[TransactionStatus] = obpJvmInboundTransactionRequestStatus.bulkTransactionsStatus
  }
  override def getProducts(bankId: BankId): Box[List[Product]] = Empty

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = Empty

  override  def createOrUpdateBranch(branch: Branch): Box[BranchT] = Empty
  
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


  override def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = Empty

  //TODO need to fix in obpjvm, just mocked result as Mapper
  override def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge] = {
    val transactionRequestTypeChargeMapper = MappedTransactionRequestTypeCharge.find(
      By(MappedTransactionRequestTypeCharge.mBankId, bankId.value),
      By(MappedTransactionRequestTypeCharge.mTransactionRequestTypeId, transactionRequestType.value))

    val transactionRequestTypeCharge = transactionRequestTypeChargeMapper match {
      case Full(transactionRequestType) => Full(TransactionRequestTypeChargeMock(
        transactionRequestType.transactionRequestTypeId,
        transactionRequestType.bankId,
        transactionRequestType.chargeCurrency,
        transactionRequestType.chargeAmount,
        transactionRequestType.chargeSummary
        )
      )
      //If it is empty, return the default value : "0.0000000" and set the BankAccount currency
      case _ =>
        for {
          fromAccount <- getBankAccount(bankId, accountId)
          fromAccountCurrency <- tryo{ fromAccount.currency }
        } yield {
          TransactionRequestTypeChargeMock(transactionRequestType.value, bankId.value, fromAccountCurrency, "0.00", "Warning! Default value!")
        }
    }

    transactionRequestTypeCharge
  }

  override def getEmptyBankAccount(): Box[BankAccount] = {
    Full(new ObpJvmBankAccount(ObpJvmInboundAccount(id = "",
                                                    bank = "",
                                                    label = "",
                                                    number = "",
                                                    `type` = "",
                                                    balance = ObpJvmInboundBalance("", ""),
                                                    IBAN = "",
                                                    owners = Nil,
                                                    generate_public_view = true,
                                                    generate_accountants_view = true,
                                                    generate_auditors_view = true)))
  }
}


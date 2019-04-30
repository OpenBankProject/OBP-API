package code.bankconnectors

import java.util.Date
import java.util.UUID.randomUUID

import code.accountapplication.AccountApplication
import code.accountattribute.AccountAttribute
import code.accountholders.AccountHolders
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.cache.Caching
import code.api.util.APIUtil.{OBPReturnType, isValidCurrencyISOCode, saveConnectorMetric, stringOrNull}
import code.api.util.ErrorMessages._
import code.api.util._
import code.atms.Atms.Atm
import code.atms.MappedAtm
import code.bankconnectors.vJune2017.InboundAccountJune2017
import code.branches.Branches.Branch
import code.branches.MappedBranch
import code.cards.MappedPhysicalCard
import code.context.{UserAuthContextProvider, UserAuthContextUpdate, UserAuthContextUpdateProvider}
import code.customer._
import code.customeraddress.CustomerAddress
import code.fx.{FXRate, MappedFXRate, fx}
import code.management.ImporterAPI.ImporterTransaction
import code.meetings.Meeting
import code.metadata.comments.Comments
import code.metadata.counterparties.Counterparties
import code.metadata.narrative.Narrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.model._
import code.model.dataAccess._
import code.productattribute.ProductAttribute
import code.productcollection.ProductCollection
import code.productcollectionitem.ProductCollectionItem
import code.products.MappedProduct
import com.openbankproject.commons.model.Product
import code.taxresidence.TaxResidence
import code.transaction.MappedTransaction
import code.transactionrequests._
import code.users.Users
import code.util.Helper
import code.util.Helper.{MdcLoggable, _}
import code.views.Views
import com.google.common.cache.CacheBuilder
import com.openbankproject.commons.model.{AccountApplication, AccountAttribute, ProductAttribute, ProductCollectionItem, TaxResidence, _}
import com.tesobe.CacheKeyFromArguments
import com.tesobe.model.UpdateBankAccount
import net.liftweb.common._
import net.liftweb.mapper.{By, _}
import net.liftweb.util.Helpers.{tryo, _}
import scalacache.ScalaCache
import scalacache.guava.GuavaCache

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.BigInt
import scala.util.Random


object LocalMappedConnector extends Connector with MdcLoggable {
  
//  override type AccountType = MappedBankAccount
  val maxBadLoginAttempts = APIUtil.getPropsValue("max.bad.login.attempts") openOr "10"

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
  val getTransactionsTTL                    = APIUtil.getPropsValue("connector.cache.ttl.seconds.getTransactions", "0").toInt * 1000 // Miliseconds

  //This is the implicit parameter for saveConnectorMetric function.
  //eg:  override def getBank(bankId: BankId, callContext: Option[CallContext]) = saveConnectorMetric
  implicit override val nameOfConnector = LocalMappedConnector.getClass.getSimpleName

  //
  override def getAdapterInfoFuture(callContext: Option[CallContext]) : Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = Future(
    Full(InboundAdapterInfoInternal(
      errorCode = "",
      backendMessages = Nil,
      name ="LocalMappedConnector",
      version ="Just for testing.",
      git_commit ="",
      date =""
    ), callContext))
  
  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String, 
                                     accountId: String, 
                                     viewId: String, 
                                     transactionRequestType: String, 
                                     currency: String, 
                                     userId: String, 
                                     userName: String, 
                                     callContext: Option[CallContext]): Future[(Box[AmountOfMoney], Option[CallContext])] = Future {
    val propertyName = "transactionRequests_challenge_threshold_" + transactionRequestType.toUpperCase
    val threshold = BigDecimal(APIUtil.getPropsValue(propertyName, "1000"))
    logger.debug(s"threshold is $threshold")
    
    val thresholdCurrency: String = APIUtil.getPropsValue("transactionRequests_challenge_currency", "EUR")
    logger.debug(s"thresholdCurrency is $thresholdCurrency")
    isValidCurrencyISOCode(thresholdCurrency)match {
      case true =>
        fx.exchangeRate(thresholdCurrency, currency, Some(bankId)) match {
          case rate@Some(_) =>
            val convertedThreshold = fx.convert(threshold, rate)
            logger.debug(s"getChallengeThreshold for currency $currency is $convertedThreshold")
            (Full(AmountOfMoney(currency, convertedThreshold.toString())), callContext)
          case _ =>
            val msg = s"$InvalidCurrency The requested currency conversion (${thresholdCurrency} to ${currency}) is not supported."
            (Failure(msg), callContext)
        }
      case false =>
        val msg =s"$InvalidISOCurrencyCode ${thresholdCurrency}"
        (Failure(msg), callContext)
    }
  }

  /**
    * Steps To Create, Store and Send Challenge
    * 1. Generate a random challenge
    * 2. Generate a long random salt
    * 3. Prepend the salt to the challenge and hash it with a standard password hashing function like Argon2, bcrypt, scrypt, or PBKDF2.
    * 4. Save both the salt and the hash in the user's database record.
    * 5. Send the challenge over an separate communication channel.
    */
  // Now, move this method to `code.transactionChallenge.MappedExpectedChallengeAnswerProvider.validateChallengeAnswerInOBPSide`
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, callContext: Option[CallContext]) = Future {
//    val challengeId = UUID.randomUUID().toString
//    val challenge = StringHelpers.randomString(6)
//    // Random string. For instance: EONXOA
//    val salt = BCrypt.gensalt()
//    val hash = BCrypt.hashpw(challenge, salt).substring(0, 44)
//    // TODO Extend database model in order to store users salt and hash
//    // Store salt and hash and bind to challengeId
//    // TODO Send challenge to the user over an separate communication channel
//    //Return id of challenge
//    Full(challengeId)
    (Full("123"), callContext)
  }

  /**
    * To Validate A Challenge Answer
    * 1. Retrieve the user's salt and hash from the database.
    * 2. Prepend the salt to the given password and hash it using the same hash function.
    * 3. Compare the hash of the given answer with the hash from the database. If they match, the answer is correct. Otherwise, the answer is incorrect.
    */
  // TODO Extend database model in order to get users salt and hash it
  override def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]) = Future{
    val answer = for {
      nonEmpty <- booleanToBox(hashOfSuppliedAnswer.nonEmpty) ?~ "Need a non-empty answer"
      answerToNumber <- tryo(BigInt(hashOfSuppliedAnswer)) ?~! "Need a numeric TAN"
      positive <- booleanToBox(answerToNumber > 0) ?~ "Need a positive TAN"
    } yield true
    
    (answer, callContext)
  }

  override def getChargeLevel(bankId: BankId,
                              accountId: AccountId,
                              viewId: ViewId,
                              userId: String,
                              userName: String,
                              transactionRequestType: String,
                              currency: String,
                              callContext:Option[CallContext]) = Future {
    val propertyName = "transactionRequests_charge_level_" + transactionRequestType.toUpperCase
    val chargeLevel = BigDecimal(APIUtil.getPropsValue(propertyName, "0.0001"))
    logger.debug(s"transactionRequests_charge_level is $chargeLevel")

    // TODO constrain this to supported currencies.
    //    val chargeLevelCurrency = APIUtil.getPropsValue("transactionRequests_challenge_currency", "EUR")
    //    logger.debug(s"chargeLevelCurrency is $chargeLevelCurrency")
    //    val rate = fx.exchangeRate (chargeLevelCurrency, currency)
    //    val convertedThreshold = fx.convert(chargeLevel, rate)
    //    logger.debug(s"getChallengeThreshold for currency $currency is $convertedThreshold")

    (Full(AmountOfMoney(currency, chargeLevel.toString)),callContext)
  }

  //gets a particular bank handled by this connector
  override def getBank(bankId: BankId, callContext: Option[CallContext]) = saveConnectorMetric {
    getMappedBank(bankId).map(bank =>(bank, callContext))
  }("getBank")

  private def getMappedBank(bankId: BankId): Box[MappedBank] =
    MappedBank
      .find(By(MappedBank.permalink, bankId.value))
      .map(
        bank =>
            bank
              .mBankRoutingScheme(APIUtil.ValueOrOBP(bank.bankRoutingScheme))
              .mBankRoutingAddress(APIUtil.ValueOrOBPId(bank.bankRoutingAddress,bank.bankId.value))
      )

  override def getBankFuture(bankId : BankId, callContext: Option[CallContext]) = Future {
    getBank(bankId, callContext)
  }
  

  override def getBanks(callContext: Option[CallContext]) = saveConnectorMetric {
     Full(MappedBank
        .findAll()
        .map(
          bank =>
             bank
               .mBankRoutingScheme(APIUtil.ValueOrOBP(bank.bankRoutingScheme))
               .mBankRoutingAddress(APIUtil.ValueOrOBPId(bank.bankRoutingAddress, bank.bankId.value))
        ),
       callContext
     )
  }("getBanks")

  override def getBanksFuture(callContext: Option[CallContext]) = Future {
    getBanks(callContext)
  }

  //This method is only for testing now. Normall this method 
  override def getBankAccountsForUser(username: String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])]= {
    val inboundAccountCommonsBox: Box[Set[InboundAccountCommons]] =for{
      //1 get all the accounts for one user
      user <- Users.users.vend.getUserByUserName(username)
      bankAccountIds = AccountHolders.accountHolders.vend.getAccountsHeldByUser(user)
    } yield{
      for{
        bankAccountId <- bankAccountIds
        (bankAccount, callContext) <- getBankAccountCommon(bankAccountId.bankId, bankAccountId.accountId,callContext)
        inboundAccountCommons = InboundAccountCommons(
          bankId = bankAccount.bankId.value,
          branchId = bankAccount.branchId,
          accountId = bankAccount.accountId.value,
          accountNumber = bankAccount.number,
          accountType = bankAccount.accountType,
          balanceAmount = bankAccount.balance.toString(),
          balanceCurrency = bankAccount.currency,
          owners = bankAccount.userOwners.map(_.name).toList,
          viewsToGenerate = List("Owner"),
          bankRoutingScheme = bankAccount.bankRoutingScheme,
          bankRoutingAddress = bankAccount.bankRoutingAddress,
          branchRoutingScheme = "",
          branchRoutingAddress ="",
          accountRoutingScheme = bankAccount.accountRoutingScheme,
          accountRoutingAddress = bankAccount.accountRoutingAddress
        )
      } yield {
        inboundAccountCommons
      }
    }
    inboundAccountCommonsBox.map( inboundAccountCommons => (inboundAccountCommons.toList, callContext))
  }

  override def getBankAccountsForUserFuture(username: String, callContext: Option[CallContext]): Future[Box[(List[InboundAccount], Option[CallContext])]] = Future{
    getBankAccountsForUser(username,callContext)
  }

  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]) = {

    updateAccountTransactions(bankId, accountId)

    MappedTransaction.find(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountId.value),
      By(MappedTransaction.transactionId, transactionId.value)).flatMap(_.toTransaction)
      .map(transaction => (transaction, callContext))
  }

  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = {

    // TODO Refactor this. No need for database lookups etc.
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

    def getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams : Seq[QueryParam[MappedTransaction]]) : Box[List[Transaction]]
    = {
      /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getTransactionsTTL millisecond) {

          //logger.info("Cache miss getTransactionsCached")

          val mappedTransactions = MappedTransaction.findAll(mapperParams: _*)

          updateAccountTransactions(bankId, accountId)

          for (account <- getBankAccount(bankId, accountId))
            yield mappedTransactions.flatMap(_.toTransaction(account)) //each transaction will be modified by account, here we return the `class Transaction` not a trait.
        }
      }
    }
    getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams).map(transactions => (transactions, callContext))
  }
  
  override def getTransactionsCore(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) =
    {

      // TODO Refactor this. No need for database lookups etc.
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

      val optionalParams: Seq[QueryParam[MappedTransaction]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
      val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountId.value)) ++ optionalParams

      def getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams: Seq[QueryParam[MappedTransaction]]): Box[List[TransactionCore]]
      = {
        /**
          * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
          * is just a temporary value filed with UUID values in order to prevent any ambiguity.
          * The real value will be assigned by Macro during compile time at this line of a code:
          * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
          */
        var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
        CacheKeyFromArguments.buildCacheKey {
          Caching.memoizeSyncWithProvider (Some(cacheKey.toString()))(getTransactionsTTL millisecond) {

          //logger.info("Cache miss getTransactionsCached")

          val mappedTransactions = MappedTransaction.findAll(mapperParams: _*)

          for (account <- getBankAccount(bankId, accountId))
            yield mappedTransactions.flatMap(_.toTransactionCore(account)) //each transaction will be modified by account, here we return the `class Transaction` not a trait.
        }
      }
    }

    getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams).map(transactions =>(transactions,callContext))
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
      account <- getBankAccount(bankId, accountId).map(_.asInstanceOf[MappedBankAccount])
    } {
      Future{
        val useMessageQueue = APIUtil.getPropsAsBoolValue("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = Box!!account.accountLastUpdate.get match {
          case Full(l) => now after time(l.getTime + hours(APIUtil.getPropsAsIntValue("messageQueue.updateTransactionsInterval", 1)))
          case _ => true
        }
        if(outDatedTransactions && useMessageQueue) {
          UpdatesRequestSender.sendMsg(UpdateBankAccount(account.accountNumber.get, bank.national_identifier.get))
        }
      }
    }
  }

  override def getBankAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
    getBankAccountCommon(bankId, accountId, callContext)
  }

  override def getBankAccountFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : OBPReturnType[Box[BankAccount]]= Future
  {
    val accountAndCallcontext = getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext])
    (accountAndCallcontext.map(_._1), accountAndCallcontext.map(_._2).getOrElse(callContext))
  }
  
  def getBankAccountCommon(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) = {
    MappedBankAccount
      .find(By(MappedBankAccount.bank, bankId.value),
        By(MappedBankAccount.theAccountId, accountId.value))
      .map(
        account =>
          account
            .mAccountRoutingScheme(APIUtil.ValueOrOBP(account.accountRoutingScheme))
            .mAccountRoutingAddress(APIUtil.ValueOrOBPId(account.accountRoutingAddress, account.accountId.value))
      ).map(bankAccount => (bankAccount, callContext))
  }

  override def getBankAccountsFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[List[BankAccount]]] = {
    Future {
      Full(
        bankIdAccountIds.map(
          bankIdAccountId =>
            getBankAccount(
              bankIdAccountId.bankId,
              bankIdAccountId.accountId
            ).openOrThrowException(s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"))
      )
    }
  }
  
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) = {
    getBankAccount(bankId: BankId, accountId: AccountId, callContext)
  }  
  override def checkBankAccountExistsFuture(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Future[Box[(BankAccount, Option[CallContext])]] = 
    Future {
      getBankAccount(bankId: BankId, accountId: AccountId, callContext)
    }
  
  override def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[(List[CoreAccount], Option[CallContext])]= {
    Full(
      bankIdAccountIds
        .map(bankIdAccountId =>
          getBankAccount(
            bankIdAccountId.bankId, 
            bankIdAccountId.accountId)
            .openOrThrowException(s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"))
        .map(account =>
          CoreAccount(
            account.accountId.value, 
            stringOrNull(account.label),
            account.bankId.value, 
            account.accountType,
            account.accountRoutings)),
      callContext
    )
  }

  override def getCoreBankAccountsFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]] = {
    Future {getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext])}
  }
  // localConnector/getBankAccountsHeld/bankIdAccountIds/{bankIdAccountIds}
  override def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[List[AccountHeld]]= {
    Full(
      bankIdAccountIds
        .map(bankIdAccountId =>
               getBankAccount(
                 bankIdAccountId.bankId,
                 bankIdAccountId.accountId)
                 .openOrThrowException(s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"))
        .map(account =>
               AccountHeld(
                 account.accountId.value,
                 account.bankId.value,
                 stringOrNull(account.number),
                 account.accountRoutings))
    )
  }
  
  override def getBankAccountsHeldFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[List[AccountHeld]]] = {
    Future {getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext])}
  }
  

  override def getEmptyBankAccount(): Box[BankAccount] = {
    Full(new MappedBankAccount())
  }

  /**
    * This is used for create or update the special bankAccount for COUNTERPARTY stuff (toAccountProvider != "OBP") and (Connector = Kafka)
    * details in createTransactionRequest - V210 ,case COUNTERPARTY.toString
    *
    */
  def createOrUpdateMappedBankAccount(bankId: BankId, accountId: AccountId, currency: String): Box[BankAccount] = {

    val mappedBankAccount = getBankAccount(bankId, accountId).map(_.asInstanceOf[MappedBankAccount]) match {
      case Full(f) =>
        f.bank(bankId.value).theAccountId(accountId.value).accountCurrency(currency).saveMe()
      case _ =>
        MappedBankAccount.create.bank(bankId.value).theAccountId(accountId.value).accountCurrency(currency).saveMe()
    }

    Full(mappedBankAccount)
  }
  
  override def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = {
    for {
      t <- Counterparties.counterparties.vend.getMetadata(thisBankId, thisAccountId, couterpartyId)
    } yield {
      new Counterparty(
        //counterparty id is defined to be the id of its metadata as we don't actually have an id for the counterparty itself
        counterpartyId = t.getCounterpartyId,
        counterpartyName = t.getCounterpartyName,
        nationalIdentifier = "",
        otherBankRoutingAddress = None,
        otherAccountRoutingAddress = None,
        thisAccountId = thisAccountId,
        thisBankId = BankId(""),
        kind = "",
        otherBankRoutingScheme = "",
        otherAccountRoutingScheme="",
        otherAccountProvider = "",
        isBeneficiary = true
      )
    }
  }
  
  override def getCounterpartyTrait(bankId: BankId, accountId: AccountId, counterpartyId: String, callContext: Option[CallContext])= {
    getCounterpartyByCounterpartyIdFuture(CounterpartyId(counterpartyId), callContext)
  }
  
  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]) ={
    Counterparties.counterparties.vend.getCounterparty(counterpartyId.value).map(counterparty => (counterparty, callContext))
  }
  
  override def getCounterpartyByCounterpartyIdFuture(counterpartyId: CounterpartyId, callContext: Option[CallContext]) = Future{
    (Counterparties.counterparties.vend.getCounterparty(counterpartyId.value),callContext)
  }

  override def getCounterpartyByIban(iban: String, callContext: Option[CallContext]) =  {
    Future (Counterparties.counterparties.vend.getCounterpartyByIban(iban), callContext)
  }


  override def getPhysicalCards(user: User) = {
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCards(user)
    val cardList = for (l <- list) yield
      new PhysicalCard(
        bankId=l.bankId,
        bankCardNumber = l.bankCardNumber,
        nameOnCard = l.nameOnCard,
        issueNumber = l.issueNumber,
        serialNumber = l.serialNumber,
        validFrom = l.validFrom,
        expires = l.expires,
        enabled = l.enabled,
        cancelled = l.cancelled,
        onHotList = l.onHotList,
        technology = l.technology,
        networks = l.networks,
        allows = l.allows,
        account = l.account,
        replacement = l.replacement,
        pinResets = l.pinResets,
        collected = l.collected,
        posted = l.posted
      )
    Full(cardList)
  }

  override def getPhysicalCardsForBank(bank: Bank, user: User)= {
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCardsForBank(bank, user)
    val cardList = for (l <- list) yield
      new PhysicalCard(
        bankId= l.bankId,
        bankCardNumber = l.bankCardNumber,
        nameOnCard = l.nameOnCard,
        issueNumber = l.issueNumber,
        serialNumber = l.serialNumber,
        validFrom = l.validFrom,
        expires = l.expires,
        enabled = l.enabled,
        cancelled = l.cancelled,
        onHotList = l.onHotList,
        technology = l.technology,
        networks = l.networks,
        allows = l.allows,
        account = l.account,
        replacement = l.replacement,
        pinResets = l.pinResets,
        collected = l.collected,
        posted = l.posted
      )
    Full(cardList)
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
    val physicalCardBox: Box[MappedPhysicalCard] = code.cards.PhysicalCard.physicalCardProvider.vend.createOrUpdatePhysicalCard(
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
    for (l <- physicalCardBox) yield
    new PhysicalCard(
      bankId = l.bankId,
      bankCardNumber = l.bankCardNumber,
      nameOnCard = l.nameOnCard,
      issueNumber = l.issueNumber,
      serialNumber = l.serialNumber,
      validFrom = l.validFrom,
      expires = l.expires,
      enabled = l.enabled,
      cancelled = l.cancelled,
      onHotList = l.onHotList,
      technology = l.technology,
      networks = l.networks,
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
  override def makePaymentImpl(fromAccount: BankAccount,
                               toAccount: BankAccount,
                               transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                               amount: BigDecimal,
                               description: String,
                               transactionRequestType: TransactionRequestType,
                               chargePolicy: String): Box[TransactionId] = {
    for{
       rate <- tryo {fx.exchangeRate(fromAccount.currency, toAccount.currency, Some(fromAccount.bankId.value))} ?~! s"$InvalidCurrency The requested currency conversion (${fromAccount.currency} to ${fromAccount.currency}) is not supported."
       fromTransAmt = -amount//from fromAccount balance should decrease
       toTransAmt = fx.convert(amount, rate)
       sentTransactionId <- saveTransaction(fromAccount, toAccount,transactionRequestCommonBody, fromTransAmt, description, transactionRequestType, chargePolicy)
       _sentTransactionId <- saveTransaction(toAccount, fromAccount,transactionRequestCommonBody, toTransAmt, description, transactionRequestType, chargePolicy)
    } yield{
      sentTransactionId
    }
  }
  
  override def makePaymentv210(fromAccount: BankAccount,
                      toAccount: BankAccount,
                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                      amount: BigDecimal,
                      description: String,
                      transactionRequestType: TransactionRequestType,
                      chargePolicy: String, 
                      callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]]= {
    for{
       rate <- NewStyle.function.tryons(s"$InvalidCurrency The requested currency conversion (${fromAccount.currency} to ${fromAccount.currency}) is not supported.", 400, callContext) {
          fx.exchangeRate(fromAccount.currency, toAccount.currency, Some(fromAccount.bankId.value))}
       fromTransAmt = -amount//from fromAccount balance should decrease
       toTransAmt = fx.convert(amount, rate)
       sentTransactionId <- Future{saveTransaction(fromAccount, toAccount,transactionRequestCommonBody, fromTransAmt, description, transactionRequestType, chargePolicy)}
       _sentTransactionId <- Future{saveTransaction(toAccount, fromAccount,transactionRequestCommonBody, toTransAmt, description, transactionRequestType, chargePolicy)}
    } yield{
      (sentTransactionId, callContext)
    }
  }

  /**
    * Saves a transaction with @amount, @toAccount and @transactionRequestType for @fromAccount and @toCounterparty. <br>
    * Returns the id of the saved transactionId.<br>
    */
  private def saveTransaction(fromAccount: BankAccount,
                              toAccount: BankAccount,
                              transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                              amount: BigDecimal,
                              description: String,
                              transactionRequestType: TransactionRequestType,
                              chargePolicy: String): Box[TransactionId] = {
    for{
      
      currency <- Full(fromAccount.currency)
    //update the balance of the fromAccount for which a transaction is being created
      newAccountBalance <- Full(Helper.convertToSmallestCurrencyUnits(fromAccount.balance, currency) + Helper.convertToSmallestCurrencyUnits(amount, currency))
      
      //Here is the `LocalMappedConnector`, once get this point, fromAccount must be a mappedBankAccount. So can use asInstanceOf.... 
      _ <- tryo(fromAccount.asInstanceOf[MappedBankAccount].accountBalance(newAccountBalance).save()) ?~! UpdateBankAccountException
  
      mappedTransaction <- tryo(MappedTransaction.create
      //No matter which type (SANDBOX_TAN,SEPA,FREE_FORM,COUNTERPARTYE), always filled the following nine fields.
      .bank(fromAccount.bankId.value)
      .account(fromAccount.accountId.value)
      .transactionType(transactionRequestType.value)
      .amount(Helper.convertToSmallestCurrencyUnits(amount, currency))
      .newAccountBalance(newAccountBalance)
      .currency(currency)
      .tStartDate(now)
      .tFinishDate(now)
      .description(description) 
       //Old data: other BankAccount(toAccount: BankAccount)simulate counterparty 
      .counterpartyAccountHolder(toAccount.accountHolder)
      .counterpartyAccountNumber(toAccount.number)
      .counterpartyAccountKind(toAccount.accountType)
      .counterpartyBankName(toAccount.bankName)
      .counterpartyIban(toAccount.iban.getOrElse(""))
      .counterpartyNationalId(toAccount.nationalIdentifier)
       //New data: real counterparty (toCounterparty: CounterpartyTrait)
//      .CPCounterPartyId(toAccount.accountId.value)
      .CPOtherAccountRoutingScheme(toAccount.accountRoutingScheme)
      .CPOtherAccountRoutingAddress(toAccount.accountRoutingAddress)
      .CPOtherBankRoutingScheme(toAccount.bankRoutingScheme)
      .CPOtherBankRoutingAddress(toAccount.bankRoutingAddress)
      .chargePolicy(chargePolicy)
      .saveMe) ?~! s"$CreateTransactionsException, exception happened when create new mappedTransaction"
    } yield{
      mappedTransaction.theTransactionId
    }
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
                                               transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                               details: String,
                                               status: String,
                                               charge: TransactionRequestCharge,
                                               chargePolicy: String): Box[TransactionRequest] = {

    TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl210(transactionRequestId,
      transactionRequestType,
      fromAccount,
      toAccount,
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
  )  = {
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
      AccountId(APIUtil.generateUUID()),
      accountNumber, accountType,
      accountLabel, currency,
      0L, accountHolderName,
      "", "", "" //added field in V220
    )

    Full((bank, account))
  }

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String) = {
    Full(MappedBankAccount.count(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.accountNumber, accountNumber)) > 0)
  }

  //remove an account and associated transactions
  override def removeAccount(bankId: BankId, accountId: AccountId) = {
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
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal) = {
    //this will be Full(true) if everything went well
    val result = for {
      bank <- getMappedBank(bankId)
      account <- getBankAccount(bankId, accountId).map(_.asInstanceOf[MappedBankAccount])
    } yield {
      account.accountBalance(Helper.convertToSmallestCurrencyUnits(newBalance, account.currency)).save
      setBankAccountLastUpdated(bank.nationalIdentifier, account.number, now).openOrThrowException(attemptedToOpenAnEmptyBox)
    }

    Full(result.getOrElse(false))
  }

  //transaction import api uses bank national identifiers to uniquely indentify banks,
  //which is unfortunate as theoretically the national identifier is unique to a bank within
  //one country
  private def getBankByNationalIdentifier(nationalIdentifier : String) : Box[Bank] = {
    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
  }

  private def getAccountByNumber(bankId : BankId, number : String) : Box[BankAccount] = {
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
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String) = {
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

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) = {
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
    Full(result.getOrElse(false))
  }

  /*
    End of transaction importer api
   */


  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String) = {
    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId).map(_.asInstanceOf[MappedBankAccount])
      bank <- getMappedBank(bankId)
    } yield {
        acc.accountLabel(label).save
      }

    Full(result.getOrElse(false))
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

    logger.info("before create or update branch")

    val foundBranch : Box[MappedBranch] = getBranch(branch.bankId, branch.branchId)

    logger.info("after getting")

      //check the branch existence and update or insert data
    val branchToReturn = foundBranch match {
      case Full(mappedBranch) =>
        tryo {
          // Update...
          logger.info("We found a branch so update...")
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

            .mLobbyOpeningTimeOnMonday(branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnMonday(branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnTuesday(branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnTuesday(branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnWednesday(branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnWednesday(branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnThursday(branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnThursday(branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnFriday(branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnFriday(branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnSaturday(branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnSaturday(branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnSunday(branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnSunday(branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)


            // Drive Up
            .mDriveUpOpeningTimeOnMonday(branch.driveUp.map(_.monday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnMonday(branch.driveUp.map(_.monday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnTuesday(branch.driveUp.map(_.tuesday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnTuesday(branch.driveUp.map(_.tuesday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnWednesday(branch.driveUp.map(_.wednesday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnWednesday(branch.driveUp.map(_.wednesday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnThursday(branch.driveUp.map(_.thursday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnThursday(branch.driveUp.map(_.thursday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnFriday(branch.driveUp.map(_.friday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnFriday(branch.driveUp.map(_.friday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnSaturday(branch.driveUp.map(_.saturday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnSaturday(branch.driveUp.map(_.saturday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnSunday(branch.driveUp.map(_.sunday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnSunday(branch.driveUp.map(_.sunday).map(_.closingTime).orNull)

            .mIsAccessible(isAccessibleString) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown

            .mBranchType(branch.branchType.orNull)
            .mMoreInfo(branch.moreInfo.orNull)
            .mPhoneNumber(branch.phoneNumber.orNull)
            .mIsDeleted(branch.isDeleted.getOrElse(mappedBranch.isDeleted.getOrElse(false)))

            .saveMe()
        }
      case _ =>
        tryo {
          // Insert...
          logger.info("Creating Branch...")
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
            .mLobbyHours(branch.lobbyString.map(_.hours).getOrElse("")) // null no good.
            .mDriveUpHours(branch.driveUpString.map(_.hours).getOrElse("")) // OK like this? only used by versions prior to v3.0.0
            .mBranchRoutingScheme(branch.branchRouting.map(_.scheme).orNull) //Added in V220
            .mBranchRoutingAddress(branch.branchRouting.map(_.address).orNull) //Added in V220
            .mLobbyOpeningTimeOnMonday(branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnMonday(branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnTuesday(branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnTuesday(branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnWednesday(branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnWednesday(branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnThursday(branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnThursday(branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnFriday(branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnFriday(branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnSaturday(branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnSaturday(branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnSunday(branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnSunday(branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00","00:00"))).map(_.closingTime).head)


            // Drive Up
            .mDriveUpOpeningTimeOnMonday(branch.driveUp.map(_.monday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnMonday(branch.driveUp.map(_.monday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnTuesday(branch.driveUp.map(_.tuesday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnTuesday(branch.driveUp.map(_.tuesday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnWednesday(branch.driveUp.map(_.wednesday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnWednesday(branch.driveUp.map(_.wednesday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnThursday(branch.driveUp.map(_.thursday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnThursday(branch.driveUp.map(_.thursday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnFriday(branch.driveUp.map(_.friday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnFriday(branch.driveUp.map(_.friday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnSaturday(branch.driveUp.map(_.saturday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnSaturday(branch.driveUp.map(_.saturday).map(_.closingTime).orNull)

            .mDriveUpOpeningTimeOnSunday(branch.driveUp.map(_.sunday).map(_.openingTime).orNull)
            .mDriveUpClosingTimeOnSunday(branch.driveUp.map(_.sunday).map(_.closingTime).orNull)

            .mIsAccessible(isAccessibleString) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown

            .mBranchType(branch.branchType.orNull)
            .mMoreInfo(branch.moreInfo.orNull)
            .mPhoneNumber(branch.phoneNumber.orNull)
            .mIsDeleted(branch.isDeleted.getOrElse(false))
            .saveMe()
        }
    }
    // Return the recently created / updated Branch from the database
    branchToReturn
  }


  // TODO This should accept a normal case class not "json" case class i.e. don't rely on REST json structures
  override def createOrUpdateAtm(atm: Atm): Box[AtmT] = {

    val isAccessibleString = optionBooleanToString(atm.isAccessible)
    val hasDepositCapabilityString = optionBooleanToString(atm.hasDepositCapability)

    //check the atm existence and update or insert data
    getAtm(atm.bankId, atm.atmId) match {
      case Full(mappedAtm) =>
        tryo {
          mappedAtm.mName(atm.name)
            .mLine1(atm.address.line1)
            .mLine2(atm.address.line2)
            .mLine3(atm.address.line3)
            .mCity(atm.address.city)
            .mCounty(atm.address.county.getOrElse(""))
            .mCountryCode(atm.address.countryCode)
            .mState(atm.address.state)
            .mPostCode(atm.address.postCode)
            .mlocationLatitude(atm.location.latitude)
            .mlocationLongitude(atm.location.longitude)
            .mLicenseId(atm.meta.license.id)
            .mLicenseName(atm.meta.license.name)
            .mOpeningTimeOnMonday(atm.OpeningTimeOnMonday.orNull)
            .mClosingTimeOnMonday(atm.ClosingTimeOnMonday.orNull)

            .mOpeningTimeOnTuesday(atm.OpeningTimeOnTuesday.orNull)
            .mClosingTimeOnTuesday(atm.ClosingTimeOnTuesday.orNull)

            .mOpeningTimeOnWednesday(atm.OpeningTimeOnWednesday.orNull)
            .mClosingTimeOnWednesday(atm.ClosingTimeOnWednesday.orNull)

            .mOpeningTimeOnThursday(atm.OpeningTimeOnThursday.orNull)
            .mClosingTimeOnThursday(atm.ClosingTimeOnThursday.orNull)

            .mOpeningTimeOnFriday(atm.OpeningTimeOnFriday.orNull)
            .mClosingTimeOnFriday(atm.ClosingTimeOnFriday.orNull)

            .mOpeningTimeOnSaturday(atm.OpeningTimeOnSaturday.orNull)
            .mClosingTimeOnSaturday(atm.ClosingTimeOnSaturday.orNull)

            .mOpeningTimeOnSunday(atm.OpeningTimeOnSunday.orNull)
            .mClosingTimeOnSunday(atm.ClosingTimeOnSunday.orNull)
            .mIsAccessible(isAccessibleString) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown
            .mLocatedAt(atm.locatedAt.orNull)
            .mMoreInfo(atm.moreInfo.orNull)
            .mHasDepositCapability(hasDepositCapabilityString)
            .saveMe()
        }
      case _ =>
        tryo {
          MappedAtm.create
            .mAtmId(atm.atmId.value)
            .mBankId(atm.bankId.value)
            .mName(atm.name)
            .mLine1(atm.address.line1)
            .mLine2(atm.address.line2)
            .mLine3(atm.address.line3)
            .mCity(atm.address.city)
            .mCounty(atm.address.county.getOrElse(""))
            .mCountryCode(atm.address.countryCode)
            .mState(atm.address.state)
            .mPostCode(atm.address.postCode)
            .mlocationLatitude(atm.location.latitude)
            .mlocationLongitude(atm.location.longitude)
            .mLicenseId(atm.meta.license.id)
            .mLicenseName(atm.meta.license.name)
            .mOpeningTimeOnMonday(atm.OpeningTimeOnMonday.orNull)
            .mClosingTimeOnMonday(atm.ClosingTimeOnMonday.orNull)

            .mOpeningTimeOnTuesday(atm.OpeningTimeOnTuesday.orNull)
            .mClosingTimeOnTuesday(atm.ClosingTimeOnTuesday.orNull)

            .mOpeningTimeOnWednesday(atm.OpeningTimeOnWednesday.orNull)
            .mClosingTimeOnWednesday(atm.ClosingTimeOnWednesday.orNull)

            .mOpeningTimeOnThursday(atm.OpeningTimeOnThursday.orNull)
            .mClosingTimeOnThursday(atm.ClosingTimeOnThursday.orNull)

            .mOpeningTimeOnFriday(atm.OpeningTimeOnFriday.orNull)
            .mClosingTimeOnFriday(atm.ClosingTimeOnFriday.orNull)

            .mOpeningTimeOnSaturday(atm.OpeningTimeOnSaturday.orNull)
            .mClosingTimeOnSaturday(atm.ClosingTimeOnSaturday.orNull)

            .mOpeningTimeOnSunday(atm.OpeningTimeOnSunday.orNull)
            .mClosingTimeOnSunday(atm.ClosingTimeOnSunday.orNull)
            .mIsAccessible(isAccessibleString) // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown
            .mLocatedAt(atm.locatedAt.orNull)
            .mMoreInfo(atm.moreInfo.orNull)
            .mHasDepositCapability(hasDepositCapabilityString)
            .saveMe()
        }
    }
  }



  override def createOrUpdateProduct(bankId : String,
                                     code : String,
                                     parentProductCode : Option[String],
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
          parentProductCode match {
            case Some(ppc) => mappedProduct.mParentProductCode(ppc)
            case None =>
          }
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
          val product = MappedProduct.create
          product.mName(name)
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
          parentProductCode match {
            case Some(ppc) => product.mParentProductCode(ppc)
            case None =>
          }
          product.saveMe()
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

  override def getBranchesFuture(bankId: BankId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = {
    Future {
      Full(MappedBranch.findAll(By(MappedBranch.mBankId, bankId.value)), callContext)
    }
  }

  override def getBranchFuture(bankId : BankId, branchId: BranchId, callContext: Option[CallContext]) = {
    Future {
      getBranch(bankId, branchId).map(branch=>(branch, callContext))
    }
  }

  override def getAtm(bankId : BankId, atmId: AtmId) : Box[MappedAtm]= {
    MappedAtm
      .find(
        By(MappedAtm.mBankId, bankId.value),
        By(MappedAtm.mAtmId, atmId.value))
  }
  override def getAtmFuture(bankId : BankId, atmId: AtmId, callContext: Option[CallContext]) = 
    Future {
      getAtm(bankId, atmId).map(atm =>(atm, callContext))
    }

  override def getAtmsFuture(bankId: BankId, callContext: Option[CallContext], queryParams: OBPQueryParam*)= {
    Future {
      Full(MappedAtm.findAll(By(MappedAtm.mBankId, bankId.value)),callContext)
    }
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

  override def createOrUpdateFXRate(
                                     bankId: String,
                                     fromCurrencyCode: String,
                                     toCurrencyCode: String,
                                     conversionValue: Double,
                                     inverseConversionValue: Double,
                                     effectiveDate: Date
                                   ): Box[FXRate] = {
    val fxRateFromTo = MappedFXRate.find(
      By(MappedFXRate.mBankId, bankId),
      By(MappedFXRate.mFromCurrencyCode, fromCurrencyCode),
      By(MappedFXRate.mToCurrencyCode, toCurrencyCode)
    )
    fxRateFromTo match {
      case Full(x) =>
        tryo {
          x
            .mBankId(bankId)
            .mFromCurrencyCode(fromCurrencyCode)
            .mToCurrencyCode(toCurrencyCode)
            .mConversionValue(conversionValue)
            .mInverseConversionValue(inverseConversionValue)
            .mEffectiveDate(effectiveDate)
            .saveMe()
        } ?~! UpdateFxRateError
      case Empty =>
        tryo {
          MappedFXRate.create
            .mBankId(bankId)
            .mFromCurrencyCode(fromCurrencyCode)
            .mToCurrencyCode(toCurrencyCode)
            .mConversionValue(conversionValue)
            .mInverseConversionValue(inverseConversionValue)
            .mEffectiveDate(effectiveDate)
            .saveMe()
        } ?~! CreateFxRateError
      case _ =>
        Failure("UnknownFxRateError")
    }
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
        val fromAccountCurrency: String = getBankAccount(bankId, accountId).openOrThrowException(attemptedToOpenAnEmptyBox).currency
        TransactionRequestTypeChargeMock(transactionRequestType.value, bankId.value, fromAccountCurrency, "0.00", "Warning! Default value!")
    }

    Full(transactionRequestTypeCharge)
  }

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): Box[(List[CounterpartyTrait], Option[CallContext])] = {
    Counterparties.counterparties.vend.getCounterparties(thisBankId, thisAccountId, viewId).map(counterparties =>(counterparties, callContext))
  }
  override def getCounterpartiesFuture(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): OBPReturnType[Box[List[CounterpartyTrait]]] = Future {
    (getCounterparties(thisBankId, thisAccountId, viewId, callContext) map (i => i._1), callContext)
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
  
  override def createCounterparty(
    name: String,
    description: String,
    createdByUserId: String,
    thisBankId: String,
    thisAccountId: String,
    thisViewId: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String,
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String,
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    isBeneficiary:Boolean,
    bespoke: List[CounterpartyBespoke],
    callContext: Option[CallContext] = None): Box[(CounterpartyTrait, Option[CallContext])] =
    Counterparties.counterparties.vend.createCounterparty(
      createdByUserId = createdByUserId,
      thisBankId = thisBankId,
      thisAccountId = thisAccountId,
      thisViewId = thisViewId,
      name = name,
      otherAccountRoutingScheme = otherAccountRoutingScheme,
      otherAccountRoutingAddress = otherAccountRoutingAddress,
      otherBankRoutingScheme = otherBankRoutingScheme,
      otherBankRoutingAddress = otherBankRoutingAddress,
      otherBranchRoutingScheme = otherBranchRoutingScheme,
      otherBranchRoutingAddress = otherBranchRoutingAddress,
      isBeneficiary = isBeneficiary,
      otherAccountSecondaryRoutingScheme = otherAccountSecondaryRoutingScheme,
      otherAccountSecondaryRoutingAddress = otherAccountSecondaryRoutingAddress,
      description = description,
      bespoke = bespoke
    ).map(counterparty => (counterparty, callContext))

  override def checkCustomerNumberAvailableFuture(
    bankId: BankId,
    customerNumber: String
  ) = Future{tryo {Customer.customerProvider.vend.checkCustomerNumberAvailable(bankId, customerNumber)} }
  
  
  override def createCustomerFuture(
                               bankId: BankId,
                               legalName: String,
                               mobileNumber: String,
                               email: String,
                               faceImage:
                               CustomerFaceImageTrait,
                               dateOfBirth: Date,
                               relationshipStatus: String,
                               dependents: Int,
                               dobOfDependents: List[Date],
                               highestEducationAttained: String,
                               employmentStatus: String,
                               kycStatus: Boolean,
                               lastOkDate: Date,
                               creditRating: Option[CreditRatingTrait],
                               creditLimit: Option[AmountOfMoneyTrait],
                               callContext: Option[CallContext] = None,
                               title: String,
                               branchId: String,
                               nameSuffix: String): Future[Box[Customer]] = Future{
    Customer.customerProvider.vend.addCustomer(
      bankId,
      Random.nextInt(Integer.MAX_VALUE).toString,
      legalName,
      mobileNumber,
      email,
      faceImage,
      dateOfBirth,
      relationshipStatus,
      dependents,
      dobOfDependents,
      highestEducationAttained,
      employmentStatus,
      kycStatus,
      lastOkDate,
      creditRating,
      creditLimit,
      title,
      branchId,
      nameSuffix
    )
  }
  
  def getCustomersByUserId(userId: String, callContext: Option[CallContext]): Box[(List[Customer], Option[CallContext])] = {
    Full((Customer.customerProvider.vend.getCustomersByUserId(userId), callContext))
  }  
  
  override def getCustomersByUserIdFuture(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer],Option[CallContext])]]=
    Customer.customerProvider.vend.getCustomersByUserIdFuture(userId) map {
      customersBox =>(customersBox.map(customers=>(customers,callContext)))
    }

  override def getCustomerByCustomerId(customerId: String, callContext: Option[CallContext])  =
    Customer.customerProvider.vend.getCustomerByCustomerId(customerId) map {
      customersBox =>(customersBox,callContext)
    }
  
  override def getCustomerByCustomerIdFuture(customerId : String, callContext: Option[CallContext]): Future[Box[(Customer,Option[CallContext])]] =
    Customer.customerProvider.vend.getCustomerByCustomerIdFuture(customerId)  map {
      i => i.map(
        customer => (customer, callContext)
      )
    }
  override def getCustomerByCustomerNumberFuture(customerNumber : String, bankId : BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] =
    Customer.customerProvider.vend.getCustomerByCustomerNumberFuture(customerNumber, bankId)  map {
      i => i.map(
        customer => (customer, callContext)
      )
    }

  override def getCustomersFuture(bankId : BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] =
    Customer.customerProvider.vend.getCustomersFuture(bankId, queryParams)

  override def getCustomerAddress(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAddress]]] =
    CustomerAddress.address.vend.getAddress(customerId) map {
      (_, callContext)
    }
  override def createCustomerAddress(customerId: String,
                                     line1: String,
                                     line2: String,
                                     line3: String,
                                     city: String,
                                     county: String,
                                     state: String,
                                     postcode: String,
                                     countryCode: String,
                                     tags: String,
                                     status: String,
                                     callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] =
    CustomerAddress.address.vend.createAddress(
      customerId,
      line1,
      line2,
      line3,
      city,
      county,
      state,
      postcode,
      countryCode,
      tags,
      status) map {
      (_, callContext)
    }
  override def updateCustomerAddress(customerAddressId: String,
                                     line1: String,
                                     line2: String,
                                     line3: String,
                                     city: String,
                                     county: String,
                                     state: String,
                                     postcode: String,
                                     countryCode: String,
                                     tags: String,
                                     status: String,
                                     callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] =
    CustomerAddress.address.vend.updateAddress(
      customerAddressId,
      line1,
      line2,
      line3,
      city,
      county,
      state,
      postcode,
      countryCode,
      tags,
      status) map {
      (_, callContext)
    }
  override def deleteCustomerAddress(customerAddressId : String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    CustomerAddress.address.vend.deleteAddress(customerAddressId) map {
      (_, callContext)
    }

  override def getTaxResidence(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[TaxResidence]]] =
    TaxResidence.taxResidence.vend.getTaxResidence(customerId) map {
      (_, callContext)
    }
  override def createTaxResidence(customerId : String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[TaxResidence]] =
    TaxResidence.taxResidence.vend.createTaxResidence(customerId, domain, taxNumber) map {
      (_, callContext)
    }
  override def deleteTaxResidence(taxResidenceId : String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    TaxResidence.taxResidence.vend.deleteTaxResidence(taxResidenceId) map {
      (_, callContext)
    }

  override def getCheckbookOrdersFuture(
    bankId: String, 
    accountId: String, 
    callContext: Option[CallContext]
  ) = Future {
    Full(SwaggerDefinitionsJSON.checkbookOrdersJson, callContext)
  }
  
  
  override  def getStatusOfCreditCardOrderFuture(
    bankId: String, 
    accountId: String, 
    callContext: Option[CallContext]
  ) = Future
  {
    Full(List(SwaggerDefinitionsJSON.cardObjectJson), callContext)
  }


  override def createUserAuthContext(userId: String,
                                     key: String,
                                     value: String,
                                     callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContext]] =
    UserAuthContextProvider.userAuthContextProvider.vend.createUserAuthContext(userId, key, value) map {
      (_, callContext)
    }
  override def createUserAuthContextUpdate(userId: String,
                                           key: String,
                                           value: String,
                                           callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContextUpdate]] =
    UserAuthContextUpdateProvider.userAuthContextUpdateProvider.vend.createUserAuthContextUpdates(userId, key, value) map {
      (_, callContext)
    }
  override def getUserAuthContexts(userId : String,
                                   callContext: Option[CallContext]): OBPReturnType[Box[List[UserAuthContext]]] =
    UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContexts(userId) map {
      (_, callContext)
    }

  override def deleteUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    UserAuthContextProvider.userAuthContextProvider.vend.deleteUserAuthContexts(userId) map{
      (_, callContext)
    }

  override def deleteUserAuthContextById(userAuthContextId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    UserAuthContextProvider.userAuthContextProvider.vend.deleteUserAuthContextById(userAuthContextId) map{
      (_, callContext)
    }
  
  
  override def createOrUpdateProductAttribute(
      bankId: BankId,
      productCode: ProductCode,
      productAttributeId: Option[String],
      name: String,
      attributType: ProductAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[Box[ProductAttribute]] =
    ProductAttribute.productAttributeProvider.vend.createOrUpdateProductAttribute(
      bankId: BankId,
      productCode: ProductCode,
      productAttributeId: Option[String],
      name: String,
      attributType: ProductAttributeType.Value,
      value: String ) map{
        (_, callContext)
    }
  
  override def getProductAttributesByBankAndCode(
                                                  bank: BankId,
                                                  productCode: ProductCode,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[ProductAttribute]]] = 
    ProductAttribute.productAttributeProvider.vend.getProductAttributesFromProvider(bank: BankId, productCode: ProductCode) map {
      (_, callContext)
    }
  
  override def getProductAttributeById(
    productAttributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ProductAttribute]] = 
    ProductAttribute.productAttributeProvider.vend.getProductAttributeById(productAttributeId: String) map{
      (_, callContext)
    }
  
  override def deleteProductAttribute(
    productAttributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = 
    ProductAttribute.productAttributeProvider.vend.deleteProductAttribute(productAttributeId: String) map {
      (_, callContext)
    }

  override def createOrUpdateAccountAttribute(
                                               bankId: BankId,
                                               accountId: AccountId,
                                               productCode: ProductCode,
                                               accountAttributeId: Option[String],
                                               name: String,
                                               attributType: AccountAttributeType.Value,
                                               value: String,
                                               callContext: Option[CallContext]
                                             ): OBPReturnType[Box[AccountAttribute]] = {
    AccountAttribute.accountAttributeProvider.vend.createOrUpdateAccountAttribute(bankId: BankId,
                                                                                  accountId: AccountId,
                                                                                  productCode: ProductCode,
                                                                                  accountAttributeId: Option[String],
                                                                                  name: String,
                                                                                  attributType: AccountAttributeType.Value,
                                                                                  value: String) map { (_, callContext) }
  }


  override def createAccountApplication(
    productCode: ProductCode,
    userId: Option[String],
    customerId: Option[String],
    callContext: Option[CallContext]
    ): OBPReturnType[Box[AccountApplication]] =
    AccountApplication.accountApplication.vend.createAccountApplication(productCode, userId, customerId) map {
      (_, callContext)
    }

  override def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[Box[List[AccountApplication]]] =
    AccountApplication.accountApplication.vend.getAll() map {
      (_, callContext)
    }

  override def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    AccountApplication.accountApplication.vend.getById(accountApplicationId) map {
      (_, callContext)
    }

  override  def updateAccountApplicationStatus(accountApplicationId:String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    AccountApplication.accountApplication.vend.updateStatus(accountApplicationId, status) map {
      (_, callContext)
    }
  
  override  def getOrCreateProductCollection(collectionCode: String, productCodes: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    ProductCollection.productCollection.vend.getOrCreateProductCollection(collectionCode, productCodes) map {
      (_, callContext)
    } 
  
  override  def getProductCollection(collectionCode: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    ProductCollection.productCollection.vend.getProductCollection(collectionCode) map {
      (_, callContext)
    } 
  
  override  def getOrCreateProductCollectionItem(collectionCode: String,
                                                 memberProductCodes: List[String],
                                                 callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    ProductCollectionItem.productCollectionItem.vend.getOrCreateProductCollectionItem(collectionCode, memberProductCodes) map {
      (_, callContext)
    }
  
  override  def getProductCollectionItem(collectionCode: String,
                                         callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    ProductCollectionItem.productCollectionItem.vend.getProductCollectionItems(collectionCode) map {
      pci => (pci, callContext)
    }  
  override def getProductCollectionItemsTree(collectionCode: String, 
                                              bankId: String,
                                              callContext: Option[CallContext]): OBPReturnType[Box[List[(ProductCollectionItem, Product, List[ProductAttribute])]]] =
    ProductCollectionItem.productCollectionItem.vend.getProductCollectionItemsTree(collectionCode, bankId) map {
      (_, callContext)
    }
  
  override def createMeeting(
      bankId: BankId,
      staffUser: User,
      customerUser: User,
      providerId: String,
      purposeId: String,
      when: Date,
      sessionId: String,
      customerToken: String,
      staffToken: String,
      creator: ContactDetails,
      invitees: List[Invitee],
      callContext: Option[CallContext]
    ): OBPReturnType[Box[Meeting]] = 
    Future{(
      Meeting.meetingProvider.vend.createMeeting(
      bankId: BankId,
      staffUser: User,
      customerUser: User,
      providerId: String,
      purposeId: String,
      when: Date,
      sessionId: String,
      customerToken: String,
      staffToken: String,
      creator: ContactDetails,
      invitees: List[Invitee],
    ),callContext)}
  
  override def getMeetings(
    bankId : BankId, 
    user: User,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[Meeting]]] = 
    Future{(
      Meeting.meetingProvider.vend.getMeetings(
        bankId : BankId,
        user: User),
      callContext)}
  
  override def getMeeting(
    bankId: BankId,
    user: User, 
    meetingId : String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Meeting]]=
    Future{(
      Meeting.meetingProvider.vend.getMeeting(
        bankId: BankId,
        user: User,
        meetingId : String), 
      callContext)}
}

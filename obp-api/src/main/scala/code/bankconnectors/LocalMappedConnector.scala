package code.bankconnectors

import java.util.Date
import java.util.UUID.randomUUID

import code.DynamicData.DynamicDataProvider
import code.accountapplication.AccountApplicationX
import code.accountattribute.AccountAttributeX
import code.accountholders.AccountHolders
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.cache.Caching
import code.api.util.APIUtil.{OBPReturnType, isValidCurrencyISOCode, saveConnectorMetric, stringOrNull}
import code.api.util.ErrorMessages._
import code.api.util._
import code.atms.Atms.Atm
import code.atms.MappedAtm
import code.branches.Branches.Branch
import code.branches.MappedBranch
import code.cardattribute.CardAttributeX
import code.cards.MappedPhysicalCard
import code.context.{UserAuthContextProvider, UserAuthContextUpdateProvider}
import code.customer._
import code.customeraddress.CustomerAddressX
import code.customerattribute.{CustomerAttributeX, MappedCustomerAttribute}
import code.directdebit.{DirectDebitTrait, DirectDebits}
import code.dynamicEntity.{DynamicEntityProvider, DynamicEntityT}
import code.fx.{FXRate, MappedFXRate, fx}
import code.kycchecks.KycChecks
import code.kycdocuments.KycDocuments
import code.kycmedias.KycMedias
import code.kycstatuses.KycStatuses
import code.management.ImporterAPI.ImporterTransaction
import code.meetings.Meetings
import code.metadata.comments.Comments
import code.metadata.counterparties.Counterparties
import code.metadata.narrative.Narrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.model._
import code.model.dataAccess._
import code.productattribute.ProductAttributeX
import code.productcollection.ProductCollectionX
import code.productcollectionitem.ProductCollectionItems
import code.products.MappedProduct
import code.standingorders.{StandingOrderTrait, StandingOrders}
import code.taxresidence.TaxResidenceX
import code.transaction.MappedTransaction
import code.transactionChallenge.ExpectedChallengeAnswer
import code.transactionattribute.TransactionAttributeX
import code.transactionrequests._
import code.users.Users
import code.util.Helper
import code.util.Helper.{MdcLoggable, _}
import code.views.Views
import com.google.common.cache.CacheBuilder
import com.nexmo.client.NexmoClient
import com.nexmo.client.sms.messages.TextMessage
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums._
import com.openbankproject.commons.model.{AccountApplication, AccountAttribute, Product, ProductAttribute, ProductCollectionItem, TaxResidence, _}
import com.tesobe.CacheKeyFromArguments
import com.tesobe.model.UpdateBankAccount
import net.liftweb.common._
import net.liftweb.json
import net.liftweb.json.{JArray, JBool, JObject, JValue}
import net.liftweb.mapper.{By, _}
import net.liftweb.util.Helpers.{tryo, _}
import net.liftweb.util.Mailer
import net.liftweb.util.Mailer.{From, PlainMailBodyType, Subject, To}
import org.apache.commons.lang3.StringUtils
import org.mindrot.jbcrypt.BCrypt
import scalacache.ScalaCache
import scalacache.guava.GuavaCache

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.{BigDecimal, BigInt}
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
  override def getAdapterInfo(callContext: Option[CallContext]) : Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = Future(
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
  override def createChallenge(bankId: BankId,
                               accountId: AccountId,
                               userId: String,
                               transactionRequestType: TransactionRequestType,
                               transactionRequestId: String,
                               scaMethod: Option[SCA],
                               callContext: Option[CallContext]) = Future {
    createChallengeInternal(bankId: BankId,
      accountId: AccountId,
      userId: String,
      transactionRequestType: TransactionRequestType,
      transactionRequestId: String,
      scaMethod: Option[SCA],
      callContext: Option[CallContext])
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
  override def createChallenges(bankId: BankId,
                               accountId: AccountId,
                               userIds: List[String],
                               transactionRequestType: TransactionRequestType,
                               transactionRequestId: String,
                               scaMethod: Option[SCA],
                               callContext: Option[CallContext]) = Future {
    val challenges = for {
      userId <- userIds
    } yield {
      val (challengeId, _) = createChallengeInternal(
        bankId,
        accountId,
        userId,
        transactionRequestType: TransactionRequestType,
        transactionRequestId,
        scaMethod,
        callContext
      )
      challengeId.toList
    }
    (Full(challenges.flatten), callContext)
  }
  private def createChallengeInternal(bankId: BankId, 
                               accountId: AccountId, 
                               userId: String, 
                               transactionRequestType: TransactionRequestType, 
                               transactionRequestId: String,
                               scaMethod: Option[SCA], 
                               callContext: Option[CallContext]) = {
    def createHashedPassword(challengeAnswer: String) = {
      val challengeId = APIUtil.generateUUID()
      val salt = BCrypt.gensalt()
      val challengeAnswerHashed = BCrypt.hashpw(challengeAnswer, salt).substring(0, 44)
      ExpectedChallengeAnswer.expectedChallengeAnswerProvider.vend.saveExpectedChallengeAnswer(
        challengeId, 
        transactionRequestId, 
        salt, 
        challengeAnswerHashed, 
        userId)
      (Full(challengeId), callContext)
    }
    scaMethod match {
      case Some(StrongCustomerAuthentication.UNDEFINED) =>
        (Failure(ScaMethodNotDefined), callContext)
      case Some(StrongCustomerAuthentication.DUMMY) => 
        createHashedPassword("123")
      case Some(StrongCustomerAuthentication.EMAIL) =>
        val challengeAnswer = Random.nextInt(99999999).toString()
        val hashedPassword = createHashedPassword(challengeAnswer)
        APIUtil.getEmailsByUserId(userId) map {
          pair =>
            val params = PlainMailBodyType(s"Your OTP challenge : ${challengeAnswer}") :: List(To(pair._2))
            Mailer.sendMail(From("challenge@tesobe.com"), Subject("Challenge"), params :_*)
        }
        hashedPassword
      case Some(StrongCustomerAuthentication.SMS) =>
        val challengeAnswer = Random.nextInt(99999999).toString()
        val hashedPassword = createHashedPassword(challengeAnswer)
        val sendingResult: Seq[Box[Boolean]] = APIUtil.getPhoneNumbersByUserId(userId) map {
          tuple =>
            for {
              smsProviderApiKey <- APIUtil.getPropsValue("sca_phone_api_key") ?~! s"$MissingPropsValueAtThisInstance sca_phone_api_key"
              smsProviderApiSecret <- APIUtil.getPropsValue("sca_phone_api_secret") ?~! s"$MissingPropsValueAtThisInstance sca_phone_api_secret"
              client = new NexmoClient.Builder()
                .apiKey(smsProviderApiKey)
                .apiSecret(smsProviderApiSecret)
                .build();
              phoneNumber = tuple._2
              messageText = s"Your consent challenge : ${challengeAnswer}";
              message = new TextMessage("OBP-API", phoneNumber, messageText);
              response <- tryo(client.getSmsClient().submitMessage(message))
              failMsg = s"$SmsServerNotResponding: $phoneNumber. Or Please to use EMAIL first."
              _ <- Helper.booleanToBox (
                response.getMessages.get(0).getStatus == com.nexmo.client.sms.MessageStatus.OK,
                failMsg
              )
            } yield true
        }
        val errorMessage = sendingResult.filter(_.isInstanceOf[Failure]).map(_.asInstanceOf[Failure].msg)

        if(sendingResult.forall(_ == Full(true))) hashedPassword else (Failure(errorMessage.toSet.mkString(" <- ")), callContext)
      case None => // All versions which precede v4.0.0 i.e. to keep backward compatibility 
        createHashedPassword("123")
    }
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
  override def getBankLegacy(bankId: BankId, callContext: Option[CallContext]) = saveConnectorMetric {
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

  override def getBank(bankId : BankId, callContext: Option[CallContext]) = Future {
    getBankLegacy(bankId, callContext)
  }
  

  override def getBanksLegacy(callContext: Option[CallContext]): Box[(List[MappedBank], Option[CallContext])] = saveConnectorMetric {
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

  override def getBanks(callContext: Option[CallContext]) = Future {
    getBanksLegacy(callContext)
  }

  //This method is only for testing now. Normall this method 
  override def getBankAccountsForUserLegacy(username: String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])]= {
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

  override def getBankAccountsForUser(username: String, callContext: Option[CallContext]): Future[Box[(List[InboundAccount], Option[CallContext])]] = Future{
    getBankAccountsForUserLegacy(username,callContext)
  }

  override def getTransactionLegacy(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]) = {

    updateAccountTransactions(bankId, accountId)

    MappedTransaction.find(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountId.value),
      By(MappedTransaction.transactionId, transactionId.value)).flatMap(_.toTransaction)
      .map(transaction => (transaction, callContext))
  }

  override def getTransactionsLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]) = {

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
  
  override def getTransactionsCore(bankId: BankId, accountId: AccountId, queryParams:  List[OBPQueryParam], callContext: Option[CallContext]) =
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

    Future{
      (getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams), callContext)
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

  override def getBankAccountLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
    getBankAccountCommon(bankId, accountId, callContext)
  }

  override def getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : OBPReturnType[Box[BankAccount]]= Future
  {
    val accountAndCallcontext = getBankAccountLegacy(bankId : BankId, accountId : AccountId, callContext: Option[CallContext])
    (accountAndCallcontext.map(_._1), accountAndCallcontext.map(_._2).getOrElse(callContext))
  }

  override def getBankAccountByIban(iban : String, callContext: Option[CallContext]) : OBPReturnType[Box[BankAccount]]= Future
  {
    (MappedBankAccount
      .find(By(MappedBankAccount.accountIban, iban))
      .map(
        account =>
          account
            .mAccountRoutingScheme(APIUtil.ValueOrOBP(account.accountRoutingScheme))
            .mAccountRoutingAddress(APIUtil.ValueOrOBPId(account.accountRoutingAddress, account.accountId.value))
      ), callContext)
  }
  override def getBankAccountByRouting(scheme : String, address : String, callContext: Option[CallContext]) : Box[(BankAccount, Option[CallContext])]= 
    (MappedBankAccount
      .find(By(MappedBankAccount.mAccountRoutingScheme, scheme), By(MappedBankAccount.mAccountRoutingAddress, address))
      .map(
        account =>
          account
            .mAccountRoutingScheme(APIUtil.ValueOrOBP(account.accountRoutingScheme))
            .mAccountRoutingAddress(APIUtil.ValueOrOBPId(account.accountRoutingAddress, account.accountId.value))
      )).map(a => (a, callContext))
    
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

  override def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) = {
    Future {
      (Full(
        bankIdAccountIds.map(
          bankIdAccountId =>
            getBankAccount(
              bankIdAccountId.bankId,
              bankIdAccountId.accountId
            ).openOrThrowException(s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"))
      ),callContext)
    }
  }
  
  override def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) = 
    Future {
      val accountsBalances = for{
        bankIdAccountId <- bankIdAccountIds
        bankAccount <- getBankAccount(bankIdAccountId.bankId, bankIdAccountId.accountId) ?~! s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"
        accountBalance = AccountBalance(
          id = bankAccount.accountId.value,
          label = bankAccount.label,
          bankId = bankAccount.bankId.value,
          accountRoutings = bankAccount.accountRoutings.map(accountRounting => AccountRouting(accountRounting.scheme, accountRounting.address)),
          balance = AmountOfMoney(bankAccount.currency, bankAccount.balance.toString())
        )
      } yield {
        (accountBalance)
      }
      
      val allCurrencies = accountsBalances.map(_.balance.currency)
      val mostCommonCurrency = if (allCurrencies.isEmpty) "EUR" else allCurrencies.groupBy(identity).mapValues(_.size).maxBy(_._2)._1
      
      val allCommonCurrencyBalances = for {
        accountBalance <- accountsBalances
        requestAccountCurrency = accountBalance.balance.currency
        requestAccountAmount = BigDecimal(accountBalance.balance.amount)
        //From change from requestAccount Currency to mostCommon Currency
        rate <- fx.exchangeRate(requestAccountCurrency, mostCommonCurrency)
        requestChangedCurrencyAmount = fx.convert(requestAccountAmount, Some(rate))
      }yield {
        requestChangedCurrencyAmount 
      }

      val overallBalance = allCommonCurrencyBalances.sum

      (Full(AccountsBalances(
        accounts = accountsBalances,
        overallBalance = AmountOfMoney(
          mostCommonCurrency,
          overallBalance.toString
        ),
        overallBalanceDate = now
      )), callContext)
    }
  
  override def checkBankAccountExistsLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) = {
    getBankAccountLegacy(bankId: BankId, accountId: AccountId, callContext)
  }  
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) = 
    Future {
      (getBankAccountLegacy(bankId: BankId, accountId: AccountId, callContext).map(_._1), callContext)
    }
  
  override def getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[(List[CoreAccount], Option[CallContext])]= {
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

  override def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]] = {
    Future {getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext])}
  }
  // localConnector/getBankAccountsHeld/bankIdAccountIds/{bankIdAccountIds}
  override def getBankAccountsHeldLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[List[AccountHeld]]= {
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
  
  override def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext])  = {
    Future {(getBankAccountsHeldLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]),callContext)}
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
        f.bank(bankId.value).theAccountId(accountId.value).accountCurrency(currency.toUpperCase).saveMe()
      case _ =>
        MappedBankAccount.create.bank(bankId.value).theAccountId(accountId.value).accountCurrency(currency.toUpperCase).saveMe()
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
    getCounterpartyByCounterpartyId(CounterpartyId(counterpartyId), callContext)
  }
  
  override def getCounterpartyByCounterpartyIdLegacy(counterpartyId: CounterpartyId, callContext: Option[CallContext]) ={
    Counterparties.counterparties.vend.getCounterparty(counterpartyId.value).map(counterparty => (counterparty, callContext))
  }
  
  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]) = Future{
    (Counterparties.counterparties.vend.getCounterparty(counterpartyId.value),callContext)
  }

  override def getCounterpartyByIban(iban: String, callContext: Option[CallContext]) =  {
    Future (Counterparties.counterparties.vend.getCounterpartyByIban(iban), callContext)
  }


  override def getPhysicalCards(user: User) = {
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCards(user)
    val cardList = for (l <- list) yield
      new PhysicalCard(
        cardId=l.cardId,
        bankId=l.bankId,
        bankCardNumber = l.bankCardNumber,
        cardType = l.cardType,
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
        posted = l.posted,
        customerId = l.customerId
      )
    Full(cardList)
  }

  override def getPhysicalCardsForBank(bank: Bank, user : User, queryParams: List[OBPQueryParam], callContext:Option[CallContext]) = Future{(
    getPhysicalCardsForBankLegacy(bank: Bank, user: User, queryParams),
    callContext
  )}
  
  override def getPhysicalCardsForBankLegacy(bank: Bank, user: User, queryParams: List[OBPQueryParam])= {
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCardsForBank(bank, user, queryParams)
    val cardList = for (l <- list) yield
      new PhysicalCard(
        cardId = l.cardId,
        bankId= l.bankId,
        bankCardNumber = l.bankCardNumber,
        cardType = l.cardType,
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
        posted = l.posted,
        customerId = l.customerId
      )
    Full(cardList)
  }

  override def getPhysicalCardForBank(bankId: BankId, cardId: String,  callContext:Option[CallContext])= Future {
    (code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCardForBank(bankId: BankId, cardId: String, callContext),
    callContext)
  }

  override def deletePhysicalCardForBank(bankId: BankId, cardId: String,  callContext:Option[CallContext])= Future {
    (code.cards.PhysicalCard.physicalCardProvider.vend.deletePhysicalCardForBank(bankId: BankId, cardId: String, callContext),
      callContext)
  }
  
  override def createPhysicalCard(
    bankCardNumber: String,
    nameOnCard: String,
    cardType: String,
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
    posted: Option[CardPostedInfo],
    customerId: String,
    callContext: Option[CallContext]) =  Future {
    (createPhysicalCardLegacy(
      bankCardNumber: String,
      nameOnCard: String,
      cardType: String,
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
      posted: Option[CardPostedInfo],
      customerId: String,
      callContext: Option[CallContext]), 
      callContext)
  }
  

  override def createPhysicalCardLegacy(
    bankCardNumber: String,
    nameOnCard: String,
    cardType: String,
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
    posted: Option[CardPostedInfo],
    customerId: String,
    callContext: Option[CallContext]) : Box[PhysicalCard] = {
    val physicalCardBox: Box[MappedPhysicalCard] = code.cards.PhysicalCard.physicalCardProvider.vend.createPhysicalCard(
      bankCardNumber: String,
      nameOnCard: String,
      cardType: String,
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
      posted: Option[CardPostedInfo],
      customerId: String,
      callContext: Option[CallContext])
    
    for (l <- physicalCardBox) yield
    new PhysicalCard(
      cardId = l.cardId,
      bankId = l.bankId,
      bankCardNumber = l.bankCardNumber,
      cardType = l.cardType,
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
      posted = l.posted,
      customerId = l.customerId
    )
  }

  override def updatePhysicalCard(
    cardId: String,
    bankCardNumber: String,
    nameOnCard: String,
    cardType: String,
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
    posted: Option[CardPostedInfo],
    customerId: String,
    callContext: Option[CallContext]
  ) = Future {(
    code.cards.PhysicalCard.physicalCardProvider.vend.updatePhysicalCard(
      cardId:String,
      bankCardNumber: String,
      nameOnCard: String,
      cardType: String,
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
      posted: Option[CardPostedInfo],
      customerId: String,
      callContext: Option[CallContext]), 
      callContext)
  }

  override def getCardAttributeById(cardAttributeId: String, callContext:Option[CallContext]) = {
    CardAttributeX.cardAttributeProvider.vend.getCardAttributeById(cardAttributeId: String)map { (_, callContext) }
  }
  
  override def createOrUpdateCardAttribute(
    bankId: Option[BankId],
    cardId: Option[String],
    cardAttributeId: Option[String],
    name: String,
    attributeType: CardAttributeType.Value,
    value: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CardAttribute]] = {
    CardAttributeX.cardAttributeProvider.vend.createOrUpdateCardAttribute(
      bankId: Option[BankId],
      cardId: Option[String],
      cardAttributeId: Option[String],
      name: String,
      attributeType: CardAttributeType.Value,
      value: String)map { (_, callContext) }
  }

  override def getCardAttributesFromProvider(
    cardId: String, 
    callContext: Option[CallContext]): OBPReturnType[Box[List[CardAttribute]]] = {
    CardAttributeX.cardAttributeProvider.vend.getCardAttributesFromProvider(cardId: String) map { (_, callContext) }
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

  override def makeHistoricalPayment(
    fromAccount: BankAccount,
    toAccount: BankAccount,
    posted: Date,
    completed: Date,
    amount: BigDecimal,
    description: String,
    transactionRequestType: String,
    chargePolicy: String,
    callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]]= {
    for{
      rate <- NewStyle.function.tryons(s"$InvalidCurrency The requested currency conversion (${fromAccount.currency} to ${fromAccount.currency}) is not supported.", 400, callContext) {
        fx.exchangeRate(fromAccount.currency, toAccount.currency, Some(fromAccount.bankId.value))}
      fromTransAmt = -amount//from fromAccount balance should decrease
      toTransAmt = fx.convert(amount, rate)
      (sentTransactionId, callContext) <- saveHistoricalTransaction(
        fromAccount: BankAccount,
        toAccount: BankAccount,
        posted: Date,
        completed: Date,
        amount = fromTransAmt,
        description: String,
        transactionRequestType: String,
        chargePolicy: String,
        callContext: Option[CallContext]
      )
      (_sentTransactionId, callContext) <- saveHistoricalTransaction(
        toAccount: BankAccount,
        fromAccount: BankAccount,
        posted: Date,
        completed: Date,
        amount = toTransAmt,
        description: String,
        transactionRequestType: String,
        chargePolicy: String,
        callContext: Option[CallContext])
    } yield{
      (sentTransactionId, callContext)
    }
  }
  
  
  private def saveHistoricalTransaction(
    fromAccount: BankAccount,
    toAccount: BankAccount,
    posted: Date,
    completed: Date,
    amount: BigDecimal,
    description: String,
    transactionRequestType: String,
    chargePolicy: String,
    callContext: Option[CallContext]
  ) = Future {(
    for{
      currency <- Full(fromAccount.currency)
      //update the balance of the fromAccount for which a transaction is being created
      newAccountBalance <- Full(Helper.convertToSmallestCurrencyUnits(fromAccount.balance, currency) + Helper.convertToSmallestCurrencyUnits(amount, currency))

      //Here is the `LocalMappedConnector`, once get this point, fromAccount must be a mappedBankAccount. So can use asInstanceOf.... 
      _ <- tryo(fromAccount.asInstanceOf[MappedBankAccount].accountBalance(newAccountBalance).save()) ?~! UpdateBankAccountException

      mappedTransaction <- tryo(MappedTransaction.create
        .bank(fromAccount.bankId.value)
        .account(fromAccount.accountId.value)
        .transactionType(transactionRequestType)
        .amount(Helper.convertToSmallestCurrencyUnits(amount, currency))
        .newAccountBalance(newAccountBalance)
        .currency(currency)
        .tStartDate(posted)
        .tFinishDate(completed)
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
    }, callContext)
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

  override def updateBankAccount(
     bankId: BankId,
     accountId: AccountId,
     accountType: String,
     accountLabel: String,
     branchId: String,
     accountRoutingScheme: String,
     accountRoutingAddress: String,
     callContext: Option[CallContext]
   ): OBPReturnType[Box[BankAccount]] = Future {
    (for {
      (account, callContext) <- LocalMappedConnector.getBankAccountCommon(bankId, accountId, callContext)
      } yield {
        account
          .kind(accountType)
          .accountLabel(accountLabel)
          .mBranchId(branchId)
          .mAccountRoutingScheme(accountRoutingScheme)
          .mAccountRoutingAddress(accountRoutingAddress)
          .saveMe
      },callContext)
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

  override def addBankAccount(
    bankId: BankId,
    accountType: String,
    accountLabel: String,
    currency: String,
    initialBalance: BigDecimal,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[BankAccount]] = Future{
    val accountId = AccountId(APIUtil.generateUUID())
    val uniqueAccountNumber = {
      def exists(number : String) = accountExists(bankId, number).openOrThrowException(attemptedToOpenAnEmptyBox)

      def appendUntilOkay(number : String) : String = {
        val newNumber = number + Random.nextInt(10)
        if(!exists(newNumber)) newNumber
        else appendUntilOkay(newNumber)
      }

      //generates a random 8 digit account number
      val firstTry = (Random.nextDouble() * 10E8).toInt.toString
      appendUntilOkay(firstTry)
    }
    (createSandboxBankAccount(
      bankId,
      accountId,
      uniqueAccountNumber,
      accountType,
      accountLabel,
      currency,
      initialBalance,
      accountHolderName,
      branchId: String,//added field in V220
      accountRoutingScheme, //added field in V220
      accountRoutingAddress //added field in V220
    ),callContext)
  }
  
  
  override def createBankAccount(
                         bankId: BankId,
                         accountId: AccountId,
                         accountType: String,
                         accountLabel: String,
                         currency: String,
                         initialBalance: BigDecimal,
                         accountHolderName: String,
                         branchId: String,
                         accountRoutingScheme: String,
                         accountRoutingAddress: String,
                         callContext: Option[CallContext]
                       ): OBPReturnType[Box[BankAccount]] = Future{
    (Connector.connector.vend.createBankAccountLegacy(bankId: BankId,
      accountId: AccountId,
      accountType: String,
      accountLabel: String,
      currency: String,
      initialBalance: BigDecimal,
      accountHolderName: String,
      branchId: String,
      accountRoutingScheme: String,
      accountRoutingAddress: String), callContext)
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
      (bank, _)<- getBankLegacy(bankId, None) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
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
          .accountCurrency(currency.toUpperCase)
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

    val foundBranch : Box[MappedBranch] = getBranchLegacy(branch.bankId, branch.branchId)

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
            .mBranchId(branch.branchId.value)
            .mBankId(branch.bankId.value)
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
    getAtmLegacy(atm.bankId, atm.atmId) match {
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





  override def getBranchLegacy(bankId : BankId, branchId: BranchId) : Box[MappedBranch]= {
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

  override def getBranches(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]) = {
    Future {
      Full(MappedBranch.findAll(By(MappedBranch.mBankId, bankId.value)), callContext)
    }
  }

  override def getBranch(bankId : BankId, branchId: BranchId, callContext: Option[CallContext]) = {
    Future {
      getBranchLegacy(bankId, branchId).map(branch=>(branch, callContext))
    }
  }

  override def getAtmLegacy(bankId : BankId, atmId: AtmId) : Box[MappedAtm]= {
    MappedAtm
      .find(
        By(MappedAtm.mBankId, bankId.value),
        By(MappedAtm.mAtmId, atmId.value))
  }
  override def getAtm(bankId : BankId, atmId: AtmId, callContext: Option[CallContext]) = 
    Future {
      getAtmLegacy(bankId, atmId).map(atm =>(atm, callContext))
    }

  override def getAtms(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam])= {
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

  override def getCounterpartiesLegacy(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): Box[(List[CounterpartyTrait], Option[CallContext])] = {
    Counterparties.counterparties.vend.getCounterparties(thisBankId, thisAccountId, viewId).map(counterparties =>(counterparties, callContext))
  }
  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): OBPReturnType[Box[List[CounterpartyTrait]]] = Future {
    (getCounterpartiesLegacy(thisBankId, thisAccountId, viewId, callContext) map (i => i._1), callContext)
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

  override def checkCustomerNumberAvailable(
    bankId: BankId,
    customerNumber: String,
    callContext: Option[CallContext]
  ) = Future{(tryo {CustomerX.customerProvider.vend.checkCustomerNumberAvailable(bankId, customerNumber)}, callContext) }
  
  
  override def createCustomer(
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
                               title: String,
                               branchId: String,
                               nameSuffix: String,
                               callContext: Option[CallContext]
                             ) = Future{
    (CustomerX.customerProvider.vend.addCustomer(
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
    ),callContext)
  }

  override def updateCustomerScaData(customerId: String,
                                     mobileNumber: Option[String],
                                     email: Option[String],
                                     customerNumber: Option[String],
                                     callContext: Option[CallContext]): OBPReturnType[Box[Customer]] =
      CustomerX.customerProvider.vend.updateCustomerScaData(
        customerId,
        mobileNumber,
        email,
        customerNumber
      ) map {
        (_, callContext)
      }
  override def updateCustomerCreditData(customerId: String,
                                        creditRating: Option[String],
                                        creditSource: Option[String],
                                        creditLimit: Option[AmountOfMoney],
                                        callContext: Option[CallContext]): OBPReturnType[Box[Customer]] =
      CustomerX.customerProvider.vend.updateCustomerCreditData(
        customerId,
        creditRating,
        creditSource,
        creditLimit
      ) map {
        (_, callContext)
      }
  override def updateCustomerGeneralData(customerId: String,
                                          legalName: Option[String],
                                          faceImage: Option[CustomerFaceImageTrait],
                                          dateOfBirth: Option[Date],
                                          relationshipStatus: Option[String],
                                          dependents: Option[Int],
                                          highestEducationAttained: Option[String],
                                          employmentStatus: Option[String],
                                          title: Option[String],
                                          branchId: Option[String],
                                          nameSuffix: Option[String],
                                          callContext: Option[CallContext]
                                         ): OBPReturnType[Box[Customer]] =
      CustomerX.customerProvider.vend.updateCustomerGeneralData(
        customerId,
        legalName,
        faceImage,
        dateOfBirth,
        relationshipStatus,
        dependents,
        highestEducationAttained,
        employmentStatus,
        title,
        branchId,
        nameSuffix
      ) map {
        (_, callContext)
      }
  
  def getCustomersByUserIdLegacy(userId: String, callContext: Option[CallContext]): Box[(List[Customer], Option[CallContext])] = {
    Full((CustomerX.customerProvider.vend.getCustomersByUserId(userId), callContext))
  }  
  
  override def getCustomersByUserId(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer],Option[CallContext])]]=
    CustomerX.customerProvider.vend.getCustomersByUserIdFuture(userId) map {
      customersBox =>(customersBox.map(customers=>(customers,callContext)))
    }

  override def getCustomerByCustomerIdLegacy(customerId: String, callContext: Option[CallContext])  =
    CustomerX.customerProvider.vend.getCustomerByCustomerId(customerId) map {
      customersBox =>(customersBox,callContext)
    }
  
  override def getCustomerByCustomerId(customerId : String, callContext: Option[CallContext]): Future[Box[(Customer,Option[CallContext])]] =
    CustomerX.customerProvider.vend.getCustomerByCustomerIdFuture(customerId)  map {
      i => i.map(
        customer => (customer, callContext)
      )
    }
  override def getCustomerByCustomerNumber(customerNumber : String, bankId : BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] =
    CustomerX.customerProvider.vend.getCustomerByCustomerNumberFuture(customerNumber, bankId)  map {
      i => i.map(
        customer => (customer, callContext)
      )
    }

  override def getCustomers(bankId : BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] =
    CustomerX.customerProvider.vend.getCustomersFuture(bankId, queryParams)
  
  override def getCustomersByCustomerPhoneNumber(bankId : BankId, phoneNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[List[Customer]]] =
    CustomerX.customerProvider.vend.getCustomersByCustomerPhoneNumber(bankId, phoneNumber)  map {
      (_, callContext)
    }

  override def getCustomerAddress(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAddress]]] =
    CustomerAddressX.address.vend.getAddress(customerId) map {
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
    CustomerAddressX.address.vend.createAddress(
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
    CustomerAddressX.address.vend.updateAddress(
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
    CustomerAddressX.address.vend.deleteAddress(customerAddressId) map {
      (_, callContext)
    }

  override def getTaxResidence(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[TaxResidence]]] =
    TaxResidenceX.taxResidence.vend.getTaxResidence(customerId) map {
      (_, callContext)
    }
  override def createTaxResidence(customerId : String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[TaxResidence]] =
    TaxResidenceX.taxResidence.vend.createTaxResidence(customerId, domain, taxNumber) map {
      (_, callContext)
    }
  override def deleteTaxResidence(taxResidenceId : String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    TaxResidenceX.taxResidence.vend.deleteTaxResidence(taxResidenceId) map {
      (_, callContext)
    }

  override def getCheckbookOrders(
    bankId: String, 
    accountId: String, 
    callContext: Option[CallContext]
  ) = Future {
    Full(SwaggerDefinitionsJSON.checkbookOrdersJson, callContext)
  }
  
  
  override  def getStatusOfCreditCardOrder(
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
    ProductAttributeX.productAttributeProvider.vend.createOrUpdateProductAttribute(
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
    ProductAttributeX.productAttributeProvider.vend.getProductAttributesFromProvider(bank: BankId, productCode: ProductCode) map {
      (_, callContext)
    }
  
  override def getProductAttributeById(
    productAttributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ProductAttribute]] = 
    ProductAttributeX.productAttributeProvider.vend.getProductAttributeById(productAttributeId: String) map{
      (_, callContext)
    }
  
  override def deleteProductAttribute(
    productAttributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = 
    ProductAttributeX.productAttributeProvider.vend.deleteProductAttribute(productAttributeId: String) map {
      (_, callContext)
    }

  override def getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]) = 
    AccountAttributeX.accountAttributeProvider.vend.getAccountAttributeById(accountAttributeId: String) map {
      (_, callContext)
    }

  override def getTransactionAttributeById(transactionAttributeId: String, callContext: Option[CallContext]) =
    TransactionAttributeX.transactionAttributeProvider.vend.getTransactionAttributeById(transactionAttributeId: String) map {
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
    AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(bankId: BankId,
                                                                                  accountId: AccountId,
                                                                                  productCode: ProductCode,
                                                                                  accountAttributeId: Option[String],
                                                                                  name: String,
                                                                                  attributType: AccountAttributeType.Value,
                                                                                  value: String) map { (_, callContext) }
  }
  override def createAccountAttributes(bankId: BankId,
                                       accountId: AccountId,
                                       productCode: ProductCode,
                                       accountAttributes: List[ProductAttribute],
                                       callContext: Option[CallContext]
                                       ): OBPReturnType[Box[List[AccountAttribute]]] = {
    AccountAttributeX.accountAttributeProvider.vend.createAccountAttributes(
      bankId: BankId,
      accountId: AccountId,
      productCode: ProductCode,
      accountAttributes: List[ProductAttribute]) map { (_, callContext) }
  }

  override def getAccountAttributesByAccount(bankId: BankId,
                                             accountId: AccountId,
                                             callContext: Option[CallContext]
                                            ): OBPReturnType[Box[List[AccountAttribute]]] = {
    AccountAttributeX.accountAttributeProvider.vend.getAccountAttributesByAccount(
      bankId: BankId,
      accountId: AccountId) map { (_, callContext) }
  }
  
  override def createOrUpdateCustomerAttribute(
    bankId: BankId,
    customerId: CustomerId,
    customerAttributeId: Option[String],
    name: String,
    attributeType: CustomerAttributeType.Value,
    value: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CustomerAttribute]] = {
    CustomerAttributeX.customerAttributeProvider.vend.createOrUpdateCustomerAttribute(
      bankId: BankId,
      customerId: CustomerId,
      customerAttributeId: Option[String],
      name: String,
      attributeType: CustomerAttributeType.Value,
      value: String
    ) map { (_, callContext) }
  }

  override def createOrUpdateTransactionAttribute(
    bankId: BankId,
    transactionId: TransactionId,
    transactionAttributeId: Option[String],
    name: String,
    attributeType: TransactionAttributeType.Value,
    value: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TransactionAttribute]]  = {
    TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
      bankId: BankId,
      transactionId: TransactionId,
      transactionAttributeId: Option[String],
      name: String,
      attributeType: TransactionAttributeType.Value,
      value: String
    ) map { (_, callContext) }
  }
  
  
  override def getCustomerAttributes(bankId: BankId,
    customerId: CustomerId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[CustomerAttribute]]] = {
    CustomerAttributeX.customerAttributeProvider.vend.getCustomerAttributes(
      bankId: BankId,
      customerId: CustomerId) map { (_, callContext) }
  }

  override def getCustomerIdByAttributeNameValues(
                                         bankId: BankId,
                                         nameValues: Map[String, List[String]],
                                         callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] = {

    CustomerAttributeX.customerAttributeProvider.vend.getCustomerIdByAttributeNameValues(bankId, nameValues) map { (_, callContext) }
  }


  override def getCustomerAttributesForCustomers(
    customers: List[Customer],
    callContext: Option[CallContext]): OBPReturnType[Box[List[(Customer, List[CustomerAttribute])]]] = {
    CustomerAttributeX.customerAttributeProvider.vend.getCustomerAttributesForCustomers(
      customers: List[Customer]) map { (_, callContext) }
  }
  
  
  override def getTransactionAttributes(
    bankId: BankId,
    transactionId: TransactionId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[TransactionAttribute]]] = {
    TransactionAttributeX.transactionAttributeProvider.vend.getTransactionAttributes(
      bankId: BankId,
      transactionId: TransactionId) map { (_, callContext) }
  }

  override def getCustomerAttributeById(
    customerAttributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CustomerAttribute]] = {
    CustomerAttributeX.customerAttributeProvider.vend.getCustomerAttributeById(customerAttributeId: String) map { (_, callContext) }
  }

  override def createAccountApplication(
    productCode: ProductCode,
    userId: Option[String],
    customerId: Option[String],
    callContext: Option[CallContext]
    ): OBPReturnType[Box[AccountApplication]] =
    AccountApplicationX.accountApplication.vend.createAccountApplication(productCode, userId, customerId) map {
      (_, callContext)
    }

  override def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[Box[List[AccountApplication]]] =
    AccountApplicationX.accountApplication.vend.getAll() map {
      (_, callContext)
    }

  override def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    AccountApplicationX.accountApplication.vend.getById(accountApplicationId) map {
      (_, callContext)
    }

  override  def updateAccountApplicationStatus(accountApplicationId:String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    AccountApplicationX.accountApplication.vend.updateStatus(accountApplicationId, status) map {
      (_, callContext)
    }
  
  override  def getOrCreateProductCollection(collectionCode: String, productCodes: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    ProductCollectionX.productCollection.vend.getOrCreateProductCollection(collectionCode, productCodes) map {
      (_, callContext)
    } 
  
  override  def getProductCollection(collectionCode: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    ProductCollectionX.productCollection.vend.getProductCollection(collectionCode) map {
      (_, callContext)
    } 
  
  override  def getOrCreateProductCollectionItem(collectionCode: String,
                                                 memberProductCodes: List[String],
                                                 callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    ProductCollectionItems.productCollectionItem.vend.getOrCreateProductCollectionItem(collectionCode, memberProductCodes) map {
      (_, callContext)
    }
  
  override  def getProductCollectionItem(collectionCode: String,
                                         callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    ProductCollectionItems.productCollectionItem.vend.getProductCollectionItems(collectionCode) map {
      pci => (pci, callContext)
    }  
  override def getProductCollectionItemsTree(collectionCode: String, 
                                              bankId: String,
                                              callContext: Option[CallContext]): OBPReturnType[Box[List[(ProductCollectionItem, Product, List[ProductAttribute])]]] =
    ProductCollectionItems.productCollectionItem.vend.getProductCollectionItemsTree(collectionCode, bankId) map {
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
      Meetings.meetingProvider.vend.createMeeting(
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
      Meetings.meetingProvider.vend.getMeetings(
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
      Meetings.meetingProvider.vend.getMeeting(
        bankId: BankId,
        user: User,
        meetingId : String), 
      callContext)}

  override def createOrUpdateKycCheck(bankId: String,
                                       customerId: String,
                                       id: String,
                                       customerNumber: String,
                                       date: Date,
                                       how: String,
                                       staffUserId: String,
                                       mStaffName: String,
                                       mSatisfied: Boolean,
                                       comments: String,
                                       callContext: Option[CallContext]): OBPReturnType[Box[KycCheck]] = Future {
    val boxedData = KycChecks.kycCheckProvider.vend.addKycChecks(bankId, customerId, id, customerNumber, date, how, staffUserId, mStaffName, mSatisfied, comments)
    (boxedData, callContext)
  }

  override def createOrUpdateKycDocument(bankId: String,
                                         customerId: String,
                                         id: String,
                                         customerNumber: String,
                                         `type`: String,
                                         number: String,
                                         issueDate: Date,
                                         issuePlace: String,
                                         expiryDate: Date,
                                         callContext: Option[CallContext]): OBPReturnType[Box[KycDocument]] = Future {
    val boxedData = KycDocuments.kycDocumentProvider.vend.addKycDocuments(
      bankId,
      customerId,
      id,
      customerNumber,
      `type`,
      number,
      issueDate,
      issuePlace,
      expiryDate
    )
    (boxedData, callContext)
  }

  override def createOrUpdateKycMedia(bankId: String,
                                        customerId: String,
                                        id: String,
                                        customerNumber: String,
                                        `type`: String,
                                        url: String,
                                        date: Date,
                                        relatesToKycDocumentId: String,
                                        relatesToKycCheckId: String,
                                        callContext: Option[CallContext]): OBPReturnType[Box[KycMedia]] = Future {
    val boxedData = KycMedias.kycMediaProvider.vend.addKycMedias(
      bankId,
      customerId,
      id,
      customerNumber,
      `type`,
      url,
      date,
      relatesToKycDocumentId,
      relatesToKycCheckId
    )
    (boxedData, callContext)
  }


  override def createOrUpdateKycStatus(bankId: String,
                                         customerId: String,
                                         customerNumber: String,
                                         ok: Boolean,
                                         date: Date,
                                         callContext: Option[CallContext]): OBPReturnType[Box[KycStatus]] = Future {
    val boxedData = KycStatuses.kycStatusProvider.vend.addKycStatus(
      bankId,
      customerId,
      customerNumber,
      ok,
      date
    )
    (boxedData, callContext)
  }


  override def getKycChecks(customerId: String,
                   callContext: Option[CallContext]
                  ): OBPReturnType[Box[List[KycCheck]]] = Future {
    val boxedData = Box !! KycChecks.kycCheckProvider.vend.getKycChecks(customerId)
    (boxedData, callContext)
  }

  override def getKycDocuments(customerId: String,
                      callContext: Option[CallContext]
                     ): OBPReturnType[Box[List[KycDocument]]] = Future {
    val boxedData = Box !!  KycDocuments.kycDocumentProvider.vend.getKycDocuments(customerId)
    (boxedData, callContext)
  }

  override def getKycMedias(customerId: String,
                   callContext: Option[CallContext]
                  ): OBPReturnType[Box[List[KycMedia]]] = Future {
    val boxedData = Box !!  KycMedias.kycMediaProvider.vend.getKycMedias(customerId)
    (boxedData, callContext)
  }

  override def getKycStatuses(customerId: String,
                     callContext: Option[CallContext]
                    ): OBPReturnType[Box[List[KycStatus]]] = Future {
    val boxedData = Box !!  KycStatuses.kycStatusProvider.vend.getKycStatuses(customerId)
    (boxedData, callContext)
  }

  override def createMessage(user: User,
                             bankId: BankId,
                             message: String,
                             fromDepartment: String,
                             fromPerson: String,
                             callContext: Option[CallContext]) : OBPReturnType[Box[CustomerMessage]] = Future{
    val boxedData = Box !! CustomerMessages.customerMessageProvider.vend.addMessage(user, bankId, message, fromDepartment, fromPerson)
    (boxedData, callContext)
  }

  override def dynamicEntityProcess(operation: DynamicEntityOperation,
                                    entityName: String,
                                    requestBody: Option[JObject],
                                    entityId: Option[String],
                                    callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {

    val dynamicEntityBox = DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(entityName)
    // do validate, any validate process fail will return immediately
    if(dynamicEntityBox.isEmpty) {
      return Helper.booleanToFuture(s"$DynamicEntityNotExists entity's name is '$entityName'")(false)
        .map(it => (it.map(_.asInstanceOf[JValue]), callContext))
    }

    if(operation == CREATE || operation == UPDATE) {
      if(requestBody.isEmpty) {
        return Helper.booleanToFuture(s"$InvalidJsonFormat requestBody is required for $operation operation.")(false)
          .map(it => (it.map(_.asInstanceOf[JValue]), callContext))
      }
      val dynamicEntity: DynamicEntityT = dynamicEntityBox.openOrThrowException(DynamicEntityNotExists)
      val validateResult: Either[String, Unit] = dynamicEntity.validateEntityJson(requestBody.get)
      if(validateResult.isLeft) {
        return Helper.booleanToFuture(s"$InvalidJsonFormat details: ${validateResult.left.get}")(validateResult.isRight)
          .map(it => (it.map(_.asInstanceOf[JValue]), callContext))
      }
    }
    if(operation == GET_ONE || operation == UPDATE || operation == DELETE) {
      if (entityId.isEmpty) {
        return Helper.booleanToFuture(s"$InvalidJsonFormat entityId is required for $operation operation.")(entityId.isEmpty || StringUtils.isBlank(entityId.get))
          .map(it => (it.map(_.asInstanceOf[JValue]), callContext))
      }
      val id = entityId.get
      val value = DynamicDataProvider.connectorMethodProvider.vend.get(entityName, id)
      if (value.isEmpty) {
        return Helper.booleanToFuture(s"$EntityNotFoundByEntityId please check: entityId = $id", 404)(false)
          .map(it => (it.map(_.asInstanceOf[JValue]), callContext))
      }
    }
    val op: Any = operation
    Future {
      val processResult: Box[JValue] = op match {
        case GET_ALL => Full {
          val dataList = DynamicDataProvider.connectorMethodProvider.vend.getAll(entityName)
          JArray(dataList)
        }
        case GET_ONE => {
          val boxedEntity: Box[JValue] = DynamicDataProvider.connectorMethodProvider.vend
            .get(entityName, entityId.getOrElse(throw new RuntimeException(s"$DynamicEntityMissArgument the entityId is required.")))
            .map(it => json.parse(it.dataJson))
          boxedEntity
        }
        case CREATE | UPDATE => {
          val body = requestBody.getOrElse(throw new RuntimeException(s"$DynamicEntityMissArgument please supply the requestBody."))
          val id = if(operation == CREATE) None  else entityId
          val boxedEntity: Box[JValue] = DynamicDataProvider.connectorMethodProvider.vend.saveOrUpdate(entityName, body, id)
            .map(it => json.parse(it.dataJson))
          boxedEntity
        }
        case DELETE => {
          val id = entityId.getOrElse(throw new RuntimeException(s"$DynamicEntityMissArgument the entityId is required. "))
          val deleteResult: Boolean = DynamicDataProvider.connectorMethodProvider.vend.delete(entityName, id)
          Full(JBool(deleteResult))
        }
        case IS_EXISTS_DATA => {
          val isExistsData: Boolean = DynamicDataProvider.connectorMethodProvider.vend.existsData(entityName)
          Full(JBool(isExistsData))
        }
      }
      (processResult, callContext)
    }
  }

  override def createDirectDebit(bankId: String,
                                 accountId: String,
                                 customerId: String,
                                 userId: String,
                                 counterpartyId: String,
                                 dateSigned: Date,
                                 dateStarts: Date,
                                 dateExpires: Option[Date],
                                 callContext: Option[CallContext]): OBPReturnType[Box[DirectDebitTrait]] = Future {
    val result = DirectDebits.directDebitProvider.vend.createDirectDebit(
      bankId, 
      accountId, 
      customerId,
      counterpartyId,
      userId,
      dateSigned, 
      dateStarts, 
      dateExpires)
    (result, callContext)
  }

  override def createStandingOrder(bankId: String,
                                   accountId: String,
                                   customerId: String,
                                   userId: String,
                                   counterpartyId: String,
                                   amountValue: BigDecimal,
                                   amountCurrency: String,
                                   whenFrequency: String,
                                   whenDetail: String,
                                   dateSigned: Date,
                                   dateStarts: Date,
                                   dateExpires: Option[Date],
                                   callContext: Option[CallContext]): OBPReturnType[Box[StandingOrderTrait]] = Future {
    val result = StandingOrders.provider.vend.createStandingOrder(
      bankId,
      accountId,
      customerId,
      userId,
      counterpartyId,
      amountValue,
      amountCurrency,
      whenFrequency,
      whenDetail,
      dateSigned,
      dateStarts,
      dateExpires)
    (result, callContext)
  }

}

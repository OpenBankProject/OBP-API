package code.bankconnectors

import java.util.Date
import java.util.UUID.randomUUID
import _root_.akka.http.scaladsl.model.HttpMethod
import code.DynamicData.DynamicDataProvider
import code.DynamicEndpoint.{DynamicEndpointProvider, DynamicEndpointT}
import code.accountapplication.AccountApplicationX
import code.accountattribute.AccountAttributeX
import code.accountholders.{AccountHolders, MapperAccountHolders}
import code.api.BerlinGroup.{AuthenticationType, ScaStatus}
import code.api.Constant
import code.api.Constant.{INCOMING_SETTLEMENT_ACCOUNT_ID, OUTGOING_SETTLEMENT_ACCOUNT_ID}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.attributedefinition.{AttributeDefinition, AttributeDefinitionDI}
import code.api.cache.Caching
import code.api.util.APIUtil.{DateWithMsFormat, OBPReturnType, generateUUID, hasEntitlement, isValidCurrencyISOCode, saveConnectorMetric, stringOrNull, unboxFullOrFail}
import code.api.util.ApiRole.canCreateAnyTransactionRequest
import code.api.util.ErrorMessages.{attemptedToOpenAnEmptyBox, _}
import code.api.util._
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_1_0._
import code.api.v4_0_0.{PostSimpleCounterpartyJson400, TransactionRequestBodySimpleJsonV400}
import code.atms.Atms.Atm
import code.atms.{Atms, MappedAtm}
import code.bankattribute.{BankAttribute, BankAttributeX}
import code.branches.Branches.Branch
import code.branches.MappedBranch
import code.cardattribute.CardAttributeX
import code.cards.MappedPhysicalCard
import code.context.{UserAuthContextProvider, UserAuthContextUpdateProvider}
import code.customer._
import code.customeraccountlinks.CustomerAccountLinkTrait
import code.customeraddress.CustomerAddressX
import code.customerattribute.CustomerAttributeX
import code.database.authorisation.Authorisations
import code.directdebit.DirectDebits
import code.endpointTag.{EndpointTag, EndpointTagT}
import code.fx.fx.TTL
import code.fx.{MappedFXRate, fx}
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
import code.metrics.MappedMetric
import code.model._
import code.model.dataAccess.AuthUser.findUserByUsernameLocally
import code.model.dataAccess._
import code.productAttributeattribute.MappedProductAttribute
import code.productattribute.ProductAttributeX
import code.productcollection.ProductCollectionX
import code.productcollectionitem.ProductCollectionItems
import code.productfee.ProductFeeX
import code.products.MappedProduct
import code.standingorders.{StandingOrderTrait, StandingOrders}
import code.taxresidence.TaxResidenceX
import code.transaction.MappedTransaction
import code.transactionChallenge.{Challenges, MappedExpectedChallengeAnswer}
import code.transactionRequestAttribute.TransactionRequestAttributeX
import code.transactionattribute.TransactionAttributeX
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import code.transactionrequests.TransactionRequests.TransactionRequestTypes
import code.transactionrequests._
import code.users.{UserAttribute, UserAttributeProvider, Users}
import code.util.Helper
import code.util.Helper.{MdcLoggable, _}
import code.views.Views
import com.google.common.cache.CacheBuilder
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.{CustomerAndAttribute, GetProductsParam, ProductCollectionItemsTree}
import com.openbankproject.commons.model.enums.ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums.{TransactionRequestStatus, _}
import com.openbankproject.commons.model.{AccountApplication, AccountAttribute, DirectDebitTrait, FXRate, Product, ProductAttribute, ProductCollectionItem, TaxResidence, TransactionRequestCommonBodyJSON, _}
import com.tesobe.CacheKeyFromArguments
import com.tesobe.model.UpdateBankAccount
import com.twilio.Twilio
import com.twilio.rest.api.v2010.account.Message
import com.twilio.`type`.PhoneNumber
import net.liftweb.common._
import net.liftweb.json
import net.liftweb.json.JsonAST.JField
import net.liftweb.json.{JArray, JBool, JInt, JObject, JString, JValue}
import net.liftweb.mapper.{By, _}
import net.liftweb.util.Helpers.{hours, now, time, tryo}
import net.liftweb.util.{Helpers, Mailer}
import net.liftweb.util.Mailer.{From, PlainMailBodyType, Subject, To}
import org.iban4j
import org.iban4j.{CountryCode, IbanFormat}
import org.mindrot.jbcrypt.BCrypt
import scalacache.ScalaCache
import scalacache.guava.GuavaCache
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings, MultipleConnectionPoolContext}
import scalikejdbc.DB.CPContext
import scalikejdbc.{DB => scalikeDB, _}

import scala.collection.immutable.{List, Nil}
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.{BigDecimal, BigInt}
import scala.util.{Random, Try}

object LocalMappedConnector extends Connector with MdcLoggable {

  //  override type AccountType = MappedBankAccount
  val maxBadLoginAttempts = APIUtil.getPropsValue("max.bad.login.attempts") openOr "10"

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache = ScalaCache(GuavaCache(underlyingGuavaCache))
  val getTransactionsTTL = APIUtil.getPropsValue("connector.cache.ttl.seconds.getTransactions", "0").toInt * 1000 // Miliseconds

  //This is the implicit parameter for saveConnectorMetric function.
  //eg:  override def getBank(bankId: BankId, callContext: Option[CallContext]) = saveConnectorMetric
  implicit override val nameOfConnector = LocalMappedConnector.getClass.getSimpleName

  //
  override def getAdapterInfo(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = Future {
    val startTime = Helpers.now.getTime
    val source = APIUtil.getPropsValue("db.driver","org.h2.Driver")
    Full(InboundAdapterInfoInternal(
      errorCode = "",
      backendMessages = List(
        InboundStatusMessage(
          source = source,
          status = "Success",
          errorCode = "",
          text =s"Get data from $source database",
          duration = Some(BigDecimal(Helpers.now.getTime - startTime)/1000))),
      name = "LocalMappedConnector",
      version = "mapped",
      git_commit = APIUtil.gitCommit,
      date = DateWithMsFormat.format(new Date())
    ), callContext)
  }
  
  override def validateAndCheckIbanNumber(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[IbanChecker]] = Future {
    import org.iban4j.CountryCode
    import org.iban4j.Iban
    import org.iban4j.IbanFormat
    import org.iban4j.IbanFormatException
    import org.iban4j.IbanUtil
    import org.iban4j.InvalidCheckDigitException
    import org.iban4j.UnsupportedCountryException

    // Validate Iban 
    try { // 1st try
      IbanUtil.validate(iban) // IBAN as String: "DE89370400440532013000"
      (Full(IbanChecker(true, None)), callContext) // valid
    } catch {
      case error@(_: IbanFormatException | _: InvalidCheckDigitException | _: UnsupportedCountryException) =>
      // invalid
        try { // 2nd try
          IbanUtil.validate(iban, IbanFormat.Default) // IBAN as formatted String: "DE89 3704 0044 0532 0130 00"
          (Full(IbanChecker(true, None)), callContext) // valid
        } catch {
          case error@(_: IbanFormatException | _: InvalidCheckDigitException | _: UnsupportedCountryException) =>
            (Full(IbanChecker(false, None)), callContext) // invalid
        }
    }
  }

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String,
                                     accountId: String,
                                     viewId: String,
                                     transactionRequestType: String,
                                     currency: String,
                                     userId: String,
                                     username: String,
                                     callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = Future {
    val propertyName = "transactionRequests_challenge_threshold_" + transactionRequestType.toUpperCase
    val threshold = BigDecimal(APIUtil.getPropsValue(propertyName, "1000"))
    logger.debug(s"threshold is $threshold")

    val thresholdCurrency: String = APIUtil.getPropsValue("transactionRequests_challenge_currency", "EUR")
    logger.debug(s"thresholdCurrency is $thresholdCurrency")
    isValidCurrencyISOCode(thresholdCurrency) match {
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
        val msg = s"$InvalidISOCurrencyCode ${thresholdCurrency}"
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
  override def createChallenge(bankId: BankId,
                               accountId: AccountId,
                               userId: String,
                               transactionRequestType: TransactionRequestType,
                               transactionRequestId: String,
                               scaMethod: Option[SCA],
                               callContext: Option[CallContext]): OBPReturnType[Box[String]] = Future {
    val challenge = createChallengeInternal(
      userId: String,
      transactionRequestId: String,
      scaMethod: Option[SCA],
      None, //there are only for new version, set the empty here.
      None,//there are only for new version, set the empty here.
      None,//there are only for new version, set the empty here.
      challengeType = OBP_TRANSACTION_REQUEST_CHALLENGE.toString,
      callContext: Option[CallContext])
    (challenge._1.map(_.challengeId),challenge._2)
  }

  /**
    * Steps To Create, Store and Send Challenge
    * 1. Generate a random challenge
    * 2. Generate a long random salt
    * 3. Prepend the salt to the challenge and hash it with a standard password hashing function like Argon2, bcrypt, scrypt, or PBKDF2.
    * 4. Save both the salt and the hash in the user's database record.
    * 5. Send the challenge over an separate communication channel.
    */
  override def createChallenges(bankId: BankId,
                                accountId: AccountId,
                                userIds: List[String],
                                transactionRequestType: TransactionRequestType,
                                transactionRequestId: String,
                                scaMethod: Option[SCA],
                                callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] = Future {
    val challenges = for {
      userId <- userIds
    } yield {
      val (challenge, _) = createChallengeInternal(
        userId,
        transactionRequestId,
        scaMethod,
        None, //there are only for new version, set the empty here.
        None,//there are only for new version, set the empty here.
        None,//there are only for new version, set the empty here.
        challengeType = OBP_TRANSACTION_REQUEST_CHALLENGE.toString,
        callContext
      )
      challenge.map(_.challengeId).toList
    }
    (Full(challenges.flatten), callContext)
  }

  override def createChallengesC2(
    userIds: List[String],
    challengeType: ChallengeType.Value,
    transactionRequestId: Option[String],
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],//Only use for BerlinGroup Now
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String],
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[ChallengeTrait]]] = Future {
    val challenges = for {
      userId <- userIds
    } yield {
      val (challengeId, _) = createChallengeInternal(
        userId,
        transactionRequestId.getOrElse(""),
        scaMethod,
        scaStatus,
        consentId,
        authenticationMethodId,
        challengeType = OBP_TRANSACTION_REQUEST_CHALLENGE.toString,
        callContext
      )
      challengeId.toList
    }

    Authorisations.authorisationProvider.vend.createAuthorization(
      transactionRequestId.getOrElse(""),
      consentId.getOrElse(""),
      AuthenticationType.SMS_OTP.toString,
      "",
      ScaStatus.received.toString,
      "12345" // TODO Implement SMS sending
    )
    
    (Full(challenges.flatten), callContext)
  }

  private def createChallengeInternal(
    userId: String,
    transactionRequestId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus], //Only use for BerlinGroup Now
    consentId: Option[String],    // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String],
    challengeType: String,
    callContext: Option[CallContext]
  ) = {
    def createHashedPassword(challengeAnswer: String) = {
      val challengeId = APIUtil.generateUUID()
      val salt = BCrypt.gensalt()
      val challengeAnswerHashed = BCrypt.hashpw(challengeAnswer, salt).substring(0, 44)
      (Challenges.ChallengeProvider.vend.saveChallenge(
        challengeId,
        transactionRequestId,
        salt,
        challengeAnswerHashed,
        userId,
        scaMethod,
        scaStatus,
        consentId,
        authenticationMethodId,
        challengeType), callContext)
    }

    scaMethod match {
      case Some(StrongCustomerAuthentication.UNDEFINED) =>
        (Failure(ScaMethodNotDefined), callContext)
      case Some(StrongCustomerAuthentication.DUMMY) =>
        createHashedPassword("123")
      case Some(StrongCustomerAuthentication.EMAIL) =>
        val challengeAnswer = SecureRandomUtil.csprng.nextInt(99999999).toString()
        val hashedPassword = createHashedPassword(challengeAnswer)
        APIUtil.getEmailsByUserId(userId) map {
          pair =>
            val params = PlainMailBodyType(s"Your OTP challenge : ${challengeAnswer}") :: List(To(pair._2))
            Mailer.sendMail(From("challenge@tesobe.com"), Subject("Challenge"), params: _*)
        }
        hashedPassword
      case Some(StrongCustomerAuthentication.SMS) | Some(StrongCustomerAuthentication.SMS_OTP) =>
        val challengeAnswer = SecureRandomUtil.csprng.nextInt(99999999).toString()
        logger.debug(s"${scaMethod.toString} challengeAnswer is $challengeAnswer")
        val hashedPassword = createHashedPassword(challengeAnswer)
        val sendingResult: Seq[Box[Boolean]] = APIUtil.getPhoneNumbersByUserId(userId) map {
          tuple =>
            for {
              smsProviderApiKey <- APIUtil.getPropsValue("sca_phone_api_key") ?~! s"$MissingPropsValueAtThisInstance sca_phone_api_key"
              smsProviderApiSecret <- APIUtil.getPropsValue("sca_phone_api_secret") ?~! s"$MissingPropsValueAtThisInstance sca_phone_api_secret"
              client = Twilio.init(smsProviderApiKey, smsProviderApiSecret)
              phoneNumber = tuple._2
              messageText = s"Your consent challenge : ${challengeAnswer}";
              message: Box[Message] = tryo(Message.creator(new PhoneNumber(phoneNumber), new PhoneNumber(phoneNumber), messageText).create())
              failMsg = s"$SmsServerNotResponding: $phoneNumber. Or Please to use EMAIL first. ${message.map(_.getErrorMessage).getOrElse("")}"
              _ <- Helper.booleanToBox(message.forall(_.getErrorMessage.isEmpty), failMsg)
            } yield true
        }
        val errorMessage = sendingResult.filter(_.isInstanceOf[Failure]).map(_.asInstanceOf[Failure].msg)

        if (sendingResult.forall(_ == Full(true))) hashedPassword else (Failure(errorMessage.toSet.mkString(" <- ")), callContext)
      case _ => // All versions which precede v4.0.0 i.e. to keep backward compatibility 
        createHashedPassword("123")
    }
  }


  override def validateChallengeAnswerC2(
    transactionRequestId: Option[String],
    consentId: Option[String],
    challengeId: String,
    hashOfSuppliedAnswer: String,
    callContext: Option[CallContext]
  ) = Future {
    Future {
      val userId = callContext.map(_.user.map(_.userId).openOrThrowException(s"$UserNotLoggedIn Can not find the userId here."))
      (Challenges.ChallengeProvider.vend.validateChallenge(challengeId, hashOfSuppliedAnswer, userId), callContext)
    }
  }
  
  override def getChallengesByTransactionRequestId(transactionRequestId: String, callContext:  Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] =
    Future {(Challenges.ChallengeProvider.vend.getChallengesByTransactionRequestId(transactionRequestId), callContext)}  
  
  override def getChallengesByConsentId(consentId: String, callContext:  Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] =
    Future {(Challenges.ChallengeProvider.vend.getChallengesByConsentId(consentId), callContext)}


  override def getChallenge(challengeId: String, callContext:  Option[CallContext]): OBPReturnType[Box[ChallengeTrait]] = 
    Future {(Challenges.ChallengeProvider.vend.getChallenge(challengeId), callContext)}

  override def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = 
    Future { 
      val userId = callContext.map(_.user.map(_.userId).openOrThrowException(s"$UserNotLoggedIn Can not find the userId here."))
      (Full(Challenges.ChallengeProvider.vend.validateChallenge(challengeId, hashOfSuppliedAnswer, userId).isDefined), callContext)
    } 
  
  override def allChallengesSuccessfullyAnswered(
    bankId: BankId,
    accountId: AccountId,
    transReqId: TransactionRequestId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = {
    for {
      (accountAttributes, callContext) <- Connector.connector.vend.getAccountAttributesByAccount(bankId, accountId, callContext)
      (challenges, callContext) <-  NewStyle.function.getChallengesByTransactionRequestId(transReqId.value, callContext)
      quorum = accountAttributes.toList.flatten.find(_.name == "REQUIRED_CHALLENGE_ANSWERS").map(_.value).getOrElse("1").toInt
      challengeSuccess = challenges.count(_.successful == true) match {
        case number if number >= quorum => true
        case _ =>
          MappedTransactionRequestProvider.saveTransactionRequestStatusImpl(transReqId, TransactionRequestStatus.NEXT_CHALLENGE_PENDING.toString)
          false
      }
    } yield {
      (Full(challengeSuccess), callContext)
    }
  } 
  
  
  override def getChargeLevel(bankId: BankId,
                              accountId: AccountId,
                              viewId: ViewId,
                              userId: String,
                              username: String,
                              transactionRequestType: String,
                              currency: String,
                              callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = Future {
    val propertyName = "transactionRequests_charge_level_" + transactionRequestType.toUpperCase
    val chargeLevel = BigDecimal(APIUtil.getPropsValue(propertyName, "0.0001"))
    logger.debug(s"transactionRequests_charge_level is $chargeLevel")

    // TODO constrain this to supported currencies.
    //    val chargeLevelCurrency = APIUtil.getPropsValue("transactionRequests_challenge_currency", "EUR")
    //    logger.debug(s"chargeLevelCurrency is $chargeLevelCurrency")
    //    val rate = fx.exchangeRate (chargeLevelCurrency, currency)
    //    val convertedThreshold = fx.convert(chargeLevel, rate)
    //    logger.debug(s"getChallengeThreshold for currency $currency is $convertedThreshold")

    (Full(AmountOfMoney(currency, chargeLevel.toString)), callContext)
  }

  override def getChargeLevelC2(bankId: BankId,
                                accountId: AccountId,
                                viewId: ViewId,
                                userId: String,
                                username: String,
                                transactionRequestType: String,
                                currency: String,
                                amount: String,
                                toAccountRouting: List[AccountRouting],
                                customAttributes: List[CustomAttribute],
                                callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = Future {
    val propertyName = "transactionRequests_charge_level_" + transactionRequestType.toUpperCase
    val chargeLevel = BigDecimal(APIUtil.getPropsValue(propertyName, "0.0001"))
    logger.debug(s"transactionRequests_charge_level is $chargeLevel")

    // TODO constrain this to supported currencies.
    //    val chargeLevelCurrency = APIUtil.getPropsValue("transactionRequests_challenge_currency", "EUR")
    //    logger.debug(s"chargeLevelCurrency is $chargeLevelCurrency")
    //    val rate = fx.exchangeRate (chargeLevelCurrency, currency)
    //    val convertedThreshold = fx.convert(chargeLevel, rate)
    //    logger.debug(s"getChallengeThreshold for currency $currency is $convertedThreshold")

    (Full(AmountOfMoney(currency, chargeLevel.toString)), callContext)
  }

  //gets a particular bank handled by this connector
  override def getBankLegacy(bankId: BankId, callContext: Option[CallContext]): Box[(Bank, Option[CallContext])] = saveConnectorMetric {
    getMappedBank(bankId).map(bank => (bank, callContext))
  }("getBank")

  private def getMappedBank(bankId: BankId): Box[MappedBank] =
    MappedBank
      .find(By(MappedBank.permalink, bankId.value))
      .map(
        bank =>
          bank
            .mBankRoutingScheme(APIUtil.ValueOrOBP(bank.bankRoutingScheme))
            .mBankRoutingAddress(APIUtil.ValueOrOBPId(bank.bankRoutingAddress, bank.bankId.value))
      )

  override def getBank(bankId: BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = Future {
    getBankLegacy(bankId, callContext)
  }


  override def getBanksLegacy(callContext: Option[CallContext]): Box[(List[Bank], Option[CallContext])] = saveConnectorMetric {
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

  override def getBanks(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = Future {
    getBanksLegacy(callContext)
  }

  /**
   * this connector method is for onboarding user from CBS side, here OBP simulate the process.
   * The CBS connector: 
   *   OBP send the bank customer indentity (eg: customer_number, telephone ...) to CBS side.
   *   CSB will return the accounts for the customer. 
   * So in this localmapped connector:
   *   we read all accounts from accountHolder and set `owner`(later need to simulate more) view, 
   *   and return the accounts back.
   * 
   */
  override def getBankAccountsForUserLegacy(provider: String, username:String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])] = {
    //1st: get the accounts from userAuthContext
    val viewsToGenerate = List("owner") //TODO, so far only set the `owner` view, later need to simulate other views.
    val user = Users.users.vend.getUserByProviderId(provider, username).getOrElse(throw new RuntimeException(s"$RefreshUserError at getBankAccountsForUserLegacy($username, ${callContext})"))
    val userId = user.userId
    tryo{net.liftweb.common.Logger(this.getClass).debug(s"getBankAccountsForUser.user says: provider($provider), username($username)")}
    val userAuthContexts = UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(userId)
    tryo{net.liftweb.common.Logger(this.getClass).debug(s"getBankAccountsForUser.userAuthContexts says: $userAuthContexts")}
    
    //Get the (BankId,Customer) pairs from UserAuthContext,
    val bankIdCustomerNumberPairs: Set[(String, String)] =  APIUtil.getBankIdAccountIdPairsFromUserAuthContexts(userAuthContexts.getOrElse(List.empty[UserAuthContext]))
    
    // get the Bank Account Ids from Customer Account Link,
    val bankAccountIdFromCustomerAccountLinksBoxList = for{
      bankIdCustomerPair <- bankIdCustomerNumberPairs
    }yield{
      CustomerX.customerProvider.vend.getCustomerByCustomerNumber(bankIdCustomerPair._2, BankId(bankIdCustomerPair._1)).map(customer => //check if the Customer Number is existing in Customer table.
        code.customeraccountlinks.MappedCustomerAccountLinkProvider.getCustomerAccountLinkByCustomerId(customer.customerId).map(customerAccountLink => // get the account Customer link from CustomerAccountLink 
          code.bankconnectors.LocalMappedConnector.getBankAccountCommon(BankId(customerAccountLink.bankId),AccountId(customerAccountLink.accountId), None).map(result => // check the bankAccount from CustomerAccountLink.
            BankIdAccountId(result._1.bankId, result._1.accountId)))).flatten.flatten
    }

    //find the proper bankAccountIds from the `bankAccountIdFromCustomerAccountLinksBoxList`
    val validBankAccountIdsFromUserAuthContext = bankAccountIdFromCustomerAccountLinksBoxList.filter(_.isDefined).map(_.head)
    
    tryo{net.liftweb.common.Logger(this.getClass).debug(s"getBankAccountsForUser.validBankAccountIdsFromUserAuthContext says: $validBankAccountIdsFromUserAuthContext")}

    //Get All OBP accounts from `Account Holder` table, source == null --> mean accounts are created by OBP endpoints, not from User Auth Context,
    val userOwnBankAccountIdsFromAccountHolder = AccountHolders.accountHolders.vend.getAccountsHeldByUser(user, Some(null))
    tryo{net.liftweb.common.Logger(this.getClass).debug(s"getBankAccountsForUser.userOwnBankAccountIdsFromAccountHolder says: $userOwnBankAccountIdsFromAccountHolder")}
    
    //We return the accounts created by OBP and accounts from UserAuthContext,
    val validBankAccountIds = validBankAccountIdsFromUserAuthContext++userOwnBankAccountIdsFromAccountHolder
    
    Full(validBankAccountIds.map(bankAccountId =>InboundAccountCommons(
        bankId = bankAccountId.bankId.value,
        accountId = bankAccountId.accountId.value,
        viewsToGenerate = viewsToGenerate,
        branchId = "",
        accountNumber = "",
        accountType = "",
        balanceAmount = "",
        balanceCurrency = "",
        owners = List(""),
        bankRoutingScheme = "",
        bankRoutingAddress = "",
        branchRoutingScheme = "",
        branchRoutingAddress = "",
        accountRoutingScheme = "",
        accountRoutingAddress = ""
      )).toList,callContext)
    
  }

  override def getBankAccountsForUser(provider: String, username:String, callContext: Option[CallContext]): Future[Box[(List[InboundAccount], Option[CallContext])]] = Future {
    getBankAccountsForUserLegacy(provider, username, callContext)
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

    val optionalParams: Seq[QueryParam[MappedTransaction]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
    val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountId.value)) ++ optionalParams

    def getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams: Seq[QueryParam[MappedTransaction]]): Box[List[Transaction]]
    = {
      /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value field with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getTransactionsTTL millisecond) {

          //logger.info("Cache miss getTransactionsCached")

          val mappedTransactions = MappedTransaction.findAll(mapperParams: _*)

          updateAccountTransactions(bankId, accountId)

          for (account <- getBankAccountOld(bankId, accountId))
            yield mappedTransactions.flatMap(_.toTransaction(account)) //each transaction will be modified by account, here we return the `class Transaction` not a trait.
        }
      }
    }

    getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams).map(transactions => (transactions, callContext))
  }

  override def getTransactionsCore(bankId: BankId, accountId: AccountId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionCore]]] = {

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
        * is just a temporary value field with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getTransactionsTTL millisecond) {

          //logger.info("Cache miss getTransactionsCached")

          val mappedTransactions = MappedTransaction.findAll(mapperParams: _*)

          for (account <- getBankAccountOld(bankId, accountId))
            yield mappedTransactions.flatMap(_.toTransactionCore(account)) //each transaction will be modified by account, here we return the `class Transaction` not a trait.
        }
      }
    }

    Future {
      (getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams), callContext)
    }
  }

  /**
    *
    * refreshes transactions via hbci if the transaction info is sourced from hbci
    *
    * Checks if the last update of the account was made more than one hour ago.
    * if it is the case we put a message in the message queue to ask for
    * transactions updates
    *
    * It will be used each time we fetch transactions from the DB. But the test
    * is performed in a different thread.
    */
  private def updateAccountTransactions(bankId: BankId, accountId: AccountId) = {

    for {
      bank <- getMappedBank(bankId)
      account <- getBankAccountOld(bankId, accountId).map(_.asInstanceOf[MappedBankAccount])
    } {
      Future {
        val useMessageQueue = APIUtil.getPropsAsBoolValue("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = Box !! account.accountLastUpdate.get match {
          case Full(l) => now after time(l.getTime + hours(APIUtil.getPropsAsIntValue("messageQueue.updateTransactionsInterval", 1)))
          case _ => true
        }
        if (outDatedTransactions && useMessageQueue) {
          UpdatesRequestSender.sendMsg(UpdateBankAccount(account.accountNumber.get, bank.national_identifier.get))
        }
      }
    }
  }

  override def getBankAccountLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
    getBankAccountCommon(bankId, accountId, callContext)
  }
  
  override def getBankAccountByAccountId(accountId : AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = Future {
    getBankAccountByAccountIdLegacy(accountId : AccountId, callContext: Option[CallContext])
  }
  
  def getBankAccountByAccountIdLegacy(accountId : AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] =  {
    MappedBankAccount.find(
      By(MappedBankAccount.theAccountId, accountId.value)
    ).map(bankAccount => (bankAccount, callContext))
  }

  override def getBankAccountByIban(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = Future {
    getBankAccountByRouting(None, "IBAN", iban, callContext)
  }

  override def getBankAccountByRouting(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
    bankId match {
      case Some(bankId) =>
        BankAccountRouting
          .find(By(BankAccountRouting.BankId, bankId.value), By(BankAccountRouting.AccountRoutingScheme, scheme), By(BankAccountRouting.AccountRoutingAddress, address))
          .flatMap(accountRouting => getBankAccountCommon(accountRouting.bankId, accountRouting.accountId, callContext))
      case None =>
        BankAccountRouting
          .find(By(BankAccountRouting.AccountRoutingScheme, scheme), By(BankAccountRouting.AccountRoutingAddress, address))
          .flatMap(accountRouting => getBankAccountCommon(accountRouting.bankId, accountRouting.accountId, callContext))
    }
  }

  override def getAccountRoutingsByScheme(bankId: Option[BankId], scheme: String, callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccountRouting]]] = {
    Future {
      Full(bankId match {
        case Some(bankId) => BankAccountRouting.findAll(By(BankAccountRouting.BankId, bankId.value), By(BankAccountRouting.AccountRoutingScheme, scheme))
        case None => BankAccountRouting.findAll(By(BankAccountRouting.AccountRoutingScheme, scheme))
      })
    }.map((_, callContext))
  }

  override def getAccountRouting(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext]): Box[(BankAccountRouting, Option[CallContext])] = {
    bankId match {
      case Some(bankId) =>
        BankAccountRouting
          .find(By(BankAccountRouting.BankId, bankId.value), By(BankAccountRouting.AccountRoutingScheme, scheme), By(BankAccountRouting.AccountRoutingAddress, address))
          .map(accountRouting => (accountRouting, callContext))
      case None =>
        BankAccountRouting
          .find(By(BankAccountRouting.AccountRoutingScheme, scheme), By(BankAccountRouting.AccountRoutingAddress, address))
          .map(accountRouting => (accountRouting, callContext))
    }
  }

  def getBankAccountCommon(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) = {
    MappedBankAccount
      .find(By(MappedBankAccount.bank, bankId.value), By(MappedBankAccount.theAccountId, accountId.value))
      .map(bankAccount => (bankAccount, callContext))
  }

  override def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccount]]] = {
    Future {
      (Full(
        bankIdAccountIds.map(
          bankIdAccountId =>
            getBankAccountOld(
              bankIdAccountId.bankId,
              bankIdAccountId.accountId
            ).openOrThrowException(s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"))
      ), callContext)
    }
  }

  override def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[AccountsBalances]] =
    Future {
      val accountsBalances = for {
        bankIdAccountId <- bankIdAccountIds
        bankAccount <- getBankAccountOld(bankIdAccountId.bankId, bankIdAccountId.accountId) ?~! s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"
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
      } yield {
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

  override def getBankAccountBalances(bankIdAccountId: BankIdAccountId, callContext: Option[CallContext]): OBPReturnType[Box[AccountBalances]] =
    Future {
       for {
        bankAccount <- getBankAccountOld(bankIdAccountId.bankId, bankIdAccountId.accountId) ?~! s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"
        accountBalances = AccountBalances(
          id = bankAccount.accountId.value,
          label = bankAccount.label,
          bankId = bankAccount.bankId.value,
          accountRoutings = bankAccount.accountRoutings.map(accountRounting => AccountRouting(accountRounting.scheme, accountRounting.address)),
          balances = List(BankAccountBalance(AmountOfMoney(bankAccount.currency, bankAccount.balance.toString),"OpeningBooked")),
          overallBalance = AmountOfMoney(bankAccount.currency, bankAccount.balance.toString),
          overallBalanceDate = now
        )
      } yield {
        (accountBalances,callContext)
      }
    }

  override def checkBankAccountExistsLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
    getBankAccountLegacy(bankId: BankId, accountId: AccountId, callContext)
  }

  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] =
    Future {
      (getBankAccountLegacy(bankId: BankId, accountId: AccountId, callContext).map(_._1), callContext)
    }

  override def getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Box[(List[CoreAccount], Option[CallContext])] = {
    Full(
      bankIdAccountIds
        .map(bankIdAccountId =>
          getBankAccountOld(
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

  override def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Future[Box[(List[CoreAccount], Option[CallContext])]] = {
    Future {
      getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext])
    }
  }

  private lazy val getDbConnectionParameters: (String, String, String) = {
    val dbUrl = APIUtil.getPropsValue("db.url") openOr Constant.h2DatabaseDefaultUrlValue
    val username = dbUrl.split(";").filter(_.contains("user")).toList.headOption.map(_.split("=")(1))
    val password = dbUrl.split(";").filter(_.contains("password")).toList.headOption.map(_.split("=")(1))
    val dbUser = APIUtil.getPropsValue("db.user").orElse(username)
    val dbPassword = APIUtil.getPropsValue("db.password").orElse(password)
    (dbUrl, dbUser.getOrElse(""), dbPassword.getOrElse(""))
  }
  
  /**
   * this connection pool context corresponding db.url in default.props
   */
  implicit lazy val context: CPContext = {
    val settings = ConnectionPoolSettings(
      initialSize = 5,
      maxSize = 20,
      connectionTimeoutMillis = 3000L,
      validationQuery = "select 1",
      connectionPoolFactoryName = "commons-dbcp2"
    )
    val (dbUrl, user, password) = getDbConnectionParameters
    val dbName = "DB_NAME" // corresponding props db.url DB
    ConnectionPool.add(dbName, dbUrl, user, password, settings)
    val connectionPool = ConnectionPool.get(dbName)
    MultipleConnectionPoolContext(ConnectionPool.DEFAULT_NAME -> connectionPool)
  }
  
  private def findFirehoseAccounts(bankId: BankId, ordering: SQLSyntax, limit: Int, offset: Int)(implicit session: DBSession = AutoSession) = {
    def parseOwners(owners: String): List[FastFirehoseOwners] = {
      if(!owners.isEmpty) {
        transformString(owners).map {
          i =>
            FastFirehoseOwners(
              user_id = i("user_id").mkString(""),
              provider = i("provider").mkString(""),
              user_name = i("user_name").mkString("")
            )
        }
      } else {
        List()
      }
    }
    def parseRoutings(owners: String): List[FastFirehoseRoutings] = {
      if(!owners.isEmpty) {
        transformString(owners).map {
          i =>
            FastFirehoseRoutings(
              bank_id = i("bank_id").mkString(""),
              account_id = i("account_id").mkString("")
            )
        }
      } else {
        List()
      }
    }
    def parseAttributes(owners: String): List[FastFirehoseAttributes] = {
      if(!owners.isEmpty) {
        transformString(owners).map {
          i =>
            FastFirehoseAttributes(
              `type` = i("type").mkString(""),
              code = i("code").mkString(""),
              value = i("value").mkString("")
            )
        }
      } else {
        List()
      }
    }
    def transformString(owners: String): List[Map[String, List[String]]] = {
      val splitToRows: List[String] = owners.split("::").toList
      val keyValuePairs: List[List[(String, String)]] = splitToRows.map { i=>
        i.split(",").toList.map {
          x =>
            val keyValue: Array[String] = x.split(":")
            if(keyValue.size == 2) (keyValue(0), keyValue(1)) else (keyValue(0), "")
        }
      }
      val maps: List[Map[String, List[String]]] = keyValuePairs.map(_.groupBy(_._1).map { case (k,v) => (k,v.map(_._2))})
      maps
    }

    val sqlResult = sql"""
       |select
       |    mappedbankaccount.theaccountid as account_id,
       |    mappedbankaccount.bank as bank_id,
       |    mappedbankaccount.accountlabel as account_label,
       |    mappedbankaccount.accountnumber as account_number,
       |    (select
       |        string_agg(
       |            'user_id:'
       |            || resourceuser.userid_
       |            ||',provider:'
       |            ||resourceuser.provider_
       |            ||',user_name:'
       |            ||resourceuser.name_,
       |         '::') as owners
       |     from resourceuser
       |     where
       |        resourceuser.id = mapperaccountholders.user_c
       |    ),
       |    mappedbankaccount.kind as kind,
       |    mappedbankaccount.accountcurrency as account_currency ,
       |    mappedbankaccount.accountbalance as account_balance,
       |    (select 
       |        string_agg(
       |            'bank_id:'
       |            ||bankaccountrouting.bankid 
       |            ||',account_id:' 
       |            ||bankaccountrouting.accountid,
       |            '::'
       |            ) as account_routings
       |        from bankaccountrouting
       |        where 
       |              bankaccountrouting.accountid = mappedbankaccount.theaccountid
       |     ),                                                          
       |    (select 
       |        string_agg(
       |                'type:'
       |                || mappedaccountattribute.mtype
       |                ||',code:'
       |                ||mappedaccountattribute.mcode
       |                ||',value:'
       |                ||mappedaccountattribute.mvalue,
       |            '::') as account_attributes
       |    from mappedaccountattribute
       |    where
       |         mappedaccountattribute.maccountid = mappedbankaccount.theaccountid
       |     )
       |from mappedbankaccount
       |         LEFT JOIN mapperaccountholders
       |                   ON (mappedbankaccount.bank = mapperaccountholders.accountbankpermalink and mappedbankaccount.theaccountid = mapperaccountholders.accountpermalink)
       |WHERE mappedbankaccount.bank = ${bankId.value}
       |ORDER BY mappedbankaccount.theaccountid $ordering
       |LIMIT $limit
       |OFFSET $offset ;
       |
       |
       |""".stripMargin
      .map {
        rs => // Map result to case class
          val owners = parseOwners(rs.stringOpt(5).map(_.toString).getOrElse(""))
          val routings = parseRoutings(rs.stringOpt(9).map(_.toString).getOrElse(""))
          val attributes = parseAttributes(rs.stringOpt(10).map(_.toString).getOrElse(""))
          FastFirehoseAccount(
            id = rs.stringOpt(1).map(_.toString).getOrElse(null),
            bankId = rs.stringOpt(2).map(_.toString).getOrElse(null),
            label = rs.stringOpt(3).map(_.toString).getOrElse(null),
            number = rs.stringOpt(4).map(_.toString).getOrElse(null),
            owners = owners,
            productCode = rs.stringOpt(6).map(_.toString).getOrElse(null),
            balance = AmountOfMoney(
              currency = rs.stringOpt(7).map(_.toString).getOrElse(null),
              amount = rs.bigIntOpt(8).map(a =>
                Helper.smallestCurrencyUnitToBigDecimal(
                  a.longValue(),
                  rs.stringOpt(7).getOrElse("EUR")
                ).toString()
              ).getOrElse(null)
            ),
            accountRoutings = routings,
            accountAttributes = attributes
          )
      }.list().apply()
    sqlResult
  }
  
  override def getBankAccountsWithAttributes(bankId: BankId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[FastFirehoseAccount]]] =
    Future{
      val limit: Int = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(Constant.Pagination.limit)
      val offset = queryParams.collect { case OBPOffset(value) => value }.headOption.getOrElse(Constant.Pagination.offset)
      val orderBy = queryParams.collect { 
        case OBPOrdering(_, OBPDescending) => "DESC"
      }.headOption.getOrElse("ASC")

      val ordering: SQLSyntax = if (orderBy =="DESC" ) sqls"DESC" else sqls"ASC"
      
      val firehoseAccounts = {
        scalikeDB readOnly { implicit session =>
          findFirehoseAccounts(bankId, ordering, limit, offset)
        }
      }
      (Full(firehoseAccounts), callContext)
    }

  override def getBankSettlementAccounts(bankId: BankId, callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccount]]] = {
    Future {
      Full {
        MappedBankAccount.findAll(
          By(MappedBankAccount.bank, bankId.value),
          By(MappedBankAccount.kind, "SETTLEMENT")
        )
      }
    }.map(account => (account, callContext))
  }

  // localConnector/getBankAccountsHeld/bankIdAccountIds/{bankIdAccountIds}
  override def getBankAccountsHeldLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Box[List[AccountHeld]] = {
    Full(
      bankIdAccountIds
        .map(bankIdAccountId =>
          getBankAccountOld(
            bankIdAccountId.bankId,
            bankIdAccountId.accountId)
            .openOrThrowException(s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"))
        .map(account =>
          AccountHeld(
            account.accountId.value,
            account.label,
            account.bankId.value,
            stringOrNull(account.number),
            account.accountRoutings))
    )
  }

  override def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[AccountHeld]]] = {
    Future {
      (getBankAccountsHeldLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]), callContext)
    }
  }
  override def getAccountsHeld(bankId: BankId, user: User, callContext: Option[CallContext]): OBPReturnType[Box[List[BankIdAccountId]]] = {
    Future {
      (Full(AccountHolders.accountHolders.vend.getAccountsHeld(bankId, user).toList), callContext)
    }
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

    val mappedBankAccount = getBankAccountOld(bankId, accountId).map(_.asInstanceOf[MappedBankAccount]) match {
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
        otherAccountRoutingScheme = "",
        otherAccountProvider = "",
        isBeneficiary = true
      )
    }
  }

  override def getCounterpartyTrait(bankId: BankId, accountId: AccountId, counterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
    getCounterpartyByCounterpartyId(CounterpartyId(counterpartyId), callContext)
  }

  override def getCounterpartyByCounterpartyIdLegacy(counterpartyId: CounterpartyId, callContext: Option[CallContext]): Box[(CounterpartyTrait, Option[CallContext])] = {
    Counterparties.counterparties.vend.getCounterparty(counterpartyId.value).map(counterparty => (counterparty, callContext))
  }

  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = Future {
    (Counterparties.counterparties.vend.getCounterparty(counterpartyId.value), callContext)
  }

  override def deleteCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future {
    (Counterparties.counterparties.vend.deleteCounterparty(counterpartyId.value), callContext)
  }

  override def getCounterpartyByIban(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
    Future(Counterparties.counterparties.vend.getCounterpartyByIban(iban), callContext)
  }

  override def getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) = {
    Future(Counterparties.counterparties.vend.getCounterpartyByIbanAndBankAccountId(iban, bankId, accountId), callContext)
  }

  override def getCounterpartyByRoutings(
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String,
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CounterpartyTrait]] = Future {
    lazy val counterpartyFromRoutings= Counterparties.counterparties.vend.getCounterpartyByRoutings(
      otherBankRoutingScheme: String,
      otherBankRoutingAddress: String,
      otherBranchRoutingScheme: String,
      otherBranchRoutingAddress: String,
      otherAccountRoutingScheme: String,
      otherAccountRoutingAddress: String
    )

    lazy val counterpartyFromSecondaryRouting = Counterparties.counterparties.vend.getCounterpartyBySecondaryRouting(
      otherAccountSecondaryRoutingScheme: String,
      otherAccountSecondaryRoutingAddress: String
    )

    if(counterpartyFromRoutings.isDefined) {
      (counterpartyFromRoutings, callContext)
    } else if(counterpartyFromSecondaryRouting.isDefined) {
      (counterpartyFromSecondaryRouting, callContext)
    } else {
      (Failure(CounterpartyNotFoundByRoutings), callContext)
    }
  
  }
  
  
  override def getOrCreateCounterparty(
    name: String,
    description: String,
    currency: String,
    createdByUserId: String,
    thisBankId: String,
    thisAccountId: String,
    thisViewId: String,
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String,
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CounterpartyTrait]] = Future {
    
    lazy val counterpartyFromRoutings= Counterparties.counterparties.vend.getCounterpartyByRoutings(
      otherBankRoutingScheme: String,
      otherBankRoutingAddress: String,
      otherBranchRoutingScheme: String,
      otherBranchRoutingAddress: String,
      otherAccountRoutingScheme: String,
      otherAccountRoutingAddress: String
    ) 
    
    lazy val counterpartyFromSecondaryRouting = Counterparties.counterparties.vend.getCounterpartyBySecondaryRouting(
      otherAccountSecondaryRoutingScheme: String,
      otherAccountSecondaryRoutingAddress: String
    )


    if(counterpartyFromRoutings.isDefined) {
      (counterpartyFromRoutings, callContext)
    } else if(counterpartyFromSecondaryRouting.isDefined) {
      (counterpartyFromSecondaryRouting, callContext)
    } else{
      val newCounterparty = for{
        _ <- Helper.booleanToBox(
          Counterparties.counterparties.vend.checkCounterpartyExists(
            name: String,
            thisBankId: String,
            thisAccountId: String,
            thisViewId: String
          ).isEmpty, 
          CounterpartyAlreadyExists.replace("value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME.",
          s"COUNTERPARTY_NAME(${name}) for the BANK_ID(${thisBankId}) and ACCOUNT_ID(${thisAccountId}) and VIEW_ID($thisViewId)")
        )
        
        counterparty <- Counterparties.counterparties.vend.createCounterparty(
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
          isBeneficiary = true,
          otherAccountSecondaryRoutingScheme = otherAccountSecondaryRoutingScheme,
          otherAccountSecondaryRoutingAddress = otherAccountSecondaryRoutingAddress,
          description = description,
          currency = currency,
          bespoke = Nil
        )
      } yield{
        counterparty
      }
      (newCounterparty, callContext)
    }
  }

  override def getPhysicalCardsForUser(user: User, callContext: Option[CallContext]): OBPReturnType[Box[List[PhysicalCard]]] = Future {
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCards(user)
    val cardList = for (l <- list) yield
      PhysicalCard(
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
        customerId = l.customerId,
        cvv = l.cvv,
        brand = l.brand
      )
    (Full(cardList), callContext)
  }

  override def getPhysicalCardsForBank(bank: Bank, user: User, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[PhysicalCard]]] = Future {
    (
      getPhysicalCardsForBankLegacy(bank: Bank, user: User, queryParams),
      callContext
    )
  }

  override def getPhysicalCardByCardNumber(bankCardNumber: String,  callContext:Option[CallContext]) : OBPReturnType[Box[PhysicalCardTrait]] = Future {
    (
      code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCardByCardNumber(bankCardNumber: String, callContext: Option[CallContext]),
      callContext
    )
  }

  override def getPhysicalCardsForBankLegacy(bank: Bank, user: User, queryParams: List[OBPQueryParam]): Box[List[PhysicalCard]] = {
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCardsForBank(bank, user, queryParams)
    val cardList = for (l <- list) yield
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
        customerId = l.customerId,
        cvv = l.cvv,
        brand = l.brand
      )
    Full(cardList)
  }

  override def getPhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCardTrait]] = Future {
    (code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCardForBank(bankId: BankId, cardId: String, callContext),
      callContext)
  }

  override def deletePhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future {
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
                                   cvv: String,
                                   brand: String,
                                   callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCard]] = Future {
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
      cvv: String,
      brand: String,
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
                                         cvv: String,
                                         brand: String,
                                         callContext: Option[CallContext]): Box[PhysicalCard] = {
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
      cvv: String,
      brand: String,
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
        customerId = l.customerId,
        cvv = l.cvv,
        brand = l.brand,
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
                                 ): OBPReturnType[Box[PhysicalCardTrait]] = Future {
    (
      code.cards.PhysicalCard.physicalCardProvider.vend.updatePhysicalCard(
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
        callContext: Option[CallContext]),
      callContext)
  }

  override def getCardAttributeById(cardAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[CardAttribute]] = {
    CardAttributeX.cardAttributeProvider.vend.getCardAttributeById(cardAttributeId: String) map {
      (_, callContext)
    }
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
      value: String) map {
      (_, callContext)
    }
  }

  override def getCardAttributesFromProvider(
                                              cardId: String,
                                              callContext: Option[CallContext]): OBPReturnType[Box[List[CardAttribute]]] = {
    CardAttributeX.cardAttributeProvider.vend.getCardAttributesFromProvider(cardId: String) map {
      (_, callContext)
    }
  }

  override def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId,
                                                           callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.getTransactionRequestAttributesFromProvider(
      transactionRequestId: TransactionRequestId
    ).map((_, callContext))
  }

  override def getTransactionRequestAttributes(bankId: BankId,
                                               transactionRequestId: TransactionRequestId,
                                               callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.getTransactionRequestAttributes(
      bankId: BankId,
      transactionRequestId: TransactionRequestId
    ).map((_, callContext))
  }

  override def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                              transactionRequestId: TransactionRequestId,
                                                              viewId: ViewId,
                                                              callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.getTransactionRequestAttributesCanBeSeenOnView(
      bankId: BankId,
      transactionRequestId: TransactionRequestId,
      viewId: ViewId
    ).map((_, callContext))
  }

  override def getTransactionRequestAttributeById(transactionRequestAttributeId: String,
                                                  callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequestAttributeTrait]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.getTransactionRequestAttributeById(
      transactionRequestAttributeId: String
    ).map((_, callContext))
  }

  override def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]],
                                                             callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.getTransactionRequestIdsByAttributeNameValues(
      bankId: BankId,
      params: Map[String, List[String]]
    ).map((_, callContext))
  }

  override def createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                         transactionRequestId: TransactionRequestId,
                                                         transactionRequestAttributeId: Option[String],
                                                         name: String,
                                                         attributeType: TransactionRequestAttributeType.Value,
                                                         value: String,
                                                         callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequestAttributeTrait]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.createOrUpdateTransactionRequestAttribute(
      bankId: BankId,
      transactionRequestId: TransactionRequestId,
      transactionRequestAttributeId: Option[String],
      name: String,
      attributeType: TransactionRequestAttributeType.Value,
      value: String
    ).map((_, callContext))
  }

  override def createTransactionRequestAttributes(bankId: BankId,
                                                  transactionRequestId: TransactionRequestId,
                                                  transactionRequestAttributes: List[TransactionRequestAttributeTrait],
                                                  callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.createTransactionRequestAttributes(
      bankId: BankId,
      transactionRequestId: TransactionRequestId,
      transactionRequestAttributes: List[TransactionRequestAttributeTrait]
    ).map((_, callContext))
  }

  override def deleteTransactionRequestAttribute(transactionRequestAttributeId: String,
                                                 callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.deleteTransactionRequestAttribute(
      transactionRequestAttributeId: String
    ).map((_, callContext))
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
    for {
      //def exchangeRate --> do not return any exception, but it may return NONO there.   
      rate <- Full (fx.exchangeRate(fromAccount.currency, toAccount.currency, Some(fromAccount.bankId.value)))
      _ <- booleanToBox(rate.isDefined) ?~! s"$InvalidCurrency The requested currency conversion (${fromAccount.currency} to ${fromAccount.currency}) is not supported."
      
      fromTransAmt = -amount //from fromAccount balance should decrease
      toTransAmt = fx.convert(amount, rate)
      sentTransactionId <- saveTransaction(fromAccount, toAccount, transactionRequestCommonBody, fromTransAmt, description, transactionRequestType, chargePolicy)
      _sentTransactionId <- saveTransaction(toAccount, fromAccount, transactionRequestCommonBody, toTransAmt, description, transactionRequestType, chargePolicy)
    } yield {
      sentTransactionId
    }
  }

  override def makePaymentv210(fromAccount: BankAccount,
                               toAccount: BankAccount,
                               transactionRequestId: TransactionRequestId,
                               transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                               amount: BigDecimal,
                               description: String,
                               transactionRequestType: TransactionRequestType,
                               chargePolicy: String,
                               callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] =
    savePayment(fromAccount, toAccount, transactionRequestId, transactionRequestCommonBody, amount, description, transactionRequestType, chargePolicy, callContext)

  override def saveDoubleEntryBookTransaction(doubleEntryTransaction: DoubleEntryTransaction,
                                              callContext: Option[CallContext]): OBPReturnType[Box[DoubleEntryTransaction]] = {
  Future(
    tryo(DoubleEntryBookTransaction.create
      .TransactionRequestBankId(doubleEntryTransaction.transactionRequestBankId.map(_.value).getOrElse(""))
      .TransactionRequestAccountId(doubleEntryTransaction.transactionRequestAccountId.map(_.value).getOrElse(""))
      .TransactionRequestId(doubleEntryTransaction.transactionRequestId.map(_.value).getOrElse(""))
      .DebitTransactionBankId(doubleEntryTransaction.debitTransactionBankId.value)
      .DebitTransactionAccountId(doubleEntryTransaction.debitTransactionAccountId.value)
      .DebitTransactionId(doubleEntryTransaction.debitTransactionId.value)
      .CreditTransactionBankId(doubleEntryTransaction.creditTransactionBankId.value)
      .CreditTransactionAccountId(doubleEntryTransaction.creditTransactionAccountId.value)
      .CreditTransactionId(doubleEntryTransaction.creditTransactionId.value)
      .saveMe())
  ).map(doubleEntryTransaction => (doubleEntryTransaction, callContext))
  }

  override def getDoubleEntryBookTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId,
                                              callContext: Option[CallContext]): OBPReturnType[Box[DoubleEntryTransaction]] = {
    Future(
      DoubleEntryBookTransaction.find(
          By(DoubleEntryBookTransaction.DebitTransactionBankId, bankId.value),
          By(DoubleEntryBookTransaction.DebitTransactionAccountId, accountId.value),
          By(DoubleEntryBookTransaction.DebitTransactionId, transactionId.value)
        ).or(DoubleEntryBookTransaction.find(
        By(DoubleEntryBookTransaction.CreditTransactionBankId, bankId.value),
        By(DoubleEntryBookTransaction.CreditTransactionAccountId, accountId.value),
        By(DoubleEntryBookTransaction.CreditTransactionId, transactionId.value)
      ))
    ).map(doubleEntryTransaction => (doubleEntryTransaction, callContext))
  }
  override def getBalancingTransaction(transactionId: TransactionId,
                                       callContext: Option[CallContext]): OBPReturnType[Box[DoubleEntryTransaction]] = {
    Future(
      DoubleEntryBookTransaction.find(
          By(DoubleEntryBookTransaction.DebitTransactionId, transactionId.value)
        ).or(DoubleEntryBookTransaction.find(
        By(DoubleEntryBookTransaction.CreditTransactionId, transactionId.value)
      ))
    ).map(doubleEntryTransaction => (doubleEntryTransaction, callContext))
  }

  override def makePaymentV400(transactionRequest: TransactionRequest,
                               reasons: Option[List[TransactionRequestReason]],
                               callContext: Option[CallContext]): Future[Box[(TransactionId, Option[CallContext])]] = Future {

    val amount = BigDecimal(transactionRequest.body.value.amount)
    val description = transactionRequest.body.description
    val transactionRequestType = TransactionRequestType(transactionRequest.`type`)
    val chargePolicy = transactionRequest.charge_policy
    val fromBankId = BankId(transactionRequest.from.bank_id)
    val fromAccountId = AccountId(transactionRequest.from.account_id)
    val fromAccount = Connector.connector.vend.getBankAccountOld(fromBankId, fromAccountId).openOrThrowException(s"$BankAccountNotFound Current Bank_Id(${fromBankId}), Account_Id(${fromAccountId}) ")
    val transactionRequestCommonBody = TransactionRequestCommonBodyJSONCommons(
      AmountOfMoneyJsonV121(
        transactionRequest.body.value.currency,
        transactionRequest.body.value.amount
      ),
      transactionRequest.body.description
    )
    val toAccountRoutingScheme = transactionRequest.other_account_routing_scheme
    val toAccountRoutingAddress = transactionRequest.other_account_routing_address

    for {
      toAccount <-
        Connector.connector.vend.getBankAccountByRouting(None, toAccountRoutingScheme, toAccountRoutingAddress, None) match {
          case Full(bankAccount) => Future.successful(bankAccount._1)
          case _: EmptyBox =>
            NewStyle.function.getCounterpartyByIban(toAccountRoutingAddress, callContext).flatMap(counterparty =>
              NewStyle.function.getBankAccountFromCounterparty(counterparty._1, isOutgoingAccount = true, callContext)
            )
        }
      (debitTransactionId, callContext) <- savePayment(
        fromAccount, toAccount, transactionRequest.id, transactionRequestCommonBody, amount, description, transactionRequestType, chargePolicy, callContext)
    } yield (debitTransactionId, callContext)
  }

  override def makeHistoricalPayment(
                                      fromAccount: BankAccount,
                                      toAccount: BankAccount,
                                      posted: Date,
                                      completed: Date,
                                      amount: BigDecimal,
                                      currency: String,
                                      description: String,
                                      transactionRequestType: String,
                                      chargePolicy: String,
                                      callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = {
    for {
      /* Here there is three possibilities
        - fromAccount and toAccount are two real OBP accounts, in this case, we take the exchange rate of the fromAccount bankId
        - fromAccount is a real OBP account and toAccount is a fake account from counterparty, in this case, we take the exchange rate of the fromAccount bankId
        - toAccount is a real OBP account and fromAccount is a fake account from counterparty, in this case, we take the exchange rate of the toAccount bankId
        NOTE: if fromAccount and toAccount are fake account from counterparty, the makeHistoricalPayment will fail
       */

      (bankIdExchangeRate, callContext) <- NewStyle.function.getBank(fromAccount.bankId, callContext)
        .fallbackTo(NewStyle.function.getBank(toAccount.bankId, callContext))

      debitRate <- Future (fx.exchangeRate(currency, fromAccount.currency, Some(bankIdExchangeRate.bankId.value)))
      _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion ($currency to ${fromAccount.currency}) is not supported.", cc=callContext){debitRate.isDefined}
      creditRate <- Future (fx.exchangeRate(currency, toAccount.currency, Some(bankIdExchangeRate.bankId.value)))
      _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion ($currency to ${toAccount.currency}) is not supported.", cc=callContext){creditRate.isDefined}

      fromTransAmt = -fx.convert(amount, debitRate) //from fromAccount balance should decrease
      toTransAmt = fx.convert(amount, creditRate)

      debitTransactionBox <- Future(
        saveHistoricalTransaction(fromAccount, toAccount, posted, completed, fromTransAmt, description, transactionRequestType, chargePolicy, callContext)
          .map(debitTransactionId => (fromAccount.bankId, fromAccount.accountId, debitTransactionId, false))
          .or {
            // If we don't find any corresponding obp account, we debit a bank settlement account
            val settlementAccount = {
              // We first look for a specific settlement account regarding the payment system (SEPA, ...) used and the currency
              BankAccountX(toAccount.bankId, AccountId(transactionRequestType + "_SETTLEMENT_ACCOUNT_" + fromAccount.currency), callContext)
                // If it doesn't exist, we look for a default settlement account regarding the currency
                .or(BankAccountX(toAccount.bankId, AccountId("DEFAULT_SETTLEMENT_ACCOUNT_" + fromAccount.currency), callContext))
                // If no specific settlement account exist for this currency, we use the default incoming account (EUR)
                .or(BankAccountX(toAccount.bankId, AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), callContext))
            }
            settlementAccount.flatMap(settlementAccount => {
              val fromTransAmtSettlementAccount: BigDecimal = {
              // In the case we selected the default settlement account INCOMING_ACCOUNT_ID account and that the counterparty currency is different from EUR, we need to calculate the amount in EUR
                if (settlementAccount._1.accountId.value == INCOMING_SETTLEMENT_ACCOUNT_ID && settlementAccount._1.currency != fromAccount.currency) {
                  val rate = fx.exchangeRate(currency, settlementAccount._1.currency, Some(bankIdExchangeRate.bankId.value))
                  Try(-fx.convert(amount, rate)).getOrElse(throw new Exception(s"$InvalidCurrency The requested currency conversion ($currency to ${settlementAccount._1.currency}) is not supported."))
                } else fromTransAmt
              }
              saveHistoricalTransaction(settlementAccount._1, toAccount, posted, completed, fromTransAmtSettlementAccount, description, transactionRequestType, chargePolicy, callContext)
                  .map(debitTransactionId => (settlementAccount._1.bankId, settlementAccount._1.accountId, debitTransactionId, true))
            })
          }
      )
      creditTransactionBox <- Future(
        saveHistoricalTransaction(toAccount, fromAccount, posted, completed, toTransAmt, description, transactionRequestType, chargePolicy, callContext)
          .map(creditTransactionId => (toAccount.bankId, toAccount.accountId, creditTransactionId, false))
          .or {
            // If we don't find any corresponding obp account, we credit a bank settlement account
            val settlementAccount =
              // We first look for a specific settlement account regarding the payment system (SEPA, ...) used and the currency
              BankAccountX(fromAccount.bankId, AccountId(transactionRequestType + "_SETTLEMENT_ACCOUNT_" + toAccount.currency), callContext)
                // If it doesn't exist, we look for a default settlement account regarding the currency
                .or(BankAccountX(fromAccount.bankId, AccountId("DEFAULT_SETTLEMENT_ACCOUNT_" + toAccount.currency), callContext))
                // If no specific settlement account exist for this currency, we use the default outgoing account (EUR)
                .or(BankAccountX(fromAccount.bankId, AccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID), callContext))
            settlementAccount.flatMap(settlementAccount => {
              val toTransAmtSettlementAccount: BigDecimal = {
                // In the case we selected the default settlement account OUTGOING_ACCOUNT_ID account and that the counterparty currency is different from EUR, we need to calculate the amount in EUR
                if (settlementAccount._1.accountId.value == OUTGOING_SETTLEMENT_ACCOUNT_ID && settlementAccount._1.currency != toAccount.currency) {
                  val rate = fx.exchangeRate(currency, settlementAccount._1.currency, Some(bankIdExchangeRate.bankId.value))
                  Try(fx.convert(amount, rate)).getOrElse(throw new Exception(s"$InvalidCurrency The requested currency conversion ($currency to ${settlementAccount._1.currency}) is not supported."))
                } else toTransAmt
              }
              saveHistoricalTransaction(settlementAccount._1, fromAccount, posted, completed, toTransAmtSettlementAccount, description, transactionRequestType, chargePolicy, callContext)
                .map(creditTransactionId => (settlementAccount._1.bankId, settlementAccount._1.accountId, creditTransactionId, true))
            })
          }
      )

      debitTransaction = debitTransactionBox.openOrThrowException(s"Error while opening debitTransaction. This error can happen when no settlement can be found, please check that $INCOMING_SETTLEMENT_ACCOUNT_ID exists at bank ${toAccount.bankId.value}")
      creditTransaction = creditTransactionBox.openOrThrowException(s"Error while opening creditTransaction. This error can happen when no settlement can be found, please check that $OUTGOING_SETTLEMENT_ACCOUNT_ID exists at bank ${fromAccount.bankId.value}")

      _ <- NewStyle.function.saveDoubleEntryBookTransaction(
        DoubleEntryTransaction(
          transactionRequestBankId = None,
          transactionRequestAccountId = None,
          transactionRequestId = None,
          debitTransactionBankId = debitTransaction._1,
          debitTransactionAccountId = debitTransaction._2,
          debitTransactionId = debitTransaction._3,
          creditTransactionBankId = creditTransaction._1,
          creditTransactionAccountId = creditTransaction._2,
          creditTransactionId = creditTransaction._3
        ), callContext)
    } yield {
      val transactionId: Box[TransactionId] = (debitTransaction._4, creditTransaction._4) match {
        // If the debit transaction is on a settlement account and the credit transaction is on an OBP account, we return the credit transaction id
        case (true, false) => creditTransactionBox.map(_._3)
        // In all the other cases, we return the debit transaction id
        case _ => debitTransactionBox.map(_._3)
      }
      (transactionId, callContext)
      // In the future, we should return the both transactions as the API response
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
                                       ): Box[TransactionId] =
      for {
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
          .counterpartyIban(toAccount.accountRoutings.find(_.scheme == AccountRoutingScheme.IBAN.toString).map(_.address).getOrElse(""))
          .counterpartyNationalId(toAccount.nationalIdentifier)
          //New data: real counterparty (toCounterparty: CounterpartyTrait)
          //      .CPCounterPartyId(toAccount.accountId.value)
          .CPOtherAccountRoutingScheme(toAccount.accountRoutings.headOption.map(_.scheme).getOrElse(""))
          .CPOtherAccountRoutingAddress(toAccount.accountRoutings.headOption.map(_.address).getOrElse(""))
          .CPOtherBankRoutingScheme(toAccount.bankRoutingScheme)
          .CPOtherBankRoutingAddress(toAccount.bankRoutingAddress)
          .chargePolicy(chargePolicy)
          .saveMe) ?~! s"$CreateTransactionsException, exception happened when create new mappedTransaction"
      } yield {
        mappedTransaction.theTransactionId
      }

  private def savePayment(fromAccount: BankAccount,
                          toAccount: BankAccount,
                          transactionRequestId: TransactionRequestId,
                          transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                          amount: BigDecimal,
                          description: String,
                          transactionRequestType: TransactionRequestType,
                          chargePolicy: String,
                          callContext: Option[CallContext]) =
    for {
      /* Here there is three possibilities
        - fromAccount and toAccount are two real OBP accounts, in this case, we take the exchange rate of the fromAccount bankId
        - fromAccount is a real OBP account and toAccount is a fake account from counterparty, in this case, we take the exchange rate of the fromAccount bankId
        - toAccount is a real OBP account and fromAccount is a fake account from counterparty, in this case, we take the exchange rate of the toAccount bankId
        NOTE: if fromAccount and toAccount are fake account from counterparty, the makeHistoricalPayment will fail
       */

      (bankIdExchangeRate, callContext) <- NewStyle.function.getBank(fromAccount.bankId, callContext)
        .fallbackTo(NewStyle.function.getBank(toAccount.bankId, callContext))

      transactionCurrency = transactionRequestCommonBody.value.currency
      debitRate <- Future (fx.exchangeRate(transactionCurrency, fromAccount.currency, Some(bankIdExchangeRate.bankId.value)))
      _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion ($transactionCurrency to ${fromAccount.currency}) is not supported.", cc=callContext){debitRate.isDefined}
      creditRate <- Future (fx.exchangeRate(transactionCurrency, toAccount.currency, Some(bankIdExchangeRate.bankId.value)))
      _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion ($transactionCurrency to ${toAccount.currency}) is not supported.", cc=callContext){creditRate.isDefined}

      fromTransAmt = -fx.convert(amount, debitRate) //from fromAccount balance should decrease
      toTransAmt = fx.convert(amount, creditRate)

      debitTransactionBox <- Future {
        saveTransaction(fromAccount, toAccount, transactionRequestCommonBody, fromTransAmt, description, transactionRequestType, chargePolicy)
          .map(debitTransactionId => (fromAccount.bankId, fromAccount.accountId, debitTransactionId, false))
          .or {
            // If we don't find any corresponding obp account, we debit a bank settlement account
            val settlementAccount =
              // We first look for a specific settlement account regarding the payment system (SEPA, ...) used and the currency
              BankAccountX(toAccount.bankId, AccountId(transactionRequestType + "_SETTLEMENT_ACCOUNT_" + fromAccount.currency), callContext)
                // If it doesn't exist, we look for a default settlement account regarding the currency
                .or(BankAccountX(toAccount.bankId, AccountId("DEFAULT_SETTLEMENT_ACCOUNT_" + fromAccount.currency), callContext))
                // If no specific settlement account exist for this currency, we use the default incoming account (EUR)
                .or(BankAccountX(toAccount.bankId, AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), callContext))
            settlementAccount.flatMap(settlementAccount => {
              val fromTransAmtSettlementAccount = {
                // In the case we selected the default settlement account INCOMING_ACCOUNT_ID account and that the counterparty currency is different from EUR, we need to calculate the amount in EUR
                if (settlementAccount._1.accountId.value == INCOMING_SETTLEMENT_ACCOUNT_ID && settlementAccount._1.currency != fromAccount.currency) {
                  val rate = fx.exchangeRate(transactionCurrency, settlementAccount._1.currency, Some(bankIdExchangeRate.bankId.value))
                  Try(-fx.convert(amount, rate)).getOrElse(throw new Exception(s"$InvalidCurrency The requested currency conversion ($transactionCurrency to ${settlementAccount._1.currency}) is not supported."))
                } else fromTransAmt
              }
              saveTransaction(settlementAccount._1, toAccount, transactionRequestCommonBody, fromTransAmtSettlementAccount, description, transactionRequestType, chargePolicy)
                .map(debitTransactionId => (settlementAccount._1.bankId, settlementAccount._1.accountId, debitTransactionId, true))
            })
          }
      }
      creditTransactionBox <- Future {
        saveTransaction(toAccount, fromAccount, transactionRequestCommonBody, toTransAmt, description, transactionRequestType, chargePolicy)
          .map(creditTransactionId => (toAccount.bankId, toAccount.accountId, creditTransactionId, false))
          .or {
            // If we don't find any corresponding obp account, we credit a bank settlement account
            val settlementAccount =
            // We first look for a specific settlement account regarding the payment system (SEPA, ...) used and the currency
              BankAccountX(fromAccount.bankId, AccountId(transactionRequestType + "_SETTLEMENT_ACCOUNT_" + toAccount.currency), callContext)
                // If it doesn't exist, we look for a default settlement account regarding the currency
                .or(BankAccountX(fromAccount.bankId, AccountId("DEFAULT_SETTLEMENT_ACCOUNT_" + toAccount.currency), callContext))
                // If no specific settlement account exist for this currency, we use the default outgoing account (EUR)
                .or(BankAccountX(fromAccount.bankId, AccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID), callContext))
            settlementAccount.flatMap(settlementAccount => {
              val toTransAmtSettlementAccount = {
                // In the case we selected the default settlement account OUTGOING_ACCOUNT_ID account and that the counterparty currency is different from EUR, we need to calculate the amount in EUR
                if (settlementAccount._1.accountId.value == OUTGOING_SETTLEMENT_ACCOUNT_ID && settlementAccount._1.currency != toAccount.currency) {
                  val rate = fx.exchangeRate(transactionCurrency, settlementAccount._1.currency, Some(bankIdExchangeRate.bankId.value))
                  Try(fx.convert(amount, rate)).getOrElse(throw new Exception(s"$InvalidCurrency The requested currency conversion ($transactionCurrency to ${settlementAccount._1.currency}) is not supported."))
                } else toTransAmt
              }
              saveTransaction(settlementAccount._1, fromAccount, transactionRequestCommonBody, toTransAmtSettlementAccount, description, transactionRequestType, chargePolicy)
                .map(creditTransactionId => (settlementAccount._1.bankId, settlementAccount._1.accountId, creditTransactionId, true))
            })
          }
      }

      debitTransaction = debitTransactionBox.openOrThrowException(s"Error while opening debitTransaction. This error can happen when no settlement can be found, please check that $INCOMING_SETTLEMENT_ACCOUNT_ID exists at bank ${toAccount.bankId.value}")
      creditTransaction = creditTransactionBox.openOrThrowException(s"Error while opening creditTransaction. This error can happen when no settlement can be found, please check that $OUTGOING_SETTLEMENT_ACCOUNT_ID exists at bank ${fromAccount.bankId.value}")

      _ <- NewStyle.function.saveDoubleEntryBookTransaction(
        DoubleEntryTransaction(
          transactionRequestBankId = Some(fromAccount.bankId),
          transactionRequestAccountId = Some(fromAccount.accountId),
          transactionRequestId = Some(transactionRequestId),
          debitTransactionBankId = debitTransaction._1,
          debitTransactionAccountId = debitTransaction._2,
          debitTransactionId = debitTransaction._3,
          creditTransactionBankId = creditTransaction._1,
          creditTransactionAccountId = creditTransaction._2,
          creditTransactionId = creditTransaction._3
        ), callContext)
    } yield {
      val transactionId: Box[TransactionId] = (debitTransaction._4, creditTransaction._4) match {
        // If the debit transaction is on a settlement account and the credit transaction is on an OBP account, we return the credit transaction id
        case (true, false) => creditTransactionBox.map(_._3)
        // In all the other cases, we return the debit transaction id
        case _ => debitTransactionBox.map(_._3)
      }
      (transactionId, callContext)
      // In the future, we should return the both transactions as the API response
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
    for {

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
        .counterpartyIban(toAccount.accountRoutings.find(_.scheme == AccountRoutingScheme.IBAN.toString).map(_.address).getOrElse(""))
        .counterpartyNationalId(toAccount.nationalIdentifier)
        //New data: real counterparty (toCounterparty: CounterpartyTrait)
        //      .CPCounterPartyId(toAccount.accountId.value)
        .CPOtherAccountRoutingScheme(toAccount.accountRoutings.headOption.map(_.scheme).getOrElse(""))
        .CPOtherAccountRoutingAddress(toAccount.accountRoutings.headOption.map(_.address).getOrElse(""))
        .CPOtherBankRoutingScheme(toAccount.bankRoutingScheme)
        .CPOtherBankRoutingAddress(toAccount.bankRoutingAddress)
        .chargePolicy(chargePolicy)
        .saveMe) ?~! s"$CreateTransactionsException, exception happened when create new mappedTransaction"
    } yield {
      mappedTransaction.theTransactionId
    }
  }
  
  override def cancelPaymentV400(transactionId: TransactionId,
                                 callContext: Option[CallContext]): OBPReturnType[Box[CancelPayment]] = Future {
    (Full(CancelPayment(true, Some(true))), callContext)
  }

  /*
    Transaction Requests
  */
  override def getTransactionRequestStatusesImpl(): Box[TransactionRequestStatus] = Empty

  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId,
                                            transactionRequestType: TransactionRequestType,
                                            account: BankAccount,
                                            counterparty: BankAccount,
                                            body: TransactionRequestBody,
                                            status: String,
                                            charge: TransactionRequestCharge): Box[TransactionRequest] = {
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

  override def saveTransactionRequestDescriptionImpl(transactionRequestId: TransactionRequestId, description: String): Box[Boolean] = {
    TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestDescriptionImpl(transactionRequestId, description)
  }


  override def getTransactionRequestsImpl(fromAccount: BankAccount): Box[List[TransactionRequest]] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
  }

  override def getTransactionRequestsImpl210(fromAccount: BankAccount): Box[List[TransactionRequest]] = {
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
                                   ): Box[(Bank, BankAccount)] = {
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
      "",
      List.empty
    )

    Full((bank, account))
  }

  override def updateBankAccount(
                                  bankId: BankId,
                                  accountId: AccountId,
                                  accountType: String,
                                  accountLabel: String,
                                  branchId: String,
                                  accountRoutings: List[AccountRouting],
                                  callContext: Option[CallContext]
                                ): OBPReturnType[Box[BankAccount]] = Future {

    val oldAccountRoutings: List[BankAccountRouting] = BankAccountRouting.findAll(By(BankAccountRouting.BankId, bankId.value),
      By(BankAccountRouting.AccountId, accountId.value))

    // Add or update new routing schemes
    accountRoutings.foreach(accountRouting =>
      oldAccountRoutings.find(_.accountRouting.scheme == accountRouting.scheme) match {
        case Some(updatedAccountRouting) =>
          updatedAccountRouting.AccountRoutingAddress(accountRouting.address).saveMe()
        case None =>
          BankAccountRouting.create
            .BankId(bankId.value)
            .AccountId(accountId.value)
            .AccountRoutingScheme(accountRouting.scheme)
            .AccountRoutingAddress(accountRouting.address)
            .saveMe()
      }
    )

    // Delete non-present routing schemes
    oldAccountRoutings.filterNot(accountRouting => accountRoutings.exists(_.scheme == accountRouting.accountRouting.scheme))
      .foreach(_.delete_!)

    (for {
      (account, _) <- LocalMappedConnector.getBankAccountCommon(bankId, accountId, callContext)
    } yield {
      account
        .kind(accountType)
        .accountLabel(accountLabel)
        .mBranchId(branchId)
        .saveMe
    }, callContext)
  }

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String): Box[Boolean] = {
    Full(MappedBankAccount.count(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.accountNumber, accountNumber)) > 0)
  }

  //remove an account and associated transactions
  override def removeAccount(bankId: BankId, accountId: AccountId): Box[Boolean] = {
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
                               accountRoutings: List[AccountRouting],
                               callContext: Option[CallContext]
                             ): OBPReturnType[Box[BankAccount]] = Future {
    val accountId = AccountId(APIUtil.generateUUID())
    val uniqueAccountNumber = {
      def exists(number: String) = accountExists(bankId, number).openOrThrowException(attemptedToOpenAnEmptyBox)

      def appendUntilOkay(number: String): String = {
        val newNumber = number + Random.nextInt(10)
        if (!exists(newNumber)) newNumber
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
      branchId: String, //added field in V220
      accountRoutings
    ), callContext)
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
                                  accountRoutings: List[AccountRouting],
                                  callContext: Option[CallContext]
                                ): OBPReturnType[Box[BankAccount]] = Future {
    (Connector.connector.vend.createBankAccountLegacy(bankId: BankId,
      accountId: AccountId,
      accountType: String,
      accountLabel: String,
      currency: String,
      initialBalance: BigDecimal,
      accountHolderName: String,
      branchId: String,
      accountRoutings: List[AccountRouting]), callContext)
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
                                         accountRoutings: List[AccountRouting]
                                       ): Box[BankAccount] = {

    for {
      (bank, _) <- getBankLegacy(bankId, None) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
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
        accountRoutings
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
                                          accountRoutings: List[AccountRouting],
                                        ): BankAccount = {
    getBankAccountOld(bankId, accountId) match {
      case Full(a) =>
        logger.debug(s"account with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        a
      case _ =>
        accountRoutings.map(accountRouting =>
          BankAccountRouting.create
            .BankId(bankId.value)
            .AccountId(accountId.value)
            .AccountRoutingScheme(accountRouting.scheme)
            .AccountRoutingAddress(accountRouting.address)
            .saveMe()
        )
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
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal): Box[Boolean] = {
    //this will be Full(true) if everything went well
    val result = for {
      bank <- getMappedBank(bankId)
      account <- getBankAccountOld(bankId, accountId).map(_.asInstanceOf[MappedBankAccount])
    } yield {
      account.accountBalance(Helper.convertToSmallestCurrencyUnits(newBalance, account.currency)).save
      setBankAccountLastUpdated(bank.nationalIdentifier, account.number, now).openOrThrowException(attemptedToOpenAnEmptyBox)
    }

    Full(result.getOrElse(false))
  }

  //transaction import api uses bank national identifiers to uniquely indentify banks,
  //which is unfortunate as theoretically the national identifier is unique to a bank within
  //one country
  private def getBankByNationalIdentifier(nationalIdentifier: String): Box[Bank] = {
    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
  }

  private def getAccountByNumber(bankId: BankId, number: String): Box[BankAccount] = {
    MappedBankAccount.find(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.accountNumber, number))
  }

  private val bigDecimalFailureHandler: PartialFunction[Throwable, Unit] = {
    case ex: NumberFormatException => {
      logger.warn(s"could not convert amount to a BigDecimal: $ex")
    }
  }

  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier: String, accountNumber: String, amount: String, completed: Date, otherAccountHolder: String): Box[Int] = {
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

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber: String, updateDate: Date): Box[Boolean] = {
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


  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Box[Boolean] = {
    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccountOld(bankId, accountId).map(_.asInstanceOf[MappedBankAccount])
      bank <- getMappedBank(bankId)
    } yield {
      acc.accountLabel(label).save
    }

    Full(result.getOrElse(false))
  }

  override def getProducts(bankId: BankId, params: List[GetProductsParam]): Box[List[Product]] = {
    Box !! {
      if (params.isEmpty) {
        MappedProduct.findAll(By(MappedProduct.mBankId, bankId.value))
      } else {
        val paramList: List[(String, List[String])] = params.map(it => it.name -> it.value)
        val parameters: List[String] = MappedProductAttribute.getParameters(paramList)
        val sqlParametersFilter = MappedProductAttribute.getSqlParametersFilter(paramList)
        val productIdList = paramList.isEmpty match {
          case true =>
            MappedProductAttribute.findAll(
              By(MappedProductAttribute.mBankId, bankId.value)
            ).map(_.productCode.value)
          case false =>
            MappedProductAttribute.findAll(
              By(MappedProductAttribute.mBankId, bankId.value),
              BySql(sqlParametersFilter, IHaveValidatedThisSQL("developer","2020-06-28"), parameters:_*)
            ).map(_.productCode.value)
        }
        MappedProduct.findAll(ByList(MappedProduct.mCode, productIdList))
      }
    }
  }

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = {
    MappedProduct.find(
      By(MappedProduct.mBankId, bankId.value),
      By(MappedProduct.mCode, productCode.value)
    )
  }


  override def createOrUpdateBranch(branch: BranchT): Box[BranchT] = {

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

    val foundBranch: Box[BranchT] = getBranchLegacy(branch.bankId, branch.branchId)

    logger.info("after getting")

    //check the branch existence and update or insert data
    val branchToReturn = foundBranch match {
      case Full(mappedBranch: MappedBranch) =>
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

            .mLobbyOpeningTimeOnMonday(branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnMonday(branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnTuesday(branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnTuesday(branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnWednesday(branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnWednesday(branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnThursday(branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnThursday(branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnFriday(branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnFriday(branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnSaturday(branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnSaturday(branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnSunday(branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnSunday(branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)


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
            .mLobbyOpeningTimeOnMonday(branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnMonday(branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnTuesday(branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnTuesday(branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnWednesday(branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnWednesday(branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnThursday(branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnThursday(branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnFriday(branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnFriday(branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnSaturday(branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnSaturday(branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)

            .mLobbyOpeningTimeOnSunday(branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head)
            .mLobbyClosingTimeOnSunday(branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head)


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

  override def createOrUpdateAtm(atm: AtmT,  callContext: Option[CallContext]): OBPReturnType[Box[AtmT]] = Future{
    (createOrUpdateAtmLegacy(atm), callContext)
  }
  
  override def deleteAtm(atm: AtmT,  callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future {
    (Atms.atmsProvider.vend.deleteAtm(atm), callContext)
  }

  override def getEndpointTagById(endpointTagId : String, callContext: Option[CallContext]) : OBPReturnType[Box[EndpointTagT]] = Future(
    (EndpointTag.find(By(EndpointTag.EndpointTagId, endpointTagId)), callContext)
  )

  override def deleteEndpointTag(endpointTagId : String, callContext: Option[CallContext]) : OBPReturnType[Box[Boolean]] = Future(
    (EndpointTag.find(By(EndpointTag.EndpointTagId, endpointTagId)).map(_.delete_!), callContext)
  )

  override def getSystemLevelEndpointTags(operationId : String, callContext: Option[CallContext]) : OBPReturnType[Box[List[EndpointTagT]]] = Future(
    (tryo{getSystemLevelEndpointTagsBox(operationId : String)}, callContext)
  )

  override def getBankLevelEndpointTags(bankId:String, operationId : String, callContext: Option[CallContext]) : OBPReturnType[Box[List[EndpointTagT]]] = Future(
    (tryo{getBankLevelEndpointTagsBox(bankId:String, operationId : String)}, callContext)
  )

   def getAllEndpointTagsBox(operationId : String) : List[EndpointTagT] = EndpointTag.findAll(
     By(EndpointTag.OperationId, operationId),
     OrderBy(EndpointTag.TagName, Ascending)
   )
  
   def getSystemLevelEndpointTagsBox(operationId : String) : List[EndpointTagT] = EndpointTag.findAll(
     By(EndpointTag.OperationId, operationId),
     OrderBy(EndpointTag.TagName, Ascending)
   ).filter(_.bankId == None)

   def getBankLevelEndpointTagsBox(bankId:String, operationId : String) : List[EndpointTagT] = EndpointTag.findAll(
     By(EndpointTag.BankId, bankId),
     By(EndpointTag.OperationId, operationId),
     OrderBy(EndpointTag.TagName, Ascending)
   )
  
   override def createSystemLevelEndpointTag(operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (
       tryo {
         EndpointTag.create
           .BankId(null)
           .OperationId(operationId)
           .TagName(tagName)
           .saveMe()
       } ?~! CreateEndpointTagError, 
       callContext
     )
  }
  
   override def updateSystemLevelEndpointTag(endpointTagId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (
       EndpointTag.find(
         By(EndpointTag.EndpointTagId, endpointTagId)
       ).map(endpointTag =>
         endpointTag
           .BankId(null)
           .OperationId(operationId)
           .TagName(tagName)
           .saveMe()
       )
       , callContext
     )
  }
   
   override def createBankLevelEndpointTag(bankId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (
       tryo {
         EndpointTag.create
           .BankId(bankId)
           .OperationId(operationId)
           .TagName(tagName)
           .saveMe()
       } ?~! CreateEndpointTagError, 
       callContext
     )
  }
  
   override def updateBankLevelEndpointTag(bankId:String, endpointTagId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (
       EndpointTag.find(
         By(EndpointTag.EndpointTagId, endpointTagId)
       ).map(endpointTag =>
         endpointTag
           .BankId(bankId)
           .OperationId(operationId)
           .TagName(tagName)
           .saveMe()
       )
       , callContext
     )
  }
   
  override def getSystemLevelEndpointTag(operationId: String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (EndpointTag.find(
       By(EndpointTag.OperationId, operationId),
       By(EndpointTag.TagName, tagName),
     ).filter(_.bankId == None), callContext)
  }

  override def getBankLevelEndpointTag(bankId: String, operationId: String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
    (EndpointTag.find(
      By(EndpointTag.OperationId, operationId),
      By(EndpointTag.TagName, tagName),
      By(EndpointTag.TagName, tagName),
    ), callContext)
  }

  

  override def createOrUpdateAtmLegacy(atm: AtmT): Box[AtmT] = {
    Atms.atmsProvider.vend.createOrUpdateAtm(atm)
  }

  override def createOrUpdateProductFee(
    bankId: BankId,
    productCode: ProductCode,
    productFeeId: Option[String],
    name: String,
    isActive: Boolean,
    moreInfo: String,
    currency: String,
    amount: BigDecimal,
    frequency: String,
    `type`: String, 
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ProductFeeTrait]] = {
    ProductFeeX.productFeeProvider.vend.createOrUpdateProductFee(
      bankId: BankId,
      productCode: ProductCode,
      productFeeId: Option[String],
      name: String,
      isActive: Boolean,
      moreInfo: String,
      currency: String,
      amount: BigDecimal,
      frequency: String,
      `type`: String
    ) map {
      (_, callContext)
    }
  }

  override def getProductFeesFromProvider(
    bankId: BankId, 
    productCode: ProductCode,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[ProductFeeTrait]]] = {
    ProductFeeX.productFeeProvider.vend.getProductFeesFromProvider(bankId: BankId, productCode: ProductCode) map {
      (_, callContext)
    }
  }

  override def getProductFeeById(
    productFeeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ProductFeeTrait]] =  {
    ProductFeeX.productFeeProvider.vend.getProductFeeById(productFeeId) map {
      (_, callContext)
    }
  }
  
  override def deleteProductFee(
    productFeeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] =  {
    ProductFeeX.productFeeProvider.vend.deleteProductFee(productFeeId) map {
      (_, callContext)
    }
  }

  override def createOrUpdateProduct(bankId: String,
                                     code: String,
                                     parentProductCode: Option[String],
                                     name: String,
                                     category: String,
                                     family: String,
                                     superFamily: String,
                                     moreInfoUrl: String,
                                     termsAndConditionsUrl: String,
                                     details: String,
                                     description: String,
                                     metaLicenceId: String,
                                     metaLicenceName: String): Box[Product] = {

    //check the product existence and update or insert data
    getProduct(BankId(bankId), ProductCode(code)) match {
      case Full(mappedProduct: MappedProduct) =>
        tryo {
          parentProductCode match {
            case Some(ppc) => mappedProduct.mParentProductCode(ppc)
            case None =>
          }
          mappedProduct.mName(name)
            .mCode(code)
            .mBankId(bankId)
            .mName(name)
            .mCategory(category)
            .mFamily(family)
            .mSuperFamily(superFamily)
            .mMoreInfoUrl(moreInfoUrl)
            .mTermsAndConditionsUrl(termsAndConditionsUrl)
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
            .mCode(code)
            .mBankId(bankId)
            .mName(name)
            .mCategory(category)
            .mFamily(family)
            .mSuperFamily(superFamily)
            .mMoreInfoUrl(moreInfoUrl)
            .mTermsAndConditionsUrl(termsAndConditionsUrl)
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


  override def getBranchLegacy(bankId: BankId, branchId: BranchId): Box[BranchT] = {
    MappedBranch
      .find(
        By(MappedBranch.mBankId, bankId.value),
        By(MappedBranch.mBranchId, branchId.value))
      .map(
        branch =>
          branch.branchRouting.map(_.scheme) == null && branch.branchRouting.map(_.address) == null match {
            case true => branch.mBranchRoutingScheme("OBP").mBranchRoutingAddress(branch.branchId.value)
            case _ => branch
          }
      )
  }

  override def getBranches(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[BranchT], Option[CallContext])]] = {
    Future {
      Full(MappedBranch.findAll(By(MappedBranch.mBankId, bankId.value)), callContext)
    }
  }

  override def getBranch(bankId: BankId, branchId: BranchId, callContext: Option[CallContext]): Future[Box[(BranchT, Option[CallContext])]] = {
    Future {
      getBranchLegacy(bankId, branchId).map(branch => (branch, callContext))
    }
  }

  override def getAtmLegacy(bankId: BankId, atmId: AtmId): Box[AtmT] = {
    MappedAtm
      .find(
        By(MappedAtm.mBankId, bankId.value),
        By(MappedAtm.mAtmId, atmId.value))
  }

  override def getAtm(bankId: BankId, atmId: AtmId, callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      getAtmLegacy(bankId, atmId).map(atm => (atm, callContext))
    }

  override def updateAtmSupportedLanguages(bankId: BankId, atmId: AtmId, supportedLanguages: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      val supportedLanguagesString = supportedLanguages.mkString(",")
      MappedAtm
        .find(
          By(MappedAtm.mBankId, bankId.value),
          By(MappedAtm.mAtmId, atmId.value)).map(_.mSupportedLanguages(supportedLanguagesString).saveMe()).map(atm => (atm, callContext))
    }

  override def updateAtmSupportedCurrencies(bankId: BankId, atmId: AtmId, supportedCurrencies: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      val supportedCurrenciesString = supportedCurrencies.mkString(",")
      MappedAtm
        .find(
          By(MappedAtm.mBankId, bankId.value),
          By(MappedAtm.mAtmId, atmId.value)).map(_.mSupportedCurrencies(supportedCurrenciesString).saveMe()).map(atm => (atm, callContext))
    }


  override def updateAtmAccessibilityFeatures(bankId: BankId, atmId: AtmId, accessibilityFeatures: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      val accessibilityFeaturesString = accessibilityFeatures.mkString(",")
      MappedAtm
        .find(
          By(MappedAtm.mBankId, bankId.value),
          By(MappedAtm.mAtmId, atmId.value)).map(_.mAccessibilityFeatures(accessibilityFeaturesString).saveMe()).map(atm => (atm, callContext))
    }

  override def updateAtmServices(bankId: BankId, atmId: AtmId, services: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      val servicesString = services.mkString(",")
      MappedAtm
        .find(
          By(MappedAtm.mBankId, bankId.value),
          By(MappedAtm.mAtmId, atmId.value)).map(_.mServices(servicesString).saveMe()).map(atm => (atm, callContext))
    }

  override def updateAtmNotes(bankId: BankId, atmId: AtmId, notes: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      val notesString = notes.mkString(",")
      MappedAtm
        .find(
          By(MappedAtm.mBankId, bankId.value),
          By(MappedAtm.mAtmId, atmId.value)).map(_.mNotes(notesString).saveMe()).map(atm => (atm, callContext))
    }

  override def updateAtmLocationCategories(bankId: BankId, atmId: AtmId, locationCategories: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      val locationCategoriesString = locationCategories.mkString(",")
      MappedAtm
        .find(
          By(MappedAtm.mBankId, bankId.value),
          By(MappedAtm.mAtmId, atmId.value)).map(_.mLocationCategories(locationCategoriesString).saveMe()).map(atm => (atm, callContext))
    }

  override def getAtms(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[AtmT], Option[CallContext])]] = {
    Future {
      Full(MappedAtm.findAll(By(MappedAtm.mBankId, bankId.value)), callContext)
    }
  }

  override def getAllAtms(callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[AtmT], Option[CallContext])]] = {
    Future {
      Full(MappedAtm.findAll(), callContext)
    }
  }

  /**
    * get the latest record from FXRate table by the fields: fromCurrencyCode and toCurrencyCode.
    * If it is not found by (fromCurrencyCode, toCurrencyCode) order, it will try (toCurrencyCode, fromCurrencyCode) order .
    */
  override def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = {
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
        val fromAccountCurrency: String = getBankAccountOld(bankId, accountId).openOrThrowException(attemptedToOpenAnEmptyBox).currency
        TransactionRequestTypeChargeMock(transactionRequestType.value, bankId.value, fromAccountCurrency, "0.00", "Warning! Default value!")
    }

    Full(transactionRequestTypeCharge)
  }

  override def getCounterpartiesLegacy(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): Box[(List[CounterpartyTrait], Option[CallContext])] = {
    Counterparties.counterparties.vend.getCounterparties(thisBankId, thisAccountId, viewId).map(counterparties => (counterparties, callContext))
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
                                 ): Box[Bank] = {
  //check the bank existence and update or insert data
    val bank = getMappedBank(BankId(bankId)) match {
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

    // Insert the default settlement accounts if they doesn't exist
    MappedBankAccount.find(By(MappedBankAccount.bank, bankId), By(MappedBankAccount.theAccountId, INCOMING_SETTLEMENT_ACCOUNT_ID)) match {
      case Full(_) =>
        logger.debug(s"BankAccount(${bankId}, $INCOMING_SETTLEMENT_ACCOUNT_ID) is found.")
      case _ =>
        MappedBankAccount.create
          .bank(bankId)
          .theAccountId(INCOMING_SETTLEMENT_ACCOUNT_ID)
          .accountCurrency("EUR")
          .kind("SETTLEMENT")
          .holder(fullBankName)// TODO Consider to use the table MapperAccountHolder 
          .accountName("Default incoming settlement account")
          .accountLabel("Settlement account: Do not delete!")
          .saveMe()
        logger.debug(s"creating BankAccount(${bankId}, $INCOMING_SETTLEMENT_ACCOUNT_ID).")
    }

    MappedBankAccount.find(By(MappedBankAccount.bank, bankId), By(MappedBankAccount.theAccountId, OUTGOING_SETTLEMENT_ACCOUNT_ID)) match {
      case Full(_) =>
        logger.debug(s"BankAccount(${bankId}, $OUTGOING_SETTLEMENT_ACCOUNT_ID) is found.")
      case _ =>
        MappedBankAccount.create
          .bank(bankId)
          .theAccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID)
          .accountCurrency("EUR")
          .kind("SETTLEMENT")
          .holder(fullBankName)
          .accountName("Default outgoing settlement account")
          .accountLabel("Settlement account: Do not delete!")
          .saveMe()
        logger.debug(s"creating BankAccount(${bankId}, $OUTGOING_SETTLEMENT_ACCOUNT_ID).")
    }

    bank
  }

  override def createCounterparty(
                                   name: String,
                                   description: String,
                                   currency: String,
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
                                   isBeneficiary: Boolean,
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
      currency = currency,
      bespoke = bespoke
    ).map(counterparty => (counterparty, callContext))

  override def checkCounterpartyExists(
    name: String,
    thisBankId: String,
    thisAccountId: String,
    thisViewId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CounterpartyTrait]] = Future{
    (Counterparties.counterparties.vend.checkCounterpartyExists(
      name: String,
      thisBankId: String,
      thisAccountId: String,
      thisViewId: String),callContext)
  }
    
  
  
  override def checkCustomerNumberAvailable(
                                             bankId: BankId,
                                             customerNumber: String,
                                             callContext: Option[CallContext]
                                           ): OBPReturnType[Box[Boolean]] = Future {
    (tryo {
      CustomerX.customerProvider.vend.checkCustomerNumberAvailable(bankId, customerNumber)
    }, callContext)
  }


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
                             ): OBPReturnType[Box[Customer]] = Future {
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
    ), callContext)
  }

  override def createCustomerC2(
                                 bankId: BankId,
                                 legalName: String,
                                 customerNumber: String,
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
                               ): OBPReturnType[Box[Customer]] = Future {
    (CustomerX.customerProvider.vend.addCustomer(
      bankId,
      customerNumber,
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
    ), callContext)
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

  override def getCustomersByUserId(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer], Option[CallContext])]] =
    CustomerX.customerProvider.vend.getCustomersByUserIdFuture(userId) map {
      customersBox => (customersBox.map(customers => (customers, callContext)))
    }

  override def getCustomerByCustomerIdLegacy(customerId: String, callContext: Option[CallContext]) =
    CustomerX.customerProvider.vend.getCustomerByCustomerId(customerId) map {
      customersBox => (customersBox, callContext)
    }

  override def getCustomerByCustomerId(customerId: String, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] =
    CustomerX.customerProvider.vend.getCustomerByCustomerIdFuture(customerId) map {
      i =>
        i.map(
          customer => (customer, callContext)
        )
    }

  override def getCustomerByCustomerNumber(customerNumber: String, bankId: BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] =
    CustomerX.customerProvider.vend.getCustomerByCustomerNumberFuture(customerNumber, bankId) map {
      i =>
        i.map(
          customer => (customer, callContext)
        )
    }

  override def getCustomersAtAllBanks(callContext: Option[CallContext], queryParams: List[OBPQueryParam]): OBPReturnType[Box[List[Customer]]] =
    CustomerX.customerProvider.vend.getCustomersAtAllBanks(queryParams) map {
      (_, callContext)
    }
  
  override def getCustomers(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] =
    CustomerX.customerProvider.vend.getCustomersFuture(bankId, queryParams)map {
      (_, callContext)
    }

  override def getCustomersByCustomerPhoneNumber(bankId: BankId, phoneNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[List[Customer]]] =
    CustomerX.customerProvider.vend.getCustomersByCustomerPhoneNumber(bankId, phoneNumber) map {
      (_, callContext)
    }

  override def getCustomerAddress(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAddress]]] =
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

  override def deleteCustomerAddress(customerAddressId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    CustomerAddressX.address.vend.deleteAddress(customerAddressId) map {
      (_, callContext)
    }

  override def getTaxResidence(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[TaxResidence]]] =
    TaxResidenceX.taxResidence.vend.getTaxResidence(customerId) map {
      (_, callContext)
    }

  override def createTaxResidence(customerId: String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[TaxResidence]] =
    TaxResidenceX.taxResidence.vend.createTaxResidence(customerId, domain, taxNumber) map {
      (_, callContext)
    }

  override def deleteTaxResidence(taxResidenceId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    TaxResidenceX.taxResidence.vend.deleteTaxResidence(taxResidenceId) map {
      (_, callContext)
    }

  override def getCheckbookOrders(
                                   bankId: String,
                                   accountId: String,
                                   callContext: Option[CallContext]
                                 ): Future[Box[(CheckbookOrdersJson, Option[CallContext])]] = Future {
    Full(SwaggerDefinitionsJSON.checkbookOrdersJson, callContext)
  }


  override def getStatusOfCreditCardOrder(
                                           bankId: String,
                                           accountId: String,
                                           callContext: Option[CallContext]
                                         ): Future[Box[(List[CardObjectJson], Option[CallContext])]] = Future {
    Full(List(SwaggerDefinitionsJSON.cardObjectJson), callContext)
  }


  override def createUserAuthContext(userId: String,
                                     key: String,
                                     value: String,
                                     callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContext]] = {
    val consumerId = callContext.map(_.consumer.map(_.consumerId.get).getOrElse("")).getOrElse("")
    UserAuthContextProvider.userAuthContextProvider.vend.createUserAuthContext(userId, key, value, consumerId) map {
      (_, callContext)
    }
  }

  override def createUserAuthContextUpdate(userId: String,
                                           key: String,
                                           value: String,
                                           callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContextUpdate]] = {
    val consumerId = callContext.map(_.consumer.map(_.consumerId.get).getOrElse("")).getOrElse("")
    UserAuthContextUpdateProvider.userAuthContextUpdateProvider.vend.createUserAuthContextUpdates(userId,consumerId, key, value) map {
      (_, callContext)
    }
  }

  override def getUserAuthContexts(userId: String,
                                   callContext: Option[CallContext]): OBPReturnType[Box[List[UserAuthContext]]] =
    UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContexts(userId) map {
      (_, callContext)
    }

  override def deleteUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    UserAuthContextProvider.userAuthContextProvider.vend.deleteUserAuthContexts(userId) map {
      (_, callContext)
    }

  override def deleteUserAuthContextById(userAuthContextId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    UserAuthContextProvider.userAuthContextProvider.vend.deleteUserAuthContextById(userAuthContextId) map {
      (_, callContext)
    }


  override def createOrUpdateProductAttribute(
                                               bankId: BankId,
                                               productCode: ProductCode,
                                               productAttributeId: Option[String],
                                               name: String,
                                               attributeType: ProductAttributeType.Value,
                                               value: String,
                                               isActive: Option[Boolean],
                                               callContext: Option[CallContext]
                                             ): OBPReturnType[Box[ProductAttribute]] =
    ProductAttributeX.productAttributeProvider.vend.createOrUpdateProductAttribute(
      bankId: BankId,
      productCode: ProductCode,
      productAttributeId: Option[String],
      name: String,
      attributeType: ProductAttributeType.Value,
      value: String, isActive: Option[Boolean]) map {
      (_, callContext)
    }  
  override def createOrUpdateBankAttribute(bankId: BankId,
                                           bankAttributeId: Option[String],
                                           name: String,
                                           bankAttributeType: BankAttributeType.Value,
                                           value: String,
                                           isActive: Option[Boolean],
                                           callContext: Option[CallContext]
                                          ): OBPReturnType[Box[BankAttribute]] =
    BankAttributeX.bankAttributeProvider.vend.createOrUpdateBankAttribute(
      bankId: BankId,
      bankAttributeId: Option[String],
      name: String,
      bankAttributeType: BankAttributeType.Value,
      value: String, isActive: Option[Boolean]) map {
      (_, callContext)
    }


  override def getBankAttributesByBank(bank: BankId, callContext: Option[CallContext]): OBPReturnType[Box[List[BankAttribute]]] =
    BankAttributeX.bankAttributeProvider.vend.getBankAttributesFromProvider(bank: BankId) map {
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

  override def getBankAttributeById(bankAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAttribute]] =
    BankAttributeX.bankAttributeProvider.vend.getBankAttributeById(bankAttributeId: String) map {
      (_, callContext)
    }
  
  override def getProductAttributeById(
                                        productAttributeId: String,
                                        callContext: Option[CallContext]
                                      ): OBPReturnType[Box[ProductAttribute]] =
    ProductAttributeX.productAttributeProvider.vend.getProductAttributeById(productAttributeId: String) map {
      (_, callContext)
    }

  override def deleteBankAttribute(bankAttributeId: String, 
                                   callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    BankAttributeX.bankAttributeProvider.vend.deleteBankAttribute(bankAttributeId: String) map {
      (_, callContext)
    }
  
  override def deleteProductAttribute(
                                       productAttributeId: String,
                                       callContext: Option[CallContext]
                                     ): OBPReturnType[Box[Boolean]] =
    ProductAttributeX.productAttributeProvider.vend.deleteProductAttribute(productAttributeId: String) map {
      (_, callContext)
    }

  override def getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountAttribute]] =
    AccountAttributeX.accountAttributeProvider.vend.getAccountAttributeById(accountAttributeId: String) map {
      (_, callContext)
    }

  override def getTransactionAttributeById(transactionAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionAttribute]] =
    TransactionAttributeX.transactionAttributeProvider.vend.getTransactionAttributeById(transactionAttributeId: String) map {
      (_, callContext)
    }


  override def createOrUpdateAccountAttribute(
                                               bankId: BankId,
                                               accountId: AccountId,
                                               productCode: ProductCode,
                                               accountAttributeId: Option[String],
                                               name: String,
                                               attributeType: AccountAttributeType.Value,
                                               value: String,
                                               productInstanceCode: Option[String],
                                               callContext: Option[CallContext]
                                             ): OBPReturnType[Box[AccountAttribute]] = {
    AccountAttributeX.accountAttributeProvider.vend.createOrUpdateAccountAttribute(bankId: BankId,
      accountId: AccountId,
      productCode: ProductCode,
      accountAttributeId: Option[String],
      name: String,
      attributeType: AccountAttributeType.Value,
      value: String,
      productInstanceCode: Option[String]) map {
      (_, callContext)
    }
  }

  override def createAccountAttributes(bankId: BankId,
                                       accountId: AccountId,
                                       productCode: ProductCode,
                                       accountAttributes: List[ProductAttribute],
                                       productInstanceCode: Option[String],
                                       callContext: Option[CallContext]
                                      ): OBPReturnType[Box[List[AccountAttribute]]] = {
    AccountAttributeX.accountAttributeProvider.vend.createAccountAttributes(
      bankId: BankId,
      accountId: AccountId,
      productCode: ProductCode,
      accountAttributes: List[ProductAttribute],
      productInstanceCode: Option[String]) map {
      (_, callContext)
    }
  }

  override def getAccountAttributesByAccount(bankId: BankId,
                                             accountId: AccountId,
                                             callContext: Option[CallContext]
                                            ): OBPReturnType[Box[List[AccountAttribute]]] = {
    AccountAttributeX.accountAttributeProvider.vend.getAccountAttributesByAccount(
      bankId: BankId,
      accountId: AccountId) map {
      (_, callContext)
    }
  }
  override def getAccountAttributesByAccountCanBeSeenOnView(bankId: BankId, 
                                                            accountId: AccountId,
                                                            viewId: ViewId, 
                                                            callContext: Option[CallContext]
                                                           ): OBPReturnType[Box[List[AccountAttribute]]] = {
    AccountAttributeX.accountAttributeProvider.vend.getAccountAttributesByAccountCanBeSeenOnView(
      bankId: BankId,
      accountId: AccountId,
      viewId) map {
      (_, callContext)
    }
  }
  override def getAccountAttributesByAccountsCanBeSeenOnView(accounts: List[BankIdAccountId],
                                                             viewId: ViewId,
                                                             callContext: Option[CallContext]
                                                            ): OBPReturnType[Box[List[AccountAttribute]]] = {
    AccountAttributeX.accountAttributeProvider.vend.getAccountAttributesByAccountsCanBeSeenOnView(
      accounts,
      viewId) map {
      (_, callContext)
    }
  }
  override def getTransactionAttributesByTransactionsCanBeSeenOnView(bankId: BankId,
                                                                     transactionIds: List[TransactionId],
                                                                     viewId: ViewId,
                                                                     callContext: Option[CallContext]
                                                                    ): OBPReturnType[Box[List[TransactionAttribute]]] = {
    TransactionAttributeX.transactionAttributeProvider.vend.getTransactionsAttributesCanBeSeenOnView(
      bankId,
      transactionIds,
      viewId) map {
      (_, callContext)
    }
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
    ) map {
      (_, callContext)
    }
  }

  override def getUserAttributes(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[UserAttribute]]] = {
    UserAttributeProvider.userAttributeProvider.vend.getUserAttributesByUser(userId: String) map {(_, callContext)}
  }
  override def getUserAttributesByUsers(userIds: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[UserAttribute]]] = {
    UserAttributeProvider.userAttributeProvider.vend.getUserAttributesByUsers(userIds) map {(_, callContext)}
  }
  override def createOrUpdateUserAttribute(
                                            userId: String,
                                            userAttributeId: Option[String],
                                            name: String,
                                            attributeType: UserAttributeType.Value,
                                            value: String,
                                            callContext: Option[CallContext]
                                          ): OBPReturnType[Box[UserAttribute]] = {
    UserAttributeProvider.userAttributeProvider.vend.createOrUpdateUserAttribute(
      userId: String,
      userAttributeId: Option[String],
      name: String,
      attributeType: UserAttributeType.Value,
      value: String
    ) map {
      (_, callContext)
    }
  }
  
  override def createOrUpdateTransactionAttribute(
                                                   bankId: BankId,
                                                   transactionId: TransactionId,
                                                   transactionAttributeId: Option[String],
                                                   name: String,
                                                   attributeType: TransactionAttributeType.Value,
                                                   value: String,
                                                   callContext: Option[CallContext]
                                                 ): OBPReturnType[Box[TransactionAttribute]] = {
    TransactionAttributeX.transactionAttributeProvider.vend.createOrUpdateTransactionAttribute(
      bankId: BankId,
      transactionId: TransactionId,
      transactionAttributeId: Option[String],
      name: String,
      attributeType: TransactionAttributeType.Value,
      value: String
    ) map {
      (_, callContext)
    }
  }

  override def createOrUpdateAttributeDefinition(bankId: BankId,
                                                 name: String,
                                                 category: AttributeCategory.Value,
                                                 `type`: AttributeType.Value,
                                                 description: String,
                                                 alias: String,
                                                 canBeSeenOnViews: List[String],
                                                 isActive: Boolean,
                                                 callContext: Option[CallContext]
                                                ): OBPReturnType[Box[AttributeDefinition]] = {
    AttributeDefinitionDI.attributeDefinition.vend.createOrUpdateAttributeDefinition(
      bankId: BankId,
      name: String,
      category: AttributeCategory.Value,
      `type`: AttributeType.Value,
      description: String,
      alias: String,
      canBeSeenOnViews: List[String],
      isActive: Boolean
    ) map {
      (_, callContext)
    }
  }

  override def deleteAttributeDefinition(attributeDefinitionId: String,
                                            category: AttributeCategory.Value,
                                            callContext: Option[CallContext]
                                           ): OBPReturnType[Box[Boolean]] = {
    AttributeDefinitionDI.attributeDefinition.vend.deleteAttributeDefinition(
      attributeDefinitionId: String,
      category: AttributeCategory.Value
    ) map {
      (_, callContext)
    }
  }

  override def getAttributeDefinition(category: AttributeCategory.Value,
                                         callContext: Option[CallContext]
                                        ): OBPReturnType[Box[List[AttributeDefinition]]] = {
    AttributeDefinitionDI.attributeDefinition.vend.getAttributeDefinition(
      category: AttributeCategory.Value
    ) map {
      (_, callContext)
    }
  }


  override def getCustomerAttributes(bankId: BankId,
                                     customerId: CustomerId,
                                     callContext: Option[CallContext]
                                    ): OBPReturnType[Box[List[CustomerAttribute]]] = {
    CustomerAttributeX.customerAttributeProvider.vend.getCustomerAttributes(
      bankId: BankId,
      customerId: CustomerId) map {
      (_, callContext)
    }
  }

  override def getCustomerIdsByAttributeNameValues(
                                                    bankId: BankId,
                                                    nameValues: Map[String, List[String]],
                                                    callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] = {

    CustomerAttributeX.customerAttributeProvider.vend.getCustomerIdsByAttributeNameValues(bankId, nameValues) map {
      (_, callContext)
    }
  }


  override def getCustomerAttributesForCustomers(
                                                  customers: List[Customer],
                                                  callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAndAttribute]]] = {
    CustomerAttributeX.customerAttributeProvider.vend.getCustomerAttributesForCustomers(customers: List[Customer]) map {
      case Full(list) =>
        val customerAndAttributes: List[CustomerAndAttribute] = list.map(it => CustomerAndAttribute(it.customer, it.attributes))
        (Full(customerAndAttributes), callContext)
      case x => (x.asInstanceOf[Box[List[CustomerAndAttribute]]], callContext)
    }
  }

  override def getTransactionIdsByAttributeNameValues(
                                                       bankId: BankId,
                                                       nameValues: Map[String, List[String]],
                                                       callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] =
    TransactionAttributeX.transactionAttributeProvider.vend.getTransactionIdsByAttributeNameValues(bankId, nameValues) map {
      (_, callContext)
    }

  override def getTransactionAttributes(
                                         bankId: BankId,
                                         transactionId: TransactionId,
                                         callContext: Option[CallContext]
                                       ): OBPReturnType[Box[List[TransactionAttribute]]] = {
    TransactionAttributeX.transactionAttributeProvider.vend.getTransactionAttributes(
      bankId: BankId,
      transactionId: TransactionId) map {
      (_, callContext)
    }
  }
  override def getTransactionAttributesCanBeSeenOnView(bankId: BankId,
                                                       transactionId: TransactionId,
                                                       viewId: ViewId,
                                                       callContext: Option[CallContext]
                                       ): OBPReturnType[Box[List[TransactionAttribute]]] = {
    TransactionAttributeX.transactionAttributeProvider.vend.getTransactionAttributesCanBeSeenOnView(
      bankId: BankId,
      transactionId: TransactionId,
      viewId) map {
      (_, callContext)
    }
  }

  override def getCustomerAttributeById(
                                         customerAttributeId: String,
                                         callContext: Option[CallContext]
                                       ): OBPReturnType[Box[CustomerAttribute]] = {
    CustomerAttributeX.customerAttributeProvider.vend.getCustomerAttributeById(customerAttributeId: String) map {
      (_, callContext)
    }
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

  override def updateAccountApplicationStatus(accountApplicationId: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    AccountApplicationX.accountApplication.vend.updateStatus(accountApplicationId, status) map {
      (_, callContext)
    }

  override def getOrCreateProductCollection(collectionCode: String, productCodes: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    ProductCollectionX.productCollection.vend.getOrCreateProductCollection(collectionCode, productCodes) map {
      (_, callContext)
    }

  override def getProductCollection(collectionCode: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    ProductCollectionX.productCollection.vend.getProductCollection(collectionCode) map {
      (_, callContext)
    }

  override def getOrCreateProductCollectionItem(collectionCode: String,
                                                memberProductCodes: List[String],
                                                callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    ProductCollectionItems.productCollectionItem.vend.getOrCreateProductCollectionItem(collectionCode, memberProductCodes) map {
      (_, callContext)
    }

  override def getProductCollectionItem(collectionCode: String,
                                        callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    ProductCollectionItems.productCollectionItem.vend.getProductCollectionItems(collectionCode) map {
      pci => (pci, callContext)
    }

  override def getProductCollectionItemsTree(collectionCode: String,
                                             bankId: String,
                                             callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItemsTree]]] =
    ProductCollectionItems.productCollectionItem.vend.getProductCollectionItemsTree(collectionCode, bankId) map { it =>
      val data: Box[List[ProductCollectionItemsTree]] = it.map(boxValue => boxValue.map(it => ProductCollectionItemsTree(it._1, it._2, it._3)))
      (data, callContext)
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
    Future {
      (
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
        ), callContext)
    }

  override def getMeetings(
                            bankId: BankId,
                            user: User,
                            callContext: Option[CallContext]
                          ): OBPReturnType[Box[List[Meeting]]] =
    Future {
      (
        Meetings.meetingProvider.vend.getMeetings(
          bankId: BankId,
          user: User),
        callContext)
    }

  override def getMeeting(
                           bankId: BankId,
                           user: User,
                           meetingId: String,
                           callContext: Option[CallContext]
                         ): OBPReturnType[Box[Meeting]] =
    Future {
      (
        Meetings.meetingProvider.vend.getMeeting(
          bankId: BankId,
          user: User,
          meetingId: String),
        callContext)
    }

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
    val boxedData = Box !! KycDocuments.kycDocumentProvider.vend.getKycDocuments(customerId)
    (boxedData, callContext)
  }

  override def getKycMedias(customerId: String,
                            callContext: Option[CallContext]
                           ): OBPReturnType[Box[List[KycMedia]]] = Future {
    val boxedData = Box !! KycMedias.kycMediaProvider.vend.getKycMedias(customerId)
    (boxedData, callContext)
  }

  override def getKycStatuses(customerId: String,
                              callContext: Option[CallContext]
                             ): OBPReturnType[Box[List[KycStatus]]] = Future {
    val boxedData = Box !! KycStatuses.kycStatusProvider.vend.getKycStatuses(customerId)
    (boxedData, callContext)
  }

  override def createMessage(user: User,
                             bankId: BankId,
                             message: String,
                             fromDepartment: String,
                             fromPerson: String,
                             callContext: Option[CallContext]): OBPReturnType[Box[CustomerMessage]] = Future {
    val boxedData = Box !! CustomerMessages.customerMessageProvider.vend.addMessage(user, bankId, message, fromDepartment, fromPerson)
    (boxedData, callContext)
  }

  override def createCustomerMessage(
    customer: Customer,
    bankId : BankId,
    transport: String,
    message : String,
    fromDepartment : String,
    fromPerson : String,
    callContext: Option[CallContext]) : OBPReturnType[Box[CustomerMessage]] = Future{
    val boxedData = Box !! CustomerMessages.customerMessageProvider.vend.createCustomerMessage(customer, bankId, transport, message, fromDepartment, fromPerson)
    (boxedData, callContext)
  }

  override def getCustomerMessages(
    customer: Customer,
    bankId: BankId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[CustomerMessage]]] = Future{
    val boxedData = Box !! CustomerMessages.customerMessageProvider.vend.getCustomerMessages(customer, bankId)
    (boxedData, callContext)
  }

  override def dynamicEntityProcess(operation: DynamicEntityOperation,
                                    entityName: String,
                                    requestBody: Option[JObject],
                                    entityId: Option[String],
                                    bankId: Option[String], 
                                    queryParameters: Option[Map[String, List[String]]],
                                    userId: Option[String],
                                    isPersonalEntity: Boolean,
                                    callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {

    Future {
      val processResult: Box[JValue] = operation.asInstanceOf[Any] match {
        case GET_ALL => Full {
          val dataList = DynamicDataProvider.connectorMethodProvider.vend.getAllDataJson(bankId, entityName, userId, isPersonalEntity)
          JArray(dataList)
        }
        case GET_ONE => {
          val boxedEntity: Box[JValue] = DynamicDataProvider.connectorMethodProvider.vend
            .get(bankId, entityName, entityId.getOrElse(throw new RuntimeException(s"$DynamicEntityMissArgument the entityId is required.")),userId, isPersonalEntity)
            .map(it => json.parse(it.dataJson))
          boxedEntity
        }
        case CREATE => {
          val body = requestBody.getOrElse(throw new RuntimeException(s"$DynamicEntityMissArgument please supply the requestBody."))
          val boxedEntity: Box[JValue] = DynamicDataProvider.connectorMethodProvider.vend.save(bankId, entityName, body, userId, isPersonalEntity)
            .map(it => json.parse(it.dataJson))
          boxedEntity
        }
        case UPDATE => {
          val body = requestBody.getOrElse(throw new RuntimeException(s"$DynamicEntityMissArgument please supply the requestBody."))
          val boxedEntity: Box[JValue] = DynamicDataProvider.connectorMethodProvider.vend.update(bankId, entityName, body, entityId.get, userId, isPersonalEntity)
            .map(it => json.parse(it.dataJson))
          boxedEntity
        }
        case DELETE => {
          val id = entityId.getOrElse(throw new RuntimeException(s"$DynamicEntityMissArgument the entityId is required. "))
          val boxedEntity: Box[JValue] = DynamicDataProvider.connectorMethodProvider.vend.delete(bankId, entityName, id, userId, isPersonalEntity)
              .map(it => JBool(it))
          boxedEntity
        }
      }
      (processResult, callContext)
    }
  }

  /* delegate to rest connector
   */
  override def dynamicEndpointProcess(url: String, jValue: JValue, method: HttpMethod, params: Map[String, List[String]], pathParams: Map[String, String],
                                      callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {
    Connector.getConnectorInstance("rest_vMar2019").dynamicEndpointProcess(url,jValue, method, params, pathParams, callContext)
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

  override def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyId: String): Box[Counterparty] = {
    val transactions = getTransactionsLegacy(bankId, accountId, None).map(_._1).toList.flatten
    val counterparties = for {
      transaction <- transactions
      counterpartyName <- List(transaction.otherAccount.counterpartyName)
      otherAccountRoutingScheme <- List(transaction.otherAccount.otherAccountRoutingScheme)
      otherAccountRoutingAddress <- List(transaction.otherAccount.otherAccountRoutingAddress.get)
      counterpartyIdFromTransaction <- List(APIUtil.createImplicitCounterpartyId(bankId.value, accountId.value, counterpartyName, otherAccountRoutingScheme, otherAccountRoutingAddress))
      if counterpartyIdFromTransaction == counterpartyId
    } yield {
      transaction.otherAccount
    }

    counterparties match {
      case List() => Empty
      case x :: xs => Full(x) //Because they have the same counterpartId, so they are actually just one counterparty.
    }
  }

  override def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId): Box[List[Counterparty]] = {
    val counterparties = getTransactionsLegacy(bankId, accountId, None).map(_._1).toList.flatten.map(_.otherAccount)
    Full(counterparties.toSet.toList) //there are many transactions share the same Counterparty, so we need filter the same ones.
  }

  //This is old one, no callContext there. only for old style endpoints.
  override def getBankAccountOld(bankId: BankId, accountId: AccountId): Box[BankAccount] = {
    getBankAccountLegacy(bankId, accountId, None).map(_._1)
  }


  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): OBPReturnType[Box[List[Transaction]]] = {
    val result: Box[(List[Transaction], Option[CallContext])] = getTransactionsLegacy(bankId, accountId, callContext, queryParams)
    Future(result.map(_._1), result.map(_._2).getOrElse(callContext))
  }


  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext] = None): OBPReturnType[Box[Transaction]] = {
    val result: Box[(Transaction, Option[CallContext])] = getTransactionLegacy(bankId, accountId, transactionId, callContext)
    Future(result.map(_._1), result.map(_._2).getOrElse(callContext))
  }

  //Payments api: just return Failure("not supported") from makePaymentImpl if you don't want to implement it
  /**
    * \
    *
    * @param initiator      The user attempting to make the payment
    * @param fromAccountUID The unique identifier of the account sending money
    * @param toAccountUID   The unique identifier of the account receiving money
    * @param amt            The amount of money to send ( > 0 )
    * @return The id of the sender's new transaction,
    */
  override def makePayment(initiator: User, fromAccountUID: BankIdAccountId, toAccountUID: BankIdAccountId,
                           amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType): Box[TransactionId] = {
    for {
      fromAccount <- getBankAccountOld(fromAccountUID.bankId, fromAccountUID.accountId) ?~
        s"$BankAccountNotFound  Account ${fromAccountUID.accountId} not found at bank ${fromAccountUID.bankId}"
      isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId, fromAccount.accountId)), UserNoOwnerView)
      toAccount <- getBankAccountOld(toAccountUID.bankId, toAccountUID.accountId) ?~
        s"$BankAccountNotFound Account ${toAccountUID.accountId} not found at bank ${toAccountUID.bankId}"
      sameCurrency <- booleanToBox(fromAccount.currency == toAccount.currency, {
        s"$InvalidTransactionRequestCurrency, Cannot send payment to account with different currency (From ${fromAccount.currency} to ${toAccount.currency}"
      })
      isPositiveAmtToSend <- booleanToBox(amt > BigDecimal("0"), s"$NotPositiveAmount Can't send a payment with a value of 0 or less. ($amt)")
      //TODO: verify the amount fits with the currency -> e.g. 12.543 EUR not allowed, 10.00 JPY not allowed, 12.53 EUR allowed
      // Note for 'new MappedCounterparty()' in the following :
      // We update the makePaymentImpl in V210, added the new parameter 'toCounterparty: CounterpartyTrait' for V210
      // But in V200 or before, we do not used the new parameter toCounterparty. So just keep it empty.
      transactionId <- makePaymentImpl(fromAccount,
        toAccount,
        transactionRequestCommonBody = null, //Note transactionRequestCommonBody started to use  in V210
        amt,
        description,
        transactionRequestType,
        "") //Note chargePolicy started to use  in V210
    } yield transactionId
  }

  /**
    * \
    *
    * @param fromAccount            The unique identifier of the account sending money
    * @param toAccount              The unique identifier of the account receiving money
    * @param amount                 The amount of money to send ( > 0 )
    * @param transactionRequestType user input: SEPA, SANDBOX_TAN, FREE_FORM, COUNTERPARTY
    * @return The id of the sender's new transaction,
    */
  override def makePaymentv200(fromAccount: BankAccount,
                               toAccount: BankAccount,
                               transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                               amount: BigDecimal,
                               description: String,
                               transactionRequestType: TransactionRequestType,
                               chargePolicy: String): Box[TransactionId] = {
    for {
      transactionId <- makePaymentImpl(fromAccount, toAccount, transactionRequestCommonBody, amount, description, transactionRequestType, chargePolicy) ?~! InvalidConnectorResponseForMakePayment
    } yield transactionId
  }

  // This is used for 1.4.0 See createTransactionRequestv200 for 2.0.0
  override def createTransactionRequest(initiator: User, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody): Box[TransactionRequest] = {
    //set initial status
    //for sandbox / testing: depending on amount, we ask for challenge or not
    val status =
    if (transactionRequestType.value == TransactionRequestTypes.SANDBOX_TAN.toString && BigDecimal(body.value.amount) < 100) {
      TransactionRequestStatus.COMPLETED
    } else {
      TransactionRequestStatus.INITIATED
    }

    //create a new transaction request
    val request = for {
      fromAccountType <- getBankAccountOld(fromAccount.bankId, fromAccount.accountId) ?~
        s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId, fromAccount.accountId)), UserNoOwnerView)
      toAccountType <- getBankAccountOld(toAccount.bankId, toAccount.accountId) ?~
        s"account ${toAccount.accountId} not found at bank ${toAccount.bankId}"
      rawAmt <- tryo {
        BigDecimal(body.value.amount)
      } ?~! s"amount ${body.value.amount} not convertible to number"
      sameCurrency <- booleanToBox(fromAccount.currency == toAccount.currency, {
        s"Cannot send payment to account with different currency (From ${fromAccount.currency} to ${toAccount.currency}"
      })
      isPositiveAmtToSend <- booleanToBox(rawAmt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. (${rawAmt})")
      // Version 200 below has more support for charge
      charge = TransactionRequestCharge("Charge for completed transaction", AmountOfMoney(body.value.currency, "0.00"))
      transactionRequest <- createTransactionRequestImpl(TransactionRequestId(generateUUID()), transactionRequestType, fromAccount, toAccount, body, status.toString, charge)
    } yield transactionRequest

    //make sure we get something back
    var result = request.openOrThrowException("Exception: Couldn't create transactionRequest")

    //if no challenge necessary, create transaction immediately and put in data store and object to return
    if (status == TransactionRequestStatus.COMPLETED) {
      val createdTransactionId = Connector.connector.vend.makePayment(initiator, BankIdAccountId(fromAccount.bankId, fromAccount.accountId),
        BankIdAccountId(toAccount.bankId, toAccount.accountId), BigDecimal(body.value.amount), body.description, transactionRequestType)

      //set challenge to null
      result = result.copy(challenge = null)

      //save transaction_id if we have one
      createdTransactionId match {
        case Full(ti) => {
          if (!createdTransactionId.isEmpty) {
            saveTransactionRequestTransaction(result.id, ti)
            result = result.copy(transaction_ids = ti.value)
          }
        }
        case _ => None
      }
    } else {
      //if challenge necessary, create a new one
      val challenge = TransactionRequestChallenge(id = generateUUID(), allowed_attempts = 3, challenge_type = ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString)
      saveTransactionRequestChallenge(result.id, challenge)
      result = result.copy(challenge = challenge)
    }

    Full(result)
  }

  override def createTransactionRequestv200(initiator: User, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody): Box[TransactionRequest] = {
    //set initial status
    //for sandbox / testing: depending on amount, we ask for challenge or not
    val status =
    if (transactionRequestType.value == TransactionRequestTypes.SANDBOX_TAN.toString && BigDecimal(body.value.amount) < 1000) {
      TransactionRequestStatus.COMPLETED
    } else {
      TransactionRequestStatus.INITIATED
    }


    // Always create a new Transaction Request
    val request = for {
      fromAccountType <- getBankAccountOld(fromAccount.bankId, fromAccount.accountId) ?~ s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId, fromAccount.accountId)) == true || hasEntitlement(fromAccount.bankId.value, initiator.userId, canCreateAnyTransactionRequest) == true, ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
      toAccountType <- getBankAccountOld(toAccount.bankId, toAccount.accountId) ?~ s"account ${toAccount.accountId} not found at bank ${toAccount.bankId}"
      rawAmt <- tryo {
        BigDecimal(body.value.amount)
      } ?~! s"amount ${body.value.amount} not convertible to number"
      // isValidTransactionRequestType is checked at API layer. Maybe here too.
      isPositiveAmtToSend <- booleanToBox(rawAmt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. (${rawAmt})")

      // For now, arbitary charge value to demonstrate PSD2 charge transparency principle. Eventually this would come from Transaction Type? 10 decimal places of scaling so can add small percentage per transaction.
      chargeValue <- tryo {
        (BigDecimal(body.value.amount) * 0.0001).setScale(10, BigDecimal.RoundingMode.HALF_UP).toDouble
      } ?~! s"could not create charge for ${body.value.amount}"
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(body.value.currency, chargeValue.toString()))

      transactionRequest <- createTransactionRequestImpl(TransactionRequestId(generateUUID()), transactionRequestType, fromAccount, toAccount, body, status.toString, charge)
    } yield transactionRequest

    //make sure we get something back
    var result = request.openOrThrowException("Exception: Couldn't create transactionRequest")

    // If no challenge necessary, create Transaction immediately and put in data store and object to return
    if (status == TransactionRequestStatus.COMPLETED) {
      // Note for 'new MappedCounterparty()' in the following :
      // We update the makePaymentImpl in V210, added the new parameter 'toCounterparty: CounterpartyTrait' for V210
      // But in V200 or before, we do not used the new parameter toCounterparty. So just keep it empty.
      val createdTransactionId = Connector.connector.vend.makePaymentv200(fromAccount,
        toAccount,
        transactionRequestCommonBody = null, //Note chargePolicy only support in V210
        BigDecimal(body.value.amount),
        body.description,
        transactionRequestType,
        "") //Note chargePolicy only support in V210

      //set challenge to null
      result = result.copy(challenge = null)

      //save transaction_id if we have one
      createdTransactionId match {
        case Full(ti) => {
          if (!createdTransactionId.isEmpty) {
            saveTransactionRequestTransaction(result.id, ti)
            result = result.copy(transaction_ids = ti.value)
          }
        }
        case Failure(message, exception, chain) => return Failure(message, exception, chain)
        case _ => None
      }
    } else {
      //if challenge necessary, create a new one
      val challenge = TransactionRequestChallenge(id = generateUUID(), allowed_attempts = 3, challenge_type = ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString)
      saveTransactionRequestChallenge(result.id, challenge)
      result = result.copy(challenge = challenge)
    }

    Full(result)
  }

  // Set initial status
  override def getStatus(challengeThresholdAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal, transactionRequestType: TransactionRequestType): Future[TransactionRequestStatus.Value] = {
    Future(
      if (transactionRequestCommonBodyAmount < challengeThresholdAmount && transactionRequestType.value != REFUND.toString) {
        // For any connector != mapped we should probably assume that transaction_status_scheduler_delay will be > 0
        // so that getTransactionRequestStatusesImpl needs to be implemented for all connectors except mapped.
        // i.e. if we are certain that saveTransaction will be honored immediately by the backend, then transaction_status_scheduler_delay
        // can be empty in the props file. Otherwise, the status will be set to STATUS_PENDING
        // and getTransactionRequestStatusesImpl needs to be run periodically to update the transaction request status.
        if (APIUtil.getPropsAsLongValue("transaction_status_scheduler_delay").isEmpty)
          TransactionRequestStatus.COMPLETED
        else
          TransactionRequestStatus.PENDING
      } else {
        TransactionRequestStatus.INITIATED
      })
  }

  // Get the charge level value
  override def getChargeValue(chargeLevelAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal): Future[String] = {
    Future(
      transactionRequestCommonBodyAmount * chargeLevelAmount match {
        //Set the mininal cost (2 euros)for transaction request
        case value if (value < 2) => "2.0"
        //Set the largest cost (50 euros)for transaction request
        case value if (value > 50) => "50"
        //Set the cost according to the charge level
        case value => value.setScale(10, BigDecimal.RoundingMode.HALF_UP).toString()
      })
  }

  /**
    *
    * @param initiator
    * @param viewId
    * @param fromAccount
    * @param toAccount
    * @param transactionRequestType       Support Types: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY
    * @param transactionRequestCommonBody Body from http request: should have common fields
    * @param chargePolicy                 SHARED, SENDER, RECEIVER
    * @param detailsPlain                 This is the details / body of the request (contains all fields in the body)
    * @return Always create a new Transaction Request in mapper, and return all the fields
    */


  override def createTransactionRequestv210(initiator: User,
                                            viewId: ViewId,
                                            fromAccount: BankAccount,
                                            toAccount: BankAccount,
                                            transactionRequestType: TransactionRequestType,
                                            transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                            detailsPlain: String,
                                            chargePolicy: String,
                                            challengeType: Option[String],
                                            scaMethod: Option[SCA],
                                            callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {

    for {
      // Get the threshold for a challenge. i.e. over what value do we require an out of Band security challenge to be sent?
      (challengeThreshold, callContext) <- Connector.connector.vend.getChallengeThreshold(fromAccount.bankId.value, fromAccount.accountId.value, viewId.value, transactionRequestType.value, transactionRequestCommonBody.value.currency, initiator.userId, initiator.name, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold ", 400), i._2)
      }
      challengeThresholdAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetChallengeThreshold. challengeThreshold amount ${challengeThreshold.amount} not convertible to number", 400, callContext) {
        BigDecimal(challengeThreshold.amount)
      }
      transactionRequestCommonBodyAmount <- NewStyle.function.tryons(s"$InvalidNumber Request Json value.amount ${transactionRequestCommonBody.value.amount} not convertible to number", 400, callContext) {
        BigDecimal(transactionRequestCommonBody.value.amount)
      }
      status <- getStatus(challengeThresholdAmount, transactionRequestCommonBodyAmount, transactionRequestType: TransactionRequestType)
      (chargeLevel, callContext) <- Connector.connector.vend.getChargeLevel(BankId(fromAccount.bankId.value), AccountId(fromAccount.accountId.value), viewId, initiator.userId, initiator.name, transactionRequestType.value, fromAccount.currency, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
      }

      chargeLevelAmount <- NewStyle.function.tryons(s"$InvalidNumber chargeLevel.amount: ${chargeLevel.amount} can not be transferred to decimal !", 400, callContext) {
        BigDecimal(chargeLevel.amount)
      }
      chargeValue <- getChargeValue(chargeLevelAmount, transactionRequestCommonBodyAmount)
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestCommonBody.value.currency, chargeValue))
      // Always create a new Transaction Request
      transactionRequest <- Future {
        createTransactionRequestImpl210(TransactionRequestId(generateUUID()), transactionRequestType, fromAccount, toAccount, transactionRequestCommonBody, detailsPlain, status.toString, charge, chargePolicy)
      } map {
        unboxFullOrFail(_, callContext, s"$InvalidConnectorResponseForCreateTransactionRequestImpl210")
      }

      // If no challenge necessary, create Transaction immediately and put in data store and object to return
      (transactionRequest, callConext) <- status match {
        case TransactionRequestStatus.COMPLETED =>
          for {
            (createdTransactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequest.id,
              transactionRequestCommonBody,
              BigDecimal(transactionRequestCommonBody.value.amount),
              transactionRequestCommonBody.description,
              transactionRequestType,
              chargePolicy,
              callContext
            )
            //set challenge to null, otherwise it have the default value "challenge": {"id": "","allowed_attempts": 0,"challenge_type": ""}
            transactionRequest <- Future(transactionRequest.copy(challenge = null))

            //save transaction_id into database
            _ <- Future {
              saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId)
            }
            //update transaction_id field for varibale 'transactionRequest'
            transactionRequest <- Future(transactionRequest.copy(transaction_ids = createdTransactionId.value))

          } yield {
            logger.debug(s"createTransactionRequestv210.createdTransactionId return: $transactionRequest")
            (transactionRequest, callContext)
          }
        case TransactionRequestStatus.INITIATED =>
          for {
            //if challenge necessary, create a new one
            (challengeId, callContext) <- createChallenge(
              fromAccount.bankId,
              fromAccount.accountId,
              initiator.userId,
              transactionRequestType: TransactionRequestType,
              transactionRequest.id.value,
              scaMethod,
              callContext
            ) map { i =>
              (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForCreateChallenge ", 400), i._2)
            }

            newChallenge = TransactionRequestChallenge(challengeId, allowed_attempts = 3, challenge_type = challengeType.getOrElse(ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString))
            _ <- Future(saveTransactionRequestChallenge(transactionRequest.id, newChallenge))
            transactionRequest <- Future(transactionRequest.copy(challenge = newChallenge))
          } yield {
            (transactionRequest, callContext)
          }
        case _ => Future(transactionRequest, callContext)
      }
    } yield {
      logger.debug(transactionRequest)
      (Full(transactionRequest), callContext)
    }
  }


  /**
    *
    * @param initiator
    * @param viewId
    * @param fromAccount
    * @param toAccount
    * @param transactionRequestType       Support Types: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY
    * @param transactionRequestCommonBody Body from http request: should have common fields
    * @param chargePolicy                 SHARED, SENDER, RECEIVER
    * @param detailsPlain                 This is the details / body of the request (contains all fields in the body)
    * @return Always create a new Transaction Request in mapper, and return all the fields
    */


  override def createTransactionRequestv400(initiator: User,
                                            viewId: ViewId,
                                            fromAccount: BankAccount,
                                            toAccount: BankAccount,
                                            transactionRequestType: TransactionRequestType,
                                            transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                            detailsPlain: String,
                                            chargePolicy: String,
                                            challengeType: Option[String],
                                            scaMethod: Option[SCA],
                                            reasons: Option[List[TransactionRequestReason]],
                                            berlinGroupPayments: Option[SepaCreditTransfersBerlinGroupV13],
                                            callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {

    for {
      // Get the threshold for a challenge. i.e. over what value do we require an out of Band security challenge to be sent?
      (challengeThreshold, callContext) <- Connector.connector.vend.getChallengeThreshold(fromAccount.bankId.value, fromAccount.accountId.value, viewId.value, transactionRequestType.value, transactionRequestCommonBody.value.currency, initiator.userId, initiator.name, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold ", 400), i._2)
      }
      challengeThresholdAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetChallengeThreshold. challengeThreshold amount ${challengeThreshold.amount} not convertible to number", 400, callContext) {
        BigDecimal(challengeThreshold.amount)
      }
      transactionRequestCommonBodyAmount <- NewStyle.function.tryons(s"$InvalidNumber Request Json value.amount ${transactionRequestCommonBody.value.amount} not convertible to number", 400, callContext) {
        BigDecimal(transactionRequestCommonBody.value.amount)
      }
      status <- getStatus(challengeThresholdAmount, transactionRequestCommonBodyAmount, transactionRequestType: TransactionRequestType)
      (chargeLevel, callContext) <- Connector.connector.vend.getChargeLevelC2(
        BankId(fromAccount.bankId.value), 
        AccountId(fromAccount.accountId.value), 
        viewId, 
        initiator.userId, 
        initiator.name, 
        transactionRequestType.value,
        transactionRequestCommonBody.value.currency,
        transactionRequestCommonBody.value.amount,
        toAccount.accountRoutings,
        Nil,
        callContext
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
      }

      chargeLevelAmount <- NewStyle.function.tryons(s"$InvalidNumber chargeLevel.amount: ${chargeLevel.amount} can not be transferred to decimal !", 400, callContext) {
        BigDecimal(chargeLevel.amount)
      }
      challengeTypeValue <- NewStyle.function.tryons(s"$InvalidChallengeType Current Type is $challengeType", 400, callContext) {
        challengeType.map(ChallengeType.withName(_)).head
      }
      chargeValue <- getChargeValue(chargeLevelAmount, transactionRequestCommonBodyAmount)
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestCommonBody.value.currency, chargeValue))
      // Always create a new Transaction Request
      transactionRequest <- Future {
        val transactionRequest = createTransactionRequestImpl210(TransactionRequestId(generateUUID()), transactionRequestType, fromAccount, toAccount, transactionRequestCommonBody, detailsPlain, status.toString, charge, chargePolicy)
        saveTransactionRequestReasons(reasons, transactionRequest)
        transactionRequest
      } map {
        unboxFullOrFail(_, callContext, s"$InvalidConnectorResponseForCreateTransactionRequestImpl210")
      }

      // If no challenge necessary, create Transaction immediately and put in data store and object to return
      (transactionRequest, callContext) <- status match {
        case TransactionRequestStatus.COMPLETED =>
          for {
            (createdTransactionId, callContext) <- transactionRequestType match {
              case TransactionRequestType("SEPA") =>
                Connector.connector.vend.makePaymentV400(transactionRequest, reasons, callContext)map { i =>
                  (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForMakePayment ",400), i._2)
                }
              case _ =>
                NewStyle.function.makePaymentv210(
                  fromAccount,
                  toAccount,
                  transactionRequest.id,
                  transactionRequestCommonBody,
                  BigDecimal(transactionRequestCommonBody.value.amount),
                  transactionRequestCommonBody.description,
                  transactionRequestType,
                  chargePolicy,
                  callContext
                )
            }
            //set challenge to null, otherwise it have the default value "challenge": {"id": "","allowed_attempts": 0,"challenge_type": ""}
            transactionRequest <- Future(transactionRequest.copy(challenge = null))

            //save transaction_id into database
            _ <- Future {
              saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId)
            }
            //update transaction_id field for varibale 'transactionRequest'
            transactionRequest <- Future(transactionRequest.copy(transaction_ids = createdTransactionId.value))

          } yield {
            logger.debug(s"createTransactionRequestv210.createdTransactionId return: $transactionRequest")
            (transactionRequest, callContext)
          }
        case TransactionRequestStatus.INITIATED =>
          //If it is BerlinGroup standard, there is no need the challenges, it has its own `Start the authorisation process for a payment initiation` endpoint
          if(transactionRequestType.value ==TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString) {
            Future(transactionRequest, callContext)
          } else {
            // return the lists of users, who need to be answered the challenges
            def getUsersForChallenges(bankId: BankId,
                                      accountId: AccountId) = {
              Connector.connector.vend.getAccountAttributesByAccount(bankId, accountId, None) map {
                _._1.map {
                  x => {
                    if (x.find(_.name == "REQUIRED_CHALLENGE_ANSWERS").map(_.value).getOrElse("1").toInt > 1) {
                      for (
                        permission <- Views.views.vend.permissions(BankIdAccountId(bankId, accountId))
                      ) yield {
                        permission.views.exists(_.canAddTransactionRequestToAnyAccount == true) match {
                          case true => Some(permission.user)
                          case _ => None
                        }
                      }
                    } else List(Some(initiator))
                  }.flatten.distinct
                }
              }
            }
  
            for {
              //if challenge necessary, create a new one
              users <- getUsersForChallenges(fromAccount.bankId, fromAccount.accountId)
              //now we support multiple challenges. We can support multiple people to answer the challenges.
              //So here we return the challengeIds. 
              (challenges, callContext) <- Connector.connector.vend.createChallengesC2(
                userIds = users.toList.flatten.map(_.userId),
                challengeType = challengeTypeValue,
                transactionRequestId = Some(transactionRequest.id.value),
                scaMethod = scaMethod,
                scaStatus = None, //Only use for BerlinGroup Now
                consentId = None, // Note: consentId and transactionRequestId are exclusive here.
                authenticationMethodId = None,
                callContext = callContext
                ) map { i =>
                (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForCreateChallenge ", 400), i._2)
              }
             
              //NOTE:this is only for Backward compatibility, now we use the MappedExpectedChallengeAnswer tables instead of the single field in TransactionRequest.
              //Here only put the dummy date.
              newChallenge = TransactionRequestChallenge(s"challenges number:${challenges.length}", allowed_attempts = 3, challenge_type = ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString)
              _ <- Future(saveTransactionRequestChallenge(transactionRequest.id, newChallenge))
              transactionRequest <- Future(transactionRequest.copy(challenge = newChallenge))
            } yield {
              (transactionRequest, callContext)
            }
          }
        case _ => Future(transactionRequest, callContext)
      }
    } yield {
      logger.debug(transactionRequest)
      (Full(transactionRequest), callContext)
    }
  }

  private def saveTransactionRequestReasons(reasons: Option[List[TransactionRequestReason]], transactionRequest: Box[TransactionRequest]) = {
    for (reason <- reasons.getOrElse(Nil)) {
      TransactionRequestReasons
        .create
        .TransactionRequestId(transactionRequest.map(_.id.value).getOrElse(""))
        .Amount(reason.amount.getOrElse(""))
        .Code(reason.code)
        .Currency(reason.currency.getOrElse(""))
        .DocumentNumber(reason.documentNumber.getOrElse(""))
        .Description(reason.description.getOrElse(""))
        .save()
    }
  }

  override def notifyTransactionRequest(fromAccount: BankAccount, toAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequestStatusValue]] =
    Future((Full(TransactionRequestStatusValue(transactionRequest.status)), callContext))

  override def saveTransactionRequestTransaction(transactionRequestId: TransactionRequestId, transactionId: TransactionId) = {
    //put connector agnostic logic here if necessary
    saveTransactionRequestTransactionImpl(transactionRequestId, transactionId)
  }

  override def getTransactionRequests(initiator: User, fromAccount: BankAccount): Box[List[TransactionRequest]] = {
    val transactionRequests =
      for {
        fromAccount <- getBankAccountOld(fromAccount.bankId, fromAccount.accountId) ?~
          s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
        isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId, fromAccount.accountId)), UserNoOwnerView)
        transactionRequests <- getTransactionRequestsImpl(fromAccount)
      } yield transactionRequests

    //make sure we return null if no challenge was saved (instead of empty fields)
    if (!transactionRequests.isEmpty) {
      for {
        treq <- transactionRequests
      } yield {
        treq.map(tr => if (tr.challenge.id == "") {
          tr.copy(challenge = null)
        } else {
          tr
        })
      }
    } else {
      transactionRequests
    }
  }

  override def getTransactionRequests210(initiator: User, fromAccount: BankAccount, callContext: Option[CallContext] = None): Box[(List[TransactionRequest], Option[CallContext])] = {
    val transactionRequests =
      for {
        transactionRequests <- getTransactionRequestsImpl210(fromAccount)
      } yield transactionRequests

    //make sure we return null if no challenge was saved (instead of empty fields)
    val transactionRequestsNew = if (!transactionRequests.isEmpty) {
      for {
        treq <- transactionRequests
      } yield {
        treq.map(tr => if (tr.challenge.id == "") {
          tr.copy(challenge = null)
        } else {
          tr
        })
      }
    } else {
      transactionRequests
    }

    transactionRequestsNew.map(transactionRequests => (transactionRequests, callContext))
  }

  override def getTransactionRequestStatuses(): Box[TransactionRequestStatus] = {
    for {
      transactionRequestStatuses <- getTransactionRequestStatusesImpl()
    } yield transactionRequestStatuses
  }

  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId, callContext: Option[CallContext]): Box[(TransactionRequest, Option[CallContext])] =
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequest(transactionRequestId).map(transactionRequest => (transactionRequest, callContext))

  override def getTransactionRequestTypes(initiator: User, fromAccount: BankAccount): Box[List[TransactionRequestType]] = {
    for {
      isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId, fromAccount.accountId)), UserNoOwnerView)
      transactionRequestTypes <- getTransactionRequestTypesImpl(fromAccount)
    } yield transactionRequestTypes
  }

  override def getTransactionRequestTypesImpl(fromAccount: BankAccount): Box[List[TransactionRequestType]] = {
    //TODO: write logic / data access
    // Get Transaction Request Types from Props "transactionRequests_supported_types". Default is empty string
    val validTransactionRequestTypes = APIUtil.getPropsValue("transactionRequests_supported_types", "").split(",").map(x => TransactionRequestType(x)).toList
    Full(validTransactionRequestTypes)
  }

  //Note: Now we use validateChallengeAnswer instead, new methods validate over kafka, and move the allowed_attempts guard into API level.
  //It is only used for V140 and V200, has been deprecated from V210.
  @deprecated
  override def answerTransactionRequestChallenge(transReqId: TransactionRequestId, answer: String): Box[Boolean] = {
    val tr = getTransactionRequestImpl(transReqId, None) ?~! s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"

    tr.map(_._1) match {
      case Full(tr: TransactionRequest) =>
        if (tr.challenge.allowed_attempts > 0) {
          if (tr.challenge.challenge_type == ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString) {
            //check if answer supplied is correct (i.e. for now, TAN -> some number and not empty)
            for {
              nonEmpty <- booleanToBox(answer.nonEmpty) ?~ "Need a non-empty answer"
              answerToNumber <- tryo(BigInt(answer)) ?~! "Need a numeric TAN"
              positive <- booleanToBox(answerToNumber > 0) ?~ "Need a positive TAN"
            } yield true

            //TODO: decrease allowed attempts value
          }
          //else if (tr.challenge.challenge_type == ...) {}
          else {
            Failure("unknown challenge type")
          }
        } else {
          Failure("Sorry, you've used up your allowed attempts.")
        }
      case Failure(f, Empty, Empty) => Failure(f)
      case _ => Failure("Error getting Transaction Request")
    }
  }

  override def createTransactionAfterChallenge(initiator: User, transReqId: TransactionRequestId): Box[TransactionRequest] = {
    for {
      (tr, callContext) <- getTransactionRequestImpl(transReqId, None) ?~! s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"
      transId <- makePayment(initiator, BankIdAccountId(BankId(tr.from.bank_id), AccountId(tr.from.account_id)),
        BankIdAccountId(BankId(tr.body.to_sandbox_tan.get.bank_id), AccountId(tr.body.to_sandbox_tan.get.account_id)), BigDecimal(tr.body.value.amount), tr.body.description, TransactionRequestType(tr.`type`)) ?~! InvalidConnectorResponseForMakePayment
      didSaveTransId <- saveTransactionRequestTransaction(transReqId, transId)
      didSaveStatus <- saveTransactionRequestStatusImpl(transReqId, TransactionRequestStatus.COMPLETED.toString)
      //get transaction request again now with updated values
      (tr, callContext) <- getTransactionRequestImpl(transReqId, None) ?~! s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"
    } yield {
      tr
    }
  }

  override def createTransactionAfterChallengev200(fromAccount: BankAccount, toAccount: BankAccount, transactionRequest: TransactionRequest): Box[TransactionRequest] = {
    for {
      transRequestId <- Full(transactionRequest.id)
      transactionId <- makePaymentv200(
        fromAccount,
        toAccount,
        transactionRequestCommonBody = null, //Note transactionRequestCommonBody started to use from V210
        BigDecimal(transactionRequest.body.value.amount),
        transactionRequest.body.description,
        TransactionRequestType(transactionRequest.`type`),
        "" //Note chargePolicy  started to use from V210
      ) ?~! InvalidConnectorResponseForMakePayment
      didSaveTransId <- saveTransactionRequestTransaction(transRequestId, transactionId)
      didSaveStatus <- saveTransactionRequestStatusImpl(transRequestId, TransactionRequestStatus.COMPLETED.toString)

      transactionRequestUpdated <- Full(transactionRequest.copy(transaction_ids = transactionId.value, status = TransactionRequestStatus.COMPLETED.toString))
    } yield {
      transactionRequestUpdated
    }
  }

  override def createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
    for {
      body <- Future(transactionRequest.body)

      transactionRequestType = transactionRequest.`type`
      transactionRequestId = transactionRequest.id
      (transactionId, callContext) <- TransactionRequestTypes.withName(transactionRequestType) match {
        case SANDBOX_TAN | ACCOUNT | ACCOUNT_OTP =>
          for {
            toSandboxTan <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodySandBoxTanJSON ", 400, callContext) {
              body.to_sandbox_tan.get
            }
            toBankId = BankId(toSandboxTan.bank_id)
            toAccountId = AccountId(toSandboxTan.account_id)
            (toAccount, callContext) <- NewStyle.function.getBankAccount(toBankId, toAccountId, callContext)
            sandboxBody = TransactionRequestBodySandBoxTanJSON(
              to = TransactionRequestAccountJsonV140(toBankId.value, toAccountId.value),
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description)
            (transactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequest.id,
              transactionRequestCommonBody = sandboxBody,
              BigDecimal(sandboxBody.value.amount),
              sandboxBody.description,
              TransactionRequestType(transactionRequestType),
              transactionRequest.charge_policy,
              callContext
            )
          } yield {
            (transactionId, callContext)
          }
        case COUNTERPARTY | CARD =>
          for {
            bodyToCounterparty <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodyCounterpartyJSON", 400, callContext) {
              body.to_counterparty.get
            }
            counterpartyId = CounterpartyId(bodyToCounterparty.counterparty_id)
            (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(counterpartyId, callContext)
            toAccount <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            counterpartyBody = TransactionRequestBodyCounterpartyJSON(
              to = CounterpartyIdJson(counterpartyId.value),
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description,
              charge_policy = transactionRequest.charge_policy,
              future_date = transactionRequest.future_date)

            (transactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequest.id,
              transactionRequestCommonBody = counterpartyBody,
              BigDecimal(counterpartyBody.value.amount),
              counterpartyBody.description,
              TransactionRequestType(transactionRequestType),
              transactionRequest.charge_policy,
              callContext
            )
          } yield {
            (transactionId, callContext)
          }
        case SIMPLE =>
          for {
            bodyToSimple <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodyCounterpartyJSON", 400, callContext) {
              body.to_simple.get
            }
            (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByRoutings(
              bodyToSimple.otherBankRoutingScheme,
              bodyToSimple.otherBankRoutingAddress,
              bodyToSimple.otherBranchRoutingScheme,
              bodyToSimple.otherBranchRoutingAddress,
              bodyToSimple.otherAccountRoutingScheme,
              bodyToSimple.otherAccountRoutingAddress,
              bodyToSimple.otherAccountSecondaryRoutingScheme,
              bodyToSimple.otherAccountSecondaryRoutingAddress,
              callContext
            )
            toAccount <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            counterpartyBody = TransactionRequestBodySimpleJsonV400(
              to = PostSimpleCounterpartyJson400(
                name = toCounterparty.name,
                description = toCounterparty.description,
                other_bank_routing_scheme = toCounterparty.otherBankRoutingScheme,
                other_bank_routing_address = toCounterparty.otherBankRoutingAddress,
                other_account_routing_scheme = toCounterparty.otherAccountRoutingScheme,
                other_account_routing_address = toCounterparty.otherAccountRoutingAddress,
                other_account_secondary_routing_scheme = toCounterparty.otherAccountSecondaryRoutingScheme,
                other_account_secondary_routing_address = toCounterparty.otherAccountSecondaryRoutingAddress,
                other_branch_routing_scheme = toCounterparty.otherBranchRoutingScheme,
                other_branch_routing_address = toCounterparty.otherBranchRoutingAddress,
              ),
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description,
              charge_policy = transactionRequest.charge_policy,
              future_date = transactionRequest.future_date
            )
            (transactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequest.id,
              transactionRequestCommonBody = counterpartyBody,
              BigDecimal(counterpartyBody.value.amount),
              counterpartyBody.description,
              TransactionRequestType(transactionRequestType),
              transactionRequest.charge_policy,
              callContext
            )
          } yield {
            (transactionId, callContext)
          }
        // In the case of a REFUND (currently working only implemented for SEPA refund request)
        case REFUND =>
          for {
            (fromAccount, toAccount, callContext) <- {
              if (fromAccount.accountId.value == transactionRequest.from.account_id) {
                val toCounterpartyIban = transactionRequest.other_account_routing_address
                for {
                  (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByIbanAndBankAccountId(toCounterpartyIban, fromAccount.bankId, fromAccount.accountId, callContext)
                  toAccount <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
                } yield (fromAccount, toAccount, callContext)
              } else {
                // Warning here, we need to use the accountId here to store the counterparty IBAN.
                // Maybe we should change the transaction request design to support bidirectional transaction requests.
                val fromCounterpartyIban = transactionRequest.from.account_id
                val toAccount = fromAccount
                for {
                  (fromCounterparty, callContext) <- NewStyle.function.getCounterpartyByIbanAndBankAccountId(fromCounterpartyIban, toAccount.bankId, toAccount.accountId, callContext)
                  fromAccount <- NewStyle.function.getBankAccountFromCounterparty(fromCounterparty, false, callContext)
                } yield (fromAccount, toAccount, callContext)
              }
            }
            refundBody = TransactionRequestBodyCommonJSON(
              value = AmountOfMoneyJsonV121(transactionRequest.body.value.currency, transactionRequest.body.value.amount),
              description = transactionRequest.body.description,
            )
            (transactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequest.id,
              transactionRequestCommonBody = refundBody,
              BigDecimal(refundBody.value.amount),
              refundBody.description,
              TransactionRequestType(transactionRequestType),
              transactionRequest.charge_policy,
              callContext
            )
          } yield {
            (transactionId, callContext)
          }
        case SEPA =>
          for {
            bodyToCounterpartyIBan <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodySEPAJSON", 400, callContext) {
              body.to_sepa.get
            }
            toCounterpartyIBan = bodyToCounterpartyIBan.iban
            (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByIban(toCounterpartyIBan, callContext)
            toAccount <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            sepaBody = TransactionRequestBodySEPAJSON(
              to = IbanJson(toCounterpartyIBan),
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description,
              charge_policy = transactionRequest.charge_policy,
              future_date = transactionRequest.future_date
            )
            (transactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequest.id,
              transactionRequestCommonBody = sepaBody,
              BigDecimal(sepaBody.value.amount),
              sepaBody.description,
              TransactionRequestType(transactionRequestType),
              transactionRequest.charge_policy,
              callContext
            )
          } yield {
            (transactionId, callContext)
          }
        case FREE_FORM => for {
          freeformBody <- Future(
            TransactionRequestBodyFreeFormJSON(
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description
            )
          )
          (transactionId, callContext) <- NewStyle.function.makePaymentv210(
            fromAccount,
            fromAccount,
            transactionRequest.id,
            transactionRequestCommonBody = freeformBody,
            BigDecimal(freeformBody.value.amount),
            freeformBody.description,
            TransactionRequestType(transactionRequestType),
            transactionRequest.charge_policy,
            callContext
          )
        } yield {
          (transactionId, callContext)
        }
        case SEPA_CREDIT_TRANSFERS => for {

          toSepaCreditTransfers <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodySandBoxTanJSON ", 400, callContext) {
            body.to_sepa_credit_transfers.get
          }
          toAccountIban = toSepaCreditTransfers.creditorAccount.iban
          (toAccount, callContext) <- NewStyle.function.getToBankAccountByIban(toAccountIban, callContext)
          (createdTransactionId, callContext) <- NewStyle.function.makePaymentv210(
            fromAccount,
            toAccount,
            transactionRequest.id,
            TransactionRequestCommonBodyJSONCommons(
              toSepaCreditTransfers.instructedAmount,
              ""
            ),
            BigDecimal(toSepaCreditTransfers.instructedAmount.amount),
            "", //This is empty for BerlinGroup sepa_credit_transfers type now.
            TransactionRequestType(transactionRequestType),
            transactionRequest.charge_policy,
            callContext
          )
        } yield {
          (createdTransactionId, callContext)
        }
        case transactionRequestType => Future((throw new Exception(s"${InvalidTransactionRequestType}: '${transactionRequestType}'. Not supported in this version.")), callContext)
      }

      didSaveTransId <- Future {
        saveTransactionRequestTransaction(transactionRequestId, transactionId).openOrThrowException(attemptedToOpenAnEmptyBox)
      }
      didSaveStatus <- Future {
        saveTransactionRequestStatusImpl(transactionRequestId, TransactionRequestStatus.COMPLETED.toString).openOrThrowException(attemptedToOpenAnEmptyBox)
      }
      //After `makePaymentv200` and update data for request, we get the new requqest from database again.
      (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, callContext)

    } yield {
      (Full(transactionRequest), callContext)
    }
  }

  //generates an unused account number and then creates the sandbox account using that number
  @deprecated("This return Box, not a future, try to use @createBankAccount instead. ", "10-05-2019")
  override def createBankAccountLegacy(
                                        bankId: BankId,
                                        accountId: AccountId,
                                        accountType: String,
                                        accountLabel: String,
                                        currency: String,
                                        initialBalance: BigDecimal,
                                        accountHolderName: String,
                                        branchId: String,
                                        accountRoutings: List[AccountRouting]
                                      ): Box[BankAccount] = {
    val uniqueAccountNumber = {
      def exists(number: String) = Connector.connector.vend.accountExists(bankId, number).openOrThrowException(attemptedToOpenAnEmptyBox)

      def appendUntilOkay(number: String): String = {
        val newNumber = number + Random.nextInt(10)
        if (!exists(newNumber)) newNumber
        else appendUntilOkay(newNumber)
      }

      //generates a random 8 digit account number
      val firstTry = (Random.nextDouble() * 10E8).toInt.toString
      appendUntilOkay(firstTry)
    }

    createSandboxBankAccount(
      bankId,
      accountId,
      uniqueAccountNumber,
      accountType,
      accountLabel,
      currency,
      initialBalance,
      accountHolderName,
      branchId: String, //added field in V220
      accountRoutings
    )

  }

  /**
    * A sepecil method:
    * This used for set account holder for accounts from Adapter. used in side @code.bankconnectors.Connector#updateUserAccountViewsOld
    * But from vJune2017 we introduce the new method `code.model.dataAccess.AuthUser.updateUserAccountViews` instead.
    * New method is much powerful and clear then this one.
    * If you only want to use this method, please double check your design. You need also think about the view, account holders.
    */
  @deprecated("we create new code.model.dataAccess.AuthUser.updateUserAccountViews for June2017 connector, try to use new instead of this", "11 September 2018")
  override def setAccountHolder(owner: String, bankId: BankId, accountId: AccountId, account_owners: List[String]): Unit = {
    //    if (account_owners.contains(owner)) { // No need for now, fix it later
    val resourceUserOwner = Users.users.vend.getUserByUserName(owner)
    resourceUserOwner match {
      case Full(owner) => {
        if (!accountOwnerExists(owner, bankId, accountId).openOrThrowException(attemptedToOpenAnEmptyBox)) {
          val holder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(owner, BankIdAccountId(bankId, accountId))
          logger.debug(s"Connector.setAccountHolder create account holder: $holder")
        }
      }
      case _ => {
        //          This shouldn't happen as AuthUser should generate the ResourceUsers when saved
        logger.error(s"resource user(s) $owner not found.")
      }
      //      }
    }
  }

  //This method is only existing in mapper
  override def accountOwnerExists(user: User, bankId: BankId, accountId: AccountId): Box[Boolean] = {
    val res =
      MapperAccountHolders.findAll(
        By(MapperAccountHolders.user, user.asInstanceOf[ResourceUser]),
        By(MapperAccountHolders.accountBankPermalink, bankId.value),
        By(MapperAccountHolders.accountPermalink, accountId.value)
      )

    Full(res.nonEmpty)
  }

  //This method is in Connector.scala, not in MappedView.scala.
  //Reason: this method is only used for different connectors. Used for mapping users/accounts/ between MainFrame and OBP.
  // Not used for creating views from OBP-API side.
  override def createViews(bankId: BankId, accountId: AccountId, owner_view: Boolean = false,
                           public_view: Boolean = false,
                           accountants_view: Boolean = false,
                           auditors_view: Boolean = false): List[View] = {

    val ownerView: Box[View] =
      if (owner_view)
        Views.views.vend.getOrCreateOwnerView(bankId, accountId, "Owner View")
      else Empty

    val publicView: Box[View] =
      if (public_view)
        Views.views.vend.getOrCreateCustomPublicView(bankId, accountId, "Public View")
      else Empty

    val accountantsView: Box[View] =
      if (accountants_view)
        Views.views.vend.getOrCreateAccountantsView(bankId, accountId, "Accountants View")
      else Empty

    val auditorsView: Box[View] =
      if (auditors_view)
        Views.views.vend.getOrCreateAuditorsView(bankId, accountId, "Auditors View")
      else Empty

    List(ownerView, publicView, accountantsView, auditorsView).flatten
  }

  override def getCurrentFxRateCached(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value field with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(TTL seconds) {
        getCurrentFxRate(bankId, fromCurrencyCode, toCurrencyCode)
      }
    }
  }

  /**
    * get transaction request type charges
    */
  override def getTransactionRequestTypeCharges(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestTypes: List[TransactionRequestType]): Box[List[TransactionRequestTypeCharge]] = {
    val res: List[TransactionRequestTypeCharge] = for {
      trt: TransactionRequestType <- transactionRequestTypes
      trtc: TransactionRequestTypeCharge <- getTransactionRequestTypeCharge(bankId, accountId, viewId, trt)
    } yield {
      trtc
    }
    Full(res)
  }

  override def deleteCustomerAttribute(customerAttributeId: String, callContext: Option[CallContext] ): OBPReturnType[Box[Boolean]] = {
    CustomerAttributeX.customerAttributeProvider.vend.deleteCustomerAttribute(customerAttributeId)  map { ( _, callContext) }
  }

  //NOTE: this method is not for mapped connector, we put it here for the star default implementation.
  //    : we call that method only when we set external authentication and provider is not OBP-API
  override def checkExternalUserCredentials(username: String, password: String, callContext: Option[CallContext]): Box[InboundExternalUser] = Failure("")

  //NOTE: this method is not for mapped connector, we put it here for the star default implementation.
  //    : we call that method only when we set external authentication and provider is not OBP-API
  override def checkExternalUserExists(username: String, callContext: Option[CallContext]): Box[InboundExternalUser] = {
    findUserByUsernameLocally(username).map( user =>
      InboundExternalUser(aud = "",
        exp = "",
        iat = "",
        iss = "",
        sub = user.username.get,
        azp = None,
        email = None,
        emailVerified = None,
        name = None
      )
    )
  }


  override def validateUserAuthContextUpdateRequest(
    bankId: String,
    userId: String,
    key: String,
    value: String,
    scaMethod: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[UserAuthContextUpdate]] = {
    for{
      _ <- Helper.booleanToFuture(s"$InvalidAuthContextUpdateRequestKey. Current Sandbox only support key == CUSTOMER_NUMBER", cc=callContext){
        key.equals("CUSTOMER_NUMBER")
      }
      //1st: check if the customer is existing 
      (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(value, BankId(bankId), callContext)
      //2rd: if the customer is existing, we can create the userAuthContextUpdateChallenge
      (userAuthContextUpdate, callContext) <- NewStyle.function.createUserAuthContextUpdate(userId, key, value, callContext)
      //3rd: send the challenge to the user.
      _ <- Future{
        scaMethod match {
          case v if v == StrongCustomerAuthentication.EMAIL.toString => // Send the email
            val params = PlainMailBodyType(userAuthContextUpdate.challenge) :: List(To(customer.email))
            Mailer.sendMail(
            From("challenge@tesobe.com"),
            Subject("Challenge request"),
            params :_*
            )
          case v if v == StrongCustomerAuthentication.SMS.toString => // Not implemented
          case _ => // Not handled
        }
      }
    } yield{
      (Full(userAuthContextUpdate), callContext)
    }
  }

  override def checkAnswer(authContextUpdateId: String, challenge: String, callContext: Option[CallContext]) = 
    UserAuthContextUpdateProvider.userAuthContextUpdateProvider.vend.checkAnswer(authContextUpdateId, challenge) map { ( _, callContext) }

  override def sendCustomerNotification(
    scaMethod: StrongCustomerAuthentication,
    recipient: String,
    subject: Option[String], //Only for EMAIL, SMS do not need it, so here it is Option
    message: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[String]] = {
    if (scaMethod == StrongCustomerAuthentication.EMAIL){ // Send the email
      val params = PlainMailBodyType(message) :: List(To(recipient))
      Mailer.sendMail(From("challenge@tesobe.com"), Subject("OBP Consent Challenge"), params :_*)
      Future{(Full("Success"), callContext)}
    } else if (scaMethod == StrongCustomerAuthentication.SMS){ // Send the SMS
      for {
        phoneNumber <- Future.successful(recipient)
        failMsg =s"$MissingPropsValueAtThisInstance sca_phone_api_key"
        smsProviderApiKey <- NewStyle.function.tryons(failMsg, 400, callContext) {
          APIUtil.getPropsValue("sca_phone_api_key").openOrThrowException(s"")
        }
        failMsg = s"$MissingPropsValueAtThisInstance sca_phone_api_secret"
        smsProviderApiSecret <- NewStyle.function.tryons(failMsg, 400, callContext) {
          APIUtil.getPropsValue("sca_phone_api_secret").openOrThrowException(s"")
        }
        client = Twilio.init(smsProviderApiKey, smsProviderApiSecret)
        failMsg = s"$SmsServerNotResponding: $phoneNumber. Or Please to use EMAIL first."
        messageSent: Message <- NewStyle.function.tryons(failMsg,400, callContext) {
          Message.creator(new PhoneNumber(phoneNumber), new PhoneNumber(phoneNumber), message).create()
        }
        failMsg = messageSent.getErrorMessage
        _ <- Helper.booleanToFuture(failMsg, cc=callContext) {
          messageSent.getErrorMessage.isEmpty
        }
      }yield Future{(Full("Success"), callContext)}
    } else
      Future{(Full("Success"), callContext)}
  }

  override def getCustomerAccountLinksByCustomerId(customerId: String, callContext: Option[CallContext]) = Future{
    (CustomerAccountLinkTrait.customerAccountLink.vend.getCustomerAccountLinksByCustomerId(customerId),callContext)
  }

  override def getCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]) = Future{
    (CustomerAccountLinkTrait.customerAccountLink.vend.getCustomerAccountLinkById(customerAccountLinkId),callContext)
  }

  override def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String, callContext: Option[CallContext])= Future{
    (CustomerAccountLinkTrait.customerAccountLink.vend.getCustomerAccountLinksByBankIdAccountId(bankId, accountId),callContext)
  }

  override def deleteCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]) = 
    CustomerAccountLinkTrait.customerAccountLink.vend.deleteCustomerAccountLinkById(customerAccountLinkId).map {(_, callContext)}

  override def updateCustomerAccountLinkById(customerAccountLinkId: String,  relationshipType: String, callContext: Option[CallContext]) = Future{
    (CustomerAccountLinkTrait.customerAccountLink.vend.updateCustomerAccountLinkById(customerAccountLinkId, relationshipType),callContext)
  }

  override def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAccountLinkTrait]] = Future{
    CustomerAccountLinkTrait.customerAccountLink.vend.createCustomerAccountLink(customerId: String, bankId, accountId: String, relationshipType: String) map { ( _, callContext) }
  }

}

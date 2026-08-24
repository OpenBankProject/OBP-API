package code.bankconnectors

import org.json4s._
import _root_.org.apache.pekko.http.scaladsl.model.HttpMethod
import code.DynamicData.DynamicDataProvider
import code.accountapplication.AccountApplicationX
import code.accountattribute.AccountAttributeX
import code.accountholders.AccountHolders
import code.api.Constant
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.attributedefinition.{AttributeDefinition, AttributeDefinitionDI}
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util._
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_1_0._
import code.api.v4_0_0.{AgentCashWithdrawalJson, PostSimpleCounterpartyJson400, TransactionRequestBodyAgentJsonV400, TransactionRequestBodySimpleJsonV400}
import code.atmattribute.AtmAttributeX
import code.atms.Atms
import code.bankaccountbalance.BankAccountBalanceX
import code.bankattribute.BankAttributeX
import code.branches.MappedBranch
import code.cardattribute.CardAttributeX
import code.cards.MappedPhysicalCard
import code.context.{UserAuthContextProvider, UserAuthContextUpdateProvider}
import code.counterpartylimit.CounterpartyLimitProvider
import code.customer._
import code.mandate.{MandateTrait, MandateProvisionTrait, SignatoryPanelTrait, MappedMandateProvider}
import code.customer.agent.AgentX
import code.customeraccountlinks.CustomerAccountLinkX
import code.customeraddress.CustomerAddressX
import code.customerattribute.CustomerAttributeX
import code.directdebit.DirectDebits
import code.endpointTag.EndpointTag
import code.fx.{DoobieFXRateQueries, fx}
import code.kycchecks.KycChecks
import code.kycdocuments.KycDocuments
import code.kycmedias.KycMedias
import code.kycstatuses.KycStatuses
import code.meetings.Meetings
import code.metadata.counterparties.Counterparties
import code.model._
import code.model.dataAccess._
import code.productattribute.{DoobieProductAttributeProvider, ProductAttributeX}
import code.productcollection.ProductCollectionX
import code.productcollectionitem.ProductCollectionItems
import code.productfee.ProductFeeX
import code.products.MappedProduct
import code.regulatedentities.MappedRegulatedEntityProvider
import code.standingorders.StandingOrders
import code.taxresidence.TaxResidenceX
import code.transaction.{MappedTransaction, TransactionQuery}
import code.transactionChallenge.Challenges
import code.transactionRequestAttribute.TransactionRequestAttributeX
import code.transactionattribute.TransactionAttributeX
import code.transactionrequests._
import code.users.{UserAttribute, UserAttributeProvider, Users}
import code.util.Helper
import code.util.Helper._
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.{CustomerAndAttribute, GetProductsParam, ProductCollectionItemsTree}
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums.TransactionRequestTypes._
import com.openbankproject.commons.model.enums.{TransactionRequestStatus, _}
import com.tesobe.model.UpdateBankAccount
import com.twilio.Twilio
import com.twilio.`type`.PhoneNumber
import com.twilio.rest.api.v2010.account.Message
import net.liftweb.common._
import com.openbankproject.commons.util.json
import org.json4s.{JArray, JBool, JObject, JValue}
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.{hours, now, time, tryo}
import org.mindrot.jbcrypt.BCrypt
import doobie._
import doobie.implicits._
import code.api.util.DoobieUtil

import java.util.Date
import java.util.UUID.randomUUID
import scala.collection.immutable.{List, Nil}
import scala.jdk.CollectionConverters._
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Random, Try}

object LocalMappedConnector extends Connector with MdcLoggable {

  //  override type AccountType = MappedBankAccount
  
  val getTransactionsTTL = APIUtil.getPropsValue("connector.cache.ttl.seconds.getTransactions", "0").toInt * 1000 // Miliseconds

  // Trading offer storage
  private val tradingOffers = new java.util.concurrent.ConcurrentHashMap[String, TradingOffer]()

  // Market trading storage
  private val marketOrders = new java.util.concurrent.ConcurrentHashMap[String, MarketOrder]()
  private val marketMatches = new java.util.concurrent.ConcurrentHashMap[String, MarketMatch]()
  private val marketTrades = new java.util.concurrent.ConcurrentHashMap[String, MarketTrade]()
  private val settlements = new java.util.concurrent.ConcurrentHashMap[String, Settlement]()
  private val deposits = new java.util.concurrent.ConcurrentHashMap[String, Deposit]()
  private val withdrawals = new java.util.concurrent.ConcurrentHashMap[String, Withdrawal]()
  private val paymentAuths = new java.util.concurrent.ConcurrentHashMap[String, PaymentAuth]()

  //This is the implicit parameter for saveConnectorMetric function.
  //eg:  override def getBank(bankId: BankId, callContext: Option[CallContext]) = saveConnectorMetric
  implicit override val nameOfConnector: String = LocalMappedConnector.getClass.getSimpleName

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
    import org.iban4j._

    if(getPropsAsBoolValue("validate_iban", false)) {
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
    } else {
      (Full(IbanChecker(true, None)), callContext)
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
    // OPEN_CORRIDOR_PROMISE traffic is M2M (OAuth2 client-credentials; the customer's SCA
    // happened at the originating bank's own channel), so its default threshold is effectively
    // infinite and no challenge fires. An operator can still set the prop explicitly to turn
    // the challenge into a four-eyes control for high-value corridor payments.
    val defaultThreshold =
      if (transactionRequestType.equalsIgnoreCase(TransactionRequestTypes.OPEN_CORRIDOR_PROMISE.toString)) "999999999999"
      else "1000"
    val threshold = BigDecimal(APIUtil.getPropsValue(propertyName, defaultThreshold))
    logger.debug(s"threshold is $threshold")

    val thresholdCurrency: String = APIUtil.getPropsValue("transactionRequests_challenge_currency", "EUR")
    logger.debug(s"thresholdCurrency is $thresholdCurrency")
    isValidCurrencyISOCode(thresholdCurrency) match {
      case true if((currency.toLowerCase.equals("lovelace")||(currency.toLowerCase.equals("ada")))) =>
        (Full(AmountOfMoney(currency, "10000000000000")), callContext)
      case true if(currency.equalsIgnoreCase("ETH")) =>
        // For ETH, skip FX conversion and return a large threshold in wei-equivalent semantic (string value).
        // Here we use a high number to effectively avoid challenge for typical dev/testing amounts.
        (Full(AmountOfMoney("ETH", "10000")), callContext)
      case true =>
        fx.exchangeRate(thresholdCurrency, currency, Some(bankId), callContext) match {
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


  override def getPaymentLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    transactionRequestType: String,
    currency: String,
    userId: String,
    username: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[AmountOfMoney]] = Future {
    
    //Get the limit from userAttribute, default is 1 
    val userAttributeName = s"TRANSACTION_REQUESTS_PAYMENT_LIMIT_${currency}_" + transactionRequestType.toUpperCase
    val userAttributes = UserAttribute.findAllByUserIdAndPersonal(userId, isPersonal = false)
    val userAttributeValue = userAttributes.find(_.name == userAttributeName).map(_.value)
    val paymentLimit = APIUtil.getPropsAsIntValue("transactionRequests_payment_limit",100000)
    val paymentLimitBox = tryo (BigDecimal(userAttributeValue.getOrElse(paymentLimit.toString)))
    logger.debug(s"getPaymentLimit: paymentLimitBox is $paymentLimitBox")
    logger.debug(s"getPaymentLimit: currency is $currency")
    paymentLimitBox match {
      case Full(paymentLimitValue)  =>
        isValidCurrencyISOCode(currency) match {
          case true =>
            (Full(AmountOfMoney(currency, paymentLimitValue.toString())), callContext)
          case false =>
            val msg = s"$InvalidISOCurrencyCode ${currency}"
            (Failure(msg), callContext)
        }
      case _ =>
        val msg = s"$InvalidNumber Current user attribute ${userAttributeName}.value is (${userAttributeValue.getOrElse("")})"
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
        None, // Signing Baskets are introduced in case of version createChallengesC3
        authenticationMethodId,
        challengeType = challengeType.toString,
        callContext
      )
      challengeId.toList
    }
    (Full(challenges.flatten), callContext)
  }

  override def createChallengesC3(
    userIds: List[String],
    challengeType: ChallengeType.Value,
    transactionRequestId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],//Only use for BerlinGroup Now
    consentId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    basketId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
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
        basketId,
        authenticationMethodId,
        challengeType = challengeType.toString,
        callContext
      )
      challengeId.toList
    }
    (Full(challenges.flatten), callContext)
  }

  private def createChallengeInternal(
    userId: String,
    transactionRequestId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus], //Only use for BerlinGroup Now
    consentId: Option[String],    // Note: consentId and transactionRequestId and BasketId are exclusive here.
    basketId: Option[String],    // Note: consentId and transactionRequestId and BasketId are exclusive here.
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
        basketId,
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
            val emailContent = CommonsEmailWrapper.EmailContent(
              from = mailUsersUserinfoSenderAddress,
              to = List(pair._2),
              subject = "Challenge",
              textContent = Some(s"Your OTP challenge : ${challengeAnswer}")
            )
            CommonsEmailWrapper.sendTextEmail(emailContent)
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
              scaPhoneApiId <- APIUtil.getPropsValue("sca_phone_api_id") ?~! s"$MissingPropsValueAtThisInstance sca_phone_api_id"
              client = Twilio.init(smsProviderApiKey, smsProviderApiSecret) //TODO, move this to other place, we only need to init it once.
              phoneNumber = tuple._2
              messageText = s"Your consent challenge : ${challengeAnswer}";
              message: Message <- tryo {Message.creator(
                new PhoneNumber(phoneNumber), 
                scaPhoneApiId, 
                messageText).create()}
              
              isSuccess <- tryo {message.getErrorMessage == null}
              
              _ = logger.debug(s"createChallengeInternal.send message to $phoneNumber, detail is $message")
              
              failMsg = if (message.getErrorMessage ==null) 
                  s"$SmsServerNotResponding: $phoneNumber. Or Please to use EMAIL first. ${message.getErrorMessage}"
                else
                  s"$SmsServerNotResponding: $phoneNumber. Or Please to use EMAIL first."
                  
              _ <- Helper.booleanToBox(isSuccess, failMsg)
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
  ): OBPReturnType[Box[ChallengeTrait]] = Future {
    val userId = callContext.map(_.user.map(_.userId).openOrThrowException(s"$AuthenticatedUserIsRequired Can not find the userId here."))
    (Challenges.ChallengeProvider.vend.validateChallenge(challengeId, hashOfSuppliedAnswer, userId), callContext)
  }
  override def validateChallengeAnswerC3(
    transactionRequestId: Option[String],
    consentId: Option[String],
    basketId: Option[String],
    challengeId: String,
    hashOfSuppliedAnswer: String,
    callContext: Option[CallContext]
  ) : OBPReturnType[Box[ChallengeTrait]] = Future {
    val userId = callContext.map(_.user.map(_.userId).openOrThrowException(s"$AuthenticatedUserIsRequired Can not find the userId here."))
    (Challenges.ChallengeProvider.vend.validateChallenge(challengeId, hashOfSuppliedAnswer, userId), callContext)
  }


  override def validateChallengeAnswerC4(
    transactionRequestId: Option[String],
    consentId: Option[String],
    challengeId: String,
    suppliedAnswer: String,
    suppliedAnswerType: SuppliedAnswerType.Value,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ChallengeTrait]] = Future {
    val userId = callContext.map(_.user.map(_.userId).openOrThrowException(s"$AuthenticatedUserIsRequired Can not find the userId here."))
    (Challenges.ChallengeProvider.vend.validateChallenge(challengeId, suppliedAnswer, userId), callContext)
  }
  
  override def validateChallengeAnswerC5(
    transactionRequestId: Option[String],
    consentId: Option[String],
    basketId: Option[String],
    challengeId: String,
    suppliedAnswer: String,
    suppliedAnswerType: SuppliedAnswerType.Value,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ChallengeTrait]] = Future {
    val userId = callContext.map(_.user.map(_.userId).openOrThrowException(s"$AuthenticatedUserIsRequired Can not find the userId here."))
    (Challenges.ChallengeProvider.vend.validateChallenge(challengeId, suppliedAnswer, userId), callContext)
  }
  
  override def getChallengesByTransactionRequestId(transactionRequestId: String, callContext:  Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] =
    Future {(Challenges.ChallengeProvider.vend.getChallengesByTransactionRequestId(transactionRequestId), callContext)}  
  
  override def getChallengesByConsentId(consentId: String, callContext:  Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] =
    Future {(Challenges.ChallengeProvider.vend.getChallengesByConsentId(consentId), callContext)}
  override def getChallengesByBasketId(basketId: String, callContext:  Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] =
    Future {(Challenges.ChallengeProvider.vend.getChallengesByBasketId(basketId), callContext)}


  override def getChallenge(challengeId: String, callContext:  Option[CallContext]): OBPReturnType[Box[ChallengeTrait]] = 
    Future {(Challenges.ChallengeProvider.vend.getChallenge(challengeId), callContext)}

  override def validateChallengeAnswerV2(challengeId: String, suppliedAnswer: String, suppliedAnswerType:SuppliedAnswerType, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = 
    Future { 
      val userId = callContext.map(_.user.map(_.userId).openOrThrowException(s"$AuthenticatedUserIsRequired Can not find the userId here."))
      //In OBP, we only validateChallenge with SuppliedAnswerType.PLAN_TEXT,
      (Full(Challenges.ChallengeProvider.vend.validateChallenge(challengeId, suppliedAnswer, userId).isDefined), callContext)
    } 

  override def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = 
    Future { 
      val userId = callContext.map(_.user.map(_.userId).openOrThrowException(s"$AuthenticatedUserIsRequired Can not find the userId here."))
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
  override def getBankLegacy(bankId: BankId, callContext: Option[CallContext]): Box[(Bank, Option[CallContext])] = {
    // The routing scheme and address are defaulted on the way out, not stored: an empty scheme
    // reads back as "OBP" and an empty address as the bank id. Mapper set the fields on the
    // in-memory entity without saving; copy does the same.
    MappedBank
      .findByBankId(bankId)
      .map(
        bank =>
          bank.copy(
            bankRoutingScheme = APIUtil.ValueOrOBP(bank.bankRoutingScheme),
            bankRoutingAddress = APIUtil.ValueOrOBPId(bank.bankRoutingAddress, bank.bankId.value))
      ).map(bank => (bank, callContext))
  }

  override def getBank(bankId: BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = Future {
    getBankLegacy(bankId, callContext)
  }


  override def getBanksLegacy(callContext: Option[CallContext]): Box[(List[Bank], Option[CallContext])] = {
    Full(MappedBank
      .findAll()
      .map(
        bank =>
          bank.copy(
            bankRoutingScheme = APIUtil.ValueOrOBP(bank.bankRoutingScheme),
            bankRoutingAddress = APIUtil.ValueOrOBPId(bank.bankRoutingAddress, bank.bankId.value))
      ),
      callContext
    )
  }

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
    val viewsToGenerate = List(SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID,SYSTEM_OWNER_VIEW_ID, SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID, SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID) //TODO, so far only set the `owner` view, later need to simulate other views.
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
        code.customeraccountlinks.DoobieCustomerAccountLinkProvider.getCustomerAccountLinkByCustomerId(customer.customerId).map(customerAccountLink => // get the account Customer link from CustomerAccountLink
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

    MappedTransaction.find(bankId, accountId, transactionId).flatMap(_.toTransaction)
      .map(transaction => (transaction, callContext))
  }

  /**
   * The OBPQueryParams a transaction read carries, as Mapper query params.
   *
   * One copy for both transaction reads below. They had identical translations, and the direction
   * restriction had to be written into each of them -- a rule that decides what a consent may see
   * should exist once, not once per caller that remembers to add it.
   *
   * The direction restriction is pushed into the query rather than applied to the rows afterwards,
   * so the database narrows and paginates in the same pass; filtering an already-limited page hands
   * the caller a short page it cannot distinguish from the end of the data. Zero counts as a credit,
   * matching UKAmounts.creditDebitIndicator -- `amount` is signed and in the smallest currency unit,
   * so its sign is all this needs.
   */
  private def transactionQueryParams(queryParams: List[OBPQueryParam]): TransactionQuery =
    TransactionQuery.fromQueryParams(queryParams)

  override def getTransactionsLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]) = {

    // TODO Refactor this. No need for database lookups etc.
    val optionalParams: TransactionQuery = transactionQueryParams(queryParams)

    def getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams: TransactionQuery): Box[List[Transaction]]
    = {
      val cacheKey = ("code.bankconnectors.LocalMappedConnector", "getTransactionsCached", List(bankId, accountId, optionalParams).mkString("_"))
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getTransactionsTTL millisecond) {

        //logger.info("Cache miss getTransactionsCached")

        val mappedTransactions = MappedTransaction.findAll(bankId, accountId, optionalParams)

        updateAccountTransactions(bankId, accountId)

        for ((account, callContext) <- getBankAccountLegacy(bankId, accountId, None))
          yield mappedTransactions.flatMap(_.toTransaction(account)) //each transaction will be modified by account, here we return the `class Transaction` not a trait.
      }
    }

    getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams).map(transactions => (transactions, callContext))
  }

  override def getTransactionsCore(bankId: BankId, accountId: AccountId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionCore]]] = {

    // TODO Refactor this. No need for database lookups etc.
    val optionalParams: TransactionQuery = transactionQueryParams(queryParams)

    def getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams: TransactionQuery): Box[List[TransactionCore]]
    = {
      val cacheKey = ("code.bankconnectors.LocalMappedConnector", "getTransactionsCached", List(bankId, accountId, optionalParams).mkString("_"))
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getTransactionsTTL millisecond) {

        //logger.info("Cache miss getTransactionsCached")

        val mappedTransactions = MappedTransaction.findAll(bankId, accountId, optionalParams)

        for ((account, callContext) <- getBankAccountLegacy(bankId, accountId, None))
          yield mappedTransactions.flatMap(_.toTransactionCore(account)) //each transaction will be modified by account, here we return the `class Transaction` not a trait.
      }
    }

    Future {
      (getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams), callContext)
    }
  }

  override def getCountOfTransactionsFromAccountToCounterparty(fromBankId: BankId, fromAccountId: AccountId, counterpartyId: CounterpartyId, fromDate: Date, toDate:Date, callContext: Option[CallContext]) :OBPReturnType[Box[Int]] = {
    val queryParams = List(OBPFromDate(fromDate),OBPToDate(toDate), OBPOrdering(None,OBPAscending))
    for{
      (transactionRequestsBox,callContext) <- LocalMappedConnectorInternal.getTransactionRequestsInternal(fromBankId: BankId, fromAccountId: AccountId, counterpartyId: CounterpartyId, queryParams, callContext: Option[CallContext])
    }yield{
      (transactionRequestsBox.map(_.length), callContext)
    }
  }
  
  override def getSumOfTransactionsFromAccountToCounterparty(fromBankId: BankId, fromAccountId: AccountId, counterpartyId: CounterpartyId, fromDate: Date, toDate:Date, callContext: Option[CallContext]):OBPReturnType[Box[AmountOfMoney]] = {
    
    val queryParams = List(OBPFromDate(fromDate),OBPToDate(toDate), OBPOrdering(None,OBPAscending))
    for{
      (fromBankAccount, callContext) <- NewStyle.function.getBankAccount(fromBankId, fromAccountId, callContext)
      (transactionRequestsBox,callContext) <- LocalMappedConnectorInternal.getTransactionRequestsInternal(fromBankId: BankId, fromAccountId: AccountId, counterpartyId: CounterpartyId, queryParams, callContext: Option[CallContext])
      // Check the input JSON format, here is just check the common parts of all four types
      (amountSum,currency) <- NewStyle.function.tryons(s"$UnknownError can not get the sum of transactions", 400, callContext) {
        val transactionRequests = transactionRequestsBox.getOrElse(Nil)
        val fromAccountCurrency = fromBankAccount.currency // eg: the fromAccount currency is EUR, and the 1 GBP  = 1.16278 Euro.
        val allAmounts = for{
          transactionRequest <- transactionRequests
          transferCurrency = transactionRequest.bodyValueCurrency //eg: if the payment json body currency is GBP.
          transferAmount= BigDecimal(transactionRequest.bodyValueAmount) //eg: if the payment json body amount is 1.
          debitRate = fx.exchangeRate(transferCurrency, fromAccountCurrency, Some(fromBankId.value), callContext) //eg: the rate here is 1.16278.
          transactionAmount = fx.convert(transferAmount, debitRate) // 1.16278 Euro
        }yield{
          transactionAmount // 1.16278 Euro
        }
        (allAmounts.sum, fromAccountCurrency) // Here we just sum all the transfer amounts.
      }
    } yield {
      (Full(AmountOfMoney(currency, amountSum.toString())), callContext)
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
      (bank, _) <- getBankLegacy(bankId, None)
      account <- getBankAccountLegacy(bankId, accountId, None).map(_._1).map(_.asInstanceOf[MappedBankAccount])
    } {
      Future {
        val useMessageQueue = APIUtil.getPropsAsBoolValue("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = Box !! account.accountLastUpdate match {
          case Full(l) => now after time(l.getTime + hours(APIUtil.getPropsAsIntValue("messageQueue.updateTransactionsInterval", 1)))
          case _ => true
        }
        if (outDatedTransactions && useMessageQueue) {
          UpdatesRequestSender.sendMsg(UpdateBankAccount(account.accountNumber, bank.nationalIdentifier))
        }
      }
    }
  }

  override def getBankAccountLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
    getBankAccountCommon(bankId, accountId, callContext)
  }
  
  override def getBankAccountByIban(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = Future {
    getBankAccountByRoutingLegacy(None, "IBAN", iban, callContext)
  }

  override def getBankAccountByRoutingLegacy(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {

    def byRoutingTable: Box[(MappedBankAccount, Option[CallContext])] = {
      def handleRouting(routing: List[BankAccountRoutingRow]): Box[(MappedBankAccount, Option[CallContext])] = {
        if (routing.size > 1) { // Handle more than 1 occurrence
          // Routing MUST be unique
          val errorMessage = s"$AccountRoutingNotUnique (scheme: $scheme, address: $address)"
          Failure(errorMessage)
        } else { // Handle 0 and 1 occurrence
          Box(routing.headOption).flatMap(accountRouting => getBankAccountCommon(accountRouting.bankId, accountRouting.accountId, callContext))
        }
      }

      bankId match {
        case Some(bankId) => // Bank specific routing
          val routing = DoobieBankAccountRoutingQueries.findAllByBankSchemeAddress(bankId, scheme, address)
          handleRouting(routing)
        case None => // World wide specific routing (IBAN etc.)
          val routing = DoobieBankAccountRoutingQueries.findAllBySchemeAddress(scheme, address)
          handleRouting(routing)
      }
    }

    // OBP-family schemes (OBP / OBP_ACCOUNT_ID) are implicit self-identifiers — address IS the
    // account_id — so they resolve directly against the BankAccount table.
    //
    // But that is not the only thing an OBP-scheme address can be. A bank may also *register* an
    // `OBP` routing whose address is something other than the account id, and BankAccountRouting
    // stores it like any other. Treating the implicit reading as the only one made those accounts
    // unreachable through every endpoint that resolves by routing: the account was right there in
    // the table, and the answer was "Bank Account not found".
    //
    // So try the implicit reading first, and fall back to the registered routing when it finds
    // nothing. The implicit reading still wins where both would match, which is what happened
    // before, so no address that resolves today resolves differently now.
    if (isImplicitOBPAccountScheme(scheme)) {
      val implicitly = bankId match {
        case Some(bankId) =>
          getBankAccountCommon(bankId, AccountId(address), callContext)
        case None =>
          // No bank context — accept only when the account_id is globally unique.
          MappedBankAccount.findAllByAccountId(address) match {
            case account :: Nil => Full((account, callContext))
            case Nil            => Empty
            case _              =>
              Failure(s"$AccountRoutingNotUnique (scheme: $scheme, address: $address)")
          }
      }
      implicitly match {
        // Nothing answers to the implicit reading, so try a registered routing.
        case Empty => byRoutingTable
        // A hit, or an ambiguity. `or` would have replaced the ambiguity with whatever the routing
        // table said, which for an ambiguous address is nothing -- turning "this address matches
        // several accounts" into a bare "not found". Keep what the implicit reading concluded.
        case decided => decided
      }
    } else {
      byRoutingTable
    }
  }

  override def getBankAccountByRouting(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = Future {
    getBankAccountByRoutingLegacy(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext])
  }


  override def getAccountRoutingsByScheme(bankId: Option[BankId], scheme: String, callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccountRoutingTrait]]] = {
    Future {
      Full(bankId match {
        case Some(bankId) => DoobieBankAccountRoutingQueries.findAllByBankScheme(bankId, scheme)
        case None => DoobieBankAccountRoutingQueries.findAllByScheme(scheme)
      })
    }.map((_, callContext))
  }

  override def getAccountRouting(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext]): Box[(BankAccountRoutingTrait, Option[CallContext])] = {
    // OBP-family schemes are never stored as explicit BankAccountRouting rows
    // (account lookups by OBP scheme go through getBankAccountByRouting, not here).
    // This lookup is used as a uniqueness check on routing-row creation, so for
    // OBP-family it must always report "no existing row" — otherwise virtual +
    // stored could be ambiguous.
    if (isImplicitOBPAccountScheme(scheme)) {
      Empty
    } else {
      val found = bankId match {
        case Some(bankId) => DoobieBankAccountRoutingQueries.findByBankSchemeAddress(bankId, scheme, address)
        case None => DoobieBankAccountRoutingQueries.findBySchemeAddress(scheme, address)
      }
      Box(found).map(accountRouting => (accountRouting, callContext))
    }
  }

  def getBankAccountCommon(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(MappedBankAccount, Option[CallContext])] = {

    def getByBankAndAccount(): Box[(MappedBankAccount, Option[CallContext])] = {
      MappedBankAccount.find(bankId.value, accountId.value)
        .map(bankAccount => (bankAccount, callContext))
    }

    if(APIUtil.checkIfStringIsUUID(accountId.value)) {
      // Find bank accounts by accountId first
      val bankAccounts = MappedBankAccount.findAllByAccountId(accountId.value)

      // If exactly one account is found, return it, else filter by bankId
      bankAccounts match {
        case account :: Nil =>
          // If exactly one account is found, return it
          Some(account, callContext)
        case _ =>
          // If multiple or no accounts are found, filter by bankId
          getByBankAndAccount()
      }
    } else {
      getByBankAndAccount()
    }

  }

  override def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccount]]] = {
    Future {
      // Tolerate stale account access: a user can hold a view/grant on an account that has
      // since been deleted (a dangling BankIdAccountId). Skip such accounts instead of
      // throwing, which would otherwise 500 an entire "list my accounts" call because of one
      // orphaned grant. Callers that need a specific account use getBankAccount(single).
      (Full(
        bankIdAccountIds.flatMap(
          bankIdAccountId =>
            getBankAccountLegacy(
              bankIdAccountId.bankId,
              bankIdAccountId.accountId,
              callContext
            ).map(_._1).toList)
      ), callContext)
    }
  }

  override def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[AccountsBalances]] =
    Future {
      val accountsBalances = for {
        bankIdAccountId <- bankIdAccountIds
        (bankAccount, callContext)<- getBankAccountLegacy(bankIdAccountId.bankId, bankIdAccountId.accountId, callContext) ?~! s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"
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
      val mostCommonCurrency = if (allCurrencies.isEmpty) "EUR" else allCurrencies.groupBy(identity).map { case (currency, occurrences) => currency -> occurrences.size }.maxBy(_._2)._1

      val allCommonCurrencyBalances = for {
        accountBalance <- accountsBalances
        requestAccountCurrency = accountBalance.balance.currency
        requestAccountAmount = BigDecimal(accountBalance.balance.amount)
        //From change from requestAccount Currency to mostCommon Currency
        rate <- fx.exchangeRate(requestAccountCurrency, mostCommonCurrency, None, callContext)
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
        (bankAccount, callContext)<- getBankAccountLegacy(bankIdAccountId.bankId, bankIdAccountId.accountId, callContext) ?~! s"${ErrorMessages.BankAccountNotFound} current BANK_ID(${bankIdAccountId.bankId}) and ACCOUNT_ID(${bankIdAccountId.accountId})"
        accountBalances = AccountBalances(
          id = bankAccount.accountId.value,
          label = bankAccount.label,
          bankId = bankAccount.bankId.value,
          accountRoutings = bankAccount.accountRoutings.map(accountRounting => AccountRouting(accountRounting.scheme, accountRounting.address)),
          balances = List(BankAccountBalance(AmountOfMoney(bankAccount.currency, bankAccount.balance.toString), balanceType= "interimBooked")),
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

  override def getBankAccountByNumber(bankId : Option[BankId], accountNumber : String, callContext: Option[CallContext]) : OBPReturnType[Box[(BankAccount)]] = 
    Future {
      val bankAccounts: Seq[MappedBankAccount] = if (bankId.isDefined){
        MappedBankAccount.findAllByAccountNumber(Some(bankId.head.value), accountNumber)
      }else{
        MappedBankAccount.findAllByAccountNumber(None, accountNumber)
      }

      val errorMessage =
        if(bankId.isEmpty)
          s"$AccountNumberNotUniqueError, current AccountNumber is $accountNumber"
        else
          s"$AccountNumberNotUniqueError, current BankId is ${bankId.head.value}, AccountNumber is $accountNumber"
          
      if(bankAccounts.length > 1){ // If the account number is not unique, return the error message
        (Failure(errorMessage), callContext)
      }else if (bankAccounts.length == 1){  // If the account number is unique, return the account
        (Full(bankAccounts.head), callContext)
      }else{ // If the account number is not found, return the error message
        (Failure(s"$InvalidAccountNumber, current AccountNumber is $accountNumber"), callContext)
      }
    }

  // This method handles external bank accounts that may not exist in our database.
  // If the account is not found, we create an in-memory account using counterparty information for payment processing.
  override def getOtherBankAccountByNumber(bankId : Option[BankId], accountNumber : String, counterparty: Option[CounterpartyTrait], callContext: Option[CallContext]) : OBPReturnType[Box[(BankAccount)]] = {
    
    for {
      (existingAccountBox, updatedCallContext) <- getBankAccountByNumber(bankId, accountNumber, callContext)
      (finalAccountBox, finalCallContext) <- existingAccountBox match {
        case Full(account) => 
          // If account found in database, return it
          Future.successful((Full(account), updatedCallContext))
        case _ => 
          // If account not found, check if we can create in-memory account
          counterparty match {
            case Some(cp) =>
              // Create in-memory account using counterparty information
              Future {
                val accountRouting1 =
                  if (cp.otherAccountRoutingScheme.isEmpty) Nil
                  else List(AccountRouting(cp.otherAccountRoutingScheme, cp.otherAccountRoutingAddress))
                val accountRouting2 =
                  if (cp.otherAccountSecondaryRoutingScheme.isEmpty) Nil
                  else List(AccountRouting(cp.otherAccountSecondaryRoutingScheme, cp.otherAccountSecondaryRoutingAddress))

                // Due to the new field in the database, old counterparty have void currency, so by default, we set it to EUR
                val counterpartyCurrency = if (cp.currency.nonEmpty) cp.currency else "EUR"

                val inMemoryAccount = BankAccountCommons(
                  AccountId(if (cp.otherAccountSecondaryRoutingAddress.nonEmpty) cp.otherAccountSecondaryRoutingAddress else accountNumber), 
                  "", 0,
                  currency = counterpartyCurrency,
                  name = cp.name,
                  "", accountNumber, 
                  BankId(cp.otherBankRoutingAddress), 
                  new Date(), "",
                  accountRoutings = accountRouting1 ++ accountRouting2,
                  List.empty, 
                  accountHolder = cp.name,
                  Some(List(Attribute(
                    name = "BANK_ROUTING_SCHEME",
                    `type` = "STRING",
                    value = cp.otherBankRoutingScheme
                  ),
                    Attribute(
                      name = "BANK_ROUTING_ADDRESS",
                      `type` = "STRING",
                      value = cp.otherBankRoutingAddress
                    ),
                  ))
                )
                (Full(inMemoryAccount), updatedCallContext)
              }
            case None =>
              // No counterparty provided, return failure
              Future.successful((Failure(s"$InvalidAccountNumber, current AccountNumber is $accountNumber and no counterparty provided for creating in-memory account"), updatedCallContext))
          }
      }
    } yield {
      (finalAccountBox, finalCallContext)
    }
  }

  override def getBankAccountByRoutings(
    bankAccountRoutings: BankAccountRoutings,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[(BankAccount)]]= { 
    val res: Future[(BankAccount, Option[CallContext])] = for{
      (fromAccount, callContext) <- if ((bankAccountRoutings.bank.scheme.equalsIgnoreCase("OBP")|| (bankAccountRoutings.bank.scheme.equalsIgnoreCase("OBP_BANK_ID")))
        && (bankAccountRoutings.account.scheme.equalsIgnoreCase("OBP") || bankAccountRoutings.account.scheme.equalsIgnoreCase("OBP_ACCOUNT_ID"))){
        for{
          (_, callContext) <- NewStyle.function.getBank(BankId(bankAccountRoutings.bank.address), callContext)
          bankId = BankId(bankAccountRoutings.bank.address)
          // The OBP scheme reads two ways -- the address is normally the account id, but a bank may
          // also have registered an OBP routing whose address is something else. Ask the resolver
          // that knows both rather than assuming the first, and fall back to checkBankAccountExists
          // when neither answers, so a genuinely unknown account still reports itself the same way.
          (account, callContext) <- getBankAccountByRoutingLegacy(
            Some(bankId), bankAccountRoutings.account.scheme, bankAccountRoutings.account.address, callContext
          ) match {
            case Full((resolved, cc)) => Future.successful((resolved, cc))
            case _ => NewStyle.function.checkBankAccountExists(
              bankId, AccountId(bankAccountRoutings.account.address), callContext)
          }
        } yield {
          (account, callContext)
        }
      } else if (bankAccountRoutings.account.scheme.equalsIgnoreCase("ACCOUNT_NUMBER")|| bankAccountRoutings.account.scheme.equalsIgnoreCase("ACCOUNT_NO")){
        for{
          bankIdOption <- Future.successful(if (bankAccountRoutings.bank.address.isEmpty) None else Some(bankAccountRoutings.bank.address))
          (account, callContext) <- NewStyle.function.getBankAccountByNumber(
            bankIdOption.map(BankId(_)),
            bankAccountRoutings.account.address,
            callContext)
        } yield {
          (account, callContext)
        }
      }else if (bankAccountRoutings.account.scheme.equalsIgnoreCase("IBAN")){
        for{
          (account, callContext) <- NewStyle.function.getBankAccountByIban(
            bankAccountRoutings.account.address,
            callContext)
        } yield {
          (account, callContext)
        }
      } else {
        throw new RuntimeException(s"$BankAccountNotFoundByRoutings. Only support scheme = OBP or scheme IBAN or scheme = ACCOUNT_NUMBER. Current value is: ${bankAccountRoutings} ")
      }}yield{
        (fromAccount, callContext)
      }
    res.map(i=>(Full(i._1),i._2))
    
  }


  override def getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Box[(List[CoreAccount], Option[CallContext])] = {
    Full(
      bankIdAccountIds
        // Tolerate stale account access: a user can hold a view/grant on an account that has
        // since been deleted (a dangling BankIdAccountId). Skip such accounts instead of
        // throwing, which would otherwise 500 an entire "list my accounts" call because of one
        // orphaned grant. Callers that need a specific account use getBankAccount(single).
        .flatMap(bankIdAccountId =>
          getBankAccountLegacy(
            bankIdAccountId.bankId,
            bankIdAccountId.accountId,
            callContext
          ).map(_._1).toList)
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
  
  private def findFirehoseAccounts(bankId: BankId, ordering: String, limit: Int, offset: Int): List[FastFirehoseAccount] = {
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

    val query = (fr"""
       select
           mappedbankaccount.theaccountid as account_id,
           mappedbankaccount.bank as bank_id,
           mappedbankaccount.accountlabel as account_label,
           mappedbankaccount.accountnumber as account_number,
           (select
               string_agg(
                   'user_id:'
                   || resourceuser.userid_
                   ||',provider:'
                   ||resourceuser.provider_
                   ||',user_name:'
                   ||resourceuser.name_,
                '::') as owners
            from resourceuser
            where
               resourceuser.id = mapperaccountholders.user_c
           ),
           mappedbankaccount.kind as kind,
           mappedbankaccount.accountcurrency as account_currency ,
           mappedbankaccount.accountbalance as account_balance,
           (select
               string_agg(
                   'bank_id:'
                   ||bankaccountrouting.bankid
                   ||',account_id:'
                   ||bankaccountrouting.accountid,
                   '::'
                   ) as account_routings
               from bankaccountrouting
               where
                     bankaccountrouting.accountid = mappedbankaccount.theaccountid
            ),
           (select
               string_agg(
                       'type:'
                       || mappedaccountattribute.mtype
                       ||',code:'
                       ||mappedaccountattribute.mcode
                       ||',value:'
                       ||mappedaccountattribute.mvalue,
                   '::') as account_attributes
           from mappedaccountattribute
           where
                mappedaccountattribute.maccountid = mappedbankaccount.theaccountid
            )
       from mappedbankaccount
                LEFT JOIN mapperaccountholders
                          ON (mappedbankaccount.bank = mapperaccountholders.accountbankpermalink and mappedbankaccount.theaccountid = mapperaccountholders.accountpermalink)
       WHERE mappedbankaccount.bank = ${bankId.value}
       ORDER BY mappedbankaccount.theaccountid """ ++ Fragment.const(ordering) ++ fr"""
       LIMIT $limit
       OFFSET $offset
       """)
      .query[(Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[java.math.BigDecimal], Option[String], Option[String])]
      .to[List]
      .map(_.map { case (id, bankIdCol, label, number, owners, kind, currency, balance, routings, attributes) =>
        FastFirehoseAccount(
          id = id.orNull,
          bankId = bankIdCol.orNull,
          label = label.orNull,
          number = number.orNull,
          owners = parseOwners(owners.getOrElse("")),
          productCode = kind.orNull,
          balance = AmountOfMoney(
            currency = currency.orNull,
            amount = balance.map(a =>
              Helper.smallestCurrencyUnitToBigDecimal(
                a.longValue(),
                currency.getOrElse("EUR")
              ).toString()
            ).orNull
          ),
          accountRoutings = parseRoutings(routings.getOrElse("")),
          accountAttributes = parseAttributes(attributes.getOrElse(""))
        )
      })
    DoobieUtil.runQuery(query)
  }
  
  override def getBankAccountsWithAttributes(bankId: BankId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[FastFirehoseAccount]]] =
    Future{
      val limit: Int = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(Constant.Pagination.limit)
      val offset = queryParams.collect { case OBPOffset(value) => value }.headOption.getOrElse(Constant.Pagination.offset)
      val orderBy = queryParams.collect {
        case OBPOrdering(_, OBPDescending) => "DESC"
      }.headOption.getOrElse("ASC")

      val ordering: String = if (orderBy == "DESC") "DESC" else "ASC"

      val firehoseAccounts = findFirehoseAccounts(bankId, ordering, limit, offset)
      (Full(firehoseAccounts), callContext)
    }

  private def findAccountDirectory(bankId: BankId, ordering: String, limit: Int, offset: Int): List[AccountDirectoryItem] = {
    def parseRoutings(routings: String): List[AccountRouting] = {
      if(!routings.isEmpty) {
        transformStringDirectory(routings).map {
          i =>
            AccountRouting(
              scheme = i("scheme").mkString(""),
              address = i("address").mkString("")
            )
        }
      } else {
        List()
      }
    }
    def parseAttributes(attributes: String): List[FastFirehoseAttributes] = {
      if(!attributes.isEmpty) {
        transformStringDirectory(attributes).map {
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
    def transformStringDirectory(input: String): List[Map[String, List[String]]] = {
      val splitToRows: List[String] = input.split("::").toList
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

    val query = (fr"""
       select
           mappedbankaccount.theaccountid as account_id,
           mappedbankaccount.bank as bank_id,
           mappedbankaccount.accountlabel as account_label,
           mappedbankaccount.accountnumber as account_number,
           mappedbankaccount.kind as kind,
           mappedbankaccount.mbranchid as branch_id,
           (select
               string_agg(
                   'scheme:'
                   ||bankaccountrouting.accountroutingscheme
                   ||',address:'
                   ||bankaccountrouting.accountroutingaddress,
                   '::'
                   ) as account_routings
               from bankaccountrouting
               where
                     bankaccountrouting.accountid = mappedbankaccount.theaccountid
                 and bankaccountrouting.bankid = mappedbankaccount.bank
            ),
           (select
               string_agg(
                       'type:'
                       || mappedaccountattribute.mtype
                       ||',code:'
                       ||mappedaccountattribute.mcode
                       ||',value:'
                       ||mappedaccountattribute.mvalue,
                   '::') as account_attributes
           from mappedaccountattribute
           where
                mappedaccountattribute.maccountid = mappedbankaccount.theaccountid
            )
       from mappedbankaccount
       WHERE mappedbankaccount.bank = ${bankId.value}
       ORDER BY mappedbankaccount.theaccountid """ ++ Fragment.const(ordering) ++ fr"""
       LIMIT $limit
       OFFSET $offset
       """)
      .query[(Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String], Option[String])]
      .to[List]
      .map(_.map { case (id, bankIdCol, label, number, kind, branchId, routings, attributes) =>
        AccountDirectoryItem(
          id = id.orNull,
          bankId = bankIdCol.orNull,
          label = label.orNull,
          number = number.orNull,
          productCode = kind.orNull,
          branchId = branchId.orNull,
          accountRoutings = parseRoutings(routings.getOrElse("")),
          accountAttributes = parseAttributes(attributes.getOrElse(""))
        )
      })
    DoobieUtil.runQuery(query)
  }

  override def getAccountDirectory(bankId: BankId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[AccountDirectoryItem]]] =
    Future{
      val limit: Int = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(Constant.Pagination.limit)
      val offset = queryParams.collect { case OBPOffset(value) => value }.headOption.getOrElse(Constant.Pagination.offset)
      val orderBy = queryParams.collect {
        case OBPOrdering(_, OBPDescending) => "DESC"
      }.headOption.getOrElse("ASC")

      val ordering: String = if (orderBy == "DESC") "DESC" else "ASC"

      val accounts = findAccountDirectory(bankId, ordering, limit, offset)
      (Full(accounts), callContext)
    }

  override def getBankSettlementAccounts(bankId: BankId, callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccount]]] = {
    Future {
      Full {
        MappedBankAccount.findAllByBankIdAndKind(bankId.value, "SETTLEMENT")
      }
    }.map(account => (account, callContext))
  }

  // localConnector/getBankAccountsHeld/bankIdAccountIds/{bankIdAccountIds}
  override def getBankAccountsHeldLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Box[List[AccountHeld]] = {
    Full(
      bankIdAccountIds
        .map(bankIdAccountId =>
          getBankAccountLegacy(
            bankIdAccountId.bankId,
            bankIdAccountId.accountId,
            callContext
          ).map(_._1)
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
  override def getAccountsHeldByUser(user: User, callContext: Option[CallContext]): OBPReturnType[Box[List[BankIdAccountId]]] = {
    Future {
      (Full(AccountHolders.accountHolders.vend.getAccountsHeldByUser(user).toList), callContext)
    }
  }


  
  /**
    * This is used for create or update the special bankAccount for COUNTERPARTY stuff (toAccountProvider != "OBP") and (Connector = RabbitMq)
    * details in createTransactionRequest - V210 ,case COUNTERPARTY.toString
    *
    */
  def createOrUpdateMappedBankAccount(bankId: BankId, accountId: AccountId, currency: String): Box[BankAccount] = {

    val mappedBankAccount = getBankAccountLegacy(bankId, accountId, None).map(_._1).map(_.asInstanceOf[MappedBankAccount]) match {
      case Full(_) =>
        MappedBankAccount.setCurrency(bankId.value, accountId.value, currency.toUpperCase)
          .openOrThrowException("the account just updated must be readable")
      case _ =>
        MappedBankAccount.insert(bankId.value, accountId.value,
          accountCurrency = currency.toUpperCase)
    }

    Full(mappedBankAccount)
  }
  
  override def getCounterpartyTrait(bankId: BankId, accountId: AccountId, counterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
    getCounterpartyByCounterpartyId(CounterpartyId(counterpartyId), callContext)
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

  override def getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
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
    
    // Empty routing values must never be used as a lookup key: matching on ("", "")
    // returns an arbitrary counterparty that happens to have the field empty — the
    // caller would silently get a beneficiary pointing at the wrong bank/account.
    lazy val counterpartyFromRoutings =
      if (otherAccountRoutingScheme.trim.nonEmpty && otherAccountRoutingAddress.trim.nonEmpty)
        Counterparties.counterparties.vend.getCounterpartyByRoutings(
          otherBankRoutingScheme: String,
          otherBankRoutingAddress: String,
          otherBranchRoutingScheme: String,
          otherBranchRoutingAddress: String,
          otherAccountRoutingScheme: String,
          otherAccountRoutingAddress: String
        )
      else Empty

    lazy val counterpartyFromSecondaryRouting =
      if (otherAccountSecondaryRoutingScheme.trim.nonEmpty && otherAccountSecondaryRoutingAddress.trim.nonEmpty)
        Counterparties.counterparties.vend.getCounterpartyBySecondaryRouting(
          otherAccountSecondaryRoutingScheme: String,
          otherAccountSecondaryRoutingAddress: String
        )
      else Empty


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
      LocalMappedConnectorInternal.getPhysicalCardsForBankLocal(bank: Bank, user: User, queryParams),
      callContext
    )
  }

  override def getPhysicalCardByCardNumber(bankCardNumber: String,  callContext:Option[CallContext]) : OBPReturnType[Box[PhysicalCardTrait]] = Future {
    (
      code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCardByCardNumber(bankCardNumber: String, callContext: Option[CallContext]),
      callContext
    )
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
  
  override def createAgent(
    bankId: String,
    legalName : String,
    mobileNumber : String,
    agentNumber : String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Agent]] = {
    AgentX.agentProvider.vend.createAgent(
      bankId: String,
      legalName : String,
      mobileNumber : String,
      agentNumber : String,
      callContext: Option[CallContext]
    ).map((_, callContext))
  }

  override def updateAgentStatus(
    agentId: String,
    isPendingAgent: Boolean,
    isConfirmedAgent: Boolean,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Agent]] = {
    AgentX.agentProvider.vend.updateAgentStatus(
      agentId: String,
      isPendingAgent: Boolean,
      isConfirmedAgent: Boolean,
      callContext: Option[CallContext]
    ).map((_, callContext))
  }

  override def getAgentByAgentId(
    agentId : String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Agent]] = {
    AgentX.agentProvider.vend.getAgentByAgentIdFuture(
      agentId : String
    ).map((_, callContext))
  }

  override def getAgentByAgentNumber(
    bankId : BankId,
    agentNumber : String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Agent]] = {
    AgentX.agentProvider.vend.getAgentByAgentNumberFuture(
      bankId, agentNumber: String
    ).map((_, callContext))
  }

  override def getAgents(
    bankId : String,
    queryParams: List[OBPQueryParam],
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[Agent]]] = {
    AgentX.agentProvider.vend.getAgentsFuture(
      BankId(bankId),
      queryParams: List[OBPQueryParam]
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

  override def getTransactionRequestIdsByAttributeNameValues(
    bankId: BankId, 
    params: Map[String, List[String]], 
    isPersonal: Boolean,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[String]]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.getTransactionRequestIdsByAttributeNameValues(
      bankId: BankId,
      params: Map[String, List[String]],
      isPersonal
    ).map((_, callContext))
  }


  override def getByAttributeNameValues(
    bankId: BankId, 
    params: Map[String, List[String]],
    isPersonal: Boolean,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.getByAttributeNameValues(
      bankId: BankId, 
      params: Map[String, List[String]],
      isPersonal: Boolean,
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
                                                  transactionRequestAttributes: List[TransactionRequestAttributeJsonV400],
                                                  isPersonal: Boolean,
                                                  callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.createTransactionRequestAttributes(
      bankId: BankId,
      transactionRequestId: TransactionRequestId,
      transactionRequestAttributes: List[TransactionRequestAttributeJsonV400],
      isPersonal: Boolean,
    ).map((_, callContext))
  }

  override def deleteTransactionRequestAttribute(transactionRequestAttributeId: String,
                                                 callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
    TransactionRequestAttributeX.transactionRequestAttributeProvider.vend.deleteTransactionRequestAttribute(
      transactionRequestAttributeId: String
    ).map((_, callContext))
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
    tryo(DoubleEntryBookTransaction.insert(
      doubleEntryTransaction.transactionRequestBankId.map(_.value).getOrElse(""),
      doubleEntryTransaction.transactionRequestAccountId.map(_.value).getOrElse(""),
      doubleEntryTransaction.transactionRequestId.map(_.value).getOrElse(""),
      doubleEntryTransaction.debitTransactionBankId.value,
      doubleEntryTransaction.debitTransactionAccountId.value,
      doubleEntryTransaction.debitTransactionId.value,
      doubleEntryTransaction.creditTransactionBankId.value,
      doubleEntryTransaction.creditTransactionAccountId.value,
      doubleEntryTransaction.creditTransactionId.value))
  ).map(doubleEntryTransaction => (DoubleEntryTransaction.toCommonsBox(doubleEntryTransaction), callContext))
  }

  override def getDoubleEntryBookTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId,
                                              callContext: Option[CallContext]): OBPReturnType[Box[DoubleEntryTransaction]] = {
    Future(
      DoubleEntryBookTransaction.findByLeg(bankId.value, accountId.value, transactionId.value)
    ).map(doubleEntryTransaction => (DoubleEntryTransaction.toCommonsBox(doubleEntryTransaction), callContext))
  }
  override def getBalancingTransaction(transactionId: TransactionId,
                                       callContext: Option[CallContext]): OBPReturnType[Box[DoubleEntryTransaction]] = {
    Future(
      DoubleEntryBookTransaction.findByTransactionId(transactionId.value)
    ).map(doubleEntryTransaction => (DoubleEntryTransaction.toCommonsBox(doubleEntryTransaction), callContext))
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
    val (fromAccount, _) = Connector.connector.vend.getBankAccountLegacy(fromBankId, fromAccountId, callContext).openOrThrowException(s"$BankAccountNotFound Current Bank_Id(${fromBankId}), Account_Id(${fromAccountId}) ")
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
      (toAccount, callContext) <-
        Connector.connector.vend.getBankAccountByRoutingLegacy(None, toAccountRoutingScheme, toAccountRoutingAddress, callContext) match {
          case Full(bankAccount) => Future.successful(bankAccount)
          case _: EmptyBox =>
            NewStyle.function.getCounterpartyByIban(toAccountRoutingAddress, callContext).flatMap(counterparty =>
              NewStyle.function.getBankAccountFromCounterparty(counterparty._1, isOutgoingAccount = true, callContext)
            )
        }
      (debitTransactionId, callContext) <- savePayment(
        fromAccount,  toAccount, transactionRequest.id, transactionRequestCommonBody, amount, description, transactionRequestType, chargePolicy, callContext)
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

      debitRate <- Future (fx.exchangeRate(currency, fromAccount.currency, Some(bankIdExchangeRate.bankId.value), callContext))
      _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion ($currency to ${fromAccount.currency}) is not supported.", cc=callContext){debitRate.isDefined}
      creditRate <- Future (fx.exchangeRate(currency, toAccount.currency, Some(bankIdExchangeRate.bankId.value), callContext))
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
              BankAccountX(toAccount.bankId, AccountId(s"${transactionRequestType}_SETTLEMENT_ACCOUNT_${fromAccount.currency}"), callContext)
                // If it doesn't exist, we look for a default settlement account regarding the currency
                .or(BankAccountX(toAccount.bankId, AccountId("DEFAULT_SETTLEMENT_ACCOUNT_" + fromAccount.currency), callContext))
                // If no specific settlement account exist for this currency, we use the default incoming account (EUR)
                .or(BankAccountX(toAccount.bankId, AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), callContext))
            }
            settlementAccount.flatMap(settlementAccount => {
              val fromTransAmtSettlementAccount: BigDecimal = {
              // In the case we selected the default settlement account INCOMING_ACCOUNT_ID account and that the counterparty currency is different from EUR, we need to calculate the amount in EUR
                if (settlementAccount._1.accountId.value == INCOMING_SETTLEMENT_ACCOUNT_ID && settlementAccount._1.currency != fromAccount.currency) {
                  val rate = fx.exchangeRate(currency, settlementAccount._1.currency, Some(bankIdExchangeRate.bankId.value), callContext)
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
              BankAccountX(fromAccount.bankId, AccountId(s"${transactionRequestType}_SETTLEMENT_ACCOUNT_${toAccount.currency}"), callContext)
                // If it doesn't exist, we look for a default settlement account regarding the currency
                .or(BankAccountX(fromAccount.bankId, AccountId("DEFAULT_SETTLEMENT_ACCOUNT_" + toAccount.currency), callContext))
                // If no specific settlement account exist for this currency, we use the default outgoing account (EUR)
                .or(BankAccountX(fromAccount.bankId, AccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID), callContext))
            settlementAccount.flatMap(settlementAccount => {
              val toTransAmtSettlementAccount: BigDecimal = {
                // In the case we selected the default settlement account OUTGOING_ACCOUNT_ID account and that the counterparty currency is different from EUR, we need to calculate the amount in EUR
                if (settlementAccount._1.accountId.value == OUTGOING_SETTLEMENT_ACCOUNT_ID && settlementAccount._1.currency != toAccount.currency) {
                  val rate = fx.exchangeRate(currency, settlementAccount._1.currency, Some(bankIdExchangeRate.bankId.value), callContext)
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
        // atomically update the balance using Doobie and SELECT FOR UPDATE row locking
        newAccountBalance <- DoobieBankAccountQueries.updateBalance(
          fromAccount.bankId.value,
          fromAccount.accountId.value,
          Helper.convertToSmallestCurrencyUnits(amount, currency)
        ) ?~! UpdateBankAccountException

        mappedTransaction <- tryo(MappedTransaction.insert(
          bank = fromAccount.bankId.value,
          account = fromAccount.accountId.value,
          transactionType = transactionRequestType,
          amount = Helper.convertToSmallestCurrencyUnits(amount, currency),
          newAccountBalance = newAccountBalance,
          currency = currency,
          tStartDate = posted,
          tFinishDate = completed,
          description = description,
          //Old data: other BankAccount(toAccount: BankAccount)simulate counterparty
          counterpartyAccountHolder = toAccount.accountHolder,
          counterpartyAccountNumber = toAccount.number,
          counterpartyAccountKind = toAccount.accountType,
          counterpartyBankName = toAccount.bankName,
          counterpartyIban = toAccount.accountRoutings.find(_.scheme == AccountRoutingScheme.IBAN.toString).map(_.address).getOrElse(""),
          counterpartyNationalId = toAccount.nationalIdentifier,
          //New data: real counterparty (toCounterparty: CounterpartyTrait)
          cpOtherAccountRoutingScheme = toAccount.accountRoutings.headOption.map(_.scheme).getOrElse(""),
          cpOtherAccountRoutingAddress = toAccount.accountRoutings.headOption.map(_.address).getOrElse(""),
          cpOtherBankRoutingScheme = toAccount.bankRoutingScheme,
          cpOtherBankRoutingAddress = toAccount.bankRoutingAddress,
          chargePolicy = chargePolicy)) ?~! s"$CreateTransactionsException, exception happened when create new mappedTransaction"
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
      debitRate <- Future (fx.exchangeRate(transactionCurrency, fromAccount.currency, Some(bankIdExchangeRate.bankId.value), callContext))
      _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion ($transactionCurrency to ${fromAccount.currency}) is not supported.", cc=callContext){debitRate.isDefined}
      creditRate <- Future (fx.exchangeRate(transactionCurrency, toAccount.currency, Some(bankIdExchangeRate.bankId.value), callContext))
      _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion ($transactionCurrency to ${toAccount.currency}) is not supported.", cc=callContext){creditRate.isDefined}

      fromTransAmt = -fx.convert(amount, debitRate) //from fromAccount balance should decrease
      toTransAmt = fx.convert(amount, creditRate)

      debitTransactionBox <- Future {
        LocalMappedConnectorInternal.saveTransaction(fromAccount, toAccount, transactionRequestCommonBody, fromTransAmt, description, transactionRequestType, chargePolicy)
          .map(debitTransactionId => (fromAccount.bankId, fromAccount.accountId, debitTransactionId, false))
          .or {
            // If we don't find any corresponding obp account, we debit a bank settlement account
            val settlementAccount =
              // We first look for a specific settlement account regarding the payment system (SEPA, ...) used and the currency
              BankAccountX(toAccount.bankId, AccountId(s"${transactionRequestType}_SETTLEMENT_ACCOUNT_${fromAccount.currency}"), callContext)
                // If it doesn't exist, we look for a default settlement account regarding the currency
                .or(BankAccountX(toAccount.bankId, AccountId("DEFAULT_SETTLEMENT_ACCOUNT_" + fromAccount.currency), callContext))
                // If no specific settlement account exist for this currency, we use the default incoming account (EUR)
                .or(BankAccountX(toAccount.bankId, AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), callContext))
            settlementAccount.flatMap(settlementAccount => {
              val fromTransAmtSettlementAccount = {
                // In the case we selected the default settlement account INCOMING_ACCOUNT_ID account and that the counterparty currency is different from EUR, we need to calculate the amount in EUR
                if (settlementAccount._1.accountId.value == INCOMING_SETTLEMENT_ACCOUNT_ID && settlementAccount._1.currency != fromAccount.currency) {
                  val rate = fx.exchangeRate(transactionCurrency, settlementAccount._1.currency, Some(bankIdExchangeRate.bankId.value), callContext)
                  Try(-fx.convert(amount, rate)).getOrElse(throw new Exception(s"$InvalidCurrency The requested currency conversion ($transactionCurrency to ${settlementAccount._1.currency}) is not supported."))
                } else fromTransAmt
              }
              LocalMappedConnectorInternal.saveTransaction(settlementAccount._1, toAccount, transactionRequestCommonBody, fromTransAmtSettlementAccount, description, transactionRequestType, chargePolicy)
                .map(debitTransactionId => (settlementAccount._1.bankId, settlementAccount._1.accountId, debitTransactionId, true))
            })
          }
      }
      creditTransactionBox <- Future {
        LocalMappedConnectorInternal.saveTransaction(toAccount, fromAccount, transactionRequestCommonBody, toTransAmt, description, transactionRequestType, chargePolicy)
          .map(creditTransactionId => (toAccount.bankId, toAccount.accountId, creditTransactionId, false))
          .or {
            // If we don't find any corresponding obp account, we credit a bank settlement account
            val settlementAccount =
            // We first look for a specific settlement account regarding the payment system (SEPA, ...) used and the currency
              BankAccountX(fromAccount.bankId, AccountId(s"${transactionRequestType}_SETTLEMENT_ACCOUNT_${toAccount.currency}"), callContext)
                // If it doesn't exist, we look for a default settlement account regarding the currency
                .or(BankAccountX(fromAccount.bankId, AccountId("DEFAULT_SETTLEMENT_ACCOUNT_" + toAccount.currency), callContext))
                // If no specific settlement account exist for this currency, we use the default outgoing account (EUR)
                .or(BankAccountX(fromAccount.bankId, AccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID), callContext))
            settlementAccount.flatMap(settlementAccount => {
              val toTransAmtSettlementAccount = {
                // In the case we selected the default settlement account OUTGOING_ACCOUNT_ID account and that the counterparty currency is different from EUR, we need to calculate the amount in EUR
                if (settlementAccount._1.accountId.value == OUTGOING_SETTLEMENT_ACCOUNT_ID && settlementAccount._1.currency != toAccount.currency) {
                  val rate = fx.exchangeRate(transactionCurrency, settlementAccount._1.currency, Some(bankIdExchangeRate.bankId.value), callContext)
                  Try(fx.convert(amount, rate)).getOrElse(throw new Exception(s"$InvalidCurrency The requested currency conversion ($transactionCurrency to ${settlementAccount._1.currency}) is not supported."))
                } else toTransAmt
              }
              LocalMappedConnectorInternal.saveTransaction(settlementAccount._1, fromAccount, transactionRequestCommonBody, toTransAmtSettlementAccount, description, transactionRequestType, chargePolicy)
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

  
  
  override def cancelPaymentV400(transactionId: TransactionId,
                                 callContext: Option[CallContext]): OBPReturnType[Box[CancelPayment]] = Future {
    // Get transaction to determine if SCA is needed based on amount
    val transaction = MappedTransaction.findByTransactionId(transactionId)

    val startSca = transaction match {
      case Full(t) =>
        // Decide based on amount (similar to real CBS logic)
        // Small amounts (<=100) don't need SCA, large amounts (>100) do
        // Convert from smallest currency unit (cents) to actual decimal amount
        val amount = Helper.smallestCurrencyUnitToBigDecimal(t.amount, t.currency).abs
        val threshold = 100
        Some(amount > threshold)
      case _ =>
        // If transaction not found, default to no SCA required
        Some(false)
    }
    
    (Full(CancelPayment(canBeCancelled = true, startSca = startSca)), callContext)
  }
  
  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = 
    Future{(TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestStatusImpl(transactionRequestId, status), callContext)}
  
  
  override def getBankAccountFromCounterparty(counterparty: CounterpartyTrait, isOutgoingAccount: Boolean, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] =
    BankAccountX.getBankAccountFromCounterparty(counterparty, isOutgoingAccount, callContext)
    
  override def updateBankAccount(
                                  bankId: BankId,
                                  accountId: AccountId,
                                  accountType: String,
                                  accountLabel: String,
                                  branchId: String,
                                  accountRoutings: List[AccountRouting],
                                  callContext: Option[CallContext]
                                ): OBPReturnType[Box[BankAccount]] = Future {

    val oldAccountRoutings: List[BankAccountRoutingRow] =
      DoobieBankAccountRoutingQueries.findAllByBankAccount(bankId, accountId)

    // Add or update new routing schemes
    accountRoutings.foreach(accountRouting =>
      oldAccountRoutings.find(_.accountRouting.scheme == accountRouting.scheme) match {
        case Some(_) =>
          DoobieBankAccountRoutingQueries.updateAddress(bankId, accountId, accountRouting.scheme, accountRouting.address)
        case None =>
          DoobieBankAccountRoutingQueries.create(bankId, accountId, accountRouting.scheme, accountRouting.address)
      }
    )

    // Delete non-present routing schemes
    oldAccountRoutings.filterNot(accountRouting => accountRoutings.exists(_.scheme == accountRouting.accountRouting.scheme))
      .foreach(accountRouting => DoobieBankAccountRoutingQueries.deleteByBankAccountScheme(bankId, accountId, accountRouting.accountRouting.scheme))

    (for {
      (account, _) <- LocalMappedConnector.getBankAccountCommon(bankId, accountId, callContext)
    } yield {
      MappedBankAccount.update(bankId.value, accountId.value, List(
        fr"kind = ${Option(accountType)}",
        fr"accountlabel = ${Option(accountLabel)}",
        fr"mbranchid = ${Option(branchId)}"))
        .openOrThrowException("the account just updated must be readable")
    }, callContext)
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
    (LocalMappedConnectorInternal.createBankAccountLegacy(bankId: BankId,
      accountId: AccountId,
      accountType: String,
      accountLabel: String,
      currency: String,
      initialBalance: BigDecimal,
      accountHolderName: String,
      branchId: String,
      accountRoutings: List[AccountRouting]), callContext)
  }


  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
    //this will be Full(true) if everything went well
    Future {
      (
        for {
          _ <- getBankLegacy(bankId, None)
          acc<- getBankAccountLegacy(bankId, accountId, None).map(_._1).map(_.asInstanceOf[MappedBankAccount])
        } yield {
          MappedBankAccount.setAccountLabel(bankId.value, accountId.value, label).isDefined
        }, 
        callContext
      )
    }
  }

  override def getProducts(bankId: BankId, params: List[GetProductsParam], callContext: Option[CallContext]): OBPReturnType[Box[List[Product]]] = {
    Future{Box !! {
      // `tag` params are resolved via the ProductTag table (AND semantics when repeated); the rest
      // continue to be treated as MappedProductAttribute filters.
      val (tagParams, attributeParams) = params.partition(_.name.toLowerCase == "tag")
      val requestedTags = tagParams.flatMap(_.value).map(_.trim).filter(_.nonEmpty)
      val codesFromTags: Option[Set[String]] =
        if (requestedTags.isEmpty) None
        else Some(code.products.ProductTagsProvider.getProductCodesWithAllTags(bankId, requestedTags))

      // Short-circuit if the tag filter yielded no matches.
      if (codesFromTags.exists(_.isEmpty)) Nil
      else if (attributeParams.isEmpty) {
        codesFromTags match {
          case Some(codes) =>
            MappedProduct.findAllByBankIdAndCodes(bankId.value, codes.toList)
          case None =>
            MappedProduct.findAllByBankId(bankId.value)
        }
      } else {
        val paramList: List[(String, List[String])] = attributeParams.map(it => it.name -> it.value)
        val codesFromAttrs: List[String] = paramList.isEmpty match {
          case true =>
            DoobieProductAttributeProvider.getProductCodesForBank(bankId.value)
          case false =>
            DoobieProductAttributeProvider.getProductCodesMatchingAnyAttribute(bankId.value, paramList)
        }
        val finalCodes = codesFromTags match {
          case Some(tagSet) => codesFromAttrs.filter(tagSet.contains)
          case None => codesFromAttrs
        }
        MappedProduct.findAllByCodes(finalCodes)
      }
    }
  }}.map(products => (products, callContext))

  override def getProduct(bankId: BankId, productCode: ProductCode, callContext: Option[CallContext]): OBPReturnType[Box[Product]] = Future{
    MappedProduct.find(bankId.value, productCode.value)
  }.map(product => (product, callContext))
  
  override def getProductTree(bankId: BankId, productCode: ProductCode, callContext: Option[CallContext]): OBPReturnType[Box[List[Product]]] = Future{
    def getProduct(bankId: BankId, productCode: ProductCode) =
      MappedProduct.find(bankId.value, productCode.value)
    
    def getProductTre(bankId : BankId, productCode : ProductCode): List[Product] = {
      getProduct(bankId, productCode) match {
        case Full(p) if p.parentProductCode.value.nonEmpty => p :: getProductTre(p.bankId, p.parentProductCode)
        case Full(p) => List(p)
        case _ => List()
      }
    }

    Full(getProductTre(bankId : BankId, productCode : ProductCode))
    
  }.map(product => (product, callContext))


  override def createOrUpdateBranch(branch: BranchT, callContext: Option[CallContext]): OBPReturnType[Box[BranchT]] = Future{

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

    val foundBranch: Box[BranchT] = LocalMappedConnectorInternal.getBranchLocal(branch.bankId, branch.branchId)

    logger.info("after getting")

    //check the branch existence and update or insert data
    val branchToReturn = tryo {
      // createOrUpdate decides insert-vs-update on (bankId, branchId); the two Mapper branches
      // differed only in that the update preserved the stored isDeleted when the caller omitted
      // it, which is why foundBranch is still resolved above.
      MappedBranch.createOrUpdate(
            branchIdRaw = branch.branchId.value,
            bankIdRaw = branch.bankId.value,
            nameRaw = branch.name,
            line1 = branch.address.line1,
            line2 = branch.address.line2,
            line3 = branch.address.line3,
            city = branch.address.city,
            county = branch.address.county.orNull,
            state = branch.address.state,
            postCode = branch.address.postCode,
            countryCode = branch.address.countryCode,
            latitude = branch.location.latitude,
            longitude = branch.location.longitude,
            licenseId = branch.meta.license.id,
            licenseName = branch.meta.license.name,
            lobbyHours = branch.lobbyString.map(_.hours).getOrElse(""), // null no good.
            driveUpHours = branch.driveUpString.map(_.hours).getOrElse(""), // OK like this? only used by versions prior to v3.0.0
            branchRoutingSchemeRaw = branch.branchRouting.map(_.scheme).orNull, //Added in V220
            branchRoutingAddressRaw = branch.branchRouting.map(_.address).orNull, //Added in V220
            lobbyOpenMonday = branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head,
            lobbyCloseMonday = branch.lobby.map(_.monday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head,
            lobbyOpenTuesday = branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head,
            lobbyCloseTuesday = branch.lobby.map(_.tuesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head,
            lobbyOpenWednesday = branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head,
            lobbyCloseWednesday = branch.lobby.map(_.wednesday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head,
            lobbyOpenThursday = branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head,
            lobbyCloseThursday = branch.lobby.map(_.thursday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head,
            lobbyOpenFriday = branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head,
            lobbyCloseFriday = branch.lobby.map(_.friday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head,
            lobbyOpenSaturday = branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head,
            lobbyCloseSaturday = branch.lobby.map(_.saturday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head,
            lobbyOpenSunday = branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.openingTime).head,
            lobbyCloseSunday = branch.lobby.map(_.sunday).getOrElse(List(OpeningTimes("00:00", "00:00"))).map(_.closingTime).head,
            // Drive Up
            driveUpOpenMonday = branch.driveUp.map(_.monday).map(_.openingTime).orNull,
            driveUpCloseMonday = branch.driveUp.map(_.monday).map(_.closingTime).orNull,
            driveUpOpenTuesday = branch.driveUp.map(_.tuesday).map(_.openingTime).orNull,
            driveUpCloseTuesday = branch.driveUp.map(_.tuesday).map(_.closingTime).orNull,
            driveUpOpenWednesday = branch.driveUp.map(_.wednesday).map(_.openingTime).orNull,
            driveUpCloseWednesday = branch.driveUp.map(_.wednesday).map(_.closingTime).orNull,
            driveUpOpenThursday = branch.driveUp.map(_.thursday).map(_.openingTime).orNull,
            driveUpCloseThursday = branch.driveUp.map(_.thursday).map(_.closingTime).orNull,
            driveUpOpenFriday = branch.driveUp.map(_.friday).map(_.openingTime).orNull,
            driveUpCloseFriday = branch.driveUp.map(_.friday).map(_.closingTime).orNull,
            driveUpOpenSaturday = branch.driveUp.map(_.saturday).map(_.openingTime).orNull,
            driveUpCloseSaturday = branch.driveUp.map(_.saturday).map(_.closingTime).orNull,
            driveUpOpenSunday = branch.driveUp.map(_.sunday).map(_.openingTime).orNull,
            driveUpCloseSunday = branch.driveUp.map(_.sunday).map(_.closingTime).orNull,
            // Easy access for people who use wheelchairs etc. Tristate boolean "Y"=true "N"=false ""=Unknown
            isAccessibleRaw = isAccessibleString,
            accessibleFeaturesRaw = branch.accessibleFeatures.orNull,
            branchTypeRaw = branchTypeString,
            moreInfoRaw = branch.moreInfo.orNull,
            phoneNumberRaw = branch.phoneNumber.orNull,
            isDeletedRaw = branch.isDeleted.getOrElse(foundBranch.flatMap(_.isDeleted).getOrElse(false)))
    }
    // Return the recently created / updated Branch from the database
    branchToReturn
  }.map((_, callContext))

  override def createOrUpdateAtm(atm: AtmT,  callContext: Option[CallContext]): OBPReturnType[Box[AtmT]] = Future{
    ( 
      Atms.atmsProvider.vend.createOrUpdateAtm(atm), 
      callContext
    )
  }
  
  override def deleteAtm(atm: AtmT,  callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future {
    (Atms.atmsProvider.vend.deleteAtm(atm), callContext)
  }

  override def getEndpointTagById(endpointTagId : String, callContext: Option[CallContext]) : OBPReturnType[Box[EndpointTagT]] = Future(
    (EndpointTag.findByEndpointTagId(endpointTagId), callContext)
  )

  override def deleteEndpointTag(endpointTagId : String, callContext: Option[CallContext]) : OBPReturnType[Box[Boolean]] = Future(
    (EndpointTag.findByEndpointTagId(endpointTagId).map(_ => EndpointTag.deleteByEndpointTagId(endpointTagId)), callContext)
  )

  override def getSystemLevelEndpointTags(operationId : String, callContext: Option[CallContext]) : OBPReturnType[Box[List[EndpointTagT]]] = Future(
    (tryo{getSystemLevelEndpointTagsBox(operationId : String)}, callContext)
  )

  override def getBankLevelEndpointTags(bankId:String, operationId : String, callContext: Option[CallContext]) : OBPReturnType[Box[List[EndpointTagT]]] = Future(
    (tryo{getBankLevelEndpointTagsBox(bankId:String, operationId : String)}, callContext)
  )

   def getAllEndpointTagsBox(operationId : String) : List[EndpointTagT] =
     EndpointTag.findAllByOperationId(operationId)
  
   def getSystemLevelEndpointTagsBox(operationId : String) : List[EndpointTagT] =
     EndpointTag.findAllByOperationId(operationId).filter(_.bankId == None)

   def getBankLevelEndpointTagsBox(bankId:String, operationId : String) : List[EndpointTagT] =
     EndpointTag.findAllByBankIdAndOperationId(bankId, operationId)
  
   override def createSystemLevelEndpointTag(operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (
       tryo {
         EndpointTag.insert(None, operationId, tagName)
       } ?~! CreateEndpointTagError, 
       callContext
     )
  }
  
   override def updateSystemLevelEndpointTag(endpointTagId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (
       EndpointTag.updateById(endpointTagId, None, operationId, tagName)
       , callContext
     )
  }
   
   override def createBankLevelEndpointTag(bankId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (
       tryo {
         EndpointTag.insert(Some(bankId), operationId, tagName)
       } ?~! CreateEndpointTagError, 
       callContext
     )
  }
  
   override def updateBankLevelEndpointTag(bankId:String, endpointTagId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (
       EndpointTag.updateById(endpointTagId, Some(bankId), operationId, tagName)
       , callContext
     )
  }
   
  override def getSystemLevelEndpointTag(operationId: String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
     (EndpointTag.findByOperationIdAndTagName(operationId, tagName).filter(_.bankId == None), callContext)
  }

  override def getBankLevelEndpointTag(bankId: String, operationId: String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{
    // Deliberately does NOT filter by bankId: the Mapper version repeated By(TagName, tagName)
    // where a By(BankId, bankId) was clearly meant, so a bank-level lookup has always resolved
    // like a system-level one. Preserved verbatim - fixing it would change which tag callers get
    // back, under cover of a storage swap.
    (EndpointTag.findByOperationIdAndTagName(operationId, tagName), callContext)
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
                                     metaLicenceName: String,
                                     callContext: Option[CallContext]): OBPReturnType[Box[Product]] = Future{

    //check the product existence and update or insert data
    tryo {
      MappedProduct.createOrUpdate(bankId, code, parentProductCode, name, category, family,
        superFamily, moreInfoUrl, termsAndConditionsUrl, details, description, metaLicenceId,
        metaLicenceName)
      // Mapper distinguished the update and create failures by error message; the store now
      // decides which it is, so the create message stands for both.
    } ?~! ErrorMessages.CreateProductError
  }.map((_, callContext))

  override def getBranches(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[BranchT], Option[CallContext])]] = {
    Future {
      Full(MappedBranch.findAllByBankId(bankId.value), callContext)
    }
  }

  override def getBranch(bankId: BankId, branchId: BranchId, callContext: Option[CallContext]): Future[Box[(BranchT, Option[CallContext])]] = {
    Future {
      LocalMappedConnectorInternal.getBranchLocal(bankId, branchId).map(branch => (branch, callContext))
    }
  }

  override def getAtm(bankId: BankId, atmId: AtmId, callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      Box(Atms.atmsProvider.vend.getAtm(bankId, atmId).map(atm => (atm, callContext)))
    }

  override def updateAtmSupportedLanguages(bankId: BankId, atmId: AtmId, supportedLanguages: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      Atms.atmsProvider.vend.updateAtmSupportedLanguages(bankId, atmId, supportedLanguages).map(atm => (atm, callContext))
    }

  override def updateAtmSupportedCurrencies(bankId: BankId, atmId: AtmId, supportedCurrencies: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      Atms.atmsProvider.vend.updateAtmSupportedCurrencies(bankId, atmId, supportedCurrencies).map(atm => (atm, callContext))
    }


  override def updateAtmAccessibilityFeatures(bankId: BankId, atmId: AtmId, accessibilityFeatures: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      Atms.atmsProvider.vend.updateAtmAccessibilityFeatures(bankId, atmId, accessibilityFeatures).map(atm => (atm, callContext))
    }

  override def updateAtmServices(bankId: BankId, atmId: AtmId, services: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      Atms.atmsProvider.vend.updateAtmServices(bankId, atmId, services).map(atm => (atm, callContext))
    }

  override def updateAtmNotes(bankId: BankId, atmId: AtmId, notes: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      Atms.atmsProvider.vend.updateAtmNotes(bankId, atmId, notes).map(atm => (atm, callContext))
    }

  override def updateAtmLocationCategories(bankId: BankId, atmId: AtmId, locationCategories: List[String], callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] =
    Future {
      Atms.atmsProvider.vend.updateAtmLocationCategories(bankId, atmId, locationCategories).map(atm => (atm, callContext))
    }

  override def getAtms(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[AtmT], Option[CallContext])]] = {
    Future {
      Full((Atms.atmsProvider.vend.getAtms(bankId, queryParams).getOrElse(Nil), callContext))
    }
  }

  override def getAllAtms(callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[AtmT], Option[CallContext])]] = {
    Future {
      Full((Atms.atmsProvider.vend.getAllAtms(queryParams), callContext))
    }
  }


  override def getCurrentCurrencies(bankId: BankId, callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] = Future {
    val rates = DoobieFXRateQueries.findAllForBank(bankId.value)
    val result = rates.map(_.fromCurrencyCode) ::: rates.map(_.toCurrencyCode)
    Some(result.distinct)
  } map {
    (_, callContext)
  }


  /**
    * get the latest record from FXRate table by the fields: fromCurrencyCode and toCurrencyCode.
    * If it is not found by (fromCurrencyCode, toCurrencyCode) order, it will try (toCurrencyCode, fromCurrencyCode) order .
    */
  override def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String, callContext: Option[CallContext]): Box[FXRate] =
    Box(DoobieFXRateQueries.find(bankId.value, fromCurrencyCode, toCurrencyCode))

  override def createOrUpdateFXRate(
                                     bankId: String,
                                     fromCurrencyCode: String,
                                     toCurrencyCode: String,
                                     conversionValue: Double,
                                     inverseConversionValue: Double,
                                     effectiveDate: Date,
                                     callContext: Option[CallContext]
                                   ): OBPReturnType[Box[FXRate]] = Future{
    val existing = DoobieFXRateQueries.find(bankId, fromCurrencyCode, toCurrencyCode)
    val errorMsg = if (existing.isDefined) UpdateFxRateError else CreateFxRateError
    DoobieFXRateQueries.createOrUpdate(bankId, fromCurrencyCode, toCurrencyCode, conversionValue, inverseConversionValue, effectiveDate) ?~! errorMsg
  }.map(fxRate=>(fxRate, callContext))



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
                                   bankRoutingAddress: String,
                                   callContext: Option[CallContext]
                                 ): Box[Bank] = {
  //check the bank existence and update or insert data
    val bank = MappedBank.findByBankId(BankId(bankId)) match {
      case Full(_) =>
        tryo {
          MappedBank.updateByBankId(bankId, fullBankName, shortBankName, logoURL, websiteURL,
            swiftBIC, national_identifier, bankRoutingScheme, bankRoutingAddress)
            .openOrThrowException("the bank just updated must be readable")
        } ?~! ErrorMessages.CreateBankError
      case _ =>
        tryo {
          // Only a create records who made the bank; an update leaves the original creator alone.
          MappedBank.insert(bankId, fullBankName, shortBankName, logoURL, websiteURL, swiftBIC,
            national_identifier, bankRoutingScheme, bankRoutingAddress,
            callContext.map(_.user).flatMap(_.toOption).map(_.userId).getOrElse(""))
        } ?~! ErrorMessages.UpdateBankError
    }

    // Insert the default settlement accounts if they doesn't exist
    MappedBankAccount.find(bankId, INCOMING_SETTLEMENT_ACCOUNT_ID) match {
      case Full(_) =>
        logger.debug(s"BankAccount(${bankId}, $INCOMING_SETTLEMENT_ACCOUNT_ID) is found.")
      case _ =>
        MappedBankAccount.insert(
          bankId = bankId,
          accountId = INCOMING_SETTLEMENT_ACCOUNT_ID,
          accountCurrency = "EUR",
          kind = "SETTLEMENT",
          holder = fullBankName, // TODO Consider to use the table MapperAccountHolder
          accountName = "Default incoming settlement account",
          accountLabel = "Settlement account: Do not delete!")
        logger.debug(s"creating BankAccount(${bankId}, $INCOMING_SETTLEMENT_ACCOUNT_ID).")
    }

    MappedBankAccount.find(bankId, OUTGOING_SETTLEMENT_ACCOUNT_ID) match {
      case Full(_) =>
        logger.debug(s"BankAccount(${bankId}, $OUTGOING_SETTLEMENT_ACCOUNT_ID) is found.")
      case _ =>
        MappedBankAccount.insert(
          bankId = bankId,
          accountId = OUTGOING_SETTLEMENT_ACCOUNT_ID,
          accountCurrency = "EUR",
          kind = "SETTLEMENT",
          holder = fullBankName,
          accountName = "Default outgoing settlement account",
          accountLabel = "Settlement account: Do not delete!")
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
  
  override def checkAgentNumberAvailable(
    bankId: BankId,
    agentNumber: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future {
    //in OBP, customer and agent share the same customer model. the CustomerAccountLink and AgentAccountLink also share the same model
    (tryo {
      CustomerX.customerProvider.vend.checkCustomerNumberAvailable(bankId, agentNumber)
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
                                 customerType: String = "",
                                 parentCustomerId: String = "",
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
      nameSuffix,
      customerType,
      parentCustomerId
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
                                         customerType: Option[String] = None,
                                         parentCustomerId: Option[String] = None,
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
      nameSuffix,
      customerType,
      parentCustomerId
    ) map {
      (_, callContext)
    }

  override def getCustomersByParentCustomerId(bankId: BankId, parentCustomerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[Customer]]] =
    CustomerX.customerProvider.vend.getCustomersByParentCustomerId(bankId, parentCustomerId) map {
      (_, callContext)
    }

  override def getCustomersByCustomerTypes(bankId: BankId, customerTypes: List[String], callContext: Option[CallContext], queryParams: List[OBPQueryParam]): OBPReturnType[Box[List[Customer]]] =
    CustomerX.customerProvider.vend.getCustomersByCustomerTypes(bankId, customerTypes, queryParams) map {
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
  override def getCustomersByCustomerLegalName(bankId: BankId, legalName: String, callContext: Option[CallContext]): OBPReturnType[Box[List[Customer]]] =
    CustomerX.customerProvider.vend.getCustomersByCustomerLegalName(bankId, legalName) map {
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
    val consumerId = callContext.map(_.consumer.map(_.consumerId).getOrElse("")).getOrElse("")
    UserAuthContextProvider.userAuthContextProvider.vend.createUserAuthContext(userId, key, value, consumerId) map {
      (_, callContext)
    }
  }

  override def createUserAuthContextUpdate(userId: String,
                                           key: String,
                                           value: String,
                                           callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContextUpdate]] = {
    val consumerId = callContext.map(_.consumer.map(_.consumerId).getOrElse("")).getOrElse("")
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
                                          ): OBPReturnType[Box[BankAttributeTrait]] =
    BankAttributeX.bankAttributeProvider.vend.createOrUpdateBankAttribute(
      bankId: BankId,
      bankAttributeId: Option[String],
      name: String,
      bankAttributeType: BankAttributeType.Value,
      value: String, isActive: Option[Boolean]) map {
      (_, callContext)
    }
  
  override def createOrUpdateAtmAttribute(bankId: BankId,
                                          atmId: AtmId,
                                          atmAttributeId: Option[String],
                                          name: String,
                                          atmAttributeType: AtmAttributeType.Value,
                                          value: String,
                                          isActive: Option[Boolean],
                                          callContext: Option[CallContext]
                                          ): OBPReturnType[Box[AtmAttributeTrait]] =
    AtmAttributeX.atmAttributeProvider.vend.createOrUpdateAtmAttribute(
      bankId: BankId,
      atmId: AtmId,
      atmAttributeId: Option[String],
      name: String,
      atmAttributeType: AtmAttributeType.Value,
      value: String, isActive: Option[Boolean]) map {
      (_, callContext)
    }


  override def getBankAttributesByBank(bankId: BankId, callContext: Option[CallContext]): OBPReturnType[Box[List[BankAttributeTrait]]] =
    BankAttributeX.bankAttributeProvider.vend.getBankAttributesFromProvider(bankId: BankId) map {
      (_, callContext)
    }
  
  override def getAtmAttributesByAtm(bank: BankId, atm: AtmId, callContext: Option[CallContext]): OBPReturnType[Box[List[AtmAttributeTrait]]] =
    AtmAttributeX.atmAttributeProvider.vend.getAtmAttributesFromProvider(bank: BankId, atm: AtmId) map {
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

  override def getBankAttributeById(bankAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAttributeTrait]] =
    BankAttributeX.bankAttributeProvider.vend.getBankAttributeById(bankAttributeId: String) map {
      (_, callContext)
    }
  
  override def getAtmAttributeById(atmAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[AtmAttributeTrait]] =
    AtmAttributeX.atmAttributeProvider.vend.getAtmAttributeById(atmAttributeId: String) map {
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
  override def deleteAtmAttribute(atmAttributeId: String, 
                                  callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    AtmAttributeX.atmAttributeProvider.vend.deleteAtmAttribute(atmAttributeId: String) map {
      (_, callContext)
    }
  
  override def deleteAtmAttributesByAtmId(atmId: AtmId, 
                                  callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    AtmAttributeX.atmAttributeProvider.vend.deleteAtmAttributesByAtmId(atmId: AtmId) map {
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
  
  override def getNonPersonalUserAttributes(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[UserAttribute]]] = {
    UserAttributeProvider.userAttributeProvider.vend.getNonPersonalUserAttributes(userId: String) map {(_, callContext)}
  }
  override def getPersonalUserAttributes(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[UserAttribute]]] = {
    UserAttributeProvider.userAttributeProvider.vend.getPersonalUserAttributes(userId: String) map {(_, callContext)}
  }
  override def getUserAttributesByUsers(userIds: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[UserAttribute]]] = {
    UserAttributeProvider.userAttributeProvider.vend.getUserAttributesByUsers(userIds) map {(_, callContext)}
  }
  override def deleteUserAttribute(userAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
    UserAttributeProvider.userAttributeProvider.vend.deleteUserAttribute(userAttributeId) map {(_, callContext)}
  }
  override def createOrUpdateUserAttribute(
                                            userId: String,
                                            userAttributeId: Option[String],
                                            name: String,
                                            attributeType: UserAttributeType.Value,
                                            value: String,
                                            isPersonal: Boolean,
                                            callContext: Option[CallContext]
                                          ): OBPReturnType[Box[UserAttribute]] = {
    UserAttributeProvider.userAttributeProvider.vend.createOrUpdateUserAttribute(
      userId: String,
      userAttributeId: Option[String],
      name: String,
      attributeType: UserAttributeType.Value,
      value: String,
      isPersonal: Boolean
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
      // it._3 is List[ProductAttribute] straight off DoobieProductAttributeProvider, whose rows are
      // ProductAttributeRow - not ProductAttributeCommons. Casting compiles and checks nothing;
      // it defers a ClassCastException to whoever reads the tree's attributes at the Commons type.
      val data: Box[List[ProductCollectionItemsTree]] = it.map(boxValue => boxValue.map(it =>
        ProductCollectionItemsTree(it._1, it._2, ProductAttributeCommons.toCommonsList(it._3))))
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

  override def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[Counterparty]] = Future{
    val transactions = getTransactionsLegacy(bankId, accountId ,None).map(_._1).toList.flatten
    val counterparties = for {
      transaction <- transactions
      counterpartyName <- List(transaction.otherAccount.counterpartyName)
      otherAccountRoutingScheme <- List(transaction.otherAccount.otherAccountRoutingScheme)
      otherAccountRoutingAddress <- List(transaction.otherAccount.otherAccountRoutingAddress.get)
      counterpartyIdFromTransaction <- List(APIUtil.createImplicitCounterpartyId(bankId.value,accountId.value,counterpartyName,otherAccountRoutingScheme, otherAccountRoutingAddress))
      if counterpartyIdFromTransaction == counterpartyId
    } yield {
      transaction.otherAccount
    }

    counterparties match {
      case List() => Empty
      case x :: xs => Full(x) //Because they have the same counterpartId, so they are actually just one counterparty.
    }
  }.map(counterparty=>(counterparty,callContext))

  override def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[List[Counterparty]] ]= {
    Future{
      (Full(getTransactionsLegacy(bankId, accountId, None).map(_._1).toList.flatten.map(_.otherAccount).toSet.toList), 
      callContext)
    } //there are many transactions share the same Counterparty, so we need filter the same ones.
  }
  
  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): OBPReturnType[Box[List[Transaction]]] = {
    val result: Box[(List[Transaction], Option[CallContext])] = getTransactionsLegacy(bankId, accountId, callContext, queryParams)
    Future(result.map(_._1), result.map(_._2).getOrElse(callContext))
  }


  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext] = None): OBPReturnType[Box[Transaction]] = {
    val result: Box[(Transaction, Option[CallContext])] = getTransactionLegacy(bankId, accountId, transactionId, callContext)
    Future(result.map(_._1), result.map(_._2).getOrElse(callContext))
  }
  
  override def saveTransactionRequestChallenge(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] ={
    Future{(TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestChallengeImpl(transactionRequestId, challenge), callContext)}
  }
  
  // Set initial status
  override def getStatus(challengeThresholdAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal, transactionRequestType: TransactionRequestType, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequestStatus.Value]]  = {
    Future(Full(
      // OPEN_CORRIDOR_PROMISE is held at PENDING: promises accumulate for bilateral netting
      // and no Transaction may post at create time — the settle-pair step posts the net later
      // (OPEN_CORRIDOR_SIMPLE_NETTING.md). At/above the challenge threshold the INITIATED +
      // challenge seam still applies (four-eyes); the challenge answer also lands at PENDING
      // (see createTransactionAfterChallengeV210).
      if (transactionRequestType.value == TransactionRequestTypes.OPEN_CORRIDOR_PROMISE.toString
          && transactionRequestCommonBodyAmount < challengeThresholdAmount) {
        TransactionRequestStatus.PENDING
      } else if (transactionRequestCommonBodyAmount < challengeThresholdAmount && transactionRequestType.value != REFUND.toString) {
        // For any connector != mapped we should probably assume that transaction_request_status_scheduler_delay will be > 0
        // so that getTransactionRequestStatuses needs to be implemented for all connectors except mapped.
        // i.e. if we are certain that saveTransaction will be honored immediately by the backend, then transaction_request_status_scheduler_delay
        // can be empty in the props file. Otherwise, the status will be set to STATUS_PENDING
        // and getTransactionRequestStatuses needs to be run periodically to update the transaction request status.
        if (APIUtil.getPropsAsLongValue("transaction_request_status_scheduler_delay").isEmpty)
          TransactionRequestStatus.COMPLETED
        else
          TransactionRequestStatus.PENDING
      } else {
        TransactionRequestStatus.INITIATED
      }), callContext)
  }

  // Get the charge level value
  override def getChargeValue(chargeLevelAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal, callContext: Option[CallContext]): OBPReturnType[Box[String]] = {
    Future(
      (Full(transactionRequestCommonBodyAmount * chargeLevelAmount match {
        //Set the mininal cost (2 euros)for transaction request
        case value if (value < 2) => "2.0"
        //Set the largest cost (50 euros)for transaction request
        case value if (value > 50) => "50"
        //Set the cost according to the charge level
        case value => value.setScale(10, BigDecimal.RoundingMode.HALF_UP).toString()
      }), callContext)
    )
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

      transactionRequestCommonBodyAmount <- NewStyle.function.tryons(s"$InvalidNumber Request Json value.amount ${transactionRequestCommonBody.value.amount} not convertible to number", 400, callContext) {
        BigDecimal(transactionRequestCommonBody.value.amount)
      }

      (paymentLimit, callContext) <- Connector.connector.vend.getPaymentLimit(fromAccount.bankId.value, fromAccount.accountId.value, viewId.value, transactionRequestType.value, transactionRequestCommonBody.value.currency, initiator.userId, initiator.name, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetPaymentLimit ", 400), i._2)
      }

      paymentLimitAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetPaymentLimit. payment limit amount ${paymentLimit.amount} not convertible to number", 400, callContext) {
        BigDecimal(paymentLimit.amount)
      }

      _ <- Helper.booleanToFuture(s"$InvalidJsonValue the payment amount is over the payment limit($paymentLimit)", 400, callContext) {
        transactionRequestCommonBodyAmount <= paymentLimitAmount
      }
      
      // Get the threshold for a challenge. i.e. over what value do we require an out of Band security challenge to be sent?
      (challengeThreshold, callContext) <- Connector.connector.vend.getChallengeThreshold(fromAccount.bankId.value, fromAccount.accountId.value, viewId.value, transactionRequestType.value, transactionRequestCommonBody.value.currency, initiator.userId, initiator.name, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold - ${nameOf(getChallengeThreshold _)}", 400), i._2)
      }
      challengeThresholdAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetChallengeThreshold. challengeThreshold amount ${challengeThreshold.amount} not convertible to number", 400, callContext) {
        BigDecimal(challengeThreshold.amount)
      }
      (status, callContext) <- NewStyle.function.getStatus(challengeThresholdAmount, transactionRequestCommonBodyAmount, transactionRequestType: TransactionRequestType, callContext)
      (chargeLevel, callContext) <- Connector.connector.vend.getChargeLevel(BankId(fromAccount.bankId.value), AccountId(fromAccount.accountId.value), viewId, initiator.userId, initiator.name, transactionRequestType.value, fromAccount.currency, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
      }

      chargeLevelAmount <- NewStyle.function.tryons(s"$InvalidNumber chargeLevel.amount: ${chargeLevel.amount} can not be transferred to decimal !", 400, callContext) {
        BigDecimal(chargeLevel.amount)
      }
      (chargeValue, callContext) <- NewStyle.function.getChargeValue(chargeLevelAmount, transactionRequestCommonBodyAmount, callContext)
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestCommonBody.value.currency, chargeValue))
      // Always create a new Transaction Request
      transactionRequest <- Future {
        TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl210(
          TransactionRequestId(generateUUID()),
          transactionRequestType,
          fromAccount,
          toAccount,
          transactionRequestCommonBody,
          detailsPlain,
          status.toString,
          charge,
          chargePolicy,
          None, 
          None,
          None,
          None,
          callContext
        )
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
            _ <- saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId,callContext)
           
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
            _ <- saveTransactionRequestChallenge(transactionRequest.id, newChallenge, callContext)
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
                                            callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {

    for {
      transactionRequestCommonBodyAmount <- NewStyle.function.tryons(s"$InvalidNumber Request Json value.amount ${transactionRequestCommonBody.value.amount} not convertible to number", 400, callContext) {
        BigDecimal(transactionRequestCommonBody.value.amount)
      }
      
      (paymentLimit, callContext) <- Connector.connector.vend.getPaymentLimit(fromAccount.bankId.value, fromAccount.accountId.value, viewId.value, transactionRequestType.value, transactionRequestCommonBody.value.currency, initiator.userId, initiator.name, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetPaymentLimit ", 400), i._2)
      }

      paymentLimitAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetPaymentLimit. payment limit amount ${paymentLimit.amount} not convertible to number", 400, callContext) {
        BigDecimal(paymentLimit.amount)
      }
      
      _ <- Helper.booleanToFuture(s"$InvalidJsonValue the payment amount is over the payment limit($paymentLimit)", 400, callContext) {
        transactionRequestCommonBodyAmount <= paymentLimitAmount
      }
      
      // Get the threshold for a challenge. i.e. over what value do we require an out of Band security challenge to be sent?
      (challengeThreshold, callContext) <- Connector.connector.vend.getChallengeThreshold(fromAccount.bankId.value, fromAccount.accountId.value, viewId.value, transactionRequestType.value, transactionRequestCommonBody.value.currency, initiator.userId, initiator.name, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold - ${nameOf(getChallengeThreshold _)}", 400), i._2)
      }
      challengeThresholdAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetChallengeThreshold. challengeThreshold amount ${challengeThreshold.amount} not convertible to number", 400, callContext) {
        BigDecimal(challengeThreshold.amount)
      }


      (status, callContext) <- NewStyle.function.getStatus(challengeThresholdAmount, transactionRequestCommonBodyAmount, transactionRequestType: TransactionRequestType, callContext)
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
      (chargeValue, callContext) <- NewStyle.function.getChargeValue(chargeLevelAmount, transactionRequestCommonBodyAmount, callContext)
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestCommonBody.value.currency, chargeValue))
      // Always create a new Transaction Request
      transactionRequest <- Future {
        val transactionRequest = TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl210(
          TransactionRequestId(generateUUID()),
          transactionRequestType,
          fromAccount,
          toAccount,
          transactionRequestCommonBody,
          detailsPlain,
          status.toString,
          charge,
          chargePolicy,
          None,
          None,
          None,
          None,
          callContext
        )
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
            _ <- saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId, callContext)
            
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
                        permission.views.exists(view =>view.view.allowed_actions.exists( _ == CAN_ANSWER_TRANSACTION_REQUEST_CHALLENGE))
                        match {
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
              _ <- saveTransactionRequestChallenge(transactionRequest.id, newChallenge, callContext)
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
  
  override def createTransactionRequestSepaCreditTransfersBGV1(
    initiator: Option[User],
    paymentServiceType: PaymentServiceTypes,
    transactionRequestType: TransactionRequestTypes,
    transactionRequestBody: SepaCreditTransfersBerlinGroupV13,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TransactionRequestBGV1]] = {
    LocalMappedConnectorInternal.createTransactionRequestBGInternal(
      initiator: Option[User],
      paymentServiceType: PaymentServiceTypes,
      transactionRequestType: TransactionRequestTypes,
      transactionRequestBody: SepaCreditTransfersBerlinGroupV13,
      callContext: Option[CallContext]
    )
  }

  override def createTransactionRequestPeriodicSepaCreditTransfersBGV1(
    initiator: Option[User],
    paymentServiceType: PaymentServiceTypes,
    transactionRequestType: TransactionRequestTypes,
    transactionRequestBody: PeriodicSepaCreditTransfersBerlinGroupV13,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TransactionRequestBGV1]] = {
    LocalMappedConnectorInternal.createTransactionRequestBGInternal(
      initiator: Option[User],
      paymentServiceType: PaymentServiceTypes,
      transactionRequestType: TransactionRequestTypes,
      transactionRequestBody: PeriodicSepaCreditTransfersBerlinGroupV13,
      callContext: Option[CallContext]
    )
  }


  private def saveTransactionRequestReasons(reasons: Option[List[TransactionRequestReason]], transactionRequest: Box[TransactionRequest]) = {
    for (reason <- reasons.getOrElse(Nil)) {
      code.transactionrequests.DoobieTransactionRequestReasonsQueries.create(
        transactionRequestId = transactionRequest.map(_.id.value).getOrElse(""),
        code = reason.code,
        documentNumber = reason.documentNumber.getOrElse(""),
        amount = reason.amount.getOrElse(""),
        currency = reason.currency.getOrElse(""),
        description = reason.description.getOrElse("")
      )
    }
  }

  override def notifyTransactionRequest(fromAccount: BankAccount, toAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequestStatusValue]] =
    Future((Full(TransactionRequestStatusValue(transactionRequest.status)), callContext))

  override def saveTransactionRequestTransaction(transactionRequestId: TransactionRequestId, transactionId: TransactionId, callContext: Option[CallContext]) : OBPReturnType[Box[Boolean]]= {
    Future{(TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestTransactionImpl(transactionRequestId, transactionId), callContext)}
  }

  override def getTransactionRequests210(initiator: User, fromAccount: BankAccount, callContext: Option[CallContext] = None): Box[(List[TransactionRequest], Option[CallContext])] = {
    val transactionRequests =
      for {
        transactionRequests <- TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
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

  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId, callContext: Option[CallContext]): Box[(TransactionRequest, Option[CallContext])] =
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequest(transactionRequestId).map(transactionRequest => (transactionRequest, callContext))

  override def getTransactionRequestTypes(initiator: User, fromAccount: BankAccount, callContext: Option[CallContext]):Box[(List[TransactionRequestType], Option[CallContext])] = {
    Full((APIUtil.getPropsValue("transactionRequests_supported_types", "").split(",").map(x => TransactionRequestType(x)).toList, callContext))
  }
  
  override def createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
    // OPEN_CORRIDOR_PROMISE never posts at challenge-answer: a successfully answered challenge
    // (four-eyes control) admits the promise into the corridor at PENDING, where it accumulates
    // for bilateral netting. The settle-pair step posts the net Transaction later
    // (OPEN_CORRIDOR_SIMPLE_NETTING.md). Posting here would move funds outside the netting model.
    if (transactionRequest.`type` == TransactionRequestTypes.OPEN_CORRIDOR_PROMISE.toString) {
      for {
        _ <- NewStyle.function.saveTransactionRequestStatusImpl(transactionRequest.id, TransactionRequestStatus.PENDING.toString, callContext)
        (heldTransactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequest.id, callContext)
      } yield {
        (Full(heldTransactionRequest), callContext)
      }
    } else
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
            (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            counterpartyBody = TransactionRequestBodyCounterpartyJSON(
              to = CounterpartyIdJson(counterpartyId.value),
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description,
              charge_policy = transactionRequest.charge_policy,
              future_date = transactionRequest.future_date,
              None)//this TransactionRequestAttributeJsonV400 is only in OBP side 

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
        case AGENT_CASH_WITHDRAWAL =>
          for {
            bodyToAgent <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodyAgentJsonV400", 400, callContext) {
              body.to_agent.get
            }
            (agent, callContext) <- NewStyle.function.getAgentByAgentNumber(BankId(bodyToAgent.bank_id), bodyToAgent.agent_number, callContext)
            (agentAccountLinks, callContext) <- NewStyle.function.getAgentAccountLinksByAgentId(agent.agentId, callContext)
            agentAccountLink <- NewStyle.function.tryons(AgentAccountLinkNotFound, 400, callContext) {
              agentAccountLinks.head
            }
            (toAccount, callContext) <- NewStyle.function.getBankAccount(BankId(agentAccountLink.bankId), AccountId(agentAccountLink.accountId), callContext)

            agentRequestJsonBody = TransactionRequestBodyAgentJsonV400(
              to = AgentCashWithdrawalJson(bodyToAgent.bank_id, bodyToAgent.agent_number),
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description,
              charge_policy = transactionRequest.charge_policy,
              future_date = transactionRequest.future_date
            )

            (transactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequest.id,
              transactionRequestCommonBody = agentRequestJsonBody,
              BigDecimal(agentRequestJsonBody.value.amount),
              agentRequestJsonBody.description,
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
            (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
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
        // OPEN_CORRIDOR_PROMISE is handled by the hold-at-PENDING branch at the top of this
        // method and never reaches this match.
        // In the case of a REFUND (currently working only implemented for SEPA refund request)
        case REFUND =>
          for {
            (fromAccount, toAccount, callContext) <- {
              if (fromAccount.accountId.value == transactionRequest.from.account_id) {
                val toCounterpartyIban = transactionRequest.other_account_routing_address
                for {
                  (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByIbanAndBankAccountId(toCounterpartyIban, fromAccount.bankId, fromAccount.accountId, callContext)
                  (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
                } yield (fromAccount, toAccount, callContext)
              } else {
                // Warning here, we need to use the accountId here to store the counterparty IBAN.
                // Maybe we should change the transaction request design to support bidirectional transaction requests.
                val fromCounterpartyIban = transactionRequest.from.account_id
                val toAccount = fromAccount
                for {
                  (fromCounterparty, callContext) <- NewStyle.function.getCounterpartyByIbanAndBankAccountId(fromCounterpartyIban, toAccount.bankId, toAccount.accountId, callContext)
                  (fromAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(fromCounterparty, false, callContext)
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
            (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
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

      didSaveTransId <- saveTransactionRequestTransaction(transactionRequestId, transactionId, callContext)

      didSaveStatus <- NewStyle.function.saveTransactionRequestStatusImpl(transactionRequestId, TransactionRequestStatus.COMPLETED.toString, callContext)
      
      //After `makePaymentv210` and update data for request, we get the new request from database .
      (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, callContext)

    } yield {
      (Full(transactionRequest), callContext)
    }
  }
  
  /**
    * get transaction request type charges
    */
  override def getTransactionRequestTypeCharges(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestTypes: List[TransactionRequestType], callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestTypeCharge]]] = Future {
    (Full(for {
      trt: TransactionRequestType <- transactionRequestTypes
      trtc: TransactionRequestTypeCharge <- LocalMappedConnectorInternal.getTransactionRequestTypeCharge(bankId, accountId, viewId, trt)
    } yield {
      trtc
    }), callContext)
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
    AuthUser.findAuthUserByUsernameAndProvider(username, Constant.localIdentityProvider).map(user =>
      InboundExternalUser(aud = "",
        exp = "",
        iat = "",
        iss = "",
        sub = user.username,
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
            val emailContent = CommonsEmailWrapper.EmailContent(
              from = mailUsersUserinfoSenderAddress,
              to = List(customer.email),
              subject = "Challenge request",
              textContent = Some(userAuthContextUpdate.challenge)
            )
            CommonsEmailWrapper.sendTextEmail(emailContent)
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
      val emailContent = CommonsEmailWrapper.EmailContent(
        from = mailUsersUserinfoSenderAddress,
        to = List(recipient),
        subject = "OBP Consent Challenge",
        textContent = Some(message)
      )
      CommonsEmailWrapper.sendTextEmail(emailContent)
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
    (CustomerAccountLinkX.customerAccountLink.vend.getCustomerAccountLinksByCustomerId(customerId),callContext)
  }

  override def getAgentAccountLinksByAgentId(agentId: String, callContext: Option[CallContext]) = Future{
    //in OBP, customer and agent share the same customer model. the CustomerAccountLink and AgentAccountLink also share the same model
    (CustomerAccountLinkX.customerAccountLink.vend.getCustomerAccountLinksByCustomerId(agentId),callContext) 
  }

  override def getCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]) = Future{
    (CustomerAccountLinkX.customerAccountLink.vend.getCustomerAccountLinkById(customerAccountLinkId),callContext)
  }

  override def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String, callContext: Option[CallContext])= Future{
    (CustomerAccountLinkX.customerAccountLink.vend.getCustomerAccountLinksByBankIdAccountId(bankId, accountId),callContext)
  }

  override def deleteCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]) = 
    CustomerAccountLinkX.customerAccountLink.vend.deleteCustomerAccountLinkById(customerAccountLinkId).map {(_, callContext)}

  override def updateCustomerAccountLinkById(customerAccountLinkId: String,  relationshipType: String, callContext: Option[CallContext]) = Future{
    (CustomerAccountLinkX.customerAccountLink.vend.updateCustomerAccountLinkById(customerAccountLinkId, relationshipType),callContext)
  }

  override def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAccountLinkTrait]] = Future{
    CustomerAccountLinkX.customerAccountLink.vend.createCustomerAccountLink(customerId: String, bankId, accountId: String, relationshipType: String) map { ( _, callContext) }
  }

  override def createAgentAccountLink(agentId: String, bankId: String, accountId: String, callContext: Option[CallContext]): OBPReturnType[Box[AgentAccountLinkTrait]] = Future{
    //in OBP, customer and agent share the same customer model. the CustomerAccountLink and AgentAccountLink also share the same model
    CustomerAccountLinkX.customerAccountLink.vend.createCustomerAccountLink(agentId: String, bankId, accountId: String, "Owner") map { customer => (
      AgentAccountLinkTraitCommons(
        agentAccountLinkId = customer.customerAccountLinkId,
        agentId = customer.customerId,
        bankId = customer.bankId,
        accountId = customer.accountId,
      ), 
      callContext) 
    }
  }

  override def createCustomerLink(bankId: String, customerId: String, otherBankId: String, otherCustomerId: String, relationshipTo: String, callContext: Option[CallContext]): OBPReturnType[Box[code.customerlinks.CustomerLinkTrait]] = Future{
    (code.customerlinks.CustomerLinkX.customerLink.vend.createCustomerLink(bankId, customerId, otherBankId, otherCustomerId, relationshipTo), callContext)
  }

  override def getCustomerLinkById(customerLinkId: String, callContext: Option[CallContext]): OBPReturnType[Box[code.customerlinks.CustomerLinkTrait]] = Future{
    (code.customerlinks.CustomerLinkX.customerLink.vend.getCustomerLinkById(customerLinkId), callContext)
  }

  override def getCustomerLinksByBankId(bankId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[code.customerlinks.CustomerLinkTrait]]] = Future{
    (code.customerlinks.CustomerLinkX.customerLink.vend.getCustomerLinksByBankId(bankId), callContext)
  }

  override def getCustomerLinksByCustomerId(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[code.customerlinks.CustomerLinkTrait]]] = Future{
    (code.customerlinks.CustomerLinkX.customerLink.vend.getCustomerLinksByCustomerId(customerId), callContext)
  }

  override def updateCustomerLinkById(customerLinkId: String, relationshipTo: String, callContext: Option[CallContext]): OBPReturnType[Box[code.customerlinks.CustomerLinkTrait]] = Future{
    (code.customerlinks.CustomerLinkX.customerLink.vend.updateCustomerLinkById(customerLinkId, relationshipTo), callContext)
  }

  override def deleteCustomerLinkById(customerLinkId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
    code.customerlinks.CustomerLinkX.customerLink.vend.deleteCustomerLinkById(customerLinkId).map{(_, callContext)}
  }

  override def getConsentImplicitSCA(user: User, callContext: Option[CallContext]): OBPReturnType[Box[ConsentImplicitSCAT]] = Future {
  //find the email from the user, and the OBP Implicit SCA is email
    (Full(ConsentImplicitSCA(
      scaMethod =  StrongCustomerAuthentication.EMAIL,
      recipient = user.emailAddress
    )), callContext)
  }

  override def createOrUpdateCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    currency: String,
    maxSingleAmount: BigDecimal,
    maxMonthlyAmount: BigDecimal,
    maxNumberOfMonthlyTransactions: Int,
    maxYearlyAmount: BigDecimal,
    maxNumberOfYearlyTransactions: Int,
    maxTotalAmount: BigDecimal,
    maxNumberOfTransactions: Int, 
    callContext: Option[CallContext]) =
    CounterpartyLimitProvider.counterpartyLimit.vend.createOrUpdateCounterpartyLimit(
      bankId: String,
      accountId: String,
      viewId: String,
      counterpartyId: String,
      currency: String,
      maxSingleAmount: BigDecimal,
      maxMonthlyAmount: BigDecimal,
      maxNumberOfMonthlyTransactions: Int,
      maxYearlyAmount: BigDecimal,
      maxNumberOfYearlyTransactions: Int,
      maxTotalAmount: BigDecimal,
      maxNumberOfTransactions: Int
    ) map {
      (_, callContext)
    }
    
  override def getCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    callContext: Option[CallContext]
  ) =
    CounterpartyLimitProvider.counterpartyLimit.vend.getCounterpartyLimit(
      bankId: String,
      accountId: String,
      viewId: String,
      counterpartyId: String
    ) map {
      (_, callContext)
    }

  override def deleteCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] =
    CounterpartyLimitProvider.counterpartyLimit.vend.deleteCounterpartyLimit(
      bankId: String,
      accountId: String,
      viewId: String,
      counterpartyId: String) map {
      (_, callContext)
    }

  override def getRegulatedEntities(
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[RegulatedEntityTrait]]] = Future {
    tryo {MappedRegulatedEntityProvider.getRegulatedEntities()}
  } map {
    (_, callContext)
  }

  override def getRegulatedEntityByEntityId(
    regulatedEntityId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[RegulatedEntityTrait]] = Future {
    MappedRegulatedEntityProvider.getRegulatedEntityByEntityId(regulatedEntityId)
  } map {
    (_, callContext)
  }

  override def getBankAccountBalancesByAccountId(
    accountId: AccountId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[BankAccountBalanceTrait]]] = {
    val balancesF = BankAccountBalanceX.bankAccountBalanceProvider.vend.getBankAccountBalances(accountId).map {
      (_, callContext)
    }

    val bankId = BankId(defaultBankId)

    val bankAccountBalancesF = LocalMappedConnector.getBankAccountBalances(BankIdAccountId(bankId, accountId), callContext).map {
      response =>
        response._1.map(_.balances.map(balance => BankAccountBalanceTraitCommons(
          bankId = bankId,
          accountId = accountId,
          balanceId = BalanceId(""), // BalanceId is not used in this context, so we can set it to a dummy value.
          balanceType = balance.balanceType,
          balanceAmount = BigDecimal(balance.balance.amount),
          lastChangeDateTime = None,
          referenceDate = None,
        )))

    }

    for {
      balances <- balancesF
      bankAccountBalances <- bankAccountBalancesF
    } yield {
      val merged = for {
        b1 <- balances._1
        b2 <- bankAccountBalances
      } yield b1 ++ b2
      (merged, callContext)
    }
  }

  override def getBankAccountsBalancesByAccountIds(
    accountIds: List[AccountId],
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[BankAccountBalanceTrait]]] = {
    BankAccountBalanceX.bankAccountBalanceProvider.vend.getBankAccountsBalances(accountIds).map {
      (_, callContext)
    }
  }

  override def getBankAccountBalanceById(
    balanceId: BalanceId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[BankAccountBalanceTrait]] = {
    BankAccountBalanceX.bankAccountBalanceProvider.vend.getBankAccountBalanceById(balanceId).map {
      (_, callContext)
    }
  }

  override def createOrUpdateBankAccountBalance(
    bankId: BankId,
    accountId: AccountId,
    balanceId: Option[BalanceId],
    balanceType: String,
    balanceAmount: BigDecimal,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[BankAccountBalanceTrait]] = {
    BankAccountBalanceX.bankAccountBalanceProvider.vend.createOrUpdateBankAccountBalance(
      bankId,
      accountId,
      balanceId,
      balanceType,
      balanceAmount
    ).map {
      (_, callContext)
    }
  }

  override def deleteBankAccountBalance(
    balanceId: BalanceId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = {
    BankAccountBalanceX.bankAccountBalanceProvider.vend.deleteBankAccountBalance(balanceId).map {
      (_, callContext)
    }
  }

  // Mandate methods
  override def getMandateById(
    mandateId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MandateTrait]] = Future {
    (MappedMandateProvider.getMandateById(mandateId), callContext)
  }

  override def getMandatesByBankAndAccount(
    bankId: BankId,
    accountId: AccountId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[MandateTrait]]] = Future {
    (MappedMandateProvider.getMandatesByBankAndAccount(bankId.value, accountId.value), callContext)
  }

  override def getActiveMandatesByBankAndAccount(
    bankId: BankId,
    accountId: AccountId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[MandateTrait]]] = Future {
    (MappedMandateProvider.getActiveMandatesByBankAndAccount(bankId.value, accountId.value), callContext)
  }

  override def createMandate(
    bankId: BankId,
    accountId: AccountId,
    customerId: String,
    mandateName: String,
    mandateReference: String,
    legalText: String,
    description: String,
    status: String,
    validFrom: Date,
    validTo: Date,
    createdByUserId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MandateTrait]] = Future {
    (MappedMandateProvider.createMandate(
      bankId.value, accountId.value, customerId, mandateName, mandateReference,
      legalText, description, status, validFrom, validTo, createdByUserId
    ), callContext)
  }

  override def updateMandate(
    mandateId: String,
    mandateName: String,
    mandateReference: String,
    legalText: String,
    description: String,
    status: String,
    validFrom: Date,
    validTo: Date,
    updatedByUserId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MandateTrait]] = Future {
    (MappedMandateProvider.updateMandate(
      mandateId, mandateName, mandateReference, legalText, description,
      status, validFrom, validTo, updatedByUserId
    ), callContext)
  }

  override def deleteMandate(
    mandateId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future {
    (MappedMandateProvider.deleteMandate(mandateId), callContext)
  }

  // Mandate Provision methods
  override def getMandateProvisionById(
    provisionId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MandateProvisionTrait]] = Future {
    (MappedMandateProvider.getMandateProvisionById(provisionId), callContext)
  }

  override def getMandateProvisionsByMandateId(
    mandateId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[MandateProvisionTrait]]] = Future {
    (MappedMandateProvider.getMandateProvisionsByMandateId(mandateId), callContext)
  }

  override def createMandateProvision(
    mandateId: String,
    provisionName: String,
    provisionDescription: String,
    legalReference: String,
    provisionType: String,
    conditions: String,
    signatoryRequirements: String,
    linkedViewId: String,
    linkedAbacRuleId: String,
    linkedChallengeType: String,
    isActive: Boolean,
    sortOrder: Int,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MandateProvisionTrait]] = Future {
    (MappedMandateProvider.createMandateProvision(
      mandateId, provisionName, provisionDescription, legalReference, provisionType,
      conditions, signatoryRequirements, linkedViewId, linkedAbacRuleId,
      linkedChallengeType, isActive, sortOrder
    ), callContext)
  }

  override def updateMandateProvision(
    provisionId: String,
    provisionName: String,
    provisionDescription: String,
    legalReference: String,
    provisionType: String,
    conditions: String,
    signatoryRequirements: String,
    linkedViewId: String,
    linkedAbacRuleId: String,
    linkedChallengeType: String,
    isActive: Boolean,
    sortOrder: Int,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MandateProvisionTrait]] = Future {
    (MappedMandateProvider.updateMandateProvision(
      provisionId, provisionName, provisionDescription, legalReference, provisionType,
      conditions, signatoryRequirements, linkedViewId, linkedAbacRuleId,
      linkedChallengeType, isActive, sortOrder
    ), callContext)
  }

  override def deleteMandateProvision(
    provisionId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future {
    (MappedMandateProvider.deleteMandateProvision(provisionId), callContext)
  }

  // Signatory Panel methods
  override def getSignatoryPanelById(
    panelId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[SignatoryPanelTrait]] = Future {
    (MappedMandateProvider.getSignatoryPanelById(panelId), callContext)
  }

  override def getSignatoryPanelsByMandateId(
    mandateId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[SignatoryPanelTrait]]] = Future {
    (MappedMandateProvider.getSignatoryPanelsByMandateId(mandateId), callContext)
  }

  override def createSignatoryPanel(
    mandateId: String,
    panelName: String,
    description: String,
    userIds: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[SignatoryPanelTrait]] = Future {
    (MappedMandateProvider.createSignatoryPanel(mandateId, panelName, description, userIds), callContext)
  }

  override def updateSignatoryPanel(
    panelId: String,
    panelName: String,
    description: String,
    userIds: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[SignatoryPanelTrait]] = Future {
    (MappedMandateProvider.updateSignatoryPanel(panelId, panelName, description, userIds), callContext)
  }

  override def deleteSignatoryPanel(
    panelId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future {
    (MappedMandateProvider.deleteSignatoryPanel(panelId), callContext)
  }

  // Trading Methods Implementation
  override def createTradingOffer(
    bankId: BankId,
    accountId: AccountId,
    offerType: String,
    assetCode: String,
    assetAmount: BigDecimal,
    priceCurrency: String,
    priceAmount: BigDecimal,
    settlementAccountId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TradingOffer]] = Future {
    // Extract audit fields from CallContext
    val userId = callContext.flatMap(_.user.map(_.userId)).getOrElse("SYSTEM")
    val consentId: Option[String] = None  // TODO: Extract from consent when available
    
    // Generate offer ID (auto-generated UUID following OBP design pattern)
    val offerId = randomUUID().toString

    // Create offer
    val offer = TradingOffer(
      offerId = offerId,
      offerType = offerType,
      status = "active",
      offerDetails = TradingOfferDetails(
        assetCode = assetCode,
        assetAmount = assetAmount,
        priceCurrency = priceCurrency,
        priceAmount = priceAmount,
        settlementAccountId = settlementAccountId,
        expiryDatetime = None,
        minimumFill = None
      ),
      accountInfo = TradingAccountInfo(
        bankId = bankId.value,
        accountId = accountId.value,
        viewId = "owner" // Default view
      ),
      executions = List.empty,
      userId = userId,
      consentId = consentId,
      createdAt = new Date(),
      updatedAt = new Date()
    )

    // Store offer
    tradingOffers.put(offerId, offer)

    (Full(offer), callContext)
  }

  override def getTradingOffer(
    offerId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TradingOffer]] = Future {
    val offer = Option(tradingOffers.get(offerId))
    (Box(offer), callContext)
  }

  override def cancelTradingOffer(
    offerId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TradingOffer]] = Future {
    val offer = Option(tradingOffers.get(offerId))

    offer match {
      case Some(o) =>
        val cancelledOffer = o.copy(
          status = "cancelled",
          updatedAt = new Date()
        )
        tradingOffers.put(offerId, cancelledOffer)
        (Full(cancelledOffer), callContext)
      case None =>
        (Empty, callContext)
    }
  }

  override def updateTradingOffer(
    offerId: String,
    priceAmount: Option[BigDecimal],
    expiryDatetime: Option[Date],
    minimumFill: Option[BigDecimal],
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TradingOffer]] = Future {
    val offer = Option(tradingOffers.get(offerId))

    offer match {
      case Some(o) =>
        // Update only the fields that are provided
        val updatedDetails = o.offerDetails.copy(
          priceAmount = priceAmount.getOrElse(o.offerDetails.priceAmount),
          expiryDatetime = expiryDatetime.orElse(o.offerDetails.expiryDatetime),
          minimumFill = minimumFill.orElse(o.offerDetails.minimumFill)
        )
        val updatedOffer = o.copy(
          offerDetails = updatedDetails,
          updatedAt = new Date()
        )
        tradingOffers.put(offerId, updatedOffer)
        (Full(updatedOffer), callContext)
      case None =>
        (Empty, callContext)
    }
  }

  override def getTradingOffers(
    bankId: BankId,
    accountId: AccountId,
    status: Option[String],
    offerType: Option[String],
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[TradingOffer]]] = Future {
    // Get all offers and filter by bankId and accountId
    val allOffers = tradingOffers.values().asScala.toList
    
    val filteredOffers = allOffers
      .filter(o => o.accountInfo.bankId == bankId.value && o.accountInfo.accountId == accountId.value)
      .filter(o => status.forall(_ == o.status))
      .filter(o => offerType.forall(_ == o.offerType))
      .sortBy(_.createdAt.getTime)(Ordering[Long].reverse) // Most recent first
    
    (Full(filteredOffers), callContext)
  }

  // Market Trading Methods Implementation
  override def createMarketOrder(
    bankId: BankId,
    accountId: AccountId,
    side: String,
    price: BigDecimal,
    quantity: BigDecimal,
    settlementAccountId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MarketOrder]] = Future {
    // Extract audit fields from CallContext
    val userId = callContext.flatMap(_.user.map(_.userId)).getOrElse("SYSTEM")
    val consentId: Option[String] = None  // TODO: Extract from consent when available
    
    // Generate order ID (auto-generated UUID following OBP design pattern)
    val orderId = randomUUID().toString

    // Create order
    val order = MarketOrder(
      orderId = orderId,
      side = side,
      price = price,
      quantity = quantity,
      accountId = settlementAccountId,
      status = "active",
      userId = userId,
      consentId = consentId,
      createdAt = new Date(),
      updatedAt = new Date()
    )

    // Store order
    marketOrders.put(orderId, order)

    (Full(order), callContext)
  }

  override def getMarketOrder(
    bankId: BankId,
    accountId: AccountId,
    orderId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MarketOrder]] = Future {
    val order = Option(marketOrders.get(orderId))
    (Box(order), callContext)
  }

  override def cancelMarketOrder(
    bankId: BankId,
    accountId: AccountId,
    orderId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MarketOrder]] = Future {
    val order = Option(marketOrders.get(orderId))

    order match {
      case Some(o) =>
        val cancelledOrder = o.copy(
          status = "cancelled",
          updatedAt = new Date()
        )
        marketOrders.put(orderId, cancelledOrder)
        (Full(cancelledOrder), callContext)
      case None =>
        (Empty, callContext)
    }
  }

  override def createMarketMatch(
    bankId: BankId,
    accountId: AccountId,
    orderId: String,
    counterOrderId: String,
    amount: BigDecimal,
    price: BigDecimal,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MarketMatch]] = Future {
    // Extract audit fields from CallContext
    val userId = callContext.flatMap(_.user.map(_.userId)).getOrElse("SYSTEM")
    val consentId: Option[String] = None  // TODO: Extract from consent when available
    
    // Generate match ID
    val matchId = randomUUID().toString

    // Create match
    val marketMatch = MarketMatch(
      matchId = matchId,
      orderId = orderId,
      counterOrderId = counterOrderId,
      amount = amount,
      price = price,
      userId = userId,
      consentId = consentId,
      createdAt = new Date()
    )

    // Store match
    marketMatches.put(matchId, marketMatch)

    // Create corresponding trade
    val tradeId = randomUUID().toString
    val trade = MarketTrade(
      tradeId = tradeId,
      buyOrderId = orderId,
      sellOrderId = counterOrderId,
      amount = amount,
      price = price,
      status = "pending",
      userId = userId,
      consentId = consentId,
      createdAt = new Date()
    )
    marketTrades.put(tradeId, trade)

    (Full(marketMatch), callContext)
  }

  override def getMarketTrade(
    bankId: BankId,
    accountId: AccountId,
    tradeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[MarketTrade]] = Future {
    val trade = Option(marketTrades.get(tradeId))
    (Box(trade), callContext)
  }

  override def requestSettlement(
    bankId: BankId,
    accountId: AccountId,
    tradeId: String,
    step: Option[String],
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Settlement]] = Future {
    // Extract audit fields from CallContext
    val userId = callContext.flatMap(_.user.map(_.userId)).getOrElse("SYSTEM")
    val consentId: Option[String] = None  // TODO: Extract from consent when available
    
    // Generate settlement ID
    val settlementId = randomUUID().toString

    // Create settlement
    val settlement = Settlement(
      settlementId = settlementId,
      tradeId = tradeId,
      step = step,
      status = "pending",
      userId = userId,
      consentId = consentId,
      createdAt = new Date(),
      completedAt = None
    )

    // Store settlement
    settlements.put(settlementId, settlement)

    (Full(settlement), callContext)
  }

  override def notifyDeposit(
    bankId: BankId,
    accountId: AccountId,
    txHash: String,
    from: String,
    to: String,
    amount: BigDecimal,
    confirmations: Int,
    requiredConfirmations: Int,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Deposit]] = Future {
    // Extract audit fields from CallContext
    val userId = callContext.flatMap(_.user.map(_.userId)).getOrElse("SYSTEM")
    val consentId: Option[String] = None  // TODO: Extract from consent when available
    
    // Generate deposit ID
    val depositId = randomUUID().toString

    // Determine status based on confirmations
    val status = if (confirmations >= requiredConfirmations) "confirmed" else "pending"

    // Create deposit
    val deposit = Deposit(
      depositId = depositId,
      txHash = txHash,
      from = from,
      to = to,
      amount = amount,
      confirmations = confirmations,
      requiredConfirmations = requiredConfirmations,
      status = status,
      nonce = None,  // TODO: Extract from blockchain transaction
      gasUsed = None,  // TODO: Extract from blockchain transaction receipt
      errorMessage = None,
      userId = userId,
      consentId = consentId,
      createdAt = new Date()
    )

    // Store deposit
    deposits.put(depositId, deposit)

    (Full(deposit), callContext)
  }

  override def requestWithdrawal(
    bankId: BankId,
    accountId: AccountId,
    settlementAccountId: String,
    amount: BigDecimal,
    address: String,
    requiredConfirmations: Int,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Withdrawal]] = Future {
    // Extract audit fields from CallContext
    val userId = callContext.flatMap(_.user.map(_.userId)).getOrElse("SYSTEM")
    val consentId: Option[String] = None  // TODO: Extract from consent when available
    
    // Generate withdrawal ID (auto-generated UUID following OBP design pattern)
    val withdrawalId = randomUUID().toString

    // Create withdrawal
    val withdrawal = Withdrawal(
      withdrawalId = withdrawalId,
      accountId = settlementAccountId,
      amount = amount,
      address = address,
      status = "pending",
      txHash = None,  // Will be set when transaction is submitted to blockchain
      confirmations = None,  // Will be updated as blockchain confirms
      requiredConfirmations = requiredConfirmations,
      nonce = None,  // TODO: Will be set when transaction is submitted
      gasUsed = None,  // TODO: Will be set after transaction is mined
      errorMessage = None,
      userId = userId,
      consentId = consentId,
      createdAt = new Date()
    )

    // Store withdrawal
    withdrawals.put(withdrawalId, withdrawal)

    (Full(withdrawal), callContext)
  }

  // TCC Payment Authorization Implementation
  override def createPaymentAuth(
    bankId: BankId,
    accountId: AccountId,
    tradeId: String,
    buyerAccountId: String,
    sellerAccountId: String,
    amountFiat: BigDecimal,
    currency: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[PaymentAuth]] = Future {
    // Extract audit fields from CallContext
    val userId = callContext.flatMap(_.user.map(_.userId)).getOrElse("SYSTEM")
    val consentId: Option[String] = None  // TODO: Extract from consent when available
    
    // Generate auth ID (auto-generated UUID following OBP design pattern)
    val authId = randomUUID().toString
    val now = new Date()

    // Create payment authorization in PREAUTH state
    val auth = PaymentAuth(
      authId = authId,
      tradeId = tradeId,
      buyerAccountId = buyerAccountId,
      sellerAccountId = sellerAccountId,
      amountFiat = amountFiat,
      currency = currency,
      state = "PREAUTH",  // Initial state: funds are frozen
      holdId = None,  // TODO: P5 integration - create account hold
      errorMessage = None,
      userId = userId,
      consentId = consentId,
      createdAt = now,
      updatedAt = now
    )

    // Store payment authorization
    paymentAuths.put(authId, auth)

    (Full(auth), callContext)
  }

  override def capturePaymentAuth(
    bankId: BankId,
    accountId: AccountId,
    authId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[PaymentAuth]] = Future {
    // Retrieve existing authorization
    Option(paymentAuths.get(authId)) match {
      case Some(auth) =>
        // Validate state transition: only PREAUTH can be captured
        auth.state match {
          case "PREAUTH" =>
            // Update to CAPTURED state (funds are actually deducted)
            val updatedAuth = auth.copy(
              state = "CAPTURED",
              updatedAt = new Date()
            )
            paymentAuths.put(authId, updatedAuth)
            (Full(updatedAuth), callContext)
          
          case "CAPTURED" =>
            (Failure(ErrorMessages.PaymentAuthAlreadyCaptured), callContext)
          
          case "RELEASED" =>
            (Failure(ErrorMessages.InvalidPaymentAuthState + " Cannot capture a released authorization."), callContext)
          
          case "FAILED" =>
            (Failure(ErrorMessages.InvalidPaymentAuthState + " Cannot capture a failed authorization."), callContext)
          
          case _ =>
            (Failure(ErrorMessages.InvalidPaymentAuthState), callContext)
        }
      
      case None =>
        (Failure(ErrorMessages.PaymentAuthNotFound), callContext)
    }
  }

  override def releasePaymentAuth(
    bankId: BankId,
    accountId: AccountId,
    authId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[PaymentAuth]] = Future {
    // Retrieve existing authorization
    Option(paymentAuths.get(authId)) match {
      case Some(auth) =>
        // Validate state transition: PREAUTH or CAPTURED can be released
        auth.state match {
          case "PREAUTH" | "CAPTURED" =>
            // Update to RELEASED state (funds are unfrozen/refunded)
            val updatedAuth = auth.copy(
              state = "RELEASED",
              updatedAt = new Date()
            )
            paymentAuths.put(authId, updatedAuth)
            (Full(updatedAuth), callContext)
          
          case "RELEASED" =>
            (Failure(ErrorMessages.PaymentAuthAlreadyReleased), callContext)
          
          case "FAILED" =>
            (Failure(ErrorMessages.InvalidPaymentAuthState + " Cannot release a failed authorization."), callContext)
          
          case _ =>
            (Failure(ErrorMessages.InvalidPaymentAuthState), callContext)
        }
      
      case None =>
        (Failure(ErrorMessages.PaymentAuthNotFound), callContext)
    }
  }

  override def getPaymentAuth(
    bankId: BankId,
    accountId: AccountId,
    authId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[PaymentAuth]] = Future {
    // Retrieve payment authorization
    Option(paymentAuths.get(authId)) match {
      case Some(auth) => (Full(auth), callContext)
      case None => (Failure(ErrorMessages.PaymentAuthNotFound), callContext)
    }
  }

}

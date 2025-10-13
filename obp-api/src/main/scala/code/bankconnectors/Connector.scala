package code.bankconnectors

import _root_.akka.http.scaladsl.model.HttpMethod
import code.api.attributedefinition.AttributeDefinition
import code.api.util.APIUtil.{OBPReturnType, _}
import code.api.util.ErrorMessages._
import code.api.util._
import code.api.{APIFailure, APIFailureNewStyle}
import code.atmattribute.AtmAttribute
import code.bankattribute.BankAttribute
import code.bankconnectors.akka.AkkaConnector_vDec2018
import code.bankconnectors.cardano.CardanoConnector_vJun2025
import code.bankconnectors.ethereum.EthereumConnector_vSept2025
import code.bankconnectors.rabbitmq.RabbitMQConnector_vOct2024
import code.bankconnectors.rest.RestConnector_vMar2019
import code.bankconnectors.storedprocedure.StoredProcedureConnector_vDec2019
import code.model.dataAccess.BankAccountRouting
import code.users.UserAttribute
import code.util.Helper._
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.{CustomerAndAttribute, GetProductsParam, InBoundTrait, ProductCollectionItemsTree}
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums._
import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
import net.liftweb.common._
import net.liftweb.json
import net.liftweb.json.{Formats, JObject, JValue}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

import java.util.Date
import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.runtime.universe.{MethodSymbol, typeOf}

/*
So we can switch between different sources of resources e.g.
- Mapper ORM for connecting to RDBMS (via JDBC) https://www.assembla.com/wiki/show/liftweb/Mapper
- MongoDB
- RabbitMq
etc.

Note: We also have individual providers for resources like Branches and Products.
Probably makes sense to have more targeted providers like this.

Could consider a Map of ("resourceType" -> "provider") - this could tell us which tables we need to schemify (for list in Boot), whether or not to
 initialise MongoDB etc. resourceType might be sub devided to allow for different account types coming from different internal APIs, MQs.
 */

object Connector extends SimpleInjector {
  // An object is a class that has exactly one instance. It is created lazily when it is referenced, like a lazy val.
  // As a top-level value, an object is a singleton.
  // As a member of an enclosing class or as a local value, it behaves exactly like a lazy val.
  // Previously the right hand part was surrounded by Functions.lazyValue function
  val nameToConnector: Map[String, Connector] = Map(
    "mapped" -> LocalMappedConnector,
    "akka_vDec2018" -> AkkaConnector_vDec2018,
    "rest_vMar2019" -> RestConnector_vMar2019,
    "stored_procedure_vDec2019" -> StoredProcedureConnector_vDec2019,
    "rabbitmq_vOct2024" -> RabbitMQConnector_vOct2024,
    "cardano_vJun2025" -> CardanoConnector_vJun2025,
    "ethereum_vSept2025" -> EthereumConnector_vSept2025,
    // this proxy connector only for unit test, can set connector=proxy in test.default.props, but never set it in default.props
    "proxy" -> ConnectorUtils.proxyConnector,
    // internal is the dynamic connector, the developers can upload the source code and override connector method themselves.
    "internal" -> InternalConnector.instance
  )

  def getConnectorInstance(connectorVersion: String): Connector = {
    connectorVersion match {
      case "star" => StarConnector
      case k => nameToConnector.get(k)
        .getOrElse(throw new RuntimeException(s"$InvalidConnector Current Input is $k"))
    }
  }

  val connector = new Inject(buildOne _) {}

  def buildOne: Connector = {
    val connectorProps = code.api.Constant.CONNECTOR.openOrThrowException(s"$MandatoryPropertyIsNotSet The missing props is 'connector'")
    getConnectorInstance(connectorProps)

  }

  def extractAdapterResponse[T: Manifest](responseJson: String, inBoundMapping: Box[JObject]): Box[T] = {
    val clazz = manifest[T].runtimeClass
    val boxJValue: Box[Box[JValue]] = tryo {
      val jValue = inBoundMapping match {
        case Full(m) => JsonUtils.buildJson(json.parse(responseJson), m)
        case _ => json.parse(responseJson)
      }
      if (ErrorMessage.isErrorMessage(jValue)) {
        val ErrorMessage(code, message) = jValue.extract[ErrorMessage]
        ParamFailure(message, Empty, Empty, APIFailure(message, code))
      } else {
        Box !! jValue
      }
    } ~> APIFailureNewStyle(s"INTERNAL-$InvalidJsonFormat The Json body should be the ${clazz.getName} ", 400)

    boxJValue match {
      case Full(Full(jValue)) =>
        tryo {
          jValue.extract[T](CustomJsonFormats.nullTolerateFormats, manifest[T])
        } ~> APIFailureNewStyle(s"INTERNAL-$InvalidJsonFormat The Json body should be the ${clazz.getName} ", 400)

      case Full(failure) => failure.asInstanceOf[Box[T]]
      case empty: EmptyBox => empty
    }
  }
}

trait Connector extends MdcLoggable {
  implicit val formats: Formats = CustomJsonFormats.nullTolerateFormats

  val messageDocs = ArrayBuffer[MessageDoc]()
  protected implicit val nameOfConnector = Connector.getClass.getSimpleName

  //Move all the cache ttl to Connector, all the sub-connectors share the same cache.

  protected val bankTTL = getSecondsCache("getBank")
  protected val banksTTL = getSecondsCache("getBanks")
  protected val userTTL = getSecondsCache("getUser")
  protected val accountTTL = getSecondsCache("getAccount")
  protected val accountsTTL = getSecondsCache("getAccounts")
  protected val transactionTTL = getSecondsCache("getTransaction")
  protected val transactionsTTL = getSecondsCache("getTransactions")
  protected val transactionRequests210TTL = getSecondsCache("getTransactionRequests210")
  protected val counterpartiesTTL = getSecondsCache("getCounterparties")
  protected val counterpartyByCounterpartyIdTTL = getSecondsCache("getCounterpartyByCounterpartyId")
  protected val counterpartyTrait = getSecondsCache("getCounterpartyTrait")
  protected val customersByUserIdTTL = getSecondsCache("getCustomersByUserId")
  protected val memoryCounterpartyTTL = getSecondsCache("createMemoryCounterparty")
  protected val memoryTransactionTTL = getSecondsCache("createMemoryTransaction")
  protected val branchesTTL = getSecondsCache("getBranches")
  protected val branchTTL = getSecondsCache("getBranch")
  protected val atmsTTL = getSecondsCache("getAtms")
  protected val atmTTL = getSecondsCache("getAtm")
  protected val statusOfCheckbookOrders = getSecondsCache("getStatusOfCheckbookOrdersFuture")
  protected val statusOfCreditcardOrders = getSecondsCache("getStatusOfCreditCardOrderFuture")
  protected val bankAccountsBalancesTTL = getSecondsCache("getBankAccountsBalances")


  /**
   * trait Connector declared methods, name to MethodSymbol.
   * these methods:
   *  1. not abstract
   *  2. public
   *  3. no override
   *  4. is not $default$
   */
  protected lazy val connectorMethods: Map[String, MethodSymbol] = {
    val tp = typeOf[Connector]
    val result = tp.decls
      .withFilter(_.isPublic)
      .withFilter(_.isMethod)
      .map(m =>(m.name.decodedName.toString.trim, m.asMethod))
      .collect{
        case kv @(name, method)
          if method.overrides.isEmpty &&
            method.paramLists.nonEmpty &&
            method.paramLists.head.nonEmpty &&
            !name.contains("$default$") => kv
      }.toMap
    result
  }
  /**
   * current connector instance implemented Connector method,
   * methodName to method
   */
  protected lazy val implementedMethods: Map[String, MethodSymbol] = {
    val tp = ReflectUtils.getType(this)
    val result = tp.members
        .withFilter(_.isPublic)
        .withFilter(_.isMethod)
        .map(m =>(m.name.decodedName.toString.trim, m.asMethod))
        .collect{
          case kv @(name, method)
            if method.overrides.nonEmpty &&
            method.paramLists.nonEmpty &&
            method.paramLists.head.nonEmpty &&
            method.owner != typeOf[Connector] &&
            !name.contains("$default$") => kv
        }.toMap
    connectorMethods ++ result // result put after ++ to make sure methods of Connector's subtype be kept when name conflict.
  }

  def callableMethods: Map[String, MethodSymbol] = implementedMethods

  protected implicit def boxToTuple[T](box: Box[(T, Option[CallContext])]): (Box[T], Option[CallContext]) =
    (box.map(_._1), box.flatMap(_._2))

  protected implicit def tupleToBoxTuple[T](tuple: (Box[T], Option[CallContext])): Box[(T, Option[CallContext])] =
    tuple._1.map(it => (it, tuple._2))

  protected implicit def tupleToBox[T](tuple: (Box[T], Option[CallContext])): Box[T] = tuple._1


  /**
    * convert original return type future to OBPReturnType
    *
    * @param future original return type
    * @tparam T future success value type
    * @return OBPReturnType type future
    */
  protected implicit def futureReturnTypeToOBPReturnType[T](future: Future[Box[(T, Option[CallContext])]]): OBPReturnType[Box[T]] =
    future map boxToTuple

  /**
    * convert OBPReturnType return type to original future type
    *
    * @param value OBPReturnType return type
    * @tparam T future success value type
    * @return original future type
    */
  protected implicit def OBPReturnTypeToFutureReturnType[T](value: OBPReturnType[Box[T]]): Future[Box[(T, Option[CallContext])]] =
    value map tupleToBoxTuple

  private val futureTimeOut: Duration = 20 seconds
  /**
    * convert OBPReturnType return type to Tuple type
    *
    * @param value Tuple return type
    * @tparam T future success value type
    * @return original future tuple box type
    */
  protected implicit def OBPReturnTypeToTupleBox[T](value: OBPReturnType[Box[T]]): (Box[T], Option[CallContext]) =
    Await.result(value, futureTimeOut)

  /**
    * convert OBPReturnType return type to Box Tuple type
    *
    * @param value Box Tuple return type
    * @tparam T future success value type
    * @return original future box tuple type
    */
  protected implicit def OBPReturnTypeToBoxTuple[T](value: OBPReturnType[Box[T]]):  Box[(T, Option[CallContext])] =
    Await.result(
      OBPReturnTypeToFutureReturnType(value), 30 seconds
    )

  /**
    * convert OBPReturnType return type to Box value
    *
    * @param value Box Tuple return type
    * @tparam T future success value type
    * @return original future box value
    */
  protected implicit def OBPReturnTypeToBox[T](value: OBPReturnType[Box[T]]): Box[T] =
    Await.result(
      value.map(_._1),
      30 seconds
    )

  protected def convertToTuple[T](callContext: Option[CallContext])(inbound: Box[InBoundTrait[T]]): (Box[T], Option[CallContext]) = {
    val boxedResult = inbound match {
      case Full(in) if (in.status.hasNoError) => Full(in.data)
      case Full(inbound) if (inbound.status.hasError) => {
        val errorMessage = s"CoreBank - Status.errorCode: ${inbound.status.errorCode}. Error.details:" + inbound.status.backendMessages
        val errorCode: Int = try {
          inbound.status.errorCode.toInt
        } catch {
          case _: Throwable => 400
        }
        ParamFailure(errorMessage, Empty, Empty, APIFailure(errorMessage, errorCode))
      }
      case failureOrEmpty: Failure => failureOrEmpty
    }

    (boxedResult, callContext)
  }


  private def setUnimplementedError(methodName:String) : String = {
    NotImplemented + methodName + s" Please check `Get Message Docs`endpoint and implement the process `obp.$methodName` in Adapter side."
  }

  def getAdapterInfo(callContext: Option[CallContext]) : Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = Future{Failure(setUnimplementedError(nameOf(getAdapterInfo _)))}
  
  def validateAndCheckIbanNumber(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[IbanChecker]] = Future{(Failure(setUnimplementedError(nameOf(validateAndCheckIbanNumber _))), callContext)}

  // Gets current challenge level for transaction request
  // challenge threshold. Level at which challenge is created and needs to be answered
  // before we attempt to create a transaction on the south side
  // The Currency is EUR. Connector implementations may convert the value to the transaction request currency.
  // Connector implementation may well provide dynamic response
  def getChallengeThreshold(
                             bankId: String,
                             accountId: String,
                             viewId: String,
                             transactionRequestType: String,
                             currency: String,
                             userId: String,
                             username: String,
                             callContext: Option[CallContext]
                           ): OBPReturnType[Box[AmountOfMoney]] =Future{(Failure(setUnimplementedError(nameOf(getChallengeThreshold _))), callContext)}

  //TODO. WIP
  def shouldRaiseConsentChallenge(
    bankId: String,
    accountId: String,
    viewId: String,
    consentRequestType: String,
    userId: String,
    username: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[(Boolean, String)]] = Future {
    (Failure(setUnimplementedError(nameOf(shouldRaiseConsentChallenge _))), callContext)
  }

  def getPaymentLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    transactionRequestType: String,
    currency: String,
    userId: String,
    username: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[AmountOfMoney]] = Future{(Failure(setUnimplementedError(nameOf(getPaymentLimit _))), callContext)}


  //Gets current charge level for transaction request
  def getChargeLevel(bankId: BankId,
                     accountId: AccountId,
                     viewId: ViewId,
                     userId: String,
                     username: String,
                     transactionRequestType: String,
                     currency: String,
                     callContext:Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] =Future{(Failure(setUnimplementedError(nameOf(getChargeLevel _))), callContext)}

  //Gets current charge level for transaction request
  def getChargeLevelC2(bankId: BankId,
                       accountId: AccountId,
                       viewId: ViewId,
                       userId: String,
                       username: String,
                       transactionRequestType: String,
                       currency: String,
                       amount: String,
                       toAccountRoutings: List[AccountRouting],
                       customAttributes: List[CustomAttribute],
                       callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = Future{(Failure(setUnimplementedError(nameOf(getChargeLevelC2 _))), callContext)}
  
  // Initiate creating a challenge for transaction request and returns an id of the challenge
  def createChallenge(bankId: BankId, 
                      accountId: AccountId, 
                      userId: String, 
                      transactionRequestType: TransactionRequestType, 
                      transactionRequestId: String,
                      scaMethod: Option[SCA], 
                      callContext: Option[CallContext]) : OBPReturnType[Box[String]]= Future{(Failure(setUnimplementedError(nameOf(createChallenge _))), callContext)}  
  // Initiate creating a challenges for transaction request and returns an ids of the challenges
  def createChallenges(bankId: BankId, 
                      accountId: AccountId, 
                      userIds: List[String], 
                      transactionRequestType: TransactionRequestType, 
                      transactionRequestId: String,
                      scaMethod: Option[SCA], 
                      callContext: Option[CallContext]) : OBPReturnType[Box[List[String]]]= Future{(Failure(setUnimplementedError(nameOf(createChallenges _))), callContext)}

  // now, we try to share the same challenges for obp payments, berlin group payments, and berlin group consents
  def createChallengesC2(
    userIds: List[String],
    challengeType: ChallengeType.Value,
    transactionRequestId: Option[String],
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],//Only use for BerlinGroup Now
    consentId: Option[String], // Note: consentId and transactionRequestId are exclusive here.
    authenticationMethodId: Option[String],
    callContext: Option[CallContext]) : OBPReturnType[Box[List[ChallengeTrait]]]= Future{(Failure(setUnimplementedError(nameOf(createChallengesC2 _))), callContext)}

  // now, we try to share the same challenges for obp payments, berlin group payments, berlin group consents and signing baskets
  def createChallengesC3(
    userIds: List[String],
    challengeType: ChallengeType.Value,
    transactionRequestId: Option[String],
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],//Only use for BerlinGroup Now
    consentId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    basketId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    authenticationMethodId: Option[String],
    callContext: Option[CallContext]) : OBPReturnType[Box[List[ChallengeTrait]]]= Future{(Failure(setUnimplementedError(nameOf(createChallengesC3 _))), callContext)}

  @deprecated("Please use @validateChallengeAnswerV2 instead ","01.07.2024")
  def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(validateChallengeAnswer _))), callContext)}
  
  // Validates an answer for a challenge and returns if the answer is correct or not
  def validateChallengeAnswerV2(challengeId: String, suppliedAnswer: String, suppliedAnswerType:SuppliedAnswerType.Value, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = 
    Future{(Failure(setUnimplementedError(nameOf(validateChallengeAnswerV2 _))), callContext)}
  
  def allChallengesSuccessfullyAnswered(
    bankId: BankId,
    accountId: AccountId,
    transReqId: TransactionRequestId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]]= Future{(Failure(setUnimplementedError(nameOf(allChallengesSuccessfullyAnswered _))), callContext)}

  @deprecated("Please use @validateChallengeAnswerC4 instead ","04.07.2024")
  def validateChallengeAnswerC2(
    transactionRequestId: Option[String],
    consentId: Option[String],
    challengeId: String,
    hashOfSuppliedAnswer: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ChallengeTrait]] = Future{(Failure(setUnimplementedError(nameOf(validateChallengeAnswerC2 _))), callContext)}

  @deprecated("Please use @validateChallengeAnswerC5 instead ","04.07.2024")
  def validateChallengeAnswerC3(
    transactionRequestId: Option[String],
    consentId: Option[String],
    basketId: Option[String],
    challengeId: String,
    hashOfSuppliedAnswer: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ChallengeTrait]] = Future{(Failure(setUnimplementedError(nameOf(validateChallengeAnswerC3 _))), callContext)}
  
  def validateChallengeAnswerC4(
    transactionRequestId: Option[String],
    consentId: Option[String],
    challengeId: String,
    suppliedAnswer: String,
    suppliedAnswerType: SuppliedAnswerType.Value,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ChallengeTrait]] = Future{(Failure(setUnimplementedError(nameOf(validateChallengeAnswerC4 _))), callContext)}

  def validateChallengeAnswerC5(
    transactionRequestId: Option[String],
    consentId: Option[String],
    basketId: Option[String],
    challengeId: String,
    suppliedAnswer: String,
    suppliedAnswerType: SuppliedAnswerType.Value,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ChallengeTrait]] = Future{(Failure(setUnimplementedError(nameOf(validateChallengeAnswerC5 _))), callContext)}

  def getChallengesByTransactionRequestId(transactionRequestId: String, callContext:  Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getChallengesByTransactionRequestId _))), callContext)}
  
  def getChallengesByConsentId(consentId: String, callContext:  Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getChallengesByConsentId _))), callContext)}
  def getChallengesByBasketId(basketId: String, callContext:  Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getChallengesByBasketId _))), callContext)}

  def getChallenge(challengeId: String, callContext:  Option[CallContext]): OBPReturnType[Box[ChallengeTrait]] = Future{(Failure(setUnimplementedError(nameOf(getChallenge _))), callContext)}
  
  //gets a particular bank handled by this connector
  def getBankLegacy(bankId : BankId, callContext: Option[CallContext]) : Box[(Bank, Option[CallContext])] = Failure(setUnimplementedError(nameOf(getBankLegacy _)))

  def getBank(bankId : BankId, callContext: Option[CallContext]) : Future[Box[(Bank, Option[CallContext])]] = Future(Failure(setUnimplementedError(nameOf(getBank _))))

  //gets banks handled by this connector
  def getBanksLegacy(callContext: Option[CallContext]): Box[(List[Bank], Option[CallContext])] = Failure(setUnimplementedError(nameOf(getBanksLegacy _)))

  def getBanks(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = Future{(Failure(setUnimplementedError(nameOf(getBanks _))))}

  /**
   * please see @getBankAccountsForUser
   */
  def getBankAccountsForUserLegacy(provider: String, username:String, callContext: Option[CallContext]) : Box[(List[InboundAccount], Option[CallContext])] = Failure(setUnimplementedError(nameOf(getBankAccountsForUserLegacy _)))

  /**
    * Get Accounts from cbs, this method is mainly used for onboarding Bank Customer to OBP.
   *  If it is CBS connector: 
   *   the input is the username + userAuthContext (we can get it from callContext),
   *    userAuthContext can be CustomerNumber, AccountNumber, TelephoneNumber ..., any values which CBS need to identify a bank Customer
   *  the output is the CBS accounts belong to the user:
   *    So far the InboundAccount.BankId, InboundAccount.AccountId and InboundAccount.viewsToGenerate are mandatory, OBP need these to 
   *    create view and grant the account access.
   *  If it is Mapped connector:  
   *    OBP will return all the accounts from accountHolder
    * @param username username of the user.
    * @param callContext inside, should contain the proper values for CBS to identify a bank Customer 
    * @return all the accounts, get from Main Frame.
    */
  def getBankAccountsForUser(provider: String, username:String, callContext: Option[CallContext]) : Future[Box[(List[InboundAccount], Option[CallContext])]] = Future{
    Failure(setUnimplementedError(nameOf(getBankAccountsForUser _)))
  }

  /**
    * This method is for checking external User via connector
    * @param username
    * @param password
    * @return
    */
  def checkExternalUserCredentials(username: String, password: String, callContext: Option[CallContext]): Box[InboundExternalUser] = Failure(setUnimplementedError(nameOf(checkExternalUserCredentials _)))
  
  /**
    * This method is for checking external User via connector
    * @param username
    * @return
    */
  def checkExternalUserExists(username: String, callContext: Option[CallContext]): Box[InboundExternalUser] = Failure(setUnimplementedError(nameOf(checkExternalUserExists _)))

  
  //This one just added the callContext in parameters.
  def getBankAccountLegacy(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : Box[(BankAccount, Option[CallContext])]= Failure(setUnimplementedError(nameOf(getBankAccountLegacy _)))
  
  def getBankAccountByIban(iban : String, callContext: Option[CallContext]) : OBPReturnType[Box[BankAccount]]= Future{(Failure(setUnimplementedError(nameOf(getBankAccountByIban _))),callContext)}
  def getBankAccountByRoutingLegacy(bankId: Option[BankId], scheme : String, address : String, callContext: Option[CallContext]) : Box[(BankAccount, Option[CallContext])]= Failure(setUnimplementedError(nameOf(getBankAccountByRoutingLegacy _)))
  def getBankAccountByRouting(bankId: Option[BankId], scheme : String, address : String, callContext: Option[CallContext]) : OBPReturnType[Box[BankAccount]]= Future{(Failure(setUnimplementedError(nameOf(getBankAccountByRouting _))), callContext)}
  def getAccountRoutingsByScheme(bankId: Option[BankId], scheme : String, callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccountRouting]]] = Future{(Failure(setUnimplementedError(nameOf(getAccountRoutingsByScheme _))),callContext)}
  def getAccountRouting(bankId: Option[BankId], scheme : String, address : String, callContext: Option[CallContext]) : Box[(BankAccountRouting, Option[CallContext])]= Failure(setUnimplementedError(nameOf(getAccountRouting _)))

  def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : OBPReturnType[Box[List[BankAccount]]]= Future{(Failure(setUnimplementedError(nameOf(getBankAccounts _))), callContext)}

  def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : OBPReturnType[Box[AccountsBalances]]= Future{(Failure(setUnimplementedError(nameOf(getBankAccountsBalances _))), callContext)}
  
  def getBankAccountBalances(bankIdAccountId: BankIdAccountId, callContext: Option[CallContext]) : OBPReturnType[Box[AccountBalances]]= Future{(Failure(setUnimplementedError(nameOf(getBankAccountBalances _))), callContext)}

  def getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[(List[CoreAccount], Option[CallContext])] =
    Failure(setUnimplementedError(nameOf(getCoreBankAccountsLegacy _)))
  def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]]=
    Future{Failure(setUnimplementedError(nameOf(getCoreBankAccounts _)))}


  def getBankAccountsWithAttributes(bankId: BankId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[FastFirehoseAccount]]] =
    Future{(Failure(setUnimplementedError(nameOf(getBankAccountsWithAttributes _))), callContext)}
    
  def getBankSettlementAccounts(bankId: BankId, callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccount]]] = Future{(Failure(setUnimplementedError(nameOf(getBankSettlementAccounts _))), callContext)}

  def getBankAccountsHeldLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[List[AccountHeld]]= Failure(setUnimplementedError(nameOf(getBankAccountsHeldLegacy _)))
  def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : OBPReturnType[Box[List[AccountHeld]]]= Future {(Failure(setUnimplementedError(nameOf(getBankAccountsHeld _))), callContext)}
  def getAccountsHeld(bankId: BankId, user: User, callContext: Option[CallContext]): OBPReturnType[Box[List[BankIdAccountId]]]= Future {(Failure(setUnimplementedError(nameOf(getAccountsHeld _))), callContext)}
  def getAccountsHeldByUser(user: User, callContext: Option[CallContext]): OBPReturnType[Box[List[BankIdAccountId]]]= Future {(Failure(setUnimplementedError(nameOf(getAccountsHeld _))), callContext)}

  def checkBankAccountExistsLegacy(bankId : BankId, accountId : AccountId, callContext: Option[CallContext] = None) : Box[(BankAccount, Option[CallContext])]= Failure(setUnimplementedError(nameOf(checkBankAccountExistsLegacy _)))
  def checkBankAccountExists(bankId : BankId, accountId : AccountId, callContext: Option[CallContext] = None) : OBPReturnType[Box[(BankAccount)]] = Future {(Failure(setUnimplementedError(nameOf(checkBankAccountExists _))), callContext)}
  def getBankAccountFromCounterparty(counterparty: CounterpartyTrait, isOutgoingAccount: Boolean, callContext: Option[CallContext]) : OBPReturnType[Box[(BankAccount)]] = Future {(Failure(setUnimplementedError(nameOf(getBankAccountFromCounterparty _))), callContext)}
  
  def getBankAccountByNumber(bankId : Option[BankId], accountNumber : String, callContext: Option[CallContext]) : OBPReturnType[Box[(BankAccount)]] = Future {(Failure(setUnimplementedError(nameOf(getBankAccountByNumber _))), callContext)}

  // This method handles external bank accounts that may not exist in our database.
  // If the account is not found, we create an in-memory account using counterparty information for payment processing.
  //TODO understand more about this method.
  def getOtherBankAccountByNumber(bankId : Option[BankId], accountNumber : String, counterparty: Option[CounterpartyTrait], callContext: Option[CallContext]) : OBPReturnType[Box[(BankAccount)]] = Future {(Failure(setUnimplementedError(nameOf(getOtherBankAccountByNumber _))), callContext)}

  def getBankAccountByRoutings(bankAccountRoutings: BankAccountRoutings, callContext: Option[CallContext]) : OBPReturnType[Box[(BankAccount)]] = Future {(Failure(setUnimplementedError(nameOf(getBankAccountByRoutings _))), callContext)}
  
  def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[Counterparty]] = Future {(Failure(setUnimplementedError(nameOf(checkBankAccountExists _))), callContext)}

  def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[List[Counterparty]]]= Future{Failure(setUnimplementedError(nameOf(createChallengesC3 _)))}

  def getCounterpartyTrait(bankId: BankId, accountId: AccountId, couterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]]= Future{(Failure(setUnimplementedError(nameOf(getCounterpartyTrait _))), callContext)}

  def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = Future{(Failure(setUnimplementedError(nameOf(getCounterpartyByCounterpartyId _))), callContext)}
  
  def deleteCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteCounterpartyByCounterpartyId _))), callContext)}


  /**
    * get Counterparty by iban (OtherAccountRoutingAddress field in MappedCounterparty table)
    * This is a helper method that assumes OtherAccountRoutingScheme=IBAN
    */
  def getCounterpartyByIban(iban: String, callContext: Option[CallContext]) : OBPReturnType[Box[CounterpartyTrait]] = Future {(Failure(setUnimplementedError(nameOf(getCounterpartyByIban _))), callContext)}

  def getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) : OBPReturnType[Box[CounterpartyTrait]] = Future {(Failure(setUnimplementedError(nameOf(getCounterpartyByIbanAndBankAccountId _))), callContext)}
  
  def getOrCreateCounterparty(
    name: String,
    description: String,
    currency: String,
    createdByUserId: String,
    thisBankId: String,
    thisAccountId: String,
    thisViewId: String,
    other_bank_routing_scheme: String,
    other_bank_routing_address: String,
    other_branch_routing_scheme: String,
    other_branch_routing_address: String,
    other_account_routing_scheme: String,
    other_account_routing_address: String,
    other_account_secondary_routing_scheme: String,
    other_account_secondary_routing_address: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CounterpartyTrait]] = Future {(Failure(setUnimplementedError(nameOf(getOrCreateCounterparty _))), callContext)}  
  
  def getCounterpartyByRoutings(
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String,
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CounterpartyTrait]] = Future {(Failure(setUnimplementedError(nameOf(getCounterpartyByRoutings _))), callContext)}

  def getCounterpartiesLegacy(thisBankId: BankId, thisAccountId: AccountId, viewId :ViewId, callContext: Option[CallContext] = None): Box[(List[CounterpartyTrait], Option[CallContext])]= Failure(setUnimplementedError(nameOf(getCounterpartiesLegacy _)))

  def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): OBPReturnType[Box[List[CounterpartyTrait]]] = Future {(Failure(setUnimplementedError(nameOf(getCounterparties _))), callContext)}

  //TODO, here is a problem for return value `List[Transaction]`, this is a normal class, not a trait. It is a big class,
  // it contains thisAccount(BankAccount object) and otherAccount(Counterparty object)
  def getTransactionsLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Box[(List[Transaction], Option[CallContext])]= Failure(setUnimplementedError(nameOf(getTransactionsLegacy _)))

  def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): OBPReturnType[Box[List[Transaction]]] = Future{(Failure(setUnimplementedError(nameOf(getTransactions _))), callContext)}
  
  def getTransactionsCore(bankId: BankId, accountId: AccountId, queryParams:  List[OBPQueryParam] = Nil, callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionCore]]] = Future{(Failure(setUnimplementedError(nameOf(getTransactionsCore _))), callContext)}

  def getCountOfTransactionsFromAccountToCounterparty(fromBankId: BankId, fromAccountId: AccountId, counterpartyId: CounterpartyId, fromDate: Date, toDate:Date, callContext: Option[CallContext]) :OBPReturnType[Box[Int]] = Future{(Failure(setUnimplementedError(nameOf(getCountOfTransactionsFromAccountToCounterparty _))), callContext: Option[CallContext])}

  def getSumOfTransactionsFromAccountToCounterparty(fromBankId: BankId, fromAccountId: AccountId, counterpartyId: CounterpartyId, fromDate: Date, toDate:Date, callContext: Option[CallContext]):OBPReturnType[Box[AmountOfMoney]] = Future{(Failure(setUnimplementedError(nameOf(getSumOfTransactionsFromAccountToCounterparty _))), callContext: Option[CallContext])}

  def getTransactionLegacy(bankId: BankId, accountId : AccountId, transactionId : TransactionId, callContext: Option[CallContext] = None): Box[(Transaction, Option[CallContext])] = Failure(setUnimplementedError(nameOf(getTransactionLegacy _)))

  def getTransaction(bankId: BankId, accountId : AccountId, transactionId : TransactionId, callContext: Option[CallContext] = None): OBPReturnType[Box[Transaction]] = Future{(Failure(setUnimplementedError(nameOf(getTransaction _))), callContext)}

  def getPhysicalCardsForUser(user : User, callContext: Option[CallContext] = None) : OBPReturnType[Box[List[PhysicalCard]]] = Future{(Failure(setUnimplementedError(nameOf(getPhysicalCardsForUser _))), callContext)}

  def getPhysicalCardForBank(bankId: BankId, cardId: String,  callContext:Option[CallContext]) : OBPReturnType[Box[PhysicalCardTrait]] = Future{(Failure(setUnimplementedError(nameOf(getPhysicalCardForBank _))), callContext)}
  
  def getPhysicalCardByCardNumber(bankCardNumber: String,  callContext:Option[CallContext]) : OBPReturnType[Box[PhysicalCardTrait]] = Future{(Failure(setUnimplementedError(nameOf(getPhysicalCardByCardNumber _))), callContext)}
  
  def deletePhysicalCardForBank(bankId: BankId, cardId: String,  callContext:Option[CallContext]) : OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deletePhysicalCardForBank _))), callContext)}

  def getPhysicalCardsForBank(bank: Bank, user : User, queryParams: List[OBPQueryParam], callContext:Option[CallContext]) : OBPReturnType[Box[List[PhysicalCard]]] = Future{(Failure(setUnimplementedError(nameOf(getPhysicalCardsForBank _))), callContext)}

  def createPhysicalCardLegacy(
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
    callContext: Option[CallContext]
  ): Box[PhysicalCard] = Failure(setUnimplementedError("createPhysicalCardLegacy"))

  def createPhysicalCard(
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
    callContext: Option[CallContext]
  ): OBPReturnType[Box[PhysicalCard]] = Future{(Failure{setUnimplementedError("createPhysicalCard")}, callContext)}

  def updatePhysicalCard(
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
  ): OBPReturnType[Box[PhysicalCardTrait]] = Future{(Failure{setUnimplementedError(nameOf(updatePhysicalCard _))}, callContext)}

  def makePaymentv210(fromAccount: BankAccount,
                      toAccount: BankAccount,
                      transactionRequestId: TransactionRequestId,
                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                      amount: BigDecimal,
                      description: String,
                      transactionRequestType: TransactionRequestType,
                      chargePolicy: String,
                      callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]]= Future{(Failure(setUnimplementedError(nameOf(makePaymentv210 _))), callContext)}

  def saveDoubleEntryBookTransaction(doubleEntryTransaction: DoubleEntryTransaction,
                                     callContext: Option[CallContext]): OBPReturnType[Box[DoubleEntryTransaction]]= Future{(Failure(setUnimplementedError(nameOf(saveDoubleEntryBookTransaction _))), callContext)}

  def getDoubleEntryBookTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId,
                                     callContext: Option[CallContext]): OBPReturnType[Box[DoubleEntryTransaction]]= Future{(Failure(setUnimplementedError(nameOf(saveDoubleEntryBookTransaction _))), callContext)}
  def getBalancingTransaction(transactionId: TransactionId,
                              callContext: Option[CallContext]): OBPReturnType[Box[DoubleEntryTransaction]]= Future{(Failure(setUnimplementedError(nameOf(getBalancingTransaction _))), callContext)}
  
  // Set initial status
  def getStatus(challengeThresholdAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal, transactionRequestType: TransactionRequestType, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequestStatus.Value]] =
    Future{(Failure(setUnimplementedError(nameOf(getStatus _))), callContext)}

  // Get the charge level value
  def getChargeValue(chargeLevelAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal, callContext: Option[CallContext]): OBPReturnType[Box[String]] = 
    Future{(Failure(setUnimplementedError(nameOf(getChargeValue _))), callContext)}


  /**
    *
    * @param initiator
    * @param viewId
    * @param fromAccount
    * @param toAccount
    * @param transactionRequestType Support Types: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY
    * @param transactionRequestCommonBody Body from http request: should have common fields
    * @param chargePolicy  SHARED, SENDER, RECEIVER
    * @param detailsPlain This is the details / body of the request (contains all fields in the body)
    * @return Always create a new Transaction Request in mapper, and return all the fields
    */


  def createTransactionRequestv210(initiator: User,
                                   viewId: ViewId,
                                   fromAccount: BankAccount,
                                   toAccount: BankAccount,
                                   transactionRequestType: TransactionRequestType,
                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                   detailsPlain: String,
                                   chargePolicy: String,
                                   challengeType: Option[String],
                                   scaMethod: Option[SCA],
                                   callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = Future{(Failure(setUnimplementedError(nameOf(createTransactionRequestv210 _))), callContext)}


  /**
    *
    * @param initiator
    * @param viewId
    * @param fromAccount
    * @param toAccount
    * @param transactionRequestType Support Types: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY
    * @param transactionRequestCommonBody Body from http request: should have common fields
    * @param chargePolicy  SHARED, SENDER, RECEIVER
    * @param detailsPlain This is the details / body of the request (contains all fields in the body)
    * @return Always create a new Transaction Request in mapper, and return all the fields
    */


  def createTransactionRequestv400(initiator: User,
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
                                   callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = Future{(Failure(setUnimplementedError(nameOf(createTransactionRequestv400 _))), callContext)}


  def createTransactionRequestSepaCreditTransfersBGV1(
    initiator: Option[User],
    paymentServiceType: PaymentServiceTypes,
    transactionRequestType: TransactionRequestTypes,
    transactionRequestBody: SepaCreditTransfersBerlinGroupV13,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TransactionRequestBGV1]] = Future{(Failure(setUnimplementedError(nameOf(createTransactionRequestSepaCreditTransfersBGV1 _))), callContext)}

  def createTransactionRequestPeriodicSepaCreditTransfersBGV1(
    initiator: Option[User],
    paymentServiceType: PaymentServiceTypes,
    transactionRequestType: TransactionRequestTypes,
    transactionRequestBody: PeriodicSepaCreditTransfersBerlinGroupV13,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TransactionRequestBGV1]] = Future{(Failure(setUnimplementedError(nameOf(createTransactionRequestPeriodicSepaCreditTransfersBGV1 _))), callContext)}
  
  def notifyTransactionRequest(fromAccount: BankAccount, toAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequestStatusValue]] =
    Future{(Failure(setUnimplementedError(nameOf(notifyTransactionRequest _))), callContext)}

  def saveTransactionRequestTransaction(transactionRequestId: TransactionRequestId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]]=
    Future{(Failure(setUnimplementedError(nameOf(saveTransactionRequestTransaction _))), callContext)}

  def saveTransactionRequestChallenge(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{Failure(setUnimplementedError(nameOf(saveTransactionRequestChallenge _)))}

  def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future {Failure(setUnimplementedError(nameOf(saveTransactionRequestStatusImpl _)))}
  
  def getTransactionRequests210(initiator : User, fromAccount : BankAccount, callContext: Option[CallContext]) : Box[(List[TransactionRequest], Option[CallContext])] = Failure(setUnimplementedError(nameOf(getTransactionRequests210 _)))
  
  def getTransactionRequestImpl(transactionRequestId: TransactionRequestId, callContext: Option[CallContext]): Box[(TransactionRequest, Option[CallContext])] =
    Failure(setUnimplementedError(nameOf(getTransactionRequestImpl _)))


  def getTransactionRequestTypes(initiator : User, fromAccount : BankAccount, callContext: Option[CallContext]) : Box[(List[TransactionRequestType], Option[CallContext])] =Failure(setUnimplementedError(nameOf(createChallengesC3 _)))
  
  def createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]) : OBPReturnType[Box[TransactionRequest]] = 
    Future{(Failure(setUnimplementedError(nameOf(createTransactionAfterChallengeV210 _))), callContext)}

  /*
    non-standard calls --do not make sense in the regular context but are used for e.g. tests
  */
  
  
  def updateBankAccount(
                         bankId: BankId,
                         accountId: AccountId,
                         accountType: String,
                         accountLabel: String,
                         branchId: String,
                         accountRoutings: List[AccountRouting],
                         callContext: Option[CallContext]
                       ): OBPReturnType[Box[BankAccount]] = Future{(Failure(setUnimplementedError(nameOf(updateBankAccount _))), callContext)}
  

  
  def createBankAccount(
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
                       ): OBPReturnType[Box[BankAccount]] = Future{(Failure(setUnimplementedError(nameOf(createBankAccount _))), callContext)}


  def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{Failure(setUnimplementedError(nameOf(updateAccountLabel _)))}

  def getProducts(bankId : BankId, params: List[GetProductsParam], callContext: Option[CallContext]): OBPReturnType[Box[List[Product]]] = Future {Failure(setUnimplementedError(nameOf(getProducts _)))}

  def getProduct(bankId : BankId, productCode : ProductCode, callContext: Option[CallContext]): OBPReturnType[Box[Product]] = Future {Failure(setUnimplementedError(nameOf(getProduct _)))}
  
  def getProductTree(bankId : BankId, productCode : ProductCode, callContext: Option[CallContext]): OBPReturnType[Box[List[Product]]] = Future {Failure(setUnimplementedError(nameOf(getProduct _)))}

  //Note: this is a temporary way for compatibility
  //It is better to create the case class for all the connector methods
  def createOrUpdateBranch(branch: BranchT, callContext: Option[CallContext]): OBPReturnType[Box[BranchT]] = Future{Failure(setUnimplementedError(nameOf(createOrUpdateBranch _)))}

  def createOrUpdateBank(
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
                        ): Box[Bank] = Failure(setUnimplementedError(nameOf(createOrUpdateBank _)))
  
  def createOrUpdateAtm(atm: AtmT,  callContext: Option[CallContext]): OBPReturnType[Box[AtmT]] = Future{Failure(setUnimplementedError(nameOf(createOrUpdateAtm _)))}
  
  def deleteAtm(atm: AtmT,  callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{Failure(setUnimplementedError(nameOf(deleteAtm _)))}
  
  def createSystemLevelEndpointTag(operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{Failure(setUnimplementedError(nameOf(createSystemLevelEndpointTag _)))}
  
  def updateSystemLevelEndpointTag(endpointTagId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{Failure(setUnimplementedError(nameOf(updateSystemLevelEndpointTag _)))}
  
  def createBankLevelEndpointTag(bankId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{Failure(setUnimplementedError(nameOf(createBankLevelEndpointTag _)))}
  
  def updateBankLevelEndpointTag(bankId:String, endpointTagId:String, operationId:String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{Failure(setUnimplementedError(nameOf(updateBankLevelEndpointTag _)))}
  
  def getSystemLevelEndpointTag(operationId: String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{Failure(setUnimplementedError(nameOf(getSystemLevelEndpointTag _)))}
  
  def getBankLevelEndpointTag(bankId: String, operationId: String, tagName:String, callContext: Option[CallContext]): OBPReturnType[Box[EndpointTagT]] = Future{Failure(setUnimplementedError(nameOf(getBankLevelEndpointTag _)))}

  def getEndpointTagById(endpointTagId : String, callContext: Option[CallContext]) : OBPReturnType[Box[EndpointTagT]] = Future(Failure(setUnimplementedError(nameOf(getEndpointTagById _))))
  
  def deleteEndpointTag(endpointTagId : String, callContext: Option[CallContext]) : OBPReturnType[Box[Boolean]] = Future(Failure(setUnimplementedError(nameOf(deleteEndpointTag _))))
  
  def getSystemLevelEndpointTags(operationId : String, callContext: Option[CallContext]) : OBPReturnType[Box[List[EndpointTagT]]] = Future(Failure(setUnimplementedError(nameOf(getSystemLevelEndpointTags _))))
  
  def getBankLevelEndpointTags(bankId:String, operationId : String, callContext: Option[CallContext]) : OBPReturnType[Box[List[EndpointTagT]]] = Future(Failure(setUnimplementedError(nameOf(getBankLevelEndpointTags _))))
  
  def createOrUpdateProduct(
                             bankId : String,
                             code : String,
                             parentProductCode : Option[String],
                             name : String,
                             category : String,
                             family : String,
                             superFamily : String,
                             moreInfoUrl : String,
                             termsAndConditionsUrl : String,
                             details : String,
                             description : String,
                             metaLicenceId : String,
                             metaLicenceName : String,
                             callContext: Option[CallContext]
                           ): OBPReturnType[Box[Product]] = Future{Failure(setUnimplementedError(nameOf(createOrUpdateProduct _)))}
  
  def createOrUpdateProductFee(
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
  ): OBPReturnType[Box[ProductFeeTrait]]= Future(Failure(setUnimplementedError(nameOf(createOrUpdateProductFee _))))

  def getProductFeesFromProvider(
    bankId: BankId,
    productCode: ProductCode,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[ProductFeeTrait]]] = Future(Failure(setUnimplementedError(nameOf(getProductFeesFromProvider _))))

  def getProductFeeById(
    productFeeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[ProductFeeTrait]] = Future(Failure(setUnimplementedError(nameOf(getProductFeeById _))))

  def deleteProductFee(
    productFeeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future(Failure(setUnimplementedError(nameOf(deleteProductFee _))))
    
  
  def createOrUpdateFXRate(
                            bankId: String,
                            fromCurrencyCode: String,
                            toCurrencyCode: String,
                            conversionValue: Double,
                            inverseConversionValue: Double,
                            effectiveDate: Date,
                            callContext: Option[CallContext]
                          ): OBPReturnType[Box[FXRate]] = Future(Failure(setUnimplementedError(nameOf(createOrUpdateFXRate _))))

  def getBranch(bankId : BankId, branchId: BranchId, callContext: Option[CallContext]) :  Future[Box[(BranchT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(getBranch _)))
  }

  def getBranches(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Future[Box[(List[BranchT], Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(getBranches _)))
  }
  
  def getAtm(bankId : BankId, atmId: AtmId, callContext: Option[CallContext]) : Future[Box[(AtmT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(getAtm _)))
  }

  def updateAtmSupportedLanguages(bankId : BankId, atmId: AtmId, supportedLanguages: List[String], callContext: Option[CallContext]) : Future[Box[(AtmT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(updateAtmSupportedLanguages _)))
  }
  
  def updateAtmSupportedCurrencies(bankId : BankId, atmId: AtmId, supportedCurrencies: List[String], callContext: Option[CallContext]) : Future[Box[(AtmT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(updateAtmSupportedCurrencies _)))
  }
   
  def updateAtmAccessibilityFeatures(bankId : BankId, atmId: AtmId, accessibilityFeatures: List[String], callContext: Option[CallContext]) : Future[Box[(AtmT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(updateAtmAccessibilityFeatures _)))
  }

  def updateAtmServices(bankId : BankId, atmId: AtmId, supportedCurrencies: List[String], callContext: Option[CallContext]) : Future[Box[(AtmT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(updateAtmServices _)))
  }

  def updateAtmNotes(bankId : BankId, atmId: AtmId, notes: List[String], callContext: Option[CallContext]) : Future[Box[(AtmT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(updateAtmNotes _)))
  }

  def updateAtmLocationCategories(bankId : BankId, atmId: AtmId, locationCategories: List[String], callContext: Option[CallContext]) : Future[Box[(AtmT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(updateAtmLocationCategories _)))
  }

  def getAtms(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Future[Box[(List[AtmT], Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(getAtms _)))
  }

  def getAllAtms(callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Future[Box[(List[AtmT], Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(getAllAtms _)))
  }
  
  def getCurrentCurrencies(bankId: BankId, callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] = Future{Failure(setUnimplementedError(nameOf(getCurrentCurrencies _)))}
  
  def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String, callContext: Option[CallContext]): Box[FXRate] = Failure(setUnimplementedError(nameOf(getCurrentFxRate _)))

  /**
   * There is no mapped implimetaion . it is only use for CBS mode at the moment.
   */
  def createTransactionAfterChallengev300(
                                           initiator: User,
                                           fromAccount: BankAccount,
                                           transReqId: TransactionRequestId,
                                           transactionRequestType: TransactionRequestType,
                                           callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = Future{(Failure(setUnimplementedError(nameOf(createTransactionAfterChallengev300 _))), callContext)}

  def makePaymentv300(
                       initiator: User,
                       fromAccount: BankAccount,
                       toAccount: BankAccount,
                       toCounterparty: CounterpartyTrait,
                       transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                       transactionRequestType: TransactionRequestType,
                       chargePolicy: String,
                       callContext: Option[CallContext]): Future[Box[(TransactionId, Option[CallContext])]] = Future{Failure(setUnimplementedError(nameOf(makePaymentv300 _)))}

  def createTransactionRequestv300(
                                    initiator: User,
                                    viewId: ViewId,
                                    fromAccount: BankAccount,
                                    toAccount: BankAccount,
                                    toCounterparty: CounterpartyTrait,
                                    transactionRequestType: TransactionRequestType,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                    detailsPlain: String,
                                    chargePolicy: String,
                                    callContext: Option[CallContext]): Future[Box[(TransactionRequest, Option[CallContext])]]  = Future{Failure(setUnimplementedError(nameOf(createTransactionRequestv300 _)))}

  def makePaymentV400(transactionRequest: TransactionRequest,
                      reasons: Option[List[TransactionRequestReason]],
                      callContext: Option[CallContext]): Future[Box[(TransactionId, Option[CallContext])]] = Future {
    Failure(setUnimplementedError(nameOf(makePaymentV400 _)))
  }
  
  def cancelPaymentV400(transactionId: TransactionId,
                        callContext: Option[CallContext]): OBPReturnType[Box[CancelPayment]] = Future {
    (Failure(setUnimplementedError(nameOf(cancelPaymentV400 _))), callContext)
  }
  
  /**
    * get transaction request type charges
    */
  def getTransactionRequestTypeCharges(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestTypes: List[TransactionRequestType], callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestTypeCharge]]] =
    Future{(Failure(setUnimplementedError(nameOf(getTransactionRequestTypeCharges _))), callContext)}

  def createCounterparty(
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
                          isBeneficiary:Boolean,
                          bespoke: List[CounterpartyBespoke],
                          callContext: Option[CallContext] = None): Box[(CounterpartyTrait, Option[CallContext])] = Failure(setUnimplementedError(nameOf(createCounterparty _)))

  def checkCounterpartyExists(
    name: String,
    thisBankId: String,
    thisAccountId: String,
    thisViewId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CounterpartyTrait]]= Future{(Failure(setUnimplementedError(nameOf(checkCounterpartyExists _))), callContext)}

  def checkCustomerNumberAvailable(
    bankId: BankId,
    customerNumber: String, 
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(checkCustomerNumberAvailable _))), callContext)}

  def checkAgentNumberAvailable(
    bankId: BankId,
    agentNumber: String, 
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(checkAgentNumberAvailable _))), callContext)}

  def createCustomer(
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
                      callContext: Option[CallContext],
                    ): OBPReturnType[Box[Customer]] = Future{(Failure(setUnimplementedError(nameOf(createCustomer _))), callContext)}

  def createCustomerC2(
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
                        callContext: Option[CallContext],
                      ): OBPReturnType[Box[Customer]] = Future{(Failure(setUnimplementedError(nameOf(createCustomerC2 _))), callContext)}

  def updateCustomerScaData(customerId: String, 
                            mobileNumber: Option[String], 
                            email: Option[String],
                            customerNumber: Option[String],
                            callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = 
    Future{(Failure(setUnimplementedError(nameOf(updateCustomerScaData _))), callContext)}
    
  def getAgentByAgentId(
    agentId : String, 
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Agent]] = Future{(Failure(setUnimplementedError(nameOf(getAgentByAgentId _))), callContext)}
  
       
  def getAgentByAgentNumber(
    bankId: BankId,
    agentNumber: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Agent]] = Future{(Failure(setUnimplementedError(nameOf(getAgentByAgentNumber _))), callContext)}
  
     
  def getAgents(
    bankId : String, 
    queryParams: List[OBPQueryParam], 
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[Agent]]] = Future{(Failure(setUnimplementedError(nameOf(getAgents _))), callContext)}
  
  
  def updateAgentStatus(
    agentId: String,
    isPendingAgent: Boolean,
    isConfirmedAgent: Boolean,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Agent]] = Future{(Failure(setUnimplementedError(nameOf(updateAgentStatus _))), callContext)}

  def createAgent(
    bankId: String,
    legalName : String,
    mobileNumber : String,
    agentNumber : String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Agent]] = Future{(Failure(setUnimplementedError(nameOf(createAgent _))), callContext)}

  def updateCustomerCreditData(customerId: String,
                               creditRating: Option[String],
                               creditSource: Option[String],
                               creditLimit: Option[AmountOfMoney],
                               callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = 
    Future{(Failure(setUnimplementedError(nameOf(updateCustomerCreditData _))), callContext)}

  def updateCustomerGeneralData(customerId: String,
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
                                callContext: Option[CallContext]): OBPReturnType[Box[Customer]] =
    Future {
      (Failure(setUnimplementedError(nameOf(updateCustomerGeneralData _))), callContext)
    }

  def getCustomersByUserId(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer],Option[CallContext])]] = Future{Failure(setUnimplementedError(nameOf(getCustomersByUserId _)))}

  def getCustomerByCustomerIdLegacy(customerId: String, callContext: Option[CallContext]): Box[(Customer,Option[CallContext])]= Failure(setUnimplementedError(nameOf(getCustomerByCustomerIdLegacy _)))

  def getCustomerByCustomerId(customerId: String, callContext: Option[CallContext]): Future[Box[(Customer,Option[CallContext])]] = Future{Failure(setUnimplementedError(nameOf(getCustomerByCustomerId _)))}

  def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] =
    Future{Failure(setUnimplementedError(nameOf(getCustomerByCustomerNumber _)))}

  def getCustomerAddress(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAddress]]] =
    Future{(Failure(setUnimplementedError(nameOf(getCustomerAddress _))), callContext)}

  def createCustomerAddress(customerId: String,
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
                            callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = Future{(Failure(setUnimplementedError(nameOf(createCustomerAddress _))), callContext)}

  def updateCustomerAddress(customerAddressId: String,
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
                            callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = Future{(Failure(setUnimplementedError(nameOf(updateCustomerAddress _))), callContext)}
  def deleteCustomerAddress(customerAddressId : String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteCustomerAddress _))), callContext)}

  def createTaxResidence(customerId : String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[TaxResidence]] = Future{(Failure(setUnimplementedError(nameOf(createTaxResidence _))), callContext)}

  def getTaxResidence(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[TaxResidence]]] = Future{(Failure(setUnimplementedError(nameOf(getTaxResidence _))), callContext)}

  def deleteTaxResidence(taxResourceId : String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteTaxResidence _))), callContext)}

  def getCustomersAtAllBanks(callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): OBPReturnType[Box[List[Customer]]] = Future{Failure(setUnimplementedError(nameOf(getCustomersAtAllBanks _)))}
  
  def getCustomers(bankId : BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Future[Box[List[Customer]]] = Future{Failure(setUnimplementedError(nameOf(getCustomers _)))}
  
  def getCustomersByCustomerPhoneNumber(bankId : BankId, phoneNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[List[Customer]]] = Future{(Failure(setUnimplementedError(nameOf(getCustomersByCustomerPhoneNumber _))), callContext)}

  def getCustomersByCustomerLegalName(bankId: BankId, legalName: String, callContext: Option[CallContext]): OBPReturnType[Box[List[Customer]]] = Future{(Failure(setUnimplementedError(nameOf(getCustomersByCustomerLegalName _))), callContext)}
  def getCheckbookOrders(
                          bankId: String,
                          accountId: String,
                          callContext: Option[CallContext]
                        ): Future[Box[(CheckbookOrdersJson, Option[CallContext])]] = Future{Failure(setUnimplementedError(nameOf(getCheckbookOrders _)))}

  def getStatusOfCreditCardOrder(
                                  bankId: String,
                                  accountId: String,
                                  callContext: Option[CallContext]
                                ): Future[Box[(List[CardObjectJson], Option[CallContext])]] = Future{Failure(setUnimplementedError(nameOf(getStatusOfCreditCardOrder _)))}

  //This method is normally used in obp side, so it has the default mapped implementation  
  def createUserAuthContext(userId: String,
                            key: String,
                            value: String,
                            callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContext]] =Future{(Failure(setUnimplementedError(nameOf(createUserAuthContext _))), callContext)}
  
  //This method is normally used in obp side, so it has the default mapped implementation  
  def createUserAuthContextUpdate(userId: String,
                                  key: String,
                                  value: String,
                                  callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContextUpdate]] =Future{(Failure(setUnimplementedError(nameOf(createUserAuthContextUpdate _))), callContext)}

  //This method is normally used in obp side, so it has the default mapped implementation   
  def deleteUserAuthContexts(userId: String,
                             callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =Future{(Failure(setUnimplementedError(nameOf(deleteUserAuthContexts _))), callContext)}

  //This method is normally used in obp side, so it has the default mapped implementation  
  def deleteUserAuthContextById(userAuthContextId: String,
                                callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =Future{(Failure(setUnimplementedError(nameOf(deleteUserAuthContextById _))), callContext)}
  //This method is normally used in obp side, so it has the default mapped implementation  
  def getUserAuthContexts(userId : String,
                          callContext: Option[CallContext]): OBPReturnType[Box[List[UserAuthContext]]] =Future{(Failure(setUnimplementedError(nameOf(getUserAuthContexts _))), callContext)}

  def createOrUpdateProductAttribute(
                                      bankId: BankId,
                                      productCode: ProductCode,
                                      productAttributeId: Option[String],
                                      name: String,
                                      productAttributeType: ProductAttributeType.Value,
                                      value: String,
                                      isActive: Option[Boolean],
                                      callContext: Option[CallContext]
                                    ): OBPReturnType[Box[ProductAttribute]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateProductAttribute _))), callContext)}

  def createOrUpdateBankAttribute(bankId: BankId,
                                  bankAttributeId: Option[String],
                                  name: String,
                                  bankAttributeType: BankAttributeType.Value,
                                  value: String,
                                  isActive: Option[Boolean],
                                  callContext: Option[CallContext]
                                 ): OBPReturnType[Box[BankAttribute]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateBankAttribute _))), callContext)}

  def createOrUpdateAtmAttribute(bankId: BankId,
                                 atmId: AtmId,
                                 atmAttributeId: Option[String],
                                 name: String,
                                 atmAttributeType: AtmAttributeType.Value,
                                 value: String,
                                 isActive: Option[Boolean],
                                 callContext: Option[CallContext]
                                ): OBPReturnType[Box[AtmAttribute]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateAtmAttribute _))), callContext)}
  
  def getBankAttributesByBank(bankId: BankId, callContext: Option[CallContext]): OBPReturnType[Box[List[BankAttributeTrait]]] =
    Future{(Failure(setUnimplementedError(nameOf(getBankAttributesByBank _))), callContext)}

  def getAtmAttributesByAtm(bank: BankId, atm: AtmId, callContext: Option[CallContext]): OBPReturnType[Box[List[AtmAttribute]]] =
    Future{(Failure(setUnimplementedError(nameOf(getAtmAttributesByAtm _))), callContext)}

  def getBankAttributeById(bankAttributeId: String,
                           callContext: Option[CallContext]
                          ): OBPReturnType[Box[BankAttribute]] = Future{(Failure(setUnimplementedError(nameOf(getBankAttributeById _))), callContext)}

  def getAtmAttributeById(atmAttributeId: String, 
                          callContext: Option[CallContext]): OBPReturnType[Box[AtmAttribute]] = 
    Future{(Failure(setUnimplementedError(nameOf(getAtmAttributeById _))), callContext)}
  
  def getProductAttributeById(
                               productAttributeId: String,
                               callContext: Option[CallContext]
                             ): OBPReturnType[Box[ProductAttribute]] = Future{(Failure(setUnimplementedError(nameOf(getProductAttributeById _))), callContext)}

  def getProductAttributesByBankAndCode(
                                         bank: BankId,
                                         productCode: ProductCode,
                                         callContext: Option[CallContext]
                                       ): OBPReturnType[Box[List[ProductAttribute]]] =
    Future{(Failure(setUnimplementedError(nameOf(getProductAttributesByBankAndCode _))), callContext)}

  def deleteBankAttribute(bankAttributeId: String,
                          callContext: Option[CallContext]
                         ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteBankAttribute _))), callContext)}
  
  def deleteAtmAttribute(atmAttributeId: String,
                         callContext: Option[CallContext]
                         ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteAtmAttribute _))), callContext)}
  
  def deleteAtmAttributesByAtmId(atmId: AtmId,
                                 callContext: Option[CallContext]
                                ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteAtmAttributesByAtmId _))), callContext)}
  
  def deleteProductAttribute(
                              productAttributeId: String,
                              callContext: Option[CallContext]
                            ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteProductAttribute _))), callContext)}


  def getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountAttribute]] = Future{(Failure(setUnimplementedError(nameOf(getAccountAttributeById _))), callContext)}
  def getTransactionAttributeById(transactionAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionAttribute]] = Future{(Failure(setUnimplementedError(nameOf(getTransactionAttributeById _))), callContext)}
  
  def createOrUpdateAccountAttribute(
                                      bankId: BankId,
                                      accountId: AccountId,
                                      productCode: ProductCode,
                                      productAttributeId: Option[String],
                                      name: String,
                                      accountAttributeType: AccountAttributeType.Value,
                                      value: String,
                                      productInstanceCode: Option[String],
                                      callContext: Option[CallContext]
                                    ): OBPReturnType[Box[AccountAttribute]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateAccountAttribute _))), callContext)}

  def createOrUpdateCustomerAttribute(bankId: BankId, 
                                      customerId: CustomerId,
                                      customerAttributeId: Option[String],
                                      name: String,
                                      attributeType: CustomerAttributeType.Value,
                                      value: String,
                                      callContext: Option[CallContext]
  ): OBPReturnType[Box[CustomerAttribute]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateCustomerAttribute _))), callContext)}

  def createOrUpdateAttributeDefinition(bankId: BankId,
                                        name: String,
                                        category: AttributeCategory.Value,
                                        `type`: AttributeType.Value,
                                        description: String,
                                        alias: String,
                                        canBeSeenOnViews: List[String],
                                        isActive: Boolean,
                                        callContext: Option[CallContext]
                                       ): OBPReturnType[Box[AttributeDefinition]] =
    Future {
      (Failure(setUnimplementedError(nameOf(createOrUpdateAttributeDefinition _))), callContext)
    }

  def deleteAttributeDefinition(attributeDefinitionId: String,
                                   category: AttributeCategory.Value,
                                   callContext: Option[CallContext]
                                  ): OBPReturnType[Box[Boolean]] =
    Future {
      (Failure(setUnimplementedError(nameOf(deleteAttributeDefinition _))), callContext)
    }

  def getAttributeDefinition(category: AttributeCategory.Value,
                                callContext: Option[CallContext]
                               ): OBPReturnType[Box[List[AttributeDefinition]]] =
    Future {
      (Failure(setUnimplementedError(nameOf(getAttributeDefinition _))), callContext)
    }
  
  def getUserAttributes(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[UserAttribute]]] = 
    Future{(Failure(setUnimplementedError(nameOf(getUserAttributes _))), callContext)}   
  
  def getPersonalUserAttributes(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[UserAttribute]]] = 
    Future{(Failure(setUnimplementedError(nameOf(getPersonalUserAttributes _))), callContext)}  
    
  def getNonPersonalUserAttributes(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[UserAttribute]]] = 
    Future{(Failure(setUnimplementedError(nameOf(getNonPersonalUserAttributes _))), callContext)}   
  
  def getUserAttributesByUsers(userIds: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[UserAttribute]]] = 
    Future{(Failure(setUnimplementedError(nameOf(getUserAttributesByUsers _))), callContext)}  
  
  def createOrUpdateUserAttribute(
    userId: String,
    userAttributeId: Option[String],
    name: String,
    attributeType: UserAttributeType.Value,
    value: String,
    isPersonal: Boolean,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[UserAttribute]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateUserAttribute _))), callContext)} 
  
  def deleteUserAttribute(
    userAttributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteUserAttribute _))), callContext)} 
  
  def createOrUpdateTransactionAttribute(
    bankId: BankId,
    transactionId: TransactionId,
    transactionAttributeId: Option[String],
    name: String,
    attributeType: TransactionAttributeType.Value,
    value: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TransactionAttribute]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateTransactionAttribute _))), callContext)}
  
  
  def createAccountAttributes(bankId: BankId,
                              accountId: AccountId,
                              productCode: ProductCode,
                              accountAttributes: List[ProductAttribute],
                              productInstanceCode: Option[String],
                              callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = 
    Future{(Failure(setUnimplementedError(nameOf(createAccountAttributes _))), callContext)} 
  
  def getAccountAttributesByAccount(bankId: BankId,
                                    accountId: AccountId,
                                    callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = 
    Future{(Failure(setUnimplementedError(nameOf(getAccountAttributesByAccount _))), callContext)}
  
  def getAccountAttributesByAccountCanBeSeenOnView(bankId: BankId,
                                                   accountId: AccountId,
                                                   viewId: ViewId,
                                                   callContext: Option[CallContext]
                                                  ): OBPReturnType[Box[List[AccountAttribute]]] = 
    Future{(Failure(setUnimplementedError(nameOf(getAccountAttributesByAccountCanBeSeenOnView _))), callContext)}

  def getAccountAttributesByAccountsCanBeSeenOnView(accounts: List[BankIdAccountId],
                                                    viewId: ViewId,
                                                    callContext: Option[CallContext]
                                                   ): OBPReturnType[Box[List[AccountAttribute]]] =
    Future{(Failure(setUnimplementedError(nameOf(getAccountAttributesByAccountsCanBeSeenOnView _))), callContext)}
  
  def getTransactionAttributesByTransactionsCanBeSeenOnView(bankId: BankId,
                                                            transactionIds: List[TransactionId],
                                                            viewId: ViewId,
                                                            callContext: Option[CallContext]
                                                           ): OBPReturnType[Box[List[TransactionAttribute]]] =
    Future{(Failure(setUnimplementedError(nameOf(getTransactionAttributesByTransactionsCanBeSeenOnView _))), callContext)}

  def getCustomerAttributes(
    bankId: BankId,
    customerId: CustomerId,
    callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAttribute]]] =
    Future{(Failure(setUnimplementedError(nameOf(getCustomerAttributes _))), callContext)}

  /**
   * get CustomerAttribute according name and values
   * @param bankId CustomerAttribute must belongs the bank
   * @param nameValues key is attribute name, value is attribute values.
   *                   CustomerAttribute name must equals name,
   *                   CustomerAttribute value must be one of values
   * @param callContext
   * @return filtered CustomerAttribute.customerId
   */
  def getCustomerIdsByAttributeNameValues(
    bankId: BankId,
    nameValues: Map[String, List[String]],
    callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] =
    Future{(Failure(setUnimplementedError(nameOf(getCustomerIdsByAttributeNameValues _))), callContext)}

  def getCustomerAttributesForCustomers(
    customers: List[Customer],
    callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAndAttribute]]] =
    Future{(Failure(setUnimplementedError(nameOf(getCustomerAttributesForCustomers _))), callContext)}

  def getTransactionIdsByAttributeNameValues(
    bankId: BankId,
    nameValues: Map[String, List[String]],
    callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] =
    Future{(Failure(setUnimplementedError(nameOf(getTransactionIdsByAttributeNameValues _))), callContext)}
  
  def getTransactionAttributes(
    bankId: BankId,
    transactionId: TransactionId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[TransactionAttribute]]] = Future{(Failure(setUnimplementedError(nameOf(getTransactionAttributes _))), callContext)}  
  
  def getTransactionAttributesCanBeSeenOnView(bankId: BankId,
                                              transactionId: TransactionId,
                                              viewId: ViewId, 
                                              callContext: Option[CallContext]
  ): OBPReturnType[Box[List[TransactionAttribute]]] = Future{(Failure(setUnimplementedError(nameOf(getTransactionAttributesCanBeSeenOnView _))), callContext)}

  def getCustomerAttributeById(
    customerAttributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CustomerAttribute]]  =
    Future{(Failure(setUnimplementedError(nameOf(getCustomerAttributeById _))), callContext)}
  
  
  def createOrUpdateCardAttribute(
                                  bankId: Option[BankId],
                                  cardId: Option[String],
                                  cardAttributeId: Option[String],
                                  name: String,
                                  cardAttributeType: CardAttributeType.Value,
                                  value: String,
                                  callContext: Option[CallContext]
                                ): OBPReturnType[Box[CardAttribute]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateCardAttribute _))), callContext)}

  def getCardAttributeById(cardAttributeId: String, callContext:Option[CallContext]): OBPReturnType[Box[CardAttribute]] = Future{(Failure(setUnimplementedError(nameOf(getCardAttributeById _))), callContext)}
  
  def getCardAttributesFromProvider(cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CardAttribute]]] = Future{(Failure(setUnimplementedError(nameOf(getCardAttributesFromProvider _))), callContext)}

  def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId,
                                                  callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getTransactionRequestAttributesFromProvider _))), callContext)}

  def getTransactionRequestAttributes(bankId: BankId,
                                      transactionRequestId: TransactionRequestId,
                                      callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getTransactionRequestAttributes _))), callContext)}

  def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                     transactionRequestId: TransactionRequestId,
                                                     viewId: ViewId,
                                                     callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getTransactionRequestAttributesCanBeSeenOnView _))), callContext)}

  def getTransactionRequestAttributeById(transactionRequestAttributeId: String,
                                         callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequestAttributeTrait]] = Future{(Failure(setUnimplementedError(nameOf(getTransactionRequestAttributeById _))), callContext)}

  def getTransactionRequestIdsByAttributeNameValues(
    bankId: BankId, 
    params: Map[String, List[String]], 
    isPersonal: Boolean,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[String]]] = Future{(Failure(setUnimplementedError(nameOf(getTransactionRequestIdsByAttributeNameValues _))), callContext)}

  def getByAttributeNameValues(
    bankId: BankId, 
    params: Map[String, List[String]],
    isPersonal: Boolean,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getByAttributeNameValues _))), callContext)}

  def createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                transactionRequestId: TransactionRequestId,
                                                transactionRequestAttributeId: Option[String],
                                                name: String,
                                                attributeType: TransactionRequestAttributeType.Value,
                                                value: String,
                                                callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequestAttributeTrait]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateTransactionRequestAttribute _))), callContext)}
  def createTransactionRequestAttributes(bankId: BankId,
                                         transactionRequestId: TransactionRequestId,
                                         transactionRequestAttributes: List[TransactionRequestAttributeJsonV400],
                                         isPersonal: Boolean,
                                         callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionRequestAttributeTrait]]] = Future{(Failure(setUnimplementedError(nameOf(createTransactionRequestAttributes _))), callContext)}

  def deleteTransactionRequestAttribute(transactionRequestAttributeId: String,
                                        callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteTransactionRequestAttribute _))), callContext)}

  def createAccountApplication(
                                productCode: ProductCode,
                                userId: Option[String],
                                customerId: Option[String],
                                callContext: Option[CallContext]
                              ): OBPReturnType[Box[AccountApplication]] = Future{(Failure(setUnimplementedError(nameOf(createAccountApplication _))), callContext)}

  def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[Box[List[AccountApplication]]] =
    Future{(Failure(setUnimplementedError(nameOf(getAllAccountApplication _))), callContext)}

  def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    Future{(Failure(setUnimplementedError(nameOf(getAccountApplicationById _))), callContext)}

  def updateAccountApplicationStatus(accountApplicationId:String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    Future{(Failure(setUnimplementedError(nameOf(updateAccountApplicationStatus _))), callContext)}

  def getOrCreateProductCollection(collectionCode: String,
                                   productCodes: List[String],
                                   callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    Future{(Failure(setUnimplementedError(nameOf(getOrCreateProductCollection _))), callContext)}

  def getProductCollection(collectionCode: String,
                           callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    Future{(Failure(setUnimplementedError(nameOf(getProductCollection _))), callContext)}

  def getOrCreateProductCollectionItem(collectionCode: String,
                                       memberProductCodes: List[String],
                                       callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    Future{(Failure(setUnimplementedError(nameOf(getOrCreateProductCollectionItem _))), callContext)}
  def getProductCollectionItem(collectionCode: String,
                               callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    Future{(Failure(setUnimplementedError(nameOf(getProductCollectionItem _))), callContext)}

  def getProductCollectionItemsTree(collectionCode: String,
                                    bankId: String,
                                    callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItemsTree]]] =
    Future{(Failure(setUnimplementedError(nameOf(getProductCollectionItemsTree _))), callContext)}

  def createMeeting(
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
    Future{(Failure(setUnimplementedError(nameOf(createMeeting _))), callContext)}

  def getMeetings(
                   bankId : BankId,
                   user: User,
                   callContext: Option[CallContext]
                 ): OBPReturnType[Box[List[Meeting]]] =
    Future{(Failure(setUnimplementedError(nameOf(getMeetings _))), callContext)}

  def getMeeting(
                  bankId: BankId,
                  user: User,
                  meetingId : String,
                  callContext: Option[CallContext]
                ): OBPReturnType[Box[Meeting]]=Future{(Failure(setUnimplementedError(nameOf(getMeeting _))), callContext)}

  def createOrUpdateKycCheck(bankId: String,
                             customerId: String,
                             id: String,
                             customerNumber: String,
                             date: Date,
                             how: String,
                             staffUserId: String,
                             mStaffName: String,
                             mSatisfied: Boolean,
                             comments: String,
                             callContext: Option[CallContext]): OBPReturnType[Box[KycCheck]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateKycCheck _))), callContext)}

  def createOrUpdateKycDocument(bankId: String,
                                customerId: String,
                                id: String,
                                customerNumber: String,
                                `type`: String,
                                number: String,
                                issueDate: Date,
                                issuePlace: String,
                                expiryDate: Date,
                                callContext: Option[CallContext]): OBPReturnType[Box[KycDocument]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateKycDocument _))), callContext)}

  def createOrUpdateKycMedia(bankId: String,
                             customerId: String,
                             id: String,
                             customerNumber: String,
                             `type`: String,
                             url: String,
                             date: Date,
                             relatesToKycDocumentId: String,
                             relatesToKycCheckId: String,
                             callContext: Option[CallContext]): OBPReturnType[Box[KycMedia]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateKycDocument _))), callContext)}

  def createOrUpdateKycStatus(bankId: String,
                              customerId: String,
                              customerNumber: String,
                              ok: Boolean,
                              date: Date,
                              callContext: Option[CallContext]): OBPReturnType[Box[KycStatus]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateKycStatus _))), callContext)}

  def getKycChecks(customerId: String,
                   callContext: Option[CallContext]
                  ): OBPReturnType[Box[List[KycCheck]]] = Future{(Failure(setUnimplementedError(nameOf(getKycChecks _))), callContext)}

  def getKycDocuments(customerId: String,
                      callContext: Option[CallContext]
                     ): OBPReturnType[Box[List[KycDocument]]] = Future{(Failure(setUnimplementedError(nameOf(getKycDocuments _))), callContext)}

  def getKycMedias(customerId: String,
                   callContext: Option[CallContext]
                  ): OBPReturnType[Box[List[KycMedia]]] = Future{(Failure(setUnimplementedError(nameOf(getKycMedias _))), callContext)}

  def getKycStatuses(customerId: String,
                     callContext: Option[CallContext]
                    ): OBPReturnType[Box[List[KycStatus]]] = Future{(Failure(setUnimplementedError(nameOf(getKycStatuses _))), callContext)}

  def createMessage(user : User,
                    bankId : BankId,
                    message : String,
                    fromDepartment : String,
                    fromPerson : String,
                    callContext: Option[CallContext]) : OBPReturnType[Box[CustomerMessage]] = Future{(Failure(setUnimplementedError(nameOf(createMessage _))), callContext)}
  
  def createCustomerMessage(customer: Customer,
                    bankId : BankId,
                    transport : String,
                    message : String,
                    fromDepartment : String,
                    fromPerson : String,
                    callContext: Option[CallContext]) : OBPReturnType[Box[CustomerMessage]] = Future{(Failure(setUnimplementedError(nameOf(createCustomerMessage _))), callContext)}

  def getCustomerMessages(
    customer: Customer,
    bankId: BankId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[CustomerMessage]]] = Future{(Failure(setUnimplementedError(nameOf(getCustomerMessages _))), callContext)}

  def makeHistoricalPayment(fromAccount: BankAccount,
                            toAccount: BankAccount,
                            posted: Date,
                            completed: Date,
                            amount: BigDecimal,
                            currency: String,
                            description: String,
                            transactionRequestType: String,
                            chargePolicy: String,
                            callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = Future{(Failure(setUnimplementedError(nameOf(makeHistoricalPayment _))), callContext)}

  /**
   * DynamicEntity process function
   * @param operation type of operation, this is an enumeration
   * @param entityName DynamicEntity's entity name
   * @param requestBody content of request
   * @param entityId    id of given DynamicEntity
   * @param bankId    bank id of the Entity
   * @param queryParameters: eg: ("status":List("pending","available"))
   * @param callContext
   * @return result DynamicEntity process
   */
  def dynamicEntityProcess(operation: DynamicEntityOperation,
                             entityName: String,
                             requestBody: Option[JObject],
                             entityId: Option[String],
                             bankId: Option[String],
                             queryParameters: Option[Map[String, List[String]]],
                             userId: Option[String],
                             isPersonalEntity: Boolean,
                             callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = Future{(Failure(setUnimplementedError(nameOf(dynamicEntityProcess _))), callContext)}

  def dynamicEndpointProcess(url: String, jValue: JValue, method: HttpMethod, params: Map[String, List[String]], pathParams: Map[String, String],
                             callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = Future{(Failure(setUnimplementedError(nameOf(dynamicEndpointProcess _))), callContext)}
  
  def createDirectDebit(bankId: String,
                        accountId: String,
                        customerId: String,
                        userId: String,
                        counterpartyId: String,
                        dateSigned: Date,
                        dateStarts: Date,
                        dateExpires: Option[Date],
                        callContext: Option[CallContext]): OBPReturnType[Box[DirectDebitTrait]] = Future{(Failure(setUnimplementedError(nameOf(createDirectDebit _))), callContext)}

  def createStandingOrder(bankId: String,
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
    (Failure(setUnimplementedError(nameOf(createStandingOrder _))), callContext)
  }

  def deleteCustomerAttribute(customerAttributeId: String,
                           callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteCustomerAttribute _))), callContext)}


  /**
   * this method is used to validate the UserAuthContextUpdateRequest. OBP will do the followings in connector level:
   * 1st: check if the `real` bank customer is existing to search for this key-value pair. If not found, we will throw exception.
   *    if it is `CUSTOMERB_NUMBER: 1234`,then we will check the customer by customer_number. 
   *    if it is `PASSPORT_NUMBER:1234`, then we will check the customer by passport_number. 
   * 2rd: create the UserAuthContextUpdateRequestChallenge  
   * 
   * 3rd: send the Challenge to the user by email/phone .....
   * 
   * @return
   */
  def validateUserAuthContextUpdateRequest(
    bankId: String,
    userId: String,
    key: String,
    value: String,
    scaMethod: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[UserAuthContextUpdate]] = Future{(Failure(setUnimplementedError(nameOf(validateUserAuthContextUpdateRequest _))), callContext)}

  def checkAnswer(authContextUpdateId: String, challenge: String, callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContextUpdate]] = Future{(Failure(setUnimplementedError(nameOf(checkAnswer _))), callContext)}

  def sendCustomerNotification(
    scaMethod: StrongCustomerAuthentication,
    recipient: String, 
    subject: Option[String], //Only for EMAIL, SMS do not need it, so here it is Option
    message: String, 
    callContext: Option[CallContext]
  ): OBPReturnType[Box[String]] = Future{(Failure(setUnimplementedError(nameOf(sendCustomerNotification _))), callContext)}

  def getCustomerAccountLink(customerId: String, accountId: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAccountLinkTrait]] = Future{(Failure(setUnimplementedError(nameOf(getCustomerAccountLink _))), callContext)}
  
  def getCustomerAccountLinksByCustomerId(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAccountLinkTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getCustomerAccountLinksByCustomerId _))), callContext)}
  
  def getAgentAccountLinksByAgentId(agnetId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAccountLinkTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getCustomerAccountLinksByCustomerId _))), callContext)}
  
  def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAccountLinkTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getCustomerAccountLinksByBankIdAccountId _))), callContext)}
  
  def getCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAccountLinkTrait]] = Future{(Failure(setUnimplementedError(nameOf(getCustomerAccountLinkById _))), callContext)}
  
  def deleteCustomerAccountLinkById(customerAccountLinkId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteCustomerAccountLinkById _))), callContext)}
  
  def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAccountLinkTrait]] = Future{(Failure(setUnimplementedError(nameOf(createCustomerAccountLink _))), callContext)}
  
  def createAgentAccountLink(agentId: String, bankId: String, accountId: String, callContext: Option[CallContext]): OBPReturnType[Box[AgentAccountLinkTrait]] = Future{(Failure(setUnimplementedError(nameOf(createAgentAccountLink _))), callContext)}
  
  def updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAccountLinkTrait]] = Future{(Failure(setUnimplementedError(nameOf(updateCustomerAccountLinkById _))), callContext)}
  
  def getConsentImplicitSCA(user: User, callContext: Option[CallContext]): OBPReturnType[Box[ConsentImplicitSCAT]] = Future{(Failure(setUnimplementedError(nameOf(getConsentImplicitSCA _))), callContext)}

  def createOrUpdateCounterpartyLimit(
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
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CounterpartyLimitTrait]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateCounterpartyLimit _))), callContext)}
  
  def getCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CounterpartyLimitTrait]] = Future{(Failure(setUnimplementedError(nameOf(getCounterpartyLimit _))), callContext)}
  
  def deleteCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteCounterpartyLimit _))), callContext)}

  def getRegulatedEntities(
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[RegulatedEntityTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getRegulatedEntities _))), callContext)}
  
  def getRegulatedEntityByEntityId(
    regulatedEntityId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[RegulatedEntityTrait]] = Future{(Failure(setUnimplementedError(nameOf(getRegulatedEntityByEntityId _))), callContext)}

  def getBankAccountBalancesByAccountId(
    accountId: AccountId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[BankAccountBalanceTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getBankAccountBalancesByAccountId(_, _)))), callContext)}
  
  def getBankAccountsBalancesByAccountIds(
    accountIds: List[AccountId],
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[BankAccountBalanceTrait]]] = Future{(Failure(setUnimplementedError(nameOf(getBankAccountsBalancesByAccountIds(_, _)))), callContext)}

  def getBankAccountBalanceById(
    balanceId: BalanceId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[BankAccountBalanceTrait]] = Future{(Failure(setUnimplementedError(nameOf(getBankAccountBalanceById _))), callContext)}


  def createOrUpdateBankAccountBalance(
    bankId: BankId,
    accountId: AccountId,
    balanceId: Option[BalanceId],
    balanceType: String,
    balanceAmount: BigDecimal,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[BankAccountBalanceTrait]] = Future{(Failure(setUnimplementedError(nameOf(createOrUpdateBankAccountBalance _))), callContext)}

  def deleteBankAccountBalance(
    balanceId: BalanceId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError(nameOf(deleteBankAccountBalance _))), callContext)}
}

package code.bankconnectors

import java.util.Date
import java.util.UUID.randomUUID

import code.accountholders.{AccountHolders, MapperAccountHolders}
import code.api.{APIFailure, APIFailureNewStyle}
import code.api.cache.Caching
import code.api.util.APIUtil.{OBPReturnType, _}
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import code.api.util._
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_1_0._
import code.atms.Atms
import code.bankconnectors.akka.AkkaConnector_vDec2018
import code.bankconnectors.rest.RestConnector_vMar2019
import code.bankconnectors.storedprocedure.StoredProcedureConnector_vDec2019
import code.bankconnectors.vJune2017.KafkaMappedConnector_vJune2017
import code.bankconnectors.vMar2017.KafkaMappedConnector_vMar2017
import code.bankconnectors.vMay2019.KafkaMappedConnector_vMay2019
import code.bankconnectors.vSept2018.KafkaMappedConnector_vSept2018
import code.branches.Branches.Branch
import code.customerattribute.MappedCustomerAttribute
import code.directdebit.DirectDebitTrait
import code.fx.FXRate
import code.fx.fx.TTL
import code.management.ImporterAPI.ImporterTransaction
import code.model.dataAccess.ResourceUser
import code.model.toUserExtended
import code.standingorders.StandingOrderTrait
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import code.transactionrequests.TransactionRequests._
import code.transactionrequests.{TransactionRequestTypeCharge, TransactionRequests}
import code.users.Users
import code.util.Helper._
import code.util.JsonUtils
import code.views.Views
import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, CustomerAttributeType, DynamicEntityOperation, ProductAttributeType, TransactionAttributeType}
import com.openbankproject.commons.model.{AccountApplication, Bank, CounterpartyTrait, CustomerAddress, Product, ProductCollection, ProductCollectionItem, TaxResidence, TransactionRequestStatus, UserAuthContext, UserAuthContextUpdate, _}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, EmptyBox, Failure, Full, ParamFailure}
import net.liftweb.json.{Formats, JObject, JValue}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ReflectUtils
import com.openbankproject.commons.util.Functions.lazyValue
import net.liftweb.json

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.math.{BigDecimal, BigInt}
import scala.util.Random
import scala.reflect.runtime.universe.{MethodSymbol, typeOf}

/*
So we can switch between different sources of resources e.g.
- Mapper ORM for connecting to RDBMS (via JDBC) https://www.assembla.com/wiki/show/liftweb/Mapper
- MongoDB
- KafkaMQ
etc.

Note: We also have individual providers for resources like Branches and Products.
Probably makes sense to have more targeted providers like this.

Could consider a Map of ("resourceType" -> "provider") - this could tell us which tables we need to schemify (for list in Boot), whether or not to
 initialise MongoDB etc. resourceType might be sub devided to allow for different account types coming from different internal APIs, MQs.
 */

object Connector extends SimpleInjector {

  val nameToConnector: Map[String, () => Connector] = Map(
    "mapped" -> lazyValue(LocalMappedConnector),
    "akka_vDec2018" -> lazyValue(AkkaConnector_vDec2018),
    "mongodb" -> lazyValue(LocalRecordConnector),
    "obpjvm" -> lazyValue(ObpJvmMappedConnector),
    "kafka" -> lazyValue(KafkaMappedConnector),
    "kafka_JVMcompatible" -> lazyValue(KafkaMappedConnector_JVMcompatible),
    "kafka_vMar2017" -> lazyValue(KafkaMappedConnector_vMar2017),
    "kafka_vJune2017" -> lazyValue(KafkaMappedConnector_vJune2017),
    "kafka_vSept2018" -> lazyValue(KafkaMappedConnector_vSept2018),
    "kafka_vMay2019" -> lazyValue(KafkaMappedConnector_vMay2019),
    "rest_vMar2019" -> lazyValue(RestConnector_vMar2019),
    "stored_procedure_vDec2019" -> lazyValue(StoredProcedureConnector_vDec2019)
  )

  def getConnectorInstance(connectorVersion: String): Connector = {
    connectorVersion match {
      case "star" => StarConnector
      case k => nameToConnector.get(k)
        .map(f => f())
        .getOrElse(throw new RuntimeException(s"Do not Support this connector version: $k"))
    }
  }

  val connector = new Inject(buildOne _) {}

  def buildOne: Connector = {
    val connectorProps = APIUtil.getPropsValue("connector").openOrThrowException("connector props filed not set")
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
   * current connector instance implemented Connector method,
   * methodName to method
   */
  lazy val implementedMethods: Map[String, MethodSymbol] = {
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
    result
  }

  /**
    * convert original return type future to OBPReturnType
    *
    * @param future original return type
    * @tparam T future success value type
    * @return OBPReturnType type future
    */
  protected implicit def futureReturnTypeToOBPReturnType[T](future: Future[Box[(T, Option[CallContext])]]): OBPReturnType[Box[T]] = future map {
    boxedTuple => (boxedTuple.map(_._1), boxedTuple.map(_._2).getOrElse(None))
  }

  /**
    * convert OBPReturnType return type to original future type
    *
    * @param value OBPReturnType return type
    * @tparam T future success value type
    * @return original future type
    */
  protected implicit def OBPReturnTypeToFutureReturnType[T](value: OBPReturnType[Box[T]]): Future[Box[(T, Option[CallContext])]] = value.map {
    tuple => tuple._1.map((_, tuple._2))
  }

  /**
    * This method will return the method name of the current calling method.
    * The scala compiler will tweak the method name, so we need clean the `getMethodName` return value.
    * @return
    */
  private def setUnimplementedError() : String = {
    val currentMethodName = Thread.currentThread.getStackTrace()(2).getMethodName
      .replaceFirst("""^.*\$(.+?)\$.*$""", "$1") // "$anonfun$getBanksFuture$" --> .
      .replace("Future","") //getBanksFuture --> getBanks

    NotImplemented + currentMethodName + s" Please check `Get Message Docs`endpoint and implement the process `obp.$currentMethodName` in Adapter side."
  }

  def getAdapterInfo(callContext: Option[CallContext]) : Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = Future{Failure(setUnimplementedError)}

  // Gets current challenge level for transaction request
  // Transaction request challenge threshold. Level at which challenge is created and needs to be answered
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
                             userName: String,
                             callContext: Option[CallContext]
                           ): OBPReturnType[Box[AmountOfMoney]] =
    LocalMappedConnector.getChallengeThreshold(
      bankId: String,
      accountId: String,
      viewId: String,
      transactionRequestType: String,
      currency: String,
      userId: String,
      userName: String,
      callContext: Option[CallContext]
    )

  //Gets current charge level for transaction request
  def getChargeLevel(bankId: BankId,
                     accountId: AccountId,
                     viewId: ViewId,
                     userId: String,
                     userName: String,
                     transactionRequestType: String,
                     currency: String,
                     callContext:Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] =
    LocalMappedConnector.getChargeLevel(
      bankId: BankId,
      accountId: AccountId,
      viewId: ViewId,
      userId: String,
      userName: String,
      transactionRequestType: String,
      currency: String,
      callContext:Option[CallContext]
    )

  // Initiate creating a challenge for transaction request and returns an id of the challenge
  def createChallenge(bankId: BankId, 
                      accountId: AccountId, 
                      userId: String, 
                      transactionRequestType: TransactionRequestType, 
                      transactionRequestId: String,
                      scaMethod: Option[SCA], 
                      callContext: Option[CallContext]) : OBPReturnType[Box[String]]= Future{(Failure(setUnimplementedError), callContext)}  
  // Initiate creating a challenges for transaction request and returns an ids of the challenges
  def createChallenges(bankId: BankId, 
                      accountId: AccountId, 
                      userIds: List[String], 
                      transactionRequestType: TransactionRequestType, 
                      transactionRequestId: String,
                      scaMethod: Option[SCA], 
                      callContext: Option[CallContext]) : OBPReturnType[Box[List[String]]]= Future{(Failure(setUnimplementedError), callContext)}
  // Validates an answer for a challenge and returns if the answer is correct or not
  def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Full(true), callContext)}

  //gets a particular bank handled by this connector
  def getBankLegacy(bankId : BankId, callContext: Option[CallContext]) : Box[(Bank, Option[CallContext])] = Failure(setUnimplementedError)

  def getBank(bankId : BankId, callContext: Option[CallContext]) : Future[Box[(Bank, Option[CallContext])]] = Future(Failure(setUnimplementedError))

  //gets banks handled by this connector
  def getBanksLegacy(callContext: Option[CallContext]): Box[(List[Bank], Option[CallContext])] = Failure(setUnimplementedError)

  def getBanks(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = Future{(Failure(setUnimplementedError))}

  /**
    *
    * @param username username of the user.
    * @return all the accounts, get from Main Frame.
    */
  def getBankAccountsForUserLegacy(username: String, callContext: Option[CallContext]) : Box[(List[InboundAccount], Option[CallContext])] = Failure(setUnimplementedError)

  /**
    *
    * @param username username of the user.
    * @return all the accounts, get from Main Frame.
    */
  def getBankAccountsForUser(username: String, callContext: Option[CallContext]) : Future[Box[(List[InboundAccount], Option[CallContext])]] = Future{
    Failure(setUnimplementedError)
  }

  /**
    * This method is for get User from external, eg kafka/obpjvm...
    *  getUserId  --> externalUserHelper--> getUserFromConnector --> getUser
    * @param name
    * @param password
    * @return
    */
  def getUser(name: String, password: String): Box[InboundUser]= Failure(setUnimplementedError)

  /**
    * This is a helper method
    * for remote user(means the user will get from kafka) to update the views, accountHolders for OBP side
    * It depends different use cases, normally (also see it in KafkaMappedConnector_vJune2017.scala)
    *
    * @param user the user is from remote side
    */
  @deprecated("Now move it to AuthUser.updateUserAccountViews","17-07-2017")
  def updateUserAccountViewsOld(user: ResourceUser) = {}

  //This is old one, no callContext there. only for old style endpoints.
  def getBankAccount(bankId : BankId, accountId : AccountId) : Box[BankAccount]= {
    getBankAccountLegacy(bankId, accountId, None).map(_._1)
  }

  //This one just added the callContext in parameters.
  def getBankAccountLegacy(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : Box[(BankAccount, Option[CallContext])]= Failure(setUnimplementedError)

  //This one return the Future.
  def getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : OBPReturnType[Box[BankAccount]]= Future{(Failure(setUnimplementedError),callContext)}
  
  def getBankAccountByIban(iban : String, callContext: Option[CallContext]) : OBPReturnType[Box[BankAccount]]= Future{(Failure(setUnimplementedError),callContext)}
  def getBankAccountByRouting(scheme : String, address : String, callContext: Option[CallContext]) : Box[(BankAccount, Option[CallContext])]= Failure(setUnimplementedError)

  def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : OBPReturnType[Box[List[BankAccount]]]= Future{(Failure(setUnimplementedError), callContext)}
  
  def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : OBPReturnType[Box[AccountsBalances]]= Future{(Failure(setUnimplementedError), callContext)}

  def getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[(List[CoreAccount], Option[CallContext])] =
    Failure(setUnimplementedError)
  def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]]=
    Future{Failure(setUnimplementedError)}

  def getBankAccountsHeldLegacy(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[List[AccountHeld]]= Failure(setUnimplementedError)
  def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : OBPReturnType[Box[List[AccountHeld]]]= Future {(Failure(setUnimplementedError), callContext)}

  def checkBankAccountExistsLegacy(bankId : BankId, accountId : AccountId, callContext: Option[CallContext] = None) : Box[(BankAccount, Option[CallContext])]= Failure(setUnimplementedError)
  def checkBankAccountExists(bankId : BankId, accountId : AccountId, callContext: Option[CallContext] = None) : OBPReturnType[Box[(BankAccount)]] = Future {(Failure(setUnimplementedError), callContext)}

  /**
    * This method is just return an empty account to AccountType.
    * It is used for SEPA, Counterparty empty toAccount
    *
    * @return empty bankAccount
    */
  def getEmptyBankAccount(): Box[BankAccount]= Failure(setUnimplementedError)

  def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyId: String): Box[Counterparty] = {
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
  }

  def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId): Box[List[Counterparty]] = {
    val counterparties = getTransactionsLegacy(bankId, accountId, None).map(_._1).toList.flatten.map(_.otherAccount)
    Full(counterparties.toSet.toList) //there are many transactions share the same Counterparty, so we need filter the same ones.
  }

  def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty]= Failure(setUnimplementedError)

  def getCounterpartyTrait(bankId: BankId, accountId: AccountId, couterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]]= Future{(Failure(setUnimplementedError), callContext)}

  def getCounterpartyByCounterpartyIdLegacy(counterpartyId: CounterpartyId, callContext: Option[CallContext]): Box[(CounterpartyTrait, Option[CallContext])]= Failure(setUnimplementedError)

  def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = Future{(Failure(setUnimplementedError), callContext)}


  /**
    * get Counterparty by iban (OtherAccountRoutingAddress field in MappedCounterparty table)
    * This is a helper method that assumes OtherAccountRoutingScheme=IBAN
    */
  def getCounterpartyByIban(iban: String, callContext: Option[CallContext]) : OBPReturnType[Box[CounterpartyTrait]] = Future {(Failure(setUnimplementedError), callContext)}

  def getCounterpartiesLegacy(thisBankId: BankId, thisAccountId: AccountId, viewId :ViewId, callContext: Option[CallContext] = None): Box[(List[CounterpartyTrait], Option[CallContext])]= Failure(setUnimplementedError)

  def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): OBPReturnType[Box[List[CounterpartyTrait]]] = Future {(Failure(setUnimplementedError), callContext)}

  //TODO, here is a problem for return value `List[Transaction]`, this is a normal class, not a trait. It is a big class,
  // it contains thisAccount(BankAccount object) and otherAccount(Counterparty object)
  def getTransactionsLegacy(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Box[(List[Transaction], Option[CallContext])]= Failure(setUnimplementedError)
  def getTransactions(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): OBPReturnType[Box[List[Transaction]]] = {
    val result: Box[(List[Transaction], Option[CallContext])] = getTransactionsLegacy(bankId, accountID, callContext, queryParams)
    Future(result.map(_._1), result.map(_._2).getOrElse(callContext))
  }
  def getTransactionsCore(bankId: BankId, accountID: AccountId, queryParams:  List[OBPQueryParam] = Nil, callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionCore]]] = Future{(Failure(setUnimplementedError), callContext)}

  def getTransactionLegacy(bankId: BankId, accountID : AccountId, transactionId : TransactionId, callContext: Option[CallContext] = None): Box[(Transaction, Option[CallContext])] = Failure(setUnimplementedError)
  def getTransaction(bankId: BankId, accountID : AccountId, transactionId : TransactionId, callContext: Option[CallContext] = None): OBPReturnType[Box[Transaction]] = {
    val result: Box[(Transaction, Option[CallContext])] = getTransactionLegacy(bankId, accountID, transactionId, callContext)
    Future(result.map(_._1), result.map(_._2).getOrElse(callContext))
  }

  def getPhysicalCards(user : User) : Box[List[PhysicalCard]] = Failure(setUnimplementedError)
  
  def getPhysicalCardForBank(bankId: BankId, cardId: String,  callContext:Option[CallContext]) : OBPReturnType[Box[PhysicalCardTrait]] = Future{(Failure(setUnimplementedError), callContext)}
  def deletePhysicalCardForBank(bankId: BankId, cardId: String,  callContext:Option[CallContext]) : OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError), callContext)}
  
  def getPhysicalCardsForBankLegacy(bank: Bank, user : User, queryParams: List[OBPQueryParam]) : Box[List[PhysicalCard]] = Failure(setUnimplementedError)
  def getPhysicalCardsForBank(bank: Bank, user : User, queryParams: List[OBPQueryParam], callContext:Option[CallContext]) : OBPReturnType[Box[List[PhysicalCard]]] = Future{(Failure(setUnimplementedError), callContext)}

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
    callContext: Option[CallContext]
  ): Box[PhysicalCard] = Failure(setUnimplementedError)

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
    callContext: Option[CallContext]
  ): OBPReturnType[Box[PhysicalCard]] = Future{(Failure{setUnimplementedError}, callContext)}

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
  ): OBPReturnType[Box[PhysicalCardTrait]] = Future{(Failure{setUnimplementedError}, callContext)}
  
  //Payments api: just return Failure("not supported") from makePaymentImpl if you don't want to implement it
  /**
    * \
    *
    * @param initiator The user attempting to make the payment
    * @param fromAccountUID The unique identifier of the account sending money
    * @param toAccountUID The unique identifier of the account receiving money
    * @param amt The amount of money to send ( > 0 )
    * @return The id of the sender's new transaction,
    */
  def makePayment(initiator : User, fromAccountUID : BankIdAccountId, toAccountUID : BankIdAccountId,
                  amt : BigDecimal, description : String, transactionRequestType: TransactionRequestType) : Box[TransactionId] = {
    for{
      fromAccount <- getBankAccount(fromAccountUID.bankId, fromAccountUID.accountId) ?~
        s"$BankAccountNotFound  Account ${fromAccountUID.accountId} not found at bank ${fromAccountUID.bankId}"
      isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId,fromAccount.accountId)), UserNoOwnerView)
      toAccount <- getBankAccount(toAccountUID.bankId, toAccountUID.accountId) ?~
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
        transactionRequestCommonBody = null,//Note transactionRequestCommonBody started to use  in V210
        amt,
        description,
        transactionRequestType,
        "") //Note chargePolicy started to use  in V210
    } yield transactionId
  }

  /**
    * \
    *
    * @param fromAccount The unique identifier of the account sending money
    * @param toAccount The unique identifier of the account receiving money
    * @param amount The amount of money to send ( > 0 )
    * @param transactionRequestType user input: SEPA, SANDBOX_TAN, FREE_FORM, COUNTERPARTY
    * @return The id of the sender's new transaction,
    */
  def makePaymentv200(fromAccount: BankAccount,
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

  //Note: introduce v210 here, is for kafka connectors, use callContext and return Future.
  def makePaymentv210(fromAccount: BankAccount,
                      toAccount: BankAccount,
                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                      amount: BigDecimal,
                      description: String,
                      transactionRequestType: TransactionRequestType,
                      chargePolicy: String,
                      callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]]= Future{(Failure(setUnimplementedError), callContext)}


  protected def makePaymentImpl(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String): Box[TransactionId]= Failure(setUnimplementedError)



  /*
    Transaction Requests
  */


  // This is used for 1.4.0 See createTransactionRequestv200 for 2.0.0
  def createTransactionRequest(initiator : User, fromAccount : BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) : Box[TransactionRequest] = {
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
      fromAccountType <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
        s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId,fromAccount.accountId)), UserNoOwnerView)
      toAccountType <- getBankAccount(toAccount.bankId, toAccount.accountId) ?~
        s"account ${toAccount.accountId} not found at bank ${toAccount.bankId}"
      rawAmt <- tryo { BigDecimal(body.value.amount) } ?~! s"amount ${body.value.amount} not convertible to number"
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
          if (! createdTransactionId.isEmpty) {
            saveTransactionRequestTransaction(result.id, ti)
            result = result.copy(transaction_ids = ti.value)
          }
        }
        case _ => None
      }
    } else {
      //if challenge necessary, create a new one
      val challenge = TransactionRequestChallenge(id = generateUUID(), allowed_attempts = 3, challenge_type = TransactionChallengeTypes.OTP_VIA_API.toString)
      saveTransactionRequestChallenge(result.id, challenge)
      result = result.copy(challenge = challenge)
    }

    Full(result)
  }


  def createTransactionRequestv200(initiator : User, fromAccount : BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) : Box[TransactionRequest] = {
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
      fromAccountType <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~ s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId,fromAccount.accountId)) == true || hasEntitlement(fromAccount.bankId.value, initiator.userId, canCreateAnyTransactionRequest) == true, ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
      toAccountType <- getBankAccount(toAccount.bankId, toAccount.accountId) ?~ s"account ${toAccount.accountId} not found at bank ${toAccount.bankId}"
      rawAmt <- tryo { BigDecimal(body.value.amount) } ?~! s"amount ${body.value.amount} not convertible to number"
      // isValidTransactionRequestType is checked at API layer. Maybe here too.
      isPositiveAmtToSend <- booleanToBox(rawAmt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. (${rawAmt})")

      // For now, arbitary charge value to demonstrate PSD2 charge transparency principle. Eventually this would come from Transaction Type? 10 decimal places of scaling so can add small percentage per transaction.
      chargeValue <- tryo {(BigDecimal(body.value.amount) * 0.0001).setScale(10, BigDecimal.RoundingMode.HALF_UP).toDouble} ?~! s"could not create charge for ${body.value.amount}"
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
        transactionRequestCommonBody=null,//Note chargePolicy only support in V210
        BigDecimal(body.value.amount),
        body.description,
        transactionRequestType,
        "") //Note chargePolicy only support in V210

      //set challenge to null
      result = result.copy(challenge = null)

      //save transaction_id if we have one
      createdTransactionId match {
        case Full(ti) => {
          if (! createdTransactionId.isEmpty) {
            saveTransactionRequestTransaction(result.id, ti)
            result = result.copy(transaction_ids = ti.value)
          }
        }
        case Failure(message, exception, chain) => return Failure(message, exception, chain)
        case _ => None
      }
    } else {
      //if challenge necessary, create a new one
      val challenge = TransactionRequestChallenge(id = generateUUID(), allowed_attempts = 3, challenge_type = TransactionChallengeTypes.OTP_VIA_API.toString)
      saveTransactionRequestChallenge(result.id, challenge)
      result = result.copy(challenge = challenge)
    }

    Full(result)
  }
  // Set initial status
  def getStatus(challengeThresholdAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal, transactionRequestType: TransactionRequestType): Future[TransactionRequestStatus.Value] = {
    Future(
      if (transactionRequestCommonBodyAmount < challengeThresholdAmount) {
        // For any connector != mapped we should probably assume that transaction_status_scheduler_delay will be > 0
        // so that getTransactionRequestStatusesImpl needs to be implemented for all connectors except mapped.
        // i.e. if we are certain that saveTransaction will be honored immediately by the backend, then transaction_status_scheduler_delay
        // can be empty in the props file. Otherwise, the status will be set to STATUS_PENDING
        // and getTransactionRequestStatusesImpl needs to be run periodically to update the transaction request status.
        if (APIUtil.getPropsAsLongValue("transaction_status_scheduler_delay").isEmpty || (transactionRequestType.value ==REFUND.toString))
          TransactionRequestStatus.COMPLETED
        else
          TransactionRequestStatus.PENDING
      } else {
        TransactionRequestStatus.INITIATED
      })
  }

  // Get the charge level value
  def getChargeValue(chargeLevelAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal): Future[String] = {
    Future(
      transactionRequestCommonBodyAmount* chargeLevelAmount match {
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
                                   callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {

    for{
      // Get the threshold for a challenge. i.e. over what value do we require an out of Band security challenge to be sent?
      (challengeThreshold, callContext) <- Connector.connector.vend.getChallengeThreshold(fromAccount.bankId.value, fromAccount.accountId.value, viewId.value, transactionRequestType.value, transactionRequestCommonBody.value.currency, initiator.userId, initiator.name, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold ", 400), i._2)
      }
      challengeThresholdAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetChallengeThreshold. challengeThreshold amount ${challengeThreshold.amount} not convertible to number", 400, callContext) {
        BigDecimal(challengeThreshold.amount)}
      transactionRequestCommonBodyAmount <- NewStyle.function.tryons(s"$InvalidNumber Request Json value.amount ${transactionRequestCommonBody.value.amount} not convertible to number", 400, callContext) {
        BigDecimal(transactionRequestCommonBody.value.amount)}
      status <- getStatus(challengeThresholdAmount,transactionRequestCommonBodyAmount, transactionRequestType: TransactionRequestType)
      (chargeLevel, callContext) <- Connector.connector.vend.getChargeLevel(BankId(fromAccount.bankId.value), AccountId(fromAccount.accountId.value), viewId, initiator.userId, initiator.name, transactionRequestType.value, fromAccount.currency, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
      }

      chargeLevelAmount <- NewStyle.function.tryons( s"$InvalidNumber chargeLevel.amount: ${chargeLevel.amount} can not be transferred to decimal !", 400, callContext) {
        BigDecimal(chargeLevel.amount)}
      chargeValue <- getChargeValue(chargeLevelAmount,transactionRequestCommonBodyAmount)
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestCommonBody.value.currency, chargeValue))
      // Always create a new Transaction Request
      transactionRequest <- Future{ createTransactionRequestImpl210(TransactionRequestId(generateUUID()), transactionRequestType, fromAccount, toAccount, transactionRequestCommonBody, detailsPlain, status.toString, charge, chargePolicy)} map {
        unboxFullOrFail(_, callContext, s"$InvalidConnectorResponseForCreateTransactionRequestImpl210")
      }

      // If no challenge necessary, create Transaction immediately and put in data store and object to return
      (transactionRequest, callConext) <- status match {
        case TransactionRequestStatus.COMPLETED =>
          for {
            (createdTransactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
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
            _ <- Future {saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId)}
            //update transaction_id filed for varibale 'transactionRequest'
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
              (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
            }
            
            newChallenge = TransactionRequestChallenge(challengeId, allowed_attempts = 3, challenge_type = challengeType.getOrElse(TransactionChallengeTypes.OTP_VIA_API.toString))
            _ <- Future (saveTransactionRequestChallenge(transactionRequest.id, newChallenge))
            transactionRequest <- Future(transactionRequest.copy(challenge = newChallenge))
          } yield {
            (transactionRequest, callContext)
          }
        case _ => Future (transactionRequest, callContext)
      }
    }yield{
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
                                   callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {

    for{
      // Get the threshold for a challenge. i.e. over what value do we require an out of Band security challenge to be sent?
      (challengeThreshold, callContext) <- Connector.connector.vend.getChallengeThreshold(fromAccount.bankId.value, fromAccount.accountId.value, viewId.value, transactionRequestType.value, transactionRequestCommonBody.value.currency, initiator.userId, initiator.name, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold ", 400), i._2)
      }
      challengeThresholdAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetChallengeThreshold. challengeThreshold amount ${challengeThreshold.amount} not convertible to number", 400, callContext) {
        BigDecimal(challengeThreshold.amount)}
      transactionRequestCommonBodyAmount <- NewStyle.function.tryons(s"$InvalidNumber Request Json value.amount ${transactionRequestCommonBody.value.amount} not convertible to number", 400, callContext) {
        BigDecimal(transactionRequestCommonBody.value.amount)}
      status <- getStatus(challengeThresholdAmount,transactionRequestCommonBodyAmount, transactionRequestType: TransactionRequestType)
      (chargeLevel, callContext) <- Connector.connector.vend.getChargeLevel(BankId(fromAccount.bankId.value), AccountId(fromAccount.accountId.value), viewId, initiator.userId, initiator.name, transactionRequestType.value, fromAccount.currency, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
      }

      chargeLevelAmount <- NewStyle.function.tryons( s"$InvalidNumber chargeLevel.amount: ${chargeLevel.amount} can not be transferred to decimal !", 400, callContext) {
        BigDecimal(chargeLevel.amount)}
      chargeValue <- getChargeValue(chargeLevelAmount,transactionRequestCommonBodyAmount)
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestCommonBody.value.currency, chargeValue))
      // Always create a new Transaction Request
      transactionRequest <- Future{ createTransactionRequestImpl210(TransactionRequestId(generateUUID()), transactionRequestType, fromAccount, toAccount, transactionRequestCommonBody, detailsPlain, status.toString, charge, chargePolicy)} map {
        unboxFullOrFail(_, callContext, s"$InvalidConnectorResponseForCreateTransactionRequestImpl210")
      }

      // If no challenge necessary, create Transaction immediately and put in data store and object to return
      (transactionRequest, callConext) <- status match {
        case TransactionRequestStatus.COMPLETED =>
          for {
            (createdTransactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
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
            _ <- Future {saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId)}
            //update transaction_id filed for varibale 'transactionRequest'
            transactionRequest <- Future(transactionRequest.copy(transaction_ids = createdTransactionId.value))

          } yield {
            logger.debug(s"createTransactionRequestv210.createdTransactionId return: $transactionRequest")
            (transactionRequest, callContext)
          }
        case TransactionRequestStatus.INITIATED =>
          def getUsersForChallenges(bankId: BankId,
                                    accountId: AccountId) = {
            Connector.connector.vend.getAccountAttributesByAccount(bankId, accountId, None) map {
              _._1.map {
                x =>
                  {
                    if(x.find(_.name == "REQUIRED_CHALLENGE_ANSWERS").map(_.value).getOrElse("1").toInt > 1) {
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
            (challengeIds, callContext) <- createChallenges(
              fromAccount.bankId,
              fromAccount.accountId,
              users.toList.flatten.map(_.userId),
              transactionRequestType: TransactionRequestType,
              transactionRequest.id.value,
              scaMethod,
              callContext
            ) map { i =>
              (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
            }

            newChallenge = TransactionRequestChallenge(challengeIds.headOption.getOrElse(""), allowed_attempts = 3, challenge_type = challengeType.getOrElse(TransactionChallengeTypes.OTP_VIA_API.toString))
            _ <- Future (saveTransactionRequestChallenge(transactionRequest.id, newChallenge))
            transactionRequest <- Future(transactionRequest.copy(challenge = newChallenge))
          } yield {
            (transactionRequest, callContext)
          }
        case _ => Future (transactionRequest, callContext)
      }
    }yield{
      logger.debug(transactionRequest)
      (Full(transactionRequest), callContext)
    }
  }

  //place holder for various connector methods that overwrite methods like these, does the actual data access
  protected def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                             fromAccount : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                             status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = Failure(setUnimplementedError)

  /**
    *
    * @param transactionRequestId
    * @param transactionRequestType Support Types: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY
    * @param fromAccount
    * @param toAccount
    * @param transactionRequestCommonBody Body from http request: should have common fields:
    * @param details  This is the details / body of the request (contains all fields in the body)
    * @param status   "INITIATED" "PENDING" "FAILED"  "COMPLETED"
    * @param charge
    * @param chargePolicy  SHARED, SENDER, RECEIVER
    * @return  Always create a new Transaction Request in mapper, and return all the fields
    */
  protected def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                                transactionRequestType: TransactionRequestType,
                                                fromAccount: BankAccount,
                                                toAccount: BankAccount,
                                                transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                                details: String,
                                                status: String,
                                                charge: TransactionRequestCharge,
                                                chargePolicy: String): Box[TransactionRequest] =
    LocalMappedConnector.createTransactionRequestImpl210(
      transactionRequestId: TransactionRequestId,
      transactionRequestType: TransactionRequestType,
      fromAccount: BankAccount,
      toAccount: BankAccount,
      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
      details: String,
      status: String,
      charge: TransactionRequestCharge,
      chargePolicy: String
    )

  def saveTransactionRequestTransaction(transactionRequestId: TransactionRequestId, transactionId: TransactionId) = {
    //put connector agnostic logic here if necessary
    saveTransactionRequestTransactionImpl(transactionRequestId, transactionId)
  }

  protected def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = LocalMappedConnector.saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId)

  def saveTransactionRequestChallenge(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) = {
    //put connector agnostic logic here if necessary
    saveTransactionRequestChallengeImpl(transactionRequestId, challenge)
  }

  protected def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestChallengeImpl(transactionRequestId, challenge)

  protected def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestStatusImpl(transactionRequestId, status)

  def getTransactionRequests(initiator : User, fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    val transactionRequests =
      for {
        fromAccount <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
          s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
        isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId,fromAccount.accountId)), UserNoOwnerView)
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

  def getTransactionRequests210(initiator : User, fromAccount : BankAccount, callContext: Option[CallContext] = None) : Box[(List[TransactionRequest], Option[CallContext])] = {
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

    transactionRequestsNew.map(transactionRequests =>(transactionRequests, callContext))
  }

  def getTransactionRequestStatuses() : Box[TransactionRequestStatus] = {
    for {
      transactionRequestStatuses <- getTransactionRequestStatusesImpl()
    } yield transactionRequestStatuses

  }

  protected def getTransactionRequestStatusesImpl() : Box[TransactionRequestStatus] = Failure(setUnimplementedError)

  protected def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)

  protected def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]] = TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)

  def getTransactionRequestImpl(transactionRequestId: TransactionRequestId, callContext: Option[CallContext]): Box[(TransactionRequest, Option[CallContext])] = TransactionRequests.transactionRequestProvider.vend.getTransactionRequest(transactionRequestId).map(transactionRequest =>(transactionRequest, callContext))

  final def getTransactionRequestTypes(initiator : User, fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
    for {
      isOwner <- booleanToBox(initiator.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId,fromAccount.accountId)), UserNoOwnerView)
      transactionRequestTypes <- getTransactionRequestTypesImpl(fromAccount)
    } yield transactionRequestTypes
  }

  protected def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
    //TODO: write logic / data access
    // Get Transaction Request Types from Props "transactionRequests_supported_types". Default is empty string
    val validTransactionRequestTypes = APIUtil.getPropsValue("transactionRequests_supported_types", "").split(",").map(x => TransactionRequestType(x)).toList
    Full(validTransactionRequestTypes)
  }


  //Note: Now we use validateChallengeAnswer instead, new methods validate over kafka, and move the allowed_attempts guard into API level.
  //It is only used for V140 and V200, has been deprecated from V210.
  @deprecated
  def answerTransactionRequestChallenge(transReqId: TransactionRequestId, answer: String) : Box[Boolean] = {
    val tr= getTransactionRequestImpl(transReqId, None) ?~! s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"

    tr.map(_._1) match {
      case Full(tr: TransactionRequest) =>
        if (tr.challenge.allowed_attempts > 0) {
          if (tr.challenge.challenge_type == TransactionChallengeTypes.OTP_VIA_API.toString) {
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

  def createTransactionAfterChallenge(initiator: User, transReqId: TransactionRequestId) : Box[TransactionRequest] = {
    for {
      (tr, callContext)<- getTransactionRequestImpl(transReqId, None) ?~! s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"
      transId <- makePayment(initiator, BankIdAccountId(BankId(tr.from.bank_id), AccountId(tr.from.account_id)),
        BankIdAccountId (BankId(tr.body.to_sandbox_tan.get.bank_id), AccountId(tr.body.to_sandbox_tan.get.account_id)), BigDecimal (tr.body.value.amount), tr.body.description, TransactionRequestType(tr.`type`)) ?~! InvalidConnectorResponseForMakePayment
      didSaveTransId <- saveTransactionRequestTransaction(transReqId, transId)
      didSaveStatus <- saveTransactionRequestStatusImpl(transReqId, TransactionRequestStatus.COMPLETED.toString)
      //get transaction request again now with updated values
      (tr, callContext) <- getTransactionRequestImpl(transReqId, None)?~! s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"
    } yield {
      tr
    }
  }

  def createTransactionAfterChallengev200(fromAccount: BankAccount, toAccount: BankAccount, transactionRequest: TransactionRequest): Box[TransactionRequest] = {
    for {
      transRequestId <- Full(transactionRequest.id)
      transactionId <- makePaymentv200(
        fromAccount,
        toAccount,
        transactionRequestCommonBody = null,//Note transactionRequestCommonBody started to use from V210
        BigDecimal(transactionRequest.body.value.amount),
        transactionRequest.body.description,
        TransactionRequestType(transactionRequest.`type`),
        "" //Note chargePolicy  started to use from V210
      ) ?~! InvalidConnectorResponseForMakePayment
      didSaveTransId <- saveTransactionRequestTransaction(transRequestId, transactionId)
      didSaveStatus <- saveTransactionRequestStatusImpl(transRequestId, TransactionRequestStatus.COMPLETED.toString)

      transactionRequestUpdated <- Full(transactionRequest.copy(transaction_ids = transactionId.value,status=TransactionRequestStatus.COMPLETED.toString))
    } yield {
      transactionRequestUpdated
    }
  }

  def createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]) : OBPReturnType[Box[TransactionRequest]] = {
    for {
      body <- Future (transactionRequest.body)

      transactionRequestType = transactionRequest.`type`
      transactionRequestId=transactionRequest.id
      (transactionId, callContext) <- TransactionRequestTypes.withName(transactionRequestType) match {
        case SANDBOX_TAN | ACCOUNT | ACCOUNT_OTP =>
          for{
            toSandboxTan <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodySandBoxTanJSON ", 400, callContext){
              body.to_sandbox_tan.get
            }
            toBankId = BankId(toSandboxTan.bank_id)
            toAccountId = AccountId(toSandboxTan.account_id)
            (toAccount, callContext) <- NewStyle.function.getBankAccount(toBankId,toAccountId, callContext)
            sandboxBody = TransactionRequestBodySandBoxTanJSON(
              to = TransactionRequestAccountJsonV140(toBankId.value, toAccountId.value),
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description)
            (transactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequestCommonBody=sandboxBody,
              BigDecimal(sandboxBody.value.amount),
              sandboxBody.description,
              TransactionRequestType(transactionRequestType),
              transactionRequest.charge_policy,
              callContext
            )
          }yield{
            (transactionId, callContext)
          }
        case COUNTERPARTY   =>
          for{
            bodyToCounterparty <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodyCounterpartyJSON", 400, callContext){
              body.to_counterparty.get
            }
            counterpartyId = CounterpartyId(bodyToCounterparty.counterparty_id)
            (toCounterparty,callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(counterpartyId, callContext)
            toAccount <- NewStyle.function.toBankAccount(toCounterparty, callContext)
            counterpartyBody = TransactionRequestBodyCounterpartyJSON(
              to = CounterpartyIdJson(counterpartyId.value),
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description,
              charge_policy = transactionRequest.charge_policy,
              future_date = transactionRequest.future_date)

            (transactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequestCommonBody=counterpartyBody,
              BigDecimal(counterpartyBody.value.amount),
              counterpartyBody.description,
              TransactionRequestType(transactionRequestType),
              transactionRequest.charge_policy,
              callContext
            )
          }yield{
            (transactionId, callContext)
          }
        case SEPA  =>
          for{
            bodyToCounterpartyIBan <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodySEPAJSON", 400, callContext){
              body.to_sepa.get
            }
            toCounterpartyIBan =bodyToCounterpartyIBan.iban
            (toCounterparty, callContext)<- NewStyle.function.getCounterpartyByIban(toCounterpartyIBan, callContext)
            toAccount <- NewStyle.function.toBankAccount(toCounterparty, callContext)
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
              transactionRequestCommonBody=sepaBody,
              BigDecimal(sepaBody.value.amount),
              sepaBody.description,
              TransactionRequestType(transactionRequestType),
              transactionRequest.charge_policy,
              callContext
            )
          }yield{
            (transactionId, callContext)
          }
        case FREE_FORM => for{
          freeformBody <- Future(
            TransactionRequestBodyFreeFormJSON(
              value = AmountOfMoneyJsonV121(body.value.currency, body.value.amount),
              description = body.description
            )
          )
          (transactionId,callContext) <- NewStyle.function.makePaymentv210(
            fromAccount,
            fromAccount,
            transactionRequestCommonBody=freeformBody,
            BigDecimal(freeformBody.value.amount),
            freeformBody.description,
            TransactionRequestType(transactionRequestType),
            transactionRequest.charge_policy,
            callContext
          )
        }yield{
          (transactionId,callContext)
        }
        case SEPA_CREDIT_TRANSFERS => for{

          toSepaCreditTransfers <- NewStyle.function.tryons(s"$TransactionRequestDetailsExtractException It can not extract to $TransactionRequestBodySandBoxTanJSON ", 400, callContext){
            body.to_sepa_credit_transfers.get
          }
          toAccountIban = toSepaCreditTransfers.creditorAccount.iban
          (toAccount, callContext) <- NewStyle.function.getBankAccountByIban(toAccountIban, callContext)
          (createdTransactionId, callContext) <- NewStyle.function.makePaymentv210(
            fromAccount,
            toAccount,
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
        }yield{
          (createdTransactionId,callContext)
        }
        case transactionRequestType => Future((throw new Exception(s"${InvalidTransactionRequestType}: '${transactionRequestType}'. Not supported in this version.")), callContext)
      }

      didSaveTransId <- Future{saveTransactionRequestTransaction(transactionRequestId, transactionId).openOrThrowException(attemptedToOpenAnEmptyBox)}
      didSaveStatus <- Future{saveTransactionRequestStatusImpl(transactionRequestId, TransactionRequestStatus.COMPLETED.toString).openOrThrowException(attemptedToOpenAnEmptyBox)}
      //After `makePaymentv200` and update data for request, we get the new requqest from database again.
      (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, callContext)

    } yield {
      (Full(transactionRequest), callContext)
    }
  }


  /*
    non-standard calls --do not make sense in the regular context but are used for e.g. tests
  */

  def addBankAccount(
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
  ): OBPReturnType[Box[BankAccount]] = Future{(Failure(setUnimplementedError), callContext)}
  
  
  def updateBankAccount(
                         bankId: BankId,
                         accountId: AccountId,
                         accountType: String,
                         accountLabel: String,
                         branchId: String,
                         accountRoutingScheme: String,
                         accountRoutingAddress: String,
                         callContext: Option[CallContext]
                       ): OBPReturnType[Box[BankAccount]] = Future{(Failure(setUnimplementedError), callContext)}
  

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  def createBankAndAccount(
                            bankName: String,
                            bankNationalIdentifier: String,
                            accountNumber: String,
                            accountType: String,
                            accountLabel: String,
                            currency: String,
                            accountHolderName: String,
                            branchId: String,
                            accountRoutingScheme: String,  //added field in V220
                            accountRoutingAddress: String   //added field in V220
                          ): Box[(Bank, BankAccount)] = Failure(setUnimplementedError)

  //generates an unused account number and then creates the sandbox account using that number
  //TODO, this is new style method, it return future, but do not use it yet. only for messageDoc now.
  def createBankAccount(
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
                       ): OBPReturnType[Box[BankAccount]] = Future{(Failure(setUnimplementedError), callContext)}

  //generates an unused account number and then creates the sandbox account using that number
  @deprecated("This return Box, not a future, try to use @createBankAccount instead. ","10-05-2019")
  def createBankAccountLegacy(
                               bankId: BankId,
                               accountId: AccountId,
                               accountType: String,
                               accountLabel: String,
                               currency: String,
                               initialBalance: BigDecimal,
                               accountHolderName: String,
                               branchId: String,
                               accountRoutingScheme: String,
                               accountRoutingAddress: String
                             ): Box[BankAccount] = {
    val uniqueAccountNumber = {
      def exists(number : String) = Connector.connector.vend.accountExists(bankId, number).openOrThrowException(attemptedToOpenAnEmptyBox)

      def appendUntilOkay(number : String) : String = {
        val newNumber = number + Random.nextInt(10)
        if(!exists(newNumber)) newNumber
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
      branchId: String,//added field in V220
      accountRoutingScheme, //added field in V220
      accountRoutingAddress //added field in V220
    )

  }

  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  def createSandboxBankAccount(
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
                              ): Box[BankAccount] = Failure(setUnimplementedError)

  /**
    * A sepecil method:
    *   This used for set account holder for accounts from Adapter. used in side @code.bankconnectors.Connector#updateUserAccountViewsOld
    * But from vJune2017 we introduce the new method `code.model.dataAccess.AuthUser.updateUserAccountViews` instead.
    * New method is much powerful and clear then this one.
    * If you only want to use this method, please double check your design. You need also think about the view, account holders.
    */
  @deprecated("we create new code.model.dataAccess.AuthUser.updateUserAccountViews for June2017 connector, try to use new instead of this","11 September 2018")
  def setAccountHolder(owner : String, bankId: BankId, accountId: AccountId, account_owners: List[String]) : Unit = {
    //    if (account_owners.contains(owner)) { // No need for now, fix it later
    val resourceUserOwner = Users.users.vend.getUserByUserName(owner)
    resourceUserOwner match {
      case Full(owner) => {
        if ( ! accountOwnerExists(owner, bankId, accountId).openOrThrowException(attemptedToOpenAnEmptyBox)) {
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

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  def accountExists(bankId : BankId, accountNumber : String) : Box[Boolean] = Failure(setUnimplementedError)

  //remove an account and associated transactions
  def removeAccount(bankId: BankId, accountId: AccountId) : Box[Boolean]  = Failure(setUnimplementedError)

  //used by transaction import api call to check for duplicates

  //the implementation is responsible for dealing with the amount as a string
  def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount : String, completed : Date, otherAccountHolder : String) : Box[Int] = Failure(setUnimplementedError)
  def createImportedTransaction(transaction: ImporterTransaction) : Box[Transaction]  = Failure(setUnimplementedError)
  def updateAccountBalance(bankId : BankId, accountId : AccountId, newBalance : BigDecimal) : Box[Boolean]  = Failure(setUnimplementedError)
  def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Box[Boolean] = Failure(setUnimplementedError)

  def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Box[Boolean] = Failure(setUnimplementedError)
  
  def updateAccount(bankId: BankId, accountId: AccountId, label: String): Box[Boolean] = Failure(setUnimplementedError)

  def getProducts(bankId : BankId) : Box[List[Product]] = Failure(setUnimplementedError)

  def getProduct(bankId : BankId, productCode : ProductCode) : Box[Product] = Failure(setUnimplementedError)

  //Note: this is a temporary way for compatibility
  //It is better to create the case class for all the connector methods
  def createOrUpdateBranch(branch: Branch): Box[BranchT] = Failure(setUnimplementedError)

  def createOrUpdateBank(
                          bankId: String,
                          fullBankName: String,
                          shortBankName: String,
                          logoURL: String,
                          websiteURL: String,
                          swiftBIC: String,
                          national_identifier: String,
                          bankRoutingScheme: String,
                          bankRoutingAddress: String
                        ): Box[Bank] = Failure(setUnimplementedError)


  def createOrUpdateAtm(atm: Atms.Atm): Box[AtmT] = Failure(setUnimplementedError)


  def createOrUpdateProduct(
                             bankId : String,
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
                             metaLicenceName : String
                           ): Box[Product] = Failure(setUnimplementedError)


  def createOrUpdateFXRate(
                            bankId: String,
                            fromCurrencyCode: String,
                            toCurrencyCode: String,
                            conversionValue: Double,
                            inverseConversionValue: Double,
                            effectiveDate: Date
                          ): Box[FXRate] = Failure(setUnimplementedError)



  def getBranchLegacy(bankId : BankId, branchId: BranchId) : Box[BranchT] = Failure(setUnimplementedError)
  def getBranch(bankId : BankId, branchId: BranchId, callContext: Option[CallContext]) :  Future[Box[(BranchT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError)
  }

  def getBranches(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Future[Box[(List[BranchT], Option[CallContext])]] = Future {
    Failure(setUnimplementedError)
  }

  def getAtmLegacy(bankId : BankId, atmId: AtmId) : Box[AtmT] = Failure(setUnimplementedError)
  def getAtm(bankId : BankId, atmId: AtmId, callContext: Option[CallContext]) : Future[Box[(AtmT, Option[CallContext])]] = Future {
    Failure(setUnimplementedError)
  }

  def getAtms(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Future[Box[(List[AtmT], Option[CallContext])]] = Future {
    Failure(setUnimplementedError)
  }

  //This method is only existing in mapper
  def accountOwnerExists(user: User, bankId: BankId, accountId: AccountId): Box[Boolean]= {
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
  def createViews(bankId: BankId, accountId: AccountId, owner_view: Boolean = false,
                  public_view: Boolean = false,
                  accountants_view: Boolean = false,
                  auditors_view: Boolean = false ) : List[View] = {

    val ownerView: Box[View] =
      if(owner_view)
        Views.views.vend.getOrCreateOwnerView(bankId, accountId, "Owner View")
      else Empty

    val publicView: Box[View]  =
      if(public_view)
        Views.views.vend.getOrCreateCustomPublicView(bankId, accountId, "Public View")
      else Empty

    val accountantsView: Box[View]  =
      if(accountants_view)
        Views.views.vend.getOrCreateAccountantsView(bankId, accountId, "Accountants View")
      else Empty

    val auditorsView: Box[View] =
      if(auditors_view)
        Views.views.vend.getOrCreateAuditorsView(bankId, accountId, "Auditors View")
      else Empty

    List(ownerView, publicView, accountantsView, auditorsView).flatten
  }

  //  def incrementBadLoginAttempts(username:String):Unit
  //
  //  def userIsLocked(username:String):Boolean
  //
  //  def resetBadLoginAttempts(username:String):Unit


  def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = Failure(setUnimplementedError)
  def getCurrentFxRateCached(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
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
    * get transaction request type charge specified by: bankId, accountId, viewId, transactionRequestType.
    */
  def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge] = Failure(setUnimplementedError)


  //////// Following Methods are only existing in some connectors, they are in process,
  /// Please do not move the following methods, for Merge issues.
  //  If you modify these methods, if will make some forks automatically merging broken .
  /**
    * This a Helper method, it is only used in some connectors. Not all the connectors need it yet.
    * This is in progress.
    * Here just return some String to make sure the method return sth, and the API level is working well !
    *
    * @param username
    * @return
    */
  def UpdateUserAccoutViewsByUsername(username: String): Box[Any] = {
    Full(setUnimplementedError)
  }

  def createTransactionAfterChallengev300(
                                           initiator: User,
                                           fromAccount: BankAccount,
                                           transReqId: TransactionRequestId,
                                           transactionRequestType: TransactionRequestType,
                                           callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = Future{(Failure(setUnimplementedError), callContext)}

  def makePaymentv300(
                       initiator: User,
                       fromAccount: BankAccount,
                       toAccount: BankAccount,
                       toCounterparty: CounterpartyTrait,
                       transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                       transactionRequestType: TransactionRequestType,
                       chargePolicy: String,
                       callContext: Option[CallContext]): Future[Box[(TransactionId, Option[CallContext])]] = Future{Failure(setUnimplementedError)}

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
                                    callContext: Option[CallContext]): Future[Box[(TransactionRequest, Option[CallContext])]]  = Future{Failure(setUnimplementedError)}

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


  /**
    * get transaction request type charges
    */
  def getTransactionRequestTypeCharges(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestTypes: List[TransactionRequestType]): Box[List[TransactionRequestTypeCharge]] = {
    val res: List[TransactionRequestTypeCharge] = for {
      trt: TransactionRequestType <- transactionRequestTypes
      trtc: TransactionRequestTypeCharge <- getTransactionRequestTypeCharge(bankId, accountId, viewId, trt)
    } yield { trtc }
    Full(res)
  }

  def createCounterparty(
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
                          callContext: Option[CallContext] = None): Box[(CounterpartyTrait, Option[CallContext])] = Failure(setUnimplementedError)

  def checkCustomerNumberAvailable(
    bankId: BankId,
    customerNumber: String, 
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError), callContext)}

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
                    ): OBPReturnType[Box[Customer]] = Future{(Failure(setUnimplementedError), callContext)}
  
  def updateCustomerScaData(customerId: String, 
                            mobileNumber: Option[String], 
                            email: Option[String],
                            customerNumber: Option[String],
                            callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = 
    Future{(Failure(setUnimplementedError), callContext)}

  def updateCustomerCreditData(customerId: String,
                               creditRating: Option[String],
                               creditSource: Option[String],
                               creditLimit: Option[AmountOfMoney],
                               callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = 
    Future{(Failure(setUnimplementedError), callContext)}

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
      (Failure(setUnimplementedError), callContext)
    }

  def getCustomersByUserId(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer],Option[CallContext])]] = Future{Failure(setUnimplementedError)}

  def getCustomerByCustomerIdLegacy(customerId: String, callContext: Option[CallContext]): Box[(Customer,Option[CallContext])]= Failure(setUnimplementedError)

  def getCustomerByCustomerId(customerId: String, callContext: Option[CallContext]): Future[Box[(Customer,Option[CallContext])]] = Future{Failure(setUnimplementedError)}

  def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] =
    Future{Failure(setUnimplementedError)}

  def getCustomerAddress(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAddress]]] =
    Future{(Failure(setUnimplementedError), callContext)}

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
                            callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = Future{(Failure(setUnimplementedError), callContext)}

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
                            callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = Future{(Failure(setUnimplementedError), callContext)}
  def deleteCustomerAddress(customerAddressId : String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError), callContext)}

  def createTaxResidence(customerId : String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[TaxResidence]] = Future{(Failure(setUnimplementedError), callContext)}

  def getTaxResidence(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[TaxResidence]]] = Future{(Failure(setUnimplementedError), callContext)}

  def deleteTaxResidence(taxResourceId : String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError), callContext)}

  def getCustomers(bankId : BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam] = Nil): Future[Box[List[Customer]]] = Future{Failure(setUnimplementedError)}
  
  def getCustomersByCustomerPhoneNumber(bankId : BankId, phoneNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[List[Customer]]] = Future{(Failure(setUnimplementedError), callContext)}


  def getCheckbookOrders(
                          bankId: String,
                          accountId: String,
                          callContext: Option[CallContext]
                        ): Future[Box[(CheckbookOrdersJson, Option[CallContext])]] = Future{Failure(setUnimplementedError)}

  def getStatusOfCreditCardOrder(
                                  bankId: String,
                                  accountId: String,
                                  callContext: Option[CallContext]
                                ): Future[Box[(List[CardObjectJson], Option[CallContext])]] = Future{Failure(setUnimplementedError)}

  //This method is normally used in obp side, so it has the default mapped implementation  
  def createUserAuthContext(userId: String,
                            key: String,
                            value: String,
                            callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContext]] =
    LocalMappedConnector.createUserAuthContext(userId: String,
      key: String,
      value: String,
      callContext: Option[CallContext])
  //This method is normally used in obp side, so it has the default mapped implementation  
  def createUserAuthContextUpdate(userId: String,
                                  key: String,
                                  value: String,
                                  callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContextUpdate]] =
    LocalMappedConnector.createUserAuthContextUpdate(userId: String,
      key: String,
      value: String,
      callContext: Option[CallContext])

  //This method is normally used in obp side, so it has the default mapped implementation   
  def deleteUserAuthContexts(userId: String,
                             callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    LocalMappedConnector.deleteUserAuthContexts(userId: String,
      callContext: Option[CallContext])

  //This method is normally used in obp side, so it has the default mapped implementation  
  def deleteUserAuthContextById(userAuthContextId: String,
                                callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] =
    LocalMappedConnector.deleteUserAuthContextById(userAuthContextId: String,
      callContext: Option[CallContext])
  //This method is normally used in obp side, so it has the default mapped implementation  
  def getUserAuthContexts(userId : String,
                          callContext: Option[CallContext]): OBPReturnType[Box[List[UserAuthContext]]] =
    LocalMappedConnector.getUserAuthContexts(userId : String,
      callContext: Option[CallContext])

  def createOrUpdateProductAttribute(
                                      bankId: BankId,
                                      productCode: ProductCode,
                                      productAttributeId: Option[String],
                                      name: String,
                                      productAttributeType: ProductAttributeType.Value,
                                      value: String,
                                      callContext: Option[CallContext]
                                    ): OBPReturnType[Box[ProductAttribute]] = Future{(Failure(setUnimplementedError), callContext)}

  def getProductAttributeById(
                               productAttributeId: String,
                               callContext: Option[CallContext]
                             ): OBPReturnType[Box[ProductAttribute]] = Future{(Failure(setUnimplementedError), callContext)}

  def getProductAttributesByBankAndCode(
                                         bank: BankId,
                                         productCode: ProductCode,
                                         callContext: Option[CallContext]
                                       ): OBPReturnType[Box[List[ProductAttribute]]] =
    Future{(Failure(setUnimplementedError), callContext)}

  def deleteProductAttribute(
                              productAttributeId: String,
                              callContext: Option[CallContext]
                            ): OBPReturnType[Box[Boolean]] = Future{(Failure(setUnimplementedError), callContext)}


  def getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountAttribute]] = Future{(Failure(setUnimplementedError), callContext)}
  def getTransactionAttributeById(transactionAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionAttribute]] = Future{(Failure(setUnimplementedError), callContext)}
  
  def createOrUpdateAccountAttribute(
                                      bankId: BankId,
                                      accountId: AccountId,
                                      productCode: ProductCode,
                                      productAttributeId: Option[String],
                                      name: String,
                                      accountAttributeType: AccountAttributeType.Value,
                                      value: String,
                                      callContext: Option[CallContext]
                                    ): OBPReturnType[Box[AccountAttribute]] = Future{(Failure(setUnimplementedError), callContext)}

  def createOrUpdateCustomerAttribute(bankId: BankId, 
                                      customerId: CustomerId,
                                      customerAttributeId: Option[String],
                                      name: String,
                                      attributeType: CustomerAttributeType.Value,
                                      value: String,
                                      callContext: Option[CallContext]
  ): OBPReturnType[Box[CustomerAttribute]] = Future{(Failure(setUnimplementedError), callContext)}

  def createOrUpdateTransactionAttribute(
    bankId: BankId,
    transactionId: TransactionId,
    transactionAttributeId: Option[String],
    name: String,
    attributeType: TransactionAttributeType.Value,
    value: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TransactionAttribute]] = Future{(Failure(setUnimplementedError), callContext)}
  
  
  def createAccountAttributes(bankId: BankId,
                              accountId: AccountId,
                              productCode: ProductCode,
                              accountAttributes: List[ProductAttribute],
                              callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = 
    Future{(Failure(setUnimplementedError), callContext)} 
  
  def getAccountAttributesByAccount(bankId: BankId,
                                    accountId: AccountId,
                                    callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = 
    Future{(Failure(setUnimplementedError), callContext)}

  def getCustomerAttributes(
    bankId: BankId,
    customerId: CustomerId,
    callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAttribute]]] =
    Future{(Failure(setUnimplementedError), callContext)}

  /**
   * get CustomerAttribute according name and values
   * @param bankId CustomerAttribute must belongs the bank
   * @param nameValues key is attribute name, value is attribute values.
   *                   CustomerAttribute name must equals name,
   *                   CustomerAttribute value must be one of values
   * @param callContext
   * @return filtered CustomerAttribute.customerId
   */
  def getCustomerIdByAttributeNameValues(
    bankId: BankId,
    nameValues: Map[String, List[String]],
    callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] =
    Future{(Failure(setUnimplementedError), callContext)}

  def getCustomerAttributesForCustomers(
    customers: List[Customer],
    callContext: Option[CallContext]): OBPReturnType[Box[List[(Customer, List[CustomerAttribute])]]] =
    Future{(Failure(setUnimplementedError), callContext)}
  
  def getTransactionAttributes(
    bankId: BankId,
    transactionId: TransactionId,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[TransactionAttribute]]] = Future{(Failure(setUnimplementedError), callContext)}

  def getCustomerAttributeById(
    customerAttributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[CustomerAttribute]]  =
    Future{(Failure(setUnimplementedError), callContext)}
  
  
  def createOrUpdateCardAttribute(
                                  bankId: Option[BankId],
                                  cardId: Option[String],
                                  cardAttributeId: Option[String],
                                  name: String,
                                  cardAttributeType: CardAttributeType.Value,
                                  value: String,
                                  callContext: Option[CallContext]
                                ): OBPReturnType[Box[CardAttribute]] = Future{(Failure(setUnimplementedError), callContext)}

  def getCardAttributeById(cardAttributeId: String, callContext:Option[CallContext]): OBPReturnType[Box[CardAttribute]] = Future{(Failure(setUnimplementedError), callContext)}
  
  def getCardAttributesFromProvider(cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CardAttribute]]] = Future{(Failure(setUnimplementedError), callContext)}

  def createAccountApplication(
                                productCode: ProductCode,
                                userId: Option[String],
                                customerId: Option[String],
                                callContext: Option[CallContext]
                              ): OBPReturnType[Box[AccountApplication]] = Future{(Failure(setUnimplementedError), callContext)}

  def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[Box[List[AccountApplication]]] =
    Future{(Failure(setUnimplementedError), callContext)}

  def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    Future{(Failure(setUnimplementedError), callContext)}

  def updateAccountApplicationStatus(accountApplicationId:String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    Future{(Failure(setUnimplementedError), callContext)}

  def getOrCreateProductCollection(collectionCode: String,
                                   productCodes: List[String],
                                   callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    Future{(Failure(setUnimplementedError), callContext)}

  def getProductCollection(collectionCode: String,
                           callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    Future{(Failure(setUnimplementedError), callContext)}

  def getOrCreateProductCollectionItem(collectionCode: String,
                                       memberProductCodes: List[String],
                                       callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    Future{(Failure(setUnimplementedError), callContext)}
  def getProductCollectionItem(collectionCode: String,
                               callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    Future{(Failure(setUnimplementedError), callContext)}

  def getProductCollectionItemsTree(collectionCode: String,
                                    bankId: String,
                                    callContext: Option[CallContext]): OBPReturnType[Box[List[(ProductCollectionItem, Product, List[ProductAttribute])]]] =
    Future{(Failure(setUnimplementedError), callContext)}

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
    Future{(Failure(setUnimplementedError), callContext)}

  def getMeetings(
                   bankId : BankId,
                   user: User,
                   callContext: Option[CallContext]
                 ): OBPReturnType[Box[List[Meeting]]] =
    Future{(Failure(setUnimplementedError), callContext)}

  def getMeeting(
                  bankId: BankId,
                  user: User,
                  meetingId : String,
                  callContext: Option[CallContext]
                ): OBPReturnType[Box[Meeting]]=Future{(Failure(setUnimplementedError), callContext)}

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
                             callContext: Option[CallContext]): OBPReturnType[Box[KycCheck]] = Future{(Failure(setUnimplementedError), callContext)}

  def createOrUpdateKycDocument(bankId: String,
                                customerId: String,
                                id: String,
                                customerNumber: String,
                                `type`: String,
                                number: String,
                                issueDate: Date,
                                issuePlace: String,
                                expiryDate: Date,
                                callContext: Option[CallContext]): OBPReturnType[Box[KycDocument]] = Future{(Failure(setUnimplementedError), callContext)}

  def createOrUpdateKycMedia(bankId: String,
                             customerId: String,
                             id: String,
                             customerNumber: String,
                             `type`: String,
                             url: String,
                             date: Date,
                             relatesToKycDocumentId: String,
                             relatesToKycCheckId: String,
                             callContext: Option[CallContext]): OBPReturnType[Box[KycMedia]] = Future{(Failure(setUnimplementedError), callContext)}

  def createOrUpdateKycStatus(bankId: String,
                              customerId: String,
                              customerNumber: String,
                              ok: Boolean,
                              date: Date,
                              callContext: Option[CallContext]): OBPReturnType[Box[KycStatus]] = Future{(Failure(setUnimplementedError), callContext)}

  def getKycChecks(customerId: String,
                   callContext: Option[CallContext]
                  ): OBPReturnType[Box[List[KycCheck]]] = Future{(Failure(setUnimplementedError), callContext)}

  def getKycDocuments(customerId: String,
                      callContext: Option[CallContext]
                     ): OBPReturnType[Box[List[KycDocument]]] = Future{(Failure(setUnimplementedError), callContext)}

  def getKycMedias(customerId: String,
                   callContext: Option[CallContext]
                  ): OBPReturnType[Box[List[KycMedia]]] = Future{(Failure(setUnimplementedError), callContext)}

  def getKycStatuses(customerId: String,
                     callContext: Option[CallContext]
                    ): OBPReturnType[Box[List[KycStatus]]] = Future{(Failure(setUnimplementedError), callContext)}

  def createMessage(user : User,
                    bankId : BankId,
                    message : String,
                    fromDepartment : String,
                    fromPerson : String,
                    callContext: Option[CallContext]) : OBPReturnType[Box[CustomerMessage]] = Future{(Failure(setUnimplementedError), callContext)}

  def makeHistoricalPayment(fromAccount: BankAccount,
                            toAccount: BankAccount,
                            posted: Date,
                            completed: Date,
                            amount: BigDecimal,
                            description: String,
                            transactionRequestType: String,
                            chargePolicy: String,
                            callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = Future{(Failure(setUnimplementedError), callContext)}

  /**
   * DynamicEntity process function
   * @param operation type of operation, this is an enumeration
   * @param entityName DynamicEntity's entity name
   * @param requestBody content of request
   * @param entityId    id of given DynamicEntity
   * @param callContext
   * @return result DynamicEntity process
   */
  def dynamicEntityProcess(operation: DynamicEntityOperation,
                             entityName: String,
                             requestBody: Option[JObject],
                             entityId: Option[String],
                             callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = Future{(Failure(setUnimplementedError), callContext)}
  
  def createDirectDebit(bankId: String,
                        accountId: String,
                        customerId: String,
                        userId: String,
                        counterpartyId: String,
                        dateSigned: Date,
                        dateStarts: Date,
                        dateExpires: Option[Date],
                        callContext: Option[CallContext]): OBPReturnType[Box[DirectDebitTrait]] = Future{(Failure(setUnimplementedError), callContext)}

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
    (Failure(setUnimplementedError), callContext)
  }
}

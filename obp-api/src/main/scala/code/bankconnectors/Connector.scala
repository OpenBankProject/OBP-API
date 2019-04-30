package code.bankconnectors

import java.util.Date
import java.util.UUID.randomUUID

import code.accountapplication.AccountApplication
import code.accountholders.{AccountHolders, MapperAccountHolders}
import code.accountholders.{AccountHolders, MapperAccountHolders}
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.util._
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_1_0._
import code.atms.Atms
import code.bankconnectors.akka.AkkaConnector_vDec2018
import code.bankconnectors.rest.RestConnector_vMar2019
import code.bankconnectors.vJune2017.KafkaMappedConnector_vJune2017
import code.bankconnectors.vMar2017.KafkaMappedConnector_vMar2017
import code.bankconnectors.vSept2018.KafkaMappedConnector_vSept2018
import code.branches.Branches.Branch
import code.branches.Branches.Branch
import code.context.UserAuthContextUpdate
import code.model.toUserExtended
import code.customeraddress.CustomerAddress
import code.fx.FXRate
import code.fx.fx.TTL
import code.management.ImporterAPI.ImporterTransaction
import code.model.dataAccess.ResourceUser
import code.model.toUserExtended
import com.openbankproject.commons.model.Product
import code.transactionChallenge.ExpectedChallengeAnswer
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import code.transactionrequests.TransactionRequests._
import code.transactionrequests.{TransactionRequestTypeCharge, TransactionRequests}
import code.users.Users
import code.util.Helper._
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountApplication, Bank, CounterpartyTrait, CustomerAddress, ProductCollection, ProductCollectionItem, TaxResidence, TransactionRequestStatus, UserAuthContext, _}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.json.Extraction.decompose
import net.liftweb.json.JsonAST.JValue
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.{Helpers, SimpleInjector}
import org.mindrot.jbcrypt.BCrypt

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.math.BigInt
import scala.util.Random
import scala.concurrent.duration._

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
  def getConnectorInstance(connectorVersion: String):Connector = {
    connectorVersion match {
      case "mapped" => LocalMappedConnector
      case "akka_vDec2018" => AkkaConnector_vDec2018
      case "mongodb" => LocalRecordConnector
      case "obpjvm" => ObpJvmMappedConnector
      case "kafka" => KafkaMappedConnector
      case "kafka_JVMcompatible" => KafkaMappedConnector_JVMcompatible
      case "kafka_vMar2017" => KafkaMappedConnector_vMar2017
      case "kafka_vJune2017" => KafkaMappedConnector_vJune2017
      case "kafka_vSept2018" => KafkaMappedConnector_vSept2018
      case "rest_vMar2019" => RestConnector_vMar2019
      case _ => throw new RuntimeException(s"Do not Support this connector version: $connectorVersion")
    }
  }

  val connector = new Inject(buildOne _) {}

  def buildOne: Connector = {
    val connectorProps = APIUtil.getPropsValue("connector").openOrThrowException("connector props filed not set")
    getConnectorInstance(connectorProps)
    
  }

}

trait Connector extends MdcLoggable with CustomJsonFormats{

  val emptyObjectJson: JValue = decompose(Nil)
  
  val messageDocs = ArrayBuffer[MessageDoc]()
  implicit val nameOfConnector = Connector.getClass.getSimpleName
  
  //Move all the cache ttl to Connector, all the sub-connectors share the same cache.
  val bankTTL = getSecondsCache("getBanks")
  val banksTTL = getSecondsCache("getBanks")
  val userTTL = getSecondsCache("getUser")
  val accountTTL = getSecondsCache("getAccount")
  val accountsTTL = getSecondsCache("getAccounts")
  val transactionTTL = getSecondsCache("getTransaction")
  val transactionsTTL = getSecondsCache("getTransactions")
  val transactionRequests210TTL = getSecondsCache("getTransactionRequests210")
  val counterpartiesTTL = getSecondsCache("getCounterparties")
  val counterpartyByCounterpartyIdTTL = getSecondsCache("getCounterpartyByCounterpartyId")
  val counterpartyTrait = getSecondsCache("getCounterpartyTrait")
  val customersByUserIdBoxTTL = getSecondsCache("getCustomersByUserIdBox")
  val memoryCounterpartyTTL = getSecondsCache("createMemoryCounterparty")
  val memoryTransactionTTL = getSecondsCache("createMemoryTransaction") 
  val createCustomerFutureTTL = getSecondsCache("createCustomerFuture")
  val branchesTTL = getSecondsCache("getBranches") 
  val branchTTL = getSecondsCache("getBranch")
  val atmsTTL = getSecondsCache("getAtms")
  val atmTTL = getSecondsCache("getAtm")
  val statusOfCheckbookOrders = getSecondsCache("getStatusOfCheckbookOrdersFuture")
  val statusOfCreditcardOrders = getSecondsCache("getStatusOfCreditCardOrderFuture")
  
  /**
    * This method will return the method name of the current calling method
    * @return
    */
  private def currentMethodName() : String = Thread.currentThread.getStackTrace()(2).getMethodName
  
  //This method is used for testing API<-->Kafka connection. not need sent it to Adapter.
  def getObpApiLoopback(callContext: Option[CallContext]): OBPReturnType[Box[ObpApiLoopback]] = 
  {
    for{
      connectorVersion <- Future {APIUtil.getPropsValue("connector").openOrThrowException("connector props filed not set")}
      startTime <- Future{Helpers.now}
      req <- Future{ObpApiLoopback(connectorVersion, gitCommit, "")}
      obpApiLoopback <- connectorVersion.contains("kafka") match {
        case false => Future{ObpApiLoopback("mapped",gitCommit,"0")}
        case true =>  
          for{
            res <- KafkaMappedConnector_vSept2018.processToFuture[ObpApiLoopback](req)
            endTime <- Future{Helpers.now}
            durationTime <- Future{endTime.getTime - startTime.getTime}
            obpApiLoopback<- Future{res.extract[ObpApiLoopback]}
          } yield {
            obpApiLoopback.copy(durationTime = durationTime.toString)
          }
      }
    } yield {
      (Full(obpApiLoopback), callContext)
    }
  }
  
  def getAdapterInfoFuture(callContext: Option[CallContext]) : Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = Future{Failure(NotImplemented + currentMethodName)}

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
  def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, callContext: Option[CallContext]) : OBPReturnType[Box[String]]= Future{(Failure(NotImplemented + currentMethodName), callContext)}
  // Validates an answer for a challenge and returns if the answer is correct or not
  def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Full(true), callContext)}

  //gets a particular bank handled by this connector
  def getBank(bankId : BankId, callContext: Option[CallContext]) : Box[(Bank, Option[CallContext])] = Failure(NotImplemented + currentMethodName)
  
  def getBankFuture(bankId : BankId, callContext: Option[CallContext]) : Future[Box[(Bank, Option[CallContext])]] = Future(Failure(NotImplemented + currentMethodName))

  //gets banks handled by this connector
  def getBanks(callContext: Option[CallContext]): Box[(List[Bank], Option[CallContext])] = Failure(NotImplemented + currentMethodName)
  
  def getBanksFuture(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = Future {Failure(NotImplemented + currentMethodName)}

  /**
    * 
    * @param username username of the user.
    * @param forceFresh call the MainFrame call, or only get the cache data.
    * @return all the accounts, get from Main Frame.
    */
  def getBankAccountsForUser(username: String, callContext: Option[CallContext]) : Box[(List[InboundAccount], Option[CallContext])] = Failure(NotImplemented + currentMethodName)

  /**
    *
    * @param username username of the user.
    * @param forceFresh call the MainFrame call, or only get the cache data.
    * @return all the accounts, get from Main Frame.
    */
  def getBankAccountsForUserFuture(username: String, callContext: Option[CallContext]) : Future[Box[(List[InboundAccount], Option[CallContext])]] = Future{
    Failure(NotImplemented + currentMethodName)
  }

  /**
    * This method is for get User from external, eg kafka/obpjvm...
    *  getUserId  --> externalUserHelper--> getUserFromConnector --> getUser
    * @param name
    * @param password
    * @return
    */
  def getUser(name: String, password: String): Box[InboundUser]= Failure(NotImplemented + currentMethodName)

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
    getBankAccount(bankId, accountId, None).map(_._1)
  }

  //This one just added the callContext in parameters.
  def getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : Box[(BankAccount, Option[CallContext])]= Failure(NotImplemented + currentMethodName)

  //This one return the Future.
  def getBankAccountFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : OBPReturnType[Box[BankAccount]]=
    Future{(Failure(NotImplemented + nameOf(getBankAccountFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]))),callContext)}

  def getBankAccountsFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[List[BankAccount]]]= Future{Failure(NotImplemented + currentMethodName)}

  def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[(List[CoreAccount], Option[CallContext])] = 
    Failure(NotImplemented + nameOf(getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext])))
  def getCoreBankAccountsFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]]= 
    Future{Failure(NotImplemented + nameOf(getCoreBankAccountsFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext])))}
  
  def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Box[List[AccountHeld]]= Failure(NotImplemented + currentMethodName)
  def getBankAccountsHeldFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[List[AccountHeld]]]= Future {Failure(NotImplemented + currentMethodName)}

  def checkBankAccountExists(bankId : BankId, accountId : AccountId, callContext: Option[CallContext] = None) : Box[(BankAccount, Option[CallContext])]= Failure(NotImplemented + currentMethodName)
  def checkBankAccountExistsFuture(bankId : BankId, accountId : AccountId, callContext: Option[CallContext] = None) : Future[Box[(BankAccount, Option[CallContext])]] = Future {Failure(NotImplemented + currentMethodName)}

  /**
    * This method is just return an empty account to AccountType.
    * It is used for SEPA, Counterparty empty toAccount
    *
    * @return empty bankAccount
    */
  def getEmptyBankAccount(): Box[BankAccount]= Failure(NotImplemented + currentMethodName)

  def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyId: String): Box[Counterparty] = {
    val transactions = getTransactions(bankId, accountId).toList.flatten
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
    val counterparties = getTransactions(bankId, accountId).toList.flatten.map(_.otherAccount)
    Full(counterparties.toSet.toList) //there are many transactions share the same Counterparty, so we need filter the same ones.
  }

  def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty]= Failure(NotImplemented + currentMethodName)

  def getCounterpartyTrait(bankId: BankId, accountId: AccountId, couterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]]= Future{(Failure(NotImplemented + currentMethodName), callContext)}

  def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): Box[(CounterpartyTrait, Option[CallContext])]= Failure(NotImplemented + currentMethodName)

  def getCounterpartyByCounterpartyIdFuture(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = Future{(Failure(NotImplemented + currentMethodName), callContext)}

  
  /**
    * get Counterparty by iban (OtherAccountRoutingAddress field in MappedCounterparty table)
    * This is a helper method that assumes OtherAccountRoutingScheme=IBAN
    */
  def getCounterpartyByIban(iban: String, callContext: Option[CallContext]) : OBPReturnType[Box[CounterpartyTrait]] = Future {(Failure(NotImplemented + currentMethodName), callContext)}

  def getCounterparties(thisBankId: BankId, thisAccountId: AccountId,viewId :ViewId, callContext: Option[CallContext] = None): Box[(List[CounterpartyTrait], Option[CallContext])]= Failure(NotImplemented + currentMethodName)

  def getCounterpartiesFuture(thisBankId: BankId, thisAccountId: AccountId,viewId: ViewId, callContext: Option[CallContext] = None): OBPReturnType[Box[List[CounterpartyTrait]]] = Future {(Failure(NotImplemented + currentMethodName), callContext)}

  def getTransactions(bankId: BankId, accountID: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]]= {
    getTransactions(bankId, accountID, None, queryParams: _*).map(_._1)
  }

  //TODO, here is a problem for return value `List[Transaction]`, this is a normal class, not a trait. It is a big class, 
  // it contains thisAccount(BankAccount object) and otherAccount(Counterparty object)
  def getTransactions(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Box[(List[Transaction], Option[CallContext])]= Failure(NotImplemented + currentMethodName)
  def getTransactionsFuture(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): OBPReturnType[Box[List[Transaction]]] = {
    val result: Box[(List[Transaction], Option[CallContext])] = getTransactions(bankId, accountID, callContext, queryParams: _*)
    Future(result.map(_._1), result.map(_._2).getOrElse(callContext))
  }
  def getTransactionsCore(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Box[(List[TransactionCore], Option[CallContext])]= Failure(NotImplemented + currentMethodName)

  def getTransaction(bankId: BankId, accountID : AccountId, transactionId : TransactionId, callContext: Option[CallContext] = None): Box[(Transaction, Option[CallContext])] = Failure(NotImplemented + currentMethodName)
  def getTransactionFuture(bankId: BankId, accountID : AccountId, transactionId : TransactionId, callContext: Option[CallContext] = None): OBPReturnType[Box[Transaction]] = {
    val result: Box[(Transaction, Option[CallContext])] = getTransaction(bankId, accountID, transactionId, callContext)
    Future(result.map(_._1), result.map(_._2).getOrElse(callContext))
  }

  def getPhysicalCards(user : User) : Box[List[PhysicalCard]] = Failure(NotImplemented + currentMethodName)
  
  def getPhysicalCardsForBank(bank: Bank, user : User) : Box[List[PhysicalCard]] = Failure(NotImplemented + currentMethodName)

  def createOrUpdatePhysicalCard(bankCardNumber: String,
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
                             ) : Box[PhysicalCard] = Failure(NotImplemented + currentMethodName)


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
    * @param toCounterparty The unique identifier of the acounterparty receiving money
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
                      callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]]= Future{(Failure(NotImplemented + currentMethodName), callContext)}


  protected def makePaymentImpl(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String): Box[TransactionId]= Failure(NotImplemented + currentMethodName)



  /*
    Transaction Requests
  */


  // This is used for 1.4.0 See createTransactionRequestv200 for 2.0.0
  def createTransactionRequest(initiator : User, fromAccount : BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) : Box[TransactionRequest] = {
    //set initial status
    //for sandbox / testing: depending on amount, we ask for challenge or not
    val status =
      if (transactionRequestType.value == TransactionChallengeTypes.SANDBOX_TAN.toString && BigDecimal(body.value.amount) < 100) {
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
      val challenge = TransactionRequestChallenge(id = generateUUID(), allowed_attempts = 3, challenge_type = TransactionChallengeTypes.SANDBOX_TAN.toString)
      saveTransactionRequestChallenge(result.id, challenge)
      result = result.copy(challenge = challenge)
    }

    Full(result)
  }


  def createTransactionRequestv200(initiator : User, fromAccount : BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) : Box[TransactionRequest] = {
    //set initial status
    //for sandbox / testing: depending on amount, we ask for challenge or not
    val status =
      if (transactionRequestType.value == TransactionChallengeTypes.SANDBOX_TAN.toString && BigDecimal(body.value.amount) < 1000) {
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
      val challenge = TransactionRequestChallenge(id = generateUUID(), allowed_attempts = 3, challenge_type = TransactionChallengeTypes.SANDBOX_TAN.toString)
      saveTransactionRequestChallenge(result.id, challenge)
      result = result.copy(challenge = challenge)
    }

    Full(result)
  }
  // Set initial status
  def getStatus(challengeThresholdAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal): Future[TransactionRequestStatus.Value] = {
  Future(
    if (transactionRequestCommonBodyAmount < challengeThresholdAmount) {
      // For any connector != mapped we should probably assume that transaction_status_scheduler_delay will be > 0
      // so that getTransactionRequestStatusesImpl needs to be implemented for all connectors except mapped.
      // i.e. if we are certain that saveTransaction will be honored immediately by the backend, then transaction_status_scheduler_delay
      // can be empty in the props file. Otherwise, the status will be set to STATUS_PENDING
      // and getTransactionRequestStatusesImpl needs to be run periodically to update the transaction request status.
      if (APIUtil.getPropsAsLongValue("transaction_status_scheduler_delay").isEmpty )
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
    * @param toCounterparty
    * @param transactionRequestType Support Types: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY
    * @param transactionRequestCommonBody Body from http request: should have common fields
    * @param chargePolicy  SHARED, SENDER, RECEIVER
    * @param detailsPlain This is the details / body of the request (contains all fields in the body)
    * @return Always create a new Transaction Request in mapper, and return all the fields
    */


  // TODO Add challengeType as a parameter to this function
  def createTransactionRequestv210(initiator: User,
                                   viewId: ViewId,
                                   fromAccount: BankAccount,
                                   toAccount: BankAccount,
                                   transactionRequestType: TransactionRequestType,
                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                   detailsPlain: String,
                                   chargePolicy: String, 
                                   callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {

    for{
     // Get the threshold for a challenge. i.e. over what value do we require an out of bounds security challenge to be sent?
      (challengeThreshold, callContext) <- Connector.connector.vend.getChallengeThreshold(fromAccount.bankId.value, fromAccount.accountId.value, viewId.value, transactionRequestType.value, transactionRequestCommonBody.value.currency, initiator.userId, initiator.name, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold ", 400), i._2)
      } 
      challengeThresholdAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetChallengeThreshold. challengeThreshold amount ${challengeThreshold.amount} not convertible to number", 400, callContext) {
        BigDecimal(challengeThreshold.amount)}
      transactionRequestCommonBodyAmount <- NewStyle.function.tryons(s"$InvalidNumber Request Json value.amount ${transactionRequestCommonBody.value.amount} not convertible to number", 400, callContext) {
        BigDecimal(transactionRequestCommonBody.value.amount)}
      status <- getStatus(challengeThresholdAmount,transactionRequestCommonBodyAmount)
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
            (challengeAnswer, callContext) <- createChallenge(fromAccount.bankId, fromAccount.accountId, initiator.userId, transactionRequestType: TransactionRequestType, transactionRequest.id.value, callContext) map { i =>
              (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
            }
      
            challengeId = generateUUID()
            salt = BCrypt.gensalt()
            challengeAnswerHashed = BCrypt.hashpw(challengeAnswer, salt).substring(0, 44)
      
            //Save the challengeAnswer in OBP side, will check it in `Answer Transaction Request` endpoint.
            _ <- Future {ExpectedChallengeAnswer.expectedChallengeAnswerProvider.vend.saveExpectedChallengeAnswer(challengeId, salt, challengeAnswerHashed)} map { 
              unboxFullOrFail(_, callContext, s"$UnknownError ")
            }
      
            // TODO: challenge_type should not be hard coded here. Rather it should be sent as a parameter to this function createTransactionRequestv300
            newChallenge = TransactionRequestChallenge(challengeId, allowed_attempts = 3, challenge_type = TransactionChallengeTypes.SANDBOX_TAN.toString)
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
                                             status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = Failure(NotImplemented + currentMethodName)

  /**
    *
    * @param transactionRequestId
    * @param transactionRequestType Support Types: SANDBOX_TAN, FREE_FORM, SEPA and COUNTERPARTY
    * @param fromAccount
    * @param toAccount
    * @param toCounterparty
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

  protected def getTransactionRequestStatusesImpl() : Box[TransactionRequestStatus] = Failure(NotImplemented + currentMethodName)

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
          if (tr.challenge.challenge_type == TransactionChallengeTypes.SANDBOX_TAN.toString) {
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
        case SANDBOX_TAN =>
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
  ): Box[(Bank, BankAccount)] = Failure(NotImplemented + currentMethodName)

  //generates an unused account number and then creates the sandbox account using that number
  def createSandboxBankAccount(
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
  ): Box[BankAccount] = Failure(NotImplemented + currentMethodName)

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
  def accountExists(bankId : BankId, accountNumber : String) : Box[Boolean] = Failure(NotImplemented + currentMethodName)

  //remove an account and associated transactions
  def removeAccount(bankId: BankId, accountId: AccountId) : Box[Boolean]  = Failure(NotImplemented + currentMethodName)

  //used by transaction import api call to check for duplicates

  //the implementation is responsible for dealing with the amount as a string
  def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount : String, completed : Date, otherAccountHolder : String) : Box[Int] = Failure(NotImplemented + currentMethodName)
  def createImportedTransaction(transaction: ImporterTransaction) : Box[Transaction]  = Failure(NotImplemented + currentMethodName)
  def updateAccountBalance(bankId : BankId, accountId : AccountId, newBalance : BigDecimal) : Box[Boolean]  = Failure(NotImplemented + currentMethodName)
  def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Box[Boolean] = Failure(NotImplemented + currentMethodName)

  def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Box[Boolean] = Failure(NotImplemented + currentMethodName)

  def getProducts(bankId : BankId) : Box[List[Product]] = Failure(NotImplemented + currentMethodName)

  def getProduct(bankId : BankId, productCode : ProductCode) : Box[Product] = Failure(NotImplemented + currentMethodName)

  //Note: this is a temporary way for compatibility
  //It is better to create the case class for all the connector methods
  def createOrUpdateBranch(branch: Branch): Box[BranchT] = Failure(NotImplemented + currentMethodName)
  
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
  ): Box[Bank] = Failure(NotImplemented + currentMethodName)


  def createOrUpdateAtm(atm: Atms.Atm): Box[AtmT] = Failure(NotImplemented + currentMethodName)


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
                       ): Box[Product] = Failure(NotImplemented + currentMethodName)


  def createOrUpdateFXRate(
                            bankId: String,
                            fromCurrencyCode: String,
                            toCurrencyCode: String,
                            conversionValue: Double,
                            inverseConversionValue: Double,
                            effectiveDate: Date
                          ): Box[FXRate] = Failure(NotImplemented + currentMethodName)



  def getBranch(bankId : BankId, branchId: BranchId) : Box[BranchT] = Failure(NotImplemented + currentMethodName)
  def getBranchFuture(bankId : BankId, branchId: BranchId, callContext: Option[CallContext]) :  Future[Box[(BranchT, Option[CallContext])]] = Future {
    Failure(NotImplemented + currentMethodName)
  }

  def getBranchesFuture(bankId: BankId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Future[Box[(List[BranchT], Option[CallContext])]] = Future {
    Failure(NotImplemented + currentMethodName)
  }

  def getAtm(bankId : BankId, atmId: AtmId) : Box[AtmT] = Failure(NotImplemented + currentMethodName)
  def getAtmFuture(bankId : BankId, atmId: AtmId, callContext: Option[CallContext]) : Future[Box[(AtmT, Option[CallContext])]] = Future {
    Failure(NotImplemented + currentMethodName)
  }

  def getAtmsFuture(bankId: BankId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Future[Box[(List[AtmT], Option[CallContext])]] = Future {
    Failure(NotImplemented + currentMethodName)
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
        Views.views.vend.getOrCreatePublicView(bankId, accountId, "Public View")
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


  def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = Failure(NotImplemented + currentMethodName)
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
  def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge] = Failure(NotImplemented + currentMethodName)
  
  
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
    Full(NotImplemented + currentMethodName+".Only some connectors need this method !")
  }
  
  def createTransactionAfterChallengev300(
    initiator: User,
    fromAccount: BankAccount,
    transReqId: TransactionRequestId,
    transactionRequestType: TransactionRequestType,
    callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = Future{(Failure(NotImplemented + currentMethodName +".Only some connectors need this method !"), callContext)}
  
  def makePaymentv300(
    initiator: User,
    fromAccount: BankAccount,
    toAccount: BankAccount,
    toCounterparty: CounterpartyTrait,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    transactionRequestType: TransactionRequestType,
    chargePolicy: String, 
    callContext: Option[CallContext]): Future[Box[(TransactionId, Option[CallContext])]] = Future{Failure(NotImplemented + currentMethodName +".Only some connectors need this method !")}
  
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
    callContext: Option[CallContext]): Future[Box[(TransactionRequest, Option[CallContext])]]  = Future{Failure(NotImplemented + currentMethodName+".Only some connectors need this method !")}
  
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
    callContext: Option[CallContext] = None): Box[(CounterpartyTrait, Option[CallContext])] = Failure(NotImplemented + currentMethodName)

  def checkCustomerNumberAvailableFuture(
    bankId : BankId, 
    customerNumber : String
  ) : Future[Box[Boolean]] = Future{Failure(NotImplemented + currentMethodName())}
  
  def createCustomerFuture(
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
                      nameSuffix: String
                    ): Future[Box[Customer]] = Future{Failure(NotImplemented + currentMethodName())}

  def getCustomersByUserIdFuture(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer],Option[CallContext])]] = Future{Failure(NotImplemented + currentMethodName+"getCustomersByUserIdFuture in Connector!")}

  def getCustomerByCustomerId(customerId: String, callContext: Option[CallContext]): Box[(Customer,Option[CallContext])]= Failure(NotImplemented + currentMethodName+"getCustomersByUserIdFuture in Connector!")
  
  def getCustomerByCustomerIdFuture(customerId: String, callContext: Option[CallContext]): Future[Box[(Customer,Option[CallContext])]] = Future{Failure(NotImplemented + currentMethodName+"getCustomerByCustomerIdFuture in Connector!")}

  def getCustomerByCustomerNumberFuture(customerNumber: String, bankId : BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] = 
    Future{Failure(NotImplemented + currentMethodName+"getCustomerByCustomerNumberFuture in Connector!")}

  def getCustomerAddress(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAddress]]] = 
    Future{(Failure(NotImplemented + currentMethodName+"getCustomerAddress in Connector!"), callContext)}
  
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
                            callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = Future{(Failure(NotImplemented + currentMethodName+"createCustomerAddress in Connector!"), callContext)}
  
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
                            callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = Future{(Failure(NotImplemented + currentMethodName+"updateCustomerAddress in Connector!"), callContext)}
  def deleteCustomerAddress(customerAddressd : String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(NotImplemented + currentMethodName+"deleteCustomerAddress in Connector!"), callContext)}

  def createTaxResidence(customerId : String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[TaxResidence]] = Future{(Failure(NotImplemented + currentMethodName+"postTaxResidence in Connector!"), callContext)}

  def getTaxResidence(customerId : String, callContext: Option[CallContext]): OBPReturnType[Box[List[TaxResidence]]] = Future{(Failure(NotImplemented + currentMethodName+"getTaxResidence in Connector!"), callContext)}

  def deleteTaxResidence(taxResourceId : String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = Future{(Failure(NotImplemented + currentMethodName+"deleteTaxResidence in Connector!"), callContext)}

  def getCustomersFuture(bankId : BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] = Future{Failure(NotImplemented + currentMethodName+"getCustomersFuture in Connector!")}

  
  def getCheckbookOrdersFuture(
    bankId: String, 
    accountId: String, 
    callContext: Option[CallContext]
  ): Future[Box[(CheckbookOrdersJson, Option[CallContext])]] = Future{Failure(NotImplemented + currentMethodName)}
  
  def getStatusOfCreditCardOrderFuture(
    bankId: String, 
    accountId: String, 
    callContext: Option[CallContext]
  ): Future[Box[(List[CardObjectJson], Option[CallContext])]] = Future{Failure(NotImplemented + currentMethodName)}

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
      attributType: ProductAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[Box[ProductAttribute]] = Future{(Failure(NotImplemented + currentMethodName), callContext)}
  
  def getProductAttributeById(
      productAttributeId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[Box[ProductAttribute]] = Future{(Failure(NotImplemented + currentMethodName), callContext)}

  def getProductAttributesByBankAndCode(
                                         bank: BankId,
                                         productCode: ProductCode,
                                         callContext: Option[CallContext]
                                       ): OBPReturnType[Box[List[ProductAttribute]]] =
    Future{(Failure(NotImplemented + currentMethodName), callContext)}
  
  def deleteProductAttribute(
    productAttributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Boolean]] = Future{(Failure(NotImplemented + currentMethodName), callContext)}


  def createOrUpdateAccountAttribute(
      bankId: BankId,
      accountId: AccountId,
      productCode: ProductCode,
      productAttributeId: Option[String],
      name: String,
      attributType: AccountAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
  ): OBPReturnType[Box[AccountAttribute]] = Future{(Failure(NotImplemented + currentMethodName), callContext)}

  def createAccountApplication(
    productCode: ProductCode,
    userId: Option[String],
    customerId: Option[String],
    callContext: Option[CallContext]
  ): OBPReturnType[Box[AccountApplication]] = Future{(Failure(NotImplemented + currentMethodName), callContext)}

  def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[Box[List[AccountApplication]]] =
    Future{(Failure(NotImplemented + currentMethodName), callContext)}

  def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    Future{(Failure(NotImplemented + currentMethodName), callContext)}

  def updateAccountApplicationStatus(accountApplicationId:String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] =
    Future{(Failure(NotImplemented + currentMethodName), callContext)}

  def getOrCreateProductCollection(collectionCode: String, 
                                   productCodes: List[String], 
                                   callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    Future{(Failure(NotImplemented + currentMethodName), callContext)}
  
  def getProductCollection(collectionCode: String,
                           callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] =
    Future{(Failure(NotImplemented + currentMethodName), callContext)}

  def getOrCreateProductCollectionItem(collectionCode: String,
                                       memberProductCodes: List[String],
                                       callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    Future{(Failure(NotImplemented + currentMethodName), callContext)}
  def getProductCollectionItem(collectionCode: String,
                               callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] =
    Future{(Failure(NotImplemented + currentMethodName), callContext)}  
  
  def getProductCollectionItemsTree(collectionCode: String, 
                                    bankId: String,
                                    callContext: Option[CallContext]): OBPReturnType[Box[List[(ProductCollectionItem, Product, List[ProductAttribute])]]] =
    Future{(Failure(NotImplemented + currentMethodName), callContext)}
  
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
    Future{(Failure(NotImplemented + currentMethodName), callContext)}
  
  def getMeetings(
    bankId : BankId, 
    user: User,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[List[Meeting]]] = 
    Future{(Failure(NotImplemented + currentMethodName), callContext)}
  
  def getMeeting(
    bankId: BankId,
    user: User, 
    meetingId : String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[Meeting]]=Future{(Failure(NotImplemented + currentMethodName), callContext)}
}
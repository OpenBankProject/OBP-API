package code.bankconnectors

import java.util.Date

import code.accountholder.{AccountHolders, MapperAccountHolders}
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages
import code.api.v2_1_0._
import code.atms.Atms
import code.atms.Atms.{AtmId, AtmT}
import code.bankconnectors.vJune2017.{InboundAccountJune2017, KafkaMappedConnector_vJune2017}
import code.bankconnectors.vMar2017.KafkaMappedConnector_vMar2017
import code.branches.Branches.{Branch, BranchId, BranchT}
import code.branches.{InboundAdapterInfo, MappedBranch}
import code.fx.FXRate
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.counterparties.{Counterparties, CounterpartyTrait, MappedCounterparty}
import code.model.dataAccess.ResourceUser
import code.model.{Transaction, TransactionRequestType, User, _}
import code.products.Products.{Product, ProductCode}
import code.transactionrequests.TransactionRequests._
import code.transactionrequests.{TransactionRequestTypeCharge, TransactionRequests}
import code.users.Users
import code.util.Helper._
import code.views.Views
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._
import net.liftweb.util.{Props, SimpleInjector}

import scala.collection.mutable.ArrayBuffer
import scala.math.BigInt
import scala.util.Random


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

  import scala.reflect.runtime.universe._
  def getObjectInstance(clsName: String):Connector = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val module = mirror.staticModule(clsName)
    mirror.reflectModule(module).instance.asInstanceOf[Connector]
  }

  val connector = new Inject(buildOne _) {}

  def buildOne: Connector = {
    val connectorProps = Props.get("connector").openOrThrowException("no connector set")

    connectorProps match {
      case "mapped" => LocalMappedConnector
      case "mongodb" => LocalRecordConnector
      case "obpjvm" => ObpJvmMappedConnector
      case "kafka" => KafkaMappedConnector
      case "kafka_JVMcompatible" => KafkaMappedConnector_JVMcompatible
      case "kafka_vJune2017" => KafkaMappedConnector_vJune2017
      case "kafka_vMar2017" => KafkaMappedConnector_vMar2017
      case matchKafkaVersion(version) => getObjectInstance(s"""code.bankconnectors.KafkaMappedConnector_v${version}""")
    }
  }

}

class OBPQueryParam
trait OBPOrder { def orderValue : Int }
object OBPOrder {
  def apply(s: Option[String]): OBPOrder = s match {
    case Some("asc") => OBPAscending
    case Some("ASC")=> OBPAscending
    case _ => OBPDescending
  }
}
object OBPAscending extends OBPOrder { def orderValue = 1 }
object OBPDescending extends OBPOrder { def orderValue = -1}
case class OBPLimit(value: Int) extends OBPQueryParam
case class OBPOffset(value: Int) extends OBPQueryParam
case class OBPFromDate(value: Date) extends OBPQueryParam
case class OBPToDate(value: Date) extends OBPQueryParam
case class OBPOrdering(field: Option[String], order: OBPOrder) extends OBPQueryParam
case class OBPConsumerId(value: String) extends OBPQueryParam
case class OBPUserId(value: String) extends OBPQueryParam
case class OBPUrl(value: String) extends OBPQueryParam
case class OBPAppName(value: String) extends OBPQueryParam
case class OBPImplementedByPartialFunction(value: String) extends OBPQueryParam
case class OBPImplementedInVersion(value: String) extends OBPQueryParam
case class OBPVerb(value: String) extends OBPQueryParam

//Note: this is used for connector method: 'def getUser(name: String, password: String): Box[InboundUser]'
case class InboundUser(
  email: String,
  password: String,
  displayName: String
)
// This is the common InboundAccount from all Kafka/remote, not finished yet.
trait InboundAccountCommon{
  def errorCode: String
  def cbsToken: String
  def bankId: String
  def branchId: String
  def accountId: String
  def accountNumber: String
  def accountType: String
  def balanceAmount: String
  def balanceCurrency: String
  def owners: List[String]
  def viewsToGenerate: List[String]
  def bankRoutingScheme:String
  def bankRoutingAddress:String
  def branchRoutingScheme:String
  def branchRoutingAddress:String
  def accountRoutingScheme:String
  def accountRoutingAddress:String
}

trait Connector extends MdcLoggable{

  //We have the Connector define its BankAccount implementation here so that it can
  //have access to the implementation details (e.g. the ability to set the balance) in
  //the implementation of makePaymentImpl
  type AccountType <: BankAccount


  val messageDocs = ArrayBuffer[MessageDoc]()

  implicit val nameOfConnector = Connector.getClass.getSimpleName

  def getAdapterInfo(): Box[InboundAdapterInfo]

  // Gets current challenge level for transaction request
  // Transaction request challenge threshold. Level at which challenge is created and needs to be answered
  // before we attempt to create a transaction on the south side
  // The Currency is EUR. Connector implementations may convert the value to the transaction request currency.
  // Connector implementation may well provide dynamic response
  def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String): AmountOfMoney

  //Gets current charge level for transaction request
  def getChargeLevel(bankId: BankId,
                     accountId: AccountId,
                     viewId: ViewId,
                     userId: String,
                     userName: String,
                     transactionRequestType: String,
                     currency: String): Box[AmountOfMoney]

  // Initiate creating a challenge for transaction request and returns an id of the challenge
  def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String) : Box[String]
  // Validates an answer for a challenge and returs if the answer is correct or not
  def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String) : Box[Boolean]

  //gets a particular bank handled by this connector
  def getBank(bankId : BankId) : Box[Bank]

  //gets banks handled by this connector
  def getBanks(): Box[List[Bank]]

  def getBankAccounts(accounts: List[(BankId, AccountId)]) : List[BankAccount] = {
    for {
      acc <- accounts
      a <- getBankAccount(acc._1, acc._2)
    } yield a
  }

  //Not implement yet, this will be called by AuthUser.updateUserAccountViews2
  //when it is stable, will call this method.
  def getBankAccounts(username: String) : Box[List[InboundAccountJune2017]] = Empty

  /**
    * This method is for get User from external, eg kafka/obpjvm...
    *  getUserId  --> externalUserHelper--> getUserFromConnector --> getUser
    * @param name
    * @param password
    * @return
    */
  def getUser(name: String, password: String): Box[InboundUser]

  /**
    * This is a helper method
    * for remote user(means the user will get from kafka) to update the views, accountHolders for OBP side
    * It depends different use cases, normally (also see it in KafkaMappedConnector_vJune2017.scala)
    *
    * @param user the user is from remote side
    */
  @deprecated("Now move it to AuthUser.updateUserAccountViews","17-07-2017")
  def updateUserAccountViewsOld(user: ResourceUser) = {}

  def getBankAccount(bankId : BankId, accountId : AccountId) : Box[AccountType]

  /**
    * This method is just return an empty account to AccountType.
    * It is used for SEPA, Counterparty empty toAccount
    *
    * @return empty bankAccount
    */
  def getEmptyBankAccount(): Box[AccountType]

  def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyID: String): Box[Counterparty] = {
    // Please note that Metadata and Transaction can be at different locations
    // Obtain all necessary data and then intersect they
    val metadata: List[CounterpartyMetadata] = Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterpartyID).toList
    val list: List[Transaction] = getTransactions(bankId, accountId).toList.flatten
    val x = for {
      l <- list
      m <- metadata if l.otherAccount.thisAccountId.value == m.getAccountNumber
    } yield {
      getCounterpartyFromTransaction(bankId, accountId, m, l).toList
    }
    x.flatten match {
      case List() => Empty
      case x :: xs => Full(x)
    }
  }

  def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId): List[Counterparty] = {
    // Please note that Metadata and Transaction can be at different locations
    // Obtain all necessary data and then intersect they
    val metadata: List[CounterpartyMetadata] = Counterparties.counterparties.vend.getMetadatas(bankId, accountId)
    val list: List[Transaction] = getTransactions(bankId, accountId).toList.flatten
    val x = for {
      l <- list
      m <- metadata if l.otherAccount.thisAccountId.value == m.getAccountNumber
    } yield {
      getCounterpartyFromTransaction(bankId, accountId, m, l).toList
    }
    x.flatten
  }

  def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty]

  def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait]

  /**
    * get Counterparty by iban (OtherAccountRoutingAddress field in MappedCounterparty table)
    * This is a helper method that assumes OtherAccountRoutingScheme=IBAN
    */
  def getCounterpartyByIban(iban: String): Box[CounterpartyTrait]

  def getCounterparties(thisBankId: BankId, thisAccountId: AccountId,viewId :ViewId): Box[List[CounterpartyTrait]]

  def getTransactions(bankId: BankId, accountID: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]]

  def getTransaction(bankId: BankId, accountID : AccountId, transactionId : TransactionId): Box[Transaction]

  def getPhysicalCards(user : User) : List[PhysicalCard]

  def getPhysicalCardsForBank(bank: Bank, user : User) : List[PhysicalCard]

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
                             ) : Box[PhysicalCard]



  //gets the users who are the legal owners/holders of the account
  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = {
    AccountHolders.accountHolders.vend.getAccountHolders(bankId, accountId)
  }

  def getCounterpartyFromTransaction(thisBankId : BankId, thisAccountId : AccountId, metadata : CounterpartyMetadata, t: Transaction) : Box[Counterparty] = {
    //because we don't have a db backed model for OtherBankAccounts, we need to construct it from an
    //OtherBankAccountMetadata and a transaction
         Full(
           new Counterparty(
            //counterparty id is defined to be the id of its metadata as we don't actually have an id for the counterparty itself
            counterPartyId = metadata.metadataId,
            label = metadata.getHolder,
            nationalIdentifier = t.otherAccount.nationalIdentifier,
            otherBankRoutingAddress = None,
            otherAccountRoutingAddress = t.otherAccount.otherAccountRoutingAddress,
            thisAccountId = AccountId(metadata.getAccountNumber),
            thisBankId = t.otherAccount.thisBankId,
            kind = t.otherAccount.kind,
            otherBankId = thisBankId,
            otherAccountId = thisAccountId,
            alreadyFoundMetadata = Some(metadata),
            name = "",
            otherBankRoutingScheme = "",
            otherAccountRoutingScheme="",
            otherAccountProvider = "",
            isBeneficiary = true
          )
         )
  }

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
                  amt : BigDecimal, description : String) : Box[TransactionId] = {
    for{
      fromAccount <- getBankAccount(fromAccountUID.bankId, fromAccountUID.accountId) ?~
        s"account ${fromAccountUID.accountId} not found at bank ${fromAccountUID.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
      toAccount <- getBankAccount(toAccountUID.bankId, toAccountUID.accountId) ?~
        s"account ${toAccountUID.accountId} not found at bank ${toAccountUID.bankId}"
      sameCurrency <- booleanToBox(fromAccount.currency == toAccount.currency, {
        s"Cannot send payment to account with different currency (From ${fromAccount.currency} to ${toAccount.currency}"
      })
      isPositiveAmtToSend <- booleanToBox(amt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. ($amt)")
      //TODO: verify the amount fits with the currency -> e.g. 12.543 EUR not allowed, 10.00 JPY not allowed, 12.53 EUR allowed
      // Note for 'new MappedCounterparty()' in the following :
      // We update the makePaymentImpl in V210, added the new parameter 'toCounterparty: CounterpartyTrait' for V210
      // But in V200 or before, we do not used the new parameter toCounterparty. So just keep it empty.
      transactionId <- makePaymentImpl(fromAccount, toAccount, new MappedCounterparty(), amt, description,
                                       TransactionRequestType(""), //Note TransactionRequestType only support in V210
                                       "") //Note chargePolicy only support in V210
    } yield transactionId
  }

  /**
    * \
    *
    * @param initiator The user attempting to make the payment
    * @param fromAccountUID The unique identifier of the account sending money
    * @param toAccountUID The unique identifier of the account receiving money
    * @param toCounterparty The unique identifier of the acounterparty receiving money
    * @param amount The amount of money to send ( > 0 )
    * @param transactionRequestType user input: SEPA, SANDBOX_TAN, FREE_FORM, COUNTERPARTY
    * @return The id of the sender's new transaction,
    */
  def makePaymentv200(initiator: User,
                      fromAccountUID: BankIdAccountId,
                      toAccountUID: BankIdAccountId,
                      toCounterparty: CounterpartyTrait,
                      amount: BigDecimal,
                      description: String,
                      transactionRequestType: TransactionRequestType,
                      chargePolicy: String): Box[TransactionId] = {
    for {
      // Note: These following guards are checked in AIP level (maybe some other function call it, so leave the guards here)
      fromAccount <- getBankAccount(fromAccountUID.bankId, fromAccountUID.accountId) ?~
        s"account ${fromAccountUID.accountId} not found at bank ${fromAccountUID.bankId}"
      isMapped: Boolean <- tryo{Props.get("connector", "").equalsIgnoreCase("mapped")}
      toAccount <-if(isMapped || transactionRequestType.value.equals("SANDBOX_TAN")){
        getBankAccount(toAccountUID.bankId, toAccountUID.accountId) ?~ s"account ${toAccountUID.accountId} not found at bank ${toAccountUID.bankId}"
      }else{
        getEmptyBankAccount()
      } ?~ "code.bankconnectors.Connector.makePatmentV200 method toAccount is Empty!" //The error is for debugging not for showing to browser

      transactionId <- makePaymentImpl(fromAccount, toAccount, toCounterparty, amount, description, transactionRequestType, chargePolicy) ?~
        "code.bankconnectors.Connector.makePatmentV200.makePaymentImpl return Empty!" //The error is for debugging not for showing to browser
    } yield transactionId
  }


  protected def makePaymentImpl(fromAccount: AccountType, toAccount: AccountType, toCounterparty: CounterpartyTrait, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String): Box[TransactionId]


  /*
    Transaction Requests
  */


  // This is used for 1.4.0 See createTransactionRequestv200 for 2.0.0
  def createTransactionRequest(initiator : User, fromAccount : BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) : Box[TransactionRequest] = {
    //set initial status
    //for sandbox / testing: depending on amount, we ask for challenge or not
    val status =
      if (transactionRequestType.value == TransactionRequests.CHALLENGE_SANDBOX_TAN && BigDecimal(body.value.amount) < 100) {
        TransactionRequests.STATUS_COMPLETED
      } else {
        TransactionRequests.STATUS_INITIATED
      }



    //create a new transaction request
    val request = for {
      fromAccountType <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
        s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
      toAccountType <- getBankAccount(toAccount.bankId, toAccount.accountId) ?~
        s"account ${toAccount.accountId} not found at bank ${toAccount.bankId}"
      rawAmt <- tryo { BigDecimal(body.value.amount) } ?~! s"amount ${body.value.amount} not convertible to number"
      sameCurrency <- booleanToBox(fromAccount.currency == toAccount.currency, {
        s"Cannot send payment to account with different currency (From ${fromAccount.currency} to ${toAccount.currency}"
      })
      isPositiveAmtToSend <- booleanToBox(rawAmt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. (${rawAmt})")
      // Version 200 below has more support for charge
      charge = TransactionRequestCharge("Charge for completed transaction", AmountOfMoney(body.value.currency, "0.00"))
      transactionRequest <- createTransactionRequestImpl(TransactionRequestId(java.util.UUID.randomUUID().toString), transactionRequestType, fromAccount, toAccount, body, status, charge)
    } yield transactionRequest

    //make sure we get something back
    var result = request.openOrThrowException("Exception: Couldn't create transactionRequest")

    //if no challenge necessary, create transaction immediately and put in data store and object to return
    if (status == TransactionRequests.STATUS_COMPLETED) {
      val createdTransactionId = Connector.connector.vend.makePayment(initiator, BankIdAccountId(fromAccount.bankId, fromAccount.accountId),
        BankIdAccountId(toAccount.bankId, toAccount.accountId), BigDecimal(body.value.amount), body.description)

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
      val challenge = TransactionRequestChallenge(id = java.util.UUID.randomUUID().toString, allowed_attempts = 3, challenge_type = TransactionRequests.CHALLENGE_SANDBOX_TAN)
      saveTransactionRequestChallenge(result.id, challenge)
      result = result.copy(challenge = challenge)
    }

    Full(result)
  }


  def createTransactionRequestv200(initiator : User, fromAccount : BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, body: TransactionRequestBody) : Box[TransactionRequest] = {
    //set initial status
    //for sandbox / testing: depending on amount, we ask for challenge or not
    val status =
      if (transactionRequestType.value == TransactionRequests.CHALLENGE_SANDBOX_TAN && BigDecimal(body.value.amount) < 1000) {
        TransactionRequests.STATUS_COMPLETED
      } else {
        TransactionRequests.STATUS_INITIATED
      }


    // Always create a new Transaction Request
    val request = for {
      fromAccountType <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
        s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount) == true || hasEntitlement(fromAccount.bankId.value, initiator.userId, CanCreateAnyTransactionRequest) == true , ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
      toAccountType <- getBankAccount(toAccount.bankId, toAccount.accountId) ?~
        s"account ${toAccount.accountId} not found at bank ${toAccount.bankId}"
      rawAmt <- tryo { BigDecimal(body.value.amount) } ?~! s"amount ${body.value.amount} not convertible to number"
       // isValidTransactionRequestType is checked at API layer. Maybe here too.
      isPositiveAmtToSend <- booleanToBox(rawAmt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. (${rawAmt})")

      // For now, arbitary charge value to demonstrate PSD2 charge transparency principle. Eventually this would come from Transaction Type? 10 decimal places of scaling so can add small percentage per transaction.
      chargeValue <- tryo {(BigDecimal(body.value.amount) * 0.0001).setScale(10, BigDecimal.RoundingMode.HALF_UP).toDouble} ?~! s"could not create charge for ${body.value.amount}"
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(body.value.currency, chargeValue.toString()))

      transactionRequest <- createTransactionRequestImpl(TransactionRequestId(java.util.UUID.randomUUID().toString), transactionRequestType, fromAccount, toAccount, body, status, charge)
    } yield transactionRequest

    //make sure we get something back
    var result = request.openOrThrowException("Exception: Couldn't create transactionRequest")

    // If no challenge necessary, create Transaction immediately and put in data store and object to return
    if (status == TransactionRequests.STATUS_COMPLETED) {
      // Note for 'new MappedCounterparty()' in the following :
      // We update the makePaymentImpl in V210, added the new parameter 'toCounterparty: CounterpartyTrait' for V210
      // But in V200 or before, we do not used the new parameter toCounterparty. So just keep it empty.
      val createdTransactionId = Connector.connector.vend.makePaymentv200(initiator,
                                                                          BankIdAccountId(fromAccount.bankId, fromAccount.accountId),
                                                                          BankIdAccountId(toAccount.bankId, toAccount.accountId),
                                                                          new MappedCounterparty(),
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
      val challenge = TransactionRequestChallenge(id = java.util.UUID.randomUUID().toString, allowed_attempts = 3, challenge_type = TransactionRequests.CHALLENGE_SANDBOX_TAN)
      saveTransactionRequestChallenge(result.id, challenge)
      result = result.copy(challenge = challenge)
    }

    Full(result)
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
                                   toCounterparty: CounterpartyTrait,
                                   transactionRequestType: TransactionRequestType,
                                   transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                   detailsPlain: String,
                                   chargePolicy: String): Box[TransactionRequest] = {

    // Get the threshold for a challenge. i.e. over what value do we require an out of bounds security challenge to be sent?
    val challengeThreshold = getChallengeThreshold(fromAccount.bankId.value,
                                                   fromAccount.accountId.value,
                                                   viewId.value,
                                                   transactionRequestType.value,
                                                   transactionRequestCommonBody.value.currency,
                                                   initiator.userId,
                                                   initiator.name)

    // Set initial status
    val status = if (BigDecimal(transactionRequestCommonBody.value.amount) < BigDecimal(challengeThreshold.amount)) {

      // For any connector != mapped we should probably assume that transaction_status_scheduler_delay will be > 0
      // so that getTransactionRequestStatusesImpl needs to be implemented for all connectors except mapped.

      // i.e. if we are certain that saveTransaction will be honored immediately by the backend, then transaction_status_scheduler_delay
      // can be empty in the props file. Otherwise, the status will be set to STATUS_PENDING
      // and getTransactionRequestStatusesImpl needs to be run periodically to update the transaction request status.

        if ( Props.getLong("transaction_status_scheduler_delay").isEmpty )
          TransactionRequests.STATUS_COMPLETED
        else
          TransactionRequests.STATUS_PENDING
      } else {
        TransactionRequests.STATUS_INITIATED
      }

    // Always create a new Transaction Request
    val transactionReq = for {
      chargeLevel <- getChargeLevel(BankId(fromAccount.bankId.value), AccountId(fromAccount.accountId.value), viewId, initiator.userId,
                                    initiator.name, transactionRequestType.value, fromAccount.currency)

      chargeValue <- tryo {
                            BigDecimal(transactionRequestCommonBody.value.amount) * BigDecimal(chargeLevel.amount) match {
                              //Set the mininal cost (2 euros)for transaction request
                              case value if (value < 2) => BigDecimal("2.0")
                              //Set the largest cost (50 euros)for transaction request
                              case value if (value > 50) => BigDecimal("50")
                              //Set the cost according to the charge level
                              case value => value.setScale(10, BigDecimal.RoundingMode.HALF_UP).toDouble
                            }
                          } ?~! s"The value transactionRequestCommonBody.value.amount: ${transactionRequestCommonBody.value.amount } or chargeLevel.amount: ${chargeLevel.amount } can not be transfered to decimal "

      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestCommonBody.value.currency, chargeValue.toString()))

      transactionRequest <- createTransactionRequestImpl210(TransactionRequestId(java.util.UUID.randomUUID().toString),
                                                            transactionRequestType,
                                                            fromAccount,
                                                            toAccount,
                                                            toCounterparty,
                                                            transactionRequestCommonBody,
                                                            detailsPlain,
                                                            status,
                                                            charge,
                                                            chargePolicy)
    } yield transactionRequest

    //make sure we get something back
    var transactionRequest = transactionReq.openOrThrowException("Exception: Couldn't create transactionRequest")

    // If no challenge necessary, create Transaction immediately and put in data store and object to return
    status match {
      case TransactionRequests.STATUS_COMPLETED =>
        val createdTransactionId = Connector.connector.vend.makePaymentv200(initiator,
                                                                            BankIdAccountId(fromAccount.bankId, fromAccount.accountId),
                                                                            BankIdAccountId(toAccount.bankId, toAccount.accountId),
                                                                            toCounterparty,
                                                                            BigDecimal(transactionRequestCommonBody.value.amount),
                                                                            transactionRequestCommonBody.description,
                                                                            transactionRequestType,
                                                                            chargePolicy)
        //set challenge to null, otherwise it have the default value "challenge": {"id": "","allowed_attempts": 0,"challenge_type": ""}
        transactionRequest = transactionRequest.copy(challenge = null)
        //save transaction_id into database
        saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId.openOrThrowException("Exception: Couldn't create transaction"))
        //update transaction_id filed for varibale 'transactionRequest'
        transactionRequest = transactionRequest.copy(transaction_ids = createdTransactionId.get.value)

      case TransactionRequests.STATUS_PENDING =>
        transactionRequest = transactionRequest

      case TransactionRequests.STATUS_INITIATED =>
        //if challenge necessary, create a new one
        val challengeId = createChallenge(fromAccount.bankId, fromAccount.accountId, initiator.userId, transactionRequestType: TransactionRequestType, transactionRequest.id.value).openOrThrowException("Exception: Couldn't create create challenge id")


        // TODO: challenge_type should not be hard coded here. Rather it should be sent as a parameter to this function createTransactionRequestv210
        val challenge = TransactionRequestChallenge(challengeId, allowed_attempts = 3, challenge_type = TransactionRequests.CHALLENGE_SANDBOX_TAN)
        saveTransactionRequestChallenge(transactionRequest.id, challenge)
        transactionRequest = transactionRequest.copy(challenge = challenge)
    }

    Full(transactionRequest)
  }

  //place holder for various connector methods that overwrite methods like these, does the actual data access
  protected def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                             fromAccount : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                             status: String, charge: TransactionRequestCharge) : Box[TransactionRequest]

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
                                                toCounterparty: CounterpartyTrait,
                                                transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                                details: String,
                                                status: String,
                                                charge: TransactionRequestCharge,
                                                chargePolicy: String): Box[TransactionRequest]

  def saveTransactionRequestTransaction(transactionRequestId: TransactionRequestId, transactionId: TransactionId) = {
    //put connector agnostic logic here if necessary
    saveTransactionRequestTransactionImpl(transactionRequestId, transactionId)
  }

  protected def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean]

  def saveTransactionRequestChallenge(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) = {
    //put connector agnostic logic here if necessary
    saveTransactionRequestChallengeImpl(transactionRequestId, challenge)
  }

  protected def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean]

  protected def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean]

  def getTransactionRequests(initiator : User, fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    val transactionRequests =
    for {
      fromAccount <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
            s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
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

  def getTransactionRequests210(initiator : User, fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    val transactionRequests =
      for {
        fromAccount <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
          s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
        isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
        transactionRequests <- getTransactionRequestsImpl210(fromAccount)
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

  def getTransactionRequestStatuses() : Box[TransactionRequestStatus] = {
    for {
      transactionRequestStatuses <- getTransactionRequestStatusesImpl()
    } yield transactionRequestStatuses

  }

  protected def getTransactionRequestStatusesImpl() : Box[TransactionRequestStatus]

  protected def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]]

  protected def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]]

  def getTransactionRequestImpl(transactionRequestId: TransactionRequestId) : Box[TransactionRequest]

  def getTransactionRequestTypes(initiator : User, fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
    for {
      fromAccount <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
        s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
      transactionRequestTypes <- getTransactionRequestTypesImpl(fromAccount)
    } yield transactionRequestTypes
  }

  protected def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]]


  //Note: Now we use validateChallengeAnswer instead, new methods validate over kafka, and move the allowed_attempts guard into API level.
  //It is only used for V140 and V200, has been deprecated from V210.
  @deprecated
  def answerTransactionRequestChallenge(transReqId: TransactionRequestId, answer: String) : Box[Boolean] = {
    val tr = getTransactionRequestImpl(transReqId) ?~ s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"

    tr match {
      case Full(tr: TransactionRequest) =>
        if (tr.challenge.allowed_attempts > 0) {
          if (tr.challenge.challenge_type == TransactionRequests.CHALLENGE_SANDBOX_TAN) {
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
      tr <- getTransactionRequestImpl(transReqId) ?~ "Transaction Request not found"
      transId <- makePayment(initiator, BankIdAccountId(BankId(tr.from.bank_id), AccountId(tr.from.account_id)),
          BankIdAccountId (BankId(tr.body.to.bank_id), AccountId(tr.body.to.account_id)), BigDecimal (tr.body.value.amount), tr.body.description) ?~ "Couldn't create Transaction"
      didSaveTransId <- saveTransactionRequestTransaction(transReqId, transId)
      didSaveStatus <- saveTransactionRequestStatusImpl(transReqId, TransactionRequests.STATUS_COMPLETED)
      //get transaction request again now with updated values
      tr <- getTransactionRequestImpl(transReqId)
    } yield {
      tr
    }
  }

  def createTransactionAfterChallengev200(initiator: User, transReqId: TransactionRequestId, transactionRequestType: TransactionRequestType): Box[TransactionRequest] = {
    for {
      tr <- getTransactionRequestImpl(transReqId) ?~ s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"
      // Note for 'new MappedCounterparty()' in the following :
      // We update the makePaymentImpl in V210, added the new parameter 'toCounterparty: CounterpartyTrait' for V210
      // But in V200 or before, we do not used the new parameter toCounterparty. So just keep it empty.
      transId <- makePaymentv200(initiator, BankIdAccountId(BankId(tr.from.bank_id), AccountId(tr.from.account_id)),
                                 BankIdAccountId (BankId(tr.body.to.bank_id), AccountId(tr.body.to.account_id)),
                                 new MappedCounterparty(),  //Note MappedCounterparty only support in V210
                                 BigDecimal (tr.body.value.amount),
                                 tr.body.description, transactionRequestType,
                                 "" //Note chargePolicy only support in V210
      ) ?~ "Couldn't create Transaction"
      didSaveTransId <- saveTransactionRequestTransaction(transReqId, transId)
      didSaveStatus <- saveTransactionRequestStatusImpl(transReqId, TransactionRequests.STATUS_COMPLETED)
      //get transaction request again now with updated values
      tr <- getTransactionRequestImpl(transReqId)
    } yield {
      tr
    }
  }

  def createTransactionAfterChallengev210(initiator: User, transReqId: TransactionRequestId, transactionRequestType: TransactionRequestType): Box[TransactionRequest] = {
    for {
      tr <- getTransactionRequestImpl(transReqId) ?~ s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"

      details = tr.details

      //Note, it should be four different type of details in mappedtransactionrequest.
      //But when we design "createTransactionRequest", we try to make it the same as SandBoxTan. There is still some different now.
      // Take a look at TransactionRequestDetailsMapperJSON, TransactionRequestDetailsMapperCounterpartyJSON, TransactionRequestDetailsMapperSEPAJSON and TransactionRequestDetailsMapperFreeFormJSON
      detailsJsonExtract = details.extract[TransactionRequestDetailsMapperJSON]

      toBankId = detailsJsonExtract.to.bank_id

      toAccountId = detailsJsonExtract.to.account_id

      valueAmount = detailsJsonExtract.value.amount

      valueCurrency = detailsJsonExtract.value.currency

      description = detailsJsonExtract.description

      // Note for 'toCounterparty' in the following :
      // We update the makePaymentImpl in V210, added the new parameter 'toCounterparty: CounterpartyTrait' for V210
      // And it only used for "COUNTERPARTY" and  "SEPA" ,other types keep it empty now.
      toCounterparty  <- transactionRequestType.value match {
        case "COUNTERPARTY" | "SEPA"  =>
          val counterpartyId = tr.counterparty_id
          val toCounterparty = Connector.connector.vend.getCounterpartyByCounterpartyId(counterpartyId) ?~! {ErrorMessages.CounterpartyNotFoundByCounterpartyId}
          toCounterparty
        case _ =>
          Full(new MappedCounterparty())
      }

      transId <- makePaymentv200(initiator, BankIdAccountId(BankId(tr.from.bank_id), AccountId(tr.from.account_id)),
                                 BankIdAccountId(BankId(toBankId), AccountId(toAccountId)),
                                 toCounterparty,
                                 BigDecimal(valueAmount), description,
                                 transactionRequestType,
                                 tr.charge_policy
                                 ) ?~ "Couldn't create Transaction"

      didSaveTransId <- saveTransactionRequestTransaction(transReqId, transId)

      didSaveStatus <- saveTransactionRequestStatusImpl(transReqId, TransactionRequests.STATUS_COMPLETED)

    } yield {
      var tr = getTransactionRequestImpl(transReqId).openOrThrowException("Exception: Couldn't create transaction")
      //update the return value, getTransactionRequestImpl is not in real-time. need update the data manually.
      tr=tr.copy(transaction_ids =transId.value)
      tr=tr.copy(status =TransactionRequests.STATUS_COMPLETED)
      tr
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
  ): (Bank, BankAccount)

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
      def exists(number : String) = Connector.connector.vend.accountExists(bankId, number)

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
  ): Box[BankAccount]

  //sets a user as an account owner/holder
  def setAccountHolder(bankAccountUID: BankIdAccountId, user: User): Unit = {
    AccountHolders.accountHolders.vend.createAccountHolder(user.resourceUserId.value, bankAccountUID.accountId.value, bankAccountUID.bankId.value)
  }

  /**
    * sets a user as an account owner/holder, this maybe duplicated with
    * @ setAccountHolder(bankAccountUID: BankAccountUID, user: User)
    *
    * @param owner
    * @param bankId
    * @param accountId
    * @param account_owners
    */
  def setAccountHolder(owner : String, bankId: BankId, accountId: AccountId, account_owners: List[String]) : Unit = {
//    if (account_owners.contains(owner)) { // No need for now, fix it later
      val resourceUserOwner = Users.users.vend.getUserByUserName(owner)
      resourceUserOwner match {
        case Full(owner) => {
          if ( ! accountOwnerExists(owner, bankId, accountId)) {
            val holder = AccountHolders.accountHolders.vend.createAccountHolder(owner.resourceUserId.value, bankId.value, accountId.value)
            logger.debug(s"Connector.setAccountHolder create account holder: $holder")
          }
        }
        case Empty => {
//          This shouldn't happen as AuthUser should generate the ResourceUsers when saved
          logger.error(s"resource user(s) $owner not found.")
        }
//      }
    }
  }

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  def accountExists(bankId : BankId, accountNumber : String) : Boolean

  //remove an account and associated transactions
  def removeAccount(bankId: BankId, accountId: AccountId) : Boolean

  //used by transaction import api call to check for duplicates

  //the implementation is responsible for dealing with the amount as a string
  def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount : String, completed : Date, otherAccountHolder : String) : Int
  def createImportedTransaction(transaction: ImporterTransaction) : Box[Transaction]
  def updateAccountBalance(bankId : BankId, accountId : AccountId, newBalance : BigDecimal) : Boolean
  def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Boolean

  def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Boolean

  def getProducts(bankId : BankId) : Box[List[Product]]

  def getProduct(bankId : BankId, productCode : ProductCode) : Box[Product]

  //Note: this is a temporary way for compatibility
  //It is better to create the case class for all the connector methods
  def createOrUpdateBranch(branch: Branch): Box[BranchT]
  
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
  ): Box[Bank] = Empty


  def createOrUpdateAtm(atm: Atms.Atm): Box[AtmT] = Empty


  def createOrUpdateProduct(
                             bankId : String,
                             code : String,
                             name : String,
                             category : String,
                             family : String,
                             superFamily : String,
                             moreInfoUrl : String,
                             details : String,
                             description : String,
                             metaLicenceId : String,
                             metaLicenceName : String
                       ): Box[Product] = Empty


  def createOrUpdateFXRate(
  bankId : String,
  fromCurrencyCode: String,
  toCurrencyCode: String,
  conversionValue: Double,
  inverseConversionValue: Double,
  effectiveDate: Date): Box[FXRate] = Empty



  def getBranch(bankId : BankId, branchId: BranchId) : Box[BranchT]

  def getAtm(bankId : BankId, atmId: AtmId) : Box[AtmT]

  //This method is only existing in mapper
  def accountOwnerExists(user: ResourceUser, bankId: BankId, accountId: AccountId): Boolean = {
    val res =
      MapperAccountHolders.findAll(
        By(MapperAccountHolders.user, user),
        By(MapperAccountHolders.accountBankPermalink, bankId.value),
        By(MapperAccountHolders.accountPermalink, accountId.value)
      )

    res.nonEmpty
  }
  

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


  def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate]

  /**
    * get transaction request type charge specified by: bankId, accountId, viewId, transactionRequestType. 
    */
  def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge]

  /**
    * get transaction request type charges
    */
  def getTransactionRequestTypeCharges(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestTypes: List[TransactionRequestType]): Box[List[TransactionRequestTypeCharge]] = {
    val res = for {
      trt <- transactionRequestTypes.map(getTransactionRequestTypeCharge(bankId, accountId, viewId, _))
    } yield { trt }.toList
    res.headOption
  }
}
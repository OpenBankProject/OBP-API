package code.bankconnectors

import java.util.Date

import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages
import code.api.v2_1_0._
import code.branches.Branches.{Branch, BranchId}
import code.fx.fx
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.counterparties.CounterpartyTrait
import code.model.{Transaction, User, _}
import code.model.dataAccess.{APIUser, MappedAccountHolder}
import code.transactionrequests.TransactionRequests
import code.transactionrequests.TransactionRequests._
import code.util.Helper._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.json
import net.liftweb.util.Helpers._
import net.liftweb.util.{Props, SimpleInjector}
import code.products.Products.{Product, ProductCode}
import code.views.Views
import net.liftweb.mapper.By

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

object Connector  extends SimpleInjector {


  val connector = new Inject(buildOne _) {}

  def buildOne: Connector = {
    val connectorProps = Props.get("connector").openOrThrowException("no connector set")

    connectorProps match {
      case "mapped" => LocalMappedConnector
      case "mongodb" => LocalConnector
      case "kafka" => KafkaMappedConnector
      case "obpjvm" => ObpJvmMappedConnector
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

trait Connector {

  //We have the Connector define its BankAccount implementation here so that it can
  //have access to the implementation details (e.g. the ability to set the balance) in
  //the implementation of makePaymentImpl
  type AccountType <: BankAccount

  // Gets current challenge level for transaction request
  // Transaction request challenge threshold. Level at which challenge is created and needs to be answered
  // before we attempt to create a transaction on the south side
  // The Currency is EUR. Connector implementations may convert the value to the transaction request currency.
  // Connector implementation may well provide dynamic response
  def getChallengeThreshold(userId: String, accountId: String, transactionRequestType: String, currency: String): (BigDecimal, String)

  //gets a particular bank handled by this connector
  def getBank(bankId : BankId) : Box[Bank]

  //gets banks handled by this connector
  def getBanks : List[Bank]

  def getBankAccounts(accounts: List[(BankId, AccountId)]) : List[BankAccount] = {
    for {
      acc <- accounts
      a <- getBankAccount(acc._1, acc._2)
    } yield a
  }

  case class InboundUser( email : String,
                          password : String,
                          display_name : String )

  def getUser(name: String, password: String): Box[InboundUser]

  def updateUserAccountViews(user: APIUser)

  def getBankAccount(bankId : BankId, accountId : AccountId) : Box[AccountType]

  def getCounterpartyFromTransaction(bankId: BankId, accountID : AccountId, counterpartyID : String) : Box[Counterparty]

  def getCounterpartiesFromTransaction(bankId: BankId, accountID : AccountId): List[Counterparty]

  def getCounterparty(thisAccountBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty]

  def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait]

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
  def getAccountHolders(bankId: BankId, accountID: AccountId) : Set[User]


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
  def makePayment(initiator : User, fromAccountUID : BankAccountUID, toAccountUID : BankAccountUID,
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
      transactionId <- makePaymentImpl(fromAccount, toAccount, amt, description)
    } yield transactionId
  }

  /**
    * \
    *
    * @param initiator The user attempting to make the payment
    * @param fromAccountUID The unique identifier of the account sending money
    * @param toAccountUID The unique identifier of the account receiving money
    * @param amt The amount of money to send ( > 0 )
    * @return The id of the sender's new transaction,
    */
  def makePaymentv200(initiator : User, fromAccountUID : BankAccountUID, toAccountUID : BankAccountUID,
                      amt : BigDecimal, description : String) : Box[TransactionId] = {
    for {
      fromAccount <- getBankAccount(fromAccountUID.bankId, fromAccountUID.accountId) ?~
        s"account ${fromAccountUID.accountId} not found at bank ${fromAccountUID.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount) == true || hasEntitlement(fromAccountUID.bankId.value, initiator.userId, CanCreateAnyTransactionRequest) == true, ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
      toAccount <- getBankAccount(toAccountUID.bankId, toAccountUID.accountId) ?~
        s"account ${toAccountUID.accountId} not found at bank ${toAccountUID.bankId}"
      //sameCurrency <- booleanToBox(fromAccount.currency == toAccount.currency, {
      //  s"Cannot send payment to account with different currency (From ${fromAccount.currency} to ${toAccount.currency}"
      //})

    // Note: These are guards. Values are calculated in makePaymentImpl
      rate <- tryo {
        fx.exchangeRate(fromAccount.currency, toAccount.currency)
      } ?~! {
        s"The requested currency conversion (${fromAccount.currency} to ${toAccount.currency}) is not supported."
      }
      notUsedHereConvertedAmount <- tryo {
        fx.convert(amt, rate)
      } ?~! {
        "Currency conversion failed."
      }
      isPositiveAmtToSend <- booleanToBox(amt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. ($amt)")
      //TODO: verify the amount fits with the currency -> e.g. 12.543 EUR not allowed, 10.00 JPY not allowed, 12.53 EUR allowed


      transactionId <- makePaymentImpl(fromAccount, toAccount, amt, description)
    } yield transactionId
  }


  protected def makePaymentImpl(fromAccount : AccountType, toAccount : AccountType, amt : BigDecimal, description : String) : Box[TransactionId]


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
    var result = for {
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
    result = Full(result.openOrThrowException("Exception: Couldn't create transactionRequest"))

    //if no challenge necessary, create transaction immediately and put in data store and object to return
    if (status == TransactionRequests.STATUS_COMPLETED) {
      val createdTransactionId = Connector.connector.vend.makePayment(initiator, BankAccountUID(fromAccount.bankId, fromAccount.accountId),
        BankAccountUID(toAccount.bankId, toAccount.accountId), BigDecimal(body.value.amount), body.description)

      //set challenge to null
      result = Full(result.get.copy(challenge = null))

      //save transaction_id if we have one
      createdTransactionId match {
        case Full(ti) => {
          if (! createdTransactionId.isEmpty) {
            saveTransactionRequestTransaction(result.get.id, ti)
            result = Full(result.get.copy(transaction_ids = ti.value))
          }
        }
        case _ => None
      }
    } else {
      //if challenge necessary, create a new one
      var challenge = TransactionRequestChallenge(id = java.util.UUID.randomUUID().toString, allowed_attempts = 3, challenge_type = TransactionRequests.CHALLENGE_SANDBOX_TAN)
      saveTransactionRequestChallenge(result.get.id, challenge)
      result = Full(result.get.copy(challenge = challenge))
    }

    result
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
    var result = for {
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
    result = Full(result.openOrThrowException("Exception: Couldn't create transactionRequest"))

    // If no challenge necessary, create Transaction immediately and put in data store and object to return
    if (status == TransactionRequests.STATUS_COMPLETED) {
      val createdTransactionId = Connector.connector.vend.makePaymentv200(initiator, BankAccountUID(fromAccount.bankId, fromAccount.accountId),
        BankAccountUID(toAccount.bankId, toAccount.accountId), BigDecimal(body.value.amount), body.description)

      //set challenge to null
      result = Full(result.get.copy(challenge = null))

      //save transaction_id if we have one
      createdTransactionId match {
        case Full(ti) => {
          if (! createdTransactionId.isEmpty) {
            saveTransactionRequestTransaction(result.get.id, ti)
            result = Full(result.get.copy(transaction_ids = ti.value))
          }
        }
        case _ => None
      }
    } else {
      //if challenge necessary, create a new one
      var challenge = TransactionRequestChallenge(id = java.util.UUID.randomUUID().toString, allowed_attempts = 3, challenge_type = TransactionRequests.CHALLENGE_SANDBOX_TAN)
      saveTransactionRequestChallenge(result.get.id, challenge)
      result = Full(result.get.copy(challenge = challenge))
    }

    result
  }

  def createTransactionRequestv210(initiator : User, fromAccount : BankAccount, toAccount: Box[BankAccount], transactionRequestType: TransactionRequestType, details: TransactionRequestDetails, detailsPlain: String) : Box[TransactionRequest] = {
    //set initial status
    //for sandbox / testing: depending on amount, we ask for challenge or not
    val (limit, currency) = getChallengeThreshold("", "", transactionRequestType.value, details.value.currency)
    val status =
      //if (transactionRequestType.value == TransactionRequests.CHALLENGE_SANDBOX_TAN && BigDecimal(details.value.amount) < limit) {


      if (BigDecimal(details.value.amount) < limit) {
        TransactionRequests.STATUS_COMPLETED
      } else {
        TransactionRequests.STATUS_INITIATED
      }


    // Always create a new Transaction Request
    var result = for {
      fromAccountType <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
        s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount) == true || hasEntitlement(fromAccount.bankId.value, initiator.userId, CanCreateAnyTransactionRequest) == true , ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)

      rawAmt <- tryo { BigDecimal(details.value.amount) } ?~! s"amount ${details.value.amount} not convertible to number"
      // isValidTransactionRequestType is checked at API layer. Maybe here too.
      isPositiveAmtToSend <- booleanToBox(rawAmt > BigDecimal("0"), s"Can't send a payment with a value of 0 or less. (${rawAmt})")


      // For now, arbitary charge value to demonstrate PSD2 charge transparency principle. Eventually this would come from Transaction Type? 10 decimal places of scaling so can add small percentage per transaction.
      chargeValue <- tryo {(BigDecimal(details.value.amount) * 0.0001).setScale(10, BigDecimal.RoundingMode.HALF_UP).toDouble} ?~! s"could not create charge for ${details.value.amount}"
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(details.value.currency, chargeValue.toString()))



      transactionRequest <- createTransactionRequestImpl210(TransactionRequestId(java.util.UUID.randomUUID().toString), transactionRequestType, fromAccount, detailsPlain, status, charge)
    } yield transactionRequest

    //make sure we get something back
    result = Full(result.openOrThrowException("Exception: Couldn't create transactionRequest"))

    // If no challenge necessary, create Transaction immediately and put in data store and object to return
    if (status == TransactionRequests.STATUS_COMPLETED) {
      val createdTransactionId = transactionRequestType.value match {
        case "SANDBOX_TAN" => Connector.connector.vend.makePaymentv200(initiator, BankAccountUID(fromAccount.bankId, fromAccount.accountId),
          BankAccountUID(toAccount.get.bankId, toAccount.get.accountId), BigDecimal(details.value.amount), details.asInstanceOf[TransactionRequestDetailsSandBoxTan].description)
        case "COUNTERPARTY" => Connector.connector.vend.makePaymentv200(initiator, BankAccountUID(fromAccount.bankId, fromAccount.accountId),
          BankAccountUID(toAccount.get.bankId, toAccount.get.accountId), BigDecimal(details.value.amount), details.asInstanceOf[TransactionRequestDetailsCounterpartyResponse].description)
        case "SEPA" => Connector.connector.vend.makePaymentv200(initiator, BankAccountUID(fromAccount.bankId, fromAccount.accountId),
          BankAccountUID(toAccount.get.bankId, toAccount.get.accountId), BigDecimal(details.value.amount), details.asInstanceOf[TransactionRequestDetailsSEPAResponse].description)
        case "FREE_FORM" => Connector.connector.vend.makePaymentv200(initiator, BankAccountUID(fromAccount.bankId, fromAccount.accountId),
          BankAccountUID(toAccount.get.bankId, toAccount.get.accountId), BigDecimal(details.value.amount), "")
      }

      //set challenge to null
      result = Full(result.get.copy(challenge = null))

      //save transaction_id if we have one
      createdTransactionId match {
        case Full(ti) => {
          if (! createdTransactionId.isEmpty) {
            saveTransactionRequestTransaction(result.get.id, ti)
            result = Full(result.get.copy(transaction_ids = ti.value))
          }
        }
        case _ => None
      }
    } else {
      //if challenge necessary, create a new one
      var challenge = TransactionRequestChallenge(id = java.util.UUID.randomUUID().toString, allowed_attempts = 3, challenge_type = TransactionRequests.CHALLENGE_SANDBOX_TAN)
      saveTransactionRequestChallenge(result.get.id, challenge)
      result = Full(result.get.copy(challenge = challenge))
    }

    result
  }





  //place holder for various connector methods that overwrite methods like these, does the actual data access
  protected def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                             fromAccount : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                             status: String, charge: TransactionRequestCharge) : Box[TransactionRequest]

  protected def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                             fromAccount : BankAccount, details: String,
                                             status: String, charge: TransactionRequestCharge) : Box[TransactionRequest]


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
      Full(
        transactionRequests.get.map(tr => if (tr.challenge.id == "") {
          tr.copy(challenge = null)
        } else {
          tr
        })
      )
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
      Full(
        transactionRequests.get.map(tr => if (tr.challenge.id == "") {
          tr.copy(challenge = null)
        } else {
          tr
        })
      )
    } else {
      transactionRequests
    }
  }

  def getTransactionRequestStatus(initiator : User, transactionRequestId: TransactionRequestId) : Box[Boolean] = {
    for {
      transactionRequest <- getTransactionRequestImpl(transactionRequestId)
      fromAccount <- tryo{transactionRequest.from}
      bankAccount <- BankAccount(BankId(fromAccount.bank_id), AccountId(fromAccount.account_id))
      isOwner <- booleanToBox(initiator.ownerAccess(bankAccount), "user does not have access to owner view")
      transactionRequestStatus <- getTransactionRequestStatusImpl(transactionRequestId)
    } yield transactionRequestStatus

  }

  protected def getTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId) : Box[Boolean]

  protected def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]]

  protected def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]]

  protected def getTransactionRequestImpl(transactionRequestId: TransactionRequestId) : Box[TransactionRequest]

  def getTransactionRequestTypes(initiator : User, fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
    for {
      fromAccount <- getBankAccount(fromAccount.bankId, fromAccount.accountId) ?~
        s"account ${fromAccount.accountId} not found at bank ${fromAccount.bankId}"
      isOwner <- booleanToBox(initiator.ownerAccess(fromAccount), "user does not have access to owner view")
      transactionRequestTypes <- getTransactionRequestTypesImpl(fromAccount)
    } yield transactionRequestTypes
  }

  protected def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]]


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
      transId <- makePayment(initiator, BankAccountUID(BankId(tr.from.bank_id), AccountId(tr.from.account_id)),
          BankAccountUID (BankId(tr.body.to.bank_id), AccountId(tr.body.to.account_id)), BigDecimal (tr.body.value.amount), tr.body.description) ?~ "Couldn't create Transaction"
      didSaveTransId <- saveTransactionRequestTransaction(transReqId, transId)
      didSaveStatus <- saveTransactionRequestStatusImpl(transReqId, TransactionRequests.STATUS_COMPLETED)
      //get transaction request again now with updated values
      tr <- getTransactionRequestImpl(transReqId)
    } yield {
      tr
    }
  }

  def createTransactionAfterChallengev200(initiator: User, transReqId: TransactionRequestId) : Box[TransactionRequest] = {
    for {
      tr <- getTransactionRequestImpl(transReqId) ?~ s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"
      transId <- makePaymentv200(initiator, BankAccountUID(BankId(tr.from.bank_id), AccountId(tr.from.account_id)),
        BankAccountUID (BankId(tr.body.to.bank_id), AccountId(tr.body.to.account_id)), BigDecimal (tr.body.value.amount), tr.body.description) ?~ "Couldn't create Transaction"
      didSaveTransId <- saveTransactionRequestTransaction(transReqId, transId)
      didSaveStatus <- saveTransactionRequestStatusImpl(transReqId, TransactionRequests.STATUS_COMPLETED)
      //get transaction request again now with updated values
      tr <- getTransactionRequestImpl(transReqId)
    } yield {
      tr
    }
  }

  def createTransactionAfterChallengev210(initiator: User, transReqId: TransactionRequestId) : Box[TransactionRequest] = {
    for {
      tr <- getTransactionRequestImpl(transReqId) ?~ s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"

      //dummy1 = print(s"Getting Details.. \n")

      details = tr.details
      //dummy2 = print(s"details are ${details} \n")

      detailsJsonExtract = details.extract[TransactionRequestDetailsSandBoxTanJSON]
      //dummy4 = print(s"detailsJsonExtract are ${detailsJsonExtract} \n")

      toBankId = detailsJsonExtract.to.bank_id
      //dummy5 = print(s"toBankId is ${toBankId} \n")

      toAccountId = detailsJsonExtract.to.account_id
      //dummy6 = print(s"toAccountId is ${toAccountId} \n")

      valueAmount = detailsJsonExtract.value.amount
      //dummy7 = print(s"valueAmount is ${valueAmount} \n")

      valueCurrency = detailsJsonExtract.value.currency
      //dummy8 = print(s"valueCurrency is ${valueCurrency} \n")

      description = detailsJsonExtract.description
      //dummy9 = print(s"description is ${description} \n")

      transId <- makePaymentv200(
        initiator,
        BankAccountUID(BankId(tr.from.bank_id), AccountId(tr.from.account_id)),
        BankAccountUID (
          BankId(toBankId),
          AccountId(toAccountId)
        ),
        BigDecimal (valueAmount),
        description
      ) ?~ "Couldn't create Transaction"

      didSaveTransId <- saveTransactionRequestTransaction(transReqId, transId)
      //dummy10 = print(s"didSaveTransId is ${didSaveTransId} \n")

      didSaveStatus <- saveTransactionRequestStatusImpl(transReqId, TransactionRequests.STATUS_COMPLETED)
      //dummy12 = print(s"didSaveStatus is ${didSaveStatus} \n")

      //get transaction request again now with updated values
      tr <- getTransactionRequestImpl(transReqId)
      //dummy13 = print(s"About to yield tr. tr.details is ${tr.details} \n")
    } yield {
      tr
    }
  }

  /*
    non-standard calls --do not make sense in the regular context but are used for e.g. tests
  */

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  def createBankAndAccount(bankName : String, bankNationalIdentifier : String, accountNumber : String,
                           accountType: String, accountLabel:String, currency: String, accountHolderName : String) : (Bank, BankAccount)

  //generates an unused account number and then creates the sandbox account using that number
  def createSandboxBankAccount(bankId : BankId, accountId : AccountId, accountType: String, accountLabel: String, currency : String, initialBalance : BigDecimal, accountHolderName : String) : Box[BankAccount] = {
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
      accountHolderName
    )

  }

  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  def createSandboxBankAccount(bankId : BankId, accountId : AccountId,  accountNumber: String,
                               accountType: String, accountLabel: String, currency : String,
                               initialBalance : BigDecimal, accountHolderName : String) : Box[BankAccount]

  //sets a user as an account owner/holder
  def setAccountHolder(bankAccountUID: BankAccountUID, user : User) : Unit

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

  def createOrUpdateBranch(branch: BranchJsonPost): Box[Branch]

  def getBranch(bankId : BankId, branchId: BranchId) : Box[Branch]

  def accountOwnerExists(user: APIUser, bankId: BankId, accountId: AccountId): Boolean = {
    val res =
      MappedAccountHolder.findAll(
        By(MappedAccountHolder.user, user),
        By(MappedAccountHolder.accountBankPermalink, bankId.value),
        By(MappedAccountHolder.accountPermalink, accountId.value)
      )

    res.nonEmpty
  }

  //def setAccountOwner(owner : String, account: KafkaInboundAccount) : Unit = {
  def setAccountOwner(owner : String, bankId: BankId, accountId: AccountId, account_owners: List[String]) : Unit = {
    if (account_owners.contains(owner)) {
      val apiUserOwner = APIUser.findAll.find(user => owner == user.name)
      apiUserOwner match {
        case Some(o) => {
          if ( ! accountOwnerExists(o, bankId, accountId)) {
            MappedAccountHolder.createMappedAccountHolder(o.apiId.value, bankId.value, accountId.value, "KafkaMappedConnector")
          }
       }
        case None => {
          //This shouldn't happen as OBPUser should generate the APIUsers when saved
          //logger.error(s"api user(s) $owner not found.")
       }
      }
    }
  }

  def createViews(bankId: BankId, accountId: AccountId, owner_view: Boolean = false,
                  public_view: Boolean = false,
                  accountants_view: Boolean = false,
                  auditors_view: Boolean = false ) : List[View] = {

    val ownerView: Box[View] =
      if(owner_view)
        Views.views.vend.createOwnerView(bankId, accountId, "Owner View")
      else Empty

    val publicView: Box[View]  =
      if(public_view)
        Views.views.vend.createPublicView(bankId, accountId, "Public View")
      else Empty

    val accountantsView: Box[View]  =
      if(accountants_view)
        Views.views.vend.createAccountantsView(bankId, accountId, "Accountants View")
      else Empty

    val auditorsView: Box[View] =
      if(auditors_view)
        Views.views.vend.createAuditorsView(bankId, accountId, "Auditors View")
      else Empty

    List(ownerView, publicView, accountantsView, auditorsView).flatten
  }

//  def incrementBadLoginAttempts(username:String):Unit
//
//  def userIsLocked(username:String):Boolean
//
//  def resetBadLoginAttempts(username:String):Unit

  def getConsumer(consumerId: Long,consumerKey:String): Box[Consumer]
  
}

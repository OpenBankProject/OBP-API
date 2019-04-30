package code.transactionrequests


import code.api.util.APIUtil
import code.remotedata.RemotedataTransactionRequests
import com.openbankproject.commons.model.{TransactionRequest,TransactionRequestChallenge, TransactionRequestCharge, _}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

object TransactionRequests extends SimpleInjector {

  object TransactionRequestStatus extends Enumeration {
    type TransactionRequestStatus = Value
    val INITIATED, PENDING, FAILED, COMPLETED, FORWARDED, REJECTED = Value
  }
  
  object TransactionChallengeTypes extends Enumeration {
    type TransactionChallengeTypes = Value
    val SANDBOX_TAN = Value
  }
  
  object TransactionRequestTypes extends Enumeration {
    type TransactionRequestTypes = Value
    val SANDBOX_TAN, COUNTERPARTY, SEPA, FREE_FORM, TRANSFER_TO_PHONE, TRANSFER_TO_ATM, TRANSFER_TO_ACCOUNT, TRANSFER_TO_REFERENCE_ACCOUNT = Value
  }

  def updatestatus(newStatus: String) = {}

  val transactionRequestProvider = new Inject(buildOne _) {}

  def buildOne: TransactionRequestProvider  =
    APIUtil.getPropsValue("transactionRequests_connector", "mapped") match {
      case "mapped" => APIUtil.getPropsAsBoolValue("use_akka", false) match {
        case false  => MappedTransactionRequestProvider
        case true => RemotedataTransactionRequests     // We will use Akka as a middleware
      }
      case tc: String => throw new IllegalArgumentException("No such connector for Transaction Requests: " + tc)
    }

  // Helper to get the count out of an option
  def countOfTransactionRequests(listOpt: Option[List[TransactionRequest]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }

}

trait TransactionRequestProvider {

  private val logger = Logger(classOf[TransactionRequestProvider])

  final def getTransactionRequest(transactionRequestId : TransactionRequestId) : Box[TransactionRequest] = {
    getTransactionRequestFromProvider(transactionRequestId)
  }

  final def getTransactionRequests(bankId : BankId, accountId: AccountId) : Box[List[TransactionRequest]] = {
    getTransactionRequestsFromProvider(bankId, accountId)
  }

  def getMappedTransactionRequest(transactionRequestId: TransactionRequestId): Box[MappedTransactionRequest]
  def getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId): Box[List[TransactionRequest]]
  def getTransactionRequestFromProvider(transactionRequestId : TransactionRequestId) : Box[TransactionRequest]
  def updateAllPendingTransactionRequests: Box[Option[Unit]]
  def createTransactionRequestImpl(transactionRequestId: TransactionRequestId,
                                   transactionRequestType: TransactionRequestType,
                                   account : BankAccount,
                                   counterparty : BankAccount,
                                   body: TransactionRequestBody,
                                   status: String,
                                   charge: TransactionRequestCharge) : Box[TransactionRequest]
  def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                      transactionRequestType: TransactionRequestType,
                                      fromAccount: BankAccount,
                                      toAccount: BankAccount,
                                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                      details: String,
                                      status: String,
                                      charge: TransactionRequestCharge,
                                      chargePolicy: String): Box[TransactionRequest]
  def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean]
  def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean]
  def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean]
  def bulkDeleteTransactionRequests(): Boolean
}

class RemotedataTransactionRequestsCaseClasses {
  case class getMappedTransactionRequest(transactionRequestId: TransactionRequestId)
  case class getTransactionRequestsFromProvider(bankId : BankId, accountId: AccountId)
  case class getTransactionRequestFromProvider(transactionRequestId : TransactionRequestId)
  case class updateAllPendingTransactionRequests()
  case class createTransactionRequestImpl(transactionRequestId: TransactionRequestId,
                                          transactionRequestType: TransactionRequestType,
                                          account : BankAccount,
                                          counterparty : BankAccount,
                                          body: TransactionRequestBody,
                                          status: String,
                                          charge: TransactionRequestCharge)
  case class createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                             transactionRequestType: TransactionRequestType,
                                             fromAccount: BankAccount, 
                                             toAccount: BankAccount,
                                             transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                             details: String,
                                             status: String,
                                             charge: TransactionRequestCharge,
                                             chargePolicy: String)
  case class saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId)
  case class saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge)
  case class saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String)
  case class bulkDeleteTransactionRequests()
}

object RemotedataTransactionRequestsCaseClasses extends RemotedataTransactionRequestsCaseClasses
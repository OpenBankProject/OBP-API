package code.transactionrequests


import code.api.util.APIUtil
import com.openbankproject.commons.model.{TransactionRequest, TransactionRequestChallenge, TransactionRequestCharge, _}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

object TransactionRequests extends SimpleInjector {
  
  //These are berlin Group Standard
  object PaymentServiceTypes extends Enumeration {
    type PaymentServiceTypes = Value
    val payments, bulk_payments, periodic_payments = Value
  }
  
  object TransactionRequestTypes extends Enumeration {
    type TransactionRequestTypes = Value
    val SANDBOX_TAN, ACCOUNT, ACCOUNT_OTP, COUNTERPARTY, SEPA, FREE_FORM, SIMPLE, CARD,
    TRANSFER_TO_PHONE, TRANSFER_TO_ATM, TRANSFER_TO_ACCOUNT, TRANSFER_TO_REFERENCE_ACCOUNT, 
    //The following are BerlinGroup Standard 
    SEPA_CREDIT_TRANSFERS, INSTANT_SEPA_CREDIT_TRANSFERS, TARGET_2_PAYMENTS, CROSS_BORDER_CREDIT_TRANSFERS, REFUND = Value
  }

  def updatestatus(newStatus: String) = {}

  val transactionRequestProvider = new Inject(buildOne _) {}

  def buildOne: TransactionRequestProvider  =
    APIUtil.getPropsValue("transactionRequests_connector", "mapped") match {
      case "mapped" =>MappedTransactionRequestProvider
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
  def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                      transactionRequestType: TransactionRequestType,
                                      fromAccount: BankAccount,
                                      toAccount: BankAccount,
                                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                      details: String,
                                      status: String,
                                      charge: TransactionRequestCharge,
                                      chargePolicy: String,
                                      berlinGroupPayments: Option[BerlinGroupTransactionRequestCommonBodyJson]): Box[TransactionRequest]
  
  def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean]
  def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean]
  def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean]
  def saveTransactionRequestDescriptionImpl(transactionRequestId: TransactionRequestId, description: String): Box[Boolean]
  def bulkDeleteTransactionRequestsByTransactionId(transactionId: TransactionId): Boolean
  def bulkDeleteTransactionRequests(): Boolean
}



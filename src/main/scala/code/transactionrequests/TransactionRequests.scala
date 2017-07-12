package code.transactionrequests


import java.util.Date

import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.metadata.counterparties.CounterpartyTrait
import code.metadata.wheretags.{MapperWhereTags, WhereTags}
import code.model._
import code.remotedata.{RemotedataTransactionRequests, RemotedataWhereTags}
import code.transactionrequests.TransactionRequests.{TransactionRequest, TransactionRequestBody, TransactionRequestChallenge, TransactionRequestCharge}
import net.liftweb.common.{Box, Logger}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.{Props, SimpleInjector}
import org.elasticsearch.common.inject.Inject

object TransactionRequests extends SimpleInjector {

  //TODO: change these to some kind of case class / type thingy (so we can match {} on them)
  val STATUS_INITIATED = "INITIATED"
  val STATUS_PENDING = "PENDING"
  val STATUS_FAILED = "FAILED"
  val STATUS_COMPLETED = "COMPLETED"

  val CHALLENGE_SANDBOX_TAN = "SANDBOX_TAN"

  def updatestatus(newStatus: String) = {}

  case class TransactionRequestCharge(
                                 val summary: String,
                                 val value : AmountOfMoney
  )

  case class TransactionRequest (
    val id: TransactionRequestId,
    val `type` : String,
    val from: TransactionRequestAccount,
    val details: JValue, // Note: This is unstructured! (allows multiple "to" accounts etc.)
    val body: TransactionRequestBody, // Note: This is structured with one "to" account etc.
    val transaction_ids: String,
    val status: String,
    val start_date: Date,
    val end_date: Date,
    val challenge: TransactionRequestChallenge,
    val charge: TransactionRequestCharge,
    val charge_policy: String,
    val counterparty_id :CounterpartyId,
    val name :String,
    val this_bank_id : BankId,
    val this_account_id : AccountId,
    val this_view_id :ViewId,
    val other_account_routing_scheme : String,
    val other_account_routing_address : String,
    val other_bank_routing_scheme : String,
    val other_bank_routing_address : String,
    val is_beneficiary :Boolean

  )

  case class TransactionRequestChallenge (
    val id: String,
    val allowed_attempts : Int,
    val challenge_type: String
  )

  case class TransactionRequestAccount (
    val bank_id: String,
    val account_id : String
  )

  case class TransactionRequestBody (
    val to: TransactionRequestAccount,
    val value : AmountOfMoney,
    val description : String
  )

  val transactionRequestProvider = new Inject(buildOne _) {}

  def buildOne: TransactionRequestProvider  =
    Props.get("transactionRequests_connector", "mapped") match {
      case "mapped" => Props.getBool("use_akka", false) match {
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
                                      toCounterparty: CounterpartyTrait,
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
                                             toCounterparty: CounterpartyTrait,
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
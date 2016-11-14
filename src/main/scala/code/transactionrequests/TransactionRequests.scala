package code.transactionrequests


import java.util.Date

import code.model._
import net.liftweb.common.Logger
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.{Props, SimpleInjector}
import TransactionRequests.TransactionRequest

object TransactionRequests extends SimpleInjector {

  //TODO: change these to some kind of case class / type thingy (so we can match {} on them)
  val STATUS_INITIATED = "INITIATED"
  val STATUS_CHALLENGE_PENDING = "CHALLENGE_PENDING"
  //val STATUS_PROCESSING_PENDING = "PROCESSING_PENDING"  //e.g. in case bank needs some time to process transaction request
  val STATUS_FAILED = "FAILED"
  val STATUS_COMPLETED = "COMPLETED"

  val CHALLENGE_SANDBOX_TAN = "SANDBOX_TAN"


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
    val charge: TransactionRequestCharge
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

  trait TransactionRequestDetails {
    val value : AmountOfMoney
  }

  case class TransactionRequestDetailsSandBoxTan (
                                      val to: TransactionRequestAccount,
                                      val value : AmountOfMoney,
                                      val description : String
                                    ) extends TransactionRequestDetails

  case class TransactionRequestDetailsSEPA (
                                                  val value : AmountOfMoney,
                                                  val description : String
                                           ) extends TransactionRequestDetails

  case class TransactionRequestDetailsFreeForm (
                                                 val value : AmountOfMoney
                                           ) extends TransactionRequestDetails

  val transactionRequestProvider = new Inject(buildOne _) {}

  def buildOne: TransactionRequestProvider  =
    Props.get("transactionRequests_connector", "mapped") match {
      case "mapped" => MappedTransactionRequestProvider
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

  final def getTransactionRequest(transactionRequesId : TransactionRequestId) : Option[TransactionRequest] = {
    getTransactionRequestFromProvider(transactionRequesId)
  }

  final def getTransactionRequests(bankId : BankId, accountId: AccountId, viewId: ViewId) : Option[List[TransactionRequest]] = {
    getTransactionRequestsFromProvider(bankId, accountId, viewId)
  }

  protected def getTransactionRequestsFromProvider(bankId : BankId, accountId: AccountId, viewId: ViewId) : Option[List[TransactionRequest]]
  protected def getTransactionRequestFromProvider(transactionRequestId : TransactionRequestId) : Option[TransactionRequest]
}


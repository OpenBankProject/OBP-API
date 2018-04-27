package code.transactionrequests


import java.util.Date

import code.api.util.APIUtil
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_1_0.{CounterpartyIdJson, IbanJson, TransactionRequestCommonBodyJSON}
import code.api.v3_0_0.custom._
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.remotedata.RemotedataTransactionRequests
import code.transactionrequests.TransactionRequests.{TransactionRequest, TransactionRequestBody, TransactionRequestChallenge, TransactionRequestCharge}
import net.liftweb.common.{Box, Logger}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.{Props, SimpleInjector}
import org.elasticsearch.common.inject.Inject

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

  case class TransactionRequestCharge(
                                 val summary: String,
                                 val value : AmountOfMoney
  )

  case class TransactionRequest (
    val id: TransactionRequestId,
    val `type` : String,
    val from: TransactionRequestAccount,
    val body: TransactionRequestBodyAllTypes,
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
  
  //For COUNTERPATY, it need the counterparty_id to find the toCounterpaty--> toBankAccount
  case class TransactionRequestCounterpartyId (counterparty_id : String)
  
  //For SEPA, it need the iban to find the toCounterpaty--> toBankAccount
  case class TransactionRequestIban (iban : String)

  case class FromAccountTransfer(
    mobile_phone_number: String,
    nickname: String
  )

  case class ToAccountTransferToPhone(
    mobile_phone_number: String
  )

  case class ToAccountTransferToAtmKycDocument(
    `type`: String,
    number: String
  )

  case class ToAccountTransferToAtm(
    legal_name: String,
    date_of_birth: String,
    mobile_phone_number: String,
    kyc_document: ToAccountTransferToAtmKycDocument
  )

  case class ToAccountTransferToAccountAccount(
    number: String,
    iban: String
  )

  case class ToAccountTransferToAccount(
    name: String,
    bank_code: String,
    branch_number: String,
    account: ToAccountTransferToAccountAccount
  )

  case class TransactionRequestTransferToPhone(
    value: AmountOfMoneyJsonV121,
    description: String,
    message: String,
    from: FromAccountTransfer,
    to: ToAccountTransferToPhone
  ) extends TransactionRequestCommonBodyJSON

  case class TransactionRequestTransferToAtm(
    value: AmountOfMoneyJsonV121,
    description: String,
    message: String,
    from: FromAccountTransfer,
    to: ToAccountTransferToAtm
  ) extends TransactionRequestCommonBodyJSON

  case class TransactionRequestTransferToAccount(
    value: AmountOfMoneyJsonV121,
    description: String,
    transfer_type: String,
    future_date: String,
    to: ToAccountTransferToAccount
  ) extends TransactionRequestCommonBodyJSON

  case class TransactionRequestBody (
    val to: TransactionRequestAccount,
    val value : AmountOfMoney,
    val description : String
  )
  
  case class TransactionRequestBodyAllTypes (
    to_sandbox_tan: Option[TransactionRequestAccount],
    to_sepa: Option[TransactionRequestIban],
    to_counterparty: Option[TransactionRequestCounterpartyId],
    to_transfer_to_phone: Option[TransactionRequestTransferToPhone] = None, //TODO not stable 
    to_transfer_to_atm: Option[TransactionRequestTransferToAtm]= None,//TODO not stable 
    to_transfer_to_account: Option[TransactionRequestTransferToAccount]= None,//TODO not stable 
    value: AmountOfMoney,
    description: String
  )

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
package code.transfers


import java.util.Date

import code.model._
import net.liftweb.common.Logger
import net.liftweb.util.{Props, SimpleInjector}
import Transfers.{Transfer, TransferId}

object Transfers extends SimpleInjector {

  val STATUS_INITIATED = "INITIATED"
  val STATUS_CHALLENGE_PENDING = "CHALLENGE_PENDING"
  val STATUS_FAILED = "FAILED"
  val STATUS_COMPLETED = "COMPLETED"

  case class TransferId(value : String)

  case class Transfer (
    val transferId: TransferId,
    val `type` : String,
    val from: TransferAccount,
    val body: TransferBody,
    val transaction_ids: String,
    val status: String,
    val start_date: Date,
    val end_date: Date,
    val challenge: TransferChallenge
  )

  case class TransferChallenge (
    val id: String,
    val allowed_attempts : Int,
    val challenge_type: String
  )

  case class TransferAccount (
    val bank_id: String,
    val account_id : String
  )

  case class TransferBody (
    val to: TransferAccount,
    val value : AmountOfMoney,
    val description : String
  )

  val transfersProvider = new Inject(buildOne _) {}

  def buildOne: TransfersProvider  =
    Props.get("transfers_connector", "mapped") match {
      case "mapped" => MappedTransfersProvider
      case tc: String => throw new IllegalArgumentException("No such connector for Transfers: " + tc)
    }

  // Helper to get the count out of an option
  def countOfTransfers(listOpt: Option[List[Transfer]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }
}

trait TransfersProvider {

  private val logger = Logger(classOf[TransfersProvider])

  final def getTransfer(transferId : TransferId) : Option[Transfer] = {
    getTransferFromProvider(transferId)
  }

  final def getTransfers(bankId : BankId, accountId: AccountId, viewId: ViewId) : Option[List[Transfer]] = {
    getTransfersFromProvider(bankId, accountId, viewId)
  }

  protected def getTransfersFromProvider(bankId : BankId, accountId: AccountId, viewId: ViewId) : Option[List[Transfer]]
  protected def getTransferFromProvider(transferId : TransferId) : Option[Transfer]
}


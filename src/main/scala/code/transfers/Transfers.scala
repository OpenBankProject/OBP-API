package code.transfers


import java.util.Date

import code.model._
import net.liftweb.common.Logger
import net.liftweb.util.{Props, SimpleInjector}
import Transfers.{Transfer, TransferId}

object Transfers extends SimpleInjector {

  case class TransferId(value : String)

  trait Transfer {
    def transferId: TransferId
    def `type` : String
    def from: TransferAccount
    def body: TransferBody
    def transaction_ids: String
    def status: String
    def start_date: Date
    def end_date: Date
    def challenge: Challenge
  }

  trait Challenge {
    def id: String
    def allowed_attempts : Int
    def challenge_type: String
  }

  trait TransferAccount {
    def bank_id: String
    def account_id : String
  }

  trait TransferBody {
    def to: TransferAccount
    def value : AmountOfMoney
    def description : String
  }

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


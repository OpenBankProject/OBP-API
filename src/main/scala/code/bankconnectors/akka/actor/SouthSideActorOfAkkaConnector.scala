package code.bankconnectors.akka.actor

import akka.actor.{Actor, ActorLogging}
import code.bankconnectors.LocalMappedConnector
import code.bankconnectors.akka.{OutboundGetBank, OutboundGetBanks}
import code.model.BankId
import code.util.Helper.MdcLoggable


/**
  * This Actor acts in next way:
  */
class SouthSideActorOfAkkaConnector extends Actor with ActorLogging with MdcLoggable {

  def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case OutboundGetBanks(_) =>
      sender ! LocalMappedConnector.getBanks(None)
    
    case OutboundGetBank(_, bankId) =>
      sender ! LocalMappedConnector.getBank(BankId(bankId), None)
  }

}


package code.bankconnectors.akka.actor

import akka.actor.{Actor, ActorLogging}
import code.bankconnectors.LocalMappedConnector
import code.bankconnectors.akka.OutboundGetBanks
import code.util.Helper.MdcLoggable


/**
  * This Actor acts in next way:
  */
class NorthSideAkkaConnectorActor extends Actor with ActorLogging with MdcLoggable {

  def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case OutboundGetBanks(_) =>
      sender ! LocalMappedConnector.getBanks(None)
  }

}


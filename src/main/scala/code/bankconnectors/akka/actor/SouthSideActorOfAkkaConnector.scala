package code.bankconnectors.akka.actor

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import code.api.util.APIUtil
import code.bankconnectors.LocalMappedConnector
import code.bankconnectors.akka.{OutboundGetAdapterInfo, OutboundGetBank, OutboundGetBanks}
import code.bankconnectors.vMar2017.{InboundAdapterInfoInternal, InboundStatusMessage}
import code.model.BankId
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full


/**
  * This Actor acts in next way:
  */
class SouthSideActorOfAkkaConnector extends Actor with ActorLogging with MdcLoggable {

  def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case OutboundGetAdapterInfo(cc, _) =>
      val res = InboundAdapterInfoInternal("",  List(InboundStatusMessage("ESB","Success", "0", "OK")),"systemName", "version", APIUtil.gitCommit, (new Date()).toString)
      sender ! Full(res, cc)   
    
    case OutboundGetBanks(cc) =>
      sender ! LocalMappedConnector.getBanks(cc)
    
    case OutboundGetBank(cc, bankId) =>
      sender ! LocalMappedConnector.getBank(BankId(bankId), cc)
  }

}


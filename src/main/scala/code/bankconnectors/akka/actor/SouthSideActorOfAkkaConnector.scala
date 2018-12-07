package code.bankconnectors.akka.actor

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import code.api.util.APIUtil
import code.bankconnectors.LocalMappedConnector._
import code.bankconnectors.akka._
import code.bankconnectors.vMar2017.{InboundAdapterInfoInternal, InboundStatusMessage}
import code.model.BankId
import code.model.dataAccess.MappedBank
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Full}


/**
  * This Actor acts in next way:
  */
class SouthSideActorOfAkkaConnector extends Actor with ActorLogging with MdcLoggable {

  def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case OutboundGetAdapterInfo(_, cc) =>
      val result = 
        InboundAdapterInfo(
          "systemName",
          "version", 
          APIUtil.gitCommit, 
          (new Date()).toString,
          cc
        )
      sender ! result   
    
    case OutboundGetBanks(cc) =>
      val result: Box[List[MappedBank]] = getBanks(None).map(r => r._1)
      sender ! InboundGetBanks(result.map(l => l.map(Transformer.bank(_))).toOption, cc)
    
    case OutboundGetBank(bankId, cc) =>
      val result: Box[MappedBank] = getBank(BankId(bankId), None).map(r => r._1)
      sender ! InboundGetBank(result.map(Transformer.bank(_)).toOption, cc)
  }

}



object Transformer {
  def bank(mb: MappedBank): Bank = 
    Bank(
      bankId=mb.bankId,
      shortName=mb.shortName,
      fullName=mb.fullName,
      logoUrl=mb.logoUrl,
      websiteUrl=mb.websiteUrl,
      bankRoutingScheme=mb.bankRoutingScheme,
      bankRoutingAddress=mb.bankRoutingAddress
    )
}


package code.bankconnectors.akka

import akka.pattern.ask
import code.actorsystem.ObpLookupSystem
import code.api.util.CallContext
import code.bankconnectors.Connector
import code.bankconnectors.akka.actor.{AkkaConnectorActorInit, AkkaConnectorHelperActor}
import code.bankconnectors.vJune2017.{AuthInfo, OutboundGetBanks, OutboundGetBank}
import code.model.{Bank, BankId}
import code.model.dataAccess.MappedBank
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future

object AkkaConnector_vDec2018 extends Connector with AkkaConnectorActorInit {

  implicit override val nameOfConnector = AkkaConnector_vDec2018.toString
  
  lazy val southSideActor = ObpLookupSystem.getAkkaConnectorActor(AkkaConnectorHelperActor.actorName)

  override def getBanksFuture(callContext: Option[CallContext]): Future[Box[(List[MappedBank], Option[CallContext])]] = {
    val req = OutboundGetBanks(AuthInfo())
    (southSideActor ? req).mapTo[Box[(List[MappedBank], Option[CallContext])]]
  }
  
  override def getBankFuture(bankId : BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = {
    val req = OutboundGetBank(AuthInfo(), bankId.value)
    (southSideActor ? req).mapTo[Box[(Bank, Option[CallContext])]]
  }

}

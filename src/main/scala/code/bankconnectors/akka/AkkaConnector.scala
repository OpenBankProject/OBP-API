package code.bankconnectors.akka

import akka.pattern.ask
import code.actorsystem.ObpLookupSystem
import code.api.util.CallContext
import code.bankconnectors.Connector
import code.bankconnectors.akka.actor.{AkkaConnectorActorInit, AkkaConnectorHelperActor}
import code.bankconnectors.vJune2017.{AuthInfo, OutboundGetBanks}
import code.model.dataAccess.MappedBank
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future

object AkkaConnector extends Connector with AkkaConnectorActorInit {

  implicit override val nameOfConnector = AkkaConnector.toString
  
  lazy val southSideActor = ObpLookupSystem.getAkkaConnectorActor(AkkaConnectorHelperActor.actorName)

  override def getBanksFuture(callContext: Option[CallContext]): Future[Box[(List[MappedBank], Option[CallContext])]] = {
    val req = OutboundGetBanks(AuthInfo())
    (southSideActor ? req).mapTo[Box[(List[MappedBank], Option[CallContext])]]
  }

}

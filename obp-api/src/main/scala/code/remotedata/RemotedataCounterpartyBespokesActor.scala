package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.counterparties.{MapperCounterpartyBespokes, RemotedataCounterpartyBespokesCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.CounterpartyBespoke

import scala.collection.immutable.List


class RemotedataCounterpartyBespokesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperCounterpartyBespokes
  val cc = RemotedataCounterpartyBespokesCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke]) =>
      logger.debug(s"createCounterpartyBespokes($mapperCounterpartyPrimaryKey , $bespokes)")
      sender ! (mapper.createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke]))

    case cc.getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long) =>
      logger.debug(s"getCounterpartyBespokesByCounterpartyId($mapperCounterpartyPrimaryKey)")
      sender ! (mapper.getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long))
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

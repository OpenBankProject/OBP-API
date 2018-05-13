package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.metadata.counterparties.{MapperCounterpartyBespokes, RemotedataCounterpartyBespokesCaseClasses}
import code.model.CounterpartyBespoke
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List


class RemotedataCounterpartyBespokesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperCounterpartyBespokes
  val cc = RemotedataCounterpartyBespokesCaseClasses

  def receive = {

    case cc.createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke]) =>
      logger.debug(s"createCounterpartyBespokes($mapperCounterpartyPrimaryKey , $bespokes)")
      sender ! extractResult(mapper.createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke]))

    case cc.getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long) =>
      logger.debug(s"getCounterpartyBespokesByCounterpartyId($mapperCounterpartyPrimaryKey)")
      sender ! extractResult(mapper.getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long))
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

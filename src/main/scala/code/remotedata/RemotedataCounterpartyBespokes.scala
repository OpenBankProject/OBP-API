package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.counterparties._
import code.model.CounterpartyBespoke

import scala.collection.immutable.List


object RemotedataCounterpartyBespokes extends ObpActorInit with CounterpartyBespokes {

  val cc = RemotedataCounterpartyBespokesCaseClasses

  override def createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long,bespokes: List[CounterpartyBespoke]): List[MappedCounterpartyBespoke] =
    extractFuture(actor ? cc.createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke]))
  
  override def getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long): List[MappedCounterpartyBespoke] =
    extractFuture(actor ? cc.getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long))

}

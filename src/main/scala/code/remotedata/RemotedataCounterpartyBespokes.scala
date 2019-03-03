package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.counterparties._
import com.openbankproject.commons.model.CounterpartyBespoke

import scala.collection.immutable.List


object RemotedataCounterpartyBespokes extends ObpActorInit with CounterpartyBespokes {

  val cc = RemotedataCounterpartyBespokesCaseClasses

  override def createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long,bespokes: List[CounterpartyBespoke]): List[MappedCounterpartyBespoke] = getValueFromFuture(
    (actor ? cc.createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke])).mapTo[List[MappedCounterpartyBespoke]]
  )
  
  override def getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long): List[MappedCounterpartyBespoke] = getValueFromFuture(
    (actor ? cc.getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long)).mapTo[List[MappedCounterpartyBespoke]]
  )

}

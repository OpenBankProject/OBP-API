package code.metadata.counterparties

import code.model.CounterpartyBespoke
import code.util.Helper.MdcLoggable
import net.liftweb.mapper.{MappedString, _}

import scala.collection.immutable.List

class MappedCounterpartyBespoke extends LongKeyedMapper[MappedCounterpartyBespoke] with IdPK {
  def getSingleton = MappedCounterpartyBespoke
  
  object mCounterparty extends MappedLongForeignKey(this, MappedCounterparty)
  object mKey extends MappedString(this, 255)
  object mVaule extends MappedString(this, 255)
  
}
object MappedCounterpartyBespoke extends MappedCounterpartyBespoke with LongKeyedMetaMapper[MappedCounterpartyBespoke]{}


object MapperCounterpartyBespokes extends CounterpartyBespokes with MdcLoggable{
  
  def createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long, bespokes: List[CounterpartyBespoke]): List[MappedCounterpartyBespoke]= {
    bespokes.map(
      bespoke =>
        MappedCounterpartyBespoke
          .create
          .mCounterparty(mapperCounterpartyPrimaryKey)
          .mKey(bespoke.key)
          .mVaule(bespoke.value)
          .saveMe()
    )
  }
  
  def getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long): List[MappedCounterpartyBespoke] =
    MappedCounterpartyBespoke
      .findAll(
        By(MappedCounterpartyBespoke.mCounterparty, mapperCounterpartyPrimaryKey)
      )
  
  
}
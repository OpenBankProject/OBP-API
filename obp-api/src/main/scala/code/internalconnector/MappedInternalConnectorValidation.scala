package code.internalconnector

import code.util.UUIDString
import net.liftweb.mapper._

class InternalConnector extends LongKeyedMapper[InternalConnector] with IdPK {

  override def getSingleton = InternalConnector

  object InternalConnectorId extends UUIDString(this)
  object MethodName extends MappedString(this, 255)

  object MethodBody extends MappedText(this)

}


object InternalConnector extends InternalConnector with LongKeyedMetaMapper[InternalConnector] {
  override def dbIndexes: List[BaseIndex[InternalConnector]] = UniqueIndex(InternalConnectorId) :: super.dbIndexes
}


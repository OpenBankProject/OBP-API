package code.connectormethod

import code.util.UUIDString
import net.liftweb.mapper._

class ConnectorMethod extends LongKeyedMapper[ConnectorMethod] with IdPK {

  override def getSingleton = ConnectorMethod

  object ConnectorMethodId extends UUIDString(this)
  object MethodName extends MappedString(this, 255)

  object MethodBody extends MappedText(this)

}


object ConnectorMethod extends ConnectorMethod with LongKeyedMetaMapper[ConnectorMethod] {
  override def dbIndexes: List[BaseIndex[ConnectorMethod]] = UniqueIndex(ConnectorMethodId) :: UniqueIndex(MethodName) :: super.dbIndexes
}


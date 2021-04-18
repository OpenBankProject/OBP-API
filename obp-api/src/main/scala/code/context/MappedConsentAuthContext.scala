package code.context

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.ConsentAuthContext
import net.liftweb.mapper._

class MappedConsentAuthContext extends ConsentAuthContext with LongKeyedMapper[MappedConsentAuthContext] with IdPK with CreatedUpdated {

  def getSingleton = MappedConsentAuthContext

  object ConsentAuthContextId extends MappedUUID(this)
  object ConsentId extends UUIDString(this)
  object Key extends MappedString(this, 255)
  object Value extends MappedString(this, 255)

  override def consentId = ConsentId.get   
  override def key = Key.get  
  override def value = Value.get  
  override def consentAuthContextId = ConsentAuthContextId.get
}

object MappedConsentAuthContext extends MappedConsentAuthContext with LongKeyedMetaMapper[MappedConsentAuthContext] {
  override def dbTableName = "ConsentAuthContext" // define a custom DB table name
  override def dbIndexes = UniqueIndex(ConsentId, Key) :: super.dbIndexes
}

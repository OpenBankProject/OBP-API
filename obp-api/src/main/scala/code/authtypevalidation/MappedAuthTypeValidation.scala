package code.authtypevalidation

import net.liftweb.mapper._

class AuthTypeValidation extends LongKeyedMapper[AuthTypeValidation] with IdPK {

  override def getSingleton = AuthTypeValidation


  object OperationId extends MappedString(this, 200)
  object AllowedAuthTypes extends MappedString(this, 300)

  def operationId: String = OperationId.get
  def allowedAuthTypes: String = AllowedAuthTypes.get
}


object AuthTypeValidation extends AuthTypeValidation with LongKeyedMetaMapper[AuthTypeValidation] {
  override def dbIndexes: List[BaseIndex[AuthTypeValidation]] = UniqueIndex(OperationId) :: super.dbIndexes
}


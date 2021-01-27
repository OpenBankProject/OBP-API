package code.authtypevalidation

import net.liftweb.mapper._

class AuthenticationTypeValidation extends LongKeyedMapper[AuthenticationTypeValidation] with IdPK {

  override def getSingleton = AuthenticationTypeValidation


  object OperationId extends MappedString(this, 200)
  object AllowedAuthTypes extends MappedString(this, 300)

  def operationId: String = OperationId.get
  def allowedAuthTypes: String = AllowedAuthTypes.get
}


object AuthenticationTypeValidation extends AuthenticationTypeValidation with LongKeyedMetaMapper[AuthenticationTypeValidation] {
  override def dbIndexes: List[BaseIndex[AuthenticationTypeValidation]] = UniqueIndex(OperationId) :: super.dbIndexes
}


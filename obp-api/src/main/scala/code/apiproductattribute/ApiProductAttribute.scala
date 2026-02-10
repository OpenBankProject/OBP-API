package code.apiproductattribute

import code.util.{MappedUUID, UUIDString}
import net.liftweb.mapper._

class ApiProductAttribute extends ApiProductAttributeTrait with LongKeyedMapper[ApiProductAttribute] with IdPK with CreatedUpdated {
  def getSingleton = ApiProductAttribute

  object BankId extends UUIDString(this)
  object ApiProductCode extends MappedString(this, 50)
  object ApiProductAttributeId extends MappedUUID(this)
  object Name extends MappedString(this, 256)
  object Type extends MappedString(this, 50)
  object Value extends MappedString(this, 2000)
  object IsActive extends MappedBoolean(this)

  override def bankId: String = BankId.get
  override def apiProductCode: String = ApiProductCode.get
  override def apiProductAttributeId: String = ApiProductAttributeId.get
  override def name: String = Name.get
  override def attributeType: String = Type.get
  override def value: String = Value.get
  override def isActive: Option[Boolean] = Some(IsActive.get)
}

object ApiProductAttribute extends ApiProductAttribute with LongKeyedMetaMapper[ApiProductAttribute] {
  override def dbIndexes = Index(BankId) :: UniqueIndex(ApiProductAttributeId) :: super.dbIndexes
}

trait ApiProductAttributeTrait {
  def bankId: String
  def apiProductCode: String
  def apiProductAttributeId: String
  def name: String
  def attributeType: String
  def value: String
  def isActive: Option[Boolean]
}

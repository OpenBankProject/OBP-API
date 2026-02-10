package code.apiproduct

import code.util.{MappedUUID, UUIDString}
import net.liftweb.mapper._

class ApiProduct extends ApiProductTrait with LongKeyedMapper[ApiProduct] with IdPK with CreatedUpdated {
  def getSingleton = ApiProduct

  object ApiProductId extends MappedUUID(this)
  object BankId extends UUIDString(this)
  object ApiProductCode extends MappedString(this, 50)
  object ParentApiProductCode extends MappedString(this, 50)
  object Name extends MappedString(this, 256)
  object Category extends MappedString(this, 256)
  object MoreInfoUrl extends MappedString(this, 2000)
  object TermsAndConditionsUrl extends MappedString(this, 2000)
  object Description extends MappedString(this, 2000)

  override def apiProductId: String = ApiProductId.get
  override def bankId: String = BankId.get
  override def apiProductCode: String = ApiProductCode.get
  override def parentApiProductCode: String = ParentApiProductCode.get
  override def name: String = Name.get
  override def category: String = Category.get
  override def moreInfoUrl: String = MoreInfoUrl.get
  override def termsAndConditionsUrl: String = TermsAndConditionsUrl.get
  override def description: String = Description.get
}

object ApiProduct extends ApiProduct with LongKeyedMetaMapper[ApiProduct] {
  override def dbIndexes = UniqueIndex(BankId, ApiProductCode) :: Index(BankId) :: super.dbIndexes
}

trait ApiProductTrait {
  def apiProductId: String
  def bankId: String
  def apiProductCode: String
  def parentApiProductCode: String
  def name: String
  def category: String
  def moreInfoUrl: String
  def termsAndConditionsUrl: String
  def description: String
}

package code.apiproductsubscriptionattribute

import code.util.MappedUUID
import net.liftweb.mapper._

/** Attributes on an API Product Subscription. Billing adapters store e.g. STRIPE_SUBSCRIPTION_ID here. */
class ApiProductSubscriptionAttribute extends ApiProductSubscriptionAttributeTrait with LongKeyedMapper[ApiProductSubscriptionAttribute] with IdPK with CreatedUpdated {
  def getSingleton = ApiProductSubscriptionAttribute

  object ApiProductSubscriptionId extends MappedString(this, 50)
  object ApiProductSubscriptionAttributeId extends MappedUUID(this)
  object Name extends MappedString(this, 256)
  object Type extends MappedString(this, 50)
  object Value extends MappedString(this, 2000)
  object IsActive extends MappedBoolean(this)

  override def apiProductSubscriptionId: String = ApiProductSubscriptionId.get
  override def apiProductSubscriptionAttributeId: String = ApiProductSubscriptionAttributeId.get
  override def name: String = Name.get
  override def attributeType: String = Type.get
  override def value: String = Value.get
  override def isActive: Option[Boolean] = Some(IsActive.get)
}

object ApiProductSubscriptionAttribute extends ApiProductSubscriptionAttribute with LongKeyedMetaMapper[ApiProductSubscriptionAttribute] {
  override def dbIndexes = Index(ApiProductSubscriptionId) :: UniqueIndex(ApiProductSubscriptionAttributeId) :: super.dbIndexes
}

trait ApiProductSubscriptionAttributeTrait {
  def apiProductSubscriptionId: String
  def apiProductSubscriptionAttributeId: String
  def name: String
  def attributeType: String
  def value: String
  def isActive: Option[Boolean]
}

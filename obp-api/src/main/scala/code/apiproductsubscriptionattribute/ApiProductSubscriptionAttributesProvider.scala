package code.apiproductsubscriptionattribute

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

trait ApiProductSubscriptionAttributesProvider {
  def getApiProductSubscriptionAttributes(apiProductSubscriptionId: String): Box[List[ApiProductSubscriptionAttributeTrait]]

  def getApiProductSubscriptionAttributeById(apiProductSubscriptionAttributeId: String): Box[ApiProductSubscriptionAttributeTrait]

  def createOrUpdateApiProductSubscriptionAttribute(
    apiProductSubscriptionId: String,
    apiProductSubscriptionAttributeId: Option[String],
    name: String,
    attributeType: String,
    value: String,
    isActive: Option[Boolean]
  ): Box[ApiProductSubscriptionAttributeTrait]

  def deleteApiProductSubscriptionAttribute(apiProductSubscriptionAttributeId: String): Box[Boolean]

  def deleteApiProductSubscriptionAttributes(apiProductSubscriptionId: String): Box[Boolean]
}

object DoobieApiProductSubscriptionAttributesProvider extends MdcLoggable with ApiProductSubscriptionAttributesProvider {

  override def getApiProductSubscriptionAttributes(apiProductSubscriptionId: String): Box[List[ApiProductSubscriptionAttributeTrait]] =
    tryo(ApiProductSubscriptionAttribute.findBySubscriptionId(apiProductSubscriptionId)
      .map(r => r: ApiProductSubscriptionAttributeTrait))

  override def getApiProductSubscriptionAttributeById(apiProductSubscriptionAttributeId: String): Box[ApiProductSubscriptionAttributeTrait] =
    ApiProductSubscriptionAttribute.findById(apiProductSubscriptionAttributeId)

  override def createOrUpdateApiProductSubscriptionAttribute(
    apiProductSubscriptionId: String,
    apiProductSubscriptionAttributeId: Option[String],
    name: String,
    attributeType: String,
    value: String,
    isActive: Option[Boolean]
  ): Box[ApiProductSubscriptionAttributeTrait] = {
    val existing = apiProductSubscriptionAttributeId.flatMap(id =>
      ApiProductSubscriptionAttribute.findById(id).toOption)
    existing match {
      case Some(row) =>
        ApiProductSubscriptionAttribute.update(row.apiProductSubscriptionAttributeId,
          apiProductSubscriptionId, name, attributeType, value, isActive.getOrElse(true))
      case None =>
        tryo(ApiProductSubscriptionAttribute.insert(apiProductSubscriptionId, name, attributeType,
          value, isActive.getOrElse(true)))
    }
  }

  override def deleteApiProductSubscriptionAttribute(apiProductSubscriptionAttributeId: String): Box[Boolean] =
    ApiProductSubscriptionAttribute.findById(apiProductSubscriptionAttributeId)
      .map(_ => ApiProductSubscriptionAttribute.delete(apiProductSubscriptionAttributeId))

  override def deleteApiProductSubscriptionAttributes(apiProductSubscriptionId: String): Box[Boolean] =
    tryo(ApiProductSubscriptionAttribute.deleteBySubscriptionId(apiProductSubscriptionId))
}

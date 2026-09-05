package code.apiproductsubscriptionattribute

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.mapper.By
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

object MappedApiProductSubscriptionAttributesProvider extends MdcLoggable with ApiProductSubscriptionAttributesProvider {

  override def getApiProductSubscriptionAttributes(apiProductSubscriptionId: String): Box[List[ApiProductSubscriptionAttributeTrait]] =
    tryo(ApiProductSubscriptionAttribute.findAll(By(ApiProductSubscriptionAttribute.ApiProductSubscriptionId, apiProductSubscriptionId)))

  override def getApiProductSubscriptionAttributeById(apiProductSubscriptionAttributeId: String): Box[ApiProductSubscriptionAttributeTrait] =
    ApiProductSubscriptionAttribute.find(By(ApiProductSubscriptionAttribute.ApiProductSubscriptionAttributeId, apiProductSubscriptionAttributeId))

  override def createOrUpdateApiProductSubscriptionAttribute(
    apiProductSubscriptionId: String,
    apiProductSubscriptionAttributeId: Option[String],
    name: String,
    attributeType: String,
    value: String,
    isActive: Option[Boolean]
  ): Box[ApiProductSubscriptionAttributeTrait] = {
    val existing = apiProductSubscriptionAttributeId.flatMap(id =>
      ApiProductSubscriptionAttribute.find(By(ApiProductSubscriptionAttribute.ApiProductSubscriptionAttributeId, id)))
    existing match {
      case Some(row) =>
        tryo(
          row
            .ApiProductSubscriptionId(apiProductSubscriptionId)
            .Name(name)
            .Type(attributeType)
            .Value(value)
            .IsActive(isActive.getOrElse(true))
            .saveMe()
        )
      case None =>
        tryo(
          ApiProductSubscriptionAttribute.create
            .ApiProductSubscriptionId(apiProductSubscriptionId)
            .Name(name)
            .Type(attributeType)
            .Value(value)
            .IsActive(isActive.getOrElse(true))
            .saveMe()
        )
    }
  }

  override def deleteApiProductSubscriptionAttribute(apiProductSubscriptionAttributeId: String): Box[Boolean] =
    ApiProductSubscriptionAttribute
      .find(By(ApiProductSubscriptionAttribute.ApiProductSubscriptionAttributeId, apiProductSubscriptionAttributeId))
      .map(_.delete_!)

  override def deleteApiProductSubscriptionAttributes(apiProductSubscriptionId: String): Box[Boolean] = tryo {
    ApiProductSubscriptionAttribute
      .findAll(By(ApiProductSubscriptionAttribute.ApiProductSubscriptionId, apiProductSubscriptionId))
      .foreach(_.delete_!)
    true
  }
}

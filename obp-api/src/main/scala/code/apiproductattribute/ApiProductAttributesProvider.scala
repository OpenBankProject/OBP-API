package code.apiproductattribute

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

trait ApiProductAttributesProvider {
  def getApiProductAttributesByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[List[ApiProductAttributeTrait]]

  def getApiProductAttributeById(
    apiProductAttributeId: String
  ): Box[ApiProductAttributeTrait]

  def createOrUpdateApiProductAttribute(
    bankId: String,
    apiProductCode: String,
    apiProductAttributeId: Option[String],
    name: String,
    attributeType: String,
    value: String,
    isActive: Option[Boolean]
  ): Box[ApiProductAttributeTrait]

  def deleteApiProductAttribute(
    apiProductAttributeId: String
  ): Box[Boolean]

  def deleteApiProductAttributesByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[Boolean]
}

object MappedApiProductAttributesProvider extends MdcLoggable with ApiProductAttributesProvider {

  override def getApiProductAttributesByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[List[ApiProductAttributeTrait]] = {
    tryo(
      ApiProductAttribute.findAll(
        By(ApiProductAttribute.BankId, bankId),
        By(ApiProductAttribute.ApiProductCode, apiProductCode)
      )
    )
  }

  override def getApiProductAttributeById(
    apiProductAttributeId: String
  ): Box[ApiProductAttributeTrait] = {
    ApiProductAttribute.find(By(ApiProductAttribute.ApiProductAttributeId, apiProductAttributeId))
  }

  override def createOrUpdateApiProductAttribute(
    bankId: String,
    apiProductCode: String,
    apiProductAttributeId: Option[String],
    name: String,
    attributeType: String,
    value: String,
    isActive: Option[Boolean]
  ): Box[ApiProductAttributeTrait] = {
    apiProductAttributeId match {
      case Some(id) =>
        ApiProductAttribute.find(By(ApiProductAttribute.ApiProductAttributeId, id)) match {
          case net.liftweb.common.Full(existing) =>
            tryo(
              existing
                .BankId(bankId)
                .ApiProductCode(apiProductCode)
                .Name(name)
                .Type(attributeType)
                .Value(value)
                .IsActive(isActive.getOrElse(true))
                .saveMe()
            )
          case _ =>
            createNew(bankId, apiProductCode, name, attributeType, value, isActive)
        }
      case None =>
        createNew(bankId, apiProductCode, name, attributeType, value, isActive)
    }
  }

  private def createNew(
    bankId: String,
    apiProductCode: String,
    name: String,
    attributeType: String,
    value: String,
    isActive: Option[Boolean]
  ): Box[ApiProductAttributeTrait] = {
    tryo(
      ApiProductAttribute
        .create
        .BankId(bankId)
        .ApiProductCode(apiProductCode)
        .Name(name)
        .Type(attributeType)
        .Value(value)
        .IsActive(isActive.getOrElse(true))
        .saveMe()
    )
  }

  override def deleteApiProductAttribute(
    apiProductAttributeId: String
  ): Box[Boolean] = {
    ApiProductAttribute.find(By(ApiProductAttribute.ApiProductAttributeId, apiProductAttributeId)).map(_.delete_!)
  }

  override def deleteApiProductAttributesByBankIdAndCode(
    bankId: String,
    apiProductCode: String
  ): Box[Boolean] = {
    tryo {
      ApiProductAttribute.findAll(
        By(ApiProductAttribute.BankId, bankId),
        By(ApiProductAttribute.ApiProductCode, apiProductCode)
      ).foreach(_.delete_!)
      true
    }
  }
}

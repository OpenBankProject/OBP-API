package code.apiproductattribute

import net.liftweb.common.Box

trait ApiProductAttributeTrait {
  def bankId: String
  def apiProductCode: String
  def apiProductAttributeId: String
  def name: String
  def attributeType: String
  def value: String
  def isActive: Option[Boolean]
}

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

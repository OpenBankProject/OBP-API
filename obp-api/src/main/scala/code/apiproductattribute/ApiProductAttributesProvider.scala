/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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

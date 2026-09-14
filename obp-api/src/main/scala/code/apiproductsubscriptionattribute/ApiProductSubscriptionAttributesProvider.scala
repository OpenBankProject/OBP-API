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

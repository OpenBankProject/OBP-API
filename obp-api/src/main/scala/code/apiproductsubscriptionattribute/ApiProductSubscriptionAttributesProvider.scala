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

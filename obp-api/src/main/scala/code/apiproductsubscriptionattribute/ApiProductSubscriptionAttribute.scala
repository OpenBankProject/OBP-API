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

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

import code.util.{MappedUUID, UUIDString}
import net.liftweb.mapper._

class ApiProductAttribute extends ApiProductAttributeTrait with LongKeyedMapper[ApiProductAttribute] with IdPK with CreatedUpdated {
  def getSingleton = ApiProductAttribute

  object BankId extends UUIDString(this)
  object ApiProductCode extends MappedString(this, 50)
  object ApiProductAttributeId extends MappedUUID(this)
  object Name extends MappedString(this, 256)
  object Type extends MappedString(this, 50)
  object Value extends MappedString(this, 2000)
  object IsActive extends MappedBoolean(this)

  override def bankId: String = BankId.get
  override def apiProductCode: String = ApiProductCode.get
  override def apiProductAttributeId: String = ApiProductAttributeId.get
  override def name: String = Name.get
  override def attributeType: String = Type.get
  override def value: String = Value.get
  override def isActive: Option[Boolean] = Some(IsActive.get)
}

object ApiProductAttribute extends ApiProductAttribute with LongKeyedMetaMapper[ApiProductAttribute] {
  override def dbIndexes = Index(BankId) :: UniqueIndex(ApiProductAttributeId) :: super.dbIndexes
}

trait ApiProductAttributeTrait {
  def bankId: String
  def apiProductCode: String
  def apiProductAttributeId: String
  def name: String
  def attributeType: String
  def value: String
  def isActive: Option[Boolean]
}

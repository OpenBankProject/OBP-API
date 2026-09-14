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

package code.cardattribute

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.CardAttributeType
import net.liftweb.mapper._

class MappedCardAttribute extends CardAttribute with LongKeyedMapper[MappedCardAttribute] with IdPK {

  override def getSingleton = MappedCardAttribute

  object mBankId extends UUIDString(this) // combination of this
  object mCardId extends UUIDString(this) // combination of this

  object mCardAttributeId extends MappedUUID(this)

  object mName extends MappedString(this, 50)

  object mType extends MappedString(this, 50)

  object mValue extends MappedString(this, 255)


  override def bankId = Some(BankId(mBankId.get))

  override def cardId = Some(mCardId.get)

  override def cardAttributeId = Some(mCardAttributeId.get)

  override def name: String = mName.get

  override def attributeType: CardAttributeType.Value = CardAttributeType.withName(mType.get)

  override def value: String = mValue.get


}


object MappedCardAttribute extends MappedCardAttribute with LongKeyedMetaMapper[MappedCardAttribute] {
  override def dbIndexes: List[BaseIndex[MappedCardAttribute]] = Index(mCardId) :: Index(mCardAttributeId) :: super.dbIndexes
}


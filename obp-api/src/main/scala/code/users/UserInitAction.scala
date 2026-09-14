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

package code.users

import code.util.MappedUUID
import net.liftweb.mapper._

class UserInitAction extends UserInitActionTrait with LongKeyedMapper[UserInitAction] with IdPK with CreatedUpdated {
  def getSingleton = UserInitAction

  object UserId extends MappedUUID(this)
  object ActionName extends MappedString(this, 100)
  object ActionValue extends MappedString(this, 100)
  object Success extends MappedBoolean(this)

  override def userId: String = UserId.get
  override def actionName: String = ActionName.get
  override def actionValue: String = ActionValue.get
  override def success: Boolean = Success.get
}

object UserInitAction extends UserInitAction with LongKeyedMetaMapper[UserInitAction] {
  override def dbIndexes: List[BaseIndex[UserInitAction]] = UniqueIndex(UserId, ActionName, ActionValue) :: super.dbIndexes
}

trait UserInitActionTrait {
  def userId: String
  def actionName: String
  def actionValue: String
  def success: Boolean
}

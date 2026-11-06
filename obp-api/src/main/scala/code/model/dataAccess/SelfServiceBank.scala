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
package code.model.dataAccess

import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

/**
 * The banks made with Create My Bank (POST /my/banks): a space of their own for developers, without CanCreateBank.
 * Anyone may make one and grant themselves its Roles, so those Roles vouch for nothing: files stored there start
 * pending, for example. The Create My Bank quota counts these rows.
 *
 * Create My Bank refuses unless OBP stores banks itself (the mapped connector), so a self-service bank is never on a
 * backend system. A bank from a backend is never in this record, and counts as a real bank.
 */
class SelfServiceBank extends LongKeyedMapper[SelfServiceBank] with IdPK {
  def getSingleton = SelfServiceBank

  object BankId extends MappedString(this, 255)
  object CreatedByUserId extends MappedString(this, 255)
  object CreatedAt extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
}

object SelfServiceBank extends SelfServiceBank with LongKeyedMetaMapper[SelfServiceBank] {
  override def dbTableName = "SelfServiceBank"
  override def dbIndexes = UniqueIndex(BankId) :: super.dbIndexes

  /** Whether the bank was made with Create My Bank. SYS and banks from a backend are not. */
  def isSelfService(bankId: String): Boolean = find(By(BankId, bankId)).isDefined

  def record(bankId: String, createdByUserId: String): Unit =
    if (!isSelfService(bankId)) tryo(create.BankId(bankId).CreatedByUserId(createdByUserId).saveMe())
}

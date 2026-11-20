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

import com.openbankproject.commons.model.BankRoutingJson
import net.liftweb.mapper._

/**
 * One bank routing of a bank stored by the local database connector, such as its BIC or its BLZ.
 * A bank can have any number of them. Before this table, a bank had room for only one routing (the
 * bank routing columns on MappedBank) plus the deprecated swift_bic column. Those columns are still
 * read, so banks stored before this table need no migration.
 *
 * A routing belongs to one bank only: the same scheme and address cannot be stored for two banks.
 */
class MappedBankRouting extends LongKeyedMapper[MappedBankRouting] with IdPK with CreatedUpdated {
  def getSingleton: MappedBankRouting.type = MappedBankRouting

  // The OBP bank id (MappedBank.permalink).
  object BankId extends MappedString(this, 255)

  // Stored as written, except that every spelling of BIC is stored as "BIC".
  object Scheme extends MappedString(this, 64)

  object Address extends MappedString(this, 255)

  def bankRouting: BankRoutingJson = BankRoutingJson(Scheme.get, Address.get)
}

object MappedBankRouting extends MappedBankRouting with LongKeyedMetaMapper[MappedBankRouting] {

  override def dbIndexes: List[BaseIndex[MappedBankRouting]] =
    Index(BankId) :: UniqueIndex(Scheme, Address) :: super.dbIndexes

  /** The bank's routings, in the order they were stored. */
  def routingsOf(bankId: String): List[BankRoutingJson] =
    findAll(By(BankId, bankId), OrderBy(id, Ascending)).map(_.bankRouting)

  /** Replaces all of the bank's routings with `routings`. */
  def replaceRoutingsOf(bankId: String, routings: List[BankRoutingJson]): Unit = {
    bulkDelete_!!(By(BankId, bankId))
    routings.foreach(r => create.BankId(bankId).Scheme(r.scheme).Address(r.address).saveMe())
  }
}

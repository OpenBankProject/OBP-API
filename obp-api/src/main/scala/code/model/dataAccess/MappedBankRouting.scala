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

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.BankRoutingJson
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._

/**
 * One bank routing of a bank stored by the local database connector, such as its BIC or its BLZ.
 * A bank can have any number of them. Before this table, a bank had room for only one routing (the
 * bank routing columns on MappedBank) plus the deprecated swift_bic column. Those columns are still
 * read, so banks stored before this table need no migration.
 *
 * A routing belongs to one bank only: the same scheme and address cannot be stored for two banks.
 * That is the unique index on (scheme, address), which the database enforces: a second bank's
 * routing with the same pair makes the insert throw.
 *
 * The routings are kept in the table they were first stored in, mappedbankrouting.
 */
case class BankRouting(
  // The OBP bank id (MappedBank.permalink).
  bankId: String,
  // Stored as written, except that every spelling of BIC is stored as "BIC".
  scheme: String,
  address: String
) {
  def bankRouting: BankRoutingJson = BankRoutingJson(scheme, address)
}

object BankRouting {

  // Many banks' routings are read with one IN list per this many banks, well under every
  // database's limit on bound parameters.
  private val InListChunk = 500

  private type Row = (Option[String], Option[String], Option[String])

  private def fromRow(row: Row): BankRouting = BankRouting(row._1.orNull, row._2.orNull, row._3.orNull)

  private val selectColumns = fr"SELECT bankid, scheme, address FROM mappedbankrouting"

  /** The bank's routings, in the order they were stored. */
  def routingsOf(bankId: String): List[BankRoutingJson] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE bankid = ${Option(bankId)} ORDER BY id ASC").query[Row].to[List]
    ).map(fromRow).map(_.bankRouting)

  /**
   * The routings of each of the given banks, by bank id, each in the order it was stored. A bank with
   * none has no entry. One query per chunk of banks, so a list of banks does not cost a query each.
   */
  def routingsOfBanks(bankIds: List[String]): Map[String, List[BankRoutingJson]] = {
    val ids = bankIds.filter(_ != null).distinct
    // Mapper's ByList with an empty list matched nothing.
    if (ids.isEmpty) Map.empty
    else ids.grouped(InListChunk).flatMap { chunk =>
      val in = Fragments.in(fr"bankid", cats.data.NonEmptyList.fromListUnsafe(chunk))
      DoobieUtil.runQuery((selectColumns ++ fr"WHERE" ++ in ++ fr"ORDER BY id ASC").query[Row].to[List])
        .map(fromRow)
    }.toList.groupBy(_.bankId).map { case (bankId, routings) => bankId -> routings.map(_.bankRouting) }
  }

  /**
   * Replaces all of the bank's routings with `routings`. The delete and the inserts run in one
   * transaction (the request's, inside a request), and a routing another bank already has makes an
   * insert throw on the unique index on scheme and address.
   */
  def replaceRoutingsOf(bankId: String, routings: List[BankRoutingJson]): Unit = {
    import cats.implicits._
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val program: ConnectionIO[Unit] = for {
      _ <- sql"DELETE FROM mappedbankrouting WHERE bankid = ${Option(bankId)}".update.run
      _ <- routings.traverse_ { r =>
        sql"""INSERT INTO mappedbankrouting (bankid, scheme, address, createdat, updatedat)
              VALUES (${Option(bankId)}, ${Option(r.scheme)}, ${Option(r.address)}, $now, $now)""".update.run
      }
    } yield ()
    DoobieUtil.runUpdate(program)
  }
}

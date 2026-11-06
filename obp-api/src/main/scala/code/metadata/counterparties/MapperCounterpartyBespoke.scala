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

package code.metadata.counterparties

import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.CounterpartyBespoke
import doobie._
import doobie.implicits._

import scala.collection.immutable.List

/**
 * A free-form key/value pair attached to a counterparty.
 *
 * `counterpartyKey` is MAPPEDCOUNTERPARTY's numeric primary key, not the public counterparty_id —
 * the callers already pass that key in.
 *
 * The value column is spelled `mvaule` in the database. That typo is load-bearing: renaming it here
 * would stop the code finding existing rows.
 */
case class MappedCounterpartyBespoke(
  counterpartyKey: Long,
  key: String,
  value: String
)

object MappedCounterpartyBespoke {

  private val selectColumns = fr"SELECT mcounterparty, mkey, mvaule FROM mappedcounterpartybespoke"

  private def query(condition: Fragment): List[MappedCounterpartyBespoke] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[(Option[Long], Option[String], Option[String])].to[List])
      .map { case (counterpartyKey, key, value) =>
        MappedCounterpartyBespoke(counterpartyKey.getOrElse(0L), key.orNull, value.orNull) }

  def insert(counterpartyKey: Long, key: String, value: String): MappedCounterpartyBespoke = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedcounterpartybespoke (mcounterparty, mkey, mvaule)
            VALUES ($counterpartyKey, $key, $value)"""
        .update.run)
    MappedCounterpartyBespoke(counterpartyKey, key, value)
  }

  def findAllByCounterpartyKey(counterpartyKey: Long): List[MappedCounterpartyBespoke] =
    query(fr"WHERE mcounterparty = $counterpartyKey ORDER BY id ASC")

  def deleteByCounterpartyKey(counterpartyKey: Long): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcounterpartybespoke WHERE mcounterparty = $counterpartyKey".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterpartybespoke".update.run)
    ()
  }
}

object MapperCounterpartyBespokes extends CounterpartyBespokes with MdcLoggable {

  def createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long,
                                 bespokes: List[CounterpartyBespoke]): List[MappedCounterpartyBespoke] =
    bespokes.map(b => MappedCounterpartyBespoke.insert(mapperCounterpartyPrimaryKey, b.key, b.value))

  def getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long): List[MappedCounterpartyBespoke] =
    MappedCounterpartyBespoke.findAllByCounterpartyKey(mapperCounterpartyPrimaryKey)
}

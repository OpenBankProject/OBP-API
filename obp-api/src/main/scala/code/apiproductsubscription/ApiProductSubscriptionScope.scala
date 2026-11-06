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

package code.apiproductsubscription

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

/**
 * Join table recording which Scope rows a subscription created (Phase 3), so that cancelling
 * removes exactly those and never a Scope granted by hand.
 *
 * Doobie store rather than the Lift Mapper entity upstream declares: this branch has no Lift
 * Mapper and no Schemifier, so the table lives in db.changelog-develop-merge-2.yaml.
 */
case class ApiProductSubscriptionScope(
  apiProductSubscriptionId: String,
  scopeId: String
)

object ApiProductSubscriptionScope {

  private val selectColumns =
    fr"SELECT apiproductsubscriptionid, scopeid FROM apiproductsubscriptionscope"

  private type Row = (Option[String], Option[String])

  private def fromRow(row: Row): ApiProductSubscriptionScope =
    ApiProductSubscriptionScope(row._1.orNull, row._2.orNull)

  def findBySubscriptionId(apiProductSubscriptionId: String): List[ApiProductSubscriptionScope] =
    DoobieUtil.runQuery(
      (selectColumns ++ fr"WHERE apiproductsubscriptionid = $apiProductSubscriptionId ORDER BY id ASC")
        .query[Row].to[List]).map(fromRow)

  def insert(apiProductSubscriptionId: String, scopeId: String): ApiProductSubscriptionScope = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO apiproductsubscriptionscope
            (apiproductsubscriptionid, scopeid, createdat, updatedat)
            VALUES (${Option(apiProductSubscriptionId)}, ${Option(scopeId)}, $now, $now)"""
        .update.run)
    ApiProductSubscriptionScope(apiProductSubscriptionId, scopeId)
  }

  def deleteBySubscriptionId(apiProductSubscriptionId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"""DELETE FROM apiproductsubscriptionscope
            WHERE apiproductsubscriptionid = $apiProductSubscriptionId""".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM apiproductsubscriptionscope".update.run)
    ()
  }
}

object MappedApiProductSubscriptionScopesProvider {

  def addScopeRecord(apiProductSubscriptionId: String, scopeId: String): Box[ApiProductSubscriptionScope] =
    tryo(ApiProductSubscriptionScope.insert(apiProductSubscriptionId, scopeId))

  def getScopeIds(apiProductSubscriptionId: String): List[String] =
    ApiProductSubscriptionScope.findBySubscriptionId(apiProductSubscriptionId).map(_.scopeId)

  def deleteScopeRecords(apiProductSubscriptionId: String): Box[Boolean] =
    tryo(ApiProductSubscriptionScope.deleteBySubscriptionId(apiProductSubscriptionId))
}

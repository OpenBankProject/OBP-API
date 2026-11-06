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

package code.consumeraccountaccess

import java.util.Date

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{AccountId, BankId, ViewId}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

object ConsumerAccountAccessDbProvider extends ConsumerAccountAccessProvider {

  override def grant(consumerId: String, bankId: BankId, accountId: AccountId, viewId: ViewId, grantedByUserId: String): Box[ConsumerAccountAccessTrait] =
    tryo {
      val now = new Date()
      ConsumerAccountAccess.insert(ConsumerAccountAccess(
        consumerAccountAccessId = java.util.UUID.randomUUID().toString,
        consumerId = consumerId,
        bankId = bankId.value,
        accountId = accountId.value,
        viewId = viewId.value,
        grantedByUserId = grantedByUserId,
        createdAt = now,
        updatedAt = now))
    }

  override def get(bankId: BankId, accountId: AccountId, consumerAccountAccessId: String): Box[ConsumerAccountAccessTrait] =
    ConsumerAccountAccess.first(
      fr"WHERE consumer_account_access_id = ${Option(consumerAccountAccessId)}" ++
        fr"AND bank_id = ${Option(bankId.value)} AND account_id = ${Option(accountId.value)}")

  override def find(consumerId: String, bankId: BankId, accountId: AccountId, viewId: ViewId): Box[ConsumerAccountAccessTrait] =
    ConsumerAccountAccess.first(
      fr"WHERE consumer_id = ${Option(consumerId)} AND bank_id = ${Option(bankId.value)}" ++
        fr"AND account_id = ${Option(accountId.value)} AND view_id = ${Option(viewId.value)}")

  override def getByAccount(bankId: BankId, accountId: AccountId): Box[List[ConsumerAccountAccessTrait]] =
    tryo {
      ConsumerAccountAccess.query(
        fr"WHERE bank_id = ${Option(bankId.value)} AND account_id = ${Option(accountId.value)} ORDER BY id ASC")
    }

  override def revoke(consumerAccountAccessId: String): Box[Boolean] =
    ConsumerAccountAccess.first(fr"WHERE consumer_account_access_id = ${Option(consumerAccountAccessId)}")
      .flatMap(row => tryo(ConsumerAccountAccess.delete(row.consumerAccountAccessId)))
}

/**
 * One row of consumer_account_access. Upstream declares this as a Lift Mapper entity; here it is a
 * Doobie store over the table the changelog creates (db.changelog-develop-merge-3.yaml), unique on
 * consumer_account_access_id and on (consumer_id, bank_id, account_id, view_id).
 */
case class ConsumerAccountAccess(
  consumerAccountAccessId: String,
  consumerId: String,
  bankId: String,
  accountId: String,
  viewId: String,
  grantedByUserId: String,
  createdAt: Date,
  updatedAt: Date
) extends ConsumerAccountAccessTrait

object ConsumerAccountAccess {

  private val selectColumns =
    fr"""SELECT consumer_account_access_id, consumer_id, bank_id, account_id, view_id, granted_by_user_id,
                created_at, updated_at
         FROM consumer_account_access"""

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def readDate(t: Option[java.sql.Timestamp]): Date = t.map(ts => new Date(ts.getTime)).orNull

  private def ts(d: Date): Option[java.sql.Timestamp] = Option(d).map(x => new java.sql.Timestamp(x.getTime))

  private def fromRow(r: Row): ConsumerAccountAccess =
    ConsumerAccountAccess(r._1.orNull, r._2.orNull, r._3.orNull, r._4.orNull, r._5.orNull, r._6.orNull,
      readDate(r._7), readDate(r._8))

  private[consumeraccountaccess] def query(condition: Fragment): List[ConsumerAccountAccess] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private[consumeraccountaccess] def first(condition: Fragment): Box[ConsumerAccountAccess] =
    query(condition ++ fr"ORDER BY id ASC").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def insert(a: ConsumerAccountAccess): ConsumerAccountAccess = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO consumer_account_access (consumer_account_access_id, consumer_id, bank_id, account_id,
              view_id, granted_by_user_id, created_at, updated_at)
            VALUES (${Option(a.consumerAccountAccessId)}, ${Option(a.consumerId)}, ${Option(a.bankId)},
              ${Option(a.accountId)}, ${Option(a.viewId)}, ${Option(a.grantedByUserId)}, ${ts(a.createdAt)},
              ${ts(a.updatedAt)})""".update.run)
    a
  }

  def delete(consumerAccountAccessId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""DELETE FROM consumer_account_access
            WHERE consumer_account_access_id = ${Option(consumerAccountAccessId)}""".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM consumer_account_access".update.run)
    ()
  }
}

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

package code.accountcreation

import java.util.Date

import code.api.util.DoobieUtil
import code.consent.Consents
import code.users.{UserReference, Users}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

/**
 * Who created a bank account, and for whom.
 *
 * The account itself is created through the Connector, which may keep it outside OBP's database, so
 * the creator cannot be a column of the account. This table holds one row per account created
 * through v7.0.0 Create Account. It records both ids, like a Counterparty or a Dynamic Entity
 * webhook: the caller, which is an agent's own id when an agent made the call, and the person the
 * call was made for. See UserReference.AccountCreation_CreatedByUserId. When an agent made the call,
 * it also records the consent_reference_id of the Consent it acted under, the id the metrics record
 * for the same call.
 */
object AccountCreations extends SimpleInjector {
  val accountCreationProvider = new Inject(() => buildOne) {}
  def buildOne: AccountCreationProvider = AccountCreationDbProvider
}

trait AccountCreationTrait {
  def bankId: String
  def accountId: String
  def createdByUserId: String
  def createdByOnBehalfOfUserId: String
  /** The Consent the agent acted under; None when the caller acted for themselves. */
  def consentReferenceId: Option[String]
  def createdAt: Date
}

trait AccountCreationProvider {
  /** Records that `createdByUserId`, the authenticated caller, created the account. */
  def record(bankId: String, accountId: String, createdByUserId: String): Box[AccountCreationTrait]
  def get(bankId: String, accountId: String): Box[AccountCreationTrait]
  /** The records of those of `accountIds` at `bankId` that have one, in one query. */
  def getForAccounts(bankId: String, accountIds: List[String]): List[AccountCreationTrait]
}

object AccountCreationDbProvider extends AccountCreationProvider {

  override def record(bankId: String, accountId: String, createdByUserId: String): Box[AccountCreationTrait] =
    for {
      attribution <- Users.users.vend.attributionOf(createdByUserId, UserReference.AccountCreation_CreatedByUserId)
      consentReferenceId = attribution.consentId
        .flatMap(id => Consents.consentProvider.vend.getConsentByConsentId(id).toOption)
        .map(_.consentReferenceId)
      row <- tryo {
        AccountCreation.insert(AccountCreation(
          bankId = bankId,
          accountId = accountId,
          createdByUserId = attribution.userId,
          createdByOnBehalfOfUserId = attribution.onBehalfOfUserId,
          consentReferenceIdRaw = consentReferenceId.orNull,
          createdAt = new Date()))
      }
    } yield row

  override def get(bankId: String, accountId: String): Box[AccountCreationTrait] =
    AccountCreation.query(fr"WHERE bank_id = ${Option(bankId)} AND account_id = ${Option(accountId)} ORDER BY id ASC")
      .headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  override def getForAccounts(bankId: String, accountIds: List[String]): List[AccountCreationTrait] =
    if (accountIds.isEmpty) Nil
    else AccountCreation.query(fr"WHERE bank_id = ${Option(bankId)} AND" ++
      Fragments.in(fr"account_id", cats.data.NonEmptyList.fromListUnsafe(accountIds.distinct)) ++ fr"ORDER BY id ASC")
}

/**
 * One row of account_creation. Upstream declares this as a Lift Mapper entity; here it is a Doobie
 * store over the table the changelog creates (db.changelog-develop-merge-3.yaml), unique on
 * (bank_id, account_id).
 */
case class AccountCreation(
  bankId: String,
  accountId: String,
  createdByUserId: String,
  createdByOnBehalfOfUserId: String,
  private[accountcreation] val consentReferenceIdRaw: String,
  createdAt: Date
) extends AccountCreationTrait {
  override def consentReferenceId: Option[String] = Option(consentReferenceIdRaw).filter(_.nonEmpty)
}

object AccountCreation {

  private val selectColumns =
    fr"""SELECT bank_id, account_id, created_by_user_id, created_by_on_behalf_of_user_id, consent_reference_id,
                created_at
         FROM account_creation"""

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp])

  private def fromRow(r: Row): AccountCreation =
    AccountCreation(r._1.orNull, r._2.orNull, r._3.orNull, r._4.orNull, r._5.orNull,
      r._6.map(ts => new Date(ts.getTime)).orNull)

  private[accountcreation] def query(condition: Fragment): List[AccountCreation] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(a: AccountCreation): AccountCreation = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO account_creation (bank_id, account_id, created_by_user_id, created_by_on_behalf_of_user_id,
              consent_reference_id, created_at)
            VALUES (${Option(a.bankId)}, ${Option(a.accountId)}, ${Option(a.createdByUserId)},
              ${Option(a.createdByOnBehalfOfUserId)}, ${Option(a.consentReferenceIdRaw)},
              ${Option(a.createdAt).map(d => new java.sql.Timestamp(d.getTime))})""".update.run)
    a
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM account_creation".update.run)
    ()
  }
}

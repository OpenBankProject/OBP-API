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

package code.api.util

import doobie._
import doobie.implicits._

/**
 * Row from the v_account_access_with_views SQL view.
 * This view joins accountaccess + resourceuser + viewdefinition in a single query.
 */
case class AccountAccessWithViewRow(
  accountAccessId: Long,
  bankId: String,
  accountId: String,
  viewId: String,
  consumerId: String,
  userId: String,
  username: String,
  email: Option[String],
  provider: String,
  resourceUserPrimaryKey: Long,
  viewName: String,
  viewDescription: Option[String],
  metadataView: Option[String],
  isSystem: Boolean,
  isPublic: Boolean,
  isFirehose: Boolean
)

/**
 * Doobie queries against the v_account_access_with_views SQL view.
 *
 * These replace multiple Mapper queries (AccountAccess + ResourceUser + ViewDefinition)
 * with a single SQL query, eliminating N+1 patterns and reducing round-trips.
 *
 * The SQL view is created by MigrationOfAccountAccessWithViewsView.
 */
object DoobieAccountAccessViewQueries {

  private val baseSelect = fr"""
    SELECT account_access_id, bank_id, account_id, view_id, consumer_id,
           user_id, username, email, provider, resource_user_primary_key,
           view_name, view_description, metadata_view, is_system, is_public, is_firehose
    FROM v_account_access_with_views
  """

  /**
   * Filter for private views only, unless allowPublicViews is enabled.
   */
  private def privateFilter: Fragment = {
    if (APIUtil.allowPublicViews) fr""
    else fr"AND is_public = ${false}"
  }

  /** Get all account access rows for a user (by userId UUID string). */
  def getByUser(userId: String): List[AccountAccessWithViewRow] = {
    val query = (baseSelect ++ fr"WHERE user_id = $userId" ++ privateFilter)
      .query[AccountAccessWithViewRow].to[List]
    DoobieUtil.runQuery(query)
  }

  /** Get account access rows for a user at a specific bank. */
  def getByUserAndBank(userId: String, bankId: String): List[AccountAccessWithViewRow] = {
    val query = (baseSelect ++ fr"WHERE user_id = $userId AND bank_id = $bankId" ++ privateFilter)
      .query[AccountAccessWithViewRow].to[List]
    DoobieUtil.runQuery(query)
  }

  /** Get account access rows for a user at a specific bank/account. */
  def getByUserBankAccount(userId: String, bankId: String, accountId: String): List[AccountAccessWithViewRow] = {
    val query = (baseSelect ++ fr"WHERE user_id = $userId AND bank_id = $bankId AND account_id = $accountId" ++ privateFilter)
      .query[AccountAccessWithViewRow].to[List]
    DoobieUtil.runQuery(query)
  }

  /** Get account access rows for a user filtered by view IDs. */
  def getByUserAndViewIds(userId: String, viewIds: List[String]): List[AccountAccessWithViewRow] = {
    if (viewIds.isEmpty) return Nil
    val inClause = viewIds.map(v => fr"$v").reduceLeft((a, b) => a ++ fr"," ++ b)
    val query = (baseSelect ++ fr"WHERE user_id = $userId AND view_id IN (" ++ inClause ++ fr")" ++ privateFilter)
      .query[AccountAccessWithViewRow].to[List]
    DoobieUtil.runQuery(query)
  }

  /** Get account access rows for a user at a specific bank through a specific view. */
  def getByUserBankView(userId: String, bankId: String, viewId: String): List[AccountAccessWithViewRow] = {
    val query = (baseSelect ++ fr"WHERE user_id = $userId AND bank_id = $bankId AND view_id = $viewId" ++ privateFilter)
      .query[AccountAccessWithViewRow].to[List]
    DoobieUtil.runQuery(query)
  }

  /** Get all account access rows for a bank/account (for permissions). */
  def getByBankAccount(bankId: String, accountId: String): List[AccountAccessWithViewRow] = {
    val query = (baseSelect ++ fr"WHERE bank_id = $bankId AND account_id = $accountId" ++ privateFilter)
      .query[AccountAccessWithViewRow].to[List]
    DoobieUtil.runQuery(query)
  }
}

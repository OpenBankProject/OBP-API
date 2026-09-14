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

import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

/**
 * Join table recording which Scope rows a subscription created (Phase 3), so that cancelling
 * removes exactly those and never a Scope granted by hand.
 */
class ApiProductSubscriptionScope extends LongKeyedMapper[ApiProductSubscriptionScope] with IdPK with CreatedUpdated {
  def getSingleton = ApiProductSubscriptionScope

  object ApiProductSubscriptionId extends MappedString(this, 50)
  object ScopeId extends MappedString(this, 50)

  def apiProductSubscriptionId: String = ApiProductSubscriptionId.get
  def scopeId: String = ScopeId.get
}

object ApiProductSubscriptionScope extends ApiProductSubscriptionScope with LongKeyedMetaMapper[ApiProductSubscriptionScope] {
  override def dbIndexes = Index(ApiProductSubscriptionId) :: super.dbIndexes
}

object MappedApiProductSubscriptionScopesProvider {

  def addScopeRecord(apiProductSubscriptionId: String, scopeId: String): Box[ApiProductSubscriptionScope] = tryo(
    ApiProductSubscriptionScope.create
      .ApiProductSubscriptionId(apiProductSubscriptionId)
      .ScopeId(scopeId)
      .saveMe()
  )

  def getScopeIds(apiProductSubscriptionId: String): List[String] =
    ApiProductSubscriptionScope
      .findAll(By(ApiProductSubscriptionScope.ApiProductSubscriptionId, apiProductSubscriptionId))
      .map(_.scopeId)

  def deleteScopeRecords(apiProductSubscriptionId: String): Box[Boolean] = tryo {
    ApiProductSubscriptionScope
      .findAll(By(ApiProductSubscriptionScope.ApiProductSubscriptionId, apiProductSubscriptionId))
      .foreach(_.delete_!)
    true
  }
}

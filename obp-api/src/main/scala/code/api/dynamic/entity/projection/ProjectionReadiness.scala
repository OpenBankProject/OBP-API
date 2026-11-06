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

package code.api.dynamic.entity.projection

import code.api.dynamic.entity.query.QueryPlan

/**
 * This object says whether the query projection can serve a plan right now: every field the plan
 * filters or sorts on has a projection column that is provisioned and backfilled ("ready"), and so
 * has the link field and every nested-filter field of each `obp_exists` / `obp_not_exists` join.
 * A field that is declared indexed but not yet ready makes this false; the caller decides what that
 * means (the list endpoint answers 409; a Dynamic Query reads in memory instead).
 */
object ProjectionReadiness {

  /** The parent fields a plan filters or sorts on. */
  def planFields(plan: QueryPlan): List[String] =
    (plan.filters.map(_.field) ++ plan.sort.map(_.field)).distinct

  def ready(bankId: Option[String], entityName: String, plan: QueryPlan): Boolean = {
    val parentReady = ProjectionProvisioner.readyFields(bankId, entityName)
    planFields(plan).forall(parentReady.contains) && plan.joins.forall { join =>
      val childReady = ProjectionProvisioner.readyFields(bankId, join.childEntity)
      val linkReady = if (join.onChild) childReady.contains(join.linkField) else parentReady.contains(join.linkField)
      linkReady && join.predicate.map(_.field).forall(childReady.contains)
    }
  }
}

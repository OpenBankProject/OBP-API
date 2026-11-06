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

package code.api.dynamic.entity.query

import cats.effect.IO
import org.json4s.JsonAST.JObject

/**
 * The Shape B seam: one query contract, swappable implementations.
 *
 * Implementations (selected once at startup by capability detection):
 *   - InMemoryQueryBackend     — portable floor for deployments with no projection backend
 *   - PostgresProjectionBackend — Approach A: per-entity typed projection tables + indexes (Phase 3+)
 *   - SqlServerProjectionBackend — deferred (Phase 5.3)
 *
 * The public DE endpoint and the [[QueryPlan]] it parses are identical on every backend; only
 * `query`/`provision` differ. Unservable queries return an error — never a silent fallback.
 */
trait DynamicEntityQueryBackend {

  /** Short name for logging / capability reporting (e.g. "in-memory", "postgres"). */
  def name: String

  /**
   * Execute a validated plan and return the matching records (already filtered, sorted, paged).
   * Records are canonical JObjects hydrated from the blob store.
   */
  def query(
    entityName: String,
    bankId: Option[String],
    userId: Option[String],
    isPersonalEntity: Boolean,
    plan: QueryPlan
  ): IO[List[JObject]]

  /**
   * Provision (or reconcile) whatever storage this backend needs for the entity's declared
   * `indexed` fields — DDL, backfill, index build. Default no-op (in-memory needs nothing).
   */
  def provision(entityName: String, bankId: Option[String]): IO[Unit] = IO.unit
}

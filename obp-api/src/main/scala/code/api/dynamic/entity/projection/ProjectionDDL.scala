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

import cats.effect.IO
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._

/**
 * Idempotent DDL for per-entity projection tables (Approach A), executed via Doobie.
 *
 * Phase 2 skeleton: builds and runs the statements; not yet invoked by definition changes (that
 * wiring + backfill + dual-write is Phase 3). Identifiers come from [[ProjectionNaming]] (hashed /
 * sanitised) and are injected via `Fragment.const` — Doobie binds *values* but not *identifiers*,
 * so identifier safety rests on ProjectionNaming, never on raw user strings.
 *
 * Postgres-first (SQL Server backend deferred — Phase 5.3). `CREATE INDEX CONCURRENTLY` must run
 * outside a transaction; `DoobieUtil.runQueryIO` uses the autocommit fallback pool, not the request
 * connection.
 */
object ProjectionDDL extends MdcLoggable {

  /** Map a DE scalar field type to a portable SQL column type. (Spatial handled separately in Phase 4.) */
  def sqlColumnType(fieldType: String): String = fieldType match {
    case "number"        => "numeric"
    case "integer"       => "bigint"
    case "boolean"       => "boolean"
    case "DATE_WITH_DAY" => "date"
    case _               => "text" // string + reference types
  }

  /** CREATE TABLE IF NOT EXISTS de_<hash>(data_id varchar primary key). */
  def createTableIO(safeTable: String): IO[Int] =
    run(s"CREATE TABLE IF NOT EXISTS $safeTable (data_id varchar(255) PRIMARY KEY)")

  /** ALTER TABLE de_<hash> ADD COLUMN IF NOT EXISTS c_<hash> <sqlType> (nullable). */
  def addColumnIO(safeTable: String, safeColumn: String, sqlType: String): IO[Int] =
    run(s"ALTER TABLE $safeTable ADD COLUMN IF NOT EXISTS $safeColumn $sqlType")

  /** CREATE INDEX IF NOT EXISTS idx_... ON de_<hash> (c_<hash>). Runs in any context (used in Phase 3). */
  def createIndexIO(safeTable: String, safeColumn: String): IO[Int] =
    run(s"CREATE INDEX IF NOT EXISTS ${ProjectionNaming.indexName(safeTable, safeColumn)} ON $safeTable ($safeColumn)")

  /** CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_... — non-blocking on large tables, but must run
   *  OUTSIDE a transaction (autocommit connection). Production refinement over [[createIndexIO]]. */
  def createIndexConcurrentlyIO(safeTable: String, safeColumn: String): IO[Int] =
    run(s"CREATE INDEX CONCURRENTLY IF NOT EXISTS ${ProjectionNaming.indexName(safeTable, safeColumn)} ON $safeTable ($safeColumn)")

  /** DROP TABLE IF EXISTS de_<hash> (entity retired / rebuild). */
  def dropTableIO(safeTable: String): IO[Int] =
    run(s"DROP TABLE IF EXISTS $safeTable")

  // All DDL identifiers originate from ProjectionNaming (hashed) — safe to inline via Fragment.const.
  private def run(ddl: String): IO[Int] =
    ProjectionDb.run(Fragment.const(ddl).update.run)
}

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
import code.api.util.{APIUtil, BlockingIoExecutionContext}
import doobie._
import doobie.implicits._

/**
 * A **committing** Doobie transactor over the shared HikariCP pool, for projection operations that
 * run independently of any Lift request transaction — provisioner DDL/backfill and the read-path
 * projection backend. Uses Doobie's default Strategy (autoCommit off → run → commit), so each
 * statement persists.
 *
 * Contrast with `DoobieUtil.runQuery`/`runQueryIO`, which use `Strategy.void` to *share* Lift's
 * request connection/transaction — that is the right tool for the dual-write hook (so the projection
 * upsert commits/rolls back with the canonical blob write), but it never commits on its own.
 */
object ProjectionDb {
  private lazy val xa: Transactor[IO] =
    Transactor.fromDataSource[IO].apply(APIUtil.vendor.HikariDatasource.ds, BlockingIoExecutionContext.ec)

  def run[A](program: ConnectionIO[A]): IO[A] = program.transact(xa)
}

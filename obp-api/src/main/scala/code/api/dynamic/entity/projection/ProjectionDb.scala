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

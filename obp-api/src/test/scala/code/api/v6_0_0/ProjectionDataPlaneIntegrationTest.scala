package code.api.v6_0_0

import code.api.dynamic.entity.projection.{IndexingCapabilities, ProjectionDb, ProjectionDDL, ProjectionSql, ProjectionStore}
import code.api.dynamic.entity.query._
import code.api.util.APIUtil
import cats.effect.unsafe.implicits.global
import doobie.implicits._

/**
 * Phase 3 integration proof: exercises the projection data-plane (DDL + upsert + compiled SQL) against
 * the real Postgres test DB. Validates that numeric filtering, sorting and offset/limit run as actual
 * SQL on a per-entity projection table (not in-memory). No DE-definition machinery — pure data-plane.
 */
class ProjectionDataPlaneIntegrationTest extends V600ServerSetup {

  private val table    = "de_itest_projection"
  private val priceCol = "c_price_itest"

  private def run[A](io: cats.effect.IO[A]): A = io.unsafeRunSync()
  private def upsert(id: String, price: Option[String]): Unit =
    run(ProjectionDb.run(ProjectionStore.upsert(table, id, List(ProjectionStore.ColumnValue(priceCol, "numeric", price)))))

  private def columnOf(f: String): Option[String]  = if (f == "price") Some(priceCol) else None
  private def sqlTypeOf(f: String): Option[String] = if (f == "price") Some("numeric") else None
  private def ids(plan: QueryPlan): List[String] =
    run(ProjectionDb.run(ProjectionSql.selectDataIds(table, plan, columnOf, sqlTypeOf).get.query[String].to[List]))

  feature("DE projection data-plane on Postgres") {
    scenario("create table, upsert rows, then filter / sort / paginate via compiled SQL") {
      // Postgres-only: this test runs Postgres-specific SQL (ON CONFLICT) that H2 cannot execute.
      // Gated OFF by default so CI / H2 / developer workstations skip it (canceled, not failed).
      // Enable locally with `test.projection.postgres=true` in test.default.props AND a Postgres db.url.
      if (!APIUtil.getPropsAsBoolValue("test.projection.postgres", false) || IndexingCapabilities.vendor != IndexingCapabilities.Postgres)
        cancel("Postgres projection integration tests disabled (set test.projection.postgres=true with a Postgres db.url; cannot run on H2).")

      run(ProjectionDDL.dropTableIO(table))
      run(ProjectionDDL.createTableIO(table))
      run(ProjectionDDL.addColumnIO(table, priceCol, "numeric"))

      upsert("id1", Some("10"))
      upsert("id2", Some("5"))
      upsert("id3", Some("20"))
      upsert("id4", None) // coerce-or-null: no price

      Then("numeric filter price < 10 returns only id2 (numeric, not lexical)")
      ids(QueryPlan(List(Filter("price", FilterOp.Lt, List("10"))), Nil, Page.empty)) should equal(List("id2"))

      Then("between 6 and 20 returns id1 and id3")
      ids(QueryPlan(List(Filter("price", FilterOp.Between, List("6", "20"))),
        List(SortKey("price", SortDirection.Asc)), Page.empty)) should equal(List("id1", "id3"))

      Then("sort DESC orders by numeric value; the NULL-price row sorts last and is excluded by a filter")
      ids(QueryPlan(List(Filter("price", FilterOp.Ge, List("0"))),
        List(SortKey("price", SortDirection.Desc)), Page.empty)) should equal(List("id3", "id1", "id2"))

      Then("offset/limit applies after the ORDER BY")
      ids(QueryPlan(List(Filter("price", FilterOp.Ge, List("0"))),
        List(SortKey("price", SortDirection.Asc)), Page(Some(1), Some(1)))) should equal(List("id1"))

      run(ProjectionDDL.dropTableIO(table))
    }
  }
}

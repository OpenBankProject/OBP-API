package code.api.util

import doobie.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * SQL Server and Oracle have no LIMIT, so every row limit goes through DoobieUtil, which writes it in the
 * form the database takes. On H2, Postgres and MySQL the result must be the LIMIT the queries were written
 * with, so nothing changes for the databases that already ran them.
 */
class RowLimitDialectTest extends AnyFlatSpec with Matchers {

  import DoobieUtil._

  private def norm(s: String) = s.trim.replaceAll("\\s+", " ")

  private val ordered = fr"SELECT id FROM t WHERE a = ${"x"} ORDER BY id DESC"
  private val unordered = fr"SELECT id FROM t WHERE a = ${"x"}"

  "a first-row limit" should "stay LIMIT 1 on the databases that have LIMIT" in {
    norm(firstRowFor(LimitDialect, ordered).internals.sql) should equal("SELECT id FROM t WHERE a = ? ORDER BY id DESC LIMIT 1")
    norm(firstRowFor(LimitDialect, unordered).internals.sql) should equal("SELECT id FROM t WHERE a = ? LIMIT 1")
  }

  it should "become OFFSET ... FETCH on SQL Server, adding an ORDER BY only when there is none" in {
    norm(firstRowFor(SqlServerFetchDialect, ordered).internals.sql) should equal(
      "SELECT id FROM t WHERE a = ? ORDER BY id DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY")
    norm(firstRowFor(SqlServerFetchDialect, unordered).internals.sql) should equal(
      "SELECT id FROM t WHERE a = ? ORDER BY (SELECT NULL) OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY")
  }

  it should "become OFFSET ... FETCH on Oracle, which needs no ORDER BY" in {
    norm(firstRowFor(OracleFetchDialect, unordered).internals.sql) should equal(
      "SELECT id FROM t WHERE a = ? OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY")
  }

  "a page" should "be LIMIT and OFFSET where the database has them" in {
    norm(pageFor(LimitDialect, ordered, Some(10), Some(20)).internals.sql) should equal(
      "SELECT id FROM t WHERE a = ? ORDER BY id DESC LIMIT ? OFFSET ?")
    norm(pageFor(LimitDialect, ordered, None, Some(20)).internals.sql) should equal(
      "SELECT id FROM t WHERE a = ? ORDER BY id DESC OFFSET ?")
    norm(pageFor(LimitDialect, ordered, None, None).internals.sql) should equal(norm(ordered.internals.sql))
  }

  it should "put OFFSET before FETCH on SQL Server and Oracle" in {
    norm(pageFor(SqlServerFetchDialect, ordered, Some(10), Some(20)).internals.sql) should equal(
      "SELECT id FROM t WHERE a = ? ORDER BY id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")
    norm(pageFor(SqlServerFetchDialect, unordered, Some(10), None).internals.sql) should equal(
      "SELECT id FROM t WHERE a = ? ORDER BY (SELECT NULL) OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")
    norm(pageFor(OracleFetchDialect, ordered, None, Some(5)).internals.sql) should equal(
      "SELECT id FROM t WHERE a = ? ORDER BY id DESC OFFSET ? ROWS")
  }

  "an ORDER BY" should "count only at the top level of the statement" in {
    hasTopLevelOrderBy("SELECT a FROM t ORDER BY a") should equal(true)
    hasTopLevelOrderBy("SELECT a FROM (SELECT a FROM t ORDER BY a) x") should equal(false)
    hasTopLevelOrderBy("SELECT reorder_by FROM t") should equal(false)
  }
}

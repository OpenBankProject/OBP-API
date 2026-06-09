package code.api.dynamic.entity.projection

import code.api.dynamic.entity.query._
import doobie.implicits._
import org.scalatest.{FlatSpec, Matchers}

class ProjectionSqlSpec extends FlatSpec with Matchers {

  private val cols  = Map("price" -> "c_price_x", "status" -> "c_status_y")
  private val types = Map("price" -> "numeric", "status" -> "text")
  private def columnOf(f: String): Option[String]  = cols.get(f)
  private def sqlTypeOf(f: String): Option[String] = types.get(f)

  private def sql(plan: QueryPlan): String =
    ProjectionSql.selectDataIds("de_t", plan, columnOf, sqlTypeOf).get.query[String].sql

  "ProjectionSql" should "build select data_id with where / order / limit / offset and cast operands" in {
    val s = sql(QueryPlan(
      List(Filter("price", FilterOp.Lt, List("10"))),
      List(SortKey("price", SortDirection.Desc)),
      Page(Some(40), Some(20))))
    s          should include ("SELECT data_id FROM de_t")
    s          should include ("c_price_x")
    s.toUpperCase should include ("CAST(")
    s          should include ("numeric")
    s.toUpperCase should include ("ORDER BY")
    s.toUpperCase should include ("DESC")
    s.toUpperCase should include ("LIMIT")
    s.toUpperCase should include ("OFFSET")
  }

  it should "AND multiple predicates and support in / between" in {
    val s = sql(QueryPlan(List(
      Filter("status", FilterOp.In, List("a", "b")),
      Filter("price", FilterOp.Between, List("5", "10"))), Nil, Page.empty))
    s.toUpperCase should include ("AND")
    s.toUpperCase should include ("IN (")
    s.toUpperCase should include ("BETWEEN")
  }

  it should "return None when a field is unresolved or a spatial operator is present" in {
    ProjectionSql.selectDataIds("de_t",
      QueryPlan(List(Filter("nope", FilterOp.Eq, List("1"))), Nil, Page.empty), columnOf, sqlTypeOf) shouldBe None
    ProjectionSql.selectDataIds("de_t",
      QueryPlan(List(Filter("price", FilterOp.DWithin, List("x"))), Nil, Page.empty), columnOf, sqlTypeOf) shouldBe None
  }
}

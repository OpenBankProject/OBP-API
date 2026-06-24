package code.api.dynamic.entity.query

import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import org.json4s.JsonAST.JObject
import org.scalatest.{FlatSpec, Matchers}

/**
 * Pure unit tests for the DE_indexing query core: param parser, definition-driven planner,
 * and the in-memory executor (the portable floor + oracle). No server / DB.
 */
class QuerySpec extends FlatSpec with Matchers {

  private def params(kvs: (String, String)*): Map[String, List[String]] =
    kvs.groupBy(_._1).map { case (k, vs) => k -> vs.map(_._2).toList }

  private def rec(s: String): JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[JObject]

  private val indexed: Map[String, FieldSpec] = Map(
    "price"  -> FieldSpec(DynamicEntityFieldType.number, "scalar"),
    "qty"    -> FieldSpec(DynamicEntityFieldType.integer, "scalar"),
    "status" -> FieldSpec(DynamicEntityFieldType.string, "scalar"),
    "active" -> FieldSpec(DynamicEntityFieldType.boolean, "scalar"),
    "geom"   -> FieldSpec(DynamicEntityFieldType.json, "spatial")
  )
  private val fieldTypes = indexed.mapValues(_.fieldType).toMap

  // ----- QueryParamParser -----

  "QueryParamParser" should "parse a scalar filter, sort and pagination" in {
    val Right((filters, _, sort, page)) = QueryParamParser.parse(params(
      "obp_filter[price]" -> "lt:10", "obp_sort_by" -> "price", "obp_sort_direction" -> "DESC",
      "obp_limit" -> "20", "obp_offset" -> "40"))
    filters shouldBe List(Filter("price", FilterOp.Lt, List("10")))
    sort shouldBe List(SortKey("price", SortDirection.Desc))
    page shouldBe Page(Some(40), Some(20))
  }

  it should "split in/between values on commas but keep other operands opaque" in {
    val Right((filters, _, _, _)) = QueryParamParser.parse(params(
      "obp_filter[status]" -> "in:a,b,c", "obp_filter[price]" -> "between:5,10"))
    filters.toSet shouldBe Set(
      Filter("status", FilterOp.In, List("a", "b", "c")),
      Filter("price", FilterOp.Between, List("5", "10")))
  }

  it should "reject a missing operator, an unknown operator and a bad direction/limit" in {
    QueryParamParser.parse(params("obp_filter[price]" -> "10")).isLeft shouldBe true
    QueryParamParser.parse(params("obp_filter[price]" -> "foo:10")).isLeft shouldBe true
    QueryParamParser.parse(params("obp_sort_by" -> "price", "obp_sort_direction" -> "sideways")).isLeft shouldBe true
    QueryParamParser.parse(params("obp_limit" -> "-1")).isLeft shouldBe true
    QueryParamParser.parse(params("obp_limit" -> "x")).isLeft shouldBe true
  }

  // ----- QueryPlanner -----

  "QueryPlanner" should "accept a valid plan" in {
    QueryPlanner.plan(List(Filter("price", FilterOp.Lt, List("10"))), List(SortKey("price", SortDirection.Asc)), Page.empty, indexed).isRight shouldBe true
  }

  it should "reject a non-indexed field" in {
    QueryPlanner.plan(List(Filter("colour", FilterOp.Eq, List("red"))), Nil, Page.empty, indexed).isLeft shouldBe true
  }

  it should "reject an operator illegal for the field's type" in {
    QueryPlanner.plan(List(Filter("active", FilterOp.Gt, List("true"))), Nil, Page.empty, indexed).isLeft shouldBe true // gt on boolean
    QueryPlanner.plan(List(Filter("status", FilterOp.Gt, List("x"))), Nil, Page.empty, indexed).isLeft shouldBe true     // gt on string
  }

  it should "reject a value that does not coerce to the field type" in {
    QueryPlanner.plan(List(Filter("price", FilterOp.Lt, List("abc"))), Nil, Page.empty, indexed).isLeft shouldBe true
    QueryPlanner.plan(List(Filter("qty", FilterOp.Eq, List("1.5"))), Nil, Page.empty, indexed).isLeft shouldBe true
  }

  it should "reject between with the wrong arity" in {
    QueryPlanner.plan(List(Filter("price", FilterOp.Between, List("5"))), Nil, Page.empty, indexed).isLeft shouldBe true
  }

  it should "reject sorting by a json/spatial field" in {
    QueryPlanner.plan(Nil, List(SortKey("geom", SortDirection.Asc)), Page.empty, indexed).isLeft shouldBe true
  }

  it should "allow spatial operators only on a spatial field" in {
    QueryPlanner.plan(List(Filter("geom", FilterOp.DWithin, List("13.4,52.5;100000"))), Nil, Page.empty, indexed).isRight shouldBe true
    QueryPlanner.plan(List(Filter("price", FilterOp.DWithin, List("x"))), Nil, Page.empty, indexed).isLeft shouldBe true
  }

  // ----- InMemoryQueryExecutor -----

  private val data = List(
    rec("""{"price":10,"qty":1,"status":"active"}"""),
    rec("""{"price":5,"qty":3,"status":"pending"}"""),
    rec("""{"price":20,"qty":2,"status":"active"}""")
  )

  "InMemoryQueryExecutor" should "filter numerically (not lexically)" in {
    val plan = QueryPlan(List(Filter("price", FilterOp.Lt, List("10"))), Nil, Page.empty)
    InMemoryQueryExecutor.execute(data, plan, fieldTypes).map(d => (d \ "price").values) shouldBe List(BigInt(5))
  }

  it should "sort ascending and descending by a numeric field" in {
    val asc = InMemoryQueryExecutor.execute(data, QueryPlan(Nil, List(SortKey("price", SortDirection.Asc)), Page.empty), fieldTypes)
    asc.map(d => (d \ "price").values) shouldBe List(BigInt(5), BigInt(10), BigInt(20))
    val desc = InMemoryQueryExecutor.execute(data, QueryPlan(Nil, List(SortKey("price", SortDirection.Desc)), Page.empty), fieldTypes)
    desc.map(d => (d \ "price").values) shouldBe List(BigInt(20), BigInt(10), BigInt(5))
  }

  it should "apply offset and limit after sorting" in {
    val plan = QueryPlan(Nil, List(SortKey("price", SortDirection.Asc)), Page(Some(1), Some(1)))
    InMemoryQueryExecutor.execute(data, plan, fieldTypes).map(d => (d \ "price").values) shouldBe List(BigInt(10))
  }

  it should "support eq, in and between" in {
    InMemoryQueryExecutor.execute(data, QueryPlan(List(Filter("status", FilterOp.Eq, List("active"))), Nil, Page.empty), fieldTypes).size shouldBe 2
    InMemoryQueryExecutor.execute(data, QueryPlan(List(Filter("status", FilterOp.In, List("pending", "x"))), Nil, Page.empty), fieldTypes).size shouldBe 1
    InMemoryQueryExecutor.execute(data, QueryPlan(List(Filter("price", FilterOp.Between, List("6", "20"))), Nil, Page.empty), fieldTypes).size shouldBe 2
  }

  it should "exclude records whose field is missing or not coercible" in {
    val withMissing = data :+ rec("""{"qty":9,"status":"active"}""") // no price
    InMemoryQueryExecutor.execute(withMissing, QueryPlan(List(Filter("price", FilterOp.Ge, List("0"))), Nil, Page.empty), fieldTypes).size shouldBe 3
  }
}

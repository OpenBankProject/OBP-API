package code.api.dynamic.entity.query

import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import org.json4s.JsonAST.JObject
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Pure unit tests for the one-hop join Feature(obp_exists / obp_not_exists) and the value-absence
 * operators (is_null / not_set): query-param parsing, definition-driven edge resolution in the planner,
 * and in-memory nullary-op evaluation. No server / DB — the EXISTS/NOT EXISTS SQL itself is exercised by
 * the Postgres-gated integration suite. See ideas/DYNAMIC_ENTITY_JOIN_QUERIES.md.
 *
 * Domain: parent `Partner` (tier), child `Contract` (active, status, partner_id : reference:Partner).
 */
class JoinQuerySpec extends AnyFlatSpec with Matchers {

  private def params(kvs: (String, String)*): Map[String, List[String]] =
    kvs.groupBy(_._1).map { case (k, vs) => k -> vs.map(_._2).toList }

  private def rec(s: String): JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[JObject]

  // ----- planner fixtures -----

  private val partnerIndexed: Map[String, FieldSpec] = Map("tier" -> FieldSpec(DynamicEntityFieldType.string, "scalar"))

  // child Contract with a single edge to Partner (partner_id)
  private val contractSingleEdge = JoinTargetInfo(
    indexedFields = Map(
      "active"     -> FieldSpec(DynamicEntityFieldType.boolean, "scalar"),
      "status"     -> FieldSpec(DynamicEntityFieldType.string, "scalar"),
      "partner_id" -> FieldSpec(DynamicEntityFieldType.reference, "scalar")),
    referenceFields = Map("partner_id" -> "Partner"))

  // child Contract with two edges to Partner (buyer_id, seller_id)
  private val contractTwoEdges = contractSingleEdge.copy(
    indexedFields = contractSingleEdge.indexedFields - "partner_id"
      + ("buyer_id" -> FieldSpec(DynamicEntityFieldType.reference, "scalar"))
      + ("seller_id" -> FieldSpec(DynamicEntityFieldType.reference, "scalar")),
    referenceFields = Map("buyer_id" -> "Partner", "seller_id" -> "Partner"))

  private def childInfoOf(map: Map[String, JoinTargetInfo])(name: String): Option[JoinTargetInfo] = map.get(name)

  private def planJoin(raw: RawJoin, child: JoinTargetInfo,
                       parentRefs: Map[String, String] = Map.empty): Either[QueryError, QueryPlan] =
    QueryPlanner.plan(Nil, List(raw), Nil, Page.empty, "Partner",
      partnerIndexed, parentRefs, childInfoOf(Map("Contract" -> child)))

  // ----- parser: join clauses -----

  "QueryParamParser" should "parse obp_exists with no predicate (has-any)" in {
    val Right((_, joins, _, _)) = QueryParamParser.parse(params("obp_exists[Contract]" -> ""))
    joins shouldBe List(RawJoin(Quantifier.Exists, "Contract", None, Nil))
  }

  it should "parse obp_not_exists distinctly from obp_exists" in {
    val Right((_, joins, _, _)) = QueryParamParser.parse(params("obp_not_exists[Contract]" -> ""))
    joins shouldBe List(RawJoin(Quantifier.NotExists, "Contract", None, Nil))
  }

  it should "parse a nested predicate reusing the filter grammar" in {
    val Right((_, joins, _, _)) = QueryParamParser.parse(params("obp_exists[Contract]" -> "filter[active]=eq:true"))
    joins shouldBe List(RawJoin(Quantifier.Exists, "Contract", None, List(Filter("active", FilterOp.Eq, List("true")))))
  }

  it should "parse via: plus a nested predicate (semicolon-separated)" in {
    val Right((_, joins, _, _)) = QueryParamParser.parse(params("obp_exists[Contract]" -> "via:buyer_id;filter[status]=eq:signed"))
    joins shouldBe List(RawJoin(Quantifier.Exists, "Contract", Some("buyer_id"), List(Filter("status", FilterOp.Eq, List("signed")))))
  }

  it should "reject an unrecognised token inside a join clause" in {
    QueryParamParser.parse(params("obp_exists[Contract]" -> "bogus")).isLeft shouldBe true
  }

  // ----- parser: nullary value-absence operators -----

  it should "parse is_null / not_set with no operand" in {
    val Right((f1, _, _, _)) = QueryParamParser.parse(params("obp_filter[tier]" -> "is_null"))
    f1 shouldBe List(Filter("tier", FilterOp.IsNull, Nil))
    val Right((f2, _, _, _)) = QueryParamParser.parse(params("obp_filter[tier]" -> "not_set"))
    f2 shouldBe List(Filter("tier", FilterOp.NotSet, Nil))
  }

  // ----- planner: edge resolution -----

  "QueryPlanner join resolution" should "infer the only reference edge (child -> parent)" in {
    val plan = planJoin(RawJoin(Quantifier.Exists, "Contract", None, Nil), contractSingleEdge)
    plan.isRight shouldBe true
    plan.toOption.get.joins shouldBe List(JoinClause(Quantifier.Exists, "Contract", "partner_id", onChild = true, Nil))
  }

  it should "reject an ambiguous join when two edges exist and no via is given" in {
    planJoin(RawJoin(Quantifier.Exists, "Contract", None, Nil), contractTwoEdges).isLeft shouldBe true
  }

  it should "resolve the edge when via picks one of several candidates" in {
    val plan = planJoin(RawJoin(Quantifier.Exists, "Contract", Some("seller_id"), Nil), contractTwoEdges)
    plan.toOption.get.joins.head.linkField shouldBe "seller_id"
  }

  it should "reject a via that is not a real reference edge" in {
    planJoin(RawJoin(Quantifier.Exists, "Contract", Some("not_a_ref"), Nil), contractTwoEdges).isLeft shouldBe true
  }

  it should "reject a join target that has no reference link to the queried entity" in {
    val unrelated = JoinTargetInfo(Map("active" -> FieldSpec(DynamicEntityFieldType.boolean, "scalar")), Map.empty)
    planJoin(RawJoin(Quantifier.Exists, "Contract", None, Nil), unrelated).isLeft shouldBe true
  }

  // ----- planner: precise rejections when the reference exists but is not indexed -----

  // child Contract whose partner_id is typed reference:Partner but NOT declared indexed
  private val contractUnindexedEdge = JoinTargetInfo(
    indexedFields = Map("active" -> FieldSpec(DynamicEntityFieldType.boolean, "scalar")),
    referenceFields = Map.empty,
    unindexedReferenceFields = Map("partner_id" -> "Partner"))

  it should "name the unindexed child reference field instead of claiming no reference is declared" in {
    val Left(err) = planJoin(RawJoin(Quantifier.Exists, "Contract", None, Nil), contractUnindexedEdge)
    err.message should include ("via 'partner_id'")
    err.message should include ("'partner_id' on 'Contract' is typed 'reference:Partner'")
    err.message should include ("not declared \"indexed\": true")
    err.message should not include ("no declared reference")
  }

  it should "name the unindexed child reference field when via: points at it" in {
    val Left(err) = planJoin(RawJoin(Quantifier.Exists, "Contract", Some("partner_id"), Nil), contractUnindexedEdge)
    err.message should include ("'partner_id' on 'Contract'")
    err.message should include ("not declared \"indexed\": true")
  }

  it should "name the unindexed parent reference field for a parent -> child edge" in {
    val childNoEdges = JoinTargetInfo(Map("active" -> FieldSpec(DynamicEntityFieldType.boolean, "scalar")), Map.empty)
    val Left(err) = QueryPlanner.plan(Nil, List(RawJoin(Quantifier.NotExists, "Contract", None, Nil)), Nil, Page.empty,
      "Partner", partnerIndexed, Map.empty, childInfoOf(Map("Contract" -> childNoEdges)),
      parentUnindexedReferenceFields = Map("favourite_contract" -> "Contract"))
    err.message should include ("'favourite_contract' on 'Partner' is typed 'reference:Contract'")
    err.message should include ("not declared \"indexed\": true")
  }

  it should "list every unindexed reference field when more than one could be the edge" in {
    val twoUnindexed = contractUnindexedEdge.copy(unindexedReferenceFields = Map("buyer_id" -> "Partner", "seller_id" -> "Partner"))
    val Left(err) = planJoin(RawJoin(Quantifier.Exists, "Contract", None, Nil), twoUnindexed)
    err.message should include ("'buyer_id' on 'Contract'")
    err.message should include ("'seller_id' on 'Contract'")
    err.message should include ("via:<field>")
  }

  it should "reject a join from a parent that has no indexed fields at all, saying so" in {
    val Left(err) = QueryPlanner.plan(Nil, List(RawJoin(Quantifier.Exists, "Contract", None, Nil)), Nil, Page.empty,
      "Partner", Map.empty, Map.empty, childInfoOf(Map("Contract" -> contractSingleEdge)))
    err.message should include ("Cannot join from 'Partner'")
    err.message should include ("no SQL projection")
  }

  it should "keep the generic message when no reference of any kind links the entities" in {
    val unrelated = JoinTargetInfo(Map("active" -> FieldSpec(DynamicEntityFieldType.boolean, "scalar")), Map.empty)
    val Left(err) = planJoin(RawJoin(Quantifier.Exists, "Contract", None, Nil), unrelated)
    err.message should include ("no declared reference")
  }

  it should "reject a join onto a non-existent entity" in {
    QueryPlanner.plan(Nil, List(RawJoin(Quantifier.Exists, "Nope", None, Nil)), Nil, Page.empty, "Partner",
      partnerIndexed, Map.empty, _ => None).isLeft shouldBe true
  }

  it should "resolve a parent -> child edge (link on the parent)" in {
    val plan = QueryPlanner.plan(Nil, List(RawJoin(Quantifier.NotExists, "Contract", None, Nil)), Nil, Page.empty,
      "Partner", partnerIndexed, Map("favourite_contract" -> "Contract"),
      childInfoOf(Map("Contract" -> JoinTargetInfo(Map.empty, Map.empty))))
    plan.toOption.get.joins shouldBe List(JoinClause(Quantifier.NotExists, "Contract", "favourite_contract", onChild = false, Nil))
  }

  // ----- planner: nested predicate validation against the CHILD -----

  it should "validate the nested predicate against the child's indexed fields" in {
    planJoin(RawJoin(Quantifier.Exists, "Contract", None, List(Filter("active", FilterOp.Eq, List("true")))), contractSingleEdge).isRight shouldBe true
    // 'colour' is not indexed on the child
    planJoin(RawJoin(Quantifier.Exists, "Contract", None, List(Filter("colour", FilterOp.Eq, List("x")))), contractSingleEdge).isLeft shouldBe true
    // gt is illegal on a boolean child field
    planJoin(RawJoin(Quantifier.Exists, "Contract", None, List(Filter("active", FilterOp.Gt, List("true")))), contractSingleEdge).isLeft shouldBe true
  }

  it should "accept a nullary op (no operand) in the nested predicate" in {
    planJoin(RawJoin(Quantifier.Exists, "Contract", None, List(Filter("status", FilterOp.IsNull, Nil))), contractSingleEdge).isRight shouldBe true
  }

  it should "reject a nullary op carrying an operand" in {
    planJoin(RawJoin(Quantifier.Exists, "Contract", None, List(Filter("status", FilterOp.IsNull, List("x")))), contractSingleEdge).isLeft shouldBe true
  }

  // ----- in-memory: is_null / not_set evaluation (aliases: match absent field or JSON null) -----

  "InMemoryQueryExecutor nullary ops" should "match a missing field and an explicit null, but not a present value" in {
    val data = List(rec("""{"tier":"gold"}"""), rec("""{"tier":null}"""), rec("""{"other":1}"""))
    val fieldTypes = Map("tier" -> DynamicEntityFieldType.string)
    for (op <- List(FilterOp.IsNull, FilterOp.NotSet)) {
      val out = InMemoryQueryExecutor.execute(data, QueryPlan(List(Filter("tier", op, Nil)), Nil, Page.empty), fieldTypes)
      out.size shouldBe 2 // the null one and the missing one; not the "gold" one
    }
  }
}

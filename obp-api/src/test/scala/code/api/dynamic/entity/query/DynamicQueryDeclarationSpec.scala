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

import org.json4s.JsonAST.{JBool, JString}
import org.scalatest.{FlatSpec, Matchers}

/**
 * Pure unit tests for reading a Dynamic Query body: the shape it accepts, its defaults, and the
 * messages for a malformed body. Checking a body against entity definitions, and running it, is
 * covered by code.api.v4_0_0.DynamicQueryTest.
 */
class DynamicQueryDeclarationSpec extends FlatSpec with Matchers {

  private def parsed(body: String): DynamicQueryDeclaration =
    DynamicQueryDeclaration.parse(body).fold(e => fail(e.message), identity)
  private def errorOf(body: String): String =
    DynamicQueryDeclaration.parse(body).left.toOption.map(_.message).getOrElse(fail(s"expected an error for $body"))

  "DynamicQueryDeclaration.parse" should "read every part of a full body" in {
    val declaration = parsed(
      """{
        |  "from": "activity",
        |  "select": ["activity_id", "name"],
        |  "where": { "city": "eq:Berlin", "status": ["ne:closed", "is_null"] },
        |  "join": [
        |    { "entity": "operator", "on": "operator_id", "fields": { "operator_legal_name": "legal_name" } },
        |    { "entity": "certificate", "on": "activity_id", "cardinality": "exists", "as": "certified",
        |      "where": { "status": "in:valid,renewed" }, "true_value": "yes", "false_value": false }
        |  ],
        |  "envelope": { "rows": "activities", "count": "count" }
        |}""".stripMargin)
    declaration.from shouldBe "activity"
    declaration.select shouldBe Some(List("activity_id", "name"))
    declaration.where shouldBe List(
      Filter("city", FilterOp.Eq, List("Berlin")), Filter("status", FilterOp.Ne, List("closed")), Filter("status", FilterOp.IsNull, Nil))
    declaration.joins.head shouldBe JoinRequest("operator", "operator_id", fields = List("operator_legal_name" -> "legal_name"))
    val second = declaration.joins(1)
    (second.cardinality, second.as, second.where) shouldBe ((Some("exists"), Some("certified"), List(Filter("status", FilterOp.In, List("valid", "renewed")))))
    (second.trueValue, second.falseValue) shouldBe ((Some(JString("yes")), Some(JBool(false))))
    declaration.envelope shouldBe DynamicQueryEnvelope("activities", Some("count"))
  }

  it should "default to all fields, no filters, no joins, and the entity's list name" in {
    val declaration = parsed("""{ "from": "ActivityRecord" }""")
    (declaration.select, declaration.where, declaration.joins) shouldBe ((None, Nil, Nil))
    declaration.envelope shouldBe DynamicQueryEnvelope("activity_record_list", None)
  }

  it should "keep the order of a join's fields" in {
    parsed("""{ "from": "a", "join": [ { "entity": "b", "on": "b_id", "fields": { "z": "f1", "a": "f2", "m": "f3" } } ] }""")
      .joins.head.fields.map(_._1) shouldBe List("z", "a", "m")
  }

  it should "reject a body that is not a JSON object, or lacks 'from'" in {
    errorOf("not json") should include("must be a JSON object")
    errorOf("""["from"]""") should include("must be a JSON object")
    errorOf("""{ "select": ["a"] }""") should include("needs 'from'")
  }

  it should "reject unknown keys, so a misspelt key is not silently ignored" in {
    errorOf("""{ "from": "a", "joins": [] }""") should include("unknown key 'joins'")
    errorOf("""{ "from": "a", "join": [ { "entity": "b", "on": "b_id", "feilds": {} } ] }""") should include("Join 1 has an unknown key 'feilds'")
    errorOf("""{ "from": "a", "envelope": { "rows": "x", "total": "n" } }""") should include("unknown key 'total'")
  }

  it should "reject malformed parts with the place they are in" in {
    errorOf("""{ "from": "a", "select": [] }""") should include("'select' must be a non-empty list")
    errorOf("""{ "from": "a", "where": { "city": 3 } }""") should include("the filter on 'city' must be a string")
    errorOf("""{ "from": "a", "where": { "city": "Berlin" } }""") should include("<operator>:<value>")
    errorOf("""{ "from": "a", "where": { "city": "near:Berlin" } }""") should include("Unknown filter operator 'near'")
    errorOf("""{ "from": "a", "join": {} }""") should include("'join' must be a list")
    errorOf("""{ "from": "a", "join": [ { "on": "b_id" } ] }""") should include("Join 1 needs 'entity'")
    errorOf("""{ "from": "a", "join": [ { "entity": "b", "on": "b_id", "fields": { "x": 1 } } ] }""") should include("'x' does not")
    errorOf("""{ "from": "a", "envelope": { "rows": "n", "count": "n" } }""") should include("one name for both")
  }
}

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

import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import org.json4s.JsonAST._
import org.scalatest.{FlatSpec, Matchers}

/**
 * Pure unit tests for joins: the planner's checks, the direction it infers, and the merge of records
 * already read into a page. No server and no database; reading the other records, with personal
 * records, row-level access and the projection, is covered by
 * code.api.v6_0_0.DynamicEntityJoinPlanIntegrationTest.
 *
 * Domain:
 *  - `activity` (name, city, operator_id : reference:operator);
 *  - `operator` (legal_name, country, status);
 *  - `certificate` (number, status, issue_date, secret, activity_id : reference:activity indexed,
 *    loose_activity_id : reference:activity not indexed);
 *  - `employee` (name, manager_id : reference:employee indexed), a self-reference.
 */
class JoinSpec extends FlatSpec with Matchers {

  import DynamicEntityFieldType._

  private val activity = JoinEntityInfo(Set("name", "city", "operator_id"), "activity_id",
    Map("operator_id" -> "operator"), Map("name" -> string, "city" -> string, "operator_id" -> reference))
  private val operator = JoinEntityInfo(Set("legal_name", "country", "status"), "operator_id", Map.empty,
    Map("legal_name" -> string, "country" -> string, "status" -> string))
  private val certificate = JoinEntityInfo(
    Set("number", "status", "issue_date", "secret", "activity_id", "loose_activity_id"), "certificate_id",
    Map("activity_id" -> "activity", "loose_activity_id" -> "activity"),
    Map("number" -> string, "status" -> string, "issue_date" -> DATE_WITH_DAY, "secret" -> string,
      "activity_id" -> reference, "loose_activity_id" -> reference),
    Set("activity_id"))
  private val employee = JoinEntityInfo(Set("name", "manager_id"), "employee_id", Map("manager_id" -> "employee"),
    Map("name" -> string, "manager_id" -> reference), Set("manager_id"))
  private val entities = Map("activity" -> activity, "operator" -> operator, "certificate" -> certificate, "employee" -> employee)

  private def planOf(parent: String, mayReadEntity: String => Boolean, mayReadField: (String, String) => Boolean)(requests: JoinRequest*) =
    JoinPlanner.plan(parent, entities(parent), requests.toList, entities.get, mayReadEntity, mayReadField)
  private def plan(requests: JoinRequest*) = planOf("activity", _ => true, (_, _) => true)(requests: _*)
  private def errorOf(result: Either[QueryError, List[Join]]): String =
    result.left.toOption.map(_.message).getOrElse(fail(s"expected an error, got $result"))
  private def planned(requests: JoinRequest*): List[Join] = plan(requests: _*).fold(e => fail(e.message), identity)

  private def operatorJoin(fields: (String, String)*) = JoinRequest("operator", "operator_id", fields = fields.toList)
  private def latestCertificate(fields: (String, String)*) =
    JoinRequest("certificate", "activity_id", cardinality = Some("at_most_one"), pick = Some("latest_by:issue_date"), fields = fields.toList)

  // ----- direction -----

  "JoinPlanner" should "infer forward when the parent holds the reference, at_most_one by default" in {
    val join = planned(operatorJoin("operator_legal_name" -> "legal_name")).head
    (join.direction, join.cardinality) shouldBe ((JoinDirection.Forward, Cardinality.AtMostOne))
  }

  it should "infer reverse when the other entity holds the reference" in {
    planned(latestCertificate("n" -> "number")).head.direction shouldBe JoinDirection.Reverse
  }

  it should "require a direction for a self-reference, and honour it" in {
    def employeePlan(requests: JoinRequest*) = planOf("employee", _ => true, (_, _) => true)(requests: _*)
    errorOf(employeePlan(JoinRequest("employee", "manager_id", fields = List("manager_name" -> "name")))) should include("Say which with \"direction\"")
    employeePlan(
      JoinRequest("employee", "manager_id", direction = Some("forward"), fields = List("manager_name" -> "name")),
      JoinRequest("employee", "manager_id", direction = Some("reverse"), cardinality = Some("many"), as = Some("reports"), fields = List("name" -> "name"))
    ).map(_.map(_.direction)) shouldBe Right(List(JoinDirection.Forward, JoinDirection.Reverse))
  }

  it should "reject a direction the definitions do not support, or an unknown one" in {
    errorOf(plan(operatorJoin("x" -> "legal_name").copy(direction = Some("reverse")))) should include("direction is reverse, but 'operator' has no field 'operator_id' typed 'reference:activity'")
    errorOf(plan(operatorJoin("x" -> "legal_name").copy(direction = Some("sideways")))) should include("direction must be 'forward' or 'reverse'")
  }

  it should "reject an 'on' field that links the two in neither direction" in {
    errorOf(plan(JoinRequest("operator", "city", fields = List("x" -> "legal_name")))) should include(
      "'activity' has no field 'city' typed 'reference:operator' and 'operator' has no field 'city' typed 'reference:activity'")
  }

  // ----- other checks -----

  it should "reject an unknown entity, or one the caller may not read" in {
    errorOf(plan(JoinRequest("permit", "activity_id", cardinality = Some("exists"), as = Some("x")))) should include("no Dynamic Entity 'permit'")
    errorOf(planOf("activity", _ != "operator", (_, _) => true)(operatorJoin("x" -> "legal_name"))) should include("you may not read 'operator'")
  }

  it should "require a reverse join's link field to be indexed, but not a forward one's" in {
    errorOf(plan(JoinRequest("certificate", "loose_activity_id", cardinality = Some("exists"), as = Some("x")))) should include("must be declared \"indexed\": true")
    plan(operatorJoin("x" -> "legal_name")).isRight shouldBe true // activity.operator_id is not indexed
  }

  it should "fit the cardinality to the direction" in {
    errorOf(plan(operatorJoin("x" -> "legal_name").copy(cardinality = Some("many"), as = Some("y")))) should include("cannot be 'many'")
    errorOf(plan(JoinRequest("certificate", "activity_id", as = Some("x")))) should include("needs \"cardinality\"")
    errorOf(plan(JoinRequest("certificate", "activity_id", cardinality = Some("several"), as = Some("x")))) should include("cardinality must be one of")
    plan(operatorJoin().copy(cardinality = Some("exists"), as = Some("has_operator"))).isRight shouldBe true
  }

  it should "require a pick for a reverse at_most_one, and refuse one for a forward join" in {
    errorOf(plan(latestCertificate("n" -> "number").copy(pick = None))) should include("at_most_one needs 'pick'")
    errorOf(plan(operatorJoin("x" -> "legal_name").copy(pick = Some("latest_by:legal_name")))) should include("'pick' is not used by a join that can find at most one record")
    errorOf(plan(latestCertificate("n" -> "number").copy(pick = Some("newest:issue_date")))) should include("must be 'latest_by:<field>' or 'earliest_by:<field>'")
    errorOf(plan(latestCertificate("n" -> "number").copy(pick = Some("latest_by:missing")))) should include("names 'missing'")
  }

  it should "require the shape each cardinality needs" in {
    errorOf(plan(operatorJoin())) should include("at_most_one needs 'fields'")
    errorOf(plan(operatorJoin("x" -> "legal_name").copy(as = Some("y")))) should include("'as' is not used with cardinality at_most_one")
    errorOf(plan(JoinRequest("certificate", "activity_id", cardinality = Some("many"), fields = List("n" -> "number")))) should include("many needs 'as'")
    errorOf(plan(JoinRequest("certificate", "activity_id", cardinality = Some("many"), as = Some("x")))) should include("many needs 'fields'")
    errorOf(plan(JoinRequest("certificate", "activity_id", cardinality = Some("exists"), as = Some("x"), fields = List("n" -> "number")))) should include("'fields' is not used")
    errorOf(plan(operatorJoin("x" -> "no_such_field"))) should include("'operator' has no field 'no_such_field'")
  }

  it should "reject a result name taken by the parent, an earlier join, used twice, or unusable" in {
    errorOf(plan(operatorJoin("name" -> "legal_name"))) should include("'name' is already a field of the result")
    errorOf(plan(operatorJoin("activity_id" -> "legal_name"))) should include("'activity_id' is already a field of the result")
    errorOf(plan(operatorJoin("x" -> "legal_name"), latestCertificate("x" -> "number"))) should include("'x' is already a field of the result")
    errorOf(plan(operatorJoin("x" -> "legal_name", "x" -> "country"))) should include("a result name is used twice")
    errorOf(plan(operatorJoin("1st" -> "legal_name"))) should include("must be a field name")
  }

  it should "validate where filters with the list endpoint's rules, in either direction" in {
    def whereOn(filter: Filter) = JoinRequest("certificate", "activity_id", cardinality = Some("exists"), as = Some("x"), where = List(filter))
    errorOf(plan(whereOn(Filter("issue_date", FilterOp.Eq, List("not-a-date"))))) should include("not a valid 'DATE_WITH_DAY'")
    errorOf(plan(whereOn(Filter("status", FilterOp.Lt, List("a"))))) should include("Operator 'lt' is not valid")
    errorOf(plan(whereOn(Filter("nope", FilterOp.Eq, List("a"))))) should include("names 'nope'")
    plan(operatorJoin("x" -> "legal_name").copy(where = List(Filter("status", FilterOp.Eq, List("active"))))).isRight shouldBe true
  }

  it should "refuse to filter or order by a field the caller may not read" in {
    def noSecret(request: JoinRequest) = planOf("activity", _ => true, (_, field) => field != "secret")(request)
    errorOf(noSecret(JoinRequest("certificate", "activity_id", cardinality = Some("exists"), as = Some("x"),
      where = List(Filter("secret", FilterOp.Eq, List("a")))))) should include("you may not read 'secret'")
    errorOf(noSecret(latestCertificate("n" -> "number").copy(pick = Some("latest_by:secret")))) should include("you may not read 'secret'")
  }

  // ----- merge -----

  private def cert(id: String, number: String, status: String, issueDate: Option[String], activityId: String): JObject =
    JObject(List(JField("certificate_id", JString(id)), JField("number", JString(number)), JField("status", JString(status)),
      JField("activity_id", JString(activityId))) ++ issueDate.map(d => JField("issue_date", JString(d))).toList)
  private def activityRecord(id: String, operatorId: JValue): JObject =
    JObject(JField("activity_id", JString(id)), JField("operator_id", operatorId))

  private val linked: RecordJoiner.LinkedRecords = Map(
    RecordJoiner.Link("operator", "operator_id", JoinDirection.Forward) -> Map(
      "op-1" -> List(JObject(JField("operator_id", JString("op-1")), JField("legal_name", JString("Acme Ltd")), JField("country", JString("DE")), JField("status", JString("active")))),
      "op-2" -> List(JObject(JField("operator_id", JString("op-2")), JField("legal_name", JString("No Country Ltd")), JField("status", JString("closed"))))),
    RecordJoiner.Link("certificate", "activity_id", JoinDirection.Reverse) -> Map("a1" -> List(
      cert("c3", "C-3", "valid", None, "a1"),
      cert("c2", "C-2", "revoked", Some("2026-06-01"), "a1"),
      cert("c1", "C-1", "valid", Some("2026-01-01"), "a1"),
      cert("c0", "C-0", "valid", Some("2026-06-01"), "a1"))))

  private val page = List(
    activityRecord("a1", JString("op-1")),
    activityRecord("a2", JString("op-2")),
    activityRecord("a3", JString("op-missing")),
    activityRecord("a4", JNull))

  private def mergeWith(readable: (String, String) => Boolean)(requests: JoinRequest*): List[JObject] =
    RecordJoiner.merge(page, planned(requests: _*), "activity_id", linked, readable)
  private def merge(requests: JoinRequest*): List[JObject] = mergeWith((_, _) => true)(requests: _*)

  "RecordJoiner.merge" should "copy a forward join's fields, null when there is no record or no field" in {
    val merged = merge(operatorJoin("operator_legal_name" -> "legal_name", "operator_country" -> "country"))
    merged.map(_ \ "operator_legal_name") shouldBe List(JString("Acme Ltd"), JString("No Country Ltd"), JNull, JNull)
    merged.map(_ \ "operator_country") shouldBe List(JString("DE"), JNull, JNull, JNull)
    merged.head.obj.map(_._1) shouldBe List("activity_id", "operator_id", "operator_legal_name", "operator_country")
  }

  it should "apply where to a forward join, and answer exists for one" in {
    merge(operatorJoin("active_operator" -> "legal_name").copy(where = List(Filter("status", FilterOp.Eq, List("active")))))
      .map(_ \ "active_operator") shouldBe List(JString("Acme Ltd"), JNull, JNull, JNull)
    merge(operatorJoin().copy(cardinality = Some("exists"), as = Some("has_operator")))
      .map(_ \ "has_operator") shouldBe List(JBool(true), JBool(true), JBool(false), JBool(false))
  }

  it should "pick the latest in a reverse join, breaking a tie by record id, never choosing a record without the date" in {
    merge(latestCertificate("n" -> "number")).map(_ \ "n") shouldBe List(JString("C-0"), JNull, JNull, JNull)
  }

  it should "pick the earliest, still putting a record without the date last" in {
    merge(latestCertificate("n" -> "number").copy(pick = Some("earliest_by:issue_date"))).head \ "n" shouldBe JString("C-1")
  }

  it should "apply where before the pick" in {
    merge(latestCertificate("n" -> "number").copy(where = List(Filter("status", FilterOp.Eq, List("revoked"))))).head \ "n" shouldBe JString("C-2")
  }

  it should "list many in order, by record id without one, and give an empty array to a record with none" in {
    val ordered = merge(JoinRequest("certificate", "activity_id", cardinality = Some("many"), as = Some("all"),
      order = Some("earliest_by:issue_date"), fields = List("n" -> "number")))
    ordered.head \ "all" \ "n" shouldBe JArray(List(JString("C-1"), JString("C-0"), JString("C-2"), JString("C-3")))
    ordered(1) \ "all" shouldBe JArray(Nil)
    merge(JoinRequest("certificate", "activity_id", cardinality = Some("many"), as = Some("all"), fields = List("n" -> "number")))
      .head \ "all" \ "n" shouldBe JArray(List(JString("C-0"), JString("C-1"), JString("C-2"), JString("C-3")))
  }

  it should "give exists its values, true and false by default" in {
    merge(JoinRequest("certificate", "activity_id", cardinality = Some("exists"), as = Some("certified")))
      .map(_ \ "certified") shouldBe List(JBool(true), JBool(false), JBool(false), JBool(false))
    merge(JoinRequest("certificate", "activity_id", cardinality = Some("exists"), as = Some("state"),
      where = List(Filter("status", FilterOp.Eq, List("revoked"))), trueValue = Some(JString("revoked")), falseValue = Some(JString("clean"))))
      .map(_ \ "state") shouldBe List(JString("revoked"), JString("clean"), JString("clean"), JString("clean"))
  }

  it should "give null for a copied field the caller may not read, without hiding the others" in {
    val merged = mergeWith((_, field) => field != "country")(operatorJoin("name1" -> "legal_name", "c" -> "country"))
    (merged.head \ "name1", merged.head \ "c") shouldBe ((JString("Acme Ltd"), JNull))
  }

  it should "never add or remove a record, or change their order" in {
    merge(operatorJoin("x" -> "legal_name"), latestCertificate("n" -> "number")).map(_ \ "activity_id") shouldBe
      List(JString("a1"), JString("a2"), JString("a3"), JString("a4"))
  }
}

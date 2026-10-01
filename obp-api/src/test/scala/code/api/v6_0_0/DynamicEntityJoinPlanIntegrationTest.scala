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

package code.api.v6_0_0

import code.DynamicData.{DynamicDataAccessProvider, DynamicDataProvider}
import code.api.dynamic.entity.helper.DynamicEntitySpace
import code.api.dynamic.entity.projection.{IndexingCapabilities, ProjectionProvisioner}
import code.api.dynamic.entity.query._
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.entitlement.Entitlement
import cats.effect.unsafe.implicits.global
import net.liftweb.util.StringHelpers
import org.json4s.JsonAST._

/**
 * This suite checks the join machinery of Dynamic Views against the real record store: joins planned
 * from stored definitions, in both directions (forward: the record's reference field names the other
 * record; reverse: the other records' reference field names this one), applied with the access rules:
 * shared records only, the row-level access list, and read-restricted fields.
 *
 * Neither direction needs the PostgreSQL projection, so this runs on any database. When the projection
 * is enabled, the reverse scenario also provisions it and checks that the indexed lookup gives the same
 * result as the in-memory one. Entity names carry a per-run suffix so reruns never see stale data. The
 * planner's checks and the merge on their own are covered by code.api.dynamic.entity.query.JoinSpec.
 */
class DynamicEntityJoinPlanIntegrationTest extends V600ServerSetup {

  private val owner = "join-plan-owner"
  private val userA = "join-plan-user-a"
  private val userB = "join-plan-user-b"
  private def suffix(): String = java.util.UUID.randomUUID().toString.take(8).replace("-", "")
  private def idField(entity: String): String = StringHelpers.snakify(entity) + "_id"

  private def createDef(entity: String, propsJson: String, rowLevel: Boolean = false): Unit =
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(
      DynamicEntityCommons(entity, s"""{"$entity":{"properties":$propsJson}}""", None, owner, None,
        hasPersonalEntity = true, hasCommunityAccess = true, useRowLevelAccess = rowLevel)
    ).openOrThrowException(s"failed to create definition for $entity")

  /** Save a record with an explicit id, shared unless `personalOwner` is given. Returns the id. */
  private def saveRec(entity: String, personalOwner: Option[String], fields: (String, JValue)*): String = {
    val id = java.util.UUID.randomUUID().toString
    val body = JObject(JField(idField(entity), JString(id)) :: fields.toList.map { case (k, v) => JField(k, v) })
    DynamicDataProvider.connectorMethodProvider.vend.save(None, entity, body, personalOwner.orElse(Some(owner)), personalOwner.isDefined)
      .openOrThrowException(s"failed to save $entity record")
    id
  }

  private def grantRead(entity: String, recordId: String, userId: String): Unit =
    DynamicDataAccessProvider.provider.vend.grant(bankId = None, entityName = entity, dynamicDataId = recordId,
      userId = userId, canRead = true, canUpdate = false, canDelete = false, canGrant = false, grantedBy = owner)

  /** The parent entity's shared records, ordered by `name` so assertions can be positional. */
  private def page(entity: String): List[JObject] =
    DynamicDataProvider.connectorMethodProvider.vend.getAllDataJson(None, entity, None, isPersonalEntity = false)
      .sortBy(r => (r \ "name").values.toString)

  private def column(records: List[JObject], field: String): List[JValue] = records.map(_ \ field)

  private def plan(parent: String, joins: List[JoinRequest], caller: Option[String]): JoinPlan =
    JoinPlanner.planFor(None, parent, joins, caller, _ => true).fold(e => fail(e.message), identity)

  feature("Forward joins: the record's reference field names the other record") {
    scenario("shared, personal, dangling and missing references, a row-level target, and a read-restricted field") {
      val sfx = suffix()
      val Operator = s"Operator$sfx"; val Licence = s"Licence$sfx"; val Activity = s"Activity$sfx"
      Given("operators (one field read-restricted), row-level licences, and activities referencing both")
      createDef(Operator, s"""{"${idField(Operator)}":{"type":"string"},"legal_name":{"type":"string"},"tax_number":{"type":"string","read_role_required":true}}""")
      createDef(Licence, s"""{"${idField(Licence)}":{"type":"string"},"number":{"type":"string"}}""", rowLevel = true)
      createDef(Activity, s"""{"${idField(Activity)}":{"type":"string"},"name":{"type":"string"},"city":{"type":"string"},""" +
        s""""operator_id":{"type":"reference:$Operator"},"licence_id":{"type":"reference:$Licence"}}""")

      val acme = saveRec(Operator, None, "legal_name" -> JString("Acme Ltd"), "tax_number" -> JString("DE-123"))
      val privateOperator = saveRec(Operator, Some(userB), "legal_name" -> JString("Someone's own operator"))
      val licenceForA = saveRec(Licence, None, "number" -> JString("L-1"))
      grantRead(Licence, licenceForA, userA)

      saveRec(Activity, None, "name" -> JString("1 acme"), "operator_id" -> JString(acme), "licence_id" -> JString(licenceForA))
      saveRec(Activity, None, "name" -> JString("2 private operator"), "operator_id" -> JString(privateOperator))
      saveRec(Activity, None, "name" -> JString("3 dangling"), "operator_id" -> JString("no-such-operator"))
      saveRec(Activity, None, "name" -> JString("4 no reference"))

      val joins = List(
        JoinRequest(Operator, "operator_id", fields = List("operator_legal_name" -> "legal_name", "operator_tax_number" -> "tax_number")),
        JoinRequest(Licence, "licence_id", fields = List("licence_number" -> "number")))

      Then("a planning mistake is reported against the stored definition")
      JoinPlanner.planFor(None, Activity, List(JoinRequest(Operator, "city", fields = List("x" -> "legal_name"))), Some(userA), _ => true)
        .left.map(_.message) shouldBe Left(s"Join 1 ('$Operator' on 'city'): 'city' must be a reference field linking '$Activity' and '$Operator', " +
          s"on either of them, but '$Activity' has no field 'city' typed 'reference:$Operator' and '$Operator' has no field 'city' typed 'reference:$Activity'.")

      When("user A, who may read the licence but lacks the tax number's read role, expands the page")
      val forA = plan(Activity, joins, Some(userA))(page(Activity), None, Some(userA))
      Then("shared operators are copied; another user's personal operator and a dangling reference give null")
      column(forA, "operator_legal_name") shouldBe List(JString("Acme Ltd"), JNull, JNull, JNull)
      And("the read-restricted tax number is null without its role")
      column(forA, "operator_tax_number") shouldBe List(JNull, JNull, JNull, JNull)
      And("the row-level licence is copied, because A's access list allows it")
      column(forA, "licence_number").head shouldBe JString("L-1")

      When("user B expands the same page")
      val forB = plan(Activity, joins, Some(userB))(page(Activity), None, Some(userB))
      Then("the licence is null for B, whose access list does not include it")
      column(forB, "licence_number").head shouldBe JNull
      And("B's own personal operator is still not copied: only shared records are used")
      column(forB, "operator_legal_name")(1) shouldBe JNull

      When("user A is granted the tax number's read role")
      Entitlement.entitlement.vend.addEntitlement(DynamicEntitySpace.bankIdOrSystem(None), userA, s"CanGetDynamicEntityField_${Operator}__tax_number")
      Then("the tax number is copied for A")
      column(plan(Activity, joins, Some(userA))(page(Activity), None, Some(userA)), "operator_tax_number").head shouldBe JString("DE-123")

      And("the page keeps its records and order")
      forA.map(r => (r \ "name").values.toString) shouldBe List("1 acme", "2 private operator", "3 dangling", "4 no reference")
    }
  }

  feature("Reverse joins: the other records' reference field names this record") {
    scenario("at_most_one with pick, many with order, exists with where and custom values, and a row-level related entity") {
      val sfx = suffix()
      val Activity = s"Activity$sfx"; val Certificate = s"Certificate$sfx"; val Inspection = s"Inspection$sfx"
      Given("activities, certificates referring to them (indexed), and row-level inspections referring to them")
      createDef(Activity, s"""{"${idField(Activity)}":{"type":"string"},"name":{"type":"string"}}""")
      createDef(Certificate, s"""{"${idField(Certificate)}":{"type":"string"},"number":{"type":"string"},"status":{"type":"string"},""" +
        s""""issue_date":{"type":"DATE_WITH_DAY"},"activity_id":{"type":"reference:$Activity","indexed":true},""" +
        s""""unindexed_activity_id":{"type":"reference:$Activity"}}""")
      createDef(Inspection, s"""{"${idField(Inspection)}":{"type":"string"},"activity_id":{"type":"reference:$Activity","indexed":true}}""", rowLevel = true)

      val withCertificates = saveRec(Activity, None, "name" -> JString("1 with certificates"))
      val withNone = saveRec(Activity, None, "name" -> JString("2 with none"))
      saveRec(Certificate, None, "number" -> JString("C-1"), "status" -> JString("valid"), "issue_date" -> JString("2026-01-01"), "activity_id" -> JString(withCertificates))
      saveRec(Certificate, None, "number" -> JString("C-2"), "status" -> JString("revoked"), "issue_date" -> JString("2026-06-01"), "activity_id" -> JString(withCertificates))
      saveRec(Certificate, None, "number" -> JString("C-3"), "status" -> JString("valid"), "activity_id" -> JString(withCertificates))
      saveRec(Certificate, Some(userB), "number" -> JString("C-personal"), "status" -> JString("valid"), "issue_date" -> JString("2027-01-01"), "activity_id" -> JString(withCertificates))
      grantRead(Inspection, saveRec(Inspection, None, "activity_id" -> JString(withCertificates)), userA)
      saveRec(Inspection, None, "activity_id" -> JString(withNone))

      val joins = List(
        JoinRequest(Certificate, "activity_id", cardinality = Some("at_most_one"), pick = Some("latest_by:issue_date"),
          fields = List("latest_certificate_number" -> "number", "latest_certificate_issue_date" -> "issue_date")),
        JoinRequest(Certificate, "activity_id", cardinality = Some("at_most_one"), pick = Some("latest_by:issue_date"),
          where = List(Filter("status", FilterOp.Eq, List("valid"))), fields = List("latest_valid_certificate_number" -> "number")),
        JoinRequest(Certificate, "activity_id", cardinality = Some("many"), as = Some("certificates"), order = Some("earliest_by:issue_date"),
          fields = List("number" -> "number", "status" -> "status")),
        JoinRequest(Certificate, "activity_id", cardinality = Some("exists"), as = Some("revocation"),
          where = List(Filter("status", FilterOp.Eq, List("revoked"))),
          trueValue = Some(JString("revoked")), falseValue = Some(JString("clean"))),
        JoinRequest(Inspection, "activity_id", cardinality = Some("exists"), as = Some("inspected")))

      Then("a reference field that is not indexed is rejected with the field to fix")
      JoinPlanner.planFor(None, Activity, List(JoinRequest(Certificate, "unindexed_activity_id", cardinality = Some("exists"), as = Some("x"))), Some(userA), _ => true)
        .left.map(_.message).left.getOrElse("") should include("'unindexed_activity_id' on '" + Certificate + "' must be declared \"indexed\": true")

      When("user A applies them to the page")
      val forA = plan(Activity, joins, Some(userA))(page(Activity), None, Some(userA))
      Then("at_most_one picks the latest dated certificate; the undated one and another user's personal one are never chosen")
      column(forA, "latest_certificate_number") shouldBe List(JString("C-2"), JNull)
      column(forA, "latest_certificate_issue_date") shouldBe List(JString("2026-06-01"), JNull)
      And("the where filter is applied before the pick")
      column(forA, "latest_valid_certificate_number") shouldBe List(JString("C-1"), JNull)
      And("many lists the shared certificates, earliest first, the undated one last")
      (forA.head \ "certificates" \ "number") shouldBe JArray(List(JString("C-1"), JString("C-2"), JString("C-3")))
      (forA(1) \ "certificates") shouldBe JArray(Nil)
      And("exists gives the custom values")
      column(forA, "revocation") shouldBe List(JString("revoked"), JString("clean"))
      And("the row-level inspection counts only where A's access list allows it")
      column(forA, "inspected") shouldBe List(JBool(true), JBool(false))

      When("user B applies the same")
      val forB = plan(Activity, joins, Some(userB))(page(Activity), None, Some(userB))
      Then("B sees no inspection, and B's own personal certificate is still not used")
      column(forB, "inspected") shouldBe List(JBool(false), JBool(false))
      column(forB, "latest_certificate_number").head shouldBe JString("C-2")

      if (IndexingCapabilities.projectionEnabled) {
        When("the projection is enabled, provision it so the lookup goes through the indexed column")
        List(Certificate, Inspection).foreach(e => ProjectionProvisioner.ensureProvisioned(None, e).unsafeRunSync())
        Then("the indexed lookup gives exactly the same result")
        plan(Activity, joins, Some(userA))(page(Activity), None, Some(userA)) shouldBe forA
      }
    }
  }

  feature("A self-reference needs a direction") {
    scenario("an employee's manager (forward) and direct reports (reverse) through the same field") {
      val Employee = s"Employee${suffix()}"
      createDef(Employee, s"""{"${idField(Employee)}":{"type":"string"},"name":{"type":"string"},"manager_id":{"type":"reference:$Employee","indexed":true}}""")
      val boss = saveRec(Employee, None, "name" -> JString("1 boss"))
      saveRec(Employee, None, "name" -> JString("2 alice"), "manager_id" -> JString(boss))
      saveRec(Employee, None, "name" -> JString("3 bob"), "manager_id" -> JString(boss))

      Then("without a direction the join is refused, with both readings explained")
      JoinPlanner.planFor(None, Employee, List(JoinRequest(Employee, "manager_id", fields = List("manager_name" -> "name"))), Some(userA), _ => true)
        .left.map(_.message).left.getOrElse("") should include("Say which with \"direction\"")

      When("both directions are given")
      val joined = plan(Employee, List(
        JoinRequest(Employee, "manager_id", direction = Some("forward"), fields = List("manager_name" -> "name")),
        JoinRequest(Employee, "manager_id", direction = Some("reverse"), cardinality = Some("many"), as = Some("reports"),
          order = Some("earliest_by:name"), fields = List("name" -> "name"))), Some(userA))(page(Employee), None, Some(userA))
      Then("each employee gets their manager, and the boss gets their reports")
      column(joined, "manager_name") shouldBe List(JNull, JString("1 boss"), JString("1 boss"))
      (joined.head \ "reports" \ "name") shouldBe JArray(List(JString("2 alice"), JString("3 bob")))
      (joined(1) \ "reports") shouldBe JArray(Nil)
    }
  }
}

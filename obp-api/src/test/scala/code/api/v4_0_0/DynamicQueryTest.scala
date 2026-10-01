package code.api.v4_0_0

import code.DynamicData.DynamicDataProvider
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import cats.effect.unsafe.implicits.global
import code.api.dynamic.entity.helper.DynamicEntitySpace
import code.api.dynamic.entity.projection.{IndexingCapabilities, ProjectionProvisioner}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.{DynamicCodeExecutionDisabled, DynamicQueryEntityNotReadable, DynamicQueryInvalid}
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import net.liftweb.util.StringHelpers
import org.json4s._
import org.json4s.native.Serialization.write

import java.net.URLEncoder

/**
 * This suite checks Dynamic Queries end to end: a Dynamic Resource Doc with `programming_lang` `Query`
 * is created over v4.0.0, checked against the entity definitions, served under the dynamic-endpoint
 * prefix, and called: the entity read check, the joins, the envelope and count, and the caller's own
 * list parameters. Also: a Dynamic Query must be GET, and needs no user-supplied code to be enabled;
 * and the v7.0.0 dry-run compile checks one. The body parser on its own is covered by
 * code.api.dynamic.entity.query.DynamicQueryDeclarationSpec, the joins by JoinSpec and
 * DynamicEntityJoinPlanIntegrationTest.
 */
class DynamicQueryTest extends V400ServerSetup {

  private val owner = "dynamic-query-owner"
  private val sfx = java.util.UUID.randomUUID().toString.take(8).replace("-", "")
  private val Operator = s"Operator$sfx"
  private val Activity = s"Activity$sfx"
  private val Certificate = s"Certificate$sfx"
  private def idField(entity: String): String = StringHelpers.snakify(entity) + "_id"

  private def createDef(entity: String, propsJson: String): Unit =
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(
      DynamicEntityCommons(entity, s"""{"$entity":{"properties":$propsJson}}""", None, owner, None, hasPersonalEntity = false)
    ).openOrThrowException(s"failed to create definition for $entity")

  private def saveRec(entity: String, fields: (String, JValue)*): String = {
    val id = java.util.UUID.randomUUID().toString
    val body = JObject(JField(idField(entity), JString(id)) :: fields.toList.map { case (k, v) => JField(k, v) })
    DynamicDataProvider.connectorMethodProvider.vend.save(None, entity, body, Some(owner), false).openOrThrowException(s"failed to save $entity record")
    id
  }

  private def grantRecordRead(entity: String): Unit =
    Entitlement.entitlement.vend.addEntitlement(DynamicEntitySpace.bankIdOrSystem(None), resourceUser1.userId, s"CanGetDynamicEntityRecord_$entity")

  // Braced body on purpose: .github/scripts/check_test_isolation.py only recognises `def name {` as a helper.
  private def userCodeAllowed(allowed: Boolean): Unit = {
    setPropsValues("allow_user_generated_scala_code" -> allowed.toString)
  }

  private def queryDoc(urlSegment: String, declaration: String, verb: String = "GET") =
    SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
      dynamicResourceDocId = None, bankId = None, roles = "",
      partialFunctionName = s"dynamicQuery${urlSegment.capitalize}",
      requestVerb = verb, requestUrl = s"/$urlSegment",
      exampleRequestBody = None,
      methodBody = URLEncoder.encode(declaration, "UTF-8"),
      programmingLang = "Query")

  private def create(doc: code.dynamicResourceDoc.JsonDynamicResourceDoc) =
    makePostRequest((v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1), write(doc))

  private def messageOf(response: code.setup.APIResponse): String = response.body.extract[ErrorMessage].message

  private val activitiesQuery =
    s"""{
       |  "from": "$Activity",
       |  "select": ["${idField(Activity)}", "name"],
       |  "join": [
       |    { "entity": "$Operator", "on": "operator_id", "fields": { "operator_legal_name": "legal_name" } },
       |    { "entity": "$Certificate", "on": "activity_id", "cardinality": "at_most_one", "pick": "latest_by:issue_date",
       |      "fields": { "latest_certificate": "number" } },
       |    { "entity": "$Certificate", "on": "activity_id", "cardinality": "exists", "as": "certified" }
       |  ],
       |  "envelope": { "rows": "activities", "count": "count" }
       |}""".stripMargin

  feature("Dynamic Query: a Dynamic Resource Doc whose body is a declaration (programming_lang Query)") {

    scenario("create, check access, join, page and count") {
      Given("operators, activities and certificates referring to the activities")
      createDef(Operator, s"""{"${idField(Operator)}":{"type":"string"},"legal_name":{"type":"string"}}""")
      createDef(Activity, s"""{"${idField(Activity)}":{"type":"string"},"name":{"type":"string","indexed":true},"operator_id":{"type":"reference:$Operator"}}""")
      createDef(Certificate, s"""{"${idField(Certificate)}":{"type":"string"},"number":{"type":"string"},"issue_date":{"type":"DATE_WITH_DAY"},""" +
        s""""activity_id":{"type":"reference:$Activity","indexed":true}}""")
      val acme = saveRec(Operator, "legal_name" -> JString("Acme Ltd"))
      val first = saveRec(Activity, "name" -> JString("a first"), "operator_id" -> JString(acme))
      saveRec(Activity, "name" -> JString("b second"))
      saveRec(Certificate, "number" -> JString("C-old"), "issue_date" -> JString("2026-01-01"), "activity_id" -> JString(first))
      saveRec(Certificate, "number" -> JString("C-new"), "issue_date" -> JString("2026-06-01"), "activity_id" -> JString(first))
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      When("a Dynamic Query is created")
      create(queryDoc(s"dq_$sfx/activities", activitiesQuery)).code should equal(201)
      val call = dynamicEndpoint_Request / "dynamic-resource-doc" / s"dq_$sfx" / "activities"

      Then("a caller who may not read the entities it reads is refused")
      val refused = makeGetRequest(call.GET <@ (user1))
      refused.code should equal(403)
      messageOf(refused) should include(DynamicQueryEntityNotReadable)
      grantRecordRead(Activity); grantRecordRead(Operator)
      messageOf(makeGetRequest(call.GET <@ (user1))) should include(s"It reads '$Certificate'")
      grantRecordRead(Certificate)

      When("the caller may read all three")
      val answered = makeGetRequest(call.GET <@ (user1) <<? List("obp_sort_by" -> "name"))
      Then("the selected fields and the joins come back in the envelope, with the count")
      answered.code should equal(200)
      val rows = (answered.body \ "activities").asInstanceOf[JArray].arr
      rows.map(_ \ "name") shouldBe List(JString("a first"), JString("b second"))
      rows.head.asInstanceOf[JObject].obj.map(_._1) shouldBe List(idField(Activity), "name", "operator_legal_name", "latest_certificate", "certified")
      rows.map(_ \ "operator_legal_name") shouldBe List(JString("Acme Ltd"), JNull)
      rows.map(_ \ "latest_certificate") shouldBe List(JString("C-new"), JNull)
      rows.map(_ \ "certified") shouldBe List(JBool(true), JBool(false))
      (answered.body \ "count") shouldBe JInt(2)

      When("the caller narrows it with the list endpoint's own parameters")
      val paged = makeGetRequest(call.GET <@ (user1) <<? List("obp_sort_by" -> "name", "obp_sort_direction" -> "DESC", "obp_limit" -> "1"))
      Then("one page comes back, and the count is still of every match")
      ((paged.body \ "activities").asInstanceOf[JArray].arr.map(_ \ "name"), paged.body \ "count") shouldBe ((List(JString("b second")), JInt(2)))
      val filtered = makeGetRequest(call.GET <@ (user1) <<? List("obp_filter[name]" -> "eq:a first"))
      (filtered.body \ "count") shouldBe JInt(1)

      if (IndexingCapabilities.projectionEnabled) {
        When("the projection is enabled and provisioned, so the page is read from it")
        ProjectionProvisioner.ensureProvisioned(None, Activity).unsafeRunSync()
        Then("the page and the count are the same")
        makeGetRequest(call.GET <@ (user1) <<? List("obp_sort_by" -> "name")).body shouldBe answered.body
        makeGetRequest(call.GET <@ (user1) <<? List("obp_sort_by" -> "name", "obp_sort_direction" -> "DESC", "obp_limit" -> "1")).body shouldBe paged.body
      }

      And("a filter on a field that is not indexed is refused with the list endpoint's message")
      val unindexed = makeGetRequest(call.GET <@ (user1) <<? List("obp_filter[operator_id]" -> s"eq:$acme"))
      unindexed.code should equal(400)
      messageOf(unindexed) should include(DynamicQueryInvalid)
    }

    scenario("a Dynamic Query is checked when created, must be GET, and needs no user-supplied code") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)
      val missing = create(queryDoc(s"dq_missing_$sfx", s"""{ "from": "NoSuchEntity$sfx" }"""))
      missing.code should equal(400)
      messageOf(missing) should include(s"${DynamicQueryInvalid}There is no Dynamic Entity 'NoSuchEntity$sfx' in this space.")

      val malformed = create(queryDoc(s"dq_malformed_$sfx", s"""{ "from": "x", "joins": [] }"""))
      malformed.code should equal(400)
      messageOf(malformed) should include("unknown key 'joins'")

      val posted = create(queryDoc(s"dq_post_$sfx", s"""{ "from": "x" }""", verb = "POST").copy(exampleRequestBody = Some(JObject())))
      posted.code should equal(400)
      messageOf(posted) should include("request_verb must be GET")

      When("user-supplied code is switched off")
      userCodeAllowed(false)
      createDef(s"Plain$sfx", s"""{"${idField(s"Plain$sfx")}":{"type":"string"},"name":{"type":"string"}}""")
      Then("a Dynamic Query can still be created, but a Scala body cannot")
      create(queryDoc(s"dq_switched_off_$sfx", s"""{ "from": "Plain$sfx" }""")).code should equal(201)
      val scala = create(queryDoc(s"dq_scala_$sfx", "").copy(methodBody = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.methodBody,
        programmingLang = "Scala", requestVerb = "POST", exampleRequestBody = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.exampleRequestBody))
      messageOf(scala) should include(DynamicCodeExecutionDisabled)
      userCodeAllowed(true)
    }

    scenario("the v7.0.0 dry-run compile checks a Dynamic Query") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)
      createDef(s"Checked$sfx", s"""{"${idField(s"Checked$sfx")}":{"type":"string"},"name":{"type":"string"}}""")
      def compile(declaration: String) = makePostRequest(
        (baseRequest / "obp" / "v7.0.0" / "management" / "dynamic-resource-docs" / "compile").POST <@ (user1),
        write(Map("request_verb" -> "GET", "request_url" -> "/checked", "programming_lang" -> "Query",
          "method_body" -> URLEncoder.encode(declaration, "UTF-8"))))
      val good = compile(s"""{ "from": "Checked$sfx", "select": ["name"] }""")
      good.code should equal(200)
      (good.body \ "compiles") shouldBe JBool(true)
      val bad = compile(s"""{ "from": "Checked$sfx", "select": ["nope"] }""")
      (bad.body \ "compiles") shouldBe JBool(false)
      ((bad.body \ "errors")(0) \ "message").values.toString should include("'select' names 'nope'")
    }
  }
}

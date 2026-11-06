package code.api.v4_0_0

import code.DynamicData.{DynamicDataAccessProvider, DynamicDataProvider}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.dynamic.entity.helper.DynamicEntitySpace
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import cats.effect.unsafe.implicits.global
import code.api.dynamic.entity.projection.{IndexingCapabilities, ProjectionProvisioner}
import code.api.util.ErrorMessages.{DynamicEntityFieldNotReadable, DynamicQueryEntityNotReadable, DynamicQueryInvalid, UserHasMissingRoles}
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.entitlement.Entitlement
import code.setup.OBPReq
import com.openbankproject.commons.model.ErrorMessage
import net.liftweb.util.StringHelpers
import org.json4s._
import org.json4s.native.Serialization.write

import java.net.URLEncoder

/**
 * This suite checks who can read what through one Dynamic Query that joins four Dynamic Entities,
 * end to end over HTTP, as the entities' access and the caller's Roles vary.
 *
 * The four entities, created afresh for each scenario:
 *  - `activity`, the query's `from`;
 *  - `operator`, joined forward through `activity.operator_id`; its `tax_number` needs its own read Role;
 *  - `country`, joined forward through `activity.country_id`;
 *  - `certificate`, joined in reverse through `certificate.activity_id`, twice: the latest one, and
 *    whether any exists.
 *
 * The rule under test: the entities' own access is the primary control. A caller must be able to read
 * every entity the query reads (public access, the entity's read Role, or row-level access); Roles on
 * the doc itself can only narrow who may call it, never widen what a caller can read.
 */
class DynamicQueryAccessTest extends V400ServerSetup {

  private val owner = "dynamic-query-access-owner"
  private def idField(entity: String): String = StringHelpers.snakify(entity) + "_id"

  /** The four entities of one scenario, and the records the assertions refer to. */
  private case class Registry(activity: String, operator: String, country: String, certificate: String,
                              withEverything: String, withNothing: String, acme: String)

  private def createDef(entity: String, propsJson: String, publicAccess: Boolean, rowLevel: Boolean = false): Unit =
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(
      DynamicEntityCommons(entity, s"""{"$entity":{"properties":$propsJson}}""", None, owner, None,
        hasPersonalEntity = true, hasPublicAccess = publicAccess, useRowLevelAccess = rowLevel)
    ).openOrThrowException(s"failed to create definition for $entity")

  private def saveRec(entity: String, fields: (String, JValue)*): String = saveAs(entity, None, fields: _*)

  /** Save a record, shared unless `personalOwner` is given. Returns its id. */
  private def saveAs(entity: String, personalOwner: Option[String], fields: (String, JValue)*): String = {
    val id = java.util.UUID.randomUUID().toString
    val body = JObject(JField(idField(entity), JString(id)) :: fields.toList.map { case (k, v) => JField(k, v) })
    DynamicDataProvider.connectorMethodProvider.vend.save(None, entity, body, personalOwner.orElse(Some(owner)), personalOwner.isDefined)
      .openOrThrowException(s"failed to save $entity record")
    id
  }

  /** Four entities; `public` names the ones with public access (by role in the query: activity, operator, country, certificate). */
  private def registry(public: Set[String]): Registry = {
    val sfx = java.util.UUID.randomUUID().toString.take(8).replace("-", "")
    val r = Registry(s"Activity$sfx", s"Operator$sfx", s"Country$sfx", s"Certificate$sfx", "", "", "")
    createDef(r.operator, s"""{"${idField(r.operator)}":{"type":"string"},"legal_name":{"type":"string"},""" +
      s""""tax_number":{"type":"string","read_role_required":true},"contact_email":{"type":"string","hide_field_from_public_access":true}}""",
      public.contains("operator"))
    createDef(r.country, s"""{"${idField(r.country)}":{"type":"string"},"name":{"type":"string"}}""", public.contains("country"))
    createDef(r.activity, s"""{"${idField(r.activity)}":{"type":"string"},"name":{"type":"string","indexed":true},""" +
      s""""operator_id":{"type":"reference:${r.operator}"},"country_id":{"type":"reference:${r.country}"}}""", public.contains("activity"))
    createDef(r.certificate, s"""{"${idField(r.certificate)}":{"type":"string"},"number":{"type":"string"},"issue_date":{"type":"DATE_WITH_DAY"},""" +
      s""""activity_id":{"type":"reference:${r.activity}","indexed":true}}""", public.contains("certificate"))

    val acme = saveRec(r.operator, "legal_name" -> JString("Acme Ltd"), "tax_number" -> JString("DE-123"),
      "contact_email" -> JString("office@acme.example"))
    val germany = saveRec(r.country, "name" -> JString("Germany"))
    val withEverything = saveRec(r.activity, "name" -> JString("1 with everything"), "operator_id" -> JString(acme), "country_id" -> JString(germany))
    val withNothing = saveRec(r.activity, "name" -> JString("2 with nothing"))
    saveRec(r.certificate, "number" -> JString("C-old"), "issue_date" -> JString("2026-01-01"), "activity_id" -> JString(withEverything))
    saveRec(r.certificate, "number" -> JString("C-new"), "issue_date" -> JString("2026-06-01"), "activity_id" -> JString(withEverything))
    r.copy(withEverything = withEverything, withNothing = withNothing, acme = acme)
  }

  private def declaration(r: Registry, extraJoins: String = ""): String =
    s"""{
       |  "from": "${r.activity}",
       |  "select": ["name"],
       |  "join": [
       |    { "entity": "${r.operator}", "on": "operator_id", "fields": { "operator_name": "legal_name", "operator_tax_number": "tax_number" } },
       |    { "entity": "${r.country}", "on": "country_id", "fields": { "country_name": "name" } },
       |    { "entity": "${r.certificate}", "on": "activity_id", "cardinality": "at_most_one", "pick": "latest_by:issue_date",
       |      "fields": { "latest_certificate": "number" } },
       |    { "entity": "${r.certificate}", "on": "activity_id", "cardinality": "exists", "as": "certified" }$extraJoins
       |  ],
       |  "envelope": { "rows": "activities", "count": "count" }
       |}""".stripMargin

  /** Create a Dynamic Query doc as user1 (who holds the create Role) and return the path it is served at. */
  private def createQuery(segment: String, body: String, roles: String = "") = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)
    val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
      dynamicResourceDocId = None, bankId = None, roles = roles,
      partialFunctionName = s"accessQuery${segment.capitalize}", requestVerb = "GET", requestUrl = s"/$segment",
      exampleRequestBody = None, errorResponseBodies = "OBP-50000: Unknown Error.",
      methodBody = URLEncoder.encode(body, "UTF-8"), programmingLang = "Query")
    val created = makePostRequest((v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1), write(doc))
    withClue(s"create: ${created.body}") { created.code should equal(201) }
    dynamicEndpoint_Request / "dynamic-resource-doc" / segment
  }

  private def grantRead(userId: String, entity: String): Unit =
    Entitlement.entitlement.vend.addEntitlement(DynamicEntitySpace.bankIdOrSystem(None), userId, s"CanGetDynamicEntityRecord_$entity")

  private def needs(entity: String) = s"$entity (needs CanGetDynamicEntityRecord_$entity at bank SYS)"
  private def notReadable(entities: String*) = s"$DynamicQueryEntityNotReadable${entities.map(needs).mkString(", ")}."
  private def messageOf(response: code.setup.APIResponse): String = response.body.extract[ErrorMessage].message

  /** The rows of an answer, by activity name. */
  private def rows(response: code.setup.APIResponse): Map[String, JValue] =
    withClue(s"response: ${response.body}") {
      response.code should equal(200)
      (response.body \ "activities").asInstanceOf[JArray].arr.map(row => (row \ "name").values.toString -> row).toMap
    }

  private val everything = "1 with everything"
  private val nothing = "2 with nothing"

  feature("A Dynamic Query that joins four Dynamic Entities, as their access and the caller's Roles vary") {

    scenario("all four entities public, no Roles on the doc: anyone can call it, and a read-restricted field stays null") {
      val r = registry(public = Set("activity", "operator", "country", "certificate"))
      val call = createQuery(s"access_all_public_${r.activity}", declaration(r))

      val answered = rows(makeGetRequest(call))
      answered(everything) \ "operator_name" shouldBe JString("Acme Ltd")
      answered(everything) \ "country_name" shouldBe JString("Germany")
      answered(everything) \ "latest_certificate" shouldBe JString("C-new")
      answered(everything) \ "certified" shouldBe JBool(true)
      answered(everything) \ "operator_tax_number" shouldBe JNull
      answered(nothing) \ "operator_name" shouldBe JNull
      answered(nothing) \ "certified" shouldBe JBool(false)
    }

    scenario("some entities public, some not: each caller is told every entity they may not read, until they may read them all") {
      val r = registry(public = Set("activity", "country"))
      val call = createQuery(s"access_mixed_${r.activity}", declaration(r))

      Then("an anonymous caller is refused, naming both entities that are not public, in the query's order")
      val anonymous = makeGetRequest(call)
      anonymous.code should equal(403)
      messageOf(anonymous) shouldBe notReadable(r.operator, r.certificate)

      And("so is a logged-in caller with no Roles")
      messageOf(makeGetRequest(call <@ (user2))) shouldBe notReadable(r.operator, r.certificate)

      When("the caller may read the operators")
      grantRead(resourceUser2.userId, r.operator)
      Then("only the certificates are still named")
      messageOf(makeGetRequest(call <@ (user2))) shouldBe notReadable(r.certificate)

      When("the caller may read the certificates too")
      grantRead(resourceUser2.userId, r.certificate)
      val answered = rows(makeGetRequest(call <@ (user2)))
      answered(everything) \ "operator_name" shouldBe JString("Acme Ltd")
      answered(everything) \ "latest_certificate" shouldBe JString("C-new")
      (makeGetRequest(call <@ (user2)).body \ "count") shouldBe JInt(2)

      And("another caller without those Roles is still refused")
      messageOf(makeGetRequest(call <@ (user3))) shouldBe notReadable(r.operator, r.certificate)
    }

    scenario("no entity public: the caller needs every entity's read Role, including the one it reads from") {
      val r = registry(public = Set.empty)
      val call = createQuery(s"access_none_public_${r.activity}", declaration(r))
      messageOf(makeGetRequest(call <@ (user2))) shouldBe notReadable(r.activity, r.operator, r.country, r.certificate)
      List(r.activity, r.operator, r.country).foreach(grantRead(resourceUser2.userId, _))
      messageOf(makeGetRequest(call <@ (user2))) shouldBe notReadable(r.certificate)
      grantRead(resourceUser2.userId, r.certificate)
      rows(makeGetRequest(call <@ (user2)))(everything) \ "country_name" shouldBe JString("Germany")
    }

    scenario("a Role on the doc only narrows who may call it; it never stands in for access to an entity") {
      val r = registry(public = Set("activity", "country"))
      val docRole = s"CanReadRegistry${r.activity}"
      val call = createQuery(s"access_doc_role_${r.activity}", declaration(r), roles = docRole)

      Then("a caller who may read every entity but lacks the doc's Role is refused by the doc's Role")
      List(r.operator, r.certificate).foreach(grantRead(resourceUser2.userId, _))
      val narrowed = makeGetRequest(call <@ (user2))
      narrowed.code should equal(403)
      messageOf(narrowed) should include(UserHasMissingRoles)

      And("with the doc's Role as well, the query answers")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, docRole)
      rows(makeGetRequest(call <@ (user2)))(everything) \ "operator_name" shouldBe JString("Acme Ltd")

      And("a caller with only the doc's Role is refused for the entities it may not read")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser3.userId, docRole)
      messageOf(makeGetRequest(call <@ (user3))) shouldBe notReadable(r.operator, r.certificate)
    }

    scenario("a read-restricted field is null without its Role, copied with it, and cannot be filtered on without it") {
      val r = registry(public = Set("activity", "operator", "country", "certificate"))
      val call = createQuery(s"access_field_${r.activity}", declaration(r))
      rows(makeGetRequest(call <@ (user2)))(everything) \ "operator_tax_number" shouldBe JNull

      val filtered = createQuery(s"access_field_where_${r.activity}",
        s"""{ "from": "${r.activity}", "join": [ { "entity": "${r.operator}", "on": "operator_id",
           |  "where": { "tax_number": "eq:DE-123" }, "fields": { "operator_name": "legal_name" } } ] }""".stripMargin)
      val refused = makeGetRequest(filtered <@ (user2))
      refused.code should equal(400)
      messageOf(refused) should include(DynamicQueryInvalid)
      messageOf(refused) should include("you may not read 'tax_number'")

      When("the caller holds the field's read Role")
      Entitlement.entitlement.vend.addEntitlement(DynamicEntitySpace.bankIdOrSystem(None), resourceUser2.userId,
        s"CanGetDynamicEntityField_${r.operator}__tax_number")
      rows(makeGetRequest(call <@ (user2)))(everything) \ "operator_tax_number" shouldBe JString("DE-123")
      makeGetRequest(filtered <@ (user2)).code should equal(200)
    }

    scenario("a row-level entity joined in counts only the rows the caller's access list allows; personal records are never used") {
      val r = registry(public = Set("activity", "operator", "country", "certificate"))
      val Inspection = s"Inspection${r.activity}"
      createDef(Inspection, s"""{"${idField(Inspection)}":{"type":"string"},"result":{"type":"string"},""" +
        s""""activity_id":{"type":"reference:${r.activity}","indexed":true}}""", publicAccess = false, rowLevel = true)
      val passed = saveRec(Inspection, "result" -> JString("passed"), "activity_id" -> JString(r.withEverything))
      saveRec(Inspection, "result" -> JString("failed"), "activity_id" -> JString(r.withEverything))
      And("an activity whose operator is someone's personal record")
      val personalOperator = saveAs(r.operator, Some(resourceUser2.userId), "legal_name" -> JString("Personal Ltd"))
      saveRec(r.activity, "name" -> JString("3 personal operator"), "operator_id" -> JString(personalOperator))

      val call = createQuery(s"access_row_level_${r.activity}", declaration(r,
        s""",
           |    { "entity": "$Inspection", "on": "activity_id", "cardinality": "many", "as": "inspections",
           |      "order": "earliest_by:result", "fields": { "result": "result" } }""".stripMargin))

      Then("an anonymous caller sees no inspections: rows of a row-level entity need an access list entry")
      rows(makeGetRequest(call))(everything) \ "inspections" shouldBe JArray(Nil)
      When("user 2 may read one of the two inspections")
      DynamicDataAccessProvider.provider.vend.grant(bankId = None, entityName = Inspection, dynamicDataId = passed,
        userId = resourceUser2.userId, canRead = true, canUpdate = false, canDelete = false, canGrant = false, grantedBy = owner)
      val forUser2 = rows(makeGetRequest(call <@ (user2)))
      Then("user 2 sees that one only, and user 3 still sees none")
      forUser2(everything) \ "inspections" \ "result" shouldBe JArray(List(JString("passed")))
      rows(makeGetRequest(call <@ (user3)))(everything) \ "inspections" shouldBe JArray(Nil)
      And("the personal operator is not copied, not even for the user who owns it")
      forUser2("3 personal operator") \ "operator_name" shouldBe JNull
    }
  }

  /** POST the declaration to Explain Dynamic Query; `sign` adds the caller's credentials to the request. */
  private def explain(declarationText: String, sign: OBPReq => OBPReq, anonymous: Boolean = false, callerParameters: String = "") = {
    val body = JObject(
      JField("method_body", JString(URLEncoder.encode(declarationText, "UTF-8"))),
      JField("caller_parameters", JString(callerParameters)),
      JField("as_anonymous_caller", JBool(anonymous)))
    makePostRequest(sign((baseRequest / "obp" / "v7.0.0" / "management" / "dynamic-resource-docs" / "explain").POST),
      com.openbankproject.commons.util.JsonAliases.compactRender(body))
  }

  feature("Explain Dynamic Query: the reads a query would make, and the access it needs") {

    scenario("the author sees every step, every entity with its Role, the restricted field, and the refusal a caller would get") {
      val r = registry(public = Set("activity", "country"))
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      val forMe = explain(declaration(r), _ <@ (user1))
      withClue(s"explain: ${forMe.body}") { forMe.code should equal(200) }
      val json = forMe.body
      (json \ "space") shouldBe JString("SYS")
      (json \ "explained_for") shouldBe JString(s"user ${resourceUser1.userId}")
      (json \ "caller_may_run") shouldBe JBool(false)
      (json \ "refusal") shouldBe JString(notReadable(r.operator, r.certificate))

      Then("every entity is listed in the query's order, with its Role, its public access and whether I may read it")
      val entities = (json \ "entities").children
      entities.map(e => (e \ "entity").values.toString) shouldBe List(r.activity, r.operator, r.country, r.certificate)
      entities.map(e => (e \ "read_role").values.toString) shouldBe List(r.activity, r.operator, r.country, r.certificate).map("CanGetDynamicEntityRecord_" + _)
      entities.map(e => (e \ "public_access").values) shouldBe List(true, false, true, false)
      entities.map(e => (e \ "caller_may_read").values) shouldBe List(true, false, true, false)

      And("the read-restricted field it copies is named with its Role")
      ((json \ "restricted_fields").children.map(f => ((f \ "entity").values, (f \ "field").values, (f \ "read_role").values))) shouldBe
        List((r.operator, "tax_number", s"CanGetDynamicEntityField_${r.operator}__tax_number"))

      And("the steps are the page, then each join in order; the second certificate join reuses the first one's read")
      val steps = (json \ "steps").children
      steps.map(step => (step \ "step").values) shouldBe List(1, 2, 3, 4, 5)
      val backends = steps.map(step => (step \ "backend").values.toString)
      (backends(1), backends(2), backends(4)) shouldBe (("record provider", "record provider", "shared"))
      (steps(1) \ "purpose").values.toString should include(s"follows '${r.activity}.operator_id' to '${r.operator}' (forward)")
      (steps(3) \ "purpose").values.toString should include(s"'${r.certificate}' records whose 'activity_id' names the '${r.activity}' (reverse)")
      (steps(4) \ "notes").children.head shouldBe JString("Uses the records already read for Join 3; nothing more is read.")
      (json \ "rules").children should not be empty

      When("it is explained for an anonymous caller")
      val anonymous = explain(declaration(r), _ <@ (user1), anonymous = true).body
      (anonymous \ "explained_for") shouldBe JString("an anonymous caller")
      (anonymous \ "refusal") shouldBe JString(notReadable(r.operator, r.certificate))

      When("I may read the two other entities")
      List(r.operator, r.certificate).foreach(grantRead(resourceUser1.userId, _))
      val allowed = explain(declaration(r), _ <@ (user1)).body
      Then("the query can run for me, and nothing is refused")
      (allowed \ "caller_may_run") shouldBe JBool(true)
      (allowed \ "refusal") shouldBe JNothing
    }

    scenario("with the projection, the page and the reverse join show the SQL that would run, every value a ?") {
      if (!IndexingCapabilities.projectionEnabled) cancel("needs the query projection (dynamic_entity.indexing.backend=auto)")
      val r = registry(public = Set("activity", "operator", "country", "certificate"))
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)
      List(r.activity, r.certificate).foreach(entity => ProjectionProvisioner.ensureProvisioned(None, entity).unsafeRunSync())

      val steps = (explain(declaration(r), _ <@ (user1), callerParameters = "obp_sort_by=name&obp_limit=10").body \ "steps").children
      val page = steps.head
      (page \ "backend") shouldBe JString("projection")
      val pageSql = (page \ "sql").values.toString
      pageSql should startWith("SELECT d.datajson FROM de_")
      pageSql should include("d.bankid = ?")
      pageSql should include("d.ispersonalentity = ?")
      pageSql should include("ORDER BY")
      pageSql should include("LIMIT ?")
      (page \ "parameter_count").values shouldBe pageSql.count(_ == '?')
      (steps(1) \ "purpose").values.toString should include("Count every match")

      val reverse = steps.find(step => (step \ "purpose").values.toString.contains("(reverse)")).get
      (reverse \ "backend") shouldBe JString("projection")
      (reverse \ "sql").values.toString should include("IN (")
    }

    scenario("explaining needs the Role for writing Dynamic Resource Docs, and an invalid declaration is refused as Check would") {
      val r = registry(public = Set("activity"))
      val withoutRole = explain(declaration(r), _ <@ (user3))
      withoutRole.code should equal(403)
      messageOf(withoutRole) should include(UserHasMissingRoles)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)
      val invalid = explain(s"""{ "from": "${r.activity}", "select": ["nope"] }""", _ <@ (user1))
      invalid.code should equal(400)
      messageOf(invalid) should include(s"${DynamicQueryInvalid}'select' names 'nope'")
    }
  }

  private def dynamicEntityRequest: OBPReq = baseRequest / "obp" / "dynamic-entity"

  feature("hide_field_from_public_access: a field of a public entity hidden from callers who reach it through public access") {

    scenario("hidden in a Dynamic Query and on the public endpoint, shown with the entity's read Role, and never usable to filter by those who may not see it") {
      val r = registry(public = Set("activity", "operator", "country", "certificate"))
      val call = createQuery(s"access_public_hidden_${r.activity}",
        s"""{ "from": "${r.activity}", "select": ["name"], "join": [ { "entity": "${r.operator}", "on": "operator_id",
           |  "fields": { "operator_name": "legal_name", "operator_contact": "contact_email" } } ] }""".stripMargin)
      def contact(response: code.setup.APIResponse): JValue =
        (response.body \ StringHelpers.snakify(r.activity).concat("_list")).children
          .find(row => (row \ "name") == JString(everything)).map(_ \ "operator_contact").getOrElse(fail(s"no row in ${response.body}"))

      Then("an anonymous caller, and a logged-in one without the operator's read Role, get null for it")
      contact(makeGetRequest(call)) shouldBe JNull
      contact(makeGetRequest(call <@ (user2))) shouldBe JNull
      When("the caller holds the operator's read Role, their access is not public, and they see it")
      grantRead(resourceUser2.userId, r.operator)
      contact(makeGetRequest(call <@ (user2))) shouldBe JString("office@acme.example")

      And("Explain names the field, the rule and the Role that lifts it")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)
      val explained = explain(s"""{ "from": "${r.activity}", "join": [ { "entity": "${r.operator}", "on": "operator_id",
                                 |  "fields": { "operator_contact": "contact_email" } } ] }""".stripMargin, _ <@ (user1), anonymous = true).body
      ((explained \ "restricted_fields").children.map(f => ((f \ "field").values, (f \ "restriction").values, (f \ "read_role").values, (f \ "caller_may_read").values))) shouldBe
        List(("contact_email", "hide_field_from_public_access", s"CanGetDynamicEntityRecord_${r.operator}", false))

      Then("the public endpoint leaves out both the hidden and the read-restricted field")
      val publicList = makeGetRequest(dynamicEntityRequest / "public" / r.operator)
      withClue(s"public: ${publicList.body}") { publicList.code should equal(200) }
      val publicRecord = (publicList.body \ StringHelpers.snakify(r.operator).concat("_list")).children.head
      (publicRecord \ "legal_name") shouldBe JString("Acme Ltd")
      (publicRecord \ "contact_email") shouldBe JNothing
      (publicRecord \ "tax_number") shouldBe JNothing

      And("a public caller cannot filter by either, which would reveal their values")
      val byContact = makeGetRequest(dynamicEntityRequest / "public" / r.operator <<? List("contact_email" -> "office@acme.example"))
      byContact.code should equal(400)
      messageOf(byContact) shouldBe s"$DynamicEntityFieldNotReadable${r.operator}.contact_email."
      messageOf(makeGetRequest(dynamicEntityRequest / "public" / r.operator <<? List("tax_number" -> "DE-123"))) shouldBe
        s"$DynamicEntityFieldNotReadable${r.operator}.tax_number."

      And("on the authenticated endpoint, the read Role holder may filter by the hidden field but not by the one needing its own Role")
      makeGetRequest(dynamicEntityRequest / r.operator <@ (user2) <<? List("contact_email" -> "office@acme.example")).code should equal(200)
      val byTax = makeGetRequest(dynamicEntityRequest / r.operator <@ (user2) <<? List("tax_number" -> "DE-123"))
      byTax.code should equal(400)
      messageOf(byTax) shouldBe s"$DynamicEntityFieldNotReadable${r.operator}.tax_number."
    }
  }
}

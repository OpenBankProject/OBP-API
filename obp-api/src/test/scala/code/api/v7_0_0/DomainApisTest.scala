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
package code.api.v7_0_0

import code.DynamicData.DynamicDataProvider
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.dynamic.domainapi.Http4sDomainApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{canCreateBankLevelDynamicResourceDoc, canCreateDomainApi, canCreateDynamicEntityDefinition, canDeleteDomainApi, canGetDomainApis, canUpdateBankLevelDynamicResourceDoc, canUpdateDomainApi}
import code.api.util.ErrorMessages._
import code.api.v6_0_0.V600ServerSetup
import code.domainapi.DomainApis
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.dynamicResourceDoc.DynamicResourceDocProvider
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.net.URLEncoder

/**
 * This suite checks Domain APIs: a space's Dynamic Entities and Dynamic Resource Docs published under a
 * base path of its own. It covers the v7.0.0 management endpoints (Roles at SYS and at a bank, the checks
 * on base path, version and title, overlap and clash refusals), the front door (a Dynamic Entity and a
 * Dynamic Query answered under the base path exactly as at their OBP URLs, without bank_id, with the same
 * refusal for a caller who may not read), and the OpenAPI document (every documented path is served, and
 * the documented example has the fields the real response has).
 */
class DomainApisTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("createDomainApi")
  object ApiEndpoint2 extends Tag("getDomainApis")
  object ApiEndpoint3 extends Tag("getDomainApi")
  object ApiEndpoint4 extends Tag("updateDomainApi")
  object ApiEndpoint5 extends Tag("deleteDomainApi")
  object ApiEndpoint6 extends Tag("getPubliclyDiscoverableDomainApis")
  object ApiEndpoint7 extends Tag("getDomainApiResourceDocs")
  object ApiEndpoint8 extends Tag("getPubliclyDiscoverableDomainApisInEverySpace")

  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  private val owner = "domain-api-owner"
  private val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(8)
  private val entity = s"activity_$suffix"
  /** The form in which the Domain API publishes an entity name: with hyphens for underscores. */
  private def hyphenated(entityName: String) = entityName.replace('_', '-')
  private val queryPath = s"summary_$suffix"

  private def v7 = baseRequest / "obp" / "v7.0.0"
  private def domainApis(bankId: String) = v7 / "management" / "banks" / bankId / "domain-apis"
  private def publicDomainApis(bankId: String) = v7 / "banks" / bankId / "domain-apis"
  private def registration(basePath: String, version: String, title: String = "Test Domain API",
                           publiclyDiscoverable: Option[Boolean] = None) =
    compact(render(("base_path" -> basePath) ~ ("version" -> version) ~ ("title" -> title) ~ ("description" -> "For the test.") ~
      ("is_publicly_discoverable" -> publiclyDiscoverable)))
  private def message(response: code.setup.APIResponse) = response.body.extract[ErrorMessage].message
  private def grantAt(bankId: String, role: code.api.util.ApiRole) =
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, role.toString)
  private def grantAllAt(bankId: String): Unit =
    List(canCreateDomainApi, canGetDomainApis, canUpdateDomainApi, canDeleteDomainApi).foreach(grantAt(bankId, _))
  /** Every value of `field` anywhere in `json`, one or many. */
  private def valuesOf(json: JValue, field: String): List[String] = (json \\ field) match {
    case JObject(fields) => fields.map(_._2.extract[String])
    case JNothing => Nil
    case single => List(single.extract[String])
  }
  private def under(basePath: String) = basePath.split("/").foldLeft(baseRequest)(_ / _)

  /** An entity in a space (None = system) holding one record, readable by user1 there. */
  private def entityWithRecord(space: Option[String], entityName: String, name: String): Unit = {
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
      entityName, s"""{"$entityName":{"properties":{"${entityName}_id":{"type":"string","example":"1"},"name":{"type":"string","example":"tree planting"}}}}""",
      None, owner, space, hasPersonalEntity = false)).openOrThrowException("definition")
    DynamicDataProvider.connectorMethodProvider.vend.save(space, entityName,
      JObject(JField(s"${entityName}_id", JString(java.util.UUID.randomUUID().toString)), JField("name", JString(name))),
      Some(owner), false).openOrThrowException("record")
    Entitlement.entitlement.vend.addEntitlement(space.getOrElse(SYS), resourceUser1.userId, s"CanGetDynamicEntityRecord_$entityName")
  }

  /** A Dynamic Query in a space (None = system) at `url`, reading `entityName`. */
  private def queryDoc(space: Option[String], url: String, entityName: String, functionName: String): Unit =
    DynamicResourceDocProvider.provider.vend.create(space, SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
      dynamicResourceDocId = None, bankId = space, roles = "", partialFunctionName = functionName,
      requestVerb = "GET", requestUrl = url, exampleRequestBody = None, errorResponseBodies = "OBP-50000: Unknown Error.",
      methodBody = URLEncoder.encode(s"""{ "from": "$entityName", "select": ["name"], "envelope": { "rows": "names" } }""", "UTF-8"),
      programmingLang = "Query"), Some(owner)).openOrThrowException(s"doc in $space")

  private def dynamicResourceDocsAt(bankId: String) = baseRequest / "obp" / "v4.0.0" / "management" / "banks" / bankId / "dynamic-resource-docs"

  /** The body of a Dynamic Query at `url` in a bank, reading `entityName`, as the management endpoints take it. */
  private def queryDocBody(bankId: String, url: String, entityName: String, functionName: String): String =
    write(SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
      dynamicResourceDocId = None, bankId = Some(bankId), roles = "", partialFunctionName = functionName,
      requestVerb = "GET", requestUrl = url, exampleRequestBody = None, errorResponseBodies = "OBP-50000: Unknown Error.",
      methodBody = URLEncoder.encode(s"""{ "from": "$entityName", "select": ["name"], "envelope": { "rows": "names" } }""", "UTF-8"),
      programmingLang = "Query"))

  feature("Managing Domain APIs") {

    scenario("create, read, update and delete a Domain API at SYS, with the Roles held at SYS",
      ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, VersionOfApi) {
      val basePath = s"managed-$suffix/v1"

      When("an anonymous caller creates one")
      makePostRequest(domainApis(SYS).POST, registration(basePath, "1.0.0")).code should equal(401)

      When("user1 creates one without the Role")
      val refused = makePostRequest(domainApis(SYS).POST <@ (user1), registration(basePath, "1.0.0"))
      refused.code should equal(403)
      message(refused) should include(UserHasMissingRoles)

      grantAllAt(SYS)

      When("user1 creates one with the Role at SYS")
      val created = makePostRequest(domainApis(SYS).POST <@ (user1), registration(basePath, "1.0.0"))
      withClue(created.body) { created.code should equal(201) }
      val domainApiId = (created.body \ "domain_api_id").extract[String]
      (created.body \ "bank_id").extract[String] should equal(SYS)
      (created.body \ "base_path").extract[String] should equal(basePath)
      (created.body \ "url").extract[String] should endWith(s"/$basePath")

      Then("it is listed and can be read")
      val listed = makeGetRequest(domainApis(SYS).GET <@ (user1))
      listed.code should equal(200)
      valuesOf(listed.body \ "domain_apis", "domain_api_id") should contain(domainApiId)
      makeGetRequest((domainApis(SYS) / domainApiId).GET <@ (user1)).code should equal(200)

      And("a base path that overlaps it is refused, in any space")
      grantAllAt(testBankId1.value)
      val overlapping = makePostRequest(domainApis(testBankId1.value).POST <@ (user1), registration(basePath, "1.0.0"))
      overlapping.code should equal(409)
      message(overlapping) should include(DomainApiBasePathAlreadyExists)

      When("its version moves to 1.1.0")
      val updated = makePutRequest((domainApis(SYS) / domainApiId).PUT <@ (user1), registration(basePath, "1.1.0"))
      withClue(updated.body) { updated.code should equal(200) }
      (updated.body \ "version").extract[String] should equal("1.1.0")

      When("it is deleted")
      makeDeleteRequest((domainApis(SYS) / domainApiId).DELETE <@ (user1)).code should equal(204)
      Then("it is gone")
      val gone = makeGetRequest((domainApis(SYS) / domainApiId).GET <@ (user1))
      gone.code should equal(404)
      message(gone) should include(DomainApiNotFound)
    }

    scenario("a Domain API is listed publicly only once it is made publicly discoverable",
      ApiEndpoint1, ApiEndpoint4, ApiEndpoint6, ApiEndpoint8, VersionOfApi) {
      grantAllAt(SYS)
      val basePath = s"discoverable-$suffix/v1"
      // Listed for its space, and for every space at once: the two must agree.
      def publiclyListed: List[String] = {
        val listed = makeGetRequest(publicDomainApis(SYS).GET)
        withClue(listed.body) { listed.code should equal(200) }
        val inEverySpace = makeGetRequest((v7 / "domain-apis").GET)
        withClue(inEverySpace.body) { inEverySpace.code should equal(200) }
        val inSpace = valuesOf(listed.body \ "domain_apis", "base_path")
        valuesOf(inEverySpace.body \ "domain_apis", "base_path").contains(basePath) should equal(inSpace.contains(basePath))
        inSpace
      }

      When("one is created without saying")
      val created = makePostRequest(domainApis(SYS).POST <@ (user1), registration(basePath, "1.0.0"))
      withClue(created.body) { created.code should equal(201) }
      val domainApiId = (created.body \ "domain_api_id").extract[String]
      Then("it is not publicly discoverable, and an anonymous caller does not find it")
      (created.body \ "is_publicly_discoverable").extract[Boolean] should equal(false)
      publiclyListed should not contain basePath

      When("it is made publicly discoverable")
      val made = makePutRequest((domainApis(SYS) / domainApiId).PUT <@ (user1), registration(basePath, "1.0.0", publiclyDiscoverable = Some(true)))
      withClue(made.body) { made.code should equal(200) }
      (made.body \ "is_publicly_discoverable").extract[Boolean] should equal(true)
      Then("an anonymous caller finds it, with where it is published but not who made it")
      publiclyListed should contain(basePath)
      val listed = (makeGetRequest(publicDomainApis(SYS).GET).body \ "domain_apis").children
        .find(api => (api \ "base_path").extract[String] == basePath).get
      (listed \ "url").extract[String] should endWith(s"/$basePath")
      (listed \ "openapi_url").extract[String] should endWith(s"/$basePath/openapi.yaml")
      (listed \ "created_by_user_id") should equal(JNothing)

      When("it is updated without saying")
      val kept = makePutRequest((domainApis(SYS) / domainApiId).PUT <@ (user1), registration(basePath, "1.1.0"))
      Then("it stays publicly discoverable")
      (kept.body \ "is_publicly_discoverable").extract[Boolean] should equal(true)
      publiclyListed should contain(basePath)

      When("it is made not publicly discoverable")
      makePutRequest((domainApis(SYS) / domainApiId).PUT <@ (user1), registration(basePath, "1.1.0", publiclyDiscoverable = Some(false))).code should equal(200)
      Then("an anonymous caller no longer finds it")
      publiclyListed should not contain basePath

      makeDeleteRequest((domainApis(SYS) / domainApiId).DELETE <@ (user1)).code should equal(204)
    }

    scenario("base path, version and title are checked", ApiEndpoint1, VersionOfApi) {
      grantAllAt(SYS)
      def attempt(basePath: String, version: String, title: String = "Test Domain API") =
        makePostRequest(domainApis(SYS).POST <@ (user1), registration(basePath, version, title))

      Then("a base path without a version, in capitals, starting with obp, or too long is refused")
      List(s"noversion-$suffix", s"Upper-$suffix/v1", "obp/v1", s"a-$suffix/b/c/d/e/v1", s"/lead-$suffix/v1").foreach { basePath =>
        val response = attempt(basePath, "1.0.0")
        withClue(basePath) { response.code should equal(400) }
        message(response) should include(InvalidDomainApiBasePath)
      }
      And("a version that is not MAJOR.MINOR.PATCH, or whose MAJOR is not the base path's, is refused")
      List("1.0", "v1.0.0", "2.0.0").foreach { version =>
        val response = attempt(s"versioned-$suffix/v1", version)
        withClue(version) { response.code should equal(400) }
        message(response) should include(InvalidDomainApiVersion)
      }
      And("an empty title is refused")
      message(attempt(s"titled-$suffix/v1", "1.0.0", "")) should include(InvalidDomainApiTitle)
    }
  }

  feature("The Domain API front door") {

    scenario("a Dynamic Entity and a Dynamic Query answer under the base path as at their OBP URLs, without bank_id") {
      // The test props set write_metrics=false; the metric assertions below need the calls recorded.
      setPropsValues("write_metrics" -> "true")
      entityWithRecord(None, entity, "tree planting")
      queryDoc(None, s"/$queryPath/names", entity, s"domainApiSummary$suffix")
      grantAllAt(SYS)
      val basePath = s"registry-$suffix/v1"
      val created = makePostRequest(domainApis(SYS).POST <@ (user1), registration(basePath, "1.0.0"))
      withClue(created.body) { created.code should equal(201) }

      When("the entity's records are read under the base path and at the OBP URL")
      val published = makeGetRequest((under(basePath) / entity).GET <@ (user1))
      val obp = makeGetRequest((v7 / "banks" / SYS / "dynamic-entities" / entity).GET <@ (user1))
      Then("both answer the same records, and only the OBP URL names the space")
      withClue(published.body) { published.code should equal(200) }
      obp.code should equal(200)
      (published.body \ "bank_id") should equal(JNothing)
      (obp.body \ "bank_id").extract[String] should equal(SYS)
      valuesOf(published.body, "name") should contain("tree planting")
      (published.body \ s"${entity}_list") should equal(obp.body \ s"${entity}_list")

      And("a caller who may not read the entity is refused as at the OBP URL")
      val anonymous = makeGetRequest((under(basePath) / entity).GET)
      anonymous.code should equal(makeGetRequest((v7 / "banks" / SYS / "dynamic-entities" / entity).GET).code)

      When("the Dynamic Query is called under the base path")
      val query = makeGetRequest((under(basePath) / queryPath / "names").GET <@ (user1))
      Then("it answers")
      withClue(query.body) { query.code should equal(200) }
      valuesOf(query.body \ "names", "name") should contain("tree planting")

      And("each call's API Metric keeps the OBP URL and records the called one in domain_api_url")
      code.metrics.MetricBatchWriter.flush()
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, code.api.util.ApiRole.canReadMetrics.toString)
      val metrics = makeGetRequest((baseRequest / "obp" / "v6.0.0" / "management" / "metrics").GET <@ (user1) <<? List(
        "domain_api_url" -> s"/$basePath/", "limit" -> "50"))
      withClue(metrics.body) { metrics.code should equal(200) }
      val rows = (metrics.body \ "metrics").children
      val called = rows.map(row => ((row \ "domain_api_url").extract[String], (row \ "url").extract[String]))
      called should contain((s"/$basePath/$entity", s"/obp/v7.0.0/banks/$SYS/dynamic-entities/$entity"))
      called should contain((s"/$basePath/$queryPath/names", s"/obp/dynamic-endpoint/banks/$SYS/dynamic-resource-doc/$queryPath/names"))
      called.foreach { case (domainApiUrl, _) => domainApiUrl should startWith(s"/$basePath/") }

      And("a path the space does not serve is a 404")
      makeGetRequest((under(basePath) / s"nothing_$suffix").GET <@ (user1)).code should equal(404)

      When("the OpenAPI document is read")
      val document = makeGetRequest((under(basePath) / "openapi.json").GET)
      withClue(document.body) { document.code should equal(200) }
      Then("it carries the Domain API's title, version and server, and its paths")
      (document.body \ "info" \ "title").extract[String] should equal("Test Domain API")
      (document.body \ "info" \ "version").extract[String] should equal("1.0.0")
      (document.body \ "servers")(0) \ "url" match {
        case JString(url) => url should endWith(s"/$basePath")
        case other => fail(s"no server url: $other")
      }
      val paths = (document.body \ "paths") match { case JObject(fields) => fields.map(_._1); case _ => Nil }
      paths should contain(s"/${hyphenated(entity)}")
      paths should not contain (s"/$entity")
      paths should contain(s"/$queryPath/names")
      paths.exists(_.contains("dynamic-entities")) shouldBe false
      paths.exists(_.contains("banks")) shouldBe false
      makeGetRequest((under(basePath) / "openapi.yaml").GET).code should equal(200)

      When("its Resource Docs are read, without logging in")
      val resourceDocsAt = v7 / "banks" / SYS / "domain-apis" / "resource-docs"
      val resourceDocs = makeGetRequest(resourceDocsAt.GET <<? List("base_path" -> basePath))
      withClue(resourceDocs.body) { resourceDocs.code should equal(200) }
      Then("they are the OpenAPI document's endpoints, each at its full published path")
      val docUrls = (resourceDocs.body \ "resource_docs").children.map(doc =>
        ((doc \ "request_url").extract[String], (doc \ "specified_url").extract[String]))
      docUrls.map(_._1) should contain(s"/$basePath/${hyphenated(entity)}")
      docUrls.map(_._1) should contain(s"/$basePath/$queryPath/names")
      docUrls.foreach { case (requestUrl, specifiedUrl) =>
        requestUrl should startWith(s"/$basePath/")
        specifiedUrl should equal(requestUrl)
      }
      And("base_path is required, and must be a Domain API of that space")
      makeGetRequest(resourceDocsAt.GET).code should equal(400)
      val unknown = makeGetRequest(resourceDocsAt.GET <<? List("base_path" -> s"unknown-$suffix/v1"))
      unknown.code should equal(404)
      message(unknown) should include(DomainApiNotFound)
      makeGetRequest((v7 / "banks" / testBankId1.value / "domain-apis" / "resource-docs").GET <<? List("base_path" -> basePath)).code should equal(404)

      Then("the documentation is true to the endpoints: each documented path without a placeholder is served, and the documented example has the response's fields")
      val route = DomainApis.domainApiProvider.vend.routes().find(_.basePath == basePath)
        .getOrElse(fail("the previous scenario registers this Domain API"))
      val docs = Http4sDomainApi.publishedDocs(route).filter(_.requestVerb.equalsIgnoreCase("GET"))
      withClue(Http4sDomainApi.spaceDocs(route.bankId).map(d => s"${d.requestVerb} ${d.requestUrl} ${d.createdByBankId}").mkString("\n")) {
        docs should not be empty
      }
      docs.filterNot(_.requestUrl.split("/").exists(_.matches("[A-Z][A-Z0-9_]*"))).foreach { doc =>
        val response = makeGetRequest(doc.requestUrl.split("/").filter(_.nonEmpty).foldLeft(under(basePath))(_ / _).GET <@ (user1))
        withClue(s"GET ${doc.requestUrl} (${doc.partialFunctionName}): ${response.body}") {
          response.code should not equal (404)
          if (response.code == 200 && doc.partialFunctionName.toLowerCase.contains("dynamicentity")) {
            val documented = doc.successResponseBody match { case JObject(fields) => fields.map(_._1).toSet; case _ => Set.empty[String] }
            val answered = response.body match { case JObject(fields) => fields.map(_._1).toSet; case _ => Set.empty[String] }
            answered should equal(documented)
          }
        }
      }
    }

    scenario("the hyphenated form of a Dynamic Entity name answers what the underscored name answers") {
      val name = s"tech_practices_$suffix"
      val hyphenName = hyphenated(name)
      Given("an entity at SYS with personal, public and community access, holding one record whose id has hyphens")
      DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
        name, s"""{"$name":{"properties":{"name":{"type":"string","example":"no-till farming"}}}}""",
        None, owner, None, hasPersonalEntity = true, hasPublicAccess = true, hasCommunityAccess = true)).openOrThrowException("definition")
      val recordId = java.util.UUID.randomUUID().toString
      DynamicDataProvider.connectorMethodProvider.vend.save(None, name,
        JObject(JField(s"${name}_id", JString(recordId)), JField("name", JString("no-till farming"))), Some(owner), false).openOrThrowException("record")
      Entitlement.entitlement.vend.addEntitlement(SYS, resourceUser1.userId, s"CanGetDynamicEntityRecord_$name")
      And("a Dynamic Query whose own path has a hyphen")
      val queryWithHyphen = s"registry-$suffix"
      queryDoc(None, s"/$queryWithHyphen/activities-query", name, s"activitiesQuery$suffix")
      grantAllAt(SYS)
      val basePath = s"hyphens-$suffix/v1"
      val created = makePostRequest(domainApis(SYS).POST <@ (user1), registration(basePath, "1.0.0"))
      withClue(created.body) { created.code should equal(201) }

      def both(segments: String*)(call: code.setup.APIResponse => Unit = _ => ()): Unit = {
        def path(entityName: String) = segments.map(segment => if (segment == name) entityName else segment)
          .foldLeft(under(basePath))(_ / _).GET <@ (user1)
        val underscored = makeGetRequest(path(name))
        val withHyphens = makeGetRequest(path(hyphenName))
        withClue(s"${segments.mkString("/")}: ${withHyphens.body}") {
          withHyphens.code should not equal (404)
          withHyphens.code should equal(underscored.code)
          withHyphens.body should equal(underscored.body)
        }
        call(withHyphens)
      }

      Then("the list, a record by its id, my, public, community and a record's access answer the same in both spellings")
      both(name) { response =>
        response.code should equal(200)
        valuesOf(response.body, "name") should contain("no-till farming")
      }
      both(name, recordId) { response =>
        response.code should equal(200)
        valuesOf(response.body, s"${name}_id") should contain(recordId)
      }
      both("my", name)()
      both("public", name) { _.code should equal(200) }
      both("community", name) { _.code should equal(200) }
      both(name, recordId, "access")()

      And("a Dynamic Query whose path has hyphens still answers as the doc, not as an entity")
      val query = makeGetRequest((under(basePath) / queryWithHyphen / "activities-query").GET <@ (user1))
      withClue(query.body) { query.code should equal(200) }
      valuesOf(query.body \ "names", "name") should contain("no-till farming")

      And("the OpenAPI document lists each of the entity's paths once, with hyphens")
      val document = makeGetRequest((under(basePath) / "openapi.json").GET)
      withClue(document.body) { document.code should equal(200) }
      val paths = (document.body \ "paths") match { case JObject(fields) => fields.map(_._1); case _ => Nil }
      List(s"/$hyphenName", s"/public/$hyphenName", s"/community/$hyphenName", s"/my/$hyphenName").foreach { published =>
        paths should contain(published)
      }
      paths.exists(_.contains(name)) shouldBe false
      paths should contain(s"/$queryWithHyphen/activities-query")
    }

    scenario("every Dynamic Entity record operation works under a base path, in both spellings, at SYS and at a bank") {
      for (space <- List(SYS, testBankId1.value)) {
        val name = s"crud_${space.filter(_.isLetterOrDigit).take(6).toLowerCase}_$suffix"
        Given(s"an entity $name at $space with personal, public and community access, and a Domain API over $space")
        grantAt(space, canCreateDynamicEntityDefinition)
        val definition = compact(render(("entity_name" -> name) ~ ("has_personal_entity" -> true) ~
          ("has_public_access" -> true) ~ ("has_community_access" -> true) ~
          ("schema" -> (("description" -> "Records written under a Domain API.") ~ ("required" -> List("name")) ~
            ("properties" -> ("name" -> (("type" -> "string") ~ ("example" -> "a name"))))))))
        val defined = makePostRequest((v7 / "management" / "banks" / space / "dynamic-entities").POST <@ (user1), definition)
        withClue(defined.body) { defined.code should equal(201) }
        grantAllAt(space)
        val basePath = s"crud-${space.filter(_.isLetterOrDigit).take(6).toLowerCase}-$suffix/v1"
        val registered = makePostRequest(domainApis(space).POST <@ (user1), registration(basePath, "1.0.0"))
        withClue(registered.body) { registered.code should equal(201) }

        def at(segments: String*) = segments.foldLeft(under(basePath))(_ / _)
        def idOf(response: code.setup.APIResponse) = (response.body \ s"${name}_id") match {
          case JString(id) => id
          case other => fail(s"no ${name}_id in ${response.body}: $other")
        }
        def nameOf(response: code.setup.APIResponse) = (response.body \ "name") match {
          case JString(value) => value
          case other => fail(s"no name in ${response.body}: $other")
        }

        for (spelling <- List(name, hyphenated(name))) {
          When(s"a record is created, read, listed, replaced, patched and deleted at /$basePath/$spelling")
          val created = makePostRequest(at(spelling).POST <@ (user1), write(("name" -> "first"): JValue))
          withClue(s"POST $spelling: ${created.body}") { created.code should equal(201) }
          val recordId = idOf(created)

          val one = makeGetRequest(at(spelling, recordId).GET <@ (user1))
          withClue(s"GET $spelling/id: ${one.body}") { one.code should equal(200) }
          nameOf(one) should equal("first")

          val listed = makeGetRequest(at(spelling).GET <@ (user1))
          withClue(s"GET $spelling: ${listed.body}") { listed.code should equal(200) }
          valuesOf(listed.body, s"${name}_id") should contain(recordId)

          val replaced = makePutRequest(at(spelling, recordId).PUT <@ (user1), write(("name" -> "second"): JValue))
          withClue(s"PUT $spelling/id: ${replaced.body}") { replaced.code should equal(200) }
          nameOf(replaced) should equal("second")

          val patched = makePatchRequest(at(spelling, recordId).PATCH <@ (user1), write(("name" -> "third"): JValue))
          withClue(s"PATCH $spelling/id: ${patched.body}") { patched.code should equal(200) }
          nameOf(patched) should equal("third")

          Then("the OBP URL sees the same record")
          val obp = makeGetRequest((v7 / "banks" / space / "dynamic-entities" / name / recordId).GET <@ (user1))
          withClue(obp.body) { obp.code should equal(200) }
          nameOf(obp) should equal("third")

          When(s"the personal form /my/$spelling is used")
          val mine = makePostRequest(at("my", spelling).POST <@ (user1), write(("name" -> "mine"): JValue))
          withClue(s"POST my/$spelling: ${mine.body}") { mine.code should equal(201) }
          val myId = idOf(mine)
          val myList = makeGetRequest(at("my", spelling).GET <@ (user1))
          withClue(s"GET my/$spelling: ${myList.body}") { myList.code should equal(200) }
          valuesOf(myList.body, s"${name}_id") should contain(myId)
          val myPut = makePutRequest(at("my", spelling, myId).PUT <@ (user1), write(("name" -> "mine again"): JValue))
          withClue(s"PUT my/$spelling/id: ${myPut.body}") { myPut.code should equal(200) }
          val myDeleted = makeDeleteRequest(at("my", spelling, myId).DELETE <@ (user1))
          withClue(s"DELETE my/$spelling/id: ${myDeleted.body}") { myDeleted.code should equal(200) }

          And("the public and community forms list the record")
          val public = makeGetRequest(at("public", spelling).GET)
          withClue(s"GET public/$spelling: ${public.body}") { public.code should equal(200) }
          valuesOf(public.body, s"${name}_id") should contain(recordId)
          val community = makeGetRequest(at("community", spelling).GET <@ (user1))
          withClue(s"GET community/$spelling: ${community.body}") { community.code should equal(200) }

          Then("the record is deleted, and is gone")
          val deleted = makeDeleteRequest(at(spelling, recordId).DELETE <@ (user1))
          withClue(s"DELETE $spelling/id: ${deleted.body}") { deleted.code should equal(200) }
          makeGetRequest(at(spelling, recordId).GET <@ (user1)).code should equal(404)
        }
      }
    }

    scenario("a Domain API is refused while two endpoints of its space would answer the same path") {
      val bankId = testBankId2.value
      val clashing = s"clash_$suffix"
      entityWithRecord(Some(bankId), clashing, "clash")
      Given("a Dynamic Query at the path the entity's list is published at")
      queryDoc(Some(bankId), s"/$clashing", clashing, s"domainApiClash$suffix")
      grantAllAt(bankId)
      When("a Domain API is registered over that bank")
      val refused = makePostRequest(domainApis(bankId).POST <@ (user1), registration(s"clash-$suffix/v1", "1.0.0"))
      Then("it is refused, naming the clash")
      refused.code should equal(409)
      message(refused) should include(DomainApiPathClash)
      message(refused) should include(s"/$clashing")
    }
  }

  feature("Keeping a space's paths unambiguous, with or without a Domain API") {

    scenario("a Dynamic Resource Doc whose path starts with a Dynamic Entity's name is refused, in a space with no Domain API") {
      val bankId = testBankId1.value
      val name = s"owned_$suffix"
      entityWithRecord(Some(bankId), name, "owned")
      grantAt(bankId, canCreateBankLevelDynamicResourceDoc)

      When("a Dynamic Query is created at /<entity>/summary")
      val refused = makePostRequest(dynamicResourceDocsAt(bankId).POST <@ (user1), queryDocBody(bankId, s"/$name/summary", name, s"ownedSummary$suffix"))
      Then("it is refused, naming the entity")
      withClue(refused.body) { refused.code should equal(409) }
      message(refused) should include(DynamicPathAmbiguous)
      message(refused) should include(s"the Dynamic Entity $name")

      When("the same query is created at a path of its own")
      val accepted = makePostRequest(dynamicResourceDocsAt(bankId).POST <@ (user1), queryDocBody(bankId, s"/report_$suffix/summary", name, s"ownedReport$suffix"))
      Then("it is accepted")
      withClue(accepted.body) { accepted.code should equal(201) }
    }

    scenario("two Dynamic Resource Docs of one verb that would match one request are refused") {
      val bankId = testBankId1.value
      val name = s"listed_$suffix"
      entityWithRecord(Some(bankId), name, "listed")
      grantAt(bankId, canCreateBankLevelDynamicResourceDoc)
      grantAt(bankId, canUpdateBankLevelDynamicResourceDoc)
      val first = makePostRequest(dynamicResourceDocsAt(bankId).POST <@ (user1), queryDocBody(bankId, s"/sites_$suffix/SITE_ID", name, s"siteById$suffix"))
      withClue(first.body) { first.code should equal(201) }

      When("a second doc is created whose literal segment the first one's path variable also matches")
      val refused = makePostRequest(dynamicResourceDocsAt(bankId).POST <@ (user1), queryDocBody(bankId, s"/sites_$suffix/summary", name, s"siteSummary$suffix"))
      Then("it is refused, naming the first")
      withClue(refused.body) { refused.code should equal(409) }
      message(refused) should include(DynamicPathAmbiguous)
      message(refused) should include(s"siteById$suffix")

      When("a doc is moved onto such a path by an update")
      val other = makePostRequest(dynamicResourceDocsAt(bankId).POST <@ (user1), queryDocBody(bankId, s"/regions_$suffix/summary", name, s"regionSummary$suffix"))
      withClue(other.body) { other.code should equal(201) }
      val otherId = (other.body \ "dynamic_resource_doc_id").extract[String]
      val moved = makePutRequest((dynamicResourceDocsAt(bankId) / otherId).PUT <@ (user1), queryDocBody(bankId, s"/sites_$suffix/summary", name, s"regionSummary$suffix"))
      Then("it is refused too")
      withClue(moved.body) { moved.code should equal(409) }
      message(moved) should include(DynamicPathAmbiguous)
    }

    scenario("a Dynamic Entity named after the first segment of a Dynamic Resource Doc's path is refused") {
      val bankId = testBankId1.value
      val name = s"source_$suffix"
      val taken = s"taken_$suffix"
      entityWithRecord(Some(bankId), name, "source")
      grantAt(bankId, canCreateBankLevelDynamicResourceDoc)
      grantAt(bankId, canCreateDynamicEntityDefinition)
      val doc = makePostRequest(dynamicResourceDocsAt(bankId).POST <@ (user1), queryDocBody(bankId, s"/$taken/names", name, s"takenNames$suffix"))
      withClue(doc.body) { doc.code should equal(201) }

      When(s"a Dynamic Entity named $taken is created")
      val definition = compact(render(("entity_name" -> taken) ~ ("has_personal_entity" -> false) ~
        ("schema" -> (("description" -> "Taken.") ~ ("required" -> List("name")) ~
          ("properties" -> ("name" -> (("type" -> "string") ~ ("example" -> "a name"))))))))
      val refused = makePostRequest((baseRequest / "obp" / "v6.0.0" / "management" / "banks" / bankId / "dynamic-entities").POST <@ (user1), definition)
      Then("it is refused, naming the doc")
      withClue(refused.body) { refused.code should equal(409) }
      message(refused) should include(DynamicPathAmbiguous)
      message(refused) should include(s"/$taken/names")
    }

    scenario("the ambiguity rules read a hyphen in a path's first segment as an underscore") {
      val bankId = testBankId1.value
      val name = s"like_this_$suffix"
      entityWithRecord(Some(bankId), name, "like this")
      grantAt(bankId, canCreateBankLevelDynamicResourceDoc)
      grantAt(bankId, canCreateDynamicEntityDefinition)

      When(s"a Dynamic Query is created at the hyphenated form of the entity's name, /${hyphenated(name)}/summary")
      val refused = makePostRequest(dynamicResourceDocsAt(bankId).POST <@ (user1), queryDocBody(bankId, s"/${hyphenated(name)}/summary", name, s"likeThisSummary$suffix"))
      Then("it is refused, naming the entity")
      withClue(refused.body) { refused.code should equal(409) }
      message(refused) should include(DynamicPathAmbiguous)
      message(refused) should include(s"the Dynamic Entity $name")

      Given("a Dynamic Query at a hyphenated path no entity uses yet")
      val later = s"later_entity_$suffix"
      val doc = makePostRequest(dynamicResourceDocsAt(bankId).POST <@ (user1), queryDocBody(bankId, s"/${hyphenated(later)}/names", name, s"laterNames$suffix"))
      withClue(doc.body) { doc.code should equal(201) }
      When(s"a Dynamic Entity named $later is created")
      val definition = compact(render(("entity_name" -> later) ~ ("has_personal_entity" -> false) ~
        ("schema" -> (("description" -> "Later.") ~ ("required" -> List("name")) ~
          ("properties" -> ("name" -> (("type" -> "string") ~ ("example" -> "a name"))))))))
      val refusedEntity = makePostRequest((baseRequest / "obp" / "v6.0.0" / "management" / "banks" / bankId / "dynamic-entities").POST <@ (user1), definition)
      Then("it is refused, naming the doc")
      withClue(refusedEntity.body) { refusedEntity.code should equal(409) }
      message(refusedEntity) should include(DynamicPathAmbiguous)
      message(refusedEntity) should include(s"/${hyphenated(later)}/names")
    }

    scenario("a new Dynamic Entity name with a hyphen or a space is refused, also in v4.0.0") {
      val bankId = testBankId1.value
      grantAt(bankId, canCreateDynamicEntityDefinition)
      def v4Definition(entityName: String) = compact(render((entityName ->
        (("description" -> "A name with a hyphen.") ~ ("required" -> List("name")) ~
          ("properties" -> ("name" -> (("type" -> "string") ~ ("example" -> "a name")))))) ~ ("hasPersonalEntity" -> false)))
      val v4Entities = baseRequest / "obp" / "v4.0.0" / "management" / "banks" / bankId / "dynamic-entities"

      When("v4.0.0 is asked to create an entity whose name has a hyphen")
      val hyphen = makePostRequest(v4Entities.POST <@ (user1), v4Definition(s"with-hyphen-$suffix"))
      Then("it is refused")
      withClue(hyphen.body) { hyphen.code should equal(400) }
      message(hyphen) should include(DynamicEntityNameHasHyphenOrSpace)

      When("v4.0.0 is asked to create an entity whose name has a space")
      val space = makePostRequest(v4Entities.POST <@ (user1), v4Definition(s"with space $suffix"))
      Then("it is refused")
      space.code should equal(400)

      When("the same name with underscores is created")
      val accepted = makePostRequest(v4Entities.POST <@ (user1), v4Definition(s"with_hyphen_$suffix"))
      Then("it is accepted")
      withClue(accepted.body) { accepted.code should equal(201) }
    }

    scenario("under a Domain API a Dynamic Resource Doc is tried before a Dynamic Entity") {
      val bankId = testBankId1.value
      grantAllAt(bankId)
      val basePath = s"precedence-$suffix/v1"
      val created = makePostRequest(domainApis(bankId).POST <@ (user1), registration(basePath, "1.0.0"))
      withClue(created.body) { created.code should equal(201) }

      Given("an ambiguity written straight to the database, as one that predates the rules would be")
      val name = s"older_$suffix"
      entityWithRecord(Some(bankId), name, "older record")
      queryDoc(Some(bankId), s"/$name/summary", name, s"olderSummary$suffix")

      try {
        When("the doc's path is called under the base path, which the entity would read as a record id")
        val response = makeGetRequest((under(basePath) / name / "summary").GET <@ (user1))
        Then("the Dynamic Resource Doc answers")
        withClue(response.body) { response.code should equal(200) }
        valuesOf(response.body \ "names", "name") should contain("older record")
        And("the entity still answers its own paths")
        makeGetRequest((under(basePath) / name).GET <@ (user1)).code should equal(200)
      } finally {
        DynamicResourceDocProvider.provider.vend.getByVerbAndUrl(Some(bankId), "GET", s"/$name/summary")
          .foreach(doc => DynamicResourceDocProvider.provider.vend.deleteById(Some(bankId), doc.dynamicResourceDocId.getOrElse("")))
      }
    }
  }
}

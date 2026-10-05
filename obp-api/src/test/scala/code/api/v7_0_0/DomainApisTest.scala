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

  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  private val owner = "domain-api-owner"
  private val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(8)
  private val entity = s"activity_$suffix"
  private val queryPath = s"summary_$suffix"

  private def v7 = baseRequest / "obp" / "v7.0.0"
  private def domainApis(bankId: String) = v7 / "management" / "banks" / bankId / "domain-apis"
  private def registration(basePath: String, version: String, title: String = "Test Domain API") =
    compact(render(("base_path" -> basePath) ~ ("version" -> version) ~ ("title" -> title) ~ ("description" -> "For the test.")))
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
      paths should contain(s"/$entity")
      paths should contain(s"/$queryPath/names")
      paths.exists(_.contains("dynamic-entities")) shouldBe false
      paths.exists(_.contains("banks")) shouldBe false
      makeGetRequest((under(basePath) / "openapi.yaml").GET).code should equal(200)

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

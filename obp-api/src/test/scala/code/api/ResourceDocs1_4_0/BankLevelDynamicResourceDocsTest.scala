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
package code.api.ResourceDocs1_4_0

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.canReadDynamicResourceDocsAtOneBank
import code.api.util.ErrorMessages.{BankNotFound, UserHasMissingRoles}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.{ExampleValue, NewStyle}
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider, DynamicEntityT}
import code.entitlement.Entitlement
import code.setup.{DefaultUsers, PropsReset}
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * This suite covers the bank level resource-docs routes, /banks/BANK_ID/resource-docs/API_VERSION/
 * followed by obp, openapi or openapi.yaml.
 *
 * Each of them documents the dynamic things of one space only: the Dynamic Entities (and Dynamic
 * Endpoints and Dynamic Resource Docs) of one bank, or of the system space when BANK_ID is SYS. The
 * obp form used to ignore the bank after checking that it existed, and listed every space's dynamic
 * docs, so every scenario here checks that the other spaces' entities are absent as well as that the
 * requested space's entity is present.
 */
class BankLevelDynamicResourceDocsTest extends ResourceDocsV140ServerSetup with PropsReset with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v1_4_0.toString)
  object ObpFormat extends Tag("getBankLevelDynamicResourceDocsObp")
  object OpenApiFormat extends Tag("getBankLevelDynamicResourceDocsOpenAPI31")

  private val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(8)
  private val bankOneEntity = s"rdbankone$suffix"
  private val bankTwoEntity = s"rdbanktwo$suffix"
  private val systemEntity = s"rdsystem$suffix"
  private val allEntities = List(bankOneEntity, bankTwoEntity, systemEntity)

  private val requestedVersion = ApiVersion.v7_0_0.toString
  private def v7Request = baseRequest / "obp" / "v7.0.0"

  private def definition(entity: String, space: Option[String]): DynamicEntityCommons =
      DynamicEntityCommons(
        entityName = entity,
        metadataJson = s"""{"$entity":{"description":"An entity used by BankLevelDynamicResourceDocsTest.","required":[],"properties":{"name":{"type":"string","example":"Alice","description":"a name"}}}}""",
        dynamicEntityId = None,
        userId = resourceUser1.userId,
        bankId = space,
        hasPersonalEntity = false,
        hasCommunityAccess = true
      )

  private def register(entity: String, space: Option[String]): DynamicEntityT =
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(definition(entity, space))
      .openOrThrowException(s"could not register $entity")

  // The test setup empties every table before each scenario (LocalMappedConnectorTestSetup.wipeTestData),
  // so the entities are registered again for each one.
  override def beforeEach(): Unit = {
    super.beforeEach()
    register(bankOneEntity, Some(testBankId1.value))
    register(bankTwoEntity, Some(testBankId2.value))
    register(systemEntity, None)
  }

  /** Asserts the body names the one expected entity and none of the other spaces' entities. */
  private def shouldDocumentOnly(body: String, expected: String): Unit = {
    withClue(s"$expected should be documented. ") { body should include(expected) }
    allEntities.filterNot(_ == expected).foreach { other =>
      withClue(s"$other belongs to another space and should not be documented. ") { body should not include other }
    }
  }

  feature("A bank level resource-docs document holds only that space's dynamic things") {

    scenario("the obp format lists one bank's Dynamic Entities and no other space's", ObpFormat, VersionOfApi) {
      val response = makeGetRequest((v7Request / "banks" / testBankId1.value / "resource-docs" / requestedVersion / "obp").GET)
      response.code should equal(200)
      shouldDocumentOnly(response.body.toString, bankOneEntity)
    }

    scenario("the openapi format lists one bank's Dynamic Entities and no other space's", OpenApiFormat, VersionOfApi) {
      val response = makeGetRequest((v7Request / "banks" / testBankId2.value / "resource-docs" / requestedVersion / "openapi").GET)
      response.code should equal(200)
      (response.body \ "openapi").values.toString should startWith("3.1")
      shouldDocumentOnly(response.body.toString, bankTwoEntity)
    }

    scenario("the openapi.yaml format at SYS lists the system level Dynamic Entities only", OpenApiFormat, VersionOfApi) {
      val response = makeGetRequest((v7Request / "banks" / DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID / "resource-docs" / requestedVersion / "openapi.yaml").GET)
      response.code should equal(200)
      shouldDocumentOnly(response.body.toString, systemEntity)
    }

    scenario("the obp format at SYS lists the system level Dynamic Entities only", ObpFormat, VersionOfApi) {
      val response = makeGetRequest((v7Request / "banks" / DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID / "resource-docs" / requestedVersion / "obp").GET)
      response.code should equal(200)
      shouldDocumentOnly(response.body.toString, systemEntity)
    }
  }

  feature("A bank level resource-docs request is checked like the obp format always was") {

    scenario("an unknown bank is a 404", OpenApiFormat, VersionOfApi) {
      val response = makeGetRequest((v7Request / "banks" / s"no-such-bank-$suffix" / "resource-docs" / requestedVersion / "openapi").GET)
      response.code should equal(404)
      response.body.toString should include(BankNotFound)
    }

    scenario("an empty tags parameter is a 400", OpenApiFormat, VersionOfApi) {
      val response = makeGetRequest((v7Request / "banks" / testBankId1.value / "resource-docs" / requestedVersion / "openapi").GET <<? List(("tags", "")))
      response.code should equal(400)
      response.body.toString should include("OBP-10053")
    }

    scenario("when resource_docs_requires_role=true the role is needed at the requested space", OpenApiFormat, VersionOfApi) {
      setPropsValues("resource_docs_requires_role" -> "true")
      val request = (v7Request / "banks" / DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID / "resource-docs" / requestedVersion / "openapi").GET <@ (user1)

      Given("the caller holds the role at another bank only")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, canReadDynamicResourceDocsAtOneBank.toString)
      val refused = makeGetRequest(request)
      refused.code should equal(403)
      refused.body.toString should include(UserHasMissingRoles)

      Given("the caller holds the role at SYS")
      Entitlement.entitlement.vend.addEntitlement(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, resourceUser1.userId, canReadDynamicResourceDocsAtOneBank.toString)
      val allowed = makeGetRequest(request)
      allowed.code should equal(200)
      shouldDocumentOnly(allowed.body.toString, systemEntity)
    }
  }

  feature("An instance wide content=dynamic document is rebuilt when a Dynamic Entity is added") {

    // These documents used to be kept in the static swagger cache, which creating a Dynamic Entity
    // does not clear, so a new entity stayed missing from them for the rest of the cache TTL.
    scenario("the openapi content=dynamic document lists an entity created after it was first served", VersionOfApi) {
      val request = (v7Request / "resource-docs" / requestedVersion / "openapi").GET <<? List(("content", "dynamic"))
      val lateEntity = s"rdlate$suffix"

      Given("the document has been served, and so cached, before the entity exists")
      val before = makeGetRequest(request)
      before.code should equal(200)
      before.body.toString should not include lateEntity

      When("the entity is created through the same path the create endpoints use")
      Await.result(NewStyle.function.createOrUpdateDynamicEntity(definition(lateEntity, None), None), 30.seconds)
        .openOrThrowException(s"could not create $lateEntity")

      Then("the next request lists it")
      val after = makeGetRequest(request)
      after.code should equal(200)
      after.body.toString should include(lateEntity)
    }
  }

  feature("Adding a Dynamic Endpoint or a Dynamic Resource Doc clears the cached dynamic documents") {

    // Only Dynamic Entity changes used to clear the dynamic resource docs cache, so a new Dynamic
    // Endpoint or Dynamic Resource Doc stayed missing from a cached document for the rest of its TTL.
    def dynamicDocsRequest = (v7Request / "resource-docs" / requestedVersion / "obp").GET <<? List(("content", "dynamic"))

    scenario("a Dynamic Endpoint created after the document was served is listed", VersionOfApi) {
      val latePath = s"rdlateendpoint$suffix"

      Given("the document has been served, and so cached, before the endpoint exists")
      val before = makeGetRequest(dynamicDocsRequest)
      before.code should equal(200)
      before.body.toString should not include latePath

      When("the endpoint is created through the same path the create endpoints use")
      val swagger = ExampleValue.dynamicEndpointSwagger.replace("\"/accounts\"", s"\"/$latePath\"")
      Await.result(NewStyle.function.createDynamicEndpoint(None, resourceUser1.userId, swagger, None), 30.seconds)

      Then("the next request lists it")
      makeGetRequest(dynamicDocsRequest).body.toString should include(latePath)
    }

    scenario("a Dynamic Resource Doc created after the document was served is listed", VersionOfApi) {
      val latePath = s"rdlatedoc$suffix"

      Given("the document has been served, and so cached, before the doc exists")
      val before = makeGetRequest(dynamicDocsRequest)
      before.code should equal(200)
      before.body.toString should not include latePath

      When("the doc is created through the same path the create endpoints use")
      val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        bankId = None,
        dynamicResourceDocId = None,
        requestUrl = s"/$latePath/MY_USER_ID"
      )
      Await.result(NewStyle.function.createJsonDynamicResourceDoc(None, doc, None), 30.seconds)

      Then("the next request lists it")
      makeGetRequest(dynamicDocsRequest).body.toString should include(latePath)
    }
  }
}

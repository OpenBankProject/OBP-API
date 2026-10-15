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
package code.api.v4_0_0

import code.DynamicData.DynamicDataProvider
import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{canCreateDynamicEndpoint, canCreateEndpointMapping}
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.dynamicentitywebhook.DynamicEntityWebhooks
import code.entitlement.Entitlement
import code.messageoutbox.MessageOutbox
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

/**
 * This suite drives a Dynamic Endpoint whose host is `dynamic_entity` end to end: a swagger whose
 * operations are served from a Dynamic Entity's records through Endpoint Mappings, called at
 * /obp/dynamic-endpoint/... like any client would call it.
 *
 * It checks that a PUT updates the record its URL names, in place: the record keeps its id, its values
 * change, no second record appears, and the entity's webhooks see one UPDATED. A PUT used to delete the
 * record and create a new one with a new id, which a client holding the id lost track of, and which
 * webhooks saw as DELETED then CREATED.
 */
class DynamicEndpointBackedByDynamicEntityTest extends V400ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object DynamicEndpointPut extends Tag("dynamicEndpoint PUT backed by a Dynamic Entity")

  private val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(8)
  private val entityName = s"gadget_$suffix"
  private val idField = s"${entityName}_id"
  private val collectionPath = s"gadgets-$suffix"

  /** A swagger served from the Dynamic Entity: create, read one, update one. */
  private val swagger: String =
    s"""{
       |  "swagger": "2.0",
       |  "info": {"title": "Gadgets $suffix", "version": "1.0.0"},
       |  "definitions": {
       |    "Gadget": {
       |      "type": "object",
       |      "properties": {
       |        "gadget_id": {"type": "string", "example": "a1"},
       |        "name": {"type": "string", "example": "lamp"},
       |        "status": {"type": "string", "example": "OPEN"}
       |      }
       |    }
       |  },
       |  "paths": {
       |    "/$collectionPath": {
       |      "post": {
       |        "operationId": "POST_gadget_$suffix",
       |        "consumes": ["application/json"], "produces": ["application/json"],
       |        "parameters": [{"in": "body", "name": "body", "required": true, "schema": {"$$ref": "#/definitions/Gadget"}}],
       |        "responses": {"201": {"description": "Created", "schema": {"$$ref": "#/definitions/Gadget"}}},
       |        "summary": "Create a gadget $suffix"
       |      }
       |    },
       |    "/$collectionPath/{gadget_id}": {
       |      "get": {
       |        "operationId": "GET_gadget_$suffix",
       |        "produces": ["application/json"],
       |        "parameters": [{"in": "path", "name": "gadget_id", "required": true, "type": "string"}],
       |        "responses": {"200": {"description": "OK", "schema": {"$$ref": "#/definitions/Gadget"}}},
       |        "summary": "Get a gadget $suffix"
       |      },
       |      "put": {
       |        "operationId": "PUT_gadget_$suffix",
       |        "consumes": ["application/json"], "produces": ["application/json"],
       |        "parameters": [
       |          {"in": "path", "name": "gadget_id", "required": true, "type": "string"},
       |          {"in": "body", "name": "body", "required": true, "schema": {"$$ref": "#/definitions/Gadget"}}
       |        ],
       |        "responses": {"200": {"description": "OK", "schema": {"$$ref": "#/definitions/Gadget"}}},
       |        "summary": "Update a gadget $suffix"
       |      }
       |    }
       |  },
       |  "host": "dynamic_entity",
       |  "schemes": ["https"]
       |}""".stripMargin

  /** Each response field read from the entity, the record found by its id field. */
  private val responseMapping: JValue = {
    def from(field: String): JValue = ("entity" -> entityName) ~ ("field" -> field) ~ ("query" -> idField)
    ("gadget_id" -> from(idField)) ~ ("name" -> from("name")) ~ ("status" -> from("status"))
  }

  /** The entity's fields taken from the request body. */
  private val requestMapping: JValue = ("name" -> "name") ~ ("status" -> "status")

  /** The ResourceDoc OBP generated for one operation of the swagger. */
  private def generatedDoc(verb: String, url: String) =
    DynamicEndpointHelper.doc.find(doc => doc.requestVerb == verb && doc.requestUrl.endsWith(url))
      .getOrElse(fail(s"no generated ResourceDoc for $verb $url"))

  private def dynamicEndpoint(segments: String*) = segments.foldLeft(baseRequest / "obp" / "dynamic-endpoint")(_ / _)

  feature("A Dynamic Endpoint backed by a Dynamic Entity") {

    scenario("a PUT updates the record its URL names in place: same id, new values, one UPDATED", DynamicEndpointPut, VersionOfApi) {
      Given("a system level Dynamic Entity, and a webhook on it")
      val entityId = DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
        entityName,
        s"""{"$entityName":{"properties":{"name":{"type":"string","example":"lamp"},"status":{"type":"string","example":"OPEN"}}}}""",
        None, resourceUser1.userId, None, hasPersonalEntity = false)).openOrThrowException("entity").dynamicEntityId.get
      val webhook = DynamicEntityWebhooks.dynamicEntityWebhookProvider.vend.create(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID,
        entityId, entityName, s"https://receiver-$suffix.invalid/hook", List("CREATED", "UPDATED", "DELETED"), Nil,
        isActive = true, "", resourceUser1.userId).openOrThrowException("webhook")

      And("a Dynamic Endpoint served from it, with an Endpoint Mapping for each operation")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)
      val createdEndpoint = makePostRequest((v4_0_0_Request / "management" / "dynamic-endpoints").POST <@ (user1), swagger)
      withClue(createdEndpoint.body) { createdEndpoint.code should equal(201) }

      val postDoc = generatedDoc("POST", s"/$collectionPath")
      val getDoc = generatedDoc("GET", s"/$collectionPath/GADGET_ID")
      val putDoc = generatedDoc("PUT", s"/$collectionPath/GADGET_ID")

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateEndpointMapping.toString)
      List(postDoc, getDoc, putDoc).foreach { doc =>
        val mapping = compact(render(("operation_id" -> doc.operationId) ~
          ("request_mapping" -> requestMapping) ~ ("response_mapping" -> responseMapping)))
        val created = makePostRequest((v4_0_0_Request / "management" / "endpoint-mappings").POST <@ (user1), mapping)
        withClue(created.body) { created.code should equal(201) }
        doc.roles.toList.flatten.foreach(role => Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role.toString))
      }

      When("a gadget is created through the endpoint")
      val created = makePostRequest(dynamicEndpoint(collectionPath).POST <@ (user1),
        compact(render(("name" -> "lamp") ~ ("status" -> "OPEN"))))
      // Success, whichever code: the dispatcher answers 200 for every operation, not the swagger's 201.
      withClue(created.body) { List(200, 201) should contain(created.code) }
      val gadgetId = (created.body \ "gadget_id").extract[String]
      gadgetId should not be empty

      When("it is changed with a PUT")
      val updated = makePutRequest(dynamicEndpoint(collectionPath, gadgetId).PUT <@ (user1),
        compact(render(("name" -> "brighter lamp") ~ ("status" -> "CLOSED"))))
      withClue(updated.body) { updated.code should equal(200) }

      Then("the response is the same record, with the new values")
      (updated.body \ "gadget_id").extract[String] should equal(gadgetId)
      (updated.body \ "name").extract[String] should equal("brighter lamp")
      (updated.body \ "status").extract[String] should equal("CLOSED")

      And("reading it by its id gives the new values")
      val read = makeGetRequest(dynamicEndpoint(collectionPath, gadgetId).GET <@ (user1))
      withClue(read.body) { read.code should equal(200) }
      (read.body \ "status").extract[String] should equal("CLOSED")

      And("the entity still holds exactly one record, under the same id")
      val records = DynamicDataProvider.connectorMethodProvider.vend.getAllCommunity(None, entityName)
      records.flatMap(_.dynamicDataId) should equal(List(gadgetId))

      And("the webhook saw it created and then updated, never deleted")
      val queued = MessageOutbox.findAll(By(MessageOutbox.OutboxType, MessageOutbox.TYPE_WEBHOOK), By(MessageOutbox.TargetId, webhook.webhookId))
        .sortBy(_.id.get)
      queued.map(_.operationName) should equal(List("dynamic_entity.record.created", "dynamic_entity.record.updated"))
      queued.foreach(_.payloadJson should include(gadgetId))
    }
  }
}

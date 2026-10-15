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

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{canCreateDynamicEntityWebhook, canDeleteDynamicEntityWebhook, canGetDynamicEntityWebhooks, canUpdateDynamicEntityWebhook}
import code.api.util.ErrorMessages._
import code.api.v6_0_0.V600ServerSetup
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

/**
 * This suite checks the v7.0.0 endpoints that manage Dynamic Entity webhooks: the Roles at SYS and at a
 * bank, create, read, list, update and delete, the checks on events, field conditions, description and url,
 * the one-webhook-per-url rule, and how `is_active` and `deactivated_reason` move together on update.
 */
class DynamicEntityWebhooksTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("createDynamicEntityWebhook")
  object ApiEndpoint2 extends Tag("getDynamicEntityWebhooks")
  object ApiEndpoint3 extends Tag("getDynamicEntityWebhooksAtBank")
  object ApiEndpoint4 extends Tag("getDynamicEntityWebhook")
  object ApiEndpoint5 extends Tag("updateDynamicEntityWebhook")
  object ApiEndpoint6 extends Tag("deleteDynamicEntityWebhook")

  private val SYS = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  private val owner = "dynamic-entity-webhook-owner"
  private val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(8)

  // A host that does not resolve is accepted at registration (the delivery check refuses it later if it
  // resolves somewhere forbidden), so the test does not depend on the network.
  private def receiverUrl(path: String) = s"https://receiver-$suffix.invalid/$path"

  private def v7 = baseRequest / "obp" / "v7.0.0"
  private def webhooks(bankId: String, dynamicEntityId: String) =
    v7 / "management" / "banks" / bankId / "dynamic-entities" / dynamicEntityId / "web-hooks"
  private def webhooksAtBank(bankId: String) = v7 / "management" / "banks" / bankId / "dynamic-entity-web-hooks"

  private def message(response: code.setup.APIResponse) = response.body.extract[ErrorMessage].message
  private def webhookIdsIn(response: code.setup.APIResponse): List[String] =
    (response.body \ "web_hooks").children.map(webhook => (webhook \ "webhook_id").extract[String])
  private def grantAllAt(bankId: String): Unit =
    List(canCreateDynamicEntityWebhook, canGetDynamicEntityWebhooks, canUpdateDynamicEntityWebhook, canDeleteDynamicEntityWebhook)
      .foreach(role => Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, role.toString))

  /** A Dynamic Entity in a space (None = system) with a boolean, a number and a string field; its id. */
  private def entityIn(space: Option[String], entityName: String): String =
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
      entityName,
      s"""{"$entityName":{"properties":{
         |"approved":{"type":"boolean","example":"false"},
         |"amount":{"type":"number","example":"10.5"},
         |"status":{"type":"string","example":"OPEN"}}}}""".stripMargin,
      None, owner, space, hasPersonalEntity = false)).openOrThrowException("definition").dynamicEntityId.get

  private def webhookBody(url: String, events: List[String], conditions: Option[JValue] = None,
                          isActive: Option[Boolean] = None, description: Option[String] = None): String =
    compact(render(("url" -> url) ~ ("events" -> events) ~ ("field_conditions" -> conditions) ~
      ("is_active" -> isActive) ~ ("description" -> description)))

  private val approvedCondition: JValue = JArray(List(("field" -> "approved") ~ ("from" -> false) ~ ("to" -> true)))

  feature("Managing Dynamic Entity webhooks") {

    scenario("create, read, list, update and delete a webhook at SYS, with the Roles held at SYS",
      ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, ApiEndpoint5, ApiEndpoint6, VersionOfApi) {
      val entityId = entityIn(None, s"loan_$suffix")
      val url = receiverUrl("managed")
      val body = webhookBody(url, List("CREATED", "UPDATED"), Some(approvedCondition), description = Some("For the test."))

      When("an anonymous caller creates one")
      makePostRequest(webhooks(SYS, entityId).POST, body).code should equal(401)

      When("user1 creates one without the Role")
      val refused = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), body)
      refused.code should equal(403)
      message(refused) should include(UserHasMissingRoles)

      grantAllAt(SYS)

      When("user1 creates one with the Role at SYS")
      val created = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), body)
      withClue(created.body) { created.code should equal(201) }
      val webhookId = (created.body \ "webhook_id").extract[String]
      (created.body \ "bank_id").extract[String] should equal(SYS)
      (created.body \ "dynamic_entity_id").extract[String] should equal(entityId)
      (created.body \ "entity_name").extract[String] should equal(s"loan_$suffix")
      (created.body \ "url").extract[String] should equal(url)
      (created.body \ "events").extract[List[String]] should equal(List("CREATED", "UPDATED"))
      (created.body \ "field_conditions") should equal(approvedCondition)
      (created.body \ "is_active").extract[Boolean] should equal(true)
      (created.body \ "deactivated_reason") should equal(JNothing)
      (created.body \ "created_by_user_id").extract[String] should equal(resourceUser1.userId)
      List("signed", "not_configured") should contain((created.body \ "signature_status").extract[String])

      Then("it is listed for its entity and for its space, and can be read")
      val listed = makeGetRequest(webhooks(SYS, entityId).GET <@ (user1))
      listed.code should equal(200)
      webhookIdsIn(listed) should contain(webhookId)
      val atBank = makeGetRequest(webhooksAtBank(SYS).GET <@ (user1))
      atBank.code should equal(200)
      webhookIdsIn(atBank) should contain(webhookId)
      makeGetRequest((webhooks(SYS, entityId) / webhookId).GET <@ (user1)).code should equal(200)

      When("only its description is changed")
      val updated = makePutRequest((webhooks(SYS, entityId) / webhookId).PUT <@ (user1),
        compact(render("description" -> "Changed.")))
      withClue(updated.body) { updated.code should equal(200) }
      Then("everything left out keeps its value")
      (updated.body \ "description").extract[String] should equal("Changed.")
      (updated.body \ "url").extract[String] should equal(url)
      (updated.body \ "events").extract[List[String]] should equal(List("CREATED", "UPDATED"))
      (updated.body \ "field_conditions") should equal(approvedCondition)

      When("it is deleted")
      makeDeleteRequest((webhooks(SYS, entityId) / webhookId).DELETE <@ (user1)).code should equal(204)
      Then("it is gone")
      val gone = makeGetRequest((webhooks(SYS, entityId) / webhookId).GET <@ (user1))
      gone.code should equal(404)
      message(gone) should include(DynamicEntityWebhookNotFound)
    }

    scenario("a Role held at a bank covers that bank's entities, not the system space's", ApiEndpoint1, VersionOfApi) {
      val bankId = testBankId1.value
      val bankEntityId = entityIn(Some(bankId), s"bank_loan_$suffix")
      val systemEntityId = entityIn(None, s"sys_loan_$suffix")
      grantAllAt(bankId)

      val atBank = makePostRequest(webhooks(bankId, bankEntityId).POST <@ (user1), webhookBody(receiverUrl("bank"), List("CREATED")))
      withClue(atBank.body) { atBank.code should equal(201) }
      (atBank.body \ "bank_id").extract[String] should equal(bankId)

      Then("the bank's Role does not reach the system space")
      val atSystem = makePostRequest(webhooks(SYS, systemEntityId).POST <@ (user1), webhookBody(receiverUrl("bank"), List("CREATED")))
      atSystem.code should equal(403)

      Then("the system entity is not found through the bank")
      val throughBank = makePostRequest(webhooks(bankId, systemEntityId).POST <@ (user1), webhookBody(receiverUrl("other"), List("CREATED")))
      throughBank.code should equal(404)
      message(throughBank) should include(DynamicEntityNotFoundByDynamicEntityId)
    }

    scenario("an unknown entity or webhook is a 404", ApiEndpoint1, ApiEndpoint4, VersionOfApi) {
      grantAllAt(SYS)
      val unknownEntity = makePostRequest(webhooks(SYS, "no-such-entity").POST <@ (user1), webhookBody(receiverUrl("x"), List("CREATED")))
      unknownEntity.code should equal(404)
      message(unknownEntity) should include(DynamicEntityNotFoundByDynamicEntityId)

      val entityId = entityIn(None, s"lookup_$suffix")
      val unknownWebhook = makeGetRequest((webhooks(SYS, entityId) / "no-such-webhook").GET <@ (user1))
      unknownWebhook.code should equal(404)
      message(unknownWebhook) should include(DynamicEntityWebhookNotFound)
    }

    scenario("bad events, field conditions, description and urls are refused with a 400", ApiEndpoint1, VersionOfApi) {
      grantAllAt(SYS)
      val entityId = entityIn(None, s"checks_$suffix")
      def create(body: String) = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), body)
      def refusedWith(body: String, error: String): Unit = {
        val response = create(body)
        withClue(response.body) { response.code should equal(400) }
        message(response) should include(error)
      }

      When("the body is not JSON")
      refusedWith("not json", InvalidJsonFormat)

      When("events is empty, unknown or repeated")
      refusedWith(webhookBody(receiverUrl("e1"), Nil), InvalidDynamicEntityWebhookEvents)
      refusedWith(webhookBody(receiverUrl("e2"), List("READ")), InvalidDynamicEntityWebhookEvents)
      refusedWith(webhookBody(receiverUrl("e3"), List("CREATED", "CREATED")), InvalidDynamicEntityWebhookEvents)

      When("field conditions are given without UPDATED")
      refusedWith(webhookBody(receiverUrl("c1"), List("CREATED"), Some(approvedCondition)), InvalidDynamicEntityWebhookFieldCondition)
      When("a condition names a field the entity does not have")
      refusedWith(webhookBody(receiverUrl("c2"), List("UPDATED"), Some(JArray(List("field" -> "colour")))),
        InvalidDynamicEntityWebhookFieldCondition)
      When("a value does not fit the field's type")
      refusedWith(webhookBody(receiverUrl("c3"), List("UPDATED"), Some(JArray(List(("field" -> "amount") ~ ("to" -> "lots"))))),
        InvalidDynamicEntityWebhookFieldCondition)
      When("from and to are the same")
      refusedWith(webhookBody(receiverUrl("c4"), List("UPDATED"), Some(JArray(List(("field" -> "status") ~ ("from" -> "OPEN") ~ ("to" -> "OPEN"))))),
        InvalidDynamicEntityWebhookFieldCondition)
      When("a field is named twice")
      refusedWith(webhookBody(receiverUrl("c5"), List("UPDATED"), Some(JArray(List("field" -> "amount", "field" -> "amount")))),
        InvalidDynamicEntityWebhookFieldCondition)

      When("the description is too long")
      refusedWith(webhookBody(receiverUrl("d1"), List("CREATED"), description = Some("x" * 2001)), InvalidDynamicEntityWebhookDescription)

      When("the url is not https, carries a password, is relative, or points at a loopback or private address")
      refusedWith(webhookBody(s"http://receiver-$suffix.invalid/hook", List("CREATED")), InvalidDynamicEntityWebhookUrl)
      refusedWith(webhookBody(s"https://user:secret@receiver-$suffix.invalid/hook", List("CREATED")), InvalidDynamicEntityWebhookUrl)
      refusedWith(webhookBody("/hook", List("CREATED")), InvalidDynamicEntityWebhookUrl)
      refusedWith(webhookBody("https://127.0.0.1/hook", List("CREATED")), InvalidDynamicEntityWebhookUrl)
      refusedWith(webhookBody("https://10.1.2.3/hook", List("CREATED")), InvalidDynamicEntityWebhookUrl)
      refusedWith(webhookBody("https://169.254.169.254/latest/meta-data", List("CREATED")), InvalidDynamicEntityWebhookUrl)
      refusedWith(webhookBody("https://[::1]/hook", List("CREATED")), InvalidDynamicEntityWebhookUrl)

      Then("a condition with only a field, or only a to value of the right type, is accepted")
      val accepted = create(webhookBody(receiverUrl("ok"), List("UPDATED"),
        Some(JArray(List("field" -> "amount", ("field" -> "status") ~ ("to" -> "CLOSED"))))))
      withClue(accepted.body) { accepted.code should equal(201) }
    }

    scenario("an entity has at most one webhook per url", ApiEndpoint1, ApiEndpoint5, VersionOfApi) {
      grantAllAt(SYS)
      val entityId = entityIn(None, s"unique_$suffix")
      val first = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), webhookBody(receiverUrl("one"), List("CREATED")))
      withClue(first.body) { first.code should equal(201) }

      When("a second webhook has the same url")
      val duplicate = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), webhookBody(receiverUrl("one"), List("DELETED")))
      duplicate.code should equal(409)
      message(duplicate) should include(DynamicEntityWebhookUrlAlreadyExists)

      When("another webhook is moved onto that url")
      val second = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), webhookBody(receiverUrl("two"), List("DELETED")))
      withClue(second.body) { second.code should equal(201) }
      val secondId = (second.body \ "webhook_id").extract[String]
      val moved = makePutRequest((webhooks(SYS, entityId) / secondId).PUT <@ (user1), compact(render("url" -> receiverUrl("one"))))
      moved.code should equal(409)

      Then("a webhook may keep its own url on update")
      val kept = makePutRequest((webhooks(SYS, entityId) / secondId).PUT <@ (user1), compact(render("url" -> receiverUrl("two"))))
      withClue(kept.body) { kept.code should equal(200) }

      And("another entity may use the same url")
      val otherEntityId = entityIn(None, s"unique_other_$suffix")
      makePostRequest(webhooks(SYS, otherEntityId).POST <@ (user1), webhookBody(receiverUrl("one"), List("CREATED"))).code should equal(201)
    }

    scenario("turning a webhook off and on sets and clears its deactivated_reason", ApiEndpoint1, ApiEndpoint5, VersionOfApi) {
      grantAllAt(SYS)
      val entityId = entityIn(None, s"toggle_$suffix")
      def put(webhookId: String, body: JValue) = makePutRequest((webhooks(SYS, entityId) / webhookId).PUT <@ (user1), compact(render(body)))

      When("a webhook is created inactive")
      val created = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), webhookBody(receiverUrl("toggle"), List("CREATED"), isActive = Some(false)))
      withClue(created.body) { created.code should equal(201) }
      val webhookId = (created.body \ "webhook_id").extract[String]
      (created.body \ "is_active").extract[Boolean] should equal(false)
      (created.body \ "deactivated_reason").extract[String] should equal("TURNED_OFF")

      When("it is turned on")
      val on = put(webhookId, "is_active" -> true)
      withClue(on.body) { on.code should equal(200) }
      (on.body \ "is_active").extract[Boolean] should equal(true)
      (on.body \ "deactivated_reason") should equal(JNothing)

      When("it is turned off")
      val off = put(webhookId, "is_active" -> false)
      (off.body \ "is_active").extract[Boolean] should equal(false)
      (off.body \ "deactivated_reason").extract[String] should equal("TURNED_OFF")

      When("something else changes while it is off")
      val stillOff = put(webhookId, "description" -> "Still off.")
      (stillOff.body \ "is_active").extract[Boolean] should equal(false)
      (stillOff.body \ "deactivated_reason").extract[String] should equal("TURNED_OFF")
    }

    scenario("an empty field_conditions list removes the conditions on update", ApiEndpoint5, VersionOfApi) {
      grantAllAt(SYS)
      val entityId = entityIn(None, s"clear_$suffix")
      val created = makePostRequest(webhooks(SYS, entityId).POST <@ (user1),
        webhookBody(receiverUrl("clear"), List("UPDATED"), Some(approvedCondition)))
      withClue(created.body) { created.code should equal(201) }
      val webhookId = (created.body \ "webhook_id").extract[String]

      Then("events cannot drop UPDATED while conditions remain")
      val dropUpdated = makePutRequest((webhooks(SYS, entityId) / webhookId).PUT <@ (user1), compact(render("events" -> List("CREATED"))))
      dropUpdated.code should equal(400)
      message(dropUpdated) should include(InvalidDynamicEntityWebhookFieldCondition)

      When("the conditions are cleared at the same time")
      val cleared = makePutRequest((webhooks(SYS, entityId) / webhookId).PUT <@ (user1),
        compact(render(("events" -> List("CREATED")) ~ ("field_conditions" -> JArray(Nil)))))
      withClue(cleared.body) { cleared.code should equal(200) }
      (cleared.body \ "field_conditions") should equal(JArray(Nil))
    }
  }
}

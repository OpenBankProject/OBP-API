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
import code.messageoutbox.{MessageOutbox, MessageOutboxRelay, WebhookOutbox}
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.{JWSObject, Payload}
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import net.liftweb.mapper.By
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import scala.collection.JavaConverters._

/**
 * This suite checks the v7.0.0 endpoints that manage Dynamic Entity webhooks: the Roles at SYS and at a
 * bank, create, read, list, update and delete, the checks on events, field conditions, description and url,
 * the one-webhook-per-url rule, and how `is_active` and `deactivated_reason` move together on update.
 * It also delivers requests through the test endpoint and the message outbox to a receiver run by the
 * suite on this machine, to check the request OBP sends and which replies are retried, and checks that a
 * signature verifies against the public key the way a receiver would verify it.
 */
class DynamicEntityWebhooksTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("createDynamicEntityWebhook")
  object ApiEndpoint2 extends Tag("getDynamicEntityWebhooks")
  object ApiEndpoint3 extends Tag("getDynamicEntityWebhooksAtBank")
  object ApiEndpoint4 extends Tag("getDynamicEntityWebhook")
  object ApiEndpoint5 extends Tag("updateDynamicEntityWebhook")
  object ApiEndpoint6 extends Tag("deleteDynamicEntityWebhook")
  object ApiEndpoint7 extends Tag("testDynamicEntityWebhook")
  object ApiEndpoint8 extends Tag("getDynamicEntityWebhookDeliveries")
  object ApiEndpoint9 extends Tag("retryDynamicEntityWebhookDelivery")

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

  /** A request the suite's receiver got. */
  private case class Received(headers: Map[String, String], body: String)

  /**
   * A receiver on this machine that answers every request with `status` (and `location`, for a redirect)
   * and keeps what it got. Webhooks may only reach it while `webhook.allow_http` and
   * `webhook.allow_private_addresses` are set.
   */
  private class Receiver(status: Int, location: Option[String] = None) {
    val received = new java.util.concurrent.ConcurrentLinkedQueue[Received]()
    private val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", (exchange: HttpExchange) => {
      val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
      val headers = exchange.getRequestHeaders.asScala.map { case (name, values) => name.toLowerCase -> values.asScala.mkString(",") }.toMap
      received.add(Received(headers, body))
      location.foreach(exchange.getResponseHeaders.add("Location", _))
      val reply = s"""{"answer":$status}""".getBytes(StandardCharsets.UTF_8)
      exchange.sendResponseHeaders(status, reply.length.toLong)
      exchange.getResponseBody.write(reply)
      exchange.close()
    })
    server.start()
    val url = s"http://127.0.0.1:${server.getAddress.getPort}/hooks/obp"
    def stop(): Unit = server.stop(0)
  }

  private def allowLocalReceivers(): Unit =
    setPropsValues("webhook.allow_http" -> "true", "webhook.allow_private_addresses" -> "true")

  private def outboxRow(eventId: String): MessageOutbox =
    MessageOutbox.find(By(MessageOutbox.SubjectId, eventId)).openOrThrowException(s"outbox row $eventId")

  /**
   * One delivery attempt of the row, as the relay makes it. The relay running in the test server may have
   * made it already; the claim in the outbox lets only one of them send, so the result is the same.
   */
  private def deliverOnce(eventId: String): MessageOutbox = {
    val row = outboxRow(eventId)
    if (row.status == MessageOutbox.STATUS_PENDING && row.attempts == 0) MessageOutboxRelay.relayRow(row)
    outboxRow(eventId)
  }

  /** A webhook at SYS sending to `url`, and the event id of the test request queued for it. */
  private def webhookWithTestRequest(entityId: String, url: String): (String, String) = {
    val created = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), webhookBody(url, List("CREATED")))
    withClue(created.body) { created.code should equal(201) }
    val webhookId = (created.body \ "webhook_id").extract[String]
    val queued = makePostRequest((webhooks(SYS, entityId) / webhookId / "test").POST <@ (user1), "")
    withClue(queued.body) { queued.code should equal(201) }
    (webhookId, (queued.body \ "event_id").extract[String])
  }

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
  feature("Delivering Dynamic Entity webhook requests") {

    scenario("the test endpoint queues a webhook.test request, which the relay posts with the OBP headers",
      ApiEndpoint7, VersionOfApi) {
      allowLocalReceivers()
      grantAllAt(SYS)
      val entityName = s"delivered_$suffix"
      val entityId = entityIn(None, entityName)
      val receiver = new Receiver(200)
      try {
        When("an anonymous caller asks for a test request")
        val created = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), webhookBody(receiver.url, List("CREATED")))
        withClue(created.body) { created.code should equal(201) }
        val webhookId = (created.body \ "webhook_id").extract[String]
        makePostRequest((webhooks(SYS, entityId) / webhookId / "test").POST, "").code should equal(401)

        When("user1 asks for one")
        val queued = makePostRequest((webhooks(SYS, entityId) / webhookId / "test").POST <@ (user1), "")
        withClue(queued.body) { queued.code should equal(201) }
        val eventId = (queued.body \ "event_id").extract[String]
        (queued.body \ "event_name").extract[String] should equal("webhook.test")
        (queued.body \ "record_id") should equal(JNothing)

        When("the relay sends it")
        val row = deliverOnce(eventId)
        Then("it is delivered, once")
        withClue(row.LastError.get) { row.status should equal(MessageOutbox.STATUS_DELIVERED) }
        row.attempts should equal(1)
        WebhookOutbox.lastReply(row).map(_.http_status) should equal(Some(200))
        receiver.received.size should equal(1)

        And("the request carries the OBP headers and identifiers only")
        val request = receiver.received.peek()
        request.headers("obp-webhook-id") should equal(webhookId)
        request.headers("obp-event-id") should equal(eventId)
        request.headers("obp-delivery-attempt") should equal("1")
        request.headers("obp-signature-status") should equal(WebhookOutbox.signatureStatus)
        request.headers.get("obp-signature").isDefined should equal(WebhookOutbox.isSigningConfigured)
        request.headers("content-type") should startWith("application/json")
        val body = org.json4s.native.JsonMethods.parse(request.body)
        (body \ "event_name").extract[String] should equal("webhook.test")
        (body \ "event_id").extract[String] should equal(eventId)
        (body \ "webhook_id").extract[String] should equal(webhookId)
        (body \ "bank_id").extract[String] should equal(SYS)
        (body \ "entity_name").extract[String] should equal(entityName)
        (body \ "occurred_at").extract[String] should fullyMatch regex """\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z"""
        (body \ "record_id") should equal(JNothing)
      } finally receiver.stop()
    }

    scenario("a 503 reply is retried, and deleting the webhook drops the request", ApiEndpoint6, ApiEndpoint7, VersionOfApi) {
      allowLocalReceivers()
      grantAllAt(SYS)
      val entityId = entityIn(None, s"unavailable_$suffix")
      val receiver = new Receiver(503)
      try {
        val (webhookId, eventId) = webhookWithTestRequest(entityId, receiver.url)
        val row = deliverOnce(eventId)
        Then("it stays PENDING, with the attempt and the reply recorded")
        row.status should equal(MessageOutbox.STATUS_PENDING)
        row.attempts should equal(1)
        WebhookOutbox.lastReply(row).map(_.http_status) should equal(Some(503))
        row.LastError.get should include("503")

        When("the webhook is deleted")
        makeDeleteRequest((webhooks(SYS, entityId) / webhookId).DELETE <@ (user1)).code should equal(204)
        Then("the request is no longer queued")
        MessageOutbox.find(By(MessageOutbox.SubjectId, eventId)).isDefined should equal(false)
      } finally receiver.stop()
    }

    scenario("a 400 reply or a redirect is not retried", ApiEndpoint7, VersionOfApi) {
      allowLocalReceivers()
      grantAllAt(SYS)
      val entityId = entityIn(None, s"refusing_$suffix")
      val refusing = new Receiver(400)
      val redirecting = new Receiver(302, location = Some("http://169.254.169.254/latest/meta-data"))
      try {
        val (_, refusedEventId) = webhookWithTestRequest(entityId, refusing.url)
        val refused = deliverOnce(refusedEventId)
        refused.status should equal(MessageOutbox.STATUS_STICKY)
        WebhookOutbox.lastReply(refused).map(_.http_status) should equal(Some(400))

        val (_, redirectedEventId) = webhookWithTestRequest(entityId, redirecting.url)
        val redirected = deliverOnce(redirectedEventId)
        redirected.status should equal(MessageOutbox.STATUS_STICKY)
        WebhookOutbox.lastReply(redirected).map(_.http_status) should equal(Some(302))
        Then("the redirect was not followed")
        redirecting.received.size should equal(1)
      } finally { refusing.stop(); redirecting.stop() }
    }

    scenario("a url that is refused by the time of delivery is not sent to", ApiEndpoint7, VersionOfApi) {
      allowLocalReceivers()
      grantAllAt(SYS)
      val entityId = entityIn(None, s"refused_later_$suffix")
      val receiver = new Receiver(200)
      try {
        val (_, eventId) = webhookWithTestRequest(entityId, receiver.url)
        When("private addresses are no longer allowed when the request is sent")
        setPropsValues("webhook.allow_private_addresses" -> "false")
        val row = deliverOnce(eventId)
        Then("the request is not sent, and is not retried")
        row.status should equal(MessageOutbox.STATUS_STICKY)
        row.LastError.get should include("refused")
        receiver.received.size should equal(0)
      } finally receiver.stop()
    }
  }

  feature("Following and retrying Dynamic Entity webhook deliveries") {

    scenario("a STICKY delivery is listed, can be retried once, and goes back to PENDING with its attempts reset",
      ApiEndpoint8, ApiEndpoint9, VersionOfApi) {
      allowLocalReceivers()
      grantAllAt(SYS)
      val entityId = entityIn(None, s"followed_$suffix")
      val refusing = new Receiver(400)
      try {
        val (webhookId, eventId) = webhookWithTestRequest(entityId, refusing.url)
        deliverOnce(eventId).status should equal(MessageOutbox.STATUS_STICKY)
        val deliveries = webhooks(SYS, entityId) / webhookId / "deliveries"

        When("an anonymous caller lists the deliveries")
        makeGetRequest(deliveries.GET).code should equal(401)

        When("user1 lists them")
        val listed = makeGetRequest(deliveries.GET <@ (user1))
        withClue(listed.body) { listed.code should equal(200) }
        val delivery = (listed.body \ "deliveries").children.find(d => (d \ "event_id").extract[String] == eventId)
          .getOrElse(fail(s"delivery $eventId is not listed"))
        (delivery \ "event_name").extract[String] should equal("webhook.test")
        (delivery \ "status").extract[String] should equal("STICKY")
        (delivery \ "attempts").extract[Int] should equal(1)
        (delivery \ "last_http_status").extract[Int] should equal(400)
        (delivery \ "last_error").extract[String] should include("400")
        (delivery \ "record_id") should equal(JNothing)

        Then("the status filter narrows the list, and an unknown status is refused")
        (makeGetRequest(deliveries.GET <<? List("status" -> "STICKY") <@ (user1)).body \ "deliveries").children should have size 1
        (makeGetRequest(deliveries.GET <<? List("status" -> "DELIVERED") <@ (user1)).body \ "deliveries").children should have size 0
        val badStatus = makeGetRequest(deliveries.GET <<? List("status" -> "LOST") <@ (user1))
        badStatus.code should equal(400)
        message(badStatus) should include(InvalidUrlParameters)

        When("it is retried")
        val retried = makePostRequest((deliveries / eventId / "retry").POST <@ (user1), "")
        withClue(retried.body) { retried.code should equal(200) }
        Then("it is PENDING again, with its attempts reset and the same event id")
        (retried.body \ "status").extract[String] should equal("PENDING")
        (retried.body \ "attempts").extract[Int] should equal(0)
        (retried.body \ "event_id").extract[String] should equal(eventId)

        Then("a delivery that is not STICKY cannot be retried")
        // The relay running in the test server may have sent it again already; either way it is not STICKY
        // until that attempt has been made and refused, so retry the row only while it is PENDING.
        if (outboxRow(eventId).status == MessageOutbox.STATUS_PENDING) {
          val again = makePostRequest((deliveries / eventId / "retry").POST <@ (user1), "")
          again.code should equal(400)
          message(again) should include(DynamicEntityWebhookDeliveryNotSticky)
        }

        And("an unknown delivery is a 404")
        val unknown = makePostRequest((deliveries / "no-such-event" / "retry").POST <@ (user1), "")
        unknown.code should equal(404)
        message(unknown) should include(DynamicEntityWebhookDeliveryNotFound)
      } finally refusing.stop()
    }
  }

  feature("Keeping webhooks in step with their Dynamic Entity's definition") {

    /** This saves the definition `dynamicEntityId` again, as `entityName` with `properties`, as every definition endpoint does. */
    def redefine(dynamicEntityId: String, entityName: String, properties: String): Unit =
      DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
        entityName, s"""{"$entityName":{"properties":$properties}}""", Some(dynamicEntityId), owner, None,
        hasPersonalEntity = false)).openOrThrowException("redefinition")

    def webhookAt(entityId: String, webhookId: String): JValue = {
      val response = makeGetRequest((webhooks(SYS, entityId) / webhookId).GET <@ (user1))
      withClue(response.body) { response.code should equal(200) }
      response.body
    }

    def create(entityId: String, body: String): String = {
      val created = makePostRequest(webhooks(SYS, entityId).POST <@ (user1), body)
      withClue(created.body) { created.code should equal(201) }
      (created.body \ "webhook_id").extract[String]
    }

    scenario("removing or retyping a field a condition names turns the webhook off; other webhooks are left alone",
      ApiEndpoint4, ApiEndpoint5, VersionOfApi) {
      grantAllAt(SYS)
      val entityName = s"redefined_$suffix"
      val entityId = entityIn(None, entityName)
      val onApproved = create(entityId, webhookBody(receiverUrl("approved"), List("UPDATED"), Some(approvedCondition)))
      val onAmount = create(entityId, webhookBody(receiverUrl("amount"), List("UPDATED"), Some(JArray(List("field" -> "amount")))))
      val unconditional = create(entityId, webhookBody(receiverUrl("all"), List("CREATED", "UPDATED")))
      val alreadyOff = create(entityId, webhookBody(receiverUrl("off"), List("UPDATED"), Some(approvedCondition), isActive = Some(false)))

      When("the definition drops approved")
      redefine(entityId, entityName, """{"amount":{"type":"number","example":"1"},"status":{"type":"string","example":"OPEN"}}""")
      Then("the active webhook whose condition names approved is turned off, saying why")
      (webhookAt(entityId, onApproved) \ "is_active").extract[Boolean] should equal(false)
      (webhookAt(entityId, onApproved) \ "deactivated_reason").extract[String] should equal("ENTITY_FIELD_REMOVED")
      And("the others are as they were")
      (webhookAt(entityId, onAmount) \ "is_active").extract[Boolean] should equal(true)
      (webhookAt(entityId, unconditional) \ "is_active").extract[Boolean] should equal(true)
      (webhookAt(entityId, alreadyOff) \ "deactivated_reason").extract[String] should equal("TURNED_OFF")

      When("the definition changes amount from a number to a string")
      redefine(entityId, entityName, """{"amount":{"type":"string","example":"1"},"status":{"type":"string","example":"OPEN"}}""")
      Then("the webhook whose condition names amount is turned off, saying why")
      (webhookAt(entityId, onAmount) \ "deactivated_reason").extract[String] should equal("ENTITY_FIELD_TYPE_CHANGED")
      (webhookAt(entityId, unconditional) \ "is_active").extract[Boolean] should equal(true)

      When("the owner turns the first back on without fixing its condition")
      val unfixed = makePutRequest((webhooks(SYS, entityId) / onApproved).PUT <@ (user1), compact(render("is_active" -> true)))
      Then("it is refused")
      unfixed.code should equal(400)
      message(unfixed) should include(InvalidDynamicEntityWebhookFieldCondition)

      When("the owner fixes the condition and turns it back on")
      val fixed = makePutRequest((webhooks(SYS, entityId) / onApproved).PUT <@ (user1),
        compact(render(("is_active" -> true) ~ ("field_conditions" -> JArray(List(("field" -> "status") ~ ("to" -> "CLOSED")))))))
      withClue(fixed.body) { fixed.code should equal(200) }
      (fixed.body \ "is_active").extract[Boolean] should equal(true)
      (fixed.body \ "deactivated_reason") should equal(JNothing)
    }

    scenario("renaming an entity renames it on its webhooks, and deleting it deletes them", ApiEndpoint3, VersionOfApi) {
      grantAllAt(SYS)
      val entityId = entityIn(None, s"renamed_$suffix")
      val webhookId = create(entityId, webhookBody(receiverUrl("renamed"), List("CREATED")))

      When("the entity is renamed")
      redefine(entityId, s"renamed_again_$suffix",
        """{"approved":{"type":"boolean","example":"false"},"amount":{"type":"number","example":"1"},"status":{"type":"string","example":"OPEN"}}""")
      Then("its webhook carries the new name")
      (webhookAt(entityId, webhookId) \ "entity_name").extract[String] should equal(s"renamed_again_$suffix")

      When("the entity is deleted")
      val entity = DynamicEntityProvider.connectorMethodProvider.vend.getById(None, entityId).openOrThrowException("entity")
      DynamicEntityProvider.connectorMethodProvider.vend.delete(entity).openOrThrowException("delete") should equal(true)
      Then("its webhook is gone")
      webhookIdsIn(makeGetRequest(webhooksAtBank(SYS).GET <@ (user1))) should not contain webhookId
    }
  }

  feature("Signing Dynamic Entity webhook requests") {

    scenario("a signature verifies against the public key and the exact body, and nothing else", VersionOfApi) {
      val generator = KeyPairGenerator.getInstance("RSA")
      generator.initialize(2048)
      val keyPair = generator.generateKeyPair()
      val publicKey = keyPair.getPublic.asInstanceOf[RSAPublicKey]
      val body = """{"event_name":"webhook.test","event_id":"e1"}""".getBytes(StandardCharsets.UTF_8)

      val signature = WebhookOutbox.sign(body, keyPair.getPrivate.asInstanceOf[RSAPrivateKey], publicKey, 1760000000L)

      Then("its payload part is empty: the body is the payload")
      signature.split("\\.", -1).toList.map(_.isEmpty) should equal(List(false, true, false))
      And("it verifies against the body, and names the key and its own type")
      val verified = JWSObject.parse(signature, new Payload(body))
      verified.verify(new RSASSAVerifier(publicKey)) should equal(true)
      verified.getHeader.getKeyID should equal(new RSAKey.Builder(publicKey).keyIDFromThumbprint().build().getKeyID)
      verified.getHeader.getType.getType should equal("obp-webhook+jwt")
      verified.getHeader.getCustomParam("iat").toString should equal("1760000000")
      And("it does not verify against any other body")
      val tampered = JWSObject.parse(signature, new Payload("""{"event_name":"webhook.test","event_id":"e2"}"""))
      tampered.verify(new RSASSAVerifier(publicKey)) should equal(false)
    }
  }
}

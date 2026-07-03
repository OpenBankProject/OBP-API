package code.api.v7_0_0

import org.json4s._
import code.Http4sTestServer
import code.api.util.ApiRole.{canCreateEntitlementAtAnyBank, canDeleteEntitlementAtAnyBank}
import code.entitlement.Entitlement
import code.setup.{OBPReq, ServerSetupWithTestData}
import org.json4s.JsonAST.{JObject, JString}
import com.openbankproject.commons.util.JsonAliases.parse
import org.json4s.JValue
import org.scalatest.Tag
import scala.collection.JavaConverters._

/**
 * Integration tests for the v7 request-scoped transaction feature.
 *
 * Each mutating HTTP request handled by the http4s stack runs inside
 * `ResourceDocMiddleware.withBusinessDBTransaction`, which:
 *   - Lazily borrows one real JDBC connection from HikariCP on the first DB call
 *     (endpoints that only call external REST/SOAP connectors never touch the pool)
 *   - Wraps it in a non-closing proxy so Lift Mapper cannot commit early
 *   - Commits on Outcome.Succeeded (HTTP 2xx or error response)
 *   - Rolls back on Outcome.Errored / Outcome.Canceled (uncaught exception)
 *
 * These tests exercise the observable guarantee: data written inside a
 * successful request is durably committed; the connection is returned to the
 * pool so subsequent requests can proceed.
 *
 * Commit-on-success is the primary path tested here.  Rollback is only
 * triggered by an uncaught IO exception (not by a 4xx business-logic
 * response), so it is verified indirectly: a 4xx response that reaches the
 * client means the IO succeeded, the connection was committed and released,
 * and the pool is still healthy.
 */
class Http4s700TransactionTest extends ServerSetupWithTestData {

  object Http4s700TransactionTag extends Tag("Http4s700Transaction")

  private val http4sServer = Http4sTestServer
  private val baseUrl      = s"http://${http4sServer.host}:${http4sServer.port}"

  // ─── HTTP helpers ────────────────────────────────────────────────────────────

  private def execAndParse(req: OBPReq): (Int, JValue, Map[String, String]) = {
    val (code, bodyStr, okHdrs) = req.executeRaw()
    val json = if (bodyStr.trim.isEmpty) JObject(Nil) else parse(bodyStr)
    val hdrs = okHdrs.toMultimap.asScala.map { case (k, vs) => k -> vs.asScala.mkString(",") }.toMap
    (code, json, hdrs)
  }

  private def makeHttpRequest(
    path: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = {
    val req = headers.foldLeft(OBPReq.url(s"$baseUrl$path").addHeader("Accept", "*/*")) {
      case (r, (k, v)) => r.addHeader(k, v)
    }
    execAndParse(req)
  }

  private def makeHttpRequestWithBody(
    method: String,
    path: String,
    body: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = {
    val base = OBPReq.url(s"$baseUrl$path").addHeader("Accept", "*/*").addHeader("Content-Type", "application/json")
    val withHdr = headers.foldLeft(base) { case (r, (k, v)) => r.addHeader(k, v) }
    val req = method.toUpperCase match {
      case "POST" => withHdr.POST << body
      case "PUT"  => withHdr.PUT  << body
      case _      => withHdr << body
    }
    execAndParse(req)
  }

  private def makeHttpRequestWithMethod(
    method: String,
    path: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = {
    val base = OBPReq.url(s"$baseUrl$path").addHeader("Accept", "*/*")
    val withHdr = headers.foldLeft(base) { case (r, (k, v)) => r.addHeader(k, v) }
    val req = method.toUpperCase match {
      case "DELETE" => withHdr.DELETE
      case "POST"   => withHdr.POST
      case _        => withHdr
    }
    execAndParse(req)
  }

  private def entitlementIdFromJson(json: JValue): String =
    json match {
      case JObject(fields) =>
        fields.collectFirst { case (name, value) if name == "entitlement_id" =>
          value.asInstanceOf[JString].s
        }.getOrElse(fail("Expected entitlement_id in response"))
      case _ => fail("Expected JSON object in response")
    }

  // ─── Commit on successful write ───────────────────────────────────────────

  feature("v7 transaction — commit on successful write") {

    scenario("POST addEntitlement → 201: created row is durable in the DB", Http4s700TransactionTag) {
      Given("canCreateEntitlementAtAnyBank granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canCreateEntitlementAtAnyBank.toString)

      When("POST /obp/v7.0.0/users/USER_ID/entitlements returns 201")
      val roleName = "CanGetAnyUser"
      val body     = s"""{"bank_id":"","role_name":"$roleName"}"""
      val headers  = Map("DirectLogin" -> s"token=${token1.value}")
      val (status, json, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/users/${resourceUser1.userId}/entitlements", body, headers)

      status shouldBe 201

      Then("The entitlement_id from the response is readable directly from the DB")
      val entitlementId = entitlementIdFromJson(json)
      val fromDb = Entitlement.entitlement.vend.getEntitlementById(entitlementId)
      fromDb.isDefined shouldBe true

      And("The stored row has the expected role and user")
      fromDb.foreach { e =>
        e.roleName shouldBe roleName
        e.userId   shouldBe resourceUser1.userId
      }
    }

    scenario("POST addEntitlement: a second request after the first can read committed data", Http4s700TransactionTag) {
      Given("canCreateEntitlementAtAnyBank and canDeleteEntitlementAtAnyBank granted")
      addEntitlement("", resourceUser1.userId, canCreateEntitlementAtAnyBank.toString)
      addEntitlement("", resourceUser1.userId, canDeleteEntitlementAtAnyBank.toString)

      When("Request 1 — POST creates a CanGetCardsForBank entitlement")
      val body    = s"""{"bank_id":"${testBankId1.value}","role_name":"CanGetCardsForBank"}"""
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (status1, json1, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/users/${resourceUser1.userId}/entitlements", body, headers)
      status1 shouldBe 201
      val createdId = entitlementIdFromJson(json1)

      And("Request 2 — DELETE removes the entitlement just created")
      val (status2, _, _) = makeHttpRequestWithMethod(
        "DELETE", s"/obp/v7.0.0/entitlements/$createdId", headers)

      Then("The DELETE sees the row committed by the POST (returns 204, not 404)")
      status2 shouldBe 204
    }
  }

  // ─── Commit on successful delete ─────────────────────────────────────────

  feature("v7 transaction — commit on successful delete") {

    scenario("DELETE deleteEntitlement → 204: row is gone from the DB", Http4s700TransactionTag) {
      Given("canDeleteEntitlementAtAnyBank granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canDeleteEntitlementAtAnyBank.toString)

      And("A target entitlement created directly in the DB")
      val target = Entitlement.entitlement.vend
        .addEntitlement(testBankId1.value, resourceUser1.userId, "CanGetCardsForBank")
        .openOrThrowException("Expected entitlement to be created for DELETE test")

      When(s"DELETE /obp/v7.0.0/entitlements/${target.entitlementId} returns 204")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (status, _, _) = makeHttpRequestWithMethod(
        "DELETE", s"/obp/v7.0.0/entitlements/${target.entitlementId}", headers)

      status shouldBe 204

      Then("The row is no longer readable from the DB — the DELETE was committed")
      val afterDelete = Entitlement.entitlement.vend.getEntitlementById(target.entitlementId)
      afterDelete.isDefined shouldBe false
    }
  }

  // ─── Connection pool health ───────────────────────────────────────────────

  feature("v7 transaction — connection pool health across multiple requests") {

    scenario("Ten sequential requests all succeed — connections are returned to the pool", Http4s700TransactionTag) {
      Given("canCreateEntitlementAtAnyBank granted to resourceUser1")
      addEntitlement("", resourceUser1.userId, canCreateEntitlementAtAnyBank.toString)
      addEntitlement("", resourceUser1.userId, canDeleteEntitlementAtAnyBank.toString)

      val headers = Map("DirectLogin" -> s"token=${token1.value}")

      When("10 sequential POST + DELETE pairs are executed")
      val uniqueRole = "CanGetAnyUser"
      var allStatuses = List.empty[Int]

      (1 to 10).foreach { _ =>
        val body = s"""{"bank_id":"","role_name":"$uniqueRole"}"""
        val (postStatus, postJson, _) = makeHttpRequestWithBody(
          "POST", s"/obp/v7.0.0/users/${resourceUser1.userId}/entitlements", body, headers)
        allStatuses :+= postStatus

        if (postStatus == 201) {
          val eid = entitlementIdFromJson(postJson)
          val (delStatus, _, _) = makeHttpRequestWithMethod(
            "DELETE", s"/obp/v7.0.0/entitlements/$eid", headers)
          allStatuses :+= delStatus
        }
      }

      Then("All POST responses are 201 and all DELETE responses are 204")
      val postStatuses   = allStatuses.zipWithIndex.collect { case (s, i) if i % 2 == 0 => s }
      val deleteStatuses = allStatuses.zipWithIndex.collect { case (s, i) if i % 2 == 1 => s }
      postStatuses.forall(_ == 201) shouldBe true
      deleteStatuses.forall(_ == 204) shouldBe true
    }

    scenario("A 4xx error response does not exhaust the connection pool", Http4s700TransactionTag) {
      Given("An unauthenticated POST request that will return 401")
      val body = s"""{"bank_id":"","role_name":"CanGetAnyUser"}"""
      val (unauthStatus, _, _) = makeHttpRequestWithBody(
        "POST", s"/obp/v7.0.0/users/${resourceUser1.userId}/entitlements", body)

      When("The unauthenticated request returns 401")
      unauthStatus shouldBe 401

      Then("A subsequent public request still works — the pool was not leaked by the 401 path")
      val (banksStatus, _, _) = makeHttpRequest("/obp/v7.0.0/banks")
      banksStatus shouldBe 200
    }
  }

  // ── Rollback on uncaught exception ───────────────────────────────────────

  feature("v7 transaction — rollback on uncaught exception") {

    scenario("Uncaught IO exception triggers rollback — write is not committed", Http4s700TransactionTag) {
      Given("No TestRollbackSentinel entitlement exists for resourceUser1 before the request")
      val before = Entitlement.entitlement.vend.getEntitlementsByUserId(resourceUser1.userId)
        .map(_.filter(_.roleName == "TestRollbackSentinel"))
        .openOr(Nil)
      before shouldBe empty

      When("POST /obp/v7.0.0/test/rollback-check raises an uncaught IO error after writing")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (status, _, _) = makeHttpRequestWithBody(
        "POST", "/obp/v7.0.0/test/rollback-check", "{}", headers)

      Then("The server returns 500 (IO error propagated through the stack)")
      status shouldBe 500

      And("The TestRollbackSentinel row is NOT in the DB — the transaction was rolled back")
      val after = Entitlement.entitlement.vend.getEntitlementsByUserId(resourceUser1.userId)
        .map(_.filter(_.roleName == "TestRollbackSentinel"))
        .openOr(Nil)
      after shouldBe empty
    }
  }
}

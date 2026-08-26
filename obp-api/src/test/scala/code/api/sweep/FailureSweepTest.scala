package code.api.sweep

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.util.APIUtil.ResourceDoc
import code.api.util.CustomJsonFormats
import code.api.util.http4s.Http4sApp
import code.setup.{DefaultUsers, ServerSetupWithTestData}
import fs2.Stream
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.json4s.Extraction
import org.json4s.JValue
import org.json4s.JsonAST.JObject
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag
import org.typelevel.ci.CIString
import com.openbankproject.commons.util.JsonAliases.parse

/**
 * No endpoint answers a well-formed request with a 500.
 *
 * The auth sweep proves an endpoint refuses the wrong caller. This one proves it survives the
 * right caller asking for something that is not there — which is the far more common shape of a
 * production incident, and the one a migration is most likely to introduce. A connector that
 * returns a slightly different empty value, a JSON codec that stops handling a null, a column
 * read that no longer tolerates NULL: none of those show up as a wrong answer, they show up as
 * a 500 on a request that used to work.
 *
 * That class of defect has a track record on this codebase. The first run of AuthSweepTest found
 * `createTransactionRequestFreeForm` answering 500 to a nonexistent view id where it should have
 * answered 403 or 404, and it found it by accident — the sweep was looking for something else.
 * This suite looks for it on purpose, across every endpoint.
 *
 * ── What is sent ──
 *
 * A caller holding EVERY role, so nothing stops at the authorisation gate; path identifiers that
 * are well-formed and do not exist; and, for verbs that take one, the endpoint's own
 * `exampleRequestBody` serialised through the same json4s path the server uses to publish it.
 *
 * The example body is the right instrument here for the reason it is the wrong one for a success
 * test: it is structurally valid and referentially meaningless. Its bank ids, currencies and
 * entity references are illustrative, so an endpoint accepting it will almost always answer 400
 * or 404 — which is exactly the input that exercises the error paths, and exactly where an
 * unhandled null or an over-narrow match arm turns into a 500.
 *
 * ── What is asserted ──
 *
 *   4xx  fine, whatever the code. "Not found", "bad request", "not allowed" are all correct
 *        answers to a request for something that does not exist.
 *   2xx  fine. Some endpoints legitimately succeed with no arguments, or answer an empty list.
 *   5xx  a finding, always.
 *
 * Nothing here asserts WHICH 4xx. That would be a contract test, and the contract suite already
 * owns it; asserting it twice, from a place with no baseline to compare against, would produce
 * failures every time a message was reworded.
 */
object FailureSweepTest {

  /**
   * The single definition of what this sweep covers. Exposed so SweepCoverageTest's drift check
   * reads this directly instead of re-deriving its own copy of the same filter -- two copies of
   * one expression are equal by construction and can never catch this sweep's own filtering
   * changing independently of AuthSweepTest's.
   */
  def scope: List[ResourceDoc] = EndpointCatalog.all.filter(EndpointCatalog.skipReason(_).isEmpty)
}

class FailureSweepTest extends ServerSetupWithTestData with DefaultUsers with SweepFixtures {

  object FailureSweep extends Tag("FailureSweep")

  implicit val runtime: IORuntime = IORuntime.global
  private lazy val app = Http4sApp.httpApp

  private def entities: Map[String, String] =
    realBankId.map("BANK_ID" -> _).toList.toMap

  private def call(verb: String, path: String, headers: Map[String, String], body: String)
      : (Int, JValue) = {
    val method = Method.fromString(verb.toUpperCase).getOrElse(Method.GET)
    val hdrs = if (body.nonEmpty) headers + ("Content-Type" -> "application/json") else headers
    val req = Request[IO](
      method  = method,
      uri     = Uri.unsafeFromString(path),
      headers = Headers(hdrs.map { case (k, v) => Header.Raw(CIString(k), v) }.toList),
      body    = if (body.nonEmpty) Stream.emits(body.getBytes("UTF-8")).covary[IO] else Stream.empty
    )
    val resp    = app.run(req).unsafeRunSync()
    val bodyStr = resp.bodyText.compile.string.unsafeRunSync()
    val json = try { if (bodyStr.trim.isEmpty) JObject(Nil) else parse(bodyStr) }
               catch { case _: Exception => JObject(Nil) }
    (resp.status.code, json)
  }

  /**
   * The doc's own example body as JSON, or "" when it has none.
   *
   * Extraction.decompose under CustomJsonFormats is the same route Http4s uses to publish these
   * objects, so what is sent is what the documentation shows a caller to send.
   */
  private def exampleBody(doc: ResourceDoc): String = {
    implicit val formats = CustomJsonFormats.formats
    doc.exampleRequestBody match {
      case null => ""
      case body =>
        try compact(render(Extraction.decompose(body))) catch { case _: Exception => "" }
    }
  }

  /**
   * Endpoints whose 5xx is the answer they are built to give.
   *
   * Exactly one so far, and it is not a real endpoint: Http4s700 registers
   * `POST /obp/v7.0.0/test/rollback-check` inside `if (Props.testMode)` specifically to abort a
   * transaction and prove the rollback happened, so a 500 is its pass condition. It exists only
   * under `run.mode=test`, which is to say only where this sweep runs.
   *
   * Kept as a named map rather than removed from the catalog: SweepCoverageTest counts what the
   * sweeps cover, and an endpoint quietly dropped from a list is the failure mode that whole
   * test exists to prevent. Anything added here needs the same kind of reason.
   */
  private val expected5xx: Map[String, String] = Map(
    "OBPv7.0.0-testRollbackEndpoint" -> ("test-mode-only endpoint that deliberately throws to " +
      "verify transaction rollback; its 500 IS the assertion (Http4s700, Props.testMode)"),
    "OBPv7.0.0-createTestEmail" -> ("500 OBP-10056: refuses to send because portal_external_url " +
      "is unset, which is true of any test rig. Environmental -- but a missing configuration is " +
      "a 503, not a 500, so the status itself is logged in REGRESSION-GAPS rather than accepted " +
      "as correct")
  )

  private lazy val inScope: List[ResourceDoc] = FailureSweepTest.scope

  private def check(doc: ResourceDoc, headers: Map[String, String],
                    ents: Map[String, String]): Option[String] = {
    val path = EndpointCatalog.concretePath(doc, ents)
    val body = if (doc.requestVerb.toUpperCase == "GET" || doc.requestVerb.toUpperCase == "DELETE")
                 "" else exampleBody(doc)
    val (status, json) = call(doc.requestVerb, path, headers, body)
    if (status >= 500 && !expected5xx.contains(doc.operationId)) {
      implicit val formats = CustomJsonFormats.formats
      val msg = (json \ "message").extractOpt[String].getOrElse("<no message>")
      Some(s"${doc.operationId} ${doc.requestVerb} $path -> HTTP $status: $msg")
    } else None
  }

  private lazy val byVersion: Map[String, List[ResourceDoc]] =
    inScope.groupBy(_.implementedInApiVersion.toString)

  feature("No endpoint answers a well-formed request with a server error") {

    byVersion.keys.toList.sorted.foreach { version =>
      scenario(s"$version -- a fully-entitled caller asking for something absent gets 4xx, never 5xx",
               FailureSweep) {
        setPropsValues("api_disabled_endpoints" -> "[]", "api_enabled_endpoints" -> "[]")
        // Grant once per scenario, not once per class: beforeEach wipes the entitlement
        // table, so a lazy val granted during the first scenario leaves every later one
        // calling as an unentitled user -- which stops at 403 and never reaches the code
        // that might crash. That is how the first run reported only two 5xx.
        val headers = omniscientCaller
        val ents    = entities
        val docs    = byVersion(version)

        When(s"each of the ${docs.size} $version endpoints is called with valid credentials, " +
             s"every role, a nonexistent id and its own example body")
        val failures = docs.flatMap(check(_, headers, ents))

        Then("none of them crashes")
        withClue(s"${failures.size} of ${docs.size} $version endpoints answered 5xx. A request " +
                 s"for something that does not exist is an ordinary 404; a 500 means an " +
                 s"unhandled path, and it is the shape a migration introduces most often:\n" +
                 s"${failures.mkString("\n")}\n") {
          failures shouldBe empty
        }
      }
    }
  }
}

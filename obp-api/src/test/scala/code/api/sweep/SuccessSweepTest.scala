package code.api.sweep

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.util.APIUtil.ResourceDoc
import code.api.util.CustomJsonFormats
import code.api.util.http4s.Http4sApp
import code.setup.{DefaultUsers, ServerSetupWithTestData}
import fs2.Stream
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.json4s.JValue
import org.json4s.JsonAST.JObject
import org.scalatest.Tag
import org.typelevel.ci.CIString
import com.openbankproject.commons.util.JsonAliases.parse

/**
 * The endpoints that need nothing but a caller actually answer.
 *
 * The other two sweeps assert what happens when something is wrong: no credentials, no role, no
 * such entity. Neither would notice an endpoint that had stopped returning data altogether — a
 * GET that answers 404 for every caller passes the failure sweep, which only objects to 5xx.
 * This one closes that: for the endpoints where a correct answer requires no setup at all, a
 * fully-entitled caller must get one.
 *
 * ── Why only the no-path-variable endpoints ──
 *
 * Of the endpoints a caller can reach, roughly a third carry no ALL_CAPS placeholder in their
 * URL — /banks, /users/current, /my/accounts, /management/metrics and so on. For those, "call it
 * and expect an answer" is a complete test: there is no entity to create first, so a non-answer
 * is the endpoint's own fault.
 *
 * The remaining two thirds are deliberately out of scope here. About half of them reference an
 * identifier the fixtures do supply (BANK_ID, ACCOUNT_ID, VIEW_ID …) and the other half
 * reference one nothing creates — CHAT_ROOM_ID, CUSTOMER_ID, CONSENT_ID and about twenty more.
 * Both need per-cluster setup to be worth asserting, and a sweep that fabricated ids for them
 * would be asserting 404-handling under a name that promises success. They are the next two
 * waves, not this one.
 *
 * ── GET only ──
 *
 * The 116 no-variable POSTs are excluded because a generic success POST is not a thing: the
 * example bodies are illustrative rather than referentially valid (which is exactly what makes
 * them good failure-path input, see FailureSweepTest), and a POST that did succeed would leave
 * a row behind that the next scenario's fixture reset may or may not clear. A write-path success
 * sweep needs per-endpoint bodies and per-endpoint cleanup; that is Wave 3b's problem.
 *
 * ── What "an answer" means ──
 *
 * 2xx. Not a shape, not a field — the contract suite owns field-level assertions and has a
 * baseline to compare against, which this does not. Asserting shape here would duplicate that
 * work from a worse position and fail every time a message was reworded.
 *
 * Endpoints that legitimately cannot answer 2xx on a fixture database — because they need a
 * connector this build does not have, or a feature the props disable — are listed in
 * `expectedNon2xx` with the reason, and asserted to STILL not be 5xx. An endpoint that stops
 * answering is a finding; an endpoint that was never going to answer here is a documented skip.
 */
class SuccessSweepTest extends ServerSetupWithTestData with DefaultUsers with SweepFixtures {

  object SuccessSweep extends Tag("SuccessSweep")

  implicit val runtime: IORuntime = IORuntime.global
  private lazy val app = Http4sApp.httpApp

  /**
   * Endpoints that answer non-2xx on a fixture database for a stated reason.
   *
   * Every entry is a claim that the non-answer is environmental, not a defect. They are still
   * called, and still required not to crash — the skip is only from the 2xx assertion. Keeping
   * them here rather than filtering them out of the catalog means SweepCoverageTest still counts
   * them, and means each exemption has to be written down next to its reason.
   */
  private val expectedNon2xx: Map[String, String] = Map(
    // ── needs an external service this build does not run ──
    "OBPv2.0.0-elasticSearchMetrics"    -> "404: needs an Elasticsearch instance; none in the test rig",
    "OBPv2.0.0-elasticSearchWarehouse"  -> "404: needs an Elasticsearch instance; none in the test rig",
    "OBPv2.2.0-getMessageDocs"          -> ("400 OBP-30211: asks which connector's message docs to " +
      "return; the fixture rig runs `mapped`, which publishes none"),
    "OBPv3.1.0-getObpConnectorLoopback" -> "400 OBP-10010: not implemented by the mapped connector",
    "OBPv6.0.0-getMessageDocsJsonSchema" -> "same as getMessageDocs -- no connector message docs to derive a schema from",

    // ── needs a certificate the test caller does not present ──
    "OBPv5.1.0-mtlsClientCertificateInfo" -> ("400 OBP-20300: reports the caller's client " +
      "certificate; these requests are driven in-process with no TLS peer"),
    "OBPv4.0.0-verifyRequestSignResponse" -> ("401 OBP-20311: authenticates by JWS request " +
      "signature rather than by session; the sweep signs nothing"),

    // ── the URL names an entity, in a segment shaped like a literal ──
    // These carry ALL_CAPS segments that EndpointCatalog deliberately leaves verbatim
    // (API_COLLECTION_NAME, WEBUI_PROP_NAME, SCHEME), so the server correctly reports that no
    // such entity exists. Creating one first is Wave 3c's job, not this sweep's.
    "OBPv4.0.0-getMyApiCollectionByName"    -> "400 OBP-30079: no ApiCollection named API_COLLECTION_NAME",
    "OBPv4.0.0-getMyApiCollectionEndpoints" -> "400 OBP-30079: no ApiCollection named API_COLLECTION_NAME",
    "OBPv6.0.0-getWebUiProp"                -> "400 OBP-08003: no WebUi prop named WEBUI_PROP_NAME",
    "OBPv7.0.0-getRoutingScheme"            -> "404 OBP-30514: no routing scheme named SCHEME"
  )

  private def get(path: String, headers: Map[String, String]): (Int, JValue) = {
    val req = Request[IO](
      method  = Method.GET,
      uri     = Uri.unsafeFromString(path),
      headers = Headers(headers.map { case (k, v) => Header.Raw(CIString(k), v) }.toList),
      body    = Stream.empty
    )
    val resp    = app.run(req).unsafeRunSync()
    val bodyStr = resp.bodyText.compile.string.unsafeRunSync()
    val json = try { if (bodyStr.trim.isEmpty) JObject(Nil) else parse(bodyStr) }
               catch { case _: Exception => JObject(Nil) }
    (resp.status.code, json)
  }

  /**
   * No ALL_CAPS placeholder in the URL -- nothing to create before calling.
   *
   * EndpointCatalog.hasPlaceholder, not a local copy of the rule. This used to hold its own,
   * and the two had already drifted: the catalog substitutes any segment ending in ID, _CODE or
   * _NAME, this one looked for _ID and _CODE and had never learnt about _NAME. So
   * `/signal-channels/CHANNEL_NAME/info` was a placeholder to the catalog -- which duly replaced
   * it with a channel that does not exist -- and NOT a placeholder here, so this suite selected
   * it as an endpoint that "needs nothing created first" and then failed it for answering 404.
   *
   * The first half of the old condition was worse than wrong, it was vacuous:
   * `concretePath(doc) == concretePath(doc, Map.empty)` compares a default argument with the same
   * value passed explicitly, so it is true for every doc and filtered nothing.
   */
  private def hasNoPathVariable(doc: ResourceDoc): Boolean = !EndpointCatalog.hasPlaceholder(doc)

  private lazy val inScope: List[ResourceDoc] =
    EndpointCatalog.all
      .filter(EndpointCatalog.skipReason(_).isEmpty)
      .filter(_.requestVerb.toUpperCase == "GET")
      .filter(hasNoPathVariable)

  private lazy val byVersion: Map[String, List[ResourceDoc]] =
    inScope.groupBy(_.implementedInApiVersion.toString)

  private def check(doc: ResourceDoc, headers: Map[String, String]): Option[String] = {
    val path = EndpointCatalog.concretePath(doc)
    val (status, json) = get(path, headers)
    implicit val formats = CustomJsonFormats.formats
    lazy val msg = (json \ "message").extractOpt[String].getOrElse("")

    if (status >= 500)
      Some(s"${doc.operationId} GET $path -> HTTP $status (crash): $msg")
    else if (status >= 200 && status < 300)
      None
    else expectedNon2xx.get(doc.operationId) match {
      case Some(_) => None   // documented environmental non-answer; not crashing is enough
      case None    => Some(s"${doc.operationId} GET $path -> HTTP $status: $msg")
    }
  }

  feature("Endpoints that require no setup answer a fully-entitled caller") {

    byVersion.keys.toList.sorted.foreach { version =>
      scenario(s"$version -- every no-argument GET returns data", SuccessSweep) {
        setPropsValues("api_disabled_endpoints" -> "[]", "api_enabled_endpoints" -> "[]")
        // Once per scenario -- beforeEach wipes the entitlement table, so a class-level
        // lazy val would leave every scenario after the first calling without roles.
        val headers = omniscientCaller
        val docs    = byVersion(version)

        When(s"each of the ${docs.size} $version GETs that need no path variable is called")
        val failures = docs.flatMap(check(_, headers))

        Then("each one answers")
        withClue(s"${failures.size} of ${docs.size} $version no-argument GETs did not answer. " +
                 s"These need nothing created first, so a non-2xx is the endpoint's own. If one " +
                 s"of them cannot answer on a fixture database, add it to expectedNon2xx with " +
                 s"the reason rather than deleting the assertion:\n${failures.mkString("\n")}\n") {
          failures shouldBe empty
        }
      }
    }
  }
}

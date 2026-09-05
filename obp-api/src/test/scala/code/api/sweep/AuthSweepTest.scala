package code.api.sweep

import org.json4s._
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages.{ApplicationNotIdentified, AuthenticatedUserIsRequired, UserHasMissingRoles}
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
 * Every endpoint answers an unauthenticated call the way its own ResourceDoc says it will.
 *
 * Why a sweep instead of more hand-written suites: of the 870 endpoints a caller can reach,
 * 384 are referenced by no test at all, and of the ones that ARE tested only about a third
 * carry an anonymous-access scenario. Writing those by hand is several hundred near-identical
 * files that then rot one endpoint at a time; driving them off the registry means an endpoint
 * added tomorrow is swept the day it is registered, and SweepCoverageTest fails if it is not.
 *
 * Three assertions, chosen by what the doc declares:
 *
 *   auth required, no roles   anonymous -> 401 with exactly AuthenticatedUserIsRequired
 *   auth required, roles      anonymous -> 401; authenticated-without-the-role -> 403
 *   public                    anonymous -> anything EXCEPT 401
 *
 * The public case is deliberately weak. A public endpoint may well answer 400 or 404 to a call
 * with no body and a nonexistent id — that is not an authentication defect, and asserting 200
 * would make this sweep a fixture problem instead of an auth check. What must never happen is a
 * public endpoint demanding credentials, and that is what is asserted.
 *
 * The 403 assertion uses startWith, not equal: at runtime the message carries the missing roles
 * joined with " or ", and ApiRole.requiresBankId appends " for BankId(...)". An equality
 * assertion here passes locally and fails the moment an endpoint gains a second role.
 *
 * ── Why one scenario per version rather than per endpoint ──
 * ServerSetupWithTestData.beforeEach wipes and rebuilds banks, accounts and views for EVERY
 * scenario. At ~1600 assertions that fixture cost, not the assertions, would dominate: the
 * requests themselves run in-process against Http4sApp.httpApp with no TCP and no server, and
 * cost single-digit milliseconds each. So each scenario sweeps one version, collects every
 * mismatch, and fails once with the whole list. The per-endpoint detail that a
 * scenario-per-endpoint layout would have given is preserved in that list — each line names the
 * operationId, the verb, the URL, the expectation and what actually came back.
 */
object AuthSweepTest {

  /**
   * The single definition of what this sweep covers. Exposed so SweepCoverageTest's drift check
   * reads this directly instead of re-deriving its own copy of the same filter -- two copies of
   * one expression are equal by construction and can never catch this sweep's own filtering
   * changing independently of FailureSweepTest's.
   */
  def scope: List[ResourceDoc] = EndpointCatalog.all.filter(EndpointCatalog.skipReason(_).isEmpty)
}

class AuthSweepTest extends ServerSetupWithTestData with DefaultUsers with SweepFixtures {

  object AuthSweep extends Tag("AuthSweep")

  implicit val runtime: IORuntime = IORuntime.global
  private lazy val app = Http4sApp.httpApp

  /** One in-process request. No TCP, no server startup. */
  private def call(verb: String, path: String, headers: Map[String, String]): (Int, JValue) = {
    val method = Method.fromString(verb.toUpperCase).getOrElse(Method.GET)
    val req = Request[IO](
      method  = method,
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

  // Berlin Group requests get a PSD2-mandated error envelope instead of OBP's {code, message} --
  // see ErrorResponseConverter.isBerlinGroupRequest/toBgErrorBody -- {"tppMessages": [{"text":
  // ..., ...}]}, with no top-level "message" key at all. The underlying text is the SAME string
  // ResourceDocMiddleware.authenticate passed in (createErrorResponse just picks the envelope),
  // so falling back to it here is not a weaker check -- it recovers the identical assertion for a
  // shape json4s.JValue's "message" alone cannot see, rather than the sweep misreading a
  // correctly-401'd Berlin Group endpoint as broken.
  private def messageOf(json: JValue): String = {
    implicit val formats = code.api.util.CustomJsonFormats.formats
    (json \ "message").extractOpt[String]
      .orElse((json \ "tppMessages" \ "text").extractOpt[List[String]].flatMap(_.headOption))
      .getOrElse("")
  }

  /** A token for a user holding no entitlements at all — the natural 403 probe. */
  private def noRoleHeaders: Map[String, String] = Map("DirectLogin" -> s"token=${token1.value}")

  /**
   * Real identifiers from the fixtures, for the role assertion only.
   *
   * An endpoint that declares BankNotFound and carries BANK_ID validates the bank before it
   * checks roles, so a nonexistent bank answers 404 and the role gate never runs. These come
   * from the fixture banks/accounts ServerSetupWithTestData creates, read directly rather than
   * over HTTP — the sweep is in-process and a round trip per lookup would be the only slow part
   * of it.
   */
  private lazy val realEntities: Map[String, String] = realBankId match {
    case Some(bankIdValue) =>
      val accountId = code.model.dataAccess.MappedBankAccount
        .findAllByBankId(bankIdValue)
        .headOption
        .map(_.accountId.value)
      Map("BANK_ID" -> bankIdValue) ++ accountId.map("ACCOUNT_ID" -> _).toList.toMap
    case None => Map.empty
  }

  private def describe(doc: ResourceDoc): String =
    s"${doc.operationId} ${doc.requestVerb} ${EndpointCatalog.concretePath(doc)}"

  // ── the three checks, each returning a failure line or None ──────────────────

  /**
   * Deviations that are deliberate, with the reason each one is not a defect.
   *
   * A signed-off list rather than a hard zero, for the same reason KryoGoldenCompatTest keeps
   * knownDrift: a permanently red suite is one people learn to ignore, and the two entries here
   * are both behaviour somebody chose and wrote down. Anything NOT listed still fails, and
   * adding a line costs a written justification.
   */
  private val expectedAuthDeviation: Map[String, String] = Map(
    "OBPv4.0.0-verifyRequestSignResponse" ->
      ("Refuses with OBP-20311 'The Request is not signed' -- JWS request signing, a third " +
       "authentication mechanism alongside user and application. ResourceDoc has no way to " +
       "declare it: authMode covers user/application only, so neither the doc nor this sweep " +
       "can express the requirement. The 401 is correct; only the message differs."),
    "OBPv4.0.0-createTransactionRequestFreeForm" ->
      ("Answers 400 InsufficientAuthorisationToCreateTransactionRequest rather than 403. The " +
       "endpoint deliberately does no upfront view/role check and delegates the decision to " +
       "checkAuthorisationToCreateTransactionRequest inside the connector -- its own comment " +
       "says so, and an existing test depends on it. Whether an authorisation failure ought to " +
       "be 400 at all is a product question, not something to change from inside a sweep.")
  )

  /** Which exemptions were actually needed this run -- see the stale-entry scenario below. */
  private val deviationsUsed = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  private def deviationFor(doc: ResourceDoc): Option[String] = {
    val why = expectedAuthDeviation.get(doc.operationId)
    if (why.isDefined) deviationsUsed.add(doc.operationId)
    why
  }

  private def checkAnonymousIs401(doc: ResourceDoc): Option[String] = {
    val (code, json) = call(doc.requestVerb, EndpointCatalog.concretePath(doc), Map.empty)
    if (code != 401)
      Some(s"${describe(doc)} -- expected 401 for an anonymous call, got $code")
    else if (messageOf(json) != AuthenticatedUserIsRequired)
      deviationFor(doc) match {
        case Some(why) =>
          info(s"${describe(doc)} -- 401 with '${messageOf(json)}'; expected deviation: $why")
          None
        case None =>
          Some(s"${describe(doc)} -- 401 but message was '${messageOf(json)}', expected '$AuthenticatedUserIsRequired'")
      }
    else None
  }

  /**
   * A doc that asks for no USER may still ask for an APPLICATION, and that is not a defect.
   *
   * OBP has three ways to refuse an anonymous caller, and this check originally modelled one:
   *
   *   OBP-20001  User not logged in            -- user authentication
   *   OBP-20200  The application cannot be identified -- consumer/application authentication
   *   OBP-20311  The Request is not signed     -- JWS request signing
   *
   * `EndpointCatalog.needsAuthentication` reproduces the middleware's predicate, which reads
   * only errorResponseBodies and roles -- both about the user. So an endpoint that requires a
   * consumer is classified "public" here and then fails this assertion for doing exactly what
   * its doc says. Measured on createConsentRequest, getConsentRequest and
   * createVRPConsentRequest: all three answer OBP-20200, and the last one spells it out in its
   * own description -- "Client, Consumer or Application Authentication is mandatory for this
   * endpoint". Their docs were right; this check was wrong.
   *
   * So a 401 is only a violation when it is the USER one. An application-auth 401 is reported
   * as an observation instead of a failure -- named, not silently swallowed, because the doc
   * still has no machine-readable way to say "needs an application" unless someone sets
   * authMode, and a reader of resource-docs cannot tell.
   */
  private def checkPublicIsNot401(doc: ResourceDoc): Option[String] = {
    val (code, json) = call(doc.requestVerb, EndpointCatalog.concretePath(doc), Map.empty)
    val msg = messageOf(json)
    if (code != 401) None
    else if (msg.startsWith(ApplicationNotIdentified.take(9))) {
      info(s"${describe(doc)} -- declares no user authentication and requires an APPLICATION " +
           s"instead ($msg). The doc is accurate about the user; consider authMode = " +
           s"ApplicationOnly so resource-docs can say so too.")
      None
    } else
      Some(s"${describe(doc)} -- declares no authentication requirement yet answered 401 " +
           s"anonymously with '$msg'")
  }

  private def checkNoRoleIs403(doc: ResourceDoc): Option[String] = {
    val path = EndpointCatalog.concretePath(doc, realEntities)
    val (code, json) = call(doc.requestVerb, path, noRoleHeaders)
    val roles = doc.roles.getOrElse(Nil).map(_.toString).mkString(",")
    if (code != 403)
      deviationFor(doc) match {
        case Some(why) =>
          info(s"${doc.operationId} answered $code rather than 403; expected deviation: $why")
          None
        case None =>
          Some(s"${doc.operationId} ${doc.requestVerb} $path -- roles $roles: " +
               s"expected 403 for a user holding no entitlements, got $code")
      }
    else if (!messageOf(json).startsWith(UserHasMissingRoles))
      Some(s"${doc.operationId} ${doc.requestVerb} $path -- 403 but message was " +
           s"'${messageOf(json)}', expected it to start with '$UserHasMissingRoles'")
    else None
  }

  // ── the sweep, one scenario per version ─────────────────────────────────────

  private lazy val byVersion: Map[String, List[ResourceDoc]] =
    AuthSweepTest.scope.groupBy(_.implementedInApiVersion.toString)

  feature("Every reachable endpoint enforces the authentication its ResourceDoc declares") {

    byVersion.keys.toList.sorted.foreach { version =>
      scenario(s"$version -- anonymous calls are refused, public ones are not", AuthSweep) {
        // Endpoint-level enable/disable props are read per request by ResourceDocMiddleware,
        // and a disabled endpoint falls through to 404 rather than 401 -- which would read as a
        // sweep failure. Cleared the way SwaggerDocsTest does; PropsReset restores afterwards.
        // Written out in each scenario rather than shared in a helper because
        // check_test_isolation.py scans statically: any setPropsValues outside a scenario body
        // reads to it as a class-body push, `def` or not.
        setPropsValues("api_disabled_endpoints" -> "[]", "api_enabled_endpoints" -> "[]")
        val docs = byVersion(version)

        When(s"every one of the ${docs.size} $version endpoints is called with no credentials")
        val failures = docs.flatMap { doc =>
          if (EndpointCatalog.needsAuthentication(doc)) checkAnonymousIs401(doc)
          else checkPublicIsNot401(doc)
        }

        Then("each one answers as its own ResourceDoc declares")
        withClue(s"${failures.size} of ${docs.size} $version endpoints disagreed with their own " +
                 s"ResourceDoc:\n${failures.mkString("\n")}\n") {
          failures shouldBe empty
        }
      }
    }

    byVersion.keys.toList.sorted.foreach { version =>
      lazy val roleGated = byVersion(version)
        .filter(EndpointCatalog.isRoleGated)
        .filter(EndpointCatalog.roleSkipReason(_).isEmpty)

      if (roleGated.nonEmpty) {
        scenario(s"$version -- role-gated endpoints refuse a user holding no entitlements", AuthSweep) {
          setPropsValues("api_disabled_endpoints" -> "[]", "api_enabled_endpoints" -> "[]")

          When(s"every one of the ${roleGated.size} role-gated $version endpoints is called as a user with no entitlements")
          val failures = roleGated.flatMap(checkNoRoleIs403)

          Then("each one answers 403 naming the roles it wanted")
          withClue(s"${failures.size} of ${roleGated.size} role-gated $version endpoints did not " +
                   s"refuse an unentitled user:\n${failures.mkString("\n")}\n") {
            failures shouldBe empty
          }
        }
      }
    }

      // Declared after both version loops, so deviationsUsed is complete when it runs.
    scenario("no expectedAuthDeviation entry outlives the behaviour it excuses", AuthSweep) {
      import scala.jdk.CollectionConverters._
      val used = deviationsUsed.asScala.toSet
      val stale = expectedAuthDeviation.keySet -- used
      val unknown = expectedAuthDeviation.keySet -- EndpointCatalog.all.map(_.operationId).toSet

      withClue(s"these endpoints no longer deviate, so their exemption is a claim that stopped " +
               s"being true and the next reader will take it as still true: ${stale.mkString(", ")}. " +
               s"Delete the entry. ") {
        stale shouldBe empty
      }
      withClue(s"these operationIds are not in the catalog at all -- renamed or removed, and " +
               s"the exemption was left behind: ${unknown.mkString(", ")}. ") {
        unknown shouldBe empty
      }
      info(s"${used.size} deviation(s) exercised: ${used.mkString(", ")}")
    }
  }
}

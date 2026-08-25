package code.api.sweep

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
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
class AuthSweepTest extends ServerSetupWithTestData with DefaultUsers {

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

  private def messageOf(json: JValue): String = {
    implicit val formats = code.api.util.CustomJsonFormats.formats
    (json \ "message").extractOpt[String].getOrElse("")
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
  private lazy val realEntities: Map[String, String] = {
    val bank = code.bankconnectors.LocalMappedConnector.getBanksLegacy(None).map(_._1).getOrElse(Nil).headOption
    bank match {
      case Some(b) =>
        val accountId = code.model.dataAccess.MappedBankAccount
          .find(net.liftweb.mapper.By(code.model.dataAccess.MappedBankAccount.bank, b.bankId.value))
          .map(_.accountId.value)
        Map("BANK_ID" -> b.bankId.value) ++ accountId.map("ACCOUNT_ID" -> _).toList.toMap
      case None => Map.empty
    }
  }

  private def describe(doc: ResourceDoc): String =
    s"${doc.operationId} ${doc.requestVerb} ${EndpointCatalog.concretePath(doc)}"

  // ── the three checks, each returning a failure line or None ──────────────────

  private def checkAnonymousIs401(doc: ResourceDoc): Option[String] = {
    val (code, json) = call(doc.requestVerb, EndpointCatalog.concretePath(doc), Map.empty)
    if (code != 401)
      Some(s"${describe(doc)} -- expected 401 for an anonymous call, got $code")
    else if (messageOf(json) != AuthenticatedUserIsRequired)
      Some(s"${describe(doc)} -- 401 but message was '${messageOf(json)}', expected '$AuthenticatedUserIsRequired'")
    else None
  }

  private def checkPublicIsNot401(doc: ResourceDoc): Option[String] = {
    val (code, _) = call(doc.requestVerb, EndpointCatalog.concretePath(doc), Map.empty)
    if (code == 401)
      Some(s"${describe(doc)} -- declares no authentication requirement yet answered 401 anonymously")
    else None
  }

  private def checkNoRoleIs403(doc: ResourceDoc): Option[String] = {
    val path = EndpointCatalog.concretePath(doc, realEntities)
    val (code, json) = call(doc.requestVerb, path, noRoleHeaders)
    val roles = doc.roles.getOrElse(Nil).map(_.toString).mkString(",")
    if (code != 403)
      Some(s"${doc.operationId} ${doc.requestVerb} $path -- roles $roles: " +
           s"expected 403 for a user holding no entitlements, got $code")
    else if (!messageOf(json).startsWith(UserHasMissingRoles))
      Some(s"${doc.operationId} ${doc.requestVerb} $path -- 403 but message was " +
           s"'${messageOf(json)}', expected it to start with '$UserHasMissingRoles'")
    else None
  }

  // ── the sweep, one scenario per version ─────────────────────────────────────

  private lazy val byVersion: Map[String, List[ResourceDoc]] =
    EndpointCatalog.all
      .filter(EndpointCatalog.skipReason(_).isEmpty)
      .groupBy(_.implementedInApiVersion.toString)

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
  }
}

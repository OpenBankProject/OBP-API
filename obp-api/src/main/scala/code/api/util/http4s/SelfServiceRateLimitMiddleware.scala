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

package code.api.util.http4s

import cats.effect.IO
import code.api.util.SelfServiceRateLimiter
import code.api.util.SelfServiceRateLimiter.{Blocked, Outcome, Skipped, Warned, Window}
import code.util.Helper.MdcLoggable
import org.http4s.{Header, Headers, Method, Request, Response, Status}
import org.typelevel.ci.CIString

import scala.util.matching.Regex

/** Applies [[SelfServiceRateLimiter]] to the self-service endpoints by path, before routing.
 *
 *  Sits in Http4sApp.httpApp around the whole route chain, so it needs nothing from the
 *  endpoint: the scope comes from a (method, path) table and the key is the client IP address
 *  resolved the same way CallContext.ipAddress is. Runs for every API version because the
 *  patterns are version-agnostic.
 *
 *  Per request, when a scope matches:
 *   - the request is counted;
 *   - enforce mode and over a limit: 429 with the OBP error body, `Retry-After` and the
 *     `X-Rate-Limit-*` headers, and the route is never run;
 *   - otherwise the route runs and the response gains `X-Rate-Limit-Limit`, `-Remaining`
 *     and `-Reset` for the tightest window (only when the response has none already) plus,
 *     on a shadow trip, `X-Rate-Limit-Warning`.
 */
object SelfServiceRateLimitMiddleware extends MdcLoggable {

  val WarningHeader   = "X-Rate-Limit-Warning"
  val LimitHeader     = "X-Rate-Limit-Limit"
  val RemainingHeader = "X-Rate-Limit-Remaining"
  val ResetHeader     = "X-Rate-Limit-Reset"

  /** A self-service endpoint class. `condition` lets an entry opt out per request, e.g. a
   *  signal publish only counts as channel creation when the channel does not exist yet. */
  final case class Entry(scope: String, method: Method, path: Regex, condition: (Request[IO], Regex.Match) => Boolean = (_, _) => true)

  private val V = "/obp/v[^/]+" // any /obp/vN.N.N prefix

  /** The self-service table. Order matters only for readability; the first match wins. */
  val entries: List[Entry] = List(
    // Logins are deliberately absent: AuthRateLimiter counts every credential check (DirectLogin,
    // DAuth, GatewayLogin, SIWE) by IP and by account, so a login entry here would count twice.
    // signup: self-registration and the tokens it emails
    Entry("signup", Method.POST, s"^$V/users$$".r),
    Entry("signup", Method.POST, s"^$V/users/email-validation$$".r),
    Entry("signup", Method.POST, s"^$V/banks/[^/]+/user-invitations$$".r),
    // password_reset: mail sending and token guessing
    Entry("password_reset", Method.POST, s"^$V/users/password-reset-url$$".r),
    Entry("password_reset", Method.POST, s"^$V/users/password$$".r),
    // consent_request: anonymous rows created on behalf of a TPP
    Entry("consent_request", Method.POST, s"^$V/consumer/consent-requests$$".r),
    Entry("consent_request", Method.POST, s"^$V/consumer/vrp-consent-requests$$".r),
    // consumer_registration: each success creates a Consumer
    Entry("consumer_registration", Method.POST, s"^$V/dynamic-registration/consumers$$".r),
    // lookup: read-only but reaches a connector
    Entry("lookup", Method.POST, s"^$V/account/check/scheme/iban$$".r),
    // signal_channel_create: the one unbounded write into Redis. Counted only when the
    // channel named in the path does not exist yet, so ordinary publishing is untouched.
    Entry("signal_channel_create", Method.POST, s"^$V/signal-channels/([^/]+)/messages$$".r,
      (_, m) => code.api.cache.RedisMessaging.channelInfo(m.group(1)).isEmpty),
    // documentation: every public documentation read, for any version prefix. Public and
    // anonymous by design, and some are expensive when not cached (rendering the whole API,
    // or working out popular endpoints from usage records). The resource-docs routes are served
    // outside ResourceDocMiddleware, so the per-IP limit for anonymous calls never reaches them;
    // for them this entry is the only per-IP limit. `/root` is left out: it is cheap, and
    // monitoring polls it. Shadow mode unless the scope's own mode prop is set
    // (SelfServiceRateLimiter.shadowUnlessSetScopes).
    Entry("documentation", Method.GET, "^/obp/[^/]+/resource-docs/[^/]+/(obp|swagger|openapi|openapi\\.yaml)$".r),
    Entry("documentation", Method.GET, "^/obp/[^/]+/banks/[^/]+/resource-docs/[^/]+/obp$".r),
    Entry("documentation", Method.GET, "^/obp/[^/]+/message-docs/[^/]+(/json-schema|/swagger2\\.0)?$".r),
    Entry("documentation", Method.GET, "^/obp/[^/]+/api/(glossary(/[^/]+)?|tags|versions|error-messages|popular-endpoints)$".r),
    Entry("documentation", Method.GET, "^/obp/[^/]+/endpoints/(json-schema-validations|authentication-type-validations)$".r)
  )

  /** What each scope counts, in words, for the rate limiter configuration endpoint and API Manager. */
  val scopeDescriptions: Map[String, String] = Map(
    "signup" -> "POST /users, /users/email-validation, /banks/BANK_ID/user-invitations",
    "password_reset" -> "POST /users/password-reset-url, /users/password",
    "consent_request" -> "POST /consumer/consent-requests, /consumer/vrp-consent-requests",
    "consumer_registration" -> "POST /dynamic-registration/consumers",
    "lookup" -> "POST /account/check/scheme/iban",
    "signal_channel_create" -> "POST /signal-channels/CHANNEL_NAME/messages, when the channel does not exist yet",
    "documentation" -> ("GET of the public documentation: resource-docs (obp, swagger, openapi, openapi.yaml, bank level), " +
      "message-docs (plain, json-schema, swagger2.0), api/glossary, api/tags, api/versions, api/error-messages, " +
      "api/popular-endpoints, endpoints/json-schema-validations, endpoints/authentication-type-validations")
  )

  def scopeFor(req: Request[IO]): Option[String] = {
    val path = req.uri.path.renderString
    entries.iterator.map { e =>
      if (e.method != req.method) None
      else e.path.findFirstMatchIn(path).filter(m => safely(e.condition(req, m))).map(_ => e.scope)
    }.collectFirst { case Some(scope) => scope }
  }

  private def safely(b: => Boolean): Boolean =
    try b catch { case scala.util.control.NonFatal(e) =>
      logger.warn(s"SelfServiceRateLimitMiddleware condition failed open: ${e.getMessage}")
      false
    }

  /** Wrap the application. */
  def apply(req: Request[IO])(run: Request[IO] => IO[Response[IO]]): IO[Response[IO]] =
    scopeFor(req) match {
      case None => run(req)
      case Some(scope) =>
        IO.blocking(SelfServiceRateLimiter.check(scope, Http4sCallContextBuilder.clientIp(req), "ip")).flatMap {
          case Blocked(s, _, exceeded) => IO.pure(blockedResponse(s, exceeded))
          case outcome                 => run(req).map(resp => decorate(resp, outcome))
        }
    }

  private def blockedResponse(scope: String, exceeded: Window): Response[IO] = {
    val message = SelfServiceRateLimiter.blockedMessage(scope, exceeded)
    val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
    Response[IO](status = Status.TooManyRequests)
      .withEntity(s"""{"code":429,"message":"$escaped"}""".getBytes("UTF-8"))
      .withHeaders(Headers(
        Header.Raw(CIString("Content-Type"), "application/json; charset=utf-8"),
        Header.Raw(CIString("Retry-After"), exceeded.resetSeconds.toString),
        Header.Raw(CIString(LimitHeader), exceeded.limit.toString),
        Header.Raw(CIString(RemainingHeader), "0"),
        Header.Raw(CIString(ResetHeader), exceeded.resetSeconds.toString)
      ))
  }

  private def decorate(resp: Response[IO], outcome: Outcome): Response[IO] = outcome match {
    case Skipped(_) => resp
    case _ =>
      val hasLimitHeaders = resp.headers.headers.exists(_.name.toString.equalsIgnoreCase(LimitHeader))
      val counterHeaders: List[Header.Raw] = outcome.tightest.toList.filterNot(_ => hasLimitHeaders).flatMap { w =>
        List(
          Header.Raw(CIString(LimitHeader), w.limit.toString),
          Header.Raw(CIString(RemainingHeader), w.remaining.toString),
          Header.Raw(CIString(ResetHeader), w.resetSeconds.toString)
        )
      }
      val warning: List[Header.Raw] = outcome match {
        case Warned(scope, _, exceeded) =>
          List(Header.Raw(CIString(WarningHeader), SelfServiceRateLimiter.warningMessage(scope, exceeded)))
        case _ => Nil
      }
      (counterHeaders ++ warning).foldLeft(resp)((r, h) => r.putHeaders(h))
  }
}

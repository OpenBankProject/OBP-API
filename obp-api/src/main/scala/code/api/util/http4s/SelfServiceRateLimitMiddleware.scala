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
      (_, m) => code.api.cache.RedisMessaging.channelInfo(m.group(1)).isEmpty)
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

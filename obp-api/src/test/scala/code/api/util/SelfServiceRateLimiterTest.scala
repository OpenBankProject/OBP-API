package code.api.util

import cats.effect.IO
import code.api.util.SelfServiceRateLimiter._
import code.api.util.http4s.SelfServiceRateLimitMiddleware
import code.setup.ServerSetup
import org.http4s.{Method, Request, Uri}

import java.util.concurrent.atomic.AtomicLong

class SelfServiceRateLimiterTest extends ServerSetup {

  // Unique keys per scenario so Redis counters from different scenarios never collide.
  private val counter = new AtomicLong(System.nanoTime())
  private def freshIp(): String = s"198.51.100.${counter.incrementAndGet() % 255 + 1}.${counter.get()}"
  private def freshScope(): String = s"testscope_${counter.incrementAndGet()}"

  private val P = SelfServiceRateLimiter.PropsPrefix

  feature("SelfServiceRateLimiter") {

    scenario("disabled: returns Skipped and counts nothing") {
      setPropsValues(s"$P.enabled" -> "false")
      SelfServiceRateLimiter.check("signup", freshIp()) shouldBe Skipped("signup")
    }

    scenario("default mode is shadow: a trip returns Warned, never Blocked") {
      val scope = freshScope()
      setPropsValues(
        s"$P.enabled" -> "true",
        s"$P.mode" -> "",
        s"$P.$scope.per_ip.per_minute" -> "1",
        s"$P.$scope.per_ip.per_hour" -> "1000",
        s"$P.$scope.per_ip.per_day" -> "1000"
      )
      val ip = freshIp()
      SelfServiceRateLimiter.check(scope, ip) shouldBe a[Allowed]
      val second = SelfServiceRateLimiter.check(scope, ip)
      second shouldBe a[Warned]
      val Warned(_, _, exceeded) = second
      exceeded.name shouldBe "ip_per_minute"
      exceeded.limit shouldBe 1L
      exceeded.current shouldBe 2L
    }

    scenario("the warning text carries OBP-10059, the scope and the limit; no date unless announced") {
      val scope = freshScope()
      val exceeded = Window("ip_per_hour", RateLimitingPeriod.PER_HOUR, limit = 5, current = 6, resetSeconds = 100)
      setPropsValues(s"$P.enforce_announced_from" -> "")
      val plain = SelfServiceRateLimiter.warningMessage(scope, exceeded)
      plain shouldBe s"OBP-10059: Could conflict with a Future Rate Limit: This request might exceed the rate limit for $scope (5 per hour) in the future."

      setPropsValues(s"$P.enforce_announced_from" -> "2026-10-01")
      SelfServiceRateLimiter.warningMessage(scope, exceeded) should endWith(" in the future, from 2026-10-01.")
    }

    scenario("enforce mode: the (limit+1)th request is Blocked, with reset within the window") {
      val scope = freshScope()
      setPropsValues(
        s"$P.enabled" -> "true",
        s"$P.mode" -> "enforce",
        s"$P.$scope.per_ip.per_minute" -> "2",
        s"$P.$scope.per_ip.per_hour" -> "1000",
        s"$P.$scope.per_ip.per_day" -> "1000"
      )
      val ip = freshIp()
      SelfServiceRateLimiter.check(scope, ip) shouldBe a[Allowed]
      SelfServiceRateLimiter.check(scope, ip) shouldBe a[Allowed]
      val third = SelfServiceRateLimiter.check(scope, ip)
      third shouldBe a[Blocked]
      val Blocked(_, _, exceeded) = third
      exceeded.name shouldBe "ip_per_minute"
      exceeded.resetSeconds should (be > 0L and be <= 60L)
      SelfServiceRateLimiter.blockedMessage(scope, exceeded) should startWith(ErrorMessages.TooManyRequestsSelfService)
      SelfServiceRateLimiter.blockedMessage(scope, exceeded) should include("OBP-10060")
    }

    scenario("different IPs do not share per-IP counters") {
      val scope = freshScope()
      setPropsValues(
        s"$P.enabled" -> "true",
        s"$P.mode" -> "enforce",
        s"$P.$scope.per_ip.per_minute" -> "1",
        s"$P.$scope.per_ip.per_hour" -> "1000",
        s"$P.$scope.per_ip.per_day" -> "1000"
      )
      SelfServiceRateLimiter.check(scope, freshIp()) shouldBe a[Allowed]
      SelfServiceRateLimiter.check(scope, freshIp()) shouldBe a[Allowed]
      SelfServiceRateLimiter.check(scope, freshIp()) shouldBe a[Allowed]
    }

    scenario("a window set to -1 is switched off; -1 everywhere means Skipped") {
      val scope = freshScope()
      setPropsValues(
        s"$P.enabled" -> "true",
        s"$P.mode" -> "enforce",
        s"$P.$scope.per_ip.per_minute" -> "-1",
        s"$P.$scope.per_ip.per_hour" -> "-1",
        s"$P.$scope.per_ip.per_day" -> "-1",
        s"$P.$scope.global.per_hour" -> "-1"
      )
      SelfServiceRateLimiter.check(scope, freshIp()) shouldBe Skipped(scope)
    }

    scenario("the global per-hour cap trips across different IPs") {
      val scope = freshScope()
      setPropsValues(
        s"$P.enabled" -> "true",
        s"$P.mode" -> "enforce",
        s"$P.$scope.per_ip.per_minute" -> "1000",
        s"$P.$scope.per_ip.per_hour" -> "1000",
        s"$P.$scope.per_ip.per_day" -> "1000",
        s"$P.$scope.global.per_hour" -> "2"
      )
      SelfServiceRateLimiter.check(scope, freshIp()) shouldBe a[Allowed]
      SelfServiceRateLimiter.check(scope, freshIp()) shouldBe a[Allowed]
      val third = SelfServiceRateLimiter.check(scope, freshIp())
      third shouldBe a[Blocked]
      val Blocked(_, _, exceeded) = third
      exceeded.name shouldBe "global_per_hour"
    }

    scenario("an unknown IP disables the per-IP windows but keeps the global one") {
      val scope = freshScope()
      setPropsValues(
        s"$P.enabled" -> "true",
        s"$P.mode" -> "enforce",
        s"$P.$scope.per_ip.per_minute" -> "1",
        s"$P.$scope.global.per_hour" -> "1000"
      )
      val outcome = SelfServiceRateLimiter.check(scope, "Unknown")
      outcome shouldBe a[Allowed]
      outcome.windows.map(_.name) shouldBe List("global_per_hour")
    }

    scenario("limit resolution: scope prop beats generic prop beats built-in defaults") {
      val scope = freshScope()
      setPropsValues(
        s"$P.per_ip.per_minute" -> "7",
        s"$P.$scope.per_ip.per_minute" -> "3",
        s"$P.$scope.per_ip.per_hour" -> "",
        s"$P.per_ip.per_hour" -> "",
        s"$P.signup.per_ip.per_hour" -> ""
      )
      SelfServiceRateLimiter.perKeyLimit(scope, "per_minute") shouldBe 3L
      SelfServiceRateLimiter.perKeyLimit(scope, "per_hour") shouldBe genericDefaults.perHour
      SelfServiceRateLimiter.perKeyLimit("signup", "per_hour") shouldBe scopeDefaults("signup").perHour
      SelfServiceRateLimiter.globalPerHourLimit("signup") shouldBe scopeDefaults("signup").globalPerHour
    }

    scenario("tightest window is the one with the fewest remaining calls") {
      val windows = List(
        Window("ip_per_minute", RateLimitingPeriod.PER_MINUTE, limit = 10, current = 1, resetSeconds = 50),
        Window("ip_per_hour", RateLimitingPeriod.PER_HOUR, limit = 60, current = 58, resetSeconds = 900),
        Window("ip_per_day", RateLimitingPeriod.PER_DAY, limit = 200, current = 58, resetSeconds = 80000)
      )
      Allowed("x", windows).tightest.map(_.name) shouldBe Some("ip_per_hour")
    }
  }

  feature("SelfServiceRateLimitMiddleware scope table") {

    def post(path: String): Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString(path))
    def get(path: String): Request[IO] = Request[IO](Method.GET, Uri.unsafeFromString(path))

    scenario("self-service paths map to their scopes, for any API version") {
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v6.0.0/users")) shouldBe Some("signup")
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v7.0.0/users")) shouldBe Some("signup")
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v6.0.0/users/email-validation")) shouldBe Some("signup")
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v4.0.0/banks/gh.29.uk/user-invitations")) shouldBe Some("signup")
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v6.0.0/users/password-reset-url")) shouldBe Some("password_reset")
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v6.0.0/users/password")) shouldBe Some("password_reset")
      // logins belong to AuthRateLimiter, not to the self-service table
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v6.0.0/my/logins/direct")) shouldBe None
      SelfServiceRateLimitMiddleware.scopeFor(post("/my/logins/direct")) shouldBe None
      SelfServiceRateLimitMiddleware.scopeFor(post("/my/logins/siwe/challenge")) shouldBe None
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v5.0.0/consumer/consent-requests")) shouldBe Some("consent_request")
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v6.0.0/consumer/vrp-consent-requests")) shouldBe Some("consent_request")
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v6.0.0/dynamic-registration/consumers")) shouldBe Some("consumer_registration")
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v4.0.0/account/check/scheme/iban")) shouldBe Some("lookup")
    }

    scenario("everything else is left alone") {
      SelfServiceRateLimitMiddleware.scopeFor(get("/obp/v6.0.0/users")) shouldBe None
      SelfServiceRateLimitMiddleware.scopeFor(get("/obp/v6.0.0/users/current")) shouldBe None
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v6.0.0/users/USER_ID/attributes")) shouldBe None
      SelfServiceRateLimitMiddleware.scopeFor(post("/obp/v6.0.0/banks")) shouldBe None
      SelfServiceRateLimitMiddleware.scopeFor(get("/obp/v6.0.0/signal-channels")) shouldBe None
    }
  }
}

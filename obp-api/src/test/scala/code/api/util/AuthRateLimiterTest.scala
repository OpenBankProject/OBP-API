/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.util

import code.setup.ServerSetup

import java.util.concurrent.atomic.AtomicLong

class AuthRateLimiterTest extends ServerSetup {

  // Unique values per scenario so Redis counters from different scenarios don't collide.
  private val ipCounter = new AtomicLong(System.nanoTime())
  private def freshIp(): String = s"203.0.113.${ipCounter.incrementAndGet() % 255 + 1}.${ipCounter.get()}"
  private def freshUsername(): String = s"authratelimit_user_${ipCounter.incrementAndGet()}"
  private val provider = "local"

  feature("AuthRateLimiter") {

    scenario("disabled by default — always returns Right, no Redis calls") {
      // No setPropsValues — relies on the code default `auth.rate_limit.enabled = false`
      AuthRateLimiter.check(freshIp(), provider, freshUsername()) shouldBe Right(())
    }

    scenario("enabled, under limits — returns Right") {
      setPropsValues(
        "auth.rate_limit.enabled" -> "true",
        "auth.rate_limit.mode" -> "enforce",
        "auth.rate_limit.per_ip.per_minute" -> "10",
        "auth.rate_limit.per_ip.per_hour" -> "100",
        "auth.rate_limit.per_user.per_minute" -> "6"
      )
      AuthRateLimiter.check(freshIp(), provider, freshUsername()) shouldBe Right(())
    }

    scenario("enforce mode: per-IP/min limit trips on the (limit+1)th attempt") {
      setPropsValues(
        "auth.rate_limit.enabled" -> "true",
        "auth.rate_limit.mode" -> "enforce",
        "auth.rate_limit.per_ip.per_minute" -> "2",
        "auth.rate_limit.per_ip.per_hour" -> "1000",
        "auth.rate_limit.per_user.per_minute" -> "1000"
      )
      val ip = freshIp()
      AuthRateLimiter.check(ip, provider, freshUsername()) shouldBe Right(())
      AuthRateLimiter.check(ip, provider, freshUsername()) shouldBe Right(())

      val tripped = AuthRateLimiter.check(ip, provider, freshUsername())
      tripped.isLeft shouldBe true
      val Left(exceeded) = tripped
      exceeded.counter shouldBe "ip_per_minute"
      exceeded.limit shouldBe 2L
      exceeded.current should be > 2L
      exceeded.retryAfterSeconds should (be > 0L and be <= 60L)
    }

    scenario("shadow mode: trip is logged but check still returns Right") {
      setPropsValues(
        "auth.rate_limit.enabled" -> "true",
        "auth.rate_limit.mode" -> "shadow",
        "auth.rate_limit.per_ip.per_minute" -> "1",
        "auth.rate_limit.per_ip.per_hour" -> "1000",
        "auth.rate_limit.per_user.per_minute" -> "1000"
      )
      val ip = freshIp()
      AuthRateLimiter.check(ip, provider, freshUsername()) shouldBe Right(())
      // Would have tripped in enforce mode — shadow lets it through anyway
      AuthRateLimiter.check(ip, provider, freshUsername()) shouldBe Right(())
      AuthRateLimiter.check(ip, provider, freshUsername()) shouldBe Right(())
    }

    scenario("per-username/min trips independent of IP") {
      setPropsValues(
        "auth.rate_limit.enabled" -> "true",
        "auth.rate_limit.mode" -> "enforce",
        "auth.rate_limit.per_ip.per_minute" -> "1000",
        "auth.rate_limit.per_ip.per_hour" -> "1000",
        "auth.rate_limit.per_user.per_minute" -> "2"
      )
      val username = freshUsername()
      AuthRateLimiter.check(freshIp(), provider, username) shouldBe Right(())
      AuthRateLimiter.check(freshIp(), provider, username) shouldBe Right(())

      val tripped = AuthRateLimiter.check(freshIp(), provider, username)
      tripped.isLeft shouldBe true
      val Left(exceeded) = tripped
      exceeded.counter shouldBe "user_per_minute"
      exceeded.limit shouldBe 2L
    }

    scenario("same username across providers do not share a counter") {
      setPropsValues(
        "auth.rate_limit.enabled" -> "true",
        "auth.rate_limit.mode" -> "enforce",
        "auth.rate_limit.per_ip.per_minute" -> "1000",
        "auth.rate_limit.per_ip.per_hour" -> "1000",
        "auth.rate_limit.per_user.per_minute" -> "1"
      )
      val username = freshUsername()
      // First attempt under provider A — counter at 1, allowed.
      AuthRateLimiter.check(freshIp(), "providerA", username) shouldBe Right(())
      // Same username under provider B — independent counter, also allowed.
      AuthRateLimiter.check(freshIp(), "providerB", username) shouldBe Right(())
      // Second attempt under provider A trips its own counter.
      AuthRateLimiter.check(freshIp(), "providerA", username).isLeft shouldBe true
      // Provider B's counter is still at 1 — second attempt is its limit-trigger.
      AuthRateLimiter.check(freshIp(), "providerB", username).isLeft shouldBe true
    }

  }
}

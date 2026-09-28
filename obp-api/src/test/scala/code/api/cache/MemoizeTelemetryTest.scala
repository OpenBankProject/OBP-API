package code.api.cache

import java.util.UUID

import code.telemetry.Telemetry
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

/**
 * This suite checks the Telemetry recorded by Caching's memoize functions: a call that had to run
 * the cached function is a miss, a call answered from the cache is a hit, and the `cache` tag names
 * the cached code only when the key was built by CacheKeyFromArguments. A key a caller wrote itself
 * may contain an identifier, so it is labelled "other".
 */
class MemoizeTelemetryTest extends FlatSpec with Matchers {

  private def gets(cache: String, result: String): Double =
    Option(Telemetry.registry.find("obp.api.memoize.gets")
      .tags("provider", "in_memory", "cache", cache, "result", result).counter())
      .map(_.count()).getOrElse(0.0)

  "Caching.telemetryLabel" should "name Owner.method for a key built by CacheKeyFromArguments" in {
    Caching.telemetryLabel(Some("(code.bankconnectors.LocalMappedConnector$,getBanks,List())")) shouldBe
      "code.bankconnectors.LocalMappedConnector$.getBanks"
  }

  it should "say other for a key a caller wrote itself" in {
    Caching.telemetryLabel(Some("rl_active_2f6c1f0e-consumer-id_2026-09-27-10")) shouldBe "other"
    Caching.telemetryLabel(None) shouldBe "other"
  }

  "memoizeSyncWithImMemory" should "count the first call as a miss and the second as a hit" in {
    val method = s"probe${UUID.randomUUID().toString.replace("-", "").take(8)}"
    val key = s"(MemoizeTelemetryTest,$method,1)"
    val label = s"MemoizeTelemetryTest.$method"
    var runs = 0
    Caching.memoizeSyncWithImMemory(Some(key))(60.seconds) { runs += 1; "value" } shouldBe "value"
    Caching.memoizeSyncWithImMemory(Some(key))(60.seconds) { runs += 1; "value" } shouldBe "value"
    runs shouldBe 1
    gets(label, "miss") shouldBe 1.0
    gets(label, "hit") shouldBe 1.0
  }
}

package code.concurrency

import code.actorsystem.{ObpActorSystem, ObpLookupSystem}
import code.api.util.APIUtil
import code.bankconnectors.DynamicConnector
import code.util.SecureLogging

import java.lang.reflect.Modifier
import java.util.UUID

/**
 * H5: DynamicConnector.singletonObjectMap — unsynchronised mutable.Map.
 * H7: SecureLogging.customPatternCache — unsynchronised mutable.Map.getOrElseUpdate.
 * M8: APIUtil.connectorToEndpoint — mutable.Map written only at startup (structural note).
 * M9: ObpActorSystem.northSideAkkaConnectorActorSystem — bare `var` (structural note).
 *
 * THE HAZARD:
 *   H5 / H7 share the same root cause: both use scala.collection.mutable.Map, whose resize,
 *   rehash, and getOrElseUpdate operations are NOT thread-safe. Concurrent mutations can cause:
 *     - Lost writes (two threads both insert at the same hash bucket; one insert is dropped)
 *     - HashMap corruption (infinite loop during resize on concurrent structural modification)
 *     - NPE / ClassCastException from reading partially-written internal state
 *
 *   H5 (DynamicConnector.singletonObjectMap):
 *     `createSingletonObject` calls `singletonObjectMap.put(key, value)` with no synchronisation.
 *     In mapped-connector mode, multiple DynamicConnector calls from concurrent HTTP requests can
 *     race on this map, corrupting the object registry or silently dropping a registration.
 *
 *   H7 (SecureLogging.customPatternCache):
 *     `maskWithCustomPattern` calls `customPatternCache.getOrElseUpdate(regex, compile(regex))`.
 *     `getOrElseUpdate` on a mutable.Map is non-atomic: it reads the map, misses, compiles the
 *     Pattern, then puts it — two concurrent compilers of the same regex both compile and both
 *     put, and the resulting double-put of a (String → Pattern) entry can tear the HashMap's
 *     internal chain structure.
 *
 *   M8 (APIUtil.connectorToEndpoint): a mutable.Map populated at startup by addEndpointInfos.
 *     Startup runs single-threaded, so the write window is narrow, but if two Boot threads ever
 *     race (e.g. lazy-init re-entrance), the map can be corrupted.
 *
 *   M9 (ObpActorSystem.northSideAkkaConnectorActorSystem): a bare `var` assigned once without
 *     `@volatile`. JVM memory model does not guarantee visibility of a non-volatile write to
 *     another thread; a reader spinning on Boot startup can see a stale null.
 *
 * EXPECTED TO FAIL (H5, H7) under high concurrency until the maps are replaced with
 * ConcurrentHashMap or wrapped in synchronised blocks. M8 and M9 are startup-only structural
 * hazards — no failing assertion possible in a live server test.
 * Tagged ConcurrencyRace.
 */
class ConcurrentMutableSingletonRaceTest extends ConcurrentRaceSetup {

  feature("Mutable singleton maps must be thread-safe") {

    scenario("H5: concurrent createSingletonObject calls must not lose writes or corrupt DynamicConnector.singletonObjectMap", ConcurrencyRace) {
      Given("a set of unique keys to be registered concurrently in DynamicConnector.singletonObjectMap")
      val n    = 50
      val keys = (1 to n).map(i => s"__conc_h5_key_${i}_${UUID.randomUUID.toString.take(6)}")

      When(s"$n threads concurrently call createSingletonObject, one key per thread")
      val results = runConcurrentWithBarrier(n) { i =>
        DynamicConnector.createSingletonObject(keys(i), s"value_$i")
      }

      Then("every key must be retrievable from the map — no writes may be lost")
      val missing = keys.filter(k => DynamicConnector.getSingletonObject(k).isEmpty)
      withClue(
        s"missing=${missing.size}/$n keys after concurrent createSingletonObject: " +
        s"scala.collection.mutable.Map.put is not thread-safe — concurrent structural modifications " +
        s"during a resize can silently drop entries or corrupt the HashMap — "
      ) {
        missing shouldBe empty
      }
    }

    scenario("H7: concurrent maskWithCustomPattern calls must not corrupt SecureLogging.customPatternCache", ConcurrencyRace) {
      Given("a set of distinct regex patterns to be compiled and cached concurrently")
      val n        = 30
      val patterns = (1 to n).map(i => s"conc_h7_pattern_${i}_[a-z]+")
      val input    = "hello world conc_h7_pattern_1_abc"

      When(s"$n threads concurrently call maskWithCustomPattern with different patterns")
      val results = runConcurrentWithBarrier(n) { i =>
        scala.util.Try {
          SecureLogging.maskWithCustomPattern(patterns(i), "***", input)
        }
      }

      Then("no call must throw — customPatternCache.getOrElseUpdate must not corrupt the HashMap")
      val failures = results.collect {
        case scala.util.Failure(e) => s"${e.getClass.getSimpleName}: ${e.getMessage.take(80)}"
      }
      withClue(
        s"failures=$failures: " +
        s"SecureLogging.customPatternCache uses scala.collection.mutable.Map.getOrElseUpdate, which " +
        s"is not thread-safe — concurrent compilations of different patterns can cause HashMap " +
        s"corruption (NPE, ClassCastException, or infinite loop during resize) — "
      ) {
        failures shouldBe empty
      }
    }

    scenario("H7b: the same pattern compiled concurrently must not corrupt the cache", ConcurrencyRace) {
      Given("a single regex pattern that n threads will all compile into customPatternCache simultaneously")
      val n       = 30
      val pattern = s"conc_h7b_${UUID.randomUUID.toString.take(8)}_[0-9]+"
      val input   = "some text 1234"

      When(s"$n threads concurrently call maskWithCustomPattern with the same new pattern")
      val results = runConcurrentWithBarrier(n) { _ =>
        scala.util.Try { SecureLogging.maskWithCustomPattern(pattern, "***", input) }
      }

      Then("no call must throw — double-insert of the same (regex → Pattern) must be idempotent")
      val failures = results.collect {
        case scala.util.Failure(e) => s"${e.getClass.getSimpleName}: ${e.getMessage.take(80)}"
      }
      withClue(
        s"failures=$failures: concurrent getOrElseUpdate for the same key: both threads miss, both " +
        s"compile, both call put — the double-put can tear the HashMap's bucket chain — "
      ) {
        failures shouldBe empty
      }
    }

    // ── Structural hardening tests (H6, M8, M9) ──────────────────────────────
    // These are init-time vars / maps, not request-path data races, so a failing concurrent test
    // can't reliably reproduce them. Instead we assert the hardening primitive is in place:
    //  - M8: connectorToEndpoint must be a thread-safe concurrent Map (TrieMap), not a plain HashMap.
    //  - H6/M9: the actor-system vars must be @volatile so a write is visible across threads.
    // RED until the fix lands; GREEN once the primitive is present.

    def fieldIsVolatile(holder: AnyRef, fieldName: String): Boolean = {
      val f = holder.getClass.getDeclaredField(fieldName)
      Modifier.isVolatile(f.getModifiers)
    }

    scenario("M8: APIUtil.connectorToEndpoint must be a thread-safe concurrent map", ConcurrencyRace) {
      Given("APIUtil.connectorToEndpoint, populated at startup and read on the resource-docs path")
      When("inspecting its concrete type")
      val isConcurrent = APIUtil.connectorToEndpoint.isInstanceOf[scala.collection.concurrent.Map[_, _]]
      Then("it must be a scala.collection.concurrent.Map (e.g. TrieMap), not a plain mutable.HashMap")
      withClue(
        s"connectorToEndpoint runtimeClass=${APIUtil.connectorToEndpoint.getClass.getName}: " +
        s"a plain mutable.Map is not safe for concurrent put/getOrElse during startup re-entrance. " +
        s"Fix: scala.collection.concurrent.TrieMap (same Map API, lock-free, thread-safe) — "
      ) {
        isConcurrent shouldBe true
      }
    }

    scenario("H6: ObpLookupSystem.obpLookupSystem must be @volatile (visible across threads)", ConcurrencyRace) {
      Given("the lazily-initialised actor-system holder var")
      When("inspecting the field modifiers")
      val volatileField = fieldIsVolatile(ObpLookupSystem, "obpLookupSystem")
      Then("the field must be volatile so the double-checked init publishes safely")
      withClue(
        "ObpLookupSystem.init() does `if (obpLookupSystem == null) { ...; obpLookupSystem = system }` " +
        "with no @volatile and no synchronized — two threads can both see null, both build an ActorSystem, " +
        "and a reader can see a stale null. Fix: @volatile var + synchronized init — "
      ) {
        volatileField shouldBe true
      }
    }

    scenario("M9: ObpActorSystem.northSideAkkaConnectorActorSystem must be @volatile", ConcurrencyRace) {
      Given("the north-side Akka connector actor-system var")
      When("inspecting the field modifiers")
      val volatileField = fieldIsVolatile(ObpActorSystem, "northSideAkkaConnectorActorSystem")
      Then("the field must be volatile so its single assignment is visible to all readers")
      withClue(
        "ObpActorSystem.northSideAkkaConnectorActorSystem is a bare `var ... = _` assigned once without " +
        "@volatile — the JVM memory model does not guarantee a reader sees the assignment. " +
        "Fix: @volatile var + synchronized start — "
      ) {
        volatileField shouldBe true
      }
    }
  }
}

package code.telemetry

import java.io.File

import code.setup.ServerSetupWithTestData
import org.scalatest.Tag

import scala.io.Source
import scala.jdk.CollectionConverters._

/**
 * This suite holds OBP-API to the Telemetry conventions in docs/telemetry_conventions.md.
 *
 * Two guards read the source: new hand-written `AtomicLong` counters, and new Guava caches that are
 * not registered with Telemetry, each have an allowlist of what exists today. **An allowlist only
 * ever shrinks**, as in AnyBankScopeSweepTest: a new counter belongs in Telemetry, and a new cache
 * is registered with `Telemetry.monitorCache`.
 *
 * Two guards read what the running server registered: meter names, and tag keys that would carry
 * an identifier of a person or a record.
 */
class TelemetryConventionsTest extends ServerSetupWithTestData {

  object TelemetryConventions extends Tag("TelemetryConventions")

  // Maven runs the suite from obp-api/, an IDE often from the repository root.
  private val sourceRoot: File =
    List(new File("src/main/scala"), new File("obp-api/src/main/scala")).find(_.isDirectory)
      .getOrElse(throw new IllegalStateException("TelemetryConventionsTest cannot find obp-api/src/main/scala"))

  private def scalaFilesOutsideTelemetry: List[File] = {
    def walk(dir: File): List[File] =
      Option(dir.listFiles).toList.flatten.flatMap(f => if (f.isDirectory) walk(f) else List(f))
    walk(sourceRoot).filter(_.getName.endsWith(".scala")).filterNot(_.getPath.contains("/code/telemetry/"))
  }

  private def relative(file: File): String = sourceRoot.toPath.relativize(file.toPath).toString

  private def read(file: File): String = {
    val source = Source.fromFile(file, "UTF-8")
    try source.mkString finally source.close()
  }

  private def occurrences(text: String, needle: String): Int = text.sliding(needle.length).count(_ == needle)

  /** Hand-written `AtomicLong` counters that existed when Telemetry arrived, per file. */
  private val atomicLongAllowlist: Map[String, Int] = Map(
    "code/metricsstream/MetricsEventBus.scala" -> 1,
    "code/util/Helper.scala" -> 3,
    "code/util/SecureLogging.scala" -> 1,
    "code/api/cache/RedisLogger.scala" -> 1,
    "code/api/util/JsonSchemaGenerator.scala" -> 1,
    "code/api/v2_2_0/MessageDocsJsonCache.scala" -> 4,
    "code/logcache/LogCacheEventBus.scala" -> 1
  )

  /** Standard meter families OBP-API registers through Micrometer's own binders. */
  private val standardPrefixes = List("jvm.", "process.", "system.", "hikaricp.", "cache.", "disk.")

  /** Tag keys that would put an identifier of a person or a record on a series. */
  private val forbiddenTagKeys = Set(
    "user_id", "username", "consumer_id", "consent_id", "consent_reference_id", "account_id",
    "transaction_id", "customer_id", "bank_id", "url", "path", "correlation_id", "api_instance_id")

  feature("Hand-written counters and unregistered caches do not grow") {

    scenario("No new AtomicLong counter outside code.telemetry", TelemetryConventions) {
      val found = scalaFilesOutsideTelemetry
        .map(file => relative(file) -> occurrences(read(file), "AtomicLong("))
        .filter(_._2 > 0).toMap
      val grown = found.filter { case (file, count) => count > atomicLongAllowlist.getOrElse(file, 0) }
      withClue("These files hold more AtomicLong counters than when Telemetry arrived. Record the count with " +
        "Telemetry.counter instead (docs/telemetry_conventions.md, section 9).\n" + grown.mkString("\n") + "\n") {
        grown shouldBe empty
      }
      val shrunk = atomicLongAllowlist.filter { case (file, count) => found.getOrElse(file, 0) < count }
      withClue("These files hold fewer AtomicLong counters than the allowlist says. Lower or delete their lines.\n" +
        shrunk.mkString("\n") + "\n") {
        shrunk shouldBe empty
      }
    }

    scenario("Every Guava cache is registered with Telemetry", TelemetryConventions) {
      val unregistered = scalaFilesOutsideTelemetry.map(file => relative(file) -> read(file)).filter { case (_, text) =>
        occurrences(text, "CacheBuilder.newBuilder") > occurrences(text, "Telemetry.monitorCache(")
      }.map(_._1)
      withClue("These files build a Guava cache without registering it. Build it with recordStats() and wrap it in " +
        "Telemetry.monitorCache(cache, \"name\").\n" + unregistered.mkString("\n") + "\n") {
        unregistered shouldBe empty
      }
    }
  }

  feature("What the running server registered follows the naming and tag rules") {

    scenario("Every meter is either OBP-API's own (obp.api.) or a standard family", TelemetryConventions) {
      val offenders = Telemetry.registry.getMeters.asScala.map(_.getId.getName).toSet
        .filterNot(name => name.startsWith(Telemetry.OwnPrefix) || standardPrefixes.exists(p => name.startsWith(p)))
      withClue(s"These meters are neither obp.api.* nor standard (${standardPrefixes.mkString(", ")}).\n" +
        offenders.mkString("\n") + "\n") {
        offenders shouldBe empty
      }
    }

    scenario("No series carries an identifier of a person or a record", TelemetryConventions) {
      val offenders = Telemetry.registry.getMeters.asScala.map(_.getId)
        .filterNot(_.getName == "obp.api.instance.info") // names the instance once, by design
        .flatMap(id => id.getTags.asScala.map(_.getKey).filter(forbiddenTagKeys.contains).map(key => s"${id.getName} [$key]"))
        .toSet
      withClue("These series carry a tag whose values are unbounded identifiers (docs/telemetry_conventions.md, section 6).\n" +
        offenders.mkString("\n") + "\n") {
        offenders shouldBe empty
      }
    }
  }
}

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
package code.telemetry

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicBoolean

import com.google.common.cache.Cache
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import io.micrometer.core.instrument._
import io.micrometer.core.instrument.binder.cache.GuavaCacheMetrics
import io.micrometer.core.instrument.binder.jvm._
import io.micrometer.core.instrument.binder.system.{FileDescriptorMetrics, ProcessorMetrics, UptimeMetrics}
import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

/**
 * This object is OBP-API's single entry point for Telemetry: the aggregated numbers (counts,
 * durations, sizes, current levels) that describe how the running instance is behaving.
 *
 * Code elsewhere records Telemetry through the methods here rather than through Micrometer
 * directly, so that the rules in docs/telemetry_conventions.md hold in one place: OBP-API's own
 * meters are named `obp.api.*` (served to Prometheus as `obp_api_*`), and a tag never carries an
 * identifier of a person or a record.
 *
 * Recording always happens, because it costs no more than an atomic add and the Role-gated
 * Telemetry endpoint reads the same registry. The `telemetry.port.enabled` prop only decides
 * whether the separate port that Prometheus scrapes is opened.
 *
 * This object deliberately does not extend MdcLoggable: Helper registers its log counters with
 * Telemetry, and Telemetry must not need Helper to be initialised first.
 */
object Telemetry {

  private val logger = LoggerFactory.getLogger(getClass)

  /** Every meter OBP-API invents starts with this. Standard library meters (jvm.*, hikaricp.*, cache.*) keep their own names. */
  val OwnPrefix = "obp.api."

  /** The path the separate port serves. Not `/metrics`, which in OBP means API Metrics. */
  val ScrapePath = "/telemetry"

  val DefaultPort = 9464

  /**
   * Bucket limits for request-style timers (endpoints, Connector calls). A fixed, short list keeps
   * the number of Prometheus series per operation small, where Micrometer's default percentile
   * histogram would add about seventy buckets to every operation.
   */
  val RequestDurationBuckets: Seq[Duration] =
    Seq(10L, 50L, 100L, 250L, 500L, 1000L, 2500L, 5000L, 10000L, 30000L).map(Duration.ofMillis)

  lazy val registry: PrometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

  private def checkedName(name: String): String = {
    require(name.startsWith(OwnPrefix), s"Telemetry meter '$name' must start with '$OwnPrefix'. See docs/telemetry_conventions.md.")
    name
  }

  private def tagsOf(tags: Seq[(String, String)]): java.lang.Iterable[Tag] =
    tags.map { case (key, value) => Tag.of(key, Option(value).getOrElse("")) }.asJava

  // ===== Recording =====

  def counter(name: String, tags: (String, String)*): Counter =
    registry.counter(checkedName(name), tagsOf(tags))

  def timer(name: String, tags: (String, String)*): Timer =
    Timer.builder(checkedName(name)).tags(tagsOf(tags)).register(registry)

  /** A timer that also publishes the fixed [[RequestDurationBuckets]], for request-style work only. */
  def requestTimer(name: String, tags: (String, String)*): Timer =
    Timer.builder(checkedName(name)).tags(tagsOf(tags)).serviceLevelObjectives(RequestDurationBuckets: _*).register(registry)

  def summary(name: String, baseUnit: String, tags: (String, String)*): DistributionSummary =
    DistributionSummary.builder(checkedName(name)).baseUnit(baseUnit).tags(tagsOf(tags)).register(registry)

  /** A gauge read from `value` whenever Telemetry is collected. */
  def gauge(name: String, tags: (String, String)*)(value: => Double): Gauge =
    Gauge.builder(checkedName(name), () => java.lang.Double.valueOf(value)).tags(tagsOf(tags)).strongReference(true).register(registry)

  /** A counter whose value lives elsewhere (an existing AtomicLong getter) and only ever goes up. */
  def functionCounter(name: String, tags: (String, String)*)(value: => Long): FunctionCounter =
    FunctionCounter.builder(checkedName(name), this, (_: Telemetry.type) => value.toDouble).tags(tagsOf(tags)).register(registry)

  /** Records hits, misses, evictions and size of a Guava cache built with `recordStats()`, under the standard `cache.*` names. */
  def monitorCache[K, V](cache: Cache[K, V], cacheName: String): Cache[K, V] =
    GuavaCacheMetrics.monitor[K, V, Cache[K, V]](registry, cache, cacheName)

  /** "2xx", "4xx", "5xx": the status class, which keeps the tag's values few. */
  def statusClass(code: Int): String = s"${code / 100}xx"

  /** Records one request served by an endpoint that has a ResourceDoc. */
  def recordEndpoint(operationId: String, apiVersion: String, statusCode: Int, nanos: Long, responseBytes: Option[Long]): Unit = {
    requestTimer("obp.api.endpoint.requests",
      "operation" -> operationId, "api_version" -> apiVersion, "status" -> statusClass(statusCode))
      .record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS)
    responseBytes.foreach(bytes => summary("obp.api.endpoint.response.size", "bytes", "operation" -> operationId).record(bytes.toDouble))
  }

  /**
   * Times a route that answers outside ResourceDocMiddleware and records it as that middleware
   * would: under the operation id of the route's own ResourceDoc and that doc's API version. Used
   * where a route cannot sit behind the middleware (see
   * docs/resource_doc_and_endpoint_consistency_status.md). A response that fails with an exception
   * is recorded as 5xx, then the exception carries on.
   */
  def timeEndpoint(operationId: String, apiVersion: String)(
    response: cats.effect.IO[org.http4s.Response[cats.effect.IO]]
  ): cats.effect.IO[org.http4s.Response[cats.effect.IO]] =
    cats.effect.IO(System.nanoTime()).flatMap { startNanos =>
      response
        .flatTap(served => cats.effect.IO(recordEndpoint(operationId, apiVersion, served.status.code, System.nanoTime() - startNanos, served.contentLength)))
        .onError { case _ => cats.effect.IO(recordEndpoint(operationId, apiVersion, 500, System.nanoTime() - startNanos, None)) }
    }

  /**
   * The number of items in a list response: the length of the response itself when it is a JSON
   * array, or of its only array field when it is an object wrapping one list (`{"banks": [...]}`,
   * the usual OBP shape). None for anything else, which is then not recorded.
   */
  def listItemCount(json: org.json4s.JValue): Option[Int] = json match {
    case org.json4s.JArray(items) => Some(items.size)
    case org.json4s.JObject(fields) =>
      fields.collect { case (_, org.json4s.JArray(items)) => items.size } match {
        case List(size) => Some(size)
        case _ => None
      }
    case _ => None
  }

  /** Records how many items a list response carried, when it is one. */
  def recordResponseItems(operationId: String, json: org.json4s.JValue): Unit =
    listItemCount(json).foreach(size =>
      summary("obp.api.endpoint.response.items", "items", "operation" -> operationId).record(size.toDouble))

  /** Records one call from OBP-API to a Connector method. */
  def recordConnectorCall(connectorName: String, methodName: String, millis: Long, isSuccess: Boolean): Unit =
    requestTimer("obp.api.connector.calls",
      "connector" -> connectorName, "connector_method" -> methodName, "result" -> (if (isSuccess) "success" else "failure"))
      .record(millis, java.util.concurrent.TimeUnit.MILLISECONDS)

  /** Prometheus text format of everything recorded. */
  def scrape(): String = registry.scrape()

  // ===== Start-up =====

  private val started = new AtomicBoolean(false)
  @volatile private var server: Option[HttpServer] = None

  /** The prop values in force, read once at start-up. */
  case class PortSettings(enabled: Boolean, host: String, port: Int)

  def portSettings: PortSettings = {
    import code.api.util.APIUtil
    PortSettings(
      enabled = APIUtil.getPropsAsBoolValue("telemetry.port.enabled", false),
      host = APIUtil.getPropsValue("telemetry.host", "0.0.0.0"),
      port = APIUtil.getPropsAsIntValue("telemetry.port", DefaultPort))
  }

  /** When Prometheus (or anything) last collected Telemetry from the separate port; None if never since start-up. */
  @volatile private var lastScrapeAt: Option[Long] = None
  def lastScrapeMillis: Option[Long] = lastScrapeAt

  /** The port actually bound, when the separate port is open. */
  def boundPort: Option[Int] = server.map(_.getAddress.getPort)

  /**
   * Registers the standard and OBP-API meters and, when `telemetry.port.enabled` is true, opens the
   * separate port. Called once from Boot; later calls do nothing.
   */
  def start(): Unit = if (started.compareAndSet(false, true)) {
    bindStandardMeters()
    TelemetryBindings.bindAll()
    val settings = portSettings
    if (settings.enabled) {
      server = Some(startServer(settings.host, settings.port))
      logger.info(s"Telemetry.start says: serving Telemetry at http://${settings.host}:${boundPort.getOrElse(settings.port)}$ScrapePath")
    } else {
      logger.info("Telemetry.start says: telemetry.port.enabled is false, so the Telemetry port is not opened")
    }
  }

  private def bindStandardMeters(): Unit = {
    List(
      new JvmMemoryMetrics(), new JvmGcMetrics(), new JvmHeapPressureMetrics(), new JvmThreadMetrics(),
      new ClassLoaderMetrics(), new JvmInfoMetrics(), new ProcessorMetrics(), new UptimeMetrics(),
      new FileDescriptorMetrics()
    ).foreach(_.bindTo(registry))
    gauge("obp.api.instance.info",
      "api_instance_id" -> code.api.Constant.ApiInstanceId,
      "git_commit" -> code.api.util.APIUtil.gitCommit)(1.0)
  }

  /**
   * Opens a small HTTP server with its own single thread, so it keeps answering while the main
   * request pool is saturated. It serves only [[ScrapePath]]. Port 0 picks a free port (tests).
   */
  def startServer(host: String, port: Int): HttpServer = {
    val httpServer = HttpServer.create(new InetSocketAddress(host, port), 0)
    httpServer.createContext("/", (exchange: HttpExchange) => {
      try {
        val (status, body, contentType) =
          if (exchange.getRequestURI.getPath == ScrapePath && exchange.getRequestMethod == "GET")
            { lastScrapeAt = Some(System.currentTimeMillis()); (200, scrape(), "text/plain; version=0.0.4; charset=utf-8") }
          else
            (404, s"Not found. Telemetry is served at $ScrapePath\n", "text/plain; charset=utf-8")
        val bytes = body.getBytes(StandardCharsets.UTF_8)
        exchange.getResponseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        exchange.getResponseBody.write(bytes)
      } finally exchange.close()
    })
    httpServer.setExecutor(Executors.newSingleThreadExecutor(new ThreadFactory {
      def newThread(runnable: Runnable): Thread = {
        val thread = new Thread(runnable, "telemetry-http")
        thread.setDaemon(true)
        thread
      }
    }))
    httpServer.start()
    httpServer
  }
}

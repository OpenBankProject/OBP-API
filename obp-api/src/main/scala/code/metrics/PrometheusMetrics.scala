package code.metrics

import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram, Summary}
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import net.liftweb.http.{GetRequest, LiftRules, PlainTextResponse, Req}
import net.liftweb.common.Full

object PrometheusMetrics {
  def init(): Unit = {
    DefaultExports.initialize()
    registerMetricsEndpoint()
  }

  val apiRequests: Counter = Counter.build()
    .name("api_requests_total")
    .help("Total API requests")
    .labelNames("method", "endpoint", "code")
    .register()

  val apiActiveTpp: Counter = Counter.build()
    .name("api_active_tpp")
    .help("Active TPP requests")
    .labelNames("tpp")
    .register()

  val apiLatency: Summary = Summary.build()
    .name("api_latency_seconds")
    .help("API request latency")
    .quantile(0.5, 0.05)
    .quantile(0.95, 0.01)
    .register()

  val apiLatencyEndpoint: Histogram = Histogram.build()
    .name("api_latency_seconds_by_endpoint")
    .help("API request latency by endpoint")
    .labelNames("endpoint")
    .buckets(0.005, 0.01, 0.025, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0)
    .register()


  def registerMetricsEndpoint(): Unit = {
    LiftRules.dispatch.append {
      case Req("metrics" :: Nil, _, GetRequest) =>
        () => {
          val writer = new java.io.StringWriter()
          TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples())
          Full(PlainTextResponse(writer.toString))
        }
    }
  }

  def recordApiRequest(method: String, endpoint: String, code: Int): Unit = {
    apiRequests.labels(method, endpoint, code.toString()).inc()
  }

  def recordApiActiveTpp(tpp: String): Unit = {
    apiActiveTpp.labels(tpp).inc()
  }

  def recordApiLatency(seconds: Double): Unit = {
    apiLatency.observe(seconds)
  }

  def recordApiLatencyByEndpoint(seconds: Double, endpoint: String): Unit = {
    apiLatencyEndpoint.labels(endpoint).observe(seconds)
  }
}

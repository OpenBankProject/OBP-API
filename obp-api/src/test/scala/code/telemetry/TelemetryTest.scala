package code.telemetry

import java.net.{HttpURLConnection, URI}
import java.nio.charset.StandardCharsets
import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}

/**
 * This suite checks the Telemetry object on its own, without an OBP-API server: the naming rule,
 * what a recorded endpoint request looks like to Prometheus, and the separate port.
 *
 * The registry is shared by the whole JVM, so every check uses a meter name or tag value unique
 * to the check.
 */
class TelemetryTest extends FlatSpec with Matchers {

  private def unique(label: String) = s"${label}_${UUID.randomUUID().toString.replace("-", "").take(8)}"

  private def httpGet(port: Int, path: String): (Int, String) = {
    val connection = URI.create(s"http://127.0.0.1:$port$path").toURL.openConnection().asInstanceOf[HttpURLConnection]
    try {
      val status = connection.getResponseCode
      val stream = if (status < 400) connection.getInputStream else connection.getErrorStream
      (status, new String(stream.readAllBytes(), StandardCharsets.UTF_8))
    } finally connection.disconnect()
  }

  "Telemetry" should "refuse an OBP-API meter whose name does not start with obp.api." in {
    an[IllegalArgumentException] should be thrownBy Telemetry.counter("cache.gets")
    an[IllegalArgumentException] should be thrownBy Telemetry.timer("obp.apisomething")
  }

  it should "serve a counter to Prometheus with underscores and _total" in {
    val name = unique("probe")
    Telemetry.counter(s"obp.api.test.$name", "result" -> "hit").increment(3)
    Telemetry.scrape() should include(s"""obp_api_test_${name}_total{result="hit"} 3.0""")
  }

  it should "record an endpoint request with its status class and the fixed duration buckets" in {
    val operation = unique("OBPv7.0.0-probe")
    Telemetry.recordEndpoint(operation, "v7.0.0", 404, 30L * 1000 * 1000, Some(512L))
    val scraped = Telemetry.scrape()
    scraped should include(s"""obp_api_endpoint_requests_seconds_count{api_version="v7.0.0",operation="$operation",status="4xx"} 1""")
    // 30 ms falls in the 50 ms bucket and every larger one, not in the 10 ms bucket.
    scraped should include(s"""obp_api_endpoint_requests_seconds_bucket{api_version="v7.0.0",operation="$operation",status="4xx",le="0.01"} 0""")
    scraped should include(s"""obp_api_endpoint_requests_seconds_bucket{api_version="v7.0.0",operation="$operation",status="4xx",le="0.05"} 1""")
    scraped should include(s"""obp_api_endpoint_response_size_bytes_sum{operation="$operation"} 512.0""")
  }

  it should "record a Connector call as success or failure" in {
    val method = unique("getProbe")
    Telemetry.recordConnectorCall("star", method, 5L, isSuccess = false)
    Telemetry.scrape() should include(s"""obp_api_connector_calls_seconds_count{connector="star",connector_method="$method",result="failure"} 1""")
  }

  it should "report a status class, never a status code" in {
    Telemetry.statusClass(200) shouldBe "2xx"
    Telemetry.statusClass(403) shouldBe "4xx"
    Telemetry.statusClass(503) shouldBe "5xx"
  }

  it should "count the items of a list response, whether a bare array or an object wrapping one list" in {
    import org.json4s._
    Telemetry.listItemCount(JArray(List(JInt(1), JInt(2)))) shouldBe Some(2)
    Telemetry.listItemCount(JObject(List("banks" -> JArray(List(JInt(1), JInt(2), JInt(3)))))) shouldBe Some(3)
    // An object with no list, or with two lists, is not a list response.
    Telemetry.listItemCount(JObject(List("bank_id" -> JString("x")))) shouldBe None
    Telemetry.listItemCount(JObject(List("a" -> JArray(Nil), "b" -> JArray(Nil)))) shouldBe None
  }

  it should "time a route outside the middleware under its operation id, and count an exception as 5xx" in {
    import cats.effect.IO
    import cats.effect.unsafe.implicits.global
    import org.http4s.{Response, Status}
    val operation = unique("OBPv1.4.0-probeDocs")
    Telemetry.timeEndpoint(operation, "v1.4.0")(IO.pure(Response[IO](Status.Ok))).unsafeRunSync().status shouldBe Status.Ok
    an[RuntimeException] should be thrownBy
      Telemetry.timeEndpoint(operation, "v1.4.0")(IO.raiseError[Response[IO]](new RuntimeException("boom"))).unsafeRunSync()
    val scraped = Telemetry.scrape()
    scraped should include(s"""obp_api_endpoint_requests_seconds_count{api_version="v1.4.0",operation="$operation",status="2xx"} 1""")
    scraped should include(s"""obp_api_endpoint_requests_seconds_count{api_version="v1.4.0",operation="$operation",status="5xx"} 1""")
  }

  "BatchWriterTelemetry" should "derive the queue depth from rows queued, written and lost" in {
    val writer = unique("writer")
    val batchTelemetry = new BatchWriterTelemetry(writer)
    (1 to 5).foreach(_ => batchTelemetry.queued())
    batchTelemetry.written(2, 1000L)
    batchTelemetry.lost(1, 1000L)
    val scraped = Telemetry.scrape()
    scraped should include(s"""obp_api_batch_writer_queue_depth{writer="$writer"} 2.0""")
    scraped should include(s"""obp_api_batch_writer_rows_total{result="lost",writer="$writer"} 1.0""")
    scraped should include(s"""obp_api_batch_writer_flushes_seconds_count{result="failure",writer="$writer"} 1""")
  }

  "The Telemetry port" should "serve Telemetry at /telemetry and nothing else" in {
    val name = unique("port_probe")
    Telemetry.counter(s"obp.api.test.$name").increment()
    val server = Telemetry.startServer("127.0.0.1", 0)
    try {
      val port = server.getAddress.getPort
      val (status, body) = httpGet(port, "/telemetry")
      status shouldBe 200
      body should include(s"obp_api_test_${name}_total 1.0")

      httpGet(port, "/metrics")._1 shouldBe 404
      httpGet(port, "/")._1 shouldBe 404
    } finally server.stop(0)
  }
}

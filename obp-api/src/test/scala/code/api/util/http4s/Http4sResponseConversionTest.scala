package code.api.util.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import net.liftweb.http._
import org.http4s.{Header, Headers, Response, Status}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import org.typelevel.ci.CIString

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unit tests for Lift → HTTP4S response conversion in Http4sLiftWebBridge.
 * 
 * Tests validate:
 * - Handling of all Lift response types (InMemoryResponse, StreamingResponse, OutputStreamResponse, BasicResponse)
 * - HTTP status code and header preservation
 * - Error response format consistency
 * - Streaming responses and callbacks
 * - Edge cases (empty responses, large payloads, special characters)
 * 
 * Validates: Requirements 2.4 (Task 2.5)
 */
class Http4sResponseConversionTest extends FeatureSpec with Matchers with GivenWhenThen {

  feature("Lift to HTTP4S response conversion - InMemoryResponse") {
    scenario("Convert simple InMemoryResponse with JSON body") {
      Given("A Lift InMemoryResponse with JSON data")
      val jsonData = """{"status":"success","message":"Test response"}"""
      val data = jsonData.getBytes("UTF-8")
      val headers = List(("Content-Type", "application/json; charset=utf-8"))
      val liftResponse = InMemoryResponse(data, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be preserved")
      http4sResponse.status.code should equal(200)

      And("Headers should be preserved")
      val contentType = http4sResponse.headers.get(CIString("Content-Type"))
      contentType should not be empty
      contentType.get.head.value should include("application/json")

      And("Body should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      new String(bodyBytes, "UTF-8") should equal(jsonData)
    }

    scenario("Convert InMemoryResponse with empty body") {
      Given("A Lift InMemoryResponse with empty body")
      val liftResponse = InMemoryResponse(Array.emptyByteArray, Nil, Nil, 204)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be 204 No Content")
      http4sResponse.status.code should equal(204)

      And("Body should be empty")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      bodyBytes.length should equal(0)
    }

    scenario("Convert InMemoryResponse with multiple headers") {
      Given("A Lift InMemoryResponse with multiple headers")
      val data = "test".getBytes("UTF-8")
      val headers = List(
        ("Content-Type", "application/json"),
        ("X-Custom-Header", "custom-value"),
        ("X-Request-Id", "12345"),
        ("Cache-Control", "no-cache")
      )
      val liftResponse = InMemoryResponse(data, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("All headers should be preserved")
      http4sResponse.headers.get(CIString("Content-Type")) should not be empty
      http4sResponse.headers.get(CIString("X-Custom-Header")).get.head.value should equal("custom-value")
      http4sResponse.headers.get(CIString("X-Request-Id")).get.head.value should equal("12345")
      http4sResponse.headers.get(CIString("Cache-Control")).get.head.value should equal("no-cache")
    }

    scenario("Convert InMemoryResponse with UTF-8 characters") {
      Given("A Lift InMemoryResponse with UTF-8 data")
      val utf8Data = """{"name":"Bänk Tëst","currency":"€"}"""
      val data = utf8Data.getBytes("UTF-8")
      val headers = List(("Content-Type", "application/json; charset=utf-8"))
      val liftResponse = InMemoryResponse(data, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("UTF-8 characters should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      new String(bodyBytes, "UTF-8") should equal(utf8Data)
    }

    scenario("Convert InMemoryResponse with large payload") {
      Given("A Lift InMemoryResponse with large payload (>1MB)")
      val largeData = ("x" * (1024 * 1024 + 100)).getBytes("UTF-8") // 1MB + 100 bytes
      val liftResponse = InMemoryResponse(largeData, Nil, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Large payload should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      bodyBytes.length should equal(largeData.length)
    }

    scenario("Convert InMemoryResponse with error status codes") {
      Given("Lift InMemoryResponses with various error status codes")
      val errorCodes = List(400, 401, 403, 404, 500, 502, 503)

      errorCodes.foreach { code =>
        val errorData = s"""{"code":$code,"message":"Error message"}""".getBytes("UTF-8")
        val liftResponse = InMemoryResponse(errorData, Nil, Nil, code)

        When(s"Response with status $code is converted")
        val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

        Then(s"Status code $code should be preserved")
        http4sResponse.status.code should equal(code)
      }
    }
  }

  feature("Lift to HTTP4S response conversion - StreamingResponse") {
    scenario("Convert StreamingResponse with callback") {
      Given("A Lift StreamingResponse with data and callback")
      val testData = "streaming test data"
      val inputStream = new ByteArrayInputStream(testData.getBytes("UTF-8"))
      val callbackInvoked = new AtomicBoolean(false)
      val onEnd = () => callbackInvoked.set(true)
      val headers = List(("Content-Type", "text/plain"))
      val liftResponse = StreamingResponse(inputStream, onEnd, -1, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be preserved")
      http4sResponse.status.code should equal(200)

      And("Headers should be preserved")
      http4sResponse.headers.get(CIString("Content-Type")).get.head.value should include("text/plain")

      And("Body should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      new String(bodyBytes, "UTF-8") should equal(testData)

      And("Callback should be invoked")
      callbackInvoked.get() should be(true)
    }

    scenario("Convert StreamingResponse with empty stream") {
      Given("A Lift StreamingResponse with empty stream")
      val emptyStream = new ByteArrayInputStream(Array.emptyByteArray)
      val callbackInvoked = new AtomicBoolean(false)
      val onEnd = () => callbackInvoked.set(true)
      val liftResponse = StreamingResponse(emptyStream, onEnd, 0, Nil, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Body should be empty")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      bodyBytes.length should equal(0)

      And("Callback should still be invoked")
      callbackInvoked.get() should be(true)
    }

    scenario("Convert StreamingResponse with large stream") {
      Given("A Lift StreamingResponse with large stream (>1MB)")
      val largeData = "x" * (1024 * 1024 + 100) // 1MB + 100 bytes
      val inputStream = new ByteArrayInputStream(largeData.getBytes("UTF-8"))
      val callbackInvoked = new AtomicBoolean(false)
      val onEnd = () => callbackInvoked.set(true)
      val liftResponse = StreamingResponse(inputStream, onEnd, -1, Nil, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Large stream should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      bodyBytes.length should equal(largeData.length)

      And("Callback should be invoked")
      callbackInvoked.get() should be(true)
    }

    scenario("Convert StreamingResponse ensures callback invocation on error") {
      Given("A Lift StreamingResponse with failing stream")
      val failingStream = new InputStream {
        override def read(): Int = throw new RuntimeException("Stream read error")
      }
      val callbackInvoked = new AtomicBoolean(false)
      val onEnd = () => callbackInvoked.set(true)
      val liftResponse = StreamingResponse(failingStream, onEnd, -1, Nil, Nil, 200)

      When("Response conversion is attempted")
      val result = try {
        liftResponseToHttp4sForTest(liftResponse)
        "no-error"
      } catch {
        case _: RuntimeException => "error-caught"
      }

      Then("Error should be caught")
      result should equal("error-caught")

      And("Callback should still be invoked (finally block)")
      callbackInvoked.get() should be(true)
    }
  }

  feature("Lift to HTTP4S response conversion - OutputStreamResponse") {
    scenario("Convert OutputStreamResponse with simple output") {
      Given("A Lift OutputStreamResponse")
      val testData = "output stream test data"
      val out: OutputStream => Unit = (os: OutputStream) => {
        os.write(testData.getBytes("UTF-8"))
        os.flush()
      }
      val headers = List(("Content-Type", "text/plain"))
      val liftResponse = OutputStreamResponse(out, -1, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be preserved")
      http4sResponse.status.code should equal(200)

      And("Headers should be preserved")
      http4sResponse.headers.get(CIString("Content-Type")).get.head.value should include("text/plain")

      And("Body should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      new String(bodyBytes, "UTF-8") should equal(testData)
    }

    scenario("Convert OutputStreamResponse with JSON output") {
      Given("A Lift OutputStreamResponse with JSON data")
      val jsonData = """{"status":"success","data":{"id":123,"name":"Test"}}"""
      val out: OutputStream => Unit = (os: OutputStream) => {
        os.write(jsonData.getBytes("UTF-8"))
        os.flush()
      }
      val headers = List(("Content-Type", "application/json"))
      val liftResponse = OutputStreamResponse(out, -1, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("JSON body should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      new String(bodyBytes, "UTF-8") should equal(jsonData)
    }

    scenario("Convert OutputStreamResponse with empty output") {
      Given("A Lift OutputStreamResponse with no output")
      val out: OutputStream => Unit = (os: OutputStream) => {
        os.flush()
      }
      val liftResponse = OutputStreamResponse(out, 0, Nil, Nil, 204)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be 204")
      http4sResponse.status.code should equal(204)

      And("Body should be empty")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      bodyBytes.length should equal(0)
    }

    scenario("Convert OutputStreamResponse with large output") {
      Given("A Lift OutputStreamResponse with large output (>1MB)")
      val largeData = "x" * (1024 * 1024 + 100) // 1MB + 100 bytes
      val out: OutputStream => Unit = (os: OutputStream) => {
        os.write(largeData.getBytes("UTF-8"))
        os.flush()
      }
      val liftResponse = OutputStreamResponse(out, -1, Nil, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Large output should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      bodyBytes.length should equal(largeData.length)
    }

    scenario("Convert OutputStreamResponse with UTF-8 output") {
      Given("A Lift OutputStreamResponse with UTF-8 data")
      val utf8Data = """{"name":"Tëst Bänk","symbol":"€£¥"}"""
      val out: OutputStream => Unit = (os: OutputStream) => {
        os.write(utf8Data.getBytes("UTF-8"))
        os.flush()
      }
      val headers = List(("Content-Type", "application/json; charset=utf-8"))
      val liftResponse = OutputStreamResponse(out, -1, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("UTF-8 characters should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      new String(bodyBytes, "UTF-8") should equal(utf8Data)
    }
  }

  feature("Lift to HTTP4S response conversion - BasicResponse (via NotFoundResponse)") {
    scenario("Convert NotFoundResponse with no body") {
      Given("A Lift NotFoundResponse")
      val liftResponse = NotFoundResponse()

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be 404")
      http4sResponse.status.code should equal(404)

      And("Body should be empty")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      bodyBytes.length should equal(0)
    }

    scenario("Convert InternalServerErrorResponse") {
      Given("A Lift InternalServerErrorResponse")
      val liftResponse = InternalServerErrorResponse()

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be 500")
      http4sResponse.status.code should equal(500)
    }

    scenario("Convert ForbiddenResponse") {
      Given("A Lift ForbiddenResponse")
      val liftResponse = ForbiddenResponse()

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be 403")
      http4sResponse.status.code should equal(403)
    }

    scenario("Convert UnauthorizedResponse") {
      Given("A Lift UnauthorizedResponse")
      val liftResponse = UnauthorizedResponse("DirectLogin")

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be 401")
      http4sResponse.status.code should equal(401)
    }

    scenario("Convert BadResponse") {
      Given("A Lift BadResponse")
      val liftResponse = BadResponse()

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be 400")
      http4sResponse.status.code should equal(400)
    }
  }

  feature("Lift to HTTP4S response conversion - Content-Type handling") {
    scenario("Add default Content-Type when missing") {
      Given("A Lift InMemoryResponse without Content-Type header")
      val data = """{"status":"success"}""".getBytes("UTF-8")
      val liftResponse = InMemoryResponse(data, Nil, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Default Content-Type should be added")
      val contentType = http4sResponse.headers.get(CIString("Content-Type"))
      contentType should not be empty
      contentType.get.head.value should include("application/json")
    }

    scenario("Preserve existing Content-Type header") {
      Given("A Lift InMemoryResponse with Content-Type header")
      val data = "plain text".getBytes("UTF-8")
      val headers = List(("Content-Type", "text/plain; charset=utf-8"))
      val liftResponse = InMemoryResponse(data, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Existing Content-Type should be preserved")
      val contentType = http4sResponse.headers.get(CIString("Content-Type"))
      contentType should not be empty
      contentType.get.head.value should equal("text/plain; charset=utf-8")
    }

    scenario("Handle various Content-Type formats") {
      Given("Lift responses with various Content-Type formats")
      val contentTypes = List(
        "application/json",
        "application/json; charset=utf-8",
        "text/html",
        "text/plain; charset=iso-8859-1",
        "application/xml",
        "application/octet-stream"
      )

      contentTypes.foreach { ct =>
        val data = "test".getBytes("UTF-8")
        val headers = List(("Content-Type", ct))
        val liftResponse = InMemoryResponse(data, headers, Nil, 200)

        When(s"Response with Content-Type '$ct' is converted")
        val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

        Then("Content-Type should be preserved")
        val contentType = http4sResponse.headers.get(CIString("Content-Type"))
        contentType should not be empty
        contentType.get.head.value should equal(ct)
      }
    }
  }

  feature("Lift to HTTP4S response conversion - Error responses") {
    scenario("Convert error response with JSON body") {
      Given("A Lift error response with JSON error message")
      val errorJson = """{"code":400,"message":"Invalid request"}"""
      val data = errorJson.getBytes("UTF-8")
      val headers = List(("Content-Type", "application/json"))
      val liftResponse = InMemoryResponse(data, headers, Nil, 400)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Error status code should be preserved")
      http4sResponse.status.code should equal(400)

      And("Error body should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      new String(bodyBytes, "UTF-8") should equal(errorJson)
    }

    scenario("Convert 404 Not Found response") {
      Given("A Lift 404 response")
      val errorJson = """{"code":404,"message":"Resource not found"}"""
      val data = errorJson.getBytes("UTF-8")
      val liftResponse = InMemoryResponse(data, Nil, Nil, 404)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("404 status should be preserved")
      http4sResponse.status.code should equal(404)
    }

    scenario("Convert 500 Internal Server Error response") {
      Given("A Lift 500 response")
      val errorJson = """{"code":500,"message":"Internal server error"}"""
      val data = errorJson.getBytes("UTF-8")
      val liftResponse = InMemoryResponse(data, Nil, Nil, 500)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("500 status should be preserved")
      http4sResponse.status.code should equal(500)
    }

    scenario("Convert 401 Unauthorized response") {
      Given("A Lift 401 response")
      val errorJson = """{"code":401,"message":"Authentication required"}"""
      val data = errorJson.getBytes("UTF-8")
      val headers = List(("WWW-Authenticate", "DirectLogin"))
      val liftResponse = InMemoryResponse(data, headers, Nil, 401)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("401 status should be preserved")
      http4sResponse.status.code should equal(401)

      And("WWW-Authenticate header should be preserved")
      http4sResponse.headers.get(CIString("WWW-Authenticate")).get.head.value should equal("DirectLogin")
    }
  }

  feature("Lift to HTTP4S response conversion - Edge cases") {
    scenario("Handle response with special characters in headers") {
      Given("A Lift response with special characters in header values")
      val headers = List(
        ("X-Special", "value with spaces, commas, and \"quotes\""),
        ("X-Unicode", "Tëst Hëädër Välüë")
      )
      val liftResponse = InMemoryResponse(Array.emptyByteArray, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Special characters in headers should be preserved")
      http4sResponse.headers.get(CIString("X-Special")).get.head.value should equal("value with spaces, commas, and \"quotes\"")
      http4sResponse.headers.get(CIString("X-Unicode")).get.head.value should equal("Tëst Hëädër Välüë")
    }

    scenario("Handle response with many headers") {
      Given("A Lift response with many headers")
      val headers = (1 to 50).map(i => (s"X-Header-$i", s"value-$i")).toList
      val liftResponse = InMemoryResponse(Array.emptyByteArray, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("All headers should be preserved")
      (1 to 50).foreach { i =>
        http4sResponse.headers.get(CIString(s"X-Header-$i")).get.head.value should equal(s"value-$i")
      }
    }

    scenario("Handle response with binary data") {
      Given("A Lift response with binary data")
      val binaryData = (0 to 255).map(_.toByte).toArray
      val headers = List(("Content-Type", "application/octet-stream"))
      val liftResponse = InMemoryResponse(binaryData, headers, Nil, 200)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Binary data should be preserved")
      val bodyBytes = http4sResponse.body.compile.to(Array).unsafeRunSync()
      bodyBytes should equal(binaryData)
    }

    scenario("Handle response with invalid status code") {
      Given("A Lift response with unusual status code")
      val liftResponse = InMemoryResponse(Array.emptyByteArray, Nil, Nil, 999)

      When("Response is converted to HTTP4S")
      val http4sResponse = liftResponseToHttp4sForTest(liftResponse)

      Then("Status code should be handled gracefully")
      // HTTP4S will either accept it or convert to 500
      http4sResponse.status.code should (equal(999) or equal(500))
    }
  }

  // Helper method to access private liftResponseToHttp4s method for testing
  private def liftResponseToHttp4sForTest(response: LiftResponse): Response[IO] = {
    // Use reflection to access private method
    val method = Http4sLiftWebBridge.getClass.getDeclaredMethod(
      "liftResponseToHttp4s",
      classOf[LiftResponse]
    )
    method.setAccessible(true)
    method.invoke(Http4sLiftWebBridge, response).asInstanceOf[IO[Response[IO]]].unsafeRunSync()
  }
}

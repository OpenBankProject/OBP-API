package code.api.util.http4s

import cats.effect.IO
import code.api.util.ExampleValue
import net.liftweb.http.Req
import org.http4s.{Header, Method, Request, Uri}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import org.typelevel.ci.CIString

/**
 * Unit tests for HTTP4S → Lift Req conversion in Http4sLiftWebBridge.
 * 
 * Tests validate:
 * - Header parameter extraction and conversion
 * - Query parameter handling for complex scenarios
 * - Request body handling for all content types
 * - Edge cases (empty bodies, special characters, large payloads)
 * 
 * Validates: Requirements 2.2
 */
class Http4sCallContextBuilderTest extends FeatureSpec with Matchers with GivenWhenThen {

  feature("HTTP4S to Lift Req conversion - Header handling") {
    scenario("Extract single header value") {
      Given("An HTTP4S request with a single header")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      ).putHeaders(Header.Raw(CIString("X-Custom-Header"), "test-value"))

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Header should be accessible through Lift Req")
      val headerValue = liftReq.request.headers("X-Custom-Header")
      headerValue should not be empty
      headerValue.head should equal("test-value")
    }

    scenario("Extract multiple values for same header") {
      Given("An HTTP4S request with multiple values for same header")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      ).putHeaders(
        Header.Raw(CIString("Accept"), "application/json"),
        Header.Raw(CIString("Accept"), "text/html")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("All header values should be accessible")
      val headerValues = liftReq.request.headers("Accept")
      headerValues.size should be >= 1
      headerValues should contain("application/json")
    }

    scenario("Extract Authorization header") {
      Given("An HTTP4S request with Authorization header")
      val authValue = s"""DirectLogin username=\"test\", password=\"${ExampleValue.passwordExample.value}\", consumer_key=\"key\"""""
      val request = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8086/my/logins/direct")
      ).putHeaders(Header.Raw(CIString("Authorization"), authValue))

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Authorization header should be preserved exactly")
      val authHeader = liftReq.request.headers("Authorization")
      authHeader should not be empty
      authHeader.head should equal(authValue)
    }

    scenario("Handle headers with special characters") {
      Given("An HTTP4S request with headers containing special characters")
      val specialValue = "value with spaces, commas, and \"quotes\""
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      ).putHeaders(Header.Raw(CIString("X-Special"), specialValue))

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Special characters should be preserved")
      val headerValue = liftReq.request.headers("X-Special")
      headerValue should not be empty
      headerValue.head should equal(specialValue)
    }

    scenario("Handle case-insensitive header lookup") {
      Given("An HTTP4S request with mixed-case headers")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      ).putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Header should be accessible with different case")
      liftReq.request.headers("content-type") should not be empty
      liftReq.request.headers("Content-Type") should not be empty
      liftReq.request.headers("CONTENT-TYPE") should not be empty
    }
  }

  feature("HTTP4S to Lift Req conversion - Query parameter handling") {
    scenario("Extract single query parameter") {
      Given("An HTTP4S request with a single query parameter")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks?limit=10")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Query parameter should be accessible")
      val paramValue = liftReq.request.param("limit")
      paramValue should not be empty
      paramValue.head should equal("10")
    }

    scenario("Extract multiple query parameters") {
      Given("An HTTP4S request with multiple query parameters")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks?limit=10&offset=20&sort=name")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("All query parameters should be accessible")
      liftReq.request.param("limit").head should equal("10")
      liftReq.request.param("offset").head should equal("20")
      liftReq.request.param("sort").head should equal("name")
    }

    scenario("Handle query parameters with multiple values") {
      Given("An HTTP4S request with repeated query parameter")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks?tag=retail&tag=commercial")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("All parameter values should be accessible")
      val tagValues = liftReq.request.param("tag")
      tagValues.size should equal(2)
      tagValues should contain("retail")
      tagValues should contain("commercial")
    }

    scenario("Handle query parameters with special characters") {
      Given("An HTTP4S request with URL-encoded query parameters")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks?name=Test%20Bank&symbol=%24")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Parameters should be decoded correctly")
      liftReq.request.param("name").head should equal("Test Bank")
      liftReq.request.param("symbol").head should equal("$")
    }

    scenario("Handle empty query parameter values") {
      Given("An HTTP4S request with empty query parameter")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks?filter=")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Empty parameter should be accessible")
      val filterValue = liftReq.request.param("filter")
      filterValue should not be empty
      filterValue.head should equal("")
    }
  }

  feature("HTTP4S to Lift Req conversion - Request body handling") {
    scenario("Handle empty request body") {
      Given("An HTTP4S GET request with no body")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Body should be accessible as empty")
      val inputStream = liftReq.request.inputStream
      inputStream.available() should equal(0)
    }

    scenario("Handle JSON request body") {
      Given("An HTTP4S POST request with JSON body")
      val jsonBody = """{"name":"Test Bank","id":"test-bank-123"}"""
      val request = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      ).withEntity(jsonBody)
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

      When("Request is converted through bridge")
      val bodyBytes = jsonBody.getBytes("UTF-8")
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Body should be accessible and parseable")
      val inputStream = liftReq.request.inputStream
      val bodyContent = scala.io.Source.fromInputStream(inputStream).mkString
      bodyContent should equal(jsonBody)
      
      And("Content-Type should be preserved")
      liftReq.request.contentType should not be empty
      liftReq.request.contentType.openOr("").toString should include("application/json")
    }

    scenario("Handle request body with UTF-8 characters") {
      Given("An HTTP4S POST request with UTF-8 body")
      val utf8Body = """{"name":"Bänk Tëst","description":"Tëst with spëcial çhars: €£¥"}"""
      val request = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      ).withEntity(utf8Body)
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json; charset=utf-8"))

      When("Request is converted through bridge")
      val bodyBytes = utf8Body.getBytes("UTF-8")
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("UTF-8 characters should be preserved")
      val inputStream = liftReq.request.inputStream
      val bodyContent = scala.io.Source.fromInputStream(inputStream, "UTF-8").mkString
      bodyContent should equal(utf8Body)
    }

    scenario("Handle large request body") {
      Given("An HTTP4S POST request with large body (>1MB)")
      val largeBody = "x" * (1024 * 1024 + 100) // 1MB + 100 bytes
      val request = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      ).withEntity(largeBody)

      When("Request is converted through bridge")
      val bodyBytes = largeBody.getBytes("UTF-8")
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Large body should be accessible")
      val inputStream = liftReq.request.inputStream
      val bodyContent = scala.io.Source.fromInputStream(inputStream).mkString
      bodyContent.length should equal(largeBody.length)
    }

    scenario("Handle request body with special characters") {
      Given("An HTTP4S POST request with special characters in body")
      val specialBody = """{"data":"Line1\nLine2\tTabbed\r\nWindows\u0000Null"}"""
      val request = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      ).withEntity(specialBody)
        .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

      When("Request is converted through bridge")
      val bodyBytes = specialBody.getBytes("UTF-8")
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Special characters should be preserved")
      val inputStream = liftReq.request.inputStream
      val bodyContent = scala.io.Source.fromInputStream(inputStream).mkString
      bodyContent should equal(specialBody)
    }
  }

  feature("HTTP4S to Lift Req conversion - HTTP method and URI") {
    scenario("Preserve GET method") {
      Given("An HTTP4S GET request")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Method should be GET")
      liftReq.request.method should equal("GET")
    }

    scenario("Preserve POST method") {
      Given("An HTTP4S POST request")
      val request = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Method should be POST")
      liftReq.request.method should equal("POST")
    }

    scenario("Preserve PUT method") {
      Given("An HTTP4S PUT request")
      val request = Request[IO](
        method = Method.PUT,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks/bank-id")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Method should be PUT")
      liftReq.request.method should equal("PUT")
    }

    scenario("Preserve DELETE method") {
      Given("An HTTP4S DELETE request")
      val request = Request[IO](
        method = Method.DELETE,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks/bank-id")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Method should be DELETE")
      liftReq.request.method should equal("DELETE")
    }

    scenario("Preserve URI path") {
      Given("An HTTP4S request with complex path")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks/bank-id/accounts/account-id")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("URI path should be preserved")
      liftReq.request.uri should include("/obp/v5.0.0/banks/bank-id/accounts/account-id")
    }

    scenario("Preserve URI with query string") {
      Given("An HTTP4S request with query string")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks?limit=10&offset=20")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Query string should be accessible")
      liftReq.request.queryString should not be empty
      liftReq.request.queryString.openOr("") should include("limit=10")
    }
  }

  feature("HTTP4S to Lift Req conversion - Edge cases") {
    scenario("Handle request with no headers") {
      Given("An HTTP4S request with minimal headers")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Request should be valid")
      liftReq.request.method should equal("GET")
      liftReq.request.uri should not be empty
    }

    scenario("Handle request with very long URI") {
      Given("An HTTP4S request with very long URI")
      val longPath = "/obp/v5.0.0/" + ("segment/" * 50) + "endpoint"
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"http://localhost:8086$longPath")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("Long URI should be preserved")
      liftReq.request.uri should include(longPath)
    }

    scenario("Handle request with many query parameters") {
      Given("An HTTP4S request with many query parameters")
      val params = (1 to 50).map(i => s"param$i=$i").mkString("&")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"http://localhost:8086/obp/v5.0.0/banks?$params")
      )

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("All query parameters should be accessible")
      (1 to 50).foreach { i =>
        liftReq.request.param(s"param$i").head should equal(i.toString)
      }
    }

    scenario("Handle request with many headers") {
      Given("An HTTP4S request with many headers")
      var request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
      )
      (1 to 50).foreach { i =>
        request = request.putHeaders(Header.Raw(CIString(s"X-Header-$i"), s"value-$i"))
      }

      When("Request is converted through bridge")
      val bodyBytes = Array.emptyByteArray
      val liftReq = buildLiftReqForTest(request, bodyBytes)

      Then("All headers should be accessible")
      (1 to 50).foreach { i =>
        liftReq.request.headers(s"X-Header-$i").head should equal(s"value-$i")
      }
    }

    scenario("Handle Content-Type variations") {
      Given("An HTTP4S request with various Content-Type formats")
      val contentTypes = List(
        ("application/json", "application/json"),
        ("application/json; charset=utf-8", "application/json"),
        ("application/json;charset=UTF-8", "application/json"),
        ("application/json ; charset=utf-8", "application/json"),
        ("text/plain", "text/plain"),
        ("application/x-www-form-urlencoded", "application/x-www-form-urlencoded")
      )

      contentTypes.foreach { case (ct, expectedPrefix) =>
        val request = Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString("http://localhost:8086/obp/v5.0.0/banks")
        ).withEntity("test body")
          .withContentType(org.http4s.headers.`Content-Type`.parse(ct).getOrElse(
            org.http4s.headers.`Content-Type`(org.http4s.MediaType.application.json)
          ))

        When(s"Request with Content-Type '$ct' is converted")
        val bodyBytes = "test body".getBytes("UTF-8")
        val liftReq = buildLiftReqForTest(request, bodyBytes)

        Then("Content-Type should be accessible")
        liftReq.request.contentType should not be empty
        liftReq.request.contentType.openOr("").toString should include(expectedPrefix)
      }
    }
  }

  // Helper method to access private buildLiftReq method for testing
  private def buildLiftReqForTest(req: Request[IO], body: Array[Byte]): Req = {
    // Use reflection to access private method
    val method = Http4sLiftWebBridge.getClass.getDeclaredMethod(
      "buildLiftReq",
      classOf[Request[IO]],
      classOf[Array[Byte]]
    )
    method.setAccessible(true)
    method.invoke(Http4sLiftWebBridge, req, body).asInstanceOf[Req]
  }
}

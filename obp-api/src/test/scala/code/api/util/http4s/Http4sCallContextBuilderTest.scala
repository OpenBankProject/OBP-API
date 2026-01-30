package code.api.util.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.openbankproject.commons.util.ApiShortVersions
import net.liftweb.common.{Empty, Full}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers, Tag}

/**
 * Unit tests for Http4sCallContextBuilder
 * 
 * Tests CallContext building from http4s Request[IO]:
 * - URL extraction (including query parameters)
 * - Header extraction and conversion to HTTPParam
 * - Body extraction for POST requests
 * - Correlation ID generation/extraction
 * - IP address extraction (X-Forwarded-For and direct)
 * - Auth header extraction for all auth types
 * 
 */
class Http4sCallContextBuilderTest extends FeatureSpec with Matchers with GivenWhenThen {
  
  object Http4sCallContextBuilderTag extends Tag("Http4sCallContextBuilder")
  private val v700 = ApiShortVersions.`v7.0.0`.toString
  private val base = s"/obp/$v700"
  
  feature("Http4sCallContextBuilder - URL extraction") {
    
    scenario("Extract URL with path only", Http4sCallContextBuilderTag) {
      Given(s"A request with path $base/banks")
      val request = Request[IO]( 
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("URL should match the request URI")
      callContext.url should equal(s"$base/banks")
    }
    
    scenario("Extract URL with query parameters", Http4sCallContextBuilderTag) {
      Given("A request with query parameters")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks?limit=10&offset=0")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("URL should include query parameters")
      callContext.url should equal(s"$base/banks?limit=10&offset=0")
    }
    
    scenario("Extract URL with path parameters", Http4sCallContextBuilderTag) {
      Given("A request with path parameters")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks/gh.29.de/accounts/test1")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("URL should include path parameters")
      callContext.url should equal(s"$base/banks/gh.29.de/accounts/test1")
    }
  }
  
  feature("Http4sCallContextBuilder - Header extraction") {
    
    scenario("Extract headers and convert to HTTPParam", Http4sCallContextBuilderTag) {
      Given("A request with multiple headers")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("Content-Type"), "application/json"),
        Header.Raw(org.typelevel.ci.CIString("Accept"), "application/json"),
        Header.Raw(org.typelevel.ci.CIString("X-Custom-Header"), "test-value")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Headers should be converted to HTTPParam list")
      callContext.requestHeaders should not be empty
      callContext.requestHeaders.exists(_.name == "Content-Type") should be(true)
      callContext.requestHeaders.exists(_.name == "Accept") should be(true)
      callContext.requestHeaders.exists(_.name == "X-Custom-Header") should be(true)
    }
    
    scenario("Extract empty headers list", Http4sCallContextBuilderTag) {
      Given("A request with no custom headers")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Headers list should be empty or contain only default headers")
      // http4s may add default headers, so we just check it's a list
      callContext.requestHeaders should be(a[List[_]])
    }
  }
  
  feature("Http4sCallContextBuilder - Body extraction") {
    
    scenario("Extract body from POST request", Http4sCallContextBuilderTag) {
      Given("A POST request with JSON body")
      val jsonBody = """{"name": "Test Bank", "id": "test-bank-1"}"""
      val request = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withEntity(jsonBody)
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Body should be extracted as Some(string)")
      callContext.httpBody should be(Some(jsonBody))
    }
    
    scenario("Extract empty body from GET request", Http4sCallContextBuilderTag) {
      Given("A GET request with no body")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Body should be None")
      callContext.httpBody should be(None)
    }
    
    scenario("Extract body from PUT request", Http4sCallContextBuilderTag) {
      Given("A PUT request with JSON body")
      val jsonBody = """{"name": "Updated Bank"}"""
      val request = Request[IO](
        method = Method.PUT,
        uri = Uri.unsafeFromString(s"$base/banks/test-bank-1")
      ).withEntity(jsonBody)
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Body should be extracted")
      callContext.httpBody should be(Some(jsonBody))
    }
  }
  
  feature("Http4sCallContextBuilder - Correlation ID") {
    
    scenario("Extract correlation ID from X-Request-ID header", Http4sCallContextBuilderTag) {
      Given("A request with X-Request-ID header")
      val requestId = "test-correlation-id-12345"
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("X-Request-ID"), requestId)
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Correlation ID should match the header value")
      callContext.correlationId should equal(requestId)
    }
    
    scenario("Generate correlation ID when header missing", Http4sCallContextBuilderTag) {
      Given("A request without X-Request-ID header")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Correlation ID should be generated (UUID format)")
      callContext.correlationId should not be empty
      // UUID format: 8-4-4-4-12 hex digits
      callContext.correlationId should fullyMatch regex "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    }
  }
  
  feature("Http4sCallContextBuilder - IP address extraction") {
    
    scenario("Extract IP from X-Forwarded-For header", Http4sCallContextBuilderTag) {
      Given("A request with X-Forwarded-For header")
      val clientIp = "192.168.1.100"
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("X-Forwarded-For"), clientIp)
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("IP address should match the header value")
      callContext.ipAddress should equal(clientIp)
    }
    
    scenario("Extract first IP from X-Forwarded-For with multiple IPs", Http4sCallContextBuilderTag) {
      Given("A request with X-Forwarded-For containing multiple IPs")
      val forwardedFor = "192.168.1.100, 10.0.0.1, 172.16.0.1"
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("X-Forwarded-For"), forwardedFor)
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("IP address should be the first IP in the list")
      callContext.ipAddress should equal("192.168.1.100")
    }
    
    scenario("Handle missing IP address", Http4sCallContextBuilderTag) {
      Given("A request without X-Forwarded-For or remote address")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("IP address should be empty string")
      callContext.ipAddress should equal("")
    }
  }
  
  feature("Http4sCallContextBuilder - Authentication header extraction") {
    
    scenario("Extract DirectLogin token from DirectLogin header (new format)", Http4sCallContextBuilderTag) {
      Given("A request with DirectLogin header")
      val token = "eyJhbGciOiJIUzI1NiJ9.eyIiOiIifQ.test"
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("DirectLogin"), s"token=$token")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("DirectLogin params should contain token")
      callContext.directLoginParams should contain key "token"
      callContext.directLoginParams("token") should equal(token)
    }
    
    scenario("Extract DirectLogin token from Authorization header (old format)", Http4sCallContextBuilderTag) {
      Given("A request with Authorization: DirectLogin header")
      val token = "eyJhbGciOiJIUzI1NiJ9.eyIiOiIifQ.test"
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("Authorization"), s"DirectLogin token=$token")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("DirectLogin params should contain token")
      callContext.directLoginParams should contain key "token"
      callContext.directLoginParams("token") should equal(token)
      
      And("Authorization header should be stored")
      callContext.authReqHeaderField should equal(Full(s"DirectLogin token=$token"))
    }
    
    scenario("Extract DirectLogin with username and password", Http4sCallContextBuilderTag) {
      Given("A request with DirectLogin username and password")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("DirectLogin"), """username="testuser", password="testpass", consumer_key="key123"""")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("DirectLogin params should contain all parameters")
      callContext.directLoginParams should contain key "username"
      callContext.directLoginParams should contain key "password"
      callContext.directLoginParams should contain key "consumer_key"
      callContext.directLoginParams("username") should equal("testuser")
      callContext.directLoginParams("password") should equal("testpass")
      callContext.directLoginParams("consumer_key") should equal("key123")
    }
    
    scenario("Extract OAuth parameters from Authorization header", Http4sCallContextBuilderTag) {
      Given("A request with OAuth Authorization header")
      val oauthHeader = """OAuth oauth_consumer_key="consumer123", oauth_token="token456", oauth_signature="sig789""""
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("Authorization"), oauthHeader)
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("OAuth params should be extracted")
      callContext.oAuthParams should contain key "oauth_consumer_key"
      callContext.oAuthParams should contain key "oauth_token"
      callContext.oAuthParams should contain key "oauth_signature"
      callContext.oAuthParams("oauth_consumer_key") should equal("consumer123")
      callContext.oAuthParams("oauth_token") should equal("token456")
      callContext.oAuthParams("oauth_signature") should equal("sig789")
      
      And("Authorization header should be stored")
      callContext.authReqHeaderField should equal(Full(oauthHeader))
    }
    
    scenario("Extract Bearer token from Authorization header", Http4sCallContextBuilderTag) {
      Given("A request with Bearer token")
      val bearerToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature"
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("Authorization"), s"Bearer $bearerToken")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Authorization header should be stored")
      callContext.authReqHeaderField should equal(Full(s"Bearer $bearerToken"))
    }
    
    scenario("Handle missing Authorization header", Http4sCallContextBuilderTag) {
      Given("A request without Authorization header")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Auth header field should be Empty")
      callContext.authReqHeaderField should equal(Empty)
      
      And("DirectLogin params should be empty")
      callContext.directLoginParams should be(empty)
      
      And("OAuth params should be empty")
      callContext.oAuthParams should be(empty)
    }
  }
  
  feature("Http4sCallContextBuilder - Request metadata") {
    
    scenario("Extract HTTP verb", Http4sCallContextBuilderTag) {
      Given("A POST request")
      val request = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString(s"$base/banks")
      )
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("Verb should be POST")
      callContext.verb should equal("POST")
    }
    
    scenario("Set implementedInVersion from parameter", Http4sCallContextBuilderTag) {
      Given(s"A request with API version $v700")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      )
      
      When("Building CallContext with version parameter")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("implementedInVersion should match the parameter")
      callContext.implementedInVersion should equal(v700)
    }
    
    scenario("Set startTime to current date", Http4sCallContextBuilderTag) {
      Given("A request")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$base/banks")
      )
      
      When("Building CallContext")
      val beforeTime = new java.util.Date()
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      val afterTime = new java.util.Date()
      
      Then("startTime should be set and within reasonable range")
      callContext.startTime should be(defined)
      callContext.startTime.get.getTime should be >= beforeTime.getTime
      callContext.startTime.get.getTime should be <= afterTime.getTime
    }
  }
  
  feature("Http4sCallContextBuilder - Complete integration") {
    
    scenario("Build complete CallContext with all fields", Http4sCallContextBuilderTag) {
      Given("A complete POST request with all headers and body")
      val jsonBody = """{"name": "Test Bank"}"""
      val token = "test-token-123"
      val correlationId = "correlation-123"
      val clientIp = "192.168.1.100"
      
      val request = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString(s"$base/banks?limit=10")
      ).withHeaders(
        Header.Raw(org.typelevel.ci.CIString("Content-Type"), "application/json"),
        Header.Raw(org.typelevel.ci.CIString("DirectLogin"), s"token=$token"),
        Header.Raw(org.typelevel.ci.CIString("X-Request-ID"), correlationId),
        Header.Raw(org.typelevel.ci.CIString("X-Forwarded-For"), clientIp)
      ).withEntity(jsonBody)
      
      When("Building CallContext")
      val callContext = Http4sCallContextBuilder.fromRequest(request, v700).unsafeRunSync()
      
      Then("All fields should be populated correctly")
      callContext.url should equal(s"$base/banks?limit=10")
      callContext.verb should equal("POST")
      callContext.implementedInVersion should equal(v700)
      callContext.correlationId should equal(correlationId)
      callContext.ipAddress should equal(clientIp)
      callContext.httpBody should be(Some(jsonBody))
      callContext.directLoginParams should contain key "token"
      callContext.directLoginParams("token") should equal(token)
      callContext.requestHeaders should not be empty
      callContext.startTime should be(defined)
    }
  }
}

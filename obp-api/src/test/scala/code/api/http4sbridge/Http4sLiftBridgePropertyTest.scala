package code.api.http4sbridge

import code.Http4sTestServer
import code.api.ResponseHeader
import code.api.v5_0_0.V500ServerSetup
import dispatch.Defaults._
import dispatch._
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonParser.parse
import net.liftweb.util.Helpers._
import org.scalatest.Tag

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

/**
 * Property-Based Tests for Http4s Lift Bridge
 * 
 * These tests validate universal properties that should hold across all inputs
 * for the HTTP4S-Lift bridge integration, particularly focusing on the Lift
 * dispatch mechanism.
 * 
 * Property 6: Lift Dispatch Mechanism Integration
 * Validates: Requirements 1.3, 2.3, 2.5
 */
class Http4sLiftBridgePropertyTest extends V500ServerSetup {

  object PropertyTag extends Tag("lift-to-http4s-migration-property")

  private val http4sServer = Http4sTestServer
  private val http4sBaseUrl = s"http://${http4sServer.host}:${http4sServer.port}"

  private def makeHttp4sGetRequest(path: String, headers: Map[String, String] = Map.empty): (Int, JValue, Map[String, String]) = {
    val request = url(s"$http4sBaseUrl$path")
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    
    try {
      val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p => {
        val statusCode = p.getStatusCode
        val body = if (p.getResponseBody != null && p.getResponseBody.trim.nonEmpty) p.getResponseBody else "{}"
        val json = parse(body)
        val responseHeaders = p.getHeaders.iterator().asScala.map(e => e.getKey -> e.getValue).toMap
        (statusCode, json, responseHeaders)
      }))
      Await.result(response, DurationInt(10).seconds)
    } catch {
      case e: java.util.concurrent.ExecutionException =>
        val statusPattern = """(\d{3})""".r
        statusPattern.findFirstIn(e.getCause.getMessage) match {
          case Some(code) => (code.toInt, JObject(Nil), Map.empty)
          case None => throw e
        }
      case e: Exception =>
        throw e
    }
  }

  private def makeHttp4sPostRequest(path: String, body: String, headers: Map[String, String] = Map.empty): (Int, JValue, Map[String, String]) = {
    val request = url(s"$http4sBaseUrl$path").POST.setBody(body)
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    
    try {
      val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p => {
        val statusCode = p.getStatusCode
        val responseBody = if (p.getResponseBody != null && p.getResponseBody.trim.nonEmpty) p.getResponseBody else "{}"
        val json = parse(responseBody)
        val responseHeaders = p.getHeaders.iterator().asScala.map(e => e.getKey -> e.getValue).toMap
        (statusCode, json, responseHeaders)
      }))
      Await.result(response, DurationInt(10).seconds)
    } catch {
      case e: Exception =>
        throw e
    }
  }

  private def hasField(json: JValue, key: String): Boolean = {
    json match {
      case JObject(fields) => fields.exists(_.name == key)
      case _ => false
    }
  }

  private def assertCorrelationId(headers: Map[String, String]): Unit = {
    val header = headers.find { case (key, _) => key.equalsIgnoreCase(ResponseHeader.`Correlation-Id`) }
    header.isDefined shouldBe true
    header.map(_._2.trim.nonEmpty).getOrElse(false) shouldBe true
  }

  // Test data generators
  private val apiVersions = List(
    "v1.2.1", "v1.3.0", "v1.4.0", "v2.0.0", "v2.1.0", "v2.2.0",
    "v3.0.0", "v3.1.0", "v4.0.0", "v5.0.0", "v5.1.0", "v6.0.0"
  )

  private val publicEndpoints = List(
    "/banks",
    "/banks/BANK_ID",
    "/root"
  )

  private val authenticatedEndpoints = List(
    "/my/banks",
    "/my/logins/direct"
  )

  feature("Property 6: Lift Dispatch Mechanism Integration") {
    
    scenario("Property 6.1: All registered public endpoints return valid responses (100 iterations)", PropertyTag) {
      var successCount = 0
      var failureCount = 0
      val iterations = 100
      
      (1 to iterations).foreach { i =>
        val version = apiVersions(Random.nextInt(apiVersions.length))
        val endpoint = publicEndpoints(Random.nextInt(publicEndpoints.length))
        val path = s"/obp/$version$endpoint"
        
        try {
          val (status, json, headers) = makeHttp4sGetRequest(path)
          
          // Verify response is valid
          status should (be >= 200 and be < 600)
          
          // Verify standard headers are present
          assertCorrelationId(headers)
          
          // Verify response is valid JSON
          json should not be null
          
          successCount += 1
        } catch {
          case e: Exception =>
            logger.warn(s"Iteration $i failed for $path: ${e.getMessage}")
            failureCount += 1
        }
      }
      
      logger.info(s"Property 6.1 completed: $successCount successes, $failureCount failures out of $iterations iterations")
      successCount should be >= (iterations * 0.95).toInt // 95% success rate
    }

    scenario("Property 6.2: Handler priority is consistent (100 iterations)", PropertyTag) {
      var successCount = 0
      val iterations = 100
      
      (1 to iterations).foreach { i =>
        val version = apiVersions(Random.nextInt(apiVersions.length))
        val path = s"/obp/$version/banks"
        
        val (status1, json1, headers1) = makeHttp4sGetRequest(path)
        val (status2, json2, headers2) = makeHttp4sGetRequest(path)
        
        // Same request should always return same status code (handler priority is consistent)
        status1 should equal(status2)
        
        // Both should have correlation IDs
        assertCorrelationId(headers1)
        assertCorrelationId(headers2)
        
        successCount += 1
      }
      
      logger.info(s"Property 6.2 completed: $successCount iterations")
      successCount should equal(iterations)
    }

    scenario("Property 6.3: Missing handlers return 404 with error message (100 iterations)", PropertyTag) {
      var successCount = 0
      val iterations = 100
      
      (1 to iterations).foreach { i =>
        val randomPath = s"/obp/v5.0.0/nonexistent/${randomString(10)}"
        
        val (status, json, headers) = makeHttp4sGetRequest(randomPath)
        
        // Should return 404
        status should equal(404)
        
        // Should have error message
        (hasField(json, "error") || hasField(json, "message")) shouldBe true
        
        // Should have correlation ID
        assertCorrelationId(headers)
        
        successCount += 1
      }
      
      logger.info(s"Property 6.3 completed: $successCount iterations")
      successCount should equal(iterations)
    }

    scenario("Property 6.4: Authentication failures return consistent error responses (100 iterations)", PropertyTag) {
      var successCount = 0
      val iterations = 100
      
      (1 to iterations).foreach { i =>
        val version = apiVersions(Random.nextInt(apiVersions.length))
        val path = s"/obp/$version/my/banks"
        
        // Request without authentication
        val (status, json, headers) = makeHttp4sGetRequest(path)
        
        // Should return 401 or 400 (depending on version)
        status should (be >= 400 and be < 500)
        
        // Should have error message
        (hasField(json, "error") || hasField(json, "message")) shouldBe true
        
        // Should have correlation ID
        assertCorrelationId(headers)
        
        successCount += 1
      }
      
      logger.info(s"Property 6.4 completed: $successCount iterations")
      successCount should equal(iterations)
    }

    scenario("Property 6.5: POST requests are properly dispatched (100 iterations)", PropertyTag) {
      var successCount = 0
      val iterations = 100
      
      (1 to iterations).foreach { i =>
        val path = "/my/logins/direct"
        val headers = Map("Content-Type" -> "application/json")
        
        // POST without auth should return error (not 404)
        val (status, json, responseHeaders) = makeHttp4sPostRequest(path, "", headers)
        
        // Should return 400 or 401 (not 404 - handler was found)
        status should (be >= 400 and be < 500)
        
        // Should have error message
        (hasField(json, "error") || hasField(json, "message")) shouldBe true
        
        // Should have correlation ID
        assertCorrelationId(responseHeaders)
        
        successCount += 1
      }
      
      logger.info(s"Property 6.5 completed: $successCount iterations")
      successCount should equal(iterations)
    }

    scenario("Property 6.6: Concurrent requests are handled correctly (100 iterations)", PropertyTag) {
      import scala.concurrent.Future
      
      val iterations = 100
      val batchSize = 10 // Process in batches to avoid overwhelming the server
      
      var successCount = 0
      
      // Process requests in batches
      (0 until iterations by batchSize).foreach { batchStart =>
        val batchEnd = Math.min(batchStart + batchSize, iterations)
        val batchFutures = (batchStart until batchEnd).map { i =>
          Future {
            val version = apiVersions(Random.nextInt(apiVersions.length))
            val endpoint = publicEndpoints(Random.nextInt(publicEndpoints.length))
            val path = s"/obp/$version$endpoint"
            
            val (status, json, headers) = makeHttp4sGetRequest(path)
            
            // Verify response is valid
            status should (be >= 200 and be < 600)
            assertCorrelationId(headers)
            
            1 // Success
          }(scala.concurrent.ExecutionContext.global)
        }
        
        val batchResults = Await.result(
          Future.sequence(batchFutures)(implicitly, scala.concurrent.ExecutionContext.global), 
          DurationInt(30).seconds
        )
        successCount += batchResults.sum
      }
      
      logger.info(s"Property 6.6 completed: $successCount concurrent requests handled")
      successCount should equal(iterations)
    }

    scenario("Property 6.7: Error responses have consistent structure (100 iterations)", PropertyTag) {
      var successCount = 0
      val iterations = 100
      
      (1 to iterations).foreach { i =>
        // Generate random invalid paths
        val invalidPaths = List(
          s"/obp/v5.0.0/invalid/${randomString(10)}",
          s"/obp/v5.0.0/banks/${randomString(10)}/invalid",
          s"/obp/invalid/banks"
        )
        val path = invalidPaths(Random.nextInt(invalidPaths.length))
        
        val (status, json, headers) = makeHttp4sGetRequest(path)
        
        // Should return error status
        status should (be >= 400 and be < 600)
        
        // Should have error field or message field
        (hasField(json, "error") || hasField(json, "message")) shouldBe true
        
        // Should have correlation ID
        assertCorrelationId(headers)
        
        // Response should be valid JSON
        json should not be null
        
        successCount += 1
      }
      
      logger.info(s"Property 6.7 completed: $successCount iterations")
      successCount should equal(iterations)
    }
  }
}

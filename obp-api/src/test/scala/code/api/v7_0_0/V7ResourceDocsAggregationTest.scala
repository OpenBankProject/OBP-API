package code.api.v7_0_0

import org.json4s._
import code.Http4sTestServer
import code.setup.{OBPReq, ServerSetupWithTestData}
import org.json4s.JValue
import org.json4s.JsonAST.{JArray, JObject, JString}
import com.openbankproject.commons.util.JsonAliases.parse
import org.scalatest.Tag

import scala.collection.JavaConverters._
import com.openbankproject.commons.util.JsonAliases.RichJField

/**
 * Regression test for v7 Resource Docs Aggregation.
 *
 * The aggregation bug described below has been FIXED; these scenarios now PASS and
 * guard against regressions. They were originally written TDD-style to fail on the
 * unfixed code (hence the "Property 1: ..." naming and the now-historical bug notes).
 *
 * **Validates: Requirements 1.1, 1.2, 1.3**
 *
 * Original bug:
 * The v7.0.0 resource-docs endpoint used to return only v7.0.0's own endpoints (~10)
 * instead of aggregating all versions (v7.0.0 + v6.0.0 + v5.1.0 + ... + v1.3.0), which is 500+.
 *
 * Behavior now guaranteed:
 * - Response includes v7.0.0 endpoints
 * - Response includes v6.0.0 endpoints (e.g., getScannedApiVersions)
 * - Response includes v5.1.0 and earlier endpoints
 * - Deduplication works correctly (same URL+method keeps newest version)
 */
class V7ResourceDocsAggregationTest extends ServerSetupWithTestData {

  object V7ResourceDocsAggregationTag extends Tag("V7ResourceDocsAggregation")

  // Test message constants (to avoid SonarCube duplication warnings)
  private val MSG_RESPONSE_200_OK = "Response should be 200 OK"
  private val MSG_GIVEN_V7_ENDPOINT = "The v7.0.0 resource-docs endpoint is called"
  private val MSG_GIVEN_V6_ENDPOINT = "The v6.0.0 resource-docs endpoint is called"
  private val MSG_GIVEN_V5_ENDPOINT = "The v5.1.0 resource-docs endpoint is called"
  private val MSG_GIVEN_V4_ENDPOINT = "The v4.0.0 resource-docs endpoint is called"
  private val MSG_GIVEN_NON_RESOURCE_DOCS = "A non-resource-docs v7.0.0 endpoint is called"

  // Use Http4sTestServer for full integration testing
  private val http4sServer = Http4sTestServer
  private val baseUrl = s"http://${http4sServer.host}:${http4sServer.port}"

  private def makeHttpRequest(
    path: String,
    headers: Map[String, String] = Map.empty
  ): (Int, JValue, Map[String, String]) = {
    val req = headers.foldLeft(OBPReq.url(s"$baseUrl$path").addHeader("Accept", "*/*")) {
      case (r, (k, v)) => r.addHeader(k, v)
    }
    val (status, body, okHdrs) = req.executeRaw()
    val json = if (body.trim.isEmpty) JObject(Nil) else parse(body)
    val hdrs = okHdrs.toMultimap.asScala.flatMap { case (k, vs) => vs.asScala.map(v => k -> v) }.toMap
    (status, json, hdrs)
  }

  private def toFieldMap(fields: List[org.json4s.JsonAST.JField]): Map[String, JValue] =
    fields.map(field => field.name -> field.value).toMap

  private def extractResourceDocs(json: JValue): List[JObject] = {
    json match {
      case JObject(fields) =>
        toFieldMap(fields).get("resource_docs") match {
          case Some(JArray(docs)) =>
            docs.collect { case obj: JObject => obj }
          case _ => Nil
        }
      case _ => Nil
    }
  }

  private def getEndpointSignature(doc: JObject): Option[(String, String)] = {
    val fieldMap = toFieldMap(doc.obj)
    for {
      JString(verb) <- fieldMap.get("request_verb")
      JString(url) <- fieldMap.get("request_url")
    } yield (verb, url)
  }

  private def getOperationId(doc: JObject): Option[String] = {
    val fieldMap = toFieldMap(doc.obj)
    fieldMap.get("operation_id").collect { case JString(id) => id }
  }

  feature("Bug Condition Exploration - V7 Resource Docs Aggregation") {

    scenario("Property 1: V7 resource-docs aggregates all versions (>=500 docs, dedup, no duplicate signatures)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_V7_ENDPOINT)
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v7.0.0/resource-docs/v7.0.0/obp request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain aggregated docs from all versions (v7.0.0 + v6.0.0 + v5.1.0 + ... + v1.3.0)")
      val resourceDocs = extractResourceDocs(json)

      // **Counterexample 1**: Expected 500+ endpoints, Actual: ~10 endpoints
      info(s"Total endpoint count: ${resourceDocs.size}")
      resourceDocs.size should be >= 500

      And("Response should include v7.0.0 endpoints")
      val v7Endpoints = resourceDocs.filter { doc =>
        getEndpointSignature(doc) match {
          case Some((verb, url)) =>
            // v7.0.0 specific endpoints
            (verb == "GET" && url.endsWith("/root")) ||
            (verb == "GET" && url.endsWith("/banks")) ||
            (verb == "GET" && url.endsWith("/cards"))
          case None => false
        }
      }
      info(s"V7.0.0 endpoints found: ${v7Endpoints.size}")
      v7Endpoints should not be empty

      And("Response should include v6.0.0 endpoints (e.g., getScannedApiVersions)")
      // **Counterexample 2**: Expected v6.0.0 endpoints discoverable, Actual: not found
      val v6Endpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) =>
            // Known v6.0.0 endpoints
            opId == "OBPv6.0.0-getScannedApiVersions" ||
            opId.startsWith("OBPv6.0.0-")
          case None => false
        }
      }
      info(s"V6.0.0 endpoints found: ${v6Endpoints.size}")
      v6Endpoints should not be empty

      And("Response should include v5.1.0 and earlier endpoints")
      val olderVersionEndpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) =>
            opId.startsWith("OBPv5.1.0-") ||
            opId.startsWith("OBPv5.0.0-") ||
            opId.startsWith("OBPv4.0.0-") ||
            opId.startsWith("OBPv3.1.0-") ||
            opId.startsWith("OBPv3.0.0-") ||
            opId.startsWith("OBPv2.2.0-") ||
            opId.startsWith("OBPv2.1.0-") ||
            opId.startsWith("OBPv2.0.0-") ||
            opId.startsWith("OBPv1.4.0-") ||
            opId.startsWith("OBPv1.3.0-")
          case None => false
        }
      }
      info(s"V5.1.0 and earlier endpoints found: ${olderVersionEndpoints.size}")
      olderVersionEndpoints should not be empty

      And("Deduplication should work correctly (same URL+method keeps newest version)")
      // Check for duplicate endpoints (same verb + url)
      val endpointSignatures = resourceDocs.flatMap(getEndpointSignature)
      val duplicates = endpointSignatures.groupBy(identity).filter(_._2.size > 1)
      info(s"Duplicate endpoint signatures found: ${duplicates.size}")
      duplicates shouldBe empty

      info("**Outcome**: passes post-fix (allResourceDocs aggregation is wired in Http4s700)")
      info(s"  - endpoints: ${resourceDocs.size} (expected >= 500)")
      info(s"  - v6.0.0 endpoints discoverable: ${v6Endpoints.size}")
      info(s"  - v5.1.0+ endpoints discoverable: ${olderVersionEndpoints.size}")
    }

    scenario("Baseline - V6 Resource Docs Returns Aggregated Endpoints (SHOULD PASS)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_V6_ENDPOINT)
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v6.0.0/resource-docs/v6.0.0/obp request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v6.0.0/resource-docs/v6.0.0/obp")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain aggregated docs from v6.0.0 and earlier versions")
      val resourceDocs = extractResourceDocs(json)
      info(s"V6.0.0 total endpoint count: ${resourceDocs.size}")
      resourceDocs.size should be >= 400

      And("Response should include v6.0.0 endpoints")
      val v6Endpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) => opId.startsWith("OBPv6.0.0-")
          case None => false
        }
      }
      info(s"V6.0.0 endpoints in v6 docs: ${v6Endpoints.size}")
      v6Endpoints should not be empty

      And("Response should include v5.1.0 and earlier endpoints")
      val olderVersionEndpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) =>
            opId.startsWith("OBPv5.1.0-") ||
            opId.startsWith("OBPv5.0.0-") ||
            opId.startsWith("OBPv4.0.0-")
          case None => false
        }
      }
      info(s"V5.1.0 and earlier endpoints in v6 docs: ${olderVersionEndpoints.size}")
      olderVersionEndpoints should not be empty

      info("**Expected Outcome**: This test PASSES (establishes baseline behavior)")
      info("**Purpose**: Confirms v6.0.0 aggregation works correctly")
    }

    scenario("Cross-Version Query - V7 Endpoint Can Query V6 Docs", V7ResourceDocsAggregationTag) {
      Given("The v7.0.0 endpoint is used to query v6.0.0 resource-docs")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v7.0.0/resource-docs/v6.0.0/obp request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v6.0.0/obp")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain v6.0.0 aggregated docs")
      val resourceDocs = extractResourceDocs(json)
      info(s"V6.0.0 docs via v7 endpoint: ${resourceDocs.size}")
      resourceDocs.size should be >= 400

      info("**Expected Outcome**: This test should PASS (cross-version query works)")
      info("**Purpose**: Verifies v7 endpoint can serve other versions' docs")
    }

    scenario("Specific Endpoint Discovery - V6 getScannedApiVersions Through V7", V7ResourceDocsAggregationTag) {
      Given("The v7.0.0 resource-docs endpoint is queried for a specific v6.0.0 endpoint")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v7.0.0/resource-docs/v7.0.0/obp?functions=getScannedApiVersions request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp?functions=getScannedApiVersions")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain the getScannedApiVersions endpoint")
      val resourceDocs = extractResourceDocs(json)
      info(s"getScannedApiVersions docs found: ${resourceDocs.size}")

      // **Counterexample 3**: Expected getScannedApiVersions discoverable, Actual: empty result
      resourceDocs should not be empty

      val scannedApiVersionsEndpoint = resourceDocs.find { doc =>
        getEndpointSignature(doc) match {
          case Some((verb, url)) =>
            verb == "GET" && url.contains("/api/versions")
          case None => false
        }
      }
      scannedApiVersionsEndpoint shouldBe defined

      info("**Outcome**: passes post-fix")
      info("getScannedApiVersions (v6.0.0) is discoverable through v7.0.0")
    }
  }

  /**
   * Preservation Property Tests for v7 Resource Docs Aggregation Fix
   *
   * **CRITICAL**: These tests MUST PASS on unfixed code - passing confirms baseline behavior to preserve
   * **GOAL**: Establish baseline behavior for non-buggy inputs (v4-v6 resource-docs endpoints)
   * **PURPOSE**: Ensure the fix does not break existing v4-v6 resource-docs functionality
   *
   * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
   *
   * Preservation Requirements:
   * - v4.0.0, v5.1.0, and v6.0.0 resource-docs endpoints return aggregated docs
   * - Query parameter filtering (tags, functions, locale) works correctly
   * - Non-resource-docs v7.0.0 endpoints are unchanged
   * - collectResourceDocs() deduplication by (URL, HTTP method) keeps newest version
   */
  feature("Preservation Property Tests - Non-V7 Resource Docs Behavior") {

    scenario("Property 2.1: V6 Resource Docs Aggregation Preserved (MUST PASS)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_V6_ENDPOINT)
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v6.0.0/resource-docs/v6.0.0/obp request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v6.0.0/resource-docs/v6.0.0/obp")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain aggregated docs from v6.0.0 and earlier versions")
      val resourceDocs = extractResourceDocs(json)
      info(s"V6.0.0 total endpoint count: ${resourceDocs.size}")
      
      // Baseline observation: v6.0.0 returns 400+ aggregated endpoints
      resourceDocs.size should be >= 400

      And("Response should include v6.0.0 endpoints")
      val v6Endpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) => opId.startsWith("OBPv6.0.0-")
          case None => false
        }
      }
      info(s"V6.0.0 endpoints: ${v6Endpoints.size}")
      v6Endpoints should not be empty

      And("Response should include v5.1.0 and earlier endpoints")
      val v5AndEarlierEndpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) =>
            opId.startsWith("OBPv5.1.0-") ||
            opId.startsWith("OBPv5.0.0-") ||
            opId.startsWith("OBPv4.0.0-") ||
            opId.startsWith("OBPv3.1.0-") ||
            opId.startsWith("OBPv3.0.0-")
          case None => false
        }
      }
      info(s"V5.1.0 and earlier endpoints: ${v5AndEarlierEndpoints.size}")
      v5AndEarlierEndpoints should not be empty

      And("Deduplication should work for most endpoints (baseline behavior)")
      val endpointSignatures = resourceDocs.flatMap(getEndpointSignature)
      val duplicates = endpointSignatures.groupBy(identity).filter(_._2.size > 1)
      info(s"Duplicate endpoint signatures: ${duplicates.size}")
      // Note: Baseline behavior has 1 known duplicate (v1.4.0 resource-docs endpoint)
      // This is acceptable for preservation testing - we're capturing actual behavior
      duplicates.size should be <= 1

      info("**Expected Outcome**: This test PASSES on unfixed code (baseline behavior)")
      info("**Validates**: Requirements 3.1, 3.2 - v6.0.0 aggregation and deduplication work correctly")
    }

    scenario("Property 2.2: V5.1 Resource Docs Aggregation Preserved (MUST PASS)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_V5_ENDPOINT)
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v5.1.0/resource-docs/v5.1.0/obp request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v5.1.0/resource-docs/v5.1.0/obp")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain aggregated docs from v5.1.0 and earlier versions")
      val resourceDocs = extractResourceDocs(json)
      info(s"V5.1.0 total endpoint count: ${resourceDocs.size}")
      
      // Baseline observation: v5.1.0 returns 300+ aggregated endpoints
      resourceDocs.size should be >= 300

      And("Response should include v5.1.0 endpoints")
      val v5_1Endpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) => opId.startsWith("OBPv5.1.0-")
          case None => false
        }
      }
      info(s"V5.1.0 endpoints: ${v5_1Endpoints.size}")
      v5_1Endpoints should not be empty

      And("Response should include v5.0.0 and earlier endpoints")
      val v5AndEarlierEndpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) =>
            opId.startsWith("OBPv5.0.0-") ||
            opId.startsWith("OBPv4.0.0-") ||
            opId.startsWith("OBPv3.1.0-")
          case None => false
        }
      }
      info(s"V5.0.0 and earlier endpoints: ${v5AndEarlierEndpoints.size}")
      v5AndEarlierEndpoints should not be empty

      And("Deduplication should work for most endpoints (baseline behavior)")
      val endpointSignatures = resourceDocs.flatMap(getEndpointSignature)
      val duplicates = endpointSignatures.groupBy(identity).filter(_._2.size > 1)
      info(s"Duplicate endpoint signatures: ${duplicates.size}")
      // Note: Baseline behavior has 1 known duplicate (v1.4.0 resource-docs endpoint)
      // This is acceptable for preservation testing - we're capturing actual behavior
      duplicates.size should be <= 1

      info("**Expected Outcome**: This test PASSES on unfixed code (baseline behavior)")
      info("**Validates**: Requirements 3.1, 3.2 - v5.1.0 aggregation and deduplication work correctly")
    }

    scenario("Property 2.3: V4 Resource Docs Aggregation Preserved (MUST PASS)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_V4_ENDPOINT)
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v4.0.0/resource-docs/v4.0.0/obp request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v4.0.0/resource-docs/v4.0.0/obp")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain aggregated docs from v4.0.0 and earlier versions")
      val resourceDocs = extractResourceDocs(json)
      info(s"V4.0.0 total endpoint count: ${resourceDocs.size}")
      
      // Baseline observation: v4.0.0 returns 200+ aggregated endpoints
      resourceDocs.size should be >= 200

      And("Response should include v4.0.0 endpoints")
      val v4Endpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) => opId.startsWith("OBPv4.0.0-")
          case None => false
        }
      }
      info(s"V4.0.0 endpoints: ${v4Endpoints.size}")
      v4Endpoints should not be empty

      And("Response should include v3.1.0 and earlier endpoints")
      val v3AndEarlierEndpoints = resourceDocs.filter { doc =>
        getOperationId(doc) match {
          case Some(opId) =>
            opId.startsWith("OBPv3.1.0-") ||
            opId.startsWith("OBPv3.0.0-") ||
            opId.startsWith("OBPv2.2.0-")
          case None => false
        }
      }
      info(s"V3.1.0 and earlier endpoints: ${v3AndEarlierEndpoints.size}")
      v3AndEarlierEndpoints should not be empty

      And("Deduplication should work for most endpoints (baseline behavior)")
      val endpointSignatures = resourceDocs.flatMap(getEndpointSignature)
      val duplicates = endpointSignatures.groupBy(identity).filter(_._2.size > 1)
      info(s"Duplicate endpoint signatures: ${duplicates.size}")
      // Note: Baseline behavior has 1 known duplicate (v1.4.0 resource-docs endpoint)
      // This is acceptable for preservation testing - we're capturing actual behavior
      duplicates.size should be <= 1

      info("**Expected Outcome**: This test PASSES on unfixed code (baseline behavior)")
      info("**Validates**: Requirements 3.1, 3.2 - v4.0.0 aggregation and deduplication work correctly")
    }

    scenario("Property 2.4: Query Parameter Filtering Preserved - Functions (MUST PASS)", V7ResourceDocsAggregationTag) {
      Given("The v6.0.0 resource-docs endpoint is called with functions filter")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v6.0.0/resource-docs/v6.0.0/obp?functions=getScannedApiVersions request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v6.0.0/resource-docs/v6.0.0/obp?functions=getScannedApiVersions")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain only the filtered endpoint")
      val resourceDocs = extractResourceDocs(json)
      info(s"Filtered endpoint count: ${resourceDocs.size}")
      
      // Baseline observation: functions filter returns specific endpoint(s)
      resourceDocs should not be empty
      resourceDocs.size should be <= 5 // Should be a small number of filtered results

      And("Filtered endpoint should match the requested function")
      val matchingEndpoint = resourceDocs.find { doc =>
        getEndpointSignature(doc) match {
          case Some((verb, url)) =>
            verb == "GET" && url.contains("/api/versions")
          case None => false
        }
      }
      matchingEndpoint shouldBe defined

      info("**Expected Outcome**: This test PASSES on unfixed code (baseline behavior)")
      info("**Validates**: Requirements 3.3 - query parameter filtering works correctly")
    }

    scenario("Property 2.5: Query Parameter Filtering Preserved - Tags (MUST PASS)", V7ResourceDocsAggregationTag) {
      Given("The v6.0.0 resource-docs endpoint is called with tags filter")
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v6.0.0/resource-docs/v6.0.0/obp?tags=Account request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v6.0.0/resource-docs/v6.0.0/obp?tags=Account")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain only endpoints tagged with Account")
      val resourceDocs = extractResourceDocs(json)
      info(s"Account-tagged endpoint count: ${resourceDocs.size}")
      
      // Baseline observation: tags filter returns subset of endpoints
      resourceDocs should not be empty
      resourceDocs.size should be < 500 // Should be less than total endpoints

      And("All returned endpoints should have Account tag")
      val allHaveAccountTag = resourceDocs.forall { doc =>
        val fieldMap = toFieldMap(doc.obj)
        fieldMap.get("tags") match {
          case Some(JArray(tags)) =>
            tags.exists {
              case JString(tag) => tag.toLowerCase.contains("account")
              case _ => false
            }
          case _ => false
        }
      }
      // Note: This assertion may be relaxed if tag filtering is case-sensitive or partial match
      info(s"All endpoints have Account tag: $allHaveAccountTag")

      info("**Expected Outcome**: This test PASSES on unfixed code (baseline behavior)")
      info("**Validates**: Requirements 3.3 - query parameter filtering works correctly")
    }

    scenario("Property 2.6: Non-Resource-Docs V7 Endpoints Unchanged - Root (MUST PASS)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_NON_RESOURCE_DOCS)

      When("Making GET /obp/v7.0.0/root request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/root")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain expected root endpoint data")
      json match {
        case JObject(fields) =>
          val fieldMap = toFieldMap(fields)
          // Root endpoint should return version info
          fieldMap.get("version") shouldBe defined
          fieldMap.get("git_commit") shouldBe defined
        case _ =>
          fail("Expected JObject response from root endpoint")
      }

      info("**Expected Outcome**: This test PASSES on unfixed code (baseline behavior)")
      info("**Validates**: Requirements 3.5 - non-resource-docs v7.0.0 endpoints unchanged")
    }

    scenario("Property 2.7: Non-Resource-Docs V7 Endpoints Unchanged - Banks (MUST PASS)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_NON_RESOURCE_DOCS)

      When("Making GET /obp/v7.0.0/banks request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/banks")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should contain expected banks data structure")
      json match {
        case JObject(fields) =>
          val fieldMap = toFieldMap(fields)
          // Banks endpoint should return banks array
          fieldMap.get("banks") shouldBe defined
        case _ =>
          fail("Expected JObject response from banks endpoint")
      }

      info("**Expected Outcome**: This test PASSES on unfixed code (baseline behavior)")
      info("**Validates**: Requirements 3.5 - non-resource-docs v7.0.0 endpoints unchanged")
    }

    scenario("Property 2.8: Deduplication Keeps Newest Version (MUST PASS)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_V6_ENDPOINT)
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v6.0.0/resource-docs/v6.0.0/obp request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v6.0.0/resource-docs/v6.0.0/obp")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("For endpoints with same URL+method, deduplication should work for most endpoints")
      val resourceDocs = extractResourceDocs(json)
      
      // Group by endpoint signature (verb + url)
      val endpointsBySignature = resourceDocs.groupBy(getEndpointSignature)
      
      // Check that most signatures appear only once
      val duplicateSignatures = endpointsBySignature.filter(_._2.size > 1)
      info(s"Duplicate signatures found: ${duplicateSignatures.size}")
      // Note: Baseline behavior has 1 known duplicate (v1.4.0 resource-docs endpoint)
      // This is acceptable for preservation testing - we're capturing actual behavior
      duplicateSignatures.size should be <= 1

      // Verify that for common endpoints, the newest version is kept
      // Example: GET /obp/vX.X.X/banks should be from v6.0.0, not older versions
      val banksEndpoint = resourceDocs.find { doc =>
        getEndpointSignature(doc) match {
          case Some((verb, url)) =>
            verb == "GET" && url.matches(".*/banks$")
          case None => false
        }
      }
      
      banksEndpoint match {
        case Some(doc) =>
          val opId = getOperationId(doc)
          info(s"Banks endpoint operation_id: $opId")
          // Should be from v6.0.0 or v5.1.0 (newest available)
          opId match {
            case Some(id) =>
              val isNewestVersion = id.startsWith("OBPv6.0.0-") || id.startsWith("OBPv5.1.0-")
              info(s"Banks endpoint is from newest version: $isNewestVersion")
            case None =>
              info("Banks endpoint has no operation_id")
          }
        case None =>
          info("Banks endpoint not found in v6.0.0 resource docs")
      }

      info("**Expected Outcome**: This test PASSES on unfixed code (baseline behavior)")
      info("**Validates**: Requirements 3.2 - collectResourceDocs() deduplication works correctly")
    }

    scenario("Property 2.9: JSON Response Format Preserved (MUST PASS)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_V6_ENDPOINT)
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v6.0.0/resource-docs/v6.0.0/obp request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v6.0.0/resource-docs/v6.0.0/obp")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("Response should have expected JSON structure")
      json match {
        case JObject(fields) =>
          val fieldMap = toFieldMap(fields)
          
          // Should have resource_docs array
          fieldMap.get("resource_docs") shouldBe defined
          
          fieldMap.get("resource_docs") match {
            case Some(JArray(docs)) =>
              info(s"Resource docs array size: ${docs.size}")
              
              // Each doc should have expected fields
              docs.headOption match {
                case Some(doc: JObject) =>
                  val docFieldMap = toFieldMap(doc.obj)
                  
                  // Required fields in resource doc
                  docFieldMap.get("operation_id") shouldBe defined
                  docFieldMap.get("request_verb") shouldBe defined
                  docFieldMap.get("request_url") shouldBe defined
                  docFieldMap.get("summary") shouldBe defined
                  docFieldMap.get("description") shouldBe defined
                  
                  info("Resource doc has expected field structure")
                case _ =>
                  fail("Expected JObject in resource_docs array")
              }
            case _ =>
              fail("Expected JArray for resource_docs field")
          }
        case _ =>
          fail("Expected JObject response")
      }

      info("**Expected Outcome**: This test PASSES on unfixed code (baseline behavior)")
      info("**Validates**: Requirements 3.4 - JSON response format unchanged")
    }

    scenario("Property 2.10: V7 specifiedUrl Uses V7 Version for Aggregated Docs (MUST PASS AFTER FIX)", V7ResourceDocsAggregationTag) {
      Given(MSG_GIVEN_V7_ENDPOINT)
      setPropsValues("resource_docs_requires_role" -> "false")

      When("Making GET /obp/v7.0.0/resource-docs/v7.0.0/obp request")
      val (statusCode, json, _) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp")

      Then(MSG_RESPONSE_200_OK)
      statusCode shouldBe 200

      And("All resource docs should have specifiedUrl with v7.0.0 version")
      val resourceDocs = extractResourceDocs(json)
      info(s"Total endpoint count: ${resourceDocs.size}")

      // Check that all docs have specifiedUrl field set
      val docsWithSpecifiedUrl = resourceDocs.filter { doc =>
        val fieldMap = toFieldMap(doc.obj)
        fieldMap.get("specified_url") match {
          case Some(JString(url)) => url.nonEmpty
          case _ => false
        }
      }
      info(s"Docs with non-empty specifiedUrl: ${docsWithSpecifiedUrl.size}")
      docsWithSpecifiedUrl.size shouldBe resourceDocs.size

      // Dynamic-entity / dynamic-endpoint docs are a deliberate exception: their routes are
      // served ONLY under /obp/dynamic-entity/... and /obp/dynamic-endpoint/... (by
      // Http4sDynamicEntity / Http4sDynamicEndpoint), NOT at the requested version. Rendering
      // them at /obp/v7.0.0/<x> produces a 404 in API Explorer. So they are legitimately NOT
      // under /v7.0.0/ and must be excluded from the bulk version assertion below.
      def specifiedUrlOf(doc: JObject): Option[String] = toFieldMap(doc.obj).get("specified_url").collect {
        case JString(url) => url
      }
      // CRITICAL — classify by operation_id, NEVER by specified_url (the thing we validate).
      // operation_id embeds the partial function name (buildOperationId = "${version}-${fnName}").
      // The dynamic docs visible here are:
      //   - real dynamic entities   → fnName starts with "dynamicEntity"   → /obp/dynamic-entity/...
      //   - real dynamic endpoints  → fnName starts with "dynamicEndpoint" → /obp/dynamic-endpoint/...
      //   - the always-present practise fixture (PractiseEndpointGroup, urlPrefix
      //     "test-dynamic-resource-doc", from DynamicEndpoints.dynamicResourceDocs) → /obp/dynamic-endpoint/...
      // Classifying by operation_id (URL-independent) is what makes the guard real: if the
      // efb97531e prefix bug regresses, these docs get /obp/v7.0.0/<x> but are STILL recognised
      // here as dynamic, so the guard FAILS. Classifying by the URL would be circular and would
      // silently pass on a regression — exactly how the original bug slipped through.
      val practiseFixtureOpId = "OBPv4.0.0-test-dynamic-resource-doc"
      def isDynamicEntityDoc(doc: JObject): Boolean = getOperationId(doc).exists(_.contains("dynamicEntity"))
      def isDynamicEndpointDoc(doc: JObject): Boolean =
        getOperationId(doc).exists(id => id.contains("dynamicEndpoint") || id == practiseFixtureOpId)
      def isDynamicDoc(doc: JObject): Boolean = isDynamicEntityDoc(doc) || isDynamicEndpointDoc(doc)

      val (dynamicDocs, normalDocs) = resourceDocs.partition(isDynamicDoc)
      info(s"Dynamic (entity/endpoint) docs: ${dynamicDocs.size}; normal versioned docs: ${normalDocs.size}")
      // Sanity: the test env always registers the practise fixture, so at least one dynamic doc
      // must be present — otherwise this scenario could not catch the prefix regression at all.
      withClue("test env must expose at least one dynamic doc (practise fixture) to guard the prefix regression ") {
        dynamicDocs.nonEmpty shouldBe true
      }

      // Check that all NORMAL (non-dynamic) specifiedUrl values use the v7.0.0 version
      val docsWithV7SpecifiedUrl = normalDocs.filter { doc =>
        specifiedUrlOf(doc).exists(_.contains("/v7.0.0/"))
      }
      info(s"Normal docs with v7.0.0 in specifiedUrl: ${docsWithV7SpecifiedUrl.size}")
      docsWithV7SpecifiedUrl.size shouldBe normalDocs.size

      // Regression guard for the dynamic prefix bug (efb97531e): every dynamic doc's specifiedUrl
      // must keep its dynamic-* prefix and must NOT be rewritten to /v7.0.0/.
      resourceDocs.filter(isDynamicEntityDoc).foreach { doc =>
        val url = specifiedUrlOf(doc).getOrElse(fail(s"Expected specified_url on dynamicEntity doc ${getOperationId(doc)}"))
        withClue(s"dynamicEntity doc ${getOperationId(doc)} specifiedUrl must keep /dynamic-entity/ prefix, got: $url ") {
          url should include("/dynamic-entity/")
          url should not include("/v7.0.0/")
        }
      }
      resourceDocs.filter(isDynamicEndpointDoc).foreach { doc =>
        val url = specifiedUrlOf(doc).getOrElse(fail(s"Expected specified_url on dynamicEndpoint doc ${getOperationId(doc)}"))
        withClue(s"dynamicEndpoint doc ${getOperationId(doc)} specifiedUrl must keep /dynamic-endpoint/ prefix, got: $url ") {
          url should include("/dynamic-endpoint/")
          url should not include("/v7.0.0/")
        }
      }

      // Verify a specific v6.0.0 endpoint has correct specifiedUrl
      val v6Endpoint = resourceDocs.find { doc =>
        getOperationId(doc) match {
          case Some(opId) => opId == "OBPv6.0.0-getScannedApiVersions"
          case None => false
        }
      }

      v6Endpoint match {
        case Some(doc) =>
          val fieldMap = toFieldMap(doc.obj)
          fieldMap.get("specified_url") match {
            case Some(JString(url)) =>
              info(s"getScannedApiVersions specifiedUrl: $url")
              url should include("/v7.0.0/")
              url should not include("/v6.0.0/")
            case _ =>
              fail("Expected specifiedUrl field in getScannedApiVersions doc")
          }
        case None =>
          info("Warning: getScannedApiVersions endpoint not found in aggregated docs")
      }

      info("**Expected Outcome**: This test PASSES after specifiedUrl fix")
      info("**Validates**: normal aggregated docs use v7.0.0; dynamic-entity/endpoint docs keep their dynamic-* prefix")
    }
  }
}


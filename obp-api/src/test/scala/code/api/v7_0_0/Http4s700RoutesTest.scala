package code.api.v7_0_0

import org.scalatest.Ignore
import code.Http4sTestServer
import code.api.util.ApiRole.{canGetCardsForBank, canReadResourceDoc}
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles}
import code.setup.ServerSetupWithTestData
import dispatch.Defaults._
import dispatch._
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.json.JsonParser.parse
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * HTTP4S v7.0.0 Routes Integration Test
 * 
 * Uses Http4sTestServer (singleton) to test v7.0.0 endpoints through real HTTP requests.
 * This ensures we test the complete server stack including middleware, error handling, etc.
 */
@Ignore
class Http4s700RoutesTest extends ServerSetupWithTestData {
  
  object Http4s700RoutesTag extends Tag("Http4s700Routes")

  // Use Http4sTestServer for full integration testing
  private val http4sServer = Http4sTestServer
  private val baseUrl = s"http://${http4sServer.host}:${http4sServer.port}"

  private def makeHttpRequest(path: String, headers: Map[String, String] = Map.empty): (Int, JValue) = {
    val request = url(s"$baseUrl$path")
    val requestWithHeaders = headers.foldLeft(request) { case (req, (key, value)) =>
      req.addHeader(key, value)
    }
    
    try {
      val response = Http.default(requestWithHeaders.setHeader("Accept", "*/*") > as.Response(p => (p.getStatusCode, p.getResponseBody)))
      val (statusCode, body) = Await.result(response, 10.seconds)
      val json = if (body.trim.isEmpty) JObject(Nil) else parse(body)
      (statusCode, json)
    } catch {
      case e: java.util.concurrent.ExecutionException =>
        val statusPattern = """(\d{3})""".r
        statusPattern.findFirstIn(e.getCause.getMessage) match {
          case Some(code) => (code.toInt, JObject(Nil))
          case None => throw e
        }
      case e: Exception =>
        throw e
    }
  }

  private def toFieldMap(fields: List[JField]): Map[String, JValue] = {
    fields.map(field => field.name -> field.value).toMap
  }

  feature("Http4s700 root endpoint") {

    scenario("Return API info JSON", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root request")
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/root")

      Then("Response is 200 OK with API info fields")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val keys = fields.map(_.name)
          keys should contain("version")
          keys should contain("version_status")
          keys should contain("git_commit")
          keys should contain("connector")
        case _ =>
          fail("Expected JSON object for root endpoint")
      }
    }
  }

  feature("Http4s700 banks endpoint") {

    scenario("Return banks list JSON", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks request")
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/banks")

      Then("Response is 200 OK with banks array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          val valueOpt = toFieldMap(fields).get("banks")
          valueOpt should not be empty
          valueOpt.get match {
            case JArray(_) =>
              succeed
            case _ =>
              fail("Expected banks field to be an array")
          }
        case _ =>
          fail("Expected JSON object for banks endpoint")
      }
    }
  }

  feature("Http4s700 cards endpoint") {

    scenario("Reject unauthenticated access to cards", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/cards request without auth headers")
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/cards")

      Then("Response is 401 Unauthorized with appropriate error message")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(AuthenticatedUserIsRequired)
            case _ =>
              fail("Expected message field as JSON string for cards unauthorized response")
          }
        case _ =>
          fail("Expected JSON object for cards unauthorized response")
      }
    }

    scenario("Return cards list JSON when authenticated", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/cards request with DirectLogin header")
      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/cards", headers)

      Then("Response is 200 OK with cards array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("cards") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected cards field to be an array")
          }
        case _ => fail("Expected JSON object for cards endpoint")
      }
    }
  }

  feature("Http4s700 bank cards endpoint") {

    scenario("Return bank cards list JSON when authenticated and entitled", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/cards request with DirectLogin header and role")
      val bankId = testBankId1.value
      addEntitlement(bankId, resourceUser1.userId, canGetCardsForBank.toString)

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/cards?limit=10&offset=0", headers)

      Then("Response is 200 OK with cards array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("cards") match {
            case Some(JArray(_)) => succeed
            case _ => fail("Expected cards field to be an array")
          }
        case _ => fail("Expected JSON object for bank cards endpoint")
      }
    }

    scenario("Reject bank cards access when missing required role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/cards request with DirectLogin header but no role")
      val bankId = testBankId1.value
      
      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/cards", headers)

      Then("Response is 403 Forbidden")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(UserHasMissingRoles)
              message should include(canGetCardsForBank.toString)
            case _ =>
              fail("Expected message field as JSON string for missing-role response")
          }
        case _ =>
          fail("Expected JSON object for missing-role response")
      }
    }

    scenario("Return BankNotFound when bank does not exist and user is entitled", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/banks/BANK_ID/cards request for non-existing bank")
      val bankId = "non-existing-bank-id"
      addEntitlement(bankId, resourceUser1.userId, canGetCardsForBank.toString)

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest(s"/obp/v7.0.0/banks/$bankId/cards", headers)

      Then("Response is 404 Not Found with BankNotFound message")
      statusCode shouldBe 404
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(BankNotFound)
            case _ =>
              fail("Expected message field as JSON string for BankNotFound response")
          }
        case _ =>
          fail("Expected JSON object for BankNotFound response")
      }
    }
  }

  feature("Http4s700 resource-docs endpoint") {

    scenario("Allow public access when resource docs role is not required", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request without auth headers")
      setPropsValues("resource_docs_requires_role" -> "false")
      
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp")

      Then("Response is 200 OK with resource_docs array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(_)) =>
              succeed
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }

    scenario("Reject unauthenticated access when resource docs role is required", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request without auth headers and role required")
      setPropsValues("resource_docs_requires_role" -> "true")
      
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp")

      Then("Response is 401 Unauthorized")
      statusCode shouldBe 401
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(AuthenticatedUserIsRequired)
            case _ =>
              fail("Expected message field as JSON string for resource-docs unauthorized response")
          }
        case _ =>
          fail("Expected JSON object for resource-docs unauthorized response")
      }
    }

    scenario("Reject access when authenticated but missing canReadResourceDoc role", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request with auth but no canReadResourceDoc role")
      setPropsValues("resource_docs_requires_role" -> "true")
      
      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp", headers)

      Then("Response is 403 Forbidden")
      statusCode shouldBe 403
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(UserHasMissingRoles)
              message should include(canReadResourceDoc.toString)
            case _ =>
              fail("Expected message field as JSON string for missing-role response")
          }
        case _ =>
          fail("Expected JSON object for missing-role response")
      }
    }

    scenario("Return docs when authenticated and entitled with canReadResourceDoc", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request with auth and canReadResourceDoc role")
      setPropsValues("resource_docs_requires_role" -> "true")
      addEntitlement("", resourceUser1.userId, canReadResourceDoc.toString)

      When("Making HTTP request to server")
      val headers = Map("DirectLogin" -> s"token=${token1.value}")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp", headers)

      Then("Response is 200 OK with resource_docs array")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(_)) =>
              succeed
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }

    scenario("Filter docs by tags parameter", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp?tags=Card request")
      setPropsValues("resource_docs_requires_role" -> "false")
      
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp?tags=Card")

      Then("Response is 200 OK and all returned docs contain Card tag")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(resourceDocs)) =>
              resourceDocs.foreach {
                case JObject(rdFields) =>
                  toFieldMap(rdFields).get("tags") match {
                    case Some(JArray(tags)) =>
                      tags.exists {
                        case JString(tag) => tag == "Card"
                        case _ => false
                      } shouldBe true
                    case _ =>
                      fail("Expected tags field to be an array")
                  }
                case _ =>
                  fail("Expected resource doc to be a JSON object")
              }
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }

    scenario("Filter docs by functions parameter", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp?functions=getBanks request")
      setPropsValues("resource_docs_requires_role" -> "false")
      
      When("Making HTTP request to server")
      val (statusCode, json) = makeHttpRequest("/obp/v7.0.0/resource-docs/v7.0.0/obp?functions=getBanks")

      Then("Response is 200 OK and includes GET /banks")
      statusCode shouldBe 200
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(resourceDocs)) =>
              resourceDocs.foreach {
                case JObject(rdFields) =>
                  val fieldMap = toFieldMap(rdFields)
                  (fieldMap.get("request_verb"), fieldMap.get("request_url")) match {
                    case (Some(JString(verb)), Some(JString(url))) =>
                      verb shouldBe "GET"
                      url should endWith("/banks")
                    case _ =>
                      fail("Expected request_verb and request_url fields as JSON strings")
                  }
                case _ =>
                  fail("Expected resource doc to be a JSON object")
              }
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }
  }

}

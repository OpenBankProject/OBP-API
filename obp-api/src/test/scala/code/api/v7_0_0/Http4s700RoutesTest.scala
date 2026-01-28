package code.api.v7_0_0

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.util.ApiRole.{canGetCardsForBank, canReadResourceDoc}
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BankNotFound, InvalidApiVersionString, UserHasMissingRoles}
import code.setup.ServerSetupWithTestData
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.json.JsonParser.parse
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.scalatest.Tag

class Http4s700RoutesTest extends ServerSetupWithTestData {
  
  object Http4s700RoutesTag extends Tag("Http4s700Routes")

  private def runAndParseJson(request: Request[IO]): (Status, JValue) = {
    val response = Http4s700.wrappedRoutesV700Services.orNotFound.run(request).unsafeRunSync()
    val body = response.as[String].unsafeRunSync()
    val json = if (body.trim.isEmpty) JObject(Nil) else parse(body)
    (response.status, json)
  }

  private def withDirectLoginToken(request: Request[IO], token: String): Request[IO] = {
    request.withHeaders(
      Header.Raw(org.typelevel.ci.CIString("DirectLogin"), s"token=$token")
    )
  }

  private def toFieldMap(fields: List[JField]): Map[String, JValue] = {
    fields.map(field => field.name -> field.value).toMap
  }

  feature("Http4s700 root endpoint") {

    scenario("Return API info JSON", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/root request")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/root")
      )

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 200 OK with API info fields")
      status shouldBe Status.Ok
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
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/banks")
      )

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 200 OK with banks array")
      status shouldBe Status.Ok
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
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/cards")
      )

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 401 Unauthorized with appropriate error message")
      status.code shouldBe 401
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
      val baseRequest = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/cards")
      )
      val request = withDirectLoginToken(baseRequest, token1.value)

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 200 OK with cards array")
      status shouldBe Status.Ok
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

      val baseRequest = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"/obp/v7.0.0/banks/$bankId/cards?limit=10&offset=0")
      )
      val request = withDirectLoginToken(baseRequest, token1.value)

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 200 OK with cards array")
      status shouldBe Status.Ok
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
      val baseRequest = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"/obp/v7.0.0/banks/$bankId/cards")
      )
      val request = withDirectLoginToken(baseRequest, token1.value)

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 403 Forbidden")
      status.code shouldBe 403
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

      val baseRequest = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"/obp/v7.0.0/banks/$bankId/cards")
      )
      val request = withDirectLoginToken(baseRequest, token1.value)

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 404 Not Found with BankNotFound message")
      status.code shouldBe 404
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
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/resource-docs/v7.0.0/obp")
      )

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 200 OK with resource_docs array")
      status shouldBe Status.Ok
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(resourceDocs)) =>
              resourceDocs.exists {
                case JObject(rdFields) =>
                  toFieldMap(rdFields).get("implemented_by") match {
                    case Some(JObject(implFields)) =>
                      toFieldMap(implFields).get("technology") match {
                        case Some(JString(value)) => value == "http4s"
                        case _ => false
                      }
                    case _ => false
                  }
                case _ => false
              } shouldBe true
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }

    scenario("Return only http4s technology endpoints", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request")
      setPropsValues("resource_docs_requires_role" -> "false")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/resource-docs/v7.0.0/obp")
      )

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 200 OK and includes no lift endpoints")
      status shouldBe Status.Ok
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("resource_docs") match {
            case Some(JArray(resourceDocs)) =>
              resourceDocs.exists {
                case JObject(rdFields) =>
                  toFieldMap(rdFields).get("implemented_by") match {
                    case Some(JObject(implFields)) =>
                      toFieldMap(implFields).get("technology") match {
                        case Some(JString(value)) => value == "http4s"
                        case _ => false
                      }
                    case _ => false
                  }
                case _ => false
              } shouldBe true
              resourceDocs.exists {
                case JObject(rdFields) =>
                  toFieldMap(rdFields).get("implemented_by") match {
                    case Some(JObject(implFields)) =>
                      toFieldMap(implFields).get("technology") match {
                        case Some(JString(value)) => value == "lift"
                        case _ => false
                      }
                    case _ => false
                  }
                case _ => false
              } shouldBe false
            case _ =>
              fail("Expected resource_docs field to be an array")
          }
        case _ =>
          fail("Expected JSON object for resource-docs endpoint")
      }
    }

    scenario("Reject requesting non-v7 API version docs", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v6.0.0/obp request")
      setPropsValues("resource_docs_requires_role" -> "false")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/resource-docs/v6.0.0/obp")
      )

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 400 Bad Request")
      status.code shouldBe 400
      json match {
        case JObject(fields) =>
          toFieldMap(fields).get("message") match {
            case Some(JString(message)) =>
              message should include(InvalidApiVersionString)
              message should include("v6.0.0")
            case _ =>
              fail("Expected message field as JSON string for invalid-version response")
          }
        case _ =>
          fail("Expected JSON object for invalid-version response")
      }
    }

    scenario("Reject unauthenticated access when resource docs role is required", Http4s700RoutesTag) {
      Given("GET /obp/v7.0.0/resource-docs/v7.0.0/obp request without auth headers and role required")
      setPropsValues("resource_docs_requires_role" -> "true")
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/resource-docs/v7.0.0/obp")
      )

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 401 Unauthorized")
      status.code shouldBe 401
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
      val baseRequest = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/resource-docs/v7.0.0/obp")
      )
      val request = withDirectLoginToken(baseRequest, token1.value)

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 403 Forbidden")
      status.code shouldBe 403
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

      val baseRequest = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/resource-docs/v7.0.0/obp")
      )
      val request = withDirectLoginToken(baseRequest, token1.value)

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 200 OK with resource_docs array")
      status shouldBe Status.Ok
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
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/resource-docs/v7.0.0/obp?tags=Card")
      )

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 200 OK and all returned docs contain Card tag")
      status shouldBe Status.Ok
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
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/obp/v7.0.0/resource-docs/v7.0.0/obp?functions=getBanks")
      )

      When("Running through wrapped routes")
      val (status, json) = runAndParseJson(request)

      Then("Response is 200 OK and includes GET /banks")
      status shouldBe Status.Ok
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

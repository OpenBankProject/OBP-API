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

package code.api.ResourceDocs1_4_0

import code.api.v1_4_0.JSONFactory1_4_0
import code.setup.ServerSetup
import org.json4s.JsonAST.{JNothing, JValue}
import org.json4s.native.JsonMethods.parse

/**
 * Covers OpenAPI31JSONFactory.
 *
 * It serves a public endpoint - GET /obp/vX/resource-docs/API_VERSION/openapi - and its input is
 * the typed_success_response_body that ResourceDocJson carries.
 */
class OpenAPI31FactoryTest extends ServerSetup {

  private def doc(operationId: String, typedBody: JValue): JSONFactory1_4_0.ResourceDocJson =
    JSONFactory1_4_0.ResourceDocJson(
      operation_id = operationId,
      implemented_by = JSONFactory1_4_0.ImplementedByJson("4.0.0", operationId),
      request_verb = "GET",
      request_url = "/test",
      summary = "Test",
      description = "Test desc",
      description_markdown = "Test desc",
      example_request_body = null,
      success_response_body = null,
      error_response_bodies = List("OBP-10000"),
      tags = List("Test"),
      typed_request_body = JNothing,
      typed_success_response_body = typedBody,
      roles = Some(List()),
      is_featured = false,
      special_instructions = "",
      specified_url = "/obp/v4.0.0/test",
      connector_methods = List(),
      created_by_bank_id = None
    )

  private def responseSchema(typedBody: JValue): JValue = {
    val openApi = OpenAPI31JSONFactory.createOpenAPI31Json(List(doc("testOp", typedBody)), "v4.0.0", "http://localhost:8080")
    val rendered = parse(org.json4s.native.Serialization.write(openApi))
    val paths = (rendered \ "paths") match {
      case org.json4s.JObject(fields) => fields
      case other => fail(s"paths was not an object: $other")
    }
    withClue(s"expected exactly one path, got ${paths.map(_._1)}: ") { paths.size should equal(1) }
    (paths.head._2 \ "get" \ "responses" \ "200" \ "content" \ "application/json" \ "schema")
  }

  feature("OpenAPI 3.1 JSON factory schema conversion") {
    scenario("an object typed body becomes an object schema carrying its properties") {
      val schema = responseSchema(parse("""{"type":"object","properties":{"bank_id":{"type":"string"}}}"""))

      (schema \ "type") should equal(org.json4s.JString("object"))
      (schema \ "properties" \ "bank_id" \ "type") should equal(org.json4s.JString("string"))
    }

    scenario("an array typed body becomes an array schema that keeps its items") {
      val schema = responseSchema(parse(
        """{"type":"array","items":{"type":"object","properties":{"tag_name":{"type":"string"}}}}"""))

      (schema \ "type") should equal(org.json4s.JString("array"))
      (schema \ "items" \ "type") should equal(org.json4s.JString("object"))
      (schema \ "items" \ "properties" \ "tag_name" \ "type") should equal(org.json4s.JString("string"))
    }

    scenario("a nested array field keeps its items through the conversion") {
      val schema = responseSchema(parse(
        """{"type":"object","properties":{"views":{"type":"array","items":{"type":"object","properties":{"id":{"type":"string"}}}}}}"""))

      (schema \ "properties" \ "views" \ "type") should equal(org.json4s.JString("array"))
      (schema \ "properties" \ "views" \ "items" \ "properties" \ "id" \ "type") should equal(org.json4s.JString("string"))
    }

    scenario("the document declares the OpenAPI version it claims to be") {
      val openApi = OpenAPI31JSONFactory.createOpenAPI31Json(List(doc("testOp", JNothing)), "v4.0.0", "http://localhost:8080")

      openApi.openapi should equal("3.1.0")
    }

    scenario("survives a typed body that is absent") {
      noException should be thrownBy
        OpenAPI31JSONFactory.createOpenAPI31Json(List(doc("testOp", JNothing)), "v4.0.0", "http://localhost:8080")
    }
  }

  feature("OpenAPI 3.1 auth descriptions and URLs") {
    scenario("consumerRegistrationUrl falls back to hostname/consumer-registration when no portal URL is set") {
      OpenAPI31JSONFactory.consumerRegistrationUrl("https://api.example.com") should equal("https://api.example.com/consumer-registration")
    }

    scenario("consumerRegistrationUrl points to /consumers/register when portal URL is set") {
      setPropsValues("public_obp_portal_url" -> "https://portal.example.com")
      OpenAPI31JSONFactory.consumerRegistrationUrl("https://api.example.com") should equal("https://portal.example.com/consumers/register")
      OpenAPI31JSONFactory.consumerCreationGuideUrl should equal(Some("https://portal.example.com/developers/consumer-creation"))
    }

    scenario("oAuth2Description uses consumerRegistrationUrl for registration and hostname for API endpoints") {
      val desc = OpenAPI31JSONFactory.oAuth2Description("https://api.example.com")
      desc should include("Register your application at https://api.example.com/consumer-registration to obtain a client_id and client_secret.")
      desc should include("GET https://api.example.com/obp/v7.0.0/my/banks")
    }

    scenario("oAuth2Description guides users through portal guide and registration form when portal URL is set") {
      setPropsValues("public_obp_portal_url" -> "https://portal.example.com")
      val desc = OpenAPI31JSONFactory.oAuth2Description("https://api.example.com")
      desc should include("Read the registration instructions at https://portal.example.com/developers/consumer-creation, log in and proceed to https://portal.example.com/consumers/register to register your application and obtain a client_id and client_secret.")
    }

    scenario("directLoginDescription uses consumerRegistrationUrl for registration and hostname for API endpoints") {
      val desc = OpenAPI31JSONFactory.directLoginDescription("https://api.example.com")
      desc should include("Register your application at https://api.example.com/consumer-registration to obtain a consumer_key.")
      desc should include("POST https://api.example.com/obp/v6.0.0/my/logins/direct")
      desc should include("GET https://api.example.com/obp/v7.0.0/my/banks")
    }

    scenario("directLoginDescription guides users through portal guide and registration form when portal URL is set") {
      setPropsValues("public_obp_portal_url" -> "https://portal.example.com")
      val desc = OpenAPI31JSONFactory.directLoginDescription("https://api.example.com")
      desc should include("Read the registration instructions at https://portal.example.com/developers/consumer-creation, log in and proceed to https://portal.example.com/consumers/register to register your application and obtain a consumer_key.")
    }

    scenario("gatewayLoginDescription uses hostname for API endpoints") {
      val desc = OpenAPI31JSONFactory.gatewayLoginDescription("https://api.example.com")
      desc should include("GET https://api.example.com/obp/v7.0.0/my/banks")
    }

    scenario("oidcTokenEndpoint falls back to <OIDC_provider_token_endpoint> when no OIDC host is set") {
      OpenAPI31JSONFactory.oidcTokenEndpoint should equal("<OIDC_provider_token_endpoint>")
    }

    scenario("oidcTokenEndpoint resolves token endpoint from oauth2.obp_oidc.host") {
      setPropsValues("oauth2.obp_oidc.host" -> "https://oidc.example.com")
      OpenAPI31JSONFactory.oidcTokenEndpoint should equal("https://oidc.example.com/obp-oidc/token")
    }

    scenario("oidcTokenEndpoint strips trailing slashes when resolving token endpoint") {
      setPropsValues("oauth2.obp_oidc.host" -> "https://oidc.example.com/")
      OpenAPI31JSONFactory.oidcTokenEndpoint should equal("https://oidc.example.com/obp-oidc/token")
    }

    scenario("oidcDiscoveryUrl returns None when no OIDC host or well-known prop is set") {
      OpenAPI31JSONFactory.oidcDiscoveryUrl should equal(None)
    }

    scenario("oidcDiscoveryUrl resolves discovery url from oauth2.obp_oidc.host") {
      setPropsValues("oauth2.obp_oidc.host" -> "https://oidc.example.com")
      OpenAPI31JSONFactory.oidcDiscoveryUrl should equal(Some("https://oidc.example.com/obp-oidc/.well-known/openid-configuration"))
    }

    scenario("oidcDiscoveryUrl prefers explicit oauth2.obp_oidc.well_known prop when present") {
      setPropsValues("oauth2.obp_oidc.well_known" -> "https://custom.example.com/.well-known/openid-configuration")
      OpenAPI31JSONFactory.oidcDiscoveryUrl should equal(Some("https://custom.example.com/.well-known/openid-configuration"))
    }

    scenario("oAuth2Description renders dynamically resolved token endpoint and discovery info when OIDC host is set") {
      setPropsValues("oauth2.obp_oidc.host" -> "https://oidc.example.com")
      val desc = OpenAPI31JSONFactory.oAuth2Description("https://api.example.com")
      desc should include("POST https://oidc.example.com/obp-oidc/token")
      desc should include("OIDC discovery endpoint: https://oidc.example.com/obp-oidc/.well-known/openid-configuration")
      desc should include("GET https://api.example.com/obp/v5.1.0/well-known")
    }
  }
}

package code.api.ResourceDocs1_4_0

import code.api.ResourceDocs1_4_0.ResourceDocs140.ImplementationsResourceDocs
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.api.util.{ApiRole, CustomJsonFormats}
import code.setup.{DefaultUsers, PropsReset}
import com.github.dwickern.macros.NameOf.nameOf
import code.api.util.APIUtil.OAuth._
import code.entitlement.Entitlement
import com.openbankproject.commons.util.{ApiVersion, Functions}
import io.swagger.parser.OpenAPIParser
import net.liftweb.json
import net.liftweb.json.JsonAST._
import net.liftweb.json.{Formats, JString, Serializer, TypeInfo}
import net.liftweb.util.Html5
import org.scalatest.Tag

import java.util
import scala.xml.NodeSeq

class SwaggerDocsTest extends ResourceDocsV140ServerSetup with PropsReset with DefaultUsers{
  object VersionOfApi extends Tag(ApiVersion.v1_4_0.toString)
  object ApiEndpoint1 extends Tag("getResourceDocsSwagger")
  
  override def beforeEach() = {
    super.beforeEach()
    setPropsValues(
      "api_disabled_versions" -> "[]",
      "api_enabled_versions" -> "[]"
    )
  }

  // here must supply a Serializer of json, to support Product type, because the follow type are Product:
  //ResourceDocsJson#ResourceDocJson.example_request_body
  //ResourceDocsJson#ResourceDocJson.success_response_body
  object ProductSerializer extends Serializer[Product] {
    private val CLAZZ = classOf[Product]

    override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, json.JValue), Product] = {
      case (TypeInfo(CLAZZ, _), json) if json == JNull || json == JNothing => null
      case (TypeInfo(CLAZZ, _), json: JObject) => json
    }

    override def serialize(implicit format: Formats): PartialFunction[Any, json.JValue] = Functions.doNothing
  }
  // here must supply a Serializer of json, to support Product type, because the follow type are ApiRole:
  //ResourceDocsJson#ResourceDocJson.roles
  object ApiRoleSerializer extends Serializer[ApiRole] {
    private val CLAZZ = classOf[ApiRole]
    override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, json.JValue), ApiRole] = {
      case (TypeInfo(CLAZZ, _), role) => {
        val roleName = (role \ "role").asInstanceOf[JString].s
        ApiRole.valueOf(roleName)
      }
    }

    override def serialize(implicit format: Formats): PartialFunction[Any, json.JValue] = {
      case null => JNull // not need do serialize
    }
  }
  override implicit val formats = CustomJsonFormats.formats + ProductSerializer + ApiRoleSerializer

  /**
   * API_Explorer side use this method, so it need to be right. 
   * @param html
   * @return
   */
  def stringToNodeSeq(html : String) : NodeSeq = {
    val newHtmlString =scala.xml.XML.loadString("<div>" + html + "</div>").toString()
    //Note: `parse` method: We much enclose the div, otherwise only the first element is returned. 
    Html5.parse(newHtmlString).head
  }
  
  
  feature(s"test ${ApiEndpoint1.name} ") {
    scenario(s"We will test ${ApiEndpoint1.name} Api - v5.0.0/v5.1.0 ", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV5_1Request / "resource-docs" / "v5.1.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)
      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      if (!errors.isEmpty) logger.info(s"Here is the wrong swagger json:    $swaggerJsonString")
      errors.isEmpty should be (true)
    }
    
    scenario(s"We will test ${ApiEndpoint1.name} Api - v4.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v4.0.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)
      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      if (!errors.isEmpty) logger.info(s"Here is the wrong swagger json:    $swaggerJsonString")
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api - v1.2.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.2.1" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }
    
  }

  //Note: it is tricky to validate the swagger string, I just find this : https://github.com/swagger-api/swagger-parser/issues/718
  //So follow it to call the `Validate` method:
  //https://github.com/OpenAPITools/openapi-generator/blob/master/modules/openapi-generator-cli/src/main/java/org/openapitools/codegen/cmd/Validate.java#L46
  def ValidateSwaggerString (swaggerJsonString: String)= {
    val result = new OpenAPIParser().readContents(swaggerJsonString, null, null)
    val messageList: util.List[String] = result.getMessages()

    val errors = new util.HashSet[String](messageList)
    val warnings = new util.HashSet[String]

    val sb = new StringBuilder

    if (!errors.isEmpty) {
      sb.append("Errors:").append(System.lineSeparator)
      errors.forEach((msg: String) => sb.append("\t-").append(msg).append(System.lineSeparator))
    }

    if (!warnings.isEmpty) {
      sb.append("Warnings: ").append(System.lineSeparator)
      warnings.forEach((msg: String) => sb.append("\t-").append(msg).append(System.lineSeparator))
    }

    if (!errors.isEmpty) {
      sb.append(System.lineSeparator)
      sb.append("[error] Spec has ").append(errors.size).append(" errors.")
      System.err.println(sb.toString)
      System.exit(1)
    }
    else if (!warnings.isEmpty) {
      sb.append(System.lineSeparator)
      sb.append("[info] Spec has ").append(warnings.size).append(" recommendation(s).")
    }
    else { // we say "issues" here rather than "errors" to account for both errors and issues.
      sb.append("No validation issues detected.")
    }
    val allMessages = sb.toString
    logger.info(s"validatedSwaggerResult.errors $errors")
    logger.info(s"validatedSwaggerResult.warnings $warnings")
    logger.info(s"validatedSwaggerResult.allMessages $allMessages")
    
    (errors, warnings, allMessages)
  }

  // Additional tests to verify that the Swagger/OpenAPI endpoints respect the resource_docs_requires_role prop.
  // These are minimal checks that mirror the behaviour validated elsewhere (Lift/http4s tests).
  feature(s"Swagger & OpenAPI access control for resource_docs_requires_role") {
    scenario("Swagger - public access when resource_docs_requires_role is false", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "false",
      )
      val requestGetSwagger = (ResourceDocsV5_1Request / "resource-docs" / "v5.1.0" / "swagger").GET
      val responseGetSwagger = makeGetRequest(requestGetSwagger)
      responseGetSwagger.code should equal(200)
    }

    scenario("Swagger - unauthenticated rejected when resource_docs_requires_role is true", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetSwagger = (ResourceDocsV5_1Request / "resource-docs" / "v5.1.0" / "swagger").GET
      val responseGetSwagger = makeGetRequest(requestGetSwagger)
      // Lift endpoints typically return 401 with AuthenticatedUserIsRequired message when auth required
      responseGetSwagger.code should equal(401)
      responseGetSwagger.body.toString should include(AuthenticatedUserIsRequired)
    }

    scenario("Swagger - authenticated but missing role gets 403", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetSwagger = (ResourceDocsV5_1Request / "resource-docs" / "v5.1.0" / "swagger").GET <@ (user1)
      val responseGetSwagger = makeGetRequest(requestGetSwagger)
      responseGetSwagger.code should equal(403)
      responseGetSwagger.body.toString should include(UserHasMissingRoles)
      responseGetSwagger.body.toString should include(ApiRole.canReadResourceDoc.toString())
    }

    scenario("Swagger - authenticated and entitled canReadResourceDoc returns 200", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      // grant the entitlement to the resource user used in tests
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canReadResourceDoc.toString)
      val requestGetSwagger = (ResourceDocsV5_1Request / "resource-docs" / "v5.1.0" / "swagger").GET <@ (user1)
      val responseGetSwagger = makeGetRequest(requestGetSwagger)
      responseGetSwagger.code should equal(200)
    }

    // OpenAPI JSON checks (v6.0.0 used elsewhere for OpenAPI tests)
    scenario("OpenAPI JSON - public access when resource_docs_requires_role is false", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "false",
      )
      val requestGetOpenAPI = (ResourceDocsV6_0Request / "resource-docs" / "v6.0.0" / "openapi").GET <<? List(("tags", "Consumer"))
      val responseGetOpenAPI = makeGetRequest(requestGetOpenAPI)
      responseGetOpenAPI.code should equal(200)
    }

    scenario("OpenAPI JSON - unauthenticated rejected when resource_docs_requires_role is true", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetOpenAPI = (ResourceDocsV6_0Request / "resource-docs" / "v6.0.0" / "openapi").GET <<? List(("tags", "Consumer"))
      val responseGetOpenAPI = makeGetRequest(requestGetOpenAPI)
      responseGetOpenAPI.code should equal(401)
      responseGetOpenAPI.body.toString should include(AuthenticatedUserIsRequired)
    }

    scenario("OpenAPI YAML - raw response: public access when resource_docs_requires_role is false", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "false",
      )
      val requestGetOpenAPIYAML = (ResourceDocsV6_0Request / "resource-docs" / "v6.0.0" / "openapi.yaml").GET <<? List(("tags", "Consumer"))
      val responseGetOpenAPIYAML = makeGetRequest(requestGetOpenAPIYAML)
      responseGetOpenAPIYAML.code should equal(200)
      // body should be non-empty YAML
      responseGetOpenAPIYAML.body.toString.trim.nonEmpty should be (true)
    }

    // The OpenAPI routes used to be gated on `prefix == "v6.0.0"` by the
    // centralised Http4sResourceDocs service — replicating the historical Lift
    // setup where only ResourceDocs600 registered them. The gate was removed
    // because the spec content only depends on the API-version path segment,
    // not on the URL prefix. Verify a non-v6 prefix is now served.
    scenario("OpenAPI JSON - served for non-v6.0.0 URL prefix (v5.1.0)", ApiEndpoint1, VersionOfApi) {
      setPropsValues("resource_docs_requires_role" -> "false")
      val req = (ResourceDocsV5_1Request / "resource-docs" / "v5.1.0" / "openapi").GET <<? List(("tags", "Consumer"))
      val resp = makeGetRequest(req)
      resp.code should equal(200)
    }

    scenario("OpenAPI YAML - served for non-v6.0.0 URL prefix (v5.1.0)", ApiEndpoint1, VersionOfApi) {
      setPropsValues("resource_docs_requires_role" -> "false")
      val req = (ResourceDocsV5_1Request / "resource-docs" / "v5.1.0" / "openapi.yaml").GET <<? List(("tags", "Consumer"))
      val resp = makeGetRequest(req)
      resp.code should equal(200)
      resp.body.toString.trim.nonEmpty should be (true)
    }

  }

}

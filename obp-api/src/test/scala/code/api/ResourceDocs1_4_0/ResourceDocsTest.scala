package code.api.ResourceDocs1_4_0

import code.api.ResourceDocs1_4_0.ResourceDocs140.ImplementationsResourceDocs
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.APIUtil.OAuth._

import java.util
import code.api.util.{ApiRole, CustomJsonFormats}
import code.api.v1_4_0.JSONFactory1_4_0.ResourceDocsJson
import code.setup.{DefaultUsers, PropsReset}
import com.openbankproject.commons.util.{ApiVersion, Functions}
import com.github.dwickern.macros.NameOf.nameOf
import io.swagger.parser.OpenAPIParser
import net.liftweb.json
import net.liftweb.json.JsonAST._
import net.liftweb.json.{Formats, JString, Serializer, TypeInfo}
import net.liftweb.util.Html5
import org.scalatest.Tag

import scala.xml.NodeSeq

class ResourceDocsTest extends ResourceDocsV140ServerSetup with PropsReset with DefaultUsers{
  object VersionOfApi extends Tag(ApiVersion.v1_4_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(ImplementationsResourceDocs.getResourceDocsObp))
  object ApiEndpoint2 extends Tag(nameOf(ImplementationsResourceDocs.getResourceDocsSwagger))
  object ApiEndpoint3 extends Tag(nameOf(ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp))
//  object ApiEndpoint4 extends Tag(nameOf(ImplementationsResourceDocs.getStaticResourceDocsObp))

  
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
    scenario(s"We will test ${ApiEndpoint1.name} Api -v5.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV5_0Request / "resource-docs" / "v5.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv5.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV5_0Request / "resource-docs" / "OBPv5.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }
    
    scenario(s"We will test ${ApiEndpoint1.name} Api -v4.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v4.0.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv4.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "OBPv4.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v3.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.1.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      org.scalameta.logger.elem(responseGetObp)
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv3.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "OBPv3.1.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v3.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.0.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv3.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "OBPv3.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v2.2.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.2.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv2.2.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "OBPv2.2.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v2.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.1.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv2.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "OBPv2.1.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v2.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.0.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv2.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "OBPv2.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.4.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.4.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv1.4.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "OBPv1.4.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }
    
    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.3.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.3.0" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv1.3.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "OBPv1.3.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.2.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.2.1" / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv1.2.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "OBPv1.2.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }
    
    
    scenario(s"We will test ${ApiEndpoint1.name} Api -BGv1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "BGv1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.3", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.3" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }
    
    scenario(s"We will test ${ApiEndpoint1.name} Api -BGv1.3", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "BGv1.3" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v3.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }
    
    scenario(s"We will test ${ApiEndpoint1.name} Api -UKv3.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "UKv3.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v4.0.0 - resource_docs_requires_role props", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v4.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(401)
      responseGetObp.toString contains(UserNotLoggedIn) should be (true)
    }

    scenario(s"We will test ${ApiEndpoint1.name} Api -v4.0.0 - resource_docs_requires_role props- login in user", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v4.0.0" / "obp").GET <@ (user1)
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(403)
      responseGetObp.toString contains(UserHasMissingRoles) should be (true)
      responseGetObp.toString contains( ApiRole.canReadResourceDoc.toString()) should be (true)
    }
    
  }

  feature(s"test ${ApiEndpoint2.name} ") {
    scenario(s"We will test ${ApiEndpoint2.name} Api - v5.0.0/v5.0.0 ", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV5_0Request / "resource-docs" / "v5.0.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)
      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      if (!errors.isEmpty) logger.info(s"Here is the wrong swagger json:    $swaggerJsonString")
      errors.isEmpty should be (true)
    }
    
     scenario(s"We will test ${ApiEndpoint2.name} Api - v5.0.0/v4.0.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV5_0Request / "resource-docs" / "v4.0.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)
      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      if (!errors.isEmpty) logger.info(s"Here is the wrong swagger json:    $swaggerJsonString")
      errors.isEmpty should be (true)
    }
    
    scenario(s"We will test ${ApiEndpoint2.name} Api - v4.0.0", ApiEndpoint2, VersionOfApi) {
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

    scenario(s"We will test ${ApiEndpoint2.name} Api - v3.1.1", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.1.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v3.0.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.0.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)
      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v2.2.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.2.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v2.1.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.1.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)
      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v2.0.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v2.0.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v1.4.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.4.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v1.3.0", ApiEndpoint2, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v1.3.0" / "swagger").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.code should equal(200)
      val swaggerJsonString = json.compactRender(responseGetObp.body)

      val validatedSwaggerResult = ValidateSwaggerString(swaggerJsonString)
      val errors = validatedSwaggerResult._1
      errors.isEmpty should be (true)
    }

    scenario(s"We will test ${ApiEndpoint2.name} Api - v1.2.1", ApiEndpoint2, VersionOfApi) {
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

  feature(s"test ${ApiEndpoint3.name} ") {
    scenario(s"We will test ${ApiEndpoint3.name} Api -v5.0.0/v4.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v5.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v4.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v4.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -OBPv4.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "OBPv4.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v3.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v3.1.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      org.scalameta.logger.elem(responseGetObp)
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -OBPv3.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "OBPv3.1.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v3.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v3.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -OBPv3.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "OBPv3.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v2.2.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v2.2.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -OBPv2.2.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "OBPv2.2.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v2.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v2.1.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -OBPv2.1.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "OBPv2.1.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v2.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v2.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -OBPv2.0.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "OBPv2.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v1.4.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v1.4.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -OBPv1.4.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "OBPv1.4.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v1.3.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v1.3.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -OBPv1.3.0", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "OBPv1.3.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v1.2.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v1.2.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -OBPv1.2.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "OBPv1.2.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }


    scenario(s"We will test ${ApiEndpoint3.name} Api -BGv1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "BGv1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v1.3", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v1.3" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -BGv1.3", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "BGv1.3" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v3.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v3.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -UKv3.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "UKv3.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v4.0.0 - resource_docs_requires_role props", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v4.0.0" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(401)
      responseGetObp.toString contains(UserNotLoggedIn) should be (true)
    }

    scenario(s"We will test ${ApiEndpoint3.name} Api -v4.0.0 - resource_docs_requires_role props- login in user", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v4.0.0" / "obp").GET <@ (user1)
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(403)
      responseGetObp.toString contains(UserHasMissingRoles) should be (true)
      responseGetObp.toString contains(ApiRole.canReadDynamicResourceDocsAtOneBank.toString) should be (true)
    }

  }
//
//  feature(s"test ${ApiEndpoint4.name} ") {
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v4.0.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v4.0.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv4.0.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "OBPv4.0.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v3.1.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v3.1.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      org.scalameta.logger.elem(responseGetObp)
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv3.1.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "OBPv3.1.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v3.0.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v3.0.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv3.0.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "OBPv3.0.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v2.2.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v2.2.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv2.2.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "OBPv2.2.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v2.1.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v2.1.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv2.1.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "OBPv2.1.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v2.0.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v2.0.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv2.0.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "OBPv2.0.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.4.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v1.4.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv1.4.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "OBPv1.4.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.3.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v1.3.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv1.3.0", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "OBPv1.3.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.2.1", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v1.2.1" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -OBPv1.2.1", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "OBPv1.2.1" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -BGv1", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "BGv1" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v1.3", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v1.3" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -BGv1.3", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "BGv1.3" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v3.1", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v3.1" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -UKv3.1", ApiEndpoint1, VersionOfApi) {
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "UKv3.1" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(200)
//      //This should not throw any exceptions
//      responseDocs.resource_docs.map(responseDoc => stringToNodeSeq(responseDoc.description))
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v4.0.0 - resource_docs_requires_role props", ApiEndpoint1, VersionOfApi) {
//      setPropsValues(
//        "resource_docs_requires_role" -> "true",
//      )
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v4.0.0" / "obp").GET
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(401)
//      responseGetObp.toString contains(UserNotLoggedIn) should be (true)
//    }
//
//    scenario(s"We will test ${ApiEndpoint1.name} Api -v4.0.0 - resource_docs_requires_role props- login in user", ApiEndpoint1, VersionOfApi) {
//      setPropsValues(
//        "resource_docs_requires_role" -> "true",
//      )
//      val requestGetObp = (ResourceDocsV4_0Request / "static-resource-docs" / "v4.0.0" / "obp").GET <@ (user1)
//      val responseGetObp = makeGetRequest(requestGetObp)
//      And("We should get  200 and the response can be extract to case classes")
//      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
//      responseGetObp.code should equal(403)
//      responseGetObp.toString contains(UserHasMissingRoles) should be (true)
//      responseGetObp.toString contains( ApiRole.canReadStaticResourceDoc.toString()) should be (true)
//    }
//
//  }

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
} 

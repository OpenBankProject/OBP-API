package code.api.ResourceDocs1_4_0

import org.json4s._
import code.api.Constant
import code.api.ResourceDocs1_4_0.ResourceDocs140.ImplementationsResourceDocs
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{InvalidApiCollectionIdParameter, UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.util.{ApiRole, CustomJsonFormats}
import code.api.v1_4_0.JSONFactory1_4_0.ResourceDocsJson
import code.setup.{DefaultUsers, PropsReset}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiVersion, Functions}
import com.openbankproject.commons.util.json
import org.json4s.{Formats, JString, Serializer, TypeInfo}
import org.scalatest.Tag

import scala.xml.NodeSeq

class ResourceDocsTest extends ResourceDocsV140ServerSetup with PropsReset with DefaultUsers{
  object VersionOfApi extends Tag(ApiVersion.v1_4_0.toString)
  object ApiEndpoint1 extends Tag("getResourceDocsObp")
  object ApiEndpoint2 extends Tag("getBankLevelDynamicResourceDocsObp")

  private val v600 = ApiVersion.v6_0_0.toString
  private val fq600 = ApiVersion.v6_0_0.fullyQualifiedVersion
  private val v510 = ApiVersion.v5_1_0.toString
  private val fq510 = ApiVersion.v5_1_0.fullyQualifiedVersion
  private val v500 = ApiVersion.v5_0_0.toString
  private val fq500 = ApiVersion.v5_0_0.fullyQualifiedVersion
  private val v400 = ApiVersion.v4_0_0.toString
  private val fq400 = ApiVersion.v4_0_0.fullyQualifiedVersion
  private val v310 = ApiVersion.v3_1_0.toString
  private val fq310 = ApiVersion.v3_1_0.fullyQualifiedVersion
  private val v300 = ApiVersion.v3_0_0.toString
  private val fq300 = ApiVersion.v3_0_0.fullyQualifiedVersion
  private val v220 = ApiVersion.v2_2_0.toString
  private val fq220 = ApiVersion.v2_2_0.fullyQualifiedVersion
  private val v210 = ApiVersion.v2_1_0.toString
  private val fq210 = ApiVersion.v2_1_0.fullyQualifiedVersion
  private val v200 = ApiVersion.v2_0_0.toString
  private val fq200 = ApiVersion.v2_0_0.fullyQualifiedVersion
  private val v140 = ApiVersion.v1_4_0.toString
  private val fq140 = ApiVersion.v1_4_0.fullyQualifiedVersion
  private val v130 = ApiVersion.v1_3_0.toString
  private val fq130 = ApiVersion.v1_3_0.fullyQualifiedVersion
  private val v121 = ApiVersion.v1_2_1.toString
  private val fq121 = ApiVersion.v1_2_1.fullyQualifiedVersion
  
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
  override implicit val formats: org.json4s.Formats = CustomJsonFormats.formats + ProductSerializer + ApiRoleSerializer

  /**
   * API_Explorer side use this method, so it need to be right. 
   * @param html
   * @return
   */
  def stringToNodeSeq(html : String) : NodeSeq = {
    //Note: we must enclose the div, otherwise only the first element is returned.
    scala.xml.XML.loadString("<div>" + html + "</div>")
  }
  
  
  Feature(s"test ${ApiEndpoint1.name} ") {
    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v600", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV6_0Request / "resource-docs" / v600 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      responseDocs.resource_docs.head.implemented_by.technology should (equal(Some(Constant.TECHNOLOGY_LIFTWEB)) or equal(Some(Constant.TECHNOLOGY_HTTP4S)))
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }
    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq600", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV6_0Request / "resource-docs" / fq600 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }
    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v500", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV5_0Request / "resource-docs" / v500 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      responseDocs.resource_docs.head.implemented_by.technology shouldBe None
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }
    
    Scenario("Test OpenAPI endpoint with valid parameters", ApiEndpoint1, VersionOfApi) {
      val requestGetOpenAPI = (ResourceDocsV6_0Request / "resource-docs" / v600 / "openapi").GET <<? List(("content", "static"), ("tags", "Account"))
      val responseGetOpenAPI = makeGetRequest(requestGetOpenAPI)
      responseGetOpenAPI.code should equal(200)
    }
    
    Scenario("Test OpenAPI endpoint with invalid content parameter", ApiEndpoint1, VersionOfApi) {
      val requestGetOpenAPI = (ResourceDocsV6_0Request / "resource-docs" / v600 / "openapi").GET <<? List(("content", "invalid"))
      val responseGetOpenAPI = makeGetRequest(requestGetOpenAPI)
      responseGetOpenAPI.code should equal(400)
      responseGetOpenAPI.body.toString should include("OBP-10052")
    }
    
    Scenario("Test OpenAPI endpoint with empty tags parameter", ApiEndpoint1, VersionOfApi) {
      val requestGetOpenAPI = (ResourceDocsV6_0Request / "resource-docs" / v600 / "openapi").GET <<? List(("tags", ""))
      val responseGetOpenAPI = makeGetRequest(requestGetOpenAPI)
      responseGetOpenAPI.code should equal(400)
      responseGetOpenAPI.body.toString should include("OBP-10053")
    }
    
    Scenario("Test OpenAPI endpoint with empty functions parameter", ApiEndpoint1, VersionOfApi) {
      val requestGetOpenAPI = (ResourceDocsV6_0Request / "resource-docs" / v600 / "openapi").GET <<? List(("functions", ""))
      val responseGetOpenAPI = makeGetRequest(requestGetOpenAPI)
      responseGetOpenAPI.code should equal(400)
      responseGetOpenAPI.body.toString should include("OBP-10054")
    }
    
    Scenario("Test OpenAPI endpoint with valid multiple tags", ApiEndpoint1, VersionOfApi) {
      val requestGetOpenAPI = (ResourceDocsV6_0Request / "resource-docs" / v600 / "openapi").GET <<? List(("tags", "Account,Bank"), ("content", "static"))
      val responseGetOpenAPI = makeGetRequest(requestGetOpenAPI)
      responseGetOpenAPI.code should equal(200)
    }
    
    Scenario("Test OpenAPI endpoint with Account-Firehose tag and static content", ApiEndpoint1, VersionOfApi) {
      val requestGetOpenAPI = (ResourceDocsV6_0Request / "resource-docs" / v600 / "openapi").GET <<? List(("content", "static"), ("tags", "Account-Firehose"))
      val responseGetOpenAPI = makeGetRequest(requestGetOpenAPI)
      responseGetOpenAPI.code should equal(200)
    }
    
    Scenario("Test OpenAPI endpoint with empty api-collection-id parameter", ApiEndpoint1, VersionOfApi) {
      val requestGetOpenAPI = (ResourceDocsV6_0Request / "resource-docs" / v600 / "openapi").GET <<? List(("api-collection-id", ""))
      val responseGetOpenAPI = makeGetRequest(requestGetOpenAPI)
      responseGetOpenAPI.code should equal(400)
      responseGetOpenAPI.body.toString should include(InvalidApiCollectionIdParameter)
    }
    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v510", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV5_0Request / "resource-docs" / v510 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq500", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV5_0Request / "resource-docs" / fq500 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }
    
    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v400", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v400 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq400", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / fq400 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v310", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v310 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      println(s"responseGetObp = $responseGetObp")
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq310", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / fq310 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v300", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v300 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq300", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / fq300 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v220", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v220 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq220", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / fq220 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v210", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v210 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq210", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / fq210 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v200", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v200 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq200", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / fq200 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v140", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v140 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq140", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / fq140 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }
    
    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v130", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v130 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq130", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / fq130 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v121", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v121 / "obp").GET 
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$fq121", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / fq121 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -v1.3", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / ConstantsBG.berlinGroupVersion1.apiShortVersion / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }
    
    Scenario(s"We will test ${ApiEndpoint1.name} Api -BGv1.3", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / s"BG${ConstantsBG.berlinGroupVersion1.apiShortVersion}" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -v3.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "v3.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }
    
    Scenario(s"We will test ${ApiEndpoint1.name} Api -UKv3.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / "UKv3.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v400 - resource_docs_requires_role props", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v400 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(401)
      responseGetObp.toString contains(AuthenticatedUserIsRequired) should be (true)
    }

    Scenario(s"We will test ${ApiEndpoint1.name} Api -$v400 - resource_docs_requires_role props- login in user", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetObp = (ResourceDocsV4_0Request / "resource-docs" / v400 / "obp").GET <@ (user1)
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(403)
      responseGetObp.toString contains(UserHasMissingRoles) should be (true)
      responseGetObp.toString contains( ApiRole.canReadResourceDoc.toString()) should be (true)
    }
    
  }

  Feature(s"test ${ApiEndpoint2.name} ") {
    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v600", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v600 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }
    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq600", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq600 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }
    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v500/$v400", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v500 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v400", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v400 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq400", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq400 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v310", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v310 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      println(s"responseGetObp = $responseGetObp")
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq310", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq310 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v300", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v300 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq300", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq300 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v220", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v220 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq220", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq220 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v210", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v210 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq210", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq210 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v200", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v200 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq200", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq200 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v140", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v140 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq140", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq140 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v130", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v130 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq130", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq130 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v121", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v121 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$fq121", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / fq121 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -v1.3", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / ConstantsBG.berlinGroupVersion1.apiShortVersion / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -BGv1.3", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / s"BG${ConstantsBG.berlinGroupVersion1.apiShortVersion}" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -v3.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "v3.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -UKv3.1", ApiEndpoint1, VersionOfApi) {
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / "UKv3.1" / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(200)
      //This should not throw any exceptions
      responseDocs.resource_docs.take(3).foreach(doc => stringToNodeSeq(doc.description))
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v400 - resource_docs_requires_role props", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v400 / "obp").GET
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(401)
      responseGetObp.toString contains(AuthenticatedUserIsRequired) should be (true)
    }

    Scenario(s"We will test ${ApiEndpoint2.name} Api -$v400 - resource_docs_requires_role props- login in user", ApiEndpoint1, VersionOfApi) {
      setPropsValues(
        "resource_docs_requires_role" -> "true",
      )
      val requestGetObp = (ResourceDocsV1_4Request /"banks"/ testBankId1.value/ "resource-docs" / v400 / "obp").GET <@ (user1)
      val responseGetObp = makeGetRequest(requestGetObp)
      And("We should get  200 and the response can be extract to case classes")
      val responseDocs = responseGetObp.body.extract[ResourceDocsJson]
      responseGetObp.code should equal(403)
      responseGetObp.toString contains(UserHasMissingRoles) should be (true)
      responseGetObp.toString contains(ApiRole.canReadDynamicResourceDocsAtOneBank.toString) should be (true)
    }

  }
  
} 

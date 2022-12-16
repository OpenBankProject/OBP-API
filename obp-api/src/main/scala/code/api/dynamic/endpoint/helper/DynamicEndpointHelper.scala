package code.api.dynamic.endpoint.helper

import akka.http.scaladsl.model.{HttpMethods, HttpMethod => AkkaHttpMethod}
import code.DynamicData.{DynamicDataProvider, DynamicDataT}
import code.DynamicEndpoint.{DynamicEndpointProvider, DynamicEndpointT}
import code.api.util.APIUtil.{BigDecimalBody, BigIntBody, BooleanBody, DoubleBody, EmptyBody, FloatBody, IntBody, JArrayBody, LongBody, PrimaryDataBody, ResourceDoc, StringBody}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{DynamicDataNotFound, InvalidUrlParameters, UnknownError, UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{APIUtil, ApiRole, ApiTag, CommonUtil, CustomJsonFormats, NewStyle}
import com.openbankproject.commons.util.{ApiShortVersions, ApiStandards, ApiVersion}
import com.openbankproject.commons.util.Functions.Memo
import io.swagger.v3.oas.models.PathItem.HttpMethod
import io.swagger.v3.oas.models.media._
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.{ApiResponse, ApiResponses}
import io.swagger.v3.oas.models.{OpenAPI, Operation, PathItem}
import io.swagger.v3.parser.OpenAPIV3Parser
import net.liftweb.common.{Box, Full}
import net.liftweb.http.Req
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json.JsonAST.{JArray, JField, JNothing, JObject, JValue}
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser.ParseException
import org.apache.commons.lang3.{StringUtils, Validate}
import net.liftweb.util.{StringHelpers, ThreadGlobal}
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils

import java.io.File
import java.nio.charset.Charset
import java.util
import java.util.regex.Pattern
import java.util.{Date, UUID}
import com.openbankproject.commons.model.enums.DynamicEntityOperation.GET_ALL
import io.swagger.v3.oas.models.examples.Example
import net.liftweb.json.{Formats, JBool}

import scala.collection.JavaConverters._
import scala.collection.immutable.List
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}


object DynamicEndpointHelper extends RestHelper {
  protected override implicit def formats: Formats = CustomJsonFormats.formats

  /**
   * dynamic endpoints url prefix
   */
  val urlPrefix = APIUtil.getPropsValue("dynamic_endpoints_url_prefix", "")
  private val implementedInApiVersion = ApiVersion.v4_0_0
  private val IsDynamicEntityUrl = """.*dynamic_entity.*"""
  private val IsMockUrlString = """.*obp_mock(?::\d+)?.*"""
  private val IsMockUrl = IsMockUrlString.r

  def isDynamicEntityResponse (serverUrl : String) = serverUrl matches (IsDynamicEntityUrl)
  def isMockedResponse (serverUrl : String) = serverUrl matches (IsMockUrlString)

  /**
   * 1st: we check if it OpenAPI3.0,
   * 2rd: if not, we will check Swagger2.0
   * other case, we will return ""
   * @param openApiJson it can be swagger2.0 or openApi3.0
   * @return the openapi
   */
  def getOpenApiVersion(openApiJson: String) ={
    val jValue = json.parse(openApiJson)
    val openApiVersion = jValue \ "openapi"
    val swaggerVersion = jValue \ "swagger"
    if (openApiVersion != JNothing ) {
      openApiVersion.values.toString.trim
    } else if (swaggerVersion != JNothing){
      swaggerVersion.values.toString.trim
    }else{
      ""
    }
  }

  /**
   * 1st: we check if it OpenAPI3.0, we will keep the OpenAPI3.0 format
   * 
   * 2rd: if not, we will change it as Swagger2.0 format
   * 
   */
  def changeOpenApiVersionHost(openApiJson: String, newHost:String) ={
    //for this case, there is no host/servers object, we will add the object
    //https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/api-with-examples.json
    val openApiVersion = getOpenApiVersion(openApiJson)
    val openApiJValue = json.parse(openApiJson)
    val serversField = openApiJValue \ "servers"
    val hostField = openApiJValue \ "host"
    
    if (openApiVersion.startsWith("3.") && serversField != JNothing) {
      json.compactRender(openApiJValue.replace("servers"::Nil, JArray(List(JObject(List(JField("url",newHost)))))))
    } else if (openApiVersion.startsWith("3.") && serversField == JNothing) {
      val newServers =  json.parse(s"""{
                          |  "servers": [
                          |    {
                          |      "url": "$newHost"
                          |    }
                          |  ]
                          |}""".stripMargin)
      json.compactRender(openApiJValue merge newServers)
    } else if(hostField != JNothing){
      json.compactRender(openApiJValue.replace("host" :: Nil, JString(newHost)))
    } else {
      val host =  json.parse(s"""{"host":  "$newHost"}""".stripMargin)
      json.compactRender(openApiJValue merge host)
    }
   
  }
  
  private def dynamicEndpointInfos: List[DynamicEndpointInfo] = {
    val dynamicEndpoints: List[DynamicEndpointT] = DynamicEndpointProvider.connectorMethodProvider.vend.getAll(None)
    val infos = dynamicEndpoints.map(it => buildDynamicEndpointInfo(it.swaggerString, it.dynamicEndpointId.get, it.bankId))
    infos
  }

  def allDynamicEndpointRoles: List[ApiRole] = {
    for {
      dynamicEndpoint <- DynamicEndpointProvider.connectorMethodProvider.vend.getAll(None)
      info = buildDynamicEndpointInfo(dynamicEndpoint.swaggerString, dynamicEndpoint.dynamicEndpointId.get, dynamicEndpoint.bankId)
      role <- getRoles(info)
    } yield role
  }

  def getRoles(bankId: Option[String], dynamicEndpointId: String): List[ApiRole] = {
    val foundInfos: Box[DynamicEndpointInfo] = DynamicEndpointProvider.connectorMethodProvider.vend.get(bankId, dynamicEndpointId)
      .map(dynamicEndpoint => buildDynamicEndpointInfo(dynamicEndpoint.swaggerString, dynamicEndpoint.dynamicEndpointId.get, dynamicEndpoint.bankId))


    val roles: List[ApiRole] = foundInfos match {
      case Full(x) => getRoles(x)
      case _ => Nil
    }

    roles
  }

  def listOfRolesToUseAllDynamicEndpointsAOneBank(bankId: Option[String]): List[ApiRole] = {
    val foundInfos: List[DynamicEndpointInfo] = DynamicEndpointProvider.connectorMethodProvider.vend.getAll(bankId)
      .map(dynamicEndpoint => buildDynamicEndpointInfo(dynamicEndpoint.swaggerString, dynamicEndpoint.dynamicEndpointId.get, dynamicEndpoint.bankId))

    foundInfos.map(getRoles(_)).flatten.toSet.toList
  }

  def getRoles(dynamicEndpointInfo: DynamicEndpointInfo): List[ApiRole] =
    for {
      resourceDoc <- dynamicEndpointInfo.resourceDocs.toList
      rolesOption = resourceDoc.roles
      if rolesOption.isDefined
      role <- rolesOption.get
    } yield role

  /**
   * extract request body, no matter GET, POST, PUT or DELETE method
   */
  object DynamicReq extends JsonTest with JsonBody {

    private val ExpressionRegx = """\{(.+?)\}""".r
    /**
     * unapply Request to (request url, json, http method, request parameters, path parameters, role)
     * request url is  current request target url to remote server
     * json is request body
     * http method is request http method
     * request parameters : http request query parameters, eg:  /pet/findByStatus?status=available => (status, List(available))
     * path parameters: /banks/{bankId}/users/{userId} bankId and userId corresponding key to value
     * role is current endpoint required entitlement
     * @param r HttpRequest
     * @return (adapterUrl, requestBodyJson, httpMethod, requestParams, pathParams, role, operationId, mockResponseCode->mockResponseBody)
     */
    def unapply(r: Req): Option[(String, JValue, AkkaHttpMethod, Map[String, List[String]], Map[String, String], ApiRole, String, Option[(Int, JValue)], Option[String])] = {
      
      val requestUri = r.request.uri //eg: `/obp/dynamic-endpoint/fashion-brand-list/BRAND_ID`
      val partPath = r.path.partPath //eg: List("fashion-brand-list","BRAND_ID"), the dynamic is from OBP URL, not in the partPath now.
      
      if (!testResponse_?(r) || !requestUri.startsWith(s"/${ApiStandards.obp.toString}/${ApiShortVersions.`dynamic-endpoint`.toString}"+urlPrefix))//if check the Content-Type contains json or not, and check the if it is the `dynamic_endpoints_url_prefix`
        None //if do not match `URL and Content-Type`, then can not find this endpoint. return None.
      else {
        val akkaHttpMethod = HttpMethods.getForKeyCaseInsensitive(r.requestType.method).get
        val httpMethod = HttpMethod.valueOf(r.requestType.method)
        val urlQueryParameters = r.params
        // url that match original swagger endpoint.
        val url = partPath.mkString("/", "/", "") // eg: --> /feature-test
        val foundDynamicEndpoint: Option[(String, String, Int, ResourceDoc, Option[String])] = dynamicEndpointInfos
          .map(_.findDynamicEndpoint(httpMethod, url))
          .collectFirst {
            case Some(x) => x
          }

        foundDynamicEndpoint
          .flatMap { it =>
            val (serverUrl, endpointUrl, code, doc, bankId) = it

            val pathParams: Map[String, String] = if(endpointUrl == url) {
              Map.empty[String, String]
            } else {
              val tuples: Array[(String, String)] = StringUtils.split(endpointUrl, "/").zip(partPath)
              tuples.collect {
                case (ExpressionRegx(name), value) => name->value
              }.toMap
            }

            val mockResponse: Option[(Int, JValue)] = (serverUrl, doc.successResponseBody) match {
              case (IsMockUrl(), v: PrimaryDataBody[_]) =>
                //If the openAPI json do not have response body, we return true as default
                val response = if (v.toJValue == JNothing) {
                  JBool(true)
                } else{
                  v.toJValue
                }
                Some(code -> response)

              case (IsMockUrl(), v: JValue) =>
                //If the openAPI json do not have response body, we return true as default
                val response = if (v == JNothing) {
                  JBool(true)
                } else{
                  v
                }
                Some(code -> response)

              case (IsMockUrl(), v) =>
                Some(code -> json.Extraction.decompose(v))

              case _ => None
            }

            val Some(role::_) = doc.roles
            val requestBodyJValue = body(r).getOrElse(JNothing)
            Full(s"""$serverUrl$url""", requestBodyJValue, akkaHttpMethod, urlQueryParameters, pathParams, role, doc.operationId, mockResponse, bankId)
          }

      }
    }
  }


  def findExistingDynamicEndpoints(openAPI: OpenAPI): List[(HttpMethod, String)] = {
     for {
      (path, pathItem) <- openAPI.getPaths.asScala.toList
      (method: HttpMethod, _) <- pathItem.readOperationsMap.asScala
      if dynamicEndpointInfos.exists(_.existsEndpoint(method, path))
    } yield (method, path)

  }

  private val dynamicEndpointInfoMemo = new Memo[String, DynamicEndpointInfo]

  private def buildDynamicEndpointInfo(content: String, id: String, bankId:Option[String]): DynamicEndpointInfo =
    dynamicEndpointInfoMemo.memoize(content) {
      val openAPI: OpenAPI = parseSwaggerContent(content)
      buildDynamicEndpointInfo(openAPI, id, bankId)
    }

  def buildDynamicEndpointInfo(openAPI: OpenAPI, id: String, bankId:Option[String]): DynamicEndpointInfo = {
    val tags: List[ResourceDocTag] = List(ApiTag(openAPI.getInfo.getTitle), apiTagNewStyle, apiTagDynamicEndpoint, apiTagDynamic)

    val serverUrl = {
      val servers = openAPI.getServers
      assert(!servers.isEmpty, s"swagger host is mandatory, but current swagger host is empty, id=$id")
      servers.get(0).getUrl
    }

    val paths: mutable.Map[String, PathItem] = openAPI.getPaths.asScala
    def entitlementSuffix(path: String) = {
      val pathHashCode = Math.abs(path.hashCode).toString
      //eg: path can be "/" --> "/".hashCode => 47, the length is only 2, we need to prepare the worst case: 
      if(pathHashCode.length>3)
        pathHashCode.substring(0, 3)
      else
        pathHashCode.substring(0, 2)
    } // to avoid different swagger have same entitlement
    val dynamicEndpointItems: mutable.Iterable[DynamicEndpointItem] = for {
      (path, pathItem) <- paths
      (method: HttpMethod, op: Operation) <- pathItem.readOperationsMap.asScala
    } yield {
      val partialFunctionName: String = buildPartialFunctionName(method.name(), path)
      val requestVerb: String = method.name()
      val requestUrl: String = buildRequestUrl(path, urlPrefix)
      val summary: String = Option(pathItem.getSummary)
        .filter(StringUtils.isNotBlank)
        .getOrElse(buildSummary(openAPI, method, op, path))
      val description: String = Option(pathItem.getDescription)
        .filter(StringUtils.isNotBlank)
        .orElse(Option(op.getDescription))
        .filter(StringUtils.isNotBlank)
        .map(_.capitalize)
        .getOrElse(summary) +
        s"""
          |
          |MethodRouting settings example:
          |
          |<details>
          |
          |```
          |{
          |  "is_bank_id_exact_match":false,
          |  "method_name":"dynamicEndpointProcess",
          |  "connector_name":"rest_vMar2019",
          |  "bank_id_pattern":".*",
          |  "parameters":[
          |    {
          |        "key":"url_pattern",
          |        "value":"$serverUrl$path"
          |    },
          |    {
          |        "key":"http_method",
          |        "value":"$requestVerb"
          |    }
          |    {
          |        "key":"url",
          |        "value":"http://mydomain.com/xxx"
          |    }
          |  ]
          |}
          |```
          |
          |</details>
          |""".stripMargin
      val exampleRequestBody: Product = getRequestExample(openAPI, op.getRequestBody)
      val (successCode, successResponseBody: Product) = getResponseExample(openAPI, op.getResponses)
      val errorResponseBodies: List[String] = List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      )

      val roles: Option[List[ApiRole]] = {
        val entityName = getEntityName(openAPI, op)
        val roleNamePrefix = if(method == HttpMethod.POST) {
          "CanCreateDynamicEndpoint_"
        } else if(method == HttpMethod.PUT) {
          "CanUpdateDynamicEndpoint_"
        } else {
          val opName = method.name().toLowerCase().capitalize
          s"Can${opName}DynamicEndpoint_"
        }
        var roleName = if(StringUtils.isNotBlank(op.getOperationId)) {
          val prettyOperationId = op.getOperationId
            .replaceAll("""(?i)(get|find|search|add|create|delete|update|of|new|the|one|that|\s)""", "")
            .capitalize

          s"$roleNamePrefix$prettyOperationId${entitlementSuffix(path)}"
        } else if(StringUtils.isNotBlank(entityName)) {
          s"$roleNamePrefix$entityName${entitlementSuffix(path)}"
        } else {
          // Capitalize summary, remove disturbed word
          val prettySummary = StringHelpers.capify{
            summary.replaceAll("""(?i)\b(get|find|search|add|create|delete|update|a|new|the|one|that)\b""", "")
          }.replace(" ", "")

          s"$roleNamePrefix$prettySummary${entitlementSuffix(path)}"
        }
        // substring role name to avoid it have over the maximum length of db column.
        if(roleName.size > 64) {
          roleName = StringUtils.substring(roleName, 0, 53) + roleName.hashCode()
        }
        Some(List(
          ApiRole.getOrCreateDynamicApiRole(roleName, bankId.isDefined)
        ))
      }
      val doc = ResourceDoc(
        APIUtil.dynamicEndpointStub,
        implementedInApiVersion,
        partialFunctionName,
        requestVerb,
        requestUrl,
        StringHelpers.capify(summary),
        description,
        exampleRequestBody,
        successResponseBody,
        errorResponseBodies,
        tags,
        roles,
        createdByBankId= bankId
      )
      DynamicEndpointItem(path, successCode, doc)
    }

    DynamicEndpointInfo(id, dynamicEndpointItems, serverUrl, bankId)
  }

  private val PathParamRegx = """\{(.+?)\}""".r
  private val WordBoundPattern = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|-")

  def parseSwaggerContent(content: String): OpenAPI = {
    val tempSwaggerFile = File.createTempFile("temp", ".swagger")
    FileUtils.write(tempSwaggerFile, content, Charset.forName("utf-8"))
    val openAPI: OpenAPI = new OpenAPIV3Parser().read(tempSwaggerFile.getAbsolutePath)
    // Delete temp file when program exits, only if delete fail.
    if(!FileUtils.deleteQuietly(tempSwaggerFile)){
      tempSwaggerFile.deleteOnExit()
    }
    openAPI
  }

  private def buildRequestUrl(path: String, prefix: String = ""): String = {
    val url = StringUtils.split(s"$prefix/$path", "/")
    url.map {
      case PathParamRegx(param) => WordBoundPattern.matcher(param).replaceAll("_").toUpperCase()
      case v => v
    }.mkString("/", "/", "")
  }

  private def buildPartialFunctionName(httpMethod: String, path: String) = {
    val noQueryParamPath = path match {
      case p if path.contains("/?") => StringUtils.substringBefore(p, "/?")
      case p if path.contains("?") => StringUtils.substringBefore(p, "?")
      case p => p
    }
    val noExpressionPath = buildRequestUrl(noQueryParamPath) // replace expression to upper case, e.g: /abc/{helloWorld}/good -> /abc/{HELLO_WORLD}/good
                            .substring(1)        // remove the started character '/'

    s"dynamicEndpoint_${httpMethod}_$noExpressionPath".replaceAll("\\W", "_")
  }

  def buildOperationId(httpMethod: String, path: String): String = {
    val partialFunctionName = buildPartialFunctionName(httpMethod, path)
    APIUtil.buildOperationId(implementedInApiVersion, partialFunctionName)
  }

  def doc: ArrayBuffer[ResourceDoc] = {
    val docs = for {
      info <- dynamicEndpointInfos
      doc <- info.resourceDocs
    } yield doc

    ArrayBuffer[ResourceDoc](docs:_*)
  }

  private def buildSummary(openAPI: OpenAPI, method: HttpMethod, op: Operation, path: String): String = {
    if(StringUtils.isNotBlank(op.getSummary)) {
      op.getSummary
    } else {
      val opName = method.name().toLowerCase match {
        case "post" => "Create"
        case "put" => "Update"
        case v => v.capitalize
      }
      Option(getEntityName(openAPI, op))
        .map(entityName => s"$opName $entityName")
        .orElse(Option(op.getDescription).filterNot(StringUtils.isBlank))
        .orElse(Option(s"$opName $path"))
        .map(_.replaceFirst("(?i)((get|delete)\\s+\\S+).*", "$1"))
        .map(capitalize)
        .get
    }
  }

  private def getEntityName(openAPI: OpenAPI, op: Operation): String = {
    def getName(ref: String) = StringUtils.substringAfterLast(ref, "/").capitalize

    val body = op.getRequestBody

    val successResponse = Option(op.getResponses).flatMap(_.asScala.find(_._1.startsWith("20"))).map(_._2)
    if(body == null && successResponse.isEmpty) {
      null
    } else if(body != null && StringUtils.isNotBlank(body.get$ref())) {
      getName(body.get$ref())
    } else if(successResponse.isDefined && StringUtils.isNotBlank(successResponse.get.get$ref())) {
      getName(successResponse.get.get$ref())
    } else {
      val maybeMediaType: Option[MediaType] = Option(body)
        .flatMap(it => getMediaType(it.getContent))
        .orElse {
          successResponse.flatMap(it => getMediaType(it.getContent))
        }
      maybeMediaType match {
        // https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#mediaTypeObject
        // following rule is also valid in Swagger UI using this json (object foo)
        //: https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/api-with-examples.json
        // if schema is not null, then it has the 1st priority
        // if schema is null, 2rd priority is examples.
        // if schema is null and examples is null, 3rd priority is example field.
        case Some(mediaType) if mediaType.getSchema() != null =>
          val schema = mediaType.getSchema()
          if(schema.isInstanceOf[ArraySchema]) {
            val itemsRef = schema.asInstanceOf[ArraySchema].getItems.get$ref()
            getName(itemsRef)
          } else {
            List(schema.getName(), schema.get$ref())
              .find(StringUtils.isNotBlank)
              .map(getName)
              .orNull
          }
        case Some(mediaType) if mediaType.getExamples() != null =>{
          val examples: util.Map[String, Example] = mediaType.getExamples()
          val objectName: Option[String] = examples.keySet().asScala.headOption
          objectName.getOrElse(examples.values().toString)
        }
        case Some(mediaType) if mediaType.getExample() != null =>{
          val example: AnyRef = mediaType.getExample()
          example.toString //TODO, here better set a default value? or can get name from the object(but it depends on the input)
        }
        case None => null
      }
    }
  }

  private def capitalize(str: String): String =
    StringUtils.split(str, " ").map(_.capitalize).mkString(" ")

  private def getRequestExample(openAPI: OpenAPI, body: RequestBody): Product = {
    if(body == null || body.getContent == null) {
       EmptyBody
    } else if(StringUtils.isNotBlank(body.get$ref())) {
      val schema = getRefSchema(openAPI, body.get$ref())

      getExample(openAPI, schema)
    } else {
      //body.content is `REQUIRED` field
      val mediaType = getMediaType(body.getContent())
      assert(mediaType.isDefined, s"RequestBody $body have no MediaType of 'application/json', 'application/x-www-form-urlencoded', 'multipart/form-data' or '*/*'")
      // https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#mediaTypeObject
      // following rule is also valid in Swagger UI using this json (object foo)
      //: https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/api-with-examples.json
      // if schema is not null, then it has the 1st priority
      // if schema is null, 2rd priority is examples.
      // if schema is null and examples is null, 3rd priority is example field.
      if (mediaType.get.getSchema != null)
        getExample(openAPI, mediaType.get.getSchema)
      else if (body!=null
        && body.getContent != null
        && body.getContent.values().size()>0
        && body.getContent.values().asScala.head.getExamples != null
        && body.getContent.values().asScala.head.getExamples.values().size() > 0
      ) {
        val examplesValue = body.getContent.values().asScala.map(_.getExamples.values().asScala.map(_.getValue.toString)).map(_.head)
        convertToProduct(json.parse(examplesValue.head))
      } else if(body!=null
        && body.getContent != null
        && body.getContent.values().size()>0
        && body.getContent.values().asScala.head.getExample != null
      ) {
        val exampleValue = body.getContent.values().asScala.map(_.getExample.toString)
        convertToProduct(json.parse(exampleValue.head))
      }else
        EmptyBody
    }
  }

  private def getMediaType(content: Content) = content match {
    case null => None
    case v if v.containsKey("application/json") => Some(v.get("application/json"))
    case v if v.containsKey("*/*") => Some(v.get("*/*"))
    case v if v.containsKey("application/x-www-form-urlencoded") => Some(v.get("application/x-www-form-urlencoded"))
    case v if v.containsKey("multipart/form-data") => Some(v.get("multipart/form-data"))
    case _ => None
  }

  // get response example: success code -> success response body
  private def getResponseExample(openAPI: OpenAPI, apiResponses: ApiResponses): (Int, Product) = {
    val emptySuccess = 200 -> EmptyBody

    if(apiResponses == null || apiResponses.isEmpty) {
      return emptySuccess
    }

    val successResponse: Option[(Int, ApiResponse)] =
      apiResponses.asScala.toList.sortBy(_._1) collectFirst {
      case (code, apiResponse) if code.startsWith("20") => code.toInt -> apiResponse
      case (code, apiResponse) if code == "default" => 200 -> apiResponse
    }

    val result: Option[(Int, Product)] = for {
     (code, response) <- successResponse
     schema <- getResponseSchema(openAPI, response)
      // https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#mediaTypeObject
      // following rule is also valid in Swagger UI using this json (object foo)
      //: https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/api-with-examples.json
      // if schema is not null, then it has the 1st priority
      // if schema is null, 2rd priority is examples.
      // if schema is null and examples is null, 3rd priority is example field.
     example = if (schema != null)
       getExample(openAPI, schema)
     else if (response!=null 
       && response.getContent != null 
       && response.getContent.values().size()>0
       && response.getContent.values().asScala.head.getExamples != null
       && response.getContent.values().asScala.head.getExamples.values().size() > 0 
     ) {
       val examplesValue = response.getContent.values().asScala.map(_.getExamples.values().asScala.map(_.getValue.toString)).map(_.head)
       convertToProduct(json.parse(examplesValue.head))
     } else if(response!=null 
       && response.getContent != null 
       && response.getContent.values().size()>0 
       && response.getContent.values().asScala.head.getExample != null
     ) {
       val exampleValue = response.getContent.values().asScala.map(_.getExample.toString)
       convertToProduct(json.parse(exampleValue.head))
     }
     else
       EmptyBody
    } yield code -> example

    result
      .orElse(
          successResponse.collect { //if only exists default ApiResponse, use description as example
            case (code, apiResponse) if StringUtils.isNotBlank(apiResponse.getDescription) =>
              code -> StringBody(apiResponse.getDescription)
          }
       )
      .getOrElse(emptySuccess)
  }

  private def getResponseSchema(openAPI: OpenAPI, apiResponse: ApiResponse): Option[Schema[_]] = {
    val ref = apiResponse.get$ref()
    if(StringUtils.isNotBlank(ref)) {
      Option(getRefSchema(openAPI, ref))
    } else {
      val mediaType = getMediaType(apiResponse.getContent)
      mediaType.map(_.getSchema)
    }
  }

  private val RegexDefinitions = """(?:#/components/schemas(?:/#definitions)?/)(.+)""".r
  private val RegexResponse = """#/responses/(.+)""".r

  private def getRefSchema(openAPI: OpenAPI, ref: String): Schema[_] = ref match{
    case RegexResponse(refName) =>
      val response: ApiResponse = openAPI.getComponents.getResponses.get(refName)
      getResponseSchema(openAPI, response).orNull

    case RegexDefinitions(refName) =>
      val schema: Schema[_] = openAPI.getComponents.getSchemas.get(refName)
      schema
    case _ => null
  }

  private def getExample(openAPI: OpenAPI, schema: Schema[_]): Product = {
    implicit val formats = CustomJsonFormats.formats

    val example: Any = getExampleBySchema(openAPI, schema)
    convertToProduct(example)
  }

  private def convertToProduct(example: Any): Product = example match {
    //In Swagger UI, if the schema reference is not existing, it just return a `string` instead
    case null => StringBody("string") 
    case v: String => StringBody(v)
    case v: Boolean => BooleanBody(v)
    case v: Int => IntBody(v)
    case v: Long => LongBody(v)
    case v: BigInt => BigIntBody(v)
    case v: Float => FloatBody(v)
    case v: Double => DoubleBody(v)
    case v: BigDecimal => BigDecimalBody(v)
    case v: JArray => JArrayBody(v)
    case v: JObject => v
    case v :scala.Product => v
    case v => json.Extraction.decompose(v) match {
      case o: JObject => o
      case JArray(arr) => arr
      case _ => throw new RuntimeException(s"Not supporting example type: $v, ${v.getClass}")
    }
  }

  private def getExampleBySchema(openAPI: OpenAPI, schema: Schema[_]):Any = {

    def getDefaultValue[T](schema: Schema[_<:T], t: => T): T = Option(schema.getExample.asInstanceOf[T])
      .orElse(Option(schema.getDefault))
      .orElse{
        schema.getEnum() match {
          case null => None
          case l if l.isEmpty => None
          case l => Option(l.get(0))
        }
      }
      .getOrElse(t)

    val schemas:ListBuffer[Schema[_]] = ListBuffer()
    def rec(schema: Schema[_]): Any = {
      if(schema.isInstanceOf[ObjectSchema]) {
        schemas += schema
      }
      // check whether this schema already recurse two times
      if(schemas.count(schema ==) > 3) {
        return JObject(Nil)
      }

      schema match {

        case null => null
        case v: BooleanSchema => getDefaultValue(v, true)
        case v if v.getType() =="boolean" => true
        case v: DateSchema => getDefaultValue(v, {
          APIUtil.DateWithDayFormat.format(new Date())
        })
        case v if v.getFormat() == "date" => getDefaultValue(v, {
          APIUtil.DateWithDayFormat.format(new Date())
        })
        case v: DateTimeSchema => getDefaultValue(v, {
          APIUtil.DateWithSecondsFormat.format(new Date())
        })
        case v if v.getFormat() == "date-time" => getDefaultValue(v, {
          APIUtil.DateWithSecondsFormat.format(new Date())
        })
        case v: IntegerSchema => getDefaultValue(v, 1)
        case v if v.getFormat() == "int32" => 1
        case v: NumberSchema => getDefaultValue(v, 1.2)
        case v if v.getType() == "number" => 1.2
        case v: StringSchema => getDefaultValue(v, "string")
        case v: UUIDSchema => getDefaultValue(v, UUID.randomUUID())
        case v if v.getFormat() == "uuid" =>  UUID.randomUUID()
        case v: EmailSchema => getDefaultValue(v, "example@tesobe.com")
        case v if v.getFormat() == "email" => "example@tesobe.com"
        case v: FileSchema => getDefaultValue(v, "file_example.txt")
        case v if v.getFormat() == "binary" =>  "file_example.txt"
        case v: PasswordSchema => getDefaultValue(v, "very_complex_password_I_promise_!!")
        case v if v.getFormat() == "password" => "very_complex_password_I_promise_!!"
        case v: ArraySchema =>
          getDefaultValue(v, {
            rec(v.getItems) match {
              case v: JValue => JArray(v::Nil)
              case v => json.Extraction.decompose(Array(v))
            }
          })
        case v: MapSchema => 
          //additionalProperties filed: 
//        eg1:
//          "parameters": {
//            "type": "object",
//            "additionalProperties": {
//            "$ref": "#/components/schemas/ParameterModel"
//          }
//          
//        eg2:
//          "a1":{
//            "type": "object",
//            "additionalProperties":{
//              "type": "string"
//              }
//            },
//        eg3:
//          "a2":{
//            "type": "object",
//            "additionalProperties":{
//            "type": "integer"
//          }
//          },
//        eg4:
//          "a3":{
//            "type": "object",
//            "additionalProperties":{
//            "type": "array",
//            "items": {
//              "type": "string"
//            }
//            }
//          },
          if (v.getType =="object" && v.getAdditionalProperties() != null && v.getAdditionalProperties().isInstanceOf[Schema[_]]) {
            val value = v.getAdditionalProperties().asInstanceOf[Schema[_]]
            val valueExample = rec(value)
            getDefaultValue(v, Map("additionalProp1"-> valueExample, "additionalProp2" -> valueExample, "additionalProp3" -> valueExample))
          }
          else{
            getDefaultValue(v, Map("additionalProp1"-> "string", "additionalProp2" -> "string", "additionalProp3" -> "string"))
          }
        //The swagger object schema may not contain any properties: eg:
        // "Account": {
        //   "title": "accountTransactibility",
        //   "type": "object"
        // }
        case v if v.isInstanceOf[ObjectSchema] && CommonUtil.Map.isEmpty(v.getProperties()) =>
          EmptyBody

        case v if v.isInstanceOf[ObjectSchema] || CommonUtil.Map.isNotEmpty(v.getProperties()) =>
          val properties: util.Map[String, Schema[_]] = v.getProperties

          val jFields: mutable.Iterable[JField] = properties.asScala.map { kv =>
            val (name, value) = kv
            val valueExample = rec(value)
            JField(name, json.Extraction.decompose(valueExample))
          }
          JObject(jFields.toList)

        case v: Schema[_] if StringUtils.isNotBlank(v.get$ref()) =>
          val refSchema = getRefSchema(openAPI, v.get$ref())
          convertToProduct(rec(refSchema))
        
        //For OpenAPI30, have some default object, which do not have any ref.  
        case v: Schema[_] if StringUtils.isNotBlank(v.getDescription) =>
          getDefaultValue(v, v.getDescription)

        //https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/petstore-expanded.json
        //added this case according to the up swagger, it has `allOf` 
        case v: ComposedSchema  =>{
          if (v.getAllOf != null && v.getAllOf.size() >0)  {
            v.getAllOf.asScala.map(rec(_))
              .filter(_.!=(null))
              .filter(_.isInstanceOf[JObject])
              .map(_.asInstanceOf[JObject])
              .reduceLeft(_ merge _)
          } else if (v.getAnyOf != null && v.getAnyOf.size()>0){
            rec(v.getAllOf.asScala.head)
          }else if(v.getOneOf != null && v.getOneOf.size()>0){
            rec(v.getOneOf.asScala.head)
          }else{ 
            EmptyBody
          }
        }
        case v if v.getType() == "string" => "string"
        case _ => throw new RuntimeException(s"Not support type $schema, please support it if necessary.")
      }
    }
    rec(schema)
  }


  def prepareMappingFields (originalJson: String): JValue = {
    val jValue: json.JValue = json.parse(originalJson)
    prepareMappingFields(jValue)
  }

  /**
   *  This function will replace the object to a pure field value, to prepare the parameters for @buildJson method 
   *  Please also see the scala test.
   * "id": {                    
   *        "entity": "PetEntity",      
   *        "field": "field1",          
   *        "query": "field1"        
   *      }                             
   *      -->                           
   *      "id": "field1"                
   *
   * @param originalJson
   * @return
   */
  def prepareMappingFields (originalJson: JValue): JValue = {
    // 1st: remove all the entity, query 
    //    "id": {
    //      "entity": "PetEntity",
    //      "field": "field1",
    //      "query": "field1"
    //    }
    //    -->
    //    "id": {
    //      "field": "field1",
    //    }
    val JvalueRemoved = originalJson.removeField{
      case JField(n, _) => n =="entity"
    }.removeField{
      case JField(n, _) => n =="query"
    }
    //2rd:  {
    //    //      "field": "field1",
    //    //    } -->
    //    "field1"
    val JvalueReplaced = JvalueRemoved transform {
      case JObject(List(JField("entity",JNothing), JField("field",v), JField("query",JNothing)))=> v
    }
    JvalueReplaced
  }


  /**
   * get all the dynamic entities from the json, please check the test
   */
  def getAllEntitiesFromMappingJson (originalJson: String): List[String] = {
    val jValue: json.JValue = json.parse(originalJson)
    getAllEntityFromMapping(jValue)
  }

  /**
   * get all the dynamic entities from the json, please check the test
   */
  def getAllEntityFromMapping (originalJson: JValue): List[String] = {
    //    {
    //      "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
    //      "request_mapping": {},
    //      "response_mapping": {
    //        "id": {
    //        "entity": "PetEntity",
    //        "field": "field1",
    //        "query": "field1"
    //      }
    //      }
    //    }
    val entityList = for {
      JObject(child) <- originalJson
      JField("entity", JString(name)) <- child
    } yield (name)

    //Remove the duplicated items
    entityList.toSet.toList
  }

  def getEntityQueryIdsFromMapping (originalJson: String): List[String] = {
    val jValue: json.JValue = json.parse(originalJson)
    getEntityQueryIdsFromMapping(jValue)
  }

  /**
   * get all the dynamic entities from the json, please check the test
   */
  def getEntityQueryIdsFromMapping (originalJson: JValue): List[String] = {
    //    {
    //      "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
    //      "request_mapping": {},
    //      "response_mapping": {
    //        "id": {
    //        "entity": "PetEntity",
    //        "field": "field1",
    //        "query": "field1"
    //      }
    //      }
    //    }
    val entityList = for {
      JObject(child) <- originalJson
      JField("query", JString(name)) <- child
    } yield (name)

    //Remove the duplicated items
    entityList.toSet.toList
  }


  /**
   * we can query the JArray by the (key, value) pair
   */
  def getObjectByKeyValuePair (jsonArray: JArray, key:String, value:String) = {
    val result: JValue =  jsonArray.arr
      .find(
        jObject => {
          val jFieldOption = jObject.findField {
            case JField(n, v) => n == key && v.values.toString == value
          }
          jFieldOption.isDefined
        }
      )
    
    if(result == JNothing)
      throw new RuntimeException(s"$DynamicDataNotFound, current query is (key=$key,value=$value)")
    else
      result
  }


  /**
   * get the query key and value form URL, and convert to the dynamic entity ones.
   * 
   *  eg: URL is /obp/v4.0.0/dynamic/pet/findByStatus?status=available
   *  and mapping is: 
   *  {
   *     "operation_id": "OBPv4.0.0-dynamicEndpoint_GET_pet_PET_ID",
   *     "request_mapping": {},
   *     "response_mapping": {
   *       "status": {
   *         "entity": "PetEntity",
   *         "field": "field8",
   *         "query": "field1"
   *       }
   *     }
   *   }
   *   --> we can get the key-pair(field8-available) to query the dynamicEntity.
   * 
   */
  def convertToMappingQueryParams (mapping: JValue, params:Map[String, List[String]]) = {
    if(params.isEmpty) None
    else if (params.size > 1) {
      throw new RuntimeException(s"$InvalidUrlParameters only support one set at the moment.");
    } else {
      //1st: find the `status` field in mapping: it should return:
  //    {
  //       "entity": "PetEntity",
  //       "field": "field8",
  //       "query": "field1"
  //     }
      val queryField: Option[JField] = mapping findField {case JField(n, _) => n.contains(params.head._1)}
  
      //return: "field8", note: `field` is the obp structure, so we can hardcode it here.
      val fieldValueOption = queryField.map(_.value \ "field")
      
      //return Map(field8 -> List(available))
      fieldValueOption.map(fieldValue => Map(fieldValue.values.toString -> params.head._2))
    }
  }

  def convertToMappingQueryParams (mapping: String, params:Map[String, List[String]]): Option[Map[String, List[String]]] = {
    if(params.isEmpty) None else {
      val jValue: json.JValue = json.parse(mapping)
      convertToMappingQueryParams(jValue, params)
    }
  }

  /**
   * we can query the JArray by parameters
   */
  def getObjectsByParams (jsonArray: JArray, params:Option[Map[String, List[String]]]) = {
    if (params.isEmpty) {
      jsonArray
    } else if (params.isDefined && params.get.size > 1){
      throw new RuntimeException(s"$InvalidUrlParameters only support one set at the moment.");
    } else if (params.isDefined && params.get.size == 1){
      val key = params.get.head._1
      val value = params.get.head._2.head
      val result: List[JValue] =  jsonArray.arr
        .filter(
          jObject => {
            val jFieldOption = jObject.findField {
              case JField(n, v) => n == key && v.values.toString == value
            }
            jFieldOption.isDefined
          }
        )
      JArray(result)
    } else {
      jsonArray
    }
  }


  /**
   * We can get the (entityName, entityIdKey, entityIdValueFromUrl) by the parameters. Better see the scala test.
   * @param mappingJson it should be a valid endpoint mapping, it can be requestMapping or responseMapping
   * @param pathParams the url parameters: eg: Map("petId"-> "1")
   * @return eg: we can get: ("PetEntity","field1",Some("1"))  
   * 
   */
  //TODO Here need to be refactor later, only support one Entity and one Id here, and it may throw exceptions.
  def getEntityNameKeyAndValue (mappingJson: String, pathParams:Map[String, String]): (String, String, Option[String]) = {
    // we can get the entity name, eg:PetEntity
    val entityName = DynamicEndpointHelper.getAllEntitiesFromMappingJson(mappingJson).head
    // it will get the entity field, eg: field1
    val entityIdKey = DynamicEndpointHelper.getEntityQueryIdsFromMapping(mappingJson).head
    // it will get the id value from the url, eg: 1
    val entityIdValueFromUrl = pathParams.find(parameter => parameter._1.toLowerCase.contains("id")).map(_._2)
    //(PetEntity, field1, 1) we can query all the PetEntity data
    (entityName, entityIdKey, entityIdValueFromUrl)
  }

  def findDynamicData(dynamicDataList: List[DynamicDataT], dynamicDataJson: JValue) = {
    val dynamicDataOption = dynamicDataList.find(dynamicData=>json.parse(dynamicData.dataJson) == dynamicDataJson)
    dynamicDataOption.map(dynamicData =>(dynamicData.dynamicEntityName,dynamicData.dynamicDataId.getOrElse(""))).getOrElse("","")
  }

  /**
   * we delete dynamic data by the (key, value) pair
   */
  def deleteObjectByKeyValuePair (dynamicDataList: List[DynamicDataT], jsonArray: JArray, key:String, value:String): JValue = {
    val dynamicDataJson = getObjectByKeyValuePair(jsonArray: JArray, key:String, value:String)
    val (dynamicEntityName, dynamicDateId) = findDynamicData(dynamicDataList, dynamicDataJson)
    JBool(DynamicDataProvider.connectorMethodProvider.vend.delete(None, dynamicEntityName, dynamicDateId, None, false).getOrElse(false))
  }
  
  def addedBankToPath(swagger: String, bankId: Option[String]): JValue = {
    val jvalue = json.parse(swagger)
    addedBankToPath(jvalue, bankId)
  }

  // If it is bank is defined, we will add the bank into the path, better check the scala tests 
  //  eg: /fashion-brand-list  --> /banks/gh.29.uk/fashion-brand-list
  def addedBankToPath(swagger: JValue, bankId: Option[String]): JValue = {
    if(bankId.isDefined){
      swagger transformField {
        case JField(name, JObject(obj)) =>
          if (name.startsWith("/")) 
            JField(s"/banks/${bankId.get}$name", JObject(obj))
          else 
            JField(name, JObject(obj))
      }
    } else{
      swagger
    }
  }

}

/**
 * one endpoint information defined in swagger file.
 * @param path path defined in swagger file
 * @param successCode success response code
 * @param resourceDoc ResourceDoc built with one endpoint information defined in swagger file
 */
case class DynamicEndpointItem(path: String, successCode: Int, resourceDoc: ResourceDoc)

/**
 *
 * @param id DynamicEntity id value
 * @param dynamicEndpointItems ResourceDoc to url that defined in swagger content
 * @param serverUrl base url that defined in swagger content
 */
case class DynamicEndpointInfo(id: String, dynamicEndpointItems: mutable.Iterable[DynamicEndpointItem], serverUrl: String, bankId:Option[String]) {
  val resourceDocs: mutable.Iterable[ResourceDoc] = dynamicEndpointItems.map(_.resourceDoc)

  private val existsUrlToMethod: mutable.Iterable[(HttpMethod, String, Int, ResourceDoc)] =
    dynamicEndpointItems
    .map(it => {
      val DynamicEndpointItem(path, code, doc) = it
      (HttpMethod.valueOf(doc.requestVerb), path, code, doc)
    })

  // return (serverUrl, endpointUrl, successCode, resourceDoc)
  def findDynamicEndpoint(newMethod: HttpMethod, newUrl: String): Option[(String, String, Int, ResourceDoc, Option[String])] =
    existsUrlToMethod collectFirst {
    case (method, url, code, doc) if isSameUrl(newUrl, url) && newMethod == method =>
      (this.serverUrl, url, code, doc, this.bankId)
  }

  def existsEndpoint(newMethod: HttpMethod, newUrl: String): Boolean = findDynamicEndpoint(newMethod, newUrl).isDefined

  def targetUrl(url: String): String = s"""$serverUrl$url"""

  /**
   * check whether two url is the same:
   *  isSameUrl("/abc/efg", "/abc/efg") == true
   *  isSameUrl("/abc/efg", "/abc/{id}") == true
   *  isSameUrl("/abc/{userId}", "/abc/{id}") == true
   *  isSameUrl("/abc/{userId}/", "/abc/{id}") == true
   *  isSameUrl("/def/abc/", "/abc/{id}") == false
   * @param pathX
   * @param pathY
   * @return
   */
  private def isSameUrl(pathX: String, pathY: String) = {
    val splitPathX = StringUtils.split(pathX, '/')
    val splitPathY = StringUtils.split(pathY, '/')
    if(splitPathX.size != splitPathY.size) {
      false
    } else {
      splitPathX.zip(splitPathY).forall {kv =>
        val (partX, partY) = kv
        partX == partY ||
          (partX.startsWith("{") && partX.endsWith("}")) ||
          (partY.startsWith("{") && partY.endsWith("}"))
      }
    }
  }

}

/**
 * if the endpoint domain is obp_mock, then pass success code and example response to connector
 */
object MockResponseHolder {
  private val _codeAndBody = new ThreadGlobal[(Int, JValue)]

  def init[B](codeAndBody: Option[(Int, JValue)])(f: => B): B = {
    _codeAndBody.doWith(codeAndBody.orNull) {
      f
    }
  }

  def mockResponse: Box[(Int, JValue)] = _codeAndBody.box
}
package code.api.v4_0_0.dynamic

import akka.http.scaladsl.model.{HttpMethods, HttpMethod => AkkaHttpMethod}
import code.DynamicEndpoint.{DynamicEndpointProvider, DynamicEndpointT}
import code.api.util.APIUtil.{BigDecimalBody, BigIntBody, BooleanBody, DoubleBody, EmptyBody, FloatBody, IntBody, JArrayBody, LongBody, PrimaryDataBody, ResourceDoc, StringBody}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{UnknownError, UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{APIUtil, ApiRole, ApiTag, CustomJsonFormats}
import com.openbankproject.commons.util.ApiVersion
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
import net.liftweb.json.JsonAST.{JArray, JField, JNothing, JObject}
import net.liftweb.json.{Formats, JValue}
import net.liftweb.util.{StringHelpers, ThreadGlobal}
import org.apache.commons.collections4.MapUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils

import java.io.File
import java.nio.charset.Charset
import java.util
import java.util.regex.Pattern
import java.util.{Date, UUID}
import scala.collection.JavaConverters._
import scala.collection.immutable.List
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


object DynamicEndpointHelper extends RestHelper {
  protected override implicit def formats: Formats = CustomJsonFormats.formats

  /**
   * dynamic endpoints url prefix
   */
  val urlPrefix = APIUtil.getPropsValue("dynamic_endpoints_url_prefix", "dynamic")
  private val implementedInApiVersion = ApiVersion.v4_0_0

  private def dynamicEndpointInfos: List[DynamicEndpointInfo] = {
    val dynamicEndpoints: List[DynamicEndpointT] = DynamicEndpointProvider.connectorMethodProvider.vend.getAll()
    val infos = dynamicEndpoints.map(it => buildDynamicEndpointInfo(it.swaggerString, it.dynamicEndpointId.get))
    infos
  }

  def allDynamicEndpointRoles: List[ApiRole] = {
    for {
      dynamicEndpoint <- DynamicEndpointProvider.connectorMethodProvider.vend.getAll()
      info = buildDynamicEndpointInfo(dynamicEndpoint.swaggerString, dynamicEndpoint.dynamicEndpointId.get)
      role <- getRoles(info)
    } yield role
  }

  def getRoles(dynamicEndpointId: String): List[ApiRole] = {
    val foundInfos: Box[DynamicEndpointInfo] = DynamicEndpointProvider.connectorMethodProvider.vend.get(dynamicEndpointId)
      .map(dynamicEndpoint => buildDynamicEndpointInfo(dynamicEndpoint.swaggerString, dynamicEndpoint.dynamicEndpointId.get))


    val roles: List[ApiRole] = foundInfos match {
      case Full(x) => getRoles(x)
      case _ => Nil
    }

    roles
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
    private val IsMockUrl = """https?://obp_mock(?::\d+)?/.*""".r
    /**
     * unapply Request to (request url, json, http method, request parameters, path parameters, role)
     * request url is  current request target url to remote server
     * json is request body
     * http method is request http method
     * request parameters is http request parameters
     * path parameters: /banks/{bankId}/users/{userId} bankId and userId corresponding key to value
     * role is current endpoint required entitlement
     * @param r HttpRequest
     * @return (adapterUrl, requestBodyJson, httpMethod, requestParams, pathParams, role, operationId, mockResponseCode->mockResponseBody)
     */
    def unapply(r: Req): Option[(String, JValue, AkkaHttpMethod, Map[String, List[String]], Map[String, String], ApiRole, String, Option[(Int, JValue)])] = {
      val partPath = r.path.partPath
      if (!testResponse_?(r) || partPath.headOption != Option(urlPrefix))
        None
      else {
        val akkaHttpMethod = HttpMethods.getForKeyCaseInsensitive(r.requestType.method).get
        val httpMethod = HttpMethod.valueOf(r.requestType.method)
        // url that match original swagger endpoint.
        val url = partPath.tail.mkString("/", "/", "")
        val foundDynamicEndpoint: Option[(String, String, Int, ResourceDoc)] = dynamicEndpointInfos
          .map(_.findDynamicEndpoint(httpMethod, url))
          .collectFirst {
            case Some(x) => x
          }

        foundDynamicEndpoint
          .flatMap { it =>
            val (serverUrl, endpointUrl, code, doc) = it

            val pathParams: Map[String, String] = if(endpointUrl == url) {
              Map.empty[String, String]
            } else {
              val tuples: Array[(String, String)] = StringUtils.split(endpointUrl, "/").zip(partPath.tail)
              tuples.collect {
                case (ExpressionRegx(name), value) => name->value
              }.toMap
            }

            val mockResponse: Option[(Int, JValue)] = (serverUrl, doc.successResponseBody) match {
              case (IsMockUrl(), v: PrimaryDataBody[_]) =>
                Some(code -> v.toJValue)

              case (IsMockUrl(), v: JValue) =>
                Some(code -> v)

              case (IsMockUrl(), v) =>
                Some(code -> json.Extraction.decompose(v))

              case _ => None
            }

            val Some(role::_) = doc.roles
            body(r).toOption
              .orElse(Some(JNothing))
              .map(zson => (s"""$serverUrl$url""", zson, akkaHttpMethod, r.params, pathParams, role, doc.operationId, mockResponse))
          }

      }
    }
  }


  def findExistsEndpoints(openAPI: OpenAPI): List[(HttpMethod, String)] = {
     for {
      (path, pathItem) <- openAPI.getPaths.asScala.toList
      (method: HttpMethod, _) <- pathItem.readOperationsMap.asScala
      if dynamicEndpointInfos.exists(_.existsEndpoint(method, path))
    } yield (method, path)

  }

  private val dynamicEndpointInfoMemo = new Memo[String, DynamicEndpointInfo]

  private def buildDynamicEndpointInfo(content: String, id: String): DynamicEndpointInfo =
    dynamicEndpointInfoMemo.memoize(content) {
      val openAPI: OpenAPI = parseSwaggerContent(content)
      buildDynamicEndpointInfo(openAPI, id)
    }

  private def buildDynamicEndpointInfo(openAPI: OpenAPI, id: String): DynamicEndpointInfo = {
    val tags: List[ResourceDocTag] = List(ApiTag(openAPI.getInfo.getTitle), apiTagApi, apiTagNewStyle, apiTagDynamicEntity, apiTagDynamic)

    val serverUrl = {
      val servers = openAPI.getServers
      assert(!servers.isEmpty, s"swagger host is mandatory, but current swagger host is empty, id=$id")
      servers.get(0).getUrl
    }

    val paths: mutable.Map[String, PathItem] = openAPI.getPaths.asScala
    def entitlementSuffix(path: String) = Math.abs(path.hashCode).toString.substring(0, 3) // to avoid different swagger have same entitlement
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
        val roleName = if(StringUtils.isNotBlank(op.getOperationId)) {
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

        Some(List(
          ApiRole.getOrCreateDynamicApiRole(roleName)
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
        roles
      )
      DynamicEndpointItem(path, successCode, doc)
    }

    DynamicEndpointInfo(id, dynamicEndpointItems, serverUrl)
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
      val mediaType = getMediaType(body.getContent())
      assert(mediaType.isDefined, s"RequestBody $body have no MediaType of 'application/json', 'application/x-www-form-urlencoded', 'multipart/form-data' or '*/*'")
      val schema = mediaType.get.getSchema
      getExample(openAPI, schema)
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
     example = getExample(openAPI, schema)
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

    example match {
      case null => EmptyBody
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
  }

  private def getExampleBySchema(openAPI: OpenAPI, schema: Schema[_]):Any = {
    def getDefaultValue[T](schema: Schema[_<:T], t: => T): T = Option(schema.getExample.asInstanceOf[T])
      .orElse(Option(schema.getDefault))
      .orElse{
        Option(schema.getEnum())
          .filterNot(_.isEmpty)
          .map(_.get(0))
      }
      .getOrElse(t)

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
          val itemsSchema: Schema[_] = v.getItems
          val singleItemExample = getExampleBySchema(openAPI, itemsSchema)
          singleItemExample match {
            case v: JValue => JArray(v::Nil)
            case v => json.Extraction.decompose(Array(v))
          }
        })
      case v: MapSchema => getDefaultValue(v, Map("name"-> "John", "age" -> 12))

      case v if v.isInstanceOf[ObjectSchema] || MapUtils.isNotEmpty(v.getProperties()) =>
        val properties: util.Map[String, Schema[_]] = v.getProperties

        val jFields: mutable.Iterable[JField] = properties.asScala.map { kv =>
          val (name, value) = kv
          val valueExample = getExampleBySchema(openAPI, value)
          JField(name, json.Extraction.decompose(valueExample))
        }
        JObject(jFields.toList)

      case v: Schema[_] if StringUtils.isNotBlank(v.get$ref()) =>
        val refSchema = getRefSchema(openAPI, v.get$ref())

        getExample(openAPI, refSchema)

      case v if v.getType() == "string" => "string"
      case _ => throw new RuntimeException(s"Not support type $schema, please support it if necessary.")
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
case class DynamicEndpointInfo(id: String, dynamicEndpointItems: mutable.Iterable[DynamicEndpointItem], serverUrl: String) {
  val resourceDocs: mutable.Iterable[ResourceDoc] = dynamicEndpointItems.map(_.resourceDoc)

  private val existsUrlToMethod: mutable.Iterable[(HttpMethod, String, Int, ResourceDoc)] =
    dynamicEndpointItems
    .map(it => {
      val DynamicEndpointItem(path, code, doc) = it
      (HttpMethod.valueOf(doc.requestVerb), path, code, doc)
    })

  // return (serverUrl, endpointUrl, successCode, resourceDoc)
  def findDynamicEndpoint(newMethod: HttpMethod, newUrl: String): Option[(String, String, Int, ResourceDoc)] =
    existsUrlToMethod collectFirst {
    case (method, url, code, doc) if isSameUrl(newUrl, url) && newMethod == method =>
      (this.serverUrl, url, code, doc)
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
package code.api.v4_0_0

import java.io.File
import java.nio.charset.Charset
import java.util
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern
import java.util.{Date, Optional}

import akka.http.scaladsl.model.HttpMethods
import code.DynamicEndpoint.{DynamicEndpointProvider, DynamicEndpointT}
import code.api.util.APIUtil.{Catalogs, OBPEndpoint, ResourceDoc, authenticationRequiredMessage, emptyObjectJson, generateUUID, notCore, notOBWG, notPSD2}
import code.api.util.ApiTag.{ResourceDocTag, apiTagApi, apiTagNewStyle}
import code.api.util.ErrorMessages.{InvalidJsonFormat, UnknownError, UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{APIUtil, ApiRole, ApiTag, CustomJsonFormats, NewStyle}
import code.api.util.ApiRole.getOrCreateDynamicApiRole
import com.openbankproject.commons.model.enums.DynamicEntityFieldType
import com.openbankproject.commons.util.{ApiVersion, Functions}
import io.swagger.v3.oas.models.{OpenAPI, Operation, PathItem}
import io.swagger.v3.oas.models.PathItem.HttpMethod
import akka.http.scaladsl.model.{HttpMethod => AkkaHttpMethod}
import io.swagger.v3.oas.models.media.{ArraySchema, BooleanSchema, Content, DateSchema, DateTimeSchema, IntegerSchema, NumberSchema, ObjectSchema, Schema, StringSchema}
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.parser.OpenAPIV3Parser
import net.liftweb.http.Req
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JsonAST.{JArray, JField, JNothing, JObject}
import net.liftweb.json.JsonDSL._
import net.liftweb.json
import net.liftweb.json.JValue
import net.liftweb.util.StringHelpers
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._


object DynamicEndpointHelper extends RestHelper {

  /**
   * dynamic endpoints url prefix
   */
  val urlPrefix = APIUtil.getPropsValue("dynamic_endpoints_url_prefix", "dynamic")

  private lazy val dynamicEndpointInfos: CopyOnWriteArrayList[DynamicEndpointInfo] = {
    val dynamicEndpoints: List[DynamicEndpointT] = DynamicEndpointProvider.connectorMethodProvider.vend.getAll()
    val infos = dynamicEndpoints.map(it => swaggerToResourceDocs(it.swaggerString, it.dynamicEndpointId.get))
    new CopyOnWriteArrayList(infos.asJava)
  }

  def getRoles(dynamicEndpointId: String): List[ApiRole] = {
    val foundInfos: Option[DynamicEndpointInfo] = dynamicEndpointInfos.asScala
      .find(_.id == dynamicEndpointId)

    val roles = foundInfos.toList
      .flatMap(_.resourceDocs)
      .map(_.roles)
      .collect {
        case Some(role :: _) => role
      }

    roles
  }
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
     * request parameters is http request parameters
     * path parameters: /banks/{bankId}/users/{userId} bankId and userId corresponding key to value
     * role is current endpoint required entitlement
     * @param r HttpRequest
     * @return
     */
    def unapply(r: Req): Option[(String, JValue, AkkaHttpMethod, Map[String, List[String]], Map[String, String], ApiRole)] = {
      val partPath = r.path.partPath
      if (!testResponse_?(r) || partPath.headOption != Option(urlPrefix))
        None
      else {
        val akkaHttpMethod = HttpMethods.getForKeyCaseInsensitive(r.requestType.method).get
        val httpMethod = HttpMethod.valueOf(r.requestType.method)
        // url that match original swagger endpoint.
        val url = partPath.tail.mkString("/", "/", "")
        val foundDynamicEndpoint: Optional[(DynamicEndpointInfo, ResourceDoc, String)] = dynamicEndpointInfos.stream()
          .map[Option[(DynamicEndpointInfo, ResourceDoc, String)]](_.findDynamicEndpoint(httpMethod, url))
          .filter(_.isDefined)
          .findFirst()
          .map(_.get)

        foundDynamicEndpoint.asScala
          .flatMap[(String, JValue, AkkaHttpMethod, Map[String, List[String]], Map[String, String], ApiRole)] { it =>
            val (dynamicEndpointInfo, doc, originalUrl) = it

            val pathParams: Map[String, String] = if(originalUrl == url) {
              Map.empty[String, String]
            } else {
              val tuples: Array[(String, String)] = StringUtils.split(originalUrl, "/").zip(partPath.tail)
              tuples.collect {
                case (ExpressionRegx(name), value) => name->value
              }.toMap
            }

            val Some(role::_) = doc.roles
            body(r).toOption
              .orElse(Some(JNothing))
              .map(t => (dynamicEndpointInfo.targetUrl(url), t, akkaHttpMethod, r.params, pathParams, role))
          }

      }
    }
  }

  def addEndpoint(openAPI: OpenAPI, id: String): Boolean = {
    val endpointInfo = swaggerToResourceDocs(openAPI, id)
    dynamicEndpointInfos.add(endpointInfo)
  }

  def removeEndpoint(id: String): Boolean = {
    dynamicEndpointInfos.asScala.find(_.id == id) match {
      case Some(v) => dynamicEndpointInfos.remove(v)
      case _ => false
    }
  }

  def findExistsEndpoints(openAPI: OpenAPI): List[(HttpMethod, String)] = {
     for {
      (path, pathItem) <- openAPI.getPaths.asScala.toList
      (method: HttpMethod, _) <- pathItem.readOperationsMap.asScala
      if dynamicEndpointInfos.stream().anyMatch(_.existsEndpoint(method, path))
    } yield (method, path)

  }

  private def swaggerToResourceDocs(content: String, id: String): DynamicEndpointInfo = {
    val openAPI: OpenAPI = parseSwaggerContent(content)
    swaggerToResourceDocs(openAPI, id)
  }

  private def swaggerToResourceDocs(openAPI: OpenAPI, id: String): DynamicEndpointInfo = {
    val tags: List[ResourceDocTag] = List(ApiTag.apiTagDynamicEndpoint, apiTagApi, apiTagNewStyle)

    val serverUrl = {
      val servers = openAPI.getServers
      assert(!servers.isEmpty, s"swagger host is mandatory, but current swagger host is empty, id=$id")
      servers.get(0).getUrl
    }

    val paths: mutable.Map[String, PathItem] = openAPI.getPaths.asScala
    def entitlementSuffix(path: String) = Math.abs(path.hashCode).toString.substring(0, 3) // to avoid different swagger have same entitlement
    val docs: mutable.Iterable[(ResourceDoc, String)] = for {
      (path, pathItem) <- paths
      (method: HttpMethod, op: Operation) <- pathItem.readOperationsMap.asScala
    } yield {
      val implementedInApiVersion = ApiVersion.v4_0_0
      val partialFunction: OBPEndpoint = APIMethods400.Implementations4_0_0.genericEndpoint // this function is just placeholder, not need a real value.
      val partialFunctionName: String = s"$method-$path".replaceAll("\\W", "_")
      val requestVerb: String = method.name()
      val requestUrl: String = buildRequestUrl(path)
      val summary: String = Option(pathItem.getSummary)
        .filter(StringUtils.isNotBlank)
        .getOrElse(buildSummary(method, op, path))
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
      val successResponseBody: Product = getResponseExample(openAPI, op.getResponses)
      val errorResponseBodies: List[String] = List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      )
      val catalogs: Catalogs = Catalogs(notCore, notPSD2, notOBWG)

      val roles: Option[List[ApiRole]] = {
        val roleName = s"Can$summary${entitlementSuffix(path)}"
          .replaceFirst("Can(Create|Update|Get|Delete)", "Can$1Dynamic")
          .replace(" ", "")
        Some(List(
          ApiRole.getOrCreateDynamicApiRole(roleName)
        ))
      }
      val doc = ResourceDoc(
        partialFunction,
        implementedInApiVersion,
        partialFunctionName,
        requestVerb,
        requestUrl,
        summary,
        description,
        exampleRequestBody,
        successResponseBody,
        errorResponseBodies,
        catalogs,
        tags,
        roles
      )
      (doc, path)
    }

    DynamicEndpointInfo(id, docs, serverUrl)
  }

  private val PathParamRegx = """\{(.+?)\}""".r
  private val WordBoundPattern = Pattern.compile("(?<=[a-z])(?=[A-Z])|-")

  private def buildRequestUrl(path: String): String = {
    val url = StringUtils.split(s"$urlPrefix/$path", "/")
    url.map {
      case PathParamRegx(param) => WordBoundPattern.matcher(param).replaceAll("_").toUpperCase()
      case v => v
    }.mkString("/", "/", "")
  }

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

  def doc: ArrayBuffer[ResourceDoc] = {
    val docs = ArrayBuffer[ResourceDoc]()
    dynamicEndpointInfos.forEach { info =>
      info.resourceDocs.foreach { doc =>
        docs += doc
      }
    }
    docs
  }

  private def buildSummary(method: HttpMethod, op: Operation, path: String): String = method match {
    case _ if StringUtils.isNotBlank(op.getSummary) => op.getSummary
    case HttpMethod.GET | HttpMethod.DELETE =>
      val opName = if(method == HttpMethod.GET) "Get" else "Delete"
      op.getResponses.asScala
        .find(_._1.startsWith("20"))
        .flatMap(it => getRef(it._2.getContent, it._2.get$ref()) )
        .map(StringUtils.substringAfterLast(_, "/"))
        .map(entityName => s"$opName $entityName")
        .orElse(Option(op.getDescription))
        .filter(StringUtils.isNotBlank)
        .orElse(Option(s"$opName $path"))
        .map(_.replaceFirst("(?i)((get|delete)\\s+\\S+).*", "$1"))
        .map(capitalize)
        .get

    case m@(HttpMethod.POST | HttpMethod.PUT) =>
      val opName = if(m == HttpMethod.POST) "Create" else "Update"

      getRef(op.getRequestBody.getContent, op.getRequestBody.get$ref())
        .map(StringUtils.substringAfterLast(_, "/"))
        .map(entityName => s"$opName $entityName")
        .orElse(Option(op.getDescription))
        .filter(StringUtils.isNotBlank)
        .orElse(Option(s"$method $path"))
        .map(capitalize)
        .get
    case _ => throw new RuntimeException(s"Support HTTP METHOD: GET, POST, PUT, DELETE, current method is $method")
  }
  private def capitalize(str: String): String =
    StringUtils.split(str, " ").map(_.capitalize).mkString(" ")

  private def getRequestExample(openAPI: OpenAPI, body: RequestBody): Product = {
    if(body == null || body.getContent == null) {
       JObject()
    } else {
      getExample(openAPI, getRef(body.getContent, body.get$ref()).orNull)
    }
  }
  private def getResponseExample(openAPI: OpenAPI, apiResponses: ApiResponses): Product = {
    if(apiResponses == null || apiResponses.isEmpty) {
      JObject()
    } else {
      val ref: Option[String] = apiResponses.asScala
        .find(_._1.startsWith("20"))
        .flatMap(it => getRef(it._2.getContent, it._2.get$ref()))
      getExample(openAPI, ref.orNull)
    }
  }

  private def getRef(content: Content, $ref: String): Option[String] = {
    if(StringUtils.isNoneBlank($ref)) {
       Option($ref)
    } else {
      val schemaRef: Option[String] = Option(content.get("application/json"))
        .flatMap(it => Option[Schema[_]](it.getSchema))
        .map(_.get$ref())
        .filter(StringUtils.isNoneBlank(_))

      if(schemaRef.isDefined) {
        Option(schemaRef.get)
      } else  {
        val supportMediaTypes = content.values().asScala
        supportMediaTypes.collectFirst {
          case mediaType if mediaType.getSchema != null && StringUtils.isNotBlank(mediaType.getSchema.get$ref()) =>
            mediaType.getSchema.get$ref()
        }
      }
    }

  }
  private val RegexDefinitions = """(?:#/components/schemas(?:/#definitions)?/)(.+)""".r
  private val RegexResponse = """#/responses/(.+)""".r

  private def getExample(openAPI: OpenAPI, ref: String): Product = {
    implicit val formats = CustomJsonFormats.formats
    ref match {
      case null => JObject()

      case RegexResponse(refName) =>
        val response = openAPI.getComponents.getResponses.get(refName)
        val ref = getRef(response.getContent, response.get$ref())
        getExample(openAPI, ref.get)

      case RegexDefinitions(refName) =>
        openAPI.getComponents.getSchemas.get(refName) match {
          case o: ObjectSchema =>
            val properties: util.Map[String, Schema[_]] = o.getProperties

            val jFields: mutable.Iterable[JField] = properties.asScala.map { kv =>
              val (name, value) = kv
              val valueExample = if(value.getClass == classOf[Schema[_]]) getExample(openAPI, value.get$ref()) else getPropertyExample(value)
              JField(name, json.Extraction.decompose(valueExample))
            }
            JObject(jFields.toList)

          case a: ArraySchema =>
            Option(a.getExample)
              .map(json.Extraction.decompose(_).asInstanceOf[JObject])
              .getOrElse {
                val schema: Schema[_] = a.getItems
                val singleItem: Any = if(schema.getClass == classOf[Schema[_]]) getExample(openAPI, schema.get$ref()) else getPropertyExample(schema)
                val jItem = json.Extraction.decompose(singleItem)
                jItem :: Nil
              }
        }

    }
  }

  private def getPropertyExample(schema: Schema[_]) = schema match {
    case b: BooleanSchema => Option(b.getExample).getOrElse(true)
    case d: DateSchema => Option(d.getExample).getOrElse {
      APIUtil.DateWithDayFormat.format(new Date())
    }
    case t: DateTimeSchema => Option(t.getExample).getOrElse {
      APIUtil.DateWithSecondsFormat.format(new Date())
    }
    case i: IntegerSchema => Option(i.getExample).getOrElse(1)
    case n: NumberSchema => Option(n.getExample).getOrElse(1.2)
    case s: StringSchema => Option(s.getExample).getOrElse("string")
    case _ => throw new RuntimeException(s"Not support type $schema, please support it if necessary.")
  }
}

/**
 *
 * @param id DynamicEntity id value
 * @param docsToUrl ResourceDoc to url that defined in swagger content
 * @param serverUrl base url that defined in swagger content
 */
case class DynamicEndpointInfo(id: String, docsToUrl: mutable.Iterable[(ResourceDoc, String)], serverUrl: String) {
  val resourceDocs: mutable.Iterable[ResourceDoc] = docsToUrl.map(_._1)

  private val existsUrlToMethod: mutable.Iterable[(HttpMethod, String, ResourceDoc)] =
    docsToUrl
    .map(it => {
      val (doc, path) = it
      (HttpMethod.valueOf(doc.requestVerb), path, doc)
    })

  def findDynamicEndpoint(newMethod: HttpMethod, newUrl: String): Option[(DynamicEndpointInfo, ResourceDoc, String)] = existsUrlToMethod.find(it => {
    val (method, url, _) = it
    isSameUrl(newUrl, url) && newMethod == method
  }).map(it => (this, it._3, it._2))

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
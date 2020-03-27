package code.api.v4_0_0

import java.io.File
import java.nio.charset.Charset
import java.util
import java.util.concurrent.CopyOnWriteArrayList
import java.util.{Date, Objects}

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
import io.swagger.v3.oas.models.media.{ArraySchema, BooleanSchema, Content, DateSchema, DateTimeSchema, IntegerSchema, NumberSchema, ObjectSchema, Schema, StringSchema}
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.parser.OpenAPIV3Parser
import net.liftweb.json.JsonAST.{JArray, JField, JObject}
import net.liftweb.json.JsonDSL._
import net.liftweb.json
import net.liftweb.json.JValue
import net.liftweb.util.StringHelpers
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.atteo.evo.inflector.English

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._


object DynamicEndpointHelper {
  private implicit val formats = CustomJsonFormats.formats
  /**
   * dynamic endpoints url prefix
   */
  val urlPrefix = APIUtil.getPropsValue("dynamic_endpoints_url_prefix", "dynamic")

  private lazy val dynamicEndpointInfos: CopyOnWriteArrayList[DynamicEndpointInfo] = {
    val dynamicEndpoints: List[DynamicEndpointT] = DynamicEndpointProvider.connectorMethodProvider.vend.getAll()
    val infos = dynamicEndpoints.map(it => swaggerToResourceDocs(it.swaggerString, it.dynamicEndpointId.get))
    new CopyOnWriteArrayList(infos.asJava)
  }

  def findExistsEndpoints(openAPI: OpenAPI): List[(String, HttpMethod)] = {
    val existsUrlToMethod: mutable.Buffer[(String, HttpMethod)] = dynamicEndpointInfos.asScala.flatMap(_.resourceDocs)
      .map(doc => doc.requestUrl.replace(urlPrefix, "") -> HttpMethod.valueOf(doc.requestVerb))

    def isExists(newUrl: String, newMethod: HttpMethod) = existsUrlToMethod.exists(kv => {
      val (url, method) = kv
      isSameUrl(newUrl, url) && newMethod == method
    })

     for {
      (path, pathItem) <- openAPI.getPaths.asScala.toList
      (method: HttpMethod, _) <- pathItem.readOperationsMap.asScala
      if isExists(path, method)
    } yield (path, method)

  }

  /**
   * check whether two url is the same:
   *  isSameUrl("/abc/efg", "/abc/efg") == true
   *  isSameUrl("/abc/efg", "/abc/{id}") == true
   *  isSameUrl("/abc/{userId}", "/abc/{id}") == true
   *  isSameUrl("/abc/{userId}/", "/abc/{id}") == true
   *  isSameUrl("/def/abc/", "/abc/{id}") == false
   * @param path1
   * @param path2
   * @return
   */
  private def isSameUrl(path1: String, path2: String) = {
    val path1Parts = StringUtils.split(path1, '/')
    val path2Parts = StringUtils.split(path2, '/')
    if(path1Parts.size != path2Parts.size) {
      false
    } else {
      path1Parts.zip(path2Parts).forall {kv =>
        val (part1, part2) = kv
        part1 == part2 ||
          (part1.startsWith("{") && part1.endsWith("}")) ||
          (part2.startsWith("{") && part2.endsWith("}"))
      }
    }
  }

  def swaggerToResourceDocs(content: String, id: String): DynamicEndpointInfo = {
    val openAPI: OpenAPI = parseSwaggerContent(content)

    val tags: List[ResourceDocTag] = List(ApiTag.apiTagDynamicEndpoint, apiTagApi, apiTagNewStyle)

    val paths: mutable.Map[String, PathItem] = openAPI.getPaths.asScala
    def entitlementSuffix(path: String) = Math.abs(path.hashCode).toString.substring(0, 3) // to avoid different swagger have same entitlement
    val docs: mutable.Iterable[ResourceDoc] = for {
      (path, pathItem) <- paths
      (method: HttpMethod, op: Operation) <- pathItem.readOperationsMap.asScala
    } yield {
      val implementedInApiVersion = ApiVersion.v4_0_0
      val partialFunction: OBPEndpoint = APIMethods400.Implementations4_0_0.genericEndpoint // TODO create real endpoint
      val partialFunctionName: String = s"$method-$path".replace('/', '_')
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
        .getOrElse(summary)
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
      val connectorMethods = Some(List(s"""dynamicEntityProcess: parameters contains {"key": "entityName", "value": "$summary"}""")) //TODO temp
      ResourceDoc(
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
        roles,
        connectorMethods = connectorMethods
      )
    }

    val serverUrl = {
      val servers = openAPI.getServers
      if(servers.isEmpty) {
        None
      } else {
        Some(servers.get(0).getUrl)
      }
    }
    DynamicEndpointInfo(null, docs, serverUrl)
  }

  private def buildRequestUrl(path: String): String =
    s"/$urlPrefix/$path".replace("//", "/")

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
       ""
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

  private def getExample(openAPI: OpenAPI, ref: String): Product = ref match {
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

case class DynamicEndpointInfo(id: String, resourceDocs: mutable.Iterable[ResourceDoc], serverUrl: Option[String]) {

}
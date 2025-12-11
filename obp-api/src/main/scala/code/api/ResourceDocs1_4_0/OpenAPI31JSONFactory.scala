/**
Open Bank Project - API
Copyright (C) 2011-2024, TESOBE GmbH.

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

import code.api.util.APIUtil.{EmptyBody, JArrayBody, PrimaryDataBody, ResourceDoc}
import code.api.util.ErrorMessages._
import code.api.util._
import code.api.v1_4_0.JSONFactory1_4_0.ResourceDocJson
import com.openbankproject.commons.model.ListResult
import com.openbankproject.commons.util.{ApiVersion, JsonAble, JsonUtils, ReflectUtils}
import net.liftweb.json.JsonAST.{JArray, JObject, JValue}
import net.liftweb.json._
import net.liftweb.json.Extraction

import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._
import java.lang.{Boolean => XBoolean, Double => XDouble, Float => XFloat, Integer => XInt, Long => XLong, String => XString}
import java.math.{BigDecimal => JBigDecimal}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import code.util.Helper.MdcLoggable

/**
 * OpenAPI 3.1 JSON Factory for OBP API
 * 
 * This factory generates OpenAPI 3.1 compliant JSON documentation
 * from OBP ResourceDoc objects.
 */
object OpenAPI31JSONFactory extends MdcLoggable {

  // OpenAPI 3.1 Root Object
  case class OpenAPI31Json(
    openapi: String = "3.1.0",
    info: InfoJson,
    servers: List[ServerJson],
    paths: Map[String, PathItemJson],
    components: ComponentsJson,
    security: Option[List[Map[String, List[String]]]] = None,
    tags: Option[List[TagJson]] = None,
    externalDocs: Option[ExternalDocumentationJson] = None
  )

  // Info Object
  case class InfoJson(
    title: String,
    version: String,
    description: Option[String] = None,
    termsOfService: Option[String] = None,
    contact: Option[ContactJson] = None,
    license: Option[LicenseJson] = None,
    summary: Option[String] = None
  )

  case class ContactJson(
    name: Option[String] = None,
    url: Option[String] = None,
    email: Option[String] = None
  )

  case class LicenseJson(
    name: String,
    identifier: Option[String] = None,
    url: Option[String] = None
  )

  // Server Object
  case class ServerJson(
    url: String,
    description: Option[String] = None,
    variables: Option[Map[String, ServerVariableJson]] = None
  )

  case class ServerVariableJson(
    enum: Option[List[String]] = None,
    default: String,
    description: Option[String] = None
  )

  // Components Object
  case class ComponentsJson(
    schemas: Option[Map[String, SchemaJson]] = None,
    responses: Option[Map[String, ResponseJson]] = None,
    parameters: Option[Map[String, ParameterJson]] = None,
    examples: Option[Map[String, ExampleJson]] = None,
    requestBodies: Option[Map[String, RequestBodyJson]] = None,
    headers: Option[Map[String, HeaderJson]] = None,
    securitySchemes: Option[Map[String, SecuritySchemeJson]] = None,
    links: Option[Map[String, LinkJson]] = None,
    callbacks: Option[Map[String, CallbackJson]] = None,
    pathItems: Option[Map[String, PathItemJson]] = None
  )

  // Path Item Object
  case class PathItemJson(
    summary: Option[String] = None,
    description: Option[String] = None,
    get: Option[OperationJson] = None,
    put: Option[OperationJson] = None,
    post: Option[OperationJson] = None,
    delete: Option[OperationJson] = None,
    options: Option[OperationJson] = None,
    head: Option[OperationJson] = None,
    patch: Option[OperationJson] = None,
    trace: Option[OperationJson] = None,
    servers: Option[List[ServerJson]] = None,
    parameters: Option[List[ParameterJson]] = None
  )

  // Operation Object
  case class OperationJson(
    tags: Option[List[String]] = None,
    summary: Option[String] = None,
    description: Option[String] = None,
    externalDocs: Option[ExternalDocumentationJson] = None,
    operationId: Option[String] = None,
    parameters: Option[List[ParameterJson]] = None,
    requestBody: Option[RequestBodyJson] = None,
    responses: ResponsesJson,
    callbacks: Option[Map[String, CallbackJson]] = None,
    deprecated: Option[Boolean] = None,
    security: Option[List[Map[String, List[String]]]] = None,
    servers: Option[List[ServerJson]] = None
  )

  // Parameter Object
  case class ParameterJson(
    name: String,
    in: String,
    description: Option[String] = None,
    required: Option[Boolean] = None,
    deprecated: Option[Boolean] = None,
    allowEmptyValue: Option[Boolean] = None,
    style: Option[String] = None,
    explode: Option[Boolean] = None,
    allowReserved: Option[Boolean] = None,
    schema: Option[SchemaJson] = None,
    example: Option[JValue] = None,
    examples: Option[Map[String, ExampleJson]] = None
  )

  // Request Body Object
  case class RequestBodyJson(
    description: Option[String] = None,
    content: Map[String, MediaTypeJson],
    required: Option[Boolean] = None
  )

  // Responses Object - simplified to avoid nesting
  type ResponsesJson = Map[String, ResponseJson]

  // Response Object
  case class ResponseJson(
    description: String,
    headers: Option[Map[String, HeaderJson]] = None,
    content: Option[Map[String, MediaTypeJson]] = None,
    links: Option[Map[String, LinkJson]] = None
  )

  // Media Type Object
  case class MediaTypeJson(
    schema: Option[SchemaJson] = None,
    example: Option[JValue] = None,
    examples: Option[Map[String, ExampleJson]] = None,
    encoding: Option[Map[String, EncodingJson]] = None
  )

  // Schema Object (JSON Schema 2020-12)
  case class SchemaJson(
    // Core vocabulary
    `$schema`: Option[String] = None,
    `$id`: Option[String] = None,
    `$ref`: Option[String] = None,
    `$defs`: Option[Map[String, SchemaJson]] = None,
    
    // Type validation
    `type`: Option[String] = None,
    enum: Option[List[JValue]] = None,
    const: Option[JValue] = None,
    
    // Numeric validation
    multipleOf: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[BigDecimal] = None,
    minimum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[BigDecimal] = None,
    
    // String validation
    maxLength: Option[Int] = None,
    minLength: Option[Int] = None,
    pattern: Option[String] = None,
    
    // Array validation
    maxItems: Option[Int] = None,
    minItems: Option[Int] = None,
    uniqueItems: Option[Boolean] = None,
    maxContains: Option[Int] = None,
    minContains: Option[Int] = None,
    
    // Object validation
    maxProperties: Option[Int] = None,
    minProperties: Option[Int] = None,
    required: Option[List[String]] = None,
    dependentRequired: Option[Map[String, List[String]]] = None,
    
    // Schema composition
    allOf: Option[List[SchemaJson]] = None,
    anyOf: Option[List[SchemaJson]] = None,
    oneOf: Option[List[SchemaJson]] = None,
    not: Option[SchemaJson] = None,
    
    // Conditional schemas
    `if`: Option[SchemaJson] = None,
    `then`: Option[SchemaJson] = None,
    `else`: Option[SchemaJson] = None,
    
    // Array schemas
    prefixItems: Option[List[SchemaJson]] = None,
    items: Option[SchemaJson] = None,
    contains: Option[SchemaJson] = None,
    
    // Object schemas
    properties: Option[Map[String, SchemaJson]] = None,
    patternProperties: Option[Map[String, SchemaJson]] = None,
    additionalProperties: Option[Either[Boolean, SchemaJson]] = None,
    propertyNames: Option[SchemaJson] = None,
    
    // Format
    format: Option[String] = None,
    
    // Metadata
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[JValue] = None,
    deprecated: Option[Boolean] = None,
    readOnly: Option[Boolean] = None,
    writeOnly: Option[Boolean] = None,
    examples: Option[List[JValue]] = None
  )

  // Supporting objects
  case class ExampleJson(
    summary: Option[String] = None,
    description: Option[String] = None,
    value: Option[JValue] = None,
    externalValue: Option[String] = None
  )

  case class EncodingJson(
    contentType: Option[String] = None,
    headers: Option[Map[String, HeaderJson]] = None,
    style: Option[String] = None,
    explode: Option[Boolean] = None,
    allowReserved: Option[Boolean] = None
  )

  case class HeaderJson(
    description: Option[String] = None,
    required: Option[Boolean] = None,
    deprecated: Option[Boolean] = None,
    allowEmptyValue: Option[Boolean] = None,
    style: Option[String] = None,
    explode: Option[Boolean] = None,
    allowReserved: Option[Boolean] = None,
    schema: Option[SchemaJson] = None,
    example: Option[JValue] = None,
    examples: Option[Map[String, ExampleJson]] = None
  )

  case class SecuritySchemeJson(
    `type`: String,
    description: Option[String] = None,
    name: Option[String] = None,
    in: Option[String] = None,
    scheme: Option[String] = None,
    bearerFormat: Option[String] = None,
    flows: Option[OAuthFlowsJson] = None,
    openIdConnectUrl: Option[String] = None
  )

  case class OAuthFlowsJson(
    `implicit`: Option[OAuthFlowJson] = None,
    password: Option[OAuthFlowJson] = None,
    clientCredentials: Option[OAuthFlowJson] = None,
    authorizationCode: Option[OAuthFlowJson] = None
  )

  case class OAuthFlowJson(
    authorizationUrl: Option[String] = None,
    tokenUrl: Option[String] = None,
    refreshUrl: Option[String] = None,
    scopes: Map[String, String]
  )

  // Security requirements are just a map of scheme name to scopes
  type SecurityRequirementJson = Map[String, List[String]]

  case class TagJson(
    name: String,
    description: Option[String] = None,
    externalDocs: Option[ExternalDocumentationJson] = None
  )

  case class ExternalDocumentationJson(
    description: Option[String] = None,
    url: String
  )

  case class LinkJson(
    operationRef: Option[String] = None,
    operationId: Option[String] = None,
    parameters: Option[Map[String, JValue]] = None,
    requestBody: Option[JValue] = None,
    description: Option[String] = None,
    server: Option[ServerJson] = None
  )

  case class CallbackJson(
    expressions: Map[String, PathItemJson]
  )

  /**
   * Creates an OpenAPI 3.1 document from a list of ResourceDoc objects
   */
  def createOpenAPI31Json(
    resourceDocs: List[ResourceDocJson], 
    requestedApiVersion: String,
    hostname: String
  ): OpenAPI31Json = {

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    
    // Clean version string to avoid double 'v' prefix
    val cleanVersion = if (requestedApiVersion.startsWith("v")) requestedApiVersion.substring(1) else requestedApiVersion
    
    // Create Info object
    val info = InfoJson(
      title = s"Open Bank Project API v$cleanVersion",
      version = cleanVersion,
      description = Some(s"""The Open Bank Project API v$cleanVersion provides standardized banking APIs.
        |
        |This specification was automatically generated from the OBP API codebase.
        |Generated on: $timestamp
        |
        |For more information, visit: https://github.com/OpenBankProject/OBP-API""".stripMargin),
      contact = Some(ContactJson(
        name = Some("Open Bank Project"),
        url = Some("https://www.openbankproject.com"),
        email = Some("contact@tesobe.com")
      )),
      license = Some(LicenseJson(
        name = "AGPL v3",
        url = Some("https://www.gnu.org/licenses/agpl-3.0.html")
      ))
    )

    // Create Servers
    val servers = List(
      ServerJson(
        url = hostname,
        description = Some("Back-end server")
      )
    )

    // Group resource docs by path and convert to operations
    val pathGroups = resourceDocs.groupBy(_.request_url)
    val paths = pathGroups.map { case (path, docs) =>
      val openApiPath = convertPathToOpenAPI(path)
      val pathItem = createPathItem(docs)
      openApiPath -> pathItem
    }

    // Extract schemas from all request/response bodies
    val schemas = extractSchemas(resourceDocs)

    // Create security schemes
    val securitySchemes = Map(
      "DirectLogin" -> SecuritySchemeJson(
        `type` = "apiKey",
        description = Some("Direct Login token authentication"),
        name = Some("Authorization"),
        in = Some("header")
      ),
      "GatewayLogin" -> SecuritySchemeJson(
        `type` = "apiKey", 
        description = Some("Gateway Login token authentication"),
        name = Some("Authorization"),
        in = Some("header")
      ),
      "OAuth2" -> SecuritySchemeJson(
        `type` = "oauth2",
        description = Some("OAuth2 authentication"),
        flows = Some(OAuthFlowsJson(
          authorizationCode = Some(OAuthFlowJson(
            authorizationUrl = Some("/oauth/authorize"),
            tokenUrl = Some("/oauth/token"),
            scopes = Map.empty
          ))
        ))
      )
    )

    // Create components
    val components = ComponentsJson(
      schemas = if (schemas.nonEmpty) Some(schemas) else None,
      securitySchemes = Some(securitySchemes)
    )

    // Extract unique tags
    val allTags = resourceDocs.flatMap(_.tags).distinct.map { tag =>
      TagJson(
        name = cleanTagName(tag),
        description = Some(s"Operations related to ${cleanTagName(tag)}")
      )
    }

    OpenAPI31Json(
      info = info,
      servers = servers,
      paths = paths,
      components = components,
      tags = if (allTags.nonEmpty) Some(allTags) else None
    )
  }

  /**
   * Converts OBP path format to OpenAPI path format
   */
  private def convertPathToOpenAPI(obpPath: String): String = {
    // Handle paths that are already in OpenAPI format or convert from OBP format
    if (obpPath.contains("{") && obpPath.contains("}")) {
      // Already in OpenAPI format, return as-is
      obpPath
    } else {
      // Convert OBP path parameters (BANK_ID) to OpenAPI format ({bankid})
      val segments = obpPath.split("/")
      segments.map { segment =>
        if (segment.matches("[A-Z_]+")) {
          s"{${segment.toLowerCase.replace("_", "")}}"
        } else {
          segment
        }
      }.mkString("/")
    }
  }

  /**
   * Creates a PathItem object from a list of ResourceDoc objects for the same path
   */
  private def createPathItem(docs: List[ResourceDocJson]): PathItemJson = {
    val operations = docs.map(createOperation).toMap
    
    PathItemJson(
      get = operations.get("GET"),
      post = operations.get("POST"), 
      put = operations.get("PUT"),
      delete = operations.get("DELETE"),
      patch = operations.get("PATCH"),
      options = operations.get("OPTIONS"),
      head = operations.get("HEAD")
    )
  }

  /**
   * Creates an Operation object from a ResourceDoc
   */
  private def createOperation(doc: ResourceDocJson): (String, OperationJson) = {
    val method = doc.request_verb.toUpperCase
    
    // Convert path to OpenAPI format and extract parameters
    val openApiPath = convertPathToOpenAPI(doc.request_url)
    val pathParams = extractOpenAPIPathParameters(openApiPath)
    
    // Create parameters
    val parameters = pathParams.map { paramName =>
      ParameterJson(
        name = paramName,
        in = "path",
        required = Some(true),
        schema = Some(SchemaJson(`type` = Some("string"))),
        description = Some(s"The ${paramName.toUpperCase} identifier")
      )
    }

    // Create request body if needed
    val requestBody = if (List("POST", "PUT", "PATCH").contains(method) && doc.typed_request_body != JNothing) {
      Some(RequestBodyJson(
        description = Some("Request body"),
        content = Map(
          "application/json" -> MediaTypeJson(
            schema = Some(inferSchemaFromExample(doc.typed_request_body)),
            example = Some(doc.typed_request_body)
          )
        ),
        required = Some(true)
      ))
    } else None

    // Create responses
    val successResponse = ResponseJson(
      description = "Successful operation",
      content = if (doc.typed_success_response_body != JNothing) {
        Some(Map(
          "application/json" -> MediaTypeJson(
            schema = Some(inferSchemaFromExample(doc.typed_success_response_body)),
            example = Some(doc.typed_success_response_body)
          )
        ))
      } else None
    )

    val errorResponses = createErrorResponses(doc.error_response_bodies)
    
    val responsesMap = Map("200" -> successResponse) ++ errorResponses

    // Create tags
    val tags = if (doc.tags.nonEmpty) {
      Some(doc.tags.map(cleanTagName))
    } else None

    // Check if authentication is required
    val security = if (requiresAuthentication(doc)) {
      Some(List(
        Map("DirectLogin" -> List.empty[String]),
        Map("GatewayLogin" -> List.empty[String]),
        Map("OAuth2" -> List.empty[String])
      ))
    } else None

    val operation = OperationJson(
      summary = Some(doc.summary),
      description = Some(doc.description),
      operationId = Some(doc.operation_id),
      tags = tags,
      parameters = if (parameters.nonEmpty) Some(parameters) else None,
      requestBody = requestBody,
      responses = responsesMap,
      security = security
    )

    method -> operation
  }



  /**
   * Extracts path parameters from OpenAPI path format
   */
  private def extractOpenAPIPathParameters(path: String): List[String] = {
    val paramPattern = """\{([^}]+)\}""".r
    paramPattern.findAllMatchIn(path).map(_.group(1)).toList
  }

  /**
   * Infers a JSON Schema from an example JSON value
   */
  private def inferSchemaFromExample(example: JValue): SchemaJson = {
    example match {
      case JObject(fields) =>
        val properties = fields.map { case JField(name, value) =>
          name -> inferSchemaFromExample(value)
        }.toMap
        
        val required = fields.collect {
          case JField(name, value) if value != JNothing && value != JNull => name
        }

        SchemaJson(
          `type` = Some("object"),
          properties = Some(properties),
          required = if (required.nonEmpty) Some(required) else None
        )

      case JArray(values) =>
        val itemSchema = values.headOption.map(inferSchemaFromExample)
          .getOrElse(SchemaJson(`type` = Some("object")))
        
        SchemaJson(
          `type` = Some("array"),
          items = Some(itemSchema)
        )

      case JString(_) => SchemaJson(`type` = Some("string"))
      case JInt(_) => SchemaJson(`type` = Some("integer"))
      case JDouble(_) => SchemaJson(`type` = Some("number"))
      case JBool(_) => SchemaJson(`type` = Some("boolean"))
      case JNull => SchemaJson(`type` = Some("null"))
      case JNothing => SchemaJson(`type` = Some("object"))
      case _ => SchemaJson(`type` = Some("object"))
    }
  }

  /**
   * Extracts reusable schemas from all resource docs
   */
  private def extractSchemas(resourceDocs: List[ResourceDocJson]): Map[String, SchemaJson] = {
    // This could be enhanced to extract common schemas and create references
    // For now, we'll return an empty map and inline schemas
    Map.empty[String, SchemaJson]
  }

  /**
   * Creates error response objects
   */
  private def createErrorResponses(errorBodies: List[String]): Map[String, ResponseJson] = {
    val commonErrors = Map(
      "400" -> ResponseJson(description = "Bad Request"),
      "401" -> ResponseJson(description = "Unauthorized"), 
      "403" -> ResponseJson(description = "Forbidden"),
      "404" -> ResponseJson(description = "Not Found"),
      "500" -> ResponseJson(description = "Internal Server Error")
    )

    // Always include common error responses for better API documentation
    if (errorBodies.nonEmpty) {
      commonErrors.filter { case (code, _) =>
        errorBodies.exists(_.contains(code)) ||
        errorBodies.exists(_.toLowerCase.contains("unauthorized")) && code == "401" ||
        errorBodies.exists(_.toLowerCase.contains("not found")) && code == "404" ||
        errorBodies.exists(_.toLowerCase.contains("bad request")) && code == "400" ||
        code == "500" // Always include 500 for server errors
      }
    } else {
      Map("500" -> ResponseJson(description = "Internal Server Error"))
    }
  }

  /**
   * Determines if an endpoint requires authentication
   */
  private def requiresAuthentication(doc: ResourceDocJson): Boolean = {
    doc.error_response_bodies.exists(_.contains("UserNotLoggedIn")) ||
    doc.roles.nonEmpty ||
    doc.description.toLowerCase.contains("authentication is required") ||
    doc.description.toLowerCase.contains("user must be logged in")
  }

  /**
   * Cleans tag names for better presentation
   */
  private def cleanTagName(tag: String): String = {
    tag.replaceFirst("^apiTag", "").replaceFirst("^tag", "")
  }

  /**
   * Converts OpenAPI31Json to JValue for JSON output
   */
  object OpenAPI31JsonFormats {
    implicit val formats: Formats = DefaultFormats

    def toJValue(openapi: OpenAPI31Json): JValue = {
      val baseJson = Extraction.decompose(openapi)(formats)
      // Transform to fix nested structures
      transformJson(baseJson)
    }
    
    private def transformJson(json: JValue): JValue = {
      json.transform {
        // Fix responses structure - flatten nested responses
        case JObject(fields) if fields.exists(_.name == "responses") =>
          JObject(fields.map {
            case JField("responses", JObject(responseFields)) =>
              // If responses contains another responses field, flatten it
              responseFields.find(_.name == "responses") match {
                case Some(JField(_, JObject(innerResponses))) =>
                  JField("responses", JObject(innerResponses))
                case _ =>
                  JField("responses", JObject(responseFields))
              }
            case other => other
          })
        // Fix security structure - remove requirements wrapper
        case JObject(fields) if fields.exists(_.name == "security") =>
          JObject(fields.map {
            case JField("security", JArray(securityItems)) =>
              val fixedSecurity = securityItems.map {
                case JObject(List(JField("requirements", securityObj))) => securityObj
                case other => other
              }
              JField("security", JArray(fixedSecurity))
            case other => other
          })
      }
    }
  }
}
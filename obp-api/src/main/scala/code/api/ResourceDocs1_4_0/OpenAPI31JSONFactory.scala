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
import org.json4s.JsonAST.{JArray, JObject, JValue}
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._
import org.json4s.Extraction

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

  // =====================================================================================
  // Authentication descriptions — SOURCE OF TRUTH
  // These methods are the canonical documentation for OBP authentication methods.
  // The Glossary and other docs import from here. (DRY)
  // If you need to change authentication documentation, change it here.
  // =====================================================================================

  def directLoginDescription(hostname: String): String =
    s"""Direct Login is a simple authentication process for trusted environments and testing.
       |
       |### Step 1: Get your Consumer Key
       |
       |Register your application at ${hostname}/consumer-registration to obtain a consumer_key.
       |
       |### Step 2: Obtain a token
       |
       |Send a POST request with your credentials in the DirectLogin header:
       |
       |    POST ${hostname}/obp/v6.0.0/my/logins/direct
       |    Content-Type: application/json
       |    DirectLogin: username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=YOUR_CONSUMER_KEY
       |
       |The body should be left empty.
       |
       |A successful response returns a JSON object containing a token:
       |
       |    {"token": "your-token-string"}
       |
       |### Step 3: Use the token in subsequent API calls
       |
       |Include the token in the DirectLogin header on all subsequent requests:
       |
       |    GET ${hostname}/obp/v6.0.0/my/banks
       |    DirectLogin: token=your-token-string
       |
       |### Parameters
       |
       |Parameter names and values are case sensitive. Each parameter must appear only once per request.
       |
       |- **username** - The name of the user to authenticate.
       |- **password** - The password of the user.
       |- **consumer_key** - The application identifier obtained during registration.
       |
       |### Notes
       |
       |- The header name is **DirectLogin** (case insensitive for HTTP/1.1, must be lower case for HTTP/2).
       |- Direct Login is intended for hackathons, testing, and trusted environments.
       |- For production use with third-party applications, use OAuth2 / OIDC instead.""".stripMargin

  def oAuth2Description(hostname: String): String =
    s"""OAuth2 / OpenID Connect (OIDC) is the recommended authentication method for production use.
       |
       |OBP supports the OAuth2 Authorization Code flow for user-facing applications and the
       |Client Credentials flow for application-only (machine-to-machine) access.
       |
       |### Authorization Code Flow (3-legged)
       |
       |Use this flow when your application needs to act on behalf of a user.
       |
       |1. Register your application at ${hostname}/consumer-registration to obtain a client_id and client_secret.
       |2. Redirect the user to the authorization URL with your client_id and redirect_uri.
       |3. After the user authenticates and consents, they are redirected back with an authorization code.
       |4. Exchange the authorization code for an access token at the token endpoint.
       |5. Use the access token as a Bearer token in the Authorization header:
       |
       |       GET ${hostname}/obp/v6.0.0/my/banks
       |       Authorization: Bearer your-access-token
       |
       |### Client Credentials Flow (2-legged / Application Only)
       |
       |Use this flow for endpoints with authMode ApplicationOnly or UserOrApplication.
       |The application authenticates itself using its client_id and client_secret without a user login.
       |
       |    POST <OIDC_provider_token_endpoint>
       |    Content-Type: application/x-www-form-urlencoded
       |
       |    grant_type=client_credentials&client_id=YOUR_CLIENT_ID&client_secret=YOUR_CLIENT_SECRET
       |
       |The response contains a Bearer token to use in subsequent API calls.
       |
       |### Notes
       |
       |- The authorization and token endpoint URLs depend on the OIDC provider configured for this OBP instance.
       |- Token expiry and refresh behaviour depend on the OIDC provider configuration.
       |- OAuth2 / OIDC is the recommended method for production deployments.""".stripMargin

  def gatewayLoginDescription(hostname: String): String =
    s"""Gateway Login allows a gateway (e.g. API Gateway) to authenticate on behalf of a user.
       |
       |The gateway sends a JWT in the Authorization header. The JWT is signed with a shared secret
       |between the gateway and OBP. This method does not require a separate token-obtaining step —
       |the JWT is sent directly with each API request.
       |
       |    GET ${hostname}/obp/v6.0.0/my/banks
       |    Authorization: GatewayLogin token="your-jwt-token"
       |
       |### Notes
       |
       |- Gateway Login is intended for trusted infrastructure, not end-user applications.
       |- The JWT must be signed with the pre-shared secret configured on the OBP instance.
       |- Contact your OBP administrator for gateway integration details.""".stripMargin

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
    `enum`: Option[List[String]] = None,
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
    `enum`: Option[List[JValue]] = None,
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

    // Create security schemes — descriptions come from the methods defined at the top of this object
    val securitySchemes = Map(
      "DirectLogin" -> SecuritySchemeJson(
        `type` = "apiKey",
        description = Some(directLoginDescription(hostname)),
        name = Some("DirectLogin"),
        in = Some("header")
      ),
      "GatewayLogin" -> SecuritySchemeJson(
        `type` = "apiKey",
        description = Some(gatewayLoginDescription(hostname)),
        name = Some("Authorization"),
        in = Some("header")
      ),
      // OBP API consumes Bearer tokens issued by external IdPs (Google, Yahoo,
      // Azure, Keycloak, OBP-OIDC) — it does not issue its own OAuth2 tokens. The
      // accurate OpenAPI representation is `type: http, scheme: bearer`, not an
      // `oauth2` flow with token-issuance URLs.
      "OAuth2" -> SecuritySchemeJson(
        `type` = "http",
        description = Some(oAuth2Description(hostname)),
        scheme = Some("bearer"),
        bearerFormat = Some("JWT")
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
            schema = Some(convertJValueSchemaToSchemaJson(doc.typed_request_body)),
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
            schema = Some(convertJValueSchemaToSchemaJson(doc.typed_success_response_body)),
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
   * Converts a JValue that is already a JSON Schema into a SchemaJson case class.
   *
   * The typed_request_body and typed_success_response_body fields from ResourceDocJson
   * are already JSON Schemas produced by JSONFactory1_4_0.translateEntity(),
   * so we convert them directly rather than inferring a schema from example data.
   */
  private def convertJValueSchemaToSchemaJson(schema: JValue): SchemaJson = {
    schema match {
      case JObject(fields) =>
        val fieldMap = fields.map(f => f.name -> f.value).toMap

        val schemaType = fieldMap.get("type").collect { case JString(t) => t }
        val format = fieldMap.get("format").collect { case JString(f) => f }

        val `enum` = fieldMap.get("enum").collect {
          case JArray(values) => values
        }

        val properties = fieldMap.get("properties").collect {
          case JObject(props) =>
            props.map { case JField(name, value) =>
              name -> convertJValueSchemaToSchemaJson(value)
            }.toMap
        }

        val items = fieldMap.get("items").map(convertJValueSchemaToSchemaJson)

        val required = fieldMap.get("required").collect {
          case JArray(values) => values.collect { case JString(v) => v }
        }

        // Extract array validation constraints
        // Note: For GeoJSON cadastral coordinates, we enforce 2D only (minItems: 2, maxItems: 2)
        // This ensures coordinate dimension consistency and simplifies API usage
        val minItems = fieldMap.get("minItems").collect { case JInt(v) => v.toInt }
        val maxItems = fieldMap.get("maxItems").collect { case JInt(v) => v.toInt }

        SchemaJson(
          `type` = schemaType,
          format = format,
          properties = properties,
          items = items,
          required = required,
          `enum` = `enum`,
          minItems = minItems,
          maxItems = maxItems
        )

      case _ =>
        SchemaJson(`type` = Some("object"))
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
    doc.error_response_bodies.exists(_.contains("AuthenticatedUserIsRequired")) ||
    doc.error_response_bodies.exists(_.contains("ApplicationNotIdentified")) ||
    doc.roles.nonEmpty ||
    doc.description.toLowerCase.contains("authentication is required") ||
    doc.description.toLowerCase.contains("user must be logged in") ||
    doc.description.toLowerCase.contains("application access is required")
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
#!/usr/bin/env scala

/**
 * OpenAPI 3.1 Exporter for OBP API v6.0.0
 * 
 * This script extracts API documentation from the OBP API v6.0.0 codebase
 * and converts it to OpenAPI 3.1 format.
 * 
 * Usage:
 * scala OpenAPI31Exporter.scala [output_file]
 * 
 * If no output file is specified, it writes to stdout.
 */

import scala.io.Source
import scala.util.matching.Regex
import java.io.{File, PrintWriter}
import scala.collection.mutable.ListBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

case class ApiEndpoint(
  name: String,
  method: String,
  path: String,
  summary: String,
  description: String,
  requestBody: Option[String],
  responseBody: Option[String],
  errorCodes: List[String],
  tags: List[String],
  roles: List[String] = List.empty
)

case class JsonSchema(
  name: String,
  properties: Map[String, Any],
  required: List[String] = List.empty,
  description: Option[String] = None
)

object OpenAPI31Exporter {
  
  def main(args: Array[String]): Unit = {
    val outputFile = if (args.length > 0) Some(args(0)) else None
    val projectRoot = findProjectRoot()
    
    println(s"Extracting API documentation from: $projectRoot")
    val endpoints = extractEndpoints(projectRoot)
    val schemas = extractSchemas(projectRoot)
    
    val openApiYaml = generateOpenAPI31(endpoints, schemas)
    
    outputFile match {
      case Some(file) =>
        val writer = new PrintWriter(new File(file))
        try {
          writer.write(openApiYaml)
          println(s"OpenAPI 3.1 documentation written to: $file")
        } finally {
          writer.close()
        }
      case None =>
        println(openApiYaml)
    }
  }
  
  def findProjectRoot(): String = {
    val currentDir = new File(".")
    val candidates = List(
      "./obp-api/src/main/scala/code/api/v6_0_0",
      "../obp-api/src/main/scala/code/api/v6_0_0",
      "./OBP-API/obp-api/src/main/scala/code/api/v6_0_0"
    )
    
    candidates.find(path => new File(path).exists()) match {
      case Some(path) => new File(path).getParentFile.getParentFile.getParentFile.getParentFile.getParentFile.getAbsolutePath
      case None => 
        throw new RuntimeException("Could not find OBP API project root. Please run from the project directory.")
    }
  }
  
  def extractEndpoints(projectRoot: String): List[ApiEndpoint] = {
    val apiMethodsFile = new File(s"$projectRoot/obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala")
    val endpoints = ListBuffer[ApiEndpoint]()
    
    if (!apiMethodsFile.exists()) {
      throw new RuntimeException(s"APIMethods600.scala not found at: ${apiMethodsFile.getAbsolutePath}")
    }
    
    val content = Source.fromFile(apiMethodsFile).getLines().mkString("\n")
    
    // Extract ResourceDoc definitions
    val resourceDocPattern = """staticResourceDocs \+= ResourceDoc\(\s*([^,]+),\s*[^,]+,\s*[^,]+,\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*s?"""([^"]*(?:"[^"]*"[^"]*)*?)""",\s*([^,]+),\s*([^,]+),\s*List\(([^)]*)\),\s*List\(([^)]*)\)(?:,\s*Some\(List\(([^)]*)\)))?\s*\)""".r
    
    resourceDocPattern.findAllMatchIn(content).foreach { m =>
      val endpointName = m.group(1).trim
      val method = m.group(2).trim
      val path = m.group(3).trim
      val summary = m.group(4).trim
      val description = cleanDescription(m.group(5))
      val requestBodyRef = m.group(6).trim
      val responseBodyRef = m.group(7).trim
      val errorCodes = parseList(m.group(8))
      val tags = parseList(m.group(9))
      val roles = if (m.group(10) != null) parseList(m.group(10)) else List.empty
      
      endpoints += ApiEndpoint(
        name = endpointName,
        method = method,
        path = path,
        summary = summary,
        description = description,
        requestBody = if (requestBodyRef != "EmptyBody") Some(requestBodyRef) else None,
        responseBody = Some(responseBodyRef),
        errorCodes = errorCodes,
        tags = tags,
        roles = roles
      )
    }
    
    endpoints.toList
  }
  
  def extractSchemas(projectRoot: String): List[JsonSchema] = {
    val jsonFactoryFile = new File(s"$projectRoot/obp-api/src/main/scala/code/api/v6_0_0/JSONFactory6.0.0.scala")
    val schemas = ListBuffer[JsonSchema]()
    
    if (!jsonFactoryFile.exists()) {
      println(s"Warning: JSONFactory6.0.0.scala not found at: ${jsonFactoryFile.getAbsolutePath}")
      return schemas.toList
    }
    
    val content = Source.fromFile(jsonFactoryFile).getLines().mkString("\n")
    
    // Extract case class definitions
    val caseClassPattern = """case class ([A-Za-z0-9_]+)\(\s*(.*?)\s*\)""".r
    
    caseClassPattern.findAllMatchIn(content).foreach { m =>
      val className = m.group(1)
      val fieldsStr = m.group(2)
      
      val properties = parseFields(fieldsStr)
      val required = properties.filter(_._2.asInstanceOf[Map[String, Any]].get("nullable").isEmpty).keys.toList
      
      schemas += JsonSchema(
        name = className,
        properties = properties,
        required = required,
        description = Some(s"Schema for $className")
      )
    }
    
    schemas.toList
  }
  
  def parseFields(fieldsStr: String): Map[String, Any] = {
    val properties = scala.collection.mutable.Map[String, Any]()
    
    if (fieldsStr.trim.nonEmpty) {
      val fieldPattern = """([a-zA-Z_][a-zA-Z0-9_]*)\s*:\s*([^,]+)""".r
      
      fieldPattern.findAllMatchIn(fieldsStr).foreach { m =>
        val fieldName = m.group(1).trim
        val fieldType = m.group(2).trim
        
        val (schemaType, nullable) = mapScalaTypeToJsonSchema(fieldType)
        
        val fieldSchema = scala.collection.mutable.Map[String, Any](
          "type" -> schemaType
        )
        
        if (nullable) {
          fieldSchema += "nullable" -> true
        }
        
        if (fieldType.contains("Date")) {
          fieldSchema += "format" -> "date-time"
        }
        
        properties += fieldName -> fieldSchema.toMap
      }
    }
    
    properties.toMap
  }
  
  def mapScalaTypeToJsonSchema(scalaType: String): (String, Boolean) = {
    val cleanType = scalaType.replaceAll("Option\\[(.*)\\]", "$1").trim
    val nullable = scalaType.contains("Option[")
    
    val jsonType = cleanType match {
      case t if t.startsWith("String") => "string"
      case t if t.startsWith("Int") || t.startsWith("Long") => "integer"
      case t if t.startsWith("Double") || t.startsWith("Float") || t.startsWith("BigDecimal") => "number"
      case t if t.startsWith("Boolean") => "boolean"
      case t if t.startsWith("List[") || t.startsWith("Array[") => "array"
      case t if t.contains("Date") => "string"
      case _ => "object"
    }
    
    (jsonType, nullable)
  }
  
  def cleanDescription(desc: String): String = {
    desc.replaceAll("\\|", "")
       .replaceAll("\\$\\{[^}]+\\}", "")
       .replaceAll("\\s+", " ")
       .trim
  }
  
  def parseList(listStr: String): List[String] = {
    if (listStr.trim.isEmpty) List.empty
    else listStr.split(",").map(_.trim.replaceAll("[\"$]", "")).filter(_.nonEmpty).toList
  }
  
  def generateOpenAPI31(endpoints: List[ApiEndpoint], schemas: List[JsonSchema]): String = {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    
    val yaml = new StringBuilder()
    
    // OpenAPI header
    yaml.append("openapi: 3.1.0\n")
    yaml.append("\n")
    yaml.append("info:\n")
    yaml.append("  title: Open Bank Project API v6.0.0\n")
    yaml.append("  version: 6.0.0\n")
    yaml.append("  description: |\n")
    yaml.append("    The Open Bank Project API v6.0.0 provides standardized banking APIs.\n")
    yaml.append("    \n")
    yaml.append("    This specification was automatically generated from the OBP API codebase.\n")
    yaml.append(s"    Generated on: $timestamp\n")
    yaml.append("    \n")
    yaml.append("    For more information, visit: https://github.com/OpenBankProject/OBP-API\n")
    yaml.append("  contact:\n")
    yaml.append("    name: Open Bank Project\n")
    yaml.append("    url: https://www.openbankproject.com\n")
    yaml.append("    email: contact@tesobe.com\n")
    yaml.append("  license:\n")
    yaml.append("    name: AGPL v3\n")
    yaml.append("    url: https://www.gnu.org/licenses/agpl-3.0.html\n")
    yaml.append("\n")
    
    // Servers
    yaml.append("servers:\n")
    yaml.append("  - url: https://api.openbankproject.com\n")
    yaml.append("    description: Production server\n")
    yaml.append("  - url: https://apisandbox.openbankproject.com\n")
    yaml.append("    description: Sandbox server\n")
    yaml.append("\n")
    
    // Security schemes
    yaml.append("components:\n")
    yaml.append("  securitySchemes:\n")
    yaml.append("    DirectLogin:\n")
    yaml.append("      type: apiKey\n")
    yaml.append("      in: header\n")
    yaml.append("      name: Authorization\n")
    yaml.append("      description: Direct Login token authentication\n")
    yaml.append("    GatewayLogin:\n")
    yaml.append("      type: apiKey\n")
    yaml.append("      in: header\n")
    yaml.append("      name: Authorization\n")
    yaml.append("      description: Gateway Login token authentication\n")
    yaml.append("    OAuth2:\n")
    yaml.append("      type: oauth2\n")
    yaml.append("      flows:\n")
    yaml.append("        authorizationCode:\n")
    yaml.append("          authorizationUrl: /oauth/authorize\n")
    yaml.append("          tokenUrl: /oauth/token\n")
    yaml.append("          scopes: {}\n")
    yaml.append("\n")
    
    // Schemas
    if (schemas.nonEmpty) {
      yaml.append("  schemas:\n")
      schemas.foreach { schema =>
        yaml.append(s"    ${schema.name}:\n")
        yaml.append("      type: object\n")
        if (schema.description.isDefined) {
          yaml.append(s"      description: ${schema.description.get}\n")
        }
        if (schema.required.nonEmpty) {
          yaml.append("      required:\n")
          schema.required.foreach { field =>
            yaml.append(s"        - $field\n")
          }
        }
        if (schema.properties.nonEmpty) {
          yaml.append("      properties:\n")
          schema.properties.foreach { case (name, propSchema) =>
            val props = propSchema.asInstanceOf[Map[String, Any]]
            yaml.append(s"        $name:\n")
            yaml.append(s"          type: ${props("type")}\n")
            props.get("format").foreach { format =>
              yaml.append(s"          format: $format\n")
            }
            props.get("nullable").foreach { _ =>
              yaml.append("          nullable: true\n")
            }
          }
        }
        yaml.append("\n")
      }
    }
    
    // Paths
    yaml.append("paths:\n")
    
    val groupedEndpoints = endpoints.groupBy(_.path)
    groupedEndpoints.toSeq.sortBy(_._1).foreach { case (path, pathEndpoints) =>
      val openApiPath = convertPathToOpenAPI(path)
      yaml.append(s"  $openApiPath:\n")
      
      pathEndpoints.sortBy(_.method).foreach { endpoint =>
        val method = endpoint.method.toLowerCase
        yaml.append(s"    $method:\n")
        yaml.append(s"      summary: ${endpoint.summary}\n")
        yaml.append(s"      operationId: ${endpoint.name}\n")
        
        if (endpoint.description.nonEmpty) {
          yaml.append("      description: |\n")
          endpoint.description.split("\n").foreach { line =>
            yaml.append(s"        $line\n")
          }
        }
        
        if (endpoint.tags.nonEmpty) {
          yaml.append("      tags:\n")
          endpoint.tags.foreach { tag =>
            yaml.append(s"        - ${tag.replaceAll("apiTag", "")}\n")
          }
        }
        
        // Parameters (path parameters)
        val pathParams = extractPathParameters(path)
        if (pathParams.nonEmpty) {
          yaml.append("      parameters:\n")
          pathParams.foreach { param =>
            yaml.append(s"        - name: $param\n")
            yaml.append("          in: path\n")
            yaml.append("          required: true\n")
            yaml.append("          schema:\n")
            yaml.append("            type: string\n")
          }
        }
        
        // Request body
        if (endpoint.requestBody.isDefined && method != "get" && method != "delete") {
          yaml.append("      requestBody:\n")
          yaml.append("        required: true\n")
          yaml.append("        content:\n")
          yaml.append("          application/json:\n")
          yaml.append("            schema:\n")
          yaml.append(s"              $$ref: '#/components/schemas/${endpoint.requestBody.get}'\n")
        }
        
        // Responses
        yaml.append("      responses:\n")
        yaml.append("        '200':\n")
        yaml.append("          description: Success\n")
        if (endpoint.responseBody.isDefined) {
          yaml.append("          content:\n")
          yaml.append("            application/json:\n")
          yaml.append("              schema:\n")
          yaml.append(s"                $$ref: '#/components/schemas/${endpoint.responseBody.get}'\n")
        }
        
        // Error responses
        if (endpoint.errorCodes.nonEmpty) {
          endpoint.errorCodes.filter(_.contains("400")).foreach { _ =>
            yaml.append("        '400':\n")
            yaml.append("          description: Bad Request\n")
          }
          endpoint.errorCodes.filter(_.contains("401")).foreach { _ =>
            yaml.append("        '401':\n")
            yaml.append("          description: Unauthorized\n")
          }
          endpoint.errorCodes.filter(_.contains("404")).foreach { _ =>
            yaml.append("        '404':\n")
            yaml.append("          description: Not Found\n")
          }
        }
        
        // Security
        if (endpoint.roles.nonEmpty || !endpoint.errorCodes.exists(_.contains("UserNotLoggedIn"))) {
          yaml.append("      security:\n")
          yaml.append("        - DirectLogin: []\n")
          yaml.append("        - GatewayLogin: []\n")
          yaml.append("        - OAuth2: []\n")
        }
        
        yaml.append("\n")
      }
    }
    
    yaml.toString()
  }
  
  def convertPathToOpenAPI(obpPath: String): String = {
    obpPath.replaceAll("([A-Z_]+)", "{$1}")
          .replaceAll("\\{([A-Z_]+)\\}", "{${1.toLowerCase}}")
          .replaceAll("_", "-")
  }
  
  def extractPathParameters(path: String): List[String] = {
    val paramPattern = """([A-Z_]+)""".r
    paramPattern.findAllMatchIn(path).map(_.group(1).toLowerCase.replace("_", "-")).toList
  }
}
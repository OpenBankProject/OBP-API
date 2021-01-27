package code.util

import java.nio.charset.Charset
import java.util.{Set => JSet}

import code.api.util.CallContext
import code.validation.{JsonValidation, JsonSchemaValidationProvider}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.google.common.hash.Hashing
import com.networknt.schema.{JsonSchema, JsonSchemaFactory, SpecVersionDetector, ValidationMessage}
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils

object JsonSchemaUtil {
  private val mapper = new ObjectMapper
  // cache JsonSchema object, the key is schema string hash value
  private val ttlCache = TTLCache.apply[JsonSchema](Int.MaxValue)
  private val hashFunction = Hashing.sha512()

  /**
   * validate json-schema, whether it is a correct structure of json-schema
   */
  lazy val validateSchema: String => JSet[ValidationMessage] = {
    val schemaJson: JsonNode = mapper.readTree(this.metaSchema)
    val factory = JsonSchemaFactory.getInstance(SpecVersionDetector.detect(schemaJson))
    val jsonSchema = factory.getSchema(schemaJson)

    (schema: String) => jsonSchema.validate(mapper.readTree(schema))
  }

  def validateJson(jsonSchema: String, jsonContent: String): JSet[ValidationMessage] = {
    val jsonSchemaCacheKey = hashFunction.hashString(jsonSchema, Charset.forName("UTF-8")).toString

    val schema: JsonSchema = this.ttlCache.getOrElseUpdate(jsonSchemaCacheKey, () => {
      val jsonNode: JsonNode = mapper.readTree(jsonSchema)
      val factory = JsonSchemaFactory.getInstance(SpecVersionDetector.detect(jsonNode))
      factory.getSchema(jsonNode)
    })

    schema.validate(mapper.readTree(jsonContent))
  }

  def validateRequest(callContext: Option[CallContext])(operationIdBuilder: => String): Option[String] = {
    // validate request payload with json-schema
    for {
      _ <- callContext.filter(it => it.verb == "POST" || it.verb == "PUT")
      requestBody <- callContext.flatMap(_.httpBody)
      JsonValidation(_, jsonSchema) <- JsonSchemaValidationProvider.validationProvider.vend.getByOperationId(operationIdBuilder)
      errorSet = JsonSchemaUtil.validateJson(jsonSchema, requestBody)
      if CollectionUtils.isNotEmpty(errorSet)
      errorInfo = StringUtils.join(errorSet, "; ")
    } yield errorInfo
  }

  /**
   * a meta schema to validate other json-schema
   */
  private val metaSchema =
    """
      |{
      |    "$id": "http://example.com/example.json",
      |    "$schema": "http://json-schema.org/draft-07/schema",
      |    "description": "The 'json-schema' for validate 'json-schema'.",
      |    "title": "The json schema for validate other json-schema",
      |    "type": "object",
      |    "required": [
      |        "$schema",
      |        "type",
      |        "title",
      |        "description",
      |        "required",
      |        "properties"
      |    ],
      |    "properties": {
      |        "$schema": {
      |            "type": "string",
      |            "format": "uri",
      |            "examples": [
      |                "http://json-schema.org/draft-07/schema"
      |            ]
      |        },
      |        "$id": {
      |            "type": "string",
      |            "format": "uri",
      |            "examples": [
      |                "http://example.com/example.json"
      |            ]
      |        },
      |        "type": {
      |            "type": "string",
      |            "examples": [
      |                "object"
      |            ],
      |            "enum": [
      |                "object",
      |                "string",
      |                "array",
      |                "boolean",
      |                "number",
      |                "integer",
      |                "null"
      |            ]
      |        },
      |        "title": {
      |            "type": "string",
      |            "examples": [
      |                "The root schema"
      |            ]
      |        },
      |        "description": {
      |            "type": "string",
      |            "examples": [
      |                "The root schema comprises the entire JSON document."
      |            ]
      |        },
      |        "examples": {
      |            "type": "array",
      |            "additionalItems": true,
      |            "items": {
      |                "anyOf" : [
      |                    {"type" : "object"},
      |                    {"type": "string"},
      |                    {"type" : "array"},
      |                    {"type" : "boolean"},
      |                    {"type" : "number"},
      |                    {"type" : "integer"},
      |                    {"type" : "null"}
      |                ]
      |            }
      |        },
      |        "required": {
      |            "type": "array",
      |            "additionalItems": true,
      |            "items": {
      |                "type": "string"
      |            }
      |        },
      |        "properties": {
      |            "type": "object",
      |            "minProperties": 1,
      |            "required": [],
      |            "properties":{},
      |            "patternProperties": {
      |                ".+": {
      |                    "type": "object",
      |                    "required": [
      |                        "type"
      |                    ],
      |                    "properties": {
      |                        "type": {
      |                            "type": "string",
      |                            "examples": [
      |                                "object"
      |                            ],
      |                            "enum": [
      |                                "object",
      |                                "string",
      |                                "array",
      |                                "boolean",
      |                                "number",
      |                                "integer",
      |                                "null"
      |                            ]
      |                        },
      |                        "title": {
      |                            "type": "string",
      |                            "examples": [
      |                                "The root schema"
      |                            ]
      |                        },
      |                        "description": {
      |                            "type": "string",
      |                            "examples": [
      |                                "The root schema comprises the entire JSON document."
      |                            ]
      |                        },
      |                        "examples": {
      |                            "type": "array",
      |                            "additionalItems": true,
      |                            "items": {
      |                                "anyOf" : [
      |                                    { "type" : "object"},
      |                                    {"type": "string"},
      |                                    { "type" : "array"},
      |                                    { "type" : "boolean"},
      |                                    { "type" : "number"},
      |                                    { "type" : "integer"},
      |                                    { "type" : "null"}
      |                                ]
      |                            }
      |                        }
      |                    }
      |                }
      |            },
      |            "additionalProperties": true
      |        },
      |        "additionalProperties": {
      |            "type": "boolean",
      |            "default": false,
      |            "examples": [
      |                true
      |            ]
      |        }
      |    },
      |    "additionalProperties": true
      |}
      |""".stripMargin
}

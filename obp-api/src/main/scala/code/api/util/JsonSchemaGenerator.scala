package code.api.util

import org.json4s._
import code.api.util.APIUtil.MessageDoc
import com.openbankproject.commons.util.{JsonSchemaGeneratorTypes, ReflectUtils}
import org.json4s.JsonDSL._

import scala.reflect.runtime.universe._

/**
 * Utility for generating JSON Schema from Scala case classes
 * Used by the message-docs JSON Schema endpoint to provide machine-readable schemas
 * for adapter code generation in any language.
 */
object JsonSchemaGenerator {

  /**
   * Convert a list of MessageDoc to a complete JSON Schema document
   */
  def messageDocsToJsonSchema(messageDocs: List[MessageDoc], connectorName: String): JObject = {
    val allDefinitions = scala.collection.mutable.Map[String, JObject]()
    
    val messages = messageDocs.map { messageDoc =>
      val outboundType = ReflectUtils.getType(messageDoc.exampleOutboundMessage)
      val inboundType = ReflectUtils.getType(messageDoc.exampleInboundMessage)
      
      // Collect all nested type definitions
      collectDefinitions(outboundType, allDefinitions)
      collectDefinitions(inboundType, allDefinitions)
      
      ("process" -> messageDoc.process) ~
      ("description" -> messageDoc.description) ~
      ("message_format" -> messageDoc.messageFormat) ~
      ("outbound_topic" -> messageDoc.outboundTopic) ~
      ("inbound_topic" -> messageDoc.inboundTopic) ~
      ("outbound_schema" -> typeToJsonSchema(outboundType)) ~
      ("inbound_schema" -> typeToJsonSchema(inboundType)) ~
      ("adapter_implementation" -> messageDoc.adapterImplementation.map { impl =>
        ("group" -> impl.group) ~
        ("suggested_order" -> JInt(BigInt(impl.suggestedOrder)))
      })
    }
    
    ("$schema" -> "http://json-schema.org/draft-07/schema#") ~
    ("title" -> s"$connectorName Message Schemas") ~
    ("description" -> s"JSON Schema definitions for $connectorName connector messages") ~
    ("type" -> "object") ~
    ("properties" -> (
      ("messages" -> (
        ("type" -> "array") ~
        ("items" -> messages)
      ))
    )) ~
    ("definitions" -> JObject(allDefinitions.toList.map { case (name, schema) => JField(name, schema) }))
  }
  
  /**
   * Convert a Scala Type to JSON Schema
   */
  private def typeToJsonSchema(tpe: Type): JObject = {
    tpe match {
      case t if t =:= JsonSchemaGeneratorTypes.tString =>
        ("type" -> "string")
        
      case t if t =:= JsonSchemaGeneratorTypes.tInt =>
        ("type" -> "integer") ~ ("format" -> "int32")
        
      case t if t =:= JsonSchemaGeneratorTypes.tLong =>
        ("type" -> "integer") ~ ("format" -> "int64")
        
      case t if t =:= JsonSchemaGeneratorTypes.tDouble =>
        ("type" -> "number") ~ ("format" -> "double")
        
      case t if t =:= JsonSchemaGeneratorTypes.tFloat =>
        ("type" -> "number") ~ ("format" -> "float")
        
      case t if t =:= JsonSchemaGeneratorTypes.tBigDecimal =>
        ("type" -> "number")
        
      case t if t =:= JsonSchemaGeneratorTypes.tBoolean =>
        ("type" -> "boolean")
        
      case t if t =:= JsonSchemaGeneratorTypes.tJavaUtilDate =>
        ("type" -> "string") ~ ("format" -> "date-time")
        
      case t if t <:< JsonSchemaGeneratorTypes.tOptionWildcard =>
        val innerType = t.typeArgs.head
        typeToJsonSchema(innerType)
        
      case t if t <:< JsonSchemaGeneratorTypes.tListWildcard || t <:< JsonSchemaGeneratorTypes.tSeqWildcard =>
        val itemType = t.typeArgs.head
        ("type" -> "array") ~ ("items" -> typeToJsonSchema(itemType))
        
      case t if t <:< JsonSchemaGeneratorTypes.tMapWildcardWildcard =>
        ("type" -> "object") ~ ("additionalProperties" -> typeToJsonSchema(t.typeArgs.last))
        
      case t if isEnumType(t) =>
        val enumValues = getEnumValues(t)
        ("type" -> "string") ~ ("enum" -> JArray(enumValues.map(JString(_))))
        
      case t if isCaseClass(t) =>
        val typeName = getTypeName(t)
        ("$ref" -> s"#/definitions/$typeName")
        
      case _ =>
        // Fallback for unknown types
        ("type" -> "object")
    }
  }
  
  /**
   * Collect all type definitions recursively
   */
  private def collectDefinitions(tpe: Type, definitions: scala.collection.mutable.Map[String, JObject]): Unit = {
    if (!isCaseClass(tpe) || isPrimitiveOrKnown(tpe)) return
    
    val typeName = getTypeName(tpe)
    if (definitions.contains(typeName)) return
    
    val schema = caseClassToJsonSchema(tpe, definitions)
    definitions += (typeName -> schema)
  }
  
  /**
   * Convert a case class to JSON Schema definition
   */
  private def caseClassToJsonSchema(tpe: Type, definitions: scala.collection.mutable.Map[String, JObject]): JObject = {
    try {
      val constructor = ReflectUtils.getPrimaryConstructor(tpe)
      val params = constructor.paramLists.flatten
      
      val properties = params.map { param =>
        val paramName = param.name.toString
        val paramType = param.typeSignature
        
        // Recursively collect nested definitions
        if (isCaseClass(paramType) && !isPrimitiveOrKnown(paramType)) {
          collectDefinitions(paramType, definitions)
        }
        
        // Handle List/Seq inner types
        if (paramType <:< JsonSchemaGeneratorTypes.tListWildcard || paramType <:< JsonSchemaGeneratorTypes.tSeqWildcard) {
          val innerType = paramType.typeArgs.headOption.getOrElse(JsonSchemaGeneratorTypes.tAny)
          if (isCaseClass(innerType) && !isPrimitiveOrKnown(innerType)) {
            collectDefinitions(innerType, definitions)
          }
        }
        
        // Handle Option inner types
        if (paramType <:< JsonSchemaGeneratorTypes.tOptionWildcard) {
          val innerType = paramType.typeArgs.headOption.getOrElse(JsonSchemaGeneratorTypes.tAny)
          if (isCaseClass(innerType) && !isPrimitiveOrKnown(innerType)) {
            collectDefinitions(innerType, definitions)
          }
        }
        
        val propertySchema = typeToJsonSchema(paramType)
        
        // Add description from annotations if available
        val description = getFieldDescription(param)
        val schemaWithDesc = if (description.nonEmpty) {
          propertySchema ~ ("description" -> description)
        } else {
          propertySchema
        }
        
        JField(paramName, schemaWithDesc)
      }
      
      // Determine required fields (non-Option types)
      val requiredFields = params
        .filterNot(p => p.typeSignature <:< JsonSchemaGeneratorTypes.tOptionWildcard)
        .map(_.name.toString)
      
      val baseSchema = ("type" -> "object") ~ ("properties" -> JObject(properties))
      
      if (requiredFields.nonEmpty) {
        baseSchema ~ ("required" -> JArray(requiredFields.map(JString(_))))
      } else {
        baseSchema
      }
    } catch {
      case e: Exception =>
        // Fallback for types we can't introspect
        ("type" -> "object") ~ ("description" -> s"Schema generation failed: ${e.getMessage}")
    }
  }
  
  /**
   * Get readable type name for schema definitions
   */
  private def getTypeName(tpe: Type): String = {
    val fullName = tpe.typeSymbol.fullName
    // Remove package prefix, keep only class name
    val simpleName = fullName.split("\\.").last
    // Handle nested types
    simpleName.replace("$", "")
  }
  
  /**
   * Check if type is a case class
   */
  private def isCaseClass(tpe: Type): Boolean = {
    tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isCaseClass
  }
  
  /**
   * Check if type is an enum
   */
  private def isEnumType(tpe: Type): Boolean = {
    // Check for common enum patterns in OBP
    val typeName = tpe.typeSymbol.fullName
    typeName.contains("enums.") || 
    (tpe.baseClasses.exists(_.fullName.contains("Enumeration")) && tpe.typeSymbol.isModuleClass)
  }
  
  /**
   * Get enum values if type is an enum
   */
  private def getEnumValues(tpe: Type): List[String] = {
    try {
      // Try to get enum values through reflection
      // This is a simplified version - might need enhancement for complex enums
      List.empty[String] // Placeholder - enum extraction can be complex
    } catch {
      case _: Exception => List.empty[String]
    }
  }
  
  /**
   * Check if type is primitive or commonly known type that shouldn't be expanded
   */
  private def isPrimitiveOrKnown(tpe: Type): Boolean = {
    tpe =:= JsonSchemaGeneratorTypes.tString ||
    tpe =:= JsonSchemaGeneratorTypes.tInt ||
    tpe =:= JsonSchemaGeneratorTypes.tLong ||
    tpe =:= JsonSchemaGeneratorTypes.tDouble ||
    tpe =:= JsonSchemaGeneratorTypes.tFloat ||
    tpe =:= JsonSchemaGeneratorTypes.tBoolean ||
    tpe =:= JsonSchemaGeneratorTypes.tBigDecimal ||
    tpe =:= JsonSchemaGeneratorTypes.tJavaUtilDate ||
    tpe <:< JsonSchemaGeneratorTypes.tOptionWildcard ||
    tpe <:< JsonSchemaGeneratorTypes.tListWildcard ||
    tpe <:< JsonSchemaGeneratorTypes.tSeqWildcard ||
    tpe <:< JsonSchemaGeneratorTypes.tMapWildcardWildcard
  }
  
  /**
   * Extract field description from annotations or scaladoc (simplified)
   */
  private def getFieldDescription(param: Symbol): String = {
    // This is a placeholder - extracting scaladoc is complex
    // Could be enhanced to read annotations or scaladoc comments
    ""
  }
  
  /**
   * Generate a simplified single-message JSON Schema (for testing)
   */
  def generateSchemaForType(tpe: Type): JObject = {
    val definitions = scala.collection.mutable.Map[String, JObject]()
    collectDefinitions(tpe, definitions)
    
    ("$schema" -> "http://json-schema.org/draft-07/schema#") ~
    typeToJsonSchema(tpe) ~
    ("definitions" -> JObject(definitions.toList.map { case (name, schema) => JField(name, schema) }))
  }
}
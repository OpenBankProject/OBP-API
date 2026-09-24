/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

package code.api.util

import org.json4s._
import code.api.util.APIUtil.MessageDoc
import com.openbankproject.commons.util.ReflectUtils
import com.tesobe.CacheKeyFromArguments
import org.json4s.JsonDSL._

import scala.concurrent.duration._
import scala.reflect.runtime.universe._

/**
 * Utility for generating JSON Schema from Scala case classes
 * Used by the message-docs JSON Schema endpoint to provide machine-readable schemas
 * for adapter code generation in any language.
 */
object JsonSchemaGenerator {

  /**
   * Convert a list of MessageDoc to a complete JSON Schema document.
   *
   * Memoized in-process, keyed by connectorName: for a given connector the message docs
   * (and therefore the schema) are static for the lifetime of the JVM, but building it
   * walks every message type's full field tree via Scala runtime reflection (`<:<`/`=:=`
   * subtype checks), which is expensive and -- unlike a plain field lookup -- leaves behind
   * long-lived reflection bookkeeping objects (TypeConstraint/UndoPair/Symbol) that don't
   * get reclaimed promptly. Recomputing this on every request under sustained polling keeps
   * adding them and grows old-gen heap usage. The caller (Http4s600) also has a
   * Redis-backed cache in front of this, but that one silently falls through to a full
   * recompute if Redis is unreachable or slow -- this in-memory layer doesn't depend on
   * Redis at all, so it stays a working safety net even when Redis is the one struggling.
   */
  def messageDocsToJsonSchema(messageDocs: List[MessageDoc], connectorName: String): JObject = {
    // This 3-tuple of random UUIDs is a placeholder only -- CacheKeyFromArguments is a macro
    // that replaces it at compile time with a real key derived from this method's owner,
    // name and arguments (regardless of this method's own arity; the convention throughout
    // this codebase is always a 3-tuple here). See:
    // https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
    var cacheKey = (java.util.UUID.randomUUID().toString, java.util.UUID.randomUUID().toString, java.util.UUID.randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      code.api.cache.Caching.memoizeSyncWithImMemory(Some(cacheKey.toString()))(100000.days) {
        messageDocsToJsonSchemaUncached(messageDocs, connectorName)
      }
    }
  }

  private def messageDocsToJsonSchemaUncached(messageDocs: List[MessageDoc], connectorName: String): JObject = {
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
      case t if t =:= typeOf[String] =>
        ("type" -> "string")
        
      case t if t =:= typeOf[Int] =>
        ("type" -> "integer") ~ ("format" -> "int32")
        
      case t if t =:= typeOf[Long] =>
        ("type" -> "integer") ~ ("format" -> "int64")
        
      case t if t =:= typeOf[Double] =>
        ("type" -> "number") ~ ("format" -> "double")
        
      case t if t =:= typeOf[Float] =>
        ("type" -> "number") ~ ("format" -> "float")
        
      case t if t =:= typeOf[BigDecimal] || t =:= typeOf[scala.math.BigDecimal] =>
        ("type" -> "number")
        
      case t if t =:= typeOf[Boolean] =>
        ("type" -> "boolean")
        
      case t if t =:= typeOf[java.util.Date] =>
        ("type" -> "string") ~ ("format" -> "date-time")
        
      case t if t <:< typeOf[Option[_]] =>
        val innerType = t.typeArgs.head
        typeToJsonSchema(innerType)
        
      case t if t <:< typeOf[List[_]] || t <:< typeOf[Seq[_]] || t <:< typeOf[scala.collection.immutable.List[_]] =>
        val itemType = t.typeArgs.head
        ("type" -> "array") ~ ("items" -> typeToJsonSchema(itemType))
        
      case t if t <:< typeOf[Map[_, _]] =>
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
        if (paramType <:< typeOf[List[_]] || paramType <:< typeOf[Seq[_]]) {
          val innerType = paramType.typeArgs.headOption.getOrElse(typeOf[Any])
          if (isCaseClass(innerType) && !isPrimitiveOrKnown(innerType)) {
            collectDefinitions(innerType, definitions)
          }
        }
        
        // Handle Option inner types
        if (paramType <:< typeOf[Option[_]]) {
          val innerType = paramType.typeArgs.headOption.getOrElse(typeOf[Any])
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
        .filterNot(p => p.typeSignature <:< typeOf[Option[_]])
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
    tpe =:= typeOf[String] ||
    tpe =:= typeOf[Int] ||
    tpe =:= typeOf[Long] ||
    tpe =:= typeOf[Double] ||
    tpe =:= typeOf[Float] ||
    tpe =:= typeOf[Boolean] ||
    tpe =:= typeOf[BigDecimal] ||
    tpe =:= typeOf[java.util.Date] ||
    tpe <:< typeOf[Option[_]] ||
    tpe <:< typeOf[List[_]] ||
    tpe <:< typeOf[Seq[_]] ||
    tpe <:< typeOf[Map[_, _]]
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
  def generateSchemaForType[T: TypeTag]: JObject = {
    val tpe = typeOf[T]
    val definitions = scala.collection.mutable.Map[String, JObject]()
    collectDefinitions(tpe, definitions)
    
    ("$schema" -> "http://json-schema.org/draft-07/schema#") ~
    typeToJsonSchema(tpe) ~
    ("definitions" -> JObject(definitions.toList.map { case (name, schema) => JField(name, schema) }))
  }
}
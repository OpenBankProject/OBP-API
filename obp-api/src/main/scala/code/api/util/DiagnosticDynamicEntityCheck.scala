package code.api.util

import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.dynamicEntity.DynamicEntityT
import net.liftweb.json._
import org.apache.commons.lang3.StringUtils

/**
 * Diagnostic utility to identify dynamic entities with malformed boolean field examples
 * This helps troubleshoot Swagger generation issues where boolean fields have invalid example values
 */
object DiagnosticDynamicEntityCheck {

  case class BooleanFieldIssue(
    entityName: String,
    bankId: Option[String],
    fieldName: String,
    exampleValue: String,
    errorMessage: String
  )

  case class DiagnosticResult(
    issues: List[BooleanFieldIssue],
    scannedEntities: List[String]
  )

  /**
   * Check all dynamic entities for problematic boolean field examples
   * @return DiagnosticResult with issues found and list of scanned entities
   */
  def checkAllDynamicEntities(): DiagnosticResult = {
    val issues = scala.collection.mutable.ListBuffer[BooleanFieldIssue]()
    var dynamicEntities = List.empty[DynamicEntityT]

    try {
      dynamicEntities = NewStyle.function.getDynamicEntities(None, true)

      dynamicEntities.foreach { entity =>
        try {
          val dynamicEntityInfo = DynamicEntityInfo(
            entity.metadataJson,
            entity.entityName,
            entity.bankId,
            entity.hasPersonalEntity
          )

          // Check the entity definition
          val entityIssues = checkEntityDefinition(
            dynamicEntityInfo.entityName,
            dynamicEntityInfo.bankId,
            dynamicEntityInfo.definition
          )

          issues ++= entityIssues

          // Also check the raw metadata JSON for malformed example values
          try {
            // Parse the metadata to look for problematic patterns
            val metadataJson = parse(entity.metadataJson)
            // Look for any "example" fields that contain "{" which would indicate malformed JSON
            val exampleFields = metadataJson \\ "example"
            exampleFields.children.foreach {
              case JString(s) if s.contains("{") || s.contains("}") =>
                // This is likely a malformed JSON string in the example
                issues += BooleanFieldIssue(
                  entity.entityName,
                  entity.bankId,
                  "RAW_METADATA",
                  s,
                  s"Example value contains JSON-like characters which may cause parsing errors: '$s'"
                )
              case _ => // OK
            }
          } catch {
            case e: Exception =>
              // Ignore parsing errors here, will be caught elsewhere
          }

          // Try to generate the example to see if it fails (this is what Swagger does)
          try {
            val singleExample = dynamicEntityInfo.getSingleExampleWithoutId
            // Success - no issue
          } catch {
            case e: IllegalArgumentException =>
              // This catches boolean conversion errors like "{\"tok".toBoolean
              issues += BooleanFieldIssue(
                entity.entityName,
                entity.bankId,
                "EXAMPLE_GENERATION",
                "N/A",
                s"Failed to generate example (likely boolean conversion error): ${e.getMessage}"
              )
            case e: NumberFormatException =>
              // This catches integer/number conversion errors
              issues += BooleanFieldIssue(
                entity.entityName,
                entity.bankId,
                "EXAMPLE_GENERATION",
                "N/A",
                s"Failed to generate example (number format error): ${e.getMessage}"
              )
            case e: Exception =>
              issues += BooleanFieldIssue(
                entity.entityName,
                entity.bankId,
                "EXAMPLE_GENERATION",
                "N/A",
                s"Failed to generate example: ${e.getMessage}"
              )
          }

        } catch {
          case e: Exception =>
            issues += BooleanFieldIssue(
              entity.entityName,
              entity.bankId,
              "ENTITY_PROCESSING",
              "N/A",
              s"Failed to process entity: ${e.getMessage}"
            )
        }
      }

    } catch {
      case e: Exception =>
        issues += BooleanFieldIssue(
          "UNKNOWN",
          None,
          "FATAL_ERROR",
          "N/A",
          s"Fatal error during diagnostic check: ${e.getMessage}"
        )
    }

    val scannedEntityNames = dynamicEntities.map { entity =>
      val bankIdStr = entity.bankId.map(id => s"($id)").getOrElse("(SYSTEM)")
      s"${entity.entityName} $bankIdStr"
    }

    DiagnosticResult(issues.toList, scannedEntityNames)
  }

  /**
   * Check a single entity definition for boolean field issues
   */
  private def checkEntityDefinition(
    entityName: String,
    bankId: Option[String],
    definitionJson: String
  ): List[BooleanFieldIssue] = {

    val issues = scala.collection.mutable.ListBuffer[BooleanFieldIssue]()

    try {
      implicit val formats = DefaultFormats
      val json = parse(definitionJson)

      // Find the entity definition (it should be the first key in the JSON)
      val JObject(topLevelFields) = json

      topLevelFields.headOption.foreach { case JField(entityKey, entityDef) =>
        // Get properties
        val properties = entityDef \ "properties"

        properties match {
          case JObject(fields) =>
            fields.foreach { case JField(fieldName, fieldDef) =>
              val fieldType = (fieldDef \ "type") match {
                case JString(t) => Some(t)
                case _ => None
              }

              val example = fieldDef \ "example"

              // Check if this is a boolean field
              if (fieldType.contains("boolean")) {
                example match {
                  case JString(exampleStr) =>
                    // Try to convert to boolean exactly as the code does in DynamicEntityHelper
                    try {
                      val result = exampleStr.toLowerCase.toBoolean
                      // If it succeeds but isn't "true" or "false", it's still problematic
                      if (exampleStr.toLowerCase != "true" && exampleStr.toLowerCase != "false") {
                        issues += BooleanFieldIssue(
                          entityName,
                          bankId,
                          fieldName,
                          exampleStr,
                          s"Boolean field has invalid example value. Expected 'true' or 'false', got: '$exampleStr'. This will cause Swagger generation to fail."
                        )
                      }
                    } catch {
                      case e: IllegalArgumentException =>
                        issues += BooleanFieldIssue(
                          entityName,
                          bankId,
                          fieldName,
                          exampleStr,
                          s"Cannot convert example to boolean: ${e.getMessage}. This will cause Swagger generation to fail with 'expected boolean' error."
                        )
                      case e: Exception =>
                        issues += BooleanFieldIssue(
                          entityName,
                          bankId,
                          fieldName,
                          exampleStr,
                          s"Unexpected error converting example to boolean: ${e.getMessage}"
                        )
                    }

                  case JBool(boolValue) =>
                    // Boolean examples MUST be strings "true" or "false", not actual JSON booleans
                    // The code expects JString and calls .toBoolean on it
                    issues += BooleanFieldIssue(
                      entityName,
                      bankId,
                      fieldName,
                      boolValue.toString,
                      s"""Boolean field has JSON boolean value ($boolValue) instead of string. Expected string "true" or "false", got JSON boolean $boolValue. This will cause Swagger generation to fail with 'expected boolean' error. Update the entity definition to use string examples."""
                    )

                  case JNothing | JNull =>
                    issues += BooleanFieldIssue(
                      entityName,
                      bankId,
                      fieldName,
                      "null/missing",
                      "Boolean field has no example value"
                    )

                  case other =>
                    issues += BooleanFieldIssue(
                      entityName,
                      bankId,
                      fieldName,
                      other.toString,
                      s"Boolean field has unexpected example type: ${other.getClass.getSimpleName}"
                    )
                }
              }

              // Also check for malformed JSON in example field itself
              example match {
                case JString(str) if str.contains("{") || str.contains("}") || str.contains("[") || str.contains("]") =>
                  if (!str.trim.startsWith("{") && !str.trim.startsWith("[")) {
                    // Looks like incomplete JSON
                    issues += BooleanFieldIssue(
                      entityName,
                      bankId,
                      fieldName,
                      str,
                      s"Example value appears to contain partial JSON: '$str'"
                    )
                  }
                case _ => // OK
              }
            }
          case _ =>
            // Could not find properties - add warning
            issues += BooleanFieldIssue(
              entityName,
              bankId,
              "PROPERTIES",
              "N/A",
              s"Could not find properties section in entity definition"
            )
        }
      }

    } catch {
      case e: Exception =>
        issues += BooleanFieldIssue(
          entityName,
          bankId,
          "JSON_PARSE",
          definitionJson.take(100),
          s"Failed to parse entity JSON: ${e.getMessage}"
        )
    }

    issues.toList
  }
}

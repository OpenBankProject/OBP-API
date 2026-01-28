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
package code.api.util

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.json.compactRender
import code.util.Helper.MdcLoggable
import scala.util.{Try, Success, Failure}

/**
 * Utility object for YAML conversion operations
 * 
 * This utility provides methods to convert Lift's JValue objects to YAML format
 * using Jackson's YAML support.
 */
object YAMLUtils extends MdcLoggable {

  private val jsonMapper = new ObjectMapper()
  private val yamlMapper = new ObjectMapper(new YAMLFactory())

  /**
   * Converts a JValue to YAML string
   * 
   * @param jValue The Lift JValue to convert
   * @return Try containing the YAML string or error
   */
  def jValueToYAML(jValue: JValue): Try[String] = {
    Try {
      // First convert JValue to JSON string
      val jsonString = compactRender(jValue)
      
      // Parse JSON string to Jackson JsonNode
      val jsonNode: JsonNode = jsonMapper.readTree(jsonString)
      
      // Convert JsonNode to YAML string
      yamlMapper.writeValueAsString(jsonNode)
    }.recoverWith {
      case ex: Exception =>
        logger.error(s"Failed to convert JValue to YAML: ${ex.getMessage}", ex)
        Failure(new RuntimeException(s"YAML conversion failed: ${ex.getMessage}", ex))
    }
  }

  /**
   * Converts a JValue to YAML string with error handling that returns a default value
   * 
   * @param jValue The Lift JValue to convert
   * @param defaultValue Default value to return if conversion fails
   * @return YAML string or default value
   */
  def jValueToYAMLSafe(jValue: JValue, defaultValue: String = ""): String = {
    jValueToYAML(jValue) match {
      case Success(yamlString) => yamlString
      case Failure(ex) =>
        logger.warn(s"YAML conversion failed, returning default value: ${ex.getMessage}")
        defaultValue
    }
  }

  /**
   * Checks if the given content type indicates YAML format
   * 
   * @param contentType The content type to check
   * @return true if the content type indicates YAML
   */
  def isYAMLContentType(contentType: String): Boolean = {
    val normalizedContentType = contentType.toLowerCase.trim
    normalizedContentType.contains("application/x-yaml") || 
    normalizedContentType.contains("application/yaml") ||
    normalizedContentType.contains("text/yaml") ||
    normalizedContentType.contains("text/x-yaml")
  }

  /**
   * Gets the appropriate YAML content type
   * 
   * @return Standard YAML content type
   */
  def getYAMLContentType: String = "application/x-yaml"
}
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
import com.fasterxml.jackson.core.{JsonGenerator, JsonFactory}
import net.liftweb.json.JsonAST.{JObject, JArray, JBool, JNull, JNothing, JDouble, JInt, JString, JField, JValue}
import net.liftweb.json._
import net.liftweb.json.compactRender
import code.util.Helper.MdcLoggable
import scala.util.{Try, Success, Failure}
import java.io.{OutputStream, InputStream, PipedInputStream, PipedOutputStream}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Utility object for YAML conversion operations
 * 
 * This utility provides methods to convert Lift's JValue objects to YAML format
 * using Jackson's YAML support. It provides both simple string-based conversion
 * and streaming conversion APIs to avoid building huge intermediate strings.
 */
object YAMLUtils extends MdcLoggable {

  private val jsonMapper = new ObjectMapper()
  private val yamlMapper = new ObjectMapper(new YAMLFactory())

  /**
   * Convert a Lift JValue by writing it token-by-token to a Jackson JsonGenerator.
   * This avoids creating a very large intermediate JSON string.
   */
  private def writeJValueToGenerator(gen: JsonGenerator, j: JValue): Unit = {
    j match {
      case JObject(fields) =>
        gen.writeStartObject()
        fields.foreach {
          case JField(name, value) =>
            gen.writeFieldName(name)
            writeJValueToGenerator(gen, value)
        }
        gen.writeEndObject()
      case JArray(items) =>
        gen.writeStartArray()
        items.foreach(item => writeJValueToGenerator(gen, item))
        gen.writeEndArray()
      case JString(s) =>
        gen.writeString(s)
      case JInt(num) =>
        // Jackson supports BigInteger via writeNumber(String)
        gen.writeNumber(num.toString)
      case JDouble(d) =>
        gen.writeNumber(d)
      // JDecimal is not available in this Lift version; high-precision decimals will
      // fall through to the fallback case (written via compactRender) or be represented
      // as JDouble/JInt depending on creation site.
      case JBool(b) =>
        gen.writeBoolean(b)
      case JNull | JNothing =>
        gen.writeNull()
      case other =>
        // fallback: write compact rendering as string
        gen.writeString(compactRender(other))
    }
  }

  /**
   * Stream a JValue as YAML into a supplied OutputStream.
   * The caller is responsible for closing the OutputStream when appropriate.
   *
   * @param jValue the JValue to serialize
   * @param out the OutputStream to write YAML bytes to
   * @return Try[Unit] indicating success or failure
   */
  def jValueToYAMLStream(jValue: JValue, out: OutputStream): Try[Unit] = {
    Try {
      val gen = yamlMapper.getFactory.createGenerator(out)
      try {
        writeJValueToGenerator(gen, jValue)
        gen.flush()
      } finally {
        // Do not close the provided OutputStream here; just close the generator
        try { gen.close() } catch { case _: Throwable => }
      }
    }.recoverWith {
      case ex: Exception =>
        logger.error(s"Failed to stream JValue to YAML: ${ex.getMessage}", ex)
        Failure(new RuntimeException(s"YAML streaming failed: ${ex.getMessage}", ex))
    }
  }

  /**
   * Provide an InputStream that streams YAML representation of the provided JValue.
   * Writing is performed on a background thread into a PipedOutputStream connected to
   * the returned PipedInputStream. Caller must close the InputStream when done.
   *
   * @param jValue the JValue to serialize
   * @return Try[InputStream] that will yield the YAML bytes
   */
  def jValueToYAMLInputStream(jValue: JValue): Try[InputStream] = {
    Try {
      val in = new PipedInputStream(64 * 1024)
      val out = new PipedOutputStream(in)
      // Write in a background thread so the caller can read as we generate
      val writerThread = new Thread(new Runnable {
        override def run(): Unit = {
          try {
            jValueToYAMLStream(jValue, out) match {
              case Success(_) => // done
              case Failure(e) =>
                // attempt to write an error message into the stream so the reader sees something useful
                try {
                  val msg = s"# Error generating YAML: ${e.getMessage}\n"
                  out.write(msg.getBytes("UTF-8"))
                } catch { case _: Throwable => }
            }
          } finally {
            try { out.close() } catch { case _: Throwable => }
          }
        }
      }, "yaml-stream-writer")
      writerThread.setDaemon(true)
      writerThread.start()
      in
    }.recoverWith {
      case ex: Exception =>
        logger.error(s"Failed to create YAML InputStream: ${ex.getMessage}", ex)
        Failure(new RuntimeException(s"Failed to create YAML InputStream: ${ex.getMessage}", ex))
    }
  }

  /**
   * Converts a JValue to YAML string (keeps compatibility). This method uses the streaming
   * generator internally but still accumulates into a String (for callers that need a String).
   * Prefer streaming APIs for large documents.
   * 
   * @param jValue The Lift JValue to convert
   * @return Try containing the YAML string or error
   */
  def jValueToYAML(jValue: JValue): Try[String] = {
    Try {
      val baos = new java.io.ByteArrayOutputStream()
      jValueToYAMLStream(jValue, baos).get
      baos.toString("UTF-8")
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
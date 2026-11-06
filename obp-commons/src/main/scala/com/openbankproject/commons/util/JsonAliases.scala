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

package com.openbankproject.commons.util

import org.json4s._
import org.json4s.JValue
import org.json4s.native.JsonMethods

/**
 * Helpers mirroring the surface of the former lift-json package object,
 * so call sites keep their original shape on top of json4s:
 *  - parse / parseOpt / compactRender / prettyRender as plain functions
 *  - JField member access by name (json4s aliases JField to (String, JValue))
 */
object JsonAliases {

  def parse(s: String): JValue = JsonMethods.parse(s)

  def parseOpt(s: String): Option[JValue] = JsonMethods.parseOpt(s)

  def compactRender(value: JValue): String = JsonMethods.compact(JsonMethods.render(value))

  def prettyRender(value: JValue): String = JsonMethods.pretty(JsonMethods.render(value))

  implicit class RichJField(private val jfield: org.json4s.JField) extends AnyVal {
    def name: String = jfield._1
    def value: JValue = jfield._2
  }
}

/**
 * Forwarder object standing in for the former lift-json package,
 * keeping `json.parse(...)` / `json.JValue` style call sites compiling
 * against json4s after `import com.openbankproject.commons.util.json`.
 */
object json {
  type JValue = org.json4s.JValue
  type JObject = org.json4s.JObject
  type JArray = org.json4s.JArray
  type JString = org.json4s.JString
  type JInt = org.json4s.JInt
  type JDouble = org.json4s.JDouble
  type JBool = org.json4s.JBool
  type JField = org.json4s.JField
  type Formats = org.json4s.Formats
  type MappingException = org.json4s.MappingException

  val JNothing = org.json4s.JNothing
  val JNull = org.json4s.JNull
  val JObject = org.json4s.JObject
  val JArray = org.json4s.JArray
  val JString = org.json4s.JString
  val JInt = org.json4s.JInt
  val JDouble = org.json4s.JDouble
  val JBool = org.json4s.JBool
  val JField = org.json4s.JField
  val JsonAST = org.json4s.JsonAST
  val JsonDSL = org.json4s.JsonDSL
  val Extraction = org.json4s.Extraction
  val DefaultFormats = org.json4s.DefaultFormats
  val NoTypeHints = org.json4s.NoTypeHints
  val Serialization = org.json4s.native.Serialization
  val JsonParser = org.json4s.native.JsonParser

  def parse(s: String): JValue = JsonAliases.parse(s)
  def parseOpt(s: String): Option[JValue] = JsonAliases.parseOpt(s)
  def compactRender(value: JValue): String = JsonAliases.compactRender(value)
  def prettyRender(value: JValue): String = JsonAliases.prettyRender(value)
}

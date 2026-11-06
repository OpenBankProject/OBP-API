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

package code.dynamicResourceDoc

import org.json4s._
import com.openbankproject.commons.model.JsonFieldReName
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import com.openbankproject.commons.util.json
import org.json4s.JsonAST.JNothing
import org.json4s.{Formats, JValue, JsonAST}
import net.liftweb.util.SimpleInjector
import org.apache.commons.lang3.StringUtils

import java.net.URLDecoder
import scala.collection.immutable.List

object DynamicResourceDocProvider extends SimpleInjector {

  val provider = new Inject(() => buildOne) {}

  def buildOne: MappedDynamicResourceDocProvider.type = MappedDynamicResourceDocProvider
}

case class JsonDynamicResourceDoc(
   bankId: Option[String],
   dynamicResourceDocId: Option[String],
   methodBody: String,
   partialFunctionName: String,
   requestVerb: String,
   requestUrl: String,
   summary: String,
   description: String,
   exampleRequestBody: Option[JValue],
   successResponseBody: Option[JValue],
   errorResponseBodies: String,
   tags: String,
   roles: String
) extends JsonFieldReName {
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

trait DynamicResourceDocProvider {

  def getById(bankId: Option[String], dynamicResourceDocId: String): Box[JsonDynamicResourceDoc]
  def getByVerbAndUrl(bankId: Option[String], requestVerb: String, requestUrl: String): Box[JsonDynamicResourceDoc]

  def getAll(bankId: Option[String]): List[JsonDynamicResourceDoc] = getAllAndConvert(bankId, identity)

  def getAllAndConvert[T: Manifest](bankId: Option[String], transform: JsonDynamicResourceDoc => T): List[T]

  def create(bankId: Option[String], entity: JsonDynamicResourceDoc, createdByUserId: Option[String]): Box[JsonDynamicResourceDoc]
  def update(bankId: Option[String], entity: JsonDynamicResourceDoc, updatedByUserId: Option[String]): Box[JsonDynamicResourceDoc]
  def deleteById(bankId: Option[String], dynamicResourceDocId: String): Box[Boolean]

}

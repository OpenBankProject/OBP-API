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

package code.dynamicMessageDoc

import org.json4s._
import java.net.URLDecoder
import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.common.Box
import org.json4s.JsonAST.JValue
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

object DynamicMessageDocProvider extends SimpleInjector {

  val provider = new Inject(() => buildOne) {}

  def buildOne: MappedDynamicMessageDocProvider.type = MappedDynamicMessageDocProvider
}

case class JsonDynamicMessageDoc(
  bankId: Option[String],
  dynamicMessageDocId: Option[String],
  process: String,
  messageFormat: String, 
  description: String, 
  outboundTopic: String, 
  inboundTopic: String, 
  exampleOutboundMessage: JValue, 
  exampleInboundMessage: JValue, 
  outboundAvroSchema: String, 
  inboundAvroSchema: String,
  adapterImplementation: String,
  methodBody: String,
  programmingLang: String
) extends JsonFieldReName{
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

trait DynamicMessageDocProvider {

  def getById(bankId: Option[String], dynamicMessageDocId: String): Box[JsonDynamicMessageDoc]
  def getByProcess(bankId: Option[String], process: String): Box[JsonDynamicMessageDoc]
  def getAll(bankId: Option[String]): List[JsonDynamicMessageDoc]

  def create(bankId: Option[String], entity: JsonDynamicMessageDoc, createdByUserId: Option[String]): Box[JsonDynamicMessageDoc]
  def update(bankId: Option[String], entity: JsonDynamicMessageDoc, updatedByUserId: Option[String]): Box[JsonDynamicMessageDoc]
  def deleteById(bankId: Option[String], dynamicMessageDocId: String): Box[Boolean]

}
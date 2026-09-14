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

package code.endpointTag

/* For Connector endpoint routing, star connector use this provider to find proxy connector name */

import org.json4s._
import com.openbankproject.commons.model.{Converter, JsonFieldReName, EndpointTagT}
import net.liftweb.common.Box
import org.json4s.Formats
import org.json4s.JsonAST.{JField, JNull, JObject, JString}
import net.liftweb.util.SimpleInjector

object EndpointTagProvider extends SimpleInjector {

  val endpointTagProvider = new Inject(() => buildOne) {}

  def buildOne: MappedEndpointTagProvider.type = MappedEndpointTagProvider
}

case class EndpointTagCommons(
  endpointTagId: Option[String],
  operationId: String,
  tagName: String,
  bankId: Option[String],
  ) extends EndpointTagT with JsonFieldReName {
  /**
    * when serialized to json, the  Option field will be not shown, this endpoint just generate a full fields json, include all None value fields
    * @return JObject include all fields
    */
  def toJson(implicit format: Formats) = {
    JObject(List(
      JField("operation_id", JString(this.operationId)),
      JField("endpoint_mapping_id", this.endpointTagId.map(JString(_)).getOrElse(JNull)),
      JField("tagName", JString(this.tagName)),
      JField("bankId", JString(this.bankId.getOrElse("")))
    ))
  }
}

object EndpointTagCommons extends Converter[EndpointTagT, EndpointTagCommons]

trait EndpointTagProvider {
  def getById(endpointTagId: String): Box[EndpointTagT]
  
  def getByOperationId(operationId: String): Box[EndpointTagT]
  
  def getAllEndpointTags: List[EndpointTagT]

  def createOrUpdate(endpointTag: EndpointTagT): Box[EndpointTagT]

  def delete(endpointTagId: String):Box[Boolean]
}
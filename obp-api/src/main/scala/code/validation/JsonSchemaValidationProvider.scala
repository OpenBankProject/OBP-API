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

package code.validation

/* For CardAttribute */

import org.json4s._
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import org.json4s.{Formats, JsonAST}
import net.liftweb.util.SimpleInjector
import org.json4s.JsonDSL._
import com.openbankproject.commons.util.json

object JsonSchemaValidationProvider extends SimpleInjector {

  val validationProvider = new Inject(() => buildOne) {}

  def buildOne: MappedJsonSchemaValidationProvider.type = MappedJsonSchemaValidationProvider
}

case class JsonValidation(operationId: String, jsonSchema: String) extends JsonAble {

  override def toJValue(implicit format: Formats): JsonAST.JValue =
    ("operation_id", operationId) ~ ("json_schema", json.parse(jsonSchema))
}

trait JsonSchemaValidationProvider {

  def getByOperationId(operationId: String): Box[JsonValidation]

  def getAll(): List[JsonValidation]

  def create(jsonValidation: JsonValidation): Box[JsonValidation]
  def update(jsonValidation: JsonValidation): Box[JsonValidation]
  def deleteByOperationId(operationId: String): Box[Boolean]

}

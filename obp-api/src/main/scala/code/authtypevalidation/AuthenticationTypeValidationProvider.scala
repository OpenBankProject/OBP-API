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

package code.authtypevalidation

/* For CardAttribute */

import org.json4s._
import code.api.util.AuthenticationType
import com.openbankproject.commons.util.JsonAble
import net.liftweb.common.Box
import org.json4s.JsonDSL._
import com.openbankproject.commons.util.json
import org.json4s.{Formats, JsonAST}
import net.liftweb.util.SimpleInjector
import org.apache.commons.lang3.StringUtils

object AuthenticationTypeValidationProvider extends SimpleInjector {

  val validationProvider = new Inject(() => buildOne) {}

  def buildOne: MappedAuthTypeValidationProvider.type = MappedAuthTypeValidationProvider
}

case class JsonAuthTypeValidation(operationId: String, authTypes: List[AuthenticationType]) extends JsonAble {

  override def toJValue(implicit format: Formats): JsonAST.JValue =
    ("operation_id", operationId) ~ ("allowed_authentication_types", json.Extraction.decompose(authTypes.map(_.toString)))
}

object JsonAuthTypeValidation {
  def apply(operationId: String, authTypes: String): JsonAuthTypeValidation = {
    val typeList = StringUtils.split(authTypes, ",").toList.map(AuthenticationType.withName)
    JsonAuthTypeValidation(operationId, typeList)
  }
}

trait AuthenticationTypeValidationProvider {

  def getByOperationId(operationId: String): Box[JsonAuthTypeValidation]

  def getAll(): List[JsonAuthTypeValidation]

  def create(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation]
  def update(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation]
  def deleteByOperationId(operationId: String): Box[Boolean]

}

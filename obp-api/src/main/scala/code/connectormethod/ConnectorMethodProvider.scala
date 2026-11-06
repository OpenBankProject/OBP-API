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

package code.connectormethod

import com.openbankproject.commons.model.JsonFieldReName
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import java.net.URLDecoder

object ConnectorMethodProvider extends SimpleInjector {

  val provider = new Inject(() => buildOne) {}

  def buildOne: DoobieConnectorMethodProvider.type = DoobieConnectorMethodProvider
}

case class JsonConnectorMethod(connectorMethodId: Option[String], methodName: String, methodBody: String, programmingLang: String="Scala") extends JsonFieldReName{
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

case class JsonConnectorMethodMethodBody(methodBody: String, programmingLang: String="Scala") extends JsonFieldReName {
  def decodedMethodBody: String = URLDecoder.decode(methodBody, "UTF-8")
}

trait ConnectorMethodProvider {

  def getById(connectorMethodId: String): Box[JsonConnectorMethod]
  def getByMethodNameWithCache(methodName: String): Box[JsonConnectorMethod]
  def getByMethodNameWithoutCache(methodName: String): Box[JsonConnectorMethod]

  def getAll(): List[JsonConnectorMethod]

  def create(entity: JsonConnectorMethod, createdByUserId: Option[String]): Box[JsonConnectorMethod]
  def update(connectorMethodId: String, connectorMethodBody: String, programmingLang: String, updatedByUserId: Option[String]): Box[JsonConnectorMethod]
  def deleteById(connectorMethodId: String): Box[Boolean]

}

/**
 * A connector method plus the provenance columns, for the v7.0.0 read-only endpoints.
 *
 * Kept separate from JsonConnectorMethod because that one is the request/response contract for
 * create and update, and adding server-set fields to it would let a caller submit them.
 */
case class ConnectorMethodWithProvenance(
  connectorMethod: JsonConnectorMethod,
  createdByUserId: Option[String],
  updatedByUserId: Option[String],
  methodBodyHash: Option[String],
  createdAt: Option[java.util.Date],
  updatedAt: Option[java.util.Date],
  // Maker/checker (upstream commit 5d4af81c3) - see DynamicResourceDoc for the full contract.
  approvedHash: Option[String] = None,
  isActive: Boolean = true
)

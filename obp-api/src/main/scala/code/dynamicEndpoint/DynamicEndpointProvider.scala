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

package code.DynamicEndpoint

import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object DynamicEndpointProvider extends SimpleInjector {

  val connectorMethodProvider = new Inject(() => buildOne) {}

  def buildOne: MappedDynamicEndpointProvider.type = MappedDynamicEndpointProvider
}

trait DynamicEndpointT {
  def dynamicEndpointId: Option[String]
  def swaggerString: String
  /**
   * The user who create this DynamicEndpoint
   */
  def userId: String
  def bankId: Option[String]
}

case class DynamicEndpointCommons(
                                dynamicEndpointId: Option[String] = None,
                                swaggerString: String,
                                userId: String,
                                bankId: Option[String]
                               ) extends DynamicEndpointT with JsonFieldReName

object DynamicEndpointCommons extends Converter[DynamicEndpointT, DynamicEndpointCommons]

case class DynamicEndpointSwagger(swaggerString: String, dynamicEndpointId: Option[String] = None)

trait DynamicEndpointProvider {
  def create(bankId:Option[String], userId: String, swaggerString: String): Box[DynamicEndpointT]
  def update(bankId:Option[String], dynamicEndpointId: String, swaggerString: String): Box[DynamicEndpointT]
  def updateHost(bankId:Option[String], dynamicEndpointId: String, hostString: String): Box[DynamicEndpointT]
  def get(bankId:Option[String],dynamicEndpointId: String): Box[DynamicEndpointT]
  def getAll(bankId:Option[String]): List[DynamicEndpointT]
  def getDynamicEndpointsByUserId(userId: String): List[DynamicEndpointT]
  def delete(bankId:Option[String], dynamicEndpointId: String): Boolean
}
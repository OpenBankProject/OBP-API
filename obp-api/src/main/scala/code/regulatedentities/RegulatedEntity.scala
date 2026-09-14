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

package code.regulatedentities


import com.openbankproject.commons.model.{RegulatedEntityTrait}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

object RegulatedEntityX extends SimpleInjector {
  val regulatedEntityProvider = new Inject(() => buildOne) {}
  def buildOne: RegulatedEntityProvider = MappedRegulatedEntityProvider
}
/* For ProductFee */
trait RegulatedEntityProvider extends MdcLoggable {

  def getRegulatedEntities(): List[RegulatedEntityTrait]

  def getRegulatedEntityByEntityId(entityId: String): Box[RegulatedEntityTrait]

  def createRegulatedEntity(certificateAuthorityCaOwnerId: Option[String],
                            entityCertificatePublicKey: Option[String],
                            entityName: Option[String],
                            entityCode: Option[String],
                            entityType: Option[String],
                            entityAddress: Option[String],
                            entityTownCity: Option[String],
                            entityPostCode: Option[String],
                            entityCountry: Option[String],
                            entityWebSite: Option[String],
                            services: Option[String]
                           ): Box[RegulatedEntityTrait]

  def deleteRegulatedEntity(id: String): Box[Boolean]

}

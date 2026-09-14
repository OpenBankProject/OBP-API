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

package code.api.util.newstyle

import code.api.util.APIUtil.{OBPReturnType, unboxFull, unboxFullOrFail}
import code.api.util.ErrorMessages.{RegulatedEntityNotDeleted, RegulatedEntityNotFound}
import code.api.util.{APIUtil, CallContext}
import code.consumer.Consumers
import code.model.{AppType, Consumer}
import code.regulatedentities.MappedRegulatedEntityProvider
import com.openbankproject.commons.model.RegulatedEntityTrait
import net.liftweb.common.Box
import code.bankconnectors.Connector
import code.api.util.ErrorMessages.{InvalidConnectorResponse}
import code.api.util.{APIUtil, CallContext}
import com.github.dwickern.macros.NameOf.nameOf

import scala.concurrent.Future

object RegulatedEntityNewStyle {

  import com.openbankproject.commons.ExecutionContext.Implicits.global

  def createRegulatedEntityNewStyle(certificateAuthorityCaOwnerId: Option[String],
                                    entityCertificatePublicKey: Option[String],
                                    entityName: Option[String],
                                    entityCode: Option[String],
                                    entityType: Option[String],
                                    entityAddress: Option[String],
                                    entityTownCity: Option[String],
                                    entityPostCode: Option[String],
                                    entityCountry: Option[String],
                                    entityWebSite: Option[String],
                                    services: Option[String],
                                    callContext: Option[CallContext]): OBPReturnType[RegulatedEntityTrait] = {
    Future {
      MappedRegulatedEntityProvider.createRegulatedEntity(
        certificateAuthorityCaOwnerId: Option[String],
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
      ) map {
        (_, callContext)
      }
    } map{ i =>
      unboxFullOrFail(i, callContext,s"$InvalidConnectorResponse ${nameOf(createRegulatedEntityNewStyle _)} ", 400 )
    }
  }

  def getRegulatedEntitiesNewStyle(
    callContext: Option[CallContext]
  ): OBPReturnType[List[RegulatedEntityTrait]] = {
    Connector.connector.vend.getRegulatedEntities(callContext: Option[CallContext]) map { i =>
      (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponse ${nameOf(Connector.connector.vend.getRegulatedEntities _)} ", 400 ), i._2)
    }
  }
  def getRegulatedEntityByEntityIdNewStyle(
    id: String,
    callContext: Option[CallContext]
  ): OBPReturnType[RegulatedEntityTrait] = {
    Connector.connector.vend.getRegulatedEntityByEntityId(id, callContext: Option[CallContext]) map { i =>
      (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponse ${nameOf(Connector.connector.vend.getRegulatedEntityByEntityId _)} ", 400 ), i._2)
    }
  }
  def deleteRegulatedEntityNewStyle(id: String,
                                    callContext: Option[CallContext]
                                   ): OBPReturnType[Boolean] = {
    Future {
      MappedRegulatedEntityProvider.deleteRegulatedEntity(id)
    } map {
      (_, callContext)
    } map {
      x => (unboxFullOrFail(x._1, callContext, RegulatedEntityNotDeleted, 400), x._2)
    }
  }


}

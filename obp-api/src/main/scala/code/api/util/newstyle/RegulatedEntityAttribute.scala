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
import code.api.util.ErrorMessages.{InvalidConnectorResponse}
import code.api.util.CallContext
import code.bankconnectors.Connector
import code.regulatedentities.attribute.RegulatedEntityAttributeX
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{RegulatedEntityAttributeTrait, RegulatedEntityId}
import com.openbankproject.commons.model.enums.RegulatedEntityAttributeType
import scala.concurrent.Future
import com.github.dwickern.macros.NameOf.nameOf

object RegulatedEntityAttributeNewStyle {

  def createOrUpdateRegulatedEntityAttribute(
    regulatedEntityId: RegulatedEntityId,
    regulatedEntityAttributeId: Option[String],
    name: String,
    attributeType: RegulatedEntityAttributeType.Value,
    value: String,
    isActive: Option[Boolean],
    callContext: Option[CallContext]
  ): OBPReturnType[RegulatedEntityAttributeTrait] = {
      RegulatedEntityAttributeX.regulatedEntityAttributeProvider.vend.createOrUpdateRegulatedEntityAttribute(
        regulatedEntityId: RegulatedEntityId,
        regulatedEntityAttributeId: Option[String],
        name: String,
        attributeType: RegulatedEntityAttributeType.Value,
        value: String,
        isActive: Option[Boolean]
      )
    }.map {
        result =>
          (
            unboxFullOrFail(
              result, 
              callContext,
              s"$InvalidConnectorResponse ${nameOf(createOrUpdateRegulatedEntityAttribute _)}", 
              400), 
            callContext
          )
    }

  def getRegulatedEntityAttributeById(
    attributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[RegulatedEntityAttributeTrait] = {
      RegulatedEntityAttributeX.regulatedEntityAttributeProvider.vend.getRegulatedEntityAttributeById(attributeId).map {
        result =>
          (
            unboxFullOrFail(
              result,
              callContext,
              s"$InvalidConnectorResponse ${nameOf(getRegulatedEntityAttributeById _)}",
              404),
            callContext
          )
    }
  }

  def getRegulatedEntityAttributes(
    entityId: RegulatedEntityId,
    callContext: Option[CallContext]
  ): OBPReturnType[List[RegulatedEntityAttributeTrait]] = {
    RegulatedEntityAttributeX.regulatedEntityAttributeProvider.vend.getRegulatedEntityAttributes(entityId).map {
      result =>
        (
          unboxFullOrFail(
            result,
            callContext,
            s"$InvalidConnectorResponse ${nameOf(getRegulatedEntityAttributes _)}",
            404),
          callContext
        )
    }
  }

  def deleteRegulatedEntityAttribute(
    attributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Boolean] = {
      RegulatedEntityAttributeX.regulatedEntityAttributeProvider.vend.deleteRegulatedEntityAttribute(attributeId).map {
      result =>
        (
          unboxFullOrFail(
            result,
            callContext,
            s"$InvalidConnectorResponse ${nameOf(deleteRegulatedEntityAttribute _)}",
            400),
          callContext
        )
    }
  }
}

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

import code.api.util.APIUtil.{OBPReturnType, unboxFullOrFail}
import code.api.util.ErrorMessages.InvalidConnectorResponse
import code.api.util.CallContext
import code.counterpartyattribute.CounterpartyAttributeX
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{CounterpartyAttributeTrait, CounterpartyId}
import com.openbankproject.commons.model.enums.CounterpartyAttributeType
import scala.concurrent.Future
import com.github.dwickern.macros.NameOf.nameOf

object CounterpartyAttributeNewStyle {

  def createOrUpdateCounterpartyAttribute(
    counterpartyId: CounterpartyId,
    counterpartyAttributeId: Option[String],
    name: String,
    attributeType: CounterpartyAttributeType.Value,
    value: String,
    isActive: Option[Boolean],
    callContext: Option[CallContext]
  ): OBPReturnType[CounterpartyAttributeTrait] = {
      CounterpartyAttributeX.counterpartyAttributeProvider.vend.createOrUpdateCounterpartyAttribute(
        counterpartyId: CounterpartyId,
        counterpartyAttributeId: Option[String],
        name: String,
        attributeType: CounterpartyAttributeType.Value,
        value: String,
        isActive: Option[Boolean]
      )
    }.map {
        result =>
          (
            unboxFullOrFail(
              result,
              callContext,
              s"$InvalidConnectorResponse ${nameOf(createOrUpdateCounterpartyAttribute _)}",
              400),
            callContext
          )
    }

  def getCounterpartyAttributeById(
    attributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[CounterpartyAttributeTrait] = {
      CounterpartyAttributeX.counterpartyAttributeProvider.vend.getCounterpartyAttributeById(attributeId).map {
        result =>
          (
            unboxFullOrFail(
              result,
              callContext,
              s"$InvalidConnectorResponse ${nameOf(getCounterpartyAttributeById _)}",
              404),
            callContext
          )
    }
  }

  def getCounterpartyAttributes(
    counterpartyId: CounterpartyId,
    callContext: Option[CallContext]
  ): OBPReturnType[List[CounterpartyAttributeTrait]] = {
    CounterpartyAttributeX.counterpartyAttributeProvider.vend.getCounterpartyAttributes(counterpartyId).map {
      result =>
        (
          unboxFullOrFail(
            result,
            callContext,
            s"$InvalidConnectorResponse ${nameOf(getCounterpartyAttributes _)}",
            404),
          callContext
        )
    }
  }

  def deleteCounterpartyAttribute(
    attributeId: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Boolean] = {
      CounterpartyAttributeX.counterpartyAttributeProvider.vend.deleteCounterpartyAttribute(attributeId).map {
      result =>
        (
          unboxFullOrFail(
            result,
            callContext,
            s"$InvalidConnectorResponse ${nameOf(deleteCounterpartyAttribute _)}",
            400),
          callContext
        )
    }
  }
}

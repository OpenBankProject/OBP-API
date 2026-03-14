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

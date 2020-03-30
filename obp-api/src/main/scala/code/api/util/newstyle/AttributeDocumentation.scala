package code.api.util.newstyle

import code.api.attributedefinition.AttributeDefinition
import code.api.util.APIUtil.OBPReturnType
import code.api.util.{APIUtil, CallContext}
import code.bankconnectors.Connector
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}

import scala.collection.immutable.List

object AttributeDefinition {
  def createOrUpdateAttributeDefinition(bankId: BankId,
                                        name: String,
                                        category: AttributeCategory.Value,
                                        `type`: AttributeType.Value,
                                        description: String,
                                        alias: String,
                                        canBeSeenOnViews: List[String],
                                        isActive: Boolean,
                                        callContext: Option[CallContext]
                                       ): OBPReturnType[AttributeDefinition] = {
    Connector.connector.vend.createOrUpdateAttributeDefinition(
      bankId: BankId,
      name: String,
      category: AttributeCategory.Value,
      `type`: AttributeType.Value,
      description: String,
      alias: String,
      canBeSeenOnViews: List[String],
      isActive: Boolean,
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }

  def deleteAttributeDefinition(attributeDefinitionId: String,
                                   category: AttributeCategory.Value,
                                   callContext: Option[CallContext]
                                  ): OBPReturnType[Boolean] = {
    Connector.connector.vend.deleteAttributeDefinition(
      attributeDefinitionId: String,
      category: AttributeCategory.Value,
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }

  def getAttributeDefinition(category: AttributeCategory.Value,
                                callContext: Option[CallContext]
                               ): OBPReturnType[List[AttributeDefinition]] = {
    Connector.connector.vend.getAttributeDefinition(
      category: AttributeCategory.Value,
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }
  
}


package code.api.util.newstyle

import code.api.attributedocumentation.AttributeDocumentation
import code.api.util.APIUtil.OBPReturnType
import code.api.util.{APIUtil, CallContext}
import code.bankconnectors.Connector
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}

import scala.collection.immutable.List

object AttributeDocumentation {
  def createOrUpdateAttributeDocumentation(bankId: BankId,
                                           name: String,
                                           category: AttributeCategory.Value,
                                           `type`: AttributeType.Value,
                                           description: String,
                                           alias: String,
                                           isActive: Boolean,
                                           callContext: Option[CallContext]
                                          ): OBPReturnType[AttributeDocumentation]  = {
    Connector.connector.vend.createOrUpdateAttributeDocumentation(
      bankId: BankId,
      name: String,
      category: AttributeCategory.Value,
      `type`: AttributeType.Value,
      description: String,
      alias: String,
      isActive: Boolean,
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }

  def deleteAttributeDocumentation(attributeDocumentationId: String,
                                   category: AttributeCategory.Value,
                                   callContext: Option[CallContext]
                                  ): OBPReturnType[Boolean] = {
    Connector.connector.vend.deleteAttributeDocumentation(
      attributeDocumentationId: String,
      category: AttributeCategory.Value,
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }

  def getAttributeDocumentation(category: AttributeCategory.Value,
                                callContext: Option[CallContext]
                               ): OBPReturnType[List[AttributeDocumentation]] = {
    Connector.connector.vend.getAttributeDocumentation(
      category: AttributeCategory.Value,
      callContext: Option[CallContext]
    ) map {
      i => (APIUtil.connectorEmptyResponse(i._1, callContext), i._2)
    }
  }
  
}


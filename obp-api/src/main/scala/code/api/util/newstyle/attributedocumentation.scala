package code.api.util.newstyle

import code.api.attributedocumentation.AttributeDocumentation
import code.api.util.APIUtil.OBPReturnType
import code.api.util.{APIUtil, CallContext}
import code.bankconnectors.Connector
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}

object attributedocumentation {
  def createOrUpdateAttributeDocumentation(name: String,
                                           category: AttributeCategory.Value,
                                           `type`: AttributeType.Value,
                                           description: String,
                                           alias: String,
                                           isActive: Boolean,
                                           callContext: Option[CallContext]
                                          ): OBPReturnType[AttributeDocumentation]  = {
    Connector.connector.vend.createOrUpdateAttributeDocumentation(
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
}


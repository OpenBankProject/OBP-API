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


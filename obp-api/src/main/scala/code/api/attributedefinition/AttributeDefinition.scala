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

package code.api.attributedefinition

import code.api.util.APIUtil
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object AttributeDefinitionDI extends SimpleInjector {
  val attributeDefinition = new Inject(() => buildOne) {}
  def buildOne: AttributeDefinitionProviderTrait = MappedAttributeDefinitionProvider 
}

trait AttributeDefinitionProviderTrait {
  def createOrUpdateAttributeDefinition(bankId: BankId,
                                        name: String,
                                        category: AttributeCategory.Value,
                                        `type`: AttributeType.Value,
                                        description: String,
                                        alias: String,
                                        canBeSeenOnViews: List[String],
                                        isActive: Boolean
                                       ): Future[Box[AttributeDefinition]]

  def deleteAttributeDefinition(attributeDefinitionId: String,
                                category: AttributeCategory.Value): Future[Box[Boolean]]
  
  def getAttributeDefinition(category: AttributeCategory.Value): Future[Box[List[AttributeDefinition]]]
}

trait AttributeDefinitionTrait {
  def attributeDefinitionId: String
  def bankId: BankId
  def name: String
  def category: AttributeCategory.Value
  def `type`: AttributeType.Value
  def description: String
  def alias: String
  def canBeSeenOnViews: List[String]
  def isActive: Boolean
}
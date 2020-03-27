package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.api.attributedefinition.{AttributeDefinition, AttributeDefinitionProviderTrait, RemotedatAttributeDefinitionCaseClasses}
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataAttributeDefinition extends ObpActorInit with AttributeDefinitionProviderTrait {

  val cc = RemotedatAttributeDefinitionCaseClasses

  def createOrUpdateAttributeDefinition(bankId: BankId,
                                        name: String,
                                        category: AttributeCategory.Value,
                                        `type`: AttributeType.Value,
                                        description: String,
                                        alias: String,
                                        canBeSeenOnViews: List[String],
                                        isActive: Boolean
                                       ): Future[Box[AttributeDefinition]] =
    (actor ? cc.createOrUpdateAttributeDefinition(bankId, name, category, `type`, description, alias, canBeSeenOnViews, isActive))
      .mapTo[Box[AttributeDefinition]]

  def deleteAttributeDefinition(attributeDefinitionId: String, 
                                   category: AttributeCategory.Value): Future[Box[Boolean]] =
    (actor ? cc.deleteAttributeDefinition(attributeDefinitionId, category)).mapTo[Box[Boolean]]
  
  def getAttributeDefinition(category: AttributeCategory.Value): Future[Box[List[AttributeDefinition]]] =
    (actor ? cc.getAttributeDefinition(category)).mapTo[Box[List[AttributeDefinition]]]


}

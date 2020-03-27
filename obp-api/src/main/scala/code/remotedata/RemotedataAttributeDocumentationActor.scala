package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.api.attributedefinition.{MappedAttributeDefinitionProvider, RemotedatAttributeDefinitionCaseClasses}
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId

import scala.collection.immutable.List

class RemotedataAttributeDefinitionActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedAttributeDefinitionProvider
  val cc = RemotedatAttributeDefinitionCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.createOrUpdateAttributeDefinition(bankId: BankId,
                                              name: String,
                                              category: AttributeCategory.Value,
                                              `type`: AttributeType.Value,
                                              description: String,
                                              alias: String,
                                              canBeSeenOnViews: List[String],
                                              isActive: Boolean
    ) =>
      logger.debug(s"createOrUpdateConsumerCallLimits($bankId, $name, ${category.toString}, ${`type`.toString}, $description, $alias, $isActive")
      mapper.createOrUpdateAttributeDefinition(bankId, name, category, `type`, description, alias, canBeSeenOnViews, isActive) pipeTo sender
  
    case cc.deleteAttributeDefinition(attributeDefinitionId: String, category: AttributeCategory.Value) =>
      logger.debug(s"deleteAttributeDefinition($attributeDefinitionId, ${category.toString})")
      mapper.deleteAttributeDefinition(attributeDefinitionId, category) pipeTo sender
      
    case cc.getAttributeDefinition(category: AttributeCategory.Value) =>
      logger.debug(s"getAttributeDefinition(${category.toString})")
      mapper.getAttributeDefinition(category) pipeTo sender
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}



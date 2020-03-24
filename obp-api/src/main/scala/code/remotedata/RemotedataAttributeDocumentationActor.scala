package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.api.attributedocumentation.{MappedAttributeDocumentationProvider, RemotedatattributeDocumentationCaseClasses}
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId

class RemotedataAttributeDocumentationActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedAttributeDocumentationProvider
  val cc = RemotedatattributeDocumentationCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.createOrUpdateAttributeDocumentation(bankId: BankId,
                                                 name: String,
                                                category: AttributeCategory.Value,
                                                `type`: AttributeType.Value,
                                                description: String,
                                                alias: String,
                                                isActive: Boolean
    ) =>
      logger.debug(s"createOrUpdateConsumerCallLimits($bankId, $name, ${category.toString}, ${`type`.toString}, $description, $alias, $isActive")
      mapper.createOrUpdateAttributeDocumentation(bankId, name, category, `type`, description, alias, isActive) pipeTo sender
  
    case cc.deleteAttributeDocumentation(attributeDocumentationId: String, category: AttributeCategory.Value) =>
      logger.debug(s"deleteAttributeDocumentation($attributeDocumentationId, ${category.toString})")
      mapper.deleteAttributeDocumentation(attributeDocumentationId, category) pipeTo sender
      
    case cc.getAttributeDocumentation(category: AttributeCategory.Value) =>
      logger.debug(s"getAttributeDocumentation(${category.toString})")
      mapper.getAttributeDocumentation(category) pipeTo sender
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}



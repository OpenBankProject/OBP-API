package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.api.attributedocumentation.{AttributeDocumentation, AttributeDocumentationProviderTrait, RemotedatattributeDocumentationCaseClasses}
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import net.liftweb.common.Box

import scala.concurrent.Future


object RemotedataAttributeDocumentation extends ObpActorInit with AttributeDocumentationProviderTrait {

  val cc = RemotedatattributeDocumentationCaseClasses

  def createOrUpdateAttributeDocumentation(bankId: BankId,
                                           name: String,
                                           category: AttributeCategory.Value,
                                           `type`: AttributeType.Value,
                                           description: String,
                                           alias: String,
                                           isActive: Boolean
                                          ): Future[Box[AttributeDocumentation]] =
    (actor ? cc.createOrUpdateAttributeDocumentation(bankId, name, category, `type`, description, alias, isActive)).mapTo[Box[AttributeDocumentation]]

  def deleteAttributeDocumentation(attributeDocumentationId: String, 
                                   category: AttributeCategory.Value): Future[Box[Boolean]] =
    (actor ? cc.deleteAttributeDocumentation(attributeDocumentationId, category)).mapTo[Box[Boolean]]


}

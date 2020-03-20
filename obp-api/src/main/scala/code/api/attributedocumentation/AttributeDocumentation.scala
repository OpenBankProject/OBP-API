package code.api.attributedocumentation

import code.api.util.APIUtil
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import code.remotedata.{RemotedataAttributeDocumentation, RemotedataRateLimiting}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object AttributeDocumentationDI extends SimpleInjector {
  val attributeDocumentation = new Inject(buildOne _) {}
  def buildOne: AttributeDocumentationProviderTrait = APIUtil.getPropsAsBoolValue("use_akka", false) match {
    case false  => MappedAttributeDocumentationProvider
    case true => RemotedataAttributeDocumentation   // We will use Akka as a middleware
  }
}

trait AttributeDocumentationProviderTrait {
  def createOrUpdateAttributeDocumentation(name: String,
                                           category: AttributeCategory.Value,
                                           `type`: AttributeType.Value,
                                           description: String,
                                           alias: String, 
                                           isActive: Boolean
                                          ): Future[Box[AttributeDocumentation]]
}

trait AttributeDocumentationTrait {
  def name: String
  def category: AttributeCategory.Value
  def `type`: AttributeType.Value
  def description: String
  def alias: String
  def isActive: Boolean
}


class RemotedataAttributeDocumentationCaseClasses {
  case class createOrUpdateAttributeDocumentation(name: String,
                                                  category: AttributeCategory.Value,
                                                  `type`: AttributeType.Value,
                                                  description: String,
                                                  alias: String,
                                                  isActive: Boolean)
}

object RemotedatattributeDocumentationCaseClasses extends RemotedataAttributeDocumentationCaseClasses

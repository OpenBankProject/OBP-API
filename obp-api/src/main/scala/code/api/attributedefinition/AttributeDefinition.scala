package code.api.attributedefinition

import code.api.util.APIUtil
import code.remotedata.RemotedataAttributeDefinition
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.{AttributeCategory, AttributeType}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object AttributeDefinitionDI extends SimpleInjector {
  val attributeDefinition = new Inject(buildOne _) {}
  def buildOne: AttributeDefinitionProviderTrait = APIUtil.getPropsAsBoolValue("use_akka", false) match {
    case false  => MappedAttributeDefinitionProvider
    case true => RemotedataAttributeDefinition   // We will use Akka as a middleware
  }
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


class RemotedataAttributeDefinitionCaseClasses {

  case class createOrUpdateAttributeDefinition(bankId: BankId,
                                               name: String,
                                               category: AttributeCategory.Value,
                                               `type`: AttributeType.Value,
                                               description: String,
                                               alias: String,
                                               canBeSeenOnViews: List[String],
                                               isActive: Boolean)
  case class deleteAttributeDefinition(attributeDefinitionId: String, category: AttributeCategory.Value)
  case class getAttributeDefinition(category: AttributeCategory.Value)
}

object RemotedatAttributeDefinitionCaseClasses extends RemotedataAttributeDefinitionCaseClasses

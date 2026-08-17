package code.regulatedentities.attribute

/* For ProductAttribute */

import com.openbankproject.commons.model.{RegulatedEntityAttributeTrait, RegulatedEntityId, BankId}
import com.openbankproject.commons.model.enums.RegulatedEntityAttributeType
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object RegulatedEntityAttributeX extends SimpleInjector {

  val regulatedEntityAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: RegulatedEntityAttributeProviderTrait = DoobieRegulatedEntityAttributeProvider

  // Helper to get the count out of an option
  def countOfRegulatedEntityAttribute(listOpt: Option[List[RegulatedEntityAttributeTrait]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait RegulatedEntityAttributeProviderTrait {

  def getRegulatedEntityAttributes(regulatedEntityId: RegulatedEntityId): Future[Box[List[RegulatedEntityAttributeTrait]]]

  def getRegulatedEntityAttributeById(regulatedEntityAttributeId: String): Future[Box[RegulatedEntityAttributeTrait]]

  def createOrUpdateRegulatedEntityAttribute(
    regulatedEntityId: RegulatedEntityId,
    regulatedEntityAttributeId: Option[String],
    name: String,
    attributeType: RegulatedEntityAttributeType.Value,
    value: String,
    isActive: Option[Boolean]): Future[Box[RegulatedEntityAttributeTrait]]

  def deleteRegulatedEntityAttribute(regulatedEntityAttributeId: String): Future[Box[Boolean]]

  def deleteRegulatedEntityAttributesByRegulatedEntityId(regulatedEntityId: RegulatedEntityId): Future[Box[Boolean]]
}

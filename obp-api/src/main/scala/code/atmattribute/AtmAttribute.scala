package code.atmattribute

/* For ProductAttribute */

import com.openbankproject.commons.model.{AtmAttributeTrait, AtmId, BankId}
import com.openbankproject.commons.model.enums.AtmAttributeType
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.concurrent.Future

object AtmAttributeX extends SimpleInjector {

  val atmAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: AtmAttributeProviderTrait = DoobieAtmAttributeProvider

  // Helper to get the count out of an option
  def countOfAtmAttribute(listOpt: Option[List[AtmAttributeTrait]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait AtmAttributeProviderTrait extends MdcLoggable {

  def getAtmAttributesFromProvider(bankId: BankId, atmId: AtmId): Future[Box[List[AtmAttributeTrait]]]

  def getAtmAttributeById(AtmAttributeId: String): Future[Box[AtmAttributeTrait]]

  def createOrUpdateAtmAttribute(bankId : BankId,
                                 atmId: AtmId,
                                 AtmAttributeId: Option[String],
                                 name: String,
                                 attributeType: AtmAttributeType.Value,
                                 value: String,
                                 isActive: Option[Boolean]): Future[Box[AtmAttributeTrait]]
  def deleteAtmAttribute(AtmAttributeId: String): Future[Box[Boolean]]

  def deleteAtmAttributesByAtmId(atmId: AtmId): Future[Box[Boolean]]
  // End of Trait
}

package code.bankattribute

/* For ProductAttribute */

import code.api.util.APIUtil
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.BankAttributeType
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.concurrent.Future

object BankAttributeX extends SimpleInjector {

  val bankAttributeProvider = new Inject(buildOne _) {}

  def buildOne: BankAttributeProviderTrait = BankAttributeProvider

  // Helper to get the count out of an option
  def countOfBankAttribute(listOpt: Option[List[BankAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait BankAttributeProviderTrait extends MdcLoggable {

  def getBankAttributesFromProvider(bankId: BankId): Future[Box[List[BankAttribute]]]

  def getBankAttributeById(bankAttributeId: String): Future[Box[BankAttribute]]

  def createOrUpdateBankAttribute(bankId : BankId,
                                  bankAttributeId: Option[String],
                                  name: String,
                                  attributType: BankAttributeType.Value,
                                  value: String,
                                  isActive: Option[Boolean]): Future[Box[BankAttribute]]
  def deleteBankAttribute(bankAttributeId: String): Future[Box[Boolean]]
  // End of Trait
}

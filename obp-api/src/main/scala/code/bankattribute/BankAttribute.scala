package code.bankattribute

/* For ProductAttribute */

import code.api.util.APIUtil
import code.remotedata.RemotedataBankAttribute
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.BankAttributeType
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object BankAttributeX extends SimpleInjector {

  val bankAttributeProvider = new Inject(buildOne _) {}

  def buildOne: BankAttributeProviderTrait =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => BankAttributeProvider
      case true => RemotedataBankAttribute     // We will use Akka as a middleware
    }

  // Helper to get the count out of an option
  def countOfBankAttribute(listOpt: Option[List[BankAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait BankAttributeProviderTrait {

  private val logger = Logger(classOf[BankAttributeProviderTrait])

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

class RemotedataBankAttributeCaseClasses {
  case class getBankAttributesFromProvider(bank: BankId)

  case class getBankAttributeById(bankAttributeId: String)

  case class createOrUpdateBankAttribute(bankId : BankId,
                                         bankAttributeId: Option[String],
                                         name: String,
                                         attributType: BankAttributeType.Value,
                                         value: String, 
                                         isActive: Option[Boolean])

  case class deleteBankAttribute(bankAttributeId: String)
}

object RemotedataBankAttributeCaseClasses extends RemotedataBankAttributeCaseClasses

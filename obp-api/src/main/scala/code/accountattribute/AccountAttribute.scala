package code.accountattribute

/* For AccountAttribute */

import code.accountattribute.AccountAttribute.{AccountAttribute, AccountAttributeType}
import code.api.util.APIUtil
import code.products.Products.ProductCode
import code.remotedata.RemotedataAccountAttribute
import com.openbankproject.commons.model.{AccountId, BankId}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object AccountAttribute extends SimpleInjector {

  object AccountAttributeType extends Enumeration{
    type ProductAttributeType = Value
    val STRING = Value("STRING")

    val INTEGER = Value("INTEGER")

    val DOUBLE = Value("DOUBLE")

    val DATE_WITH_DAY = Value("DATE_WITH_DAY")
  }



  trait AccountAttribute {
    def bankId: BankId
    
    def accountId: AccountId

    def productCode: ProductCode

    def accountAttributeId: String

    def name: String

    def attributeType: AccountAttributeType.Value

    def value: String
  }


  val accountAttributeProvider = new Inject(buildOne _) {}

  def buildOne: AccountAttributeProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedAccountAttributeProvider
      case true => RemotedataAccountAttribute     // We will use Akka as a middleware
    }

  // Helper to get the count out of an option
  def countOfAccountAttribute(listOpt: Option[List[AccountAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait AccountAttributeProvider {

  private val logger = Logger(classOf[AccountAttributeProvider])

  def getAccountAttributesFromProvider(accountId: AccountId, productCode: ProductCode): Future[Box[List[AccountAttribute]]]

  def getAccountAttributeById(productAttributeId: String): Future[Box[AccountAttribute]]

  def createOrUpdateAccountAttribute(bankId: BankId,
                                     accountId: AccountId,
                                     productCode: ProductCode,
                                     accountAttributeId: Option[String],
                                     name: String,
                                     attributeType: AccountAttributeType.Value,
                                     value: String): Future[Box[AccountAttribute]]
  def deleteAccountAttribute(accountAttributeId: String): Future[Box[Boolean]]
  // End of Trait
}

class RemotedataAccountAttributeCaseClasses {
  case class getAccountAttributesFromProvider(accountId: AccountId, productCode: ProductCode)

  case class getAccountAttributeById(accountAttributeId: String)

  case class createOrUpdateAccountAttribute(bankId: BankId,
                                            accountId: AccountId,
                                            productCode: ProductCode,
                                            accountAttributeId: Option[String],
                                            name: String,
                                            attributeType: AccountAttributeType.Value,
                                            value: String)

  case class deleteAccountAttribute(accountAttributeId: String)
}

object RemotedataAccountAttributeCaseClasses extends RemotedataAccountAttributeCaseClasses

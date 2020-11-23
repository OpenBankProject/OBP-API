package code.accountattribute

/* For AccountAttribute */

import code.api.util.APIUtil
import code.remotedata.RemotedataAccountAttribute
import com.openbankproject.commons.model.enums.AccountAttributeType
import com.openbankproject.commons.model.{AccountAttribute, AccountId, BankId, BankIdAccountId, ProductAttribute, ProductCode, ViewId}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object AccountAttributeX extends SimpleInjector {

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
  def getAccountAttributesByAccount(bankId: BankId,
                                    accountId: AccountId): Future[Box[List[AccountAttribute]]]
  def getAccountAttributesByAccountCanBeSeenOnView(bankId: BankId, 
                                                   accountId: AccountId, 
                                                   viewId: ViewId): Future[Box[List[AccountAttribute]]]
  def getAccountAttributesByAccountsCanBeSeenOnView(accounts: List[BankIdAccountId], 
                                                    viewId: ViewId): Future[Box[List[AccountAttribute]]]

  def getAccountAttributeById(productAttributeId: String): Future[Box[AccountAttribute]]

  def createOrUpdateAccountAttribute(bankId: BankId,
                                     accountId: AccountId,
                                     productCode: ProductCode,
                                     accountAttributeId: Option[String],
                                     name: String,
                                     attributeType: AccountAttributeType.Value,
                                     value: String): Future[Box[AccountAttribute]]

  def createAccountAttributes(bankId: BankId,
                              accountId: AccountId,
                              productCode: ProductCode,
                              accountAttributes: List[ProductAttribute]): Future[Box[List[AccountAttribute]]]
  
  def deleteAccountAttribute(accountAttributeId: String): Future[Box[Boolean]]

  def getAccountIdsByParams(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]]

  // End of Trait
}

class RemotedataAccountAttributeCaseClasses {
  case class getAccountAttributesFromProvider(accountId: AccountId, productCode: ProductCode)
  case class getAccountAttributesByAccount(bankId: BankId,
                                           accountId: AccountId)
  case class getAccountAttributesByAccountCanBeSeenOnView(bankId: BankId, 
                                                          accountId: AccountId, 
                                                          viewId: ViewId) 
  case class getAccountAttributesByAccountsCanBeSeenOnView(accounts: List[BankIdAccountId],
                                                           viewId: ViewId)

  case class getAccountAttributeById(accountAttributeId: String)

  case class createOrUpdateAccountAttribute(bankId: BankId,
                                            accountId: AccountId,
                                            productCode: ProductCode,
                                            accountAttributeId: Option[String],
                                            name: String,
                                            attributeType: AccountAttributeType.Value,
                                            value: String)
  
  case class createAccountAttributes(bankId: BankId,
                                     accountId: AccountId,
                                     productCode: ProductCode,
                                     accountAttributes: List[ProductAttribute])

  case class deleteAccountAttribute(accountAttributeId: String)

  case class getAccountIdsByParams(bankId: BankId, params: Map[String, List[String]])
}

object RemotedataAccountAttributeCaseClasses extends RemotedataAccountAttributeCaseClasses

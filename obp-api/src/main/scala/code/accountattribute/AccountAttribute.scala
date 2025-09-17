package code.accountattribute

/* For AccountAttribute */

import code.api.util.APIUtil
import com.openbankproject.commons.model.enums.AccountAttributeType
import com.openbankproject.commons.model.{AccountAttribute, AccountId, BankId, BankIdAccountId, ProductAttribute, ProductCode, ViewId}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List
import scala.concurrent.Future

object AccountAttributeX extends SimpleInjector {

  val accountAttributeProvider = new Inject(buildOne _) {}

  def buildOne: AccountAttributeProvider = MappedAccountAttributeProvider

  // Helper to get the count out of an option
  def countOfAccountAttribute(listOpt: Option[List[AccountAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait AccountAttributeProvider extends MdcLoggable {

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
                                     value: String,
                                     productInstanceCode: Option[String]): Future[Box[AccountAttribute]]

  def createAccountAttributes(bankId: BankId,
                              accountId: AccountId,
                              productCode: ProductCode,
                              accountAttributes: List[ProductAttribute],
                              productInstanceCode: Option[String]): Future[Box[List[AccountAttribute]]]

  def deleteAccountAttribute(accountAttributeId: String): Future[Box[Boolean]]

  def getAccountIdsByParams(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]]

  // End of Trait
}

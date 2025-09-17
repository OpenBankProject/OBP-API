package code.customerattribute

/* For CustomerAttribute */

import code.api.util.APIUtil
import com.openbankproject.commons.dto.CustomerAndAttribute
import com.openbankproject.commons.model.enums.CustomerAttributeType
import com.openbankproject.commons.model.{BankId, Customer, CustomerAttribute, CustomerId}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List
import scala.concurrent.Future

object CustomerAttributeX extends SimpleInjector {

  val customerAttributeProvider = new Inject(buildOne _) {}

  def buildOne: CustomerAttributeProvider = MappedCustomerAttributeProvider

  // Helper to get the count out of an option
  def countOfCustomerAttribute(listOpt: Option[List[CustomerAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait CustomerAttributeProvider extends MdcLoggable {

  def getCustomerAttributesFromProvider(customerId: CustomerId): Future[Box[List[CustomerAttribute]]]
  def getCustomerAttributes(bankId: BankId,
                                    customerId: CustomerId): Future[Box[List[CustomerAttribute]]]

  def getCustomerIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]]

  def getCustomerAttributesForCustomers(customers: List[Customer]): Future[Box[List[CustomerAndAttribute]]]

  def getCustomerAttributeById(customerAttributeId: String): Future[Box[CustomerAttribute]]

  def createOrUpdateCustomerAttribute(bankId: BankId,
                                     customerId: CustomerId,
                                     customerAttributeId: Option[String],
                                     name: String,
                                     attributeType: CustomerAttributeType.Value,
                                     value: String): Future[Box[CustomerAttribute]]

  def createCustomerAttributes(bankId: BankId,
                              customerId: CustomerId,
                              customerAttributes: List[CustomerAttribute]): Future[Box[List[CustomerAttribute]]]

  def deleteCustomerAttribute(customerAttributeId: String): Future[Box[Boolean]]
  // End of Trait
}

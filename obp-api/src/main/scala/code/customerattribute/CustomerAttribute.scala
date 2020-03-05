package code.customerattribute

/* For CustomerAttribute */

import code.api.util.APIUtil
import code.remotedata.RemotedataCustomerAttribute
import com.openbankproject.commons.model.enums.CustomerAttributeType
import com.openbankproject.commons.model.{BankId, Customer, CustomerAttribute, CustomerId}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object CustomerAttributeX extends SimpleInjector {

  val customerAttributeProvider = new Inject(buildOne _) {}

  def buildOne: CustomerAttributeProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedCustomerAttributeProvider
      case true => RemotedataCustomerAttribute     // We will use Akka as a middleware
    }

  // Helper to get the count out of an option
  def countOfCustomerAttribute(listOpt: Option[List[CustomerAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait CustomerAttributeProvider {

  private val logger = Logger(classOf[CustomerAttributeProvider])

  def getCustomerAttributesFromProvider(customerId: CustomerId): Future[Box[List[CustomerAttribute]]]
  def getCustomerAttributes(bankId: BankId,
                                    customerId: CustomerId): Future[Box[List[CustomerAttribute]]]

  def getCustomerIdByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]]

  def getCustomerAttributesForCustomers(customers: List[Customer]): Future[Box[List[(Customer, List[CustomerAttribute])]]]
  
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

class RemotedataCustomerAttributeCaseClasses {
  case class getCustomerAttributesFromProvider(customerId: CustomerId)
  case class getCustomerAttributes(bankId: BankId,
                                           customerId: CustomerId)
  case class getCustomerIdByAttributeNameValues(bankId: BankId, params: Map[String, List[String]])
  case class getCustomerAttributesForCustomers(customers: List[Customer])

  case class getCustomerAttributeById(customerAttributeId: String)

  case class createOrUpdateCustomerAttribute(bankId: BankId,
                                            customerId: CustomerId,
                                            customerAttributeId: Option[String],
                                            name: String,
                                            attributeType: CustomerAttributeType.Value,
                                            value: String)
  
  case class createCustomerAttributes(bankId: BankId,
                                     customerId: CustomerId,
                                     customerAttributes: List[CustomerAttribute])

  case class deleteCustomerAttribute(customerAttributeId: String)
}

object RemotedataCustomerAttributeCaseClasses extends RemotedataCustomerAttributeCaseClasses

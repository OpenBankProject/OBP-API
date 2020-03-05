package code.remotedata

import akka.pattern.ask
import code.customerattribute.{CustomerAttributeProvider, MappedCustomerAttribute, RemotedataCustomerAttributeCaseClasses}
import code.actorsystem.ObpActorInit
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.CustomerAttributeType
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataCustomerAttribute extends ObpActorInit with CustomerAttributeProvider {

  val cc = RemotedataCustomerAttributeCaseClasses

  override def getCustomerAttributesFromProvider(customerId: CustomerId): Future[Box[List[CustomerAttribute]]] = 
    (actor ? cc.getCustomerAttributesFromProvider(customerId)).mapTo[Box[List[CustomerAttribute]]]
  
  override def getCustomerAttributes(bankId: BankId,
                                             customerId: CustomerId): Future[Box[List[CustomerAttribute]]] = 
    (actor ? cc.getCustomerAttributes(bankId, customerId)).mapTo[Box[List[CustomerAttribute]]]

  override def getCustomerIdByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] =
    (actor ? cc.getCustomerIdByAttributeNameValues(bankId, params)).mapTo[Box[List[String]]]

  override def getCustomerAttributesForCustomers(customers: List[Customer]): Future[Box[List[(Customer, List[CustomerAttribute])]]] =
    (actor ? cc.getCustomerAttributesForCustomers(customers: List[Customer])).mapTo[Box[List[(Customer, List[CustomerAttribute])]]]

  override def getCustomerAttributeById(customerAttributeId: String): Future[Box[CustomerAttribute]] = 
    (actor ? cc.getCustomerAttributeById(customerAttributeId)).mapTo[Box[CustomerAttribute]]

  override def createOrUpdateCustomerAttribute(bankId: BankId,
                                              customerId: CustomerId,
                                              customerAttributeId: Option[String],
                                              name: String,
                                              attributeType: CustomerAttributeType.Value,
                                              value: String): Future[Box[CustomerAttribute]] = 
    (actor ? cc.createOrUpdateCustomerAttribute(bankId, customerId, customerAttributeId , name , attributeType , value )).mapTo[Box[CustomerAttribute]]

  override def createCustomerAttributes(bankId: BankId,
                                              customerId: CustomerId,
                                              customerAttributes: List[CustomerAttribute]): Future[Box[List[CustomerAttribute]]] = 
    (actor ? cc.createCustomerAttributes(bankId, customerId, customerAttributes)).mapTo[Box[List[CustomerAttribute]]]

  override def deleteCustomerAttribute(customerAttributeId: String): Future[Box[Boolean]] = 
    (actor ? cc.deleteCustomerAttribute(customerAttributeId)).mapTo[Box[Boolean]]

}

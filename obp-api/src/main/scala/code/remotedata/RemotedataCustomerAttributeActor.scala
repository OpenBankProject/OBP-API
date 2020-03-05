package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.customerattribute.{MappedCustomerAttributeProvider, RemotedataCustomerAttributeCaseClasses}
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.CustomerAttributeType
import com.openbankproject.commons.model.{BankId, CustomerAttribute, CustomerId, ProductAttribute, ProductCode}

import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataCustomerAttributeActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedCustomerAttributeProvider
  val cc = RemotedataCustomerAttributeCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getCustomerAttributesFromProvider(customerId: CustomerId) =>
      logger.debug(s"getCustomerAttributesFromProvider(${customerId})")
      mapper.getCustomerAttributesFromProvider(customerId) pipeTo sender
      
    case cc.getCustomerAttributes(bankId: BankId, customerId: CustomerId) =>
      logger.debug(s"getCustomerAttributes(${bankId}, ${customerId})")
      mapper.getCustomerAttributes(bankId, customerId) pipeTo sender

    case cc.getCustomerIdByAttributeNameValues(bankId: BankId, nameValues: Map[String, List[String]]) =>
      logger.debug(s"getCustomerIdByAttributeNameValues($bankId, $nameValues)")
      mapper.getCustomerIdByAttributeNameValues(bankId, nameValues) pipeTo sender

    case cc.getCustomerAttributeById(customerAttributeId: String) =>
      logger.debug(s"getCustomerAttributeById(${customerAttributeId})")
      mapper.getCustomerAttributeById(customerAttributeId) pipeTo sender

    case cc.createOrUpdateCustomerAttribute(bankId: BankId,
            customerId: CustomerId,
            customerAttributeId: Option[String],
            name: String,
            attributeType: CustomerAttributeType.Value,
            value: String) =>
      logger.debug(s"createOrUpdateCustomerAttribute(${bankId}, ${customerId}, ${customerAttributeId}, ${name}, ${attributeType}, ${value})")
      mapper.createOrUpdateCustomerAttribute(bankId, customerId,
        customerAttributeId,
        name,
        attributeType,
        value) pipeTo sender
      //TODO 这里的 productArributes 是什么? 
    case cc.createCustomerAttributes(bankId: BankId,
            customerId: CustomerId,
            customerAttributes: List[CustomerAttribute]) =>
      mapper.createCustomerAttributes(
        bankId, 
        customerId,
        customerAttributes) pipeTo sender

    case cc.deleteCustomerAttribute(customerAttributeId: String) =>
      logger.debug(s"deleteCustomerAttribute(${customerAttributeId})")
      mapper.deleteCustomerAttribute(customerAttributeId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }

}



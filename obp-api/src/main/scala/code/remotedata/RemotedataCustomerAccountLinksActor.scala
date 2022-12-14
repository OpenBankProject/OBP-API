package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.customeraccountlinks.{MappedCustomerAccountLinkProvider, RemotedataCustomerAccountLinkProviderCaseClass}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataCustomerAccountLinksActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedCustomerAccountLinkProvider
  val cc = RemotedataCustomerAccountLinkProviderCaseClass

  def receive: PartialFunction[Any, Unit] = {

    case cc.createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType) =>
      logger.debug(s"createCustomerAccountLink($customerId, $bankId, $accountId, $relationshipType)")
      sender ! (mapper.createCustomerAccountLink(accountId, bankId: String, customerId, relationshipType))
      
    case cc.getOrCreateCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType) =>
      logger.debug(s"getOrCreateCustomerAccountLink($customerId, $bankId, $accountId, $relationshipType)")
      sender ! (mapper.getOrCreateCustomerAccountLink(customerId, bankId, accountId, relationshipType))

    case cc.getCustomerAccountLinkByCustomerId(customerId: String) =>
      logger.debug(s"getCustomerAccountLinkByCustomerId($customerId)")
      sender ! (mapper.getCustomerAccountLinkByCustomerId(customerId))
      
    case cc.getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String)=>
      logger.debug(s"getCustomerAccountLinksByBankIdAccountId(bankId($bankId), accountId($accountId))")
      sender ! (mapper.getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String))
      
    case cc.getCustomerAccountLinksByCustomerId(customerId: String) =>
      logger.debug(s"getCustomerAccountLinksByCustomerId($customerId)")
      sender ! (mapper.getCustomerAccountLinksByCustomerId(customerId))

    case cc.getCustomerAccountLinksByAccountId(bankId: String, accountId: String) =>
      logger.debug(s"getCustomerAccountLinksByAccountId($bankId, $accountId)")
      sender ! (mapper.getCustomerAccountLinksByAccountId(bankId, accountId))
      
    case cc.getCustomerAccountLinkById(customerAccountLinkId: String)=>
      logger.debug(s"getCustomerAccountLinkById($customerAccountLinkId)")
      sender ! (mapper.getCustomerAccountLinkById(customerAccountLinkId))
      
    case cc.updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String)=>
      logger.debug(s"updateCustomerAccountLinkById($customerAccountLinkId, $relationshipType)")
      sender ! (mapper.updateCustomerAccountLinkById(customerAccountLinkId, relationshipType))

    case cc.getCustomerAccountLinks() =>
      logger.debug(s"getCustomerAccountLinks()")
      sender ! (mapper.getCustomerAccountLinks)

    case cc.bulkDeleteCustomerAccountLinks() =>
      logger.debug(s"bulkDeleteCustomerAccountLinks()")
      sender ! (mapper.bulkDeleteCustomerAccountLinks())
      
    case cc.deleteCustomerAccountLinkById(customerAccountLinkId) =>
      logger.debug(s"deleteCustomerAccountLink($customerAccountLinkId)")
      mapper.deleteCustomerAccountLinkById(customerAccountLinkId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


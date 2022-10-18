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

    case cc.createCustomerAccountLink(customerId: String, accountId: String, relationshipType) =>
      logger.debug(s"createCustomerAccountLink($accountId, $relationshipType)")
      sender ! (mapper.createCustomerAccountLink(accountId, customerId, relationshipType))
      
    case cc.getOrCreateCustomerAccountLink(customerId: String, accountId: String, relationshipType) =>
      logger.debug(s"getOrCreateCustomerAccountLink($accountId, $relationshipType)")
      sender ! (mapper.getOrCreateCustomerAccountLink(accountId, customerId, relationshipType))

    case cc.getCustomerAccountLinkByCustomerId(customerId: String) =>
      logger.debug(s"getCustomerAccountLinkByCustomerId($customerId)")
      sender ! (mapper.getCustomerAccountLinkByCustomerId(customerId))
      
    case cc.getCustomerAccountLinksByCustomerId(customerId: String) =>
      logger.debug(s"getCustomerAccountLinksByCustomerId($customerId)")
      sender ! (mapper.getCustomerAccountLinksByCustomerId(customerId))

    case cc.getCustomerAccountLinksByAccountId(accountId: String) =>
      logger.debug(s"getCustomerAccountLinksByAccountId($accountId)")
      sender ! (mapper.getCustomerAccountLinksByAccountId(accountId))
      
    case cc.getCustomerAccountLinkById(customerAccountLinkId: String)=>
      logger.debug(s"getCustomerAccountLinkById($customerAccountLinkId)")
      sender ! (mapper.getCustomerAccountLinkById(customerAccountLinkId))
      
    case cc.updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String)=>
      logger.debug(s"updateCustomerAccountLinkById($customerAccountLinkId, $relationshipType)")
      sender ! (mapper.updateCustomerAccountLinkById(customerAccountLinkId, relationshipType))

    case cc.getCustomerAccountLink(customerId: String, accountId: String) =>
      logger.debug(s"getCustomerAccountLink($accountId, $customerId)")
      sender ! (mapper.getCustomerAccountLink(accountId, customerId))

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


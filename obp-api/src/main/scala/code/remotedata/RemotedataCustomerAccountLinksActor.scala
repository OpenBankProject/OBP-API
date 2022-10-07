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
      
    case cc.getOCreateCustomerAccountLink(customerId: String, accountId: String, relationshipType) =>
      logger.debug(s"getOCreateCustomerAccountLink($accountId, $relationshipType)")
      sender ! (mapper.getOCreateCustomerAccountLink(accountId, customerId, relationshipType))

    case cc.getCustomerAccountLinkByCustomerId(customerId: String) =>
      logger.debug(s"getCustomerAccountLinkByCustomerId($customerId)")
      sender ! (mapper.getCustomerAccountLinkByCustomerId(customerId))
      
    case cc.getCustomerAccountLinksByCustomerId(customerId: String) =>
      logger.debug(s"getCustomerAccountLinksByCustomerId($customerId)")
      sender ! (mapper.getCustomerAccountLinksByCustomerId(customerId))

    case cc.getCustomerAccountLinksByAccountId(accountId: String) =>
      logger.debug(s"getCustomerAccountLinksByAccountId($accountId)")
      sender ! (mapper.getCustomerAccountLinksByAccountId(accountId))

    case cc.getCustomerAccountLink(customerId: String, accountId: String) =>
      logger.debug(s"getCustomerAccountLink($accountId, $customerId)")
      sender ! (mapper.getCustomerAccountLink(accountId, customerId))

    case cc.getCustomerAccountLinks() =>
      logger.debug(s"getCustomerAccountLinks()")
      sender ! (mapper.getCustomerAccountLinks)

    case cc.bulkDeleteCustomerAccountLinks() =>
      logger.debug(s"bulkDeleteCustomerAccountLinks()")
      sender ! (mapper.bulkDeleteCustomerAccountLinks())
      
    case cc.deleteCustomerAccountLink(customerAccountLinkId) =>
      logger.debug(s"deleteCustomerAccountLink($customerAccountLinkId)")
      mapper.deleteCustomerAccountLink(customerAccountLinkId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


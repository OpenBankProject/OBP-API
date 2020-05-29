package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.usercustomerlinks.{MappedUserCustomerLinkProvider, RemotedataUserCustomerLinkProviderCaseClass}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataUserCustomerLinksActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserCustomerLinkProvider
  val cc = RemotedataUserCustomerLinkProviderCaseClass

  def receive: PartialFunction[Any, Unit] = {

    case cc.createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean) =>
      logger.debug(s"createUserCustomerLink($userId, $dateInserted, $isActive)")
      sender ! (mapper.createUserCustomerLink(userId, customerId, dateInserted, isActive))
      
    case cc.getOCreateUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean) =>
      logger.debug(s"getOCreateUserCustomerLink($userId, $dateInserted, $isActive)")
      sender ! (mapper.getOCreateUserCustomerLink(userId, customerId, dateInserted, isActive))

    case cc.getUserCustomerLinkByCustomerId(customerId: String) =>
      logger.debug(s"getUserCustomerLinkByCustomerId($customerId)")
      sender ! (mapper.getUserCustomerLinkByCustomerId(customerId))
      
    case cc.getUserCustomerLinksByCustomerId(customerId: String) =>
      logger.debug(s"getUserCustomerLinksByCustomerId($customerId)")
      sender ! (mapper.getUserCustomerLinksByCustomerId(customerId))

    case cc.getUserCustomerLinksByUserId(userId: String) =>
      logger.debug(s"getUserCustomerLinksByUserId($userId)")
      sender ! (mapper.getUserCustomerLinksByUserId(userId))

    case cc.getUserCustomerLink(userId: String, customerId: String) =>
      logger.debug(s"getUserCustomerLink($userId, $customerId)")
      sender ! (mapper.getUserCustomerLink(userId, customerId))

    case cc.getUserCustomerLinks() =>
      logger.debug(s"getUserCustomerLinks()")
      sender ! (mapper.getUserCustomerLinks)

    case cc.bulkDeleteUserCustomerLinks() =>
      logger.debug(s"bulkDeleteUserCustomerLinks()")
      sender ! (mapper.bulkDeleteUserCustomerLinks())
      
    case cc.deleteUserCustomerLink(userCustomerLinkId) =>
      logger.debug(s"deleteUserCustomerLink($userCustomerLinkId)")
      mapper.deleteUserCustomerLink(userCustomerLinkId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.event.Logging
import code.usercustomerlinks.{MappedUserCustomerLinkProvider, RemotedataUserCustomerLinkProviderCaseClass}


class RemotedataUserCustomerLinksActor extends Actor with ActorHelper {

  val logger = Logging(context.system, this)

  val mapper = MappedUserCustomerLinkProvider
  val cc = RemotedataUserCustomerLinkProviderCaseClass

  def receive = {

    case cc.createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean) =>
      logger.info("createUserCustomerLink(" + userId + ", " + dateInserted + ", " + isActive + ")")
      sender ! extractResult(mapper.createUserCustomerLink(userId, customerId, dateInserted, isActive))

    case cc.getUserCustomerLinkByCustomerId(customerId: String) =>
      logger.info("getUserCustomerLinkByCustomerId(" + customerId + ")")
      sender ! extractResult(mapper.getUserCustomerLinkByCustomerId(customerId))

    case cc.getUserCustomerLinkByUserId(userId: String) =>
      logger.info("getUserCustomerLinkByUserId(" + userId + ")")
      sender ! extractResult(mapper.getUserCustomerLinkByUserId(userId))

    case cc.getUserCustomerLink(userId: String, customerId: String) =>
      logger.info("getUserCustomerLink(" + userId + ", " + customerId + ")")
      sender ! extractResult(mapper.getUserCustomerLink(userId, customerId))

    case cc.getUserCustomerLinks() =>
      logger.info("getUserCustomerLinks()")
      sender ! extractResult(mapper.getUserCustomerLinks)

    case cc.bulkDeleteUserCustomerLinks() =>
      logger.info("bulkDeleteUserCustomerLinks()")
      sender ! extractResult(mapper.bulkDeleteUserCustomerLinks())

    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


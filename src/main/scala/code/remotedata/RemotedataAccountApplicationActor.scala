package code.remotedata

import akka.actor.Actor
import code.accountapplication.{MappedAccountApplicationProvider, RemotedataAccountApplicationCaseClasses}
import code.actorsystem.ObpActorHelper
import code.products.Products.ProductCode
import code.util.Helper.MdcLoggable


class RemotedataAccountApplicationActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedAccountApplicationProvider
  val cc = RemotedataAccountApplicationCaseClasses

  def receive = {

    case cc.getAll() =>
      logger.debug("getAll()")
      sender ! extractResult(mapper.getAll())
      
    case cc.getById(accountApplicationId: String) =>
      logger.debug(s"getById(${accountApplicationId})")
      sender ! extractResult(mapper.getById(accountApplicationId))

    case cc.createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String]) =>
      logger.debug(s"createAccountApplication(${productCode}, ${userId}, ${customerId}")
      sender ! extractResult(mapper.createAccountApplication(productCode, userId, customerId))
      
    case cc.updateStatus(accountApplicationId: String, status: String) =>
      logger.debug(s"updateStatus(${accountApplicationId}, ${status})")
      sender ! extractResult(mapper.updateStatus(accountApplicationId, status))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }
}


package code.remotedata

import akka.actor.Actor
import code.accountapplication.{MappedAccountApplicationProvider, RemotedataAccountApplicationCaseClasses}
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import akka.pattern.pipe
import com.openbankproject.commons.model.ProductCode

import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataAccountApplicationActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedAccountApplicationProvider
  val cc = RemotedataAccountApplicationCaseClasses

  def receive = {

    case cc.getAll() =>
      logger.debug("getAll()")
      mapper.getAll() pipeTo sender
      
    case cc.getById(accountApplicationId: String) =>
      logger.debug(s"getById(${accountApplicationId})")
      mapper.getById(accountApplicationId) pipeTo sender

    case cc.createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String]) =>
      logger.debug(s"createAccountApplication(${productCode}, ${userId}, ${customerId}")
      mapper.createAccountApplication(productCode, userId, customerId)  pipeTo sender
      
    case cc.updateStatus(accountApplicationId: String, status: String) =>
      logger.debug(s"updateStatus(${accountApplicationId}, ${status})")
      mapper.updateStatus(accountApplicationId, status) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }
}


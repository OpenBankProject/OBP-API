package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.taxresidence.{MappedTaxResidenceProvider, RemotedataTaxResidenceCaseClasses}
import code.util.Helper.MdcLoggable
import akka.pattern.pipe
import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataTaxResidenceActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedTaxResidenceProvider
  val cc = RemotedataTaxResidenceCaseClasses

  def receive = {

    case cc.getTaxResidence(customerId: String) =>
      logger.debug("getTaxResidence(" + customerId + ")")
      mapper.getTaxResidence(customerId) pipeTo sender

    case cc.createTaxResidence(customerId: String, domain: String, taxNumber: String) =>
      logger.debug("addTaxResidence(" + customerId + ", " + domain + ", " + taxNumber + ")")
      mapper.createTaxResidence(customerId, domain, taxNumber) pipeTo sender

    case cc.deleteTaxResidence(taxResidenceId: String) =>
      logger.debug("deleteTaxResidence(" + taxResidenceId + ")")
      mapper.deleteTaxResidence(taxResidenceId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}



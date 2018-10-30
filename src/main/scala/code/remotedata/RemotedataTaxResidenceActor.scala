package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.taxresidence.{MappedTaxResidenceProvider, RemotedataTaxResidenceCaseClasses}
import code.util.Helper.MdcLoggable

class RemotedataTaxResidenceActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedTaxResidenceProvider
  val cc = RemotedataTaxResidenceCaseClasses

  def receive = {

    case cc.getTaxResidence(customerId: String) =>
      logger.debug("getTaxResidence(" + customerId + ")")
      sender ! (mapper.getTaxResidenceRemote(customerId))

    case cc.createTaxResidence(customerId: String, domain: String, taxNumber: String) =>
      logger.debug("addTaxResidence(" + customerId + ", " + domain + ", " + taxNumber + ")")
      sender ! (mapper.createTaxResidenceRemote(customerId, domain, taxNumber))

    case cc.deleteTaxResidence(taxResidenceId: String) =>
      logger.debug("deleteTaxResidence(" + taxResidenceId + ")")
      sender ! (mapper.deleteTaxResidenceRemote(taxResidenceId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}



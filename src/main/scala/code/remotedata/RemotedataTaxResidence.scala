package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.taxresidence.{RemotedataTaxResidenceCaseClasses, TaxResidence, TaxResidenceProvider}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataTaxResidence extends ObpActorInit with TaxResidenceProvider {

  val cc = RemotedataTaxResidenceCaseClasses

  def getTaxResidence(customerId: String): Future[Box[List[TaxResidence]]] =
    (actor ? cc.getTaxResidence(customerId)).mapTo[Box[List[TaxResidence]]]

  def createTaxResidence(customerId: String, domain: String, taxNumber: String): Future[Box[TaxResidence]] =
    (actor ? cc.createTaxResidence(customerId, domain, taxNumber)).mapTo[Box[TaxResidence]]

  def deleteTaxResidence(taxResidenceId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteTaxResidence(taxResidenceId)).mapTo[Box[Boolean]]

}

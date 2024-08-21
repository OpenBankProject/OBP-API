package code.taxresidence

import code.api.util.APIUtil
import com.openbankproject.commons.model.TaxResidence
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object TaxResidenceX extends SimpleInjector {

  val taxResidence = new Inject(buildOne _) {}

  def buildOne: TaxResidenceProvider = MappedTaxResidenceProvider
  
}

trait TaxResidenceProvider {
  def getTaxResidence(customerId: String): Future[Box[List[TaxResidence]]]
  def createTaxResidence(customerId: String, domain: String, taxNumber: String): Future[Box[TaxResidence]]
  def deleteTaxResidence(taxResidenceId: String): Future[Box[Boolean]]
}
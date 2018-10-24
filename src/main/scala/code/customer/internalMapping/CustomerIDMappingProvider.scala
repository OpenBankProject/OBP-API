package code.customer.internalMapping

import code.model.BankId
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object CustomerIDMappingProvider extends SimpleInjector {

  val customerIDMappingProvider = new Inject(buildOne _) {}

  def buildOne: CustomerIDMappingProvider = MappedCustomerIDMappingProvider

}

trait CustomerIDMappingProvider {
  
  def getOrCreateCustomerId(bankId: BankId, customerNumber: String): Box[CustomerIDMapping]
  
}

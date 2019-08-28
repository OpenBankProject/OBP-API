package code.customer.internalMapping

import com.openbankproject.commons.model.{BankId, CustomerId}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object CustomerIdMappingProvider extends SimpleInjector {

  val customerIdMappingProvider = new Inject(buildOne _) {}

  def buildOne: CustomerIdMappingProvider = MappedCustomerIdMappingProvider

}

trait CustomerIdMappingProvider {

  def getOrCreateCustomerId(customerPlainTextReference: String): Box[CustomerId]

  def getCustomerPlainTextReference(customerId: CustomerId): Box[String]

}

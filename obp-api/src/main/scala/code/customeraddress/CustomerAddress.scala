package code.customeraddress

import code.api.util.APIUtil
import com.openbankproject.commons.model.CustomerAddress
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object CustomerAddressX extends SimpleInjector {

  val address = new Inject(buildOne _) {}

  def buildOne: CustomerAddressProvider = MappedCustomerAddressProvider
  
}

trait CustomerAddressProvider {
  def getAddress(customerId: String): Future[Box[List[CustomerAddress]]]
  def createAddress(customerId: String,
                    line1: String,
                    line2: String,
                    line3: String,
                    city: String,
                    county: String,
                    state: String,
                    postcode: String,
                    countryCode: String,
                    tags: String,
                    status: String
                ): Future[Box[CustomerAddress]]
  def updateAddress(customerAddressId: String,
                    line1: String,
                    line2: String,
                    line3: String,
                    city: String,
                    county: String,
                    state: String,
                    postcode: String,
                    countryCode: String,
                    tags: String,
                    status: String
                   ): Future[Box[CustomerAddress]]
  def deleteAddress(customerAddressId: String): Future[Box[Boolean]]
}
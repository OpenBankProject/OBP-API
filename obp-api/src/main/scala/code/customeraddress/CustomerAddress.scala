package code.customeraddress

import code.api.util.APIUtil
import code.remotedata.RemotedataCustomerAddress
import com.openbankproject.commons.model.CustomerAddress
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object CustomerAddressX extends SimpleInjector {

  val address = new Inject(buildOne _) {}

  def buildOne: CustomerAddressProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedCustomerAddressProvider
      case true => RemotedataCustomerAddress     // We will use Akka as a middleware
    }
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


class RemotedataCustomerAddressCaseClasses {
  case class getAddress(customerId: String)
  case class updateAddress(customerAddressId: String,
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
                       )
  case class createAddress(customerId: String,
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
                       )
  case class deleteAddress(customerAddressId: String)
}

object RemotedataCustomerAddressCaseClasses extends RemotedataCustomerAddressCaseClasses
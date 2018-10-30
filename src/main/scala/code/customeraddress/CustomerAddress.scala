package code.customeraddress

import java.util.Date

import code.api.util.APIUtil
import code.remotedata.RemotedataCustomerAddress
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object CustomerAddress extends SimpleInjector {

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
                    status: String
                ): Future[Box[CustomerAddress]]
  def deleteAddress(customerAddressId: String): Future[Box[Boolean]]
}

trait CustomerAddress {
  def customerId: String
  def customerAddressId: String
  def line1: String
  def line2: String
  def line3: String
  def city: String
  def county: String
  def state: String
  def postcode: String
  def countryCode: String
  def status: String
  def insertDate: Date
}


class RemotedataCustomerAddressCaseClasses {
  case class getAddress(customerId: String)
  case class createAddress(customerId: String,
                           line1: String,
                           line2: String,
                           line3: String,
                           city: String,
                           county: String,
                           state: String,
                           postcode: String,
                           countryCode: String,
                           status: String
                       )
  case class deleteAddress(customerAddressId: String)
}

object RemotedataCustomerAddressCaseClasses extends RemotedataCustomerAddressCaseClasses
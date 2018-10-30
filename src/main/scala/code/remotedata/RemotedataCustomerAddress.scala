package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.customeraddress.{CustomerAddress, CustomerAddressProvider, RemotedataCustomerAddressCaseClasses}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataCustomerAddress extends ObpActorInit with CustomerAddressProvider {

  val cc = RemotedataCustomerAddressCaseClasses

  def getAddress(customerId: String): Future[Box[List[CustomerAddress]]] =
    (actor ? cc.getAddress(customerId)).mapTo[Box[List[CustomerAddress]]]

  def createAddress(customerId: String,
                    line1: String,
                    line2: String,
                    line3: String,
                    city: String,
                    county: String,
                    state: String,
                    postcode: String,
                    countryCode: String,
                    status: String): Future[Box[CustomerAddress]] =
    (actor ? cc.createAddress(customerId,
      line1,
      line2,
      line3,
      city,
      county,
      state,
      postcode,
      countryCode,
      status)).mapTo[Box[CustomerAddress]]


  def deleteAddress(customerAddressId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteAddress(customerAddressId)).mapTo[Box[Boolean]]


}

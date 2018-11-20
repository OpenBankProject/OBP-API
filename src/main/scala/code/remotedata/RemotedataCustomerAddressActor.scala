package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.customeraddress.{MappedCustomerAddressProvider, RemotedataCustomerAddressCaseClasses}
import code.util.Helper.MdcLoggable
import akka.pattern.pipe
import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataCustomerAddressActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedCustomerAddressProvider
  val cc = RemotedataCustomerAddressCaseClasses

  def receive = {

    case cc.getAddress(customerId: String) =>
      logger.debug("getAddress(" + customerId + ")")
      mapper.getAddress(customerId) pipeTo sender

    case cc.createAddress(customerId: String,
                      line1: String,
                      line2: String,
                      line3: String,
                      city: String,
                      county: String,
                      state: String,
                      postcode: String,
                      countryCode: String,
                      status: String) =>
      logger.debug("createAddress(" + customerId + ", " + line1 + ", " + line2 + ")")
      mapper.createAddress(customerId,
                           line1,
                           line2,
                           line3,
                           city,
                           county,
                           state,
                           postcode,
                           countryCode,
                           status) pipeTo sender

    case cc.deleteAddress(customerAddressId: String) =>
      logger.debug("deleteAddress(" + customerAddressId + ")")
      mapper.deleteAddress(customerAddressId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}



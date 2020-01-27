package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.customeraddress.{MappedCustomerAddressProvider, RemotedataCustomerAddressCaseClasses}
import code.util.Helper.MdcLoggable
import akka.pattern.pipe
import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataCustomerAddressActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedCustomerAddressProvider
  val cc = RemotedataCustomerAddressCaseClasses

  def receive = {

    case cc.getAddress(customerId: String) =>
      logger.debug(s"getAddress($customerId)")
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
                      tags: String,
                      status: String) =>
      logger.debug(s"createAddress($customerId, $line1, $line2)")
      mapper.createAddress(customerId,
                           line1,
                           line2,
                           line3,
                           city,
                           county,
                           state,
                           postcode,
                           countryCode,
                           tags,
                           status) pipeTo sender
    case cc.updateAddress(customeraddressId: String,
                      line1: String,
                      line2: String,
                      line3: String,
                      city: String,
                      county: String,
                      state: String,
                      postcode: String,
                      countryCode: String,
                      tags: String,
                      status: String) =>
      logger.debug(s"updateAddress($customeraddressId, $line1, $line2)")
      mapper.updateAddress(customeraddressId,
                           line1,
                           line2,
                           line3,
                           city,
                           county,
                           state,
                           postcode,
                           countryCode,
                           tags,
                           status) pipeTo sender

    case cc.deleteAddress(customerAddressId: String) =>
      logger.debug(s"deleteAddress($customerAddressId)")
      mapper.deleteAddress(customerAddressId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}



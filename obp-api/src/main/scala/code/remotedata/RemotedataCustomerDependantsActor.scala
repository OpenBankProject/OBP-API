package code.remotedata

import akka.actor.Actor
import code.CustomerDependants.{ MappedCustomerDependants, RemotedataCustomerDependantsCaseClasses}
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{BankId, CustomerAttribute, CustomerDependant, CustomerId}

import scala.collection.immutable.List

class RemotedataCustomerDependantsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedCustomerDependants
  val cc = RemotedataCustomerDependantsCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.createCustomerDependants(mapperCustomerPrimaryKey: Long, customerDependants: List[CustomerDependant]) =>
      logger.debug(s"createCustomerDependants(${mapperCustomerPrimaryKey}, ${customerDependants})")
      sender ! (mapper.createCustomerDependants(mapperCustomerPrimaryKey: Long, customerDependants: List[CustomerDependant]))
      
    case cc.getCustomerDependantsByCustomerPrimaryKey(mapperCustomerPrimaryKey: Long) =>
      logger.debug(s"getCustomerDependantsByCustomerPrimaryKey(${mapperCustomerPrimaryKey})")
      sender !  (mapper.getCustomerDependantsByCustomerPrimaryKey(mapperCustomerPrimaryKey: Long))

  }

}



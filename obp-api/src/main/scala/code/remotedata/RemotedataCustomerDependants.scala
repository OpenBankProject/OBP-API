package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.CustomerDependants.{CustomerDependants, MappedCustomerDependant, RemotedataCustomerDependantsCaseClasses}
import com.openbankproject.commons.model.CustomerDependant

import scala.collection.immutable.List


object RemotedataCustomerDependants extends ObpActorInit with CustomerDependants {

  val cc = RemotedataCustomerDependantsCaseClasses

  override def createCustomerDependants(mapperCustomerPrimaryKey: Long,customerDependants: List[CustomerDependant]): List[MappedCustomerDependant] = getValueFromFuture(
    (actor ? cc.createCustomerDependants(mapperCustomerPrimaryKey: Long, customerDependants: List[CustomerDependant])).mapTo[List[MappedCustomerDependant]]
  )
  
  override def getCustomerDependantsByCustomerPrimaryKey(mapperCustomerPrimaryKey: Long): List[MappedCustomerDependant] = getValueFromFuture(
    (actor ? cc.getCustomerDependantsByCustomerPrimaryKey(mapperCustomerPrimaryKey: Long)).mapTo[List[MappedCustomerDependant]]
  )

}

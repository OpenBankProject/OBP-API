package code.CustomerDependants

import code.api.util.APIUtil
import com.openbankproject.commons.model.CustomerDependant
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List

object CustomerDependants extends SimpleInjector {

  val CustomerDependants = new Inject(buildOne _) {}

  def buildOne: CustomerDependants = MappedCustomerDependants

}

trait CustomerDependants {
  //Note: Here is tricky, it return the MappedCustomerDependant not the CustomerDependantTrait, because it will be used in `one-to-many` model ...
  def createCustomerDependants(mapperCustomerPrimaryKey: Long, customerDependants: List[CustomerDependant]): List[MappedCustomerDependant]
  def getCustomerDependantsByCustomerPrimaryKey(mapperCustomerPrimaryKey: Long): List[MappedCustomerDependant]
}
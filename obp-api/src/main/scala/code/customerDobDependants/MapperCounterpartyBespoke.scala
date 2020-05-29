package code.CustomerDependants

import code.customer.MappedCustomer
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.CustomerDependant
import net.liftweb.mapper.{MappedDateTime, _}
import scala.collection.immutable.List

class MappedCustomerDependant extends LongKeyedMapper[MappedCustomerDependant] with IdPK {
  def getSingleton = MappedCustomerDependant
  
  object mCustomer extends MappedLongForeignKey(this, MappedCustomer)
  object mDateOfBirth extends MappedDateTime(this)
  
}
object MappedCustomerDependant extends MappedCustomerDependant with LongKeyedMetaMapper[MappedCustomerDependant]{}


object MappedCustomerDependants extends CustomerDependants with MdcLoggable{
  
  def createCustomerDependants(mapperCustomerPrimaryKey: Long, customerDependants: List[CustomerDependant]): List[MappedCustomerDependant]= {
    customerDependants.map(
      customerDependant =>
        MappedCustomerDependant
          .create
          .mCustomer(mapperCustomerPrimaryKey)
          .mDateOfBirth(customerDependant.dateOfBirth)
          .saveMe()
    )
  }
  
  def getCustomerDependantsByCustomerPrimaryKey(mapperCustomerPrimaryKey: Long): List[MappedCustomerDependant] =
    MappedCustomerDependant
      .findAll(
        By(MappedCustomerDependant.mCustomer, mapperCustomerPrimaryKey)
      )
  
}
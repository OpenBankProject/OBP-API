package code.customer.internalMapping

import code.util.MappedUUID
import com.openbankproject.commons.model.{BankId, CustomerId}
import net.liftweb.mapper._

class MappedCustomerIdMapping extends CustomerIdMapping with LongKeyedMapper[MappedCustomerIdMapping] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerIdMapping

  object mCustomerId extends MappedUUID(this)
  object mCustomerPlainTextReference extends MappedString(this, 255)

  override def customerId = CustomerId(mCustomerId.get)
  override def customerPlainTextReference = mCustomerPlainTextReference.get


  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  object mBankId extends MappedString(this, 50)
  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  object mCustomerNumber extends MappedString(this, 50)
  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  override def bankId = BankId(mBankId.get)
  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  override def customerNumber: String = mCustomerNumber.get

}

object MappedCustomerIdMapping extends MappedCustomerIdMapping with LongKeyedMetaMapper[MappedCustomerIdMapping] {
  //one customer info per bank for each api user
  override def dbIndexes = UniqueIndex(mCustomerId) :: UniqueIndex(mCustomerId, mCustomerPlainTextReference) :: super.dbIndexes
}
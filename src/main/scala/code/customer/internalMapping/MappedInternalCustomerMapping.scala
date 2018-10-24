package code.customer.internalMapping

import code.model.{BankId, CustomerId}
import code.util.MappedUUID
import net.liftweb.mapper._

class MappedCustomerIDMapping extends CustomerIDMapping with LongKeyedMapper[MappedCustomerIDMapping] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerIDMapping

  // Unique
  object mCustomerId extends MappedUUID(this)
  object mCustomerNumber extends MappedString(this, 50)
  object mBankId extends MappedString(this, 50)

  override def customerId = CustomerId(mCustomerId.get) 
  override def bankId = BankId(mBankId.get)
  override def customerNumber: String = mCustomerNumber.get
  
}

object MappedCustomerIDMapping extends MappedCustomerIDMapping with LongKeyedMetaMapper[MappedCustomerIDMapping] {
  //one customer info per bank for each api user
  override def dbIndexes = UniqueIndex(mCustomerId) :: UniqueIndex(mBankId, mCustomerNumber) :: super.dbIndexes
}
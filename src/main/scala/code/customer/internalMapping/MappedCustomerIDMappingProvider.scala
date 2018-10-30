package code.customer.internalMapping

import code.model.{BankId, CustomerId}
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.mapper.By


object MappedCustomerIDMappingProvider extends CustomerIDMappingProvider with MdcLoggable
{
  
  override def getOrCreateCustomerIDMapping(
    bankId: BankId,
    customerNumber: String
  ) =
  {
  
    val mappedCustomerIDMapping = MappedCustomerIDMapping.find(
      By(MappedCustomerIDMapping.mBankId, bankId.value),
      By(MappedCustomerIDMapping.mCustomerNumber, customerNumber)
    )
  
    mappedCustomerIDMapping match
    {
      case Full(vImpl) =>
      {
        logger.debug(s"getOrCreateCustomerId --> the mappedCustomerIDMapping has been existing in server !")
        mappedCustomerIDMapping
      }
      case Empty =>
      {
        val mappedCustomerIDMapping: MappedCustomerIDMapping =
          MappedCustomerIDMapping
            .create
            .mBankId(bankId.value)
            .mCustomerNumber(customerNumber)
            .saveMe
        logger.debug(s"getOrCreateCustomerId--> create mappedCustomerIDMapping : $mappedCustomerIDMapping")
        Full(mappedCustomerIDMapping)
      }
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }
  
  
  override def getCustomerIDMapping(customerId: CustomerId) = {
    MappedCustomerIDMapping.find(
      By(MappedCustomerIDMapping.mCustomerId, customerId.value),
    )
  }
}


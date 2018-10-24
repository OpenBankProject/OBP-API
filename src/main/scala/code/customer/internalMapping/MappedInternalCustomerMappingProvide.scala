package code.customer.internalMapping

import code.model.BankId
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Empty, Full}
import net.liftweb.mapper.By


object MappedCustomerIDMappingProvider extends CustomerIDMappingProvider with MdcLoggable
{
  
  override def getOrCreateCustomerId(
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
    }
  }
}


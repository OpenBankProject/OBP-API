package code.customer.internalMapping

import code.model.{BankId, CustomerId}

/**
  * This trait is used for storing the mapped between obp customer_id and bank real customer number.
  * eg: Once we create the customer over CBS, we need also create a CustomerId in api side.
  *     For security reason, we can only use the customerId (UUID) in the apis.  
  *     Because these id’s might be cached on the internet.
  */
trait CustomerIDMapping {
  def bankId : BankId 
  def customerId : CustomerId // The UUID for the customer,  To be used in URLs
  def customerNumber : String // The Customer number i.e. the bank identifier for the customer.
}
package code.customer.internalMapping

import com.openbankproject.commons.model.{BankId, CustomerId}

/**
 * This trait is used for storing the mapped between obp customer_id and bank real customer reference.
 * customerPlainTextReference is just a plain text from bank. Bank need prepare it and make it unique for each Customer.
 *
 * eg: Once we create the customer over CBS, we need also create a CustomerId in api side.
 *     For security reason, we can only use the customerId (UUID) in the apis.  
 *     Because these id’s might be cached on the internet.
 */
trait CustomerIdMapping {
  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  def bankId : BankId
  @deprecated("We used customerPlainTextReference instead","23-08-2019")
  def customerNumber : String // The Customer number i.e. the bank identifier for the customer.
  /**
   * This is the obp customer UUID. 
   * @return
   */
  def customerId : CustomerId

  /**
   * This is the bank customer plain text string, need to be unique for each customer. ( Bank need to take care of it)
   * @return  It can be concatenated of real bank customer data: eg: customerPlainTextReference =  customerNumber + customerCode + customerType
   */
  def customerPlainTextReference : String
}
package code.model.dataAccess.internalMapping

import com.openbankproject.commons.model.AccountId

/**
 * This trait is used for storing the mapped between obp account_id and bank real account reference.
 * AccountPlainTextReference is just a plain text from bank. Bank need to prepare it and make it unique for each Account.
 *
 * eg: Once we create the account over CBS, we need also create a AccountId in api side.
 *     For security reason, we can only use the accountId (UUID) in the apis.  
 *     Because these id’s might be cached on the internet.
 */
trait AccountIdMappingT {
  /**
   * This is the obp Account UUID. 
   * @return
   */
  def accountId : AccountId

  /**
   * This is the bank account plain text string, need to be unique for each account. ( Bank need to take care of it)
   * @return  It can be concatenated of real bank account data: 
   *          eg: accountPlainTextReference =  accountNumber + accountCode + accountType
   */
  def accountPlainTextReference : String
}
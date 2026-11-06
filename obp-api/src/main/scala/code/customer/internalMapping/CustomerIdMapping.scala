/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
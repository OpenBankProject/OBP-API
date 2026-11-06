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

package code.customeraddress

import code.api.util.APIUtil
import com.openbankproject.commons.model.CustomerAddress
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object CustomerAddressX extends SimpleInjector {

  val address = new Inject(() => buildOne) {}

  def buildOne: CustomerAddressProvider = MappedCustomerAddressProvider
  
}

trait CustomerAddressProvider {
  def getAddress(customerId: String): Future[Box[List[CustomerAddress]]]
  def createAddress(customerId: String,
                    line1: String,
                    line2: String,
                    line3: String,
                    city: String,
                    county: String,
                    state: String,
                    postcode: String,
                    countryCode: String,
                    tags: String,
                    status: String
                ): Future[Box[CustomerAddress]]
  def updateAddress(customerAddressId: String,
                    line1: String,
                    line2: String,
                    line3: String,
                    city: String,
                    county: String,
                    state: String,
                    postcode: String,
                    countryCode: String,
                    tags: String,
                    status: String
                   ): Future[Box[CustomerAddress]]
  def deleteAddress(customerAddressId: String): Future[Box[Boolean]]
}
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
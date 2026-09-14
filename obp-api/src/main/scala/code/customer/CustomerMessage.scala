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

package code.customer

import java.util.Date
import com.openbankproject.commons.model.{BankId, Customer, CustomerMessage, User}
import net.liftweb.util.SimpleInjector


object CustomerMessages extends SimpleInjector {

  val customerMessageProvider = new Inject(() => buildOne) {}

  def buildOne: CustomerMessageProvider = MappedCustomerMessageProvider

}

trait CustomerMessageProvider {

  //TODO: pagination? is this sorted by date?
  def getMessages(user : User, bankId : BankId) : List[CustomerMessage]

  def addMessage(user : User, bankId : BankId, message : String, fromDepartment : String, fromPerson : String) : CustomerMessage
  
  def createCustomerMessage(customer : Customer, bankId : BankId, transport : String, message : String, fromDepartment : String, fromPerson : String) : CustomerMessage
  
  def getCustomerMessages(customer : Customer, bankId : BankId) : List[CustomerMessage]

}



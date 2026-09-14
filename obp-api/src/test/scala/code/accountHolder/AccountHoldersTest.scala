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

package code.accountHolder

import code.accountholders.AccountHolders
import code.model._
import code.setup.{DefaultUsers, ServerSetup}
import code.views.system.ViewDefinition
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId}

class AccountHoldersTest extends ServerSetup with DefaultUsers{
  
  override def beforeAll() = {
    super.beforeAll()
    AccountHolders.accountHolders.vend.bulkDeleteAllAccountHolders()
    ViewDefinition.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    AccountHolders.accountHolders.vend.bulkDeleteAllAccountHolders()
  }
  
  val bankIdAccountId = BankIdAccountId(BankId("1"),AccountId("2"))
  
  feature("test some important methods in MappedViews ") {
    
    scenario("test - getOrCreateAccountView") {
      
      Given("3 users and 1 bankAccount, and call the method")
      var mapperAccountHolder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(resourceUser1, bankIdAccountId)
      var mapperAccountHolder2 = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(resourceUser2, bankIdAccountId)
      var mapperAccountHolder3 = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(resourceUser3, bankIdAccountId)
      
      Then("Check the result.")
      var accountholders = AccountHolders.accountHolders.vend.getAccountHolders(bankIdAccountId.bankId, bankIdAccountId.accountId)
  
      val numberOfAccountholders = accountholders.toList.length
      numberOfAccountholders should equal(3)
      
      Then("We call this method again")
      AccountHolders.accountHolders.vend.getOrCreateAccountHolder(resourceUser1, bankIdAccountId)
      
      Then("Check the result, the number should be the same as before ")
      accountholders = AccountHolders.accountHolders.vend.getAccountHolders(bankIdAccountId.bankId, bankIdAccountId.accountId)
      numberOfAccountholders  should equal(3)
      
    }
  
  }
  
  
}

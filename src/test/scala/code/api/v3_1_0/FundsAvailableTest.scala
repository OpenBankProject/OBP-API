/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

  */
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCheckFundsAvailable, canCheckFundsAvailable}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}

class FundsAvailableTest extends V310ServerSetup {

  feature("Check available funds v3.1.0 - Unauthorized access")
  {
    scenario("We will check available without user credentials") {
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.bank_id / view / "funds-available").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
  }

  feature("Check available funds v3.1.0 - Authorized access")
  {
    scenario("We will check available funds without a proper Role " + canCheckFundsAvailable) {
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("We make a request v3.1.0 without a Role " + canCheckFundsAvailable)
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.bank_id / view / "funds-available").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCheckFundsAvailable)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanCheckFundsAvailable)
    }
  }

}

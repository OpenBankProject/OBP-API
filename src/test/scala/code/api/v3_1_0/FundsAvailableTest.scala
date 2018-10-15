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
  */
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, ApiVersion}
import code.api.util.ApiRole.{CanCheckFundsAvailable, canCheckFundsAvailable}
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class FundsAvailableTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.checkFundsAvailable))

  feature("Check available funds v3.1.0 - Unauthorized access")
  {
    scenario("We will check available without user credentials", ApiEndpoint, VersionOfApi) {
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
    scenario("We will check available funds without a proper Role " + canCheckFundsAvailable, ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("We make a request v3.1.0 without a Role " + canCheckFundsAvailable)
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "funds-available").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCheckFundsAvailable)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanCheckFundsAvailable)
    }

    scenario("We will check available funds with a proper Role " + canCheckFundsAvailable + " but without params", ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCheckFundsAvailable.toString)

      When("We make a request v3.1.0 without a Role " + canCheckFundsAvailable + " but without all params")
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "funds-available").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + MissingQueryParams)
      response310.body.extract[ErrorMessage].error should startWith (MissingQueryParams)

      val response310_amount = makeGetRequest(request310 <<? Map("amount" -> "1"))
      Then("We should get a 400")
      response310_amount.code should equal(400)
      And("error should be " + MissingQueryParams)
      response310_amount.body.extract[ErrorMessage].error should startWith (MissingQueryParams)

      val response310_ccy = makeGetRequest(request310 <<? Map("currency" -> "EUR"))
      Then("We should get a 400")
      response310_ccy.code should equal(400)
      And("error should be " + MissingQueryParams)
      response310_ccy.body.extract[ErrorMessage].error should startWith (MissingQueryParams)
    }

    scenario("We will check available funds with a proper Role " + canCheckFundsAvailable + " and params", ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCheckFundsAvailable.toString)

      When("We make a request v3.1.0 with a Role " + canCheckFundsAvailable + " and all params")
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "funds-available").GET <@(user1)
      val response310 = makeGetRequest(request310 <<? Map("currency" -> "EUR", "amount" -> "1"))
      Then("We should get a 200")
      response310.code should equal(200)

      When("We make a request v3.1.0 with a Role " + canCheckFundsAvailable + " and all params but currency is invalid")
      val response310_invalic_ccy = makeGetRequest(request310 <<? Map("currency" -> "eur", "amount" -> "1"))
      Then("We should get a 400")
      response310_invalic_ccy.code should equal(400)
      And("error should be " + InvalidISOCurrencyCode)
      response310_invalic_ccy.body.extract[ErrorMessage].error should equal(InvalidISOCurrencyCode)

      When("We make a request v3.1.0 with a Role " + canCheckFundsAvailable + " and all params but amount is invalid")
      val response310_amount_ccy = makeGetRequest(request310 <<? Map("currency" -> "EUR", "amount" -> "bb"))
      Then("We should get a 400")
      response310_amount_ccy.code should equal(400)
      And("error should be " + InvalidAmount)
      response310_amount_ccy.body.extract[ErrorMessage].error should startWith (InvalidAmount)
    }
  }

}

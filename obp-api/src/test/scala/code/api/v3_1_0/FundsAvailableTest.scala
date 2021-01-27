/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.canCheckFundsAvailable
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v1_2_1.{CreateViewJsonV121, ViewJSONV121}
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.setup.APIResponse
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

class FundsAvailableTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "checkFundsAvailable":
    * 	./mvn.sh test -DtagsToInclude=checkFundsAvailable
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.checkFundsAvailable))

  def getThirdPartyAppView(bankId: String, accountId: String) = postView(bankId, accountId, randomView(true, "")).body.extract[ViewJSONV121]
  def randomView(isPublic: Boolean, alias: String) : CreateViewJsonV121 = {
    val viewFields = List("can_query_available_funds", "can_see_transaction_this_bank_account")
    CreateViewJsonV121(
      name = "_"+randomString(3),//Now, all created views should start with `_`.
      description = randomString(3),
      is_public = isPublic,
      which_alias_to_use=alias,
      hide_metadata_if_alias_used = false,
      allowed_actions = viewFields
    )
  }
  def postView(bankId: String, accountId: String, view: CreateViewJsonV121): APIResponse = {
    val request = (baseRequest / "obp" / "v1.2.1" / "banks" / bankId / "accounts" / accountId / "views").POST <@(user1)
    makePostRequest(request, write(view))
  }

  def grantUserAccessToView(bankId : String, accountId : String, userId : String, viewId : String) : APIResponse= {
    val request = (baseRequest / "obp" / "v1.2.1" / "banks" / bankId / "accounts" / accountId / "permissions"/ defaultProvider / userId / "views" / viewId).POST <@(user1)
    makePostRequest(request, "")
  }

  feature("Check available funds v3.1.0 - Unauthorized access")
  {
    scenario("We will check available without user credentials", ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.bank_id / view / "funds-available").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature("Check available funds v3.1.0 - Authorized access")
  {
    scenario("We will check available funds without params", ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)

      When("We make a request v3.1.0 without all params")
      val viewId = getThirdPartyAppView(bankId, bankAccount.id).id
      grantUserAccessToView(bankId, bankAccount.id, resourceUser1.idGivenByProvider, viewId)
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / viewId / "funds-available").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + MissingQueryParams)
      response310.body.extract[ErrorMessage].message should startWith (MissingQueryParams)

      val response310_amount = makeGetRequest(request310 <<? Map("amount" -> "1"))
      Then("We should get a 400")
      response310_amount.code should equal(400)
      And("error should be " + MissingQueryParams)
      response310_amount.body.extract[ErrorMessage].message should startWith (MissingQueryParams)

      val response310_ccy = makeGetRequest(request310 <<? Map("currency" -> "EUR"))
      Then("We should get a 400")
      response310_ccy.code should equal(400)
      And("error should be " + MissingQueryParams)
      response310_ccy.body.extract[ErrorMessage].message should startWith (MissingQueryParams)
    }

    scenario("We will check available funds and params", ApiEndpoint, VersionOfApi) {
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)

      When("We make a request v3.1.0 with a Role " + canCheckFundsAvailable + " and all params")
      val viewId = getThirdPartyAppView(bankId, bankAccount.id).id
      grantUserAccessToView(bankId, bankAccount.id, resourceUser1.idGivenByProvider, viewId)
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / viewId / "funds-available").GET <@(user1)
      val response310 = makeGetRequest(request310 <<? Map("currency" -> "EUR", "amount" -> "1"))
      Then("We should get a 200")
      response310.code should equal(200)

      When("We make a request v3.1.0 with all params but currency is invalid")
      val response310_invalic_ccy = makeGetRequest(request310 <<? Map("currency" -> "eur", "amount" -> "1"))
      Then("We should get a 400")
      response310_invalic_ccy.code should equal(400)
      And("error should be " + InvalidISOCurrencyCode)
      response310_invalic_ccy.body.extract[ErrorMessage].message startsWith(InvalidISOCurrencyCode)

      When("We make a request v3.1.0 with all params but amount is invalid")
      val response310_amount_ccy = makeGetRequest(request310 <<? Map("currency" -> "EUR", "amount" -> "bb"))
      Then("We should get a 400")
      response310_amount_ccy.code should equal(400)
      And("error should be " + InvalidAmount)
      response310_amount_ccy.body.extract[ErrorMessage].message should startWith (InvalidAmount)
    }
  }

}

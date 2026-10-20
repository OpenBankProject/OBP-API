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

package code.api.v2_2_0

import code.accountholders.AccountHolders
import com.openbankproject.commons.model.AccountId
import org.json4s._
import code.api.Constant
import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.extractErrorMessageCode
import code.api.util.{ApiRole, ErrorMessages}
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121}
import org.json4s.native.Serialization.write

import java.util.concurrent.TimeUnit
import scala.util.Random

class AccountTest extends V220ServerSetup with DefaultUsers {

  val mockAccountId1 = "NEW_MOCKED_ACCOUNT_ID_01"
  val mockAccountId2 = "NEW_MOCKED_ACCOUNT_ID_02"
  
  
  feature("Assuring that Get all accounts at all banks works as expected - v2.2.0") {

    scenario("We create an account and get accounts as anonymous and then as authenticated user - allAccountsAllBanks") {
      val createAccountJSONV220 = CreateAccountJSONV220(
        user_id = resourceUser1.userId,
        label = "Label",
        `type` = "CURRENT",
        balance = AmountOfMoneyJsonV121(
          "EUR",
          "0"
        ),
        branch_id = "1234",
        account_routing = AccountRoutingJsonV121(
          scheme = "OBP",
          address = "UK123456"
        )
      )
      Given("The bank")
      val testBank = testBankId1
      val accountPutJSON = createAccountJSONV220
      val requestPut = (v2_2Request / "banks" / testBank.value / "accounts" / mockAccountId1).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(accountPutJSON))
      And("We should get a 200")
      responsePut.code should equal(200)

      When("We make the authenticated access request")
      val requestGetAll = (v2_2Request / "accounts").GET <@ (user1)
      val responseGetAll = makeGetRequest(requestGetAll)

      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val isPublicAll =
        for {
          obj@JObject(o) <- responseGetAll.body
          if (o contains JField("id", JString(mockAccountId1)))
          JBool(isPublic) <- obj \\ "is_public"
        } yield {
          isPublic
        }
      And("The new created account has to be private")
      isPublicAll.forall(_ == false) should equal(true)
    }

    scenario("We create an account and get accounts as anonymous and then as authenticated user - allAccountsAtOneBank") {
      val createAccountJSONV220 = CreateAccountJSONV220(
        user_id = resourceUser1.userId,
        label = "Label",
        `type` = "CURRENT",
        balance = AmountOfMoneyJsonV121(
          "EUR",
          "0"
        ),
        branch_id = "1234",
        account_routing = AccountRoutingJsonV121(
          scheme = "OBP",
          address = "UK123456"
        )
      )
      
      Given("The bank")
      val testBank = testBankId1

      Then("We create an private account at the bank")
      val accountPutJSON = createAccountJSONV220
      val requestPut = (v2_2Request / "banks" / testBank.value / "accounts" / mockAccountId1).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(accountPutJSON))

      And("We should get a 200")
      responsePut.code should equal(200)

      When("We make the authenticated access request")
      val requestGetAll = (v2_2Request / "banks" / testBank.value / "accounts").GET <@ (user1)
      val responseGetAll = makeGetRequest(requestGetAll)

      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val isPublicAll =
        for {
          obj@JObject(o) <- responseGetAll.body
          if (o contains JField("id", JString(testAccountId1.value)))
          JBool(isPublic) <- obj \\ "is_public"
        } yield {
          isPublic
        }
      And("The new created account has to be private")
      isPublicAll.forall(_ == false) should equal(true)

      Then("We test login user create account for other users with no role first")
      val requestPutNewAccountId = (v2_2Request / "banks" / testBank.value / "accounts" / mockAccountId2).PUT <@ (user1)
      val accountPutJSON2 = accountPutJSON.copy(user_id = resourceUser2.userId,
        account_routing = AccountRoutingJsonV121(Random.nextString(10), Random.nextString(10)))
      val responseWithNoRole = makePutRequest(requestPutNewAccountId, write(accountPutJSON2))

      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(extractErrorMessageCode(UserHasMissingRoles)) should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val responseWithOtherUesrV310 = makePutRequest(requestPutNewAccountId, write(accountPutJSON2))
      
      Then("We should get a 200")
      responseWithOtherUesrV310.code should equal(200)
    }

    scenario("We create an account and check the accountViews") {
      val createAccountJSONV220 = CreateAccountJSONV220(
        user_id = resourceUser1.userId,
        label = "Label",
        `type` = "CURRENT",
        balance = AmountOfMoneyJsonV121(
          "EUR",
          "0"
        ),
        branch_id = "1234",
        account_routing = AccountRoutingJsonV121(
          scheme = "OBP",
          address = "UK123456"
        )
      )

      Given("The bank")
      val testBank = testBankId1

      Then("We create an private account at the bank")
      val accountPutJSON = createAccountJSONV220
      val requestPut = (v2_2Request / "banks" / testBank.value / "accounts" / mockAccountId1).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(accountPutJSON))

      Then("we get the account access for this account")
      val accountViewsRequest = (v2_2Request / "banks" / testBank.value / "accounts" / mockAccountId1 / "views").GET <@(user1)
      val accountViewsResponse = makeGetRequest(accountViewsRequest)
      val accountViews = accountViewsResponse.body.extract[ViewsJSONV220]
      //Note: now when we create new account, will have the systemOwnerView access to this view.
      accountViews.views.length > 0 should be (true)
      accountViews.views.map(_.id).toString() contains(Constant.SYSTEM_OWNER_VIEW_ID) should be (true)
    }

    scenario("We create an account, but with wrong format of account_id ") {
      val createAccountJSONV220 = CreateAccountJSONV220(
        user_id = resourceUser1.userId,
        label = "Label",
        `type` = "CURRENT",
        balance = AmountOfMoneyJsonV121(
          "EUR",
          "0"
        ),
        branch_id = "1234",
        account_routing = AccountRoutingJsonV121(
          scheme = "OBP",
          address = "UK123456"
        )
      )
      
      Given("The bank")
      val testBank = testBankId1
      val newAccountIdWithSpaces = "account%20with%20spaces"

      Then("We create an private account at the bank")
      val accountPutJSON = createAccountJSONV220
      val requestPut = (v2_2Request / "banks" / testBank.value / "accounts" / newAccountIdWithSpaces).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(accountPutJSON))

      And("We should get a 400")
      responsePut.code should equal(400)
      And("We should have the error massage")
      val error: String = (responsePut.body \ "message") match {
        case JString(i) => i
        case _ => ""
      }
      Then("We should have the error: " + ErrorMessages.InvalidAccountIdFormat)
      error.toString contains (ErrorMessages.InvalidAccountIdFormat) should be (true)
    }
  }

  private def createAccountBodyFor(userId: String) =
    CreateAccountJSONV220(
      user_id = userId,
      label = "Label",
      `type` = "CURRENT",
      balance = AmountOfMoneyJsonV121("EUR", "0"),
      branch_id = "1234",
      account_routing = AccountRoutingJsonV121(scheme = "OBP", address = "UK123456"))

  feature("Create Account refuses an ACCOUNT_ID that already exists - v2.2.0") {

    scenario("a second User PUTting an existing ACCOUNT_ID gets 409 AccountIdAlreadyExists and does not become a holder") {
      val existingAccountId = "EXISTING_ACCOUNT_ID_V220"
      val createdByUser1 = makePutRequest(
        (v2_2Request / "banks" / testBankId1.value / "accounts" / existingAccountId).PUT <@ (user1),
        write(createAccountBodyFor(resourceUser1.userId)))
      createdByUser1.code should equal(200)

      val attemptByUser2 = makePutRequest(
        (v2_2Request / "banks" / testBankId1.value / "accounts" / existingAccountId).PUT <@ (user2),
        write(createAccountBodyFor(resourceUser2.userId)))
      attemptByUser2.code should equal(409)
      (attemptByUser2.body \ "message").extract[String] should include(ErrorMessages.AccountIdAlreadyExists)

      val holderUserIds = AccountHolders.accountHolders.vend
        .getAccountHolders(testBankId1, AccountId(existingAccountId)).map(_.userId)
      holderUserIds should contain(resourceUser1.userId)
      holderUserIds should not contain(resourceUser2.userId)
    }
  }

  feature("Create Account refuses a consent user as the account holder - v2.2.0") {

    scenario("a User with CanCreateAccount naming a consent user's USER_ID gets 400 InvalidUserId and no account is created") {
      // A consent user: an agent identity minted by a Consent, which disappears with the Consent.
      val consentUser = code.users.Users.users.vend.createResourceUser(
        provider = "create-account-consent-user-test",
        providerId = Some(code.api.util.APIUtil.generateUUID()),
        createdByConsentId = Some(code.api.util.APIUtil.generateUUID()),
        name = Some("consent-user"),
        email = None,
        userId = None,
        createdByUserInvitationId = None,
        company = None,
        lastMarketingAgreementSignedDate = None
      ).openOrThrowException("Expected resource user to be created")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val accountIdForConsentUser = "CONSENT_USER_ACCOUNT_ID_V220"
      val response = makePutRequest(
        (v2_2Request / "banks" / testBankId1.value / "accounts" / accountIdForConsentUser).PUT <@ (user1),
        write(createAccountBodyFor(consentUser.userId)))
      response.code should equal(400)
      (response.body \ "message").extract[String] should include(ErrorMessages.InvalidUserId)
      (response.body \ "message").extract[String] should include("consent user")

      AccountHolders.accountHolders.vend
        .getAccountHolders(testBankId1, AccountId(accountIdForConsentUser)) should be (empty)
    }
  }
}

package code.api.v2_2_0

import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121}
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization.write

import scala.util.Random

class AccountTest extends V220ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
  
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
      responseWithNoRole.body.toString contains(s"$UserHasMissingRoles") should be (true)


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
      val accountViewsRequest = v2_2Request / "banks" / testBank.value / "accounts" / mockAccountId1 / "views" <@(user1)
      val accountViewsResponse = makeGetRequest(accountViewsRequest)
      val accountViews = accountViewsResponse.body.extract[ViewsJSONV220]
      //Note: now when we create new account, will have the systemOwnerView access to this view.
      accountViews.views.length > 0 should be (true)
      accountViews.views.map(_.id).toString() contains("owner") should be (true)
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

}

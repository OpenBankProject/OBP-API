package code.api.v2_2_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.api.v1_2_1.{AccountRoutingJsonV121, AmountOfMoneyJsonV121}
import code.setup.DefaultUsers
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization.write

class AccountTest extends V220ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
  
  val mockAccountId1 = "NEW_MOCKED_ACCOUNT_ID_01"
  
  
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
org.scalameta.logger.elem(responsePut)
      And("We should get a 200")
      org.scalameta.logger.elem(responsePut)
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

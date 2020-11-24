package code.api.v3_1_0

import code.api.Constant
import com.openbankproject.commons.model.{AccountRouting, AccountRoutingJsonV121, AmountOfMoneyJsonV121, ErrorMessage, enums}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.updateAccountRequestJsonV310
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.ApiRole
import code.api.v2_0_0.BasicAccountJSON
import code.api.v2_2_0.CreateAccountJSONV220
import code.api.v3_0_0.{CoreAccountsJsonV300, ModeratedCoreAccountJsonV300}
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.entitlement.Entitlement
import code.model.dataAccess.BankAccountRouting
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.util.Random

class AccountTest extends V310ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.updateAccount))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.createAccount))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.getBankAccountsBalances))
  //We need this endpoint to test the result 
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_0_0.corePrivateAccountsAllBanks))
  object ApiEndpoint5 extends Tag(nameOf(Implementations2_0_0.getPrivateAccountsAtOneBank))
  object ApiEndpoint6 extends Tag(nameOf(Implementations3_0_0.getPrivateAccountById))

  lazy val testBankId = testBankId1
  lazy val putCreateAccountJSONV310 = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
  lazy val putCreateAccountOtherUserJsonV310 = SwaggerDefinitionsJSON.createAccountRequestJsonV310
    .copy(user_id = resourceUser2.userId, balance = AmountOfMoneyJsonV121("EUR","0"),
    account_routings = List(AccountRoutingJsonV121(Random.nextString(10), Random.nextString(10))))
  
  
  feature("test Update Account") {
    scenario("We will test Update Account Api", ApiEndpoint1, VersionOfApi) {
      Given("The test bank and test account")
      val testAccount = testAccountId1
      val testPutJson = updateAccountRequestJsonV310
      
      Then(s"We call the update api without proper role:  ${ApiRole.canUpdateAccount}")
      val requestPut = (v3_1_0_Request / "management" / "banks" / testBankId.value / "accounts" / testAccount.value).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(testPutJson))
      And("We should get  403 and the error message missing can CanUpdateAccount role")
      responsePut.code should equal(403)
      responsePut.body.toString contains("OBP-20006: User is missing one or more roles: CanUpdateAccount")


      Then(s"We grant the user ${ApiRole.canUpdateAccount} role")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canUpdateAccount.toString)
      val responsePut2 = makePutRequest(requestPut, write(testPutJson))
      And("We should get 200 and updated account data")
      responsePut2.code should equal(200)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].`type` should be (testPutJson.`type`)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].branch_id should be (testPutJson.branch_id)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].label should be (testPutJson.label)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].account_routings.head.scheme should be (testPutJson.account_routings.head.scheme)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].account_routings.head.address should be (testPutJson.account_routings.head.address)


      val requestGet = (v3_1_0_Request /"my"/ "banks" / testBankId.value / "accounts" / testAccount.value / "account").PUT <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get 200 and updated account data")
      responseGet.code should equal(200)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].`type` should be (testPutJson.`type`)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].label should be (testPutJson.label)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].account_routings.toString() contains (testPutJson.account_routings.head.scheme) should be (true)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].account_routings.toString() contains (testPutJson.account_routings.head.address) should be (true)
      
    }

    scenario("We will test update on account routings", ApiEndpoint1, VersionOfApi) {
      Given("The test bank and test account with a canUpdateAccount entitlement")
      val testAccount0 = testAccountId0
      val testPutJson = updateAccountRequestJsonV310
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canUpdateAccount.toString())

      val requestPut = (v3_1_0_Request / "management" / "banks" / testBankId.value / "accounts" / testAccount0.value).PUT <@ (user1)
      val requestGet = (v3_1_0_Request /"my"/ "banks" / testBankId.value / "accounts" / testAccount0.value / "account").PUT <@ (user1)


      When("We want to add an account routing scheme (IBAN)")
      val newRoutingSchemeIban = List(AccountRoutingJsonV121(enums.AccountRoutingScheme.IBAN.toString, Random.nextString(10)))
      val testPutJsonWithIban = testPutJson.copy(account_routings = testPutJson.account_routings ++ newRoutingSchemeIban)

      val responsePut1 = makePutRequest(requestPut, write(testPutJsonWithIban))
      Then("We should get 200 and updated account routings in the updateAccount response")
      responsePut1.code should equal(200)
      responsePut1.body.extract[UpdateAccountResponseJsonV310].account_routings should be (testPutJsonWithIban.account_routings)

      val responseGet1 = makeGetRequest(requestGet)
      And("We should get 200 and updated account routings in the getAccount response")
      responseGet1.code should equal(200)
      responseGet1.body.extract[ModeratedCoreAccountJsonV300].account_routings should be (testPutJsonWithIban.account_routings)


      When("We want to remove an account routing scheme (AccountNumber)")
      val testPutJsonWithoutAccountNumber = testPutJsonWithIban.copy(account_routings = newRoutingSchemeIban)

      val responsePut2 = makePutRequest(requestPut, write(testPutJsonWithoutAccountNumber))
      Then("We should get 200 and updated account routings in the updateAccount response")
      responsePut2.code should equal(200)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].account_routings should be (testPutJsonWithoutAccountNumber.account_routings)

      val responseGet2 = makeGetRequest(requestGet)
      And("We should get 200 and updated account routings in the getAccount response")
      responseGet2.code should equal(200)
      responseGet2.body.extract[ModeratedCoreAccountJsonV300].account_routings should be (testPutJsonWithoutAccountNumber.account_routings)


      When("We want to update an account routing scheme (IBAN)")
      val updatedRoutingSchemeIban = List(AccountRoutingJsonV121(enums.AccountRoutingScheme.IBAN.toString, Random.nextString(10)))
      val testPutJsonWithUpdatedIban = testPutJsonWithIban.copy(account_routings = updatedRoutingSchemeIban)

      val responsePut3 = makePutRequest(requestPut, write(testPutJsonWithUpdatedIban))
      Then("We should get 200 and updated account routings in the updateAccount response")
      responsePut3.code should equal(200)
      responsePut3.body.extract[UpdateAccountResponseJsonV310].account_routings should be (testPutJsonWithUpdatedIban.account_routings)

      val responseGet3 = makeGetRequest(requestGet)
      And("We should get 200 and updated account routings in the getAccount response")
      responseGet3.code should equal(200)
      responseGet3.body.extract[ModeratedCoreAccountJsonV300].account_routings should be (testPutJsonWithUpdatedIban.account_routings)


      When("We want to update an account with a routing scheme duplication")
      val anOtherRoutingSchemeIban = List(AccountRoutingJsonV121(enums.AccountRoutingScheme.IBAN.toString, Random.nextString(10)))
      val testPutJsonWithRoutingSchemeDuplication =
        testPutJsonWithUpdatedIban.copy(account_routings = testPutJsonWithUpdatedIban.account_routings ++ anOtherRoutingSchemeIban)

      val responsePut4 = makePutRequest(requestPut, write(testPutJsonWithRoutingSchemeDuplication))
      Then("We should get 400 in the updateAccount response")
      responsePut4.code should equal(400)
      responsePut4.body.toString should include ("Duplication detected in account routings, please specify only one value per routing scheme")

      val responseGet4 = makeGetRequest(requestGet)
      And("We should get 200 and non-updated account routings on the getAccount response")
      responseGet4.code should equal(200)
      responseGet4.body.extract[ModeratedCoreAccountJsonV300].account_routings should be (testPutJsonWithUpdatedIban.account_routings)


      When("We want to add an account routing scheme (IBAN) with an already existing routing scheme (IBAN)")
      val testAccount1 = testAccountId1
      val testPutJsonWithSameIban = testPutJsonWithUpdatedIban

      val requestPutOtherAccount = (v3_1_0_Request / "management" / "banks" / testBankId.value / "accounts" / testAccount1.value).PUT <@ (user1)
      val requestGetOtherAccount = (v3_1_0_Request / "my" / "banks" / testBankId.value / "accounts" / testAccount1.value / "account").PUT <@ (user1)

      val responseGetOtherAccount_1 = makeGetRequest(requestGetOtherAccount)
      Then("We should get 200 on the first getAccount response to get original account routings")
      responseGetOtherAccount_1.code should equal(200)
      val originalAccountRoutings = responseGetOtherAccount_1.body.extract[ModeratedCoreAccountJsonV300].account_routings

      val responsePutOtherAccount = makePutRequest(requestPutOtherAccount, write(testPutJsonWithSameIban))
      Then("We should get 400 in the updateAccount response")
      responsePutOtherAccount.code should equal(400)
      responsePutOtherAccount.body.toString should include ("OBP-30115: Account Routing already exist.")

      val responseGetOtherAccount_2 = makeGetRequest(requestGetOtherAccount)
      And("We should get 200 and non-updated account routings on the second getAccount response")
      responseGetOtherAccount_2.code should equal(200)
      responseGetOtherAccount_2.body.extract[ModeratedCoreAccountJsonV300].account_routings should be (originalAccountRoutings)
    }
  }


  feature("Create Account v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "ACCOUNT_ID" ).PUT
      val response310 = makePutRequest(request310, write(putCreateAccountJSONV310))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Create Account v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCreateAccountJSONV310))
      Then("We should get a 201")
      response310.code should equal(201)
      val account = response310.body.extract[CreateAccountResponseJsonV310]
      account.product_code should be (putCreateAccountJSONV310.product_code)
      account.`label` should be (putCreateAccountJSONV310.`label`)
      account.balance.amount.toDouble should be (putCreateAccountJSONV310.balance.amount.toDouble)
      account.balance.currency should be (putCreateAccountJSONV310.balance.currency)
      account.branch_id should be (putCreateAccountJSONV310.branch_id)
      account.user_id should be (putCreateAccountJSONV310.user_id)
      account.label should be (putCreateAccountJSONV310.label)
      account.account_routings should be (putCreateAccountJSONV310.account_routings)

      
      Then(s"we call $ApiEndpoint4 to get the account back")
      val requestApiEndpoint4 = (v3_1_0_Request / "my" / "accounts" ).PUT <@(user1)
      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)

      responseApiEndpoint4.code should equal(200)
      val accounts = responseApiEndpoint4.body.extract[CoreAccountsJsonV300].accounts
      accounts.map(_.id).toList.toString() contains(account.account_id) should be (true)


      Then(s"we call $ApiEndpoint5 to get the account back")
      val requestApiEndpoint5 = (v3_1_0_Request /"banks" / testBankId.value / "accounts").GET <@ (user1)
      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)

      Then("We should get a 200")
      responseApiEndpoint5.code should equal(200)
      responseApiEndpoint5.body.extract[List[BasicAccountJSON]].toList.toString() contains(account.account_id) should be (true)


      val requestGetApiEndpoint3 = (v3_1_0_Request / "banks" / testBankId.value / "balances").GET <@ (user1)
      val responseGetApiEndpoint3 = makeGetRequest(requestGetApiEndpoint3)
      responseGetApiEndpoint3.code should equal(200)
      responseGetApiEndpoint3.body.extract[AccountsBalancesV310Json].accounts.toList.toString() contains(account.account_id) should be (true)
      
      
      Then("We make a request v3.1.0 but with other user")
      val request310WithNewAccountId = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID2" ).PUT <@(user1)
      val responseWithNoRole = makePutRequest(request310WithNewAccountId, write(putCreateAccountOtherUserJsonV310))
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(s"$UserHasMissingRoles") should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val responseWithOtherUesrV310 = makePutRequest(request310WithNewAccountId, write(putCreateAccountOtherUserJsonV310))
      
      val account2 = responseWithOtherUesrV310.body.extract[CreateAccountResponseJsonV310]
      account2.product_code should be (putCreateAccountOtherUserJsonV310.product_code)
      account2.`label` should be (putCreateAccountOtherUserJsonV310.`label`)
      account2.balance.amount.toDouble should be (putCreateAccountOtherUserJsonV310.balance.amount.toDouble)
      account2.balance.currency should be (putCreateAccountOtherUserJsonV310.balance.currency)
      account2.branch_id should be (putCreateAccountOtherUserJsonV310.branch_id)
      account2.user_id should be (putCreateAccountOtherUserJsonV310.user_id)
      account2.label should be (putCreateAccountOtherUserJsonV310.label)
      account2.account_routings should be (putCreateAccountOtherUserJsonV310.account_routings)

    }

    scenario("Create new account will have system owner view, and other use also have the system owner view should not get the account back", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID" ).PUT <@(user1)
      val putCreateAccountJson = putCreateAccountJSONV310.copy(account_routings = List(AccountRoutingJsonV121("AccountNumber", "15649885656")))
      val response310 = makePutRequest(request310, write(putCreateAccountJson))
      Then("We should get a 201")
      response310.code should equal(201)
      val account = response310.body.extract[CreateAccountResponseJsonV310]
      account.product_code should be (putCreateAccountJson.product_code)
      account.`label` should be (putCreateAccountJson.`label`)
      account.balance.amount.toDouble should be (putCreateAccountJson.balance.amount.toDouble)
      account.balance.currency should be (putCreateAccountJson.balance.currency)
      account.branch_id should be (putCreateAccountJson.branch_id)
      account.user_id should be (putCreateAccountJson.user_id)
      account.label should be (putCreateAccountJson.label)
      account.account_routings should be (putCreateAccountJson.account_routings)


      Then(s"we call $ApiEndpoint6 to get the account back")
      val requestApiEndpoint6 = (v3_1_0_Request /"banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID" / Constant.SYSTEM_OWNER_VIEW_ID/ "account" ).GET <@(user1)
      val responseApiEndpoint6 = makeGetRequest(requestApiEndpoint6)

      responseApiEndpoint6.code should equal(200)
      val accountEndpoint6 = responseApiEndpoint6.body.extract[ModeratedCoreAccountJsonV300]
      accountEndpoint6.id should be ("TEST_ACCOUNT_ID")
      accountEndpoint6.label should be (account.label)

      Then(s"we prepare the user2 will create a new account ($ApiEndpoint2)and he will have system view, and to call  get account ($ApiEndpoint6) and compare the result.")
      val requestUser2_310 = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID_2" ).PUT <@(user2)
      val responseUser2_310 = makePutRequest(requestUser2_310, write(putCreateAccountJSONV310.copy(user_id = resourceUser2.userId, balance = AmountOfMoneyJsonV121("EUR","0"))))
      Then("We should get a 201")
      responseUser2_310.code should equal(201)


      Then(s"we call $ApiEndpoint6 to get the account back by user2")
      val requestApiUser2Endpoint6 = (v3_1_0_Request /"banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID" / Constant.SYSTEM_OWNER_VIEW_ID/ "account" ).GET <@(user2)
      val responseApiUser2Endpoint6 = makeGetRequest(requestApiUser2Endpoint6)
      //This mean, the user2 can not get access to user1's account!
      responseApiUser2Endpoint6.code should not equal(200)
      
    }

    scenario("Create new account with an already existing routing scheme/address should not create the account", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0 to create the first account")
      val request310_1 = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID_1" ).PUT <@(user1)
      val response310_1 = makePutRequest(request310_1, write(putCreateAccountJSONV310))
      Then("We should get a 201")
      response310_1.code should equal(201)
      val account = response310_1.body.extract[CreateAccountResponseJsonV310]
      account.product_code should be (putCreateAccountJSONV310.product_code)
      account.`label` should be (putCreateAccountJSONV310.`label`)
      account.balance.amount.toDouble should be (putCreateAccountJSONV310.balance.amount.toDouble)
      account.balance.currency should be (putCreateAccountJSONV310.balance.currency)
      account.branch_id should be (putCreateAccountJSONV310.branch_id)
      account.user_id should be (putCreateAccountJSONV310.user_id)
      account.label should be (putCreateAccountJSONV310.label)
      account.account_routings should be (putCreateAccountJSONV310.account_routings)

      When("We make a request v3.1.0 to create the second account with an already existing scheme/address")
      val request310_2 = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID_2" ).PUT <@(user1)
      val response310_2 = makePutRequest(request310_2, write(putCreateAccountJSONV310))
      Then("We should get a 400 in the createAccount response")
      response310_2.code should equal(400)
      response310_2.body.toString should include("OBP-30115: Account Routing already exist.")

      Then(s"The second account should not be created")
      val requestApiGetAccount = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID_2" / Constant.SYSTEM_OWNER_VIEW_ID / "account" ).GET <@(user1)
      val responseApiGetAccount = makeGetRequest(requestApiGetAccount)
      And("We should get a 404 in the getAccount response")
      responseApiGetAccount.code should equal(404)
    }

    scenario("Create new account with a duplication in routing scheme should not create the account", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0 to create the account")
      val request310 = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID" ).PUT <@(user1)
      val putCreateAccountJsonWithRoutingSchemeDuplication = putCreateAccountJSONV310.copy(account_routings =
        List(AccountRoutingJsonV121(AccountRoutingScheme.IBAN.toString, Random.nextString(10)),
          AccountRoutingJsonV121(AccountRoutingScheme.IBAN.toString, Random.nextString(10))))
      val response310 = makePutRequest(request310, write(putCreateAccountJsonWithRoutingSchemeDuplication))
      Then("We should get a 400 in the createAccount response")
      response310.code should equal(400)
      response310.body.toString should include ("Duplication detected in account routings, please specify only one value per routing scheme")

      Then(s"The account should not be created")
      val requestApiGetAccount = (v3_1_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID" / Constant.SYSTEM_OWNER_VIEW_ID / "account" ).GET <@(user1)
      val responseApiGetAccount = makeGetRequest(requestApiGetAccount)
      And("We should get a 404 in the getAccount response")
      responseApiGetAccount.code should equal(404)
    }
  }
  

  feature(s"test ${ApiEndpoint3.name}") {
    scenario("We will test ${ApiEndpoint3.name}", ApiEndpoint3, VersionOfApi) {
      Given("The test bank and test accounts")
      val requestGet = (v3_1_0_Request / "banks" / testBankId.value / "balances").GET <@ (user1)
      
      val responseGet = makeGetRequest(requestGet)
      responseGet.code should equal(200)
      responseGet.body.extract[AccountsBalancesV310Json].accounts.size > 0 should be (true)
      responseGet.body.extract[AccountsBalancesV310Json].overall_balance.currency.nonEmpty should be (true) 
      responseGet.body.extract[AccountsBalancesV310Json].overall_balance.amount.nonEmpty should be (true)
      responseGet.body.extract[AccountsBalancesV310Json].overall_balance_date.getTime >0 should be (true) 

    }
  }

} 

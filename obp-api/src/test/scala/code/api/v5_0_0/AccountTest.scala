package code.api.v5_0_0

import code.api.Constant
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.extractErrorMessageCode
import code.api.util.ApiRole
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v2_0_0.BasicAccountJSON
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v3_0_0.CoreAccountsJsonV300
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.{AccountsBalancesJsonV400, ModeratedCoreAccountJsonV400}
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.util.Random

class AccountTest extends V500ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.createAccount))
  //We need this endpoint to test the result 
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_0_0.corePrivateAccountsAllBanks))
  object ApiEndpoint5 extends Tag(nameOf(Implementations2_0_0.getPrivateAccountsAtOneBank))
  object ApiEndpoint6 extends Tag(nameOf(Implementations3_0_0.getPrivateAccountById))

  lazy val testBankId = testBankId1
  lazy val putCreateAccountJSONV310 = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
  lazy val putCreateAccountOtherUserJsonV310 = SwaggerDefinitionsJSON.createAccountRequestJsonV310
    .copy(user_id = resourceUser2.userId, balance = AmountOfMoneyJsonV121("EUR","0"),
    account_routings = List(AccountRoutingJsonV121(Random.nextString(10), Random.nextString(10))))

  val userAccountId = UUID.randomUUID.toString
  val user2AccountId = UUID.randomUUID.toString


  feature(s"Create Account $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      val request310 = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / "ACCOUNT_ID" ).PUT
      val response310 = makePutRequest(request310, write(putCreateAccountJSONV310))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature(s"Create Account $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString())
      val request = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID" ).PUT <@(user1)
      val response = makePutRequest(request, write(putCreateAccountJSONV310))
      Then("We should get a 201")
      response.code should equal(201)
      //for create account endpoint, we need to wait for `setAccountHolderAndRefreshUserAccountAccess` method, 
      //it is an asynchronous process, need some time to be done.
      TimeUnit.SECONDS.sleep(2)
      
      
      val account = response.body.extract[CreateAccountResponseJsonV310]
      account.product_code should be (putCreateAccountJSONV310.product_code)
      account.`label` should be (putCreateAccountJSONV310.`label`)
      account.balance.amount.toDouble should be (putCreateAccountJSONV310.balance.amount.toDouble)
      account.balance.currency should be (putCreateAccountJSONV310.balance.currency)
      account.branch_id should be (putCreateAccountJSONV310.branch_id)
      account.user_id should be (putCreateAccountJSONV310.user_id)
      account.label should be (putCreateAccountJSONV310.label)
      account.account_routings should be (putCreateAccountJSONV310.account_routings)


      //We need to waite some time for the account creation, because we introduce `AuthUser.refreshUser(user, callContext)`
      //It may not finished when we call the get accounts directly.
      TimeUnit.SECONDS.sleep(2)
      
      Then(s"we call $ApiEndpoint4 to get the account back")
      val requestApiEndpoint4 = (v5_0_0_Request / "my" / "accounts" ).PUT <@(user1)
      val responseApiEndpoint4 = makeGetRequest(requestApiEndpoint4)
      
      

      responseApiEndpoint4.code should equal(200)
      val accounts = responseApiEndpoint4.body.extract[CoreAccountsJsonV300].accounts
      accounts.map(_.id).toList.toString() contains(account.account_id) should be (true)
      
      Then(s"we call $ApiEndpoint5 to get the account back")
      val requestApiEndpoint5 = (v5_0_0_Request /"banks" / testBankId.value / "accounts").GET <@ (user1)
      val responseApiEndpoint5 = makeGetRequest(requestApiEndpoint5)

      Then("We should get a 200")
      responseApiEndpoint5.code should equal(200)
      responseApiEndpoint5.body.extract[List[BasicAccountJSON]].toList.toString() contains(account.account_id) should be (true)


      val requestGetApiEndpoint3 = (v5_0_0_Request / "banks" / testBankId.value / "balances").GET <@ (user1)
      val responseGetApiEndpoint3 = makeGetRequest(requestGetApiEndpoint3)
      responseGetApiEndpoint3.code should equal(200)
      responseGetApiEndpoint3.body.extract[AccountsBalancesJsonV400].accounts.map(_.account_id) contains(account.account_id) should be (true)
      
      
      Then(s"We make a request $VersionOfApi but with other user")
      val request500WithNewAccountId = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID2" ).PUT <@(user2)
      val responseWithNoRole = makePutRequest(request500WithNewAccountId, write(putCreateAccountOtherUserJsonV310))
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(extractErrorMessageCode(UserHasMissingRoles)) should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser2.userId, ApiRole.canCreateAccount.toString)
      val responseWithOtherUserV500 = makePutRequest(request500WithNewAccountId, write(putCreateAccountOtherUserJsonV310))
      
      val account2 = responseWithOtherUserV500.body.extract[CreateAccountResponseJsonV310]
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
      When(s"We make a request $VersionOfApi")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val request500 = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / userAccountId ).PUT <@(user1)
      val putCreateAccountJson = putCreateAccountJSONV310.copy(account_routings = List(AccountRoutingJsonV121("AccountNumber", "15649885656")))
      val response500 = makePutRequest(request500, write(putCreateAccountJson))
      Then("We should get a 201")
      response500.code should equal(201)
      //for create account endpoint, we need to wait for `setAccountHolderAndRefreshUserAccountAccess` method, 
      //it is an asynchronous process, need some time to be done.
      TimeUnit.SECONDS.sleep(2)
      
      val account = response500.body.extract[CreateAccountResponseJsonV310]
      account.product_code should be (putCreateAccountJson.product_code)
      account.`label` should be (putCreateAccountJson.`label`)
      account.balance.amount.toDouble should be (putCreateAccountJson.balance.amount.toDouble)
      account.balance.currency should be (putCreateAccountJson.balance.currency)
      account.branch_id should be (putCreateAccountJson.branch_id)
      account.user_id should be (putCreateAccountJson.user_id)
      account.label should be (putCreateAccountJson.label)
      account.account_routings should be (putCreateAccountJson.account_routings)


      Then(s"we call $ApiEndpoint6 to get the account back")
      val requestApiEndpoint6 = (v5_0_0_Request /"banks" / testBankId.value / "accounts" / userAccountId / Constant.SYSTEM_OWNER_VIEW_ID/ "account" ).GET <@(user1)
      val responseApiEndpoint6 = makeGetRequest(requestApiEndpoint6)

      responseApiEndpoint6.code should equal(200)
      val accountEndpoint6 = responseApiEndpoint6.body.extract[ModeratedCoreAccountJsonV400]
      accountEndpoint6.id should be (userAccountId)
      accountEndpoint6.label should be (account.label)

      Then(s"we prepare the user2 will create a new account ($ApiEndpoint2)and he will have system view, and to call  get account ($ApiEndpoint6) and compare the result.")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser2.userId, ApiRole.canCreateAccount.toString)
      val requestUser2_500 = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / user2AccountId ).PUT <@(user2)
      val responseUser2_500 = makePutRequest(requestUser2_500, write(putCreateAccountJSONV310.copy(user_id = resourceUser2.userId, balance = AmountOfMoneyJsonV121("EUR","0"))))
      Then("We should get a 201")
      responseUser2_500.code should equal(201)

      //for create account endpoint, we need to wait for `setAccountHolderAndRefreshUserAccountAccess` method, 
      //it is an asynchronous process, need some time to be done.
      TimeUnit.SECONDS.sleep(2)


      Then(s"we call $ApiEndpoint6 to get the account back by user2")
      val requestApiUser2Endpoint6 = (v5_0_0_Request /"banks" / testBankId.value / "accounts" / userAccountId / Constant.SYSTEM_OWNER_VIEW_ID/ "account" ).GET <@(user2)
      val responseApiUser2Endpoint6 = makeGetRequest(requestApiUser2Endpoint6)
      //This mean, the user2 can not get access to user1's account!
      responseApiUser2Endpoint6.code should not equal(200)
      
    }

    scenario("Create new account with an already existing routing scheme/address should not create the account", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi to create the first account")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val request310_1 = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / "TEST_ACCOUNT_ID_1" ).PUT <@(user1)
      val response310_1 = makePutRequest(request310_1, write(putCreateAccountJSONV310))
      Then("We should get a 201")
      response310_1.code should equal(201)

      //for create account endpoint, we need to wait for `setAccountHolderAndRefreshUserAccountAccess` method, 
      //it is an asynchronous process, need some time to be done.
      TimeUnit.SECONDS.sleep(2)
      val account = response310_1.body.extract[CreateAccountResponseJsonV310]
      account.product_code should be (putCreateAccountJSONV310.product_code)
      account.`label` should be (putCreateAccountJSONV310.`label`)
      account.balance.amount.toDouble should be (putCreateAccountJSONV310.balance.amount.toDouble)
      account.balance.currency should be (putCreateAccountJSONV310.balance.currency)
      account.branch_id should be (putCreateAccountJSONV310.branch_id)
      account.user_id should be (putCreateAccountJSONV310.user_id)
      account.label should be (putCreateAccountJSONV310.label)
      account.account_routings should be (putCreateAccountJSONV310.account_routings)

      When(s"We make a request $VersionOfApi to create the second account with an already existing scheme/address")
      val request310_2 = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / user2AccountId ).PUT <@(user1)
      val response310_2 = makePutRequest(request310_2, write(putCreateAccountJSONV310))
      Then("We should get a 400 in the createAccount response")
      response310_2.code should equal(400)
      response310_2.body.toString should include("OBP-30115: Account Routing already exist.")

      Then(s"The second account should not be created")
      val requestApiGetAccount = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / user2AccountId / Constant.SYSTEM_OWNER_VIEW_ID / "account" ).GET <@(user1)
      val responseApiGetAccount = makeGetRequest(requestApiGetAccount)
      And("We should get a 404 in the getAccount response")
      responseApiGetAccount.code should equal(404)
    }

    scenario("Create new account with a duplication in routing scheme should not create the account", ApiEndpoint2, VersionOfApi) {
      When(s"We make a request $VersionOfApi to create the account")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val request500 = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / userAccountId ).PUT <@(user1)
      val putCreateAccountJsonWithRoutingSchemeDuplication = putCreateAccountJSONV310.copy(account_routings =
        List(AccountRoutingJsonV121(AccountRoutingScheme.IBAN.toString, Random.nextString(10)),
          AccountRoutingJsonV121(AccountRoutingScheme.IBAN.toString, Random.nextString(10))))
      val response500 = makePutRequest(request500, write(putCreateAccountJsonWithRoutingSchemeDuplication))
      Then("We should get a 400 in the createAccount response")
      response500.code should equal(400)
      response500.body.toString should include ("Duplication detected in account routings, please specify only one value per routing scheme")

      Then(s"The account should not be created")
      val requestApiGetAccount = (v5_0_0_Request / "banks" / testBankId.value / "accounts" / userAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "account" ).GET <@(user1)
      val responseApiGetAccount = makeGetRequest(requestApiGetAccount)
      And("We should get a 404 in the getAccount response")
      responseApiGetAccount.code should equal(404)
    }
  }

} 

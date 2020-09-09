package code.api.MXOpenFinance_0_0_1

import code.api.MxOpenFinace.APIMethods_AccountsApi
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{UserNoPermissionAccessView, UserNotLoggedIn}
import code.api.v3_0_0.ViewJsonV300
import code.api.v4_0_0.{PostAccountAccessJsonV400, PostViewJsonV400}
import code.model.dataAccess.MappedBankAccount
import code.setup.{APIResponse, DefaultUsers}
import code.views.system.AccountAccess
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, ErrorMessage}
import net.liftweb.json
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class MXOpenFinanceV001Tests extends MXOpenFinanceV001ServerSetup with DefaultUsers {
  
  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object MXOpenFinanceV001 extends Tag("MXOpenFinanceV001")
  object ApiEndpoint1 extends Tag(nameOf(APIMethods_AccountsApi.getAccountByAccountId))
  object ApiEndpoint2 extends Tag(nameOf(APIMethods_AccountsApi.getAccounts))
  
  val postJson =       json.compactRender (json.parse("""{
              "Data" : {
                "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
                "ExpirationDateTime" : "2028-02-23T04:56:07.000+00:00",
                "Permissions" : ["ReadAccountsBasic", "ReadAccountsDetail", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits", "ReadTransactionsDetail"],
                "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
              }}"""))

  
  
  feature(s"test $ApiEndpoint2 - Unauthorized access") {
    scenario("GET Accounts", MXOpenFinanceV001, ApiEndpoint2) {
      val requestGetAll = (MXOpenFinance001Request / "accounts" ).GET
      val response: APIResponse = makeGetRequest(requestGetAll)

      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }  
  feature(s"test $ApiEndpoint2 - Successful access") {
    scenario("GET Accounts", MXOpenFinanceV001, ApiEndpoint2) {
      val consentResponse = makePostRequest((MXOpenFinance001Request / "account-access-consents" ).POST <@(user1), postJson)
      Then("We should get a 201")
      consentResponse.code should equal(201)
      
      val requestGetAll = (MXOpenFinance001Request / "accounts" ).GET <@(user1)
      val response: APIResponse = makeGetRequest(requestGetAll)
      Then("We should get a 200")
      response.code should equal(200)
    }
  }
  

  feature(s"test $ApiEndpoint1 - Unauthorized access") {
    scenario("GET Account by ACCOUNT_ID", MXOpenFinanceV001, ApiEndpoint1) {
      val requestGetAll = (MXOpenFinance001Request / "accounts" / testAccountId1.value).GET
      val response: APIResponse = makeGetRequest(requestGetAll)

      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 - Successful access") {
    scenario("GET Account by ACCOUNT_ID", MXOpenFinanceV001, ApiEndpoint1) {
      val bankId = randomBankId
      val bankAccount = createAccountRelevantResource(Some(resourceUser1), BankId(bankId), AccountId("Some_unique_account_id"), "EUR")
      
      val consentResponse = makePostRequest((MXOpenFinance001Request / "account-access-consents" ).POST <@(user1), postJson)
      Then("We should get a 201")
      consentResponse.code should equal(201)
      
      val requestGetAll = (MXOpenFinance001Request / "accounts" / bankAccount.accountId.value ).GET <@(user1)
      val response: APIResponse = makeGetRequest(requestGetAll)
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message contains (UserNoPermissionAccessView) should be (true)

      val postAccountAccessJson = PostAccountAccessJsonV400(resourceUser1.userId, PostViewJsonV400("ReadAccountsBasic", true))
      val requestGrantAccess = (baseRequest / "obp" / "v4.0.0" / "banks" / bankAccount.bankId.value / "accounts" / bankAccount.accountId.value / "account-access" / "grant").POST <@ (user1)
      val responseGrantAccess = makePostRequest(requestGrantAccess, write(postAccountAccessJson))
      Then("We should get a 201 and check the response body")
      responseGrantAccess.code should equal(201)
      responseGrantAccess.body.extract[ViewJsonV300]
      
      val successResponse = makeGetRequest(requestGetAll)
      successResponse.code should equal(200)
    }
  }
    
}
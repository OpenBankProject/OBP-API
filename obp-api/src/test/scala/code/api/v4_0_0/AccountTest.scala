package code.api.v4_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.accountAttributeJson
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.util.ApiRole.CanCreateAccountAttributeAtOneBank
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v2_0_0.{BasicAccountJSON, BasicAccountsJSON}
import code.api.v3_1_0.{AccountAttributeResponseJson, CreateAccountResponseJsonV310, PostPutProductJsonV310, ProductJsonV310}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.AmountOfMoneyJsonV121
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.collection.immutable.List

class AccountTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getCoreAccountById))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getPrivateAccountByIdFull))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.addAccount))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getPrivateAccountsAtOneBank))

  lazy val testBankId = testBankId1
  lazy val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
  lazy val addAccountJsonOtherUser = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser2.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
  
  
  feature(s"test $ApiEndpoint1") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint1) {
      Given("We prepare the accounts in V300ServerSetup, just check the response")

      When("We send the request")
      val request = (v4_0_0_Request /"my" / "banks" / testBankId1.value/ "accounts" / testAccountId1.value / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedCoreAccountJsonV400 = response.body.extract[ModeratedCoreAccountJsonV400]
      moderatedCoreAccountJsonV400.account_attributes.length == 0 should be (true)
      moderatedCoreAccountJsonV400.views_basic.length >= 1 should be (true)

    }
  }
  feature(s"test $ApiEndpoint2") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint2) {
      Given("We prepare the accounts in V300ServerSetup, just check the response")

      lazy val bankId = randomBankId
      lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
      lazy val view = randomOwnerViewPermalinkViaEndpoint(bankId, bankAccount)

      When("We send the request")
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedAccountJSON400 = response.body.extract[ModeratedAccountJSON400]
      moderatedAccountJSON400.account_attributes.length == 0 should be (true)
      moderatedAccountJSON400.views_available.length >= 1 should be (true)
    }
  }

  feature(s"test $ApiEndpoint3 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId.value / "accounts"  ).POST
      val response400 = makePostRequest(request400, write(addAccountJson))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint3 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val response400 = try {
        val request400 = (v4_0_0_Request / "banks" / testBankId.value / "accounts" ).POST <@(user1)
        makePostRequest(request400, write(addAccountJson))
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 201")
      response400.code should equal(201)
      val account = response400.body.extract[CreateAccountResponseJsonV310]
      account.account_id should not be empty
      account.product_code should be (addAccountJson.product_code)
      account.`label` should be (addAccountJson.`label`)
      account.balance.amount.toDouble should be (addAccountJson.balance.amount.toDouble)
      account.balance.currency should be (addAccountJson.balance.currency)
      account.branch_id should be (addAccountJson.branch_id)
      account.user_id should be (addAccountJson.user_id)
      account.label should be (addAccountJson.label)
      account.account_routings should be (List(addAccountJson.account_routings))


      Then(s"We call $ApiEndpoint1 to get the account back")
      val request = (v4_0_0_Request /"my" / "banks" / testBankId.value/ "accounts" / account.account_id / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedCoreAccountJsonV400 = response.body.extract[ModeratedCoreAccountJsonV400]
      moderatedCoreAccountJsonV400.account_attributes.length == 0 should be (true)
      moderatedCoreAccountJsonV400.views_basic.length >= 1 should be (true)




      Then("We make a request v4.0.0 but with other user")
      val requestWithNewAccountId = (v4_0_0_Request / "banks" / testBankId.value / "accounts" ).POST <@(user1)
      val responseWithNoRole = makePostRequest(requestWithNewAccountId, write(addAccountJsonOtherUser))
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(s"$UserHasMissingRoles") should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val responseWithOtherUesr = makePostRequest(requestWithNewAccountId, write(addAccountJsonOtherUser))

      val account2 = responseWithOtherUesr.body.extract[CreateAccountResponseJsonV310]
      account2.account_id should not be empty
      account2.product_code should be (addAccountJson.product_code)
      account2.`label` should be (addAccountJson.`label`)
      account2.balance.amount.toDouble should be (addAccountJson.balance.amount.toDouble)
      account2.balance.currency should be (addAccountJson.balance.currency)
      account2.branch_id should be (addAccountJson.branch_id)
      account2.user_id should be (addAccountJsonOtherUser.user_id)
      account2.label should be (addAccountJson.label)
      account2.account_routings should be (List(addAccountJson.account_routings))
    }
  }

  feature(s"test $ApiEndpoint4 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, VersionOfApi) {

      val testBankId = randomBankId
      val parentPostPutProductJsonV310: PostPutProductJsonV310 = SwaggerDefinitionsJSON.postPutProductJsonV310.copy(parent_product_code ="")
      val postAccountAttributeJson = accountAttributeJson
      
      When("We will first prepare the product")
      val product: ProductJsonV310 =
        createProductViaEndpoint(
          bankId=testBankId,
          code=APIUtil.generateUUID(),
          json=parentPostPutProductJsonV310
        )
      
      Then("We will prepare the account attribute")
      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, CanCreateAccountAttributeAtOneBank.toString)
      When(s"We make a request $VersionOfApi")
      val requestCreate310 = (v4_0_0_Request / "banks" / testBankId / "accounts" / testAccountId0.value / "products" / product.code / "attribute").POST <@(user1)
      val responseCreate310 = makePostRequest(requestCreate310, write(postAccountAttributeJson))
      Then("We should get a 201")
      responseCreate310.code should equal(201)
      
      
      Then(s"We call $ApiEndpoint1 to get the account back")
      val request = (v4_0_0_Request /"banks" / testBankId/ "accounts").GET <@ (user1) 
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[List[BasicAccountJSON]].length should be (2)

      Then(s"We call $ApiEndpoint1 to get the account back with correct parameters")
      val request1 = (v4_0_0_Request /"banks" / testBankId/ "accounts").GET <@ (user1) <<? (List(("OVERDRAFT_START_DATE","2012-04-23")))
      val response1 = makeGetRequest(request1)

      Then("We should get a 200 and check the response body")
      response1.code should equal(200)
      response1.body.extract[List[BasicAccountJSON]].length should be (1)

      Then(s"We call $ApiEndpoint1 to get the account back with wrong parameters")
      val request2 = (v4_0_0_Request /"banks" / testBankId/ "accounts").GET <@ (user1) <<? (List(("OVERDRAFT_START_DATE1","2012-04-23")))
      val response2 = makeGetRequest(request2)

      Then("We should get a 200 and check the response body")
      response2.code should equal(200)
      response2.body.extract[List[BasicAccountJSON]].length should be (0)

    }
  }
  
}

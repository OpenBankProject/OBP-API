package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.accountAttributeJson
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateAccountAttributeAtOneBank
import code.api.util.ErrorMessages.{BankAccountNotFoundByAccountRouting, UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{APIUtil, ApiRole}
import code.api.v2_0_0.BasicAccountJSON
import code.api.v3_1_0.{CreateAccountResponseJsonV310, PostPutProductJsonV310, ProductJsonV310}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.collection.immutable.List
import scala.util.Random

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
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getAccountByAccountRouting))

  lazy val testBankId = testBankId1
  lazy val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
  lazy val addAccountJsonOtherUser = SwaggerDefinitionsJSON.createAccountRequestJsonV310
    .copy(user_id = resourceUser2.userId, balance = AmountOfMoneyJsonV121("EUR","0"),
      account_routings = List(AccountRoutingJsonV121(Random.nextString(10), Random.nextString(10))))
  lazy val getAccountByRoutingJson = SwaggerDefinitionsJSON.bankAccountRoutingJson
  
  
  feature(s"test $ApiEndpoint1") {
    scenario("prepare all the need parameters", VersionOfApi, ApiEndpoint1) {
      Given("We prepare the accounts in V300ServerSetup, just check the response")

      When("We send the request")
      val request = (v4_0_0_Request /"my" / "banks" / testBankId1.value/ "accounts" / testAccountId1.value / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedCoreAccountJsonV400 = response.body.extract[ModeratedCoreAccountJsonV400]
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
      account.account_routings should be (addAccountJson.account_routings)


      Then(s"We call $ApiEndpoint1 to get the account back")
      val request = (v4_0_0_Request /"my" / "banks" / testBankId.value/ "accounts" / account.account_id / "account").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val moderatedCoreAccountJsonV400 = response.body.extract[ModeratedCoreAccountJsonV400]
      moderatedCoreAccountJsonV400.views_basic.length >= 1 should be (true)




      Then("We make a request v4.0.0 but with other user")
      val requestWithNewAccountId = (v4_0_0_Request / "banks" / testBankId.value / "accounts" ).POST <@(user1)
      val responseWithNoRole = makePostRequest(requestWithNewAccountId, write(addAccountJsonOtherUser))
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(s"$UserHasMissingRoles") should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val responseWithOtherUser = makePostRequest(requestWithNewAccountId, write(addAccountJsonOtherUser))

      val account2 = responseWithOtherUser.body.extract[CreateAccountResponseJsonV310]
      account2.account_id should not be empty
      account2.product_code should be (addAccountJsonOtherUser.product_code)
      account2.`label` should be (addAccountJsonOtherUser.`label`)
      account2.balance.amount.toDouble should be (addAccountJsonOtherUser.balance.amount.toDouble)
      account2.balance.currency should be (addAccountJsonOtherUser.balance.currency)
      account2.branch_id should be (addAccountJsonOtherUser.branch_id)
      account2.user_id should be (addAccountJsonOtherUser.user_id)
      account2.label should be (addAccountJsonOtherUser.label)
      account2.account_routings should be (addAccountJsonOtherUser.account_routings)
    }

    scenario("Create new account with an already existing routing scheme/address should not create the account", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0 to create the first account")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      val request400_1 = (v4_0_0_Request / "banks" / testBankId.value / "accounts").POST <@(user1)
      val response400_1 = makePostRequest(request400_1, write(addAccountJson))
      Then("We should get a 201")
      response400_1.code should equal(201)
      val account = response400_1.body.extract[CreateAccountResponseJsonV310]
      account.account_id should not be empty
      account.product_code should be (addAccountJson.product_code)
      account.`label` should be (addAccountJson.`label`)
      account.balance.amount.toDouble should be (addAccountJson.balance.amount.toDouble)
      account.balance.currency should be (addAccountJson.balance.currency)
      account.branch_id should be (addAccountJson.branch_id)
      account.user_id should be (addAccountJson.user_id)
      account.label should be (addAccountJson.label)
      account.account_routings should be (addAccountJson.account_routings)

      When("We make a request v4.0.0 to create the second account with an already existing scheme/address")
      val request400_2 = (v4_0_0_Request / "banks" / testBankId.value / "accounts").POST <@(user1)
      val response400_2 = makePostRequest(request400_2, write(addAccountJson))
      Then("We should get a 400 in the createAccount response")
      response400_2.code should equal(400)
      response400_2.body.toString should include("OBP-30115: Account Routing already exist.")
    }

    scenario("Create new account with a duplication in routing scheme should not create the account", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanCreateAccount.toString)
      When("We make a request v4.0.0 to create the account")
      val request400 = (v4_0_0_Request / "banks" / testBankId.value / "accounts").POST <@(user1)
      val postCreateAccountJsonWithRoutingSchemeDuplication = addAccountJson.copy(account_routings =
        List(AccountRoutingJsonV121(AccountRoutingScheme.IBAN.toString, Random.nextString(10)),
          AccountRoutingJsonV121(AccountRoutingScheme.IBAN.toString, Random.nextString(10))))
      val response400 = makePostRequest(request400, write(postCreateAccountJsonWithRoutingSchemeDuplication))
      Then("We should get a 400 in the createAccount response")
      response400.code should equal(400)
      response400.body.toString should include ("OBP-30114: Invalid Account Routings. Duplication detected in account routings, please specify only one value per routing scheme")
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

  feature(s"test $ApiEndpoint5 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "accounts" / "account-routing-query").POST
      val response400 = makePostRequest(request400, write(getAccountByRoutingJson))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint5 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint5, VersionOfApi) {
      Given("We create an account with account routings")

      val accountRoutingSchemeTest = "AccountNumber"
      val accountRoutingAddressTest = "1567564589535564"

      val createdAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310
        .copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR", "0"),
          account_routings = List(
            AccountRoutingJsonV121(accountRoutingSchemeTest, accountRoutingAddressTest)
          ))

      createAccountViaEndpoint(testBankId.value, createdAccountJson, user1)


      When("We make a request to get the account with an existing account routing without specifying bankId")
      val getAccountByRoutingWithNoBankJson = getAccountByRoutingJson.copy(bank_id = None, AccountRoutingJsonV121(accountRoutingSchemeTest, accountRoutingAddressTest))

      val requestNoBank = (v4_0_0_Request / "management" / "accounts" / "account-routing-query").POST <@ (user1)
      val responseNoBank = makePostRequest(requestNoBank, write(getAccountByRoutingWithNoBankJson))

      Then("We should get a 200 and check the response body")
      responseNoBank.code should equal(200)
      val account1 = responseNoBank.body.extract[ModeratedAccountJSON400]
      account1.account_routings.find(_.scheme == getAccountByRoutingWithNoBankJson.account_routing.scheme)
        .map(_.address) shouldBe Some(getAccountByRoutingWithNoBankJson.account_routing.address)


      When("We make a request to get the account with an existing account routing with specifying correct bankId")
      val getAccountByRoutingWithBankJson = getAccountByRoutingJson.copy(Some(testBankId.value), AccountRoutingJsonV121(accountRoutingSchemeTest, accountRoutingAddressTest))

      val requestWithBank = (v4_0_0_Request / "management" / "accounts" / "account-routing-query").POST <@ (user1)
      val responseWithBank = makePostRequest(requestWithBank, write(getAccountByRoutingWithBankJson))

      Then("We should get a 200 and check the response body")
      responseWithBank.code should equal(200)
      val account2 = responseWithBank.body.extract[ModeratedAccountJSON400]
      account2.account_routings.find(_.scheme == getAccountByRoutingWithNoBankJson.account_routing.scheme)
        .map(_.address) shouldBe Some(getAccountByRoutingWithNoBankJson.account_routing.address)


      When("We make a request to get the account with an existing account routing with specifying incorrect bankId")
      val getAccountByRoutingIncorrectBankJson = getAccountByRoutingJson.copy(Some(testBankId2.value), AccountRoutingJsonV121(accountRoutingSchemeTest, accountRoutingAddressTest))

      val requestIncorrectBank = (v4_0_0_Request / "management" / "accounts" / "account-routing-query").POST <@ (user1)
      val responseIncorrectBank = makePostRequest(requestIncorrectBank, write(getAccountByRoutingIncorrectBankJson))

      Then("We should get a 404 with an error message")
      responseIncorrectBank.code should equal(404)
      responseIncorrectBank.body.extract[ErrorMessage].message contains BankAccountNotFoundByAccountRouting should be (true)


      When("We make a request to get the account with an non-existing account routing")
      val getAccountByRoutingIncorrectRouting = getAccountByRoutingJson.copy(bank_id = None, account_routing = AccountRoutingJsonV121("UnknownScheme", "UnknownAddress"))

      val requestIncorrectRouting = (v4_0_0_Request / "management" / "accounts" / "account-routing-query").POST <@ (user1)
      val responseIncorrectRouting = makePostRequest(requestIncorrectRouting, write(getAccountByRoutingIncorrectRouting))

      Then("We should get a 404 with an error message")
      responseIncorrectBank.code should equal(404)
      responseIncorrectBank.body.extract[ErrorMessage].message contains BankAccountNotFoundByAccountRouting should be (true)

    }
  }

  feature(s"test ${ApiEndpoint3.name}") {
    scenario("We will test ${ApiEndpoint3.name}", ApiEndpoint3, VersionOfApi) {
      Given("The test bank and test accounts")
      val requestGet = (v4_0_0_Request / "banks" / testBankId.value / "balances").GET <@ (user1)

      val responseGet = makeGetRequest(requestGet)
      responseGet.code should equal(200)
      responseGet.body.extract[AccountsBalancesJsonV400].accounts.size > 0 should be (true)
    }
  }
  
}

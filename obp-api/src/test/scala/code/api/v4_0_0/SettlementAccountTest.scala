package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.accountAttributeJson
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateAccountAttributeAtOneBank
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.{APIUtil, ApiRole, NewStyle}
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

class SettlementAccountTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object CreateSettlementAccountEndpoint extends Tag(nameOf(Implementations4_0_0.createSettlementAccount))
  object GetSettlementAccountsEndpoint extends Tag(nameOf(Implementations4_0_0.getSettlementAccounts))

  lazy val testBankId = testBankId1
  lazy val createSettlementAccountJson = SwaggerDefinitionsJSON.settlementAccountRequestJson
    .copy(user_id = resourceUser1.userId, payment_system = "SEPA", balance = AmountOfMoneyJsonV121("EUR","0"))
  lazy val createSettlementAccountOtherUser = SwaggerDefinitionsJSON.settlementAccountRequestJson
    .copy(user_id = resourceUser2.userId, payment_system = "CARD", balance = AmountOfMoneyJsonV121("USD","0"),
      account_routings = List(AccountRoutingJsonV121(Random.nextString(10), Random.nextString(10))))
  

  feature(s"test $CreateSettlementAccountEndpoint - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", CreateSettlementAccountEndpoint, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId.value / "settlement-accounts").POST
      val response400 = makePostRequest(request400, write(createSettlementAccountJson))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature(s"test $CreateSettlementAccountEndpoint - Authorized access") {
    scenario("We will call the endpoint with user credentials", CreateSettlementAccountEndpoint, VersionOfApi) {
      When("We make a request v4.0.0")
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanCreateSettlementAccountAtOneBank.toString)
      val response400 = try {
        val request400 = (v4_0_0_Request / "banks" / testBankId.value / "settlement-accounts" ).POST <@(user1)
        makePostRequest(request400, write(createSettlementAccountJson))
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 201")
      response400.code should equal(201)
      val account = response400.body.extract[SettlementAccountResponseJson]
      account.account_id should not be empty
      account.payment_system should be (createSettlementAccountJson.payment_system)
      account.balance.amount.toDouble should be (createSettlementAccountJson.balance.amount.toDouble)
      account.balance.currency should be (createSettlementAccountJson.balance.currency)
      account.branch_id should be (createSettlementAccountJson.branch_id)
      account.user_id should be (createSettlementAccountJson.user_id)
      account.label should be (createSettlementAccountJson.label)
      account.account_routings should be (createSettlementAccountJson.account_routings)


      Then("We make a request v4.0.0 but with other user")
      val requestWithNewAccountId = (v4_0_0_Request / "banks" / testBankId.value / "settlement-accounts" ).POST <@(user1)
      val responseWithNoRole = makePostRequest(requestWithNewAccountId, write(createSettlementAccountOtherUser))
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(s"$UserHasMissingRoles") should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanCreateSettlementAccountAtOneBank.toString)
      val responseWithOtherUser = makePostRequest(requestWithNewAccountId, write(createSettlementAccountOtherUser))

      val account2 = responseWithOtherUser.body.extract[SettlementAccountResponseJson]
      account2.account_id should not be empty
      account2.payment_system should be (createSettlementAccountOtherUser.payment_system)
      account2.balance.amount.toDouble should be (createSettlementAccountOtherUser.balance.amount.toDouble)
      account2.balance.currency should be (createSettlementAccountOtherUser.balance.currency)
      account2.branch_id should be (createSettlementAccountOtherUser.branch_id)
      account2.user_id should be (createSettlementAccountOtherUser.user_id)
      account2.label should be (createSettlementAccountOtherUser.label)
      account2.account_routings should be (createSettlementAccountOtherUser.account_routings)
    }
  }


  feature(s"test $GetSettlementAccountsEndpoint - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", VersionOfApi, GetSettlementAccountsEndpoint) {
      Given("The two previously created settlement accounts")

      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId.value / "settlement-accounts").GET
      val response = makeGetRequest(request)

      Then("We should get a 401")
      response.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature(s"test $GetSettlementAccountsEndpoint - Authorized access") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, GetSettlementAccountsEndpoint) {
      Given("We create two settlement accounts at the testBank")

      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanCreateSettlementAccountAtOneBank.toString)
      makePostRequest((v4_0_0_Request / "banks" / testBankId.value / "settlement-accounts" ).POST <@(user1), write(createSettlementAccountJson))
      makePostRequest((v4_0_0_Request / "banks" / testBankId.value / "settlement-accounts" ).POST <@(user1), write(createSettlementAccountOtherUser))

      When("We send the request")
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanGetSettlementAccountAtOneBank.toString)

      val response = try {
        val request = (v4_0_0_Request / "banks" / testBankId.value / "settlement-accounts").GET <@(user1)
        makeGetRequest(request)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }

      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val settlementAccounts = response.body.extract[SettlementAccountsJson]
      val sepaSettlementAccount = settlementAccounts.settlement_accounts.find(_.account_id == "SEPA_SETTLEMENT_ACCOUNT_EUR")
      val cardSettlementAccount = settlementAccounts.settlement_accounts.find(_.account_id == "CARD_SETTLEMENT_ACCOUNT_USD")
      sepaSettlementAccount.map(_.balance.currency) shouldBe Some("EUR")
      sepaSettlementAccount.map(_.payment_system) shouldBe Some("SEPA")
      cardSettlementAccount.map(_.balance.currency) shouldBe Some("USD")
      cardSettlementAccount.map(_.payment_system) shouldBe Some("CARD")


      Then("We make a request v4.0.0 but with other user")
      val requestWithNewAccountId = (v4_0_0_Request / "banks" / testBankId.value / "settlement-accounts" ).GET <@(user1)
      val responseWithNoRole = makeGetRequest(requestWithNewAccountId)
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains(s"$UserHasMissingRoles") should be (true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanGetSettlementAccountAtOneBank.toString)
      val responseWithOtherUser = makeGetRequest(requestWithNewAccountId)

      val settlementAccounts1 = responseWithOtherUser.body.extract[SettlementAccountsJson]
      val sepaSettlementAccount1 = settlementAccounts1.settlement_accounts.find(_.account_id == "SEPA_SETTLEMENT_ACCOUNT_EUR")
      val cardSettlementAccount1 = settlementAccounts1.settlement_accounts.find(_.account_id == "CARD_SETTLEMENT_ACCOUNT_USD")
      sepaSettlementAccount1.map(_.balance.currency) shouldBe Some("EUR")
      sepaSettlementAccount1.map(_.payment_system) shouldBe Some("SEPA")
      cardSettlementAccount1.map(_.balance.currency) shouldBe Some("USD")
      cardSettlementAccount1.map(_.payment_system) shouldBe Some("CARD")
    }
  }
  
}

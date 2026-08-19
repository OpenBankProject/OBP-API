package code.api.UKOpenBanking.v2_0_0

import code.api.UKOpenBanking.v2_0_0.JSONFactory_UKOpenBanking_200.{AccountBalancesUKV200, Accounts, TransactionsJsonUKV200}
import org.json4s.jvalue2extractable
import code.api.util.APIUtil.OAuth._
import code.setup.{APIResponse, DefaultUsers}
import org.scalatest.Tag

class UKOpenBankingV200Tests extends UKOpenBankingV200ServerSetup with DefaultUsers {

  object UKOpenBankingV200 extends Tag("UKOpenBankingV200")

  Feature("test the UKOpenBankingV200 GET Account List") {
    Scenario("Successful Case", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "accounts" ).GET <@(user1)
      val response: APIResponse = makeGetRequest(requestGetAll)

      Then("We should get a 200 ")
      response.code should equal(200)
      val accounts = response.body.extract[Accounts]
      accounts.Links.Self contains ("open-banking/v2.0/accounts") should be (true)
    }

    Scenario("Unauthenticated access is rejected", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "accounts" ).GET
      val response: APIResponse = makeGetRequest(requestGetAll)

      Then("We should get a 401 ")
      response.code should equal(401)
    }
  }

  Feature("test the UKOpenBankingV200 GET Account") {
    Scenario("Successful Case", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "accounts" / testAccountId1.value ).GET <@(user1)
      val response: APIResponse = makeGetRequest(requestGetAll)

      Then("We should get a 200 ")
      response.code should equal(200)
      val accounts = response.body.extract[Accounts]
      accounts.Links.Self contains ("open-banking/v2.0/accounts") should be (true)
    }

    Scenario("Unauthenticated access is rejected", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "accounts" / testAccountId1.value ).GET
      val response: APIResponse = makeGetRequest(requestGetAll)

      Then("We should get a 401 ")
      response.code should equal(401)
    }
  }

  Feature("test the UKOpenBankingV200 Get Account Balances") {
    Scenario("Successful Case", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "accounts"/ testAccountId1.value /"balances" ).GET <@(user1)
      val response = makeGetRequest(requestGetAll)

      Then("We should get a 200 ")
      response.code should equal(200)
      val accountBalancesUKV200 = response.body.extract[AccountBalancesUKV200]
      accountBalancesUKV200.Links.Self contains("balances")

    }

    Scenario("Unauthenticated access is rejected", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "accounts"/ testAccountId1.value /"balances" ).GET
      val response = makeGetRequest(requestGetAll)

      Then("We should get a 401 ")
      response.code should equal(401)
    }
  }

  Feature("test the UKOpenBankingV200 Get Balances") {
    Scenario("Successful Case", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "balances" ).GET <@(user1)
      val response = makeGetRequest(requestGetAll)

      Then("We should get a 200 ")
      response.code should equal(200)
      val accountBalancesUKV200 = response.body.extract[AccountBalancesUKV200]
      accountBalancesUKV200.Links.Self contains("balances")

    }

    Scenario("Unauthenticated access is rejected", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "balances" ).GET
      val response = makeGetRequest(requestGetAll)

      Then("We should get a 401 ")
      response.code should equal(401)
    }
  }

  Feature("test the UKOpenBankingV200 GET Account Transactions") {
    Scenario("Successful Case", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "accounts"/ testAccountId1.value /"transactions" ).GET <@(user1)
      val response = makeGetRequest(requestGetAll)

      Then("We should get a 200 ")
      response.code should equal(200)

      val transactionsJsonUKV200 = response.body.extract[TransactionsJsonUKV200]
      transactionsJsonUKV200.Links.Self contains("Transactions")
    }

    Scenario("Unauthenticated access is rejected", UKOpenBankingV200) {
      val requestGetAll = (UKOpenBankingV200Request / "accounts"/ testAccountId1.value /"transactions" ).GET
      val response = makeGetRequest(requestGetAll)

      Then("We should get a 401 ")
      response.code should equal(401)
    }
  }

}

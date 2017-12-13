package code.api.v3_0_0

class TransactionsTest extends V300ServerSetup {
  
  feature("Get Transactions for Account (Full)") {
    scenario("Success Full case") {
      When("We prepare the input data")
      val bankId = randomBankId
      val accountId = randomPrivateAccountId(bankId)
      val loginedUser = user1

      Then("we call the endpoint")
      val httpResponse = getTransactionsForAccountFull(bankId, accountId,loginedUser)

      Then("We should get a 200 and check the response body")
      httpResponse.code should equal(200)
//      httpResponse.body.extract[TransactionsJsonV300]
    }
  }

  feature("Get Transactions for Account (Core)") {
    scenario("Success Full case") {
      When("We prepare the input data")
      val bankId = randomBankId
      val accountId = randomPrivateAccountId(bankId)
      val loginedUser = user1
  
      Then("we call the endpoint")
      val httpResponse = getTransactionsForAccountCore(bankId, accountId,loginedUser)

      Then("We should get a 200 and check the response body")
      httpResponse.code should equal(200)
      httpResponse.body.extract[CoreTransactionsJsonV300]
    }
  }

}

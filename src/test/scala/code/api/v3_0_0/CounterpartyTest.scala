package code.api.v3_0_0

class CounterpartyTest extends V300ServerSetup {
  
  feature("Get Other Accounts of one Account.and Get Other Account by Id. - V300") {
    scenario("prepare all the need parameters") {
      Given("We prepare all the parameters, just check the response")
      val bankId = randomBankId
      val accountId = randomPrivateAccountId(bankId)
      val viewId = "owner"
      val loginedUser = user1
      
      When("we call the `Get Other Accounts of one Account.`")
      val httpResponseAccounts = getOtherAccountsForBankAccount(bankId,accountId,viewId,user1)

      Then("We should get a 200 and check the response body")
      httpResponseAccounts.code should equal(200)
      val otherAccountsJson = httpResponseAccounts.body.extract[OtherAccountsJsonV300]
      
      
      Then("We random get a otherAccountId ")
      val otherAccountId=otherAccountsJson.other_accounts.head.id
      
      Then("we call the `Get Other Account by Id.`")
      val httpResponseAccount = getOtherAccountByIdForBankAccount(bankId,accountId,viewId,otherAccountId,user1)
      
      
      Then("We should get a 200 and check the response body")
      httpResponseAccount.code should equal(200)
      httpResponseAccount.body.extract[OtherAccountJsonV300]
    }
  }

}

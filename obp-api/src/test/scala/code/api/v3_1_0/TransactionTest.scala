/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateHistoricalTransaction
import code.api.util.{ApiRole, ApiVersion}
import code.api.util.ErrorMessages.UserNotLoggedIn
import code.api.v1_2_1.TransactionJSON
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_1_0.TransactionRequestWithChargeJSONs210
import code.api.v3_0_0.NewModeratedCoreAccountJsonV300
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.AmountOfMoneyJsonV121
import org.scalatest.Tag
import net.liftweb.json.Serialization.write

class TransactionTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.getTransactionRequests))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.saveHistoricalTransaction))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_0_0.getCoreAccountById))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_1_0.getTransactionByIdForBankAccount))

  val bankId1 = testBankId1.value
  val bankId2 = testBankId2.value
  val bankAccountId1 = testAccountId1.value
  val bankAccountId2 = testAccountId0.value
  val postJson = SwaggerDefinitionsJSON.postHistoricalTransactionJson.copy(
    from = TransactionRequestAccountJsonV140(bankId1,bankAccountId1),
    to = TransactionRequestAccountJsonV140(bankId2,bankAccountId2),
    value = AmountOfMoneyJsonV121("EUR","1000")
  )
  
  feature("Get Transaction by Id - v3.1.0")
  {
    scenario("We will Get Transaction by Id - user is NOT logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "transactions" / transaction.id / "transaction").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will Get Transaction by Id - user is logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "transactions" / transaction.id / "transaction").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[TransactionRequestWithChargeJSONs210]
    }
  }

  feature(s"$ApiEndpoint2")
  {
    scenario("We will test saveHistoricalTransaction --user is not Login", ApiEndpoint2, ApiEndpoint4, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions").POST
      val response310 = makePostRequest(request310, write(postJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    
    scenario("We will test saveHistoricalTransaction --user is not Login, but no Role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions")<@(user1)
      val response310 = makePostRequest(request310, write(postJson))
      Then("We should get a 400")
      response310.code should equal(403)
      response310.body.toString contains (ApiRole.canCreateHistoricalTransaction.toString()) should be (true)
    }

    scenario("We will test saveHistoricalTransaction --user is not Login, with Role and with Proper values", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateHistoricalTransaction.toString)
            
      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions")<@(user1)
      val response310 = makePostRequest(request310, write(postJson))
      
      Then("We should get a 201")
      response310.code should equal(201)
      val responseJson = response310.body.extract[PostHistoricalTransactionResponseJson]
      responseJson.value should be(postJson.value)
      responseJson.description should be(postJson.description)
      responseJson.transaction_id.length > 0 should be (true)
    }

    scenario("We will test saveHistoricalTransaction --user is not Login, with Role and with Proper values, and check the account balance", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      
      //Before call saveHistoricalTransaction, we need store the balance for both account:
      //ApiEndpoint3
      val requestGetAccount1 = (v3_1_0_Request /"my" / "banks" / bankId1/ "accounts" / bankAccountId1 / "account").GET <@ (user1)
      val httpResponseGetAccount1 = makeGetRequest(requestGetAccount1)
      httpResponseGetAccount1.code should equal(200)
      val accountI1Balance = httpResponseGetAccount1.body.extract[NewModeratedCoreAccountJsonV300].balance.amount

      val requestGetAccount2 = (v3_1_0_Request /"my" / "banks" / bankId2/ "accounts" / bankAccountId2 / "account").GET <@ (user1)
      val httpResponseGetAccount2 = makeGetRequest(requestGetAccount2)
      httpResponseGetAccount2.code should equal(200)
      val accountI2Balance= httpResponseGetAccount2.body.extract[NewModeratedCoreAccountJsonV300].balance.amount
      
      
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateHistoricalTransaction.toString)

      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions")<@(user1)
      val response310 = makePostRequest(request310, write(postJson))

      Then("We should get a 201")
      response310.code should equal(201)
      val responseJson = response310.body.extract[PostHistoricalTransactionResponseJson]
      responseJson.value should be(postJson.value)
      responseJson.description should be(postJson.description)
      responseJson.transaction_id.length > 0 should be (true)

      val requestGetAccount1After = (v3_1_0_Request /"my" / "banks" / bankId1/ "accounts" / bankAccountId1 / "account").GET <@ (user1)
      val httpResponseGetAccount1After = makeGetRequest(requestGetAccount1)
      httpResponseGetAccount1After.code should equal(200)
      val accountI1BalanceAfter = httpResponseGetAccount1After.body.extract[NewModeratedCoreAccountJsonV300].balance.amount

      val requestGetAccount2After = (v3_1_0_Request /"my" / "banks" / bankId2/ "accounts" / bankAccountId2 / "account").GET <@ (user1)
      val httpResponseGetAccount2After = makeGetRequest(requestGetAccount2)
      httpResponseGetAccount2After.code should equal(200)
      val accountI2BalanceAfter= httpResponseGetAccount2After.body.extract[NewModeratedCoreAccountJsonV300].balance.amount

      (BigDecimal(accountI1BalanceAfter) - BigDecimal(accountI1Balance)) should be (BigDecimal(-1000)) 
      (BigDecimal(accountI2BalanceAfter) - BigDecimal(accountI2Balance)) should be (BigDecimal(1000)) 
      
      Then("We can get the transaction back")
      val transactionNewId = responseJson.transaction_id
      val getTransactionbyIdRequest = (v3_1_0_Request / "banks" / bankId1/ "accounts" / bankAccountId1 / "owner" / "transactions" / transactionNewId / "transaction").GET <@ (user1)
      val getTransactionbyIdResponse = makeGetRequest(getTransactionbyIdRequest)

      getTransactionbyIdResponse.code should equal(200)
      getTransactionbyIdResponse.body.extract[TransactionJSON].id should be(transactionNewId)
      getTransactionbyIdResponse.body.extract[TransactionJSON].details.`type` should be(postJson.`type`)
      getTransactionbyIdResponse.body.extract[TransactionJSON].details.description should be(postJson.description)
      
    }
    
    
  }

}

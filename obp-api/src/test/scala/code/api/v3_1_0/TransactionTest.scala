/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v3_1_0

import code.api.Constant._
import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateHistoricalTransaction
import code.api.util.ApiRole
import code.api.util.ErrorMessages._
import code.api.v1_2_1.TransactionJSON
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_1_0.TransactionRequestWithChargeJSONs210
import code.api.v2_2_0.CounterpartyWithMetadataJson
import code.api.v3_0_0.{NewModeratedCoreAccountJsonV300, TransactionJsonV300}
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.api.v3_1_0.OBPAPI3_1_0.Implementations2_2_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.AmountOfMoneyJsonV121
import com.openbankproject.commons.util.ApiVersion
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
  //Use this endpoint to prepare the data.
  object ApiEndpoint5 extends Tag(nameOf(Implementations2_2_0.createCounterparty))

  val bankId1 = testBankId1.value
  val bankId2 = testBankId2.value
  val bankAccountId1 = testAccountId1.value
  val bankAccountId2 = testAccountId0.value
  val postJsonAccount = SwaggerDefinitionsJSON.postHistoricalTransactionJson.copy(
    from = HistoricalTransactionAccountJsonV310(Some(bankId1), Some(bankAccountId1), None),
    to = HistoricalTransactionAccountJsonV310(Some(bankId2), Some(bankAccountId2), None),
    value = AmountOfMoneyJsonV121("EUR","1000")
  )
  
  feature("Get Transaction by Id - v3.1.0")
  {
    scenario("We will Get Transaction by Id - user is NOT logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = bankAccount.views_available.map(_.id).headOption.getOrElse("owner")
      val transaction = randomTransaction(bankId, bankAccount.id, view)
      val request310 = (v3_1_0_Request / "banks" / bankId / "accounts" / bankAccount.id / view / "transactions" / transaction.id / "transaction").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    scenario("We will Get Transaction by Id - user is logged in", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = bankAccount.views_available.map(_.id).headOption.getOrElse("owner")
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
      val response310 = makePostRequest(request310, write(postJsonAccount))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
    
    scenario("We will test saveHistoricalTransaction --user is not Login, but no Role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions")<@(user1)
      val response310 = makePostRequest(request310, write(postJsonAccount))
      Then("We should get a 400")
      response310.code should equal(403)
      response310.body.toString contains (ApiRole.canCreateHistoricalTransaction.toString()) should be (true)
    }

    scenario("We will test saveHistoricalTransaction --user is not Login, with Role and with Proper values", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateHistoricalTransaction.toString)
            
      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions")<@(user1)
      val response310 = makePostRequest(request310, write(postJsonAccount))
      
      Then("We should get a 201")
      response310.code should equal(201)
      val responseJson = response310.body.extract[PostHistoricalTransactionResponseJson]
      responseJson.value should be(postJsonAccount.value)
      responseJson.description should be(postJsonAccount.description)
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
      val response310 = makePostRequest(request310, write(postJsonAccount))

      Then("We should get a 201")
      response310.code should equal(201)
      val responseJson = response310.body.extract[PostHistoricalTransactionResponseJson]
      responseJson.value should be(postJsonAccount.value)
      responseJson.description should be(postJsonAccount.description)
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
      val getTransactionbyIdRequest = (v3_1_0_Request / "banks" / bankId1/ "accounts" / bankAccountId1 / CUSTOM_OWNER_VIEW_ID / "transactions" / transactionNewId / "transaction").GET <@ (user1)
      val getTransactionbyIdResponse = makeGetRequest(getTransactionbyIdRequest)

      getTransactionbyIdResponse.code should equal(200)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].id should be(transactionNewId)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].details.`type` should be(postJsonAccount.`type`)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].details.description should be(postJsonAccount.description)
      
    }

    scenario("We will test saveHistoricalTransaction -- account --> counterparty", ApiEndpoint2, VersionOfApi) {
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


      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJSON.copy(other_bank_routing_address=bankId2,other_account_routing_address=bankAccountId2)

      When(s"We make the request Create counterparty for an account $ApiEndpoint5")
      val requestPost = (v3_1_0_Request / "banks" / bankId1 / "accounts" / bankAccountId1 / "owner" / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      val counterpartyId = responsePost.body.extract[CounterpartyWithMetadataJson].counterparty_id
      
      
      val postJsonCounterparty = SwaggerDefinitionsJSON.postHistoricalTransactionJson.copy(
        from = HistoricalTransactionAccountJsonV310(Some(bankId1), Some(bankAccountId1), None),
        to = HistoricalTransactionAccountJsonV310(None,None, Some(counterpartyId)),
        value = AmountOfMoneyJsonV121("EUR","1000")
      )
      

      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions")<@(user1)
      val response310 = makePostRequest(request310, write(postJsonCounterparty))

      Then("We should get a 201")
      response310.code should equal(201)
      val responseJson = response310.body.extract[PostHistoricalTransactionResponseJson]
      responseJson.value should be(postJsonAccount.value)
      responseJson.description should be(postJsonAccount.description)
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
      val getTransactionbyIdRequest = (v3_1_0_Request / "banks" / bankId1/ "accounts" / bankAccountId1 / CUSTOM_OWNER_VIEW_ID / "transactions" / transactionNewId / "transaction").GET <@ (user1)
      val getTransactionbyIdResponse = makeGetRequest(getTransactionbyIdRequest)

      getTransactionbyIdResponse.code should equal(200)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].id should be(transactionNewId)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].details.`type` should be(postJsonAccount.`type`)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].details.description should be(postJsonAccount.description)
    }

    scenario("We will test saveHistoricalTransaction -- counterparty  --> account", ApiEndpoint2, VersionOfApi) {
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


      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJSON.copy(other_bank_routing_address=bankId1,other_account_routing_address=bankAccountId1)

      When(s"We make the request Create counterparty for an account $ApiEndpoint5")
      val requestPost = (v3_1_0_Request / "banks" / bankId2 / "accounts" / bankAccountId2 / "owner" / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      val counterpartyId = responsePost.body.extract[CounterpartyWithMetadataJson].counterparty_id


      val postJsonCounterparty = SwaggerDefinitionsJSON.postHistoricalTransactionJson.copy(
        from = HistoricalTransactionAccountJsonV310(None,None, Some(counterpartyId)) ,
        to = HistoricalTransactionAccountJsonV310(Some(bankId2), Some(bankAccountId2), None),
        value = AmountOfMoneyJsonV121("EUR","1000")
      )


      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions")<@(user1)
      val response310 = makePostRequest(request310, write(postJsonCounterparty))

      Then("We should get a 201")
      response310.code should equal(201)
      val responseJson = response310.body.extract[PostHistoricalTransactionResponseJson]
      responseJson.value should be(postJsonAccount.value)
      responseJson.description should be(postJsonAccount.description)
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
      val getTransactionbyIdRequest = (v3_1_0_Request / "banks" / bankId1/ "accounts" / bankAccountId1 / CUSTOM_OWNER_VIEW_ID / "transactions" / transactionNewId / "transaction").GET <@ (user1)
      val getTransactionbyIdResponse = makeGetRequest(getTransactionbyIdRequest)

      getTransactionbyIdResponse.code should equal(200)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].id should be(transactionNewId)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].details.`type` should be(postJsonAccount.`type`)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].details.description should be(postJsonAccount.description)
    }

    scenario("We will test saveHistoricalTransaction -- counterparty  --> counterparty", ApiEndpoint2, VersionOfApi) {
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


      val counterpartyPostJsonFrom = SwaggerDefinitionsJSON.postCounterpartyJSON.copy(other_bank_routing_address=bankId1,other_account_routing_address=bankAccountId1)

      When(s"We make the request Create counterparty for an account $ApiEndpoint5")
      val requestPostFrom = (v3_1_0_Request / "banks" / bankId1 / "accounts" / bankAccountId1 / "owner" / "counterparties" ).POST <@ (user1)
      val responsePostFrom = makePostRequest(requestPostFrom, write(counterpartyPostJsonFrom))

      Then("We should get a 201 and check all the fields")
      responsePostFrom.code should equal(201)

      val counterpartyIdFrom = responsePostFrom.body.extract[CounterpartyWithMetadataJson].counterparty_id
      
      

      val counterpartyPostJsonTo = SwaggerDefinitionsJSON.postCounterpartyJSON.copy(other_bank_routing_address=bankId2,other_account_routing_address=bankAccountId2)

      When(s"We make the request Create counterparty for an account $ApiEndpoint5")
      val requestPostTo = (v3_1_0_Request / "banks" / bankId2 / "accounts" / bankAccountId2 / "owner" / "counterparties" ).POST <@ (user1)
      val responsePostTo = makePostRequest(requestPostTo, write(counterpartyPostJsonTo))

      Then("We should get a 201 and check all the fields")
      responsePostTo.code should equal(201)

      val counterpartyIdTo = responsePostTo.body.extract[CounterpartyWithMetadataJson].counterparty_id


      val postJsonCounterparty = SwaggerDefinitionsJSON.postHistoricalTransactionJson.copy(
        from = HistoricalTransactionAccountJsonV310(None,None, Some(counterpartyIdFrom)) ,
        to = HistoricalTransactionAccountJsonV310(None,None, Some(counterpartyIdTo)),
        value = AmountOfMoneyJsonV121("EUR","1000")
      )

      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions")<@(user1)
      val response310 = makePostRequest(request310, write(postJsonCounterparty))

      Then("We should get a 201")
      response310.code should equal(201)
      val responseJson = response310.body.extract[PostHistoricalTransactionResponseJson]
      responseJson.value should be(postJsonAccount.value)
      responseJson.description should be(postJsonAccount.description)
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
      val getTransactionbyIdRequest = (v3_1_0_Request / "banks" / bankId1/ "accounts" / bankAccountId1 / CUSTOM_OWNER_VIEW_ID / "transactions" / transactionNewId / "transaction").GET <@ (user1)
      val getTransactionbyIdResponse = makeGetRequest(getTransactionbyIdRequest)

      getTransactionbyIdResponse.code should equal(200)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].id should be(transactionNewId)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].details.`type` should be(postJsonAccount.`type`)
      getTransactionbyIdResponse.body.extract[TransactionJsonV300].details.description should be(postJsonAccount.description)
    }
    
    scenario(s"We will test saveHistoricalTransaction --counterparty- test error: $InvalidJsonFormat", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")


      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateHistoricalTransaction.toString)

      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJSON.copy(other_bank_routing_address=bankId2,other_account_routing_address=bankAccountId2)

      When(s"We make the request Create counterparty for an account $ApiEndpoint5")
      val requestPost = (v3_1_0_Request / "banks" / bankId1 / "accounts" / bankAccountId1 / "owner" / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      val counterpartyId = responsePost.body.extract[CounterpartyWithMetadataJson].counterparty_id
      val request310 = (v3_1_0_Request / "management" / "historical" / "transactions")<@(user1)
      
      

      val postJsonCounterparty1 = SwaggerDefinitionsJSON.postHistoricalTransactionJson.copy(
        from = HistoricalTransactionAccountJsonV310(None,None, None),
        to = HistoricalTransactionAccountJsonV310(None,None, Some(counterpartyId)),
        value = AmountOfMoneyJsonV121("EUR","1000")
      )

      val responseError1 = makePostRequest(request310, write(postJsonCounterparty1))
      Then("We should get a 400")
      
      responseError1.code should equal(400)
      responseError1.body.toString contains("from object should only contain bank_id and account_id or counterparty_id in the post json body.") should be (true)

      val postJsonCounterparty2 = SwaggerDefinitionsJSON.postHistoricalTransactionJson.copy(
        from = HistoricalTransactionAccountJsonV310(None,None, Some(counterpartyId)),
        to = HistoricalTransactionAccountJsonV310(None,None, None),
        value = AmountOfMoneyJsonV121("EUR","1000")
      )

      val responseError2 = makePostRequest(request310, write(postJsonCounterparty2))
      Then("We should get a 400")

      responseError2.code should equal(400)
      responseError2.body.toString contains("to object should only contain bank_id and account_id or counterparty_id in the post json body.") should be (true)
    }

  }

}

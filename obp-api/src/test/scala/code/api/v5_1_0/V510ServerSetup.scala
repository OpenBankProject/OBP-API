package code.api.v5_1_0

import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJsonV300
import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.api.util.ApiRole
import code.api.util.ApiRole.CanCreateCustomer
import code.api.v1_2_1.{AccountJSON, AccountsJSON, PostTransactionCommentJSON, ViewsJSONV121}
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.{BasicAccountsJSON, TransactionRequestBodyJsonV200}
import code.api.v3_0_0.ViewJsonV300
import code.api.v3_1_0.{CreateAccountRequestJsonV310, CreateAccountResponseJsonV310, CustomerJsonV310}
import code.api.v4_0_0.{AtmJsonV400, BanksJson400, CallLimitPostJsonV400, PostAccountAccessJsonV400, PostViewJsonV400, TransactionRequestWithChargeJSON400}
import code.api.v5_0_0.PostCustomerJsonV500
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, CreateViewJson}
import com.openbankproject.commons.util.ApiShortVersions
import dispatch.Req
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers.randomString

import scala.util.Random
import scala.util.Random.nextInt

trait V510ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"
  def v5_0_0_Request: Req = baseRequest / "obp" / "v5.0.0"
  def v5_1_0_Request: Req = baseRequest / "obp" / "v5.1.0"
  def dynamicEndpoint_Request: Req = baseRequest / "obp" / ApiShortVersions.`dynamic-endpoint`.toString
  def dynamicEntity_Request: Req = baseRequest / "obp" / ApiShortVersions.`dynamic-entity`.toString


  def setRateLimiting(consumerAndToken: Option[(Consumer, Token)], putJson: CallLimitPostJsonV400): APIResponse = {
    val Some((c, _)) = consumerAndToken
    val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
    val request400 = (v4_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@ (consumerAndToken)
    makePutRequest(request400, write(putJson))
  }

  def randomBankId : String = {
    def getBanksInfo : APIResponse  = {
      val request = v5_1_0_Request / "banks"
      makeGetRequest(request)
    }
    val banksJson = getBanksInfo.body.extract[BanksJson400]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }

  def randomPrivateAccount(bankId: String): AccountJSON = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def getPrivateAccountsViaEndpoint(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v5_1_0_Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def randomPrivateAccountViaEndpoint(bankId : String): AccountJSON = {
    val accountsJson = getPrivateAccountsViaEndpoint(bankId, user1).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def createViewViaEndpoint(bankId: String, accountId: String, createViewJson: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): ViewJsonV300 = {
    def postView(bankId: String, accountId: String, view: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumerAndToken)
      makePostRequest(request, write(view))
    }
    val reply = postView(bankId, accountId, createViewJson, consumerAndToken)
    reply.body.extract[ViewJsonV300]
  }
  
  def createAtmAtBank(bankId: String): AtmJsonV400 = {
    val postAtmJson = SwaggerDefinitionsJSON.atmJsonV400.copy(bank_id = bankId)
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
    val requestCreateAtm = (v4_0_0_Request / "banks" / bankId / "atms").POST <@ (user1)
    val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))
    val responseBodyCreateAtm = responseCreateAtm.body.extract[AtmJsonV400]
    responseBodyCreateAtm should be (postAtmJson)
    responseCreateAtm.code should be (201)
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    responseBodyCreateAtm
  }

  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v5_1_0_Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken) //TODO, how can we know which endpoint it called? Although it is V300, but this endpoint called V200-privateAccountsAtOneBank
    makeGetRequest(request)
  }
  def randomPrivateAccountId(bankId : String) : String = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[BasicAccountsJSON].accounts //TODO, how to make this map automatically.
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition).id
  }

  def createCustomerEndpointV510(bankId: String, legalName: String, mobilePhoneNumber: String): CustomerJsonV310 = {
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
    val request = (v5_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
    val response = makePostRequest(request, write(PostCustomerJsonV500(legal_name = legalName,mobile_phone_number = mobilePhoneNumber)))
    Then("We should get a 201")
    response.code should equal(201)
    response.body.extract[CustomerJsonV310]
  }

  def createTransactionRequestViaEndpoint(fromBankId: String,
                                          fromAccountId: String,
                                          fromCurrency: String,
                                          fromViewId: String,
                                          amount: String,
                                          toBankId: String,
                                          toAccountId: String,
                                          consumerAndToken: Option[(Consumer, Token)]): TransactionRequestWithChargeJSON400 = {
    val toAccountJson = TransactionRequestAccountJsonV140(toBankId, toAccountId)
    val bodyValue = AmountOfMoneyJsonV121(fromCurrency, amount)
    val description = "Just test it!"
    val transactionRequestBody = TransactionRequestBodyJsonV200(toAccountJson, bodyValue, description)
    val createTransReqRequest = (v5_1_0_Request / "banks" / fromBankId / "accounts" / fromAccountId /
      fromViewId / "transaction-request-types" / "SANDBOX_TAN" / "transaction-requests").POST <@ (consumerAndToken)

    makePostRequest(createTransReqRequest, write(transactionRequestBody)).body.extract[TransactionRequestWithChargeJSON400]
  }
  def createAccountViaEndpoint(bankId : String, json: CreateAccountRequestJsonV310, consumerAndToken: Option[(Consumer, Token)]) = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
    And("We make a request v4.0.0")
    val request400 = (v5_1_0_Request / "banks" / bankId / "accounts" ).POST <@(consumerAndToken)
    val response400 = makePostRequest(request400, write(json))


    Then("We should get a 201")
    response400.code should equal(201)
    val account = response400.body.extract[CreateAccountResponseJsonV310]
    account.account_id should not be empty
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    account
  }
  def grantUserAccessToViewViaEndpoint(bankId: String,
                                       accountId: String,
                                       userId: String,
                                       consumerAndToken: Option[(Consumer, Token)],
                                       postBody: PostViewJsonV400
                                      ): ViewJsonV300 = {
    val postJson = PostAccountAccessJsonV400(userId, postBody)
    val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "account-access" / "grant").POST <@ (consumerAndToken)
    val response = makePostRequest(request, write(postJson))
    Then("We should get a 201 and check the response body")
    response.code should equal(201)
    response.body.extract[ViewJsonV300]
  }
  def createTransactionRequest(bankId: String, amountOnMoney: String): (String, String, String) = {
    // Create a Bank
    val bank = createBank(bankId)
    val addAccountJson1 = SwaggerDefinitionsJSON.createAccountRequestJsonV310
      .copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"),
        account_routings = List(AccountRoutingJsonV121(Random.nextString(10), Random.nextString(10))))
    val addAccountJson2 = SwaggerDefinitionsJSON.createAccountRequestJsonV310
      .copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"),
        account_routings = List(AccountRoutingJsonV121(Random.nextString(10), Random.nextString(10))))
    // Create from account
    val fromAccount = createAccountViaEndpoint(bank.bankId.value, addAccountJson1, user1)
    // Create to account
    val toAccount = createAccountViaEndpoint(bank.bankId.value, addAccountJson2, user1)
    // Create a custom view
    val customViewJson = createViewJsonV300.copy(name = "_cascade_delete", metadata_view = "_cascade_delete", is_public = false).toCreateViewJson
    val customView = createViewViaEndpoint(bank.bankId.value, fromAccount.account_id, customViewJson, user1)
    // Grant access to the view
    grantUserAccessToViewViaEndpoint(
      bank.bankId.value,
      fromAccount.account_id,
      resourceUser1.userId,
      user1,
      PostViewJsonV400(view_id = customView.id, is_system = false)
    )
    // Create a Transaction Request
    val transactionRequest = createTransactionRequestViaEndpoint(
      fromBankId = bank.bankId.value,
      fromAccountId = fromAccount.account_id,
      fromCurrency = fromAccount.balance.currency,
      fromViewId = customView.id,
      amount = amountOnMoney,
      toBankId = bank.bankId.value,
      toAccountId = toAccount.account_id,
      user1
    )
    val transactionId = transactionRequest.transaction_ids.headOption.getOrElse("")
    (fromAccount.account_id, toAccount.account_id, transactionId)
  }
  
}
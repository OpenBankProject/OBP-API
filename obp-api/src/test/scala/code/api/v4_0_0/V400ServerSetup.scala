package code.api.v4_0_0

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJson
import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.api.util.ApiRole.{CanCreateAccountAttributeAtOneBank, CanCreateCustomer, CanCreateProduct, _}
import code.api.util.{APIUtil, ApiRole}
import code.api.v1_2_1._
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.{BasicAccountsJSON, TransactionRequestBodyJsonV200}
import code.api.v2_1_0.{TransactionRequestWithChargeJSON210, TransactionRequestWithChargeJSONs210}
import code.api.v3_0_0.{CustomerAttributeResponseJsonV300, TransactionJsonV300, TransactionsJsonV300, UserJsonV300, ViewJsonV300}
import code.api.v3_1_0._
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.metadata.comments.MappedComment
import code.metadata.narrative.MappedNarrative
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import code.transactionattribute.MappedTransactionAttribute
import com.openbankproject.commons.model.{AccountId, AccountRoutingJsonV121, AmountOfMoneyJsonV121, BankId, CreateViewJson, UpdateViewJSON}
import dispatch.Req
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.randomString

import scala.util.Random
import scala.util.Random.nextInt

trait V400ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"

  def randomBankId : String = {
    def getBanksInfo : APIResponse  = {
      val request = v4_0_0_Request / "banks"
      makeGetRequest(request)
    }
    val banksJson = getBanksInfo.body.extract[BanksJson400]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }
  def getPrivateAccountsViaEndpoint(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v4_0_0_Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }

  def randomPrivateAccountIdViaEndpoint(bankId : String) : String = {
    val accountsJson = getPrivateAccountsViaEndpoint(bankId, user1).body.extract[BasicAccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition).id
  }

  def randomPrivateAccountViaEndpoint(bankId : String): AccountJSON = {
    val accountsJson = getPrivateAccountsViaEndpoint(bankId, user1).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def randomOwnerViewPermalinkViaEndpoint(bankId: String, account: AccountJSON) : String = {
    val request = v4_0_0_Request / "banks" / bankId / "accounts" / account.id / "views" <@(consumer, token1)
    val reply = makeGetRequest(request)
    val possibleViewsPermalinks = reply.body.extract[ViewsJSONV121].views.filterNot(_.is_public==true).filter(_.id == CUSTOM_OWNER_VIEW_ID)
    val randomPosition = nextInt(possibleViewsPermalinks.size)
    possibleViewsPermalinks(randomPosition).id
  }

  def getTransactionsViaEndpoint(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)], params: List[(String, String)] = Nil): APIResponse = {
    val request = v4_0_0_Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" <@(consumerAndToken)
    makeGetRequest(request, params)
  }

  def randomTransactionViaEndpoint(bankId : String, accountId : String, viewId: String) : TransactionJsonV300 = {
    val transactionsJson = getTransactionsViaEndpoint(bankId, accountId, viewId, user1).body.extract[TransactionsJsonV300].transactions
    val randomPosition = nextInt(transactionsJson.size)
    transactionsJson(randomPosition)
  }
  
  def randomTransactionRequestViaEndpoint(bankId : String, accountId : String, viewId: String, consumerAndToken: Option[(Consumer, Token)]) : TransactionRequestWithChargeJSON210 = {
    val request310 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / viewId / "transaction-requests").GET <@(consumerAndToken)
    val response310 = makeGetRequest(request310)
    response310.code should equal(200)
    val transactionRequests = response310.body.extract[TransactionRequestWithChargeJSONs210].transaction_requests_with_charges
    val randomPosition = nextInt(transactionRequests.size)
    transactionRequests(randomPosition)
  }
  
  def getCurrentUserEndpoint(consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val requestCurrentUserNewStyle = baseRequest / "obp" / "v4.0.0" / "users" / "current"
    makeGetRequest(requestCurrentUserNewStyle.GET <@(consumerAndToken))
  }
  
  def setRateLimiting(consumerAndToken: Option[(Consumer, Token)], putJson: CallLimitPostJsonV400): APIResponse = {
    val Some((c, _)) = consumerAndToken
    val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanSetCallLimits.toString)
    val request400 = (v4_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(consumerAndToken)
    makePutRequest(request400, write(putJson))
  }  
  def setRateLimitingWithoutRole(consumerAndToken: Option[(Consumer, Token)], putJson: CallLimitPostJsonV400): APIResponse = {
    val Some((c, _)) = consumerAndToken
    val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(c.key).map(_.consumerId.get).getOrElse("")
    val request400 = (v4_0_0_Request / "management" / "consumers" / consumerId / "consumer" / "call-limits").PUT <@(consumerAndToken)
    makePutRequest(request400, write(putJson))
  }  
  def setRateLimitingAnonymousAccess(putJson: CallLimitPostJsonV400): APIResponse = {
    val request400 = (v4_0_0_Request / "management" / "consumers" / "some_consumer_id" / "consumer" / "call-limits").PUT
    makePutRequest(request400, write(putJson))
  }
  
  def updateViewViaEndpoint(bankId: String, accountId: String, viewId: String, updateViewJson: UpdateViewJSON, consumerAndToken: Option[(Consumer, Token)]): ViewJsonV300 = {
    def putView(bankId: String, accountId: String, viewId : String, view: UpdateViewJSON, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).PUT <@(consumerAndToken)
      makePutRequest(request, write(view))
    }
    val reply = putView(bankId, accountId, viewId, updateViewJson, consumerAndToken)
    reply.body.extract[ViewJsonV300]
  }
  
  def createViewViaEndpoint(bankId: String, accountId: String, createViewJson: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): ViewJsonV300 = {
    def postView(bankId: String, accountId: String, view: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumerAndToken)
      makePostRequest(request, write(view))
    }
    val reply = postView(bankId, accountId, createViewJson, consumerAndToken)
    reply.body.extract[ViewJsonV300]
  }

  def createProductViaEndpoint(bankId: String, code: String, json: PostPutProductJsonV310): ProductJsonV310 = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateProduct.toString)
    val request310 = (v4_0_0_Request / "banks" / bankId / "products" / code).PUT <@ (user1)
    val response310 = makePutRequest(request310, write(json))
    response310.code should equal(201)
    val product = response310.body.extract[ProductJsonV310]
    product.code shouldBe code
    product.parent_product_code shouldBe json.parent_product_code
    product.bank_id shouldBe bankId
    product.name shouldBe json.name
    product.category shouldBe json.category
    product.super_family shouldBe json.super_family
    product.family shouldBe json.family
    product.more_info_url shouldBe json.more_info_url
    product.details shouldBe json.details
    product.description shouldBe json.description
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    product
  }

  def createAccountAttributeViaEndpoint(bankId: String, accountId: String, name: String, value: String, `type`: String): AccountAttributeResponseJson = {
    val postPutProductJsonV310 = PostPutProductJsonV310(
      name = "product name",
      parent_product_code = "",
      category = "category",
      family = "family",
      super_family = "super family",
      more_info_url = "www.example.com/prod1/more-info.html",
      details = "Details",
      description = "Description",
      meta = SwaggerDefinitionsJSON.metaJson
    )
    val product: ProductJsonV310 =
      createProductViaEndpoint(
        bankId=bankId,
        code=APIUtil.generateUUID(),
        json=postPutProductJsonV310
      )
    val accountAttributeJson = AccountAttributeJson(
      name = name,
      `type` = `type`,
      value = value
    )
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateAccountAttributeAtOneBank.toString)
    val requestCreate310 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId /
      "products" / product.code / "attribute").POST <@(user1)
    val responseCreate310 = makePostRequest(requestCreate310, write(accountAttributeJson))
    Then("We should get a 201")
    responseCreate310.code should equal(201)
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    responseCreate310.body.extract[AccountAttributeResponseJson]
  }
  
  // This will call create customer ,then return the customerId
  def createAndGetCustomerIdViaEndpoint(bankId:String, consumerAndToken: Option[(Consumer, Token)]) = {
    val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
    def createCustomer(consumerAndToken: Option[(Consumer, Token)]) ={
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val request310 = (v4_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 201")
      response310.code should equal(201)
      response310.body.extract[CustomerJsonV310]
    }
    createCustomer(consumerAndToken).customer_id
  }
  
  def createAndGetCustomerAttributeIdViaEndpoint(bankId:String, customerId:String, consumerAndToken: Option[(Consumer, Token)], postCustomerAttributeJson: Option[CustomerAttributeJsonV400] = None) = {
    lazy val postCustomerAttributeJsonV400 = postCustomerAttributeJson.getOrElse(SwaggerDefinitionsJSON.customerAttributeJsonV400)
    val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateCustomerAttributeAtOneBank.toString)
    val responseWithRole = makePostRequest(request400, write(postCustomerAttributeJsonV400))
    responseWithRole.body.extract[CustomerAttributeResponseJsonV300].customer_attribute_id
  }

  def createTransactionAttributeEndpoint(bankId:String, accountId:String, transactionId:String, consumerAndToken: Option[(Consumer, Token)]) = {
    lazy val postTransactionAttributeJsonV400 = SwaggerDefinitionsJSON.transactionAttributeJsonV400
    val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attribute").POST <@ (user1)
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateTransactionAttributeAtOneBank.toString)
    val responseWithRole = makePostRequest(request400, write(postTransactionAttributeJsonV400))
    responseWithRole.code should equal(201)
    responseWithRole.body.extract[TransactionAttributeResponseJson].transaction_attribute_id
  }

  def createTransactionRequestAttributeEndpoint(bankId:String, accountId:String, transactionRequestId:String, consumerAndToken: Option[(Consumer, Token)]) = {
    lazy val postTransactionRequestAttributeJsonV400 = SwaggerDefinitionsJSON.transactionRequestAttributeJsonV400
    val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transaction-requests" / transactionRequestId / "attribute").POST <@ (user1)
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateTransactionRequestAttributeAtOneBank.toString())
    val responseWithRole = makePostRequest(request400, write(postTransactionRequestAttributeJsonV400))
    responseWithRole.code should equal(201)
    responseWithRole.body.extract[TransactionRequestAttributeResponseJson].transaction_request_attribute_id
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
  
  def createWebhookViaEndpoint(bankId: String,
                               accountId: String,
                               userId: String,
                               consumerAndToken: Option[(Consumer, Token)]): AccountWebhookJson = {
    val postJson = SwaggerDefinitionsJSON.accountWebhookPostJson
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, userId, CanCreateWebhook.toString)
    When("We make a request v3.1.0 with a Role " + canCreateWebhook)
    val request310 = (v4_0_0_Request / "banks" / bankId / "account-web-hooks").POST <@(consumerAndToken)
    val response310 = makePostRequest(request310, write(postJson.copy(account_id = accountId)))
    Then("We should get a 201")
    response310.code should equal(201)
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    response310.body.extract[AccountWebhookJson]
  }

  def postCommentForOneTransactionViaEndpoint(bankId : String, accountId : String, viewId : String, transactionId : String, comment: PostTransactionCommentJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments").POST <@(consumerAndToken)
    makePostRequest(request, write(comment))
  }
  def createAccountViaEndpoint(bankId : String, json: CreateAccountRequestJsonV310, consumerAndToken: Option[(Consumer, Token)]) = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
    And("We make a request v4.0.0")
    val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" ).POST <@(consumerAndToken)
    val response400 = makePostRequest(request400, write(json))
    Then("We should get a 201")
    response400.code should equal(201)
    val account = response400.body.extract[CreateAccountResponseJsonV310]
    account.account_id should not be empty
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    account
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
    val createTransReqRequest = (v4_0_0_Request / "banks" / fromBankId / "accounts" / fromAccountId /
      fromViewId / "transaction-request-types" / "SANDBOX_TAN" / "transaction-requests").POST <@ (consumerAndToken)

    makePostRequest(createTransReqRequest, write(transactionRequestBody)).body.extract[TransactionRequestWithChargeJSON400]
  }
  def getTransactionAttributesViaEndpoint(bankId: String,
                                          accountId: String,
                                          transactionId: String,
                                          userId: String,
                                          consumerAndToken: Option[(Consumer, Token)]): TransactionAttributesResponseJson = {
    // We grant the role to the user
    Entitlement.entitlement.vend.addEntitlement(bankId, userId, CanGetTransactionAttributesAtOneBank.toString)
    val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attributes" ).GET <@ (consumerAndToken)
    val response400 = makeGetRequest(request400)
    // We should get a 200
    response400.code should equal(200)
    response400.body.extract[TransactionAttributesResponseJson]
  }

  def checkAllTransactionRelatedData(bankId: String,
                                     accountId: String,
                                     transactionId: String): Boolean = {
    val attributes = MappedTransactionAttribute.findAll(
      By(MappedTransactionAttribute.mBankId, bankId),
      By(MappedTransactionAttribute.mTransactionId, transactionId)
    ).size == 0
    val comments = MappedComment.findAll(
      By(MappedComment.bank, bankId),
      By(MappedComment.account, accountId),
      By(MappedComment.transaction, transactionId)
    ).size == 0
    val narrative = MappedNarrative.findAll(
      By(MappedNarrative.bank, bankId),
      By(MappedNarrative.account, accountId),
      By(MappedNarrative.transaction, transactionId)
    ).size == 0
    val images = MappedTransactionImage.findAll(
      By(MappedTransactionImage.bank, bankId),
      By(MappedTransactionImage.account, accountId),
      By(MappedTransactionImage.transaction, transactionId)
    ).size == 0
    val whereTag = MappedWhereTag.find(
      By(MappedWhereTag.bank, bankId),
      By(MappedWhereTag.account, accountId),
      By(MappedWhereTag.transaction, transactionId)
    ).size == 0
    List(attributes, comments, narrative, images, whereTag).forall(_ == true)
  }
  
  def createTransactionRequestForDeleteCascade(bankId: String) = {
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
    val customViewJson = createViewJson.copy(name = "_cascade_delete", metadata_view = "_cascade_delete", is_public = false)
    val customView = createViewViaEndpoint(bank.bankId.value, toAccount.account_id, customViewJson, user1)
    // Grant access to the view
    grantUserAccessToViewViaEndpoint(
      bank.bankId.value,
      toAccount.account_id,
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
      amount = "10",
      toBankId = bank.bankId.value,
      toAccountId = toAccount.account_id,
      user1
    )
    val transactionId = transactionRequest.transaction_ids.headOption.getOrElse("")
    val transactionAttributeId = createTransactionAttributeEndpoint(bank.bankId.value, fromAccount.account_id, transactionId, user1)
    val comment = postCommentForOneTransactionViaEndpoint(
      bank.bankId.value,
      fromAccount.account_id,
      customView.id,
      transactionId,
      PostTransactionCommentJSON(randomString(5)),
      user1
    )
    (bank.bankId.value, fromAccount.account_id, transactionId)
  }

  def saveHistoricalTransactionViaEndpoint(fromBankId: BankId,
                                          fromAccountId: AccountId,
                                          toBankId: BankId,
                                          toAccountId: AccountId,
                                          amount: BigDecimal,
                                          description: String,
                                          consumerAndToken: Option[(Consumer, Token)]): PostHistoricalTransactionResponseJson = {
    val dateTimeNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
    val historicalTransactionJson = PostHistoricalTransactionJson(
      from = HistoricalTransactionAccountJsonV310(
        bank_id = Some(fromBankId.value),
        account_id = Some(fromAccountId.value),
        counterparty_id = None
      ),
      to = HistoricalTransactionAccountJsonV310(
        bank_id = Some(toBankId.value),
        account_id = Some(toAccountId.value),
        counterparty_id = None
      ),
      value = AmountOfMoneyJsonV121(
        currency = "EUR",
        amount = amount.toString()
      ),
      description = description,
      posted = dateTimeNow,
      completed = dateTimeNow,
      `type` = "SEPA",
      charge_policy = "SHARED"
    )
    val saveHistoricalTransactionRequest = (v4_0_0_Request / "management" / "historical" / "transactions").POST <@ (consumerAndToken)

    makePostRequest(saveHistoricalTransactionRequest, write(historicalTransactionJson)).body.extract[PostHistoricalTransactionResponseJson]
  }
  
}
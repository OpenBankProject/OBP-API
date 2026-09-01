package code.api.v5_1_0
import org.json4s._
import java.util.UUID
import code.api.Constant.{SYSTEM_OWNER_VIEW_ID}
import code.api.util.ErrorMessages._
import code.api.util.APIUtil.OAuth._
import code.api.v2_1_0.{CounterpartyIdJson, TransactionRequestBodyCounterpartyJSON}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class CounterpartyLimitTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createCounterpartyLimit))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.getCounterpartyLimit))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.updateCounterpartyLimit))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_1_0.deleteCounterpartyLimit))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.createTransactionRequestCounterparty))
  object ApiEndpoint6 extends Tag(nameOf(Implementations5_1_0.getCounterpartyLimitStatus))

  
  val bankId = testBankId1.value
  val accountId = testAccountId1.value
  val ownerView = SYSTEM_OWNER_VIEW_ID
  val postCounterpartyLimitTestMonthly  = PostCounterpartyLimitV510(
    currency = "EUR",
    max_single_amount = "10.0", // if I transfer 11 euros, then we trigger this guard.
    max_monthly_amount = "11.0", // if I transfer 10, then transfer 20, --> we trigger this guard.
    max_number_of_monthly_transactions = 2, //if I transfer 10, then transfer 2, then transfer 3 --> we can trigger this guard. 
    max_yearly_amount = "30.0", //
    max_number_of_yearly_transactions = 5,
    max_total_amount = "50.0", //
    max_number_of_transactions = 6
  )
  val putCounterpartyLimitV510 = PostCounterpartyLimitV510(
    currency = "EUR",
    max_single_amount = "1.1",
    max_monthly_amount = "2.1",
    max_number_of_monthly_transactions = 3,
    max_yearly_amount = "4.1",
    max_number_of_yearly_transactions = 5,
    max_total_amount="6.1",
    max_number_of_transactions=7,
  )
  val postCounterpartyLimitTestYearly = PostCounterpartyLimitV510(
    currency = "EUR",
    max_single_amount = "100",
    max_monthly_amount = "200",
    max_number_of_monthly_transactions = 20,
    max_yearly_amount = "11", //if I transfer 11 euros, then we trigger this guard.
    max_number_of_yearly_transactions = 2,//if I transfer 1, then transfer 2, then transfer 3 --> we can trigger this guard. 
    max_total_amount = "50",
    max_number_of_transactions = 20
  )

  val postCounterpartyLimitTestTotal = PostCounterpartyLimitV510(
    currency = "EUR",
    max_single_amount = "100",
    max_monthly_amount = "200",
    max_number_of_monthly_transactions = 20,
    max_yearly_amount = "100",
    max_number_of_yearly_transactions = 20,
    max_total_amount = "11",      //if I transfer 5 euros, then transfer 10  --> we can trigger this guard.
    max_number_of_transactions = 2//if I transfer 1, then transfer 2, then transfer 3 --> we can trigger this guard. 
  )

  feature(s"test $ApiEndpoint1,$ApiEndpoint2, $ApiEndpoint3, $ApiEndpoint4,   Authorized access") {
    
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, ApiEndpoint2,ApiEndpoint3,ApiEndpoint4,VersionOfApi) {
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString); 
      
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").POST
      val response510 = makePostRequest(request510, write(postCounterpartyLimitTestMonthly))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)

      {

        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").PUT
        val response510 = makePutRequest(request510, write(postCounterpartyLimitTestMonthly))
        Then("We should get a 401")
        response510.code should equal(401)
        response510.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)

      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET
        val response510 = makeGetRequest(request510)
        Then("We should get a 401")
        response510.code should equal(401)
        response510.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)

      }
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").DELETE
        val response510 = makeDeleteRequest(request510)
        Then("We should get a 401")
        response510.code should equal(401)
        response510.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)

      }
    }
    
    scenario("We will call the endpoint success case", ApiEndpoint1, ApiEndpoint2,ApiEndpoint3,ApiEndpoint4,VersionOfApi) {
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString);   
      
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCounterpartyLimitTestMonthly))
      Then("We should get a 201")
      response510.code should equal(201)
      response510.body.extract[CounterpartyLimitV510].max_monthly_amount should equal(postCounterpartyLimitTestMonthly.max_monthly_amount)
      response510.body.extract[CounterpartyLimitV510].max_number_of_monthly_transactions should equal(postCounterpartyLimitTestMonthly.max_number_of_monthly_transactions)
      response510.body.extract[CounterpartyLimitV510].max_number_of_yearly_transactions should equal(postCounterpartyLimitTestMonthly.max_number_of_yearly_transactions)
      response510.body.extract[CounterpartyLimitV510].max_single_amount should equal(postCounterpartyLimitTestMonthly.max_single_amount)
      response510.body.extract[CounterpartyLimitV510].max_yearly_amount should equal(postCounterpartyLimitTestMonthly.max_yearly_amount)

      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET<@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[CounterpartyLimitV510].max_monthly_amount should equal(postCounterpartyLimitTestMonthly.max_monthly_amount)
        response510.body.extract[CounterpartyLimitV510].max_number_of_monthly_transactions should equal(postCounterpartyLimitTestMonthly.max_number_of_monthly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_number_of_yearly_transactions should equal(postCounterpartyLimitTestMonthly.max_number_of_yearly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_single_amount should equal(postCounterpartyLimitTestMonthly.max_single_amount)
        response510.body.extract[CounterpartyLimitV510].max_yearly_amount should equal(postCounterpartyLimitTestMonthly.max_yearly_amount)
        
      }
      
      {

        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").PUT<@ (user1)
        val response510 = makePutRequest(request510, write(putCounterpartyLimitV510))
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[CounterpartyLimitV510].max_monthly_amount should equal(putCounterpartyLimitV510.max_monthly_amount)
        response510.body.extract[CounterpartyLimitV510].max_number_of_monthly_transactions should equal(putCounterpartyLimitV510.max_number_of_monthly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_number_of_yearly_transactions should equal(putCounterpartyLimitV510.max_number_of_yearly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_single_amount should equal(putCounterpartyLimitV510.max_single_amount)
        response510.body.extract[CounterpartyLimitV510].max_yearly_amount should equal(putCounterpartyLimitV510.max_yearly_amount)
        response510.body.extract[CounterpartyLimitV510].max_total_amount should equal(putCounterpartyLimitV510.max_total_amount)
        response510.body.extract[CounterpartyLimitV510].max_number_of_transactions should equal(putCounterpartyLimitV510.max_number_of_transactions)
      }
      
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET<@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        response510.body.extract[CounterpartyLimitV510].max_monthly_amount should equal(putCounterpartyLimitV510.max_monthly_amount)
        response510.body.extract[CounterpartyLimitV510].max_number_of_monthly_transactions should equal(putCounterpartyLimitV510.max_number_of_monthly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_number_of_yearly_transactions should equal(putCounterpartyLimitV510.max_number_of_yearly_transactions)
        response510.body.extract[CounterpartyLimitV510].max_single_amount should equal(putCounterpartyLimitV510.max_single_amount)
        response510.body.extract[CounterpartyLimitV510].max_yearly_amount should equal(putCounterpartyLimitV510.max_yearly_amount)
        response510.body.extract[CounterpartyLimitV510].max_total_amount should equal(putCounterpartyLimitV510.max_total_amount)
        response510.body.extract[CounterpartyLimitV510].max_number_of_transactions should equal(putCounterpartyLimitV510.max_number_of_transactions)
      }
      
      {
        val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").DELETE<@ (user1)
        val response510 = makeDeleteRequest(request510)
        Then("We should get a 204")
        response510.code should equal(204)
      }
    }
    
    scenario("We will call the endpoint wrong bankId case", ApiEndpoint1, ApiEndpoint2,ApiEndpoint3,ApiEndpoint4,VersionOfApi) {
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString);   
      
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / "wrongId" / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCounterpartyLimitTestMonthly))
      Then("We should get a 404")
      response510.code should equal(404)
      response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)

      {
        val request510 = (v5_1_0_Request / "banks" / "wrongId" / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET<@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 404")
        response510.code should equal(404)
        response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)
        
      }
      
      {

        val request510 = (v5_1_0_Request / "banks" / "wrongId" / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").PUT<@ (user1)
        val response510 = makePutRequest(request510, write(putCounterpartyLimitV510))
        Then("We should get a 404")
        response510.code should equal(404)
        response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)
      }
      
      {
        val request510 = (v5_1_0_Request / "banks" / "wrongId" / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").GET<@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 404")
        response510.code should equal(404)
        response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)
      }
      
      {
        val request510 = (v5_1_0_Request / "banks" / "wrongId"  / "accounts" / accountId /"views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").DELETE<@ (user1)
        val response510 = makeDeleteRequest(request510)
        Then("We should get a 404")
        response510.code should equal(404)
        response510.body.extract[ErrorMessage].message contains(BankNotFound) shouldBe (true)
      }
    }

    scenario("We will create consent properly, and test the counterparty limit - monthly guard", ApiEndpoint1, ApiEndpoint4, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString);

      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCounterpartyLimitTestMonthly))
      Then("We should get a 201")
      response510.code should equal(201)

      Then("We can test the COUNTERPARTY payment limit")

      val createTransReqRequest = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId /
        ownerView / "transaction-request-types" / "COUNTERPARTY" / "transaction-requests").POST  <@ (user1)
      
      val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
        to = CounterpartyIdJson(counterparty.counterpartyId),
        value = AmountOfMoneyJsonV121("EUR","11"),
        description ="testing the limit",
        charge_policy = "SHARED",
        future_date = None,
        None,
      )
    
      val response = makePostRequest(createTransReqRequest, write(transactionRequestBodyCounterparty))
      
      response.code shouldBe(400)
      response.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response.body.extract[ErrorMessage].message contains("max_single_amount") shouldBe(true)

      //("we try the max_monthly_amount limit (11 euros) . now we transfer 9 euro first. then 9 euros, we will get the error")
      val response1 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","3")))
      )
      response1.code shouldBe(201)
      val response2 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","9")))
      )

      response2.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response2.body.extract[ErrorMessage].message contains("max_monthly_amount") shouldBe(true)

      //("we try the max_number_of_monthly_transactions limit (2 times), we try the 3rd request, we will get the error. response2 failed, so does not count in database.")
      val response3 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2")))
      )
      response3.code shouldBe(201)

      val response4 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2")))
      )
      response4.code shouldBe(400)

      response4.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response4.body.extract[ErrorMessage].message contains("max_number_of_monthly_transactions") shouldBe(true)

    }

    scenario("We will create consent properly, and test the counterparty limit - yearly guard", ApiEndpoint1, ApiEndpoint4, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString);

      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCounterpartyLimitTestYearly))
      Then("We should get a 201")
      response510.code should equal(201)

      Then("We can test the COUNTERPARTY payment limit")

      val createTransReqRequest = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId /
        ownerView / "transaction-request-types" / "COUNTERPARTY" / "transaction-requests").POST  <@ (user1)

      val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
        to = CounterpartyIdJson(counterparty.counterpartyId),
        value = AmountOfMoneyJsonV121("EUR","11"),
        description ="testing the limit",
        charge_policy = "SHARED",
        future_date = None,
        None,
      )

      val response1 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","3")))
      )
      response1.code shouldBe(201)
      val response2 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","9")))
      )
      response2.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response2.body.extract[ErrorMessage].message contains("max_yearly_amount") shouldBe(true)

      //("we try the max_number_of_monthly_transactions limit (2 times), we try the 3rd request, we will get the error. response2 failed, so does not count in database.")
      val response3 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2")))
      )
      response3.code shouldBe(201)

      val response4 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2")))
      )
      response4.code shouldBe(400)

      response4.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response4.body.extract[ErrorMessage].message contains("max_number_of_yearly_transactions") shouldBe(true)

    }

    scenario("We will create consent properly, and test the counterparty limit - total guard", ApiEndpoint1, ApiEndpoint4, VersionOfApi) {
      When(s"We try $ApiEndpoint1 v5.1.0")
      val counterparty = createCounterparty(bankId, accountId, accountId, true, UUID.randomUUID.toString);

      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limits").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postCounterpartyLimitTestTotal))
      Then("We should get a 201")
      response510.code should equal(201)

      Then("We can test the COUNTERPARTY payment limit")

      val createTransReqRequest = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId /
        ownerView / "transaction-request-types" / "COUNTERPARTY" / "transaction-requests").POST  <@ (user1)

      val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
        to = CounterpartyIdJson(counterparty.counterpartyId),
        value = AmountOfMoneyJsonV121("EUR","11"),
        description ="testing the limit",
        charge_policy = "SHARED",
        future_date = None,
        None,
      )

      //("we try the max_monthly_amount limit (11 euros) . now we transfer 9 euro first. then 9 euros, we will get the error")
      val response1 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","3")))
      )
      response1.code shouldBe(201)
      val response2 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","9")))
      )

      response2.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response2.body.extract[ErrorMessage].message contains("max_total_amount") shouldBe(true)

      //("we try the max_number_of_monthly_transactions limit (2 times), we try the 3rd request, we will get the error. response2 failed, so does not count in database.")
      val response3 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2")))
      )
      response3.code shouldBe(201)

      val response4 = makePostRequest(
        createTransReqRequest,
        write(transactionRequestBodyCounterparty.copy(value=AmountOfMoneyJsonV121("EUR","2")))
      )
      response4.code shouldBe(400)

      response4.body.extract[ErrorMessage].message contains(CounterpartyLimitValidationError) shouldBe (true)
      response4.body.extract[ErrorMessage].message contains("max_number_of_transactions") shouldBe(true)

      val requestLimitStatus = (v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views" / ownerView /"counterparties" / counterparty.counterpartyId /"limit-status").GET <@ (user1)
      val responseLimitStatus = makeGetRequest(requestLimitStatus)
      responseLimitStatus.code shouldBe (200)
      responseLimitStatus.body.extract[CounterpartyLimitStatusV510].status.currency_status shouldBe("EUR")
      responseLimitStatus.body.extract[CounterpartyLimitStatusV510].status.max_number_of_monthly_transactions_status shouldBe(2)
      responseLimitStatus.body.extract[CounterpartyLimitStatusV510].status.max_number_of_yearly_transactions_status shouldBe(2)
      responseLimitStatus.body.extract[CounterpartyLimitStatusV510].status.max_number_of_transactions_status shouldBe(2)

    }

  }
}

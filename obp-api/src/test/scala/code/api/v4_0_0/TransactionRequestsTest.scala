package code.api.v4_0_0

import java.util.UUID

import code.api.ChargePolicy
import code.api.Constant._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateAnyTransactionRequest
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ErrorMessages}
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeAnswerJSON, TransactionRequestAccountJsonV140}
import code.api.v2_0_0.TransactionRequestBodyJsonV200
import code.api.v2_1_0._
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.bankconnectors.Connector
import code.fx.fx
import code.model.BankAccountX
import code.setup.{APIResponse, DefaultUsers}
import com.openbankproject.commons.model.enums.TransactionRequestStatus
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class TransactionRequestsTest extends V400ServerSetup with DefaultUsers {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createTransactionRequest))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.answerTransactionRequestChallenge))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getTransactionRequest))
 

  def transactionCount(accounts: BankAccount*): Int = {
    accounts.foldLeft(0)((accumulator, account) => {
      accumulator + Connector.connector.vend.getTransactionsLegacy(account.bankId, account.accountId, None).map(_._1).openOrThrowException(attemptedToOpenAnEmptyBox).size
    })
  }

  def defaultSetup(transactionRequestTypeInput : String= ACCOUNT.toString) = new DefaultSetup(transactionRequestTypeInput)
  
  class DefaultSetup(transactionRequestTypeInput : String= ACCOUNT.toString) {

      val sharedChargePolicy = ChargePolicy.withName("SHARED").toString
      var transactionRequestType: String = transactionRequestTypeInput
      val testBank = createBank("__transactions-test-bank2")
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1__")
      val accountId2 = AccountId("__acc2__")

      var amt = BigDecimal("12.50")
      var fromCurrency = "AED"
      var toCurrency = "AED"

      def setCurrencyAndAmt(fromCurrency: String, toCurrency: String, amt: String) = {
        this.fromCurrency = fromCurrency
        this.toCurrency = toCurrency
        this.amt = BigDecimal(amt)
        updateAccountCurrency(bankId, accountId2, toCurrency)
      }

      createAccountRelevantResource(Some(resourceUser1), bankId, accountId1, fromCurrency)
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId2, toCurrency)

      def getFromAccount: BankAccount = {
        BankAccountX(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount: BankAccount = {
        BankAccountX(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      var totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      var beforeFromBalance = fromAccount.balance
      var beforeToBalance = toAccount.balance

      //we expected transfer amount
      val zero: BigDecimal = BigDecimal(0)
      var expectedAmtTo = fx.exchangeRate(fromCurrency, toCurrency, Some(fromAccount.bankId.value)) match {
        case Some(exchangeRate) => amt * exchangeRate
        case _ => amt * BigDecimal("0")
      }
      // We debit the From
      var expectedFromNewBalance = beforeFromBalance - amt
      // We credit the To
      var expectedToNewBalance = beforeToBalance + expectedAmtTo

      var transactionRequestId = TransactionRequestId("__trans1")
      var toAccountJson = TransactionRequestAccountJsonV140(toAccount.bankId.value, toAccount.accountId.value)

      var bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
      val description = "Just test it!"
      var transactionRequestBody = TransactionRequestBodyJsonV200(toAccountJson, bodyValue, description)

      // prepare for Answer Transaction Request Challenge endpoint
      var challengeId = ""
      var transRequestId = ""
      var answerJson = ChallengeAnswerJSON(id = challengeId, answer = "123")

      //prepare for counterparty and SEPA stuff
      //For SEPA, otherAccountRoutingScheme must be 'IBAN'
      val counterpartySEPA = createCounterparty(bankId.value, accountId2.value, true, UUID.randomUUID.toString);
      //For Counterpart local mapper, the  mOtherAccountRoutingScheme='OBP' and  mOtherBankRoutingScheme = 'OBP'
      val counterpartyCounterparty = createCounterparty(bankId.value, accountId2.value, true, UUID.randomUUID.toString);

      var transactionRequestBodySEPA = TransactionRequestBodySEPAJSON(bodyValue, IbanJson(counterpartySEPA.otherAccountSecondaryRoutingAddress), description, sharedChargePolicy)

      var transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(CounterpartyIdJson(counterpartyCounterparty.counterpartyId), bodyValue, description, sharedChargePolicy)

      def setAnswerTransactionRequest(challengeId: String = this.challengeId, transRequestId: String = this.transRequestId, consumerAndToken: Option[(Consumer, Token)] = user1) = {
        this.challengeId = challengeId
        this.transRequestId = transRequestId
        answerJson = ChallengeAnswerJSON(id = challengeId, answer = "123")
        val answerRequestNew = (v4_0_0_Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          CUSTOM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@ (consumerAndToken)
        answerRequest = answerRequestNew
      }

      def setCreateTransactionRequestType(transactionRequestType: String) = {
        this.transactionRequestType = transactionRequestType
        val createTransReqRequestNew = (v4_0_0_Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          CUSTOM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)
        createTransReqRequest = createTransReqRequestNew
      }

      /**
        * Create Transaction Request. -- V400
        */
      var createTransReqRequest = (v4_0_0_Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
        CUSTOM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)

      def makeCreateTransReqRequest: APIResponse = makePostRequest(createTransReqRequest, write(transactionRequestBody))
      def makeCreateTransReqRequestSEPA: APIResponse = makePostRequest(createTransReqRequest, write(transactionRequestBodySEPA))
      def makeCreateTransReqRequestCounterparty: APIResponse = makePostRequest(createTransReqRequest, write(transactionRequestBodyCounterparty))

      def checkAllCreateTransReqResBodyField(createTransactionRequestResponse: APIResponse, withChallenge: Boolean): Unit = {
        Then("we should get a 201 created code")
        (createTransactionRequestResponse.code) should equal(201)

        Then("We should have a new transaction id in response body")
        transRequestId = (createTransactionRequestResponse.body \ "id").values.toString
        transRequestId should not equal ("")

        if (withChallenge) {
          Then("We should have the INITIATED status in response body")
          (createTransactionRequestResponse.body \ "status").values.toString should equal(TransactionRequestStatus.INITIATED.toString)
          Then("The transaction_ids filed should be empty")
          (createTransactionRequestResponse.body \ "transaction_ids").values.toString should equal("List()")
          Then("Challenge should have body, this is the with challenge scenario")
          (createTransactionRequestResponse.body \ "challenges").children.size should not equal (0)
          challengeId = (createTransactionRequestResponse.body \ "challenges" \ "id").values.toString
          challengeId should not equal ("")
        } else {
          Then("We should have the COMPLETED status in response body")
          (createTransactionRequestResponse.body \ "status").values.toString should equal(TransactionRequestStatus.COMPLETED.toString)
          Then("The transaction_ids filed should be not empty")
          (createTransactionRequestResponse.body \ "transaction_ids").values.toString should not equal ("List()")
          Then("Challenge should be null, this is the no challenge scenario")
          (createTransactionRequestResponse.body \ "challenge").children.size should equal(0)
        }

        Then("We should have a new TransactionIds value")
        (createTransactionRequestResponse.body \ "transaction_ids").values.toString should not equal ("")

      }

      /**
        * Get all Transaction Requests. - V400
        */
      var getTransReqRequest = (v4_0_0_Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
        CUSTOM_OWNER_VIEW_ID / "transaction-requests").GET <@ (user1)

      def makeGetTransReqRequest = makeGetRequest(getTransReqRequest)

      def checkAllGetTransReqResBodyField(getTransactionRequestResponse: APIResponse, withChellenge: Boolean): Unit = {
        Then("we should get a 200 created code")
        (getTransactionRequestResponse.code) should equal(200)

        And("We should have a new transaction id in response body")
        (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "id").values.toString should not equal ("")

        if (withChellenge) {
          And("We should have the INITIATED status in response body")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "status").values.toString should equal(TransactionRequestStatus.INITIATED.toString)

          And("Challenge should be not null, this is the no challenge scenario")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "challenge").children.size should not equal (0)

          And("We should have be null value for TransactionIds")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "transaction_ids").values.toString should equal("List()")
        } else {
          And("We should have the COMPLETED status in response body")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "status").values.toString should equal(TransactionRequestStatus.COMPLETED.toString)

          And("Challenge should be null, this is the no challenge scenario")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "challenge").children.size should equal(0)

          And("We should have a new TransactionIds value")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "transaction_ids").values.toString should not equal ("")
        }

      }

      /**
        * Get Transactions for Account (Full) -- V400
        */
      var getTransactionRequest = (v4_0_0_Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value / CUSTOM_OWNER_VIEW_ID / "transactions").GET <@ (user1)

      def makeGetTransRequest = makeGetRequest(getTransactionRequest)

      def checkAllGetTransResBodyField(getTransactionResponse: APIResponse, withChellenge: Boolean): Unit = {
        Then("we should get a 200 created code")
        (getTransactionResponse.code) should equal(200)
        And("we should get the body size is one")
        (getTransactionResponse.body.children.size) should equal(1)
        if (withChellenge) {
          And("we should get None, there is no transaction yet")
          ((getTransactionResponse.body \ "transactions"\"details").toString contains (description)) should not equal(true)
        } else {
          And("we should get the body description value is as we set before")
          ((getTransactionResponse.body \ "transactions"\"details").toString contains (description)) should equal(true)
        }
      }

      /**
        * check the balance, after the transactions.
        *
        * @param finishedTransaction : finished the transaction or not ? If finished it is true, if it is not it is false.
        */
      def checkBankAccountBalance(finishedTransaction: Boolean): Unit = {
        val toAccount = getToAccount
        val fromAccount = getFromAccount
        val rate = fx.exchangeRate(fromAccount.currency, toAccount.currency, Some(fromAccount.bankId.value))
        val convertedAmount = fx.convert(amt, rate)
        val fromAccountBalance = fromAccount.balance
        val toAccountBalance = toAccount.balance


        if (finishedTransaction ) {
          if(transactionRequestTypeInput.equals(FREE_FORM.toString)){
            Then("FREE_FORM just transfer money to itself, the money should be the same as before ")
            fromAccountBalance should equal((beforeFromBalance))
            And("there should now be 2 new transactions in the database")
            transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore+2)
          } else {
            Then("check that the balances have been properly decreased/increased (since we handle that logic for sandbox accounts at least) ")
            fromAccountBalance should equal((beforeFromBalance - amt))
            And("the account receiving the payment should have a new balance plus the amount paid")
            toAccountBalance should equal(beforeToBalance + convertedAmount)
            And("there should now be 2 new transactions in the database (one for the sender, one for the receiver")
            transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore + 2)
          }

        } else {
          Then("No transaction, it should be the same as before ")
          fromAccountBalance should equal((beforeFromBalance))
          And("No transaction, it should be the same as before ")
          toAccountBalance should equal(beforeToBalance)
          And("No transaction, it should be the same as before ")
          transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore)
        }
      }

      /**
        * Answer Transaction Request Challenge - V400
        */

      var answerRequest = (v4_0_0_Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
        CUSTOM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@ (user1)

      def makeAnswerRequest = makePostRequest(answerRequest, write(answerJson))

      def checkAllAnsTransReqBodyFields(ansTransReqResponse: APIResponse, withChallenge: Boolean): Unit = {
        Then("we should get a 202 created code")
        (ansTransReqResponse.code) should equal(202)

        And("we should get the body sie is 10, the response Json body have 10 Attributes")
        (ansTransReqResponse.body.children.size) should equal(10)

        Then("We should have a new TransactionIds value")
        (ansTransReqResponse.body \ "transaction_ids").values.toString should not equal ("")

        Then("We should have the COMPLETED status in response body")
        (ansTransReqResponse.body \ "status").values.toString should equal(TransactionRequestStatus.COMPLETED.toString)
      }
    }

  feature("Security Tests: permissions, roles, views...") {


    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No login user", ApiEndpoint1) {}
    } else {
      scenario("No login user", ApiEndpoint1) {

        val helper = defaultSetup()

        Then("We call the 'Create Transaction Request.' without the login user")
        var request = (v4_0_0_Request / "banks" / helper.fromAccount.bankId.value / "accounts" / helper.fromAccount.accountId.value /
          CUSTOM_OWNER_VIEW_ID / "transaction-request-types" / helper.transactionRequestType / "transaction-requests").POST
        var response = makePostRequest(request, write(helper.transactionRequestBody))


        Then("we should get a 401 created code")
        response.code should equal(401)

        Then("We should have the error message")
        response.body.extract[ErrorMessage].message should startWith(ErrorMessages.UserNotLoggedIn)

      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No owner view , No CanCreateAnyTransactionRequest role", ApiEndpoint1) {}
    } else {
      scenario("No owner view, No CanCreateAnyTransactionRequest role", ApiEndpoint1) {

        val helper = defaultSetup()

        Then("We used the login user2, but it does not have the owner view and CreateTransactionRequest role ")
        val request = (v4_0_0_Request / "banks" / helper.testBank.bankId.value / "accounts" / helper.fromAccount.accountId.value /
          CUSTOM_OWNER_VIEW_ID / "transaction-request-types" / helper.transactionRequestType / "transaction-requests").POST <@ (user2)
        val response = makePostRequest(request, write(helper.transactionRequestBody))

        Then("we should get a 400 created code")
        response.code should equal(400)

        Then("We should have the error: " + ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
        val error: String = (response.body \ "message").values.toString
        error should equal(ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No owner view, With CanCreateAnyTransactionRequest role", ApiEndpoint1) {}
    } else {
      scenario("No owner view, With CanCreateAnyTransactionRequest role", ApiEndpoint1) {

        val helper = defaultSetup()

        Then("We grant the CanCreateAnyTransactionRequest role to user3")
        addEntitlement(helper.bankId.value, resourceUser3.userId, CanCreateAnyTransactionRequest.toString)

        Then("We used the login user3, it does not have the owner view ,but has the  CreateTransactionRequest role ")
        var request = (v4_0_0_Request / "banks" / helper.testBank.bankId.value / "accounts" / helper.fromAccount.accountId.value /
          CUSTOM_OWNER_VIEW_ID / "transaction-request-types" / helper.transactionRequestType / "transaction-requests").POST <@ (user3)
        var response = makePostRequest(request, write(helper.transactionRequestBody))

        Then("we should get a 201 created code")
        response.code should equal(201)

      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("Invalid transactionRequestType", ApiEndpoint1) {}
    } else {
      scenario("Invalid transactionRequestType", ApiEndpoint1) {

        val helper = defaultSetup()

        Then("We grant the CanCreateAnyTransactionRequest role to user3")
        addEntitlement(helper.bankId.value, resourceUser3.userId, CanCreateAnyTransactionRequest.toString)

        Then("We call createTransactionRequest with invalid transactionRequestType - V400")
        val invalidTransactionRequestType = "invalidTransactionRequestType"
        var request = (v4_0_0_Request / "banks" / helper.fromAccount.bankId.value / "accounts" / helper.fromAccount.accountId.value /
          CUSTOM_OWNER_VIEW_ID / "transaction-request-types" / invalidTransactionRequestType / "transaction-requests").POST <@ (user3)
        var response = makePostRequest(request, write(helper.transactionRequestBody))

        Then("we should get a 400 created code")
        response.code should equal(400)
        
        response.body.extract[ErrorMessage].message should startWith(ErrorMessages.InvalidTransactionRequestType)
        
      }
    }

  }

  feature("we can create transaction requests -- ACCOUNT") {

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No challenge, No FX (same currencies)", ApiEndpoint1) {}
    } else {
      scenario("No challenge, No FX (same currencies)", ApiEndpoint1) {

        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup()

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequest

        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amout info")
        helper.checkBankAccountBalance(true)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No challenge, With FX ", ApiEndpoint1) {}
    } else {
      scenario("No challenge, With FX ", ApiEndpoint1) {

        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup()

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "10"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)
        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBody = helper.transactionRequestBody.copy(value= helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequest

        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amount info")
        helper.checkBankAccountBalance(true)

      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("With challenge, No FX", ApiEndpoint1, ApiEndpoint2) {}
    } else {
      scenario("With challenge, No FX ", ApiEndpoint1, ApiEndpoint2) {
        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup()
        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "AED"
        val amt = "50000.00"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)
        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBody = helper.transactionRequestBody.copy(value= helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequest
        And("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        Then("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest
        And("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        Then("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        And("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, true)

        Then("we need check the account amount info")
        helper.checkBankAccountBalance(false)

        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest()
        And("we call the endpoint")
        val ansReqResponse = helper.makeAnswerRequest
        And("We check the all the fields of getAnsReqResponse body ")
        helper.checkAllAnsTransReqBodyFields(ansReqResponse, true)

        Then("we need check the account amount info")
        helper.checkBankAccountBalance(true)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("With challenge, With FX ", ApiEndpoint1, ApiEndpoint2) {}
    } else {
      scenario("With challenge, With FX ", ApiEndpoint1, ApiEndpoint2) {
        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup()

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "50000.00"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)

        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBody = helper.transactionRequestBody.copy(value= helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequest

        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, true)

        When("We checked all the data in database, we need check the account amount info")
        helper.checkBankAccountBalance(false)
  
        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest()
        And("we call the endpoint")
        val ansReqResponse = helper.makeAnswerRequest
        And("We check the all the fields of getAnsReqResponse body ")
        helper.checkAllAnsTransReqBodyFields(ansReqResponse, true)
  
        Then("we need check the account amount info")
        helper.checkBankAccountBalance(true)
      }
    }
  }

  feature("we can create transaction requests -- FREE_FORM") {

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No challenge, No FX ", ApiEndpoint1) {}
    } else {
      scenario("No challenge, No FX ", ApiEndpoint1) {

        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(FREE_FORM.toString)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequest

        Then("We checked all the fields of createTransact dionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amout info")
        helper.checkBankAccountBalance(true)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No challenge, With FX ", ApiEndpoint1) {}
    } else {
      scenario("No challenge, With FX ", ApiEndpoint1) {

        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(FREE_FORM.toString)

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "10"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)
        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBody = helper.transactionRequestBody.copy(value= helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequest

        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amout info")
        helper.checkBankAccountBalance(true)

      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("With challenge, No FX", ApiEndpoint1, ApiEndpoint2) {}
    } else {
      scenario("With challenge, No FX ", ApiEndpoint1, ApiEndpoint2) {
        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(FREE_FORM.toString)
        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "AED"
        val amt = "50000.00"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)
        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBody = helper.transactionRequestBody.copy(value= helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequest
        And("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        Then("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest
        And("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        Then("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        And("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, true)

        Then("we need check the account amount info")
        helper.checkBankAccountBalance(false)

        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest()
        And("we call the endpoint")
        val ansReqResponse = helper.makeAnswerRequest
        And("We check the all the fields of getAnsReqResponse body ")
        helper.checkAllAnsTransReqBodyFields(ansReqResponse, true)

        Then("we need check the account amount info")
        helper.checkBankAccountBalance(true)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("With challenge, With FX ", ApiEndpoint1, ApiEndpoint2) {}
    } else {
      scenario("With challenge, With FX ", ApiEndpoint1, ApiEndpoint2) {
        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(FREE_FORM.toString)

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "50000.00"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)

        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBody = helper.transactionRequestBody.copy(value= helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequest

        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, true)

        When("We checked all the data in database, we need check the account amount info")
        helper.checkBankAccountBalance(false)
  
        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest()
        And("we call the endpoint")
        val ansReqResponse = helper.makeAnswerRequest
        And("We check the all the fields of getAnsReqResponse body ")
        helper.checkAllAnsTransReqBodyFields(ansReqResponse, true)
  
        Then("we need check the account amount info")
        helper.checkBankAccountBalance(true)
      }
    }
  }

  feature("we can create transaction requests -- SEPA") {

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No challenge, No FX ", ApiEndpoint1) {}
    } else {
      scenario("No challenge, No FX ", ApiEndpoint1) {

        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(SEPA.toString)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequestSEPA

        Then("We checked all the fields of createTransact dionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amout info")
        helper.checkBankAccountBalance(true)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No challenge, With FX ", ApiEndpoint1) {}
    } else {
      scenario("No challenge, With FX ", ApiEndpoint1) {

        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(SEPA.toString)

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "10"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)
        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBodySEPA = helper.transactionRequestBodySEPA.copy(helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequestSEPA

        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amout info")
        helper.checkBankAccountBalance(true)

      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("With challenge, No FX ", ApiEndpoint1, ApiEndpoint2) {}
    } else {
      scenario("With challenge, No FX ", ApiEndpoint1, ApiEndpoint2) {
        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(SEPA.toString)
        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "AED"
        val amt = "50000.00"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)
        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBodySEPA = helper.transactionRequestBodySEPA.copy(helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequestSEPA
        And("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        Then("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest
        And("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        Then("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        And("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, true)

        Then("we need check the account amount info")
        helper.checkBankAccountBalance(false)

        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest()
        And("we call the endpoint")
        val ansReqResponse = helper.makeAnswerRequest
        And("We check the all the fields of getAnsReqResponse body ")
        helper.checkAllAnsTransReqBodyFields(ansReqResponse, true)

        Then("we need check the account amount info")
        helper.checkBankAccountBalance(true)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("With challenge, With FX ", ApiEndpoint1) {}
    } else {
      scenario("With challenge, With FX ", ApiEndpoint1) {
        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(SEPA.toString)

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "50000.00"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)

        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBodySEPA = helper.transactionRequestBodySEPA.copy(helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequestSEPA

        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, true)

        When("We checked all the data in database, we need check the account amount info")
        helper.checkBankAccountBalance(false)
  
        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest()
        And("we call the endpoint")
        val ansReqResponse = helper.makeAnswerRequest
        And("We check the all the fields of getAnsReqResponse body ")
        helper.checkAllAnsTransReqBodyFields(ansReqResponse, true)
  
        Then("we need check the account amount info")
        helper.checkBankAccountBalance(true)
      }
    }
  }

  feature("we can create transaction requests -- COUNTERPARTY") {

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No challenge, No FX ", ApiEndpoint1) {}
    } else {
      scenario("No challenge, No FX ", ApiEndpoint1) {

        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(COUNTERPARTY.toString)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequestCounterparty

        Then("We checked all the fields of createTransact dionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amount info")
        helper.checkBankAccountBalance(true)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("No challenge, With FX ", ApiEndpoint1) {}
    } else {
      scenario("No challenge, With FX ", ApiEndpoint1) {

        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(COUNTERPARTY.toString)

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "10"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)
        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBodyCounterparty = helper.transactionRequestBodyCounterparty.copy(value=helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequestCounterparty

        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amout info")
        helper.checkBankAccountBalance(true)

      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("With challenge, No FX ", ApiEndpoint1) {}
    } else {
      scenario("With challenge, No FX ", ApiEndpoint1) {
        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(COUNTERPARTY.toString)
        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "AED"
        val amt = "50000.00"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)
        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBodyCounterparty = helper.transactionRequestBodyCounterparty.copy(value=helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequestCounterparty
        And("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        Then("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest
        And("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        Then("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        And("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, true)

        Then("we need check the account amount info")
        helper.checkBankAccountBalance(false)

        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest()
        And("we call the endpoint")
        val ansReqResponse = helper.makeAnswerRequest
        And("We check the all the fields of getAnsReqResponse body ")
        helper.checkAllAnsTransReqBodyFields(ansReqResponse, true)

        Then("we need check the account amount info")
        helper.checkBankAccountBalance(true)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("With challenge, With FX", ApiEndpoint1) {}
    } else {
      scenario("With challenge, With FX", ApiEndpoint1) {
        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(COUNTERPARTY.toString)

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "50000.00"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)

        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBodyCounterparty = helper.transactionRequestBodyCounterparty.copy(value=helper.bodyValue)

        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequestCounterparty

        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, true)

        When("We checked all the data in database, we need check the account amount info")
        helper.checkBankAccountBalance(false)
  
        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest()
        And("we call the endpoint")
        val ansReqResponse = helper.makeAnswerRequest
        And("We check the all the fields of getAnsReqResponse body ")
        helper.checkAllAnsTransReqBodyFields(ansReqResponse, true)
  
        Then("we need check the account amount info")
        helper.checkBankAccountBalance(true)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("With N challenges, With FX", ApiEndpoint1) {}
    } else {
      scenario("With N challenges, With FX", ApiEndpoint1) {
        When("we prepare all the conditions for a normal success -- V400 Create Transaction Request")
        val helper = defaultSetup(COUNTERPARTY.toString)

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "50000.00"
        helper.setCurrencyAndAmt(fromCurrency, toCurrency, amt)

        And("We set the special input JSON values for 'V400 Create Transaction Request' endpoint")
        helper.bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt.toString())
        helper.transactionRequestBodyCounterparty = helper.transactionRequestBodyCounterparty.copy(value=helper.bodyValue)

        createAccountAttributeViaEndpoint(
          helper.bankId.value, 
          helper.accountId1.value, 
          "REQUIRED_CHALLENGE_ANSWERS", 
          "2", 
          "INTEGER"
        )

        val grantedView = grantUserAccessToViewViaEndpoint(
          helper.bankId.value,
          helper.accountId1.value,
          resourceUser2.userId,
          user1,
          PostViewJsonV400(view_id = "owner", is_system = true)
        )
        
        Then("we call the 'V400 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = helper.makeCreateTransReqRequestCounterparty
        val createTransactionRequestJsonResponse = createTransactionRequestResponse.body.extract[TransactionRequestWithChargeJSON400]
        createTransactionRequestJsonResponse.status should equal(TransactionRequestStatus.INITIATED.toString)
        
        val challengeOfUser1: Option[ChallengeJsonV400] = createTransactionRequestJsonResponse.challenges.find(_.user_id == resourceUser1.userId)
        val challengeOfUser2: Option[ChallengeJsonV400] = createTransactionRequestJsonResponse.challenges.find(_.user_id == resourceUser2.userId)
        
        Then("We checked all the fields of createTransactionRequestResponse body ")
        helper.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        When("we need check the 'Get all Transaction Requests. - V400' to double check it in database")
        val getTransReqResponse = helper.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        helper.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        When("we need to check the 'Get Transactions for Account (Full) -V400' to check the transaction info ")
        val getTransResponse = helper.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        helper.checkAllGetTransResBodyField(getTransResponse, true)

        When("We checked all the data in database, we need check the account amount info")
        helper.checkBankAccountBalance(false)

        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest(challengeId = challengeOfUser1.map(_.id).getOrElse(""))
        And("we call the endpoint")
        val ansReqResponseUser1 = helper.makeAnswerRequest
        ansReqResponseUser1.body.extract[ErrorMessage].message should equal(NextChallengePending)
        
        Then("We call 'Answer Transaction Request Challenge - V400' to finish the request")
        And("we prepare the parameters for it")
        helper.setAnswerTransactionRequest(
          challengeId = challengeOfUser2.map(_.id).getOrElse(""),
          consumerAndToken = user2
        )
        And("we call the endpoint")
        val ansReqResponseUser2 = helper.makeAnswerRequest
        ansReqResponseUser2.body.extract[TransactionRequestWithChargeJSON400].status should equal(TransactionRequestStatus.COMPLETED.toString)
      }
    }
    
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {

    lazy val bankId = testBankId1.value
    lazy val accountId = testAccountId1.value
    lazy val view = "owner"
    
    scenario("We will call the endpoint WITHOUT user credentials", ApiEndpoint1, VersionOfApi) {

      val transactionRequestId = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1).id
      
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId / view / "transaction-requests" / transactionRequestId).GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario("We will call the endpoint WITH user credentials", ApiEndpoint1, VersionOfApi) {
      
      val transactionRequestId = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1).id
      
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId / view / "transaction-requests" / transactionRequestId).GET <@ (user1)
      val response400 = makeGetRequest(request400)
      Then("We should get a 200")
      response400.code should equal(200)
      response400.body.extract[TransactionRequestWithChargeJSON210].id should equal(transactionRequestId)
    }
    
  }
  
}

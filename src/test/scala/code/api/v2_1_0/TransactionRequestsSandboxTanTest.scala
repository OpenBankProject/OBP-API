package code.api.v2_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages
import code.api.v1_2_1.AmountOfMoneyJSON
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeAnswerJSON, TransactionRequestAccountJSON}
import code.api.v2_0_0.TransactionRequestBodyJSON
import code.api.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import code.bankconnectors.Connector
import code.fx.fx
import code.model.{AccountId, BankAccount, TransactionRequestId}
import net.liftweb.json.Serialization.write
import net.liftweb.util.Props
import org.scalatest.Tag

class TransactionRequestsSandboxTanTest extends ServerSetupWithTestData with DefaultUsers with V210ServerSetup {

  object TransactionRequest extends Tag("transactionRequests")
  val transactionRequestType: String = "SANDBOX_TAN"

  val view = "owner"

  def transactionCount(accounts: BankAccount*): Int = {
    accounts.foldLeft(0)((accumulator, account) => {
      //TODO: might be nice to avoid direct use of the connector, but if we use an api call we need to do
      //it with the correct account owners, and be sure that we don't even run into pagination problems
      accumulator + Connector.connector.vend.getTransactions(account.bankId, account.accountId).get.size
    })
  }

  def defaultSetup() =
    new {

      val testBank = createBank("transactions-test-bank")
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")

      var amt = BigDecimal("12.50")
      var fromCurrency = "AED"
      var toCurrency = "AED"

      def setCurrencyAndAmt(fromCurrency: String, toCurrency: String, amt: String) = {
        this.fromCurrency = fromCurrency
        this.toCurrency = toCurrency
        this.amt = BigDecimal(amt)
      }

      createAccountAndOwnerView(Some(authuser1), bankId, accountId1, fromCurrency)
      createAccountAndOwnerView(Some(authuser1), bankId, accountId2, toCurrency)

      // we add CanCreateAnyTransactionRequest role to user3
      addEntitlement(bankId.value, authuser3.userId, CanCreateAnyTransactionRequest.toString)

      def getFromAccount: BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount: BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      var fromAccount = getFromAccount
      var toAccount = getToAccount

      var totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      var beforeFromBalance = fromAccount.balance
      var beforeToBalance = toAccount.balance

      //we expected transfer amount
      var expectedAmtTo = amt * fx.exchangeRate(fromCurrency, toCurrency).get
      // We debit the From
      var expectedFromNewBalance = beforeFromBalance - amt
      // We credit the To
      var expectedToNewBalance = beforeToBalance + expectedAmtTo

      var transactionRequestId = TransactionRequestId("__trans1")
      var toAccountJson = TransactionRequestAccountJSON(toAccount.bankId.value, toAccount.accountId.value)


      var bodyValue = AmountOfMoneyJSON(fromCurrency, amt.toString())
      var transactionRequestBody = TransactionRequestBodyJSON(toAccountJson, bodyValue, "Test Transaction Request description")

      /**
        * Create Transaction Request. -- V210
        */
      var requestHasRoles = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
        "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)

      def makeCreateTransReqRequest: APIResponse = makePostRequest(requestHasRoles, write(transactionRequestBody))

      def checkAllCreateTransReqResBodyField(createTransactionRequestResponse: APIResponse, withChallenge: Boolean): Unit = {
        Then("we should get a 201 created code")
        createTransactionRequestResponse.code should equal(201)

        Then("We should have a new transaction id in response body")
        (createTransactionRequestResponse.body \ "id").values.toString should not equal ("")

        if (withChallenge) {
          Then("We should have the INITIATED status in response body")
          (createTransactionRequestResponse.body \ "status").values.toString should equal(code.transactionrequests.TransactionRequests.STATUS_INITIATED)
        } else {
          Then("We should have the COMPLETED status in response body")
          (createTransactionRequestResponse.body \ "status").values.toString should equal(code.transactionrequests.TransactionRequests.STATUS_COMPLETED)
        }

        if (withChallenge) {
          Then("Challenge should have body, this is the with challenge scenario")
          (createTransactionRequestResponse.body \ "challenge").children.size should not equal (0)
        } else {
          Then("Challenge should be null, this is the no challenge scenario")
          (createTransactionRequestResponse.body \ "challenge").children.size should equal(0)
        }

        Then("We should have a new TransactionIds value")
        (createTransactionRequestResponse.body \ "transaction_ids").values.toString should not equal ("")

      }

      /**
        * Get all Transaction Requests. - V210
        */
      var getTransReqRequest = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
        "owner" / "transaction-requests").GET <@ (user1)

      def makeGetTransReqRequest = makeGetRequest(getTransReqRequest)

      def checkAllGetTransReqResBodyField(getTransactionRequestResponse: APIResponse, withChellenge: Boolean): Unit = {
        Then("we should get a 200 created code")
        (getTransactionRequestResponse.code) should equal(200)

        And("We should have a new transaction id in response body")
        (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "id").values.toString should not equal ("")

        if (withChellenge) {
          And("We should have the INITIATED status in response body")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "status").values.toString should equal(code.transactionrequests.TransactionRequests.STATUS_INITIATED)

          And("Challenge should be not null, this is the no challenge scenario")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "challenge").children.size should not equal (0)

          And("We should have be null value for TransactionIds")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "transaction_ids").values.toString should equal("List()")
        } else {
          And("We should have the COMPLETED status in response body")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "status").values.toString should equal(code.transactionrequests.TransactionRequests.STATUS_COMPLETED)

          And("Challenge should be null, this is the no challenge scenario")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "challenge").children.size should equal(0)

          And("We should have a new TransactionIds value")
          (getTransactionRequestResponse.body \ "transaction_requests_with_charges" \ "transaction_ids").values.toString should not equal ("")
        }

      }

      /**
        * Get Transactions for Account (Full) -- V210
        */
      var getTransactionRequest = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value / "owner" / "transactions").GET <@ (user1)

      def makeGetTransRequest = makeGetRequest(getTransactionRequest)

      def checkAllGetTransResBodyField(getTransactionResponse: APIResponse, withChellenge: Boolean): Unit = {
        Then("we should get a 200 created code")
        (getTransactionResponse.code) should equal(200)
        And("we should get the body sie is one")
        (getTransactionResponse.body.children.size) should equal(1)
        if (withChellenge) {
          And("we should get None, there is no transaction yet")
          (getTransactionResponse.body \ "transactions" \ "details" \ "description").values.toString should equal("None")
        } else {
          And("we should get the body discription value is as we set before")
          (getTransactionResponse.body \ "transactions" \ "details" \ "description").values.toString should equal(transactionRequestBody.description)
        }
      }

      def checkBankAccountBalance(withChellenge: Boolean): Unit = {
        var rate = fx.exchangeRate(fromAccount.currency, toAccount.currency)
        var convertedAmount = fx.convert(amt, rate)
        var fromAccountBalance = getFromAccount.balance
        var toAccountBalance = getToAccount.balance

        if (withChellenge) {
          Then("No transaction, it should be the same as before ")
          fromAccountBalance should equal((beforeFromBalance))
          And("No transaction, it should be the same as before ")
          toAccountBalance should equal(beforeToBalance)
          And("No transaction, it should be the same as before ")
          transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore)
        } else {
          Then("check that the balances have been properly decreased/increased (since we handle that logic for sandbox accounts at least) ")
          fromAccountBalance should equal((beforeFromBalance - amt))
          And("the account receiving the payment should have a new balance plus the amount paid")
          toAccountBalance should equal(beforeToBalance + convertedAmount)
          And("there should now be 2 new transactions in the database (one for the sender, one for the receiver")
          transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore + 2)
        }
      }
    }

  feature("we can create transaction requests") {

    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("Success -- No challenge, No FX", TransactionRequest) {}
    } else {
      scenario("Success -- No challenge, No FX", TransactionRequest) {

        When("we prepare all the conditions for a normal success -- V210 Create Transaction Request")
        val fixture = defaultSetup()

        Then("we call the 'V210 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = fixture.makeCreateTransReqRequest

        Then("We checked all the fields of createTransactionRequestResponse body ")
        fixture.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V210' to double check it in database")
        val getTransReqResponse = fixture.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        fixture.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V210' to check the transaction info ")
        val getTransResponse = fixture.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        fixture.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amout info")
        fixture.checkBankAccountBalance(false)
      }
    }


    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("No owner view access", TransactionRequest) {}
    } else {
      scenario("No owner view access", TransactionRequest) {

        val fixture = defaultSetup()

        val request = (v2_1Request / "banks" / fixture.testBank.bankId.value / "accounts" / fixture.fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user2)
        val response = makePostRequest(request, write(fixture.transactionRequestBody))

        Then("we should get a 400 created code")
        response.code should equal(400)

        //created a transaction request, check some return values. As type is SANDBOX_TAN and value is < 1000, we expect no challenge
        val error: String = (response.body \ "error").values.toString
        Then("We should have the error: " + ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
        error should equal(ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)

      }

    }

    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("No challenge, with FX (different currencies)", TransactionRequest) {}
    } else {
      scenario("No challenge, with FX (different currencies)", TransactionRequest) {

        When("we prepare all the conditions for a normal success -- V210 Create Transaction Request")
        val fixture = defaultSetup()

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "10"
        fixture.setCurrencyAndAmt(fromCurrency, toCurrency, amt)
        And("We set the special input JSON values for 'V210 Create Transaction Request' endpoint")
        fixture.bodyValue = AmountOfMoneyJSON(fromCurrency, amt.toString())
        fixture.transactionRequestBody = TransactionRequestBodyJSON(fixture.toAccountJson, fixture.bodyValue, "Test Transaction Request description")

        Then("we call the 'V210 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = fixture.makeCreateTransReqRequest

        Then("We checked all the fields of createTransactionRequestResponse body ")
        fixture.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, false)

        When("we need check the 'Get all Transaction Requests. - V210' to double check it in database")
        val getTransReqResponse = fixture.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        fixture.checkAllGetTransReqResBodyField(getTransReqResponse, false)

        When("we need to check the 'Get Transactions for Account (Full) -V210' to check the transaction info ")
        val getTransResponse = fixture.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        fixture.checkAllGetTransResBodyField(getTransResponse, false)

        When("We checked all the data in database, we need check the account amout info")
        fixture.checkBankAccountBalance(false)

      }
    }

    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("With challenge, No FX", TransactionRequest) {}
    } else {
      scenario("With challenge, No FX", TransactionRequest) {
        When("we prepare all the conditions for a normal success -- V210 Create Transaction Request")
        val fixture = defaultSetup()

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "AED"
        val amt = "50000.00"
        fixture.setCurrencyAndAmt(fromCurrency, toCurrency, amt)

        And("We set the special input JSON values for 'V210 Create Transaction Request' endpoint")
        fixture.bodyValue = AmountOfMoneyJSON(fromCurrency, amt.toString())
        fixture.transactionRequestBody = TransactionRequestBodyJSON(fixture.toAccountJson, fixture.bodyValue, "Test Transaction Request description")

        Then("we call the 'V210 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = fixture.makeCreateTransReqRequest

        Then("We checked all the fields of createTransactionRequestResponse body ")
        fixture.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        When("we need check the 'Get all Transaction Requests. - V210' to double check it in database")
        val getTransReqResponse = fixture.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        fixture.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        When("we need to check the 'Get Transactions for Account (Full) -V210' to check the transaction info ")
        val getTransResponse = fixture.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        fixture.checkAllGetTransResBodyField(getTransResponse, true)

        When("We checked all the data in database, we need check the account amount info")
        fixture.checkBankAccountBalance(true)
      }
    }


    // With Challenge, with FX
    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create an FX transaction request with challenge", TransactionRequest) {}
    } else {
      scenario("we create an FX transaction request with challenge", TransactionRequest) {
        When("we prepare all the conditions for a normal success -- V210 Create Transaction Request")
        val fixture = defaultSetup()

        And("We set the special conditions for different currencies")
        val fromCurrency = "AED"
        val toCurrency = "INR"
        val amt = "50000.00"
        fixture.setCurrencyAndAmt(fromCurrency, toCurrency, amt)

        And("We set the special input JSON values for 'V210 Create Transaction Request' endpoint")
        fixture.bodyValue = AmountOfMoneyJSON(fromCurrency, amt.toString())
        fixture.transactionRequestBody = TransactionRequestBodyJSON(fixture.toAccountJson, fixture.bodyValue, "Test Transaction Request description")

        Then("we call the 'V210 Create Transaction Request' endpoint")
        val createTransactionRequestResponse = fixture.makeCreateTransReqRequest

        Then("We checked all the fields of createTransactionRequestResponse body ")
        fixture.checkAllCreateTransReqResBodyField(createTransactionRequestResponse, true)

        When("we need check the 'Get all Transaction Requests. - V210' to double check it in database")
        val getTransReqResponse = fixture.makeGetTransReqRequest

        Then("We checked all the fields of getTransReqResponse body")
        fixture.checkAllGetTransReqResBodyField(getTransReqResponse, true)

        When("we need to check the 'Get Transactions for Account (Full) -V210' to check the transaction info ")
        val getTransResponse = fixture.makeGetTransRequest
        Then("We checked all the fields of getTransResponse body")
        fixture.checkAllGetTransResBodyField(getTransResponse, true)

        When("We checked all the data in database, we need check the account amount info")
        fixture.checkBankAccountBalance(true)
      }
    }
  }
}
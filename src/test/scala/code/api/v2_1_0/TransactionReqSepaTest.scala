package code.api.v2_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages
import code.api.v1_2_1.AmountOfMoneyJSON
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeAnswerJSON, TransactionRequestAccountJSON}
import code.api.{DefaultUsers, ServerSetupWithTestData}
import code.bankconnectors.Connector
import code.fx.fx
import code.model.{AccountRoutingAddress, _}
import net.liftweb.json.JsonAST.{JArray, JField, JObject, JString}
import net.liftweb.json.Serialization.write
import net.liftweb.util.Props
import org.scalatest.Tag

class TransactionReqSepaTest extends ServerSetupWithTestData with DefaultUsers with V210ServerSetup {

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }

  object TransactionRequest extends Tag("transactionRequests")

  val transactionRequestType: String = "SEPA"

  feature("Assuring that endpoint 'Create Transaction Request.' works as expected - v2.1.0") {
    val view = "owner"

    def transactionCount(accounts: BankAccount*): Int = {
      accounts.foldLeft(0)((accumulator, account) => {
        //TODO: might be nice to avoid direct use of the connector, but if we use an api call we need to do
        //it with the correct account owners, and be sure that we don't even run into pagination problems
        accumulator + Connector.connector.vend.getTransactions(account.bankId, account.accountId).get.size
      })
    }
    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request without login user", TransactionRequest) {}
    } else {
      scenario("we create a transaction request without login user", TransactionRequest) {

        Given("We create the BankAccount and Counterparty")
        val testBank = createBank("testBank-bank")

        val fromBankId = testBank.bankId
        val fromAccountId = AccountId("fromAccountId")
        val toBankId = testBank.bankId
        val toAccountId = AccountId("toAccountId")
        val fromAccount = createAccount(fromBankId, fromAccountId, "EUR")
        val toAccount = createAccount(toBankId, toAccountId, "EUR")

        val accountRoutingAddress = AccountRoutingAddress("toIban");
        val isBeneficiary = true
        val counterParty = createCounterparty(toBankId.value, toAccountId.value, accountRoutingAddress.value, isBeneficiary);


        Then("Create the view and grant the owner view to use1")
        // ownerView is 'view = "owner"', we made it before
        val ownerView = createOwnerView(fromBankId, fromAccountId)
        grantAccessToView(obpuser1, ownerView)

        Then("Add the CanCreateAnyTransactionRequest entitlement to user1")
        addEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest.toString)
        val hasEntitlement = code.api.util.APIUtil.hasEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest)
        hasEntitlement should equal(true)

        Then("We prepare for the request Json")
        val bodyValue = AmountOfMoneyJSON("EUR", "12.50")

        val noExistAccountRoutingAddress = counterParty.accountRoutingAddress
        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, noExistAccountRoutingAddress, "Test Transaction Request description")

        Then("We call createTransactionRequest - V210")
        var request = (v2_1Request / "banks" / fromAccount.bankId.value / "accounts" / fromAccount.accountId.value /
          view / "transaction-request-types" / transactionRequestType / "transaction-requests").POST
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 400 created code")
        response.code should equal(400)

        //created a transaction request, check some return values. As type is SEPA and value is < 1000, we expect no challenge
        val error = for {JObject(o) <- response.body; JField("error", JString(error)) <- o} yield error
        Then("We should have the error message")
        error should contain(ErrorMessages.UserNotLoggedIn)

      }
    }

    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request with a user without owner view access", TransactionRequest) {}
    } else {
      scenario("we create a transaction request with a user without owner view access", TransactionRequest) {


        val testBank = createBank("transactions-test-bank")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__acc1")
        val accountId2 = AccountId("__acc2")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

        def getFromAccount: BankAccount = {
          BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        }

        def getToAccount: BankAccount = {
          BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
        }

        val fromAccount = getFromAccount
        val toAccount = getToAccount

        val toAccountJson = TransactionRequestAccountJSON(toAccount.bankId.value, toAccount.accountId.value)

        val amt = BigDecimal("12.50")
        val bodyValue = AmountOfMoneyJSON("EUR", amt.toString())

        val counterpartyMetadataIban1 = AccountRoutingAddress("IBAN1");
        val counterpartyMetadataIban2 = AccountRoutingAddress("IBAN2");
        val counterpartyMetadata1 = createCounterparty(bankId.value, accountId1.value, counterpartyMetadataIban1.value, true);
        val counterpartyMetadata2 = createCounterparty(bankId.value, accountId2.value, counterpartyMetadataIban2.value, true);

        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, counterpartyMetadata2.accountRoutingAddress, "Test Transaction Request description")


        //call createTransactionRequest with a user without owner view access
        val request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user2)
        val response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 400 created code")
        response.code should equal(400)

        //created a transaction request, check some return values. As type is SEPA and value is < 1000, we expect no challenge
        val error: String = (response.body \ "error") match {
          case JString(i) => i
          case _ => ""
        }
        Then("We should have the error: " + ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
        error should equal(ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)

      }
    }


    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request with a user who doesn't have access to owner view but has CanCreateAnyTransactionRequest at a different BANK_ID", TransactionRequest) {}
    } else {
      scenario("we create a transaction request with a user who doesn't have access to owner view but has CanCreateAnyTransactionRequest at a different BANK_ID", TransactionRequest) {
        val testBank = createBank("transactions-test-bank")
        val testBank2 = createBank("transactions-test-bank2")
        val bankId = testBank.bankId
        val bankId2 = testBank2.bankId
        val accountId1 = AccountId("__acc1")
        val accountId2 = AccountId("__acc2")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")
        addEntitlement(bankId2.value, obpuser3.userId, CanCreateAnyTransactionRequest.toString)

        Then("We add entitlement to user3")
        val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId2.value, obpuser3.userId, CanCreateAnyTransactionRequest)
        hasEntitlement should equal(true)

        def getFromAccount: BankAccount = {
          BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        }

        def getToAccount: BankAccount = {
          BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
        }

        val fromAccount = getFromAccount
        val toAccount = getToAccount

        val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

        val beforeFromBalance = fromAccount.balance
        val beforeToBalance = toAccount.balance

        val transactionRequestId = TransactionRequestId("__trans2")
        val toAccountJson = TransactionRequestAccountJSON(toAccount.bankId.value, toAccount.accountId.value)

        val amt = BigDecimal("12.50")
        val bodyValue = AmountOfMoneyJSON("EUR", amt.toString())
        //          val transactionRequestBody = TransactionRequestBodyJSON(toAccountJson, bodyValue, "Test Transaction Request description")

        val counterpartyMetadataIban1 = AccountRoutingAddress("IBAN1");
        val counterpartyMetadataIban2 = AccountRoutingAddress("IBAN2");
        val counterpartyMetadata1 = createCounterparty(bankId.value, accountId1.value, counterpartyMetadataIban1.value, true);
        val counterpartyMetadata2 = createCounterparty(bankId.value, accountId2.value, counterpartyMetadataIban2.value, true);

        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(AmountOfMoneyJSON("EUR", amt.toString()), counterpartyMetadata2.accountRoutingAddress, "Test Transaction Request description")


        //call createTransactionRequest
        val request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user3)
        val response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 400 created code")
        response.code should equal(400)

        //created a transaction request, check some return values. As type is SEPA and value is < 1000, we expect no challenge
        val error: String = (response.body \ "error") match {
          case JString(i) => i
          case _ => ""
        }
        Then("We should have the error: " + ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
        error should equal(ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)


      }
    }


    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request but with noExisting Iban", TransactionRequest) {}
    } else {
      scenario("we create a transaction request  but with noExisting Iban  ", TransactionRequest) {

        Given("We create the BankAccount ")
        val testBank = createBank("testBank-bank")

        val fromBankId = testBank.bankId
        val fromAccountId = AccountId("fromAccountId")
        val toBankId = testBank.bankId
        val toAccountId = AccountId("toAccountId")
        val fromAccount = createAccount(fromBankId, fromAccountId, "EUR")
        val toAccount = createAccount(toBankId, toAccountId, "EUR")

        Then("Create the view and grant the owner view to use1")
        // ownerView is 'view = "owner"', we made it before
        val ownerView = createOwnerView(fromBankId, fromAccountId)
        grantAccessToView(obpuser1, ownerView)

        Then("Add the CanCreateAnyTransactionRequest entitlement to user1")
        addEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest.toString)
        val hasEntitlement = code.api.util.APIUtil.hasEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest)
        hasEntitlement should equal(true)

        Then("We prepare for the request Json")
        val bodyValue = AmountOfMoneyJSON("EUR", "12.50")

        val noExistAccountRoutingAddress = "noExistAccountRoutingAddress"
        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, noExistAccountRoutingAddress, "Test Transaction Request description")

        Then("We call createTransactionRequest - V210")
        var request = (v2_1Request / "banks" / fromAccount.bankId.value / "accounts" / fromAccount.accountId.value /
          view / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 400 created code")
        response.code should equal(400)

        //created a transaction request, check some return values. As type is SEPA and value is < 1000, we expect no challenge
        val error = for {JObject(o) <- response.body; JField("error", JString(error)) <- o} yield error
        Then("We should have the error message")
        error should contain(ErrorMessages.CounterpartyNotFoundByIban)

      }
    }


    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request  but with noExisting otherAccountId", TransactionRequest) {}
    } else {
      scenario("we create a transaction request but with noExisting otherAccountId", TransactionRequest) {

        Given("Create the BankAccount")
        val testBank = createBank("testBank-bank")

        val fromBankId = testBank.bankId
        val fromAccountId = AccountId("fromAccountId")
        val toBankId = testBank.bankId
        val toAccountId = AccountId("toAccountId")
        val fromAccount = createAccount(fromBankId, fromAccountId, "EUR")
        val toAccount = createAccount(toBankId, toAccountId, "EUR")

        Given("Create the counterParty with wrong otherAccountId ")
        val accountRoutingAddress = AccountRoutingAddress("toIban");
        val isBeneficiary = true
        val noExistingAccoundId = "noExistingAccoundID"
        val counterParty = createCounterparty(toBankId.value, noExistingAccoundId, accountRoutingAddress.value, isBeneficiary);


        Then("Create the view and grant the owner view to use1")
        // ownerView is 'view = "owner"', we made it before
        val ownerView = createOwnerView(fromBankId, fromAccountId)
        grantAccessToView(obpuser1, ownerView)

        Then("Add the CanCreateAnyTransactionRequest entitlement to user1")
        addEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest.toString)
        val hasEntitlement = code.api.util.APIUtil.hasEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest)
        hasEntitlement should equal(true)

        Then("We prepare for the request Json")
        val bodyValue = AmountOfMoneyJSON("EUR", "12.50")

        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, counterParty.accountRoutingAddress, "Test Transaction Request description")

        //call createTransactionRequest
        var request = (v2_1Request / "banks" / fromAccount.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 400 created code")
        response.code should equal(400)

        //created a transaction request, check some return values. As type is SEPA and value is < 1000, we expect no challenge
        val error = for {JObject(o) <- response.body; JField("error", JString(error)) <- o} yield error
        Then("We should have the error message")
        error should contain(ErrorMessages.CounterpartyNotFound)
      }
    }


    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request but the isBeneficiary is false", TransactionRequest) {}
    } else {
      scenario("we create a transaction request but the isBeneficiary is false", TransactionRequest) {

        Given("We create the BankAccount and Counterparty")
        val testBank = createBank("testBank-bank")

        val fromBankId = testBank.bankId
        val fromAccountId = AccountId("fromAccountId")
        val toBankId = testBank.bankId
        val toAccountId = AccountId("toAccountId")
        val fromAccount = createAccount(fromBankId, fromAccountId, "EUR")
        val toAccount = createAccount(toBankId, toAccountId, "EUR")

        val accountRoutingAddress = AccountRoutingAddress("toIban");
        val isBeneficiary = false
        val counterParty = createCounterparty(toBankId.value, toAccountId.value, accountRoutingAddress.value, isBeneficiary);


        Then("Create the view and grant the owner view to use1")
        // ownerView is 'view = "owner"', we made it before
        val ownerView = createOwnerView(fromBankId, fromAccountId)
        grantAccessToView(obpuser1, ownerView)

        Then("Add the CanCreateAnyTransactionRequest entitlement to user1")
        addEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest.toString)
        val hasEntitlement = code.api.util.APIUtil.hasEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest)
        hasEntitlement should equal(true)

        Then("We prepare for the request Json")
        val bodyValue = AmountOfMoneyJSON("EUR", "12.50")

        val noExistAccountRoutingAddress = counterParty.accountRoutingAddress
        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, noExistAccountRoutingAddress, "Test Transaction Request description")

        Then("We call createTransactionRequest - V210")
        var request = (v2_1Request / "banks" / fromAccount.bankId.value / "accounts" / fromAccount.accountId.value /
          view / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 400 created code")
        response.code should equal(400)

        //created a transaction request, check some return values. As type is SEPA and value is < 1000, we expect no challenge
        val error = for {JObject(o) <- response.body; JField("error", JString(error)) <- o} yield error
        Then("We should have the error message")
        error should contain(ErrorMessages.CounterpartyBeneficiaryPermit)

      }
    }

    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request but the transactionRequestType is invalid", TransactionRequest) {}
    } else {
      scenario("we create a transaction request but the transactionRequestType is invalid", TransactionRequest) {

        Given("We create the BankAccount and Counterparty")
        val testBank = createBank("testBank-bank")

        val fromBankId = testBank.bankId
        val fromAccountId = AccountId("fromAccountId")
        val toBankId = testBank.bankId
        val toAccountId = AccountId("toAccountId")
        val fromAccount = createAccount(fromBankId, fromAccountId, "EUR")
        val toAccount = createAccount(toBankId, toAccountId, "EUR")

        val accountRoutingAddress = AccountRoutingAddress("toIban");
        val isBeneficiary = false
        val counterParty = createCounterparty(toBankId.value, toAccountId.value, accountRoutingAddress.value, isBeneficiary);


        Then("Create the view and grant the owner view to use1")
        // ownerView is 'view = "owner"', we made it before
        val ownerView = createOwnerView(fromBankId, fromAccountId)
        grantAccessToView(obpuser1, ownerView)

        Then("Add the CanCreateAnyTransactionRequest entitlement to user1")
        addEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest.toString)
        val hasEntitlement = code.api.util.APIUtil.hasEntitlement(fromBankId.value, obpuser1.userId, CanCreateAnyTransactionRequest)
        hasEntitlement should equal(true)

        Then("We prepare for the request Json")
        val bodyValue = AmountOfMoneyJSON("EUR", "12.50")
        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, counterParty.accountRoutingAddress, "Test Transaction Request description")

        Then("We call createTransactionRequest with invalid transactionRequestType - V210")
        val invalidTransactionRequestType = "invalidTransactionRequestType"
        var request = (v2_1Request / "banks" / fromAccount.bankId.value / "accounts" / fromAccount.accountId.value /
          view / "transaction-request-types" / invalidTransactionRequestType / "transaction-requests").POST <@ (user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 400 created code")
        response.code should equal(400)

        //created a transaction request, check some return values. As type is SEPA and value is < 1000, we expect no challenge
        val error: List[String] = for {JObject(o) <- response.body; JField("error", JString(error)) <- o} yield error
        Then("We should have the error message")
        error(0) should include(ErrorMessages.InvalidTransactionRequestType)
      }
    }

    // No challenge, No FX (same currencies) - user3 has CanCreateAnyTransactionRequest, but doesn't have access to owner view
    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request with a user who doesn't have access to owner view but has CanCreateAnyTransactionRequest at BANK_ID", TransactionRequest) {}
    } else {
      scenario("we create a transaction request with a user who doesn't have access to owner view but has CanCreateAnyTransactionRequest at BANK_ID", TransactionRequest) {

        Given("test BankAccounts and CounterParties")
        val testBank = createBank("transactions-test-bank")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__acc1")
        val accountId2 = AccountId("__acc2")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

        val accountRoutingAddress1 = AccountRoutingAddress("IBAN1");
        val accountRoutingAddress2 = AccountRoutingAddress("IBAN2");
        val counterParty1 = createCounterparty(bankId.value, accountId1.value, accountRoutingAddress1.value, true);
        val counterParty2 = createCounterparty(bankId.value, accountId2.value, accountRoutingAddress2.value, true);

        def getFromAccount: BankAccount = {
          BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        }

        def getToAccount: BankAccount = {
          BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
        }

        val fromAccount = getFromAccount
        val toAccount = getToAccount

        val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

        val beforeFromBalance = fromAccount.balance
        val beforeToBalance = toAccount.balance


        Then("We add entitlement to user3")
        addEntitlement(bankId.value, obpuser3.userId, CanCreateAnyTransactionRequest.toString)
        val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, obpuser3.userId, CanCreateAnyTransactionRequest)
        hasEntitlement should equal(true)


        val amt = BigDecimal("12.50")
        val bodyValue = AmountOfMoneyJSON("EUR", "12.50")
        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, counterParty2.accountRoutingAddress, "Test Transaction Request description")

        //call createTransactionRequest v210
        var request = (v2_1Request / "banks" / fromAccount.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user3)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 201 created code")
        response.code should equal(201)

        //created a transaction request, check some return values. As type is SEPA and value is < 1000, we expect no challenge
        val transRequestId: String = (response.body \ "id") match {
          case JString(i) => i
          case _ => ""
        }
        Then("We should have some new transaction id")
        transRequestId should not equal ("")

        val status: String = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal(code.transactionrequests.TransactionRequests.STATUS_COMPLETED)

        // Challenge should be null (none required)
        var challenge = (response.body \ "challenge").children
        challenge.size should equal(0)

        var transaction_ids = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case JArray(i) => i
          case _ => ""
        }
        //If user does not have access to owner or other view - they won’t be able to view transaction. Hence they can’t see the transaction_id

        transaction_ids should not equal ("")

        //call getTransactionRequests, check that we really created a transaction request
        request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-requests").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        val transactionRequests = response.body.children
        transactionRequests.size should not equal (0)


        //check transaction_ids again
        transaction_ids = (response.body \ "transaction_requests_with_charges" \ "transaction_ids") match {
          case JArray(i) if (i.length > 0) => i
          case _ => ""
        }
        transaction_ids should not equal ("")

        //make sure that we also get no challenges back from this url (after getting from db)
        challenge = (response.body \ "challenge").children
        challenge.size should equal(0)


        //check that the balances have been properly decreased/increased (since we handle that logic for sandbox accounts at least)
        //(do it here even though the payments test does test makePayment already)
        val rate = fx.exchangeRate(fromAccount.currency, toAccount.currency)
        val convertedAmount = fx.convert(amt, rate)
        val fromAccountBalance = getFromAccount.balance
        And("the from account should have a balance smaller by the amount specified to pay")
        fromAccountBalance should equal((beforeFromBalance - convertedAmount))

        And("the account receiving the payment should have a new balance plus the amount paid")
        val toAccountBalance = getToAccount.balance
        toAccountBalance should equal(beforeToBalance + convertedAmount)

        And("there should now be 2 new transactions in the database (one for the sender, one for the receiver")
        transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore + 2)
      }
    }


    // No challenge, No FX (same currencies)- user1 has CanCreateAnyTransactionRequest and can access to owner view
    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request without challenge, no FX (same currencies)", TransactionRequest) {}
    } else {
      scenario("we create a transaction request without challenge, no FX (same currencies)", TransactionRequest) {
        Given("BankAccounts and CounterParties")
        val testBank = createBank("transactions-test-bank")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__acc1")
        val accountId2 = AccountId("__acc2")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

        val accountRoutingAddress1 = AccountRoutingAddress("IBAN1");
        val accountRoutingAddress2 = AccountRoutingAddress("IBAN2");
        val counterParty1 = createCounterparty(bankId.value, accountId1.value, accountRoutingAddress1.value, true);
        val counterParty2 = createCounterparty(bankId.value, accountId2.value, accountRoutingAddress2.value, true);

        def getFromAccount: BankAccount = {
          BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        }

        def getToAccount: BankAccount = {
          BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
        }

        val fromAccount = getFromAccount
        val toAccount = getToAccount

        val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

        val beforeFromBalance = fromAccount.balance
        val beforeToBalance = toAccount.balance

        Given("POST input Json")
        val amt = BigDecimal("12.50")
        val bodyValue = AmountOfMoneyJSON("EUR", amt.toString())
        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, counterParty2.accountRoutingAddress, "Test Transaction Request description")

        //call createTransactionRequest -V210
        var request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 201 created code")
        response.code should equal(201)

        //created a transaction request, check some return values. As type is SEPA and value is < 1000, we expect no challenge
        Then("We should have a new transaction id")
        val transRequestId: String = (response.body \ "id") match {
          case JString(i) => i
          case _ => ""
        }
        transRequestId should not equal ("")


        Then("status should be complieted")
        val status: String = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal(code.transactionrequests.TransactionRequests.STATUS_COMPLETED)

        // Challenge should be null (none required)
        var challenge = (response.body \ "challenge").children
        challenge.size should equal(0)

        var transaction_ids = (response.body \ "transaction_ids") match {
          case JArray(i) if (i.length > 0) => i
          case _ => ""
        }
        transaction_ids should not equal ("")

        //call getTransactionRequests, check that we really created a transaction request
        request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-requests").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        val transactionRequests = response.body.children
        transactionRequests.size should not equal (0)

        //check transaction_ids again
        transaction_ids = (response.body \ "transaction_requests_with_charges" \ "transaction_ids") match {
          case JArray(i) if (i.length > 0) => i
          case _ => ""
        }
        transaction_ids should not equal ("")

        //check that the balances have been properly decreased/increased (since we handle that logic for sandbox accounts at least)
        //(do it here even though the payments test does test makePayment already)
        val rate = fx.exchangeRate(fromAccount.currency, toAccount.currency)
        val convertedAmount = fx.convert(amt, rate)
        val fromAccountBalance = getFromAccount.balance
        And("the from account should have a balance smaller by the amount specified to pay")
        fromAccountBalance should equal((beforeFromBalance - convertedAmount))


        And("the account receiving the payment should have a new balance plus the amount paid")
        val toAccountBalance = getToAccount.balance
        toAccountBalance should equal(beforeToBalance + convertedAmount)

        And("there should now be 2 new transactions in the database (one for the sender, one for the receiver")
        transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore + 2)
      }
    }


    // No challenge, with FX
    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create an FX transaction request without challenge, with FX (different currencies)", TransactionRequest) {}
    } else {
      scenario("we create an FX transaction request without challenge, with FX (different currencies)", TransactionRequest) {
        val testBank = createBank("transactions-test-bank")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__acc1fx")
        val accountId2 = AccountId("__acc2fx")

        val fromCurrency = "AED"
        val toCurrency = "INR"

        val amt = BigDecimal("10.00") // This is money going out. We want to transfer this away from the From account.
        val expectedAmtTo = amt * fx.exchangeRate(fromCurrency, toCurrency).get

        createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, fromCurrency)
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, toCurrency)

        val accountRoutingAddress1 = AccountRoutingAddress("IBAN1");
        val accountRoutingAddress2 = AccountRoutingAddress("IBAN2");
        val counterParty1 = createCounterparty(bankId.value, accountId1.value, accountRoutingAddress1.value, true);
        val counterParty2 = createCounterparty(bankId.value, accountId2.value, accountRoutingAddress2.value, true);


        def getFromAccount: BankAccount = {
          BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        }

        def getToAccount: BankAccount = {
          BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
        }

        val fromAccount = getFromAccount
        val toAccount = getToAccount

        val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

        val beforeFromBalance = fromAccount.balance
        val beforeFromCurrency = fromAccount.currency

        val beforeToBalance = toAccount.balance
        val beforeToCurrency = toAccount.currency

        // We debit the From
        val expectedFromNewBalance = beforeFromBalance - amt

        // We credit the To
        val expectedToNewBalance = beforeToBalance + expectedAmtTo



        val bodyValue = AmountOfMoneyJSON(fromCurrency, amt.toString())
        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, counterParty2.accountRoutingAddress, "Test Transaction Request description")


        //call createTransactionRequest
        var request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 201 created code")
        response.code should equal(201)


        //created a transaction request, check some return values. As type is SEPA, we expect no challenge
        val transRequestId: String = (response.body \ "id") match {
          case JString(i) => i
          case _ => ""
        }
        Then("We should have some new transaction request id")
        transRequestId should not equal ("")

        val status: String = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal(code.transactionrequests.TransactionRequests.STATUS_COMPLETED)


        Then("we should not have a challenge object")
        var challenge = (response.body \ "challenge").children
        challenge.size should equal(0)

        var transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case JArray(i) => i
          case _ => ""
        }
        transaction_id should not equal ("")

        //call getTransactionRequests, check that we really created a transaction request
        request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-requests").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        val transactionRequests = response.body.children
        transactionRequests.size should not equal (0)

        //check transaction_ids again
        transaction_id = (response.body \ "transaction_requests_with_charges" \ "transaction_ids") match {
          case JArray(i) if (i.length > 0) => i
          case _ => ""
        }
        transaction_id should not equal ("")

        //make sure that we also get no challenges back from this url (after getting from db)
        challenge = (response.body \ "challenge").children
        challenge.size should equal(0)

        //check that we created a new transaction (since no challenge)
        request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transactions").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)

        val fromTransactions = response.body.children

        fromTransactions.size should equal(1)

        //check that the description has been set
        val description = (((response.body \ "transactions") (0) \ "details") \ "description") match {
          case JString(i) => i
          case _ => ""
        }
        description should not equal ("")

        // Transaction Value
        val actualFromAmount = (((response.body \ "transactions") (0) \ "details") \ "value" \ "amount") match {
          case JString(i) => i
          case _ => ""
        }

        // We are debiting the amount
        amt should equal(-1 * BigDecimal(actualFromAmount))

        // New Balance
        val actualFromBalance = (((response.body \ "transactions") (0) \ "details") \ "new_balance" \ "amount") match {
          case JString(i) => i
          case _ => ""
        }
        expectedFromNewBalance should equal(BigDecimal(actualFromBalance))

        //check that we created a new transaction (since no challenge)
        request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / toAccount.accountId.value /
          "owner" / "transactions").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)

        val toTransactions = response.body.children

        toTransactions.size should equal(1)

        //check that the description has been set
        val toDescription = (((response.body \ "transactions") (0) \ "details") \ "description") match {
          case JString(i) => i
          case _ => ""
        }
        description should not equal ("")

        // Transaction Value
        val actualToAmount = (((response.body \ "transactions") (0) \ "details") \ "value" \ "amount") match {
          case JString(i) => i
          case _ => ""
        }
        expectedAmtTo should equal(BigDecimal(actualToAmount))

        // New Balance
        val actualToBalance = (((response.body \ "transactions") (0) \ "details") \ "new_balance" \ "amount") match {
          case JString(i) => i
          case _ => ""
        }
        expectedToNewBalance should equal(BigDecimal(actualToBalance))


        val rate = fx.exchangeRate(fromAccount.currency, toAccount.currency)
        val convertedAmount = fx.convert(amt, rate)
        val fromAccountBalance = getFromAccount.balance
        And("the from account should have a balance smaller by the original amount specified to pay")
        fromAccountBalance should equal(beforeFromBalance - amt)


        And("the account receiving the payment should have a new balance plus the amount paid")
        val toAccountBalance = getToAccount.balance
        toAccountBalance should equal(beforeToBalance + convertedAmount)

        And("there should now be 2 new transactions in the database (one for the sender, one for the receiver)")
        transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore + 2)
      }
    }


    // With challenge, No FX (Same currencies)
    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create a transaction request with a challenge, same currencies", TransactionRequest) {}
    } else {
      scenario("we create a transaction request with a challenge, same currencies", TransactionRequest) {
        Given("setup accounts")
        val testBank = createBank("transactions-test-bank")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__acc1")
        val accountId2 = AccountId("__acc2")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

        def getFromAccount: BankAccount = {
          BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        }

        def getToAccount: BankAccount = {
          BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
        }

        val fromAccount = getFromAccount
        val toAccount = getToAccount

        val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

        val beforeFromBalance = fromAccount.balance
        val beforeToBalance = toAccount.balance

        val transactionRequestId = TransactionRequestId("__trans1")
        val toAccountJson = TransactionRequestAccountJSON(toAccount.bankId.value, toAccount.accountId.value)


        val accountRoutingAddress1 = AccountRoutingAddress("IBAN1");
        val accountRoutingAddress2 = AccountRoutingAddress("IBAN2");
        val counterParty1 = createCounterparty(bankId.value, accountId1.value, accountRoutingAddress1.value, true);
        val counterParty2 = createCounterparty(bankId.value, accountId2.value, accountRoutingAddress2.value, true);

        //1. TODO: get possible challenge types from account

        //2. create transaction request to to-account with one of the possible challenges
        //amount over 1000 €, so should trigger challenge request
        val amt = BigDecimal("1250.00")
        val bodyValue = AmountOfMoneyJSON("EUR", amt.toString())
        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, counterParty2.accountRoutingAddress, "Test Transaction Request description")

        //call createTransactionRequest API method
        var request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 201 created code")
        response.code should equal(201)

        //ok, created a transaction request, check some return values. As type is SEPA but over 100€, we expect a challenge
        val transRequestId: String = (response.body \ "id") match {
          case JString(i) => i
          case _ => ""
        }
        transRequestId should not equal ("")

        var status: String = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal(code.transactionrequests.TransactionRequests.STATUS_INITIATED)

        var transaction_id = (response.body \ "transaction_ids") match {
          //TODO my wrong logic here ,it is a Array , it is wrong!!
          case JString(i) => i
          case _ => ""
        }
        transaction_id should equal("")

        var challenge = (response.body \ "challenge").children
        challenge.size should not equal (0)

        val challenge_id = (response.body \ "challenge" \ "id") match {
          case JString(s) => s
          case _ => ""
        }
        challenge_id should not equal ("")

        //call getTransactionRequests, check that we really created a transaction request
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-requests").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        var transactionRequests = response.body.children

        transactionRequests.size should equal(1)
        transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_id should equal("")

        challenge = (response.body \ "challenge").children
        challenge.size should not equal (0)

        //3. answer challenge and check if transaction is being created
        //call answerTransactionRequestChallenge, give a false answer
        var answerJson = ChallengeAnswerJSON(id = challenge_id, answer = "hello") //wrong answer, not a number
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@ (user1)
        response = makePostRequest(request, write(answerJson))
        Then("we should get a 400 bad request code")
        response.code should equal(400)

        //TODO: check if allowed_attempts is decreased

        //call answerTransactionRequestChallenge again, give a good answer
        answerJson = ChallengeAnswerJSON(id = challenge_id, answer = "12345") //good answer, not a number
        request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@ (user1)
        response = makePostRequest(request, write(answerJson))
        Then("we should get a 202 accepted code")
        response.code should equal(202)

        //check if returned data includes new transaction's id
        status = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal(code.transactionrequests.TransactionRequests.STATUS_COMPLETED)

        transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_id should not equal ("")

        //call getTransactionRequests, check that we really created a transaction
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-requests").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        transactionRequests = response.body.children

        transactionRequests.size should equal(1)
        transaction_id = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_id should not equal ("")

        challenge = (response.body \ "challenge").children
        challenge.size should not equal (0)

        //check that the balances have been properly decreased/increased (since we handle that logic for sandbox accounts at least)
        //(do it here even though the payments test does test makePayment already)

        val fromAccountBalance = getFromAccount.balance
        And("the from account should have a balance smaller by the amount specified to pay")
        fromAccountBalance should equal((beforeFromBalance - amt))

        /*
        And("the newest transaction for the account receiving the payment should have the proper amount")
        newestToAccountTransaction.details.value.amount should equal(amt.toString)
        */

        And("the account receiving the payment should have a new balance plus the amount paid")
        val toAccountBalance = getToAccount.balance
        toAccountBalance should equal(beforeToBalance + amt)

        And("there should now be 2 new transactions in the database (one for the sender, one for the receiver")
        transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore + 2)
      }
    }


    // With Challenge, with FX
    if (Props.getBool("transactionRequests_enabled", false) == false) {
      ignore("we create an FX transaction request with challenge", TransactionRequest) {}
    } else {
      scenario("we create an FX transaction request with challenge", TransactionRequest) {
        val testBank = createBank("transactions-test-bank")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__acc1fx")
        val accountId2 = AccountId("__acc2fx")

        val fromCurrency = "AED"
        val toCurrency = "INR"

        // This value is over the "challenge threshold" i.e. a security challenge will need to be answered.
        // the limited AED is 4140 = 1000 eruo
        val amt = BigDecimal("5000.00") // This is money going out. We want to transfer this away from the From account.


        val expectedAmtTo = amt * fx.exchangeRate(fromCurrency, toCurrency).get

        createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, fromCurrency)
        createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, toCurrency)

        def getFromAccount: BankAccount = {
          BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        }

        def getToAccount: BankAccount = {
          BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
        }

        val fromAccount = getFromAccount
        val toAccount = getToAccount

        val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

        val beforeFromBalance = fromAccount.balance
        val beforeFromCurrency = fromAccount.currency


        val beforeToBalance = toAccount.balance
        val beforeToCurrency = toAccount.currency

        // We debit the From
        val expectedFromNewBalance = beforeFromBalance - amt

        // We credit the To
        val expectedToNewBalance = beforeToBalance + expectedAmtTo

        val accountRoutingAddress1 = AccountRoutingAddress("IBAN1");
        val accountRoutingAddress2 = AccountRoutingAddress("IBAN2");
        val counterParty1 = createCounterparty(bankId.value, accountId1.value, accountRoutingAddress1.value, true);
        val counterParty2 = createCounterparty(bankId.value, accountId2.value, accountRoutingAddress2.value, true);


        //Create a transaction (request)
        //1. get possible challenge types for from account
        //2. create transaction request to to-account with one of the possible challenges
        //3. answer challenge
        //4. have a new transaction

        val bodyValue = AmountOfMoneyJSON(fromCurrency, amt.toString())
        val transactionRequestBody = TransactionRequestDetailsSEPAJSON(bodyValue, counterParty2.accountRoutingAddress, "Test Transaction Request description")

        //call createTransactionRequest
        var request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@ (user1)
        var response = makePostRequest(request, write(transactionRequestBody))
        Then("we should get a 201 created code")
        response.code should equal(201)

        //created a transaction request, check some return values. As type is SEPA, we expect no challenge
        val transRequestId: String = (response.body \ "id") match {
          case JString(i) => i
          case _ => ""
        }
        Then("We should have some new transaction id")
        transRequestId should not equal ("")

        var status: String = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal(code.transactionrequests.TransactionRequests.STATUS_INITIATED)

        var transaction_ids1 = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_ids1 should equal("")

        var challenge = (response.body \ "challenge").children
        challenge.size should not equal (0)

        val challenge_id = (response.body \ "challenge" \ "id") match {
          case JString(s) => s
          case _ => ""
        }
        challenge_id should not equal ("")

        //call getTransactionRequests, check that we really created a transaction request
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-requests").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        var transactionRequests = response.body.children

        transactionRequests.size should equal(1)
        var transaction_ids2 = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case _ => ""
        }
        transaction_ids2 should equal("")

        Then("we should have a challenge object")
        challenge = (response.body \ "challenge").children
        challenge.size should not equal (0)

        //3. answer challenge and check if transaction is being created
        //call answerTransactionRequestChallenge, give a false answer
        var answerJson = ChallengeAnswerJSON(id = challenge_id, answer = "hello") //wrong answer, not a number
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@ (user1)
        response = makePostRequest(request, write(answerJson))
        Then("we should get a 400 bad request code")
        response.code should equal(400)

        //TODO: check if allowed_attempts is decreased

        //call answerTransactionRequestChallenge again, give a good answer
        answerJson = ChallengeAnswerJSON(id = challenge_id, answer = "12345") //good answer, not a number
        request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@ (user1)
        response = makePostRequest(request, write(answerJson))
        Then("we should get a 202 accepted code")
        response.code should equal(202)

        //check if returned data includes new transaction's id
        status = (response.body \ "status") match {
          case JString(i) => i
          case _ => ""
        }
        status should equal(code.transactionrequests.TransactionRequests.STATUS_COMPLETED)

        var transaction_ids = (response.body \ "transaction_ids") match {
          case JString(i) => i
          case JArray(i) => i
          case _ => ""
        }
        transaction_ids should not equal ("")

        //call getTransactionRequests, check that we really created a transaction request
        request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transaction-requests").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)
        transactionRequests = response.body.children

        transactionRequests.size should not equal (0)

        //check transaction_ids again
        transaction_ids = (response.body \ "transaction_requests_with_charges" \ "transaction_ids") match {
          case JString(i) => i
          case JArray(i) => i
          case _ => ""
        }
        transaction_ids should not equal ("")

        //make sure that we also get no challenges back from this url (after getting from db)
        challenge = (response.body \ "transaction_requests_with_charges" \ "challenge").children
        challenge.size should not equal (0)

        //check that we created a new transaction (since no challenge)
        request = (v1_4Request / "banks" / testBank.bankId.value / "accounts" / fromAccount.accountId.value /
          "owner" / "transactions").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)

        val fromTransactions = response.body.children

        fromTransactions.size should equal(1)

        //check that the description has been set
        val description = (((response.body \ "transactions") (0) \ "details") \ "description") match {
          case JString(i) => i
          case _ => ""
        }
        description should not equal ("")

        // Transaction Value
        val actualFromAmount = (((response.body \ "transactions") (0) \ "details") \ "value" \ "amount") match {
          case JString(i) => i
          case _ => ""
        }

        // We are debiting the amount
        amt should equal(-1 * BigDecimal(actualFromAmount))

        // New Balance
        val actualFromBalance = (((response.body \ "transactions") (0) \ "details") \ "new_balance" \ "amount") match {
          case JString(i) => i
          case _ => ""
        }
        expectedFromNewBalance should equal(BigDecimal(actualFromBalance))

        //check that we created a new transaction
        request = (v2_1Request / "banks" / testBank.bankId.value / "accounts" / toAccount.accountId.value /
          "owner" / "transactions").GET <@ (user1)
        response = makeGetRequest(request)

        Then("we should get a 200 ok code")
        response.code should equal(200)

        val toTransactions = response.body.children

        toTransactions.size should equal(1)

        //check that the description has been set
        val toDescription = (((response.body \ "transactions") (0) \ "details") \ "description") match {
          case JString(i) => i
          case _ => ""
        }
        description should not equal ("")

        // Transaction Value
        val actualToAmount = (((response.body \ "transactions") (0) \ "details") \ "value" \ "amount") match {
          case JString(i) => i
          case _ => ""
        }
        expectedAmtTo should equal(BigDecimal(actualToAmount))

        // New Balance
        val actualToBalance = (((response.body \ "transactions") (0) \ "details") \ "new_balance" \ "amount") match {
          case JString(i) => i
          case _ => ""
        }
        expectedToNewBalance should equal(BigDecimal(actualToBalance))


        val rate = fx.exchangeRate(fromAccount.currency, toAccount.currency)
        val convertedAmount = fx.convert(amt, rate)
        val fromAccountBalance = getFromAccount.balance
        And("the from account should have a balance smaller by the original amount specified to pay")
        fromAccountBalance should equal(beforeFromBalance - amt)


        //val fromAccountBalance = getFromAccount.balance
        //And("the from account should have a balance smaller by the amount specified to pay")
        //fromAccountBalance should equal((beforeFromBalance - amt))

        /*
        And("the newest transaction for the account receiving the payment should have the proper amount")
        newestToAccountTransaction.details.value.amount should equal(amt.toString)
        */

        And("the account receiving the payment should have a new balance plus the amount paid")
        val toAccountBalance = getToAccount.balance
        toAccountBalance should equal(beforeToBalance + convertedAmount)

        And("there should now be 2 new transactions in the database (one for the sender, one for the receiver)")
        transactionCount(fromAccount, toAccount) should equal(totalTransactionsBefore + 2)
      }
    }






    /*
    scenario("we can't make a payment without access to the owner view", Payments) {
      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId

      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("12.33")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user2)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    }

    scenario("we can't make a payment without an oauth user", Payments) {
      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("12.33")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, None)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    }

    scenario("we can't make a payment of zero units of currency", Payments) {
      When("we try to make a payment with amount = 0")

      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("0")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user1)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    }

    scenario("we can't make a payment with a negative amount of money", Payments) {

      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      val acc1 = createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      val acc2  = createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "EUR")

      When("we try to make a payment with amount < 0")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("-20.30")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user1)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    }

    scenario("we can't make a payment to an account that doesn't exist", Payments) {

      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val acc1 = createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")

      When("we try to make a payment to an account that doesn't exist")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      val fromAccount = getFromAccount

      val totalTransactionsBefore = transactionCount(fromAccount)

      val beforeFromBalance = fromAccount.balance

      val amt = BigDecimal("17.30")

      val payJson = MakePaymentJson(bankId.value, "ACCOUNTTHATDOESNOTEXIST232321321", amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user1)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for the sender's account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount))

      And("the balance of the sender's account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
    }

    scenario("we can't make a payment between accounts with different currencies", Payments) {
      When("we try to make a payment to an account that has a different currency")
      val testBank = createPaymentTestBank()
      val bankId = testBank.bankId
      val accountId1 = AccountId("__acc1")
      val accountId2 = AccountId("__acc2")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId1, "EUR")
      createAccountAndOwnerView(Some(obpuser1), bankId, accountId2, "GBP")

      def getFromAccount : BankAccount = {
        BankAccount(bankId, accountId1).getOrElse(fail("couldn't get from account"))
      }

      def getToAccount : BankAccount = {
        BankAccount(bankId, accountId2).getOrElse(fail("couldn't get to account"))
      }

      val fromAccount = getFromAccount
      val toAccount = getToAccount

      val totalTransactionsBefore = transactionCount(fromAccount, toAccount)

      val beforeFromBalance = fromAccount.balance
      val beforeToBalance = toAccount.balance

      val amt = BigDecimal("4.95")

      val payJson = MakePaymentJson(toAccount.bankId.value, toAccount.accountId.value, amt.toString)
      val postResult = postTransaction(fromAccount.bankId.value, fromAccount.accountId.value, view, payJson, user1)

      Then("we should get a 400")
      postResult.code should equal(400)

      And("the number of transactions for each account should remain unchanged")
      totalTransactionsBefore should equal(transactionCount(fromAccount, toAccount))

      And("the balances of each account should remain unchanged")
      beforeFromBalance should equal(getFromAccount.balance)
      beforeToBalance should equal(getToAccount.balance)
    } */
  }
}

package code.api.v3_0_0

import code.api.util.APIUtil.OAuth._
import code.api.ErrorMessage
import code.api.util.{APIUtil, ErrorMessages}
import code.api.util.ErrorMessages.{FirehoseViewsNotAllowedOnThisInstance, UserHasMissingRoles}
import code.api.util.ApiRole.CanUseFirehoseAtAnyBank
import net.liftweb.json.JsonAST.compactRender
import org.scalatest.Tag

class TransactionsTest extends V300ServerSetup {

  /************************* test tags ************************/
  /**
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude=getPermissions
    *
    *  This is made possible by the scalatest maven plugin
    */
  object API300 extends Tag("api3.0.0")
  object GetTransactions extends Tag("getTransactions")
  object GetTransactionsWithParams extends Tag("getTransactionsWithParams")
  
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





  feature("transactions with params"){
    import java.text.SimpleDateFormat
    import java.util.{Calendar, Date}

    val defaultFormat = APIUtil.DateWithMsFormat
    val rollbackFormat = APIUtil.DateWithMsRollbackFormat

    scenario("we don't get transactions due to wrong value for sort_direction parameter", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with a wrong value for param sort_direction")
      val params = ("sort_direction", "foo") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("error should be " + ErrorMessages.FilterSortDirectionError)
      reply.body.extract[ErrorMessage].error should equal (ErrorMessages.FilterSortDirectionError)
    }
    scenario("we get all the transactions sorted by ASC", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with the value ASC for param sort_direction")
      val params = ("sort_direction", "ASC") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val transactions = (reply.body).extract[TransactionsJsonV300]
      And("transactions array should not be empty")
      transactions.transactions.size should not equal (0)
      val transaction1 = transactions.transactions(0)
      val transaction2 = transactions.transactions(1)
      transaction1.details.completed.before(transaction2.details.completed) should equal(true)
    }
    scenario("we get all the transactions sorted by asc", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with the value asc for param sort_direction")
      val params = ("sort_direction", "asc") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val transactions = reply.body.extract[TransactionsJsonV300]
      And("transactions array should not be empty")
      transactions.transactions.size should not equal (0)
      val transaction1 = transactions.transactions(0)
      val transaction2 = transactions.transactions(1)
      transaction1.details.completed.before(transaction2.details.completed) should equal(true)
    }
    scenario("we get all the transactions sorted by DESC", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with the value DESC for param sort_direction")
      val params = ("sort_direction", "DESC") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val transactions = reply.body.extract[TransactionsJsonV300]
      And("transactions array should not be empty")
      transactions.transactions.size should not equal (0)
      val transaction1 = transactions.transactions(0)
      val transaction2 = transactions.transactions(1)
      transaction1.details.completed.before(transaction2.details.completed) should equal(false)
    }
    scenario("we get all the transactions sorted by desc", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with the value desc for param sort_direction")
      val params = ("sort_direction1", "desc") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val transactions = reply.body.extract[TransactionsJsonV300]
      And("transactions array should not be empty")
      transactions.transactions.size should not equal (0)
      val transaction1 = transactions.transactions(0)
      val transaction2 = transactions.transactions(1)
      transaction1.details.completed.before(transaction2.details.completed) should equal(false)

    }
    scenario("we don't get transactions due to wrong value (not a number) for limit parameter", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with a wrong value for param limit")
      val params = ("limit", "foo") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("error should be " + ErrorMessages.FilterLimitError)
      reply.body.extract[ErrorMessage].error should equal (ErrorMessages.FilterLimitError)
    }
    scenario("we don't get transactions due to wrong value (0) for limit parameter", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with a wrong value for param limit")
      val params = ("limit", "0") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("error should be " + ErrorMessages.FilterLimitError)
      reply.body.extract[ErrorMessage].error should equal (ErrorMessages.FilterLimitError)
    }
    scenario("we don't get transactions due to wrong value (-100) for limit parameter", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with a wrong value for param limit")
      val params = ("limit", "-100") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("error should be " + ErrorMessages.FilterLimitError)
      reply.body.extract[ErrorMessage].error should equal (ErrorMessages.FilterLimitError)
    }
    scenario("we get only 5 transactions due to the limit parameter value", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with the value ASC for parameter limit")
      val params = ("limit", "5") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val transactions = reply.body.extract[TransactionsJsonV300]
      And("transactions size should be equal to 5")
      transactions.transactions.size should equal (5)
    }
    scenario("we don't get transactions due to wrong value for from_date parameter", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with a wrong value for param from_date")
      val params = ("from_date", "foo") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("error should be " + ErrorMessages.FilterDateFormatError)
      reply.body.extract[ErrorMessage].error should equal (ErrorMessages.FilterDateFormatError)
    }
    scenario("we get transactions from a previous date with the right format", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with from_date into a proper format")
      val currentDate = new Date()
      val calendar = Calendar.getInstance
      calendar.setTime(currentDate)
      calendar.add(Calendar.YEAR, -1)
      val pastDate = calendar.getTime
      val formatedPastDate = defaultFormat.format(pastDate)
      val params = ("from_date", formatedPastDate) :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("transactions size should not be empty")
      val transactions = reply.body.extract[TransactionsJsonV300]
      transactions.transactions.size should not equal (0)
    }
    scenario("we get transactions from a previous date (from_date) with the fallback format", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with from_date into an accepted format")
      val currentDate = new Date()
      val calendar = Calendar.getInstance
      calendar.setTime(currentDate)
      calendar.add(Calendar.YEAR, -1)
      val pastDate = calendar.getTime
      val formatedPastDate = rollbackFormat.format(pastDate)
      val params = ("from_date", formatedPastDate) :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("transactions size should not be empty")
      val transactions = reply.body.extract[TransactionsJsonV300]
      transactions.transactions.size should not equal (0)
    }
    scenario("we don't get transactions from a date in the future", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with from_date into a proper format")
      val currentDate = new Date()
      val calendar = Calendar.getInstance
      calendar.setTime(currentDate)
      calendar.add(Calendar.YEAR, 1)
      val futureDate = calendar.getTime
      val formatedFutureDate = defaultFormat.format(futureDate)
      val params = ("from_date", formatedFutureDate) :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("transactions size should not be empty")
      val transactions = reply.body.extract[TransactionsJsonV300]
      transactions.transactions.size should equal (0)
    }
    scenario("we don't get transactions due to wrong value for to_date parameter", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with a wrong value for param to_date")
      val params = ("to_date", "foo") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("error should be " + ErrorMessages.FilterDateFormatError)
      reply.body.extract[ErrorMessage].error should equal (ErrorMessages.FilterDateFormatError)
    }
    scenario("we get transactions from a previous (to_date) date with the right format", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with to_date into a proper format")
      val currentDate = new Date()
      val formatedCurrentDate = defaultFormat.format(currentDate)
      val params = ("to_date", formatedCurrentDate) :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("transactions size should not be empty")
      val transactions = reply.body.extract[TransactionsJsonV300]
      transactions.transactions.size should not equal (0)
    }
    scenario("we get transactions from a previous date with the fallback format", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with тo_date into an accepted format")
      val currentDate = new Date()
      val formatedCurrentDate = defaultFormat.format(currentDate)
      val params = ("to_date", formatedCurrentDate) :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("transactions size should not be empty")
      val transactions = reply.body.extract[TransactionsJsonV300]
      transactions.transactions.size should not equal (0)
    }
    scenario("we don't get transactions from a date in the past", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with to_date into a proper format")
      val currentDate = new Date()
      val calendar = Calendar.getInstance
      calendar.setTime(currentDate)
      calendar.add(Calendar.YEAR, -1)
      val pastDate = calendar.getTime
      val formatedPastDate = defaultFormat.format(pastDate)
      val params = ("to_date", formatedPastDate) :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("transactions size should be empty")
      val transactions = reply.body.extract[TransactionsJsonV300]
      transactions.transactions.size should equal (0)
    }
    scenario("we don't get transactions due to wrong value (not a number) for offset parameter", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with a wrong value for param offset")
      val params = ("offset", "foo") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("error should be " + ErrorMessages.FilterOffersetError)
      reply.body.extract[ErrorMessage].error should equal (ErrorMessages.FilterOffersetError)
    }
    scenario("we don't get transactions due to the (2000) for offset parameter", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with a wrong value for param offset")
      val params = ("offset", "2000") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 code")
      reply.code should equal (200)
      And("transactions size should be empty")
      val transactions = reply.body.extract[TransactionsJsonV300]
      transactions.transactions.size should equal (0)
    }
    scenario("we don't get transactions due to wrong value (-100) for offset parameter", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with a wrong value for param offset")
      val params = ("offset", "-100") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("error should be " + ErrorMessages.FilterOffersetError)
      reply.body.extract[ErrorMessage].error should equal (ErrorMessages.FilterOffersetError)
    }
    scenario("we get only 5 transactions due to the offset parameter value", API300, GetTransactions, GetTransactionsWithParams) {
      Given("We will use an access token")
      val bankId = randomBankId
      val bankAccount = randomPrivateAccount(bankId)
      val view = randomViewPermalink(bankId, bankAccount)
      When("the request is sent with the value ASC for parameter offset")
      val params = ("offset", "5") :: Nil
      val reply = getTransactions(bankId,bankAccount.id,view, user1, params)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val transactions = reply.body.extract[TransactionsJsonV300]
      And("transactions size should be equal to 5")
      transactions.transactions.size should equal (5)
    }
  }

  feature("Assuring that entitlement requirements are checked for transaction(s) related endpoints") {

    scenario("We try to get firehose transactions without required role " + CanUseFirehoseAtAnyBank){

      When("We have to find it by endpoint getFirehoseTransactionsForBankAccount")
      val requestGet = (v3_0Request / "banks" / "BANK_ID" / "firehose" / "accounts" /  "AccountId(accountId)" / "views" / "ViewId(viewId)" / "transactions").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      And("We should get a 403")
      responseGet.code should equal(403)
      compactRender(responseGet.body \ "error").replaceAll("\"", "") should equal(FirehoseViewsNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseFirehoseAtAnyBank  )
    }}






}

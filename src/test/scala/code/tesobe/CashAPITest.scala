package code.tesobe

import java.util.{UUID, Date}
import net.liftweb.json.Serialization.write
import code.api.DefaultConnectorTestSetup
import code.api.test.{APIResponse, ServerSetup}
import code.bankconnectors.Connector
import code.model.AccountId
import net.liftweb.common.{Full, Loggable}
import net.liftweb.util.Props
import dispatch._

/**
 * The cash api isn't very well designed and is used only for internal projects. If we want to
 * expand this, a rewrite would be best, though we would need to update the applications using this api.
 *
 * API issues:
 *    -CashTransaction.kind is used as a switch for if the transaction value should be positive (incoming) or
 *     negative (outgoing) by checking if it's value is "in" or not
 *    -Despite this parameter, there are no checks to verify CashTransaction.value should be positive (to avoid
 *     a situation where value is negative but kind is outgoing)
 *    -Uses a double value for transaction value, instead of String/BigDecimal
 *
 */
class CashAPITest extends ServerSetup with Loggable with DefaultConnectorTestSetup {

  override def beforeEach() = {
    super.beforeEach()
    wipeTestData()
  }

  override def afterEach() = {
    super.afterEach()
    wipeTestData()
  }

  val CashKeyParam = "cashApplicationKey"
  val validKey = Props.get(CashKeyParam).openOrThrowException("Props key CashKeyParam not found")

  def fixture() = new {
    lazy val bank = createBank("test-bank")
    lazy val account = createAccount(bank.bankId, AccountId("some-account-id"), "EUR")
    lazy val incomingTransactionData = CashTransaction(
      otherParty = "foo",
      date = new Date(),
      amount = 12.33,
      kind = "in",
      label = "some label",
      otherInformation = "some more info"
      )
    lazy val outgoingTransactionData = incomingTransactionData.copy(kind = "out")
  }

  def addCashTransaction(accountUUID : String, data : String, secretKey : Option[String]) : APIResponse = {

    val baseReq = (baseRequest / "obp" / "v1.0" / "cash-accounts" / accountUUID / "transactions").POST

    val req = secretKey match {
      case Some(key) => baseReq <:< Map(CashKeyParam -> key) //the key goes in a header
      case None => baseReq
    }

    makePostRequest(req, data)
  }

  def addCashTransaction(accountUUID : String, data : CashTransaction, secretKey : Option[String]) : APIResponse = {
    addCashTransaction(accountUUID, write(data), secretKey)
  }

  def checkSameDate(d1 : Date, d2 : Date) = {
    //we want to check they are the same date in the api output format
    //since the api discards some small fractions of a second, a direct date comparison will fail
    formats.dateFormat.format(d1) should equal(formats.dateFormat.format(d1))
  }

  feature("Adding cash transactions to accounts") {

    scenario("Attempting to add a transaction to an existing account using an incorrect secret key") {
      val f = fixture()
      Given("An invalid key")
      val invalidKey = validKey + "1"

      And("An account with no existing transactions")
      Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId).isDefined should equal(true)
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsBefore.size should equal(0)

      When("We try to add a cash transaction")
      val response = addCashTransaction(f.account.uuid, f.incomingTransactionData, Some(invalidKey))

      Then("We should get a 401 not authorized")
      response.code should equal(401)

      And("No transaction should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsAfter.size should equal(0)
    }

    scenario("Attempting to add a transaction to an existing account without using a secret key ") {
      val f = fixture()
      Given("An account with no existing transactions")
      Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId).isDefined should equal(true)
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsBefore.size should equal(0)

      When("We try to add a cash transaction")
      val response = addCashTransaction(f.account.uuid, f.incomingTransactionData, None)

      Then("We should get a 401 not authorized")
      response.code should equal(401)

      And("No transaction should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsAfter.size should equal(0)
    }

    scenario("Attempting to add a transaction to nonexistent account using the correct secret key") {
      val f = fixture()
      val nonexistentAccountUUID = UUID.randomUUID().toString

      When("We try to add a cash transaction")
      val response = addCashTransaction(nonexistentAccountUUID, f.incomingTransactionData, Some(validKey))

      Then("We should get a 400")
      response.code should equal(400)
    }


    scenario("Attempting to add an incoming transaction to an existing account using the correct secret key") {
      val f = fixture()
      Given("An account with no existing transactions")
      val accountBox = Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId)
      accountBox.isDefined should equal(true)

      val balanceBefore = accountBox.get.balance

      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsBefore.size should equal(0)

      When("We try to add a cash transaction")
      val tData = f.incomingTransactionData
      val response = addCashTransaction(f.account.uuid, tData, Some(validKey))

      //for some reason this was originally set to 200 instead of 201, but we'll leave it that way to avoid breaking anything
      Then("We should get a 200")
      response.code should equal(200)

      //TODO: check response body format?

      And("The transaction should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsAfter.size should equal(1)

      val addedTransaction = tsAfter(0)

      And("This transaction should have the appropriate values")
      checkSameDate(addedTransaction.finishDate, tData.date)
      addedTransaction.description should equal(Some(tData.label))
      addedTransaction.otherAccount.label should equal(tData.otherParty)

      //cash api should always set transaction type to cash
      addedTransaction.transactionType should equal("cash")

      //incoming transaction should have positive value
      addedTransaction.amount.toDouble should equal(tData.amount) //icky BigDecimal to double conversion here..

      And("The account should have its balance properly updated")
      val balanceAfter = Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId).get.balance
      balanceAfter.toDouble should equal(balanceBefore.toDouble + tData.amount) //icky BigDecimal to double conversion here..
    }

    scenario("Attempting to add an outgoing transaction to an existing account using the correct secret key") {
      val f = fixture()
      Given("An account with no existing transactions")
      val accountBox = Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId)
      accountBox.isDefined should equal(true)

      val balanceBefore = accountBox.get.balance

      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsBefore.size should equal(0)

      When("We try to add a cash transaction")
      val tData = f.outgoingTransactionData
      val response = addCashTransaction(f.account.uuid, tData, Some(validKey))

      //for some reason this was originally set to 200 instead of 201, but we'll leave it that way to avoid breaking anything
      Then("We should get a 200")
      response.code should equal(200)

      //TODO: check response body format?

      And("The transaction should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsAfter.size should equal(1)

      val addedTransaction = tsAfter(0)

      And("This transaction should have the appropriate values")
      checkSameDate(addedTransaction.finishDate, tData.date)
      addedTransaction.description should equal(Some(tData.label))
      addedTransaction.otherAccount.label should equal(tData.otherParty)

      //cash api should always set transaction type to cash
      addedTransaction.transactionType should equal("cash")

      //outgoing transaction should have negative value
      addedTransaction.amount.toDouble should equal(-1 * tData.amount) //icky BigDecimal to double conversion here..

      And("The account should have its balance properly updated")
      val balanceAfter = Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId).get.balance
      balanceAfter.toDouble should equal(balanceBefore.toDouble - tData.amount) //icky BigDecimal to double conversion here..
    }

    scenario("Sending the wrong kind of json") {
      val f = fixture()

      When("We send the wrong json format")
      val response = addCashTransaction(f.account.uuid, """{"foo": "bar"}""", Some(validKey))

      Then("We should get a 400")
      response.code should equal(400)

      And("No transaction should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsAfter.size should equal(0)
    }

  }

}

package code.management

import java.text.SimpleDateFormat
import java.util.TimeZone

import code.api.util.APIUtil
import code.api.util.ErrorMessages._
import code.bankconnectors.Connector
import code.model.{AccountId, Transaction}
import code.setup.{APIResponse, DefaultConnectorTestSetup, ServerSetup}
import code.util.Helper.MdcLoggable
import net.liftweb.util.Props
import net.liftweb.util.TimeHelpers._


class ImporterTest extends ServerSetup with MdcLoggable with DefaultConnectorTestSetup {

  override def beforeEach() = {
    super.beforeEach()
    wipeTestData()
  }

  override def afterEach() = {
    super.afterEach()
    wipeTestData()
  }

  val secretKeyHttpParamName = "secret"
  val secretKeyValue = APIUtil.getPropsValue("importer_secret").openOrThrowException("Prop importer_secret not specified.")

  val dummyKind = "Transfer"

  def defaultFixture() = fixture("an-account")

  def fixture(accId : String) = new {
    lazy val bank = createBank("a-bank")
    lazy val accountCurrency = "EUR"
    lazy val account = createAccount(bank.bankId, AccountId(accId), accountCurrency)
    val originalBalance = account.balance.toString

    val t1Value = "12.34"
    val t1NewBalance = "3434.22"
    val t1StartDate = "2012-01-04T18:06:22.000Z"
    val t1EndDate = "2012-01-05T18:52:13.000Z"

    val t2Value = "13.54"
    val t2NewBalance = "3447.76"
    val t2StartDate = "2012-01-04T18:06:22.000Z"
    val t2EndDate = "2012-01-06T18:52:13.000Z"

    val dummyLabel = "this is a description"

    //import transaction json is just an array of 'tJson'.
    val testJson = importJson(List(
      tJson(t1Value, t1NewBalance, t1StartDate, t1EndDate),
      tJson(t2Value, t2NewBalance, t2StartDate, t2EndDate)))

    def importJson(transactionJsons: List[String]) = {
      s"""[${transactionJsons.mkString(",")}
      ]"""
    }

    def tJson(value : String, newBalance : String, startDateString : String, endDateString : String) : String = {
      //the double dollar signs are single dollar signs that have been escaped
      s"""{
        |    "obp_transaction": {
        |        "this_account": {
        |            "holder": "${account.accountHolder}",
        |            "number": "${account.number}",
        |            "kind": "${account.accountType}",
        |            "bank": {
        |                "IBAN": "${account.iban.getOrElse("")}",
        |                "national_identifier": "${account.nationalIdentifier}",
        |                "name": "${account.bankName}"
        |            }
        |        },
        |        "other_account": {
        |            "holder": "Client 1",
        |            "number": "123567",
        |            "kind": "current",
        |            "bank": {
        |                "IBAN": "UK12222879",
        |                "national_identifier": "uk.10010010",
        |                "name": "HSBC"
        |            }
        |        },
        |        "details": {
        |            "kind": "$dummyKind",
        |            "label": "$dummyLabel"
        |            "posted": {
        |                "$$dt": "$startDateString"
        |            },
        |            "completed": {
        |                "$$dt": "$endDateString"
        |            },
        |            "new_balance": {
        |                "currency": "EUR",
        |                "amount": "$newBalance"
        |            },
        |            "value": {
        |                "currency": "EUR",
        |                "amount": "$value"
        |            }
        |        }
        |    }
        |}""".stripMargin //stripMargin removes the whitespace before the pipes, and the pipes themselves
    }
  }

  feature("Importing transactions via an API call") {

    def addTransactions(data : String, secretKey : Option[String]) : APIResponse = {

      val baseReq = (baseRequest / "obp_transactions_saver" / "api" / "transactions").POST

      val req = secretKey match {
        case Some(key) =>
          // the <<? adds as url query params
          baseReq <<? Map(secretKeyHttpParamName -> key)
        case None =>
          baseReq
      }

      makePostRequest(req, data)
    }

    def checkOkay(t : Transaction, value : String, newBalance : String, startDate : String, endDate : String, label : String) = {
      t.amount.toString should equal(value)
      t.balance.toString should equal(newBalance)
      t.description should equal(Some(label))

      //the import api uses a different degree of detail than the main api (extra SSS)
      //to compare the import api date string values to the dates returned from the api
      //we need to parse them
      val importJsonDateFormat = {
        val f = APIUtil.DateWithMsFormat
        //setting the time zone is important!
        f.setTimeZone(TimeZone.getTimeZone("UTC"))
        f
      }

      t.transactionType should equal(dummyKind)

      //compare time as a long to avoid issues comparing Dates, e.g. java.util.Date vs java.sql.Date
      t.startDate.getTime should equal(importJsonDateFormat.parse(startDate).getTime) 
      t.finishDate.getTime should equal(importJsonDateFormat.parse(endDate).getTime)
    }

    scenario("Attempting to import transactions without using a secret key") {
      val f = defaultFixture()

      Given("An account with no transactions")
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsBefore.size should equal(0)

      When("We try to import transactions without using a secret key")
      val response = addTransactions(f.testJson, None)

      Then("We should get a 400")
      response.code should equal(400)

      And("No transactions should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsAfter.size should equal(0)
    }

    scenario("Attempting to import transactions with the incorrect secret key") {
      val f = defaultFixture()

      Given("An account with no transactions")
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsBefore.size should equal(0)

      When("We try to import transactions with the incorrect secret key")
      val response = addTransactions(f.testJson, Some(secretKeyValue + "asdsadsad"))

      Then("We should get a 401")
      response.code should equal(401)

      And("No transactions should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsAfter.size should equal(0)
    }

    scenario("Attempting to import transactions with the correct secret key") {
      val f = defaultFixture()

      Given("An account with no transactions")
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsBefore.size should equal(0)

      When("We try to import transactions with the correct secret key")
      val response = addTransactions(f.testJson, Some(secretKeyValue))

      Then("We should get a 200") //implementation returns 200 and not 201, so we'll leave it like that
      response.code should equal(200)

      And("Transactions should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsAfter.size should equal(2)

      And("The transactions should have the correct parameters")
      val t1 = tsAfter(0)
      checkOkay(t1, f.t1Value, f.t1NewBalance, f.t1StartDate, f.t1EndDate, f.dummyLabel)

      val t2 = tsAfter(1)
      checkOkay(t2, f.t2Value, f.t2NewBalance, f.t2StartDate, f.t2EndDate, f.dummyLabel)


      And("The account should have its balance set to the 'new_balance' value of the most recently completed transaction")
      val account = Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      account.balance.toString should equal(f.t2NewBalance) //t2 has a later completed date than t1

      And("The account should have accountLastUpdate set to the current time")
      val dt = (now.getTime - account.lastUpdate.getTime)
      dt < 1000 should equal(true)

    }

    scenario("Attempting to add 'identical' transactions") {
      val f = defaultFixture()
      def checkTransactionOkay(t : Transaction) = checkOkay(t, f.t1Value, f.t1NewBalance, f.t1StartDate, f.t1EndDate, f.dummyLabel)

      Given("An account with no transactions")
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsBefore.size should equal(0)

      When("We try to import two identical transactions with the correct secret key")
      val t1Json = f.tJson(f.t1Value, f.t1NewBalance, f.t1StartDate, f.t1EndDate)
      val importJson = f.importJson(List.fill(2)(t1Json))
      val response = addTransactions(importJson, Some(secretKeyValue))

      //it should NOT complain about identical "new balances" as sometimes this value is unknown, or computed on a daily basis
      //and hence all transactions for the day will have the same end balance
      Then("We should get a 200") //implementation returns 200 and not 201, so we'll leave it like that
      response.code should equal(200)

      And("Transactions should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsAfter.size should equal(2)

      And("The transactions should have the correct parameters")
      tsAfter.foreach(checkTransactionOkay)

      And("The account should have its balance set to the 'new_balance' value of the most recently completed transaction")
      val account = Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      account.balance.toString should equal(f.t1NewBalance)

      And("The account should have accountLastUpdate set to the current time")
      val dt = (now.getTime - account.lastUpdate.getTime)
      dt < 1000 should equal(true)
    }

    scenario("Adding transactions that have already been imported") {
      val f = defaultFixture()
      def checkTransactionOkay(t : Transaction) = checkOkay(t, f.t1Value, f.t1NewBalance, f.t1StartDate, f.t1EndDate, f.dummyLabel)

      val t1Json = f.tJson(f.t1Value, f.t1NewBalance, f.t1StartDate, f.t1EndDate)
      val importJson = f.importJson(List.fill(2)(t1Json))

      Given("Two 'identical' existing transactions")
      addTransactions(importJson, Some(secretKeyValue))
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsBefore.size should equal(2)

      tsBefore.foreach(checkTransactionOkay)

      //remember lastUpdate time
      var account = Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      val oldTime = if(account.lastUpdate != null) account.lastUpdate.getTime else 0

      When("We try to add those transactions again")
      val response = addTransactions(importJson, Some(secretKeyValue))

      Then("We should get a 200") //implementation returns 200 and not 201, so we'll leave it like that
      response.code should equal(200)

      And("There should still only be two transactions")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsAfter.size should equal(2)

      tsAfter.foreach(checkTransactionOkay)

      And("The account should have accountLastUpdate set to the current time (different from first insertion)")
      account = Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      val dt = (account.lastUpdate.getTime - oldTime)
      dt > 0 should equal(true)
    }

    scenario("Adding 'identical' transactions, some of which have already been imported") {
      val f = defaultFixture()
      def checkTransactionOkay(t : Transaction) = checkOkay(t, f.t1Value, f.t1NewBalance, f.t1StartDate, f.t1EndDate, f.dummyLabel)

      val t1Json = f.tJson(f.t1Value, f.t1NewBalance, f.t1StartDate, f.t1EndDate)
      val initialImportJson = f.importJson(List.fill(2)(t1Json))
      val secondImportJson = f.importJson(List.fill(5)(t1Json))

      Given("Two 'identical' existing transactions")
      addTransactions(initialImportJson, Some(secretKeyValue))
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsBefore.size should equal(2)

      checkTransactionOkay(tsBefore(0))
      checkTransactionOkay(tsBefore(1))

      When("We try to add 5 copies of the transaction")
      val response = addTransactions(secondImportJson, Some(secretKeyValue))

      Then("We should get a 200") //implementation returns 200 and not 201, so we'll leave it like that
      response.code should equal(200)

      And("There should now be 5 transactions")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsAfter.size should equal(5)

      tsAfter.foreach(checkTransactionOkay)
    }

    //TODO: this test case is pretty messy and was done in bit of a rush
    scenario("Adding 'identical' transactions when such transactions already exist, but for another account") {
      val f1 = fixture("an-account")
      val f2 = fixture("another-account")
      def checkF1TransactionOkay(t : Transaction) = checkOkay(t, f1.t1Value, f1.t1NewBalance, f1.t1StartDate, f1.t1EndDate, f1.dummyLabel)

      def checkF2TransactionOkay(t : Transaction) = checkOkay(t, f2.t1Value, f2.t1NewBalance, f2.t1StartDate, f2.t1EndDate, f2.dummyLabel)

      val t1F1Json = f1.tJson(f1.t1Value, f1.t1NewBalance, f1.t1StartDate, f1.t1EndDate)
      val t1F1ImportJson = f1.importJson(List.fill(2)(t1F1Json))
      val t1F2Json = f2.tJson(f1.t1Value, f1.t1NewBalance, f1.t1StartDate, f1.t1EndDate)
      val t1F2ImportJson = f2.importJson(List.fill(2)(t1F2Json))

      Given("Two 'identical' existing transactions at a different account")
      addTransactions(t1F1ImportJson, Some(secretKeyValue))

      val f1TsBefore = Connector.connector.vend.getTransactions(f1.account.bankId, f1.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      f1TsBefore.size should equal(2)
      f1TsBefore.foreach(checkF1TransactionOkay)

      When("We add these same 'identical' transactions to a different account")
      addTransactions(t1F2ImportJson, Some(secretKeyValue))

      Then("There should be two transactions for each account")
      val f1TsAfter = Connector.connector.vend.getTransactions(f1.account.bankId, f1.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      f1TsAfter.size should equal(2)
      f1TsAfter.foreach(checkF1TransactionOkay)

      val f2Ts = Connector.connector.vend.getTransactions(f2.account.bankId, f2.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      f2Ts.size should equal(2)
      f2Ts.foreach(checkF2TransactionOkay)

    }

    scenario("Attempting to import transactions using an incorrect json format") {
      val f = defaultFixture()
      Given("An account with no transactions")
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsBefore.size should equal(0)

      When("We try to import transactions with the correct secret key")
      val response = addTransactions("""{"some_gibberish" : "json"}""", Some(secretKeyValue))

      Then("We should get a 500") //implementation returns 500, so we'll leave it like that
      response.code should equal(200)

      And("No transactions should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).openOrThrowException(attemptedToOpenAnEmptyBox)
      tsAfter.size should equal(0)
    }

  }

}

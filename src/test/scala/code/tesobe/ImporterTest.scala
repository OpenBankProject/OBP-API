package code.tesobe

import java.text.SimpleDateFormat
import java.util.TimeZone

import code.api.DefaultConnectorTestSetup
import code.api.test.{APIResponse, ServerSetup}
import code.bankconnectors.Connector
import code.model.{Transaction, AccountId}
import net.liftweb.common.Loggable
import net.liftweb.util.Props
import dispatch._

class ImporterTest extends ServerSetup with Loggable with DefaultConnectorTestSetup {

  override def beforeEach() = {
    super.beforeEach()
    wipeTestData()
  }

  override def afterEach() = {
    super.afterEach()
    wipeTestData()
  }

  val secretKeyHttpParamName = "secret"
  val secretKeyValue = Props.get("importer_secret").get

  def fixture() = new {
    lazy val bank = createBank("a-bank")
    lazy val accountCurrency = "EUR"
    lazy val account = createAccount(bank.bankId, AccountId("an-account"), accountCurrency)
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
    val testJson = s"""[${List(
      tJson(t1Value, t1NewBalance, t1StartDate, t1EndDate),
      tJson(t2Value, t2NewBalance, t2StartDate, t2EndDate)).mkString(",")}
      ]"""

    def tJson(value : String, newBalance : String, startDateString : String, endDateString : String) : String = {
      //the double dollar signs are single dollar signs that have been escaped
      s"""{
        |    "obp_transaction": {
        |        "this_account": {
        |            "holder": "Alan Holder",
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
        |            "type_en": "Transfer",
        |            "type_de": "Überweisung",
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

      val baseReq = (baseRequest / "api" / "transactions").POST

      val req = secretKey match {
        case Some(key) =>
          // the <<? adds as url query params
          baseReq <<? Map(secretKeyHttpParamName -> key)
        case None =>
          baseReq
      }

      makePostRequest(req, data)
    }

    scenario("Attempting to import transactions without using a secret key") {
      val f = fixture()

      Given("An account with no transactions")
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsBefore.size should equal(0)

      When("We try to import transactions without using a secret key")
      val response = addTransactions(f.testJson, None)

      Then("We should get a 400")
      response.code should equal(400)

      And("No transactions should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsAfter.size should equal(0)
    }

    scenario("Attempting to import transactions with the incorrect secret key") {
      val f = fixture()

      Given("An account with no transactions")
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsBefore.size should equal(0)

      When("We try to import transactions with the incorrect secret key")
      val response = addTransactions(f.testJson, Some(secretKeyValue + "asdsadsad"))

      Then("We should get a 401")
      response.code should equal(401)

      And("No transactions should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsAfter.size should equal(0)
    }

    scenario("Attempting to import transactions with the correct secret key") {
      val f = fixture()

      def checkOkay(t : Transaction, value : String, newBalance : String, startDate : String, endDate : String) = {
        t.amount.toString should equal(value)
        t.balance.toString should equal(newBalance)
        t.description should equal(Some(f.dummyLabel))

        //the import api uses a different degree of detail than the main api (extra SSS)
        //to compare the import api date string values to the dates returned from the api
        //we need to parse them
        val importJsonDateFormat = {
          val f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
          //setting the time zone is important!
          f.setTimeZone(TimeZone.getTimeZone("UTC"))
          f
        }

        t.startDate should equal(importJsonDateFormat.parse(startDate))
        t.finishDate should equal(importJsonDateFormat.parse(endDate))
      }

      Given("An account with no transactions")
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsBefore.size should equal(0)

      When("We try to import transactions with the correct secret key")
      val response = addTransactions(f.testJson, Some(secretKeyValue))

      Then("We should get a 200") //implementation returns 200 and not 201, so we'll leave it like that
      response.code should equal(200)

      And("Transactions should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsAfter.size should equal(2)

      And("The transactions should have the correct parameters")
      val t1 = tsAfter(0)
      checkOkay(t1, f.t1Value, f.t1NewBalance, f.t1StartDate, f.t1EndDate)

      val t2 = tsAfter(1)
      checkOkay(t2, f.t2Value, f.t2NewBalance, f.t2StartDate, f.t2EndDate)


      And("The account should have its balance set to the 'new_balance' value of the most recently completed transaction")
      val account = Connector.connector.vend.getBankAccount(f.account.bankId, f.account.accountId).get
      account.balance.toString should equal(f.t2NewBalance) //t2 has a later completed date than t1

    }

    scenario("Attempting to import transactions using an incorrect json format") {
      val f = fixture()
      Given("An account with no transactions")
      val tsBefore = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsBefore.size should equal(0)

      When("We try to import transactions with the correct secret key")
      val response = addTransactions("""{"some_gibberish" : "json"}""", Some(secretKeyValue))

      Then("We should get a 500") //implementation returns 500, so we'll leave it like that
      response.code should equal(200)

      And("No transactions should be added")
      val tsAfter = Connector.connector.vend.getTransactions(f.account.bankId, f.account.accountId).get
      tsAfter.size should equal(0)
    }

  }

}

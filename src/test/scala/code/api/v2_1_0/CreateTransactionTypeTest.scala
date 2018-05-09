package code.api.v2_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateAnyTransactionRequest, CanCreateTransactionType, CanGetEntitlementsForAnyUserAtAnyBank, CanGetEntitlementsForAnyUserAtOneBank}
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.api.v2_0_0.{CreateAccountJSON, TransactionTypeJsonV200}
import code.entitlement.Entitlement
import code.model.dataAccess.MappedBankAccount
import code.model.{AmountOfMoney, BankId, TransactionTypeId}
import code.setup.DefaultUsers
import code.transaction_types.MappedTransactionType
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization._
import net.liftweb.util.TimeHelpers._
import org.scalatest.BeforeAndAfter

/**
  * Created by zhanghongwei on 17/11/16.
  */
class CreateTransactionTypeTest extends V210ServerSetup with DefaultUsers {

  lazy val transactionTypeJSON = TransactionTypeJsonV200(
    TransactionTypeId("1"), //mockTransactionTypeId,
    "1", //mockBankId.value,
    "1", //short_code
    "This is for test ", //summary,
    "Many data here", //description,
    AmountOfMoneyJsonV121("EUR", "0"))

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
    MappedTransactionType.bulkDelete_!!()
  }

  feature("Assuring that endpoint 'Create Transaction Type at bank' works as expected - v2.1.0") {

    scenario("We try to put data without Authentication - Create Transaction Type...") {
      When("We make the request")
      val requestPut = (v2_1Request / "banks" / testBankId1.value / "transaction-types").PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(transactionTypeJSON))
      Then("We should get a 400")
      responsePut.code should equal(400)
      val error = for {JObject(o) <- responsePut.body; JField("error", JString(error)) <- o} yield error
      And("We should get a message: " + ErrorMessages.InsufficientAuthorisationToCreateTransactionType)
      error should contain(ErrorMessages.InsufficientAuthorisationToCreateTransactionType)
    }

    scenario("We try to get all roles with Authentication - Create Transaction Type...") {
      Given("The Authentication")
      setCanCreateTransactionType

      When("We make the request")
      val requestPut = (v2_1Request / "banks" / testBankId1.value / "transaction-types").PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(transactionTypeJSON))

      And("We should get a 200")
      responsePut.code should equal(200)
    }
  }

  feature("Assuring We pass the Authentication - Create Transaction Type... - v2.1.0") {

    scenario("We try to insert and update data, call 'Create Transaction Type offered by the bank' correctly ") {
      Given("The Authentication")
      setCanCreateTransactionType

      Then("We make the request")
      val requestPut1 = (v2_1Request / "banks" / testBankId1.value / "transaction-types").PUT <@ (user1)
      val responsePut1 = makePutRequest(requestPut1, write(transactionTypeJSON))

      And("We should get a 200")
      responsePut1.code should equal(200)

      Then("update input value and We make the request")
      lazy val transactionTypeJSON2 = TransactionTypeJsonV200(
        TransactionTypeId("1"), //mockTransactionTypeId,
        "1", //mockBankId.value,
        "1", //short_code
        "change here  ", //summary,
        "Many data here", //description,
        AmountOfMoneyJsonV121("EUR", "0"))

      val requestPut = (v2_1Request / "banks" / testBankId1.value / "transaction-types").PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(transactionTypeJSON2))

      And("We should get a 200")
      responsePut.code should equal(200)
    }

    scenario("We try to insert and update error, call 'Create Transaction Type offered by the bank' correctly ") {
      Given("The Authentication")
      setCanCreateTransactionType

      Then("insert some data and We make the request")
      val requestPut1 = (v2_1Request / "banks" / testBankId1.value / "transaction-types").PUT <@ (user1)
      val responsePut1 = makePutRequest(requestPut1, write(transactionTypeJSON))

      And("We should get a 200")
      responsePut1.code should equal(200)

      Then("insert new data and We make the request")
      lazy val transactionTypeJSON1 = TransactionTypeJsonV200(
        TransactionTypeId("3"), //mockTransactionTypeId,
        "1", //mockBankId.value,
        "1", //short_code
        "1  ", //summary,
        "1", //description,
        AmountOfMoneyJsonV121("EUR", "0"))

      val requestPut2 = (v2_1Request / "banks" / testBankId1.value / "transaction-types").PUT <@ (user1)
      val responsePut2 = makePutRequest(requestPut2, write(transactionTypeJSON1))

      And("We should get a 400")
      responsePut2.code should equal(400)
      val errorInsert = for {JObject(o) <- responsePut2.body; JField("error", JString(error)) <- o} yield error
      And("We should get a message: " + ErrorMessages.CreateTransactionTypeInsertError)
      errorInsert.toString.contains(ErrorMessages.CreateTransactionTypeInsertError) should be (true)


      Then("insert new data and We make the request")
      lazy val transactionTypeJSON2 = TransactionTypeJsonV200(
        TransactionTypeId("1"), //mockTransactionTypeId,
        "1", //mockBankId.value,
        "1", //short_code
        "1  ", //summary,
        "1", //description,
        AmountOfMoneyJsonV121("EUReeeeeeee", "0"))

      val requestPut3 = (v2_1Request / "banks" / testBankId1.value / "transaction-types").PUT <@ (user1)
      val responsePut3 = makePutRequest(requestPut3, write(transactionTypeJSON2))

      And("We should get a 400")
      responsePut3.code should equal(400)
      val errorUpdate = for {JObject(o) <- responsePut3.body; JField("error", JString(error)) <- o} yield error
      And("We should get a message: " + ErrorMessages.CreateTransactionTypeUpdateError)
      errorInsert.toString.contains(ErrorMessages.CreateTransactionTypeInsertError) should be (true)
    }
  }
  /**
    * set CanCreateTransactionType Entitlements to user1
    */
  def setCanCreateTransactionType: Unit = {
    addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateTransactionType.toString)
    Then("We add entitlement to user1")
    val hasEntitlement = code.api.util.APIUtil.hasEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canCreateTransactionType)
    hasEntitlement should equal(true)
  }
}
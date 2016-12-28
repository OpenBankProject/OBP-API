package code.api.v2_1_0


import code.api.DefaultUsers
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.model.{AccountId, ViewId}
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.json.Serialization.write

class CreateCounterpartyTest extends V210ServerSetup with DefaultUsers {

  val customerPostJSON = PostCounterpartyJSON(
    name = "Company Salary",
    other_bank_id ="gh.29.de",
    other_account_id="007a268b-98bf-44ef-8f6a-9944618378cf",
    other_account_provider="OBP",
    other_account_routing_scheme="IBAN",
    other_account_routing_address="DE12 1234 5123 4510 2207 8077 877",
    other_bank_routing_scheme="BIC",
    other_bank_routing_address="123456",
    is_beneficiary = true
  )

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }

  feature("Assuring that endpoint 'Create counterparty for an account' works as expected - v2.1.0") {

    scenario("There is a user has the owner view and the BankAccount") {

      Given("The user ower access and BankAccount")
      val testBank = createBank("transactions-test-bank")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      val viewId =ViewId("owner")
      val bankAccount = createAccountAndOwnerView(Some(obpuser1), bankId, accountId, "EUR")

      When("We make the request Create counterparty for an account")
      val requestPost = (v2_1Request / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(customerPostJSON))

      Then("We should get a 200 and check all the fields")
      responsePost.code should equal(200)

      var thisAccount = (responsePost.body \ "this_account" \ "bank_id") match {
        case JString(i) => i
        case _ => ""
      }
      thisAccount should equal(bankId.value)

      var accountRoutingAddress = (responsePost.body \ "other_account_routing"  \ "address") match {
        case JString(i) => i
        case _ => ""
      }
      accountRoutingAddress should  equal(customerPostJSON.other_account_routing_address)

      var bankRoutingScheme = (responsePost.body \ "other_bank_routing" \ "scheme" ) match {
        case JString(i) => i
        case _ => ""
      }
      bankRoutingScheme should  equal(customerPostJSON.other_bank_routing_scheme)
    }

    scenario("No BankAccount in Database") {
      Given("The user ,but no BankAccount")

      val testBank = createBank("transactions-test-bank")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      val viewId =ViewId("owner")
      val ownerView = createOwnerView(bankId, accountId)
      grantAccessToView(obpuser1, ownerView)

      val requestPost = (v2_1Request / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      responsePost.code should equal(400)

      val error = for { JObject(o) <- responsePost.body; JField("error", JString(error)) <- o } yield error
      error  should contain  (ErrorMessages.AccountNotFound)
    }

    scenario("counterparty is not unique for name/bank_id/account_id/view_id") {
      Given("The user ower access and BankAccount")
      val testBank = createBank("transactions-test-bank")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      val viewId =ViewId("owner")
      val bankAccount = createAccountAndOwnerView(Some(obpuser1), bankId, accountId, "EUR")

      When("We make the request Create counterparty for an account")
      val requestPost = (v2_1Request / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      var responsePost = makePostRequest(requestPost, write(customerPostJSON))

      Then("We make the request again, the same name/bank_id/account_id/view_id")
      responsePost = makePostRequest(requestPost, write(customerPostJSON))

      Then("We should get a 400 and check the error massage")
      responsePost.code should equal(400)

      val error = for { JObject(o) <- responsePost.body; JField("error", JString(error)) <- o } yield error
      error  should contain  (ErrorMessages.CounterpartyAlreadyExists)

    }
  }

}

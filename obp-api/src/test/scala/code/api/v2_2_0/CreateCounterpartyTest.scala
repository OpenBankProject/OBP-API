package code.api.v2_2_0

import code.api.Constant._
import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.setup.DefaultUsers
import code.views.Views
import com.openbankproject.commons.model.{AccountId, BankId, ViewId, ViewIdBankIdAccountId}
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization.write

class CreateCounterpartyTest extends V220ServerSetup with DefaultUsers {
  
  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }

  feature("Assuring that endpoint 'Create counterparty for an account' works as expected - v2.1.0") {

    scenario("There is a user has the owner view and the BankAccount") {

      Given("The user owner access and BankAccount")
      val testBank = createBank("transactions-test-bank1")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      val viewId =ViewId(CUSTOM_OWNER_VIEW_ID)


      // Note: The view created below has can_add_counterparty set to true
      // TODO Add a test to test the creation of that permission on a view that doesn't have it, and then try to create the Couterparty
      val bankAccount = createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")
  
      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJSON.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)

      When("We make the request Create counterparty for an account")
      val requestPost = (v2_2Request / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      var accountRoutingAddress = (responsePost.body \  "other_account_routing_address" ) match {
        case JString(i) => i
        case _ => ""
      }
      
      accountRoutingAddress should equal(counterpartyPostJSON.other_account_routing_address)
  
      val counterpartyId = (responsePost.body \  "counterparty_id" ) match {
        case JString(i) => i
        case _ => ""
      }
  
      
      Then("we can test the `Get Counterparty by Id, endpoint`")
      val requestGet = (v2_2Request / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyId ).POST <@ (user1)
      val responseGet = makeGetRequest(requestGet)
  
  
      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)
  
      val accountRoutingAddressGet = (responsePost.body \  "other_account_routing_address" ) match {
        case JString(i) => i
        case _ => ""
      }
  
      accountRoutingAddressGet should equal(counterpartyPostJSON.other_account_routing_address)
  
  
      val counterpartyIdGet = (responsePost.body \  "counterparty_id" ) match {
        case JString(i) => i
        case _ => ""
      }
  
      counterpartyIdGet should equal(counterpartyId)

    }

    scenario("No BankAccount in Database") {
      Given("The user, but no BankAccount")

      val testBank = createBank("transactions-test-bank")
      val bankId = testBank.bankId
      val accountId = AccountId("notExistingAccountId")
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)
      val ownerView = createOwnerView(bankId, accountId)
      Views.views.vend.grantAccessToCustomView(ViewIdBankIdAccountId(viewId, bankId, accountId), resourceUser1)

      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJSON.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)
  
      val requestPost = (v2_2Request / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))
      Then("We should get a 404")
      responsePost.code should equal(404)

      responsePost.body.extract[ErrorMessage].message should startWith(ErrorMessages.BankAccountNotFound)
    }

    scenario("counterparty is not unique for name/bank_id/account_id/view_id") {
      Given("The user owner access and BankAccount")
      val testBank = createBank("transactions-test-bank")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      val viewId =ViewId(CUSTOM_OWNER_VIEW_ID)
      val bankAccount = createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")

      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJSON.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)
  
      When("We make the request Create counterparty for an account")
      val requestPost = (v2_2Request / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      var responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We make the request again, the same name/bank_id/account_id/view_id")
      responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 400 and check the error massage")
      responsePost.code should equal(400)
      
      responsePost.body.extract[ErrorMessage].message should startWith("OBP-30014: Counterparty already exists.")

    }
  }

}

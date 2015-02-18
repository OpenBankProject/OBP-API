package code.api.v1_4_0

import code.api.DefaultUsers
import code.api.util.APIUtil
import code.api.v1_4_0.JSONFactory1_4_0.{AddCustomerMessageJson, CustomerMessagesJson}
import code.customerinfo.{MappedCustomerMessage, MappedCustomerInfo, CustomerInfo}
import code.model.BankId
import dispatch._
import code.api.util.APIUtil.OAuth._
import net.liftweb.json.Serialization.{read, write}

//TODO: API test should be independent of CustomerMessages implementation
class MappedCustomerMessagesTest extends V140ServerSetup with DefaultUsers {

  implicit val format = APIUtil.formats

  val mockBankId = BankId("mockbank1")
  val mockCustomerNumber = "9393490320"

  //TODO: need better tests
  feature("Customer messages") {
    scenario("Getting messages when none exist") {
      Given("No messages exist")
      MappedCustomerMessage.count() should equal(0)

      When("We get the messages")
      val request = (v1_4Request / "banks" / mockBankId.value / "customer" / "messages").GET  <@ user1
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("We should get no messages")
      val json = response.body.extract[CustomerMessagesJson]
      json.messages.size should equal(0)
    }

    scenario("Adding a message") {
      When("We add a message")
      val request = (v1_4Request / "banks" / mockBankId.value / "customer" / mockCustomerNumber / "messages").POST
      val messageJson = AddCustomerMessageJson("some message", "some department", "some person")
      val response = makePostRequest(request, write(messageJson))

      Then("We should get a 201")
      response.code should equal(201)

      And("We should get that message when we do a get messages request ")
      val getMessagesRequest = (v1_4Request / "banks" / mockBankId.value / "customer" / "messages").GET  <@ user1
      val getMessagesResponse = makeGetRequest(getMessagesRequest)
      val json = getMessagesResponse.body.extract[CustomerMessagesJson]
      json.messages.size should equal(1)

      val msg = json.messages(0)
      msg.message should equal(messageJson.message)
      msg.from_department should equal(messageJson.from_department)
      msg.from_person should equal(messageJson.from_person)
      msg.id.nonEmpty should equal(true)
    }
  }


  override def beforeAll(): Unit = {
    super.beforeAll()
    //TODO: this shouldn't be tied to an implementation
    //need to create a customer info obj since the customer messages call needs to find user by customer number
    MappedCustomerInfo.create
      .mBank(mockBankId.value)
      .mUser(obpuser1)
      .mNumber(mockCustomerNumber).save()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    MappedCustomerMessage.bulkDelete_!!()
  }

}

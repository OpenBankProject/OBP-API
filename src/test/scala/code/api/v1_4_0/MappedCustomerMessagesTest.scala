package code.api.v1_4_0

import java.text.SimpleDateFormat

import code.api.DefaultUsers
import code.api.util.APIUtil
import code.api.v1_4_0.JSONFactory1_4_0.{CustomerFaceImageJson, AddCustomerMessageJson, CustomerMessagesJson, CustomerJson}
import code.customer.{MappedCustomerMessage, MappedCustomer, Customer}
import code.model.BankId
import dispatch._
import code.api.util.APIUtil.OAuth._
import net.liftweb.json.Serialization.{read, write}

//TODO: API test should be independent of CustomerMessages implementation
class MappedCustomerMessagesTest extends V140ServerSetup with DefaultUsers {
  implicit val format = APIUtil.formats

  val mockBankId = BankId("testBank1")
  val mockCustomerNumber = "9393490320"
  val mockCustomerId = "uuid-asdfasdfaoiu8u8u8hkjhsf"


  val exampleDateString : String ="22/08/2013"
  val simpleDateFormat : SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
  val exampleDate = simpleDateFormat.parse(exampleDateString)

  //TODO: need better tests
  feature("Customer messages") {
    scenario("Getting messages when none exist") {
      Given("No messages exist")
      MappedCustomerMessage.count() should equal(0)

      When("We get the messages")
      val request = (v1_4Request / "banks" / mockBankId.value / "customer" / "messages").GET <@ user1
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("We should get no messages")
      val json = response.body.extract[CustomerMessagesJson]
      json.messages.size should equal(0)
    }

    scenario("Adding a message") {
      //first add a customer to send message to
      var request = (v1_4Request / "banks" / mockBankId.value / "customer").POST <@ user1
      var customerJson = CustomerJson(
              customer_id = mockCustomerId,
              customer_number = mockCustomerNumber,
              legal_name = "Someone",
              mobile_phone_number = "125245",
              email = "hello@hullo.com",
              face_image = CustomerFaceImageJson("www.example.com/person/123/image.png", exampleDate),
              date_of_birth = exampleDate,
              relationship_status = "Single",
              dependants = 1,
              dob_of_dependants = List(exampleDate),
              highest_education_attained = "Bachelor’s Degree",
              employment_status = "Employed",
              kyc_status = true,
              last_ok_date = exampleDate
      )
      var response = makePostRequest(request, write(customerJson))

      When("We add a message")
      request = (v1_4Request / "banks" / mockBankId.value / "customer" / mockCustomerNumber / "messages").POST <@ user1
      val messageJson = AddCustomerMessageJson("some message", "some department", "some person")
      response = makePostRequest(request, write(messageJson))

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
    MappedCustomer.create
      .mBank(mockBankId.value)
      .mUser(obpuser1)
      .mNumber(mockCustomerNumber).save()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    MappedCustomerMessage.bulkDelete_!!()
  }

}

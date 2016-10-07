package code.api.v1_4_0

import java.text.SimpleDateFormat
import code.api.DefaultUsers
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v1_4_0.JSONFactory1_4_0.{CustomerFaceImageJson, CustomerJson}
import code.api.v2_0_0.{CreateUserCustomerLinkJSON, V200ServerSetup, CreateCustomerJson}
import code.customer.{MappedCustomer}
import code.entitlement.Entitlement
import code.model.{BankId}
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization._
import code.api.util.APIUtil.OAuth._

class CustomerTest extends V200ServerSetup with DefaultUsers {

  val exampleDateString: String = "22/08/2013"
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
  val exampleDate = simpleDateFormat.parse(exampleDateString)

  val mockBankId = BankId("testBank1")
  val mockCustomerNumber = "9393490320"


  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
    MappedCustomer.bulkDelete_!!()
  }


  feature("Assuring that create customer, v1.4.0, feedback and get customer, v1.4.0, feedback are the same") {

    scenario("There is a user, and the bank in questions has customer info for that user - v1.4.0") {
      Given("The bank in question has customer info")
      val testBank = mockBankId

      val customerPostJSON = CreateCustomerJson(
        user_id = obpuser1.userId,
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
      val requestPost = (v1_4Request / "banks" / testBank.value / "customer").POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 200")
      responsePost.code should equal(200)
      And("We should get the right information back")
      val infoPost = responsePost.body.extract[CustomerJson]

      When("We make the request without link user to customer")
      val requestGetWithoutLink = (v1_4Request / "banks" / testBank.value / "customer").GET <@ (user1)
      val responseGetWithoutLink = makeGetRequest(requestGetWithoutLink)
      Then("We should get a 400")
      responseGetWithoutLink.code should equal(400)
      val error = for { JObject(o) <- responseGetWithoutLink.body; JField("error", JString(error)) <- o } yield error
      And("We should get a message: " + ErrorMessages.CustomerDoNotExistsForUser)
      error should contain (ErrorMessages.CustomerDoNotExistsForUser)


      val customerId: String = (responsePost.body \ "customer_id") match {
        case JString(i) => i
        case _ => ""
      }

      When("We link user to customer")
      val uclJSON = CreateUserCustomerLinkJSON(user_id = obpuser1.userId, customer_id = customerId)
      val requestPostUcl = (v2_0Request / "banks" / "user_customer_links").POST <@ (user1)
      val responsePostUcl = makePostRequest(requestPostUcl, write(uclJSON))
      Then("We should get a 400")
      responsePostUcl.code should equal(400)

      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBank.value, obpuser1.userId, ApiRole.CanCreateCustomer.toString)
      val responsePostUclSec = makePostRequest(requestPostUcl, write(uclJSON))
      Then("We should get a 201")
      responsePostUclSec.code should equal(201)


      When("We make the request")
      val requestGet = (v1_4Request / "banks" / testBank.value / "customer").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 200")
      responseGet.code should equal(200)

      And("We should get the right information back")
      val infoGet = responseGet.body.extract[CustomerJson]

      And("POST feedback and GET feedback must be the same")
      infoGet should equal(infoPost)
    }
  }


}

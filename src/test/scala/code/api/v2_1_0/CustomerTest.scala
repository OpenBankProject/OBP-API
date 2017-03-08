package code.api.v2_1_0

import java.text.SimpleDateFormat

import code.api.DefaultUsers
import code.api.util.ApiRole
import code.api.v1_2_1.AmountOfMoneyJSON
import code.api.v1_4_0.JSONFactory1_4_0.CustomerFaceImageJson
import code.customer.Customer
import code.entitlement.Entitlement
import code.model.BankId
import net.liftweb.json.Serialization.write
import code.api.util.APIUtil.OAuth._

class CustomerTest extends V210ServerSetup with DefaultUsers {

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
    Customer.customerProvider.vend.bulkDeleteCustomers()
  }

  feature("Assuring that create customer, v2.0.0, feedback and get customer, v1.4.0, feedback are the same") {

    scenario("There is a user, and the bank in questions has customer info for that user - v2.0.0") {
      Given("The bank in question has customer info")
      val testBank = mockBankId

      val customerPostJSON = PostCustomerJson(
        user_id = authuser1.userId,
        customer_number = mockCustomerNumber,
        legal_name = "Someone",
        mobile_phone_number = "125245",
        email = "hello@hullo.com",
        face_image = CustomerFaceImageJson("www.example.com/person/123/image.png", exampleDate),
        date_of_birth = exampleDate,
        relationship_status = "Single",
        dependants = 1,
        dob_of_dependants = List(exampleDate),
        credit_rating = CustomerCreditRatingJSON(rating = "5", source = "Credit biro"),
        credit_limit = AmountOfMoneyJSON(currency = "EUR", amount = "5000"),
        highest_education_attained = "Bachelor’s Degree",
        employment_status = "Employed",
        kyc_status = true,
        last_ok_date = exampleDate
      )

      val requestPost = (v2_1Request / "banks" / testBank.value / "customers").POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      responsePost.code should equal(400)

      When("We add one required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBank.value, authuser1.userId, ApiRole.CanCreateCustomer.toString)
      val responsePost1 = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      responsePost1.code should equal(400)

      When("We add all required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBank.value, authuser1.userId, ApiRole.CanCreateUserCustomerLink.toString)
      val responsePost2 = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 201")
      responsePost2.code should equal(201)
      And("We should get the right information back")
      val infoPost = responsePost2.body.extract[CustomerJson]

      When("We make the request")
      val requestGet = (v2_1Request / "banks" / testBank.value / "customer").GET <@ (user1)
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

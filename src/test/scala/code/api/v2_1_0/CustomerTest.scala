package code.api.v2_1_0

import java.text.SimpleDateFormat

import code.api.DefaultUsers
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v1_4_0.JSONFactory1_4_0.CustomerFaceImageJson
import code.customer.Customer
import code.entitlement.Entitlement
import code.model.BankId
import net.liftweb.json.Serialization.write
import code.api.util.APIUtil.OAuth._
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.usercustomerlinks.UserCustomerLink
import net.liftweb.json.JsonAST.{JField, JObject, JString}

class CustomerTest extends V210ServerSetup with DefaultUsers {

  val exampleDateString: String = "22/08/2013"
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
  val exampleDate = simpleDateFormat.parse(exampleDateString)

  val mockBankId1 = BankId("testBank1")
  val mockBankId2 = BankId("testBank2")
  val mockCustomerNumber1 = "93934903201"
  val mockCustomerNumber2 = "93934903202"

  def createCustomerJson(customerNumber: String) = {
    PostCustomerJsonV210(
      user_id = authuser1.userId,
      customer_number = customerNumber,
      legal_name = "Someone",
      mobile_phone_number = "125245",
      email = "hello@hullo.com",
      face_image = CustomerFaceImageJson("www.example.com/person/123/image.png", exampleDate),
      date_of_birth = exampleDate,
      relationship_status = "Single",
      dependants = 1,
      dob_of_dependants = List(exampleDate),
      credit_rating = CustomerCreditRatingJSON(rating = "5", source = "Credit biro"),
      credit_limit = AmountOfMoneyJsonV121(currency = "EUR", amount = "5000"),
      highest_education_attained = "Bachelor’s Degree",
      employment_status = "Employed",
      kyc_status = true,
      last_ok_date = exampleDate
    )
  }


  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
    Customer.customerProvider.vend.bulkDeleteCustomers()
    UserCustomerLink.userCustomerLink.vend.bulkDeleteUserCustomerLinks()
  }

  feature("Assuring that create customer, v2.0.0, feedback and get customer, v1.4.0, feedback are the same") {

    scenario("There is a user, and the bank in questions has customer info for that user - v2.0.0") {
      Given("The bank in question has customer info")

      val customerPostJSON = createCustomerJson(mockCustomerNumber1)
      Then("User is linked to 0 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON.user_id).size should equal(0)
      val requestPost = (v2_1Request / "banks" / mockBankId1.value / "customers").POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      responsePost.code should equal(400)

      When("We add one required entitlement")
      Entitlement.entitlement.vend.addEntitlement(mockBankId1.value, authuser1.userId, ApiRole.CanCreateCustomer.toString)
      val responsePost1 = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      responsePost1.code should equal(400)

      When("We add all required entitlement")
      Entitlement.entitlement.vend.addEntitlement(mockBankId1.value, authuser1.userId, ApiRole.CanCreateUserCustomerLink.toString)
      val responsePost2 = makePostRequest(requestPost, write(customerPostJSON))
      println("responsePost2 " + responsePost2)
      Then("We should get a 201")
      responsePost2.code should equal(201)
      And("We should get the right information back")
      val infoPost = responsePost2.body.extract[CustomerJsonV210]

      When("We make the request")
      val requestGet = (v2_1Request / "banks" / mockBankId1.value / "customer").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 200")
      responseGet.code should equal(200)

      And("We should get the right information back")
      val infoGet = responseGet.body.extract[CustomerJsonV210]

      And("POST feedback and GET feedback must be the same")
      infoGet should equal(infoPost)

      And("User is linked to 1 customer")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON.user_id).size should equal(1)


      When("We try to make the second request with same customer number at same bank")
      val secondResponsePost = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      secondResponsePost.code should equal(400)
      val error = for { JObject(o) <- secondResponsePost.body; JField("error", JString(error)) <- o } yield error
      And("We should get a message: " + ErrorMessages.CustomerNumberAlreadyExists)
      error should contain (ErrorMessages.CustomerNumberAlreadyExists)
      And("User is linked to 1 customer")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON.user_id).size should equal(1)

      When("We try to make a request with changed customer number at same bank")
      val customerPostJSON3 = createCustomerJson(mockCustomerNumber2)
      val requestPost3 = (v2_1Request / "banks" / mockBankId1.value / "customers").POST <@ (user1)
      val responsePost3 = makePostRequest(requestPost3, write(customerPostJSON3))
      Then("We should get a 201")
      responsePost3.code should equal(201)
      And("User is linked to 2 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON3.user_id).size should equal(2)

      When("We try to make a request with same customer number at different bank")
      Then("first we add all required entitlements")
      Entitlement.entitlement.vend.addEntitlement(mockBankId2.value, authuser1.userId, ApiRole.CanCreateCustomer.toString)
      Entitlement.entitlement.vend.addEntitlement(mockBankId2.value, authuser1.userId, ApiRole.CanCreateUserCustomerLink.toString)
      val customerPostJSON4 = createCustomerJson(mockCustomerNumber1)
      val requestPost4 = (v2_1Request / "banks" / mockBankId2.value / "customers").POST <@ (user1)
      val responsePost4 = makePostRequest(requestPost4, write(customerPostJSON4))
      Then("We should get a 201")
      responsePost4.code should equal(201)
      And("User is linked to 3 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON4.user_id).size should equal(3)

      When("We try to make the second request with same customer number at same bank")
      val secondResponsePost4 = makePostRequest(requestPost4, write(customerPostJSON4))
      Then("We should get a 400")
      secondResponsePost4.code should equal(400)
      val error4 = for { JObject(o) <- secondResponsePost4.body; JField("error", JString(error)) <- o } yield error
      And("We should get a message: " + ErrorMessages.CustomerNumberAlreadyExists)
      error4 should contain (ErrorMessages.CustomerNumberAlreadyExists)
      And("User is linked to 3 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON.user_id).size should equal(3)
    }
  }

}

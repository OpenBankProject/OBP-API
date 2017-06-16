package code.api.v2_0_0

import java.text.SimpleDateFormat

import code.customer.Customer
import code.model.BankId
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v1_4_0.JSONFactory1_4_0.{CustomerFaceImageJson, CustomerJsonV140}
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import code.usercustomerlinks.UserCustomerLink
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.json.Serialization.write

class CustomerTest extends V200ServerSetup with DefaultUsers {

  def createCustomerJson(customerNumber: String) = {
    CreateCustomerJson(
      user_id = resourceUser1.userId,
      customer_number = customerNumber,
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
  }

  feature("Assuring that create customer, v2.0.0, feedback and get customer, v1.4.0, feedback are the same") {

    scenario("There is a user, and the bank in questions has customer info for that user - v2.0.0") {
      Given("The bank in question has customer info")

      val customerPostJSON = createCustomerJson(mockCustomerNumber1)
      Then("User is linked to 0 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON.user_id).size must equal(0)
      val requestPost = (v2_0Request / "banks" / mockBankId1.value / "customers").POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      responsePost.code must equal(400)

      When("We add one required entitlement")
      Entitlement.entitlement.vend.addEntitlement(mockBankId1.value, resourceUser1.userId, ApiRole.CanCreateCustomer.toString)
      val responsePost1 = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      responsePost1.code must equal(400)

      When("We add all required entitlement")
      Entitlement.entitlement.vend.addEntitlement(mockBankId1.value, resourceUser1.userId, ApiRole.CanCreateUserCustomerLink.toString)
      val responsePost2 = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 201")
      responsePost2.code must equal(201)
      And("We should get the right information back")
      val infoPost = responsePost2.body.extract[CustomerJsonV140]

      When("We make the request")
      val requestGet = (v2_0Request / "banks" / mockBankId1.value / "customer").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 200")
      responseGet.code must equal(200)

      And("We should get the right information back")
      val infoGet = responseGet.body.extract[CustomerJsonV140]

      And("POST feedback and GET feedback should be the same")
      infoGet must equal(infoPost)

      And("User is linked to 1 customer")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON.user_id).size must equal(1)


      When("We try to make the second request with same customer number at same bank")
      val secondResponsePost = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      secondResponsePost.code must equal(400)
      val error = for { JObject(o) <- secondResponsePost.body; JField("error", JString(error)) <- o } yield error
      And("We should get a message: " + ErrorMessages.CustomerNumberAlreadyExists)
      error must contain (ErrorMessages.CustomerNumberAlreadyExists)
      And("User is linked to 1 customer")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON.user_id).size must equal(1)

      When("We try to make a request with changed customer number at same bank")
      val customerPostJSON3 = createCustomerJson(mockCustomerNumber2)
      val requestPost3 = (v2_0Request / "banks" / mockBankId1.value / "customers").POST <@ (user1)
      val responsePost3 = makePostRequest(requestPost3, write(customerPostJSON3))
      Then("We should get a 201")
      responsePost3.code must equal(201)
      And("User is linked to 2 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON3.user_id).size must equal(2)

      When("We try to make a request with same customer number at different bank")
      Then("first we add all required entitlements")
      Entitlement.entitlement.vend.addEntitlement(mockBankId2.value, resourceUser1.userId, ApiRole.CanCreateCustomer.toString)
      Entitlement.entitlement.vend.addEntitlement(mockBankId2.value, resourceUser1.userId, ApiRole.CanCreateUserCustomerLink.toString)
      val customerPostJSON4 = createCustomerJson(mockCustomerNumber1)
      val requestPost4 = (v2_0Request / "banks" / mockBankId2.value / "customers").POST <@ (user1)
      val responsePost4 = makePostRequest(requestPost4, write(customerPostJSON4))
      Then("We should get a 201")
      responsePost4.code must equal(201)
      And("User is linked to 3 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON4.user_id).size must equal(3)

      When("We try to make the second request with same customer number at same bank")
      val secondResponsePost4 = makePostRequest(requestPost4, write(customerPostJSON4))
      Then("We should get a 400")
      secondResponsePost4.code must equal(400)
      val error4 = for { JObject(o) <- secondResponsePost4.body; JField("error", JString(error)) <- o } yield error
      And("We should get a message: " + ErrorMessages.CustomerNumberAlreadyExists)
      error4 must contain (ErrorMessages.CustomerNumberAlreadyExists)
      And("User is linked to 3 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByUserId(customerPostJSON.user_id).size must equal(3)

    }
  }

}

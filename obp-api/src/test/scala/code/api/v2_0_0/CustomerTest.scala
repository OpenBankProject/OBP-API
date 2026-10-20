/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.v2_0_0

import java.util.Date
import org.json4s._
import com.openbankproject.commons.model.{BankId, CustomerFaceImage, ErrorMessage}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole, ErrorMessages}
import code.customer.CustomerX
import code.socialmedia.SocialMediaHandle
import code.api.v1_4_0.JSONFactory1_4_0.CustomerJsonV140
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import code.usercustomerlinks.UserCustomerLink
import org.json4s.native.Serialization.write

class CustomerTest extends V200ServerSetup with DefaultUsers {

  def createCustomerJson(customerNumber: String) = {
    //try to use the json, we provide to the app.
    SwaggerDefinitionsJSON.createCustomerJson.copy(user_id = resourceUser1.userId, customer_number = customerNumber)
  }

  feature("Assuring that create customer, v2.0.0, feedback and get customer, v1.4.0, feedback are the same") {

    // TODO Add test for AnyBank entitlements

    scenario("There is a user, and the bank in questions has customer info for that user - v2.0.0") {
      Given("The bank in question has customer info")

      val customerPostJSON = createCustomerJson(mockCustomerNumber1)
      Then("User is linked to 0 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(customerPostJSON.user_id).size should equal(0)
      val requestPost = (v2_0Request / "banks" / testBankId1.value / "customers").POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 403")
      responsePost.code should equal(403)

      When("We add one required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanCreateCustomer.toString)
      val responsePost1 = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 403")
      responsePost1.code should equal(403)

      When("We add all required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanCreateUserCustomerLink.toString)
      val responsePost2 = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 201")
      responsePost2.code should equal(201)
      And("We should get the right information back")
      val infoPost = responsePost2.body.extract[CustomerJsonV140]

      When("We make the request")
      val requestGet = (v2_0Request / "banks" / testBankId1.value / "customer").GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 200")
      responseGet.code should equal(200)

      And("We should get the right information back")
      val infoGet = responseGet.body.extract[CustomerJsonV140]

      And("POST feedback and GET feedback must be the same")
      infoGet should equal(infoPost)

      And("User is linked to 1 customer")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(customerPostJSON.user_id).size should equal(1)


      When("We try to make the second request with same customer number at same bank")
      val secondResponsePost = makePostRequest(requestPost, write(customerPostJSON))
      Then("We should get a 400")
      secondResponsePost.code should equal(400)
      And("We should get a message: " + ErrorMessages.CustomerNumberAlreadyExists)
      secondResponsePost.body.extract[ErrorMessage].message should startWith (ErrorMessages.CustomerNumberAlreadyExists)
      And("User is linked to 1 customer")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(customerPostJSON.user_id).size should equal(1)

      When("We try to make a request with changed customer number at same bank")
      val customerPostJSON3 = createCustomerJson(mockCustomerNumber2)
      val requestPost3 = (v2_0Request / "banks" / testBankId1.value / "customers").POST <@ (user1)
      val responsePost3 = makePostRequest(requestPost3, write(customerPostJSON3))
      Then("We should get a 201")
      responsePost3.code should equal(201)
      And("User is linked to 2 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(customerPostJSON3.user_id).size should equal(2)

      When("We try to make a request with same customer number at different bank")
      Then("first we add all required entitlements")
      Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser1.userId, ApiRole.CanCreateCustomer.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser1.userId, ApiRole.CanCreateUserCustomerLink.toString)
      val customerPostJSON4 = createCustomerJson(mockCustomerNumber1)
      val requestPost4 = (v2_0Request / "banks" / testBankId2.value / "customers").POST <@ (user1)
      val responsePost4 = makePostRequest(requestPost4, write(customerPostJSON4))
      Then("We should get a 201")
      responsePost4.code should equal(201)
      And("User is linked to 3 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(customerPostJSON4.user_id).size should equal(3)

      When("We try to make the second request with same customer number at same bank")
      val secondResponsePost4 = makePostRequest(requestPost4, write(customerPostJSON4))
      Then("We should get a 400")
      secondResponsePost4.code should equal(400)
      And("We should get a message: " + ErrorMessages.CustomerNumberAlreadyExists)
      secondResponsePost4.body.extract[ErrorMessage].message should startWith (ErrorMessages.CustomerNumberAlreadyExists)
      And("User is linked to 3 customers")
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(customerPostJSON.user_id).size should equal(3)

    }
  }

  // a customer created straight through the provider, at the given bank
  private def createCustomerAtBank(bankId: String) =
    CustomerX.customerProvider.vend.addCustomer(
      BankId(bankId), APIUtil.generateUUID(), "Social Media Test", "+49 123 456", "social-media-test@example.com",
      CustomerFaceImage(new Date(), "http://example.com/face.png"),
      new Date(), "single", 0, Nil, "Bachelor", "employed", true, new Date(),
      None, None, "", "", ""
    ).openOrThrowException("expected the customer to be created")

  feature("Create Customer Social Media Handle only writes to a customer of the bank in the path - v2.0.0") {

    scenario("The CUSTOMER_ID in the path belongs to another bank - v2.0.0") {
      Given("A customer at the second bank, and the Role at the first bank only")
      val otherBankCustomer = createCustomerAtBank(testBankId2.value)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanAddSocialMediaHandle.toString)

      When("We post a handle for that customer at the first bank")
      val request = (v2_0Request / "banks" / testBankId1.value / "customers" / otherBankCustomer.customerId / "social_media_handles").POST <@ (user1)
      val response = makePostRequest(request, write(SwaggerDefinitionsJSON.socialMediaJSON.copy(customer_number = otherBankCustomer.number)))

      Then("We should get a 404")
      response.code should equal(404)
      And("We should get a message: " + ErrorMessages.CustomerNotFoundByCustomerId)
      response.body.extract[ErrorMessage].message should startWith (ErrorMessages.CustomerNotFoundByCustomerId)
      And("No handle is stored for that customer")
      SocialMediaHandle.socialMediaHandleProvider.vend.getSocialMedias(otherBankCustomer.number) should equal(Nil)
    }
  }

  feature("Get Customer Social Media Handles only reads a customer of the bank in the path - v2.0.0") {

    scenario("The CUSTOMER_ID in the path belongs to another bank - v2.0.0") {
      Given("A customer at the second bank with a handle, and the Role at the first bank only")
      val otherBankCustomer = createCustomerAtBank(testBankId2.value)
      SocialMediaHandle.socialMediaHandleProvider.vend.addSocialMedias(otherBankCustomer.number, "twitter", "@other-bank-customer", new Date(), new Date())
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanGetSocialMediaHandles.toString)

      When("We get the handles of that customer at the first bank")
      val request = (v2_0Request / "banks" / testBankId1.value / "customers" / otherBankCustomer.customerId / "social_media_handles").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 404")
      response.code should equal(404)
      And("We should get a message: " + ErrorMessages.CustomerNotFoundByCustomerId)
      response.body.extract[ErrorMessage].message should startWith (ErrorMessages.CustomerNotFoundByCustomerId)
    }
  }

}

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

package code.api.v1_4_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.v1_4_0.JSONFactory1_4_0.{AddCustomerMessageJson, CustomerFaceImageJson, CustomerMessagesJson}
import code.api.v2_0_0.CreateCustomerJson
import code.customer.{CustomerX, MappedCustomerMessage}
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import code.usercustomerlinks.UserCustomerLink
import com.openbankproject.commons.model.Customer
import net.liftweb.common.{Box, Empty, Full}
import org.json4s.native.Serialization.write

//TODO: API test should be independent of CustomerMessages implementation
class MappedCustomerMessagesTest extends V140ServerSetup with DefaultUsers {

  //TODO: need better tests
  feature("Customer messages") {
    scenario("Getting messages when none exist") {
      Given("No messages exist")
      MappedCustomerMessage.count() should equal(0)

      When("We get the messages")
      val request = (v1_4Request / "banks" / testBankId1.value / "customer" / "messages").GET <@ user1
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("We should get no messages")
      val json = response.body.extract[CustomerMessagesJson]
      json.messages.size should equal(0)
    }

    scenario("Adding a message") {
      //first add a customer to send message to
      var request = (v1_4Request / "banks" / testBankId1.value / "customer").POST <@ user1
      val customerJson = CreateCustomerJson(
                                            title = "Title",
                                            branchId = "The branchId",
                                            nameSuffix = "The nameSuffix",
                                            user_id = resourceUser1.userId,
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
                                            last_ok_date = exampleDate)

      When("We add all required entitlement")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanCreateCustomer.toString)
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanCreateUserCustomerLink.toString)
      var response = makePostRequest(request, write(customerJson))

      val customer: Box[Customer] = CustomerX.customerProvider.vend.getCustomerByCustomerNumber(mockCustomerNumber, testBankId1)
      val customerId = customer match {
        case Full(c) => c.customerId
        case Empty => "Empty"
        case _ => "Failure"
      }

      When("We add a message")
      request = (v1_4Request / "banks" / testBankId1.value / "customer" / customerId / "messages").POST <@ user1
      val messageJson = AddCustomerMessageJson("some message", "some department", "some person")
      response = makePostRequest(request, write(messageJson))
      Then("We should get a 201")
      response.code should equal(201)

      And("We should get that message when we do a get messages request ")
      val getMessagesRequest = (v1_4Request / "banks" / testBankId1.value / "customer" / "messages").GET  <@ user1
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
    MappedCustomerMessage.bulkDelete_!!()
    UserCustomerLink.userCustomerLink.vend.bulkDeleteUserCustomerLinks()
    CustomerX.customerProvider.vend.bulkDeleteCustomers()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    MappedCustomerMessage.bulkDelete_!!()
    UserCustomerLink.userCustomerLink.vend.bulkDeleteUserCustomerLinks()
    CustomerX.customerProvider.vend.bulkDeleteCustomers()
  }

}

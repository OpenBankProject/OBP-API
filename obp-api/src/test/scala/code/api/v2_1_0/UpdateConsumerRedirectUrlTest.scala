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

package code.api.v2_1_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.util.ApiRole.CanUpdateConsumerRedirectUrl
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNoPermissionUpdateConsumer}
import code.setup.DefaultUsers
import org.json4s.JsonAST.JString
import org.json4s.native.Serialization.write

class UpdateConsumerRedirectUrlTest extends V210ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  feature("Assuring that endpoint 'updateConsumerRedirectUrl' works as expected - v2.1.0") {

    val consumerRedirectUrlJSON = ConsumerRedirectUrlJSON("x-com.tesobe.helloobp.ios://callback")

    scenario("Try to Update Redirect Url without proper role ") {

      When("We make the request Update Redirect Url for a Consumer")
      val requestPut = (v2_1Request / "management" / "consumers" / testConsumer.id.get / "consumer" / "redirect_url" ).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(consumerRedirectUrlJSON))

      Then("We should get a 403")
      println(responsePut.body)
      responsePut.code should equal(403)

      val error = (responsePut.body \ "message" ) match {
        case JString(i) => i
        case _ => ""
      }
      And("We should get a message " + UserHasMissingRoles + CanUpdateConsumerRedirectUrl)
      error should equal(UserHasMissingRoles + CanUpdateConsumerRedirectUrl)
    }

    scenario("Try to Update Redirect Url created by other user ") {

      Then("We add entitlement to user2")
      addEntitlement("", resourceUser2.userId, CanUpdateConsumerRedirectUrl.toString)
      val hasEntitlement = APIUtil.hasEntitlement("", resourceUser2.userId, ApiRole.canUpdateConsumerRedirectUrl)
      hasEntitlement should equal(true)

      When("We make the request Update Redirect Url for a Consumer")
      val requestPut = (v2_1Request / "management" / "consumers" / testConsumer.id.get / "consumer" / "redirect_url" ).PUT <@ (user2)
      val responsePut = makePutRequest(requestPut, write(consumerRedirectUrlJSON))

      Then("We should get a 400")
      responsePut.code should equal(400)

      val error = (responsePut.body \ "message" ) match {
        case JString(i) => i
        case _ => ""
      }
      And("We should get a message " + UserNoPermissionUpdateConsumer)
      error.toString contains (UserNoPermissionUpdateConsumer) should be (true)
    }

    scenario("Try to Update Redirect Url successfully ") {

      Then("We add entitlement to user1")
      addEntitlement("", resourceUser1.userId, CanUpdateConsumerRedirectUrl.toString)
      val hasEntitlement = APIUtil.hasEntitlement("", resourceUser1.userId, ApiRole.canUpdateConsumerRedirectUrl)
      hasEntitlement should equal(true)

      When("We make the request Update Redirect Url for a Consumer")
      val requestPut = (v2_1Request / "management" / "consumers" / testConsumer.id.get / "consumer" / "redirect_url" ).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(consumerRedirectUrlJSON))

      Then("We should get a 200")
      println(responsePut.body)
      responsePut.code should equal(200)

      val field = (responsePut.body \ "redirect_url" ) match {
        case JString(i) => i
        case _ => ""
      }
      And("We should get an updated url " + consumerRedirectUrlJSON.redirect_url)
      field should equal(consumerRedirectUrlJSON.redirect_url)
    }



  }


}

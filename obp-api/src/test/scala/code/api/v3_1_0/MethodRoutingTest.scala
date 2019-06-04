/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import code.methodrouting.MethodRoutingCommons
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class MethodRoutingTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createMethodRouting))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.updateMethodRouting))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.getMethodRoutings))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_1_0.deleteMethodRouting))

  val rightEntity = MethodRoutingCommons("getBank", "rest_vMar2019", false, Some("some_bankId_.*"))
  val wrongEntity = MethodRoutingCommons("getBank", "rest_vMar2019", false, Some("some_bankId_([")) // wrong regex


  feature("Add a MethodRouting v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "method-routing").POST
      val response310 = makePostRequest(request310, write(rightEntity))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update a MethodRouting v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "method-routing"/ "some-method-routing-id").PUT
      val response310 = makePutRequest(request310, write(rightEntity))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Get MethodRoutings v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "method-routing" / "method-name" / "getBank").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Delete the MethodRouting specified by METHOD_ROUTING_ID v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "method-routing" / "METHOD_ROUTING_ID").DELETE
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }


  feature("Add a MethodRouting v3.1.0 - Unauthorized access - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canCreateMethodRouting, ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + canCreateTaxResidence)
      val request310 = (v3_1_0_Request / "management" / "method-routing").POST <@(user1)
      val response310 = makePostRequest(request310, write(rightEntity))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateMethodRouting)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanCreateMethodRouting)
    }

    scenario("We will call the endpoint with the proper Role " + canCreateMethodRouting , ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateMethodRouting.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "method-routing").POST <@(user1)
      val response310 = makePostRequest(request310, write(rightEntity))
      Then("We should get a 201")
      response310.code should equal(201)
      val customerJson = response310.body.extract[MethodRoutingCommons]

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateMethodRouting.toString)
      When("We make a request v3.1.0 with the Role " + canUpdateMethodRouting)

      {
        // update success
        val request310 = (v3_1_0_Request / "management" / "method-routing" / customerJson.methodRoutingId.get ).PUT <@(user1)
        val response310 = makePutRequest(request310, write(customerJson.copy(connectorName = "mapped")))
        Then("We should get a 200")
        response310.code should equal(200)
        val taxResidenceJson = response310.body.extract[MethodRoutingCommons]
        taxResidenceJson.connectorName should be ("mapped")
      }

      {
        // update a not exists MethodRouting
        val request310 = (v3_1_0_Request / "management" / "method-routing" / "not-exists-id" ).PUT <@(user1)
        val response310 = makePutRequest(request310, write(customerJson.copy(connectorName = "mapped")))
        Then("We should get a 400")
        response310.code should equal(400)
        response310.body.extract[ErrorMessage].message should startWith (MethodRoutingNotFoundByMethodRoutingId)
      }

      {
        // update a MethodRouting with wrong regex of bankIdPattern
        val request310 = (v3_1_0_Request / "management" / "method-routing" / customerJson.methodRoutingId.get ).PUT <@(user1)
        val response310 = makePutRequest(request310, write(wrongEntity))
        Then("We should get a 400")
        response310.code should equal(400)
        response310.body.extract[ErrorMessage].message should startWith (InvalidBankIdRegex)
      }

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetMethodRoutings.toString)
      When("We make a request v3.1.0 with the Role " + canGetMethodRoutings)
      val requestGet310 = (v3_1_0_Request / "management" / "method-routing" / "method-name" / "getBank").GET <@(user1)
      val responseGet310 = makeGetRequest(requestGet310)
      Then("We should get a 200")
      responseGet310.code should equal(200)
      val json = responseGet310.body \ "results"
      val taxResidenceGetJson = json.extract[List[MethodRoutingCommons]]

      taxResidenceGetJson.size should be (1)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteMethodRouting.toString)
      When("We make a request v3.1.0 with the Role " + canDeleteMethodRouting)
      val requestDelete310 = (v3_1_0_Request / "management" / "method-routing" / taxResidenceGetJson.head.methodRoutingId.get).DELETE <@(user1)
      val responseDelete310 = makeDeleteRequest(requestDelete310)
      Then("We should get a 200")
      responseDelete310.code should equal(200)

    }
  }


}

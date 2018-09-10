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
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

  */
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanGetConsumers
import code.api.util.ErrorMessages.{ConsumerNotFoundByConsumerId, UserHasMissingRoles, UserNotLoggedIn}
import code.entitlement.Entitlement

import scala.collection.immutable.List

class ConsumerTest extends V310ServerSetup {

  feature("Get Consumer by CONSUMER_ID - v3.1.0")
  {
    scenario("We will Get Consumer by CONSUMER_ID without a proper Role " + ApiRole.canGetConsumers) {
      When("We make a request v3.1.0 without a Role " + ApiRole.canGetConsumers)
      val request310 = (v3_1_0_Request / "management" / "consumers" / "non existing CONSUMER_ID").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetConsumers)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanGetConsumers)
    }
    scenario("We will Get Consumer by CONSUMER_ID with a proper Role " + ApiRole.canGetConsumers) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetConsumers.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "consumers" / "non existing CONSUMER_ID").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(400)
      And("error should be " + ConsumerNotFoundByConsumerId)
      response310.body.extract[ErrorMessage].error should equal (ConsumerNotFoundByConsumerId)
    }
  }

  feature("Get Consumers for current use - v3.1.0")
  {
    scenario("We will Get Consumers for current user - NOT logged in") {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "users" / "current" / "consumers").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
    scenario("We will Get Consumers for current user") {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "management" / "users" / "current" / "consumers").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      response310.body.extract[List[ConsumerJson]]
    }
  }

}

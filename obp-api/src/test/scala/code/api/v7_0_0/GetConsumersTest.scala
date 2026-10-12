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

package code.api.v7_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.canGetConsumers
import code.api.util.ErrorMessages.{ApplicationNotIdentified, UserHasMissingRoles}
import code.api.v6_0_0.V600ServerSetup
import code.api.v7_0_0.JSONFactory700.ConsumersJsonV700
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.{JArray, JObject}
import org.scalatest.Tag

/** GET /obp/v7.0.0/management/consumers: every Consumer, referenced by consumer_id, never with its key. */
class GetConsumersTest extends V600ServerSetup {
  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("getConsumers")

  feature(s"test $ApiEndpoint1 version $VersionOfApi") {
    scenario("Without any credentials the application cannot be identified", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest((v7_0_0_Request / "management" / "consumers").GET)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should include(ApplicationNotIdentified)
    }

    scenario("A user without the Role is refused", ApiEndpoint1, VersionOfApi) {
      val response = makeGetRequest((v7_0_0_Request / "management" / "consumers").GET <@ (user1))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
      response.body.extract[ErrorMessage].message should include(canGetConsumers.toString)
    }

    scenario("With the Role the list has every Consumer and no consumer_key", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canGetConsumers.toString)
      val response = makeGetRequest((v7_0_0_Request / "management" / "consumers").GET <@ (user1))
      Then("We should get a 200")
      response.code should equal(200)
      val consumers = response.body.extract[ConsumersJsonV700].consumers
      consumers.map(_.consumer_id) should contain(testConsumer.consumerId.get)
      And("no listed Consumer carries a consumer_key field")
      val fieldNames = (response.body \ "consumers") match {
        case JArray(items) => items.collect { case JObject(fields) => fields.map(_._1) }.flatten.toSet
        case _ => Set.empty[String]
      }
      fieldNames should contain("consumer_id")
      fieldNames should not contain("consumer_key")
      And("the key value appears nowhere in the body")
      response.body.toString should not include(testConsumer.key.get)
    }
  }
}

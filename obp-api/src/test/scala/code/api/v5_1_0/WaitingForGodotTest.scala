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

package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetWaitingForGodot
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.entitlement.Entitlement
import code.setup.APIResponse
import org.json4s._

/**
 * This suite checks that waitingForGodot needs an authenticated User with the Role CanGetWaitingForGodot,
 * and that it only ever delays for between zero and the instance's maximum, however large or invalid the
 * requested delay is.
 */
class WaitingForGodotTest extends V510ServerSetup {

  private def waitingForGodot(sleep: String) = (v5_1_0_Request / "waiting-for-godot").GET <<? List("sleep" -> sleep)

  private def sleepReported(response: APIResponse): Long = (response.body \ "sleep_in_milliseconds").extract[Long]

  private def messageOf(response: APIResponse): String = (response.body \ "message").extractOpt[String].getOrElse("")

  private def grantRoleToUser1(): Unit =
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetWaitingForGodot.toString)

  feature("waitingForGodot needs authentication and a Role") {

    scenario("an anonymous caller is refused with 401") {
      val response = makeGetRequest(waitingForGodot("20"))
      response.code should equal(401)
      messageOf(response) should include(AuthenticatedUserIsRequired)
    }

    scenario("a User without the Role is refused with 403") {
      val response = makeGetRequest(waitingForGodot("20") <@ (user2))
      response.code should equal(403)
      messageOf(response) should equal(UserHasMissingRoles + CanGetWaitingForGodot)
    }
  }

  feature("waitingForGodot delays only within bounds") {

    scenario("a User with the Role gets the delay it asks for when it is within the maximum") {
      grantRoleToUser1()
      val response = makeGetRequest(waitingForGodot("20") <@ (user1))
      response.code should equal(200)
      sleepReported(response) should equal(20L)
    }

    scenario("a delay above the maximum is reduced to the maximum") {
      grantRoleToUser1()
      setPropsValues("waiting_for_godot_max_sleep_in_milliseconds" -> "50")
      val started = System.currentTimeMillis()
      val response = makeGetRequest(waitingForGodot(Long.MaxValue.toString) <@ (user1))
      val elapsed = System.currentTimeMillis() - started
      response.code should equal(200)
      sleepReported(response) should equal(50L)
      elapsed should be < 10000L
    }

    scenario("a negative or unreadable delay counts as zero instead of failing") {
      grantRoleToUser1()
      List("-1", "not-a-number").foreach { requested =>
        val response = makeGetRequest(waitingForGodot(requested) <@ (user1))
        withClue(requested) {
          response.code should equal(200)
          sleepReported(response) should equal(0L)
        }
      }
    }
  }
}

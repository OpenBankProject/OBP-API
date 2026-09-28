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
import code.api.util.ApiRole.{CanCreateIpPenalty, CanDeleteIpPenalty, CanGetIpPenalties}
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.api.util.IpPenalties
import code.api.v6_0_0.V600ServerSetup
import code.api.v7_0_0.JSONFactory700.{IpPenaltiesJsonV700, IpPenaltyJsonV700, PostIpPenaltyJsonV700}
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * This suite checks the IP penalty endpoints (create, list, delete, each behind its own Role) and the
 * penalty itself: a penalised address is refused with 429 OBP-10062 once over its per-minute limit,
 * on any endpoint, while the penalty endpoints stay reachable so a mistake can be undone.
 */
class IpPenaltiesTest extends V600ServerSetup {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag("ipPenalties")

  private def penalties = v7_0_0_Request / "management" / "ip-penalties"

  private def withRoles[T](body: => T): T = {
    val granted = List(CanCreateIpPenalty, CanGetIpPenalties, CanDeleteIpPenalty)
      .map(role => Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, role.toString))
    try body finally granted.foreach(Entitlement.entitlement.vend.deleteEntitlement)
  }

  private def post(json: PostIpPenaltyJsonV700) = makePostRequest(penalties.POST <@ (user1), write(json))

  // Addresses from the documentation ranges (RFC 5737), never a real client.
  private val documentationAddress = "203.0.113.77"

  feature(s"IP penalties - /obp/v7.0.0/management/ip-penalties - $VersionOfApi") {

    scenario("anonymous access is 401 and a user without the Roles gets 403", ApiEndpoint, VersionOfApi) {
      makeGetRequest(penalties.GET).code should equal(401)
      makeGetRequest(penalties.GET).body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)

      val get = makeGetRequest(penalties.GET <@ (user1))
      get.code should equal(403)
      get.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetIpPenalties)

      post(PostIpPenaltyJsonV700(documentationAddress, 10, 60, "test")).code should equal(403)
      makeDeleteRequest((penalties / documentationAddress).DELETE <@ (user1)).code should equal(403)
    }

    scenario("create, list and delete a penalty", ApiEndpoint, VersionOfApi) {
      withRoles {
        try {
          val created = post(PostIpPenaltyJsonV700(documentationAddress, 10, 60, "scanning resource-docs"))
          created.code should equal(201)
          val penalty = created.body.extract[IpPenaltyJsonV700]
          penalty.ip_address should equal(documentationAddress)
          penalty.per_minute_limit should equal(10L)
          penalty.created_by_user_id should equal(resourceUser1.userId)
          (penalty.expires_at.getTime - penalty.created_at.getTime) should equal(60L * 60 * 1000)

          Then("a second penalty on the same address is a conflict")
          post(PostIpPenaltyJsonV700(documentationAddress, 5, 30, "again")).code should equal(409)

          Then("the list shows it")
          val listed = makeGetRequest(penalties.GET <@ (user1))
          listed.code should equal(200)
          listed.body.extract[IpPenaltiesJsonV700].ip_penalties.map(_.ip_address) should contain(documentationAddress)

          Then("delete removes it, and deleting again is 404")
          makeDeleteRequest((penalties / documentationAddress).DELETE <@ (user1)).code should equal(204)
          makeDeleteRequest((penalties / documentationAddress).DELETE <@ (user1)).code should equal(404)
        } finally IpPenalties.remove(documentationAddress)
      }
    }

    scenario("invalid input is refused with 400", ApiEndpoint, VersionOfApi) {
      withRoles {
        post(PostIpPenaltyJsonV700("example.com", 10, 60, "a host name is not an address")).code should equal(400)
        post(PostIpPenaltyJsonV700(documentationAddress, -1, 60, "negative limit")).code should equal(400)
        post(PostIpPenaltyJsonV700(documentationAddress, 10, 0, "no duration")).code should equal(400)
        post(PostIpPenaltyJsonV700(documentationAddress, 10, IpPenalties.MaxDurationMinutes + 1, "too long")).code should equal(400)
        post(PostIpPenaltyJsonV700(documentationAddress, 10, 60, "")).code should equal(400)
        IpPenalties.exists(documentationAddress) shouldBe false
      }
    }

    scenario("a penalised address is refused on any endpoint once over its limit, but can still manage penalties", ApiEndpoint, VersionOfApi) {
      // The test server may see this client as either loopback address.
      val loopbackAddresses = List("127.0.0.1", "::1")
      try {
        loopbackAddresses.foreach(address => IpPenalties.add(address, 1, 5, "IpPenaltiesTest", resourceUser1.userId))
        IpPenalties.refresh()

        val first = makeGetRequest((v7_0_0_Request / "banks").GET)
        val second = makeGetRequest((v7_0_0_Request / "banks").GET)
        first.code should equal(200)
        second.code should equal(429)
        second.body.extract[ErrorMessage].message should startWith("OBP-10062")

        Then("the penalty endpoints still answer, so the penalty can be removed")
        withRoles {
          makeGetRequest(penalties.GET <@ (user1)).code should equal(200)
        }
      } finally {
        loopbackAddresses.foreach(IpPenalties.remove)
      }

      Then("once removed, the address is served again")
      makeGetRequest((v7_0_0_Request / "banks").GET).code should equal(200)
    }

    scenario("addresses are compared in canonical form, and host names are not addresses", ApiEndpoint, VersionOfApi) {
      IpPenalties.canonicalAddress("2001:DB8:0:0:0:0:0:1") shouldBe Some("2001:db8::1")
      IpPenalties.canonicalAddress(" 203.0.113.5 ") shouldBe Some("203.0.113.5")
      IpPenalties.canonicalAddress("example.com") shouldBe None
      IpPenalties.canonicalAddress("") shouldBe None
    }
  }
}

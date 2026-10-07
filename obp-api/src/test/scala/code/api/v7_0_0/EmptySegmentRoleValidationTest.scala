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

import cats.effect.unsafe.IORuntime
import code.api.sweep.SweepFixtures
import code.api.util.ApiRole
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.entitlement.Entitlement
import code.setup.{DefaultUsers, ServerSetupWithTestData}
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

/**
 * A role-gated v7.0.0 endpoint must enforce its roles whatever the path parameters hold.
 *
 * `GET /banks/BANK_ID/api-products/API_PRODUCT_CODE/subscriptions` declares
 * `canGetApiProductSubscriptionAtOneBank`, and its handler does not check that role itself: the
 * role is enforced only by ResourceDocMiddleware, from the doc it selects for the request. With an
 * empty `API_PRODUCT_CODE` segment (`/api-products//subscriptions`) the http4s route still
 * matches, but the middleware counts segments, finds no doc and runs the handler without any
 * validation, so the caller gets the handler's own answer instead of the role check.
 *
 * The first two scenarios use a caller that is authenticated and holds no role at all. The third uses
 * a caller that holds the role at the bank: the empty segment must not make the middleware check the
 * role against a bank called "" or skip the bank, so the answer is the handler's own, not a 403 or a 500.
 */
class EmptySegmentRoleValidationTest extends ServerSetupWithTestData with DefaultUsers with SweepFixtures {

  object EmptySegmentRoleValidationTag extends Tag("EmptySegmentRoleValidation")

  implicit val runtime: IORuntime = IORuntime.global

  private def callerWithoutRoles: Map[String, String] = Map("DirectLogin" -> s"token=${token1.value}")

  private def bank: String = realBankId.getOrElse(fail("the test fixtures created no bank"))

  feature("Roles of a v7.0.0 endpoint are enforced for every request its route serves") {

    scenario("A caller without the role is refused for a well-formed path", EmptySegmentRoleValidationTag) {
      val (status, json) = callApi("GET", s"/obp/v7.0.0/banks/$bank/api-products/some-product/subscriptions", callerWithoutRoles)
      withClue(s"body: ${compact(render(json))}\n") {
        status shouldBe 403
        compact(render(json)) should include(UserHasMissingRoles.take(10))
      }
    }

    scenario("A caller without the role is refused when a path parameter is empty", EmptySegmentRoleValidationTag) {
      val (status, json) = callApi("GET", s"/obp/v7.0.0/banks/$bank/api-products//subscriptions", callerWithoutRoles)
      withClue(s"body: ${compact(render(json))}\n") {
        status shouldBe 403
        compact(render(json)) should include(UserHasMissingRoles.take(10))
      }
    }

    scenario("A caller with the role at the bank is not refused by the role check when a path parameter is empty", EmptySegmentRoleValidationTag) {
      val entitlement = Entitlement.entitlement.vend.addEntitlement(
        bank, resourceUser1.userId, ApiRole.canGetApiProductSubscriptionAtOneBank.toString)
      try {
        val (status, json) = callApi("GET", s"/obp/v7.0.0/banks/$bank/api-products//subscriptions", callerWithoutRoles)
        withClue(s"body: ${compact(render(json))}\n") {
          status should not be 403
          status should not be 500
          compact(render(json)) should not include UserHasMissingRoles.take(10)
        }
      } finally Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }
}

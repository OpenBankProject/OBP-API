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
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v7_0_0.Http4s700.Implementations7_0_0
import code.entitlement.Entitlement
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/** DELETE /banks/BANK_ID/entitlements/ENTITLEMENT_ID: CanDeleteEntitlementAtOneBank at that bank is enough. */
class DeleteEntitlementAtOneBankTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations7_0_0.deleteEntitlementAtOneBank))

  def v7 = baseRequest / "obp" / "v7.0.0"

  def errorOf(response: code.setup.APIResponse): String = response.body.extract[ErrorMessage].message

  /** A Role held by user2 at the bank, for user1 to delete. */
  def grantToUser2(bankId: String): String =
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser2.userId, CanGetCustomersAtOneBank.toString)
      .map(_.entitlementId).openOrThrowException("could not create the Entitlement")

  feature("Delete an Entitlement at one bank") {
    scenario("Anonymous is 401; without the Role it is 403", ApiEndpoint, VersionOfApi) {
      val entitlementId = grantToUser2(testBankId1.value)
      val path = v7 / "banks" / testBankId1.value / "entitlements" / entitlementId
      makeDeleteRequest(path.DELETE).code should equal(401)
      val noRole = makeDeleteRequest(path.DELETE <@ (user1))
      noRole.code should equal(403)
      errorOf(noRole) should startWith(UserHasMissingRoles)
      Entitlement.entitlement.vend.getEntitlementById(entitlementId).isDefined should equal(true)
    }

    scenario("The Role at the bank deletes an Entitlement there, and only there", ApiEndpoint, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanDeleteEntitlementAtOneBank.toString)

      val atThisBank = grantToUser2(testBankId1.value)
      makeDeleteRequest((v7 / "banks" / testBankId1.value / "entitlements" / atThisBank).DELETE <@ (user1)).code should equal(204)
      Entitlement.entitlement.vend.getEntitlementById(atThisBank).isDefined should equal(false)

      Then("an Entitlement at another bank is not found through this bank")
      val atOtherBank = grantToUser2(testBankId2.value)
      val wrongBank = makeDeleteRequest((v7 / "banks" / testBankId1.value / "entitlements" / atOtherBank).DELETE <@ (user1))
      wrongBank.code should equal(404)
      errorOf(wrongBank) should startWith(EntitlementNotFound)
      Entitlement.entitlement.vend.getEntitlementById(atOtherBank).isDefined should equal(true)

      And("the Role at one bank does not reach the other bank")
      makeDeleteRequest((v7 / "banks" / testBankId2.value / "entitlements" / atOtherBank).DELETE <@ (user1)).code should equal(403)
      Entitlement.entitlement.vend.getEntitlementById(atOtherBank).isDefined should equal(true)
    }
  }
}

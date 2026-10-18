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
package code.api.v2_2_0

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.bankJSONV220
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, ErrorMessages, NewStyle}
import code.api.v2_2_0.Http4s220.Implementations2_2_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

class BankTest extends V220ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v2_2_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations2_2_0.createBank))

  feature("Assuring that endpoint createBank works as expected - v2.2.0") {

    // An existing BANK_ID is refused, so CanCreateBank cannot overwrite another bank or gain its entitlement admin role
    scenario("We try to create a bank with the BANK_ID of an existing bank - Authorized access", ApiEndpoint1, VersionOfApi) {
      Given("A bank that already exists and a user with CanCreateBank")
      val existingBankId = createBank("testBank0").bankId.value
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateBank.toString)
      When("We post a bank with that BANK_ID")
      val request = (v2_2Request / "banks").POST <@ (user1)
      val response = makePostRequest(request, write(bankJSONV220.copy(id = existingBankId)))
      Then("We should get a 400 with bankIdAlreadyExists")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.bankIdAlreadyExists)
      And("The caller is not given CanCreateEntitlementAtOneBank at that bank")
      Await.result(
        NewStyle.function.getEntitlementsByUserId(resourceUser1.userId, None), 10.seconds
      ).exists(e => e.roleName == ApiRole.CanCreateEntitlementAtOneBank.toString && e.bankId == existingBankId) should equal(false)
    }
  }
}

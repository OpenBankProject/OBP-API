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

package code.api.v6_0_0

import org.json4s.jvalue2extractable
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.AuthenticatedUserIsRequired
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class ProductsV600Test extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getProductsV600))

  feature(s"Assuring that endpoint getProductsV600 honours apiOptions.getProductsIsPublic - $VersionOfApi") {

    // with apiOptions.getProductsIsPublic=false the product list refuses anonymous callers with 401; the prop is read once at startup, so this JVM sees only one branch
    scenario("We get the products without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We get the products with no authentication")
      val request = (v6_0_0_Request / "banks" / testBankId1.value / "products").GET
      val response = makeGetRequest(request)
      if (code.api.util.APIUtil.getProductsIsPublic) {
        Then("We should get a 200")
        response.code should equal(200)
      } else {
        Then("We should get a 401")
        response.code should equal(401)
        And("error should be " + AuthenticatedUserIsRequired)
        response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
      }
    }

    // a logged-in caller gets the product list whatever the prop says
    scenario("We get the products with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We get the products as user1")
      val request = (v6_0_0_Request / "banks" / testBankId1.value / "products").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
    }
  }
}

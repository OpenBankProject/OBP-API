/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{postUserAuthContextJson, postUserAuthContextUpdateJsonV310}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v3_1_0.CustomerJsonV310
import code.api.v5_0_0.OBPAPI5_0_0.Implementations5_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.language.postfixOps

class ATMTest extends V500ServerSetupAsync {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.headAtms))


  feature("Head Bank ATMS v5.0.0") {
    scenario("We will call the Add endpoint properly", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.0.0")
      lazy val bankId = randomBankId
      val request500 = (v5_0_0_Request / "banks" / bankId / "atms").HEAD
      val response500 = makeHeadRequest(request500)
      Then("We should get a 200")
      response500.code should equal(200)
    }
    scenario("We will call the Add endpoint with wrong BankId", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.0.0")
      val request500 = (v5_0_0_Request / "banks" / "xx_non_existing_bank_id" / "atms").HEAD
      val response500 = makeHeadRequest(request500)
      Then("We should get a 404")
      response500.code should equal(404)
    }
    
  }
}

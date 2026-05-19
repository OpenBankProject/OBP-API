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
package code.api.v3_1_0

import code.api.v2_2_0.JSONFactory220.MessageDocsJson
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class GetMessageDocsSwaggerTest extends V310ServerSetup with DefaultUsers {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.getMessageDocsSwagger))

  feature("Get Message Docs Swagger v3.1.0")
  {
    scenario(s"should return proper response", ApiEndpoint, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "message-docs" / "rest_vMar2019" / "swagger2.0").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      //TODO can add more tests for the swagger validation here.
    }
  }


}

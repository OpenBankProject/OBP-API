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
package code.api.v4_0_0

import code.api.util.ApiRole._
import code.api.v4_0_0.APIMethods400.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ListResult
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.scalatest.Tag

import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter
class GetScannedApiVersionsTest extends V400ServerSetup {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * 	mvn test -D tagsToInclude
   *
   *  This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations4_0_0.getScannedApiVersions))


  feature("Get all scanned API versions should works") {
    scenario("We get all the scanned API versions", ApiEndpoint, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateDynamicEntity.toString)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "api" / "versions").GET

      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)

      val listResult = response.body.extract[ListResult[List[ScannedApiVersion]]]
      val responseApiVersions = listResult.results
      val scannedApiVersions = ApiVersion.allScannedApiVersion.asScala.toList

      responseApiVersions should equal(scannedApiVersions)

    }
  }

}

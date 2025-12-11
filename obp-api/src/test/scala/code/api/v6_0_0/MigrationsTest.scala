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
package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetMigrations
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v6_0_0.OBPAPI6_0_0.Implementations6_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class MigrationsTest extends V600ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getMigrations))

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0")
      val request600 = (v6_0_0_Request / "system" / "migrations").GET
      val response600 = makeGetRequest(request600)
      Then("We should get a 401")
      response600.code should equal(401)
      response600.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without proper Role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 without a proper role")
      val request600 = (v6_0_0_Request / "system" / "migrations").GET <@ (user1)
      val response600 = makeGetRequest(request600)
      Then("We should get a 403")
      response600.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetMigrations)
      response600.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetMigrations)
    }

    scenario("We will call the endpoint with proper Role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 with a proper role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetMigrations.toString)
      val request600 = (v6_0_0_Request / "system" / "migrations").GET <@ (user1)
      val response600 = makeGetRequest(request600)
      Then("We should get a 200")
      response600.code should equal(200)
      And("we should get the correct response format")
      val migrationsJson = response600.body.extract[MigrationScriptLogsJsonV600]
      migrationsJson.migration_script_logs should not be null
      migrationsJson.migration_script_logs shouldBe a[List[_]]
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Response validation") {
    scenario("We will verify the response structure contains expected fields", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetMigrations.toString)
      When("We make a request v6.0.0")
      val request600 = (v6_0_0_Request / "system" / "migrations").GET <@ (user1)
      val response600 = makeGetRequest(request600)
      Then("We should get a 200")
      response600.code should equal(200)
      And("The response should have the correct structure")
      val migrationsJson = response600.body.extract[MigrationScriptLogsJsonV600]
      migrationsJson.migration_script_logs should not be null
      
      if (migrationsJson.migration_script_logs.nonEmpty) {
        val firstLog = migrationsJson.migration_script_logs.head
        firstLog.migration_script_log_id should not be empty
        firstLog.name should not be empty
        firstLog.commit_id should not be empty
        firstLog.is_successful shouldBe a[Boolean]
        firstLog.start_date should be > 0L
        firstLog.end_date should be >= firstLog.start_date
        firstLog.duration_in_ms should equal(firstLog.end_date - firstLog.start_date)
        firstLog.remark should not be null
        firstLog.created_at should not be null
        firstLog.updated_at should not be null
      }
    }
  }
}
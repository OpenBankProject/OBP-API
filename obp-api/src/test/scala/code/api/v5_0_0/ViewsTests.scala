/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.api.v5_0_0

import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.OAuth._
import code.api.v1_2_1.{PermissionJSON, PermissionsJSON}
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.setup.APIResponse
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

import scala.util.Random.nextInt

class ViewsTests extends V500ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_0_0.getPermissionForUserForBankAccount))
  
  
  //Custom view, name starts from `_`
  val postBodyViewJson = createViewJsonV300
  //System view, owner
  val postBodySystemViewJson = createViewJsonV300.copy(name=SYSTEM_OWNER_VIEW_ID).copy(metadata_view = SYSTEM_OWNER_VIEW_ID)
  

  def getAccountAccessForUser(bankId: String, accountId: String, provider : String, providerId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v5_0_0_Request / "banks" / bankId / "accounts" / accountId / "permissions" / provider / providerId).GET <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  //This will call v1_2_1Request and we need it here to prepare the data for further tests
  //BK need to check this endpoint....
  def getAccountPermissions(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v5_0_0_Request / "banks" / bankId / "accounts" / accountId / "permissions" <@(consumerAndToken)
    makeGetRequest(request)
  }
  //This is a helper method, used to prepare the test parameters
  def randomAccountPermission(bankId : String, accountId : String) : PermissionJSON = {
    val persmissionsInfo = getAccountPermissions(bankId, accountId, user1).body.extract[PermissionsJSON]
    val randomPermission = nextInt(persmissionsInfo.permissions.size)
    persmissionsInfo.permissions(randomPermission)
  }
  
  feature(s"$ApiEndpoint1 - Get Account access for User. - $VersionOfApi") {
    scenario("we will Get Account access for User.") {
      Given("Prepare all the parameters:")
      val bankId = randomBankId
      val bankAccountId = randomPrivateAccountId(bankId)
      val provider = defaultProvider
      val permission = randomAccountPermission(bankId, bankAccountId)
      val providerId = permission.user.id
  
      When("We use a valid access token and valid put json")
      val reply = getAccountAccessForUser(bankId, bankAccountId, provider, providerId, user1)
      Then("We should get back the updated view")
      reply.code should equal(200)
      val response = reply.body.extract[ViewsJsonV500]
      response.views.length should not equal (0)

      //Note: as to new system view stuff, now the default account should both have some system view accesses and custom view accesses.
      response.views.filter(_.is_system).length > 0  should be (true)
      response.views.filter(!_.is_system).length > 0  should be (true)
    }
  }

}

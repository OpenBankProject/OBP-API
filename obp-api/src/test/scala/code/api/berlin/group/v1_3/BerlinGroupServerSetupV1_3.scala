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

package code.api.berlin.group.v1_3

import org.json4s._
import code.api.Constant
import code.api.Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.OAuth._
import code.api.v3_0_0.ViewJsonV300
import code.api.v4_0_0.{PostAccountAccessJsonV400, PostViewJsonV400}
import code.setup.ServerSetupWithTestData
import code.views.Views
import code.setup.OBPReq
import org.json4s.native.Serialization.write
import org.scalatest.Tag

trait BerlinGroupServerSetupV1_3 extends ServerSetupWithTestData {

  val berlinGroupVersion1: String = ConstantsBG.berlinGroupVersion1.apiShortVersion
  object BerlinGroupV1_3 extends Tag("BerlinGroup_v1_3")
  val V1_3_BG = baseRequest / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion
  def v4_0_0_Request: OBPReq = baseRequest / "obp" / "v4.0.0"

  override def beforeEach() = {
    super.beforeEach()
    // Create necessary system views for APIs of Berlin Group)
    Views.views.vend.getOrCreateSystemView(SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID)
    Views.views.vend.getOrCreateSystemView(Constant.SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID)
    Views.views.vend.getOrCreateSystemView(Constant.SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID)
    Views.views.vend.getOrCreateSystemView(Constant.SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID)
  }

  def grantUserAccessToViewViaEndpoint(bankId: String,
                                       accountId: String,
                                       userId: String,
                                       consumerAndToken: Option[(Consumer, Token)],
                                       postBody: PostViewJsonV400
                                      ): ViewJsonV300 = {
    val postJson = PostAccountAccessJsonV400(userId, postBody)
    val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "account-access" / "grant").POST <@ (consumerAndToken)
    val response = makePostRequest(request, write(postJson))
    Then("We should get a 201 and check the response body")
    response.code should equal(201)
    response.body.extract[ViewJsonV300]
  }
  
}

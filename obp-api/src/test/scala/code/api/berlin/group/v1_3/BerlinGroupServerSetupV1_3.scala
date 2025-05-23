package code.api.berlin.group.v1_3

import code.api.Constant
import code.api.Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.OAuth._
import code.api.v3_0_0.ViewJsonV300
import code.api.v4_0_0.{PostAccountAccessJsonV400, PostViewJsonV400}
import code.setup.ServerSetupWithTestData
import code.views.Views
import dispatch.Req
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

trait BerlinGroupServerSetupV1_3 extends ServerSetupWithTestData {

  val berlinGroupVersion1: String = ConstantsBG.berlinGroupVersion1.apiShortVersion
  object BerlinGroupV1_3 extends Tag("BerlinGroup_v1_3")
  val V1_3_BG = baseRequest / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion
  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"

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

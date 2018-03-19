

package code.api.v3_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanGetAnyUser, CanSearchWarehouse}
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.entitlement.Entitlement
import code.model.CreateViewJson
import code.setup.{APIResponse, DefaultUsers}
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers.randomString
import code.api.v3_0_0.APIMethods300._


class WarehouseTest extends V300ServerSetup with DefaultUsers {
  
  
  val basicElasticsearchBody: String = 
    """{  "es_uri_part":"/_search",  "es_body_part":{ 
    "query": {
            "match_all": {}
    }
 }}"""

  def postSearch( consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v3_0Request / "search" / "warehouse" / "ALL").POST <@(consumerAndToken)
    makePostRequest(request, write(basicElasticsearchBody))
  }

  feature("Assuring that Search Warehouse is working as expected - v3.0.0") {

    scenario("We try to search warehouse without required role " + CanSearchWarehouse) {

      When("When we make the search request")
      val responsePost = postSearch(user1)

      And("We should get a 403")
      responsePost.code should equal(403)
      compactRender(responsePost.body \ "error").replaceAll("\"", "") should equal(UserHasMissingRoles + CanSearchWarehouse)
    }
  }
  
}



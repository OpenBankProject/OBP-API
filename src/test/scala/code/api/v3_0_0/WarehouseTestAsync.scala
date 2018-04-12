

package code.api.v3_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanSearchWarehouse
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.setup.{APIResponse, DefaultUsers}
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization.write
import scala.concurrent.Future

/**
  * Created by Marko Milić on 09/04/18.
  */
class WarehouseTestAsync extends V300ServerSetupAsync with DefaultUsers {


  val basicElasticsearchBody: String =
    """{  "es_uri_part":"/_search",  "es_body_part":{ 
      "query": {
              "match_all": {}
      }
    }}"""

  def postSearch(consumerAndToken: Option[(Consumer, Token)]): Future[APIResponse] = {
    val request = (v3_0Request / "search" / "warehouse" / "ALL").POST <@ (consumerAndToken)
    makePostRequestAsync(request, write(basicElasticsearchBody))
  }

  feature("Assuring that Search Warehouse is working as expected - v3.0.0") {

    scenario("We try to search warehouse without required role " + CanSearchWarehouse) {

      When("When we make the search request")
      val responsePost = postSearch(user1)

      And("We should get a 403")
      responsePost map {
        r =>
          r.code should equal(403)
          compactRender(r.body \ "error").replaceAll("\"", "") should equal(UserHasMissingRoles + CanSearchWarehouse)
      }

    }
  }

}



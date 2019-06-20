

package code.api.v3_0_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanSearchWarehouse
import code.api.util.{ApiVersion, CustomJsonFormats}
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.concurrent.Future

/**
  * Created by Marko Milić on 09/04/18.
  */
class WarehouseTestAsync extends V300ServerSetupAsync with DefaultUsers with CustomJsonFormats {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_0_0.dataWarehouseSearch))

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

    scenario("We try to search warehouse without required role " + CanSearchWarehouse, VersionOfApi, ApiEndpoint1) {

      When("When we make the search request")
      val responsePost = postSearch(user1)

      And("We should get a 403")
      responsePost map {
        r =>
          r.code should equal(403)
          r.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanSearchWarehouse)
      }

    }
  }

}



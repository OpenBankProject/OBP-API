

package code.api.v3_0_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanSearchWarehouse
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag


class WarehouseTest extends V300ServerSetup with DefaultUsers {
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

  def postSearch( consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v3_0Request / "search" / "warehouse" / "ALL").POST <@(consumerAndToken)
    makePostRequest(request, write(basicElasticsearchBody))
  }

  feature("Assuring that Search Warehouse is working as expected - v3.0.0") {

    scenario("We try to search warehouse without required role " + CanSearchWarehouse, VersionOfApi, ApiEndpoint1) {

      When("When we make the search request")
      val responsePost = postSearch(user1)

      And("We should get a 403")
      responsePost.code should equal(403)
      responsePost.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanSearchWarehouse)
    }
  }
  
}



package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.consumer.Consumers
import code.scope.Scope
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class CurrenciesTest extends V510ServerSetup with DefaultUsers {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.getCurrenciesAtBank))

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
  
  feature(s"Assuring $ApiEndpoint1 works as expected - $VersionOfApi") {

    scenario(s"We Call $ApiEndpoint1", VersionOfApi, ApiEndpoint1) {
      setPropsValues("require_scopes_for_all_roles" -> "true")
      val testBank = testBankId1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(user1.get._1.key).map(_.id.get.toString).getOrElse("")
      Scope.scope.vend.addScope(testBank.value, consumerId, ApiRole.canReadFx.toString())
      val requestGet = (v5_1_0_Request / "banks" / testBank.value / "currencies" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 200")
      responseGet.code should equal(200)
    }
    scenario(s"We Call $ApiEndpoint1 without a proper scope", VersionOfApi, ApiEndpoint1) {
      setPropsValues("require_scopes_for_all_roles" -> "true")
      val testBank = testBankId1
      val requestGet = (v5_1_0_Request / "banks" / testBank.value / "currencies" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 403")
      responseGet.code should equal(403)
    }
    scenario(s"We Call $ApiEndpoint1 with anonymous access", VersionOfApi, ApiEndpoint1) {
      setPropsValues("require_scopes_for_all_roles" -> "true")
      val testBank = testBankId1
      val requestGet = (v5_1_0_Request / "banks" / testBank.value / "currencies" ).GET
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 401")
      responseGet.code should equal(401)
    }
    
  }

}

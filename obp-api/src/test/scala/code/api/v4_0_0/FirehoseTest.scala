package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanUseAccountFirehoseAtAnyBank
import code.api.util.ErrorMessages.AccountFirehoseNotAllowedOnThisInstance
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class FirehoseTest extends V400ServerSetup  with PropsReset{
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getFirehoseAccountsAtOneBank))

  feature(s"test $ApiEndpoint1  version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1) {
      setPropsValues("allow_account_firehose" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId1.value /"firehose" / "accounts" / "views"/"owner").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[ModeratedFirehoseAccountsJsonV400]
    }
    scenario("We will call the endpoint with user credentials, props alias", VersionOfApi, ApiEndpoint1) {
      setPropsValues("allow_firehose_views" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId1.value /"firehose" / "accounts" / "views"/"owner").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[ModeratedFirehoseAccountsJsonV400]
    }

    scenario("We will call the endpoint missing role", VersionOfApi, ApiEndpoint1) {
      setPropsValues("allow_account_firehose" -> "true")
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId1.value / "firehose" / "accounts" / "views" / "owner").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403 and check the response body")
      response.code should equal(403)
      response.body.toString contains (CanUseAccountFirehoseAtAnyBank.toString()) should be(true)
    }
    
    scenario("We will call the endpoint missing props ", VersionOfApi, ApiEndpoint1) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId1.value /"firehose" / "accounts" / "views"/"owner").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 400 and check the response body")
      response.code should equal(400)
      response.body.toString contains (AccountFirehoseNotAllowedOnThisInstance) should be (true)
    }
  }
  
}

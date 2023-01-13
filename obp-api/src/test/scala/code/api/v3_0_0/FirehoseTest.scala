package code.api.v3_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanUseAccountFirehose, CanUseAccountFirehoseAtAnyBank}
import code.api.util.ErrorMessages.AccountFirehoseNotAllowedOnThisInstance
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.entitlement.Entitlement
import code.setup.PropsReset
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class FirehoseTest extends V300ServerSetup with PropsReset{
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_0_0.toString)
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_0_0.getFirehoseAccountsAtOneBank))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_0_0.getFirehoseTransactionsForBankAccount))
  

  feature(s"test ${ApiEndpoint2}") {

    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint2) {
      setPropsValues("allow_account_firehose" -> "true")
      setPropsValues("enable.force_error"->"true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v3_0Request / "banks" / testBankId1.value /"firehose" / "accounts" / "views"/"firehose").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[ModeratedCoreAccountsJsonV300]
    }
    scenario("We will call the endpoint with user credentials, props alias", VersionOfApi, ApiEndpoint2) {
      setPropsValues("allow_firehose_views" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v3_0Request / "banks" / testBankId1.value /"firehose" / "accounts" / "views"/"firehose").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[ModeratedCoreAccountsJsonV300]
    }


    scenario("We will call the endpoint missing role", VersionOfApi, ApiEndpoint2) {
      setPropsValues("allow_account_firehose" -> "true")
      When("We send the request")
      val request = (v3_0Request / "banks" / testBankId1.value / "firehose" / "accounts" / "views" / "firehose").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403 and check the response body")
      response.code should equal(403)
      response.body.toString contains (CanUseAccountFirehoseAtAnyBank.toString()) should be(true)
    }

    scenario("We will call the endpoint missing props ", VersionOfApi, ApiEndpoint2) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v3_0Request / "banks" / testBankId1.value /"firehose" / "accounts" / "views"/"firehose").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 400 and check the response body")
      response.code should equal(400)
      response.body.toString contains (AccountFirehoseNotAllowedOnThisInstance) should be (true)
    }
  }

  feature(s"test ${ApiEndpoint4.name}") {

    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint4) {
      setPropsValues("allow_account_firehose" -> "true")
      setPropsValues("enable.force_error"->"true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v3_0Request / "banks" / testBankId1.value /"firehose" / "accounts" / testAccountId1.value / "views"/"owner"/"transactions").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[ModeratedCoreAccountsJsonV300]
    }

    scenario("We will call the endpoint with user credentials - bank level role", VersionOfApi, ApiEndpoint4) {
      setPropsValues("allow_account_firehose" -> "true")
      setPropsValues("enable.force_error" -> "true")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.CanUseAccountFirehose.toString)
      When("We send the request")
      val request = (v3_0Request / "banks" / testBankId1.value / "firehose" / "accounts" / testAccountId1.value / "views" / "owner" / "transactions").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[ModeratedCoreAccountsJsonV300]
    }
    
    scenario("We will call the endpoint with user credentials, props alias", VersionOfApi, ApiEndpoint4) {
      setPropsValues("allow_firehose_views" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v3_0Request / "banks" / testBankId1.value /"firehose" / "accounts" / testAccountId1.value / "views"/"owner"/"transactions").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[ModeratedCoreAccountsJsonV300]
    }


    scenario("We will call the endpoint missing role", VersionOfApi, ApiEndpoint4) {
      setPropsValues("allow_account_firehose" -> "true")
      When("We send the request")
      val request = (v3_0Request / "banks" / testBankId1.value / "firehose" / "accounts" / testAccountId1.value /"views" / "owner"/"transactions").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403 and check the response body")
      response.code should equal(403)
      response.body.toString contains (CanUseAccountFirehoseAtAnyBank.toString()) should be(true)
      response.body.toString contains (CanUseAccountFirehose.toString()) should be(true)
    }

    scenario("We will call the endpoint missing props ", VersionOfApi, ApiEndpoint4) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v3_0Request / "banks" / testBankId1.value /"firehose" / "accounts" / testAccountId1.value / "views"/"owner"/"transactions").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 400 and check the response body")
      response.code should equal(400)
      response.body.toString contains (AccountFirehoseNotAllowedOnThisInstance) should be (true)
    }
  }
}
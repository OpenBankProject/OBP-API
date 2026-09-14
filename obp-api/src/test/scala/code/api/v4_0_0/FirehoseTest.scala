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

package code.api.v4_0_0

import code.api.Constant.{PARAM_LOCALE, PARAM_TIMESTAMP}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanUseAccountFirehoseAtAnyBank
import code.api.util.ErrorMessages.AccountFirehoseNotAllowedOnThisInstance
import code.api.v4_0_0.Http4s400.Implementations4_0_0
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
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getFastFirehoseAccountsAtOneBank))

  feature(s"test $ApiEndpoint1  version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1) {
      setPropsValues("allow_account_firehose" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId1.value /"firehose" / "accounts" / "views"/ "firehose").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[ModeratedFirehoseAccountsJsonV400]
    }
    scenario("We will call the endpoint with user credentials, props alias", VersionOfApi, ApiEndpoint1) {
      setPropsValues("allow_firehose_views" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId1.value /"firehose" / "accounts" / "views"/ "firehose").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      response.body.extract[ModeratedFirehoseAccountsJsonV400]
    }

    scenario("We will call the endpoint missing role", VersionOfApi, ApiEndpoint1) {
      setPropsValues("allow_account_firehose" -> "true")
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId1.value / "firehose" / "accounts" / "views" / "firehose").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403 and check the response body")
      response.code should equal(403)
      response.body.toString contains (CanUseAccountFirehoseAtAnyBank.toString()) should be(true)
    }

    scenario("We will call the endpoint missing props ", VersionOfApi, ApiEndpoint1) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId1.value /"firehose" / "accounts" / "views"/ "firehose").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 400 and check the response body")
      response.code should equal(400)
      response.body.toString contains (AccountFirehoseNotAllowedOnThisInstance) should be (true)
    }

    scenario("We will test the endpoint URL Params", VersionOfApi, ApiEndpoint1) {
      setPropsValues("allow_account_firehose" -> "true")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v4_0_0_Request / "banks" / testBankId1.value / "firehose" / "accounts" / "views" / "firehose").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val accounts = response.body.extract[ModeratedFirehoseAccountsJsonV400]
      accounts.accounts.length > (0) shouldBe(true)
      
      {
        val request = (v4_0_0_Request / "banks" / testBankId1.value / "firehose" / "accounts" / "views" / "firehose").GET <@ (user1) <<? (List(("NoExistingFieldName", "xxxxxx")))
        val response = makeGetRequest(request)
        Then("We should get a 200 and check the response body")
        response.code should equal(200)
        val accounts = response.body.extract[ModeratedFirehoseAccountsJsonV400]
        accounts.accounts.length shouldBe (0)
      }

      {
        val request = (v4_0_0_Request / "banks" / testBankId1.value / "firehose" / "accounts" / "views" / "firehose").GET <@ (user1) <<? (List((PARAM_LOCALE, "en_GB")))
        val response = makeGetRequest(request)
        Then("We should get a 200 and check the response body")
        response.code should equal(200)
        val accounts = response.body.extract[ModeratedFirehoseAccountsJsonV400]
        accounts.accounts.length > (0) shouldBe (true)
      }

      {
        val request = (v4_0_0_Request / "banks" / testBankId1.value / "firehose" / "accounts" / "views" / "firehose").GET <@ (user1) <<? (List((PARAM_TIMESTAMP, "1596762180358")))
        val response = makeGetRequest(request)
        Then("We should get a 200 and check the response body")
        response.code should equal(200)
        val accounts = response.body.extract[ModeratedFirehoseAccountsJsonV400]
        accounts.accounts.length > (0) shouldBe (true)
      }
    }
    
  }
  
  feature(s"test $ApiEndpoint2  version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1) {
      setPropsValues("allow_account_firehose" -> "true")

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v4_0_0_Request /"management" / "banks" / testBankId1.value /"fast-firehose" / "accounts" ).GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 200 and check the response body")
      response.code should equal(200)
      val responseAccounts =response.body.extract[FastFirehoseAccountsJsonV400].accounts

      {
        val params = ("limit", "2") ::("offset", "0"):: Nil
        val request = (v4_0_0_Request /"management" / "banks" / testBankId1.value /"fast-firehose" / "accounts" ).GET <@ (user1)
        val response = makeGetRequest(request, params)
        Then("We should get a 200 and check the response body")
        response.code should equal(200)
        val accounts = response.body.extract[FastFirehoseAccountsJsonV400].accounts
        accounts.length shouldBe (2)
      }

      {
        val params = ("limit", "1") ::("offset", "0"):: Nil
        val request = (v4_0_0_Request /"management" / "banks" / testBankId1.value /"fast-firehose" / "accounts" ).GET <@ (user1) <<? params
        val response = makeGetRequest(request)
        Then("We should get a 200 and check the response body")
        response.code should equal(200)
        val accounts = response.body.extract[FastFirehoseAccountsJsonV400].accounts
        accounts.length shouldBe (1)
        accounts.head shouldBe(responseAccounts.head)
      }

      {
        val params = ("limit", "1") ::("offset", "1"):: Nil
        val request = (v4_0_0_Request /"management" / "banks" / testBankId1.value /"fast-firehose" / "accounts" ).GET <@ (user1)<<? params
        val response = makeGetRequest(request)
        Then("We should get a 200 and check the response body")
        response.code should equal(200)
        val accounts = response.body.extract[FastFirehoseAccountsJsonV400].accounts
        accounts.length shouldBe (1)
        accounts.head shouldBe(responseAccounts(1))
      }
    }

    scenario("We will call the endpoint missing role", VersionOfApi, ApiEndpoint1) {
      setPropsValues("allow_account_firehose" -> "true")
      When("We send the request")
      val request = (v4_0_0_Request /"management" / "banks" / testBankId1.value /"fast-firehose" / "accounts" ).GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403 and check the response body")
      response.code should equal(403)
      response.body.toString contains (CanUseAccountFirehoseAtAnyBank.toString()) should be(true)
    }

    scenario("We will call the endpoint missing props ", VersionOfApi, ApiEndpoint1) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUseAccountFirehoseAtAnyBank.toString)
      When("We send the request")
      val request = (v4_0_0_Request /"management" / "banks" / testBankId1.value /"fast-firehose" / "accounts" ).GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 400 and check the response body")
      response.code should equal(400)
      response.body.toString contains (AccountFirehoseNotAllowedOnThisInstance) should be (true)
    }
  }
  
}

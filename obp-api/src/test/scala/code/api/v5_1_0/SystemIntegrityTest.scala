package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetSystemIntegrity
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class SystemIntegrityTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.customViewNamesCheck))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.systemViewNamesCheck))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.accountAccessUniqueIndexCheck))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_1_0.accountCurrencyCheck))
  object ApiEndpoint5 extends Tag(nameOf(Implementations5_1_0.orphanedAccountCheck))
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "custom-view-names-check").GET
      val response510 = makeGetRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "custom-view-names-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("error should be " + UserHasMissingRoles + CanGetSystemIntegrity)
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetSystemIntegrity)
    }
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemIntegrity.toString)
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "custom-view-names-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("We get successful response")
      response510.code should equal(200)
      response510.body.extract[CheckSystemIntegrityJsonV510]
    }
  }



  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "system-view-names-check").GET
      val response510 = makeGetRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "system-view-names-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("error should be " + UserHasMissingRoles + CanGetSystemIntegrity)
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetSystemIntegrity)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemIntegrity.toString)
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "system-view-names-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("We get successful response")
      response510.code should equal(200)
      response510.body.extract[CheckSystemIntegrityJsonV510]
    }
  }



  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "account-access-unique-index-1-check").GET
      val response510 = makeGetRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "account-access-unique-index-1-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("error should be " + UserHasMissingRoles + CanGetSystemIntegrity)
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetSystemIntegrity)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemIntegrity.toString)
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "account-access-unique-index-1-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("We get successful response")
      response510.code should equal(200)
      response510.body.extract[CheckSystemIntegrityJsonV510]
    }
  }


  feature(s"test $ApiEndpoint4 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "banks" / testBankId1.value / "account-currency-check").GET
      val response510 = makeGetRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "banks" / testBankId1.value / "account-currency-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("error should be " + UserHasMissingRoles + CanGetSystemIntegrity)
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetSystemIntegrity)
    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemIntegrity.toString)
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "banks" / testBankId1.value / "account-currency-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("We get successful response")
      response510.code should equal(200)
      response510.body.extract[CheckSystemIntegrityJsonV510]
    }
  }

  feature(s"test $ApiEndpoint5 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "banks" / testBankId1.value / "orphaned-account-check").GET
      val response510 = makeGetRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint5 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "banks" / testBankId1.value / "orphaned-account-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("error should be " + UserHasMissingRoles + CanGetSystemIntegrity)
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanGetSystemIntegrity)
    }
  }

  feature(s"test $ApiEndpoint5 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetSystemIntegrity.toString)
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "management" / "system" / "integrity" / "banks" / testBankId1.value / "orphaned-account-check").GET <@(user1)
      val response510 = makeGetRequest(request510)
      Then("We get successful response")
      response510.code should equal(200)
      response510.body.extract[CheckSystemIntegrityJsonV510]
    }
  }
  
  
}

package code.api.v5_1_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetAccountsHeldAtAnyBank, CanGetAccountsHeldAtOneBank, CanSyncUser}
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v5_1_0.Http4s510.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class AccountTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object GetCoreAccountByIdThroughView extends Tag(nameOf(Implementations5_1_0.getCoreAccountByIdThroughView))
  object getAccountsHeldByUserAtBank extends Tag(nameOf(Implementations5_1_0.getAccountsHeldByUserAtBank))
  object GetAccountsHeldByUser extends Tag(nameOf(Implementations5_1_0.getAccountsHeldByUser))
  object SyncExternalUser extends Tag(nameOf(Implementations5_1_0.syncExternalUser))

  lazy val bankId = randomBankId

  feature(s"test ${GetCoreAccountByIdThroughView.name}") {
    scenario(s"We will test ${GetCoreAccountByIdThroughView.name}", GetCoreAccountByIdThroughView, VersionOfApi) {

      val requestGet = (v5_1_0_Request / "banks" / "BANK_ID" / "accounts" / "ACCOUNT_ID"/ "views" / "VIEW_ID").GET

      // Anonymous call fails
      val anonymousResponseGet = makeGetRequest(requestGet)
      anonymousResponseGet.code should equal(401)
      anonymousResponseGet.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
      
    }
  }

  feature(s"test ${getAccountsHeldByUserAtBank.name}") {
    scenario(s"We will test ${getAccountsHeldByUserAtBank.name}", getAccountsHeldByUserAtBank, VersionOfApi) {
      val requestGet = (v5_1_0_Request / "users" / resourceUser2.userId / "banks" / bankId / "accounts-held").GET
      // Anonymous call fails
      val anonymousResponseGet = makeGetRequest(requestGet)
      anonymousResponseGet.code should equal(401)
      anonymousResponseGet.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
    scenario("We will call the endpoint with user credentials", getAccountsHeldByUserAtBank, VersionOfApi) {
      When(s"We make a request $getAccountsHeldByUserAtBank")
      val requestGet = (v5_1_0_Request / "users" / resourceUser2.userId / "banks" / bankId / "accounts-held").GET <@(user1)
      val response = makeGetRequest(requestGet)
      Then("We should get a 403")
      response.code should equal(403)
      val errorMessage = UserHasMissingRoles + s"${CanGetAccountsHeldAtOneBank} or $CanGetAccountsHeldAtAnyBank"
      response.body.extract[ErrorMessage].message contains errorMessage should be(true)
    }
  }

  feature(s"test ${GetAccountsHeldByUser.name}") {
    scenario(s"We will test ${GetAccountsHeldByUser.name}", GetAccountsHeldByUser, VersionOfApi) {
      val requestGet = (v5_1_0_Request / "users" / resourceUser2.userId / "accounts-held").GET
      // Anonymous call fails
      val anonymousResponseGet = makeGetRequest(requestGet)
      anonymousResponseGet.code should equal(401)
      anonymousResponseGet.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
    scenario("We will call the endpoint with user credentials", GetAccountsHeldByUser, VersionOfApi) {
      When(s"We make a request $GetAccountsHeldByUser")
      val requestGet = (v5_1_0_Request / "users" / resourceUser2.userId / "accounts-held").GET <@(user1)
      val response = makeGetRequest(requestGet)
      Then("We should get a 403")
      response.code should equal(403)
      val errorMessage = UserHasMissingRoles + s"$CanGetAccountsHeldAtAnyBank"
      response.body.extract[ErrorMessage].message contains errorMessage should be(true)
    }
  }

  feature(s"test ${SyncExternalUser.name}") {
    scenario(s"We will test ${SyncExternalUser.name}", SyncExternalUser, VersionOfApi) {
      val request = (v5_1_0_Request / "users" / resourceUser2.provider / resourceUser2.idGivenByProvider / "sync").GET
      // Anonymous call fails
      val response = makePostRequest(request, write(""))
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
    scenario("We will call the endpoint with user credentials", SyncExternalUser, VersionOfApi) {
      When(s"We make a request $SyncExternalUser")
      val requestGet = (v5_1_0_Request / "users" / resourceUser2.provider / resourceUser2.idGivenByProvider / "sync").GET <@(user1)
      val response = makePostRequest(requestGet,  write(""))
      Then("We should get a 403")
      response.code should equal(403)
      val errorMessage = UserHasMissingRoles + s"$CanSyncUser"
      response.body.extract[ErrorMessage].message contains errorMessage should be(true)
    }
  }
  
}
package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanSeeAccountAccessForAnyUser
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.AccountsMinimalJson400
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
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
  object GetAccountAccessByUserId extends Tag(nameOf(Implementations5_1_0.getAccountAccessByUserId))

  feature(s"test ${GetAccountAccessByUserId.name}") {
    scenario(s"We will test ${GetAccountAccessByUserId.name}", GetAccountAccessByUserId, VersionOfApi) {

      val requestGet = (v5_1_0_Request / "users" / resourceUser2.userId / "account-access").GET

      // Anonymous call fails
      val anonymousResponseGet = makeGetRequest(requestGet)
      anonymousResponseGet.code should equal(401)
      anonymousResponseGet.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
      
      // Call endpoint without the entitlement
      val badResponseGet = makeGetRequest(requestGet <@ user1)
      badResponseGet.code should equal(403)
      val errorMessage = badResponseGet.body.extract[ErrorMessage].message
      errorMessage contains UserHasMissingRoles should be (true)
      errorMessage contains CanSeeAccountAccessForAnyUser.toString() should be (true)

      // All good
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanSeeAccountAccessForAnyUser.toString())
      val goodResponseGet = makeGetRequest(requestGet <@ user1)
      goodResponseGet.code should equal(200)
      goodResponseGet.body.extract[AccountsMinimalJson400]
      
    }
  }
  
}
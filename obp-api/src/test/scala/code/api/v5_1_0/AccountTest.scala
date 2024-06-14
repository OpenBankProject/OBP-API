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
  object GetCoreAccountByIdThroughView extends Tag(nameOf(Implementations5_1_0.getCoreAccountByIdThroughView))

  feature(s"test ${GetCoreAccountByIdThroughView.name}") {
    scenario(s"We will test ${GetCoreAccountByIdThroughView.name}", GetCoreAccountByIdThroughView, VersionOfApi) {

      val requestGet = (v5_1_0_Request / "banks" / "BANK_ID" / "accounts" / "ACCOUNT_ID"/ "views" / "VIEW_ID").GET

      // Anonymous call fails
      val anonymousResponseGet = makeGetRequest(requestGet)
      anonymousResponseGet.code should equal(401)
      anonymousResponseGet.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
      
    }
  }
  
}
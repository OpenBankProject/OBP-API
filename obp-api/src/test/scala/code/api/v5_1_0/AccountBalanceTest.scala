package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.UserNotLoggedIn
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class AccountBalanceTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.getBankAccountBalances))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  def requestGetAccountBalances(viewId: String = "None") = (v5_1_0_Request / "banks" / bankAccount.bank_id / "accounts" / bankAccount.id / "views" / viewId / "balances").GET

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val responseGetAccountBalances = makeGetRequest(requestGetAccountBalances())
      Then("We should get a 401")
      responseGetAccountBalances.code should equal(401)
      responseGetAccountBalances.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access, no proper view") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1) {
      val responseGetAccountBalances = makeGetRequest(requestGetAccountBalances() <@ user1)
      Then("We should get a 403")
      responseGetAccountBalances.code should equal(403)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access with proper view") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1) {
      val responseGetAccountBalances = makeGetRequest(requestGetAccountBalances("owner") <@ user1)
      Then("We should get a 200")
      responseGetAccountBalances.code should equal(200)
    }
  }
}

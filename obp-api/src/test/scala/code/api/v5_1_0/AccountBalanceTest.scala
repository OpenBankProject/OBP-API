package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
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

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1) {
      val requestGetAccountBalances = (v5_1_0_Request / "banks" / bankAccount.bank_id / "accounts" / bankAccount.id / "views"/ "VIEW_ID" / "balances").GET <@ (user1)
      val responseGetAccountBalances = makeGetRequest(requestGetAccountBalances)
      Then("We should get a 403")
      org.scalameta.logger.elem(responseGetAccountBalances)
      responseGetAccountBalances.code should equal(403)
    }
  }
}

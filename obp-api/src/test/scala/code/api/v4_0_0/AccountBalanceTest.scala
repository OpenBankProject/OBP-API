package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import org.json4s.jvalue2extractable
import code.api.util.ErrorMessages.CannotFindAccountAccess
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

class AccountBalanceTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.getBankAccountsBalancesForCurrentUser))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getBankAccountBalancesForCurrentUser))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)

  Feature(s"test $ApiEndpoint1 and $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    Scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
      val requestGetAccountBalances = (v4_0_0_Request / "banks" / bankAccount.bank_id / "accounts" / bankAccount.id / "balances").GET <@ (user1)
      val responseGetAccountBalances = makeGetRequest(requestGetAccountBalances)
      Then("We should get a 200")
      responseGetAccountBalances.code should equal(200)

      val requestGetAccountsBalances = (v4_0_0_Request / "banks" / bankId / "balances").GET <@ (user1)
      val responseGetAccountsBalances = makeGetRequest(requestGetAccountsBalances)
      Then("We should get a 200")
      responseGetAccountsBalances.code should equal(200)
    }
    Scenario("We will call the endpoint with user2 who has no account access ", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
      val requestGetAccountBalances = (v4_0_0_Request / "banks" / bankAccount.bank_id / "accounts" / bankAccount.id / "balances").GET <@ (user2)
      val responseGetAccountBalances = makeGetRequest(requestGetAccountBalances)
      Then("We should get a 200")
      responseGetAccountBalances.code should equal(400)
      responseGetAccountBalances.body.toString contains(CannotFindAccountAccess) should be (true)

      val requestGetAccountsBalances = (v4_0_0_Request / "banks" / bankId / "balances").GET <@ (user2)
      val responseGetAccountsBalances = makeGetRequest(requestGetAccountsBalances)
      Then("We should get a 200")
      responseGetAccountsBalances.code should equal(200)
      responseGetAccountsBalances.body.extract[AccountsBalancesJsonV400].accounts.length should equal(0)
    }
  }
}

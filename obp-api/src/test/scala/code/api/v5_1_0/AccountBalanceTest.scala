package code.api.v5_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.UserNotLoggedIn
import code.api.v4_0_0.{AccountsBalancesJsonV400, BalanceJsonV400}
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import dispatch.Req
import net.liftweb.json
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
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.getBankAccountsBalances))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.getBankAccountsBalancesThroughView))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  def requestGetAccountBalances(viewId: String = "None"): Req = (v5_1_0_Request / "banks" / bankAccount.bank_id / "accounts" / bankAccount.id / "views" / viewId / "balances").GET
  def requestGetAccountsBalances(): Req = (v5_1_0_Request / "banks" / bankAccount.bank_id / "balances").GET
  def requestGetAccountsBalancesThroughView(viewId: String = "None"): Req = (v5_1_0_Request / "banks" / bankAccount.bank_id / "views" / viewId / "balances").GET

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


  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val responseGetAccountBalances = makeGetRequest(requestGetAccountsBalances())
      Then("We should get a 401")
      responseGetAccountBalances.code should equal(401)
      responseGetAccountBalances.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access with proper view") {
    scenario("We will call the endpoint with user credentials", VersionOfApi, ApiEndpoint1) {

      val responseGetAccountBalances = makeGetRequest(requestGetAccountsBalances() <@ user1)
      Then("We should get a 200")
      responseGetAccountBalances.code should equal(200)
      val accountsBefore = responseGetAccountBalances.body.extract[AccountsBalancesJsonV400].accounts.length

      // Make transaction
      val amountOfMoney = "10.00"
      // Create from and to account with 0 balance and transfer money
      val (fromAccountId, toAccountId, _) = createTransactionRequest(bankId, amountOfMoney)

      val responseGetAccountBalances2 = makeGetRequest(requestGetAccountsBalances() <@ user1)
      Then("We should get a 200")
      responseGetAccountBalances2.code should equal(200)

      val accountsAfter = responseGetAccountBalances2.body.extract[AccountsBalancesJsonV400].accounts.length
      accountsAfter should equal(accountsBefore + 2)

      val balances = responseGetAccountBalances2.body.extract[AccountsBalancesJsonV400]

      val toAccountBalance = balances.accounts.filter(_.account_id == toAccountId)
      val filteredBalances: List[BalanceJsonV400] = toAccountBalance.flatMap(_.balances.filter(i => i.amount == amountOfMoney && i.currency == "EUR"))
      filteredBalances.length should equal(1)

      val fromAccountBalance = balances.accounts.filter(_.account_id == fromAccountId)
      val filteredFromAccountBalance: List[BalanceJsonV400] = fromAccountBalance.flatMap(_.balances.filter(i => i.amount == s"-${amountOfMoney}" && i.currency == "EUR"))
      filteredFromAccountBalance.length should equal(1)
    }
  }


  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When(s"We make a request $ApiEndpoint1")
      val responseGetAccountBalances = makeGetRequest(requestGetAccountsBalancesThroughView("owner"))
      Then("We should get a 401")
      responseGetAccountBalances.code should equal(401)
      responseGetAccountBalances.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }


}

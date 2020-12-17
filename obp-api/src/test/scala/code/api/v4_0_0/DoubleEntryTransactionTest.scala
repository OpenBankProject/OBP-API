package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import org.scalatest.Tag

class DoubleEntryTransactionTest extends V400ServerSetup {

  lazy val testBankId: BankId = testBankId1
  lazy val testAccountId: AccountId = testAccountId1
  lazy val view = "owner"

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * mvn test -D tagsToInclude
   *
   * This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString())

  object GetDoubleEntryTransactionEndpoint extends Tag(nameOf(Implementations4_0_0.getDoubleEntryTransaction))

  feature(s"test $GetDoubleEntryTransactionEndpoint - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", GetDoubleEntryTransactionEndpoint, VersionOfApi) {
      Given("a random transaction")
      lazy val transaction = randomTransactionViaEndpoint(testBankId.value, testAccountId.value, view)

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / testBankId.value / "accounts" / testAccountId.value / view / "transactions" / transaction.id / "double-entry-transaction").GET
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $GetDoubleEntryTransactionEndpoint - Authorized access") {
    scenario("We will call the endpoint with user credentials", GetDoubleEntryTransactionEndpoint, VersionOfApi) {
      Given("a created transaction ")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateHistoricalTransaction.toString)
      val transaction = saveHistoricalTransactionViaEndpoint(testBankId, testAccountId, testBankId2, testAccountId0, BigDecimal(156.96), "a transaction", user1)

      When("We make a request v4.0.0")
      val addedEntitlement: Box[Entitlement] = Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanGetDoubleEntryTransactionAtOneBank.toString)
      val response400 = try {
        val request400 = (v4_0_0_Request / "banks" / testBankId.value / "accounts" / testAccountId.value / view / "transactions" / transaction.transaction_id / "double-entry-transaction").GET <@ (user1)
        makeGetRequest(request400)
      } finally {
        Entitlement.entitlement.vend.deleteEntitlement(addedEntitlement)
      }


      Then("We should get a 200")
      response400.code should equal(200)
      val doubleEntryTransaction = response400.body.extract[DoubleEntryTransactionJson]
      doubleEntryTransaction.transaction_request should be(null)
      doubleEntryTransaction.debit_transaction.bank_id should be(testBankId.value)
      doubleEntryTransaction.debit_transaction.account_id should be(testAccountId.value)
      doubleEntryTransaction.debit_transaction.transaction_id should be(transaction.transaction_id)
      doubleEntryTransaction.credit_transaction.bank_id should be(testBankId2.value)
      doubleEntryTransaction.credit_transaction.account_id should be(testAccountId0.value)
      doubleEntryTransaction.credit_transaction.transaction_id should not be empty


      Then("We make a request v4.0.0 but with other user")
      val requestWithNewAccountId = (v4_0_0_Request / "banks" / testBankId.value / "accounts" / testAccountId.value / view / "transactions" / transaction.transaction_id / "double-entry-transaction").GET <@ (user1)
      val responseWithNoRole = makeGetRequest(requestWithNewAccountId)
      Then("We should get a 403 and some error message")
      responseWithNoRole.code should equal(403)
      responseWithNoRole.body.toString contains (s"$UserHasMissingRoles") should be(true)


      Then("We grant the roles and test it again")
      Entitlement.entitlement.vend.addEntitlement(testBankId.value, resourceUser1.userId, ApiRole.CanGetDoubleEntryTransactionAtOneBank.toString)
      val responseWithOtherUser = makeGetRequest(requestWithNewAccountId)

      val doubleEntryTransaction2 = responseWithOtherUser.body.extract[DoubleEntryTransactionJson]
      doubleEntryTransaction2.transaction_request should be(null)
      doubleEntryTransaction2.debit_transaction.bank_id should be(testBankId.value)
      doubleEntryTransaction2.debit_transaction.account_id should be(testAccountId.value)
      doubleEntryTransaction2.debit_transaction.transaction_id should be(transaction.transaction_id)
      doubleEntryTransaction2.credit_transaction.bank_id should be(testBankId2.value)
      doubleEntryTransaction2.credit_transaction.account_id should be(testAccountId0.value)
      doubleEntryTransaction2.credit_transaction.transaction_id should not be empty
    }
  }
}

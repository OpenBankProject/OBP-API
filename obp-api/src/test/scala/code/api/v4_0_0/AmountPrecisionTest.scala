package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.util.ErrorMessages.InvalidAmountPrecision
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.TransactionRequestBodyJsonV200
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.entitlement.Entitlement
import code.setup.APIResponse
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, ErrorMessage}
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * This class tests that a request carrying an amount with more decimal places than its currency
 * allows is refused with 400 (OBP-10068), instead of the extra decimal places being cut off when the
 * amount is stored.
 *
 * The check is made once for every endpoint, in ResourceDocMiddleware, so these scenarios use two
 * endpoints that carry an amount in their body: creating an account with an opening balance, and
 * creating a SANDBOX_TAN transaction request. FundsAvailableTest covers an amount in a query string,
 * and code.asset.AmountPrecisionTest covers the check itself.
 */
class AmountPrecisionTest extends V400ServerSetup {

  object AmountPrecisionTag extends Tag("AmountPrecision")

  private val bankId = testBankId1.value

  /** This posts a Create Account request with the given opening balance and returns the response. */
  private def createAccountWithBalance(currency: String, amount: String) = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
    val body = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(
      user_id = resourceUser1.userId,
      balance = AmountOfMoneyJsonV121(currency, amount),
      account_routings = List(AccountRoutingJsonV121(s"scheme-${APIUtil.generateUUID().take(8)}", s"address-${APIUtil.generateUUID().take(8)}")))
    try makePostRequest((v4_0_0_Request / "banks" / bankId / "accounts").POST <@ (user1), write(body))
    finally Entitlement.entitlement.vend.deleteEntitlement(entitlement)
  }

  private def messageOf(response: APIResponse): String = response.body.extract[ErrorMessage].message

  feature("Amounts with more decimal places than their currency allows are refused") {

    scenario("An opening balance of 0.001 EUR is refused, because EUR has 2 decimal places", AmountPrecisionTag) {
      val response = createAccountWithBalance("EUR", "0.001")
      response.code should equal(400)
      messageOf(response) should startWith(InvalidAmountPrecision)
      messageOf(response) should include("The amount 0.001 EUR has 3 decimal place(s), but EUR allows at most 2.")
    }

    scenario("An amount in JPY may not have decimal places at all", AmountPrecisionTag) {
      val response = createAccountWithBalance("JPY", "0.5")
      response.code should equal(400)
      messageOf(response) should include("JPY allows at most 0.")
    }

    scenario("The currency code is checked in any letter case", AmountPrecisionTag) {
      val response = createAccountWithBalance("eur", "0.001")
      response.code should equal(400)
      messageOf(response) should include("EUR allows at most 2.")
    }

    scenario("Trailing zeros do not count as decimal places", AmountPrecisionTag) {
      List(("EUR", "0.000"), ("JPY", "0.00")).foreach { case (currency, amount) =>
        val response = createAccountWithBalance(currency, amount)
        withClue(s"$amount $currency: ${response.body}") { response.code should equal(201) }
      }
    }

    scenario("Crypto amounts are not checked until their real precision is recorded", AmountPrecisionTag) {
      val response = createAccountWithBalance("ETH", "0.001")
      response.body.extractOpt[ErrorMessage].map(_.message).getOrElse("") should not include ("OBP-10068")
    }

    scenario("A transaction request for 10.005 EUR is refused and nothing is paid", AmountPrecisionTag) {
      Given("two EUR accounts")
      val fromAccount = createAccountWithBalance("EUR", "0").body.extract[CreateAccountResponseJsonV310]
      val toAccount = createAccountWithBalance("EUR", "0").body.extract[CreateAccountResponseJsonV310]

      When("a transaction request for 10.005 EUR is made")
      val body = TransactionRequestBodyJsonV200(
        TransactionRequestAccountJsonV140(bankId, toAccount.account_id), AmountOfMoneyJsonV121("EUR", "10.005"), "precision test")
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / fromAccount.account_id / "owner" /
        "transaction-request-types" / "SANDBOX_TAN" / "transaction-requests").POST <@ (user1)
      val response = makePostRequest(request, write(body))

      Then("it is refused with 400")
      response.code should equal(400)
      messageOf(response) should include("The amount 10.005 EUR has 3 decimal place(s), but EUR allows at most 2.")

      And("the same request for 10.00 EUR is not refused for its precision")
      val accepted = makePostRequest(request, write(body.copy(value = AmountOfMoneyJsonV121("EUR", "10.00"))))
      accepted.body.extractOpt[ErrorMessage].map(_.message).getOrElse("") should not include ("OBP-10068")
    }
  }
}

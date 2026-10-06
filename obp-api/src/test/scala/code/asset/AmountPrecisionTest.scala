package code.asset

import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

/**
 * This class tests AmountPrecision, which finds an amount in a request that has more decimal places
 * than its currency allows: how decimal places are counted, how an amount is paired with its currency
 * in a JSON body and in a query string, and which currencies are not checked.
 *
 * Everything here is a pure function call with no database, so the currencies' decimal places come
 * from the built-in list (EUR 2, JPY 0, KWD 3).
 */
class AmountPrecisionTest extends FeatureSpec with Matchers with GivenWhenThen {

  feature("Counting decimal places") {

    scenario("Trailing zeros and exponents are not decimal places") {
      AmountPrecision.decimalPlacesOf(BigDecimal("12.34")) shouldBe 2
      AmountPrecision.decimalPlacesOf(BigDecimal("12.340")) shouldBe 2
      AmountPrecision.decimalPlacesOf(BigDecimal("10.00")) shouldBe 0
      AmountPrecision.decimalPlacesOf(BigDecimal("1E+3")) shouldBe 0
      AmountPrecision.decimalPlacesOf(BigDecimal("1e-5")) shouldBe 5
      AmountPrecision.decimalPlacesOf(BigDecimal("-0.125")) shouldBe 3
    }
  }

  feature("Amounts in a JSON body") {

    scenario("An amount with too many decimal places is found, however deep it is") {
      val found = AmountPrecision.inJsonBody("""{"to":{"bank_id":"b"},"value":{"currency":"EUR","amount":"12.345"}}""")
      found.map(_.describe) shouldBe Some("The amount 12.345 EUR has 3 decimal place(s), but EUR allows at most 2.")
    }

    scenario("Amounts within their currency's precision pass") {
      AmountPrecision.inJsonBody("""{"value":{"currency":"EUR","amount":"12.34"}}""") shouldBe None
      AmountPrecision.inJsonBody("""{"value":{"currency":"JPY","amount":"100.00"}}""") shouldBe None
      AmountPrecision.inJsonBody("""{"value":{"currency":"KWD","amount":"1.125"}}""") shouldBe None
    }

    scenario("An amount sent as a JSON number is read exactly") {
      AmountPrecision.inJsonBody("""{"currency":"EUR","amount":12.345}""").map(_.amount) shouldBe Some("12.345")
      AmountPrecision.inJsonBody("""{"currency":"EUR","amount":0.1}""") shouldBe None
      AmountPrecision.inJsonBody("""{"currency":"JPY","amount":5}""") shouldBe None
    }

    scenario("UK Open Banking's Amount and Currency are paired the same way") {
      AmountPrecision.inJsonBody("""{"InstructedAmount":{"Amount":"10.001","Currency":"GBP"}}""").map(_.currency) shouldBe Some("GBP")
    }

    scenario("Every amount in an array is checked") {
      val body = """{"payments":[{"currency":"EUR","amount":"1.00"},{"currency":"EUR","amount":"2.005"}]}"""
      AmountPrecision.inJsonBody(body).map(_.amount) shouldBe Some("2.005")
    }

    scenario("With several currency fields, the one named currency is the amount's") {
      AmountPrecision.inJsonBody("""{"charge_currency":"JPY","currency":"EUR","amount":"1.50"}""") shouldBe None
      AmountPrecision.inJsonBody("""{"from_currency":"EUR","to_currency":"JPY","amount":"1.50"}""") shouldBe None
    }

    scenario("Amounts the check cannot judge are left to the endpoint") {
      AmountPrecision.inJsonBody("""{"currency":"ZZZ","amount":"1.123456"}""") shouldBe None
      AmountPrecision.inJsonBody("""{"currency":"EUR","amount":"twelve"}""") shouldBe None
      AmountPrecision.inJsonBody("""{"amount":"1.123456"}""") shouldBe None
      AmountPrecision.inJsonBody("""not json, amount 1.123""") shouldBe None
    }

    scenario("Crypto amounts are not checked until their real precision is recorded") {
      AmountPrecision.inJsonBody("""{"currency":"ETH","amount":"0.000000000000000001"}""") shouldBe None
      AmountPrecision.inJsonBody("""{"currency":"ADA","amount":"1.123456"}""") shouldBe None
      AmountPrecision.inJsonBody("""{"currency":"lovelace","amount":"1.5"}""") shouldBe None
    }
  }

  feature("Amounts in a query string") {

    scenario("An amount next to a currency parameter is checked") {
      AmountPrecision.inQuery(Seq("currency" -> Some("EUR"), "amount" -> Some("1.234"))).map(_.amount) shouldBe Some("1.234")
      AmountPrecision.inQuery(Seq("currency" -> Some("EUR"), "amount" -> Some("1.23"))) shouldBe None
      AmountPrecision.inQuery(Seq("amount" -> Some("1.234"))) shouldBe None
    }
  }
}

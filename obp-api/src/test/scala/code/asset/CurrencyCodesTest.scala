package code.asset

import org.json4s.JsonDSL._
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

/**
 * This class tests CurrencyCodes, which holds OBP's rule that currency codes are case-insensitive:
 * how two codes are compared, which field and query parameter names are taken to hold a currency
 * code, and how a JSON request body has its currency codes upper-cased without any other value
 * changing.
 *
 * Everything here is a pure function call: no server, no database.
 */
class CurrencyCodesTest extends FeatureSpec with Matchers with GivenWhenThen {

  feature("Comparing and normalising codes") {

    scenario("A code is stored trimmed and in upper case") {
      CurrencyCodes.normalise("eur") shouldBe "EUR"
      CurrencyCodes.normalise(" Eur ") shouldBe "EUR"
      CurrencyCodes.normalise("lovelace") shouldBe "LOVELACE"
    }

    scenario("Two codes are the same whatever their letter case") {
      CurrencyCodes.same("eur", "EUR") shouldBe true
      CurrencyCodes.same("Ada", "ada") shouldBe true
      CurrencyCodes.same("EUR", "USD") shouldBe false
    }

    scenario("A missing code is not the same as any code") {
      CurrencyCodes.same(null, "EUR") shouldBe false
      CurrencyCodes.same("EUR", null) shouldBe false
      CurrencyCodes.same(null, null) shouldBe false
    }
  }

  feature("Which names hold a currency code") {

    scenario("Names ending in currency or currency code, in any case and with or without underscores") {
      List("currency", "Currency", "price_currency", "from_currency_code", "toCurrencyCode", "CURRENCY")
        .filterNot(CurrencyCodes.isCurrencyKey) shouldBe Nil
    }

    scenario("Names that only mention currency elsewhere do not") {
      List("currency_status", "currencies", "amount", "currency_rate")
        .filter(CurrencyCodes.isCurrencyKey) shouldBe Nil
    }
  }

  feature("Upper-casing the currency codes in a JSON body") {

    val fields = Set("currency", "price_currency")

    scenario("The currency fields are upper-cased wherever they appear, and nothing else changes") {
      val body = """{"value":{"currency":"eur","amount":"10.50"},"price_currency":"usd","description":"eur","items":[{"currency":"gbp"}]}"""
      CurrencyCodes.normaliseJsonBody(body, fields) shouldBe
        """{"value":{"currency":"EUR","amount":"10.50"},"price_currency":"USD","description":"eur","items":[{"currency":"GBP"}]}"""
    }

    scenario("Amounts sent as JSON numbers keep their exact form") {
      val body = """{"currency":"eur","amount":12345678901234567.123456789012345678}"""
      CurrencyCodes.normaliseJsonBody(body, fields) shouldBe
        """{"currency":"EUR","amount":12345678901234567.123456789012345678}"""
    }

    scenario("A value that does not look like a code is left alone") {
      val body = """{"currency":"Euro of the Eurozone"}"""
      CurrencyCodes.normaliseJsonBody(body, fields) shouldBe body
    }

    scenario("With no currency fields the body is returned as it came") {
      val body = """{"currency":"eur"}"""
      CurrencyCodes.normaliseJsonBody(body, Set.empty) shouldBe body
    }
  }

  feature("Finding the currency fields from an endpoint's example body") {

    scenario("String fields whose name holds a currency code, at any depth") {
      val example = ("value" -> ("currency" -> "EUR") ~ ("amount" -> "10")) ~
        ("charge_policy" -> "SHARED") ~
        ("payments" -> List(("instructed_currency" -> "EUR") ~ ("amount" -> "1")))
      CurrencyCodes.currencyFieldsOf(example) shouldBe Set("currency", "instructed_currency")
    }

    scenario("A field called currency that holds an object, not a code, is not one") {
      val example = ("currency" -> ("code" -> "EUR") ~ ("name" -> "Euro"))
      CurrencyCodes.currencyFieldsOf(example) shouldBe Set.empty
    }

    scenario("An endpoint with no example body has none") {
      CurrencyCodes.currencyFieldsOf(null) shouldBe Set.empty
    }
  }
}

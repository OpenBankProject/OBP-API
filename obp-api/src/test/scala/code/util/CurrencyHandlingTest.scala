package code.util

import code.api.util.APIUtil
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

/**
 * This class tests how OBP-API treats currency codes: which codes are accepted, how many decimal
 * places each one gets, and how amounts are turned into the stored minor-unit Long and back.
 *
 * The asset registry design (ideas/ASSET_REGISTRY.md) replaces the code behind these answers, and
 * this suite is the check that it keeps every correct answer while doing so.
 *
 * The suite asserts correct behaviour only. Where the code is known to be wrong today, the correct
 * expectation is written inside `pendingUntilFixed`: the scenario reports as pending while the
 * defect remains, and fails as soon as the code is fixed, so the wrapper has to be removed in the
 * same change and the scenario becomes an ordinary assertion. Behaviour whose correct form has not
 * been decided yet (for example how excess decimal places should be rejected) is not tested here.
 *
 * Everything here is a pure function call: no server, no database.
 */
class CurrencyHandlingTest extends FeatureSpec with Matchers with GivenWhenThen {

  /**
   * Every distinct code in media/xml/ISOCurrencyCodes.xml at the time of writing (183 codes).
   * It is a literal rather than read from the file so that an edit to the file shows up here as
   * a failure, instead of silently changing what the registry would seed.
   */
  private val codesInXmlFile: List[String] = List(
    "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG", "AZN", "BAM", "BBD",
    "BDT", "BGN", "BHD", "BIF", "BMD", "BND", "BOB", "BOV", "BRL", "BSD", "BTN", "BWP",
    "BYN", "BZD", "CAD", "CDF", "CHE", "CHF", "CHW", "CLF", "CLP", "CNY", "COP", "COU",
    "CRC", "CUC", "CUP", "CVE", "CZK", "DJF", "DKK", "DOP", "DZD", "EGP", "ERN", "ETB",
    "ETH", "EUR", "FJD", "FKP", "GBP", "GEL", "GHS", "GIP", "GMD", "GNF", "GTQ", "GYD",
    "HKD", "HNL", "HRK", "HTG", "HUF", "IDR", "ILS", "INR", "IQD", "IRR", "ISK", "JMD",
    "JOD", "JPY", "KES", "KGS", "KHR", "KMF", "KPW", "KRW", "KWD", "KYD", "KZT", "LAK",
    "LBP", "LKR", "LRD", "LSL", "LYD", "MAD", "MDL", "MGA", "MKD", "MMK", "MNT", "MOP",
    "MRU", "MUR", "MVR", "MWK", "MXN", "MXV", "MYR", "MZN", "NAD", "NGN", "NIO", "NOK",
    "NPR", "NZD", "OMR", "PAB", "PEN", "PGK", "PHP", "PKR", "PLN", "PYG", "QAR", "RON",
    "RSD", "RUB", "RWF", "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SHP", "SLL", "SOS",
    "SRD", "SSP", "STN", "SVC", "SYP", "SZL", "THB", "TJS", "TMT", "TND", "TOP", "TRY",
    "TTD", "TWD", "TZS", "UAH", "UGX", "USD", "USN", "UYI", "UYU", "UYW", "UZS", "VES",
    "VND", "VUV", "WST", "XAF", "XAG", "XAU", "XBA", "XBB", "XBC", "XBD", "XCD", "XDR",
    "XOF", "XPD", "XPF", "XPT", "XSU", "XTS", "XUA", "XXX", "YER", "ZAR", "ZMW", "ZWL",
    "ada", "lovelace", "wei"
  )

  /** XBT is not in the file; isValidCurrencyISOCode appends it in code. */
  private val acceptedCodes: List[String] = codesInXmlFile :+ "XBT"

  /**
   * The ISO 4217 minor units of every ISO currency in the file, read from its CcyMnrUnts element.
   * Codes whose minor unit ISO gives as "N.A." (precious metals, fund and accounting units) and the
   * non-ISO crypto codes are left out: their correct precision is an open question in the design.
   */
  private lazy val isoMinorUnits: Map[String, Int] =
    (APIUtil.CurrencyIsoCodeFromXmlFile \ "CcyTbl" \ "CcyNtry").flatMap { entry =>
      val code = (entry \ "Ccy").text.trim
      val minorUnits = (entry \ "CcyMnrUnts").text.trim
      if (code.nonEmpty && minorUnits.forall(_.isDigit) && minorUnits.nonEmpty && !nonIsoCodes.contains(code))
        Some(code -> minorUnits.toInt)
      else None
    }.toMap

  private val nonIsoCodes = Set("ETH", "ada", "lovelace", "wei")

  /** Codes where OBP's decimal places differ from ISO 4217 today (ideas/ASSET_REGISTRY.md, Background). */
  private val codesWithWrongDecimalPlaces = Set(
    "BHD", "IQD", "JOD", "LYD", "TND",
    "CLF", "UYW",
    "CZK",
    "BIF", "CLP", "DJF", "GNF", "ISK", "KMF", "PYG", "RWF", "UGX", "UYI", "VND", "VUV", "XAF", "XOF", "XPF"
  )

  feature("Which currency codes APIUtil.isValidCurrencyISOCode accepts") {

    scenario("Every code in the XML file, plus XBT, is accepted") {
      val rejected = acceptedCodes.filterNot(APIUtil.isValidCurrencyISOCode)
      rejected shouldBe Nil
    }

    scenario("The XML file holds exactly the codes listed in this test") {
      val codesReadFromFile = (APIUtil.CurrencyIsoCodeFromXmlFile \ "CcyTbl" \ "CcyNtry" \ "Ccy")
        .map(_.text.trim).filter(_.nonEmpty).distinct.sorted.toList
      codesReadFromFile shouldBe codesInXmlFile.sorted
    }

    scenario("Values that are not currency codes are rejected") {
      APIUtil.isValidCurrencyISOCode("") shouldBe false
      APIUtil.isValidCurrencyISOCode("EUR USD") shouldBe false
      APIUtil.isValidCurrencyISOCode("978") shouldBe false // EUR's ISO numeric code
      APIUtil.isValidCurrencyISOCode("USDC") shouldBe false
      APIUtil.isValidCurrencyISOCode("MRO") shouldBe false // replaced by MRU in ISO 4217
    }

    scenario("Currency codes are accepted in any letter case") {
      List("eur", "Eur", "xbt", "eth", "ADA", "LOVELACE", "WEI").filterNot(APIUtil.isValidCurrencyISOCode) shouldBe Nil
    }
  }

  feature("How many decimal places Helper.currencyDecimalPlaces gives each code") {

    scenario("ISO currencies get their ISO 4217 minor units") {
      Given(s"the ${isoMinorUnits.size} ISO currencies in the file with a numeric minor unit, less the known defects")
      val mismatches = isoMinorUnits.toList.sorted.collect {
        case (code, expected) if !codesWithWrongDecimalPlaces.contains(code) && Helper.currencyDecimalPlaces(code) != expected =>
          s"$code: expected $expected, got ${Helper.currencyDecimalPlaces(code)}"
      }
      mismatches shouldBe Nil
    }

    scenario("Known defects: these ISO currencies should also get their ISO 4217 minor units") {
      pendingUntilFixed {
        val mismatches = codesWithWrongDecimalPlaces.toList.sorted.collect {
          case code if Helper.currencyDecimalPlaces(code) != isoMinorUnits(code) =>
            s"$code: expected ${isoMinorUnits(code)}, got ${Helper.currencyDecimalPlaces(code)}"
        }
        mismatches shouldBe Nil
      }
    }

    scenario("The decimal places of a code do not depend on its letter case") {
      Helper.currencyDecimalPlaces("jpy") shouldBe 0
      Helper.currencyDecimalPlaces("kwd") shouldBe 3
    }
  }

  feature("How Helper.convertToSmallestCurrencyUnits turns an amount into the stored Long") {

    scenario("Amounts within the currency's precision convert exactly") {
      Helper.convertToSmallestCurrencyUnits(BigDecimal("12.45"), "EUR") shouldBe 1245L
      Helper.convertToSmallestCurrencyUnits(BigDecimal("9034"), "JPY") shouldBe 9034L
      Helper.convertToSmallestCurrencyUnits(BigDecimal("1.234"), "KWD") shouldBe 1234L
      Helper.convertToSmallestCurrencyUnits(BigDecimal("0"), "EUR") shouldBe 0L
      Helper.convertToSmallestCurrencyUnits(BigDecimal("-12.45"), "EUR") shouldBe -1245L
    }

    scenario("The largest and smallest amounts that fit in a Long convert exactly") {
      Helper.convertToSmallestCurrencyUnits(BigDecimal("92233720368547758.07"), "EUR") shouldBe Long.MaxValue
      Helper.convertToSmallestCurrencyUnits(BigDecimal("-92233720368547758.08"), "EUR") shouldBe Long.MinValue
    }

    scenario("An amount too large for a Long is refused, not wrapped around") {
      Given("10^17 EUR, which is 10^19 cents, more than Long.MaxValue (about 9.22 x 10^18)")
      an[IllegalArgumentException] should be thrownBy
        Helper.convertToSmallestCurrencyUnits(BigDecimal("100000000000000000"), "EUR")
      Given("one cent above Long.MaxValue")
      an[IllegalArgumentException] should be thrownBy
        Helper.convertToSmallestCurrencyUnits(BigDecimal("92233720368547758.08"), "EUR")
      Given("one cent below Long.MinValue")
      an[IllegalArgumentException] should be thrownBy
        Helper.convertToSmallestCurrencyUnits(BigDecimal("-92233720368547758.09"), "EUR")
    }
  }

  feature("How Helper.smallestCurrencyUnitToBigDecimal turns the stored Long back into an amount") {

    scenario("The scale of the result is the currency's decimal places") {
      val euros = Helper.smallestCurrencyUnitToBigDecimal(1245L, "EUR")
      euros shouldBe BigDecimal("12.45")
      euros.scale shouldBe 2

      val yen = Helper.smallestCurrencyUnitToBigDecimal(9034L, "JPY")
      yen shouldBe BigDecimal("9034")
      yen.scale shouldBe 0

      val dinars = Helper.smallestCurrencyUnitToBigDecimal(1234L, "KWD")
      dinars shouldBe BigDecimal("1.234")
      dinars.scale shouldBe 3

      Helper.smallestCurrencyUnitToBigDecimal(-1245L, "EUR") shouldBe BigDecimal("-12.45")
    }

    scenario("Converting an amount within the currency's precision to minor units and back is lossless") {
      List(("12.45", "EUR"), ("-0.01", "EUR"), ("99999.99", "EUR"), ("9034", "JPY"), ("1.234", "KWD")).foreach {
        case (amount, code) =>
          val stored = Helper.convertToSmallestCurrencyUnits(BigDecimal(amount), code)
          Helper.smallestCurrencyUnitToBigDecimal(stored, code) shouldBe BigDecimal(amount)
      }
    }

    scenario("The same stored value means a different amount for each precision") {
      Given("1000 minor units, which is why a precision change needs a data migration")
      Helper.smallestCurrencyUnitToBigDecimal(1000L, "EUR") shouldBe BigDecimal("10.00")
      Helper.smallestCurrencyUnitToBigDecimal(1000L, "KWD") shouldBe BigDecimal("1.000")
      Helper.smallestCurrencyUnitToBigDecimal(1000L, "JPY") shouldBe BigDecimal("1000")
    }
  }
}

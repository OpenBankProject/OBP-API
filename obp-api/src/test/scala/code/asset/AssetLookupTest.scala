package code.asset

import code.api.util.APIUtil
import code.setup.ServerSetup
import code.util.Helper

/**
 * This class tests that `APIUtil.isValidCurrencyISOCode` and `Helper.currencyDecimalPlaces` answer
 * from the asset registry, through AssetLookup, and that their answers are the ones OBP gave before
 * the registry existed (ideas/ASSET_REGISTRY.md, progress step 6).
 *
 * CurrencyHandlingTest checks the same functions without a database, where AssetLookup uses the
 * built-in list; this suite checks them with the registry seeded.
 *
 * Each scenario seeds the registry itself and tells AssetLookup to forget what it read before, and
 * the suite puts the seeded registry back when it ends (RestoresSeededAssetRegistry).
 */
class AssetLookupTest extends ServerSetup with RestoresSeededAssetRegistry {

  private def seedRegistry(): Unit = {
    Asset.bulkDelete_!!()
    AssetSeed.run()
    AssetLookup.invalidate()
  }

  /** Every code the built-in list knows, plus values it rejects or treats differently by letter case. */
  private val codesToCompare: List[String] =
    APIUtil.builtInCurrencyCodes.toList.sorted ++
      List("eur", "Eur", "jpy", "kwd", "xbt", "eth", "LOVELACE", "WEI", "", "EUR USD", "978", "USDC", "MRO")

  feature("Answers from the seeded registry") {

    scenario("Every code is accepted or rejected as the built-in list does, except that ADA is now accepted") {
      seedRegistry()
      val differences = codesToCompare.filter(code => APIUtil.isValidCurrencyISOCode(code) != APIUtil.builtInCurrencyCodes.contains(code))
      differences shouldBe Nil
      Then("ADA, the registry's code for Cardano's currency, is accepted, and so is the built-in spelling ada")
      APIUtil.isValidCurrencyISOCode("ADA") shouldBe true
      APIUtil.isValidCurrencyISOCode("ada") shouldBe true
    }

    scenario("Every code has the decimal places of the built-in table") {
      seedRegistry()
      val differences = (codesToCompare :+ "ADA").collect {
        case code if Helper.currencyDecimalPlaces(code) != Helper.builtInCurrencyDecimalPlaces(code) =>
          s"$code: registry ${Helper.currencyDecimalPlaces(code)}, built-in ${Helper.builtInCurrencyDecimalPlaces(code)}"
      }
      differences shouldBe Nil
    }
  }

  feature("The registry is what is read") {

    scenario("An asset added to the registry is accepted, with the registry's decimal places") {
      seedRegistry()
      APIUtil.isValidCurrencyISOCode("TOKENAB") shouldBe false

      When("a bank registers a deposit token with 4 decimal places")
      Assets.createAsset("TOKENAB", AssetTypes.DEPOSIT_TOKEN, "Token AB", 4, Some("bank-a"), AssetStatuses.ACTIVE, "user-1")
        .openOrThrowException("the token is created")

      Then("the code is accepted at once, with 4 decimal places")
      APIUtil.isValidCurrencyISOCode("TOKENAB") shouldBe true
      Helper.currencyDecimalPlaces("TOKENAB") shouldBe 4
      Helper.convertToSmallestCurrencyUnits(BigDecimal("1.2345"), "TOKENAB") shouldBe 12345L
    }

    scenario("With the registry empty, the built-in answers are used") {
      Asset.bulkDelete_!!()
      AssetLookup.invalidate()
      APIUtil.isValidCurrencyISOCode("EUR") shouldBe true
      APIUtil.isValidCurrencyISOCode("lovelace") shouldBe true
      APIUtil.isValidCurrencyISOCode("ADA") shouldBe false
      Helper.currencyDecimalPlaces("JPY") shouldBe 0
      Helper.currencyDecimalPlaces("KWD") shouldBe 3
    }
  }
}

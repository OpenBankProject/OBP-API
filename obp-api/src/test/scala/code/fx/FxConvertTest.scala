package code.fx

import code.asset.{Asset, AssetLookup, AssetSeed, RestoresSeededAssetRegistry}
import code.setup.ServerSetup

/**
 * This class tests fx.convert, which rounds a converted amount to the decimal places of the currency
 * it is converted into. It used to round every currency to 2 places, so an amount converted into
 * OMR, which has 3, lost its third decimal.
 *
 * The decimal places come from the asset registry, so each scenario seeds it as OBP does at boot.
 */
class FxConvertTest extends ServerSetup with RestoresSeededAssetRegistry {
  private def seedRegistry(): Unit = {
    Asset.bulkDelete_!!()
    AssetSeed.run()
    AssetLookup.invalidate()
  }

  feature("fx.convert rounds to the decimal places of the target currency") {
    scenario("An amount converted into OMR keeps three decimal places") {
      seedRegistry()
      When("100 EUR is converted into OMR at 0.41823")
      val converted = fx.convert(BigDecimal("100"), Some(0.41823), "OMR")
      Then("the result is 41.823 OMR, not 41.82")
      converted shouldBe BigDecimal("41.823")
      converted.scale shouldBe 3
    }

    scenario("An amount converted into OMR is rounded half up at the third decimal place") {
      seedRegistry()
      fx.convert(BigDecimal("1"), Some(0.38455), "OMR") shouldBe BigDecimal("0.385")
      fx.convert(BigDecimal("1"), Some(0.38454), "OMR") shouldBe BigDecimal("0.385")
      fx.convert(BigDecimal("1"), Some(0.38449), "OMR") shouldBe BigDecimal("0.384")
    }

    scenario("The letter case of the currency code does not matter") {
      seedRegistry()
      fx.convert(BigDecimal("100"), Some(0.41823), "omr") shouldBe BigDecimal("41.823")
    }

    scenario("Other currencies keep their own decimal places") {
      seedRegistry()
      Then("EUR has 2")
      fx.convert(BigDecimal("100"), Some(2.391234), "EUR") shouldBe BigDecimal("239.12")
      Then("JPY has 0")
      fx.convert(BigDecimal("100"), Some(1.413735), "JPY") shouldBe BigDecimal("141")
      Then("KWD has 3, like OMR")
      fx.convert(BigDecimal("100"), Some(0.307456), "KWD") shouldBe BigDecimal("30.746")
    }
  }
}

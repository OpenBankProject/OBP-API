package code.asset

import code.setup.ServerSetup
import code.util.Helper
import net.liftweb.mapper.By

/**
 * This class tests the asset registry's seed and the rules the registry enforces on every row.
 *
 * The seed's job at this stage is to reproduce exactly what OBP answered before the registry: every
 * code in the built-in list (`APIUtil.builtInCurrencyCodes`) is seeded (apart from `lovelace` and
 * `wei`, which are units of ADA and ETH, and `ada`, which is seeded upper case), each at the decimal
 * places of the built-in table (`Helper.builtInCurrencyDecimalPlaces`). These scenarios, with
 * AssetLookupTest, are what show that reading the registry instead changed nothing.
 *
 * Each scenario empties the asset table and runs the seed itself rather than relying on the seed
 * run at boot, and the suite puts the seeded registry back when it ends (RestoresSeededAssetRegistry).
 */
class AssetSeedTest extends ServerSetup with RestoresSeededAssetRegistry {

  private def emptyRegistryAndSeed(): (Int, Int, Int) = {
    Asset.bulkDelete_!!()
    AssetSeed.run()
  }

  feature("The seed entries") {
    scenario("Every code in the currency XML file is seeded, except the crypto units, plus XBT, ADA and ETH") {
      val seededCodes = AssetSeed.entries.map(_.assetCode)
      seededCodes.distinct.size shouldBe seededCodes.size
      // 183 codes in the file, minus ada, lovelace, ETH and wei, plus XBT, ADA and ETH.
      seededCodes.size shouldBe 182
      seededCodes should contain allOf ("EUR", "USD", "JPY", "KWD", "XAU", "XDR", "XBT", "ADA", "ETH")
      seededCodes should contain noneOf ("ada", "lovelace", "LOVELACE", "wei", "WEI")
    }

    scenario("Every entry has the decimal places OBP uses for that code today") {
      AssetSeed.entries.foreach { entry =>
        withClue(entry.assetCode) { entry.decimalPlaces shouldBe Helper.builtInCurrencyDecimalPlaces(entry.assetCode) }
      }
    }

    scenario("Each code gets the right asset type") {
      val typeByCode = AssetSeed.entries.map(entry => entry.assetCode -> entry.assetType).toMap
      typeByCode("EUR") shouldBe AssetTypes.FIAT
      typeByCode("XAU") shouldBe AssetTypes.PRECIOUS_METAL
      typeByCode("XAG") shouldBe AssetTypes.PRECIOUS_METAL
      typeByCode("XDR") shouldBe AssetTypes.ACCOUNTING_UNIT
      typeByCode("XTS") shouldBe AssetTypes.ACCOUNTING_UNIT
      typeByCode("XBT") shouldBe AssetTypes.CRYPTO
      typeByCode("ADA") shouldBe AssetTypes.CRYPTO
      typeByCode("ETH") shouldBe AssetTypes.CRYPTO
    }
  }

  feature("Seeding the registry") {
    scenario("A first run inserts every entry as an ACTIVE asset with no issuer") {
      val (inserted, alreadyPresent, failed) = emptyRegistryAndSeed()
      inserted shouldBe AssetSeed.entries.size
      alreadyPresent shouldBe 0
      failed shouldBe 0

      val assetsByCode = Assets.getAssets().map(asset => asset.assetCode -> asset).toMap
      assetsByCode.keySet shouldBe AssetSeed.entries.map(_.assetCode).toSet
      AssetSeed.entries.foreach { entry =>
        val asset = assetsByCode(entry.assetCode)
        withClue(entry.assetCode) {
          asset.assetType shouldBe entry.assetType
          asset.name shouldBe entry.name
          asset.decimalPlaces shouldBe entry.decimalPlaces
          asset.status shouldBe AssetStatuses.ACTIVE
          asset.issuerBankId shouldBe None
          asset.createdByUserId shouldBe AssetSeed.SeedActor
          asset.assetId should not be empty
        }
      }
    }

    scenario("A second run inserts nothing") {
      emptyRegistryAndSeed()
      val (inserted, alreadyPresent, failed) = AssetSeed.run()
      inserted shouldBe 0
      alreadyPresent shouldBe AssetSeed.entries.size
      failed shouldBe 0
      Asset.count shouldBe AssetSeed.entries.size
    }

    scenario("A run never changes an asset that is already there") {
      emptyRegistryAndSeed()
      Asset.find(By(Asset.AssetCode, "XTS")).openOrThrowException("XTS was seeded")
        .Status(AssetStatuses.SUSPENDED).Name("Changed by this deployment").saveMe()

      AssetSeed.run()

      val xts = Assets.getAsset("XTS").openOrThrowException("XTS is still there")
      xts.status shouldBe AssetStatuses.SUSPENDED
      xts.name shouldBe "Changed by this deployment"
    }
  }

  feature("Looking up and creating assets") {
    scenario("A lookup ignores the case of the code") {
      emptyRegistryAndSeed()
      Assets.getAsset("eur").map(_.assetCode) shouldBe Assets.getAsset("EUR").map(_.assetCode)
      Assets.getAsset("Ada").map(_.assetCode).toOption shouldBe Some("ADA")
      Assets.getAsset("lovelace").isDefined shouldBe false
    }

    scenario("A code is stored upper case") {
      Asset.bulkDelete_!!()
      Assets.createAsset("tokenab", AssetTypes.DEPOSIT_TOKEN, "Token AB", 2, Some("bank-a"), AssetStatuses.ACTIVE, "user-1")
        .map(_.assetCode).toOption shouldBe Some("TOKENAB")
    }

    scenario("Assets that break the registry's rules are refused") {
      Asset.bulkDelete_!!()
      def create(code: String, assetType: String, decimalPlaces: Int, issuer: Option[String], status: String = AssetStatuses.ACTIVE) =
        Assets.createAsset(code, assetType, "Name", decimalPlaces, issuer, status, "user-1")

      create("AB", AssetTypes.CRYPTO, 2, None).isDefined shouldBe false                     // too short
      create("ABCDEFGHIJK", AssetTypes.CRYPTO, 2, None).isDefined shouldBe false            // too long
      create("AB-C", AssetTypes.CRYPTO, 2, None).isDefined shouldBe false                   // not a letter or digit
      create("ABCD", "NOT_A_TYPE", 2, None).isDefined shouldBe false
      create("ABCD", AssetTypes.CRYPTO, -1, None).isDefined shouldBe false
      create("ABCD", AssetTypes.CRYPTO, 19, None).isDefined shouldBe false                  // beyond the storage scale
      create("ABCD", AssetTypes.DEPOSIT_TOKEN, 2, None).isDefined shouldBe false            // issued type needs an issuer
      create("ABCD", AssetTypes.FIAT, 2, Some("bank-a")).isDefined shouldBe false           // fiat has no issuer
      create("ABCD", AssetTypes.CRYPTO, 2, None, status = "FROZEN").isDefined shouldBe false
      Asset.count shouldBe 0

      create("ABCD", AssetTypes.CRYPTO, 18, None).isDefined shouldBe true
    }

    scenario("A code can only be registered once, whatever its case") {
      Asset.bulkDelete_!!()
      Assets.createAsset("ABCD", AssetTypes.CRYPTO, "First", 2, None, AssetStatuses.ACTIVE, "user-1").isDefined shouldBe true
      Assets.createAsset("abcd", AssetTypes.CRYPTO, "Second", 2, None, AssetStatuses.ACTIVE, "user-1").isDefined shouldBe false
      Asset.count shouldBe 1
    }
  }
}

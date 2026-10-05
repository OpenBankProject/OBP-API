package code.asset

import org.scalatest.{BeforeAndAfterAll, Suite}

/**
 * This trait is for suites that empty the asset registry or fill it with test assets. Currency
 * validation and decimal places read the registry (AssetLookup), and a suite that runs later in the
 * same JVM without resetting the database, such as the pure unit test CurrencyHandlingTest, would
 * otherwise read whatever the last scenario left there. When the suite ends, the registry is put
 * back to what the boot seed makes, and AssetLookup forgets its in-memory copy.
 */
trait RestoresSeededAssetRegistry extends BeforeAndAfterAll { this: Suite =>
  override def afterAll(): Unit = {
    try {
      Asset.bulkDelete_!!()
      AssetSeed.run()
      AssetLookup.invalidate()
    } finally super.afterAll()
  }
}

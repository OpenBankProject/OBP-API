package code.apiproduct

import code.setup.ServerSetup

/**
 * Characterization test for the api-product store.
 *
 * The table had no direct coverage: the only suite naming ApiProduct exercised the separate
 * api-product-ATTRIBUTE table. Written against the Lift Mapper implementation first and confirmed
 * green there, so it pins existing behaviour rather than describing the Doobie rewrite.
 *
 * What it pins, beyond plain round-tripping:
 *   - createOrUpdate is keyed on (bankId, apiProductCode) and updates in place, so a second call
 *     for the same pair mutates rather than inserting a second row - and leaves apiProductId,
 *     which the caller never supplies, untouched.
 *   - tags survive a pipe-delimited storage format that normalises them (trim, lower-case, strip
 *     embedded pipes, de-duplicate, drop empties).
 *   - the tag filter matches a whole tag, not a substring of one: filtering by "beta" must not
 *     return a product tagged only "beta-2". That is the entire reason the stored form carries
 *     leading and trailing pipes.
 */
class ApiProductsProviderTest extends ServerSetup {

  private val provider = MappedApiProductsProvider
  private val bankId = "test-bank-for-api-products"

  private def create(code: String, name: String = "a name", tags: List[String] = Nil) =
    provider.createOrUpdateApiProduct(
      bankId = bankId, apiProductCode = code, parentApiProductCode = "", name = name,
      category = "", moreInfoUrl = "", termsAndConditionsUrl = "", description = "",
      collectionId = "", monthlySubscriptionCurrency = "", monthlySubscriptionAmount = "",
      perSecondCallLimit = -1L, perMinuteCallLimit = -1L, perHourCallLimit = -1L,
      perDayCallLimit = -1L, perWeekCallLimit = -1L, perMonthCallLimit = -1L, tags = tags)

  Feature("api-product storage") {

    Scenario("a created product round-trips and is retrievable by its natural key") {
      create("prod-roundtrip", name = "Round Trip").isDefined should equal(true)

      val found = provider.getApiProductByBankIdAndCode(bankId, "prod-roundtrip")
        .openOrThrowException("expected the product just created")
      found.bankId should equal(bankId)
      found.apiProductCode should equal("prod-roundtrip")
      found.name should equal("Round Trip")
      found.apiProductId.nonEmpty should equal(true)
    }

    Scenario("createOrUpdate on an existing (bankId, code) updates in place rather than inserting") {
      create("prod-upsert", name = "First")
      val idAfterFirst = provider.getApiProductByBankIdAndCode(bankId, "prod-upsert")
        .openOrThrowException("expected the product").apiProductId

      create("prod-upsert", name = "Second")

      val after = provider.getApiProductByBankIdAndCode(bankId, "prod-upsert")
        .openOrThrowException("expected the product")
      after.name should equal("Second")
      And("the generated apiProductId is preserved - the caller never supplies it")
      after.apiProductId should equal(idAfterFirst)
      And("there is still exactly one row for that code")
      provider.getApiProductsByBankId(bankId).count(_.apiProductCode == "prod-upsert") should equal(1)
    }

    Scenario("tags are normalised on the way in and read back as a list") {
      create("prod-tags", tags = List("  Featured ", "BETA", "featured", "", "we|ird"))

      val found = provider.getApiProductByBankIdAndCode(bankId, "prod-tags")
        .openOrThrowException("expected the product")
      Then("trimmed, lower-cased, de-duplicated, empties dropped, embedded pipes stripped")
      found.tags should equal(List("featured", "beta", "weird"))
    }

    Scenario("a product with no tags reads back an empty list") {
      create("prod-notags", tags = Nil)
      provider.getApiProductByBankIdAndCode(bankId, "prod-notags")
        .openOrThrowException("expected the product").tags should equal(Nil)
    }

    Scenario("the tag filter matches whole tags, not substrings of longer ones") {
      create("prod-beta", tags = List("beta"))
      create("prod-beta-2", tags = List("beta-2"))

      val betaOnly = provider.getApiProductsByBankId(bankId, Some("beta")).map(_.apiProductCode)
      betaOnly should contain("prod-beta")
      withClue("filtering by 'beta' must not drag in a product tagged only 'beta-2': ") {
        betaOnly should not contain "prod-beta-2"
      }
    }

    Scenario("listing without a tag filter returns every product for the bank") {
      create("prod-list-1")
      create("prod-list-2")
      val codes = provider.getApiProductsByBankId(bankId).map(_.apiProductCode)
      codes should contain allOf ("prod-list-1", "prod-list-2")
    }

    Scenario("delete removes the product; a second delete reports nothing to remove") {
      create("prod-delete")
      provider.deleteApiProduct(bankId, "prod-delete").isDefined should equal(true)
      provider.getApiProductByBankIdAndCode(bankId, "prod-delete").isDefined should equal(false)

      And("deleting an unknown code is Empty rather than an error")
      provider.deleteApiProduct(bankId, "prod-never-existed").isDefined should equal(false)
    }
  }
}

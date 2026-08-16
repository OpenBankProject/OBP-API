package code.products

import code.setup.ServerSetup
import com.openbankproject.commons.model.{BankId, ProductCode}

/**
 * Characterization of ProductTagsProvider, written before the implementation moves to Doobie.
 *
 * The provider had no test. Four behaviours are pinned, all of them things a rewrite can lose:
 *
 *  - normalisation: tags are trimmed, lower-cased, de-duplicated, and blanks dropped, on the way
 *    in AND on the way into a query;
 *  - setTags has replace semantics implemented as a diff, not truncate-and-reinsert. The comment
 *    on the method says why (concurrent updates of disjoint tags stay race-free at row level), so
 *    the test asserts the surviving rows rather than just the resulting set;
 *  - getProductCodesWithAllTags is AND, not OR - a product must carry every requested tag - and
 *    an empty request returns nothing rather than everything;
 *  - getTagsByProductCodes is a batch lookup keyed by product code, and an empty input returns an
 *    empty map rather than the whole bank.
 *
 * ProductTagsProvider is a plain object rather than a vend, so this calls it directly; the object
 * keeps its name when its innards move to Doobie.
 */
class ProductTagsProviderTest extends ServerSetup {

  private val bankId = BankId("producttag-test-bank")
  private val otherBankId = BankId("producttag-test-bank-2")
  private val productA = ProductCode("PROD-A")
  private val productB = ProductCode("PROD-B")

  override def beforeEach() = {
    super.beforeEach()
    ProductTagsProvider.setTags(bankId, productA, Nil)
    ProductTagsProvider.setTags(bankId, productB, Nil)
    ProductTagsProvider.setTags(otherBankId, productA, Nil)
  }

  Feature("product tag storage") {

    Scenario("a product with no tags reads as an empty list") {
      ProductTagsProvider.getTags(bankId, productA) should equal(Nil)
    }

    Scenario("tags are normalised: trimmed, lower-cased, de-duplicated, blanks dropped") {
      ProductTagsProvider.setTags(bankId, productA, List("  Savings ", "SAVINGS", "green", "", "   "))

      ProductTagsProvider.getTags(bankId, productA) should equal(List("green", "savings"))
    }

    Scenario("setTags replaces the previous set") {
      ProductTagsProvider.setTags(bankId, productA, List("one", "two"))
      ProductTagsProvider.setTags(bankId, productA, List("two", "three"))

      ProductTagsProvider.getTags(bankId, productA) should equal(List("three", "two"))
    }

    Scenario("setTags is scoped to one product and one bank") {
      ProductTagsProvider.setTags(bankId, productA, List("shared"))
      ProductTagsProvider.setTags(bankId, productB, List("other"))
      ProductTagsProvider.setTags(otherBankId, productA, List("elsewhere"))

      ProductTagsProvider.getTags(bankId, productA) should equal(List("shared"))
      ProductTagsProvider.getTags(bankId, productB) should equal(List("other"))
      ProductTagsProvider.getTags(otherBankId, productA) should equal(List("elsewhere"))
    }

    Scenario("getProductCodesWithAllTags requires EVERY tag, not any of them") {
      ProductTagsProvider.setTags(bankId, productA, List("green", "savings"))
      ProductTagsProvider.setTags(bankId, productB, List("green"))

      Then("asking for one tag returns both products")
      ProductTagsProvider.getProductCodesWithAllTags(bankId, List("green")) should
        equal(Set("PROD-A", "PROD-B"))

      And("asking for both tags returns only the product that carries both")
      ProductTagsProvider.getProductCodesWithAllTags(bankId, List("green", "savings")) should
        equal(Set("PROD-A"))
    }

    Scenario("getProductCodesWithAllTags normalises the request and rejects an empty one") {
      ProductTagsProvider.setTags(bankId, productA, List("savings"))

      ProductTagsProvider.getProductCodesWithAllTags(bankId, List("  SAVINGS ")) should
        equal(Set("PROD-A"))

      And("an empty or blank-only request matches nothing rather than everything")
      ProductTagsProvider.getProductCodesWithAllTags(bankId, Nil) should equal(Set.empty)
      ProductTagsProvider.getProductCodesWithAllTags(bankId, List("", "   ")) should equal(Set.empty)
    }

    Scenario("getTagsByProductCodes returns one entry per product that has tags") {
      ProductTagsProvider.setTags(bankId, productA, List("b", "a"))
      ProductTagsProvider.setTags(bankId, productB, List("c"))

      val byCode = ProductTagsProvider.getTagsByProductCodes(bankId, List("PROD-A", "PROD-B"))
      byCode("PROD-A") should equal(List("a", "b"))
      byCode("PROD-B") should equal(List("c"))
    }

    Scenario("getTagsByProductCodes returns an empty map for an empty request") {
      ProductTagsProvider.setTags(bankId, productA, List("a"))
      ProductTagsProvider.getTagsByProductCodes(bankId, Nil) should equal(Map.empty)
    }
  }
}

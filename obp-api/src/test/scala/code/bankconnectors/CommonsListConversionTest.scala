package code.bankconnectors

import code.productattribute.ProductAttributeRow
import com.openbankproject.commons.model.enums.ProductAttributeType
import com.openbankproject.commons.model.{BankId, ProductAttribute, ProductAttributeCommons, ProductCode}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * A provider result reaches a Commons list by conversion, not by a cast.
 *
 * `list.asInstanceOf[List[XCommons]]` compiles and does nothing: the element type is erased, so
 * the cast checks nothing at the point it is written. What it does is licence the compiler to
 * insert a checkcast at the first element access - so the failure lands somewhere else entirely,
 * on whoever reads the field, with a stack trace that does not name the cast.
 *
 * The premise behind those casts - "the provider only ever constructs XCommons" - stopped holding
 * when the providers moved to Doobie: `DoobieProductAttributeProvider` returns `ProductAttributeRow`,
 * its own type implementing the same trait. This pins both halves of that: the cast survives being
 * written and then throws on use, and `toCommonsList` gives back a list that does not.
 *
 * `check_no_blind_commons_casts.py` is the other half - it keeps the pattern from coming back.
 */
class CommonsListConversionTest extends AnyFlatSpec with Matchers {

  private val row: ProductAttribute = ProductAttributeRow(
    bankId = BankId("gh.29.uk"),
    productCode = ProductCode("1234BW"),
    productAttributeId = "attr-1",
    attributeType = ProductAttributeType.STRING,
    name = "OVERDRAFT_START_DATE",
    value = "2026-01-01",
    isActive = Some(true)
  )

  private val fromProvider: List[ProductAttribute] = List(row)

  "a provider row" should "not be a Commons instance in the first place" in {
    // If this ever fails the cast below would be harmless and this test would be arguing about
    // nothing - so it is asserted rather than assumed.
    row shouldBe a[ProductAttributeRow]
    row should not be a[ProductAttributeCommons]
  }

  "casting the list to a Commons list" should "survive the cast and then throw on first use" in {
    val cast = fromProvider.asInstanceOf[List[ProductAttributeCommons]]
    withClue("the cast itself must be a no-op - that is the whole problem with it: ") {
      cast should have size 1
    }
    a[ClassCastException] should be thrownBy {
      // Reading an element at the Commons type is what inserts the checkcast, and it is what every
      // consumer of a List[XCommons] field does.
      cast.head.productAttributeId
    }
  }

  "toCommonsList" should "convert the rows into real Commons instances" in {
    val converted: List[ProductAttributeCommons] = ProductAttributeCommons.toCommonsList(fromProvider)
    converted should have size 1
    converted.head shouldBe a[ProductAttributeCommons]
    converted.head.productAttributeId should equal("attr-1")
    converted.head.bankId should equal(BankId("gh.29.uk"))
    converted.head.name should equal("OVERDRAFT_START_DATE")
    converted.head.value should equal("2026-01-01")
    converted.head.attributeType should equal(ProductAttributeType.STRING)
    converted.head.isActive should equal(Some(true))
  }
}

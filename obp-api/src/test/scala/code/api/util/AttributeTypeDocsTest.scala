package code.api.util

import com.openbankproject.commons.model.enums._
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

/**
 * This class checks that every attribute `type` enumeration accepts the same names, and that the
 * shared description in [[AttributeTypeDocs]] lists exactly those names.
 *
 * Each kind of attribute (Bank, Product, Account and so on) has its own enumeration in obp-commons,
 * but error messages and ResourceDoc descriptions for all of them are built from one list. If a
 * name were added to one enumeration and not the others, or to the enumerations and not the list,
 * callers would be told a type is accepted when it is not, or the reverse. This suite catches that.
 *
 * Everything here is a pure function call: no server, no database.
 */
class AttributeTypeDocsTest extends FeatureSpec with Matchers with GivenWhenThen {

  private val expectedNames = Set("STRING", "INTEGER", "DOUBLE", "DECIMAL", "BOOLEAN", "DATE_WITH_DAY")

  private val namesPerEnumeration: List[(String, Set[String])] = List(
    "AttributeType"                   -> AttributeType.values.map(_.toString).toSet,
    "UserAttributeType"               -> UserAttributeType.values.map(_.toString).toSet,
    "AtmAttributeType"                -> AtmAttributeType.values.map(_.toString).toSet,
    "RegulatedEntityAttributeType"    -> RegulatedEntityAttributeType.values.map(_.toString).toSet,
    "CounterpartyAttributeType"       -> CounterpartyAttributeType.values.map(_.toString).toSet,
    "BankAttributeType"               -> BankAttributeType.values.map(_.toString).toSet,
    "AccountAttributeType"            -> AccountAttributeType.values.map(_.toString).toSet,
    "ProductAttributeType"            -> ProductAttributeType.values.map(_.toString).toSet,
    "CardAttributeType"               -> CardAttributeType.values.map(_.toString).toSet,
    "CustomerAttributeType"           -> CustomerAttributeType.values.map(_.toString).toSet,
    "TransactionAttributeType"        -> TransactionAttributeType.values.map(_.toString).toSet,
    "TransactionRequestAttributeType" -> TransactionRequestAttributeType.values.map(_.toString).toSet
  )

  feature("Every attribute type enumeration accepts the same names") {
    namesPerEnumeration.foreach { case (enumerationName, names) =>
      scenario(s"$enumerationName accepts STRING, INTEGER, DOUBLE, DECIMAL, BOOLEAN and DATE_WITH_DAY") {
        names shouldBe expectedNames
      }
    }

    scenario("DECIMAL and BOOLEAN can be parsed from the name a caller sends") {
      ProductAttributeType.withNameOption("DECIMAL") shouldBe Some(ProductAttributeType.DECIMAL)
      AccountAttributeType.withNameOption("BOOLEAN") shouldBe Some(AccountAttributeType.BOOLEAN)
      AttributeType.withNameOption("DECIMAL") shouldBe Some(AttributeType.DECIMAL)
    }
  }

  feature("The shared description lists exactly the accepted names") {
    scenario("AttributeTypeDocs.examples has one entry for each accepted name") {
      AttributeTypeDocs.examples.map(_._1.toString) should contain theSameElementsAs expectedNames
    }

    scenario("The error message text names every type with an example") {
      AttributeTypeDocs.typesWithExamples shouldBe
        "STRING(TAX_NUMBER), INTEGER(123), DOUBLE(12.1234), DECIMAL(1234.5678), BOOLEAN(true) and DATE_WITH_DAY(2012-04-23)"
    }

    scenario("The ResourceDoc description names every type") {
      expectedNames.foreach { name =>
        AttributeTypeDocs.typeFieldDescription should include(s""""$name"""")
      }
    }
  }
}

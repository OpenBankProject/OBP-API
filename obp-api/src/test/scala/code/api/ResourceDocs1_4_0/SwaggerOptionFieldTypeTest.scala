package code.api.ResourceDocs1_4_0

import java.util.Date
import org.json4s.jvalue2monadic

import org.json4s.JsonAST.{JNothing, JString, JValue}
import org.json4s.native.JsonMethods.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * An Option field must be documented as the thing it holds, not as an array of it.
 *
 * SwaggerJSONFactory dispatches on the field's type through a chain of `isTypeOf[...]` tests, and
 * the collection cases go through `type Coll[T]`. That alias was GenTraversableLike on 2.12 and is
 * IterableOnce here - which 2.13's Option implements and 2.12's did not. So every `Coll[X]` test
 * now also answers true for `Option[X]`, and wherever a Coll case is reached before the matching
 * Option case, a scalar field is published as an array.
 *
 * Two places reach it first. The String block tests Coll[String] before Option[String] - String is
 * the one scalar whose Option case sits below its Coll case - and the generic List-or-Array
 * fallback at the end of the chain catches every Option the earlier cases did not name: an Option
 * of a case class, of a JValue. The other scalar blocks are safe only because their Option case
 * happens to be written above their Coll case.
 *
 * These are checks on the published contract, not on internals: the swagger definitions are what
 * clients generate code from, and a string that claims to be an array of strings breaks them.
 */
// Declared at file scope, not nested inside the test class: SwaggerJSONFactory.translateEntity
// reflects on the value's runtime type via scala.reflect.runtime.universe, which for a nested
// case class also has to resolve the enclosing class - here that would be a ScalaTest suite
// (AnyFlatSpec/Matchers/Assertions), and walking that unrelated third-party hierarchy throws
// (same class of scala-reflect symbol-table limitation as the CyclicReference fix in
// RestConnector_vMar2019_FrozenTest, reached via a nested-case-class's outer pointer instead of a
// connector type's base classes). A file-scope case class has no such enclosing class to resolve.
case class Inner(x: String)
case class OptionalScalars(
  optString: Option[String],
  optInner: Option[Inner],
  plainString: String
)
object Colour extends Enumeration { type Colour = Value; val Red, Green = Value }

case class Enums(
  oneEnum: Colour.Value,
  listOfEnum: List[Colour.Value],
  optListOfEnum: Option[List[Colour.Value]],
  optEnum: Option[Colour.Value]
)

case class RealCollections(
  listOfString: List[String],
  optListOfString: Option[List[String]],
  optListOfDate: Option[List[Date]]
)

class SwaggerOptionFieldTypeTest extends AnyFlatSpec with Matchers {

  /**
   * The field's schema, parsed. Asserted on structurally rather than by substring: the factory
   * emits both `"type":"array"` and `"type": "array"` depending on which case produced it, so a
   * substring check answers the wrong question and fails on spacing.
   */
  private def schemaOf(entity: Any, field: String): JValue = {
    // translateEntity returns a definitions *fragment* - `"EntityName":{...}` - not a document,
    // so it has to be wrapped before it will parse.
    val json = SwaggerJSONFactory.translateEntity(entity)
    val parsed = parse(s"{$json}")
    val schema = (parsed \\ field) match {
      case JNothing => fail(s"$field is absent from the generated schema:\n$json")
      case found => found
    }
    schema
  }

  private def typeOf(schema: JValue): Option[String] = (schema \ "type").toOption.collect {
    case JString(t) => t
  }

  "an Option[String] field" should "be documented as a string, not an array of strings" in {
    val schema = schemaOf(OptionalScalars(Some("a"), Some(Inner("b")), "c"), "optString")

    withClue(s"optString schema was ${org.json4s.native.JsonMethods.compact(org.json4s.native.JsonMethods.render(schema))}: ") {
      typeOf(schema) should equal(Some("string"))
    }
  }

  "an Option of a case class" should "be documented as a reference, not an array of references" in {
    val schema = schemaOf(OptionalScalars(Some("a"), Some(Inner("b")), "c"), "optInner")

    withClue(s"optInner schema was ${org.json4s.native.JsonMethods.compact(org.json4s.native.JsonMethods.render(schema))}: ") {
      typeOf(schema) should not equal Some("array")
      (schema \\ "$ref") should not equal JNothing
    }
  }

  "a plain String field" should "still be documented as a string" in {
    typeOf(schemaOf(OptionalScalars(Some("a"), Some(Inner("b")), "c"), "plainString")) should equal(Some("string"))
  }

  // The other half of the contract: what is genuinely a collection must stay an array. A fix that
  // suppressed Option everywhere would break these, and the mixed Date case - written as
  // isOneOfType[Coll[Date], Option[Coll[Date]]] - is the one a blanket rule breaks first.
  private val collections = RealCollections(List("a"), Some(List("b")), Some(List(new Date())))

  "a List field" should "still be documented as an array" in {
    typeOf(schemaOf(collections, "listOfString")) should equal(Some("array"))
  }

  "an Option[List[String]] field" should "still be documented as an array" in {
    typeOf(schemaOf(collections, "optListOfString")) should equal(Some("array"))
  }

  "an Option[List[Date]] field" should "still be documented as an array" in {
    typeOf(schemaOf(collections, "optListOfDate")) should equal(Some("array"))
  }

  // The String block's cases carry two independent clauses: a type test and an isNestEnumeration
  // test. Moving the Option case above the Coll case moved both, and the enumeration clauses were
  // ordered among themselves - isNestEnumeration digs to the innermost type argument, so
  // Option[List[Colour]] satisfies isNestEnumeration[Option[_]] just as much as
  // isNestEnumeration[Option[List[_]]]. Whichever is tested first wins, and only one of them is
  // right. These pin all four shapes so the two orderings cannot be conflated again.
  private val enums = Enums(Colour.Red, List(Colour.Red), Some(List(Colour.Red)), Some(Colour.Red))

  "an enumeration field" should "be documented as a string" in {
    typeOf(schemaOf(enums, "oneEnum")) should equal(Some("string"))
  }

  "a List of enumerations" should "be documented as an array" in {
    typeOf(schemaOf(enums, "listOfEnum")) should equal(Some("array"))
  }

  "an Option[List[enumeration]]" should "be documented as an array, not a string" in {
    withClue("isNestEnumeration digs to the innermost type arg, so an Option case tested before " +
      "the Option[List[...]] case claims this one too: ") {
      typeOf(schemaOf(enums, "optListOfEnum")) should equal(Some("array"))
    }
  }

  "an Option[enumeration]" should "be documented as a string" in {
    typeOf(schemaOf(enums, "optEnum")) should equal(Some("string"))
  }
}
